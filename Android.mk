LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

# Temporarily blocking the build
ifneq ($(TARGET_PRODUCT),$(filter $(TARGET_PRODUCT),am62x evk_8mm))

    ifneq ($(shell test $(PLATFORM_SDK_VERSION) -ge 30 && echo Android12),Android12)
        LOCAL_MODULE_TAGS := eng
    endif

    LOCAL_PROGUARD_ENABLED := disabled
    LOCAL_MANIFEST := $(shell cp "$(LOCAL_PATH)/AndroidManifest-base.xml" "$(LOCAL_PATH)/AndroidManifest.xml")
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

    ifneq ($(TARGET_PRODUCT),$(filter $(TARGET_PRODUCT),lahaina))
        # Native functions in jni folder
        LOCAL_SHARED_LIBRARIES := libgstreamer_jni
    endif

    LOCAL_STATIC_JAVA_LIBRARIES := gson
    LOCAL_STATIC_JAVA_LIBRARIES += CresStoreJsonJNI

    ifeq ($(TARGET_PRODUCT),$(filter $(TARGET_PRODUCT),msm8953_64 lahaina am62x evk_8mm))
    	LOCAL_MULTILIB := 32
    	LOCAL_MODULE_PATH := $(TARGET_OUT_VENDOR)/priv-app

    ifeq ($(TARGET_PRODUCT),$(filter $(TARGET_PRODUCT),msm8953_64))
    	LOCAL_SRC_FILES += $(call all-java-files-under, Snapdragon)
    endif

    ifeq ($(TARGET_PRODUCT),$(filter $(TARGET_PRODUCT),lahaina))
    	LOCAL_SRC_FILES += $(call all-java-files-under, Snapdragon_X80)
    	LOCAL_MANIFEST := $(shell cp "$(LOCAL_PATH)/Snapdragon_X80/AndroidManifest.xml" "$(LOCAL_PATH)")
    endif

    #	In AOSP(8.0), Surface.aidl moved from frameworks/base... to frameworks/native...
    	LOCAL_AIDL_INCLUDES += frameworks/native/aidl/gui
    #	Ensure CresStreamSvc builds for 32-bit access to these libraries
    	LOCAL_MODULE_TARGET_ARCH := arm
    	LOCAL_PREBUILT_JNI_LIBS_arm := /../../../${PRODUCT_OUT}/vendor/lib/libgstreamer_jni.so
    	LOCAL_PREBUILT_JNI_LIBS_arm += /../../../${PRODUCT_OUT}/vendor/lib/libcresstreamctrl_jni.so
    	LOCAL_PREBUILT_JNI_LIBS_arm += /../../../${PRODUCT_OUT}/vendor/lib/libCsioProdInfo.so
    	LOCAL_STATIC_JAVA_LIBRARIES += \
            android-support-v4 \
            android-support-v7-appcompat \
            android-support-design

    ifneq ($(TARGET_PRODUCT),$(filter $(TARGET_PRODUCT),lahaina am62x evk_8mm))
	    LOCAL_STATIC_JAVA_LIBRARIES += droideic
    	LOCAL_AAPT_FLAGS += --extra-packages com.droideic.app
    endif

    ifeq ($(shell test $(PLATFORM_SDK_VERSION) -ge 30 && echo Android12),Android12)
        LOCAL_PRIVATE_PLATFORM_APIS := true
    else
    ifeq ($(shell test $(PLATFORM_SDK_VERSION) -ge 29 && echo Android10),Android10)
        LOCAL_PRIVATE_PLATFORM_APIS := true
    else
        LOCAL_JNI_SHARED_LIBRARIES := libdisplaysetting
    endif
    endif
    	LOCAL_PROGUARD_ENABLED := disabled
    	LOCAL_AAPT_FLAGS += \
            --auto-add-overlay \
            --extra-packages android.support.v7.appcompat \
            --extra-packages android.support.design
    endif

    ifeq ($(TARGET_PRODUCT),$(filter $(TARGET_PRODUCT), am3x00_box kona qssi))
        LOCAL_MULTILIB := 32
        LOCAL_MODULE_PATH := $(TARGET_OUT_VENDOR)/priv-app
        LOCAL_PRIVATE_PLATFORM_APIS := true
        #Product Specific file
        LOCAL_SRC_FILES += $(call all-java-files-under, am3x00_box)
    #   In AOSP(8.0), Surface.aidl moved from frameworks/base... to frameworks/native...
        LOCAL_AIDL_INCLUDES += frameworks/native/aidl/gui
    #   Ensure CresStreamSvc builds for 32-bit access to these libraries
        LOCAL_MODULE_TARGET_ARCH := arm
        LOCAL_PREBUILT_JNI_LIBS_arm := ../../../${PRODUCT_OUT}/vendor/lib/libgstreamer_jni.so
        LOCAL_PREBUILT_JNI_LIBS_arm += ../../../${PRODUCT_OUT}/vendor/lib/libcresstreamctrl_jni.so
        LOCAL_PREBUILT_JNI_LIBS_arm += ../../../${PRODUCT_OUT}/vendor/lib/libCsioProdInfo.so
    	LOCAL_STATIC_JAVA_LIBRARIES += am3x00
        LOCAL_STATIC_JAVA_LIBRARIES += \
            android-support-v4 \
            android-support-v7-appcompat \
            android-support-design
        LOCAL_PROGUARD_ENABLED := disabled
        LOCAL_AAPT_FLAGS += \
            --auto-add-overlay \
            --extra-packages android.support.v7.appcompat \
            --extra-packages android.support.design \
            --extra-packages com.droideic.app
    endif

    ifeq ($(TARGET_PRODUCT),$(filter $(TARGET_PRODUCT),yushan_one full_omap5panda msm8953_64 am3x00_box lahaina am62x evk_8mm kona qssi))

        ifeq ($(TARGET_PRODUCT),$(filter $(TARGET_PRODUCT),lahaina))
            LOCAL_DEX_PREOPT := false
        endif
        include $(BUILD_PACKAGE)

        ifeq ($(TARGET_PRODUCT),$(filter $(TARGET_PRODUCT),lahaina kona qssi))
            LOCAL_REQUIRED_MODULES := txrxservice-default-permissions.xml
            include $(CLEAR_VARS)
            LOCAL_MODULE := txrxservice-default-permissions.xml
            LOCAL_MODULE_CLASS := ETC
            LOCAL_MODULE_PATH := $(TARGET_OUT_VENDOR)/etc/default-permissions
            LOCAL_SRC_FILES := $(LOCAL_MODULE)
            include $(BUILD_PREBUILT)
        endif

        ifeq ($(TARGET_PRODUCT),$(filter $(TARGET_PRODUCT),kona qssi))
            LOCAL_REQUIRED_MODULES := txrxservice-permissions.xml
            include $(CLEAR_VARS)
            LOCAL_MODULE := txrxservice-permissions.xml
            LOCAL_MODULE_CLASS := ETC
            LOCAL_MODULE_PATH := $(TARGET_OUT_VENDOR)/etc/permissions
            LOCAL_SRC_FILES := $(LOCAL_MODULE)
            include $(BUILD_PREBUILT)
        endif

        include $(LOCAL_PATH)/jni/Android.mk
        include $(CLEAR_VARS)
        LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := gson:../libs/gson-2.3.1.jar
        LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES += CresStoreJsonJNI:../libs/CresStoreJsonJNI.jar

        ifeq ($(TARGET_PRODUCT),$(filter $(TARGET_PRODUCT), am3x00_box kona qssi))
            LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES += am3x00:../libs/am3x00.jar
        endif
        include $(BUILD_MULTI_PREBUILT)
    endif
endif
