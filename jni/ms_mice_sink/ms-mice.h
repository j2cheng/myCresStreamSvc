#ifndef CTL_EXTENSION_MS_MICE_H
#define CTL_EXTENSION_MS_MICE_H

typedef struct _ms_mice_sink_session ms_mice_sink_session;
typedef struct _ms_mice_sink_service ms_mice_sink_service;
#include "csioCommBase.h"
//#include "extensions-common.h"
#include "ms-mice-utilities.h"
//#include "ms-mice-errors.h"
#include "ms-mice-tlv.h"
#include "ms-mice-messages.h"
#include "ms-mice-sink-session.h"
#include "ms-mice-sink-service.h"
//#include "ms-mice-test.h"
//#include "ms-mice-dtls.h"

//copy from common.h
#define DEFAULT_MIRACAST_RTSP_PORT 7236

//copy from util.h
static inline bool strings_are_equal(const char *const lhs, const char *const rhs)
{
    if (lhs == rhs) return true;
    else if (lhs == NULL || rhs == NULL) return false;
    return strcmp(lhs, rhs) == 0;
}

#define RAISE_NOT_IMPLEMENTED() CSIO_LOG(eLogLevel_error,"NOT IMPLEMENTED  %s  at  %s : %d",  __FUNCTION__, __FILE__, __LINE__)

#endif /* CTL_EXTENSION_MS_MICE_H */
