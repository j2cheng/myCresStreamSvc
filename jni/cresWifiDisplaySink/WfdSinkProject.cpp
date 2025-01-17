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

#include "WfdCommon.h"
#include "WfdSinkProject.h"


// ***
int sssl_Test(int arg);


static wfdSinkProjClass* gWFDSinkProjPtr = NULL;
static Mutex gProjectsLock;
static int gProjectDebug = eLogLevel_debug;
int wfdSinkProjClass::maxMiracastBitrate;

const WFD_STRNUMPAIR wfd_proj_timestamp_names[] =
{
    {"wfd sink project init time     " ,   WFD_SINK_PROJ_TIMESTAMP_INIT},
    {"wfd sink tcp connect start time" ,   WFD_SINK_PROJ_TIMESTAMP_START},
    {"wfd sink tcp connect stop time " ,   WFD_SINK_PROJ_TIMESTAMP_STOP},
    {"wfd sink request idr time      " ,   WFD_SINK_PROJ_TIMESTAMP_REQ_IDR},

    {0,0}//terminate the list
};

std::string g_rtspAudioCodecStr = "" ;
std::string g_rtspVidResRefStr  = "" ;
std::string g_rtspVid2ResRefStr = "" ;

extern void Wfd_setup_gst_pipeline (int id, int state, struct GST_PIPELINE_CONFIG* gst_config);
extern void Wfd_set_latency_by_the_source (int id, int latency);

/*************************** Locals ***********************************************/
static int systemMode2id(std::string systemMode)
{
    int mode = WFD_SINK_SYSTEMMODE_UNDEFINED;

    if(systemMode.compare("Custom") == 0)
        mode = WFD_SINK_SYSTEMMODE_CUSTOM;
    else if(systemMode.compare("OptimizedForVideoQuality") == 0)
        mode = WFD_SINK_SYSTEMMODE_OPTIMIZED_FOR_VIDEO_QUALITY;
    else if(systemMode.compare("OptimizedForMultiplePresentations") == 0)
        mode = WFD_SINK_SYSTEMMODE_OPTIMIZED_FOR_MULTIPLE_PRESENTATIONS;

    return(mode);
}

/*************************** Global functions  ************************************/
void* WfdSinkProjInit()
{
    void* ret = NULL;

    gProjectsLock.lock();

    //1.create static state machine threads first
    if(wfdSinkStMachineClass::m_wfdSinkStMachineThreadPtr == NULL)
    {
        //Note: only one thread for all state machine object
        wfdSinkStMachineClass::m_wfdSinkStMachineThreadPtr = new wfdSinkStMachineThread(gProjectDebug);
        CSIO_LOG(gProjectDebug, "WfdSinkProjInit: m_wfdSinkStMachineThreadPtr:0x%x\n",wfdSinkStMachineClass::m_wfdSinkStMachineThreadPtr);
    }

    //2.create project object now
    if(gWFDSinkProjPtr == NULL)
    {
        gWFDSinkProjPtr = new wfdSinkProjClass();
        CSIO_LOG(gProjectDebug, "WfdSinkProjInit: gWFDSinkProjPtr:0x%x\n",gWFDSinkProjPtr);
    }

    //3.run all threads
    if(gWFDSinkProjPtr && !gWFDSinkProjPtr->m_ThreadIsRunning)
    {
        gWFDSinkProjPtr->CreateNewThread("WFD_SINK_PROJ0",NULL);
    }

    if(wfdSinkStMachineClass::m_wfdSinkStMachineThreadPtr &&
       !wfdSinkStMachineClass::m_wfdSinkStMachineThreadPtr->m_ThreadIsRunning)
    {
        wfdSinkStMachineClass::m_wfdSinkStMachineThreadPtr->CreateNewThread("WFD_ST_THRD0",NULL);
    }

    ret = gWFDSinkProjPtr;

    gProjectsLock.unlock();

    return ret;
}

void WfdSinkProjDeInit()
{
    //1. close SinkStMachineThread first
    if(wfdSinkStMachineClass::m_wfdSinkStMachineThreadPtr)
    {
        wfdSinkStMachineClass::m_wfdSinkStMachineThreadPtr->exitThread();

        //wait until thread exits
        CSIO_LOG(gProjectDebug, "WfdSinkProjDeInit: call WaitForThreadToExit[0x%x]\n",wfdSinkStMachineClass::m_wfdSinkStMachineThreadPtr);
        wfdSinkStMachineClass::m_wfdSinkStMachineThreadPtr->WaitForThreadToExit();
        CSIO_LOG(gProjectDebug, "WfdSinkProjDeInit: Wait is done\n");

        //delete the object, and set list to NULL
        gProjectsLock.lock();

        if(wfdSinkStMachineThread::m_wfdSinkStMachineTaskList)
        {
            for(int i = 0; i < MAX_WFD_TCP_CONN; i++)
            {
                if(wfdSinkStMachineThread::m_wfdSinkStMachineTaskList[i])
                {
                    delete wfdSinkStMachineThread::m_wfdSinkStMachineTaskList[i];
                    wfdSinkStMachineThread::m_wfdSinkStMachineTaskList[i] = NULL;
                    CSIO_LOG(gProjectDebug, "WfdSinkProDeInit: delete m_wfdSinkStMachineTaskList[%d]\n",i);
                }
            }
        }

        delete wfdSinkStMachineClass::m_wfdSinkStMachineThreadPtr;
        wfdSinkStMachineClass::m_wfdSinkStMachineThreadPtr = NULL;

        gProjectsLock.unlock();

        CSIO_LOG(gProjectDebug, "WfdSinkProDeInit: delete m_wfdSinkStMachineThreadPtr is DONE\n");
    }

    //2. close WFDSinkProj at the end
    if(gWFDSinkProjPtr)
    {
        gWFDSinkProjPtr->exitThread();

        //wait until thread exits
        CSIO_LOG(gProjectDebug, "WfdSinkProjDeInit: call WaitForThreadToExit[0x%x]\n",gWFDSinkProjPtr);
        gWFDSinkProjPtr->WaitForThreadToExit();
        CSIO_LOG(gProjectDebug, "WfdSinkProjDeInit: Wait is done\n");

        //delete the object, and set list to NULL
        gProjectsLock.lock();

        delete gWFDSinkProjPtr;
        gWFDSinkProjPtr = NULL;

        gProjectsLock.unlock();

        CSIO_LOG(gProjectDebug, "WfdSinkProDeInit: delete HYDRGNProjObjPtr is DONE\n");
    }
}
void WfdSinkProjSendEvent(int evnt, int iId, int data_size, void* bufP)
{
    gProjectsLock.lock();

    if(gWFDSinkProjPtr)
    {
        CSIO_LOG(gProjectDebug, "WfdSinkProjSendEvent: call sendEvent[%d].\n",evnt);
        csioEventQueueStruct EvntQ;
        memset(&EvntQ,0,sizeof(csioEventQueueStruct));
        EvntQ.obj_id = iId;
        EvntQ.event_type = evnt;
        EvntQ.buf_size   = data_size;
        EvntQ.buffPtr    = bufP;

        gWFDSinkProjPtr->sendEvent(&EvntQ);
        CSIO_LOG(gProjectDebug, "WfdSinkProjSendEvent: sendEvent[%d] done.\n",evnt);
    }
    else
    {
        CSIO_LOG(gProjectDebug, "WfdSinkProjSendEvent: no gWFDSinkProjPtr is running\n");
    }

    gProjectsLock.unlock();
}

