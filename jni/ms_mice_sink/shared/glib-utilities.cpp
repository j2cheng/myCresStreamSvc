#include <stdio.h>
#include <sys/epoll.h>
#include <stdbool.h>
#include "glib-utilities.h"
#include "shl_macro.h"
//#include "support/android-miraclecast-common.h"

/* ------------------------------------------------------------------------------------------------------------------
 * -- support methods for encoding/decoding
 * -- */

#define streq(a,b) (strcmp((a),(b)) == 0)
#define strneq(a, b, n) (strncmp((a), (b), (n)) == 0)
#define strcaseeq(a,b) (strcasecmp((a),(b)) == 0)
#define strncaseeq(a, b, n) (strncasecmp((a), (b), (n)) == 0)

static char hexchar(int x)
{
    static const char table[16+1] = "0123456789abcdef";

    return table[x & 15];
}

static int unhexchar(char c)
{
    if (c >= '0' && c <= '9')
        return c - '0';

    if (c >= 'a' && c <= 'f')
        return c - 'a' + 10;

    if (c >= 'A' && c <= 'F')
        return c - 'A' + 10;

    return -EINVAL;
}

static char *bus_label_escape(const char *s)
{
    char *r, *t;
    const char *f;

    /* Escapes all chars that D-Bus' object path cannot deal
     * with. Can be reversed with bus_path_unescape(). We special
     * case the empty string. */

    if (*s == 0)
        return strdup("_");

    r = (char*)malloc(strlen(s)*3 + 1);
    if (!r)
        return NULL;

    for (f = s, t = r; *f; f++) {

        /* Escape everything that is not a-zA-Z0-9. We also
         * escape 0-9 if it's the first character */

        if (!(*f >= 'A' && *f <= 'Z') &&
            !(*f >= 'a' && *f <= 'z') &&
            !(f > s && *f >= '0' && *f <= '9')) {
            *(t++) = '_';
            *(t++) = hexchar(*f >> 4);
            *(t++) = hexchar(*f);
        } else
            *(t++) = *f;
    }

    *t = 0;

    return r;
}

static char *bus_label_unescape_n(const char *f, size_t l) {
    char *r, *t;
    size_t i;

    /* Special case for the empty string */
    if (l == 1 && *f == '_')
        return strdup("");

    r = (char*)malloc(l + 1);
    if (!r)
        return NULL;

    for (i = 0, t = r; i < l; ++i) {
        if (f[i] == '_') {
            int a, b;

            if (l - i < 3 ||
                (a = unhexchar(f[i + 1])) < 0 ||
                (b = unhexchar(f[i + 2])) < 0) {
                /* Invalid escape code, let's take it literal then */
                *(t++) = '_';
            } else {
                *(t++) = (char) ((a << 4) | b);
                i += 2;
            }
        } else
            *(t++) = f[i];
    }

    *t = 0;

    return r;
}

static size_t strlen_ptr(const char *s) {
    if (!s)
        return 0;

    return strlen(s);
}

static char *bus_label_unescape(const char *f) {
    return bus_label_unescape_n(f, strlen_ptr(f));
}

static bool object_path_is_valid(const char *p) {
    const char *q;
    bool slash;

    if (!p)
        return false;

    if (p[0] != '/')
        return false;

    if (p[1] == 0)
        return true;

    for (slash = true, q = p+1; *q; q++)
        if (*q == '/') {
            if (slash)
                return false;

            slash = true;
        } else {
            bool good;

            good =
                    (*q >= 'a' && *q <= 'z') ||
                    (*q >= 'A' && *q <= 'Z') ||
                    (*q >= '0' && *q <= '9') ||
                    *q == '_';

            if (!good)
                return false;

            slash = false;
        }

    if (slash)
        return false;

    return true;
}

static char *startswith(const char *s, const char *prefix) {
    size_t l;

    l = strlen(prefix);
    if (strncmp(s, prefix, l) == 0)
        return (char*) s + l;

    return NULL;
}

