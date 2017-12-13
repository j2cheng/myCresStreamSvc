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
import android.os.SystemClock;

///////////////////////////////////////////////////////////////////////////////

public class GstreamOut {

    static String TAG = "GstreamOut";

///////////////////////////////////////////////////////////////////////////////

    // Function prototypes for 
    private static native boolean nativeClassInitRtspServer();
    private native void nativeRtspServerStart();
    private native void nativeRtspServerStop();
    private native void nativeInitRtspServer(Object s);     
    private native void nativeFinalizeRtspServer();    
    private native void nativeSetRtspPort(int port, int sessionId);
    private native void nativeSet_Res_x(int xRes, int sessionId);
    private native void nativeSet_Res_y(int yRes, int sessionId);
    private native void nativeSet_FrameRate(int frameRate, int sessionId);
    private native void nativeSet_Bitrate(int bitrate, int sessionId);
    private native void nativeSet_IFrameInterval(int iframeinterval, int sessionId);
    private native void nativeSet_MulticastEnable(boolean enable, int sessionId);
    private native void nativeSet_MulticastAddress(String address, int sessionId);
    private native void nativeSet_StreamName(String name, int sessionId);
    private native void nativeSet_SnapshotName(String name, int sessionId);
    private native void nativeStartPreview(Object surface, int sessionId);
    private native void nativePausePreview(int sessionId);
    private native void nativeStopPreview(int sessionId);
    private native int  nativeWaitForPreviewAvailable(int sessionId,int timeout_sec);
    private native int  nativeWaitForPreviewClosed(int sessionId,int timeout_sec);
   
    private final int sessionId = 0;    // This is currently always 0
    private long native_custom_data;    // Native code will use this to keep private data
    private Object mSurface;            // We keep surface as just an object because that's how we pass it to jni
    private CresStreamCtrl streamCtl;
    private int idx;
    private boolean camStreamActive = false;
    private boolean previewActive = false;
    private boolean resReleased = true;   // default need to be true
    
///////////////////////////////////////////////////////////////////////////////

    static {
        Log.i(TAG, "class init");
        nativeClassInitRtspServer();
    }

    public GstreamOut(CresStreamCtrl ctl) {
        Log.i(TAG, "Streamout: JAVA - constructor called");
        streamCtl = ctl;
        //Don't start server until we have a surface to get data from...
        //nativeInitRtspServer(null);
        
        if (streamCtl.userSettings.getCamStreamEnable() == true)
        {
            start();
        }
    }

    public void setSessionIndex(int id){
        idx = id;
    }
    
    public int getSessionIndex(){
        return(idx);
    }

    public void setSurface(Object s) {
        //Log.i(TAG, "Set surface to " + s);
        //mSurface = s;
    }
    
    public void start() {
        if (streamCtl.mCameraDisabled == false)
        {
            Log.i(TAG, "Streamout: JAVA - start() call nativeInitRtspServer, previewActive = " + previewActive);
            if (resReleased) {
                nativeInitRtspServer(null);
                resReleased = false;
            }
            
            updateCamStreamUrl();
            updateCamSnapshotUrl();
            updateNativeDataStruct();
            Log.i(TAG, "Streamout: JAVA - start() call nativeRtspServerStart");
            nativeRtspServerStart();
            camStreamActive = true;
        }
    }
    
    public void stop() {
        updateCamStreamUrl();
        updateCamSnapshotUrl();

        camStreamActive = false;
        if (previewActive) {
            Log.i(TAG, "Streamout: JAVA - stop() RtspServer ONLY");
            nativeRtspServerStop();
        } 
        else { 
            Log.i(TAG, "Streamout: JAVA - stop() finalize RtspServer");
            nativeFinalizeRtspServer();
            resReleased = true;
        }
    }
    
    public void setPort(int port) {
        nativeSetRtspPort(port, sessionId);
    }
    
    public void setMulticastEnable(boolean enable) {
        nativeSet_MulticastEnable(enable, sessionId);
    }
    
    public void setResolution(int resolution) {
//      switch (resolution)
//      {
//      case 10: //1280x720
            nativeSet_Res_x(1280, sessionId);
            nativeSet_Res_y(720, sessionId);
//          break;
//      case 17: //1920x1080
//          nativeSet_Res_x(1920, sessionId);
//          nativeSet_Res_y(1080, sessionId);
//          break;
//      default:
//          break;
//      }
    }
    
    public void setFramerate(int fps) {
        nativeSet_FrameRate(fps, sessionId);
    }
    
    public void setBitrate(int bitrate) {
        nativeSet_Bitrate(bitrate, sessionId);
    }

    public void setIFrameInterval(int iframeinterval) {
        nativeSet_IFrameInterval(iframeinterval, sessionId);
    }
            
    public void setCamStreamName(String name) {     
        nativeSet_StreamName(name, sessionId);      
    }
    
    public void setCamStreamSnapshotName(String name) {
        nativeSet_SnapshotName(name, sessionId);
        updateCamSnapshotUrl();
    }
    
    public void setCamStreamMulticastAddress(String address) {
        nativeSet_MulticastAddress(address, sessionId);
    }
    
