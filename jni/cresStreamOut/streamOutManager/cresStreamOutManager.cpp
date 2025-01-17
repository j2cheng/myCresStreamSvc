/**
 * Copyright (C) 2016 to the present, Crestron Electronics, Inc.
 * All rights reserved.
 * No part of this software may be reproduced in any form, machine
 * or natural, without the express written consent of Crestron Electronics.
 *
 * \file        cresStreamOutManager.cpp
 *
 * \brief       Implementation of stream out
 *
 * \author      John Cheng
 *
 * \date        7/5/2016
 *
 * \note
 */

///////////////////////////////////////////////////////////////////////////////

#include <cassert>
#include <stdlib.h>
#include <fcntl.h>
#include <ctime>
#include <unistd.h>
#include <pthread.h>
#include "cresStreamOutManager.h"
#include "gst/app/gstappsrc.h"
#include "v4l2Video.h"
#include "../../shared-ssl/shared-ssl.h"

#undef NANOPC

#undef USE_VIDEOTESTSRC
#undef USE_AUDIOTESTSRC
#undef CLIENT_AUTHENTICATION_ENABLED
#undef SHOW_PEER_CERTIFICATE

//static bool PushModel = false;
int useUsbAudio = false;
int videoDumpCount = 0;
extern int wcVideoEncDumpEnable;
extern int wcAudioEncDumpEnable;
extern int wcAudioStatsEnable;
extern int wcAudioStatsReset;
extern int wcJpegStatsEnable;
extern int wcJpegStatsReset;
extern int wcJpegRateControl;
extern int wcJpegDynamicFrameRateControl;
extern int wcVideoQueueMaxTime;
extern int wcShowVideoQueueOverruns;
char encoded_frame_rate[20] = {'1', '5', '/', '1', '\0'};

//#define AUDIOENC "amcaudenc-omxgoogleaacencoder"
#define AUDIOENC "voaacenc"

#define CRES_OVERLOAD_MEDIA_CLASS 1
#define CRES_UNPREPARE_MEDIA      1
#define MAX_RTSP_SESSIONS         5
#define	UNLIMITED_RTSP_SESSIONS   0

#define WC_RTP_PORT_MIN 9010
#define WC_RTP_PORT_MAX 9020

#define MAX_NUMBER_OF_FRAMES 150
#define VIDEO_DUMP_FILE "/logs/videoencdata.h264"
#define AUDIO_DUMP_FILE "/logs/audioencdata.mp4"

extern const char *csio_jni_getAppCacheFolder();
extern const char *csio_jni_getHostName();
extern const char *csio_jni_getDomainName();
extern const char *csio_jni_getServerIpAddress();
extern void csio_jni_SendWCMediaError( int errModule, int errCode, const char* errMessage);
extern void csio_jni_SendWCServerURL( void * arg );
extern void csio_jni_onServerStart();
extern void csio_jni_onServerStop();
extern void csio_jni_onClientConnected(void * arg);
extern void csio_jni_onClientDisconnected(void * arg);
extern void csio_jni_reset_hdmi_input();

static void cb_queueOverruns(void *queue, gpointer user_data);
static void cb_queueUnderruns(void *queue, gpointer user_data);
static void cb_vidEncQueueOverruns(void *queue, gpointer user_data);
static void cb_vidEncQueueUnderruns(void *queue, gpointer user_data);

static bool jpegPassthrough = false;
static int jpegQuality = 85;
extern int wcJpegQuality;
extern int wcJpegRateBitsPerMsec;
extern bool wcIsTx3Session;

static int frameDrop = 1; // drop frameDrop-1 frames out of frameDrop frames
static int nFramesDropped = 0;
static void resetFrameDrop() { frameDrop = 1; nFramesDropped = 0;}

#define LEAKYSTR(v) (((v) == 0) ? "None" : (((v) == 1) ? "Upstream" : "Downstream"))

int read_int_from_file(const char *filePath, int defaultValue)
{
    FILE * file;
    int rv = defaultValue;

    file = fopen(filePath, "r");
    if (file != NULL)
    {
        int errorCode = 0;
        if (fscanf(file, "%d", &(errorCode)) >= 0)
        {
            rv = errorCode;
        }

        fclose(file);
    }
    return rv;
}

static int read_string_from_file(char *string, int stringSize, const char *filePath)
{
    FILE * file;
    int rv = -1;
    string[0] = '\0';

    file = fopen(filePath, "r");
    if (file != NULL) {
        if (fgets(string, stringSize, file) != NULL) {
            size_t len = strlen(string) - 1;
            if (string[len] == '\n')
                string[len] = '\0';
            rv = 0;
        }
        fclose(file);
    }
    return rv;
}

#ifdef USE_DECIMATION_RATE_FILE
static int get_decimation_rate_requested()
{
    return read_int_from_file("/dev/shm/crestron/CresStreamSvc/wc/decimation_rate", 0);
}
#else
static int get_framerate_requested(char *framerate, int size)
{
    return read_string_from_file(framerate, size, "/dev/shm/crestron/CresStreamSvc/wc/framerate");
}
#endif

static int get_bitrate_requested()
{
    return read_int_from_file("/dev/shm/crestron/CresStreamSvc/wc/bitrate", 0);
}

static int get_dscp_qos()
{
        return read_int_from_file("/dev/shm/crestron/CresStreamSvc/wc/dscp", -1);
}
int get_encoded_video_rate(VideoCaps *pCaps, int *fps_num, int *fps_den)
{
    int iRet = 0;
    *fps_den = 1;
#ifdef USE_DECIMATION_RATE_FILE
    int decimation_rate = get_decimation_rate_requested();
    if (decimation_rate > 0) {
        CSIO_LOG(eLogLevel_info, "--Streamout - encoder decimation rate requested=%d", decimation_rate);
        *fps_num = pCaps->frame_rate_num;
        *fps_den = decimation_rate;
    } else {
        *fps_num = pCaps->frame_rate_num;
        *fps_den = 1;
    }
#else
    char framerate[128];
    char delim[] = "/";
    char *token;
    if (get_framerate_requested(framerate, sizeof(framerate)) >= 0) {
        CSIO_LOG(eLogLevel_info, "--Streamout - encoder frame rate requested=%s", framerate);
        /* get the first token */
        token = strtok(framerate, delim);
        if( token != NULL )
        {
            *fps_num = atoi(token);
            token = strtok(NULL, delim);
            if( token != NULL )
            {
                *fps_den = atoi(token);
            }
        }
    } else {
        sscanf(encoded_frame_rate, "%d/%d", fps_num, fps_den);
    }
#endif
    return iRet;
}
static bool
is_supported(const char *fourcc)
{
	//TODO figure out why other formats do not work without videoconvert
	const char *formats[] = {/*"YUY2", "UYVY",*/ "I420", "NV12", NULL};
	for (int i=0; formats[i]; i++)
	{
		if (strcmp(fourcc, formats[i]) == 0)
			return true;
	}
	return false;
}

static gboolean
timeout (GstRTSPServer * server)
{
    GstRTSPSessionPool *pool;

    CSIO_LOG(eLogLevel_verbose, "--------------- In timeout() -----------------------");
    pool = gst_rtsp_server_get_session_pool (server);
    guint removed = gst_rtsp_session_pool_cleanup (pool);
    g_object_unref (pool);
    if (removed)
    	CSIO_LOG(eLogLevel_info, "Removed %d sessions", removed);

    return TRUE;
}

static
void getClientIp(GstRTSPClient * client, char *ipAddrBuf, int bufSize)
{
	ipAddrBuf[0] = '\0';
	if (client) {
		const GstRTSPConnection *client_connection = gst_rtsp_client_get_connection(client);
		if (client_connection)
		{
			const gchar *clientIpAddress = gst_rtsp_connection_get_ip(client_connection);
			if (clientIpAddress != NULL) {
				strncpy(ipAddrBuf, clientIpAddress, bufSize);
			}
		}
	}
}

static GstRTSPResult
dump_rtsp_message (GstRTSPMessage * msg)
{
  g_return_val_if_fail (msg != NULL, GST_RTSP_EINVAL);
  int logLevel = eLogLevel_info;

  switch (msg->type) {
    case GST_RTSP_MESSAGE_REQUEST:
      CSIO_LOG(logLevel,"RTSP request message %p", msg);
      CSIO_LOG(logLevel," request line:");
      CSIO_LOG(logLevel,"   method: '%s'",
          gst_rtsp_method_as_text (msg->type_data.request.method));
      CSIO_LOG(logLevel,"   uri:    '%s'", msg->type_data.request.uri);
      CSIO_LOG(logLevel,"   version: '%s'",
          gst_rtsp_version_as_text (msg->type_data.request.version));
      break;
    case GST_RTSP_MESSAGE_RESPONSE:
      CSIO_LOG(logLevel,"RTSP response message %p", msg);
      CSIO_LOG(logLevel," status line:");
      CSIO_LOG(logLevel,"   code:   '%d'", msg->type_data.response.code);
      CSIO_LOG(logLevel,"   reason: '%s'", msg->type_data.response.reason);
      CSIO_LOG(logLevel,"   version: '%s'",
          gst_rtsp_version_as_text (msg->type_data.response.version));
      break;
    case GST_RTSP_MESSAGE_DATA:
      CSIO_LOG(logLevel,"RTSP data message %p", msg);
      CSIO_LOG(logLevel," channel: '%d'", msg->type_data.data.channel);
      CSIO_LOG(logLevel," size:    '%d'", msg->body_size);
      break;
    default:
      CSIO_LOG(eLogLevel_warning,"unsupported message type %d", msg->type);
      return GST_RTSP_EINVAL;
  }
  return GST_RTSP_OK;
}

static void
client_closed (GstRTSPClient * client, void *user_data)
{
    CStreamoutManager *pMgr = (CStreamoutManager *) user_data;
    char clientIpAddress[64];

    for (std::list<RtspClient*>::iterator it = pMgr->m_clientList.begin(); it != pMgr->m_clientList.end(); it++)
    {
    	if ((*it)->client == client)
    	{
    		strncpy(clientIpAddress, (*it)->client_ip_address, sizeof(clientIpAddress));
    	    csio_jni_onClientDisconnected(clientIpAddress);
    		delete *it;
    		pMgr->m_clientList.erase(it);
    		break;
    	}
    }
//    if (PushModel && pMgr->m_clientList.size() == 0)
//        pMgr->m_bNeedData = false;
    CSIO_LOG(eLogLevel_info,"********** RTSP Client %s closed (size=%d) ****************", clientIpAddress, pMgr->m_clientList.size());
}

#if 0
static void
client_rtsp_session(GstRTSPClient * client, GstRTSPContext * ctx, gpointer user_data)
{
    GstRTSPMessage * request = ctx->request;
    CSIO_LOG(eLogLevel_info,"client_rtsp_session RTSP Message: %d", gst_rtsp_message_get_type (request));
}
#endif

static void  describe_request_cb (GstRTSPClient * self,
                           GstRTSPContext * ctx,
                           gpointer user_data)
{
    CSIO_LOG(eLogLevel_info,"RTSP Message: 'DESCRIBE' request");
    dump_rtsp_message (ctx->request);
}

static void  play_request_cb (GstRTSPClient * self,
                           GstRTSPContext * ctx,
                           gpointer user_data)
{
    CSIO_LOG(eLogLevel_info,"RTSP Message: 'PLAY' request");
    dump_rtsp_message (ctx->request);
}

static void  setup_request_cb (GstRTSPClient * self,
                           GstRTSPContext * ctx,
                           gpointer user_data)
{
    CSIO_LOG(eLogLevel_info,"RTSP Message: 'SETUP' request");
    dump_rtsp_message (ctx->request);
}

static void options_request_cb(GstRTSPClient * self,
                          GstRTSPContext * ctx,
                          gpointer user_data)
{
    CSIO_LOG(eLogLevel_info,"RTSP Message: 'OPTIONS' request");
    dump_rtsp_message (ctx->request);
}

static void
client_connected (GstRTSPServer * server, GstRTSPClient * client, void *user_data)
{
    CStreamoutManager *pMgr = (CStreamoutManager *) user_data;
    RtspClient *e = new RtspClient(client);

    getClientIp(client, e->client_ip_address, sizeof(e->client_ip_address));
    csio_jni_onClientConnected(e->client_ip_address);

    g_signal_connect(client, "closed", (GCallback) client_closed, pMgr);
    g_signal_connect(client, "options-request", (GCallback) options_request_cb, pMgr);
    g_signal_connect(client, "describe-request", (GCallback) describe_request_cb, pMgr);
    g_signal_connect(client, "play-request", (GCallback) play_request_cb, pMgr);
    g_signal_connect(client, "setup-request", (GCallback) setup_request_cb, pMgr);

    pMgr->m_clientList.push_back(e);
    CSIO_LOG(eLogLevel_info,"********** RTSP Client connected (size=%d  ip=%s e=0x%p) **************", pMgr->m_clientList.size(),
            e->client_ip_address, e);
}

