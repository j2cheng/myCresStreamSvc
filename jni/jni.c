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
#include <sys/socket.h>
#include <sys/time.h>
#include "cregstplay.h" 
#include <jni.h>
#include "GstreamIn.h"
#include "GstreamOut.h"
#include "csioCommonShare.h"
#include <gst/video/video.h>
#include <gst/rtsp-server/rtsp-server.h>
#include <gst/rtsp-server/rtsp-media-factory.h>
#include "csio_jni_if.h"
// Android headers
#include "hardware/gralloc.h"           // for GRALLOC_USAGE_PROTECTED
#include "android/native_window.h"      // for ANativeWindow_ functions
///////////////////////////////////////////////////////////////////////////////

extern int  csio_Init(int calledFromCsio);
void csio_jni_stop(int sessionId);
void csio_send_stats_no_bitrate (uint64_t video_packets_received, int video_packets_lost, uint64_t audio_packets_received, int audio_packets_lost);
void LocalConvertToUpper(char *str);
///////////////////////////////////////////////////////////////////////////////

/*
 * These macros provide a way to store the native pointer to CustomData, which might be 32 or 64 bits, into
 * a jlong, which is always 64 bits, without warnings.
 */
#if GLIB_SIZEOF_VOID_P == 8
# define GET_CUSTOM_DATA(env, thiz, fieldID) (CustomData *)(*env)->GetLongField (env, thiz, fieldID)
# define SET_CUSTOM_DATA(env, thiz, fieldID, data) (*env)->SetLongField (env, thiz, fieldID, (jlong)data)
#else
# define GET_CUSTOM_DATA(env, thiz, fieldID) (CustomData *)(jint)(*env)->GetLongField (env, thiz, fieldID)
# define SET_CUSTOM_DATA(env, thiz, fieldID, data) (*env)->SetLongField (env, thiz, fieldID, (jlong)(jint)data)
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

pthread_cond_t stop_completed_sig;
static int stop_timeout_sec = 1000;

const int c_minSocketLeft = 20;
const int c_maxNumInitialPorts	= 100;
static unsigned int *initialPorts = NULL;

static char const* file_prefix = "file://";
static unsigned const prefixLength7 = 7;

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

	if ((*java_vm)->AttachCurrentThread (java_vm, (void**)&env, &args) < 0) {
		CSIO_LOG(eLogLevel_warning, "Failed to attach current thread");
		return NULL;
	}

	return env;
}

/* Unregister this thread from the VM */
static void detach_current_thread (void *env) 
{
	CSIO_LOG(eLogLevel_debug, "Detaching thread %p", g_thread_self ());
	(*java_vm)->DetachCurrentThread (java_vm);
}

/* Retrieve the JNI environment for this thread */
static JNIEnv *get_jni_env (void) 
{
	JNIEnv *env;

	if ((env = pthread_getspecific (current_jni_env)) == NULL) {
		env = attach_current_thread ();
		pthread_setspecific (current_jni_env, env);
	}

	return env;
}

/* Change the content of the UI's TextView */
// void set_ui_message (const gchar *message, CustomData *data) 
// {
// 	JNIEnv *env = get_jni_env ();
// 	CSIO_LOG(eLogLevel_debug, "Setting message to: %s", message);
// 	jstring jmessage = (*env)->NewStringUTF(env, message);
// 	(*env)->CallVoidMethod (env, data->app, set_message_method_id, jmessage);
// 	if ((*env)->ExceptionCheck (env)) {
// 		CSIO_LOG(eLogLevel_error, "Failed to call Java method");
// 		(*env)->ExceptionClear (env);
// 	}
// 	(*env)->DeleteLocalRef (env, jmessage);
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
		//(*env)->CallVoidMethod (env, cdata->app, on_gstreamer_initialized_method_id);
		//if ((*env)->ExceptionCheck (env)) {
		//	CSIO_LOG(eLogLevel_error, "Failed to call Java method");
		//	(*env)->ExceptionClear (env);
		//}
		
		data->initialized = TRUE;
	}
}

void csio_jni_init()
{
	int iStatus = CSIO_SUCCESS;

	csio_setup_product_info(0);
	
    // MNT - 7.5.15 - Indicate that init is being called from CresStreamSvc so that
    // it does not get SIGUSR2.
	iStatus = csio_Init(0);
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
	
	cdata->app = (*env)->NewGlobalRef (env, thiz);
	init_custom_data(cdata);
	csio_jni_init();
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
		
	CSIO_LOG(eLogLevel_debug, "Deleting GlobalRef for app object at %p", cdata->app);
    (*env)->DeleteGlobalRef (env, (jobject)gStreamIn_javaClass_id);
	(*env)->DeleteGlobalRef (env, cdata->app);
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
void gst_native_play (JNIEnv* env, jobject thiz, jint sessionId)
{
    CREGSTREAM * data = GetStreamFromCustomData(CresDataDB, sessionId);

    if (!data)
    {
        CSIO_LOG(eLogLevel_error, "Could not obtain stream pointer for stream %d", sessionId);
        return;
    }

    data->isStarted = true;

    if(GetInPausedState(sessionId))
    {
        CSIO_LOG(eLogLevel_debug, "GetInPausedState is true, resume now");
        if(data->element_valve_a)
            g_object_set(G_OBJECT(data->element_valve_a), "drop", FALSE, NULL);
        if(data->element_valve_v)
            g_object_set(G_OBJECT(data->element_valve_v), "drop", FALSE, NULL);
    }

    SetInPausedState(sessionId, 0);
	currentSettingsDB->settingsMessage.msg[sessionId].src = sessionId;
	start_streaming_cmd(sessionId);
}

/* Set pipeline to PAUSED state */
static void gst_native_pause (JNIEnv* env, jobject thiz, jint sessionId)
{
    CREGSTREAM * data = GetStreamFromCustomData(CresDataDB, sessionId);

    if (!data)
    {
        CSIO_LOG(eLogLevel_error, "Could not obtain stream pointer for stream %d", sessionId);
        return;
    }
    
    if(GetInPausedState(sessionId) == 0)
    {
        CSIO_LOG(eLogLevel_debug, "GetInPausedState is false, drop all");
        if(data->element_valve_a)
            g_object_set(G_OBJECT(data->element_valve_a), "drop", TRUE, NULL);
        if(data->element_valve_v)
            g_object_set(G_OBJECT(data->element_valve_v), "drop", TRUE, NULL);
    }

    SetInPausedState(sessionId, 1);
	pause_streaming_cmd(sessionId);
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
            gst_pad_remove_probe(pad, data->udpsrc_prob_element);
            gst_object_unref(pad);
            data->udpsrc_prob_id = 0;
        }
    }
    memset(data->sourceIP_addr,0,sizeof(data->sourceIP_addr));
    data->udpsrc_prob_timer.tv_sec = 0;
    data->udpsrc_prob_timer.tv_nsec = 0;
    data->udpsrc_prob_element = 0;
    data->udpsrc_prob_id = 0;
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

			ret = readlink(filepath, &tmp, sizeof(tmp));
			if (ret >= 0)
			{
				tmp[ret] = '\0';	// readlink will not terminate buf w/ NULL

				if (strstr(&tmp, "anon_inode:[eventfd]") != NULL)
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
    data->element_fake_dec = NULL;
    data->element_zero     = NULL;
    data->element_valve_v    = NULL;
    data->element_valve_a    = NULL;
    data->element_audiorate  = NULL;
    data->streamProtocolId = 0;

    for (i = 0; i < MAX_ELEMENTS; i++)
    {
        data->element_av[i] = NULL;
        data->element_a[i] = NULL;
        data->element_v[i] = NULL;   
    }
    
    data->using_glimagsink = 0;

    csio_jni_FreeMainContext(iStreamId);

	if (shouldCloseSockets()) //all streams stopped or near max fd limit
	{
		// Close all the AF_UNIX sockets that Android creates when querying DNS
		// On Jellybean, these sockets were accruing with each connection, eventually exceeding the maximum and crashing csio
		// Fixes bug#92551
		// Get the maximum number of file descriptors this process can open
		struct rlimit limit;
		getrlimit(RLIMIT_NOFILE, &limit);

		// Don't both checking fd 0, 1, and 2
		for (index = 3; index < limit.rlim_cur; ++index)
		{
			struct sockaddr addr;
			socklen_t len = sizeof(addr);

			// if ( (this fd is open and is a socket) && (this socket is AF_UNIX) )
			if ( (getsockname(index, &addr, &len) == 0) && (addr.sa_family == AF_UNIX) )
			{
				bool portFound = false;

				for (portIndex =0; portIndex < c_maxNumInitialPorts; portIndex++)
				{
					if (initialPorts[portIndex] == index)
					{
						portFound = true;
						break;
					}
					else if (initialPorts[portIndex] == 0)
					{
						// reached end of initialPorts
						portFound = false;
						break;
					}
				}


				if (!portFound)
				{
					CSIO_LOG(eLogLevel_debug, "Closing socket fd %d\n", index);
					close(index);
				}
			}
		}

		closeEventfdLeak();
	}
}

