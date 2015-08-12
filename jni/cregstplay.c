/**
 * 				Copyright (C) 2015 to the present, Crestron Electronics, Inc.
 * 				All rights reserved.
 * 				No part of this software may be reproduced in any form, machine
 * 				or natural, without the express written consent of Crestron Electronics.
 * 
 * \file        cregstplay.c
 *
 * \brief       Gstreamer player code.
 *
 * \author      Pete McCormick
 *
 * \date        4/23/2015
 *
 * \note        The "g" in "cregstplay" is silent!  ;)
 *
 *              This module is intended to contain all of the gstreamer stuff.
 * 
 * 				Try to keep Java/Android/UI stuff out of here!
 * 
 * \todo		Resume playing if stream stops.
 * 
 * 1.
 * 2.
 * 3.
 * 4.
 * 5.
 * 6.
 * 7.
 * 8.
 * 9.
 * 10.
 * 11.
 * 12.
 * 13.
 */

///////////////////////////////////////////////////////////////////////////////

#include <string.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>		/* for setenv */
#include <unistd.h>
#include "cregstplay.h"

///////////////////////////////////////////////////////////////////////////////

static int build_audio_pipeline(gchar *encoding_name, CustomData *data, int do_rtp,GstElement **ele0,GstElement **sink);
static int build_video_pipeline(gchar *encoding_name, CustomData *data, unsigned int start, int do_rtp,GstElement **ele0,GstElement **sink);

///////////////////////////////////////////////////////////////////////////////
// Write stride value to a file so that gstreamer base libraries can use it.
static void crestron_set_stride(int stride) {
     FILE * pf;

     if(stride == 0)
     {
         unlink("/dev/shm/stride.nv12");
         return;
     }
     pf = fopen("/dev/shm/stride.nv12", "w");
     if(!pf)
     {
         return;
     }
     fprintf(pf, "%d", stride);
     fclose(pf);
}

/**
 * \author      Pete McCormick
 *
 * \date        4/23/2015
 *
 * \return      void
 *
 * \retval      void
 *
 * \brief       This gets called back from gstreamer when we are doing TS/RTP.
 *
 * \param		src
 * \param		new_pad
 * \param		data
 *
 * \note		The pipeline begins with rtspsrc and rtpmp2tdepay		
 *
 */
static void pad_added_callback2 (GstElement *src, GstPad *new_pad, CustomData *data) 
{
    GstCaps      *new_pad_caps   = gst_pad_query_caps( new_pad, NULL );
    GstStructure *new_pad_struct = gst_caps_get_structure( new_pad_caps, 0 );
    GstElement *sinker = NULL;
    GstElement *ele0 = NULL;
	const GValue* value = NULL;	
	gchar * 	p_caps_string;
	int do_rtp = 0;
	gchar * p;
	
	gst_element_set_state( data->pipeline, GST_STATE_PAUSED);
	
	GST_DEBUG ("caps are %" GST_PTR_FORMAT, new_pad_caps);
	
	p_caps_string = gst_caps_to_string (new_pad_caps);
	p = p_caps_string;
	if(strncmp(p_caps_string, "application/x-rtp", 17) == 0)
	{
		do_rtp = 1;
	}
	
	// Sample caps strings:
	// "audio/mpeg, mpegversion=(int)2, stream-format=(string)adts"
	// "video/x-h264, stream-format=(string)byte-stream, alignment=(string)nal"
	
	// Seek to the 1st comma in caps
	while((*p) && (*p != ','))
	{
		p++;
	}
	*p = 0;
	
	// Since build_audio_pipeline and build_video_pipeline look at encoding-name,
	// but those fields aren't defined here, we use the 1st part of the caps as the encoding-name.

    if (strncmp("audio", p_caps_string, 5) == 0)
    {
		build_audio_pipeline(p_caps_string, data, do_rtp,&ele0,&sinker);
        sinker = data->element_a[0];
		GST_DEBUG ("Completing audio pipeline");
    }
    else if (strncmp("video", p_caps_string, 5) == 0)
    {
		build_video_pipeline(p_caps_string, data, data->element_after_tsdemux, do_rtp,&ele0,&sinker);
        sinker = data->element_v[data->element_after_tsdemux];
		GST_DEBUG ("Completing video pipeline");
    }
    else
    {
        GST_ERROR ("Unknown stream type: %s", p_caps_string);
		gst_caps_unref( new_pad_caps );
		return;
    }
	
	if(sinker == NULL)
	{
		GST_ERROR("Empty video pipeline, not linking");
		gst_caps_unref( new_pad_caps );		
		return;
	}
	
	// Get the pad given an element.
	GstPad *sink_pad = gst_element_get_static_pad (sinker, "sink");
	if(gst_pad_is_linked(sink_pad)) 
	{
		GST_ERROR ("sink pad is already linked");
		gst_object_unref(sink_pad);
		gst_caps_unref( new_pad_caps );		
		return;
	}

	// Link rest of pipeline to beginning.
    gst_pad_link(new_pad, sink_pad);
    
	gst_element_set_state( data->pipeline, GST_STATE_PLAYING);
	
	// cleanup
	gst_object_unref(sink_pad);
    gst_caps_unref(new_pad_caps);
}

