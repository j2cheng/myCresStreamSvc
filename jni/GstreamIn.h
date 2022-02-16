/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header file for GstreamIn */

///////////////////////////////////////////////////////////////////////////////
/* JNI signatures
* Type     Character
* boolean      Z
* byte         B
* char         C
* double       D
* float        F
* int          I
* long         J
* object       L
* short        S
* void         V
* array        [
*/
///////////////////////////////////////////////////////////////////////////////

#ifndef __GstreamIn_H__
#define __GstreamIn_H__
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     		GstreamIn
 * Method:    		nativeSetSeverUrl
 * Signature: 		(Ljava/lang/string; I)V
 */
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetServerUrl(JNIEnv *, jobject, jstring, jint);

/*
 * Class:     GstreamIn
 * Method:    nativeSetRtspPort
 * Signature: (II)V
 */
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetRtspPort(JNIEnv *, jobject, jint, jint);

/*
 * Class:     GstreamIn
 * Method:    nativeSetTsPort
 * Signature: (II)V
 */
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetTsPort(JNIEnv *, jobject, jint, jint);

/*
 * Class:     GstreamIn
 * Method:    nativeSetHdcpEncrypt
 * Signature: (ZI)V
 */
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetHdcpEncrypt(JNIEnv *, jobject, jboolean, jint);

/*
 * Class:     GstreamIn
 * Method:    nativeSetRtpVideoPort
 * Signature: (II)V
 */
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetRtpVideoPort(JNIEnv *, jobject, jint, jint);

/*
 * Class:     GstreamIn
 * Method:    nativeSetRtpAudioPort
 * Signature: (II)V
 */
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetRtpAudioPort(JNIEnv *, jobject, jint, jint);

/*
 * Class:     GstreamIn
 * Method:    nativeSetSessionInitiation
 * Signature: (II)V
 */
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetSessionInitiation(JNIEnv *, jobject, jint, jint);

/*
 * Class:     GstreamIn
 * Method:    nativeSetTransportMode
 * Signature: (II)V
 */
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetTransportMode(JNIEnv *, jobject, jint, jint);

/*
 * Class:     		GstreamIn
 * Method:    		nativeSetMulticastAddress
 * Signature: 		(Ljava/lang/string; I)V
 */
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetMulticastAddress(JNIEnv *, jobject, jstring, jint);

/*
 * Class:     GstreamIn
 * Method:    nativeSetStreamingBuffer
 * Signature: (II)V
 */
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetStreamingBuffer(JNIEnv *, jobject, jint, jint);

/*
 * Class:     GstreamIn
 * Method:    nativeSetXYlocations
 * Signature: (III)V
 */
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetXYlocations(JNIEnv *, jobject, jint, jint, jint);

/*
 * Class:     GstreamIn
 * Method:    nativeSetStatistics
 * Signature: (ZI)V
 */
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetStatistics(JNIEnv *, jobject, jboolean, jint);

/*
 * Class:     GstreamIn
 * Method:    nativeResetStatistics
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeResetStatistics(JNIEnv *, jobject, jint);

/*
 * Class:     GstreamIn
 * Method:    nativeSetNewSink
 * Signature: (ZI)V
 */
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetNewSink(JNIEnv *, jobject, jboolean, jint);

/*
 * Class:     		GstreamIn
 * Method:    		nativeSetUserName
 * Signature: 		(Ljava/lang/string; I)V
 */
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetUserName(JNIEnv *, jobject, jstring, jint);

/*
 * Class:           GstreamIn
 * Method:    nativesetFieldDebugJni
 * Signature: (Ljava/lang/string; I)V
 */
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetFieldDebugJni(JNIEnv *, jobject, jstring, jint);

/*
 * Class:     		GstreamIn
 * Method:    		nativeSetPassword
 * Signature: 		(Ljava/lang/string; I)V
 */
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetPassword(JNIEnv *, jobject, jstring, jint);

/*
 * Class:     		GstreamIn
 * Method:    		nativeSetVolume
 * Signature: 		(II)V
 */
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetVolume(JNIEnv *, jobject, jint, jint);

