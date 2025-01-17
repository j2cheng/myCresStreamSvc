//#include <support/android-miraclecast-common.h>
//#include <shl_log.h>
#include "shared/glib-utilities.h"
#include <sys/socket.h>
#include "ms-mice.h"
#include "ms-mice-sink-session.h"
#include <stdio.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <netdb.h>
#include "openssl/sha.h"
#include <netinet/tcp.h>

#include "../shared-ssl/shared-ssl.h"

#define MS_MICE_SINK_SESSION_DTLS_KEY_LENGTH_STRING_LEN ((DTLS_KEY_LEN + DTLS_SALT_LEN)*2 + 1)
#define MS_MICE_SESSION_ESTABLISHMENT_TIMEOUT_SECONDS (5 * 60) /* units in seconds */

struct _ms_mice_sink_session_private {
    ms_mice_sink_service *service;
    guint64 session_id;

#ifdef API_SUPPORTED
    sink_api_session *api_session_;
#endif

    MS_MICE_SINK_SESSION_STATE state;
    bool security_options_display_pin;
    bool security_options_use_dtls_encryption;
    bool is_dtls_encryption_handshake_complete;

    guint16 rtsp_port;
    const char* friendly_name;
    const char* source_id;
    guint8 source_id_raw[MS_MICE_TLV_SOURCE_ID_LENGTH];

    const gchar* local_address;
    const gchar* remote_address;

    /* socket */
    GSocketConnection *source_connection;
    GIOChannel *source_channel;
    int source_fd;
    guint source_fd_id;
    guint source_establishment_timeout;

    /* incoming stream */
    GInputStream *base_in_stream;
    GDataInputStream *in_stream;
    bool in_message_active;
    ms_mice_message *in_message;

    /* outgoing messages */
    GOutputStream *base_out_stream;
    GDataOutputStream *out_stream;
    struct shl_dlist outgoing;

    ms_mice_sink_session_observer *observer;
    gpointer observer_data;
};

void ssl_send_DTLS_handshake(void * session,char * dtlsData,int dtlsDataLen,void ** error);
void ssl_write_to_BIO_and_check_output(ms_mice_sink_session *session, ms_mice_tlv *tlv, GError **error);
static void ms_mice_sink_session_queue_outgoing_message(ms_mice_sink_session *session, ms_mice_message_entry *entry);
static int ms_mice_sink_session_write(ms_mice_sink_session *session, GError **error, bool needEncrypt = false);

#ifdef API_SUPPORTED
sink_api_session *ms_mice_sink_session_get_api(ms_mice_sink_session *session) { return session->priv->api_session_; }
void ms_mice_sink_session_set_api(ms_mice_sink_session *session, sink_api_session *api_session) { session->priv->api_session_ = api_session; }
sink_api_session *ms_mice_sink_session_update_api(ms_mice_sink_session *ms_session, guint16 rtsp_port)
{
    sink_api_session *api_session = ms_mice_sink_session_get_api(ms_session);
    sink_api_session_state_property_set(api_session, SINK_SESSION_CONNECTION_STATE_CONNECTED);
    sink_api_session_type_property_set(api_session, SINK_SESSION_TYPE_MS_MICE_INFRASTRUCTURE);
    sink_api_session_local_address_property_set(api_session, ms_mice_sink_session_get_local_address(ms_session));
    sink_api_session_remote_address_property_set(api_session, ms_mice_sink_session_get_remote_address(ms_session));
    sink_api_session_remote_device_id_property_set(api_session, ms_mice_sink_session_get_source_id(ms_session));
    sink_api_session_remote_name_property_set(api_session, ms_mice_sink_session_get_friendly_name(ms_session));
    sink_api_session_remote_rtsp_port_property_set(api_session, rtsp_port);
    return api_session;
}
#endif

bool ms_mice_sink_session_is_connected(ms_mice_sink_session *session) { return session && session->priv->state != MS_MICE_SINK_SESSION_STATE_DISCONNECTED; }
guint64 ms_mice_sink_session_get_id(ms_mice_sink_session *session) { return session->priv->session_id; }
MS_MICE_SINK_SESSION_STATE ms_mice_sink_session_get_state(ms_mice_sink_session *session) { return session->priv->state; }
const char *ms_mice_sink_session_get_friendly_name(ms_mice_sink_session *session) { return session->priv->friendly_name; }
const char *ms_mice_sink_session_get_source_id(ms_mice_sink_session *session) { return session->priv->source_id; }
const char *ms_mice_sink_session_get_local_address(ms_mice_sink_session *session) { return session->priv->local_address; }
const char *ms_mice_sink_session_get_remote_address(ms_mice_sink_session *session) { return session->priv->remote_address; }
bool ms_mice_sink_session_get_display_pin_option(ms_mice_sink_session *session) { return session->priv->security_options_display_pin; }
bool ms_mice_sink_session_get_use_dtls_encryption_option(ms_mice_sink_session *session) { return session->priv->security_options_use_dtls_encryption; }

const char *ms_mice_sink_session_state_to_string(MS_MICE_SINK_SESSION_STATE state)
{
    switch (state) {
        case MS_MICE_SINK_SESSION_STATE_DISCONNECTED:
            return "disconnected";
        case MS_MICE_SINK_SESSION_STATE_CONNECTED:
            return "connected";
        case MS_MICE_SINK_SESSION_STATE_SESSION_REQUESTED:
            return "session-requested";
        case MS_MICE_SINK_SESSION_STATE_WAITING_FOR_SECURITY_HANDSHAKE:
            return "security-handshake-waiting";
        case MS_MICE_SINK_SESSION_STATE_DTLS_HANDSHAKE:
            return "dtls-handshake";
        case MS_MICE_SINK_SESSION_STATE_DTLS_HANDSHAKE_COMPLETE:
            return "dtls-handshake-complete";
        case MS_MICE_SINK_SESSION_STATE_PIN_WAITING:
            return "pin-waiting";
        case MS_MICE_SINK_SESSION_STATE_PIN_CHALLENGE:
            return "pin-challenge";
        case MS_MICE_SINK_SESSION_STATE_SOURCE_READY:
            return "source-ready";
        case MS_MICE_SINK_SESSION_STATE_PROJECTION:
            return "projecting";
        default:
            break;
    }

    return "state-unknown";
}

static void ms_mice_sink_session_stop_establishment_timeout(ms_mice_sink_session *session)
{
    if (!session->priv->source_establishment_timeout)
        return;

    GMainContext* context = ms_mice_sink_service_get_context(session->priv->service);
    CSIO_LOG(eLogLevel_debug,"stop_establishment_timeout { \"context\": 0x%x }" , context);

    if(context)
    {
        GSource * source = g_main_context_find_source_by_id (context,session->priv->source_establishment_timeout);
        CSIO_LOG(eLogLevel_debug,"stop_establishment_timeout { \"destroy source\": 0x%x }" , source);

        if(source)
            g_source_destroy(source);
    }
    else
    {
        g_source_remove(session->priv->source_establishment_timeout);
        CSIO_LOG(eLogLevel_debug,"stop_establishment_timeout { \"remove source id\": 0x%x }" , session->priv->source_establishment_timeout);
    }

    session->priv->source_establishment_timeout = 0;
}

static bool ms_mice_sink_session_validate_pin(ms_mice_sink_session *session,ms_mice_tlv* tlv)
{
    SHA256_CTX sha256;
    SHA256_Init(&sha256);
    unsigned char hash[SHA256_DIGEST_LENGTH];
    unsigned int ipAddr = 0;

    char tmp[256] ;
    const char* remoteAddrPtr = ms_mice_sink_session_get_remote_address(session);//10.254.46.247 c0000264
    const char* SessionPinPtr = ms_mice_sink_service_get_session_pin(session->priv->service);
    int sessionPinLen = strlen(SessionPinPtr);

    CSIO_LOG(eLogLevel_debug,"ms_mice_sink_session_validate_pin session->priv->service[0x%x],SessionPinPtr[0x%x]",session->priv->service,SessionPinPtr);

    if(!remoteAddrPtr || !SessionPinPtr || (sessionPinLen == 0))
    {
        CSIO_LOG(eLogLevel_debug,"ms_mice_sink_session_validate_pin remoteAddrPtr[0x%x] or SessionPinPtr[0x%x][0x%x][0x%x][0x%x][%d] is NULL",
                remoteAddrPtr,SessionPinPtr, SessionPinPtr[0],SessionPinPtr[1],SessionPinPtr[2],SessionPinPtr[3],sessionPinLen);
        return false;
    }    
    
    //insert string of pin into buffer
    for(int i = 0 ; i < sessionPinLen; i++)
    {
        tmp[i] = SessionPinPtr[i];
    }
    
    //convert ip address from string to int
    ms_mice_sink_convertIP((char*)remoteAddrPtr, ipAddr);

    //insert ip address here
    tmp[sessionPinLen++] = (ipAddr & 0xff000000) >> 24;
    tmp[sessionPinLen++] = (ipAddr & 0xff0000)   >> 16;
    tmp[sessionPinLen++] = (ipAddr & 0xff00)     >> 8;
    tmp[sessionPinLen++] = (ipAddr & 0xff)  ;    
    
    SHA256_Update(&sha256, tmp, sessionPinLen);
    SHA256_Final(hash, &sha256);

    for(int i = 0; i < SHA256_DIGEST_LENGTH; i++)
    {
        if( tlv->pin_challenge.pin[i] != hash[i])
            return false;
    }

    return true;
}
static bool ms_mice_sink_session_generate_pin_challenge(ms_mice_sink_session *session,unsigned char* hashbuf)
{
    SHA256_CTX sha256;
    SHA256_Init(&sha256);
    unsigned int ipAddr = 0;
    char tmp[256] ;

    if(!session)
        return false;

    const char* localAddrPtr = ms_mice_sink_session_get_local_address(session);//10.254.45.69 0afe2d45
    const char* SessionPinPtr = ms_mice_sink_service_get_session_pin(session->priv->service);
    int sessionPinLen = strlen(SessionPinPtr);

    if(!localAddrPtr || !SessionPinPtr || !hashbuf|| (sessionPinLen == 0))
    {
        CSIO_LOG(eLogLevel_debug,"ms_mice_sink_session_generate_pin_challenge remoteAddrPtr[0x%x] or SessionPinPtr[0x%x][%d] is NULL",
                localAddrPtr,SessionPinPtr, sessionPinLen);
        return false;
    }

    //insert string of pin into buffer
    for(int i = 0 ; i < sessionPinLen; i++)
    {
        tmp[i] = SessionPinPtr[i];
    }

    //convert ip address from string to int
    ms_mice_sink_convertIP((char*)localAddrPtr, ipAddr);

    //insert ip address here
    tmp[sessionPinLen++] = (ipAddr & 0xff000000) >> 24;
    tmp[sessionPinLen++] = (ipAddr & 0xff0000)   >> 16;
    tmp[sessionPinLen++] = (ipAddr & 0xff00)     >> 8;
    tmp[sessionPinLen++] = (ipAddr & 0xff)  ;

    SHA256_Update(&sha256, tmp, sessionPinLen);
    SHA256_Final(hashbuf, &sha256);

    return true;
}
/* ------------------------------------------------------------------------------------------------------------------
 * -- MS-MICE SINK SESSION RAISE EVENTS
 * -- */

