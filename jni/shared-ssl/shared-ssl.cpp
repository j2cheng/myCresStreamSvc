#include <stdio.h>
#include <unistd.h>
#include <sys/types.h>

#include <openssl/bio.h>
#include <openssl/ssl.h>
#include <openssl/err.h>
#include <openssl/conf.h>

#include "csioCommBase.h"

#include "shared-ssl.h"
#include "../ms_mice_sink/shared/glib-utilities.h"// 
// #include "support/android-miraclecast-common.h"
// #include "../gstreamer_android/miraclecast/src/shared/shl_log.h"
// #ifdef __ANDROID__
// #include <android/log.h>
// #endif /* __ANDROID__ */

#define sssl_log              CSIO_LOG
//
#define LOGLEV_error          eLogLevel_error
#define LOGLEV_warning        eLogLevel_warning
#define LOGLEV_info           eLogLevel_info
#define LOGLEV_debug          eLogLevel_debug
// 
// #define sssl_log              log_printf
// //
// #define LOGLEV_error          LOG_ERROR  
// #define LOGLEV_warning        LOG_WARNING
// #define LOGLEV_info           LOG_INFO   
// #define LOGLEV_debug          LOG_DEBUG  

#define GEN_KEY 1
#ifdef GEN_KEY
#define CRESTRON_SERV_PRIVATEKEY_FILE        "/dev/shm/mice_key.pem"
#define CRESTRON_SERV_CERTIFICATE_FILE       "/dev/shm/mice_cert.pem"
#else
//Note: these pem files came from:
//https://urldefense.proofpoint.com/v2/url?u=https-3A__github.com_wolfSSL_wolfssl-2Dexamples_blob_master_certs_ca-2Dcert.pem&d=DwIFAg&c=BevoquqpKcc6oV2fwHriBQ&r=B2DQCtbHXG3qgJ8_23NookXHqe9juKFgc0eymwW84dc&m=nc5B26E1qiVNx0fXkofRODp4ubXsYHN5RP_BiPerc8c&s=Vjzth1NkEV992wH_drRhFn4UxNwIpN5wUy4RnhqchLc&e=
//https://urldefense.proofpoint.com/v2/url?u=https-3A__github.com_wolfSSL_wolfssl-2Dexamples_blob_master_certs_ca-2Dkey.pem&d=DwIFAg&c=BevoquqpKcc6oV2fwHriBQ&r=B2DQCtbHXG3qgJ8_23NookXHqe9juKFgc0eymwW84dc&m=nc5B26E1qiVNx0fXkofRODp4ubXsYHN5RP_BiPerc8c&s=76F7UGOV9UnYIfK46dHnPbYZHW3DtJbxBTqHqQQQerc&e=
#define CRESTRON_SERV_PRIVATEKEY_FILE        "/dev/shm/server-key.pem"
#define CRESTRON_SERV_CERTIFICATE_FILE       "/dev/shm/server-cert.pem"
#define CRESTRON_CA_CERTIFICATE_FILE         "/dev/shm/ca-cert.pem"
#endif



// ***
#define LOCKSLEEPTIME      10          // seconds

int gOneTimeBeforeLockDelay = 0;
int gOneTimeAfterLockDelay = 0;
int gOneTimeBeforeUnlockDelay = 0;
int gOneTimeAfterUnlockDelay = 0;
int gCurrentStreamID = -1;
unsigned long long gCurrentSessionID = 0;
int gDelayValue = LOCKSLEEPTIME;
int gDelaySkipCnt = 1;
// ***



typedef struct {
   SSL_CTX * common_SSL_CTX;
#ifdef GEN_KEY
   EVP_PKEY * pKey;
   X509 * pX509;
#endif
} COMMON_SSL_SERVER_CONTEXT;

typedef struct {
   COMMON_SSL_SERVER_CONTEXT * commonServerContext;
   SSL * ssl;
   BIO * rbio;       // for writing encrypted
   BIO * wbio;       // for reading encrypted
   int appThInitialized;      // this is NOT a binary value
   pthread_cond_t appThCancelComplCondVar;
   pthread_mutex_t innerDTLSMutex;
   int ready;        // this flag gets checked in the following locations:
                     //    sssl_getDTLSWithSessionID()
                     //    sssl_getDTLSWithStreamID()
                     //    sssl_encryptDTLS()
                     //    sssl_decryptDTLS()
} SHARED_SSL_CONTEXT;

#define SSL_CONTEXT_STORAGE_SIZE    16

typedef struct {
   int inUse;
   unsigned long long sessionID;
   int streamID;
   SHARED_SSL_CONTEXT ssslContext;
} SSL_CONTEXT_STORAGE_ELEM;


COMMON_SSL_SERVER_CONTEXT * gCommonSSLServerContext = NULL;
SSL_CONTEXT_STORAGE_ELEM gSSLContextStorage[SSL_CONTEXT_STORAGE_SIZE];
pthread_mutex_t gContextStorageMutex;


SSL_CTX * CreateNewSSLContext();
SSL_CTX * InitServerCTX();
bool LoadCertificates(SSL_CTX * ctx, char * CertFile, char * KeyFile);
void ssl_info_callback(const SSL * ssl, int where, int ret);
EVP_PKEY * generate_key();
X509 * generate_x509(EVP_PKEY * pkey);
bool write_to_disk(EVP_PKEY * pkey, X509 * x509);

void sssl_contextStorageInitialize();


int sssl_initialize()
{
    if(gCommonSSLServerContext)
    {
        sssl_log(LOGLEV_error,"mira: sssl_initialize() - gCommonSSLServerContext is NOT NULL - exiting");
        return(-1);
    }

    COMMON_SSL_SERVER_CONTEXT * sslServerContext
         = (COMMON_SSL_SERVER_CONTEXT *)malloc(sizeof(COMMON_SSL_SERVER_CONTEXT));
    if(!sslServerContext)
    {
        sssl_log(LOGLEV_error,"mira: allocating COMMON_SSL_SERVER_CONTEXT failed.");
        return(-1);
    }
    memset(sslServerContext,0,sizeof(COMMON_SSL_SERVER_CONTEXT));

    simpleLockInit(&gContextStorageMutex);
    sssl_contextStorageInitialize();

    CRYPTO_mem_ctrl(CRYPTO_MEM_CHECK_ON);

    SSL_load_error_strings();
    SSL_library_init();
    OpenSSL_add_all_algorithms();
    OPENSSL_init_crypto(OPENSSL_INIT_LOAD_CRYPTO_STRINGS, NULL);

#ifdef GEN_KEY
    //to generate certificate:https://urldefense.proofpoint.com/v2/url?u=https-3A__gist.github.com_nathan-2Dosman_5041136&d=DwIFAg&c=BevoquqpKcc6oV2fwHriBQ&r=B2DQCtbHXG3qgJ8_23NookXHqe9juKFgc0eymwW84dc&m=nc5B26E1qiVNx0fXkofRODp4ubXsYHN5RP_BiPerc8c&s=lorRRM_tnkiqEJqP7_wM4lxWGWgcRcc9OaDt-OwIQ5s&e=
    {
        sslServerContext->pKey = generate_key();
        if(!sslServerContext->pKey)
        {
            sssl_log(LOGLEV_error,"mira: initialize_SSL: generate_key failed.");
            return(-1);
        }
        else
        {
            sslServerContext->pX509 = generate_x509(sslServerContext->pKey);
            if(!sslServerContext->pX509)
            {
                EVP_PKEY_free(sslServerContext->pKey);
                sssl_log(LOGLEV_error,"mira: initialize_SSL: generate_x509 failed.");
                return(-1);
            }
            else
            {
                bool ret = write_to_disk(sslServerContext->pKey, sslServerContext->pX509);
                sssl_log(LOGLEV_debug,"mira: initialize_SSL: writing key and certificate to disk....[%d]",ret);
            }
        }
    }
#endif

    sslServerContext->common_SSL_CTX = CreateNewSSLContext();
    sssl_log(LOGLEV_debug,"mira: initialize_SSL() - CreateNewSSLContext() returned [0x%x]",
        sslServerContext->common_SSL_CTX);

    gCommonSSLServerContext = sslServerContext;

    return(0);
}