/**
 * \author      Pete McCormick
 *
 * \date        4/23/2015
 *
 * \return      void
 *
 * \retval      void
 *
 * \brief       Build just the part of the video pipeline that comes after rtspsrc and before the video sink.
 *
 * \param		encoding_name - what kind of video it is (can sometimes be the 1st part of caps string
 * 				if there was no encoding-name.
 * \param		data - pointer to custom data structure      
 * \param		start - which video element to start with
 * 
 * \note		start will be 2 if we are doing ts/rtp/udp. 
 * 				RTP depayloaders will be added as needed.
 * 
 */
static int build_video_pipeline(gchar *encoding_name, CustomData *data, unsigned int start, int do_rtp,GstElement **ele0,GstElement **sink)
{
	unsigned int i = start;
	int do_window = 1;
	int num_elements;
	int do_sink = 1;
	
	g_using_glimagsink = 0;

	//GST_DEBUG("encoding_name=%s, native_window=%p, start=%u, do_rtp=%d",
	//		  encoding_name, data->native_window, start, do_rtp);

	if((strcmp(encoding_name, "H264") == 0) || (strcmp(encoding_name, "video/x-h264") == 0))
	{
		i = start;
		if(do_rtp)
		{
			data->element_v[i++] = gst_element_factory_make("rtph264depay", NULL);
		}
		data->element_v[i++] = gst_element_factory_make("queue", NULL);
		data->element_v[i++] = gst_element_factory_make("h264parse", NULL);
		data->element_v[i++] = gst_element_factory_make("amcviddec-omxtiducati1videodecoder", NULL);
		
		*ele0 = data->element_v[0];
	}
	else if(strcmp(encoding_name, "MP2T") == 0)
	{		
		// This happens when there's TS encapsulation.  We won't add the video sink yet.
		i = start;
		if(do_rtp)
		{
			data->element_v[i] = gst_element_factory_make("rtpmp2tdepay", NULL);
			gst_bin_add(GST_BIN(data->pipeline), data->element_v[i]);
			i++;
		}
		data->element_v[i] = gst_element_factory_make("tsdemux", NULL);
		gst_bin_add(GST_BIN(data->pipeline), data->element_v[i]);
		if(i > start)
		{
			gst_element_link(data->element_v[i-1], data->element_v[i]);
		}
		g_signal_connect(data->element_v[i], "pad-added", G_CALLBACK(pad_added_callback2), data);
		i++;
		data->element_after_tsdemux = i;
		do_window = 0;
		do_sink = 0;

		*ele0 = data->element_v[0];
	}
	else if((strcmp(encoding_name, "JPEG") == 0) || (strcmp(encoding_name, "image/jpeg") == 0))
	{	
		i = start;
		if(do_rtp)
		{
			data->element_v[i++] = gst_element_factory_make("rtpjpegdepay", NULL);
		}
		data->element_v[i++] = gst_element_factory_make("queue", NULL);
		data->element_v[i++] = gst_element_factory_make("jpegparse", NULL);
		data->element_v[i++] = gst_element_factory_make("jpegdec", NULL);

		g_using_glimagsink =1;
	}
	else if(strcmp(encoding_name, "MPEG4") == 0)
	{
		i = start;
		if(do_rtp)
		{		
			data->element_v[i++] = gst_element_factory_make("rtpmp4vdepay", NULL);
		}
		data->element_v[i++] = gst_element_factory_make("queue", NULL);
		data->element_v[i++] = gst_element_factory_make("mpeg4videoparse", NULL);
		data->element_v[i++] = gst_element_factory_make("amcviddec-omxtiducati1videodecoder", NULL);
	}
	else
	{
		data->element_v[start] = NULL;
		GST_ERROR("Unsupported video encoding %s", encoding_name);
		return CSIO_CANNOT_CREATE_ELEMENTS;
	}
		
	if(do_sink)
	{		
		data->element_v[i++] = gst_element_factory_make("videoconvert", NULL);
		if(g_using_glimagsink || currentSettingsDB.videoSettings[0].videoSinkSelect == 0)
		{
		    //using glimagesink
		    crestron_set_stride(0);
			data->video_sink = gst_element_factory_make("glimagesink", NULL);
		    GST_INFO("using glimagesink");
		}
		else
		{
		    // This value is dictated by TI OMAP hardware.
		    crestron_set_stride(4096);
			data->video_sink = gst_element_factory_make("surfaceflingersink", NULL);
		    GST_INFO("using surfaceflingersink");
		}

		*sink = data->video_sink;

		// Have to add all the elements to the bin before linking.
		gst_bin_add(GST_BIN(data->pipeline), data->video_sink);
		num_elements = i-start;		
		for(i=start;i<start+num_elements; i++)
		{
			gst_bin_add(GST_BIN(data->pipeline), data->element_v[i]);
		}
		// Now link.
		for(i=start;i<start+num_elements; i++)
		{
			if(i<start+num_elements-1)
			{
				gst_element_link(data->element_v[i], data->element_v[i+1]);
			}
			else
			{
				gst_element_link(data->element_v[i], data->video_sink);
			}
		}				
	}
	
	if(!do_window)
	{
		GST_DEBUG("Not doing window yet");
		return CSIO_SUCCESS;
	}

	if(!data->native_window)
	{
		GST_ERROR("No native window");
	}
	gst_video_overlay_set_window_handle(GST_VIDEO_OVERLAY (data->video_sink), (guintptr)data->native_window);
	// From tutorial 3 comments:
	// "We need to call gst_x_overlay_expose() twice 
	// because of the way the surface changes propagate down the OpenGL ES / EGL pipeline 
	// (The only video sink available for Android in the GStreamer SDK uses OpenGL ES). 
	// By the time we call the first expose, 
	// the surface that the sink will pick up still contains the old size."
	gst_video_overlay_expose(GST_VIDEO_OVERLAY (data->video_sink));
	gst_video_overlay_expose(GST_VIDEO_OVERLAY (data->video_sink));

	return CSIO_SUCCESS;
}