#ifdef SHOW_PEER_CERTIFICATE
static char *sgets(char *s, int n, char **strp){
    if(**strp == '\0')return NULL;
    int i;
    for(i=0;i<n-1;++i, ++(*strp)){
        s[i] = **strp;
        if(**strp == '\0')
            break;
        if(**strp == '\n'){
            s[i+1]='\0';
            ++(*strp);
            break;
        }
    }
    if(i==n-1)
        s[i] = '\0';
    return s;
}
#endif

static gboolean
accept_certificate (GstRTSPAuth *auth,
                    GTlsConnection *conn,
                    GTlsCertificate *peer_cert,
                    GTlsCertificateFlags errors,
                    gpointer user_data) {
    assert(auth);
    assert(conn);
    assert(peer_cert);
    assert(errors);
    CSIO_LOG(eLogLevel_info, "%s: auth=%p conn=%p peer_cert=%p errors=%x user_data=%p", __func__, auth, conn, peer_cert, errors, user_data);
#ifdef SHOW_PEER_CERTIFICATE
    gchar *cert_pem = NULL;
    g_object_get(peer_cert, "certificate-pem", &cert_pem, NULL);
    if (cert_pem != NULL)
    {
        char **p = &cert_pem;
        char buf[80];
        while (sgets(buf, sizeof(buf), p) != NULL)
        {
            CSIO_LOG(eLogLevel_info, "client cert=%s", buf);
        }
        g_free(cert_pem);
    }
#endif
#ifdef CLIENT_AUTHENTICATION_ENABLED
    GError *error;
    gboolean accept = FALSE;
    GTlsCertificate *ca_cert = (GTlsCertificate *) user_data;

    GTlsDatabase* database = g_tls_connection_get_database(G_TLS_CONNECTION(conn));
    if (database) {
        GSocketConnectable *peer_identity;
        GTlsCertificateFlags validation_flags;
        CSIO_LOG(eLogLevel_debug, "TLS peer certificate not accepted, checking user database...");
        peer_identity = NULL;
        errors =
                g_tls_database_verify_chain (database, peer_cert,
                                             G_TLS_DATABASE_PURPOSE_AUTHENTICATE_CLIENT, peer_identity,
                                             g_tls_connection_get_interaction (conn), G_TLS_DATABASE_VERIFY_NONE,
                                             NULL, &error);
        CSIO_LOG(eLogLevel_info, "*******accept-certificate******* errors value %x", errors);

        // Alternate verification
        if (errors == 0) {
            GTlsCertificateFlags flags = g_tls_certificate_verify(peer_cert, peer_identity, ca_cert);
            if (flags != 0)
            {
                CSIO_LOG(eLogLevel_info, "certificates did not verify susccessfully  flags=0x%x", flags);
                errors = flags;
            }
        }
        if (error)
        {
            CSIO_LOG(eLogLevel_warning, "failure verifying certificate chain: %s",
                       error->message);
            g_assert (errors != 0);
            g_clear_error (&error);
        }
    }

    if (errors == 0 && error == NULL) {
        CSIO_LOG(eLogLevel_info, "accept-certificate returning true");
        return TRUE;
    }
    CSIO_LOG(eLogLevel_info, "accept-certificate returning false");
    return FALSE;
#else
    CSIO_LOG(eLogLevel_info, "accept-certificate returning true");
    return TRUE;
#endif
}

///////////////////////////////////////////////////////////////////////////////
// Allow file to override canned pipeline, for debugging...
// The following pipeline worked!
// ( videotestsrc is-live=1 ! jpegenc ! rtpjpegpay name=pay0 pt=96 )
static bool gst_rtsp_server_get_pipeline(char * pDest, int destSize)
{
    FILE * pf;

    pf = fopen("/dev/shm/rtsp_server_pipeline", "r");
    if(pf)
    {
        fgets(pDest, destSize, pf);
        fclose(pf);
        return true;
    }

    return false;
}

static void set_max_priority()
{
    pthread_t thId = pthread_self();
    const char *wcAudioInput = "wcAudioInput";

    pthread_setname_np(thId, wcAudioInput);

    // Disabling since it seems we can only use policy NORMAL from CSS and min and max priorities are 0 for this policy
    // Other policies result int error code -1 (no permission) coming from pthread_setschedparam
    // can be done through csio if needed by sending command to csio
#if 0
    pthread_attr_t thAttr;
    int policy = 0;
    int max_prio_for_policy = 0;
    int min_prio_for_policy = 0;
    int rv = 0;
    struct sched_param param;

    pthread_attr_init(&thAttr);
    pthread_attr_getschedpolicy(&thAttr, &policy);
    CSIO_LOG(eLogLevel_debug, "Streamout: audio policy is %d  NORMAL=%d FIFO=%d RR=%d", max_prio_for_policy, SCHED_NORMAL, SCHED_FIFO, SCHED_RR);

    policy = SCHED_RR;
    min_prio_for_policy = sched_get_priority_min(policy);
    max_prio_for_policy = sched_get_priority_max(policy);
    CSIO_LOG(eLogLevel_debug, "Streamout: priority range is %d to %d", min_prio_for_policy, max_prio_for_policy );
    param.sched_priority = max_prio_for_policy;

    CSIO_LOG(eLogLevel_debug, "Streamout: setting policy to %d audio input thread priority to %d", policy, max_prio_for_policy);
    rv = pthread_setschedparam(thId, policy, &param);
    if (rv != 0)
    {
        CSIO_LOG(eLogLevel_debug, "Streamout: error setting policy and priority!!!! - err=%d", rv);
    }
    //pthread_setschedprio(thId, max_prio_for_policy);
    pthread_attr_destroy(&thAttr);
#endif
#if 0
    void csio_jni_executeRootCommand(char *command);
    int tid = gettid();
    char command[128]={0};
    snprintf(command,sizeof(command),"/system/bin/busybox chrt -r -p 70 %d", tid);
    csio_jni_executeRootCommand(command);
#endif
}

static struct {
    gint64 minInterval;
    gint64 maxInterval;
    GstClockTime totalTime;
    unsigned int count;
    GstClockTime prevTime;
} audioStats = {0,0,0,0,0};

static void clear_audio_stats()
{
    memset(&audioStats, 0, sizeof(audioStats));
    audioStats.minInterval = 0x7fffffffffffffffLL;
    audioStats.maxInterval = 0x0000000000000000LL;
    CSIO_LOG(eLogLevel_debug, "Streamout: clear_audio_stats: count=%d min=%lld, max=%lld",
                audioStats.count, audioStats.minInterval, audioStats.maxInterval);
}

static void audio_stats(int size, GstClockTime total)
{
    if (wcAudioStatsReset)
    {
        CSIO_LOG(eLogLevel_debug, "Streamout: audio_stats: resetting audio stats");
        clear_audio_stats();
        wcAudioStatsReset = 0;
    }
    bool minmax_change = false;
    if (audioStats.prevTime == 0)
    {
        CSIO_LOG(eLogLevel_debug, "Streamout: audio_stats: first entry initTime = %" GST_TIME_FORMAT, GST_TIME_ARGS (total));
        audioStats.prevTime = total;
        set_max_priority();
        return;
    }

    gint64 delta = (total - audioStats.prevTime);
    audioStats.count++;
    audioStats.prevTime = total;
    audioStats.totalTime += delta;
    if (delta < audioStats.minInterval)
    {
        minmax_change = true;
        audioStats.minInterval = delta;
    }
    if (delta > audioStats.maxInterval)
    {
        minmax_change = true;
        audioStats.maxInterval = delta;
    }
    if (minmax_change || (audioStats.count%10000 == 0))
        CSIO_LOG(eLogLevel_debug, "Streamout: audio_stats: time=%" GST_TIME_FORMAT " count=%d delta=%lld   min=%lld, max=%lld avg=%lld",
                GST_TIME_ARGS (total), audioStats.count, delta, audioStats.minInterval, audioStats.maxInterval, audioStats.totalTime/audioStats.count);
}

void cb_srcNeedData (GstElement *appsrc, guint unused_size, gpointer user_data)
{
//    CSIO_LOG(eLogLevel_verbose, "Streamout: cb_need_data...");
	CStreamoutManager *pMgr = (CStreamoutManager *) user_data;

	pMgr->m_bNeedData = true;
}

void cb_srcEnoughData (GstElement *appsrc, guint unused_size, gpointer user_data)
{
	//CSIO_LOG(eLogLevel_verbose, "Streamout: cb_enough_data...");
	CStreamoutManager *pMgr = (CStreamoutManager *) user_data;

	pMgr->m_bNeedData = false;
}

/* called when a new media pipeline is constructed. We can query the
 * pipeline and configure our media src */
static void
media_configure (GstRTSPMediaFactory * factory, GstRTSPMedia * media,
    gpointer user_data)
{
	CStreamoutManager *pMgr = (CStreamoutManager *) user_data;
	GstElement *element;
	/* get the element used for providing the streams of the media */
	element = gst_rtsp_media_get_element (media);
	gchar * n = gst_element_get_name(element);

    assert(element);
    assert(n);

	CSIO_LOG(eLogLevel_debug, "Streamout: media_configure element name[%s] of media[0x%p]",n,media);

  //set up video source
	{
		int width  = atoi(pMgr->m_res_x);
		int height = atoi(pMgr->m_res_y);
		int framerate = atoi(pMgr->m_frame_rate);

        GstElement *ele = gst_bin_get_by_name_recurse_up (GST_BIN (element), "cresappsrc");
		if(ele)
		{
			CSIO_LOG(eLogLevel_verbose, "Streamout: get_by_name cresappsrc[%p]",ele);
			pMgr->m_camera = ele;

//TODO: save CAPS from camera
			g_object_set(G_OBJECT(ele), "caps",
			gst_caps_new_simple(
			"video/x-raw",
			"format",    G_TYPE_STRING, "NV21",
			"width",     G_TYPE_INT, width,
			"height",    G_TYPE_INT, height,
			"framerate", GST_TYPE_FRACTION, 0, 1, //variable
			NULL), NULL);

			g_object_set( G_OBJECT(ele), "stream-type", 0, NULL );
			g_object_set( G_OBJECT(ele), "format", GST_FORMAT_TIME, NULL );

			g_signal_connect( ele, "need-data",   G_CALLBACK (cb_srcNeedData),   user_data );
			g_signal_connect( ele, "enough_data", G_CALLBACK (cb_srcEnoughData), user_data );
			gst_object_unref(ele);
		}

		ele = gst_bin_get_by_name_recurse_up (GST_BIN (element), "vparser");
		if(ele)
		{
			CSIO_LOG(eLogLevel_verbose, "Streamout: get_by_name vparser[%p]",ele);
			g_object_set( G_OBJECT(ele), "width",  width, NULL );
			g_object_set( G_OBJECT(ele), "height", height, NULL );
			g_object_set( G_OBJECT(ele), "format", GST_VIDEO_FORMAT_NV21, NULL );
			g_object_set( G_OBJECT(ele), "framerate", framerate,1, NULL );
			gst_object_unref(ele);
		}
	}

	CSIO_LOG(eLogLevel_debug, "Streamout: set media reusable to true media[%p]",media);
	gst_rtsp_media_set_reusable (media, TRUE);

	//pass media back to manager
	pMgr->m_pMedia = media;

	((CresRTSPMedia *)media)->m_loop = pMgr->m_loop;
    if(pMgr->m_streamoutMode == STREAMOUT_MODE_CAMERA) pMgr->m_bPushRawFrames = true;

	gst_object_unref (element);
	g_free(n);
}

