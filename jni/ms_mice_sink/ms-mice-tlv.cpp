#include <gio/gio.h>
//#include <shl_log.h>
#include "shared/glib-utilities.h"
#include "ms-mice.h"

#define MS_MICE_TLV_FRIENDLY_NAME_MAX_LENGTH_BYTES 520

//#include <sys/ioctl.h>
//#include <sys/socket.h>
//#include <arpa/inet.h>

/* ------------------------------------------------------------------------------------------------------------------
 * -- MIRACAST TLV
 * -- */

const char *ms_mice_tlvs_friendly_name_to_utf8(struct shl_dlist *tlvs)
{
    ms_mice_tlv *tlv = ms_mice_tlv_find(tlvs, MS_MICE_TLV_FRIENDLY_NAME);

    return tlv ? ms_mice_tlv_friendly_name_to_utf8(tlv) : NULL;
}

const char *ms_mice_tlv_friendly_name_to_utf8(ms_mice_tlv *tlv)
{
    gsize length = MIN(MS_MICE_TLV_FRIENDLY_NAME_MAX_LENGTH_BYTES, tlv->length);
    gsize out_length = 0;
    const gchar *out = string_convert_utf16le_to_utf8((const gchar *)tlv->friendly_name.value, length, &out_length);
    return out;
}

const char *ms_mice_tlvs_source_id_to_string(struct shl_dlist *tlvs)
{
    ms_mice_tlv *tlv = ms_mice_tlv_find(tlvs, MS_MICE_TLV_SOURCE_ID);

    return tlv ? ms_mice_tlv_source_id_to_string(tlv) : NULL;
}

const char *ms_mice_tlv_source_id_to_string(ms_mice_tlv *tlv)
{
    char *string_buffer = NULL;

    if (!tlv || tlv->type != MS_MICE_TLV_SOURCE_ID || tlv->length != MS_MICE_TLV_SOURCE_ID_LENGTH)
        return "00000000-0000-0000-0000-000000000000";

    string_buffer = g_new0(char, 37);

    sprintf(string_buffer, "%02X%02X%02X%02X-%02X%02X-%02X%02X-%02X%02X-%02X%02X%02X%02X%02X%02X",
            tlv->source_id.value[0], tlv->source_id.value[1], tlv->source_id.value[2], tlv->source_id.value[3],
            tlv->source_id.value[4], tlv->source_id.value[5],
            tlv->source_id.value[6], tlv->source_id.value[7],
            tlv->source_id.value[8], tlv->source_id.value[9],
            tlv->source_id.value[10], tlv->source_id.value[11], tlv->source_id.value[12], tlv->source_id.value[13], tlv->source_id.value[14], tlv->source_id.value[15]);

    return string_buffer;
}

const guint8 *ms_mice_tlv_source_id_from_string(const char *source_id)
{
    if (!source_id)
        return NULL;

    guint *data = (guint *)g_alloca(sizeof(guint) * MS_MICE_TLV_SOURCE_ID_LENGTH);

    sscanf(source_id, "%02X%02X%02X%02X-%02X%02X-%02X%02X-%02X%02X-%02X%02X%02X%02X%02X%02X",
           &data[0], &data[1], &data[2], &data[3],
           &data[4], &data[5],
           &data[6], &data[7],
           &data[8], &data[9],
           &data[10], &data[11], &data[12], &data[13], &data[14], &data[15]);

    guint8 *out = g_new0(guint8, MS_MICE_TLV_SOURCE_ID_LENGTH);

    for (int i = 0; i < MS_MICE_TLV_SOURCE_ID_LENGTH; i++) {
        out[i] = (guint8)data[i];
    }

    return out;
}

bool ms_mice_tlv_source_ids_are_equal(const guint8 *lhs, size_t lhs_length, const guint8 *rhs, size_t rhs_length)
{
    if (lhs == NULL || rhs == NULL) return false;

    if (lhs == rhs) return true;

    if (lhs_length != rhs_length) return false;

    for (int i = 0; i < lhs_length; i++) {
        if (lhs[i] != rhs[i]) return false;
    }

    return true;
}

