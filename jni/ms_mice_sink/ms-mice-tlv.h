#ifndef CTL_EXTENSION_MS_MICE_TLV_H
#define CTL_EXTENSION_MS_MICE_TLV_H

#include <glib.h>
#include <stdbool.h>
#include "shared/shl_dlist.h"
#include <stdio.h>
#include <gio/gio.h>

/* ------------------------------------------------------------------------------------------------------------------
 * -- MS-MICE TVL CONSTANTS
 * -- */

#define MS_MICE_TLV_MAX_LENGTH_BYTES (0xFFFF)

enum MS_MICE_TLV_TYPES {
    MS_MICE_TLV_FRIENDLY_NAME = 0x00, /* 2.2.7.1 Specifies the friendly name of the Miracast Source. */
    MS_MICE_TLV_RTSP_PORT = 0x02, /* 2.2.7.2 Specifies the port on which the Source is listening for RTSP connections. */
    MS_MICE_TLV_SOURCE_ID = 0x03, /* 2.2.7.3 Specifies an identifier for the Source, which is used for all messages sent during a Miracast session. */
    MS_MICE_TLV_SECURITY_TOKEN = 0x04, /* 2.2.7.4 Contains a DTLS handshake message. */
    MS_MICE_TLV_SECURITY_OPTIONS = 0x05, /* 2.2.7.5 Specifies whether stream encryption and/or PIN entry will be used for the session. */
    MS_MICE_TLV_PIN_CHALLENGE = 0x06, /* 2.2.7.6 Contains a salted hash of the PIN when PIN entry is used to establish the connection. */
    MS_MICE_TLV_PIN_RESPONSE_REASON = 0x07, /* 2.2.7.7 Specifies whether the PIN Response indicates a successful connection. */
};

enum MS_MICE_TLV_PIN_RESPONSE_REASON {
    MS_MICE_TLV_PIN_RESPONSE_REASON_ACCPTED = 0x00, /* PIN Accepted The PIN in the PIN Challenge message matched the one displayed by the Sink. */
    MS_MICE_TLV_PIN_RESPONSE_REASON_WRONG_PIN = 0x01, /* The PIN in the PIN Challenge message did not match the one displayed by the Sink. */
    MS_MICE_TLV_PIN_RESPONSE_REASON_INVALID_MESSAGE = 0x02, /* A PIN Challenge message was not expected. */
};

/* ------------------------------------------------------------------------------------------------------------------
 * -- MS-MICE TVL DEFINITIONS
 * -- */

typedef struct {
    /* on-the-wire: friendly name string of the Source, encoded in UTF-16; maximum length is 520 bytes */
    const gchar *value;
} ms_mice_tlv_friendly_name;

typedef struct {
    guint16 value;
} ms_mice_tlv_rtsp_port;

#define MS_MICE_TLV_SOURCE_ID_LENGTH 16

typedef struct {
    const guint8 *value; /* 16 bytes; identifier is used in all messages sent during a session. */
} ms_mice_tlv_source_id;

typedef struct {
    guint8 *token; /* The Security Token TLV contains DTLS handshake messages as specified in [RFC6347] */
} ms_mice_tlv_security_token;

typedef struct {
    bool use_dtls_stream_encryption; /* bit 7 (1 bit): 0 = do not use, 1 = use */
    bool sink_displays_pin; /* bit 6 (1 bit): 0 = PIN is not displayed by Sink, 1 = Sink displays random PIN and Source provides this PIN after DTLS handshake. Bit A MUST be set if bit B is set. */
} ms_mice_tlv_security_options;

typedef struct {
    guint8 *pin; /* The PIN Challenge TLV contains a salted and hashed value for the session PIN. The hashing is specified in section 3.1.5.6.1 */
} ms_mice_tlv_pin_challenge;

typedef struct {
    guint8 reason;
} ms_mice_tlv_pin_response_reason;

#define MS_MICE_TLV_HEADER_SIZE (sizeof(guint8) + sizeof(guint16))

typedef struct {
    struct shl_dlist list;
    guint8 type;
    guint16 length;
    union {
        ms_mice_tlv_friendly_name friendly_name;
        ms_mice_tlv_rtsp_port rtsp_port;
        ms_mice_tlv_source_id source_id;
        ms_mice_tlv_security_token security_token;
        ms_mice_tlv_security_options security_options;
        ms_mice_tlv_pin_challenge pin_challenge;
        ms_mice_tlv_pin_response_reason pin_response;
    };
} ms_mice_tlv;

/* ------------------------------------------------------------------------------------------------------------------
 * -- MS-MICE TVL CONSTRUCTION
 * -- */

ms_mice_tlv *ms_mice_tlv_new(guint8 type, guint16 length, GError **error);
ms_mice_tlv *ms_mice_tlv_friendly_name_new(const gchar *value_utf8, gsize value_bytes, GError **error);
ms_mice_tlv *ms_mice_tlv_source_id_new(const gchar *value, GError **error);
ms_mice_tlv *ms_mice_tlv_rtsp_port_new(guint16 port, GError **error);
ms_mice_tlv *ms_mice_tlv_security_options_new(bool sink_displays_pin, bool use_dtls_encryption, GError **error);
ms_mice_tlv *ms_mice_tlv_security_token_new(const guint8 *t, guint16 length, GError **error);
ms_mice_tlv *ms_mice_tlv_pin_response_reason_new(guint8 t, GError **error);
ms_mice_tlv *ms_mice_tlv_pin_challenge_new(const guint8 *t, guint16 length, GError **error);

void ms_mice_tlv_free(ms_mice_tlv *tlv);

/* ------------------------------------------------------------------------------------------------------------------
 * -- MS-MICE TVL SERIALIZATION
 * -- */

void ms_mice_tlv_pack(ms_mice_tlv *tlv, GDataOutputStream *stream, GError **error);
void ms_mice_tlv_unpack(ms_mice_tlv *tlv, GDataInputStream *stream, GError **error);

/* ------------------------------------------------------------------------------------------------------------------
 * -- MS-MICE TVL UTILITIES
 * -- */

ms_mice_tlv *ms_mice_tlv_find(struct shl_dlist *tlvs, guint8 type);

const char *ms_mice_tlv_type_to_string(guint8 type);

const char *ms_mice_tlvs_source_id_to_string(struct shl_dlist *tlvs);
const char *ms_mice_tlv_source_id_to_string(ms_mice_tlv *tlv);

bool ms_mice_tlv_source_ids_are_equal(const guint8 *lhs, size_t lhs_length, const guint8 *rhs, size_t rhs_length);

const char *ms_mice_tlvs_friendly_name_to_utf8(struct shl_dlist *tlvs);
const char *ms_mice_tlv_friendly_name_to_utf8(ms_mice_tlv *tlv);

#endif /* CTL_EXTENSION_MS_MICE_TLV_H */
