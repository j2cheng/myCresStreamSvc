package com.crestron.txrxservice;

import com.crestron.txrxservice.CresStreamCtrl;
import com.crestron.txrxservice.UsbAvDevice;
import com.gs.core.peripheral.PeripheralManager;
import com.gs.core.peripheral.StatusChangeListener;

import android.content.Context;
import android.content.Intent;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Build;
import android.util.Log;
import android.view.Surface;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.io.File;
import java.lang.reflect.Method;

public class ProductSpecific
{
    private static ProductSpecific mInstance;
    private Context context;
    private static boolean oneShot = false;
    CresCamera2 cam_handle;
    PeripheralStatusChangeListener mListener = null;

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
    private static final String HDMI_IN_DEV = "/dev/video0";

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
        File file = new File("/sys/devices/platform/ff3e0000.i2c/i2c-8/8-000f/sync_status");

        return file.exists();
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
    
    public List<UsbAvDevice> usbDeviceList = new ArrayList<UsbAvDevice> ();
    
    public UsbAvDevice findUsbDevice(int id)
    {
    	for (UsbAvDevice d : usbDeviceList)
    	{
    		if (d.perId == id)
    			return d;
    	}
    	return null;
    }
    
    public class PeripheralStatusChangeListener extends StatusChangeListener 
    {
        boolean hdmiInConnected = false;
        CresStreamCtrl cresStreamCtrl;

        public PeripheralStatusChangeListener(CresStreamCtrl ctrl) {
            super();
            cresStreamCtrl = ctrl;
            hdmiInConnected = cam_handle.findCamera(HDMI_IN_DEV);
        }

        public void HdmiInConnect()
        {
            if (!hdmiInConnected)
            {
                int tries = 0;
                while (!cam_handle.findCamera(HDMI_IN_DEV))
                {
                    try {
                        Thread.sleep(100);
                    } catch (Exception e) { e.printStackTrace(); }
                    if (++tries == 20)
                    {
                        Log.e(TAG, "No HDMI camera seen even after waiting for 2 seconds after connect");
                        break;
                    }
                }
                if (tries < 20) {
                    hdmiInConnected = true;
                    cresStreamCtrl.onHdmiInConnected();
                }
            }
        }

        public void HdmiInDisconnect()
        {
            if (hdmiInConnected)
            {
                int tries = 0;
                while (cam_handle.findCamera(HDMI_IN_DEV))
                {
                    try {
                        Thread.sleep(100);
                    } catch (Exception e) { e.printStackTrace(); }
                    if (++tries == 20)
                    {
                        Log.e(TAG, "HDMI camera seen even after waiting for 2 seconds after disconnect");
                        break;
                    }
                }
                if (tries < 20) {
                    hdmiInConnected = false;
                    cresStreamCtrl.onHdmiInDisconnected();
                }
            }
        }

        public String getVideoCaptureFile(List<String> videoList)
        {
        	for (String s : videoList) { 
        		// Assumes only one video capture device exists and has one of these patterns
        		if (s.equals("video5") || s.equals("video7"))
        			return "/dev/"+s;
        	}
        	return null;
        }
        
        public String getAudioCaptureFile(List<String> audioList)
        {
        	for (String s : audioList) { 
        		// Assumes only one audio capture device exists and has one of these patterns
        		if (s.equals("snd/pcmC5D0c") || s.equals("snd/pcmC6D0c"))
        			return "/dev/"+s;
        	}
        	return null;
        }
        
        public void onUsbStatusChanged(int usbId, int status, String name, List<String> videoList, List<String> audioList)
        {
        	boolean change = false;
        	if (status > 0)
        	{
        		// device was added on port=usbId
        		UsbAvDevice d = findUsbDevice(usbId);
        		if (d != null) {
        			usbDeviceList.remove(d);
        		}
        		String vFile = getVideoCaptureFile(videoList);
        		String aFile = getAudioCaptureFile(audioList);
        		d = new UsbAvDevice(usbId, ((usbId==PeripheralManager.PER_USB_30)?"usb3":"usb2"), name, vFile, aFile);
        		Log.i(TAG, "UsbAudioVideoDeviceAdded(): new USB device "+d.deviceName+" added on "+d.usbPortType);
        		usbDeviceList.add(d);
        	} else {
        		// device was removed on port=usbId
        		UsbAvDevice d = findUsbDevice(usbId);
        		if (d != null) {
                    Log.i(TAG, "UsbAudioVideoDeviceRemoved(): USB device "+d.deviceName+" removed from "+d.usbPortType);
        			usbDeviceList.remove(d);
        		}
        		usbDeviceList.remove(d);
        	}
            cresStreamCtrl.onUsbStatusChanged(usbDeviceList);
        }
        