const char *ms_mice_tlv_type_to_string(guint8 type)
{
    switch (type) {
        case MS_MICE_TLV_FRIENDLY_NAME:
            return "friendly-name";
        case MS_MICE_TLV_RTSP_PORT:
            return "rtsp-port";
        case MS_MICE_TLV_SOURCE_ID:
            return "source-id";
        case MS_MICE_TLV_SECURITY_TOKEN:
            return "security-token";
        case MS_MICE_TLV_SECURITY_OPTIONS:
            return "security-options";
        case MS_MICE_TLV_PIN_CHALLENGE:
            return "pin-challenge";
        case MS_MICE_TLV_PIN_RESPONSE_REASON:
            return "pin-response-reason";
        default:
            break;
    }

    return "tlv-type-unknown";
}

ms_mice_tlv *ms_mice_tlv_find(struct shl_dlist *tlvs, guint8 type)
{
    struct shl_dlist *iter;
    ms_mice_tlv *tlv = NULL;
    shl_dlist_for_each(iter, tlvs) {
        tlv = shl_dlist_entry(iter, ms_mice_tlv, list);
        if (tlv->type == type) {
            return tlv;
        }
    }
    return NULL;
}

static void ms_mice_tlv_pack_header(ms_mice_tlv *tlv, GDataOutputStream *stream, GError **error)
{
    stream_write_byte(stream, tlv->type);
    stream_write_uint16(stream, tlv->length);
}

static void ms_mice_tlv_unpack_friendly_name(ms_mice_tlv *tlv, GDataInputStream *stream, GError **error)
{
    if (tlv->length < 1) {
        CSIO_LOG(eLogLevel_error,"miracast.tlv.unpack.friendly-name { \"message\": \"Not expecting 0 length\" }");
        return;
    } else if (tlv->length > MS_MICE_TLV_FRIENDLY_NAME_MAX_LENGTH_BYTES) {
        CSIO_LOG(eLogLevel_warning,"miracast.tlv.unpack.friendly-name { \"message\": \"Friendly-name is longer than max size; received %u bytes, expected less than %u byes\" }", tlv->length, MS_MICE_TLV_FRIENDLY_NAME_MAX_LENGTH_BYTES);
        /* read anyway; need to advance the stream */
    }

    tlv->friendly_name.value = g_new0(gchar, tlv->length);
    stream_read_bytes(stream, (guint8*)tlv->friendly_name.value, tlv->length);
}

static void ms_mice_tlv_pack_friendly_name(ms_mice_tlv *tlv, GDataOutputStream *stream, GError **error)
{
    ms_mice_tlv_pack_header(tlv, stream, error);
    stream_write_bytes(stream, (guint8*)tlv->friendly_name.value, tlv->length);
}

static void ms_mice_tlv_unpack_rtsp_port(ms_mice_tlv *tlv, GDataInputStream *stream, GError **error)
{
    if (tlv->length < 1) {
        CSIO_LOG(eLogLevel_error,"miracast.tlv.unpack.rtsp-port { \"message\": \"Not expecting 0 length\" }");
        return;
    }
    tlv->rtsp_port.value = stream_read_uint16(stream);
}

static void ms_mice_tlv_pack_rtsp_port(ms_mice_tlv *tlv, GDataOutputStream *stream, GError **error)
{
    ms_mice_tlv_pack_header(tlv, stream, error);
    stream_write_uint16(stream, tlv->rtsp_port.value);
}

static void ms_mice_tlv_unpack_source_id(ms_mice_tlv *tlv, GDataInputStream *stream, GError **error)
{
    if (tlv->length < 1) {
        CSIO_LOG(eLogLevel_error,"miracast.tlv.unpack.source-id { \"message\": \"Not expecting 0 length\" }");
        return;
    } else if (tlv->length != MS_MICE_TLV_SOURCE_ID_LENGTH) {
        CSIO_LOG(eLogLevel_warning,"miracast.tlv.unpack.source-id { \"message\": \"Source ID is not the right size; received %u bytes, expected %u byes\" }", tlv->length, MS_MICE_TLV_SOURCE_ID_LENGTH);
    }

    guint8 *value = g_new0(guint8, tlv->length);
    stream_read_bytes(stream, value, tlv->length);
    tlv->source_id.value = value;
}

