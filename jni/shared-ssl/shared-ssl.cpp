#include <stdio.h>
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

typedef struct {
   SSL_CTX * common_SSL_CTX;
#ifdef GEN_KEY
   EVP_PKEY * pKey;
   X509 * pX509;
#endif
} COMMON_SSL_SERVER_CONTEXT;

typedef struct {
   int ssslInitialized;
   COMMON_SSL_SERVER_CONTEXT * commonServerContext;
   SSL * ssl;
   BIO * rbio;       // for writing encrypted
   BIO * wbio;       // for reading encrypted
   int appThInitialized;
   // int appThKeepRunning;
   pthread_cond_t appThCancelComplCondVar;
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
        sssl_log(LOGLEV_error,"sssl_initialize: gCommonSSLServerContext is NOT NULL - exiting");
        return(-1);
    }

    COMMON_SSL_SERVER_CONTEXT * sslServerContext
         = (COMMON_SSL_SERVER_CONTEXT *)malloc(sizeof(COMMON_SSL_SERVER_CONTEXT));
    if(!sslServerContext)
    {
        sssl_log(LOGLEV_error,"allocating COMMON_SSL_SERVER_CONTEXT failed.");
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
            sssl_log(LOGLEV_error,"initialize_SSL: generate_key failed.");
            return(-1);
        }
        else
        {
            sslServerContext->pX509 = generate_x509(sslServerContext->pKey);
            if(!sslServerContext->pX509)
            {
                EVP_PKEY_free(sslServerContext->pKey);
                sssl_log(LOGLEV_error,"initialize_SSL: generate_x509 failed.");
                return(-1);
            }
            else
            {
                bool ret = write_to_disk(sslServerContext->pKey, sslServerContext->pX509);
                sssl_log(LOGLEV_debug,"initialize_SSL: writing key and certificate to disk....[%d]",ret);
            }
        }
    }
#endif

    sslServerContext->common_SSL_CTX = CreateNewSSLContext();
    sssl_log(LOGLEV_debug,"initialize_SSL: CreateNewSSLContext return[0x%x]",sslServerContext->common_SSL_CTX);

    gCommonSSLServerContext = sslServerContext;

    return(0);
}


