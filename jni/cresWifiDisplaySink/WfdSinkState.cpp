/**
 * Copyright (C) 2017 to the present, Crestron Electronics, Inc.
 * All rights reserved.
 * No part of this software may be reproduced in any form, machine
 * or natural, without the express written consent of Crestron Electronics.
 *
 * \file        WfdSinkProject.cpp
 *
 * \brief
 *
 * \author      John Cheng
 *
 * \date        12/25/2018
 *
 * \note
 *
 *
 * \todo
 */

#include "WfdSinkState.h"

//#define  CSIO_LOG(LOGLEVEL, ...) printf(__VA_ARGS__)
const WFD_STRNUMPAIR Wfd_state_event_names[] =
{
    {"WFD_SINK_STM_TIME_TICKS_EVENT",       WFD_SINK_STM_TIME_TICKS_EVENT},
    {"WFD_SINK_STM_START_CONN_EVENT",       WFD_SINK_STM_START_CONN_EVENT},
    {"WFD_SINK_STM_START_TEARDOWN_EVENT",   WFD_SINK_STM_START_TEARDOWN_EVENT},
    {"WFD_SINK_STM_IDR_REQ_EVENT",          WFD_SINK_STM_IDR_REQ_EVENT},

    {"WFD_SINK_STM_M1_RQST_RCVD_EVENT",     WFD_SINK_STM_M1_RQST_RCVD_EVENT},
    {"WFD_SINK_STM_M2_RESP_RCVD_EVENT" ,    WFD_SINK_STM_M2_RESP_RCVD_EVENT},
    {"WFD_SINK_STM_M3_RQST_RCVD_EVENT" ,    WFD_SINK_STM_M3_RQST_RCVD_EVENT},
    {"WFD_SINK_STM_M4_RQST_RCVD_EVENT" ,    WFD_SINK_STM_M4_RQST_RCVD_EVENT},
    {"WFD_SINK_STM_M5_RQST_RCVD_EVENT" ,    WFD_SINK_STM_M5_RQST_RCVD_EVENT},
    {"WFD_SINK_STM_M6_RESP_RCVD_EVENT",     WFD_SINK_STM_M6_RESP_RCVD_EVENT},
    {"WFD_SINK_STM_M7_RESP_RCVD_EVENT",     WFD_SINK_STM_M7_RESP_RCVD_EVENT},
    {"WFD_SINK_STM_KEEP_ALIVE_RCVD_EVENT" , WFD_SINK_STM_KEEP_ALIVE_RCVD_EVENT},
    {"WFD_SINK_STM_INTERNAL_ERROR_EVENT",   WFD_SINK_STM_INTERNAL_ERROR_EVENT},
    {"WFD_SINK_STM_TD_RESP_RCVD_EVENT",     WFD_SINK_STM_TD_RESP_RCVD_EVENT},

    {"WFD_SINK_INSERT_STMACHINE_EVENT",     WFD_SINK_INSERT_STMACHINE_EVENT},
    {"WFD_SINK_REMOVE_STMACHINE_EVENT",     WFD_SINK_REMOVE_STMACHINE_EVENT},
    {"WFD_SINK_START_STMACHINE_EVENT",      WFD_SINK_START_STMACHINE_EVENT},
    {"WFD_SINK_TEARDOWN_TCP_CONN_EVENT",    WFD_SINK_TEARDOWN_TCP_CONN_EVENT},
    {"WFD_SINK_SEND_IDR_REQ_EVENT",         WFD_SINK_SEND_IDR_REQ_EVENT},

    {0,0}//terminate the list
};
int numOfWfdStateEventNamelList = WFD_NUM_OF(Wfd_state_event_names) - 1;

const WFD_STRNUMPAIR Wfd_Sink_states_names[WFD_SINK_STATES_MAX + 1] =
{
    {"WFD_SINK_STATES_IDLE" ,             WFD_SINK_STATES_IDLE},
    {"WFD_SINK_STATES_WAIT_M1_RQST" ,     WFD_SINK_STATES_WAIT_M1_RQST},
    {"WFD_SINK_STATES_WAIT_M2_RESP" ,     WFD_SINK_STATES_WAIT_M2_RESP},
    {"WFD_SINK_STATES_WAIT_M3_RQST" ,     WFD_SINK_STATES_WAIT_M3_RQST},
    {"WFD_SINK_STATES_WAIT_M4_RQST" ,     WFD_SINK_STATES_WAIT_M4_RQST},
    {"WFD_SINK_STATES_WAIT_M5_RQST" ,     WFD_SINK_STATES_WAIT_M5_RQST},
    {"WFD_SINK_STATES_WAIT_M6_RESP" ,     WFD_SINK_STATES_WAIT_M6_RESP},
    {"WFD_SINK_STATES_WAIT_M7_RESP" ,     WFD_SINK_STATES_WAIT_M7_RESP},

    {"WFD_SINK_STATES_KEEP_ALIVE_LOOP",   WFD_SINK_STATES_KEEP_ALIVE_LOOP},
    {"WFD_SINK_STATES_WAIT_TD_RESP",      WFD_SINK_STATES_WAIT_TD_RESP},

    {0,0}//terminate the list
};
int numOfWfd_SinkStateNamelList = WFD_NUM_OF(Wfd_Sink_states_names) - 1;

const WFD_STRNUMPAIR wfd_state_machine_timestamp_names[] =
{
    {"state machine created time                 " ,   WFD_SINK_EVENTTIME_STATEMACHINE_CREATED},
    {"state machine restart from idle time       " ,   WFD_SINK_EVENTTIME_RESTART_FROM_IDLESTATE},
    {"state machine enter keep alive state time  " ,   WFD_SINK_EVENTTIME_ENTER_KEEPALIVE_LOOP},

    {0,0}//terminate the list
};

wfdSinkStMachineThread*  wfdSinkStMachineClass::m_wfdSinkStMachineThreadPtr = NULL;

wfdSinkStMachineClass**  wfdSinkStMachineThread::m_wfdSinkStMachineTaskList = NULL;
int  wfdSinkStMachineThread::m_wfdSinkStMachineTaskListCnt = 0;

/********** wfdSinkStMachineClass class, used by wfdSinkProjClass *******************/
wfdSinkStMachineClass::wfdSinkStMachineClass(int iId,wfdSinkProjClass* parent):
m_myId(iId),
wfdSinkStMachineTimeArray(NULL),
m_parent(parent),
m_SourceUrl(),m_connTime(),m_session(),
m_debugLevel(eLogLevel_debug),
m_curentState(WFD_SINK_STATES_IDLE),
restartFromIdleCnt(0),
m_onTcpConnFlag(0),
m_sourcePort(DEFAULT_SOURCE_RTSP_PORT),
pRTSPSinkClient(NULL),
m_seq_i(0),m_seq_j(0),
m_keepAliveTimeout(WFD_SINK_STATETIMEOUT_DEFAULT_KEEP_ALIVE)//default 60s
{
    wfdSinkStMachineTimeArray = new csioTimerClockBase(WFD_SINK_EVENTTIME_MAX,WFD_SINK_STATE_TIMER_MAX);

    m_SourceUrl.clear();
    m_connTime.clear();
    m_session.clear();

    prepareBeforeIdle();

    getTimeStamp(WFD_SINK_EVENTTIME_STATEMACHINE_CREATED);

    resetTimeout();

    pRTSPSinkClient = new WfdRTSPSinkClient(this);

    CSIO_LOG(ABOVE_DEBUG_VERB(m_debugLevel), "wfdSinkStMachineClass: creating wfdSinkStMachineClass.\n");
}
wfdSinkStMachineClass::~wfdSinkStMachineClass()
{
    CSIO_LOG(m_debugLevel, "wfdSinkStMachineClass: ~wfdSinkStMachineClass.\n");

    if(wfdSinkStMachineTimeArray)
    {
        delete wfdSinkStMachineTimeArray;
        wfdSinkStMachineTimeArray = NULL;
    }

    //maybe redundant here
    if(m_wfdSinkStMachineThreadPtr)
    {
        m_wfdSinkStMachineThreadPtr->removePollFds(m_myId,0,true);
    }

    if(pRTSPSinkClient)
    {
        delete pRTSPSinkClient;
        pRTSPSinkClient = NULL;
    }

    CSIO_LOG(m_debugLevel, "wfdSinkStMachineClass: ~wfdSinkStMachineClass exit.\n");
}

