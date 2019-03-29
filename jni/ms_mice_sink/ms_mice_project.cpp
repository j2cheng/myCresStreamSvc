/**
 * Copyright (C) 2019 to the present, Crestron Electronics, Inc.
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
 * \date        3/7/2019
 *
 * \note
 *
 *
 * \todo
 */

#include "ms_mice_common.h"
#include "ms_mice_project.h"
#include "ms-mice.h"

static msMiceSinkProjClass* g_msMiceSinkProjPtr = NULL;
static Mutex gProjectsLock;
static int gProjectDebug = eLogLevel_debug;

const MSMICE_STRNUMPAIR ms_mice_proj_timestamp_names[] =
{
    {"ms mice sink project init time " ,   MS_MICE_SINK_PROJ_TIMESTAMP_INIT},
    {"ms mice sink project start time",   MS_MICE_SINK_PROJ_TIMESTAMP_START},
    {"ms mice sink project stop time ",    MS_MICE_SINK_PROJ_TIMESTAMP_STOP},

    {0,0}//terminate the list
};

int session_observer_disconnect_request_from_app(gpointer user_data);

/*************************** Global functions  ************************************/
void msMiceSinkProjInit(char* adapterAddress)
{
    gProjectsLock.lock();

    //create project object now
    if(g_msMiceSinkProjPtr == NULL)
    {
        g_msMiceSinkProjPtr = new msMiceSinkProjClass(adapterAddress);
        CSIO_LOG(gProjectDebug, "msMiceSinkProjInit: g_msMiceSinkProjPtr:0x%x\n",g_msMiceSinkProjPtr);
    }

    //run all threads
    if(g_msMiceSinkProjPtr && !g_msMiceSinkProjPtr->m_ThreadIsRunning)
    {
        g_msMiceSinkProjPtr->CreateNewThread("MSMICE_PROJ0",NULL);
    }

    gProjectsLock.unlock();

    return ;
}

void msMiceSinkProjDeInit()
{
    if(g_msMiceSinkProjPtr)
    {
        g_msMiceSinkProjPtr->exitThread();

        //wait until thread exits
        CSIO_LOG(gProjectDebug, "msMiceSinkProjDeInit: call WaitForThreadToExit[0x%x]\n",g_msMiceSinkProjPtr);
        g_msMiceSinkProjPtr->WaitForThreadToExit();
        CSIO_LOG(gProjectDebug, "msMiceSinkProjDeInit: Wait is done\n");

        //delete the object, and set list to NULL
        gProjectsLock.lock();

        delete g_msMiceSinkProjPtr;
        g_msMiceSinkProjPtr = NULL;

        gProjectsLock.unlock();

        CSIO_LOG(gProjectDebug, "msMiceSinkProjDeInit: delete HYDRGNProjObjPtr is DONE\n");
    }
    else
    {
        CSIO_LOG(gProjectDebug, "msMiceSinkProjDeInit: no g_msMiceSinkProjPtrcreated.\n");
    }
}

void msMiceSinkProjStopSession(int id, gint64 session_id)
{
    if(!session_id) return;

    CSIO_LOG(gProjectDebug, "msMiceSinkProjStopSession: enter: id[%d], session_id[%d]",id,session_id);
    gProjectsLock.lock();

    if(g_msMiceSinkProjPtr)
    {
        if(g_msMiceSinkProjPtr->m_service_obj && g_msMiceSinkProjPtr->m_service_obj->m_mice_service)
        {
            GMainContext* context = ms_mice_sink_service_get_context(g_msMiceSinkProjPtr->m_service_obj->m_mice_service);

            ms_mice_sink_service_and_sessionid* cmd = new ms_mice_sink_service_and_sessionid();
            if(cmd)
            {
                cmd->service = g_msMiceSinkProjPtr->m_service_obj->m_mice_service;
                cmd->session_id  = session_id;
                cmd->session_pin = NULL;
                g_main_context_invoke(context,session_observer_disconnect_request_from_app,cmd);

                //Note: cmd will be deleted in session_observer_disconnect_request_from_app
            }
            else
            {
                CSIO_LOG(gProjectDebug, "msMiceSinkProjClass: failed to create request for[%lld].\n",session_id);
            }
        }
    }
    else
    {
        CSIO_LOG(gProjectDebug, "msMiceSinkProjStopSession: no g_msMiceSinkProjPtr is running\n");
    }

    gProjectsLock.unlock();
    CSIO_LOG(gProjectDebug, "msMiceSinkProjStopSession: return.");
}

