#ifndef __CRESSTREAMOUTSNAPSHOT_H__
#define __CRESSTREAMOUTSNAPSHOT_H__

//////////////////////////////////////////////////////////////////////////////
#include <time.h>
#include "../cresStreamOutProject.h"
#include "cres_rtsp-media.h"
#include "cresStreamOutManager.h"
#include "cresPreview.h"

#define CRES_SNAPSHOT_RAMDISK      		"/dev/shm/crestron/frames"
#define CRES_SNAPSHOT_WEB_RAMDISK  		"/dev/shm/crestron/jpeg"
#define CRES_SNAPSHOT_FILE_BASE "frame"
#define CRES_SNAPSHOT_UPDATE_PERIOD_SECS  1
#define CRES_SNAPSHOT_SNAPSHOT_QUEUE_SIZE 5

class CStreamoutManager;
class CStreamCamera;
class SnapShot
{
	public:
		SnapShot();
		~SnapShot();

		void setUpdateRate(int period_in_seconds);
		int  getUpdateRate(void);
		void setQueueSize(int snapshot_queue_size);
		int  getQueueSize(void);

		int  add(void *arg);
		int  drop(void);
		int  deleteAllFiles(const char *dir, bool bRmFolder=false);
		int  updatelink(const gchar *snapshot);
		void start(CStreamoutManager *pMgr,CStreamCamera *pCam);
		void stop(void);
		void updateSnapShotName(void);

		GMainContext *m_context;  /* GLib context used to run the main loop */

		GMainLoop *m_loop;

		GstElement *m_videoconv;
		GstElement *m_videotee;
		GstElement *m_snapshotq;
		GstElement *m_videoscale;
		GstElement *m_videorate;
		GstElement *m_videofilter;
		GstElement *m_encoder;
		GstElement *m_valve;
		GstElement *m_sink;

		int        m_update_period_secs;
		int        m_rawfilesqueue_size;
		char       m_snapshot_name[MAX_STR_LEN];	// When updating this needs to be locked by mULock
		bool	   m_snapshot_name_updated;

		gboolean   m_bExit;
	    int        m_bStopInProgress;

	    Mutex   *mLock;
	    Mutex   *mULock;
	    CondVar *mCond;
	    Mutex   *mCond_mtx;

	private:

};

#endif //__CRESSTREAMOUTSNAPSHOT_H__

