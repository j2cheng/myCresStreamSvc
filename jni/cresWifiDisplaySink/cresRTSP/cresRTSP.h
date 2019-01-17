#ifndef CRESRTSP_H
#define CRESRTSP_H

#include "coreRTSP.h"
#include "cresRTSPUtils.h"


#ifdef __cplusplus
extern "C"
{
#endif

typedef struct _rtspheaderdata {
   //
   // members of this structure are valid only under specific, individual
   // circumstances
   //
   int sourceRTPPort;
   char * session;
   char * triggerMethod;
}RTSPHEADERDATA;

typedef struct _rtspparsingresults {
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
}RTSPPARSINGRESULTS;

typedef struct _rtspcomposingresults {
   unsigned int messageType;
   char * request_method;
   char * request_uri;
   char * reply_phrase;
   unsigned int reply_code;
   char  * composedMessagePtr;
}RTSPCOMPOSINGRESULTS;

typedef int (* RTSPPARSERAPP_CALLBACK)(RTSPPARSINGRESULTS * parsingResPtr, void * appArgument);
typedef int (* RTSPPARSERAPP_COMPOSECALLBACK)(RTSPCOMPOSINGRESULTS * composingResPtr, void * appArgument);


struct rtsp {

   RTSPPARSERAPP_CALLBACK crestCallback;
   void * crestCallbackArg;
   RTSPPARSERAPP_COMPOSECALLBACK crestComposeCallback;
   void * crestComposeCallbackArg;



   // session control
   int rtpPort;
   int sourceRTPPort;
   char session[32];
   char triggerMethod[32];
   char transport[64];
   char presentationURL[256];
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
	struct rtsp_parser {
		struct rtsp_message *m;
		struct shl_ring buf;
		size_t buflen;

		enum {
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

struct rtsp_match {
	struct shl_dlist list;
	rtsp_callback_fn cb_fn;
	void *data;

	bool is_removed : 1;
};

struct rtsp_header {
	char *key;
	char *value;
	size_t token_cnt;
	size_t token_used;
	char **tokens;
	char *line;
	size_t line_len;
};

struct rtsp_message {
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


int initRTSPParser(int rtpPort);
int deInitRTSPParser(void);
int parseRTSPMessage(char * message, RTSPPARSERAPP_CALLBACK callback, void * callbackArg);
int composeRTSPRequest(char * requestMethod,RTSPPARSERAPP_COMPOSECALLBACK callback,
      void * callbackArg);
int composeRTSPResponse(RTSPPARSINGRESULTS * requestParsingResultsPtr,
      int responseStatus, RTSPPARSERAPP_COMPOSECALLBACK callback, void * callbackArg);

#ifdef __cplusplus
}                               /* End of extern "C" */
#endif



#endif /* CRESRTSP_H */

