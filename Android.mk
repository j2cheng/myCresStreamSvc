LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := eng

LOCAL_PROGUARD_ENABLED := disabled


# Only compile source java files in this apk.
LOCAL_SRC_FILES := $(call all-java-files-under, src) 
LOCAL_SRC_FILES += $(call all-Iaidl-files-under, src)
ifeq ($(TARGET_PRODUCT),$(filter $(TARGET_PRODUCT),full_omap5panda))
	LOCAL_SRC_FILES += $(call all-java-files-under, Omap5)
endif
ifeq ($(TARGET_PRODUCT),$(filter $(TARGET_PRODUCT),yushan_one))
	LOCAL_SRC_FILES += $(call all-java-files-under, AMLogic)
	LOCAL_JAVA_LIBRARIES := droidlogic
endif

LOCAL_AIDL_INCLUDES := $(call all-Iaidl-files-under, src)

# This is the target being built.
LOCAL_PACKAGE_NAME := CresStreamSvc

LOCAL_CERTIFICATE := platform

# Native functions in jni folder
LOCAL_SHARED_LIBRARIES := libgstreamer_jni
	

LOCAL_STATIC_JAVA_LIBRARIES := gson

ifeq ($(TARGET_PRODUCT),$(filter $(TARGET_PRODUCT),yushan_one full_omap5panda))
include $(BUILD_PACKAGE)
include $(LOCAL_PATH)/jni/Android.mk

include $(CLEAR_VARS)
LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := gson:../libs/gson-2.3.1.jar
include $(BUILD_MULTI_PREBUILT)
endif


