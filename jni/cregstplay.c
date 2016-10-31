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
#include "csio_jni_if.h"
#include <gst/net/gstnetaddressmeta.h>
#include "socketHandler.h"
#ifdef SupportsHDCPEncryption
	#include "HDCP2xEncryptAPI.h"
#endif
///////////////////////////////////////////////////////////////////////////////

extern int g_using_glimagsink;

void *sockInst;//Generic instance, get socket class instance
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
static void pad_added_callback2 (GstElement *src, GstPad *new_pad, CREGSTREAM *data) 
{
    GstCaps      *new_pad_caps   = gst_pad_query_caps( new_pad, NULL );
    GstStructure *new_pad_struct = gst_caps_get_structure( new_pad_caps, 0 );
    GstElement *sinker = NULL;
    GstElement *ele0 = NULL;
    const gchar *new_pad_type = NULL;
    new_pad_type = gst_structure_get_name (new_pad_struct);
	const GValue* value = NULL;	
	gchar * 	p_caps_string;
	int do_rtp = 0;
	gchar * p;
	
	csio_element_set_state( data->pipeline, GST_STATE_PAUSED);
	
	CSIO_LOG(eLogLevel_debug, "caps are %" GST_PTR_FORMAT, new_pad_caps);
	
	p_caps_string = gst_caps_to_string (new_pad_caps);
	p = p_caps_string;
	if(strncmp(p_caps_string, "application/x-rtp", 17) == 0)
	{
		do_rtp = 1;
	}
	
	// Sample caps strings:
	// "audio/mpeg, mpegversion=(int)2, stream-format=(string)adts"
	// "video/x-h264, stream-format=(string)byte-stream, alignment=(string)nal"
	// "application/x-teletext" using this caqps for crestron metadata
	
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
		if (data->audio_sink != NULL)
		{
			csio_SetAudioSink(data->audio_sink, data->streamId); // Need to set AudioSink
			csio_SetLinearVolume(data->streamId, data->audioVolume); //Set initial volume
		}

        sinker = data->element_a[0];
		CSIO_LOG(eLogLevel_debug, "Completing audio pipeline");
    }
    else if (strncmp("video", p_caps_string, 5) == 0)
    {
		build_video_pipeline(p_caps_string, data, data->element_after_tsdemux, do_rtp,&ele0,&sinker);
        sinker = data->element_v[data->element_after_tsdemux];
		CSIO_LOG(eLogLevel_debug, "Completing video pipeline");
    }
    else if (strncmp("application/x-teletext", p_caps_string, 22) == 0){
        CSIO_LOG(eLogLevel_debug, "found  metadata in the tsdemux");
        build_metadata_pipeline(data, &sinker);
        sinker = data->app_sink;;
        CSIO_LOG(eLogLevel_debug, "Completing metadata pipeline");
    }
    else
    {
        CSIO_LOG(eLogLevel_warning, "Unknown stream type: %s", p_caps_string);
		gst_caps_unref( new_pad_caps );
		return;
    }
	
	if(sinker == NULL)
	{
		CSIO_LOG(eLogLevel_error, "Empty video pipeline, not linking");
		gst_caps_unref( new_pad_caps );		
		return;
	}
	
	// Get the pad given an element.
	GstPad *sink_pad = gst_element_get_static_pad (sinker, "sink");
	if(gst_pad_is_linked(sink_pad)) 
	{
		CSIO_LOG(eLogLevel_info, "sink pad is already linked");
		gst_object_unref(sink_pad);
		gst_caps_unref( new_pad_caps );		
		return;
	}

    // Link rest of pipeline to beginning.
    GstPadLinkReturn ret = gst_pad_link(new_pad, sink_pad);
    if (GST_PAD_LINK_FAILED (ret))
    {
        CSIO_LOG(eLogLevel_error,"Type is '%s' but link failed.\n", new_pad_type);
    	gst_object_unref(sink_pad);
        gst_caps_unref(new_pad_caps);
        return;
    }
    else
    {
        CSIO_LOG(eLogLevel_debug,"Link succeeded (type '%s').\n", new_pad_type);
    }

    //call initVideo before set to play state when video was added first
    csio_jni_initVideo(data->streamId);
    csio_jni_initAudio(data->streamId);

	csio_element_set_state( data->pipeline, GST_STATE_PLAYING);
	
	// cleanup
	gst_object_unref(sink_pad);
    gst_caps_unref(new_pad_caps);
}

