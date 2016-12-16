/**
 * Copyright (C) 2016 to the present, Crestron Electronics, Inc.
 * All rights reserved.
 * No part of this software may be reproduced in any form, machine
 * or natural, without the express written consent of Crestron Electronics.
 * 
 * \file        cresStreamOutManager.cpp
 *
 * \brief       Implementation of stream out
 *
 * \author      John Cheng
 *
 * \date        7/5/2016
 *
 * \note
 */

///////////////////////////////////////////////////////////////////////////////

#include <stdlib.h>
#include "cresStreamOutManager.h"
#define CRES_OVERLOAD_MEDIA_CLASS 1
#define CRES_UNPREPARE_MEDIA      1

///////////////////////////////////////////////////////////////////////////////
// Allow file to override canned pipeline, for debugging...
// The following pipeline worked!
// ( videotestsrc is-live=1 ! jpegenc ! rtpjpegpay name=pay0 pt=96 )
static bool gst_rtsp_server_get_pipeline(char * pDest, int destSize)
{
    FILE * pf;

    pf = fopen("/dev/shm/rtsp_server_pipeline", "r");
    if(pf)
    {
        fgets(pDest, destSize, pf);
        fclose(pf);
        return true;
    }

    return false;
}

static GstPadProbeReturn
cb_have_raw_data (GstPad *pad, GstPadProbeInfo *buffer, gpointer user_data)
{
	CStreamoutManager *pMgr = (CStreamoutManager *) user_data;
	GstPadProbeReturn ret = GST_PAD_PROBE_DROP;

	if( pMgr->m_bPushRawFrames )
	{
		pMgr->saveRawFrame( pad, buffer );
		ret = GST_PAD_PROBE_OK;
	}

    return(ret);
}

/* called when a new media pipeline is constructed. We can query the
 * pipeline and configure our ahcsrc */
static void
media_configure (GstRTSPMediaFactory * factory, GstRTSPMedia * media,
    gpointer user_data)
{
  GstElement *element;
  GstElement *ele;
  GstPad *pad;

  /* get the element used for providing the streams of the media */
  element = gst_rtsp_media_get_element (media);
  gchar * n = gst_element_get_name(element);
  CSIO_LOG(eLogLevel_debug, "Streamout: element name[%s] of media[0x%x]",n,media);

  //work on the first queue
  {
      /* get our queue with the name property */
      ele = gst_bin_get_by_name_recurse_up (GST_BIN (element), "ahcsrc_q");
      if(ele)
      {
		  CSIO_LOG(eLogLevel_debug, "Streamout: get_by_name ahcsrc_q[%p]",ele);

		  //pass our q back to manager
		  ((CStreamoutManager*)user_data)->m_ahcsrc_q = ele;
          g_object_set( G_OBJECT(((CStreamoutManager*)user_data)->m_ahcsrc_q), "max-size-bytes", (1920*1080*3), NULL );

		  //get the src pad of the queue
		  pad = gst_element_get_static_pad (ele, "sink");

		  //create probe to the pad
		  ((CStreamoutManager*)user_data)->m_id_probe_ahcsrc_q = gst_pad_add_probe (pad, GST_PAD_PROBE_TYPE_BUFFER,
																(GstPadProbeCallback) cb_have_raw_data, user_data, NULL);
		  gst_object_unref (pad);
		  gst_object_unref (ele);
      }
  }

  CSIO_LOG(eLogLevel_debug, "Streamout: set media reusable to true media[%p]",media);
  gst_rtsp_media_set_reusable (media, TRUE);

  //pass media back to manager
  ((CStreamoutManager*)user_data)->m_pMedia = media;

  ((CresRTSPMedia *)media)->m_loop = ((CStreamoutManager*)user_data)->m_loop;

  gst_object_unref (element);
  g_free(n);
}

static GstRTSPFilterResult
filter_cb (GstRTSPStream *stream, GstRTSPStreamTransport *trans,gpointer user_data)
{
    CSIO_LOG(eLogLevel_info, "Streamout: filter_cb-------stream[0x%x]---",stream);
    return GST_RTSP_FILTER_REMOVE;
}

/**********************CStreamoutManager class implementation***************************************/
CStreamoutManager::CStreamoutManager():
m_clientConnCnt(0),m_loop(NULL),m_main_loop_is_running(0),
m_pMedia(NULL),m_ahcsrc_q(NULL),m_id_probe_ahcsrc_q(0),m_snapobj(NULL),m_bNeedData(false),
m_teePipeline(NULL),m_teeSource(NULL),m_tee(NULL),m_teeLoop(NULL),m_bStopTeeInProgress(false),
m_bExit(false),m_sink(NULL),m_bus(NULL),m_bus_id(-1),m_bExitClient(false),m_clientPipeline(NULL),
m_clientLoop(NULL),m_clientSink(NULL),m_bPushRawFrames(false),m_bContinueTee(false),m_ahcsrc(NULL)
{
    m_StreamoutEvent  = new CStreamoutEvent();

    m_StreamoutEventQ = new CStreamoutEventRingBuffer(EVNT_DEFAULT_QUEUE_SIZE);

    m_snapobj = new SnapShot(this);

    m_preview = new Preview(this);

    mLock            = new Mutex();
    mTLock           = new Mutex();

    if(!m_StreamoutEvent || !m_StreamoutEventQ || !mLock)
        CSIO_LOG(eLogLevel_error, "--Streamout: CStreamoutManager malloc failed:[0x%x][0x%x][0x%x]",\
                m_StreamoutEvent,m_StreamoutEventQ,mLock);
}

