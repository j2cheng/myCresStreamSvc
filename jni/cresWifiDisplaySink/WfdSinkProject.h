#ifndef WFD_SINK_PROJECT_H_
#define WFD_SINK_PROJECT_H_

#include "WfdCommon.h"
#include "WfdSinkState.h"

enum
{
    WFD_SINK_SINGAL_WAIT_ERROR   = -1,
    WFD_SINK_SINGAL_WAIT_TIMEOUT = -2,

    WFD_SINK_SINGAL_WAIT__MAX
};

//Note: make sure to match with wfd_proj_timestamp_names[]
enum
{
    WFD_SINK_PROJ_TIMESTAMP_INIT = 0,
    WFD_SINK_PROJ_TIMESTAMP_START,
    WFD_SINK_PROJ_TIMESTAMP_STOP,
    WFD_SINK_PROJ_TIMESTAMP_REQ_IDR,

    WFD_SINK_PROJ_TIMESTAMP_MAX
};
enum
{
    //Note: not used for now
    WFD_SINK_PROJ_TIMER_TICKS = 0,

    WFD_SINK_PROJ_TIMER_MAX
};

typedef enum _eWfdSinkProjEvents
{
    WFD_SINK_EVENTS_JNI_NOP = 0, //let this one to be zero

    //events come from jni.cpp to the project
    WFD_SINK_EVENTS_JNI_START,
    WFD_SINK_EVENTS_JNI_STOP,
    WFD_SINK_EVENTS_JNI_REQ_IDR,
    WFD_SINK_EVENTS_JNI_GST_READY,
    WFD_SINK_EVENTS_JNI_CONFIG,

    //events from state machine --> project
    WFD_SINK_EVENTS_RTSP_IN_SESSION_EVENT ,
    WFD_SINK_EVENTS_RTSP_LEAVE_SESSION_EVENT,
    WFD_SINK_EVENTS_RTSP_SET_LATENCY_EVENT,

    WFD_SINK_EVENTS_MAX
}eWfdSinkProjEvents;


#define WFD_PROJ_EVNT_POLL_SLEEP_MS   1000   //1000ms

class wfdSinkStMachineClass;

class wfdSinkProjClass : public csioThreadBaseClass
{
public:

    wfdSinkProjClass();
    ~wfdSinkProjClass();

    csioTimerClockBase* wfdSinkProjTimeArray;

    virtual void exitThread();
    virtual void DumpClassPara(int);

    void sendEvent(csioEventQueueStruct* pEvntQ);

    char localIPName[4][32];

    const char* getLocIPName(int id){return localIPName[id];}
private:
    void* ThreadEntry();

    wfdSinkStMachineClass** m_wfdSinkStMachineTaskObjList ;

    csioEventQueueListBase* m_projEventQList;

    void* createCharArray(int size) { return new char [size]; }
    void deleteCharArray(void* buf)
    {
        if(buf)
        {
            char* tmp = (char*)buf;
            delete [] tmp;
        }
    }
};
/********** end of wfdSinkProjClass class *******************/

extern std::string g_rtspAudioCodecStr;
extern std::string g_rtspVidResRefStr ;

#endif //WFD_SINK_PROJECT_H_