const char* wfdSinkStMachineClass::getThisArrayNames(const WFD_STRNUMPAIR* array_names,int maxList,int mode)
{
    for(int i = 0; i < maxList; i++)
    {
        if(mode == array_names[i].num)
            return array_names[i].pStr;
        else
            continue;
    }

    return "ERROR";
}
void wfdSinkStMachineClass::printCurrentState()
{
    CSIO_LOG(eLogLevel_info, "StateMachine[%d] curentState      %s\n", m_myId,
             getThisArrayNames(Wfd_Sink_states_names,numOfWfd_SinkStateNamelList, m_curentState));
}
const char* wfdSinkStMachineClass::getCurentStateName()
{
    return getThisArrayNames(Wfd_Sink_states_names,numOfWfd_SinkStateNamelList, m_curentState);
}
void wfdSinkStMachineClass::setDebugLevel(int level)
{
    m_debugLevel = level;

    if(pRTSPSinkClient)
        pRTSPSinkClient->setDebugLevel(level);
}
void wfdSinkStMachineClass::DumpClassPara(int l)
{
    CSIO_LOG(eLogLevel_info, "wfdSinkStMachineClass: m_myId:            %d.\n",m_myId);

    CSIO_LOG(eLogLevel_info, "wfdSinkStMachineClass: m_debugLevel       %d\n", m_debugLevel);
    CSIO_LOG(eLogLevel_info, "wfdSinkStMachineClass: current state      %s\n", getCurentStateName());
    CSIO_LOG(eLogLevel_info, "wfdSinkStMachineClass: CurentSourceUrl    %s\n", getCurentSourceUrl());
    CSIO_LOG(eLogLevel_info, "wfdSinkStMachineClass: current port       %d\n", getCurentSourcePort());
    CSIO_LOG(eLogLevel_info, "wfdSinkStMachineClass: Restart count      %d\n", getRestartCnt());
    CSIO_LOG(eLogLevel_info, "wfdSinkStMachineClass: keep alive timeout %d\n", m_keepAliveTimeout/1000);

    CSIO_LOG(eLogLevel_info, "wfdSinkStMachineClass: isOnTcpDisconnSet  %d\n", isOnTcpDisconnSet());

    {
        struct timespec currentTm;
        struct timespec evtTm = {0};

        if(wfdSinkStMachineTimeArray && wfdSinkStMachineTimeArray->pTimeoutWaitEvents)
        {
            evtTm.tv_sec = wfdSinkStMachineTimeArray->pTimeoutWaitEvents[WFD_SINK_STATE_TIMEOUT_TIMER].tv_sec;
        }

        clock_gettime(CLOCK_MONOTONIC, &currentTm);
        int delta = 0;

        if(evtTm.tv_sec)
            delta = currentTm.tv_sec - evtTm.tv_sec;

        CSIO_LOG(eLogLevel_info, "wfdSinkStMachineClass: isOnTcpConnSet     %d(%d)\n", isOnTcpConnSet(),delta);
    }


    if(wfdSinkStMachineTimeArray)
    {
        char  time_string[40];
        long  milliseconds;

        for(int i = 0; i < WFD_SINK_EVENTTIME_MAX; i++)
        {
            wfdSinkStMachineTimeArray->convertTime(i,time_string,40,milliseconds);
            CSIO_LOG(eLogLevel_info, "wfdSinkStMachineClass: %s:  %s.%03ld\n",
                    wfd_state_machine_timestamp_names[i].pStr, time_string, milliseconds);
        }
    }
}

