#ifndef __CRES_RTSP_MEDIA_H__
#define __CRES_RTSP_MEDIA_H__

#include <gst/gst.h>
#include <gst/rtsp-server/rtsp-server.h>

#define CRES_TYPE_RTSP_MEDIA   (cres_rtsp_media_get_type ())

#ifdef __cplusplus
extern "C"
{
#endif

typedef struct CresRTSPMediaClass CresRTSPMediaClass;
typedef struct CresRTSPMedia CresRTSPMedia;

struct CresRTSPMediaClass
{
  GstRTSPMediaClass parent;
};

struct CresRTSPMedia
{
  GstRTSPMedia parent;
  GMainLoop *  m_loop;
};

GType cres_rtsp_media_get_type (void);

#ifdef __cplusplus
}
#endif

#endif //__CRES_RTSP_MEDIA_H__
