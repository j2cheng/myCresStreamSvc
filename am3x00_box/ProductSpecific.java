package com.crestron.txrxservice;

import com.gs.core.peripheral.PeripheralManager;
import com.gs.core.peripheral.StatusChangeListener;

import android.content.Context;
import android.content.Intent;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Build;
import android.util.Log;
import android.view.Surface;

import java.util.ArrayList;
import java.util.List;

public class ProductSpecific
{
    private static ProductSpecific mInstance;
    private Context context;
    private static boolean oneShot = false;
    CresCamera2 cam_handle;

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

    public Context getApplicationContext()
    {
        return context;
    }

    static String TAG = "AM3X00 ProductSpecific";

    // ******************* LaunchApp.java *******************
    public static void startForegroundService(Context ctx, Intent intent)
    {
        if (Build.VERSION.SDK_INT >= 27 /*Build.VERSION_CODES.O*/) {
            ctx.startForegroundService(intent);
        }
        else {
            ctx.startService(intent);
        }
    }

    // ******************* CameraStreaming.java *******************
    public static void setEncoderFps(Camera camera, int encoderFps, int hdmiInFps)
    {
        // Not implmented for vanilla
    }

    public static void setStreamTransportMode(MediaRecorder mrec, int streamTransportMode)
    {
        // Not implmented for vanilla
    }

    public static void setRTSPPort(MediaRecorder mrec, int port)
    {
        // Not implmented for vanilla
    }

    public static void setMcastIP(MediaRecorder mrec, String multicastAddress)
    {
        // Not implmented for vanilla
    }

    public static void setRtspSessionName(MediaRecorder mrec, String sessionName)
    {
        // Not implmented for vanilla
    }

    public static void setRtspSessionUserName(MediaRecorder mrec, String userName)
    {
        // Not implmented for vanilla
    }

    public static void setRtspSessionPassword(MediaRecorder mrec, String password)
    {
        // Not implmented for vanilla
    }

    public static void setRtspAuthentication(MediaRecorder mrec, int authMode)
    {
        // Not implmented for vanilla
    }

    public static void setHdcpEncrypt(MediaRecorder mrec, int hdcpEncrypt)
    {
        // Not implmented for vanilla
    }

    public static void setRTPAudioPort(MediaRecorder mrec, int port)
    {
        // Not implmented for vanilla
    }

    public static void setRTPVideoPort(MediaRecorder mrec, int port)
    {
        // Not implmented for vanilla
    }

    public static void setMPEG2TSPort(MediaRecorder mrec, int port)
    {
        // Not implmented for vanilla
    }

    public static void setDestinationIP(MediaRecorder mrec, String streamIp)
    {
        // Not implmented for vanilla
    }

    public static void setEncoderProfile(MediaRecorder mrec, int encoderVideoProfile)
    {
        // Not implmented for vanilla
    }

    public static void setVideoEncoderLevel(MediaRecorder mrec, int encodingLevel)
    {
        // Not implmented for vanilla
    }

    public static String getSDP(MediaRecorder mrec)
    {
        // Not implmented for vanilla
        return "";
    }

    public static String getStatisticsData(MediaRecorder mrec)
    {
        // Not implmented for vanilla
        return "";
    }

    // ******************* CameraPreview.java *******************
    public static int getRGB888PixelFormat()
    {
        // RGB 888 not supported on vanilla
        return ImageFormat.NV21;
    }

    public static void setPreviewSurface(Camera camera, Surface surface)
    {
        // Not implemented for vanilla
    }

    // ******************* HDMIInputInterface.java *******************
    public static byte[] getEVSHdmiInSyncStatus()
    {
        // Not implmented for vanilla
        return new byte[] { 0x0 };
    }

    public static boolean hasRealCamera()
    {
        return false;
    }

    public static boolean isHdmiDriverPresent()
    {
        return false;
    }

    public static void handleHpdHdmiEvent(HDMIInputInterface hdmiIf)
    {
        // no hdmi input on vanilla.
    }

