#ifndef SHARED_GDBUS_UTILITIES_H
#define SHARED_GDBUS_UTILITIES_H

#include <glib-unix.h>
#include <gio/gio.h>

/* ------------------------------------------------------------------------------------------------------------------
 * -- helper macros
 * -- */

#define BOOL_TO_STRING(b) ((b) ? "true" : "false")

#define BUS_TYPE_TO_STRING(v) \
    (v == G_BUS_TYPE_SYSTEM ? "system" \
    : (v == G_BUS_TYPE_SESSION ? "session" \
    : (v == G_BUS_TYPE_STARTER ? "starter" \
    : (v == G_BUS_TYPE_NONE ? "none" \
    : "unknown"))))

/* ------------------------------------------------------------------------------------------------------------------
 * -- utility to encode the external id and join with the prefix path
 * -- NOTE: object paths are restricted to only alpha-numeric characters, this method will encode illegal characters
 * -- */

int gdbus_object_path_encode(const char *prefix, const char *external_id, char **ret_path);
int gdbus_object_path_decode(const char *path, const char *prefix, char **external_id);

/* ------------------------------------------------------------------------------------------------------------------
 * -- convert usec to msec
 * -- */

inline guint32 mcast_convert_usec_to_msec(guint64 usec)
{
    guint64 msec = usec / G_TIME_SPAN_MILLISECOND;
    return (guint32)msec;
}

/* ------------------------------------------------------------------------------------------------------------------
 * -- utility to map gio condition to epoll mask
 * -- */

unsigned int mcast_g_io_condition_to_epoll_mask(GIOCondition cond);
GIOCondition mcast_g_io_epoll_mask_to_condition(unsigned int mask);
const char* g_io_condition_to_string(GIOCondition cond);

/* ------------------------------------------------------------------------------------------------------------------
 * -- gio watch that can override the source functions
 * -- NOTE:
 * --   1. only prepare is overrideable; if other methods are needed, it can be added when needed
 * --   2. this is only applicable to unix based on systems since it is using some of the g*source*unix api
 * --   3. mcast_g_io_add_watch is a convenience method, which will g_source_attach, you will need to call g_source_remove
 * -- */

typedef gboolean (*mcast_g_io_watch_prepare_override) (GSource *source, gint *timeout, gpointer user_data);

guint mcast_g_io_add_watch (GIOChannel *channel, gint priority, GIOCondition condition, mcast_g_io_watch_prepare_override prepare, GIOFunc func, gpointer user_data, GDestroyNotify notify);

GSource *mcast_g_io_watch_create(GIOChannel *channel, GIOCondition condition);

void mcast_g_io_watch_override(GSource *source, mcast_g_io_watch_prepare_override prepare, gpointer user_data);

gboolean mcast_g_io_watch_call_base_prepare(GSource *source, gint *timeout);

GIOCondition mcast_g_io_watch_condition_get(GSource *source);

void mcast_g_io_watch_condition_set(GSource *source, GIOCondition condition);

/* ------------------------------------------------------------------------------------------------------------------
 * -- gio watch for signals
 * -- */

typedef gboolean (*mcast_g_io_watch_signals) (GSource *source, int siganl, gpointer user_data);

guint mcast_g_io_add_signal_watch (gint priority, int signals[], mcast_g_io_watch_signals callback, gpointer user_data, GDestroyNotify notify);

#endif /* SHARED_GDBUS_UTILITIES_H */
