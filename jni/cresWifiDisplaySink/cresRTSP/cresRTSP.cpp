#define __STDC_FORMAT_MACROS
#include <errno.h>
#include <fcntl.h>
#include <inttypes.h>
#include <stdarg.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <ctype.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <sys/types.h>
// *** #include <systemd/sd-event.h>
#include <time.h>
#include <unistd.h>

#include "cresRTSP.h"

/* 5s default timeout for messages */
#define RTSP_DEFAULT_TIMEOUT (5ULL * 1000ULL * 1000ULL)

/* CSeq numbers have separate namespaces for locally and remotely generated
 * messages. We use a single lookup-table, so mark all remotely generated
 * cookies as such to avoid conflicts with local cookies. */
#define RTSP_FLAG_REMOTE_COOKIE 0x8000000000000000ULL


#define rtsp_message_from_htable(_p) \
	shl_htable_entry((_p), struct rtsp_message, cookie)

#define RTSP_FOREACH_WAITING(_i, _bus) \
	SHL_HTABLE_FOREACH_MACRO(_i, &(_bus)->waiting, rtsp_message_from_htable)

#define RTSP_FIRST_WAITING(_bus) \
	SHL_HTABLE_FIRST_MACRO(&(_bus)->waiting, rtsp_message_from_htable)


// Supported CEA Resolution/Refresh Rates
WFDVIDEOSUBELEMENCENTRY ceaResRefEnc[] =
{
   { 0,   640,  480, 60, "640x480p60"   },
   { 1,   720,  480, 60, "720x480p60"   },
   { 2,   720,  480, 60, "720x480i60"   },
   { 3,   720,  576, 50, "720x576p50"   },
   { 4,   720,  576, 50, "720x576i50"   },
   { 5,  1280,  720, 30, "1280x720p30"  },
   { 6,  1280,  720, 60, "1280x720p60"  },
   { 7,  1920, 1080, 30, "1920x1080p30" },
   { 8,  1920, 1080, 60, "1920x1080p60" },
   { 9,  1920, 1080, 60, "1920x1080i60" },
   { 10, 1280,  720, 25, "1280x720p25"  },
   { 11, 1280,  720, 50, "1280x720p50"  },
   { 12, 1920, 1080, 25, "1920x1080p25" },
   { 13, 1920, 1080, 50, "1920x1080p50" },
   { 14, 1920, 1080, 50, "1920x1080i50" },
   { 15, 1280,  720, 24, "1280x720p24"  },
   { 16, 1920, 1080, 24, "1920x1080p24" },
   { -1,    0,    0,  0, NULL           }
};

// Supported VESA Resolution/Refresh Rates
WFDVIDEOSUBELEMENCENTRY vesaResRefEnc[] =
{
   { 0,   800,  600, 30, "800x600p30"   },
   { 1,   800,  600, 60, "800x600p60"   },
   { 2,  1024,  768, 30, "1024x768p30"  },
   { 3,  1024,  768, 60, "1024x768p60"  },
   { 4,  1152,  854, 30, "1152x864p30"  },
   { 5,  1152,  854, 60, "1152x864p60"  },
   { 6,  1280,  768, 30, "1280x768p30"  },
   { 7,  1280,  768, 60, "1280x768p60"  },
   { 8,  1280,  800, 30, "1280x800p30"  },
   { 9,  1280,  800, 60, "1280x800p60"  },
   { 10, 1360,  768, 30, "1360x768p30"  },
   { 11, 1360,  768, 60, "1360x768p60"  },
   { 12, 1366,  768, 30, "1366x768p30"  },
   { 13, 1366,  768, 60, "1366x768p60"  },
   { 14, 1280, 1024, 30, "1280x1024p30" },
   { 15, 1280, 1024, 60, "1280x1024p60" },
   { 16, 1440, 1050, 30, "1400x1050p30" },
   { 17, 1440, 1050, 60, "1400x1050p60" },
   { 18, 1440,  900, 30, "1440x900p30"  },
   { 19, 1440,  900, 60, "1440x900p60"  },
   { 20, 1600,  900, 30, "1600x900p30"  },
   { 21, 1600,  900, 60, "1600x900p60"  },
   { 22, 1600, 1200, 30, "1600x1200p30" },
   { 23, 1600, 1200, 60, "1600x1200p60" },
   { 24, 1680, 1024, 30, "1680x1024p30" },
   { 25, 1680, 1024, 60, "1680x1024p60" },
   { 26, 1680, 1050, 30, "1680x1050p30" },
   { 27, 1680, 1050, 60, "1680x1050p60" },
   { 28, 1920, 1200, 30, "1920x1200p30" },
   { -1,    0,    0,  0, NULL           }
};

// Supported HH Resolution/Refresh Rates
WFDVIDEOSUBELEMENCENTRY hhResRefEnc[] =
{
   { 0,   800,  480, 30, "800x480p30"   },
   { 1,   800,  480, 60, "800x480p60"   },
   { 2,   854,  480, 30, "854x480p30"   },
   { 3,   854,  480, 60, "854x480p60"   },
   { 4,   864,  480, 30, "864x480p30"   },
   { 5,   864,  480, 60, "864x480p60"   },
   { 6,   640,  360, 30, "640x360p30"   },
   { 7,   640,  360, 60, "640x360p60"   },
   { 8,   960,  540, 30, "960x540p30"   },
   { 9,   960,  540, 60, "960x540p60"   },
   { 10,  848,  480, 30, "848x480p30"   },
   { 11,  848,  480, 60, "848x480p60"   },
   { -1,    0,    0,  0, NULL           } 
};

// Supported LPCM modes
WFDAUDIOSUBELEMENCENTRY lpcmModeEnc[] =
{
   { 0,    44.1, 16,  2, "LPCMx44_1x2"  },
   { 1,    48.0, 16,  2, "LPCMx48x2"    },
   { -1,    0.0,  0,  0, NULL           }
};

// Supported AAC modes
WFDAUDIOSUBELEMENCENTRY aacModeEnc[] =
{
   { 0,    48.0, 16,  2, "AACx48x2"     },
   { 1,    48.0, 16,  4, "AACx48x4"     },
   { 2,    48.0, 16,  6, "AACx48x6"     },
   { 3,    48.0, 16,  8, "AACx48x8"     },
   { -1,    0.0,  0,  0, NULL           }
};

// Supported AC3 modes
WFDAUDIOSUBELEMENCENTRY ac3ModeEnc[] =
{
   { 0,    48.0, 16,  2, "AC3x48x2"     },
   { 1,    48.0, 16,  4, "AC3x48x4"     },
   { 2,    48.0, 16,  6, "AC3x48x6"     },
   { -1,    0.0,  0,  0, NULL           }
};


int rtsp_encodeVideoFormat(char * outBuff, int outBuffSize, char * encodedValStr);
int rtsp_encodeAudioFormat(char * outBuff, int outBuffSize, char * encodedValStr);

static void rtsp_free_match(struct rtsp_match *match);
static void rtsp_drop_message(struct rtsp_message *m);
static int rtsp_incoming_message(struct rtsp_message *m);



// ***

static int rtsp_parse_data(struct rtsp *bus,const char *buf,size_t len);

#define check_and_response_option(option, response) \
   if (check_rtsp_option(orgMsg, option)) { \
      char option_response[512]; \
      sprintf(option_response, "%s: %s", option, response); \
      retv = rtsp_message_append(rep, "{&}", option_response); \
      if (retv < 0) {\
         printf("ERROR: rtsp_message_append() failed in check_and_response_option() with error code %d\n",retv); \
         return(retv); \
      } \
   }

bool check_rtsp_option(struct rtsp_message *m, char *option);

int cresRTSP_internalCallback(void * session,unsigned int messageType,
      struct rtsp_message * parsedMessagePtr,char * request_method,char * request_uri,
      char * reply_phrase,unsigned int reply_code);
int cresRTSP_internalComposeCallback(void * session,unsigned int messageType,
      char * composedMessagePtr,char * request_method,char * request_uri,char * reply_phrase,
      unsigned int reply_code);

char * loc_strchrnul(char * s, int c);
char * loc_stpcpy(char * dest, char * src);

bool processCommandLine(int argc, char * argv[]);
int readDataFromInput(char * buff, int buffSize);
int getRequestInfoFromInputData(char * buff, int buffSize, char * reqMethodBuff,
      int reqMethodBuffSize, char * reqArg0Buff, int reqArg0BuffSize,
      char * reqArg1Buff,int reqArg1BuffSize);
int splitStrOnWS(char * strStart, char ** tokenArr, int tokenArrSize);
char * findCharRun(char * strStart, char * strEnd, int invertCheck);
int spaceCheck(char chr, int invertCheck);
int getLineFromFile(char * buff, int size, int fileHd, int expectCRLF);
int printParseResults(int messageType, RTSPPARSINGRESULTS * parseResults);
int testCallback(RTSPPARSINGRESULTS * parsResPtr, void * appArgument);
int testComposeCallback(RTSPCOMPOSINGRESULTS * composingResPtr, void * appArgument);
void init_code_descriptions();

static const char *code_descriptions[RTSP_CODE_CNT] = {NULL};

#ifdef BUILD_TEST_APP

char           glCmdChar = '\0';
char *         g_cmdLineHelpStr =
   {
   "\n"
   "Usage:                                                                \n"
   "   cresrtsp -c <command>                                              \n"
   "      command:                                                        \n"
   "         p   - parse input as RTSP request/response message           \n"
   "         c   - compose RTSP request message using info from input     \n"
   "                                                                      \n"
   "   or                                                                 \n"
   "                                                                      \n"
   "   cnsTest -h                                                         \n"
   "      - print this help info                                          \n"
   "\n"
   };

int main(int argc, char **argv)
{
   bool        bretv;
   int         retv,msgLen;
   struct rtsp * rtspPtr = NULL;
   RTSPSYSTEMINFO sysInfo;
   char        requestMethod[256];
   char        requestArg0[256];
   char        requestArg1[256];
   char        locBuff[4096];

   // sysInfo.rtpPort = 4570;
   // sysInfo.preferredVidResRefStr = "848x480p60";
   // sysInfo.preferredAudioCodecStr = "AC3x48x6";
   sysInfo.rtpPort = -1;
   sysInfo.preferredVidResRefStr = NULL;
   sysInfo.preferredAudioCodecStr = NULL;

   bretv = processCommandLine(argc,argv);
   if(!bretv)
      return(-1);

   printf("\nINFO: Will execute command <%c>\n",glCmdChar);

   rtspPtr = (struct rtsp *)initRTSPParser(&sysInfo);
   if(!rtspPtr)
   {
      printf("ERROR: initRTSPParser() failed\n");
	   return(-1);
   }

   switch(glCmdChar)
   {
      case 'p':
         msgLen = readDataFromInput(locBuff,sizeof(locBuff));
         if(msgLen < 0)
         {
            printf("ERROR: Failed to read input data from stdin\n");
	         return(-1);
         }

         printf("INFO: Read from stdin a message of %d characters:\n\n",msgLen);
         printf("%s\n",locBuff);
         printf("\n");
         
         retv = parseRTSPMessage((void *)rtspPtr,locBuff,testCallback,(void *)12345);
         if(retv != 0)
         {
            printf("ERROR: parseRTSPMessage() returned %d\n",retv);
            return(-1);
         }

         break;
      case 'c':
         requestMethod[0] = '\0';
         retv = getRequestInfoFromInputData(locBuff,sizeof(locBuff),requestMethod,
            sizeof(requestMethod),requestArg0,sizeof(requestArg0),requestArg1,
            sizeof(requestArg1));
         if(!strcmp(requestMethod,"OPTIONS"))
         {
            // no arguments
            if(retv < 1)
            {
               printf("ERROR: method: %s, getRequestInfoFromInputData() returned %d\n",
                  requestMethod,retv);
               return(-1);
            }
         }
         else if(!strcmp(requestMethod,"SETUP"))
         {
            if(retv < 2)
            {
               printf("ERROR: method: %s, getRequestInfoFromInputData() returned %d\n",
                  requestMethod,retv);
               return(-1);
            }
         }
         else if(!strcmp(requestMethod,"PLAY"))
         {
            if(retv < 1)
            {
               printf("ERROR: method: %s, getRequestInfoFromInputData() returned %d\n",
                  requestMethod,retv);
               return(-1);
            }
            if(retv >= 2)
            {
               // store argument as 'session' value
               strncpy(rtspPtr->sessionID,requestArg0,sizeof(rtspPtr->sessionID) - 1);
               rtspPtr->sessionID[sizeof(rtspPtr->sessionID) - 1] = '\0';
            }
         }
         else
         {
            printf("ERROR: unexpected requestMethod in main(): %s\n",requestMethod);
            return(-1);
         }

         retv = composeRTSPRequest((void *)rtspPtr,requestMethod,testComposeCallback,(void *)12345);
         if(retv != 0)
         {
            printf("ERROR: composeRTSPRequest() returned %d\n",retv);
            return(-1);
         }

         break;
      default:
         printf("ERROR: unexpected command %c detected in main\n",glCmdChar);
         return(-1);
   }

   retv = deInitRTSPParser((void *)rtspPtr);
   if(retv != 0)
   {
      printf("ERROR: deInitRTSPParser() returned %d\n",retv);
      return(-1);
   }

   printf("INFO: all done !\n");

	return(0);
}

int testCallback(RTSPPARSINGRESULTS * parsingResPtr, void * appArgument)
{
   int retv;
   unsigned int cea_res, vesa_res, hh_res;
   const char * url;
   char * nu;
   struct rtsp_message * msgPtr;
	struct rtsp * rtspSession;

   if(!parsingResPtr || !parsingResPtr->parsedMessagePtr)
      return(-1);
   
   printf("\nINFO: ***** testCallback *****\n");
   
   switch(parsingResPtr->messageType)
   {
      case RTSP_MESSAGE_REQUEST:

         msgPtr = parsingResPtr->parsedMessagePtr;
	      rtspSession = msgPtr->bus;
	      if(!rtspSession)
         {
            printf("ERROR: NULL rtspSession in testCallback()\n");
            return(-1);
         }

         printParseResults(parsingResPtr->messageType,parsingResPtr);

         //
         // session control logic - JUST an example
         //
         // if(!strcmp(parsingResPtr->request_method,"SET_PARAMETER"))
         // {
         //    // business logic here ...
         // }
         // 

         retv = composeRTSPResponse((void *)rtspSession,parsingResPtr,RTSP_CODE_OK,
               testComposeCallback,(void *)22222);
         if(retv != 0)
         {
            printf("ERROR: composeRTSPResponse() returned %d\n",retv);
            return(-1);
         }

         break;
      case RTSP_MESSAGE_REPLY:
         printParseResults(parsingResPtr->messageType,parsingResPtr);
         break;
      default:
         printf("\nERROR: testCallback() - unexpected message type: %d\n\n",parsingResPtr->messageType);
         return(-1);
   }

   printf("\n");
   return(0);
}

int testComposeCallback(RTSPCOMPOSINGRESULTS * composingResPtr, void * appArgument)
{
   if(!composingResPtr || !composingResPtr->composedMessagePtr)
      return(-1);

   printf("\nINFO: ***** testComposeCallback *****\n");

   switch(composingResPtr->messageType)
   {
      case RTSP_MESSAGE_REQUEST:
         printf("INFO: received composed request:\n%s\n",
            composingResPtr->composedMessagePtr);
         break;
      case RTSP_MESSAGE_REPLY:
         printf("INFO: received composed response:\n%s\n",
            composingResPtr->composedMessagePtr);
         break;
      default:
         printf("ERROR: testComposeCallback() - unexpected message type: %d\n\n",
            composingResPtr->messageType);
         return(-1);
   }

   printf("\n");
   return(0);
}

