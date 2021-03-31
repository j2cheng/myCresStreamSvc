#ifndef _CRES_STREAMOUT_UTILS_H_
#define _CRES_STREAMOUT_UTILS_H_

#include <time.h>
#include "csioCommonShare.h"// need for Mutex

#define EVNT_QUEUE_MAX           200
#define EVNT_QUEUE_OVERFLOW      EVNT_QUEUE_MAX/2
#define EVNT_DEFAULT_QUEUE_SIZE  100

#define VERB_AND_ABOVE(a) ( a >= eLogLevel_verbose ? a : eLogLevel_verbose )

typedef struct _EVNT_QUEUE_STRUCT
{
    unsigned int   streamout_obj_id;
    int   event_type;
    int   buf_size;
    void* buffPtr;

    int   ext_obj;
    int   ext_obj2;
} EventQueueStruct;


typedef enum _eStreamoutEvents
{
//events come from jni.c to the project
    STREAMOUT_EVENT_JNI_CMD_START = 0,
    STREAMOUT_EVENT_JNI_CMD_STOP,

    STREAMOUT_EVENT_JNI_CMD_PORT,
    STREAMOUT_EVENT_JNI_CMD_RES_X,
    STREAMOUT_EVENT_JNI_CMD_RES_Y,
    STREAMOUT_EVENT_JNI_CMD_FRAMERATE,
    STREAMOUT_EVENT_JNI_CMD_BITRATE,
    STREAMOUT_EVENT_JNI_CMD_IFRAMEINTERVAL,
	STREAMOUT_EVENT_JNI_CMD_ENABLE_MULTICAST,
	STREAMOUT_EVENT_JNI_CMD_MULTICAST_ADDRESS,
	STREAMOUT_EVENT_JNI_CMD_STREAM_NAME,
	STREAMOUT_EVENT_JNI_CMD_SNAPSHOT_NAME,
	STREAMOUT_EVENT_JNI_CMD_START_PREVIEW,
	STREAMOUT_EVENT_JNI_CMD_PAUSE_PREVIEW,
	STREAMOUT_EVENT_JNI_CMD_STOP_PREVIEW,
	STREAMOUT_EVENT_JNI_CMD_ENABLE_SECURITY,
	STREAMOUT_EVENT_JNI_CMD_ENABLE_RANDOM_USER_PW,
	STREAMOUT_EVENT_JNI_CMD_VIDEO_CAPTURE_DEVICE,
	STREAMOUT_EVENT_JNI_CMD_AUDIO_CAPTURE_DEVICE,

    STREAMOUT_EVENTS_MAX
}eStreamoutEvents;

class CStreamoutEvent
{
public:

    CStreamoutEvent();
    ~CStreamoutEvent();

    void signalEvent(int);
    int waitForEvent(int* rtnEvnt,int timeOut_us);

    Mutex *m_EventMtx;
    CondVar *m_EventCondVar;

    //NOTE: currently, this id is not being used to identify anything
    int eventID;
};


class CStreamoutEventRingBuffer
{
protected:
    int   m_iBufSize;       // buffer size
    int   m_iBuffInIdx;     // index to input character
    int   m_iBuffOutIdx;    // index to output character
    int   m_iOverFlowThds;  //overflow threshold

    EventQueueStruct* m_ucBufPtr;    // buffer pointer
    Mutex *m_EvntQMtx;

    void clearQ(EventQueueStruct* evntQueue);
    void removeQ(EventQueueStruct* evntQueue);

public:
    CStreamoutEventRingBuffer(int evnt_list_size);

    ~CStreamoutEventRingBuffer();

    // clears the buffer
    void Clear(void ) { m_iBuffInIdx = m_iBuffOutIdx = 0;};  // resets all pointers and frees all space

    unsigned char  *FirstByte(void ) { return (unsigned char  *)&m_ucBufPtr[0];};

    void AddToBuffer( EventQueueStruct evntQueue );

    bool GetFromBuffer(EventQueueStruct* evntQueue );

    void del_Q_buf(void* buffPtr);

    int GetEvntQueueCount(void)
    {
        if ( m_iBuffInIdx >= m_iBuffOutIdx )
            return (m_iBuffInIdx - m_iBuffOutIdx);
        else
            return (m_iBufSize - (m_iBuffOutIdx - m_iBuffInIdx)) ;
    };
};

class LookupTable
{
public:
    LookupTable();
    ~LookupTable();

    bool Add(char * key);
    bool Remove(char * key);

    int numEntries() { return m_numEntries; }
    char* lookupByIndex(int i);

private:
    class TableEntry
    {
    public:
        TableEntry* fNext;
        char * key;
    };

    TableEntry* fMasterEntry; // pointer to item array
    int m_numEntries;
};

extern void convertTime(struct timeval t,char* time_string,int sizeofstring,long& milliseconds);
#endif /* _CRES_STREAMOUT_UTILS_H_ */
