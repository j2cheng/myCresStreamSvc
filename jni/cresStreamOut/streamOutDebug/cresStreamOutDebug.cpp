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
static void jni_rtsp_server_debug_printFieldDebugInfo();
static int  jni_rtsp_server_debug_getDebugIndex(char* name);
extern "C" void       gst_element_print_properties( GstElement * element );
extern CStreamoutManager * StreamoutProjectGetManagerObj();
/**********************************************************************
* Field debugging command
**********************************************************************/
enum
{
    RTSP_SERV_FIELD_DEBUG_START = 0,
    RTSP_SERV_FIELD_DEBUG_STOP,
    RTSP_SERV_FIELD_DEBUG_PORT,

    RTSP_SERV_FIELD_DEBUG_DUMPALL,
    RTSP_SERV_FIELD_DEBUG_PRINT_ELEMENT_PROPERTY,
    RTSP_SERV_FIELD_DEBUG_QUIT_LOOP,

    RTSP_SERV_FIELD_DEBUG_SET_ELEMENT_PROPERTY_INT,
    RTSP_SERV_FIELD_DEBUG_SET_ELEMENT_PROPERTY_INT64,
    RTSP_SERV_FIELD_DEBUG_SET_ELEMENT_PROPERTY_BOOL,
    RTSP_SERV_FIELD_DEBUG_GET_ELEMENT_PROPERTY_BOOL,
    RTSP_SERV_FIELD_DEBUG_GET_ELEMENT_PROPERTY_INT,
    RTSP_SERV_FIELD_DEBUG_GET_ELEMENT_PROPERTY_INT64,
    RTSP_SERV_FIELD_DEBUG_MEDIA_CHILDREN,