int printParseResults(int messageType, RTSPPARSINGRESULTS * parseResults)
{
   printf("INFO: parsing results:\n");

   switch(messageType)
   {
      case RTSP_MESSAGE_REQUEST:
         printf("   type: RTSP_MESSAGE_REQUEST\n");
         printf("   number of headers: %d\n",parseResults->parsedMessagePtr->header_used);
         if(parseResults->request_method)
            printf("   request_method: %s\n",parseResults->request_method);
         if(parseResults->request_uri)
            printf("   request_uri: %s\n",parseResults->request_uri);
         break;
      case RTSP_MESSAGE_REPLY:
         printf("   type: RTSP_MESSAGE_REPLY\n");
         printf("   number of headers: %d\n",parseResults->parsedMessagePtr->header_used);
         if(parseResults->reply_phrase)
            printf("   reply_phrase: %s\n",parseResults->reply_phrase);
         printf("   reply_code: %d\n",parseResults->reply_code);
         break;
      default:
         printf("ERROR: printParseResults() - unexpected message type: %d\n",messageType);
         return(-1);
   }

   if(parseResults->headerData.sourceRTPPort >= 0)
      printf("   sourceRTPPort: %d\n",parseResults->headerData.sourceRTPPort);
   if(parseResults->headerData.sessionID)
      printf("   sessionID: %s\n",parseResults->headerData.sessionID);
   if(parseResults->headerData.triggerMethod)
      printf("   triggerMethod: %s\n",parseResults->headerData.triggerMethod);
}

bool processCommandLine(int argc, char * argv[])
{
   int nn,nextarg;
   
   if(argc > 1)
   {
      if(!strcmp(argv[1],"?") || !strcmp(argv[1],"/?") || !strcmp(argv[1],"/h"))
      {
         printf("%s\n",g_cmdLineHelpStr);
         return false;          // will force program exit in main()
      }
      for(nn=1;nn<argc;nn++)
      {
         if(argv[nn][0] == '-')
         {
            if((argc > (nn + 1)) && (argv[nn+1][0] != '-'))
                  nextarg = 1;
            else  nextarg = 0;
            switch(argv[nn][1])
            {
               case 'c':
                  if(nextarg)
                  {
                     glCmdChar = argv[nn+1][0];
                     nn++;
                  }
                  break;
               case 'h':
               case '?':
                  printf("%s\n",g_cmdLineHelpStr);
                  return false;           // will force program exit in main()
               case '\0':
                  printf("WARNING: ignoring empty option\n");
                  break;
               default:
                  printf("WARNING: ignoring unsupported option %s\n",argv[nn]);
            }
         }
         else
         {
            printf("WARNING: missing '-' before option %s\n",argv[nn]);
         }
      }
   }
   else
   {
      printf("%s\n",g_cmdLineHelpStr);
      return false;           // will force program exit in main()
   }
   
   return true;
}

int readDataFromInput(char * buff, int buffSize)
{
   int         retv, nn = 0;
   char        charBuff;

   // for now - using blocking read
   while((retv = read(STDIN_FILENO, &charBuff, 1)) == 1)
   {
      buff[nn] = charBuff;
      nn++;
      if(nn > (buffSize-2))
      {
         printf("WARNING: provided test data is too long - dropping the remainder\n");
         buff[nn] = '\0';
         return(nn);
      }
   }
   if(retv != 0)
   {
      // error
      return(-1);
   }

   // EOF
   buff[nn] = '\0';
   return(nn);
}

int getRequestInfoFromInputData(char * buff, int buffSize, char * reqMethodBuff,
      int reqMethodBuffSize, char * reqArg0Buff, int reqArg0BuffSize,
      char * reqArg1Buff,int reqArg1BuffSize)
{
   int         tokenCnt;
   char *      tokens[64];

   int charCnt = getLineFromFile(buff, buffSize, STDIN_FILENO, 1);
   if(charCnt > 0)
   {
      tokenCnt = splitStrOnWS(buff, tokens, 64);

      printf("DEBUG: splitStrOnWS() returned %d\n",tokenCnt);

      // *** // for testing only
      // *** int nn;
      // *** printf("INFO: tokens:\n");
      // *** for(nn=0;nn<tokenCnt;nn++)
      // *** {
      // ***    printf("   %s\n",tokens[nn]);
      // *** }

      if(tokenCnt > 0)
      {
         strncpy(reqMethodBuff,tokens[0],reqMethodBuffSize - 1);
         reqMethodBuff[reqMethodBuffSize - 1] = '\0';
         if(tokenCnt > 1)
         {
            strncpy(reqArg0Buff,tokens[1],reqArg0BuffSize - 1);
            reqArg0Buff[reqArg0BuffSize - 1] = '\0';
            if(tokenCnt > 2)
            {
               strncpy(reqArg1Buff,tokens[1],reqArg1BuffSize - 1);
               reqArg1Buff[reqArg1BuffSize - 1] = '\0';
               return(3);
            }
            return(2);
         }
         return(1);
      }
      else
      {
         printf("ERROR: splitStrOnWS() returned %d\n",tokenCnt);
      }
   }
   else
   {
      printf("ERROR: getLineFromFile() returned %d\n",charCnt);
   }

   return(0);
}

int splitStrOnWS(char * strStart, char ** tokenArr, int tokenArrSize)
{
   int nn = 0;
   char * strEnd = strStart + strlen(strStart);
   if(strEnd == strStart)
      return(0);
   char * strPtr = strStart;
   while(strStart < strEnd)
   {
      strStart = findCharRun(strStart, strEnd, 0);
      strPtr = findCharRun(strStart, strEnd, 1);
      if(strStart >= strPtr)
         return(nn);
      if(tokenArr && (nn < tokenArrSize))
         tokenArr[nn] = strStart;
      if(strPtr >= strEnd)
         break;
      *strPtr = '\0';
      strStart = strPtr + 1;
      nn++;
   }
   return(nn + 1);
}

char * findCharRun(char * strStart, char * strEnd, int invertCheck)
{
   while((strStart < strEnd) && spaceCheck(*strStart, invertCheck))
      ++strStart;
   return strStart;
}

int spaceCheck(char chr, int invertCheck)
{
   if(invertCheck)
         return(!isspace(chr));
   else  return(isspace(chr));
}

int getLineFromFile(char * buff, int size, int fileHd, int expectCRLF)
{
   int retv,nn = 0;
   if(!buff || (fileHd < 0) || (size <= 0))
      return(-1);
   while((nn < (size - 1)) && ((retv = read(fileHd, &buff[nn], 1)) > 0))
   {
      switch(buff[nn])
      {
         case '\r':
            if(!expectCRLF)
            {
	            buff[nn] = '\0';
               return(nn);
            }
            break;
         case '\n':
            if((nn > 0) && (buff[nn - 1] == '\r'))
            {
	            buff[nn - 1] = '\0';
               return(nn - 1);
            }
            else
            {
	            buff[nn] = '\0';
               return(nn);
            }
         case '\0':
	         buff[nn] = '\0';
            return(nn);
         default:
            break;
      }
      nn++;
   }
   buff[nn] = '\0';
   return(nn);
}

#endif


void * initRTSPParser(RTSPSYSTEMINFO * sysInfo)
{
   // Remarks:
   //    1. sysInfo is allowed to be NULL
   // 
   int         retv;
   int         rtpPort;
   char *      prefResRefStr;
   char *      prefCodecStr;
   struct rtsp * rtspSession;

   rtpPort = 4570;
   prefResRefStr = "1920x1080i60";
   prefCodecStr = "LPCMx44_1x2";

   if(sysInfo)
   {
   if(sysInfo->rtpPort > 0)
         rtpPort = sysInfo->rtpPort;
   if(sysInfo->preferredVidResRefStr && (sysInfo->preferredVidResRefStr[0] != '\0'))
         prefResRefStr = sysInfo->preferredVidResRefStr;
   if(sysInfo->preferredAudioCodecStr && (sysInfo->preferredAudioCodecStr[0] != '\0'))
         prefCodecStr = sysInfo->preferredAudioCodecStr;
   }

   retv = rtsp_open(&rtspSession,0);
   if(retv)
      return(NULL);

   rtspSession->rtpPort = rtpPort;
   strncpy(rtspSession->preferredVidResRefStr,prefResRefStr,
      sizeof(rtspSession->preferredVidResRefStr));
   rtspSession->preferredVidResRefStr[
      sizeof(rtspSession->preferredVidResRefStr)-1] = '\0';
   strncpy(rtspSession->preferredAudioCodecStr,prefCodecStr,
      sizeof(rtspSession->preferredAudioCodecStr));
   rtspSession->preferredAudioCodecStr[
      sizeof(rtspSession->preferredAudioCodecStr)-1] = '\0';

   rtspSession->sourceRTPPort = -1;
   rtspSession->sessionID[0] = '\0';
   rtspSession->triggerMethod[0] = '\0';
   rtspSession->transport[0] = '\0';
   rtspSession->presentationURL[0] = '\0';
   rtspSession->cea_res = 0;
   rtspSession->vesa_res = 0;
   rtspSession->hh_res = 0;

   init_code_descriptions();

   init_log_codes();

   return((void *)rtspSession);
}

void init_code_descriptions()
{
   code_descriptions[RTSP_CODE_CONTINUE]					= "Continue";

   code_descriptions[RTSP_CODE_OK]						= "OK";
   code_descriptions[RTSP_CODE_CREATED]					= "Created";

   code_descriptions[RTSP_CODE_LOW_ON_STORAGE_SPACE]			= "Low on Storage Space";

   code_descriptions[RTSP_CODE_MULTIPLE_CHOICES]				= "Multiple Choices";
   code_descriptions[RTSP_CODE_MOVED_PERMANENTLY]				= "Moved Permanently";
   code_descriptions[RTSP_CODE_MOVED_TEMPORARILY]				= "Moved Temporarily";
   code_descriptions[RTSP_CODE_SEE_OTHER]					= "See Other";
   code_descriptions[RTSP_CODE_NOT_MODIFIED]				= "Not Modified";
   code_descriptions[RTSP_CODE_USE_PROXY]					= "Use Proxy";

   code_descriptions[RTSP_CODE_BAD_REQUEST]					= "Bad Request";
   code_descriptions[RTSP_CODE_UNAUTHORIZED]				= "Unauthorized";
   code_descriptions[RTSP_CODE_PAYMENT_REQUIRED]				= "Payment Required";
   code_descriptions[RTSP_CODE_FORBIDDEN]					= "Forbidden";
   code_descriptions[RTSP_CODE_NOT_FOUND]					= "Not Found";
   code_descriptions[RTSP_CODE_METHOD_NOT_ALLOWED]				= "Method not Allowed";
   code_descriptions[RTSP_CODE_NOT_ACCEPTABLE]				= "Not Acceptable";
   code_descriptions[RTSP_CODE_PROXY_AUTHENTICATION_REQUIRED]		= "Proxy Authentication Required";
   code_descriptions[RTSP_CODE_REQUEST_TIMEOUT]				= "Request Time-out";
   code_descriptions[RTSP_CODE_GONE]					= "Gone";
   code_descriptions[RTSP_CODE_LENGTH_REQUIRED]				= "Length Required";
   code_descriptions[RTSP_CODE_PRECONDITION_FAILED]				= "Precondition Failed";
   code_descriptions[RTSP_CODE_REQUEST_ENTITY_TOO_LARGE]			= "Request Entity Too Large";
   code_descriptions[RTSP_CODE_REQUEST_URI_TOO_LARGE]			= "Request-URI too Large";
   code_descriptions[RTSP_CODE_UNSUPPORTED_MEDIA_TYPE]			= "Unsupported Media Type";

   code_descriptions[RTSP_CODE_PARAMETER_NOT_UNDERSTOOD]			= "Parameter not Understood";
   code_descriptions[RTSP_CODE_CONFERENCE_NOT_FOUND]			= "Conference not Found";
   code_descriptions[RTSP_CODE_NOT_ENOUGH_BANDWIDTH]			= "Not Enough Bandwidth";
   code_descriptions[RTSP_CODE_SESSION_NOT_FOUND]				= "Session not Found";
   code_descriptions[RTSP_CODE_METHOD_NOT_VALID_IN_THIS_STATE]		= "Method not Valid in this State";
   code_descriptions[RTSP_CODE_HEADER_FIELD_NOT_VALID_FOR_RESOURCE]		= "Header Field not Valid for Resource";
   code_descriptions[RTSP_CODE_INVALID_RANGE]				= "Invalid Range";
   code_descriptions[RTSP_CODE_PARAMETER_IS_READ_ONLY]			= "Parameter is Read-only";
   code_descriptions[RTSP_CODE_AGGREGATE_OPERATION_NOT_ALLOWED]		= "Aggregate Operation not Allowed";
   code_descriptions[RTSP_CODE_ONLY_AGGREGATE_OPERATION_ALLOWED]		= "Only Aggregate Operation Allowed";
   code_descriptions[RTSP_CODE_UNSUPPORTED_TRANSPORT]			= "Unsupported Transport";
   code_descriptions[RTSP_CODE_DESTINATION_UNREACHABLE]			= "Destination Unreachable";

   code_descriptions[RTSP_CODE_INTERNAL_SERVER_ERROR]			= "Internal Server Error";
   code_descriptions[RTSP_CODE_NOT_IMPLEMENTED]				= "Not Implemented";
   code_descriptions[RTSP_CODE_BAD_GATEWAY]					= "Bad Gateway";
   code_descriptions[RTSP_CODE_SERVICE_UNAVAILABLE]				= "Service Unavailable";
   code_descriptions[RTSP_CODE_GATEWAY_TIMEOUT]				= "Gateway Time-out";
   code_descriptions[RTSP_CODE_RTSP_VERSION_NOT_SUPPORTED]			= "RTSP Version not Supported";

   code_descriptions[RTSP_CODE_OPTION_NOT_SUPPORTED]			= "Option not Supported";

   code_descriptions[RTSP_CODE_CNT]						= NULL;
}


int deInitRTSPParser(void * session)
{
   struct rtsp * rtspSession;

   if(!session)
      return(-1);

   rtspSession = (struct rtsp *)session;

   // to be completed ...

   return(0);
}

int parseRTSPMessage(void * session,char * message, RTSPPARSERAPP_CALLBACK callback,
      void * callbackArg)
{
   int         retv;
   struct rtsp * rtspSession;

   if(!session || !message)
      return(-1);

   rtspSession = (struct rtsp *)session;

   rtspSession->crestCallback = callback;
   rtspSession->crestCallbackArg = callbackArg;

   retv = rtsp_parse_data(rtspSession,message,strlen(message));
   printf("INFO: rtsp_parse_data() returned %d\n",retv);
   if(retv != 0)
      return(retv);

   rtspSession->crestCallback = NULL;
   rtspSession->crestCallbackArg = NULL;

   return(0);
}

int composeRTSPRequest(void * session,char * requestMethod,RTSPPARSERAPP_COMPOSECALLBACK callback,
      void * callbackArg)
{
   int         retv;
   char *      urlPtr;
   char        locBuff[512];
   struct rtsp * rtspSession;
   _rtsp_message_unref_ struct rtsp_message * rep = NULL;

   if(!session || !requestMethod)
      return(-1);

   rtspSession = (struct rtsp *)session;

   rtspSession->crestComposeCallback = callback;
   rtspSession->crestComposeCallbackArg = callbackArg;

   if(!strcmp(requestMethod,"OPTIONS"))
   {
      urlPtr = "*";
      retv = rtsp_message_new_request(rtspSession,&rep,requestMethod,urlPtr);
      if(retv < 0)
      {
         printf("ERROR: rtsp_message_new_request failed in composeRTSPRequest() with error code %d\n",retv);
         return(-1);
      }

      retv = rtsp_message_append(rep, "<s>","Require","org.wfa.wfd1.0");
      if(retv < 0)
      {
         printf("ERROR: rtsp_message_append() failed in composeRTSPResponse() with error code %d\n",retv);
         return(-1);
      }
   }
   else if(!strcmp(requestMethod,"SETUP"))
   {
      if(rtspSession->presentationURL[0] == '\0')
      {
         printf("WARNING: empty presentationURL string in composeRTSPRequest() with method SETUP\n");
      }
      urlPtr = rtspSession->presentationURL;
      retv = rtsp_message_new_request(rtspSession,&rep,requestMethod,urlPtr);
      if(retv < 0)
      {
         printf("ERROR: rtsp_message_new_request failed in composeRTSPRequest() with error code %d\n",retv);
         return(-1);
      }

      sprintf(locBuff,"RTP/AVP/UDP;unicast;client_port=%d",rtspSession->rtpPort);
      retv = rtsp_message_append(rep, "<s>","Transport",locBuff);
      if(retv < 0)
      {
         printf("ERROR: rtsp_message_append() failed in composeRTSPResponse() with error code %d\n",retv);
         return(-1);
      }
   }
   else if(!strcmp(requestMethod,"PLAY"))
   {
      if(rtspSession->presentationURL[0] == '\0')
      {
         printf("WARNING: empty presentationURL string in composeRTSPRequest() with method PLAY\n");
      }
      urlPtr = rtspSession->presentationURL;
      retv = rtsp_message_new_request(rtspSession,&rep,requestMethod,urlPtr);
      if(retv < 0)
      {
         printf("ERROR: rtsp_message_new_request failed in composeRTSPRequest() with error code %d\n",retv);
         return(-1);
      }
      if(rtspSession->sessionID[0] == '\0')
      {
         printf("WARNING: empty sessionID string in composeRTSPRequest() with method PLAY\n");
      }
      retv = rtsp_message_append(rep, "<s>","Session",rtspSession->sessionID);
      if(retv < 0)
      {
         printf("ERROR: rtsp_message_append() failed in composeRTSPResponse() with error code %d\n",retv);
         return(-1);
      }
   }
   else
   {
      printf("ERROR: unexpected request method %s in composeRTSPRequest()\n",requestMethod);
      return(-1);
   }

   rtsp_message_seal(rep);

   // currently rtsp_message_serialize_common() terminates the message string
   char * raw_message = (char *)rtsp_message_get_raw(rep);
   if(raw_message)
      printf("INFO: raw_message: %s\n",raw_message);

   retv = cresRTSP_internalComposeCallback(session,RTSP_MESSAGE_REQUEST,raw_message,requestMethod,
      urlPtr,NULL,-1);
	if (retv < 0)
      printf("ERROR: cresRTSP_internalComposeCallback() returned %d in composeRTSPResponse()\n",retv);

   rtsp_message_unref(rep);
   rep = NULL;

   rtspSession->crestComposeCallback = NULL;
   rtspSession->crestComposeCallbackArg = NULL;

   return(retv);
}

