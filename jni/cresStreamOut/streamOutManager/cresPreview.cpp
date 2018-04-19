/**
 * Copyright (C) 2016 to the present, Crestron Electronics, Inc.
 * All rights reserved.
 * No part of this software may be reproduced in any form, machine
 * or natural, without the express written consent of Crestron Electronics.
 *
 * \file        cresPreview.cpp
 *
 * \brief       Implementation of stream preview
 *
 * \author      John Freeman
 *
 * \date        11/30/2016
 *
 * \note
 */

///////////////////////////////////////////////////////////////////////////////
#include <stdlib.h>
#include <time.h>
#include <gst/app/gstappsrc.h>
#include <gst/gstpad.h>

#include "cresStreamOutManager.h"
#include "cresPreview.h"

Preview::Preview() : m_previewq(NULL),m_videoconv(NULL),m_valve(NULL), m_previewsink(NULL),
m_nativeWindow(NULL),m_blockpad(NULL),m_bInstallSink(false),m_pCam(NULL),m_bPipelineReady(false),
m_preview_probe_id(0),m_paused(false),m_bWindowClosed(false)
{
	mULock = new Mutex();
	mCond  = new CondVar();
	mCond_mtx = new Mutex();
}

Preview::~Preview()
{
    if(mULock)
    {
    	delete mULock;
    	mULock = NULL;
    }

    if(mCond)
    {
    	delete mCond;
    	mCond = NULL;
    }

    if(mCond_mtx)
    {
    	delete mCond_mtx;
    	mCond_mtx = NULL;
    }
}

int Preview::add(void *arg)
{
	CStreamCamera *pCam = (CStreamCamera *) arg;

	int  iStatus  = 0;

	if(pCam)
	{
		m_pCam = pCam;

		m_previewq    = gst_element_factory_make( "queue",        NULL );
		m_videoconv   = gst_element_factory_make( "videoconvert", NULL );
		m_valve       = gst_element_factory_make( "valve",        NULL);
		m_prevsinkq   = gst_element_factory_make( "queue",        NULL);
		m_previewsink = gst_element_factory_make( "fakesink",  NULL );

		if( !m_previewq || !m_videoconv || !m_valve || !m_prevsinkq || !m_previewsink )
		{
			iStatus = -1;
			CSIO_LOG(eLogLevel_error, "Preview: Cannot create pipeline elements" );
		}
		else
		{
			m_blockpad = gst_element_get_static_pad (m_prevsinkq, "src");

			gst_bin_add_many( GST_BIN( pCam->m_pipeline ), m_previewq, m_videoconv, m_valve, m_prevsinkq, m_previewsink, NULL );
			if(!gst_element_link_many( pCam->m_tee, m_previewq, m_videoconv, m_valve, m_prevsinkq, m_previewsink, NULL ))
			{
				CSIO_LOG(eLogLevel_error,  "Preview: Cannot link to preview elements" );
				iStatus = -2;
			}
			else
			{
				GstPad *pad;
				pad = gst_element_get_static_pad (m_previewsink, "sink");
				m_preview_probe_id = gst_pad_add_probe( pad, GST_PAD_PROBE_TYPE_BUFFER, cb_Playing_probe,
				this, NULL);
				gst_object_unref (pad);

				CSIO_LOG(eLogLevel_verbose,  "Preview: added" );
			}
		}
	}
	else
	{
		CSIO_LOG(eLogLevel_verbose,  "Preview: parent is NULL" );
		iStatus = -1;
	}

	return( iStatus );
}

int Preview::drop(void)
{
	int  iStatus  = 0;

	gst_object_unref( m_previewq );
	gst_object_unref( m_videoconv );
	gst_object_unref( m_valve );
	gst_object_unref( m_prevsinkq );
	gst_object_unref( m_previewsink );

	m_previewq    = NULL;
	m_videoconv   = NULL;
	m_valve       = NULL;
	m_prevsinkq   = NULL;
	m_previewsink = NULL;

	m_bPipelineReady   = false;
	m_preview_probe_id = 0;
	m_paused = false;

	wakeup();

	CSIO_LOG(eLogLevel_verbose,  "Preview: removed" );

	return( iStatus );
}

