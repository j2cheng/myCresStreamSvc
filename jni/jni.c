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



	iStatus = csio_Init();
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
	GST_DEBUG ("Created CustomData at %p", data);
	data->app = (*env)->NewGlobalRef (env, thiz);
	GST_DEBUG ("Created GlobalRef for app object at %p", data->app);
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
void gst_native_play (JNIEnv* env, jobject thiz)
{	
	start_streaming_cmd();	
}

/* Set pipeline to PAUSED state */
static void gst_native_pause (JNIEnv* env, jobject thiz) 
{
	pause_streaming_cmd();
}

/* Set pipeline to PAUSED state */
void gst_native_stop (JNIEnv* env, jobject thiz)
{
    GST_DEBUG ("Setting state to NULL");
    
    stop_streaming_cmd();
    CresDataDB->video_sink = NULL;//TODO: this will be unref by CStreamer.
    CresDataDB->audio_sink = NULL;//TODO: this will be unref by CStreamer.
    CresDataDB->pipeline   = NULL;//TODO: this will be unref by CStreamer.
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

	if ((nativeGetCurrentStreamState(sessionId) == STREAMSTATE_STARTED) && (strcasecmp(url_cstring, currentSettingsDB.settingsMessage.msg[sessionId].url)))
	{
		restartStream = 1;
	}

	if (restartStream)
	{
		jmethodID onStop = (*env)->GetMethodID(env, gStreamIn_javaClass_id, "onStop", "()V");
		if (onStop == NULL) return;

		(*env)->CallVoidMethod(env, CresDataDB->app, onStop);
		if ((*env)->ExceptionCheck (env)) {
			GST_ERROR ("Failed to call Java method 'onStop'");
			(*env)->ExceptionClear (env);
		}
	}

	GST_DEBUG ("Using URL: '%s'", url_cstring);
	strcpy(currentSettingsDB.settingsMessage.msg[sessionId].url, url_cstring);

	(*env)->ReleaseStringUTFChars(env, url_jstring, url_cstring);

	if (restartStream)
	{
		jmethodID onStart = (*env)->GetMethodID(env, gStreamIn_javaClass_id, "onStart", "()V");
		if (onStart == NULL) return;

		(*env)->CallVoidMethod(env, CresDataDB->app, onStart);
		if ((*env)->ExceptionCheck (env)) {
			GST_ERROR ("Failed to call Java method 'onStart'");
			(*env)->ExceptionClear (env);
		}
	}
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetRtspPort(JNIEnv *env, jobject thiz, jint port, jint sessionId)
{
	GST_DEBUG ("Using RtspPort: '%d'", port);
	//TODO: Currently we dont save rtsp port in CSIOsettings
	//currentSettingsDB.videoSettings[0]. = port;
	//GST_DEBUG ("URL in currentSettingsDB: '%d'", currentSettingsDB.settingsMessage.msg[0].url);
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetTsPort(JNIEnv *env, jobject thiz, jint port, jint sessionId)
{
	GST_DEBUG ("Using tsPort: '%d'", port);
	currentSettingsDB.videoSettings[sessionId].tsPort = port;
	GST_DEBUG ("tsPort in currentSettingsDB: '%d'", currentSettingsDB.videoSettings[sessionId].tsPort);
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetRtpVideoPort(JNIEnv *env, jobject thiz, jint port, jint sessionId)
{
	GST_DEBUG ("Using rtpVideoPort: '%d'", port);
	currentSettingsDB.videoSettings[sessionId].rtpVideoPort = port;
	GST_DEBUG ("rtpVideoPort in currentSettingsDB: '%d'", currentSettingsDB.videoSettings[sessionId].rtpVideoPort);
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetRtpAudioPort(JNIEnv *env, jobject thiz, jint port, jint sessionId)
{
	GST_DEBUG ("Using rtpAudioPort: '%d'", port);
	currentSettingsDB.videoSettings[sessionId].rtpAudioPort = port;
	GST_DEBUG ("rtpAudioPort in currentSettingsDB: '%d'", currentSettingsDB.videoSettings[sessionId].rtpAudioPort);
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetSessionInitiation(JNIEnv *env, jobject thiz, jint initMode, jint sessionId)
{
	GST_DEBUG ("Using sessionInitiationMode: '%d'", initMode);
	currentSettingsDB.videoSettings[sessionId].sessionInitiationMode = initMode;
	GST_DEBUG ("sessionInitiationMode in currentSettingsDB: '%d'", currentSettingsDB.videoSettings[sessionId].sessionInitiationMode);
}

JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetTransportMode(JNIEnv *env, jobject thiz, jint transportMode, jint sessionId)
{
	GST_DEBUG ("Using tsEnabled: '%d'", transportMode);
	currentSettingsDB.videoSettings[sessionId].tsEnabled = transportMode;
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
	{ "nativePlay", "()V", (void *) gst_native_play},
	{ "nativePause", "()V", (void *) gst_native_pause},
	{ "nativeStop", "()V", (void *) gst_native_stop},
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
	// Uncomment these lines to enable debugging
	// You may need to run this on the command line as well:
	//      setprop log.redirect-stdio true
	setenv("GST_DEBUG", "*:1", 1);
	setenv("GST_DEBUG_NO_COLOR", "1", 1);

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
        JNIEnv *env = get_jni_env ();

	jmethodID getCurrentWidthHeight = (*env)->GetMethodID(env, gStreamIn_javaClass_id, "getCurrentWidthHeight", "()[I");
        if (getCurrentWidthHeight == NULL) return;

        jintArray retval = (jintArray) (*env)->CallObjectMethod(env, CresDataDB->app, getCurrentWidthHeight);
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

}

void csio_start_mode_change_detection ()
{
	return;
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

int csio_jni_CreatePipeline(GstElement **pipeline,GstElement **source)
{	
	CresDataDB->pipeline = gst_pipeline_new(NULL);
	CresDataDB->element_zero = gst_element_factory_make("rtspsrc", NULL);

	gst_bin_add_many(GST_BIN(CresDataDB->pipeline), CresDataDB->element_zero, NULL);
	if( !CresDataDB->pipeline || !CresDataDB->element_zero )
	{
		GST_ERROR ("ERROR: Cannot create source pipeline elements\n");
		return CSIO_CANNOT_CREATE_ELEMENTS;
	}
	
	*pipeline = CresDataDB->pipeline;
	*source   = CresDataDB->element_zero;

	return CSIO_SUCCESS;
}


void csio_jni_InitPipeline()
{
	g_object_set(G_OBJECT(CresDataDB->element_zero), "location", currentSettingsDB.settingsMessage.msg[0].url, NULL);
	g_object_set(G_OBJECT(CresDataDB->element_zero), "latency", currentSettingsDB.videoSettings[0].streamingBuffer, NULL);
	g_object_set(G_OBJECT(CresDataDB->element_zero), "tcp_timeout", CresDataDB->tcp_timeout_usec, NULL);
	g_object_set(G_OBJECT(CresDataDB->element_zero), "timeout", CresDataDB->udp_timeout_usec, NULL);
	// For some reason, this port range must be set for rtsp with multicast to work.
	//g_object_set(G_OBJECT(CresDataDB->element_zero), "port-range", "5001-65535", NULL);
	g_object_set(G_OBJECT(CresDataDB->element_zero), "protocols", CresDataDB->protocols, NULL);

	// video part
	CresDataDB->video_sink = NULL;
}

void csio_jni_SetSourceLocation()
{
	g_object_set(G_OBJECT(CresDataDB->element_zero), "location", currentSettingsDB.settingsMessage.msg[0].url, NULL);
}

void csio_jni_SetMsgHandlers(void* obj)
{
	// Register callback.
	if(CresDataDB->element_zero != NULL)
	{
		//g_signal_connect(CresDataDB->element_zero, "pad-added", G_CALLBACK(pad_added_callback), CresDataDB);
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

	 /* Instruct the bus to emit signals for each received message, and connect to the interesting signals
	 GstBus *bus = gst_element_get_bus (CresDataDB->pipeline);
	 GSource *bus_source = gst_bus_create_watch (bus);
	 g_source_set_callback (bus_source, (GSourceFunc) gst_bus_async_signal_func, NULL, NULL);
	 g_source_attach (bus_source, CresDataDB->context);
	 g_source_unref (bus_source);
	 g_signal_connect (G_OBJECT (bus), "message::error", (GCallback)error_callback, CresDataDB);
	 g_signal_connect (G_OBJECT (bus), "message::state-changed", (GCallback)state_changed_callback, CresDataDB);
	 gst_object_unref (bus);*/
}

int csio_jni_AddAudio(GstPad *new_pad,gchar *encoding_name, int do_rtp,GstElement **sink)
{
	int iStatus  = CSIO_SUCCESS;
	*sink = NULL;
	GstElement *ele0 = NULL;
	iStatus = build_audio_pipeline(encoding_name, CresDataDB, do_rtp,&ele0,sink);
	if(ele0 == NULL)
	{
		return CSIO_CANNOT_CREATE_ELEMENTS;
	}

	//TODOL need to call CStreamer::initAudio here

	// Get the pad given an element.
	GstPad *sink_pad = NULL;
	sink_pad = gst_element_get_static_pad (ele0, "sink");
	if(sink_pad == NULL)
		return CSIO_CANNOT_LINK_ELEMENTS;

	if(gst_pad_is_linked (sink_pad))
	{
		GST_ERROR ("audio sink pad is already linked");
		gst_object_unref(sink_pad);

		return CSIO_CANNOT_LINK_ELEMENTS;
	}

	// Link rest of pipeline to beginning.
	gst_pad_link(new_pad, sink_pad);

	gst_element_set_state( CresDataDB->pipeline, GST_STATE_PLAYING);

	GST_DEBUG("csio_jni_AddAudio iStatus = %d", iStatus);
	//TODOL need to call pCurCfg->setStatus( iStatus );
	return iStatus;
}

int csio_jni_AddVideo(GstPad *new_pad,gchar *encoding_name, int do_rtp,GstElement **sink)
{
	int iStatus  = CSIO_SUCCESS;
	*sink = NULL;
	GstElement *ele0 = NULL;
	iStatus = build_video_pipeline(encoding_name, CresDataDB,0,do_rtp,&ele0,sink);
	GST_DEBUG("csio_jni_AddVideo: sink =0x%x",*sink);
	if(ele0 == NULL)
	{
		return CSIO_CANNOT_CREATE_ELEMENTS;
	}

	//TODOL need to call CStreamer::initVideo here

	// Get the pad given an element.
	GstPad *sink_pad = NULL;
	sink_pad = gst_element_get_static_pad (ele0, "sink");
	if(sink_pad == NULL)
		return CSIO_CANNOT_LINK_ELEMENTS;

	if(gst_pad_is_linked (sink_pad))
	{
		GST_ERROR ("video sink pad is already linked");
		gst_object_unref(sink_pad);

		return CSIO_CANNOT_LINK_ELEMENTS;
	}

	// Link rest of pipeline to beginning.
	iStatus = gst_pad_link(new_pad, sink_pad);
	GST_DEBUG ("csio_jni_AddVideo:gst_pad_link returns iStatus[0x%x]",iStatus);
	if(iStatus != CSIO_SUCCESS)
		return CSIO_CANNOT_LINK_ELEMENTS;

	if( gst_element_set_state( CresDataDB->pipeline, GST_STATE_PLAYING) == GST_STATE_CHANGE_FAILURE )
	{
		GST_ERROR ("Cannot restart pipeline\n");
	}

	if(iStatus != CSIO_SUCCESS)
		return CSIO_COULD_NOT_START_PIPELINE;

	//TODOL need to call pCurCfg->setStatus( iStatus );
	return iStatus;
}

void csio_jni_initVideo()
{
	g_object_set(G_OBJECT(CresDataDB->video_sink), "force-aspect-ratio", FALSE, NULL);
}