/**
 * \author      Pete McCormick
 *
 * \date        4/23/2015
 *
 * \return      void
 *
 * \retval      void
 *
 * \brief       Construct the rest of the audio pipeline
 * 				based on stream info from gstreamer.
 *
 * \param       encoding_name
 * \param		data
 * \param		do_rtp
 *
 * \todo		check for failures
 */
static int build_audio_pipeline(gchar *encoding_name, CustomData *data, int do_rtp,GstElement **ele0,GstElement **sink)
{
	unsigned int start = 0;
	unsigned int i = start;
	unsigned int num_elements;		
	
	if((strcmp(encoding_name, "MPEG4-GENERIC") == 0) || (strcmp(encoding_name, "audio/mpeg") == 0)
		|| (strcmp(encoding_name, "MP4A-LATM") == 0))
	{
		if(do_rtp)
		{
			if (strcmp(encoding_name, "MP4A-LATM") == 0)
				data->element_a[i++] = gst_element_factory_make("rtpmp4adepay", NULL);
			else
				data->element_a[i++] = gst_element_factory_make("rtpmp4gdepay", NULL);
		}
		data->element_a[i++] = gst_element_factory_make("aacparse", NULL);
		data->element_a[i++] = gst_element_factory_make("faad", NULL);
		*ele0 = data->element_a[0];
	}
	else if(strcmp(encoding_name, "PCMU") == 0)
	{
		if(do_rtp)
		{
			data->element_a[i++] = gst_element_factory_make("rtppcmudepay", NULL);
		}
		data->element_a[i++] = gst_element_factory_make("mulawdec", NULL);
		*ele0 = data->element_a[0];
	}
	else if(strcmp(encoding_name, "PCMA") == 0)
	{
		if(do_rtp)
		{
			data->element_a[i++] = gst_element_factory_make("rtppcmadepay", NULL);
		}
		data->element_a[i++] = gst_element_factory_make("alawdec", NULL);
		*ele0 = data->element_a[0];
	}
	else if(strcmp(encoding_name, "MP3") == 0)
	{
		if(do_rtp)
		{
			data->element_a[i++] = gst_element_factory_make("rtpmpadepay", NULL);
		}
		data->element_a[i++] = gst_element_factory_make("mad", NULL);
		*ele0 = data->element_a[0];
	}
	else
	{
		data->element_a[start] = NULL;
		GST_ERROR("Unsupported audio encoding %s", encoding_name);
		return CSIO_CANNOT_CREATE_ELEMENTS;
	}
	data->element_a[i++] = gst_element_factory_make("audioconvert", NULL);
	data->element_a[i++] = gst_element_factory_make("audioresample", NULL);
	num_elements = i-start;
	data->audio_sink = gst_element_factory_make("openslessink", NULL);		
	*sink = data->audio_sink;

	for(i=start; i<num_elements; i++)
	{	
		gst_bin_add(GST_BIN(data->pipeline), data->element_a[i]);
	}
	gst_bin_add(GST_BIN(data->pipeline), data->audio_sink);
	for(i=start; i<num_elements; i++)
	{	
		if(i < num_elements-1)
		{
			gst_element_link(data->element_a[i], data->element_a[i+1]);
		}
		else
		{
			gst_element_link(data->element_a[i], data->audio_sink);
		}
	}
	return CSIO_SUCCESS;
}

