/**
 * Copyright (C) 2016 to the present, Crestron Electronics, Inc.
 * All rights reserved.
 * No part of this software may be reproduced in any form, machine
 * or natural, without the express written consent of Crestron Electronics.
 *
 * \file        cresStreamOutUtiles.cpp
 *
 * \brief
 *
 * \author      John Cheng
 *
 * \date        1/27/2016
 *
 * \note
 *
 *
 * \todo
 */
#include <time.h>
#include "cresStreamOutUtils.h"

static void convertMsToTimespec(struct timespec * out_ts,int in_ms)
{
    const int64_t NANOSEC_PER_MILLISEC = 1000000;
    const int64_t NANOSEC_PER_SEC = 1000000000;

    long nanosecs, secs,ms;

    clock_gettime(CLOCK_MONOTONIC, out_ts);

    secs = in_ms/1000;
    ms   = in_ms%1000;
    nanosecs = ms*NANOSEC_PER_MILLISEC + out_ts->tv_nsec ;

    if (nanosecs >= NANOSEC_PER_SEC)
    {
        secs += 1;
        nanosecs %= NANOSEC_PER_SEC;
    }

    out_ts->tv_nsec = nanosecs;
    out_ts->tv_sec  += secs;
}

/********** Event class, includes its own mutex and condition variable *******************/
CStreamoutEvent::CStreamoutEvent()
{
    m_EventMtx      = new Mutex();
    m_EventCondVar  = new CondVar();
    eventID = 0;
}
CStreamoutEvent::~CStreamoutEvent()
{
    if (m_EventMtx != NULL)
        delete m_EventMtx;

    if (m_EventCondVar != NULL)
       delete m_EventCondVar;
}

void CStreamoutEvent::signalEvent(int evnt)
{
    m_EventMtx->lock();
    eventID = evnt;//NOTE: currently, this id is not being used to identify anything
    m_EventCondVar->signal();
    m_EventMtx->unlock();
}

int CStreamoutEvent::waitForEvent(int* rtnEvnt, int timeOut_ms)
{
    int rc;
    *rtnEvnt = STREAMOUT_EVENTS_MAX;

    if(timeOut_ms == 0)
    {
        m_EventMtx->lock();
        pthread_mutex_t *mutex  = m_EventMtx->get_mutex_ptr();
        rc = m_EventCondVar->wait( mutex );
        if (rc == 0)
            *rtnEvnt = eventID;//NOTE: currently, this id is not being used to identify anything
        m_EventMtx->unlock();
    }
    else
    {
        struct timespec   ts;
        convertMsToTimespec(&ts,timeOut_ms);

        m_EventMtx->lock();
        pthread_mutex_t *mutex  = m_EventMtx->get_mutex_ptr();
        rc = m_EventCondVar->waittimedcont( mutex, &ts);
        //if (rc == ETIMEDOUT)
        //    *rtnEvnt = PROXY_EVENTS_MAX;
        if (rc == 0)
            *rtnEvnt = eventID;//NOTE: currently, this id is not being used to identify anything
        m_EventMtx->unlock();
    }

    CSIO_LOG(eLogLevel_extraVerbose, "CStreamoutEvent: rc:[%d].\n",rc);
    return rc;
}
/************************* end of Event class ************************/


/************************ Queue class **************************/
CStreamoutEventRingBuffer::CStreamoutEventRingBuffer(int evnt_list_size)
{
    if( evnt_list_size > EVNT_QUEUE_MAX)
        evnt_list_size = EVNT_QUEUE_MAX;

    m_iBuffInIdx = m_iBuffOutIdx = 0;
    m_ucBufPtr = new EventQueueStruct[evnt_list_size];
    if ( !m_ucBufPtr )
    {
        m_iBufSize = 0;
    }
    else
    {
        memset(m_ucBufPtr, 0, sizeof(EventQueueStruct[evnt_list_size]));
        m_iBufSize = evnt_list_size;
    }

    m_iOverFlowThds = m_iBufSize/2 ;

    m_EvntQMtx     = new Mutex();
}
CStreamoutEventRingBuffer::~CStreamoutEventRingBuffer()
{
    if(m_ucBufPtr)
    {
        for(int i = 0; i < m_iBufSize; i++)
            clearQ(&m_ucBufPtr[i]);

        delete[] m_ucBufPtr;
    }

    if (m_EvntQMtx != NULL)
        delete m_EvntQMtx;
}