/*
 * Class:     		GstreamIn
 * Method:    		nativeSetStopTimeout
 * Signature: 		(I)V
 */
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetStopTimeout(JNIEnv *, jobject, jint);

/*
 * Class:     		GstreamIn
 * Method:    		nativeDropAudio
 * Signature: 		(ZZI)V
 */
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeDropAudio(JNIEnv *env, jobject thiz, jboolean enabled, jboolean dropAudioPipeline, jint sessionId);

/*
 * Class:     		GstreamIn
 * Method:    		nativeSetLogLevel
 * Signature: 		(I)V
 */
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetLogLevel(JNIEnv *env, jobject thiz, jint logLevel);

/*
 * Class:           GstreamIn
 * Method:          nativeSetTcpMode
 * Signature:       (I)V
 */
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetTcpMode(JNIEnv *env, jobject thiz, jint tcpMode, jint sessionId);

/*
 * Class:           GstreamIn
 * Method:          nativeInitUnixSocketState
 * Signature:       ()V
 */
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeInitUnixSocketState(JNIEnv *env, jobject thiz);

/*
 * Class:           GstreamIn
 * Method:          nativeSetRTCPDestIP
 * Signature:       (Ljava/lang/string; I)V
 */
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetRTCPDestIP(JNIEnv *env, jobject thiz, jstring rtcpIp, jint sessionId);

/*
 * Class:           GstreamIn
 * Method:          nativeSetWfdMaxMiracastBitrate
 * Signature:       (I)V
 */
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetWfdMaxMiracastBitrate(JNIEnv *env, jobject thiz, jint maxrate);

/*
 * Class:           GstreamIn
 * Method:          nativeSet30HzOnly
 * Signature:       (Z)V
 */
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeSetWfd30HzOnly(JNIEnv *env, jobject thiz, jboolean enable);

/*
 * Class:           GstreamIn
 * Method:          nativeWfdStart
 * Signature:       (IJLjava/lang/string; ILjava/lang/string; Ljava/lang/string;)V
 */
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeWfdStart(JNIEnv *env, jobject thiz, jint windowId, jlong sessionId, jstring url_jstring, jint rtsp_port, jstring localAddress, jstring localIfc);

/*
 * Class:           GstreamIn
 * Method:          nativeWfdStop
 * Signature:       (IJ)V
 */
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeWfdStop(JNIEnv *env, jobject thiz, jint windowId, jlong sessionId);

/*
 * Class:           GstreamIn
 * Method:          nativeWfdPause
 * Signature:       (I)V
 */
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeWfdPause(JNIEnv *env, jobject thiz, jint windowId);

/*
 * Class:           GstreamIn
 * Method:          nativeWfdResume
 * Signature:       (I)V
 */
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeWfdResume(JNIEnv *env, jobject thiz, jint windowId);

/*
 * Class:           GstreamIn
 * Method:          nativeMsMiceStart
 * Signature:       ()V
 */
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeMsMiceStart(JNIEnv *env, jobject thiz);

/*
 * Class:           GstreamIn
 * Method:          nativeMsMiceStop
 * Signature:       ()V
 */
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeMsMiceStop(JNIEnv *env, jobject thiz);

/*
 * Class:           GstreamIn
 * Method:          nativeMsMiceCloseSession
 * Signature:       (J)V
 */
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeMsMiceCloseSession(JNIEnv *env, jobject thiz, jlong session_id);

/*
 * Class:           GstreamIn
 * Method:          nativeMsMiceSetAdapterAddress
 * Signature:       (Ljava/lang/string;)V
 */
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeMsMiceSetAdapterAddress(JNIEnv *env, jobject thiz, jstring address);

/*
 * Class:           GstreamIn
 * Method:          nativeMsMiceSetPin
 * Signature:       (Ljava/lang/string;)V
 */
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_GstreamIn_nativeMsMiceSetPin(JNIEnv *env, jobject thiz, jstring pin_jstring);

///////////////////////////////////////////////////////////////////////////////
eStreamState nativeGetCurrentStreamState(jint);

#ifdef __cplusplus
}
#endif
#endif
