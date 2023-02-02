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

    {"WFD_SINK_STM_GST_READY_RCVD_EVENT",   WFD_SINK_STM_GST_READY_RCVD_EVENT},

    {"WFD_SINK_STM_M6_RESP_RCVD_EVENT",     WFD_SINK_STM_M6_RESP_RCVD_EVENT},
    {"WFD_SINK_STM_M7_RESP_RCVD_EVENT",     WFD_SINK_STM_M7_RESP_RCVD_EVENT},
    {"WFD_SINK_STM_KEEP_ALIVE_RCVD_EVENT" , WFD_SINK_STM_KEEP_ALIVE_RCVD_EVENT},
    {"WFD_SINK_STM_INTERNAL_ERROR_EVENT",   WFD_SINK_STM_INTERNAL_ERROR_EVENT},
    {"WFD_SINK_STM_TD_RESP_RCVD_EVENT",     WFD_SINK_STM_TD_RESP_RCVD_EVENT},
    {"WFD_SINK_STM_RCVD_TEARDOWN_EVENT",    WFD_SINK_STM_RCVD_TEARDOWN_EVENT},

    {"WFD_SINK_STM_M4_TRANSPORT_SWITCH_RQST_RCVD_EVENT", WFD_SINK_STM_M4_TRANSPORT_SWITCH_RQST_RCVD_EVENT},
    {"WFD_SINK_STM_M4_TRANSPORT_SWITCH_RESP_RCVD_EVENT", WFD_SINK_STM_M4_TRANSPORT_SWITCH_RESP_RCVD_EVENT},

    {"WFD_SINK_INSERT_STMACHINE_EVENT",     WFD_SINK_INSERT_STMACHINE_EVENT},
    {"WFD_SINK_REMOVE_STMACHINE_EVENT",     WFD_SINK_REMOVE_STMACHINE_EVENT},
    {"WFD_SINK_START_STMACHINE_EVENT",      WFD_SINK_START_STMACHINE_EVENT},
    {"WFD_SINK_TEARDOWN_TCP_CONN_EVENT",    WFD_SINK_TEARDOWN_TCP_CONN_EVENT},
    {"WFD_SINK_SEND_IDR_REQ_EVENT",         WFD_SINK_SEND_IDR_REQ_EVENT},
    {"WFD_SINK_GST_READY_EVENT",            WFD_SINK_GST_READY_EVENT},
    {"WFD_SINK_GST_1ST_FRAME_EVENT",        WFD_SINK_GST_1ST_FRAME_EVENT},
    {"WFD_SINK_GST_LOST_VIDEO_EVENT",       WFD_SINK_GST_LOST_VIDEO_EVENT},

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

    {"WFD_SINK_STATES_WAIT_GSTREAMER_PIPELINE_READY" ,WFD_SINK_STATES_WAIT_GSTREAMER_PIPELINE_READY},

    {"WFD_SINK_STATES_WAIT_M6_RESP" ,     WFD_SINK_STATES_WAIT_M6_RESP},
    {"WFD_SINK_STATES_WAIT_M7_RESP" ,     WFD_SINK_STATES_WAIT_M7_RESP},

    {"WFD_SINK_STATES_KEEP_ALIVE_LOOP",   WFD_SINK_STATES_KEEP_ALIVE_LOOP},
    {"WFD_SINK_STATES_WAIT_TD_RESP",      WFD_SINK_STATES_WAIT_TD_RESP},

    {"WFD_SINK_STATES_WAIT_M4_TRANSPORT_SWITCH_REQUEST", WFD_SINK_STATES_WAIT_M4_TRANSPORT_SWITCH_REQUEST},
    {"WFD_SINK_STATES_WAIT_M6_TRANSPORT_SWITCH_RESP", WFD_SINK_STATES_WAIT_M6_TRANSPORT_SWITCH_RESP},

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

//Note: the list must be the same index as eWfd_States (matched with m_curentState)
const WFD_STRNUMPAIR Wfd_rtsp_msg_string_vs_event_names[] = {
    {"NONE" ,           WFD_SINK_STM_TIME_TICKS_EVENT},  //Note: not used
    {"OPTIONS",         WFD_SINK_STM_M1_RQST_RCVD_EVENT},
    {"200 OK" ,         WFD_SINK_STM_M2_RESP_RCVD_EVENT},
    {"GET_PARAMETER" ,  WFD_SINK_STM_M3_RQST_RCVD_EVENT},
    {"SET_PARAMETER" ,  WFD_SINK_STM_M4_RQST_RCVD_EVENT},
    {"SET_PARAMETER" ,  WFD_SINK_STM_M5_RQST_RCVD_EVENT},
    {"NONE",            WFD_SINK_STM_GST_READY_RCVD_EVENT},
    {"200 OK" ,         WFD_SINK_STM_M6_RESP_RCVD_EVENT},
    {"200 OK" ,         WFD_SINK_STM_M7_RESP_RCVD_EVENT},

    {"GET_PARAMETER" ,  WFD_SINK_STM_KEEP_ALIVE_RCVD_EVENT},

    {"200 OK" ,         WFD_SINK_STM_TD_RESP_RCVD_EVENT},
    {"SET_PARAMETER" ,  WFD_SINK_STM_M4_TRANSPORT_SWITCH_RQST_RCVD_EVENT},
    {"200 OK" ,         WFD_SINK_STM_M4_TRANSPORT_SWITCH_RESP_RCVD_EVENT},
};

int Wfd_rtsp_msg_type_vs_event_names[][2] = {
    {-1 ,                    WFD_SINK_STM_TIME_TICKS_EVENT},  //Note: not used
    {RTSP_MESSAGE_REQUEST,   WFD_SINK_STM_M1_RQST_RCVD_EVENT},
    {RTSP_MESSAGE_REPLY ,    WFD_SINK_STM_M2_RESP_RCVD_EVENT},
    {RTSP_MESSAGE_REQUEST ,  WFD_SINK_STM_M3_RQST_RCVD_EVENT},
    {RTSP_MESSAGE_REQUEST ,  WFD_SINK_STM_M4_RQST_RCVD_EVENT},
    {RTSP_MESSAGE_REQUEST ,  WFD_SINK_STM_M5_RQST_RCVD_EVENT},

    {-1,   WFD_SINK_STM_GST_READY_RCVD_EVENT},

    {RTSP_MESSAGE_REPLY ,    WFD_SINK_STM_M6_RESP_RCVD_EVENT},
    {RTSP_MESSAGE_REPLY ,    WFD_SINK_STM_M7_RESP_RCVD_EVENT},

    {RTSP_MESSAGE_REQUEST ,  WFD_SINK_STM_KEEP_ALIVE_RCVD_EVENT},

    {RTSP_MESSAGE_REPLY ,    WFD_SINK_STM_TD_RESP_RCVD_EVENT},
    {RTSP_MESSAGE_REQUEST,   WFD_SINK_STM_M4_TRANSPORT_SWITCH_RQST_RCVD_EVENT},
    {RTSP_MESSAGE_REPLY,     WFD_SINK_STM_M4_TRANSPORT_SWITCH_RESP_RCVD_EVENT},
};

wfdSinkStMachineThread*  wfdSinkStMachineClass::m_wfdSinkStMachineThreadPtr = NULL;

wfdSinkStMachineClass**  wfdSinkStMachineThread::m_wfdSinkStMachineTaskList = NULL;
int  wfdSinkStMachineThread::m_wfdSinkStMachineTaskListCnt = 0;

#define DEFAULT_VIDEO_RES            "cea_19ceb"
#define DEFAULT_VIDEO_RES_30Hz_ONLY  "cea_194a0"
#define DEFAULT_VIDEO_RES_25Hz_ONLY  "cea_19420"
//(native res. indexes {h.265, MP, Level 5.1, upto 3840x2160p60}, {h.264, HP, Level 4, upto 1080p60}, {h.264, MP, Level 4, upto 1080p60})
#define DEFAULT_VIDEO2_4KRES "00 02 01 0010 0000000f94a0 000000000000 000000000000 00 0000 0000 00, 01 20 0040 0000000f94a0 000000000000 000000000000 00 0000 0000 00, 01 10 0040 0000000f94a0 000000000000 000000000000 00 0000 0000 00 00"
//(native res. indexes {h.265, MP, Level 5.1, upto 1920x1080p30}, {h.264, HP, Level 4, upto 1080p30}, {h.264, MP, Level 4, upto 1080p30})
#define DEFAULT_VIDEO2_RES_30Hz "00 02 01 0010 0000000194a0 000000000000 000000000000 00 0000 0000 00, 01 10 0040 0000000194a0 000000000000 000000000000 00 0000 0000 00, 01 20 0040 0000000194a0 000000000000 000000000000 00 0000 0000 00 00"
//(native res. indexes {h.265, MP, Level 5.1, upto 1920x1080p25}, {h.264, HP, Level 4, upto 1080p25}, {h.264, MP, Level 4, upto 1080p25})
#define DEFAULT_VIDEO2_RES_25Hz "00 02 01 0010 000000019420 000000000000 000000000000 00 0000 0000 00, 01 10 0040 000000019420 000000000000 000000000000 00 0000 0000 00, 01 20 0040 000000019420 000000000000 000000000000 00 0000 0000 00 00"
#define DEFAULT_VIDEO2_RES_NONE ""

#define MIRACAST_ON_TCP_FILEPATH "/data/CresStreamSvc/useMiracastOnTcpForTx3"

extern int csio_jni_getMaxMiracastFps();

static int read_int_from_file(const char *filePath, int defaultValue)
{
    FILE * file;
    int rv = defaultValue;

    file = fopen(filePath, "r");
    if (file != NULL)
    {
        int errorCode = 0;
        if (fscanf(file, "%d", &(errorCode)) >= 0)
        {
            rv = errorCode;
        }

        fclose(file);
    }
    return rv;
}

static char *getPreferredVideoResolutionDefaultString()
{
    int maxFps = csio_jni_getMaxMiracastFps();
    if (maxFps <= 25)
        return DEFAULT_VIDEO_RES_25Hz_ONLY;
    else if (maxFps <= 30)
        return DEFAULT_VIDEO_RES_30Hz_ONLY;
    else
        return DEFAULT_VIDEO_RES;
}

static char *getPreferredVideo2ResolutionDefaultString(bool isTx3, int systemMode)
{
    char *wfd2_videoResStr = DEFAULT_VIDEO2_RES_NONE;

    if (product_info()->does4kAirMediaDecode)
    {
        if(systemMode == WFD_SINK_SYSTEMMODE_OPTIMIZED_FOR_VIDEO_QUALITY)
        {
            wfd2_videoResStr = DEFAULT_VIDEO2_4KRES;
        }
        else
        {
            //WFD_SINK_SYSTEMMODE_OPTIMIZED_FOR_MULTIPLE_PRESENTATIONS
            //WFD_SINK_SYSTEMMODE_CUSTOM
            //WFD_SINK_SYSTEMMODE_UNDEFINE
            if (csio_jni_getMaxMiracastFps() <= 25)
            {
                wfd2_videoResStr = DEFAULT_VIDEO2_RES_25Hz;
            } else {
                wfd2_videoResStr = DEFAULT_VIDEO2_RES_30Hz;
            }
        }

        if(g_rtspVid2ResRefStr.size())
        {
            wfd2_videoResStr = (char*)g_rtspVid2ResRefStr.c_str();
        }

        CSIO_LOG(eLogLevel_verbose, "%s: isTx3[%d], systemMode Id[%d]", __FUNCTION__, isTx3, systemMode);
    }
    else
    {
        if(g_rtspVid2ResRefStr.size())
        {
            wfd2_videoResStr = (char*)g_rtspVid2ResRefStr.c_str();
        }
        else
            wfd2_videoResStr = DEFAULT_VIDEO2_RES_NONE;
    }

    return(wfd2_videoResStr);
}

