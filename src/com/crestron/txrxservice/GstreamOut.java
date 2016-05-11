///////////////////////////////////////////////////////////////////////////////
//
// Copyright (C) 2016 to the present, Crestron Electronics, Inc.
// All rights reserved.
// No part of this software may be reproduced in any form, 
// machine or natural, 
// without the express written consent of Crestron Electronics.
//  
///////////////////////////////////////////////////////////////////////////////
//
// \file        GstreamOut.java
// 
// \brief       Java class to interface to gstreamer rtsp server
// 
// \author      Pete McCormick
// 
// \date        04/15/2016
// 
// \note        Real gstreamer code is in jni.c
//
///////////////////////////////////////////////////////////////////////////////

package com.crestron.txrxservice;

import android.util.Log;

///////////////////////////////////////////////////////////////////////////////

public class GstreamOut {

    static String TAG = "GstreamOut";

///////////////////////////////////////////////////////////////////////////////

	// Function prototypes for 
    private static native boolean nativeClassInitRtspServer();
    private native void nativeInitRtspServer(Object s);     
    private native void nativeFinalizeRtspServer(); 
    
    private long native_custom_data;    // Native code will use this to keep private data
    private Object mSurface;			// We keep surface as just an object because that's how we pass it to jni
    private CresStreamCtrl streamCtl;
    
///////////////////////////////////////////////////////////////////////////////

    static {
		Log.d(TAG, "class init");
        nativeClassInitRtspServer();
    }

    public GstreamOut(CresStreamCtrl ctl) {
        Log.d(TAG, "constructor called");
        streamCtl = ctl;
        //Don't start server until we have a surface to get data from...
        nativeInitRtspServer(null);        
    }

    public void setSurface(Object s) {
		//Log.d(TAG, "Set surface to " + s);
		//mSurface = s;
    }
    
    public void start() {    
		//if(mSurface == null){
		//	Log.e(TAG, "Can't serve null surface");
		//	return;
		//}
		//Log.d(TAG, "Initializing rtsp server with surface " + mSurface);
		//nativeInitRtspServer(mSurface);
    }
    
    public void stop() {    
    }
    
///////////////////////////////////////////////////////////////////////////////
    
    protected void onDestroy() {
		Log.d(TAG, "destructor called");
        nativeFinalizeRtspServer();
    }    
    
///////////////////////////////////////////////////////////////////////////////

    private void setMessage(final String message) {
		Log.d(TAG, "setMessage " + message);
    }
}