int sssl_deinitialize()
{
    if(!gCommonSSLServerContext)
    {
        sssl_log(LOGLEV_error,"mira: sssl_deinitialize: gCommonSSLServerContext is already NULL");
        return(-1);
    }

    // it would be unexpected if control comes to this point on two different
    // threads, in which case we need to relay on pthread to gently handle
    // using of deinitialized mutex. This can not be cleanly avoided. Also, for
    // this practically impossible case there is this check bellow.

    simpleLockGet(&gContextStorageMutex);

    if(!gCommonSSLServerContext)
    {
        simpleLockRelease(&gContextStorageMutex);
        sssl_log(LOGLEV_error,"mira: sssl_deinitialize: gCommonSSLServerContext is NULL after lock");
        return(-1);
    }

    ERR_free_strings();
    EVP_cleanup();

    if(gCommonSSLServerContext->common_SSL_CTX)
    {
        SSL_CTX_free(gCommonSSLServerContext->common_SSL_CTX);
        gCommonSSLServerContext->common_SSL_CTX = NULL;
    }
#ifdef GEN_KEY
    if(!gCommonSSLServerContext->pKey)
    {
        sssl_log(LOGLEV_error,"mira: sssl_deinitialize: NULL gCommonSSLServerContext->pKey");
        // complete deinit anyway
    }
    else if(!gCommonSSLServerContext->pX509)
    {
        sssl_log(LOGLEV_error,"mira: sssl_deinitialize: NULL gCommonSSLServerContext->pX509");
        // complete deinit anyway
    }
    else
    {
        sssl_log(LOGLEV_debug,"mira: before calling EVP_PKEY_free(gCommonSSLServerContext->pKey)");

        // *** X509_free(gCommonSSLServerContext->pX509);
    }
#endif

    gCommonSSLServerContext = NULL;

    simpleLockRelease(&gContextStorageMutex);

    simpleLockDeInit(&gContextStorageMutex);

    return(0);
}


void * sssl_createDTLS(unsigned long long sessionID)
{
    if(!gCommonSSLServerContext)
        return(NULL);

    simpleLockGet(&gContextStorageMutex);

    SHARED_SSL_CONTEXT * ssslContext = (SHARED_SSL_CONTEXT *)sssl_contextCreate(sessionID);
    if(!ssslContext)
    {
        simpleLockRelease(&gContextStorageMutex);
        sssl_log(LOGLEV_error,"mira: sssl_createDTLS(sessionID = 0x%llx) - could not get SHARED_SSL_CONTEXT",sessionID);
        return(NULL);
    }
    memset(ssslContext,0,sizeof(SHARED_SSL_CONTEXT));

    ssslContext->commonServerContext = gCommonSSLServerContext;
    sssl_log(LOGLEV_debug,"mira: sssl_createDTLS(sessionID = 0x%llx)",sessionID);

    simpleLockInit(&ssslContext->innerDTLSMutex);



    // ***
    gCurrentSessionID = sessionID;



    sssl_log(LOGLEV_debug,"mira: sssl_createDTLS() ssslContext->commonServerContext->common_SSL_CTX = [0x%x]",
      ssslContext->commonServerContext->common_SSL_CTX);

    if(ssslContext->commonServerContext->common_SSL_CTX)
    {
        SSL * ssl = SSL_new(ssslContext->commonServerContext->common_SSL_CTX);
        ssslContext->ssl = ssl;
        sssl_log(LOGLEV_debug,"mira: sssl_createDTLS() ssl[0x%x] ",ssl);

        //uncomment for the callback
        //SSL_set_info_callback(ssl, ssl_info_callback);

        BIO *rbio = BIO_new(BIO_s_mem());
        BIO_set_close(rbio, BIO_CLOSE);
        //BIO_set_mem_eof_return(rbio, -1);
        BIO_set_nbio(rbio, 1);
        ssslContext->rbio = rbio;
        sssl_log(LOGLEV_debug,"mira: sssl_createDTLS() rbio[0x%x] ",rbio);

        BIO *wbio = BIO_new(BIO_s_mem());
        BIO_set_close(wbio, BIO_CLOSE);
        //BIO_set_mem_eof_return(wbio, -1);
        BIO_set_nbio(wbio, 1);
        ssslContext->wbio = wbio;
        sssl_log(LOGLEV_debug,"mira: sssl_createDTLS() wbio[0x%x] ",wbio);

        SSL_set_bio(ssl, rbio, wbio);
        SSL_set_accept_state(ssl); // The server uses SSL_set_accept_state

        simpleLockRelease(&gContextStorageMutex);
        return(ssslContext);
    }

    simpleLockRelease(&gContextStorageMutex);
    return(NULL);
}


int sssl_destroyDTLSWithSessionID(unsigned long long sessionID, int doNotLock)
{
    sssl_log(LOGLEV_debug,"mira: sssl_destroyDTLSWithSessionID(sessionID = 0x%llx)",sessionID);

    if(doNotLock == 0)
    {
        simpleLockGet(&gContextStorageMutex);
    }
    SHARED_SSL_CONTEXT * ssslContext = (SHARED_SSL_CONTEXT *)sssl_getContextWithSessionID(sessionID);
    if(!ssslContext)
    {
        if(doNotLock == 0)
        {
            simpleLockRelease(&gContextStorageMutex);
        }
        sssl_log(LOGLEV_error,"mira: sssl_destroyDTLSWithSessionID() - could not get SHARED_SSL_CONTEXT");
        return(-1);
    }

    // Note: do not call BIO_free on rbio and wbio, it is called inside SSL_free().
    if(ssslContext->ssl)
    {
        SSL_free(ssslContext->ssl);
        ssslContext->ssl = NULL;
    }

    simpleLockDeInit(&ssslContext->innerDTLSMutex);

    sssl_contextRemove(sessionID);

    if(doNotLock == 0)
    {
        simpleLockRelease(&gContextStorageMutex);
    }

    return(0);
}


int sssl_destroyDTLSWithStreamID(int streamID, int doNotLock)
{
    int retv;

    if(doNotLock == 0)
    {
        simpleLockGet(&gContextStorageMutex);
    }

    int index = sssl_getIndexWithStreamID(streamID);
    if(index < 0)
    {
        if(doNotLock == 0)
        {
            simpleLockRelease(&gContextStorageMutex);
        }
        sssl_log(LOGLEV_error,"mira: sssl_destroyDTLSWithStreamID() - could not get index for streamID %d",streamID);
        return(-1);
    }

    unsigned long long sessionID = gSSLContextStorage[index].sessionID;

    retv = sssl_destroyDTLSWithSessionID(sessionID, 1);

    if(doNotLock == 0)
    {
        simpleLockRelease(&gContextStorageMutex);
    }

    return(retv);
}


void * sssl_getDTLSWithSessionID(unsigned long long sessionID,int checkHandshake)
{
    if(!gCommonSSLServerContext)
    {
        sssl_log(LOGLEV_debug,"mira: {%s} - null gCommonSSLServerContext",__FUNCTION__);
        return(NULL);
    }
    simpleLockGet(&gContextStorageMutex);
    void * sssl = sssl_getContextWithSessionID(sessionID);
    if(checkHandshake && sssl) 
    {
        if(((SHARED_SSL_CONTEXT *)sssl)->ready != 1)
        {
            sssl = NULL;
        }
    }
    simpleLockRelease(&gContextStorageMutex);
    return(sssl);
}