void WfdSinkProjStart(int id, const char* url, int src_rtsp_port, int ts_port,bool is_mice_session, bool is_tx3_device, const char *system_mode)
{
    if(!url) return;

    CSIO_LOG(gProjectDebug, "WfdSinkProjStart: enter: id[%d], url[%s], port[%d][%d],is_mice_session[%d], is_tx3_device[%d], system_mode[%s].",
    id,url,src_rtsp_port,ts_port,is_mice_session,is_tx3_device,system_mode);
    gProjectsLock.lock();

    if(gWFDSinkProjPtr)
    {
        csioEventQueueStruct EvntQ;
        memset(&EvntQ,0,sizeof(csioEventQueueStruct));
        EvntQ.obj_id = id;
        EvntQ.event_type  = WFD_SINK_EVENTS_JNI_START;
        EvntQ.buf_size    = strlen(url);
        EvntQ.buffPtr     = (void*)url;
        EvntQ.ext_obj     = src_rtsp_port;
        EvntQ.ext_obj2    = ts_port;
        EvntQ.reserved[0] = is_mice_session;
        EvntQ.reserved[1] = (is_tx3_device) ? 1:0;
        std::string sysmode = std::string(system_mode);
        EvntQ.reserved[2] = systemMode2id(sysmode);
        gWFDSinkProjPtr->sendEvent(&EvntQ);
    }
    else
    {
        CSIO_LOG(gProjectDebug, "WfdSinkProjStart: no gWFDSinkProjPtr is running\n");
    }

    gProjectsLock.unlock();
    CSIO_LOG(gProjectDebug, "WfdSinkProjStart: return.");
}
void WfdSinkProjStop(int id)
{
    CSIO_LOG(gProjectDebug, "WfdSinkProjStop: enter: id[%d].",id);
    gProjectsLock.lock();

    if(gWFDSinkProjPtr)
    {
        csioEventQueueStruct EvntQ;
        memset(&EvntQ,0,sizeof(csioEventQueueStruct));
        EvntQ.obj_id = id;
        EvntQ.event_type = WFD_SINK_EVENTS_JNI_STOP;

        gWFDSinkProjPtr->sendEvent(&EvntQ);
    }
    else
    {
        CSIO_LOG(gProjectDebug, "WfdSinkProjStop: no gWFDSinkProjPtr is running\n");
    }

    gProjectsLock.unlock();
    CSIO_LOG(gProjectDebug, "WfdSinkProjStop: return.");
}

//Note: id is not used here
void WfdSinkProjSetLocalIPAddr(int id, const char* ip_addr)
{
    if(!ip_addr) return;

    CSIO_LOG(gProjectDebug, "WfdSinkProjSetLocalIPAddr: enter: id[%d], ip_addr[%s].",id,ip_addr);
    gProjectsLock.lock();

    if(gWFDSinkProjPtr)
    {
        csioEventQueueStruct EvntQ;
        memset(&EvntQ,0,sizeof(csioEventQueueStruct));
        EvntQ.obj_id = id;
        EvntQ.event_type = WFD_SINK_EVENTS_JNI_CONFIG;
        EvntQ.buf_size   = strlen(ip_addr);
        EvntQ.buffPtr    = (void*)ip_addr;

        gWFDSinkProjPtr->sendEvent(&EvntQ);
    }
    else
    {
        CSIO_LOG(gProjectDebug, "WfdSinkProjSetLocalIPAddr: no gWFDSinkProjPtr is running\n");
    }

    gProjectsLock.unlock();
    CSIO_LOG(gProjectDebug, "WfdSinkProjSetLocalIPAddr: return.");
}
void WfdSinkProjSendIdrReq(int id)
{
    CSIO_LOG(gProjectDebug, "WfdSinkProjSendIdrReq: enter: id[%d].",id);
    gProjectsLock.lock();

    if(gWFDSinkProjPtr)
    {
        csioEventQueueStruct EvntQ;
        memset(&EvntQ,0,sizeof(csioEventQueueStruct));
        EvntQ.obj_id = id;
        EvntQ.event_type = WFD_SINK_EVENTS_JNI_REQ_IDR;

        gWFDSinkProjPtr->sendEvent(&EvntQ);
    }
    else
    {
        CSIO_LOG(gProjectDebug, "WfdSinkProjSendIdrReq: no gWFDSinkProjPtr is running\n");
    }

    gProjectsLock.unlock();
    CSIO_LOG(gProjectDebug, "WfdSinkProjSendIdrReq: return.");
}
void WfdSinkProjSendGstReady(int id)
{
    CSIO_LOG(gProjectDebug, "WfdSinkProjSendGstReady: enter: id[%d].",id);
    gProjectsLock.lock();

    if(gWFDSinkProjPtr)
    {
        csioEventQueueStruct EvntQ;
        memset(&EvntQ,0,sizeof(csioEventQueueStruct));
        EvntQ.obj_id = id;
        EvntQ.event_type = WFD_SINK_EVENTS_JNI_GST_READY;

        gWFDSinkProjPtr->sendEvent(&EvntQ);
    }
    else
    {
        CSIO_LOG(gProjectDebug, "WfdSinkProjSendGstReady: no gWFDSinkProjPtr is running\n");
    }

    gProjectsLock.unlock();
    CSIO_LOG(gProjectDebug, "WfdSinkProjSendGstReady: return.");
}
void WfdSinkProjSendGst1stFrameEvt(int id)
{
    CSIO_LOG(gProjectDebug, "WfdSinkProjSendGst1stFrameEvt: enter: id[%d].",id);
    gProjectsLock.lock();

    if(gWFDSinkProjPtr)
    {
        csioEventQueueStruct EvntQ;
        memset(&EvntQ,0,sizeof(csioEventQueueStruct));
        EvntQ.obj_id = id;
        EvntQ.event_type = WFD_SINK_EVENTS_JNI_1ST_FRAME;

        gWFDSinkProjPtr->sendEvent(&EvntQ);
    }
    else
    {
        CSIO_LOG(gProjectDebug, "WfdSinkProjSendGst1stFrameEvt: no gWFDSinkProjPtr is running\n");
    }

    gProjectsLock.unlock();
    CSIO_LOG(gProjectDebug, "WfdSinkProjSendGst1stFrameEvt: return.");
}
void WfdSinkProjSendGstLostVideoEvt(int id)
{
    CSIO_LOG(gProjectDebug, "WfdSinkProjSendGstLostVideoEvt: enter: id[%d].",id);
    gProjectsLock.lock();

    if(gWFDSinkProjPtr)
    {
        csioEventQueueStruct EvntQ;
        memset(&EvntQ,0,sizeof(csioEventQueueStruct));
        EvntQ.obj_id = id;
        EvntQ.event_type = WFD_SINK_EVENTS_JNI_LOST_VIDEO;

        gWFDSinkProjPtr->sendEvent(&EvntQ);
    }
    else
    {
        CSIO_LOG(gProjectDebug, "WfdSinkProjSendGstLostVideoEvt: no gWFDSinkProjPtr is running\n");
    }

    gProjectsLock.unlock();
    CSIO_LOG(gProjectDebug, "WfdSinkProjSendGstLostVideoEvt: return.");
}
void WfdSinkProjSetMaxMiracastBitrate(int maxrate)
{
    CSIO_LOG(gProjectDebug, "WfdSinkProjSetMaxMiracastBitrate: enter: maxrate[%d].",maxrate);
    gProjectsLock.lock();

    wfdSinkProjClass::setMaxMiracastBitrate(maxrate);

    gProjectsLock.unlock();
    CSIO_LOG(gProjectDebug, "WfdSinkProjSetMaxMiracastBitrate: return.");
}
//Note: used only for debugging, there is no lock here.
void WfdSinkProjSetDebugLevel(int l)
{
    //No lock here
    gProjectDebug = l;

    if(gWFDSinkProjPtr)
    {
        gWFDSinkProjPtr->setDebugLevel(l);
    }

    if(wfdSinkStMachineClass::m_wfdSinkStMachineThreadPtr)
    {
        wfdSinkStMachineClass::m_wfdSinkStMachineThreadPtr->setDebugLevel(l);
    }

    //since there is no lock, it might crash.
    if(wfdSinkStMachineThread::m_wfdSinkStMachineTaskList)
    {
        for(int i = 0; i < MAX_WFD_TCP_CONN; i++)
        {
            if(wfdSinkStMachineThread::m_wfdSinkStMachineTaskList[i])
                wfdSinkStMachineThread::m_wfdSinkStMachineTaskList[i]->setDebugLevel(l);
        }
    }

    //No lock here
}

