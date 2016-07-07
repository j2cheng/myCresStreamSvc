#ifndef __CRESSTREAMOUTMANAGER_H__
#define __CRESSTREAMOUTMANAGER_H__

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


extern "C" void stream_out_start(int id);
extern "C" void stream_out_stop(int id);
extern "C" void jni_rtsp_server_debug(char *cmd_cstring);
extern "C" void LocalConvertToUpper(char *str);
#endif __CRESSTREAMOUTMANAGER_H__