int composeRTSPResponse(void * session,RTSPPARSINGRESULTS * requestParsingResultsPtr,
      int responseStatus, RTSPPARSERAPP_COMPOSECALLBACK callback, void * callbackArg)
{
   //
   // Remarks:
   //    1. Values passed in responseStatus must conform to the RTSP_CODE_XXXX
   //       enumeration.
   //    2.
   // 
   int         retv;
   struct rtsp * rtspSession;
   _rtsp_message_unref_ struct rtsp_message * rep = NULL;
   struct rtsp_message * orgMsg;
   char        locBuff[512];

   if(!session || !requestParsingResultsPtr || !requestParsingResultsPtr->parsedMessagePtr)
      return(-1);

   rtspSession = (struct rtsp *)session;

   orgMsg = requestParsingResultsPtr->parsedMessagePtr;



   //
   // *** temporary - complete it ***
   //
   if(responseStatus != RTSP_CODE_OK)
   {
      printf("ERROR: composeRTSPResponse() only supports response status RTSP_CODE_OK at this time\n");
      return(-1);
   }



   rtspSession->crestComposeCallback = callback;
   rtspSession->crestComposeCallbackArg = callbackArg;

   retv = rtsp_message_new_reply_for(orgMsg, &rep, RTSP_CODE_OK, NULL);
   if(retv < 0)
   {
      printf("ERROR: rtsp_message_new_reply_for() failed in composeRTSPResponse() with error code %d\n",retv);
      return(-1);
   }

   char * org_request_method = (char*)rtsp_message_get_method(orgMsg);

   if(!strcmp(org_request_method,"OPTIONS"))
   {
      retv = rtsp_message_append(rep, "<s>","Public","org.wfa.wfd1.0, GET_PARAMETER, SET_PARAMETER");
      if(retv < 0)
      {
         printf("ERROR: rtsp_message_append() failed in composeRTSPResponse() with error code %d\n",retv);
         return(-1);
      }
   }
   else if(!strcmp(org_request_method,"GET_PARAMETER"))
   {
      /* wfd_video_formats */
      // *** check_and_response_option("wfd_video_formats",
      // ***    "40 00 01 10 0001bdeb 051557ff 00000fff 10 0000 001f 11 0780 0438, "
      // ***    "02 10 0001bdeb 155557ff 00000fff 10 0000 001f 11 0780 0438");
      retv = rtsp_encodeVideoFormat(locBuff,sizeof(locBuff),
         rtspSession->preferredVidResRefStr);
      if(retv)
      {
         printf("ERROR: failed to construct video format string from prefString %s\n",
            rtspSession->preferredVidResRefStr);
         return(-1);
      }
      printf("INFO: video format string (from prefString %s) is : %s\n",
         rtspSession->preferredVidResRefStr,locBuff);
      check_and_response_option("wfd_video_formats",locBuff);

      /* wfd_audio_codecs */
      // *** check_and_response_option("wfd_audio_codecs", "LPCM 00000003 00, AAC 0000000f 00, AC3 00000007 00");
      retv = rtsp_encodeAudioFormat(locBuff,sizeof(locBuff),
         rtspSession->preferredAudioCodecStr);
      if(retv)
      {
         printf("ERROR: failed to construct audio format string from prefString %s\n",
            rtspSession->preferredAudioCodecStr);
         return(-1);
      }
      printf("INFO: video format string (from prefString %s) is : %s\n",
         rtspSession->preferredAudioCodecStr,locBuff);
      check_and_response_option("wfd_audio_codecs",locBuff);

      /* wfd_client_rtp_ports */
      sprintf(locBuff,"RTP/AVP/UDP;unicast %d 0 mode=play",rtspSession->rtpPort);
      check_and_response_option("wfd_client_rtp_ports",locBuff);

      /* wfd_content_protection */
      check_and_response_option("wfd_content_protection", "none");

      /* wfd_display_edid */
      // different than in captured POC session
      check_and_response_option("wfd_display_edid", "none");

      /* wfd_coupled_sink */
      check_and_response_option("wfd_coupled_sink", "none");

      /* --- others --- */

      check_and_response_option("wfd_connector_type", "05");
      check_and_response_option("wfd_idr_request_capability", "1");

      check_and_response_option("microsoft_cursor", "none");
      check_and_response_option("microsoft_rtcp_capability", "none");
      check_and_response_option("microsoft_latency_management_capability", "none");
      check_and_response_option("microsoft_format_change_capability", "none");
      check_and_response_option("microsoft_diagnostics_capability", "none");

      check_and_response_option("intel_friendly_name", "miraclecast");
      check_and_response_option("intel_sink_manufacturer_name", "GNU Linux");
      check_and_response_option("intel_sink_model_name", "Arch linux");
      check_and_response_option("intel_sink_device_URL", "http://github.com/albfan/miraclecast");

      // /* wfd_uibc_capability */
      // if (uibc_option) {
      //    check_and_response_option("wfd_uibc_capability",
      //       "input_category_list=GENERIC, HIDC;"
      //       "generic_cap_list=Keyboard;"
      //       "hidc_cap_list=Keyboard/USB, Mouse/USB, MultiTouch/USB, Gesture/USB, RemoteControl/USB;"
      //       "port=none");
   }
   else if(!strcmp(org_request_method,"SET_PARAMETER"))
   {
      printf("INFO: not adding anything to the response for SET_PARAMETER\n");
      // nothing - response has just the response line and CSeq
   }
   else
   {
      printf("ERROR: unexpected request method %s in composeRTSPResponse()\n",org_request_method);
      return(-1);
   }

   rtsp_message_seal(rep);

   char * reply_phrase = (char*)rtsp_message_get_phrase(rep);
   if(reply_phrase)
      printf("INFO: reply_phrase: %s\n",reply_phrase);

   unsigned int reply_code = rtsp_message_get_code(rep);
   printf("INFO: reply_code: %d\n",reply_code);

   // currently rtsp_message_serialize_common() terminates the message string
   char * raw_message = (char *)rtsp_message_get_raw(rep);
   if(raw_message)
      printf("INFO: raw_message: %s\n",raw_message);

   retv = cresRTSP_internalComposeCallback(session,RTSP_MESSAGE_REPLY,raw_message,NULL,NULL,
      reply_phrase,reply_code);
	if (retv < 0)
      printf("ERROR: cresRTSP_internalComposeCallback() returned %d in composeRTSPResponse()\n",retv);

   rtsp_message_unref(rep);
   rep = NULL;

   rtspSession->crestComposeCallback = NULL;
   rtspSession->crestComposeCallbackArg = NULL;

   return(retv);
}

int cresRTSP_internalCallback(void * session,unsigned int messageType,
      struct rtsp_message * parsedMessagePtr,char * request_method,char * request_uri,
      char * reply_phrase,unsigned int reply_code)
{
   int                  retv;
   unsigned int         cea_res, vesa_res, hh_res;
   const char *         urlPtr;
   const char *         triggerMethod = NULL;
   const char *         sessionID = NULL;
   const char *         transport = NULL;
   struct rtsp *        rtspSession;
   RTSPPARSINGRESULTS   parsingResults;

   if(!session || !parsedMessagePtr || (messageType < 0))
      return(-1);

   rtspSession = (struct rtsp *)session;

   // get request header values that need to be stored in the session data
   switch(messageType)
   {
      case RTSP_MESSAGE_REQUEST:
         if(!request_method)
            return(-1);

         if(!strcmp(request_method,"SET_PARAMETER"))
         {
            retv = rtsp_message_read(parsedMessagePtr, "{<s>}", "wfd_presentation_URL", &urlPtr);
            if (retv >= 0)
            {
               strncpy(rtspSession->presentationURL,urlPtr,sizeof(rtspSession->presentationURL) - 1);
               rtspSession->presentationURL[sizeof(rtspSession->presentationURL) - 1] = '\0';
            }


            // !!! must do more important video parameters !!!


            retv = rtsp_message_read(parsedMessagePtr, "{<****hhh>}", "wfd_video_formats",
               &cea_res,&vesa_res,&hh_res);
            if (retv == 0)
            {
               rtspSession->cea_res  = cea_res;
               rtspSession->vesa_res = vesa_res;
               rtspSession->hh_res   = hh_res;
            }


            // !!! must handle audio_codecs headers !!!


            retv = rtsp_message_read(parsedMessagePtr, "{<s>}", "wfd_trigger_method", &triggerMethod);
            if (retv >= 0)
            {
               strncpy(rtspSession->triggerMethod,triggerMethod,sizeof(rtspSession->triggerMethod) - 1);
               rtspSession->triggerMethod[sizeof(rtspSession->triggerMethod) - 1] = '\0';
            }
         }
         break;
      case RTSP_MESSAGE_REPLY:

         //
         // for this header data retrieval mechanism to be more precise, these operations
         // would need to be qualified with the request_method of the request to which
         // this response corresponds
         // 

         retv = rtsp_message_read(parsedMessagePtr, "<s>", "Session", &sessionID);
         if (retv >= 0)
            {
               strncpy(rtspSession->sessionID,sessionID,sizeof(rtspSession->sessionID) - 1);
               rtspSession->sessionID[sizeof(rtspSession->sessionID) - 1] = '\0';
            }
         retv = rtsp_message_read(parsedMessagePtr, "<s>", "Transport", &transport);
         if (retv >= 0)
            {
               strncpy(rtspSession->transport,transport,sizeof(rtspSession->transport) - 1);
               rtspSession->transport[sizeof(rtspSession->transport) - 1] = '\0';
            }
         break;
      default:
         break;
   }

   if(rtspSession->crestCallback)
   {
      parsingResults.messageType       = messageType;
      parsingResults.request_method    = request_method;
      parsingResults.request_uri       = request_uri;
      parsingResults.reply_phrase      = reply_phrase;
      parsingResults.reply_code        = reply_code;
      parsingResults.parsedMessagePtr  = parsedMessagePtr;

      if(sessionID)
         {
         parsingResults.headerData.sessionID = rtspSession->sessionID;
         printf("INFO: cresRTSP_internalCallback(() - sessionID = %s\n",
            parsingResults.headerData.sessionID);
         }
      else parsingResults.headerData.sessionID = NULL;
      if(triggerMethod)
         {
         parsingResults.headerData.triggerMethod = rtspSession->triggerMethod;
         printf("INFO: cresRTSP_internalCallback(() - triggerMethod = %s\n",
            parsingResults.headerData.triggerMethod);
         }
      else parsingResults.headerData.triggerMethod = NULL;

      if(transport)
         printf("INFO: cresRTSP_internalCallback(() - transport = %s\n",transport);

      retv = rtspSession->crestCallback(&parsingResults,rtspSession->crestCallbackArg);
   }
   else retv = -1;

   return(retv);
}

int cresRTSP_internalComposeCallback(void * session,unsigned int messageType,
      char * composedMessagePtr,char * request_method,char * request_uri,char * reply_phrase,
      unsigned int reply_code)
{
   int                  retv;
   struct rtsp *        rtspSession;
   RTSPCOMPOSINGRESULTS composingResults;

   if(!session || !composedMessagePtr || (messageType < 0))
      return(-1);

   rtspSession = (struct rtsp *)session;

   if(rtspSession->crestComposeCallback)
   {
      composingResults.messageType        = messageType;
      composingResults.request_method     = request_method;
      composingResults.request_uri        = request_uri;
      composingResults.reply_phrase       = reply_phrase;
      composingResults.reply_code         = reply_code;
      composingResults.composedMessagePtr = composedMessagePtr;

      retv = rtspSession->crestComposeCallback(&composingResults,rtspSession->crestComposeCallbackArg);
   }
   else retv = -1;

   return(retv);
}


// ----- additional rtsp_ functions -----

bool check_rtsp_option(struct rtsp_message *m, char *option) {
	return rtsp_message_read(m, "{<>}", option) >= 0;
}


// ----- WFD header data encoding / decoding -----

int rtsp_encodeVideoFormat(char * outBuff, int outBuffSize, char * encodedValStr)
{
   int index;
   uint32_t ceaFlags = 0,vesaFlags = 0,hhFlags = 0;
   char locBuff[128];

   if(!outBuff || !encodedValStr)
      return(-1);

   GETENCTABLEINDEX(ceaResRefEnc,encodedValStr,index);
   if(index >= 0)
      ceaFlags = GETBINFLAG(index);
   else 
   {
      GETENCTABLEINDEX(vesaResRefEnc,encodedValStr,index);
      if(index >= 0)
         vesaFlags = GETBINFLAG(index);
      else
      {
         GETENCTABLEINDEX(hhResRefEnc,encodedValStr,index);
         if(index >= 0)
            hhFlags = GETBINFLAG(index);
         else
         {
            printf("ERROR: encodeVideoFormat() failed to encode video format%s\n",encodedValStr);
            return(-1);
         }
      }
   }

   sprintf(locBuff,"00 00 01 01 %08x %08x %08x 00 0000 0000 00 none none",
      ceaFlags,vesaFlags,hhFlags);
   if(strlen(locBuff) >= outBuffSize)
   {
      printf("ERROR: outBuffSize (%d) too small to hold encoded string %s\n",locBuff);
      return(-1);
   }

   strcpy(outBuff,locBuff);

	return(0);
}

int rtsp_encodeAudioFormat(char * outBuff, int outBuffSize, char * encodedValStr)
{
   int index;
   uint32_t codecFlags = 0;
   char * codecName;
   char locBuff[128];

   if(!outBuff || !encodedValStr)
      return(-1);

   GETENCTABLEINDEX(lpcmModeEnc,encodedValStr,index);
   if(index >= 0)
   {
      codecFlags = GETBINFLAG(index);
      codecName = "LPCM";
   }
   else 
   {
      GETENCTABLEINDEX(aacModeEnc,encodedValStr,index);
      if(index >= 0)
      {
         codecFlags = GETBINFLAG(index);
         codecName = "AAC";
      }
      else
      {
         GETENCTABLEINDEX(ac3ModeEnc,encodedValStr,index);
         if(index >= 0)
         {
            codecFlags = GETBINFLAG(index);
            codecName = "AC3";
         }
         else
         {
            printf("ERROR: encodeAudioFormat() failed to encode video format%s\n",encodedValStr);
            return(-1);
         }
      }
   }

   sprintf(locBuff,"%s %08x 00",codecName,codecFlags);
   if(strlen(locBuff) >= outBuffSize)
   {
      printf("ERROR: outBuffSize (%d) too small to hold encoded string %s\n",locBuff);
      return(-1);
   }

   strcpy(outBuff,locBuff);

	return(0);
}


// ----- clib function substitutes -----

char * loc_strchrnul( char * s, int c)
{
   char * retp;
   retp = strrchr(s,c);
   if(retp == NULL)
      retp = s + strlen(s);
   return(retp);
}

char * loc_stpcpy(char * dest, char * src)
{
   size_t len = strlen(src);
   return (char*)(memcpy(dest,src,len + 1) + len);
}