CStreamoutManager::~CStreamoutManager()
{
    if(m_StreamoutEvent)
        delete m_StreamoutEvent;

    if(m_StreamoutEventQ)
        delete m_StreamoutEventQ;

    if(m_snapobj)
    	delete m_snapobj;

    if(m_preview)
    	delete m_preview;

    if(mLock)
        delete mLock;

    if(mTLock)
    	delete mTLock;

}
void CStreamoutManager::DumpClassPara(int level)
{
    CSIO_LOG(eLogLevel_info, "---Streamout: ThredId 0x%x", (unsigned long int)getThredId());
    CSIO_LOG(eLogLevel_info, "---Streamout: m_debugLevel %d", m_debugLevel);

    CSIO_LOG(eLogLevel_info, "---Streamout: m_parent 0x%x", m_parent);
    CSIO_LOG(eLogLevel_info, "---Streamout: m_clientConnCnt %d", m_clientConnCnt);
    CSIO_LOG(eLogLevel_info, "---Streamout: m_main_loop_is_running %d", m_main_loop_is_running);

    CSIO_LOG(eLogLevel_info, "---Streamout: m_loop 0x%x", m_loop);
    CSIO_LOG(eLogLevel_info, "---Streamout: m_pMedia [0x%x]",m_pMedia);

    CSIO_LOG(eLogLevel_info, "---Streamout: m_ahcsrc_q [0x%x]",m_ahcsrc_q);
    CSIO_LOG(eLogLevel_info, "---Streamout: m_id_probe_ahcsrc_q [%d]",m_id_probe_ahcsrc_q);

    CSIO_LOG(eLogLevel_info, "---Streamout: m_rtsp_port %s", m_rtsp_port);
    CSIO_LOG(eLogLevel_info, "---Streamout: m_res_x %s", m_res_x);
    CSIO_LOG(eLogLevel_info, "---Streamout: m_res_y %s", m_res_y);
    CSIO_LOG(eLogLevel_info, "---Streamout: m_frame_rate %s", m_frame_rate);

    CSIO_LOG(eLogLevel_info, "---Streamout: m_bit_rate %s", m_bit_rate);
    CSIO_LOG(eLogLevel_info, "---Streamout: m_iframe_interval %s", m_iframe_interval);
}

