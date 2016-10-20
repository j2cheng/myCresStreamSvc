#ifndef __CRESSTREAMOUTSNAPSHOT_H__
#define __CRESSTREAMOUTSNAPSHOT_H__

//////////////////////////////////////////////////////////////////////////////

#include "../cresStreamOutProject.h"
#include "cres_rtsp-media.h"
#include "cresStreamOutManager.h"

#define CRES_SNAPSHOT_RAMDISK      		"/dev/shm/crestron/frames"
#define CRES_SNAPSHOT_WEB_RAMDISK  		"/dev/shm/crestron/jpeg"
#define CRES_SNAPSHOT_FILENAME_DEFAULT 	"Snapshot.jpg"
#define CRES_SNAPSHOT_FILE_BASE "frame"
#define CRES_SNAPSHOT_UPDATE_PERIOD_SECS  1
#define CRES_SNAPSHOT_SNAPSHOT_QUEUE_SIZE 5

class CStreamoutManager;
class SnapShot
{
	public:
		SnapShot(void *arg);
		~SnapShot();

		void setStreamName(char* name);
		char* getStreamName(void);
		void setUpdateRate(int period_in_seconds);
		int  getUpdateRate(void);
		void setQueueSize(int snapshot_queue_size);
		int  getQueueSize(void);

		int  start(void *arg);
		void stop(void);
		GMainLoop *createMainLoop(void);
		void createMainContext(void);
		void freeMainContext(void);
		GMainLoop  *getMainLoop(void);
		GstElement *getPipeline(void);
		int  waitRawFrame(void);
		void signalRawFrameComplete(void);
		int  saveRawFrame(GstPad *pad, GstPadProbeInfo *buffer, gpointer user_data);
		int  updatelink(char *snapshot);
		void cleanup(void);

		GMainContext *m_context;  /* GLib context used to run the main loop */

		GMainLoop *m_loop;
		GstBus    *m_bus;
		guint      m_bus_id;

		GstElement *m_pipeline;
		GstElement *m_source;
		GstElement *m_videoparse;
		GstElement *m_encoder;
		GstElement *m_sink;

		int        m_update_period_secs;
		int        m_rawfilesqueue_size;
		char       m_stream_name[MAX_STR_LEN];

		gboolean   m_bExit;
		int        m_snapIndex;
	    int        m_rawbuffer_cnt;
	    int        m_rawfileIdx;

	    Mutex   *mLock;
	    CondVar *mCond;

	private:

};

extern "C" int  StartSnapShot(CStreamoutManager *pMgr);
extern "C" void StopSnapShot(CStreamoutManager *pMgr);

extern "C" void    *snapshotThread(void * arg);
extern "C" gboolean snapshotGstMsgHandler(GstBus *bus, GstMessage *msg, void *arg);
#endif //__CRESSTREAMOUTSNAPSHOT_H__