void get_audio_data_for_pull(CStreamoutManager *pMgr, guint size)
{
    CSIO_LOG(eLogLevel_verbose, "Streamout: get_audio_data...size=%d", size);
    GstFlowReturn ret;

    GstBuffer *buffer = gst_buffer_new_allocate(NULL, size, NULL);
    GstMapInfo mapInfo;
    GstClockTime timestamp=0, duration=0;
    int channels = pMgr->m_usbAudio->getAudioChannels();
    if (gst_buffer_map(buffer, &mapInfo, GST_MAP_WRITE)) {
    	pMgr->m_usbAudio->usb_audio_get_samples(pMgr->m_usbAudio->m_device, mapInfo.data, mapInfo.size, channels, &timestamp, &duration);

        if(pMgr->m_appsrc)
        {
            GstState cur_state;
            gst_element_get_state (GST_ELEMENT (pMgr->m_appsrc), &cur_state, NULL, 0);
            CSIO_LOG(eLogLevel_extraVerbose, "m_appsrc state is %s", gst_element_state_get_name (cur_state));

            GstClock* appSrcClock  = gst_element_get_clock(pMgr->m_appsrc);

            if(appSrcClock)
            {
                GstClockTime base_time = gst_element_get_base_time (GST_ELEMENT_CAST (pMgr->m_appsrc));
                GstClockTime now_time  = gst_clock_get_time (appSrcClock);
                GstClockTime run_time  = 0;

                CSIO_LOG(eLogLevel_extraVerbose, "Streamout: get_audio_data_for_pull: appSrcClock[0x%p]",appSrcClock);

                if (now_time > base_time)
                    run_time = now_time - base_time;
                else
                    run_time = 0;

                CSIO_LOG(eLogLevel_extraVerbose, "Streamout: get_audio_data_for_pull: base_time %" GST_TIME_FORMAT " now_time %" GST_TIME_FORMAT " run_time %" GST_TIME_FORMAT,
                        GST_TIME_ARGS (base_time), GST_TIME_ARGS (now_time), GST_TIME_ARGS (run_time));

                CSIO_LOG(eLogLevel_verbose, "Streamout: sync: timestamp from %" GST_TIME_FORMAT " to run_time %" GST_TIME_FORMAT,
                        GST_TIME_ARGS (timestamp), GST_TIME_ARGS (run_time));

                //replace timestamp with run_time
                timestamp = run_time;

                gst_object_unref (appSrcClock);
            }
            else
            {
                CSIO_LOG(eLogLevel_verbose, "Streamout: get_audio_data_for_pull: no appSrcClock");
            }
        }
        else
        {
            CSIO_LOG(eLogLevel_verbose, "Streamout: get_audio_data_for_pull: no appSrcClock");
        }
    }
    else
    {
        CSIO_LOG(eLogLevel_error, "Streamout: ERROR gst_buffer_map failed!!");
    }
    gst_buffer_unmap(buffer, &mapInfo);
    GST_BUFFER_PTS(buffer) = timestamp;
    GST_BUFFER_DURATION(buffer) = duration;

    CSIO_LOG(eLogLevel_verbose, "Streamout: calling emit with timestamp %" GST_TIME_FORMAT " duration %" GST_TIME_FORMAT,
             GST_TIME_ARGS(timestamp),GST_TIME_ARGS(duration));

    g_signal_emit_by_name(pMgr->m_appsrc, "push_buffer", buffer, &ret);
    //ret = gst_app_src_push_buffer((GstAppSrc *)appsrc, buffer);
    if (ret == GST_FLOW_OK) {
        CSIO_LOG(eLogLevel_verbose, "Streamout: buffer pushed successfully");
    } else {
        CSIO_LOG(eLogLevel_error, "Streamout: pushed returned %d", (int) ret);
    }
    gst_buffer_unref(buffer);
    if (wcAudioStatsEnable)
        audio_stats(size, timestamp);
    CSIO_LOG(eLogLevel_verbose, "Streamout: get_audio_data_for_pull....exiting - pushed buffer of size %d", size);
}

void cb_wcSrcNeedData (GstElement *appsrc, guint size, gpointer user_data)
{
    CSIO_LOG(eLogLevel_verbose, "Streamout: cb_wcSrc_need_data...size=%d (pMgr=%p)", size, user_data);
    CStreamoutManager *pMgr = (CStreamoutManager *) user_data;

    pMgr->m_bNeedData = true;
    get_audio_data_for_pull(pMgr, size);
}

void cb_wcSrcEnoughData (GstElement *appsrc, gpointer user_data)
{
    CSIO_LOG(eLogLevel_info, "Streamout: cb_wcSrc_enough_data...(pMgr=%p)", user_data);
    CStreamoutManager *pMgr = (CStreamoutManager *) user_data;

    pMgr->m_bNeedData = false;
}

static guint64 getAudioChannelMask(int nchannels)
{
    return ((1ULL<<nchannels)-1);
}

static GstPadProbeReturn cb_dump_enc_data (
              GstPad          *pad,
              GstPadProbeInfo *info,
              gpointer         user_data)
{
    GstBuffer *buffer;
    GstMapInfo map;
    const guint8 *data;
    GstBuffer *codec_data = NULL;
    FILE *fp;
    //access delimiter
    char c[] = {0x00, 0x00, 0x00, 0x01, 0x09, 0xf0, 0x00, 0x00, 0x00, 0x01};
    GstRTSPMedia * media = (GstRTSPMedia *)user_data;
    CresRTSPMedia *cresRTSPMedia = (CresRTSPMedia *) media;

    // increase the decoded frame counter
    cresRTSPMedia->currEncFrameCount++;
    if( (cresRTSPMedia->currEncFrameCount % 512)  == 0) // 15 fps - log once in ~30 seconds
        CSIO_LOG(eLogLevel_debug, "Streamout: Video Frames Processed %lld", cresRTSPMedia->currEncFrameCount);

    if (jpegPassthrough)
    {
        GstPadProbeReturn rv = GST_PAD_PROBE_OK;
        if (wcJpegDynamicFrameRateControl)
        {
            buffer = GST_PAD_PROBE_INFO_BUFFER (info);
            guint sizeInBytes = gst_buffer_get_size(buffer);
            int rate = 8*sizeInBytes/(GST_BUFFER_DURATION(buffer)/1000000);
            int dropRate = 1 + rate/wcJpegRateBitsPerMsec;
            if (dropRate != frameDrop)
            {
                frameDrop = dropRate;
                CSIO_LOG(eLogLevel_debug, "Streamout: At frame %lld frameDrop changed to %d (rate=%d)",
                        cresRTSPMedia->currEncFrameCount, frameDrop, rate);
            }
        }
        if ((cresRTSPMedia->currEncFrameCount%frameDrop) != 0)
        {
            nFramesDropped++;
            rv = GST_PAD_PROBE_DROP;
        }

        if ((cresRTSPMedia->currEncFrameCount%1000) == 0)
            CSIO_LOG(eLogLevel_debug, "Streamout: At frame %lld dropped=%d (%d %%)", cresRTSPMedia->currEncFrameCount,
                    nFramesDropped, (int) ((100*nFramesDropped)/cresRTSPMedia->currEncFrameCount));

        return rv;
    }

    if( wcVideoEncDumpEnable == 1 )
    {
        if( videoDumpCount == 0 )
        {
            CSIO_LOG(eLogLevel_debug, "Streamout: Started to dump encoded data to %s file", VIDEO_DUMP_FILE);
            //get sps and pps
            GstStructure *s;
            GstCaps *caps = gst_pad_get_current_caps (pad);
            if( caps == NULL )
                return GST_PAD_PROBE_OK;

            s = gst_caps_get_structure (caps, 0);
            if( s == NULL )
                return GST_PAD_PROBE_OK;


            gst_structure_get (s, "codec_data", GST_TYPE_BUFFER, &codec_data, NULL);

            if (!codec_data) {
                CSIO_LOG(eLogLevel_debug, "codec_data not present and hence video dumo is not possible");
            }
            else
            {
                GstMapInfo map;
                guint8 *data;
                guint size;
                gst_buffer_map (codec_data, &map, GST_MAP_READ);
                data = map.data;
                size = map.size;

                fp = fopen(VIDEO_DUMP_FILE, "w");
                if( fp != NULL )
                {

                    // codec data has the required sps and pps. Store starting of the file.
                    fwrite(c, 1, sizeof(c), fp);
                    //write sps data
                    for(unsigned int i= 8; i < size-7; i++)
                    {
                        fwrite(&data[i], 1, 1, fp);
                    }
                    fwrite(c, 1, sizeof(c), fp);
                    //write pps
                    for(unsigned int i= size-4; i < size; i++)
                    {
                        fwrite(&data[i], 1, 1, fp);
                    }
                    fclose(fp);
                    gst_buffer_unmap (codec_data, &map);
                    gst_buffer_unref (codec_data);
                }
            }
        }

        if( videoDumpCount < MAX_NUMBER_OF_FRAMES )
        {
            unsigned int i;
            buffer = GST_PAD_PROBE_INFO_BUFFER (info);

            if (buffer == NULL)
                return GST_PAD_PROBE_OK;
            if (gst_buffer_map (buffer, &map, GST_MAP_READ) )
            {
                int found_data = 0;
                data = map.data;

                fp = fopen(VIDEO_DUMP_FILE, "a");
                if( fp != NULL )
                {
                    //search for 0x41e0 (P frames), or 0x65b8 (I frames).  some times the encoder gives extra data at the starting
                    //skipping the extra data.
                    for(i = 0; i < map.size; i++ )
                    {
                        if( data[i] == 0x41 && data[i+1] == 0xe0 )
                        {
                            found_data = 1;
                            break;
                        }
                        if( data[i] == 0x65 && data[i+1] == 0xb8)
                        {
                            found_data = 1;
                            break;
                        }
                    }
                    if( found_data == 1 )
                    {
                        fwrite(data+i, 1, map.size-i, fp);
                        fwrite(c, 1, sizeof(c), fp);
                        videoDumpCount++;
                    }
                    fclose(fp);
                }
            }
            gst_buffer_unmap (buffer, &map);
        }

        if( videoDumpCount == MAX_NUMBER_OF_FRAMES)
        {
            CSIO_LOG(eLogLevel_debug, "Streamout: finished storing of video encoded data");
            videoDumpCount++;
        }
    }

    return GST_PAD_PROBE_OK;

}

static void jpeg_rate_control(GstElement *ele, int nframes, guint framesize, GstClockTime frameduration, int desiredRate)
{
    if (ele == NULL)
        return;

    int durationInMsec = (frameduration/1000000);
    int cur_rate = framesize/durationInMsec;
    int q = jpegQuality;
    int delta = desiredRate/20;
    if (cur_rate > (desiredRate+delta))
        q -= 2;
    else if (cur_rate < (desiredRate - delta))
        q += 2;
    if (q > 85) q = 85;
    if (q < 30) q = 30;
    if (q != jpegQuality)
    {
        CSIO_LOG(eLogLevel_info,"jpeg_rate_control: frame: %d framesize: %d bits duration: %d msec  current rate: %d quality changed to %d",
                nframes, framesize, durationInMsec, cur_rate, q);
        jpegQuality = q;
        g_object_set( G_OBJECT(ele), "quality", jpegQuality, NULL );
    }
}

#undef USE_PTS_FOR_TIME
static void vstats_cb (
              GstElement *identity,
              GstBuffer *buffer,
              gpointer user_data)
{
    static int nframes= 0;
    static guint64 nbytes = 0;
    static GstClockTime first_pts = 0;
    static GstClockTime base_time = 0;
    GstClockTime pts = GST_BUFFER_PTS(buffer);

    if (wcJpegStatsReset)
    {
        nframes = 0;
        nbytes = 0;
        first_pts = pts;
        base_time = gst_clock_get_time (gst_element_get_clock(identity));
        wcJpegStatsReset = false;
        return;
    }

    nframes++;
    gsize bufsize = gst_buffer_get_size(buffer);

    nbytes += bufsize;
    if (user_data != NULL && wcJpegRateControl)
        jpeg_rate_control((GstElement *) user_data, nframes, 8*bufsize, GST_BUFFER_DURATION(buffer), wcJpegRateBitsPerMsec);

    if (!wcJpegStatsEnable)
        return;

    gint64 run_time = 0;
#if USE_PTS_FOR_TIME
    run_time = (pts - first_pts)/1000000;
#else
    GstClockTime now_time = gst_clock_get_time (gst_element_get_clock(identity));

    if (now_time > base_time)
        run_time = now_time - base_time;
    else
        run_time = 0;
    run_time /= 1000000;
#endif
    if (nframes%300 == 0)
    {
        double rate = 8*((double) nbytes/(double) run_time);
        int run_time_secs = run_time/1000;
        int run_time_msecs = run_time%1000;
        CSIO_LOG(eLogLevel_info,"videostats: PTS=%lld jpegframe count: %d size: %d  runtime=%d.%d totalsize=%lld rate=%.0f kb/s",
                pts, nframes, bufsize, run_time_secs, run_time_msecs, nbytes, rate);
    }
}

//Todo: use gst_rtsp_media_factory_set_dscp_qos after update to 1.18
static void
cres_streams_set_dscp_qos(GstRTSPMedia *media, gint dscp_qos)
{
    guint i, n_streams;
    if(!media || (dscp_qos < 0 || dscp_qos > 63) )
    {
       CSIO_LOG(eLogLevel_error,"invalid: media %p , dscp_qos: %d", media, dscp_qos);
       return;
     }

     n_streams = gst_rtsp_media_n_streams(media);
     CSIO_LOG(eLogLevel_verbose,"media %p has %u streams", media, n_streams);

     for (i = 0; i < n_streams; i++)
     {
        GstRTSPStream *stream;
        stream = gst_rtsp_media_get_stream(media, i);
        if (stream == NULL)
           continue;

        gst_rtsp_stream_set_dscp_qos(stream,dscp_qos);
        CSIO_LOG(eLogLevel_verbose,"set stream %p for dscp_qos:%d", stream, dscp_qos);
     }
}

/* called when a new media pipeline is constructed. We can query the
 * pipeline and configure our media src */
