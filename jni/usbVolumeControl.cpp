/**
 * Copyright (C) 2022 to the present, Crestron Electronics, Inc.
 * All rights reserved.
 * No part of this software may be reproduced in any form, machine
 * or natural, without the express written consent of Crestron Electronics.
 * 
 * \file        usbVolumeControl.cpp
 *
 * \brief       Java <--> native interface code.
 *
 * \author      Vivek Bardia
 *
 * \date        3/10/2022
 *
 *
 * This module is intended to encapsulate the mixer interface with Java/Android.
 * 
 */

///////////////////////////////////////////////////////////////////////////////
#include "usbVolumeControl.h"
#include <string.h>
#include <android/log.h>

#ifdef HAS_TINYALSA
JNIEXPORT jboolean JNICALL Java_com_crestron_txrxservice_UsbVolumeCtrl_nativeSetPeripheralMute(    JNIEnv *env, 
                                                    jobject thiz,
                                                    jstring deviceId,
                                                    jboolean peripheralMute)
{
    jboolean retVal = JNI_TRUE;
    const char * devId_cstring = env->GetStringUTFChars( deviceId , NULL ) ;
    if (devId_cstring == NULL) return false;

    CSIO_LOG(eLogLevel_debug,"In Native Function for %s nativeSetPeripheralMute with Value %d\n", devId_cstring, peripheralMute);
    UsbPeripheralVolume *m_vol_handle = new UsbPeripheralVolume(devId_cstring);
    unsigned int i;

    m_vol_handle->ctlMute = m_vol_handle->getPlaybackMute_ctl(m_vol_handle->p_mixer);
    if (!m_vol_handle->ctlMute) {
        CSIO_LOG(eLogLevel_error, "volume switch mixer control invalid or not available");
        retVal = JNI_FALSE;
    }
    else
    {
        m_vol_handle->num_ctl_values = mixer_ctl_get_num_values(m_vol_handle->ctlMute);
        for (i = 0; i < m_vol_handle->num_ctl_values; i++) {
            /* Set all values the same */
            if (mixer_ctl_set_value(m_vol_handle->ctlMute, i, peripheralMute)) {
                CSIO_LOG(eLogLevel_error, "Error: invalid mixer value\n");
                retVal = JNI_FALSE;
            }
        }
    }

    env->ReleaseStringUTFChars(deviceId, devId_cstring);

    delete m_vol_handle;
    return retVal;
}

JNIEXPORT jboolean JNICALL Java_com_crestron_txrxservice_UsbVolumeCtrl_nativeSetPeripheralVolume(  JNIEnv *env, 
                                                    jobject thiz,
                                                    jstring deviceId,
                                                    jint peripheralVolume)
{
    jboolean retVal = JNI_TRUE;
    const char * devId_cstring = env->GetStringUTFChars( deviceId , NULL ) ;
    if (devId_cstring == NULL) return false;

    CSIO_LOG(eLogLevel_debug,"In Native Function for %s nativeSetPeripheralVolume with Value %d\n", devId_cstring, peripheralVolume);
    UsbPeripheralVolume *m_vol_handle = new UsbPeripheralVolume(devId_cstring);
    unsigned int i;

    m_vol_handle->ctlVolume = m_vol_handle->getPlaybackVolume_ctl(m_vol_handle->p_mixer);

    if (!m_vol_handle->ctlVolume) {
        CSIO_LOG(eLogLevel_error, "volume mixer control invalid or not available");
        retVal = JNI_FALSE;
    }
    else
    {
        m_vol_handle->num_ctl_values = mixer_ctl_get_num_values(m_vol_handle->ctlVolume);
        for (i = 0; i < m_vol_handle->num_ctl_values; i++) {
            /* Set all values the same */
            if (mixer_ctl_set_percent(m_vol_handle->ctlVolume, i, peripheralVolume)) {
                CSIO_LOG(eLogLevel_error, "Error: invalid mixer value\n");
                retVal = JNI_FALSE;
            }
        }
    }

    env->ReleaseStringUTFChars(deviceId, devId_cstring);
    delete m_vol_handle;

    return retVal;
}

