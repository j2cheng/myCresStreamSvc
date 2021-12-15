package com.crestron.txrxservice;

import com.crestron.txrxservice.CresStreamCtrl;
import com.crestron.txrxservice.UsbAvDevice;
import com.gs.core.peripheral.PeripheralManager;
import com.gs.core.peripheral.PeripheralUsbDevice;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.io.File;
import java.lang.reflect.Method;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.String;

public class ProductSpecific
{
    private static ProductSpecific mInstance;
    private Context context;
    private static boolean oneShot = false;
    CresCamera2 cam_handle;
    PeripheralStatusChangeListener mListener = null;
    private boolean true_hpd = false;

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

    ArrayList<String> usbDeviceWhiteList = new ArrayList<String>(Arrays.asList(
    //usbDeviceWhiteList is a simple array where USB VendorId is on even index 
    // and corresponding USB Product-Ids on odd index.

    //Make sure all instances of a device are added.

                         //VendorId(Even Index), ProductId(Odd Index)
    "5310",  "50",       //ID 14be:0032 Crestron-UC-Soundbar
    "11225", "49",       //ID 2bd9:0031 Crestron-UC-Soundbar
    "1133",  "2142",     //ID 046d:085e Logitech BRIO
    "1133",  "2115",     //ID 046d:0843 Logitech Webcam C930e
    "2830",  "9302"      //ID 0b0e:2456 Jabra Speak 810
    ));

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
    