static void
wc_media_configure (GstRTSPMediaFactory * factory, GstRTSPMedia * media,
                 gpointer user_data)
{
    CStreamoutManager *pMgr = (CStreamoutManager *) user_data;
    guint leakyVal;
    /* get the element used for providing the streams of the media */
    GstElement *element = gst_rtsp_media_get_element (media);
    gchar * n = gst_element_get_name(element);
    CSIO_LOG(eLogLevel_debug, "Streamout: wc_media_configure element name[%s] of media[0x%p]",n,media);

    //set up audio source
    if (pMgr->m_audioStream)
    {
        if (useUsbAudio && (pMgr->m_usbAudio->m_device == NULL))
        {
            if (!pMgr->m_usbAudio->configure())
            {
                CSIO_LOG(eLogLevel_error, "Error: !!! wc_media_configure Open USB Audio FAILED!!! Fatal ");
                //indicates the sessions needs to be closed. Fatal pipeline error
                pMgr->sendWcMediaPipelineError(WCERROR_MODULE_AUDIO, WCERROR_CODE_FATAL_CLOSESESSION, "Audio Configuration failed. Fatal");
                return;
            }
        }

        GstElement *ele = gst_bin_get_by_name_recurse_up (GST_BIN (element), "wc_appsrc");
        if(ele)
        {
            CSIO_LOG(eLogLevel_verbose, "Streamout: get_by_name wc_appsrc[%p]",ele);

            GstCaps *caps = gst_caps_new_simple("audio/x-raw",
                    "layout", G_TYPE_STRING, "interleaved",
                    "format", G_TYPE_STRING, pMgr->m_usbAudio->getAudioFormat(),
                    "rate", G_TYPE_INT, pMgr->m_usbAudio->getAudioSamplingRate(),
                    "channels", G_TYPE_INT, pMgr->m_usbAudio->getAudioChannels(),
                    NULL);
            if (pMgr->m_usbAudio->getAudioChannels() == 4) {
                // when not doing mono or stereo, we MUST specify a explicit channel-mask in the caps
                guint64 channel_mask = getAudioChannelMask(pMgr->m_usbAudio->getAudioChannels());
                gst_caps_set_simple(caps, "channel-mask", GST_TYPE_BITMASK, channel_mask, NULL);
            }
            g_object_set(G_OBJECT(ele), "caps", caps, NULL);
            CSIO_LOG(eLogLevel_info, "Streamout: audio src caps: %s", gst_caps_to_string(caps));

            g_object_set( G_OBJECT(ele), "stream-type", 0, NULL );
            g_object_set( G_OBJECT(ele), "format", GST_FORMAT_TIME, NULL );


            pMgr->m_appsrc = ele;
            clear_audio_stats();
            g_signal_connect(ele, "need-data", G_CALLBACK(cb_wcSrcNeedData), user_data);
            g_signal_connect(ele, "enough_data", G_CALLBACK(cb_wcSrcEnoughData), user_data);

            gst_object_unref(ele);
        }
    }

    if (pMgr->m_videoStream)
    {
        GstElement *ele = gst_bin_get_by_name_recurse_up(GST_BIN (element), "v4l2src");
        if (ele)
        {
            CSIO_LOG(eLogLevel_info, "Streamout: v4l2src is-live: %d", gst_base_src_is_live((GstBaseSrc *) ele));
            if (!gst_base_src_is_live((GstBaseSrc *) ele))
            {
                gst_base_src_set_live ((GstBaseSrc *) ele, true);
                CSIO_LOG(eLogLevel_info, "Streamout: v4l2src after forcing to true is-live: %d", gst_base_src_is_live((GstBaseSrc *) ele));
            }

            gst_object_unref(ele);
        }
        ele = gst_bin_get_by_name_recurse_up(GST_BIN (element), "vidPreQ");
        if (ele)
        {
            leakyVal = 2 /*GST_QUEUE_LEAK_DOWNSTREAM*/;
            if (jpegPassthrough && (wcVideoQueueMaxTime != 0))
            {
                CSIO_LOG(eLogLevel_info, "Streamout: set max-size-time on vidPreQ to %d msec", wcVideoQueueMaxTime);
                leakyVal = 1 /*GST_QUEUE_LEAK_UPSTREAM*/;
                g_object_set(G_OBJECT(ele), "max-size-time", (guint) wcVideoQueueMaxTime*1000000, NULL);
            }
            CSIO_LOG(eLogLevel_info, "Streamout: set leaky %s on vidPreQ", LEAKYSTR(leakyVal));
            g_object_set(G_OBJECT(ele), "leaky", leakyVal, NULL);

            pMgr->m_vidEncPreQ = ele;
            g_signal_connect( G_OBJECT(ele), "overrun", G_CALLBACK( cb_vidEncQueueOverruns ), user_data );
            g_signal_connect( G_OBJECT(ele), "underrun", G_CALLBACK( cb_vidEncQueueUnderruns ), user_data );

            gst_object_unref(ele);
        }
        ele = gst_bin_get_by_name_recurse_up(GST_BIN (element), "vidPostQ");
        if (ele)
        {
            leakyVal = 2 /*GST_QUEUE_LEAK_DOWNSTREAM*/;
            if (jpegPassthrough && (wcVideoQueueMaxTime != 0))
            {
                CSIO_LOG(eLogLevel_info, "Streamout: set max-size-time on vidPostQ to %d msec", wcVideoQueueMaxTime);
                leakyVal = 1 /*GST_QUEUE_LEAK_UPSTREAM*/;
                g_object_set(G_OBJECT(ele), "max-size-time", (guint) wcVideoQueueMaxTime*1000000, NULL);
            }
            CSIO_LOG(eLogLevel_info, "Streamout: set leaky %s on vidPostQ", LEAKYSTR(leakyVal));
            g_object_set(G_OBJECT(ele), "leaky", leakyVal, NULL);

            pMgr->m_vidEncPostQ = ele;
            g_signal_connect( G_OBJECT(ele), "overrun", G_CALLBACK( cb_vidEncQueueOverruns ), user_data );
            g_signal_connect( G_OBJECT(ele), "underrun", G_CALLBACK( cb_vidEncQueueUnderruns ), user_data );

            gst_object_unref(ele);
        }
        const char *parser = (jpegPassthrough) ? "jpegparser" : "h264parser";
        ele = gst_bin_get_by_name_recurse_up(GST_BIN (element), parser );
        if( ele )
        {
            GstPad *srcpad;
            CSIO_LOG(eLogLevel_info, "Streamout: Adding data probe on source pad of %s ", parser);
            videoDumpCount = 0;
            resetFrameDrop();
            srcpad = gst_element_get_static_pad (ele, "src");
            gst_pad_add_probe (srcpad, GST_PAD_PROBE_TYPE_BUFFER,
                    (GstPadProbeCallback) cb_dump_enc_data, media, NULL);
            gst_object_unref (srcpad);
        }
        ele = gst_bin_get_by_name_recurse_up(GST_BIN (element), "videntity");
        if (ele)
        {
            CSIO_LOG(eLogLevel_info, "Streamout: Adding handoff signal call back to vstats");
            GstElement *e = gst_bin_get_by_name_recurse_up(GST_BIN (element), "jpegenc");
            g_signal_connect_data(ele, "handoff", G_CALLBACK(vstats_cb), e, NULL, GConnectFlags());
        }
    }
    if (pMgr->m_audioStream) {
        GstElement *ele = gst_bin_get_by_name_recurse_up(GST_BIN (element), "audPreQ");
        if (ele) {
            CSIO_LOG(eLogLevel_info, "Streamout: set leaky downstream on audPreQ");
            g_object_set(G_OBJECT(ele), "leaky", (guint) 2 /*GST_QUEUE_LEAK_DOWNSTREAM*/, NULL);
            g_object_set(G_OBJECT(ele), "max-size-bytes", 48000, NULL);

            pMgr->m_audPreQ = ele;
            g_signal_connect( G_OBJECT(ele), "overrun", G_CALLBACK( cb_queueOverruns ), user_data );
            g_signal_connect( G_OBJECT(ele), "underrun", G_CALLBACK( cb_queueUnderruns ), user_data );

            gst_object_unref(ele);
        }
        ele = gst_bin_get_by_name_recurse_up(GST_BIN (element), "audPostQ");
        if (ele) {
            CSIO_LOG(eLogLevel_info, "Streamout: set leaky downstream on audPostQ");
            g_object_set(G_OBJECT(ele), "leaky", (guint) 2 /*GST_QUEUE_LEAK_DOWNSTREAM*/, NULL);
            g_object_set(G_OBJECT(ele), "max-size-bytes", 48000, NULL);

            pMgr->m_audPostQ = ele;
            g_signal_connect( G_OBJECT(ele), "overrun", G_CALLBACK( cb_queueOverruns ), user_data );
            g_signal_connect( G_OBJECT(ele), "underrun", G_CALLBACK( cb_queueUnderruns ), user_data );

            gst_object_unref(ele);
        }
    }


    CSIO_LOG(eLogLevel_debug, "Streamout: set media reusable to true media[%p]",media);
    gst_rtsp_media_set_reusable (media, TRUE);

    if (pMgr->m_streamoutMode == STREAMOUT_MODE_WIRELESSCONFERENCING)
    {
        gint dscp_qos = get_dscp_qos();
        if(dscp_qos!= -1)
        {
            cres_streams_set_dscp_qos (media, dscp_qos);
            //gst_rtsp_media_factory_set_dscp_qos (m_factory, dscp_qos);
        }
    }

    //pass media back to manager
    pMgr->m_pMedia = media;

    ((CresRTSPMedia *)media)->m_loop = pMgr->m_loop;

    gst_object_unref (element);
    g_free(n);
}

static GstRTSPFilterResult
filter_cb (GstRTSPStream *stream, GstRTSPStreamTransport *trans,gpointer user_data)
{
    CSIO_LOG(eLogLevel_info, "Streamout: filter_cb -------stream[0x%p]---",stream);
    return GST_RTSP_FILTER_REMOVE;
}

static GstRTSPFilterResult
client_filter (GstRTSPServer * server, GstRTSPClient * client,
    gpointer user_data)
{
  CSIO_LOG(eLogLevel_info, "Streamout: client_filter -------client[0x%p]---",client);
  /* Simple filter that shuts down all clients. */
  return GST_RTSP_FILTER_REMOVE;
}

void cb_queueOverruns(void *queue, gpointer user_data)
{
    CSIO_LOG(eLogLevel_verbose, "Streamout: cb_queueOverruns...(pMgr=%p)", user_data);
    CStreamoutManager *pMgr = (CStreamoutManager *)user_data;

    if (pMgr->m_audPreQ == queue)
        pMgr->audPreQOrunsCnt++;

    if (pMgr->m_audPostQ == queue)
        pMgr->audPostQOrunsCnt++;
}

void cb_queueUnderruns(void *queue, gpointer user_data)
{
    CSIO_LOG(eLogLevel_verbose, "Streamout: cb_queueUnderruns...(pMgr=%p)", user_data);
    CStreamoutManager *pMgr = (CStreamoutManager *)user_data;

    if (pMgr->m_audPreQ == queue)
        pMgr->audPreQUrunsCnt++;

    if (pMgr->m_audPostQ == queue)
        pMgr->audPostQUrunsCnt++;
}

void cb_vidEncQueueOverruns(void *queue, gpointer user_data)
{
    CSIO_LOG(eLogLevel_verbose, "Streamout: cb_vidEncQueueOverruns...(pMgr=%p)", user_data);
    CStreamoutManager *pMgr = (CStreamoutManager *)user_data;

    if (pMgr->m_vidEncPreQ == queue)
    {
        if (wcShowVideoQueueOverruns)
        {
            guint bytes, buffers;
            guint64 time;
            g_object_get(queue, "current-level-buffers", &buffers, "current-level-bytes", &bytes, "current-level-time", &time, NULL);
            CSIO_LOG(eLogLevel_info, "Streamout: cb_vidEncQueueOverruns vidPreQ bytes=%d bufs=%d time=%lld", bytes, buffers, time);
        }
        pMgr->vidEncPreQOrunsCnt++;
    }

    if (pMgr->m_vidEncPostQ == queue)
    {
        if (wcShowVideoQueueOverruns)
        {
            guint bytes, buffers;
            guint64 time;
            g_object_get(queue, "current-level-buffers", &buffers, "current-level-bytes", &bytes, "current-level-time", &time, NULL);
            CSIO_LOG(eLogLevel_info, "Streamout: cb_vidEncQueueOverruns vidPostQ bytes=%d bufs=%d time=%lld", bytes, buffers, time);
        }
        pMgr->vidEncPostQOrunsCnt++;
    }
}

