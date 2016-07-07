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


#include "cresStreamOut.h"
static void* stream_out_thread(void * arg);
GMainLoop * loop = NULL;
///////////////////////////////////////////////////////////////////////////////
// Allow file to override canned pipeline, for debugging...
// The following pipeline worked!
// ( videotestsrc is-live=1 ! jpegenc ! rtpjpegpay name=pay0 pt=96 )
static void gst_rtsp_server_get_pipeline(char * pDest, int destSize)
{
    FILE * pf;

    pf = fopen("/dev/shm/rtsp_server_pipeline", "r");
    if(!pf)
    {
        // Because camera on x60 is front-facing, it is mirrored by default for the preview.
        // Old default pipeline (with video flipping) "( ahcsrc ! videoflip method=4 ! videoconvert ! %s ! rtph264pay name=pay0 pt=96 )"
        // Enabled NV21 pixel format in libgstreamer_android.so, don't need videoconvert anymore.
        //"( ahcsrc ! videoconvert ! %s ! rtph264pay name=pay0 pt=96 )"
        snprintf(pDest, destSize, "( ahcsrc  ! video/x-raw,width=1280,height=720,framerate=15/1 ! "
                                  "%s ! rtph264pay name=pay0 pt=96 )",
                                  product_info()->video_encoder_string);
        //snprintf(pDest, destSize, "( ahcsrc ! %s ! rtph264pay name=pay0 pt=96 )",
        //  product_info()->video_encoder_string);


        return;
    }
    fgets(pDest, destSize, pf);
    fclose(pf);
}

void stream_out_start(int id)
{
    // TODO: save threadId in cdata
    CustomDataOut *cdata;
    int iRtn;

    if(loop)
    {
        CSIO_LOG(eLogLevel_debug,  "rtsp_server: loop: 0x%x\n", loop );
    }
    else
    {
        pthread_t streamOutThread;
        iRtn = pthread_create( &streamOutThread, NULL, stream_out_thread, (void*)cdata );
        if( iRtn )
        {
            CSIO_LOG(eLogLevel_error,  "rtsp_server: ERROR: Failed to launch RTSP server thread: %d\n", iRtn );
        }
    }

    CSIO_LOG(eLogLevel_error,  "rtsp_server: stream_out_start returns" );
}

// rtsp object documentation: https://gstreamer.freedesktop.org/data/doc/gstreamer/head/gst-rtsp-server/html/GstRTSPServer.html
// copied code from gst-rtsp-server-1.4.5/examples/test-launch.c
static void* stream_out_thread(void * arg)
{
    //GMainLoop *loop;
    GstRTSPServer *server;
    GstRTSPMountPoints *mounts;
    GstRTSPMediaFactory *factory;
    char pipeline[1024];
    guint id;

    CSIO_LOG(eLogLevel_debug, "rtsp_server: jni_start_rtsp_server");

    //CustomDataOut *cdata = (CustomDataOut *)data;
    // TODO: These parameters should be saved in cdata

    loop = g_main_loop_new (NULL, FALSE);   // called from CStreamer::execute
    if(!loop)
    {
        CSIO_LOG(eLogLevel_error, "rtsp_server: Failed to create rtsp server loop");
        return NULL;
    }
    server = gst_rtsp_server_new();
    if(!server)
    {
        CSIO_LOG(eLogLevel_error, "rtsp_server: Failed to create rtsp server");
        return NULL;
    }
    /* get the mount points for this server, every server has a default object
    * that be used to map uri mount points to media factories */
    mounts = gst_rtsp_server_get_mount_points (server);
    if(!mounts)
    {
        CSIO_LOG(eLogLevel_error, "rtsp_server: Failed to create server mounts");
        return NULL;
    }
    factory = gst_rtsp_media_factory_new ();
    if(!factory)
    {
        CSIO_LOG(eLogLevel_error, "rtsp_server: Failed to create factory");
        return NULL;
    }

    //if (cdata->surface)
    //{
    //    CSIO_LOG(eLogLevel_debug, "Delete GlobalRef before adding new: %p", cdata->surface);
    //    (*env)->DeleteGlobalRef(env, cdata->surface);
    //    cdata->surface = NULL;
    //}
    //cdata->surface = (*env)->NewGlobalRef(env, surface);

    // to-do: get platform-specific encoder name from csio
    gst_rtsp_server_get_pipeline(pipeline, sizeof(pipeline));
    CSIO_LOG(eLogLevel_debug, "rtsp_server: rtsp server pipeline: [%s]", pipeline);
    gst_rtsp_media_factory_set_launch (factory, pipeline);
    gst_rtsp_media_factory_set_shared (factory, TRUE);
    gst_rtsp_mount_points_add_factory (mounts, "/live.sdp", factory);
    g_object_unref (mounts);
    id = gst_rtsp_server_attach(server, NULL);
    CSIO_LOG(eLogLevel_debug, "rtsp_server: Attach to rtsp server returned id %u", id);

    g_main_loop_run (loop);             // called from CStreamer::execute

    /* cleanup */
    g_object_unref (server);
    g_object_unref (factory);
    g_main_loop_unref (loop);

    loop = NULL;
    CSIO_LOG(eLogLevel_debug, "rtsp_server: jni_start_rtsp_server ended------");
	return NULL;
}

void stream_out_stop(int id)
{
    if(loop)
    {
        g_main_loop_quit(loop);
        CSIO_LOG(eLogLevel_debug, "rtsp_server: g_main_loop_quit retruned");
    }
    else
    {
        CSIO_LOG(eLogLevel_error, "rtsp_server: loop is NULL");
    }
}