static void ms_mice_sink_session_raise_on_session_disconnected(ms_mice_sink_session *session)
{
    ms_mice_sink_session_observer *observer = session->priv->observer;
    gpointer data = session->priv->observer_data;
    if (observer) {
        void (*func)(ms_mice_sink_session *, gpointer) = observer->on_session_disconnected;
        if (func)
            func(session, data);
    }
}

static void ms_mice_sink_session_raise_on_session_state_changed(ms_mice_sink_session *session, MS_MICE_SINK_SESSION_STATE from, MS_MICE_SINK_SESSION_STATE to)
{
    ms_mice_sink_session_observer *observer = session->priv->observer;
    gpointer data = session->priv->observer_data;
    if (observer) {
        void (*func)(ms_mice_sink_session *, MS_MICE_SINK_SESSION_STATE, MS_MICE_SINK_SESSION_STATE, gpointer) = observer->on_session_state_changed;
        if (func)
            func(session, from, to, data);
    }
}

static void ms_mice_sink_session_raise_on_session_source_id_set(ms_mice_sink_session *session, const char* source_id)
{
    ms_mice_sink_session_observer *observer = session->priv->observer;
    gpointer data = session->priv->observer_data;
    if (observer) {
        void (*func)(ms_mice_sink_session *, const char *, gpointer) = observer->on_session_source_id_set;
        if (func)
            func(session, source_id, data);
    }
}

static void ms_mice_sink_session_raise_on_session_source_friendly_name_set(ms_mice_sink_session *session, const char* friendly_name)
{
    ms_mice_sink_session_observer *observer = session->priv->observer;
    gpointer data = session->priv->observer_data;
    if (observer) {
        void (*func)(ms_mice_sink_session *, const char *, gpointer) = observer->on_session_source_friendly_name_set;
        if (func)
            func(session, friendly_name, data);
    }
}

static void ms_mice_sink_session_raise_on_session_source_ready(ms_mice_sink_session *session, guint16 rtsp_port)
{
    ms_mice_sink_session_observer *observer = session->priv->observer;
    gpointer data = session->priv->observer_data;
    if (observer) {
        void (*func)(ms_mice_sink_session *, guint16, gpointer) = observer->on_session_source_ready;
        if (func)
            func(session, rtsp_port, data);
    }
}

static void ms_mice_sink_session_raise_on_session_stop_projection(ms_mice_sink_session *session)
{
    ms_mice_sink_session_observer *observer = session->priv->observer;
    gpointer data = session->priv->observer_data;
    if (observer) {
        void (*func)(ms_mice_sink_session *, gpointer) = observer->on_session_stop_projecting;
        if (func)
            func(session, data);
    }
}

/* ------------------------------------------------------------------------------------------------------------------
 * -- MS-MICE SINK SESSION PROPERTIES
 * -- */

#define ALWAYS_UPDATE_FRIENDLY_NAME false

static void ms_mice_sink_session_message_update_friendly_name(ms_mice_sink_session *session, ms_mice_message *msg)
{
    ms_mice_tlv *tlv = ms_mice_tlv_find(&msg->tlvs, MS_MICE_TLV_FRIENDLY_NAME);
    if (!tlv)
        return;

    if (session->priv->friendly_name) {
#if ALWAYS_UPDATE_FRIENDLY_NAME
        g_free((gpointer) session->priv->friendly_name);
        session->priv->friendly_name = NULL;
#else
        return;
#endif /* ALWAYS_UPDATE_FRIENDLY_NAME */
    }

    session->priv->friendly_name = ms_mice_tlv_friendly_name_to_utf8(tlv);

    CSIO_LOG(eLogLevel_debug,"ms.mice.sink.session.message.friendly-name.set { \"session-id\": %" G_GUINT64_FORMAT " , \"local-address\": \"%s\" , \"remote-address\": \"%s\" , \"state\": \"%s\" , \"friendly-name\": \"%s\" }",
             session->priv->session_id, session->priv->local_address, session->priv->remote_address, ms_mice_sink_session_state_to_string(session->priv->state), session->priv->friendly_name);

    ms_mice_sink_session_raise_on_session_source_friendly_name_set(session, session->priv->friendly_name);
}

static void ms_mice_sink_session_tlv_update_source_id(ms_mice_sink_session *session, ms_mice_tlv *tlv)
{
    session->priv->source_id = ms_mice_tlv_source_id_to_string(tlv);

    for (int i = 0; i < MS_MICE_TLV_SOURCE_ID_LENGTH; i++) {
        session->priv->source_id_raw[i] = tlv->source_id.value[i];
    }

    CSIO_LOG(eLogLevel_debug,"ms.mice.sink.session.message.source-id.set { \"session-id\": %" G_GUINT64_FORMAT " , \"local-address\": \"%s\" , \"remote-address\": \"%s\" , \"state\": \"%s\" , \"source-id\": \"%s\" }",
             session->priv->session_id, session->priv->local_address, session->priv->remote_address, ms_mice_sink_session_state_to_string(session->priv->state), session->priv->source_id);

    ms_mice_sink_session_raise_on_session_source_id_set(session, session->priv->source_id);
}

/* ------------------------------------------------------------------------------------------------------------------
 * -- MS-MICE SINK SESSION VALIDATION
 * -- */

static inline bool ms_mice_sink_session_validate(ms_mice_sink_session *session, GError **error)
{
    if (!session) {
        if (error) {
            *error = g_error_new(G_IO_ERROR, G_IO_ERROR_INVALID_ARGUMENT, "No sink session");
        }
        return false;
    }

    return true;
}

static bool ms_mice_sink_session_message_validate(ms_mice_sink_session *session, ms_mice_message *msg, GError **error)
{
    bool are_equal;
    ms_mice_tlv *tlv = NULL;

    if (!ms_mice_sink_session_validate(session, error))
        return false;

    if (!msg) {
        CSIO_LOG(eLogLevel_error,"ms.mice.sink.msg.error { \"error\": \"no message\" }");
        if (error) {
            *error = g_error_new(G_IO_ERROR, G_IO_ERROR_INVALID_ARGUMENT, "No msg");
        }
        return false;
    }

    if(ms_mice_tlv_find(&msg->tlvs, MS_MICE_TLV_SECURITY_TOKEN))
    {
        return true;
    }

    tlv = ms_mice_tlv_find(&msg->tlvs, MS_MICE_TLV_SOURCE_ID);

    if (!tlv) {
        CSIO_LOG(eLogLevel_warning,"ms.mice.sink.session.message.source-id.error { \"session-id\": %" G_GUINT64_FORMAT " , \"local-address\": \"%s\" , \"remote-address\": \"%s\" , \"state\": \"%s\" , \"error\": \"message contains no source id\" }",
                  session->priv->session_id, session->priv->local_address, session->priv->remote_address, ms_mice_sink_session_state_to_string(session->priv->state));

        return true; // TODO: it may be optional
    }

    /* set the source id */
    if (!session->priv->source_id) {
        if (tlv->length != MS_MICE_TLV_SOURCE_ID_LENGTH) {
            // TODO [RAL] should close the socket
            CSIO_LOG(eLogLevel_error,"ms.mice.sink.session.message.source-id.error { \"session-id\": %" G_GUINT64_FORMAT " , \"local-address\": \"%s\" , \"remote-address\": \"%s\" , \"state\": \"%s\" , \"error\": \"wrong length\" , \"expected\": %u , \"received\": %u }",
                      session->priv->session_id, session->priv->local_address, session->priv->remote_address, ms_mice_sink_session_state_to_string(session->priv->state), MS_MICE_TLV_SOURCE_ID_LENGTH, tlv->length);
            if (error) {
                *error = g_error_new(G_IO_ERROR, G_IO_ERROR_INVALID_ARGUMENT, "Source ID is invalid, expecting length of %u received %u", MS_MICE_TLV_SOURCE_ID_LENGTH, tlv->length);
            }
            return false;
        }

        ms_mice_sink_session_tlv_update_source_id(session, tlv);

        ms_mice_sink_session_message_update_friendly_name(session, msg);

        return true;
    }

    are_equal = ms_mice_tlv_source_ids_are_equal(&session->priv->source_id_raw[0], MS_MICE_TLV_SOURCE_ID_LENGTH, tlv->source_id.value, tlv->length);

    if (!are_equal) {
        // TODO [RAL] should close the socket
        g_autofree const char *msg_source_id = ms_mice_tlv_source_id_to_string(tlv);
        CSIO_LOG(eLogLevel_error,"ms.mice.sink.session.message.source-id.error { \"session-id\": %" G_GUINT64_FORMAT " , \"local-address\": \"%s\" , \"remote-address\": \"%s\" , \"state\": \"%s\" , \"error\": \"not equal\" , \"expected\": \"%s\" , \"received\": \"%s\" }",
                  session->priv->session_id, session->priv->local_address, session->priv->remote_address, ms_mice_sink_session_state_to_string(session->priv->state), session->priv->source_id, msg_source_id);
        if (error) {
            *error = g_error_new(G_IO_ERROR, G_IO_ERROR_INVALID_ARGUMENT, "Source ID is different, expecting %s received %s", session->priv->source_id, msg_source_id);
        }
    } else {
        ms_mice_sink_session_message_update_friendly_name(session, msg);
    }

    return are_equal;
}