void WfdSinkProDumpPara()
{
    gProjectsLock.lock();

    CSIO_LOG(eLogLevel_info, "WfdSinkProDump  : gProjectDebug:      %d\n",gProjectDebug);
    if(gWFDSinkProjPtr)
    {
        gWFDSinkProjPtr->DumpClassPara(0);
    }
    else
    {
        CSIO_LOG(eLogLevel_info, "WfdSinkProDumpPara: no gWFDSinkProjPtr is running\n");
    }

    if(wfdSinkStMachineClass::m_wfdSinkStMachineThreadPtr)
        wfdSinkStMachineClass::m_wfdSinkStMachineThreadPtr->DumpClassPara(0);

    gProjectsLock.unlock();
}

/********** WfdSinkProject class, used by jni.cpp *******************/
wfdSinkProjClass::wfdSinkProjClass():
wfdSinkProjTimeArray(NULL),
m_wfdSinkStMachineTaskObjList(wfdSinkStMachineThread::m_wfdSinkStMachineTaskList),
m_projEventQList(NULL)
{
    m_debugLevel = gProjectDebug;

    m_projEventQList = new csioEventQueueListBase(CSIO_DEFAULT_QUEUE_SIZE);

    wfdSinkProjTimeArray = new csioTimerClockBase(WFD_SINK_PROJ_TIMESTAMP_MAX,WFD_SINK_PROJ_TIMER_MAX);

    if(wfdSinkProjTimeArray)
        wfdSinkProjTimeArray->recordEventTimeStamp(WFD_SINK_PROJ_TIMESTAMP_INIT);

    for(int i = 0; i < MAX_WFD_TCP_CONN; i++)
    {
        localIPName[i][0] = 0;
    }

    maxMiracastBitrate = DEFAULT_MAX_MIRACAST_BITRATE;

    CSIO_LOG(ABOVE_DEBUG_VERB(m_debugLevel), "wfdSinkProjClass: creating WfdSinkProject.\n");
}
wfdSinkProjClass::~wfdSinkProjClass()
{
    CSIO_LOG(m_debugLevel, "WfdSinkProject: ~WfdSinkProject.\n");

    if(m_projEventQList)
    {
        //get list counts
        int cnt = m_projEventQList->GetEvntQueueCount();

        //remove lists
        if(cnt > 0)
        {
            //loop through all contents of the list
            for(int i = 0; i < cnt; i++)
            {
                csioEventQueueStruct* evntQPtr = NULL;
                if(m_projEventQList->GetFromQueueList(&evntQPtr) && evntQPtr)
                {
                    if( evntQPtr->buf_size && evntQPtr->buffPtr)
                    {
                        deleteCharArray(evntQPtr->buffPtr);
                    }

                    //delete event queue
                    delete evntQPtr;
                }
            }
        }

        delete m_projEventQList;
        m_projEventQList = NULL;
    }

    if(wfdSinkProjTimeArray)
    {
        delete wfdSinkProjTimeArray;
        wfdSinkProjTimeArray = NULL;
    }
}