/**
 * \author      Pete McCormick
 *
 * \date        4/23/2015
 *
 * \return      void
 *
 * \retval      void
 *
 * \brief       Gets called back from gstreamer when a pad is added.
 *
 * \param       src
 * \param		new_pad
 * \param		data
 * 
 * \note        For rtspsrc or souphttpsrc.
 *
 */
static void pad_added_callback(GstElement *src, GstPad *new_pad, CustomData *data) 
{
    GstCaps      *new_pad_caps   = gst_pad_query_caps( new_pad, NULL );
    GstStructure *new_pad_struct = gst_caps_get_structure( new_pad_caps, 0 );
    const gchar  *new_pad_type = gst_value_serialize(gst_structure_get_value( new_pad_struct, "media" ));
    GstElement *sinker = NULL;
    GstElement *ele0 = NULL;
	gchar        *encoding_name   = NULL;
	const GValue* value = NULL;
	int do_rtp = 0;
	gchar * p_caps_string;
	
	if(new_pad_type == NULL)
	{
		GST_ERROR("Null pad type");
		return;
	}
	
	gst_element_set_state( data->pipeline, GST_STATE_PAUSED);
	
    value = gst_structure_get_value( new_pad_struct, "encoding-name" );
    encoding_name = gst_value_serialize( value );
	p_caps_string = gst_caps_to_string (new_pad_caps);
	if(strncmp(p_caps_string, "application/x-rtp", 17) == 0)
	{
		do_rtp = 1;
	}
	GST_DEBUG("Pad added callback, type=%s, encoding_name=%s", new_pad_type, encoding_name);
	GST_DEBUG ("caps are %" GST_PTR_FORMAT, new_pad_caps);
	
    if (strncmp("audio", new_pad_type, 5) == 0)
    {
		build_audio_pipeline(encoding_name, data, do_rtp,&ele0,&sinker);
        sinker = data->element_a[0];
		GST_DEBUG ("Completing audio pipeline");
    }
    else if (strncmp("video", new_pad_type, 5) == 0)
    {
		build_video_pipeline(encoding_name, data, 0, do_rtp,&ele0,&sinker);
        sinker = data->element_v[0];
		GST_DEBUG ("Completing video pipeline");
    }
    else
    {
        GST_ERROR ("Unknown stream type: %s", new_pad_type);
		gst_caps_unref( new_pad_caps );
		g_free( encoding_name );
		return;
    }
	
	if(sinker == NULL)
	{
		GST_ERROR("Empty video pipeline, not linking");
		gst_caps_unref( new_pad_caps );		
		g_free(encoding_name);
		return;
	}
	
	// Get the pad given an element.
	GstPad *sink_pad = gst_element_get_static_pad (sinker, "sink");
	if(gst_pad_is_linked (sink_pad)) 
	{
		GST_ERROR ("sink pad is already linked");
		gst_object_unref(sink_pad);
		gst_caps_unref( new_pad_caps );		
		g_free(encoding_name);
		return;
	}

	// Link rest of pipeline to beginning.
    gst_pad_link(new_pad, sink_pad);
    
	gst_element_set_state( data->pipeline, GST_STATE_PLAYING);
	
	// cleanup
	gst_object_unref(sink_pad);
    gst_caps_unref(new_pad_caps);
	g_free(encoding_name);
}

