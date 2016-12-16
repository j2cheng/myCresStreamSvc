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

SnapShot::SnapShot(void *arg) : m_loop(NULL),m_videotee(NULL),m_videoconv(NULL),m_videoscale(NULL),
m_videorate(NULL), m_videofilter(NULL),m_sink(NULL),m_context(NULL),m_bExit(false),
m_update_period_secs(CRES_SNAPSHOT_UPDATE_PERIOD_SECS),m_snapshotq(NULL),
m_rawfilesqueue_size(CRES_SNAPSHOT_SNAPSHOT_QUEUE_SIZE),m_pMgr(NULL),
m_bStopInProgress(false), m_snapshot_name_updated(false)
{
	mLock  = new Mutex();
	mULock = new Mutex();
	mCond  = new CondVar();
	mCond_mtx = new Mutex();

	CStreamoutManager * pMgr = (CStreamoutManager *) arg;
	m_snapshot_name = pMgr->m_snapshot_name;
	m_pMgr = pMgr;

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
    if(mLock)
        delete mLock;

    if(mULock)
    	delete mULock;

    if(mCond)
    	delete mCond;

    if(mCond_mtx)
    	delete mCond_mtx;
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

int SnapShot::add(void)
{
	int  iStatus  = 0;
	if(m_pMgr)
	{
		m_snapshotq   = gst_element_factory_make( "queue",         NULL );
		m_videoconv   = gst_element_factory_make( "videoconvert",  NULL );
		m_videoscale  = gst_element_factory_make( "videoscale",    NULL );
		m_videorate   = gst_element_factory_make( "videorate",     NULL );
		m_videofilter = gst_element_factory_make( "capsfilter",    NULL );
		m_encoder     = gst_element_factory_make( "jpegenc",       NULL );
		m_sink        = gst_element_factory_make( "multifilesink", NULL );

		if( !m_snapshotq || !m_videoconv || !m_videoscale || !m_videorate ||
		!m_videofilter || !m_encoder || !m_sink )
		{
			iStatus = -2;
			CSIO_LOG(eLogLevel_error, "SnapShot: Cannot create pipeline" );
		}
		else
		{
			int width  = atoi(m_pMgr->m_res_x);
			int height = atoi(m_pMgr->m_res_y);
			int iUpdateInterval = getUpdateRate();

			g_object_set( G_OBJECT(m_videofilter), "caps",
			gst_caps_new_simple(
				 "video/x-raw",
				 "format",    G_TYPE_STRING, "NV21",
				 "width",     G_TYPE_INT, width,
				 "height",    G_TYPE_INT, height,
				 "framerate", GST_TYPE_FRACTION, iUpdateInterval, iUpdateInterval,
				  NULL), NULL );

			gst_bin_add_many( GST_BIN( m_pMgr->m_teePipeline ), m_snapshotq, m_videoconv, m_videoscale, m_videorate, m_videofilter,
			m_encoder, m_sink, NULL );

			if(!gst_element_link_many( m_pMgr->m_tee, m_snapshotq, m_videoconv, m_videoscale, m_videorate, m_videofilter, m_encoder,
			m_sink, NULL ))
			{
				CSIO_LOG(eLogLevel_error,  "SnapShot: Cannot link elements.\n" );
				iStatus = -3;
			}
		}

		if( !iStatus )
		{
			g_object_set (G_OBJECT (m_sink),
			"index",         1,
			"next-file",     0, //new file every buffer
			"max-files",     getQueueSize(), //jpg file queue size
			"post-messages", true,
			NULL);

			char *pBuf = new char[512];
			snprintf(pBuf, 512, "%s/%s%%d.jpg", CRES_SNAPSHOT_RAMDISK, CRES_SNAPSHOT_FILE_BASE );
			g_object_set( G_OBJECT( m_sink), "location", pBuf, NULL );
			CSIO_LOG(eLogLevel_verbose,  "SnapShot: JPG output frames location = %s\n", pBuf );
			delete [] pBuf;

			CSIO_LOG(eLogLevel_verbose,  "SnapShot: added" );
		}
	}
	else
	{
		CSIO_LOG(eLogLevel_verbose,  "SnapShot: parent is NULL" );
		iStatus = -1;
	}

	return( iStatus );
}

int SnapShot::drop(void)
{
	int  iStatus = 0;

	gst_object_unref( m_snapshotq );
	gst_object_unref( m_videoconv );
	gst_object_unref( m_videoscale );
	gst_object_unref( m_videorate );
	gst_object_unref( m_videofilter );
	gst_object_unref( m_encoder );
	gst_object_unref( m_sink );

	m_snapshotq   = NULL;
	m_videoconv   = NULL;
	m_videoscale  = NULL;
	m_videorate   = NULL;
	m_videofilter = NULL;
	m_encoder     = NULL;
	m_sink        = NULL;

	mULock->lock();
	deleteAllFiles( CRES_SNAPSHOT_RAMDISK );
	deleteAllFiles( CRES_SNAPSHOT_WEB_RAMDISK );
	mULock->unlock();

	CSIO_LOG(eLogLevel_verbose,  "SnapShot: removed" );

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
