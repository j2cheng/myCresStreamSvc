#ifndef _CRES_STREAMOUT_PROJECT_H_
#define _CRES_STREAMOUT_PROJECT_H_

#include "streamOutUtils/cresProjectBaseClass.h"
#include "streamOutUtils/cresStreamOutUtils.h"
#include "cresStreamOut.h"
#include "streamOutManager/cresStreamOutManager.h"

#define MAX_STREAM_OUT 1

#define IsValidStreamOut(a)  ( (a >= 0) && (a < MAX_STREAM_OUT) )

enum
{
    RTSP_PARAMETER_GET,
    RTSP_PARAMETER_SET
};

enum
{
 //events come from jni.c to the project
    STREAMOUT_EVENT_JNI_CMD_START = 0,
    STREAMOUT_EVENT_JNI_CMD_STOP,

    STREAMOUT_EVENT_JNI_CMD_PORT,

    STREAMOUT_EVENT_MAX
};

class CStreamoutManager;
class CStreamoutProject : public CresProjBaseClass
{
public:

    CStreamoutProject(int iId);
    ~CStreamoutProject();

    void    DumpClassPara(int);
    virtual void* ThreadEntry();

    void sendEvent(EventQueueStruct* pEvntQ);

    void setProjectDebugLevel(int level);
    void removeAllStreamoutTasks();

    void lockProject(){if(mLock) mLock->lock();}
    void unlockProject(){if(mLock) mLock->unlock();}

    CStreamoutEvent *m_projEvent;
    CStreamoutEventRingBuffer *m_projEventQ;

    struct timeval eventTime[STREAMOUT_EVENT_MAX];

    CStreamoutManager** m_StreamoutTaskObjList;

    char m_rtsp_port[MAX_STR_LEN];
    char m_frame_rate[MAX_STR_LEN];
    char m_res_x[MAX_STR_LEN];
    char m_res_y[MAX_STR_LEN];
private:
    int  m_projectID;
    Mutex* mLock;

    GMainLoop * main_loop ;
    GMainContext * main_context;
    GstRTSPMedia * pMediaPipeline ;
};

#endif /* _CRES_STREAMOUT_PROJECT_H_ */