    public static void Surface_forceScopedDisconnect(Surface surface) 
    {
        try {
            Method method = surface.getClass().getDeclaredMethod("forceScopedDisconnect");
            method.setAccessible(true);
            method.invoke(surface);
        } catch (Exception e) {
            e.printStackTrace();
        }
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
    
    public List<UsbAvDevice> findUsbDevices(int id)
    {
    	List<UsbAvDevice> dl = null;
    	for (UsbAvDevice d : usbDeviceList)
    	{
    		if (d.perId == id) {
    			if (dl == null)
    				dl = new ArrayList<UsbAvDevice>();
    			dl.add(d);
    		}
    	}
    	return dl;
    }
    
    private boolean getTrueHpdStatus()
    {
        boolean curr_true_hpd = false;
        StringBuilder text = new StringBuilder(16);
        try {
            File file = new File("/sys/class/switch/hdmi_true_hpd/state");

            BufferedReader br = new BufferedReader(new FileReader(file));  
            String line;   
            while ((line = br.readLine()) != null) {
                text.append(line);
            }
            br.close();
        }catch (Exception e) {
            text.append("0"); //if error default to no sync
        }
        if(Integer.parseInt(text.toString()) == 1)
            curr_true_hpd = true;

        Log.i(TAG, "getTrueHpdStatus(): returning "+curr_true_hpd);
        return curr_true_hpd;
    }
    
    public class PeripheralStatusChangeListener extends StatusChangeListener 
    {
        boolean hdmiInConnected = false;
        CresStreamCtrl cresStreamCtrl;

        public PeripheralStatusChangeListener(CresStreamCtrl ctrl) {
            super();
            cresStreamCtrl = ctrl;
            //hdmiInConnected = cam_handle.findCamera(HDMI_IN_DEV);
        }

        public void HdmiInConnect()
        {
            Log.i(TAG, "hdmiInConnected:" + hdmiInConnected + "   last camera error state:" + cam_handle.mCamErrCur);

            //If a camera error has occurred, don't see the HDMI Input to be connected
            if (cam_handle.mCamErrCur)
                hdmiInConnected = false;

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

        public boolean getSpeakerDetectedStatus(List<String> audioList)
        {
        	for (String s : audioList) { 
        		// Look for one of these devices to flag presence of a speaker
        		if (s.equals("snd/pcmC5D0p") || s.equals("snd/pcmC6D0p"))
        			return true;
        	}
        	return false;
        }

        public HashMap<String, String> genPropertiesMap(List<PeripheralUsbDevice> devices)
        {
      		HashMap<String, String> map = new HashMap<String, String>();
      		if (devices == null || devices.isEmpty())
      			return map;
      		
      		PeripheralUsbDevice device = devices.get(0);
      		if (device.getName() != null)
      			map.put("Name", device.getName());
      		if (device.getDeviceId() != null)
      			map.put("DeviceId", device.getDeviceId());
      		if (device.getVendorID() != null)
      			map.put("VendorId", device.getVendorID());
      		if (device.getDeviceClass() != null)
      			map.put("DeviceClass", device.getDeviceClass());
      		if (device.getDeviceSubclass() != null)
      			map.put("DeviceSubClass", device.getDeviceSubclass());
      		if (device.getManufacturer() != null)
      			map.put("Manufacturer", device.getManufacturer());
      		if (device.getProductID() != null)
      			map.put("ProductId", device.getProductID());
      		if (device.getProductName() != null)
      			map.put("ProductName", device.getProductName());
      		if (device.getSerialNumber() != null)
      			map.put("SerialNo", device.getSerialNumber());
      		
      		return map;
        }

        public void onUsbStatusChanged(int usbId, int status, String name, List<String> videoList, 
        		List<String> audioList, List<PeripheralUsbDevice> perUsbDevices)
        {
        	boolean change = false;
        	if (status > 0)
        	{
        		// device was added on port=usbId
        		List<UsbAvDevice> dl = findUsbDevices(usbId);
        		if (dl != null) {
        			dl.clear();
        		}
        		for (PeripheralUsbDevice perDev : perUsbDevices)
        		{
        			Log.i(TAG, "Peripheral device type = "+perDev.getType());
                    HashMap<String, String> propertyMap;
        			String aFile = null;
        			String vFile = null;
        			Boolean sFile = null;
        			if (perDev.getType() == com.gs.core.peripheral.UsbDeviceType.Audio)
        			{
        				aFile = getAudioCaptureFile(audioList);
                        Log.i(TAG, "onUsbStatusChanged(): audio capture file="+aFile);
                        if (aFile== null && perDev.getProductName().contains("Room Kit Mini"))
                        {
                            aFile="/dev/snd/pcmC5D0c";
                            Log.i(TAG, "onUsbStatusChanged(): on Room Kit Mini forcing audio capture file="+aFile);
                        }
        				//This is to populate the AirMedia WC Status IsSpeakerDetected Field
        				sFile = getSpeakerDetectedStatus(audioList);
        			} else if (perDev.getType() == com.gs.core.peripheral.UsbDeviceType.Video) {
        				vFile = getVideoCaptureFile(videoList);
                        Log.i(TAG, "onUsbStatusChanged(): video capture file="+vFile);
                        if (vFile==null && perDev.getProductName().contains("Room Kit Mini"))
                        {
                            vFile="/dev/video5";
                            Log.i(TAG, "onUsbStatusChanged(): on Room Kit Mini forcing video capture file="+vFile);
                        }
        			} else {
        				// BRIO coming in as Type "None" - need to fix
        				if (!audioList.isEmpty())
                        {
        					aFile = getAudioCaptureFile(audioList);
                            Log.i(TAG, "onUsbStatusChanged(): audio capture file="+aFile);
                            //This is to populate the AirMedia WC Status IsSpeakerDetected Field
                            sFile = getSpeakerDetectedStatus(audioList);
                            if (aFile==null && perDev.getProductName().contains("Room Kit Mini"))
                            {
                                aFile="/dev/snd/pcmC5D0c";
                                Log.i(TAG, "onUsbStatusChanged(): on Room Kit Mini forcing audio capture file="+aFile);
                            }
                        }
        				if (!videoList.isEmpty())
        				{
        					vFile = getVideoCaptureFile(videoList); 
                            Log.i(TAG, "onUsbStatusChanged(): video capture file="+vFile);
                            if (vFile==null && perDev.getProductName().contains("Room Kit Mini"))
                            {
                                vFile="/dev/video5";
                                Log.i(TAG, "onUsbStatusChanged(): on Room Kit Mini forcing video capture file="+vFile);
                            }
        				}
        			}
                    propertyMap = genPropertiesMap(perUsbDevices);
        			UsbAvDevice d = new UsbAvDevice(usbId, ((usbId==PeripheralManager.PER_USB_30)?"usb3":"usb2"), name, vFile, 
            				aFile, sFile, propertyMap);
            		Log.i(TAG, "UsbAudioVideoDeviceAdded(): new USB device "+d.deviceName+" added on "+d.usbPortType);
                    //Filter out Supported and Unsupported devices here for WC Feature
                    if(isUsbDeviceSupported(propertyMap))
                        usbDeviceList.add(d);
        		}
        	} else {
        		// device was removed on port=usbId
        		List<UsbAvDevice> dl = findUsbDevices(usbId);
        		if (dl != null) {
        			for (UsbAvDevice d : dl)
        			{
        				Log.i(TAG, "UsbAudioVideoDeviceRemoved(): USB device "+d.deviceName+" removed from "+d.usbPortType);
        				usbDeviceList.remove(d);
        			}
        		}
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
            List<PeripheralUsbDevice> perUsbDevices = null;
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
            try {
            	Method m = pMgrClass.getMethod("getDevices", int.class);
            	perUsbDevices = PeripheralManager.instance().getDevices(usbId);
            } catch (Exception e) {
            	e.printStackTrace();
            }
            if (status > 0)
            {
            	Log.i(TAG, "USB id="+usbId+"  Device="+name);
            	Log.i(TAG, "\tVideo Devices="+videoList);
            	Log.i(TAG, "\tAudio Devices="+audioList);
            	Log.i(TAG, "\tPeripheral USB Devices="+perUsbDevices);
            } else {
            	Log.i(TAG, "USB id="+usbId+"  No devices connected");
            }
    		onUsbStatusChanged(usbId, status, name, videoList, audioList, perUsbDevices);
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
                boolean new_true_hdp = getTrueHpdStatus();
                Log.i(TAG, "PeripheralManager.PER_HDMI_OUT" + " old_true_hdp " + true_hpd + " " + " new_true_hdp " + new_true_hdp);

                if(true_hpd != new_true_hdp)
                {
                    true_hpd = new_true_hdp;
                    cresStreamCtrl.onHdmiOutHpdEvent(true_hpd);
                }
                //cresStreamCtrl.onHdmiOutHpdEvent((status ==1));

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
        
        public boolean isUsbDeviceSupported(HashMap<String, String> propertyMap)
        {
            String vPropertyValue = null;
            String pPropertyValue = null;
            boolean retVal = true; //FIXME: Making default support all USB peripherals

            // using keySet() for iteration over USB keys
            for (String propertyName : propertyMap.keySet())
            {
                if(propertyName.equals("VendorId"))
                {
                    vPropertyValue = propertyMap.get(propertyName);
                    Log.v(TAG,"isUsbDeviceSupported: USB Vendor Id: " + vPropertyValue);
                }
                else if(propertyName.equals("ProductId"))
                {
                    pPropertyValue = propertyMap.get(propertyName);
                    Log.v(TAG,"isUsbDeviceSupported: USB Product Id: " + pPropertyValue);
                }
            }

            if(vPropertyValue == null || pPropertyValue == null)
                    return false;

            //usbDeviceWhiteList is a simple array where VendorId is on even index and corresponding Product-Ids on odd index.
            for (int j = 0; j < usbDeviceWhiteList.size(); j+=2) {
                if(usbDeviceWhiteList.get(j).equals(vPropertyValue) && usbDeviceWhiteList.get(j+1).equals(pPropertyValue))
                    retVal = true;
            }
            Log.i(TAG,"isUsbDeviceSupported: " + retVal);
            return retVal;
        }
    }

    public void startPeripheralListener(CresStreamCtrl ctrl) 
    {
        Log.i(TAG, "Starting Peripheral Listener");
        mListener = new PeripheralStatusChangeListener(ctrl);

        // Force hdmi in and out sync status update at startup
        boolean hdmiInStatus = PeripheralManager.instance().getStatus(PeripheralManager.PER_HDMI_IN) == 1;
        if (hdmiInStatus)
            mListener.HdmiInConnect();
        else
            mListener.HdmiInDisconnect();
        boolean hdmiOutStatus = PeripheralManager.instance().getStatus(PeripheralManager.PER_HDMI_OUT) == 1;
        true_hpd = getTrueHpdStatus();
        ctrl.onHdmiOutHpdEvent(true_hpd);

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