//overloaded from base
void CStreamoutManager::exitThread()
{
    CSIO_LOG(m_debugLevel, "Streamout: try to quit g_main_loop[0x%x]",m_loop);
    m_forceThreadExit = 1;

    if(m_loop)
    {
        if(m_factory)
            gst_rtsp_media_factory_set_eos_shutdown (m_factory, TRUE);

        if(m_ahcsrc_q)
        {
            GstPad *pad = gst_element_get_static_pad(m_ahcsrc_q, "sink");
            if (pad != NULL)
            {
                gst_pad_remove_probe(pad, m_id_probe_ahcsrc_q);
                gst_object_unref(pad);
            }
        }

        g_main_loop_quit(m_loop);
        CSIO_LOG(m_debugLevel, "Streamout: g_main_loop_quit returned");
    }
    else
    {
        CSIO_LOG(m_debugLevel, "Streamout: g_main_loop is not running");
    }
}
void* CStreamoutManager::ThreadEntry()
{
    char pipeline[1024];
    guint server_id;
    GMainContext*        context = NULL;
    GstRTSPServer *      server  = NULL;
    GstRTSPMountPoints * mounts  = NULL;
    GSource *  server_source     = NULL;

    m_factory = NULL;

    //create new context
    context = g_main_context_new ();
    g_main_context_push_thread_default(context);
    CSIO_LOG(m_debugLevel,  "Streamout: creste new context: 0x%x\n", context );
    if(!context)
    {
        CSIO_LOG(eLogLevel_error, "Streamout: Failed to create rtsp server context");
        goto exitThread;
    }

    m_loop = g_main_loop_new (context, FALSE);   // called from CStreamer::execute
    if(!m_loop)
    {
        CSIO_LOG(eLogLevel_error, "Streamout: Failed to create rtsp server loop");
        goto exitThread;
    }
    server = gst_rtsp_server_new();
    if(!server)
    {
        CSIO_LOG(eLogLevel_error, "Streamout: Failed to create rtsp server");
        goto exitThread;
    }

    //setup listening port
    CSIO_LOG(m_debugLevel, "Streamout: set_service to port:%s",m_rtsp_port);
    gst_rtsp_server_set_service (server, m_rtsp_port);

    /* get the mount points for this server, every server has a default object
    * that be used to map uri mount points to media factories */
    mounts = gst_rtsp_server_get_mount_points (server);
    if(!mounts)
    {
        CSIO_LOG(eLogLevel_error, "Streamout: Failed to create server mounts");
        goto exitThread;
    }
    m_factory = gst_rtsp_media_factory_new ();
    if(!m_factory)
    {
        CSIO_LOG(eLogLevel_error, "Streamout: Failed to create factory");
        goto exitThread;
    }

    if(gst_rtsp_server_get_pipeline(pipeline, sizeof(pipeline)) == false)
    {
        // Because camera on x60 is front-facing, it is mirrored by default for the preview.
        // Old default pipeline (with video flipping) "( ahcsrc ! videoflip method=4 ! videoconvert ! %s ! rtph264pay name=pay0 pt=96 )"
        // Enabled NV21 pixel format in libgstreamer_android.so, don't need videoconvert anymore.
        //"( ahcsrc ! videoconvert ! %s ! rtph264pay name=pay0 pt=96 )"
        // Queue before encoder reduces stream latency.
        snprintf(pipeline, sizeof(pipeline), "( ahcsrc name=cressrc ! "
                                             "video/x-raw,width=%s,height=%s,framerate=%s/1 ! queue name=ahcsrc_q ! "
                                             "%s bitrate=%s i-frame-interval=%s ! "
                                             "rtph264pay name=pay0 pt=96 )",
                                             m_res_x,m_res_y,m_frame_rate,
                                             product_info()->video_encoder_string,
                                             m_bit_rate,m_iframe_interval);
    }
    CSIO_LOG(m_debugLevel, "Streamout: rtsp server pipeline: [%s]", pipeline);
    gst_rtsp_media_factory_set_launch (m_factory, pipeline);


    if (m_multicast_enable)
    {
    	if (m_multicast_address)	// TODO: Also check that multicast address is valid
    	{
    		uint32_t ip = 0;
    		int ret = inet_pton(AF_INET, m_multicast_address, &ip);
    		ip = ntohl(ip); // put host byte order
    		if ( (ret == 1) && ((ip >> 28) == 0xe) )
    		{
    			/* make a new address pool for multicast */
    			GstRTSPAddressPool *pool = gst_rtsp_address_pool_new ();
    			gst_rtsp_address_pool_add_range (pool,
    					m_multicast_address, m_multicast_address, 11000, 12000, 64);	// Setting ttl to fixed 64, and fixed port range
    			gst_rtsp_media_factory_set_address_pool (m_factory, pool);
    			/* only allow multicast */
    			gst_rtsp_media_factory_set_protocols (m_factory,
    					GST_RTSP_LOWER_TRANS_UDP_MCAST);
    			g_object_unref (pool);
    		}
    		else
    			CSIO_LOG(eLogLevel_error, "Streamout: Invalid multicast address provided");
    	}
    	else
    		CSIO_LOG(eLogLevel_error, "Streamout: No multicast address provided");
    }


    /* notify when our media is ready, This is called whenever someone asks for
       * the media and a new pipeline with our appsrc is created */
    g_signal_connect (m_factory, "media-configure", (GCallback) media_configure,this);

    gst_rtsp_media_factory_set_shared (m_factory, TRUE);

    // Reduce stream latency.
    gst_rtsp_media_factory_set_latency (m_factory, 10);	

#ifdef CRES_OVERLOAD_MEDIA_CLASS
    gst_rtsp_media_factory_set_media_gtype (m_factory, CRES_TYPE_RTSP_MEDIA);
#endif

    if (m_stream_name)
    {
	    char mountPoint [512];
        sprintf(mountPoint, "/%s.sdp", m_stream_name);
    	gst_rtsp_mount_points_add_factory (mounts, mountPoint, m_factory);
    }
    else
    	gst_rtsp_mount_points_add_factory (mounts, "/camera.sdp", m_factory);
    g_object_unref (mounts);

//correct way to create source and attatch to mainloop
    server_source = gst_rtsp_server_create_source(server,NULL,NULL);
    if(server_source)
    {
        CSIO_LOG(m_debugLevel, "Streamout: create_source , server_source [0x%x]", server_source);
    }
    else
    {
        CSIO_LOG(eLogLevel_error, "Streamout: Failed to create_source");
        goto exitThread;
    }
    server_id = g_source_attach (server_source, g_main_loop_get_context(m_loop));
    
    if(server_id)
    {
        CSIO_LOG(m_debugLevel, "Streamout: Attached server to maincontext, server_id %u", server_id);
    }
    else
    {
        CSIO_LOG(eLogLevel_error, "Streamout: Failed to attach server");
        goto exitThread;
    }

    m_main_loop_is_running = 1;
    startTee();
    g_main_loop_run (m_loop);

exitThread:
    /* cleanup */
	stopTee();
#ifdef CRES_UNPREPARE_MEDIA
/*   please check out this bug: https://bugzilla.gnome.org/show_bug.cgi?id=747801
 *   if it is fixed, we should take it. */

    if(m_pMedia)
    {
        gst_rtsp_media_suspend (m_pMedia);

        //remove stream from session before unprepare media
        guint i, n_streams;
        n_streams = gst_rtsp_media_n_streams (m_pMedia);
        CSIO_LOG(m_debugLevel, "Streamout: -------n_streams[%d]---",n_streams);

        for (i = 0; i < n_streams; i++)
        {
            GstRTSPStream *stream = gst_rtsp_media_get_stream (m_pMedia, i);            

            if (stream == NULL)  continue;

            gst_rtsp_stream_transport_filter (stream, filter_cb, NULL);
        }

        CSIO_LOG(m_debugLevel, "Streamout: -------call gst_rtsp_media_unprepare---");
        gst_rtsp_media_unprepare_force (m_pMedia);       
     }
#endif

/* You must use g_source_destroy() for sources added to a non-default main context.  */
    if(server_source)
    {
        /*You must use g_source_destroy() for sources added to a non-default main context.*/
        g_source_destroy (server_source);
        g_source_unref(server_source);
        CSIO_LOG(m_debugLevel, "Streamout: g_source_destroy server_source[0x%x]",server_source);
    }

    if(m_factory) g_object_unref (m_factory);
    if(server) g_object_unref (server);
    if(m_loop) g_main_loop_unref (m_loop);

    //need to create a cleanup function and call here
    m_loop = NULL;
    m_main_loop_is_running = 0;
    m_pMedia = NULL;;

    if(context)
    {
        g_main_context_pop_thread_default(context);
        g_main_context_unref (context);
        context = NULL;
    }

    CSIO_LOG(m_debugLevel, "Streamout: jni_start_rtsp_server ended------");

    //thread exit here
    m_ThreadIsRunning = 0;

    return NULL;
}

