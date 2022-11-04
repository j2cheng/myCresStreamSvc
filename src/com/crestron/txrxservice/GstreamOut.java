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

import com.crestron.txrxservice.wc.ipc.WC_AudioFormat;
import com.crestron.txrxservice.wc.ipc.WC_SessionFlags;
import com.crestron.txrxservice.wc.ipc.WC_VideoFormat;

import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.Object;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import android.os.FileObserver;
import android.os.SystemClock;

///////////////////////////////////////////////////////////////////////////////

public class GstreamOut {

    static String TAG = "GstreamOut";

    private static final String RTSP_CERTIFICATE_FOLDER_PATH = "/dev/shm/crestron/CresStreamSvc/wc";
    private static final String RTSP_ROOT_CERT_PEM_FILENAME = "root_cert.pem";
    private static final String RTSP_ROOT_CERT_KEY = "root_key.pem";
    private static final String WC_URL_PATH = RTSP_CERTIFICATE_FOLDER_PATH + "/" + "server.url";

///////////////////////////////////////////////////////////////////////////////

    // Function prototypes for 
    private static native boolean nativeClassInitRtspServer();
    private native void nativeRtspServerStart();
    private native void nativeRtspServerStop();
    private native void nativeInitRtspServer(Object s);     
    private native void nativeInitWirelessConferencingRtspServer();
    private native void nativeFinalizeRtspServer();    
    private native void nativeSetRtspPort(int port, int sessionId);
    private native void nativeSet_Res_x(int xRes, int sessionId);
    private native void nativeSet_Res_y(int yRes, int sessionId);
    private native void nativeSet_FrameRate(int frameRate, int sessionId);
    private native void nativeSet_Bitrate(int bitrate, int sessionId);
    private native void nativeSet_IFrameInterval(int iframeinterval, int sessionId);
    private native int  nativeSet_Quality(int quality, int sessionId);
    private native void nativeSet_MulticastEnable(boolean enable, int sessionId);
    private native void nativeSet_MulticastAddress(String address, int sessionId);
    private native void nativeSet_StreamName(String name, int sessionId);
    private native void nativeSet_SnapshotName(String name, int sessionId);
    private native void nativeSet_WcSecurityEnable(boolean enable, int sessionId);
    private native void nativeSet_WcRandomUserPwEnable(boolean enable, int sessionId);
    private native void nativeSetAppCacheFolder(String name);
    private native void nativeSetHostName(String hostName);
    private native void nativeSetDomainName(String domainName);
    private native void nativeSetServerIpAddress(String ipAddr);
    private native void nativeSetVideoCaptureDevice(String device);
    private native void nativeSetAudioCaptureDevice(String device);
    private native int nativeGetVideoFormat(String videoFile, WC_VideoFormat format, int quality, String hdmi_in_res_x, String hdmi_in_res_y);
    private native int nativeGetAudioFormat(String videoFile, WC_AudioFormat format);
    private native void nativeStartPreview(Object surface, int sessionId);
    private native void nativePausePreview(int sessionId);
    private native void nativeStopPreview(int sessionId);
    private native int  nativeWaitForPreviewAvailable(int sessionId,int timeout_sec);
    private native int  nativeWaitForPreviewClosed(int sessionId,int timeout_sec);
    private native int  nativeSet_HDMIInResolution_x(int xRes, int sessionId);
    private native int  nativeSet_HDMIInResolution_y(int yRes, int sessionId);
   
    private final int sessionId = 0;    // This is currently always 0
    private long native_custom_data;    // Native code will use this to keep private data
    private Object mSurface;            // We keep surface as just an object because that's how we pass it to jni
    private CresStreamCtrl streamCtl;
    private int idx;
    private boolean camStreamActive = false;
    private boolean previewActive = false;
    private boolean resReleased = true;   // default need to be true
    private boolean wirelessConferencing_server_started = false;
    private String wcServerUrl = null;
    private String appCacheFolder = null;
    private CountDownLatch wcCertificateGenerationCompletedLatch = null;
    private FileObserver wcCertificateCompletionObserver = null;
    private final Object wcCertificateCompletionObserverLock = new Object();

    public boolean wcStarted() {return wirelessConferencing_server_started; }
    public String getWcServerUrl() { return wcServerUrl; }
    public String getWcServerCertificate() { return readStringFromDisk(RTSP_CERTIFICATE_FOLDER_PATH+"/"+RTSP_ROOT_CERT_PEM_FILENAME); }
    public String getWcServerKey() { return readStringFromDisk(RTSP_CERTIFICATE_FOLDER_PATH+"/"+RTSP_ROOT_CERT_KEY); }

///////////////////////////////////////////////////////////////////////////////

