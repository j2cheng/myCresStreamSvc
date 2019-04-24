#ifndef WFD_SINK_STATEMACHINE_H_
#define WFD_SINK_STATEMACHINE_H_

#include "WfdCommon.h"
#include "WfdSinkProject.h"
#include "WfdSinkConnection.h"
#include "cregstplay.h" //need for MAX_STREAMS

#include "cresRTSP/cresRTSP.h"

typedef enum _eWfd_States
{
    WFD_SINK_STATES_IDLE,

    WFD_SINK_STATES_WAIT_M1_RQST,
    WFD_SINK_STATES_WAIT_M2_RESP,
    WFD_SINK_STATES_WAIT_M3_RQST,
    WFD_SINK_STATES_WAIT_M4_RQST,
    WFD_SINK_STATES_WAIT_M5_RQST,

    WFD_SINK_STATES_WAIT_GSTREAMER_PIPELINE_READY,

    WFD_SINK_STATES_WAIT_M6_RESP,
    WFD_SINK_STATES_WAIT_M7_RESP,

    WFD_SINK_STATES_KEEP_ALIVE_LOOP,

    WFD_SINK_STATES_WAIT_TD_RESP,

    WFD_SINK_STATES_MAX
}eWfd_States;

typedef enum _eWfd_Events
{
    //nothing happens, so send this time tick event
    WFD_SINK_STM_TIME_TICKS_EVENT = 0, //let this one to be zero

    //all commands stMachineThread ---> state machine object
    WFD_SINK_STM_START_CONN_EVENT,
    WFD_SINK_STM_START_TEARDOWN_EVENT,
    WFD_SINK_STM_IDR_REQ_EVENT,

    //internal events connection --> state machine
    WFD_SINK_STM_M1_RQST_RCVD_EVENT = 20,
    WFD_SINK_STM_M2_RESP_RCVD_EVENT ,
    WFD_SINK_STM_M3_RQST_RCVD_EVENT ,
    WFD_SINK_STM_M4_RQST_RCVD_EVENT ,
    WFD_SINK_STM_M5_RQST_RCVD_EVENT ,

    WFD_SINK_STM_GST_READY_RCVD_EVENT,

    WFD_SINK_STM_M6_RESP_RCVD_EVENT,
    WFD_SINK_STM_M7_RESP_RCVD_EVENT,
    WFD_SINK_STM_KEEP_ALIVE_RCVD_EVENT ,
    WFD_SINK_STM_INTERNAL_ERROR_EVENT,
    WFD_SINK_STM_TD_RESP_RCVD_EVENT,
    WFD_SINK_STM_RCVD_TEARDOWN_EVENT,

    //events from project --> state machine thread
    WFD_SINK_INSERT_STMACHINE_EVENT = 40,
    WFD_SINK_REMOVE_STMACHINE_EVENT,//do not plan to let it happen

    WFD_SINK_START_STMACHINE_EVENT,
    WFD_SINK_TEARDOWN_TCP_CONN_EVENT,
    WFD_SINK_SEND_IDR_REQ_EVENT,
    WFD_SINK_GST_READY_EVENT,

    //do not add xxx_MAX here, number jumps
}eWfd_Events;

/*** used by state machine class****/
enum
{
    WFD_SINK_STATE_TIMEOUT_TIMER = 0,

    WFD_SINK_STATE_TIMER_MAX
};

typedef enum _eWfd_StatesTO
{
    WFD_SINK_STATETIMEOUT_IDLE_RESTART                = 5000,      //retry timeout 5sec

    WFD_SINK_STATETIMEOUT_WAIT_RQST                   = 6000,      //WAIT FOR REQUEST timeout 6s
    WFD_SINK_STATETIMEOUT_WAIT_RESP                   = 5000,      //WAIT FOR response timeout 5s

    WFD_SINK_STATETIMEOUT_WAIT_GST_PIPELINE           = 15000,     //WAIT FOR gstreamer pipeline ready timeout 15s

    WFD_SINK_STATETIMEOUT_DEFAULT_KEEP_ALIVE          = 60000,     //default 60 seconds(>10s)

    //do not add xxx_MAX here, number jumps
}eeWfd_StatesTO;

typedef enum _eWfd_timeStamp
{
    WFD_SINK_EVENTTIME_STATEMACHINE_CREATED,
    WFD_SINK_EVENTTIME_RESTART_FROM_IDLESTATE,
    WFD_SINK_EVENTTIME_ENTER_KEEPALIVE_LOOP,

    WFD_SINK_EVENTTIME_MAX
}eWfd_timeStamp;