void CStreamoutEventRingBuffer::AddToBuffer( EventQueueStruct evntQueue )
{
    m_EvntQMtx->lock();
    if(GetEvntQueueCount() >= m_iOverFlowThds)
    {
        //TODO: remove duplicated events

        CSIO_LOG(eLogLevel_debug, "Streamout: CStreamoutEventRingBuffer:overflow[%d].\n",GetEvntQueueCount());
    }
    m_ucBufPtr[m_iBuffInIdx++] = evntQueue;
    m_iBuffInIdx %= m_iBufSize;
    m_EvntQMtx->unlock();

    CSIO_LOG(eLogLevel_extraVerbose, "CStreamoutEventRingBuffer: AddToBuffer:m_iBuffInIdx[%d],m_iBuffOutIdx[%d].\n",m_iBuffInIdx,m_iBuffOutIdx);
}

/* queue and its bufferPtr will be deleted.
 * Note: it is only used by destructor, so there is no lock
 * here.
 */
void CStreamoutEventRingBuffer::clearQ(EventQueueStruct* evntQueue)
{
    evntQueue->buf_size = 0;
    if(evntQueue->buffPtr)
        del_Q_buf(evntQueue->buffPtr);

    evntQueue->buffPtr       = NULL;
    evntQueue->event_type    = 0;
    evntQueue->streamout_obj_id  = 0;
}

/* the queue removed from the list, only clear its field.
 * Note: it is called by GetFromBuffer, which is locked
*/
void CStreamoutEventRingBuffer::removeQ(EventQueueStruct* evntQueue)
{
    evntQueue->buf_size      = 0;
    evntQueue->buffPtr       = NULL;
    evntQueue->event_type    = 0;
    evntQueue->streamout_obj_id  = 0;
}

bool CStreamoutEventRingBuffer::GetFromBuffer(EventQueueStruct* evntQueue )
{
    if ( m_iBuffOutIdx == m_iBuffInIdx)
        return false;

    m_EvntQMtx->lock();
    *evntQueue = m_ucBufPtr[m_iBuffOutIdx];
    removeQ(&m_ucBufPtr[m_iBuffOutIdx]);

    m_iBuffOutIdx++;
    m_iBuffOutIdx %= m_iBufSize;

    m_EvntQMtx->unlock();

    CSIO_LOG(eLogLevel_extraVerbose, "CStreamoutEventRingBuffer: GetFromBuffer:m_iBuffInIdx[%d],m_iBuffOutIdx[%d].\n",m_iBuffInIdx,m_iBuffOutIdx);
    return true;
}

void CStreamoutEventRingBuffer::del_Q_buf(void* buffPtr)
{
    if(buffPtr)
    {
        char* tmp = (char*)buffPtr;
        delete [] tmp;
    }
}
/************************* end of Queue class ************************/

/************************* LookupTable class ************************/
LookupTable::LookupTable():
fMasterEntry(NULL),m_numEntries(0)
{

}
LookupTable::~LookupTable()
{
    int numEntries = m_numEntries;
    if(m_numEntries > 0)
    {
        TableEntry* next_entry = NULL;

        for (TableEntry* entry = fMasterEntry; entry != NULL; entry = next_entry)
        {
            next_entry = entry->fNext;
            if (entry)
                delete entry;

            if(--numEntries <= 0)
            {
                //something is very wrong
                CSIO_LOG(eLogLevel_debug, "Streamout: failed to get to the last item!\n");
                break;
            }
        }
    }
}

