/**
 * Copyright (C) 2016 to the present, Crestron Electronics, Inc.
 * All rights reserved.
 * No part of this software may be reproduced in any form, machine
 * or natural, without the express written consent of Crestron Electronics.
 *
 * \file        cresPrOJECTBaseClass.cpp
 *
 * \brief       Base class for project
 *
 * \author      John Cheng
 *
 * \date        1/12/2016
 *
 * \note        Base class
 *
 * \todo
 */

#include <cassert>
#include <pthread.h>
#include "csioCommonShare.h"
#include "cresProjectBaseClass.h"

CresProjBaseClass::CresProjBaseClass():m_forceThreadExit(0),
m_threadObjLoopCnt(0), m_threadObjID(0),m_ThreadIsRunning(0),
m_debugLevel(CSIO_DEFAULT_LOG_LEVEL),//subclass should overwrite this
eventPushIndex(0),eventPopIndex(0)
{
    memset(eventPushArray,0,sizeof(eventPushArray));
    memset(eventPopArray,0,sizeof(eventPopArray));
}

int CresProjBaseClass::CreateNewThread(const char* name, pthread_attr_t* attr)
{
    if(name) name_ = name;
    int ret = pthread_create(&thread_, attr, ThreadEntryFunc, this);

    if(0 != ret)
    {
        CSIO_LOG(
            eLogLevel_error,
            "%s: failed %s, %d(%s)",
            __func__, name_.c_str(), errno, strerror(errno));
        name_.clear();
        return -1;
    }

    CSIO_LOG(eLogLevel_info, "%s: created %s", __func__, name_.c_str());
    m_ThreadIsRunning = 1;
    return ret;
}


void CresProjBaseClass::logEventPush(int e)
{
    eventPushArray[eventPushIndex++] = e;
    if( eventPushIndex >= MAX_DEBUG_ARRAY_SIZE)
        eventPushIndex = 0;
}

void CresProjBaseClass::logEventPop(int e)
{
    eventPopArray[eventPopIndex++] = e;
    if( eventPopIndex >= MAX_DEBUG_ARRAY_SIZE)
        eventPopIndex = 0;
}

void *CresProjBaseClass::ThreadEntryFunc(void *arg)
{
    assert(arg);
    auto object = static_cast<CresProjBaseClass *>(arg);
    if(!object->name_.empty()) pthread_setname_np(object->thread_, object->name_.c_str());
    CSIO_LOG(eLogLevel_info, "%s: %s started", __func__, object->name_.c_str());
    const auto status = object->ThreadEntry();
    CSIO_LOG(eLogLevel_info, "%s: %s exiting", __func__, object->name_.c_str());
    return status;
}


/* Will not return until thread has exited. */
void CresProjBaseClass::WaitForThreadToExit()
{
    const auto status = pthread_join(thread_, nullptr);

    if(0 != status)
    {
        CSIO_LOG(
            eLogLevel_error,
            "%s: failed to join %s %d(%s)",
            __func__, name_.c_str(), errno, strerror(errno));
        assert("failed to join thread" && false);
    } else CSIO_LOG( eLogLevel_info, "%s: joined %s", __func__, name_.c_str());
}