    static {
        Log.i(TAG, "class init");
        nativeClassInitRtspServer();
    }

    public static String readStringFromDisk(String filePath)
    {
        StringBuilder text = new StringBuilder();
        text.append(""); //default to blank string
        try {
            File file = new File(filePath);

            BufferedReader br = new BufferedReader(new FileReader(file));  
            String line;   
            while ((line = br.readLine()) != null) {
                text.append("\r\n");
                text.append(line);
            }
            br.close();
        }catch (Exception e) {}
        return text.toString();
    }
    
    public GstreamOut(CresStreamCtrl ctl) {
        Log.i(TAG, "Streamout: JAVA - constructor called");
        streamCtl = ctl;
        //Don't start server until we have a surface to get data from...
        //nativeInitRtspServer(null);
        
        if (!streamCtl.isWirelessConferencingEnabled && streamCtl.userSettings.getCamStreamEnable() == true)        {
            start();
        }
        appCacheFolder = streamCtl.getCacheDir().getAbsolutePath();
        Log.i(TAG, "Streamout: Application cache folder path = "+appCacheFolder);
        if (CresStreamCtrl.isAM3K)
        {
            File file = new File(WC_URL_PATH);
            if (!file.getParentFile().exists())
                file.getParentFile().mkdirs();  // make parent dirs if necessary
            monitorWcCertificateGenerationCompletion();
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

    public List<WC_VideoFormat> getVideoFormats(String videoFile)
    {
    	List<WC_VideoFormat> videoFormats = new ArrayList<WC_VideoFormat>();
    	if (videoFile.equalsIgnoreCase("none"))
    		Log.i(TAG, "videoFile is 'none' - no video formats");
    	else if (videoFile.contains("/dev/video")) {
            String hdmiHorRes, hdmiVerRes;  
    		WC_VideoFormat format = new WC_VideoFormat(0,0,0);
            if( streamCtl.hdmiInput != null )
            {
               hdmiHorRes = streamCtl.hdmiInput.getHorizontalRes();
               hdmiVerRes = streamCtl.hdmiInput.getVerticalRes();
            }
            else
            {
                hdmiHorRes = "0";
                hdmiVerRes = "0";
            }
            if(nativeGetVideoFormat(videoFile, format, streamCtl.userSettings.getAirMediaWCQuality(), hdmiHorRes, hdmiVerRes) == 0)
            {
                Log.i(TAG, "videoFile is "+videoFile+" videoFormat="+format);
                videoFormats.add(format);
            }
            else
                setWcServerError(streamCtl.mWC_Service.WCERROR_MODULE_VIDEO,
                                streamCtl.mWC_Service.ERROR_VIDEOCAPTURE_FORMAT,
                                "Error! Unsupported Video Format Detected");
    	} else {
    		Log.i(TAG, "videoFile is "+videoFile+" - no video formats");
    	}
    	return videoFormats;
    }
    
    public List<WC_AudioFormat> getAudioFormats(String audioFile)
    {
    	List<WC_AudioFormat> audioFormats = new ArrayList<WC_AudioFormat>();
    	if (audioFile.equalsIgnoreCase("none"))
    		Log.i(TAG, "audioFile is 'none' - no video formats");
    	else if (audioFile.contains("/dev/snd/pcm")) {
    		WC_AudioFormat format = new WC_AudioFormat(0,0,"");
    		if(nativeGetAudioFormat(audioFile, format) == 0)
            {
                Log.i(TAG, "audioFile is "+audioFile+" audioFormat="+format);
                audioFormats.add(format);
            }
            else
                setWcServerError(streamCtl.mWC_Service.WCERROR_MODULE_AUDIO,
                                streamCtl.mWC_Service.ERROR_AUDIOCAPTURE_FORMAT
                                ,"Error! Unsupported Audio Format Detected");
    	} else {
    		Log.i(TAG, "audioFile is "+audioFile+" - no video formats");
    	}
    	return audioFormats;
    }
    
    public void wirelessConferencing_start() {
        Log.i(TAG, "Streamout: JAVA - wirelessConferencing_start() entered" );
        if (!wirelessConferencing_server_started)
        {
            Log.i(TAG, "Streamout: JAVA - wirelessConferencing_start() call nativeInitWirelessConferencingRtspServer" );
            nativeInitWirelessConferencingRtspServer();
            updateNativeDataStructForWirelessConferencingStreaming();
            Log.i(TAG, "Streamout: JAVA - WirelessConferencing_start() call nativeRtspServerStart");
            nativeRtspServerStart();
            wirelessConferencing_server_started = true;
        } else {
            Log.i(TAG, "Streamout: JAVA - WirelessConferencing_start() ignoring server already started" );
        }
        Log.i(TAG, "Streamout: JAVA - WirelessConferencing_start() exit" );
    }

    public void wirelessConferencing_stop() {
        Log.i(TAG, "Streamout: JAVA - wirelessConferencing_stop() entered" );
        if (wirelessConferencing_server_started)
        {
            Log.i(TAG, "Streamout: JAVA - wirelessConferencing_stop() RtspServer ONLY");
            nativeRtspServerStop();
            //Log.i(TAG, "Streamout: JAVA - WirelessConferencing_stop() finalize RtspServer");
            //nativeFinalizeRtspServer();
            wirelessConferencing_server_started = false;
        } else {
            Log.i(TAG, "Streamout: JAVA - wirelessConferencing_stop() ignoring server is already stopped");
        }
        Log.i(TAG, "Streamout: JAVA - wirelessConferencing_stop() exit" );
    }

    private void updateNativeDataStructForWirelessConferencingStreaming() {
        final String hdmiAudio="/dev/snd/pcmC2D0c";
        Log.i(TAG, "Streamout: JAVA - updateNativeDataStructForWirelessConferencingStreaming entered" );
        if (streamCtl.userSettings.getWcAudioCaptureDevice().equalsIgnoreCase("aes"))
        {
            nativeSetVideoCaptureDevice("none");
            nativeSetAudioCaptureDevice(hdmiAudio);
        } else 
        {
            WC_SessionFlags flags = streamCtl.mWC_Service.getSessionFlags();
            if (flags == WC_SessionFlags.Audio)
            {
                nativeSetVideoCaptureDevice("none");
                Log.i(TAG, "Streamout: Setting Video capture device to none");
            }
            else
            {
                nativeSetVideoCaptureDevice(streamCtl.userSettings.getWcVideoCaptureDevice());
            }
            if (flags == WC_SessionFlags.Video)
            {
                nativeSetAudioCaptureDevice("none");
                Log.i(TAG, "Streamout: Setting Audio capture device to none");
            }
            else
            {
                nativeSetAudioCaptureDevice(streamCtl.userSettings.getWcAudioCaptureDevice());
            }
            if (!streamCtl.userSettings.getWcAudioCaptureDevice().equalsIgnoreCase("aes"))
            {            
                //send the HDMI input resolution to CPP layer in order to confgure V4l2src controller.
                if( streamCtl.hdmiInput != null )
                {
                    Log.i(TAG, "Streamout: JAVA -  setHDMICameraResolution entered "+streamCtl.hdmiInput.getHorizontalRes()+"   "+streamCtl.hdmiInput.getVerticalRes()  );
                    setHDMIInResolution(Integer.parseInt(streamCtl.hdmiInput.getHorizontalRes()), Integer.parseInt(streamCtl.hdmiInput.getVerticalRes()));
                }
            }
        }
        setAppCacheFolder();
        setHostName();
        setDomainName();
        if (!streamCtl.userSettings.getWcAudioCaptureDevice().equalsIgnoreCase("aes"))
        {
            // WC mode as before change for aes
            setServerIpAddress();
            setPort(8554);
            setMulticastEnable(false);
            setCamStreamName("wc");
            setWcSecurityEnable(streamCtl.userSettings.getWcSecurityEnable());
            if (streamCtl.isAM3K && streamCtl.userSettings.getWcSecurityEnable())
                generateRtspServerCertificates();
            setWcRandomUserPwEnable(streamCtl.userSettings.getWcRandomUserPwEnable());
            setWirelessConferencingResolution(10);
            setFramerate(15);
            setBitrate(4000000);
            setIFrameInterval(1);        
            setQuality(streamCtl.userSettings.getAirMediaWCQuality());
        } else {
            // aes67 mode
            setCamStreamMulticastAddress(streamCtl.userSettings.getCamStreamMulticastAddress());
            setPort(streamCtl.userSettings.getCamStreamPort());        
            setMulticastEnable(streamCtl.userSettings.getCamStreamMulticastEnable());        
            setServerIpAddress();
            setCamStreamName("aes");
            setWcSecurityEnable(false);
        }
    }

    public void generateRtspServerCertificatesInCss()
    {
        String scriptFolder = streamCtl.getFilesDir().getAbsolutePath();
        String command = scriptFolder+"/genRtspCerts.sh";
        Process p=null;
        Log.d(TAG,"running script to generate server certificates");
        try {
            Log.d(TAG,"script path="+command);
            ProcessBuilder builder = new ProcessBuilder(command);
            final Process process = builder.start();
            final Thread ioThread = new Thread() {
                @Override
                public void run() {
                    try {
                        Log.d(TAG,"launching reader process for output of script");
                        final BufferedReader reader = new BufferedReader(
                                new InputStreamReader(process.getInputStream()));
                        String line = null;
                        while ((line = reader.readLine()) != null) {
                            Log.i(TAG, "genRtspCerts(): "+line);
                        }
                        Log.d(TAG,"closing reader");
                        reader.close();
                    } catch (final Exception e) {
                        e.printStackTrace();
                    }
                }
            };
            Log.d(TAG,"starting ioThread");
            ioThread.start();

            int retCode = process.waitFor();
            Log.d(TAG, "script return code="+retCode);
        } catch (IOException e) {
            Log.i(TAG, "IOException");
            e.printStackTrace();
        } catch (InterruptedException e) {
            Log.i(TAG, "InterruptedException");
            e.printStackTrace();
        } finally {
            if(p!=null) p.destroy();
        }
        Log.d(TAG,"finished running script to generate server certificates");
    }
    
    private void wcCertificateGenerationComplete(boolean success)
    {
        Log.i(TAG,"server certificate generation completed result="+success);
        if (wcCertificateGenerationCompletedLatch != null) {
            wcCertificateGenerationCompletedLatch.countDown();
        }
    }
    
    @SuppressWarnings("deprecation")
    private void monitorWcCertificateGenerationCompletion()
    {
        final String generationStatusPath = "/dev/shm/crestron/CresStreamSvc/certGenerationStatus.txt";

        streamCtl.checkFileExistsElseCreate(generationStatusPath);
        Log.i(TAG, "Monitor CLOSE_WRITE events on "+generationStatusPath+" file for certificate genrate completion");
        // Monitor certificate completion events by monitoring CLOSE_WRITE events on file
        wcCertificateCompletionObserver = new FileObserver(generationStatusPath, FileObserver.CLOSE_WRITE) 
        {                     
            @Override
            public void onEvent(int event, String path) 
            {
                synchronized (wcCertificateCompletionObserverLock) 
                {
                    String result = MiscUtils.readStringFromDisk(generationStatusPath);
                    Log.i(TAG, "certificate generation status = "+result);
                    wcCertificateGenerationComplete(result.equalsIgnoreCase("success"));
                }
            }
        };
        wcCertificateCompletionObserver.startWatching();
    }
    
    public void generateRtspServerCertificates()
    {
        Log.i(TAG,"asking csio to run script to generate server certificates");
        wcCertificateGenerationCompletedLatch = new CountDownLatch(1);
        streamCtl.sockTask.SendDataToAllClients("GenerateRtspServerCerts=TRUE");
        Log.i(TAG,"sent command to csio");
        try {
           wcCertificateGenerationCompletedLatch.await(10000, TimeUnit.MILLISECONDS);
        } catch (Exception e) { 
            e.printStackTrace(); 
        } finally {
            wcCertificateGenerationCompletedLatch = null;
        }
        Log.i(TAG,"finished running script to generate server certificates");
    }
    
    public List<String> getWcServerUrlList()
    {
        List<String> list = new ArrayList<String>(Arrays.asList(wcServerUrl.split("\\s*,\\s*")));
        //Log.v(TAG, "getWcServerUrlList(): urlList="+list);
        return list;
    }
    
    public void setWcServerUrl(String url)
    {
        //Log.v(TAG, "Streamout: setWcServerUrl: incoming url="+url);
        StringBuilder newUrl = new StringBuilder("");
        if (url.contains("0.0.0.0"))
        {
            Set<String> adapters = streamCtl.userSettings.getAirMediaAdapters();
            if (adapters.contains("eth0") && streamCtl.isValidIpAddress(streamCtl.userSettings.getDeviceIp()))
            {
                newUrl.append(url.replace("0.0.0.0", streamCtl.userSettings.getDeviceIp()));
            }
            if (adapters.contains("wlan0") && streamCtl.isValidIpAddress(streamCtl.userSettings.getWifiIp()))
            {
                if (newUrl.length() != 0)
                    newUrl.append(",");
                newUrl.append(url.replace("0.0.0.0", streamCtl.userSettings.getWifiIp()));
            }
            if (adapters.contains("eth1") && streamCtl.isValidIpAddress(streamCtl.userSettings.getAuxiliaryIp()))
            {
                if (newUrl.length() != 0)
                    newUrl.append(",");
                newUrl.append(url.replace("0.0.0.0", streamCtl.userSettings.getAuxiliaryIp()));
            }
        }
        wcServerUrl = newUrl.toString();
        if (wcServerUrl != null)
        {
            String[] urls = wcServerUrl.split(",");
            MiscUtils.writeStringToDisk(WC_URL_PATH, urls[0]);
        }
        //Log.v(TAG, "setWcServerUrl: WC server url="+wcServerUrl);
    }

    //This set error call can be used to indicate to Receiver APK the type of issue occured in WC errModule, errorCode, errorMessage
    public void setWcServerError(int errModule, int errCode, String errMessage)
    {
        //Log.i(TAG, "Streamout: setWcServerError module: "+errModule+" code: "+errCode);
        //TODO: Based on the type of error code received take session related actions here as well.
        if(streamCtl.mWC_Service != null)
        {
            if(errCode == (-1))
            {
                Log.i(TAG,"setWcServerError --> closeSession");
                streamCtl.mWC_Service.closeSession();
                Log.e(TAG, "setWcServerError: pipeline failed, close session needed FATAL !!!" );
            }

            //Inform AM Receiver App of the error that has occured
            streamCtl.mWC_Service.onError(errModule, errCode, errMessage);
        }
        else
            Log.e(TAG, "setWcServerError mWC_Service is NULL" );
    }

    public void onServerStart()
    {
        Log.i(TAG, "Streamout: onServerStart");
        streamCtl.mWC_Service.onServerStart();
    }
    
    public void onServerStop()
    {
        Log.i(TAG, "Streamout: onServerStop");
        streamCtl.mWC_Service.onServerStop();
    }
    
    public void onClientConnected(String clientIp)
    {
        Log.i(TAG, "Streamout: onClientConnected: clientIp="+clientIp);
        streamCtl.mWC_Service.onClientConnected(clientIp);
    }
    
    public void onClientDisconnected(String clientIp)
    {
        Log.i(TAG, "Streamout: onClientDisconnected: clientIp="+clientIp);
        streamCtl.mWC_Service.onClientDisconnected(clientIp);
    }
    
    public void resetHdmiInput()
    {
        Log.i(TAG, "Streamout: resetHdmiInput");
        streamCtl.sockTask.SendDataToAllClients("RESET_HDMI_INPUT=true");
    }
    
    public void setAppCacheFolder()
    {
        nativeSetAppCacheFolder(appCacheFolder);
    }
    
    public void setHostName()
    {
        nativeSetHostName(streamCtl.getHostName());
    }
    
    public void setDomainName()
    {
        nativeSetDomainName(streamCtl.getDomainName());
    }
    
    public void setServerIpAddress()
    {
        nativeSetServerIpAddress(streamCtl.userSettings.getDeviceIp());
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
    

    public void setWirelessConferencingResolution(int resolution) {
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

    public void setWcSecurityEnable(boolean enable) {
        nativeSet_WcSecurityEnable(enable, sessionId);
    }
    
    public void setWcRandomUserPwEnable(boolean enable) {
        nativeSet_WcRandomUserPwEnable(enable, sessionId);
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

    public void setQuality(int quality) {
        nativeSet_Quality(quality, sessionId);
    }

    public void setHDMIInResolution(int xRes, int yRes) {
        nativeSet_HDMIInResolution_x(xRes, sessionId);
        nativeSet_HDMIInResolution_y(yRes, sessionId);
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
        setWcSecurityEnable(false);
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
    
        streamCtl.sockTask.SendDataToAllClients(MiscUtils.stringFormat("CAMERA_STREAMING_STREAM_URL=%s", camUrl));
    }
    
    public void updateCamSnapshotUrl()
    {
        String snapshotUrl = buildCamSnapshotUrl();
        
        streamCtl.userSettings.setCamStreamSnapshotUrl(snapshotUrl);
    
        streamCtl.sockTask.SendDataToAllClients(MiscUtils.stringFormat("CAMERA_STREAMING_SNAPSHOT_URL=%s", snapshotUrl));
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
        
    public void recoverWCStreamOut()
    {
		Log.i(TAG, "Wireless Conferencing recovery.");
		streamCtl.setWirelessConferencingStreamEnable(false);
		streamCtl.setWirelessConferencingStreamEnable(true);
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