void * sssl_getDTLSWithStreamID(int streamID,int checkHandshake)
{
    if(!gCommonSSLServerContext)
    {
        sssl_log(LOGLEV_debug,"mira: {%s} - null gCommonSSLServerContext",__FUNCTION__);
        return(NULL);
    }
    simpleLockGet(&gContextStorageMutex);
    void * sssl = sssl_getContextWithStreamID(streamID);
    if(checkHandshake && sssl) 
    {
        if(((SHARED_SSL_CONTEXT *)sssl)->ready != 1)
        {
            sssl = NULL;
        }
    }
    simpleLockRelease(&gContextStorageMutex);
    return(sssl);
}


#define BIORETRYCOUNT      10

int sssl_encryptDTLSInner(void * sssl,void * inBuff,int inBuffSize,void * outBuff,int outBuffSize)
{
    // Remarks:
    //   1. May require external locking.
    //   2. This initial implementation makes an optimistic accumption that for each unencrypted 'record'
    //      (payload chunk) submitted in inBuff the SSL will make an entire encrypted SSL/TLS record
    //      available to BIO_read() right away.

    int retv;
    int retryCount;

    SHARED_SSL_CONTEXT * ssslContext = (SHARED_SSL_CONTEXT *)sssl;

    retryCount = BIORETRYCOUNT;
    while(retryCount > 0)
    {
        retv = SSL_write(ssslContext->ssl,inBuff,inBuffSize);
        if(retv <= 0)
        {
            // treat 0 as failure
            int errcode = SSL_get_error(ssslContext->ssl,retv);
            if(errcode == SSL_ERROR_WANT_WRITE)
            {
                usleep(5*1000);     // 5 msec
                retryCount--;
                sssl_log(eLogLevel_extraVerbose,"mira: {%s} - retrying SSL_write(), retries left = %d",
                    __FUNCTION__,retryCount);
                continue;
            }
            else if(errcode == SSL_ERROR_WANT_READ)
            {
                sssl_log(LOGLEV_error,"mira: {%s} - SSL_write() failed, retv = %d, unexpected errcode = SSL_ERROR_WANT_READ",
                  __FUNCTION__,retv);
            }
            else
            {
                sssl_log(LOGLEV_error,"mira: {%s} - SSL_write() failed, retv = %d, errcode = %d",__FUNCTION__,retv,errcode);
            }
            return(-1);
        }
        break;
    }
    if(retryCount <= 0)
    {
        // for now - at the debug level
        sssl_log(LOGLEV_debug,"mira: {%s} - SSL_write() failed despite 10 retries",__FUNCTION__);
        return(-1);
    }
    if(retv != inBuffSize)
    {
        sssl_log(LOGLEV_error,"mira: SSL_write() wrote only %d bytes out of requested %d",retv,inBuffSize);
        return(-1);
    }

    retryCount = BIORETRYCOUNT;
    while(retryCount > 0)
    {
        retv = BIO_read(ssslContext->wbio,outBuff,outBuffSize);
        if(retv <= 0)
        {
            // treat 0 as potential failure
            if(BIO_should_retry(ssslContext->wbio))  
            {
                usleep(5*1000);     // 5 msec
                retryCount--;
                sssl_log(eLogLevel_extraVerbose,"mira: {%s} - retrying BIO_read(), retries left = %d",
                    __FUNCTION__,retryCount);
                continue;
            }
            sssl_log(LOGLEV_error,"mira: {%s} - BIO_read() failed, retv = %d",__FUNCTION__,retv);
            return(-1);
        }
        break;
    }
    if(retryCount <= 0)
    {
        // for now - at the debug level
        sssl_log(LOGLEV_debug,"mira: {%s} - BIO_read() failed despite 10 retries",__FUNCTION__);
        return(-1);
    }
    if(retv >= outBuffSize)
    {
        sssl_log(LOGLEV_warning,"mira: probably insufficient buffer for BIO_read(), retv = %d, outBuffSize = %d",
            retv,outBuffSize);
    }

    return(retv);
}


int sssl_decryptDTLSInner(void * sssl,void * inBuff,int inBuffSize,void * outBuff,int outBuffSize)
{
    // Remarks:
    //   1. May require external locking.
    //   2. This initial implementation makes an optimistic accumption that entire SSL/TLS encrypted
    //      records are being submitted in inBuff and that under such conditions SSL makes entire
    //      decrypted 'records' (payload chunks) available to SSL_read() right away.

    int retv;
    int retryCount;

    SHARED_SSL_CONTEXT * ssslContext = (SHARED_SSL_CONTEXT *)sssl;

    retryCount = BIORETRYCOUNT;
    while(retryCount > 0)
    {
        retv = BIO_write(ssslContext->rbio,inBuff,inBuffSize);
        if(retv <= 0)
        {
            // treat 0 as potential failure
            if(BIO_should_retry(ssslContext->rbio))  
            {
                usleep(5*1000);     // 5 msec
                retryCount--;
                sssl_log(eLogLevel_extraVerbose,"mira: {%s} - retrying BIO_write(), retries left = %d",
                    __FUNCTION__,retryCount);
                continue;
            }
            sssl_log(LOGLEV_error,"mira: {%s} - BIO_write() failed, retv = %d",__FUNCTION__,retv);
            return(-1);
        }
        break;
    }
    if(retryCount <= 0)
    {
        // for now - at the debug level
        sssl_log(LOGLEV_debug,"mira: {%s} - BIO_write() failed despite 10 retries",__FUNCTION__);
        return(-1);
    }
    if(retv != inBuffSize)
    {
        sssl_log(LOGLEV_error,"mira: {%s} - BIO_write() wrote only %d bytes out of requested %d",
            __FUNCTION__,retv,inBuffSize);
        return(-1);
    }

    retryCount = BIORETRYCOUNT;
    while(retryCount > 0)
    {
        retv = SSL_read(ssslContext->ssl,outBuff,outBuffSize);
        if(retv <= 0)
        {
            // treat 0 as failure
            int errcode = SSL_get_error(ssslContext->ssl,retv);
            if(errcode == SSL_ERROR_WANT_READ)
            {
                usleep(5*1000);     // 5 msec
                retryCount--;
                sssl_log(eLogLevel_extraVerbose,"mira: {%s} - retrying SSL_read(), retries left = %d",
                    __FUNCTION__,retryCount);
                continue;
            }
            else if(errcode == SSL_ERROR_WANT_WRITE)
            {
                sssl_log(LOGLEV_error,"mira: {%s} - SSL_read() failed, retv = %d, unexpected errcode = SSL_ERROR_WANT_WRITE",
                  __FUNCTION__,retv);
            }
            else
            {
                sssl_log(LOGLEV_error,"mira: {%s} - SSL_read() failed, retv = %d, errcode = %d",__FUNCTION__,retv,errcode);
            }
            return(-1);
        }
        break;
    }
    if(retryCount <= 0)
    {
        // for now - at the debug level
        sssl_log(LOGLEV_debug,"mira: {%s} - SSL_read() failed despite 10 retries",__FUNCTION__);
        return(-1);
    }
    if(retv >= outBuffSize)
    {
        sssl_log(LOGLEV_warning,"mira: {%s} - probably insufficient buffer for SSL_read(), retv = %d, outBuffSize = %d",
            __FUNCTION__,retv,outBuffSize);
    }

    return(retv);
}


