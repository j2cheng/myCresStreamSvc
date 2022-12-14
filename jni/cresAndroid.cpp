#include <gui/Surface.h>
#include <gui/SurfaceControl.h>
#include <android/native_window.h>
#include <jni.h>
#include <utils/misc.h>
#include <utils/Log.h>
#ifndef NELEM
#define NELEM(x) ((int) (sizeof(x) / sizeof((x)[0])))
#endif

using namespace android;

static jlong cres_nativeGetFromSurfaceControl(JNIEnv* env, jclass clazz,
        jlong nativeObject,
        jlong surfaceControlNativeObj);

static const void *s_RefBaseOwner;

static struct {
    jclass clazz;
    jfieldID mNativeObject;
    jfieldID mLock;
    jmethodID ctor;
} g_SurfaceClassInfo;

static const JNINativeMethod g_cresSurfaceMethods[] = {
    {"nativeGetFromSurfaceControl", "(JJ)J",
            (void*)cres_nativeGetFromSurfaceControl },
};

static jlong cres_nativeGetFromSurfaceControl(JNIEnv* env, jclass clazz,
        jlong nativeObject,
        jlong surfaceControlNativeObj) {

    if((env == NULL) || (clazz == NULL))
        return 0;

    Surface* self(reinterpret_cast<Surface *>(nativeObject));
    sp<SurfaceControl> ctrl(reinterpret_cast<SurfaceControl *>(surfaceControlNativeObj));

    // If the underlying IGBP's are the same, we don't need to do anything.
    if (self != nullptr &&
            IInterface::asBinder(self->getIGraphicBufferProducer()) ==
            IInterface::asBinder(ctrl->getIGraphicBufferProducer())) {
        return nativeObject;
    }

    sp<Surface> surface(ctrl->getSurface());
    if (surface != NULL) {
        surface->incStrong(&s_RefBaseOwner);
    }

    return reinterpret_cast<jlong>(surface.get());
}

static int cres_registerNativeMethods(JNIEnv* env, const char* className,
    JNINativeMethod* gMethods, int numMethods)
{
    jclass clazz;
    clazz = env->FindClass(className);
    if (clazz == NULL) {
        ALOGE("Native registration unable to find class '%s'", className);
        return JNI_FALSE;
    }
    if (env->RegisterNatives(clazz, gMethods, numMethods) < 0) {
        ALOGE("RegisterNatives failed for '%s'", className);
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

static jclass cres_FindClassOrDie(JNIEnv* env, const char* class_name) {
    jclass clazz = env->FindClass(class_name);
    LOG_ALWAYS_FATAL_IF(clazz == NULL, "Unable to find class %s", class_name);
    return clazz;
}

static jclass cres_MakeGlobalRefOrDie(JNIEnv* env, jclass in) {
    jobject res = env->NewGlobalRef(in);
    LOG_ALWAYS_FATAL_IF(res == NULL, "Unable to create global reference.");
    return static_cast<jclass>(res);
}

static jfieldID cres_GetFieldIDOrDie(JNIEnv* env, jclass clazz, const char* field_name,
                                       const char* field_signature) {
    jfieldID res = env->GetFieldID(clazz, field_name, field_signature);
    LOG_ALWAYS_FATAL_IF(res == NULL, "Unable to find field %s with signature %s", field_name,
                        field_signature);
    return res;
}

static sp<Surface> cres_android_view_Surface_getSurface(JNIEnv* env, jobject surfaceObj) {
    sp<Surface> sur;
    jobject lock = env->GetObjectField(surfaceObj,
            g_SurfaceClassInfo.mLock);
    if (env->MonitorEnter(lock) == JNI_OK) {
        sur = reinterpret_cast<Surface *>(
                env->GetLongField(surfaceObj, g_SurfaceClassInfo.mNativeObject));
        env->MonitorExit(lock);
    }
    env->DeleteLocalRef(lock);
    return sur;
}

int cres_register_android_view_Surface(JNIEnv* env)
{
    int err = cres_registerNativeMethods(env, "android/view/Surface",
            (JNINativeMethod *) g_cresSurfaceMethods, NELEM(g_cresSurfaceMethods));

    jclass clazz = cres_FindClassOrDie(env, "android/view/Surface");
    g_SurfaceClassInfo.clazz = cres_MakeGlobalRefOrDie(env, clazz);
    g_SurfaceClassInfo.mNativeObject = cres_GetFieldIDOrDie(env,
            g_SurfaceClassInfo.clazz, "mNativeObject", "J");
    g_SurfaceClassInfo.mLock = cres_GetFieldIDOrDie(env,
            g_SurfaceClassInfo.clazz, "mLock", "Ljava/lang/Object;");

    return err;
}

ANativeWindow* cres_ANativeWindow_fromSurface(JNIEnv* env, jobject surface) {
    sp<ANativeWindow> win = cres_android_view_Surface_getSurface(env, surface);
    if (win != NULL) {
        win->incStrong((void*)ANativeWindow_acquire);
    }
    return win.get();
}