void wfdSinkProjClass::DumpClassPara(int id)
{
    CSIO_LOG(eLogLevel_info, "wfdSinkProjClass: threadObjLoopCnt:   %d.\n",m_threadObjLoopCnt);

    CSIO_LOG(eLogLevel_info, "wfdSinkProjClass: ThreadId            0x%x\n", (unsigned long int)getThredId());
    CSIO_LOG(eLogLevel_info, "wfdSinkProjClass: m_debugLevel        %d\n", m_debugLevel);
    CSIO_LOG(eLogLevel_info, "wfdSinkProjClass: m_ThreadIsRunning   %d\n", m_ThreadIsRunning);
    CSIO_LOG(eLogLevel_info, "wfdSinkProjClass: g_rtspAudioCodecStr:%s\n", g_rtspAudioCodecStr.c_str());
    CSIO_LOG(eLogLevel_info, "wfdSinkProjClass: g_rtspVidResRefStr :%s\n", g_rtspVidResRefStr.c_str());
    CSIO_LOG(eLogLevel_info, "wfdSinkProjClass: g_rtspVid2ResRefStr:%s\n", g_rtspVid2ResRefStr.c_str());

    if(m_projEventQList)
    {
        CSIO_LOG(eLogLevel_info, "wfdSinkProjClass: Event queued up     %d\n", m_projEventQList->GetEvntQueueCount());
    }

    if(wfdSinkProjTimeArray)
    {
        char  time_string[40];
        long  milliseconds;

        for(int i = 0; i < WFD_SINK_PROJ_TIMESTAMP_MAX; i++)
        {
            wfdSinkProjTimeArray->convertTime(i,time_string,40,milliseconds);
            CSIO_LOG(eLogLevel_info, "wfdSinkProjClass: %s:  %s.%03ld\n",
                     wfd_proj_timestamp_names[i].pStr, time_string, milliseconds);
        }
    }
}

void wfdSinkProjClass::exitThread()
{
    m_forceThreadExit = 1;

    csioEventQueueStruct evntQ;
    memset(&evntQ,0,sizeof(csioEventQueueStruct));
    sendEvent(&evntQ);

    CSIO_LOG(ABOVE_DEBUG_VERB(m_debugLevel), "wfdSinkProjClass: exitThread[%d] sent.\n", m_forceThreadExit);
}

void wfdSinkProjClass::sendEvent(csioEventQueueStruct* pEvntQ)
{
    if(pEvntQ)
    {
        csioEventQueueStruct evntQ;
        memset(&evntQ,0,sizeof(csioEventQueueStruct));
        evntQ.obj_id  = pEvntQ->obj_id;
        evntQ.event_type    = pEvntQ->event_type;
        evntQ.buf_size      = 0;
        evntQ.buffPtr       = NULL;
        evntQ.ext_obj       = pEvntQ->ext_obj;
        evntQ.ext_obj2      = pEvntQ->ext_obj2;
        evntQ.voidPtr       = pEvntQ->voidPtr;
        memcpy(evntQ.reserved,pEvntQ->reserved,sizeof(pEvntQ->reserved));
        CSIO_LOG((m_debugLevel), "wfdSinkProjClass: evntQ.reserved[0]=%d,pEvntQ->reserved[0]=%d.\n", evntQ.reserved[0],pEvntQ->reserved[0]);

        void* bufP = pEvntQ->buffPtr;
        int dataSize = pEvntQ->buf_size;

        CSIO_LOG(ABOVE_DEBUG_VERB(m_debugLevel), "wfdSinkProjClass::sendEvent: iId[%d],evnt[%d],dataSize[%d],bufP[0x%x]\n",\
                pEvntQ->obj_id,pEvntQ->event_type,pEvntQ->buf_size,pEvntQ->buffPtr);

        if(bufP && dataSize)
        {
            switch (evntQ.event_type)
            {
                case WFD_SINK_EVENTS_RTSP_IN_SESSION_EVENT:
                {
                    GST_PIPELINE_CONFIG* new_config = new GST_PIPELINE_CONFIG();
                    GST_PIPELINE_CONFIG* gst_config = (GST_PIPELINE_CONFIG*)bufP;
                    if(gst_config)
                    {
                        new_config->isTx3 = gst_config->isTx3;
                        new_config->useTcp = gst_config->useTcp;
                        new_config->ts_port = gst_config->ts_port;
                        new_config->ssrc    = gst_config->ssrc;
                        new_config->rtcp_dest_port = gst_config->rtcp_dest_port;
                        new_config->pSrcVersionStr = NULL;

                        if(gst_config->pSrcVersionStr)
                        {
                            int strSize = strlen(gst_config->pSrcVersionStr);
                            char* tmp = (char*)createCharArray(strSize + 1);
                            if(tmp)
                            {
                                memcpy(tmp,gst_config->pSrcVersionStr,strSize);
                                tmp[strSize] = 0;
                                new_config->pSrcVersionStr = tmp;
                            }
                        }

                        evntQ.buffPtr = (void*)new_config;
                        evntQ.buf_size = sizeof(GST_PIPELINE_CONFIG);
                    }
                    break;
                }
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
                        CSIO_LOG(eLogLevel_error, "wfdSinkProjClass::sendEvent: create buffer failed\n");
                    }

                    break;
                }
            }
        }
        //else

        if(m_projEventQList)
            m_projEventQList->EnqueueAndSignal(evntQ);

        CSIO_LOG(ABOVE_DEBUG_VERB(m_debugLevel), "wfdSinkProjClass::sendEvent[%d]: event added to the queue[0x%x].\n",evntQ.obj_id,evntQ.buffPtr);
        CSIO_LOG(ABOVE_DEBUG_VERB(m_debugLevel), "wfdSinkProjClass::sendEvent[%d]: event[%d] sent.\n",evntQ.obj_id,evntQ.event_type);
    }
    else
    {
        CSIO_LOG(eLogLevel_info, "wfdSinkProjClass::sendEvent: pEvntQ is NULL\n");
    }
}