void cb_vidEncQueueUnderruns(void *queue, gpointer user_data)
{
    CSIO_LOG(eLogLevel_verbose, "Streamout: cb_vidEncQueueUnderruns...(pMgr=%p)", user_data);
    CStreamoutManager *pMgr = (CStreamoutManager *)user_data;

    if (pMgr->m_vidEncPreQ == queue)
        pMgr->vidEncPreQUrunsCnt++;

    if (pMgr->m_vidEncPostQ == queue)
        pMgr->vidEncPostQUrunsCnt++;
}
/**********************CStreamoutManager class implementation***************************************/
CStreamoutManager::CStreamoutManager(eStreamoutMode streamoutMode,int id):
        m_streamoutMode(streamoutMode),m_id(0),m_clientConnCnt(0),m_main_loop_is_running(0),
        m_pMedia(NULL),m_ahcsrc(NULL),m_camera(NULL),
        m_appsrc(NULL), m_audPreQ(NULL), m_audPostQ(NULL), m_vidEncPreQ(NULL), m_vidEncPostQ(NULL),
        m_bNeedData(false),m_bExit(false),m_bStopTeeInProgress(false),m_bPushRawFrames(false),
        m_videoStream(false),m_audioStream(false),m_usbAudio(NULL),
        m_loop(NULL)
{
    m_id = id;

    m_StreamoutEvent  = new CStreamoutEvent();

    m_StreamoutEventQ = new CStreamoutEventRingBuffer(EVNT_DEFAULT_QUEUE_SIZE);

    mLock            = new Mutex();
    mTLock           = new Mutex();

    m_multicast_address[0] = '\0';
    m_stream_name[0] = '\0';

    setUsername("user");
    setPassword("password");
    setVideoCaptureDevice("none");
    setAudioCaptureDevice("none");
    m_audioStream = false;
    m_videoStream = false;
    m_aes67Mode = false;

    audPreQOrunsCnt = 0;
    audPreQUrunsCnt = 0;
    audPostQOrunsCnt = 0;
    audPostQUrunsCnt = 0;

    vidEncPreQOrunsCnt = 0;
    vidEncPreQUrunsCnt = 0;
    vidEncPostQOrunsCnt = 0;
    vidEncPostQUrunsCnt = 0;

    if(!m_StreamoutEvent || !m_StreamoutEventQ || !mLock)
        CSIO_LOG(eLogLevel_error, "--Streamout: CStreamoutManager malloc failed:[0x%p][0x%p][0x%p]",\
                m_StreamoutEvent,m_StreamoutEventQ,mLock);
}

CStreamoutManager::~CStreamoutManager()
{
    CSIO_LOG(eLogLevel_error, "--Streamout: enter destructor");
	if (m_usbAudio)
	{
		m_usbAudio->releaseDevice();
		delete m_usbAudio;
		m_usbAudio = NULL;
	}

    m_clientList.clear();

    if(m_StreamoutEvent)
    {
        delete m_StreamoutEvent;
        m_StreamoutEvent = NULL;
    }

    if(m_StreamoutEventQ)
    {
        delete m_StreamoutEventQ;
        m_StreamoutEventQ = NULL;
    }

    if(mLock)
    {
        delete mLock;
        mLock = NULL;
    }

    if(mTLock)
    {
    	delete mTLock;
    	mTLock = NULL;
    }
    CSIO_LOG(eLogLevel_error, "--Streamout: exit destructor");
}

void CStreamoutManager::DumpClassPara(int level)
{
    CSIO_LOG(eLogLevel_info, "---Streamout: m_debugLevel %d", m_debugLevel);

    CSIO_LOG(eLogLevel_info, "---Streamout: m_parent 0x%p", m_parent);
    CSIO_LOG(eLogLevel_info, "---Streamout: m_clientConnCnt %d", m_clientConnCnt);
    CSIO_LOG(eLogLevel_info, "---Streamout: m_main_loop_is_running %d", m_main_loop_is_running);

    CSIO_LOG(eLogLevel_info, "---Streamout: m_streamoutMode %d", m_streamoutMode);
    CSIO_LOG(eLogLevel_info, "---Streamout: m_auth_on %d", (int)m_auth_on);
    CSIO_LOG(eLogLevel_info, "---Streamout: m_tls_on %d", (int)m_tls_on);
    CSIO_LOG(eLogLevel_info, "---Streamout: m_video_capture_device %s", m_video_capture_device);
    CSIO_LOG(eLogLevel_info, "---Streamout: m_audio_capture_device %s", m_audio_capture_device);


    CSIO_LOG(eLogLevel_info, "---Streamout: m_loop 0x%p", m_loop);
    CSIO_LOG(eLogLevel_info, "---Streamout: m_pMedia [0x%p]",m_pMedia);

    CSIO_LOG(eLogLevel_info, "---Streamout: m_rtsp_port %s", m_rtsp_port);
    CSIO_LOG(eLogLevel_info, "---Streamout: m_res_x %s", m_res_x);
    CSIO_LOG(eLogLevel_info, "---Streamout: m_res_y %s", m_res_y);
    CSIO_LOG(eLogLevel_info, "---Streamout: m_frame_rate %s", m_frame_rate);

    CSIO_LOG(eLogLevel_info, "---Streamout: m_codec %s", m_codec);
    CSIO_LOG(eLogLevel_info, "---Streamout: m_bit_rate %s", m_bit_rate);
    CSIO_LOG(eLogLevel_info, "---Streamout: m_iframe_interval %s", m_iframe_interval);
    CSIO_LOG(eLogLevel_info, "---Streamout: m_quality %d", m_quality);

    CSIO_LOG(eLogLevel_info, "---Streamout: m_audPreQ [0x%p]", m_audPreQ);
    CSIO_LOG(eLogLevel_info, "---Streamout: m_audPostQ [0x%p]",m_audPostQ);

    CSIO_LOG(eLogLevel_info, "---Streamout: audPreQOrunsCnt %d",  audPreQOrunsCnt);
    CSIO_LOG(eLogLevel_info, "---Streamout: audPreQUrunsCnt %d",  audPreQUrunsCnt);
    CSIO_LOG(eLogLevel_info, "---Streamout: audPostQOrunsCnt %d", audPostQOrunsCnt);
    CSIO_LOG(eLogLevel_info, "---Streamout: audPostQUrunsCnt %d", audPostQUrunsCnt);

    CSIO_LOG(eLogLevel_info, "---Streamout: m_vidEncPreQ [0x%p]", m_vidEncPreQ);
    CSIO_LOG(eLogLevel_info, "---Streamout: m_vidEncPostQ [0x%p]",m_vidEncPostQ);

    CSIO_LOG(eLogLevel_info, "---Streamout: vidEncPreQOrunsCnt %d",  vidEncPreQOrunsCnt);
    CSIO_LOG(eLogLevel_info, "---Streamout: vidEncPreQUrunsCnt %d",  vidEncPreQUrunsCnt);
    CSIO_LOG(eLogLevel_info, "---Streamout: vidEncPostQOrunsCnt %d", vidEncPostQOrunsCnt);
    CSIO_LOG(eLogLevel_info, "---Streamout: vidEncPostQUrunsCnt %d", vidEncPostQUrunsCnt);
}

//overloaded from base
void CStreamoutManager::exitThread()
{
    CSIO_LOG(m_debugLevel, "Streamout: try to quit g_main_loop[0x%p]",m_loop);
    m_forceThreadExit = 1;

    if(m_loop)
    {
        if(m_factory)
            gst_rtsp_media_factory_set_eos_shutdown (m_factory, TRUE);

        g_main_loop_quit(m_loop);
        CSIO_LOG(m_debugLevel, "Streamout: g_main_loop_quit returned");
    }
    else
    {
        CSIO_LOG(m_debugLevel, "Streamout: g_main_loop is not running");
    }
}

static pthread_cond_t m_condv;

void *CStreamoutManager::forceUnprepare(void * arg)
{
	GstRTSPMedia * pMedia = (GstRTSPMedia *)arg;

    CSIO_LOG(eLogLevel_info, "Streamout(%s): calling gst_rtsp_media_unprepare_force", __FUNCTION__);
    gst_rtsp_media_unprepare_force (pMedia);   //bug122841 - this function hangs.
    CSIO_LOG(eLogLevel_info, "Streamout(%s): gst_rtsp_media_unprepare_force returned", __FUNCTION__);

    pthread_cond_signal(&m_condv); //Flags that stop has completed
    pthread_exit(NULL);
    return NULL;
}

void csio_jni_recoverTxrxService();
void csio_jni_sendCameraStopFb();

void CStreamoutManager::forceMediaUnprepare(GstRTSPMedia * pMedia)
{
	int result = 0;
	int timeout_sec = 5;
	struct timespec ts;
	pthread_t threadID;
	pthread_mutex_t  m_mutex;

	//init mutex and thread attributes
	pthread_cond_init(&m_condv, NULL);
	pthread_mutex_init(&m_mutex, NULL);
	pthread_attr_t attr;
	pthread_attr_init(&attr);
	pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_DETACHED);
	clock_gettime(CLOCK_REALTIME, &ts);
	ts.tv_sec += timeout_sec;

	//Kick off the new thread
	if (pthread_create(&threadID, &attr, &(CStreamoutManager::forceUnprepare), (void *)pMedia) != 0 )
	{
	    CSIO_LOG(eLogLevel_error, "forceMediaUnprepare() create the new thread failed.");
	}

	//Wait for timeout or completion
	pthread_mutex_lock(&m_mutex);
	result = pthread_cond_timedwait(&m_condv, &m_mutex, &ts);
	pthread_mutex_unlock(&m_mutex);

	//Cleanup pthread objects
	pthread_attr_destroy(&attr);
	pthread_cond_destroy(&m_condv);
	pthread_mutex_destroy(&m_mutex);

	if (result == ETIMEDOUT)
	{
		CSIO_LOG(eLogLevel_error, "forceUnprepare timed out after %d seconds, restart txrxservice ...", timeout_sec);
		csio_jni_sendCameraStopFb();
		csio_jni_recoverTxrxService();
	}
	else if (result != 0)
		CSIO_LOG(eLogLevel_error, "Unknown error occurred while waiting for forceUnprepare to complete, error = %d", result);
    CSIO_LOG(eLogLevel_info, "forceMediaUnprepare() exiting.....");
}