// ***



/*
 * Helpers
 * Some helpers that don't really belong into a specific group.
 */
static const char *get_code_description(unsigned int code)
{
	const char *error = "Internal Error";

	if (code >= SHL_ARRAY_LENGTH(code_descriptions))
		return error;

	return code_descriptions[code] ? : error;
}

static size_t sanitize_line(char *line, size_t len)
{
	char *src, *dst, c, prev, last_c;
	size_t i;
	bool quoted, escaped;

	src = line;
	dst = line;
	last_c = 0;
	quoted = false;
	escaped = false;

	for (i = 0; i < len; ++i) {
		c = *src++;
		prev = last_c;
		last_c = c;

		if (escaped) {
			escaped = false;
			/* turn escaped binary zero into "\0" */
			if (c == '\0') {
				c = '0';
				last_c = c;
			}
		} else if (quoted) {
			if (c == '"') {
				quoted = false;
			} else if (c == '\0') {
				/* skip binary 0 */
				last_c = prev;
				continue;
			} else if (c == '\\') {
				escaped = true;
			}
		} else {
			/* ignore any binary 0 */
			if (c == '\0') {
				last_c = prev;
				continue;
			}

			/* turn new-lines/tabs into white-space */
			if (c == '\r' || c == '\n' || c == '\t') {
				c = ' ';
				last_c = c;
			}

			/* trim whitespace */
			if (c == ' ' && prev == ' ')
				continue;

			/* trim leading whitespace */
			if (c == ' ' && dst == line)
				continue;

			if (c == '"')
				quoted = true;
		}

		*dst++ = c;
	}

	/* terminate string with binary zero */
	*dst = 0;

	/* remove trailing whitespace */
	if (!quoted) {
		while (dst > line && *(dst - 1) == ' ')
			*--dst = 0;
	}

	return dst - line;
}

/*
 * Messages
 * The message-layer is responsible of message handling for users. It does not
 * do the wire-protocol parsing! It is solely responsible for the user API to
 * assemble and inspect messages.
 *
 * We use per-message iterators to allow simply message-assembly and parsing in
 * a sequential manner. We do some limited container-formats, so you can dive
 * into a header, parse its contents and exit it again.
 *
 * Note that messages provide sealing-capabilities. Once a message is sealed,
 * it can never be modified again. All messages that are submitted to the bus
 * layer, or are received from the bus layer, are always sealed.
 */

static int rtsp_message_new(struct rtsp *bus,
			    struct rtsp_message **out)
{
	_rtsp_message_unref_ struct rtsp_message *m = NULL;

	if (!bus || !out)
		return -EINVAL;

	m = (struct rtsp_message *)calloc(1, sizeof(*m));
	if (!m)
		return -ENOMEM;

	m->ref = 1;
	m->bus = bus;
	rtsp_ref(bus);
	m->type = RTSP_MESSAGE_UNKNOWN;
	m->major = 1;
	m->minor = 0;

	*out = m;
	m = NULL;
	return 0;
}

static int rtsp_message_new_unknown(struct rtsp *bus,
				    struct rtsp_message **out,
				    const char *head)
{
	_rtsp_message_unref_ struct rtsp_message *m = NULL;
	int r;

	if (!bus || !out || !head)
		return -EINVAL;

	r = rtsp_message_new(bus, &m);
	if (r < 0)
		return r;

	m->type = RTSP_MESSAGE_UNKNOWN;
	m->unknown_head = strdup(head);
	if (!m->unknown_head)
		return -ENOMEM;

	*out = m;
	m = NULL;
	return 0;
}

static int rtsp_message_new_request_n(struct rtsp *bus,
				      struct rtsp_message **out,
				      const char *method,
				      size_t methodlen,
				      const char *uri,
				      size_t urilen)
{
	_rtsp_message_unref_ struct rtsp_message *m = NULL;
	int r;

	if (!bus || !out)
		return -EINVAL;
	if (shl_isempty(method) || shl_isempty(uri) || !methodlen || !urilen)
		return -EINVAL;

	r = rtsp_message_new(bus, &m);
	if (r < 0)
		return r;

	m->type = RTSP_MESSAGE_REQUEST;

	m->request_method = strndup(method, methodlen);
	if (!m->request_method)
		return -ENOMEM;

	m->request_uri = strndup(uri, urilen);
	if (!m->request_uri)
		return -ENOMEM;

	*out = m;
	m = NULL;
	return 0;
}

int rtsp_message_new_request(struct rtsp *bus,
			     struct rtsp_message **out,
			     const char *method,
			     const char *uri)
{
	if (!method || !uri)
		return -EINVAL;

	return rtsp_message_new_request_n(bus,
					  out,
					  method,
					  strlen(method),
					  uri,
					  strlen(uri));
}

static int rtsp_message_new_raw_reply(struct rtsp *bus,
				      struct rtsp_message **out,
				      unsigned int code,
				      const char *phrase)
{
	_rtsp_message_unref_ struct rtsp_message *m = NULL;
	int r;

	if (!bus || !out)
		return -EINVAL;
	if (code == RTSP_ANY_CODE)
		return -EINVAL;

	r = rtsp_message_new(bus, &m);
	if (r < 0)
		return r;

	m->type = RTSP_MESSAGE_REPLY;
	m->reply_code = code;

	if (shl_isempty(phrase))
		m->reply_phrase = strdup(get_code_description(code));
	else
		m->reply_phrase = strdup(phrase);
	if (!m->reply_phrase)
		return -ENOMEM;

	*out = m;
	m = NULL;
	return 0;
}

int rtsp_message_new_reply(struct rtsp *bus,
			   struct rtsp_message **out,
			   uint64_t cookie,
			   unsigned int code,
			   const char *phrase)
{
	_rtsp_message_unref_ struct rtsp_message *m = NULL;
	int r;

	if (!bus || !out || !cookie)
		return -EINVAL;

	r = rtsp_message_new_raw_reply(bus, &m, code, phrase);
	if (r < 0)
		return r;

	m->cookie = cookie | RTSP_FLAG_REMOTE_COOKIE;

	*out = m;
	m = NULL;
	return 0;
}

int rtsp_message_new_reply_for(struct rtsp_message *orig,
			       struct rtsp_message **out,
			       unsigned int code,
			       const char *phrase)
{
	_rtsp_message_unref_ struct rtsp_message *m = NULL;
	int r;

	if (!orig || !out)
		return -EINVAL;
	/* @orig must be a message received from the remote peer */
	if (!orig->is_used || !(orig->cookie & RTSP_FLAG_REMOTE_COOKIE))
		return -EINVAL;

	r = rtsp_message_new_reply(orig->bus, &m, orig->cookie, code, phrase);
	if (r < 0)
		return r;

	*out = m;
	m = NULL;
	return 0;
}

int rtsp_message_new_data(struct rtsp *bus,
			  struct rtsp_message **out,
			  unsigned int channel,
			  const void *payload,
			  size_t size)
{
	_rtsp_message_unref_ struct rtsp_message *m = NULL;
	int r;

	if (!bus || !out)
		return -EINVAL;
	if (channel == RTSP_ANY_CHANNEL)
		return -EINVAL;
	if (size > 0 && !payload)
		return -EINVAL;

	r = rtsp_message_new(bus, &m);
	if (r < 0)
		return r;

	m->type = RTSP_MESSAGE_DATA;

	m->data_channel = channel;
	m->data_size = size;
	if (size > 0) {
		m->data_payload = (uint8_t *)malloc(size);
		if (!m->data_payload)
			return -ENOMEM;

		memcpy(m->data_payload, payload, size);
	}

	*out = m;
	m = NULL;
	return 0;
}

void rtsp_message_ref(struct rtsp_message *m)
{
	if (!m || !m->ref)
		return;

	++m->ref;
}

void rtsp_message_unref(struct rtsp_message *m)
{
	size_t i;

	if (!m || !m->ref || --m->ref)
		return;

	for (i = 0; i < m->body_used; ++i) {
		free(m->body_headers[i].key);
		free(m->body_headers[i].value);
		shl_strv_free(m->body_headers[i].tokens);
		free(m->body_headers[i].line);
	}
	free(m->body_headers);

	for (i = 0; i < m->header_used; ++i) {
		free(m->headers[i].key);
		free(m->headers[i].value);
		shl_strv_free(m->headers[i].tokens);
		free(m->headers[i].line);
	}
	free(m->headers);

	free(m->raw);
	free(m->body);

	free(m->data_payload);
	free(m->reply_phrase);
	free(m->request_uri);
	free(m->request_method);
	free(m->unknown_head);

	rtsp_unref(m->bus);
	free(m);
}

bool rtsp_message_is_request(struct rtsp_message *m,
			     const char *method,
			     const char *uri)
{
	return m && m->type == RTSP_MESSAGE_REQUEST &&
	       (!method || !strcasecmp(m->request_method, method)) &&
	       (!uri || !strcmp(m->request_uri, uri));
}

bool rtsp_message_is_reply(struct rtsp_message *m,
			   unsigned int code,
			   const char *phrase)
{
	return m && m->type == RTSP_MESSAGE_REPLY &&
	       (code == RTSP_ANY_CODE || m->reply_code == code) &&
	       (!phrase || !strcmp(m->reply_phrase, phrase));
}

bool rtsp_message_is_data(struct rtsp_message *m,
			  unsigned int channel)
{
	return m && m->type == RTSP_MESSAGE_DATA &&
	       (channel == RTSP_ANY_CHANNEL || m->data_channel == channel);
}

unsigned int rtsp_message_get_type(struct rtsp_message *m)
{
	if (!m)
		return RTSP_MESSAGE_UNKNOWN;

	return m->type;
}

const char *rtsp_message_get_method(struct rtsp_message *m)
{
	if (!m)
		return NULL;

	return m->request_method;
}

const char *rtsp_message_get_uri(struct rtsp_message *m)
{
	if (!m)
		return NULL;

	return m->request_uri;
}

unsigned int rtsp_message_get_code(struct rtsp_message *m)
{
	if (!m)
		return RTSP_ANY_CODE;

	return m->reply_code;
}

const char *rtsp_message_get_phrase(struct rtsp_message *m)
{
	if (!m)
		return NULL;

	return m->reply_phrase;
}

unsigned int rtsp_message_get_channel(struct rtsp_message *m)
{
	if (!m)
		return RTSP_ANY_CHANNEL;

	return m->data_channel;
}

const void *rtsp_message_get_payload(struct rtsp_message *m)
{
	if (!m)
		return NULL;

	return m->data_payload;
}

size_t rtsp_message_get_payload_size(struct rtsp_message *m)
{
	if (!m)
		return 0;

	return m->data_size;
}

struct rtsp *rtsp_message_get_bus(struct rtsp_message *m)
{
	if (!m)
		return NULL;

	return m->bus;
}

uint64_t rtsp_message_get_cookie(struct rtsp_message *m)
{
	if (!m)
		return 0;

	return m->cookie & ~RTSP_FLAG_REMOTE_COOKIE;
}

bool rtsp_message_is_sealed(struct rtsp_message *m)
{
	return m && m->is_sealed;
}

static int rtsp_header_set_value(struct rtsp_header *h,
				 const char *value,
				 size_t valuelen,
				 bool force)
{
	int r;

	if (!valuelen || shl_isempty(value))
		return -EINVAL;

	if (!force) {
		if (h->value || h->token_used || h->line)
			return -EINVAL;
	} else {
		shl_strv_free(h->tokens);
		h->tokens = NULL;
		h->token_used = 0;
		h->token_cnt = 0;

		free(h->value);
		h->value = NULL;

		free(h->line);
		h->line = NULL;
	}

	h->value = strndup(value, valuelen);
	if (!h->value)
		return -ENOMEM;

	r = shl_qstr_tokenize(value, &h->tokens);
	if (r < 0) {
		free(h->value);
		h->value = NULL;
		return -ENOMEM;
	}

	h->token_cnt = r + 1;
	h->token_used = r;

	return 0;
}

static int rtsp_message_append_header(struct rtsp_message *m,
				      struct rtsp_header **out,
				      const char *key,
				      size_t keylen,
				      const char *value,
				      size_t valuelen)
{
	struct rtsp_header *h;
	int r;

	if (!m || !out || !key)
		return -EINVAL;
	if (m->type == RTSP_MESSAGE_DATA)
		return -EINVAL;

	if (m->iter_body) {
		if (!SHL_GREEDY_REALLOC0_T(m->body_headers,
					   m->body_cnt,
					   m->body_used + 1))
			return -ENOMEM;

		h = &m->body_headers[m->body_used];
	} else {
		if (!SHL_GREEDY_REALLOC0_T(m->headers,
					   m->header_cnt,
					   m->header_used + 1))
			return -ENOMEM;

		h = &m->headers[m->header_used];
	}

	h->key = strndup(key, keylen);
	if (!h->key)
		return -ENOMEM;

	if (valuelen) {
		r = rtsp_header_set_value(h, value, valuelen, true);
		if (r < 0) {
			free(h->key);
			return -ENOMEM;
		}
	}

	if (m->iter_body) {
		++m->body_used;
	} else {
		if (!strcasecmp(h->key, "Content-Length"))
			m->header_clen = h;
		if (!strcasecmp(h->key, "Content-Type"))
			m->header_ctype = h;
		else if (!strcasecmp(h->key, "CSeq"))
			m->header_cseq = h;

		++m->header_used;
	}

	*out = h;
	return 0;
}

static int rtsp_message_append_header_line(struct rtsp_message *m,
					   struct rtsp_header **out,
					   const char *line)
{
	struct rtsp_header *h;
	const char *value;
	char *t;
	size_t keylen, valuelen;
	int r;

	if (!line)
		return -EINVAL;

	t = (char *)malloc(strlen(line) + 3);
	if (!t)
		return -ENOMEM;



   // ***
	// value = strchrnul(line, ':');
	value = loc_strchrnul((char *)line, ':');



	keylen = value - line;
	if (*value) {
		++value;
		valuelen = strlen(value);
	} else {
		value = NULL;
		valuelen = 0;
	}

	while (keylen > 0 && line[keylen - 1] == ' ')
		--keylen;

	while (valuelen > 0 && value[valuelen - 1] == ' ')
		--valuelen;

	while (valuelen > 0 && value[0] == ' ') {
		++value;
		--valuelen;
	}

	r = rtsp_message_append_header(m,&h,line,keylen,value,valuelen);
	if (r < 0) {
		free(t);
		return r;
	}

	h->line = t;



	// ***
	// t = stpcpy(t, line);
	t = loc_stpcpy(t, (char *)line);



	*t++ = '\r';
	*t++ = '\n';
	*t = '\0';
	h->line_len = t - h->line;

	if (out)
		*out = h;

	return 0;
}

static int rtsp_header_append_token(struct rtsp_header *h, const char *token)
{
	if (!h || !token || h->line || h->value)
		return -EINVAL;

	if (!SHL_GREEDY_REALLOC0_T(h->tokens,h->token_cnt,h->token_used + 2))
		return -ENOMEM;

	h->tokens[h->token_used] = strdup(token);
	if (!h->tokens[h->token_used])
		return -ENOMEM;

	++h->token_used;
	return 0;
}

static int rtsp_header_serialize(struct rtsp_header *h)
{
	static char *empty_strv[1] = { NULL };
	char *t;
	int r;

	if (!h)
		return -EINVAL;
	if (h->line)
		return 0;

	if (!h->value) {
		r = shl_qstr_join(h->tokens ? : empty_strv, &h->value);
		if (r < 0)
			return r;
	}

	t = (char *)malloc(strlen(h->key) + strlen(h->value) + 5);
	if (!t)
		return -ENOMEM;

	h->line = t;



	// ***
	// t = stpcpy(t, h->key);
	t = loc_stpcpy(t, h->key);



	*t++ = ':';
	*t++ = ' ';



	// ***
	// t = stpcpy(t, h->value);
	t = loc_stpcpy(t, h->value);



	*t++ = '\r';
	*t++ = '\n';
	*t = '\0';
	h->line_len = t - h->line;

	return 0;
}

int rtsp_message_append_line(struct rtsp_message *m, const char *line)
{
	int r;

	if (!m || !line || m->type == RTSP_MESSAGE_DATA)
		return -EINVAL;
	if (m->is_sealed)
		return -EBUSY;
	if (m->iter_header)
		return -EINVAL;

	r = rtsp_message_append_header_line(m, NULL, line);
	if (r < 0)
		return r;

	return 0;
}