typedef enum _eWfd_trigger_methods
{
    WFD_SINK_TRIGGER_METHOD_NONE = 0,//NONE must be zero
    WFD_SINK_TRIGGER_METHOD_SETUP,
    WFD_SINK_TRIGGER_METHOD_PLAY,
    WFD_SINK_TRIGGER_METHOD_TEARDOWN,
}eWfd_trigger_methods;

/*** used by state machine thread****/
//Note: STATETHRD_SLEEP is a bit fast than STATETHRD_TICK_TO
#define WFD_SINK_STATETHRD_SLEEP_MS     200      //200ms

enum
{
    WFD_SINK_STATETHRD_TICKS_TIMER = 0,

    WFD_SINK_STATETHRD_TIMER_MAX
};

typedef enum _eWfd_StMachineThreadTO
{
    WFD_SINK_STATETHRD_TICK_TO     = 1000,      //ticks timeout 1000ms

    //do not add xxx_MAX here, number jumps
}eWfd_StMachineThreadTO;

typedef enum _eWfd_StMachineThreadTimeStamp
{
    WFD_SINK_EVENTTIME_STATETHRD_CREATED,

    WFD_SINK_EVENTTIME_STATETHRD_MAX
}eWfd_StMachineThreadTimeStamp;

#define MAX_WFD_TCP_CONN                   MAX_STREAMS                      //up to four windows
#define TOTAL_MANAGER_MASTER_FDS           (MAX_WFD_TCP_CONN + 1)           //MAX_WFD_TCP_CONN + m_pipefd
#define MANAGER_POLL_FDS_SIG_PIPE_INDEX    (TOTAL_MANAGER_MASTER_FDS - 1)   //location of pipefd at the end of array

#define IsValidStreamWindow(a)  ( (a >= 0) && (a < MAX_WFD_TCP_CONN) )

#define MAX_WFD_TCP_RETRY   5

class wfdSinkProjClass;
class wfdSinkStMachineClass;
class wfdSinkStMachineThread;
class WfdRTSPSinkClient;

#define ON_RTSPTCP_CONN       1

class wfdSinkStMachineClass
{
public:

    wfdSinkStMachineClass(int iId,wfdSinkProjClass* m_parent);
    ~wfdSinkStMachineClass();

/**************************************************************/
    static wfdSinkStMachineThread* m_wfdSinkStMachineThreadPtr;
    friend class wfdSinkStMachineThread;

private:
    //void startTcpConn();
    //void closeTcpConn();

    //called from wfdSinkStMachineThread
    void processThisSockectEvent();
    void processThisCommandEvent();
    void processThisTickEvent();
public:

    //called from parent project
    int waitWfdSinkStMachineSignal(int pollTimeoutInMs);
/**************************************************************/
public:
    int getId(){ return m_myId;}
    int getCurentState(){ return m_curentState;}
    void printCurrentState();
    const char* getCurentStateName();
    void DumpClassPara(int l);

    int stateFunction(csioEventQueueStruct* eventQ);

private:
    const char* getCurentSourceUrl(){ return m_SourceUrl.c_str();}
    void setCurentSourceUrl(std::string& str) { m_SourceUrl = str; }

    int  getCurentSourcePort() { return m_src_rtsp_port; }
    void setCurentSourcePort(int port) { m_src_rtsp_port = port; }

    int  getCurentTsPort() { return m_ts_Port; }
    void setCurentTsPort(int port) ;
public:
    const char* getConnTime(){ return m_connTime.c_str();}

    int   getRestartCnt() { return restartFromIdleCnt;}
    void  resetRestartCnt() { restartFromIdleCnt = 0;}

    void setDebugLevel(int level) ;
    int  getDebugLevel()          { return m_debugLevel; }

    void processPackets(int size, char* buf);

    static int parserCallbackFun(RTSPPARSINGRESULTS * parsResPtr, void * appArgument);
    static int parserComposeRespCallback(RTSPCOMPOSINGRESULTS * composingResPtr, void * appArgument);
    static int parserComposeRequestCallback(RTSPCOMPOSINGRESULTS * composingResPtr, void * appArgument);

    //note: this function can only be used by project.
    void removewfdSinkStMachineObj();

private:
    //list all state function here
    int idleState(csioEventQueueStruct* pEventQ);
    int waitM1RequestState(csioEventQueueStruct* pEventQ);
    int waitM2ResponseState(csioEventQueueStruct* pEventQ);
    int waitM3RequestState(csioEventQueueStruct* pEventQ);
    int waitM4RequestState(csioEventQueueStruct* pEventQ);
    int waitM5RequestState(csioEventQueueStruct* pEventQ);

    int waitGstPipelineReadyState(csioEventQueueStruct* pEventQ);

