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

#define WCERROR_MODULE_NONE        0
#define WCERROR_MODULE_VIDEO       1
#define WCERROR_MODULE_AUDIO       2
#define WCERROR_MODULE_AUDIOVIDEO  3

#define WCERROR_CODE_FATAL_CLOSESESSION  -1

#define RTSP_CERTIFICATE_FOLDER_PATH  "/dev/shm/crestron/CresStreamSvc/wc"
#define RTSP_ROOT_CERT_PEM_FILENAME   "root_cert.pem"
#define RTSP_ROOT_CERT_KEY            "root_key.pem"
#define RTSP_CERT_PEM_FILENAME        "server_cert.pem"
#define RTSP_CERT_KEY                 "server_key.pem"

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
    GstElement* m_audPreQ;
    GstElement* m_audPostQ;
    GstElement* m_vidEncPreQ;
    GstElement* m_vidEncPostQ;

    int audPreQOrunsCnt;
    int audPreQUrunsCnt;
    int audPostQOrunsCnt;
    int audPostQUrunsCnt;

    int vidEncPreQOrunsCnt;
    int vidEncPreQUrunsCnt;
    int vidEncPostQOrunsCnt;
    int vidEncPostQUrunsCnt;

	gboolean    m_bNeedData;
	gboolean    m_bExit;
	gboolean    m_bStopTeeInProgress;
	gboolean    m_bPushRawFrames;

    GMainLoop * getMainLoop(){return m_loop;}

    char m_rtsp_port[MAX_STR_LEN];
    char m_codec[MAX_STR_LEN];
    char m_frame_rate[MAX_STR_LEN];
    char m_res_x[MAX_STR_LEN];
    char m_res_y[MAX_STR_LEN];
    char m_bit_rate[MAX_STR_LEN];
    char m_iframe_interval[MAX_STR_LEN];
    int m_quality;
    char m_hdmi_in_res_x[MAX_STR_LEN];
    char m_hdmi_in_res_y[MAX_STR_LEN];
    int m_multicast_enable;
    char m_multicast_address[MAX_STR_LEN];
    char m_stream_name[MAX_STR_LEN];
    char m_snapshot_name[MAX_STR_LEN];
    char m_rtsp_root_cert_filename[MAX_STR_LEN];
    char m_rtsp_root_key_filename[MAX_STR_LEN];
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
    bool m_aes67Mode;
    VideoCaps m_video_caps;
    char m_caps[MAX_STR_LEN];
    /* when colorimetry=(string)1:4:0:0 was added, the string length was not enough, hence
    increased the size */
    char m_videoconvert[MAX_STR_LEN*2];
    char m_videoframerate[MAX_STR_LEN];
    UsbAudio *m_usbAudio;

    char rtsp_server_username[MAX_STR_LEN];
    char rtsp_server_password[MAX_STR_LEN];


    void setPort(char* p){strcpy(m_rtsp_port, p);}
    void setResX(char* x){strcpy(m_res_x, x);}
    void setResY(char* y){strcpy(m_res_y, y);}
    void setFrameRate(char* r){strcpy(m_frame_rate, r);}
    void setBitRate(char* b){strcpy(m_bit_rate, b);}
    void setIFrameInterval(char* i){strcpy(m_iframe_interval, i);}
    void setCodec(char* c){strcpy(m_codec, c);}
    void setQuality(int i){m_quality = i;}
    void setHDMIInResX(char* x){strcpy(m_hdmi_in_res_x, x);}
    void setHDMIInResY(char* y){strcpy(m_hdmi_in_res_y, y);}
    void setMulticastEnable(int* enable){m_multicast_enable = *enable;}
    void setMulticastAddress(char* address){strcpy(m_multicast_address, address);}
    void setStreamName(char* name){strcpy(m_stream_name, name);}
    void sendWcUrl(GstRTSPServer *server, char *mountPoint);
    void sendWcMediaPipelineError(int errModule, int errCode, const char *errMessage);
    void initWcCertificates();
    eWCstatus  initWcAudioVideo();
    void setVideoSource(char *videoSource, int n);
    void setAudioSource(char *audioSource, int n);
    void setSnapshotName(char* name);
    char* getSnapshotName(void);
    void setSecurityEnable(bool enable) {m_tls_on = enable; m_auth_on = enable;}
    void setRandomUserPwEnable(bool enable) {m_random_user_pw = enable;}
    void setUsername(const char* name){strcpy(rtsp_server_username, name);}
    void setPassword(const char* pw){strcpy(rtsp_server_password, pw);}
    void setVideoCaptureDevice(const char *dev){strcpy(m_video_capture_device, dev);}
    void setAudioCaptureDevice(const char *dev){strcpy(m_audio_capture_device, dev);}
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

