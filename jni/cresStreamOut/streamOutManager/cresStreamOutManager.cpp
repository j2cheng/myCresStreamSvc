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

#include <stdlib.h>
#include "cresStreamOutManager.h"
#include "gst/app/gstappsrc.h"
#include "../../shared-ssl/shared-ssl.h"

#undef NANOPC

#undef USE_VIDEOTESTSRC
#undef USE_AUDIOTESTSRC
#undef CLIENT_AUTHENTICATION_ENABLED

#ifdef USE_VIDEOTESTSRC
#define VIDEOSOURCE "videotestsrc"
#else
#ifdef NANOPC
#define VIDEOSOURCE "v4l2src device=/dev/video10 io-mode=4 do-timestamp=true"
#else
#define VIDEOSOURCE "v4l2src device=/dev/video5  io-mode=4 do-timestamp=true"
#endif
#endif

static bool PushModel = false;

#ifdef USE_AUDIOTESTSRC
#define AUDIOSOURCE "audiotestsrc ! audio/x-raw,format=S16LE,rate=48000,channels=2 "
int useUsbAudio = false;
#else
#define AUDIOSOURCE "appsrc name=wc_appsrc "
int useUsbAudio = true;
#endif

//#define AUDIOENC "amcaudenc-omxgoogleaacencoder"
#define AUDIOENC "voaacenc"

#define CRES_OVERLOAD_MEDIA_CLASS 1
#define CRES_UNPREPARE_MEDIA      1
#define MAX_RTSP_SESSIONS         5
#define	UNLIMITED_RTSP_SESSIONS   0

extern void csio_jni_SendWCServerURL( void * arg );
extern void usb_audio_get_samples(pcm *pcm_device, void *data, int size, GstClockTime *timestamp, GstClockTime *duration);

static gboolean
timeout (GstRTSPServer * server)
{
    GstRTSPSessionPool *pool;

    CSIO_LOG(eLogLevel_info, "--------------- In timeout() -----------------------");
    pool = gst_rtsp_server_get_session_pool (server);
    guint removed = gst_rtsp_session_pool_cleanup (pool);
    g_object_unref (pool);
    CSIO_LOG(eLogLevel_info, "Removed %d sessions", removed);

    return TRUE;
}

static void
client_closed (GstRTSPClient * client, void *user_data)
{
    CStreamoutManager *pMgr = (CStreamoutManager *) user_data;

    pMgr->m_clientList.remove(client);
//    if (PushModel && pMgr->m_clientList.size() == 0)
//        pMgr->m_bNeedData = false;
    CSIO_LOG(eLogLevel_info,"********** RTSP Client closed (size=%d) ****************\n", pMgr->m_clientList.size());
}

static void
client_connected (GstRTSPServer * server, GstRTSPClient * client, void *user_data)
{
    CStreamoutManager *pMgr = (CStreamoutManager *) user_data;

    pMgr->m_clientList.push_back(client);
//    if (PushModel)
//        pMgr->m_bNeedData = true;
    CSIO_LOG(eLogLevel_info,"********** RTSP Client connected (size=%d) **************\n", pMgr->m_clientList.size());
    g_signal_connect(client, "closed", (GCallback) client_closed, pMgr);
}

