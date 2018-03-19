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
#define MAX_RTSP_SESSIONS         5

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

void cb_srcNeedData (GstElement *appsrc, guint unused_size, gpointer user_data)
{
//    CSIO_LOG(eLogLevel_verbose, "Streamout: cb_need_data...");
	CStreamoutManager *pMgr = (CStreamoutManager *) user_data;

	pMgr->m_bNeedData = true;
}

void cb_srcEnoughData (GstElement *appsrc, guint unused_size, gpointer user_data)
{
	//CSIO_LOG(eLogLevel_verbose, "Streamout: cb_enough_data...");
	CStreamoutManager *pMgr = (CStreamoutManager *) user_data;

	pMgr->m_bNeedData = false;
}

/* called when a new media pipeline is constructed. We can query the
 * pipeline and configure our media src */
static void
media_configure (GstRTSPMediaFactory * factory, GstRTSPMedia * media,
    gpointer user_data)
{
	CStreamoutManager *pMgr = (CStreamoutManager *) user_data;
	GstElement *element;
	GstElement *ele;
	/* get the element used for providing the streams of the media */
	element = gst_rtsp_media_get_element (media);
	gchar * n = gst_element_get_name(element);
	CSIO_LOG(eLogLevel_debug, "Streamout: element name[%s] of media[0x%x]",n,media);

  //set up video source
	{
		int width  = atoi(pMgr->m_res_x);
		int height = atoi(pMgr->m_res_y);
		int framerate = atoi(pMgr->m_frame_rate);

		ele = gst_bin_get_by_name_recurse_up (GST_BIN (element), "cresappsrc");
		if(ele)
		{
			CSIO_LOG(eLogLevel_verbose, "Streamout: get_by_name cresappsrc[%p]",ele);
			pMgr->m_camera = ele;

//TODO: save CAPS from camera
			g_object_set(G_OBJECT(ele), "caps",
			gst_caps_new_simple(
			"video/x-raw",
			"format",    G_TYPE_STRING, "NV21",
			"width",     G_TYPE_INT, width,
			"height",    G_TYPE_INT, height,
			"framerate", GST_TYPE_FRACTION, 0, 1, //variable
			NULL), NULL);

			g_object_set( G_OBJECT(ele), "stream-type", 0, NULL );
			g_object_set( G_OBJECT(ele), "format", GST_FORMAT_TIME, NULL );

			g_signal_connect( ele, "need-data",   G_CALLBACK (cb_srcNeedData),   user_data );
			g_signal_connect( ele, "enough_data", G_CALLBACK (cb_srcEnoughData), user_data );
			gst_object_unref(ele);
		}

		ele = gst_bin_get_by_name_recurse_up (GST_BIN (element), "vparser");
		if(ele)
		{
			CSIO_LOG(eLogLevel_verbose, "Streamout: get_by_name vparser[%p]",ele);
			g_object_set( G_OBJECT(ele), "width",  width, NULL );
			g_object_set( G_OBJECT(ele), "height", height, NULL );
			g_object_set( G_OBJECT(ele), "format", GST_VIDEO_FORMAT_NV21, NULL );
			g_object_set( G_OBJECT(ele), "framerate", framerate,1, NULL );
			gst_object_unref(ele);
		}
	}

	CSIO_LOG(eLogLevel_debug, "Streamout: set media reusable to true media[%p]",media);
	gst_rtsp_media_set_reusable (media, TRUE);

	//pass media back to manager
	pMgr->m_pMedia = media;

	((CresRTSPMedia *)media)->m_loop = pMgr->m_loop;

	pMgr->m_bPushRawFrames = true;

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
m_pMedia(NULL),m_bNeedData(false),m_bStopTeeInProgress(false),
m_bExit(false),m_bPushRawFrames(false),m_ahcsrc(NULL),m_camera(NULL)
{
    m_StreamoutEvent  = new CStreamoutEvent();

    m_StreamoutEventQ = new CStreamoutEventRingBuffer(EVNT_DEFAULT_QUEUE_SIZE);

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

        g_main_loop_quit(m_loop);
        CSIO_LOG(m_debugLevel, "Streamout: g_main_loop_quit returned");
    }
    else
    {
        CSIO_LOG(m_debugLevel, "Streamout: g_main_loop is not running");
    }
}

static pthread_cond_t m_condv;

void *CStreamoutManager::forceUnprepare(void * arg)
{
	GstRTSPMedia * pMedia = (GstRTSPMedia *)arg;

    gst_rtsp_media_unprepare_force (pMedia);   //bug122841 - this function hangs.

    pthread_cond_signal(&m_condv); //Flags that stop has completed
    pthread_exit(NULL);
    return NULL;
}

void csio_jni_recoverTxrxService();
void csio_jni_sendCameraStopFb();