GstPadProbeReturn udpsrcProbe(GstPad *pad, GstPadProbeInfo *info, gpointer user_data)
{
    CREGSTREAM * data = (CREGSTREAM *)user_data;
    long tDiff = 0;

    if( user_data == NULL )
        return GST_PAD_PROBE_OK;

    static struct timespec   currentTime;
    clock_gettime(CLOCK_REALTIME, &currentTime);

    //if this is the first time
    if( data->udpsrc_prob_timer.tv_sec == 0)
    {
        data->udpsrc_prob_timer.tv_sec = currentTime.tv_sec;
    }
    else
    {
        //if still within the same second
        if(data->udpsrc_prob_timer.tv_sec == currentTime.tv_sec)
            return GST_PAD_PROBE_OK;
    }

    //now time increased or is the first time
    tDiff = currentTime.tv_sec - data->udpsrc_prob_timer.tv_sec;

    //only check every 5 seconds
    if(tDiff%5)
        return GST_PAD_PROBE_OK;

    //save it here
    data->udpsrc_prob_timer.tv_sec = currentTime.tv_sec;

    char *textString = data->sourceIP_addr;
    GstBuffer *gbuffer = GST_PAD_PROBE_INFO_BUFFER (info);

//get meta from the buffer
    if(gbuffer)
    {
        GstNetAddressMeta *meta;
        meta = gst_buffer_get_net_address_meta (gbuffer);

        if (meta == NULL)
        {
            //CSIO_LOG(eLogLevel_info, "Buffer does not have net_address_meta.");
            return GST_PAD_PROBE_OK;//try again
        }
        else
        {
            GSocketAddress *addr = meta->addr;
            if(addr)
            {
                gchar *host;

                host = g_inet_address_to_string (g_inet_socket_address_get_address
                                                 (G_INET_SOCKET_ADDRESS (addr)));

                memset(data->sourceIP_addr,0,sizeof(data->sourceIP_addr));
                memcpy(textString,host,sizeof(data->sourceIP_addr)-1);

                CSIO_LOG(eLogLevel_extraVerbose, "sourceIP_addr[%s], size:%d",data->sourceIP_addr,sizeof(data->sourceIP_addr));
                g_free (host);
            }
        }
    }

    return GST_PAD_PROBE_OK;
}

void insert_udpsrc_probe(CREGSTREAM *data,GstElement *element,const gchar *name)
{
    if (element)
    {
        GstPad *pad;
        pad = gst_element_get_static_pad(element, name);
        CSIO_LOG(eLogLevel_debug, "data[0x%x],element[0x%x],name[%s] pad:[0x%x]", data,element,name,pad);
        
        if (pad != NULL)
        {

            memset(data->sourceIP_addr,0,sizeof(data->sourceIP_addr));
            data->udpsrc_prob_timer.tv_sec = 0;
            data->udpsrc_prob_timer.tv_nsec = 0;
            data->udpsrc_prob_element = element;

            data->udpsrc_prob_id = gst_pad_add_probe(pad,
                    GST_PAD_PROBE_TYPE_BUFFER, udpsrcProbe,
                    data, NULL);
            
            gst_object_unref(pad);
        }
        else
        {
            CSIO_LOG(eLogLevel_error, "Failed to insert udpsrc probe");
        }
    }
}
/**
 * \author      John Cheng
 *
 * \date        6/15/2016
 *
 * \return      void
 *
 * \retval      void
 *
 * \brief       callback when decoder output the first video
 *  
 * \param		src - pointer to the element, 
 *              id  - stream id of the window      
 * 
 * 
 */
void csio_DecVideo1stOutputCB(GstElement *src,int id)
{ 
    if(csio_GetWaitDecHas1stVidDelay(id))
    {
        CSIO_LOG(eLogLevel_info, "Sending the playing message\n");
        csio_SaveNetworkProtocol(id);
        csio_SendVideoPlayingStatusMessage(id, STREAMSTATE_STARTED );
        CSIO_LOG(eLogLevel_info,  "+++SENT ACK TO IPLINK CLIENT Source- %ld\n", id);
        csio_SetWaitDecHas1stVidDelay(id,0);
    }
    else
    {
        CSIO_LOG(eLogLevel_debug, "csio_firstDecVideoCB, skip SENT ACK");
    }
}
/**
 * \author      Suresh Kumar
 *
 * \date        3/07/2016
 *
 * \return      void
 *
 * \retval      void
 *
 * \brief       Build just the part of the metadatapipeline appsink callback 
 *
 * \param		src - appsink element
 * \param		newpad - not required
 * \param		data - pointer to custom data structure      
 * 
 * 
 */
static void on_sample_callback_meta (GstElement *src, GstPad *new_pad, CREGSTREAM *data) 
{

    GstSample *sample;
    GstBuffer *metadataBuffer;
    GstMapInfo map;

    /* get the sample from appsink */
    sample = gst_app_sink_pull_sample (src);
    metadataBuffer = gst_sample_get_buffer (sample);

    if (gst_buffer_map (metadataBuffer, &map, GST_MAP_READ)) {
    	gsize metaDataSize = gst_buffer_get_size (metadataBuffer);//csio_buffer);
    	if(sockInst) {
			socketSend(sockInst, map.data, map.size, 0, false);
    		CSIO_LOG(eLogLevel_verbose, "sending metadata size %d", metaDataSize);
    		gst_buffer_unmap (metadataBuffer, &map);
    	}
    }
	else
		CSIO_LOG(eLogLevel_error, "Could not map gst buffer!");

    /* we don't need the appsink sample anymore */
    gst_sample_unref (sample);
}

