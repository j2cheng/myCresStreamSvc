/**
 * Copyright (C) 2016 to the present, Crestron Electronics, Inc.
 * All rights reserved.
 * No part of this software may be reproduced in any form, machine
 * or natural, without the express written consent of Crestron Electronics.
 *
 * \file        cresStreamSnapShot.cpp
 *
 * \brief       Implementation of stream snapshot
 *
 * \author      John Freeman
 *
 * \date        10/17/2016
 *
 * \note
 */

///////////////////////////////////////////////////////////////////////////////
#include <stdio.h>
#include <stdlib.h>
#include <sys/stat.h>
#include <string.h>
#include <unistd.h>
#include <sys/resource.h>
#include <sys/socket.h>
#include <gst/app/gstappsrc.h>
#include <sys/types.h>
#include <fts.h>

#include "cresStreamOutManager.h"
#include "cresStreamSnapShot.h"
#define PROC_SELF_FD_FILEPATH 	("/proc/self/fd")

int StartSnapShot(CStreamoutManager *pMgr)
{
	int iRtn = 0;

	pMgr->m_snapobj = new SnapShot(pMgr);

    if( pthread_create(&pMgr->m_tSnapShotId, NULL, snapshotThread, pMgr) )
    {
    	CSIO_LOG(eLogLevel_error, "SnapShot: Failed to start snapshot server");
    	iRtn = -1;
    }

    return( iRtn );
}

void StopSnapShot(CStreamoutManager *pMgr)
{
	if(pMgr->m_snapobj)
	{
		pMgr->m_snapobj->stop();

    	int   iRtn;
    	void* tResults;

    	CSIO_LOG(eLogLevel_debug,  "StopSnapShot: wait for thread to exit.\n" );
        iRtn = pthread_join( pMgr->m_tSnapShotId, &tResults );
        CSIO_LOG(eLogLevel_debug,  "Snapshot thread exited. Status = %d\n", iRtn );
        delete pMgr->m_snapobj;
        pMgr->m_snapobj = NULL;
	}
	else
	{
		CSIO_LOG(eLogLevel_error, "SnapShot is NULL\n");
	}
}

void StopSnapShotClient(CStreamoutManager *pMgr)
{
	CSIO_LOG(eLogLevel_debug, " StopSnapShotClient: stop client...\n");
	if(pMgr->m_snapobj)
	{
		pMgr->m_snapobj->stopClient();
	}
	else
	{
		CSIO_LOG(eLogLevel_error, "SnapShot client is NULL\n");
	}

	CSIO_LOG(eLogLevel_debug, " StopSnapShotClient: stop client done.\n");
}

SnapShot::SnapShot(void *arg) : m_loop(NULL),m_pipeline(NULL),m_source(NULL),
m_videoparse(NULL),m_sink(NULL),m_context(NULL),m_bExit(false),
m_update_period_secs(CRES_SNAPSHOT_UPDATE_PERIOD_SECS),
m_rawfilesqueue_size(CRES_SNAPSHOT_SNAPSHOT_QUEUE_SIZE),m_bus(NULL),m_bus_id(0),
m_clientPipeline(NULL),m_bExitClient(false),m_clientLoop(NULL),m_clientSink(NULL),
m_bNeedData(false),m_lastJpegBuffer_tm({0,0}),m_bStopInProgress(false), m_snapshot_name_updated(false)
{
	mLock  = new Mutex();
	mULock = new Mutex();

	CStreamoutManager * pMgr = (CStreamoutManager *) arg;
	m_snapshot_name = pMgr->m_snapshot_name;

	struct stat sb;
	if( stat( CRES_SNAPSHOT_RAMDISK, &sb ) != 0 )
	{
	  //raw snapshot buffer does not exist. Create it
		if( mkdir( CRES_SNAPSHOT_RAMDISK, S_IRWXU | S_IRWXG | S_IROTH | S_IXOTH) != 0 )
		{
			CSIO_LOG(eLogLevel_error, "SnapShot: Cannot create frames disk - %s", strerror(errno));
		}
	}
	else
	{
	  //delete all files in frames queue, leave folder
	    mULock->lock();
		deleteAllFiles( CRES_SNAPSHOT_RAMDISK );
	    mULock->unlock();
	}

	if( stat( CRES_SNAPSHOT_WEB_RAMDISK, &sb ) != 0 )
	{
	  //JPEG snapshot buffer does not exist. Create it
		if( mkdir( CRES_SNAPSHOT_WEB_RAMDISK, S_IRWXU | S_IRWXG | S_IROTH | S_IXOTH) != 0 )
		{
			CSIO_LOG(eLogLevel_error, "SnapShot: Cannot create JPEG web buffer - %s", strerror(errno));
		}
	}
	else
	{
		//clean snapshot files, leave folder
		mULock->lock();
		deleteAllFiles( CRES_SNAPSHOT_WEB_RAMDISK );
		mULock->unlock();
	}
}

