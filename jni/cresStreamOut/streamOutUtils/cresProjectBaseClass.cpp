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

#include "csioCommonShare.h"
#include "cresProjectBaseClass.h"

CresProjBaseClass::CresProjBaseClass():m_forceThreadExit(0),
m_threadObjLoopCnt(0), m_threadObjID(0),m_ThreadIsRunning(0),
m_debugLevel(CSIO_DEFAULT_LOG_LEVEL),//subclass should overwrite this
eventPushIndex(0),eventPopIndex(0)
{
    _thread = 0;
    memset(eventPushArray,0,sizeof(eventPushArray));
    memset(eventPopArray,0,sizeof(eventPopArray));
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