/* ------------------------------------------------------------------------------------------------------------------
 * -- MS-MICE SINK SESSION MESSAGES RECEIVED
 * -- */

static void ms_mice_sink_session_handle_source_ready_message(ms_mice_sink_session *session, ms_mice_message *msg, GError **error)
{
    /*
     * Miracast Source --> Miracast Sink
     *
     * When a Miracast Sink receives a Source Ready message (section 2.2.1) while it is in the Socket
     * Connected, Session Requested, DTLS Handshake Complete, or PIN Challenge state (section 3.1.1), it
     * MUST connect back to the Source over TCP on the RTSP port specified in the message.
     *
     * TLVArray (variable): The following TLVs, included in any order:
     * 3. Source ID TLV (section 2.2.7.3)
     * 1. Friendly Name TLV (section 2.2.7.1) (Not present if the Session Request message (section 2.2.4) was sent)
     * 2. RTSP Port TLV (section 2.2.7.2)
     */
    MS_MICE_SINK_SESSION_STATE old_state = session->priv->state;

    if (old_state == MS_MICE_SINK_SESSION_STATE_CONNECTED)
    {
        const char* SessionPinPtr = ms_mice_sink_service_get_session_pin(session->priv->service);
        if ((SessionPinPtr) && (strlen(SessionPinPtr) != 0))
        {
        	CSIO_LOG(eLogLevel_warning, "ms.mice.sink.session.message.received.source-ready without pin challenge even though pin is set.");
        	if (error)
        		*error = g_error_new(G_IO_ERROR, G_IO_ERROR_PERMISSION_DENIED, "PIN bypassed");
        	return;
        }
    }

    ms_mice_tlv *tlv = ms_mice_tlv_find(&msg->tlvs, MS_MICE_TLV_RTSP_PORT);
    session->priv->rtsp_port = (guint16) (tlv ? tlv->rtsp_port.value : DEFAULT_MIRACAST_RTSP_PORT);

    switch (old_state) {
        case MS_MICE_SINK_SESSION_STATE_CONNECTED:
        case MS_MICE_SINK_SESSION_STATE_SESSION_REQUESTED:
        case MS_MICE_SINK_SESSION_STATE_DTLS_HANDSHAKE_COMPLETE:
        case MS_MICE_SINK_SESSION_STATE_PIN_CHALLENGE:
            break;

        case MS_MICE_SINK_SESSION_STATE_DISCONNECTED:
        case MS_MICE_SINK_SESSION_STATE_WAITING_FOR_SECURITY_HANDSHAKE:
        case MS_MICE_SINK_SESSION_STATE_DTLS_HANDSHAKE:
        case MS_MICE_SINK_SESSION_STATE_PIN_WAITING:
        case MS_MICE_SINK_SESSION_STATE_PROJECTION:
        case MS_MICE_SINK_SESSION_STATE_SOURCE_READY:
        default:
            CSIO_LOG(eLogLevel_error,"ms.mice.sink.session.message.received.source-ready.error { \"session-id\": %" G_GUINT64_FORMAT " , \"local-address\": \"%s\" , \"remote-address\": \"%s\" , \"state\": \"%s\" , \"message\": \"received source-ready message in the wrong state\" }",
                     session->priv->session_id, session->priv->local_address, session->priv->remote_address, ms_mice_sink_session_state_to_string(session->priv->state));
            break;
    }

    ms_mice_sink_session_stop_establishment_timeout(session);

    session->priv->state = MS_MICE_SINK_SESSION_STATE_SOURCE_READY;

    CSIO_LOG(eLogLevel_debug,"ms.mice.sink.session.message.received.source-ready { \"session-id\": %" G_GUINT64_FORMAT " , \"local-address\": \"%s\" , \"remote-address\": \"%s\" , \"state\": \"%s\" , \"rtsp-port\": \"%u\" }",
             session->priv->session_id, session->priv->local_address, session->priv->remote_address, ms_mice_sink_session_state_to_string(session->priv->state), session->priv->rtsp_port);

    ms_mice_sink_session_raise_on_session_state_changed(session, old_state, session->priv->state);

    ms_mice_sink_session_raise_on_session_source_ready(session, session->priv->rtsp_port);
}

static void ms_mice_sink_session_handle_stop_projecting_message(ms_mice_sink_session *session, ms_mice_message *msg, GError **error)
{
    /*
     * Miracast Source --> Miracast Sink
     *
     * When a Miracast Sink receives a Stop Projection message (section 2.2.2), it MUST stop displaying the stream.
     *
     * TLVArray (variable): The following TLVs, included in any order:
     * 1. Source ID TLV (section 2.2.7.3)
     * 2. Friendly Name TLV (section 2.2.7.1)
     */
    ms_mice_sink_session_raise_on_session_stop_projection(session);
}

static void ms_mice_sink_session_handle_security_handshake_message(ms_mice_sink_session *session, ms_mice_message *msg, GError **error)
{
    /*
     * Miracast Sources <--> Miracast Sinks
     *
     * When a Miracast Sink receives a Security Handshake message (section 2.2.3) and one of the following
     * is true, it MUST proceed to complete the DTLS handshake procedure:
     * - The Sink is in the Socket Connected state (section 3.1.1).
     * - The Sink is in the Waiting for Security Handshake state (section 3.1.1).
     * - The Sink is currently in the DTLS Handshake state (section 3.1.1) and this is an additional message.
     *
     * In all other cases, the Sink MUST ignore the message and MUST tear down the connection on TCP port 7250.
     * The DTLS handshake procedure is performed by parsing the Security Token TLV (section 2.2.7.4) as a
     * message in the DTLS exchange [RFC6347]. The sink should respond with a Security Handshake
     * message containing the DTLS payload in the Security Token TLV as specified in [RFC6347]. Note that
     * each message in the DTLS exchange requires zero or more responses. Upon sending a Security
     * Handshake message which requires a response from the Source, the Sink MUST begin the Security
     * Handshake Message Timer and reset the timer upon receipt of the next message.
     * Upon completion of the DTLS handshake, the DTLS Encryption Key is stored for the remainder of the
     * session (section 3.1.1). If the Security Options have been set (section 3.1.1), indicating the Source
     * sent a Session Request message (section 2.2.4), then the TLVArray of all further messages MUST be
     * encrypted by the sender and decrypted by the receiver using the DTLS Encryption Key. If the Security
     * Options have the SinkDisplaysPin bit set, then the sink goes to the Waiting for PIN state (section
     * 3.1.1).
     *
     * TLVArray (variable): The following TLVs, included in any order:
     * 1. Source ID TLV (section 2.2.7.3) (optional)
     * 2. Security Token TLV (section 2.2.7.4)
     */

    CSIO_LOG(eLogLevel_debug,"ms_mice_sink_session_handle_security_handshake_message size[%d], command[%d]\r\n",msg->size,msg->command);

    ms_mice_tlv *tlvToken = ms_mice_tlv_find(&msg->tlvs, MS_MICE_TLV_SECURITY_TOKEN);
    if(session && tlvToken)
    {
        CSIO_LOG(eLogLevel_debug,"ms_mice_sink_session_handle_security_handshake_message found tlvToken length[%d]\r\n",tlvToken->length);
        ssl_write_to_BIO_and_check_output(session, tlvToken, error);
    }
    else
    {
        CSIO_LOG(eLogLevel_debug,"ms_mice_sink_session_handle_security_handshake_message ERROR: session[0x%x], tlvToken[0x%x]\r\n",session,tlvToken);
    }
}

