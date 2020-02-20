#include <jni.h>
/* Header file for CresLog */

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

#ifndef __CresLog_H__
#define __CresLog_H__
#ifdef __cplusplus
extern "C" {
#endif

/*
 * Class:           CresLog
 * Method:          nativeInit
 * Signature:       ()V
 */
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_CresLog_nativeInit(JNIEnv *, jobject);

/*
 * Class:           WbsStreamIn
 * Method:          nativeFinalize
 * Signature:       ()V
 */
JNIEXPORT void JNICALL Java_com_crestron_txrxservice_CresLog_nativeFinalize(JNIEnv *, jobject);

///////////////////////////////////////////////////////////////////////////////
#ifdef __cplusplus
}
#endif
#endif
