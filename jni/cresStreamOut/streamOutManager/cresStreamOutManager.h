#ifndef __CRESSTREAMOUTMANAGER_H__
#define __CRESSTREAMOUTMANAGER_H__

//////////////////////////////////////////////////////////////////////////////

#include "../cresStreamOutProject.h"
#include "cres_rtsp-media.h"
#include "cresStreamSnapShot.h"
#include "cresPreview.h"
#include "cresCamera.h"
#include "usbAudio.h"
#include "v4l2Video.h"

#include <arpa/inet.h>
#include <list>

typedef enum _eWCstatus
{
//mode is set come from jni.c upon initialization of project
    STREAMOUT_WC_STATUS_NOERROR = 0,
    STREAMOUT_WC_STATUS_ERROR,
    STREAMOUT_WC_STATUS_VIDEO_INIT_ERROR,
    STREAMOUT_WC_STATUS_AUDIO_INIT_ERROR,
    STREAMOUT_WC_STATUS_MAX
} eWCstatus;

//#define RTSP_CA_CERT_FILENAME  "/data/CresStreamSvc/digital_certificates/gst_ssl_ca.pem"
//#define RTSP_CERT_PEM_FILENAME "/data/CresStreamSvc/digital_certificates/gst_ssl_cert.pem"
//#define RTSP_CERT_KEY          "/data/CresStreamSvc/digital_certificates/gst_ssl_cert_decrypt.key"
//#define RTSP_CA_CERT_FILENAME  "/data/CresStreamSvc/digital_certificates/ca.pem"
#define GENERATE_CERTIFICATE
#define SERVER_CERT_PEM_FILENAME "/data/CresStreamSvc/digital_certificates/rtspserver_cert.pem"
#define SERVER_CERT_KEY          "/data/CresStreamSvc/digital_certificates/rtspserver_key.pem"
#define RTSP_CERT_PEM_FILENAME   "rtspserver_cert.pem"
#define RTSP_CERT_KEY            "rtspserver_key.pem"

class RtspClient {
public:
	GstRTSPClient *client;
	char client_ip_address[256];

	RtspClient(GstRTSPClient *c)
	{
		client = c;
		client_ip_address[0] = '\0';
	}

	~RtspClient() {}
};

class SnapShot;
class CStreamCamera;
class CStreamoutProject;
class CStreamoutManager : public CresProjBaseClass
{
public:

    CStreamoutManager(eStreamoutMode streamoutMode,int id);
    ~CStreamoutManager();

    void   DumpClassPara(int);
    void*  ThreadEntry();
    void setParent(CStreamoutProject* p) {m_parent = p;}
    int  saveRawFrame(GstPad *pad, GstPadProbeInfo *buffer, gpointer user_data);

    void setServManagerDebugLevel(int level){ setDebugLevel(level); }

    eStreamoutMode m_streamoutMode;
    int m_id;
    int m_clientConnCnt;
    int m_main_loop_is_running ;

    CStreamoutEvent *m_StreamoutEvent;
    CStreamoutEventRingBuffer *m_StreamoutEventQ;
    CStreamoutProject* m_parent;

    void lockProject(){if(mLock) mLock->lock();}
    void unlockProject(){if(mLock) mLock->unlock();}

    //overload exitThread function
    void exitThread() ;

    GstRTSPMedia * m_pMedia ;
    GstElement* m_ahcsrc;
    GstElement* m_camera;
    GstElement* m_appsrc;

	gboolean    m_bNeedData;
	gboolean    m_bExit;
	gboolean    m_bStopTeeInProgress;
	gboolean    m_bPushRawFrames;

    GMainLoop * getMainLoop(){return m_loop;}

    char m_rtsp_port[MAX_STR_LEN];
    char m_frame_rate[MAX_STR_LEN];
    char m_res_x[MAX_STR_LEN];
    char m_res_y[MAX_STR_LEN];
    char m_bit_rate[MAX_STR_LEN];
    char m_iframe_interval[MAX_STR_LEN];
    int m_quality;
    int m_multicast_enable;
    char m_multicast_address[MAX_STR_LEN];
    char m_stream_name[MAX_STR_LEN];
    char m_snapshot_name[MAX_STR_LEN];
    char m_rtsp_cert_filename[MAX_STR_LEN];
    char m_rtsp_key_filename[MAX_STR_LEN];
    char m_device_display_name[MAX_STR_LEN];
    char m_video_capture_device[MAX_STR_LEN];
    char m_audio_capture_device[MAX_STR_LEN];

    std::list<RtspClient *> m_clientList;
    bool m_auth_on;
    bool m_tls_on;
    bool m_random_user_pw;
    bool m_videoStream;
    bool m_audioStream;
    bool m_aacEncode;
    VideoCaps m_video_caps;
    char m_caps[MAX_STR_LEN];
    char m_videoconvert[MAX_STR_LEN];
    UsbAudio *m_usbAudio;

    char rtsp_server_username[MAX_STR_LEN];
    char rtsp_server_password[MAX_STR_LEN];


    void setPort(char* p){strcpy(m_rtsp_port, p);}
    void setResX(char* x){strcpy(m_res_x, x);}
    void setResY(char* y){strcpy(m_res_y, y);}
    void setFrameRate(char* r){strcpy(m_frame_rate, r);}
    void setBitRate(char* b){strcpy(m_bit_rate, b);}
    void setIFrameInterval(char* i){strcpy(m_iframe_interval, i);}
    void setQuality(int i){m_quality = i;}
    void setMulticastEnable(int* enable){m_multicast_enable = *enable;}
    void setMulticastAddress(char* address){strcpy(m_multicast_address, address);}
    void setStreamName(char* name){strcpy(m_stream_name, name);}
    void sendWcUrl(GstRTSPServer *server, char *mountPoint);
    void initWcCertificates();
    eWCstatus  initWcAudioVideo();
    void setVideoSource(char *videoSource, int n);
    void setAudioSource(char *audioSource, int n);
    void setSnapshotName(char* name);
    char* getSnapshotName(void);
    void setSecurityEnable(bool enable) {m_tls_on = enable; m_auth_on = enable;}
    void setRandomUserPwEnable(bool enable) {m_random_user_pw = enable;}
    void setUsername(char* name){strcpy(rtsp_server_username, name);}
    void setPassword(char* pw){strcpy(rtsp_server_password, pw);}
    void setVideoCaptureDevice(char *dev){strcpy(m_video_capture_device, dev);}
    void setAudioCaptureDevice(char *dev){strcpy(m_audio_capture_device, dev);}
    void startPreview(void* window);
    void pausePreview(void* window);
    void stopPreview(void* window);
    int  waitForPreviewAvailable(int timeout_ms);
    void forceMediaUnprepare(GstRTSPMedia * m_pMedia);

    static void* forceUnprepare (void * arg);

    GstRTSPMediaFactory *m_factory;
    GMainLoop *  m_loop;
    GMainContext *m_context;  /* GLib context used to run the main loop */

private:
    Mutex* mLock;
    Mutex* mTLock;
    pthread_t _thread;
};

#endif //__CRESSTREAMOUTMANAGER_H__