#ifdef CLIENT_AUTHENTICATION_ENABLED
static gboolean
accept_certificate (GstRTSPAuth *auth,
                    GTlsConnection *conn,
                    GTlsCertificate *peer_cert,
                    GTlsCertificateFlags errors,
                    gpointer user_data) {

    GError *error;
    gboolean accept = FALSE;
    GTlsCertificate *ca_cert = (GTlsCertificate *) user_data;

    CSIO_LOG(eLogLevel_verbose, "%s(): entered", __FUNCTION__);
    GTlsDatabase* database = g_tls_connection_get_database(G_TLS_CONNECTION(conn));
    if (database) {
        GSocketConnectable *peer_identity;
        GTlsCertificateFlags validation_flags;
        CSIO_LOG(eLogLevel_debug, "TLS peer certificate not accepted, checking user database...\n");
        peer_identity = NULL;
        errors =
                g_tls_database_verify_chain (database, peer_cert,
                                             G_TLS_DATABASE_PURPOSE_AUTHENTICATE_CLIENT, peer_identity,
                                             g_tls_connection_get_interaction (conn), G_TLS_DATABASE_VERIFY_NONE,
                                             NULL, &error);
        CSIO_LOG(eLogLevel_info, "*******accept-certificate******* errors value %d\n",errors);

        // Alternate verification
        if (errors == 0) {
            GTlsCertificateFlags flags = g_tls_certificate_verify(peer_cert, peer_identity, ca_cert);
            if (flags != 0)
            {
                CSIO_LOG(eLogLevel_info, "certificates did not verify susccessfully  flags=0x%x\n", flags);
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
}
#endif

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
	GstElement *ele;
	/* get the element used for providing the streams of the media */
	element = gst_rtsp_media_get_element (media);
	gchar * n = gst_element_get_name(element);
	CSIO_LOG(eLogLevel_debug, "Streamout: element name[%s] of media[0x%x]",n,media);

  //set up video source
	{
		int width  = atoi(pMgr->m_res_x);
		int height = atoi(pMgr->m_res_y);
		int framerate = atoi(pMgr->m_frame_rate);

		ele = gst_bin_get_by_name_recurse_up (GST_BIN (element), "cresappsrc");
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

    if (pMgr->m_streamoutMode == STREAMOUT_MODE_CAMERA)
        pMgr->m_bPushRawFrames = true;

	gst_object_unref (element);
	g_free(n);
}

void get_audio_data_for_pull(CStreamoutManager *pMgr, guint size)
{
    CSIO_LOG(eLogLevel_verbose, "Streamout: get_audio_data...size=%d", size);
    GstFlowReturn ret;
    int nsamples = size/(2*2);
    int n;

    GstBuffer *buffer = gst_buffer_new_allocate(NULL, size, NULL);
    GstMapInfo mapInfo;
    GstClockTime timestamp, duration;
    if (gst_buffer_map(buffer, &mapInfo, GST_MAP_WRITE)) {
    	usb_audio_get_samples(pMgr->m_usbAudio->m_device, mapInfo.data, mapInfo.size, &timestamp, &duration);
    }
    gst_buffer_unmap(buffer, &mapInfo);
    GST_BUFFER_PTS(buffer) = timestamp;
    GST_BUFFER_DURATION(buffer) = duration;

    g_signal_emit_by_name(pMgr->m_appsrc, "push_buffer", buffer, &ret);
    //ret = gst_app_src_push_buffer((GstAppSrc *)appsrc, buffer);
    if (ret == GST_FLOW_OK) {
        CSIO_LOG(eLogLevel_verbose, "Streamout: buffer pushed successfully");
    } else {
        CSIO_LOG(eLogLevel_error, "Streamout: pushed returned %d", (int) ret);
    }
    gst_buffer_unref(buffer);
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

/* called when a new media pipeline is constructed. We can query the
 * pipeline and configure our media src */
static void
wc_media_configure (GstRTSPMediaFactory * factory, GstRTSPMedia * media,
                 gpointer user_data)
{
    CStreamoutManager *pMgr = (CStreamoutManager *) user_data;
    GstElement *element;
    GstElement *ele;
    /* get the element used for providing the streams of the media */
    element = gst_rtsp_media_get_element (media);
    gchar * n = gst_element_get_name(element);
    CSIO_LOG(eLogLevel_debug, "Streamout: element name[%s] of media[0x%x]",n,media);

        //set up audio source
    if (pMgr->m_audioStream)
    {
        if (useUsbAudio && (pMgr->m_usbAudio->m_device == NULL))
        {
        	if (!pMgr->m_usbAudio->configure())
        		return;
        }

        ele = gst_bin_get_by_name_recurse_up (GST_BIN (element), "wc_appsrc");
        if(ele)
        {
            CSIO_LOG(eLogLevel_verbose, "Streamout: get_by_name wc_appsrc[%p]",ele);

//TODO: save CAPS from usb audio src
            g_object_set(G_OBJECT(ele), "caps",
                         gst_caps_new_simple(
                                 "audio/x-raw",
                                 "layout", G_TYPE_STRING, "interleaved",
                                 "format", G_TYPE_STRING, pMgr->m_usbAudio->getAudioFormat(),
                                 "rate", G_TYPE_INT, pMgr->m_usbAudio->getAudioSamplingRate(),
                                 "channels", G_TYPE_INT, pMgr->m_usbAudio->getAudioChannels(),
                                 NULL), NULL);

            g_object_set( G_OBJECT(ele), "stream-type", 0, NULL );
            g_object_set( G_OBJECT(ele), "format", GST_FORMAT_TIME, NULL );


            pMgr->m_appsrc = ele;
            g_signal_connect(ele, "need-data", G_CALLBACK(cb_wcSrcNeedData), user_data);
            g_signal_connect(ele, "enough_data", G_CALLBACK(cb_wcSrcEnoughData), user_data);

            gst_object_unref(ele);
        }

        if (pMgr->m_videoStream)
        {
            ele = gst_bin_get_by_name_recurse_up(GST_BIN (element), "vidPreQ");
            if (ele)
            {
                CSIO_LOG(eLogLevel_info, "Streamout: set leaky downstream on vidPreQ");
                g_object_set(G_OBJECT(ele), "leaky", (guint) 2 /*GST_QUEUE_LEAK_DOWNSTREAM*/, NULL);
                gst_object_unref(ele);
            }
            ele = gst_bin_get_by_name_recurse_up(GST_BIN (element), "vidPostQ");
            if (ele)
            {
                CSIO_LOG(eLogLevel_info, "Streamout: set leaky downstream on vidPostQ");
                g_object_set(G_OBJECT(ele), "leaky", (guint) 2 /*GST_QUEUE_LEAK_DOWNSTREAM*/, NULL);
                gst_object_unref(ele);
            }
        }
        if (pMgr->m_audioStream) {
            ele = gst_bin_get_by_name_recurse_up(GST_BIN (element), "audPreQ");
            if (ele) {
                CSIO_LOG(eLogLevel_info, "Streamout: set leaky downstream on audPreQ");
                g_object_set(G_OBJECT(ele), "leaky", (guint) 2 /*GST_QUEUE_LEAK_DOWNSTREAM*/, NULL);
                g_object_set(G_OBJECT(ele), "max-size-bytes", 48000, NULL);
                gst_object_unref(ele);
            }
            ele = gst_bin_get_by_name_recurse_up(GST_BIN (element), "audPostQ");
            if (ele) {
                CSIO_LOG(eLogLevel_info, "Streamout: set leaky downstream on audPostQ");
                g_object_set(G_OBJECT(ele), "leaky", (guint) 2 /*GST_QUEUE_LEAK_DOWNSTREAM*/, NULL);
                g_object_set(G_OBJECT(ele), "max-size-bytes", 48000, NULL);
                gst_object_unref(ele);
            }
        }
    }

    CSIO_LOG(eLogLevel_debug, "Streamout: set media reusable to true media[%p]",media);
    gst_rtsp_media_set_reusable (media, TRUE);

    //pass media back to manager
    pMgr->m_pMedia = media;

    ((CresRTSPMedia *)media)->m_loop = pMgr->m_loop;

    gst_object_unref (element);
    g_free(n);
}

static GstRTSPFilterResult
filter_cb (GstRTSPStream *stream, GstRTSPStreamTransport *trans,gpointer user_data)
{
    CSIO_LOG(eLogLevel_info, "Streamout: filter_cb-------stream[0x%x]---",stream);
    return GST_RTSP_FILTER_REMOVE;
}

/**********************CStreamoutManager class implementation***************************************/
CStreamoutManager::CStreamoutManager(eStreamoutMode streamoutMode):
m_clientConnCnt(0),m_loop(NULL),m_main_loop_is_running(0),
m_pMedia(NULL),m_bNeedData(false),m_bStopTeeInProgress(false),
m_bExit(false),m_bPushRawFrames(false),m_ahcsrc(NULL),m_camera(NULL),
m_appsrc(NULL), m_streamoutMode(streamoutMode)
{
    m_StreamoutEvent  = new CStreamoutEvent();

    m_StreamoutEventQ = new CStreamoutEventRingBuffer(EVNT_DEFAULT_QUEUE_SIZE);

    mLock            = new Mutex();
    mTLock           = new Mutex();

    m_multicast_address[0] = '\0';
    m_stream_name[0] = '\0';

    // FIXME - these must change to be random strings
    setUsername("user");
    setPassword("password");
    if (m_streamoutMode == STREAMOUT_MODE_WIRELESSCONFERENCING)
    {
        //m_clientList = new std::list<GstRtspClient *>();
        m_auth_on = true;
        m_tls_on = true;
        //if (m_tls_on)
            //create_selfsigned_certificate(RTSP_CERT_PEM_FILENAME, RTSP_CERT_KEY);
        m_videoStream = true;
        m_audioStream = true;
        m_aacEncode = true;

        m_usbAudio = new UsbAudio();

        if (m_audioStream && m_usbAudio->m_pcm_card_idx != 0) {
        	m_audioStream = m_usbAudio->getAudioParams();
        }
    }

    if(!m_StreamoutEvent || !m_StreamoutEventQ || !mLock)
        CSIO_LOG(eLogLevel_error, "--Streamout: CStreamoutManager malloc failed:[0x%x][0x%x][0x%x]",\
                m_StreamoutEvent,m_StreamoutEventQ,mLock);
}

CStreamoutManager::~CStreamoutManager()
{
	if (m_usbAudio)
	{
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

}
void CStreamoutManager::DumpClassPara(int level)
{
    CSIO_LOG(eLogLevel_info, "---Streamout: ThredId 0x%x", (unsigned long int)getThredId());
    CSIO_LOG(eLogLevel_info, "---Streamout: m_debugLevel %d", m_debugLevel);

    CSIO_LOG(eLogLevel_info, "---Streamout: m_parent 0x%x", m_parent);
    CSIO_LOG(eLogLevel_info, "---Streamout: m_clientConnCnt %d", m_clientConnCnt);
    CSIO_LOG(eLogLevel_info, "---Streamout: m_main_loop_is_running %d", m_main_loop_is_running);

    CSIO_LOG(eLogLevel_info, "---Streamout: m_streamoutMode %d", m_streamoutMode);
    CSIO_LOG(eLogLevel_info, "---Streamout: m_auth_on %d", (int)m_auth_on);
    CSIO_LOG(eLogLevel_info, "---Streamout: m_tls_on %d", (int)m_tls_on);

    CSIO_LOG(eLogLevel_info, "---Streamout: m_loop 0x%x", m_loop);
    CSIO_LOG(eLogLevel_info, "---Streamout: m_pMedia [0x%x]",m_pMedia);

    CSIO_LOG(eLogLevel_info, "---Streamout: m_rtsp_port %s", m_rtsp_port);
    CSIO_LOG(eLogLevel_info, "---Streamout: m_res_x %s", m_res_x);
    CSIO_LOG(eLogLevel_info, "---Streamout: m_res_y %s", m_res_y);
    CSIO_LOG(eLogLevel_info, "---Streamout: m_frame_rate %s", m_frame_rate);

    CSIO_LOG(eLogLevel_info, "---Streamout: m_bit_rate %s", m_bit_rate);
    CSIO_LOG(eLogLevel_info, "---Streamout: m_iframe_interval %s", m_iframe_interval);
}

//overloaded from base
void CStreamoutManager::exitThread()
{
    CSIO_LOG(m_debugLevel, "Streamout: try to quit g_main_loop[0x%x]",m_loop);
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
		CSIO_LOG(eLogLevel_error, "forceUnprepare timed out after %d seconds, restart txrxservice ...\n", timeout_sec);
		csio_jni_sendCameraStopFb();
		csio_jni_recoverTxrxService();
	}
	else if (result != 0)
		CSIO_LOG(eLogLevel_error, "Unknown error occurred while waiting for forceUnprepare to complete, error = %d\n", result);
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
    GstRTSPSessionPool *pool     = NULL;
    GError *error = NULL;
    char mountPoint [512];


    m_factory = NULL;

    //create new context
    context = g_main_context_new ();
    g_main_context_push_thread_default(context);
    CSIO_LOG(m_debugLevel,  "Streamout: create new context: 0x%x\n", context );
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
    CSIO_LOG(eLogLevel_info, "Streamout: server[0x%x] created",server);

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
            CSIO_LOG(eLogLevel_info, "Streamout: RTSP server's TLS configuration\n");
            cert = g_tls_certificate_new_from_files(RTSP_CERT_PEM_FILENAME, RTSP_CERT_KEY, &error);
            if (cert == NULL) {
                CSIO_LOG(eLogLevel_error, "Streamout: failed to parse PEM: %s\n", error->message);
                goto exitThread;
            }
#ifdef CLIENT_AUTHENTICATION_ENABLED
            GTlsDatabase *database = g_tls_file_database_new(RTSP_CA_CERT_FILENAME, &error);
            //GTlsDatabase *database = g_tls_file_database_new(RTSP_CERT_PEM_FILENAME, &error);
            if (database == NULL)
            {
                CSIO_LOG(eLogLevel_error, "Streamout: failed to create database from ca cert: %s\n", error->message);
                goto exitThread;
            }
            gst_rtsp_auth_set_tls_database(auth, database);

            GTlsCertificate *ca_cert = g_tls_certificate_new_from_file(RTSP_CA_CERT_FILENAME,&error);
            //ca_cert = g_tls_certificate_new_from_file("/home/enthusiasticgeek/gstreamer/cert/toyCA.pem",&error);
            if (ca_cert == NULL) {
                g_printerr ("failed to parse CA PEM: %s\n", error->message);
                goto exitThread;
            }
            gst_rtsp_auth_set_tls_authentication_mode(auth, G_TLS_AUTHENTICATION_REQUIRED);
            g_signal_connect (auth, "accept-certificate", G_CALLBACK
                    (accept_certificate), ca_cert);
            //if (ca_cert) g_object_unref(ca_cert); //TODO is this needed - had crash when put it in code
#endif
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
                                                 "rtph264pay name=pay0 pt=96 )",
                     product_info()->H264_encoder_string,
                     m_bit_rate,m_iframe_interval);
        }
        else
        {
            CSIO_LOG(eLogLevel_error, "Streamout: m_videoStream=%d m_audioStream=%d", m_videoStream, m_audioStream);

            if (m_videoStream && !m_audioStream) {
                snprintf(pipeline, sizeof(pipeline), "( %s ! "
                                                     "video/x-raw,format=NV12,width=%s,height=%s,framerate=%s/1 ! "
                                                     "queue name = vidPreQ ! "
                                                     "%s bitrate=%s i-frame-interval=%s ! "
                                                     "queue name=vidPostQ ! "
                                                     "h264parse ! "
                                                     "rtph264pay name=pay0 pt=96 )",
                                                     VIDEOSOURCE,
                                                     m_res_x, m_res_y, m_frame_rate,
                                                     product_info()->H264_encoder_string,
                                                     m_bit_rate, m_iframe_interval);
            }

            // for audio when we push have queues before and after enc, but pull model will not work like that so no queues
            char audioenc[128];
            const char *audioEncoderName = (m_aacEncode) ? AUDIOENC : "alawenc";
            snprintf(audioenc, sizeof(audioenc), "%s", audioEncoderName);

            if (m_videoStream && m_audioStream) {
                if (m_aacEncode) {
                    snprintf(pipeline, sizeof(pipeline), "( %s ! "
                                                         "video/x-raw,format=NV12,width=%s,height=%s,framerate=%s/1 ! "
                                                         "queue name=vidPreQ ! "
                                                         "%s bitrate=%s i-frame-interval=%s ! "
                                                         "queue name=vidPostQ ! "
                                                         "h264parse ! "
                                                         "rtph264pay name=pay0 pt=96 "
                                                         "%s ! audioresample ! audioconvert ! "
                                                         "%s ! "
                                                         "rtpmp4apay name=pay1 pt=97 )",
                                                         VIDEOSOURCE,
                                                         m_res_x, m_res_y, m_frame_rate,
                                                         product_info()->H264_encoder_string,
                                                         m_bit_rate, m_iframe_interval,
                                                         AUDIOSOURCE, audioenc);
                } else {
                    snprintf(pipeline, sizeof(pipeline), "( %s ! "
                                                         "video/x-raw,format=NV12,width=%s,height=%s,framerate=%s/1 ! "
                                                         "queue name=vidPreQ ! "
                                                         "%s bitrate=%s i-frame-interval=%s ! "
                                                         "queue name=vidPostQ ! "
                                                         "h264parse ! "
                                                         "rtph264pay name=pay0 pt=96 "
                                                         "%s ! audioresample ! audioconvert ! audio/x-raw,rate=8000 ! "
                                                         "%s ! "
                                                         "rtppcmapay name=pay1 pt=97 )",
                                                         VIDEOSOURCE,
                                                         m_res_x, m_res_y, m_frame_rate,
                                                         product_info()->H264_encoder_string,
                                                         m_bit_rate, m_iframe_interval,
                                                         AUDIOSOURCE, audioenc);
                }
            }
            if (!m_videoStream && m_audioStream)
            {
                if (m_aacEncode) {
                    snprintf(pipeline, sizeof(pipeline), "( %s ! audioresample ! audioconvert ! "
                                                         "%s ! "
                                                         "rtpmp4apay name=pay0 pt=97 )",
                                                         AUDIOSOURCE, audioenc);
                } else {
                    snprintf(pipeline, sizeof(pipeline), "( %s ! audioresample ! audioconvert ! audio/x-raw,rate=8000 ! "
                                                         "%s ! "
                                                         "rtppcmapay name=pay0 pt=97 )",
                                                          AUDIOSOURCE, audioenc);
                }
            }
        }
    }
    CSIO_LOG(m_debugLevel, "Streamout: rtsp server pipeline: [%s]", pipeline);
    gst_rtsp_media_factory_set_launch (m_factory, pipeline);

    if (m_auth_on) {
        /* add permissions for the user media role */
        GstRTSPPermissions *permissions = NULL;
        permissions = gst_rtsp_permissions_new();
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
    {
        if (m_streamoutMode == STREAMOUT_MODE_CAMERA)
            sprintf(mountPoint, "/%s.sdp", "camera");
        else
            sprintf(mountPoint, "/%s.sdp", "wc");
    }
    gst_rtsp_mount_points_add_factory (mounts, mountPoint, m_factory);
    g_object_unref(mounts);
    CSIO_LOG(eLogLevel_info, "Streamout: mount in wireless conferencing mode: [%s]", mountPoint);

    // send server URL to Gstreamout via jni
    sendWcUrl(server, mountPoint);

    //correct way to create source and attach to mainloop
    server_source = gst_rtsp_server_create_source(server,NULL,NULL);
    if(server_source)
    {
        CSIO_LOG(m_debugLevel, "Streamout: create_source , server_source [0x%x]", server_source);
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
        g_timeout_add_seconds(2, (GSourceFunc) timeout, server);
    }

    CSIO_LOG(eLogLevel_info, "Streamout: running main loop......");
    g_main_loop_run (m_loop);

exitThread:
    /* cleanup */
if (m_streamoutMode == STREAMOUT_MODE_CAMERA)
    StopSnapShot(this);

#ifdef CRES_UNPREPARE_MEDIA
/*   please check out this bug: https://bugzilla.gnome.org/show_bug.cgi?id=747801
 *   if it is fixed, we should take it. */

    if(m_pMedia)
    {
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

/* You must use g_source_destroy() for sources added to a non-default main context.  */
    if(server_source)
    {
        /*You must use g_source_destroy() for sources added to a non-default main context.*/
        g_source_destroy (server_source);
        g_source_unref(server_source);
        CSIO_LOG(m_debugLevel, "Streamout: g_source_destroy server_source[0x%x]",server_source);
    }

    CSIO_LOG(m_debugLevel, "Streamout: remove factory from mount points");
    if (m_factory && mounts) gst_rtsp_mount_points_remove_factory(mounts, mountPoint);
    CSIO_LOG(m_debugLevel, "Streamout: unreference mounts[0x%x]",mounts);
    if (mounts) g_object_unref (mounts);
    CSIO_LOG(m_debugLevel, "Streamout: unreference factory[0x%x]",m_factory);
    if(m_factory) g_object_unref (m_factory);
    CSIO_LOG(m_debugLevel, "Streamout: unreference server[0x%x]",server);
    if(server) g_object_unref (server);
    CSIO_LOG(m_debugLevel, "Streamout: unreference loop[0x%x]",m_loop);
    if(m_loop) g_main_loop_unref (m_loop);
    if(pool) g_object_unref (pool);

    //need to create a cleanup function and call here
    CSIO_LOG(m_debugLevel, "Streamout: reset m_loop, m_main_loop_is_running, m_pMedia");
    m_loop = NULL;
    m_main_loop_is_running = 0;
    m_pMedia = NULL;

    if(context)
    {
        CSIO_LOG(m_debugLevel, "Streamout: unreference context[0x%x]", context);
        g_main_context_pop_thread_default(context);
        g_main_context_unref (context);
        context = NULL;
    }

    CSIO_LOG(m_debugLevel, "Streamout: jni_start_rtsp_server ended------");

    //thread exit here
    m_ThreadIsRunning = 0;

    CSIO_LOG(m_debugLevel, "CStreamoutManager: exiting ThreadEntry function - returning NULL------");
    return NULL;
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
	CStreamCamera *pCam = (CStreamCamera *) user_data;
	int iStatus = 0;

	if(m_camera)
	{
		if(!m_bStopTeeInProgress)
		{
			if( m_bNeedData && m_bPushRawFrames )
			{
				GstBuffer *buf;
				buf = GST_PAD_PROBE_INFO_BUFFER(buffer);

				guint size;
				GstFlowReturn ret;

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