void CStreamoutManager::setSnapshotName(char* name)
{
	lockTee();

	if (m_snapobj)
	{
		if( name != NULL)
		{
			m_snapobj->m_snapshot_name_updated = true;
			strncpy( m_snapshot_name, name, MAX_STR_LEN );
		}
		else
			CSIO_LOG(eLogLevel_error, "SnapShot: setSnapshotName - name is NULL\n");
	}
	else
	{
		if( name != NULL)
			strncpy( m_snapshot_name, name, MAX_STR_LEN );
	}

	unlockTee();
}

char* CStreamoutManager::getSnapshotName(void)
{
	lockTee();
	return( m_snapshot_name );
	unlockTee();
}

void cb_teeNeedData (GstElement *appsrc, guint unused_size, gpointer user_data)
{
	//CSIO_LOG(eLogLevel_verbose, "Streamout: cb_need_data...");
	CStreamoutManager *pMgr = (CStreamoutManager *) user_data;

	pMgr->m_bNeedData = true;
}

void CStreamoutManager::startPreview(void* window)
{

	lockTee();
	if (m_preview)
	{
		m_preview->start( window );
	}
	else
	{
		CSIO_LOG(eLogLevel_error, "Preview: preview is NULL");
	}

	unlockTee();
}

void CStreamoutManager::pausePreview(void* window)
{
	lockTee();

	if (m_preview)
	{
		m_preview->pause( window );
	}
	else
	{
		CSIO_LOG(eLogLevel_error, "Preview: preview is NULL");
	}

	unlockTee();
}

void CStreamoutManager::stopPreview(void* window)
{
	lockTee();

	if (m_preview)
	{
		m_preview->stop( window );
	}
	else
	{
		CSIO_LOG(eLogLevel_error, "Preview: preview is NULL");
	}

	unlockTee();
}

int CStreamoutManager::waitForPreviewAvailable(int timeout_sec)
{
	int rtn = 0;

	if (m_preview)
	{
		rtn = m_preview->waitForPreviewAvailable(timeout_sec);
	}
	else
	{
		CSIO_LOG(eLogLevel_error, "Preview: preview is NULL");
	}

	return(rtn);
}

void cb_teeEnoughData (GstElement *appsrc, guint unused_size, gpointer user_data)
{
	//CSIO_LOG(eLogLevel_verbose, "Streamout: cb_enough_data...");
	CStreamoutManager *pMgr = (CStreamoutManager *) user_data;

	pMgr->m_bNeedData = false;
}

