package com.crestron.txrxservice;

import java.io.File;

import android.content.Context;
import android.content.Intent;
import android.view.Surface;
import android.view.Surface.PhysicalDisplayInfo;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.hardware.display.DisplayManager;
import android.media.MediaPlayer;
import android.util.Log;
import android.content.Context;

public class ProductSpecific
{
    static String TAG = "OMAP5 ProductSpecific";
    private static ProductSpecific mInstance;
    private Context context;
    CresCamera cam_handle;

	// ******************* LaunchApp.java *******************
	public static void startForegroundService(Context ctx, Intent intent)
	{
		ctx.startService(intent);
	}

	// ******************* CameraStreaming.java *******************
    public static ProductSpecific getInstance()
    {
        if (mInstance == null) 
        {
            mInstance = getSync();
        }
        
        return mInstance;
    }

    private static synchronized ProductSpecific getSync()
    {
        if (mInstance == null) 
        {
            mInstance = new ProductSpecific();
        }
        
        return mInstance;
    }

    public void initialize(Context context) 
    {
        this.context = context;
    }
    
	public static void setEncoderFps(Camera camera, int encoderFps, int hdmiInFps)
	{
		camera.setEncoderFps(encoderFps, hdmiInFps);
	}
	
	public static void setStreamTransportMode(MediaRecorder mrec, int streamTransportMode)
	{
		mrec.setStreamTransportMode(streamTransportMode);
	}
	
	public static void setRTSPPort(MediaRecorder mrec, int port)
	{
		mrec.setRTSPPort(port);
	}
	
	public static void setMcastIP(MediaRecorder mrec, String multicastAddress)
	{
		mrec.setMcastIP(multicastAddress);
	}
	
	public static void setRtspSessionName(MediaRecorder mrec, String sessionName)
	{
		mrec.setRtspSessionName(sessionName);
	}
	
	public static void setRtspSessionUserName(MediaRecorder mrec, String userName)
	{
		mrec.setRtspSessionUserName(userName);
	}

	public static void setRtspSessionPassword(MediaRecorder mrec, String password)
	{
		mrec.setRtspSessionPassword(password);
	}
	
	public static void setRtspAuthentication(MediaRecorder mrec, int authMode)
	{
		mrec.setRtspAuthentication(authMode);
	}
	
	public static void setHdcpEncrypt(MediaRecorder mrec, int hdcpEncrypt)
	{
		mrec.setHdcpEncrypt(hdcpEncrypt);
	}
	
	public static void setRTPAudioPort(MediaRecorder mrec, int port)
	{
		mrec.setRTPAudioPort(port);
	}
	
	public static void setRTPVideoPort(MediaRecorder mrec, int port)
	{
		mrec.setRTPVideoPort(port);
	}
	
	public static void setMPEG2TSPort(MediaRecorder mrec, int port)
	{
		mrec.setMPEG2TSPort(port);
	}
	
	public static void setDestinationIP(MediaRecorder mrec, String streamIp)
	{
		mrec.setDestinationIP(streamIp);
	}
	
	public static void setEncoderProfile(MediaRecorder mrec, int encoderVideoProfile)
	{
		mrec.setEncoderProfile(encoderVideoProfile);
	}
	
	public static void setVideoEncoderLevel(MediaRecorder mrec, int encodingLevel)
	{
		mrec.setVideoEncoderLevel(encodingLevel);
	}
	
	public static String getSDP(MediaRecorder mrec)
	{
		return mrec.getSDP();
	}
	
	public static String getStatisticsData(MediaRecorder mrec)
	{
		return mrec.getStatisticsData();
	}
	
	// ******************* CameraPreview.java *******************
	public static int getRGB888PixelFormat()
	{
		return ImageFormat.RGBA_8888;
	}

	public static void setPreviewSurface(Camera camera, Surface surface) throws java.io.IOException
	{
		camera.setPreviewSurface(surface);
	}
	
