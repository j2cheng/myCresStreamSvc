LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
GSTREAMER_ROOT_ANDROID := $(LOCAL_PATH)/../../../gstreamer-1.4.5
CSIO_INCLUDE_ROOT := $(LOCAL_PATH)/../../csio
CSIO_ROOT := ../../csio
CRESTRON_ROOT := $(LOCAL_PATH)/../..
LOCAL_MODULE    := libgstreamer_jni
LOCAL_SRC_FILES := \
	jni.c \
	cregstplay.c \
	gst_element_print_properties.c \
	$(CSIO_ROOT)/gstreamer-1.0/csioutils.cpp \
	$(CSIO_ROOT)/gstreamer-1.0/cstream.cpp \
	$(CSIO_ROOT)/gstreamer-1.0/cstreamer.cpp \
	$(CSIO_ROOT)/gstreamer-1.0/cstreamermc.cpp \
	$(CSIO_ROOT)/gstreamer-1.0/cstreameruc.cpp \
	$(CSIO_ROOT)/gstreamer-1.0/padsUtil.cpp\
	$(CSIO_ROOT)/gstreamer-1.0/csiosubs.cpp \
	$(CSIO_ROOT)/gstreamer-1.0/vputils.cpp \
    $(CSIO_ROOT)/csioCommonShare.cpp \
    $(CSIO_ROOT)/url_parser/url_parser.cpp

COMMON_INC_PATH := $(CRESTRON_ROOT)/Include
UTIL_INC_PATH := $(CRESTRON_ROOT)/Utilities
STL_INC_PATH := $(CRESTRON_ROOT)/../stlport/stlport
CPP_INC_PATH := $(CRESTRON_ROOT)/../../bionic


# Crestron - name was different
#LOCAL_SHARED_LIBRARIES := gstreamer_android
LOCAL_SHARED_LIBRARIES := libgstreamer_android liblog libandroid
LOCAL_SHARED_LIBRARIES += libproductName
LOCAL_SHARED_LIBRARIES += libLinuxUtil


# Crestron - why do I have to do this?
#LOCAL_LDLIBS := -llog -landroid
#LOCAL_LDFLAGS := -llog -landroid 
### Crestron added - why do I need to do this?
LOCAL_CFLAGS +=\
	-I$(CPP_INC_PATH) \
	-I$(COMMON_INC_PATH) \
	-I$(STL_INC_PATH) \
	-I$(GSTREAMER_ROOT_ANDROID)/include/gstreamer-1.0 \
	-I$(GSTREAMER_ROOT_ANDROID)/include/glib-2.0 \
	-I$(GSTREAMER_ROOT_ANDROID)/lib/glib-2.0/include \
	-I$(CRESTRON_ROOT)/common/include \
	-I$(CRESTRON_ROOT)/cipclientd/include \
	-I$(CRESTRON_ROOT)/libiplinkclientwrapper/include \
	-I$(CRESTRON_ROOT)/productNameUtil \
	-I$(CRESTRON_ROOT)/Include/External \
	-I$(CRESTRON_ROOT)/MJPEGPlayer \
	-I$(CSIO_INCLUDE_ROOT) \
	-I$(CSIO_INCLUDE_ROOT)/txrx \
	-I$(CSIO_INCLUDE_ROOT)/url_parser \
	-I$(CSIO_INCLUDE_ROOT)/gstreamer-1.0 \
	-DANDROID_OS
LOCAL_MODULE_TAGS := eng
LOCAL_PRELINK_MODULE := false
include $(BUILD_SHARED_LIBRARY)
###############################################################################
# Crestron - commenting this out b/c was built elsewhere
# ifndef GSTREAMER_ROOT
# ifndef GSTREAMER_ROOT_ANDROID
# $(error GSTREAMER_ROOT_ANDROID is not defined!)
# endif
# GSTREAMER_ROOT        := $(GSTREAMER_ROOT_ANDROID)
# endif
# GSTREAMER_NDK_BUILD_PATH  := $(GSTREAMER_ROOT)/share/gst-android/ndk-build/
# include $(GSTREAMER_NDK_BUILD_PATH)/plugins.mk
# GSTREAMER_PLUGINS         := $(GSTREAMER_PLUGINS_CORE) $(GSTREAMER_PLUGINS_SYS) $(GSTREAMER_PLUGINS_EFFECTS)  $(GSTREAMER_PLUGINS_NET) $(GSTREAMER_PLUGINS_CODECS) $(GSTREAMER_PLUGINS_PLAYBACK) $(GSTREAMER_PLUGINS_CODECS_RESTRICTED) rtsp rtp
# GSTREAMER_EXTRA_DEPS      := gstreamer-video-1.0
# include $(GSTREAMER_NDK_BUILD_PATH)/gstreamer-1.0.mk





########################
# /system/lib
########################

include $(CLEAR_VARS)
LOCAL_MODULE := libgstreamer_android.so
LOCAL_MODULE_CLASS := SHARED_LIBRARIES
LOCAL_MODULE_TAGS := eng
LOCAL_SRC_FILES := libgstreamer_android.so
include $(BUILD_PREBUILT)


