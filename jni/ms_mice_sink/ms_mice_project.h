#ifndef CTL_EXTENSION_MS_MICE_SINK_PROJECT_H
#define CTL_EXTENSION_MS_MICE_SINK_PROJECT_H

#include "csioCommBase.h"
#include "ms-mice.h"

#define MSMICE_PROJ_EVNT_POLL_SLEEP_MS   1000   //1000ms

enum
{
    MS_MICE_SINK_PROJ_TIMESTAMP_INIT = 0,
    MS_MICE_SINK_PROJ_TIMESTAMP_START,
    MS_MICE_SINK_PROJ_TIMESTAMP_STOP,

    MS_MICE_SINK_PROJ_TIMESTAMP_MAX
};
enum
{
    //Note: not used for now
    MS_MICE_SINK_PROJ_TIMER_TICKS = 0,

    MS_MICE_SINK_PROJ_TIMER_MAX
};

typedef enum _e_msMiceSinkProjEvents
{
    MS_MICE_SINK_EVENTS_JNI_NOP = 0, //let this one to be zero

    MS_MICE_SINK_EVENTS_SET_PIN,

    MS_MICE_SINK_EVENTS_MAX
}e_msMiceSinkProjEvents;

typedef struct
{
    const char * pStr;
    unsigned int num;
} MSMICE_STRNUMPAIR;
#define WFD_NUM_OF(a) (sizeof (a) / sizeof *(a))

class msMiceSinkServiceClass;

class msMiceSinkProjClass : public csioThreadBaseClass
{
public:

    msMiceSinkProjClass(char* adapterAddress);
    ~msMiceSinkProjClass();

    csioTimerClockBase* msMiceSinkProjTimeArray;

    virtual void exitThread();
    virtual void DumpClassPara(int);

    void sendEvent(csioEventQueueStruct* pEvntQ);

    msMiceSinkServiceClass *m_service_obj;
    std::string m_pinStr;

    const char* getadapterAddress(){ return m_adapterAddress.c_str();}
    void setadapterAddress(std::string& str) { m_adapterAddress = str; }

    const char* getSessionPin(){ return m_pinStr.c_str();}

    std::string m_adapterAddress;
private:
    void* ThreadEntry();

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

class msMiceSinkServiceClass : public csioThreadBaseClass
{
public:

    msMiceSinkServiceClass(msMiceSinkProjClass* m_parent);
    ~msMiceSinkServiceClass();

    csioTimerClockBase* msMiceSinkSevTimeArray;

    virtual void exitThread();
    virtual void DumpClassPara(int);

    ms_mice_sink_service *m_mice_service;
    GMainLoop* m_mainLoop ;

    msMiceSinkProjClass* m_parent;
private:
    void* ThreadEntry();

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
#endif /* CTL_EXTENSION_MS_MICE_SINK_PROJECT_H */