static void ms_mice_sink_session_handle_session_request_message(ms_mice_sink_session *session, ms_mice_message *msg, GError **error)
{
    /*
     * Miracast Source --> Miracast Sink
     *
     * When a Miracast Sink receives a Session Request message (section 2.2.4), it MUST parse the Security
     * Options TLV (section 2.2.7.5), store the Security Options (section 3.1.1), and determine the next
     * expected state. If the Security Options indicate UseDtlsStreamEncryption (section 2.2.7.5) and the
     * Sink included this bit in its capabilities, then the Sink MUST go to the Waiting for Security Handshake
     * state (section 3.1.1). If the Security Options also indicate SinkDisplaysPin (section 2.2.7.5) and the
     * Sink included this bit in its capabilities, then the Sink MUST randomly generate and display an 8 digit
     * numeric PIN.
     *
     * TLVArray (variable): The following TLVs, included in any order:
     * 1. Source ID TLV (section 2.2.7.3)
     * 2. Friendly Name TLV (section 2.2.7.1)
     * 3. Security Options TLV (section 2.2.7.5)
     */
    MS_MICE_SINK_SESSION_STATE old_state = session->priv->state;

    ms_mice_tlv *tlv = ms_mice_tlv_find(&msg->tlvs, MS_MICE_TLV_SECURITY_OPTIONS);

    if (!tlv) {
        CSIO_LOG(eLogLevel_error,"ms.mice.sink.session.message.received.session-request.error { \"session-id\": %" G_GUINT64_FORMAT " , \"local-address\": \"%s\" , \"remote-address\": \"%s\" , \"state\": \"%s\" , \"message\": \"security options missing!\" }",
                 session->priv->session_id, session->priv->local_address, session->priv->remote_address, ms_mice_sink_session_state_to_string(session->priv->state));
        return;
    }

    session->priv->state = MS_MICE_SINK_SESSION_STATE_SESSION_REQUESTED;
    session->priv->security_options_display_pin = tlv->security_options.sink_displays_pin;
    session->priv->security_options_use_dtls_encryption = tlv->security_options.use_dtls_stream_encryption;

    ms_mice_sink_session_raise_on_session_state_changed(session, old_state, session->priv->state);

    CSIO_LOG(eLogLevel_debug,"ms.mice.sink.session.message.received.session-request { \"session-id\": %" G_GUINT64_FORMAT " , \"local-address\": \"%s\" , \"remote-address\": \"%s\" , \"state\": \"%s\" , \"sink-display-pin\": \"%s\" , \"use-dtls-encryption\": \"%s\" }",
              session->priv->session_id, session->priv->local_address, session->priv->remote_address, ms_mice_sink_session_state_to_string(session->priv->state), BOOL_TO_STRING(tlv->security_options.sink_displays_pin), BOOL_TO_STRING(tlv->security_options.use_dtls_stream_encryption));
}

static void ms_mice_sink_session_handle_pin_challenge_message(ms_mice_sink_session *session, ms_mice_message *msg, GError **error)
{
    /*
     * Miracast Source --> Miracast Sink
     *
     * When the Miracast Sink receives a PIN Challenge message (section 2.2.7.6) and it is in the Waiting for
     * PIN state (section 3.1.1), it MUST validate that the PIN Challenge TLV (section 2.2.7.6) contains the
     * correct salted hash of the PIN.
     * The salted hash of the PIN is derived as defined in section 3.1.5.6.1, using IP address of the Sink for
     * the TCP connection over 7250.
     * If the PIN Challenge TLV contains the correct hash value, then the Sink MUST respond with a PIN
     * Response message (section 2.2.6) containing its own salted hash (derived in the same way but using
     * the Sink IP address) in the PIN Challenge TLV and the PIN Response Reason TLV (section 2.2.7.7)
     * with the value 0x00.
     * If the PIN Challenge TLV contains a different hash value, then the Sink MUST respond with a PIN
     * Response message containing the PIN Response Reason 0x01 and no PIN Challenge TLV.
     * In all other cases, the Sink MUST respond with a PIN Response message containing the PIN Response
     * Reason 0x02 and MUST tear down the connection on TCP port 7250.
     *
     * TLVArray (variable): The following TLVs, included in any order:
     * 1. Source ID TLV (section 2.2.7.3)
     * 2. PIN Challenge TLV (section 2.2.7.6)
     */
    MS_MICE_SINK_SESSION_STATE old_state = session->priv->state;

    ms_mice_tlv *tlv = ms_mice_tlv_find(&msg->tlvs, MS_MICE_TLV_PIN_CHALLENGE);

    if (!tlv) {
        CSIO_LOG(eLogLevel_error,"ms.mice.sink.session.message.received.session-pin-challenge.error { \"session-id\": %" G_GUINT64_FORMAT " , \"local-address\": \"%s\" , \"remote-address\": \"%s\" , \"state\": \"%s\" , \"message\": \"pin challenge missing!\" }",
                 session->priv->session_id, session->priv->local_address, session->priv->remote_address, ms_mice_sink_session_state_to_string(session->priv->state));
        return;
    }

    session->priv->state = MS_MICE_SINK_SESSION_STATE_PIN_CHALLENGE;

    ms_mice_sink_session_raise_on_session_state_changed(session, old_state, session->priv->state);

    CSIO_LOG(eLogLevel_debug,"ms.mice.sink.session.message.received.pin-challenge { \"session-id\": %" G_GUINT64_FORMAT " , \"local-address\": \"%s\" , \"remote-address\": \"%s\" , \"state\": \"%s\" }",
             session->priv->session_id, session->priv->local_address, session->priv->remote_address, ms_mice_sink_session_state_to_string(session->priv->state));

    bool ret = ms_mice_sink_session_validate_pin(session,tlv);

    if(ret)
    {
        CSIO_LOG(eLogLevel_debug,"ms_mice_sink_session_validate_pin passed\r\n");
    }
    else
    {
        char string_buffer[SHA256_DIGEST_LENGTH*2 + 1] = {0};//64 bytes of string

        for(int i = 0; i < SHA256_DIGEST_LENGTH; i++)        //32 bytes of binary
        {
            sprintf(&string_buffer[i*2],"%02X",tlv->pin_challenge.pin[i]);
        }

        CSIO_LOG(eLogLevel_warning,"Rejecting connection due to PIN mismatch Hash:%s IP:%s \r\n",string_buffer,ms_mice_sink_session_get_remote_address(session));
    }

    //create pin response reason message and send out
    MS_MICE_MESSAGE_COMMANDS command = MS_MICE_MESSAGE_PIN_RESPONSE;

    ms_mice_message *response_msg = ms_mice_message_new(1, MS_MICE_MESSAGE_VERSION_1, command, error);
    CSIO_LOG(eLogLevel_debug,"ms_mice_message_new PIN_RESPONSE[0x%x]",response_msg);

    if(response_msg)
    {
        int reason = 0;

        if(ret == false)
            reason = 1;

        //Note: according to the spec, if reason is false, don't need source-id
        //      and pin challenge. but based on our example(laptop--laptop),
        //      we are going to include all.
        //1. source-id
        ms_mice_message_tlv_source_id_attach(response_msg,session->priv->source_id,error);

        //2. pin challenge
        unsigned char challengeHash[SHA256_DIGEST_LENGTH];
        ms_mice_sink_session_generate_pin_challenge(session,challengeHash);
        ms_mice_message_tlv_pin_challenge_attach(response_msg, challengeHash, SHA256_DIGEST_LENGTH,error);

        //3. pin response reason
        ms_mice_message_tlv_pin_response_reason_attach(response_msg, reason, error);

        ms_mice_message_entry *entry = ms_mice_message_entry_new(response_msg, error);

        if(entry)
        {
            ms_mice_sink_session_queue_outgoing_message(session, entry);

            int ret = ms_mice_sink_session_write(session, error,true);
            CSIO_LOG(eLogLevel_debug,"pin_challenge_message: ms_mice_sink_session_write ret[0x%x]",ret);
        }
        else
        {
            CSIO_LOG(eLogLevel_error,"pin_challenge_message: ms_mice_message_entry_new failed");
        }
    }
}

static void ms_mice_sink_session_handle_pin_response_message(ms_mice_sink_session *session, ms_mice_message *msg, GError **error)
{
    /*
     * Miracast Source <-- Miracast Sink
     *
     * TLVArray (variable): The following TLVs, included in any order:
     * 1. Source ID TLV (section 2.2.7.3)
     * 2. PIN Challenge TLV (section 2.2.7.6)
     * 3. PIN Response Reason TLV (section 2.2.7.7)
     */
    if (error) {
        *error = g_error_new(G_IO_ERROR, G_IO_ERROR_INVALID_DATA, "Unexpected message from Miracast source, MS-MICE command= %s (0x%x)", ms_mice_command_to_string(msg->command), msg->command);
    }
}