int sssl_decryptDTLSLocal(void * sssl,void * inBuff,int inBuffSize,void * outBuff,int outBuffSize)
{
    int retv;

    simpleLockGet(&((SHARED_SSL_CONTEXT *)sssl)->innerDTLSMutex);
    retv = sssl_decryptDTLSInner(sssl,inBuff,inBuffSize,outBuff,outBuffSize);
    simpleLockRelease(&((SHARED_SSL_CONTEXT *)sssl)->innerDTLSMutex);

    return(retv);
}


int sssl_encryptDTLS(unsigned long long sessionID,void * inBuff,int inBuffSize,void * outBuff,int outBuffSize)
{
    int retv;
    void * sssl;

    // assumed to be invoked after handsake completes!

    sssl_log(LOGLEV_debug,"mira: {%s} - entering with sessionID = 0x%llx",__FUNCTION__,sessionID);

    simpleLockGet(&gContextStorageMutex);
    sssl = sssl_getContextWithSessionID(sessionID);
    if(sssl == NULL)
    {
        simpleLockRelease(&gContextStorageMutex);
        sssl_log(LOGLEV_error,"mira: {%s} - could not get sssl context",__FUNCTION__);
        return(-1);
    }
    if(((SHARED_SSL_CONTEXT *)sssl)->ready != 1)
    {
        simpleLockRelease(&gContextStorageMutex);
        sssl_log(LOGLEV_debug,"mira: {%s} - sssl context not ready",__FUNCTION__);
        return(-1);
    }
    simpleLockGet(&((SHARED_SSL_CONTEXT *)sssl)->innerDTLSMutex);
    simpleLockRelease(&gContextStorageMutex);

    retv = sssl_encryptDTLSInner(sssl,inBuff,inBuffSize,outBuff,outBuffSize);

    // no global lock wrapping here!
    // nobody will delete sssl context before this
    simpleLockRelease(&((SHARED_SSL_CONTEXT *)sssl)->innerDTLSMutex);

    sssl_log(LOGLEV_debug,"mira: {%s} - exiting",__FUNCTION__);

    return(retv);
}


int sssl_decryptDTLS(unsigned long long sessionID,void * inBuff,int inBuffSize,void * outBuff,int outBuffSize)
{
    int retv;
    void * sssl;

    // assumed to be invoked after handsake completes!

    sssl_log(LOGLEV_debug,"mira: {%s} - entering with sessionID = 0x%llx",__FUNCTION__,sessionID);

    simpleLockGet(&gContextStorageMutex);
    sssl = sssl_getContextWithSessionID(sessionID);
    if(sssl == NULL)
    {
        simpleLockRelease(&gContextStorageMutex);
        sssl_log(LOGLEV_error,"mira: {%s} - could not get sssl context",__FUNCTION__);
        return(-1);
    }
    if(((SHARED_SSL_CONTEXT *)sssl)->ready != 1)
    {
        simpleLockRelease(&gContextStorageMutex);
        sssl_log(LOGLEV_debug,"mira: {%s} - sssl context not ready",__FUNCTION__);
        return(-1);
    }
    simpleLockGet(&((SHARED_SSL_CONTEXT *)sssl)->innerDTLSMutex);
    simpleLockRelease(&gContextStorageMutex);

    retv = sssl_decryptDTLSInner(sssl,inBuff,inBuffSize,outBuff,outBuffSize);

    // no global lock wrapping here!
    // nobody will delete sssl context before this
    simpleLockRelease(&((SHARED_SSL_CONTEXT *)sssl)->innerDTLSMutex);

    sssl_log(LOGLEV_debug,"mira: {%s} - exiting",__FUNCTION__);

    return(retv);
}


int sssl_runDTLSHandshakeWithSecToken(void * sssl,void * secToken,int secTokenLength,
   bool * isDTLSHandshakeCompletePtr,DTLS_MSGSENDER sendOutDTLSHandshake,void * arg1,void ** arg2)
{
    // assumed to be executed at an early stage where no locking is needed

    struct {
        dtls_key client_key;
        dtls_key server_key;
        dtls_salt client_salt;
        dtls_salt server_salt;
    } exported_keys;

    struct {
        dtls_key key;
        dtls_salt salt;
    } client_key, server_key;

    sssl_log(LOGLEV_debug,"mira: {%s} - entering",__FUNCTION__);

    SHARED_SSL_CONTEXT * ssslContext = (SHARED_SSL_CONTEXT *)sssl;

    if(ssslContext && ssslContext->ssl && ssslContext->rbio && ssslContext->wbio)
    {
        // void* data = tlv->security_token.token;
        // int len = tlv->length;
        SSL* ssl = ssslContext->ssl;
        int status,n,pending;
        int bioReadBytes = 0;
        char BIO_read_buf[4096] = {0};

        int writeBytes = BIO_write(ssslContext->rbio,secToken,secTokenLength);

        sssl_log(LOGLEV_debug,"mira: sssl_commenceDTLSHandshakeWithSecToken() - BIO_write() returned [%d]",writeBytes);

        if(writeBytes <= 0)
        {
            sssl_log(LOGLEV_error,"mira: sssl_commenceDTLSHandshakeWithSecToken() - BIO_write() failed with [%d]",
               writeBytes);
            return(-1);
        }

        if (!SSL_is_init_finished(ssl))
        {
            sssl_log(LOGLEV_debug,
               "mira: sssl_commenceDTLSHandshakeWithSecToken() - Handshake not finished,  SSL_get_state() returned [%d]",
               SSL_get_state(ssl));

            //when SSL_do_handshake returns, data has been processed.
            n = SSL_do_handshake(ssl);

            sssl_log(LOGLEV_debug,"mira: sssl_commenceDTLSHandshakeWithSecToken() - SSL_do_handshake() returned [%d]",n);

            //when SSL_do_handshake return 1, we need to read pending data.
            status = SSL_ERROR_WANT_READ;
            if(n <= 0)
            {
                status = SSL_get_error(ssl, n);
                sssl_log(LOGLEV_debug,"mira: sssl_commenceDTLSHandshakeWithSecToken() - SSL_get_error() status[%d]",
                  status);
            }

            //do we really need to check status?
            if (SSL_ERROR_WANT_READ == status)
            {
                pending = BIO_ctrl_pending(ssslContext->wbio);

                sssl_log(LOGLEV_debug,
                  "mira: sssl_commenceDTLSHandshakeWithSecToken() - BIO_ctrl_pending() returned [%d]",pending);

                if (pending > 0)
                {
                    do
                    {
                        memset(BIO_read_buf,0,sizeof(BIO_read_buf));
                        bioReadBytes = BIO_read(ssslContext->wbio, BIO_read_buf, sizeof(BIO_read_buf));
                        if (bioReadBytes > 0)
                        {
                            sendOutDTLSHandshake(arg1,BIO_read_buf,bioReadBytes,arg2);
                        }
                        else if(!BIO_should_retry(ssslContext->wbio))
                        {
                            sssl_log(LOGLEV_debug,
                              "mira: sssl_commenceDTLSHandshakeWithSecToken() -  BIO_should_retry is false, returning ...");
                            break;
                        }
                        else continue;
                    }while(bioReadBytes>0);
                }
                else
                {
                    sssl_log(LOGLEV_debug,
                        "mira: sssl_commenceDTLSHandshakeWithSecToken() - BIO_ctrl_pending is zero, nothing to read.");
                }
            }

            if (!SSL_is_init_finished(ssl))
            {
                sssl_log(LOGLEV_debug,
                  "mira: sssl_commenceDTLSHandshakeWithSecToken() - SSL_is_init_finished is not done yet.");
                return(0);
            }
            else
            {
                *isDTLSHandshakeCompletePtr = true;
                ssslContext->ready = 1;
                sssl_log(LOGLEV_debug,
                    "mira: sssl_commenceDTLSHandshakeWithSecToken() - set isDTLSHandshakeComplete flag to true");
            }
        }
        else
        {
            sssl_log(LOGLEV_debug,
               "mira: sssl_commenceDTLSHandshakeWithSecToken() - SSL_is_init_finished() returned true after writing [%d] bytes",
               writeBytes);
        }
    }
    else
    {
        sssl_log(LOGLEV_error,
            "mira: sssl_commenceDTLSHandshakeWithSecToken() - bad ssslContext[0x%x]",ssslContext);
    }

    sssl_log(LOGLEV_debug,"mira: {%s} - exiting",__FUNCTION__);

    return(0);
}


