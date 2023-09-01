#include <jni.h>

#include <csio.h>

#ifdef __cplusplus
extern "C" {
#endif
JNIEXPORT jint JNICALL
Java_com_crestron_txrxservice_CSIOService_csioTask(JNIEnv *env, jobject thiz)
{
    return csio_Task();
}
#ifdef __cplusplus
}
#endif
