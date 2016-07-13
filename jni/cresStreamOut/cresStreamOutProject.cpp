/**
 * Copyright (C) 2016 to the present, Crestron Electronics, Inc.
 * All rights reserved.
 * No part of this software may be reproduced in any form, machine
 * or natural, without the express written consent of Crestron Electronics.
 *
 * \file        CStreamoutProject.cpp
 *
 * \brief       CStreamoutProject layer project
 *
 * \author      John Cheng
 *
 * \date        7/12/2016
 *
 * \note
 *
 *
 * \todo
 */
#include <unistd.h>  // for sleep()
#include <errno.h>   //for errno
#include <stdio.h>   //for perror
#include <stdlib.h>  //for EXIT_FAILURE

#include "cresStreamOutProject.h"

#define MAX_PROJCT_OBJ 10

static void StreamoutProjectSendEvent(int iId, int evnt, int data_size, void* bufP);

CStreamoutProject** StreamOutProjList = NULL ;
int StreamOutProjDebugLevel = CSIO_DEFAULT_LOG_LEVEL;
Mutex gProjectsLock;

/*********************fuctions called from jni.c**************************/
void StreamoutProjectInit()
{
    CSIO_LOG(StreamOutProjDebugLevel, "Streamout: StreamoutProjectInit enter");
    if(StreamOutProjList == NULL)
    {
        StreamOutProjList = new CStreamoutProject* [MAX_PROJCT_OBJ];
        CSIO_LOG(StreamOutProjDebugLevel, "Streamout: new StreamOutProjList:0x%x\n",StreamOutProjList);

        //clean up the list
        for(int i = 0; i < MAX_PROJCT_OBJ; i++)
            StreamOutProjList[i] = NULL;

        //create only one project object for now
        StreamOutProjList[0] = new CStreamoutProject(0);

        CSIO_LOG(StreamOutProjDebugLevel, "Streamout: StreamOutProjList[0]:0x%x\n",StreamOutProjList[0]);

        //create only one project thread for now
        StreamOutProjList[0]->CreateNewThread();
    }
    else
    {
        CSIO_LOG(StreamOutProjDebugLevel, "Streamout: Streamout project already created.\n");
    }

    CSIO_LOG(StreamOutProjDebugLevel, "Streamout: StreamoutProjectInit exit");
}

void StreamoutProjectDeInit()
{
    CSIO_LOG(StreamOutProjDebugLevel, "Streamout: StreamoutProjectDeInit enter");
    if(StreamOutProjList)
    {
        for(int i = 0; i < MAX_PROJCT_OBJ; i++)
        {
            if(StreamOutProjList[i])
            {
                //remove all Streamout tasks this project created.
                StreamOutProjList[i]->removeAllStreamoutTasks();

                //tell thread to exit
                StreamOutProjList[i]->exitThread();

                //wait until thread exits
                CSIO_LOG(StreamOutProjDebugLevel, "Streamout: [%d]call WaitForThreadToExit[0x%x]\n",i,StreamOutProjList[i]);
                StreamOutProjList[i]->WaitForThreadToExit();
                CSIO_LOG(StreamOutProjDebugLevel, "Streamout: Wait is done\n");

                //delete the object, and set list to NULL
                delete StreamOutProjList[i];
                StreamOutProjList[i] = NULL;

                CSIO_LOG(StreamOutProjDebugLevel, "--Streamout: delete StreamOutProjList[%d] is DONE",i);
            }
        }

        delete[] StreamOutProjList;
        StreamOutProjList = NULL;
    }
    else
    {
        CSIO_LOG(StreamOutProjDebugLevel, "Streamout: no Streamout project has created.\n");
    }
    CSIO_LOG(StreamOutProjDebugLevel, "Streamout: StreamoutProjectDeInit exit");
}