// --- DTLS app thread (media thread) cancelation facility ---

int sssl_setDTLSAppThInitializedWithSessionID(unsigned long long sessionID, int flagValue,
      void ** ssslPtr)
{
    int retv = sssl_setDTLSAppThInitializedCommon(-1,sessionID,flagValue,ssslPtr);
    return(retv);
}


int sssl_setDTLSAppThInitializedWithStreamID(int streamID, int flagValue, void ** ssslPtr)
{
    int retv = sssl_setDTLSAppThInitializedCommon(streamID,0,flagValue,ssslPtr);
    return(retv);
}


int sssl_setDTLSAppThInitializedCommon(int streamID, unsigned long long sessionID, int flagValue,
      void ** ssslPtr)
{
    // return values:
    //  >= 0 - appThInitialized was set as requested. Its previous value is returned.
    //  -1 - corresponding sssl did not exist when this function was invoked.
    // 
    // Remarks:
    //   1. May pass NULL for ssslPtr.
    //   2. *ssslPtr only valid when return value >= 0.
    //   3. This function does not handle sssl context destruction or signaling through
    //      appThCancelComplCondVar!

    int retv;
    void * sssl;

    simpleLockGet(&gContextStorageMutex);
    if(streamID >= 0)
    {
        sssl = sssl_getContextWithStreamID(streamID);
    }
    else
    {
        sssl = sssl_getContextWithSessionID(sessionID);
    }
    if(sssl != NULL)
    {
        retv = ((SHARED_SSL_CONTEXT *)sssl)->appThInitialized;
        ((SHARED_SSL_CONTEXT *)sssl)->appThInitialized = flagValue;
        if(ssslPtr)
            *ssslPtr = sssl;
    }
    else
    {
        retv = -1;
    }
    simpleLockRelease(&gContextStorageMutex);

    return(retv);
}


int sssl_getDTLSAppThInitializedWithSessionID(unsigned long long sessionID, void ** ssslPtr)
{
    int retv = sssl_getDTLSAppThInitializedCommon(-1,sessionID,ssslPtr);
    return(retv);
}


int sssl_getDTLSAppThInitializedWithStreamID(int streamID, void ** ssslPtr)
{
    int retv = sssl_getDTLSAppThInitializedCommon(streamID,0,ssslPtr);
    return(retv);
}


int sssl_getDTLSAppThInitializedCommon(int streamID, unsigned long long sessionID, void ** ssslPtr)
{
    // return values:
    //  >= 0 - value of appThInitialized
    //  -1 - corresponding sssl did not exist when this function was invoked.
    // 
    // Remarks:
    //   1. May pass NULL for ssslPtr.
    //   2. *ssslPtr only valid when return value >= 0.
    // 

    int retv;
    void * sssl;

    simpleLockGet(&gContextStorageMutex);
    if(streamID >= 0)
    {
        sssl = sssl_getContextWithStreamID(streamID);
    }
    else
    {
        sssl = sssl_getContextWithSessionID(sessionID);
    }
    if(sssl != NULL)
    {
        retv = ((SHARED_SSL_CONTEXT *)sssl)->appThInitialized;
        if(ssslPtr)
            *ssslPtr = sssl;
    }
    else
    {
        retv = -1;
    }
    simpleLockRelease(&gContextStorageMutex);

    return(retv);
}


int sssl_cancelDTLSAppThAndWait(int streamID)
{
    // return values:
    //   0 - Normal return. The media thread was running, the cancelection was initiated by this
    //       function and it successfully completed.
    //   1 - Media thread wasn't initialized yet, however the sssl context was already established.
    //   2 - The sssl context was not yet established (or was already destroyed).
    //  -1 - General error.
    //  -2 - The media thread was running, the cancelection was initiated by this
    //       function but it did not complete before timeout.

    // It will only delete sssl context in cases '0' and '1'

    int retv,retv1,prev,deleteContext;

    sssl_log(LOGLEV_debug,"mira: {%s} - entering with streamID = %d",__FUNCTION__,streamID);

    if(!gCommonSSLServerContext)
    {
        sssl_log(LOGLEV_warning,"mira: {%s} - null gCommonSSLServerContext (is Mice disabled ?)",__FUNCTION__);
        sssl_log(LOGLEV_debug,"mira: {%s} - exiting (with potential warning)",__FUNCTION__);
        return(-1);
    }

    simpleLockGet(&gContextStorageMutex);

    void * sssl = sssl_getContextWithStreamID(streamID);
    if(sssl != NULL)
    {
        deleteContext = 0;

        prev = ((SHARED_SSL_CONTEXT *)sssl)->appThInitialized;
        if(prev != 0)
        {
            ((SHARED_SSL_CONTEXT *)sssl)->appThInitialized = 1;
            if(prev == 2)
            {
                // in this case thread is guaranteed to signal

                //
                // A condition variable allows one thread to wake another up from a wait. They work only
                // if there is a thread waiting at the moment when you trigger the condition. The way to
                // ensure that this is the case is for the waiting thread to lock a mutex which is linked
                // to the condition, and for the signalling thread to lock that mutex before triggering
                // the condition. In other words, the signalling thread can only lock the mutex and trigger
                // the condition if the other thread had the mutex locked but is now waiting.
                //
                // To signal a condition variable when there is no corresponding wait is a logical error
                // because nothing will ever receive the signal.
                // 
                // !!! Condition variables don't remain in a signalled state !!!
                // 

	             struct timespec stopTimeout;
                struct timeval tp;
                
                retv1 = gettimeofday(&tp,NULL);
                stopTimeout.tv_sec  = tp.tv_sec;
                stopTimeout.tv_nsec = tp.tv_usec * 1000;
                stopTimeout.tv_sec += 10;
                
	             // lock obtained up top
	             retv1 = pthread_cond_timedwait(&((SHARED_SSL_CONTEXT *)sssl)->appThCancelComplCondVar,
                   &gContextStorageMutex,&stopTimeout);
	             if(retv1 != 0)
                {
                    sssl_log(LOGLEV_warning,"mira: {%s} - pthread_cond_timedwait() returned %d",__FUNCTION__,retv1);
	                 if(retv1 == ETIMEDOUT)
                    {
                        retv = -2;
                    }
                    else
                    {
                        retv = -1;
                    }
                }
                else
                {
                    deleteContext = 1;
                    retv = 0;
                }
            }
            else
            {
                sssl_log(LOGLEV_warning,"mira: {%s} - unexpected value of appThInitialized = %d",__FUNCTION__,prev);
                retv = -1;
            }
        }
        else
        {
            deleteContext = 1;
            retv = 1;
        }

        if(deleteContext)
        {
            sssl_log(LOGLEV_debug,"mira: {%s} - obtaining innerDTLSMutex()",__FUNCTION__);
            // After we get the inner lock, we know no 'outer' encrypt/decrypt call is in
            // progress, and since we hold the global lock here, no 'outer' encrypt/decrypt
            // will be able to commence.
            simpleLockGet(&((SHARED_SSL_CONTEXT *)sssl)->innerDTLSMutex);
            sssl_log(LOGLEV_debug,"mira: {%s} - releasing innerDTLSMutex()",__FUNCTION__);
            simpleLockRelease(&((SHARED_SSL_CONTEXT *)sssl)->innerDTLSMutex);        // may as well

            sssl_log(LOGLEV_debug,"mira: {%s} - calling sssl_destroyDTLSWithStreamID()",__FUNCTION__);
            // destroy sssl here only if media thread cancelection was indeed initiated by
            // this function or media thread was never initialized
            sssl_destroyDTLSWithStreamID(streamID,1);       // do not lock
        }
    }
    else
    {
        retv = 2;
    }

    simpleLockRelease(&gContextStorageMutex);

    sssl_log(LOGLEV_debug,"mira: {%s} - exiting",__FUNCTION__);

    return(retv);
}


