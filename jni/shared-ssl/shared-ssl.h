#ifndef SHARED_CONTEXT_SSL_H
#define SHARED_CONTEXT_SSL_H

#include <pthread.h>


// copied from gstreamer
#define DTLS_KEY_LEN          16
#define DTLS_SALT_LEN         14

typedef struct {
    unsigned char v[DTLS_KEY_LEN];
} dtls_key;

typedef struct {
    unsigned char v[DTLS_SALT_LEN];
} dtls_salt;

typedef enum {
    MS_MICE_SINK_SESSION_DTLS_AUTH_HMAC_SHA1_32 = 1,
    MS_MICE_SINK_SESSION_DTLS_AUTH_HMAC_SHA1_80 = 2
} ms_mice_sink_session_dtls_auth;

typedef enum {
    MS_MICE_SINK_SESSION_DTLS_CIPHER_AES_128_ICM = 1
} ms_mice_sink_session_dtls_cipher;

typedef void (* DTLS_MSGSENDER)(void *,char *,int,void **);

int sssl_initialize();
   // internally sets COMMON_SSL_SERVER_CONTEXT
int sssl_deinitialize();

void * sssl_createDTLS(unsigned long long sessionID);
   // returns pointer to SHARED_SSL_CONTEXT
int sssl_destroyDTLSWithSessionID(unsigned long long sessionID, int doNotLock);
int sssl_destroyDTLSWithStreamID(int streamID, int doNotLock);
void * sssl_getDTLSWithSessionID(unsigned long long sessionID);
void * sssl_getDTLSWithStreamID(int streamID);

int sssl_encryptDTLSInner(void * sssl,void * inBuff,int inBuffSize,void * outBuff,int outBuffSize);
int sssl_decryptDTLSInner(void * sssl,void * inBuff,int inBuffSize,void * outBuff,int outBuffSize);
int sssl_decryptDTLS(unsigned long long sessionID,void * inBuff,int inBuffSize,void * outBuff,
      int outBuffSize);
int sssl_encryptDTLS(unsigned long long sessionID,void * inBuff,int inBuffSize,void * outBuff,int outBuffSize);

int sssl_runDTLSHandshakeWithSecToken(void * sssl,void * secToken,int secTokenLength,
   bool * isDTLSHandshakeCompletePtr,DTLS_MSGSENDER sendOutDTLSHandshake,void * arg1,void ** arg2);


// --- DTLS app thread (media thread) cancelation facility ---

int sssl_setDTLSAppThInitializedWithSessionID(unsigned long long sessionID, int flagValue,
      void ** ssslPtr);
int sssl_setDTLSAppThInitializedWithStreamID(int streamID, int flagValue, void ** ssslPtr);
int sssl_setDTLSAppThInitializedCommon(int streamID, unsigned long long sessionID, int flagValue,
      void ** ssslPtr);
int sssl_getDTLSAppThInitializedWithSessionID(unsigned long long sessionID, void ** ssslPtr);
int sssl_getDTLSAppThInitializedWithStreamID(int streamID, void ** ssslPtr);
int sssl_getDTLSAppThInitializedCommon(int streamID, unsigned long long sessionID,
      void ** ssslPtr);
int sssl_signalDTLSAppThCanceledWithSessionID(unsigned long long sessionID);
int sssl_signalDTLSAppThCanceledWithStreamID(int streamID);
int sssl_signalDTLSAppThCanceledCommon(int streamID, unsigned long long sessionID);
int sssl_waitDTLSAppThCancel(int streamID);
int sssl_initDTLSAppThCancCondVar(int streamID);


// --- shared context storage management facility ---

void sssl_contextStorageInitialize();
void * sssl_contextCreate(unsigned long long sessionID);
int sssl_contextRemove(unsigned long long sessionID);
void * sssl_getContextWithSessionID(unsigned long long sessionID);
void * sssl_getContextWithStreamID(int streamID);
int sssl_setContextStreamID(unsigned long long sessionID,int streamID);
int sssl_getIndexWithSessionID(unsigned long long sessionID);
int sssl_getIndexWithStreamID(int streamID);


// --- shared context simple lock facility ---

int simpleLockInit(pthread_mutex_t * slMutexPtr);
void simpleLockDeInit(pthread_mutex_t * slMutexPtr);
int simpleLockGet(pthread_mutex_t * slMutexPtr);
int simpleLockTryGet(pthread_mutex_t * slMutexPtr);
int simpleLockRelease(pthread_mutex_t * slMutexPtr);


#endif /* SHARED_CONTEXT_SSL_H */

