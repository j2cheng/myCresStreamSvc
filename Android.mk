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

ifeq ($(TARGET_PRODUCT),$(filter $(TARGET_PRODUCT),msm8953_64))
	LOCAL_MULTILIB := 32
	LOCAL_MODULE_PATH := $(TARGET_OUT_VENDOR)/app/
	LOCAL_SRC_FILES += $(call all-java-files-under, Snapdragon)
	
#	In AOSP(8.0), Surface.aidl moved from frameworks/base... to frameworks/native...	
	LOCAL_AIDL_INCLUDES += frameworks/native/aidl/gui
	
endif
        
# This is the target being built.
LOCAL_PACKAGE_NAME := CresStreamSvc

LOCAL_CERTIFICATE := platform

# Native functions in jni folder
LOCAL_SHARED_LIBRARIES := libgstreamer_jni
	

LOCAL_STATIC_JAVA_LIBRARIES := gson

ifeq ($(TARGET_PRODUCT),$(filter $(TARGET_PRODUCT),yushan_one full_omap5panda msm8953_64))
#TODO: Re-enable x70. For now, do not include CresStreamSvc APK in the image until tested.
ifneq ($(TARGET_PRODUCT),$(filter $(TARGET_PRODUCT),msm8953_64))
include $(BUILD_PACKAGE)
endif
include $(LOCAL_PATH)/jni/Android.mk

include $(CLEAR_VARS)
LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := gson:../libs/gson-2.3.1.jar
include $(BUILD_MULTI_PREBUILT)
endif


