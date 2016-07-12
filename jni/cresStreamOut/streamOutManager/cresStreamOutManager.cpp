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

#include "cresStreamOutManager.h"

///////////////////////////////////////////////////////////////////////////////
// Allow file to override canned pipeline, for debugging...
// The following pipeline worked!
// ( videotestsrc is-live=1 ! jpegenc ! rtpjpegpay name=pay0 pt=96 )
static bool gst_rtsp_server_get_pipeline(char * pDest, int destSize)
{
    FILE * pf;

    pf = fopen("/dev/shm/rtsp_server_pipeline", "r");
    if(!pf)
    {
        // Because camera on x60 is front-facing, it is mirrored by default for the preview.
        // Old default pipeline (with video flipping) "( ahcsrc ! videoflip method=4 ! videoconvert ! %s ! rtph264pay name=pay0 pt=96 )"
        // Enabled NV21 pixel format in libgstreamer_android.so, don't need videoconvert anymore.
        //"( ahcsrc ! videoconvert ! %s ! rtph264pay name=pay0 pt=96 )"
        snprintf(pDest, destSize, "( ahcsrc name=cressrc ! video/x-raw,width=1280,height=720,framerate=15/1 ! "
                                  "%s ! rtph264pay name=pay0 pt=96 )",
                                  product_info()->video_encoder_string);

        return false;
    }
    fgets(pDest, destSize, pf);
    fclose(pf);
    return true;
}


/* called when a new media pipeline is constructed. We can query the
 * pipeline and configure our ahcsrc */
static void
media_configure (GstRTSPMediaFactory * factory, GstRTSPMedia * media,
    gpointer user_data)
{
  GstElement *element, *ahcsrc;

  /* get the element used for providing the streams of the media */
  element = gst_rtsp_media_get_element (media);
  gchar * n = gst_element_get_name(element);
  CSIO_LOG(eLogLevel_debug, "Streamout: element name[%s] of media[0x%x]",n,media);

  /* get our ahcsrc, we named it 'mysrc' with the name property */
  ahcsrc = gst_bin_get_by_name_recurse_up (GST_BIN (element), "cressrc");
  CSIO_LOG(eLogLevel_debug, "Streamout: get_by_name ahcsrc[%p]",ahcsrc);

  CSIO_LOG(eLogLevel_debug, "Streamout: set media reusable to true media[%p]",media);
  gst_rtsp_media_set_reusable (media, TRUE);

  //pass media back to manager
  ((CStreamoutManager*)user_data)->m_pMediaPipeline = media;

  gst_object_unref (ahcsrc);
  gst_object_unref (element);
  g_free(n);
}

/**********************CStreamoutManager class implementation***************************************/
CStreamoutManager::CStreamoutManager():
m_clientConnCnt(0),m_loop(NULL),m_main_loop_is_running(0),
m_pMediaPipeline(NULL)
{
    m_StreamoutEvent  = new CStreamoutEvent();

    m_StreamoutEventQ = new CStreamoutEventRingBuffer(EVNT_DEFAULT_QUEUE_SIZE);

    mLock            = new Mutex();

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
}
void CStreamoutManager::DumpClassPara(int level)
{
    CSIO_LOG(eLogLevel_info, "---Streamout: ThredId 0x%x", (unsigned long int)getThredId());
    CSIO_LOG(eLogLevel_info, "---Streamout: m_debugLevel %d", m_debugLevel);

    CSIO_LOG(eLogLevel_info, "---Streamout: m_parent 0x%x", m_parent);
    CSIO_LOG(eLogLevel_info, "---Streamout: m_clientConnCnt %d", m_clientConnCnt);
    CSIO_LOG(eLogLevel_info, "---Streamout: m_main_loop_is_running %d", m_main_loop_is_running);

    CSIO_LOG(eLogLevel_info, "---Streamout: m_loop 0x%x", m_loop);
    CSIO_LOG(eLogLevel_info, "---Streamout: m_pMediaPipeline [0x%x]",m_pMediaPipeline);

    CSIO_LOG(eLogLevel_info, "---Streamout: m_rtsp_port %s", m_rtsp_port);
    CSIO_LOG(eLogLevel_info, "---Streamout: m_res_x %s", m_res_x);
    CSIO_LOG(eLogLevel_info, "---Streamout: m_res_y %s", m_res_y);
    CSIO_LOG(eLogLevel_info, "---Streamout: m_frame_rate %s", m_frame_rate);
}