    private void updateNativeDataStruct() {
        setPort(streamCtl.userSettings.getCamStreamPort());        
        setMulticastEnable(streamCtl.userSettings.getCamStreamMulticastEnable());        
        setResolution(streamCtl.userSettings.getCamStreamResolution());
        setFramerate(streamCtl.userSettings.getCamStreamFrameRate());        
        setBitrate(streamCtl.userSettings.getCamStreamBitrate());        
        setIFrameInterval(streamCtl.userSettings.getCamStreamIFrameInterval());        
        setCamStreamName(streamCtl.userSettings.getCamStreamName());
        setCamStreamSnapshotName(streamCtl.userSettings.getCamStreamSnapshotName());        
        setCamStreamMulticastAddress(streamCtl.userSettings.getCamStreamMulticastAddress());
    }

    public String buildCamStreamUrl()
    {
        StringBuilder url = new StringBuilder(1024);
        url.append("");
    
        if ( (streamCtl.mCameraDisabled == false) && (streamCtl.userSettings.getCamStreamEnable() == true) )
        {
            int port = streamCtl.userSettings.getCamStreamPort();
            String deviceIp= streamCtl.userSettings.getDeviceIp();
            String file = streamCtl.userSettings.getCamStreamName();
            
            url.append("rtsp://").append(deviceIp).append(":").append(port).append("/").append(file).append(".sdp");
        } 
        Log.i(TAG, "buildCamStreamUrl() CamStreamUrl = "+url.toString());
    
        return url.toString();
    }
    
    public String buildCamSnapshotUrl()
    {
        StringBuilder url = new StringBuilder(1024);
        url.append("");
    
        if ( (streamCtl.mCameraDisabled == false) && (streamCtl.userSettings.getCamStreamEnable() == true) )
        {
            String deviceIp= streamCtl.userSettings.getDeviceIp();
            String file = streamCtl.userSettings.getCamStreamSnapshotName();
            
            url.append("http://").append(deviceIp).append("/camera/").append(file).append(".jpg");
        } 
        Log.i(TAG, "buildCamSnapshotUrl()  = " + url.toString());
    
        return url.toString();
    }
    
    public void updateCamStreamUrl()
    {
        String camUrl = buildCamStreamUrl();
        
        streamCtl.userSettings.setCamStreamUrl(camUrl);
    
        streamCtl.sockTask.SendDataToAllClients(String.format("CAMERA_STREAMING_STREAM_URL=%s", camUrl));
    }
    
    public void updateCamSnapshotUrl()
    {
        String snapshotUrl = buildCamSnapshotUrl();
        
        streamCtl.userSettings.setCamStreamSnapshotUrl(snapshotUrl);
    
        streamCtl.sockTask.SendDataToAllClients(String.format("CAMERA_STREAMING_SNAPSHOT_URL=%s", snapshotUrl));
    }
 
    protected void startPreview(Object surface, int sessionId) {
        Log.i(TAG, "Streamout: startPreview() resReleased = " + resReleased);
        if (streamCtl.mCameraDisabled == false)
        {
            if (resReleased) {
                Log.i(TAG, "Streamout: startPreview() reinit all resources + waitForPreviewAvailable");
                nativeInitRtspServer(null);
                //waitForPreviewAvailable(0, 5);
                resReleased = false;
            }

            Log.i(TAG, "Streamout: startPreview() ");
            nativeStartPreview(surface,sessionId);
            previewActive = true;
        }
        
        SystemClock.sleep(2000);
        Log.i(TAG, "Streamout: now getCamStreamEnable = " + streamCtl.userSettings.getCamStreamEnable());
        if (streamCtl.userSettings.getCamStreamEnable() == false) {
            stop();
        }           

    }
    
    protected void pausePreview(int sessionId) {
        //Log.i(TAG, "Streamout: pausePreview() is_preview = " + streamCtl.cam_preview.is_preview);
        if (streamCtl.mCameraDisabled == false)
        {
            nativePausePreview(sessionId);
        }
    }

    protected void stopPreview(int sessionId) {
        if (streamCtl.mCameraDisabled == false)
        {
            Log.i(TAG, "Streamout: stopPreview() camStreamActive = " + camStreamActive + ", resReleased = "+ resReleased );
            nativeStopPreview(sessionId);
            previewActive = false;
            
            if (!camStreamActive && !resReleased) {
                Log.i(TAG, "Streamout: stopPreview() release all resources");
                nativeFinalizeRtspServer();
                resReleased = true;
            }
        }
    }
    
    protected int waitForPreviewAvailable(int sessionId,int timeout_sec) {
        int rtn = -1;   
        if (streamCtl.mCameraDisabled == false)
        {
            Log.i(TAG, "Streamout: waitForPreviewAvailable() ");
            rtn = nativeWaitForPreviewAvailable(sessionId,timeout_sec);
        }
        
        return(rtn);
    }

    protected int waitForPreviewClosed(int sessionId,int timeout_sec) {
        int rtn = -1;   
        if (streamCtl.mCameraDisabled == false)
        {
            Log.i(TAG, "Streamout: waitForPreviewClosed() ");
            rtn = nativeWaitForPreviewClosed(sessionId,timeout_sec);
        }
        
        return(rtn);
    }

    public void recoverTxrxService()
    {
        streamCtl.RecoverTxrxService();         
    }

    public void sendCameraStopFb()
    {
        streamCtl.sockTask.SendDataToAllClients("CAMERA_STREAMING_ENABLE=false");           
    }
        
    
///////////////////////////////////////////////////////////////////////////////
    
    protected void onDestroy() {
        Log.i(TAG, "destructor called");
        nativeFinalizeRtspServer();
    }    
    
///////////////////////////////////////////////////////////////////////////////

    private void setMessage(final String message) {
        Log.i(TAG, "setMessage " + message);
    }
}