static void ms_mice_tlv_pack_source_id(ms_mice_tlv *tlv, GDataOutputStream *stream, GError **error)
{
    ms_mice_tlv_pack_header(tlv, stream, error);
    stream_write_bytes(stream, (guint8 *)tlv->source_id.value, tlv->length);
}

#define MS_MICE_TLV_SECURITY_OPTIONS_SINK_DISPLAYS_PIN_FLAG (1)
#define MS_MICE_TLV_SECURITY_OPTIONS_USE_DTLS_STREAM_ENCRYPTION_FLAG (2)

static void ms_mice_tlv_unpack_security_options(ms_mice_tlv *tlv, GDataInputStream *stream, GError **error)
{
    if (tlv->length < 1) {
        CSIO_LOG(eLogLevel_error,"miracast.tlv.unpack.security-options { \"message\": \"Not expecting 0 length\" }");
        return;
    }

    guint8 value = stream_read_byte(stream);
    tlv->security_options.sink_displays_pin = (value & MS_MICE_TLV_SECURITY_OPTIONS_SINK_DISPLAYS_PIN_FLAG) ? true : false;
    tlv->security_options.use_dtls_stream_encryption = (value & MS_MICE_TLV_SECURITY_OPTIONS_USE_DTLS_STREAM_ENCRYPTION_FLAG) ? true : false;

    if (tlv->length > 1)
        stream_advance(stream, (size_t)(tlv->length - 1));
}

static void ms_mice_tlv_pack_security_options(ms_mice_tlv *tlv, GDataOutputStream *stream, GError **error)
{
    guint8 value = 0;

    ms_mice_tlv_pack_header(tlv, stream, error);

    if (tlv->security_options.sink_displays_pin)
        value |= MS_MICE_TLV_SECURITY_OPTIONS_SINK_DISPLAYS_PIN_FLAG;

    if (tlv->security_options.use_dtls_stream_encryption)
        value |= MS_MICE_TLV_SECURITY_OPTIONS_USE_DTLS_STREAM_ENCRYPTION_FLAG;

    stream_write_byte(stream, value);
}

static void ms_mice_tlv_unpack_security_token(ms_mice_tlv *tlv, GDataInputStream *stream, GError **error)
{
    if (tlv->length < 1) {
        CSIO_LOG(eLogLevel_error,"miracast.tlv.unpack.security-token { \"message\": \"Not expecting 0 length\" }");
        return;
    }

    guint8 *value = g_new0(guint8, tlv->length);
    stream_read_bytes(stream, value, tlv->length);
    tlv->security_token.token = value;

    CSIO_LOG(eLogLevel_debug,"ms_mice_tlv_unpack_security_token value[0x%x], tlv->length[%d]\r\n",value[0],tlv->length);
}
static void ms_mice_tlv_unpack_pin_challenge(ms_mice_tlv *tlv, GDataInputStream *stream, GError **error)
{
    if (tlv->length < 1) {
        CSIO_LOG(eLogLevel_error,"miracast.tlv.unpack.pin.challenge { \"message\": \"Not expecting 0 length\" }");
        return;
    }

    guint8 *value = g_new0(guint8, tlv->length);
    stream_read_bytes(stream, value, tlv->length);
    tlv->pin_challenge.pin = value;

    CSIO_LOG(eLogLevel_debug,"ms_mice_tlv_unpack_pin_challenge value[0x%x], tlv->length[%d]\r\n",value,tlv->length);
}

