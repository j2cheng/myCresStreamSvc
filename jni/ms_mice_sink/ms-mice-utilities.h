#ifndef CTL_EXTENSION_MS_MICE_UTILITIES_H
#define CTL_EXTENSION_MS_MICE_UTILITIES_H

#include <glib.h>
#include <gio/gio.h>

/* ------------------------------------------------------------------------------------------------------------------
 * -- UTILITIES
 * -- */

#define CHAR_ENCODING_ISO_8859_1 "ISO-8859-1"
#define CHAR_ENCODING_ASCII "ASCII"
#define CHAR_ENCODING_UTF8 "UTF-8"
#define CHAR_ENCODING_UTF16 "UTF-16"
#define CHAR_ENCODING_UTF16_BE "UTF-16BE"
#define CHAR_ENCODING_UTF16_LE "UTF-16LE"

const gchar *string_convert(const gchar *in, gsize in_bytes, const gchar *from_encoding, const gchar *to_encoding, gsize* out_bytes);

const gchar *string_convert_utf16_to_utf8(const gchar *in, gsize in_bytes, gsize* out_bytes);
const gchar *string_convert_utf16le_to_utf8(const gchar *in, gsize in_bytes, gsize* out_bytes);
const gchar *string_convert_utf16be_to_utf8(const gchar *in, gsize in_bytes, gsize* out_bytes);

const gchar *string_convert_utf8_to_utf16(const gchar *in, gsize in_bytes, gsize* out_bytes);
const gchar *string_convert_utf8_to_utf16le(const gchar *in, gsize in_bytes, gsize* out_bytes);
const gchar *string_convert_utf8_to_utf16be(const gchar *in, gsize in_bytes, gsize* out_bytes);

static inline gsize stream_get_available(GDataInputStream *stream)
{
    return g_buffered_input_stream_get_available(G_BUFFERED_INPUT_STREAM(stream));
}

static inline guint8 stream_read_byte(GDataInputStream *stream)
{
    return g_data_input_stream_read_byte(stream, NULL, NULL);
}

void stream_read_bytes(GDataInputStream *stream, guint8 *dst, size_t count);

static inline guint16 stream_read_uint16(GDataInputStream *stream)
{
    return g_data_input_stream_read_uint16(stream, NULL, NULL);
}

void stream_advance(GDataInputStream *stream, size_t count);

static inline void stream_write_byte(GDataOutputStream *stream, guint8 value)
{
    g_data_output_stream_put_byte(stream, value, NULL, NULL);
}

void stream_write_bytes(GDataOutputStream *stream, const guint8 *values, size_t count);

static inline void stream_write_uint16(GDataOutputStream *stream, guint16 value)
{
    g_data_output_stream_put_uint16(stream, value, NULL, NULL);
}

static inline const gchar* socket_address_to_string(GSocketAddress *socket_address)
{
#define UNKNOWN_ADDRESS_STRING "0.0.0.0"

    GInetAddress *inet_address = NULL;
    g_autofree gchar * address = NULL;
    guint16 port = 0;
    GString* formatted = NULL;

    if (!socket_address)
        return UNKNOWN_ADDRESS_STRING;

    inet_address = g_inet_socket_address_get_address(G_INET_SOCKET_ADDRESS(socket_address));

    if (!inet_address)
        return UNKNOWN_ADDRESS_STRING;

    address = g_inet_address_to_string(inet_address);

    if (!address)
        return UNKNOWN_ADDRESS_STRING;

    formatted = g_string_new(NULL);

    g_string_printf(formatted, "%s", address);

    return g_string_free(formatted, FALSE);

#undef UNKNOWN_ADDRESS_STRING
}

static inline const gchar* socket_address_and_port_to_string(GSocketAddress *socket_address)
{
#define UNKNOWN_ADDRESS_PORT_STRING "0.0.0.0:0"

    GInetAddress *inet_address = NULL;
    g_autofree gchar * address = NULL;
    guint16 port = 0;
    GString* formatted = NULL;

    if (!socket_address)
        return UNKNOWN_ADDRESS_PORT_STRING;

    inet_address = g_inet_socket_address_get_address(G_INET_SOCKET_ADDRESS(socket_address));
    port = g_inet_socket_address_get_port(G_INET_SOCKET_ADDRESS(socket_address));

    if (!inet_address)
        return UNKNOWN_ADDRESS_PORT_STRING;

    address = g_inet_address_to_string(inet_address);

    if (!address)
        return UNKNOWN_ADDRESS_PORT_STRING;

    formatted = g_string_new(NULL);

    g_string_printf(formatted, "%s:%u", address, port);

    return g_string_free(formatted, FALSE);

#undef UNKNOWN_ADDRESS_PORT_STRING
}
//Note: copy from shl_util.h
static inline bool shl_isempty(const char *str)
{
    return !str || !*str;
}

guint64 generate_sink_session_id();

void ms_mice_sink_convertIP(char *pIPAddress, unsigned int& ldata);

#endif /* CTL_EXTENSION_MS_MICE_UTILITIES_H */
