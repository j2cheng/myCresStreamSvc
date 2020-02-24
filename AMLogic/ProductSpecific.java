package com.crestron.txrxservice;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.view.Surface;
import com.droidlogic.app.DisplaySettingManager;
import android.content.Context;

public class ProductSpecific
{	
    static String TAG = "X60 ProductSpecific";
    private static ProductSpecific mInstance;
    private Context context;

	/// ******************* LaunchApp.java *******************
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
	
	public static void setRtspSessionName(MediaRecorder mrec, String sessionName)
	{
		// Not implmented for this product
	}
	
	public static void setRtspSessionUserName(MediaRecorder mrec, String userName)
	{
		// Not implmented for this product
	}

	public static void setRtspSessionPassword(MediaRecorder mrec, String password)
	{
		// Not implmented for this product
	}
	
	public static void setRtspAuthentication(MediaRecorder mrec, int authMode)
	{
		// Not implmented for this product
	}
	
	public static void setHdcpEncrypt(MediaRecorder mrec, int hdcpEncrypt)
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
	
	// ******************* CameraPreview.java *******************
	public static int getRGB888PixelFormat()
	{
		// RGB 888 not supported on this product
		return ImageFormat.NV21;
	}

	public static void setPreviewSurface(Camera camera, Surface surface)
	{
		camera.setPreviewSurface(surface);
	}
	
	// ******************* HDMIInputInterface.java *******************
	public static byte[] getEVSHdmiInSyncStatus()
	{
		// Not implmented for this product
		return new byte[] { 0x0 };
	}
	
	public static boolean hasRealCamera()
	{
		return true;
	}
	
    public static boolean isHdmiDriverPresent()
    {
		return false;
	}
		
    public static void handleHpdHdmiEvent(HDMIInputInterface hdmiIf)
    {
		// no hdmi input on this product.
	}
	
	// ******************* HDMIOutputInterface.java *******************
	public static byte[] getEVSHdmiOutSyncStatus()
	{
		// Not implmented for this product
		return new byte[] { 0x0 };
	}
	
	// ******************* CresCamera.java *******************
	public static void getHdmiInputStatus()
	{
		// No HDMI input on this product.
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

    public static void doChromakey(boolean enable)
    {
		DisplaySettingManager.setDisplayDiscardColorEnable(0);				
		if(!enable)
		{
			return;
		}
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
    
    public static void setRGB888Mode(boolean enable)
    {
    	// Not implemented for this product
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