SnapShot::~SnapShot()
{
	if(m_pipeline != NULL)
	{
		gst_object_unref( m_pipeline );
	}

    if(mLock)
        delete mLock;

    if(mULock)
    	delete mULock;
}

void *snapshotThread(void *arg)
{
	CStreamoutManager *pMgr = (CStreamoutManager *) arg;

	pMgr->m_snapobj->start( pMgr );

	CSIO_LOG(eLogLevel_verbose, "Exiting SnapShot thread...\n");
	pthread_exit( NULL );
	return(NULL);
}

void SnapShot::setUpdateRate(int period_in_seconds)
{
	mULock->lock();
	m_update_period_secs = period_in_seconds;
	mULock->unlock();
}

int  SnapShot::getUpdateRate(void)
{
	int iInterval;

	mULock->unlock();
	iInterval = m_update_period_secs;
	mULock->unlock();

	return( iInterval );
}

void SnapShot::setQueueSize(int snapshot_queue_size)
{
	mULock->unlock();
	m_rawfilesqueue_size = snapshot_queue_size;
	mULock->unlock();
}

int  SnapShot::getQueueSize(void)
{
	int iSize;

	mULock->unlock();
	iSize = m_rawfilesqueue_size;
	mULock->unlock();

	return( iSize );
}

GMainLoop *SnapShot::createMainLoop(void)
{
	if(m_context)
	{
		return( g_main_loop_new(m_context, FALSE) );
	}
	else
	{
		CSIO_LOG(eLogLevel_error, "SnapShot context is NULL\n" );
		return( NULL );
	}
}

void SnapShot::stop(void)
{
	mULock->lock();
	m_bStopInProgress = true; //stop pushing frames
	mULock->unlock();

	m_bExit = true;
	if( m_loop )
		g_main_loop_quit( m_loop );
}

void SnapShot::stopClient(void)
{
	m_bExitClient = true;
	if( m_clientLoop )
	{
		g_main_loop_quit( m_clientLoop );
	}
	else
		CSIO_LOG(eLogLevel_error, "SnapShot: client main loop is NULL\n" );
}

void SnapShot::createMainContext(void)
{
	m_context = g_main_context_new ();
	g_main_context_push_thread_default(m_context);
}

void SnapShot::freeMainContext(void)
{
    if(m_context)
	{
        g_main_context_pop_thread_default(m_context);
        g_main_context_unref (m_context);
        m_context = NULL;
	}
}

void snapshotNeedData (GstElement *appsrc, guint unused_size, gpointer user_data)
{
	//CSIO_LOG(eLogLevel_verbose, "Streamout: cb_need_data...\n");
	SnapShot *pSnapshot = (SnapShot*) user_data;

	pSnapshot->mLock->lock();
	pSnapshot->m_bNeedData = true;
	pSnapshot->mLock->unlock();
}

void snapshotEnoughData (GstElement *appsrc, guint unused_size, gpointer user_data)
{
	//CSIO_LOG(eLogLevel_verbose, "Streamout: cb_enough_data...\n");
	SnapShot *pSnapshot = (SnapShot*) user_data;

	pSnapshot->mLock->lock();
	pSnapshot->m_bNeedData = false;
	pSnapshot->mLock->unlock();
}