    // ******************* HDMIOutputInterface.java *******************
    public static byte[] getEVSHdmiOutSyncStatus()
    {
        // Not implmented for vanilla
        return new byte[] { 0x0 };
    }

    // ******************* CresCamera.java *******************
    public static void getHdmiInputStatus(CresStreamCtrl cresStreamCtrl)
    {
        // No HDMI input on vanilla.
    }

    public void initCamera()
    {
        cam_handle = new CresCamera2();
        cam_handle.mCameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
    }

    // ******************* NstreamIn.java *******************
    public static void setTransportCommunication(MediaPlayer mediaPlayer, boolean enable)
    {
        // Not implmented for vanilla
    }

    public static void setDejitterBufferDuration(MediaPlayer mediaPlayer, int streamingBuffer) throws java.io.IOException
    {
        // Not implmented for vanilla
    }

    public static void setSDP(MediaPlayer mediaPlayer, String sdp) throws java.io.IOException
    {
        // Not implmented for vanilla
    }

    public static void doChromakey(boolean enable)
    {
        // Not implemented for vanilla
    }

    public static void setRGB888Mode(boolean enable)
    {
        // Not implemented for vanilla
    }

    public boolean hasUVCCamera() 
    {
    	CameraManager mCameraManager=null;
    	mCameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
		final String UVC_ID = "/dev/video5";
		try {
			String[] cameraIds = mCameraManager.getCameraIdList();
			for (String id:cameraIds) {
				if (UVC_ID.equals(id)) {
					Log.v(TAG, "UVC camera is connected");
					return true;
				}
			}
		} catch (CameraAccessException e) {
			e.printStackTrace();
		}
		return false;
    }
    
    public class PeripheralStatusChangeListener extends StatusChangeListener 
    {
    	boolean cameraConnected;
    	CresStreamCtrl cresStreamCtrl;
    	
    	public PeripheralStatusChangeListener(CresStreamCtrl ctrl) {
    		super();
    		cresStreamCtrl = ctrl;
    		cameraConnected = hasUVCCamera();
    	}
    	
    	public void UsbConnect()
    	{
    		if (hasUVCCamera() && !cameraConnected)
    		{
    			Log.i(TAG, "UsbConnect(): USB camera is now connected");
    			cameraConnected = true;
    			cresStreamCtrl.onCameraConnected();
    		}
    	}
    	
    	public void UsbDisconnect()
    	{
    		if (!hasUVCCamera() && cameraConnected)
    		{
    			Log.i(TAG, "UsbDisconnect(): USB camera is now disconnected");
        		cameraConnected = false;
    			cresStreamCtrl.onCameraDisconnected();
    		}
    	}
    	
    	@Override
    	public void onChanged(int i, String s, int i1) 
    	{
    		switch (i) {
    		case PeripheralManager.PER_HDMI_IN:
    			Log.i(TAG, "HDMI IN status: " + (i1 == 1 ? "Connected" : "Disconnected"));
    			break;
    		case PeripheralManager.PER_USB_30:
    			Log.i(TAG, "USB 3.0 status: " + (i1 == 1 ? "Connected" : "Disconnected"));
    			if (i1 == 1)
    			{
        			UsbConnect();
    			} else {
    				UsbDisconnect();
    			}
    			break;
    		}
    	}
    }
    
    public void monitorUVCCamera(CresStreamCtrl cresStreamCtrl) 
    {
    	final CresStreamCtrl ctrl = cresStreamCtrl;
        new Thread(new Runnable() {
            public void run() {
            	PeripheralStatusChangeListener mListener = new PeripheralStatusChangeListener(ctrl);
                PeripheralManager.instance().addStatusListener(PeripheralManager.PER_HDMI_IN, mListener, null);
                PeripheralManager.instance().addStatusListener(PeripheralManager.PER_USB_30, mListener, null);
            }
        }).start();
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
            // Not implmented for vanilla
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
