#ifndef __CREGSTPLAY_H__
#define __CREGSTPLAY_H__

///////////////////////////////////////////////////////////////////////////////

#include <jni.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <pthread.h>
#include <time.h>
#include <errno.h>
#include <gst/gst.h>
#include <gst/video/video.h>
#include <gst/rtsp/gstrtsptransport.h>
#include <gst/app/gstappsink.h>
#include <stdbool.h>
#include "csioCommonShare.h"
#include "cresStreamOut_Common_Def.h"
///////////////////////////////////////////////////////////////////////////////

GST_DEBUG_CATEGORY_STATIC (debug_category);
#define GST_CAT_DEFAULT debug_category

#define MAX_STREAMS 4
#define MAX_ELEMENTS 20

#define DEFAULT_AMCVIDDEC_TS_OFFSET 0//in ms
#define DEFAULT_MPEG_TS_OFFSET	150
#define DEFAULT_MIN_STRING_BUFFER 25//in ms
#define DEFAULT_MAX_STRING_BUFFER 5000//in ms
#define DEFAULT_UDP_BUFFER 15728640 //in bytes (15 Mb)
//#define INSERT_SF_SINK 1
///////////////////////////////////////////////////////////////////////////////
#define TIMEOUT_TIME 1000   //for 1s
#define MAX_NAMESIZE 40
#define MAX_TIMESTAMPARRAY 1000

typedef struct
{
    guint m_type;
    GstElement *m_filter;
    guint overruncount;
    guint underruncount;
    gchar m_queue_name[MAX_NAMESIZE];
    guint m_queue_probId;
    GstClockTime m_Timestamp[MAX_TIMESTAMPARRAY];
    guint m_TsIndex;
} CRESTELEINFO;

typedef struct
{
	unsigned int x;
	unsigned int y;
	unsigned int width;
	unsigned int height;
} CRESTWINDOW;

typedef struct
{
	GstElement *pipeline;
	GstBus *bus;
	GMainLoop *loop;
	char *pipelineString;
	pthread_t threadID;
} DEBUGLAUNCH;

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
	GstElement *app_sink; 
	GstElement *amcvid_dec;
	GstElement *element_fake_dec;
	GstElement *element_valve_v;
	GstElement *element_valve_a;
	GstElement *element_audiorate;
	GstElement *element_video_front_end_queue;
	GstElement *element_video_decoder_queue;
	GstElement *element_audio_front_end_queue;
	GstElement *element_audio_decoder_queue;
	
	GstElement *element_zero;
	GstElement *element_av [MAX_ELEMENTS];
	GstElement *element_a [MAX_ELEMENTS];
	GstElement *element_v [MAX_ELEMENTS];
	guint       av_index;
	GstElement *demux;
	GstElement *typefind;


   GstElement * element_appsrc;


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
    bool dropAudio;
    double audioVolume;
    gboolean mpegtsPresent;
    eProtocolId	streamProtocolId;
    gboolean doHdcp;

	GSource * g_timer_source;
	guint m_elem_prob_id[MAX_ELEMENTS];
	CRESTELEINFO videoQueues[MAX_ELEMENTS];//shared with audio

	guint udpsrc_prob_id;
	GstElement *udpsrc_prob_element;
	struct timespec udpsrc_prob_timer;
	char sourceIP_addr[32];
	char rtcp_dest_ip_addr[32];
	int rtcp_dest_port;

	guint video_rate_probe_id;
	GstElement *video_rate_probe_element;
    bool isStarted;
    eHttpMode httpMode;
    bool hls_started;
    bool has_typefind;
    bool set_video;
    bool set_audio;
    void *pStreamer;
    bool useSWdecoder;

    DEBUGLAUNCH debug_launch;	// A structure to hold info for the debug launch pipeline

    int packetizer_pcr_discont_threshold;


    unsigned int ssrc;

    int wfd_jitterbuffer_latency;

} CREGSTREAM;

/* Structure to contain all our information, so we can pass it to callbacks */
typedef struct _CustomData 
{
	jobject app;            /* Application instance, used to call its methods. A global reference is kept. */
	
    CREGSTREAM stream[MAX_STREAMS];
} CustomData;

/**********************************************************************
* for stream out
**********************************************************************/
/* per-stream info */
typedef struct _CRESSTREAMOUT
{
    unsigned int streamId;

    char rtsp_port[125];
    char res_x[125];
    char res_y[125];
    char frame_rate[125];
    char bitrate[125];
    char iframe_interval[125];
    bool multicast_enable;
    char multicast_address[256];
    char stream_name[256];
    char snapshot_name[256];

} CRESSTREAMOUT;

/* Structure to contain all our information, so we can pass it to callbacks */
typedef struct _CustomStreamOutData
{
    jobject app;            /* Application instance, used to call its methods. A global reference is kept. */

    //void * surface;           /* not sure yet */

    CRESSTREAMOUT streamOut[MAX_STREAMS];
} CustomStreamOutData;

