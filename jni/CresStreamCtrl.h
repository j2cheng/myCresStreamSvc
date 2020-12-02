#include <jni.h>

#ifndef __CresStreamCtrl_H__
#define __CresStreamCtrl_H__
#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jboolean JNICALL Java_com_crestron_txrxservice_CresStreamCtrl_nativeHaveExternalDisplays(JNIEnv *env, jobject thiz);

JNIEXPORT jboolean JNICALL Java_com_crestron_txrxservice_CresStreamCtrl_nativeHideVideoBeforeStop(JNIEnv *env, jobject thiz);

JNIEXPORT jint JNICALL Java_com_crestron_txrxservice_CresStreamCtrl_nativeGetHWPlatformEnum(JNIEnv *env, jobject thiz);

JNIEXPORT jint JNICALL Java_com_crestron_txrxservice_CresStreamCtrl_nativeGetProductTypeEnum(JNIEnv *env, jobject thiz);

JNIEXPORT jint JNICALL Java_com_crestron_txrxservice_CresStreamCtrl_nativeGetHDMIOutputBitmask(JNIEnv *env, jobject thiz);

JNIEXPORT jint JNICALL Java_com_crestron_txrxservice_CresStreamCtrl_nativeGetDmInputCount(JNIEnv *env, jobject thiz);

JNIEXPORT jboolean JNICALL Java_com_crestron_txrxservice_CresStreamCtrl_nativeGetIsAirMediaEnabledEnum(JNIEnv *env, jobject thiz);

JNIEXPORT jint JNICALL Java_com_crestron_txrxservice_CresStreamCtrl_nativeMaxVideoWindows(JNIEnv *env, jobject thiz);

JNIEXPORT jboolean JNICALL Java_com_crestron_txrxservice_CresStreamCtrl_nativeHaveHDMIoutput(JNIEnv *env, jobject thiz);

JNIEXPORT jboolean JNICALL Java_com_crestron_txrxservice_CresStreamCtrl_nativeProductOnlyAlphablend(JNIEnv *env, jobject thiz);
#ifdef __cplusplus
}
#endif
#endif