	// ******************* HDMIInputInterface.java *******************
	public static byte[] getEVSHdmiInSyncStatus()
	{
		return DisplayManager.getEVSHdmiInSyncStatus();
	}
	
	public static boolean hasRealCamera()
	{
		return false;
	}
	
    public static boolean isHdmiDriverPresent()
    {
        File file = new File("/sys/devices/platform/omap_i2c.2/i2c-2/2-000f/sync_state");

        return file.exists();
    }

    public static void handleHpdHdmiEvent(HDMIInputInterface hdmiIf)
    {
		hdmiIf.updateResolutionInfo();
	}
	
	// ******************* HDMIOutputInterface.java *******************
	public static byte[] getEVSHdmiOutSyncStatus()
	{
		return DisplayManager.getEVSHdmiOutSyncStatus();
	}
	
	// ******************* CresCamera.java *******************
    public void initCamera()
    {
        cam_handle = new CresCamera();
    }

	public void getHdmiInputStatus(CresStreamCtrl cresStreamCtrl)
	{
		Camera camera = null;

        if(cam_handle != null)
            camera = cam_handle.getCamera();
		int hdmiInResolutionEnum = HDMIInputInterface.getResolutionEnum();
		// Yes, someone actually hacked the android camera source to put hdmi into it!
		if(camera != null){
			camera.getHdmiInputStatus(hdmiInResolutionEnum);
		}
	}
	
	// ******************* NstreamIn.java *******************
	public static void setTransportCommunication(MediaPlayer mediaPlayer, boolean enable)
	{
		mediaPlayer.setTransportCommunication(enable);
	}
	
	public static void setDejitterBufferDuration(MediaPlayer mediaPlayer, int streamingBuffer) throws java.io.IOException
	{
		mediaPlayer.setDejitterBufferDuration(streamingBuffer);
	}
	
	public static void setSDP(MediaPlayer mediaPlayer, String sdp) throws java.io.IOException
	{
		mediaPlayer.setSDP(sdp);
	}

	// ******************* CresStreamCtrl.java *******************
	public static boolean getDisplayInfo (PhysicalDisplayInfo outInfo)
	{
       return Surface.getDisplayInfo(Surface.getBuiltInDisplay(Surface.BUILT_IN_DISPLAY_ID_MAIN), outInfo);
	}
	
    public static void doChromakey(boolean enable)
    {
		// no java code controlling chromakey in this product
	}
    
    public static void setRGB888Mode(boolean enable)
    {
    	String setString = enable ? "1" : "0";
    	MiscUtils.writeStringToDisk("/dev/shm/crestron/CresStreamSvc/previewEnableRGB888", setString);	// For ittiam's changes
		MiscUtils.writeStringToDisk("/sys/devices/platform/omap_i2c.2/i2c-2/2-000f/evs_prev_rgb888_en", setString);	// For ittiam's changes
    }
    
    public static void Surface_forceScopedDisconnect(Surface surface) 
    {
        // Not implemented for this product
    }
    
    public boolean hasUVCCamera() 
    {
    	// Not implemented for this product
    	return false;
    }
    
    public void startPeripheralListener(CresStreamCtrl cresStreamCtrl) 
    {
    	// Not implemented for this product
    }

    public void performSoftUsbReset()
    {
        // Not implemented for this product
    }
    
	public class DispayInfo
	{
		public int width;
        public int height;
        public float refreshRate;
        public float density;
        public float xDpi;
        public float yDpi;
        public boolean secure;
        
        private PhysicalDisplayInfo displayInfo;
        
        public DispayInfo()
        {
        	displayInfo = new PhysicalDisplayInfo();
        	getDisplayInfo(displayInfo);
        	
        	width = displayInfo.width;
	        height = displayInfo.height;
	        refreshRate = displayInfo.refreshRate;
	        density = displayInfo.density;
	        xDpi = displayInfo.xDpi;
	        yDpi= displayInfo.yDpi;
	        secure = displayInfo.secure;	        	
        }
	}
}