    int waitM6ResponseState(csioEventQueueStruct* pEventQ);
    int waitM7ResponseState(csioEventQueueStruct* pEventQ);

    int monitorKeepAliveState(csioEventQueueStruct* pEventQ);

    int waitTDResponseState(csioEventQueueStruct* pEventQ);

    bool   isTimeout()
    {
        if(wfdSinkStMachineTimeArray)
            return wfdSinkStMachineTimeArray->isTimeout(WFD_SINK_STATE_TIMEOUT_TIMER);
        else
            return false;
    }
    void resetTimeout()
    {
        if(wfdSinkStMachineTimeArray)
            wfdSinkStMachineTimeArray->resetTimeout(WFD_SINK_STATE_TIMEOUT_TIMER);
    }

    void   setTimeout(int inMs)
    {
        if(wfdSinkStMachineTimeArray)
            wfdSinkStMachineTimeArray->setTimeout(WFD_SINK_STATE_TIMEOUT_TIMER, inMs);
    }

    void getTimeStamp(int index)
    {
        if(wfdSinkStMachineTimeArray)
            wfdSinkStMachineTimeArray->recordEventTimeStamp(index);
    }
    //reset flags when it goes to idle
    void resetAllFlags();
    void resetSystemStatus() ;
    void prepareBeforeIdle(bool signalParent);
    void prepareForRestart();
    void sendEventToParentProj(int event);

    const char* getThisArrayNames(const WFD_STRNUMPAIR* array_names,int maxList,int mode);

    int  isOnRTSPTcpConnSet()     { return (m_onTcpConnFlag & ON_RTSPTCP_CONN);}
    void setOnRTSPTcpConnFlg()    { m_onTcpConnFlag |= ON_RTSPTCP_CONN; restartFromIdleCnt = 0;}
    void resetOnRTSPTcpConnFlg()  { m_onTcpConnFlag &= (~ON_RTSPTCP_CONN);}

    void* createCharArray(int size) { return new char [size]; }
    void deleteCharArray(void* buf)
    {
        if(buf)
        {
            char* tmp = (char*)buf;
            delete [] tmp;
        }
    }
public:

    int m_myId;
    csioTimerClockBase* wfdSinkStMachineTimeArray;
private:
    wfdSinkProjClass* m_parent;

    std::string m_SourceUrl;
    std::string m_connTime;
    std::string m_srcVersionStr;
public:
    std::string m_requestString;

    int m_debugLevel;
    int m_curentState;
private:
    int restartFromIdleCnt,m_onTcpConnFlag,m_src_rtsp_port,m_ts_Port;

    WfdRTSPSinkClient* pRTSPSinkClient;
    int m_seq_i,m_seq_j,m_keepAliveTimeout;

    csioEventQueueStruct m_EvntQ;
public:
    RTSPSYSTEMINFO m_rtspParserIntfInfo;
    void* m_rtspParserIntfSession;
private:
    int m_ssrc,m_rtcpDestPort;
};
/********** end of wfdSinkStMachineClass class *******************/

/********** wfdSinkStMachineThread class *******************/
class wfdSinkStMachineThread: public csioThreadBaseClass
{
public:
    wfdSinkStMachineThread(int debugLevel);
    ~wfdSinkStMachineThread();

    static wfdSinkStMachineClass** m_wfdSinkStMachineTaskList;
    static int  m_wfdSinkStMachineTaskListCnt;
    friend class wfdSinkStMachineClass;

    void* ThreadEntry() ;
    void DumpClassPara( int ) ;
    void exitThread() ;

    void sendEvent(csioEventQueueStruct* pEvntQ);

    //called from parent project
    int waitWfdSinkStMachineThreadSignal(int pollTimeoutInMs);
private:
    //called from this thread to signal parent project
    void signalWaitingThread(int v);
    void insertPollFds(int id,int sock);
    void removePollFds(int id,int sock,bool force = false);
    void resetAllPollFds();

    wfdSinkStMachineClass* getWfdSinkStMachineClassObj(int id);

    //used for sync between parent project
    int m_syncPipefd[2];
    int m_syncPipe_readfd   ;
    int m_syncPipe_writefd  ;

    csioEventQueueListBase *m_manThreadEventQ;

    //one more for m_pipefd (not m_syncPipefd)
    struct pollfd m_fdsMater[TOTAL_MANAGER_MASTER_FDS] ;

    int m_maxFds;

    csioTimerClockBase* wfdSinkStMachineThreadTimeArray;

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
/********** end of wfdSinkStMachineThread class *******************/
#endif //WFD_SINK_STATEMACHINE_H_
