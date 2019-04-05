#ifndef CTL_EXTENSION_MS_MICE_SINK_SESSION_H
#define CTL_EXTENSION_MS_MICE_SINK_SESSION_H

#include "ms-mice-tlv.h"
#include "ms-mice-messages.h"
#include "ms-mice-sink-service.h"
#include <glib.h>
#include <gio/gio.h>
//#include <ctl/extensions/sink-api.h>

#define MS_MICE_MIN_DTLS_CRYPTBUF_SIZE (512)

/* ------------------------------------------------------------------------------------------------------------------
 * -- MS-MICE SINK SESSION CONSTANTS
 * -- */

typedef enum MS_MICE_SINK_SESSION_STATE {
    MS_MICE_SINK_SESSION_STATE_DISCONNECTED = 0x00,
    MS_MICE_SINK_SESSION_STATE_CONNECTED = 0x01,
    MS_MICE_SINK_SESSION_STATE_SESSION_REQUESTED = 0x02,
    MS_MICE_SINK_SESSION_STATE_WAITING_FOR_SECURITY_HANDSHAKE = 0x03,
    MS_MICE_SINK_SESSION_STATE_DTLS_HANDSHAKE = 0x04,
    MS_MICE_SINK_SESSION_STATE_DTLS_HANDSHAKE_COMPLETE = 0x05,
    MS_MICE_SINK_SESSION_STATE_PIN_WAITING = 0x06,
    MS_MICE_SINK_SESSION_STATE_PIN_CHALLENGE = 0x07,
    MS_MICE_SINK_SESSION_STATE_SOURCE_READY = 0x08,
    MS_MICE_SINK_SESSION_STATE_PROJECTION = 0x09,
} MS_MICE_SINK_SESSION_STATE;

/* ------------------------------------------------------------------------------------------------------------------
 * -- MS-MICE SINK SESSION DEFINITIONS
 * -- */

typedef struct _ms_mice_sink_session_private ms_mice_sink_session_private;

struct _ms_mice_sink_session {
    struct shl_dlist list;
    ms_mice_sink_session_private *priv;
};

typedef struct {
    /* once the session disconnected returns from callback; the session is no longer valid; DON'T ACCESS after returning */
    void (*on_session_disconnected)(ms_mice_sink_session *session, gpointer data);

    void (*on_session_state_changed)(ms_mice_sink_session *session, MS_MICE_SINK_SESSION_STATE from, MS_MICE_SINK_SESSION_STATE to, gpointer data);
    void (*on_session_source_id_set)(ms_mice_sink_session *session, const char* source_id, gpointer data);
    void (*on_session_source_friendly_name_set)(ms_mice_sink_session *session, const char* friendly_name, gpointer data);

    void (*on_session_source_ready)(ms_mice_sink_session *session, guint16 rtsp_port, gpointer data);
    void (*on_session_stop_projecting)(ms_mice_sink_session *session, gpointer data);
} ms_mice_sink_session_observer;

/* ------------------------------------------------------------------------------------------------------------------
 * -- MS-MICE SINK SESSION CONSTRUCTION
 * -- */

void ms_mice_sink_session_new(ms_mice_sink_session **out, ms_mice_sink_service *service, guint64 session_id, GError **error);
void ms_mice_sink_session_free(ms_mice_sink_session *session);

void ms_mice_sink_session_observer_attach(ms_mice_sink_session *session, ms_mice_sink_session_observer *observer, gpointer data);
void ms_mice_sink_session_observer_detach(ms_mice_sink_session *session);

/* ------------------------------------------------------------------------------------------------------------------
 * -- MS-MICE SINK SESSION METHODS
 * -- */

void ms_mice_sink_session_connected(ms_mice_sink_session *session, GSocketConnection *connection, GError **error);
void ms_mice_sink_session_close(ms_mice_sink_session *session);

/* ------------------------------------------------------------------------------------------------------------------
 * -- MS-MICE SINK SESSION PROPERTIES
 * -- */

//sink_api_session *ms_mice_sink_session_get_api(ms_mice_sink_session *session);
//void ms_mice_sink_session_set_api(ms_mice_sink_session *session, sink_api_session *api_session);
//sink_api_session *ms_mice_sink_session_update_api(ms_mice_sink_session *session, guint16 rtsp_port);
bool ms_mice_sink_session_is_connected(ms_mice_sink_session *session);
MS_MICE_SINK_SESSION_STATE ms_mice_sink_session_get_state(ms_mice_sink_session *session);
guint64 ms_mice_sink_session_get_id(ms_mice_sink_session *session);
const char *ms_mice_sink_session_get_friendly_name(ms_mice_sink_session *session);
const char *ms_mice_sink_session_get_source_id(ms_mice_sink_session *session);
const char *ms_mice_sink_session_get_local_address(ms_mice_sink_session *session);
const char *ms_mice_sink_session_get_remote_address(ms_mice_sink_session *session);
bool ms_mice_sink_session_get_display_pin_option(ms_mice_sink_session *session);
bool ms_mice_sink_session_get_use_dtls_encryption_option(ms_mice_sink_session *session);


/* ------------------------------------------------------------------------------------------------------------------
 * -- MS-MICE SINK SESSION UTILITIES
 * -- */

const char *ms_mice_sink_session_state_to_string(MS_MICE_SINK_SESSION_STATE state);

#endif /* CTL_EXTENSION_MS_MICE_SINK_SESSION_H */
