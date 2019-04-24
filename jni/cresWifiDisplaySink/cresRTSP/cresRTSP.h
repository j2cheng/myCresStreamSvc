#ifndef CRESRTSP_H
#define CRESRTSP_H

#include "coreRTSP.h"
#include "cresRTSPUtils.h"


#ifdef __cplusplus
extern "C"
{
#endif

//
// Prefered video_format / audio_codec_configuration selection strings:
// 
//    Selection string is made of 1-3 Selection Parts, joined with ';' character.
//    There are two methods of constructing selection strings (they can not be mixed for a given
//    string):
// 
//       Method I (direct):
// 
//          !!! method NOT yet implemented !!!
// 
//          For video formats, the selection parts are a hex representation (4 hex digits - 32 bit
//          number) of bits (flags) defined in tables ceaResRefEnc,vesaResRefEnc and hhResRefEnc
//          (found in cresRTSP.c), prefixed with the table identifier - "cea_", "vesa_", "hh_".
//          For audio codec configurations, the selection parts are a hex representation (4 hex
//          digits - 32 bit number) of bits (flags) defined in tables lpcmModeEnc, aacModeEncand
//          and ac3ModeEnc (found in cresRTSP.c), prefixed with the table identifier - "lpcm_",
//          "aac_", "ac3_".
// 
//          Examples:
//          
//             "cea_0026"
//                - selects formats 720x480p60 + 720x480i60 + 1280x720p30
// 
//             "cea_0200;vesa_0002;hh_000c"
//                - selects formats 1920x1080i60 + 800x600p60 + 854x480p30 + 854x480p60
// 
//             "aac_0001;lpcm_0002"               
//                - selects formats AACx48x2 + LPCMx48x2
// 
//       Method II (human readable):
//       
//          For video formats, the parts are made of strings defined in tables
//          ceaResRefEnc,vesaResRefEnc and hhResRefEnc found cresRTSP.c. For audio
//          codecs parts are made of strings defined in tables lpcmModeEnc, aacModeEnc
//          and ac3ModeEnc, also in cresRTSP.c. Each part string selects just one option
//          from the corresponding table. Prepending the part string with the 'upto_' prefix
//          selects all the entries in the corresponding table, up to (and including) the
//          entry selected by the string. Appending postfix '_noninterlaced' to the part
//          string eliminates interlaced formats from the selected ones.
//       
//          Examples:
//          
//             "640x480p50"               
//                - selects just one video format - 640x480p50
// 
//             "upto_720x576p50_noninterlaced"
//                - selects formats "640x480p60", "720x480p60" and "720x576p50"
// 
//             "upto_1920x1080p60;960x540p60"
//                - selects first 9 formats from the CEA table plus one hand held format 960x540p60
//          
//             "upto_LPCMx48x2;upto_AACx48x8;upto_AC3x48x6"
//                - selects all formats from all 3 codec configuration tables (LPCM,AAC,AC3)
// 
typedef struct _rtspsysteminfo
{
   int rtspLogLevel;                // permisable values as per CSIO_LOG with the addition of -1
                                    // which denotes "use default"
   int rtpPort;                     // -1 denotes "use default"
   char * preferredVidResRefStr;    // preferred video format (resolution/refresh_rate) selection string
   char * preferredAudioCodecStr;   // preferred audio codec configuration selection string
   char * friendlyName;             //
   char * modelName;                //
} RTSPSYSTEMINFO;

typedef struct _rtspheaderdata
{
   //
   // members of this structure are valid only under specific, individual
   // circumstances
   //
   int sourceRTPPort[2];               // -1 if not valid, 2 ports for RTP and RTCP
   char * sessionID;                // NULL if not valid
   int keepAliveTimeout;            // -1 if not valid
   char * triggerMethod;            // NULL if not valid
   unsigned int ssrc;               // 0 assumed to be an invalid SSRC
   char * srcVersionStr;            // NULL if not valid
} RTSPHEADERDATA;

typedef struct _rtspparsingresults
{
   unsigned int messageType;
   //
   // valid with messageType RTSP_MESSAGE_REQUEST
   //
   char * request_method;
   char * request_uri;
   //
   // valid with messageType RTSP_MESSAGE_REPLY
   //
   char * reply_phrase;
   unsigned int reply_code;
   //
   RTSPHEADERDATA headerData;
   //
   struct rtsp_message * parsedMessagePtr;
} RTSPPARSINGRESULTS;

typedef struct _rtspcomposingresults
{
   unsigned int messageType;
   char * request_method;
   char * request_uri;
   char * reply_phrase;
   unsigned int reply_code;
   char  * composedMessagePtr;
} RTSPCOMPOSINGRESULTS;

typedef int (* RTSPPARSERAPP_CALLBACK)(RTSPPARSINGRESULTS * parsingResPtr, void * appArgument);
typedef int (* RTSPPARSERAPP_COMPOSECALLBACK)(RTSPCOMPOSINGRESULTS * composingResPtr, void * appArgument);

struct rtsp
{
   // *** application callback support ***
   RTSPPARSERAPP_CALLBACK crestCallback;
   void * crestCallbackArg;
   RTSPPARSERAPP_COMPOSECALLBACK crestComposeCallback;
   void * crestComposeCallbackArg;

   // *** session parameters ***
   int rtpPort;
   char preferredVidResRefStr[128];
   char preferredAudioCodecStr[64];
   char friendlyName[64];
   char modelName[64];

   // *** session control ***
   int sourceRTPPort[2];
   int keepAliveTimeout;
   unsigned int ssrc;
   char sessionID[32];
   char triggerMethod[32];
   char srcVersionStr[64];
   char presentationURL[256];
   char audioFormat[16];
   unsigned int modes;
   unsigned int latency;
   unsigned int cea_res;
   unsigned int vesa_res;
   unsigned int hh_res;

	unsigned long ref;
	uint64_t cookies;
	int fd;
	int64_t priority;
	struct shl_dlist matches;

	/* outgoing messages */
	struct shl_dlist outgoing;
	size_t outgoing_cnt;

	/* waiting messages */
	struct shl_htable waiting;
	size_t waiting_cnt;

	/* ring parser */
	struct rtsp_parser
   {
		struct rtsp_message *m;
		struct shl_ring buf;
		size_t buflen;

		enum
      {
			STATE_NEW,
			STATE_HEADER,
			STATE_HEADER_QUOTE,
			STATE_HEADER_NL,
			STATE_BODY,
			STATE_DATA_HEAD,
			STATE_DATA_BODY,
		} state;

		char last_chr;
		size_t remaining_body;

		size_t data_size;
		uint8_t data_channel;

		bool quoted : 1;
		bool dead : 1;
	} parser;

	bool is_dead : 1;
	bool is_calling : 1;
};

struct rtsp_match
{
	struct shl_dlist list;
	rtsp_callback_fn cb_fn;
	void *data;

	bool is_removed : 1;
};

struct rtsp_header
{
	char *key;
	char *value;
	size_t token_cnt;
	size_t token_used;
	char **tokens;
	char *line;
	size_t line_len;
};

struct rtsp_message
{
	unsigned long ref;
	struct rtsp *bus;
	struct shl_dlist list;

	unsigned int type;
	uint64_t cookie;
	unsigned int major;
	unsigned int minor;

	/* unknown specific */
	char *unknown_head;

	/* request specific */
	char *request_method;
	char *request_uri;

	/* reply specific */
	unsigned int reply_code;
	char *reply_phrase;

	/* data specific */
	unsigned int data_channel;
	uint8_t *data_payload;
	size_t data_size;

	/* iterators */
	bool iter_body;
	struct rtsp_header *iter_header;
	size_t iter_token;

	/* headers */
	size_t header_cnt;
	size_t header_used;
	struct rtsp_header *headers;
	struct rtsp_header *header_clen;
	struct rtsp_header *header_ctype;
	struct rtsp_header *header_cseq;

	/* body */
	uint8_t *body;
	size_t body_size;
	size_t body_cnt;
	size_t body_used;
	struct rtsp_header *body_headers;

	/* transmission */
	rtsp_callback_fn cb_fn;
	void *fn_data;
	uint64_t timeout;
	uint8_t *raw;
	size_t raw_size;
	size_t sent;

	bool is_used : 1;
	bool is_sealed : 1;
	bool is_outgoing : 1;
	bool is_waiting : 1;
	bool is_sending : 1;
};


void * initRTSPParser(RTSPSYSTEMINFO * sysInfo);      // it is o.k. to pass NULL for sysInfo
int deInitRTSPParser(void * session);
int parseRTSPMessage(void * session,char * message, RTSPPARSERAPP_CALLBACK callback,
      void * callbackArg);
int composeRTSPRequest(void * session,char * requestMethod,RTSPPARSERAPP_COMPOSECALLBACK callback,
      void * callbackArg);
int composeRTSPResponse(void * session,RTSPPARSINGRESULTS * requestParsingResultsPtr,
      int responseStatus, RTSPPARSERAPP_COMPOSECALLBACK callback, void * callbackArg);

#ifdef __cplusplus
}                               /* End of extern "C" */
#endif

#endif /* CRESRTSP_H */

