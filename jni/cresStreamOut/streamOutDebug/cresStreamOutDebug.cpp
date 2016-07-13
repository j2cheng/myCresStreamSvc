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
#include <stdlib.h>
#include "../cresStreamOut.h"
#include "../cresStreamOutProject.h"
void jni_rtsp_server_debug_printFieldDebugInfo();

extern "C" void       gst_element_print_properties( GstElement * element );
extern CStreamoutManager * StreamoutProjectGetManagerObj();
/**********************************************************************
* Field debugging command
**********************************************************************/
enum
{
    RTSP_SERV_FIELD_DEBUG_START = 1,                     //1
    RTSP_SERV_FIELD_DEBUG_STOP,
    RTSP_SERV_FIELD_DEBUG_PORT,

    RTSP_SERV_FIELD_DEBUG_DUMPALL,
    RTSP_SERV_FIELD_DEBUG_PRINT_ELEMENT_PROPERTY,
    //this should the last item
    MAX_RTSP_SERV_FIELD_DEBUG_NUM              //
};
const char * const rtsp_serv_fieldDebugNames[MAX_RTSP_SERV_FIELD_DEBUG_NUM - 1] =
{
    "01 START              " ,
    "02 STOP               " ,
    "03 PORT               " ,
    "04 DUMPALL                " ,
    "05 PRINT_ELEMENT_PROPERTY " ,

};

void jni_rtsp_server_debug(char *cmd_cstring)
{
    char *EndPtr,*CmdPtr;

    if(cmd_cstring)
        CSIO_LOG(eLogLevel_info, "rtsp_server: jni_rtsp_server_debug : %s",cmd_cstring);
    else
        CSIO_LOG(eLogLevel_info, "rtsp_server: jni_rtsp_server_debug : got null pointer");

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
                CSIO_LOG(eLogLevel_info, "rtsp_server: command of start");
                Streamout_Start(0);
                CSIO_LOG(eLogLevel_info, "rtsp_server: started");
            }
            else if (!strcmp(CmdPtr, "STOP"))
            {
                CSIO_LOG(eLogLevel_info, "rtsp_server: command of stop");
                Streamout_Stop(0);
                CSIO_LOG(eLogLevel_info, "rtsp_server: stopped");
            }
            else if (!strcmp(CmdPtr, "PORT"))
            {
                CRESSTREAMOUT *cdata = &CresStreamOutDataDB->streamOut[0];

                CmdPtr = strtok(NULL, ", ");
                if (CmdPtr == NULL)
                {
                    CSIO_LOG(eLogLevel_info, "rtsp_server: current port: %s",cdata->rtsp_port);
                }
                else
                {
                   strcpy(cdata->rtsp_port, CmdPtr);
                   CSIO_LOG(eLogLevel_info, "rtsp_server: set new PORT[%s]",cdata->rtsp_port);

                   //send it to the project
                   Streamout_SetPort(cdata->rtsp_port);
                }
            }
            else if(strcasestr(CmdPtr, "DUMPALL"))
            {
                CmdPtr = strtok(NULL, ", ");
                if(CmdPtr)
                {
                    INT32 level = (INT32)strtol(CmdPtr, &EndPtr, 10);
                    StreamoutProjectDumpClassPara(level);
                }
                else
                {
                    StreamoutProjectDumpClassPara(0);
                }
            }
            else if (!strcmp(CmdPtr, "PRINT_ELEMENT_PROPERTY"))
            {
                CmdPtr = strtok(NULL, ", ");
                if (CmdPtr == NULL)
                {
                    CSIO_LOG(eLogLevel_info, "Invalid Format, need element's name\r\n");
                }
                else
                {
                    if (!strcmp(CmdPtr, "allvideo"))
                    {
                        CSIO_LOG(eLogLevel_info, "rtsp_server: allvideo");
                    }
                    else if (!strcmp(CmdPtr, "allaudio"))
                    {
                        CSIO_LOG(eLogLevel_info, "rtsp_server: allaudio");
                    }
                    else if (!strcmp(CmdPtr, "allav"))
                    {
                        CSIO_LOG(eLogLevel_info, "rtsp_server: allav");
                    }
                    else
                    {
                        //GstElement *ele = gst_bin_get_by_name(GST_BIN(data->pipeline), CmdPtr);
                        //if(ele)
                        //    gst_element_print_properties(ele);
                        GstElement *element;

                        /* get the element used for providing the streams of the media */
                        CStreamoutManager* pM = StreamoutProjectGetManagerObj();
                        if(pM)
                        {
                            GstRTSPMedia* pMedia = pM->m_pMediaPipeline;

                            if(pMedia)
                            {
                                element = gst_rtsp_media_get_element (pMedia);
                                gchar * n = gst_element_get_name(element);
                                CSIO_LOG(eLogLevel_info, "rtsp_server: element name[%s] of pMedia[0x%x]",n,pMedia);

                                /* get our ahcsrc, we named it 'mysrc' with the name property */
                                GstElement *ele = gst_bin_get_by_name_recurse_up (GST_BIN (element), "cressrc");
                                if(ele)
                                {
                                    gst_element_print_properties(ele);
                                }
                                else
                                {
                                    CSIO_LOG(eLogLevel_info, "rtsp_server: element name[cressrc] is null");
                                }
                            }
                            else
                            {
                                CSIO_LOG(eLogLevel_info, "rtsp_server: pMedia is null");
                            }
                        }
                        else
                        {
                            CSIO_LOG(eLogLevel_info, "rtsp_server: CStreamoutManager object is null");
                        }
                    }
                }
            }
            else if(strcasestr(CmdPtr, "QUIT_LOOP"))//this is hidden command
            {
                CStreamoutManager* pM = StreamoutProjectGetManagerObj();
                if(pM)
                {
                    pM->exitThread();
                    CSIO_LOG(eLogLevel_info, "rtsp_server: quit main loop now command has set.");
                }
                else
                {
                    CSIO_LOG(eLogLevel_info, "rtsp_server: CStreamoutManager object is null");
                }
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

    CSIO_LOG(eLogLevel_info, "Stream out FieldDebugInfo:");
    for (i = 0; i < MAX_RTSP_SERV_FIELD_DEBUG_NUM - 1; i++)
    {
        CSIO_LOG(eLogLevel_info, "%s", rtsp_serv_fieldDebugNames[i]);
    }
}
