#ifndef __V4L2VIDEO_H__
#define __V4L2VIDEO_H__

//////////////////////////////////////////////////////////////////////////////

#include <gst/gst.h>
#include <gio/gio.h>

typedef struct VideoCapStruct {
    char format[5];
    int w;
    int h;
    int frame_rate_num;
    int frame_rate_den;
} VideoCaps;

int get_video_caps(char *device_name, VideoCaps *video_caps, char *display_name, int display_name_len);
int get_video_caps_string(VideoCaps *video_caps, char *caps, int maxlen);

#endif //__V4L2VIDEO_H__