int sssl_deinitialize()
{
    if(!gCommonSSLServerContext)
    {
        sssl_log(LOGLEV_error,"sssl_deinitialize: gCommonSSLServerContext is already NULL");
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
    EVP_PKEY_free(gCommonSSLServerContext->pKey);
    X509_free(gCommonSSLServerContext->pX509);
#endif

    gCommonSSLServerContext = NULL;
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
        sssl_log(LOGLEV_error,"sssl_createDTLS() - could not get SHARED_SSL_CONTEXT");
        return(NULL);
    }
    memset(ssslContext,0,sizeof(SHARED_SSL_CONTEXT));
    // so ssslContext->ssslInitialized == 0

    ssslContext->commonServerContext = gCommonSSLServerContext;
    sssl_log(LOGLEV_debug,"sssl_createDTLS() ssslContext[0x%x] ",ssslContext);

    sssl_log(LOGLEV_debug,"sssl_createDTLS() ssslContext->commonServerContext->common_SSL_CTX = [0x%x] ",
      ssslContext->commonServerContext->common_SSL_CTX);

    if(ssslContext->commonServerContext->common_SSL_CTX)
    {
        SSL * ssl = SSL_new(ssslContext->commonServerContext->common_SSL_CTX);
        ssslContext->ssl = ssl;
        sssl_log(LOGLEV_debug,"sssl_createDTLS() ssl[0x%x] ",ssl);

        //uncomment for the callback
        //SSL_set_info_callback(ssl, ssl_info_callback);

        BIO *rbio = BIO_new(BIO_s_mem());
        BIO_set_close(rbio, BIO_CLOSE);
        //BIO_set_mem_eof_return(rbio, -1);
        BIO_set_nbio(rbio, 1);
        ssslContext->rbio = rbio;
        sssl_log(LOGLEV_debug,"sssl_createDTLS() rbio[0x%x] ",rbio);

        BIO *wbio = BIO_new(BIO_s_mem());
        BIO_set_close(wbio, BIO_CLOSE);
        //BIO_set_mem_eof_return(wbio, -1);
        BIO_set_nbio(wbio, 1);
        ssslContext->wbio = wbio;
        sssl_log(LOGLEV_debug,"sssl_createDTLS() wbio[0x%x] ",wbio);

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
        sssl_log(LOGLEV_error,"sssl_destroyDTLS() - could not get SHARED_SSL_CONTEXT");
        return(-1);
    }

    // Note: do not call BIO_free on rbio and wbio, it is called inside SSL_free().
    if(ssslContext->ssl)
    {
        SSL_free(ssslContext->ssl);
        ssslContext->ssl = NULL;
    }

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

    int index = sssl_getIndexGetWithStreamID(streamID);
    if(index < 0)
    {
        if(doNotLock == 0)
        {
            simpleLockRelease(&gContextStorageMutex);
        }
        sssl_log(LOGLEV_error,"sssl_destroyDTLSWithStreamID() - could not get index with stream ID");
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


void * sssl_getDTLSWithSessionID(unsigned long long sessionID)
{
    simpleLockGet(&gContextStorageMutex);
    void * sssl = sssl_getContextWithSessionID(sessionID);
    simpleLockRelease(&gContextStorageMutex);
    return(sssl);
}


void * sssl_getDTLSWithStreamID(int streamID)
{
    simpleLockGet(&gContextStorageMutex);
    void * sssl = sssl_getContextWithStreamID(streamID);
    simpleLockRelease(&gContextStorageMutex);
    return(sssl);
}


int sssl_encryptDTLS(void * sssl,void * inBuff,int inBuffSize,void * outBuff,int outBuffSize)
{
    // due to the assumed single user, no locking needed

    // This initial implementation makes an optimistic accumption that for each unencrypted 'record'
    // (payload chunk) submitted in inBuff the SSL will make an entire encrypted SSL/TLS record
    // available to BIO_read() right away.


    // make SSL_write() blocking ?!

    SHARED_SSL_CONTEXT * ssslContext = (SHARED_SSL_CONTEXT *)sssl;

    int retv;
    retv = SSL_write(ssslContext->ssl,inBuff,inBuffSize);
    if(retv < 0)
    {
        sssl_log(LOGLEV_error,"ERROR: SSL_write() failed, retv = %d",retv);
        return(retv);
    }
    if(retv != inBuffSize)
    {
        sssl_log(LOGLEV_error,"ERROR: SSL_write() wrote only %d bytes out of requested %d",retv,inBuffSize);
        return(-1);
    }

    retv = BIO_read(ssslContext->wbio,outBuff,outBuffSize);
    if(retv <= 0)
    {
        // treat 0 as failure
        sssl_log(LOGLEV_error,"ERROR: BIO_read() failed, retv = %d",retv);
        return(-1);
    }
    if(retv >= outBuffSize)
    {
        sssl_log(LOGLEV_warning,"WARNING: probably insufficient buffer for BIO_read(), retv = %d, outBuffSize = %d",
            retv,outBuffSize);
    }

    return(retv);
}


int sssl_decryptDTLS(void * sssl,void * inBuff,int inBuffSize,void * outBuff,int outBuffSize)
{
    // due to the assumed single user, no locking needed

    // This initial implementation makes an optimistic accumption that entire SSL/TLS encrypted
    // records are being submitted in inBuff and that under such conditions SSL makes entire
    // decrypted 'records' (payload chunks) available to SSL_read() right away.


    // make SSL_read() blocking ?!

    SHARED_SSL_CONTEXT * ssslContext = (SHARED_SSL_CONTEXT *)sssl;

    int retv;
    retv = BIO_write(ssslContext->rbio,inBuff,inBuffSize);
    if(retv < 0)
    {
        sssl_log(LOGLEV_error,"ERROR: {%s} - BIO_write() failed, retv = %d",__FUNCTION__,retv);
        return(retv);
    }
    if(retv != inBuffSize)
    {
        sssl_log(LOGLEV_error,"ERROR: {%s} - BIO_write() wrote only %d bytes out of requested %d",
            __FUNCTION__,retv,inBuffSize);
        return(-1);
    }

    retv = SSL_read(ssslContext->ssl,outBuff,outBuffSize);
    if(retv <= 0)
    {
        // treat 0 as failure
        sssl_log(LOGLEV_error,"ERROR: {%s} - SSL_read() failed, retv = %d",__FUNCTION__,retv);
        return(-1);
    }
    if(retv >= outBuffSize)
    {
        sssl_log(LOGLEV_debug,"WARNING: {%s} - probably insufficient buffer for SSL_read(), retv = %d, outBuffSize = %d",
            __FUNCTION__,retv,outBuffSize);
    }

    return(retv);
}


int sssl_runDTLSHandshakeWithSecToken(void * sssl,void * secToken,int secTokenLength,
   bool * isDTLSHandshakeCompletePtr,unsigned char ** clientKeyPtr,int dtlsClientKeyLength,
   int * cipherPtr,int * authPtr,DTLS_MSGSENDER sendOutDTLSHandshake,void * arg1,void ** arg2)
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
                const char export_string[] = "EXTRACTOR-dtls_srtp";

                memset(BIO_read_buf,0,sizeof(BIO_read_buf));
                int success = SSL_export_keying_material(
                                 ssslContext->ssl,
                                 // (void *)&exported_keys,60,
                                 (unsigned char *)&exported_keys,60,
                                 export_string, sizeof(export_string),
                                 NULL,0, 0);
                sssl_log(LOGLEV_debug,
                     "mira: sssl_commenceDTLSHandshakeWithSecToken() - SSL_export_keying_material() returned [%d]",success);
                if(!success)
                {
                    sssl_log(LOGLEV_error,
                        "mira: sssl_commenceDTLSHandshakeWithSecToken() - failed to export keying material from openssl");
                    return(-1);
                }

                //this is server side, ssvn git statusg
                // it o we only need to decoder: uses client_key
                client_key.key = exported_keys.client_key;
                server_key.key = exported_keys.server_key;
                client_key.salt = exported_keys.client_salt;
                server_key.salt = exported_keys.server_salt;

                //send out key &client_key
                unsigned char * dtls_client_key = (unsigned char *)malloc(dtlsClientKeyLength);
                if(!dtls_client_key)
                {
                    sssl_log(LOGLEV_error,
                        "sssl_commenceDTLSHandshakeWithSecToken() - failed to allocate dtls_client_key");
                    return(-1);
                }
                else
                {
                    memset(dtls_client_key,0,dtlsClientKeyLength);

                    sprintf((char *)dtls_client_key, "%02X%02X%02X%02X%02X%02X%02X%02X%02X%02X%02X%02X%02X%02X%02X%02X",
                            client_key.key.v[0], client_key.key.v[1], client_key.key.v[2], client_key.key.v[3],
                            client_key.key.v[4], client_key.key.v[5], client_key.key.v[6], client_key.key.v[7],
                            client_key.key.v[8], client_key.key.v[9], client_key.key.v[10], client_key.key.v[11],
                            client_key.key.v[12], client_key.key.v[13], client_key.key.v[14], client_key.key.v[15]);

                    int stringLen = strlen((char *)dtls_client_key);

                    sprintf((char *)&dtls_client_key[stringLen], "%02X%02X%02X%02X%02X%02X%02X%02X%02X%02X%02X%02X%02X%02X",
                            client_key.salt.v[0], client_key.salt.v[1], client_key.salt.v[2], client_key.salt.v[3],
                            client_key.salt.v[4], client_key.salt.v[5], client_key.salt.v[6], client_key.salt.v[7],
                            client_key.salt.v[8], client_key.salt.v[9], client_key.salt.v[10], client_key.salt.v[11],
                            client_key.salt.v[12], client_key.salt.v[13]);

                    *clientKeyPtr = dtls_client_key;
                    *isDTLSHandshakeCompletePtr = true;

                    sssl_log(LOGLEV_debug,
                        "mira: sssl_commenceDTLSHandshakeWithSecToken() - set isDTLSHandshakeComplete flag to true");

                }

                //send out dtls srtp Auth
                SRTP_PROTECTION_PROFILE * profile = SSL_get_selected_srtp_profile(ssslContext->ssl);
                sssl_log(LOGLEV_debug,
                     "mira: sssl_commenceDTLSHandshakeWithSecToken() - srtp profile is [%d]",profile);
                *cipherPtr = MS_MICE_SINK_SESSION_DTLS_CIPHER_AES_128_ICM;
                *authPtr = MS_MICE_SINK_SESSION_DTLS_AUTH_HMAC_SHA1_80;
                if(profile)
                {
                    if(profile->name)
                        sssl_log(LOGLEV_debug,
                           "mira: sssl_commenceDTLSHandshakeWithSecToken() - profile name is [%s]",profile->name);
                    switch (profile->id)
                    {
                        case SRTP_AES128_CM_SHA1_80:
                        {
                            *cipherPtr = MS_MICE_SINK_SESSION_DTLS_CIPHER_AES_128_ICM;
                            *authPtr = MS_MICE_SINK_SESSION_DTLS_AUTH_HMAC_SHA1_80;
                            break;
                        }
                        case SRTP_AES128_CM_SHA1_32:
                        {
                            *cipherPtr = MS_MICE_SINK_SESSION_DTLS_CIPHER_AES_128_ICM;
                            *authPtr = MS_MICE_SINK_SESSION_DTLS_AUTH_HMAC_SHA1_32;
                          break;
                        }
                        default:
                        {
                            sssl_log(LOGLEV_error,
                                 "mira: sssl_commenceDTLSHandshakeWithSecToken() - invalid crypto suite set by handshake");
                            break;
                        }
                    }
                }
                else
                {
                    sssl_log(LOGLEV_debug,
                        "mira: sssl_commenceDTLSHandshakeWithSecToken() - no SRTP protection profile was negotiated");
                }
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

int sssl_setDTLSAppThInitialized(int streamID, int flagValue)
{
    // return values:
    //  >= 0 - appThInitialized was set as requested. Its previous value is returned.
    //  -1 - corresponding sssl did not exist when this function was invoked.

    int retv;

    simpleLockGet(&gContextStorageMutex);
    void * sssl = sssl_getContextWithStreamID(streamID);
    if(sssl != NULL)
    {
        retv = ((SHARED_SSL_CONTEXT *)sssl)->appThInitialized;
        ((SHARED_SSL_CONTEXT *)sssl)->appThInitialized = ((flagValue != 0) ? 1 : 0);
    }
    else
    {
        retv = -1;
    }
    simpleLockRelease(&gContextStorageMutex);


    // !!!
    // if returning 0 when media thread sets it back to 0, media thread must not destroy
    // sssl upon exit
    // !!!


    return(retv);
}


int sssl_getDTLSAppThInitialized(int streamID)
{
    // return values:
    //  >= 0 - value of appThInitialized
    //  -1 - corresponding sssl did not exist when this function was invoked.

    int retv;

    simpleLockGet(&gContextStorageMutex);
    void * sssl = sssl_getContextWithStreamID(streamID);
    if(sssl != NULL)
    {
        retv = ((SHARED_SSL_CONTEXT *)sssl)->appThInitialized;
    }
    else
    {
        retv = -1;
    }
    simpleLockRelease(&gContextStorageMutex);

    return(retv);
}


int sssl_waitDTLSAppThCancel(unsigned long long sessionID)
{
    // return values:
    //   0 - normal return. Media thread cancelection was indeed initiated by this function
    //       and it successfully completed.
    //   1 - Media thread cancelection was already in progress when this function was invoked,
    //       nevertheless, it successfully completed.
    //   2 - Media thread was already canceled when this function was invoked.
    //  -1 - general error.
    //  -2 - Media thread cancelection was indeed initiated by this function but it did not
    //       complete before timeout.
    //  -3 - Media thread cancelection was already in progress when this function was invoked
    //       and it did not complete before timeout.

    int retv,retv1;

    simpleLockGet(&gContextStorageMutex);

    retv = 2;
    void * sssl = sssl_getContextWithSessionID(sessionID);
    if(sssl != NULL)
    {
        retv = 1;

        if(((SHARED_SSL_CONTEXT *)sssl)->appThInitialized != 0)
        {
            ((SHARED_SSL_CONTEXT *)sssl)->appThInitialized = 0;
            retv = 0;
        }

	     struct timespec stopTimeout;
        stopTimeout.tv_sec += 10;
	     // ! pthread_mutex_lock(&gContextStorageMutex);
	     retv1 = pthread_cond_timedwait(&((SHARED_SSL_CONTEXT *)sssl)->appThCancelComplCondVar,
            &gContextStorageMutex, &stopTimeout);
        // ! pthread_mutex_unlock(&gContextStorageMutex);

        if((retv == 0) && (retv1 == 0))         // play safe
        {
            // destroy sssl here only if media thread cancelection was indeed initiated by
            // this function
            sssl_destroyDTLSWithSessionID(sessionID,1);
        }

	     if(retv1 != 0)
        {
	         if(retv1 == ETIMEDOUT)
            {
                retv = ((retv == 0) ? -2 : -3);
            }
            else
            {
                retv = -1;
            }
        }
    }

    simpleLockRelease(&gContextStorageMutex);
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


int sssl_signalDTLSAppThCanceled(int streamID)
{
    simpleLockGet(&gContextStorageMutex);
    void * sssl = sssl_getContextWithStreamID(streamID);
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
    int retv = pthread_mutex_lock(slMutexPtr);
    return(retv);
}


int simpleLockTryGet(pthread_mutex_t * slMutexPtr)
{
    int retv = pthread_mutex_trylock(slMutexPtr);
    return(retv);
}


int simpleLockRelease(pthread_mutex_t * slMutexPtr)
{
    int retv = pthread_mutex_unlock(slMutexPtr);
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

    retv = sssl_getIndexGetWithSessionID((unsigned long long)sessionID);
    if(retv >= 0)
    {
        sssl_log(LOGLEV_error,"sssl_contextCreate() - context already exists for sessionID = %" G_GUINT64_FORMAT,
            sessionID);
        return(NULL);
    }

    for(nn=0; nn<SSL_CONTEXT_STORAGE_SIZE; nn++)
    {
        if(gSSLContextStorage[nn].inUse == 0)
        {
            gSSLContextStorage[nn].streamID = -1;
            gSSLContextStorage[nn].sessionID = sessionID;
            gSSLContextStorage[nn].inUse = 1;
            return((void *)&(gSSLContextStorage[nn].ssslContext));
        }
    }

   return(NULL);
}


int sssl_contextRemove(unsigned long long sessionID)
{
    int index = sssl_getIndexGetWithSessionID(sessionID);
    if(index < 0)
    {
        return(-1);
    }

    gSSLContextStorage[index].streamID = -1;
    gSSLContextStorage[index].sessionID = (unsigned long long)0;
    gSSLContextStorage[index].inUse = 0;

    return(0);
}


void * sssl_getContextWithSessionID(unsigned long long sessionID)
{
    int index = sssl_getIndexGetWithSessionID(sessionID);
    if(index < 0)
    {
        return(NULL);
    }

    return((void *)&(gSSLContextStorage[index].ssslContext));
}

void * sssl_getContextWithStreamID(int streamID)
{
    int index = sssl_getIndexGetWithStreamID(streamID);
    if(index < 0)
    {
        return(NULL);
    }

    return((void *)&(gSSLContextStorage[index].ssslContext));
}


int sssl_setContextStreamID(unsigned long long sessionID,int streamID)
{
    int index = sssl_getIndexGetWithSessionID(sessionID);
    if(index < 0)
    {
        return(-1);
    }

    if(gSSLContextStorage[index].streamID != -1)
    {
        sssl_log(LOGLEV_warning,"mira: sssl_setContextStreamID() - overwriting streamID %d with %d for sessionID %" G_GUINT64_FORMAT,
           gSSLContextStorage[index].streamID,streamID,sessionID);
    }
    gSSLContextStorage[index].streamID = streamID;

    return(0);
}


int sssl_getIndexGetWithSessionID(unsigned long long sessionID)
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


int sssl_getIndexGetWithStreamID(int streamID)
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
            sssl_log(LOGLEV_error,"CreateNewSSLContext server load certificate failed");
        }
    }

    sssl_log(LOGLEV_debug,"CreateNewSSLContext ret[0x%x]\n", ctx);
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

    sssl_log(LOGLEV_debug,"InitServerCTX ret[0x%x]\n", ctx);
    return ctx;
}