static char* object_path_startswith(const char *a, const char *b) {
    const char *p;

    if (!object_path_is_valid(a) ||
        !object_path_is_valid(b))
        return NULL;

    if (streq(b, "/"))
        return (char*) a + 1;

    p = startswith(a, b);
    if (!p)
        return NULL;

    if (*p == 0)
        return (char*) p;

    if (*p == '/')
        return (char*) p + 1;

    return NULL;
}

/* ------------------------------------------------------------------------------------------------------------------
 * -- utilities for encoding/decoding the object path
 * -- */

int gdbus_object_path_encode(const char *prefix, const char *external_id, char **ret_path)
{
    _shl_free_ char *e = NULL;
    int r = 0;
    char *buf = NULL;
    size_t buf_size = 0;

    e = bus_label_escape(external_id);
    if (!e)
        return -ENOMEM;

    buf_size = strlen(prefix) + strlen(e) + 2;
    buf = (char*)malloc(buf_size + 1);
    if (!buf)
        return -ENOMEM;

    r = snprintf(buf, buf_size, "%s/%s", prefix, e);
    if (r <= 0)
        return -ENOMEM;

    *ret_path = buf;
    return 0;
}

int gdbus_object_path_decode(const char *path, const char *prefix, char **external_id)
{
    const char *e;
    char *ret;
    *external_id = NULL;

    e = object_path_startswith(path, prefix);
    if (!e) {
        *external_id = NULL;
        return 0;
    }

    ret = bus_label_unescape(e);
    if (!ret)
        return -ENOMEM;

    *external_id = ret;
    return 1;
}

/* ------------------------------------------------------------------------------------------------------------------
 * -- convert usec to msec
 * -- */

extern guint32 mcast_convert_usec_to_msec(guint64 usec);

/* ------------------------------------------------------------------------------------------------------------------
 * -- gio condition to epoll mask
 * -- */

const char* g_io_condition_to_string(GIOCondition cond)
{
    GString *s;
    bool append = false;

    s = g_string_new (NULL);

    if (cond & G_IO_IN) {
        g_string_append_printf (s, "IN");
        append = true;
    }

    if (cond & G_IO_OUT) {
        if (append) g_string_append_printf (s, "|");
        g_string_append_printf (s, "OUT");
        append = true;
    }

    if (cond & G_IO_PRI) {
        if (append) g_string_append_printf (s, "|");
        g_string_append_printf (s, "PRI");
        append = true;
    }

    if (cond & G_IO_ERR) {
        if (append) g_string_append_printf (s, "|");
        g_string_append_printf (s, "ERR");
        append = true;
    }

    if (cond & G_IO_HUP) {
        if (append) g_string_append_printf (s, "|");
        g_string_append_printf (s, "HUP");
        append = true;
    }

    if (cond & G_IO_NVAL) {
        if (append) g_string_append_printf (s, "|");
        g_string_append_printf (s, "NVAL");
        append = true;
    }

    return g_string_free(s, FALSE);
}

unsigned int mcast_g_io_condition_to_epoll_mask(GIOCondition cond)
{
    uint32_t mask = 0;

    if (cond & G_IO_IN) mask |= EPOLLIN;
    if (cond & G_IO_ERR) mask |= EPOLLERR;
    if (cond & G_IO_HUP) mask |= EPOLLHUP;
    if (cond & G_IO_OUT) mask |= EPOLLOUT;

    return mask;
}

GIOCondition mcast_g_io_epoll_mask_to_condition(unsigned int mask)
{
    unsigned int cond = 0;

    if (mask & EPOLLIN) cond |= G_IO_IN;
    if (mask & EPOLLERR) cond |= G_IO_ERR;
    if (mask & EPOLLHUP) cond |= G_IO_HUP;
    if (mask & EPOLLOUT) cond |= G_IO_OUT;

    return (GIOCondition)cond;
}

