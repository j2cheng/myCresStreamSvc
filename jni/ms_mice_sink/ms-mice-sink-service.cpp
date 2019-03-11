#include <glib.h>

//#include <wifid-gdbus.h>
//#include <shl_dlist.h>
#include <netinet/in.h>
#include <arpa/inet.h>
//#include "shared/shl_util.h"
//#include <util.h>
//#include <ctl/sink-app-runtime.h>
//#include "ctl/ctl.h"
#include "ms-mice.h"

struct _ms_mice_sink_service_private {
    const gchar *service_address;
    guint16 service_port;

    bool is_service_started;
    GSocketService *socket_service;
    gulong incoming_handler_id;

    ms_mice_sink_service_observer *observer;
    gpointer observer_data;

    GMainContext* context;
};

bool ms_mice_sink_service_is_running(ms_mice_sink_service *service) { return service->priv->is_service_started; }
const char *ms_mice_sink_service_get_address(ms_mice_sink_service *service) { return service->priv->service_address; }
guint16 ms_mice_sink_service_get_service_port(ms_mice_sink_service *service) { return service->priv->service_port; }
GMainContext* ms_mice_sink_service_get_context(ms_mice_sink_service *service) { return service->priv->context; }

void ms_mice_sink_service_raise_on_service_started(ms_mice_sink_service *service)
{
    ms_mice_sink_service_observer *observer = service->priv->observer;

    if (observer) {
        gpointer observer_data = service->priv->observer_data;
        void (*func)(ms_mice_sink_service *, gpointer) = observer->on_service_started;
        if (func)
            func(service, observer_data);
    }
}

void ms_mice_sink_service_raise_on_service_stopped(ms_mice_sink_service *service)
{
    ms_mice_sink_service_observer *observer = service->priv->observer;

    if (observer) {
        gpointer observer_data = service->priv->observer_data;
        void (*func)(ms_mice_sink_service *, gpointer) = observer->on_service_stopped;
        if (func)
            func(service, observer_data);
    }
}

void ms_mice_sink_service_raise_on_session_connected(ms_mice_sink_service *service, ms_mice_sink_session *session)
{
    ms_mice_sink_service_observer *observer = service->priv->observer;

    if (observer) {
        gpointer observer_data = service->priv->observer_data;
        void (*func)(ms_mice_sink_service *, ms_mice_sink_session *, gpointer) = observer->on_session_connected;
        if (func)
            func(service, session, observer_data);
    }   
}
 

/* ------------------------------------------------------------------------------------------------------------------
 * -- MS-MICE SERVICE
 * -- */

static void ms_mice_sink_service_on_accept(ms_mice_sink_service *service, GSocketConnection *connection, GError **error)
{
    ms_mice_sink_session* session = NULL;

    CSIO_LOG(eLogLevel_info,"ms.mice.sink.service.accept { \"service-address\": \"%s\" , \"service-port\": %u }", service->priv->service_address, service->priv->service_port);

    //TODO: warning: need to keep track of max clients!!!
    ms_mice_sink_session_new(&session, service, generate_sink_session_id(), error);

    ms_mice_sink_session_connected(session, connection, error);

    ms_mice_sink_service_raise_on_session_connected(service, session);
}

static gboolean ms_mice_sink_service_on_accept_fn(
        GSocketService *socket_service,
        GSocketConnection *connection,
        GObject *source_object,
        gpointer user_data)
{
    g_autoptr(GError) error = NULL;
    ms_mice_sink_service_on_accept((ms_mice_sink_service *)user_data, connection, &error);
    return FALSE;
}

void ms_mice_sink_service_start(ms_mice_sink_service *service, GMainContext* mainLoopCntext,GError **error)
{
    g_autoptr(GError) internal_error = NULL;

    if (service->priv->is_service_started) {
        CSIO_LOG(eLogLevel_error,"ms.mice.sink.service.start.warning { \"service-address\": \"%s\" , \"service-port\": %u , \"message\": \"service already started!\" }", service->priv->service_address, service->priv->service_port);
        return;
    }

    CSIO_LOG(eLogLevel_info,"ms.mice.sink.service.start { \"service-address\": \"%s\" , \"service-port\": %u }", service->priv->service_address, service->priv->service_port);

    service->priv->context = mainLoopCntext;

    service->priv->socket_service = g_socket_service_new();

    service->priv->incoming_handler_id = g_signal_connect(service->priv->socket_service, "incoming", G_CALLBACK (ms_mice_sink_service_on_accept_fn), service);

    if (shl_isempty(service->priv->service_address)) {
        g_socket_listener_add_inet_port(G_SOCKET_LISTENER(service->priv->socket_service), service->priv->service_port, NULL, &internal_error);
    } else {
        g_autoptr(GSocketAddress) address = g_inet_socket_address_new_from_string(service->priv->service_address, service->priv->service_port);

        g_socket_listener_add_address (G_SOCKET_LISTENER(service->priv->socket_service),
                                       address,
                                       G_SOCKET_TYPE_STREAM,
                                       G_SOCKET_PROTOCOL_TCP,
                                       NULL, NULL, &internal_error);
    }

    if (internal_error) {
        CSIO_LOG(eLogLevel_error,"ms.mice.sink.service.start.error { \"service-address\": \"%s\" , \"service-port\": %u , \"code\": %u , \"message\": \"%s\" }", service->priv->service_address, service->priv->service_port, internal_error->code, internal_error->message);
        if (error) {
            *error = internal_error;
            internal_error = NULL;
        }
        return;
    }

    service->priv->is_service_started = true;
    CSIO_LOG(eLogLevel_info,"ms.mice.sink.service.start.complete { \"service-address\": \"%s\" , \"service-port\": %u }", service->priv->service_address, service->priv->service_port);

    ms_mice_sink_service_raise_on_service_started(service);
}