void* CStreamoutManager::ThreadEntry()
{
    char pipeline[1024];
    guint server_id;
    GMainContext*        context = NULL;
    GstRTSPServer *      server  = NULL;
    GstRTSPMountPoints * mounts  = NULL;
    GSource *  server_source     = NULL;
    GSource *timeout_source      = NULL;
    GstRTSPSessionPool *pool     = NULL;
    GError *error = NULL;
    char mountPoint [512];
    char videoSource[512]={0};
    char audioSource[512]={0};
    eWCstatus wc_InitRtn = STREAMOUT_WC_STATUS_NOERROR;
    bool wcRestart = false;
    bool sent_csio_jni_onServerStart = false;
#ifdef AM3X00
    CSIO_LOG(
        eLogLevel_info,
        "%s: %s tid %d auth_on %d random_user_pw %d tls_on %d",
        __func__, name_.c_str(), gettid(), m_auth_on, m_random_user_pw, m_tls_on);
#endif

    if (m_streamoutMode == STREAMOUT_MODE_WIRELESSCONFERENCING)
    {
    	initWcCertificates();
    	wc_InitRtn = initWcAudioVideo();
    	if (!m_videoStream && !m_audioStream)
    	{
            CSIO_LOG(eLogLevel_error, "***** Streamout: could not find usable video or audio device[%d] *****",(int) wc_InitRtn);
            wc_InitRtn = STREAMOUT_WC_STATUS_ERROR;
    	} else {
    		if (m_videoStream)
    			setVideoSource(videoSource, sizeof(videoSource));
    		if (m_audioStream)
    			setAudioSource(audioSource, sizeof(audioSource));
    	}
    }

    if ((m_streamoutMode == STREAMOUT_MODE_WIRELESSCONFERENCING) && (wc_InitRtn != STREAMOUT_WC_STATUS_NOERROR))
    {
        wcRestart = true;
    }
    else
    {
        m_factory = NULL;

        //create new context
        context = g_main_context_new ();
        g_main_context_push_thread_default(context);
        CSIO_LOG(m_debugLevel,  "Streamout: create new context: %p", context );
        if(!context)
        {
            CSIO_LOG(eLogLevel_error, "Streamout: Failed to create rtsp server context");
            goto exitThread;
        }

        m_loop = g_main_loop_new (context, FALSE);   // called from CStreamer::execute
        if(!m_loop)
        {
            CSIO_LOG(eLogLevel_error, "Streamout: Failed to create rtsp server loop");
            goto exitThread;
        }
        server = gst_rtsp_server_new();
        if(!server)
        {
            CSIO_LOG(eLogLevel_error, "Streamout: Failed to create rtsp server");
            goto exitThread;
        }
        CSIO_LOG(eLogLevel_info, "Streamout: server[0x%p] created",server);

        // limit the rtsp sesssions
        pool = gst_rtsp_server_get_session_pool(server);
        if (!pool) {
            CSIO_LOG(eLogLevel_error, "Streamout: Failed to get the session pool");
            goto exitThread;
        }

        if (m_multicast_enable)
            gst_rtsp_session_pool_set_max_sessions(pool, UNLIMITED_RTSP_SESSIONS);	// Allow unlimited connections in multicast mode
        else
            gst_rtsp_session_pool_set_max_sessions(pool, MAX_RTSP_SESSIONS);

        CSIO_LOG(eLogLevel_debug, "Streamout: max_sessions set to %d", gst_rtsp_session_pool_get_max_sessions(pool));

        //setup listening port
        CSIO_LOG(m_debugLevel, "Streamout: set_service to port:%s",m_rtsp_port);
        gst_rtsp_server_set_service (server, m_rtsp_port);

        if (m_auth_on) {
            GstRTSPAuth *auth = NULL;
            GTlsCertificate *cert = NULL;
            /* make a new authentication manager. it can be added to control access to all
             * the factories on the server or on individual factories. */
            auth = gst_rtsp_auth_new();
            if (m_tls_on) {
                CSIO_LOG(eLogLevel_info, "Streamout: RTSP server's TLS configuration cert: %s, key: %s", m_rtsp_cert_filename, m_rtsp_key_filename);
                cert = g_tls_certificate_new_from_files(m_rtsp_cert_filename, m_rtsp_key_filename, &error);
                if (cert == NULL) {
                    CSIO_LOG(eLogLevel_error, "Streamout: failed to parse PEM: %s", error->message);
                    goto exitThread;
                }
    #ifdef CLIENT_AUTHENTICATION_ENABLED
                GTlsDatabase *database = g_tls_file_database_new(RTSP_CA_CERT_FILENAME, &error);
                //GTlsDatabase *database = g_tls_file_database_new(RTSP_CERT_PEM_FILENAME, &error);
                if (database == NULL)
                {
                    CSIO_LOG(eLogLevel_error, "Streamout: failed to create database from ca cert: %s", error->message);
                    goto exitThread;
                }
                gst_rtsp_auth_set_tls_database(auth, database);

                GTlsCertificate *ca_cert = g_tls_certificate_new_from_file(RTSP_CA_CERT_FILENAME,&error);
                //ca_cert = g_tls_certificate_new_from_file("/home/enthusiasticgeek/gstreamer/cert/toyCA.pem",&error);
                if (ca_cert == NULL) {
                    g_printerr ("failed to parse CA PEM: %s\n", error->message);
                    goto exitThread;
                }
    #else
                GTlsCertificate *ca_cert = NULL;
    #endif

                // Change to G_TLS_AUTHENTICATION_NONE if we don't want server to send CertificateRequest
                // to client during TLS exchange
                gst_rtsp_auth_set_tls_authentication_mode(auth, G_TLS_AUTHENTICATION_REQUIRED);

                g_signal_connect (auth, "accept-certificate", G_CALLBACK(accept_certificate), ca_cert);

                gst_rtsp_auth_set_tls_certificate(auth, cert);

                if (cert) g_object_unref(cert);
            }

            GstRTSPToken *token;
            gchar *basic;
            /* make user token */
            token = gst_rtsp_token_new (GST_RTSP_TOKEN_MEDIA_FACTORY_ROLE, G_TYPE_STRING, rtsp_server_username, NULL);
            basic = gst_rtsp_auth_make_basic (rtsp_server_username, rtsp_server_password);

            gst_rtsp_auth_add_basic (auth, basic, token);
            g_free (basic);
            gst_rtsp_token_unref (token);

            /* configure in the server */
            gst_rtsp_server_set_auth (server, auth);
            if(auth) g_object_unref(auth);
        }

        /* get the mount points for this server, every server has a default object
        * that be used to map uri mount points to media factories */
        mounts = gst_rtsp_server_get_mount_points (server);
        if(!mounts)
        {
            CSIO_LOG(eLogLevel_error, "Streamout: Failed to create server mounts");
            goto exitThread;
        }
        m_factory = gst_rtsp_media_factory_new ();
        if(!m_factory)
        {
            CSIO_LOG(eLogLevel_error, "Streamout: Failed to create factory");
            goto exitThread;
        }

        if(gst_rtsp_server_get_pipeline(pipeline, sizeof(pipeline)) == false)
        {
            if (m_streamoutMode == STREAMOUT_MODE_CAMERA)
            {
                // Because camera on x60 is front-facing, it is mirrored by default for the preview.
                // Old default pipeline (with video flipping) "( ahcsrc ! videoflip method=4 ! videoconvert ! %s ! rtph264pay name=pay0 pt=96 )"
                // Enabled NV21 pixel format in libgstreamer_android.so, don't need videoconvert anymore.
                //"( ahcsrc ! videoconvert ! %s ! rtph264pay name=pay0 pt=96 )"
                // Queue before encoder reduces stream latency.
                snprintf(pipeline, sizeof(pipeline), "( appsrc name=cresappsrc ! "
                                                     "videoparse name=vparser ! "
                                                     "queue name =vidPreQ ! "
                                                     "%s bitrate=%s i-frame-interval=%s ! "
                                                     "queue name =vidPostQ ! "
                                                     "rtph264pay name=pay0 pt=96)",
                         product_info()->H264_encoder_string,
                         m_bit_rate,m_iframe_interval);
            }
            else
            {
                CSIO_LOG(eLogLevel_error, "Streamout: m_videoStream=%d m_audioStream=%d", m_videoStream, m_audioStream);
                char video_pipeline[1024]={0};
                char audio_pipeline[1024]={0};
                char jpegenc[128]={0};
                if (m_videoStream)
                {
                    if (!jpegPassthrough)
                    {
                        snprintf(video_pipeline, sizeof(video_pipeline),
                                "%s ! %s ! %s"
                                "%s"
                                "queue name = vidPreQ ! "
                                "%s bitrate=%s i-frame-interval=%s ! "
                                "queue name=vidPostQ ! "
                                "h264parse name = h264parser ! "
                                "rtph264pay name=pay0 pt=96 config-interval=-1",
                                videoSource, m_caps, m_videoframerate,
                                m_videoconvert,
                                product_info()->H264_encoder_string,
                                m_bit_rate, m_iframe_interval);
                    }
                    else
                    {
                        if (strcmp(m_video_caps.format,"MJPG") != 0)
                        {
                            jpegQuality = wcJpegQuality; // use global setting as initial start value
                            snprintf(jpegenc, sizeof(jpegenc), "queue name=vidPreQ ! jpegenc name=jpegenc quality=%d ! ", jpegQuality);
                        }
                        const char *vstats = (wcJpegStatsEnable || wcJpegRateControl) ? "identity name=videntity ! " : "";
                        wcJpegStatsReset = true;
                        snprintf(video_pipeline, sizeof(video_pipeline),
                                "%s ! %s ! %s"
                                "%s"
                                "queue name=vidPostQ ! "
                                "%s"
                                "jpegparse name = jpegparser ! "
                                "rtpjpegpay name=pay0 pt=96",
                                videoSource, m_caps, m_videoframerate,
                                jpegenc,
                                vstats);
                    }
                }

                if (m_audioStream)
                {
                    // for audio when we push have queues before and after enc, but pull model will not work like that so no queues
                    char audioenc[128];
                    char audioencdump[1024]={0};
                    const char *audioEncoderName = (m_aacEncode) ? AUDIOENC : "alawenc";
                    snprintf(audioenc, sizeof(audioenc), "%s", audioEncoderName);
                    if (wcAudioEncDumpEnable)
                    {
                        const char *format = "tee name=atee "
                                "atee. ! queue ! mp4mux faststart=true faststart-file=/sdcard/ROMDISK/logs/faststart fragment-duration=5000 ! filesink location=%s "
                                "atee. ! queue ! ";
                        snprintf(audioencdump, sizeof(audioencdump), format, AUDIO_DUMP_FILE);
                    }
                    int payno = (m_videoStream) ? 1: 0;

                    if (m_aacEncode) {
                        // audPreQ s required before the encoder. This fixed the lipsync issue which was seen with Logitech Brio/930 camera.
                        snprintf(audio_pipeline, sizeof(audio_pipeline),
                                "%s ! audioresample ! audioconvert ! queue name=audPreQ ! "
                                "%s ! "
                                "queue name=audPostQ ! aacparse name=aacparser ! "
                                "%s"
                                "rtpmp4apay name=pay%d pt=97",
                                audioSource, audioenc, audioencdump, payno);
                    } else if (m_aes67Mode) {
                        snprintf(audio_pipeline, sizeof(audio_pipeline),
                                "%s ! audioresample ! audioconvert ! "
                                "%s ! "
                                "rtpL24pay max-ptime=1000000 min-ptime=1000000 name=pay%d pt=97",
                                audioSource, "audio/x-raw, format=S24BE, rate=48000, channels=2", payno);
                    } else {
                        snprintf(audio_pipeline, sizeof(audio_pipeline),
                                "%s ! audioresample ! audioconvert ! audio/x-raw,rate=8000 ! queue name=audPreQ ! "
                                "%s ! "
                                "rtppcmapay name=pay%d pt=97",
                                audioSource, audioenc, payno);
                    }
                }
                //CSIO_LOG(m_debugLevel, "Streamout: rtsp server video pipeline: %s", video_pipeline);
                //CSIO_LOG(m_debugLevel, "Streamout: rtsp server audio pipeline: %s", audio_pipeline);
                snprintf(pipeline, sizeof(pipeline), "( %s %s )", video_pipeline, audio_pipeline);

#undef MPEGTS
#ifdef MATROSKA
                snprintf(pipeline, sizeof(pipeline), "%s",
                        " v4l2src device=/dev/video5 io-mode=4 do-timestamp=true name=v4l2src ! "
                        " video/x-raw,format=YUY2,width=1280,height=720,framerate=30/1 ! "
                        " videorate ! video/x-raw,framerate=15/1 ! videoconvert ! video/x-raw,format=NV12 ! "
                        " queue name = vidPreQ ! "
                        " amcvidenc-omxrkvideoencoderavc bitrate=4000000 i-frame-interval=1 ! "
                        " queue name=vidPostQ ! h264parse name = h264parser ! "
                        " tee name=vtee "
                        " matroskamux name=mux ! filesink location=/tmp/av.mkv "
                        " vtee. ! queue ! mux. "
                        " vtee. ! queue ! rtph264pay name=pay0 pt=96 config-interval=-1 "
                        " appsrc name=wc_appsrc ! audioresample ! audioconvert ! "
                        " queue name=audPreQ ! "
                        " voaacenc ! queue name=audPostQ ! aacparse name=aacparser ! "
                        " tee name=atee "
                        " atee. ! queue ! mux."
                        " atee. ! rtpmp4apay name=pay1 pt=97");
#endif
#ifdef MP4
                snprintf(pipeline, sizeof(pipeline), "%s",
                        " v4l2src device=/dev/video5 io-mode=4 do-timestamp=true name=v4l2src ! "
                        " video/x-raw,format=YUY2,width=1280,height=720,framerate=30/1 ! "
                        " videorate ! video/x-raw,framerate=15/1 ! videoconvert ! video/x-raw,format=NV12 ! "
                        " queue name = vidPreQ ! "
                        " amcvidenc-omxrkvideoencoderavc bitrate=4000000 i-frame-interval=1 ! "
                        " queue name=vidPostQ ! h264parse name = h264parser ! "
                        " tee name=vtee "
                        " mp4mux name=mux faststart=true faststart-file=/tmp/faststart fragment-duration=5000 ! filesink location=/tmp/av.mp4 "
                        " vtee. ! queue ! mux. "
                        " vtee. ! queue ! rtph264pay name=pay0 pt=96 config-interval=-1 "
                        " appsrc name=wc_appsrc  ! audioresample ! audioconvert ! "
                        " queue name=audPreQ ! "
                        " voaacenc ! "
                        " tee name=atee "
                        " atee. ! queue ! mux."
                        " atee. ! queue name=audPostQ ! aacparse name=aacparser ! rtpmp4apay name=pay1 pt=97");
#endif
#ifdef MPEGTS
                snprintf(pipeline, sizeof(pipeline), "%s",
                        " v4l2src device=/dev/video5 io-mode=4 do-timestamp=true name=v4l2src ! "
                        " video/x-raw,format=YUY2,width=1280,height=720,framerate=30/1 ! "
                        " videorate ! video/x-raw,framerate=15/1 ! videoconvert ! video/x-raw,format=NV12 ! "
                        " queue name = vidPreQ ! "
                        " amcvidenc-omxrkvideoencoderavc bitrate=4000000 i-frame-interval=1 ! "
                        " queue name=vidPostQ ! h264parse name = h264parser ! "
                        " tee name=vtee "
                        " mpegtsmux name=mux ! filesink location=/tmp/av.ts "
                        " vtee. ! queue ! mux. "
                        " vtee. ! queue ! rtph264pay name=pay0 pt=96 config-interval=-1 "
                        " appsrc name=wc_appsrc  ! audioresample ! audioconvert ! "
                        " queue name=audPreQ ! "
                        " voaacenc ! "
                        " queue name=audPostQ ! aacparse name=aacparser ! "
                        " tee name=atee "
                        " atee. ! queue ! mux."
                        " atee. ! queue ! rtpmp4apay name=pay1 pt=97");
#endif
            }
        }
        CSIO_LOG(m_debugLevel, "Streamout: rtsp server pipeline: [%s]", pipeline);
        gst_rtsp_media_factory_set_launch (m_factory, pipeline);

        if (m_auth_on) {
            /* add permissions for the user media role */
            GstRTSPPermissions *permissions = gst_rtsp_permissions_new();
            gst_rtsp_permissions_add_role(permissions, rtsp_server_username,
                                          GST_RTSP_PERM_MEDIA_FACTORY_ACCESS, G_TYPE_BOOLEAN, TRUE,
                                          GST_RTSP_PERM_MEDIA_FACTORY_CONSTRUCT, G_TYPE_BOOLEAN, TRUE,
                                          NULL);
            gst_rtsp_media_factory_set_permissions(m_factory, permissions);
            gst_rtsp_permissions_unref(permissions);
            if (m_tls_on) {
                gst_rtsp_media_factory_set_profiles(m_factory, (GstRTSPProfile) (GST_RTSP_PROFILE_SAVP | GST_RTSP_PROFILE_SAVPF));
            }
        }

        if (m_multicast_enable)
        {
            if (m_multicast_address[0])	// TODO: Also check that multicast address is valid
            {
                uint32_t ip = 0;
                int ret = inet_pton(AF_INET, m_multicast_address, &ip);
                ip = ntohl(ip); // put host byte order
                if ( (ret == 1) && ((ip >> 28) == 0xe) )
                {
                    /* make a new address pool for multicast */
                    CSIO_LOG(eLogLevel_info, "Streamout: setting multicast address for RTSP server to %s", m_multicast_address);
                    GstRTSPAddressPool *pool = gst_rtsp_address_pool_new ();
                    gst_rtsp_address_pool_add_range (pool,
                            m_multicast_address, m_multicast_address, 11000, 12000, 64);	// Setting ttl to fixed 64, and fixed port range
                    gst_rtsp_media_factory_set_address_pool (m_factory, pool);
                    /* only allow multicast */
                    gst_rtsp_media_factory_set_protocols (m_factory,
                            GST_RTSP_LOWER_TRANS_UDP_MCAST);
                    g_object_unref (pool);
                }
                else
                    CSIO_LOG(eLogLevel_error, "Streamout: Invalid multicast address provided");
            }
            else
                CSIO_LOG(eLogLevel_error, "Streamout: No multicast address provided");
        }
        else
            CSIO_LOG(eLogLevel_info, "Streamout: multicast disabled");

        // In WC mode or if multicast is not enabled when in AES67 mode
        if (m_streamoutMode != STREAMOUT_MODE_CAMERA && (!m_aes67Mode || !m_multicast_enable)) {
            CSIO_LOG(eLogLevel_info, "Streamout: set RTP port range from %d to %d", WC_RTP_PORT_MIN, WC_RTP_PORT_MAX);
            GstRTSPAddressPool *pool = gst_rtsp_address_pool_new ();
            gst_rtsp_address_pool_add_range (pool, GST_RTSP_ADDRESS_POOL_ANY_IPV4, GST_RTSP_ADDRESS_POOL_ANY_IPV4, WC_RTP_PORT_MIN, WC_RTP_PORT_MAX, 0);
            gst_rtsp_media_factory_set_address_pool (m_factory, pool);
            g_object_unref (pool);
        }


        /* notify when our media is ready, This is called whenever someone asks for
           * the media and a new pipeline with our appsrc is created */
        if (m_streamoutMode == STREAMOUT_MODE_CAMERA) {
            g_signal_connect (m_factory, "media-configure", (GCallback) media_configure, this);
        } else {
            g_signal_connect (m_factory, "media-configure", (GCallback) wc_media_configure, this);
        }

        gst_rtsp_media_factory_set_shared (m_factory, TRUE);

        // Reduce stream latency.
        gst_rtsp_media_factory_set_latency (m_factory, 10);

    #ifdef CRES_OVERLOAD_MEDIA_CLASS
        gst_rtsp_media_factory_set_media_gtype (m_factory, CRES_TYPE_RTSP_MEDIA);
    #endif

        if (m_stream_name[0])
        {
            sprintf(mountPoint, "/%s.sdp", m_stream_name);
        }
        else
        {
            if (m_streamoutMode == STREAMOUT_MODE_CAMERA)
                sprintf(mountPoint, "/%s.sdp", "camera");
            else
                sprintf(mountPoint, "/%s.sdp", "wc");
        }
        gst_rtsp_mount_points_add_factory (mounts, mountPoint, m_factory);
        g_object_unref(mounts);
        CSIO_LOG(eLogLevel_info, "Streamout: mount in %s mode: [%s]",
        m_streamoutMode == STREAMOUT_MODE_CAMERA ? "camera":"wireless conferencing", mountPoint);

        // send server URL to Gstreamout via jni
        sendWcUrl(server, mountPoint);

        //correct way to create source and attach to mainloop
        server_source = gst_rtsp_server_create_source(server,NULL,NULL);
        if(server_source)
        {
            CSIO_LOG(m_debugLevel, "Streamout: create_source , server_source [0x%p]", server_source);
        }
        else
        {
            CSIO_LOG(eLogLevel_error, "Streamout: Failed to create_source");
            goto exitThread;
        }
        server_id = g_source_attach (server_source, g_main_loop_get_context(m_loop));

        if(server_id)
        {
            CSIO_LOG(m_debugLevel, "Streamout: Attached server to maincontext, server_id %u", server_id);
        }
        else
        {
            CSIO_LOG(eLogLevel_error, "Streamout: Failed to attach server");
            goto exitThread;
        }

        m_main_loop_is_running = 1;

        if (m_streamoutMode == STREAMOUT_MODE_CAMERA)
            StartSnapShot(this);

        if (m_streamoutMode == STREAMOUT_MODE_WIRELESSCONFERENCING) {
            g_signal_connect(server, "client-connected", (GCallback) client_connected, this);
            timeout_source = g_timeout_source_new_seconds(2);
            g_source_set_callback(timeout_source, (GSourceFunc) timeout, server, NULL);
            g_source_attach(timeout_source, context);
        }
        csio_jni_onServerStart();
        sent_csio_jni_onServerStart = true;
        CSIO_LOG(eLogLevel_info, "Streamout: running main loop......");
        g_main_loop_run (m_loop);
    }
exitThread:
    /* cleanup */
	if (m_streamoutMode == STREAMOUT_MODE_CAMERA)
		StopSnapShot(this);

#ifdef CRES_UNPREPARE_MEDIA
/*   please check out this bug: https://bugzilla.gnome.org/show_bug.cgi?id=747801
 *   if it is fixed, we should take it. */

	bool restart = false;
	bool dataFlowError = false;
    if(m_pMedia)
    {
        restart = ((CresRTSPMedia *)m_pMedia)->m_restart;
        dataFlowError = ((CresRTSPMedia *)m_pMedia)->dataFlowError;
        gst_rtsp_media_suspend (m_pMedia);

        //remove stream from session before unprepare media
        guint i, n_streams;
        n_streams = gst_rtsp_media_n_streams (m_pMedia);
        CSIO_LOG(m_debugLevel, "Streamout: -------n_streams[%d]---",n_streams);

        for (i = 0; i < n_streams; i++)
        {
            GstRTSPStream *stream = gst_rtsp_media_get_stream (m_pMedia, i);

            if (stream == NULL)  continue;

            gst_rtsp_stream_transport_filter (stream, filter_cb, NULL);
        }

        CSIO_LOG(m_debugLevel, "Streamout: -------call gst_rtsp_media_unprepare---");
        forceMediaUnprepare(m_pMedia);
        CSIO_LOG(m_debugLevel, "Streamout: ------- forceMediaUnprepare returned---");
    }
#endif

    if(timeout_source)
    {
        /*You must use g_source_destroy() for sources added to a non-default main context.*/
        CSIO_LOG(m_debugLevel, "Streamout: g_source_destroy timeout_source[0x%p]",timeout_source);
        g_source_destroy(timeout_source);
        g_source_unref(timeout_source);
        timeout_source = NULL;
    }

    if(server_source)
    {
        /*You must use g_source_destroy() for sources added to a non-default main context.*/
        CSIO_LOG(m_debugLevel, "Streamout: g_source_destroy server_source[0x%p]",server_source);
        g_source_destroy (server_source);
        g_source_unref(server_source);
        server_source = NULL;
    }

    if(server)
    {
        CSIO_LOG(m_debugLevel, "Streamout: remove factory from mount points");
        mounts = gst_rtsp_server_get_mount_points (server);
        if (m_factory && mounts) gst_rtsp_mount_points_remove_factory(mounts, mountPoint);
        CSIO_LOG(m_debugLevel, "Streamout: unreference mounts[0x%p]",mounts);
        if (mounts) g_object_unref (mounts);

        /* Filter existing clients and remove them */
        CSIO_LOG(m_debugLevel, "Streamout: Disconnecting existing clients");
        gst_rtsp_server_client_filter (server, client_filter, NULL);

        // check client list is empty
        m_clientList.clear();
    }
//Note:  if you unref m_factory, then unref server will give you and err
//       seems unref server is enough, it will unref m_factory also.
//    CSIO_LOG(m_debugLevel, "Streamout: unreference factory[0x%x]",m_factory);
//    if(m_factory) g_object_unref (m_factory);
    if(m_loop)
    {
        CSIO_LOG(m_debugLevel, "Streamout: unreference loop[0x%p]",m_loop);
        g_main_loop_unref (m_loop);
    }
    if(pool)
    {
        CSIO_LOG(m_debugLevel, "Streamout: unreference pool[0x%p]",pool);
        g_object_unref (pool);
    }
    if(server)
    {
        CSIO_LOG(m_debugLevel, "Streamout: unreference server[0x%p]",server);
        g_object_unref (server);
    }

    //need to create a cleanup function and call here
    CSIO_LOG(m_debugLevel, "Streamout: reset m_loop, m_main_loop_is_running, m_pMedia");
    m_loop = NULL;
    m_main_loop_is_running = 0;

    if(context)
    {
        CSIO_LOG(m_debugLevel, "Streamout: unreference context[0x%p]", context);
        g_main_context_pop_thread_default(context);
        g_main_context_unref (context);
        context = NULL;
    }

    if (m_usbAudio)
    {
        m_usbAudio->releaseDevice();
        delete m_usbAudio;
        m_usbAudio = NULL;
    }

    if(sent_csio_jni_onServerStart)
        csio_jni_onServerStop();

    // If in WC mode and using HDMI input as camera we get dataFlowError reset HDMI input
    int reset_hdmi_on_data_flow_error = read_int_from_file("/sdcard/ROMDISK/User/reset_hdmi", 1);
    if ((m_streamoutMode == STREAMOUT_MODE_WIRELESSCONFERENCING) && (strcmp(m_video_capture_device, "/dev/video0")==0) &&
            reset_hdmi_on_data_flow_error && dataFlowError)
    {
        // disable and enable HDMI interface as workaround in AM3XX-13010 for bug AM3XX-12970
        CSIO_LOG(m_debugLevel, "Streamout: disable and reenable HDMI interface------");
        csio_jni_reset_hdmi_input();
    }

    CSIO_LOG(m_debugLevel, "Streamout: jni_start_rtsp_server ended------");

    //thread exit here
    m_ThreadIsRunning = 0;

    if(restart || wcRestart)
    {
        int delay_ms = 500;
        if( m_streamoutMode != STREAMOUT_MODE_WIRELESSCONFERENCING)
        {
            CSIO_LOG(m_debugLevel, "Streamout: restart manager[%d] thread.", m_id);
            m_parent->signalProject(m_id, STREAMOUT_EVENT_JNI_CMD_RESTART_MGR_THREAD,sizeof(void*), &delay_ms);
        } else {
            CSIO_LOG(m_debugLevel, "[AM3K]Streamout: Not restarting manager[%d] thread.", m_id);
            //Since a GST_MESSAGE_ERROR occured while starting the pipeline
            //indicates the sessions needs to be closed. Fatal pipeline error
            sendWcMediaPipelineError(WCERROR_MODULE_AUDIOVIDEO,WCERROR_CODE_FATAL_CLOSESESSION,"Gstreamer pipeline error occured");
        }

    }

    m_pMedia = NULL;

    CSIO_LOG(m_debugLevel, "CStreamoutManager: exiting ThreadEntry function - returning NULL------");
    return NULL;
}