void * jni_stop (void * arg)
{
	jint sessionId = (*(jint *)arg);
	SetInPausedState(sessionId,0);
	stop_streaming_cmd(sessionId);
	csio_jni_cleanup(sessionId);

    pthread_cond_broadcast(&stop_completed_sig); //Flags that stop has completed
	pthread_exit(NULL);

	return NULL;
}

void csio_jni_stop(int sessionId)
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
	pthread_create(&gst_stop_thread, &attr, jni_stop, &sessionId);

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
		csio_jni_recoverDucati();
	}
	else if (result != 0)
		CSIO_LOG(eLogLevel_error, "Unknown error occurred while waiting for stop to complete, error = %d\n", result);
}

/* Set pipeline to PAUSED state */
void gst_native_stop (JNIEnv* env, jobject thiz, jint sessionId, jint stopTimeout_sec)
{
	CREGSTREAM * data = GetStreamFromCustomData(CresDataDB, sessionId);

	if (data)
		data->isStarted = false;
	else
		CSIO_LOG(eLogLevel_error, "Could not obtain stream pointer for stream %d, failed to set isStarted state", sessionId);

	csio_jni_stop((int)sessionId);
}

/* Static class initializer: retrieve method and field IDs */
static jboolean gst_native_class_init (JNIEnv* env, jclass klass) 
{
	CSIO_LOG(eLogLevel_debug, "gst_native_class_init\n");
	custom_data_field_id = (*env)->GetFieldID (env, klass, "native_custom_data", "J");
	set_message_method_id = (*env)->GetMethodID (env, klass, "setMessage", "(Ljava/lang/String;)V");
	//on_gstreamer_initialized_method_id = (*env)->GetMethodID (env, klass, "onGStreamerInitialized", "()V");

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
        return FALSE;
    }

    if (data->surface)
    {
        CSIO_LOG(eLogLevel_debug, "Delete GlobalRef before adding new: %p", data->surface);
        (*env)->DeleteGlobalRef(env, data->surface);
        data->surface = NULL;
    }
    data->surface = (*env)->NewGlobalRef(env, surface);
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

	jmethodID getCurrentStreamState = (*env)->GetMethodID(env, (jclass)gStreamIn_javaClass_id, "getCurrentStreamState", "(I)I");

	currentStreamState = (eStreamState)(*env)->CallIntMethod(env, CresDataDB->app, getCurrentStreamState, stream);

	if ((*env)->ExceptionCheck (env)) {
		CSIO_LOG(eLogLevel_error, "Failed to call Java method 'getCurrentStreamState'");
		(*env)->ExceptionClear (env);
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
	    (*env)->DeleteGlobalRef(env, data->surface);
	    data->surface = NULL;
	}
	//TODO: when this will be called?
}

