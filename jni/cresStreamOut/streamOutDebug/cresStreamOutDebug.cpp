/**
 * Copyright (C) 2016 to the present, Crestron Electronics, Inc.
 * All rights reserved.
 * No part of this software may be reproduced in any form, machine
 * or natural, without the express written consent of Crestron Electronics.
 * 
 * \file        cresStreamOutDebug.cpp
 *
 * \brief       Implementation of stream out debug
 *
 * \author      John Cheng
 *
 * \date        7/5/2016
 *
 * \note
 */

///////////////////////////////////////////////////////////////////////////////

#include "cresStreamOut.h"
void jni_rtsp_server_debug_printFieldDebugInfo();

/**********************************************************************
* Field debugging command
**********************************************************************/
enum
{
    RTSP_SERV_FIELD_DEBUG_START = 1,                     //1
    RTSP_SERV_FIELD_DEBUG_STOP,

    //this should the last item
    MAX_RTSP_SERV_FIELD_DEBUG_NUM              //
};
const char * const rtsp_serv_fieldDebugNames[MAX_RTSP_SERV_FIELD_DEBUG_NUM - 1] =
{
    "01 START              " ,
    "02 STOP               " ,
};

void jni_rtsp_server_debug(char *cmd_cstring)
{
    char *EndPtr,*CmdPtr;

    if(cmd_cstring)
        CSIO_LOG(eLogLevel_error, "rtsp_server: jni_rtsp_server_debug : %s",cmd_cstring);
    else
        CSIO_LOG(eLogLevel_error, "rtsp_server: jni_rtsp_server_debug : got null pointer");

    CmdPtr = strtok(cmd_cstring,", ");

    if ( CmdPtr == NULL )
    {
        jni_rtsp_server_debug_printFieldDebugInfo();
    }
    else
    {
        //pass the first command : 'STREAMOUT'
        CmdPtr = strtok(NULL, ", ");

        //1. if empty string, then print current settings
        if ( CmdPtr == NULL )
        {
            jni_rtsp_server_debug_printFieldDebugInfo();
        }
        else
        {
            LocalConvertToUpper(CmdPtr);
            //2. the first one has to be string
            if (!strcmp(CmdPtr, "START"))
            {
                CSIO_LOG(eLogLevel_error, "rtsp_server: command of start");
                stream_out_start(0);
                CSIO_LOG(eLogLevel_error, "rtsp_server started");
            }
            else if (!strcmp(CmdPtr, "STOP"))
            {
                CSIO_LOG(eLogLevel_error, "rtsp_server: command of stop");
                stream_out_stop(0);
                CSIO_LOG(eLogLevel_error, "rtsp_server stopped");
            }
            else
            {
               CSIO_LOG(eLogLevel_error, "rtsp_server: Unknown command = [%s]",CmdPtr);
            }
        }
    }

    CSIO_LOG(eLogLevel_error, "rtsp_server: jni_rtsp_server_debug return");
}

void jni_rtsp_server_debug_printFieldDebugInfo()
{
    int i;

    CSIO_LOG(eLogLevel_debug, "Stream out FieldDebugInfo:");
    for (i = 0; i < MAX_RTSP_SERV_FIELD_DEBUG_NUM - 1; i++)
    {
        CSIO_LOG(eLogLevel_debug, "%s", rtsp_serv_fieldDebugNames[i]);
    }
}