/**
 * \author      Leon Yu
 *
 * \date        10/16/2016
 *
 * \return      void
 *
 * \retval      void
 *
 * \brief       callback function - called by the have-type signal 
 *
 * \param		typefind - 
 * \param		probability - 
 * \param		caps -       
 * \param		data -       
 * 
 */
void csio_TypeFindMsgHandler( GstElement *typefind, guint probability, GstCaps *caps, CREGSTREAM *data )
{
    GstStateChangeReturn ret;

    gchar *type = gst_caps_to_string (caps);
    CSIO_LOG( eLogLevel_debug,"%s() Media type: %s, probability = %d%%\n", __FUNCTION__, type, probability );

    if( strcasestr( type, "x-hls") )
    {
        // HLS streaming
        CSIO_LOG( eLogLevel_debug,"%s() Media type is HLS\n", __FUNCTION__ );
        data->element_v[1] = gst_element_factory_make( "hlsdemux", NULL );
        g_assert(data->element_v[1] != NULL);

        // we MUST set this property, otherwise HLS link failed with -2 after playing a few seconds.
        CSIO_LOG(eLogLevel_debug, "%s() set connection-speed to 15000 \n", __FUNCTION__ );
        g_object_set(G_OBJECT(data->element_v[1]), "connection-speed", 15000, NULL);

        data->element_v[2] = gst_element_factory_make( "tsdemux", NULL );
        g_assert(data->element_v[2] != NULL);

        data->element_after_tsdemux = 3;

        gst_bin_add_many (GST_BIN (data->pipeline), data->element_v[1], data->element_v[2], NULL);

        if ( gst_element_link (data->element_v[0], data->element_v[1]) != TRUE )
        {
            CSIO_LOG(eLogLevel_error, "ERROR: link hlsdemux failed.\n");
            gst_object_unref (data->pipeline);
            return;
        }

        //Set the pipeline to READY to trigger the signal
        CSIO_LOG( eLogLevel_debug,"%s() line %d: set state to READY.\n", __FUNCTION__, __LINE__ );
        ret = csio_element_set_state(data->pipeline, GST_STATE_READY); 
        if (ret == GST_STATE_CHANGE_FAILURE) {
            CSIO_LOG(eLogLevel_error, "ERROR: unable to set the pipeline to READY state.\n");
        }

        // Connect to the pad-added signal
        CSIO_LOG( eLogLevel_debug,"%s() line %d: Connect to pad-added signal.\n", __FUNCTION__, __LINE__ );
        g_signal_connect(data->element_v[1], "pad-added", G_CALLBACK(csio_HLS_PadAddedHandler), (void *)data->element_v[2]);
        g_signal_connect(data->element_v[2], "pad-added", G_CALLBACK(csio_PadAddedMsgHandler), data->pStreamer);

        //Set back the pipeline to PLAYING
        CSIO_LOG( eLogLevel_debug,"%s() line %d: set back to PLAYING.\n", __FUNCTION__, __LINE__ );
        ret = csio_element_set_state(data->pipeline, GST_STATE_PLAYING); 
        if (ret == GST_STATE_CHANGE_FAILURE) {
            CSIO_LOG(eLogLevel_error, "ERROR: unable to set the pipeline to PLAYING state.\n");
        }
    }
    else if( strcasestr( type, "video/quicktime") )
    {
        // MP4 Streaming
        CSIO_LOG( eLogLevel_debug,"%s() Media type is MP4\n", __FUNCTION__ );
        data->element_v[1] = gst_element_factory_make( "qtdemux", NULL );
        g_assert(data->element_v[1] != NULL);

        gst_bin_add(GST_BIN (data->pipeline), data->element_v[1]);

        if( gst_element_link (data->element_v[0], data->element_v[1]) != TRUE ) 
        {
            CSIO_LOG( eLogLevel_error,"ERROR: %s() link qtdemux failed\n", __FUNCTION__ );
            gst_object_unref (data->pipeline);
            return;
        }

        //Set the pipeline to READY to trigger the signal
        CSIO_LOG( eLogLevel_debug,"%s() line %d: set state to READY.\n", __FUNCTION__, __LINE__ );
        ret = csio_element_set_state(data->pipeline, GST_STATE_READY); 
        if (ret == GST_STATE_CHANGE_FAILURE) {
            CSIO_LOG(eLogLevel_error, "ERROR: unable to set the pipeline to READY state.\n");
        }

        // Connect to the pad-added signal
        CSIO_LOG( eLogLevel_debug,"%s() line %d: Connect to pad-added signal.\n", __FUNCTION__, __LINE__ );
        g_signal_connect (data->element_v[1], "pad-added", G_CALLBACK (csio_PadAddedMsgHandler), data->pStreamer);

        //Set back the pipeline to PLAYING
        CSIO_LOG( eLogLevel_debug,"%s() line %d: set back to PLAYING.\n", __FUNCTION__, __LINE__ );
        ret = csio_element_set_state(data->pipeline, GST_STATE_PLAYING); 
        if (ret == GST_STATE_CHANGE_FAILURE) {
            CSIO_LOG(eLogLevel_error, "ERROR: unable to set the pipeline to PLAYING state.\n");
        }
//		data->video_sink = gst_bin_get_by_interface(GST_BIN(data->pipeline), GST_TYPE_VIDEO_OVERLAY);
    }
    else if( strcasestr( type, "video/dash") )
    {
        // DASH Streaming
        CSIO_LOG( eLogLevel_debug,"%s() Media type is DASH\n", __FUNCTION__ );
        //data->element_v[1] = gst_element_factory_make( "dashdemux", NULL );
    }
    else
    {
        CSIO_LOG( eLogLevel_debug,"%s() Media type is default\n", __FUNCTION__ );
    }

    g_free (type);
}

