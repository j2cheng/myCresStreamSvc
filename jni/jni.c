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
#include "cregstplay.h" 
#include <jni.h>
#include "GstreamIn.h"

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


///////////////////////////////////////////////////////////////////////////////
CustomData *CresDataDB = NULL; //

/* These global variables cache values which are not changing during execution */
static pthread_t gst_app_thread;
static pthread_key_t current_jni_env;
static JavaVM *java_vm;
static jfieldID custom_data_field_id;
static jmethodID set_message_method_id;
static jmethodID on_gstreamer_initialized_method_id;
static jclass *gStreamIn_javaClass_id;
int g_using_glimagsink = 0;
///////////////////////////////////////////////////////////////////////////////

/*
 * Private methods
 */

/* Register this thread with the VM */
static JNIEnv *attach_current_thread (void) 
{
	JNIEnv *env;
	JavaVMAttachArgs args;

	GST_DEBUG ("Attaching thread %p", g_thread_self ());
	args.version = JNI_VERSION_1_4;
	args.name = NULL;
	args.group = NULL;

	if ((*java_vm)->AttachCurrentThread (java_vm, &env, &args) < 0) {
		GST_ERROR ("Failed to attach current thread");
		return NULL;
	}

	return env;
}

/* Unregister this thread from the VM */
static void detach_current_thread (void *env) 
{
	GST_DEBUG ("Detaching thread %p", g_thread_self ());
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
void set_ui_message (const gchar *message, CustomData *data) 
{
	JNIEnv *env = get_jni_env ();
	GST_DEBUG ("Setting message to: %s", message);
	jstring jmessage = (*env)->NewStringUTF(env, message);
	(*env)->CallVoidMethod (env, data->app, set_message_method_id, jmessage);
	if ((*env)->ExceptionCheck (env)) {
		GST_ERROR ("Failed to call Java method");
		(*env)->ExceptionClear (env);
	}
	(*env)->DeleteLocalRef (env, jmessage);
}

/* Check if all conditions are met to report GStreamer as initialized.
 * These conditions will change depending on the application */
void check_initialization_complete (CustomData *data) 
{
	JNIEnv *env = get_jni_env ();
	if (!data->initialized && data->native_window && data->main_loop) 
	{
		GST_DEBUG ("Initialization complete, notifying application. native_window:%p main_loop:%p video_sink:%p", 
				   data->native_window, data->main_loop, data->video_sink);

		/* The main loop is running and we received a native window, inform the sink about it */
		if(data->video_sink)
		{
			gst_video_overlay_set_window_handle (GST_VIDEO_OVERLAY (data->video_sink), (guintptr)data->native_window);
		}

		(*env)->CallVoidMethod (env, data->app, on_gstreamer_initialized_method_id);
		if ((*env)->ExceptionCheck (env)) {
			GST_ERROR ("Failed to call Java method");
			(*env)->ExceptionClear (env);
		}
		data->initialized = TRUE;
	}
	//TODO: what we do with CallVoidMethod ?? need data->app here??

}

void csio_jni_init()
{
	int iStatus = CSIO_SUCCESS;

	/* Get the product name */
	CRESTRON_PRODUCT_TYPE product_type = GetProductType();
	GST_DEBUG("Product type: %X", (unsigned)product_type);

	/*
	 * Iterate through the hard-coded list looking for the product name,
	 * defaulting on a product name of "" (touchpanel)
	 */
	for
	(
	  prod_info = Product_Information_Table;
	  (prod_info->product_type != (CRESTRON_PRODUCT_TYPE)0) && (prod_info->product_type != product_type);
	  ++prod_info
	) ; // Intentionally empty loop


    // MNT - 7.5.15 - Indicate that init is being called from CresStreamSvc so that
    // it does not get SIGUSR2.
	iStatus = csio_Init(0);
	if(iStatus != CSIO_SUCCESS)
	{
		GST_DEBUG("csio_Init returned error %d\n", iStatus);
	}

	GST_DEBUG("Done with init\n");
}

/*
 * Java Bindings
 */

/* Instruct the native code to create its internal data structure, pipeline and thread */
static void gst_native_init (JNIEnv* env, jobject thiz) 
{
	CustomData *data = g_new0 (CustomData, 1);
	CresDataDB = data;
	SET_CUSTOM_DATA (env, thiz, custom_data_field_id, data);
	GST_DEBUG_CATEGORY_INIT (debug_category, "gstreamer_jni", 0, "Android jni");
	gst_debug_set_threshold_for_name("gstreamer_jni", GST_LEVEL_DEBUG);
	
	data->app = (*env)->NewGlobalRef (env, thiz);
	
	init_custom_data(data);
	pthread_mutex_init(&(data->ready_to_start_playing_lock), NULL);
	pthread_cond_init(&(data->ready_to_start_playing_signal), NULL);

	//call csio init here.
	csio_jni_init();
}

/* Quit the main loop, remove the native thread and free resources */
static void gst_native_finalize (JNIEnv* env, jobject thiz) 
{
	CustomData *data = GET_CUSTOM_DATA (env, thiz, custom_data_field_id);
	if (!data) return;

	GST_DEBUG ("Deleting GlobalRef for app object at %p", data->app);
    (*env)->DeleteGlobalRef (env, gStreamIn_javaClass_id);
	(*env)->DeleteGlobalRef (env, data->app);
	GST_DEBUG ("Freeing CustomData at %p", data);
	g_free (data);
	SET_CUSTOM_DATA (env, thiz, custom_data_field_id, NULL);
	GST_DEBUG ("Done finalizing");

	pthread_mutex_destroy(&(data->ready_to_start_playing_lock));
	pthread_cond_destroy(&(data->ready_to_start_playing_signal));
}

/* Set pipeline to PLAYING state */
void gst_native_play (JNIEnv* env, jobject thiz, jint sessionId)
{	    
    InPausedState = 0;
	currentSettingsDB.settingsMessage.msg[sessionId].src = sessionId;
	start_streaming_cmd(sessionId);
}

/* Set pipeline to PAUSED state */
static void gst_native_pause (JNIEnv* env, jobject thiz, jint sessionId)
{
    InPausedState = 1;
	pause_streaming_cmd(sessionId);
}

/* Set pipeline to PAUSED state */
void gst_native_stop (JNIEnv* env, jobject thiz, jint sessionId)
{    
    InPausedState = 0;
    stop_streaming_cmd(sessionId);
    csio_jni_cleanup();
}

/* Static class initializer: retrieve method and field IDs */
static jboolean gst_native_class_init (JNIEnv* env, jclass klass) 
{
	GST_DEBUG("gst_native_class_init\n");
	custom_data_field_id = (*env)->GetFieldID (env, klass, "native_custom_data", "J");
	set_message_method_id = (*env)->GetMethodID (env, klass, "setMessage", "(Ljava/lang/String;)V");
	on_gstreamer_initialized_method_id = (*env)->GetMethodID (env, klass, "onGStreamerInitialized", "()V");

	if (!custom_data_field_id || !set_message_method_id || !on_gstreamer_initialized_method_id) {
		/* We emit this message through the Android log instead of the GStreamer log because the later
		* has not been initialized yet.
		*/
		__android_log_print (ANDROID_LOG_ERROR, "gstreamer_jni", "The calling class does not implement all necessary interface methods");
		return JNI_FALSE;
	}
	return JNI_TRUE;
}

static void gst_native_surface_init (JNIEnv *env, jobject thiz, jobject surface) 
{
	CustomData *data = GET_CUSTOM_DATA (env, thiz, custom_data_field_id);
	if (!data) return;
	ANativeWindow *new_native_window = ANativeWindow_fromSurface(env, surface);
	GST_DEBUG ("Received surface %p (native window %p) video_sink=%p", 
			   surface, new_native_window, data->video_sink);

	if (data->native_window) 
	{
		ANativeWindow_release (data->native_window);
		if (data->native_window == new_native_window) 
		{
			GST_DEBUG ("New native window is the same as the previous one %p", data->native_window);
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
			GST_DEBUG ("Released previous native window %p", data->native_window);
			data->initialized = FALSE;
		}
	}
	data->native_window = new_native_window;
	check_initialization_complete (data);

	//TODO: when this will be called?
}

static void gst_native_surface_finalize (JNIEnv *env, jobject thiz) 
{
	CustomData *data = GET_CUSTOM_DATA (env, thiz, custom_data_field_id);
	if (!data) return;
	GST_DEBUG ("Releasing Native Window %p", data->native_window);

	if (data->video_sink) 
	{
		gst_video_overlay_set_window_handle (GST_VIDEO_OVERLAY (data->video_sink), (guintptr)NULL);
		//gst_element_set_state (data->pipeline, GST_STATE_READY);
	}

	if (data->native_window != NULL)
		ANativeWindow_release (data->native_window); //TODO: java seems to already handle this
	data->native_window = NULL;
	data->initialized = FALSE;

	//TODO: when this will be called?
}

/* Set Stream URL */
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetSeverUrl(JNIEnv *env, jobject thiz, jstring url_jstring, jint sessionId)
{
	int restartStream = 0;
	const char * url_cstring = (*env)->GetStringUTFChars( env, url_jstring , NULL ) ;
	if (url_cstring == NULL) return;


	if ((strcasecmp(url_cstring, currentSettingsDB.settingsMessage.msg[sessionId].url) != 0))
	{
		restartStream = 1;

		GST_DEBUG ("Using URL: '%s'", url_cstring);
		strcpy(currentSettingsDB.settingsMessage.msg[sessionId].url, url_cstring);
		(*env)->ReleaseStringUTFChars(env, url_jstring, url_cstring);
	}

	if ((restartStream) && (GetStartedPlay() == 1) && (InPausedState == 0))
	{	    
		jmethodID onStop = (*env)->GetMethodID(env, gStreamIn_javaClass_id, "onStop", "(I)V");
		if (onStop == NULL) return;

		(*env)->CallVoidMethod(env, CresDataDB->app, onStop, sessionId);
		if ((*env)->ExceptionCheck (env)) {
			GST_ERROR ("Failed to call Java method 'onStop'");
			(*env)->ExceptionClear (env);
		}

		jmethodID onStart = (*env)->GetMethodID(env, gStreamIn_javaClass_id, "onStart", "(I)V");
		if (onStart == NULL) return;

		(*env)->CallVoidMethod(env, CresDataDB->app, onStart, sessionId);
		if ((*env)->ExceptionCheck (env)) {
			GST_ERROR ("Failed to call Java method 'onStart'");
			(*env)->ExceptionClear (env);
		}
	}
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetRtspPort(JNIEnv *env, jobject thiz, jint port, jint sessionId)
{
	GST_DEBUG ("Using RtspPort: '%d'", port);
	currentSettingsDB.videoSettings[sessionId].rtsp_port = port;
	GST_DEBUG ("RtspPort in currentSettingsDB: '%d'", currentSettingsDB.videoSettings[sessionId].rtsp_port);
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetTsPort(JNIEnv *env, jobject thiz, jint port, jint sessionId)
{
	GST_DEBUG ("Using tsPort: '%d'", port);
	//currentSettingsDB.videoSettings[sessionId].tsPort = port;
	csio_SetPortNumber( sessionId, port, c_TSportNumber );
	GST_DEBUG ("tsPort in currentSettingsDB: '%d'", currentSettingsDB.videoSettings[sessionId].tsPort);
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetRtpVideoPort(JNIEnv *env, jobject thiz, jint port, jint sessionId)
{
	GST_DEBUG ("Using rtpVideoPort: '%d'", port);
	//currentSettingsDB.videoSettings[sessionId].rtpVideoPort = port;
	csio_SetPortNumber( sessionId, port, c_RTPVideoPortNumber );
	GST_DEBUG ("rtpVideoPort in currentSettingsDB: '%d'", currentSettingsDB.videoSettings[sessionId].rtpVideoPort);
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetRtpAudioPort(JNIEnv *env, jobject thiz, jint port, jint sessionId)
{
	GST_DEBUG ("Using rtpAudioPort: '%d'", port);
	//currentSettingsDB.videoSettings[sessionId].rtpAudioPort = port;
	csio_SetPortNumber( sessionId, port, c_RTPAudioPortNumber );
	GST_DEBUG ("rtpAudioPort in currentSettingsDB: '%d'", currentSettingsDB.videoSettings[sessionId].rtpAudioPort);
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetSessionInitiation(JNIEnv *env, jobject thiz, jint initMode, jint sessionId)
{
	GST_DEBUG ("Using sessionInitiationMode: '%d'", initMode);
	//currentSettingsDB.videoSettings[sessionId].sessionInitiationMode = initMode;
	csio_SetSessionInitiationMode(sessionId,initMode);
	GST_DEBUG ("sessionInitiationMode in currentSettingsDB: '%d'", currentSettingsDB.videoSettings[sessionId].sessionInitiationMode);
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetTransportMode(JNIEnv *env, jobject thiz, jint transportMode, jint sessionId)
{
	GST_DEBUG ("Using tsEnabled: '%d'", transportMode);
	currentSettingsDB.videoSettings[sessionId].tsEnabled = transportMode;
	csio_SetTransportMode(sessionId,transportMode);
	GST_DEBUG ("tsEnabled in currentSettingsDB: '%d'", currentSettingsDB.videoSettings[sessionId].tsEnabled);
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetMulticastAddress(JNIEnv *env, jobject thiz, jstring multicastIp_jstring, jint sessionId)
{
	const char * multicastIp_cstring = (*env)->GetStringUTFChars( env, multicastIp_jstring , NULL ) ;
	if (multicastIp_cstring == NULL) return;

	GST_DEBUG ("Using multicastAddress: '%s'", multicastIp_cstring);
	strcpy(currentSettingsDB.videoSettings[sessionId].multicastAddress, multicastIp_cstring);
	GST_DEBUG ("multicastAddress in currentSettingsDB: '%s'", currentSettingsDB.videoSettings[sessionId].multicastAddress);

	(*env)->ReleaseStringUTFChars(env, multicastIp_jstring, multicastIp_cstring);
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetStreamingBuffer(JNIEnv *env, jobject thiz, jint buffer_ms, jint sessionId)
{
	GST_DEBUG ("Using streamingBuffer: '%d'", buffer_ms);
	currentSettingsDB.videoSettings[sessionId].streamingBuffer = buffer_ms;
	GST_DEBUG ("streamingBuffer in currentSettingsDB: '%d'", currentSettingsDB.videoSettings[sessionId].streamingBuffer);
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetXYlocations(JNIEnv *env, jobject thiz, jint xLocation, jint yLocation, jint sessionId)
{
	currentSettingsDB.settingsMessage.msg[sessionId].left = xLocation;
	currentSettingsDB.settingsMessage.msg[sessionId].top = yLocation;
	GST_DEBUG ("xLocation in currentSettingsDB: '%d'", currentSettingsDB.settingsMessage.msg[sessionId].left);
	GST_DEBUG ("yLocation in currentSettingsDB: '%d'", currentSettingsDB.settingsMessage.msg[sessionId].top);
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetStatistics(JNIEnv *env, jobject thiz, jboolean enabled, jint sessionId)
{
	currentSettingsDB.videoSettings[sessionId].statisticsEnabled = (UINT8)enabled;

	if (nativeGetCurrentStreamState(sessionId) == STREAMSTATE_STARTED)
	{
		if (enabled)
			start_sending_stream_statistics(sessionId);
		else
			stop_sending_stream_statistics(sessionId);
	}

	//GST_DEBUG ("statisticsEnabled in currentSettingsDB: '%d'", currentSettingsDB.videoSettings[sessionId].statisticsEnabled);
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeResetStatistics(JNIEnv *env, jobject thiz, jint sessionId)
{
	reset_statistics(sessionId);
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetUserName(JNIEnv *env, jobject thiz, jstring userName_jstring, jint sessionId)
{
	const char * userName_cstring = (*env)->GetStringUTFChars( env, userName_jstring , NULL ) ;
	if (userName_cstring == NULL) return;

	GST_DEBUG ("Using UserName: '%s'", userName_cstring);
	strcpy(currentSettingsDB.videoSettings[sessionId].username, userName_cstring);
	GST_DEBUG ("UserName in currentSettingsDB: '%s'", currentSettingsDB.videoSettings[sessionId].username);

	(*env)->ReleaseStringUTFChars(env, userName_jstring, userName_cstring);
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetPassword(JNIEnv *env, jobject thiz, jstring password_jstring, jint sessionId)
{
	const char * password_cstring = (*env)->GetStringUTFChars( env, password_jstring , NULL ) ;
	if (password_cstring == NULL) return;

	GST_DEBUG ("Using password: '%s'", password_cstring);
	strcpy(currentSettingsDB.videoSettings[sessionId].password, password_cstring);
	GST_DEBUG ("Password in currentSettingsDB: '%s'", currentSettingsDB.videoSettings[sessionId].password);

	(*env)->ReleaseStringUTFChars(env, password_jstring, password_cstring);
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetVolume(JNIEnv *env, jobject thiz, jint volume, jint sessionId)
{
	double convertedVolume = (double)volume / 100;	//convert from 0-100 to 0.0-1.0
	currentSettingsDB.videoSettings[sessionId].volumeIndB = convertedVolume;
	int ret = csio_SetLinearVolume(sessionId, convertedVolume);
	GST_DEBUG ("Return from csio_SetLinearVolume = %d", ret);
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetNewSink(JNIEnv *env, jobject thiz, jboolean enabled, jint sessionId)
{
	currentSettingsDB.videoSettings[sessionId].videoSinkSelect = (UINT8)enabled;
	GST_DEBUG ("new Sink Enabled in currentSettingsDB: %d", currentSettingsDB.videoSettings[sessionId].videoSinkSelect);
}

StreamState nativeGetCurrentStreamState(jint sessionId)
{
	StreamState currentStreamState;
	JNIEnv *env = get_jni_env ();

	jmethodID getCurrentStreamState = (*env)->GetMethodID(env, gStreamIn_javaClass_id, "getCurrentStreamState", "(I)I");

	currentStreamState = (StreamState)(*env)->CallIntMethod(env, CresDataDB->app, getCurrentStreamState, sessionId);

	if ((*env)->ExceptionCheck (env)) {
		GST_ERROR ("Failed to call Java method 'getCurrentStreamState'");
		(*env)->ExceptionClear (env);
	}

	GST_DEBUG("currentStreamState = %d", (jint)currentStreamState);

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
	{ "nativeSurfaceInit", "(Ljava/lang/Object;)V", (void *) gst_native_surface_init},
	{ "nativeSurfaceFinalize", "()V", (void *) gst_native_surface_finalize},
	{ "nativeClassInit", "()Z", (void *) gst_native_class_init},
};

/* Library initializer */
jint JNI_OnLoad(JavaVM *vm, void *reserved) 
{
	JNIEnv *env = NULL;

	java_vm = vm;
	GST_DEBUG ("JNI_OnLoad ");

	set_gst_debug_level();

	if ((*vm)->GetEnv(vm, (void**) &env, JNI_VERSION_1_4) != JNI_OK) 
	{
		__android_log_print (ANDROID_LOG_ERROR, "gstreamer_jni", "Could not retrieve JNIEnv");
		return 0;
	}
	//jclass klass = (*env)->FindClass (env, "com/gst_sdk_tutorials/tutorial_3/Tutorial3");
	jclass klass = (*env)->FindClass (env, "com/crestron/txrxservice/GstreamIn");
	/* Create a global reference to GstreamIn class Id, because it gets lost when pthread is created */
	gStreamIn_javaClass_id = (jclass*)(*env)->NewGlobalRef(env, klass);
	(*env)->DeleteLocalRef(env, klass);
	if (gStreamIn_javaClass_id == NULL) {
		__android_log_print(ANDROID_LOG_ERROR, "gstreamer_jni", "gStreamIn_javaClass_id is still null when it is suppose to be global");
	     return 0; /* out of memory exception thrown */
	 }

	(*env)->RegisterNatives (env, gStreamIn_javaClass_id, native_methods, G_N_ELEMENTS(native_methods));

	pthread_key_create (&current_jni_env, detach_current_thread);

	return JNI_VERSION_1_4;
}

#include "cregstplay.c"


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

	jmethodID sendMulticastAddress = (*env)->GetMethodID(env, gStreamIn_javaClass_id, "sendMulticastAddress", "(Ljava/lang/String;I)V");
	if (sendMulticastAddress == NULL) return NULL;

	(*env)->CallVoidMethod(env, CresDataDB->app, sendMulticastAddress, multicastAddress_jstr, streamId);
	if ((*env)->ExceptionCheck (env)) {
		GST_ERROR ("Failed to call Java method 'sendMulticastAddress'");
		(*env)->ExceptionClear (env);
	}
	(*env)->DeleteLocalRef (env, multicastAddress_jstr);

	return(NULL);
}
void csio_send_stats (uint64_t video_packets_received, int video_packets_lost, uint64_t audio_packets_received, int audio_packets_lost, uint16_t bitrate)
{
	JNIEnv *env = get_jni_env ();

	jmethodID sendStatistics = (*env)->GetMethodID(env, gStreamIn_javaClass_id, "sendStatistics", "(JIJII)V");
	if (sendStatistics == NULL) return;

	(*env)->CallVoidMethod(env, CresDataDB->app, sendStatistics, (jlong)video_packets_received, (jint)video_packets_lost, (jlong)audio_packets_received, (jint)audio_packets_lost, (jint)bitrate);
	if ((*env)->ExceptionCheck (env)) {
		GST_ERROR ("Failed to call Java method 'sendStatistics'");
		(*env)->ExceptionClear (env);
	}
}

void csio_get_width_and_height_from_mode (uint32_t * width, uint32_t * height)
{
	//TODO: this method needs to be updated with source as a input parameter currently defaulting to 0
	unsigned int source = 0;
	JNIEnv *env = get_jni_env ();

	jmethodID getCurrentWidthHeight = (*env)->GetMethodID(env, gStreamIn_javaClass_id, "getCurrentWidthHeight", "(I)[I");
	if (getCurrentWidthHeight == NULL) return;

	jintArray retval = (jintArray) (*env)->CallObjectMethod(env, CresDataDB->app, getCurrentWidthHeight, (jint)source);
	if ((*env)->ExceptionCheck (env)) {
		GST_ERROR ("Failed to call Java method 'getCurrentWidthHeight'");
		(*env)->ExceptionClear (env);
	}
	jint *widthHeight = (*env)->GetIntArrayElements(env, retval, NULL);

	(*width) = widthHeight[0];
	(*height) = widthHeight[1];
	return;
}

int csio_SendVideoPlayingStatusMessage(unsigned int source, StreamState state)
{
	JNIEnv *env = get_jni_env ();

	jmethodID updateStreamStatus = (*env)->GetMethodID(env, gStreamIn_javaClass_id, "updateStreamStatus", "(II)V");
	if (updateStreamStatus == NULL) return -1; // TODO: what is error code here

	(*env)->CallVoidMethod(env, CresDataDB->app, updateStreamStatus, (jint)state, (jint)source);
	if ((*env)->ExceptionCheck (env)) {
		GST_ERROR ("Failed to call Java method 'updateStreamStatus'");
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

	jmethodID sendVideoSourceParams = (*env)->GetMethodID(env, gStreamIn_javaClass_id, "sendVideoSourceParams", "(IIIII)V");
	if (sendVideoSourceParams == NULL) return -1; // TODO: what is error code here

	(*env)->CallVoidMethod(env, CresDataDB->app, sendVideoSourceParams, (jint)source, (jint)width, (jint)height, (jint)framerate, (jint)profile);
	if ((*env)->ExceptionCheck (env)) {
		GST_ERROR ("Failed to call Java method 'sendVideoSourceParams'");
		(*env)->ExceptionClear (env);
	}

	return 0;
}
void csio_signal_that_stream_has_stopped()
{
	//Intentionally left blank
}

void csio_start_mode_change_detection ()
{
	return;
}

GMainLoop * csio_jni_CreateMainLoop()
{
	return g_main_loop_new( CresDataDB->context, FALSE );
}

void csio_jni_CreateMainContext()
{
	CresDataDB->context = g_main_context_new ();
	g_main_context_push_thread_default(CresDataDB->context);
}

void csio_jni_FreeMainContext()
{	
	g_main_context_pop_thread_default(CresDataDB->context);
	g_main_context_unref (CresDataDB->context);
}

void csio_jni_CheckInitializationComplete()
{
	check_initialization_complete(CresDataDB);
}

void csio_jni_SetOverlayWindow()
{
	/* The main loop is running and we received a native window, inform the sink about it */
	if(CresDataDB->video_sink)
	{
		gst_video_overlay_set_window_handle (GST_VIDEO_OVERLAY (CresDataDB->video_sink), (guintptr)CresDataDB->native_window);
	}
}

int csio_jni_CreatePipeline(GstElement **pipeline,GstElement **source,eProtocolId protoId, int iStreamId)
{	
	int iStatus = CSIO_SUCCESS;

	CresDataDB->pipeline = gst_pipeline_new(NULL);

	//GST_ERROR ("protoId = %d\n",protoId);

	switch( protoId )
	{
		case ePROTOCOL_RTSP_TCP:
		case ePROTOCOL_RTSP_UDP:
	    case ePROTOCOL_DEFAULT_RTSP_TCP:
	    case ePROTOCOL_RTSP_UDP_TS:
	    case ePROTOCOL_RTSP_TS:
	    {
	    	CresDataDB->element_zero = gst_element_factory_make("rtspsrc", NULL);

	    	gst_bin_add_many(GST_BIN(CresDataDB->pipeline), CresDataDB->element_zero, NULL);
			if( !CresDataDB->pipeline || !CresDataDB->element_zero )
			{
				GST_ERROR ("ERROR: Cannot create source pipeline elements\n");
				return CSIO_CANNOT_CREATE_ELEMENTS;
			}
	
			*pipeline = CresDataDB->pipeline;
			*source   = CresDataDB->element_zero;
			break;
	    }
	    case ePROTOCOL_HTTP:
	    {
	    	build_http_pipeline(CresDataDB, iStreamId);

			*pipeline = CresDataDB->pipeline;
			*source   = CresDataDB->element_zero;
			//GST_ERROR ("called build_http_pipeline [0x%x][0x%x]",CresDataDB->pipeline,CresDataDB->element_zero);
			break;
	    }
	    case ePROTOCOL_UDP_TS:
	    {
		    if(currentSettingsDB.videoSettings[iStreamId].tsEnabled==STREAM_TRANSPORT_MPEG2TS_RTP){
			    CresDataDB->element_zero = gst_element_factory_make("rtpbin", NULL);
			    gst_bin_add(GST_BIN(CresDataDB->pipeline), CresDataDB->element_zero);

			    CresDataDB->udp_port = currentSettingsDB.videoSettings[iStreamId].tsPort;
			    CresDataDB->element_av[0] = gst_element_factory_make("udpsrc", NULL);
			    g_object_set(G_OBJECT(CresDataDB->element_av[0]), "caps", CresDataDB->caps_v_ts, NULL);
			    g_object_set(G_OBJECT(CresDataDB->element_av[0]), "port", CresDataDB->udp_port, NULL);
			    gst_bin_add(GST_BIN(CresDataDB->pipeline), CresDataDB->element_av[0]);
			    int ret = gst_element_link(CresDataDB->element_av[0], CresDataDB->element_zero);
			    if(ret==0){
				    g_print( "ERROR:  Cannot link filter to source elements.\n" );
				    iStatus = CSIO_CANNOT_LINK_ELEMENTS;
			    }else
				    g_print( "link filter to source elements.\n" );

		    }else if(currentSettingsDB.videoSettings[iStreamId].tsEnabled==STREAM_TRANSPORT_MPEG2TS_UDP){
			    CresDataDB->element_zero = gst_element_factory_make("udpsrc", NULL);
			    if(!CresDataDB->element_zero)
			    {
				    iStatus = CSIO_CANNOT_CREATE_ELEMENTS;
				    g_print( "ERROR: Cannot create udp source pipeline elements\n" );
			    }

			    CresDataDB->udp_port = currentSettingsDB.videoSettings[iStreamId].tsPort;
			    g_object_set(G_OBJECT(CresDataDB->element_zero), "port", CresDataDB->udp_port, NULL);

			    CresDataDB->element_av[0] = gst_element_factory_make( "queue", NULL );
			    if(!CresDataDB->element_av[0])
			    {
				    iStatus = CSIO_CANNOT_CREATE_ELEMENTS;
				    g_print( "ERROR: Cannot create queue source pipeline elements\n" );
			    }
			    
			    CresDataDB->element_av[1] = gst_element_factory_make( "tsdemux", NULL );
			    if(!CresDataDB->element_av[1])
			    {
				    iStatus = CSIO_CANNOT_CREATE_ELEMENTS;
				    g_print( "ERROR: Cannot create tsdemux source pipeline elements\n" );
			    }

			    gst_bin_add_many(GST_BIN(CresDataDB->pipeline), CresDataDB->element_zero, CresDataDB->element_av[0], CresDataDB->element_av[1], NULL);
			    if( !gst_element_link_many(CresDataDB->element_zero, CresDataDB->element_av[0], CresDataDB->element_av[1], NULL)){
				    g_print( "ERROR:  Cannot link filter to source elements.\n" );
				    iStatus = CSIO_CANNOT_LINK_ELEMENTS;
			    }else
				    GST_DEBUG("success linking pipeline elements\n");
		    }	
		    *pipeline = CresDataDB->pipeline;
		    *source   = CresDataDB->element_zero;
		    break;
	    }
	    case ePROTOCOL_UDP:
	    {
			//build_udp_pipeline(CresDataDB,protoId);
			CresDataDB->element_zero = gst_element_factory_make("rtpbin", NULL);
			gst_bin_add(GST_BIN(CresDataDB->pipeline), CresDataDB->element_zero);

			//video
			CresDataDB->udp_video_port = currentSettingsDB.videoSettings[iStreamId].rtpVideoPort;
			CresDataDB->element_av[0] = gst_element_factory_make("udpsrc", NULL);
			g_object_set(G_OBJECT(CresDataDB->element_av[0]), "port", CresDataDB->udp_video_port, NULL);
			g_object_set(G_OBJECT(CresDataDB->element_av[0]), "caps", CresDataDB->caps_v_rtp, NULL);
			gst_bin_add(GST_BIN(CresDataDB->pipeline), CresDataDB->element_av[0]);
			gst_element_link(CresDataDB->element_av[0], CresDataDB->element_zero);

			//audio
			CresDataDB->element_av[1] = gst_element_factory_make("udpsrc", NULL);
			CresDataDB->udp_audio_port = currentSettingsDB.videoSettings[iStreamId].rtpAudioPort;
			g_object_set(G_OBJECT(CresDataDB->element_av[1]), "port", CresDataDB->udp_audio_port, NULL);
			g_object_set(G_OBJECT(CresDataDB->element_av[1]), "caps", CresDataDB->caps_a_rtp, NULL);
			gst_bin_add(GST_BIN(CresDataDB->pipeline), CresDataDB->element_av[1]);
			gst_element_link(CresDataDB->element_av[1], CresDataDB->element_zero);

			*pipeline = CresDataDB->pipeline;
			*source   = CresDataDB->element_zero;
	    	break;
	    }
	    case ePROTOCOL_MULTICAST_TS:
	    {
	    	strcpy(CresDataDB->multicast_grp,currentSettingsDB.videoSettings[iStreamId].multicastAddress);
		    if(currentSettingsDB.videoSettings[iStreamId].tsEnabled==STREAM_TRANSPORT_MPEG2TS_RTP){
			CresDataDB->element_zero = gst_element_factory_make("rtpbin", NULL);
			gst_bin_add(GST_BIN(CresDataDB->pipeline), CresDataDB->element_zero);

			CresDataDB->udp_port = currentSettingsDB.videoSettings[iStreamId].tsPort;
			CresDataDB->element_av[0] = gst_element_factory_make("udpsrc", NULL);
			g_object_set(G_OBJECT(CresDataDB->element_av[0]), "caps", CresDataDB->caps_v_ts, NULL);
			g_object_set(G_OBJECT(CresDataDB->element_av[0]), "port", CresDataDB->udp_port, NULL);
			gst_bin_add(GST_BIN(CresDataDB->pipeline), CresDataDB->element_av[0]);
			int ret = gst_element_link(CresDataDB->element_av[0], CresDataDB->element_zero);
			if(ret==0){
				g_print( "ERROR:  Cannot link filter to source elements.\n" );
				iStatus = CSIO_CANNOT_LINK_ELEMENTS;
			}else
				g_print( "ERROR: link filter to source elements.\n" );

		    }else if(currentSettingsDB.videoSettings[iStreamId].tsEnabled==STREAM_TRANSPORT_MPEG2TS_UDP){
			    CresDataDB->element_zero = gst_element_factory_make("udpsrc", NULL);
			    if(!CresDataDB->element_zero)
			    {
				    iStatus = CSIO_CANNOT_CREATE_ELEMENTS;
				    g_print( "ERROR: Cannot create udp source pipeline elements\n" );
			    }

			    CresDataDB->udp_port = currentSettingsDB.videoSettings[iStreamId].tsPort;
			    g_object_set(G_OBJECT(CresDataDB->element_zero), "port", CresDataDB->udp_port, NULL);

			    CresDataDB->element_av[0] = gst_element_factory_make( "queue", NULL );
			    if(!CresDataDB->element_av[0])
			    {
				    iStatus = CSIO_CANNOT_CREATE_ELEMENTS;
				    g_print( "ERROR: Cannot create queue source pipeline elements\n" );
			    }
			    
			    CresDataDB->element_av[1] = gst_element_factory_make( "tsdemux", NULL );
			    if(!CresDataDB->element_av[1])
			    {
				    iStatus = CSIO_CANNOT_CREATE_ELEMENTS;
				    g_print( "ERROR: Cannot create tsdemux source pipeline elements\n" );
			    }

			    gst_bin_add_many(GST_BIN(CresDataDB->pipeline), CresDataDB->element_zero, CresDataDB->element_av[0], CresDataDB->element_av[1], NULL);
			    if( !gst_element_link_many(CresDataDB->element_zero, CresDataDB->element_av[0], CresDataDB->element_av[1], NULL)){
				    g_print( "ERROR:  Cannot link filter to source elements.\n" );
				    iStatus = CSIO_CANNOT_LINK_ELEMENTS;
			    }else
				    GST_DEBUG ("success link pipeline elements\n");
			}

			*pipeline = CresDataDB->pipeline;
			*source   = CresDataDB->element_zero;
	    }
	    	break;
	    case ePROTOCOL_MULTICAST:
	    {
	    	//GST_ERROR ("ePROTOCOL_MULTICAST\n");
	    	strcpy(CresDataDB->multicast_grp,currentSettingsDB.videoSettings[iStreamId].multicastAddress);

	    	//build_udp_pipeline(CresDataDB,protoId);
	    	CresDataDB->element_zero = gst_element_factory_make("rtpbin", NULL);
			gst_bin_add(GST_BIN(CresDataDB->pipeline), CresDataDB->element_zero);

			//video
			CresDataDB->udp_video_port = currentSettingsDB.videoSettings[iStreamId].rtpVideoPort;
			CresDataDB->element_av[0] = gst_element_factory_make("udpsrc", NULL);
			g_object_set(G_OBJECT(CresDataDB->element_av[0]), "port", CresDataDB->udp_video_port, NULL);
			g_object_set(G_OBJECT(CresDataDB->element_av[0]), "caps", CresDataDB->caps_v_rtp, NULL);
			gst_bin_add(GST_BIN(CresDataDB->pipeline), CresDataDB->element_av[0]);
			gst_element_link(CresDataDB->element_av[0], CresDataDB->element_zero);

			//audio
			CresDataDB->element_av[1] = gst_element_factory_make("udpsrc", NULL);
			CresDataDB->udp_audio_port = currentSettingsDB.videoSettings[iStreamId].rtpAudioPort;
			g_object_set(G_OBJECT(CresDataDB->element_av[1]), "port", CresDataDB->udp_audio_port, NULL);
			g_object_set(G_OBJECT(CresDataDB->element_av[1]), "caps", CresDataDB->caps_a_rtp, NULL);
			gst_bin_add(GST_BIN(CresDataDB->pipeline), CresDataDB->element_av[1]);
			gst_element_link(CresDataDB->element_av[1], CresDataDB->element_zero);

	    	*pipeline = CresDataDB->pipeline;
	    	*source   = CresDataDB->element_zero;

	    	break;
	    }
	    case ePROTOCOL_FILE: //stub for now
	    default:
			iStatus = CSIO_FAILURE;
			break;
	}

	return iStatus;
}


void csio_jni_InitPipeline(eProtocolId protoId, int iStreamId)
{
	//GST_ERROR ("protoId = %d\n",protoId);
	switch( protoId )
	{
		case ePROTOCOL_RTSP_TCP:
		case ePROTOCOL_RTSP_UDP:
		case ePROTOCOL_DEFAULT_RTSP_TCP:
		case ePROTOCOL_RTSP_UDP_TS:
		case ePROTOCOL_RTSP_TS:
		{
			g_object_set(G_OBJECT(CresDataDB->element_zero), "location", currentSettingsDB.settingsMessage.msg[iStreamId].url, NULL);
			g_object_set(G_OBJECT(CresDataDB->element_zero), "latency", currentSettingsDB.videoSettings[iStreamId].streamingBuffer, NULL);
			g_object_set(G_OBJECT(CresDataDB->element_zero), "tcp_timeout", CresDataDB->tcp_timeout_usec, NULL);
			g_object_set(G_OBJECT(CresDataDB->element_zero), "timeout", CresDataDB->udp_timeout_usec, NULL);
			// For some reason, this port range must be set for rtsp with multicast to work.
			//g_object_set(G_OBJECT(CresDataDB->element_zero), "port-range", "5001-65535", NULL);
			g_object_set(G_OBJECT(CresDataDB->element_zero), "protocols", CresDataDB->protocols, NULL);

			// video part
			CresDataDB->video_sink = NULL;
			break;
		}

		case ePROTOCOL_HTTP:
			//GST_DEBUG ("ePROTOCOL_HTTP pass\n");
			break;

		case ePROTOCOL_UDP_TS:
			break;

		case ePROTOCOL_UDP:
		{
			//GST_DEBUG ("ePROTOCOL_UDP pass\n");
			break;
		}
		case ePROTOCOL_MULTICAST_TS:
			break;

		case ePROTOCOL_MULTICAST:
		{
			//GST_DEBUG ("ePROTOCOL_MULTICAST pass\n");
			break;
		}
		case ePROTOCOL_FILE: //stub for now
		default:
			//iStatus = CSIO_FAILURE;
			break;
	}
}

void csio_jni_SetSourceLocation(eProtocolId protoId, char *location, int iStreamId)
{
	//GST_ERROR ("protoId = %d\n",protoId);
	switch( protoId )
	{
		case ePROTOCOL_RTSP_TCP:
		case ePROTOCOL_RTSP_UDP:
		case ePROTOCOL_DEFAULT_RTSP_TCP:
		case ePROTOCOL_RTSP_UDP_TS:
		case ePROTOCOL_RTSP_TS:
		{
			g_object_set(G_OBJECT(CresDataDB->element_zero), "location", \
					location, NULL);
			break;
		}

		case ePROTOCOL_HTTP:
			//GST_DEBUG ("csio_jni_SetSourceLocation: ePROTOCOL_HTTP pass\n");
			break;

		case ePROTOCOL_UDP_TS:
		{
			//GST_DEBUG ("ePROTOCOL_UDP_TS pass\n");
			break;
		}

		case ePROTOCOL_UDP:
		{
			//GST_DEBUG ("ePROTOCOL_UDP pass\n");
			break;
		}
		case ePROTOCOL_MULTICAST_TS:
		case ePROTOCOL_MULTICAST:
		{
			//GST_DEBUG ("ePROTOCOL_MULTICAST: location[%s]\n",CresDataDB->multicast_grp);
			if(currentSettingsDB.videoSettings[iStreamId].tsEnabled==STREAM_TRANSPORT_MPEG2TS_UDP){
				g_object_set(G_OBJECT(CresDataDB->element_zero), "address", \
						location, NULL);
			}
			else { //RTP Only and MPEG2TS RTP 
				g_object_set(G_OBJECT(CresDataDB->element_av[0]), "address", \
						location, NULL);
			}
			//g_object_set(G_OBJECT(CresDataDB->element_av[1]), "address", \
			//		CresDataDB->multicast_grp, NULL);

			break;
		}
		case ePROTOCOL_FILE: //stub for now
		default:
			break;
	}
}

void csio_jni_SetMsgHandlers(void* obj,eProtocolId protoId, int iStreamId)
{
	GST_ERROR ("protoId = %d\n",protoId);
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
			//GST_DEBUG ("SetMsgHandlers protoId[%d]\n",protoId);
			// Register callback.
			if(CresDataDB->element_zero != NULL)
			{				
				g_signal_connect(CresDataDB->element_zero, "pad-added", G_CALLBACK(csio_PadAddedMsgHandler), obj);
			}
			else
			{
				GST_ERROR("Null element zero, no callbacks will be registered");
			}

			/* Set the pipeline to READY, so it can already accept a window handle, if we have one */
			 gst_element_set_state(CresDataDB->pipeline, GST_STATE_READY);

			 if (CresDataDB->video_sink)
			 {
				 // Try just setting the video sink, because we haven't built the pipeline yet.
				 //gst_element_set_state(data->video_sink, GST_STATE_READY);
			 }
			 else
			 {
				 CresDataDB->video_sink = gst_bin_get_by_interface(GST_BIN(CresDataDB->pipeline), GST_TYPE_VIDEO_OVERLAY);
			 }
			 
			 break;
		}
		case ePROTOCOL_UDP_TS:
		case ePROTOCOL_MULTICAST_TS:
		{
			if(CresDataDB->element_zero != NULL)
			{
		    		if(currentSettingsDB.videoSettings[iStreamId].tsEnabled==STREAM_TRANSPORT_MPEG2TS_RTP){
				g_signal_connect(CresDataDB->element_zero, "pad-added", G_CALLBACK(csio_PadAddedMsgHandler), obj);
		    		}else if(currentSettingsDB.videoSettings[iStreamId].tsEnabled==STREAM_TRANSPORT_MPEG2TS_UDP){
				g_signal_connect(CresDataDB->element_av[1], "pad-added", G_CALLBACK(csio_PadAddedMsgHandler), obj);
				}
			}
			else
			{
				GST_ERROR("Null element zero, no callbacks will be registered");
			}
			break;
		}
		case ePROTOCOL_HTTP:
			break;

		case ePROTOCOL_FILE: //stub for now
		default:
			//iStatus = CSIO_FAILURE;
			break;
	}
}

int csio_jni_AddAudio(GstPad *new_pad,gchar *encoding_name, GstElement **sink, bool send_pause)
{
	int iStatus  = CSIO_SUCCESS;
	*sink = NULL;
	GstElement *ele0 = NULL;
	int do_rtp;

	//Extracted from STR, to support IC Camera with mulaw
	if(send_pause){
		gst_element_set_state( CresDataDB->pipeline, GST_STATE_PAUSED);
	}

	gchar * p_caps_string;
	GstCaps *new_pad_caps = gst_pad_query_caps( new_pad, NULL );
	p_caps_string = gst_caps_to_string (new_pad_caps);
	if(strncmp(p_caps_string, "application/x-rtp", 17) == 0)
	{
		do_rtp = 1;
	}

	iStatus = build_audio_pipeline(encoding_name, CresDataDB, do_rtp,&ele0,sink);
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
		GST_ERROR ("audio sink pad is already linked");
		gst_object_unref(sink_pad);

		iStatus  =  CSIO_CANNOT_LINK_ELEMENTS;
		goto doneAddAudio ;
	}

	// Link rest of pipeline to beginning.
	gst_pad_link(new_pad, sink_pad);

	gst_element_set_state( CresDataDB->pipeline, GST_STATE_PLAYING);

	GST_DEBUG("csio_jni_AddAudio iStatus = %d", iStatus);

doneAddAudio:
	if(new_pad_caps != NULL)
	{
		gst_caps_unref(new_pad_caps);
	}

	return iStatus;
}

int csio_jni_AddVideo(GstPad *new_pad,gchar *encoding_name, GstElement **sink,eProtocolId protoIdi, bool send_pause)
{
	int iStatus  = CSIO_SUCCESS;
	*sink = NULL;
	GstElement *ele0 = NULL;
	int do_rtp = 0;

	//GST_DEBUG("csio_jni_AddVideo: sink =0x%x",protoId);
	//Extracted from STR, to support IC Camera
	if(send_pause){
		gst_element_set_state( CresDataDB->pipeline, GST_STATE_PAUSED);
	}

	gchar * p_caps_string;
	GstCaps *new_pad_caps = gst_pad_query_caps( new_pad, NULL );
	p_caps_string = gst_caps_to_string (new_pad_caps);
	if(strncmp(p_caps_string, "application/x-rtp", 17) == 0)
	{
		do_rtp = 1;
	}

	iStatus = build_video_pipeline(encoding_name, CresDataDB,0,do_rtp,&ele0,sink);

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
		GST_ERROR ("video sink pad is already linked");
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

	if( gst_element_set_state( CresDataDB->pipeline, GST_STATE_PLAYING) == GST_STATE_CHANGE_FAILURE )
	{
		GST_ERROR ("Cannot restart pipeline\n");
	}

	if(iStatus != CSIO_SUCCESS)
	{
		iStatus = CSIO_COULD_NOT_START_PIPELINE;
		goto doneAddVideo;
	}

	GST_DEBUG("csio_jni_AddVideo iStatus = %d", iStatus);
doneAddVideo:
	if(new_pad_caps != NULL)
	{
		gst_caps_unref(new_pad_caps);
	}
	return iStatus;
}

void csio_jni_initVideo()
{
	if(g_using_glimagsink || currentSettingsDB.videoSettings[0].videoSinkSelect == 0)
	{
	    GST_DEBUG("qos is set to default");
	    g_object_set(G_OBJECT(CresDataDB->video_sink), "force-aspect-ratio", FALSE, NULL);
    }
	else
	{	    
	    GST_DEBUG("qos is turned off for surfaceflingersink!");
	    g_object_set(G_OBJECT(CresDataDB->video_sink), "qos", FALSE, NULL);	   
	}
}

void csio_jni_cleanup (JNIEnv* env, jobject thiz)
{
    CresDataDB->video_sink = NULL;//TODO: this will be unref by CStreamer.
    CresDataDB->audio_sink = NULL;//TODO: this will be unref by CStreamer.
    CresDataDB->pipeline   = NULL;//TODO: this will be unref by CStreamer.
}

void *csio_SendInitiatorAddressFb( void * arg )
{
	jstring initiatorAddress_jstr;
	JNIEnv *env = get_jni_env ();
	jint streamId = csio_GetStreamId(arg);
	char *initiatorAddress_cstr = csio_GetInitiatorFbAddress(streamId);	

	GST_DEBUG( "Sent INITIATOR FB %s", initiatorAddress_cstr );

	initiatorAddress_jstr = (*env)->NewStringUTF(env, initiatorAddress_cstr);

	jmethodID sendInitiatorAddress = (*env)->GetMethodID(env, gStreamIn_javaClass_id, "sendInitiatorFbAddress", "(Ljava/lang/String;I)V");
	if (sendInitiatorAddress == NULL) return NULL;

	(*env)->CallVoidMethod(env, CresDataDB->app, sendInitiatorAddress, initiatorAddress_jstr, 0);
	if ((*env)->ExceptionCheck (env)) {
		GST_ERROR ("Failed to call Java method 'sendInitiatorAddress'");
		(*env)->ExceptionClear (env);
	}
	(*env)->DeleteLocalRef (env, initiatorAddress_jstr);

	pthread_exit( NULL );
	return(NULL);
}
void csio_jni_recoverDucati()
{
    JNIEnv *env = get_jni_env ();
    jmethodID recoverDucati = (*env)->GetMethodID(env, gStreamIn_javaClass_id, "recoverDucati", "()V");
    if (recoverDucati == NULL) return NULL;

    (*env)->CallVoidMethod(env, CresDataDB->app, recoverDucati);
    if ((*env)->ExceptionCheck (env)) {
        GST_ERROR ("Failed to call Java method 'recoverDucati'");
        (*env)->ExceptionClear (env);
    }
}
