/**
 * Copyright (C) 2016 to the present, Crestron Electronics, Inc.
 * All rights reserved.
 * No part of this software may be reproduced in any form, machine
 * or natural, without the express written consent of Crestron Electronics.
 *
 * \file        cresCamera.cpp
 *
 * \brief       Implementation of camera out
 *
 * \author      John Freeman
 *
 * \date        12/06/2016
 *
 * \note
 */

///////////////////////////////////////////////////////////////////////////////
#include <stdio.h>
#include <stdlib.h>
#include <sys/stat.h>
#include <string.h>
#include <unistd.h>
#include <gst/app/gstappsink.h>
#include <gst/app/gstappsrc.h>
#include "cresCamera.h"

static CStreamCamera *g_cresCam = NULL;
CStreamCamera::CStreamCamera(void * arg):
m_context(NULL), m_loop(NULL), m_pipeline(NULL),m_source(NULL),m_srcfilter(NULL),m_teeQ(NULL),m_sink(NULL),m_bStopInProgress(false),
m_bExit(false),m_bus(NULL),m_bus_id(-1),m_cam_q(NULL),m_tee(NULL),m_id_probe_cam_q(0),m_bPushRawFrames(false),
m_teefilter(NULL),m_snapobj(NULL),m_preview(NULL),m_pMgr(NULL)
{
	g_cresCam = this;
	m_parent = arg;

    m_snapobj = new SnapShot();
    m_preview = new Preview();

    mLock  = new Mutex();
    mTLock = new Mutex();
}

CStreamCamera::~CStreamCamera()
{
	StopCamera(m_parent);

    if(m_snapobj)
    	delete m_snapobj;

    if(m_preview)
    	delete m_preview;

    if(mLock)
        delete mLock;

    if(mTLock)
    	delete mTLock;
}

static gboolean cb_GstMsgHandler(GstBus *bus, GstMessage *msg, void *arg)
{
	CStreamCamera *pCam = (CStreamCamera *) arg;

	GError    *err;
	gchar     *debug_info = NULL;
	GMainLoop *pMainloop = NULL;
	GstStateChangeReturn result;

	switch (GST_MESSAGE_TYPE(msg))
	{
		case GST_MESSAGE_NEW_CLOCK:
			CSIO_LOG(eLogLevel_verbose, "cresCamera: New clock selected");
			break;

		case GST_MESSAGE_CLOCK_LOST:
			CSIO_LOG(eLogLevel_verbose, "cresCamera: Clock lost, selecting a new one");
			if(pCam)
			{
				if(pCam->m_pipeline)
				{
					result = gst_element_set_state( pCam->m_pipeline, GST_STATE_PAUSED );
					if(result != GST_STATE_CHANGE_SUCCESS)
					{
						CSIO_LOG(eLogLevel_verbose, "cresCamera: Lost clock - setting pipeline to pause returned %d", result);
					}

					result = gst_element_set_state( pCam->m_pipeline, GST_STATE_PLAYING );
					if(result != GST_STATE_CHANGE_SUCCESS)
					{
						CSIO_LOG(eLogLevel_debug, "cresCamera: Lost clock - setting pipeline to play returned %d", result);
					}
				}
			}
			break;


		case GST_MESSAGE_WARNING:
			gst_message_parse_warning( msg, &err, &debug_info );
			if( debug_info )
			{
				g_free( debug_info );
			}

			g_clear_error( &err );
			break;

		case GST_MESSAGE_ERROR:
			gst_message_parse_error( msg, &err, &debug_info );

			CSIO_LOG(eLogLevel_error,  "cresCamera: Error received from element %s: %s: error code = %d",
			GST_OBJECT_NAME(msg->src), err->message, err->code );
			CSIO_LOG(eLogLevel_error,  "cresCamera: Debugging information: %s\n", debug_info ? debug_info : "none" );

			g_clear_error( &err );
			g_free( debug_info );
			if(pCam)
			{
				if( pCam->m_loop )
					g_main_loop_quit( pCam->m_loop );
			}
			break;

		case GST_MESSAGE_EOS:
			CSIO_LOG(eLogLevel_debug,  "cresCamera: End-Of-Stream" );
			if(pCam)
			{
				GstPad *sinkpad;
				sinkpad = gst_element_get_static_pad (pCam->m_source, "sink");
				gst_pad_send_event (sinkpad, gst_event_new_eos ());
				gst_object_unref (sinkpad);

				if( pCam->m_loop )
					g_main_loop_quit( pCam->m_loop );
			}
			break;

		case GST_MESSAGE_STATE_CHANGED:
			if( GST_MESSAGE_SRC(msg) == GST_OBJECT( pCam->m_pipeline ) )
			{
				GstState old_state, new_state, pending_state;
				gst_message_parse_state_changed( msg, &old_state, &new_state,
				&pending_state );
				CSIO_LOG(eLogLevel_verbose, "cresCamera: Pipeline state changed from %s to %s",
				gst_element_state_get_name( old_state ),
				gst_element_state_get_name( new_state) );
				if( (old_state == GST_STATE_PAUSED) && (new_state == GST_STATE_PLAYING) )
				{
					CSIO_LOG(eLogLevel_verbose, "cresCamera: Pipeline state changed to playing");
					if(pCam)
					{
						if(pCam->m_pMgr)
						{
							pCam->m_bPushRawFrames = true;
						}
					}
				}
			}
			break;

		case GST_MESSAGE_ELEMENT:
			const GstStructure *structure = gst_message_get_structure (msg);
			const gchar *filename;
			if (gst_structure_has_name (structure, "GstMultiFileSink"))
			{
				if(pCam->m_pMgr)
				{
					filename = gst_structure_get_string (structure, "filename" );
					CSIO_LOG(eLogLevel_verbose, "Streamout: queued %s", filename );
					pCam->m_snapobj->updatelink( filename );
				}
			}

			break;
	}

	return TRUE;
}

