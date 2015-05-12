#ifndef __CREGSTPLAY_H__
#define __CREGSTPLAY_H__

///////////////////////////////////////////////////////////////////////////////

#include <jni.h>
#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <pthread.h>
#include <gst/gst.h>
#include <gst/video/video.h>
#include <gst/rtsp/gstrtsptransport.h>
#include <stdbool.h>
#include "csioCommonShare.h"
///////////////////////////////////////////////////////////////////////////////

GST_DEBUG_CATEGORY_STATIC (debug_category);
#define GST_CAT_DEFAULT debug_category
#define PETE_JNI_CODE
///////////////////////////////////////////////////////////////////////////////

typedef struct
{
	unsigned int x;
	unsigned int y;
	unsigned int width;
	unsigned int height;
} CRESTWINDOW;

/* Structure to contain all our information, so we can pass it to callbacks */
typedef struct _CustomData 
{
	jobject app;            /* Application instance, used to call its methods. A global reference is kept. */
	ANativeWindow *native_window; /* The Android native window where video will be rendered */

	pthread_mutex_t ready_to_start_playing_lock;
	pthread_cond_t ready_to_start_playing_signal;

	GstElement *pipeline;   /* The running pipeline */
	GMainContext *context;  /* GLib context used to run the main loop */
	GMainLoop *main_loop;   /* GLib main loop */
	gboolean initialized;   /* To avoid informing the UI multiple times about the initialization */
	GstElement *video_sink; /* The video sink element which receives XOverlay commands */
	GstElement *audio_sink; 
	char url [1024];
	GstElement *element_zero;	
	GstElement *element_av [10];
	GstElement *element_a [10];
	GstElement *element_v [10];
	unsigned int element_after_tsdemux;	/* Used to add the rest of the video pipeline */
	
	// parameters from control system/platform/java
	unsigned int udp_port;
	unsigned int udp_video_port;
	unsigned int udp_audio_port;
	char multicast_grp[256];
	unsigned int buffer_msec;
    CRESTWINDOW	win;
	// rtspsrc parameters
	guint64 tcp_timeout_usec;
	guint64 udp_timeout_usec;
	GstRTSPLowerTrans protocols;
} CustomData;

///////////////////////////////////////////////////////////////////////////////

extern void *app_function (void *userdata);
extern void set_ui_message (const gchar *message, CustomData *data);
extern void check_initialization_complete (CustomData *data);
extern void init_custom_data(void *userdata);


//#include "csioCapability.h"


#endif