int SnapShot::start(void *arg)
{
	const int c_usleep_1s    = 1000000;
	const int c_usleep_100ms = 100000;

	CSIO_LOG(eLogLevel_verbose,  "SnapShot...\n" );

	CStreamoutManager *pMgr = (CStreamoutManager *) arg;

	startClient(pMgr);

	int  iStatus  = 0;
	while (!m_bExit)
	{
		createMainContext();
		if( (m_loop = createMainLoop()) )
		{
			m_pipeline    = gst_pipeline_new( "snapshot-appsrc-pipeline" );
			m_source      = gst_element_factory_make( "appsrc",       NULL );
			m_videoparse  = gst_element_factory_make( "videoconvert", NULL );
			m_encoder     = gst_element_factory_make( "jpegenc",      NULL );
			m_sink        = gst_element_factory_make( "multifilesink",NULL );

			if( !m_pipeline || !m_source || !m_videoparse || !m_encoder || !m_sink )
			{
				iStatus = -1;
				CSIO_LOG(eLogLevel_error, "SnapShot: Cannot create pipeline elements\n" );
			}
			else
			{

				int xRes = atoi(pMgr->m_res_x);
				int yRes = atoi(pMgr->m_res_y);
				int iFramerate = atoi(pMgr->m_frame_rate);

				g_object_set (G_OBJECT (m_source), "caps",
					gst_caps_new_simple (
							     "video/x-raw",
							     "format",    G_TYPE_STRING, "NV21",
								 "width",     G_TYPE_INT, xRes,
								 "height",    G_TYPE_INT, yRes,
								 "framerate", GST_TYPE_FRACTION, 0, 1, //variable
								 NULL), NULL);

				gst_bin_add_many( GST_BIN( m_pipeline ), m_source, m_videoparse, m_encoder,
				m_sink, NULL );

				if(!gst_element_link_many( m_source, m_videoparse, m_encoder, m_sink, NULL ))
				{
					CSIO_LOG(eLogLevel_error,  "SnapShot: Cannot link elements.\n" );
					iStatus = -2;
				}
			}

			if( !iStatus )
			{
				g_object_set (G_OBJECT (m_source),
				"stream-type", 0,
				"format", GST_FORMAT_TIME, NULL);

				g_object_set (G_OBJECT (m_sink),
				"index", 1,
				"next-file", 0, //new file every buffer
				"max-files", getQueueSize(), //jpg file queue size
				"post-messages", true,
				NULL);

				m_bus = gst_pipeline_get_bus( GST_PIPELINE( m_pipeline ) );
				m_bus_id = gst_bus_add_watch( m_bus, snapshotGstMsgHandler, this );
				gst_object_unref( m_bus );

				char *pBuf = new char[512];
				snprintf(pBuf, 512, "%s/%s%%d.jpg", CRES_SNAPSHOT_RAMDISK, CRES_SNAPSHOT_FILE_BASE );
				g_object_set( G_OBJECT( m_sink), "location", pBuf, NULL );
				CSIO_LOG(eLogLevel_verbose,  "SnapShot: JPG output frames = %s\n", pBuf );
				delete [] pBuf;

				g_signal_connect (m_source, "need-data",   G_CALLBACK (snapshotNeedData),   this);
				g_signal_connect (m_source, "enough_data", G_CALLBACK (snapshotEnoughData), this);

				CSIO_LOG(eLogLevel_verbose,  "SnapShot: Starting pipeline...\n" );
				if( (gst_element_set_state( m_pipeline, GST_STATE_PLAYING)) == GST_STATE_CHANGE_FAILURE )
				   CSIO_LOG(eLogLevel_error,  "SnapShot: Cannot start pipeline\n" );
				else
					CSIO_LOG(eLogLevel_debug,  "SnapShot: server started\n" );
				g_main_loop_run( m_loop );

				CSIO_LOG(eLogLevel_verbose,  "Snapshot: exited mainloop\n" );
			}
		}

		cleanup();

		freeMainContext();
		usleep(c_usleep_100ms);
	}

	CSIO_LOG(eLogLevel_debug,  "Snapshot: server stopped\n" );

	return( iStatus );
}