static GstPadProbeReturn cb_have_raw_data (GstPad *pad, GstPadProbeInfo *buffer, gpointer user_data)
{
	CStreamCamera *pCam = (CStreamCamera *) user_data;

	if( pCam->m_bPushRawFrames )
	{
		pCam->lockCam();

		if(pCam->m_pMgr)
		{
//			CSIO_LOG(eLogLevel_verbose, "cresCamera: cb_have_raw_data - push raw frame..." );
			pCam->m_pMgr->saveRawFrame( pad, buffer, pCam );
		}

		pCam->unlockCam();
	}

    return(GST_PAD_PROBE_OK);
}

void CStreamCamera::createMainContext(void)
{
	m_context = g_main_context_new ();
	g_main_context_push_thread_default(m_context);
}

void CStreamCamera::freeMainContext(void)
{
    if(m_context)
	{
        g_main_context_pop_thread_default(m_context);
        g_main_context_unref (m_context);
        m_context = NULL;
	}
}

GMainLoop *CStreamCamera::createMainLoop(void)
{
	if(m_context)
	{
		return( g_main_loop_new(m_context, FALSE) );
	}
	else
	{
		CSIO_LOG(eLogLevel_error, "cresCamera: context is NULL" );
		return( NULL );
	}
}

bool CStreamCamera::create(CStreamoutProject *pMgr)
{
	bool bRtn = true;

    if( pthread_create(&pMgr->m_tCameraId, NULL, cameraThread, pMgr) )
    {
    	CSIO_LOG(eLogLevel_error, "cresCamera: Failed to start camera server");
    	bRtn = false;
    }

    return( bRtn );
}

void CStreamCamera::remove(CStreamoutProject *pMgr)
{
	if(pMgr->m_cameraobj)
	{
		pMgr->m_cameraobj->stop();

    	int   iRtn;
    	void* tResults;

    	CSIO_LOG(eLogLevel_verbose,  "cresCamera: wait for thread to exit" );
        iRtn = pthread_join( pMgr->m_tCameraId, &tResults );
        CSIO_LOG(eLogLevel_verbose,  "cresCamera: thread exited. Status = %d", iRtn );
	}
	else
	{
		CSIO_LOG(eLogLevel_error, "Camera is NULL\n");
	}
}