        public void usbEvent(int usbId)
        {
        	String name = PeripheralManager.instance().getUsbPeripheralName(usbId);
        	int status = PeripheralManager.instance().getStatus(usbId);
            Class pMgrClass = PeripheralManager.instance().getClass();
            List<String> videoList = null;
            List<String> audioList = null;
            try {
            	Method m = pMgrClass.getMethod("getUsbVideoDevices", int.class);
            	videoList = PeripheralManager.instance().getUsbVideoDevices(usbId);
            } catch (Exception e) {
            	e.printStackTrace();
            }
            try {
            	Method m = pMgrClass.getMethod("getUsbAudioDevices", int.class);
            	audioList = PeripheralManager.instance().getUsbAudioDevices(usbId);
            } catch (Exception e) {
            	e.printStackTrace();
            }
            if (status > 0)
            {
            	Log.i(TAG, "USB id="+usbId+"  Device="+name);
            	Log.i(TAG, "\tVideo Devices="+videoList);
            	Log.i(TAG, "\tAudio Devices="+audioList);
            } else {
            	Log.i(TAG, "USB id="+usbId+"  No devices connected");
            }
    		onUsbStatusChanged(usbId, status, name, videoList, audioList);
        }
        
        @Override
        public void onChanged(int perId, String desc, int status)
        {
        	if (desc != null) {
        		desc = desc.replaceAll("\r", "").replaceAll("\n", "");
        		Log.i(TAG, "onChanged(): description="+desc+"   peripheralId="+perId+"   status="+status+"   getStatus()="+PeripheralManager.instance().getStatus(perId));
        	}
        	switch (perId) {
            case PeripheralManager.PER_HDMI_IN:
                Log.i(TAG, "HDMI IN status: " + (status == 1 ? "Connected" : "Disconnected"));
                if (status == 1)
                {
                    HdmiInConnect();
                } else {
                    HdmiInDisconnect();
                }
                break;
            case PeripheralManager.PER_HDMI_OUT:
                Log.i(TAG, "HDMI OUT status: " + ((status != 0) ? "Connected" : "Disconnected"));
                cresStreamCtrl.onHdmiOutHpdEvent((status ==1));
                break;
            case PeripheralManager.PER_USB_20:
                Log.v(TAG, "USB 2.0 status: " + ((status > 0) ? "Connected" : "Disconnected"));
            	usbEvent(PeripheralManager.PER_USB_20);
                break;
            case PeripheralManager.PER_USB_30:
                Log.v(TAG, "USB 3.0 status: " + ((status > 0) ? "Connected" : "Disconnected"));
            	usbEvent(PeripheralManager.PER_USB_30);
                break;
            }
        }
    }

    public void startPeripheralListener(CresStreamCtrl ctrl) 
    {
        mListener = new PeripheralStatusChangeListener(ctrl);

        // Force hdmi in and out sync status update at startup
        boolean hdmiInStatus = PeripheralManager.instance().getStatus(PeripheralManager.PER_HDMI_IN) == 1;
        if (hdmiInStatus)
            mListener.HdmiInConnect();
        else
            mListener.HdmiInDisconnect();
        boolean hdmiOutStatus = PeripheralManager.instance().getStatus(PeripheralManager.PER_HDMI_OUT) == 1;
        ctrl.onHdmiOutHpdEvent(hdmiOutStatus);

        if (PeripheralManager.instance().getStatus(PeripheralManager.PER_USB_30) > 0)
        	mListener.usbEvent(PeripheralManager.PER_USB_30);
        if (PeripheralManager.instance().getStatus(PeripheralManager.PER_USB_20) > 0)
        	mListener.usbEvent(PeripheralManager.PER_USB_20);

        Log.i(TAG, "Attaching listener for HDMI, USB events");
        PeripheralManager.instance().addStatusListener(PeripheralManager.PER_HDMI_IN, mListener, null);
        PeripheralManager.instance().addStatusListener(PeripheralManager.PER_HDMI_OUT, mListener, null);
        PeripheralManager.instance().addStatusListener(PeripheralManager.PER_USB_20, mListener, null);
        PeripheralManager.instance().addStatusListener(PeripheralManager.PER_USB_30, mListener, null);
    }
    
    public void showUsbDevices()
    {
        UsbManager manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
        while(deviceIterator.hasNext()){
            UsbDevice device = deviceIterator.next();
            Log.i(TAG,"Device: name="+device.getDeviceName()+"  id="+device.getDeviceId()+"  class="+device.getClass()+
                    "  subclass="+device.getDeviceSubclass());
            Log.i(TAG,"        manufacturer="+device.getManufacturerName()+"  productId="+device.getProductId()+"  producName="+device.getProductName()+
                    "  serialNumber="+device.getSerialNumber()+"  vendorId="+device.getVendorId());
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