int rtsp_message_open_header(struct rtsp_message *m, const char *name)
{
	struct rtsp_header *h;
	int r;

	if (!m || shl_isempty(name) || m->type == RTSP_MESSAGE_DATA)
		return -EINVAL;
	if (m->is_sealed)
		return -EBUSY;
	if (m->iter_header)
		return -EINVAL;

	r = rtsp_message_append_header(m, &h, name, strlen(name), NULL, 0);
	if (r < 0)
		return r;

	m->iter_header = h;

	return 0;
}

int rtsp_message_close_header(struct rtsp_message *m)
{
	int r;

	if (!m || m->type == RTSP_MESSAGE_DATA)
		return -EINVAL;
	if (m->is_sealed)
		return -EBUSY;
	if (!m->iter_header)
		return -EINVAL;

	r = rtsp_header_serialize(m->iter_header);
	if (r < 0)
		return r;

	m->iter_header = NULL;

	return 0;
}

int rtsp_message_open_body(struct rtsp_message *m)
{
	if (!m || m->type == RTSP_MESSAGE_DATA)
		return -EINVAL;
	if (m->is_sealed)
		return -EBUSY;
	if (m->iter_header || m->iter_body)
		return -EINVAL;

	m->iter_body = true;

	return 0;
}

int rtsp_message_close_body(struct rtsp_message *m)
{
	if (!m || m->type == RTSP_MESSAGE_DATA)
		return -EINVAL;
	if (m->is_sealed)
		return -EBUSY;
	if (!m->iter_body)
		return -EINVAL;
	if (m->iter_header)
		return -EINVAL;

	m->iter_body = false;

	return 0;
}

int rtsp_message_append_basic(struct rtsp_message *m,
			      char type,
			      ...)
{
	va_list args;
	int r;

	va_start(args, type);
	r = rtsp_message_appendv_basic(m, type, &args);
	va_end(args);

	return r;
}

int rtsp_message_appendv_basic(struct rtsp_message *m,
			       char type,
			       va_list *args)
{
	char buf[128] = { };
	const char *orig;
	uint32_t u32;
	int32_t i32;

	if (!m || m->type == RTSP_MESSAGE_DATA)
		return -EINVAL;
	if (m->is_sealed)
		return -EBUSY;

	switch (type) {
	case RTSP_TYPE_RAW:
		orig = va_arg(*args, const char*);
		if (!orig)
			orig = "";

		if (m->iter_header)
			return rtsp_header_set_value(m->iter_header,orig,strlen(orig),false);
		else
			return rtsp_message_append_line(m, orig);
	case RTSP_TYPE_HEADER_START:
		orig = va_arg(*args, const char*);

		return rtsp_message_open_header(m, orig);
	case RTSP_TYPE_HEADER_END:
		return rtsp_message_close_header(m);
	case RTSP_TYPE_BODY_START:
		return rtsp_message_open_body(m);
	case RTSP_TYPE_BODY_END:
		return rtsp_message_close_body(m);
	}

	if (!m->iter_header)
		return -EINVAL;

	switch (type) {
	case RTSP_TYPE_STRING:
		orig = va_arg(*args, const char*);
		if (!orig)
			orig = "";

		break;
	case RTSP_TYPE_INT32:
		i32 = va_arg(*args, int32_t);
		sprintf(buf, "%" PRId32, i32);
		orig = buf;
		break;
	case RTSP_TYPE_UINT32:
		u32 = va_arg(*args, uint32_t);
		sprintf(buf, "%" PRIu32, u32);
		orig = buf;
		break;
	default:
		return -EINVAL;
	}

	return rtsp_header_append_token(m->iter_header, orig);
}

int rtsp_message_append(struct rtsp_message *m,
			const char *types,
			...)
{
	va_list args;
	int r;

	va_start(args, types);
	r = rtsp_message_appendv(m, types, &args);
	va_end(args);

	return r;
}

int rtsp_message_appendv(struct rtsp_message *m,
			 const char *types,
			 va_list *args)
{
	int r;

	if (!m || m->type == RTSP_MESSAGE_DATA)
		return -EINVAL;
	if (m->is_sealed)
		return -EBUSY;
	if (!types)
		return 0;

	for ( ; *types; ++types) {
		r = rtsp_message_appendv_basic(m, *types, args);
		if (r < 0)
			return r;
	}

	return 0;
}

static int rtsp_message_serialize_common(struct rtsp_message *m)
{
	_shl_free_ char *head = NULL, *headers = NULL, *body = NULL;
	char buf[128];
	char *raw, *p, *cbody;
	size_t rawlen, i, l, body_size;
	int r;

	switch (m->type) {
	case RTSP_MESSAGE_UNKNOWN:
		head = shl_strcat(m->unknown_head, "\r\n");
		if (!head)
			return -ENOMEM;

		break;
	case RTSP_MESSAGE_REQUEST:
		r = asprintf(&head, "%s %s RTSP/%u.%u\r\n",
			     m->request_method,
			     m->request_uri,
			     m->major,
			     m->minor);
		if (r < 0)
			return -ENOMEM;

		break;
	case RTSP_MESSAGE_REPLY:
		r = asprintf(&head, "RTSP/%u.%u %u %s\r\n",
			     m->major,
			     m->minor,
			     m->reply_code,
			     m->reply_phrase);
		if (r < 0)
			return -ENOMEM;

		break;
	default:
		return -EINVAL;
	}

	rawlen = strlen(head);

	/* concat body */

	if (m->body) {
		body_size = m->body_size;
		cbody = (char *)m->body;
	} else {
		l = 0;
		for (i = 0; i < m->body_used; ++i)
			l += m->body_headers[i].line_len;

		body = (char *)malloc(l + 1);
		if (!body)
			return -ENOMEM;

		p = (char*)body;
		for (i = 0; i < m->body_used; ++i)
	      // ***
			// p = stpcpy(p, m->body_headers[i].line);
			p = loc_stpcpy(p, m->body_headers[i].line);


		*p = 0;

		body_size = p - body;
		cbody = body;
	}

	rawlen += body_size;

	/* set content-length header */

	if (m->header_clen) {
		sprintf(buf, "%zu", body_size);
		r = rtsp_header_set_value(m->header_clen,
					  buf,
					  strlen(buf),
					  true);
		if (r < 0)
			return r;

		r = rtsp_header_serialize(m->header_clen);
		if (r < 0)
			return r;
	} else if (body_size) {
		rtsp_message_close_header(m);
		rtsp_message_close_body(m);
		r = rtsp_message_append(m, "<u>",
					"Content-Length",
					(uint32_t)body_size);
		if (r < 0)
			return r;
	}

	/* set content-type header */

	if (m->body_used && m->header_ctype) {
		r = rtsp_header_set_value(m->header_ctype,
					  "text/parameters",
					  15,
					  true);
		if (r < 0)
			return r;

		r = rtsp_header_serialize(m->header_ctype);
		if (r < 0)
			return r;
	} else if (m->body_used) {
		rtsp_message_close_header(m);
		rtsp_message_close_body(m);
		r = rtsp_message_append(m, "<s>",
					"Content-Type",
					"text/parameters");
		if (r < 0)
			return r;
	}

	/* set cseq header */

	sprintf(buf, "%llu", m->cookie & ~RTSP_FLAG_REMOTE_COOKIE);
	if (m->header_cseq) {
		r = rtsp_header_set_value(m->header_cseq,
					  buf,
					  strlen(buf),
					  true);
		if (r < 0)
			return r;

		r = rtsp_header_serialize(m->header_cseq);
		if (r < 0)
			return r;
	} else {
		rtsp_message_close_header(m);
		rtsp_message_close_body(m);
		r = rtsp_message_append(m, "<s>",
					"CSeq",
					buf);
		if (r < 0)
			return r;
	}

	/* concat headers */

	l = 0;
	for (i = 0; i < m->header_used; ++i)
		l += m->headers[i].line_len;

	headers = (char *)malloc(l + 1);
	if (!headers)
		return -ENOMEM;

	p = headers;
	for (i = 0; i < m->header_used; ++i)
	   // ***
		// p = stpcpy(p, m->headers[i].line);
		p = loc_stpcpy(p, m->headers[i].line);

	*p = 0;
	rawlen += p - headers;

	/* final concat */

	rawlen += 2;
	raw = (char *)malloc(rawlen + 1);
	if (!raw)
		return -ENOMEM;

	p = raw;



   // ***
	// p = stpcpy(p, head);
	// p = stpcpy(p, headers);
	p = loc_stpcpy(p, head);
	p = loc_stpcpy(p, headers);



	*p++ = '\r';
	*p++ = '\n';
	memcpy(p, cbody, body_size);
	p += body_size;

	/* terminate - mainly for debugging */
	*p = 0;

	m->raw = (uint8_t *)raw;
	m->raw_size = rawlen;

	m->body = (uint8_t *)cbody;
	m->body_size = body_size;
	body = NULL;

	return 0;
}

static int rtsp_message_serialize_data(struct rtsp_message *m)
{
	uint8_t *raw;
	size_t rawlen;

	rawlen = 1 + 1 + 2 + m->data_size;
	raw = (uint8_t *)malloc(rawlen + 1);
	if (!raw)
		return -ENOMEM;

	raw[0] = '$';
	raw[1] = m->data_channel;
	raw[2] = (m->data_size & 0xff00U) >> 8;
	raw[3] = (m->data_size & 0x00ffU);
	if (m->data_size)
		memcpy(&raw[4], m->data_payload, m->data_size);

	/* for debugging */
	raw[rawlen] = 0;

	m->raw = raw;
	m->raw_size = rawlen;

	return 0;
}

int rtsp_message_set_cookie(struct rtsp_message *m, uint64_t cookie)
{
	if (!m)
		return -EINVAL;
	if (m->is_sealed)
		return -EBUSY;

	m->cookie = cookie & ~RTSP_FLAG_REMOTE_COOKIE;
	if (m->type == RTSP_MESSAGE_REPLY)
		m->cookie |= RTSP_FLAG_REMOTE_COOKIE;

	return 0;
}

int rtsp_message_seal(struct rtsp_message *m)
{
	int r;

	if (!m)
		return -EINVAL;
	if (m->is_sealed)
		return 0;
	if (m->iter_body || m->iter_header)
		return -EINVAL;

	if (!m->cookie)
		m->cookie = ++m->bus->cookies ? : ++m->bus->cookies;
	if (m->type == RTSP_MESSAGE_REPLY)
		m->cookie |= RTSP_FLAG_REMOTE_COOKIE;

	switch (m->type) {
	case RTSP_MESSAGE_UNKNOWN:
	case RTSP_MESSAGE_REQUEST:
	case RTSP_MESSAGE_REPLY:
		r = rtsp_message_serialize_common(m);
		if (r < 0)
			return r;

		break;
	case RTSP_MESSAGE_DATA:
		r = rtsp_message_serialize_data(m);
		if (r < 0)
			return r;

		break;
	}

	m->is_sealed = true;

	return 0;
}

int rtsp_message_enter_header(struct rtsp_message *m, const char *name)
{
	size_t i;

	if (!m || shl_isempty(name) || m->type == RTSP_MESSAGE_DATA)
		return -EINVAL;
	if (!m->is_sealed)
		return -EBUSY;
	if (m->iter_header)
		return -EINVAL;

	if (m->iter_body) {
		for (i = 0; i < m->body_used; ++i) {
			if (!strcasecmp(m->body_headers[i].key, name)) {
				m->iter_header = &m->body_headers[i];
				m->iter_token = 0;
				return 0;
			}
		}
	} else {
		for (i = 0; i < m->header_used; ++i) {
			if (!strcasecmp(m->headers[i].key, name)) {
				m->iter_header = &m->headers[i];
				m->iter_token = 0;
				return 0;
			}
		}
	}

	return -ENOENT;
}

void rtsp_message_exit_header(struct rtsp_message *m)
{
	if (!m || !m->is_sealed || m->type == RTSP_MESSAGE_DATA)
		return;
	if (!m->iter_header)
		return;

	m->iter_header = NULL;
}

int rtsp_message_enter_body(struct rtsp_message *m)
{
	if (!m || m->type == RTSP_MESSAGE_DATA)
		return -EINVAL;
	if (!m->is_sealed)
		return -EBUSY;
	if (m->iter_header)
		return -EINVAL;
	if (m->iter_body)
		return -EINVAL;

	m->iter_body = true;

	return 0;
}

void rtsp_message_exit_body(struct rtsp_message *m)
{
	if (!m || !m->is_sealed || m->type == RTSP_MESSAGE_DATA)
		return;
	if (!m->iter_body)
		return;

	m->iter_body = false;
	m->iter_header = NULL;
}

int rtsp_message_read_basic(struct rtsp_message *m,
			    char type,
			    ...)
{
	va_list args;
	int r;

	va_start(args, type);
	r = rtsp_message_readv_basic(m, type, &args);
	va_end(args);

	return r;
}

int rtsp_message_readv_basic(struct rtsp_message *m,
			     char type,
			     va_list *args)
{
	const char *key;
	const char **out_str, *entry;
	int32_t i32, *out_i32;
	uint32_t u32, *out_u32;

	if (!m || m->type == RTSP_MESSAGE_DATA)
		return -EINVAL;
	if (!m->is_sealed)
		return -EBUSY;

	switch (type) {
	case RTSP_TYPE_RAW:
		if (!m->iter_header)
			return -EINVAL;

		out_str = va_arg(*args, const char**);
		if (out_str)
			*out_str = m->iter_header->value ? : "";

		return 0;
	case RTSP_TYPE_HEADER_START:
		key = va_arg(*args, const char*);

		return rtsp_message_enter_header(m, key);
	case RTSP_TYPE_HEADER_END:
		rtsp_message_exit_header(m);
		return 0;
	case RTSP_TYPE_BODY_START:
		return rtsp_message_enter_body(m);
	case RTSP_TYPE_BODY_END:
		rtsp_message_exit_body(m);
		return 0;
	}

	if (!m->iter_header)
		return -EINVAL;
	if (m->iter_token >= m->iter_header->token_used)
		return -ENOENT;

	entry = m->iter_header->tokens[m->iter_token];

	switch (type) {
	case RTSP_TYPE_STRING:
		out_str = va_arg(*args, const char**);
		if (out_str)
			*out_str = entry;

		break;
	case RTSP_TYPE_INT32:
		if (sscanf(entry, "%" SCNd32, &i32) != 1)
			return -EINVAL;

		out_i32 = va_arg(*args, int32_t*);
		if (out_i32)
			*out_i32 = i32;

		break;
	case RTSP_TYPE_UINT32:
		if (sscanf(entry, "%" SCNu32, &u32) != 1)
			return -EINVAL;

		out_u32 = va_arg(*args, uint32_t*);
		if (out_u32)
			*out_u32 = u32;

		break;
	case RTSP_TYPE_HEX32:
		if (sscanf(entry, "%" SCNx32, &u32) != 1)
			return -EINVAL;

		out_u32 = va_arg(*args, uint32_t*);
		if (out_u32)
			*out_u32 = u32;

		break;
	case RTSP_TYPE_SKIP:
		/* just increment token */
		break;
	default:
		return -EINVAL;
	}

	++m->iter_token;

	return 0;
}

int rtsp_message_read(struct rtsp_message *m,
		      const char *types,
		      ...)
{
	va_list args;
	int r;

	va_start(args, types);
	r = rtsp_message_readv(m, types, &args);
	va_end(args);

	return r;
}

int rtsp_message_readv(struct rtsp_message *m,
		       const char *types,
		       va_list *args)
{
	int r;

	if (!m || m->type == RTSP_MESSAGE_DATA)
		return -EINVAL;
	if (!m->is_sealed)
		return -EBUSY;
	if (!types)
		return 0;

	for ( ; *types; ++types) {
		r = rtsp_message_readv_basic(m, *types, args);
		if (r < 0) {
			if (m->iter_body)
				rtsp_message_exit_body(m);
			if (m->iter_header)
				rtsp_message_exit_header(m);
			return r;
		}
	}

	return 0;
}

int rtsp_message_skip_basic(struct rtsp_message *m, char type)
{
	return rtsp_message_read_basic(m, type, NULL, NULL, NULL, NULL);
}

int rtsp_message_skip(struct rtsp_message *m, const char *types)
{
	int r;

	if (!m || m->type == RTSP_MESSAGE_DATA)
		return -EINVAL;
	if (!m->is_sealed)
		return -EBUSY;
	if (!types)
		return 0;

	for ( ; *types; ++types) {
		r = rtsp_message_skip_basic(m, *types);
		if (r < 0)
			return r;
	}

	return 0;
}

