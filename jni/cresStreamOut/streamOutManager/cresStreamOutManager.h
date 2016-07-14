#ifndef __CRESSTREAMOUTMANAGER_H__
#define __CRESSTREAMOUTMANAGER_H__

//////////////////////////////////////////////////////////////////////////////

#include "../cresStreamOutProject.h"
#include "cres_rtsp-media.h"

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
    GMainLoop * getMainLoop(){return m_loop;}

    char m_rtsp_port[MAX_STR_LEN];
    char m_frame_rate[MAX_STR_LEN];
    char m_res_x[MAX_STR_LEN];
    char m_res_y[MAX_STR_LEN];

    void setPort(char* p){strcpy(m_rtsp_port, p);}
    void setResX(char* x){strcpy(m_res_x, x);}
    void setResY(char* y){strcpy(m_res_y, y);}
    void setFrameRate(char* r){strcpy(m_frame_rate, r);}


private:
    Mutex* mLock;
    GMainLoop *  m_loop;
};
#endif __CRESSTREAMOUTMANAGER_H__