void wfdSinkStMachineClass::resetAllFlags()
{
    m_connTime.clear();
}
void wfdSinkStMachineClass::resetSystemStatus()
{
    if(pRTSPSinkClient)
    {
        pRTSPSinkClient->resetSocket();
        pRTSPSinkClient->resetReceivedBuffer();
    }//else

    if(m_wfdSinkStMachineThreadPtr)
        m_wfdSinkStMachineThreadPtr->removePollFds(m_myId,0,true);
}
void wfdSinkStMachineClass::prepareBeforeIdle()
{
    resetAllFlags();
    resetSystemStatus();

    resetOnTcpConnFlg();
    resetOnTcpDisconnFlg();
    resetTimeout();
    CSIO_LOG(m_debugLevel, "wfdSinkStMachineClass[%d]: prepareBeforeIdle\n", m_myId);
}
//Note: should be called only inside states function.
//      Making a one second delay (timeout)
void wfdSinkStMachineClass::prepareForRestart()
{
    resetAllFlags();
    resetSystemStatus();

    setTimeout(WFD_SINK_STATETIMEOUT_IDLE_RESTART);

    CSIO_LOG(m_debugLevel, "wfdSinkStMachineClass[%d]: prepareForRestart\n", m_myId);
}
//Note: this is the only place that m_curentState can be changed
int wfdSinkStMachineClass::stateFunction(csioEventQueueStruct* pEventQ)
{
    if(!pEventQ)
        return 0;

    int currenState = m_curentState;

    if(pEventQ->event_type != WFD_SINK_STM_TIME_TICKS_EVENT)
        CSIO_LOG(m_debugLevel,   "wfdSinkStMachineClass[%d]: stateFunction: m_curentState[%s], event_type[%s][%d]\n",
                  m_myId,
                  getThisArrayNames(Wfd_Sink_states_names,numOfWfd_SinkStateNamelList, m_curentState),
                  getThisArrayNames(Wfd_state_event_names,numOfWfdStateEventNamelList,pEventQ->event_type),
                  pEventQ->event_type);

    if(pRTSPSinkClient == NULL)
    {
        CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]:pRTSPSinkClient is NULL\n",m_myId);
        return 0;
    }

    switch(m_curentState)
    {
        case WFD_SINK_STATES_IDLE:
        {
            m_curentState = idleState(pEventQ);
            break;
        }
        case WFD_SINK_STATES_WAIT_M1_RQST:
        {
            m_curentState = waitM1RequestState(pEventQ);
            break;
        }

        case WFD_SINK_STATES_WAIT_M2_RESP:
        {
            m_curentState = waitM2ResponseState(pEventQ);
            break;
        }
        case WFD_SINK_STATES_WAIT_M3_RQST:
        {
            m_curentState = waitM3RequestState(pEventQ);
            break;
        }
        case WFD_SINK_STATES_WAIT_M4_RQST:
        {
            m_curentState = waitM4RequestState(pEventQ);
            break;
        }
        case WFD_SINK_STATES_WAIT_M5_RQST:
        {
            m_curentState = waitM5RequestState(pEventQ);
            break;
        }
        case WFD_SINK_STATES_WAIT_M6_RESP:
        {
            m_curentState = waitM6ResponseState(pEventQ);
            break;
        }
        case WFD_SINK_STATES_WAIT_M7_RESP:
        {
            m_curentState = waitM7ResponseState(pEventQ);
            break;
        }
        case WFD_SINK_STATES_KEEP_ALIVE_LOOP:
        {
            m_curentState = monitorKeepAliveState(pEventQ);
            break;
        }
        case WFD_SINK_STATES_WAIT_TD_RESP:
        {
            m_curentState = waitTDResponseState(pEventQ);
            break;
        }
    }

    if(pEventQ->event_type != WFD_SINK_STM_TIME_TICKS_EVENT || currenState != m_curentState)
    {
        CSIO_LOG((m_debugLevel), "wfdSinkStMachineClass[%d]: stateFunction: new state[%s]\n",
                  m_myId,getThisArrayNames(Wfd_Sink_states_names,numOfWfd_SinkStateNamelList, m_curentState));
    }
    return 0;
}
int wfdSinkStMachineClass::idleState(csioEventQueueStruct* pEventQ)
{
    if(!pEventQ)
        return m_curentState;

    int nextState = m_curentState;
    int events = pEventQ->event_type;
    int restartNow = 0;

    //Note: if ON_TCP_DISCONN is set, treat as if timeout,
    //      then check ON_TCP_CONN and reset timer.
    if(isOnTcpDisconnSet())
    {
        resetOnTcpDisconnFlg();
        restartNow =1;
    }

    switch(events)
    {
        case WFD_SINK_STM_TIME_TICKS_EVENT:
        {
            //Note: if we don't have restart wait state, this will be used as a delay
            if(isTimeout() || restartNow)
            {
                resetTimeout();

                if(isOnTcpConnSet())
                {
                    CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: idleState: restart now[%d].\n",m_myId,++restartFromIdleCnt);
                    getTimeStamp(WFD_SINK_EVENTTIME_RESTART_FROM_IDLESTATE);

                    if(pRTSPSinkClient)
                    {
                        pRTSPSinkClient->setSourceAddrPort(getCurentSourceUrl(),getCurentSourcePort());
                        int sock = pRTSPSinkClient->openConn();

                        if(sock >=0)
                        {
                            if(m_wfdSinkStMachineThreadPtr)
                                m_wfdSinkStMachineThreadPtr->insertPollFds(m_myId,sock);

                            setTimeout(WFD_SINK_STATETIMEOUT_WAIT_RQST);

                            nextState = WFD_SINK_STATES_WAIT_M1_RQST;
                        }
                        else
                        {
                            prepareForRestart();
                            CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]:RTSP connection failed\n",m_myId);
                            nextState = WFD_SINK_STATES_IDLE;
                        }
                    }//else
                }
                else
                {
                    CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: idleState: stay in idle do nothing.\n",m_myId);
                }
            }

            break;
        }
        case WFD_SINK_STM_START_CONN_EVENT:
        {
            CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: start connection: addr[%s],port[%d].\n",
                    m_myId,getCurentSourceUrl(),getCurentSourcePort());

            setOnTcpConnFlg();;

            if(pRTSPSinkClient)
            {
                pRTSPSinkClient->setSourceAddrPort(getCurentSourceUrl(),getCurentSourcePort());
                int sock = pRTSPSinkClient->openConn();

                if(sock >=0)
                {
                    if(m_wfdSinkStMachineThreadPtr)
                        m_wfdSinkStMachineThreadPtr->insertPollFds(m_myId,sock);

                    setTimeout(WFD_SINK_STATETIMEOUT_WAIT_RQST);

                    nextState = WFD_SINK_STATES_WAIT_M1_RQST;
                }
                else
                {
                    prepareForRestart();
                    CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]:RTSP connection failed\n",m_myId);
                    nextState = WFD_SINK_STATES_IDLE;
                }
            }//else

            break;
        }
        case WFD_SINK_STM_START_TEARDOWN_EVENT:
        {
            prepareBeforeIdle();
            break;
        }
        default:
        {
            CSIO_LOG(eLogLevel_info,   "wfdSinkStMachineClass[%d]: idleState: unprocessed events[%s].\n",
                     m_myId,
                     getThisArrayNames(Wfd_state_event_names,numOfWfdStateEventNamelList,events));
            break;
        }
    }

    return nextState;
}
int wfdSinkStMachineClass::waitM1RequestState(csioEventQueueStruct* pEventQ)
{
    if(!pEventQ)
        return m_curentState;

    int nextState = m_curentState;
    int events = pEventQ->event_type;

    switch(events)
    {
        case WFD_SINK_STM_TIME_TICKS_EVENT:
        {
            //Note: if we don't have restart wait state, this will be used as a delay
            if(isTimeout())
            {
                resetTimeout();

                CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: waitM1RequestState: timeout.\n",m_myId);

                prepareForRestart();

                nextState = WFD_SINK_STATES_IDLE;
            }

            break;
        }
        case WFD_SINK_STM_INTERNAL_ERROR_EVENT:
        case WFD_SINK_STM_START_TEARDOWN_EVENT:
        {
            //TODO: send out tear down message
            if(pRTSPSinkClient)
            {
                std::string M8Tdrqst = "TEARDWON rtsp://10.254.44.15/wfd1.0/streamid=0 RTSP/1.0\r\nSession: 421764282\r\nCSeq: 3\r\n\r\n";
                pRTSPSinkClient->sendDataOut((char*)M8Tdrqst.c_str(),M8Tdrqst.size());

                if(events == WFD_SINK_STM_START_TEARDOWN_EVENT)
                {
                    resetOnTcpConnFlg();
                    setOnTcpDisconnFlg();
                }

                setTimeout(WFD_SINK_STATETIMEOUT_WAIT_RESP);

                nextState = WFD_SINK_STATES_WAIT_TD_RESP;
            }//else

            break;
        }
        case WFD_SINK_STM_M1_RQST_RCVD_EVENT:
        {
            //TODO: send out M1 response
            //TODO: and send out M2 request
            if(pRTSPSinkClient)
            {
                std::string m1resp = "RTSP/1.0 200 OK\r\nPublic: \"org.wfa.wfd1.0, GET_PARAMETER, SET_PARAMETER\"\r\nCSeq: 1\r\n\r\n";
                pRTSPSinkClient->sendDataOut((char*)m1resp.c_str(),m1resp.size());

                std::string m2rqst = "OPTIONS * RTSP/1.0\r\nRequire: org.wfa.wfd1.0\r\nCSeq: 1\r\n\r\n";
                pRTSPSinkClient->sendDataOut((char*)m2rqst.c_str(),m2rqst.size());

                CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: WFD_SINK_STM_M1_RQST_RCVD_EVENT processed.\n",m_myId);

                setTimeout(WFD_SINK_STATETIMEOUT_WAIT_RESP);

                nextState = WFD_SINK_STATES_WAIT_M2_RESP;
            }
            else
            {
                prepareForRestart();
                CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]:pRTSPSinkClient is NULL\n",m_myId);

                nextState = WFD_SINK_STATES_IDLE;
            }

            break;
        }
        case WFD_SINK_STM_START_CONN_EVENT:
        {
            setOnTcpConnFlg();
            break;
        }
        default:
        {
            CSIO_LOG(eLogLevel_info,   "wfdSinkStMachineClass[%d]: waitM1RequestState: unprocessed events[%s].\n",
                     m_myId,
                     getThisArrayNames(Wfd_state_event_names,numOfWfdStateEventNamelList,events));
            break;
        }
    }

    return nextState;
}
int wfdSinkStMachineClass::waitM2ResponseState(csioEventQueueStruct* pEventQ)
{
    if(!pEventQ)
        return m_curentState;

    int nextState = m_curentState;
    int events = pEventQ->event_type;

    switch(events)
    {
        case WFD_SINK_STM_TIME_TICKS_EVENT:
        {
            //Note: if we don't have restart wait state, this will be used as a delay
            if(isTimeout())
            {
                resetTimeout();

                CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: waitM2ResponseState: timeout.\n",m_myId);

                prepareForRestart();

                nextState = WFD_SINK_STATES_IDLE;
            }

            break;
        }
        case WFD_SINK_STM_INTERNAL_ERROR_EVENT:
        case WFD_SINK_STM_START_TEARDOWN_EVENT:
        {
            //TODO: send out tear down message
            if(pRTSPSinkClient)
            {
                std::string M8Tdrqst = "TEARDWON rtsp://10.254.44.15/wfd1.0/streamid=0 RTSP/1.0\r\nSession: 421764282\r\nCSeq: 3\r\n\r\n";
                pRTSPSinkClient->sendDataOut((char*)M8Tdrqst.c_str(),M8Tdrqst.size());

                if(events == WFD_SINK_STM_START_TEARDOWN_EVENT)
                {
                    resetOnTcpConnFlg();
                    setOnTcpDisconnFlg();
                }

                setTimeout(WFD_SINK_STATETIMEOUT_WAIT_RESP);

                nextState = WFD_SINK_STATES_WAIT_TD_RESP;
            }//else

            break;
        }
        case WFD_SINK_STM_M2_RESP_RCVD_EVENT:
        {
            resetTimeout();

            CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: WFD_SINK_STM_M2_RESP_RCVD_EVENT processed.\n",m_myId);

            setTimeout(WFD_SINK_STATETIMEOUT_WAIT_RQST);

            nextState = WFD_SINK_STATES_WAIT_M3_RQST;

            break;
        }
        case WFD_SINK_STM_START_CONN_EVENT:
        {
            setOnTcpConnFlg();
            break;
        }
        default:
        {
            CSIO_LOG(eLogLevel_info,   "wfdSinkStMachineClass[%d]: waitM2ResponseState: unprocessed events[%s].\n",
                     m_myId,
                     getThisArrayNames(Wfd_state_event_names,numOfWfdStateEventNamelList,events));
            break;
        }
    }

    return nextState;
}
int wfdSinkStMachineClass::waitM3RequestState(csioEventQueueStruct* pEventQ)
{
    if(!pEventQ)
        return m_curentState;

    int nextState = m_curentState;
    int events = pEventQ->event_type;

    switch(events)
    {
        case WFD_SINK_STM_TIME_TICKS_EVENT:
        {
            //Note: if we don't have restart wait state, this will be used as a delay
            if(isTimeout())
            {
                resetTimeout();

                CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]:: waitM3RequestState: timeout.\n",m_myId);

                prepareForRestart();

                nextState = WFD_SINK_STATES_IDLE;
            }

            break;
        }
        case WFD_SINK_STM_INTERNAL_ERROR_EVENT:
        case WFD_SINK_STM_START_TEARDOWN_EVENT:
        {
            //TODO: send out tear down message
            if(pRTSPSinkClient)
            {
                std::string M8Tdrqst = "TEARDWON rtsp://10.254.44.15/wfd1.0/streamid=0 RTSP/1.0\r\nSession: 421764282\r\nCSeq: 3\r\n\r\n";
                pRTSPSinkClient->sendDataOut((char*)M8Tdrqst.c_str(),M8Tdrqst.size());

                if(events == WFD_SINK_STM_START_TEARDOWN_EVENT)
                {
                    resetOnTcpConnFlg();
                    setOnTcpDisconnFlg();
                }

                setTimeout(WFD_SINK_STATETIMEOUT_WAIT_RESP);

                nextState = WFD_SINK_STATES_WAIT_TD_RESP;
            }//else

            break;
        }
        case WFD_SINK_STM_M3_RQST_RCVD_EVENT:
        {
            //TODO: send out M3 response
            CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: WFD_SINK_STM_M3_RQST_RCVD_EVENT processed.\n",m_myId);

            if(pRTSPSinkClient)
            {
                std::string m3rqst = "RTSP/1.0 200 OK\r\n"
                        "Content-Length: 1028\r\n"
                        "Content-Type: text/parameters\r\n"
                        "CSeq: 2\r\n\r\n"
                        "wfd_content_protection: none\r\n"
                        "wfd_video_formats: 40 00 01 10 0001bdeb 051557ff 00000fff 10 0000 001f 11 0780 0438, 02 10 0001bdeb 155557ff 00000fff 10 0000 001f 11 0780 0438\r\n"
                        "wfd_audio_codecs: LPCM 00000003 00, AAC 0000000f 00, AC3 00000007 00\r\n"
                        "wfd_client_rtp_ports: RTP/AVP/UDP;unicast 1991 0 mode=play\r\n"
                        "wfd_content_protection: none\r\n"
                        "wfd_display_edid: 0001 00ffffffffffff0051f38f50010000000e100104a51d10ff2f0000a057499b2610484f000000010101010101010101010101010101011a36809c70381f403020350025a510000018000000fc00496e7465726e616c204c43440a000000fd003c3c9a9a0e00000000000000000000000000000000000000000000000000000030\r\n"
                        "wfd_connector_type: 05\r\n"
                        "microsoft_cursor: none\r\n"
                        "microsoft_rtcp_capability: none\r\n"
                        "wfd_idr_request_capability: 1\r\n"
                        "microsoft_latency_management_capability: none\r\n"
                        "microsoft_format_change_capability: none\r\n"
                        "microsoft_diagnostics_capability: none\r\n"
                        "intel_friendly_name: miraclecast\r\n"
                        "intel_sink_manufacturer_name: GNU Linux\r\n"
                        "intel_sink_model_name: Arch linux\r\n"
                        "intel_sink_device_URL: http://github.com/albfan/miraclecast\r\n"
                        "SET_PARAMETER rtsp://localhost/wfd1.0 RTSP/1.0\r\n";

                pRTSPSinkClient->sendDataOut((char*)m3rqst.c_str(),m3rqst.size());

                setTimeout(WFD_SINK_STATETIMEOUT_WAIT_RQST);

                nextState = WFD_SINK_STATES_WAIT_M4_RQST;
            }
            else
            {
                prepareForRestart();
                CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]:pRTSPSinkClient is NULL\n",m_myId);
                nextState = WFD_SINK_STATES_IDLE;
            }

            break;
        }
        case WFD_SINK_STM_START_CONN_EVENT:
        {
            setOnTcpConnFlg();
            break;
        }
        default:
        {
            CSIO_LOG(eLogLevel_info,   "wfdSinkStMachineClass[%d]: waitM3RequestState: unprocessed events[%s].\n",
                     m_myId,
                     getThisArrayNames(Wfd_state_event_names,numOfWfdStateEventNamelList,events));
            break;
        }
    }

    return nextState;
}
int wfdSinkStMachineClass::waitM4RequestState(csioEventQueueStruct* pEventQ)
{
    if(!pEventQ)
        return m_curentState;

    int nextState = m_curentState;
    int events = pEventQ->event_type;

    switch(events)
    {
        case WFD_SINK_STM_TIME_TICKS_EVENT:
        {
            //Note: if we don't have restart wait state, this will be used as a delay
            if(isTimeout())
            {
                resetTimeout();

                CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: waitM4RequestState: timeout.\n",m_myId);

                prepareForRestart();

                nextState = WFD_SINK_STATES_IDLE;
            }

            break;
        }
        case WFD_SINK_STM_INTERNAL_ERROR_EVENT:
        case WFD_SINK_STM_START_TEARDOWN_EVENT:
        {
            //TODO: send out tear down message
            if(pRTSPSinkClient)
            {
                std::string M8Tdrqst = "TEARDWON rtsp://10.254.44.15/wfd1.0/streamid=0 RTSP/1.0\r\nSession: 421764282\r\nCSeq: 3\r\n\r\n";
                pRTSPSinkClient->sendDataOut((char*)M8Tdrqst.c_str(),M8Tdrqst.size());

                if(events == WFD_SINK_STM_START_TEARDOWN_EVENT)
                {
                    resetOnTcpConnFlg();
                    setOnTcpDisconnFlg();
                }

                setTimeout(WFD_SINK_STATETIMEOUT_WAIT_RESP);

                nextState = WFD_SINK_STATES_WAIT_TD_RESP;
            }//else

            break;
        }
        case WFD_SINK_STM_M4_RQST_RCVD_EVENT:
        {
            //TODO: send out M4 response
            CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: WFD_SINK_STM_M4_RQST_RCVD_EVENT processed.\n",m_myId);

            if(pRTSPSinkClient)
            {
                std::string m4resp = "RTSP/1.0 200 OK\r\nCSeq: 3\r\n\r\n";

                pRTSPSinkClient->sendDataOut((char*)m4resp.c_str(),m4resp.size());

                setTimeout(WFD_SINK_STATETIMEOUT_WAIT_RQST);

                nextState = WFD_SINK_STATES_WAIT_M5_RQST;
            }
            else
            {
                prepareForRestart();
                CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]:pRTSPSinkClient is NULL\n",m_myId);
                nextState = WFD_SINK_STATES_IDLE;
            }

            break;
        }
        case WFD_SINK_STM_START_CONN_EVENT:
        {
            setOnTcpConnFlg();
            break;
        }
        default:
        {
            CSIO_LOG(eLogLevel_info,   "wfdSinkStMachineClass[%d]: waitM4RequestState: unprocessed events[%s].\n",
                     m_myId,
                     getThisArrayNames(Wfd_state_event_names,numOfWfdStateEventNamelList,events));
            break;
        }
    }

    return nextState;
}
int wfdSinkStMachineClass::waitM5RequestState(csioEventQueueStruct* pEventQ)
{
    if(!pEventQ)
        return m_curentState;

    int nextState = m_curentState;
    int events = pEventQ->event_type;

    switch(events)
    {
        case WFD_SINK_STM_TIME_TICKS_EVENT:
        {
            //Note: if we don't have restart wait state, this will be used as a delay
            if(isTimeout())
            {
                resetTimeout();

                CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: waitM5RequestState: timeout.\n",m_myId);

                prepareForRestart();

                nextState = WFD_SINK_STATES_IDLE;
            }

            break;
        }
        case WFD_SINK_STM_INTERNAL_ERROR_EVENT:
        case WFD_SINK_STM_START_TEARDOWN_EVENT:
        {
            //TODO: send out tear down message
            if(pRTSPSinkClient)
            {
                std::string M8Tdrqst = "TEARDWON rtsp://10.254.44.15/wfd1.0/streamid=0 RTSP/1.0\r\nSession: 421764282\r\nCSeq: 3\r\n\r\n";
                pRTSPSinkClient->sendDataOut((char*)M8Tdrqst.c_str(),M8Tdrqst.size());

                if(events == WFD_SINK_STM_START_TEARDOWN_EVENT)
                {
                    resetOnTcpConnFlg();
                    setOnTcpDisconnFlg();
                }

                setTimeout(WFD_SINK_STATETIMEOUT_WAIT_RESP);

                nextState = WFD_SINK_STATES_WAIT_TD_RESP;
            }//else

            break;
        }
        case WFD_SINK_STM_M5_RQST_RCVD_EVENT:
        {
            //TODO: send out M5 response
            //TODO: need to get what kind of trigger.
            //TODO: send out M6 request
            CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: WFD_SINK_STM_M5_RQST_RCVD_EVENT processed.\n",m_myId);

            if(pRTSPSinkClient)
            {
                std::string m5resp = "RTSP/1.0 200 OK\r\nCSeq: 4\r\n\r\n";
                pRTSPSinkClient->sendDataOut((char*)m5resp.c_str(),m5resp.size());

                //Note: processing M5 response trigger setup/play/pause
                std::string m6rqst = "SETUP rtsp://10.254.44.15/wfd1.0/streamid=0 RTSP/1.0\r\nTransport: RTP/AVP/UDP;unicast;client_port=1991\r\nCSeq: 2\r\n\r\n";
                pRTSPSinkClient->sendDataOut((char*)m6rqst.c_str(),m6rqst.size());

                setTimeout(WFD_SINK_STATETIMEOUT_WAIT_RESP);

                nextState = WFD_SINK_STATES_WAIT_M6_RESP;
            }
            else
            {
                prepareForRestart();
                CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]:pRTSPSinkClient is NULL\n",m_myId);
                nextState = WFD_SINK_STATES_IDLE;
            }

            break;
        }
        case WFD_SINK_STM_START_CONN_EVENT:
        {
            setOnTcpConnFlg();
            break;
        }
        default:
        {
            CSIO_LOG(eLogLevel_info,   "wfdSinkStMachineClass[%d]: waitM5RequestState: unprocessed events[%s].\n",
                     m_myId,
                     getThisArrayNames(Wfd_state_event_names,numOfWfdStateEventNamelList,events));
            break;
        }
    }

    return nextState;
}
int wfdSinkStMachineClass::waitM6ResponseState(csioEventQueueStruct* pEventQ)
{
    if(!pEventQ)
        return m_curentState;

    int nextState = m_curentState;
    int events = pEventQ->event_type;

    switch(events)
    {
        case WFD_SINK_STM_TIME_TICKS_EVENT:
        {
            //Note: if we don't have restart wait state, this will be used as a delay
            if(isTimeout())
            {
                resetTimeout();

                CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: waitM6ResponseState: timeout.\n",m_myId);

                prepareForRestart();

                nextState = WFD_SINK_STATES_IDLE;
            }

            break;
        }
        case WFD_SINK_STM_INTERNAL_ERROR_EVENT:
        case WFD_SINK_STM_START_TEARDOWN_EVENT:
        {
            //TODO: send out tear down message
            if(pRTSPSinkClient)
            {
                std::string M8Tdrqst = "TEARDWON rtsp://10.254.44.15/wfd1.0/streamid=0 RTSP/1.0\r\nSession: 421764282\r\nCSeq: 3\r\n\r\n";
                pRTSPSinkClient->sendDataOut((char*)M8Tdrqst.c_str(),M8Tdrqst.size());

                if(events == WFD_SINK_STM_START_TEARDOWN_EVENT)
                {
                    resetOnTcpConnFlg();
                    setOnTcpDisconnFlg();
                }

                setTimeout(WFD_SINK_STATETIMEOUT_WAIT_RESP);

                nextState = WFD_SINK_STATES_WAIT_TD_RESP;
            }//else

            break;
        }
        case WFD_SINK_STM_M6_RESP_RCVD_EVENT:
        {
            //TODO: save session number(m_session) and timeout(m_keepAliveTimeout in ms) here
            //TODO: send out M7 request
            CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: WFD_SINK_STM_M6_RESP_RCVD_EVENT processed.\n",m_myId);

            if(pRTSPSinkClient)
            {
                std::string m7rqst = "PLAY rtsp://10.254.44.15/wfd1.0/streamid=0 RTSP/1.0\r\nSession: 421764282\r\nCSeq: 3\r\n\r\n";
                pRTSPSinkClient->sendDataOut((char*)m7rqst.c_str(),m7rqst.size());

                setTimeout(WFD_SINK_STATETIMEOUT_WAIT_RESP);

                nextState = WFD_SINK_STATES_WAIT_M7_RESP;
            }
            else
            {
                prepareForRestart();
                CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]:pRTSPSinkClient is NULL\n",m_myId);
                nextState = WFD_SINK_STATES_IDLE;
            }

            break;
        }
        case WFD_SINK_STM_START_CONN_EVENT:
        {
            setOnTcpConnFlg();
            break;
        }
        default:
        {
            CSIO_LOG(eLogLevel_info,   "wfdSinkStMachineClass[%d]: waitM6ResponseState: unprocessed events[%s].\n",
                     m_myId,
                     getThisArrayNames(Wfd_state_event_names,numOfWfdStateEventNamelList,events));
            break;
        }
    }

    return nextState;
}
int wfdSinkStMachineClass::waitM7ResponseState(csioEventQueueStruct* pEventQ)
{
    if(!pEventQ)
        return m_curentState;

    int nextState = m_curentState;
    int events = pEventQ->event_type;

    switch(events)
    {
        case WFD_SINK_STM_TIME_TICKS_EVENT:
        {
            //Note: if we don't have restart wait state, this will be used as a delay
            if(isTimeout())
            {
                resetTimeout();

                CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: waitM7ResponseState: timeout.\n",m_myId);

                prepareForRestart();

                nextState = WFD_SINK_STATES_IDLE;
            }

            break;
        }
        case WFD_SINK_STM_INTERNAL_ERROR_EVENT:
        case WFD_SINK_STM_START_TEARDOWN_EVENT:
        {
            //TODO: send out tear down message
            if(pRTSPSinkClient)
            {
                std::string M8Tdrqst = "TEARDWON rtsp://10.254.44.15/wfd1.0/streamid=0 RTSP/1.0\r\nSession: 421764282\r\nCSeq: 3\r\n\r\n";
                pRTSPSinkClient->sendDataOut((char*)M8Tdrqst.c_str(),M8Tdrqst.size());

                if(events == WFD_SINK_STM_START_TEARDOWN_EVENT)
                {
                    resetOnTcpConnFlg();
                    setOnTcpDisconnFlg();
                }

                setTimeout(WFD_SINK_STATETIMEOUT_WAIT_RESP);

                nextState = WFD_SINK_STATES_WAIT_TD_RESP;
            }//else

            break;
        }
        case WFD_SINK_STM_M7_RESP_RCVD_EVENT:
        {
            CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: WFD_SINK_STM_M7_RESP_RCVD_EVENT processed.\n",m_myId);

            resetRestartCnt();
            getTimeStamp(WFD_SINK_EVENTTIME_ENTER_KEEPALIVE_LOOP);

            setTimeout(m_keepAliveTimeout);

            nextState = WFD_SINK_STATES_KEEP_ALIVE_LOOP;

            break;
        }
        case WFD_SINK_STM_START_CONN_EVENT:
        {
            setOnTcpConnFlg();
            break;
        }
        default:
        {
            CSIO_LOG(eLogLevel_info,   "wfdSinkStMachineClass[%d]: waitM7ResponseState: unprocessed events[%s].\n",
                     m_myId,
                     getThisArrayNames(Wfd_state_event_names,numOfWfdStateEventNamelList,events));
            break;
        }
    }

    return nextState;
}
int wfdSinkStMachineClass::waitTDResponseState(csioEventQueueStruct* pEventQ)
{
    if(!pEventQ)
        return m_curentState;

    int nextState = m_curentState;
    int events = pEventQ->event_type;

    switch(events)
    {
        case WFD_SINK_STM_TIME_TICKS_EVENT:
        {
            //Note: if we don't have restart wait state, this will be used as a delay
            if(isTimeout())
            {
                resetTimeout();

                CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: waitTDResponseState: timeout.\n",m_myId);

                //Note: in transition to idle, only check ON_TCP_CONN flag
                if(isOnTcpConnSet())
                    prepareForRestart();
                else
                    prepareBeforeIdle();

                nextState = WFD_SINK_STATES_IDLE;
            }

            break;
        }
        case WFD_SINK_STM_TD_RESP_RCVD_EVENT:
        case WFD_SINK_STM_INTERNAL_ERROR_EVENT:
        {
            CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: WFD_SINK_STM_TD_RESP_RCVD_EVENT processed.\n",m_myId);

            //Note: in transition to idle, only check ON_TCP_CONN flag
            if(isOnTcpConnSet())
                prepareForRestart();
            else
                prepareBeforeIdle();

            nextState = WFD_SINK_STATES_IDLE;

            break;
        }
        case WFD_SINK_STM_START_CONN_EVENT:
        {
            setOnTcpConnFlg();
            break;
        }
        case WFD_SINK_STM_START_TEARDOWN_EVENT:
        {
            resetOnTcpConnFlg();
            setOnTcpDisconnFlg();
            break;
        }
        default:
        {
            CSIO_LOG(eLogLevel_info,   "wfdSinkStMachineClass[%d]: waitTDResponseState: unprocessed events[%s].\n",
                     m_myId,
                     getThisArrayNames(Wfd_state_event_names,numOfWfdStateEventNamelList,events));
            break;
        }
    }

    return nextState;
}
int wfdSinkStMachineClass::monitorKeepAliveState(csioEventQueueStruct* pEventQ)
{
    if(!pEventQ)
       return m_curentState;

    int nextState = m_curentState;
    int events = pEventQ->event_type;

    switch(events)
    {
        case WFD_SINK_STM_TIME_TICKS_EVENT:
        {
            if(isTimeout())
            {
                resetTimeout();

                CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: monitorKeepAliveState: timeout.\n",m_myId);

                //Note: go to idle without teardown
                prepareForRestart();

                nextState = WFD_SINK_STATES_IDLE;
            }

            break;
        }
        case WFD_SINK_STM_INTERNAL_ERROR_EVENT:
        case WFD_SINK_STM_START_TEARDOWN_EVENT:
        {
            //TODO: send out tear down message
            if(pRTSPSinkClient)
            {
                std::string M8Tdrqst = "TEARDWON rtsp://10.254.44.15/wfd1.0/streamid=0 RTSP/1.0\r\nSession: 421764282\r\nCSeq: 3\r\n\r\n";
                pRTSPSinkClient->sendDataOut((char*)M8Tdrqst.c_str(),M8Tdrqst.size());

                if(events == WFD_SINK_STM_START_TEARDOWN_EVENT)
                {
                    resetOnTcpConnFlg();
                    setOnTcpDisconnFlg();
                }

                setTimeout(WFD_SINK_STATETIMEOUT_WAIT_RESP);

                nextState = WFD_SINK_STATES_WAIT_TD_RESP;
            }//else

            break;
        }
        case WFD_SINK_STM_KEEP_ALIVE_RCVD_EVENT:
        {
            setTimeout(m_keepAliveTimeout);

            //TODO: send out response
            CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: WFD_SINK_STM_KEEP_ALIVE_RCVD_EVENT processed.\n",m_myId);

            if(pRTSPSinkClient)
            {
                std::string m16resp = "RTSP/1.0 200 OK\r\nCSeq: 5\r\n\r\n";

                pRTSPSinkClient->sendDataOut((char*)m16resp.c_str(),m16resp.size());

            }
            else
            {
                prepareForRestart();
                CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]:pRTSPSinkClient is NULL\n",m_myId);
                nextState = WFD_SINK_STATES_IDLE;
            }
            break;
        }
        case WFD_SINK_STM_IDR_REQ_EVENT:
        {
            //TODO: send out idr request
            CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: WFD_SINK_STM_IDR_REQ_EVENT processed.\n",m_myId);

            if(pRTSPSinkClient)
            {
                std::string m13rqst = "SET_PARAMETER rtsp://localhost/wfd1.0 RTSP/1.0\r\nContent-Length: 20\r\nContent-Type: text/parameters\r\nCSeq: 4\r\n\r\nwfd-idr-request: 1\r\n";

                pRTSPSinkClient->sendDataOut((char*)m13rqst.c_str(),m13rqst.size());

            }
            else
            {
                prepareForRestart();
                CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]:pRTSPSinkClient is NULL\n",m_myId);
                nextState = WFD_SINK_STATES_IDLE;
            }
            break;
        }
        case WFD_SINK_STM_START_CONN_EVENT:
        {
            setOnTcpConnFlg();
            break;
        }
        default:
        {
            CSIO_LOG(eLogLevel_info,   "wfdSinkStMachineClass[%d]: monitorKeepAliveState: unprocessed events[%s].\n",
                    m_myId,getThisArrayNames(Wfd_state_event_names,numOfWfdStateEventNamelList,events));
            break;
        }
    }

    return nextState;
}

