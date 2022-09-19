/**
 * Copyright (C) 2016 to the present, Crestron Electronics, Inc.
 * All rights reserved.
 * No part of this software may be reproduced in any form, machine
 * or natural, without the express written consent of Crestron Electronics.
 * 
 * \file        cres_rtsp-media.c
 *
 * \brief       overload rtsp-media.c message_handler()
 *
 * \author      John Cheng
 *
 * \date        7/15/2016
 *
 * \note
 */

///////////////////////////////////////////////////////////////////////////////

#include "cres_rtsp-media.h"

/////////////////////////////////////////////////////////////////////////////////////////////////
GST_DEBUG_CATEGORY_STATIC (cres_rtsp_media_debug);

#define GST_CAT_DEFAULT cres_rtsp_media_debug

static gboolean
custom_handle_message (GstRTSPMedia * media, GstMessage * message);

G_DEFINE_TYPE (CresRTSPMedia, cres_rtsp_media, GST_TYPE_RTSP_MEDIA);

static void
cres_rtsp_media_class_init (CresRTSPMediaClass * cres_klass)
{
    GST_DEBUG("cres_media: cres_rtsp_media_class_init");
    GstRTSPMediaClass *klass = (GstRTSPMediaClass *) (cres_klass);

    GST_DEBUG_CATEGORY_INIT (cres_rtsp_media_debug, "cresrtspmedia", 0, "CresRTSPMedia");

    klass->handle_message = custom_handle_message;
}

static void
cres_rtsp_media_init (CresRTSPMedia * media)
{
    GST_DEBUG("cres_media: cres_rtsp_media_init");
    media->m_restart = false;

    media->dataFlowMonitorThreadID = 0;
}

static void *gst_pipeline_data_flow_check(void *args)
{
    CresRTSPMedia *cresRTSPMedia = (CresRTSPMedia *)args;
    int dataFlowErrorCnt = 0;

    while (cresRTSPMedia->threadActive == true)
    {
        usleep(500000); // 500msec Note: If this value is increased, will add more time
                        //during WC stop.

        //check for if data counter has changed.
        if( cresRTSPMedia->prevEncFrameCount == cresRTSPMedia->currEncFrameCount )
        {
            // it could be that the pipeline is slow. so lets wait for some more time
	        // While testing over VPN, it was found that RTP neogitation took more than
            // 5 seconds. So on safer side waiting for 10 seconds 	
            if( dataFlowErrorCnt == 20 )
            {
                //streaming data is not flowing. Something went wrong. Restart WC
                //quit loop to trigger restart
                GST_ERROR_OBJECT(cresRTSPMedia, "Data is not flowing in the pipeline, so restarting WC");
                cresRTSPMedia->m_restart = true;
                g_main_loop_quit(cresRTSPMedia->m_loop);
                break;
            }
            else
            {
                dataFlowErrorCnt++;
                continue;
            }

        }
        dataFlowErrorCnt = 0;
        cresRTSPMedia->prevEncFrameCount = cresRTSPMedia->currEncFrameCount;
    }

    return NULL;
}

