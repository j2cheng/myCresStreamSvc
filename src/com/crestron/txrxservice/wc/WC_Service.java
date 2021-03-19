package com.crestron.txrxservice.wc;

import android.content.Intent;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.Log;

import java.util.List;

import com.crestron.txrxservice.CresStreamCtrl;
import com.crestron.txrxservice.GstreamOut;
import com.crestron.txrxservice.MiscUtils;
import com.crestron.txrxservice.UsbAvDevice;
import com.crestron.txrxservice.wc.ipc.WC_Connection;
import com.crestron.txrxservice.wc.ipc.WC_Status;
import com.crestron.txrxservice.wc.ipc.WC_UsbDevice;
import com.crestron.txrxservice.wc.ipc.IWC_Callback;
import com.crestron.txrxservice.wc.ipc.IWC_Service;

public class WC_Service {
    private static final String TAG="WC_Service";
    private static final int ERROR_IN_USE = -1;
    private static final int ERROR_NO_USB_DEVICES = -2;
    private static final int ERROR_INVALID_ID = -3;

    public CresStreamCtrl mStreamCtrl = null;
    public GstreamOut mStreamOut = null;
    public int mCurrentId = 0;
    WC_Status mWcStatus = null;
    String mVideoFile;
    String mAudioFile;

    public WC_Service(CresStreamCtrl streamCtrl)
    {
        mStreamCtrl = streamCtrl;
        mStreamOut = mStreamCtrl.getStreamOut();
        mWcStatus = new WC_Status();
    }

    final RemoteCallbackList<IWC_Callback> mCallbacks = new RemoteCallbackList<IWC_Callback>();

    private final IWC_Service.Stub mBinder = new IWC_Service.Stub() {
        // will check USB, start server and return a positive session id on success or a negative integer code in event of failure (-1 = in use, -2 = no USB devices present)
        // when server is started a new username/password are generated to form URL and new X509 certificate and privateKey are generated
        public int WC_OpenSession(String userId)
        {
            Log.i(TAG,"WC_OpenSession: request from userId="+userId);
            if (!mStreamOut.wcStarted()) {
                mStreamCtrl.setWirelessConferencingStreamEnable(true);
                mCurrentId++;
                return mCurrentId;
            } else {
                return ERROR_IN_USE;
            }
        }

        // WC connection contains URL (includes username and password as part of URL), certificate, and private key as strings read from .pem files
        public WC_Connection WC_GetConnectionParameters(int id)
        {
            Log.i(TAG,"WC_GetConnectionParameters: request from id="+id);
            WC_Connection wc_connection = null;
            if (id == mCurrentId) {
                wc_connection = getConnectionParameters();
            }
            return wc_connection;
        }

        // errorcode or 0 for success
        public int WC_CloseSession(int id)
        {
            Log.i(TAG,"WC_CloseSession: request from id="+id);
            if (id == mCurrentId)
            {
                if (mStreamOut.wcStarted()) {
                    mStreamCtrl.setWirelessConferencingStreamEnable(false);
                }
                return 0;
            } else {
                return ERROR_INVALID_ID;
            }
        }

        public void registerCallback(IWC_Callback cb)
        {
            if (cb != null) mCallbacks.register(cb);
            Log.i(TAG,"registering new callback: try to send current WC status");
            // Broadcast to current client.
            final int N = mCallbacks.beginBroadcast();
            for (int i=0; i<N; i++) {
                try {
                	if (mCallbacks.getBroadcastItem(i) == cb) {
                        Log.i(TAG,"send current WC status to latest client");
                		mCallbacks.getBroadcastItem(i).onStatusChanged(mWcStatus);
                	}
                } catch (RemoteException e) {
                    // The RemoteCallbackList will take care of removing
                    // the dead object for us.
                }
            }
            mCallbacks.finishBroadcast();
        }

        public void unregisterCallback(IWC_Callback cb)
        {
            if (cb != null) mCallbacks.unregister(cb);
        }
    };

    public IWC_Service.Stub getBinder()
    {
        return mBinder;
    }

    public void unbind(Intent intent)
    {
    	Log.i(TAG, "unbind() - intent="+intent);
    }
    
    public void rebind(Intent intent)
    {
    	Log.i(TAG, "rebind() - intent="+intent);
    }
    
    public void onClientConnected(String clientIp)
    {
        Log.i(TAG,"onClientConnected: client IP address="+clientIp);
        // Broadcast to all clients the new value.
        final int N = mCallbacks.beginBroadcast();
        for (int i=0; i<N; i++) {
            try {
                mCallbacks.getBroadcastItem(i).onClientConnected(clientIp);
            } catch (RemoteException e) {
                // The RemoteCallbackList will take care of removing
                // the dead object for us.
            }
        }
        mCallbacks.finishBroadcast();
    }

    public void onClientDisconnected(String clientIp)
    {
        Log.i(TAG,"onClientDisconnected: client IP address="+clientIp);
        // Broadcast to all clients the new value.
        final int N = mCallbacks.beginBroadcast();
        for (int i=0; i<N; i++) {
            try {
                mCallbacks.getBroadcastItem(i).onClientDisconnected(clientIp);
            } catch (RemoteException e) {
                // The RemoteCallbackList will take care of removing
                // the dead object for us.
            }
        }
        mCallbacks.finishBroadcast();
    }

    public void onServerStart()
    {
        Log.i(TAG,"invoking onServerStart() callbacks");
        // Broadcast to all clients the new value.
        final int N = mCallbacks.beginBroadcast();
        for (int i=0; i<N; i++) {
            try {
                mCallbacks.getBroadcastItem(i).onServerStart();
            } catch (RemoteException e) {
                // The RemoteCallbackList will take care of removing
                // the dead object for us.
            }
        }
        mCallbacks.finishBroadcast();
    }