void msMiceSinkProjSetPin(int id,char* pin)
{
    CSIO_LOG(gProjectDebug, "msMiceSinkProjSetPin: enter: id[%d]",id);
    gProjectsLock.lock();

    if(g_msMiceSinkProjPtr)
    {
        csioEventQueueStruct EvntQ;
        memset(&EvntQ,0,sizeof(csioEventQueueStruct));
        EvntQ.obj_id = 0;
        EvntQ.event_type = MS_MICE_SINK_EVENTS_SET_PIN;

        if(pin)
        {
            EvntQ.buf_size   = strlen(pin);
            EvntQ.buffPtr    = pin;
            CSIO_LOG(gProjectDebug, "msMiceSinkProjSetPin: pin:[%s]",pin);
        }

        g_msMiceSinkProjPtr->sendEvent(&EvntQ);
    }
    else
    {
        CSIO_LOG(gProjectDebug, "msMiceSinkProjSetPin: no g_msMiceSinkProjPtr is running\n");
    }

    gProjectsLock.unlock();
    CSIO_LOG(gProjectDebug, "msMiceSinkProjSetPin: return.");
}

/***************************** start of ms-mice static interface functions ****
 *  Note: this session must run under one thread,
 *  currently is in msMiceSinkServiceClass::ThreadEntry()
 *  ***************************************************************************/
/* ------------------------------------------------------------------------------------------------------------------
 * -- ms-mice extensions
 * -- */

static void app_extension_ms_mice_session_observer_on_disconnected(ms_mice_sink_session *ms_session, gpointer data)
{
    guint64 id = ms_mice_sink_session_get_id(ms_session);
    CSIO_LOG(eLogLevel_debug,"app.ms-mice.session.event.disconnected { \"session-id\": %" G_GUINT64_FORMAT " }", id);

    ms_mice_sink_session_observer_detach(ms_session);
}

static void app_extension_ms_mice_session_observer_on_state_changed(ms_mice_sink_session *session, MS_MICE_SINK_SESSION_STATE from, MS_MICE_SINK_SESSION_STATE to, gpointer data)
{
    CSIO_LOG(eLogLevel_debug,"app.ms-mice.session.event.state { \"session-id\": %"G_GUINT64_FORMAT" , \"from\": \"%s\" , \"to\": \"%s\" }", ms_mice_sink_session_get_id(session), ms_mice_sink_session_state_to_string(from), ms_mice_sink_session_state_to_string(to));
}

static void app_extension_ms_mice_session_observer_on_source_id_set(ms_mice_sink_session *session, const char *source_id, gpointer data)
{
    CSIO_LOG(eLogLevel_debug,"app.ms-mice.session.event.source-id { \"session-id\": %"G_GUINT64_FORMAT" , \"source-id\": \"%s\" }", ms_mice_sink_session_get_id(session), source_id);
}

static void app_extension_ms_mice_session_observer_on_friendly_name_set(ms_mice_sink_session *session, const char *friendly_name, gpointer data)
{
    CSIO_LOG(eLogLevel_debug,"app.ms-mice.session.event.friendly-name { \"session-id\": %"G_GUINT64_FORMAT" , \"friendly-name\": \"%s\" }", ms_mice_sink_session_get_id(session), friendly_name);
}

static void app_extension_ms_mice_session_observer_on_source_ready(ms_mice_sink_session *ms_session, guint16 rtsp_port, gpointer data)
{
    CSIO_LOG(eLogLevel_debug,"app.ms-mice.session.event.source-ready { \"session-id\": %"G_GUINT64_FORMAT" , \"rtsp-port\": %u }", ms_mice_sink_session_get_id(ms_session), rtsp_port);

    //TODO: emit_source_ready to java
    Wfd_ms_mice_signal_raise (
            ms_mice_sink_session_get_id(ms_session),
            1,
            (char*)ms_mice_sink_session_get_source_id(ms_session),
            (char*)ms_mice_sink_session_get_friendly_name(ms_session),
            (char*)ms_mice_sink_session_get_remote_address(ms_session),
            rtsp_port);
}

static void app_extension_ms_mice_session_observer_on_stop_projection(ms_mice_sink_session *ms_session, gpointer data)
{
    CSIO_LOG(eLogLevel_debug,"app.ms-mice.session.event.stop-projection { \"session-id\": %"G_GUINT64_FORMAT" }", ms_mice_sink_session_get_id(ms_session));

    //TODO: emit_stop_projecting to java
    Wfd_ms_mice_signal_raise (
            ms_mice_sink_session_get_id(ms_session),
            0,
            (char*)ms_mice_sink_session_get_source_id(ms_session),
            (char*)ms_mice_sink_session_get_friendly_name(ms_session),
            (char*)ms_mice_sink_session_get_remote_address(ms_session),
            0);
}

ms_mice_sink_session_observer app_extension_ms_mice_session_observer = {
        app_extension_ms_mice_session_observer_on_disconnected,
        app_extension_ms_mice_session_observer_on_state_changed,
        app_extension_ms_mice_session_observer_on_source_id_set,
        app_extension_ms_mice_session_observer_on_friendly_name_set,
        app_extension_ms_mice_session_observer_on_source_ready,
        app_extension_ms_mice_session_observer_on_stop_projection
};


