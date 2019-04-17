/**
 * Copyright (C) 2015 to the present, Crestron Electronics, Inc.
 * All rights reserved.
 * No part of this software may be reproduced in any form, machine
 * or natural, without the express written consent of Crestron Electronics.
 * 
 * \file        jni.c
 *
 * \brief       Java <--> native interface code.
 *
 * \author      Pete McCormick
 *
 * \date        4/23/2015
 *
 * \note        Based on gstreamer android tutorials.
 *
 * This module is intended to encapsulate the interface with Java/Android.
 * 
 * \todo		Try to move gstreamer stuff out of here.
 */

///////////////////////////////////////////////////////////////////////////////

#include <string.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>		/* for setenv */
#include <unistd.h>
#include <sys/resource.h>
#include <arpa/inet.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <ctype.h>
#include "cregstplay.h" 
#include <jni.h>
#include "GstreamIn.h"
#include "GstreamOut.h"
#include "WbsStreamIn.h"
#include "Wbs.h"
#include "cresStreamOut.h"
#include "csioCommonShare.h"
#include "gst_element_print_properties.h"
#include <gst/video/video.h>
#include "csio_jni_if.h"
// Android headers
#include "hardware/gralloc.h"           // for GRALLOC_USAGE_PROTECTED
#include "android/native_window.h"      // for ANativeWindow_ functions
#include <cresNextCommonShare.h>
#include "cresNextDef.h"
#include <CresNextSerializer.h>

#include "cresWifiDisplaySink/WfdCommon.h"
#include "ms_mice_sink/ms_mice_common.h"
#include "shared-ssl/shared-ssl.h"

///////////////////////////////////////////////////////////////////////////////

extern int  csio_Init(int calledFromCsio);
void csio_jni_stop(int streamId);
void csio_send_stats_no_bitrate (uint64_t video_packets_received, int video_packets_lost, uint64_t audio_packets_received, int audio_packets_lost);
void LocalConvertToUpper(char *str);
static void * debug_launch_pipeline(void *data);
static void Wfd_set_firewall_rules (int rtsp_port, int ts_port);
int csio_jni_StartRTPMediaStreamThread(int iStreamId, GstElement *appSource, unsigned int udpPort);
void updateProbeInfo(int streamID, struct timespec * currentTimePtr, char * srcIPAddress);
void * rtpMediaStreamThread(void * threadData);
static bool loopShouldLog(int * errorCountPtr, int * logLevelPtr);

///////////////////////////////////////////////////////////////////////////////

/*
 * These macros provide a way to store the native pointer to CustomData, which might be 32 or 64 bits, into
 * a jlong, which is always 64 bits, without warnings.
 */
#if GLIB_SIZEOF_VOID_P == 8
# define GET_CUSTOM_DATA(env, thiz, fieldID) (CustomData *)env->GetLongField (thiz, fieldID)
# define SET_CUSTOM_DATA(env, thiz, fieldID, data) env->SetLongField (thiz, fieldID, (jlong)data)
#else
# define GET_CUSTOM_DATA(env, thiz, fieldID) (CustomData *)(jint)env->GetLongField (thiz, fieldID)
# define SET_CUSTOM_DATA(env, thiz, fieldID, data) env->SetLongField (thiz, fieldID, (jlong)(jint)data)
#endif
   
#define STREAM_TRANSPORT_MPEG2TS_RTP (1)
#define STREAM_TRANSPORT_MPEG2TS_UDP (2)

#define CRESTRON_USER_AGENT 	("Crestron/1.8.2")
#define PROC_SELF_FD_FILEPATH 	("/proc/self/fd")
///////////////////////////////////////////////////////////////////////////////
CustomData *CresDataDB = NULL; //
CustomStreamOutData *CresStreamOutDataDB = NULL; //

/* These global variables cache values which are not changing during execution */
static pthread_t gst_app_thread;
static pthread_key_t current_jni_env;
static JavaVM *java_vm;
static jfieldID custom_data_field_id;
static jmethodID set_message_method_id;
static jfieldID custom_data_field_id_rtsp_server;
static jmethodID set_message_method_id_rtsp_server;
// not used
//static jmethodID on_gstreamer_initialized_method_id;
static jclass *gStreamIn_javaClass_id;
static jclass *gStreamOut_javaClass_id;
static jclass *wbsStreamIn_javaClass_id;

pthread_cond_t stop_completed_sig;
static int stop_timeout_sec = 1000;

const int c_minSocketLeft = 20;
const int c_maxNumInitialPorts	= 100;
static unsigned int *initialPorts = NULL;

static char const* file_prefix = "file://";
static unsigned const prefixLength7 = 7;

CresNextDef *CresNextDefaults = NULL;

///////////////////////////////////////////////////////////////////////////////
void csio_jni_FreeMainContext(int iStreamId);

/*rtsp_server*/
static jboolean gstNativeClassInitRtspServer (JNIEnv* env, jclass klass) ;
static void gstNativeInitRtspServer (JNIEnv* env, jobject thiz, jobject surface);
static void gstNativeFinalizeRtspServer (JNIEnv* env, jobject thiz);
void gst_native_rtsp_server_start (JNIEnv* env, jobject thiz);
void gst_native_rtsp_server_stop (JNIEnv* env, jobject thiz);

static JNINativeMethod native_methods_rtsp_server[] =
{
    { "nativeInitRtspServer", "(Ljava/lang/Object;)V", (void *) gstNativeInitRtspServer},
    { "nativeFinalizeRtspServer", "()V", (void *) gstNativeFinalizeRtspServer},
    { "nativeClassInitRtspServer", "()Z", (void *) gstNativeClassInitRtspServer},
    { "nativeRtspServerStart", "()V", (void *) gst_native_rtsp_server_start},
    { "nativeRtspServerStop", "()V",  (void *) gst_native_rtsp_server_stop},
};

UINT32 g_lSpecialFieldDebugState[SPECIAL_FIELD_DEBUG_ARRAY_SIZE] = {0};
const char * const fieldDebugNames[MAX_SPECIAL_FIELD_DEBUG_NUM - 1] =
{
    "01 BLOCK_AUDIO              " ,
    "02 INSERT_VIDEO_PROBE       " ,
    "03 PRINT_PROBE_TS           " ,
    "04 SET_PIPELINE_TO_PAUSE    " ,
    "05 FLUSH_PIPELINE           " ,
    "06 SET_AMCVIDDEC_DEBUG_LEVEL     " ,
    "07 SET_VIDEODECODER_DEBUG_LEVEL  " ,
    "08 SET_OPENSLESSINK_DEBUG_LEVEL  " ,
    "09 SET_CATEGORY_DEBUG_LEVEL      " ,
    "10 SET_AUDIOSINK_TS_OFFSET     " ,
    "11 SET_AMCVIDDEC_TS_OFFSET     " ,
    "12 PRINT_AUDIOSINK_PROPERTIES  " ,
    "13 PRINT_ELEMENT_PROPERTY      " ,
    "14 PRINT_A_V_DEBUG             " ,
    "15 INSERT_TIME_CB              " ,
    "16 NOT_PROCESS_RTCP            " ,
    "17 SET_BASETIME_OFFSET         ",
    "18 SET_SEEK_EVENT              ",
    "19 SET_AUDIO_PAD_OFFSET        ",
    "20 DROP_AUDIO_PACKETS          ",
    "21 INSERT_AUDIO_PROBE          ",
    "22 PRINT_BACKWDS_TS            ",
    
};
int amcviddec_debug_level    = GST_LEVEL_ERROR;
int videodecoder_debug_level = GST_LEVEL_ERROR;
int debug_blocking_audio = 0;
/*
 * Private methods
 */

/* Return a pointer to a stream's info, given the CustomData struct and a stream number (0-based) */
static CREGSTREAM * GetStreamFromCustomData(CustomData * cdata, int stream)
{
	if(!cdata)
	{
		return NULL;
	}
	
    if(stream >= MAX_STREAMS)
	{
		return NULL;
	}

	return &cdata->stream[stream];
}

/* Register this thread with the VM */
static JNIEnv *attach_current_thread (void) 
{
	JNIEnv *env;
	JavaVMAttachArgs args;

	CSIO_LOG(eLogLevel_debug, "Attaching thread %p", g_thread_self ());
	args.version = JNI_VERSION_1_4;
	args.name = NULL;
	args.group = NULL;

	if (java_vm->AttachCurrentThread ((void**)&env, &args) < 0) {
		CSIO_LOG(eLogLevel_warning, "Failed to attach current thread");
		return NULL;
	}

	return env;
}

/* Unregister this thread from the VM */
static void detach_current_thread (void *env) 
{
	CSIO_LOG(eLogLevel_debug, "Detaching thread %p", g_thread_self ());
	java_vm->DetachCurrentThread ();
}

/* Retrieve the JNI environment for this thread */
static JNIEnv *get_jni_env (void) 
{
	JNIEnv *env;

	if ((env = (JNIEnv*)pthread_getspecific (current_jni_env)) == NULL) {
		env = attach_current_thread ();
		pthread_setspecific (current_jni_env, env);
	}

	return env;
}

static void dump_jni_reference_table(JNIEnv* env)
{
	jclass vm_class = env->FindClass("dalvik/system/VMDebug");
	jmethodID dump_mid = env->GetStaticMethodID( vm_class, "dumpReferenceTables", "()V" );
	env->CallStaticVoidMethod( vm_class, dump_mid );
}

void csio_dump_jni_reference_table(char *label)
{
	JNIEnv *env = get_jni_env ();
	CSIO_LOG(eLogLevel_debug, "%s: **********************  %s ********************", __FUNCTION__, label);
	dump_jni_reference_table(env);
}

/* Change the content of the UI's TextView */
// void set_ui_message (const gchar *message, CustomData *data) 
// {
// 	JNIEnv *env = get_jni_env ();
// 	CSIO_LOG(eLogLevel_debug, "Setting message to: %s", message);
// 	jstring jmessage = env->NewStringUTF(message);
// 	env->CallVoidMethod (data->app, set_message_method_id, jmessage);
// 	if (env->ExceptionCheck ()) {
// 		CSIO_LOG(eLogLevel_error, "Failed to call Java method");
// 		env->ExceptionClear ();
// 	}
// 	env->DeleteLocalRef (jmessage);
// }

/* Check if all conditions are met to report GStreamer as initialized.
 * These conditions will change depending on the application */
static void check_initialization_complete (CustomData *cdata, int stream) 
{
	JNIEnv *env = get_jni_env ();
	CREGSTREAM * data = GetStreamFromCustomData(cdata, stream);

	CSIO_LOG(eLogLevel_debug, "stream=%d", stream);
	
	if(!data)
 	{
		CSIO_LOG(eLogLevel_error, "Could not obtain stream pointer for stream %d", stream);
		return;
	}

	CSIO_LOG(eLogLevel_debug, "stream=%d: initialized=%d, native_window=%p, main_loop=%p",
		stream, data->initialized, data->native_window,data->main_loop);
	
	if (!data->initialized && data->native_window && data->main_loop) 
	{
		CSIO_LOG(eLogLevel_debug, "Initialization complete for stream %d, video_sink=%p",
			stream, data->video_sink);

		/* The main loop is running and we received a native window, inform the sink about it */
		if(data->video_sink)
		{
			gst_video_overlay_set_window_handle (GST_VIDEO_OVERLAY(data->video_sink), (guintptr)data->native_window);
		}

		// Not used.
		//env->CallVoidMethod (cdata->app, on_gstreamer_initialized_method_id);
		//if (env->ExceptionCheck ()) {
		//	CSIO_LOG(eLogLevel_error, "Failed to call Java method");
		//	env->ExceptionClear ();
		//}
		
		data->initialized = TRUE;
	}
}

void csio_jni_init()
{
	int iStatus = CSIO_SUCCESS;

	currentSettingsDB->csioLogLevel = CSIO_DEFAULT_LOG_LEVEL;
	csio_setup_product_info(0);

	CSIOCnsIntf = new CSIOCnsCommon();
	CresNextDefaults = new CresNextDef();
	CSIOCnsIntf->addDefaults(CresNextDef::getDefaults());
	CSIOCnsIntf->setDefaults();

    // MNT - 7.5.15 - Indicate that init is being called from CresStreamSvc so that
    // it does not get SIGUSR2.
	iStatus = csio_Init(0);

	//TODO: send actual device mode setting down from Java code. Set to receiver for now.
	CSIOCnsIntf->setDeviceSpec_DEVICEMODE(0,SENDTOCRESSTORE_NONE);

	if(iStatus != CSIO_SUCCESS)
	{
		CSIO_LOG(eLogLevel_info, "csio_Init returned error %d\n", iStatus);
	}

	CSIO_LOG(eLogLevel_debug, "Done with init\n");
}

/*
 * Java Bindings
 */

/* Instruct the native code to create its internal data structure, pipeline and thread */
static void gst_native_init (JNIEnv* env, jobject thiz) 
{
	CustomData *cdata = g_new0 (CustomData, 1);
	CresDataDB = cdata;
	SET_CUSTOM_DATA (env, thiz, custom_data_field_id, cdata);
	GST_DEBUG_CATEGORY_INIT (debug_category, "gstreamer_jni", 0, "Android jni");
	gst_debug_set_threshold_for_name("gstreamer_jni", GST_LEVEL_ERROR);
	
	cdata->app = env->NewGlobalRef (thiz);
	init_custom_data(cdata);
	csio_jni_init();

    WfdSinkProjInit();
}

// Set up some defaults for streaming out using gstreamer.
void init_custom_data_out(CustomStreamOutData * cdata)
{	
	//cdata->surface = NULL;
    CSIO_LOG(eLogLevel_debug, "rtsp_server: init_custom_data_out cdata[0x%x],cdata->app[0x%x],streamOut[0x%x]",
            cdata,cdata->app,&cdata->streamOut[0]);

    strcpy(cdata->streamOut[0].rtsp_port, DEFAULT_RTSP_PORT);
    strcpy(cdata->streamOut[0].res_x, DEFAULT_RES_X);
    strcpy(cdata->streamOut[0].res_y, DEFAULT_RES_Y);
    strcpy(cdata->streamOut[0].frame_rate, DEFAULT_FRAME_RATE);
    strcpy(cdata->streamOut[0].bitrate, DEFAULT_BIT_RATE);
    strcpy(cdata->streamOut[0].iframe_interval, DEFAULT_IFRAME_INTERVAL);
}

/* Quit the main loop, remove the native thread and free resources */
static void gst_native_finalize (JNIEnv* env, jobject thiz) 
{
	CustomData *cdata = GET_CUSTOM_DATA (env, thiz, custom_data_field_id);
	int i;
	
	if (!cdata) return;

    WfdSinkProjDeInit();
	CSIO_LOG(eLogLevel_debug, "Deleting GlobalRef for app object at %p", cdata->app);
    env->DeleteGlobalRef ((jobject)gStreamIn_javaClass_id);
	env->DeleteGlobalRef (cdata->app);
	CSIO_LOG(eLogLevel_debug, "Freeing CustomData at %p", cdata);
	g_free (cdata);
	SET_CUSTOM_DATA (env, thiz, custom_data_field_id, NULL);
	CSIO_LOG(eLogLevel_debug, "Done finalizing");

	if (initialPorts != NULL)
	{
		free(initialPorts);
		initialPorts = NULL;
	}
}

/* Set pipeline to PLAYING state */
void gst_native_play (JNIEnv* env, jobject thiz, jint streamId)
{
    CREGSTREAM * data = GetStreamFromCustomData(CresDataDB, streamId);

    if (!data)
    {
        CSIO_LOG(eLogLevel_error, "Could not obtain stream pointer for stream %d", streamId);
        return;
    }

    data->isStarted = true;

    if(GetInPausedState(streamId))
    {
        CSIO_LOG(eLogLevel_debug, "GetInPausedState is true, resume now");
        switch (data->httpMode)
        {
        case eHttpMode_UNSPECIFIED:
        case eHttpMode_MJPEG:
        	// RTSP/MJPEG are live so use valves to pause
			CSIO_LOG(eLogLevel_verbose, "Unpausing pipeline with valves");
        	if(data->element_valve_a)
        		g_object_set(G_OBJECT(data->element_valve_a), "drop", FALSE, NULL);
        	if(data->element_valve_v)
        		g_object_set(G_OBJECT(data->element_valve_v), "drop", FALSE, NULL);
        	break;
        case eHttpMode_MP4:
        case eHttpMode_HLS:
        case eHttpMode_DASH:
        case eHttpMode_MSS:
        	// Not live so pause entire pipeline
			CSIO_LOG(eLogLevel_verbose, "Unpausing pipeline state");
        	csio_element_set_state(data->pipeline, GST_STATE_PLAYING);
        	break;
        default:
        	CSIO_LOG(eLogLevel_warning, "Unimplemented http mode %d", data->httpMode);
        }
    }

    SetInPausedState(streamId, 0);
	start_streaming_cmd(streamId);
}

/* Set pipeline to PAUSED state */
static void gst_native_pause (JNIEnv* env, jobject thiz, jint streamId)
{
    CREGSTREAM * data = GetStreamFromCustomData(CresDataDB, streamId);

    if (!data)
    {
        CSIO_LOG(eLogLevel_error, "Could not obtain stream pointer for stream %d", streamId);
        return;
    }
    
    if(GetInPausedState(streamId) == 0)
    {
        CSIO_LOG(eLogLevel_debug, "GetInPausedState is false, drop all");
        switch (data->httpMode)
        {
        case eHttpMode_UNSPECIFIED:
        case eHttpMode_MJPEG:
        	// RTSP/MJPEG are live so use valves to pause
			CSIO_LOG(eLogLevel_verbose, "Pausing pipeline with valves");
        	if(data->element_valve_a)
        		g_object_set(G_OBJECT(data->element_valve_a), "drop", TRUE, NULL);
        	if(data->element_valve_v)
        		g_object_set(G_OBJECT(data->element_valve_v), "drop", TRUE, NULL);
        	break;
        case eHttpMode_MP4:
        case eHttpMode_HLS:
        case eHttpMode_DASH:
        case eHttpMode_MSS:
        	// Not live so pause entire pipeline
			CSIO_LOG(eLogLevel_verbose, "Pausing pipeline state");
        	csio_element_set_state(data->pipeline, GST_STATE_PAUSED);
        	break;
        default:
        	CSIO_LOG(eLogLevel_warning, "Unimplemented http mode %d", data->httpMode);
        }
    }

    SetInPausedState(streamId, 1);
	pause_streaming_cmd(streamId);
}

void csio_jni_remove_video_rate_probe(int iStreamId)
{
    CREGSTREAM * data = GetStreamFromCustomData(CresDataDB, iStreamId);

    if (!data)
    {
        CSIO_LOG(eLogLevel_error, "Could not obtain stream pointer for stream %d", iStreamId);
        return;
    }

    if (data->video_rate_probe_id)
    {
        GstPad *pad;
        if (data->video_rate_probe_element == NULL) {
		    CSIO_LOG(eLogLevel_debug, "%s() video rate element is null even though id is not zero", __FUNCTION__);
		    data->video_rate_probe_id = 0;
        }
        pad = gst_element_get_static_pad(data->video_rate_probe_element, "sink");
        if (pad != NULL)
        {
		    CSIO_LOG(eLogLevel_debug, "%s() removing video rate probe (id=%lu) from element = %s @ %p (pad=%p)", __FUNCTION__,
		    		data->video_rate_probe_id, GST_ELEMENT_NAME(data->video_rate_probe_element), data->video_rate_probe_element, pad);
            gst_pad_remove_probe(pad, data->video_rate_probe_id);
            gst_object_unref(pad);
            data->video_rate_probe_element = NULL;
			data->video_rate_probe_id = 0;
        }
    }
}


void csio_jni_remove_probe (int iStreamId)
{
    CREGSTREAM * data = GetStreamFromCustomData(CresDataDB, iStreamId);

    if (!data)
    {
        CSIO_LOG(eLogLevel_error, "Could not obtain stream pointer for stream %d", iStreamId);
        return;
    }
    
    if(data->udpsrc_prob_id)
    {
        GstPad *pad;
        pad = gst_element_get_static_pad(data->udpsrc_prob_element, "src");
        if (pad != NULL)
        {
            gst_pad_remove_probe(pad, data->udpsrc_prob_id);
            gst_object_unref(pad);
            data->udpsrc_prob_id = 0;
        }
    }
    memset(data->sourceIP_addr,0,sizeof(data->sourceIP_addr));
    data->udpsrc_prob_timer.tv_sec = 0;
    data->udpsrc_prob_timer.tv_nsec = 0;
    data->udpsrc_prob_element = 0;
    data->udpsrc_prob_id = 0;

    csio_jni_remove_video_rate_probe(iStreamId);
}

static void set_queue_leaky(GstElement *queue, char *queue_id)
{
    gint leak;

    if (queue)
    {
    	g_object_get(G_OBJECT(queue), "leaky", &leak, NULL);
    	if (leak != (guint)2 /*GST_QUEUE_LEAK_DOWNSTREAM*/ )
    	{
    		g_object_set(G_OBJECT(queue), "leaky", (guint)2 /*GST_QUEUE_LEAK_DOWNSTREAM*/, NULL);
    		CSIO_LOG(eLogLevel_debug, "set %s leaky property to 'leak downstream'", queue_id);
    	}
    }
}

void csio_jni_change_queues_to_leaky(int id)
{
    CREGSTREAM * data = GetStreamFromCustomData(CresDataDB, id);
    set_queue_leaky(data->element_video_front_end_queue, "video-front-end-queue");
    set_queue_leaky(data->element_video_decoder_queue, "video-decoder-queue");
    set_queue_leaky(data->element_audio_front_end_queue, "audio-front-end-queue");
    set_queue_leaky(data->element_audio_decoder_queue, "audio-decoder-queue");
}

void csio_jni_setAutoBitrate(int id)
{
    CREGSTREAM * data = GetStreamFromCustomData(CresDataDB, id);
    if ( data && data->httpMode == eHttpMode_HLS ) {
        g_object_set(G_OBJECT(data->element_av[1]), "connection-speed", 0, NULL); // Once first frame is decoded, we can set to auto bitrate
    }
}

// This is still a current problem as of Android 4.4 and Gstreamer 1.8.2, we leak 2 sockets everytime a new url is connected to
static bool shouldCloseSockets()
{
	int i;
	bool closeSockets = true;

	for (i = 0; i < MAX_STREAMS; i++)
	{
		CREGSTREAM * data = GetStreamFromCustomData(CresDataDB, i);
		if (data == NULL)
		{
			CSIO_LOG(eLogLevel_error, "Failed to obtain CREGSTREAM for index %d", i);
			return true;
		}

		if (data->isStarted == true)
		{
			closeSockets = false;
			break;
		}
	}

	if (closeSockets == false)
	{
		unsigned int fd_count = 0;
		char buf[64];
		struct dirent *dp;
		struct rlimit limit;

		snprintf(buf, 64, "/proc/%i/fd/", getpid());

		DIR *dir = opendir(buf);
		while ((dp = readdir(dir)) != NULL) {
			fd_count++;
		}
		closedir(dir);

		getrlimit(RLIMIT_NOFILE, &limit);

		if ((limit.rlim_cur - fd_count) < c_minSocketLeft)
		{
			CSIO_LOG(eLogLevel_warning, "Warning: Closing sockets before max is reached");
			closeSockets = true;
		}
	}

	return closeSockets;
}

// TODO: If we ever use eventfd elsewhere we will need to make sure that we dont close it here
static void closeEventfdLeak()
{
	// In Gstreamer 1.8.x a anon_inode[eventfd] leak was introduced, we must close these as well
	DIR *fdFolder;
	struct dirent *next_file;
	char tmp[512];
	char filepath[256];
	ssize_t ret;

	fdFolder = opendir(PROC_SELF_FD_FILEPATH);

	if (fdFolder != NULL)
	{
		while ( (next_file = readdir(fdFolder)) != NULL )
		{
			// build the path for each file in the folder
			sprintf(filepath, "%s/%s", PROC_SELF_FD_FILEPATH, next_file->d_name);

			ret = readlink(filepath, &(tmp[0]), sizeof(tmp));
			if (ret >= 0)
			{
				tmp[ret] = '\0';	// readlink will not terminate buf w/ NULL

				if (strstr(&(tmp[0]), "anon_inode:[eventfd]") != NULL)
				{
					char *end;

					int fd = (int)strtol(next_file->d_name, &end, 10);        //10 specifies base-10
					if (end != next_file->d_name)     //if no characters were converted these pointers are equal
					{
						CSIO_LOG(eLogLevel_debug, "Closing anon_inode fd %d", fd);
						if (close(fd) == -1)
							CSIO_LOG(eLogLevel_error, "Error: Could not close fd %d, errno = %d", fd, errno);
					}
				}
			}
		}
		closedir(fdFolder);
	}
}

