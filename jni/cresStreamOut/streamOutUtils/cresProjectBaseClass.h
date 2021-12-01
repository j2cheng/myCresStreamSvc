#ifndef _CRES_PROJECT_BASE_CLASS_H_
#define _CRES_PROJECT_BASE_CLASS_H_

#include <pthread.h>

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
    virtual ~CresProjBaseClass() {/* empty */}

    /** Returns true if created, false otherwise */
    int CreateNewThread(const char* name, pthread_attr_t* attr)
    {
        int ret = pthread_create(&_thread, attr, ThreadEntryFunc, this);

        if(ret == 0)//success
        {
            if(name)
            {
                pthread_setname_np(_thread,name);
            }

            m_ThreadIsRunning = 1;
        }

        return ret;
    }

    /* Will not return until thread has exited. */
    void WaitForThreadToExit()
    {
        (void) pthread_join(_thread, NULL);
    }



    void setDebugLevel(int level) { m_debugLevel = level; }
    int  getDebugLevel() { return m_debugLevel; }

    void exitThread() { m_forceThreadExit = 1; }
    pthread_t getThredId() { return _thread; }

    virtual void DumpClassPara( int ) = 0;

    char  m_forceThreadExit;

    int  m_ThreadIsRunning;

    int  eventPushArray[MAX_DEBUG_ARRAY_SIZE];
    int  eventPushIndex;
    void logEventPush(int e);

    int  eventPopArray[MAX_DEBUG_ARRAY_SIZE];
    int  eventPopIndex;
    void logEventPop(int e);
protected:
    /** Must implement in subclass. */
    virtual void* ThreadEntry() = 0;

    int   m_debugLevel;
    int   m_threadObjLoopCnt;
    int   m_threadObjID;

private:
    static void * ThreadEntryFunc(void * This) {((CresProjBaseClass *)This)->ThreadEntry(); return NULL;}

    pthread_t _thread;
};
#endif /* _CRES_PROJECT_BASE_CLASS_H_ */