    public void onServerStop()
    {
        Log.i(TAG,"invoking onServerStop() callbacks");
        // Broadcast to all clients the new value.
        final int N = mCallbacks.beginBroadcast();
        for (int i=0; i<N; i++) {
            try {
                mCallbacks.getBroadcastItem(i).onServerStop();
            } catch (RemoteException e) {
                // The RemoteCallbackList will take care of removing
                // the dead object for us.
            }
        }
        mCallbacks.finishBroadcast();
    }

    public void onStatusChanged()
    {
        Log.i(TAG,"invoking onStatusChanged() callbacks");
        // Broadcast to all clients the new value.
        final int N = mCallbacks.beginBroadcast();
        for (int i=0; i<N; i++) {
            try {
                mCallbacks.getBroadcastItem(i).onStatusChanged(mWcStatus);
            } catch (RemoteException e) {
                // The RemoteCallbackList will take care of removing
                // the dead object for us.
            }
        }
        mCallbacks.finishBroadcast();
    }
    
    public String getUrl()
    {
        return mStreamOut.getWcServerUrl();
    }

    public String getCertificate()
    {
        return mStreamOut.getWcServerCertificate();
    }

    public String getKey()
    {
        return mStreamOut.getWcServerKey();
    }

    public WC_Connection getConnectionParameters()
    {
        String url = mStreamOut.getWcServerUrl();
        String cert = mStreamOut.getWcServerCertificate();
        String privKey = mStreamOut.getWcServerKey();
        WC_Connection wc_connection = new WC_Connection(1, url, cert, privKey);
        return wc_connection;
    }

    public void showConnectionParameters(String from)
    {
        Log.i(TAG, "from "+from);
        Log.i(TAG,"Connection_Parameters={\n"+getConnectionParameters().toString()+"\n}");
    }
    
    public void updateStatus(List<UsbAvDevice> devices)
    {
    	WC_Status wcStatus = generateWcStatus(devices);
    	if (!mWcStatus.isEqual(wcStatus))
    	{
    		mWcStatus = wcStatus;
    		Log.i(TAG, "getWcStatus(): status has changed: "+mWcStatus);
    		onStatusChanged();
    	} else {
    		Log.i(TAG, "getWcStatus(): no change in status");
    	}
    }
    
    public WC_Status generateWcStatus(List<UsbAvDevice> devices)
    {
    	WC_Status wcStatus = new WC_Status();
    	UsbAvDevice usb2Device = null;
    	UsbAvDevice usb3Device = null;
    	
    	for (UsbAvDevice device : devices) {
    		if (device.usbPortType.equals("usb2"))
    			usb2Device = device;
    		else if (device.usbPortType.equals("usb3"))
    			usb3Device = device;
    		else
        		Log.i(TAG, "generateWcStatus(): unexpected device port type: "+device);
    	}
    	// Add explicitly to list so that order is guaranteed when isEquals() is called
    	if (usb3Device != null) {
    		WC_UsbDevice dev = new WC_UsbDevice("usb3", usb3Device.deviceName, 
    				(usb3Device.videoFile != null), (usb3Device.audioFile != null));
    		if (wcStatus.devices != null)
    		{
    			wcStatus.devices.add(dev);
    		} else
        		Log.e(TAG, "generateWcStatus(): null devices list");
    	}
    	if (usb2Device != null) {
    		WC_UsbDevice dev = new WC_UsbDevice("usb2", usb2Device.deviceName, 
    				(usb2Device.videoFile != null), (usb2Device.audioFile != null));
    		if (wcStatus.devices != null)
    		{
    			wcStatus.devices.add(dev);
    		} else
        		Log.e(TAG, "generateWcStatus(): null devices list");
    	}
    	
    	setActiveDevices(usb3Device, usb2Device);
    	
    	return wcStatus;
    }
    
    public void setActiveDevices(UsbAvDevice usb3Device, UsbAvDevice usb2Device)
    {
    	String videoFile = null;
    	String audioFile = null;
    	boolean change = false;
    	
    	if (usb3Device != null) {
    		// Use USB3 video in preference to USB2 if connected
    		if (usb3Device.videoFile != null)
    		{
    			videoFile = usb3Device.videoFile;
    			
    		}
    		if (usb3Device.audioFile != null)
    		{
    			audioFile = usb3Device.audioFile;	
    		}
    	}
    	if (usb2Device != null) {
    		if (videoFile == null && usb2Device.videoFile != null)
    		{
    			videoFile = usb2Device.videoFile;
    			
    		}
    		// use USB2 audio in preference to USB3 audio if it is connected and is an audio only device
    		if (usb2Device.audioFile != null && (audioFile == null || usb2Device.videoFile == null))
    		{
    			audioFile = usb2Device.audioFile;	
    		}
    	}
    	
    	if ((videoFile == null && mVideoFile != null) || (videoFile != null && !videoFile.equals(mVideoFile)))
    	{
    		mVideoFile = videoFile;
    		change = true;
    	}
    	if ((audioFile == null && mAudioFile != null) || (audioFile != null && !audioFile.equals(mAudioFile)))
    	{
    		mAudioFile = audioFile;
    		change = true;
    	}
    	if (change)
    	{
    		Log.i(TAG, "WC video device is "+mVideoFile+" audio device is "+mAudioFile);
    		mStreamCtrl.userSettings.setWcVideoCaptureDevice(mVideoFile);
    		mStreamCtrl.userSettings.setWcAudioCaptureDevice(mAudioFile);
    	}
    }
}