//called from parent project
int wfdSinkStMachineClass::waitWfdSinkStMachineSignal(int pollTimeoutInMs)
{
    if(m_wfdSinkStMachineThreadPtr)
        return m_wfdSinkStMachineThreadPtr->waitWfdSinkStMachineThreadSignal(pollTimeoutInMs);
    else
        return 0;
}
//called from thread, should take care of read only.
void wfdSinkStMachineClass::processThisSockectEvent()
{
    if(pRTSPSinkClient)
    {
        int rd_size = pRTSPSinkClient->readSocket();
        if(rd_size < 0)
        {
            pRTSPSinkClient->resetSocket();
            pRTSPSinkClient->resetReceivedBuffer();

            if(m_wfdSinkStMachineThreadPtr)
                m_wfdSinkStMachineThreadPtr->removePollFds(m_myId,0,true);

            csioEventQueueStruct EvntQ;
            memset(&EvntQ,0,sizeof(csioEventQueueStruct));
            EvntQ.obj_id = m_myId;
            EvntQ.event_type = WFD_SINK_STM_INTERNAL_ERROR_EVENT;
            EvntQ.buf_size   = 0;
            EvntQ.buffPtr    = 0;

            stateFunction(&EvntQ);
        }
    }//else
}
//called from processThisSockectEvent --->   readSocket()  -->processPackets
void wfdSinkStMachineClass::processPackets(int size, char* buf)
{
    CSIO_LOG(eLogLevel_info, "wfdSinkStMachineClass[%d]: processPackets size[%d][%s]\n", m_myId,size,buf);

    //TODO: call other function to get type of the message.
    int type = m_curentState + 19;//WFD_SINK_STM_M1_RQST_RCVD_EVENT;

    //TODO: clean up, call state directly here
    {
        csioEventQueueStruct EvntQ;
        memset(&EvntQ,0,sizeof(csioEventQueueStruct));
        EvntQ.obj_id = m_myId;
        EvntQ.event_type = type;
        EvntQ.buf_size   = size;
        EvntQ.buffPtr    = buf;

        stateFunction(&EvntQ);
    }
}
//Note: currently this function is not used/called
//      because we only have event for state machine
void wfdSinkStMachineClass::processThisCommandEvent()
{
    //empty for now
}