/* ------------------------------------------------------------------------------------------------------------------
 * -- ms-mice sink service observer
 * -- */

static void app_extension_ms_mice_service_observer_on_started(ms_mice_sink_service *service, gpointer data)
{
    CSIO_LOG(eLogLevel_debug,"app.ms-mice.service.event.started { }");
}

static void app_extension_ms_mice_service_observer_on_stopped(ms_mice_sink_service *service, gpointer data)
{
    CSIO_LOG(eLogLevel_debug,"app.ms-mice.service.event.stopped { }");
}

//Note: this function replaced app_extension_api_session_observer_disconnect_ms_mice_request()
//Note: expect project keep track of session_id(string) to ms_session(ms_mice_sink_session*)
int session_observer_disconnect_request_from_app(gpointer user_data)
{
    ms_mice_sink_service_and_sessionid* cmd = (ms_mice_sink_service_and_sessionid*)user_data;

    if(cmd)
    {
        CSIO_LOG(eLogLevel_debug,"session_observer_disconnect_request_from_app session_id[%lld]", cmd->session_id);

        if(cmd->service)
        {
            ms_mice_sink_session *ms_session = ms_mice_sink_service_find_session_by_id(cmd->service,cmd->session_id);
            CSIO_LOG(eLogLevel_debug,"session_observer_disconnect_request_from_app ms_session[0x%x]", ms_session);

            if(ms_session)
            {
                ms_mice_sink_session_observer_detach(ms_session);

                //Note: this function will also call ms_mice_sink_session_free()
                ms_mice_sink_session_close(ms_session);

                //ms_mice_sink_session_free(ms_session);
            }
            else
            {
                CSIO_LOG(eLogLevel_debug,"session_observer_disconnect_request_from_app could not find session.");
            }
        }
        else
        {
            CSIO_LOG(eLogLevel_debug,"session_observer_disconnect_request_from_app cmd->service is NULL");
        }

        delete cmd;
    }
    else
    {
        CSIO_LOG(eLogLevel_debug,"session_observer_disconnect_request_from_app cmd is NULL");
    }

    CSIO_LOG(eLogLevel_debug,"session_observer_disconnect_request_from_app return");
    return 0;
}

static void app_extension_ms_mice_service_observer_on_session_connected(ms_mice_sink_service *service, ms_mice_sink_session *ms_session, gpointer data)
{
    g_autoptr(GError) error = NULL;
    CSIO_LOG(eLogLevel_debug,"app.ms-mice.service.event.session-connected { \"session-id\": %"G_GUINT64_FORMAT" }", ms_mice_sink_session_get_id(ms_session));

    ms_mice_sink_session_observer_attach(ms_session, &app_extension_ms_mice_session_observer, NULL);
}

ms_mice_sink_service_observer ctl_fn_ms_mice_sink_service_observer = {
        app_extension_ms_mice_service_observer_on_started,
        app_extension_ms_mice_service_observer_on_stopped,
        app_extension_ms_mice_service_observer_on_session_connected
};


int app_extension_ms_mice_service_set_session_pin(gpointer user_data)
{
    ms_mice_sink_service_and_sessionid* cmd = (ms_mice_sink_service_and_sessionid*)user_data;
    ms_mice_sink_service *s = NULL;

    if(cmd)
    {
        CSIO_LOG(eLogLevel_debug,"app_extension_ms_mice_service_set_session_pin: service[0x%x]",cmd->service);

        if(cmd->service)
        {
            if(cmd->session_pin)
            {
                ms_mice_sink_service_set_session_pin(cmd->service,cmd->session_pin);

                CSIO_LOG(eLogLevel_debug,"app_extension_ms_mice_service_set_session_pin: get_session_pin[0x%x]",
                         ms_mice_sink_service_get_session_pin(cmd->service));
            }//else

            CSIO_LOG(eLogLevel_debug,"app_extension_ms_mice_service_set_session_pin %s",cmd->session_pin? cmd->session_pin : "NULL");
        }
        else
        {
            CSIO_LOG(eLogLevel_debug,"app_extension_ms_mice_service_set_session_pin cmd->service is NULL");
        }

        g_free((gpointer)cmd->session_pin);
        delete cmd;
    }
    else
    {
        CSIO_LOG(eLogLevel_debug,"app_extension_ms_mice_service_set_session_pin cmd is NULL");
    }
}
/* ------------------------------------------------------------------------------------------------------------------
 * -- load | unload extensions
 * -- */