int sssl_handleDTLSAppThCancelation(int streamID)
{
    // return values:
    //   0 - normal return.
    //  -1 - failure (including non-existing sssl context)

    // It will always delete sssl context - except when cancelation was already requested
    // (appThInitialized == 1), because in that case somebody may be waiting on
    // appThCancelComplCondVar, which is located in the sssl context.

    int retv,prev;
    void * sssl;

    sssl_log(LOGLEV_debug,"mira: {%s} - entering with streamID = %d",__FUNCTION__,streamID);

    simpleLockGet(&gContextStorageMutex);

    sssl = sssl_getContextWithStreamID(streamID);
    if(sssl != NULL)
    {
        prev = ((SHARED_SSL_CONTEXT *)sssl)->appThInitialized;
        ((SHARED_SSL_CONTEXT *)sssl)->appThInitialized = 0;

        pthread_cond_broadcast(&((SHARED_SSL_CONTEXT *)sssl)->appThCancelComplCondVar);

        sssl_log(LOGLEV_debug,"mira: {%s} - found appThInitialized = %d",__FUNCTION__,prev);

        retv = 0;
        if(prev == 2)
        {
            sssl_log(LOGLEV_debug,"mira: {%s} - obtaining innerDTLSMutex()",__FUNCTION__);
            // After we get the inner lock, we know no 'outer' encrypt/decrypt call is in
            // progress, and since we hold the global lock here, no 'outer' encrypt/decrypt
            // will be able to commence.
            simpleLockGet(&((SHARED_SSL_CONTEXT *)sssl)->innerDTLSMutex);
            sssl_log(LOGLEV_debug,"mira: {%s} - releasing innerDTLSMutex()",__FUNCTION__);
            simpleLockRelease(&((SHARED_SSL_CONTEXT *)sssl)->innerDTLSMutex);        // may as well

            sssl_log(LOGLEV_debug,"mira: {%s} - calling sssl_destroyDTLSWithStreamID() for streamID %d",
                __FUNCTION__,streamID);
            retv = sssl_destroyDTLSWithStreamID(streamID,1);     // do not lock
        }
        else if(prev != 1)
        {
            // abnormal condition
            sssl_log(LOGLEV_warning,"mira: {%s} - unexpected value of appThInitialized = %d",__FUNCTION__,prev);
            retv = -1;
        }
    }
    else
    {
        sssl_log(LOGLEV_debug,"mira: {%s} - sssl context not found for streamID %d",__FUNCTION__,streamID);
        retv = -1;
    }

    simpleLockRelease(&gContextStorageMutex);

    sssl_log(LOGLEV_debug,"mira: {%s} - exiting",__FUNCTION__);

    return(retv);
}


int sssl_initDTLSAppThCancCondVar(int streamID)
{
    simpleLockGet(&gContextStorageMutex);
    void * sssl = sssl_getContextWithStreamID(streamID);
    if(sssl != NULL)
    {
        pthread_cond_init(&((SHARED_SSL_CONTEXT *)sssl)->appThCancelComplCondVar, NULL);
    }
    simpleLockRelease(&gContextStorageMutex);
    return(0);
}


int sssl_signalDTLSAppThCanceledWithSessionID(unsigned long long sessionID)
{
    int retv = sssl_signalDTLSAppThCanceledCommon(-1,sessionID);
    return(retv);
}


int sssl_signalDTLSAppThCanceledWithStreamID(int streamID)
{
    int retv = sssl_signalDTLSAppThCanceledCommon(streamID,0);
    return(retv);
}


int sssl_signalDTLSAppThCanceledCommon(int streamID, unsigned long long sessionID)
{
    void * sssl;
    simpleLockGet(&gContextStorageMutex);
    if(streamID >= 0)
    {
        sssl = sssl_getContextWithStreamID(streamID);
    }
    else
    {
        sssl = sssl_getContextWithSessionID(sessionID);
    }
    if(sssl != NULL)
    {
        pthread_cond_broadcast(&((SHARED_SSL_CONTEXT *)sssl)->appThCancelComplCondVar);
    }
    simpleLockRelease(&gContextStorageMutex);
    return(0);
}


// --- shared context simple lock facility ---

int simpleLockInit(pthread_mutex_t * slMutexPtr)
{
    int retv;
    
    pthread_mutexattr_t attr;
    pthread_mutexattr_init(&attr);
    // PTHREAD_MUTEX_ERRORCHECK:
    //    This type of mutex provides error checking. A thread attempting to
    //    relock this mutex without first unlocking it will return with an
    //    error. A thread attempting to unlock a mutex which another thread
    //    has locked will return with an error. A thread attempting to unlock
    //    an unlocked mutex will return with an error.
    pthread_mutexattr_settype(&attr, PTHREAD_MUTEX_ERRORCHECK);
    retv = pthread_mutex_init(slMutexPtr,&attr);
    if(retv != 0)
    {
        return(-1);
    }
    else
    {
        return(0);
    }
}


void simpleLockDeInit(pthread_mutex_t * slMutexPtr)
{
    pthread_mutex_unlock(slMutexPtr);
    pthread_mutex_destroy(slMutexPtr);
}


int simpleLockGet(pthread_mutex_t * slMutexPtr)
{
    // ***
    // int retv = pthread_mutex_lock(slMutexPtr);
    // ***
    int delayValue;
    if(gOneTimeBeforeLockDelay > 0)
    {
        if(gDelaySkipCnt > 0)
        {
            gDelaySkipCnt--;
        }
        else 
        {
            delayValue = gOneTimeBeforeLockDelay * 1000000;    // usec
            gOneTimeBeforeLockDelay = 0;
            sssl_log(LOGLEV_debug,"mira: {%s} - before lock, before sleeping",__FUNCTION__);
            usleep(delayValue);
            sssl_log(LOGLEV_debug,"mira: {%s} - before lock, after sleeping",__FUNCTION__);
        }
    }
    int retv = pthread_mutex_lock(slMutexPtr);
    if(gOneTimeAfterLockDelay > 0)
    {
        if(gDelaySkipCnt > 0)
        {
            gDelaySkipCnt--;
        }
        else 
        {
            delayValue = gOneTimeAfterLockDelay * 1000000;    // usec
            gOneTimeAfterLockDelay = 0;
            sssl_log(LOGLEV_debug,"mira: {%s} - after lock, before sleeping",__FUNCTION__);
            usleep(delayValue);
            sssl_log(LOGLEV_debug,"mira: {%s} - after lock, after sleeping",__FUNCTION__);
        }
    }
    // ***

    return(retv);
}


int simpleLockTryGet(pthread_mutex_t * slMutexPtr)
{
    int retv = pthread_mutex_trylock(slMutexPtr);
    return(retv);
}


