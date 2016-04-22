package com.crestron.txrxservice;

import android.view.Surface;
import android.view.Surface.PhysicalDisplayInfo;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.hardware.display.DisplayManager;
import android.media.MediaPlayer;

public class ProductSpecific
{
	// ******************* CameraStreaming.java *******************
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
	
	// ******************* HDMIInputInterface.java *******************
	public static byte[] getEVSHdmiInSyncStatus()
	{
		return DisplayManager.getEVSHdmiInSyncStatus();
	}
	
	// ******************* HDMIOutputInterface.java *******************
	public static byte[] getEVSHdmiOutSyncStatus()
	{
		return DisplayManager.getEVSHdmiOutSyncStatus();
	}
	
	// ******************* CresCamera.java *******************
	public static void getHdmiInputStatus(Camera camera, int hdmiInResolutionEnum)
	{
		camera.getHdmiInputStatus(hdmiInResolutionEnum);
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