void SnapShot::cleanup(void)
{
	if(m_pipeline != NULL)
	{
		gst_element_set_state( m_pipeline, GST_STATE_NULL );

        GstStateChangeReturn rtn;
		gst_object_unref( m_pipeline );
	}

    if(m_bus != NULL)
	{
		if( !gst_bus_remove_watch (m_bus) )
		{
			CSIO_LOG(eLogLevel_error, "Cannot remove msg handler %d", m_bus_id);
		}

		gst_object_unref( m_bus );
		m_bus = NULL;
		m_bus_id = -1;
	}

    if(m_loop != NULL)
    {
        g_main_loop_unref( m_loop );
    }

	m_loop     = NULL;
	m_pipeline = NULL;
	m_source   = NULL;
	m_videoparse  = NULL;
	m_encoder  = NULL;
	m_sink     = NULL;

	m_lastJpegBuffer_tm = {0,0};

    mULock->lock();
	deleteAllFiles( CRES_SNAPSHOT_RAMDISK );
	deleteAllFiles( CRES_SNAPSHOT_WEB_RAMDISK );
    mULock->unlock();

// TODO: If we ever use eventfd elsewhere we will need to make sure that we dont close it here
//	 In Gstreamer 1.8.x a anon_inode[eventfd] leak was introduced, we must close these as well
	DIR *fdFolder;
	struct dirent *next_file;
	char tmp[512];
	char filepath[256];
	ssize_t ret;

	fdFolder = opendir(PROC_SELF_FD_FILEPATH);

	if (fdFolder != NULL)
	{
		while ( (next_file = readdir(fdFolder)) != NULL )
		{
			// build the path for each file in the folder
			sprintf(filepath, "%s/%s", PROC_SELF_FD_FILEPATH, next_file->d_name);

			ret = readlink(filepath, (char *) &tmp[0], sizeof(tmp));
			if (ret >= 0)
			{
				tmp[ret] = '\0';	// readlink will not terminate buf w/ NULL

				if (strstr((char *) &tmp[0], "anon_inode:[eventfd]") != NULL)
				{
					char *end;

					int fd = (int)strtol(next_file->d_name, &end, 10);        //10 specifies base-10
					if (end != next_file->d_name)     //if no characters were converted these pointers are equal
					{
						CSIO_LOG(eLogLevel_verbose, "SnapShot: Closing anon_inode fd %d", fd);
						if (close(fd) == -1)
							CSIO_LOG(eLogLevel_error, "SnapShot: Could not close fd %d, errno = %d", fd, errno);
					}
				}
			}
		}
		closedir(fdFolder);
	}
}

int SnapShot::startClient(void *arg)
{
	const int c_usleep_1s = 1000000;
	char pipeline[1024];
	CStreamoutManager *pMgr = (CStreamoutManager *) arg;
	m_clientLoop = NULL;
	GstBus    *bus = NULL;
	guint      bus_id;
	GstElement *source   = NULL;

	int  iStatus  = 0;

	CSIO_LOG(eLogLevel_verbose,  "SnapShot startClient...\n" );

	while (!m_bExit && !m_bExitClient)
	{
		m_clientLoop = g_main_loop_new( NULL, FALSE );

		if( m_clientLoop  )
		{
			m_clientPipeline = gst_pipeline_new( "snapshot-clientpipeline" );
			source       = gst_element_factory_make( "rtspsrc", NULL );
			m_clientSink = gst_element_factory_make( "fakesink",NULL );

			if( !m_clientPipeline || !source || !m_clientSink )
			{
				iStatus = -1;
				CSIO_LOG(eLogLevel_error, "SnapShot: Cannot create snapshot client pipeline elements\n" );
			}
			else
			{
				gst_bin_add_many( GST_BIN( m_clientPipeline ), source, m_clientSink, NULL );

				char *pBuf = new char[512];
				snprintf(pBuf, 512, "rtsp://127.0.0.1:%s/%s.sdp", pMgr->m_rtsp_port, pMgr->m_stream_name );
				g_object_set( G_OBJECT( source), "location",pBuf, NULL );
				CSIO_LOG(eLogLevel_verbose,  "camera URL = %s\n", pBuf );
				delete [] pBuf;

				g_signal_connect( source, "pad-added", G_CALLBACK( snapShotClientPadAdded ),this );
			}

			if( !iStatus )
			{
				bus = gst_pipeline_get_bus( GST_PIPELINE( m_clientPipeline ) );
				bus_id = gst_bus_add_watch( bus, snapshotClientGstMsgHandler, this );

				CSIO_LOG(eLogLevel_verbose,  "SnapShot: Starting client pipeline...\n" );
				if( (gst_element_set_state( m_clientPipeline, GST_STATE_PLAYING)) == GST_STATE_CHANGE_FAILURE )
				   CSIO_LOG(eLogLevel_error,  "Cannot start client pipeline\n" );
				else
					CSIO_LOG(eLogLevel_debug,  "SnapShot: client started\n" );

				g_main_loop_run( m_clientLoop );
				CSIO_LOG(eLogLevel_verbose,  "Snapshot: exited client mainloop\n" );
			}
		}

		if(m_clientPipeline != NULL)
		{
			gst_element_set_state( m_clientPipeline, GST_STATE_NULL );

	        GstStateChangeReturn rtn;
			gst_object_unref( m_clientPipeline );
		}

	    if(bus != NULL)
		{
			if( !gst_bus_remove_watch (bus) )
			{
				CSIO_LOG(eLogLevel_error, "Cannot remove client msg handler %d", bus_id);
			}

			gst_object_unref( bus );
			bus = NULL;
			bus_id = -1;
		}

	    if(m_clientLoop != NULL)
	    {
	        g_main_loop_unref( m_clientLoop );
	    }

	    m_clientLoop     = NULL;
		m_clientPipeline = NULL;
		source       = NULL;
		m_clientSink = NULL;

		usleep( c_usleep_1s );
	}

	CSIO_LOG(eLogLevel_debug,  "Snapshot: client stopped\n" );

	return( iStatus );
}