void* wfdSinkProjClass::ThreadEntry()
{
    CSIO_LOG(ABOVE_DEBUG_VERB(m_debugLevel), "wfdSinkProjClass: Enter ThreadEntry.\n");

    int wtRtn  = 0;
    csioEventQueueStruct* evntQPtr = NULL;

    //log thread init. time stamp
    if(wfdSinkProjTimeArray)
        wfdSinkProjTimeArray->recordEventTimeStamp(WFD_SINK_PROJ_TIMESTAMP_INIT);

    if(!m_projEventQList)
    {
        CSIO_LOG(eLogLevel_error, "wfdSinkProjClass::m_projEventQList is NULL!\n");
        return NULL;
    }

    for(;;)
    {
        m_threadObjLoopCnt++;

        wtRtn  = m_projEventQList->waitMsgQueueSignal(WFD_PROJ_EVNT_POLL_SLEEP_MS);

        CSIO_LOG(ABOVE_DEBUG_XTRVERB(m_debugLevel), "wfdSinkProjClass: waitMsgQueueSignal return:%d, m_threadObjLoopCnt[%d]\n",wtRtn,m_threadObjLoopCnt);

        evntQPtr = NULL;

        if(m_projEventQList->GetFromQueueList(&evntQPtr) && evntQPtr)
        {
            CSIO_LOG(ABOVE_DEBUG_VERB(m_debugLevel), "wfdSinkProjClass: evntQ is:size[%d],type[%d],iId[%d],GetEvntQueueCount[%d]\n",\
                            evntQPtr->buf_size,evntQPtr->event_type,evntQPtr->obj_id,m_projEventQList->GetEvntQueueCount());

            switch (evntQPtr->event_type)
            {
                case WFD_SINK_EVENTS_JNI_START:
                {
                    int id = evntQPtr->obj_id;
                    CSIO_LOG(m_debugLevel, "wfdSinkProjClass: processing WFD_SINK_EVENTS_JNI_START[%d].\n",id);

                    if( !IsValidStreamWindow(id))
                    {
                        CSIO_LOG(eLogLevel_info, "wfdSinkProjClass: WFD_SINK_EVENTS_JNI_START obj ID is invalid = %d",id);
                    }
                    else if( evntQPtr->buf_size && evntQPtr->buffPtr)
                    {
                        //lock here, m_wfdSinkStMachineTaskObjList[] will be changed
                        gProjectsLock.lock();

                        //if the same object id is active
                        if(m_wfdSinkStMachineTaskObjList && m_wfdSinkStMachineTaskObjList[id])
                        {
                            if(wfdSinkStMachineClass::m_wfdSinkStMachineThreadPtr)
                            {
                                csioEventQueueStruct EvntQ;

                                //send tcp connection command with new url and port
                                memset(&EvntQ,0,sizeof(csioEventQueueStruct));
                                EvntQ.obj_id = id;
                                EvntQ.event_type = WFD_SINK_START_STMACHINE_EVENT;
                                EvntQ.buf_size   = evntQPtr->buf_size;
                                EvntQ.buffPtr    = evntQPtr->buffPtr;
                                EvntQ.ext_obj    = evntQPtr->ext_obj;
                                EvntQ.ext_obj2   = evntQPtr->ext_obj2;
                                EvntQ.reserved[0]   = evntQPtr->reserved[0];
                                EvntQ.reserved[1]   = evntQPtr->reserved[1];
                                EvntQ.reserved[2]   = evntQPtr->reserved[2];
                                wfdSinkStMachineClass::m_wfdSinkStMachineThreadPtr->sendEvent(&EvntQ);

                                CSIO_LOG(m_debugLevel, "wfdSinkProjClass: EvntQ.reserved-0[%d], EvntQ.reserved-1[%d], EvntQ.reserved-2[%d].\n",EvntQ.reserved[0],EvntQ.reserved[1],EvntQ.reserved[2]);
                            }//else

                            CSIO_LOG(m_debugLevel, "wfdSinkProjClass: process with existing wfdSinkStMachineClass object is done.\n");
                        }
                        else
                        {
                            //create new manager task object
                            wfdSinkStMachineClass* p = new wfdSinkStMachineClass(id,this);
                            CSIO_LOG(m_debugLevel, "wfdSinkProjClass: wfdSinkStMachineClass[0x%x] created.\n",p);

                            if(p)
                            {
                                p->setDebugLevel(m_debugLevel);

                                //Note: m_wfdSinkStMachineThreadPtr will insert this object to the list,
                                if(wfdSinkStMachineClass::m_wfdSinkStMachineThreadPtr)
                                {
                                    csioEventQueueStruct EvntQ;
                                    memset(&EvntQ,0,sizeof(csioEventQueueStruct));
                                    EvntQ.obj_id = id;
                                    EvntQ.event_type = WFD_SINK_INSERT_STMACHINE_EVENT;
                                    EvntQ.buf_size   = evntQPtr->buf_size;
                                    EvntQ.buffPtr    = evntQPtr->buffPtr;
                                    EvntQ.ext_obj    = evntQPtr->ext_obj;
                                    EvntQ.ext_obj2   = evntQPtr->ext_obj2;
                                    EvntQ.voidPtr    = (void*)(p);
                                    EvntQ.reserved[0]   = evntQPtr->reserved[0];
                                    EvntQ.reserved[1]   = evntQPtr->reserved[1];
                                    EvntQ.reserved[2]   = evntQPtr->reserved[2];

                                    CSIO_LOG(m_debugLevel, "wfdSinkProjClass: EvntQ.reserved-0[%d], EvntQ.reserved-1[%d], EvntQ.reserved-2[%d].\n",EvntQ.reserved[0],EvntQ.reserved[1],EvntQ.reserved[2]);

                                    wfdSinkStMachineClass::m_wfdSinkStMachineThreadPtr->sendEvent(&EvntQ);
                                }//else

                                //Note: waitWfdSinkStMachineSignal will wait for m_wfdSinkStMachineThreadPtr to signal
                                CSIO_LOG(m_debugLevel, "wfdSinkProjClass: call waitWfdSinkStMachineSignal[%d]\n",id);
                                int retNum = p->waitWfdSinkStMachineSignal(10000);//timeout 10s
                                if( retNum == WFD_SINK_SINGAL_WAIT_TIMEOUT ||
                                    retNum == WFD_SINK_SINGAL_WAIT_ERROR)
                                {
                                    CSIO_LOG(m_debugLevel, "wfdSinkProjClass: ERROR insertwfdSinkStMachineObj[%d] failed!!!\n",id);
                                    //Note: I don't think this will happen, but if so, we need to delete object
                                    delete p;
                                    p = NULL;
                                }
                                else//state machine is running fine
                                {
                                    CSIO_LOG(m_debugLevel, "wfdSinkProjClass: waitWfdSinkStMachineSignal[0x%x] returns without error.\n",p);
                                }

                                CSIO_LOG(m_debugLevel, "wfdSinkProjClass: process new wfdSinkStMachineClass object is done.\n");
                            }
                        }

                        gProjectsLock.unlock();

                        //log start time stamp
                        if(wfdSinkProjTimeArray)
                            wfdSinkProjTimeArray->recordEventTimeStamp(WFD_SINK_PROJ_TIMESTAMP_START);
                    }
                    else
                    {
                        CSIO_LOG(eLogLevel_info, "wfdSinkProjClass: did not get url[%d]",id);
                    }

                    if( evntQPtr->buf_size && evntQPtr->buffPtr)
                    {
                        deleteCharArray(evntQPtr->buffPtr);
                    }
                    break;
                }
                case WFD_SINK_EVENTS_JNI_STOP:
                {
                    int id = evntQPtr->obj_id;

                    if( !IsValidStreamWindow(id))
                    {
                        CSIO_LOG(eLogLevel_info, "wfdSinkProjClass: WFD_SINK_EVENTS_JNI_STOP obj ID is invalid = %d",id);
                    }
                    //send tear down command only, this will keep object running in idle
                    else if(wfdSinkStMachineClass::m_wfdSinkStMachineThreadPtr)
                    {
                        csioEventQueueStruct EvntQ;

                        memset(&EvntQ,0,sizeof(csioEventQueueStruct));
                        EvntQ.obj_id = id;
                        EvntQ.event_type = WFD_SINK_TEARDOWN_TCP_CONN_EVENT;
                        wfdSinkStMachineClass::m_wfdSinkStMachineThreadPtr->sendEvent(&EvntQ);

                        //log stop time stamp
                        if(wfdSinkProjTimeArray)
                            wfdSinkProjTimeArray->recordEventTimeStamp(WFD_SINK_PROJ_TIMESTAMP_STOP);
                    }//else

                    CSIO_LOG(m_debugLevel, "wfdSinkProjClass: processing WFD_SINK_EVENTS_JNI_STOP[%d].\n",id);
                    break;
                }
                case WFD_SINK_EVENTS_JNI_REQ_IDR:
                {
                    int id = evntQPtr->obj_id;
                    CSIO_LOG(m_debugLevel, "wfdSinkProjClass: process WFD_SINK_EVENTS_JNI_REQ_IDR[%d].\n",id);

                    if( !IsValidStreamWindow(id))
                    {
                        CSIO_LOG(eLogLevel_info, "wfdSinkProjClass: WFD_SINK_EVENTS_JNI_STOP obj ID is invalid = %d",id);
                    }
                    else
                    {
                        csioEventQueueStruct EvntQ;

                        memset(&EvntQ,0,sizeof(csioEventQueueStruct));
                        EvntQ.obj_id = id;
                        EvntQ.event_type = WFD_SINK_SEND_IDR_REQ_EVENT;
                        wfdSinkStMachineClass::m_wfdSinkStMachineThreadPtr->sendEvent(&EvntQ);

                        if(wfdSinkProjTimeArray)
                            wfdSinkProjTimeArray->recordEventTimeStamp(WFD_SINK_PROJ_TIMESTAMP_REQ_IDR);
                    }

                    break;
                }
                case WFD_SINK_EVENTS_RTSP_IN_SESSION_EVENT:
                {
                    int id = evntQPtr->obj_id;
                    CSIO_LOG(m_debugLevel, "wfdSinkProjClass: process WFD_SINK_EVENTS_RTSP_IN_SESSION_EVENT[%d].\n",id);

                    if( evntQPtr->buf_size && evntQPtr->buffPtr)
                    {
                        struct GST_PIPELINE_CONFIG* gst_config = (struct GST_PIPELINE_CONFIG*)evntQPtr->buffPtr;
                        Wfd_setup_gst_pipeline(id, 1,gst_config);

                        //to delete gst_config->pSrcVersionStr first 
                        if(gst_config->pSrcVersionStr)
                            deleteCharArray((void*)gst_config->pSrcVersionStr);

                        //now delete gst_config 
                        delete gst_config;
                    }
                    break;
                }
                case WFD_SINK_EVENTS_RTSP_LEAVE_SESSION_EVENT:
                {
                    int id = evntQPtr->obj_id;
                    CSIO_LOG(m_debugLevel, "wfdSinkProjClass: process WFD_SINK_EVENTS_RTSP_LEAVE_SESSION_EVENT[%d].\n",id);

                    Wfd_setup_gst_pipeline(id, 0,NULL);
                    break;
                }
                case WFD_SINK_EVENTS_RTSP_SET_LATENCY_EVENT:
                {
                    int id = evntQPtr->obj_id;
                    int latency = evntQPtr->ext_obj;
                    CSIO_LOG(m_debugLevel, "wfdSinkProjClass: process WFD_SINK_EVENTS_RTSP_SET_LATENCY_EVENT[%d]: latency:%d.\n",id,latency);

                    Wfd_set_latency_by_the_source(id, latency);
                    break;
                }
                case WFD_SINK_EVENTS_RTSP_SWITCH_TRANSPORT_MODE_EVENT:
                {
                    int id = evntQPtr->obj_id;
                    int transport_mode = evntQPtr->ext_obj;
                    CSIO_LOG(m_debugLevel, "wfdSinkProjClass: process WFD_SINK_EVENTS_RTSP_SWITCH_TRANSPORT_MODE_EVENT[%d]: mode:%d.\n",id,transport_mode);

                    //Wfd_set_transport_mode_by_the_source(id, tcp_mode);
                    break;
                }
                case WFD_SINK_EVENTS_JNI_GST_READY:
                {
                    int id = evntQPtr->obj_id;
                    CSIO_LOG(m_debugLevel, "wfdSinkProjClass: process WFD_SINK_EVENTS_JNI_GST_READY[%d].\n",id);

                    if( !IsValidStreamWindow(id))
                    {
                        CSIO_LOG(eLogLevel_info, "wfdSinkProjClass: WFD_SINK_EVENTS_JNI_GST_READY obj ID is invalid = %d",id);
                    }
                    else
                    {
                        csioEventQueueStruct EvntQ;

                        memset(&EvntQ,0,sizeof(csioEventQueueStruct));
                        EvntQ.obj_id = id;
                        EvntQ.event_type = WFD_SINK_GST_READY_EVENT;
                        wfdSinkStMachineClass::m_wfdSinkStMachineThreadPtr->sendEvent(&EvntQ);
                    }
                    break;
                }
                case WFD_SINK_EVENTS_JNI_CONFIG:
                {
                    int id = evntQPtr->obj_id;
                    CSIO_LOG(m_debugLevel, "wfdSinkProjClass: process WFD_SINK_EVENTS_JNI_CONFIG[%d].\n",id);

                    if( !IsValidStreamWindow(id))
                    {
                        CSIO_LOG(m_debugLevel, "wfdSinkProjClass: WFD_SINK_EVENTS_JNI_CONFIG obj ID is invalid = %d",id);
                    }
                    else
                    {
                        if( evntQPtr->buf_size && evntQPtr->buffPtr)
                        {
                            if(evntQPtr->buf_size < sizeof(localIPName[id]))
                            {
                            	memset(localIPName[id],0,sizeof(localIPName[id]));
                            	memcpy(localIPName[id],evntQPtr->buffPtr,evntQPtr->buf_size);

                                CSIO_LOG(m_debugLevel, "wfdSinkProjClass: WFD_SINK_EVENTS_JNI_CONFIG local ip is %s",localIPName[id]);
                            }
                            else
                            {
                                CSIO_LOG(m_debugLevel, "wfdSinkProjClass: WFD_SINK_EVENTS_JNI_CONFIG local ip is too long");
                            }
                        }//else
                    }

                    if( evntQPtr->buf_size && evntQPtr->buffPtr)
                        deleteCharArray(evntQPtr->buffPtr);

                    break;
                }
                case WFD_SINK_EVENTS_JNI_1ST_FRAME:
                {
                    int id = evntQPtr->obj_id;
                    CSIO_LOG(m_debugLevel, "wfdSinkProjClass: process WFD_SINK_EVENTS_JNI_1ST_FRAME[%d].\n",id);

                    if( !IsValidStreamWindow(id))
                    {
                        CSIO_LOG(eLogLevel_info, "wfdSinkProjClass: WFD_SINK_EVENTS_JNI_1ST_FRAME obj ID is invalid = %d",id);
                    }
                    else
                    {
                        csioEventQueueStruct EvntQ;

                        memset(&EvntQ,0,sizeof(csioEventQueueStruct));
                        EvntQ.obj_id = id;
                        EvntQ.event_type = WFD_SINK_GST_1ST_FRAME_EVENT;
                        wfdSinkStMachineClass::m_wfdSinkStMachineThreadPtr->sendEvent(&EvntQ);
                    }
                    break;
                }
                case WFD_SINK_EVENTS_JNI_LOST_VIDEO:
                {
                    int id = evntQPtr->obj_id;
                    CSIO_LOG(m_debugLevel, "wfdSinkProjClass: process WFD_SINK_EVENTS_JNI_LOST_VIDEO[%d].\n",id);

                    if( !IsValidStreamWindow(id))
                    {
                        CSIO_LOG(eLogLevel_info, "wfdSinkProjClass: WFD_SINK_EVENTS_JNI_LOST_VIDEO obj ID is invalid = %d",id);
                    }
                    else
                    {
                        csioEventQueueStruct EvntQ;

                        memset(&EvntQ,0,sizeof(csioEventQueueStruct));
                        EvntQ.obj_id = id;
                        EvntQ.event_type = WFD_SINK_GST_LOST_VIDEO_EVENT;
                        wfdSinkStMachineClass::m_wfdSinkStMachineThreadPtr->sendEvent(&EvntQ);
                    }
                    break;
                }
                default:
                {
                    CSIO_LOG(eLogLevel_info, "wfdSinkProjClass: unknown type[%d].\n",evntQPtr->event_type);
                    break;
                }
            }

            delete evntQPtr;
        }

        if(m_forceThreadExit)
        {
            //TODO: exit all child thread and wait here
            break;
        }
    }

    CSIO_LOG(m_debugLevel, "wfdSinkProjClass: exiting...\n");

    //thread exit here
    m_ThreadIsRunning = 0;

    return NULL;
}

