///////////////////////////////////////////////////////////////////////////////
//
// Copyright (C) 2022 to the present, Crestron Electronics, Inc.
// All rights reserved.
// No part of this software may be reproduced in any form, 
// machine or natural, 
// without the express written consent of Crestron Electronics.
//  
///////////////////////////////////////////////////////////////////////////////
//
// \file        UsbVolumeCtrl.java
// 
// \brief       Java class to interface to native mixer api
// 
// \author      Vivek Bardia
// 
// \date        03/10/2022
// 
///////////////////////////////////////////////////////////////////////////////

package com.crestron.txrxservice;

import android.util.Log;

///////////////////////////////////////////////////////////////////////////////

public class UsbVolumeCtrl {

    public int devVolume = 0;
    public boolean devMute = true;
    public boolean devVolSupport = false;

    static String TAG = "UsbVolumeCtrl";

    // Function prototypes for 
    public native boolean nativeSetPeripheralMute(String audioPlaybackFile, boolean peripheralMute);
    public native boolean nativeSetPeripheralVolume(String audioPlaybackFile, int peripheralVolume);
    public native boolean nativeGetPeripheralVolume(String audioPlaybackFile, UsbVolumeCtrl mUsbVolumeCtrlDevice);

    private CresStreamCtrl streamCtl;

///////////////////////////////////////////////////////////////////////////////

    public UsbVolumeCtrl(CresStreamCtrl ctl) {
        Log.i(TAG, "UsbVolumeCtrl: JAVA - constructor called");
        streamCtl = ctl;
    }

    public int setUsbPeripheralVolume(String audioPlaybackFile, int peripheralVolume)
    {
        int val = -1;
        Log.i(TAG, "UsbVolume: setUsbPeripheralVolume: incoming device ="+audioPlaybackFile+", Volume = "+peripheralVolume);
        if(nativeSetPeripheralVolume(audioPlaybackFile,peripheralVolume)) {
            val = 0;
            Log.i(TAG, "UsbVolume: Volume set success");
        }
        return val;
    }

    public int setUsbPeripheralMute(String audioPlaybackFile, boolean peripheralMute)
    {
        int val = -1;
        Log.i(TAG, "UsbVolume: setUsbPeripheralMute: incoming device ="+audioPlaybackFile+", Mute = "+peripheralMute);
        if(nativeSetPeripheralMute(audioPlaybackFile, peripheralMute)) {
            val = 0;
            Log.i(TAG, "UsbVolume: mute set success");
        }
        return val;
    }

    public int getUsbPeripheralVolume(String audioPlaybackFile)
    {
        int val = -1;
        Log.v(TAG, "UsbVolume: getUsbPeripheralVolume: For device ="+audioPlaybackFile);
        if(nativeGetPeripheralVolume(audioPlaybackFile, this))
        {
                val = 0;
                Log.i(TAG, "UsbVolume: getUsbPeripheralVolume: devVolSupport = " + devVolSupport );
                Log.i(TAG, "UsbVolume: getUsbPeripheralVolume: devVolume = " + devVolume );
                Log.i(TAG, "UsbVolume: getUsbPeripheralVolume: devMute = " + devMute );
        }
        return val;
    }
}
