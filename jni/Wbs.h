/**
 * Copyright (C) 2017 to the present, Crestron Electronics, Inc.
 * All rights reserved.
 * No part of this software may be reproduced in any form, machine
 * or natural, without the express written consent of Crestron Electronics.
 *
 * \file        Wbs.cpp
 *
 * \brief       WBS Live Stream Decoder
 *
 * \author      Rajesh Hingorani
 *
 * \date        11/29/2017
 *
 * \note        Modifications to code provided by Light Blue Optics Ltd.
 *
 * This module does the actual decoding of the WBS Live Stream
 *
 *
 */

///////////////////////////////////////////////////////////////////////////////


#ifndef __WBS_H__
#define __WBS_H__

///////////////////////////////////////////////////////////////////////////////

#include <cregstplay.h>
#include <jni.h>
#include <pthread.h>
#include <time.h>
#include <errno.h>
#include <stdbool.h>
#include "csioCommonShare.h"
// Android headers
#include "hardware/gralloc.h"           // for GRALLOC_USAGE_PROTECTED
#include "android/native_window.h"      // for ANativeWindow_ functions

#define RESTART_MAX_BACKOFF_SECS 10
#define FORCE_STOP_AFTER_FAILED_RESTARTS 5

typedef struct _ConditionObj
{
	pthread_cond_t cond;
	pthread_mutex_t mutex;
} ConditionObj;

typedef struct Wbs_t {
    unsigned int streamId;
	ANativeWindow *native_window; /* The Android native window where video will be rendered */
	void* surface;
	int format;

	char url[256];
	bool urlIsValid;
	bool secure;
	char hostname[256];
	char resource[256];
	int portnumber;
	int backoffInSecs;
	int width;
	int height;
	uint32_t frameCount;
	uint64_t totalFrameCount;

	pthread_t wbsTid;
	bool isStarted;
	bool isPaused;
	bool requestStop;
	bool logRejectionEventAsError;
	int failedRestartAttempts;  // used to do forced stop

    ConditionObj waiter;        // used for timed wait
} Wbs_t;

#ifdef __cplusplus
extern "C"
{
#endif

jobject wbs_get_app();
void wbs_set_app(jobject app);
void wbs_setUrl(const char *url, int sessId);
void wbs_init();
int wbs_start(int sessId);
void wbs_stop(int sessId);
void wbs_pause(int sessId);
void wbs_unpause(int sessId);
Wbs_t *wbs_get_stream_data(int sessId);
void wbs_update_window(int sessId, int width, int height);
int wbs_SendVideoPlayingStatusMessage(unsigned int source, eStreamState state);
int wbs_forceStop(unsigned int source);
int wbs_getLogLevel();
void wbs_setLogLevel(int level);
void waitMsec(Wbs_t *pWbs, int msec);
void signalWaitExit(Wbs_t *pWbs);

#ifdef __cplusplus
}
#endif
#endif //__WBS_H__