void *cameraThread(void *arg)
{
	CStreamoutProject *pMgr = (CStreamoutProject *) arg;

	pMgr->m_cameraobj->start();

	CSIO_LOG(eLogLevel_verbose, "Exiting Camera thread...\n");
	return(NULL);
}

int StartCamera(void *parent)
{
	CStreamoutProject *pMgr = (CStreamoutProject *) parent;

	int iRtn = 0;

    if( pthread_create(&pMgr->m_tCameraId, NULL, cameraThread, pMgr) )
    {
    	CSIO_LOG(eLogLevel_error, "cresCamera: Failed to start camera server");
    	iRtn = -1;
    }

    return( iRtn );
}

void StopCamera(void *parent)
{
	CStreamoutProject *pMgr = (CStreamoutProject *) parent;

	if(pMgr->m_cameraobj)
	{
		pMgr->m_cameraobj->stop();

    	int   iRtn;
    	void* tResults;

    	CSIO_LOG(eLogLevel_debug,  "cresCamera: wait for thread to exit" );
        iRtn = pthread_join( pMgr->m_tCameraId, &tResults );
        CSIO_LOG(eLogLevel_debug,  "cresCamera: thread exited. Status = %d", iRtn );
	}
	else
	{
		CSIO_LOG(eLogLevel_error, "Camera is NULL\n");
	}
}

int CStreamCamera::start(void)
{
	const int c_usleep_500ms = 500000;
	int  iStatus  = 0;

	CSIO_LOG(eLogLevel_verbose,  "cresCamera: startCamera..." );

	while( !m_bExit )
	{
		createMainContext();
		if( (m_loop = createMainLoop()) )
		{
			m_pipeline  = gst_pipeline_new( NULL );
			m_source    = gst_element_factory_make( "ahcsrc",       NULL );
			m_srcfilter = gst_element_factory_make( "capsfilter",   NULL );
			m_crop      = gst_element_factory_make( "videocrop",    NULL );
			m_cam_q     = gst_element_factory_make( "queue",        NULL );
			m_tee       = gst_element_factory_make( "tee",          NULL );
			m_teeQ      = gst_element_factory_make( "queue",        NULL );
			m_sink      = gst_element_factory_make( "fakesink",     NULL );

			if( !m_pipeline || !m_source || !m_srcfilter || !m_crop || !m_cam_q || !m_tee || !m_teeQ || !m_sink )
			{
				iStatus = -1;
				CSIO_LOG(eLogLevel_error, "cresCamera: Cannot create pipeline" );
			}
			else
			{
				g_object_set( G_OBJECT(m_srcfilter), "caps",
				gst_caps_new_simple(
					 "video/x-raw",
					 "format",    G_TYPE_STRING, "NV21",
					 "width",     G_TYPE_INT, c_camera_width,
					 "height",    G_TYPE_INT, c_camera_height,
					 "framerate", GST_TYPE_FRACTION, c_camera_framerate, 1,
					  NULL), NULL );

				g_object_set( G_OBJECT(m_crop), "top", (c_camera_height-c_cropped_height)/2, NULL);
				g_object_set( G_OBJECT(m_crop), "bottom", (c_camera_height-c_cropped_height)/2, NULL);
				g_object_set( G_OBJECT(m_crop), "left", 0, NULL);
				g_object_set( G_OBJECT(m_crop), "right", 0, NULL);

				g_object_set( G_OBJECT(m_cam_q), "max-size-bytes", (1920*1080*3), NULL );

				g_object_set( G_OBJECT(m_sink), "sync", false, NULL );

		 	  //create data extraction point
				GstPad *pad;
				pad = gst_element_get_static_pad (m_teeQ, "sink");
				m_id_probe_cam_q = gst_pad_add_probe (pad, GST_PAD_PROBE_TYPE_BUFFER,
				(GstPadProbeCallback) cb_have_raw_data, this, NULL);
				gst_object_unref (pad);

				gst_bin_add_many( GST_BIN( m_pipeline ), m_source, m_srcfilter, m_crop, m_cam_q, m_tee, m_teeQ, m_sink, NULL );
			}

			if( !iStatus )
			{
				m_bus    = gst_pipeline_get_bus( GST_PIPELINE( m_pipeline ) );
				m_bus_id = gst_bus_add_watch( m_bus, cb_GstMsgHandler, this );

				if(!gst_element_link_many( m_source, m_srcfilter, m_crop, m_cam_q, m_tee, m_teeQ, m_sink, NULL ))
				{
					CSIO_LOG(eLogLevel_error,  "cresCamera: Cannot link elements" );
					iStatus = -2;
				}
				else
				{
					m_snapobj->add(this);
					m_preview->add(this);

					if( (gst_element_set_state( m_pipeline, GST_STATE_PLAYING)) == GST_STATE_CHANGE_FAILURE )
					   CSIO_LOG(eLogLevel_error,  "cresCamera: Cannot start pipeline" );
					else
					{
						CSIO_LOG(eLogLevel_verbose,  "cresCamera: camera started" );
						m_bPushRawFrames = true;
					}

					g_main_loop_run( m_loop );
					CSIO_LOG(eLogLevel_verbose,  "cresCamera: exited camera mainloop" );
				}

				cleanup();
				usleep( c_usleep_500ms );
			}
		}
	}

	freeMainContext();

	return(iStatus);
}

