/*
 * WfdCommon.h
 *
 *  Created on: Dec 11, 2018
 *      Author: buildUser
 */

#ifndef WFDCOMMON_H_
#define WFDCOMMON_H_

#include "csioCommBase.h"

typedef struct
{
    const char * pStr;
    unsigned int num;
} WFD_STRNUMPAIR;

#define WFD_NUM_OF(a) (sizeof (a) / sizeof *(a))


typedef struct GST_PIPELINE_CONFIG
{
    unsigned int ts_port;
    unsigned int ssrc;
    unsigned int rtcp_dest_port;
    char * pSrcVersionStr;
} ;

extern void* WfdSinkProjInit();
extern void WfdSinkProjDeInit();

extern void WfdSinkProDumpPara();
extern void WfdSinkProj_fdebug(char *cmd_cstring);

extern void WfdSinkProjStart(int id, const char* url, int src_rtsp_port, int ts_port);
extern void WfdSinkProjStop(int id);

extern void WfdSinkProjSendGstReady(int id);

#endif /* WFDCOMMON_H_ */