void CStreamoutManager::forceMediaUnprepare(GstRTSPMedia * pMedia)
{
	int result = 0;
	int timeout_sec = 5;
	struct timespec ts;
	pthread_t threadID;
	pthread_mutex_t  m_mutex;

	//init mutex and thread attributes
	pthread_cond_init(&m_condv, NULL);
	pthread_mutex_init(&m_mutex, NULL);
	pthread_attr_t attr;
	pthread_attr_init(&attr);
	pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_DETACHED);
	clock_gettime(CLOCK_REALTIME, &ts);
	ts.tv_sec += timeout_sec;

	//Kick off the new thread
	if (pthread_create(&threadID, &attr, &(CStreamoutManager::forceUnprepare), (void *)pMedia) != 0 )
	{
	    CSIO_LOG(eLogLevel_error, "forceMediaUnprepare() create the new thread failed.");
	}

	//Wait for timeout or completion
	pthread_mutex_lock(&m_mutex);
	result = pthread_cond_timedwait(&m_condv, &m_mutex, &ts);
	pthread_mutex_unlock(&m_mutex);

	//Cleanup pthread objects
	pthread_attr_destroy(&attr);
	pthread_cond_destroy(&m_condv);
	pthread_mutex_destroy(&m_mutex);

	if (result == ETIMEDOUT)
	{
		CSIO_LOG(eLogLevel_error, "forceUnprepare timed out after %d seconds, restart txrxservice ...\n", timeout_sec);
		csio_jni_sendCameraStopFb();
		csio_jni_recoverTxrxService();
	}
	else if (result != 0)
		CSIO_LOG(eLogLevel_error, "Unknown error occurred while waiting for forceUnprepare to complete, error = %d\n", result);
}

void* CStreamoutManager::ThreadEntry()
{
    char pipeline[1024];
    guint server_id;
    GMainContext*        context = NULL;
    GstRTSPServer *      server  = NULL;
    GstRTSPMountPoints * mounts  = NULL;
    GSource *  server_source     = NULL;
    GstRTSPSessionPool *pool     = NULL;

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

    // limit the rtsp sesssions
    pool = gst_rtsp_server_get_session_pool(server); 
    if (!pool) {
        CSIO_LOG(eLogLevel_error, "Streamout: Failed to get the session pool");
        goto exitThread;
    }
    gst_rtsp_session_pool_set_max_sessions(pool, MAX_RTSP_SESSIONS);
    CSIO_LOG(eLogLevel_debug, "Streamout: max_sessions set to %d", gst_rtsp_session_pool_get_max_sessions(pool));

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
        snprintf(pipeline, sizeof(pipeline), "( appsrc name=cresappsrc ! "
                "videoparse name=vparser ! "
				 "queue ! "
				 "%s bitrate=%s i-frame-interval=%s ! "
				 "rtph264pay name=pay0 pt=96 )",
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

    StartSnapShot(this);

    g_main_loop_run (m_loop);

exitThread:
    /* cleanup */
	StopSnapShot(this);

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
        forceMediaUnprepare(m_pMedia);
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
    if(pool) g_object_unref (pool);

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
	if( name != NULL)
	{
		strncpy( m_snapshot_name, name, MAX_STR_LEN );
	}
	else
	{
		CSIO_LOG(m_debugLevel, "Streamout: cannot set snapshot name. Name is NULL");
	}
}

void CStreamoutManager::startPreview(void* window)
{
	StartPreview(window);
}

void CStreamoutManager::pausePreview(void* window)
{
	PausePreview(window);
}

void CStreamoutManager::stopPreview(void* window)
{
	StopPreview(window);
}

int CStreamoutManager::waitForPreviewAvailable(int timeout_sec)
{
	int rtn = 0;

	rtn = WaitForPreviewAvailable(timeout_sec);

	return(rtn);
}

int CStreamoutManager::saveRawFrame (GstPad *pad, GstPadProbeInfo *buffer, gpointer user_data)
{
	CStreamCamera *pCam = (CStreamCamera *) user_data;
	int iStatus = 0;

	if(m_camera)
	{
		if(!m_bStopTeeInProgress)
		{
			if( m_bNeedData && m_bPushRawFrames )
			{
				GstBuffer *buf;
				GstMapInfo info;
				buf = GST_PAD_PROBE_INFO_BUFFER(buffer);
				gst_buffer_map(buf,&info,GST_MAP_READ);

				guint size;
				GstFlowReturn ret;

				GstBuffer *DstBuffer;
				DstBuffer = gst_buffer_copy( buf );	//TODO: See if we can avoid buffer copy

//				CSIO_LOG(eLogLevel_error, "Streamout: saveRawFrame push buffer");
				g_signal_emit_by_name( m_camera, "push-buffer", DstBuffer, &ret );
				gst_buffer_unref (DstBuffer);

				if( ret != GST_FLOW_OK )
				{
					CSIO_LOG(eLogLevel_error, "Streamout: saveRawFrame buffer push failed");
				}

				gst_buffer_unmap(buf,&info);
			}
		}
	}

    return( iStatus );
}