static void app_extension_ms_mice_unload(msMiceSinkServiceClass *p_servceClass)
{
    if(p_servceClass && p_servceClass->m_mice_service)
    {
        CSIO_LOG(eLogLevel_debug,"app.ms-mice.unload projPtr->m_mice_service[0x%x]",p_servceClass->m_mice_service);

        ms_mice_sink_service_stop(p_servceClass->m_mice_service);
        ms_mice_sink_service_observer_detach(p_servceClass->m_mice_service);
        ms_mice_sink_service_free(p_servceClass->m_mice_service);
        p_servceClass->m_mice_service = NULL;

        CSIO_LOG(eLogLevel_debug,"app.ms-mice.unload exit");
    }
}

static void app_extension_ms_mice_load(msMiceSinkServiceClass* p_servceClass,GError **error)
{
    if(p_servceClass == NULL || error == NULL)
    {
        CSIO_LOG(eLogLevel_debug,"app.ms-mice.load p_servceClass is NULL");
        return;
    }

    g_autoptr(GError) internal_error = NULL;

    if(p_servceClass->m_parent->m_adapterAddress.size())
    {
        ms_mice_sink_service_new(&p_servceClass->m_mice_service,
                                 p_servceClass->m_parent->getadapterAddress(),
                                 DEFAULT_MIRACAST_OVER_INFRASTRUCTURE_PORT,
                                 p_servceClass->m_parent->getSessionPin(),
                                 &internal_error);
    }
    else
    {
        ms_mice_sink_service_new(&p_servceClass->m_mice_service,
                                 "0.0.0.0",
                                 DEFAULT_MIRACAST_OVER_INFRASTRUCTURE_PORT,
                                 p_servceClass->m_parent->getSessionPin(),
                                 &internal_error);
    }

    if (internal_error || !p_servceClass->m_mice_service)
        goto cleanup_error;

    ms_mice_sink_service_observer_attach(p_servceClass->m_mice_service, &ctl_fn_ms_mice_sink_service_observer, NULL);

    if (internal_error)
        goto cleanup_error;

    return;

cleanup_error:
    CSIO_LOG(eLogLevel_error,"app.ms-mice.load.error { \"code\": %d , \"message\": \"%s\" }", (internal_error ? internal_error->code : -1), (internal_error ? internal_error->message : "unknown"));
    app_extension_ms_mice_unload(p_servceClass);
    if (error) {
        *error = internal_error;
        internal_error = NULL;
    }
}
/***************************** end of ms-mice static interface functions **************************************/

/***************************** msMiceSinkProjClass class **************************************/
msMiceSinkProjClass::msMiceSinkProjClass(char* adapterAddr):
msMiceSinkProjTimeArray(NULL),
m_service_obj(NULL),
m_pinStr(),
m_adapterAddress(),
m_projEventQList(NULL)
{
    m_debugLevel = gProjectDebug;
    m_projEventQList = new csioEventQueueListBase(CSIO_DEFAULT_QUEUE_SIZE);

    msMiceSinkProjTimeArray = new csioTimerClockBase(MS_MICE_SINK_PROJ_TIMESTAMP_MAX,MS_MICE_SINK_PROJ_TIMER_MAX);

    if(msMiceSinkProjTimeArray)
        msMiceSinkProjTimeArray->recordEventTimeStamp(MS_MICE_SINK_PROJ_TIMESTAMP_INIT);

    m_adapterAddress.clear();
    m_pinStr.clear();

    if(adapterAddr)
    {
        std::string strAddr = (char*)adapterAddr;
        setadapterAddress(strAddr);
    }
}
msMiceSinkProjClass::~msMiceSinkProjClass()
{
    CSIO_LOG(eLogLevel_info, "msMiceSinkProjClass::~msMiceSinkProjClass\n");
}

void msMiceSinkProjClass::exitThread()
{
    m_forceThreadExit = 1;

    csioEventQueueStruct evntQ;
    memset(&evntQ,0,sizeof(csioEventQueueStruct));
    sendEvent(&evntQ);

    CSIO_LOG(ABOVE_DEBUG_VERB(m_debugLevel), "msMiceSinkProjDeInit: exitThread[%d] sent.\n", m_forceThreadExit);
}

