#ifndef __CRESSTREAMOUTMANAGER_H__
#define __CRESSTREAMOUTMANAGER_H__

//////////////////////////////////////////////////////////////////////////////

#include "../cresStreamOutProject.h"
#include "cres_rtsp-media.h"
#include "cresStreamSnapShot.h"
#include "cresPreview.h"

#include <arpa/inet.h>

class SnapShot;
class CStreamCamera;
class CStreamoutProject;
class CStreamoutManager : public CresProjBaseClass
{
public:

    CStreamoutManager();
    ~CStreamoutManager();

    void   DumpClassPara(int);
    void*  ThreadEntry();
    void setParent(CStreamoutProject* p) {m_parent = p;}
    int  startTee(void);
    int  stopTee(void);
    int  saveRawFrame(GstPad *pad, GstPadProbeInfo *buffer);
    void cleanup(void);

    void setServManagerDebugLevel(int level){ setDebugLevel(level); }

    int m_clientConnCnt;
    int m_main_loop_is_running ;

    CStreamoutEvent *m_StreamoutEvent;
    CStreamoutEventRingBuffer *m_StreamoutEventQ;
    CStreamoutProject* m_parent;

    void* CameraTeeEntry();
    /** Returns true if created, false otherwise */
    bool CreateTeeThread()
    {
        return (pthread_create(&m_tCameraTeeId, NULL, TeeThreadEntry, this) == 0);
    }
    void lockTee(){if(mTLock) mTLock->lock();}
    void unlockTee(){if(mTLock) mTLock->unlock();}
	GMainLoop *createMainLoop(void);
	void createMainContext(void);
	void freeMainContext(void);
    void* DummyClientEntry();
    bool CreateDummyClientThread()
    {
        return (pthread_create(&m_tDummyClientId, NULL, DummyClientThreadEntry, this) == 0);
    }
	int  startClient(void);
	void stopClient(void);
	GstElement *getClientPipeline(void);
	GMainLoop  *getClientMainLoop(void);

    void lockProject(){if(mLock) mLock->lock();}
    void unlockProject(){if(mLock) mLock->unlock();}

    //overload exitThread function
    void exitThread() ;

    GstRTSPMedia * m_pMedia ;
    GstElement* m_ahcsrc;
    GstElement* m_ahcsrc_q;

    pthread_t   m_tCameraTeeId;
	GstElement *m_teePipeline;
	GstElement *m_teeSource;
	GstElement *m_tee;
	GstElement *m_teeQ;
	GstElement *m_sink;
	GMainLoop  *m_teeLoop;
	gboolean    m_bNeedData;
	gboolean    m_bExit;
	gboolean    m_bStopTeeInProgress;
	gboolean    m_bContinueTee;
	GstBus     *m_bus;
	guint       m_bus_id;
	gboolean    m_bPushRawFrames;

	gboolean    m_bExitClient;
	GstElement *m_clientPipeline;
	GMainLoop  *m_clientLoop;
	GstElement *m_clientSink;

    int m_id_probe_ahcsrc_q;
    GMainLoop * getMainLoop(){return m_loop;}

    char m_rtsp_port[MAX_STR_LEN];
    char m_frame_rate[MAX_STR_LEN];
    char m_res_x[MAX_STR_LEN];
    char m_res_y[MAX_STR_LEN];
    char m_bit_rate[MAX_STR_LEN];
    char m_iframe_interval[MAX_STR_LEN];
    int m_multicast_enable;
    char m_multicast_address[MAX_STR_LEN];
    char m_stream_name[MAX_STR_LEN];
    char m_snapshot_name[MAX_STR_LEN];
    SnapShot *m_snapobj;
    pthread_t m_tSnapShotId;
    int  m_snapshot_stat;

    Preview   *m_preview;
    CStreamCamera *m_cameraobj;
    pthread_t  m_tPreviewId;
    pthread_t  m_tCameraId;
    int        m_preview_stat;
    pthread_t  m_tDummyClientId;

    void setPort(char* p){strcpy(m_rtsp_port, p);}
    void setResX(char* x){strcpy(m_res_x, x);}
    void setResY(char* y){strcpy(m_res_y, y);}
    void setFrameRate(char* r){strcpy(m_frame_rate, r);}
    void setBitRate(char* b){strcpy(m_bit_rate, b);}
    void setIFrameInterval(char* i){strcpy(m_iframe_interval, i);}
    void setMulticastEnable(int* enable){m_multicast_enable = *enable;}
    void setMulticastAddress(char* address){strcpy(m_multicast_address, address);}
    void setStreamName(char* name){strcpy(m_stream_name, name);}
    void setSnapshotName(char* name);
    char* getSnapshotName(void);
    void startPreview(void* window);
    void pausePreview(void* window);
    void stopPreview(void* window);
    int  waitForPreviewAvailable(int timeout_ms);

    GstRTSPMediaFactory *m_factory;
    GMainLoop *  m_loop;
    static void * TeeThreadEntry(void * This) {((CStreamoutManager *)This)->CameraTeeEntry(); return NULL;}
    static void * DummyClientThreadEntry(void * This) {((CStreamoutManager *)This)->DummyClientEntry(); return NULL;}
	GMainContext *m_context;  /* GLib context used to run the main loop */

private:
    Mutex* mLock;
    Mutex* mTLock;
    pthread_t _thread;
};

extern "C" gboolean cb_teeGstMsgHandler(GstBus *bus, GstMessage *msg, void *arg);
extern "C" void StopSnapShotClient(CStreamoutManager *pMgr);
extern "C" gboolean cb_clientGstMsgHandler(GstBus *bus, GstMessage *msg, void *arg);
extern "C" void cb_clientPadAddedHandler(GstElement *src, GstPad *new_pad, void *arg);

#endif //__CRESSTREAMOUTMANAGER_H__