void csio_jni_cleanup (int iStreamId)
{
    int i;
	unsigned int index;
	int portIndex;
    CREGSTREAM * data = GetStreamFromCustomData(CresDataDB, iStreamId);

    if (!data)
    {
        CSIO_LOG(eLogLevel_error, "Could not obtain stream pointer for stream %d", iStreamId);
        return;
    }
    
    memset(data->sourceIP_addr,0,sizeof(data->sourceIP_addr));
    data->udpsrc_prob_timer.tv_sec = 0;
    data->udpsrc_prob_timer.tv_nsec = 0;
    data->udpsrc_prob_element = 0;
    data->udpsrc_prob_id = 0;

    data->pipeline     = NULL;
    data->video_sink   = NULL;
    data->audio_sink   = NULL;
    data->amcvid_dec   = NULL;
    data->element_video_front_end_queue = NULL;
    data->element_video_decoder_queue = NULL;
    data->element_audio_front_end_queue = NULL;
    data->element_audio_decoder_queue = NULL;
    data->element_fake_dec = NULL;
    data->element_zero     = NULL;
    data->element_valve_v    = NULL;
    data->element_valve_a    = NULL;
    data->element_audiorate  = NULL;
    data->streamProtocolId = (eProtocolId)0;
    data->httpMode  = eHttpMode_UNSPECIFIED;
	data->mpegtsPresent = FALSE;

    for (i = 0; i < MAX_ELEMENTS; i++)
    {
        data->element_av[i] = NULL;
        data->element_a[i] = NULL;
        data->element_v[i] = NULL;   
    }
    
    data->using_glimagsink = 0;

    csio_jni_FreeMainContext(iStreamId);

// Do not close AF_UNIX sockets or file descriptors since doing so interferes with sockets opened by streamout.
// These leaks were addressed by a gstreamer check-in for bug #118244.
//	if (shouldCloseSockets()) //all streams stopped or near max fd limit
//	{
//		// Close all the AF_UNIX sockets that Android creates when querying DNS
//		// On Jellybean, these sockets were accruing with each connection, eventually exceeding the maximum and crashing csio
//		// Fixes bug#92551
//		// Get the maximum number of file descriptors this process can open
//		struct rlimit limit;
//		getrlimit(RLIMIT_NOFILE, &limit);
//
//		// Don't both checking fd 0, 1, and 2
//		for (index = 3; index < limit.rlim_cur; ++index)
//		{
//			struct sockaddr addr;
//			socklen_t len = sizeof(addr);
//
//			// if ( (this fd is open and is a socket) && (this socket is AF_UNIX) )
//			if ( (getsockname(index, &addr, &len) == 0) && (addr.sa_family == AF_UNIX) )
//			{
//				bool portFound = false;
//
//				for (portIndex =0; portIndex < c_maxNumInitialPorts; portIndex++)
//				{
//					if (initialPorts[portIndex] == index)
//					{
//						portFound = true;
//						break;
//					}
//					else if (initialPorts[portIndex] == 0)
//					{
//						// reached end of initialPorts
//						portFound = false;
//						break;
//					}
//				}
//
//
//				if (!portFound)
//				{
//					CSIO_LOG(eLogLevel_debug, "Closing socket fd %d\n", index);
//					close(index);
//				}
//			}
//		}
//
//		closeEventfdLeak();
//	}
}

void * jni_stop (void * arg)
{
	jint streamId = (*(jint *)arg);
	SetInPausedState(streamId,0);
	stop_streaming_cmd(streamId);
	csio_jni_cleanup(streamId);
   pthread_cond_broadcast(&stop_completed_sig); //Flags that stop has completed
	pthread_exit(NULL);
}

void csio_jni_stop(int streamId)
{
	jint timeout_sec = stop_timeout_sec;
	struct timespec stopTimeout;
	int result;
	pthread_t gst_stop_thread;
	pthread_mutex_t stop_completed_lock;

	//init mutex and thread attributes
	pthread_cond_init(&stop_completed_sig, NULL);
	pthread_mutex_init(&stop_completed_lock, NULL);
	pthread_attr_t attr;
	pthread_attr_init(&attr);
	pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_DETACHED);
	clock_gettime(CLOCK_REALTIME, &stopTimeout);
	stopTimeout.tv_sec += timeout_sec;

	//Kick off stop thread
	pthread_create(&gst_stop_thread, &attr, jni_stop, &streamId);

	//Wait for timeout or completion
	pthread_mutex_lock(&stop_completed_lock);
	result = pthread_cond_timedwait(&stop_completed_sig, &stop_completed_lock, &stopTimeout);
	pthread_mutex_unlock(&stop_completed_lock);

	//Cleanup pthread objects
	pthread_attr_destroy(&attr);
	pthread_cond_destroy(&stop_completed_sig);
	pthread_mutex_destroy(&stop_completed_lock);

	if (result == ETIMEDOUT)
	{
		CSIO_LOG(eLogLevel_error, "Stop timed out after %d seconds\n", timeout_sec);
		ResetStartedPlay(streamId);
		csio_jni_recoverDucati();
	}
	else if (result != 0)
		CSIO_LOG(eLogLevel_error, "Unknown error occurred while waiting for stop to complete, error = %d\n", result);
}

/* Set pipeline to PAUSED state */
void gst_native_stop (JNIEnv* env, jobject thiz, jint streamId, jint stopTimeout_sec)
{
	CREGSTREAM * data = GetStreamFromCustomData(CresDataDB, streamId);

	if (!data)
		CSIO_LOG(eLogLevel_error, "Could not obtain stream pointer for stream %d, failed to set isStarted state", streamId);
	else
	{
	    if (data->isStarted)
	    {
	        data->isStarted = false;

	        csio_jni_stop((int)streamId);
	    }
	    else
	    	csio_SendVideoPlayingStatusMessage((int)streamId, STREAMSTATE_STOPPED);
	}
}

/* Static class initializer: retrieve method and field IDs */
static jboolean gst_native_class_init (JNIEnv* env, jclass klass) 
{
	CSIO_LOG(eLogLevel_debug, "gst_native_class_init\n");
	custom_data_field_id = env->GetFieldID (klass, "native_custom_data", "J");
	set_message_method_id = env->GetMethodID (klass, "setMessage", "(Ljava/lang/String;)V");
	//on_gstreamer_initialized_method_id = env->GetMethodID (klass, "onGStreamerInitialized", "()V");

	//if (!custom_data_field_id || !set_message_method_id || !on_gstreamer_initialized_method_id) {
    if (!custom_data_field_id || !set_message_method_id) {		
		/* We emit this message through the Android log instead of the GStreamer log because the later
		* has not been initialized yet.
		*/
		CSIO_LOG(eLogLevel_error, "The calling class does not implement all necessary interface methods");
		return JNI_FALSE;
	}
	return JNI_TRUE;
}

//this is to set up the surface format
static void gst_jni_setup_surface_format(JNIEnv *env,ANativeWindow *new_native_window,CREGSTREAM * data,jobject surface)
{
    int format = 0x100;
    int err = 0;
    int usage = 0;
    int queuesToNativeWindow = 0;
    int minUndequeuedBufs = 0;

    CSIO_LOG(eLogLevel_debug, "ANativeWindow format was %x", ANativeWindow_getFormat(new_native_window));

    err = native_window_set_buffers_format(new_native_window, format);
    if (err != 0)
    {
        CSIO_LOG(eLogLevel_error, "Failed to set buffers format to %d", format);
        return;
    }

    CSIO_LOG(eLogLevel_debug, "ANativeWindow after set buffers format, width=%d, height=%d, format=0x%x",
            ANativeWindow_getWidth(new_native_window), ANativeWindow_getHeight(new_native_window),
            ANativeWindow_getFormat(new_native_window));

    usage = GRALLOC_USAGE_PROTECTED | GRALLOC_USAGE_HW_TEXTURE
            | GRALLOC_USAGE_EXTERNAL_DISP; // Sunita - standard set of flags we set to allow HW acceleration of the layers
    native_window_set_usage(new_native_window, usage);

    err = native_window_set_scaling_mode(new_native_window,
            NATIVE_WINDOW_SCALING_MODE_SCALE_TO_WINDOW);
    if (err != 0)
    {
        CSIO_LOG(eLogLevel_error, "Failed to set buffers scaling_mode : %s (%d)",strerror(-err), -err);
        return;
    }

    err = new_native_window->query(new_native_window,
            NATIVE_WINDOW_QUEUES_TO_WINDOW_COMPOSER, &queuesToNativeWindow);
    if (err != 0)
    {
        CSIO_LOG(eLogLevel_error, "error authenticating native window: %d", err);
        return;
    }

    err = new_native_window->query(new_native_window,
            NATIVE_WINDOW_MIN_UNDEQUEUED_BUFFERS, (int*) &minUndequeuedBufs);
    if (err != 0)
    {
        CSIO_LOG(eLogLevel_error, "NATIVE_WINDOW_MIN_UNDEQUEUED_BUFFERS query failed: %s (%d)", strerror(-err), -err);
        return;
    }

    if (data->surface)
    {
        CSIO_LOG(eLogLevel_debug, "Delete GlobalRef before adding new: %p", data->surface);
        env->DeleteGlobalRef((jobject)data->surface);
        data->surface = NULL;
    }
    data->surface = env->NewGlobalRef(surface);
    CSIO_LOG(eLogLevel_debug, "native window = %p,surface[%p],data->surface[%p]", data->native_window,surface,data->surface);
}

static void gst_native_surface_init(JNIEnv *env, jobject thiz, jobject surface, jint stream)
{
    CustomData *cdata = GET_CUSTOM_DATA (env, thiz, custom_data_field_id);
    ANativeWindow *new_native_window;
    CREGSTREAM * data;

    CSIO_LOG(eLogLevel_debug, "surface=%p, stream=%d", surface, stream);

    if (!surface)
    {
        CSIO_LOG(eLogLevel_error, "No surface for stream %d", stream);
        return;
    }

    new_native_window = ANativeWindow_fromSurface(env, surface);
    if (!new_native_window)
    {
        CSIO_LOG(eLogLevel_error, "No native window for stream %d", stream);
        return;
    }

    if (!cdata)
    {
        CSIO_LOG(eLogLevel_error, "Could not access custom data");
        return;
    }

    data = GetStreamFromCustomData(cdata, stream);
    if (!data)
    {
        CSIO_LOG(eLogLevel_error, "Could not obtain stream pointer for stream %d", stream);
        return;
    }

	CSIO_LOG(eLogLevel_debug, "Received surface %p (native window %p) for stream %d, video_sink=%p",
			   surface, new_native_window, stream, data->video_sink);

    if (data->native_window)
    {
        ANativeWindow_release(data->native_window);
        if (data->native_window == new_native_window)
        {
			CSIO_LOG(eLogLevel_debug, "New native window is the same as the previous one %p", data->native_window);
            if (data->video_sink)
            {
                // From tutorial 3 comments:
                // "We need to call gst_x_overlay_expose() twice
                // because of the way the surface changes propagate down the OpenGL ES / EGL pipeline
                // (The only video sink available for Android in the GStreamer SDK uses OpenGL ES).
                // By the time we call the first expose,
                // the surface that the sink will pick up still contains the old size."
                gst_video_overlay_expose(GST_VIDEO_OVERLAY (data->video_sink));
                gst_video_overlay_expose(GST_VIDEO_OVERLAY (data->video_sink));
            }
            return;
        }
        else
        {
            CSIO_LOG(eLogLevel_debug, "Released previous native window %p", data->native_window);
            data->initialized = FALSE;
        }
    }
    data->native_window = new_native_window;

    gst_jni_setup_surface_format(env, new_native_window, data, surface);

    check_initialization_complete(cdata, stream);
}

eStreamState gst_native_get_current_stream_state(int stream)
{
	eStreamState currentStreamState;
	JNIEnv *env = get_jni_env ();

	jmethodID getCurrentStreamState = env->GetMethodID((jclass)gStreamIn_javaClass_id, "getCurrentStreamState", "(I)I");

	currentStreamState = (eStreamState)env->CallIntMethod(CresDataDB->app, getCurrentStreamState, stream);

	if (env->ExceptionCheck ()) {
		CSIO_LOG(eLogLevel_error, "Failed to call Java method 'getCurrentStreamState'");
		env->ExceptionClear ();
	}

	CSIO_LOG(eLogLevel_debug, "currentStreamState(%d) = %d", stream, (jint)currentStreamState);

	return currentStreamState;
}