// "souphttpsrc location=http://ssabet.no-ip.org:8084/video.mjpg ! jpegdec ! glimagesink" works with gst_parse_launch,
// but here we get internal data flow error!?
static void build_http_pipeline(void * userdata, int iStreamId)
{
	char *url = (char *)currentSettingsDB.settingsMessage.msg[iStreamId].url;
	CustomData *data = (CustomData *)userdata;
	
	/*if(g_str_has_suffix(url, ".mjpg") || g_str_has_suffix(url, ".mjpeg") || g_str_has_suffix(url, ".cgi")
		|| g_str_has_suffix(url, ".jpg") || g_str_has_suffix(url, ".jpeg"))
	{*/		
		GstElement *sinker = NULL;
		GstElement *ele0 = NULL;

		data->element_zero = gst_element_factory_make("souphttpsrc", NULL);
		g_object_set(G_OBJECT(data->element_zero), "location", url, NULL);
		g_object_set(G_OBJECT(data->element_zero), "is-live", 1, NULL);
		g_object_set(G_OBJECT(data->element_zero), "do-timestamp", 1, NULL);
		gst_bin_add(GST_BIN(data->pipeline), data->element_zero);
		build_video_pipeline("image/jpeg", data, 0, 0,&ele0,&sinker);
		gst_element_link_many(data->element_zero, data->element_v[0], NULL);
		data->element_zero = NULL;	// no callbacks
	/*}
	else if(g_str_has_suffix(url, ".mp4"))
	{
		data->element_av[0] = gst_element_factory_make("souphttpsrc", NULL);
		g_object_set(G_OBJECT(data->element_av[0]), "location", url, NULL);
		gst_bin_add(GST_BIN(data->pipeline), data->element_av[0]);

		data->element_zero = gst_element_factory_make("qtdemux", NULL);
		gst_bin_add(GST_BIN(data->pipeline), data->element_zero);

		gst_element_link_many(data->element_av[0], data->element_zero, NULL);
	}
	else if(g_str_has_suffix(url, ".flv"))
	{
		data->element_av[0] = gst_element_factory_make("souphttpsrc", NULL);
		g_object_set(G_OBJECT(data->element_av[0]), "location", url, NULL);
		gst_bin_add(GST_BIN(data->pipeline), data->element_av[0]);

		data->element_zero = gst_element_factory_make("flvdemux", NULL);
		gst_bin_add(GST_BIN(data->pipeline), data->element_zero);

		gst_element_link_many(data->element_av[0], data->element_zero, NULL);
	}
	else
	{
		GST_DEBUG("Unsupported http url %s", url);
	}*/
}

/**
 * \author      Pete McCormick
 *
 * \date        4/23/2015
 *
 * \return      void 
 *
 * \brief       Retrieve errors from the bus and show them on the UI
 *
 * \param       bus
 * \param		msg
 * \param		data
 */
static void error_callback(GstBus *bus, GstMessage *msg, CustomData *data) 
{
	GError *err;
	gchar *debug_info;
	gchar *message_string;

	gst_message_parse_error (msg, &err, &debug_info);
	message_string = g_strdup_printf ("Error received from element %s: %s", GST_OBJECT_NAME (msg->src), err->message);
	g_clear_error (&err);
	g_free (debug_info);
	set_ui_message (message_string, data);
	g_free (message_string);
	gst_element_set_state (data->pipeline, GST_STATE_NULL);
}