void CStreamCamera::cleanup(void)
{
	CSIO_LOG(eLogLevel_verbose,  "cresCamera: cleanup" );

	lockCam();

	if(m_pipeline != NULL)
	{
		gst_element_set_state( m_pipeline, GST_STATE_NULL );

        GstStateChangeReturn rtn;
		gst_object_unref( m_pipeline );
	}

    if( m_bus != NULL )
	{
		if( !gst_bus_remove_watch( m_bus) )
		{
			CSIO_LOG(eLogLevel_error, "cresCamera: Cannot remove msg handler %d", m_bus_id);
		}

		gst_object_unref( m_bus );
		m_bus    = NULL;
		m_bus_id = -1;
	}

    if(m_loop != NULL)
    {
        g_main_loop_unref( m_loop );
    }

    if(m_cam_q)
    {
        GstPad *pad = gst_element_get_static_pad(m_cam_q, "sink");
        if (pad != NULL)
        {
            gst_pad_remove_probe(pad, m_id_probe_cam_q);
            gst_object_unref(pad);
        }
    }

    if(m_preview)
    	m_preview->drop();

    if(m_snapobj)
    	m_snapobj->drop();

    m_loop     = NULL;
    m_pipeline = NULL;
    m_source   = NULL;
    m_crop     = NULL;
    m_cam_q    = NULL;
	m_srcfilter = NULL;
	m_tee       = NULL;
	m_teeQ      = NULL;
	m_sink      = NULL;

	m_bPushRawFrames = false;

	unlockCam();

	CSIO_LOG(eLogLevel_verbose,  "cresCamera: cleanup done" );
}

int CStreamCamera::stop(void)
{
	int iRtn = 0;

	lockCam();
	m_bStopInProgress = true; //stop pushing frames
	m_bExit = true;
	if( m_loop )
	{
		g_main_loop_quit( m_loop );
	}
	else
	{
		CSIO_LOG(eLogLevel_verbose,  "cresCamera: loop is NULL" );
		iRtn = -1;
	}

	unlockCam();

	return(iRtn);
}

void CStreamCamera::startPreview(void* window)
{
	lockCam();
	if (m_preview)
	{
		m_preview->start( window );
	}
	else
	{
		CSIO_LOG(eLogLevel_error, "cresCamera: preview is NULL");
	}

	unlockCam();
}

void CStreamCamera::pausePreview(void* window)
{
	lockCam();

	if (m_preview)
	{
		m_preview->pause( window );
	}
	else
	{
		CSIO_LOG(eLogLevel_error, "cresCamera: preview is NULL");
	}

	unlockCam();
}

