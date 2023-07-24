#ifndef __CRESSTREAMOUTCOMMON_DEF_H__
#define __CRESSTREAMOUTCOMMON_DEF_H__

///////////////////////////////////////////////////////////////////////////////


#define DEFAULT_RTSP_PORT "8554"
#define DEFAULT_RES_X     "1280"
#define DEFAULT_RES_Y     "720"
#define DEFAULT_FRAME_RATE "30"
#define DEFAULT_BIT_RATE        "4194304"    // 4Mbps = 4,194,304 bps
#define DEFAULT_IFRAME_INTERVAL "15"          // 1 second
#define DEFAULT_QUALITY     "0"    // Default zero is High 
#define DEFAULT_VIDEO_CODEC "H264"
#define DEFAULT_HIGH_QUALITY 0
#define DEFAULT_HDMI_IN_RES_X     "1920"
#define DEFAULT_HDMI_IN_RES_y     "1080"
#define DEFAULT_MULTICAST_ENABLE 0
#define DEFAULT_MULTICAST_ADDRESS "235.0.0.0"
#define DEFAULT_STREAM_NAME "camera"
#define DEFAULT_WIRELESSCONFERENCING_STREAM_NAME "wc"
#define DEFAULT_SNAPSHOT_NAME "snapshot"
#define DEFAULT_VIDEO_CAPTURE_DEVICE "/dev/video5"
#define DEFAULT_AUDIO_CAPTURE_DEVICE "/dev/snd/pcmC5D0c"

#define MAX_STR_LEN    126
#endif //__CRESSTREAMOUTCOMMON_DEF_H__