/********** wfdSinkStMachineClass class, used by wfdSinkProjClass *******************/
wfdSinkStMachineClass::wfdSinkStMachineClass(int iId,wfdSinkProjClass* parent):
m_myId(iId),
wfdSinkStMachineTimeArray(NULL),
m_parent(parent),
m_SourceUrl(),m_connTime(),m_srcVersionStr(),
m_requestString(),
m_debugLevel(parent->getDebugLevel()),
m_curentState(WFD_SINK_STATES_IDLE),
m_msLatency(-1),
restartFromIdleCnt(0),m_max_restartCnt(MAX_WFD_TCP_RETRY),
m_onTcpConnFlag(0),
m_src_rtsp_port(DEFAULT_SOURCE_RTSP_PORT),
m_ts_Port(DEFAULT_CLIENT_TS_PORT),
pRTSPSinkClient(NULL),
m_seq_i(0),m_seq_j(0),
m_keepAliveTimeout(WFD_SINK_STATETIMEOUT_DEFAULT_KEEP_ALIVE),//default 60s
m_EvntQ(),
m_rtspParserIntfInfo(),
m_rtspParserIntfSession(),
m_ssrc(),
m_rtcpDestPort(-1),
m_is_mice_session(0),
m_IsTx3session(false),
m_useTcpTransport(false),
m_systemMode(WFD_SINK_SYSTEMMODE_UNDEFINED)
{
    wfdSinkStMachineTimeArray = new csioTimerClockBase(WFD_SINK_EVENTTIME_MAX,WFD_SINK_STATE_TIMER_MAX);

    m_SourceUrl.clear();
    m_connTime.clear();
    m_requestString.clear();
    m_srcVersionStr.clear();

    m_rtspParserIntfInfo.rtpPort      = m_ts_Port;
    m_rtspParserIntfInfo.rtspLogLevel = m_debugLevel;
    setMaxMiracastRate(m_IsTx3session);

    if(g_rtspAudioCodecStr.size())
    {
    	m_rtspParserIntfInfo.preferredAudioCodecStr = (char*)g_rtspAudioCodecStr.c_str();
    }
    else
    {
    	m_rtspParserIntfInfo.preferredAudioCodecStr = "AACx48x2";
    }

    if(g_rtspVidResRefStr.size())
    {
    	m_rtspParserIntfInfo.preferredVidResRefStr  = (char*)g_rtspVidResRefStr.c_str() ;
    }
    else
    {
        m_rtspParserIntfInfo.preferredVidResRefStr = getPreferredVideoResolutionDefaultString();
    }

    m_rtspParserIntfInfo.preferredVid2ResRefStr = getPreferredVideo2ResolutionDefaultString(m_IsTx3session, m_systemMode);

    if(m_rtspParserIntfInfo.preferredAudioCodecStr)
    	CSIO_LOG(m_debugLevel, "wfdSinkStMachineClass: preferredAudioCodecStr[%s].\n",m_rtspParserIntfInfo.preferredAudioCodecStr);
    else
    	CSIO_LOG(m_debugLevel, "wfdSinkStMachineClass: ERROR: preferredAudioCodecStr not set.\n");

    if(m_rtspParserIntfInfo.preferredVidResRefStr)
    	CSIO_LOG(m_debugLevel, "wfdSinkStMachineClass: preferredVidResRefStr[%s].\n",m_rtspParserIntfInfo.preferredVidResRefStr);
    else
        CSIO_LOG(m_debugLevel, "wfdSinkStMachineClass: ERROR: preferredVidResRefStr not set.\n");

    if(m_rtspParserIntfInfo.preferredVid2ResRefStr)
         CSIO_LOG(m_debugLevel, "wfdSinkStMachineClass: preferredVid2ResRefStr[%s].\n",m_rtspParserIntfInfo.preferredVid2ResRefStr);
    else
        CSIO_LOG(m_debugLevel, "wfdSinkStMachineClass: preferredVid2ResRefStr not set.\n");

    getTimeStamp(WFD_SINK_EVENTTIME_STATEMACHINE_CREATED);

    prepareBeforeIdle(false);

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
    CSIO_LOG(eLogLevel_info, "wfdSinkStMachineClass: source rtsp port   %d\n", getCurentSourcePort());
    CSIO_LOG(eLogLevel_info, "wfdSinkStMachineClass: client ts port     %d\n", getCurentTsPort());
    CSIO_LOG(eLogLevel_info, "wfdSinkStMachineClass: Restart count      %d\n", getRestartCnt());
    CSIO_LOG(eLogLevel_info, "wfdSinkStMachineClass: Max Restart count  %d\n", m_max_restartCnt);
    CSIO_LOG(eLogLevel_info, "wfdSinkStMachineClass: keep alive timeout %d\n", m_keepAliveTimeout/1000);
    CSIO_LOG(eLogLevel_info, "wfdSinkStMachineClass: m_is_mice_session  %d\n", m_is_mice_session);

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

        CSIO_LOG(eLogLevel_info, "wfdSinkStMachineClass: isOnRTSPTcpConnSet     %d(%d)\n", isOnRTSPTcpConnSet(),delta);
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

    if(pRTSPSinkClient)
    {
        CSIO_LOG(eLogLevel_info, "wfdSinkStMachineClass: pRTSPSinkClient    getSocket(%d)\n", pRTSPSinkClient->getSocket());
    }

    if(m_rtspParserIntfInfo.preferredAudioCodecStr)
		CSIO_LOG(m_debugLevel, "wfdSinkStMachineClass: preferredAudioCodecStr[%s].\n",m_rtspParserIntfInfo.preferredAudioCodecStr);
	else
		CSIO_LOG(m_debugLevel, "wfdSinkStMachineClass: ERROR: preferredAudioCodecStr not set.\n");

	if(m_rtspParserIntfInfo.preferredVidResRefStr)
		CSIO_LOG(m_debugLevel, "wfdSinkStMachineClass: preferredVidResRefStr[%s].\n",m_rtspParserIntfInfo.preferredVidResRefStr);
	else
		CSIO_LOG(m_debugLevel, "wfdSinkStMachineClass: ERROR: preferredVidResRefStr not set.\n");

    if(m_rtspParserIntfInfo.preferredVid2ResRefStr)
        CSIO_LOG(m_debugLevel, "wfdSinkStMachineClass: preferredVid2ResRefStr[%s].\n",m_rtspParserIntfInfo.preferredVid2ResRefStr);
    else
        CSIO_LOG(m_debugLevel, "wfdSinkStMachineClass: ERROR: preferredVid2ResRefStr not set.\n");
}

void wfdSinkStMachineClass::setCurentTsPort(int port)
{
    m_ts_Port = port;
    m_rtspParserIntfInfo.rtpPort = m_ts_Port;
}

void wfdSinkStMachineClass::setIsTx3Session(bool isTx3)
{
    m_IsTx3session = isTx3;
    m_rtspParserIntfInfo.isTx3 = isTx3;
    if (isTx3)
    {
        m_rtspParserIntfInfo.preferTcpTransport = read_int_from_file(MIRACAST_ON_TCP_FILEPATH, 1);
        CSIO_LOG(eLogLevel_info, "wfdSinkStMachineClass: m_rtspParserIntfInfo.preferTcpTransport = %d.\n",m_rtspParserIntfInfo.preferTcpTransport);
    }
}

void wfdSinkStMachineClass::setUseTcpTransport(bool useTcp)
{
    m_useTcpTransport = useTcp;
}

void wfdSinkStMachineClass::setMaxMiracastRate(bool isTx3)
{
    if(isTx3)
    {
        m_parent->setMaxMiracastBitrate(DEFAULT_MAX_TX3_MIRACAST_BITRATE);
        CSIO_LOG(m_debugLevel, "wfdSinkStMachineClass::%s(): Tx3 maxMiracastRate[%d]\n", __FUNCTION__, m_parent->getMaxMiracastBitrate());
    }

    m_parent->updateIfMaxBitrateOverride(DEFAULT_MIN_OVERRIDE_MIRACAST_BITRATE, DEFAULT_MAX_OVERRIDE_MIRACAST_BITRATE);
	m_rtspParserIntfInfo.maxMiracastRate = m_parent->getMaxMiracastBitrate();
	CSIO_LOG(m_debugLevel, "wfdSinkStMachineClass::%s(): maxMiracastRate[%d], isTx3[%d]\n", __FUNCTION__, m_rtspParserIntfInfo.maxMiracastRate, isTx3);
}

void wfdSinkStMachineClass::setVideoResolutionDefaults()
{
    m_rtspParserIntfInfo.preferredVidResRefStr = getPreferredVideoResolutionDefaultString();
    CSIO_LOG(m_debugLevel, "wfdSinkStMachineClass::%s(): preferredVidResRefStr[%s]\n", __FUNCTION__, m_rtspParserIntfInfo.preferredVidResRefStr);
}

void wfdSinkStMachineClass::setVideo2ResolutionDefaults(bool isTx3, int systemMode)
{
    m_rtspParserIntfInfo.preferredVid2ResRefStr = getPreferredVideo2ResolutionDefaultString(isTx3, systemMode);
    CSIO_LOG(m_debugLevel, "wfdSinkStMachineClass::%s(): preferredVid2ResRefStr[%s], systemMode Id[%d]\n", __FUNCTION__, m_rtspParserIntfInfo.preferredVid2ResRefStr, systemMode);
}

void wfdSinkStMachineClass::resetAllFlags()
{
    m_connTime.clear();
    m_srcVersionStr.clear();;
    m_ssrc = 0;
    m_rtcpDestPort = -1;
    m_msLatency = -1;
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

    deInitRTSPParser(m_rtspParserIntfSession);
}
void wfdSinkStMachineClass::prepareBeforeIdle(bool signalParent = true)
{
    resetAllFlags();
    resetSystemStatus();

    resetOnRTSPTcpConnFlg();
    restartFromIdleCnt = 0;

    resetTimeout();

    if(signalParent)
        sendEventToParentProj(WFD_SINK_EVENTS_RTSP_LEAVE_SESSION_EVENT);

    CSIO_LOG(m_debugLevel, "wfdSinkStMachineClass[%d]: prepareBeforeIdle signalParent[%d]\n", m_myId,signalParent);
}
//Note: should be called only inside states function.
//      Making a one second delay (timeout)
//......default timeout parameter takes on value WFD_SINK_STATETIMEOUT_IDLE_RESTART
void wfdSinkStMachineClass::prepareForRestart(int timeout)
{
    resetAllFlags();
    resetSystemStatus();

    setTimeout(timeout);
    m_max_restartCnt = MAX_WFD_TCP_RETRY;

    sendEventToParentProj(WFD_SINK_EVENTS_RTSP_LEAVE_SESSION_EVENT);

    CSIO_LOG(m_debugLevel, "wfdSinkStMachineClass[%d]: prepareForRestart (timeout=%d)\n", m_myId, timeout);
}

void wfdSinkStMachineClass::sendEventToParentProj(int event)
{
    csioEventQueueStruct EvntQ;
    memset(&EvntQ,0,sizeof(csioEventQueueStruct));
    EvntQ.obj_id = m_myId;
    EvntQ.event_type = event;
    struct GST_PIPELINE_CONFIG gst_config;

    if(event == WFD_SINK_EVENTS_RTSP_IN_SESSION_EVENT)
    {
        gst_config.isTx3 = m_IsTx3session;
        gst_config.useTcp = m_useTcpTransport;
        gst_config.ts_port = getCurentTsPort();
        gst_config.ssrc    = m_ssrc;
        gst_config.rtcp_dest_port = m_rtcpDestPort;

        if(m_srcVersionStr.size())
            gst_config.pSrcVersionStr = (char*)m_srcVersionStr.c_str();
        else
            gst_config.pSrcVersionStr = NULL;

        EvntQ.buf_size = sizeof(GST_PIPELINE_CONFIG);
        EvntQ.buffPtr = (void*)&gst_config;
    }

    m_parent->sendEvent(&EvntQ);
}

