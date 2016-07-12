#ifndef __CRESSTREAMOUT_H__
#define __CRESSTREAMOUT_H__

///////////////////////////////////////////////////////////////////////////////

#include <jni.h>
#include <pthread.h>
#include <time.h>
#include <errno.h>
#include <gst/gst.h>
#include <gst/video/video.h>
#include <gst/rtsp-server/rtsp-server.h>
#include <gst/rtsp-server/rtsp-media-factory.h>
#include <gst/rtsp/gstrtsptransport.h>
#include <stdbool.h>
#include "csioCommonShare.h"
#include "cregstplay.h"
#include "cresStreamOut_Common_Def.h"

#ifdef __cplusplus
extern "C"
{
#endif

//called by jni.c
void jni_rtsp_server_debug(char *cmd_cstring);

//functions defined in jni.c
void LocalConvertToUpper(char *str);

//parameters defined in jni.c
extern CustomStreamOutData *CresStreamOutDataDB ;

//all project functions
void StreamoutProjectInit();
void StreamoutProjectDeInit();
void StreamoutProjectDumpClassPara(int level);
void Streamout_Start(int streamID);
void Streamout_Stop(int streamID);
void Streamout_SetDebugLevel(int level);
void Streamout_SetPort(char* port);

#ifdef __cplusplus
}
#endif
#endif __CRESSTREAMOUT_H__
