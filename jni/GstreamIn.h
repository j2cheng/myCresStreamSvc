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

///////////////////////////////////////////////////////////////////////////////
StreamState nativeGetCurrentStreamState(jint);
void csio_send_stats (uint64_t, int, uint64_t, int, uint16_t);
int csio_SendVideoPlayingStatusMessage(unsigned int, StreamState);
int csio_SendVideoSourceParams(unsigned int, unsigned int, unsigned int, unsigned int, unsigned int);

#ifdef __cplusplus
}
#endif
#endif