//Note: this is the only place that m_curentState can be changed
int wfdSinkStMachineClass::stateFunction(csioEventQueueStruct* pEventQ)
{
    if(!pEventQ)
        return 0;

    int currenState = m_curentState;

    if(pEventQ->event_type != WFD_SINK_STM_TIME_TICKS_EVENT)
        CSIO_LOG(ABOVE_DEBUG_VERB(m_debugLevel),   "wfdSinkStMachineClass[%d]: stateFunction: m_curentState[%s], event_type[%s][%d]\n",
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
        case WFD_SINK_STATES_WAIT_GSTREAMER_PIPELINE_READY:
        {
            m_curentState = waitGstPipelineReadyState(pEventQ);
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
        case WFD_SINK_STATES_WAIT_M4_TRANSPORT_SWITCH_REQUEST:
        {
            m_curentState = waitM4TransportSwitchRequestState(pEventQ);
            break;
        }
        case WFD_SINK_STATES_WAIT_M6_TRANSPORT_SWITCH_RESP:
        {
            m_curentState = waitM6TransportSwitchResponseState(pEventQ);
            break;
        }
    }

    if(pEventQ->event_type != WFD_SINK_STM_TIME_TICKS_EVENT || currenState != m_curentState)
    {
        CSIO_LOG(ABOVE_DEBUG_VERB(m_debugLevel), "wfdSinkStMachineClass[%d]: stateFunction: new state[%s]\n",
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

    switch(events)
    {
        case WFD_SINK_STM_TIME_TICKS_EVENT:
        {
            //Note: if we don't have restart wait state, this will be used as a delay
            if(isTimeout())
            {
                resetTimeout();

                if(isOnRTSPTcpConnSet())
                {
                    CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: idleState: restart now[%d].\n",m_myId,restartFromIdleCnt);

                    if(restartFromIdleCnt++ > m_max_restartCnt)
                    {
                        prepareBeforeIdle();
                        break;
                    }

                    getTimeStamp(WFD_SINK_EVENTTIME_RESTART_FROM_IDLESTATE);
                    if(pRTSPSinkClient)
                    {
                        m_rtspParserIntfSession = initRTSPParser(&m_rtspParserIntfInfo);

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
                            prepareForRestart(500); // set restart timeout to 500 msec rather than 5000 second
                            CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]:RTSP connection failed\n",m_myId);
                            nextState = WFD_SINK_STATES_IDLE;

                            //Note: failed to connect to the port 7250.sometimes port 7250 is not ready soon enough.
                            //      if we wait 5 seconds to reconnect, seems to be very long to the user.
                            //      so adjust restart time(500ms) and max restart count(30) here.
                            m_max_restartCnt = 30;
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

            setOnRTSPTcpConnFlg();;

            if(pRTSPSinkClient)
            {
                m_rtspParserIntfSession = initRTSPParser(&m_rtspParserIntfInfo);

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
                    prepareForRestart(500); // override default timeout of 5 seconds
                    CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]:RTSP connection failed\n",m_myId);
                    nextState = WFD_SINK_STATES_IDLE;
                }
            }//else

            break;
        }
        case WFD_SINK_STM_START_TEARDOWN_EVENT:
        case WFD_SINK_STM_RCVD_TEARDOWN_EVENT:
        {
            prepareBeforeIdle(false);
            break;
        }
        default:
        {
            CSIO_LOG(m_debugLevel,   "wfdSinkStMachineClass[%d]: idleState: unprocessed events[%s].\n",
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
        	if(events == WFD_SINK_STM_START_TEARDOWN_EVENT)
			{
				resetOnRTSPTcpConnFlg();
			}

        	//Note: composeRTSPRequest will fail(no session yet)
        	int ret = composeRTSPRequest(m_rtspParserIntfSession,"TEARDOWN",parserComposeRequestCallback,(void*)this);

            if(pRTSPSinkClient && (ret == 0))
            {
				pRTSPSinkClient->sendDataOut((char*)m_requestString.c_str(),m_requestString.size());

				setTimeout(WFD_SINK_STATETIMEOUT_WAIT_RESP);
				nextState = WFD_SINK_STATES_WAIT_TD_RESP;
            }
            else
            {
            	if(isOnRTSPTcpConnSet())
					prepareForRestart(((m_curentState==WFD_SINK_STATES_WAIT_M1_RQST) && (events == WFD_SINK_STM_INTERNAL_ERROR_EVENT))?500:WFD_SINK_STATETIMEOUT_IDLE_RESTART);
				else
					prepareBeforeIdle();

            	nextState = WFD_SINK_STATES_IDLE;
            }

            break;
        }
        case WFD_SINK_STM_RCVD_TEARDOWN_EVENT:
        {
            if( pEventQ->buf_size && pEventQ->buffPtr)
            {
                CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: WFD_SINK_STM_RCVD_TEARDOWN_EVENT composed[%s][%d].\n",
                         m_myId,pEventQ->buffPtr,pEventQ->buf_size);
                pRTSPSinkClient->sendDataOut((char*)pEventQ->buffPtr,pEventQ->buf_size);
            }//else

            prepareBeforeIdle();

            nextState = WFD_SINK_STATES_IDLE;

            break;
        }
        case WFD_SINK_STM_M1_RQST_RCVD_EVENT:
        {
            //TODO: send out M1 response
            //TODO: and send out M2 request
            if(pRTSPSinkClient)
            {
                if( pEventQ->buf_size && pEventQ->buffPtr)
                {
                    CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: M1_RQST_RCVD_EVENT composed[%s][%d].\n",
                            m_myId,pEventQ->buffPtr,pEventQ->buf_size);
                    pRTSPSinkClient->sendDataOut((char*)pEventQ->buffPtr,pEventQ->buf_size);
                }
                else
                {
                    CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: M1_RQST_RCVD_EVENT no composed string.\n",
                             m_myId);
                }

                int ret = composeRTSPRequest(m_rtspParserIntfSession,"OPTIONS",parserComposeRequestCallback,(void*)this);
                if(ret == 0)
                    pRTSPSinkClient->sendDataOut((char*)m_requestString.c_str(),m_requestString.size());
                else
                    CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: composeRTSPRequest failed.\n",m_myId);

                CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: WFD_SINK_STM_M1_RQST_RCVD_EVENT processed msg size[%d].\n",
                         m_myId,m_requestString.size());

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
            setOnRTSPTcpConnFlg();
            break;
        }
        default:
        {
            CSIO_LOG(m_debugLevel,   "wfdSinkStMachineClass[%d]: waitM1RequestState: unprocessed events[%s].\n",
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
        	if(events == WFD_SINK_STM_START_TEARDOWN_EVENT)
			{
				resetOnRTSPTcpConnFlg();
			}

			//Note: composeRTSPRequest will fail(no session yet)
			int ret = composeRTSPRequest(m_rtspParserIntfSession,"TEARDOWN",parserComposeRequestCallback,(void*)this);

			if(pRTSPSinkClient && (ret == 0))
			{
				pRTSPSinkClient->sendDataOut((char*)m_requestString.c_str(),m_requestString.size());

				setTimeout(WFD_SINK_STATETIMEOUT_WAIT_RESP);
				nextState = WFD_SINK_STATES_WAIT_TD_RESP;
			}
			else
			{
				if(isOnRTSPTcpConnSet())
					prepareForRestart();
				else
					prepareBeforeIdle();

				nextState = WFD_SINK_STATES_IDLE;
			}

            break;
        }
        case WFD_SINK_STM_RCVD_TEARDOWN_EVENT:
        {
            if( pEventQ->buf_size && pEventQ->buffPtr)
            {
                CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: WFD_SINK_STM_RCVD_TEARDOWN_EVENT composed[%s][%d].\n",
                         m_myId,pEventQ->buffPtr,pEventQ->buf_size);
                pRTSPSinkClient->sendDataOut((char*)pEventQ->buffPtr,pEventQ->buf_size);
            }//else

            prepareBeforeIdle();

            nextState = WFD_SINK_STATES_IDLE;

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
            setOnRTSPTcpConnFlg();
            break;
        }
        default:
        {
            CSIO_LOG(m_debugLevel,   "wfdSinkStMachineClass[%d]: waitM2ResponseState: unprocessed events[%s].\n",
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
        	if(events == WFD_SINK_STM_START_TEARDOWN_EVENT)
			{
				resetOnRTSPTcpConnFlg();
			}

			//Note: composeRTSPRequest will fail(no session yet)
			int ret = composeRTSPRequest(m_rtspParserIntfSession,"TEARDOWN",parserComposeRequestCallback,(void*)this);

			if(pRTSPSinkClient && (ret == 0))
			{
				pRTSPSinkClient->sendDataOut((char*)m_requestString.c_str(),m_requestString.size());

				setTimeout(WFD_SINK_STATETIMEOUT_WAIT_RESP);
				nextState = WFD_SINK_STATES_WAIT_TD_RESP;
			}
			else
			{
				if(isOnRTSPTcpConnSet())
					prepareForRestart();
				else
					prepareBeforeIdle();

				nextState = WFD_SINK_STATES_IDLE;
			}

            break;
        }
        case WFD_SINK_STM_RCVD_TEARDOWN_EVENT:
        {
            if( pEventQ->buf_size && pEventQ->buffPtr)
            {
                CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: WFD_SINK_STM_RCVD_TEARDOWN_EVENT composed[%s][%d].\n",
                         m_myId,pEventQ->buffPtr,pEventQ->buf_size);
                pRTSPSinkClient->sendDataOut((char*)pEventQ->buffPtr,pEventQ->buf_size);
            }//else

            prepareBeforeIdle();

            nextState = WFD_SINK_STATES_IDLE;

            break;
        }
        case WFD_SINK_STM_M3_RQST_RCVD_EVENT:
        {
            //TODO: send out M3 response
            CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: WFD_SINK_STM_M3_RQST_RCVD_EVENT processed.\n",m_myId);

            if(pRTSPSinkClient)
            {
                if( pEventQ->buf_size && pEventQ->buffPtr)
                {
                    CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: WFD_SINK_STM_M3_RQST_RCVD_EVENT composed[%s][%d].\n",
                            m_myId,pEventQ->buffPtr,pEventQ->buf_size);
                    pRTSPSinkClient->sendDataOut((char*)pEventQ->buffPtr,pEventQ->buf_size);
                }
                else
                {
                    CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: M3_RQST_RCVD_EVENT no composed string.\n",
                             m_myId);
                }

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
            setOnRTSPTcpConnFlg();
            break;
        }
        default:
        {
            CSIO_LOG(m_debugLevel,   "wfdSinkStMachineClass[%d]: waitM3RequestState: unprocessed events[%s].\n",
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
        	if(events == WFD_SINK_STM_START_TEARDOWN_EVENT)
			{
				resetOnRTSPTcpConnFlg();
			}

			//Note: composeRTSPRequest will fail(no session yet)
			int ret = composeRTSPRequest(m_rtspParserIntfSession,"TEARDOWN",parserComposeRequestCallback,(void*)this);

			if(pRTSPSinkClient && (ret == 0))
			{
				pRTSPSinkClient->sendDataOut((char*)m_requestString.c_str(),m_requestString.size());

				setTimeout(WFD_SINK_STATETIMEOUT_WAIT_RESP);
				nextState = WFD_SINK_STATES_WAIT_TD_RESP;
			}
			else
			{
				if(isOnRTSPTcpConnSet())
					prepareForRestart();
				else
					prepareBeforeIdle();

				nextState = WFD_SINK_STATES_IDLE;
			}

            break;
        }
        case WFD_SINK_STM_RCVD_TEARDOWN_EVENT:
        {
            if( pEventQ->buf_size && pEventQ->buffPtr)
            {
                CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: WFD_SINK_STM_RCVD_TEARDOWN_EVENT composed[%s][%d].\n",
                         m_myId,pEventQ->buffPtr,pEventQ->buf_size);
                pRTSPSinkClient->sendDataOut((char*)pEventQ->buffPtr,pEventQ->buf_size);
            }//else

            prepareBeforeIdle();

            nextState = WFD_SINK_STATES_IDLE;

            break;
        }
        case WFD_SINK_STM_M4_RQST_RCVD_EVENT:
        {
            //TODO: if M4 response is ok, move on to the next state
            //      else --->  E.2 RTSP M4 with error case: this should generally
            //                 be treated as a non-recoverable failure to connect
            //                 and followed up with a teardown trigger from the WFD Source.
            //                 should received WFD_SINK_STM_INTERNAL_ERROR_EVENT.
            CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: WFD_SINK_STM_M4_RQST_RCVD_EVENT processed.\n",m_myId);

            if(pRTSPSinkClient)
            {
                if( pEventQ->buf_size && pEventQ->buffPtr)
                {
                    CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: WFD_SINK_STM_M4_RQST_RCVD_EVENT composed[%s][%d].\n",
                            m_myId,pEventQ->buffPtr,pEventQ->buf_size);
                    pRTSPSinkClient->sendDataOut((char*)pEventQ->buffPtr,pEventQ->buf_size);
                }
                else
                {
                    CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: M4_RQST_RCVD_EVENT no composed string.\n",
                             m_myId);
                }

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
            setOnRTSPTcpConnFlg();
            break;
        }
        default:
        {
            CSIO_LOG(m_debugLevel,   "wfdSinkStMachineClass[%d]: waitM4RequestState: unprocessed events[%s].\n",
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
        	if(events == WFD_SINK_STM_START_TEARDOWN_EVENT)
			{
				resetOnRTSPTcpConnFlg();
			}

			//Note: composeRTSPRequest will fail(no session yet)
			int ret = composeRTSPRequest(m_rtspParserIntfSession,"TEARDOWN",parserComposeRequestCallback,(void*)this);

			if(pRTSPSinkClient && (ret == 0))
			{
				pRTSPSinkClient->sendDataOut((char*)m_requestString.c_str(),m_requestString.size());

				setTimeout(WFD_SINK_STATETIMEOUT_WAIT_RESP);
				nextState = WFD_SINK_STATES_WAIT_TD_RESP;
			}
			else
			{
				if(isOnRTSPTcpConnSet())
					prepareForRestart();
				else
					prepareBeforeIdle();

				nextState = WFD_SINK_STATES_IDLE;
			}

            break;
        }
        case WFD_SINK_STM_RCVD_TEARDOWN_EVENT:
        {
            if( pEventQ->buf_size && pEventQ->buffPtr)
            {
                CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: WFD_SINK_STM_RCVD_TEARDOWN_EVENT composed[%s][%d].\n",
                         m_myId,pEventQ->buffPtr,pEventQ->buf_size);
                pRTSPSinkClient->sendDataOut((char*)pEventQ->buffPtr,pEventQ->buf_size);
            }//else

            prepareBeforeIdle();

            nextState = WFD_SINK_STATES_IDLE;

            break;
        }
        case WFD_SINK_STM_M5_RQST_RCVD_EVENT:
        {
            CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: WFD_SINK_STM_M5_RQST_RCVD_EVENT processed[%d][%d].\n",
                     m_myId,pEventQ->buf_size,pEventQ->ext_obj);

            if(pRTSPSinkClient)
            {
                //send out response
                if( pEventQ->buf_size && pEventQ->buffPtr)
                {
                    CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: WFD_SINK_STM_M5_RQST_RCVD_EVENT composed[%s][%d].\n",
                            m_myId,pEventQ->buffPtr,pEventQ->buf_size);
                    pRTSPSinkClient->sendDataOut((char*)pEventQ->buffPtr,pEventQ->buf_size);
                }
                else
                {
                    CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: WFD_SINK_STM_M5_RQST_RCVD_EVENT no composed string..\n",
                             m_myId);
                }

                if(pEventQ->ext_obj == WFD_SINK_TRIGGER_METHOD_PLAY)
                {
                    sendEventToParentProj(WFD_SINK_EVENTS_RTSP_IN_SESSION_EVENT);

                    //Note: although we set 15s time out, source might call tear down early
                    setTimeout(WFD_SINK_STATETIMEOUT_WAIT_GST_PIPELINE);

                    nextState = WFD_SINK_STATES_WAIT_GSTREAMER_PIPELINE_READY;
                }
                else
                {
                    int ret = composeRTSPRequest(m_rtspParserIntfSession,"SETUP",parserComposeRequestCallback,(void*)this);
                    if(ret == 0)
                        pRTSPSinkClient->sendDataOut((char*)m_requestString.c_str(),m_requestString.size());
                    else
                        CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: composeRTSPRequest failed.\n",m_myId);

                    CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: METHOD_SETUP composed[%s][%d].\n",
                             m_myId,m_requestString.c_str(),ret);

                    setTimeout(WFD_SINK_STATETIMEOUT_WAIT_RESP);

                    nextState = WFD_SINK_STATES_WAIT_M6_RESP;
                }
            }
            else
            {
                prepareForRestart();
                CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]:pRTSPSinkClient is NULL\n",m_myId,pEventQ->ext_obj);
                nextState = WFD_SINK_STATES_IDLE;
            }

            break;
        }
        case WFD_SINK_STM_START_CONN_EVENT:
        {
            setOnRTSPTcpConnFlg();
            break;
        }
        default:
        {
            CSIO_LOG(m_debugLevel,   "wfdSinkStMachineClass[%d]: waitM5RequestState: unprocessed events[%s].\n",
                     m_myId,
                     getThisArrayNames(Wfd_state_event_names,numOfWfdStateEventNamelList,events));
            break;
        }
    }

    return nextState;
}

