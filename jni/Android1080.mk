LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE_PATH  := $(TARGET_OUT_VENDOR)/lib/
#LOCAL_SRC_FILES := ${LOCAL_PATH}/../../../../out/soong/.intermediates/hardware/interfaces/graphics/bufferqueue/1.0/android.hardware.graphics.bufferqueue@1.0/android_vendor.30_arm_armv8-2a_cortex-a75_shared/android.hardware.graphics.bufferqueue@1.0.so 
#LOCAL_VENDOR_LIB := $(shell cp "$(LOCAL_SRC_FILES)" "$(LOCAL_MODULE_PATH)")
LOCAL_VENDOR_LIB := $(shell cp "$(LOCAL_PATH)/../Snapdragon_X80/android.hardware.graphics.bufferqueue@1.0.so" "$(TARGET_OUT_VENDOR)/lib/")

#LOCAL_SRC_FILES := ${LOCAL_PATH}/../../../../out/soong/.intermediates/hardware/interfaces/graphics/bufferqueue/2.0/android.hardware.graphics.bufferqueue@2.0/android_vendor.30_arm_armv8-2a_cortex-a75_shared/android.hardware.graphics.bufferqueue@2.0.so
#LOCAL_VENDOR_LIB := $(shell cp "$(LOCAL_SRC_FILES)" "$(LOCAL_MODULE_PATH)")
LOCAL_VENDOR_LIB := $(shell cp "$(LOCAL_PATH)/../Snapdragon_X80/android.hardware.graphics.bufferqueue@2.0.so" "$(TARGET_OUT_VENDOR)/lib/")

#LOCAL_SRC_FILES := ${LOCAL_PATH}/../../../../out/soong/.intermediates/system/libhidl/transport/token/1.0/utils/android.hidl.token@1.0-utils/android_vendor.30_arm_armv8-2a_cortex-a75_shared/android.hidl.token@1.0-utils.so
#LOCAL_VENDOR_LIB := $(shell cp "$(LOCAL_SRC_FILES)" "$(LOCAL_MODULE_PATH)")
LOCAL_VENDOR_LIB := $(shell cp "$(LOCAL_PATH)/../Snapdragon_X80/android.hidl.token@1.0-utils.so" "$(TARGET_OUT_VENDOR)/lib/")

#LOCAL_SRC_FILES := ${LOCAL_PATH}/../../../../out/out/soong/.intermediates/hardware/interfaces/media/1.0/android.hardware.media@1.0/android_vendor.30_arm_armv8-2a_cortex-a75_shared/android.hardware.media@1.0.so
#LOCAL_VENDOR_LIB := $(shell cp "$(LOCAL_SRC_FILES)" "$(LOCAL_MODULE_PATH)")
LOCAL_VENDOR_LIB := $(shell cp "$(LOCAL_PATH)/../Snapdragon_X80/android.hardware.media@1.0.so" "$(TARGET_OUT_VENDOR)/lib/")

#LOCAL_SRC_FILES := ${LOCAL_PATH}/../../../../out/out/soong/.intermediates/system/libhidl/transport/token/1.0/android.hidl.token@1.0/android_vendor.30_arm_armv8-2a_cortex-a75_shared/android.hidl.token@1.0.so
#LOCAL_VENDOR_LIB := $(shell cp "$(LOCAL_SRC_FILES)" "$(LOCAL_MODULE_PATH)")
LOCAL_VENDOR_LIB := $(shell cp "$(LOCAL_PATH)/../Snapdragon_X80/android.hidl.token@1.0.so" "$(TARGET_OUT_VENDOR)/lib/")

#LOCAL_SRC_FILES := ${LOCAL_PATH}/../../../../out/out/soong/.intermediates/frameworks/native/libs/ui/libui/android_vendor.30_arm_armv8-2a_cortex-a75_shared/libui.so
#LOCAL_VENDOR_LIB := $(shell cp "$(LOCAL_SRC_FILES)" "$(LOCAL_MODULE_PATH)")
LOCAL_VENDOR_LIB := $(shell cp "$(LOCAL_PATH)/../Snapdragon_X80/libui.so" "$(TARGET_OUT_VENDOR)/lib/")

#LOCAL_SRC_FILES := ${LOCAL_PATH}/../../../../out/out/soong/.intermediates/hardware/interfaces/graphics/allocator/2.0/android.hardware.graphics.allocator@2.0/android_vendor.30_arm_armv8-2a_cortex-a75_shared/android.hardware.graphics.allocator@2.0.so
#LOCAL_VENDOR_LIB := $(shell cp "$(LOCAL_SRC_FILES)" "$(LOCAL_MODULE_PATH)")
LOCAL_VENDOR_LIB := $(shell cp "$(LOCAL_PATH)/../Snapdragon_X80/android.hardware.graphics.allocator@2.0.so" "$(TARGET_OUT_VENDOR)/lib/")

#LOCAL_SRC_FILES := ${LOCAL_PATH}/../../../../out/out/soong/.intermediates/hardware/interfaces/graphics/allocator/3.0/android.hardware.graphics.allocator@3.0/android_vendor.30_arm_armv8-2a_cortex-a75_shared/android.hardware.graphics.allocator@3.0.so
#LOCAL_VENDOR_LIB := $(shell cp "$(LOCAL_SRC_FILES)" "$(LOCAL_MODULE_PATH)")
LOCAL_VENDOR_LIB := $(shell cp "$(LOCAL_PATH)/../Snapdragon_X80/android.hardware.graphics.allocator@3.0.so" "$(TARGET_OUT_VENDOR)/lib/")

#LOCAL_SRC_FILES := ${LOCAL_PATH}/../../../../out/out/soong/.intermediates/hardware/interfaces/graphics/allocator/4.0/android.hardware.graphics.allocator@4.0/android_vendor.30_arm_armv8-2a_cortex-a75_shared/android.hardware.graphics.allocator@4.0.so
#LOCAL_VENDOR_LIB := $(shell cp "$(LOCAL_SRC_FILES)" "$(LOCAL_MODULE_PATH)")
LOCAL_VENDOR_LIB := $(shell cp "$(LOCAL_PATH)/../Snapdragon_X80/android.hardware.graphics.allocator@4.0.so" "$(TARGET_OUT_VENDOR)/lib/")
