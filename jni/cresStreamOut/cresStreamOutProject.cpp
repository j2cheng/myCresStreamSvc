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
CStreamoutManager** gStreamoutTaskObjList = NULL;

/*********************fuctions called from jni.c**************************/
void StreamoutProjectInit(eStreamoutMode mode)
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
        StreamOutProjList[0] = new CStreamoutProject(0, mode);

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
                CSIO_LOG(StreamOutProjDebugLevel, "Streamout[%d]: call removeAllStreamoutTasks()", i);
                //remove all Streamout tasks this project created.
                StreamOutProjList[i]->removeAllStreamoutTasks();

                //tell thread to exit
                StreamOutProjList[i]->exitThread();

                //wait until thread exits
                CSIO_LOG(StreamOutProjDebugLevel, "Streamout: DONE ** [%d]call WaitForThreadToExit[0x%x]\n",i,StreamOutProjList[i]);
                StreamOutProjList[i]->WaitForThreadToExit();
                CSIO_LOG(StreamOutProjDebugLevel, "Streamout: Wait is done\n");

                //delete the object, and set list to NULL
                CSIO_LOG(StreamOutProjDebugLevel, "--Streamout: delete StreamOutProjList[%d]",i);
                delete StreamOutProjList[i];
                CSIO_LOG(StreamOutProjDebugLevel, "--Streamout: set StreamOutProjList[%d] to null",i);
                StreamOutProjList[i] = NULL;

                CSIO_LOG(StreamOutProjDebugLevel, "--Streamout: delete StreamOutProjList[%d] is DONE",i);
            }
        }

        CSIO_LOG(StreamOutProjDebugLevel, "Streamout: delete StreamOutProjList");
        delete[] StreamOutProjList;
        CSIO_LOG(StreamOutProjDebugLevel, "Streamout: set StreamOutProjList to null");
        StreamOutProjList = NULL;
    }
    else
    {
        CSIO_LOG(StreamOutProjDebugLevel, "Streamout: no Streamout project was created.\n");
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

void Streamout_SetFrameRate(char* rate)
{
    CSIO_LOG(StreamOutProjDebugLevel, "Streamout: Streamout_SetFrameRate() enter");

    gProjectsLock.lock();

    if(rate)
    {
        CSIO_LOG(StreamOutProjDebugLevel, "Streamout: set rate to [%s].\n",rate);

        StreamoutProjectSendEvent(0, STREAMOUT_EVENT_JNI_CMD_FRAMERATE,strlen(rate), rate);
    }

    gProjectsLock.unlock();
    CSIO_LOG(StreamOutProjDebugLevel, "Streamout: Streamout_SetFrameRate() exit.");
}

void Streamout_SetBitrate(char* bitrate)
{
    CSIO_LOG(StreamOutProjDebugLevel, "Streamout: Streamout_SetBitrate() enter");

    gProjectsLock.lock();

    if(bitrate)
    {
        CSIO_LOG(StreamOutProjDebugLevel, "Streamout: set bitrate to [%s].\n", bitrate);

        StreamoutProjectSendEvent(0, STREAMOUT_EVENT_JNI_CMD_BITRATE,strlen(bitrate), bitrate);
    }

    gProjectsLock.unlock();
    CSIO_LOG(StreamOutProjDebugLevel, "Streamout: Streamout_SetBitrate() exit.");
}

void Streamout_SetIFrameInterval(char* iframeinterval)
{
    CSIO_LOG(StreamOutProjDebugLevel, "Streamout: Streamout_SetIFrameInterval() enter");

    gProjectsLock.lock();

    if(iframeinterval)
    {
        CSIO_LOG(StreamOutProjDebugLevel, "Streamout: set iframeinterval to [%s].\n", iframeinterval);

        StreamoutProjectSendEvent(0, STREAMOUT_EVENT_JNI_CMD_IFRAMEINTERVAL,strlen(iframeinterval), iframeinterval);
    }

    gProjectsLock.unlock();
    CSIO_LOG(StreamOutProjDebugLevel, "Streamout: Streamout_SetIFrameInterval() exit.");
}

void Streamout_SetRes_x(char* res_x)
{
    CSIO_LOG(StreamOutProjDebugLevel, "Streamout: Streamout_SetRes_x() enter");

    gProjectsLock.lock();

    if(res_x)
    {
        CSIO_LOG(StreamOutProjDebugLevel, "Streamout: set res_x to [%s].\n",res_x);

        StreamoutProjectSendEvent(0, STREAMOUT_EVENT_JNI_CMD_RES_X,strlen(res_x), res_x);
    }

    gProjectsLock.unlock();
    CSIO_LOG(StreamOutProjDebugLevel, "Streamout: Streamout_SetRes_x() exit.");
}
void Streamout_SetRes_y(char* res_y)
{
    CSIO_LOG(StreamOutProjDebugLevel, "Streamout: Streamout_SetRes_y() enter");

    gProjectsLock.lock();

    if(res_y)
    {
        CSIO_LOG(StreamOutProjDebugLevel, "Streamout: set res_y to [%s].\n",res_y);

        StreamoutProjectSendEvent(0, STREAMOUT_EVENT_JNI_CMD_RES_Y,strlen(res_y), res_y);
    }

    gProjectsLock.unlock();
    CSIO_LOG(StreamOutProjDebugLevel, "Streamout: Streamout_SetRes_y() exit.");
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

void Streamout_EnableSecurity(int enable)
{
	CSIO_LOG(StreamOutProjDebugLevel, "Streamout: %s() enter", __FUNCTION__);

	gProjectsLock.lock();

	CSIO_LOG(StreamOutProjDebugLevel, "Streamout: set security enable to [%d].\n", enable);

	StreamoutProjectSendEvent(0, STREAMOUT_EVENT_JNI_CMD_ENABLE_SECURITY, sizeof(int), &enable);

	gProjectsLock.unlock();
	CSIO_LOG(StreamOutProjDebugLevel, "Streamout: %s() exit.", __FUNCTION__);
}

void Streamout_EnableMulticast(int enable)
{
	CSIO_LOG(StreamOutProjDebugLevel, "Streamout: %s() enter", __FUNCTION__);

	gProjectsLock.lock();

	CSIO_LOG(StreamOutProjDebugLevel, "Streamout: set multicast enable to [%d].\n", enable);

	StreamoutProjectSendEvent(0, STREAMOUT_EVENT_JNI_CMD_ENABLE_MULTICAST,sizeof(int), &enable);

	gProjectsLock.unlock();
	CSIO_LOG(StreamOutProjDebugLevel, "Streamout: %s() exit.", __FUNCTION__);
}

void Streamout_SetMulticastAddress(char * address)
{
	CSIO_LOG(StreamOutProjDebugLevel, "Streamout: %s() enter", __FUNCTION__);

	gProjectsLock.lock();

	CSIO_LOG(StreamOutProjDebugLevel, "Streamout: set multicast address to [%s].\n",address);

	StreamoutProjectSendEvent(0, STREAMOUT_EVENT_JNI_CMD_MULTICAST_ADDRESS,strlen(address), address);

	gProjectsLock.unlock();
	CSIO_LOG(StreamOutProjDebugLevel, "Streamout: %s() exit.", __FUNCTION__);
}

void Streamout_SetStreamName(char * name)
{
	CSIO_LOG(StreamOutProjDebugLevel, "Streamout: %s() enter", __FUNCTION__);

	gProjectsLock.lock();

	CSIO_LOG(StreamOutProjDebugLevel, "Streamout: set stream name to [%s].\n", name);

	StreamoutProjectSendEvent(0, STREAMOUT_EVENT_JNI_CMD_STREAM_NAME,strlen(name), name);

	gProjectsLock.unlock();
	CSIO_LOG(StreamOutProjDebugLevel, "Streamout: %s() exit.", __FUNCTION__);
}

void Streamout_SetSnapshotName(char * name)
{
	CSIO_LOG(StreamOutProjDebugLevel, "Streamout: %s() enter", __FUNCTION__);

	gProjectsLock.lock();

	CSIO_LOG(StreamOutProjDebugLevel, "Streamout: set snapshot name to [%s].\n", name);

	StreamoutProjectSendEvent(0, STREAMOUT_EVENT_JNI_CMD_SNAPSHOT_NAME,strlen(name), name);

	gProjectsLock.unlock();
	CSIO_LOG(StreamOutProjDebugLevel, "Streamout: %s() exit.", __FUNCTION__);
}

void Streamout_StartPreview(int streamid, void *native_window)
{
	CSIO_LOG(StreamOutProjDebugLevel, "Streamout: %s() enter", __FUNCTION__);

	gProjectsLock.lock();

	StreamoutProjectSendEvent(streamid, STREAMOUT_EVENT_JNI_CMD_START_PREVIEW, 1, native_window);

	gProjectsLock.unlock();
	CSIO_LOG(StreamOutProjDebugLevel, "Streamout: %s() exit.", __FUNCTION__);
}

void Streamout_PausePreview(int streamid)
{
	CSIO_LOG(StreamOutProjDebugLevel, "Streamout: %s() enter", __FUNCTION__);

	gProjectsLock.lock();

	void *window = NULL;
	StreamoutProjectSendEvent(streamid, STREAMOUT_EVENT_JNI_CMD_PAUSE_PREVIEW, 1, window);

	gProjectsLock.unlock();
	CSIO_LOG(StreamOutProjDebugLevel, "Streamout: %s() exit.", __FUNCTION__);
}

void Streamout_StopPreview(int streamid)
{
	CSIO_LOG(StreamOutProjDebugLevel, "Streamout: %s() enter", __FUNCTION__);

	gProjectsLock.lock();

	void *window = NULL;
	StreamoutProjectSendEvent(streamid, STREAMOUT_EVENT_JNI_CMD_STOP_PREVIEW, 1, window);

	gProjectsLock.unlock();
	CSIO_LOG(StreamOutProjDebugLevel, "Streamout: %s() exit.", __FUNCTION__);
}

int  Streamout_WaitForPreviewAvailable(int streamid,int timeout_sec)
{
	CSIO_LOG(StreamOutProjDebugLevel, "Streamout: %s() enter", __FUNCTION__);

	int rtn = 0;

	gProjectsLock.lock();

	if( IsValidStreamOut(streamid) )
	{
		if(gStreamoutTaskObjList)
		{
			if(gStreamoutTaskObjList[streamid])
			{
				rtn = gStreamoutTaskObjList[streamid]->waitForPreviewAvailable(timeout_sec);
			}
		}
	}
	else
	{
		CSIO_LOG(eLogLevel_error, "Streamout: obj ID is invalid = %d",streamid);
	}

	gProjectsLock.unlock();
	CSIO_LOG(StreamOutProjDebugLevel, "Streamout: %s() exit.", __FUNCTION__);

	return( rtn );
}

int  Streamout_WaitForPreviewClosed(int streamid,int timeout_sec)
{
	CSIO_LOG(StreamOutProjDebugLevel, "Streamout: %s() enter", __FUNCTION__);

	int rtn = 0;

	gProjectsLock.lock();

	if( IsValidStreamOut(streamid) )
	{
		if(gStreamoutTaskObjList)
		{
			rtn = WaitForPreviewClosed(timeout_sec);
		}
	}
	else
	{
		CSIO_LOG(eLogLevel_error, "Streamout: obj ID is invalid = %d",streamid);
	}

	gProjectsLock.unlock();
	CSIO_LOG(StreamOutProjDebugLevel, "Streamout: %s() exit.", __FUNCTION__);

	return( rtn );
}

void Streamout_SetVideoCaptureDevice(const char * device)
{
	CSIO_LOG(StreamOutProjDebugLevel, "Streamout: %s() enter", __FUNCTION__);

	gProjectsLock.lock();

	CSIO_LOG(StreamOutProjDebugLevel, "Streamout: set video capture device to [%s].\n", device);

	StreamoutProjectSendEvent(0, STREAMOUT_EVENT_JNI_CMD_VIDEO_CAPTURE_DEVICE, strlen(device), (void *) device);

	gProjectsLock.unlock();
	CSIO_LOG(StreamOutProjDebugLevel, "Streamout: %s() exit.", __FUNCTION__);
}

void Streamout_SetAudioCaptureDevice(const char * device)
{
	CSIO_LOG(StreamOutProjDebugLevel, "Streamout: %s() enter", __FUNCTION__);

	gProjectsLock.lock();

	CSIO_LOG(StreamOutProjDebugLevel, "Streamout: set audio capture device to [%s].\n", device);

	StreamoutProjectSendEvent(0, STREAMOUT_EVENT_JNI_CMD_AUDIO_CAPTURE_DEVICE, strlen(device), (void *) device);

	gProjectsLock.unlock();
	CSIO_LOG(StreamOutProjDebugLevel, "Streamout: %s() exit.", __FUNCTION__);
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

CStreamoutProject::CStreamoutProject(int iId, eStreamoutMode streamoutMode): m_projectID(iId), m_streamoutMode(streamoutMode)
{
    CSIO_LOG(m_debugLevel, "Streamout: create CStreamoutProject object - mode = %d", m_streamoutMode);

    m_debugLevel = StreamOutProjDebugLevel;

    m_projEvent  = new CStreamoutEvent();

    m_projEventQ = new CStreamoutEventRingBuffer(EVNT_DEFAULT_QUEUE_SIZE);

    mLock        = new Mutex();

    m_StreamoutTaskObjList = new CStreamoutManager* [MAX_STREAM_OUT];
    gStreamoutTaskObjList = m_StreamoutTaskObjList;
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
    strcpy(m_bit_rate, DEFAULT_BIT_RATE);
    strcpy(m_iframe_interval, DEFAULT_IFRAME_INTERVAL);
    m_multicast_enable = DEFAULT_MULTICAST_ENABLE;
    strcpy(m_multicast_address, DEFAULT_MULTICAST_ADDRESS);
    strcpy(m_stream_name, DEFAULT_STREAM_NAME);
    strcpy(m_snapshot_name, DEFAULT_SNAPSHOT_NAME);
    m_security_enabled = false;
    strcpy(m_video_capture_device, DEFAULT_VIDEO_CAPTURE_DEVICE);
    strcpy(m_audio_capture_device, DEFAULT_AUDIO_CAPTURE_DEVICE);

    if (m_streamoutMode == STREAMOUT_MODE_WIRELESSCONFERENCING)
    {
    	m_security_enabled = true;
        strcpy(m_res_x, "1920");
        strcpy(m_res_y, "1080");
        strcpy(m_iframe_interval, "30");
        strcpy(m_stream_name, DEFAULT_WIRELESSCONFERENCING_STREAM_NAME);
    }

    if (m_streamoutMode == STREAMOUT_MODE_CAMERA)
    {
        m_cameraobj = new CStreamCamera(this);
        m_cameraobj->create(this);
    }
}

CStreamoutProject::~CStreamoutProject()
{
    removeAllStreamoutTasks();
    CSIO_LOG(m_debugLevel, "--Streamout: ~CStreamoutProject removeAllStreamoutTasks() is complete");

    CSIO_LOG(m_debugLevel, "--Streamout: ~CStreamoutProject streamoutMode=%d", m_streamoutMode);
    if (m_streamoutMode == STREAMOUT_MODE_CAMERA)
        m_cameraobj->remove(this);

    if(m_projEvent)
    {
        CSIO_LOG(m_debugLevel, "--Streamout: ~CStreamoutProject delete m_projEvent");
        delete m_projEvent;
        m_projEvent = NULL;
    }

    if(m_projEventQ)
    {
        CSIO_LOG(m_debugLevel, "--Streamout: ~CStreamoutProject delete m_projEventQ");
        delete m_projEventQ;
        m_projEventQ = NULL;
    }

	if(m_cameraobj)
	{
        CSIO_LOG(m_debugLevel, "--Streamout: ~CStreamoutProject delete m_cameraobj");
		delete m_cameraobj;
		m_cameraobj = NULL;
	}

    if(mLock)
    {
        CSIO_LOG(m_debugLevel, "--Streamout: ~CStreamoutProject delete mLock");
        delete mLock;
        mLock = NULL;
    }

    CSIO_LOG(m_debugLevel, "--Streamout: ~CStreamoutProject exit");
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

    CSIO_LOG(eLogLevel_info, "--Streamout: m_bit_rate %s", m_bit_rate);
    CSIO_LOG(eLogLevel_info, "--Streamout: m_iframe_interval %s", m_iframe_interval);
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
        bool eventReceived = false;

        if((eventReceived = m_projEventQ->GetFromBuffer(&evntQ)))
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
                            CSIO_LOG(m_debugLevel, "Streamout: create new gst_rtsp_server_start at id[%d].",
                                    id);
                            m_StreamoutTaskObjList[id] = new CStreamoutManager(m_streamoutMode);

                            m_StreamoutTaskObjList[id]->setParent(this);
                            m_StreamoutTaskObjList[id]->setServManagerDebugLevel(m_debugLevel);

                            //init default variables
                            m_StreamoutTaskObjList[id]->setPort(m_rtsp_port);
                            m_StreamoutTaskObjList[id]->setResX(m_res_x);
                            m_StreamoutTaskObjList[id]->setResY(m_res_y);
                            m_StreamoutTaskObjList[id]->setFrameRate(m_frame_rate);
                            m_StreamoutTaskObjList[id]->setBitRate(m_bit_rate);
                            m_StreamoutTaskObjList[id]->setIFrameInterval(m_iframe_interval);
                            m_StreamoutTaskObjList[id]->setMulticastEnable(&m_multicast_enable);
                            m_StreamoutTaskObjList[id]->setMulticastAddress(m_multicast_address);
                            m_StreamoutTaskObjList[id]->setStreamName(m_stream_name);
                            m_StreamoutTaskObjList[id]->setSnapshotName(m_snapshot_name);
                            m_StreamoutTaskObjList[id]->setSecurityEnable(m_security_enabled);
                            m_StreamoutTaskObjList[id]->setVideoCaptureDevice(m_video_capture_device);
                            m_StreamoutTaskObjList[id]->setAudioCaptureDevice(m_audio_capture_device);

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
                case STREAMOUT_EVENT_JNI_CMD_PORT:
                {
                    int id = evntQ.streamout_obj_id;
                    if( evntQ.buf_size && evntQ.buffPtr)
                    {
                        CSIO_LOG(m_debugLevel, "Streamout: call setPort streamId[%d],port[%s]",
                                 id,evntQ.buffPtr);

                        //save for this project
                        strcpy(m_rtsp_port, (char*)evntQ.buffPtr);

                        m_projEventQ->del_Q_buf(evntQ.buffPtr);
                    }
                    else
                    {
                        CSIO_LOG(m_debugLevel, "Streamout: streamId[%d],port string is null",id);
                    }

                    CSIO_LOG(m_debugLevel, "Streamout: STREAMOUT_EVENT_JNI_CMD_PORT done.");
                    break;
                }
                case STREAMOUT_EVENT_JNI_CMD_RES_X:
                {
                    int id = evntQ.streamout_obj_id;
                    if( evntQ.buf_size && evntQ.buffPtr)
                    {
                        CSIO_LOG(m_debugLevel, "Streamout: call setFrameRate streamId[%d],res_x[%s]",
                                 id,evntQ.buffPtr);

                        //save for this project
                        strcpy(m_res_x, (char*)evntQ.buffPtr);

                        m_projEventQ->del_Q_buf(evntQ.buffPtr);
                    }
                    else
                    {
                        CSIO_LOG(m_debugLevel, "Streamout: streamId[%d],res_x string is null",id);
                    }

                    CSIO_LOG(m_debugLevel, "Streamout: STREAMOUT_EVENT_JNI_CMD_RES_X done.");
                    break;
                }
                case STREAMOUT_EVENT_JNI_CMD_RES_Y:
                {
                    int id = evntQ.streamout_obj_id;
                    if( evntQ.buf_size && evntQ.buffPtr)
                    {
                        CSIO_LOG(m_debugLevel, "Streamout: call setResY streamId[%d],res_y[%s]",
                                 id,evntQ.buffPtr);

                        //save for this project
                        strcpy(m_res_y, (char*)evntQ.buffPtr);

                        m_projEventQ->del_Q_buf(evntQ.buffPtr);
                    }
                    else
                    {
                        CSIO_LOG(m_debugLevel, "Streamout: streamId[%d],res_y string is null",id);
                    }

                    CSIO_LOG(m_debugLevel, "Streamout: STREAMOUT_EVENT_JNI_CMD_RES_Y done.");
                    break;
                }
                case STREAMOUT_EVENT_JNI_CMD_FRAMERATE:
                {
                    int id = evntQ.streamout_obj_id;
                    if( evntQ.buf_size && evntQ.buffPtr)
                    {
                        CSIO_LOG(m_debugLevel, "Streamout: call setFrameRate streamId[%d],framerate[%s]",
                                 id,evntQ.buffPtr);

                        //save for this project
                        strcpy(m_frame_rate, (char*)evntQ.buffPtr);

                        m_projEventQ->del_Q_buf(evntQ.buffPtr);
                    }
                    else
                    {
                        CSIO_LOG(m_debugLevel, "Streamout: streamId[%d],framerate string is null",id);
                    }

                    CSIO_LOG(m_debugLevel, "Streamout: STREAMOUT_EVENT_JNI_CMD_FRAMERATE done.");
                    break;
                }
                case STREAMOUT_EVENT_JNI_CMD_BITRATE:
                {
                    int id = evntQ.streamout_obj_id;
                    if( evntQ.buf_size && evntQ.buffPtr)
                    {
                        CSIO_LOG(m_debugLevel, "Streamout: call setBitRate streamId[%d],bitrate[%s]",
                                 id,evntQ.buffPtr);

                        //save for this project
                        strcpy(m_bit_rate, (char*)evntQ.buffPtr);

                        m_projEventQ->del_Q_buf(evntQ.buffPtr);
                    }
                    else
                    {
                        CSIO_LOG(m_debugLevel, "Streamout: streamId[%d],bitrate string is null",id);
                    }

                    CSIO_LOG(m_debugLevel, "Streamout: STREAMOUT_EVENT_JNI_CMD_BITRATE done.");
                    break;
                }
                case STREAMOUT_EVENT_JNI_CMD_IFRAMEINTERVAL:
                {
                    int id = evntQ.streamout_obj_id;
                    if( evntQ.buf_size && evntQ.buffPtr)
                    {
                        CSIO_LOG(m_debugLevel, "Streamout: call setIFrameInterval streamId[%d],iframeinterval[%s]",
                                 id,evntQ.buffPtr);

                        //save for this project
                        strcpy(m_iframe_interval, (char*)evntQ.buffPtr);

                        m_projEventQ->del_Q_buf(evntQ.buffPtr);
                    }
                    else
                    {
                        CSIO_LOG(m_debugLevel, "Streamout: streamId[%d],iframeinterval string is null",id);
                    }

                    CSIO_LOG(m_debugLevel, "Streamout: STREAMOUT_EVENT_JNI_CMD_IFRAMEINTERVAL done.");
                    break;
                }
                case STREAMOUT_EVENT_JNI_CMD_ENABLE_SECURITY:
                {
                    if (m_streamoutMode == STREAMOUT_MODE_CAMERA) { // short circuit - not applicable for WirelessConferencing case
                        CSIO_LOG(m_debugLevel, "Streamout: STREAMOUT_EVENT_JNI_CMD_ENABLE_MULTICAST ignored in WirlessConferencing mode.");
                    	break;
                    }
                	int id = evntQ.streamout_obj_id;

                	if( evntQ.buf_size && evntQ.buffPtr)
                	{
						int enable = *((int *)evntQ.buffPtr);
                		CSIO_LOG(m_debugLevel, "Streamout: call enableSecurity streamId[%d],securityEnable[%d]",
                				id, enable);

                		//save for this project
                		m_security_enabled = (enable) ? true : false;

                		m_projEventQ->del_Q_buf(evntQ.buffPtr);
                	}
                	else
                	{
                		CSIO_LOG(m_debugLevel, "Streamout: streamId[%d], enableSecurity is null",id);
                	}

                	CSIO_LOG(m_debugLevel, "Streamout: STREAMOUT_EVENT_JNI_CMD_ENABLE_MULTICAST done.");
                	break;
                }
                case STREAMOUT_EVENT_JNI_CMD_ENABLE_MULTICAST:
                {
                    if (m_streamoutMode != STREAMOUT_MODE_CAMERA) { // short circuit - not applicable for WirelessConferencing case
                        CSIO_LOG(m_debugLevel, "Streamout: STREAMOUT_EVENT_JNI_CMD_ENABLE_MULTICAST ignored in WirlessConferencing mode.");
                    	break;
                    }
                	int id = evntQ.streamout_obj_id;
                	
                	if( evntQ.buf_size && evntQ.buffPtr)
                	{
						int enable = *((int *)evntQ.buffPtr);
                		CSIO_LOG(m_debugLevel, "Streamout: call enableMulticast streamId[%d],multicastEnable[%d]",
                				id, enable);

                		//save for this project
                		m_multicast_enable = enable;

                		m_projEventQ->del_Q_buf(evntQ.buffPtr);
                	}
                	else
                	{
                		CSIO_LOG(m_debugLevel, "Streamout: streamId[%d], enableMulticast is null",id);
                	}

                	CSIO_LOG(m_debugLevel, "Streamout: STREAMOUT_EVENT_JNI_CMD_ENABLE_MULTICAST done.");
                	break;
                }
                case STREAMOUT_EVENT_JNI_CMD_MULTICAST_ADDRESS:
                {
                	if (m_streamoutMode != STREAMOUT_MODE_CAMERA) { // short circuit - not applicable for WirelessConferencing case
                		CSIO_LOG(m_debugLevel, "Streamout: STREAMOUT_EVENT_JNI_CMD_MULTICAST_ADDRESS ignored in WirlessConferencing mode.");
                		break;
                	}
                	int id = evntQ.streamout_obj_id;
                	if( evntQ.buf_size && evntQ.buffPtr)
                	{
                		CSIO_LOG(m_debugLevel, "Streamout: call setMulticastAddress streamId[%d],multicastAddress[%s]",
                				id,evntQ.buffPtr);

                		//save for this project
                		strcpy(m_multicast_address, (char*)evntQ.buffPtr);

                		m_projEventQ->del_Q_buf(evntQ.buffPtr);
                	}
                	else
                	{
                		CSIO_LOG(m_debugLevel, "Streamout: streamId[%d],setMulticastAddress string is null",id);
                	}

                	CSIO_LOG(m_debugLevel, "Streamout: STREAMOUT_EVENT_JNI_CMD_MULTICAST_ADDRESS done.");
                	break;
                }
                case STREAMOUT_EVENT_JNI_CMD_STREAM_NAME:
                {
                	int id = evntQ.streamout_obj_id;
                	if( evntQ.buf_size && evntQ.buffPtr)
                	{
                		CSIO_LOG(m_debugLevel, "Streamout: call setStreamName streamId[%d],streamName[%s]",
                				id,evntQ.buffPtr);

                		//save for this project
                		strcpy(m_stream_name, (char*)evntQ.buffPtr);

                		m_projEventQ->del_Q_buf(evntQ.buffPtr);
                	}
                	else
                	{
                		CSIO_LOG(m_debugLevel, "Streamout: streamId[%d],setStreamName string is null",id);
                	}

                	CSIO_LOG(m_debugLevel, "Streamout: STREAMOUT_EVENT_JNI_CMD_STREAM_NAME done.");
                	break;
                }
                case STREAMOUT_EVENT_JNI_CMD_SNAPSHOT_NAME:
                {
                	if (m_streamoutMode != STREAMOUT_MODE_CAMERA) { // short circuit - not applicable for WirelessConferencing case
                		CSIO_LOG(m_debugLevel, "Streamout: STREAMOUT_EVENT_JNI_CMD_SNAPSHOT_NAME ignored in WirlessConferencing mode.");
                		break;
                	}
                	int id = evntQ.streamout_obj_id;
                	if( evntQ.buf_size && evntQ.buffPtr)
                	{
                		CSIO_LOG(m_debugLevel, "Streamout: call setSnapshotName streamId[%d],snapshotName[%s]",
                				id,evntQ.buffPtr);

                		//save for this project
                		strcpy(m_snapshot_name, (char*)evntQ.buffPtr);

                		//validate, for now we can only do one stream
                		if( IsValidStreamOut(id) )
                		{
                			//for now we can only do one stream
                			if(m_StreamoutTaskObjList)
                			{
                				if(m_StreamoutTaskObjList[id])
                				{
                					m_StreamoutTaskObjList[id]->setSnapshotName(m_snapshot_name);
                				}
                			}
                		}
                		else
                		{
                			CSIO_LOG(eLogLevel_error, "Streamout: obj ID is invalid = %d",id);
                		}

                		m_projEventQ->del_Q_buf(evntQ.buffPtr);
                	}
                	else
                	{
                		CSIO_LOG(m_debugLevel, "Streamout: streamId[%d],setSnapshotName string is null",id);
                	}

                	CSIO_LOG(m_debugLevel, "Streamout: STREAMOUT_EVENT_JNI_CMD_SNAPSHOT_NAME done.");
                	break;
                }

                case STREAMOUT_EVENT_JNI_CMD_START_PREVIEW:
                {
                	if (m_streamoutMode != STREAMOUT_MODE_CAMERA) { // short circuit - not applicable for WirelessConferencing case
                		CSIO_LOG(m_debugLevel, "Streamout: STREAMOUT_EVENT_JNI_CMD_START_PREVIEW ignored in WirlessConferencing mode.");
                		break;
                	}
                	int id = evntQ.streamout_obj_id;
                	if( evntQ.buf_size )
                	{
                		CSIO_LOG(m_debugLevel, "Streamout: call startPreview streamId[%d],native window[%x]",
                				id,evntQ.buffPtr);

                		if( IsValidStreamOut(id) )
                		{
                			if(m_StreamoutTaskObjList)
                			{
								if(m_StreamoutTaskObjList[id])
								{
									CSIO_LOG(m_debugLevel, "Streamout: START_PREVIEW already exist[0x%x].",
											m_StreamoutTaskObjList[id]);
								}
								else
								{
									CSIO_LOG(eLogLevel_error, "Streamout: START_PREVIEW doesn't exist [0x%x]", 
											m_StreamoutTaskObjList[id]);
								
									m_StreamoutTaskObjList[id] = new CStreamoutManager(m_streamoutMode);
								
									m_StreamoutTaskObjList[id]->setParent(this);
									m_StreamoutTaskObjList[id]->setServManagerDebugLevel(m_debugLevel);
								
									//init default variables
									m_StreamoutTaskObjList[id]->setPort(m_rtsp_port);
									m_StreamoutTaskObjList[id]->setResX(m_res_x);
									m_StreamoutTaskObjList[id]->setResY(m_res_y);
									m_StreamoutTaskObjList[id]->setFrameRate(m_frame_rate);
									m_StreamoutTaskObjList[id]->setBitRate(m_bit_rate);
									m_StreamoutTaskObjList[id]->setIFrameInterval(m_iframe_interval);
									m_StreamoutTaskObjList[id]->setMulticastEnable(&m_multicast_enable);
									m_StreamoutTaskObjList[id]->setMulticastAddress(m_multicast_address);
									m_StreamoutTaskObjList[id]->setStreamName(m_stream_name);
									m_StreamoutTaskObjList[id]->setSnapshotName(m_snapshot_name);
									m_StreamoutTaskObjList[id]->setSecurityEnable(m_security_enabled);
								
									CSIO_LOG(eLogLevel_error, "Streamout: START_PREVIEW CreateNewThread()");
									m_StreamoutTaskObjList[id]->CreateNewThread();
								}
                				StartPreview((void*) evntQ.buffPtr);
                			}
                		}
                		else
                		{
                			CSIO_LOG(eLogLevel_error, "Streamout: obj ID is invalid = %d",id);
                		}

                		m_projEventQ->del_Q_buf(evntQ.buffPtr);
                	}
                	else
                	{
                		CSIO_LOG(m_debugLevel, "Streamout: streamId[%d],startPreview message is null",id);
                	}

                	CSIO_LOG(m_debugLevel, "Streamout: STREAMOUT_EVENT_JNI_CMD_START_PREVIEW done.");
                	break;
                }

                case STREAMOUT_EVENT_JNI_CMD_PAUSE_PREVIEW:
                {
                	if (m_streamoutMode != STREAMOUT_MODE_CAMERA) { // short circuit - not applicable for WirelessConferencing case
                		CSIO_LOG(m_debugLevel, "Streamout: STREAMOUT_EVENT_JNI_CMD_PAUSE_PREVIEW ignored in WirlessConferencing mode.");
                		break;
                	}
                	int id = evntQ.streamout_obj_id;
					CSIO_LOG(m_debugLevel, "Streamout: call pausePreview streamId[%d],native window[%x]",
							id,evntQ.buffPtr);

					if( IsValidStreamOut(id) )
					{
						if(m_StreamoutTaskObjList)
						{
							if(m_StreamoutTaskObjList[id])
							{
								m_StreamoutTaskObjList[id]->pausePreview((void*)evntQ.buffPtr);
							}
						}
					}
					else
					{
						CSIO_LOG(eLogLevel_error, "Streamout: obj ID is invalid = %d",id);
					}

					m_projEventQ->del_Q_buf(evntQ.buffPtr);

                	CSIO_LOG(m_debugLevel, "Streamout: STREAMOUT_EVENT_JNI_CMD_PAUSE_PREVIEW done.");
                	break;
                }

                case STREAMOUT_EVENT_JNI_CMD_STOP_PREVIEW:
                {
                	if (m_streamoutMode != STREAMOUT_MODE_CAMERA) { // short circuit - not applicable for WirelessConferencing case
                		CSIO_LOG(m_debugLevel, "Streamout: STREAMOUT_EVENT_JNI_CMD_STOP_PREVIEW ignored in WirlessConferencing mode.");
                		break;
                	}
                	int id = evntQ.streamout_obj_id;
					CSIO_LOG(m_debugLevel, "Streamout: call stopPreview streamId[%d],native window[%x]",
							id,evntQ.buffPtr);

					if( IsValidStreamOut(id) )
					{
						if(m_StreamoutTaskObjList)
						{
							StopPreview((void*)evntQ.buffPtr);
						}
					}
					else
					{
						CSIO_LOG(eLogLevel_error, "Streamout: obj ID is invalid = %d",id);
					}

					m_projEventQ->del_Q_buf(evntQ.buffPtr);

                	CSIO_LOG(m_debugLevel, "Streamout: STREAMOUT_EVENT_JNI_CMD_STOP_PREVIEW done.");
                	break;
                }

                case STREAMOUT_EVENT_JNI_CMD_VIDEO_CAPTURE_DEVICE:
                {
                	if (m_streamoutMode != STREAMOUT_MODE_WIRELESSCONFERENCING) { // short circuit - only applicable for WirelessConferencing case
                		CSIO_LOG(m_debugLevel, "Streamout: STREAMOUT_EVENT_JNI_CMD_VIDEO_CAPTURE_DEVICE ignored when not in WirelessConferencing mode.");
                		break;
                	}
                	int id = evntQ.streamout_obj_id;
                	if( evntQ.buf_size && evntQ.buffPtr)
                	{
                		CSIO_LOG(m_debugLevel, "Streamout: call setVideoCaptureDevice streamId[%d],videoCaptureDevice[%s]",
                				id,evntQ.buffPtr);

                		//save for this project
                		strcpy(m_video_capture_device, (char*)evntQ.buffPtr);

                		//validate, for now we can only do one stream
                		if( IsValidStreamOut(id) )
                		{
                			//for now we can only do one stream
                			if(m_StreamoutTaskObjList)
                			{
                				if(m_StreamoutTaskObjList[id])
                				{
                					m_StreamoutTaskObjList[id]->setVideoCaptureDevice(m_video_capture_device);
                				}
                			}
                		}
                		else
                		{
                			CSIO_LOG(eLogLevel_error, "Streamout: obj ID is invalid = %d",id);
                		}

                		m_projEventQ->del_Q_buf(evntQ.buffPtr);
                	}
                	else
                	{
                		CSIO_LOG(m_debugLevel, "Streamout: streamId[%d],setSnapshotName string is null",id);
                	}

                	CSIO_LOG(m_debugLevel, "Streamout: STREAMOUT_EVENT_JNI_CMD_VIDEO_CAPTURE_DEVICE done.");
                	break;
                }

                case STREAMOUT_EVENT_JNI_CMD_AUDIO_CAPTURE_DEVICE:
                {
                	if (m_streamoutMode != STREAMOUT_MODE_WIRELESSCONFERENCING) { // short circuit - only applicable for WirelessConferencing case
                		CSIO_LOG(m_debugLevel, "Streamout: STREAMOUT_EVENT_JNI_CMD_AUDIO_CAPTURE_DEVICE ignored when not in WirelessConferencing mode.");
                		break;
                	}
                	int id = evntQ.streamout_obj_id;
                	if( evntQ.buf_size && evntQ.buffPtr)
                	{
                		CSIO_LOG(m_debugLevel, "Streamout: call setAudioCaptureDevice streamId[%d],audioCaptureDevice[%s]",
                				id,evntQ.buffPtr);

                		//save for this project
                		strcpy(m_audio_capture_device, (char*)evntQ.buffPtr);

                		//validate, for now we can only do one stream
                		if( IsValidStreamOut(id) )
                		{
                			//for now we can only do one stream
                			if(m_StreamoutTaskObjList)
                			{
                				if(m_StreamoutTaskObjList[id])
                				{
                					m_StreamoutTaskObjList[id]->setAudioCaptureDevice(m_audio_capture_device);
                				}
                			}
                		}
                		else
                		{
                			CSIO_LOG(eLogLevel_error, "Streamout: obj ID is invalid = %d",id);
                		}

                		m_projEventQ->del_Q_buf(evntQ.buffPtr);
                	}
                	else
                	{
                		CSIO_LOG(m_debugLevel, "Streamout: streamId[%d],setAudioCaptureDevice string is null",id);
                	}

                	CSIO_LOG(m_debugLevel, "Streamout: STREAMOUT_EVENT_JNI_CMD_AUDIO_CAPTURE_DEVICE done.");
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

        if(m_forceThreadExit)
        {
            CSIO_LOG(m_debugLevel, "Streamout: m_projectID ID:%d, forced exit\n",m_projectID);
            //TODO: exit all child thread and wait here
            break;
        }
        else if(eventReceived)
        {
            //check to see if we need to restart task
            restartStreamoutIfMainLoopEnded();
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
                case STREAMOUT_EVENT_JNI_CMD_PORT:
                case STREAMOUT_EVENT_JNI_CMD_RES_X:
                case STREAMOUT_EVENT_JNI_CMD_RES_Y:
                case STREAMOUT_EVENT_JNI_CMD_FRAMERATE:
                case STREAMOUT_EVENT_JNI_CMD_BITRATE:
                case STREAMOUT_EVENT_JNI_CMD_IFRAMEINTERVAL:
				case STREAMOUT_EVENT_JNI_CMD_MULTICAST_ADDRESS:
				case STREAMOUT_EVENT_JNI_CMD_STREAM_NAME:
				case STREAMOUT_EVENT_JNI_CMD_SNAPSHOT_NAME:
				case STREAMOUT_EVENT_JNI_CMD_VIDEO_CAPTURE_DEVICE:
				case STREAMOUT_EVENT_JNI_CMD_AUDIO_CAPTURE_DEVICE:
                {
                    evntQ.buffPtr = new char [dataSize + 1];//data might be binary
                    if(evntQ.buffPtr)
                    {
                        //copy data
                        char* destPtr = (char*)evntQ.buffPtr;
                        memcpy(destPtr,(char*)bufP,dataSize);
                        destPtr[dataSize] = 0;//terminate string
                        evntQ.buf_size = dataSize;
                    }
                    else
                    {
                        CSIO_LOG(eLogLevel_warning, "Streamout: create buffer failed\n");
                    }
                    break;
                }
				case STREAMOUT_EVENT_JNI_CMD_ENABLE_MULTICAST:
				{
					evntQ.buffPtr = new int(0);
					if(evntQ.buffPtr)
		            {
						int * destPtr = (int *)evntQ.buffPtr;
						memcpy(destPtr, (int*)bufP, dataSize);
						evntQ.buf_size = dataSize;
					}
			        else
			        {
			            CSIO_LOG(eLogLevel_warning, "Streamout: create buffer failed\n");
			        }
					break;
				}
				case STREAMOUT_EVENT_JNI_CMD_ENABLE_SECURITY:
				{
					evntQ.buffPtr = new int(0);
					if(evntQ.buffPtr)
		            {
						int * destPtr = (int *)evntQ.buffPtr;
						memcpy(destPtr, (int*)bufP, dataSize);
						evntQ.buf_size = dataSize;
					}
			        else
			        {
			            CSIO_LOG(eLogLevel_warning, "Streamout: create buffer failed\n");
			        }
					break;
				}

				case STREAMOUT_EVENT_JNI_CMD_START_PREVIEW:
				case STREAMOUT_EVENT_JNI_CMD_PAUSE_PREVIEW:
				case STREAMOUT_EVENT_JNI_CMD_STOP_PREVIEW:
				{
					evntQ.buffPtr = new (void *);
					if(evntQ.buffPtr)
		            {
						evntQ.buffPtr  = bufP;
						evntQ.buf_size = dataSize;
					}
			        else
			        {
			            CSIO_LOG(eLogLevel_warning, "Streamout: create buffer failed\n");
			        }
					break;
				}
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
                    CSIO_LOG(m_debugLevel, "Streamout: Wait for task[%d] threads DONE.\n", i);
                    CSIO_LOG(eLogLevel_warning, "Streamout: task[%d] try to restart a new stream out thread.\n", i);
                    //as long as this object exist, restart it
                    int ret = m_StreamoutTaskObjList[i]->CreateNewThread();

                    CSIO_LOG(eLogLevel_warning, "Streamout: ret[%d]. restarts a new stream out thread for task[%d].\n", ret, i);
                }
            }
        }
    }
}
void CStreamoutProject::removeAllStreamoutTasks()
{
    CSIO_LOG(eLogLevel_info, "Streamout(%s): entered\n",__FUNCTION__);
    if(m_StreamoutTaskObjList)
    {
        CSIO_LOG(eLogLevel_info, "Streamout(%s): loop start\n",__FUNCTION__);
        for(int i = 0; i < MAX_STREAM_OUT; i++)
        {
            if(m_StreamoutTaskObjList[i])
            {
                CSIO_LOG(eLogLevel_info, "Streamout(%s): Handling m_StreamoutTaskObjList[%d]\n",__FUNCTION__, i);
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

        CSIO_LOG(eLogLevel_info, "Streamout(%s): loop end\n",__FUNCTION__);

        CSIO_LOG(eLogLevel_info, "Streamout(%s): delete m_StreamoutTaskObjList array\n",__FUNCTION__);
        delete [] m_StreamoutTaskObjList;
        CSIO_LOG(eLogLevel_info, "Streamout(%s): set m_StreamoutTaskObjList to null\n",__FUNCTION__);
        m_StreamoutTaskObjList = NULL;
    }
    CSIO_LOG(eLogLevel_info, "Streamout(%s): exit\n",__FUNCTION__);
}
void CStreamoutProject::setProjectDebugLevel(int level)
{
    setDebugLevel(level);
}
