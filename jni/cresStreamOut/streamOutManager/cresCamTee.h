#ifndef _CAMTEE_H
#define _CAMTEE_H

class CamTee
{
	public:
		CamTee();
		~CamTee();

	    int  startTee(void);
	    int  stopTee(void);
	    int  saveRawFrame(GstPad *pad, GstPadProbeInfo *buffer);
	    void cleanup(void);

	    void* CameraTeeEntry();
	    /** Returns true if created, false otherwise */
	    bool CreateTeeThread()
	    {
	        return (pthread_create(&m_tCameraTeeId, NULL, TeeThreadEntry, this) == 0);
	    }
	    void lockTee(){if(mTLock) mTLock->lock();}
	    void unlockTee(){if(mTLock) mTLock->unlock();}
		GMainLoop *createMainLoop(void);
		void createMainContext(void);
		void freeMainContext(void);

	    pthread_t   m_tCameraTeeId;
		GstElement *m_teePipeline;
		GstElement *m_teeSource;
		GstElement *m_tee;
		GstElement *m_teeQ;
		GstElement *m_sink;
		GMainLoop  *m_teeLoop;
		gboolean    m_bNeedData;
		gboolean    m_bExit;
		gboolean    m_bStopTeeInProgress;
		GstBus     *m_bus;
		guint       m_bus_id;

	    static void * TeeThreadEntry(void * This) {((CStreamoutManager *)This)->CameraTeeEntry(); return NULL;}
		GMainContext *m_context;  /* GLib context used to run the main loop */

	private:
	    Mutex* mTLock;
};

extern "C" gboolean cb_teeGstMsgHandler(GstBus *bus, GstMessage *msg, void *arg);

#endif //_CAMTEE_H

