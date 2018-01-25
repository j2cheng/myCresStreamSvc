#ifndef __CRESCAMERA_H__
#define __CRESCAMERA_H__

//////////////////////////////////////////////////////////////////////////////
#include "../cresStreamOutProject.h"
#include "cres_rtsp-media.h"
#include "cresStreamSnapShot.h"
#include "cresPreview.h"

#include <arpa/inet.h>

const int c_camera_width  = 1280;
const int c_camera_height = 960;
const int c_camera_framerate = 30;
const int c_cropped_height = 720;

class SnapShot;
class CStreamoutProject;
class CStreamCamera
{
	public:

		CStreamCamera(void *parent);
		~CStreamCamera();

	    bool create(CStreamoutProject *pMgr);
	    void remove(CStreamoutProject *pMgr);
		int  start(void);
		int  stop(void);
		void cleanup(void);

		void lockCam(){if(mTLock) mTLock->lock();}
		void unlockCam(){if(mTLock) mTLock->unlock();}
		GMainLoop *createMainLoop(void);
		void createMainContext(void);
		void freeMainContext(void);

	    void startPreview(void* window);
	    void pausePreview(void* window);
	    void stopPreview(void* window);
	    int  waitForPreviewAvailable(int timeout_ms);
	    int  waitForPreviewClosed(int time_out);

		gboolean    m_bExit;
		GMainContext *m_context;
		GstElement   *m_pipeline;
		GMainLoop  *m_loop;
		GstElement *m_source;
		GstElement *m_srcfilter;
		GstElement *m_crop;
		GstElement *m_cam_q;
		int         m_id_probe_cam_q;
		GstElement *m_tee;
		GstElement *m_teefilter;
		GstElement *m_teeQ;
		GstElement *m_sink;
		GstBus     *m_bus;
		guint       m_bus_id;

		gboolean m_bStopInProgress;
		pthread_t m_tCameraId;
		gboolean  m_bPushRawFrames;

		GMainLoop   *getMainLoop(){return m_loop;}

	    SnapShot *m_snapobj;
	    Preview  *m_preview;

	    void *m_parent;
	    CStreamoutManager *m_pMgr;

	private:
		Mutex* mLock;
		Mutex* mTLock;
};

extern "C" int  StartCamera(void *parent);
extern "C" void StopCamera(void *parent);
extern "C" void *cameraThread(void * arg);

extern "C" int  StartSnapShot(CStreamoutManager *pMgr);
extern "C" int  StopSnapShot(CStreamoutManager *pMgr);
extern "C" void UpdateSnapshotName(void);

extern "C" int  WaitForPreviewAvailable(int timeout_sec);
extern "C" int  WaitForPreviewClosed(int timeout_sec);

extern "C" void StartPreview(void* window);
extern "C" void PausePreview(void* window);
extern "C" void StopPreview(void* window);

#endif //__CRESCAMERA_H__