JNIEXPORT jboolean JNICALL Java_com_crestron_txrxservice_UsbVolumeCtrl_nativeGetPeripheralVolume(  JNIEnv *env, 
                                                    jobject thiz,
                                                    jstring deviceId,
                                                    jobject devvolume)
{
    jboolean retVal = JNI_FALSE;
    jint curVolume = 0;
    jboolean curMuteVal = false;
    jboolean isVolCtrlsSupported = false;

    const char * devId_cstring = env->GetStringUTFChars( deviceId , NULL ) ;
    if (devId_cstring == NULL) return false;

    CSIO_LOG(eLogLevel_debug,"In Native Function nativeGetIsPeripheralVolumeSupported for %s\n", devId_cstring);
    UsbPeripheralVolume *m_vol_handle = new UsbPeripheralVolume(devId_cstring);

    m_vol_handle->ctlVolume = m_vol_handle->getPlaybackVolume_ctl(m_vol_handle->p_mixer);
    if (m_vol_handle->ctlVolume == NULL) {
        CSIO_LOG(eLogLevel_error, "volume mixer control invalid or not available");
    }

    m_vol_handle->ctlMute = m_vol_handle->getPlaybackMute_ctl(m_vol_handle->p_mixer);
    if (m_vol_handle->ctlMute == NULL) {
        CSIO_LOG(eLogLevel_error, "volume switch mixer control invalid or not available");
    }

    if((m_vol_handle->ctlMute != NULL) && (m_vol_handle->ctlVolume != NULL)) //Assumption, if either is null we claim Volume controls not supported
    {
        isVolCtrlsSupported = true;
        curVolume = m_vol_handle->getPlaybackVolume(m_vol_handle); 
        curMuteVal = m_vol_handle->getPlaybackMuteStatus(m_vol_handle);

        CSIO_LOG(eLogLevel_debug,"In Native Function nativeGetIsPeripheralVolumeSupported for %d, %d, %d\n", isVolCtrlsSupported, curVolume, curMuteVal);

        retVal = JNI_TRUE;
    }

    // Find the id of the Java method to be called and fill the java object class UsbVolumeCtrl
    jclass devvolumeClass=env->GetObjectClass(devvolume);
    jfieldID fieldId = env->GetFieldID(devvolumeClass , "devVolume", "I");
    env->SetIntField(devvolume, fieldId, curVolume);
    fieldId = env->GetFieldID(devvolumeClass , "devMute", "Z");
    env->SetBooleanField(devvolume, fieldId, curMuteVal);
    fieldId = env->GetFieldID(devvolumeClass , "devVolSupport", "Z");
    env->SetBooleanField(devvolume, fieldId, isVolCtrlsSupported);

    env->ReleaseStringUTFChars(deviceId, devId_cstring);
    delete m_vol_handle;

    return retVal;
}

struct mixer_ctl * UsbPeripheralVolume::getPlaybackVolume_ctl(struct mixer *mixer)
{
    unsigned int num_ctls;
    unsigned int i;
    const char *name;
    struct mixer_ctl *lookupCtl = NULL;

    num_ctls = mixer_get_num_ctls(mixer);
    
    for (i = 0; i < num_ctls; i++) {
        lookupCtl = mixer_get_ctl(mixer, i);

        name = mixer_ctl_get_name(lookupCtl);

        if(isPresent_volumectl_byname(name))
        {
            CSIO_LOG(eLogLevel_info, "Volume Property: %s",name);
            break;
        }
    }

    if(i == num_ctls)
        lookupCtl = NULL;

    return lookupCtl;
}

struct mixer_ctl * UsbPeripheralVolume::getPlaybackMute_ctl(struct mixer *mixer)
{
    unsigned int num_ctls;
    unsigned int i;
    const char *name;
    struct mixer_ctl *lookupCtl = NULL;

    num_ctls = mixer_get_num_ctls(mixer);

    for (i = 0; i < num_ctls; i++) {
        lookupCtl = mixer_get_ctl(mixer, i);

        name = mixer_ctl_get_name(lookupCtl);

        if(isPresent_mutectl_byname(name))
        {
            CSIO_LOG(eLogLevel_info, "Mute Property: %s",name);
            break;
        }
    }

    if(i == num_ctls)
        lookupCtl = NULL;

    return lookupCtl;
}

int UsbPeripheralVolume::getPlaybackVolume(UsbPeripheralVolume *m_vol_handle)
{
    int retval = -1;
    int retvalp = -1;
    unsigned int n_ctl_values = 0;
    int min = 0;
    int max = 0;

    n_ctl_values = mixer_ctl_get_num_values(m_vol_handle->ctlVolume);
    min = mixer_ctl_get_range_min(m_vol_handle->ctlVolume);
    max = mixer_ctl_get_range_max(m_vol_handle->ctlVolume);

    for (unsigned int i = 0; i < n_ctl_values; i++) {
        /* get all values and is the same */
        retval = mixer_ctl_get_value(m_vol_handle->ctlVolume, i);
        retvalp = int_to_percent(min,max,retval);
    }
    return retvalp;
}

bool UsbPeripheralVolume::getPlaybackMuteStatus(UsbPeripheralVolume *m_vol_handle)
{
    bool retval = false;
    unsigned int n_ctl_values = 0;
    n_ctl_values = mixer_ctl_get_num_values(m_vol_handle->ctlMute);
    for (unsigned int i = 0; i < n_ctl_values; i++) {
        if(mixer_ctl_get_value(m_vol_handle->ctlMute, i))
            retval = false;//If On, means mute is disabled
    }
    return retval;
}