int rtsp_message_rewind(struct rtsp_message *m, bool complete)
{
	if (!m || m->type == RTSP_MESSAGE_DATA)
		return -EINVAL;
	if (!m->is_sealed)
		return -EBUSY;

	m->iter_token = 0;
	if (complete) {
		m->iter_body = false;
		m->iter_header = NULL;
	}

	return 0;
}

void *rtsp_message_get_body(struct rtsp_message *m)
{
	if (!m || m->type == RTSP_MESSAGE_DATA)
		return NULL;
	if (!m->is_sealed)
		return NULL;

	return m->body;
}

size_t rtsp_message_get_body_size(struct rtsp_message *m)
{
	if (!m || m->type == RTSP_MESSAGE_DATA)
		return 0;
	if (!m->is_sealed)
		return 0;

	return m->body_size;
}

void *rtsp_message_get_raw(struct rtsp_message *m)
{
	if (!m)
		return NULL;
	if (!m->is_sealed)
		return NULL;

	return m->raw;
}

size_t rtsp_message_get_raw_size(struct rtsp_message *m)
{
	if (!m)
		return 0;
	if (!m->is_sealed)
		return 0;

	return m->raw_size;
}

/*
 * Message Assembly
 * These helpers take the raw RTSP input strings, parse them line by line to
 * assemble an rtsp_message object.
 */

static int rtsp_message_from_request(struct rtsp *bus,
				     struct rtsp_message **out,
				     const char *line)
{
	struct rtsp_message *m;
	unsigned int major, minor;
	size_t cmdlen, urllen;
	const char *next, *prev, *cmd, *url;
	int r;

	if (!bus || !line)
		return -EINVAL;

	/*
	 * Requests look like this:
	 *   <cmd> <url> RTSP/<major>.<minor>
	 */

	next = line;

	/* parse <cmd> */
	cmd = line;
	next = strchr(next, ' ');
	if (!next || next == cmd)
		goto error;
	cmdlen = next - cmd;

	/* skip " " */
	++next;

	/* parse <url> */
	url = next;
	next = strchr(next, ' ');
	if (!next || next == url)
		goto error;
	urllen = next - url;

	/* skip " " */
	++next;

	/* parse "RTSP/" */
	if (strncasecmp(next, "RTSP/", 5))
		goto error;
	next += 5;

	/* parse "%u" */
	prev = next;
	shl_atoi_u(prev, 10, (const char**)&next, &major);
	if (next == prev || *next != '.')
		goto error;

	/* skip "." */
	++next;

	/* parse "%u" */
	prev = next;
	shl_atoi_u(prev, 10, (const char**)&next, &minor);
	if (next == prev || *next)
		goto error;

	r = rtsp_message_new_request_n(bus, &m, cmd, cmdlen, url, urllen);
	if (r < 0)
		return r;

	m->major = major;
	m->minor = minor;

	*out = m;
	return 0;

error:
	/*
	 * Invalid request line.. Set type to UNKNOWN and let the caller deal
	 * with it. We will not try to send any error to avoid triggering
	 * another error if the remote side doesn't understand proper RTSP (or
	 * if our implementation is buggy).
	 */

	return rtsp_message_new_unknown(bus, out, line);
}

static int rtsp_message_from_reply(struct rtsp *bus,
				   struct rtsp_message **out,
				   const char *line)
{
	struct rtsp_message *m;
	unsigned int major, minor, code;
	const char *prev, *next, *str;
	int r;

	if (!bus || !out || !line)
		return -EINVAL;

	/*
	 * Responses look like this:
	 *   RTSP/<major>.<minor> <code> <string..>
	 *   RTSP/%u.%u %u %s
	 * We first parse the RTSP version and code. Everything appended to
	 * this is optional and represents the error string.
	 */

	/* parse "RTSP/" */
	if (strncasecmp(line, "RTSP/", 5))
		goto error;
	next = &line[5];

	/* parse "%u" */
	prev = next;
	shl_atoi_u(prev, 10, (const char**)&next, &major);
	if (next == prev || *next != '.')
		goto error;

	/* skip "." */
	++next;

	/* parse "%u" */
	prev = next;
	shl_atoi_u(prev, 10, (const char**)&next, &minor);
	if (next == prev || *next != ' ')
		goto error;

	/* skip " " */
	++next;

	/* parse: %u */
	prev = next;
	shl_atoi_u(prev, 10, (const char**)&next, &code);
	if (next == prev)
		goto error;
	if (*next && *next != ' ')
		goto error;

	/* skip " " */
	if (*next)
		++next;

	/* parse: %s */
	str = next;

	r = rtsp_message_new_raw_reply(bus, &m, code, str);
	if (r < 0)
		return r;

	m->major = major;
	m->minor = minor;

	*out = m;
	return 0;

error:
	/*
	 * Couldn't parse line. Avoid sending an error message as we could
	 * trigger another error and end up in an endless error loop. Instead,
	 * set message type to UNKNOWN and let the caller deal with it.
	 */

	return rtsp_message_new_unknown(bus, out, line);
}

static int rtsp_message_from_head(struct rtsp *bus,
				  struct rtsp_message **out,
				  const char *line)
{
	if (!bus || !out || !line)
		return -EINVAL;

	if (!strncasecmp(line, "RTSP/", 5))
		return rtsp_message_from_reply(bus, out, line);
	else
		return rtsp_message_from_request(bus, out, line);
}

static size_t rtsp__strncspn(const char *str,
			     size_t len,
			     const char *reject)
{
	size_t i, j;

	for (i = 0; i < len; ++i)
		for (j = 0; reject[j]; ++j)
			if (str[i] == reject[j])
				return i;

	return i;
}

static int rtsp_message_append_body(struct rtsp_message *m,
				    const void *body,
				    size_t len)
{
	_shl_free_ char *line = NULL;
	const char *d, *v;
	void *t;
	size_t dl, vl;
	int r;



   // ***
   printf("INFO: in rtsp_message_append_body()\n");



	if (!m)
		return -EINVAL;
	if (len > 0 && !body)
		return -EINVAL;

	/* if body is empty, nothing to do */
	if (!len)
		return 0;

	/* Usually, we should verify the content-length
	 * parameter here. However, that's not needed if the
	 * input is of fixed length, so we skip that. It's
	 * the caller's responsibility to do that. */

	/* if content-type is not text/parameters, append the binary blob */
	if (!m->header_ctype ||
	    !m->header_ctype->value ||
	    strcmp(m->header_ctype->value, "text/parameters")) {
		t = malloc(len + 1);
		if (!t)
			return -ENOMEM;

		free(m->body);
		m->body = (uint8_t *)t;
		memcpy(m->body, body, len);
		m->body_size = len;
		return 0;
	}

	r = rtsp_message_open_body(m);
	if (r < 0)
		return r;

	d = (const char *)body;
	while (len > 0) {
		dl = rtsp__strncspn(d, len, "\r\n");

		v = d;
		vl = dl;

		/* allow \r, \n, and \r\n as terminator */
		if (dl < len) {
			++dl;
			if (d[dl] == '\r') {
				if (dl < len && d[dl] == '\n')
					++dl;
			}
		}

		d += dl;
		len -= dl;

		/* ignore empty body lines */
		if (vl > 0) {
			free(line);
			line = (char *)malloc(vl + 1);
			if (!line)
				return -ENOMEM;

			memcpy(line, v, vl);
			line[vl] = 0;
			sanitize_line(line, vl);

			/* full header; append to message */
			r = rtsp_message_append_header_line(m, NULL, line);
			if (r < 0)
				return r;
		}
	}

	r = rtsp_message_close_body(m);
	if (r < 0)
		return r;

	return 0;
}

int rtsp_message_new_from_raw(struct rtsp *bus,
			      struct rtsp_message **out,
			      const void *data,
			      size_t len)
{
	_rtsp_message_unref_ struct rtsp_message *m = NULL;
	_shl_free_ char *line = NULL;
	const char *d, *v;
	size_t dl, vl;
	int r;

	if (!bus)
		return -EINVAL;
	if (len > 0 && !data)
		return -EINVAL;

	d = (const char *)data;
	while (len > 0) {
		dl = rtsp__strncspn(d, len, "\r\n");

		v = d;
		vl = dl;

		/* allow \r, \n, and \r\n as terminator */
		if (dl < len && d[dl++] == '\r')
			if (dl < len && d[dl] == '\n')
				++dl;

		d += dl;
		len -= dl;

		if (!vl) {
			/* empty line; start of body */

			if (!m) {
				r = rtsp_message_from_head(bus, &m, "");
				if (r < 0)
					return r;
			}

			r = rtsp_message_append_body(m, d, len);
			if (r < 0)
				return r;

			break;
		} else {
			free(line);
			line = (char *)malloc(vl + 1);
			if (!line)
				return -ENOMEM;

			memcpy(line, v, vl);
			line[vl] = 0;
			sanitize_line(line, vl);

			if (m) {
				/* full header; append to message */
				r = rtsp_message_append_header_line(m,
								    NULL,
								    line);
			} else {
				/* head line; create message */
				r = rtsp_message_from_head(bus, &m, line);
			}

			if (r < 0)
				return r;
		}
	}

	if (!m)
		return -EINVAL;

	r = rtsp_message_seal(m);
	if (r < 0)
		return r;

	*out = m;
	m = NULL;

	return 0;
}

/*
 * Parser State Machine
 * The parser state-machine is quite simple. We take an input buffer of
 * arbitrary length from the caller and feed it byte by byte into the state
 * machine.
 *
 * Parsing RTSP messages is rather troublesome due to the ASCII-nature. It's
 * easy to parse as is, but has lots of corner-cases which we want to be
 * compatible to maybe broken implementations. Thus, we need this
 * state-machine.
 *
 * All we do here is split the endless input stream into header-lines. The
 * header-lines are not handled by the state-machine itself but passed on. If a
 * message contains an entity payload, we parse the body. Otherwise, we submit
 * the message and continue parsing the next one.
 */

static int parser_append_header(struct rtsp *bus,
				char *line)
{
	struct rtsp::rtsp_parser *dec = &bus->parser;
	struct rtsp_header *h;
	size_t clen;
	const char *next;
	int r;

	r = rtsp_message_append_header_line(dec->m,
					    &h,
					    line);
	if (r < 0)
		return r;

	if (h == dec->m->header_clen) {
		/* Screwed content-length line? We cannot recover from that as
		 * the attached entity is of unknown length. Abort.. */
		if (h->token_used < 1)
			return -EINVAL;

		r = shl_atoi_z(h->tokens[0], 10, &next, &clen);
		if (r < 0 || *next)
			return -EINVAL;

		/* overwrite previous lengths */
		dec->remaining_body = clen;
	} else if (h == dec->m->header_cseq) {
		if (h->token_used >= 1) {
			r = shl_atoi_z(h->tokens[0], 10, &next, &clen);
			if (r >= 0 &&
			    !*next &&
			    !(clen & RTSP_FLAG_REMOTE_COOKIE)) {
				/* overwrite previous values */
				dec->m->cookie = clen | RTSP_FLAG_REMOTE_COOKIE;
			}
		}
	}

	return r;
}

static int parser_finish_header_line(struct rtsp *bus)
{
	struct rtsp::rtsp_parser *dec = &bus->parser;
	_shl_free_ char *line = NULL;
	int r;

	line = (char *)malloc(dec->buflen + 1);
	if (!line)
		return -ENOMEM;

	shl_ring_copy(&dec->buf, line, dec->buflen);
	line[dec->buflen] = 0;
	sanitize_line(line, dec->buflen);



   // ***
	if (!dec->m)
   {
      printf("INFO: in parser_finish_header_line(), invoking rtsp_message_from_head()\n");
		r = rtsp_message_from_head(bus, &dec->m, line);
   }
	else
   {
      printf("INFO: in parser_finish_header_line(), invoking parser_append_header()\n");
		r = parser_append_header(bus, line);
   }
   // ***



	return r;
}

static int parser_submit(struct rtsp *bus)
{
	_rtsp_message_unref_ struct rtsp_message *m = NULL;
	struct rtsp::rtsp_parser *dec = &bus->parser;
	int r;



   // ***
   printf("INFO: in parser_submit()\n");



	if (!dec->m)
		return 0;

	m = dec->m;
	dec->m = NULL;

	r = rtsp_message_seal(m);
	if (r < 0)
		return r;

	m->is_used = true;

	return rtsp_incoming_message(m);
}

static int parser_submit_data(struct rtsp *bus, uint8_t *p)
{
	_rtsp_message_unref_ struct rtsp_message *m = NULL;
	struct rtsp::rtsp_parser *dec = &bus->parser;
	int r;



   // ***
   printf("INFO: in parser_submit_data()\n");



	r = rtsp_message_new_data(bus,&m,dec->data_channel,p,dec->data_size);
	if (r < 0) {
		free(p);
		return r;
	}

	r = rtsp_message_seal(m);
	if (r < 0)
		return r;

	m->is_used = true;

	return rtsp_incoming_message(m);
}

static int parser_feed_char_new(struct rtsp *bus, char ch)
{
	struct rtsp::rtsp_parser *dec = &bus->parser;

	switch (ch) {
	case '\r':
	case '\n':
	case '\t':
	case ' ':
		/* If no msg has been started, yet, we ignore LWS for
		 * compatibility reasons. Note that they're actually not
		 * allowed, but should be ignored by implementations. */
		++dec->buflen;
		break;
	case '$':
		/* Interleaved data. Followed by 1 byte channel-id and 2-byte
		 * data-length. */
		dec->state = rtsp::rtsp_parser::STATE_DATA_HEAD;
		dec->data_channel = 0;
		dec->data_size = 0;

		/* clear any previous whitespace and leading '$' */
		shl_ring_pull(&dec->buf, dec->buflen + 1);
		dec->buflen = 0;
		break;
	default:
		/* Clear any pending data in the ring-buffer and then just
		 * push the char into the buffer. Any char except LWS is fine
		 * here. */
		dec->state = rtsp::rtsp_parser::STATE_HEADER;
		dec->remaining_body = 0;

		shl_ring_pull(&dec->buf, dec->buflen);
		dec->buflen = 1;
		break;
	}

	return 0;
}

static int parser_feed_char_header(struct rtsp *bus, char ch)
{
	struct rtsp::rtsp_parser *dec = &bus->parser;
	int r;

	switch (ch) {
	case '\r':
		if (dec->last_chr == '\r' || dec->last_chr == '\n') {
			/* \r\r means empty new-line. We actually allow \r\r\n,
			 * too. \n\r means empty new-line, too, but might also
			 * be finished off as \n\r\n so go to STATE_HEADER_NL
			 * to optionally complete the new-line.
			 * However, if the body is empty, we need to finish the
			 * msg early as there might be no \n coming.. */
			dec->state = rtsp::rtsp_parser::STATE_HEADER_NL;

			/* First finish the last header line if any. Don't
			 * include the current \r as it is already part of the
			 * empty following line. */
			r = parser_finish_header_line(bus);
			if (r < 0)
				return r;

			/* discard buffer *and* whitespace */
			shl_ring_pull(&dec->buf, dec->buflen + 1);
			dec->buflen = 0;

			/* No remaining body. Finish message! */
			if (!dec->remaining_body) {
				r = parser_submit(bus);
				if (r < 0)
					return r;
			}
		} else {
			/* '\r' following any character just means newline
			 * (optionally followed by \n). We don't do anything as
			 * it might be a continuation line. */
			++dec->buflen;
		}
		break;
	case '\n':
		if (dec->last_chr == '\n') {
			/* We got \n\n, which means we need to finish the
			 * current header-line. If there's no remaining body,
			 * we immediately finish the message and go to
			 * STATE_NEW. Otherwise, we go to STATE_BODY
			 * straight. */

			/* don't include second \n in header-line */
			r = parser_finish_header_line(bus);
			if (r < 0)
				return r;

			/* discard buffer *and* whitespace */
			shl_ring_pull(&dec->buf, dec->buflen + 1);
			dec->buflen = 0;

			if (dec->remaining_body) {
				dec->state = rtsp::rtsp_parser::STATE_BODY;
			} else {
				dec->state = rtsp::rtsp_parser::STATE_NEW;
				r = parser_submit(bus);
				if (r < 0)
					return r;
			}
		} else if (dec->last_chr == '\r') {
			/* We got an \r\n. We cannot finish the header line as
			 * it might be a continuation line. Next character
			 * decides what to do. Don't do anything here.
			 * \r\n\r cannot happen here as it is handled by
			 * STATE_HEADER_NL. */
			++dec->buflen;
		} else {
			/* Same as above, we cannot finish the line as it
			 * might be a continuation line. Do nothing. */
			++dec->buflen;
		}
		break;
	case '\t':
	case ' ':
		/* Whitespace. Simply push into buffer and don't do anything.
		 * In case of a continuation line, nothing has to be done,
		 * either. */
		++dec->buflen;
		break;
	default:
		if (dec->last_chr == '\r' || dec->last_chr == '\n') {
			/* Last line is complete and this is no whitespace,
			 * thus it's not a continuation line.
			 * Finish the line. */

			/* don't include new char in line */
			r = parser_finish_header_line(bus);
			if (r < 0)
				return r;
			shl_ring_pull(&dec->buf, dec->buflen);
			dec->buflen = 0;
		}

		/* consume character and handle special chars */
		++dec->buflen;
		if (ch == '"') {
			/* go to STATE_HEADER_QUOTE */
			dec->state = rtsp::rtsp_parser::STATE_HEADER_QUOTE;
			dec->quoted = false;
		}

		break;
	}

	return 0;
}