/* Set Stream URL */
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetServerUrl(JNIEnv *env, jobject thiz, jstring url_jstring, jint sessionId)
{
	int restartStream = 0;
	const char * url_cstring = (*env)->GetStringUTFChars( env, url_jstring , NULL ) ;
	if (url_cstring == NULL) return;

	CSIO_LOG(eLogLevel_debug, "Using server url: '%s'", url_cstring);
	csio_SetURL(sessionId, (char *)url_cstring, strlen(url_cstring) + 1);

	(*env)->ReleaseStringUTFChars(env, url_jstring, url_cstring);
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetRtspPort(JNIEnv *env, jobject thiz, jint port, jint sessionId)
{
	CSIO_LOG(eLogLevel_debug, "Using RtspPort: '%d'", port);
	currentSettingsDB->videoSettings[sessionId].rtsp_port = port;
	CSIO_LOG(eLogLevel_debug, "RtspPort in currentSettingsDB: '%d'", currentSettingsDB->videoSettings[sessionId].rtsp_port);
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetTsPort(JNIEnv *env, jobject thiz, jint port, jint sessionId)
{
	CSIO_LOG(eLogLevel_debug, "Using tsPort: '%d'", port);
	//currentSettingsDB->videoSettings[sessionId].tsPort = port;
	csio_SetPortNumber( sessionId, port, c_TSportNumber );
	CSIO_LOG(eLogLevel_debug, "tsPort in currentSettingsDB: '%ld'", currentSettingsDB->videoSettings[sessionId].tsPort);
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetHdcpEncrypt(JNIEnv *env, jobject thiz, jboolean flag, jint sessionId)
{
    CREGSTREAM * data = GetStreamFromCustomData(CresDataDB, sessionId);

    if(!data)
    {
        CSIO_LOG(eLogLevel_error, "Could not obtain stream pointer for stream %d", sessionId);
        return;
    }

    data->doHdcp = (gboolean)flag;
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetRtpVideoPort(JNIEnv *env, jobject thiz, jint port, jint sessionId)
{
	CSIO_LOG(eLogLevel_debug, "Using rtpVideoPort: '%d'", port);
	//currentSettingsDB->videoSettings[sessionId].rtpVideoPort = port;
	csio_SetPortNumber( sessionId, port, c_RTPVideoPortNumber );
	CSIO_LOG(eLogLevel_debug, "rtpVideoPort in currentSettingsDB: '%ld'", currentSettingsDB->videoSettings[sessionId].rtpVideoPort);
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetRtpAudioPort(JNIEnv *env, jobject thiz, jint port, jint sessionId)
{
	CSIO_LOG(eLogLevel_debug, "Using rtpAudioPort: '%d'", port);
	//currentSettingsDB->videoSettings[sessionId].rtpAudioPort = port;
	csio_SetPortNumber( sessionId, port, c_RTPAudioPortNumber );
	CSIO_LOG(eLogLevel_debug, "rtpAudioPort in currentSettingsDB: '%ld'", currentSettingsDB->videoSettings[sessionId].rtpAudioPort);
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetSessionInitiation(JNIEnv *env, jobject thiz, jint initMode, jint sessionId)
{
	CSIO_LOG(eLogLevel_debug, "Using sessionInitiationMode: '%d'", initMode);
	currentSettingsDB->videoSettings[sessionId].sessionInitiationMode = initMode;
	csio_SetSessionInitiationMode(sessionId,initMode);
	CSIO_LOG(eLogLevel_debug, "sessionInitiationMode in currentSettingsDB: '%d'", currentSettingsDB->videoSettings[sessionId].sessionInitiationMode);
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetTransportMode(JNIEnv *env, jobject thiz, jint transportMode, jint sessionId)
{
	CSIO_LOG(eLogLevel_debug, "Using tsEnabled: '%d'", transportMode);
	currentSettingsDB->videoSettings[sessionId].tsEnabled = transportMode;
	csio_SetTransportMode(sessionId,transportMode);
	CSIO_LOG(eLogLevel_debug, "tsEnabled in currentSettingsDB: '%d'", currentSettingsDB->videoSettings[sessionId].tsEnabled);
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetMulticastAddress(JNIEnv *env, jobject thiz, jstring multicastIp_jstring, jint sessionId)
{
	const char * multicastIp_cstring = (*env)->GetStringUTFChars( env, multicastIp_jstring , NULL ) ;
	if (multicastIp_cstring == NULL) return;

	CSIO_LOG(eLogLevel_debug, "Using multicastAddress: '%s'", multicastIp_cstring);
	strcpy((char*)currentSettingsDB->videoSettings[sessionId].multicastAddress, multicastIp_cstring);
	CSIO_LOG(eLogLevel_debug, "multicastAddress in currentSettingsDB: '%s'", currentSettingsDB->videoSettings[sessionId].multicastAddress);

	(*env)->ReleaseStringUTFChars(env, multicastIp_jstring, multicastIp_cstring);
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetStreamingBuffer(JNIEnv *env, jobject thiz, jint buffer_ms, jint sessionId)
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

	currentSettingsDB->videoSettings[sessionId].streamingBuffer = buffer_ms;
	CSIO_LOG(eLogLevel_debug, "streamingBuffer in currentSettingsDB: '%d'", currentSettingsDB->videoSettings[sessionId].streamingBuffer);
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetXYlocations(JNIEnv *env, jobject thiz, jint xLocation, jint yLocation, jint sessionId)
{
	currentSettingsDB->settingsMessage.msg[sessionId].left = xLocation;
	currentSettingsDB->settingsMessage.msg[sessionId].top = yLocation;
	CSIO_LOG(eLogLevel_debug, "xLocation in currentSettingsDB: '%d'", currentSettingsDB->settingsMessage.msg[sessionId].left);
	CSIO_LOG(eLogLevel_debug, "yLocation in currentSettingsDB: '%d'", currentSettingsDB->settingsMessage.msg[sessionId].top);
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetStatistics(JNIEnv *env, jobject thiz, jboolean enabled, jint sessionId)
{
	currentSettingsDB->videoSettings[sessionId].statisticsEnabled = (UINT8)enabled;

	if (nativeGetCurrentStreamState(sessionId) == STREAMSTATE_STARTED)
	{
		if (enabled)
			start_sending_stream_statistics(sessionId);
		//we will keep thread running all the time.
	}

	//CSIO_LOG(eLogLevel_debug, "statisticsEnabled in currentSettingsDB: '%d'", currentSettingsDB->videoSettings[sessionId].statisticsEnabled);
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeResetStatistics(JNIEnv *env, jobject thiz, jint sessionId)
{
	csio_send_stats_no_bitrate(0, 0, 0, 0); //omit bitrate so that it won't be sent to control system

	reset_statistics(sessionId);
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetUserName(JNIEnv *env, jobject thiz, jstring userName_jstring, jint sessionId)
{
	const char * userName_cstring = (*env)->GetStringUTFChars( env, userName_jstring , NULL ) ;
	if (userName_cstring == NULL) return;

	CSIO_LOG(eLogLevel_debug, "Using UserName: '%s'", userName_cstring);
	strcpy(currentSettingsDB->videoSettings[sessionId].username, userName_cstring);
	CSIO_LOG(eLogLevel_debug, "UserName in currentSettingsDB: '%s'", currentSettingsDB->videoSettings[sessionId].username);

	(*env)->ReleaseStringUTFChars(env, userName_jstring, userName_cstring);
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetPassword(JNIEnv *env, jobject thiz, jstring password_jstring, jint sessionId)
{
	const char * password_cstring = (*env)->GetStringUTFChars( env, password_jstring , NULL ) ;
	if (password_cstring == NULL) return;

	CSIO_LOG(eLogLevel_debug, "Using password: '%s'", password_cstring);
	strcpy(currentSettingsDB->videoSettings[sessionId].password, password_cstring);
	CSIO_LOG(eLogLevel_debug, "Password in currentSettingsDB: '%s'", currentSettingsDB->videoSettings[sessionId].password);

	(*env)->ReleaseStringUTFChars(env, password_jstring, password_cstring);
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
	currentSettingsDB->videoSettings[sessionId].videoSinkSelect = (UINT8)enabled;
	CSIO_LOG(eLogLevel_debug, "new Sink Enabled in currentSettingsDB: %d", currentSettingsDB->videoSettings[sessionId].videoSinkSelect);
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
    currentSettingsDB->videoSettings[sessionId].rtspTCPMode = tcpMode;
    CSIO_LOG(eLogLevel_debug, "Setting window{%d] tcpMode to %d", sessionId, tcpMode);

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
    char namestring[100];

    const char * cmd_cstring = (*env)->GetStringUTFChars( env, cmd_jstring , NULL ) ;
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
        CmdPtr = strtok(cmd_cstring,", ");
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
                csio_jni_printFieldDebugInfo(CmdPtr);
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
                            gst_debug_set_threshold_for_name(namestring,fieldNum);
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
                        gst_debug_set_threshold_for_name("amcvideodec",fieldNum);
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
                        gst_debug_set_threshold_for_name("openslessink",fieldNum);
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

                                    data->amcviddec_ts_offset -= currentSettingsDB->videoSettings[sessionId].streamingBuffer;
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
            else
            {
                CSIO_LOG(eLogLevel_info, "Invalid command:%s\r\n",CmdPtr);
            }
        }
    }

    (*env)->ReleaseStringUTFChars(env, cmd_jstring, cmd_cstring);
}
eStreamState nativeGetCurrentStreamState(jint sessionId)
{
	eStreamState currentStreamState;
	JNIEnv *env = get_jni_env ();

	jmethodID getCurrentStreamState = (*env)->GetMethodID(env, (jclass)gStreamIn_javaClass_id, "getCurrentStreamState", "(I)I");

	currentStreamState = (eStreamState)(*env)->CallIntMethod(env, CresDataDB->app, getCurrentStreamState, sessionId);

	if ((*env)->ExceptionCheck (env)) {
		CSIO_LOG(eLogLevel_error, "Failed to call Java method 'getCurrentStreamState'");
		(*env)->ExceptionClear (env);
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
    currentSettingsDB = (CSIOSettings*)malloc(sizeof(CSIOSettings));
	java_vm = vm;

	currentSettingsDB->csioLogLevel = CSIO_DEFAULT_LOG_LEVEL;

	CSIO_LOG(eLogLevel_debug, "JNI_OnLoad ");

	set_gst_debug_level();
	
	if ((*vm)->GetEnv(vm, (void**) &env, JNI_VERSION_1_4) != JNI_OK) 
	{
		CSIO_LOG(eLogLevel_error, "gstreamer_jni", "Could not retrieve JNIEnv");
		return 0;
	}
	//jclass klass = (*env)->FindClass (env, "com/gst_sdk_tutorials/tutorial_3/Tutorial3");
	jclass klass = (*env)->FindClass (env, "com/crestron/txrxservice/GstreamIn");
	/* Create a global reference to GstreamIn class Id, because it gets lost when pthread is created */
	gStreamIn_javaClass_id = (jclass*)(*env)->NewGlobalRef(env, klass);
	(*env)->DeleteLocalRef(env, klass);
	if (gStreamIn_javaClass_id == NULL) {
		CSIO_LOG(eLogLevel_error, "gstreamer_jni", "gStreamIn_javaClass_id is still null when it is suppose to be global");
	     return 0; /* out of memory exception thrown */
	 }

	(*env)->RegisterNatives (env, (jclass)gStreamIn_javaClass_id, native_methods, G_N_ELEMENTS(native_methods));

	// Crestron - PEM - register jni for GstreamOut
	CSIO_LOG(eLogLevel_error, "gstreamer_jni : Registering natives for GstreamOut");
	jclass klass2 = (*env)->FindClass (env, "com/crestron/txrxservice/GstreamOut");
	gStreamOut_javaClass_id = (jclass*)(*env)->NewGlobalRef(env, klass2);
	(*env)->DeleteLocalRef(env, klass2);
	if (gStreamOut_javaClass_id == NULL) {
		CSIO_LOG(eLogLevel_error, "gstreamer_jni", "gStreamOut_javaClass_id is still null when it is suppose to be global");
	     return 0; /* out of memory exception thrown */
	}
	(*env)->RegisterNatives (env, (jclass)gStreamOut_javaClass_id, native_methods_rtsp_server, G_N_ELEMENTS(native_methods_rtsp_server));
	
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

	multicastAddress_jstr = (*env)->NewStringUTF(env, multicastAddress_cstr);

	jmethodID sendMulticastAddress = (*env)->GetMethodID(env, (jclass)gStreamIn_javaClass_id, "sendMulticastAddress", "(Ljava/lang/String;I)V");
	if (sendMulticastAddress == NULL) return NULL;

	(*env)->CallVoidMethod(env, CresDataDB->app, sendMulticastAddress, multicastAddress_jstr, streamId);
	if ((*env)->ExceptionCheck (env)) {
		CSIO_LOG(eLogLevel_error, "Failed to call Java method 'sendMulticastAddress'");
		(*env)->ExceptionClear (env);
	}
	(*env)->DeleteLocalRef (env, multicastAddress_jstr);

	return(NULL);
}
void csio_send_stats (uint64_t video_packets_received, int video_packets_lost, uint64_t audio_packets_received, int audio_packets_lost, uint16_t bitrate)
{
	JNIEnv *env = get_jni_env ();

	jmethodID sendStatistics = (*env)->GetMethodID(env, (jclass)gStreamIn_javaClass_id, "sendStatistics", "(JIJII)V");
	if (sendStatistics == NULL) return;

	(*env)->CallVoidMethod(env, CresDataDB->app, sendStatistics, (jlong)video_packets_received, (jint)video_packets_lost, (jlong)audio_packets_received, (jint)audio_packets_lost, (jint)bitrate);
	if ((*env)->ExceptionCheck (env)) {
		CSIO_LOG(eLogLevel_error, "Failed to call Java method 'sendStatistics'");
		(*env)->ExceptionClear (env);
	}
}

void csio_send_stats_no_bitrate (uint64_t video_packets_received, int video_packets_lost, uint64_t audio_packets_received, int audio_packets_lost)
{
	int bitrate = -1; //bitrate was omitted so send -1 which will flag txrxservice to ignore
	JNIEnv *env = get_jni_env ();

	jmethodID sendStatistics = (*env)->GetMethodID(env, (jclass)gStreamIn_javaClass_id, "sendStatistics", "(JIJII)V");
	if (sendStatistics == NULL) return;

	(*env)->CallVoidMethod(env, CresDataDB->app, sendStatistics, (jlong)video_packets_received, (jint)video_packets_lost, (jlong)audio_packets_received, (jint)audio_packets_lost, (jint)bitrate);
	if ((*env)->ExceptionCheck (env)) {
		CSIO_LOG(eLogLevel_error, "Failed to call Java method 'sendStatistics'");
		(*env)->ExceptionClear (env);
	}
}

void csio_get_width_and_height_from_mode (uint32_t * width, uint32_t * height)
{
	//TODO: this method needs to be updated with source as a input parameter currently defaulting to 0
	unsigned int source = 0;
	JNIEnv *env = get_jni_env ();

	jmethodID getCurrentWidthHeight = (*env)->GetMethodID(env, (jclass)gStreamIn_javaClass_id, "getCurrentWidthHeight", "(I)[I");
	if (getCurrentWidthHeight == NULL) return;

	jintArray retval = (jintArray) (*env)->CallObjectMethod(env, CresDataDB->app, getCurrentWidthHeight, (jint)source);
	if ((*env)->ExceptionCheck (env)) {
		CSIO_LOG(eLogLevel_error, "Failed to call Java method 'getCurrentWidthHeight'");
		(*env)->ExceptionClear (env);
	}
	jint *widthHeight = (*env)->GetIntArrayElements(env, retval, NULL);

	(*width) = widthHeight[0];
	(*height) = widthHeight[1];
	return;
}

int csio_SendVideoPlayingStatusMessage(unsigned int source, eStreamState state)
{
	JNIEnv *env = get_jni_env ();

	jmethodID updateStreamStatus = (*env)->GetMethodID(env, (jclass)gStreamIn_javaClass_id, "updateStreamStatus", "(II)V");
	if (updateStreamStatus == NULL) return -1; // TODO: what is error code here

	(*env)->CallVoidMethod(env, CresDataDB->app, updateStreamStatus, (jint)state, (jint)source);
	if ((*env)->ExceptionCheck (env)) {
		CSIO_LOG(eLogLevel_error, "Failed to call Java method 'updateStreamStatus'");
		(*env)->ExceptionClear (env);
	}

	// Reset source parameters when stopped - Bug 88712
	if (state == STREAMSTATE_STOPPED)
	{
		csio_SendVideoSourceParams(source,0,0,0,0);
	}
	
	return 0;
}
int csio_SendVideoSourceParams(unsigned int source, unsigned int width, unsigned int height, unsigned int framerate, unsigned int profile)
{
	JNIEnv *env = get_jni_env ();	

	jmethodID sendVideoSourceParams = (*env)->GetMethodID(env, (jclass)gStreamIn_javaClass_id, "sendVideoSourceParams", "(IIIII)V");
	if (sendVideoSourceParams == NULL) return -1; // TODO: what is error code here

	(*env)->CallVoidMethod(env, CresDataDB->app, sendVideoSourceParams, (jint)source, (jint)width, (jint)height, (jint)framerate, (jint)profile);
	if ((*env)->ExceptionCheck (env)) {
		CSIO_LOG(eLogLevel_error, "Failed to call Java method 'sendVideoSourceParams'");
		(*env)->ExceptionClear (env);
	}

	return 0;
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
}

int csio_jni_CreatePipeline(GstElement **pipeline, GstElement **source, eProtocolId protoId, int iStreamId)
{
	int iStatus = CSIO_SUCCESS;
    CSIO_LOG(eLogLevel_debug, "%s() protoId = %d", __FUNCTION__, protoId);
	CREGSTREAM * data = GetStreamFromCustomData(CresDataDB, iStreamId);

	if(!data)
	{
		CSIO_LOG(eLogLevel_error, "Could not obtain stream pointer for stream %d", iStreamId);
		return CSIO_FAILURE;
	}

	csio_jni_CreateMainContext(iStreamId);      //@todo: duplicated, rewrite this func, and delete above several lines

	data->pipeline = gst_pipeline_new(NULL);
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

	    	gst_bin_add_many(GST_BIN(data->pipeline), data->element_zero, NULL);
			if( !data->pipeline || !data->element_zero )
			{
				CSIO_LOG(eLogLevel_error, "ERROR: Cannot create source pipeline elements\n");
				return CSIO_CANNOT_CREATE_ELEMENTS;
			}
	
			*pipeline = data->pipeline;
			*source   = data->element_zero;
			break;
	    }
	    case ePROTOCOL_HTTP:
	    {
	    	build_http_pipeline(data, iStreamId);

			*pipeline = data->pipeline;
			*source   = data->element_zero;
			//CSIO_LOG(eLogLevel_debug, "called build_http_pipeline [0x%x][0x%x]",CresDataDB->pipeline,CresDataDB->element_zero);
			break;
	    }
	    case ePROTOCOL_ADAPTIVE_STREAMING:
            {
                build_hls_pipeline(data, iStreamId);
                *pipeline = data->pipeline;
                *source   = data->element_zero;
                break;
            }
	    case ePROTOCOL_UDP_TS:
	    {
		    if(currentSettingsDB->videoSettings[iStreamId].tsEnabled==STREAM_TRANSPORT_MPEG2TS_RTP)
		    {
		        data->element_zero = gst_element_factory_make("rtpbin", NULL);
			    gst_bin_add(GST_BIN(data->pipeline), data->element_zero);

			    data->udp_port = currentSettingsDB->videoSettings[iStreamId].tsPort;
			    data->element_av[0] = gst_element_factory_make("udpsrc", NULL);
			    insert_udpsrc_probe(data,data->element_av[0],"src");

			    g_object_set(G_OBJECT(data->element_av[0]), "caps", data->caps_v_ts, NULL);
			    g_object_set(G_OBJECT(data->element_av[0]), "port", data->udp_port, NULL);
			    gst_bin_add(GST_BIN(data->pipeline), data->element_av[0]);
			    int ret = gst_element_link(data->element_av[0], data->element_zero);
			    if(ret==0)
			    {
			        CSIO_LOG(eLogLevel_error,  "ERROR:  Cannot link filter to source elements.\n" );
				    iStatus = CSIO_CANNOT_LINK_ELEMENTS;
			    }
			    else
			        CSIO_LOG(eLogLevel_debug,  "link filter to source elements.\n" );

		    }
		    else if(currentSettingsDB->videoSettings[iStreamId].tsEnabled==STREAM_TRANSPORT_MPEG2TS_UDP)
		    {
		        data->element_zero = gst_element_factory_make("udpsrc", NULL);
			    if(!data->element_zero)
			    {
				    iStatus = CSIO_CANNOT_CREATE_ELEMENTS;
				    CSIO_LOG(eLogLevel_error,  "ERROR: Cannot create udp source pipeline elements\n" );
			    }
			    else
			        insert_udpsrc_probe(data,data->element_zero,"src");

			    data->udp_port = currentSettingsDB->videoSettings[iStreamId].tsPort;
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
			data->udp_video_port = currentSettingsDB->videoSettings[iStreamId].rtpVideoPort;
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

                data->udp_audio_port = currentSettingsDB->videoSettings[iStreamId].rtpAudioPort;
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
	    	strcpy(data->multicast_grp, (char*)currentSettingsDB->videoSettings[iStreamId].multicastAddress);
		    if(currentSettingsDB->videoSettings[iStreamId].tsEnabled==STREAM_TRANSPORT_MPEG2TS_RTP)
		    {
				CSIO_LOG(eLogLevel_debug,  "in STREAM_TRANSPORT_MPEG2TS_RTP\n" );

				data->element_zero = gst_element_factory_make("rtpbin", NULL);
				gst_bin_add(GST_BIN(data->pipeline), data->element_zero);

				data->udp_port = currentSettingsDB->videoSettings[iStreamId].tsPort;
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
		    else if(currentSettingsDB->videoSettings[iStreamId].tsEnabled==STREAM_TRANSPORT_MPEG2TS_UDP)
		    {
				CSIO_LOG(eLogLevel_debug,  "in STREAM_TRANSPORT_MPEG2TS_UDP\n" );

			    data->element_zero = gst_element_factory_make("udpsrc", NULL);
			    if(!data->element_zero)
			    {
				    iStatus = CSIO_CANNOT_CREATE_ELEMENTS;
				    CSIO_LOG(eLogLevel_error,  "ERROR: Cannot create udp source pipeline elements\n" );
			    }
			    insert_udpsrc_probe(data,data->element_zero,"src");
			    			    
			    data->udp_port = currentSettingsDB->videoSettings[iStreamId].tsPort;
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
	    	strcpy(data->multicast_grp,(char*)currentSettingsDB->videoSettings[iStreamId].multicastAddress);

	    	//build_udp_pipeline(data,protoId);
	    	data->element_zero = gst_element_factory_make("rtpbin", NULL);
			gst_bin_add(GST_BIN(data->pipeline), data->element_zero);

			//video
			data->udp_video_port = currentSettingsDB->videoSettings[iStreamId].rtpVideoPort;
			data->element_av[0] = gst_element_factory_make("udpsrc", NULL);
            insert_udpsrc_probe(data,data->element_av[0],"src"); 

			g_object_set(G_OBJECT(data->element_av[0]), "port", data->udp_video_port, NULL);
			g_object_set(G_OBJECT(data->element_av[0]), "caps", data->caps_v_rtp, NULL);
			gst_bin_add(GST_BIN(data->pipeline), data->element_av[0]);
			gst_element_link(data->element_av[0], data->element_zero);

			//audio
			data->element_av[1] = gst_element_factory_make("udpsrc", NULL);
			data->udp_audio_port = currentSettingsDB->videoSettings[iStreamId].rtpAudioPort;
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

            char *location = currentSettingsDB->settingsMessage.msg[iStreamId].url;
            if ( strcasestr(location, file_prefix) )
            {
                location += prefixLength7;
            } else {
                CSIO_LOG(eLogLevel_error, "ERROR: %s invalid SDP url: %s\n", __FUNCTION__, location);
            }
            CSIO_LOG(eLogLevel_debug, "%s() location=%s\n", __FUNCTION__, location);

            g_object_set(G_OBJECT(data->element_zero), "location", location, NULL);

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
			g_object_set(G_OBJECT(data->element_zero), "location", currentSettingsDB->settingsMessage.msg[iStreamId].url, NULL);
			g_object_set(G_OBJECT(data->element_zero), "latency", currentSettingsDB->videoSettings[iStreamId].streamingBuffer, NULL);
			g_object_set(G_OBJECT(data->element_zero), "tcp_timeout", data->tcp_timeout_usec, NULL);
			g_object_set(G_OBJECT(data->element_zero), "timeout", data->udp_timeout_usec, NULL);
			// For some reason, this port range must be set for rtsp with multicast to work.
			//g_object_set(G_OBJECT(data->element_zero), "port-range", "5001-65535", NULL);
			data->protocols = tcpModeFlags;
			g_object_set(G_OBJECT(data->element_zero), "protocols", data->protocols, NULL);
			g_object_set(G_OBJECT(data->element_zero), "udp-buffer-size", DEFAULT_UDP_BUFFER, NULL);
			
			g_object_set(G_OBJECT(data->element_zero), "user-agent", (gchar *)CRESTRON_USER_AGENT, NULL); // TESTING REMOVE

			// video part
			data->video_sink = NULL;
			break;
		}

		case ePROTOCOL_HTTP:
			//CSIO_LOG(eLogLevel_debug, "ePROTOCOL_HTTP pass\n");
			break;

		case ePROTOCOL_ADAPTIVE_STREAMING:
			//CSIO_LOG(eLogLevel_debug, "ePROTOCOL_ADAPTIVE_STREAMING pass\n");
		    g_object_set(G_OBJECT(data->element_v[1]), "connection-speed", 15000, NULL);
			break;

		case ePROTOCOL_UDP_TS:
			if (currentSettingsDB->videoSettings[iStreamId].tsEnabled==STREAM_TRANSPORT_MPEG2TS_RTP)
				g_object_set(G_OBJECT(data->element_av[0]), "buffer-size", DEFAULT_UDP_BUFFER, NULL);
			else if(currentSettingsDB->videoSettings[iStreamId].tsEnabled==STREAM_TRANSPORT_MPEG2TS_UDP)
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
		case ePROTOCOL_MULTICAST_TS:
			if (currentSettingsDB->videoSettings[iStreamId].tsEnabled==STREAM_TRANSPORT_MPEG2TS_RTP)
				g_object_set(G_OBJECT(data->element_av[0]), "buffer-size", DEFAULT_UDP_BUFFER, NULL);
			else if(currentSettingsDB->videoSettings[iStreamId].tsEnabled==STREAM_TRANSPORT_MPEG2TS_UDP)
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
			g_object_set(G_OBJECT(data->element_av[0]), "latency", currentSettingsDB->videoSettings[iStreamId].streamingBuffer, NULL);
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
		{
			g_object_set(G_OBJECT(data->element_zero), "location", \
					location, NULL);
			break;
		}

		case ePROTOCOL_HTTP:
			break;

		case ePROTOCOL_ADAPTIVE_STREAMING:
			//CSIO_LOG(eLogLevel_debug, "csio_jni_SetSourceLocation: ePROTOCOL_HTTP pass\n");
		    url = (char *)currentSettingsDB->settingsMessage.msg[iStreamId].url;
		    CSIO_LOG(eLogLevel_debug, "using url %s", url);
		    g_object_set(G_OBJECT(data->element_zero), "location", url,  NULL);
			break;

		case ePROTOCOL_UDP_TS:
		{
			//CSIO_LOG(eLogLevel_debug, "ePROTOCOL_UDP_TS pass\n");
			break;
		}

		case ePROTOCOL_UDP:
		{
			//CSIO_LOG(eLogLevel_debug, "ePROTOCOL_UDP pass\n");
			break;
		}
		case ePROTOCOL_MULTICAST_TS:
		case ePROTOCOL_MULTICAST:
		{
			//CSIO_LOG(eLogLevel_debug, "ePROTOCOL_MULTICAST: location[%s]\n",CresDataDB->multicast_grp);
			if(currentSettingsDB->videoSettings[iStreamId].tsEnabled==STREAM_TRANSPORT_MPEG2TS_UDP){
				g_object_set(G_OBJECT(data->element_zero), "address", \
						location, NULL);
			}
			else { //RTP Only and MPEG2TS RTP 
				g_object_set(G_OBJECT(data->element_av[0]), "address", \
						location, NULL);
			}
			//g_object_set(G_OBJECT(CresDataDB->element_av[1]), "address", 
			//		CresDataDB->multicast_grp, NULL);

			break;
		}
		case ePROTOCOL_FILE:
		{
			//g_object_set(G_OBJECT(data->element_zero), "location", location, NULL);
			//break;
		}
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
		    		if(currentSettingsDB->videoSettings[iStreamId].tsEnabled==STREAM_TRANSPORT_MPEG2TS_RTP){
				g_signal_connect(data->element_zero, "pad-added", G_CALLBACK(csio_PadAddedMsgHandler), obj);
		    		}else if(currentSettingsDB->videoSettings[iStreamId].tsEnabled==STREAM_TRANSPORT_MPEG2TS_UDP){
				g_signal_connect(data->element_av[1], "pad-added", G_CALLBACK(csio_PadAddedMsgHandler), obj);
				}
			}
			else
			{
				CSIO_LOG(eLogLevel_warning, "Null element zero, no callbacks will be registered");
			}
			break;
		}
		case ePROTOCOL_HTTP:
			break;

		case ePROTOCOL_ADAPTIVE_STREAMING:
		    g_signal_connect(data->element_v[1], "pad-added", G_CALLBACK(csio_pad_added_callback_hls), (void *) data->element_v[2]);
			g_signal_connect(data->element_v[2], "pad-added", G_CALLBACK(csio_PadAddedMsgHandler), obj);
			break;

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

		    break;
		default:
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

	gchar * p_caps_string;
	GstCaps *new_pad_caps = gst_pad_query_caps( new_pad, NULL );
	p_caps_string = gst_caps_to_string (new_pad_caps);
	if(strncmp(p_caps_string, "application/x-rtp", 17) == 0)
	{
		do_rtp = 1;
	}

	iStatus = build_audio_pipeline(encoding_name, data, do_rtp,&ele0,sink);
	if(ele0 == NULL)
	{
		iStatus  = CSIO_CANNOT_CREATE_ELEMENTS;
		goto doneAddAudio ;
	}

	// calling initAudio on return

	// Get the pad given an element.
	GstPad *sink_pad = NULL;
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

	gchar * p_caps_string;
	GstCaps *new_pad_caps = gst_pad_query_caps( new_pad, NULL );
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
	GstPad *sink_pad = NULL;
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
	    if( data->amcvid_dec &&
	    		(!debug_blocking_audio) &&
				data->audio_sink &&
				( currentSettingsDB->videoSettings[iStreamId].sessionInitiationMode == 0 ||
						currentSettingsDB->videoSettings[iStreamId].sessionInitiationMode == 2  )
						&& (data->streamProtocolId != ePROTOCOL_HTTP) && (data->streamProtocolId != ePROTOCOL_ADAPTIVE_STREAMING) )
	    {
	        int tmp = currentSettingsDB->videoSettings[iStreamId].streamingBuffer +
	                  data->amcviddec_ts_offset;
	        g_object_set(G_OBJECT(data->amcvid_dec), "ts-offset", tmp, NULL);
	        CSIO_LOG(eLogLevel_debug, "streamingBuffer or latency is:%d",currentSettingsDB->videoSettings[iStreamId].streamingBuffer);
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

	initiatorAddress_jstr = (*env)->NewStringUTF(env, initiatorAddress_cstr);

	jmethodID sendInitiatorAddress = (*env)->GetMethodID(env, (jclass)gStreamIn_javaClass_id, "sendInitiatorFbAddress", "(Ljava/lang/String;I)V");
	if (sendInitiatorAddress == NULL) return NULL;

	(*env)->CallVoidMethod(env, CresDataDB->app, sendInitiatorAddress, initiatorAddress_jstr, 0);
	if ((*env)->ExceptionCheck (env)) {
		CSIO_LOG(eLogLevel_error, "Failed to call Java method 'sendInitiatorAddress'");
		(*env)->ExceptionClear (env);
	}
	(*env)->DeleteLocalRef (env, initiatorAddress_jstr);

	pthread_exit( NULL );
	return(NULL);
}
void csio_jni_recoverDucati()
{
    JNIEnv *env = get_jni_env ();
    jmethodID recoverDucati = (*env)->GetMethodID(env, (jclass)gStreamIn_javaClass_id, "recoverDucati", "()V");
    if (recoverDucati == NULL) return;

    (*env)->CallVoidMethod(env, CresDataDB->app, recoverDucati);
    if ((*env)->ExceptionCheck (env)) {
        CSIO_LOG(eLogLevel_error, "Failed to call Java method 'recoverDucati'");
        (*env)->ExceptionClear (env);
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
    custom_data_field_id_rtsp_server = (*env)->GetFieldID (env, klass, "native_custom_data", "J");
    set_message_method_id_rtsp_server = (*env)->GetMethodID (env, klass, "setMessage", "(Ljava/lang/String;)V");
    //on_gstreamer_initialized_method_id = (*env)->GetMethodID (env, klass, "onGStreamerInitialized", "()V");

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
    
    cdata->app = (*env)->NewGlobalRef(env, thiz);
    init_custom_data_out(cdata);

    //init project
    StreamoutProjectInit();

    CSIO_LOG(eLogLevel_debug, "rtsp_server: gstNativeInitRtspServer exit.");
}

static void gstNativeFinalizeRtspServer (JNIEnv* env, jobject thiz)
{
    CustomStreamOutData *cdata = GET_CUSTOM_DATA (env, thiz, custom_data_field_id_rtsp_server);
    int i;

    if (!cdata) return;

    //turn off project and all stream out first
    StreamoutProjectDeInit();

    CSIO_LOG(eLogLevel_debug, "rtsp_server: Deleting GlobalRef for app object at %p", cdata->app);
    (*env)->DeleteGlobalRef (env, (jobject)gStreamOut_javaClass_id);
    (*env)->DeleteGlobalRef (env, cdata->app);
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
    CSIO_LOG(eLogLevel_debug, "rtsp_server: Using RtspPort: '%d'", port);
    char* desBuf = CresStreamOutDataDB->streamOut[0].rtsp_port;
    sprintf(desBuf, "%d", port);

    Streamout_SetPort(desBuf);
    CSIO_LOG(eLogLevel_debug, "rtsp_server: RtspPort in CresStreamOutDataDB: '%s'", CresStreamOutDataDB->streamOut[0].rtsp_port);
}
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamOut_nativeSet_1Res_1x(JNIEnv *env, jobject thiz, jint Res_x, jint sessionId)
{
    CSIO_LOG(eLogLevel_debug, "rtsp_server: Using Res_x: '%d'", Res_x);
    char* desBuf = CresStreamOutDataDB->streamOut[0].res_x;
    sprintf(desBuf, "%d", Res_x);

    Streamout_SetRes_x(desBuf);
    CSIO_LOG(eLogLevel_debug, "rtsp_server: res_x in CresStreamOutDataDB: '%s'", CresStreamOutDataDB->streamOut[0].res_x);
}
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamOut_nativeSet_1Res_1y(JNIEnv *env, jobject thiz, jint Res_y, jint sessionId)
{
    CSIO_LOG(eLogLevel_debug, "rtsp_server: Using Res_y: '%d'", Res_y);
    char* desBuf = CresStreamOutDataDB->streamOut[0].res_y;
    sprintf(desBuf, "%d", Res_y);

    Streamout_SetRes_y(desBuf);
    CSIO_LOG(eLogLevel_debug, "rtsp_server: res_y in CresStreamOutDataDB: '%s'", CresStreamOutDataDB->streamOut[0].res_y);
}
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamOut_nativeSet_1FrameRate(JNIEnv *env, jobject thiz, jint FrameRate, jint sessionId)
{
    CSIO_LOG(eLogLevel_debug, "rtsp_server: Using FrameRate: '%d'", FrameRate);
    char* desBuf = CresStreamOutDataDB->streamOut[0].frame_rate;
    sprintf(desBuf, "%d", FrameRate);

    Streamout_SetFrameRate(desBuf);
    CSIO_LOG(eLogLevel_debug, "rtsp_server: frame_rate in CresStreamOutDataDB: '%s'", CresStreamOutDataDB->streamOut[0].frame_rate);
}
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamOut_nativeSet_1Bitrate(JNIEnv *env, jobject thiz, jint Bitrate, jint sessionId)
{
    CSIO_LOG(eLogLevel_debug, "rtsp_server: Using Bitrate: '%d'", Bitrate);
    char* desBuf = CresStreamOutDataDB->streamOut[sessionId].bitrate;
    sprintf(desBuf, "%d", Bitrate);

    Streamout_SetBitrate(desBuf);
    CSIO_LOG(eLogLevel_debug, "rtsp_server: bitrate in CresStreamOutDataDB: '%s'", CresStreamOutDataDB->streamOut[0].bitrate);
}
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamOut_nativeSet_1IFrameInterval(JNIEnv *env, jobject thiz, jint IFrameInterval, jint sessionId)
{
    CSIO_LOG(eLogLevel_debug, "rtsp_server: Using iFrameInterval: '%d'", IFrameInterval);
    char* desBuf = CresStreamOutDataDB->streamOut[sessionId].iframe_interval;
    sprintf(desBuf, "%d", IFrameInterval);

    Streamout_SetIFrameInterval(desBuf);
    CSIO_LOG(eLogLevel_debug, "rtsp_server: IFrameInterval in CresStreamOutDataDB: '%s'", CresStreamOutDataDB->streamOut[0].iframe_interval);
}
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamOut_nativeSet_1MulticastEnable(JNIEnv *env, jobject thiz, jboolean enable, jint sessionId)
{
    CSIO_LOG(eLogLevel_debug, "rtsp_server: Using multicast enable: '%d'", enable);
    CresStreamOutDataDB->streamOut[sessionId].multicast_enable = enable;

    Streamout_EnableMulticast((int) CresStreamOutDataDB->streamOut[sessionId].multicast_enable);

    CSIO_LOG(eLogLevel_debug, "rtsp_server: multicast_enable in CresStreamOutDataDB: '%d'", CresStreamOutDataDB->streamOut[sessionId].multicast_enable);
}
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamOut_nativeSet_1MulticastAddress(JNIEnv *env, jobject thiz, jstring address_jstring, jint sessionId)
{
	const char * address_cstring = (*env)->GetStringUTFChars( env, address_jstring , NULL ) ;
	if (address_cstring == NULL) return;

	CSIO_LOG(eLogLevel_debug, "rtsp_server: Using multicast address: '%s'", address_cstring);
	char* desBuf = CresStreamOutDataDB->streamOut[sessionId].multicast_address;
	strcpy(desBuf, address_cstring);

	Streamout_SetMulticastAddress(desBuf);
	CSIO_LOG(eLogLevel_debug, "rtsp_server: multicast_address in CresStreamOutDataDB: '%s'", CresStreamOutDataDB->streamOut[sessionId].multicast_address);
	(*env)->ReleaseStringUTFChars(env, address_jstring, address_cstring);
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamOut_nativeSet_1StreamName(JNIEnv *env, jobject thiz, jstring name_jstring, jint sessionId)
{
	const char * name_cstring = (*env)->GetStringUTFChars( env, name_jstring , NULL ) ;
	if (name_cstring == NULL) return;

	CSIO_LOG(eLogLevel_debug, "rtsp_server: Using stream name: '%s'", name_cstring);
	char* desBuf = CresStreamOutDataDB->streamOut[sessionId].stream_name;
	strcpy(desBuf, name_cstring);

	Streamout_SetStreamName(desBuf);
	CSIO_LOG(eLogLevel_debug, "rtsp_server: stream_name in CresStreamOutDataDB: '%s'", CresStreamOutDataDB->streamOut[sessionId].stream_name);
	(*env)->ReleaseStringUTFChars(env, name_jstring, name_cstring);
}

/***************************** end of rtsp_server for video streaming out *********************************/