//Note: currently this function is not used/called
void wfdSinkStMachineClass::processThisTickEvent()
{
    /*
    csioEventQueueStruct evntQ;
    memset(&evntQ,0,sizeof(csioEventQueueStruct));
    evntQ.event_type = WFD_SINK_STM_TIME_TICKS_EVENT;

    p->stateFunction(&evntQ);
    */
}

//note: this function can only be used by project.
void wfdSinkStMachineClass::removewfdSinkStMachineObj()
{
    if(m_wfdSinkStMachineThreadPtr)
    {
        csioEventQueueStruct EvntQ;
        memset(&EvntQ,0,sizeof(csioEventQueueStruct));
        EvntQ.obj_id = m_myId;
        EvntQ.event_type = WFD_SINK_REMOVE_STMACHINE_EVENT;
        EvntQ.buf_size   = 0;
        EvntQ.buffPtr    = NULL;

        m_wfdSinkStMachineThreadPtr->sendEvent(&EvntQ);
    }
    else
    {
        CSIO_LOG(eLogLevel_error,"ERROR: removewfdSinkStMachineObj m_wfdSinkStMachineThreadPtr is NULL.");
    }
}

/****************************** end of wfdSinkStMachineClass class *****************************/

/***************************** wfdSinkStMachineThread class **************************************/
//Note: we only expect one object of this class, so create m_sManagerTaskList here
wfdSinkStMachineThread::wfdSinkStMachineThread(int debugLevel):
m_syncPipe_readfd(-1),m_syncPipe_writefd(-1),
m_fdsMater(),m_maxFds(0),
wfdSinkStMachineThreadTimeArray(NULL)
{
    m_debugLevel = debugLevel;

    if(wfdSinkStMachineThread::m_wfdSinkStMachineTaskList == NULL)
    {
        wfdSinkStMachineThread::m_wfdSinkStMachineTaskList = new wfdSinkStMachineClass* [MAX_WFD_TCP_CONN];

        if(wfdSinkStMachineThread::m_wfdSinkStMachineTaskList)
        {
            for(int i = 0; i < MAX_WFD_TCP_CONN; i++)
                wfdSinkStMachineThread::m_wfdSinkStMachineTaskList[i] = NULL;
        }
        else
        {
            CSIO_LOG(eLogLevel_error, "ERROR: failed to create wfdSinkStMachineThread::m_wfdSinkStMachineTaskList");
        }
    }

    m_manThreadEventQ = new csioEventQueueListBase(CSIO_DEFAULT_QUEUE_SIZE);

    resetAllPollFds();

    if(m_manThreadEventQ)
    {
        insertPollFds(MANAGER_POLL_FDS_SIG_PIPE_INDEX,m_manThreadEventQ->m_msgQ_pipe_readfd);
    }

    if(pipe2(m_syncPipefd, O_NONBLOCK) < 0)
    {
        CSIO_LOG(eLogLevel_error, "ERROR: wfdSinkStMachineThread failed to create m_syncPipefd");
    }
    else
    {
        m_syncPipe_readfd  = m_syncPipefd[0];
        m_syncPipe_writefd = m_syncPipefd[1];
    }

    wfdSinkStMachineThreadTimeArray = new csioTimerClockBase(WFD_SINK_EVENTTIME_STATETHRD_MAX,WFD_SINK_STATETHRD_TIMER_MAX);

    if(wfdSinkStMachineThreadTimeArray)
        wfdSinkStMachineThreadTimeArray->recordEventTimeStamp(WFD_SINK_EVENTTIME_STATETHRD_CREATED);

    CSIO_LOG(m_debugLevel, "wfdSinkStMachineThread this[0x%x], m_wfdSinkStMachineTaskList[0x%x]\n",
             this,wfdSinkStMachineThread::m_wfdSinkStMachineTaskList);
}

