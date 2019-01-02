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

static wfdSinkProjClass* gWFDSinkProjPtr = NULL;
static Mutex gProjectsLock;
static int gProjectDebug = eLogLevel_debug;

const WFD_STRNUMPAIR wfd_proj_timestamp_names[] =
{
    {"wfd sink project init time     " ,   WFD_SINK_PROJ_TIMESTAMP_INIT},
    {"wfd sink tcp connect start time" ,   WFD_SINK_PROJ_TIMESTAMP_START},
    {"wfd sink tcp connect stop time " ,   WFD_SINK_PROJ_TIMESTAMP_STOP},
    {"wfd sink request idr time      " ,   WFD_SINK_PROJ_TIMESTAMP_REQ_IDR},

    {0,0}//terminate the list
};


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

void WfdSinkProjStart(int id, const char* url, int port)
{
    if(!url) return;

    CSIO_LOG(gProjectDebug, "WfdSinkProjStart: enter: id[%d], url[%s], port[%d].",id,url,port);
    gProjectsLock.lock();

    if(gWFDSinkProjPtr)
    {
        csioEventQueueStruct EvntQ;
        memset(&EvntQ,0,sizeof(csioEventQueueStruct));
        EvntQ.obj_id = id;
        EvntQ.event_type = WFD_SINK_EVENTS_JNI_START;
        EvntQ.buf_size   = strlen(url);
        EvntQ.buffPtr    = (void*)url;
        EvntQ.ext_obj    = port;

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
        evntQ.voidPtr       = pEvntQ->voidPtr;

        void* bufP = pEvntQ->buffPtr;
        int dataSize = pEvntQ->buf_size;

        CSIO_LOG(ABOVE_DEBUG_VERB(m_debugLevel), "wfdSinkProjClass::sendEvent: iId[%d],evnt[%d],dataSize[%d],bufP[0x%x]\n",\
                pEvntQ->obj_id,pEvntQ->event_type,pEvntQ->buf_size,pEvntQ->buffPtr);

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
                                wfdSinkStMachineClass::m_wfdSinkStMachineThreadPtr->sendEvent(&EvntQ);
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
                                    EvntQ.voidPtr    = (void*)(p);

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
                    WfdSinkProjStart(0,addr.c_str(),port);
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
        else
        {
           CSIO_LOG(eLogLevel_info, "WfdSinkProj_debug: Unknown command = [%s]\n",CmdPtr);
        }

    }

    CSIO_LOG(eLogLevel_info, "WfdSinkProj_debug: exit\n");
}
/********** end of WfdSinkProjDebug API, used by jni.cpp *******************/



