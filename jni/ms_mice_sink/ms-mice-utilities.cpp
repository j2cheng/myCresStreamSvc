//#include <shl_log.h>
#include "ms-mice.h"


const gchar *string_convert(const gchar *in, gsize in_bytes, const gchar *from_encoding, const gchar *to_encoding, gsize* out_bytes)
{
    g_autoptr(GError) error = NULL;
    gsize bytes_read = 0, bytes_written = 0;
    gchar *out = NULL;

    if (!in || !in_bytes)
        return out;

    out = g_convert(in, in_bytes, to_encoding, from_encoding, &bytes_read, &bytes_written, &error);

    if (error) {
        CSIO_LOG(eLogLevel_error,"string.conversion.error { \"from\": \"%s\" , \"to\": \"%s\" , \"code\": %u , \"message\": \"%s\" }", from_encoding, to_encoding, error->code, error->message);
    }

    if (out_bytes) {
        *out_bytes = bytes_written;
    }

    return out;
}

const gchar *string_convert_utf16_to_utf8(const gchar *in, gsize in_bytes, gsize* out_bytes)
{
    return string_convert(in, in_bytes, CHAR_ENCODING_UTF16, CHAR_ENCODING_UTF8, out_bytes);
}

const gchar *string_convert_utf16le_to_utf8(const gchar *in, gsize in_bytes, gsize* out_bytes)
{
    return string_convert(in, in_bytes, CHAR_ENCODING_UTF16_LE, CHAR_ENCODING_UTF8, out_bytes);
}

const gchar *string_convert_utf16be_to_utf8(const gchar *in, gsize in_bytes, gsize* out_bytes)
{
    return string_convert(in, in_bytes, CHAR_ENCODING_UTF16_BE, CHAR_ENCODING_UTF8, out_bytes);
}

const gchar *string_convert_utf8_to_utf16(const gchar *in, gsize in_bytes, gsize* out_bytes)
{
    return string_convert(in, in_bytes, CHAR_ENCODING_UTF8, CHAR_ENCODING_UTF16, out_bytes);
}

const gchar *string_convert_utf8_to_utf16le(const gchar *in, gsize in_bytes, gsize* out_bytes)
{
    return string_convert(in, in_bytes, CHAR_ENCODING_UTF8, CHAR_ENCODING_UTF16_LE, out_bytes);
}

const gchar *string_convert_utf8_to_utf16be(const gchar *in, gsize in_bytes, gsize* out_bytes)
{
    return string_convert(in, in_bytes, CHAR_ENCODING_UTF8, CHAR_ENCODING_UTF16_BE, out_bytes);
}

static const gunichar2* string_utf8_to_utf16(const gchar *in, glong in_length, glong *out_length)
{
    g_autoptr(GError) error = NULL;
    glong items_read = 0, items_written = 0;

    gunichar2 *out= g_utf8_to_utf16(in, in_length, &items_read, &items_written, &error);

    if (error) {
        CSIO_LOG(eLogLevel_error,"string.conversion.utf-8.to.utf-16 { \"code\": %u , \"message\": \"%s\" }", error->code, error->message);
    }

    if (out_length) {
        *out_length = items_written;
    }

    return out;
}

static const gchar* string_utf16_to_utf8(const gunichar2 *in, glong in_length, glong *out_length)
{
    g_autoptr(GError) error = NULL;
    glong items_read = 0, items_written = 0;

    gchar *out = g_utf16_to_utf8(in, in_length, &items_read, &items_written, &error);

    if (error) {
        CSIO_LOG(eLogLevel_error,"string.conversion.utf-16.to.utf-8 { \"code\": %u , \"message\": \"%s\" }", error->code, error->message);
    }

    if (out_length) {
        *out_length = items_written;
    }

    return out;
}

void stream_read_bytes(GDataInputStream *stream, guint8 *dst, size_t count)
{
    for (size_t i = 0; i < count; i++) {
        dst[i] = stream_read_byte(stream);
    }
}

void stream_advance(GDataInputStream *stream, size_t count)
{
    for (size_t i = 0; i < count; i++) {
        stream_read_byte(stream);
    }
}

void stream_write_bytes(GDataOutputStream *stream, const guint8 *values, size_t count)
{
    for (size_t i = 0; i < count; i++) {
        stream_write_byte(stream, values[i]);
    }
}

static GMutex sesson_id_generator_lock;
static guint64 sesson_id_generator;

//Note: copy from app-runtime.c
//      generate only non-zero positive numbers
guint64 generate_sink_session_id()
{
    g_mutex_lock(&sesson_id_generator_lock);
    ++sesson_id_generator;
    sesson_id_generator &= G_MAXINT64;

    if (!sesson_id_generator) ++sesson_id_generator;
    g_mutex_unlock(&sesson_id_generator_lock);
    return sesson_id_generator;
}