void Streamout_Start(int streamID)
{
    CSIO_LOG(StreamOutProjDebugLevel, "Streamout: Streamout_Start() enter");

    gProjectsLock.lock();
    StreamoutProjectSendEvent(streamID, STREAMOUT_EVENT_JNI_CMD_START,0,NULL);
    gProjectsLock.unlock();
    CSIO_LOG(StreamOutProjDebugLevel, "Streamout: Streamout_Start() exit.");
}
void Streamout_Stop(int streamID)
{
    CSIO_LOG(StreamOutProjDebugLevel, "Streamout: Streamout_Stop() enter");
    gProjectsLock.lock();
    StreamoutProjectSendEvent(streamID, STREAMOUT_EVENT_JNI_CMD_STOP,0,NULL);
    gProjectsLock.unlock();
    CSIO_LOG(StreamOutProjDebugLevel, "Streamout: Streamout_Stop() exit.");
}

void Streamout_SetDebugLevel(int level)
{
    StreamOutProjDebugLevel = level;

    gProjectsLock.lock();

    if(StreamOutProjList)
    {
        for(int i = 0; i < MAX_PROJCT_OBJ; i++)
        {
            if(StreamOutProjList[i])
            {
                StreamOutProjList[i]->setProjectDebugLevel(level);
                CSIO_LOG(StreamOutProjDebugLevel, "Streamout: [%d]set debug level to: %d.\n",i,level);
            }
        }
    }
    else
    {
        CSIO_LOG(StreamOutProjDebugLevel, "Streamout: no proxy project is running\n");
    }

    gProjectsLock.unlock();
}

void Streamout_SetPort(char* port)
{
    CSIO_LOG(StreamOutProjDebugLevel, "Streamout: Streamout_SetPort() enter");

    gProjectsLock.lock();

    if(port)
    {
        CSIO_LOG(StreamOutProjDebugLevel, "Streamout: set port to [%s].\n",port);

        StreamoutProjectSendEvent(0, STREAMOUT_EVENT_JNI_CMD_PORT,strlen(port), port);
    }

    gProjectsLock.unlock();
    CSIO_LOG(StreamOutProjDebugLevel, "Streamout: Streamout_SetPort() exit.");
}
void StreamoutProjectDumpClassPara(int level)
{
    gProjectsLock.lock();

    CSIO_LOG(eLogLevel_info, "Streamout: StreamOutProjDebugLevel: %d.\n",StreamOutProjDebugLevel);
    if(StreamOutProjList)
    {
        for(int i = 0; i < MAX_PROJCT_OBJ; i++)
        {
            if(StreamOutProjList[i])
            {
                CSIO_LOG(eLogLevel_info, "Streamout: Project ID: %d, taskObjList[0x%x].\n",
                         i,StreamOutProjList[i]->m_StreamoutTaskObjList);

                StreamOutProjList[i]->DumpClassPara(level);

                if(StreamOutProjList[i]->m_StreamoutTaskObjList)
                {
                    for(int j = 0; j < MAX_STREAM_OUT; j++)
                    {
                        if(StreamOutProjList[i]->m_StreamoutTaskObjList[j])
                        {
                            StreamOutProjList[i]->m_StreamoutTaskObjList[j]->DumpClassPara(level);
                        }
                    }
                }
            }
        }
    }
    else
    {
        CSIO_LOG(eLogLevel_info, "Streamout: no project is running\n");
    }

    gProjectsLock.unlock();
}