static void ms_mice_tlv_pack_security_token(ms_mice_tlv *tlv, GDataOutputStream *stream, GError **error)
{
    ms_mice_tlv_pack_header(tlv, stream, error);

    stream_write_bytes(stream, tlv->security_token.token,tlv->length);

    CSIO_LOG(eLogLevel_debug,"ms_mice_tlv_pack_security_token token[0x%x], tlv->length[%d]\r\n",tlv->security_token.token[0],tlv->length);
}
static void ms_mice_tlv_pack_pin_challenge(ms_mice_tlv *tlv, GDataOutputStream *stream, GError **error)
{
    ms_mice_tlv_pack_header(tlv, stream, error);

    stream_write_bytes(stream, tlv->pin_challenge.pin,tlv->length);

    CSIO_LOG(eLogLevel_debug,"ms_mice_tlv_pack_pin_challenge pin[0x%x], tlv->length[%d]\r\n",tlv->pin_challenge.pin[0],tlv->length);
}
static void ms_mice_tlv_pack_pin_response_reason(ms_mice_tlv *tlv, GDataOutputStream *stream, GError **error)
{
    ms_mice_tlv_pack_header(tlv, stream, error);

    stream_write_byte(stream, tlv->pin_response.reason);

    CSIO_LOG(eLogLevel_debug,"ms_mice_tlv_pack_pin_response_reason reason[0x%x], tlv->length[%d]\r\n",tlv->pin_response.reason,tlv->length);
}
void ms_mice_tlv_pack(ms_mice_tlv *tlv, GDataOutputStream *stream, GError **error)
{
    if (!tlv) {
        if (error) {
            *error = g_error_new(G_IO_ERROR, G_IO_ERROR_INVALID_ARGUMENT, "No Miracast TLV");
        }
        return;
    }

    if (!stream) {
        if (error) {
            *error = g_error_new(G_IO_ERROR, G_IO_ERROR_INVALID_ARGUMENT, "No stream");
        }
        return;
    }

    switch (tlv->type) {
        case MS_MICE_TLV_FRIENDLY_NAME:
            ms_mice_tlv_pack_friendly_name(tlv, stream, error);
            break;
        case MS_MICE_TLV_RTSP_PORT:
            ms_mice_tlv_pack_rtsp_port(tlv, stream, error);
            break;
        case MS_MICE_TLV_SOURCE_ID:
            ms_mice_tlv_pack_source_id(tlv, stream, error);
            break;
        case MS_MICE_TLV_SECURITY_OPTIONS:
            ms_mice_tlv_pack_security_options(tlv, stream, error);
            break;
        case MS_MICE_TLV_SECURITY_TOKEN:
            ms_mice_tlv_pack_security_token(tlv, stream, error);
            break;
        case MS_MICE_TLV_PIN_CHALLENGE:
            ms_mice_tlv_pack_pin_challenge(tlv, stream, error);
            break;
        case MS_MICE_TLV_PIN_RESPONSE_REASON:
            ms_mice_tlv_pack_pin_response_reason(tlv, stream, error);
            break;
        default:
            if (error) {
                *error = g_error_new(G_IO_ERROR, G_IO_ERROR_INVALID_ARGUMENT, "Unknown Miracast TLV type= %u (0x%x)", tlv->type, tlv->type);
            }
            break;
    }
}