int SnapShot::updatelink(const gchar *filename)
{
	int iStatus = 0;
	int Rtn;

	mULock->lock();

	// Remove all symlink files in dir if updated
	if (m_snapshot_name_updated)
	{
		m_snapshot_name_updated = false;
		deleteAllFiles(CRES_SNAPSHOT_WEB_RAMDISK);
	}

	char *pJpegFile = new char[512];
	snprintf( pJpegFile, 512, "%s/%s.jpg", CRES_SNAPSHOT_WEB_RAMDISK, m_snapshot_name );
	if( access( pJpegFile, F_OK ) == 0 )
	{
		Rtn = unlink( pJpegFile );
		if( Rtn )
		{
			CSIO_LOG(eLogLevel_error,  "SnapShot: Cannot unlink %s: %s\n", pJpegFile, strerror(errno) );
			iStatus = -1;
		}
	}

	Rtn = symlink( filename, pJpegFile );
	if( Rtn )
	{
		CSIO_LOG(eLogLevel_verbose,  "SnapShot: Cannot link %s to %s:, %s\n", pJpegFile, filename, strerror(errno) );
		iStatus = -2;
		if( strcasestr( strerror(errno), "File exists" ) )
		{
		  //delete old link then retry
			Rtn = remove( pJpegFile );
			Rtn = symlink( filename, pJpegFile );
			if( !Rtn )
				CSIO_LOG(eLogLevel_verbose,  "SnapShot: Retry link. %s to %s - Link successful\n", pJpegFile, filename );
			else
				CSIO_LOG(eLogLevel_error,  "SnapShot: Cannot link %s to %s:, %s\n", pJpegFile, filename, strerror(errno) );
		}
	}

	delete [] pJpegFile;

	mULock->unlock();

	return(iStatus);
}

GMainLoop *SnapShot::getMainLoop(void)
{
	return( m_loop );
}

GstElement *SnapShot::getPipeline(void)
{
	return( m_pipeline );
}

GstElement *SnapShot::getClientPipeline(void)
{
	return( m_clientPipeline );
}

GMainLoop *SnapShot::getClientMainLoop(void)
{
	return( m_clientLoop );
}

int SnapShot::saveRawFrame (GstPad *pad, GstPadProbeInfo *buffer, gpointer user_data)
{
	int iStatus = 0;

	//CSIO_LOG(eLogLevel_debug, "SnapShot: save_raw_frame...\n");
	CStreamoutManager *pMgr = (CStreamoutManager *) user_data;

	if(pMgr->m_snapobj)
	{
		pMgr->m_snapobj->mULock->lock();
		if(!pMgr->m_snapobj->m_bStopInProgress)
		{
			struct timespec currentTime;

			gboolean bNeedData;
			int      nDataPushes;
			pMgr->m_snapobj->mLock->lock();
			bNeedData = pMgr->m_snapobj->m_bNeedData;
			pMgr->m_snapobj->mLock->unlock();

			int update_period_secs = getUpdateRate();

			clock_gettime(CLOCK_REALTIME, &currentTime);

			if (m_lastJpegBuffer_tm.tv_sec == 0)
			{
				m_lastJpegBuffer_tm.tv_sec = currentTime.tv_sec; //start new interval timer
			}
			else if ((currentTime.tv_sec - m_lastJpegBuffer_tm.tv_sec) >= update_period_secs)
			{
				if( bNeedData )
				{
					GstBuffer *buf;
					GstMapInfo info;
					buf = GST_PAD_PROBE_INFO_BUFFER(buffer);
					gst_buffer_map(buf,&info,GST_MAP_READ);

					guint size;
					GstFlowReturn ret;

					GstBuffer *DstBuffer;
					DstBuffer = gst_buffer_copy( buf );

					g_signal_emit_by_name (pMgr->m_snapobj->m_source, "push-buffer", DstBuffer, &ret);
					gst_buffer_unref (DstBuffer);

					if( ret != GST_FLOW_OK )
					{
						CSIO_LOG(eLogLevel_debug, "SnapShot: saveRawFrame buffer push failed...restart appsrc\n");
						g_main_loop_quit(pMgr->m_snapobj->m_loop);
					}

					gst_buffer_unmap(buf,&info);
				}
			}
		}

		pMgr->m_snapobj->mULock->unlock();
	}

    return( iStatus );
}