static void ms_mice_sink_session_dispatch_message(ms_mice_sink_session *session, ms_mice_message *msg, GError **error)
{
    CSIO_LOG(eLogLevel_debug,"ms.mice.sink.session.message.received.handle { \"session-id\": %" G_GUINT64_FORMAT " , \"local-address\": \"%s\" , \"remote-address\": \"%s\" , \"state\": \"%s\" , \"ms-mice-size\": \"%u\" , \"ms-mice-version\": %u , \"ms-mice-command\": %s }",
              session->priv->session_id, session->priv->local_address, session->priv->remote_address, ms_mice_sink_session_state_to_string(session->priv->state), msg->size, msg->version, ms_mice_command_to_string(msg->command));

    /* validate will also extract source-id and friendly-name */
    if (!ms_mice_sink_session_message_validate(session, msg, error))
        return;

    switch (msg->command) {
        case MS_MICE_MESSAGE_SOURCE_READY:
            ms_mice_sink_session_handle_source_ready_message(session, msg, error);
            break;

        case MS_MICE_MESSAGE_STOP_PROJECTION:
            ms_mice_sink_session_handle_stop_projecting_message(session, msg, error);
            break;

        case MS_MICE_MESSAGE_SECURITY_HANDSHAKE:
            ms_mice_sink_session_handle_security_handshake_message(session, msg, error);
            break;

        case MS_MICE_MESSAGE_SESSION_REQUEST:
            ms_mice_sink_session_handle_session_request_message(session, msg, error);
            break;

        case MS_MICE_MESSAGE_PIN_CHALLENGE:
            ms_mice_sink_session_handle_pin_challenge_message(session, msg, error);
            break;

        case MS_MICE_MESSAGE_PIN_RESPONSE:
            ms_mice_sink_session_handle_pin_response_message(session, msg, error);
            break;

        default:
            CSIO_LOG(eLogLevel_error,"ms.mice.sink.session.message.received.error { \"session-id\": %" G_GUINT64_FORMAT " , \"local-address\": \"%s\" , \"remote-address\": \"%s\" , \"state\": \"%s\" , \"ms-mice-command\": \"%u (0x%02x)\" , \"message\": \"unknown command\" }",
                      session->priv->session_id, session->priv->local_address, session->priv->remote_address, ms_mice_sink_session_state_to_string(session->priv->state), msg->command, msg->command);
            if (error) {
                *error = g_error_new(G_IO_ERROR, G_IO_ERROR_INVALID_ARGUMENT, "Unknown MS-MICE command= %u (0x%x)", msg->command, msg->command);
            }
            break;
    }
}

/* ------------------------------------------------------------------------------------------------------------------
 * -- MS-MICE SINK SESSION SOCKET READ
 * -- */

static int ms_mice_sink_session_handle_read_data(ms_mice_sink_session *session, const guint8 *const buffer, size_t buffer_size)
{
    GError *error = NULL;
    gsize available = 0;
    gsize required = 0;
    bool pin_bypass_error = false;

    CSIO_LOG(eLogLevel_debug,"ms.mice.sink handle_read_data enter\n");

    g_memory_input_stream_add_data(G_MEMORY_INPUT_STREAM(session->priv->base_in_stream), buffer, buffer_size, NULL);
    g_buffered_input_stream_fill(G_BUFFERED_INPUT_STREAM(session->priv->in_stream), buffer_size, NULL, NULL);

    available = stream_get_available(session->priv->in_stream);

    while (available > 0)
    {
        if (!session->priv->in_message_active)
        {
            guint16 length;
            guint8 version;
            guint8 command;

            if (available < MS_MICE_HEADER_SIZE)
            {
                CSIO_LOG(eLogLevel_debug,"ms.mice.sink handle_read_data return\n");
                return -EAGAIN;
            }

            length = stream_read_uint16(session->priv->in_stream);
            version = stream_read_byte(session->priv->in_stream);
            command = stream_read_byte(session->priv->in_stream);

            if (length > MS_MICE_MESSAGE_MAX_LENGTH_BYTES) {
                CSIO_LOG(eLogLevel_error,"ms.mice.sink.session.message.received.header.error { \"session-id\": %" G_GUINT64_FORMAT " , \"local-address\": \"%s\" , \"remote-address\": \"%s\" , \"state\": \"%s\" , \"ms-mice-size\": \"%u\" , \"message\": \"message length is larger than expected; reporting %u bytes, expecting less than %u bytes\" }",
                         session->priv->session_id, session->priv->local_address, session->priv->remote_address, ms_mice_sink_session_state_to_string(session->priv->state), length, length, MS_MICE_MESSAGE_MAX_LENGTH_BYTES);
                return -EPIPE;
            }

            session->priv->in_message = ms_mice_message_new(length, version, command, &error);
            if (error != NULL)
            {
                CSIO_LOG(eLogLevel_debug,"ms.mice.sink.session.message Unable to create mew message: %s\n", error->message);
                g_error_free (error);
                error = NULL;
            }
            // TODO [RAL] check for error

            available = stream_get_available(session->priv->in_stream);

            session->priv->in_message_active = true;

            CSIO_LOG(eLogLevel_debug,"ms.mice.sink.session.message.received.header { \"session-id\": %" G_GUINT64_FORMAT " , \"local-address\": \"%s\" , \"remote-address\": \"%s\" , \"state\": \"%s\" , \"ms-mice-size\": \"%u\" , \"ms-mice-version\": %u , \"ms-mice-command\": %s , \"available\": %" G_GSIZE_FORMAT " }",
                      session->priv->session_id, session->priv->local_address, session->priv->remote_address, ms_mice_sink_session_state_to_string(session->priv->state), session->priv->in_message->size, session->priv->in_message->version, ms_mice_command_to_string(session->priv->in_message->command), available);
        }

        required = session->priv->in_message->size - MS_MICE_HEADER_SIZE;
        CSIO_LOG(eLogLevel_debug,"ms.mice.sink.session.message.received { \"session-id\": %" G_GUINT64_FORMAT " , \"state\": \"%s\" , \"ms-mice-size\": \"%u\" , \"required\": %" G_GSIZE_FORMAT ",\"available\": %" G_GSIZE_FORMAT " , \"handshake_complete\": \"%u\"}",
                 session->priv->session_id, ms_mice_sink_session_state_to_string(session->priv->state), session->priv->in_message->size, required,available,session->priv->is_dtls_encryption_handshake_complete);

        if (required > available)
        {
            CSIO_LOG(eLogLevel_debug,"ms.mice.sink handle_read_data return\n");
            return -EAGAIN;
        }

        //Note: if pin option is false, the message from source is not encrypted.
        if(ms_mice_sink_session_get_display_pin_option(session) && session->priv->is_dtls_encryption_handshake_complete)
        {
            guint8 *value = g_new0(guint8, required);
            stream_read_bytes(session->priv->in_stream, value, required);

            guint64 sessionID = ms_mice_sink_session_get_id(session);

            //SHA-256 of pin + ip :  “12345678”  + 192.0.2.100   >>> 31 32 33 34 35 36 37 38    c0 00 02 64
            //     SHA-256 hash is:  60 54 09 f8 32 30 8a d0   b8 93 a7 f9 1b e4 2b 26
            //                       4c 73 72 b3 6e 90 77 50   6e 1b 4c c1 83 de 79 da
            if(sessionID)
            {
                int cryptBufSize = MS_MICE_MIN_DTLS_CRYPTBUF_SIZE;
                //need to find out buff size here
                if( (required * 2 ) > MS_MICE_MIN_DTLS_CRYPTBUF_SIZE )
                {
                    cryptBufSize = required * 2;
                }

                guint8 *decryptBuf = g_new0(guint8, cryptBufSize);
                CSIO_LOG(eLogLevel_debug,"ms.mice.sink ms_mice_sink_session_handle_read_data created decryptBuf[0x%x] of size[%d]\r\n",
                         decryptBuf,cryptBufSize);

                if(decryptBuf)
                {
                    int decryptSize = sssl_decryptDTLS(sessionID,(void*)value,required,(void*)decryptBuf,cryptBufSize);

                    if(decryptSize > 0)
                    {
                        //update message size
                        session->priv->in_message->size = decryptSize + MS_MICE_HEADER_SIZE;

                        GInputStream *base_in_stream = g_memory_input_stream_new();
                        GDataInputStream * in_stream;

                        in_stream = g_data_input_stream_new(base_in_stream);
                        g_data_input_stream_set_byte_order(in_stream, G_DATA_STREAM_BYTE_ORDER_BIG_ENDIAN);

                        g_memory_input_stream_add_data(G_MEMORY_INPUT_STREAM(base_in_stream), decryptBuf, decryptSize, NULL);
                        g_buffered_input_stream_fill(G_BUFFERED_INPUT_STREAM(in_stream), decryptSize, NULL, NULL);

                        ms_mice_message_unpack(session->priv->in_message, in_stream, &error);
                        if (error != NULL)
                        {
                            CSIO_LOG(eLogLevel_debug,"ms.mice.sink Unable to unpack message: %s\n", error->message);
                            g_error_free (error);
                            error = NULL;
                        }

                        ms_mice_sink_session_dispatch_message(session, session->priv->in_message, &error);
                        if (error != NULL)
                        {
                            CSIO_LOG(eLogLevel_debug,"ms.mice.sink Unable to dispatch message: %s\n", error->message);
                            if (error->code == G_IO_ERROR_PERMISSION_DENIED)
                            	pin_bypass_error = true;
                            g_error_free (error);
                            error = NULL;
                        }

                        CSIO_LOG(eLogLevel_debug,"ms.mice.sink available after unpack[%d]\n",stream_get_available(in_stream));

                        if (in_stream)
                        {
                            g_input_stream_close(G_INPUT_STREAM(in_stream), NULL, NULL);
                            g_object_unref(in_stream);
                            in_stream = NULL;
                        }

                        if (base_in_stream)
                        {
                            g_input_stream_close(G_INPUT_STREAM(base_in_stream), NULL, NULL);
                            g_object_unref(base_in_stream);
                            base_in_stream = NULL;
                        }
                    }
                    else
                    {
                        CSIO_LOG(eLogLevel_debug,"ms.mice.sink error: sssl_decryptDTLS return: %d\n",decryptSize);
                    }
                    g_free((gpointer)decryptBuf);
                }
                else
                {
                    CSIO_LOG(eLogLevel_debug,"ms.mice.sink error: failed to create decryptBuf\n");
                }
            }//else

            g_free((gpointer)value);
        }
        else
        {
            ms_mice_message_unpack(session->priv->in_message, session->priv->in_stream, &error);

            if (error != NULL)
            {
                CSIO_LOG(eLogLevel_debug,"Unable to unpack message: %s\n", error->message);
                g_error_free (error);
                error = NULL;
            }

            CSIO_LOG(eLogLevel_debug,"ms.mice.sink.session.message.received { \"session-id\": %" G_GUINT64_FORMAT " , \"local-address\": \"%s\" , \"remote-address\": \"%s\" , \"state\": \"%s\" , \"ms-mice-size\": \"%u\" , \"ms-mice-version\": %u , \"ms-mice-command\": %s }",
                      session->priv->session_id, session->priv->local_address, session->priv->remote_address, ms_mice_sink_session_state_to_string(session->priv->state), session->priv->in_message->size, session->priv->in_message->version, ms_mice_command_to_string(session->priv->in_message->command));

            // TODO [RAL] check for error

            ms_mice_sink_session_dispatch_message(session, session->priv->in_message, &error);
            if (error != NULL)
            {
                CSIO_LOG(eLogLevel_debug,"ms.mice.sink Unable to dispatch message: %s\n", error->message);
                if (error->code == G_IO_ERROR_PERMISSION_DENIED)
                	pin_bypass_error = true;
                g_error_free (error);
                error = NULL;
            }
        }

        //inserted for bug MERC-1304 to see if it works.
        if(session->priv->in_message)
            ms_mice_message_free(session->priv->in_message);

        session->priv->in_message = NULL;

        // TODO [RAL] check for error

        session->priv->in_message_active = false;

        available = stream_get_available(session->priv->in_stream);

        CSIO_LOG(eLogLevel_debug,"ms.mice.sink handle_read_data end of while loop: in_stream available: %d\n", available);
    }

    if (pin_bypass_error)
    	return -ECONNABORTED;
    CSIO_LOG(eLogLevel_debug,"ms.mice.sink handle_read_data return\n");
    return -EAGAIN;
}