gboolean cb_teeGstMsgHandler(GstBus *bus, GstMessage *msg, void *arg)
{
	CStreamoutManager *pMgr = (CStreamoutManager *) arg;

	GError    *err;
	gchar     *debug_info = NULL;
	GMainLoop *pMainloop = NULL;

	switch (GST_MESSAGE_TYPE(msg))
	{
		case GST_MESSAGE_NEW_CLOCK:
			CSIO_LOG(eLogLevel_verbose, "Streamout: New clock selected");
			break;

		case GST_MESSAGE_CLOCK_LOST:
			CSIO_LOG(eLogLevel_verbose, "Streamout: Clock lost, selecting a new one");
			if(pMgr)
			{
				if(pMgr->m_teePipeline)
				{
					GstStateChangeReturn result;
					result = gst_element_set_state( pMgr->m_teePipeline, GST_STATE_PAUSED );
					if(result != GST_STATE_CHANGE_SUCCESS)
					{
						CSIO_LOG(eLogLevel_verbose, "Streamout: Lost clock - setting pipeline to pause returned %d", result);
					}

					result = gst_element_set_state( pMgr->m_teePipeline, GST_STATE_PLAYING );
					if(result != GST_STATE_CHANGE_SUCCESS)
					{
						CSIO_LOG(eLogLevel_debug, "Streamout: Lost clock - setting pipeline to play returned %d", result);
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

			CSIO_LOG(eLogLevel_error,  "Streamout: Error received from element %s: %s: error code = %d",
			GST_OBJECT_NAME(msg->src), err->message, err->code );
			CSIO_LOG(eLogLevel_error,  "Streamout: Debugging information: %s\n", debug_info ? debug_info : "none" );

			g_clear_error( &err );
			g_free( debug_info );
			if(pMgr)
			{
				if( pMgr->m_teeLoop )
					g_main_loop_quit( pMgr->m_teeLoop );
			}
			break;

		case GST_MESSAGE_EOS:
			CSIO_LOG(eLogLevel_debug,  "Streamout: End-Of-Stream" );
			if(pMgr)
			{
				GstPad *sinkpad;
				sinkpad = gst_element_get_static_pad (pMgr->m_teeSource, "sink");
				gst_pad_send_event (sinkpad, gst_event_new_eos ());
				gst_object_unref (sinkpad);

				if( pMgr->m_teeLoop )
					g_main_loop_quit( pMgr->m_teeLoop );
			}
			break;

		case GST_MESSAGE_STATE_CHANGED:
			if( GST_MESSAGE_SRC(msg) == GST_OBJECT( pMgr->m_teePipeline ) )
			{
				GstState old_state, new_state, pending_state;
				gst_message_parse_state_changed( msg, &old_state, &new_state,
				&pending_state );
				CSIO_LOG(eLogLevel_verbose, "Streamout: Pipeline state changed from %s to %s",
				gst_element_state_get_name( old_state ),
				gst_element_state_get_name( new_state) );
				if( (old_state == GST_STATE_READY) && (new_state == GST_STATE_PAUSED) )
				{
					CSIO_LOG(eLogLevel_verbose, "Streamout: Pipeline state changed to playing");
					if(pMgr)
					{
						pMgr->m_bPushRawFrames = true;
					}
				}
			}
			break;

		case GST_MESSAGE_ELEMENT:
			const GstStructure *structure = gst_message_get_structure (msg);
			const gchar *filename;
			if (gst_structure_has_name (structure, "GstMultiFileSink"))
			{
				filename = gst_structure_get_string (structure, "filename" );
				CSIO_LOG(eLogLevel_verbose, "Streamout: queued %s", filename );
				pMgr->m_snapobj->updatelink( filename );
			}

			break;
	}

	return TRUE;
}

void CStreamoutManager::createMainContext(void)
{
	m_context = g_main_context_new ();
	g_main_context_push_thread_default(m_context);
}

void CStreamoutManager::freeMainContext(void)
{
    if(m_context)
	{
        g_main_context_pop_thread_default(m_context);
        g_main_context_unref (m_context);
        m_context = NULL;
	}
}

GMainLoop *CStreamoutManager::createMainLoop(void)
{
	if(m_context)
	{
		return( g_main_loop_new(m_context, FALSE) );
	}
	else
	{
		CSIO_LOG(eLogLevel_error, "Context is NULL" );
		return( NULL );
	}
}

void* CStreamoutManager::CameraTeeEntry()
{
	const int c_usleep_1s = 1000000;
	const int c_usleep_500ms = 500000;
	const int c_usleep_10ms = 10000;
	int  iStatus  = 0;

	CSIO_LOG(eLogLevel_verbose,  "Starting camera Tee..." );

	while( !m_bExit )
	{
		createMainContext();
		if( (m_teeLoop = createMainLoop()) )
		{
			m_teePipeline = gst_pipeline_new( NULL );
			m_teeSource   = gst_element_factory_make( "appsrc",  NULL );
			m_tee         = gst_element_factory_make( "tee",     NULL );
			m_teeQ        = gst_element_factory_make( "queue",   NULL );
			m_sink        = gst_element_factory_make( "fakesink",NULL );

			if( !m_teePipeline || !m_teeSource || !m_tee || !m_teeQ || !m_sink )
			{
				iStatus = -1;
				CSIO_LOG( eLogLevel_error, "Streamout: Cannot create tee pipeline" );
			}
			else
			{
				gst_bin_add_many( GST_BIN( m_teePipeline ), m_teeSource, m_tee, m_teeQ, m_sink, NULL );
				int width  = atoi(m_res_x);
				int height = atoi(m_res_y);
				int iFramerate = atoi(m_frame_rate);

				CSIO_LOG( eLogLevel_verbose, "Streamout: width = %d, height = %d, framerate = %d\n", width, height, iFramerate );

				g_object_set( G_OBJECT (m_teeSource), "caps",
					gst_caps_new_simple (
					 "video/x-raw",
					 "format",    G_TYPE_STRING, "NV21",
					 "width",     G_TYPE_INT, width,
					 "height",    G_TYPE_INT, height,
					 "framerate", GST_TYPE_FRACTION, 0, 1, //variable
					  NULL), NULL );

				m_snapobj->add();
				m_preview->add();

				if( !gst_element_link_many( m_teeSource, m_tee, m_teeQ, m_sink, NULL ))
				{
					CSIO_LOG( eLogLevel_error,  "Streamout: Cannot link tee elements." );
					iStatus = -2;
				}
				else
				{
					g_object_set(G_OBJECT (m_teeSource),
					"stream-type", 0,
					"format",      GST_FORMAT_TIME, NULL);

					m_bus    = gst_pipeline_get_bus( GST_PIPELINE( m_teePipeline ) );
					m_bus_id = gst_bus_add_watch( m_bus, cb_teeGstMsgHandler, this );

					g_signal_connect( m_teeSource, "need-data",   G_CALLBACK (cb_teeNeedData),   this);
					g_signal_connect( m_teeSource, "enough_data", G_CALLBACK (cb_teeEnoughData), this);

					m_bPushRawFrames = true;

					if( (gst_element_set_state( m_teePipeline, GST_STATE_PLAYING)) == GST_STATE_CHANGE_FAILURE )
					   CSIO_LOG(eLogLevel_error,  "Streamout: Cannot start tee pipeline" );
					else
					{
						CSIO_LOG(eLogLevel_debug,  "Streamout: Tee server started" );
					}

					g_main_loop_run( m_teeLoop );
					CSIO_LOG(eLogLevel_verbose,  "Streamout: exited tee mainloop" );

					cleanup();
					usleep( c_usleep_500ms );
				}
			}
		}

		freeMainContext();
		usleep( c_usleep_500ms );
	}

	CSIO_LOG(eLogLevel_debug, "Exiting Tee thread");
	pthread_exit( NULL );
	return(NULL);
}

void CStreamoutManager::cleanup(void)
{
	lockTee();

	CSIO_LOG(eLogLevel_verbose,  "Streamout: cleanup" );

	if(m_teePipeline != NULL)
	{
		gst_element_set_state( m_teePipeline, GST_STATE_NULL );

        GstStateChangeReturn rtn;
		gst_object_unref( m_teePipeline );
	}

    if( m_bus != NULL )
	{
		if( !gst_bus_remove_watch( m_bus) )
		{
			CSIO_LOG(eLogLevel_error, "Streamout: Cannot remove tee msg handler %d", m_bus_id);
		}

		gst_object_unref( m_bus );
		m_bus    = NULL;
		m_bus_id = -1;
	}

    if(m_teeLoop != NULL)
    {
        g_main_loop_unref( m_teeLoop );
    }

    if(m_preview)
    	m_preview->drop();

    if(m_snapobj)
    	m_snapobj->drop();

    m_teeLoop     = NULL;
    m_teePipeline = NULL;
    m_teeSource   = NULL;
	m_tee         = NULL;
	m_teeQ        = NULL;
	m_sink        = NULL;

	m_bNeedData      = false;
	m_bPushRawFrames = false;

	CSIO_LOG(eLogLevel_verbose,  "Streamout: cleanup done" );

	unlockTee();
}

int CStreamoutManager::stopTee(void)
{
	int iRtn = 0;

	lockTee();
	m_bStopTeeInProgress = true; //stop pushing frames
	m_bExit = true;
	if( m_teeLoop )
	{
		g_main_loop_quit( m_teeLoop );
		unlockTee();

    	int   iRtn;
    	void* tResults;

    	CSIO_LOG(eLogLevel_verbose,  "Streamout: wait for thread to exit." );
        iRtn = pthread_join( m_tCameraTeeId, &tResults );
        CSIO_LOG(eLogLevel_verbose,  "Tee thread exited. Status = %d", iRtn );
	}
	else
	{
		unlockTee();
		CSIO_LOG(eLogLevel_verbose,  "Tee loop is NULL" );
		iRtn = -1;
	}

	return(iRtn);
}

int CStreamoutManager::startTee(void)
{
	int iRtn = 0;

    if( !CreateTeeThread() )
    {
    	CSIO_LOG(eLogLevel_error, "Streamout: Failed to start camera tee thread");
    	iRtn = -1;
    }
    else
    {
    	startClient();
    }

    return( iRtn );
}

int CStreamoutManager::saveRawFrame (GstPad *pad, GstPadProbeInfo *buffer)
{
	int iStatus = 0;

	//CSIO_LOG(eLogLevel_verbose, "Streamout: save_raw_frame...\n");

	lockTee();

	if(m_teeSource)
	{
		if(!m_bStopTeeInProgress)
		{
			if( m_bNeedData )
			{
				GstBuffer *buf;
				GstMapInfo info;
				buf = GST_PAD_PROBE_INFO_BUFFER(buffer);
				gst_buffer_map(buf,&info,GST_MAP_READ);

				guint size;
				GstFlowReturn ret;

				GstBuffer *DstBuffer;
				DstBuffer = gst_buffer_copy( buf );

				g_signal_emit_by_name( m_teeSource, "push-buffer", DstBuffer, &ret );
				gst_buffer_unref (DstBuffer);

				if( ret != GST_FLOW_OK )
				{
					CSIO_LOG(eLogLevel_error, "Streamout: saveRawFrame buffer push failed");
				}

				gst_buffer_unmap(buf,&info);
			}
		}
	}

	unlockTee();

    return( iStatus );
}

int CStreamoutManager::startClient(void)
{
	int iRtn = 0;

    if( !CreateDummyClientThread() )
    {
    	CSIO_LOG(eLogLevel_error, "Streamout: Failed to start dummy client thread");
    	iRtn = -1;
    }

    return( iRtn );
}

//connect to camera stream then automatically disconnect
void* CStreamoutManager::DummyClientEntry()
{
	const int c_usleep_1s = 1000000;
	char pipeline[1024];
	m_clientLoop = NULL;
	GstBus    *bus = NULL;
	guint      bus_id;
	GstElement *source   = NULL;

	int  iStatus  = 0;

	CSIO_LOG(eLogLevel_verbose,  "Streamout: startClient...\n" );

	while (!m_bExit && !m_bExitClient)
	{
		m_clientLoop = g_main_loop_new( NULL, FALSE );

		if( m_clientLoop  )
		{
			m_clientPipeline = gst_pipeline_new( NULL );
			source       = gst_element_factory_make( "rtspsrc", NULL );
			m_clientSink = gst_element_factory_make( "fakesink",NULL );

			if( !m_clientPipeline || !source || !m_clientSink )
			{
				iStatus = -1;
				CSIO_LOG(eLogLevel_error, "Streamout: Cannot create snapshot client pipeline elements\n" );
			}
			else
			{
				gst_bin_add_many( GST_BIN( m_clientPipeline ), source, m_clientSink, NULL );

				char *pBuf = new char[512];
				snprintf(pBuf, 512, "rtsp://127.0.0.1:%s/%s.sdp", m_rtsp_port, m_stream_name );
				g_object_set( G_OBJECT( source), "location",pBuf, NULL );
				CSIO_LOG(eLogLevel_verbose,  "camera URL = %s\n", pBuf );
				delete [] pBuf;

				g_signal_connect( source, "pad-added", G_CALLBACK( cb_clientPadAddedHandler ),this );
			}

			if( !iStatus )
			{
				bus = gst_pipeline_get_bus( GST_PIPELINE( m_clientPipeline ) );
				bus_id = gst_bus_add_watch( bus, cb_clientGstMsgHandler, this );

				CSIO_LOG(eLogLevel_verbose,  "Streamout: Starting client pipeline...\n" );
				if( (gst_element_set_state( m_clientPipeline, GST_STATE_PLAYING)) == GST_STATE_CHANGE_FAILURE )
				   CSIO_LOG(eLogLevel_error,  "Streamout: Cannot start client pipeline\n" );
				else
					CSIO_LOG(eLogLevel_verbose,  "Streamout: client started\n" );

				g_main_loop_run( m_clientLoop );
				CSIO_LOG(eLogLevel_verbose,  "Streamout: exited client mainloop\n" );
			}
		}

		if(m_clientPipeline != NULL)
		{
			gst_element_set_state( m_clientPipeline, GST_STATE_NULL );

	        GstStateChangeReturn rtn;
			gst_object_unref( m_clientPipeline );
		}

	    if(bus != NULL)
		{
			if( !gst_bus_remove_watch (bus) )
			{
				CSIO_LOG(eLogLevel_error, "Streamout: Cannot remove client msg handler %d", bus_id);
			}

			gst_object_unref( bus );
			bus = NULL;
			bus_id = -1;
		}

	    if(m_clientLoop != NULL)
	    {
	        g_main_loop_unref( m_clientLoop );
	    }

	    m_clientLoop     = NULL;
		m_clientPipeline = NULL;
		source       = NULL;
		m_clientSink = NULL;

		usleep( c_usleep_1s );
	}

	CSIO_LOG(eLogLevel_verbose, "Exiting dummy client thread");
	pthread_exit( NULL );
	return(NULL);
}

void CStreamoutManager::stopClient(void)
{
	m_bExitClient = true;
	if( m_clientLoop )
	{
		g_main_loop_quit( m_clientLoop );
	}
	else
		CSIO_LOG(eLogLevel_error, "Streamout: client main loop is NULL" );
}

GstElement *CStreamoutManager::getClientPipeline(void)
{
	return( m_clientPipeline );
}

GMainLoop *CStreamoutManager::getClientMainLoop(void)
{
	return( m_clientLoop );
}

void cb_clientPadAddedHandler( GstElement *src, GstPad *new_pad, void *arg )
{
	CStreamoutManager *pMgr = (CStreamoutManager *) arg;
	GstCaps      *new_pad_caps   = NULL;
	GstStructure *new_pad_struct = NULL;
	const gchar  *new_pad_type   = NULL;
	gchar        *mediaName      = NULL;
	gchar        *encodingName   = NULL;
	gchar        *payload        = NULL;

	CSIO_LOG(eLogLevel_verbose, "Streamout: Received new pad '%s' from '%s':\n", GST_PAD_NAME (new_pad), GST_ELEMENT_NAME (src));

	new_pad_caps = gst_pad_query_caps( new_pad, NULL );
	new_pad_struct = gst_caps_get_structure( new_pad_caps, 0 );
	new_pad_type = gst_structure_get_name(new_pad_struct);
	CSIO_LOG(eLogLevel_verbose, "Streamout: New pads type: '%s'\n", new_pad_type);

	//Extract type of pad directly if UDP
	if( strncmp( new_pad_type, "video/x-h264", sizeof("video/x-h264") ) == 0 )
	{
		mediaName = (gchar*)g_malloc( 6 );
		snprintf( mediaName, 6, "video" );
		encodingName = (gchar*)g_malloc(sizeof( "H264" ));
		snprintf(encodingName, sizeof( "H264" ), "%s", "H264" );
	}
	else
	{
		if( strncmp( new_pad_type, "audio/mpeg", sizeof( "audio/mpeg" ) ) == 0 )
		{
			mediaName = (gchar*)g_malloc( 6 );
			snprintf( mediaName, 6, "audio" );
			encodingName = (gchar*)g_malloc(sizeof( "MPEG4-GENERIC" ));
			snprintf(encodingName, sizeof( "MPEG4-GENERIC" ), "%s", "MPEG4-GENERIC" );
		}
	}

	const GValue* value = NULL;
	if( !mediaName )
		value = gst_structure_get_value( new_pad_struct, "media" );

	if( (value !=NULL) || mediaName )
	{
		if( !mediaName )
		{
			mediaName = gst_value_serialize( value );

			value = gst_structure_get_value( new_pad_struct, "encoding-name" );
			encodingName = gst_value_serialize( value );
			value = gst_structure_get_value( new_pad_struct, "payload" );
			payload = gst_value_serialize( value );

			if(encodingName == NULL)
			{
				// gst_rtp_jpeg_depay_sink_template has 2 entries, one without an encoding-name field.
				// So, in this case we check the
				if(strcmp(payload, "26") == 0)
				{
				   CSIO_LOG(eLogLevel_debug, "SnapShot: setting encoding-name to JPEG\n");
				   // 26 is for jpeg
				   encodingName = (gchar*)g_malloc(16);
				   snprintf(encodingName, 15, "JPEG");
				}
			}
		}

		if( mediaName )
		{
			//CSIO_LOG(eLogLevel_debug,  "Found media field:'%s'\r\n", mediaName );
			if (strcmp(mediaName, "video") == 0)
			{
				//video component detected
				CSIO_LOG(eLogLevel_verbose, "Streamout: adding video pad...\n");
				GstPad  *sink_pad = NULL;
				sink_pad = gst_element_get_static_pad( pMgr->m_clientSink, "sink" );
				if( sink_pad )
				{
				   GstPadLinkReturn linkRtn = gst_pad_link( new_pad, sink_pad );
				   if( !GST_PAD_LINK_FAILED( linkRtn ) )
				   {
					   CSIO_LOG(eLogLevel_verbose,  "Streamout: Linked stream for client.\n" );
					   pMgr->stopClient();
				   }
				   else
					   CSIO_LOG(eLogLevel_error, "Streamout: Cannot link input pad\n");

				   gst_object_unref( sink_pad );
				}
				else
				   CSIO_LOG(eLogLevel_error, "Streamout: Cannot get first pad's SINK pad\n" );
			}
			else if( strcmp( mediaName, "audio") == 0 )
			{
			//audio component detected
			}
			else
			{
				CSIO_LOG(eLogLevel_error, "Streamout: unknown media name %s\n", mediaName);
			}
		}

		//release any resources if necessary
		if( new_pad_caps != NULL )
			gst_caps_unref( new_pad_caps );

		if( mediaName != NULL )
			g_free( mediaName );

		if( encodingName != NULL )
			g_free( encodingName );

		if( payload  != NULL )
		{
			g_free( payload );
		}
	}
}

gboolean cb_clientGstMsgHandler(GstBus *bus, GstMessage *msg, void *arg)
{
	CStreamoutManager *pMgr = (CStreamoutManager *) arg;
	gchar     *debug_info = NULL;
	GMainLoop *pMainloop  = NULL;
	GError    *err;

	switch (GST_MESSAGE_TYPE(msg))
	{
		case GST_MESSAGE_NEW_CLOCK:
			GstClock *clock;

			gst_message_parse_new_clock (msg, &clock);

			CSIO_LOG(eLogLevel_verbose, "Streamout: New client clock: %s\n", (clock ? GST_OBJECT_NAME (clock) : "NULL"));
			break;

		case GST_MESSAGE_CLOCK_LOST:
			CSIO_LOG(eLogLevel_verbose, "Streamout: Client clock lost, selecting a new one\n");
			if(pMgr)
			{
				if(pMgr->getClientPipeline())
				{
					GstStateChangeReturn result;
					result = gst_element_set_state (pMgr->getClientPipeline(), GST_STATE_PAUSED);
					if(result != GST_STATE_CHANGE_SUCCESS)
					{
						CSIO_LOG(eLogLevel_verbose, "Streamout: Lost clock - setting client pipeline to pause returned %d\n", result);
					}

					result = gst_element_set_state (pMgr->getClientPipeline(), GST_STATE_PLAYING);
					if(result != GST_STATE_CHANGE_SUCCESS)
					{
						CSIO_LOG(eLogLevel_verbose, "Streamout: Lost clock - setting client pipeline to play returned %d\n", result);
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

			CSIO_LOG(eLogLevel_error,  "Streamout: Client error received from element %s: %s: error code = %d\n",
			GST_OBJECT_NAME(msg->src), err->message, err->code );
			CSIO_LOG(eLogLevel_error,  "Streamout: Client debugging information: %s\n", debug_info ? debug_info : "none" );

			g_clear_error( &err );
			g_free( debug_info );
			if(pMgr)
			{
				pMainloop = pMgr->getClientMainLoop();
				if( pMainloop )
					g_main_loop_quit( pMainloop );
			}
			break;

		case GST_MESSAGE_EOS:
			CSIO_LOG(eLogLevel_verbose,  "Streamout: End-Of-Client-Stream\n" );
			if(pMgr)
			{
				pMainloop = pMgr->getClientMainLoop();
				if( pMainloop )
					g_main_loop_quit( pMainloop );
			}
			break;

		case GST_MESSAGE_STATE_CHANGED:
			if( GST_MESSAGE_SRC(msg) == GST_OBJECT( pMgr->getClientPipeline() ) )
			{
				GstState old_state, new_state, pending_state;
				gst_message_parse_state_changed( msg, &old_state, &new_state,
				&pending_state );
				CSIO_LOG(eLogLevel_verbose, "Streamout: Pipeline client state changed from %s to %s:\n",
				gst_element_state_get_name( old_state ),
				gst_element_state_get_name( new_state) );
			}
			break;

		default:
			break;
	}

	return TRUE;
}
