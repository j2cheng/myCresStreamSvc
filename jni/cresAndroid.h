#ifndef __CRESANDROID_H__
#define __CRESANDROID_H__
#ifdef BOARD_VNDK_VERSION
#include <jni.h>
#include <android/native_window.h>
#include <utils/StrongPointer.h>

int cres_register_android_view_Surface(JNIEnv* env);
ANativeWindow* cres_ANativeWindow_fromSurface(JNIEnv* env, jobject surface);
#endif
#endif //__CRESANDROID_H__