static int ms_mice_sink_session_read(ms_mice_sink_session *session)
{
    char buf[4096];
    ssize_t r = 0;

    r = recv(session->priv->source_fd, buf, sizeof(buf), MSG_DONTWAIT);
    if (r < 0) {
        if (errno == EAGAIN || errno == EINTR)
            return -EAGAIN;
        return -errno;
    } else if (!r) {
        /* there're no 0-length packets on streams; this is EOF; socket closed by remote */
        CSIO_LOG(eLogLevel_warning,"ms.mice.sink read got 0 length message - remote end must have closed connection\n");
        return -EPIPE;
    } else if (r > sizeof(buf)) {
        r = sizeof(buf);
    }

    return ms_mice_sink_session_handle_read_data(session, (guint8 *)buf, (size_t)r);
}

/* ------------------------------------------------------------------------------------------------------------------
 * -- MS-MICE SINK SESSION WRITE
 * -- */

static void ms_mice_sink_session_queue_outgoing_message(ms_mice_sink_session *session, ms_mice_message_entry *entry)
{
    shl_dlist_link_tail(&session->priv->outgoing, &entry->list);
}

static int ms_mice_sink_session_write_message(ms_mice_sink_session *session, ms_mice_message_entry *entry, bool needEncrypt,GError **error)
{
    size_t remaining;
    ssize_t r;

    g_assert(session && entry);

    if (!entry->is_sending) {

        if(needEncrypt)
            ms_mice_message_entry_encrypt_pack(session,entry, error);
        else
            ms_mice_message_entry_pack(entry, error);

        CSIO_LOG(eLogLevel_debug,"ms.mice.sink.session.message.write { \"session-id\": %" G_GUINT64_FORMAT " , \"local-address\": \"%s\" , \"remote-address\": \"%s\" , \"state\": \"%s\" , \"ms-mice-size\": \"%u\" , \"ms-mice-version\": %u , \"ms-mice-command\": \"%s\" , \"send\": %" G_GSIZE_FORMAT " }",
                  session->priv->session_id, session->priv->local_address, session->priv->remote_address, ms_mice_sink_session_state_to_string(session->priv->state), entry->msg->size, entry->msg->version, ms_mice_command_to_string(entry->msg->command), entry->raw_size);
    }

    entry->is_sending = true;
    remaining = entry->raw_size - entry->sent;
    r = send(session->priv->source_fd, &entry->raw[entry->sent], remaining, MSG_NOSIGNAL | MSG_DONTWAIT);
    if (r < 0) {
        if (errno == EAGAIN || errno == EINTR)
            return -EAGAIN;
        return -errno;
    } else if (r > (ssize_t)remaining) {
        r = remaining;
    }

    entry->sent += r;
    if (entry->sent >= entry->raw_size) {
        CSIO_LOG(eLogLevel_debug,"ms.mice.sink.session.message.write.complete { \"session-id\": %" G_GUINT64_FORMAT " , \"local-address\": \"%s\" , \"remote-address\": \"%s\" , \"state\": \"%s\" , \"ms-mice-size\": \"%u\" , \"ms-mice-version\": %u , \"ms-mice-command\": %s , \"sent\": %" G_GSIZE_FORMAT " }",
                  session->priv->session_id, session->priv->local_address, session->priv->remote_address, ms_mice_sink_session_state_to_string(session->priv->state), entry->msg->size, entry->msg->version, ms_mice_command_to_string(entry->msg->command), entry->sent);

        ms_mice_message_entry_free(entry);
    }

    return 0;
}

static int ms_mice_sink_session_write(ms_mice_sink_session *session,GError **error, bool needEncrypt)
{
    ms_mice_message_entry *m;

    if (shl_dlist_empty(&session->priv->outgoing))
        return 0;

    m = shl_dlist_first_entry(&session->priv->outgoing, ms_mice_message_entry, list);

    return ms_mice_sink_session_write_message(session, m, needEncrypt,error);
}

/* ------------------------------------------------------------------------------------------------------------------
 * -- MS-MICE SINK SESSION SOCKET EVENTS
 * -- */
static void send_out_BIO_data(ms_mice_sink_session* session, char* buf, guint16 len, GError **error)
{
    MS_MICE_MESSAGE_COMMANDS command = MS_MICE_MESSAGE_SECURITY_HANDSHAKE;

    ms_mice_message *msg = ms_mice_message_new(len, MS_MICE_MESSAGE_VERSION_1, command, error);
    CSIO_LOG(eLogLevel_debug,"ms_mice_message_new msg[0x%x]",msg);

    if(msg)
    {
        ms_mice_message_tlv_security_token_attach(msg, (guint8 *)buf, len, error);

        ms_mice_message_entry *entry = ms_mice_message_entry_new(msg, error);

        if(entry)
        {
            ms_mice_sink_session_queue_outgoing_message(session, entry);

            int ret = ms_mice_sink_session_write(session,error);
            CSIO_LOG(eLogLevel_debug,"ms_mice_sink_session_write ret[0x%x]",ret);
        }
    }
}

void ssl_send_DTLS_handshake(void * session,char * dtlsData,int dtlsDataLen,void ** error)
{
   send_out_BIO_data((ms_mice_sink_session *)session,dtlsData,dtlsDataLen,(GError **)error);
}

void ssl_write_to_BIO_and_check_output(ms_mice_sink_session *session, ms_mice_tlv *tlv, GError **error)
{
    int retv;
    void * sssl;
    void * secToken;
    int secTokenLength;
    bool * isHandshakeCompletePtr;

    CSIO_LOG(eLogLevel_debug,"mira: {%s} - ***** entering *****",__FUNCTION__);

    if(session && session->priv)
    {
        guint64 sessionID = ms_mice_sink_session_get_id(session);

        CSIO_LOG(eLogLevel_debug, "mira: {%s} - calling sssl_getContextWithSessionID()",__FUNCTION__);

        sssl = sssl_getContextWithSessionID(sessionID);
        if(sssl == NULL) {
            if (error) {
                *error = g_error_new(G_IO_ERROR, G_IO_ERROR_INVALID_DATA,
                     "ssl_write_to_BIO_and_check_output() - failed to get DTLS context");
            }
            CSIO_LOG(eLogLevel_error, "mira: {%s} - sssl_getContextWithSessionID() returned NULL",__FUNCTION__);

            return;
        }

        secToken = tlv->security_token.token;
        secTokenLength = tlv->length;
        isHandshakeCompletePtr = &session->priv->is_dtls_encryption_handshake_complete;

        CSIO_LOG(eLogLevel_debug, "mira: {%s} - calling sssl_runDTLSHandshakeWithSecToken()",__FUNCTION__);

        retv = sssl_runDTLSHandshakeWithSecToken(sssl,
                                                secToken,
                                                secTokenLength,
                                                isHandshakeCompletePtr,
                                                ssl_send_DTLS_handshake,
                                                (void *)session,
                                                (void **)error);

        CSIO_LOG(eLogLevel_debug,"mira: sssl_runDTLSHandshakeWithSecToken() returned %d",retv);
    }

    CSIO_LOG(eLogLevel_debug,"mira: {%s} - ===== exiting =====",__FUNCTION__);
}