static void gst_native_surface_finalize (JNIEnv *env, jobject thiz, jint stream) 
{
	CustomData *cdata = GET_CUSTOM_DATA (env, thiz, custom_data_field_id);
	CREGSTREAM * data;	
	
	if (!cdata) return;	

	data = GetStreamFromCustomData(cdata, stream);
	if(!data)
	{
		CSIO_LOG(eLogLevel_error, "Could not obtain stream pointer for stream %d", stream);
		return;
	}
	
	CSIO_LOG(eLogLevel_debug, "Releasing native window %p for stream %d",
			   data->native_window, stream);
	
	if (data->video_sink) 
	{
		gst_video_overlay_set_window_handle (GST_VIDEO_OVERLAY (data->video_sink), (guintptr)NULL);
		//gst_element_set_state (data->pipeline, GST_STATE_READY);
	}

	if (data->native_window != NULL)
		ANativeWindow_release (data->native_window); //TODO: java seems to already handle this
	data->native_window = NULL;
	data->initialized = FALSE;

	if(data->surface)
	{
	    CSIO_LOG(eLogLevel_debug, "Delete GlobalRef %p", data->surface);
	    env->DeleteGlobalRef((jobject)data->surface);
	    data->surface = NULL;
	}
	//TODO: when this will be called?
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetRTCPDestIP(JNIEnv *env, jobject thiz, jstring rtcpIp_jstring, jint streamId)
{
    const char * rtcpIp_cstring = env->GetStringUTFChars( rtcpIp_jstring , NULL ) ;
    if (rtcpIp_cstring == NULL) return;

    CSIO_LOG(eLogLevel_debug, "RTCP dest ip: '%s'", rtcpIp_cstring);

    CREGSTREAM * data = GetStreamFromCustomData(CresDataDB, streamId);
    if (data)
    {
        strncpy(data->rtcp_dest_ip_addr, rtcpIp_cstring, 32);
		data->rtcp_dest_ip_addr[31] = 0;
    }

    env->ReleaseStringUTFChars(rtcpIp_jstring, rtcpIp_cstring);
}

/* Set Stream URL */
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetServerUrl(JNIEnv *env, jobject thiz, jstring url_jstring, jint streamId)
{
	int restartStream = 0;
	const char * url_cstring = env->GetStringUTFChars( url_jstring , NULL ) ;
	if (url_cstring == NULL) return;

	CSIO_LOG(eLogLevel_debug, "Using server url: '%s'", url_cstring);
	csio_SetURL(streamId, (char *)url_cstring, strlen(url_cstring) + 1);

	env->ReleaseStringUTFChars(url_jstring, url_cstring);
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetRtspPort(JNIEnv *env, jobject thiz, jint port, jint streamId)
{
	CSIO_LOG(eLogLevel_debug, "Using RtspPort: '%d'", port);
	CSIOCnsIntf->setStreamTxRx_RTSPPORT(streamId, port, SENDTOCRESSTORE_NONE);
	CSIO_LOG(eLogLevel_debug, "RtspPort in cache: '%d'", CSIOCnsIntf->getStreamTxRx_RTSPPORT(streamId));
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetTsPort(JNIEnv *env, jobject thiz, jint port, jint streamId)
{
	CSIO_LOG(eLogLevel_debug, "Using tsPort: '%d'", port);
	csio_SetPortNumber( streamId, port, c_TSportNumber );
	CSIO_LOG(eLogLevel_debug, "tsPort in cache: '%ld'", CSIOCnsIntf->getStreamTxRx_TSPORT(streamId));
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetHdcpEncrypt(JNIEnv *env, jobject thiz, jboolean flag, jint streamId)
{
    CREGSTREAM * data = GetStreamFromCustomData(CresDataDB, streamId);

    if(!data)
    {
        CSIO_LOG(eLogLevel_error, "Could not obtain stream pointer for stream %d", streamId);
        return;
    }

    csio_SetHDCPEncrypt(streamId, flag);

    data->doHdcp = (gboolean)flag;
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetRtpVideoPort(JNIEnv *env, jobject thiz, jint port, jint streamId)
{
	CSIO_LOG(eLogLevel_debug, "Using rtpVideoPort: '%d'", port);
	csio_SetPortNumber( streamId, port, c_RTPVideoPortNumber );
	CSIO_LOG(eLogLevel_debug, "rtpVideoPort in cache: '%ld'", CSIOCnsIntf->getStreamTxRx_RTPVIDEOPORT(streamId));
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetRtpAudioPort(JNIEnv *env, jobject thiz, jint port, jint streamId)
{
	CSIO_LOG(eLogLevel_debug, "Using rtpAudioPort: '%d'", port);
	csio_SetPortNumber( streamId, port, c_RTPAudioPortNumber );
	CSIO_LOG(eLogLevel_debug, "rtpAudioPort in cache: '%ld'", CSIOCnsIntf->getStreamTxRx_RTPAUDIOPORT(streamId));
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetSessionInitiation(JNIEnv *env, jobject thiz, jint initMode, jint streamId)
{
	CSIO_LOG(eLogLevel_debug, "Using sessionInitiationMode: '%d'", initMode);
	CSIOCnsIntf->setStreamTxRx_SESSIONINITIATION(streamId, initMode, SENDTOCRESSTORE_NONE);
	csio_SetSessionInitiationMode(streamId,initMode);
	CSIO_LOG(eLogLevel_debug, "sessionInitiationMode in cache: '%d'", CSIOCnsIntf->getStreamTxRx_SESSIONINITIATION(streamId));
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetTransportMode(JNIEnv *env, jobject thiz, jint transportMode, jint streamId)
{
	CSIO_LOG(eLogLevel_debug, "Using tsEnabled: '%d'", transportMode);
	CSIOCnsIntf->setStreamTxRx_TRANSPORTMODE(streamId, transportMode, SENDTOCRESSTORE_NONE);
	csio_SetTransportMode(streamId,transportMode);
	CSIO_LOG(eLogLevel_debug, "tsEnabled in cache: '%d'", CSIOCnsIntf->getStreamTxRx_TRANSPORTMODE(streamId));
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetMulticastAddress(JNIEnv *env, jobject thiz, jstring multicastIp_jstring, jint streamId)
{
	char *buf = NULL;
	
	const char * multicastIp_cstring = env->GetStringUTFChars( multicastIp_jstring , NULL ) ;
	if (multicastIp_cstring == NULL) return;

	CSIO_LOG(eLogLevel_debug, "Using multicastAddress: '%s'", multicastIp_cstring);
	std::string strValue = multicastIp_cstring;
	CSIOCnsIntf->setStreamTxRx_MULTICASTADDRESS(streamId, strValue, SENDTOCRESSTORE_NONE);
	buf = (char *) malloc(strlen(multicastIp_cstring)+1);
	CSIOCnsIntf->getStreamTxRx_MULTICASTADDRESS(streamId, buf, strlen(multicastIp_cstring)+1);
	CSIO_LOG(eLogLevel_debug, "multicastAddress in cache: '%s'", buf);
	free(buf);

	env->ReleaseStringUTFChars(multicastIp_jstring, multicastIp_cstring);
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetStreamingBuffer(JNIEnv *env, jobject thiz, jint buffer_ms, jint streamId)
{
    CSIO_LOG(eLogLevel_debug, "Using streamingBuffer: '%d'", buffer_ms);
	if(buffer_ms < DEFAULT_MIN_STRING_BUFFER)
	{
	    buffer_ms = DEFAULT_MIN_STRING_BUFFER;
	    CSIO_LOG(eLogLevel_debug, "Clip streamingBuffer to: '%d'", DEFAULT_MIN_STRING_BUFFER);
	}

	if(buffer_ms > DEFAULT_MAX_STRING_BUFFER)
    {
        buffer_ms = DEFAULT_MAX_STRING_BUFFER;
        CSIO_LOG(eLogLevel_debug, "Clip streamingBuffer to: '%d'", DEFAULT_MIN_STRING_BUFFER);
    }

	CSIOCnsIntf->setStreamRx_BUFFER(streamId, buffer_ms, SENDTOCRESSTORE_NONE);
	CSIO_LOG(eLogLevel_debug, "streamingBuffer in cache: '%d'", CSIOCnsIntf->getStreamRx_BUFFER(streamId));
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetXYlocations(JNIEnv *env, jobject thiz, jint xLocation, jint yLocation, jint streamId)
{
//TODO: delete
//	currentSettingsDB->settingsMessage.msg[streamId].left = xLocation;
//	currentSettingsDB->settingsMessage.msg[streamId].top = yLocation;
//	CSIO_LOG(eLogLevel_debug, "xLocation in currentSettingsDB: '%d'", currentSettingsDB->settingsMessage.msg[streamId].left);
//	CSIO_LOG(eLogLevel_debug, "yLocation in currentSettingsDB: '%d'", currentSettingsDB->settingsMessage.msg[streamId].top);
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetStatistics(JNIEnv *env, jobject thiz, jboolean enabled, jint streamId)
{
	CSIOCnsIntf->setStreamTxRx_ISSTATISTICSENABLED(streamId, (int)enabled, SENDTOCRESSTORE_NONE);

	if (nativeGetCurrentStreamState(streamId) == STREAMSTATE_STARTED)
	{
		if (enabled)
			start_sending_stream_statistics(streamId);
		//we will keep thread running all the time.
	}

	//CSIO_LOG(eLogLevel_debug, "statisticsEnabled in cache: '%d'", CSIOCnsIntf->getStreamTxRx_ISSTATISTICSENABLED(streamId));
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeResetStatistics(JNIEnv *env, jobject thiz, jint streamId)
{
	csio_send_stats_no_bitrate(0, 0, 0, 0); //omit bitrate so that it won't be sent to control system

	reset_statistics(streamId);
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetUserName(JNIEnv *env, jobject thiz, jstring userName_jstring, jint sessionId)
{
	char *buf = NULL;
	const char * userName_cstring = env->GetStringUTFChars( userName_jstring , NULL ) ;
	if (userName_cstring == NULL) return;

	CSIO_LOG(eLogLevel_debug, "Using UserName: '%s'", userName_cstring);
	std::string strValue = userName_cstring;
	CSIOCnsIntf->setStreamTxRx_USERNAME(sessionId, strValue, SENDTOCRESSTORE_NONE);
	buf = (char *) malloc(strlen(userName_cstring)+1);
	CSIOCnsIntf->getStreamTxRx_USERNAME(sessionId, buf, strlen(userName_cstring)+1);
	CSIO_LOG(eLogLevel_debug, "UserName in cache: '%s'", buf);
	free(buf);

	env->ReleaseStringUTFChars(userName_jstring, userName_cstring);
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetPassword(JNIEnv *env, jobject thiz, jstring password_jstring, jint sessionId)
{
	char *buf = NULL;
	const char * password_cstring = env->GetStringUTFChars( password_jstring , NULL ) ;
	if (password_cstring == NULL) return;

	CSIO_LOG(eLogLevel_debug, "Using password: '%s'", password_cstring);
	std::string strValue = password_cstring;
	CSIOCnsIntf->setStreamTxRx_PASSWORD(sessionId, strValue, SENDTOCRESSTORE_NONE);
	buf = (char *) malloc(strlen(password_cstring)+1);
	CSIOCnsIntf->getStreamTxRx_PASSWORD(sessionId, buf, strlen(password_cstring)+1);
	CSIO_LOG(eLogLevel_debug, "Password in cache: '%s'", buf);
	free(buf);

	env->ReleaseStringUTFChars(password_jstring, password_cstring);
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetVolume(JNIEnv *env, jobject thiz, jint volume, jint sessionId)
{
	double convertedVolume = (double)volume / 100;	//convert from 0-100 to 0.0-1.0
	CREGSTREAM * data = GetStreamFromCustomData(CresDataDB, sessionId);
	if (data)
		data->audioVolume = convertedVolume;
	else
		CSIO_LOG(eLogLevel_error, "Unable to access data object");

	int ret = csio_SetLinearVolume(sessionId, convertedVolume);
	CSIO_LOG(eLogLevel_debug, "Return from csio_SetLinearVolume = %d", ret);
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetStopTimeout(JNIEnv *env, jobject thiz, jint stopTimeout_sec)
{
	stop_timeout_sec = stopTimeout_sec;
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetNewSink(JNIEnv *env, jobject thiz, jboolean enabled, jint sessionId)
{
//TODO: delete
	//currentSettingsDB->videoSettings[sessionId].videoSinkSelect = (UINT8)enabled;
	//CSIO_LOG(eLogLevel_debug, "new Sink Enabled in currentSettingsDB: %d", currentSettingsDB->videoSettings[sessionId].videoSinkSelect);
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeDropAudio(JNIEnv *env, jobject thiz, jboolean enabled, jboolean dropAudioPipeline, jint sessionId)
{
	if (dropAudioPipeline)
	{
		if (enabled)
			debug_blocking_audio = 1;
		else
			debug_blocking_audio = 0;
	}
	else
	{
		// Set all streams to new drop Audio mode
		int i;
		CREGSTREAM * data;
		for(i=0; i<MAX_STREAMS; i++)
		{
			data = GetStreamFromCustomData(CresDataDB, i);
			if(data)
				data->dropAudio = enabled;
		}

		data = GetStreamFromCustomData(CresDataDB, sessionId);

		if(!data)
		{
			CSIO_LOG(eLogLevel_error, "Could not obtain stream pointer for stream %d", sessionId);
			return;
		}

		if(data->element_valve_a)
		{
			g_object_set(G_OBJECT(data->element_valve_a), "drop", enabled, NULL);
			CSIO_LOG(eLogLevel_debug, "set audio valve drop property to:%d",enabled);
		}
	}
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetLogLevel(JNIEnv *env, jobject thiz, jint logLevel)
{
	currentSettingsDB->csioLogLevel = (eLogLevel)logLevel;
	CSIO_LOG(eLogLevel_debug, "Setting minimum printed log level to %d", logLevel);
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetTcpMode(JNIEnv *env, jobject thiz, jint tcpMode, jint sessionId)
{
	CSIOCnsIntf->setStreamRx_TCPMODE(sessionId, tcpMode, SENDTOCRESSTORE_NONE);
    CSIO_LOG(eLogLevel_debug, "Setting window[%d] tcpMode to %d", sessionId, tcpMode);

    csio_SetRtspNetworkMode(sessionId,tcpMode);
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeInitUnixSocketState(JNIEnv *env, jobject thiz)
{
	int i;

	//Store all open AF_UNIX ports before starting Gstreamer so that we don't kill on cleanup
	struct rlimit limit;
	getrlimit(RLIMIT_NOFILE, &limit);

	unsigned initialPortIndex =0;
	initialPorts = (unsigned int *)malloc(sizeof(unsigned int) * c_maxNumInitialPorts);

	memset(initialPorts, 0, (sizeof(unsigned int) * c_maxNumInitialPorts));

	// Don't bother checking fd 0, 1, and 2
	for (i = 3; i < limit.rlim_cur; ++i)
	{
		struct sockaddr addr;
		socklen_t len = sizeof(addr);

		// if ( (this fd is open and is a socket) && (this socket is AF_UNIX) )
		if ( (getsockname(i, &addr, &len) == 0) && (addr.sa_family == AF_UNIX) )
		{
			if (initialPortIndex < c_maxNumInitialPorts)
			{
				initialPorts[initialPortIndex++] = i;
			}
			else
				CSIO_LOG(eLogLevel_error, "ERROR: ran out of sockets to store, increase array size!!!!\n");
		}
	}
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetFieldDebugJni(JNIEnv *env, jobject thiz, jstring cmd_jstring, jint sessionId)
{
    int iStringLen = 0;
    int fieldNum = 0;
    int iEnable = 0;
    char *EndPtr,*CmdPtr;
    int i = 0;
    char namestring[1024];

    const char * cmd_cstring = env->GetStringUTFChars( cmd_jstring , NULL ) ;
    if (cmd_cstring == NULL)
    {
        CSIO_LOG(eLogLevel_info, "cmd_cstring is NULL");
        return;
    }

    CREGSTREAM * data = GetStreamFromCustomData(CresDataDB, sessionId);
    if(!data)
    {
        CSIO_LOG(eLogLevel_error, "Could not obtain stream pointer for stream %d", sessionId);
        return;
    }

    iStringLen = strlen(cmd_cstring);
    CSIO_LOG(eLogLevel_debug, "Set Field debug: '%s', iStringLen:%d", cmd_cstring,iStringLen);
    if(cmd_cstring == 0)
    {
        CSIO_LOG(eLogLevel_info, "Received NULL string");
    }
    else
    {
        //save command for streamout
        strcpy(namestring,cmd_cstring);

        //1. if empty string, then print surrent settings
        CmdPtr = strtok((char *)cmd_cstring,", ");
        if ( CmdPtr == NULL )
        {
            csio_jni_printFieldDebugInfo();
        }
        else
        {
            LocalConvertToUpper(CmdPtr);
            //2. the first one has to be string
            if (!strcmp(CmdPtr, "HELP"))
            {
                csio_jni_printFieldDebugInfo();
            }
            else if (!strcmp(CmdPtr, "STREAMOUT"))
            {
                CSIO_LOG(eLogLevel_info, "namestring[%s]\r\n",namestring);
                LocalConvertToUpper(namestring);
                CSIO_LOG(eLogLevel_info, "namestring converted[%s]\r\n",namestring);

                jni_rtsp_server_debug(namestring);
            }
            else if (!strcmp(CmdPtr, "ON"))
            {
                CmdPtr = strtok(NULL, ", ");
                if (CmdPtr == NULL)
                {
                    CSIO_LOG(eLogLevel_info, "Invalid Format, need a parameter\r\n");
                }
                else
                {
                    fieldNum = (int) strtol(CmdPtr, &EndPtr, 10);

                    if ( IsValidSpecialFieldIndex(fieldNum) )
                    {
                        EnableSpecialFieldDebugIndex(fieldNum);
                        CSIO_LOG(eLogLevel_debug, "turn ON %d\r\n",fieldNum);
                    }
                    else
                    {
                        CSIO_LOG(eLogLevel_info, "Invalid fdebug number:%d\r\n",fieldNum);
                    }
                }
            }
            else if (!strcmp(CmdPtr, "OFF"))
            {
                CmdPtr = strtok(NULL, ", ");
                if (CmdPtr == NULL)
                {
                    CSIO_LOG(eLogLevel_info, "Invalid Format, need a parameter\r\n");
                }
                else
                {
                    fieldNum = (int) strtol(CmdPtr, &EndPtr, 10);

                    if ( IsValidSpecialFieldIndex(fieldNum) )
                    {
                        DisableSpecialFieldDebugIndex(fieldNum);
                        CSIO_LOG(eLogLevel_debug, "turn OFF %d\r\n",fieldNum);
                    }
                    else
                    {
                        CSIO_LOG(eLogLevel_info, "Invalid fdebug number:%d\r\n",fieldNum);
                    }
                }
            }
            else if (!strcmp(CmdPtr, "BLOCK_AUDIO"))
            {
                CmdPtr = strtok(NULL, ", ");
                if (CmdPtr == NULL)
                {
                    CSIO_LOG(eLogLevel_debug, "BLOCK_AUDIO is %s",
                               (IsSpecialFieldDebugIndexActive(FIELD_DEBUG_BLOCK_AUDIO) ? "ON" : "OFF"));
                }
                else
                {
                    LocalConvertToUpper(CmdPtr);
                    if (!strcmp(CmdPtr, "ON"))
                    {
                        EnableSpecialFieldDebugIndex(FIELD_DEBUG_BLOCK_AUDIO);
                        debug_blocking_audio = 1;
                        CSIO_LOG(eLogLevel_debug, "BLOCK_AUDIO enabled\r\n");
                    }
                    else if (!strcmp(CmdPtr, "OFF"))
                    {
                        DisableSpecialFieldDebugIndex(FIELD_DEBUG_BLOCK_AUDIO);
                        debug_blocking_audio = 0;
                        CSIO_LOG(eLogLevel_debug, "BLOCK_AUDIO disabled\r\n");
                    }
                    else
                    {
                        CSIO_LOG(eLogLevel_info, "Invalid Format, On or OFF\r\n");
                    }
                }
            }
            else if (!strcmp(CmdPtr, "FLUSH_PIPELINE"))
            {
                CSIO_LOG(eLogLevel_debug, "flushing pipeline...");
                GstEvent* flush_start = gst_event_new_flush_start();
                gboolean ret = FALSE;
                ret = gst_element_send_event(GST_ELEMENT(data->pipeline), flush_start); //Try element 0
                if (!ret)
                {
                    CSIO_LOG(eLogLevel_warning, "failed to send flush-start event");
                }
                else
                {
                    //true: to reset timestamp, false not to
                    GstEvent* flush_stop = gst_event_new_flush_stop(TRUE);

                    ret = gst_element_send_event(GST_ELEMENT(data->pipeline), flush_stop);
                    if (!ret)
                        CSIO_LOG(eLogLevel_warning, "failed to send flush-stop event");
                    else
                        CSIO_LOG(eLogLevel_debug, "Just flushed pipeline");
                }
            }
            else if (!strcmp(CmdPtr, "SET_CATEGORY_DEBUG_LEVEL"))
            {
                CmdPtr = strtok(NULL, ", ");
                if (CmdPtr == NULL)
                {
                    CSIO_LOG(eLogLevel_info, "Invalid Format, need a parameter\r\n");
                }
                else
                {
                    //save category name
                    strcpy(namestring,CmdPtr);
                    CSIO_LOG(eLogLevel_debug, "CmdPtr:%s\r\n",CmdPtr);
                    CSIO_LOG(eLogLevel_debug, "namestring:%s\r\n",namestring);
                    //get debug level
                    CmdPtr = strtok(NULL, ", ");
                    if (CmdPtr == NULL)
                    {
                        CSIO_LOG(eLogLevel_warning, "Invalid Format, need a parameter\r\n");
                    }
                    else
                    {
                        fieldNum = (int) strtol(CmdPtr, &EndPtr, 10);
                        if (IsValidDebugLevel(fieldNum))
                        {
                            gst_debug_set_threshold_for_name(namestring, (GstDebugLevel)fieldNum);
                            amcviddec_debug_level = fieldNum;
                            CSIO_LOG(eLogLevel_debug, "set [%s] debug level to: %d\r\n",namestring,fieldNum);
                        }
                        else
                        {
                            CSIO_LOG(eLogLevel_info, "Invalid gst_debug_level:%d\r\n",fieldNum);
                        }
                    }
                }
            }
            else if (!strcmp(CmdPtr, "AMCVIDDEC_DEBUG_LEVEL"))
            {
                CmdPtr = strtok(NULL, ", ");
                if (CmdPtr == NULL)
                {
                    CSIO_LOG(eLogLevel_info, "Invalid Format, need a parameter\r\n");
                }
                else
                {
                    fieldNum = (int) strtol(CmdPtr, &EndPtr, 10);
                    if (IsValidDebugLevel(fieldNum))
                    {
                        gst_debug_set_threshold_for_name("amcvideodec", (GstDebugLevel)fieldNum);
                        amcviddec_debug_level = fieldNum;
                        CSIO_LOG(eLogLevel_debug, "set amcvideodec debug level to: %d\r\n",fieldNum);
                    }
                    else
                    {
                        CSIO_LOG(eLogLevel_info, "Invalid gst_debug_level:%d\r\n",fieldNum);
                    }
                }
            }
            else if (!strcmp(CmdPtr, "SET_OPENSLESSINK_DEBUG_LEVEL"))
            {
                CmdPtr = strtok(NULL, ", ");
                if (CmdPtr == NULL)
                {
                    CSIO_LOG(eLogLevel_info, "Invalid Format, need a parameter\r\n");
                }
                else
                {
                    fieldNum = (int) strtol(CmdPtr, &EndPtr, 10);
                    if (IsValidDebugLevel(fieldNum))
                    {
                        gst_debug_set_threshold_for_name("openslessink", (GstDebugLevel)fieldNum);
                        amcviddec_debug_level = fieldNum;
                        CSIO_LOG(eLogLevel_debug, "set openslessink debug level to: %d\r\n",fieldNum);
                    }
                    else
                    {
                        CSIO_LOG(eLogLevel_info, "Invalid gst_debug_level:%d\r\n",fieldNum);
                    }
                }
            }
            else if (!strcmp(CmdPtr, "SET_AUDIOSINK_BUFFER_TIME"))
            {
                CmdPtr = strtok(NULL, ", ");
                guint64  tmp = 0;
                if (CmdPtr == NULL)
                {
                    if(data->audio_sink)
                    {
                        g_object_get(G_OBJECT(data->audio_sink), "buffer-time", &tmp, NULL);
                        CSIO_LOG(eLogLevel_debug, "FieldDebugInfo",\
                                 "Size of audio buffer in microseconds, buffer-time is set to %lld microseconds", tmp);
                    }
                    else
                    {
                        CSIO_LOG(eLogLevel_debug, "FieldDebugInfo",\
                                 "no audio sink found.", tmp);
                    }
                }
                else
                {
                    fieldNum = (int) strtol(CmdPtr, &EndPtr, 10);
                    tmp = (guint64)fieldNum;
                    if(data->audio_sink)
                    {
                        g_object_set(G_OBJECT(data->audio_sink), "buffer-time", tmp, NULL);

                        CSIO_LOG(eLogLevel_debug, "FieldDebugInfo",\
                              "Set buffer-time to %lld microseconds", tmp);
                    }
                    else
                    {
                        CSIO_LOG(eLogLevel_debug, "FieldDebugInfo",\
                                 "no audio sink found.", tmp);
                    }
                }
            }
            else if (!strcmp(CmdPtr, "SET_AMCVIDDEC_TS_OFFSET"))
            {
                CmdPtr = strtok(NULL, ", ");
                if (CmdPtr == NULL)
                {
                    CSIO_LOG(eLogLevel_info, "Invalid Format, need a parameter\r\n");
                }
                else
                {

                    fieldNum = (int) strtol(CmdPtr, &EndPtr, 10);
                    if ( fieldNum < 5000 && fieldNum >= 0)
                    {
                        data->amcviddec_ts_offset = fieldNum;
                        for(i=0; i<MAX_ELEMENTS; i++)
                        {
                            if(data->element_v[i])
                            {
                                gchar * n = gst_element_get_name(data->element_v[i]);
                                CSIO_LOG(eLogLevel_debug, "[%d]element name[%s]",i,n);
                                if(strstr(n,"amcvideodec"))
                                {
                                    g_object_set(G_OBJECT(data->element_v[i]), "ts-offset", data->amcviddec_ts_offset, NULL);

                                    data->amcviddec_ts_offset -= CSIOCnsIntf->getStreamRx_BUFFER(sessionId);
                                    CSIO_LOG(eLogLevel_debug, "[%d]set amcviddec_ts_offset:%d",i,data->amcviddec_ts_offset);
                                    break;
                                }
                            }
                            else
                            {
                                CSIO_LOG(eLogLevel_debug, "[%d]break",i);
                                break;
                            }
                        }
                    }
                    else
                    {
                        CSIO_LOG(eLogLevel_info, "Invalid gst_debug_level:%d\r\n",fieldNum);
                    }
                }
            }
            else if (!strcmp(CmdPtr, "SET_AUDIOSINK_TS_OFFSET"))
            {
                CmdPtr = strtok(NULL, ", ");
                if (CmdPtr == NULL)
                {
                    CSIO_LOG(eLogLevel_info, "Invalid Format, need a parameter\r\n");
                }
                else
                {
                    gint64 tmp = 0;
                    fieldNum = (int) strtol(CmdPtr, &EndPtr, 10);

                    data->audiosink_ts_offset = fieldNum ;

                    if(data->audio_sink)
                    {
                        gchar * n = gst_element_get_name(data->audio_sink);
                        CSIO_LOG(eLogLevel_debug, "element name[%s]",n);
                        tmp = data->audiosink_ts_offset * 1000000;
                        g_object_set(G_OBJECT(data->audio_sink), "ts-offset",
                                     tmp, NULL);
                        CSIO_LOG(eLogLevel_debug, "set audiosink_ts_offset:%lldns",tmp);
                    }
                }
            }
            else if (!strcmp(CmdPtr, "PRINT_ELEMENT_PROPERTY"))
            {
                CmdPtr = strtok(NULL, ", ");
                if (CmdPtr == NULL)
                {
                    CSIO_LOG(eLogLevel_info, "Invalid Format, need element's name\r\n");
                }
                else
                {
                    if(data->pipeline)
                    {
                        if (!strcmp(CmdPtr, "allvideo"))
                        {
                            if(data->element_zero)
                                gst_element_print_properties(data->element_zero);
                            for(i=0; i<MAX_ELEMENTS; i++)
                            {
                                if(data->element_v[i])
                                {
                                    gst_element_print_properties(data->element_v[i]);
                                }
                                else
                                {
                                    CSIO_LOG(eLogLevel_debug, "[%d]break",i);
                                    break;
                                }
                            }
                        }
                        else if (!strcmp(CmdPtr, "allaudio"))
                        {
                            if(data->element_zero)
                                gst_element_print_properties(data->element_zero);

                            if(data->audio_sink)
                                gst_element_print_properties(data->audio_sink);

                            for(i=0; i<MAX_ELEMENTS; i++)
                            {
                                if(data->element_a[i])
                                {
                                    gst_element_print_properties(data->element_a[i]);
                                }
                                else
                                {
                                    CSIO_LOG(eLogLevel_debug, "[%d]break",i);
                                    break;
                                }
                            }
                        }
                        else if (!strcmp(CmdPtr, "allav"))
						{
							if(data->element_zero)
								gst_element_print_properties(data->element_zero);
							for(i=0; i<MAX_ELEMENTS; i++)
							{
								if(data->element_av[i])
								{
									gst_element_print_properties(data->element_av[i]);
								}
								else
								{
									CSIO_LOG(eLogLevel_debug, "[%d]break",i);
									break;
								}
							}
						}
                        else
                        {
                            GstElement *ele = gst_bin_get_by_name(GST_BIN(data->pipeline), CmdPtr);
                            if(ele)
                                gst_element_print_properties(ele);
                        }
                    }
                }
            }
            else if (!strcmp(CmdPtr, "PRINT_AUDIOSINK_PROPERTIES"))
            {
                if(data->audio_sink)
                {
                    gchar * name = gst_element_get_name(data->audio_sink);
                    CSIO_LOG(eLogLevel_debug, "FieldDebugInfo: "\
                                        "element name[%s]",name);

                    gboolean audioSync = 0;
                    guint64  tmp = 0;

                    g_object_get(G_OBJECT(data->audio_sink), "sync", &audioSync, NULL);
                    CSIO_LOG(eLogLevel_debug, "FieldDebugInfo: "\
                            "audioSync is set to %d", audioSync);

                    g_object_get(G_OBJECT(data->audio_sink), "max-lateness", &tmp, NULL);
                    CSIO_LOG(eLogLevel_debug, "FieldDebugInfo: "\
                            "Maximum number of nanoseconds that a buffer can be late before it is dropped (-1 unlimited).max-lateness is set to %lld", tmp);

                    g_object_get(G_OBJECT(data->audio_sink), "ts-offset", &tmp, NULL);
                    CSIO_LOG(eLogLevel_debug, "FieldDebugInfo: "\
                            "ts-offset is set to %lld", tmp);

                    g_object_get(G_OBJECT(data->audio_sink), "render-delay", &tmp, NULL);
                    CSIO_LOG(eLogLevel_debug, "FieldDebugInfo: "\
                            "The additional delay between synchronisation and actual rendering of the media. \nThis property will add additional latency to the device in order to make other sinks compensate for the delay.\nrender-delay is set to %lld", tmp);

                    g_object_get(G_OBJECT(data->audio_sink), "buffer-time", &tmp, NULL);
                    CSIO_LOG(eLogLevel_debug, "FieldDebugInfo: "\
                            "Size of audio buffer in microseconds, this is the minimum latency that the sink reports.buffer-time is set to %lld microseconds", tmp);

                    g_object_get(G_OBJECT(data->audio_sink), "latency-time", &tmp, NULL);
                    CSIO_LOG(eLogLevel_debug, "FieldDebugInfo: "\
                            "The minimum amount of data to write in each iteration: latency-time is set to %lld microseconds", tmp);

                    g_object_get(G_OBJECT(data->audio_sink), "provide-clock", &audioSync, NULL);
                    CSIO_LOG(eLogLevel_debug, "FieldDebugInfo: "\
                            "provide-clock is set to %d", audioSync);

                    g_object_get(G_OBJECT(data->audio_sink), "alignment-threshold", &tmp, NULL);
                    CSIO_LOG(eLogLevel_debug, "FieldDebugInfo: "\
                            "Timestamp alignment threshold: alignment-threshold is set to %lld nanoseconds", tmp);

                    g_object_get(G_OBJECT(data->audio_sink), "drift-tolerance", &tmp, NULL);
                    CSIO_LOG(eLogLevel_debug, "FieldDebugInfo: "\
                            "drift-tolerance is set to %lld microseconds ", tmp);

                    g_object_get(G_OBJECT(data->audio_sink), "discont-wait", &tmp, NULL);
                    CSIO_LOG(eLogLevel_debug, "FieldDebugInfo: "\
                            "discont-wait is set to %lld nanoseconds ", tmp);

                }
                else
                {
                    CSIO_LOG(eLogLevel_info, "No audio sink found\r\n");
                }

            }
            else if (!strcmp(CmdPtr, "SET_ELEMENT_PROPERTY_INT"))
			{
				CmdPtr = strtok(NULL, ", ");
				if (CmdPtr == NULL)
				{
					CSIO_LOG(eLogLevel_info, "Invalid Format, need element's name\r\n");
				}
				else
				{
					if(data->pipeline)
					{

						GstElement *ele = gst_bin_get_by_name(GST_BIN(data->pipeline), CmdPtr);
						if(ele)
						{
							CSIO_LOG(eLogLevel_info, "print properties before setting.\r\n");
							gst_element_print_properties(ele);

							CmdPtr = strtok(NULL, ", ");
							if (CmdPtr == NULL)
							{
								CSIO_LOG(eLogLevel_info, "Invalid Format, need property name\r\n");
							}
							else
							{
								char* proName = CmdPtr;

								CmdPtr = strtok(NULL, ", ");
								if (CmdPtr != NULL)
								{
									int tmp = (int) strtol(CmdPtr, &EndPtr, 10);

									g_object_set(G_OBJECT(ele), proName, tmp, NULL);

									CSIO_LOG(eLogLevel_info, "setting properties[%s] setting[%d].\r\n",proName,tmp);

									gst_element_print_properties(ele);
								}
							}


						}

					}
				}
			}
            else if (!strcmp(CmdPtr, "LAUNCH_START"))
            {
            	char * launchStr = strstr(namestring, " ");
            	launchStr++;	// Remove preceding space
            	int strLen = strlen(launchStr);

            	// Stop previous pipeline if running
            	if (data->debug_launch.pipeline)
            	{
            		CSIO_LOG(eLogLevel_debug, "Stopping previous run session, pipeline: %p", data->debug_launch.pipeline);
            		gst_element_set_state (data->debug_launch.pipeline, GST_STATE_NULL);

            		if (data->debug_launch.loop)
            			g_main_loop_quit(data->debug_launch.loop);
            		if (!data->debug_launch.threadID)
            			pthread_join(data->debug_launch.threadID, NULL);
            	}

            	// Make sure variables are nulled out
            	if (data->debug_launch.pipelineString)
            	{
            		data->debug_launch.pipelineString = NULL;
            		free(data->debug_launch.pipelineString);
            	}
            	data->debug_launch.threadID = NULL;
            	data->debug_launch.pipeline = NULL;
            	data->debug_launch.loop = NULL;
            	data->debug_launch.bus = NULL;


            	CSIO_LOG(eLogLevel_debug, "Launching debug pipeline: %s", namestring, CmdPtr, data->debug_launch.pipelineString);

            	data->debug_launch.pipelineString = (char *)malloc(sizeof(char) * strLen + 1);	// will be freed by thread
            	strcpy(data->debug_launch.pipelineString, launchStr);

            	//Kick off the new thread
            	if (pthread_create(&(data->debug_launch.threadID), NULL, debug_launch_pipeline, (void *)data) != 0 )
            	{
            	    CSIO_LOG(eLogLevel_error, "Failed to create new thread for debug pipeline.");
            	}
            }
            else if (!strcmp(CmdPtr, "LAUNCH_STOP"))
            {
				CSIO_LOG(eLogLevel_debug, "Stopping debug pipeline");
            	if (data->debug_launch.pipeline)
            		gst_element_set_state (data->debug_launch.pipeline, GST_STATE_NULL);

            	if (data->debug_launch.loop)
            		g_main_loop_quit(data->debug_launch.loop);

            	if (data->debug_launch.threadID)
            	{
            		pthread_join(data->debug_launch.threadID, NULL);
            		data->debug_launch.threadID = NULL;
            	}
            }
            else if (!strcmp(CmdPtr, "WFD_FDEBUG"))
            {
                CSIO_LOG(eLogLevel_info, "command namestring[%s]\r\n",namestring);

                WfdSinkProj_fdebug(namestring);
            }
            else if (!strcmp(CmdPtr, "MSMICE_FDEBUG"))
            {
                CSIO_LOG(eLogLevel_info, "command namestring[%s]\r\n",namestring);

                msMiceSinkProj_fdebug(namestring);
            }
            else if (!strcmp(CmdPtr, "GRAPH"))
			{
			    bool verbose = false;
				char * fileName = strstr(namestring, " ");

				if (fileName && data->pipeline)
                {
                    FILE * file;
                    char filePath [1024];
                    GstDebugGraphDetails graph_option = GST_DEBUG_GRAPH_SHOW_ALL;

				fileName++;	// Remove preceding space

				if (!strncmp(fileName, "-v", 2))
				{
				    verbose = true;
				    fileName = strstr(fileName, " ");
				    fileName++;	// Remove preceding space
				}

				CSIO_LOG(eLogLevel_info, "command graph[%s] verbose[%d]", fileName, verbose);

                    snprintf(filePath, 1024, "/dev/shm/crestron/CresStreamSvc/%s.dot", fileName);

                    if (verbose)
                        graph_option = GST_DEBUG_GRAPH_SHOW_VERBOSE;

					gchar * dotData = gst_debug_bin_to_dot_data(GST_BIN(data->pipeline), graph_option);

                    file = fopen(filePath, "w");
                    if (file != NULL)
                    {
                        fprintf(file, "%s", dotData);
                        fclose(file);
                    }
                    else
                    {
                        CSIO_LOG(eLogLevel_info, "Error writing graph file - %s: %s", filePath, strerror(errno));
                    }
				}
				else
				    CSIO_LOG(eLogLevel_warning, "Error: fileName %p, pipeline %p", fileName, data->pipeline);
			}
            else if (!strcmp(CmdPtr, "JITTER_LATENCY"))
            {
                CmdPtr = strtok(NULL, ", ");
                if (CmdPtr == NULL)
                {
                    CSIO_LOG(eLogLevel_info, "current data->wfd_jitterbuffer_latency: %d\r\n",data->wfd_jitterbuffer_latency);
                }
                else
                {
                    data->wfd_jitterbuffer_latency = (int) strtol(CmdPtr, &EndPtr, 10);

                    CSIO_LOG(eLogLevel_debug, "set jitterbuffer_latency: %d",data->wfd_jitterbuffer_latency);
                }
            }
            else
            {
                CSIO_LOG(eLogLevel_info, "Invalid command:%s\r\n",CmdPtr);
            }
        }
    }

    env->ReleaseStringUTFChars(cmd_jstring, cmd_cstring);
}

static GstElement * find_android_decoder(CREGSTREAM * data)
{
	GstIterator *it = gst_bin_iterate_elements(GST_BIN(data->debug_launch.pipeline));
	GValue item = G_VALUE_INIT;
	GstElement *decoder = NULL;
	while (gst_iterator_next(it, &item) == GST_ITERATOR_OK)
	{
		GstElement *elem = (GstElement *)g_value_get_object(&item);
		gchar *elemName = gst_element_get_name(elem);
		CSIO_LOG(eLogLevel_extraVerbose, "Found element %s", elemName);

		if (strstr(elemName, "amcvideodec") != 0)
		{
			// found decoder, now break, currently only handle one android decoder per pipeline
			decoder = elem;
			g_free(elemName);
			break;
		}
		g_free(elemName);
		g_value_reset(&item);
	}
	g_value_unset(&item);
	gst_iterator_free(it);

	return decoder;
}

static gboolean debug_launch_bus_message (GstBus * bus, GstMessage * message, CREGSTREAM * data)
{
	CSIO_LOG(eLogLevel_debug,"got message %s", gst_message_type_get_name (GST_MESSAGE_TYPE (message)));

	switch (GST_MESSAGE_TYPE (message)) {
	case GST_MESSAGE_ERROR:
	{
		GError *err = NULL;
		gchar *debug = NULL;

		gst_message_parse_error (message, &err, &debug);

		CSIO_LOG(eLogLevel_error, "Error from element %s: %s, quitting loop", GST_OBJECT_NAME (message->src), err->message);
		CSIO_LOG(eLogLevel_debug, "Debugging info: %s", (debug) ? debug : "none");

		g_error_free(err);
		g_free(debug);
		if (data->debug_launch.loop)
			g_main_loop_quit(data->debug_launch.loop);
		break;
	}
	case GST_MESSAGE_EOS:
		CSIO_LOG(eLogLevel_debug, "Received EOS from source, quitting loop");
		if (data->debug_launch.loop)
			g_main_loop_quit(data->debug_launch.loop);
		break;
	case GST_MESSAGE_WARNING:
	{
		GError *err = NULL;
		gchar *debug = NULL;

		gst_message_parse_warning (message, &err , &debug);

		if(message->src && err && err->message)
		{
			CSIO_LOG(eLogLevel_warning, "Warning from element %s: %s", GST_OBJECT_NAME (message->src), err->message);
		}

		CSIO_LOG(eLogLevel_debug, "Debugging info: %s", debug? debug : "none");

		g_error_free(err);
		g_free(debug);
		break;
	}
	default:
		break;
	}
	return TRUE;
}

static void * debug_launch_pipeline(void *vData)
{
	GError *error = NULL;

	CREGSTREAM * data = (CREGSTREAM *)vData;

	if (!data)
	{
		CSIO_LOG(eLogLevel_error, "Failed to launch debug pipeline, data is NULL");
		return NULL;
	}
	else if (!data->debug_launch.pipelineString)
	{
		CSIO_LOG(eLogLevel_error, "Failed to launch debug pipeline, pipelineString is NULL");
		return NULL;
	}

	data->debug_launch.pipeline = gst_parse_launch ((const gchar *)data->debug_launch.pipelineString, &error);
	if (!data->debug_launch.pipeline)
	{
		CSIO_LOG(eLogLevel_error, "Failed to launch debug pipeline, error: %s", error->message);
	}
	else
	{

		// Set loop
		data->debug_launch.loop = g_main_loop_new( data->context, FALSE );

		// Setup message handler
		data->debug_launch.bus = gst_pipeline_get_bus (GST_PIPELINE (data->debug_launch.pipeline));
		if (data->debug_launch.bus)
			gst_bus_add_watch (data->debug_launch.bus, (GstBusFunc) debug_launch_bus_message, data);

		// Find decoder element and set the video window to it
		GstElement *decoder = find_android_decoder(data);

		// If AMC video decoder is found, we need to set surface-window on it to render properly
		if (decoder)
		{
			CSIO_LOG(eLogLevel_debug, "Found AMC decoder, setting surface window %p", data->surface);
			g_object_set(G_OBJECT(decoder), "surface-window", data->surface, NULL);
		}

		// Go to playing state
		gst_element_set_state (data->debug_launch.pipeline, GST_STATE_PLAYING);

		// Set loop to run
		g_main_loop_run (data->debug_launch.loop);

		CSIO_LOG(eLogLevel_debug, "Exited debug main loop");

		if (data->debug_launch.bus)
		{
			gst_object_unref (data->debug_launch.bus);
			data->debug_launch.bus = NULL;
		}
		if (data->debug_launch.loop)
		{
			g_main_loop_unref (data->debug_launch.loop);
			data->debug_launch.loop = NULL;
		}
		if (data->debug_launch.pipeline)
		{
			gst_object_unref (data->debug_launch.pipeline);
			data->debug_launch.pipeline = NULL;
		}
	}

	if (data->debug_launch.pipelineString)
	{
		free(data->debug_launch.pipelineString);
		data->debug_launch.pipelineString = NULL;
	}
	CSIO_LOG(eLogLevel_debug, "Exiting debug pipeline thread");
	return NULL;
}

eStreamState nativeGetCurrentStreamState(jint sessionId)
{
	eStreamState currentStreamState;
	JNIEnv *env = get_jni_env ();

	jmethodID getCurrentStreamState = env->GetMethodID((jclass)gStreamIn_javaClass_id, "getCurrentStreamState", "(I)I");

	currentStreamState = (eStreamState)env->CallIntMethod(CresDataDB->app, getCurrentStreamState, sessionId);

	if (env->ExceptionCheck ()) {
		CSIO_LOG(eLogLevel_error, "Failed to call Java method 'getCurrentStreamState'");
		env->ExceptionClear ();
	}

	CSIO_LOG(eLogLevel_debug, "currentStreamState = %d", (jint)currentStreamState);

	return currentStreamState;
}

/* List of implemented native methods */
static JNINativeMethod native_methods[] = 
{
	{ "nativeInit", "()V", (void *) gst_native_init},
	{ "nativeFinalize", "()V", (void *) gst_native_finalize},
	{ "nativePlay", "(I)V", (void *) gst_native_play},
	{ "nativePause", "(I)V", (void *) gst_native_pause},
	{ "nativeStop", "(I)V", (void *) gst_native_stop},
	{ "nativeSurfaceInit", "(Ljava/lang/Object;I)V", (void *) gst_native_surface_init},
	{ "nativeSurfaceFinalize", "(I)V", (void *) gst_native_surface_finalize},
	{ "nativeClassInit", "()Z", (void *) gst_native_class_init}
};

/* Library initializer */
jint JNI_OnLoad(JavaVM *vm, void *reserved) 
{
	JNIEnv *env = NULL;
//TODO: investigate why there are 2 instances of currentSettingsDB
	if (currentSettingsDB == NULL)
		currentSettingsDB = (CSIOSettings*)malloc(sizeof(CSIOSettings));
	java_vm = vm;

	currentSettingsDB->csioLogLevel = CSIO_DEFAULT_LOG_LEVEL;

	CSIO_LOG(eLogLevel_debug, "JNI_OnLoad ");

	set_gst_debug_level();
	
	if (vm->GetEnv((void**) &env, JNI_VERSION_1_4) != JNI_OK)
	{
		CSIO_LOG(eLogLevel_error, "gstreamer_jni", "Could not retrieve JNIEnv");
		return 0;
	}
	//jclass klass = env->FindClass ("com/gst_sdk_tutorials/tutorial_3/Tutorial3");
	jclass klass = env->FindClass ("com/crestron/txrxservice/GstreamIn");
	/* Create a global reference to GstreamIn class Id, because it gets lost when pthread is created */
	gStreamIn_javaClass_id = (jclass*)env->NewGlobalRef(klass);
	env->DeleteLocalRef(klass);
	if (gStreamIn_javaClass_id == NULL) {
		CSIO_LOG(eLogLevel_error, "gstreamer_jni", "gStreamIn_javaClass_id is still null when it is suppose to be global");
	     return 0; /* out of memory exception thrown */
	 }

	env->RegisterNatives ((jclass)gStreamIn_javaClass_id, native_methods, G_N_ELEMENTS(native_methods));

	// Crestron - PEM - register jni for GstreamOut
	CSIO_LOG(eLogLevel_error, "gstreamer_jni : Registering natives for GstreamOut");
	jclass klass2 = env->FindClass ("com/crestron/txrxservice/GstreamOut");
	gStreamOut_javaClass_id = (jclass*)env->NewGlobalRef(klass2);
	env->DeleteLocalRef(klass2);
	if (gStreamOut_javaClass_id == NULL) {
		CSIO_LOG(eLogLevel_error, "gstreamer_jni", "gStreamOut_javaClass_id is still null when it is suppose to be global");
	     return 0; /* out of memory exception thrown */
	}
	env->RegisterNatives ((jclass)gStreamOut_javaClass_id, native_methods_rtsp_server, G_N_ELEMENTS(native_methods_rtsp_server));
	
	// Crestron - RH - setup wbsStreamIn_javaClass_id for WbsStreamIn for C++ to be able to call JAVA class WbsStreamIn functions
	CSIO_LOG(eLogLevel_error, "wbstream_jni : Registering natives for WbsStreamIn");
	jclass klass3 = env->FindClass ("com/crestron/txrxservice/WbsStreamIn");
	wbsStreamIn_javaClass_id = (jclass*)env->NewGlobalRef(klass3);
	env->DeleteLocalRef(klass3);
	if (wbsStreamIn_javaClass_id == NULL) {
		CSIO_LOG(eLogLevel_error, "wbstream_jni", "wbsStreamIn_javaClass_id is still null when it is suppose to be global");
	     return 0; /* out of memory exception thrown */
	}

	pthread_key_create (&current_jni_env, detach_current_thread);
	return JNI_VERSION_1_4;
}

void JNI_OnUnload(JavaVM *vm, void *reserved)
{
    free(currentSettingsDB);
}

int csio_IpLinkClientConnected()
{
	return 1;
}
void csio_send_zerostats()
{	
	csio_send_stats(0, 0, 0, 0, 0);
}
int csio_ClearOverlay()
{
	return 0;
}

void    *csio_SendMulticastAddressFb(void * arg)
{
	jstring multicastAddress_jstr;
	JNIEnv *env = get_jni_env ();
	jint streamId = csio_GetStreamId(arg);
	char *multicastAddress_cstr = csio_GetMulticastAddress(streamId);	

	multicastAddress_jstr = env->NewStringUTF(multicastAddress_cstr);

	jmethodID sendMulticastAddress = env->GetMethodID((jclass)gStreamIn_javaClass_id, "sendMulticastAddress", "(Ljava/lang/String;I)V");
	if (sendMulticastAddress == NULL) return NULL;

	env->CallVoidMethod(CresDataDB->app, sendMulticastAddress, multicastAddress_jstr, streamId);
	if (env->ExceptionCheck ()) {
		CSIO_LOG(eLogLevel_error, "Failed to call Java method 'sendMulticastAddress'");
		env->ExceptionClear ();
	}
	env->DeleteLocalRef (multicastAddress_jstr);

	return(NULL);
}
void csio_send_stats (uint64_t video_packets_received, int video_packets_lost, uint64_t audio_packets_received, int audio_packets_lost, uint16_t bitrate)
{
	JNIEnv *env = get_jni_env ();

	jmethodID sendStatistics = env->GetMethodID((jclass)gStreamIn_javaClass_id, "sendStatistics", "(JIJII)V");
	if (sendStatistics == NULL) return;

	env->CallVoidMethod(CresDataDB->app, sendStatistics, (jlong)video_packets_received, (jint)video_packets_lost, (jlong)audio_packets_received, (jint)audio_packets_lost, (jint)bitrate);
	if (env->ExceptionCheck ()) {
		CSIO_LOG(eLogLevel_error, "Failed to call Java method 'sendStatistics'");
		env->ExceptionClear ();
	}
}

void csio_send_stats_no_bitrate (uint64_t video_packets_received, int video_packets_lost, uint64_t audio_packets_received, int audio_packets_lost)
{
	int bitrate = -1; //bitrate was omitted so send -1 which will flag txrxservice to ignore
	JNIEnv *env = get_jni_env ();

	jmethodID sendStatistics = env->GetMethodID((jclass)gStreamIn_javaClass_id, "sendStatistics", "(JIJII)V");
	if (sendStatistics == NULL) return;

	env->CallVoidMethod(CresDataDB->app, sendStatistics, (jlong)video_packets_received, (jint)video_packets_lost, (jlong)audio_packets_received, (jint)audio_packets_lost, (jint)bitrate);
	if (env->ExceptionCheck ()) {
		CSIO_LOG(eLogLevel_error, "Failed to call Java method 'sendStatistics'");
		env->ExceptionClear ();
	}
}

void csio_get_width_and_height_from_mode (uint32_t * width, uint32_t * height)
{
	//TODO: this method needs to be updated with source as a input parameter currently defaulting to 0
	unsigned int source = 0;
	JNIEnv *env = get_jni_env ();

	jmethodID getCurrentWidthHeight = env->GetMethodID((jclass)gStreamIn_javaClass_id, "getCurrentWidthHeight", "(I)[I");
	if (getCurrentWidthHeight == NULL) return;

	jintArray retval = (jintArray) env->CallObjectMethod(CresDataDB->app, getCurrentWidthHeight, (jint)source);
	if (env->ExceptionCheck ()) {
		CSIO_LOG(eLogLevel_error, "Failed to call Java method 'getCurrentWidthHeight'");
		env->ExceptionClear ();
	}
	jint *widthHeight = env->GetIntArrayElements(retval, NULL);

	(*width) = widthHeight[0];
	(*height) = widthHeight[1];
	env->ReleaseIntArrayElements(retval, widthHeight, 0);
	return;
}

int csio_SendVideoPlayingStatusMessage(unsigned int streamId, eStreamState state)
{
	JNIEnv *env = get_jni_env ();

	jmethodID updateStreamStatus = env->GetMethodID((jclass)gStreamIn_javaClass_id, "updateStreamStatus", "(II)V");
	if (updateStreamStatus == NULL) return -1; // TODO: what is error code here

	env->CallVoidMethod(CresDataDB->app, updateStreamStatus, (jint)state, (jint)streamId);
	if (env->ExceptionCheck ()) {
		CSIO_LOG(eLogLevel_error, "Failed to call Java method 'updateStreamStatus'");
		env->ExceptionClear ();
	}

	// Reset source parameters when stopped - Bug 88712
	if (state == STREAMSTATE_STOPPED)
	{
		csio_SendVideoSourceParams(streamId,0,0,0,0);
	}
	
	return 0;
}
void csio_SendVideoSourceParams(unsigned int source, unsigned int width, unsigned int height, unsigned int framerate, unsigned int profile)
{
	JNIEnv *env = get_jni_env ();	

	jmethodID sendVideoSourceParams = env->GetMethodID((jclass)gStreamIn_javaClass_id, "sendVideoSourceParams", "(IIIII)V");
	if (sendVideoSourceParams == NULL) return;

	env->CallVoidMethod(CresDataDB->app, sendVideoSourceParams, (jint)source, (jint)width, (jint)height, (jint)framerate, (jint)profile);
	if (env->ExceptionCheck ()) {
		CSIO_LOG(eLogLevel_error, "Failed to call Java method 'sendVideoSourceParams'");
		env->ExceptionClear ();
	}
}

void csio_SendDSVideoReady()
{
	JNIEnv *env = get_jni_env ();

	jmethodID sendDSVideoReady = env->GetMethodID((jclass)gStreamIn_javaClass_id, "sendDSVideoReady", "()V");
	if (sendDSVideoReady == NULL) return;

	env->CallVoidMethod(CresDataDB->app, sendDSVideoReady);
	if (env->ExceptionCheck ()) {
		CSIO_LOG(eLogLevel_error, "Failed to call Java method 'sendDSVideoReady'");
		env->ExceptionClear ();
	}
}

void csio_signal_that_stream_has_stopped(int iStreamId)
{
	//Intentionally left blank
}

void csio_start_mode_change_detection ()
{
	return;
}

GMainLoop * csio_jni_CreateMainLoop(int iStreamId)
{
	CREGSTREAM * data = GetStreamFromCustomData(CresDataDB, iStreamId);
	
	if(!data)
	{
		CSIO_LOG(eLogLevel_error, "Could not obtain stream pointer for stream %d", iStreamId);
		return NULL;
	}	
	
	return g_main_loop_new( data->context, FALSE );
}

void csio_jni_CreateMainContext(int iStreamId)
{
	CREGSTREAM * data = GetStreamFromCustomData(CresDataDB, iStreamId);
	
	if(!data)
	{
		CSIO_LOG(eLogLevel_error, "Could not obtain stream pointer for stream %d", iStreamId);
		return;
	}	
	
	data->context = g_main_context_new ();
	g_main_context_push_thread_default(data->context);
}

void csio_jni_FreeMainContext(int iStreamId)
{
	CREGSTREAM * data = GetStreamFromCustomData(CresDataDB, iStreamId);
	
	if(!data)
	{
		CSIO_LOG(eLogLevel_error, "Could not obtain stream pointer for stream %d", iStreamId);
		return;
	}	

    if(data->context)
	{
        g_main_context_pop_thread_default(data->context);
        g_main_context_unref (data->context);
        data->context = NULL;
	}

        clearMetadataConnections();
}

void csio_jni_CheckInitializationComplete(int iStreamId)
{
	check_initialization_complete(CresDataDB, iStreamId);
}

void csio_jni_SetOverlayWindow(int iStreamId)
{
	CREGSTREAM * data = GetStreamFromCustomData(CresDataDB, iStreamId);
	
	if(!data)
	{
		CSIO_LOG(eLogLevel_error, "Could not obtain stream pointer for stream %d", iStreamId);
		return;
	}	
	
	/* The main loop is running and we received a native window, inform the sink about it */
	if(data->video_sink)
	{
		gst_video_overlay_set_window_handle (GST_VIDEO_OVERLAY (data->video_sink), (guintptr)data->native_window);
	}

	//send event to wifi display project that gstreamer is ready.
	WfdSinkProjSendGstReady(iStreamId);
}

//================================================================================
// a unified function to create all HTTP related pipelines, including mjpeg, HLS, mp4, DASH, etc.
//================================================================================
int csio_jni_CreateHttpPipeline(void *obj, GstElement **pipeline, GstElement **source, eProtocolId protoId, int iStreamId, eHttpMode httpMode, bool useSWdecoder)
{
    int iStatus = CSIO_SUCCESS;
    CSIO_LOG(eLogLevel_debug, "%s() enter ...", __FUNCTION__);

    CREGSTREAM * data = GetStreamFromCustomData(CresDataDB, iStreamId);
    if(!data)
    {
        CSIO_LOG(eLogLevel_error, "Could not obtain stream pointer for stream %d", iStreamId);
        return CSIO_FAILURE;
    }

    csio_jni_CreateMainContext(iStreamId);

    data->pipeline = gst_pipeline_new( NULL );
    if( !data->pipeline )
    {
        CSIO_LOG( eLogLevel_error, "ERROR: %s() create pipeline failed\n", __FUNCTION__ );
        return CSIO_CANNOT_CREATE_ELEMENTS;
    }
    data->streamProtocolId = protoId;
    data->pStreamer = obj;
    data->httpMode = httpMode;
    data->useSWdecoder = useSWdecoder;

    build_http_pipeline(data, iStreamId);
    *pipeline = data->pipeline;
    *source   = data->element_zero;

    return iStatus;
}




// ***

#define DTLSPACKETMAXSIZE           4096           // more than twice the MTU
#define LOOPERRORTHRESHOLD          100

char * lookupCipherName(int index)
{
   if(index == 1)
         return("aes-128-icm");
   else  return(NULL);
}

char * lookupAuthName(int index)
{
   switch(index)
      {
      case 1:
         return("hmac-sha1-32");
      case 2:
         return("hmac-sha1-80");
      }
   return(NULL);
}

typedef struct {
   int sockFD;
   int streamID;
   GstElement * appSource;
   pthread_t mediaRTPThread;
   pthread_cond_t initComplCondVar;
	pthread_mutex_t initComplMutex;
} MEDIARTPTHREADCONTEXT;


int csio_jni_StartRTPMediaStreamThread(int iStreamId, GstElement * appSource, unsigned int udpPort)
{
	int retv;
   struct sockaddr_in servaddr;
   MEDIARTPTHREADCONTEXT rtpMedStrContext;

   // !!!!!!!
   // Check if it is still valid, correct if needed
   // 
   //    locking arrangement:
   //       - access to sssl context storage API needs a global lock
   //       - since the only entity deleting the SSL context is going to be the RTP thread,
   //         there is no need for individual locks, per ssl context. The context deletion must
   //         follow the sequence
   //             - get the global sssl context storage lock
   //             - find entry's index from the key
   //             - delete SSL context at the given index
   //             - delete sssl context storage at the given index
   //             - release the global lock
   //    
   // !!!!!!!


   CSIO_LOG(eLogLevel_debug,"mira: {%s} - entering with iStreamId = %d",__FUNCTION__,iStreamId);

   memset((void *)&rtpMedStrContext, 0, sizeof(rtpMedStrContext)); 

   void * sssl = sssl_getContextWithStreamID(iStreamId);
   if(sssl == NULL)
   {
      CSIO_LOG(eLogLevel_error, "mira: could not obtain sssl context for iStreamId = %d",iStreamId);
      return(-1);
   }
   rtpMedStrContext.streamID = iStreamId;
   rtpMedStrContext.appSource = appSource;

   rtpMedStrContext.sockFD = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
   if(rtpMedStrContext.sockFD < 0)
   {
      CSIO_LOG(eLogLevel_error, "mira: could not create RTP socket");
      return(-1);
   }

   memset((void *)&servaddr, 0, sizeof(servaddr)); 

   // bind the socket with the server address 
   servaddr.sin_family        = AF_INET;           // IPv4 
   servaddr.sin_addr.s_addr   = INADDR_ANY; 
   servaddr.sin_port          = htons(udpPort);
   if(bind(rtpMedStrContext.sockFD, (const struct sockaddr *)&servaddr, sizeof(servaddr)) < 0) 
   { 
      CSIO_LOG(eLogLevel_error, "mira: could not bind RTP socket");
      return(-1);
   } 

   // may want to set these socket level options:
   //    SO_RCVBUF
   //    SO_DEBUG

   pthread_cond_init(&rtpMedStrContext.initComplCondVar, NULL);
   pthread_mutex_init(&rtpMedStrContext.initComplMutex, NULL);

   sssl_initDTLSAppThCancCondVar(iStreamId);

   pthread_attr_t attr;
   pthread_attr_init(&attr);
   pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_DETACHED);
	retv = pthread_create(&rtpMedStrContext.mediaRTPThread, &attr, rtpMediaStreamThread,
         (void *)&rtpMedStrContext);
   if(retv)
   {
      CSIO_LOG(eLogLevel_error, "mira: failed to create media RTP thread, retv = %d", retv);
      return(-1);
   }

	struct timespec initTimeout;
   clock_gettime(CLOCK_REALTIME, &initTimeout);
   initTimeout.tv_sec += 5;

   // wait for thread to initialize
   pthread_mutex_lock(&rtpMedStrContext.initComplMutex);
   retv = pthread_cond_timedwait(&rtpMedStrContext.initComplCondVar,&rtpMedStrContext.initComplMutex,
      &initTimeout);
   pthread_mutex_unlock(&rtpMedStrContext.initComplMutex);

   if(retv == ETIMEDOUT)
   {
      CSIO_LOG(eLogLevel_warning, "mira: csio_jni_StartRTPMediaStreamThread() - pthread_cond_timedwait() returned with timeout");
   }

   // cleanup pthread objects
   // should not destroy if timeout ???
   pthread_attr_destroy(&attr);
   pthread_cond_destroy(&rtpMedStrContext.initComplCondVar);
   pthread_mutex_destroy(&rtpMedStrContext.initComplMutex);

   CSIO_LOG(eLogLevel_debug,"mira: {%s} - exiting",__FUNCTION__);

   return(0);
}


void updateProbeInfo(int streamID, struct timespec * currentTimePtr, char * srcIPAddress)
{
    CREGSTREAM * data = GetStreamFromCustomData(CresDataDB, streamID);
    if(!data)
    {
        CSIO_LOG(eLogLevel_error, "mira: {%s} - could not obtain stream pointer for stream %d",
            __FUNCTION__,streamID);
        return;
    }

    // if this is the first time
    if( data->udpsrc_prob_timer.tv_sec == 0)
    {
        data->udpsrc_prob_timer.tv_sec = currentTimePtr->tv_sec;
    }
    else
    {
        // if still within the same second
        if(data->udpsrc_prob_timer.tv_sec == currentTimePtr->tv_sec)
            return;
    }

    long tDiff = currentTimePtr->tv_sec - data->udpsrc_prob_timer.tv_sec;

    // only update every 5 seconds
    if(tDiff % 5)
        return;

    data->udpsrc_prob_timer.tv_sec = currentTimePtr->tv_sec;

    strncpy(data->sourceIP_addr,srcIPAddress,sizeof(data->sourceIP_addr)-1);
    data->sourceIP_addr[sizeof(data->sourceIP_addr)-1] = '\0';

    CSIO_LOG(eLogLevel_extraVerbose, "mira: {%s} - updated stream data for streamID %d with timestamp = %u, IPAddress = %s",
        __FUNCTION__,streamID,(unsigned int)currentTimePtr->tv_sec,data->sourceIP_addr);

    return;
}


static bool loopShouldLog(int * errorCountPtr, int * logLevelPtr)
{
   bool doPrint = false;
   
   *logLevelPtr = eLogLevel_extraVerbose;
	if(*errorCountPtr < 3)
   {
      doPrint = true;
      *logLevelPtr = eLogLevel_debug;
   }
	else if(*errorCountPtr > LOOPERRORTHRESHOLD)
   {
      doPrint = true;
	   *errorCountPtr = 0;
      *logLevelPtr = eLogLevel_debug;
   }
	(*errorCountPtr)++;

   return(doPrint);
}


void * rtpMediaStreamThread(void * threadData)
{
	int retv,retv1,nn;
	int timeoutErrorCount,typeErrorCount,decryptErrorCount,llev;
   int rtpDataSize,dtlsDataSize;
   void * sssl;
   struct sockaddr senderAddr;
   int addrLen;
   static struct timespec currentTime;
   GstFlowReturn ret;
   GstBuffer * buffer;
   guchar * rtpData;
   GstMapInfo map;
   GstClockTime now;
   GstClock * theclock;
   MEDIARTPTHREADCONTEXT rtpMedStrContext;
   unsigned char dtlsPacketBuff[DTLSPACKETMAXSIZE];
   unsigned char rtpPacketBuff[DTLSPACKETMAXSIZE];

   CSIO_LOG(eLogLevel_debug,"mira: {%s} - ***** entering *****",__FUNCTION__);

   memcpy((void *)&rtpMedStrContext,(const void *)threadData,sizeof(MEDIARTPTHREADCONTEXT));

   // signal initialization completed
   pthread_cond_broadcast(&((MEDIARTPTHREADCONTEXT *)threadData)->initComplCondVar);

   struct timeval recvTimeout;
   recvTimeout.tv_sec = 0;
   recvTimeout.tv_usec = 250000;          // 250ms timeout
   retv = setsockopt(rtpMedStrContext.sockFD, SOL_SOCKET, SO_RCVTIMEO, &recvTimeout,
      sizeof(recvTimeout));
   if(retv < 0)
   {
      CSIO_LOG(eLogLevel_error, "mira: setsockopt failed, errno = %d", errno);
      goto earlyreturn;
   }

   retv = sssl_setDTLSAppThInitializedWithStreamID(rtpMedStrContext.streamID, 2, &sssl);
   if(retv != 0)
   {
      // If appThInitialized is not 0 at this point, it is inconsistent.
      CSIO_LOG(eLogLevel_error,"mira: rtpMediaStreamThread() - sssl_setDTLSAppThInitializedWithStreamID() returned %d, exiting ...",
         retv);
      goto earlyreturn;
   }

   // get the clock
   theclock = gst_element_get_clock(rtpMedStrContext.appSource);

	nn = -1;
	timeoutErrorCount = typeErrorCount = decryptErrorCount = 0;

   while(1==1)
   {
	   nn++;

      // TCP:
      //    Send: AA BBBB CCC DDDDDD E         Recv: A ABB B BCC CDDD DDDE
      //    All data sent is received in order, but not necessarily in the same chunks.
      // UDP:
      //    Send: AA BBBB CCC DDDDDD E         Recv: CCC AA E
      //    Data is not necessarily in the same order, and not necessarily received at all, but
      //    messages are preserved in their entirety.

      // blocking read with timeout
      memset((void *)&senderAddr,0,sizeof(senderAddr));
      addrLen = sizeof(senderAddr);        // must be initialized
      dtlsDataSize = recvfrom(rtpMedStrContext.sockFD, (char *)dtlsPacketBuff, DTLSPACKETMAXSIZE,
         0, &senderAddr, &addrLen);
      if(dtlsDataSize < 0)
      {
         if(errno == EWOULDBLOCK)
         {
            retv = sssl_getDTLSAppThInitializedWithStreamID(rtpMedStrContext.streamID, NULL);
            if(retv != 2)
            {
               // we are asked to exit
               CSIO_LOG(eLogLevel_debug,"mira: RTP loop: {recv tmo} sssl_getDTLSAppThInitializedWithStreamID() returned %d",retv);
               break;
            }
            if(loopShouldLog(&timeoutErrorCount, &llev))
            {
               CSIO_LOG(llev,"mira: RTP loop: receive timeout, continuing anyway ...");
            }
            continue;
         }
         else
         {
            CSIO_LOG(eLogLevel_error,"mira: RTP loop: receive error, errno = %d",errno);
            break;
         }
      }

      if(nn < 3)
      {
         CSIO_LOG(eLogLevel_debug,"mira: RTP loop: received %d bytes from IP address %s",
            dtlsDataSize,inet_ntoa(((struct sockaddr_in *)&senderAddr)->sin_addr));
      }

      //   TLS Record Type Values     dec      hex
      //   ------------------------------------------
      //      CHANGE_CIPHER_SPEC      20       0x14
      //      ALERT                   21       0x15
      //      HANDSHAKE               22       0x16
      //      APPLICATION_DATA        23       0x17
      // 
      unsigned char tlsType = dtlsPacketBuff[0];
      if(tlsType != 0x17)
      {
         if(loopShouldLog(&typeErrorCount, &llev))
         {
            CSIO_LOG(llev,"mira: RTP loop: received packet of %d bytes with bad TLS type 0x%x, continuing anyway ...",
               dtlsDataSize,(int)tlsType);
         }
         continue;
      }

      retv = sssl_getDTLSAppThInitializedWithStreamID(rtpMedStrContext.streamID, NULL);
      if(retv != 2)
      {
         // we are asked to exit
         CSIO_LOG(eLogLevel_debug,"mira: RTP loop: sssl_getDTLSAppThInitializedWithStreamID() returned %d",retv);
         break;
      }

      rtpDataSize = sssl_decryptDTLSLocal(sssl,(void *)dtlsPacketBuff,dtlsDataSize,(void *)rtpPacketBuff,
            DTLSPACKETMAXSIZE);
      if(rtpDataSize <= 0)
      {
         // something went wrong
         if(loopShouldLog(&decryptErrorCount, &llev))
         {
            CSIO_LOG(llev,"mira: RTP loop: sssl_decryptDTLS() returned %d, continuing anyway ...",rtpDataSize);
         }
         continue;
      }
      if(nn < 3)
      {
         CSIO_LOG(eLogLevel_debug,"mira: RTP loop: received %d bytes after DTLS dcryption",rtpDataSize);
      }

      clock_gettime(CLOCK_REALTIME,&currentTime);
      updateProbeInfo(rtpMedStrContext.streamID,&currentTime,
         inet_ntoa(((struct sockaddr_in *)&senderAddr)->sin_addr));

      // create a new empty buffer
      buffer = gst_buffer_new_and_alloc(rtpDataSize);
      if(buffer == NULL)
      {
         // something went wrong
         CSIO_LOG(eLogLevel_error,"mira: RTP loop: gst_buffer_new_and_alloc() returned NULL");
         break;
      }

      // set its timestamp and duration
      now = gst_clock_get_time(theclock);
      GST_BUFFER_TIMESTAMP(buffer) = now;
      GST_BUFFER_DURATION(buffer) = GST_CLOCK_TIME_NONE;

      gst_buffer_map(buffer, &map, GST_MAP_WRITE);

      // copy RTP data to GStreamer's buffer
      memcpy((void *)map.data,(const void *)rtpPacketBuff,rtpDataSize);

      gst_buffer_unmap(buffer, &map);

      // push the buffer into the appsrc
      // 
      // ..... make sure it is blocking write !
      // 
      g_signal_emit_by_name(rtpMedStrContext.appSource, "push-buffer", buffer, &ret);

      // free the buffer now that we are done with it
      gst_buffer_unref(buffer);

      if(ret != GST_FLOW_OK)
      {
         // we got some error, stop sending data
         CSIO_LOG(eLogLevel_error,"mira: RTP loop: g_signal_emit_by_name() returned error %d",(int)ret);
         break;
      }

      if(nn < 3)
      {
         CSIO_LOG(eLogLevel_debug,"mira: RTP loop: submitted buffer to AppSrc, nn = %d",nn);
      }
   }

threadreturn:
   CSIO_LOG(eLogLevel_debug,"mira: {%s} - commencing normal exit",__FUNCTION__);
   // will destroy sssl context
   retv = sssl_handleDTLSAppThCancelation(rtpMedStrContext.streamID);
   close(rtpMedStrContext.sockFD);
   CSIO_LOG(eLogLevel_debug,"mira: {%s} - ===== exiting =====",__FUNCTION__);
	pthread_exit(NULL);

earlyreturn:
   // no sssl context destruction
   close(rtpMedStrContext.sockFD);
   CSIO_LOG(eLogLevel_debug,"mira: {%s} - ===== exiting (early) =====",__FUNCTION__);
	pthread_exit(NULL);
}


int csio_jni_CreatePipeline(GstElement **pipeline, GstElement **source, eProtocolId protoId, int iStreamId)
{
	int iStatus = CSIO_SUCCESS;
	char *buf = NULL;
	
    CSIO_LOG(eLogLevel_debug, "%s() protoId = %d", __FUNCTION__, protoId);

	CREGSTREAM * data = GetStreamFromCustomData(CresDataDB, iStreamId);
	if(!data)
	{
		CSIO_LOG(eLogLevel_error, "Could not obtain stream pointer for stream %d", iStreamId);
		return CSIO_FAILURE;
	}

	csio_jni_CreateMainContext(iStreamId);      //@todo: duplicated, rewrite this func, and delete above several lines

    data->pipeline = gst_pipeline_new( NULL );
    if( !data->pipeline )
    {
        CSIO_LOG( eLogLevel_error, "ERROR: %s() create pipeline failed\n", __FUNCTION__ );
        return CSIO_CANNOT_CREATE_ELEMENTS;
    }
    data->streamProtocolId = protoId;

	switch( protoId )
	{
		case ePROTOCOL_RTSP_TCP:
		case ePROTOCOL_RTSP_UDP:
	    case ePROTOCOL_DEFAULT_RTSP_TCP:
	    case ePROTOCOL_RTSP_UDP_TS:
	    case ePROTOCOL_RTSP_TS:
	    {
	    	data->element_zero = gst_element_factory_make("rtspsrc", NULL);
			if( !data->element_zero )
			{
				CSIO_LOG(eLogLevel_error, "ERROR: Cannot create rtspsrc element\n");
				return CSIO_CANNOT_CREATE_ELEMENTS;
			}
	    	gst_bin_add_many(GST_BIN(data->pipeline), data->element_zero, NULL);
			*pipeline = data->pipeline;
			*source   = data->element_zero;
			break;
	    }
	    case ePROTOCOL_UDP_TS:
	    {
		    if(CSIOCnsIntf->getStreamTxRx_TRANSPORTMODE(iStreamId)==STREAM_TRANSPORT_MPEG2TS_RTP)
		    {
		        data->element_zero = gst_element_factory_make("rtpbin", NULL);
			    gst_bin_add(GST_BIN(data->pipeline), data->element_zero);

			    if(data->wfd_jitterbuffer_latency != -1)
			        g_signal_connect( data->element_zero, "new-jitterbuffer",
			                          G_CALLBACK(csio_jni_callback_rtpbin_new_jitterbuffer),
			                          (gpointer)data->wfd_jitterbuffer_latency );

			    data->udp_port = CSIOCnsIntf->getStreamTxRx_TSPORT(iStreamId);
			    data->element_av[0] = gst_element_factory_make("udpsrc", NULL);
			    insert_udpsrc_probe(data,data->element_av[0],"src");

			    //create the second udpsrc for rtcp
             data->element_av[1] = gst_element_factory_make("udpsrc", NULL);
             g_object_set(G_OBJECT(data->element_av[1]), "port", (data->udp_port + 1), NULL);
             GstCaps *RtcpCaps = gst_caps_new_simple("application/x-rtcp",NULL);
             if(RtcpCaps)
             {
                 g_object_set(G_OBJECT(data->element_av[1]), "caps", RtcpCaps, NULL);
                 gst_caps_unref( RtcpCaps );
             }
             else
             {
                 CSIO_LOG(eLogLevel_error, "ERROR: Cannot create RtcpCaps\n");
             }

             gst_bin_add(GST_BIN(data->pipeline), data->element_av[1]);
             int linkRtcpRet = gst_element_link(data->element_av[1], data->element_zero);
             if(linkRtcpRet==0)
             {
                 CSIO_LOG(eLogLevel_error,  "ERROR:  Cannot link filter to source elements.\n" );
                 iStatus = CSIO_CANNOT_LINK_ELEMENTS;
             }
             else
             {
                 CSIO_LOG(eLogLevel_debug,  "link filter to source elements.\n" );
             }

             // ***
             int ret;
             int ret1 = 0;
             int doDTLS = 0;

             // it is safe - we do not actually use the sssl
             void * sssl = sssl_getDTLSWithStreamID(iStreamId,1);
             if(sssl != NULL)
             {
                 CSIO_LOG(eLogLevel_debug,"mira: {%s} - DTLS context detected",__FUNCTION__);
                 doDTLS = 1;
             }
             else
             {
                 CSIO_LOG(eLogLevel_debug,"mira: {%s} - DTLS context NOT detected",__FUNCTION__);
             }

             if(doDTLS)
			    {
                data->element_appsrc = gst_element_factory_make("appsrc", NULL);
                gst_bin_add(GST_BIN(data->pipeline), data->element_appsrc);

                // do I need this ? ...
                g_object_set(G_OBJECT(data->element_appsrc), "caps", data->caps_v_ts, NULL);
			    }
             else
             {
                g_object_set(G_OBJECT(data->element_av[0]), "caps", data->caps_v_ts, NULL);
			       g_object_set(G_OBJECT(data->element_av[0]), "port", data->udp_port, NULL);
			       gst_bin_add(GST_BIN(data->pipeline), data->element_av[0]);
             }

             if(doDTLS)
             {
			       ret = gst_element_link(data->element_appsrc, data->element_zero);

                CSIO_LOG(eLogLevel_debug,"mira: {%s} - linked DTLS GStreamer pipeline",__FUNCTION__);

                CSIO_LOG(eLogLevel_debug,"mira: {%s} - ***** starting RTP thread *****",__FUNCTION__);

                ret1 = csio_jni_StartRTPMediaStreamThread(iStreamId,data->element_appsrc,
                     (unsigned int)data->udp_port);

                CSIO_LOG(eLogLevel_debug,"mira: {%s} - ===== RTP thread started =====",__FUNCTION__);
             }
             else ret = gst_element_link(data->element_av[0], data->element_zero);

			    if((ret == 0) || (ret1 != 0))
			    {
			        CSIO_LOG(eLogLevel_error,"mira: Cannot link pipeline elements.\n" );
				     iStatus = CSIO_CANNOT_LINK_ELEMENTS;
			    }
			    else
			        CSIO_LOG(eLogLevel_debug,"mira: linked pipeline elements.\n" );

             // ***


				// Set up udpsink for RTCP
				if (strlen(data->rtcp_dest_ip_addr) != 0 && data->rtcp_dest_port >= 0) {
					// Need to get socket info from udpsrc
					GSocket* rtcpUdpSocket = 0;
					gst_element_set_state (data->element_av[1], GST_STATE_READY);
					g_object_get(G_OBJECT(data->element_av[1]), "used-socket", &rtcpUdpSocket, NULL);

					data->element_av[2] = gst_element_factory_make("udpsink", NULL);
					gst_bin_add(GST_BIN(data->pipeline), data->element_av[2]);

					CSIO_LOG(eLogLevel_verbose, "Setting rtcp dst IP as %s:%d", data->rtcp_dest_ip_addr, data->rtcp_dest_port);
					g_object_set (G_OBJECT(data->element_av[2]), "port", data->rtcp_dest_port,
							"auto-multicast", FALSE,
							"host", data->rtcp_dest_ip_addr,
							"loop", FALSE,
							"sync", FALSE,
							"async", FALSE,
							NULL);

					if (rtcpUdpSocket) {
						g_object_set(G_OBJECT(data->element_av[2]), "socket",
									 rtcpUdpSocket, 
									 "close-socket", FALSE,
									 NULL);
						g_object_unref(rtcpUdpSocket);
					}

					GstPad *srcpad = gst_element_get_request_pad(data->element_zero, "send_rtcp_src_0");
					GstPad *sinkpad = gst_element_get_static_pad(data->element_av[2], "sink");

					if (gst_pad_link(srcpad, sinkpad) != GST_PAD_LINK_OK)
						CSIO_LOG(eLogLevel_info, "Failed to link rtpbin to udpsink for RTCP");
					
					gst_object_unref(srcpad);
					gst_object_unref(sinkpad);
				}
		    }
		    else if(CSIOCnsIntf->getStreamTxRx_TRANSPORTMODE(iStreamId)==STREAM_TRANSPORT_MPEG2TS_UDP)
		    {
		        data->element_zero = gst_element_factory_make("udpsrc", NULL);
			    if(!data->element_zero)
			    {
				    iStatus = CSIO_CANNOT_CREATE_ELEMENTS;
				    CSIO_LOG(eLogLevel_error,  "ERROR: Cannot create udp source pipeline elements\n" );
			    }
			    else
			        insert_udpsrc_probe(data,data->element_zero,"src");

			    data->udp_port = CSIOCnsIntf->getStreamTxRx_TSPORT(iStreamId);
			    g_object_set(G_OBJECT(data->element_zero), "port", data->udp_port, NULL);

			    data->element_av[0] = gst_element_factory_make( "queue", NULL );
			    if(!data->element_av[0])
			    {
				    iStatus = CSIO_CANNOT_CREATE_ELEMENTS;
				    CSIO_LOG(eLogLevel_error,  "ERROR: Cannot create queue source pipeline elements\n" );
			    }
			    
			    data->element_av[1] = gst_element_factory_make( "tsdemux", NULL );
			    if(!data->element_av[1])
			    {
				    iStatus = CSIO_CANNOT_CREATE_ELEMENTS;
				    CSIO_LOG(eLogLevel_error,  "ERROR: Cannot create tsdemux source pipeline elements\n" );
			    }
			    data->mpegtsPresent = TRUE;

			    gst_bin_add_many(GST_BIN(data->pipeline), data->element_zero, data->element_av[0], data->element_av[1], NULL);
			    if( !gst_element_link_many(data->element_zero, data->element_av[0], data->element_av[1], NULL))
			    {
			        CSIO_LOG(eLogLevel_error,  "ERROR:  Cannot link filter to source elements.\n" );
				    iStatus = CSIO_CANNOT_LINK_ELEMENTS;
			    }
			    else
			        CSIO_LOG(eLogLevel_debug, "success linking pipeline elements\n");
		    }	
		    *pipeline = data->pipeline;
		    *source   = data->element_zero;
		    break;
	    }
	    case ePROTOCOL_UDP:
	    {
			//build_udp_pipeline(CresDataDB,protoId);
			data->element_zero = gst_element_factory_make("rtpbin", NULL);
			gst_bin_add(GST_BIN(data->pipeline), data->element_zero);

			//video
			data->udp_video_port = CSIOCnsIntf->getStreamTxRx_RTPVIDEOPORT(iStreamId);
			data->element_av[0] = gst_element_factory_make("udpsrc", NULL);
			insert_udpsrc_probe(data,data->element_av[0],"src");

			g_object_set(G_OBJECT(data->element_av[0]), "port", data->udp_video_port, NULL);
			g_object_set(G_OBJECT(data->element_av[0]), "caps", data->caps_v_rtp, NULL);
			gst_bin_add(GST_BIN(data->pipeline), data->element_av[0]);
			gst_element_link(data->element_av[0], data->element_zero);

			//audio
			if(!debug_blocking_audio)
			{
			    data->element_av[1] = gst_element_factory_make("udpsrc", NULL);

                data->udp_audio_port = CSIOCnsIntf->getStreamTxRx_RTPAUDIOPORT(iStreamId);
                g_object_set(G_OBJECT(data->element_av[1]), "port", data->udp_audio_port, NULL);
                g_object_set(G_OBJECT(data->element_av[1]), "caps", data->caps_a_rtp, NULL);
                gst_bin_add(GST_BIN(data->pipeline), data->element_av[1]);
                gst_element_link(data->element_av[1], data->element_zero);
			}

            *pipeline = data->pipeline;
            *source   = data->element_zero;

	    	break;
	    }
	    case ePROTOCOL_MULTICAST_TS:
	    {
	    	buf = (char *) malloc(MAX_VIDEO_URL_SIZE);
	    	CSIOCnsIntf->getStreamTxRx_MULTICASTADDRESS(iStreamId, buf, MAX_VIDEO_URL_SIZE);
	    	strcpy(data->multicast_grp, buf);
	    	free(buf);
		    if(CSIOCnsIntf->getStreamTxRx_TRANSPORTMODE(iStreamId)==STREAM_TRANSPORT_MPEG2TS_RTP)
		    {
				CSIO_LOG(eLogLevel_debug,  "in STREAM_TRANSPORT_MPEG2TS_RTP\n" );

				data->element_zero = gst_element_factory_make("rtpbin", NULL);
				gst_bin_add(GST_BIN(data->pipeline), data->element_zero);

				data->udp_port = CSIOCnsIntf->getStreamTxRx_TSPORT(iStreamId);
				data->element_av[0] = gst_element_factory_make("udpsrc", NULL);
				insert_udpsrc_probe(data,data->element_av[0],"src");

				g_object_set(G_OBJECT(data->element_av[0]), "caps", data->caps_v_ts, NULL);
				g_object_set(G_OBJECT(data->element_av[0]), "port", data->udp_port, NULL);
				gst_bin_add(GST_BIN(data->pipeline), data->element_av[0]);
				int ret = gst_element_link(data->element_av[0], data->element_zero);
				if(ret==0){
					CSIO_LOG(eLogLevel_error,  "ERROR:  Cannot link filter to source elements.\n" );
					iStatus = CSIO_CANNOT_LINK_ELEMENTS;
				}
				else
					CSIO_LOG(eLogLevel_error,  "ERROR: link filter to source elements.\n" );
		    }
		    else if(CSIOCnsIntf->getStreamTxRx_TRANSPORTMODE(iStreamId)==STREAM_TRANSPORT_MPEG2TS_UDP)
		    {
				CSIO_LOG(eLogLevel_debug,  "in STREAM_TRANSPORT_MPEG2TS_UDP\n" );

			    data->element_zero = gst_element_factory_make("udpsrc", NULL);
			    if(!data->element_zero)
			    {
				    iStatus = CSIO_CANNOT_CREATE_ELEMENTS;
				    CSIO_LOG(eLogLevel_error,  "ERROR: Cannot create udp source pipeline elements\n" );
			    }
			    insert_udpsrc_probe(data,data->element_zero,"src");
			    			    
			    data->udp_port = CSIOCnsIntf->getStreamTxRx_TSPORT(iStreamId);
			    g_object_set(G_OBJECT(data->element_zero), "port", data->udp_port, NULL);

			    data->element_av[0] = gst_element_factory_make( "queue", NULL );
			    if(!data->element_av[0])
			    {
				    iStatus = CSIO_CANNOT_CREATE_ELEMENTS;
				    CSIO_LOG(eLogLevel_error,  "ERROR: Cannot create queue source pipeline elements\n" );
			    }
			    
			    data->element_av[1] = gst_element_factory_make( "tsdemux", NULL );
			    if(!data->element_av[1])
			    {
				    iStatus = CSIO_CANNOT_CREATE_ELEMENTS;
				    CSIO_LOG(eLogLevel_error,  "ERROR: Cannot create tsdemux source pipeline elements\n" );
			    }
			    data->mpegtsPresent = TRUE;

			    gst_bin_add_many(GST_BIN(data->pipeline), data->element_zero, data->element_av[0], data->element_av[1], NULL);

			    if( !gst_element_link_many(data->element_zero, data->element_av[0], data->element_av[1], NULL))
			    {
				    CSIO_LOG(eLogLevel_error,  "ERROR:  Cannot link filter to source elements.\n" );
				    iStatus = CSIO_CANNOT_LINK_ELEMENTS;
			    }
			    else
				    CSIO_LOG(eLogLevel_debug, "success link pipeline elements\n");
			}else 
			{
				CSIO_LOG(eLogLevel_debug,  "WARNING - invalid case\n" );

			}

			*pipeline = data->pipeline;
			*source   = data->element_zero;
	    }
	    	break;
	    case ePROTOCOL_MULTICAST:
	    {
	    	//CSIO_LOG(eLogLevel_debug, "ePROTOCOL_MULTICAST\n");
	    	buf = (char *) malloc(MAX_VIDEO_URL_SIZE);
	    	CSIOCnsIntf->getStreamTxRx_MULTICASTADDRESS(iStreamId, buf, MAX_VIDEO_URL_SIZE);
	    	strcpy(data->multicast_grp, buf);
	    	free(buf);

	    	//build_udp_pipeline(data,protoId);
	    	data->element_zero = gst_element_factory_make("rtpbin", NULL);
			gst_bin_add(GST_BIN(data->pipeline), data->element_zero);

			//video
			data->udp_video_port = CSIOCnsIntf->getStreamTxRx_RTPVIDEOPORT(iStreamId);
			data->element_av[0] = gst_element_factory_make("udpsrc", NULL);
            insert_udpsrc_probe(data,data->element_av[0],"src"); 

			g_object_set(G_OBJECT(data->element_av[0]), "port", data->udp_video_port, NULL);
			g_object_set(G_OBJECT(data->element_av[0]), "caps", data->caps_v_rtp, NULL);
			gst_bin_add(GST_BIN(data->pipeline), data->element_av[0]);
			gst_element_link(data->element_av[0], data->element_zero);

			//audio
			data->element_av[1] = gst_element_factory_make("udpsrc", NULL);
			data->udp_audio_port = CSIOCnsIntf->getStreamTxRx_RTPAUDIOPORT(iStreamId);
			g_object_set(G_OBJECT(data->element_av[1]), "port", data->udp_audio_port, NULL);
			g_object_set(G_OBJECT(data->element_av[1]), "caps", data->caps_a_rtp, NULL);
			gst_bin_add(GST_BIN(data->pipeline), data->element_av[1]);
			gst_element_link(data->element_av[1], data->element_zero);

	    	*pipeline = data->pipeline;
	    	*source   = data->element_zero;

	    	break;
	    }
        case ePROTOCOL_FILE:
        {
            CSIO_LOG( eLogLevel_debug, "%s() in ePROTOCOL_FILE\n", __FUNCTION__ );
            data->element_zero = gst_element_factory_make("filesrc", NULL);
            if(!data->element_zero)
            {
                iStatus = CSIO_CANNOT_CREATE_ELEMENTS;
                CSIO_LOG(eLogLevel_error,  "ERROR: Cannot create FILESRC element\n" );
            }

            data->element_av[0] = gst_element_factory_make("sdpdemux", NULL);
            if(!data->element_av[0])
            {
                iStatus = CSIO_CANNOT_CREATE_ELEMENTS;
                CSIO_LOG(eLogLevel_error,  "ERROR: Cannot create SDPDEMUX element\n" );
            }

            gst_bin_add_many(GST_BIN(data->pipeline), data->element_zero, data->element_av[0], NULL);
            if( !gst_element_link_many(data->element_zero, data->element_av[0], NULL))
            {
                CSIO_LOG(eLogLevel_error,  "ERROR: Cannot link filesrc to sdpdemux.\n" );
                iStatus = CSIO_CANNOT_LINK_ELEMENTS;
            }
            else
            	CSIO_LOG(eLogLevel_debug, "success linked filesrc and sdpdemux\n");
            
            *pipeline = data->pipeline;
            *source   = data->element_zero;
            break;
        }
	    case ePROTOCOL_UDP_BPT:
	    {
	    	data->element_zero  = gst_element_factory_make("rtpbin", NULL);
			data->element_av[0] = gst_element_factory_make("udpsrc", NULL);
			data->element_av[1] = gst_element_factory_make( "valve", NULL );
			data->element_av[2] = gst_element_factory_make("queue", NULL);
			insert_udpsrc_probe(data,data->element_av[0],"src");
			gst_bin_add_many( GST_BIN( data->pipeline ), data->element_zero, data->element_av[0], data->element_av[1], data->element_av[2], NULL );

			data->element_v[0] = gst_element_factory_make("rtph264depay", NULL);
			data->element_v[1] = gst_element_factory_make("h264parse", NULL);
			data->element_v[2] = gst_element_factory_make("queue", NULL);
			data->element_v[3] = gst_element_factory_make(product_info()->H264_decoder_string, NULL);
			data->amcvid_dec = data->element_v[3];
			gst_bin_add_many( GST_BIN( data->pipeline ), data->element_v[0], data->element_v[1], data->element_v[2], data->element_v[3], NULL );

            *pipeline = data->pipeline;
            *source   = data->element_zero;

	    	break;
	    }
        default:
			CSIO_LOG(eLogLevel_error,  "ERROR: invalid protoID.\n" );
			iStatus = CSIO_FAILURE;
			break;
	}

	return iStatus;
}


void csio_jni_InitPipeline(eProtocolId protoId, int iStreamId,GstRTSPLowerTrans tcpModeFlags)
{
	CREGSTREAM * data = GetStreamFromCustomData(CresDataDB, iStreamId);
	
	if(!data)
	{
		CSIO_LOG(eLogLevel_error, "Could not obtain stream pointer for stream %d", iStreamId);
		return;
	}	

	// Reset TS flag
	data->mpegtsPresent = FALSE;

	// Read if we should do HDCP decryption, TODO: this will be commanded from CSIO through join eventually
	//Set dynamically from CSIO now
/*	data->doHdcp = FALSE;
	int hdcpEncrypt = (int)data->doHdcp;
	FILE * hdcpEncryptFile = fopen("/dev/shm/crestron/CresStreamSvc/HDCPEncrypt", "r");
	// Check if user set hdcp encrypt value
	if (hdcpEncryptFile != NULL)
	{
		fscanf(hdcpEncryptFile, "%d", &hdcpEncrypt);
		fclose(hdcpEncryptFile);
	}
	data->doHdcp = (gboolean)!!hdcpEncrypt;*/


	switch( protoId )
	{
		case ePROTOCOL_RTSP_TCP:
		case ePROTOCOL_RTSP_UDP:
		case ePROTOCOL_DEFAULT_RTSP_TCP:
		case ePROTOCOL_RTSP_UDP_TS:
		case ePROTOCOL_RTSP_TS:
		{
			g_object_set(G_OBJECT(data->element_zero), "location", CSIOCnsIntf->getStreamTxRx_STREAMLOCATION(iStreamId).c_str(), NULL);
			g_object_set(G_OBJECT(data->element_zero), "latency", CSIOCnsIntf->getStreamRx_BUFFER(iStreamId), NULL);
			g_object_set(G_OBJECT(data->element_zero), "tcp_timeout", data->tcp_timeout_usec, NULL);
			g_object_set(G_OBJECT(data->element_zero), "timeout", data->udp_timeout_usec, NULL);
			// For some reason, this port range must be set for rtsp with multicast to work.
			//g_object_set(G_OBJECT(data->element_zero), "port-range", "5001-65535", NULL);
			data->protocols = tcpModeFlags;
			g_object_set(G_OBJECT(data->element_zero), "protocols", data->protocols, NULL);
			g_object_set(G_OBJECT(data->element_zero), "udp-buffer-size", DEFAULT_UDP_BUFFER, NULL);
			
			g_object_set(G_OBJECT(data->element_zero), "user-agent", (gchar *)CRESTRON_USER_AGENT, NULL); // TESTING REMOVE

			if(data->rtcp_dest_ip_addr[0])
			    g_object_set(G_OBJECT(data->element_zero), "rtcp-destination-ip", data->rtcp_dest_ip_addr, NULL);

			// video part
			data->video_sink = NULL;
			break;
		}
		case ePROTOCOL_UDP_TS:
			if (CSIOCnsIntf->getStreamTxRx_TRANSPORTMODE(iStreamId)==STREAM_TRANSPORT_MPEG2TS_RTP)
				g_object_set(G_OBJECT(data->element_av[0]), "buffer-size", DEFAULT_UDP_BUFFER, NULL);
			else if(CSIOCnsIntf->getStreamTxRx_TRANSPORTMODE(iStreamId)==STREAM_TRANSPORT_MPEG2TS_UDP)
				g_object_set(G_OBJECT(data->element_zero), "buffer-size", DEFAULT_UDP_BUFFER, NULL);

			break;

		case ePROTOCOL_UDP:
		{
			g_object_set(G_OBJECT(data->element_av[0]), "buffer-size", DEFAULT_UDP_BUFFER, NULL);
			if(!debug_blocking_audio)
				g_object_set(G_OBJECT(data->element_av[1]), "buffer-size", DEFAULT_UDP_BUFFER, NULL);
			//CSIO_LOG(eLogLevel_debug, "ePROTOCOL_UDP pass\n");
			break;
		}
		case ePROTOCOL_UDP_BPT:
		{
			g_object_set(G_OBJECT(data->element_av[0]), "caps", data->caps_v_rtp, NULL);
			gst_element_link_many(  data->element_av[0], data->element_av[1], data->element_av[2], NULL);

			int i = 3;  //index to decoder

			GstPad *pad;
			pad = gst_element_get_static_pad( data->element_v[i-1], "src" );	//queue before decoder
			if( pad != NULL )
			{
				guint video_probe_id = gst_pad_add_probe( pad, GST_PAD_PROBE_TYPE_BUFFER, csio_videoProbe, (void *) &data->streamId, NULL );
				csio_SetVideoProbeId(data->streamId, video_probe_id);
				gst_object_unref( pad );
			}

			csio_SetVpuDecoder(data->amcvid_dec, data->streamId);

			//SET OFSSET to zero for now
			g_object_set(G_OBJECT(data->amcvid_dec), "ts-offset", 0, NULL);

			//pass surface object to the decoder
			g_object_set(G_OBJECT(data->element_v[i]), "surface-window", data->surface, NULL);
			CSIO_LOG(eLogLevel_debug, "SET surface-window[0x%x][%d]",data->surface,data->surface);

			if(data->amcvid_dec && csio_GetWaitDecHas1stVidDelay(data->streamId) == 0)
			{
				int sigId = 0;
				sigId = g_signal_connect(data->amcvid_dec, "crestron-vdec-output", G_CALLBACK(csio_DecVideo1stOutputCB), (gpointer)data->streamId);
				CSIO_LOG(eLogLevel_debug, "connect to crestron-vdec-output: StreamId[%d],sigHandlerId[%d]",data->streamId,sigId);

				if(sigId)
					csio_SetWaitDecHas1stVidDelay(data->streamId,1);
			}

			int j;
			for( j = 0; j < i; j++ )
			{
				if(!gst_element_link(  data->element_v[j], data->element_v[j+1]))
				{
					CSIO_LOG(eLogLevel_error,  "ERROR: Cannot link video pipeline elements.\n" );
					break;
				}
			}

			if(gst_element_link(  data->element_av[2], data->element_v[0]))
			{
				CSIO_LOG(eLogLevel_debug,  "Linked video pipeline.\n" );
			}

			//skip for now  initVideo();

			break;
		}
		case ePROTOCOL_MULTICAST_TS:
			if (CSIOCnsIntf->getStreamTxRx_TRANSPORTMODE(iStreamId)==STREAM_TRANSPORT_MPEG2TS_RTP)
				g_object_set(G_OBJECT(data->element_av[0]), "buffer-size", DEFAULT_UDP_BUFFER, NULL);
			else if(CSIOCnsIntf->getStreamTxRx_TRANSPORTMODE(iStreamId)==STREAM_TRANSPORT_MPEG2TS_UDP)
				g_object_set(G_OBJECT(data->element_zero), "buffer-size", DEFAULT_UDP_BUFFER, NULL);
			break;

		case ePROTOCOL_MULTICAST:
		{
			g_object_set(G_OBJECT(data->element_av[0]), "buffer-size", DEFAULT_UDP_BUFFER, NULL);
			if(!debug_blocking_audio)
				g_object_set(G_OBJECT(data->element_av[1]), "buffer-size", DEFAULT_UDP_BUFFER, NULL);
			//CSIO_LOG(eLogLevel_debug, "ePROTOCOL_MULTICAST pass\n");
			break;
		}
		case ePROTOCOL_FILE:
		{
			g_object_set(G_OBJECT(data->element_av[0]), "latency", CSIOCnsIntf->getStreamRx_BUFFER(iStreamId), NULL);
			break;
		}
		default:
			//iStatus = CSIO_FAILURE;
			break;
	}
}

void csio_jni_SetSourceLocation(eProtocolId protoId, char *location, int iStreamId)
{
	CREGSTREAM * data = GetStreamFromCustomData(CresDataDB, iStreamId);
    char *url;

	CSIO_LOG(eLogLevel_debug, "%s() location: %s, iStreamId=%d", __FUNCTION__, location, iStreamId);
	
	if(!data)
	{
		CSIO_LOG(eLogLevel_error, "Could not obtain stream pointer for stream %d", iStreamId);
		return;
	}	
	
	switch( protoId )
	{
		case ePROTOCOL_RTSP_TCP:
		case ePROTOCOL_RTSP_UDP:
		case ePROTOCOL_DEFAULT_RTSP_TCP:
		case ePROTOCOL_RTSP_UDP_TS:
		case ePROTOCOL_RTSP_TS:
			g_object_set(G_OBJECT(data->element_zero), "location", location, NULL);
			break;
		case ePROTOCOL_UDP_TS:
		case ePROTOCOL_UDP:
			break;
		case ePROTOCOL_UDP_BPT:
			g_object_set( G_OBJECT( data->element_av[0]), "port",
					CSIOCnsIntf->getStreamTxRx_RTPVIDEOPORT(iStreamId), NULL );
			break;
		case ePROTOCOL_MULTICAST_TS:
		case ePROTOCOL_MULTICAST:
			//CSIO_LOG(eLogLevel_debug, "ePROTOCOL_MULTICAST: location[%s]\n",CresDataDB->multicast_grp);
			// Bug 134988: do not set local host as address because gstreamer needs it to be the default of 0.0.0.0
			if (strcmp(location, "127.0.0.1") != 0)
			{
				if(CSIOCnsIntf->getStreamTxRx_TRANSPORTMODE(iStreamId)==STREAM_TRANSPORT_MPEG2TS_UDP){
					g_object_set(G_OBJECT(data->element_zero), "address", \
							location, NULL);
				}
				else { //RTP Only and MPEG2TS RTP
					g_object_set(G_OBJECT(data->element_av[0]), "address", \
							location, NULL);
				}
			}
			//g_object_set(G_OBJECT(CresDataDB->element_av[1]), "address", 
			//		CresDataDB->multicast_grp, NULL);
			break;
		case ePROTOCOL_FILE:
            if ( !strcasestr(location, file_prefix) )
                CSIO_LOG(eLogLevel_error, "ERROR: %s invalid SDP url: %s\n", __FUNCTION__, location);
            g_object_set(G_OBJECT(data->element_zero), "location", &location[prefixLength7], NULL);
			break;
		default:
			break;
	}
}

void csio_jni_SetMsgHandlers(void* obj,eProtocolId protoId, int iStreamId)
{
	CREGSTREAM * data = GetStreamFromCustomData(CresDataDB, iStreamId);
	
	if(!data)
	{
		CSIO_LOG(eLogLevel_error, "Could not obtain stream pointer for stream %d", iStreamId);
		return;
	}	
	
	CSIO_LOG(eLogLevel_debug, "%s() protoId = %d\n", __FUNCTION__, protoId);
	switch( protoId )
	{
		case ePROTOCOL_RTSP_TCP:
		case ePROTOCOL_RTSP_UDP:
		case ePROTOCOL_DEFAULT_RTSP_TCP:
		case ePROTOCOL_MULTICAST:
		case ePROTOCOL_RTSP_UDP_TS:
		case ePROTOCOL_RTSP_TS:
		case ePROTOCOL_UDP:
		{
			//CSIO_LOG(eLogLevel_debug, "SetMsgHandlers protoId[%d]\n",protoId);
			// Register callback.
			if(data->element_zero != NULL)
			{				
				g_signal_connect(data->element_zero, "pad-added", G_CALLBACK(csio_PadAddedMsgHandler), obj);
			}
			else
			{
				CSIO_LOG(eLogLevel_warning, "Null element zero, no callbacks will be registered");
			}
			/* Set the pipeline to READY, so it can already accept a window handle, if we have one */
			 csio_element_set_state(data->pipeline, GST_STATE_READY);
			 if (data->video_sink)
			 {
				 // Try just setting the video sink, because we haven't built the pipeline yet.
				 //gst_element_set_state(data->video_sink, GST_STATE_READY);
			 }
			 else
			 {
				 data->video_sink = gst_bin_get_by_interface(GST_BIN(data->pipeline), GST_TYPE_VIDEO_OVERLAY);
			 }
			 break;
		}
		case ePROTOCOL_UDP_TS:
		case ePROTOCOL_MULTICAST_TS:
		{
			if(data->element_zero != NULL)
			{
		    		if(CSIOCnsIntf->getStreamTxRx_TRANSPORTMODE(iStreamId)==STREAM_TRANSPORT_MPEG2TS_RTP){
				g_signal_connect(data->element_zero, "pad-added", G_CALLBACK(csio_PadAddedMsgHandler), obj);
		    		}else if(CSIOCnsIntf->getStreamTxRx_TRANSPORTMODE(iStreamId)==STREAM_TRANSPORT_MPEG2TS_UDP){
				g_signal_connect(data->element_av[1], "pad-added", G_CALLBACK(csio_PadAddedMsgHandler), obj);
				}
			}
			else
			{
				CSIO_LOG(eLogLevel_warning, "Null element zero, no callbacks will be registered");
			}
			break;
		}
		case ePROTOCOL_HTTP:         //@todo: in http case, we should avoid call to here, we do nothing here.
        {
			CSIO_LOG(eLogLevel_debug, "%s() in ePROTOCOL_HTTP\n", __FUNCTION__);
            // run to here too quick !!!we haven't built data->element_v[1] yet for Adaptive Streaming
			break;
        }
		case ePROTOCOL_FILE:
			CSIO_LOG(eLogLevel_debug, "%s() in ePROTOCOL_FILE\n", __FUNCTION__);
			if(data->element_zero != NULL)
			{
			    g_signal_connect(data->element_av[0], "pad-added", G_CALLBACK(csio_PadAddedMsgHandler), obj);
			}
			else
			{
				CSIO_LOG(eLogLevel_warning, "Null element zero, no callbacks will be registered");
			}
			CSIO_LOG(eLogLevel_debug, "%s() in ePROTOCOL_FILE: Set the pipeline to READY \n", __FUNCTION__);
			csio_element_set_state(data->pipeline, GST_STATE_READY);   //Set the pipeline to READY
			data->video_sink = gst_bin_get_by_interface(GST_BIN(data->pipeline), GST_TYPE_VIDEO_OVERLAY);
			csio_SendDSVideoReady();
		    break;
		case ePROTOCOL_UDP_BPT:
			break;
		default:
			CSIO_LOG(eLogLevel_debug, "%s() not supportted !!\n", __FUNCTION__);
			//iStatus = CSIO_FAILURE;
			break;
	}
}

int csio_jni_AddAudio(GstPad *new_pad,gchar *encoding_name, GstElement **sink, bool send_pause, int iStreamId)
{
	int iStatus  = CSIO_SUCCESS;
	*sink = NULL;
	GstElement *ele0 = NULL;
	int do_rtp = 0;
	CREGSTREAM * data = GetStreamFromCustomData(CresDataDB, iStreamId);
	gchar * p_caps_string = NULL;
	GstCaps *new_pad_caps = NULL;
	GstPad *sink_pad = NULL;

	if(!data)
	{
		CSIO_LOG(eLogLevel_error, "Could not obtain stream pointer for stream %d", iStreamId);
		return CSIO_FAILURE;
	}	

	if(debug_blocking_audio)
	{
	    CSIO_LOG(eLogLevel_debug, "debug_blocking_audio is set");
	    return CSIO_AUDIO_BLOCKED;
	}

	//Extracted from STR, to support IC Camera with mulaw
	if(send_pause){
		csio_element_set_state( data->pipeline, GST_STATE_PAUSED);
	}

	new_pad_caps = gst_pad_query_caps( new_pad, NULL );
	p_caps_string = gst_caps_to_string (new_pad_caps);
	if(strncmp(p_caps_string, "application/x-rtp", 17) == 0)
	{
		do_rtp = 1;
	}

	iStatus = build_audio_pipeline(encoding_name, data, do_rtp,&ele0,sink);
	if (iStatus == CSIO_AUDIO_BLOCKED)
		goto doneAddAudio ;
	if(ele0 == NULL)
	{
		iStatus  = CSIO_CANNOT_CREATE_ELEMENTS;
		goto doneAddAudio ;
	}

	// calling initAudio on return

	// Get the pad given an element.
	sink_pad = gst_element_get_static_pad (ele0, "sink");
	if(sink_pad == NULL)
	{
		iStatus  = CSIO_CANNOT_LINK_ELEMENTS;
		goto doneAddAudio ;
	}

	if(gst_pad_is_linked (sink_pad))
	{
		CSIO_LOG(eLogLevel_info, "audio sink pad is already linked");
		gst_object_unref(sink_pad);

		iStatus  =  CSIO_CANNOT_LINK_ELEMENTS;
		goto doneAddAudio ;
	}

	// Link rest of pipeline to beginning.
	gst_pad_link(new_pad, sink_pad);

	//call init before set to play state when video was added first
	csio_jni_initVideo(iStreamId);
	csio_jni_initAudio(iStreamId);

	csio_element_set_state( data->pipeline, GST_STATE_PLAYING);

	CSIO_LOG(eLogLevel_debug, "csio_jni_AddAudio iStatus = %d", iStatus);

doneAddAudio:
	if(new_pad_caps != NULL)
	{
		gst_caps_unref(new_pad_caps);
	}

	return iStatus;
}

int csio_jni_AddVideo(GstPad *new_pad,gchar *encoding_name, GstElement **sink,eProtocolId protoIdi, bool send_pause, int iStreamId)
{
	int iStatus  = CSIO_SUCCESS;
	*sink = NULL;
	GstElement *ele0 = NULL;
	int do_rtp = 0;
	CREGSTREAM * data = GetStreamFromCustomData(CresDataDB, iStreamId);
	gchar * p_caps_string = NULL;
	GstCaps *new_pad_caps = NULL;
	GstPad *sink_pad = NULL;


	if(!data)
	{
		CSIO_LOG(eLogLevel_error, "Could not obtain stream pointer for stream %d", iStreamId);
		return CSIO_FAILURE;
	}	

	//CSIO_LOG(eLogLevel_debug, "csio_jni_AddVideo: sink =0x%x",protoId);
	//Extracted from STR, to support IC Camera
	if(send_pause){
		csio_element_set_state( data->pipeline, GST_STATE_PAUSED);
	}

	new_pad_caps = gst_pad_query_caps( new_pad, NULL );
	p_caps_string = gst_caps_to_string (new_pad_caps);
	if(strncmp(p_caps_string, "application/x-rtp", 17) == 0)
	{
		do_rtp = 1;
	}

	iStatus = build_video_pipeline(encoding_name, data,0,do_rtp,&ele0,sink);

	if(ele0 == NULL)
	{
		iStatus = CSIO_CANNOT_CREATE_ELEMENTS;
		goto doneAddVideo;
	}

	//TODOL need to call CStreamer::initVideo here

	// Get the pad given an element.
	sink_pad = gst_element_get_static_pad (ele0, "sink");
	if(sink_pad == NULL)
	{
		iStatus = CSIO_CANNOT_LINK_ELEMENTS;
		goto doneAddVideo;
	}

	if(gst_pad_is_linked (sink_pad))
	{
		CSIO_LOG(eLogLevel_info, "video sink pad is already linked");
		gst_object_unref(sink_pad);

		iStatus = CSIO_CANNOT_LINK_ELEMENTS;
		goto doneAddVideo;
	}

	// Link rest of pipeline to beginning.
	iStatus = gst_pad_link(new_pad, sink_pad);

	if(iStatus != CSIO_SUCCESS)
	{
		iStatus = CSIO_CANNOT_LINK_ELEMENTS;
		goto doneAddVideo;
	}

	//call initVideo before set to play state
	csio_jni_initVideo(iStreamId);

	if( csio_element_set_state( data->pipeline, GST_STATE_PLAYING) == GST_STATE_CHANGE_FAILURE )
	{
		CSIO_LOG(eLogLevel_error, "Cannot restart pipeline\n");
	}

	if(iStatus != CSIO_SUCCESS)
	{
		iStatus = CSIO_COULD_NOT_START_PIPELINE;
		goto doneAddVideo;
	}

	CSIO_LOG(eLogLevel_debug, "csio_jni_AddVideo iStatus = %d", iStatus);
doneAddVideo:
	if(new_pad_caps != NULL)
	{
		gst_caps_unref(new_pad_caps);
	}
	return iStatus;
}

void csio_jni_initAudio(int iStreamId)
{
    CREGSTREAM * data = GetStreamFromCustomData(CresDataDB, iStreamId);

    if(!data)
    {
        CSIO_LOG(eLogLevel_error, "Could not obtain stream pointer for stream %d", iStreamId);
        return;
    }

    if(data->element_valve_a)
    {
    	if (data->dropAudio)
		{
			CSIO_LOG(eLogLevel_debug, "Dropping audio flag set");
    		g_object_set(G_OBJECT(data->element_valve_a), "drop", TRUE, NULL);
		}
    	else
    		g_object_set(G_OBJECT(data->element_valve_a), "drop", FALSE, NULL);
    }

    if( data->audio_sink)
    {
    	gint64 tmp = data->audiosink_ts_offset * 1000000;

    	// Bug 107700: AV goes haywire when packets are lost when openslessink is set to GST_AUDIO_BASE_SINK_SLAVE_SKEW, resample fixes the problem
    	// Bug 110954: Setting this to 0 caused audio to get messed up, original issue was caused by encoder timestamp problem, leaving mode to GST_AUDIO_BASE_SINK_SLAVE_SKEW
    	//		g_object_set(G_OBJECT(data->audio_sink), "slave-method", 0, NULL); // 0 = GST_AUDIO_BASE_SINK_SLAVE_RESAMPLE

    	g_object_set(G_OBJECT(data->audio_sink), "ts-offset", tmp, NULL);
    	CSIO_LOG(eLogLevel_debug, "set audiosink_ts_offset:%lld",tmp);
    }
}

void csio_jni_initVideo(int iStreamId)
{
    CREGSTREAM * data = GetStreamFromCustomData(CresDataDB, iStreamId);
    
    if(!data)
    {
        CSIO_LOG(eLogLevel_error, "Could not obtain stream pointer for stream %d", iStreamId);
        return;
    }
    
    if(data->using_glimagsink)
    {
    CSIO_LOG(eLogLevel_debug, "qos is set to default");
    g_object_set(G_OBJECT(data->video_sink), "force-aspect-ratio", FALSE, NULL);
    }
    else
    {
        //SET OFSSET
        // Bug 113246: For RTSP modes we need to set ts offset, for udp modes we should not or AV sync is off
        if( data->amcvid_dec && (!debug_blocking_audio) && data->audio_sink &&
            ( CSIOCnsIntf->getStreamTxRx_SESSIONINITIATION(iStreamId) == 0 ||
              CSIOCnsIntf->getStreamTxRx_SESSIONINITIATION(iStreamId) == 2  ) &&
              (data->streamProtocolId != ePROTOCOL_HTTP) )
        {
        	int tmp;
        	if (data->mpegtsPresent)
        	{
        		tmp = CSIOCnsIntf->getStreamRx_BUFFER(iStreamId) +
									  data->amcviddec_ts_offset + DEFAULT_MPEG_TS_OFFSET;
        	}
        	else
        	{
        		tmp = CSIOCnsIntf->getStreamRx_BUFFER(iStreamId) +
        		                      data->amcviddec_ts_offset;
        	}
            g_object_set(G_OBJECT(data->amcvid_dec), "ts-offset", tmp, NULL);
            CSIO_LOG(eLogLevel_debug, "streamingBuffer or latency is:%d",CSIOCnsIntf->getStreamRx_BUFFER(iStreamId));
            CSIO_LOG(eLogLevel_debug, "amcviddec_ts_offset:%d",data->amcviddec_ts_offset);
            CSIO_LOG(eLogLevel_debug, "total ts_offset:%d",tmp);
        }
    
        if(data->element_valve_v)
            g_object_set(G_OBJECT(data->element_valve_v), "drop", FALSE, NULL);
    
        CSIO_LOG(eLogLevel_debug, "qos is turned off for surfaceflingersink!");
        if(data->video_sink)
            g_object_set(G_OBJECT(data->video_sink), "qos", FALSE, NULL);   
    }
}

GstElement * csio_jni_getVideoDecEle(int iStreamId)
{
    CREGSTREAM * data = GetStreamFromCustomData(CresDataDB, iStreamId);
    int i;

    if(!data)
    {
        CSIO_LOG(eLogLevel_error, "Could not obtain stream pointer for stream %d", iStreamId);
        return NULL;
    }

    return data->element_fake_dec;
}

void *csio_SendInitiatorAddressFb( void * arg )
{
	jstring initiatorAddress_jstr;
	JNIEnv *env = get_jni_env ();
	jint streamId = csio_GetStreamId(arg);

	CREGSTREAM * data = GetStreamFromCustomData(CresDataDB, streamId);
    if(!data)
    {
        CSIO_LOG(eLogLevel_error, "Could not obtain stream pointer for stream %d", streamId);
        pthread_exit( NULL );
        return NULL;
    }

	char *initiatorAddress_cstr = data->sourceIP_addr;//csio_GetInitiatorFbAddress(streamId);

	CSIO_LOG(eLogLevel_debug,  "Sent INITIATOR FB %s", initiatorAddress_cstr );

	initiatorAddress_jstr = env->NewStringUTF(initiatorAddress_cstr);

	jmethodID sendInitiatorAddress = env->GetMethodID((jclass)gStreamIn_javaClass_id, "sendInitiatorFbAddress", "(Ljava/lang/String;I)V");
	if (sendInitiatorAddress == NULL) return NULL;

	env->CallVoidMethod(CresDataDB->app, sendInitiatorAddress, initiatorAddress_jstr, 0);
	if (env->ExceptionCheck ()) {
		CSIO_LOG(eLogLevel_error, "Failed to call Java method 'sendInitiatorAddress'");
		env->ExceptionClear ();
	}
	env->DeleteLocalRef (initiatorAddress_jstr);

	pthread_exit( NULL );
	return(NULL);
}

void csio_recoverHdcpVideoFailure(int iStreamId)
{
	CREGSTREAM * data = GetStreamFromCustomData(CresDataDB, iStreamId);
	if(!data)
	{
		CSIO_LOG(eLogLevel_error, "Could not obtain stream pointer for stream %d", iStreamId);
		pthread_exit( NULL );
		return;
	}
	if (data->doHdcp)
	{
		CSIO_LOG(eLogLevel_warning, "%s:Recovering encrypted video", __FUNCTION__);
		JNIEnv *env = get_jni_env ();
		jmethodID recoverDucati = env->GetMethodID((jclass)gStreamIn_javaClass_id, "recoverHdcpVideoFailure", "(I)V");
		if (recoverDucati == NULL) return;

		env->CallVoidMethod(CresDataDB->app, recoverDucati, iStreamId);
		if (env->ExceptionCheck ()) {
			CSIO_LOG(eLogLevel_error, "Failed to call Java method 'recoverDucati'");
			env->ExceptionClear ();
		}
	}
}

void csio_jni_recoverDucati()
{
    JNIEnv *env = get_jni_env ();
    jmethodID recoverDucati = env->GetMethodID((jclass)gStreamIn_javaClass_id, "recoverDucati", "()V");
    if (recoverDucati == NULL) return;

    env->CallVoidMethod(CresDataDB->app, recoverDucati);
    if (env->ExceptionCheck ()) {
        CSIO_LOG(eLogLevel_error, "Failed to call Java method 'recoverDucati'");
        env->ExceptionClear ();
    }
}

void csio_jni_recoverTxrxService()
{
    JNIEnv *env = get_jni_env ();
    jclass cls = env->GetObjectClass(CresStreamOutDataDB->app);

    jmethodID recoverTxrxService = env->GetMethodID((jclass)cls, "recoverTxrxService", "()V");
    if (recoverTxrxService == NULL) return;

    env->CallVoidMethod(CresStreamOutDataDB->app, recoverTxrxService);
    if (env->ExceptionCheck ()) {
        CSIO_LOG(eLogLevel_error, "Failed to call Java method 'recoverTxrxService'");
        env->ExceptionClear ();
    }
}

void csio_jni_sendCameraStopFb()
{
    JNIEnv *env = get_jni_env ();
    jclass cls = env->GetObjectClass(CresStreamOutDataDB->app);

    jmethodID sendCameraStopFb = env->GetMethodID((jclass)cls, "sendCameraStopFb", "()V");
    if (sendCameraStopFb == NULL) return;

    env->CallVoidMethod(CresStreamOutDataDB->app , sendCameraStopFb);
    if (env->ExceptionCheck ()) {
        CSIO_LOG(eLogLevel_error, "Failed to call Java method 'sendCameraStopFb'");
        env->ExceptionClear ();
    }
}

void LocalConvertToUpper(char *str)
{
    char *TmpPtr;

    for (TmpPtr = str; *TmpPtr != 0; TmpPtr++)
    {
        *TmpPtr = toupper(*TmpPtr);
        if ( *TmpPtr == ' ')
            break;
    }
}
void csio_jni_printFieldDebugInfo()
{
    int i;

    CREGSTREAM * data = GetStreamFromCustomData(CresDataDB, 0);

    if(!data)
    {
        CSIO_LOG(eLogLevel_error, "Could not obtain stream pointer for stream %d", 0);
        return;
    }

    CSIO_LOG(eLogLevel_debug, "FieldDebugInfo Current setting:");
    for (i = 0; i < MAX_SPECIAL_FIELD_DEBUG_NUM - 1; i++)
    {

        if((i+1) == FIELD_DEBUG_SET_AMCVIDDEC_DEBUG_LEVEL)
        {
            CSIO_LOG(eLogLevel_debug, "FieldDebugInfo   %s  -- %d", \
                                fieldDebugNames[i], amcviddec_debug_level);
        }
        else if((i+1) == FIELD_DEBUG_SET_VIDEODECODER_DEBUG_LEVEL)
        {
            CSIO_LOG(eLogLevel_debug, "FieldDebugInfo   %s  -- %d", \
                                fieldDebugNames[i], videodecoder_debug_level);
        }        
        else if((i+1) == FIELD_DEBUG_SET_AUDIOSINK_TS_OFFSET)
        {
            CSIO_LOG(eLogLevel_debug, "FieldDebugInfo   %s  -- %dms", \
                                fieldDebugNames[i],data->audiosink_ts_offset);
        }
        else if((i+1) == FIELD_DEBUG_SET_AMCVIDDEC_TS_OFFSET)
        {
            CSIO_LOG(eLogLevel_debug, "FieldDebugInfo   %s  -- %dms", \
                                fieldDebugNames[i],data->amcviddec_ts_offset);
        }
        else if((i+1) == FIELD_DEBUG_PRINT_AUDIOSINK_PROPERTIES)
        {
            CSIO_LOG(eLogLevel_debug, "FieldDebugInfo   %s",fieldDebugNames[i]);
        }
        else
        {
            CSIO_LOG(eLogLevel_debug, "FieldDebugInfo   %s%s", \
                                fieldDebugNames[i], \
                                (IsSpecialFieldDebugIndexActive(i+1) ? "ON" : "OFF"));
        }
    }
}

/***************************** rtsp_server for video streaming out **************************************/

// rtsp object documentation: https://gstreamer.freedesktop.org/data/doc/gstreamer/head/gst-rtsp-server/html/GstRTSPServer.html
// copied code from gst-rtsp-server-1.4.5/examples/test-launch.c

static jboolean gstNativeClassInitRtspServer (JNIEnv* env, jclass klass)
{
    CSIO_LOG(eLogLevel_debug, "rtsp_server: gst_native_class_init_rtsp_server,env[0x%x],klass[0x%x]\n",env,klass);
    custom_data_field_id_rtsp_server = env->GetFieldID (klass, "native_custom_data", "J");
    set_message_method_id_rtsp_server = env->GetMethodID (klass, "setMessage", "(Ljava/lang/String;)V");
    //on_gstreamer_initialized_method_id = env->GetMethodID (klass, "onGStreamerInitialized", "()V");

    if (!custom_data_field_id_rtsp_server || !set_message_method_id_rtsp_server) {
        /* We emit this message through the Android log instead of the GStreamer log because the later
        * has not been initialized yet.
        */
        CSIO_LOG(eLogLevel_error, "rtsp_server: The calling class does not implement all necessary interface methods");
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

static void gstNativeInitRtspServer (JNIEnv* env, jobject thiz, jobject surface)
{
    CSIO_LOG(eLogLevel_debug, "rtsp_server: Creating rtsp server, jobject surface=%p", surface);

    CustomStreamOutData *cdata = g_new0 (CustomStreamOutData, 1);
    CresStreamOutDataDB = cdata;
    
    SET_CUSTOM_DATA (env, thiz, custom_data_field_id_rtsp_server, cdata);
    
    cdata->app = env->NewGlobalRef(thiz);
    init_custom_data_out(cdata);

    //init project
    StreamoutProjectInit();

    CSIO_LOG(eLogLevel_debug, "rtsp_server: gstNativeInitRtspServer exit.");
}

static void gstNativeFinalizeRtspServer (JNIEnv* env, jobject thiz)
{
    CustomStreamOutData *cdata = (CustomStreamOutData *)GET_CUSTOM_DATA (env, thiz, custom_data_field_id_rtsp_server);
    int i;

    if (!cdata) return;

    //turn off project and all stream out first
    StreamoutProjectDeInit();

    CSIO_LOG(eLogLevel_debug, "rtsp_server: Deleting GlobalRef for app object at %p", cdata->app);
    env->DeleteGlobalRef ((jobject)gStreamOut_javaClass_id);
    env->DeleteGlobalRef ((jobject)cdata->app);
    CSIO_LOG(eLogLevel_debug, "Freeing CustomData at %p", cdata);
    g_free (cdata);
    CresStreamOutDataDB = NULL;
    SET_CUSTOM_DATA (env, thiz, custom_data_field_id_rtsp_server, NULL);
    CSIO_LOG(eLogLevel_debug, "rtsp_server: Done finalizing");
}
/* Set stream out server to start */
void gst_native_rtsp_server_start (JNIEnv* env, jobject thiz)
{
    CSIO_LOG(eLogLevel_debug, "rtsp_server: gst_native_rtsp_server_start");
    Streamout_Start(0);
    CSIO_LOG(eLogLevel_debug, "rtsp_server: gst_native_rtsp_server_start is done");
}
/* Set stream out server to stop */
void gst_native_rtsp_server_stop (JNIEnv* env, jobject thiz)
{
    CSIO_LOG(eLogLevel_debug, "rtsp_server: gst_native_rtsp_server_stop");
    Streamout_Stop(0);
    CSIO_LOG(eLogLevel_debug, "rtsp_server: gst_native_rtsp_server_stop is done");
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamOut_nativeSetRtspPort(JNIEnv *env, jobject thiz, jint port, jint sessionId)
{
	if (!CresStreamOutDataDB)
	{
		CSIO_LOG(eLogLevel_info, "%s: cannot set value, CresStreamOutDataDB is null", __FUNCTION__);
		return;
	}
    CSIO_LOG(eLogLevel_debug, "rtsp_server: Using RtspPort: '%d'", port);
    char* desBuf = CresStreamOutDataDB->streamOut[0].rtsp_port;
    sprintf(desBuf, "%d", port);

    Streamout_SetPort(desBuf);
    CSIO_LOG(eLogLevel_debug, "rtsp_server: RtspPort in CresStreamOutDataDB: '%s'", CresStreamOutDataDB->streamOut[0].rtsp_port);
}
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamOut_nativeSet_1Res_1x(JNIEnv *env, jobject thiz, jint Res_x, jint sessionId)
{
	if (!CresStreamOutDataDB)
	{
		CSIO_LOG(eLogLevel_info, "%s: cannot set value, CresStreamOutDataDB is null", __FUNCTION__);
	  	return;
	}
    CSIO_LOG(eLogLevel_debug, "rtsp_server: Using Res_x: '%d'", Res_x);
    char* desBuf = CresStreamOutDataDB->streamOut[0].res_x;
    sprintf(desBuf, "%d", Res_x);

    Streamout_SetRes_x(desBuf);
    CSIO_LOG(eLogLevel_debug, "rtsp_server: res_x in CresStreamOutDataDB: '%s'", CresStreamOutDataDB->streamOut[0].res_x);
}
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamOut_nativeSet_1Res_1y(JNIEnv *env, jobject thiz, jint Res_y, jint sessionId)
{
	if (!CresStreamOutDataDB)
	{
		CSIO_LOG(eLogLevel_info, "%s: cannot set value, CresStreamOutDataDB is null", __FUNCTION__);
		return;
	}
    CSIO_LOG(eLogLevel_debug, "rtsp_server: Using Res_y: '%d'", Res_y);
    char* desBuf = CresStreamOutDataDB->streamOut[0].res_y;
    sprintf(desBuf, "%d", Res_y);

    Streamout_SetRes_y(desBuf);
    CSIO_LOG(eLogLevel_debug, "rtsp_server: res_y in CresStreamOutDataDB: '%s'", CresStreamOutDataDB->streamOut[0].res_y);
}
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamOut_nativeSet_1FrameRate(JNIEnv *env, jobject thiz, jint FrameRate, jint sessionId)
{
	if (!CresStreamOutDataDB)
	{
		CSIO_LOG(eLogLevel_info, "%s: cannot set value, CresStreamOutDataDB is null", __FUNCTION__);
		return;
	}
    CSIO_LOG(eLogLevel_debug, "rtsp_server: Using FrameRate: '%d'", FrameRate);
    char* desBuf = CresStreamOutDataDB->streamOut[0].frame_rate;
    sprintf(desBuf, "%d", FrameRate);

    Streamout_SetFrameRate(desBuf);
    CSIO_LOG(eLogLevel_debug, "rtsp_server: frame_rate in CresStreamOutDataDB: '%s'", CresStreamOutDataDB->streamOut[0].frame_rate);
}
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamOut_nativeSet_1Bitrate(JNIEnv *env, jobject thiz, jint Bitrate, jint sessionId)
{
	if (!CresStreamOutDataDB)
	{
		CSIO_LOG(eLogLevel_info, "%s: cannot set value, CresStreamOutDataDB is null", __FUNCTION__);
		return;
	}
    CSIO_LOG(eLogLevel_debug, "rtsp_server: Using Bitrate: '%d'", Bitrate);
    char* desBuf = CresStreamOutDataDB->streamOut[sessionId].bitrate;
    sprintf(desBuf, "%d", Bitrate);

    Streamout_SetBitrate(desBuf);
    CSIO_LOG(eLogLevel_debug, "rtsp_server: bitrate in CresStreamOutDataDB: '%s'", CresStreamOutDataDB->streamOut[0].bitrate);
}
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamOut_nativeSet_1IFrameInterval(JNIEnv *env, jobject thiz, jint IFrameInterval, jint sessionId)
{
	if (!CresStreamOutDataDB)
	{
		CSIO_LOG(eLogLevel_info, "%s: cannot set value, CresStreamOutDataDB is null", __FUNCTION__);
		return;
	}
    CSIO_LOG(eLogLevel_debug, "rtsp_server: Using iFrameInterval: '%d'", IFrameInterval);
    char* desBuf = CresStreamOutDataDB->streamOut[sessionId].iframe_interval;
    sprintf(desBuf, "%d", IFrameInterval);

    Streamout_SetIFrameInterval(desBuf);
    CSIO_LOG(eLogLevel_debug, "rtsp_server: IFrameInterval in CresStreamOutDataDB: '%s'", CresStreamOutDataDB->streamOut[0].iframe_interval);
}
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamOut_nativeSet_1MulticastEnable(JNIEnv *env, jobject thiz, jboolean enable, jint sessionId)
{
	if (!CresStreamOutDataDB)
	{
		CSIO_LOG(eLogLevel_info, "%s: cannot set value, CresStreamOutDataDB is null", __FUNCTION__);
		return;
	}
    CSIO_LOG(eLogLevel_debug, "rtsp_server: Using multicast enable: '%d'", enable);
    CresStreamOutDataDB->streamOut[sessionId].multicast_enable = enable;

    Streamout_EnableMulticast((int) CresStreamOutDataDB->streamOut[sessionId].multicast_enable);

    CSIO_LOG(eLogLevel_debug, "rtsp_server: multicast_enable in CresStreamOutDataDB: '%d'", CresStreamOutDataDB->streamOut[sessionId].multicast_enable);
}
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamOut_nativeSet_1MulticastAddress(JNIEnv *env, jobject thiz, jstring address_jstring, jint sessionId)
{
	if (!CresStreamOutDataDB)
	{
		CSIO_LOG(eLogLevel_info, "%s: cannot set value, CresStreamOutDataDB is null", __FUNCTION__);
		return;
	}
	const char * address_cstring = env->GetStringUTFChars( address_jstring , NULL ) ;
	if (address_cstring == NULL) return;

	CSIO_LOG(eLogLevel_debug, "rtsp_server: Using multicast address: '%s'", address_cstring);
	char* desBuf = CresStreamOutDataDB->streamOut[sessionId].multicast_address;
	strcpy(desBuf, address_cstring);

	Streamout_SetMulticastAddress(desBuf);
	CSIO_LOG(eLogLevel_debug, "rtsp_server: multicast_address in CresStreamOutDataDB: '%s'", CresStreamOutDataDB->streamOut[sessionId].multicast_address);
	env->ReleaseStringUTFChars(address_jstring, address_cstring);
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamOut_nativeSet_1StreamName(JNIEnv *env, jobject thiz, jstring name_jstring, jint sessionId)
{
	if (!CresStreamOutDataDB)
	{
		CSIO_LOG(eLogLevel_info, "%s: cannot set value, CresStreamOutDataDB is null", __FUNCTION__);
		return;
	}
	const char * name_cstring = env->GetStringUTFChars( name_jstring , NULL ) ;
	if (name_cstring == NULL) return;

	CSIO_LOG(eLogLevel_debug, "rtsp_server: Using stream name: '%s'", name_cstring);
	char* desBuf = CresStreamOutDataDB->streamOut[sessionId].stream_name;
	strcpy(desBuf, name_cstring);

	Streamout_SetStreamName(desBuf);
	CSIO_LOG(eLogLevel_debug, "rtsp_server: stream_name in CresStreamOutDataDB: '%s'", CresStreamOutDataDB->streamOut[sessionId].stream_name);
	env->ReleaseStringUTFChars(name_jstring, name_cstring);
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamOut_nativeSet_1SnapshotName(JNIEnv *env, jobject thiz, jstring name_jstring, jint sessionId)
{
	if (!CresStreamOutDataDB)
	{
		CSIO_LOG(eLogLevel_info, "%s: cannot set value, CresStreamOutDataDB is null", __FUNCTION__);
		return;
	}
	const char * name_cstring = env->GetStringUTFChars( name_jstring , NULL ) ;
	if (name_cstring == NULL) return;

	CSIO_LOG(eLogLevel_debug, "rtsp_server: Using snapshot name: '%s'", name_cstring);
	char* desBuf = CresStreamOutDataDB->streamOut[sessionId].snapshot_name;
	strcpy(desBuf, name_cstring);

	Streamout_SetSnapshotName(desBuf);
	CSIO_LOG(eLogLevel_debug, "rtsp_server: snapshot_name in CresStreamOutDataDB: '%s'", CresStreamOutDataDB->streamOut[sessionId].snapshot_name);
	env->ReleaseStringUTFChars(name_jstring, name_cstring);
}
/***************************** end of rtsp_server for video streaming out *********************************/

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamOut_nativeStartPreview(JNIEnv* env, jobject thiz, jobject surface, jint stream)
{
    CSIO_LOG(eLogLevel_verbose, "Preview: Creating jobject surface=%p, stream_id=%d", surface, stream);
    ANativeWindow *native_window;

    if(surface)
    {
		native_window = ANativeWindow_fromSurface(env, surface);
		if (!native_window)
		{
			CSIO_LOG(eLogLevel_error, "Preview: No native window for stream");
		}
		else
		{
			CSIO_LOG(eLogLevel_verbose, "Preview: Creating native preview window %p for stream %d", native_window, stream);
			Streamout_StartPreview(stream, native_window);
		}
    }
    else
    {
        CSIO_LOG(eLogLevel_error, "Preview: No surface for stream");
    }

    CSIO_LOG(eLogLevel_verbose, "Preview: startPreview exit.");
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamOut_nativePausePreview(JNIEnv* env, jobject thiz, jint stream)
{
    CSIO_LOG(eLogLevel_verbose, "Preview: Pausing preview stream_id=%d", stream);

    Streamout_PausePreview(stream);

    CSIO_LOG(eLogLevel_verbose, "Preview: pausePreview exit.");
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamOut_nativeStopPreview(JNIEnv* env, jobject thiz, jint stream)
{
    CSIO_LOG(eLogLevel_verbose, "Preview: Stopping preview stream_id=%d", stream);

    Streamout_StopPreview(stream);

    CSIO_LOG(eLogLevel_verbose, "Preview: stopPreview exit.");
}

JNIEXPORT int JNICALL Java_com_crestron_txrxservice_GstreamOut_nativeWaitForPreviewAvailable(JNIEnv* env, jobject thiz, jint stream, jint timeout_sec)
{
    CSIO_LOG(eLogLevel_verbose, "Preview: wait up to %ds for preview stream_id=%d to play", timeout_sec, stream);

    int rtn;

    rtn = Streamout_WaitForPreviewAvailable(stream,timeout_sec);

    CSIO_LOG(eLogLevel_verbose, "Preview: WaitPreview exit. rtn = %d", rtn);

    return(rtn);
}

JNIEXPORT int JNICALL Java_com_crestron_txrxservice_GstreamOut_nativeWaitForPreviewClosed(JNIEnv* env, jobject thiz, jint stream, jint timeout_sec)
{
    CSIO_LOG(eLogLevel_verbose, "Preview: wait up to %ds for preview stream_id=%d to close", timeout_sec, stream);

    int rtn;

    rtn = Streamout_WaitForPreviewClosed(stream,timeout_sec);

    CSIO_LOG(eLogLevel_verbose, "Preview: WaitPreview close exit. rtn = %d", rtn);

    return(rtn);
}

/***************************** end of preview with video streaming out *********************************/
/***************************** start of Kaptivo whiteboard streaming in *********************************/

//this is to set up the surface format
static void wbs_jni_setup_surface_format(JNIEnv *env,ANativeWindow *new_native_window,Wbs_t *pWbs, jobject surface)
{
    int format = WINDOW_FORMAT_RGBA_8888;
    int err = 0;
    int usage = 0;
    int queuesToNativeWindow = 0;
    int minUndequeuedBufs = 0;

    CSIO_LOG(eLogLevel_debug, "%s: ANativeWindow format was %x", __FUNCTION__, ANativeWindow_getFormat(new_native_window));

    err = native_window_set_buffers_format(new_native_window, format);
    if (err != 0)
    {
        CSIO_LOG(eLogLevel_error, "Failed to set buffers format to %d", format);
        return;
    }

    pWbs->format = format;
    CSIO_LOG(eLogLevel_debug, "ANativeWindow after set buffers format, width=%d, height=%d, format=0x%x",
            ANativeWindow_getWidth(new_native_window), ANativeWindow_getHeight(new_native_window),
            ANativeWindow_getFormat(new_native_window));

    usage = GRALLOC_USAGE_PROTECTED | GRALLOC_USAGE_HW_TEXTURE
            | GRALLOC_USAGE_EXTERNAL_DISP; // Sunita - standard set of flags we set to allow HW acceleration of the layers
    native_window_set_usage(new_native_window, usage);

    err = native_window_set_scaling_mode(new_native_window,
            NATIVE_WINDOW_SCALING_MODE_SCALE_TO_WINDOW);
    if (err != 0)
    {
        CSIO_LOG(eLogLevel_error, "Failed to set buffers scaling_mode : %s (%d)",strerror(-err), -err);
        return;
    }

    err = new_native_window->query(new_native_window,
            NATIVE_WINDOW_QUEUES_TO_WINDOW_COMPOSER, &queuesToNativeWindow);
    if (err != 0)
    {
        CSIO_LOG(eLogLevel_error, "error authenticating native window: %d", err);
        return;
    }

    err = new_native_window->query(new_native_window,
            NATIVE_WINDOW_MIN_UNDEQUEUED_BUFFERS, (int*) &minUndequeuedBufs);
    if (err != 0)
    {
        CSIO_LOG(eLogLevel_error, "NATIVE_WINDOW_MIN_UNDEQUEUED_BUFFERS query failed: %s (%d)", strerror(-err), -err);
        return;
    }

    if (pWbs->surface)
    {
        CSIO_LOG(eLogLevel_debug, "Delete GlobalRef before adding new: %p", pWbs->surface);
        env->DeleteGlobalRef((jobject)pWbs->surface);
        pWbs->surface = NULL;
    }
    pWbs->surface = env->NewGlobalRef(surface);
    CSIO_LOG(eLogLevel_debug, "native window = %p,surface[%p],pWbs->surface[%p]", pWbs->native_window,surface,pWbs->surface);
}

// Invoke JAVA method updateWindow to update the transformation once width and height are known
void wbs_update_window(int sessId, int width, int height)
{
	JNIEnv *env = get_jni_env ();
	jint streamId = sessId;
	jint w = width;
	jint h = height;

	CSIO_LOG(eLogLevel_debug,  "wbs_update_window for sessionId %d (w=%d h=%d)", streamId, w, h );

	jmethodID update_window = env->GetMethodID((jclass)wbsStreamIn_javaClass_id, "updateWindow", "(III)V");
	if (update_window == NULL) {
		CSIO_LOG(eLogLevel_debug,  "Could not find Java method 'updateWindow'");
		return;
	}

	env->CallVoidMethod(wbs_get_app(), update_window, streamId, w, h);
	if (env->ExceptionCheck ()) {
		CSIO_LOG(eLogLevel_error, "Failed to call Java method 'updateWindow'");
		env->ExceptionClear ();
	}
	return;
}

// Invoke JAVA method updateStreamStatus to update the StreamState
int wbs_SendVideoPlayingStatusMessage(unsigned int source, eStreamState state)
{
	JNIEnv *env = get_jni_env ();

	jmethodID updateStreamStatus = env->GetMethodID((jclass)wbsStreamIn_javaClass_id, "updateStreamStatus", "(II)V");
	if (updateStreamStatus == NULL) {
		CSIO_LOG(eLogLevel_debug,  "Could not find Java method 'updateStreamStatus'");
		return -1; // TODO: what is error code here
	}

	env->CallVoidMethod(wbs_get_app(), updateStreamStatus, (jint)state, (jint)source);
	if (env->ExceptionCheck ()) {
		CSIO_LOG(eLogLevel_error, "Failed to call Java method 'updateStreamStatus'");
		env->ExceptionClear ();
	}

	return 0;
}

// Invoke JAVA method forceStop to force a stop of WBS stream - normally requested by WBS thread itself
// Not used - expect an explicit STOP message from csio before a START is attempted
int wbs_forceStop(unsigned int source)
{
	JNIEnv *env = get_jni_env ();

	jmethodID forceStop = env->GetMethodID((jclass)wbsStreamIn_javaClass_id, "forceStop", "(I)V");
	if (forceStop == NULL) {
		CSIO_LOG(eLogLevel_debug,  "Could not find Java method 'forceStop'");
		return -1; // TODO: what is error code here
	}

	env->CallVoidMethod(wbs_get_app(), forceStop, (jint)source);
	if (env->ExceptionCheck ()) {
		CSIO_LOG(eLogLevel_error, "Failed to call Java method 'forceStop'");
		env->ExceptionClear ();
	}

	return 0;
}

// Initialize the WBS data structure with the incoming surface
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_WbsStreamIn_nativeSurfaceInit(JNIEnv *env, jobject thiz, jobject surface, jint stream)
{
    ANativeWindow *new_native_window;
	CSIO_LOG(eLogLevel_verbose, "%s", __FUNCTION__);

	Wbs_t *pWbs = wbs_get_stream_data(stream);
    if (!pWbs)
    {
        CSIO_LOG(eLogLevel_error, "Could not access wbs stream data pointer");
        return;
    }

    CSIO_LOG(eLogLevel_debug, "%s: pWbs=%p surface=%p, stream=%d", __FUNCTION__, pWbs, surface, stream);

    if (!surface)
    {
        CSIO_LOG(eLogLevel_error, "No surface for stream %d", stream);
        return;
    }

    new_native_window = ANativeWindow_fromSurface(env, surface);
    if (!new_native_window)
    {
        CSIO_LOG(eLogLevel_error, "No native window for stream %d", stream);
        return;
    }

	CSIO_LOG(eLogLevel_debug, "Received surface %p (native window %p) for stream %d",
			   surface, new_native_window, stream);

    if (pWbs->native_window)
    {
        ANativeWindow_release(pWbs->native_window);
        if (pWbs->native_window == new_native_window)
        {
			CSIO_LOG(eLogLevel_debug, "New native window is the same as the previous one %p", pWbs->native_window);
            return;
        }
        else
        {
            CSIO_LOG(eLogLevel_debug, "Released previous native window %p", pWbs->native_window);
        }
    }
    pWbs->native_window = new_native_window;

    wbs_jni_setup_surface_format(env, new_native_window, pWbs, surface);
}

// Detach the surface/window from the WBS data structure
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_WbsStreamIn_nativeSurfaceFinalize(JNIEnv *env, jobject thiz, jint stream)
{
	CSIO_LOG(eLogLevel_verbose, "%s", __FUNCTION__);

	Wbs_t *pWbs = wbs_get_stream_data(stream);
    if (!pWbs)
    {
        CSIO_LOG(eLogLevel_error, "Could not access wbs stream data pointer");
        return;
    }

	CSIO_LOG(eLogLevel_debug, "Releasing native window %p for stream %d",
			   pWbs->native_window, stream);

	if (pWbs->native_window != NULL)
		ANativeWindow_release (pWbs->native_window); //TODO: java seems to already handle this
	pWbs->native_window = NULL;

	if(pWbs->surface)
	{
	    CSIO_LOG(eLogLevel_debug, "Delete GlobalRef %p", pWbs->surface);
	    env->DeleteGlobalRef((jobject)pWbs->surface);
	    pWbs->surface = NULL;
	}
	//TODO: when this will be called?
}

/* Instruct the native code to create its internal data structure */
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_WbsStreamIn_nativeInit(JNIEnv *env, jobject thiz)
{
	CSIO_LOG(eLogLevel_verbose, "%s", __FUNCTION__);
	jobject app = env->NewGlobalRef (thiz);
	wbs_set_app(app);
}

/* Free resources */
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_WbsStreamIn_nativeFinalize(JNIEnv *env, jobject thiz)
{
	CSIO_LOG(eLogLevel_verbose, "%s", __FUNCTION__);
	if (wbsStreamIn_javaClass_id != NULL) {
		CSIO_LOG(eLogLevel_debug, "Deleting GlobalRef for wbsStreamIn_javaClass_id object at %p", wbsStreamIn_javaClass_id);
		env->DeleteGlobalRef ((jobject)wbsStreamIn_javaClass_id);
	}
	if (wbs_get_app() != NULL)
	{
		CSIO_LOG(eLogLevel_debug, "Deleting GlobalRef for app object at %p", wbs_get_app());
		env->DeleteGlobalRef ((jobject)wbs_get_app());
		wbs_set_app(NULL);
	}
}


/* Transition to PLAYING state */
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_WbsStreamIn_nativePlay(JNIEnv *env, jobject thiz, jint sessionId)
{
	CSIO_LOG(eLogLevel_verbose, "%s", __FUNCTION__);
	wbs_start(sessionId);
}

/* Transition to STOPPED state */
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_WbsStreamIn_nativeStop(JNIEnv *env, jobject thiz, jint sessionId)
{
	CSIO_LOG(eLogLevel_verbose, "%s", __FUNCTION__);
	wbs_stop(sessionId); //TODO may need timeout
}

/* Transition to PAUSED state */
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_WbsStreamIn_nativePause(JNIEnv *env, jobject thiz, jint sessionId)
{
	CSIO_LOG(eLogLevel_verbose, "%s", __FUNCTION__);
	wbs_pause(sessionId);
}

/* Transition to STARTED state */
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_WbsStreamIn_nativeUnpause(JNIEnv *env, jobject thiz, jint sessionId)
{
	CSIO_LOG(eLogLevel_verbose, "%s", __FUNCTION__);
	wbs_unpause(sessionId);
}

/* Set Stream URL */
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_WbsStreamIn_nativeSetUrl(JNIEnv *env, jobject thiz, jstring url_jstring, jint sessionId)
{
	int restartStream = 0;
	const char * url_cstring = env->GetStringUTFChars( url_jstring , NULL ) ;
	if (url_cstring == NULL) return;

	CSIO_LOG(eLogLevel_debug, "Using url: '%s'", url_cstring);
	wbs_setUrl(url_cstring, sessionId);
	//csio_SetURL(sessionId, (char *)url_cstring, strlen(url_cstring) + 1);

	env->ReleaseStringUTFChars(url_jstring, url_cstring);
}

/* Set Stream URL */
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_WbsStreamIn_nativeSetLogLevel(JNIEnv *env, jobject thiz, jint logLevel)
{
	CSIO_LOG(eLogLevel_verbose, "%s", __FUNCTION__);
	wbs_setLogLevel(logLevel);
}
/***************************** end of Kaptivo whiteboard streaming in *********************************/

/***************************** start of Miracast(Wifi Display:wfd) streaming in shares GStreamIn class instance *********************************/
/* Start wfd connection .
 * Note: calling function should call gst_native_surface_init() to setup surface first.
 *
 * */
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeWfdStart(JNIEnv *env, jobject thiz, jint windowId, jlong msMiceSessionId, jstring url_jstring, jint rtsp_port)
{
    const char * url_cstring = env->GetStringUTFChars( url_jstring , NULL ) ;
    if (url_cstring == NULL)
    {
        env->ReleaseStringUTFChars(url_jstring, url_cstring);
        CSIO_LOG(eLogLevel_error, "url_jstring is NULL.");
        return;
    }

    CSIO_LOG(eLogLevel_info, "%s: start TCP connection source windowId[%d] sessionId[%lld] url[%s], port[%d]", __FUNCTION__, windowId, (long long) msMiceSessionId, url_cstring,rtsp_port);

    int retv = sssl_setContextStreamID((unsigned long long)msMiceSessionId, windowId);

    CSIO_LOG(eLogLevel_debug,"mira: {%s} - sssl_setContextStreamID() called with ms mice sessionID = %lld, streamID = %d returned %d",
      __FUNCTION__,msMiceSessionId,windowId,retv);

    Wfd_set_firewall_rules(rtsp_port, -1);

    CREGSTREAM * data = GetStreamFromCustomData(CresDataDB, windowId);
    if (!data)
    {
       env->ReleaseStringUTFChars(url_jstring, url_cstring);
       CSIO_LOG(eLogLevel_error, "Could not obtain stream pointer for stream %d", windowId);
       return;
    }

    data->wfd_jitterbuffer_latency = 50;//set latency to 50ms
    strcpy(data->rtcp_dest_ip_addr, url_cstring);	// Set RTSP IP as RTCP IP

    int ts_port = CSIOCnsIntf->getStreamTxRx_TSPORT(windowId);
    WfdSinkProjStart(windowId,url_cstring,rtsp_port,ts_port);

    CSIO_LOG(eLogLevel_debug,"mira: {%s} - exiting",__FUNCTION__);

    env->ReleaseStringUTFChars(url_jstring, url_cstring);
}

/* Stop/Teardown wfd connection
 *
 * TODO: should calling function call gst_native_surface_finalize() after this?
 * TODO: this function should call csio_jni_stop?
 * */
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeWfdStop(JNIEnv *env, jobject thiz, jint windowId)
{
    // *** CSIO_LOG(eLogLevel_debug,"mira: {%s} - ***** calling sssl_cancelDTLSAppThWithStreamIDAndWait() *****",__FUNCTION__);
    // *** int retv = sssl_cancelDTLSAppThWithStreamIDAndWait(windowId);
    // *** CSIO_LOG(eLogLevel_debug,"mira: {%s} - ===== returned from sssl_cancelDTLSAppThWithStreamIDAndWait(), retv = %d =====",
    // ***     __FUNCTION__,retv);

    //Note: you can call WfdSinkProjStop multiple times.
    WfdSinkProjStop(windowId);

    //TODO: copy from gst_native_stop()
    {
        CREGSTREAM * data = GetStreamFromCustomData(CresDataDB, windowId);

        if (!data)
        {
        	CSIO_LOG(eLogLevel_error, "mira: Could not obtain stream pointer for stream %d, failed to set isStarted state", windowId);
        }
        else
        {
            data->wfd_jitterbuffer_latency = -1;
            data->ssrc = 0;

			data->rtcp_dest_ip_addr[0] = '\0';
			data->rtcp_dest_port = -1;
            CSIO_LOG(eLogLevel_debug, "GstreamIn_nativeWfdStop[%d]: data->isStarted[%d]", windowId,data->isStarted);

            if (data->isStarted)
            {
                data->isStarted = false;
                data->packetizer_pcr_discont_threshold = -1;

                csio_jni_stop((int)windowId);

                Wfd_set_firewall_rules(0, 0);
            }
            else
            {
            	csio_SendVideoPlayingStatusMessage((int)windowId, STREAMSTATE_STOPPED);
            }
        }
    }

    // *** CSIO_LOG(eLogLevel_debug,"mira: {%s} - exiting",__FUNCTION__);
}

// <0 means ignore, 0 means remove rule, >0 means open firewall to that port
static void Wfd_set_firewall_rules (int rtsp_port, int ts_port)
{
    JNIEnv *env = get_jni_env ();

	jmethodID updateStreamStatus = env->GetMethodID((jclass)gStreamIn_javaClass_id, "wfdSetFirewallRules", "(II)V");
	if (updateStreamStatus == NULL) return;

	env->CallVoidMethod(CresDataDB->app, updateStreamStatus, (jint)rtsp_port, (jint)ts_port);
	if (env->ExceptionCheck ()) {
		CSIO_LOG(eLogLevel_error, "Failed to call Java method 'wfdSetFirewallRules'");
		env->ExceptionClear ();
	}

}

//TODO: get surface from app, report status back to app, resolution setting came from app

/* called from state machine for : TCP connected/disconnected
 *
 * */


void Wfd_setup_gst_pipeline (int id, int state, struct GST_PIPELINE_CONFIG* gst_config)
{
    if(state && gst_config)
    {

        CSIO_LOG(eLogLevel_debug, "mira: {%s} - id[%d], ts_port[%d], ssrc[0x%x], rtcp_dest_port[%d]",
                 __FUNCTION__,id,gst_config->ts_port,gst_config->ssrc,gst_config->rtcp_dest_port);

        Wfd_set_firewall_rules(-1, gst_config->ts_port);

        //TODO: remove the following settings if it is done already
        csio_SetPortNumber( id, gst_config->ts_port, c_TSportNumber );

        CSIOCnsIntf->setStreamTxRx_SESSIONINITIATION(id, 1, SENDTOCRESSTORE_NONE);
        csio_SetSessionInitiationMode(id,1);

        CSIOCnsIntf->setStreamTxRx_TRANSPORTMODE(id, 1, SENDTOCRESSTORE_NONE);
        csio_SetTransportMode(id,1);

        //TODO: for testing only
        //gst_native_play(NULL,NULL,id);
        {
            CREGSTREAM * data = GetStreamFromCustomData(CresDataDB, id);

            if (!data)
            {
                CSIO_LOG(eLogLevel_error, "Could not obtain stream pointer for stream %d", id);
                return;
            }

            data->isStarted = true;
            data->packetizer_pcr_discont_threshold = 5;

            data->ssrc = gst_config->ssrc;
            data->rtcp_dest_port = gst_config->rtcp_dest_port;

            if(GetInPausedState(id))
            {
                CSIO_LOG(eLogLevel_debug, "GetInPausedState is true, resume now");
            }

            SetInPausedState(id, 0);
            start_streaming_cmd(id);
        }

        CSIO_LOG(eLogLevel_debug, "%s exit", __FUNCTION__);
    }

    if(state == 0)
    {
        csio_SendVideoPlayingStatusMessage(id, STREAMSTATE_STOPPED);
        CSIO_LOG(eLogLevel_debug, "stream %d set STREAMSTATE_STOPPED", id);
    }

    CSIO_LOG(eLogLevel_debug, "mira: {%s} - exiting",__FUNCTION__);
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeMsMiceStart(JNIEnv *env, jobject thiz)
{
    CSIO_LOG(eLogLevel_debug,"mira: {%s} - ***** calling sssl_initialize() *****",__FUNCTION__);
    sssl_initialize();
    CSIO_LOG(eLogLevel_debug, "mira: {%s} - ===== returned from sssl_initialize() =====",__FUNCTION__);

    msMiceSinkProjInit(NULL);

    //to set ms mice pin after msMiceSinkProjInit
    CREGSTREAM * data = GetStreamFromCustomData(CresDataDB, 0);

    if (!data)
    {
        CSIO_LOG(eLogLevel_error, "mira: Could not obtain stream pointer for stream %d, failed to set isStarted state", 0);
    }
    else
    {
        if(data->ms_mice_pin[0])
        {
            msMiceSinkProjSetPin(0,data->ms_mice_pin);
        }
        else
        {
            msMiceSinkProjSetPin(0,NULL);
        }
    }
}
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeMsMiceSetAdapterAddress(JNIEnv *env, jobject thiz, jstring address)
{
    char * locAddr = NULL;
    if(address != NULL)
    {
        locAddr = (char *)env->GetStringUTFChars(address, NULL);
    }//else

    if(locAddr == NULL)
    {
       CSIO_LOG(eLogLevel_debug, "MsMiceSetAdapterAddress changed to NULL ");
       msMiceSinkProjDeInit();
       msMiceSinkProjInit(NULL);
    }
    else
    {
        CSIO_LOG(eLogLevel_debug, "MsMiceSetAdapterAddress changed to %s ",locAddr);
        msMiceSinkProjDeInit();
        msMiceSinkProjInit(locAddr);

        env->ReleaseStringUTFChars(address, locAddr);
    }

    //to set ms mice pin after msMiceSinkProjInit
    CREGSTREAM * data = GetStreamFromCustomData(CresDataDB, 0);

    if (!data)
    {
        CSIO_LOG(eLogLevel_error, "mira: Could not obtain stream pointer for stream %d, failed to set isStarted state", 0);
    }
    else
    {
        if(data->ms_mice_pin[0])
        {
            msMiceSinkProjSetPin(0,data->ms_mice_pin);
        }
        else
        {
            msMiceSinkProjSetPin(0,NULL);
        }
    }
}
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeMsMiceStop(JNIEnv *env, jobject thiz)
{
    msMiceSinkProjDeInit();

    CSIO_LOG(eLogLevel_debug,"mira: {%s} - ***** calling sssl_deinitialize() *****",__FUNCTION__);
    sssl_deinitialize();
    CSIO_LOG(eLogLevel_debug,"mira: {%s} - ===== returned from sssl_deinitialize() =====",__FUNCTION__);
}
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeMsMiceCloseSession(JNIEnv *env, jobject thiz, jlong msMiceSessionId)
{
    msMiceSinkProjStopSession(0,msMiceSessionId);
}
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeMsMiceSetPin(JNIEnv *env, jobject thiz, jstring pin_jstring)
{
    int id = 0;

    char * locPin = NULL;
    if(pin_jstring != NULL)
    {
        locPin = (char *)env->GetStringUTFChars(pin_jstring, NULL);
    }//else

    CREGSTREAM * data = GetStreamFromCustomData(CresDataDB, 0);

    if (!data)
    {
        CSIO_LOG(eLogLevel_error, "mira: Could not obtain stream pointer for stream %d, failed to set isStarted state", 0);
    }

    if(locPin == NULL)
    {
       CSIO_LOG(eLogLevel_error, "pin is NULL or invalid");
       msMiceSinkProjSetPin(id,NULL);

       if (data)
           data->ms_mice_pin[0] = 0;
    }
    else
    {
        if (data)
        {
            data->ms_mice_pin[0] = 0;
            int pinSize = strlen(locPin);
            if(pinSize < sizeof(data->ms_mice_pin))
            {
                memcpy(data->ms_mice_pin,locPin,pinSize);
                msMiceSinkProjSetPin(id,locPin);
            }
            else
            {
                CSIO_LOG(eLogLevel_info, "pin is too long[%d]",pinSize);
                msMiceSinkProjSetPin(id,NULL);
            }
        }

        env->ReleaseStringUTFChars(pin_jstring, locPin);
    }
}
void csio_SendMsMiceStateChange(gint64 sessionId, int state, char *device_id, char *device_name, char *device_addr, int rtsp_port)
{
    JNIEnv *env = get_jni_env ();
    jstring deviceId;
    jstring deviceName;
    jstring deviceAddress;

    CSIO_LOG(eLogLevel_debug, "csio_SendMsMiceStateChange,sessionId[%lld],state[%d],device_id[%s],device_name[%s],device_addr[%s],rtsp_port[%d]",
            sessionId,state,device_id,device_name,device_addr,rtsp_port);

    jmethodID sendMsMiceStateChange = env->GetMethodID((jclass)gStreamIn_javaClass_id, "sendMsMiceStateChange", "(JILjava/lang/String;Ljava/lang/String;Ljava/lang/String;I)V");
    if (sendMsMiceStateChange == NULL) {
        CSIO_LOG(eLogLevel_error, "Failed to find Java method 'sendMsMiceStateChange'");
        return;
    }

    deviceId = env->NewStringUTF(device_id);
    deviceName = env->NewStringUTF(device_name);
    deviceAddress = env->NewStringUTF(device_addr);

    env->CallVoidMethod(CresDataDB->app, sendMsMiceStateChange, (jlong)sessionId, (jint)state, (jstring) deviceId, (jstring) deviceName, (jstring) deviceAddress, (jint)rtsp_port);
    if (env->ExceptionCheck ()) {
        CSIO_LOG(eLogLevel_error, "Failed to call Java method 'sendMsMiceStateChange'");
            env->ExceptionClear ();
        }

    env->DeleteLocalRef(deviceId);
    env->DeleteLocalRef(deviceName);
    env->DeleteLocalRef(deviceAddress);
}
void Wfd_ms_mice_signal_raise (gint64 session_id, int state, char *device_id, char *device_name, char *device_addr, int rtsp_port)
{
    CSIO_LOG(eLogLevel_debug, "Wfd_ms_mice_signal_raise,session_id[%lld],state[%d],rtsp_port[%d]", session_id,state,rtsp_port);
    if(device_id && device_name && device_addr)
    {
        CSIO_LOG(eLogLevel_debug, "Wfd_ms_mice_signal_raise,device_id[%s],device_name[%s],device_addr[%s]", device_id,device_name,device_addr);
        csio_SendMsMiceStateChange(session_id,state,device_id,device_name,device_addr,rtsp_port);
    }
    else
    {
        csio_SendMsMiceStateChange(session_id,state,"device_id","device_name","device_addr",rtsp_port);
    }
}
/***************************** end of Miracast(Wifi Display:wfd) streaming in *********************************/