CStreamoutManager * StreamoutProjectGetManagerObj()
{
    CStreamoutManager * pManager = NULL;

    if(StreamOutProjList)
    {
        for(int i = 0; i < MAX_PROJCT_OBJ; i++)
        {
            if(StreamOutProjList[i])
            {
                CSIO_LOG(StreamOutProjDebugLevel, "Streamout: Project ID: %d, taskObjList[0x%x].\n",
                         i,StreamOutProjList[i]->m_StreamoutTaskObjList);

                if(StreamOutProjList[i]->m_StreamoutTaskObjList)
                {
                    for(int j = 0; j < MAX_STREAM_OUT; j++)
                    {
                        if(StreamOutProjList[i]->m_StreamoutTaskObjList[j])
                        {
                            pManager = StreamOutProjList[i]->m_StreamoutTaskObjList[j];
                            CSIO_LOG(StreamOutProjDebugLevel, "Streamout: found pManager :i[%d],j[%d].[0x%x]\n",i,j,
                                     pManager);  
 
                            break;//only one for now                         
                        }
                    }
                }
            }
        }
    }
    else
    {
        CSIO_LOG(StreamOutProjDebugLevel, "Streamout: no project is running\n");
    }

    CSIO_LOG(StreamOutProjDebugLevel, "Streamout: pManager : 0x%x\n",pManager);
    return pManager;
}
/*********************local static functions **************************/
static void StreamoutProjectSendEvent(int iId, int evnt, int data_size, void* bufP)
{
    //only one project for now
    if(StreamOutProjList && StreamOutProjList[0])
    {
        CSIO_LOG(StreamOutProjDebugLevel, "Streamout: [%d]call sendEvent.\n",iId);
        EventQueueStruct EvntQ;
        memset(&EvntQ,0,sizeof(EventQueueStruct));
        EvntQ.streamout_obj_id = iId;
        EvntQ.event_type = evnt;
        EvntQ.buf_size   = data_size;
        EvntQ.buffPtr    = bufP;

        StreamOutProjList[0]->sendEvent(&EvntQ);
        CSIO_LOG(StreamOutProjDebugLevel, "Streamout: [%d]done sendEvent.\n",iId);
    }
    else
    {
        CSIO_LOG(StreamOutProjDebugLevel, "Streamout: no proxy project is running\n");
    }
}

CStreamoutProject::CStreamoutProject(int iId): m_projectID(iId)
{
    m_debugLevel = StreamOutProjDebugLevel;

    m_projEvent  = new CStreamoutEvent();

    m_projEventQ = new CStreamoutEventRingBuffer(EVNT_DEFAULT_QUEUE_SIZE);

    mLock        = new Mutex();

    m_StreamoutTaskObjList = new CStreamoutManager* [MAX_STREAM_OUT];
    for(int i = 0; i < MAX_STREAM_OUT; i++)
    {
        m_StreamoutTaskObjList[i] = NULL;
    }

    main_loop     = NULL;
    main_context   = NULL;
    pMediaPipeline = NULL;

    //init default variables
    strcpy(m_rtsp_port, DEFAULT_RTSP_PORT);
    strcpy(m_res_x, DEFAULT_RES_X);
    strcpy(m_res_y, DEFAULT_RES_Y);
    strcpy(m_frame_rate, DEFAULT_FRAME_RATE);
}

CStreamoutProject::~CStreamoutProject()
{
    removeAllStreamoutTasks();
    CSIO_LOG(m_debugLevel, "--Streamout: ~CStreamoutProject delete m_projEvent is DONE");

    if(m_projEvent)
        delete m_projEvent;

    if(m_projEventQ)
        delete m_projEventQ;

    if(mLock)
        delete mLock;

    //Note:removeAllStreamoutTasks() should be called before delete this object
}