/* ------------------------------------------------------------------------------------------------------------------
 * -- gio watch that can override the source functions
 * -- */

static gboolean mcast_g_io_watch_source_prepare_proxy(GSource *source, gint *timeout);

static gboolean mcast_g_io_watch_source_base_prepare(GSource *source, gint *timeout);
static gboolean mcast_g_io_watch_source_base_check(GSource *source);
static gboolean mcast_g_io_watch_source_base_dispatch(GSource *source, GSourceFunc callback, gpointer user_data);
static void mcast_g_io_watch_source_base_finalize(GSource *source);

GSourceFuncs mcast_g_io_watch_source_funcs = {
        mcast_g_io_watch_source_prepare_proxy,
        mcast_g_io_watch_source_base_check,
        mcast_g_io_watch_source_base_dispatch,
        mcast_g_io_watch_source_base_finalize
};

typedef struct
{
    GSource source;
    GIOChannel *channel;
    GIOCondition condition;
    gpointer source_id;
    mcast_g_io_watch_prepare_override prepare;
    gpointer user_data;
} GioOverrideableWatch;

/* --
 * -- utilities
 * -- */
static GIOCondition check_for_any_conditions(GIOCondition current, GIOCondition mask)
{
    return (GIOCondition)(current & mask);
}

static gboolean is_all_conditions_met(GIOCondition current, GIOCondition mask)
{
    return check_for_any_conditions(current, mask) == mask;
}

static gboolean mcast_g_io_watch_source_prepare_proxy(GSource *source, gint *timeout)
{
    GioOverrideableWatch *watch = (GioOverrideableWatch *)source;

    mcast_g_io_watch_prepare_override prepare = watch->prepare;

    return (!prepare)
        ? mcast_g_io_watch_source_base_prepare(source, timeout)
        : prepare(source, timeout, watch->user_data);
}

static gboolean mcast_g_io_watch_source_base_prepare(GSource *source, gint *timeout)
{
    GioOverrideableWatch *watch = (GioOverrideableWatch *)source;
//    GIOCondition buffer_condition = g_source_query_unix_fd (watch, watch->source_id);
//  fprintf(stderr, "\nmcast_g_io_watch_source_base_prepare  *******Condition %x\n", buffer_condition);

    *timeout = -1;

    /*
     * https://developer.gnome.org/glib/stable/glib-The-Main-Event-Loop.html#GSourceFuncs
     *
     * For file descriptor sources, the prepare function typically returns FALSE, since it must wait until poll() has
     * been called before it knows whether any events need to be processed. It sets the returned timeout to -1 to
     * indicate that it doesn't mind how long the poll() call blocks. In the check function, it tests the results of
     * the poll() call to see if the required condition has been met, and returns TRUE if so.
     *
     * Only return TRUE here if _all_ bits in watch->condition will be set
     */

//    return is_all_conditions_met(buffer_condition, watch->condition);
    return 0;
}

static gboolean mcast_g_io_watch_source_base_check(GSource *source)
{
    GioOverrideableWatch *watch = (GioOverrideableWatch *)source;
    GIOCondition buffer_condition = g_source_query_unix_fd (source, watch->source_id);

    /*
     * https://developer.gnome.org/glib/stable/glib-The-Main-Event-Loop.html#GSourceFuncs
     *
     * Called after all the file descriptors are polled. The source should return TRUE if it is ready to be dispatched.
     * Note that some time may have passed since the previous prepare function was called, so the source should be
     * checked again here.
     */


    return check_for_any_conditions(buffer_condition, watch->condition);
}

