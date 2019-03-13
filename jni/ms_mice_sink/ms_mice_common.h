#ifndef CTL_EXTENSION_MS_MICE_COMMON_H
#define CTL_EXTENSION_MS_MICE_COMMON_H

#include "csioCommBase.h"

extern void msMiceSinkProjInit();
extern void msMiceSinkProjDeInit();
extern void msMiceSinkProjSetPin(int id,int pin);
extern void msMiceSinkProjStopSession(int id, long long session_id);

extern void  msMiceSinkProjDumpPara();
extern void  msMiceSinkProj_fdebug(char *cmd_cstring);

//calling functions in jni.cpp
extern void Wfd_ms_mice_signal_raise (long long session_id, int state, char *device_id, char *device_name, char *device_addr, int rtsp_port);
#endif /* CTL_EXTENSION_MS_MICE_COMMON_H */