void CStreamoutProject::DumpClassPara(int level)
{
    CSIO_LOG(eLogLevel_info, "--Streamout: Streamout project m_projectID:       %d.\n",m_projectID);
    CSIO_LOG(eLogLevel_info, "--Streamout: Streamout project threadObjLoopCnt:  %d.\n",m_threadObjLoopCnt);

    CSIO_LOG(eLogLevel_info, "--Streamout: ThredId 0x%x", (unsigned long int)getThredId());
    CSIO_LOG(eLogLevel_info, "--Streamout: m_debugLevel %d", m_debugLevel);

    if(m_projEventQ)
        CSIO_LOG(eLogLevel_info, "--Streamout project: Event queued up %d", m_projEventQ->GetEvntQueueCount());

    CSIO_LOG(eLogLevel_info, "--Streamout: m_rtsp_port %s", m_rtsp_port);
    CSIO_LOG(eLogLevel_info, "--Streamout: m_res_x %s", m_res_x);
    CSIO_LOG(eLogLevel_info, "--Streamout: m_res_y %s", m_res_y);
    CSIO_LOG(eLogLevel_info, "--Streamout: m_frame_rate %s", m_frame_rate);

}
void* CStreamoutProject::ThreadEntry()
{
    CSIO_LOG(m_debugLevel, "Streamout: Enter ThreadEntry, m_projectID ID:%d.\n",m_projectID);

    int wtRtn  = 0;
    int evntId = STREAMOUT_EVENTS_MAX;
    if(!m_projEvent || !m_projEventQ)
    {
        //thread exit here
        m_ThreadIsRunning = 0;
        return NULL;
    }

    for(;;)
    {
        //sleep if nothing queued
        if(m_projEventQ->GetEvntQueueCount() == 0)
        {
            //NOTE: currently, this evntId is not being used to identify anything
            wtRtn = m_projEvent->waitForEvent(&evntId,THREAD_LOOP_TO);
        }
        else//consume all events here
        {
            CSIO_LOG(eLogLevel_extraVerbose, "Streamout: skip wait, GetEvntQueueCount:%d\n",
                     m_projEventQ->GetEvntQueueCount());
        }

        m_threadObjLoopCnt++;

        //TODO: we need to check wtRtn here
        //CSIO_LOG(eLogLevel_extraVerbose, "Streamout: waitForEvent return:%d, event ID:%d\n",wtRtn,evntId);

        EventQueueStruct evntQ;

        if(m_projEventQ->GetFromBuffer(&evntQ))
        {
            CSIO_LOG(m_debugLevel, "Streamout: evntQ is:size[%d],type[%d],iId[%d],buffPtr[0x%x]\n",\
                     evntQ.buf_size,evntQ.event_type,evntQ.streamout_obj_id,evntQ.buffPtr);

            logEventPop(evntQ.event_type);

            switch (evntQ.event_type)
            {
                case STREAMOUT_EVENT_JNI_CMD_START:
                {
                    int id = evntQ.streamout_obj_id;

                    //validate, for now we can only do one stream
                    if( !IsValidStreamOut(id) )
                    {
                        CSIO_LOG(eLogLevel_error, "Streamout: gst_rtsp_server_start obj ID is invalid = %d",id);
                        break;
                    }

                    //for now we can only do one stream
                    if(m_StreamoutTaskObjList)
                    {
                        if(m_StreamoutTaskObjList[id])
                        {
                            CSIO_LOG(m_debugLevel, "Streamout: gst_rtsp_server_start already exist[0x%x].",
                                    m_StreamoutTaskObjList[id]);
                        }
                        else
                        {
                            m_StreamoutTaskObjList[id] = new CStreamoutManager();

                            m_StreamoutTaskObjList[id]->setParent(this);
                            m_StreamoutTaskObjList[id]->setServManagerDebugLevel(m_debugLevel);

                            //init default variables
                            m_StreamoutTaskObjList[id]->setPort(m_rtsp_port);
                            m_StreamoutTaskObjList[id]->setResX(m_res_x);
                            m_StreamoutTaskObjList[id]->setResY(m_res_y);
                            m_StreamoutTaskObjList[id]->setFrameRate(m_frame_rate);

                            m_StreamoutTaskObjList[id]->CreateNewThread();

                            //log start time stamp
                            //gettimeofday(&eventTime[PROXY_EVENTTIME_CMD_START], NULL);
                        }
                    }

                    break;
                }
                case STREAMOUT_EVENT_JNI_CMD_STOP:
                {
                    int id = evntQ.streamout_obj_id;

                    //validate, for now we can only do one stream
                    if( !IsValidStreamOut(id) )
                    {
                        CSIO_LOG(eLogLevel_error, "Streamout: gst_rtsp_server_start obj ID is invalid = %d",id);
                        break;
                    }

                    //for now we can only do one stream
                    if(m_StreamoutTaskObjList)
                    {
                        if(m_StreamoutTaskObjList[id])
                        {
                            //tell thread to exit
                            m_StreamoutTaskObjList[id]->exitThread();

                            //wait until thread exits
                            CSIO_LOG(m_debugLevel, "Streamout: call WaitForThreadToExit[0x%x]\n",m_StreamoutTaskObjList[0]);
                            m_StreamoutTaskObjList[id]->WaitForThreadToExit();
                            CSIO_LOG(m_debugLevel, "Streamout: Wait is done\n");

                            //delete the object, and set list to NULL
                            delete m_StreamoutTaskObjList[id];
                            m_StreamoutTaskObjList[id] = NULL;
                        }
                    }
                    break;
                }
                default:
                    break;
            }
        }
        else
        {
            //CSIO_LOG(eLogLevel_extraVerbose, "Streamout: evntQ is empty\n");
        }

        //check to see if we need to restart task
        restartStreamoutIfMainLoopEnded();

        if(m_forceThreadExit)
        {
            //TODO: exit all child thread and wait here
            break;
        }
    }

    CSIO_LOG(m_debugLevel, "Streamout: m_projectID ID:%d exiting...\n",m_projectID);

    //thread exit here
    m_ThreadIsRunning = 0;

    return NULL;
}
void CStreamoutProject::sendEvent(EventQueueStruct* pEvntQ)
{
    //TODO: need to work on buffer copy
    if(m_projEvent && m_projEventQ && pEvntQ)
    {
        EventQueueStruct evntQ;
        memset(&evntQ,0,sizeof(EventQueueStruct));
        evntQ.streamout_obj_id  = pEvntQ->streamout_obj_id;
        evntQ.event_type    = pEvntQ->event_type;
        evntQ.buf_size      = 0;
        evntQ.buffPtr       = NULL;
        evntQ.ext_obj       = pEvntQ->ext_obj;;
        evntQ.ext_obj2      = pEvntQ->ext_obj2;;

        void* bufP = pEvntQ->buffPtr;
        int dataSize = pEvntQ->buf_size;

        CSIO_LOG(m_debugLevel, "Streamout: iId[%d],evnt[%d],dataSize[%d],bufP[0x%x]\n",\
        		pEvntQ->streamout_obj_id,pEvntQ->event_type,pEvntQ->buf_size,pEvntQ->buffPtr);

        if(bufP && dataSize)
        {
            switch (evntQ.event_type)
            {

            }
        }
        //passing a copy of the queue
        m_projEventQ->AddToBuffer(evntQ);
        CSIO_LOG(m_debugLevel, "Streamout: event added to the queue[0x%x].",evntQ.buffPtr);

        m_projEvent->signalEvent(evntQ.event_type);
        CSIO_LOG(m_debugLevel, "Streamout: event[%d] sent.",evntQ.event_type);

        logEventPush(evntQ.event_type);
    }
    else
    {
        CSIO_LOG(eLogLevel_warning, "Streamout: m_projEvent or m_projEventQ is NULL\n");
    }
}
void CStreamoutProject::restartStreamoutIfMainLoopEnded()
{
    if(m_StreamoutTaskObjList)
    {
        for(int i = 0; i < MAX_STREAM_OUT; i++)
        {
            if(m_StreamoutTaskObjList[i])
            {
                if(m_StreamoutTaskObjList[i]->m_ThreadIsRunning == 0)
                {
                    CSIO_LOG(eLogLevel_warning, "Streamout: stream out main loop exited by itself.\n");

                    //this should return right away
                    m_StreamoutTaskObjList[i]->WaitForThreadToExit();

                    CSIO_LOG(eLogLevel_warning, "Streamout: try to restart a new stream out thread.\n");
                    //as long as this object exist, restart it
                    m_StreamoutTaskObjList[i]->CreateNewThread();

                    CSIO_LOG(eLogLevel_warning, "Streamout: restarts a new stream out thread.\n");
                }
            }
        }
    }
}
void CStreamoutProject::removeAllStreamoutTasks()
{
    if(m_StreamoutTaskObjList)
    {
        for(int i = 0; i < MAX_STREAM_OUT; i++)
        {
            if(m_StreamoutTaskObjList[i])
            {
                //tell thread to exit
                m_StreamoutTaskObjList[i]->exitThread();

                //wait until thread exits
                CSIO_LOG(m_debugLevel, "Streamout: [%d]call WaitForThreadToExit[0x%x]\n",i,m_StreamoutTaskObjList[i]);
                m_StreamoutTaskObjList[i]->WaitForThreadToExit();
                CSIO_LOG(m_debugLevel, "Streamout: Wait is done\n");

                //delete the object, and set list to NULL
                delete m_StreamoutTaskObjList[i];
                m_StreamoutTaskObjList[i] = NULL;
            }
        }

        delete[] m_StreamoutTaskObjList;
        m_StreamoutTaskObjList = NULL;
    }
}
void CStreamoutProject::setProjectDebugLevel(int level)
{
    setDebugLevel(level);
}