bool wfdSinkProjClass::updateIfMaxBitrateOverride(int minrate,int maxrate)
{
    CSIO_LOG(eLogLevel_verbose, "updateIfMaxBitrateOverride: enter: minrate[%d], maxrate[%d].",minrate, maxrate);

    bool updated = false;
    FILE *fbitrateOverride = fopen("/dev/shm/crestron/CresStreamSvc/bitrateoverride", "r");
    // Check if user set MiracastBitrate override value
    if (fbitrateOverride != NULL)
    {
        int bitrateOverride = maxrate;
        fscanf(fbitrateOverride, "%d", &bitrateOverride);
        if(bitrateOverride < minrate)
            bitrateOverride = minrate;
        else if(bitrateOverride > maxrate)
            bitrateOverride = maxrate;
        CSIO_LOG(eLogLevel_debug, "updateIfMaxBitrateOverride: max miracast bitrate override = %d\n", bitrateOverride);
        setMaxMiracastBitrate(bitrateOverride);
        updated = true;
        fclose(fbitrateOverride);
    }

    return(updated);
}
/********** end of WfdSinkProject class, used by jni.cpp *******************/

/********** WfdSinkProjDebug API, used by jni.cpp ************************/
const char * const WfdSinkProj_fieldDebugNames[] =
{
    "01 START           " ,
    "02 STOP            " ,
    "03 SETDEBUGLEVEL   " ,
    "04 DUMPALL         ",
    "05 IDRREQUEST      "
};