static std::string genRandomString(const int len) {

    std::string tmp_s;
    // see failures often when we add special characters to username/password
    static const char alphanum[] =
        "0123456789"
        "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        "abcdefghijklmnopqrstuvwxyz";

    static bool initialize_seed = true;

    if (initialize_seed) {
    	srand( (unsigned) time(NULL) * getpid());
    	initialize_seed = false;
    }

    tmp_s.reserve(len);

    // Make first character alpha (not numeric)
    tmp_s += alphanum[10 + (rand() % (sizeof(alphanum) - 11))];
    for (int i = 1; i < len; ++i)
        tmp_s += alphanum[rand() % (sizeof(alphanum) - 1)];


    return tmp_s;
}

void CStreamoutManager::initWcCertificates()
{
	if (m_auth_on && m_random_user_pw)
	{
	    // get random user name and password
		setUsername((char *) genRandomString(16).c_str());
		setPassword((char *) genRandomString(16).c_str());
	}
    if (m_streamoutMode == STREAMOUT_MODE_WIRELESSCONFERENCING)
    {
        // TODO: provide relative path to APK config
        if (m_tls_on) {
            std::string folder = std::string(RTSP_CERTIFICATE_FOLDER_PATH) + std::string("/");
            std::string filename = folder + std::string(RTSP_ROOT_CERT_PEM_FILENAME);
            strncpy(m_rtsp_root_cert_filename, filename.c_str(), sizeof(m_rtsp_root_cert_filename));
            filename = folder + std::string(RTSP_ROOT_CERT_KEY);
            strncpy(m_rtsp_root_key_filename, filename.c_str(), sizeof(m_rtsp_root_key_filename));
        	filename = folder + std::string(RTSP_CERT_PEM_FILENAME);
            strncpy(m_rtsp_cert_filename, filename.c_str(), sizeof(m_rtsp_cert_filename));
        	filename = folder + std::string(RTSP_CERT_KEY);
            strncpy(m_rtsp_key_filename, filename.c_str(), sizeof(m_rtsp_key_filename));
        }
    }
}

