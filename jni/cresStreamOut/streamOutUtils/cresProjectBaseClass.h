#pragma once

#include <pthread.h>
#include <string>

#define THREAD_LOOP_TO       50//50ms
#define MAX_DEBUG_ARRAY_SIZE 100

enum CLASS_DUMP_SELECTION{
    DUMP_ALL = 0,//default
    DUMP_TASK,
    DUMP_CLASS,

    DUMP_MAX
};

class CresProjBaseClass
{
public:
    CresProjBaseClass() ;
    virtual ~CresProjBaseClass() = default;
    /** Returns true if created, false otherwise */
    int CreateNewThread(const char* name, pthread_attr_t* attr);
    /* Will not return until thread has exited. */
    void WaitForThreadToExit();
    void setDebugLevel(int level) { m_debugLevel = level; }
    int  getDebugLevel() { return m_debugLevel; }

    void exitThread() { m_forceThreadExit = 1; }
    virtual void DumpClassPara( int ) = 0;

    char  m_forceThreadExit;
    int  m_ThreadIsRunning;
private:
    int  eventPushArray[MAX_DEBUG_ARRAY_SIZE];
    int  eventPushIndex;
    int  eventPopArray[MAX_DEBUG_ARRAY_SIZE];
    int  eventPopIndex;
protected:
    void logEventPop(int e);
    void logEventPush(int e);
    /** Must implement in subclass. */
    virtual void* ThreadEntry() = 0;

    int   m_debugLevel;
    int   m_threadObjLoopCnt;
    int   m_threadObjID;
    std::string name_;
private:
    static void *ThreadEntryFunc(void *);
    pthread_t thread_;
};
