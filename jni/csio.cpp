#include <android/log.h>
#include <jni.h>

#include <csio.h>

#ifdef __cplusplus
extern "C" {
#endif
JNIEXPORT jint JNICALL
Java_com_crestron_txrxservice_CSIOService_csioTask(
    JNIEnv *env, jobject thiz,
    jstring internalStoragePath,
    jstring externalStoragePath)
{
    __android_log_print(
        ANDROID_LOG_INFO, "TXRX calling csio_Task",
        "%s:%d %s", __FILE__, __LINE__, __func__);

    CSIOCommonArgs args
    {
        env->GetStringUTFChars(internalStoragePath, NULL),
        env->GetStringUTFChars(externalStoragePath, NULL),
    };

    return csio_Task(std::move(args));
}
#ifdef __cplusplus
}
#endif
