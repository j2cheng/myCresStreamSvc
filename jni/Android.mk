LOCAL_PATH := $(call my-dir)

########################
# /system/lib
########################
include $(CLEAR_VARS)
LOCAL_MODULE := libgstreamer_android.so
LOCAL_MODULE_CLASS := SHARED_LIBRARIES
LOCAL_MODULE_TAGS := eng
LOCAL_SRC_FILES := ./gstreamer_android/libgstreamer_android.so
include $(BUILD_PREBUILT)



include $(CLEAR_VARS)
GSTREAMER_ROOT_ANDROID := $(LOCAL_PATH)/../../../gstreamer-1.8.1
CSIO_INCLUDE_ROOT := $(LOCAL_PATH)/../../csio
CSIO_ROOT := ../../csio
CRESTRON_ROOT := $(LOCAL_PATH)/../..
LOCAL_MODULE    := libgstreamer_jni
STREAMOUT_PATH := $(LOCAL_PATH)/cresStreamOut
LOCAL_SRC_FILES := \
	jni.cpp \
	cregstplay.cpp \
	gst_element_print_properties.cpp \
	cresStreamOut/streamOutManager/cresStreamOutManager.cpp \
	cresStreamOut/streamOutManager/cres_rtsp-media.cpp \
	cresStreamOut/streamOutManager/cresStreamSnapShot.cpp \
	cresStreamOut/streamOutManager/cresPreview.cpp \
	cresStreamOut/streamOutManager/cresCamera.cpp \
	cresStreamOut/streamOutDebug/cresStreamOutDebug.cpp \
	cresStreamOut/cresStreamOutProject.cpp \
	cresStreamOut/streamOutUtils/cresProjectBaseClass.cpp \
	cresStreamOut/streamOutUtils/cresStreamOutUtils.cpp \
	Wbs.cpp \
	TileDecoder.cpp \
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
LOCAL_SHARED_LIBRARIES += libCresSocketHandler
LOCAL_SHARED_LIBRARIES += libssl
LOCAL_SHARED_LIBRARIES += libcrypto


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
	-I$(GSTREAMER_ROOT_ANDROID)/lib/gstreamer-1.0/include \
	-I$(CRESTRON_ROOT)/common/include \
	-I$(CRESTRON_ROOT)/cipclientd/include \
	-I$(CRESTRON_ROOT)/libiplinkclientwrapper/include \
	-I$(CRESTRON_ROOT)/libCresSocketHandler \
	-I$(CRESTRON_ROOT)/productNameUtil \
	-I$(CRESTRON_ROOT)/Include/External \
	-I$(CRESTRON_ROOT)/MJPEGPlayer \
	-I$(CSIO_INCLUDE_ROOT) \
    -I$(CSIO_INCLUDE_ROOT)/crestHdcp \
	-I$(CSIO_INCLUDE_ROOT)/txrx \
	-I$(CSIO_INCLUDE_ROOT)/url_parser \
	-I$(CSIO_INCLUDE_ROOT)/gstreamer-1.0 \
	-I$(STREAMOUT_PATH) \
	-I$(CRESTRON_ROOT)/../openssl/include \
	-DANDROID_OS

ifeq ($(TARGET_PRODUCT),$(filter $(TARGET_PRODUCT),yushan_one ))
LOCAL_CFLAGS += -DBIONIC_HAS_STPCPY
endif

ifeq ($(TARGET_PRODUCT),$(filter $(TARGET_PRODUCT),full_omap5panda)) #All products which support HDCP 2X encryption need this flag
LOCAL_SHARED_LIBRARIES += libHdcp2xEncryptApi
LOCAL_CFLAGS += -I$(CRESTRON_ROOT)/Hdcp2x/HDCP2xEncryptAPI
LOCAL_CFLAGS += -DSupportsHDCPEncryption
endif

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

### library for CresStreamCtrl jni functions
include $(CLEAR_VARS)
CRESTRON_ROOT := $(LOCAL_PATH)/../..
CSIO_INCLUDE_ROOT := $(LOCAL_PATH)/../../csio
LOCAL_MODULE := libcresstreamctrl_jni
LOCAL_CFLAGS +=\
	-DANDROID_OS \
	-I$(CSIO_INCLUDE_ROOT) \
	-I$(CRESTRON_ROOT)/productNameUtil \
	-I$(CRESTRON_ROOT)/common/include \
	-I$(CRESTRON_ROOT)/Include/External \
	-I$(CSIO_INCLUDE_ROOT)/crestHdcp \
	-I$(CPP_INC_PATH) \
	-I$(STL_INC_PATH)
	
#	-I$(CPP_INC_PATH) \
#	-I$(COMMON_INC_PATH) \
#	-I$(STL_INC_PATH) \
# iMX53 #
ifeq ($(TARGET_PRODUCT),imx53_smd)
LOCAL_CFLAGS +=\
	-I$(CSIO_INCLUDE_ROOT)/tsx
endif
# iMX6 #
ifeq ($(TARGET_PRODUCT),iWave_G15M)
LOCAL_CFLAGS +=\
	-I$(CSIO_INCLUDE_ROOT)/str
endif
# OMAP5 #
ifeq ($(TARGET_PRODUCT),$(filter $(TARGET_PRODUCT),full_omap5panda)) 
LOCAL_CFLAGS +=\
	-I$(CSIO_INCLUDE_ROOT)/txrx 
endif
# AM Logic #
# For now just use the txrx code.  Move this out if needed
ifeq ($(TARGET_PRODUCT),$(filter $(TARGET_PRODUCT),yushan_one))
LOCAL_CFLAGS +=\
	-I$(CSIO_INCLUDE_ROOT)/txrx 
endif	

LOCAL_LDLIBS := -llog
LOCAL_SHARED_LIBRARIES := libCsioProdInfo
LOCAL_SRC_FILES := cresstreamctrl_jni.cpp

LOCAL_MODULE_TAGS := eng
LOCAL_PRELINK_MODULE := false
include $(BUILD_SHARED_LIBRARY)