bool LoadCertificates(SSL_CTX* ctx, char* CertFile, char* KeyFile)
{
    /* set the local certificate from CertFile */
    if (SSL_CTX_use_certificate_file(ctx, CertFile, SSL_FILETYPE_PEM) <= 0)
    {
        sssl_log(LOGLEV_error,
            "LoadCertificates SSL_CTX_use_certificate_file: ERROR: %s\n",
            ERR_error_string(ERR_get_error(), NULL));
        return false;
    }
    sssl_log(LOGLEV_debug,"InitClientCTX use_certificate_file:%s\n",CertFile);

    /* set the private key from KeyFile (may be the same as CertFile) */
    if (SSL_CTX_use_PrivateKey_file(ctx, KeyFile, SSL_FILETYPE_PEM) <= 0)
    {
        sssl_log(LOGLEV_error,
            "LoadCertificates SSL_CTX_use_PrivateKey_file: ERROR: %s\n",
            ERR_error_string(ERR_get_error(), NULL));
        return false;
    }
    sssl_log(LOGLEV_debug,"InitClientCTX use_PrivateKey_file:%s\n",KeyFile);

    /* verify private key */
    if (!SSL_CTX_check_private_key(ctx))
    {
        sssl_log(LOGLEV_error,"LoadCertificates Private key does not match the public certificate");
        return false;
    }

    SSL_CTX_set_verify(ctx, SSL_VERIFY_NONE, NULL);

    sssl_log(LOGLEV_debug,"LoadCertificates exit");
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