//Note: we only expect one object of this class, so delete m_sManagerTaskList here
wfdSinkStMachineThread::~wfdSinkStMachineThread()
{
    CSIO_LOG(m_debugLevel, "wfdSinkStMachineThread: ~wfdSinkStMachineThread.\n");

    if(wfdSinkStMachineThread::m_wfdSinkStMachineTaskList)
    {
        for(int i = 0; i < MAX_WFD_TCP_CONN; i++)
        {
            if(wfdSinkStMachineThread::m_wfdSinkStMachineTaskList[i])
            {
                delete wfdSinkStMachineThread::m_wfdSinkStMachineTaskList[i];
                wfdSinkStMachineThread::m_wfdSinkStMachineTaskList[i] = NULL;
                CSIO_LOG(m_debugLevel, "~wfdSinkStMachineThread: delete m_wfdSinkStMachineTaskList[%d]\n",i);
            }
        }

        delete[] wfdSinkStMachineThread::m_wfdSinkStMachineTaskList;
        wfdSinkStMachineThread::m_wfdSinkStMachineTaskList = NULL;
    }

    if(m_manThreadEventQ)
    {
        //maybe redundant here
        removePollFds(MANAGER_POLL_FDS_SIG_PIPE_INDEX,0,true);

        //get list counts
        int cnt = m_manThreadEventQ->GetEvntQueueCount();

        //remove lists
        if(cnt > 0)
        {
            //loop through all contents of the list
            for(int i = 0; i < cnt; i++)
            {
                csioEventQueueStruct* evntQPtr = NULL;
                if(m_manThreadEventQ->GetFromQueueList(&evntQPtr) && evntQPtr)
                {
                    if( evntQPtr->buf_size && evntQPtr->buffPtr)
                    {
                        char* tmp = (char*)evntQPtr->buffPtr;
                        delete [] tmp;
                    }

                    //delete event queue
                    delete evntQPtr;
                }
            }
        }

        delete m_manThreadEventQ;
        m_manThreadEventQ = NULL;
    }

    if(m_syncPipe_readfd >= 0)
    {
        close(m_syncPipefd[0]);
        m_syncPipe_readfd = -1;
    }

    if(m_syncPipe_writefd >= 0)
    {
        close(m_syncPipefd[1]);
        m_syncPipe_writefd = -1;
    }

    if(wfdSinkStMachineThreadTimeArray)
    {
        delete wfdSinkStMachineThreadTimeArray;
        wfdSinkStMachineThreadTimeArray = NULL;
    }

    CSIO_LOG(m_debugLevel, "~wfdSinkStMachineThread exit[0x%x]\n",this);
}