int wfdSinkStMachineClass::waitGstPipelineReadyState(csioEventQueueStruct* pEventQ)
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

                CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: waitGstPipelineReadyState: timeout.\n",m_myId);

                prepareForRestart();

                nextState = WFD_SINK_STATES_IDLE;
            }

            break;
        }
        case WFD_SINK_STM_INTERNAL_ERROR_EVENT:
        case WFD_SINK_STM_START_TEARDOWN_EVENT:
        {
        	if(events == WFD_SINK_STM_START_TEARDOWN_EVENT)
			{
				resetOnRTSPTcpConnFlg();
			}

			//Note: composeRTSPRequest will fail(no session yet)
			int ret = composeRTSPRequest(m_rtspParserIntfSession,"TEARDOWN",parserComposeRequestCallback,(void*)this);

			if(pRTSPSinkClient && (ret == 0))
			{
				pRTSPSinkClient->sendDataOut((char*)m_requestString.c_str(),m_requestString.size());

				setTimeout(WFD_SINK_STATETIMEOUT_WAIT_RESP);
				nextState = WFD_SINK_STATES_WAIT_TD_RESP;
			}
			else
			{
				if(isOnRTSPTcpConnSet())
					prepareForRestart();
				else
					prepareBeforeIdle();

				nextState = WFD_SINK_STATES_IDLE;
			}

            break;
        }
        case WFD_SINK_STM_RCVD_TEARDOWN_EVENT:
        {
            if( pEventQ->buf_size && pEventQ->buffPtr)
            {
                CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: WFD_SINK_STM_RCVD_TEARDOWN_EVENT composed[%s][%d].\n",
                         m_myId,pEventQ->buffPtr,pEventQ->buf_size);
                pRTSPSinkClient->sendDataOut((char*)pEventQ->buffPtr,pEventQ->buf_size);
            }//else

            prepareBeforeIdle();

            nextState = WFD_SINK_STATES_IDLE;

            break;
        }
        case WFD_SINK_STM_GST_READY_RCVD_EVENT:
        {
            //TODO: move on to the next saved state
            CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: GST_READY_RCVD_EVENT next state.\n",m_myId);

            if (m_waitForTransportSwitch)
            {
                // send TCP switch M6 request and wait for response
                int ret =composeRTSPRequest(m_rtspParserIntfSession,"SETUP_TRANSPORT_SWITCH",parserComposeRequestCallback,(void*)this);
                if(ret == 0)
                    pRTSPSinkClient->sendDataOut((char*)m_requestString.c_str(),m_requestString.size());
                else
                    CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: composeRTSPRequest failed.\n",m_myId);

                CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: SETUP_TRANSPORT_SWITCH composed[%s].\n",
                        m_myId,m_requestString.c_str(),ret);

                setTimeout(WFD_SINK_STATETIMEOUT_WAIT_RESP);

                nextState = WFD_SINK_STATES_WAIT_M6_TRANSPORT_SWITCH_RESP;
            }
            else
            {
                int ret =composeRTSPRequest(m_rtspParserIntfSession,"PLAY",parserComposeRequestCallback,(void*)this);
                if(ret == 0)
                    pRTSPSinkClient->sendDataOut((char*)m_requestString.c_str(),m_requestString.size());
                else
                    CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: composeRTSPRequest failed.\n",m_myId);

                CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: METHOD_PLAY composed[%s].\n",
                        m_myId,m_requestString.c_str(),ret);

                setTimeout(WFD_SINK_STATETIMEOUT_WAIT_RESP);

                nextState = WFD_SINK_STATES_WAIT_M7_RESP;
            }

            break;
        }
        case WFD_SINK_STM_START_CONN_EVENT:
        {
            setOnRTSPTcpConnFlg();
            break;
        }
        default:
        {
            CSIO_LOG(m_debugLevel,   "wfdSinkStMachineClass[%d]: waitGstPipelineReadyState: unprocessed events[%s].\n",
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
        	if(events == WFD_SINK_STM_START_TEARDOWN_EVENT)
			{
				resetOnRTSPTcpConnFlg();
			}

			//Note: composeRTSPRequest will fail(no session yet)
			int ret = composeRTSPRequest(m_rtspParserIntfSession,"TEARDOWN",parserComposeRequestCallback,(void*)this);

			if(pRTSPSinkClient && (ret == 0))
			{
				pRTSPSinkClient->sendDataOut((char*)m_requestString.c_str(),m_requestString.size());

				setTimeout(WFD_SINK_STATETIMEOUT_WAIT_RESP);
				nextState = WFD_SINK_STATES_WAIT_TD_RESP;
			}
			else
			{
				if(isOnRTSPTcpConnSet())
					prepareForRestart();
				else
					prepareBeforeIdle();

				nextState = WFD_SINK_STATES_IDLE;
			}

            break;
        }
        case WFD_SINK_STM_RCVD_TEARDOWN_EVENT:
        {
            if( pEventQ->buf_size && pEventQ->buffPtr)
            {
                CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: WFD_SINK_STM_RCVD_TEARDOWN_EVENT composed[%s][%d].\n",
                         m_myId,pEventQ->buffPtr,pEventQ->buf_size);
                pRTSPSinkClient->sendDataOut((char*)pEventQ->buffPtr,pEventQ->buf_size);
            }//else

            prepareBeforeIdle();

            nextState = WFD_SINK_STATES_IDLE;

            break;
        }
        case WFD_SINK_STM_M6_RESP_RCVD_EVENT:
        {
            //TODO: save session number(m_session) and timeout(m_keepAliveTimeout in ms) here
            //TODO: send out M7 request
            CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: WFD_SINK_STM_M6_RESP_RCVD_EVENT processed.\n",m_myId);

            if (m_waitForTransportSwitch)
            {
                if(pRTSPSinkClient)
                {
                    //TODO: move on to the next saved state
                    CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: GST_READY_RCVD_EVENT next state.\n",m_myId);

                    int ret =composeRTSPRequest(m_rtspParserIntfSession,"PLAY",parserComposeRequestCallback,(void*)this);
                    if(ret == 0)
                        pRTSPSinkClient->sendDataOut((char*)m_requestString.c_str(),m_requestString.size());
                    else
                        CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: composeRTSPRequest failed.\n",m_myId);

                    CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: METHOD_PLAY composed[%s].\n",
                            m_myId,m_requestString.c_str(),ret);

                    setTimeout(WFD_SINK_STATETIMEOUT_WAIT_RESP);

                    nextState = WFD_SINK_STATES_WAIT_M7_RESP;
                }
                else
                {
                    prepareForRestart();
                    CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]:pRTSPSinkClient is NULL\n",m_myId);
                    nextState = WFD_SINK_STATES_IDLE;
                }
            }
            else
            {
                sendEventToParentProj(WFD_SINK_EVENTS_RTSP_IN_SESSION_EVENT);

                if(pRTSPSinkClient)
                {
                    //Note: although we set 15s time out, source might call tear down early
                    setTimeout(WFD_SINK_STATETIMEOUT_WAIT_GST_PIPELINE);

                    nextState = WFD_SINK_STATES_WAIT_GSTREAMER_PIPELINE_READY;
                }
                else
                {
                    prepareForRestart();
                    CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]:pRTSPSinkClient is NULL\n",m_myId);
                    nextState = WFD_SINK_STATES_IDLE;
                }
            }

            break;
        }
        case WFD_SINK_STM_START_CONN_EVENT:
        {
            setOnRTSPTcpConnFlg();
            break;
        }
        default:
        {
            CSIO_LOG(m_debugLevel,   "wfdSinkStMachineClass[%d]: waitM6ResponseState: unprocessed events[%s].\n",
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
        	if(events == WFD_SINK_STM_START_TEARDOWN_EVENT)
			{
				resetOnRTSPTcpConnFlg();
			}

			int ret = composeRTSPRequest(m_rtspParserIntfSession,"TEARDOWN",parserComposeRequestCallback,(void*)this);

			if(pRTSPSinkClient && (ret == 0))
			{
				pRTSPSinkClient->sendDataOut((char*)m_requestString.c_str(),m_requestString.size());

				setTimeout(WFD_SINK_STATETIMEOUT_WAIT_RESP);
				nextState = WFD_SINK_STATES_WAIT_TD_RESP;
			}
			else
			{
				if(isOnRTSPTcpConnSet())
					prepareForRestart();
				else
					prepareBeforeIdle();

				nextState = WFD_SINK_STATES_IDLE;
			}

            break;
        }
        case WFD_SINK_STM_RCVD_TEARDOWN_EVENT:
        {
            if( pEventQ->buf_size && pEventQ->buffPtr)
            {
                CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: WFD_SINK_STM_KEEP_ALIVE_RCVD_EVENT composed[%s][%d].\n",
                         m_myId,pEventQ->buffPtr,pEventQ->buf_size);
                pRTSPSinkClient->sendDataOut((char*)pEventQ->buffPtr,pEventQ->buf_size);
            }//else

            prepareBeforeIdle();

            nextState = WFD_SINK_STATES_IDLE;

            break;
        }
        case WFD_SINK_STM_M7_RESP_RCVD_EVENT:
        {
            CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: WFD_SINK_STM_M7_RESP_RCVD_EVENT processed.\n",m_myId);

            if (m_waitForTransportSwitch)
            {
                // with TCP turned on should normally get a TRANSPORT_SWITCH message
                setTimeout(WFD_SINK_STATETIMEOUT_WAIT_RESP);

                nextState = WFD_SINK_STATES_WAIT_M4_TRANSPORT_SWITCH_REQUEST;
            } else {
                resetRestartCnt();

                getTimeStamp(WFD_SINK_EVENTTIME_ENTER_KEEPALIVE_LOOP);

                setTimeout(m_keepAliveTimeout);

                //set a timer(15s) for the first frame event
                if(wfdSinkStMachineTimeArray)
                    wfdSinkStMachineTimeArray->setTimeout(WFD_SINK_STATE_1ST_FRAME_TIMEOUT_TIMER, WFD_SINK_STATETIMEOUT_WAIT_GST_PIPELINE);

                nextState = WFD_SINK_STATES_KEEP_ALIVE_LOOP;
            }

            break;
        }
        case WFD_SINK_STM_START_CONN_EVENT:
        {
            setOnRTSPTcpConnFlg();
            break;
        }
        default:
        {
            CSIO_LOG(m_debugLevel,   "wfdSinkStMachineClass[%d]: waitM7ResponseState: unprocessed events[%s].\n",
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
                if(isOnRTSPTcpConnSet())
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
            if(isOnRTSPTcpConnSet())
                prepareForRestart();
            else
                prepareBeforeIdle();

            nextState = WFD_SINK_STATES_IDLE;

            break;
        }
        case WFD_SINK_STM_START_CONN_EVENT:
        {
            setOnRTSPTcpConnFlg();
            break;
        }
        case WFD_SINK_STM_START_TEARDOWN_EVENT:
        {
            resetOnRTSPTcpConnFlg();
            break;
        }
        case WFD_SINK_STM_RCVD_TEARDOWN_EVENT:
        {
            if( pEventQ->buf_size && pEventQ->buffPtr)
            {
                CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: WFD_SINK_STM_KEEP_ALIVE_RCVD_EVENT composed[%s][%d].\n",
                         m_myId,pEventQ->buffPtr,pEventQ->buf_size);
                pRTSPSinkClient->sendDataOut((char*)pEventQ->buffPtr,pEventQ->buf_size);
            }//else

            prepareBeforeIdle();

            nextState = WFD_SINK_STATES_IDLE;

            break;
        }
        default:
        {
            CSIO_LOG(m_debugLevel,   "wfdSinkStMachineClass[%d]: waitTDResponseState: unprocessed events[%s].\n",
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

            //check timer(15s) for the first frame event
            if(wfdSinkStMachineTimeArray && wfdSinkStMachineTimeArray->isTimeout(WFD_SINK_STATE_1ST_FRAME_TIMEOUT_TIMER))
            {
                CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: 1ST_FRAME_TIMEOUT .\n",m_myId);
                wfdSinkStMachineTimeArray->resetTimeout(WFD_SINK_STATE_1ST_FRAME_TIMEOUT_TIMER);

                int ret = composeRTSPRequest(m_rtspParserIntfSession,"TEARDOWN",parserComposeRequestCallback,(void*)this);

                if(pRTSPSinkClient && (ret == 0))
                {
                    pRTSPSinkClient->sendDataOut((char*)m_requestString.c_str(),m_requestString.size());

                    setTimeout(WFD_SINK_STATETIMEOUT_WAIT_RESP);
                    nextState = WFD_SINK_STATES_WAIT_TD_RESP;
                }
                else
                {
                    if(isOnRTSPTcpConnSet())
                        prepareForRestart();
                    else
                        prepareBeforeIdle();

                    nextState = WFD_SINK_STATES_IDLE;
                }
            }//else

            break;
        }
        case WFD_SINK_STM_INTERNAL_ERROR_EVENT:
        case WFD_SINK_STM_START_TEARDOWN_EVENT:
        case WFD_SINK_GST_LOST_VIDEO_EVENT:
        {
            if(events == WFD_SINK_STM_START_TEARDOWN_EVENT)
            {
                resetOnRTSPTcpConnFlg();
            }

            int ret = composeRTSPRequest(m_rtspParserIntfSession,"TEARDOWN",parserComposeRequestCallback,(void*)this);

            if(pRTSPSinkClient && (ret == 0))
            {
                pRTSPSinkClient->sendDataOut((char*)m_requestString.c_str(),m_requestString.size());

                setTimeout(WFD_SINK_STATETIMEOUT_WAIT_RESP);
                nextState = WFD_SINK_STATES_WAIT_TD_RESP;
            }
            else
            {
                if(isOnRTSPTcpConnSet())
                    prepareForRestart();
                else
                    prepareBeforeIdle();

                nextState = WFD_SINK_STATES_IDLE;
            }

            break;
        }
        case WFD_SINK_STM_KEEP_ALIVE_RCVD_EVENT:
        {
            setTimeout(m_keepAliveTimeout);

            //TODO: send out response
            CSIO_LOG(ABOVE_DEBUG_VERB(m_debugLevel),  "wfdSinkStMachineClass[%d]: WFD_SINK_STM_KEEP_ALIVE_RCVD_EVENT processed.\n",m_myId);

            if(pRTSPSinkClient)
            {
                if( pEventQ->buf_size && pEventQ->buffPtr)
                {
                    CSIO_LOG(ABOVE_DEBUG_VERB(m_debugLevel),  "wfdSinkStMachineClass[%d]: WFD_SINK_STM_KEEP_ALIVE_RCVD_EVENT composed[%s][%d].\n",
                            m_myId,pEventQ->buffPtr,pEventQ->buf_size);
                    pRTSPSinkClient->sendDataOut((char*)pEventQ->buffPtr,pEventQ->buf_size);
                }
                else
                {
                    CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: KEEP_ALIVE_RCVD_EVENT no composed string.\n",
                             m_myId);
                }
            }
            else
            {
                prepareForRestart();
                CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]:pRTSPSinkClient is NULL\n",m_myId);

                nextState = WFD_SINK_STATES_IDLE;
            }
            break;
        }
        case WFD_SINK_STM_RCVD_TEARDOWN_EVENT:
        {
            if( pEventQ->buf_size && pEventQ->buffPtr)
            {
                CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: WFD_SINK_STM_KEEP_ALIVE_RCVD_EVENT composed[%s][%d].\n",
                         m_myId,pEventQ->buffPtr,pEventQ->buf_size);
                pRTSPSinkClient->sendDataOut((char*)pEventQ->buffPtr,pEventQ->buf_size);
            }//else

            prepareBeforeIdle();

            nextState = WFD_SINK_STATES_IDLE;

            break;
        }
        case WFD_SINK_STM_IDR_REQ_EVENT:
        {
            //TODO: send out idr request
            CSIO_LOG(ABOVE_DEBUG_VERB(m_debugLevel),  "wfdSinkStMachineClass[%d]: WFD_SINK_STM_IDR_REQ_EVENT processed.\n",m_myId);

            if(pRTSPSinkClient)
            {
                //std::string m13rqst = "SET_PARAMETER rtsp://localhost/wfd1.0 RTSP/1.0\r\nContent-Length: 17\r\nContent-Type: text/parameters\r\nCSeq: 4\r\n\r\nwfd_idr_request\r\n";
                //pRTSPSinkClient->sendDataOut((char*)m13rqst.c_str(),m13rqst.size());
                
                int ret = composeRTSPRequest(m_rtspParserIntfSession,"SET_PARAMETER_IDR_REQ",parserComposeRequestCallback,(void*)this);
                if(ret == 0)
                {
                    pRTSPSinkClient->sendDataOut((char*)m_requestString.c_str(),m_requestString.size());
                    CSIO_LOG(ABOVE_DEBUG_VERB(m_debugLevel),  "wfdSinkStMachineClass[%d]: send out idr request[%s].\n",m_myId,m_requestString.c_str());
                }
                else
                {
                    CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: composeRTSPRequest failed.\n",m_myId);
                }
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
            setOnRTSPTcpConnFlg();
            break;
        }
        case WFD_SINK_GST_1ST_FRAME_EVENT:
        {
            wfdSinkStMachineTimeArray->resetTimeout(WFD_SINK_STATE_1ST_FRAME_TIMEOUT_TIMER);
            CSIO_LOG(ABOVE_DEBUG_VERB(m_debugLevel),  "wfdSinkStMachineClass[%d]: WFD_SINK_GST_1ST_FRAME_EVENT processed.\n",m_myId);
            break;
        }
        default:
        {
            CSIO_LOG(m_debugLevel,   "wfdSinkStMachineClass[%d]: monitorKeepAliveState: unprocessed events[%s].\n",
                    m_myId,getThisArrayNames(Wfd_state_event_names,numOfWfdStateEventNamelList,events));
            break;
        }
    }

    return nextState;
}