void msMiceSinkProjClass::DumpClassPara(int id)
{
    CSIO_LOG(eLogLevel_info, "msMiceSinkProjClass: threadObjLoopCnt:   %d.\n",m_threadObjLoopCnt);

    CSIO_LOG(eLogLevel_info, "msMiceSinkProjClass: ThreadId            0x%x\n", (unsigned long int)getThredId());
    CSIO_LOG(eLogLevel_info, "msMiceSinkProjClass: m_debugLevel        %d\n", m_debugLevel);
    CSIO_LOG(eLogLevel_info, "msMiceSinkProjClass: m_ThreadIsRunning   %d\n", m_ThreadIsRunning);
    CSIO_LOG(eLogLevel_info, "msMiceSinkProjClass: m_service_obj       0x%x\n", m_service_obj);
    CSIO_LOG(eLogLevel_info, "msMiceSinkProjClass: m_pinStr            %s\n", m_pinStr.c_str());
    CSIO_LOG(eLogLevel_info, "msMiceSinkProjClass: m_adapterAddress    %s\n", m_adapterAddress.c_str());

    if(m_projEventQList)
    {
        CSIO_LOG(eLogLevel_info, "msMiceSinkProjClass: Event queued up     %d\n", m_projEventQList->GetEvntQueueCount());
    }

    if(msMiceSinkProjTimeArray)
    {
        char  time_string[40];
        long  milliseconds;

        for(int i = 0; i < MS_MICE_SINK_PROJ_TIMESTAMP_MAX; i++)
        {
            msMiceSinkProjTimeArray->convertTime(i,time_string,40,milliseconds);
            CSIO_LOG(eLogLevel_info, "msMiceSinkProjClass: %s:  %s.%03ld\n",
                     ms_mice_proj_timestamp_names[i].pStr, time_string, milliseconds);
        }
    }
}