void* wfdSinkStMachineThread::ThreadEntry()
{
    int rc;
    csioEventQueueStruct* evntQPtr = NULL;

    CSIO_LOG(m_debugLevel, "Enter wfdSinkStMachineThread[0x%x] ThreadEntry.\n",this);

    if(wfdSinkStMachineThreadTimeArray)
    {
        wfdSinkStMachineThreadTimeArray->setTimeout(WFD_SINK_STATETHRD_TICKS_TIMER,WFD_SINK_STATETHRD_TICK_TO);
    }

    for(;;)
    {
        CSIO_LOG(ABOVE_DEBUG_VERB(ABOVE_DEBUG_XTRVERB(m_debugLevel)), "wfdSinkStMachineThread[0x%x],m_threadObjLoopCnt[%d]\n",
                 this,m_threadObjLoopCnt);

        m_threadObjLoopCnt++;

        //Note: poll and timeout 1s
        rc = poll(m_fdsMater, m_maxFds, WFD_SINK_STATETHRD_SLEEP_MS);

        CSIO_LOG(ABOVE_DEBUG_VERB(ABOVE_DEBUG_XTRVERB(m_debugLevel)), "wfdSinkStMachineThread,GetEvntQueueCount[%d]\n",
                 m_manThreadEventQ->GetEvntQueueCount());

        //Note: m_fdsMater has (MAX_WFD_TCP_CONN + 1) items
        //      the first MAX_WFD_TCP_CONN items are the task list,
        //      the last index(MANAGER_POLL_FDS_SIG_PIPE_INDEX) is used for message queue

        //step 1. going through commands(@ MANAGER_POLL_FDS_SIG_PIPE_INDEX)
        if(m_manThreadEventQ->GetFromQueueList(&evntQPtr) && evntQPtr)
        {
            switch (evntQPtr->event_type)
            {
                case WFD_SINK_INSERT_STMACHINE_EVENT:
                {
                    int id = evntQPtr->obj_id;

                    if(wfdSinkStMachineThread::m_wfdSinkStMachineTaskList && IsValidStreamWindow(id))
                    {
                        if(wfdSinkStMachineThread::m_wfdSinkStMachineTaskList[id])
                        {
                            CSIO_LOG(m_debugLevel, "wfdSinkStMachineThread: m_wfdSinkStMachineTaskList[id] exist[0x%x]\n",
                                     wfdSinkStMachineThread::m_wfdSinkStMachineTaskList[id]);

                            //should never happen
                            signalWaitingThread(WFD_SINK_SINGAL_WAIT_ERROR);
                        }
                        else
                        {
                            wfdSinkStMachineClass* p = (wfdSinkStMachineClass*)evntQPtr->voidPtr;

                            if(p)
                            {
                                //step 1: insert first
                                wfdSinkStMachineThread::m_wfdSinkStMachineTaskList[id] = p;
                                wfdSinkStMachineThread::m_wfdSinkStMachineTaskListCnt++;

                                //step 2: signal project we are done inserting
                                signalWaitingThread(id);

                                //step 3: start state machine
                                if( evntQPtr->buf_size && evntQPtr->buffPtr)
                                {
                                    p->setCurentSourcePort(evntQPtr->ext_obj) ;

                                    std::string str = (char*)evntQPtr->buffPtr;
                                    p->setCurentSourceUrl(str);

                                    csioEventQueueStruct evntQ;
                                    memset(&evntQ,0,sizeof(csioEventQueueStruct));
                                    evntQ.event_type = WFD_SINK_STM_START_CONN_EVENT;
                                    p->stateFunction(&evntQ);
                                }
                                else
                                {
                                    CSIO_LOG(m_debugLevel, "wfdSinkStMachineThread:WFD_SINK_INSERT_STMACHINE_EVENT[id] no url\n",id);
                                }

                                CSIO_LOG(m_debugLevel, "wfdSinkStMachineThread:WFD_SINK_INSERT_STMACHINE_EVENT TaskList[%d] = 0x%x\n",
                                         id,wfdSinkStMachineThread::m_wfdSinkStMachineTaskList[id]);
                            }
                            else//should never happen
                            {
                                signalWaitingThread(WFD_SINK_SINGAL_WAIT_ERROR);
                            }
                        }
                    }
                    else//should never happen
                    {
                        signalWaitingThread(WFD_SINK_SINGAL_WAIT_ERROR);
                    }

                    if( evntQPtr->buf_size && evntQPtr->buffPtr)
                    {
                        deleteCharArray(evntQPtr->buffPtr);
                    }
                    break;
                }
                case WFD_SINK_START_STMACHINE_EVENT:
                {
                    int id = evntQPtr->obj_id;

                    if( evntQPtr->buf_size && evntQPtr->buffPtr)
                    {
                        if(wfdSinkStMachineThread::m_wfdSinkStMachineTaskList[id])
                        {
                            wfdSinkStMachineClass* p = wfdSinkStMachineThread::m_wfdSinkStMachineTaskList[id];
                            p->setCurentSourcePort(evntQPtr->ext_obj) ;

                            std::string str = (char*)evntQPtr->buffPtr;
                            p->setCurentSourceUrl(str);

                            csioEventQueueStruct evntQ;
                            memset(&evntQ,0,sizeof(csioEventQueueStruct));
                            evntQ.event_type = WFD_SINK_STM_START_CONN_EVENT;
                            p->stateFunction(&evntQ);
                        }

                        CSIO_LOG(m_debugLevel, "wfdSinkStMachineThread:WFD_SINK_START_STMACHINE_EVENT[id] processed\n",id);
                        deleteCharArray(evntQPtr->buffPtr);
                    }
                    else
                    {
                        CSIO_LOG(m_debugLevel, "wfdSinkStMachineThread:WFD_SINK_START_STMACHINE_EVENT[id] no url\n",id);
                    }
                    break;
                }
                case WFD_SINK_TEARDOWN_TCP_CONN_EVENT:
                {
                    int id = evntQPtr->obj_id;

                    if(wfdSinkStMachineThread::m_wfdSinkStMachineTaskList[id])
                    {
                        wfdSinkStMachineClass* p = wfdSinkStMachineThread::m_wfdSinkStMachineTaskList[id];

                        csioEventQueueStruct evntQ;
                        memset(&evntQ,0,sizeof(csioEventQueueStruct));
                        evntQ.event_type = WFD_SINK_STM_START_TEARDOWN_EVENT;
                        p->stateFunction(&evntQ);

                        CSIO_LOG(m_debugLevel, "wfdSinkStMachineThread:WFD_SINK_TEARDOWN_TCP_CONN_EVENT[id] processed\n",id);
                    }

                    break;
                }
                case WFD_SINK_SEND_IDR_REQ_EVENT:
                {
                    int id = evntQPtr->obj_id;

                    if(wfdSinkStMachineThread::m_wfdSinkStMachineTaskList[id])
                    {
                        wfdSinkStMachineClass* p = wfdSinkStMachineThread::m_wfdSinkStMachineTaskList[id];

                        csioEventQueueStruct evntQ;
                        memset(&evntQ,0,sizeof(csioEventQueueStruct));
                        evntQ.event_type = WFD_SINK_STM_IDR_REQ_EVENT;
                        p->stateFunction(&evntQ);

                        CSIO_LOG(m_debugLevel, "wfdSinkStMachineThread:WFD_SINK_STM_IDR_REQ_EVENT[id] processed\n",id);
                    }

                    break;
                }
#if 0
                case WFD_SINK_REMOVE_STMACHINE_EVENT:
                {
                    int id = evntQPtr->obj_id;

                    if(wfdSinkStMachineThread::m_wfdSinkStMachineTaskList && IsValidStreamWindow(id))
                    {
                        if(wfdSinkStMachineThread::m_wfdSinkStMachineTaskList[id])
                        {
                            wfdSinkStMachineClass* p = wfdSinkStMachineThread::m_wfdSinkStMachineTaskList[id];
                            if(p)
                            {
                                //step 1: remove from the list first
                                wfdSinkStMachineThread::m_wfdSinkStMachineTaskList[id] = NULL;
                                wfdSinkStMachineThread::m_wfdSinkStMachineTaskListCnt--;

                                //step 2: close manager
                                //p->closeManager();

                                //step 3: signal project we are done inserting
                                signalWaitingThread(id);
                            }
                            else//should never happen
                            {
                                signalWaitingThread(WFD_SINK_SINGAL_WAIT_ERROR);
                            }
                            CSIO_LOG(m_debugLevel, "wfdSinkStMachineThread:m_wfdSinkStMachineTaskList[%d] is 0x%x]\n",
                                     id,wfdSinkStMachineThread::m_wfdSinkStMachineTaskList[id]);
                        }
                        else//should never happen
                        {
                            signalWaitingThread(WFD_SINK_SINGAL_WAIT_ERROR);
                            CSIO_LOG(m_debugLevel, "wfdSinkStMachineThread:m_wfdSinkStMachineTaskList[id] is NULL\n",
                                     id);
                        }
                    }
                    else//should never happen
                    {
                        signalWaitingThread(WFD_SINK_SINGAL_WAIT_ERROR);
                    }
                    break;
                }
#endif
            }//end of switch

            delete evntQPtr;
        }//end of step 1, read from queue

        //step 2. make sure we tick all the state machine.
        if( wfdSinkStMachineThreadTimeArray && wfdSinkStMachineThreadTimeArray->isTimeout(WFD_SINK_STATETHRD_TICKS_TIMER) )
        {
            wfdSinkStMachineThreadTimeArray->setTimeout(WFD_SINK_STATETHRD_TICKS_TIMER,WFD_SINK_STATETHRD_TICK_TO);
            CSIO_LOG(ABOVE_DEBUG_XTRVERB(m_debugLevel), "wfdSinkStMachineThread: tick all state machine[%d]\n",
                    wfdSinkStMachineThreadTimeArray->pTimeoutWaitEvents[WFD_SINK_STATETHRD_TICK_TO].tv_sec);

            csioEventQueueStruct evntQ;
            memset(&evntQ,0,sizeof(csioEventQueueStruct));
            evntQ.event_type = WFD_SINK_STM_TIME_TICKS_EVENT;

            for(int i = 0; i < MAX_WFD_TCP_CONN; i++)
            {
                wfdSinkStMachineClass* p = getWfdSinkStMachineClassObj(i);
                if(p)
                {
                    p->stateFunction(&evntQ);
                }
            }

            //Note: no difference with p->processThisTickEvent(); except time out for the timer is WFD_SINK_STATETHRD_TICK_TO
        }

        //step 3. going through events
        if(rc < 0)// -1 means error
        {
            CSIO_LOG(m_debugLevel, "wfdSinkStMachineThread: poll returns[id].\n", rc);
            //TODO: don't know what to do here
        }
        else// = 0 time out, > 0 ok
        {
            //Note: go through task list,MANAGER_POLL_FDS_SIG_PIPE_INDEX was consumed in step 1
            for(int i = 0; i < MAX_WFD_TCP_CONN; i++)
            {
                CSIO_LOG(ABOVE_DEBUG_XTRVERB(m_debugLevel), "wfdSinkStMachineThread: poll array[%d],fd[%d],revents[0x%x].\n",
                         i, m_fdsMater[i].fd,m_fdsMater[i].revents);

                if((m_fdsMater[i].fd >= 0) && (m_fdsMater[i].revents & POLLIN))
                {
                    wfdSinkStMachineClass* p = getWfdSinkStMachineClassObj(i);
                    if(p)
                        p->processThisSockectEvent();
                }
                //else

                //Note: simply use this loop
                /*if(i < MAX_WFD_TCP_CONN)
                {
                    wfdSinkStMachineClass* p = getWfdSinkStMachineClassObj(i);
                    if(p)
                        p->processThisTickEvent();

                    //Note: no difference than p->stateFunction(&evntQ); except time out when poll is WFD_SINK_STATETHRD_SLEEP_MS
                }*/

                m_fdsMater[i].revents = 0;
            }
        }//end of step 3

        if(m_forceThreadExit)
            break;

    }//end of for(;;) loop

    CSIO_LOG(m_debugLevel, "wfdSinkStMachineThread[0x%x] exiting...\n",this);

    //maybe redundant here
    resetAllPollFds();

    //thread exit here
    m_ThreadIsRunning = 0;

    return NULL;
}
void wfdSinkStMachineThread::exitThread()
{
    CSIO_LOG(m_debugLevel, "wfdSinkStMachineThread: exitThread[0x%x]\n",this);

    m_forceThreadExit = 1;

    //send event to the thread with id = -1
    csioEventQueueStruct EvntQ;
    memset(&EvntQ,0,sizeof(csioEventQueueStruct));
    EvntQ.obj_id = -1;
    sendEvent(&EvntQ);
}

