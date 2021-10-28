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

enum Video_Resoultion_Quality
{
    //High resolution  1920x1080
    HIGH_QUALITY_RESOLUTION=(1920*1080),    
    //Medium resolution 1280x720
    MEDIUM_QUALITY_RESOLUTION=(1280*720),    
    //Low quality resolution 640x360
    LOW_QUALITY_RESOLUTION=(640*360),

};

enum
{
    HIGH_QUALITY =0,
    MEDIUM_QUALITY,
    LOW_QUALITY
};

int get_video_caps(char *device_name, VideoCaps *video_caps, char *display_name, int display_name_len, int quality);
int get_video_caps_string(VideoCaps *video_caps, char *caps, int maxlen);

#endif //__V4L2VIDEO_H__

