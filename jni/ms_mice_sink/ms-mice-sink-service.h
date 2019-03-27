#ifndef CTL_EXTENSION_MS_MICE_SINK_SERVICE_H
#define CTL_EXTENSION_MS_MICE_SINK_SERVICE_H

/* ------------------------------------------------------------------------------------------------------------------
 * -- MS-MICE DEFINITIONS
 * -- */

#include "ms-mice-tlv.h"
#include "ms-mice-messages.h"
#include "ms-mice-sink-session.h"

/* ------------------------------------------------------------------------------------------------------------------
 * -- MS-MICE SINK SERVICE CONSTANTS
 * -- */

#define DEFAULT_MIRACAST_OVER_INFRASTRUCTURE_PORT 7250

/* ------------------------------------------------------------------------------------------------------------------
 * -- MS-MICE SINK SERVICE DEFINITIONS
 * -- */

typedef struct _ms_mice_sink_service_private ms_mice_sink_service_private;

struct _ms_mice_sink_service {
    struct shl_dlist sessions;
    ms_mice_sink_service_private *priv;
};

typedef struct {
    void (*on_service_started)(ms_mice_sink_service *service, gpointer data);
    void (*on_service_stopped)(ms_mice_sink_service *service, gpointer data);

    void (*on_session_connected)(ms_mice_sink_service *service, ms_mice_sink_session *session, gpointer data);
} ms_mice_sink_service_observer;

struct ms_mice_sink_service_and_sessionid {
    ms_mice_sink_service *service;
    guint64 session_id;
    gchar *session_pin;
};
/* ------------------------------------------------------------------------------------------------------------------
 * -- MS-MICE SINK SERVICE CONSTRUCTION
 * -- */

void ms_mice_sink_service_new(ms_mice_sink_service **out, const gchar *address, guint16 port, const gchar *session_pin, GError **error);
void ms_mice_sink_service_free(ms_mice_sink_service *service);

void ms_mice_sink_service_observer_attach(ms_mice_sink_service *service, ms_mice_sink_service_observer *observer, gpointer data);
void ms_mice_sink_service_observer_detach(ms_mice_sink_service *service);

/* ------------------------------------------------------------------------------------------------------------------
 * -- MS-MICE SINK SERVICE METHODS
 * -- */

void ms_mice_sink_service_start(ms_mice_sink_service *service, GMainContext* mainLoopCntext , GError **error);
void ms_mice_sink_service_stop(ms_mice_sink_service *service);

ms_mice_sink_session *ms_mice_sink_service_find_session_by_id(ms_mice_sink_service *service, guint64 id);
ms_mice_sink_session *ms_mice_sink_service_find_session_by_source_id(ms_mice_sink_service *service, const char* source_id);

void ms_mice_sink_service_on_session_closed(ms_mice_sink_service *service, ms_mice_sink_session *session);

/* ------------------------------------------------------------------------------------------------------------------
 * -- MS-MICE SINK SERVICE PROPERTIES
 * -- */

bool ms_mice_sink_service_is_running(ms_mice_sink_service *service);
const char *ms_mice_sink_service_get_address(ms_mice_sink_service *service);
guint16 ms_mice_sink_service_get_service_port(ms_mice_sink_service *service);
GMainContext* ms_mice_sink_service_get_context(ms_mice_sink_service *service);
const char *ms_mice_sink_service_get_session_pin(ms_mice_sink_service *service);
void ms_mice_sink_service_set_session_pin(ms_mice_sink_service *service,const char* pin);

#endif /* CTL_EXTENSION_MS_MICE_SINK_SERVICE_H */