//Note: called once in constructor to initialize master fds
void wfdSinkStMachineThread::resetAllPollFds()
{
    for(int i = 0; i < TOTAL_MANAGER_MASTER_FDS; i++)
    {
        m_fdsMater[i].fd      = -1;
        m_fdsMater[i].events  = 0;
        m_fdsMater[i].revents = 0;
        m_maxFds++;
    }

    CSIO_LOG(m_debugLevel, "wfdSinkStMachineThread: setupPollFds: m_maxFds[%d].\n",m_maxFds);
    return ;
}
void wfdSinkStMachineThread::insertPollFds(int id, int sock)
{
    m_fdsMater[id].events = 0;
    m_fdsMater[id].revents = 0;

    if(sock >= 0)
    {
        m_fdsMater[id].fd     = sock;
        m_fdsMater[id].events = POLLIN;
    }

    CSIO_LOG(ABOVE_DEBUG_XTRVERB(m_debugLevel), "wfdSinkStMachineThread: insertPollFds: id[%d] sock[%d],\n",id,sock);
    return ;
}
void wfdSinkStMachineThread::removePollFds(int id, int sock,bool force)
{
    //Note: by default, force is false, so we need to check matching sock.
    if(sock == m_fdsMater[id].fd || force)
    {
        if(force && (m_fdsMater[id].fd) >= 0)
            CSIO_LOG(m_debugLevel, "ERROR: wfdSinkStMachineThread removePollFds: force is true but sock[%d]",m_fdsMater[id].fd);

        m_fdsMater[id].fd = -1;
        m_fdsMater[id].events  = 0;
        m_fdsMater[id].revents = 0;
    }
    else
    {
        CSIO_LOG(m_debugLevel, "ERROR: removePollFds: sock[%d] is not the same as m_fdsMater[%d].fd: %d.\n",sock,id,m_fdsMater[id].fd);
    }

    CSIO_LOG(ABOVE_DEBUG_XTRVERB(m_debugLevel), "wfdSinkStMachineThread:removePollFds: id[%d] sock[%d],\n",id,sock);
    return ;
}

void wfdSinkStMachineThread::sendEvent(csioEventQueueStruct* pEvntQ)
{
    if(m_manThreadEventQ && pEvntQ)
    {
        int evnt  = pEvntQ->event_type;
        int iId   = pEvntQ->obj_id;
        void* bufP = pEvntQ->buffPtr;
        int dataSize = pEvntQ->buf_size;

        csioEventQueueStruct evntQ;
        memset(&evntQ,0,sizeof(csioEventQueueStruct));
        evntQ.obj_id  = iId;
        evntQ.event_type    = evnt;
        evntQ.buf_size      = 0;
        evntQ.buffPtr       = NULL;
        evntQ.ext_obj       = pEvntQ->ext_obj;
        evntQ.voidPtr       = pEvntQ->voidPtr;

        CSIO_LOG(m_debugLevel, "wfdSinkStMachineThread::sendEvent: iId[%d],evnt[%d],dataSize[%d],bufP[0x%x]\n",\
                 iId,evnt,pEvntQ->buf_size,bufP);

        //passing to parent queue
        if(bufP && dataSize)
        {
            switch (evntQ.event_type)
            {
                default:
                {
                    char* tmp = (char*)createCharArray(dataSize + 1);
                    if(tmp)
                    {
                        //first copy configure structure
                        memcpy(tmp,(char*)bufP,dataSize);
                        tmp[dataSize] = 0;
                        evntQ.buffPtr = tmp;
                        evntQ.buf_size = dataSize;
                    }
                    else
                    {
                        CSIO_LOG(eLogLevel_error, "wfdSinkStMachineThread::sendEvent: create buffer failed\n");
                    }

                    break;
                }
            }
        }

        if(m_manThreadEventQ->EnqueueAndSignal(evntQ))
        {
            CSIO_LOG(m_debugLevel, "wfdSinkStMachineThread::sendEvent: obj_id[%d]: event queue overflow[%d], make a delay now.\n",
                     evntQ.obj_id,m_manThreadEventQ->GetEvntQueueCount());

            usleep( 20000L); //20 ms
            CSIO_LOG(m_debugLevel, "wfdSinkStMachineThread::sendEvent: obj_id[%d]: delay due to overflow is done[%d].\n",
                     evntQ.obj_id,m_manThreadEventQ->GetEvntQueueCount());
        }
        else
        {
            CSIO_LOG(m_debugLevel, "wfdSinkStMachineThread::sendEvent: event added to the queue[0x%x].",evntQ.buffPtr);
        }
    }
    else
    {
        CSIO_LOG(eLogLevel_warning, "wfdSinkStMachineThread::sendEvent: pEvntQ or m_manThreadEventQ is NULL\n");
    }
}

void wfdSinkStMachineThread::signalWaitingThread(int v)
{
    int buf = v;
    if(m_syncPipe_writefd >= 0)
        write(m_syncPipe_writefd, &buf, sizeof(buf));

    CSIO_LOG(ABOVE_DEBUG_XTRVERB(m_debugLevel), "wfdSinkStMachineThread: signalWaitingThread: v[%d]\n",v);
}
wfdSinkStMachineClass* wfdSinkStMachineThread::getWfdSinkStMachineClassObj(int id)
{
    if( (wfdSinkStMachineThread::m_wfdSinkStMachineTaskList)  && IsValidStreamWindow(id))
        return wfdSinkStMachineThread::m_wfdSinkStMachineTaskList[id];
    else
        return NULL;
}

//called from parent project
int wfdSinkStMachineThread::waitWfdSinkStMachineThreadSignal(int pollTimeoutInMs)
{
    int ret = WFD_SINK_SINGAL_WAIT_TIMEOUT;

    if(m_syncPipe_readfd >= 0)
    {
        struct pollfd pfds[1];
        memset((char*)pfds, 0, sizeof(struct pollfd));

        pfds[0].fd     = m_syncPipe_readfd;
        pfds[0].events = POLLIN;

        int result = poll(pfds, 1, pollTimeoutInMs);

        if( result < 0 )// -1 means error
        {
            CSIO_LOG(eLogLevel_error,"ERROR: waitWfdSinkStMachineThreadSignal result is %d.",result);
        }
        else// = 0 time out, > 0 ok
        {
            CSIO_LOG(ABOVE_DEBUG_XTRVERB(m_debugLevel),"waitWfdSinkStMachineThreadSignal result is %d.",result);

            //flush out pipe
            if ( pfds[0].revents & POLLIN )  //POLLIN == 1
            {
                //read out pipe
                int rc = read(m_syncPipe_readfd, &ret, sizeof(ret));
                CSIO_LOG(ABOVE_DEBUG_XTRVERB(m_debugLevel), "waitWfdSinkStMachineThreadSignal: read returns rc[%d],ret[%d]\n",rc,ret);
            }
        }
    }
    else
    {
        CSIO_LOG(eLogLevel_error,"ERROR: waitWfdSinkStMachineThreadSignal m_syncPipe_readfd is NULL.");
    }

    return ret;
}
void wfdSinkStMachineThread::DumpClassPara(int l)
{
    CSIO_LOG(eLogLevel_info, "wfdSinkStMachineThread: threadObjLoopCnt:   %d.\n",m_threadObjLoopCnt);

    CSIO_LOG(eLogLevel_info, "wfdSinkStMachineThread: m_debugLevel        %d\n", m_debugLevel);
    CSIO_LOG(eLogLevel_info, "wfdSinkStMachineThread: m_ThreadIsRunning   %d\n", m_ThreadIsRunning);

    if(wfdSinkStMachineThread::m_wfdSinkStMachineTaskList)
    {
        for(int i = 0; i < MAX_WFD_TCP_CONN; i++)
        {
            if(wfdSinkStMachineThread::m_wfdSinkStMachineTaskList[i])
            {
                wfdSinkStMachineThread::m_wfdSinkStMachineTaskList[i]->DumpClassPara(0);
            }
        }
    }
}
/***************************** end of wfdSinkStMachineThread class **************************************/