int wfdSinkStMachineClass::waitM4TransportSwitchRequestState(csioEventQueueStruct* pEventQ)
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

                CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: waitM4TransportSwitchRequestState: timeout.\n",m_myId);

                prepareForRestart();

                nextState = WFD_SINK_STATES_IDLE;
            }

            break;
        }
        case WFD_SINK_STM_INTERNAL_ERROR_EVENT:
        case WFD_SINK_STM_START_TEARDOWN_EVENT:
        {
            if(events == WFD_SINK_STM_START_TEARDOWN_EVENT)
            {
                resetOnRTSPTcpConnFlg();
            }

            //Note: composeRTSPRequest will fail(no session yet)
            int ret = composeRTSPRequest(m_rtspParserIntfSession,"TEARDOWN",parserComposeRequestCallback,(void*)this);

            if(pRTSPSinkClient && (ret == 0))
            {
                pRTSPSinkClient->sendDataOut((char*)m_requestString.c_str(),m_requestString.size());

                setTimeout(WFD_SINK_STATETIMEOUT_WAIT_RESP);
                nextState = WFD_SINK_STATES_WAIT_TD_RESP;
            }
            else
            {
                if(isOnRTSPTcpConnSet())
                    prepareForRestart();
                else
                    prepareBeforeIdle();

                nextState = WFD_SINK_STATES_IDLE;
            }

            break;
        }
        case WFD_SINK_STM_RCVD_TEARDOWN_EVENT:
        {
            if( pEventQ->buf_size && pEventQ->buffPtr)
            {
                CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: WFD_SINK_STM_RCVD_TEARDOWN_EVENT composed[%s][%d].\n",
                         m_myId,pEventQ->buffPtr,pEventQ->buf_size);
                pRTSPSinkClient->sendDataOut((char*)pEventQ->buffPtr,pEventQ->buf_size);
            }//else

            prepareBeforeIdle();

            nextState = WFD_SINK_STATES_IDLE;

            break;
        }
        case WFD_SINK_STM_M4_TRANSPORT_SWITCH_RQST_RCVD_EVENT:
        {
            CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: WFD_SINK_STM_M4_TRANSPORT_SWITCH_RQST_RCVD_EVENT processed[%d][%d].\n",
                     m_myId,pEventQ->buf_size,pEventQ->ext_obj);

            if(pRTSPSinkClient)
            {
                //send out response
                if( pEventQ->buf_size && pEventQ->buffPtr)
                {
                    CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: WFD_SINK_STM_M4_TRANSPORT_SWITCH_RQST_RCVD_EVENT composed[%s][%d].\n",
                            m_myId,pEventQ->buffPtr,pEventQ->buf_size);
                    pRTSPSinkClient->sendDataOut((char*)pEventQ->buffPtr,pEventQ->buf_size);
                }
                else
                {
                    CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: WFD_SINK_STM_M4_TRANSPORT_SWITCH_RQST_RCVD_EVENT no composed string..\n",
                             m_myId);
                }

