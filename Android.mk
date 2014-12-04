LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)


LOCAL_MODULE_TAGS := eng

LOCAL_PROGUARD_ENABLED := disabled


# Only compile source java files in this apk.
LOCAL_SRC_FILES := $(call all-java-files-under, src)

# This is the target being built.
LOCAL_PACKAGE_NAME := CresStreamSvc

ifeq ($(TARGET_PRODUCT),full_omap5panda)
include $(BUILD_PACKAGE)
endif
