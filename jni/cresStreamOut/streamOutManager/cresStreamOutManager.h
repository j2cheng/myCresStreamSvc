#ifndef __CRESSTREAMOUTMANAGER_H__
#define __CRESSTREAMOUTMANAGER_H__

//////////////////////////////////////////////////////////////////////////////

#include "../cresStreamOutProject.h"
#include "cres_rtsp-media.h"
#include "cresStreamSnapShot.h"

#include <arpa/inet.h>

class SnapShot;
class CStreamoutProject;
class CStreamoutManager : public CresProjBaseClass
{
public:

    CStreamoutManager();
    ~CStreamoutManager();

    void   DumpClassPara(int);
    void*  ThreadEntry();
    void setParent(CStreamoutProject* p) {m_parent = p;}

    void setServManagerDebugLevel(int level){ setDebugLevel(level); }

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
    GstElement* m_ahcsrc_q;
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
    SnapShot *m_snapobj;
    pthread_t m_tSnapShotId;

    void setPort(char* p){strcpy(m_rtsp_port, p);}
    void setResX(char* x){strcpy(m_res_x, x);}
    void setResY(char* y){strcpy(m_res_y, y);}
    void setFrameRate(char* r){strcpy(m_frame_rate, r);}
    void setBitRate(char* b){strcpy(m_bit_rate, b);}
    void setIFrameInterval(char* i){strcpy(m_iframe_interval, i);}
    void setMulticastEnable(int* enable){m_multicast_enable = *enable;}
    void setMulticastAddress(char* address){strcpy(m_multicast_address, address);}
    void setStreamName(char* name){strcpy(m_stream_name, name);}

    GstRTSPMediaFactory *m_factory;
    GMainLoop *  m_loop;
private:
    Mutex* mLock;
};
#endif //__CRESSTREAMOUTMANAGER_H__