#if 1
                // start up pipeline
                sendEventToParentProj(WFD_SINK_EVENTS_RTSP_IN_SESSION_EVENT);

                //Note: although we set 15s time out, source might call tear down early
                setTimeout(WFD_SINK_STATETIMEOUT_WAIT_GST_PIPELINE);

                nextState = WFD_SINK_STATES_WAIT_GSTREAMER_PIPELINE_READY;
#endif
            }
            else
            {
                prepareForRestart();
                CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]:pRTSPSinkClient is NULL\n",m_myId,pEventQ->ext_obj);
                nextState = WFD_SINK_STATES_IDLE;
            }

            break;
        }
        case WFD_SINK_STM_START_CONN_EVENT:
        {
            setOnRTSPTcpConnFlg();
            break;
        }
        default:
        {
            CSIO_LOG(m_debugLevel,   "wfdSinkStMachineClass[%d]: waitM5RequestState: unprocessed events[%s].\n",
                     m_myId,
                     getThisArrayNames(Wfd_state_event_names,numOfWfdStateEventNamelList,events));
            break;
        }
    }

    return nextState;
}

int wfdSinkStMachineClass::waitM6TransportSwitchResponseState(csioEventQueueStruct* pEventQ)
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

                CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: waitM4TransportSwitchRequestState: timeout.\n",m_myId);

                prepareForRestart();

                nextState = WFD_SINK_STATES_IDLE;
            }

            break;
        }
        case WFD_SINK_STM_INTERNAL_ERROR_EVENT:
        case WFD_SINK_STM_START_TEARDOWN_EVENT:
        {
            if(events == WFD_SINK_STM_START_TEARDOWN_EVENT)
            {
                resetOnRTSPTcpConnFlg();
            }

            //Note: composeRTSPRequest will fail(no session yet)
            int ret = composeRTSPRequest(m_rtspParserIntfSession,"TEARDOWN",parserComposeRequestCallback,(void*)this);

            if(pRTSPSinkClient && (ret == 0))
            {
                pRTSPSinkClient->sendDataOut((char*)m_requestString.c_str(),m_requestString.size());

                setTimeout(WFD_SINK_STATETIMEOUT_WAIT_RESP);
                nextState = WFD_SINK_STATES_WAIT_TD_RESP;
            }
            else
            {
                if(isOnRTSPTcpConnSet())
                    prepareForRestart();
                else
                    prepareBeforeIdle();

                nextState = WFD_SINK_STATES_IDLE;
            }

            break;
        }
        case WFD_SINK_STM_RCVD_TEARDOWN_EVENT:
        {
            if( pEventQ->buf_size && pEventQ->buffPtr)
            {
                CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: WFD_SINK_STM_RCVD_TEARDOWN_EVENT composed[%s][%d].\n",
                         m_myId,pEventQ->buffPtr,pEventQ->buf_size);
                pRTSPSinkClient->sendDataOut((char*)pEventQ->buffPtr,pEventQ->buf_size);
            }//else

            prepareBeforeIdle();

            nextState = WFD_SINK_STATES_IDLE;

            break;
        }
        case WFD_SINK_STM_M4_TRANSPORT_SWITCH_RESP_RCVD_EVENT:
        {
            CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: WFD_SINK_STM_M4_TRANSPORT_SWITCH_RESP_RCVD_EVENT processed[%d][%d].\n",
                     m_myId,pEventQ->buf_size,pEventQ->ext_obj);

            if(pRTSPSinkClient)
            {
                //send out response
                if( pEventQ->buf_size && pEventQ->buffPtr)
                {
                    CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: WFD_SINK_STM_M4_TRANSPORT_SWITCH_RESP_RCVD_EVENT composed[%s][%d].\n",
                            m_myId,pEventQ->buffPtr,pEventQ->buf_size);
                    //pRTSPSinkClient->sendDataOut((char*)pEventQ->buffPtr,pEventQ->buf_size);
                }
                else
                {
                    CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]: WFD_SINK_STM_M4_TRANSPORT_SWITCH_RESP_RCVD_EVENT no composed string..\n",
                             m_myId);
                }

                resetRestartCnt();

                getTimeStamp(WFD_SINK_EVENTTIME_ENTER_KEEPALIVE_LOOP);

                setTimeout(m_keepAliveTimeout);

                //set a timer(15s) for the first frame event
                if(wfdSinkStMachineTimeArray)
                    wfdSinkStMachineTimeArray->setTimeout(WFD_SINK_STATE_1ST_FRAME_TIMEOUT_TIMER, WFD_SINK_STATETIMEOUT_WAIT_GST_PIPELINE);

                nextState = WFD_SINK_STATES_KEEP_ALIVE_LOOP;
            }
            else
            {
                prepareForRestart();
                CSIO_LOG(m_debugLevel,  "wfdSinkStMachineClass[%d]:pRTSPSinkClient is NULL\n",m_myId,pEventQ->ext_obj);
                nextState = WFD_SINK_STATES_IDLE;
            }

            break;
        }
        case WFD_SINK_STM_START_CONN_EVENT:
        {
            setOnRTSPTcpConnFlg();
            break;
        }
        default:
        {
            CSIO_LOG(m_debugLevel,   "wfdSinkStMachineClass[%d]: waitM5RequestState: unprocessed events[%s].\n",
                     m_myId,
                     getThisArrayNames(Wfd_state_event_names,numOfWfdStateEventNamelList,events));
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
    CSIO_LOG(ABOVE_DEBUG_XTRVERB(m_debugLevel), "wfdSinkStMachineClass[%d]: processPackets size[%d][%s]\n", m_myId,size,buf);

    CSIO_LOG(ABOVE_DEBUG_XTRVERB(m_debugLevel), "m_curentState[%d]: expecting RTSP header[%s]\n",
            m_curentState,Wfd_rtsp_msg_string_vs_event_names[m_curentState].pStr);

    //Note: we are using wfdSinkStMachineClass::m_EvntQ here,
    //      must reset event queue before we call parseRTSPMessage
    memset(&m_EvntQ,0,sizeof(csioEventQueueStruct));
    m_EvntQ.obj_id = m_myId;
    m_EvntQ.event_type = WFD_SINK_STM_TIME_TICKS_EVENT;

    //two call backs might fill up events into m_EvntQ
    int ret = parseRTSPMessage(m_rtspParserIntfSession,buf,parserCallbackFun,(void*)this);

    if(ret == 0)
    {
        stateFunction(&m_EvntQ);
    }//else

    if( m_EvntQ.buf_size && m_EvntQ.buffPtr)
    {
        deleteCharArray(m_EvntQ.buffPtr);
    }

    CSIO_LOG(ABOVE_DEBUG_XTRVERB(m_debugLevel), "wfdSinkStMachineClass[%d]: processPackets: called parseRTSPMessage[%d] exits.\n", m_myId,ret);
}