bool LookupTable::Add(char * key)
{
    bool retV = false;
    if(fMasterEntry == NULL)
    {
        TableEntry* newEntry = new TableEntry;
        if(newEntry == NULL)
        {
            CSIO_LOG(eLogLevel_error, "Streamout: LookupTable::allocate memory failed\n");
            return false;
        }

        newEntry->fNext = NULL;
        newEntry->key = key;

        fMasterEntry = newEntry;
        m_numEntries++;
        retV = true;
        CSIO_LOG(eLogLevel_extraVerbose, "Streamout: LookupTable::Add fMasterEntry[0x%x] done\n",fMasterEntry);
    }
    else
    {
        TableEntry* newEntry = new TableEntry;
        if(newEntry == NULL)
        {
            CSIO_LOG(eLogLevel_error, "Streamout: LookupTable::allocate memory failed\n");
            return false;
        }

        newEntry->fNext = NULL;
        newEntry->key = key;

        //find the last entry
        int numEntries = m_numEntries;
        for (TableEntry* entry = fMasterEntry; entry != NULL; entry = entry->fNext)
        {
            if (entry->fNext == NULL)
            {
                entry->fNext = newEntry;
                retV = true;
                m_numEntries++;
                break;
            }

            if(--numEntries <= 0)
            {
                //something is very wrong
                CSIO_LOG(eLogLevel_warning, "Streamout: failed to get to the last item!\n");
                if(newEntry)
                    delete newEntry;

                break;
            }
        }
    }

    CSIO_LOG(eLogLevel_verbose, "Streamout: LookupTable::Add retV[0x%x]\n",retV);

    return retV;
}

bool LookupTable::Remove(char * key)
{
    if(fMasterEntry == NULL)
        return false;

    if(fMasterEntry->key == key)
    {
        if(fMasterEntry->fNext == NULL)
        {
            delete fMasterEntry;
            m_numEntries = 0;
            fMasterEntry = NULL;
        }
        else
        {
            TableEntry* matchedEntry = fMasterEntry->fNext;
            delete fMasterEntry;
            fMasterEntry = matchedEntry;
            m_numEntries--;
        }
        CSIO_LOG(eLogLevel_extraVerbose, "Streamout: matchedEntry is master return true");
        return true;
    }

    //find the key entry
    int numEntries = m_numEntries;
    for (TableEntry* entry = fMasterEntry; entry != NULL; entry = entry->fNext)
    {
        if (entry->fNext && (entry->fNext->key == key))
        {
            TableEntry* matchedEntry = entry->fNext;
            entry->fNext = matchedEntry->fNext;

            delete matchedEntry;
            m_numEntries--;

            CSIO_LOG(eLogLevel_extraVerbose, "Streamout: key deleted m_numEntries[%d]\n",
                     m_numEntries);

            return true;
        }

        if(--numEntries <= 0)
        {
            //something is very wrong
            CSIO_LOG(eLogLevel_debug, "Streamout: failed to get to the last item!\n");
            break;
        }
    }

    CSIO_LOG(eLogLevel_debug, "Streamout: LookupTable::Remove failed here, why??");
    return false;
}

char* LookupTable::lookupByIndex(int index)
{
    if(index >= m_numEntries) return NULL;

    int i = 0;
    for (TableEntry* entry = fMasterEntry; entry != NULL; entry = entry->fNext)
    {
        if(i++ == index)
            return entry->key;
    }
    return NULL;
}
/************************* end of LookupTable class ************************/

void convertTime(struct timeval t,char* time_string,int sizeofstring,long& milliseconds)
{
    struct tm* ptm = localtime (&t.tv_sec);
    strftime (time_string, sizeofstring, "%Y-%m-%d %H:%M:%S", ptm);
    milliseconds = t.tv_usec / 1000;
}