int SnapShot::deleteAllFiles( const char *dir, bool bRmdirectory )
{
    int ret   = 0;
    FTS *ftsp = NULL;
    FTSENT     *curr;

    char *files[] = { (char *) dir, NULL };

    ftsp = fts_open( files, FTS_NOCHDIR | FTS_PHYSICAL | FTS_XDEV, NULL );
    if( ftsp )
    {
		while( ( curr = fts_read(ftsp)) )
		{
			switch( curr->fts_info )
			{
				case FTS_NS:
				case FTS_DNR:
				case FTS_ERR:
					CSIO_LOG(eLogLevel_error, "%s: fts_read error: %s\n",
							curr->fts_accpath, strerror(curr->fts_errno));
					break;

				case FTS_DC:
				case FTS_DOT:
				case FTS_NSOK:
				  //Not reached unless FTS_LOGICAL, FTS_SEEDOT, or FTS_NOSTAT were passed to fts_open
					break;

				case FTS_D:
				  //Do nothing. Need depth-first search, so directories are deleted in FTS_DP
					break;

				case FTS_DP:
					if( !bRmdirectory )
						break;
				case FTS_F:
				case FTS_SL:
				case FTS_SLNONE:
				//case FTS_DEFAULT:
					if( remove( curr->fts_accpath ) < 0 )
					{
						CSIO_LOG(eLogLevel_error, "%s: Failed to remove: %s\n",	curr->fts_path, strerror(errno));
						ret = -1;
					}
					break;
			}
		}

		fts_close( ftsp );
    }
    else
    {
    	CSIO_LOG(eLogLevel_error, "%s: fts_open failed: %s\n", dir, strerror(errno) );
        ret = -1;
    }

    return ret;
}