int wfdSinkStMachineClass::parserCallbackFun(RTSPPARSINGRESULTS * parsResPtr, void * appArgument)
{
    if(!parsResPtr || !parsResPtr->parsedMessagePtr)
    {
        CSIO_LOG(eLogLevel_error, "wfdSinkStMachineClass: parserCallbackFun parsResPtr is NULL\n");
        return(-1);
    }

    wfdSinkStMachineClass* p = (wfdSinkStMachineClass*)appArgument;

    if(p)
    {
        CSIO_LOG(ABOVE_DEBUG_XTRVERB(p->m_debugLevel), "wfdSinkStMachineClass[%d]: parserCallbackFun: received parsed messageType[%d]\n", p->m_myId,parsResPtr->messageType);

        switch(parsResPtr->messageType)
        {
            case RTSP_MESSAGE_REQUEST:
            {
                CSIO_LOG(ABOVE_DEBUG_XTRVERB(p->m_debugLevel), "wfdSinkStMachineClass[%d]: parserCallbackFun: received parsed request\n", p->m_myId);

                if(Wfd_rtsp_msg_type_vs_event_names[p->m_curentState][0] == RTSP_MESSAGE_REQUEST)
                {
                    if(parsResPtr->request_method)
                    {
                        CSIO_LOG(ABOVE_DEBUG_XTRVERB(p->m_debugLevel), "wfdSinkStMachineClass[%d]: parserCallbackFun: request_method: %s\n", p->m_myId,parsResPtr->request_method);

                        if(strcasestr( parsResPtr->request_method, Wfd_rtsp_msg_string_vs_event_names[p->m_curentState].pStr ))
                        {
                            //looking for triggerMethod
                            if(parsResPtr->headerData.triggerMethod)
                            {
                                CSIO_LOG(ABOVE_DEBUG_XTRVERB(p->m_debugLevel), "wfdSinkStMachineClass[%d]: parserCallbackFun: triggerMethod[%s]\n",
                                         p->m_myId,parsResPtr->headerData.triggerMethod);

                                if(strcasestr( parsResPtr->headerData.triggerMethod, "TEARDOWN" ))
                                {
                                    p->m_EvntQ.ext_obj = WFD_SINK_TRIGGER_METHOD_TEARDOWN;
                                }
                                else if(strcasestr( parsResPtr->headerData.triggerMethod, "SETUP" ))
                                {
                                    p->m_EvntQ.ext_obj = WFD_SINK_TRIGGER_METHOD_SETUP;
                                }
                                else if(strcasestr( parsResPtr->headerData.triggerMethod, "PLAY" ))
                                {
                                    p->m_EvntQ.ext_obj = WFD_SINK_TRIGGER_METHOD_PLAY;
                                }//else
                            }//else

                            CSIO_LOG(ABOVE_DEBUG_XTRVERB(p->m_debugLevel), "wfdSinkStMachineClass[%d]: parserCallbackFun: triggerMethod: %d\n",
                                     p->m_myId,p->m_EvntQ.ext_obj);

                            composeRTSPResponse(p->m_rtspParserIntfSession,parsResPtr,RTSP_CODE_OK,parserComposeRespCallback,(void *)appArgument);

                            if (strcasestr(parsResPtr->request_method, "GET_PARAMETER") && (p->m_curentState == WFD_SINK_STATES_WAIT_M3_RQST))
                            {
                                p->m_waitForTransportSwitch = ((struct rtsp *)p->m_rtspParserIntfSession)->waitForTcpSwitch;
                                CSIO_LOG(p->m_debugLevel, "wfdSinkStMachineClass[%d]: parserCallbackFun: wait for TRANSPORT_SWITCH message = %d",
                                        p->m_myId, ((struct rtsp *)p->m_rtspParserIntfSession)->waitForTcpSwitch);
                            }

                            if (strcasestr(parsResPtr->request_method, "SET_PARAMETER") && (p->m_curentState == WFD_SINK_STATES_WAIT_M4_TRANSPORT_SWITCH_REQUEST))
                            {
                                if (parsResPtr->headerData.transportSwitch)
                                {
                                    CSIO_LOG(p->m_debugLevel, "wfdSinkStMachineClass[%d]: parserCallbackFun: transportSwitch[%s]\n",
                                            p->m_myId,parsResPtr->headerData.transportSwitch);

                                    if (strcasestr(parsResPtr->headerData.transportSwitch, "RTP/AVP/TCP"))
                                    {
                                        CSIO_LOG(p->m_debugLevel, "wfdSinkStMachineClass[%d]: calling composeRTSPResponse OK\n", p->m_myId);
                                        composeRTSPResponse(p->m_rtspParserIntfSession,parsResPtr,RTSP_CODE_OK,parserComposeRespCallback,(void *)appArgument);
                                        p->m_useTcpTransport = true;
                                        CSIO_LOG(p->m_debugLevel, "wfdSinkStMachineClass[%d]: useTcpTransport flag set[%d]\n",
                                                p->m_myId,p->m_useTcpTransport);
                                    } else {
                                        CSIO_LOG(p->m_debugLevel, "wfdSinkStMachineClass[%d]: unexpected transport_switch -- calling composeRTSPResponse BAD\n", p->m_myId);
                                        composeRTSPResponse(p->m_rtspParserIntfSession,parsResPtr,RTSP_CODE_BAD_REQUEST,parserComposeRespCallback,(void *)appArgument);
                                    }
                                }
                            }
                        }
                        else
                        {
                            CSIO_LOG(ABOVE_DEBUG_XTRVERB(p->m_debugLevel), "wfdSinkStMachineClass[%d]: parserCallbackFun: request_method[%s] not match[%s]\n",
                                    p->m_myId,
                                    parsResPtr->request_method,
                                    Wfd_rtsp_msg_string_vs_event_names[p->m_curentState].pStr);

                            //Note:: check to see if this is TEARDOWN request.
                            if(strcasestr( parsResPtr->request_method, "SET_PARAMETER" ))
                            {
                                if(parsResPtr->headerData.triggerMethod)
                                {
                                    if(strcasestr( parsResPtr->headerData.triggerMethod, "TEARDOWN" ))
                                    {
                                        p->m_EvntQ.ext_obj = WFD_SINK_TRIGGER_METHOD_TEARDOWN;

                                        composeRTSPResponse(p->m_rtspParserIntfSession,parsResPtr,RTSP_CODE_OK,parserComposeRespCallback,(void *)appArgument);
                                    }
                                }
                                else if(parsResPtr->headerData.msLatencyCapStr)
                                {
                                    p->m_msLatency = WFD_SINK_MS_LATENCY_CAP_INVALID;
                                    if(strcasestr( parsResPtr->headerData.msLatencyCapStr, "low" ))
                                    {
                                        p->m_msLatency = WFD_SINK_MS_LATENCY_CAP_LOW;
                                        composeRTSPResponse(p->m_rtspParserIntfSession,parsResPtr,RTSP_CODE_OK,parserComposeRespCallback,(void *)appArgument);
                                    }
                                    else if(strcasestr( parsResPtr->headerData.msLatencyCapStr, "normal" ))
                                    {
                                        p->m_msLatency = WFD_SINK_MS_LATENCY_CAP_NORMAL;
                                        composeRTSPResponse(p->m_rtspParserIntfSession,parsResPtr,RTSP_CODE_OK,parserComposeRespCallback,(void *)appArgument);
                                    }
                                    else if(strcasestr( parsResPtr->headerData.msLatencyCapStr, "high" ))
                                    {
                                        p->m_msLatency = WFD_SINK_MS_LATENCY_CAP_HIGH;
                                        composeRTSPResponse(p->m_rtspParserIntfSession,parsResPtr,RTSP_CODE_OK,parserComposeRespCallback,(void *)appArgument);
                                    }
                                    else
                                    {
                                        p->m_msLatency = WFD_SINK_MS_LATENCY_CAP_INVALID;
                                        composeRTSPResponse(p->m_rtspParserIntfSession,parsResPtr,RTSP_CODE_BAD_REQUEST,parserComposeRespCallback,(void *)appArgument);
                                    }

                                    //sendEventToParentProj(WFD_SINK_EVENTS_RTSP_SET_LATENCY_EVENT);
                                    {
                                        csioEventQueueStruct EvntQ;
                                        memset(&EvntQ,0,sizeof(csioEventQueueStruct));
                                        EvntQ.obj_id = p->m_myId;
                                        EvntQ.event_type = WFD_SINK_EVENTS_RTSP_SET_LATENCY_EVENT;
                                        EvntQ.ext_obj = p->m_msLatency;

                                        p->m_parent->sendEvent(&EvntQ);
                                    }

                                    CSIO_LOG(ABOVE_DEBUG_VERB(p->m_debugLevel), "wfdSinkStMachineClass[%d]: parserCallbackFun: received ms latency[%d]\n",
                                             p->m_myId,p->m_msLatency);
                                }//else
                            }//else: stay in the same states until next message
                        }
                    }
                    else
                    {
                        CSIO_LOG(ABOVE_DEBUG_XTRVERB(p->m_debugLevel), "wfdSinkStMachineClass[%d]: parserCallbackFun: REQUEST without request_method\n", p->m_myId);
                    }
                }
                else
                {
                    CSIO_LOG(ABOVE_DEBUG_XTRVERB(p->m_debugLevel), "wfdSinkStMachineClass[%d]: parserCallbackFun: m_curentState[%d] not waiting for request[%s].\n",
                             p->m_myId,p->m_curentState,
                             parsResPtr->request_method? parsResPtr->request_method:"NONE");

                    /*if this is set_parameter*/
                    if(parsResPtr->request_method)
                    {
                        if(strcasestr( parsResPtr->request_method, "SET_PARAMETER" ))
                        {
                            if(parsResPtr->headerData.triggerMethod)
                            {
                                if(strcasestr( parsResPtr->headerData.triggerMethod, "TEARDOWN" ))
                                {
                                    p->m_EvntQ.ext_obj = WFD_SINK_TRIGGER_METHOD_TEARDOWN;

                                    composeRTSPResponse(p->m_rtspParserIntfSession,parsResPtr,RTSP_CODE_OK,parserComposeRespCallback,(void *)appArgument);
                                }
                            }
                        }
                    }//all else do nothing
                }

                break;
            }
            case RTSP_MESSAGE_REPLY:
            {
                CSIO_LOG(ABOVE_DEBUG_XTRVERB(p->m_debugLevel), "wfdSinkStMachineClass[%d]: parserCallbackFun: received parsed response\n", p->m_myId);

                if(Wfd_rtsp_msg_type_vs_event_names[p->m_curentState][0] == RTSP_MESSAGE_REPLY)
                {
                    if(parsResPtr->reply_phrase)
                    {
                        CSIO_LOG(ABOVE_DEBUG_XTRVERB(p->m_debugLevel), "wfdSinkStMachineClass[%d]: parserCallbackFun: reply_phrase: %s\n", p->m_myId,parsResPtr->reply_phrase);
                    }

                    CSIO_LOG(ABOVE_DEBUG_XTRVERB(p->m_debugLevel), "wfdSinkStMachineClass[%d]: parserCallbackFun: reply_code: %d\n", p->m_myId,parsResPtr->reply_code);
                    CSIO_LOG(ABOVE_DEBUG_XTRVERB(p->m_debugLevel), "wfdSinkStMachineClass[%d]: parserCallbackFun: number of headers: %d\n", p->m_myId,parsResPtr->parsedMessagePtr->header_used);

                    if(parsResPtr->reply_code == 200)
                    {
                        p->m_EvntQ.event_type = Wfd_rtsp_msg_string_vs_event_names[p->m_curentState].num;
                    }
                    else
                    {
                        p->m_EvntQ.event_type = WFD_SINK_STM_INTERNAL_ERROR_EVENT;
                    }

                    //looking for keepAliveTimeout
                    if(parsResPtr->headerData.keepAliveTimeout != -1)
                    {
                        CSIO_LOG(ABOVE_DEBUG_XTRVERB(p->m_debugLevel), "wfdSinkStMachineClass[%d]: parserCallbackFun: keepAliveTimeout[%d]\n",
                                 p->m_myId,parsResPtr->headerData.keepAliveTimeout);

                        //convert headerData.keepAliveTimeout(in second) to m_keepAliveTimeout(in ms)
                        p->m_keepAliveTimeout = parsResPtr->headerData.keepAliveTimeout * 1000 ;
                    }//else

                    //ssrc == 0 is not valid
                    if(parsResPtr->headerData.ssrc)
                        p->m_ssrc = parsResPtr->headerData.ssrc;

                    if(parsResPtr->headerData.sourceRTPPort[1] != -1)
                        p->m_rtcpDestPort = parsResPtr->headerData.sourceRTPPort[1];

                    //looking for source OS version
                    if(parsResPtr->headerData.srcVersionStr)
                        p->m_srcVersionStr = parsResPtr->headerData.srcVersionStr;
                }
                //else TODO:if we received response, but we are waiting for request, shall we just ignore it?
                //p->m_EvntQ.event_type = WFD_SINK_STM_INTERNAL_ERROR_EVENT;
                break;
            }
            default:
            {
                CSIO_LOG(p->m_debugLevel, "wfdSinkStMachineClass[%d]: parserCallbackFun: unexpected message type: %d\n\n",p->m_myId,parsResPtr->messageType);

                break;
            }
        }//end of switch
    }//end of if(p)
    else
    {
        CSIO_LOG(eLogLevel_error, "wfdSinkStMachineClass[%d]: parserCallbackFun: parserCallbackFun pObj is NULL\n", p->m_myId);
    }

    return 0;
}
int wfdSinkStMachineClass::parserComposeRespCallback(RTSPCOMPOSINGRESULTS * composingResPtr, void * appArgument)
{
    if(!composingResPtr || !appArgument)
    {
        CSIO_LOG(eLogLevel_error, "wfdSinkStMachineClass: parserComposeRespCallback composingResPtr is NULL\n");
        return(-1);
    }

    wfdSinkStMachineClass* p = (wfdSinkStMachineClass*)appArgument;

    if(p)
    {
        if(composingResPtr->composedMessagePtr)
        {
            int type = Wfd_rtsp_msg_string_vs_event_names[p->m_curentState].num;

            //Note: composeRTSPResponse was called because WFD_SINK_TRIGGER_METHOD_TEARDOWN received from source
            if(p->m_EvntQ.ext_obj == WFD_SINK_TRIGGER_METHOD_TEARDOWN)
            {
                type = WFD_SINK_STM_RCVD_TEARDOWN_EVENT;
            }//else

            CSIO_LOG(ABOVE_DEBUG_XTRVERB(p->m_debugLevel), "wfdSinkStMachineClass[%d]: parserComposeRespCallback event_type[%d]\n",
                     p->m_myId,type);

            int dataSize = strlen(composingResPtr->composedMessagePtr);

            if(composingResPtr->composedMessagePtr && dataSize)
            {
                char* tmp = (char*)p->createCharArray(dataSize + 1);
                if(tmp)
                {
                    //first copy configure structure
                    memcpy(tmp,(char*)composingResPtr->composedMessagePtr,dataSize);
                    tmp[dataSize] = 0;
                    p->m_EvntQ.event_type = type;
                    p->m_EvntQ.buffPtr = tmp;
                    p->m_EvntQ.buf_size = dataSize;
                }//else
            }
        }
        else
        {
            CSIO_LOG(p->m_debugLevel, "wfdSinkStMachineClass[%d]: parserComposeRespCallback composedMessagePtr is NULL\n", p->m_myId);
        }
    }
    else
    {
        CSIO_LOG(eLogLevel_error, "wfdSinkStMachineClass[%d]: parserComposeRespCallback composingResPtr->composedMessagePtr is NULL\n", p->m_myId);
    }
    return 0;
}
int wfdSinkStMachineClass::parserComposeRequestCallback(RTSPCOMPOSINGRESULTS * composingResPtr, void * appArgument)
{
    if(!composingResPtr || !appArgument)
    {
        CSIO_LOG(eLogLevel_error, "wfdSinkStMachineClass: parserComposeRequestCallback composingResPtr is NULL\n");
        return(-1);
    }

    wfdSinkStMachineClass* p = (wfdSinkStMachineClass*)appArgument;

    if(p)
    {
        p->m_requestString.clear();

        if(composingResPtr->composedMessagePtr)
        {
            p->m_requestString = (char*)composingResPtr->composedMessagePtr;

            CSIO_LOG(ABOVE_DEBUG_VERB(p->m_debugLevel), "wfdSinkStMachineClass: parserComposeRequestCallback composedMessagePtr[%s]\n",composingResPtr->composedMessagePtr);
        }
        else
        {
            CSIO_LOG(p->m_debugLevel, "wfdSinkStMachineClass[%d]: parserComposeRequestCallback appArgument is NULL\n", p->m_myId);
        }
    }//else

    return 0;
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
                            CSIO_LOG(m_debugLevel, "wfdSinkStMachineThread: m_wfdSinkStMachineTaskList[%d] exist[0x%x]\n",
                                     id,wfdSinkStMachineThread::m_wfdSinkStMachineTaskList[id]);

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
                                    bool isTx3 = (evntQPtr->reserved[1] == 1) ? true:false;
                                    p->setMaxMiracastRate(isTx3);
                                    p->setVideoResolutionDefaults();
                                    p->setVideo2ResolutionDefaults(isTx3, evntQPtr->reserved[2]);
                                    p->setCurentSourcePort(evntQPtr->ext_obj) ;
                                    p->setCurentTsPort(evntQPtr->ext_obj2) ;
                                    p->setIsTx3Session(isTx3);

                                    std::string str = (char*)evntQPtr->buffPtr;
                                    p->setCurentSourceUrl(str);
                                    p->m_setMiceSession((int)evntQPtr->reserved[0]);
                                    CSIO_LOG(m_debugLevel, "wfdSinkStMachineThread:m_getMiceSession[%d] m_IsTx3session[%d] isTx3[%d]\n",p->m_getMiceSession(), p->getIsTx3session(), isTx3);

                                    csioEventQueueStruct evntQ;
                                    memset(&evntQ,0,sizeof(csioEventQueueStruct));
                                    evntQ.event_type = WFD_SINK_STM_START_CONN_EVENT;
                                    p->stateFunction(&evntQ);
                                }
                                else
                                {
                                    CSIO_LOG(m_debugLevel, "wfdSinkStMachineThread:WFD_SINK_INSERT_STMACHINE_EVENT[%d] no url\n",id);
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
                            bool isTx3 = (evntQPtr->reserved[1] == 1) ? true:false;
                            p->setMaxMiracastRate(isTx3);
                            p->setVideoResolutionDefaults();
                            p->setVideo2ResolutionDefaults(isTx3, evntQPtr->reserved[2]);
                            p->setCurentSourcePort(evntQPtr->ext_obj) ;
                            p->setCurentTsPort(evntQPtr->ext_obj2) ;
                            p->setUseTcpTransport(false);
                            p->setIsTx3Session(isTx3);

                            std::string str = (char*)evntQPtr->buffPtr;
                            p->setCurentSourceUrl(str);
                            p->m_setMiceSession((int)evntQPtr->reserved[0]);
                            CSIO_LOG(m_debugLevel, "wfdSinkStMachineThread:m_getMiceSession[%d] m_IsTx3session[%d] isTx3[%d]\n",p->m_getMiceSession(), p->getIsTx3session(), isTx3);

                            csioEventQueueStruct evntQ;
                            memset(&evntQ,0,sizeof(csioEventQueueStruct));
                            evntQ.event_type = WFD_SINK_STM_START_CONN_EVENT;
                            p->stateFunction(&evntQ);
                        }

                        CSIO_LOG(m_debugLevel, "wfdSinkStMachineThread:WFD_SINK_START_STMACHINE_EVENT[%d] processed\n",id);
                        deleteCharArray(evntQPtr->buffPtr);
                    }
                    else
                    {
                        CSIO_LOG(m_debugLevel, "wfdSinkStMachineThread:WFD_SINK_START_STMACHINE_EVENT[%d] no url\n",id);
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

                        CSIO_LOG(m_debugLevel, "wfdSinkStMachineThread:WFD_SINK_TEARDOWN_TCP_CONN_EVENT[%d] processed\n",id);
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

                        CSIO_LOG(ABOVE_DEBUG_VERB(m_debugLevel), "wfdSinkStMachineThread:WFD_SINK_STM_IDR_REQ_EVENT[%d] processed\n",id);
                    }

                    break;
                }
                case WFD_SINK_GST_READY_EVENT:
                {
                    int id = evntQPtr->obj_id;

                    if(wfdSinkStMachineThread::m_wfdSinkStMachineTaskList[id])
                    {
                        wfdSinkStMachineClass* p = wfdSinkStMachineThread::m_wfdSinkStMachineTaskList[id];

                        csioEventQueueStruct evntQ;
                        memset(&evntQ,0,sizeof(csioEventQueueStruct));
                        evntQ.event_type = WFD_SINK_STM_GST_READY_RCVD_EVENT;
                        p->stateFunction(&evntQ);

                        CSIO_LOG(m_debugLevel, "wfdSinkStMachineThread:WFD_SINK_GST_READY_EVENT[%d] processed\n",id);
                    }

                    break;
                }
                //Note: in most cases, only need to pass event to state machine
                case WFD_SINK_GST_LOST_VIDEO_EVENT:
                case WFD_SINK_GST_1ST_FRAME_EVENT:
                {
                    int id = evntQPtr->obj_id;

                    if(wfdSinkStMachineThread::m_wfdSinkStMachineTaskList[id])
                    {
                        wfdSinkStMachineClass* p = wfdSinkStMachineThread::m_wfdSinkStMachineTaskList[id];

                        csioEventQueueStruct evntQ;
                        memset(&evntQ,0,sizeof(csioEventQueueStruct));
                        evntQ.event_type = evntQPtr->event_type;
                        p->stateFunction(&evntQ);

                        CSIO_LOG(m_debugLevel, "wfdSinkStMachineThread:id[%d] event_type[%d] processed\n",id,evntQPtr->event_type);
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
                default:
                {
                    CSIO_LOG(eLogLevel_info, "wfdSinkStMachineThread: unknown type[%d].\n",evntQPtr->event_type);
                    break;
                }

            }//end of switch

            delete evntQPtr;
        }//end of step 1, read from queue

        //step 2. make sure we tick all the state machine.
        if( wfdSinkStMachineThreadTimeArray && wfdSinkStMachineThreadTimeArray->isTimeout(WFD_SINK_STATETHRD_TICKS_TIMER) )
        {
            wfdSinkStMachineThreadTimeArray->setTimeout(WFD_SINK_STATETHRD_TICKS_TIMER,WFD_SINK_STATETHRD_TICK_TO);
            CSIO_LOG(ABOVE_DEBUG_XTRVERB(m_debugLevel), "wfdSinkStMachineThread: tick all state machine\n");

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
            CSIO_LOG(m_debugLevel, "wfdSinkStMachineThread: poll returns[%d].\n", rc);
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
            CSIO_LOG(m_debugLevel, "wfdSinkStMachineThread removePollFds: force is true but sock[%d]",m_fdsMater[id].fd);

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
        evntQ.ext_obj2      = pEvntQ->ext_obj2;
        evntQ.voidPtr       = pEvntQ->voidPtr;
        memcpy(evntQ.reserved,pEvntQ->reserved,sizeof(pEvntQ->reserved));

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
            CSIO_LOG(m_debugLevel,"ERROR: waitWfdSinkStMachineThreadSignal result is %d.",result);
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
        CSIO_LOG(m_debugLevel,"ERROR: waitWfdSinkStMachineThreadSignal m_syncPipe_readfd is NULL.");
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
int wfdSinkStMachineThread::getCurrentTimeInSec()
{
    struct timespec ts_now;
    clock_gettime(CLOCK_MONOTONIC, &ts_now);

    return ts_now.tv_sec ;
}
/***************************** end of wfdSinkStMachineThread class **************************************/