static gboolean mcast_g_io_watch_source_base_dispatch(GSource *source, GSourceFunc callback, gpointer user_data)
{
    GIOFunc func = (GIOFunc)callback;
    GioOverrideableWatch *watch = (GioOverrideableWatch *)source;
    GIOCondition buffer_condition = g_source_query_unix_fd (source, watch->source_id);


    if (!func)
    {
        g_warning ("IO watch dispatched without callback. You must call g_source_connect().");
        return FALSE;
    }

    /*
     * https://developer.gnome.org/glib/stable/glib-The-Main-Event-Loop.html#GSourceFuncs
     *
     * Called to dispatch the event source, after it has returned TRUE in either its prepare or its check function,
     * or if a ready time has been reached. The dispatch function receives a callback function and user data. The
     * callback function may be NULL if the source was never connected to a callback using g_source_set_callback().
     * The dispatch function should call the callback function with user_data and whatever additional parameters are
     * needed for this type of event source. The return value of the dispatch function should be G_SOURCE_REMOVE if
     * the source should be removed or G_SOURCE_CONTINUE to keep it.
     */

    return (*func) (watch->channel, check_for_any_conditions(buffer_condition, watch->condition), user_data);
}

static void mcast_g_io_watch_source_base_finalize(GSource *source)
{
    GioOverrideableWatch *watch = (GioOverrideableWatch *)source;

    g_io_channel_unref (watch->channel);

    g_source_remove_unix_fd(source, watch->source_id);
}

guint mcast_g_io_add_watch (
        GIOChannel *channel,
        gint priority,
        GIOCondition condition,
        mcast_g_io_watch_prepare_override prepare,
        GIOFunc func,
        gpointer user_data,
        GDestroyNotify notify,
        GMainContext* context)
{
    GSource *source;
    guint id;

    g_return_val_if_fail (channel != NULL, 0);

    source = mcast_g_io_watch_create (channel, condition);

    mcast_g_io_watch_override (source, prepare, user_data);

    if (priority != G_PRIORITY_DEFAULT)
        g_source_set_priority (source, priority);
    g_source_set_callback (source, (GSourceFunc)func, user_data, notify);

    id = g_source_attach (source, context);

    g_source_unref (source);

    return id;
}

GSource *mcast_g_io_watch_create(GIOChannel *channel, GIOCondition condition)
{
    GSource *source;
    GioOverrideableWatch *watch;

    source = g_source_new (&mcast_g_io_watch_source_funcs, sizeof (GioOverrideableWatch));
    g_source_set_name (source, "GIOChannel (Unix) w/ overrideable prepare");
    watch = (GioOverrideableWatch *)source;

    watch->channel = channel;
    g_io_channel_ref (channel);

    watch->condition = condition;

    watch->source_id = g_source_add_unix_fd (source, g_io_channel_unix_get_fd(channel), condition);

    return source;
}

void mcast_g_io_watch_override(GSource *source, mcast_g_io_watch_prepare_override prepare, gpointer user_data)
{
    GioOverrideableWatch *watch = (GioOverrideableWatch *)source;

    watch->prepare = prepare;
    watch->user_data = user_data;
}

gboolean mcast_g_io_watch_call_base_prepare(GSource *source, gint *timeout)
{
    return mcast_g_io_watch_source_base_prepare(source, timeout);
}

GIOCondition mcast_g_io_watch_condition_get(GSource *source)
{
    GioOverrideableWatch *watch = (GioOverrideableWatch *)source;

    return watch->condition;
}

void mcast_g_io_watch_condition_set(GSource *source, GIOCondition condition)
{
    GioOverrideableWatch *watch = (GioOverrideableWatch *)source;

    if (watch->condition == condition) return;

    watch->condition = condition;
    g_source_modify_unix_fd (source, watch->source_id, condition);
}

/* ------------------------------------------------------------------------------------------------------------------
 * -- gio watch registers for a list of signals
 * -- */

typedef struct
{
    GSource source;
    int signals;
    gpointer source_id;
    gpointer user_data;
} GioSignalWatch;

guint mcast_g_io_add_signal_watch (gint priority, int signals[], mcast_g_io_watch_signals callback, gpointer user_data, GDestroyNotify notify)
{
    return 0;
}
