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
