/*
 * WfdCommon.h
 *
 *  Created on: Dec 11, 2018
 *      Author: buildUser
 */

#ifndef WFDCOMMON_H_
#define WFDCOMMON_H_

#include "csioCommBase.h"
#include "cregstplay.h" //need for MAX_STREAMS

typedef struct
{
    const char * pStr;
    unsigned int num;
} WFD_STRNUMPAIR;

#define WFD_NUM_OF(a) (sizeof (a) / sizeof *(a))

#define MAX_WFD_TCP_CONN     MAX_STREAMS   //up to four windows

typedef struct GST_PIPELINE_CONFIG
{
    unsigned int ts_port;
    unsigned int ssrc;
    unsigned int rtcp_dest_port;
    char * pSrcVersionStr;
} ;

enum
{
    WFD_SINK_MS_LATENCY_CAP_INVALID = -1,

    WFD_SINK_MS_LATENCY_CAP_LOW = 1,

    WFD_SINK_MS_LATENCY_CAP_NORMAL = 2,

    WFD_SINK_MS_LATENCY_CAP_HIGH = 3,
};

extern void* WfdSinkProjInit();
extern void WfdSinkProjDeInit();

extern void WfdSinkProDumpPara();
extern void WfdSinkProj_fdebug(char *cmd_cstring);

extern void WfdSinkProjStart(int id, const char* url, int src_rtsp_port, int ts_port,int is_mice_session);
extern void WfdSinkProjStop(int id);
extern void WfdSinkProjSetLocalIPAddr(int id, const char* intfName);

extern void WfdSinkProjSendGstReady(int id);

#endif /* WFDCOMMON_H_ */