/**********************************************************************
* Field debugging command
**********************************************************************/
enum
  {
      FIELD_DEBUG_BLOCK_AUDIO = 1,                     //1
      FIELD_DEBUG_INSERT_VIDEO_PROBE ,
      FIELD_DEBUG_PRINT_PROBE_TS ,
      FIELD_DEBUG_SET_PIPELINE_TO_PAUSE ,
      FIELD_DEBUG_FLUSH_PIPELINE ,
      FIELD_DEBUG_SET_AMCVIDDEC_DEBUG_LEVEL ,
      FIELD_DEBUG_SET_VIDEODECODER_DEBUG_LEVEL ,
      FIELD_DEBUG_SET_OPENSLESSINK_DEBUG_LEVEL ,
      FIELD_DEBUG_SET_CATEGORY_DEBUG_LEVEL,
      FIELD_DEBUG_SET_AUDIOSINK_TS_OFFSET,
      FIELD_DEBUG_SET_AMCVIDDEC_TS_OFFSET,
      FIELD_DEBUG_PRINT_AUDIOSINK_PROPERTIES,
      FIELD_DEBUG_PRINT_ELEMENT_PROPERTY,
      FIELD_DEBUG_PRINT_A_V_DEBUG,
      FIELD_DEBUG_INSERT_TIME_CB,
      FIELD_DEBUG_NOT_PROCESS_RTCP,
      FIELD_DEBUG_SET_BASETIME_OFFSET,
      FIELD_DEBUG_SET_SEEK_EVENT,
      FIELD_DEBUG_SET_AUDIO_PAD_OFFSET,
      FIELD_DEBUG_DROP_AUDIO_PACKETS,
      FIELD_DEBUG_INSERT_AUDIO_PROBE,
      FIELD_DEBUG_PRINT_BACKWDS_TS ,      

      //this should the last item
      MAX_SPECIAL_FIELD_DEBUG_NUM              //
  };
//Verification macro
#define IsValidSpecialFieldIndex(a)            ( a > 0 && a < MAX_SPECIAL_FIELD_DEBUG_NUM )

//Field debug UINT32 array size
#define SPECIAL_FIELD_DEBUG_ARRAY_SIZE         ( ((MAX_SPECIAL_FIELD_DEBUG_NUM-1)/32)+1 )

void csio_jni_printFieldDebugInfo();

//Field debug macros
#define IsSpecialFieldDebugIndexActive(a)      ( g_lSpecialFieldDebugState[((a-1)/32)] & (1<<((a-1)%32)) )
#define EnableSpecialFieldDebugIndex(a)        ( g_lSpecialFieldDebugState[((a-1)/32)] |= (1<<((a-1)%32)) )
#define DisableSpecialFieldDebugIndex(a)       ( g_lSpecialFieldDebugState[((a-1)/32)] &= ~(1<<((a-1)%32)) )

#define IsValidDebugLevel(a)            ( a > GST_LEVEL_NONE && a < GST_LEVEL_MAX )

/**********************************************************************
*End of Field debugging command
**********************************************************************/
///////////////////////////////////////////////////////////////////////////////

//void *app_function (void *userdata);
//void set_ui_message (const gchar *message, CustomData *data);
//void check_initialization_complete (CustomData *data, int stream);
void clearMetadataConnections();
void init_custom_data(CustomData * cdata);
int build_audio_pipeline(gchar *encoding_name, CREGSTREAM *data, int do_rtp,GstElement **ele0,GstElement **sink);
int build_video_pipeline(gchar *encoding_name, CREGSTREAM *data, unsigned int start, int do_rtp,GstElement **ele0,GstElement **sink);
int build_metadata_pipeline(CREGSTREAM *data, GstElement **sink);
void build_http_pipeline(CREGSTREAM *data, int iStreamId);
extern "C" void insert_udpsrc_probe(CREGSTREAM *data,GstElement *element,const gchar *name);
extern "C" void csio_jni_remove_video_rate_probe(int iStreamId);

void csio_PadAddedMsgHandler(GstElement *src, GstPad *new_pad, void *pCstreamer);
void csio_Adaptive_PadAddedMsgHandler( GstElement *src, GstPad *new_pad, CREGSTREAM *data );
void csio_DecVideo1stOutputCB(GstElement *src,int id);
void csio_jni_callback_rtpbin_new_jitterbuffer(GstElement *rtpbin,GstElement *jitterbuffer,guint session,guint ssrc,gpointer data);
GstElement* csio_jni_callback_rtpbin_new_rtp_decoder(GstElement *rtpbin,guint session,gpointer data);

// Includes for csioutils.h 
typedef gint gboolean;
void     csio_send_stats (uint64_t video_packets_received, int video_packets_lost, uint64_t audio_packets_received, int audio_packets_lost, uint16_t bitrate) ;
GstStateChangeReturn csio_element_set_state(GstElement *element, GstState state, gboolean bFileUri = false);
void csio_SetWaitDecHas1stVidDelay(int iStreamId,int v);
int  csio_GetWaitDecHas1stVidDelay(int iStreamId);
int  csio_jni_AddAudio(GstPad *,gchar *, GstElement **,bool, int stream);
int  csio_jni_AddVideo(GstPad *,gchar *, GstElement **,eProtocolId,bool, int stream);
void csio_jni_initVideo(int stream);
void csio_SetVpuDecoder(GstElement *vpuDec, int iStreamId);
int  csio_SetAudioProbeId(int iStreamId,uint Id);
int  csio_SetVideoProbeId(int iStreamId,uint Id);
void 	csio_SendVideoSourceParams (unsigned int source, unsigned int width, unsigned int height, unsigned int framerate, unsigned int profile);
int      csio_SendVideoPlayingStatusMessage(UINT32 source, eStreamState state);
void csio_SetRtspNetworkMode(int iStreamId, unsigned char ucMode);
void csio_jni_recoverDucati();
void csio_jni_initAudio(int iStreamId);
void csio_SetAudioSink(GstElement *audioSink, int iStreamId);
void csio_SaveNetworkProtocol(int iStreamId);
void csio_SendVideoInfo(int id, GstElement *element);
void csio_jni_change_queues_to_leaky(int id);
void csio_jni_setAutoBitrate(int id);



void set_gst_debug_level(void);

extern guint64 amcviddec_min_threshold_time ;
extern int debug_blocking_audio;
extern UINT32 g_lSpecialFieldDebugState[SPECIAL_FIELD_DEBUG_ARRAY_SIZE];
#endif