static int int_to_percent(int min, int max, int in_value) //Since mixer_ctl_get_percent not working
{
    int range = 0;
    range = (max - min);
    if (range == 0)
        return 0;
    return ((double)(in_value - min) / range) * 100;
}

bool isPresent_volumectl_byname(std::string ctlName)
{
    size_t i = 0;
    size_t size;
    std::size_t foundf;
    std::size_t foundd;
    std::size_t founds;

    size = sizeof(mFunctionVolume) / sizeof(*mFunctionVolume);
    for (i = 0; i < size; ++i) {
        foundf = ctlName.find(mFunctionVolume[i]);
        foundd = ctlName.find(mDirection);

        if ((foundf != std::string::npos) && (foundd != std::string::npos))
        {
            break;
        }
    }

    if(i == size){
        return false;
    }

    size = sizeof(mSourceArray) / sizeof(*mSourceArray);
    for (i = 0; i < size; ++i) {
        founds = ctlName.find(mSourceArray[i]);
        if ((founds != std::string::npos))
        {
            break;
        }
    }

    if(i == size){
        return false;
    }

    return true;
}

bool isPresent_mutectl_byname(std::string ctlName)
{
    size_t i = 0;
    size_t size;
    std::size_t foundf;
    std::size_t foundd;
    std::size_t founds;

    size = sizeof(mFunctionMute) / sizeof(*mFunctionMute);
    for (i = 0; i < size; ++i) {
        foundf = ctlName.find(mFunctionMute[i]);
        foundd = ctlName.find(mDirection);

        if ((foundf != std::string::npos) && (foundd != std::string::npos))
        {
            break;
        }
    }

    if(i == size){
        return false;
    }

    size = sizeof(mSourceArray) / sizeof(*mSourceArray);
    for (i = 0; i < size; ++i) {
        founds = ctlName.find(mSourceArray[i]);
        if ((founds != std::string::npos))
        {
            break;
        }
    }

    if(i == size){
        return false;
    }

    return true;
}

bool UsbPeripheralVolume::configure(int card_idx)
{
    struct mixer *config = NULL;
    memset(&config, 0, sizeof(config));

    if (p_mixer == NULL)
    {
        config = mixer_open(card_idx); // open USB mixer audio device
        if (!config) {
            CSIO_LOG(eLogLevel_error, "Unable to open USB mixer device\n");
            return false;
        }
        else
        {
            p_mixer = config;
            //CSIO_LOG(eLogLevel_verbose, "USB audio MIXER device card=%d device=%d is open for mixer controls\n", m_card_idx, m_device_idx);
            //CSIO_LOG(eLogLevel_verbose, "Mixer Name : %s \n", mixer_get_name(p_mixer));
        }
    }
    return true;
}

void UsbPeripheralVolume::releaseDevice()
{
    if (p_mixer)
    {
        CSIO_LOG(eLogLevel_verbose, "UsbPeripheralVolume: %s: freeing params", __FUNCTION__);
        mixer_close(p_mixer);
        p_mixer = NULL;
    }
}
#else

JNIEXPORT jboolean JNICALL Java_com_crestron_txrxservice_UsbVolumeCtrl_nativeSetPeripheralMute(    JNIEnv *env, 
                                                    jobject thiz,
                                                    jstring deviceId,
                                                    jboolean peripheralMute)
{
    return JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_com_crestron_txrxservice_UsbVolumeCtrl_nativeSetPeripheralVolume(  JNIEnv *env, 
                                                    jobject thiz,
                                                    jstring deviceId,
                                                    jint peripheralVolume)
{
    return JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_com_crestron_txrxservice_UsbVolumeCtrl_nativeGetPeripheralVolume(  JNIEnv *env, 
                                                    jobject thiz,
                                                    jstring deviceId,
                                                    jobject devvolume)
{
   return JNI_FALSE;
}

bool UsbPeripheralVolume::configure(int card_idx)
{
    return false;
}

void UsbPeripheralVolume::releaseDevice()
{

}

#endif

UsbPeripheralVolume::UsbPeripheralVolume(const char *file)
{
    CSIO_LOG(eLogLevel_verbose, "--UsbPeripheralVolume: usb audio device file: %s", file);
    strncpy(m_device_file, file, sizeof(m_device_file));

    //Audio playback Device Example /dev/snd/pcmC5D0p
    if (sscanf(file, "/dev/snd/pcmC%dD%dp", &m_card_idx, &m_device_idx) != 2)
    {
        CSIO_LOG(eLogLevel_warning, "UsbPeripheralVolume: Invalid audio device playback file: %s", file);
    }

    if(!(configure(m_card_idx)))
        CSIO_LOG(eLogLevel_error, "UsbPeripheralVolume: Coult not Configure device for mixer controls: %s", file);
}

UsbPeripheralVolume::~UsbPeripheralVolume()
{
    releaseDevice();
}
