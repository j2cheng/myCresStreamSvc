#include <jni.h>
/* Header file for GstreamOut */

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

#ifndef __GstreamOut_H__
#define __GstreamOut_H__
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     		GstreamOut
 * Method:    		nativeSetRtspPort
 * Signature: 		(II)V
 */
JNIEXPORT void JNICALL Java_com_crestron_GstreamOut_nativeSetRtspPort(JNIEnv *env, jobject thiz, jint port, jint sessionId);

/*
 * Class:     		GstreamOut
 * Method:    		nativeSet_Res_x
 * Signature: 		(II)V
 */
JNIEXPORT void JNICALL Java_com_crestron_GstreamOut_nativeSet_Res_x(JNIEnv *env, jobject thiz, jint Res_x, jint sessionId);

/*
 * Class:     		GstreamOut
 * Method:    		nativeSet_Res_y
 * Signature: 		(II)V
 */
JNIEXPORT void JNICALL Java_com_crestron_GstreamOut_nativeSet_Res_y(JNIEnv *env, jobject thiz, jint Res_y, jint sessionId);

/*
 * Class:     		GstreamOut
 * Method:    		nativeSet_FrameRate
 * Signature: 		(II)V
 */
JNIEXPORT void JNICALL Java_com_crestron_GstreamOut_nativeSet_FrameRate(JNIEnv *env, jobject thiz, jint FrameRate, jint sessionId);

///////////////////////////////////////////////////////////////////////////////


#ifdef __cplusplus
}
#endif
#endif
