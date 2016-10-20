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
    else
    {
    	CSIO_LOG(eLogLevel_debug, "SnapShot: Started snapshot server");
    }

    return( iRtn );
}

void StopSnapShot(CStreamoutManager *pMgr)
{
	if(pMgr->m_snapobj)
	{
		pMgr->m_snapobj->stop();
	}
	else
	{
		CSIO_LOG(eLogLevel_error, "SnapShot is NULL\n");
	}
}

void *snapshotThread(void *arg)
{
	CStreamoutManager *pMgr = (CStreamoutManager *) arg;

	pMgr->m_snapobj->start( pMgr );

	CSIO_LOG(eLogLevel_debug, "Exiting SnapShot...\n");
	pthread_exit( NULL );
	return(NULL);
}

void SnapShot::setStreamName(char* name)
{
	if( name != NULL)
		strncpy( m_stream_name, name, MAX_STR_LEN );
	else
		CSIO_LOG(eLogLevel_error, "SnapShot: setStreamName - name is NULL\n");
}


void SnapShot::setUpdateRate(int period_in_seconds)
{
	m_update_period_secs = period_in_seconds;
}

int  SnapShot::getUpdateRate(void)
{
	return(m_update_period_secs);
}

void SnapShot::setQueueSize(int snapshot_queue_size)
{
	m_rawfilesqueue_size = snapshot_queue_size;
}

int  SnapShot::getQueueSize(void)
{
	return(m_rawfilesqueue_size);
}