eWCstatus CStreamoutManager::initWcAudioVideo()
{
    eWCstatus initError = STREAMOUT_WC_STATUS_NOERROR;

    if (m_streamoutMode == STREAMOUT_MODE_WIRELESSCONFERENCING)
    {
		CSIO_LOG(eLogLevel_info, "--Streamout: video_capture_device=%s audio_capture_device=%s", m_video_capture_device, m_audio_capture_device);
    	if (strcasecmp(m_video_capture_device, "none") != 0)
    		m_videoStream = true;
    	if (strcasecmp(m_audio_capture_device, "none") != 0)
    		m_audioStream = true;
        jpegPassthrough = (strcasecmp(m_codec, "MJPG") == 0);
        if (wcIsTx3Session)
        {
            if (m_quality == LOW_QUALITY)
            {
                m_quality = MEDIUM_QUALITY;
                strncpy(m_frame_rate, "15", sizeof(m_frame_rate));
#ifdef USE_H264_TX3_LOW_QUALITY
                CSIO_LOG(eLogLevel_info, "--Streamout - TX3session at low quality drop frame rate but preserve resolution and use H264");
                strncpy(m_codec, "H264", sizeof(m_codec));
#else
                CSIO_LOG(eLogLevel_info, "--Streamout - TX3session at low quality drop frame rate but preserve resolution");
#endif
            }
        }
        jpegPassthrough = (strcasecmp(m_codec, "MJPG") == 0);
        CSIO_LOG(eLogLevel_info, "--Streamout - codec=%s jpegPassthrough=%d", m_codec, jpegPassthrough);
        m_aacEncode = true;

        if (m_videoStream) {
            char framerate[128]={0};
            // This is only good for frame rates which are integers (not 12.5 etc)
            snprintf(encoded_frame_rate, sizeof(encoded_frame_rate), "%s/%d", m_frame_rate, 1);
            CSIO_LOG(eLogLevel_info, "--Streamout - encoded_frame_rate: %s", encoded_frame_rate);

            if (get_framerate_requested(framerate, sizeof(framerate)) < 0)
                strncpy(framerate, encoded_frame_rate, sizeof(framerate));
            CSIO_LOG(eLogLevel_info, "--Streamout - desired capture frame rate: %s", framerate);

        	if (strcmp(m_video_capture_device, "videotestsrc") != 0)
        	{
        	    if (!get_video_caps(m_video_capture_device, &m_video_caps, m_device_display_name, sizeof(m_device_display_name), m_quality,
        	            m_codec, framerate, m_hdmi_in_res_x, m_hdmi_in_res_y))
        		{
        			if (get_video_caps_string(&m_video_caps, m_caps, sizeof(m_caps)) < 0)
        			{
        			    initError = STREAMOUT_WC_STATUS_VIDEO_INIT_ERROR;
        				m_videoStream = false;
        			}
        		} else {
        			CSIO_LOG(eLogLevel_info, "--Streamout - unable to get caps for video device: %s", m_video_capture_device);
        			initError = STREAMOUT_WC_STATUS_VIDEO_INIT_ERROR;
        			m_videoStream = false;
        		}
            	CSIO_LOG(eLogLevel_info, "--Streamout - m_video_caps.format=%s", m_video_caps.format);
            	if (strcasecmp(m_video_caps.format, "MJPG") == 0)
            	{
                    //colorimetry=(string)1:4:0:0 - is required to make the video convert to use I420 to NV12 faster implementation
                    //this could be bug with Video convert as the gstreamer implementation traditionally does not support faster I420 to NV12 format.
                    snprintf(m_videoconvert, sizeof(m_videoconvert), "queue name=jpegQ ! jpegdec ! queue name=vidConvQ ! videoconvert ! video/x-raw,format=NV12, colorimetry=(string)1:4:0:0 ! ");
            	}
            	else if (is_supported(m_video_caps.format))
        		{
        			m_videoconvert[0] = '\0';
                    //snprintf(m_videoconvert, sizeof(m_videoconvert), "videorate ! video/x-raw,framerate=30/1 !");
        		}
            	else
            	{
            	    snprintf(m_videoconvert, sizeof(m_videoconvert), "videoconvert ! video/x-raw,format=NV12 ! ");
        		}
        	} else {
        		// using videotestsrc
                snprintf(m_caps, sizeof(m_caps), "video/x-raw,format=NV12,width=%s,height=%s,framerate=%s/1",
                		m_res_x, m_res_y, m_frame_rate);
        		m_videoconvert[0] = '\0';
        	}
#ifdef USE_DECIMATION_RATE_FILE
            int decimation_rate = get_decimation_rate_requested();
            if (decimation_rate > 0) {
                CSIO_LOG(eLogLevel_info, "--Streamout - encoder decimation rate requested=%d", decimation_rate);
            	if (strcasecmp(m_video_caps.format, "MJPG") == 0)
                    snprintf(m_videoframerate, sizeof(m_videoframerate), "videorate ! image/jpeg,framerate=%d/%d ! ",
                        m_video_caps.frame_rate_num, (m_video_caps.frame_rate_den*decimation_rate));
                else
                    snprintf(m_videoframerate, sizeof(m_videoframerate), "videorate ! video/x-raw,framerate=%d/%d ! ",
                        m_video_caps.frame_rate_num, (m_video_caps.frame_rate_den*decimation_rate));

            } else {
                m_videoframerate[0] = '\0';
            }
#else
            CSIO_LOG(eLogLevel_info, "--Streamout - encoder frame rate requested=%s", framerate);
            if (strcasecmp(m_video_caps.format, "MJPG") == 0)
                snprintf(m_videoframerate, sizeof(m_videoframerate), "videorate ! image/jpeg,framerate=%s ! ", framerate);
            else
                snprintf(m_videoframerate, sizeof(m_videoframerate), "videorate ! video/x-raw,framerate=%s ! ", framerate);
#endif
            int bitrate = get_bitrate_requested();
            if (bitrate > 0)
            {
                snprintf(m_bit_rate, sizeof(m_bit_rate), "%d", bitrate);
                CSIO_LOG(eLogLevel_info, "--Streamout - encoder video bit rate requested=%s", m_bit_rate);
            }
            CSIO_LOG(eLogLevel_info, "--Streamout - m_videoconvert=%s", m_videoconvert);
            CSIO_LOG(eLogLevel_info, "--Streamout - m_videoframerate=%s", m_videoframerate);
    		CSIO_LOG(eLogLevel_info, "--Streamout - m_videoStream=%s", ((m_videoStream)?"true":"false"));
        }

        if (m_audioStream)
        {
        	if (strcmp(m_audio_capture_device, "audiotestsrc") != 0)
        	{
        	    if (strcmp(m_audio_capture_device, "/dev/snd/pcmC2D0c") == 0)
        	    {
        	        m_aacEncode = false;
        	        m_aes67Mode = true;
        	    }
        		m_usbAudio = new UsbAudio(m_audio_capture_device);

        		CSIO_LOG(eLogLevel_info, "--Streamout - initialize audio for card %d", m_usbAudio->m_pcm_card_idx);
        		if (m_audioStream && m_usbAudio->m_pcm_card_idx != 0) {
        			m_audioStream = m_usbAudio->getAudioParams();
        		} else {
        			CSIO_LOG(eLogLevel_info, "--Streamout - invalid audio card %d", m_usbAudio->m_pcm_card_idx);
        			initError = STREAMOUT_WC_STATUS_AUDIO_INIT_ERROR;
        			m_audioStream=false;
        		}
        	}
    		CSIO_LOG(eLogLevel_info, "--Streamout - mAudioStream=%s", ((m_audioStream)?"true":"false"));
        }
    }

    return(initError);
}

void CStreamoutManager::setVideoSource(char *videoSource, int n)
{
	if (strcmp(m_video_capture_device, "videotestsrc") == 0) {
		snprintf(videoSource, n, "videotestsrc is-live=true");
	} else {
#ifdef NANOPC
		snprintf(videoSource, n, "v4l2src device=%s io-mode=4 do-timestamp=true name=v4l2src", "/dev/video10");
#else
		snprintf(videoSource, n, "v4l2src device=%s io-mode=4 do-timestamp=true name=v4l2src", m_video_capture_device);
#endif
	}
	CSIO_LOG(eLogLevel_info, "--Streamout - videoSource=%s", videoSource);
}

void CStreamoutManager::setAudioSource(char *audioSource, int n)
{
	if (strcmp(m_audio_capture_device, "audiotestsrc") == 0) {
		snprintf(audioSource, n, "audiotestsrc is-live=true ! audio/x-raw,format=S16LE,rate=48000,channels=2 ");
		useUsbAudio = false;
	} else {
		snprintf(audioSource, n, "appsrc name=wc_appsrc ");
		useUsbAudio = true;
	}
	CSIO_LOG(eLogLevel_info, "--Streamout - audioSource=%s", audioSource);
}

void CStreamoutManager::sendWcUrl(GstRTSPServer *server, char *mountPoint)
{
    // send WC URL
    if (m_streamoutMode == STREAMOUT_MODE_WIRELESSCONFERENCING) {
        gchar *ipAddr = gst_rtsp_server_get_address(server);
        char url[512];

        CSIO_LOG(eLogLevel_info, "Streamout: server ip address=%s", ipAddr);
        if (m_tls_on) {
            snprintf(url, sizeof(url), "rtsps://%s:%s@%s:8554%s", rtsp_server_username,
                     rtsp_server_password, ipAddr, mountPoint);
        } else {
            if (m_auth_on) {
                snprintf(url, sizeof(url), "rtsp://%s:%s@%s:8554%s", rtsp_server_username,
                         rtsp_server_password, ipAddr, mountPoint);
            } else {
                snprintf(url, sizeof(url), "rtsp://%s:8554%s", ipAddr, mountPoint);
            }
        }
        g_free(ipAddr);
        CSIO_LOG(eLogLevel_info, "Streamout: sending WC url=%s", url);
        csio_jni_SendWCServerURL(url);
    }
}

void CStreamoutManager::sendWcMediaPipelineError(int errModule, int errCode, const char *errMessage)
{
    // send WC Media pipeline error
    if (m_streamoutMode == STREAMOUT_MODE_WIRELESSCONFERENCING) {
        CSIO_LOG(eLogLevel_error, "Streamout: sending WC Media Pipeline error=%d", errCode);
        csio_jni_SendWCMediaError(errModule, errCode, errMessage);
    }
}

void CStreamoutManager::setSnapshotName(char* name)
{
	if( name != NULL)
	{
		strncpy( m_snapshot_name, name, MAX_STR_LEN );
	}
	else
	{
		CSIO_LOG(m_debugLevel, "Streamout: cannot set snapshot name. Name is NULL");
	}
}

void CStreamoutManager::startPreview(void* window)
{
	StartPreview(window);
}

void CStreamoutManager::pausePreview(void* window)
{
	PausePreview(window);
}

void CStreamoutManager::stopPreview(void* window)
{
	StopPreview(window);
}

int CStreamoutManager::waitForPreviewAvailable(int timeout_sec)
{
	int rtn = 0;

	rtn = WaitForPreviewAvailable(timeout_sec);

	return(rtn);
}

int CStreamoutManager::saveRawFrame (GstPad *pad, GstPadProbeInfo *buffer, gpointer user_data)
{
	//CStreamCamera *pCam = (CStreamCamera *) user_data;
	int iStatus = 0;

	if(m_camera)
	{
		if(!m_bStopTeeInProgress)
		{
			if( m_bNeedData && m_bPushRawFrames )
			{
				GstBuffer *buf;
				buf = GST_PAD_PROBE_INFO_BUFFER(buffer);

				GstFlowReturn ret = GST_FLOW_OK;

				GstBuffer *DstBuffer;
				DstBuffer = gst_buffer_copy( buf );	//TODO: See if we can avoid buffer copy

                if (currentSettingsDB->csioLogLevel >= eLogLevel_extraVerbose) {
                	GstMapInfo DstInfo;
                	gst_buffer_map(DstBuffer, &DstInfo, GST_MAP_WRITE);
                	CSIO_LOG(eLogLevel_extraVerbose, "Streamout: saveRawFrame() data=%p ref=%d writeable=%d", DstInfo.data, DstBuffer->mini_object.refcount, gst_buffer_is_writable(DstBuffer));
                	gst_buffer_unmap(DstBuffer, &DstInfo);
                }
				gst_app_src_push_buffer((GstAppSrc*)m_camera, DstBuffer);

				if( ret != GST_FLOW_OK )
				{
					CSIO_LOG(eLogLevel_error, "Streamout: saveRawFrame buffer push failed");
				}
			}
		}
	}

    return( iStatus );
}