/**
 * \author      Suresh Kumar
 *
 * \date        3/07/2016
 *
 * \return      void
 *
 * \retval      void
 *
 * \brief       Build just the part of the metadata pipeline that comes after rtspsrc and before the video sink.
 *
 * \param		data - pointer to custom data structure      
 * \param		sink - pointer to custom data structure      
 * 
 */
#define META_SERVICE_PORT (9872)
int build_metadata_pipeline(CREGSTREAM *data, GstElement **sink)
{
    sockInst = getSocketInstance();
    if(sockInst){
        socketConnect(sockInst,"127.0.0.1", META_SERVICE_PORT, 0);
    }
    CSIO_LOG(eLogLevel_debug, "creating metdata pipeline with appsink");
    data->app_sink = gst_element_factory_make("appsink", NULL);
    gst_bin_add(GST_BIN(data->pipeline), data->app_sink);
    g_object_set(G_OBJECT(data->app_sink), "emit-signals", TRUE, "sync", FALSE, NULL);
    g_signal_connect(data->app_sink, "new-sample", G_CALLBACK(on_sample_callback_meta), data);
    *sink = data->app_sink;
    return CSIO_SUCCESS;
}

/**
 * \author      Suresh Kumar
 *
 * \date        3/07/2016
 *
 * \return      void
 *
 * \retval      void
 *
 * \brief       closes the socket connections for metadata sending on reception
 * 
 */
void clearMetadataConnections()
{
    if(sockInst){
        CSIO_LOG(eLogLevel_verbose, "deleting socketInstance for sending metadata");
        socketDisconnect(sockInst);
        delSocketInstance(sockInst);
        sockInst = NULL;
    }
}
/**
 * \author      Robert Secco
 *
 * \date        3/25/2016
 *
 * \return      void
 *
 * \retval      void
 *
 * \brief       Handle HDCP decryption through this signal, pass back buffer decrypted
 *
 * \param		src - appsink element
 * \param		pesPrivateData - pointer to PES private data
 * \param		pesPayload - pointer to PES payload data
 * \param		pesPayloadLength - length of PES payload
 * \param		data - pointer to custom data structure     *
 */