int simpleLockRelease(pthread_mutex_t * slMutexPtr)
{
    // ***
    // int retv = pthread_mutex_unlock(slMutexPtr);
    // ***
    int delayValue;
    if(gOneTimeBeforeUnlockDelay > 0)
    {
        if(gDelaySkipCnt > 0)
        {
            gDelaySkipCnt--;
        }
        else 
        {
            delayValue = gOneTimeBeforeUnlockDelay * 1000000;    // usec
            gOneTimeBeforeUnlockDelay = 0;
            sssl_log(LOGLEV_debug,"mira: {%s} - before unlock, before sleeping",__FUNCTION__);
            usleep(delayValue);
            sssl_log(LOGLEV_debug,"mira: {%s} - before unlock, after sleeping",__FUNCTION__);
        }
    }
    int retv = pthread_mutex_unlock(slMutexPtr);
    if(gOneTimeAfterUnlockDelay > 0)
    {
        if(gDelaySkipCnt > 0)
        {
            gDelaySkipCnt--;
        }
        else 
        {
            delayValue = gOneTimeAfterUnlockDelay * 1000000;    // usec
            gOneTimeAfterUnlockDelay = 0;
            sssl_log(LOGLEV_debug,"mira: {%s} - after unlock, before sleeping",__FUNCTION__);
            usleep(delayValue);
            sssl_log(LOGLEV_debug,"mira: {%s} - after unlock, after sleeping",__FUNCTION__);
        }
    }
    // ***

    return(retv);
}


// --- shared context storage management facility ---

void sssl_contextStorageInitialize()
{
    int nn;

    memset(gSSLContextStorage,0,sizeof(gSSLContextStorage));
    for(nn=0; nn<SSL_CONTEXT_STORAGE_SIZE; nn++)
    {
        gSSLContextStorage[nn].streamID = -1;
    }
}


void * sssl_contextCreate(unsigned long long sessionID)
{
    int nn,retv;
    SHARED_SSL_CONTEXT * retp;

    retv = sssl_getIndexWithSessionID((unsigned long long)sessionID);
    if(retv >= 0)
    {
        sssl_log(LOGLEV_error,"mira: sssl_contextCreate() - context already exists for sessionID = 0x%llx",sessionID);
        return(NULL);
    }

    for(nn=0; nn<SSL_CONTEXT_STORAGE_SIZE; nn++)
    {
        if(gSSLContextStorage[nn].inUse == 0)
        {
            gSSLContextStorage[nn].streamID = -1;
            gSSLContextStorage[nn].sessionID = sessionID;
            gSSLContextStorage[nn].inUse = 1;
            gSSLContextStorage[nn].ssslContext.ready = 0;
            return((void *)&(gSSLContextStorage[nn].ssslContext));
        }
    }

   return(NULL);
}


int sssl_contextRemove(unsigned long long sessionID)
{
    int index = sssl_getIndexWithSessionID(sessionID);
    if(index < 0)
    {
        return(-1);
    }

    gSSLContextStorage[index].streamID = -1;
    gSSLContextStorage[index].sessionID = (unsigned long long)0;
    gSSLContextStorage[index].inUse = 0;
    gSSLContextStorage[index].ssslContext.ready = 0;



    // ***
    gCurrentSessionID = 0;
    gCurrentStreamID = -1;
    gDelaySkipCnt = 1;



    return(0);
}


void * sssl_getContextWithSessionID(unsigned long long sessionID)
{
    if(!gCommonSSLServerContext)
        return(NULL);

    int index = sssl_getIndexWithSessionID(sessionID);
    if(index < 0)
    {
        return(NULL);
    }

    return((void *)&(gSSLContextStorage[index].ssslContext));
}

void * sssl_getContextWithStreamID(int streamID)
{
    if(!gCommonSSLServerContext)
        return(NULL);

    int index = sssl_getIndexWithStreamID(streamID);
    if(index < 0)
    {
        return(NULL);
    }

    return((void *)&(gSSLContextStorage[index].ssslContext));
}


int sssl_setContextStreamID(unsigned long long sessionID,int streamID)
{
    if(!gCommonSSLServerContext)
        return(-1);

    int index = sssl_getIndexWithSessionID(sessionID);
    if(index < 0)
    {
        return(-1);
    }



    // ***
    gCurrentSessionID = sessionID;
    gCurrentStreamID = streamID;



    if(gSSLContextStorage[index].streamID != -1)
    {
        sssl_log(LOGLEV_warning,"mira: sssl_setContextStreamID() - overwriting streamID %d with %d for sessionID 0x%llx",
            gSSLContextStorage[index].streamID,streamID,sessionID);
    }
    gSSLContextStorage[index].streamID = streamID;

    return(0);
}


int sssl_getIndexWithSessionID(unsigned long long sessionID)
{
    int nn;

    for(nn=0; nn<SSL_CONTEXT_STORAGE_SIZE; nn++)
    {
        if((gSSLContextStorage[nn].inUse != 0) && (gSSLContextStorage[nn].sessionID == sessionID))
        {
            return(nn);
        }
    }

   return(-1);
}


int sssl_getIndexWithStreamID(int streamID)
{
    int nn;

    for(nn=0; nn<SSL_CONTEXT_STORAGE_SIZE; nn++)
    {
        if((gSSLContextStorage[nn].inUse != 0) && (gSSLContextStorage[nn].streamID == streamID))
        {
            return(nn);
        }
    }

   return(-1);
}


// --- local functions ---

SSL_CTX * CreateNewSSLContext()
{
    SSL_CTX* ctx = NULL;

    ctx = InitServerCTX();

    if (ctx)
    {
        int ret = LoadCertificates(ctx, CRESTRON_SERV_CERTIFICATE_FILE, CRESTRON_SERV_PRIVATEKEY_FILE);
        if (ret == false)
        {
            SSL_CTX_free(ctx);
            ctx = NULL;
            sssl_log(LOGLEV_error,"mira: CreateNewSSLContext server load certificate failed");
        }
    }

    sssl_log(LOGLEV_debug,"mira: CreateNewSSLContext ret[0x%x]\n", ctx);
    return ctx;
}


SSL_CTX * InitServerCTX()
{
    SSL_CTX *ctx = NULL;

    const SSL_METHOD * method = DTLS_server_method();
    ctx = SSL_CTX_new(method);

    //Note: copy from GStreamer/gst-plugins-bad/blob/master/ext/dtls/gstdtlsagent.c
    if(ctx)
    {
        SSL_CTX_set_verify_depth (ctx, 2);
        SSL_CTX_set_tlsext_use_srtp (ctx, "SRTP_AES128_CM_SHA1_80");
        SSL_CTX_set_cipher_list (ctx,     "ALL:!ADH:!LOW:!EXP:!MD5:@STRENGTH");
        SSL_CTX_set_read_ahead (ctx, 1);
    }

    sssl_log(LOGLEV_debug,"mira: InitServerCTX ret[0x%x]\n", ctx);
    return ctx;
}