gboolean snapshotGstMsgHandler(GstBus *bus, GstMessage *msg, void *arg)
{
	SnapShot *pSnapshot = (SnapShot*) arg;
	GError    *err;
	gchar     *debug_info = NULL;
	GMainLoop *pMainloop = NULL;

	switch (GST_MESSAGE_TYPE(msg))
	{
		case GST_MESSAGE_NEW_CLOCK:
			GstClock *clock;

			gst_message_parse_new_clock (msg, &clock);

			CSIO_LOG(eLogLevel_verbose, "SnapShot: New clock: %s\n", (clock ? GST_OBJECT_NAME (clock) : "NULL"));
			break;

		case GST_MESSAGE_CLOCK_LOST:
			CSIO_LOG(eLogLevel_verbose, "SnapShot: Clock lost, selecting a new one\n");
			if(pSnapshot)
			{
				if(pSnapshot->getPipeline())
				{
					GstStateChangeReturn result;
					result = gst_element_set_state (pSnapshot->getPipeline(), GST_STATE_PAUSED);
					if(result != GST_STATE_CHANGE_SUCCESS)
					{
						CSIO_LOG(eLogLevel_verbose, "SnapShot: Lost clock - setting pipeline to pause returned %d\n", result);
					}

					result = gst_element_set_state (pSnapshot->getPipeline(), GST_STATE_PLAYING);
					if(result != GST_STATE_CHANGE_SUCCESS)
					{
						CSIO_LOG(eLogLevel_debug, "SnapShot: Lost clock - setting pipeline to play returned %d\n", result);
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

			CSIO_LOG(eLogLevel_error,  "SnapShot: Error received from element %s: %s: error code = %d\n",
			GST_OBJECT_NAME(msg->src), err->message, err->code );
			CSIO_LOG(eLogLevel_error,  "SnapShot: Debugging information: %s\n", debug_info ? debug_info : "none" );

			g_clear_error( &err );
			g_free( debug_info );
			if(pSnapshot)
			{
				pMainloop = pSnapshot->getMainLoop();
				if( pMainloop )
					g_main_loop_quit( pMainloop );
			}
			break;

		case GST_MESSAGE_EOS:
			CSIO_LOG(eLogLevel_debug,  "SnapShot: End-Of-Stream\n" );
			if(pSnapshot)
			{
				GstPad *sinkpad;
				sinkpad = gst_element_get_static_pad (pSnapshot->m_source, "sink");
				gst_pad_send_event (sinkpad, gst_event_new_eos ());
				gst_object_unref (sinkpad);

				pMainloop = pSnapshot->getMainLoop();
				if( pMainloop )
					g_main_loop_quit( pMainloop );
			}
			break;

		case GST_MESSAGE_STATE_CHANGED:
			if( GST_MESSAGE_SRC(msg) == GST_OBJECT( pSnapshot->getPipeline() ) )
			{
				GstState old_state, new_state, pending_state;
				gst_message_parse_state_changed( msg, &old_state, &new_state,
				&pending_state );
				CSIO_LOG(eLogLevel_verbose, "SnapShot: Pipeline state changed from %s to %s:\n",
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
				CSIO_LOG(eLogLevel_verbose, "SnapShot: queued %s", filename );
				pSnapshot->m_lastJpegBuffer_tm.tv_sec = 0;
				pSnapshot->updatelink( filename );
			}

			break;
	}

	return TRUE;
}

gboolean snapshotClientGstMsgHandler(GstBus *bus, GstMessage *msg, void *arg)
{
	SnapShot *pSnapshot   = (SnapShot*) arg;
	gchar     *debug_info = NULL;
	GMainLoop *pMainloop  = NULL;
	GError    *err;

	switch (GST_MESSAGE_TYPE(msg))
	{
		case GST_MESSAGE_NEW_CLOCK:
			GstClock *clock;

			gst_message_parse_new_clock (msg, &clock);

			CSIO_LOG(eLogLevel_verbose, "SnapShot: New client clock: %s\n", (clock ? GST_OBJECT_NAME (clock) : "NULL"));
			break;

		case GST_MESSAGE_CLOCK_LOST:
			CSIO_LOG(eLogLevel_verbose, "SnapShot: Client clock lost, selecting a new one\n");
			if(pSnapshot)
			{
				if(pSnapshot->getClientPipeline())
				{
					GstStateChangeReturn result;
					result = gst_element_set_state (pSnapshot->getClientPipeline(), GST_STATE_PAUSED);
					if(result != GST_STATE_CHANGE_SUCCESS)
					{
						CSIO_LOG(eLogLevel_verbose, "SnapShot: Lost clock - setting client pipeline to pause returned %d\n", result);
					}

					result = gst_element_set_state (pSnapshot->getClientPipeline(), GST_STATE_PLAYING);
					if(result != GST_STATE_CHANGE_SUCCESS)
					{
						CSIO_LOG(eLogLevel_debug, "SnapShot: Lost clock - setting client pipeline to play returned %d\n", result);
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

			CSIO_LOG(eLogLevel_error,  "SnapShot: Client error received from element %s: %s: error code = %d\n",
			GST_OBJECT_NAME(msg->src), err->message, err->code );
			CSIO_LOG(eLogLevel_error,  "SnapShot: Client debugging information: %s\n", debug_info ? debug_info : "none" );

			g_clear_error( &err );
			g_free( debug_info );
			if(pSnapshot)
			{
				pMainloop = pSnapshot->getClientMainLoop();
				if( pMainloop )
					g_main_loop_quit( pMainloop );
			}
			break;

		case GST_MESSAGE_EOS:
			CSIO_LOG(eLogLevel_debug,  "SnapShot: End-Of-Client-Stream\n" );
			if(pSnapshot)
			{
				pMainloop = pSnapshot->getClientMainLoop();
				if( pMainloop )
					g_main_loop_quit( pMainloop );
			}
			break;

		case GST_MESSAGE_STATE_CHANGED:
			if( GST_MESSAGE_SRC(msg) == GST_OBJECT( pSnapshot->getClientPipeline() ) )
			{
				GstState old_state, new_state, pending_state;
				gst_message_parse_state_changed( msg, &old_state, &new_state,
				&pending_state );
				CSIO_LOG(eLogLevel_verbose, "SnapShot: Pipeline client state changed from %s to %s:\n",
				gst_element_state_get_name( old_state ),
				gst_element_state_get_name( new_state) );
			}
			break;

		default:
			break;
	}

	return TRUE;
}

void snapShotClientPadAdded( GstElement *src, GstPad *new_pad, void *arg )
{
	SnapShot *pSnapshot   = (SnapShot*) arg;
   GstCaps      *new_pad_caps   = NULL;
   GstStructure *new_pad_struct = NULL;
   const gchar  *new_pad_type   = NULL;
   gchar        *mediaName      = NULL;
   gchar        *encodingName   = NULL;
   gchar        *payload        = NULL;

   CSIO_LOG(eLogLevel_verbose, "Received new pad '%s' from '%s':\n", GST_PAD_NAME (new_pad), GST_ELEMENT_NAME (src));

   new_pad_caps = gst_pad_query_caps( new_pad, NULL );
   new_pad_struct = gst_caps_get_structure( new_pad_caps, 0 );
   new_pad_type = gst_structure_get_name(new_pad_struct);
   CSIO_LOG(eLogLevel_verbose, "New pads type: '%s'\n", new_pad_type);

 //Extract type of pad directly if UDP
   if( strncmp( new_pad_type, "video/x-h264", sizeof("video/x-h264") ) == 0 )
   {
      mediaName = (gchar*)g_malloc( 6 );
      snprintf( mediaName, 6, "video" );
      encodingName = (gchar*)g_malloc(sizeof( "H264" ));
      snprintf(encodingName, sizeof( "H264" ), "%s", "H264" );
   }
   else
   {
      if( strncmp( new_pad_type, "audio/mpeg", sizeof( "audio/mpeg" ) ) == 0 )
      {
         mediaName = (gchar*)g_malloc( 6 );
         snprintf( mediaName, 6, "audio" );
         encodingName = (gchar*)g_malloc(sizeof( "MPEG4-GENERIC" ));
         snprintf(encodingName, sizeof( "MPEG4-GENERIC" ), "%s", "MPEG4-GENERIC" );
      }
   }

   const GValue* value = NULL;
   if( !mediaName )
      value = gst_structure_get_value( new_pad_struct, "media" );

   if( (value !=NULL) || mediaName )
   {
      if( !mediaName )
      {
         mediaName = gst_value_serialize( value );

         value = gst_structure_get_value( new_pad_struct, "encoding-name" );
         encodingName = gst_value_serialize( value );
         value = gst_structure_get_value( new_pad_struct, "payload" );
         payload = gst_value_serialize( value );

         if(encodingName == NULL)
         {
            // gst_rtp_jpeg_depay_sink_template has 2 entries, one without an encoding-name field.
            // So, in this case we check the
            if(strcmp(payload, "26") == 0)
            {
               CSIO_LOG(eLogLevel_debug, "SnapShot: setting encoding-name to JPEG\n");
               // 26 is for jpeg
               encodingName = (gchar*)g_malloc(16);
               snprintf(encodingName, 15, "JPEG");
            }
         }
      }

      if( mediaName )
      {
         //CSIO_LOG(eLogLevel_debug,  "Found media field:'%s'\r\n", mediaName );
         if (strcmp(mediaName, "video") == 0)
         {
          //video component detected
			CSIO_LOG(eLogLevel_verbose, "SnapShot: adding video pad...\n");
			GstPad  *sink_pad = NULL;
			sink_pad = gst_element_get_static_pad( pSnapshot->m_clientSink, "sink" );
			if( sink_pad )
			{
			   GstPadLinkReturn linkRtn = gst_pad_link( new_pad, sink_pad );
			   if( !GST_PAD_LINK_FAILED( linkRtn ) )
			   {
				   CSIO_LOG(eLogLevel_debug,  "SnapShot: Linked stream for client.\n" );
				   pSnapshot->stopClient();
			   }
			   else
				   CSIO_LOG(eLogLevel_debug, "SnapShot: Cannot link input pad\n");

			   gst_object_unref( sink_pad );
			}
			else
			   CSIO_LOG(eLogLevel_debug, "SnapShot: Cannot get first pad's SINK pad\n" );
         }
         else if( strcmp( mediaName, "audio") == 0 )
         {
          //audio component detected
         }
         else
         {
           CSIO_LOG(eLogLevel_error, "SnapShot: unknown media name %s\n", mediaName);
         }
      }

    //release any resources if necessary
      if( new_pad_caps != NULL )
         gst_caps_unref( new_pad_caps );

      if( mediaName != NULL )
         g_free( mediaName );

      if( encodingName != NULL )
         g_free( encodingName );

      if( payload  != NULL )
      {
        g_free( payload );
      }
   }
}