//overloaded from base
void CStreamoutManager::exitThread()
{
    CSIO_LOG(eLogLevel_debug, "Streamout: try to quit g_main_loop[0x%x]",m_loop);
    m_forceThreadExit = 1;

    if(m_loop)
    {
        g_main_loop_quit(m_loop);
        CSIO_LOG(eLogLevel_debug, "Streamout: g_main_loop_quit returned");
    }
    else
    {
        CSIO_LOG(eLogLevel_debug, "Streamout: g_main_loop is not running");
    }
}
void* CStreamoutManager::ThreadEntry()
{
    GMainContext*        context = NULL;
    GstRTSPServer *      server  = NULL;
    GstRTSPMountPoints * mounts  = NULL;
    GstRTSPMediaFactory *factory = NULL;
    char pipeline[1024];
    guint server_id;

    //create new context
    context = g_main_context_new ();
    g_main_context_push_thread_default(context);
    CSIO_LOG(eLogLevel_debug,  "Streamout: creste new context: 0x%x\n", context );
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
    CSIO_LOG(eLogLevel_error, "Streamout: set_service to port:%s",m_rtsp_port);
    gst_rtsp_server_set_service (server, m_rtsp_port);

    /* get the mount points for this server, every server has a default object
    * that be used to map uri mount points to media factories */
    mounts = gst_rtsp_server_get_mount_points (server);
    if(!mounts)
    {
        CSIO_LOG(eLogLevel_error, "Streamout: Failed to create server mounts");
        goto exitThread;
    }
    factory = gst_rtsp_media_factory_new ();
    if(!factory)
    {
        CSIO_LOG(eLogLevel_error, "Streamout: Failed to create factory");
        goto exitThread;
    }

    if(gst_rtsp_server_get_pipeline(pipeline, sizeof(pipeline)) == false)
    {
        snprintf(pipeline, sizeof(pipeline), "( ahcsrc name=cressrc ! "
                "                               video/x-raw,width=%s,height=%s,framerate=%s/1 ! "
                                                "%s ! rtph264pay name=pay0 pt=96 )",
                                                m_res_x,m_res_y,m_frame_rate,
                                                product_info()->video_encoder_string);
    }
    CSIO_LOG(eLogLevel_debug, "Streamout: rtsp server pipeline: [%s]", pipeline);
    gst_rtsp_media_factory_set_launch (factory, pipeline);

    /* notify when our media is ready, This is called whenever someone asks for
       * the media and a new pipeline with our appsrc is created */
    g_signal_connect (factory, "media-configure", (GCallback) media_configure,this);

    gst_rtsp_media_factory_set_shared (factory, TRUE);
    gst_rtsp_mount_points_add_factory (mounts, "/live.sdp", factory);
    g_object_unref (mounts);
    server_id = gst_rtsp_server_attach(server, g_main_loop_get_context(m_loop));
    CSIO_LOG(eLogLevel_debug, "Streamout: Attach to rtsp server returned server_id %u", server_id);

    m_main_loop_is_running = 1;
    g_main_loop_run (m_loop);

exitThread:
    /* cleanup */
    if(server) g_object_unref (server);
    if(factory) g_object_unref (factory);
    if(m_loop) g_main_loop_unref (m_loop);

    //need to create a cleanup function and call here
    m_loop = NULL;
    if(context)
    {
        g_main_context_pop_thread_default(context);
        g_main_context_unref (context);
        context = NULL;
    }

    CSIO_LOG(eLogLevel_debug, "Streamout: jni_start_rtsp_server ended------");

    //thread exit here
    m_ThreadIsRunning = 0;

    return NULL;
}