int Preview::start(void *window)
{
	int rtn = 0;

	if( m_previewsink )
	{
		if( m_valve )
		{
			g_object_set(G_OBJECT(m_valve), "drop", false, NULL);
			m_paused = false;
		}
		else
		{
			CSIO_LOG(eLogLevel_error, "Preview: cannot Resume. Valve is NULL");
		}

		m_nativeWindow = window;

		m_bInstallSink = true;
		gst_pad_add_probe( m_blockpad, GST_PAD_PROBE_TYPE_BLOCK_DOWNSTREAM, cb_Prevsink_probe, this, NULL);

		m_paused = false; //resume if paused
	}
	else
	{
		m_nativeWindow = window; //cache
		CSIO_LOG(eLogLevel_error,  "Preview: cannot start. Sink is NULL" );
		rtn = -1;
	}

	return(rtn);
}

int Preview::pause(void *window)
{
	int rtn = 0;

	if( m_valve )
	{
		g_object_set(G_OBJECT(m_valve), "drop", true, NULL);
		m_paused = true;
	}
	else
	{
		CSIO_LOG(eLogLevel_error, "Preview: cannot pause. Valve is NULL");
		rtn = -1;
	}

	return(rtn);
}

int Preview::stop(void *window)
{
	int rtn = 0;

	if( m_previewsink )
	{
		m_bInstallSink = false;
		m_bWindowClosed = false;
		gst_pad_add_probe( m_blockpad, GST_PAD_PROBE_TYPE_BLOCK_DOWNSTREAM, cb_Prevsink_probe, this, NULL );
	}
	else
	{
		CSIO_LOG(eLogLevel_error, "Preview: cannot stop. Sink is NULL");
		rtn = -1;
	}

	return(rtn);
}

int Preview::waitForPreviewClosed(int timeout_sec)
{
	int rtn = 0;

	if( m_bPipelineReady && !m_bWindowClosed )
	{
		int               rc=0;
		struct timespec   ts;

		clock_gettime(CLOCK_REALTIME, &ts);
		ts.tv_nsec = 0;
		ts.tv_sec  += timeout_sec;

		mCond_mtx->lock();
		pthread_mutex_t *mutex = mCond_mtx->get_mutex_ptr();
		rc = mCond->waittimedcont( mutex, &ts );
		if( rc == ETIMEDOUT )
		{
			rtn = ETIMEDOUT;
			CSIO_LOG(eLogLevel_error,  "Preview: cannot stop. Timed out: %d",rc );
		}
		else
			rtn = (rc==0) ?1:0;	//is started or stopped by user

		mCond_mtx->unlock();
	}

	return(rtn);
}

int Preview::waitForPreviewAvailable(int timeout_sec)
{
	int rtn = 0;

	if( !m_bPipelineReady )
	{
	    int               rc=0;
	    struct timespec   ts;

	    clock_gettime(CLOCK_REALTIME, &ts);
	    ts.tv_nsec = 0;
	    ts.tv_sec  += timeout_sec;

		mCond_mtx->lock();
		pthread_mutex_t *mutex = mCond_mtx->get_mutex_ptr();
		rc = mCond->waittimedcont( mutex, &ts );
		if( rc == ETIMEDOUT )
		{
			rtn = ETIMEDOUT;
			CSIO_LOG(eLogLevel_error,  "Preview: cannot start. Timed out: %d",rc );
		}
		else
			rtn = (rc==0) ?1:0;	//is started or stopped by user

		mCond_mtx->unlock();
	}

	return(rtn);
}