static gboolean ms_mice_sink_session_io_fn(GIOChannel *channel, GIOCondition cond, gpointer data)
{
    g_autofree const char* cond_string = NULL;

    ms_mice_sink_session *session = (ms_mice_sink_session *)data;
    g_autoptr(GError) error = NULL;
    int read_r = 0, write_r = 0;

    if (cond & G_IO_OUT) {
        write_r = ms_mice_sink_session_write(session, &error);
        if (write_r == -EAGAIN)
            write_r = 0;
    }

    if (cond & G_IO_IN || write_r < 0) {
        read_r = ms_mice_sink_session_read(session);
        if (read_r < 0 && read_r != -EAGAIN)
            goto error;
        else if (read_r >= 0)
            goto out;
    }

    if (!(cond & (G_IO_HUP | G_IO_ERR)) && write_r >= 0) {
        goto out;
    }

error:
    cond_string = g_io_condition_to_string(cond);
    CSIO_LOG(eLogLevel_info,"ms.mice.sink.session.io.event.info { \"session-id\": %" G_GUINT64_FORMAT " , \"local-address\": \"%s\" , \"remote-address\": \"%s\" , \"state\": \"%s\" , \"condition\": \"%s\" , \"write\": %d , \"read\": %d }",
              session->priv->session_id, session->priv->local_address, session->priv->remote_address, ms_mice_sink_session_state_to_string(session->priv->state), cond_string, write_r, read_r);
    ms_mice_sink_session_close(session);
    return G_SOURCE_REMOVE;

out:
    return G_SOURCE_CONTINUE;
}

static gboolean ms_mice_sink_session_io_prepare_fn(GSource *source, gint *timeout, gpointer data)
{
    ms_mice_sink_session *session = (ms_mice_sink_session *)data;

    if (ms_mice_sink_session_is_connected(session)) {
        int mask = (G_IO_HUP | G_IO_ERR | G_IO_IN);

        if (!shl_dlist_empty(&session->priv->outgoing))
            mask |= G_IO_OUT;

        mcast_g_io_watch_condition_set(source, (GIOCondition)mask);
    }

    return mcast_g_io_watch_call_base_prepare(source, timeout);
}

static gboolean ms_mice_sink_session_establishment_timeout_fn(gpointer data)
{
    ms_mice_sink_session *session = (ms_mice_sink_session *)data;

    CSIO_LOG(eLogLevel_error,"ms.mice.sink.session.source.establishment.timeout { \"session-id\": %" G_GUINT64_FORMAT " , \"local-address\": \"%s\" , \"remote-address\": \"%s\" , \"state\": \"%s\" }",
             session->priv->session_id, session->priv->local_address, session->priv->remote_address, ms_mice_sink_session_state_to_string(session->priv->state));

    ms_mice_sink_session_close(session);

    return G_SOURCE_REMOVE;
}

void ms_mice_sink_session_connected(ms_mice_sink_session *session, GSocketConnection *connection, GError **error)
{
    MS_MICE_SINK_SESSION_STATE old_state = session->priv->state;
    GSocket *socket = NULL;
    int fd;
    g_autoptr(GSocketAddress) local_socket_address = NULL;
    g_autoptr(GSocketAddress) remote_socket_address = NULL;

    CSIO_LOG(eLogLevel_debug,"ms.mice.sink.session.connected");

    if (!ms_mice_sink_session_validate(session, error))
        return;

    if (!connection) {
        if (error) {
            *error = g_error_new(G_IO_ERROR, G_IO_ERROR_INVALID_ARGUMENT, "No socket connection");
        }
    }

    shl_dlist_link_tail(&session->priv->service->sessions, &session->list);

    session->priv->state = g_socket_connection_is_connected(connection) ? MS_MICE_SINK_SESSION_STATE_CONNECTED : MS_MICE_SINK_SESSION_STATE_DISCONNECTED;

    local_socket_address = g_socket_connection_get_local_address(connection, NULL);
    session->priv->local_address = socket_address_to_string(local_socket_address);

    remote_socket_address = g_socket_connection_get_remote_address(connection, NULL);
    session->priv->remote_address = socket_address_to_string(remote_socket_address);

    CSIO_LOG(eLogLevel_debug,"ms.mice.sink.session.connected { \"session-id\": %" G_GUINT64_FORMAT " , \"local-address\": \"%s\" , \"remote-address\": \"%s\" , \"state\": \"%s\" }",
              session->priv->session_id, session->priv->local_address, session->priv->remote_address, ms_mice_sink_session_state_to_string(session->priv->state));

    if (!ms_mice_sink_session_is_connected(session)) {
        if (error) {
            *error = g_error_new(G_IO_ERROR, G_IO_ERROR_FAILED, "Socket not connected");
        }
        return;
    }

    session->priv->source_connection = connection;
    g_object_ref(connection);

    socket = g_socket_connection_get_socket(connection);

    if(socket)
    {
        gint optionValue = 1;
        g_socket_set_option(socket,SOL_SOCKET,SO_KEEPALIVE,optionValue,error);

        gboolean ret = g_socket_get_option(socket,SOL_SOCKET,SO_KEEPALIVE ,&optionValue,error);
        CSIO_LOG(eLogLevel_verbose, "ms_mice_sink_session_connected: set SO_KEEPALIVE to  %d, ret[%d]",optionValue,ret);

        /*https://www.tldp.org/HOWTO/TCP-Keepalive-HOWTO/programming.html
           There are also three other socket options you can set for keepalive when you write your application.
           They all use the SOL_TCP level instead of SOL_SOCKET, and they override system-wide variables only
           for the current socket. If you read without writing first, the current system-wide parameters will be returned.

           TCP_KEEPCNT: overrides tcp_keepalive_probes
           TCP_KEEPIDLE: overrides tcp_keepalive_time
           TCP_KEEPINTVL: overrides tcp_keepalive_intvl*/

        /* Start keeplives after this period */
        ret = g_socket_get_option(socket,SOL_TCP,TCP_KEEPIDLE ,&optionValue,error);
        CSIO_LOG(eLogLevel_verbose, "ms_mice_sink_session_connected: get TCP_KEEPIDLE to  %d, ret[%d]",optionValue,ret);

        /* Number of keepalives before death */
        ret = g_socket_get_option(socket, SOL_TCP, TCP_KEEPCNT, &optionValue, error);
        CSIO_LOG(eLogLevel_verbose, "ms_mice_sink_session_connected: get TCP_KEEPCNT to  %d, ret[%d]",optionValue,ret);

        /* Interval between keepalives */
        ret = g_socket_get_option(socket, SOL_TCP, TCP_KEEPINTVL, &optionValue, error);
        CSIO_LOG(eLogLevel_verbose, "ms_mice_sink_session_connected: get TCP_KEEPINTVL to  %d, ret[%d]",optionValue,ret);
    }
    else
    {
        CSIO_LOG(eLogLevel_error, "ms_mice_sink_session_connected: socket is NULL\n");
    }

    fd = g_socket_get_fd(socket);

    session->priv->source_channel = g_io_channel_unix_new(fd);

    GMainContext* context = ms_mice_sink_service_get_context(session->priv->service);

    session->priv->source_fd_id = mcast_g_io_add_watch (
            session->priv->source_channel,
            G_PRIORITY_DEFAULT,
            (GIOCondition)(G_IO_IN | G_IO_ERR | G_IO_HUP),
            ms_mice_sink_session_io_prepare_fn,
            ms_mice_sink_session_io_fn,
            session,
            NULL,
            context);
    
    session->priv->in_message_active = false;

    session->priv->base_in_stream = g_memory_input_stream_new();

    session->priv->in_stream = g_data_input_stream_new(session->priv->base_in_stream);
    g_data_input_stream_set_byte_order(session->priv->in_stream, G_DATA_STREAM_BYTE_ORDER_BIG_ENDIAN);

    session->priv->base_out_stream = g_memory_output_stream_new_resizable();

    session->priv->out_stream = g_data_output_stream_new(session->priv->base_out_stream);
    g_data_output_stream_set_byte_order(session->priv->out_stream, G_DATA_STREAM_BYTE_ORDER_BIG_ENDIAN);

    session->priv->source_fd = fd;

    guint64 sessionID = ms_mice_sink_session_get_id(session);

    CSIO_LOG(eLogLevel_debug, "mira: {%s} - ***** calling sssl_createDTLS() *****",__FUNCTION__);

    void * sssl = sssl_createDTLS((unsigned long long)sessionID);
    if(sssl == NULL) {
        if (error) {
            *error = g_error_new(G_IO_ERROR, G_IO_ERROR_INVALID_DATA, "Failed to create DTLS context");
        }

        CSIO_LOG(eLogLevel_error,"mira: {%s} - sssl_createDTLS() returned NULL",__FUNCTION__);
        return;
    }

    CSIO_LOG(eLogLevel_debug,"mira: {%s} - ===== returned from sssl_createDTLS() =====",__FUNCTION__);

    if (old_state != session->priv->state) {
        ms_mice_sink_session_raise_on_session_state_changed(session, old_state, session->priv->state);
    }

    //create timeout callback here
    if(context)
    {
        /* Create new timeout source to be called every 5min.
            * Reference count of source is 1 once created */
        GSource *source = g_timeout_source_new_seconds(MS_MICE_SESSION_ESTABLISHMENT_TIMEOUT_SECONDS);
        g_source_set_callback (source, ms_mice_sink_session_establishment_timeout_fn, session, NULL);

        session->priv->source_establishment_timeout = g_source_attach (source, context);
        g_source_unref (source);

        CSIO_LOG(eLogLevel_verbose, "ms_mice_sink_session_connected: created a timer (id : %d) and attached to the context(0x%x)",session->priv->source_establishment_timeout,context);
    }
    else
    {
        session->priv->source_establishment_timeout = g_timeout_add_seconds_full(G_PRIORITY_HIGH, MS_MICE_SESSION_ESTABLISHMENT_TIMEOUT_SECONDS, ms_mice_sink_session_establishment_timeout_fn, session, NULL);

        CSIO_LOG(eLogLevel_verbose, "ms_mice_sink_session_connected: created a timer (id : %d)",session->priv->source_establishment_timeout);
    }
}

