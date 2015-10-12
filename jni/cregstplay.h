#ifndef __CREGSTPLAY_H__
#define __CREGSTPLAY_H__

///////////////////////////////////////////////////////////////////////////////

#include <jni.h>
#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <pthread.h>
#include <time.h>
#include <errno.h>
#include <gst/gst.h>
#include <gst/video/video.h>
#include <gst/rtsp/gstrtsptransport.h>
#include <stdbool.h>
#include "csioCommonShare.h"
///////////////////////////////////////////////////////////////////////////////

GST_DEBUG_CATEGORY_STATIC (debug_category);
#define GST_CAT_DEFAULT debug_category

#define MAX_STREAMS 2
#define MAX_ELEMENTS 20

#define DEFAULT_AMCVIDDEC_TS_OFFSET 400//in ms
#define DEFAULT_MIN_STRING_BUFFER 100//in ms
#define DEFAULT_MAX_STRING_BUFFER 5000//in ms
//#define INSERT_SF_SINK 1
///////////////////////////////////////////////////////////////////////////////

typedef struct
{
	unsigned int x;
	unsigned int y;
	unsigned int width;
	unsigned int height;
} CRESTWINDOW;

/* per-stream info */
typedef struct _CREGSTREAM 
{
    unsigned int streamId;
	ANativeWindow *native_window; /* The Android native window where video will be rendered */

	//pthread_mutex_t ready_to_start_playing_lock;
	//pthread_cond_t ready_to_start_playing_signal;

	GstElement *pipeline;   /* The running pipeline */
	GMainContext *context;  /* GLib context used to run the main loop */
	GMainLoop *main_loop;   /* GLib main loop */
	gboolean initialized;   /* To avoid informing the UI multiple times about the initialization */
	GstElement *video_sink; /* The video sink element which receives XOverlay commands */
	GstElement *audio_sink; 
	GstElement *amcvid_dec;
	GstElement *element_fake_dec;
	GstElement *element_valve_v;
	GstElement *element_valve_a;
	
	GstElement *element_zero;	
	GstElement *element_av [MAX_ELEMENTS];
	GstElement *element_a [MAX_ELEMENTS];
	GstElement *element_v [MAX_ELEMENTS];
	unsigned int element_after_tsdemux;	/* Used to add the rest of the video pipeline */
	
	// parameters from control system/platform/java
	unsigned int udp_port;
	unsigned int udp_video_port;
	unsigned int udp_audio_port;
	char multicast_grp[512];

    CRESTWINDOW	win;
	// rtspsrc parameters
	guint64 tcp_timeout_usec;
	guint64 udp_timeout_usec;
	GstRTSPLowerTrans protocols;
	//g_object_set for udpsrc crashes if try to have caps as strings.
	//char caps_v[2048];
	//char caps_a[2048];
	GstCaps * caps_v;
	GstCaps * caps_v_rtp;
	GstCaps * caps_v_ts;
	GstCaps * caps_a;
	GstCaps * caps_a_rtp;
	char port_range_text[32];
	int do_udp_ts;

	int using_glimagsink;
	void* surface;
	int amcviddec_ts_offset;
	int audiosink_ts_offset;	
} CREGSTREAM;

/* Structure to contain all our information, so we can pass it to callbacks */
typedef struct _CustomData 
{
	jobject app;            /* Application instance, used to call its methods. A global reference is kept. */
	
    CREGSTREAM stream[MAX_STREAMS];
} CustomData;

/**********************************************************************
* Field debugging command
**********************************************************************/
enum
  {
      FIELD_DEBUG_BLOCK_AUDIO = 1,                     //1
      FIELD_DEBUG_INSERT_PROBE ,
      FIELD_DEBUG_AMC_PRINT_TS ,
      FIELD_DEBUG_DROP_BEFORE_PARSE ,
      FIELD_DEBUG_FLUSH_PIPELINE ,
      FIELD_DEBUG_SET_AMCVIDDEC_DEBUG_LEVEL ,
      FIELD_DEBUG_SET_VIDEODECODER_DEBUG_LEVEL ,
      FIELD_DEBUG_SET_OPENSLESSINK_DEBUG_LEVEL ,
      FIELD_DEBUG_SET_CATEGORY_DEBUG_LEVEL,
      FIELD_DEBUG_SET_AUDIOSINK_TS_OFFSET,
      FIELD_DEBUG_SET_AMCVIDDEC_TS_OFFSET,
      FIELD_DEBUG_PRINT_AUDIOSINK_PROPERTIES,
      FIELD_DEBUG_PRINT_ELEMENT_PROPERTY,
      //this should the last item
      MAX_SPECIAL_FIELD_DEBUG_NUM              //
  };
//Verification macro
#define IsValidSpecialFieldIndex(a)            ( a > 0 && a < MAX_SPECIAL_FIELD_DEBUG_NUM )

//Field debug UINT32 array size
#define SPECIAL_FIELD_DEBUG_ARRAY_SIZE         ( ((MAX_SPECIAL_FIELD_DEBUG_NUM-1)/32)+1 )

extern UINT32 g_lSpecialFieldDebugState[SPECIAL_FIELD_DEBUG_ARRAY_SIZE];
extern void csio_jni_printFieldDebugInfo();

//Field debug macros
#define IsSpecialFieldDebugIndexActive(a)      ( g_lSpecialFieldDebugState[((a-1)/32)] & (1<<((a-1)%32)) )
#define EnableSpecialFieldDebugIndex(a)        ( g_lSpecialFieldDebugState[((a-1)/32)] |= (1<<((a-1)%32)) )
#define DisableSpecialFieldDebugIndex(a)       ( g_lSpecialFieldDebugState[((a-1)/32)] &= ~(1<<((a-1)%32)) )

#define IsValidDebugLevel(a)            ( a > GST_LEVEL_NONE && a < GST_LEVEL_TRACE )

/**********************************************************************
*End of Field debugging command
**********************************************************************/
///////////////////////////////////////////////////////////////////////////////

//extern void *app_function (void *userdata);
//extern void set_ui_message (const gchar *message, CustomData *data);
//extern void check_initialization_complete (CustomData *data, int stream);
extern void init_custom_data(CustomData * cdata);
extern int build_audio_pipeline(gchar *encoding_name, CREGSTREAM *data, int do_rtp,GstElement **ele0,GstElement **sink);
extern int build_video_pipeline(gchar *encoding_name, CREGSTREAM *data, unsigned int start, int do_rtp,GstElement **ele0,GstElement **sink);
extern void build_http_pipeline(CREGSTREAM *data, int iStreamId);

extern void csio_PadAddedMsgHandler(GstElement *src, GstPad *new_pad, void *pCstreamer);

extern void set_gst_debug_level(void);

extern guint64 amcviddec_min_threshold_time ;
extern int debug_blocking_audio;
#endif
