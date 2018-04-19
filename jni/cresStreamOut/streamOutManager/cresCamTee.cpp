#include <stdlib.h>
#include "cresStreamOutManager.h"

CamTee::CamTee():
m_bNeedData(false),m_teePipeline(NULL),m_teeSource(NULL),m_tee(NULL),m_teeLoop(NULL),m_bStopTeeInProgress(false),
m_bExit(false),m_sink(NULL),m_bus(NULL),m_bus_id(-1)
{
    mTLock           = new Mutex();

}

CamTee::~CamTee()
{
    if(mTLock)
    {
    	delete mTLock;
    	mTLock = NULL;
    }
}

void cb_teeNeedData (GstElement *appsrc, guint unused_size, gpointer user_data)
{
	//CSIO_LOG(eLogLevel_verbose, "CamTee: cb_need_data...");
	CStreamoutManager *pMgr = (CStreamoutManager *) user_data;

	pMgr->m_bNeedData = true;
}

void cb_teeEnoughData (GstElement *appsrc, guint unused_size, gpointer user_data)
{
	//CSIO_LOG(eLogLevel_verbose, "CamTee: cb_enough_data...");
	CStreamoutManager *pMgr = (CStreamoutManager *) user_data;

	pMgr->m_bNeedData = false;
}

gboolean cb_teeGstMsgHandler(GstBus *bus, GstMessage *msg, void *arg)
{
	CStreamoutManager *pMgr = (CStreamoutManager *) arg;

	GError    *err;
	gchar     *debug_info = NULL;
	GMainLoop *pMainloop = NULL;

	switch (GST_MESSAGE_TYPE(msg))
	{
		case GST_MESSAGE_NEW_CLOCK:
			GstClock *clock;

			gst_message_parse_new_clock (msg, &clock);

			CSIO_LOG(eLogLevel_verbose, "CamTee: New clock: %s", (clock ? GST_OBJECT_NAME (clock) : "NULL"));
			break;

		case GST_MESSAGE_CLOCK_LOST:
			CSIO_LOG(eLogLevel_verbose, "CamTee: Clock lost, selecting a new one");
			if(pMgr)
			{
				if(pMgr->m_teePipeline)
				{
					GstStateChangeReturn result;
					result = gst_element_set_state( pMgr->m_teePipeline, GST_STATE_PAUSED );
					if(result != GST_STATE_CHANGE_SUCCESS)
					{
						CSIO_LOG(eLogLevel_verbose, "CamTee: Lost clock - setting pipeline to pause returned %d", result);
					}

					result = gst_element_set_state( pMgr->m_teePipeline, GST_STATE_PLAYING );
					if(result != GST_STATE_CHANGE_SUCCESS)
					{
						CSIO_LOG(eLogLevel_debug, "CamTee: Lost clock - setting pipeline to play returned %d", result);
					}
				}
			}
			break;


		case GST_MESSAGE_WARNING:
			gst_message_parse_warning( msg, &err, &debug_info );
			if( debug_info )
			{
				g_free( debug_info );
			}

			g_clear_error( &err );
			break;

		case GST_MESSAGE_ERROR:
			gst_message_parse_error( msg, &err, &debug_info );

			CSIO_LOG(eLogLevel_error,  "CamTee: Error received from element %s: %s: error code = %d",
			GST_OBJECT_NAME(msg->src), err->message, err->code );
			CSIO_LOG(eLogLevel_error,  "CamTee: Debugging information: %s\n", debug_info ? debug_info : "none" );

			g_clear_error( &err );
			g_free( debug_info );
			if(pMgr)
			{
				if( pMgr->m_teeLoop )
					g_main_loop_quit( pMgr->m_teeLoop );
			}
			break;

		case GST_MESSAGE_EOS:
			CSIO_LOG(eLogLevel_debug,  "CamTee: End-Of-Stream" );
			if(pMgr)
			{
				GstPad *sinkpad;
				sinkpad = gst_element_get_static_pad (pMgr->m_teeSource, "sink");
				gst_pad_send_event (sinkpad, gst_event_new_eos ());
				gst_object_unref (sinkpad);

				if( pMgr->m_teeLoop )
					g_main_loop_quit( pMgr->m_teeLoop );
			}
			break;

		case GST_MESSAGE_STATE_CHANGED:
			if( GST_MESSAGE_SRC(msg) == GST_OBJECT( pMgr->m_teePipeline ) )
			{
				GstState old_state, new_state, pending_state;
				gst_message_parse_state_changed( msg, &old_state, &new_state,
				&pending_state );
				CSIO_LOG(eLogLevel_verbose, "CamTee: Pipeline state changed from %s to %s",
				gst_element_state_get_name( old_state ),
				gst_element_state_get_name( new_state) );
			}
			break;

		case GST_MESSAGE_ELEMENT:
			const GstStructure *structure = gst_message_get_structure (msg);
			const gchar *filename;
			if (gst_structure_has_name (structure, "GstMultiFileSink"))
			{
				filename = gst_structure_get_string (structure, "filename" );
				CSIO_LOG(eLogLevel_verbose, "CamTee: queued %s", filename );
				pMgr->m_snapobj->m_lastJpegBuffer_tm.tv_sec = 0;
				pMgr->m_snapobj->updatelink( filename );
			}

			break;
	}

	return TRUE;
}

void CStreamoutManager::createMainContext(void)
{
	m_context = g_main_context_new ();
	g_main_context_push_thread_default(m_context);
}

void CStreamoutManager::freeMainContext(void)
{
    if(m_context)
	{
        g_main_context_pop_thread_default(m_context);
        g_main_context_unref (m_context);
        m_context = NULL;
	}
}

