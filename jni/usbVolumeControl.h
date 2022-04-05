/**
 * Copyright (C) 2022 to the present, Crestron Electronics, Inc.
 * All rights reserved.
 * No part of this software may be reproduced in any form, machine
 * or natural, without the express written consent of Crestron Electronics.
 * 
 * \file        usbVolumeControl.h
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
#include <jni.h>
#include <string>
#include <cstring>
#include "csioCommonShare.h"

#ifdef HAS_TINYALSA
#include "/opt/rk3399_android/android/external/tinyalsa/include/tinyalsa/asoundlib.h"
#endif
/* Header for class Java_com_crestron_txrxservice_UsbVolumeCtrl */

///////////////////////////////////////////////////////////////////////////////
/* JNI signatures
* Type     Character
* boolean      Z
* byte         B
* char         C
* double       D
* float        F
* int          I
* long         J
* object       L
* short        S
* void         V
* array        [
*/
///////////////////////////////////////////////////////////////////////////////
#ifndef __usbVolumeControl_H__
#define __usbVolumeControl_H__
#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jboolean JNICALL Java_com_crestron_txrxservice_UsbVolumeCtrl_nativeSetPeripheralMute(    JNIEnv *env, 
                                                    jobject thiz,
                                                    jstring deviceId,
                                                    jboolean peripheralMute);
                                                    
JNIEXPORT jboolean JNICALL Java_com_crestron_txrxservice_UsbVolumeCtrl_nativeSetPeripheralVolume(  JNIEnv *env, 
                                                    jobject thiz,
                                                    jstring deviceId,
                                                    jint peripheralVolume);

JNIEXPORT jboolean JNICALL Java_com_crestron_txrxservice_UsbVolumeCtrl_nativeGetPeripheralVolume(  JNIEnv *env, 
                                                    jobject thiz,
                                                    jstring deviceId,
                                                    jobject devvolume);

#ifdef __cplusplus
}
#endif

static int percent_to_int_rounded(int min, int max, int percent);
static int int_to_percent_rounded(int min, int max, int in_value);
bool isPresent_mutectl_byname(std::string ctlName);
bool isPresent_volumectl_byname(std::string ctlName);

//https://www.kernel.org/doc/html/latest/sound/designs/control-names.html
std::string const mFunctionVolume[] = {"Volume", "Volum"}; //Function keyword for Volume Mixer Control
std::string const mFunctionMute[] = {"Switch", "Switc"}; //Function keyword for Mute(Switch)) Mixer Control
std::string const mDirection = "Playback"; //Direction for Mixer Control
std::string const mSourceArray[] = {"Master", "Speaker", "Headphone", "PCM", "Headset", "Speakerphone"}; //Source for Mixer Control. May needs updates based on peripheral custom names

class UsbPeripheralVolume
{
public:
    UsbPeripheralVolume(const char *file);
    ~UsbPeripheralVolume();

    bool configure(int card_idx);
    void releaseDevice();
#ifdef HAS_TINYALSA
    struct mixer_ctl *getPlaybackVolume_ctl(struct mixer *mixer);
    struct mixer_ctl *getPlaybackMute_ctl(struct mixer *mixer);
    int getPlaybackVolume(UsbPeripheralVolume *m_vol_handle);
    bool getPlaybackMuteStatus(UsbPeripheralVolume *m_vol_handle);
    const char* getPlaybackDeviceName(UsbPeripheralVolume *m_vol_handle);

    struct mixer     *p_mixer = NULL;
    struct mixer_ctl *ctlVolume   = NULL;
    struct mixer_ctl *ctlMute     = NULL;
    unsigned int num_ctl_values = 0;
#endif
    unsigned int m_card_idx;
    unsigned int m_device_idx;    
    char m_device_file[128];

};


#endif