bool LoadCertificates(SSL_CTX* ctx, char* CertFile, char* KeyFile)
{
    /* set the local certificate from CertFile */
    if (SSL_CTX_use_certificate_file(ctx, CertFile, SSL_FILETYPE_PEM) <= 0)
    {
        sssl_log(LOGLEV_error,
            "mira: LoadCertificates SSL_CTX_use_certificate_file: ERROR: %s\n",
            ERR_error_string(ERR_get_error(), NULL));
        return false;
    }
    sssl_log(LOGLEV_debug,"mira: InitClientCTX use_certificate_file:%s\n",CertFile);

    /* set the private key from KeyFile (may be the same as CertFile) */
    if (SSL_CTX_use_PrivateKey_file(ctx, KeyFile, SSL_FILETYPE_PEM) <= 0)
    {
        sssl_log(LOGLEV_error,
            "mira: LoadCertificates SSL_CTX_use_PrivateKey_file: ERROR: %s\n",
            ERR_error_string(ERR_get_error(), NULL));
        return false;
    }
    sssl_log(LOGLEV_debug,"mira: InitClientCTX use_PrivateKey_file:%s\n",KeyFile);

    /* verify private key */
    if (!SSL_CTX_check_private_key(ctx))
    {
        sssl_log(LOGLEV_error,"mira: LoadCertificates Private key does not match the public certificate");
        return false;
    }

    SSL_CTX_set_verify(ctx, SSL_VERIFY_NONE, NULL);

    sssl_log(LOGLEV_debug,"mira: LoadCertificates exit");
    return true;
}


#define SSL_WHERE_INFO(ssl, w, flag, msg) {                \
    if(w & flag) {                                         \
        sssl_log(LOGLEV_debug,"+ ssl_info: ");                             \
        sssl_log(LOGLEV_debug,"%20.20s", msg);                             \
        sssl_log(LOGLEV_debug," - %30.30s ", SSL_state_string_long(ssl));  \
        sssl_log(LOGLEV_debug," - %5.10s ", SSL_state_string(ssl));        \
        sssl_log(LOGLEV_debug,"\n");                                       \
    }                                                      \
  }

void ssl_info_callback(const SSL* ssl, int where, int ret) {

    if(ret == 0) {
        sssl_log(LOGLEV_debug,"-- ssl_info_callback: error occured.\n");
        return;
    }

    SSL_WHERE_INFO(ssl, where, SSL_CB_LOOP, "LOOP");
    SSL_WHERE_INFO(ssl, where, SSL_CB_HANDSHAKE_START, "HANDSHAKE START");
    SSL_WHERE_INFO(ssl, where, SSL_CB_HANDSHAKE_DONE, "HANDSHAKE DONE");

    int pending = BIO_ctrl_pending(SSL_get_wbio(ssl));
    sssl_log(LOGLEV_debug,"ssl_info_callback BIO_ctrl_pending[%d],where[0x%x]",pending,where);
    sssl_log(LOGLEV_debug,"ssl_info_callback SSL_get_state return[%d]", SSL_get_state(ssl));
}



#ifdef GEN_KEY

/* Generates a 2048-bit RSA key. */
EVP_PKEY * generate_key()
{
    /* Allocate memory for the EVP_PKEY structure. */
    EVP_PKEY * pkey = EVP_PKEY_new();
    if(!pkey)
    {
        sssl_log(LOGLEV_error,"Unable to create EVP_PKEY structure.");
        return NULL;
    }

    /* Generate the RSA key and assign it to pkey. */
    RSA * rsa = RSA_generate_key(2048, RSA_F4, NULL, NULL);
    if(!EVP_PKEY_assign_RSA(pkey, rsa))
    {
        sssl_log(LOGLEV_error,"Unable to generate 2048-bit RSA key.");
        EVP_PKEY_free(pkey);
        return NULL;
    }

    /* The key has been generated, return it. */
    return pkey;
}

/* Generates a self-signed x509 certificate. */
X509 * generate_x509(EVP_PKEY * pkey)
{
    /* Allocate memory for the X509 structure. */
    X509 * x509 = X509_new();
    if(!x509)
    {
        sssl_log(LOGLEV_error,"Unable to create X509 structure.");
        return NULL;
    }

    /* Set the serial number. */
    ASN1_INTEGER_set(X509_get_serialNumber(x509), 1);

    /* This certificate is valid from now until exactly one year from now. */
    X509_gmtime_adj(X509_get_notBefore(x509), 0);
    X509_gmtime_adj(X509_get_notAfter(x509), 31536000L);

    /* Set the public key for our certificate. */
    X509_set_pubkey(x509, pkey);

    /* We want to copy the subject name to the issuer name. */
    X509_NAME * name = X509_get_subject_name(x509);

    /* Set the country code and common name. */
    X509_NAME_add_entry_by_txt(name, "C",  MBSTRING_ASC, (unsigned char *)"US",            -1, -1, 0);
    X509_NAME_add_entry_by_txt(name, "O",  MBSTRING_ASC, (unsigned char *)"Crestron Inc.", -1, -1, 0);
    X509_NAME_add_entry_by_txt(name, "CN", MBSTRING_ASC, (unsigned char *)"localhost",     -1, -1, 0);

    /* Now set the issuer name. */
    X509_set_issuer_name(x509, name);

    /* Actually sign the certificate with our key. */
    if(!X509_sign(x509, pkey, EVP_sha1()))
    {
        sssl_log(LOGLEV_error,"Error signing certificate.");
        X509_free(x509);
        return NULL;
    }

    return x509;
}

bool write_to_disk(EVP_PKEY * pkey, X509 * x509)
{
    /* Open the PEM file for writing the key to disk. */
    FILE * pkey_file = fopen(CRESTRON_SERV_PRIVATEKEY_FILE, "wb");
    if(!pkey_file)
    {
        sssl_log(LOGLEV_error,"Unable to open %s",CRESTRON_SERV_PRIVATEKEY_FILE);
        return false;
    }

    /* Write the key to disk. */
    bool ret = PEM_write_PrivateKey(pkey_file, pkey, NULL, NULL, 0, NULL, NULL);
    fclose(pkey_file);

    if(!ret)
    {
        sssl_log(LOGLEV_error,"Unable to write private key to disk.");
        return false;
    }

    /* Open the PEM file for writing the certificate to disk. */
    FILE * x509_file = fopen(CRESTRON_SERV_CERTIFICATE_FILE, "wb");
    if(!x509_file)
    {
        sssl_log(LOGLEV_error,"Unable to open %s",CRESTRON_SERV_CERTIFICATE_FILE);
        return false;
    }

    /* Write the certificate to disk. */
    ret = PEM_write_X509(x509_file, x509);
    fclose(x509_file);

    if(!ret)
    {
        sssl_log(LOGLEV_error,"Unable to write certificate to disk.");
        return false;
    }

    return true;
}
#endif



// ***

int sssl_Test(int arg)
{
    int retv;

    sssl_log(LOGLEV_debug,"mira: {%s} - executing with arg = %d",__FUNCTION__,arg);

    switch(arg)
    {
        case 1:
            gOneTimeBeforeLockDelay    = gDelayValue;
            break;
        case 2:
            gOneTimeAfterLockDelay     = gDelayValue;
            break;
        case 3:
            gOneTimeBeforeUnlockDelay  = gDelayValue;
            break;
        case 4:
            gOneTimeAfterUnlockDelay   = gDelayValue;
            break;
        case 5:
            gDelayValue = 3;
            break;
        case 6:
            gDelaySkipCnt = 1;
            break;
        case 7:
            if(gCurrentStreamID >= 0)
            {
                sssl_log(LOGLEV_debug,"mira: {%s} - calling sssl_cancelDTLSAppThAndWait()",__FUNCTION__);
                retv = sssl_cancelDTLSAppThAndWait(gCurrentStreamID);
                sssl_log(LOGLEV_debug,"mira: {%s} - returned from sssl_waitDTLSAppThCancel(), retv = %d"
                    ,__FUNCTION__,retv);
            }
            else
            {
                sssl_log(LOGLEV_error,"mira: {%s} - invalid gCurrentStreamID",__FUNCTION__);
            }
            break;
        default:
            sssl_log(LOGLEV_error,"mira: {%s} - invalid value of arg - %d",__FUNCTION__,arg);
    }
}