    //this should the last item
    MAX_RTSP_SERV_FIELD_DEBUG_NUM              //
};
const char * const rtsp_serv_fieldDebugNames[MAX_RTSP_SERV_FIELD_DEBUG_NUM] =
{
    "START" ,
    "STOP" ,
    "PORT" ,

    "DUMPALL" ,
    "PRINT_ELEMENT_PROPERTY" ,
    "QUIT_LOOP",

    "SET_ELEMENT_PROPERTY_INT" ,
    "SET_ELEMENT_PROPERTY_INT64" ,
    "SET_ELEMENT_PROPERTY_BOOL" ,
    "GET_ELEMENT_PROPERTY_BOOL" ,
    "GET_ELEMENT_PROPERTY_INT" ,
    "GET_ELEMENT_PROPERTY_INT64" ,
    "MEDIA_CHILDREN" ,
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

            int command = jni_rtsp_server_debug_getDebugIndex(CmdPtr);
            CSIO_LOG(eLogLevel_info, "rtsp_server: command is: %d",command);

            //2. the first one has to be string
            switch(command)
            {
                case RTSP_SERV_FIELD_DEBUG_START:
                {
                    CSIO_LOG(eLogLevel_info, "rtsp_server: command of start");
                    Streamout_Start(0);
                    CSIO_LOG(eLogLevel_info, "rtsp_server: started");
                    break;
                }
                case RTSP_SERV_FIELD_DEBUG_STOP:
                {
                    CSIO_LOG(eLogLevel_info, "rtsp_server: command of stop");
                    Streamout_Stop(0);
                    CSIO_LOG(eLogLevel_info, "rtsp_server: stopped");
                    break;
                }
                case RTSP_SERV_FIELD_DEBUG_PORT:
                {
                    CRESSTREAMOUT *cdata = &CresStreamOutDataDB->streamOut[0];

                    CmdPtr = strtok(NULL, ", ");
                    if (CmdPtr == NULL)
                    {
                        CSIO_LOG(eLogLevel_info, "rtsp_server: current port: %s", cdata->rtsp_port);
                    }
                    else
                    {
                        strcpy(cdata->rtsp_port, CmdPtr);
                        CSIO_LOG(eLogLevel_info, "rtsp_server: set new PORT[%s]", cdata->rtsp_port);

                        //send it to the project
                        Streamout_SetPort(cdata->rtsp_port);
                    }
                    break;
                }
                case RTSP_SERV_FIELD_DEBUG_DUMPALL:
                {
                    CmdPtr = strtok(NULL, ", ");
                    if (CmdPtr)
                    {
                        INT32 level = (INT32)strtol(CmdPtr, &EndPtr, 10);
                        StreamoutProjectDumpClassPara(level);
                    }
                    else
                    {
                        StreamoutProjectDumpClassPara(0);
                    }
                    break;
                }
                case RTSP_SERV_FIELD_DEBUG_PRINT_ELEMENT_PROPERTY:
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
                            /* get the element used for providing the streams of the media */
                            CStreamoutManager* pM = StreamoutProjectGetManagerObj();
                            if(pM)
                            {
                                GstRTSPMedia* pMedia = pM->m_pMedia;

                                if(pMedia)
                                {
                                    GstElement * element = gst_rtsp_media_get_element (pMedia);
                                    gchar * n = gst_element_get_name(element);
                                    CSIO_LOG(eLogLevel_info, "rtsp_server: element name[%s] of pMedia[0x%x] for %s",n,pMedia,CmdPtr);

                                    /* get our ele inside bin */
                                    GstElement *ele = gst_bin_get_by_name_recurse_up (GST_BIN (element), CmdPtr);
                                    if(ele)
                                    {
                                        gst_element_print_properties(ele);
                                        gst_object_unref(ele);
                                    }
                                    else
                                    {
                                        CSIO_LOG(eLogLevel_info, "rtsp_server: element name[%s] is null",CmdPtr);
                                    }
                                    gst_object_unref (element);
                                    g_free(n);
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
                    break;
                }
                case RTSP_SERV_FIELD_DEBUG_QUIT_LOOP:
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
                    break;
                }
                case RTSP_SERV_FIELD_DEBUG_SET_ELEMENT_PROPERTY_INT:
                {
                    CmdPtr = strtok(NULL, ", ");
                    if (CmdPtr == NULL)
                    {
                        CSIO_LOG(eLogLevel_info, "Invalid Format, need element's name\r\n");
                    }
                    else
                    {
                        /* get the element used for providing the streams of the media */
                        CStreamoutManager *pM = StreamoutProjectGetManagerObj();
                        if (pM)
                        {
                            GstRTSPMedia *pMedia = pM->m_pMedia;

                            if (pMedia)
                            {

                                GstElement *element = gst_rtsp_media_get_element(pMedia);
                                gchar *n = gst_element_get_name(element);
                                CSIO_LOG(eLogLevel_info, "rtsp_server: element name[%s] of pMedia[0x%x] for %s", n, pMedia, CmdPtr);

                                GstElement *ele = gst_bin_get_by_name_recurse_up(GST_BIN(element), CmdPtr);
                                if (ele)
                                {
                                    CSIO_LOG(eLogLevel_info, "print properties before setting.\r\n");
                                    gst_element_print_properties(ele);

                                    CmdPtr = strtok(NULL, ", ");
                                    if (CmdPtr == NULL)
                                    {
                                        CSIO_LOG(eLogLevel_info, "Invalid Format, need property name\r\n");
                                    }
                                    else
                                    {
                                        char *proName = CmdPtr;

                                        CmdPtr = strtok(NULL, ", ");
                                        if (CmdPtr != NULL)
                                        {
                                            int tmp = (int)strtol(CmdPtr, &EndPtr, 10);

                                            g_object_set(G_OBJECT(ele), proName, tmp, NULL);

                                            CSIO_LOG(eLogLevel_info, "setting properties[%s] setting[%d].\r\n", proName, tmp);

                                            gst_element_print_properties(ele);
                                        }
                                    }
                                    gst_object_unref(ele);
                                } //else

                                gst_object_unref (element);
                                g_free(n);
                            } //else
                        }
                    }
                    break;
                }
                case RTSP_SERV_FIELD_DEBUG_SET_ELEMENT_PROPERTY_INT64:
                {
                    CmdPtr = strtok(NULL, ", ");
                    if (CmdPtr == NULL)
                    {
                        CSIO_LOG(eLogLevel_info, "Invalid Format, need element's name\r\n");
                    }
                    else
                    {
                        /* get the element used for providing the streams of the media */
                        CStreamoutManager *pM = StreamoutProjectGetManagerObj();
                        if (pM)
                        {
                            GstRTSPMedia *pMedia = pM->m_pMedia;

                            if (pMedia)
                            {

                                GstElement *element = gst_rtsp_media_get_element(pMedia);
                                gchar *n = gst_element_get_name(element);
                                CSIO_LOG(eLogLevel_info, "rtsp_server: element name[%s] of pMedia[0x%x] for %s", n, pMedia, CmdPtr);

                                GstElement *ele = gst_bin_get_by_name_recurse_up(GST_BIN(element), CmdPtr);
                                if (ele)
                                {
                                    CSIO_LOG(eLogLevel_info, "print properties before setting.\r\n");
                                    gst_element_print_properties(ele);

                                    CmdPtr = strtok(NULL, ", ");
                                    if (CmdPtr == NULL)
                                    {
                                        CSIO_LOG(eLogLevel_info, "Invalid Format, need property name\r\n");
                                    }
                                    else
                                    {
                                        char *proName = CmdPtr;

                                        CmdPtr = strtok(NULL, ", ");
                                        if (CmdPtr != NULL)
                                        {
                                            int tmp = (int)strtol(CmdPtr, &EndPtr, 10);

                                            g_object_set(G_OBJECT(ele), proName, (gint64)(tmp * 1000000ll), NULL);

                                            CSIO_LOG(eLogLevel_info, "setting properties[%s] setting[%d].\r\n", proName, tmp);

                                            gst_element_print_properties(ele);
                                        }
                                    }
                                    gst_object_unref(ele);
                                } //else

                                gst_object_unref (element);
                                g_free(n);
                            } //else
                        }
                    }
                    break;
                }
                case RTSP_SERV_FIELD_DEBUG_SET_ELEMENT_PROPERTY_BOOL:
                {
                    CmdPtr = strtok(NULL, ", ");
                    if (CmdPtr == NULL)
                    {
                        CSIO_LOG(eLogLevel_info, "Invalid Format, need element's name\r\n");
                    }
                    else
                    {
                        /* get the element used for providing the streams of the media */
                        CStreamoutManager *pM = StreamoutProjectGetManagerObj();
                        if (pM)
                        {
                            GstRTSPMedia *pMedia = pM->m_pMedia;

                            if (pMedia)
                            {

                                GstElement *element = gst_rtsp_media_get_element(pMedia);
                                gchar *n = gst_element_get_name(element);
                                CSIO_LOG(eLogLevel_info, "rtsp_server: element name[%s] of pMedia[0x%x] for %s", n, pMedia, CmdPtr);

                                GstElement *ele = gst_bin_get_by_name_recurse_up(GST_BIN(element), CmdPtr);
                                if (ele)
                                {
                                    CSIO_LOG(eLogLevel_info, "print properties before setting.\r\n");
                                    gst_element_print_properties(ele);

                                    CmdPtr = strtok(NULL, ", ");
                                    if (CmdPtr == NULL)
                                    {
                                        CSIO_LOG(eLogLevel_info, "Invalid Format, need property name\r\n");
                                    }
                                    else
                                    {
                                        char *proName = CmdPtr;

                                        CmdPtr = strtok(NULL, ", ");
                                        if (CmdPtr != NULL)
                                        {
                                            if (!strcmp(CmdPtr, "true"))
                                            {
                                                g_object_set(G_OBJECT(ele), proName, true, NULL);
                                                CSIO_LOG(eLogLevel_info, "setting properties[%s] setting[true].\r\n", proName);
                                            }
                                            else
                                            {
                                                g_object_set(G_OBJECT(ele), proName, false, NULL);
                                                CSIO_LOG(eLogLevel_info, "setting properties[%s] setting[false].\r\n", proName);
                                            }
                                            gst_element_print_properties(ele);
                                        }
                                    }
                                    gst_object_unref(ele);
                                } //else

                                gst_object_unref (element);
                                g_free(n);
                            } //else
                        }
                    }
                    break;
                }
                case RTSP_SERV_FIELD_DEBUG_GET_ELEMENT_PROPERTY_BOOL:
                {
                    CmdPtr = strtok(NULL, ", ");
                    if (CmdPtr == NULL)
                    {
                        CSIO_LOG(eLogLevel_info, "Invalid Format, need element's name\r\n");
                    }
                    else
                    {
                        /* get the element used for providing the streams of the media */
                        CStreamoutManager *pM = StreamoutProjectGetManagerObj();
                        if (pM)
                        {
                            GstRTSPMedia *pMedia = pM->m_pMedia;

                            if (pMedia)
                            {

                                GstElement *element = gst_rtsp_media_get_element(pMedia);
                                gchar *n = gst_element_get_name(element);
                                CSIO_LOG(eLogLevel_info, "rtsp_server: element name[%s] of pMedia[0x%x] for %s", n, pMedia, CmdPtr);

                                GstElement *ele = gst_bin_get_by_name_recurse_up(GST_BIN(element), CmdPtr);
                                if (ele)
                                {
                                    CSIO_LOG(eLogLevel_info, "print all properties of the element.\r\n");
                                    gst_element_print_properties(ele);

                                    CmdPtr = strtok(NULL, ", ");
                                    if (CmdPtr == NULL)
                                    {
                                        CSIO_LOG(eLogLevel_info, "Invalid Format, need property name\r\n");
                                    }
                                    else
                                    {                                
                                        bool  tmp = 0;

                                        g_object_get(G_OBJECT(ele), CmdPtr, &tmp, NULL);
                                        CSIO_LOG(eLogLevel_info, "properties[%s] setting[%d].\r\n", CmdPtr,tmp);                                        
                                    }      
                                                                        
                                    
                                    gst_object_unref(ele);
                                } //else

                                gst_object_unref (element);
                                g_free(n);
                            } //else
                        }
                    }
                    break;
                }
                case RTSP_SERV_FIELD_DEBUG_GET_ELEMENT_PROPERTY_INT:
                {
                    CmdPtr = strtok(NULL, ", ");
                    if (CmdPtr == NULL)
                    {
                        CSIO_LOG(eLogLevel_info, "Invalid Format, need element's name\r\n");
                    }
                    else
                    {
                        /* get the element used for providing the streams of the media */
                        CStreamoutManager *pM = StreamoutProjectGetManagerObj();
                        if (pM)
                        {
                            GstRTSPMedia *pMedia = pM->m_pMedia;

                            if (pMedia)
                            {

                                GstElement *element = gst_rtsp_media_get_element(pMedia);
                                gchar *n = gst_element_get_name(element);
                                CSIO_LOG(eLogLevel_info, "rtsp_server: element name[%s] of pMedia[0x%x] for %s", n, pMedia, CmdPtr);

                                GstElement *ele = gst_bin_get_by_name_recurse_up(GST_BIN(element), CmdPtr);
                                if (ele)
                                {
                                    CSIO_LOG(eLogLevel_info, "print all properties of the element.\r\n");
                                    gst_element_print_properties(ele);

                                    CmdPtr = strtok(NULL, ", ");
                                    if (CmdPtr == NULL)
                                    {
                                        CSIO_LOG(eLogLevel_info, "Invalid Format, need property name\r\n");
                                    }
                                    else
                                    {                                
                                        int  tmp = 0;

                                        g_object_get(G_OBJECT(ele), CmdPtr, &tmp, NULL);
                                        CSIO_LOG(eLogLevel_info, "properties[%s] setting[%d].\r\n", CmdPtr,tmp);                                        
                                    }                           
                                    
                                    gst_object_unref(ele);
                                } //else

                                gst_object_unref (element);
                                g_free(n);
                            } //else
                        }
                    }
                    break;
                }
                case RTSP_SERV_FIELD_DEBUG_GET_ELEMENT_PROPERTY_INT64:
                {
                    CmdPtr = strtok(NULL, ", ");
                    if (CmdPtr == NULL)
                    {
                        CSIO_LOG(eLogLevel_info, "Invalid Format, need element's name\r\n");
                    }
                    else
                    {
                        /* get the element used for providing the streams of the media */
                        CStreamoutManager *pM = StreamoutProjectGetManagerObj();
                        if (pM)
                        {
                            GstRTSPMedia *pMedia = pM->m_pMedia;

                            if (pMedia)
                            {

                                GstElement *element = gst_rtsp_media_get_element(pMedia);
                                gchar *n = gst_element_get_name(element);
                                CSIO_LOG(eLogLevel_info, "rtsp_server: element name[%s] of pMedia[0x%x] for %s", n, pMedia, CmdPtr);

                                GstElement *ele = gst_bin_get_by_name_recurse_up(GST_BIN(element), CmdPtr);
                                if (ele)
                                {
                                    CSIO_LOG(eLogLevel_info, "print all properties of the element.\r\n");
                                    gst_element_print_properties(ele);

                                    CmdPtr = strtok(NULL, ", ");
                                    if (CmdPtr == NULL)
                                    {
                                        CSIO_LOG(eLogLevel_info, "Invalid Format, need property name\r\n");
                                    }
                                    else
                                    {                                
                                        gint64  tmp = 0;

                                        g_object_get(G_OBJECT(ele), CmdPtr, &tmp, NULL);
                                        CSIO_LOG(eLogLevel_info, "properties[%s] setting[%lld].\r\n", CmdPtr,tmp);                                        
                                    }      
                                                                        
                                    
                                    gst_object_unref(ele);
                                } //else

                                gst_object_unref (element);
                                g_free(n);
                            } //else
                        }
                    }
                    break;
                }
                case RTSP_SERV_FIELD_DEBUG_MEDIA_CHILDREN:
                {
                    CStreamoutManager* pM = StreamoutProjectGetManagerObj();
                    if(pM)
                    {
                        GstRTSPMedia *pMedia = pM->m_pMedia;

                        if (pMedia)
                        {

                            GstElement *bin = gst_rtsp_media_get_element(pMedia);
                            gchar *nBin = gst_element_get_name(bin);
                            CSIO_LOG(eLogLevel_info, "rtsp_server: bin name[%s] of pMedia[0x%x] for %s", nBin, pMedia, CmdPtr);                                               

                            CSIO_LOG(eLogLevel_info, "rtsp_server bin[0x%x],isBin[%d]", bin,GST_IS_BIN (bin));

                            GstIterator *it = gst_bin_iterate_elements(GST_BIN(bin));
                            GValue item = G_VALUE_INIT;
                            GstElement *decoder = NULL;
                            while (gst_iterator_next(it, &item) == GST_ITERATOR_OK)
                            {
                                GstElement *elem = (GstElement *)g_value_get_object(&item);
                                gchar *elemName = gst_element_get_name(elem);
                                CSIO_LOG(eLogLevel_info, "Found element %s", elemName);

                                g_free(elemName);
                                g_value_reset(&item);
                            }
                            g_value_unset(&item);
                            gst_iterator_free(it);

                            
                            //Note: create graph of the media bin, and use this command(in VM) to create the image:
                            //      dot -Tsvg -o pipelineGraph.svg pipelineGraph.dot
                            {
                                if (GST_IS_BIN (bin))
                                {
                                    char filePath[1024];
                                    GstDebugGraphDetails graph_option = GST_DEBUG_GRAPH_SHOW_ALL;
                                    bool verbose = true;
                                    CSIO_LOG(eLogLevel_info, "command graph[pipelineGraph] verbose[%d]", "pipelineGraph", verbose);

                                    snprintf(filePath, 1024, "/dev/shm/crestron/CresStreamSvc/pipelineGraph.dot");

                                    if (verbose)
                                        graph_option = GST_DEBUG_GRAPH_SHOW_VERBOSE;

                                    gchar *dotData = gst_debug_bin_to_dot_data(GST_BIN(bin), graph_option);

                                    FILE *file = fopen(filePath, "w");
                                    if (file != NULL)
                                    {
                                        fprintf(file, "%s", dotData);
                                        fclose(file);
                                    }
                                    else
                                    {
                                        CSIO_LOG(eLogLevel_info, "Error writing graph file - %s: %s", filePath, strerror(errno));
                                    }
                                }
                            }

                            gst_object_unref (bin);
                            g_free(nBin);

                        }
                    }
                    else
                    {
                        CSIO_LOG(eLogLevel_info, "rtsp_server: CStreamoutManager object is null");
                    }
                    break;
                }
                default:
                {
                    CSIO_LOG(eLogLevel_error, "rtsp_server: Unknown command = [%s]",CmdPtr);
                    break;
                }
            }//end of switch
        }
    }

    CSIO_LOG(eLogLevel_error, "rtsp_server: jni_rtsp_server_debug return");
}

void jni_rtsp_server_debug_printFieldDebugInfo()
{
    int i;

    CSIO_LOG(eLogLevel_info, "Stream out FieldDebugInfo:");
    for (i = 0; i < MAX_RTSP_SERV_FIELD_DEBUG_NUM; i++)
    {
        CSIO_LOG(eLogLevel_info, "  %s", rtsp_serv_fieldDebugNames[i]);
    }
}


int  jni_rtsp_server_debug_getDebugIndex(char* name)
{
    int ret = -1;

    if(name == NULL)
        return ret;

    for(int i = 0; i < MAX_RTSP_SERV_FIELD_DEBUG_NUM; i++)
    {
        if(strncmp(rtsp_serv_fieldDebugNames[i],name,strlen(name)) == 0)
            return i;
        else
            continue;
    }

    return ret;
}