/**
 * \author      Pete McCormick
 *
 * \date        4/23/2015
 *
 * \return      void 
 *
 * \brief       Notify UI about pipeline state changes
 *
 * \param       bus
 * \param		msg
 * \param		data
 */
static void state_changed_callback (GstBus *bus, GstMessage *msg, CustomData *data) 
{
	GstState old_state, new_state, pending_state;
	gst_message_parse_state_changed (msg, &old_state, &new_state, &pending_state);
	/* Only pay attention to messages coming from the pipeline, not its children */
	if (GST_MESSAGE_SRC (msg) == GST_OBJECT (data->pipeline)) 
	{
		gchar *message = g_strdup_printf("State changed to %s", gst_element_state_get_name(new_state));
		set_ui_message(message, data);
		g_free (message);
	}
}

/**
 * \author      Pete McCormick
 *
 * \date        4/23/2015
 *
 * \return      void 
 *
 * \retval      void
 *
 * \brief       Set initial values of members of CustomData structure as needed.
 *
 * \param       userdata	
 *
 * \note		This would be a good place to pull these values 
 * 				from The Platform or somewhere else in the system.
 */
void init_custom_data(void * userdata)
{
	CustomData *data = (CustomData *)userdata;	
	
	GST_DEBUG_CATEGORY_INIT (debug_category, "cregstplay", 0, "Crestron gstreamer player!");
	gst_debug_set_threshold_for_name("cregstplay", GST_LEVEL_DEBUG);

	data->udp_port = 9700;
	data->udp_video_port = 2048;
	data->udp_audio_port = 2049;
	data->tcp_timeout_usec = 3000000;
	data->udp_timeout_usec = 3000000;
	data->protocols = GST_RTSP_LOWER_TRANS_UDP|GST_RTSP_LOWER_TRANS_UDP_MCAST|GST_RTSP_LOWER_TRANS_TCP;

	// Tried keeping these as strings, but would crash when g_object_set was called for udpsrc.
	data->caps_v_rtp = gst_caps_new_simple(
		"application/x-rtp",
		"media",         G_TYPE_STRING, "video",
		"clock-rate",    G_TYPE_INT,     90000,
		"encoding-name", G_TYPE_STRING, "H264",
		"payload",       G_TYPE_INT,     96,
		NULL );

	data->caps_a_rtp = gst_caps_new_simple (
		"application/x-rtp",
		"media",        G_TYPE_STRING, "audio",
		"clock-rate",   G_TYPE_INT,     48000,
		"encoding-name",G_TYPE_STRING, "MPEG4-GENERIC",
		"config",G_TYPE_STRING, "1210",
		"sizelength",G_TYPE_STRING, "13",
		"payload",    G_TYPE_INT,     127,
		"streamtype",G_TYPE_STRING, "4",
		"mode",G_TYPE_STRING, "generic",
		NULL);

	// This is if there's ts encapsulation.
	data->caps_v_ts = gst_caps_new_simple(
		"application/x-rtp",
		"media",         G_TYPE_STRING, "video",
		"clock-rate",    G_TYPE_INT,     90000,
		"encoding-name", G_TYPE_STRING, "MP2T",
		"payload",       G_TYPE_INT,     33,
		NULL );

	// Pretend ts is on for now
	data->caps_v = data->caps_v_ts;
	data->caps_a = NULL;
	//data->do_udp_ts = 1;
}
void set_gst_debug_level(void)
{
	gchar temp[256];
	FILE * f;

	f = fopen("/dev/crestron/gst_debug", "r");
	if (f == NULL)
	{
		snprintf(temp, sizeof(temp), "%s", "*:1");	// default to errors, warning for all plugins
	}
	else
	{
		fgets(temp, sizeof(temp), f);
		fclose(f);
	}
	setenv("GST_DEBUG", temp, 1);
	setenv("GST_DEBUG_NO_COLOR", "1", 1);
	setenv("GST_PLUGIN_PATH", "/system/lib/gstreamer-1.0", 1);

	GST_DEBUG("Set GST_DEBUG to %s", temp);
}