void SnapShot::signalRawFrameComplete(void)
{
	mLock->lock();
	mCond->signal();
	mLock->unlock();
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
	m_bExit = true;
	signalRawFrameComplete();
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

SnapShot::SnapShot(void *arg) : m_loop(NULL),m_pipeline(NULL),m_source(NULL),
m_videoparse(NULL),m_sink(NULL),m_context(NULL),m_bExit(false),m_snapIndex(-1),
m_rawbuffer_cnt(0),m_rawfileIdx(0),m_update_period_secs(CRES_SNAPSHOT_UPDATE_PERIOD_SECS),
m_rawfilesqueue_size(CRES_SNAPSHOT_SNAPSHOT_QUEUE_SIZE),m_bus(NULL),m_bus_id(0)
{
	mLock = new Mutex();

	mCond = new CondVar();

	strcpy(m_stream_name, CRES_SNAPSHOT_FILENAME_DEFAULT);

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
		char *pRawFrames = new char[512];
		snprintf( pRawFrames, 512, "%s/*", CRES_SNAPSHOT_RAMDISK );
		//CSIO_LOG(eLogLevel_debug, "JF- SnapShot: All files = %s", pRawFrames );
		int Rtn = remove( pRawFrames );
		delete [] pRawFrames;
	}

	if( stat( CRES_SNAPSHOT_WEB_RAMDISK, &sb ) != 0 )
	{
	  //JPEG snapshot buffer does not exist. Create it
		if( mkdir( CRES_SNAPSHOT_WEB_RAMDISK, S_IRWXU | S_IRWXG | S_IROTH | S_IXOTH) != 0 )
		{
			CSIO_LOG(eLogLevel_error, "SnapShot: Cannot create JPEG web buffer - %s", strerror(errno));
		}
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

    if(mCond)
        delete mCond;
}

int SnapShot::start(void *arg)
{
	const int c_usleep_50ms = 50000;
	char pipeline[1024];
	CStreamoutManager *pMgr = (CStreamoutManager *) arg;

	int  iStatus  = 0;
	int iFrameIndex = 0;
	while (!m_bExit)
	{
		createMainContext();

		iFrameIndex = waitRawFrame();
		if(iFrameIndex >= 0)
		{
			if( (m_loop = createMainLoop()) )
			{
				m_pipeline    = gst_pipeline_new( "snapshot-pipeline" );
				m_source      = gst_element_factory_make( "filesrc",      NULL );
				m_videoparse  = gst_element_factory_make( "videoparse",   NULL );
				m_encoder     = gst_element_factory_make( "jpegenc",      NULL );
				m_sink        = gst_element_factory_make( "filesink",NULL );

				if( !m_pipeline || !m_source || !m_videoparse || !m_encoder || !m_sink )
				{
					iStatus = -1;
					CSIO_LOG(eLogLevel_error, "SnapShot: Cannot create snapshot pipeline elements\n" );
				}
				else
				{
					gst_bin_add_many( GST_BIN( m_pipeline ), m_source, m_videoparse, m_encoder, m_sink, NULL );

					int xRes = atoi(pMgr->m_res_x);
					int yRes = atoi(pMgr->m_res_y);
					int iFramerate = atoi(pMgr->m_frame_rate);

					g_object_set( G_OBJECT( m_videoparse), "width",  xRes, NULL );
					g_object_set( G_OBJECT( m_videoparse), "height", yRes, NULL );
					g_object_set( G_OBJECT( m_videoparse), "format", GST_VIDEO_FORMAT_NV21, NULL );
					g_object_set( G_OBJECT( m_videoparse), "framerate", iFramerate,1, NULL );

					char *pBuf = new char[512];
					snprintf(pBuf, 512, "%s/%s%d", CRES_SNAPSHOT_RAMDISK, CRES_SNAPSHOT_FILE_BASE, iFrameIndex );
					g_object_set( G_OBJECT( m_source), "location",pBuf, NULL );
					CSIO_LOG(eLogLevel_verbose,  "Jpeg input file = %s\n", pBuf );
					snprintf(pBuf, 512, "%s/%s%d.jpg", CRES_SNAPSHOT_RAMDISK, CRES_SNAPSHOT_FILE_BASE, iFrameIndex );
					g_object_set( G_OBJECT( m_sink), "location", pBuf, NULL );
					CSIO_LOG(eLogLevel_verbose,  "Jpeg output file = %s\n", pBuf );
					updatelink( pBuf );

					delete [] pBuf;

					if(!gst_element_link_many( m_source, m_videoparse, m_encoder, m_sink, NULL ))
					{
						CSIO_LOG(eLogLevel_error,  "SnapShot: Cannot link to sink.\n" );
						iStatus = -2;
					}
				}

				if( !iStatus )
				{
					m_bus = gst_pipeline_get_bus( GST_PIPELINE( m_pipeline ) );
					m_bus_id = gst_bus_add_watch( m_bus, snapshotGstMsgHandler, this );
					gst_object_unref( m_bus );

					CSIO_LOG(eLogLevel_verbose,  "SnapShot: Starting pipeline...\n" );
					if( (gst_element_set_state( m_pipeline, GST_STATE_PLAYING)) == GST_STATE_CHANGE_FAILURE )
					   CSIO_LOG(eLogLevel_error,  "Cannot start pipeline\n" );

					g_main_loop_run( m_loop );
					CSIO_LOG(eLogLevel_verbose,  "Snapshot: exited mainloop\n" );
				}
			}

			cleanup();
		}

		freeMainContext();
		usleep( c_usleep_50ms );
	}


	return( iStatus );
}

void SnapShot::cleanup(void)
{

	if(m_pipeline != NULL)
	{
        GstStateChangeReturn rtn;
		gst_object_unref( m_pipeline );
	}

    if(m_bus != NULL)
	{
		 gst_object_unref( m_bus );
		 m_bus = NULL;
		 g_source_remove( m_bus_id ); //disconnect from mainloop msg context
	}

	m_loop     = NULL;
	m_pipeline = NULL;
	m_source   = NULL;
	m_videoparse  = NULL;
	m_encoder  = NULL;
	m_sink     = NULL;

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
						CSIO_LOG(eLogLevel_verbose, "Closing anon_inode fd %d", fd);
						if (close(fd) == -1)
							CSIO_LOG(eLogLevel_error, "Error: Could not close fd %d, errno = %d", fd, errno);
					}
				}
			}
		}
		closedir(fdFolder);
	}
}

