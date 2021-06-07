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
}

static gboolean
custom_handle_message (GstRTSPMedia * media, GstMessage * message)
{
  GstMessageType type;

  gboolean bIgnoreError = TRUE;
  GError *err    = NULL;
  gchar  *debug  = NULL;

  type = GST_MESSAGE_TYPE (message);

  switch (type)
  {
      case GST_MESSAGE_WARNING:
      {
          gst_message_parse_warning (message, &err , &debug);

          if(message->src && err && err->message)
          {
              GST_ERROR ("warning from element %s: : %s\n", GST_OBJECT_NAME (message->src), err->message);
          }

          GST_ERROR ("Debugging info: %s\n", debug? debug : "none");

          g_error_free (err );
          g_free (debug);

          break;
      }
      case GST_MESSAGE_ERROR:
      {
    	  bIgnoreError = FALSE; // For now restart on error
          gst_message_parse_error( message, &err, &debug );
          if(message->src && err && err->message)
          {
              GST_ERROR ("error from element %s: : %s\n", GST_OBJECT_NAME (message->src), err->message);
          }

          GST_ERROR ("Debugging info: %s\n", debug? debug : "none");

          g_error_free (err );
          g_free (debug);

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

  return GST_RTSP_MEDIA_CLASS (cres_rtsp_media_parent_class)->handle_message(media,message);
}
/////////////////////////////////////////////////////////////////////////////////////////////////