void ms_mice_tlv_unpack(ms_mice_tlv *tlv, GDataInputStream *stream, GError **error)
{
    g_autofree const char *value = NULL;

    gsize stream_available = 0;

    if (!tlv) {
        if (error) {
            *error = g_error_new(G_IO_ERROR, G_IO_ERROR_INVALID_ARGUMENT, "No Miracast TLV");
        }
        return;
    }

    if (!stream) {
        if (error) {
            *error = g_error_new(G_IO_ERROR, G_IO_ERROR_INVALID_ARGUMENT, "No stream");
        }
        return;
    }

    stream_available = stream_get_available(stream);

    if (stream_available < tlv->length) {
        CSIO_LOG(eLogLevel_error,"miracast.tlv.unpack.error { \"message\": \"Available stream size (%"G_GSIZE_FORMAT" bytes) not enough for TLV length (%"G_GUINT16_FORMAT" bytes)\" }", stream_available, tlv->length);
        if (error) {
            *error = g_error_new(G_IO_ERROR, G_IO_ERROR_INVALID_ARGUMENT, "Available stream size (%"G_GSIZE_FORMAT" bytes) not enough for TLV length (%"G_GUINT16_FORMAT" bytes)", stream_available, tlv->length);
            // TODO: bad condition, should probably close socket
        }
        return;
    }

    switch (tlv->type) {
        case MS_MICE_TLV_RTSP_PORT:
            ms_mice_tlv_unpack_rtsp_port(tlv, stream, error);
            CSIO_LOG(eLogLevel_debug,"miracast.tlv.unpack { \"type\": \"%s\" , \"length\": %u , \"rtsp-port\": %u }", ms_mice_tlv_type_to_string(tlv->type), tlv->length, tlv->rtsp_port.value);
            break;
        case MS_MICE_TLV_SOURCE_ID:
            ms_mice_tlv_unpack_source_id(tlv, stream, error);
            value = ms_mice_tlv_source_id_to_string(tlv);
            CSIO_LOG(eLogLevel_debug,"miracast.tlv.unpack { \"type\": \"%s\" , \"length\": %u , \"id\": \"%s\" }", ms_mice_tlv_type_to_string(tlv->type), tlv->length, value);
            break;
        case MS_MICE_TLV_FRIENDLY_NAME:
            ms_mice_tlv_unpack_friendly_name(tlv, stream, error);
            value = ms_mice_tlv_friendly_name_to_utf8(tlv);
            CSIO_LOG(eLogLevel_debug,"miracast.tlv.unpack { \"type\": \"%s\" , \"length\": %u , \"friendly-name\": \"%s\" }", ms_mice_tlv_type_to_string(tlv->type), tlv->length, value);
            break;
        case MS_MICE_TLV_SECURITY_OPTIONS:
            ms_mice_tlv_unpack_security_options(tlv, stream, error);
            CSIO_LOG(eLogLevel_debug,"miracast.tlv.unpack { \"type\": \"%s\" , \"length\": %u , \"sink-display-pin\": \"%s\" , \"use-dtls-encryption\": \"%s\" }", ms_mice_tlv_type_to_string(tlv->type), tlv->length,
                    BOOL_TO_STRING(tlv->security_options.sink_displays_pin), BOOL_TO_STRING(tlv->security_options.use_dtls_stream_encryption));
            break;
        case MS_MICE_TLV_SECURITY_TOKEN:
            ms_mice_tlv_unpack_security_token(tlv, stream, error);
            CSIO_LOG(eLogLevel_debug,"miracast.tlv.unpack { \"type\": \"%s\" , \"length\": %u , \"friendly-name\": \"%s\" }", ms_mice_tlv_type_to_string(tlv->type), tlv->length, value);
            break;
        case MS_MICE_TLV_PIN_CHALLENGE:
            ms_mice_tlv_unpack_pin_challenge(tlv, stream, error);
            CSIO_LOG(eLogLevel_debug,"miracast.tlv.unpack { \"type\": \"%s\" , \"length\": %u }", ms_mice_tlv_type_to_string(tlv->type), tlv->length);
            break;
        case MS_MICE_TLV_PIN_RESPONSE_REASON:
        default:
            stream_advance(stream, tlv->length);
            CSIO_LOG(eLogLevel_error,"miracast.tlv.unpack.error { \"message\": \"Unknown Miracast TLV type= %u (0x%x)\" }", tlv->type, tlv->type);
            if (error) {
                *error = g_error_new(G_IO_ERROR, G_IO_ERROR_INVALID_ARGUMENT, "Unknown Miracast TLV type= %u (0x%x)", tlv->type, tlv->type);
            }
            break;
    }
}

static void ms_mice_tlv_detach(ms_mice_tlv *tlv)
{
    shl_dlist_unlink(&tlv->list);
}

/* ------------------ */
/* -- CONSTRUCTION -- */

ms_mice_tlv *ms_mice_tlv_friendly_name_new(const gchar *utf8, gsize utf8_bytes, GError **error)
{
    ms_mice_tlv *tlv = NULL;
    gsize utf16_size = 0;

    const gchar *utf16 = string_convert_utf8_to_utf16le(utf8, utf8_bytes, &utf16_size);
    if (!utf16)
        return NULL;

    tlv = ms_mice_tlv_new(MS_MICE_TLV_FRIENDLY_NAME, (guint16)utf16_size, error);
    if (!tlv) {
        g_free((gpointer)utf16);
        return NULL;
    }

    tlv->friendly_name.value = utf16;

    return tlv;
}