int Preview::wakeup(void)
{
	int rtn = -1;

	if(mCond)
	{
		mCond_mtx->lock();
		mCond->signal();
		mCond_mtx->unlock();
		rtn = 0;
	}
	else
	{
		CSIO_LOG(eLogLevel_error,  "Preview: Cannot send wake-up. Signal is NULL" );
	}

	return(rtn);
}

void Preview::setPlaying(void)
{
//	CSIO_LOG(eLogLevel_verbose, "JF-Preview: set status to playing, native window = %x", m_nativeWindow );
	if( !m_bPipelineReady )
	{
		if( m_nativeWindow )
		{
			start(m_nativeWindow);
		}
	}

	m_bPipelineReady = true;
	wakeup();
}

GstPadProbeReturn cb_Playing_probe (GstPad *pad, GstPadProbeInfo *buffer, gpointer user_data)
{
	Preview *pPrev = (Preview *) user_data;

	gst_pad_remove_probe( pad, pPrev->m_preview_probe_id );

	pPrev->setPlaying();

    return(GST_PAD_PROBE_OK);
}

GstPadProbeReturn cb_Prevsink_probe( GstPad * pad, GstPadProbeInfo *info, gpointer user_data )
{
	Preview *pPrev = (Preview *) user_data;

	gst_pad_remove_probe( pad, GST_PAD_PROBE_INFO_ID(info) );

	if(!pPrev->m_bInstallSink)
	{
		gst_video_overlay_set_window_handle( GST_VIDEO_OVERLAY (pPrev->m_previewsink), (guintptr)NULL );
		pPrev->m_nativeWindow = NULL;
	}

	gst_element_set_state( pPrev->m_previewsink, GST_STATE_NULL );
	gst_bin_remove( GST_BIN(pPrev->m_pCam->m_pipeline), pPrev->m_previewsink );

	if(!pPrev->m_bInstallSink)
	{
		pPrev->m_previewsink = gst_element_factory_make( "fakesink" ,NULL );
	}
	else
	{
		pPrev->m_previewsink = gst_element_factory_make( "glimagesink" ,NULL );
		g_object_set(G_OBJECT(pPrev->m_previewsink), "force-aspect-ratio", FALSE, NULL);
		g_object_set(G_OBJECT(pPrev->m_previewsink), "sync", FALSE, NULL);

		gst_video_overlay_set_window_handle( GST_VIDEO_OVERLAY (pPrev->m_previewsink), (guintptr) pPrev->m_nativeWindow );
		// From tutorial 3 comments:
		// "We need to call gst_x_overlay_expose() twice
		// because of the way the surface changes propagate down the OpenGL ES / EGL pipeline
		// (The only video sink available for Android in the GStreamer SDK uses OpenGL ES).
		// By the time we call the first expose,
		// the surface that the sink will pick up still contains the old size."
		gst_video_overlay_expose( GST_VIDEO_OVERLAY (pPrev->m_previewsink) );
		gst_video_overlay_expose( GST_VIDEO_OVERLAY (pPrev->m_previewsink) );

	}

	if( !pPrev->m_previewsink  )
	{
		CSIO_LOG(eLogLevel_error, "Preview: Cannot re-create sink" );
	}

	gst_bin_add( GST_BIN(pPrev->m_pCam->m_pipeline), pPrev->m_previewsink );
	if( !gst_element_link_many( pPrev->m_prevsinkq, pPrev->m_previewsink, NULL ) )
	{
		CSIO_LOG( eLogLevel_error,  "Preview: Cannot re-link sink" );
	}

	if( (gst_element_set_state( pPrev->m_previewsink, GST_STATE_PLAYING)) == GST_STATE_CHANGE_FAILURE )
	{
	   CSIO_LOG(eLogLevel_error,  "Preview: Cannot restart sink" );
	}
	else
		CSIO_LOG(eLogLevel_verbose,  "Preview: restarted sink" );

	if(!pPrev->m_bInstallSink)
	{
		pPrev->m_bWindowClosed = true;
		pPrev->wakeup();
	}

	return GST_PAD_PROBE_OK;
}