void WfdSinkProj_printFdebugInfo()
{
    unsigned int i;

    CSIO_LOG(eLogLevel_info, "WfdSinkProj_debug: FieldDebugInfo:\n");
    for (i = 0; i < WFD_NUM_OF(WfdSinkProj_fieldDebugNames); i++)
    {
        CSIO_LOG(eLogLevel_info, "%s\n", WfdSinkProj_fieldDebugNames[i]);
    }
}
void WfdSinkProj_fdebug(char *cmd_cstring)
{
    char *EndPtr,*CmdPtr;
    char origCmdString[1024];

    memset(origCmdString,0,sizeof(origCmdString));
    memcpy(origCmdString,cmd_cstring,sizeof(origCmdString)-1);//keep the last byte as '\0'

    if(cmd_cstring)
        CSIO_LOG(eLogLevel_info, "WfdSinkProj_fdebug: cmd_cstring: %s\n",cmd_cstring);
    else
        CSIO_LOG(eLogLevel_info, "WfdSinkProj_fdebug: got null pointer\n");

    if ( cmd_cstring == NULL )
    {
        WfdSinkProj_printFdebugInfo();
    }
    else
    {
        CmdPtr = strtok(cmd_cstring,", "); //this should be Wfd_FDEBUG
        CmdPtr = strtok(NULL, ", ");
        CSIO_LOG(eLogLevel_info, "WfdSinkProj_fdebug: CmdPtr: %s\n",CmdPtr);

        if (CmdPtr == NULL || *CmdPtr == '\0' || *CmdPtr == '?' || strcasestr(CmdPtr, "HELP"))
        {
            WfdSinkProj_printFdebugInfo();
        }
        else if(strcasestr(CmdPtr, "PROJINIT"))
        {
            WfdSinkProjInit();
        }
        else if(strcasestr(CmdPtr, "PROJDEINIT"))
        {
            WfdSinkProjDeInit();
        }
        else if(strcasestr(CmdPtr, "DUMPALL"))
        {
            WfdSinkProDumpPara();
        }
        else if(strcasestr(CmdPtr, "SETDEBUGLEVEL"))
        {
            CmdPtr = strtok(NULL, ", ");
            if(CmdPtr)
            {
                int level = strtol(CmdPtr, &EndPtr, 10);
                CSIO_LOG(eLogLevel_info, "Set debug level to %d\n", level);
                WfdSinkProjSetDebugLevel(level);
            }
        }
        else if(strcasestr(CmdPtr, "START"))
        {
            CmdPtr = strtok(NULL, ", ");
            if (CmdPtr == NULL)
            {
                CSIO_LOG(eLogLevel_info, "START missing source address");
            }
            else
            {
                CSIO_LOG(eLogLevel_info, "START source address: %s",CmdPtr);

                std::string addr = CmdPtr;

                CmdPtr = strtok(NULL, ", ");
                if (CmdPtr == NULL)
                {
                    CSIO_LOG(eLogLevel_info, "START missing source port");
                }
                else
                {
                    int port = (int) strtol(CmdPtr, &EndPtr, 10);

                    CSIO_LOG(eLogLevel_info, "START source addr[%s], port[%d]",addr.c_str(),port);
                    WfdSinkProjStart(0,addr.c_str(),port,4570,1,false,0); //TODO: 1) change isTx3 = false to an argument 2) lookup systemMode
                }
            }
        }
        else if(strcasestr(CmdPtr, "Stop"))
        {
            CmdPtr = strtok(NULL, ", ");
            if(CmdPtr)
            {
                int id = strtol(CmdPtr, &EndPtr, 10);
                WfdSinkProjStop(id);
            }
            else
            {
                WfdSinkProjStop(0);
            }
        }
        else if(strcasestr(CmdPtr, "IDRREQUEST"))
        {
            CmdPtr = strtok(NULL, ", ");
            if(CmdPtr)
            {
                int id = strtol(CmdPtr, &EndPtr, 10);
                WfdSinkProjSendIdrReq(id);
            }
            else
            {
                WfdSinkProjSendIdrReq(0);
            }
        }
        else if(strcasestr(CmdPtr, "SETAUDIOCODECSTR"))
		{
			CmdPtr = strtok(NULL, ", ");
			if(CmdPtr)
			{
				g_rtspAudioCodecStr = CmdPtr;

			}
			else
			{
				g_rtspAudioCodecStr = "";
			}

			CSIO_LOG(eLogLevel_info, "WfdSinkProj_debug: g_rtspAudioCodecStr = [%s]\n",g_rtspAudioCodecStr.c_str());

			//debug only, No lock here
			if(wfdSinkStMachineThread::m_wfdSinkStMachineTaskList)
			{
				for(int i = 0; i < MAX_WFD_TCP_CONN; i++)
				{
					if(wfdSinkStMachineThread::m_wfdSinkStMachineTaskList[i])
					{
						if(g_rtspAudioCodecStr.size())
						{
							wfdSinkStMachineThread::m_wfdSinkStMachineTaskList[i]->m_rtspParserIntfInfo.preferredAudioCodecStr = (char*)g_rtspAudioCodecStr.c_str();
						}
						else
						{
							wfdSinkStMachineThread::m_wfdSinkStMachineTaskList[i]->m_rtspParserIntfInfo.preferredAudioCodecStr = "AACx48x2";
						}
					}
				}
			}
		}
        else if(strcasestr(CmdPtr, "SETVIDEORESSTR"))
		{
			CmdPtr = strtok(NULL, ", ");
			if(CmdPtr)
			{

				g_rtspVidResRefStr = CmdPtr;
			}
			else
			{
				g_rtspVidResRefStr = "";
			}

			CSIO_LOG(eLogLevel_info, "WfdSinkProj_debug: g_rtspVidResRefStr = [%s]\n",g_rtspVidResRefStr.c_str());

			//debug only, No lock here
			if(wfdSinkStMachineThread::m_wfdSinkStMachineTaskList)
			{
				for(int i = 0; i < MAX_WFD_TCP_CONN; i++)
				{
					if(wfdSinkStMachineThread::m_wfdSinkStMachineTaskList[i])
					{
						if(g_rtspVidResRefStr.size())
						{
							wfdSinkStMachineThread::m_wfdSinkStMachineTaskList[i]->m_rtspParserIntfInfo.preferredVidResRefStr  = (char*)g_rtspVidResRefStr.c_str() ;
						}
						else
						{
							wfdSinkStMachineThread::m_wfdSinkStMachineTaskList[i]->m_rtspParserIntfInfo.preferredVidResRefStr  = "upto_1920x1080p24_noninterlaced;upto_1920x1200p30;upto_848x480p60";
						}
					}
				}
			}
		}
        else if (strcasestr(CmdPtr, "SETVIDEO2RESSTR"))
        {
            CmdPtr = strtok(NULL, ", ");
            if (CmdPtr)
            {
                char *message = strstr(origCmdString, "SETVIDEO2RESSTR");
                g_rtspVid2ResRefStr = message + sizeof("SETVIDEO2RESSTR");
                CSIO_LOG(eLogLevel_info, "WfdSinkProj_debug: message = [%s]\n", message);
            }
            else
            {
                g_rtspVid2ResRefStr = "";
            }

            CSIO_LOG(eLogLevel_info, "WfdSinkProj_debug: g_rtspVid2ResRefStr = [%s]\n", g_rtspVid2ResRefStr.c_str());

            //debug only, No lock here
            if (wfdSinkStMachineThread::m_wfdSinkStMachineTaskList)
            {
                for (int i = 0; i < MAX_WFD_TCP_CONN; i++)
                {
                    if (wfdSinkStMachineThread::m_wfdSinkStMachineTaskList[i])
                    {
                        if (g_rtspVid2ResRefStr.size())
                        {
                            wfdSinkStMachineThread::m_wfdSinkStMachineTaskList[i]->m_rtspParserIntfInfo.preferredVid2ResRefStr = (char *)g_rtspVid2ResRefStr.c_str();
                            CSIO_LOG(eLogLevel_info, "WfdSinkProj_debug: set new string [%s]\n",
                                     wfdSinkStMachineThread::m_wfdSinkStMachineTaskList[i]->m_rtspParserIntfInfo.preferredVid2ResRefStr);
                        } //else
                    }
                }
            }
        }

      // ***
        else if(strcasestr(CmdPtr, "sssltest"))
        {
            CmdPtr = strtok(NULL, ", ");
            if(CmdPtr)
            {
                int arg = strtol(CmdPtr, &EndPtr, 10);
                sssl_Test(arg);
            }
            else
            {
                sssl_Test(-1);
            }
        }
      // ***


        else
        {
           CSIO_LOG(eLogLevel_info, "WfdSinkProj_debug: Unknown command = [%s]\n",CmdPtr);
        }

    }

    CSIO_LOG(eLogLevel_info, "WfdSinkProj_debug: exit\n");
}
/********** end of WfdSinkProjDebug API, used by jni.cpp *******************/