void msMiceSinkProjClass::sendEvent(csioEventQueueStruct* pEvntQ)
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

        void* bufP = pEvntQ->buffPtr;
        int dataSize = pEvntQ->buf_size;

        CSIO_LOG(ABOVE_DEBUG_VERB(m_debugLevel), "msMiceSinkProjClass::sendEvent: iId[%d],evnt[%d],dataSize[%d],bufP[0x%x]\n",\
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
                        CSIO_LOG(eLogLevel_error, "msMiceSinkProjClass::sendEvent: create buffer failed\n");
                    }

                    break;
                }
            }
        }
        //else

        if(m_projEventQList)
            m_projEventQList->EnqueueAndSignal(evntQ);

        CSIO_LOG(ABOVE_DEBUG_VERB(m_debugLevel), "msMiceSinkProjClass::sendEvent[%d]: event added to the queue[0x%x].\n",evntQ.obj_id,evntQ.buffPtr);
        CSIO_LOG(ABOVE_DEBUG_VERB(m_debugLevel), "msMiceSinkProjClass::sendEvent[%d]: event[%d] sent.\n",evntQ.obj_id,evntQ.event_type);
    }
    else
    {
        CSIO_LOG(m_debugLevel, "msMiceSinkProjClass::sendEvent: pEvntQ is NULL\n");
    }
}
void* msMiceSinkProjClass::ThreadEntry()
{
    CSIO_LOG(ABOVE_DEBUG_VERB(m_debugLevel), "msMiceSinkProjClass: Enter ThreadEntry.\n");

    int wtRtn  = 0;
    csioEventQueueStruct* evntQPtr = NULL;

    //log thread init. time stamp
    if(msMiceSinkProjTimeArray)
        msMiceSinkProjTimeArray->recordEventTimeStamp(MS_MICE_SINK_PROJ_TIMESTAMP_START);

    if(!m_projEventQList)
    {
        CSIO_LOG(eLogLevel_error, "msMiceSinkProjClass::m_projEventQList is NULL!\n");
        m_ThreadIsRunning = 0;
        return NULL;
    }

    if(m_service_obj == NULL)
    {
        m_service_obj = new msMiceSinkServiceClass(this);

        if (!m_service_obj)
        {
            CSIO_LOG(eLogLevel_error, "msMiceSinkProjClass::create m_service_obj failed!\n");
            m_ThreadIsRunning = 0;
            return NULL;
        }
        else
        {
            m_service_obj->CreateNewThread("MSMICE_SRV0",NULL);

            //Note: to make sure ms mice service is set before processing any commands below:
            //      such as set pin.
            for(int i = 0; i < 10 ; i++)
            {
                if(m_service_obj->m_mainLoop)
                {
                    CSIO_LOG(m_debugLevel, "msMiceSinkProjClass:: m_service_obj->m_mainLoop is set.\n");
                    break;
                }
                else
                {
                    CSIO_LOG(m_debugLevel, "msMiceSinkProjClass:: waiting for m_service_obj->m_mainLoop!\n");
                    usleep(50000);//wait for 50ms
                }
            }
        }
    }

    for(;;)
    {
        m_threadObjLoopCnt++;

        wtRtn  = m_projEventQList->waitMsgQueueSignal(MSMICE_PROJ_EVNT_POLL_SLEEP_MS);

        CSIO_LOG(ABOVE_DEBUG_XTRVERB(m_debugLevel), "msMiceSinkProjClass: waitMsgQueueSignal return:%d, m_threadObjLoopCnt[%d]\n",wtRtn,m_threadObjLoopCnt);

        evntQPtr = NULL;

        if(m_projEventQList->GetFromQueueList(&evntQPtr) && evntQPtr)
        {
            CSIO_LOG(ABOVE_DEBUG_VERB(m_debugLevel), "msMiceSinkProjClass: evntQ is:size[%d],type[%d],iId[%d],GetEvntQueueCount[%d]\n",\
                            evntQPtr->buf_size,evntQPtr->event_type,evntQPtr->obj_id,m_projEventQList->GetEvntQueueCount());

            switch (evntQPtr->event_type)
            {
                case MS_MICE_SINK_EVENTS_SET_PIN:
                {
                    CSIO_LOG(m_debugLevel, "msMiceSinkProjClass[%d]: MS_MICE_SINK_EVENTS_SET_PIN,buf_size[%d]\n",
                             evntQPtr->obj_id,evntQPtr->buf_size);

                    m_pinStr.clear();
                    if( evntQPtr->buf_size && evntQPtr->buffPtr)
                    {

                        m_pinStr = (char*)evntQPtr->buffPtr;
                        deleteCharArray(evntQPtr->buffPtr);

                        CSIO_LOG(m_debugLevel, "msMiceSinkProjClass: MS_MICE_SINK_EVENTS_SET_PIN,set pin[%s]\n",m_pinStr.c_str());
                    }//else

                    //send it to m_mice_service context
                    if(m_service_obj->m_mice_service)
                    {
                        GMainContext* context = ms_mice_sink_service_get_context(m_service_obj->m_mice_service);

                        ms_mice_sink_service_and_sessionid* cmd = new ms_mice_sink_service_and_sessionid();
                        if(cmd)
                        {
                            cmd->service = m_service_obj->m_mice_service;

                            if(m_pinStr.size())
                            {
                                cmd->session_pin = g_strdup(m_pinStr.c_str());
                                CSIO_LOG(m_debugLevel, "msMiceSinkProjClass: MS_MICE_SINK_EVENTS_SET_PIN cmd->session_pin[0x%x][%d]\n",
                                         cmd->session_pin,strlen(cmd->session_pin));
                            }
                            else
                            {
                                cmd->session_pin = NULL;
                            }
                            g_main_context_invoke(context,app_extension_ms_mice_service_set_session_pin,cmd);

                            //Note: cmd will be deleted in app_extension_ms_mice_service_set_session_pin
                        }//else
                    }
                    else
                    {
                        CSIO_LOG(m_debugLevel, "msMiceSinkProjClass[%d]: m_service_obj->m_mice_service is NULL, pin lost.\n");
                    }
                    break;
                }
                case MS_MICE_SINK_EVENTS_JNI_NOP:
                {
                    CSIO_LOG(m_debugLevel, "msMiceSinkProjClass: MS_MICE_SINK_EVENTS_JNI_NOP\n");
                    break;
                }
                default:
                {
                    CSIO_LOG(m_debugLevel, "msMiceSinkProjClass: unknown type[%d].\n",evntQPtr->event_type);
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

        CSIO_LOG(ABOVE_DEBUG_XTRVERB(m_debugLevel), "msMiceSinkProjClass loop back\n");
    }

    if(m_service_obj)
    {
        m_service_obj->exitThread();

        //wait until thread exits
        CSIO_LOG(m_debugLevel, "msMiceSinkProjClass: call WaitForThreadToExit[0x%x]\n",m_service_obj);
        m_service_obj->WaitForThreadToExit();
        CSIO_LOG(m_debugLevel, "msMiceSinkProjClass: Wait is done\n");

        delete m_service_obj;
        m_service_obj = NULL;
    }

    CSIO_LOG(m_debugLevel, "msMiceSinkProjClass: exiting...\n");

    //thread exit here
    m_ThreadIsRunning = 0;

    return NULL;
}
/***************************** end of msMiceSinkProjClass class **************************************/

/***************************** msMiceSinkServceClass class **************************************/
msMiceSinkServiceClass::msMiceSinkServiceClass(msMiceSinkProjClass* m_parent):
msMiceSinkSevTimeArray(NULL),
m_mice_service(NULL),
m_mainLoop(NULL),
m_parent(m_parent)
{
    m_debugLevel = gProjectDebug;

    msMiceSinkSevTimeArray = new csioTimerClockBase(MS_MICE_SINK_PROJ_TIMESTAMP_MAX,MS_MICE_SINK_PROJ_TIMER_MAX);

    if(msMiceSinkSevTimeArray)
        msMiceSinkSevTimeArray->recordEventTimeStamp(MS_MICE_SINK_PROJ_TIMESTAMP_INIT);
}
msMiceSinkServiceClass::~msMiceSinkServiceClass()
{
    CSIO_LOG(eLogLevel_info, "msMiceSinkServiceClass::~msMiceSinkServiceClass\n");
}

void msMiceSinkServiceClass::exitThread()
{
    m_forceThreadExit = 1;

    //main loop quit
    if(m_mainLoop)
    {
        g_main_loop_quit(m_mainLoop);
        CSIO_LOG(m_debugLevel, "msMiceSinkServiceClass: g_main_loop_quit returned\n");
    }
    else
    {
        CSIO_LOG(m_debugLevel, "msMiceSinkServiceClass: g_main_loop is not running\n");
    }
    CSIO_LOG(ABOVE_DEBUG_VERB(m_debugLevel), "msMiceSinkServiceClass: exitThread[%d] sent.\n", m_forceThreadExit);
}

void msMiceSinkServiceClass::DumpClassPara(int id)
{
    CSIO_LOG(eLogLevel_info, "msMiceSinkServiceClass: threadObjLoopCnt:   %d.\n",m_threadObjLoopCnt);

    CSIO_LOG(eLogLevel_info, "msMiceSinkServiceClass: ThreadId            0x%x\n", (unsigned long int)getThredId());
    CSIO_LOG(eLogLevel_info, "msMiceSinkServiceClass: m_debugLevel        %d\n", m_debugLevel);
    CSIO_LOG(eLogLevel_info, "msMiceSinkServiceClass: m_ThreadIsRunning   %d\n", m_ThreadIsRunning);
    CSIO_LOG(eLogLevel_info, "msMiceSinkServiceClass: m_mice_service      0x%x\n", m_mice_service);
    CSIO_LOG(eLogLevel_info, "msMiceSinkServiceClass: m_mainLoop          0x%x\n", m_mainLoop);
}

void* msMiceSinkServiceClass::ThreadEntry()
{
    CSIO_LOG(ABOVE_DEBUG_VERB(m_debugLevel), "msMiceSinkServiceClass: Enter ThreadEntry.\n");

    int wtRtn  = 0;
    csioEventQueueStruct* evntQPtr = NULL;
    GMainContext* mainLoopContext = NULL;

    //log thread init. time stamp
    if(msMiceSinkSevTimeArray)
        msMiceSinkSevTimeArray->recordEventTimeStamp(MS_MICE_SINK_PROJ_TIMESTAMP_START);

    if(m_mice_service == NULL)
    {
        g_autoptr(GError) internal_error = NULL;

        //Note: app_extension_ms_mice_unload will be called if error
        app_extension_ms_mice_load(this,&internal_error);

        if (internal_error || !m_mice_service)
        {
            CSIO_LOG(eLogLevel_error, "msMiceSinkServiceClass: calling app_extension_ms_mice_load failed!\n");
            m_ThreadIsRunning = 0;
            return NULL;
        }
        else
        {
            CSIO_LOG(m_debugLevel, "msMiceSinkServiceClass::app_extension_ms_mice_load m_mice_service[0x%x]!\n",m_mice_service);
        }
    }

    //create context, it will be used by ms_mice_sink_service_start() below.
    mainLoopContext = g_main_context_new ();

    if(mainLoopContext)
    {
        g_main_context_push_thread_default(mainLoopContext);
        m_mainLoop = g_main_loop_new (mainLoopContext, FALSE);
        if(!m_mainLoop)
        {
            CSIO_LOG(eLogLevel_error, "CresRTSP_gstappserver: Failed to create g_main_loop_new\n");
            goto exit;
        }
    }
    else
    {
        CSIO_LOG(eLogLevel_error, "msMiceSinkServiceClass::g_main_context_new failed!\n");
        goto exit;
    }


    if(m_mice_service)
    {
        g_autoptr(GError) internal_error = NULL;

        ms_mice_sink_service_start(m_mice_service, mainLoopContext, &internal_error);

        if (internal_error)
        {
            CSIO_LOG(eLogLevel_error, "msMiceSinkServiceClass::ms_mice_sink_service_start failed!\n");
            goto exit;
        }
        else
        {
            CSIO_LOG(eLogLevel_debug, "msMiceSinkServiceClass::ms_mice_sink_service_start[0x%x]\n",m_mice_service);
        }
    }

    //main loop run
    g_main_loop_run(m_mainLoop);
    CSIO_LOG(m_debugLevel,"msMiceSinkServiceClass: g_main_loop_run returned\n");

exit:
    app_extension_ms_mice_unload(this);

    if(m_mainLoop)
    {
        g_main_loop_unref (m_mainLoop);
        m_mainLoop = NULL;
    }

    //app_extension_ms_mice_unload() was called, it is save to free context now.
    if(mainLoopContext)
    {
        g_main_context_pop_thread_default(mainLoopContext);
        g_main_context_unref (mainLoopContext);
        mainLoopContext = NULL;
    }

    CSIO_LOG(m_debugLevel, "msMiceSinkServiceClass: exiting...\n");

    //thread exit here
    m_ThreadIsRunning = 0;

    return NULL;
}
/***************************** end of msMiceSinkServceClass class **************************************/
const char * const msMiceSinkProj_fieldDebugNames[] =
{
    "01 START_SESSION   " ,
    "02 STOP_SESSION    " ,
    "03 SETDEBUGLEVEL   " ,
    "04 DUMPALL         "
};

void msMiceSinkProj_printFdebugInfo()
{
    unsigned int i;

    CSIO_LOG(eLogLevel_info, "msMiceSinkProj_printFdebugInfo: FieldDebugInfo:\n");
    for (i = 0; i < WFD_NUM_OF(msMiceSinkProj_fieldDebugNames); i++)
    {
        CSIO_LOG(eLogLevel_info, "%s\n", msMiceSinkProj_fieldDebugNames[i]);
    }
}
//Note: used only for debugging, there is no lock here.
void msMiceSinkProjSetDebugLevel(int l)
{
    //No lock here
    gProjectDebug = l;

    if(g_msMiceSinkProjPtr)
    {
        g_msMiceSinkProjPtr->setDebugLevel(l);
    }

    //No lock here
}
void msMiceSinkProjDumpPara()
{
    gProjectsLock.lock();

    CSIO_LOG(eLogLevel_info, "msMiceSinkProjDumpPara: gProjectDebug:   %d\n",gProjectDebug);
    if(g_msMiceSinkProjPtr)
    {
        g_msMiceSinkProjPtr->DumpClassPara(0);

        if(g_msMiceSinkProjPtr->m_service_obj)
            g_msMiceSinkProjPtr->m_service_obj->DumpClassPara(0);
    }
    else
    {
        CSIO_LOG(eLogLevel_info, "msMiceSinkProjDumpPara: no gWFDSinkProjPtr is running\n");
    }

    gProjectsLock.unlock();
}
void msMiceSinkProj_fdebug(char *cmd_cstring)
{
    char *EndPtr,*CmdPtr;

    if(cmd_cstring)
        CSIO_LOG(eLogLevel_info, "msMiceSinkProj_fdebug: cmd_cstring: %s\n",cmd_cstring);
    else
        CSIO_LOG(eLogLevel_info, "msMiceSinkProj_fdebug: got null pointer\n");

    if ( cmd_cstring == NULL )
    {
        msMiceSinkProj_printFdebugInfo();
    }
    else
    {
        CmdPtr = strtok(cmd_cstring,", "); //this should be Wfd_FDEBUG
        CmdPtr = strtok(NULL, ", ");
        CSIO_LOG(eLogLevel_info, "msMiceSinkProj_fdebug: CmdPtr: %s\n",CmdPtr);

        if (CmdPtr == NULL || *CmdPtr == '\0' || *CmdPtr == '?' || strcasestr(CmdPtr, "HELP"))
        {
            msMiceSinkProj_printFdebugInfo();
        }
        else if(strcasestr(CmdPtr, "PROJINIT"))
        {
            msMiceSinkProjInit("0.0.0.0");
        }
        else if(strcasestr(CmdPtr, "PROJDEINIT"))
        {
            msMiceSinkProjDeInit();
        }
        else if(strcasestr(CmdPtr, "DUMPALL"))
        {
            msMiceSinkProjDumpPara();
        }
        else if(strcasestr(CmdPtr, "SETDEBUGLEVEL"))
        {
            CmdPtr = strtok(NULL, ", ");
            if(CmdPtr)
            {
                int level = strtol(CmdPtr, &EndPtr, 10);
                msMiceSinkProjSetDebugLevel(level);
            }
        }
        else if(strcasestr(CmdPtr, "SESSIONSTOP"))
        {
            CmdPtr = strtok(NULL, ", ");
            if(CmdPtr)
            {
                //id is 64 bit
                int session_id = strtol(CmdPtr, &EndPtr, 10);
                msMiceSinkProjStopSession(0,session_id);
            }
        }
        else if(strcasestr(CmdPtr, "SETPIN"))
        {
            CmdPtr = strtok(NULL, ", ");
            if(CmdPtr)
            {
                CSIO_LOG(eLogLevel_info, "msMiceSinkProj_fdebug: SETPIN command = [%s]\n",CmdPtr);
                msMiceSinkProjSetPin(0,CmdPtr);
            }
            else
            {
                CSIO_LOG(eLogLevel_info, "msMiceSinkProj_fdebug: SETPIN command = NULL\n");
                msMiceSinkProjSetPin(0,NULL);
            }
        }
        else
        {
           CSIO_LOG(eLogLevel_info, "msMiceSinkProj_fdebug: Unknown command = [%s]\n",CmdPtr);
        }

    }

    CSIO_LOG(eLogLevel_info, "msMiceSinkProj_fdebug: exit\n");
}