static gboolean
custom_handle_message (GstRTSPMedia * media, GstMessage * message)
{
    GstMessageType type;

    gboolean bIgnoreError = TRUE;
    GError *err = NULL;
    gchar *debug = NULL;

    GST_DEBUG_OBJECT(media, "custom_handle_message media: %p\n", media);

    if (GST_IS_ELEMENT(GST_MESSAGE_SRC(message)))
    {
        GstObject *obj = GST_MESSAGE_SRC(message);
        GST_DEBUG_OBJECT(media,  "%s: obj[%s]", __FUNCTION__, GST_OBJECT_NAME(obj));
        GST_DEBUG_OBJECT(media,  "%s: g_type_name[%s]", __FUNCTION__, g_type_name(G_OBJECT_TYPE(obj)));
        GST_DEBUG_OBJECT(media,  "%s: gst_plugin_feature_get_name %s", __FUNCTION__,
                 gst_plugin_feature_get_name(GST_ELEMENT_GET_CLASS(obj)->elementfactory));
    }

    GST_DEBUG_OBJECT(media, "%s: GST_MESSAGE_TYPE[%s]", __FUNCTION__, GST_MESSAGE_TYPE_NAME(message));

    type = GST_MESSAGE_TYPE(message);

    switch (type)
    {
    case GST_MESSAGE_WARNING:
    {
        gst_message_parse_warning(message, &err, &debug);

        if (message->src && err && err->message)
        {
            GST_ERROR_OBJECT(media, "warning from element %s: : %s\n", GST_OBJECT_NAME(message->src), err->message);
        }

        GST_DEBUG_OBJECT(media, "Debugging info: %s\n", debug ? debug : "none");

        g_error_free(err);
        g_free(debug);

        break;
    }
    case GST_MESSAGE_ERROR:
    {
        bIgnoreError = FALSE; // For now restart on error
        gst_message_parse_error(message, &err, &debug);
        if (message->src && err && err->message)
        {
            GST_ERROR_OBJECT(media, "error from element %s: : %s\n", GST_OBJECT_NAME(message->src), err->message);
        }

        GST_ERROR_OBJECT(media,"Debugging info: %s\n", debug ? debug : "none");

        g_error_free(err);
        g_free(debug);

        break;
    }
    case GST_MESSAGE_STATE_CHANGED:
    {
        GstState old_state, new_state, pending_state;
        char playingState[] = "PLAYING";
        char nullState[] = "NULL";
        CresRTSPMedia *cresRTSPMedia = (CresRTSPMedia *) media;
        GstElement *element;
        GstElement *ele;
        element = gst_rtsp_media_get_element (media);
        
        gst_message_parse_state_changed(message, &old_state, &new_state, &pending_state);

        GST_DEBUG_OBJECT(media,"%s: %s state changed from %s to %s:\n",__FUNCTION__,
                  g_type_name(G_OBJECT_TYPE(GST_MESSAGE_SRC(message))),
                  gst_element_state_get_name(old_state),
                  gst_element_state_get_name(new_state));
        if( strcmp (playingState, gst_element_state_get_name(new_state)) == 0 )
        {
            cresRTSPMedia->prevEncFrameCount = 0;
            cresRTSPMedia->currEncFrameCount = 0;
            ele = gst_bin_get_by_name_recurse_up(GST_BIN (element), "v4l2src");
            // if we are moved to playing state, and the device has video source then start thread to monitor the data flow
            if(!cresRTSPMedia->dataFlowMonitorThreadID && ele != NULL )
            {
                cresRTSPMedia->threadActive = true;
                if (pthread_create(&(cresRTSPMedia->dataFlowMonitorThreadID), NULL, gst_pipeline_data_flow_check, (void *)cresRTSPMedia) != 0 )
                {
                    GST_ERROR_OBJECT(media, "Failed to create gst_pipeline_data_flow_check new thread !");
                }
                else
                {
                    pthread_setname_np(cresRTSPMedia->dataFlowMonitorThreadID, "GSTDataFLowMonitorThread");
                    GST_DEBUG_OBJECT(media, "Started GSTDataFLowMonitorThread thread!");
                }
            }
        }
        if( strcmp (nullState, gst_element_state_get_name(new_state)) == 0 )
        {
            // if we are moved to null state, then stop the thread
            if(cresRTSPMedia->dataFlowMonitorThreadID )
            {
                cresRTSPMedia->threadActive = false;
                pthread_join(cresRTSPMedia->dataFlowMonitorThreadID, NULL);
                cresRTSPMedia->dataFlowMonitorThreadID = 0;
                GST_ERROR_OBJECT(media, "Stopped GSTDataFLowMonitorThread thread!");
            }
        }
        break;
    }
    case GST_MESSAGE_EOS:
    {
        GST_ERROR_OBJECT(media, "Received EOS from source");
        break;
    }
    default:
        break;
    }

    if (!bIgnoreError)
    {
        // quit loop to trigger restart
        ((CresRTSPMedia *)media)->m_restart = true;
        g_main_loop_quit(((CresRTSPMedia *)media)->m_loop);
    }

    return GST_RTSP_MEDIA_CLASS(cres_rtsp_media_parent_class)->handle_message(media, message);
}
/////////////////////////////////////////////////////////////////////////////////////////////////