static void ts_demux_post_process_callback (GstElement *src, gpointer pesPrivateData, gpointer pesPayload, guint pesPayloadLength, CREGSTREAM *data)
{
//#define ALIGN_ENCRYPT_MASK(x, mask)  (((x) + (mask)) & ~(mask))
//#define ALIGN_ENCRYPT(x, a)       ALIGN_ENCRYPT_MASK(x, (typeof(x))(a) - 1)
	guint8 * pPrivateData = (guint8 *)pesPrivateData;
	guint8 inputCtr[8] = {0};
	guint8 streamCtr[4] = {0};

	//Do nothing if hdcp is disabled, no post processing required, or if input data invalid
	if (!data->doHdcp)
		return;
	else if ((pesPayloadLength == 0) || (pesPayload == NULL) || (pesPrivateData == NULL))
	{
		CSIO_LOG(eLogLevel_warning, "ts-demux post process failed: invalid parameters");
		return;	
	}

	// TODO: add error message

	// Parse inputCtr and streamCtr from PES private data
	inputCtr[0] =  (pPrivateData[1] << 7) | (pPrivateData[0] >> 1);
	inputCtr[1] =  ((pPrivateData[2] >> 1) << 7) | (pPrivateData[1] >> 1);

	inputCtr[2] =  (pPrivateData[3] << 6) | (pPrivateData[2] >> 2);
	inputCtr[3] =  ((pPrivateData[4] >> 1) << 6) | (pPrivateData[3] >> 2);


	inputCtr[4] =  (pPrivateData[5] << 5) | (pPrivateData[4] >> 3);
	inputCtr[5] =  ((pPrivateData[6] >> 1) << 5) | (pPrivateData[5] >> 3);

	inputCtr[6] =  (pPrivateData[7] << 4) | (pPrivateData[6] >> 4);
	inputCtr[7] =  ((pPrivateData[8] >> 1) << 4) | (pPrivateData[7] >> 4);


	streamCtr[0] =  (pPrivateData[11] << 7) | (pPrivateData[10] >> 1);
	streamCtr[1] =  ((pPrivateData[12] >> 1) << 7) | (pPrivateData[11] >> 1);

	streamCtr[2] =  (pPrivateData[13] << 6) | (pPrivateData[12] >> 2);
	streamCtr[3] =  ((pPrivateData[14] >> 1) << 6) | (pPrivateData[13] >> 2);

#ifdef SupportsHDCPEncryption
      int  err = do_decrypt (pesPayload, pesPayload , pesPayloadLength, &(inputCtr[0]), &(streamCtr[0]));
      if(err != 0)
      {
    	  CSIO_LOG(eLogLevel_warning, "Decrypt-failed: failed to decrypt :%d", err);
      }
#endif
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
int build_video_pipeline(gchar *encoding_name, CREGSTREAM *data, unsigned int start, int do_rtp,GstElement **ele0,GstElement **sink)
{
    unsigned int i = start;
    int do_window = 1;
    int num_elements;
    int do_sink = 1;
	
    data->using_glimagsink = 0;
    *sink = NULL;
    //CSIO_LOG(eLogLevel_debug, "encoding_name=%s, native_window=%p, start=%u, do_rtp=%d",
    //		  encoding_name, data->native_window, start, do_rtp);

    if((strcmp(encoding_name, "H264") == 0) || (strcmp(encoding_name, "video/x-h264") == 0))
    {
        //TODO:checking return values.
        //insert queue right after rtspsrc element
        data->element_v[i++] = gst_element_factory_make("queue", NULL);
        if (data->mpegtsPresent)
        {
			g_object_set(G_OBJECT(data->element_v[i - 1]),
						"leaky", (gint)2,					//GST_QUEUE_LEAK_DOWNSTREAM
						"max-size-bytes", (guint)16*1024*1024, //16 Mb of buffer
						"max-size-buffers", (guint)0,
						"max-size-time", (guint64)0ll,
						"silent", (gboolean)TRUE,
						NULL);
        }
        
        if(do_rtp)
        {
            data->element_v[i++] = gst_element_factory_make("rtph264depay", NULL);
        }
        
        data->element_v[i++] = gst_element_factory_make("valve", NULL);
        data->element_valve_v = data->element_v[i-1];

        data->element_v[i++] = gst_element_factory_make("h264parse", NULL);
        data->element_fake_dec = data->element_v[i-1];

        data->element_v[i++] = gst_element_factory_make("queue", NULL);
        //add a probe for loss of video detection.
        GstPad *pad;
        pad = gst_element_get_static_pad( data->element_v[i-1], "src" );
        if( pad != NULL )
        {
        	guint video_probe_id = gst_pad_add_probe( pad, GST_PAD_PROBE_TYPE_BUFFER, csio_videoProbe, (void *) &data->streamId, NULL );
        	csio_SetVideoProbeId(data->streamId, video_probe_id);
        	gst_object_unref( pad );
        }

        data->element_v[i++] = gst_element_factory_make(product_info()->video_decoder_string, NULL);
        data->amcvid_dec = data->element_v[i-1];

        csio_SetVpuDecoder(data->amcvid_dec, data->streamId);

        //SET OFSSET to zero for now
        g_object_set(G_OBJECT(data->amcvid_dec), "ts-offset", 0, NULL);

        //pass surface object to the decoder
        g_object_set(G_OBJECT(data->element_v[i-1]), "surface-window", data->surface, NULL);
        CSIO_LOG(eLogLevel_debug, "SET surface-window[0x%x][%d]",data->surface,data->surface);

        *ele0 = data->element_v[0];

        if(data->amcvid_dec && csio_GetWaitDecHas1stVidDelay(data->streamId) == 0)
        {
            int sigId = 0;
            sigId = g_signal_connect(data->amcvid_dec, "crestron-vdec-output", G_CALLBACK(csio_DecVideo1stOutputCB), data->streamId);
            CSIO_LOG(eLogLevel_debug, "connect to crestron-vdec-output: StreamId[%d],sigHandlerId[%d]",data->streamId,sigId);

            if(sigId)
                csio_SetWaitDecHas1stVidDelay(data->streamId,1);
        }
    }
    else if(strcmp(encoding_name, "MP2T") == 0)
    {
    	//insert queue right after rtspsrc element
		data->element_v[i] = gst_element_factory_make("queue", NULL);
		g_object_set(G_OBJECT(data->element_v[i]),
					"leaky", (gint)2,					//GST_QUEUE_LEAK_DOWNSTREAM
					"max-size-bytes", (guint)16*1024*1024, //16 Mb of buffer
					"max-size-buffers", (guint)0,
					"max-size-time", (guint64)0ll,
					"silent", (gboolean)TRUE,
					NULL);
		gst_bin_add(GST_BIN(data->pipeline), data->element_v[i++]);

		// This happens when there's TS encapsulation.  We won't add the video sink yet.
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
			gst_element_link_many(data->element_v[i-2], data->element_v[i-1], data->element_v[i], NULL);
		}
		else
		{
			gst_element_link(data->element_v[i-1], data->element_v[i]);
		}
		// TODO: this should just connect csio_PadAddedMsgHandler, but we need to add teletext support first!
		g_signal_connect(data->element_v[i], "pad-added", G_CALLBACK(pad_added_callback2), data);
	
		// We will only do HDCP encryption in RTSP TS modes so only add callback here
		g_signal_connect(data->element_v[i], "post-process", G_CALLBACK(ts_demux_post_process_callback), data);
		i++;
		data->element_after_tsdemux = i;
		do_window = 0;
		do_sink = 0;
		data->mpegtsPresent = TRUE;
		*ele0 = data->element_v[0];
	}
	else if((strcmp(encoding_name, "JPEG") == 0) || (strcmp(encoding_name, "image/jpeg") == 0))
	{   
		if(do_rtp)
		{
			data->element_v[i++] = gst_element_factory_make("rtpjpegdepay", NULL);
		}
		data->element_v[i++] = gst_element_factory_make("queue", NULL);
		data->element_v[i++] = gst_element_factory_make("jpegparse", NULL);
		
		if(product_info()->mjpeg_decoder_string[0])
		{
			CSIO_LOG(eLogLevel_debug, "MJPEG: using the Hardware decoder via TI Ducati\n");
			// We are using gstreamer androidmedia plugin to decode mjpeg.
		    //add a probe for loss of video detection.
			GstPad *pad;
			pad = gst_element_get_static_pad( data->element_v[i-1], "src" );
			if( pad != NULL )
			{
				guint video_probe_id = gst_pad_add_probe( pad, GST_PAD_PROBE_TYPE_BUFFER, csio_videoProbe, (void *) &data->streamId, NULL );
				csio_SetVideoProbeId(data->streamId, video_probe_id);
				gst_object_unref( pad );
			}

			data->element_v[i++] = gst_element_factory_make(product_info()->mjpeg_decoder_string, NULL);
			data->amcvid_dec = data->element_v[i-1];

			CSIO_LOG(eLogLevel_debug, "MJPEG: invokes SetVpuDecoder\n");
			csio_SetVpuDecoder(data->amcvid_dec, data->streamId);

			//SET OFFSET to zero for now
			g_object_set(G_OBJECT(data->amcvid_dec), "ts-offset", 0, NULL);

			//pass surface object to the decoder
			g_object_set(G_OBJECT(data->element_v[i-1]), "surface-window", data->surface, NULL);
			CSIO_LOG(eLogLevel_debug, "SET surface-window[0x%x][%d]",data->surface,data->surface);

			*ele0 = data->element_v[0];

			if(data->amcvid_dec && csio_GetWaitDecHas1stVidDelay(data->streamId) == 0)
			{
				int sigId = 0;
				sigId = g_signal_connect(data->amcvid_dec, "crestron-vdec-output", G_CALLBACK(csio_DecVideo1stOutputCB), data->streamId);
				CSIO_LOG(eLogLevel_debug, "connect to crestron-vdec-output: StreamId[%d],sigHandlerId[%d]",data->streamId,sigId);

				if(sigId)
					CSIO_LOG(eLogLevel_debug, "MJPEG:  invokes SetWaitDecHas1stVidDelay\n");
					csio_SetWaitDecHas1stVidDelay(data->streamId,1);
			}
		}
		else // If there is No hardware mjpeg decoder
		{
			CSIO_LOG(eLogLevel_debug, "MJPEG: using the software decoder: jpegdec\n");
			data->element_v[i++] = gst_element_factory_make("jpegdec", NULL);
			data->element_fake_dec = data->element_v[i-1];
			*ele0 = data->element_v[0];
			data->using_glimagsink = 1;
		}
	}
	else if(strcmp(encoding_name, "MPEG4") == 0)
	{
		if(do_rtp)
		{		
			data->element_v[i++] = gst_element_factory_make("rtpmp4vdepay", NULL);
		}
		//data->element_v[i++] = gst_element_factory_make("queue", NULL);
        data->element_v[i++] = gst_element_factory_make("valve", NULL);
        data->element_valve_v = data->element_v[i-1];

		data->element_v[i++] = gst_element_factory_make("mpeg4videoparse", NULL);
		data->element_fake_dec = data->element_v[i-1];

		data->element_v[i++] = gst_element_factory_make(product_info()->video_decoder_string, NULL);
		data->amcvid_dec = data->element_v[i-1];

		//SET OFSSET to zero for now
		g_object_set(G_OBJECT(data->amcvid_dec), "ts-offset", 0, NULL);

		//pass surface object to the decoder
		g_object_set(G_OBJECT(data->element_v[i-1]), "surface-window", data->surface, NULL);
		CSIO_LOG(eLogLevel_debug, "SET surface-window[0x%x][%d]",data->surface,data->surface);

		*ele0 = data->element_v[0];
	}
	else
	{
		data->element_v[start] = NULL;
		CSIO_LOG(eLogLevel_error, "Unsupported video encoding %s", encoding_name);
		return CSIO_CANNOT_CREATE_ELEMENTS;
	}
		
	if(do_sink)
	{		
	    if(data->using_glimagsink)
	    {
	        data->element_v[i++] = gst_element_factory_make("videoconvert", NULL);
	    }
        
		// Temporary hack - always force the settings to use new sink.  This avoids
		// requiring the user to restore settings.
		// TODO: Don't use old sink at all for H.264
		if(data->using_glimagsink)
		{
#ifdef INSERT_SF_SINK
		    //using glimagesink
		    crestron_set_stride(0);
#endif
		    data->video_sink = gst_element_factory_make("glimagesink", NULL);
		    CSIO_LOG(eLogLevel_debug, "using glimagesink");
		}
		else
		{
#ifdef INSERT_SF_SINK
		    // This value is dictated by TI OMAP hardware.
		    crestron_set_stride(4096);
		    data->video_sink = gst_element_factory_make("surfaceflingersink", NULL);
		    CSIO_LOG(eLogLevel_debug, "using surfaceflingersink");
#endif
		}

		*sink = data->video_sink;

		// Have to add all the elements to the bin before linking.
		if(data->video_sink)
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
			    if(data->video_sink)
			        gst_element_link(data->element_v[i], data->video_sink);
			}
		}
	}
	
	if(!do_window)
	{
		CSIO_LOG(eLogLevel_debug, "Not doing window yet");
		return CSIO_SUCCESS;
	}

	if(!data->native_window)
	{
		CSIO_LOG(eLogLevel_error, "No native window");
	}

	if(data->video_sink)
	{
	    gst_video_overlay_set_window_handle(GST_VIDEO_OVERLAY (data->video_sink), (guintptr)data->native_window);
            // From tutorial 3 comments:
            // "We need to call gst_x_overlay_expose() twice
            // because of the way the surface changes propagate down the OpenGL ES / EGL pipeline
            // (The only video sink available for Android in the GStreamer SDK uses OpenGL ES).
            // By the time we call the first expose,
            // the surface that the sink will pick up still contains the old size."
            gst_video_overlay_expose(GST_VIDEO_OVERLAY (data->video_sink));
            gst_video_overlay_expose(GST_VIDEO_OVERLAY (data->video_sink));
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
 * \brief       Construct the rest of the audio pipeline
 * 				based on stream info from gstreamer.
 *
 * \param       encoding_name
 * \param		data
 * \param		do_rtp
 *
 * \todo		check for failures
 */
int build_audio_pipeline(gchar *encoding_name, CREGSTREAM *data, int do_rtp,GstElement **ele0,GstElement **sink)
{
	unsigned int start = 0;
	unsigned int i = start;
	unsigned int num_elements;		
	
	if((strcmp(encoding_name, "MPEG4-GENERIC") == 0) || (strcmp(encoding_name, "audio/mpeg") == 0)
		|| (strcmp(encoding_name, "MP4A-LATM") == 0))
	{
	    //insert queue right after rtspsrc element
	    data->element_a[i++] = gst_element_factory_make("queue", NULL);
	    g_object_set(G_OBJECT(data->element_a[i - 1]),
	    		"leaky", (gint)2,					//GST_QUEUE_LEAK_DOWNSTREAM
	    		"max-size-bytes", (guint)16*1024*1024, //16 Mb of buffer
				"max-size-buffers", (guint)0,
				"max-size-time", (guint64)0ll,
				"silent", (gboolean)TRUE,
				NULL);
	    
		if(do_rtp)
		{
			if (strcmp(encoding_name, "MP4A-LATM") == 0)
				data->element_a[i++] = gst_element_factory_make("rtpmp4adepay", NULL);
			else
				data->element_a[i++] = gst_element_factory_make("rtpmp4gdepay", NULL);
		}
		data->element_a[i++] = gst_element_factory_make("aacparse", NULL);
		data->element_a[i++] = gst_element_factory_make("queue", NULL);
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
		CSIO_LOG(eLogLevel_error, "Unsupported audio encoding %s", encoding_name);
		return CSIO_CANNOT_CREATE_ELEMENTS;
	}
	data->element_a[i++] = gst_element_factory_make("valve", NULL);
	data->element_valve_a = data->element_a[i-1];

    data->element_a[i] = gst_element_factory_make("audiorate", NULL);
	if(data->element_a[i])
	{
        data->element_audiorate = data->element_a[i++];
        guint64 tmp = 80000000;
        g_object_set(G_OBJECT(data->element_a[i-1]), "tolerance", tmp, NULL);
	}

	data->element_a[i++] = gst_element_factory_make("audioconvert", NULL);
	data->element_a[i++] = gst_element_factory_make("audioresample", NULL);
	data->element_a[i++] = gst_element_factory_make("capsfilter", NULL);

	// AudioFlinger cannot resample properly, so set caps to force gstreamer to do the resample
	gchar *capsstr = g_strdup_printf ("audio/x-raw, rate=(int)%d", (gint)product_info()->output_audio_samplerate);
	GstCaps *caps = gst_caps_from_string (capsstr);
	g_free (capsstr);
	g_object_set (G_OBJECT(data->element_a[i - 1]), "caps", caps, NULL);
	gst_caps_unref (caps);


	//add a probe for loss of audio detection.  Probe fires as long as buffers continue to push
	//onto on source pad.
	GstPad *pad;
	pad = gst_element_get_static_pad( data->element_a[i-1], "src" );
	if( pad != NULL )
	{
		guint audio_probe_id = gst_pad_add_probe( pad, GST_PAD_PROBE_TYPE_BUFFER, csio_audioProbe, (void *) &data->streamId, NULL );
		csio_SetAudioProbeId(data->streamId, audio_probe_id);
	   gst_object_unref( pad );
   }
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

//-----------------------------------------------------------------------------
// Create the http streaming pipeline "on the fly" for Adaptive Streaming
//-----------------------------------------------------------------------------
void build_http_pipeline(CREGSTREAM *data, int iStreamId)
{
    char *url = (char *)currentSettingsDB->settingsMessage.msg[iStreamId].url;

    CSIO_LOG(eLogLevel_debug, "%s() url=%s\n", __FUNCTION__, url);

    // create the source element
    data->element_zero = gst_element_factory_make("souphttpsrc", NULL);
    if( data->element_zero == NULL)
    {
      CSIO_LOG(eLogLevel_error,  "ERROR: Unable to create souphttpsrc element\n" );
      return;
    }
    g_object_set(G_OBJECT(data->element_zero), "location", url, NULL);

    CSIO_LOG(eLogLevel_debug, "%s() Stream http type %s \n", __FUNCTION__, GST_ELEMENT_NAME (data->element_zero));

    if( data->httpMode == eHttpMode_MJPEG )
    {
        GstElement *sinker = NULL;
        GstElement *ele0 = NULL;
        CSIO_LOG( eLogLevel_debug, "%s() it is http mjpeg.\n", __FUNCTION__ );
        
        g_object_set(G_OBJECT(data->element_zero), "is-live", 1, NULL);
        g_object_set(G_OBJECT(data->element_zero), "do-timestamp", 1, NULL);
        gst_bin_add(GST_BIN(data->pipeline), data->element_zero);
        build_video_pipeline("image/jpeg", data, 0, 0,&ele0,&sinker);
        gst_element_link_many(data->element_zero, data->element_v[0], NULL);
        data->element_zero = NULL;    // no callbacks
    }
    else
    {
        // create the typefind element
        data->element_v[0] = gst_element_factory_make( "typefind", NULL );
        g_assert(data->element_v[0] != NULL);

        // adding elements to pipeline
        gst_bin_add_many(GST_BIN(data->pipeline), data->element_zero, data->element_v[0], NULL);

        // link elements
        if (gst_element_link (data->element_zero, data->element_v[0]) != TRUE)
        {
            CSIO_LOG(eLogLevel_error, "ERROR: Elements could not be linked.\n");
            gst_object_unref (data->pipeline);
            return;
        }

        // Connect to the have-type signal, streaming type will be found in CALLBACK function
        CSIO_LOG(eLogLevel_debug, "%s() Connect to have-type signal.\n", __FUNCTION__);
        g_signal_connect (data->element_v[0], "have-type", G_CALLBACK(csio_TypeFindMsgHandler), data);

        //Set the pipeline to PLAYING
        CSIO_LOG(eLogLevel_debug, "%s() set state to: PLAYING\n", __FUNCTION__);
        GstStateChangeReturn ret = csio_element_set_state(data->pipeline, GST_STATE_PLAYING); 
        if (ret == GST_STATE_CHANGE_FAILURE) {
            CSIO_LOG(eLogLevel_error, "ERROR: unable to set the pipeline to PLAYING state.\n");
        }
        data->video_sink = gst_bin_get_by_interface(GST_BIN(data->pipeline), GST_TYPE_VIDEO_OVERLAY);
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
void init_custom_data(CustomData * cdata)
{
	int i;
	CREGSTREAM * data;
	
	GST_DEBUG_CATEGORY_INIT (debug_category, "cregstplay", 0, "Crestron gstreamer player!");
	gst_debug_set_threshold_for_name("cregstplay", GST_LEVEL_ERROR);

	for(i=0; i<MAX_STREAMS; i++)
	{
		data = &cdata->stream[i];
		data->streamId = i;
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
			NULL );

		data->caps_a_rtp = gst_caps_new_simple  (
				"application/x-rtp",
				"media",        G_TYPE_STRING, 	"audio",
				"clock-rate",   G_TYPE_INT,     48000,
				"encoding-name",G_TYPE_STRING, 	"MPEG4-GENERIC",
				"mode",			G_TYPE_STRING,	"AAC-hbr",
				"config",		G_TYPE_STRING, 	"1190",
				"sizelength",	G_TYPE_STRING, 	"13",
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
		data->amcviddec_ts_offset = DEFAULT_AMCVIDDEC_TS_OFFSET;
		data->audiosink_ts_offset = 0;

		data->dropAudio = false;
		data->isStarted = false;
		data->httpMode  = eHttpMode_UNSPECIFIED;
		data->pStreamer = NULL;
	}
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
	// for x60, but should not harm other platforms - without this change you don't see any video
	setenv("GST_AMC_IGNORE_UNKNOWN_COLOR_FORMATS", "yes", 1);

	CSIO_LOG(eLogLevel_debug, "Set GST_DEBUG to %s", temp);
}
