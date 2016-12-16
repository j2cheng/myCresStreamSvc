#ifndef __CRESPREVIEW_H__
#define __CRESPREVIEW_H__

//////////////////////////////////////////////////////////////////////////////
#include <time.h>
#include <gst/gst.h>
#include "../cresStreamOutProject.h"
#include "cres_rtsp-media.h"
#include "cresStreamOutManager.h"

class CStreamoutManager;
class Preview
{
	public:
		Preview(void *arg);
		~Preview();

		int   add(void);
		int   drop(void);
		int   start(void *window);
		int   pause(void *window);
		int   stop(void *window);
		void  setPlaying(void);
		int   waitForPreviewAvailable(int timeout_sec);
		int   wakeup(void);

		CStreamoutManager *m_pMgr;
		void         *m_nativeWindow;
		GMainContext *m_context;  /* GLib context used to run the main loop */

		GstElement *m_previewq;
		GstElement *m_videoconv;
		GstElement *m_valve;
		GstElement *m_prevsinkq;
		GstElement *m_previewsink;

		GstPad    *m_blockpad;
		gulong     m_preview_probe_id;

		gboolean   m_bStopInProgress;
		gboolean   m_bInstallSink;
		gboolean   m_bPipelineReady;
		gboolean   m_paused;

	    Mutex   *mLock;
	    Mutex   *mULock;
	    CondVar *mCond;
	    Mutex   *mCond_mtx;

	private:

};

extern "C" GstPadProbeReturn cb_Prevsink_probe(GstPad *pad, GstPadProbeInfo *info, gpointer user_data);
extern "C" GstPadProbeReturn cb_Playing_probe(GstPad *pad, GstPadProbeInfo *buffer, gpointer user_data);
#endif //__CRESPREVIEW_H__

