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
	
	GST_DEBUG("encoding_name=%s, native_window=%p, start=%u, do_rtp=%d", 
			  encoding_name, data->native_window, start, do_rtp);

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
	}
	else if(strcmp(encoding_name, "JPEG") == 0)
	{	
		i = start;
		if(do_rtp)
		{
			data->element_v[i++] = gst_element_factory_make("rtpjpegdepay", NULL);
		}
		data->element_v[i++] = gst_element_factory_make("queue", NULL);
		data->element_v[i++] = gst_element_factory_make("jpegparse", NULL);
		data->element_v[i++] = gst_element_factory_make("jpegdec", NULL);
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
		data->video_sink = gst_element_factory_make("glimagesink", NULL);
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
		return CSIO_GSTREAMER_ERROR;
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
			data->element_a[i++] = gst_element_factory_make("rtpmp4gdepay", NULL);
			GST_ERROR("data->element_a[0]=0x%x", data->element_a[0]);
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
	}
	else if(strcmp(encoding_name, "PCMA") == 0)
	{
		if(do_rtp)
		{
			data->element_a[i++] = gst_element_factory_make("rtppcmadepay", NULL);
		}
		data->element_a[i++] = gst_element_factory_make("alawdec", NULL);
	}
	else if(strcmp(encoding_name, "MP3") == 0)
	{
		if(do_rtp)
		{
			data->element_a[i++] = gst_element_factory_make("rtpmpadepay", NULL);
		}
		data->element_a[i++] = gst_element_factory_make("mad", NULL);
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

/**
 * \author      Pete McCormick
 *
 * \date        4/23/2015
 *
 * \return      void
 *
 * \retval      void
 *
 * \brief       Build the beginning of a gstreamer pipeline 
 *				for playing video via RTSP.
 *
 * \param       userdata
 *
 * \note        
 *
 * \todo		check for failures
 */
static void build_rtsp_pipeline(void * userdata)
{
	CustomData *data = (CustomData *)userdata;
	
	GST_DEBUG("%p", (data->element_zero = gst_element_factory_make("rtspsrc", NULL)));
	g_object_set(G_OBJECT(data->element_zero), "location", currentSettingsDB.settingsMessage.msg[0].url, NULL);
	//g_object_set(G_OBJECT(data->element_zero), "latency", xxx, NULL);  // intentionally NOT setting this 
	g_object_set(G_OBJECT(data->element_zero), "tcp_timeout", data->tcp_timeout_usec, NULL);
	g_object_set(G_OBJECT(data->element_zero), "timeout", data->udp_timeout_usec, NULL);
	// For some reason, this port range must be set for rtsp with multicast to work.
	g_object_set(G_OBJECT(data->element_zero), "port-range", "5001-65535", NULL);
	g_object_set(G_OBJECT(data->element_zero), "protocols", data->protocols, NULL);	
	gst_bin_add_many(GST_BIN(data->pipeline), data->element_zero, NULL);		
	
	// video part
	data->video_sink = NULL;
}

// "souphttpsrc location=http://ssabet.no-ip.org:8084/video.mjpg ! jpegdec ! glimagesink" works with gst_parse_launch,
// but here we get internal data flow error!?
static void build_http_pipeline(void * userdata)
{
	CustomData *data = (CustomData *)userdata;
			
	GST_DEBUG("%p", (data->element_zero = gst_element_factory_make("souphttpsrc", NULL)));
	g_object_set(G_OBJECT(data->element_zero), "location", currentSettingsDB.settingsMessage.msg[0].url, NULL);
	gst_bin_add(GST_BIN(data->pipeline), data->element_zero);
}

// At the moment, just handles ts/udp, not ts/rtp/udp.
// The following worked using gst_parse_launch:
// udpsrc port=5000 ! queue ! tsdemux name=demux ! faad ! audioresample ! openslessink demux. ! videotestsrc ! glimagesink
static void build_udp_pipeline(void * userdata)
{
	CustomData *data = (CustomData *)userdata;


	// video+audio part
	data->element_av[0] = gst_element_factory_make("udpsrc", NULL);	
	g_object_set(G_OBJECT(data->element_av[0]), "port", currentSettingsDB.videoSettings[0].tsPort, NULL);
	data->element_av[1] = gst_element_factory_make("queue", NULL);
	data->element_zero = gst_element_factory_make("tsdemux", NULL);
		
	gst_bin_add_many(GST_BIN(data->pipeline), data->element_av[0], data->element_av[1], data->element_zero, NULL);
	gst_element_link_many(data->element_av[0], data->element_av[1], data->element_zero, NULL);	
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
 * \brief       Launch the entire pipeline at once, 
 * 				using the same pipeline syntax as gst-launch-1.0
 *
 * \param      	userdata 
 *
 * \note		This is to allow tweaking of different possible pipelines 
 *				without having to recompile.
 * 
 * \todo		check for failures
 */
static void build_gst_launch_pipeline(void * userdata)
{
	gchar temp[2048];
	FILE * f;
	GError *error = NULL;	
	CustomData *data = (CustomData *)userdata;	

	f = fopen("/dev/shm/gst-launch", "r");
	if (f == NULL) 
	{
		GST_ERROR("Could not open gst-launch file");
		return;
	}
	
	fgets(temp, sizeof(temp), f);
	fclose(f);
	
	GST_DEBUG("Doing gst_parse_launch, pipeline=%s", temp);
	data->pipeline = gst_parse_launch(temp, &error);
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
 * \return      void *
 *
 * \retval      NULL
 *
 * \brief       Thread for building gstreamer pipeline.
 *
 * \param       userdata	
 *
 * \todo		check for failures
 */
void *app_function (void *userdata) 
{
	GstBus *bus;
	CustomData *data = (CustomData *)userdata;
	GSource *bus_source;

	GST_DEBUG ("Creating pipeline in CustomData at %p", data);

	/* Create our own GLib Main Context and make it the default one */
	data->context = g_main_context_new ();
	g_main_context_push_thread_default(data->context);
	
	data->pipeline = gst_pipeline_new(NULL);
  
	if(strncmp("http://", currentSettingsDB.settingsMessage.msg[0].url, 7) == 0)
	{
		build_http_pipeline(data);
	}
	else if(strncmp("rtsp://", currentSettingsDB.settingsMessage.msg[0].url, 7) == 0)
	{
		build_rtsp_pipeline(data);
	}
	else if(strncmp("udp://", currentSettingsDB.settingsMessage.msg[0].url, 6) == 0)
	{
		build_udp_pipeline(data);
	}	
	else if(strncmp("gst-launch://", currentSettingsDB.settingsMessage.msg[0].url, 13) == 0)
	{
		data->element_zero = NULL;	// no callbacks will be registered.
		data->video_sink = NULL;	// want to auto-detect sink
		build_gst_launch_pipeline(data);
	}
	else
	{
		GST_ERROR ("Unsupported stream url: %s", currentSettingsDB.settingsMessage.msg[0].url);
		return NULL;
	}
  
    // Register callback.
    if(data->element_zero != NULL)
	{
		g_signal_connect(data->element_zero, "pad-added", G_CALLBACK(pad_added_callback), data);
	}
	else
	{
		GST_DEBUG("Null element zero, no callbacks will be registered");
	}

	/* Set the pipeline to READY, so it can already accept a window handle, if we have one */
	gst_element_set_state(data->pipeline, GST_STATE_READY);

	if (data->video_sink) 
	{
		// Try just setting the video sink, because we haven't built the pipeline yet.
		//gst_element_set_state(data->video_sink, GST_STATE_READY);	
	}
	else
	{
		data->video_sink = gst_bin_get_by_interface(GST_BIN(data->pipeline), GST_TYPE_VIDEO_OVERLAY);
	}
	
	// Video sink might not exist yet
	//if (!data->video_sink) 
	//{
	//	GST_ERROR ("Could not retrieve video sink");
	//	g_main_context_pop_thread_default(data->context);
	//	g_main_context_unref (data->context);
	//	gst_element_set_state (data->pipeline, GST_STATE_NULL);	
	//	gst_object_unref (data->pipeline);
	//	return NULL;
	//}

	/* Instruct the bus to emit signals for each received message, and connect to the interesting signals */
	bus = gst_element_get_bus (data->pipeline);
	bus_source = gst_bus_create_watch (bus);
	g_source_set_callback (bus_source, (GSourceFunc) gst_bus_async_signal_func, NULL, NULL);
	g_source_attach (bus_source, data->context);
	g_source_unref (bus_source);
	g_signal_connect (G_OBJECT (bus), "message::error", (GCallback)error_callback, data);
	g_signal_connect (G_OBJECT (bus), "message::state-changed", (GCallback)state_changed_callback, data);
	gst_object_unref (bus);

	/* Create a GLib Main Loop and set it to run */
	GST_DEBUG ("Entering main loop... (CustomData:%p)", data);
	data->main_loop = g_main_loop_new (data->context, FALSE);
	check_initialization_complete(data);
	pthread_mutex_lock(&(data->ready_to_start_playing_lock));
	pthread_cond_signal(&(data->ready_to_start_playing_signal));
	pthread_mutex_unlock(&(data->ready_to_start_playing_lock));
	
	// g_main_loop_run will not return until playback is failed or finished.
	g_main_loop_run (data->main_loop);
	
	// Wait...
	
	GST_DEBUG ("Exited main loop");
	g_main_loop_unref (data->main_loop);
	data->main_loop = NULL;

	/* Free resources */
	g_main_context_pop_thread_default(data->context);
	g_main_context_unref (data->context);
	gst_element_set_state (data->pipeline, GST_STATE_NULL);
	gst_object_unref (data->video_sink);
	gst_object_unref (data->pipeline);

	return NULL;
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
	
	currentSettingsDB.videoSettings[0].tsPort = 5000;	//TODO: remove
	// temp debug
//	data->udp_port = 5000;
	data->tcp_timeout_usec = 3000000;
	data->udp_timeout_usec = 3000000;
	data->protocols = GST_RTSP_LOWER_TRANS_UDP|GST_RTSP_LOWER_TRANS_UDP_MCAST|GST_RTSP_LOWER_TRANS_TCP;
}