void CStreamCamera::stopPreview(void* window)
{
	lockCam();

	if (m_preview)
	{
		m_preview->stop( window );
	}
	else
	{
		CSIO_LOG(eLogLevel_error, "cresCamera: preview is NULL");
	}

	unlockCam();
}

int CStreamCamera::waitForPreviewAvailable(int timeout_sec)
{
	int rtn = 0;

	if (m_preview)
	{
		rtn = m_preview->waitForPreviewAvailable(timeout_sec);
	}
	else
	{
		CSIO_LOG(eLogLevel_error, "cresCamera: preview is NULL");
	}

	return(rtn);
}

int CStreamCamera::waitForPreviewClosed(int timeout_sec)
{
	int rtn = 0;

	if (m_preview)
	{
		rtn = m_preview->waitForPreviewClosed(timeout_sec);
	}
	else
	{
		CSIO_LOG(eLogLevel_error, "cresCamera: preview is NULL");
	}

	return(rtn);
}

int WaitForPreviewAvailable(int timeout_sec)
{
	int rtn = -1;

	if( (g_cresCam != NULL) && (g_cresCam->m_preview) )
		rtn = g_cresCam->waitForPreviewAvailable( timeout_sec );
	else
		CSIO_LOG(eLogLevel_error, "cresCamera: cannot wait. Preview is NULL");

	return(rtn);
}

void StartPreview(void* window)
{
	if( (g_cresCam != NULL) && (g_cresCam->m_preview) )
		g_cresCam->startPreview( window);
	else
		CSIO_LOG(eLogLevel_error, "cresCamera: cannot start. Preview is NULL");
}

void PausePreview(void* window)
{
	if( (g_cresCam != NULL) && (g_cresCam->m_preview) )
		g_cresCam->pausePreview( window );
	else
		CSIO_LOG(eLogLevel_error, "cresCamera: cannot pause. Preview is NULL");
}

void StopPreview(void* window)
{
	if( (g_cresCam != NULL) && (g_cresCam->m_preview) )
		g_cresCam->stopPreview( window );
	else
		CSIO_LOG(eLogLevel_error, "cresCamera: cannot stop. Preview is NULL");
}

int WaitForPreviewClosed(int timeout_sec)
{
	int iRtn = 0;

	if( (g_cresCam != NULL) && (g_cresCam->m_preview) )
		iRtn = g_cresCam->waitForPreviewClosed(timeout_sec);
	else
	{
		CSIO_LOG(eLogLevel_error, "cresCamera: cannot wait for window to close. Preview is NULL");
		iRtn = -1;
	}

	return( iRtn );
}

int StartSnapShot(CStreamoutManager *pMgr)
{
	int rtn = 0;

	if( (g_cresCam != NULL) && (g_cresCam->m_snapobj))
	{
		g_cresCam->m_snapobj->start(pMgr,g_cresCam);
	}
	else
	{
		CSIO_LOG(eLogLevel_error, "cresCamera: cannot start. Snapshot is NULL");
		rtn = -1;
	}

	return( rtn );
}

int StopSnapShot(CStreamoutManager *pMgr)
{
	int rtn = 0;

	if( (g_cresCam != NULL) && g_cresCam->m_snapobj )
	{
		g_cresCam->lockCam();
		g_cresCam->m_snapobj->stop();
		g_cresCam->m_snapobj->m_bStopInProgress = true; //stop pushing frames
		g_cresCam->m_bPushRawFrames = false;
		g_cresCam->m_pMgr = NULL;
		g_cresCam->unlockCam();
	}
	else
	{
		CSIO_LOG(eLogLevel_error, "cresCamera: cannot stop. Snapshot is NULL");
		rtn = -1;
	}

	return( rtn );
}

void UpdateSnapshotName(void)
{
	if( (g_cresCam != NULL) && g_cresCam->m_snapobj )
	{
		g_cresCam->m_snapobj->updateSnapShotName();
	}
	else
	{
		CSIO_LOG(eLogLevel_error, "cresCamera: cannot update snapshot name. Snapshot is NULL");
	}
}
