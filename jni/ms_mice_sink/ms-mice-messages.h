#ifndef CTL_EXTENSION_MS_MICE_MESSAGES_H
#define CTL_EXTENSION_MS_MICE_MESSAGES_H

#include <glib.h>
#include <gio/gio.h>
#include <stdbool.h>
#include "shared/shl_dlist.h"
#include "ms-mice-tlv.h"

/* ------------------------------------------------------------------------------------------------------------------
 * -- MS-MICE MESSAGES CONSTANTS
 * -- */

#define MS_MICE_MESSAGE_VERSION_1 1
#define MS_MICE_MESSAGE_MAX_LENGTH_BYTES (0xFFFF)

typedef enum {
    MS_MICE_MESSAGE_SOURCE_READY = 0x01, /* Indicates the Miracast Source is ready to accept a connection on the RTSP port. */
    MS_MICE_MESSAGE_STOP_PROJECTION = 0x02, /* Indicates the end of the projection. */
    MS_MICE_MESSAGE_SECURITY_HANDSHAKE = 0x03, /* Used to exchange DTLS handshake messages to initiate a connection with encryption of the multimedia stream. */
    MS_MICE_MESSAGE_SESSION_REQUEST = 0x04, /* Indicates the Miracast Source intends to connect to the Sink using the specified options. */
    MS_MICE_MESSAGE_PIN_CHALLENGE = 0x05, /* Sent by the Miracast Source to initiate the session using the PIN displayed by the Miracast Sink. */
    MS_MICE_MESSAGE_PIN_RESPONSE = 0x06, /* Sent by the Miracast Sink in response to a PIN_CHALLENGE received from the Miracast Source. */
} MS_MICE_MESSAGE_COMMANDS;

/* ------------------------------------------------------------------------------------------------------------------
 * -- MS-MICE MESSAGES DEFINITIONS
 * -- */

#define MS_MICE_HEADER_SIZE (sizeof(guint16) + sizeof(guint8) + sizeof(guint8))

typedef struct {
    guint16 size;
    guint8 version;
    guint8 command;
    struct shl_dlist tlvs;
} ms_mice_message;

typedef struct {
    struct shl_dlist list;
    bool is_sending;
    guint8 *raw;
    gsize raw_size;
    gsize sent;
    ms_mice_message *msg;
} ms_mice_message_entry;

/* ------------------------------------------------------------------------------------------------------------------
 * -- MS-MICE MESSAGES CONSTRUCTION
 * -- */

ms_mice_message_entry *ms_mice_message_entry_new(ms_mice_message *msg, GError **error);
void ms_mice_message_entry_free(ms_mice_message_entry *entry);

void ms_mice_message_entry_pack(ms_mice_message_entry *entry, GError **error);
void ms_mice_message_entry_copy_raw(ms_mice_message_entry *entry, guint8 *data, size_t data_size);

ms_mice_message *ms_mice_message_new(guint16 size, guint8 version, guint8 command, GError **error);
void ms_mice_message_free(ms_mice_message *msg);

void ms_mice_message_tlv_attach(ms_mice_message *msg, ms_mice_tlv *tlv);
void ms_mice_message_tlv_friendly_name_attach(ms_mice_message *msg, const gchar *utf8, gsize utf8_bytes, GError **error);
void ms_mice_message_tlv_source_id_attach(ms_mice_message *msg, const gchar *source_id, GError **error);
void ms_mice_message_tlv_rtsp_port_attach(ms_mice_message *msg, guint16 port, GError **error);
void ms_mice_message_tlv_security_options_attach(ms_mice_message *msg, bool sink_displays_pin, bool use_dtls_encryption, GError **error);
void ms_mice_message_tlv_security_token_attach(ms_mice_message *msg, guint8 *token, guint16 length, GError **error);

void ms_mice_message_unpack(ms_mice_message *msg, GDataInputStream *stream, GError **error);
void ms_mice_message_pack(ms_mice_message *msg, GDataOutputStream *stream, GError **error);

/* ------------------------------------------------------------------------------------------------------------------
 * -- MS-MICE MESSAGES UTILITIES
 * -- */

const char *ms_mice_command_to_string(guint8 type);

#endif /* CTL_EXTENSION_MS_MICE_MESSAGES_H */
