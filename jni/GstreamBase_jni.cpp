#include <cassert>
#include <cstdlib>

#include <jni.h>

#include <gst/gst.h>
#include <gst/gstinfo.h>

#include "csioCommonShare.h"

namespace {
void listGStreamerPlugins(bool rescan = false)
{
    GstRegistry *registry = gst_registry_get();

    assert(registry);

    if(rescan)
    {
        if(!gst_update_registry())
        {
            CSIO_LOG(eLogLevel_error, "registry updated failed");
        }
    }

    GList *plugins = gst_registry_get_plugin_list(registry);
    int count = 0;

    assert(plugins);

    for (GList *i = plugins; i; i = g_list_next(i))
    {
        GstPlugin *curr = static_cast<GstPlugin *>(i->data);

        CSIO_LOG(
            eLogLevel_info,
            "%s blacklisted %d",
            gst_plugin_get_name(curr),
            GST_OBJECT_FLAG_IS_SET(curr, GST_PLUGIN_FLAG_BLACKLISTED));
        count++;
    }

    gst_plugin_list_free(plugins);
}
} // namespace

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT void JNICALL
Java_com_crestron_txrxservice_GstreamBase_postGStreamerInit(
    JNIEnv *env, jobject thiz)
{
    CSIO_LOG(eLogLevel_info, "GST_DEBUG %s", getenv("GST_DEBUG"));
    CSIO_LOG(eLogLevel_info, "is_initialized %d", gst_is_initialized());
    CSIO_LOG(eLogLevel_info, "debug_is_active %d", gst_debug_is_active());
    CSIO_LOG(eLogLevel_info, "debug_threshold %d", gst_debug_get_default_threshold());
    CSIO_LOG(eLogLevel_debug, "version  %s", gst_version_string());
    CSIO_LOG(eLogLevel_info, "GST_PLUGIN_PATH %s", getenv("GST_PLUGIN_PATH"));
    CSIO_LOG(eLogLevel_info, "GST_REGISTRY %s", getenv("GST_REGISTRY"));
    GST_INFO("validate GST_INFO env %p thiz %p", env, thiz);
    GST_DEBUG("validate GST_DEBUG env %p thiz %p", env, thiz);
    GST_LOG("validate GST_LOG env %p thiz %p", env, thiz);
    GST_TRACE("validate GST_TRACE env %p thiz %p", env, thiz);
    listGStreamerPlugins();
    listGStreamerPlugins(true);
}

#ifdef __cplusplus
}
#endif
