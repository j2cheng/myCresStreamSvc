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

typedef enum _eStreamoutMode
{
//mode is set come from jni.c upon initialization of project
    STREAMOUT_MODE_CAMERA = 0,
    STREAMOUT_MODE_WIRELESSCONFERENCING = 1,
    STREAMOUT_MODE_MAX
} eStreamoutMode;

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
void StreamoutProjectInit(eStreamoutMode streamoutMode);
void StreamoutProjectDeInit();
void StreamoutProjectDumpClassPara(int level);
void Streamout_Start(int streamID);
void Streamout_Stop(int streamID);
void Streamout_SetDebugLevel(int level);
void Streamout_SetPort(char* port);
void Streamout_SetFrameRate(char* r);
void Streamout_SetRes_x(char* x);
void Streamout_SetRes_y(char* y);
void Streamout_SetBitrate(char* r);
void Streamout_SetIFrameInterval(char* r);
void Streamout_EnableSecurity(int enable);
void Streamout_EnableMulticast(int enable);
void Streamout_SetMulticastAddress(char* address);
void Streamout_SetStreamName(char* name);
void Streamout_SetSnapshotName(char* name);
void Streamout_StartPreview(int streamID, void* native_window);
void Streamout_PausePreview(int streamID);
void Streamout_StopPreview(int streamID);
int  Streamout_WaitForPreviewAvailable(int streamID,int timeout_sec);
int  Streamout_WaitForPreviewClosed(int streamID,int timeout_sec);

#ifdef __cplusplus
}
#endif
#endif //__CRESSTREAMOUT_H__