static int parser_feed_char_header_quote(struct rtsp *bus, char ch)
{
	struct rtsp::rtsp_parser *dec = &bus->parser;

	if (dec->last_chr == '\\' && !dec->quoted) {
		/* This character is quoted, so copy it unparsed. To handle
		 * double-backslash, we set the "quoted" bit. */
		++dec->buflen;
		dec->quoted = true;
	} else {
		dec->quoted = false;

		/* consume character and handle special chars */
		++dec->buflen;
		if (ch == '"')
			dec->state = rtsp::rtsp_parser::STATE_HEADER;
	}

	return 0;
}

static int parser_feed_char_body(struct rtsp *bus, char ch)
{
	struct rtsp::rtsp_parser *dec = &bus->parser;
	char *line;
	int r;

	/* If remaining_body was already 0, the message had no body. Note that
	 * messages without body are finished early, so no need to call
	 * decoder_submit() here. Simply forward @ch to STATE_NEW.
	 * @rlen is usually 0. We don't care and forward it, too. */
	if (!dec->remaining_body) {
		dec->state = rtsp::rtsp_parser::STATE_NEW;
		return parser_feed_char_new(bus, ch);
	}

	/* *any* character is allowed as body */
	++dec->buflen;

	if (!--dec->remaining_body) {
		/* full body received, copy it and go to STATE_NEW */

		if (dec->m) {
			line = (char *)malloc(dec->buflen + 1);
			if (!line)
				return -ENOMEM;

			shl_ring_copy(&dec->buf, line, dec->buflen);
			line[dec->buflen] = 0;

			r = rtsp_message_append_body(dec->m,line,dec->buflen);
			if (r >= 0)
				r = parser_submit(bus);

			free(line);
		} else {
			r = 0;
		}

		dec->state = rtsp::rtsp_parser::STATE_NEW;
		shl_ring_pull(&dec->buf, dec->buflen);
		dec->buflen = 0;

		if (r < 0)
			return r;
	}

	return 0;
}

static int parser_feed_char_header_nl(struct rtsp *bus, char ch)
{
	struct rtsp::rtsp_parser *dec = &bus->parser;

	/* STATE_HEADER_NL means we received an empty line ending with \r. The
	 * standard requires a following \n but advises implementations to
	 * accept \r on itself, too.
	 * What we do is to parse a \n as end-of-header and any character as
	 * end-of-header plus start-of-body. Note that we discard anything in
	 * the ring-buffer that has already been parsed (which normally can
	 * nothing, but lets be safe). */

	if (ch == '\n') {
		/* discard transition chars plus new \n */
		shl_ring_pull(&dec->buf, dec->buflen + 1);
		dec->buflen = 0;

		dec->state = rtsp::rtsp_parser::STATE_BODY;
		if (!dec->remaining_body)
			dec->state = rtsp::rtsp_parser::STATE_NEW;

		return 0;
	} else {
		/* discard any transition chars and push @ch into body */
		shl_ring_pull(&dec->buf, dec->buflen);
		dec->buflen = 0;

		dec->state = rtsp::rtsp_parser::STATE_BODY;
		return parser_feed_char_body(bus, ch);
	}
}

static int parser_feed_char_data_head(struct rtsp *bus, char ch)
{
	struct rtsp::rtsp_parser *dec = &bus->parser;
	uint8_t buf[3];

	/* Read 1 byte channel-id and 2 byte body length. */

	if (++dec->buflen >= 3) {
		shl_ring_copy(&dec->buf, buf, 3);
		shl_ring_pull(&dec->buf, dec->buflen);
		dec->buflen = 0;

		dec->data_channel = buf[0];
		dec->data_size = (((uint16_t)buf[1]) << 8) | (uint16_t)buf[2];
		dec->state = rtsp::rtsp_parser::STATE_DATA_BODY;
	}

	return 0;
}

static int parser_feed_char_data_body(struct rtsp *bus, char ch)
{
	struct rtsp::rtsp_parser *dec = &bus->parser;
	uint8_t *buf;
	int r;

	/* Read @dec->data_size bytes of raw data. */

	if (++dec->buflen >= dec->data_size) {
		buf = (uint8_t *)malloc(dec->data_size + 1);
		if (!buf)
			return -ENOMEM;

		/* Not really needed, but in case it's actually a text-payload
		 * make sure it's 0-terminated to work around client bugs. */
		buf[dec->data_size] = 0;

		shl_ring_copy(&dec->buf, buf, dec->data_size);

		r = parser_submit_data(bus, buf);
		free(buf);

		dec->state = rtsp::rtsp_parser::STATE_NEW;
		shl_ring_pull(&dec->buf, dec->buflen);
		dec->buflen = 0;

		if (r < 0)
			return r;
	}

	return 0;
}

static int parser_feed_char(struct rtsp *bus, char ch)
{
	struct rtsp::rtsp_parser *dec = &bus->parser;
	int r = 0;

	switch (dec->state) {
	case rtsp::rtsp_parser::STATE_NEW:
		r = parser_feed_char_new(bus, ch);
      // *** printf("INFO: parser_feed_char_new() returned %d from processing character %c\n",r,ch); */
		break;
	case rtsp::rtsp_parser::STATE_HEADER:
		r = parser_feed_char_header(bus, ch);
      // *** printf("INFO: parser_feed_char_header() returned %d from processing character %c\n",r,ch); */
		break;
	case rtsp::rtsp_parser::STATE_HEADER_QUOTE:
		r = parser_feed_char_header_quote(bus, ch);
      // *** printf("INFO: parser_feed_char_header_quote() returned %d from processing character %c\n",r,ch); */
		break;
	case rtsp::rtsp_parser::STATE_HEADER_NL:
		r = parser_feed_char_header_nl(bus, ch);
      // *** printf("INFO: parser_feed_char_header_nl() returned %d from processing character %c\n",r,ch); */
		break;
	case rtsp::rtsp_parser::STATE_BODY:
		r = parser_feed_char_body(bus, ch);
      // *** printf("INFO: parser_feed_char_body() returned %d from processing character %c\n",r,ch); */
		break;
	case rtsp::rtsp_parser::STATE_DATA_HEAD:
		r = parser_feed_char_data_head(bus, ch);
      // *** printf("INFO: parser_feed_char_data_head() returned %d from processing character %c\n",r,ch); */
		break;
	case rtsp::rtsp_parser::STATE_DATA_BODY:
		r = parser_feed_char_data_body(bus, ch);
      // *** printf("INFO: parser_feed_char_data_body() returned %d from processing character %c\n",r,ch); */
		break;
	}

	return r;
}

static int rtsp_parse_data(struct rtsp *bus,
			   const char *buf,
			   size_t len)
{
	struct rtsp::rtsp_parser *dec = &bus->parser;
	size_t i;
	int r;

	if (!len)
		return -EAGAIN;

	/*
	 * We keep dec->buflen as cache for the current parsed-buffer size. We
	 * need to push the whole input-buffer into our parser-buffer and go
	 * through it one-by-one. The parser increments dec->buflen for each of
	 * these and once we're done, we verify our state is consistent.
	 */

	dec->buflen = shl_ring_get_size(&dec->buf);
	r = shl_ring_push(&dec->buf, buf, len);
	if (r < 0)
		return r;

	for (i = 0; i < len; ++i) {
		r = parser_feed_char(bus, buf[i]);
		if (r < 0)
			return r;

		dec->last_chr = buf[i];
	}

	/* check for internal parser inconsistencies; should not happen! */
	if (dec->buflen != shl_ring_get_size(&dec->buf))
		return -EFAULT;

	return 0;
}

/*
 * Bus Management
 * The bus layer is responsible of sending and receiving messages. It hooks
 * into any sd-event loop and properly serializes rtsp_message objects to the
 * given file-descriptor and vice-versa.
 *
 * On any I/O error, the bus layer tries to drain the input-queue and then pass
 * the HUP to the user. This way, we try to get all messages that the remote
 * side sent to us, before we give up and close the stream.
 *
 * Note that this layer is independent of the underlying transport. However, we
 * require the transport to be stream-based. Packet-based transports are not
 * supported and will fail silently.
 */

// *** static int rtsp_call_message(struct rtsp_message *m,
// *** 			     struct rtsp_message *reply)
// *** {
// *** 	int r;
// *** 
// *** 	/* protect users by making sure arguments stay around */
// *** 	rtsp_message_ref(m);
// *** 	rtsp_message_ref(reply);
// *** 
// *** 	if (m->cb_fn)
// *** 		r = m->cb_fn(m->bus, reply, m->fn_data);
// *** 	else
// *** 		r = 0;
// *** 
// *** 	rtsp_message_unref(reply);
// *** 	rtsp_message_unref(m);
// *** 
// *** 	return r;
// *** }

// *** static int rtsp_call_reply(struct rtsp *bus, struct rtsp_message *reply)
// *** {
// *** 	struct rtsp_message *m;
// *** 	uint64_t *elem;
// *** 	int r;
// *** 
// *** 	if (!shl_htable_lookup_u64(&bus->waiting,
// *** 				   reply->cookie & ~RTSP_FLAG_REMOTE_COOKIE,
// *** 				   &elem))
// *** 		return 0;
// *** 
// *** 	m = rtsp_message_from_htable(elem);
// *** 	rtsp_message_ref(m);
// *** 
// *** 	rtsp_drop_message(m);
// *** 	r = rtsp_call_message(m, reply);
// *** 
// *** 	rtsp_message_unref(m);
// *** 	return r;
// *** }

// *** static int rtsp_call(struct rtsp *bus, struct rtsp_message *m)
// *** {
// *** 	struct rtsp_match *match;
// *** 	struct shl_dlist *i, *t;
// *** 	int r;
// *** 
// *** 	/* make sure bus and message stay around during any callbacks */
// *** 	rtsp_ref(bus);
// *** 	rtsp_message_ref(m);
// *** 
// *** 	r = 0;
// *** 
// *** 	bus->is_calling = true;
// *** 	shl_dlist_for_each(i, &bus->matches) {
// *** 		match = shl_dlist_entry(i, struct rtsp_match, list);
// *** 		r = match->cb_fn(bus, m, match->data);
// *** 		if (r != 0)
// *** 			break;
// *** 	}
// *** 	bus->is_calling = false;
// *** 
// *** 	shl_dlist_for_each_safe(i, t, &bus->matches) {
// *** 		match = shl_dlist_entry(i, struct rtsp_match, list);
// *** 		if (match->is_removed)
// *** 			rtsp_free_match(match);
// *** 	}
// *** 
// *** 	rtsp_message_unref(m);
// *** 	rtsp_unref(bus);
// *** 
// *** 	return r;
// *** }

// *** static int rtsp_hup(struct rtsp *bus)
// *** {
// *** 	if (bus->is_dead)
// *** 		return 0;
// *** 
// *** 	// *** rtsp_detach_event(bus);
// *** 	bus->is_dead = true;
// *** 	return rtsp_call(bus, NULL);
// *** }

// *** static int rtsp_timer_fn(sd_event_source *src, uint64_t usec, void *data)
// *** {
// *** 	struct rtsp_message *m = data;
// *** 	int r;
// *** 
// *** 	/* make sure message stays around during unlinking and callbacks */
// *** 	rtsp_message_ref(m);
// *** 
// *** 	sd_event_source_set_enabled(m->timer_source, SD_EVENT_OFF);
// *** 	rtsp_drop_message(m);
// *** 	r = rtsp_call_message(m, NULL);
// *** 
// *** 	rtsp_message_unref(m);
// *** 
// *** 	return r;
// *** }

// *** static int rtsp_link_waiting(struct rtsp_message *m)
// *** {
// *** 	int r;
// *** 
// *** 	r = shl_htable_insert_u64(&m->bus->waiting, &m->cookie);
// *** 	if (r < 0)
// *** 		return r;
// *** 
// *** 	/* no need to wait for timeout if no-body listens */
// *** 	if (m->bus->event && m->cb_fn) {
// *** 		r = sd_event_add_time(m->bus->event,
// *** 				      &m->timer_source,
// *** 				      CLOCK_MONOTONIC,
// *** 				      m->timeout,
// *** 				      0,
// *** 				      rtsp_timer_fn,
// *** 				      m);
// *** 		if (r < 0)
// *** 			goto error;
// *** 
// *** 		r = sd_event_source_set_priority(m->timer_source,
// *** 						 m->bus->priority);
// *** 		if (r < 0)
// *** 			goto error;
// *** 	}
// *** 
// *** 	m->is_waiting = true;
// *** 	++m->bus->waiting_cnt;
// *** 	rtsp_message_ref(m);
// *** 
// *** 	return 0;
// *** 
// *** error:
// *** 	sd_event_source_unref(m->timer_source);
// *** 	m->timer_source = NULL;
// *** 	shl_htable_remove_u64(&m->bus->waiting, m->cookie, NULL);
// *** 	return r;
// *** }

static void rtsp_unlink_waiting(struct rtsp_message *m)
{
	if (m->is_waiting) {

		// *** sd_event_source_unref(m->timer_source);
		// *** m->timer_source = NULL;

		shl_htable_remove_u64(&m->bus->waiting, m->cookie, NULL);
		m->is_waiting = false;
		--m->bus->waiting_cnt;
		rtsp_message_unref(m);
	}
}

// *** static void rtsp_link_outgoing(struct rtsp_message *m)
// *** {
// *** 	shl_dlist_link_tail(&m->bus->outgoing, &m->list);
// *** 	m->is_outgoing = true;
// *** 	++m->bus->outgoing_cnt;
// *** 	rtsp_message_ref(m);
// *** }

static void rtsp_unlink_outgoing(struct rtsp_message *m)
{
	if (m->is_outgoing) {
		shl_dlist_unlink(&m->list);
		m->is_outgoing = false;
		m->is_sending = false;
		--m->bus->outgoing_cnt;
		rtsp_message_unref(m);
	}
}

static int rtsp_incoming_message(struct rtsp_message *m)
{
	int r;
   struct rtsp * rtspSession;


   // ***
   rtspSession = m->bus;
   if(!rtspSession)
   {
      printf("ERROR: NULL rtspSession in rtsp_incoming_message()\n");
      return(-1);
   }


	switch (m->type) {
       case RTSP_MESSAGE_UNKNOWN:
       case RTSP_MESSAGE_REQUEST:
       case RTSP_MESSAGE_DATA: {
          /* simply forward all these to the match-handlers */



          // ***

          printf("INFO: in rtsp_incoming_message() - handling parsed request\n");

          char *request_method = (char *) rtsp_message_get_method(m);
          if (request_method)
             printf("INFO: request_method: %s\n", request_method);

          char *request_uri = (char *) rtsp_message_get_uri(m);
          if (request_uri)
             printf("INFO: request_uri: %s\n", request_uri);

          size_t header_used = m->header_used;
          printf("INFO: header_used: %d\n", (int) header_used);

          bool is_sealed = m->is_sealed;
          printf("INFO: is_sealed: %d\n", (int) is_sealed);

          // r = rtsp_call(m->bus, m);
          // if (r < 0)
          // 	return r;
          r = cresRTSP_internalCallback((void *) rtspSession, RTSP_MESSAGE_REQUEST, m, request_method,
                                        request_uri, NULL, -1);
          if (r < 0)
             return r;

          // ***



          break;
       }
       case RTSP_MESSAGE_REPLY: {
          /* find the waiting request and invoke the handler */



          // ***

          printf("INFO: in rtsp_incoming_message() - handling parsed response\n");

          char *reply_phrase = (char *)rtsp_message_get_phrase(m);
          if (reply_phrase)
             printf("INFO: reply_phrase: %s\n", reply_phrase);

          unsigned int reply_code = rtsp_message_get_code(m);
          printf("INFO: reply_code: %d\n", reply_code);

          // r = rtsp_call_reply(m->bus, m);
          // if (r < 0)
          // 	return r;
          r = cresRTSP_internalCallback((void *) rtspSession, RTSP_MESSAGE_REPLY, m, NULL, NULL,
                                        reply_phrase, reply_code);
          if (r < 0)
             return r;

          // ***



          break;
       }
	}

	return 0;
}