void ms_mice_sink_service_stop(ms_mice_sink_service *service)
{
    bool stopped;

    if (!service)
        return;

    if (service->priv->is_service_started)
        CSIO_LOG(eLogLevel_info,"ms.mice.sink.service.stop { \"service-address\": \"%s\" , \"service-port\": %u }", service->priv->service_address, service->priv->service_port);

    if (service->priv->incoming_handler_id > 0) {
        g_signal_handler_disconnect(service->priv->socket_service, service->priv->incoming_handler_id);
        service->priv->incoming_handler_id = 0;
    }

    if (service->priv->socket_service) {
        g_socket_service_stop(service->priv->socket_service);
        g_socket_listener_close(G_SOCKET_LISTENER(service->priv->socket_service));
        g_object_unref(service->priv->socket_service);
        service->priv->socket_service = NULL;
    }

    while (!shl_dlist_empty(&service->sessions)) {
        ms_mice_sink_session *session = shl_dlist_first_entry(&service->sessions, ms_mice_sink_session, list);
        ms_mice_sink_session_close(session);
    }

    stopped = service->priv->is_service_started;
    service->priv->is_service_started = false;

    if (stopped)
        ms_mice_sink_service_raise_on_service_stopped(service);
}

void ms_mice_sink_service_on_session_closed(ms_mice_sink_service *service, ms_mice_sink_session *session)
{
    ms_mice_sink_session_free(session);
}

ms_mice_sink_session *ms_mice_sink_service_find_session_by_id(ms_mice_sink_service *service, guint64 id)
{
    struct shl_dlist *iter;
    ms_mice_sink_session *session = NULL;
    shl_dlist_for_each(iter, &service->sessions) {
        session = shl_dlist_entry(iter, ms_mice_sink_session, list);
        if (ms_mice_sink_session_get_id(session) == id) {
            return session;
        }
    }
    return NULL;
}

ms_mice_sink_session *ms_mice_sink_service_find_session_by_source_id(ms_mice_sink_service *service, const char* source_id)
{
    struct shl_dlist *iter;
    ms_mice_sink_session *session = NULL;
    shl_dlist_for_each(iter, &service->sessions) {
        session = shl_dlist_entry(iter, ms_mice_sink_session, list);
        if (strings_are_equal(ms_mice_sink_session_get_source_id(session), source_id)) {
            return session;
        }
    }
    return NULL;
}

void ms_mice_sink_service_observer_attach(ms_mice_sink_service *service, ms_mice_sink_service_observer *observer, gpointer data)
{
    service->priv->observer = observer;
    service->priv->observer_data = data;
}

void ms_mice_sink_service_observer_detach(ms_mice_sink_service *service)
{
    service->priv->observer_data = NULL;
    service->priv->observer = NULL;
}

/* ------------------ */
/* -- CONSTRUCTION -- */

void ms_mice_sink_service_new(ms_mice_sink_service **out, const gchar *address, guint16 port, GError **error)
{
    ms_mice_sink_service *s = NULL;

    if (!out) {
        if (error) {
            *error = g_error_new(G_IO_ERROR, G_IO_ERROR_INVALID_ARGUMENT, "No service reference");
        }
        return;
    }

    s = g_new0(ms_mice_sink_service, 1);
    if (!s) {
        if (error) {
            *error = g_error_new(G_IO_ERROR, G_IO_ERROR_NO_SPACE, "No more memory error");
        }
        return;
    }

    s->priv = g_new0(ms_mice_sink_service_private, 1);

    CSIO_LOG(eLogLevel_info,"ms.mice.sink.service.new { \"service-address\": \"%s\" , \"service-port\": %u }", address, port);

    s->priv->service_address = g_strdup(address);
    s->priv->service_port = port;
    shl_dlist_init(&s->sessions);
    s->priv->socket_service = NULL;
    s->priv->incoming_handler_id = 0;
    s->priv->is_service_started = false;
    s->priv->observer = NULL;
    s->priv->observer_data = NULL;
    s->priv->context = NULL;

#if ENABLE_DTLS
    ms_mice_initialize_ssl_runtime();
#endif

    *out = s;
}

void ms_mice_sink_service_free(ms_mice_sink_service *service)
{
    if (!service)
        return;

    ms_mice_sink_service_stop(service);

    CSIO_LOG(eLogLevel_info,"ms.mice.sink.service.free { \"service-address\": \"%s\" , \"service-port\": %u }", service->priv->service_address, service->priv->service_port);

    g_free((gpointer)service->priv->service_address);
    service->priv->observer = NULL;
    service->priv->observer_data = NULL;
    g_free(service->priv);
    service->priv = NULL;
    g_free(service);
#if ENABLE_DTLS
    ms_mice_destroy_ssl_runtime();
#endif
}