int SnapShot::updatelink(char *filename)
{
	int iStatus = 0;
	int Rtn;

	char *pJpegFile = new char[512];
	snprintf( pJpegFile, 512, "%s/%s", CRES_SNAPSHOT_WEB_RAMDISK, m_stream_name );
	if( access( pJpegFile, F_OK ) == 0 )
	{
		Rtn = unlink( pJpegFile );
		if( Rtn )
		{
			CSIO_LOG(eLogLevel_error,  "Cannot unlink %s: %s\n", pJpegFile, strerror(errno) );
			iStatus = -1;
		}
	}

	Rtn = symlink( filename, pJpegFile );
	if( Rtn )
	{
		CSIO_LOG(eLogLevel_error,  "Cannot link %s to %s: %s\n", pJpegFile, filename, strerror(errno) );
		iStatus = -2;
	}

	delete [] pJpegFile;

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

int SnapShot::waitRawFrame(void)
{
	const int c_waitforframe_timeout_secs = 10;
    int               rc=-1;
    struct timespec   ts;

	clock_gettime(CLOCK_REALTIME, &ts);
	ts.tv_sec += c_waitforframe_timeout_secs;  // sec.
	mLock->lock();
	pthread_mutex_t *mutex = mLock->get_mutex_ptr();

  //wait for a specified period of time for buffers
	rc = mCond->waittimedcont( mutex, &ts );
	if( rc == ETIMEDOUT )
	{
		CSIO_LOG(eLogLevel_error, "SnapShot: camera stream timeout\n");
		mLock->unlock();
		rc = -1;
	}
	else if( rc == 0 )
	{
		rc = m_snapIndex;
	}
	else
	{
		rc = -1;
	}

	mLock->unlock();

    return( rc );
}

int SnapShot::saveRawFrame (GstPad *pad, GstPadProbeInfo *buffer, gpointer user_data)
{
	int iStatus = 0;

//	CSIO_LOG(eLogLevel_debug, "SnapShot: save_raw_data...\n", pName);
	CStreamoutManager *pMgr = (CStreamoutManager *) user_data;

    const  int c_rawbuffers_per_file = 2;
    const  int c_minbuffers_per_file = 2;
    struct timespec        currentTime_v;
    static struct timespec lastCheckedTime_v = {0,0};

    int update_period_secs = getUpdateRate();

    clock_gettime(CLOCK_REALTIME, &currentTime_v);

    if (lastCheckedTime_v.tv_sec == 0)
    {
    	lastCheckedTime_v.tv_sec = currentTime_v.tv_sec; //start interval timer
    }
    else if ((currentTime_v.tv_sec - lastCheckedTime_v.tv_sec) >= update_period_secs)
    {
		GstBuffer *buf;
		GstMapInfo info;
		buf = GST_PAD_PROBE_INFO_BUFFER(buffer);
		gst_buffer_map(buf,&info,GST_MAP_READ);

		int rawbufferqueue_size = getQueueSize();
		m_rawfileIdx %= rawbufferqueue_size;

		FILE *fp;
		char sFile[256];
		snprintf(sFile, sizeof(sFile), "%s/%s%d", CRES_SNAPSHOT_RAMDISK, CRES_SNAPSHOT_FILE_BASE, m_rawfileIdx );

		if( !m_rawbuffer_cnt )
		{
			fp = fopen( sFile, "w+" );
		}
		else
		{
			fp = fopen( sFile, "a+" );
		}
		if( fp != NULL )
		{
			int iBytes;
			m_rawbuffer_cnt++;
			if( (iBytes=fwrite( (guint8 *)info.data, sizeof(guint8), info.size, fp )) == info.size )
			{
				CSIO_LOG(eLogLevel_verbose, "SnapShot: save_raw_data: wrote %d of %d bytes to %s, buffers queued = %d\n",
				iBytes, info.size, sFile, m_rawbuffer_cnt);
				if( m_rawbuffer_cnt >= ((c_rawbuffers_per_file < c_minbuffers_per_file) ? c_minbuffers_per_file:c_rawbuffers_per_file) )
				{
					m_snapIndex = m_rawfileIdx;
					signalRawFrameComplete();
					m_rawfileIdx++;		  //next raw file index
					m_rawbuffer_cnt = 0;  //start new buffer collection
					lastCheckedTime_v.tv_sec = currentTime_v.tv_sec; //reset period
				}
			}
			else
			{
				CSIO_LOG(eLogLevel_error, "SnapShot: save_raw_data: ERROR: wrote %d bytes of %d, buffers queued = %d\n",
				iBytes, info.size, m_rawbuffer_cnt);
			}

			fflush( fp );
			fclose( fp );
		}
		else
		{
			CSIO_LOG(eLogLevel_error, "SnapShot: save_raw_data: Cannot open %s for writing\n", sFile);
			iStatus = -1;
		}

		gst_buffer_unmap(buf,&info);
    }

    return( iStatus );
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
			CSIO_LOG(eLogLevel_verbose,  "SnapShot: End-Of-Stream reached\n" );
			if(pSnapshot)
			{
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

		default:
			break;
	}

	return TRUE;
}