// *** static int rtsp_read(struct rtsp *bus)
// *** {
// *** 	char buf[4096];
// *** 	ssize_t res;
// *** 
// *** 	res = recv(bus->fd,
// *** 		   buf,
// *** 		   sizeof(buf),
// *** 		   MSG_DONTWAIT);
// *** 	if (res < 0) {
// *** 		if (errno == EAGAIN || errno == EINTR)
// *** 			return -EAGAIN;
// *** 
// *** 		return -errno;
// *** 	} else if (!res) {
// *** 		/* there're no 0-length packets on streams; this is EOF */
// *** 		return -EPIPE;
// *** 	} else if (res > sizeof(buf)) {
// *** 		res = sizeof(buf);
// *** 	}
// *** 
// *** 	/* parses all messages and calls rtsp_incoming_message() for each */
// *** 	return rtsp_parse_data(bus, buf, res);
// *** }

// *** static int rtsp_write_message(struct rtsp_message *m)
// *** {
// *** 	size_t remaining;
// *** 	ssize_t res;
// *** 
// *** 	m->is_sending = true;
// *** 	remaining = m->raw_size - m->sent;
// *** 	res = send(m->bus->fd,
// *** 		   &m->raw[m->sent],
// *** 		   remaining,
// *** 		   MSG_NOSIGNAL | MSG_DONTWAIT);
// *** 	if (res < 0) {
// *** 		if (errno == EAGAIN || errno == EINTR)
// *** 			return -EAGAIN;
// *** 
// *** 		return -errno;
// *** 	} else if (res > (ssize_t)remaining) {
// *** 		res = remaining;
// *** 	}
// *** 
// *** 	m->sent += res;
// *** 	if (m->sent >= m->raw_size) {
// *** 		/* no need to wait for answer if no-body listens */
// *** 		if (!m->cb_fn)
// *** 			rtsp_unlink_waiting(m);
// *** 
// *** 		/* might destroy the message */
// *** 		rtsp_unlink_outgoing(m);
// *** 	}
// *** 
// *** 	return 0;
// *** }

// *** static int rtsp_write(struct rtsp *bus)
// *** {
// *** 	struct rtsp_message *m;
// *** 
// *** 	if (shl_dlist_empty(&bus->outgoing))
// *** 		return 0;
// *** 
// *** 	m = shl_dlist_first_entry(&bus->outgoing, struct rtsp_message, list);
// *** 	return rtsp_write_message(m);
// *** }

// *** static int rtsp_io_fn(sd_event_source *src, int fd, uint32_t mask, void *data)
// *** {
// *** 	struct rtsp *bus = data;
// *** 	int r, write_r;
// *** 
// *** 	/* make sure bus stays around during any possible callbacks */
// *** 	rtsp_ref(bus);
// *** 
// *** 	/*
// *** 	 * Whenever we encounter I/O errors, we have to make sure to drain the
// *** 	 * input queue first, before we handle any HUP. A server might send us
// *** 	 * a message and immediately close the queue. We must not handle the
// *** 	 * HUP first or we loose data.
// *** 	 * Therefore, if we read a message successfully, we always return
// *** 	 * success and wait for the next event-loop iteration. Furthermore,
// *** 	 * whenever there is a write-error, we must try reading from the input
// *** 	 * queue even if EPOLLIN is not set. The input might have arrived in
// *** 	 * between epoll_wait() and send(). Therefore, write-errors are only
// *** 	 * ever handled if the input-queue is empty. In all other cases they
// *** 	 * are ignored until either reading fails or the input queue is empty.
// *** 	 */
// *** 
// *** 	if (mask & EPOLLOUT) {
// *** 		write_r = rtsp_write(bus);
// *** 		if (write_r == -EAGAIN)
// *** 			write_r = 0;
// *** 	} else {
// *** 		write_r = 0;
// *** 	}
// *** 
// *** 	if (mask & EPOLLIN || write_r < 0) {
// *** 		r = rtsp_read(bus);
// *** 		if (r < 0 && r != -EAGAIN)
// *** 			goto error;
// *** 		else if (r >= 0)
// *** 			goto out;
// *** 	}
// *** 
// *** 	if (!(mask & (EPOLLHUP | EPOLLERR)) && write_r >= 0) {
// *** 		r = 0;
// *** 		goto out;
// *** 	}
// *** 
// *** 	/* I/O error, forward HUP to match-handlers */
// *** 
// *** error:
// *** 	r = rtsp_hup(bus);
// *** out:
// *** 	rtsp_unref(bus);
// *** 	return r;
// *** }

// *** static int rtsp_io_prepare_fn(sd_event_source *src, void *data)
// *** {
// *** 	struct rtsp *bus = data;
// *** 	uint32_t mask;
// *** 	int r;
// *** 
// *** 	mask = EPOLLHUP | EPOLLERR | EPOLLIN;
// *** 	if (!shl_dlist_empty(&bus->outgoing))
// *** 		mask |= EPOLLOUT;
// *** 
// *** 	r = sd_event_source_set_io_events(bus->fd_source, mask);
// *** 	if (r < 0)
// *** 		return r;
// *** 
// *** 	return 0;
// *** }

int rtsp_open(struct rtsp **out, int fd)
{
	_rtsp_unref_ struct rtsp *bus = NULL;

	if (!out || fd < 0)
		return -EINVAL;

	bus = (struct rtsp *)calloc(1, sizeof(*bus));
	if (!bus)
		return -ENOMEM;

	bus->ref = 1;
	bus->fd = fd;
	shl_dlist_init(&bus->matches);
	shl_dlist_init(&bus->outgoing);
	shl_htable_init_u64(&bus->waiting);

	*out = bus;
	bus = NULL;
	return 0;
}

void rtsp_ref(struct rtsp *bus)
{
	if (!bus || !bus->ref)
		return;

	++bus->ref;
}

void rtsp_unref(struct rtsp *bus)
{
	struct rtsp_message *m;
	struct rtsp_match *match;
	struct shl_dlist *i;
	size_t refs;
	bool q;

	if (!bus || !bus->ref)
		return;

	/* If the reference count is equal to the number of messages we have
	 * in our internal queues plus the reference we're about to drop, then
	 * all remaining references are self-references. Therefore, going over
	 * all messages and in case they also have no external references, drop
	 * all queues so bus->ref drops to 1 and we can free it. */
	refs = bus->outgoing_cnt + bus->waiting_cnt + 1;
	if (bus->parser.m)
		++refs;

	if (bus->ref <= refs) {
		q = true;
		shl_dlist_for_each(i, &bus->outgoing) {
			m = shl_dlist_entry(i, struct rtsp_message, list);
			if (m->ref > 1) {
				q = false;
				break;
			}
		}

		if (q) {
			RTSP_FOREACH_WAITING(m, bus) {
				if (m->ref > 1) {
					q = false;
					break;
				}
			}
		}

		if (q) {
			while (!shl_dlist_empty(&bus->outgoing)) {
				m = shl_dlist_first_entry(&bus->outgoing,
							  struct rtsp_message,
							  list);
				rtsp_unlink_outgoing(m);
			}

			while ((m = RTSP_FIRST_WAITING(bus)))
				rtsp_unlink_waiting(m);

			rtsp_message_unref(bus->parser.m);
			bus->parser.m = NULL;
		}
	}

	if (!bus->ref || --bus->ref)
		return;

	while (!shl_dlist_empty(&bus->matches)) {
		match = shl_dlist_first_entry(&bus->matches,
					      struct rtsp_match,
					      list);
		rtsp_free_match(match);
	}

	// *** rtsp_detach_event(bus);
	shl_ring_clear(&bus->parser.buf);
	shl_htable_clear_u64(&bus->waiting, NULL, NULL);
	close(bus->fd);
	free(bus);
}

bool rtsp_is_dead(struct rtsp *bus)
{
	return !bus || bus->is_dead;
}

// *** int rtsp_attach_event(struct rtsp *bus, sd_event *event, int priority)
// *** {
// *** 	struct rtsp_message *m;
// *** 	int r;
// *** 
// *** 	if (!bus)
// *** 		return -EINVAL;
// *** 	if (bus->is_dead)
// *** 		return -EINVAL;
// *** 	if (bus->event)
// *** 		return -EALREADY;
// *** 
// *** 	if (event) {
// *** 		bus->event = event;
// *** 		sd_event_ref(event);
// *** 	} else {
// *** 		r = sd_event_default(&bus->event);
// *** 		if (r < 0)
// *** 			return r;
// *** 	}
// *** 
// *** 	bus->priority = priority;
// *** 
// *** 	r = sd_event_add_io(bus->event,
// *** 			    &bus->fd_source,
// *** 			    bus->fd,
// *** 			    EPOLLHUP | EPOLLERR | EPOLLIN,
// *** 			    rtsp_io_fn,
// *** 			    bus);
// *** 	if (r < 0)
// *** 		goto error;
// *** 
// *** 	r = sd_event_source_set_priority(bus->fd_source, priority);
// *** 	if (r < 0)
// *** 		goto error;
// *** 
// *** 	r = sd_event_source_set_prepare(bus->fd_source, rtsp_io_prepare_fn);
// *** 	if (r < 0)
// *** 		goto error;
// *** 
// *** 	RTSP_FOREACH_WAITING(m, bus) {
// *** 		/* no need to wait for timeout if no-body listens */
// *** 		if (!m->cb_fn)
// *** 			continue;
// *** 
// *** 		r = sd_event_add_time(bus->event,
// *** 				      &m->timer_source,
// *** 				      CLOCK_MONOTONIC,
// *** 				      m->timeout,
// *** 				      0,
// *** 				      rtsp_timer_fn,
// *** 				      m);
// *** 		if (r < 0)
// *** 			goto error;
// *** 
// *** 		r = sd_event_source_set_priority(m->timer_source, priority);
// *** 		if (r < 0)
// *** 			goto error;
// *** 	}
// *** 
// *** 	return 0;
// *** 
// *** error:
// *** 	rtsp_detach_event(bus);
// *** 	return r;
// *** }

// *** void rtsp_detach_event(struct rtsp *bus)
// *** {
// *** 	struct rtsp_message *m;
// *** 
// *** 	if (!bus || !bus->event)
// *** 		return;
// *** 
// *** 	RTSP_FOREACH_WAITING(m, bus) {
// *** 		sd_event_source_unref(m->timer_source);
// *** 		m->timer_source = NULL;
// *** 	}
// *** 
// *** 	sd_event_source_unref(bus->fd_source);
// *** 	bus->fd_source = NULL;
// *** 	sd_event_unref(bus->event);
// *** 	bus->event = NULL;
// *** }

/**
 * rtsp_add_match() - Add match-callback
 * @bus: rtsp bus to register callback on
 * @cb_fn: function to be used as callback
 * @data: user-context data that is passed through unchanged
 *
 * The given callback is called for each incoming request that was not matched
 * automatically to scheduled transactions. Note that you can register many
 * callbacks and they're called in the order they're registered. If a callback
 * handled a message, no further callbacks are called.
 *
 * You can register multiple callbacks with the _same_ @cb_fn and @data just
 * fine. However, once you unregister them, they're always unregistered in the
 * reverse order you registered them in.
 *
 * All match-callbacks are automatically removed when @bus is destroyed.
 *
 * Returns:
 * True on success, negative error code on failure.
 */
int rtsp_add_match(struct rtsp *bus, rtsp_callback_fn cb_fn, void *data)
{
	struct rtsp_match *match;

	if (!bus || !cb_fn)
		return -EINVAL;

	match = (struct rtsp_match *)calloc(1, sizeof(*match));
	if (!match)
		return -ENOMEM;

	match->cb_fn = cb_fn;
	match->data = data;

	shl_dlist_link_tail(&bus->matches, &match->list);

	return 0;
}

/**
 * rtsp_remove_match() - Remove match-callback
 * @bus: rtsp bus to unregister callback from
 * @cb_fn: callback function to unregister
 * @data: user-context data used during registration
 *
 * This reverts a previous call to rtsp_add_match(). If a given callback is not
 * found, nothing is done. Note that if you register a callback with the same
 * cb_fn+data combination multiple times, this only removes the last of them.
 *
 * All match-callbacks are automatically removed when @bus is destroyed.
 */
void rtsp_remove_match(struct rtsp *bus, rtsp_callback_fn cb_fn, void *data)
{
	struct rtsp_match *match;
	struct shl_dlist *i;

	if (!bus || !cb_fn)
		return;

	shl_dlist_for_each_reverse(i, &bus->matches) {
		match = shl_dlist_entry(i, struct rtsp_match, list);
		if (match->cb_fn == cb_fn && match->data == data) {
			if (bus->is_calling)
				match->is_removed = true;
			else
				rtsp_free_match(match);

			break;
		}
	}
}

static void rtsp_free_match(struct rtsp_match *match)
{
	if (!match)
		return;

	shl_dlist_unlink(&match->list);
	free(match);
}

int rtsp_send(struct rtsp *bus, struct rtsp_message *m)
{



	// *** return rtsp_call_async(bus, m, NULL, NULL, 0, NULL);

	return 0;
}

int rtsp_call_async(struct rtsp *bus,
		    struct rtsp_message *m,
		    rtsp_callback_fn cb_fn,
		    void *data,
		    uint64_t timeout,
		    uint64_t *cookie)
{



	// *** int r;
   // *** 
	// *** if (!bus || bus->is_dead || !m || !m->cookie)
	// *** 	return -EINVAL;
	// *** if (m->bus != bus || m->is_outgoing || m->is_waiting || m->is_used)
	// *** 	return -EINVAL;
   // *** 
	// *** r = rtsp_message_seal(m);
	// *** if (r < 0)
	// *** 	return r;
	// *** if (!m->raw)
	// *** 	return -EINVAL;
   // *** 
	// *** m->is_used = true;
	// *** m->cb_fn = cb_fn;
	// *** m->fn_data = data;
	// *** m->timeout = timeout ? : RTSP_DEFAULT_TIMEOUT;
	// *** m->timeout += shl_now(CLOCK_MONOTONIC);
   // *** 
	// *** /* verify cookie and generate one if none is set */
	// *** if (m->cookie & RTSP_FLAG_REMOTE_COOKIE) {
	// *** 	if (m->type != RTSP_MESSAGE_UNKNOWN &&
	// *** 	    m->type != RTSP_MESSAGE_REPLY)
	// *** 		return -EINVAL;
	// *** } else {
	// *** 	if (m->type == RTSP_MESSAGE_REPLY)
	// *** 		return -EINVAL;
	// *** }
   // *** 
	// *** /* needs m->cookie set correctly */
	// *** r = rtsp_link_waiting(m);
	// *** if (r < 0)
	// *** 	return r;
   // *** 
	// *** rtsp_link_outgoing(m);
   // *** 
	// *** if (cookie)
	// *** 	*cookie = m->cookie;
   // *** 

	return 0;
}

static void rtsp_drop_message(struct rtsp_message *m)
{
	if (!m)
		return;

	/* never interrupt messages while being partly sent */
	if (!m->is_sending)
		rtsp_unlink_outgoing(m);

	/* remove from waiting list so neither timeouts nor completions fire */
	rtsp_unlink_waiting(m);
}

void rtsp_call_async_cancel(struct rtsp *bus, uint64_t cookie)
{
	struct rtsp_message *m;
	uint64_t *elem;

	if (!bus || !cookie)
		return;

	if (!shl_htable_lookup_u64(&bus->waiting, cookie, &elem))
		return;

	m = rtsp_message_from_htable(elem);
	rtsp_drop_message(m);
}

