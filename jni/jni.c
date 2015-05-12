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

/* These global variables cache values which are not changing during execution */
static pthread_t gst_app_thread;
static pthread_key_t current_jni_env;
static JavaVM *java_vm;
static jfieldID custom_data_field_id;
static jmethodID set_message_method_id;
static jmethodID on_gstreamer_initialized_method_id;

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

/*
 * Java Bindings
 */

/* Instruct the native code to create its internal data structure, pipeline and thread */
static void gst_native_init (JNIEnv* env, jobject thiz) 
{
	CustomData *data = g_new0 (CustomData, 1);
	SET_CUSTOM_DATA (env, thiz, custom_data_field_id, data);
	GST_DEBUG_CATEGORY_INIT (debug_category, "tutorial-3", 0, "Android tutorial 3");
	gst_debug_set_threshold_for_name("tutorial-3", GST_LEVEL_DEBUG);
	GST_DEBUG ("Created CustomData at %p", data);
	data->app = (*env)->NewGlobalRef (env, thiz);
	GST_DEBUG ("Created GlobalRef for app object at %p", data->app);
	init_custom_data(data);
	pthread_mutex_init(&(data->ready_to_start_playing_lock), NULL);
	pthread_cond_init(&(data->ready_to_start_playing_signal), NULL);
	//TODO: what do we do with this data->app
}

/* Quit the main loop, remove the native thread and free resources */
static void gst_native_finalize (JNIEnv* env, jobject thiz) 
{
	CustomData *data = GET_CUSTOM_DATA (env, thiz, custom_data_field_id);
	if (!data) return;

	GST_DEBUG ("Deleting GlobalRef for app object at %p", data->app);
	(*env)->DeleteGlobalRef (env, data->app);
	GST_DEBUG ("Freeing CustomData at %p", data);
	g_free (data);
	SET_CUSTOM_DATA (env, thiz, custom_data_field_id, NULL);
	GST_DEBUG ("Done finalizing");

	pthread_mutex_destroy(&(data->ready_to_start_playing_lock));
	pthread_cond_destroy(&(data->ready_to_start_playing_signal));
}

/* Set pipeline to PLAYING state */
static void gst_native_play (JNIEnv* env, jobject thiz, jstring url) 
{
	CustomData *data = GET_CUSTOM_DATA (env, thiz, custom_data_field_id);
	if (!data) return;

	const char * url_cstring = (*env)->GetStringUTFChars( env, url , NULL ) ;

	GST_DEBUG ("Using URL: '%s'", url_cstring);
	strcpy(data->url, url_cstring);

	(*env)->ReleaseStringUTFChars(env, url, url_cstring);
#ifdef PETE_JNI_CODE
	pthread_create (&gst_app_thread, NULL, &app_function, data);
	pthread_mutex_lock(&(data->ready_to_start_playing_lock));
	pthread_cond_wait(&(data->ready_to_start_playing_signal), &(data->ready_to_start_playing_lock));
	pthread_mutex_unlock(&(data->ready_to_start_playing_lock));

	GST_DEBUG ("Setting state to PLAYING");
	gst_element_set_state (data->pipeline, GST_STATE_PLAYING);
#else
	start_streaming_cmd();
#endif
}

/* Set pipeline to PAUSED state */
static void gst_native_pause (JNIEnv* env, jobject thiz) 
{
	CustomData *data = GET_CUSTOM_DATA (env, thiz, custom_data_field_id);
	if (!data) return;
	GST_DEBUG ("Setting state to PAUSED");
	gst_element_set_state (data->pipeline, GST_STATE_PAUSED);
}

/* Set pipeline to PAUSED state */
static void gst_native_stop (JNIEnv* env, jobject thiz) 
{
    CustomData *data = GET_CUSTOM_DATA (env, thiz, custom_data_field_id);
    if (!data) return;
    GST_DEBUG ("Setting state to NULL");

#ifdef PETE_JNI_CODE
    gst_element_set_state (data->pipeline, GST_STATE_NULL);

    GST_DEBUG ("Quitting main loop...");
    g_main_loop_quit (data->main_loop);
    GST_DEBUG ("Waiting for thread to finish...");
    pthread_join (gst_app_thread, NULL);
#else
    stop_streaming_cmd();
#endif
}

/* Static class initializer: retrieve method and field IDs */
static jboolean gst_native_class_init (JNIEnv* env, jclass klass) 
{
	custom_data_field_id = (*env)->GetFieldID (env, klass, "native_custom_data", "J");
	set_message_method_id = (*env)->GetMethodID (env, klass, "setMessage", "(Ljava/lang/String;)V");
	on_gstreamer_initialized_method_id = (*env)->GetMethodID (env, klass, "onGStreamerInitialized", "()V");

	if (!custom_data_field_id || !set_message_method_id || !on_gstreamer_initialized_method_id) {
		/* We emit this message through the Android log instead of the GStreamer log because the later
		* has not been initialized yet.
		*/
		__android_log_print (ANDROID_LOG_ERROR, "tutorial-3", "The calling class does not implement all necessary interface methods");
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
		gst_element_set_state (data->pipeline, GST_STATE_READY);
	}

	ANativeWindow_release (data->native_window);
	data->native_window = NULL;
	data->initialized = FALSE;

	//TODO: when this will be called?
}

/* List of implemented native methods */
static JNINativeMethod native_methods[] = 
{
	{ "nativeInit", "()V", (void *) gst_native_init},
	{ "nativeFinalize", "()V", (void *) gst_native_finalize},
	{ "nativePlay", "(Ljava/lang/String;)V", (void *) gst_native_play},
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

	// Uncomment these lines to enable debugging
	// You may need to run this on the command line as well:
	//      setprop log.redirect-stdio true
	setenv("GST_DEBUG", "*:3", 1);
	setenv("GST_DEBUG_NO_COLOR", "1", 1);

	if ((*vm)->GetEnv(vm, (void**) &env, JNI_VERSION_1_4) != JNI_OK) 
	{
		__android_log_print (ANDROID_LOG_ERROR, "tutorial-3", "Could not retrieve JNIEnv");
		return 0;
	}
	//jclass klass = (*env)->FindClass (env, "com/gst_sdk_tutorials/tutorial_3/Tutorial3");
	jclass klass = (*env)->FindClass (env, "com/crestron/txrxservice/StreamIn");
	(*env)->RegisterNatives (env, klass, native_methods, G_N_ELEMENTS(native_methods));

	pthread_key_create (&current_jni_env, detach_current_thread);

	return JNI_VERSION_1_4;
}

#include "cregstplay.c"

int csio_IpLinkCientConnected()
{
	return 0;
}
void csio_send_zerostats()
{
	   //CSIOProductMessageHandler->send_statistics(0, 0, 0, 0);
	   //CSIOProductMessageHandler->send_bitrate(0);
}
int csio_ClearOverlay()
{
	return 0;
}

void    *csio_SendMulticastAddressFb(void * arg)
{
	return NULL;
}
void csio_send_stats (uint64_t video_packets_received, int video_packets_lost, uint64_t audio_packets_received, int audio_packets_lost, uint16_t bitrate)
{
}

void csio_get_width_and_height_from_mode (uint32_t * width, uint32_t * height)
{
	return;
}

int csio_SendVideoPlayingStatusMessage(unsigned int source, StreamState state)
{
	return 0;
}
int csio_SendVideoSourceParams(unsigned int source, unsigned int width, unsigned int height, unsigned int framerate, unsigned int profile)
{
	return 0;
}
void csio_signal_that_stream_has_stopped()
{

}

void csio_start_mode_change_detection ()
{
	return;
}