GMainLoop *CStreamoutManager::createMainLoop(void)
{
	if(m_context)
	{
		return( g_main_loop_new(m_context, FALSE) );
	}
	else
	{
		CSIO_LOG(eLogLevel_error, "SnapShot context is NULL" );
		return( NULL );
	}
}

void* CStreamoutManager::CameraTeeEntry()
{
	const int c_usleep_1s = 1000000;
	int  iStatus  = 0;

	CSIO_LOG(eLogLevel_verbose,  "Starting camera Tee..." );

	while( !m_bExit )
	{
		createMainContext();
		if( (m_teeLoop = createMainLoop()) )
		{
			m_teePipeline = gst_pipeline_new( NULL );
			m_teeSource   = gst_element_factory_make( "appsrc",  NULL );
			m_tee         = gst_element_factory_make( "tee",     NULL );
			m_teeQ        = gst_element_factory_make( "queue",   NULL );
			m_sink        = gst_element_factory_make( "fakesink",NULL );

			if( !m_teePipeline || !m_teeSource || !m_tee || !m_teeQ || !m_sink )
			{
				iStatus = -1;
				CSIO_LOG( eLogLevel_error, "CamTee: Cannot create tee pipeline elements" );
			}
			else
			{
				gst_bin_add_many( GST_BIN( m_teePipeline ), m_teeSource, m_tee, m_teeQ, m_sink, NULL );
				int width  = atoi(m_res_x);
				int height = atoi(m_res_y);
				int iFramerate = atoi(m_frame_rate);

				CSIO_LOG( eLogLevel_verbose, "CamTee: width = %d, height = %d, framerate = %d\n", width, height, iFramerate );

				g_object_set( G_OBJECT (m_teeSource), "caps",
					gst_caps_new_simple (
					 "video/x-raw",
					 "format",    G_TYPE_STRING, "NV21",
					 "width",     G_TYPE_INT, width,
					 "height",    G_TYPE_INT, height,
					 "framerate", GST_TYPE_FRACTION, 0, 1, //variable
					  NULL), NULL );

				if( !gst_element_link_many( m_teeSource, m_tee, m_teeQ, m_sink, NULL ))
				{
					CSIO_LOG( eLogLevel_error,  "CamTee: Cannot link tee elements." );
					iStatus = -2;
				}
				else
				{
					g_object_set(G_OBJECT (m_teeSource),
					"stream-type", 0,
					"format",      GST_FORMAT_TIME, NULL);

					m_bus    = gst_pipeline_get_bus( GST_PIPELINE( m_teePipeline ) );
					m_bus_id = gst_bus_add_watch( m_bus, cb_teeGstMsgHandler, this );

					g_signal_connect( m_teeSource, "need-data",   G_CALLBACK (cb_teeNeedData),   this);
					g_signal_connect( m_teeSource, "enough_data", G_CALLBACK (cb_teeEnoughData), this);

					if( (gst_element_set_state( m_teePipeline, GST_STATE_PLAYING)) == GST_STATE_CHANGE_FAILURE )
					   CSIO_LOG(eLogLevel_error,  "CamTee: Cannot start tee pipeline" );
					else
						CSIO_LOG(eLogLevel_debug,  "CamTee: Tee server started" );
					g_main_loop_run( m_teeLoop );

					CSIO_LOG(eLogLevel_verbose,  "CamTee: exited tee mainloop" );
				}
			}
		}

		freeMainContext();
		usleep( c_usleep_1s );
	}

	CSIO_LOG(eLogLevel_debug, "Exiting Tee thread...");
	pthread_exit( NULL );
	return(NULL);
}

void CStreamoutManager::cleanup(void)
{
	if(m_teePipeline != NULL)
	{
		gst_element_set_state( m_teePipeline, GST_STATE_NULL );

        GstStateChangeReturn rtn;
		gst_object_unref( m_teePipeline );
	}

    if( m_bus != NULL )
	{
		if( !gst_bus_remove_watch( m_bus) )
		{
			CSIO_LOG(eLogLevel_error, "CamTee: Cannot remove tee msg handler %d", m_bus_id);
		}

		gst_object_unref( m_bus );
		m_bus    = NULL;
		m_bus_id = -1;
	}

    if(m_teeLoop != NULL)
    {
        g_main_loop_unref( m_teeLoop );
    }

    m_teeLoop     = NULL;
    m_teePipeline = NULL;
    m_teeSource   = NULL;
	m_tee         = NULL;
	m_teeQ        = NULL;
	m_sink        = NULL;
}

int CStreamoutManager::stopTee(void)
{
	int iRtn = 0;

	StopSnapShot(this);	//stop snapshots
	StopPreview(this);	//stop preview

	lockTee();
	m_bStopTeeInProgress = true; //stop pushing frames
	m_bExit = true;
	if( m_teeLoop )
	{
		g_main_loop_quit( m_teeLoop );

    	int   iRtn;
    	void* tResults;

    	CSIO_LOG(eLogLevel_verbose,  "CamTee: wait for thread to exit." );
        iRtn = pthread_join( m_tCameraTeeId, &tResults );
        CSIO_LOG(eLogLevel_verbose,  "Tee thread exited. Status = %d", iRtn );
	}
	else
	{
		CSIO_LOG(eLogLevel_verbose,  "Tee loop is NULL" );
		iRtn = -1;
	}

	unlockTee();

	return(iRtn);
}

int CStreamoutManager::startTee(void)
{
	int iRtn = 0;

    if( !CreateTeeThread() )
    {
    	CSIO_LOG(eLogLevel_error, "CamTee: Failed to start camera tee thread");
    	iRtn = -1;
    }
    else
    {
    	startClient();
    	StartSnapShot(this);	//start snapshots
    	StartPreview(this);		//start preview
    }

    return( iRtn );
}