ms_mice_tlv *ms_mice_tlv_source_id_new(const gchar *source_id, GError **error)
{
    ms_mice_tlv *tlv = NULL;

    const guint8 *value = ms_mice_tlv_source_id_from_string(source_id);

    tlv = ms_mice_tlv_new(MS_MICE_TLV_SOURCE_ID, MS_MICE_TLV_SOURCE_ID_LENGTH, error);
    if (!tlv) {
        g_free((gpointer)value);
        return NULL;
    }

    tlv->source_id.value = value;

    return tlv;
}

ms_mice_tlv *ms_mice_tlv_rtsp_port_new(guint16 port, GError **error)
{
    ms_mice_tlv *tlv = NULL;

    tlv = ms_mice_tlv_new(MS_MICE_TLV_RTSP_PORT, sizeof(port), error);
    if (!tlv) {
        return NULL;
    }

    tlv->rtsp_port.value = port;

    return tlv;
}

ms_mice_tlv *ms_mice_tlv_security_options_new(bool sink_displays_pin, bool use_dtls_encryption, GError **error)
{
    ms_mice_tlv *tlv = NULL;

    tlv = ms_mice_tlv_new(MS_MICE_TLV_SECURITY_OPTIONS, sizeof(guint8), error);
    if (!tlv) {
        return NULL;
    }

    tlv->security_options.sink_displays_pin = sink_displays_pin;
    tlv->security_options.use_dtls_stream_encryption = use_dtls_encryption;

    return tlv;
}

ms_mice_tlv *ms_mice_tlv_security_token_new(const guint8 *t, guint16 length, GError **error)
{
    ms_mice_tlv *tlv = NULL;

    guint8 *token = g_new0(guint8, length);

    tlv = ms_mice_tlv_new(MS_MICE_TLV_SECURITY_TOKEN, length, error);
    if (!tlv) {
        g_free((gpointer)token);
        return NULL;
    }

    memcpy(token,t,length);
    tlv->security_token.token = token;

    return tlv;
}

ms_mice_tlv *ms_mice_tlv_pin_response_reason_new(guint8 t, GError **error)
{
    ms_mice_tlv *tlv = NULL;

    tlv = ms_mice_tlv_new(MS_MICE_TLV_PIN_RESPONSE_REASON,sizeof(t),error);
    if (!tlv) {
        return NULL;
    }

    tlv->pin_response.reason = t;

    return tlv;
}

ms_mice_tlv *ms_mice_tlv_pin_challenge_new(const guint8 *t, guint16 length, GError **error)
{
    ms_mice_tlv *tlv = NULL;

    guint8 *challenge = g_new0(guint8, length);

    tlv = ms_mice_tlv_new(MS_MICE_TLV_PIN_CHALLENGE, length, error);
    if (!tlv) {
        g_free((gpointer)challenge);
        return NULL;
    }

    memcpy(challenge,t,length);
    tlv->pin_challenge.pin = challenge;

    return tlv;
}
ms_mice_tlv *ms_mice_tlv_new(guint8 type, guint16 length, GError **error)
{
    ms_mice_tlv *t = g_new0(ms_mice_tlv, 1);

    if (!t) {
        if (error) {
            *error = g_error_new(G_IO_ERROR, G_IO_ERROR_FAILED, "No more memory error");
            // TODO: bad condition, should probably close application
        }
        return NULL;
    }

    t->type = type;
    t->length = length;

    return t;
}

void ms_mice_tlv_free(ms_mice_tlv *tlv)
{
    if (!tlv)
        return;

    ms_mice_tlv_detach(tlv);

    switch (tlv->type) {
        case MS_MICE_TLV_SOURCE_ID:
            g_free((gpointer)tlv->source_id.value);
            break;
        case MS_MICE_TLV_FRIENDLY_NAME:
            g_free((gpointer)tlv->friendly_name.value);
            break;
        case MS_MICE_TLV_SECURITY_TOKEN:
            g_free((gpointer)tlv->security_token.token);
            break;
        case MS_MICE_TLV_SECURITY_OPTIONS:
            break;
        case MS_MICE_TLV_PIN_CHALLENGE:
            g_free((gpointer)tlv->pin_challenge.pin);
            break;
        case MS_MICE_TLV_PIN_RESPONSE_REASON:
            break;
        case MS_MICE_TLV_RTSP_PORT:
        default:
            break;
    }

    g_free(tlv);
}
