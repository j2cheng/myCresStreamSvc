package com.crestron.txrxservice;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import android.util.Log;

import android.hardware.Camera;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.view.Surface;
import com.droidlogic.app.DisplaySettingManager;

public class ProductSpecific
{	
    static String TAG = "X60 ProductSpecific";

	// ******************* CameraStreaming.java *******************
	public static void setEncoderFps(Camera camera, int encoderFps, int hdmiInFps)
	{
		// Not implmented for this product
	}
	
	public static void setStreamTransportMode(MediaRecorder mrec, int streamTransportMode)
	{
		// Not implmented for this product
	}
	
	public static void setRTSPPort(MediaRecorder mrec, int port)
	{
		// Not implmented for this product
	}
	
	public static void setMcastIP(MediaRecorder mrec, String multicastAddress)
	{
		// Not implmented for this product
	}
	
	public static void setRTPAudioPort(MediaRecorder mrec, int port)
	{
		// Not implmented for this product
	}
	
	public static void setRTPVideoPort(MediaRecorder mrec, int port)
	{
		// Not implmented for this product
	}
	
	public static void setMPEG2TSPort(MediaRecorder mrec, int port)
	{
		// Not implmented for this product
	}
	
	public static void setDestinationIP(MediaRecorder mrec, String streamIp)
	{
		// Not implmented for this product
	}
	
	public static void setEncoderProfile(MediaRecorder mrec, int encoderVideoProfile)
	{
		// Not implmented for this product
	}
	
	public static void setVideoEncoderLevel(MediaRecorder mrec, int encodingLevel)
	{
		// Not implmented for this product
	}
	
	public static String getSDP(MediaRecorder mrec)
	{
		// Not implmented for this product
		return "";
	}
	
	public static String getStatisticsData(MediaRecorder mrec)
	{
		// Not implmented for this product
		return "";
	}
	
	// ******************* HDMIInputInterface.java *******************
	public static byte[] getEVSHdmiInSyncStatus()
	{
		// Not implmented for this product
		return new byte[] { 0x0 };
	}
	
	// ******************* HDMIOutputInterface.java *******************
	public static byte[] getEVSHdmiOutSyncStatus()
	{
		// Not implmented for this product
		return new byte[] { 0x0 };
	}
	
	// ******************* CresCamera.java *******************
	public static void getHdmiInputStatus(Camera camera, int hdmiInResolutionEnum)
	{
		// Not implmented for this product
	}
	
	// ******************* NstreamIn.java *******************
	public static void setTransportCommunication(MediaPlayer mediaPlayer, boolean enable)
	{
		// Not implmented for this product
	}
	
	public static void setDejitterBufferDuration(MediaPlayer mediaPlayer, int streamingBuffer) throws java.io.IOException
	{
		// Not implmented for this product
	}
	
	public static void setSDP(MediaPlayer mediaPlayer, String sdp) throws java.io.IOException
	{
		// Not implmented for this product
	}

    public static void doChromakey()
    {
		DisplaySettingManager.setDisplayDiscardColorEnable(0);				
		File file = new File("/dev/crestron/gstreamerChromaKey");		
		try
		{
			int value;
			int red;
			int green;
			int blue;
			BufferedReader br = new BufferedReader(new FileReader(file));
			String line = br.readLine();
			if(line != null)
			{
				value = Integer.decode(line);
				red = (value >> 16) & 0xff;
				green = (value >> 8) & 0xff;
				blue = value & 0xff;
				Log.e(TAG,"X60 Setting chromakey color to " + red + "," + green + "," + blue);
				DisplaySettingManager.setDisplayDiscardColor(red, green, blue);
				DisplaySettingManager.setDisplayDiscardColorEnable(1);				
			}
			br.close();
		}
		catch(IOException e)
		{			
		}
	}

	// ******************* Classes *******************
	public class DispayInfo
	{
		public int width;
        public int height;
        public float refreshRate;
        public float density;
        public float xDpi;
        public float yDpi;
        public boolean secure;
                
        public DispayInfo()
        {
			// Not implmented for this product
        	width = 0;
	        height = 0;
	        refreshRate = 0.0f;
	        density = 0.0f;
	        xDpi = 0.0f;
	        yDpi= 0.0f;
	        secure = false;	        	
        }
	}
}
