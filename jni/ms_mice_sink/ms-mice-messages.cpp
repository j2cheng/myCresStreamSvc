#include "ms-mice-messages.h"
#include "ms-mice-tlv.h"
#include "ms-mice-utilities.h"
#include "ms-mice.h"
#include <gio/gio.h>
#include "../shared-ssl/shared-ssl.h"
const char *ms_mice_command_to_string(guint8 type)
{
    switch (type) {
        case MS_MICE_MESSAGE_SOURCE_READY:
            return "source-ready";
        case MS_MICE_MESSAGE_STOP_PROJECTION:
            return "stop-projection";
        case MS_MICE_MESSAGE_SECURITY_HANDSHAKE:
            return "security-handshake";
        case MS_MICE_MESSAGE_SESSION_REQUEST:
            return "session-request";
        case MS_MICE_MESSAGE_PIN_CHALLENGE:
            return "pin-challenge";
        case MS_MICE_MESSAGE_PIN_RESPONSE:
            return "pin-response";
        default:
            break;
    }

    return "ms-mice-command-unknown";
}

void ms_mice_message_unpack(ms_mice_message *msg, GDataInputStream *stream, GError **error)
{
    gsize stream_available = 0, payload_size = 0;

    if (!msg) {
        if (error) {
            *error = g_error_new(G_IO_ERROR, G_IO_ERROR_INVALID_ARGUMENT, "No msg");
        }
        return;
    } else if (msg->size <= (MS_MICE_HEADER_SIZE + MS_MICE_TLV_HEADER_SIZE)) {
        if (error) {
            *error = g_error_new(G_IO_ERROR, G_IO_ERROR_INVALID_ARGUMENT, "No Miracast TLVs, expecting message greater than %"G_GSIZE_FORMAT" bytes", (gsize)(MS_MICE_HEADER_SIZE + MS_MICE_TLV_HEADER_SIZE));
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
    payload_size = msg->size - MS_MICE_HEADER_SIZE;

    if (stream_available < payload_size) {
        if (error) {
            *error = g_error_new(G_IO_ERROR, G_IO_ERROR_INVALID_ARGUMENT, "Available stream size (%"G_GSIZE_FORMAT" bytes) not enough for a payload (%"G_GSIZE_FORMAT" bytes)", stream_available, payload_size);
            // TODO: bad condition, should probably close socket
        }
        return;
    }

    /* -- unpack TLVs -- */

    while (payload_size > 0) {
        g_autoptr(GError) internal_error = NULL;
        guint8 type = stream_read_byte(stream);
        guint16 length = stream_read_uint16(stream);
        ms_mice_tlv *tlv = ms_mice_tlv_new(type, length, &internal_error);

        if (!tlv) {
            if (error) {
                *error = g_error_new(G_IO_ERROR, G_IO_ERROR_NO_SPACE, "No more memory error");
                // TODO: bad condition, should probably close application, shouldn't be able to allocate error
            }
            return;
        }

        payload_size -= MS_MICE_TLV_HEADER_SIZE;

        if (payload_size < tlv->length) {
            if (error) {
                *error = g_error_new(G_IO_ERROR, G_IO_ERROR_INVALID_ARGUMENT, "Payload size (%"G_GSIZE_FORMAT" bytes) not enough for TVL length (%"G_GUINT16_FORMAT" bytes)", payload_size, tlv->length);
                // TODO: bad condition, should probably close socket
            }
            return;
        }

        ms_mice_tlv_unpack(tlv, stream, &internal_error);

        if (internal_error) {
            CSIO_LOG(eLogLevel_error,"Unable to unpack TLV  type= %u  length= %u", tlv->type, tlv->length);
        } else {
            ms_mice_message_tlv_attach(msg, tlv);
        }

        payload_size -= tlv->length;
    }
}

void ms_mice_message_pack(ms_mice_message *msg, GDataOutputStream *stream, GError **error)
{
    struct shl_dlist *iter = NULL;
    ms_mice_tlv *tlv = NULL;
    size_t msg_size = MS_MICE_HEADER_SIZE;

    if (!msg) {
        if (error) {
            *error = g_error_new(G_IO_ERROR, G_IO_ERROR_INVALID_ARGUMENT, "No msg");
        }
        return;
    } else if (shl_dlist_empty(&msg->tlvs)) {
        if (error) {
            *error = g_error_new(G_IO_ERROR, G_IO_ERROR_INVALID_ARGUMENT, "Empty TLV");
        }
        return;
    }

    if (!stream) {
        if (error) {
            *error = g_error_new(G_IO_ERROR, G_IO_ERROR_INVALID_ARGUMENT, "No stream");
        }
        return;
    }

    /* -- compute the message length to insert into output stream -- */

    shl_dlist_for_each(iter, &msg->tlvs) {
        tlv = shl_dlist_entry(iter, ms_mice_tlv, list);
        msg_size += (MS_MICE_TLV_HEADER_SIZE + tlv->length);
    }

    msg->size = (guint16)msg_size;

    stream_write_uint16(stream, msg->size);
    stream_write_byte(stream, msg->version);
    stream_write_byte(stream, msg->command);

    /* -- pack the TLVs into the output stream -- */

    shl_dlist_for_each(iter, &msg->tlvs) {
        tlv = shl_dlist_entry(iter, ms_mice_tlv, list);
        ms_mice_tlv_pack(tlv, stream, error);
    }
}

void ms_mice_message_tlv_attach(ms_mice_message *msg, ms_mice_tlv *tlv)
{
    shl_dlist_link_tail(&msg->tlvs, &tlv->list);
}

void ms_mice_message_tlv_friendly_name_attach(ms_mice_message *msg, const gchar *utf8, gsize utf8_bytes, GError **error)
{
    ms_mice_tlv *tlv = ms_mice_tlv_friendly_name_new(utf8, utf8_bytes, error);
    ms_mice_message_tlv_attach(msg, tlv);
}

void ms_mice_message_tlv_source_id_attach(ms_mice_message *msg, const gchar *source_id, GError **error)
{
    ms_mice_tlv *tlv = ms_mice_tlv_source_id_new(source_id, error);
    ms_mice_message_tlv_attach(msg, tlv);
}

void ms_mice_message_tlv_rtsp_port_attach(ms_mice_message *msg, guint16 port, GError **error)
{
    ms_mice_tlv *tlv = ms_mice_tlv_rtsp_port_new(port, error);
    ms_mice_message_tlv_attach(msg, tlv);
}

void ms_mice_message_tlv_security_options_attach(ms_mice_message *msg, bool sink_displays_pin, bool use_dtls_encryption, GError **error)
{
    ms_mice_tlv *tlv = ms_mice_tlv_security_options_new(sink_displays_pin, use_dtls_encryption, error);
    ms_mice_message_tlv_attach(msg, tlv);
}

void ms_mice_message_tlv_security_token_attach(ms_mice_message *msg, guint8 *token, guint16 length, GError **error)
{
    ms_mice_tlv *tlv = ms_mice_tlv_security_token_new(token, length,error);
    ms_mice_message_tlv_attach(msg, tlv);
    
}
void ms_mice_message_tlv_pin_response_reason_attach(ms_mice_message *msg, guint8 reason,GError **error)
{
    ms_mice_tlv *tlv = ms_mice_tlv_pin_response_reason_new(reason,error);
    ms_mice_message_tlv_attach(msg, tlv);
}

void ms_mice_message_tlv_pin_challenge_attach(ms_mice_message *msg, guint8 * challenge, guint16 length,GError **error)
{
    ms_mice_tlv *tlv = ms_mice_tlv_pin_challenge_new(challenge,length,error);
    ms_mice_message_tlv_attach(msg, tlv);
}
/* ------------------ */
/* -- CONSTRUCTION -- */

ms_mice_message_entry *ms_mice_message_entry_new(ms_mice_message *msg, GError **error)
{
    ms_mice_message_entry *entry = g_new0(ms_mice_message_entry, 1);

    shl_dlist_init(&entry->list);
    entry->is_sending = false;
    entry->raw = NULL;
    entry->raw_size = 0;
    entry->sent = 0;
    entry->msg = msg;

    return entry;
}

static void ms_mice_message_entry_free_raw(ms_mice_message_entry *entry)
{
    if (!entry)
        return;

    g_free(entry->raw);
    entry->raw = NULL;
    entry->raw_size = 0;
}

void ms_mice_message_entry_pack(ms_mice_message_entry *entry, GError **error)
{
    guint8 *raw = NULL;
    size_t raw_size = 0;

    GOutputStream *base_out_stream = g_memory_output_stream_new_resizable();

    GDataOutputStream *out_stream = g_data_output_stream_new(base_out_stream);
    g_data_output_stream_set_byte_order(out_stream, G_DATA_STREAM_BYTE_ORDER_BIG_ENDIAN);

    ms_mice_message_pack(entry->msg, out_stream, error);
    g_output_stream_flush(G_OUTPUT_STREAM(out_stream), NULL, NULL);

    raw_size = g_memory_output_stream_get_data_size(G_MEMORY_OUTPUT_STREAM(base_out_stream));
    raw = (guint8*)g_memory_output_stream_get_data(G_MEMORY_OUTPUT_STREAM(base_out_stream));

    ms_mice_message_entry_copy_raw(entry, raw, raw_size);

    g_output_stream_close(base_out_stream, NULL, NULL);
    g_object_unref(base_out_stream);

    g_output_stream_close(G_OUTPUT_STREAM(out_stream), NULL, NULL);
    g_object_unref(out_stream);
}

void ms_mice_message_entry_encrypt_pack(void *session, ms_mice_message_entry *entry, GError **error)
{
    guint8 *raw = NULL;
    size_t raw_size = 0;
    unsigned char* encryptedOutBuf = NULL;
    int cryptBufSize = MS_MICE_MIN_DTLS_CRYPTBUF_SIZE;

    GOutputStream *base_out_stream = g_memory_output_stream_new_resizable();

    GDataOutputStream *out_stream = g_data_output_stream_new(base_out_stream);
    g_data_output_stream_set_byte_order(out_stream, G_DATA_STREAM_BYTE_ORDER_BIG_ENDIAN);

    ms_mice_message_pack(entry->msg, out_stream, error);
    g_output_stream_flush(G_OUTPUT_STREAM(out_stream), NULL, NULL);

    raw_size = g_memory_output_stream_get_data_size(G_MEMORY_OUTPUT_STREAM(base_out_stream));
    raw = (guint8*)g_memory_output_stream_get_data(G_MEMORY_OUTPUT_STREAM(base_out_stream));

    //Note: data inside 'raw' contains message header
    //      skip 4 bytes to encrypt data in 'raw'

    //need to find out buff size here
    if( (raw_size * 2 ) > MS_MICE_MIN_DTLS_CRYPTBUF_SIZE )
        cryptBufSize = raw_size * 2;

    //create new buffer here
    encryptedOutBuf = g_new(guint8, cryptBufSize);
    CSIO_LOG(eLogLevel_debug,"ms_mice_message_entry_encrypt_pack created buff[0x%x] of size[%d]\r\n",
             encryptedOutBuf,cryptBufSize);

    if(encryptedOutBuf)
    {
        encryptedOutBuf[0] = 0;
        encryptedOutBuf[1] = 0;
        encryptedOutBuf[2] = raw[2];//copy version
        encryptedOutBuf[3] = raw[3];//copy message command

        guint64 sessionID = ms_mice_sink_session_get_id((ms_mice_sink_session*)session);
        int encryptedsize = sssl_encryptDTLS(sessionID,
                                             &raw[MS_MICE_HEADER_SIZE],
                                             (raw_size - MS_MICE_HEADER_SIZE),
                                             &encryptedOutBuf[MS_MICE_HEADER_SIZE],
                                             cryptBufSize);
        CSIO_LOG(eLogLevel_debug,"sssl_encryptDTLS return : %d\r\n",encryptedsize);

        if(encryptedsize > 0)
        {
            //adding size of message header
            encryptedsize += MS_MICE_HEADER_SIZE;

            //update new size of the message
            encryptedOutBuf[0] = (encryptedsize & 0xff00) >> 8;
            encryptedOutBuf[1] = encryptedsize & 0xff;

            ms_mice_message_entry_copy_raw(entry, encryptedOutBuf, encryptedsize);
            CSIO_LOG(eLogLevel_debug,"ms_mice_message_entry_copy_raw raw_size[%d] encryptedsize[%d]\r\n",
                     raw_size,encryptedsize);
        }//else
    }

    g_output_stream_close(base_out_stream, NULL, NULL);
    g_object_unref(base_out_stream);

    g_output_stream_close(G_OUTPUT_STREAM(out_stream), NULL, NULL);
    g_object_unref(out_stream);

    g_free(encryptedOutBuf);
}
void ms_mice_message_entry_copy_raw(ms_mice_message_entry *entry, guint8 *data, size_t data_size)
{
    if (!entry)
        return;

    ms_mice_message_entry_free_raw(entry);

    entry->raw = g_new(guint8, data_size);

    memcpy(entry->raw, data, data_size);

    entry->raw_size = data_size;
}

void ms_mice_message_entry_free(ms_mice_message_entry *entry)
{
    if (!entry)
        return;

    shl_dlist_unlink(&entry->list);
    entry->is_sending = false;
    entry->sent = 0;
    ms_mice_message_entry_free_raw(entry);
    ms_mice_message_free(entry->msg);
}

ms_mice_message *ms_mice_message_new(guint16 size, guint8 version, guint8 command, GError **error)
{
    ms_mice_message *m = g_new0(ms_mice_message, 1);

    if (!m) {
        if (error) {
            *error = g_error_new(G_IO_ERROR, G_IO_ERROR_NO_SPACE, "No more memory error");
        }
        return NULL;
    }

    shl_dlist_init(&m->tlvs);

    m->size = size;
    m->version = version;
    m->command = command;

    CSIO_LOG(eLogLevel_debug,"ms.mice.message.new { \"size\": %u , \"version\": %u  \"command\": \"%s (0x%02x)\"", m->size, m->version, ms_mice_command_to_string(m->command), m->command);

    return m;
}

void ms_mice_message_free(ms_mice_message *msg)
{
    ms_mice_tlv *tlv;

    if (!msg)
        return;

    while (!shl_dlist_empty(&msg->tlvs)) {
        tlv = shl_dlist_first_entry(&msg->tlvs, ms_mice_tlv, list);
        ms_mice_tlv_free(tlv);
    }

    g_free(msg);
}