static void ms_mice_sink_session_disconnect(ms_mice_sink_session *ms_session)
{
    CSIO_LOG(eLogLevel_debug,"ms.mice.sink.session.disconnect { \"session-id\": %" G_GUINT64_FORMAT " , \"local-address\": \"%s\" , \"remote-address\": \"%s\" , \"state\": \"%s\" , \"friendly-name\": \"%s\" , \"source-id\": \"%s\" }",
             ms_session->priv->session_id, ms_session->priv->local_address, ms_session->priv->remote_address, ms_mice_sink_session_state_to_string(ms_session->priv->state), ms_session->priv->friendly_name, ms_session->priv->source_id);

    // ***
    CSIO_LOG(eLogLevel_debug,"mira: {%s} - ***** calling sssl_cancelDTLSAppThWithSessionIDAndWait() *****",__FUNCTION__);
    int retv = sssl_cancelDTLSAppThWithSessionIDAndWait(ms_session->priv->session_id);
    CSIO_LOG(eLogLevel_debug,"mira: {%s} - ===== returned from sssl_cancelDTLSAppThWithSessionIDAndWait(), retv = %d =====",
        __FUNCTION__,retv);
    // ***


#ifdef API_SUPPORTED
    sink_api_session *api_session = ms_mice_sink_session_get_api(ms_session);

    sink_api_session_observer_detach(api_session);

    sink_api_session_raise_disconnected_event(api_session);

    //sink_api_session_unexport(api_session);

    sink_api_session_close(api_session);
#endif

    // ***
    CSIO_LOG(eLogLevel_debug,"mira: {%s} - exiting",__FUNCTION__);
}

void ms_mice_sink_session_close(ms_mice_sink_session *session)
{
    MS_MICE_SINK_SESSION_STATE old_state;
    
    if (!session)
        return;

    ms_mice_sink_session_stop_establishment_timeout(session);

    old_state = session->priv->state;

    CSIO_LOG(eLogLevel_debug,"ms.mice.sink.session.close { \"session-id\": %" G_GUINT64_FORMAT " , \"local-address\": \"%s\" , \"remote-address\": \"%s\" , \"state\": \"%s\" , \"friendly-name\": \"%s\" , \"source-id\": \"%s\" }",
              session->priv->session_id, session->priv->local_address, session->priv->remote_address, ms_mice_sink_session_state_to_string(session->priv->state), session->priv->friendly_name, session->priv->source_id);

    shl_dlist_unlink(&session->list);

    if (session->priv->source_fd_id) {
        GMainContext* context = ms_mice_sink_service_get_context(session->priv->service);
        CSIO_LOG(eLogLevel_debug,"ms.mice.sink.session.close { \"context\": 0x%x }" , context);

        if(context)
        {
            GSource * source = g_main_context_find_source_by_id (context,session->priv->source_fd_id);
            CSIO_LOG(eLogLevel_debug,"ms.mice.sink.session.close { \"source\": 0x%x }" , source);

            if(source)
                g_source_destroy(source);
        }
        else
        {
            g_source_remove(session->priv->source_fd_id);
        }
        session->priv->source_fd_id = 0;
    }

    if (session->priv->source_channel) {
        g_io_channel_shutdown(session->priv->source_channel, TRUE, NULL);
        g_io_channel_unref(session->priv->source_channel);
        session->priv->source_channel = NULL;
    }

    if (session->priv->in_stream) {
        g_input_stream_close(G_INPUT_STREAM(session->priv->in_stream), NULL, NULL);
        g_object_unref(session->priv->in_stream);
        session->priv->in_stream = NULL;
    }

    if (session->priv->base_in_stream) {
        g_input_stream_close(G_INPUT_STREAM(session->priv->base_in_stream), NULL, NULL);
        g_object_unref(session->priv->base_in_stream);
        session->priv->base_in_stream = NULL;
    }

    if (session->priv->out_stream) {
        g_output_stream_close(G_OUTPUT_STREAM(session->priv->out_stream), NULL, NULL);
        g_object_unref(session->priv->out_stream);
        session->priv->out_stream = NULL;
    }

    if (session->priv->base_out_stream) {
        g_output_stream_close(G_OUTPUT_STREAM(session->priv->base_out_stream), NULL, NULL);
        g_object_unref(session->priv->base_out_stream);
        session->priv->base_out_stream = NULL;
    }

    if (session->priv->source_connection) {
        g_io_stream_close(G_IO_STREAM(session->priv->source_connection), NULL, NULL);
        g_object_unref(session->priv->source_connection);
        session->priv->source_connection = NULL;
    }

    session->priv->in_message_active = false;
    session->priv->source_fd = -1;
    session->priv->state = MS_MICE_SINK_SESSION_STATE_DISCONNECTED;

    while (!shl_dlist_empty(&session->priv->outgoing)) {
        ms_mice_message_entry *entry = shl_dlist_first_entry(&session->priv->outgoing, ms_mice_message_entry, list);
        ms_mice_message_entry_free(entry);
    }

    if (old_state != MS_MICE_SINK_SESSION_STATE_DISCONNECTED) {
        ms_mice_sink_session_raise_on_session_state_changed(session, old_state, session->priv->state);
        //calling session_stop is more important than session_disconnected
        ms_mice_sink_session_raise_on_session_stop_projection(session);
        ms_mice_sink_session_raise_on_session_disconnected(session);
        ms_mice_sink_session_disconnect(session);
    }

    if (session->priv->service) {
        ms_mice_sink_service_on_session_closed(session->priv->service, session);
    }
}

void ms_mice_sink_session_observer_attach(ms_mice_sink_session *session, ms_mice_sink_session_observer *observer, gpointer data)
{
    session->priv->observer = observer;
    session->priv->observer_data = data;
}

void ms_mice_sink_session_observer_detach(ms_mice_sink_session *session)
{
    session->priv->observer = NULL;
    session->priv->observer_data = NULL;
}

/* ------------------------------------------------------------------------------------------------------------------
 * -- MS-MICE SINK SESSION CONSTRUCTION
 * -- */

void ms_mice_sink_session_new(ms_mice_sink_session **out, ms_mice_sink_service *service, guint64 session_id, GError **error)
{
    ms_mice_sink_session *s = NULL;

    if (!out) {
        if (error) {
            *error = g_error_new(G_IO_ERROR, G_IO_ERROR_INVALID_ARGUMENT, "No sink reference");
        }
        return;
    }

    s = g_new0(ms_mice_sink_session, 1);
    if (!s) {
        if (error) {
            *error = g_error_new(G_IO_ERROR, G_IO_ERROR_FAILED, "No more memory error");
        }
        return;
    }

    s->priv = g_new0(ms_mice_sink_session_private, 1);

    CSIO_LOG(eLogLevel_debug,"ms.mice.sink.session.new { \"session-id\": %" G_GUINT64_FORMAT " }", session_id);

    s->priv->service = service;
    s->priv->session_id = session_id;

#ifdef API_SUPPORTED
    s->priv->api_session_ = NULL;
#endif

    s->priv->state = MS_MICE_SINK_SESSION_STATE_DISCONNECTED;
    s->priv->rtsp_port = 0;
    s->priv->friendly_name = NULL;
    s->priv->source_id = NULL;
    s->priv->source_fd = -1;
    s->priv->in_message_active = false;
    s->priv->security_options_display_pin = false;
    s->priv->security_options_use_dtls_encryption = false;
    s->priv->source_establishment_timeout = 0;

    shl_dlist_init(&s->priv->outgoing);

    *out = s;
}

void ms_mice_sink_session_free(ms_mice_sink_session *session)
{
    if (!session)
        return;

    CSIO_LOG(eLogLevel_debug,"ms.mice.sink.session.free { \"session-id\": %" G_GUINT64_FORMAT " , \"local-address\": \"%s\" , \"remote-address\": \"%s\" , \"state\": \"%s\" , \"friendly-name\": \"%s\" , \"source-id\": \"%s\" }",
              session->priv->session_id, session->priv->local_address, session->priv->remote_address, ms_mice_sink_session_state_to_string(session->priv->state), session->priv->friendly_name, session->priv->source_id);

    if (!session->priv->local_address)
    	g_free((gpointer)session->priv->local_address);
    if (!session->priv->remote_address)
    	g_free((gpointer)session->priv->remote_address);
    if (!session->priv->source_id)
    	g_free((gpointer)session->priv->source_id);

    if (!session->priv)
    	g_free(session->priv);
    g_free(session);
}

