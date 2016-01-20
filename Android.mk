LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := eng

LOCAL_PROGUARD_ENABLED := disabled


# Only compile source java files in this apk.
LOCAL_SRC_FILES := $(call all-java-files-under, src)

# This is the target being built.
LOCAL_PACKAGE_NAME := CresStreamSvc

LOCAL_CERTIFICATE := platform

# Native functions in jni folder
LOCAL_SHARED_LIBRARIES := libgstreamer_jni
	

LOCAL_STATIC_JAVA_LIBRARIES := gson
ifeq ($(TARGET_PRODUCT),$(filter $(TARGET_PRODUCT),full_omap5panda))
#ifeq ($(TARGET_PRODUCT),$(filter $(TARGET_PRODUCT),yushan_one full_omap5panda))
include $(BUILD_PACKAGE)
include $(LOCAL_PATH)/jni/Android.mk

include $(CLEAR_VARS)
LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := gson:../libs/gson-2.3.1.jar
include $(BUILD_MULTI_PREBUILT)
endif


