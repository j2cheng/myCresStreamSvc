package com.crestron.txrxservice.wc;

import android.content.Intent;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import com.crestron.txrxservice.CresStreamCtrl;
import com.crestron.txrxservice.GstreamOut;
import com.crestron.txrxservice.HDMIInputInterface;
import com.crestron.txrxservice.UsbAvDevice;
import com.crestron.txrxservice.wc.WC_CresstoreStatus;
import com.crestron.txrxservice.wc.ipc.WC_Connection;
import com.crestron.txrxservice.wc.ipc.WC_Error;
import com.crestron.txrxservice.wc.ipc.WC_SessionFlags;
import com.crestron.txrxservice.wc.ipc.WC_SessionOptions;
import com.crestron.txrxservice.wc.ipc.WC_Status;
import com.crestron.txrxservice.wc.ipc.WC_UsbDevice;
import com.crestron.txrxservice.wc.ipc.WC_UsbDevices;
import com.crestron.txrxservice.wc.ipc.WC_VideoFormat;
import com.crestron.txrxservice.wc.ipc.IWC_Callback;
import com.crestron.txrxservice.wc.ipc.IWC_Service;
import com.crestron.txrxservice.wc.ipc.WC_AudioFormat;

public class WC_Service {
    private static final String TAG="WC_Service";
    //These return values should be in sync with IrisWcError.java and should not clash
    //OpenSession Related
    public static final int ERROR_IN_USE = -1;
    public static final int ERROR_NO_USB_DEVICES = -2;
    public static final int ERROR_INVALID_ID = -3;
    public static final int ERROR_WC_SERVICE_UNAVAILABLE = -4;
    public static final int ERROR_UNSUPPORTED_CAMERA_FORMAT = -20;
    //Pre-Session related error
    public static final int ERROR_AUDIOCAPTURE_FORMAT = 0x1000;
    public static final int ERROR_VIDEOCAPTURE_FORMAT = 0x2000;

    public static final int WCERROR_MODULE_NONE        = 0;
    public static final int WCERROR_MODULE_VIDEO       = 1;
    public static final int WCERROR_MODULE_AUDIO       = 2;
    public static final int WCERROR_MODULE_AUDIOVIDEO  = 3;

    private static final String WC_CONF_STATUS_IN_USE="In Use";
    private static final String WC_CONF_STATUS_AVAILABLE="Available";
    private static final String WC_CONF_STATUS_UNAVAILABLE="Unavailable";

    private WC_SessionFlags mSessionFlags = WC_SessionFlags.None;

    private final Object broadcastObjectLock = new Object();

    private static final int WaitForClientToConnect=30*1000;   // 30 seconds 
    private Thread mWaitForClientToConnectThread = null;	
        
    private final Object StartCloseSessionLock = new Object();
    private static final int WC_COMPLETE_TIME=30*1000;   // 30 seconds     
    private static boolean WCStartCompletePending = true;
   
    Gson gson = new GsonBuilder().create();

    public CresStreamCtrl mStreamCtrl = null;
    public GstreamOut mStreamOut = null;
    public int mCurrentId = 0;
    String mCurrentUser = null;
    WC_Status mStatus = null;
    WC_CresstoreStatus mWcCresstoreStatus = null;
    WC_UsbDevices mUsbDevices = null;
    String mVideoFile = null;
    String mAudioFile = null;
    Boolean speakerDetected = false;
    Boolean cameraFormatSupported = true;  //Do not allow opensession request to succeed if this isnt set
    Boolean mPreviousHdmiSync = null;
    List<WC_VideoFormat> mVideoFormats = new ArrayList<WC_VideoFormat>();
    List<WC_AudioFormat> mAudioFormats = new ArrayList<WC_AudioFormat>();
    List<UsbAvDevice> mUsbAvDeviceList = new ArrayList<UsbAvDevice>();
    AtomicBoolean inUse = new AtomicBoolean(false);
    AtomicBoolean closeSessionInProgress = new AtomicBoolean(false);
    final WC_UsbDevices mUsbEmptyDevices = new WC_UsbDevices(null, null, null);
    String mActiveTx3DeviceId = null;

    //run a monitor thread to see if any client has connected after we have started the WC open session
    private final Runnable waitForClientToConnectedRunnable = new Runnable() {
        @Override
            public void run() {
                Log.i(TAG,"CheckClientConnected: Waiting for Client to get connected");
                try
                {
                    Thread.sleep(WaitForClientToConnect);
                    if( !mStatus.isClientConnected )
                    {
                        Log.w(TAG,"CheckClientConnected: Timedout: No Client connected, so stopping the WC session ");
                        Log.i(TAG,"waitForClientToConnectedRunnable --> closeSession");
                        if (closeSession() == 0)
                            Log.i(TAG,"CheckClientConnected: closeSession: WC is not in use");
                    }
                }
                catch(InterruptedException ex)
                {
                    Log.w(TAG,"CheckClientConnected: Thread was interrupted externally and was exited");
                }  
                Log.i(TAG,"CheckClientConnected: Waiting for Client thread exited");
            }
    };

    public void  waitForOpenSessionToComplete()
    {
        synchronized(StartCloseSessionLock){
            if( WCStartCompletePending == true )
            {
                try{
                    Log.i(TAG,"WC_CloseSession: Open Session is in progress. Waiting to complete ...");
                    StartCloseSessionLock.wait(WC_COMPLETE_TIME);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    public void notfiyOpenSessionCompletion()
    {
        synchronized(StartCloseSessionLock)
        {
            WCStartCompletePending = false;
            try {
                StartCloseSessionLock.notify();
            } catch (Exception ex) {
                Log.i(TAG, "notfiyOpenSessionCompletion(): exception in Signaling Close Session: " + ex);
                ex.printStackTrace();
            }
        }                            
    }

    public WC_Service(CresStreamCtrl streamCtrl)
    {
        mStreamCtrl = streamCtrl;
        mStreamOut = mStreamCtrl.getStreamOut();
        mStatus = new WC_Status();
        mWcCresstoreStatus = new WC_CresstoreStatus(streamCtrl);
    }

    final RemoteCallbackList<IWC_Callback> mCallbacks = new RemoteCallbackList<IWC_Callback>();

    private final IWC_Service.Stub mBinder = new IWC_Service.Stub() {
        // will check USB, start server and return a positive session id on success or a negative integer code in event of failure (-1 = in use, -2 = no USB devices present)
        // when server is started a new username/password are generated to form URL and new X509 certificate and privateKey are generated
        public int WC_OpenSession(String clientId, WC_SessionOptions options)
        {
            Log.i(TAG,"WC_OpenSession: WC Enabled="+ mStreamCtrl.isWirelessConferencingEnabled +
                        ", PeripheralBlocked State=" +  mStreamCtrl.mDeviceAppAirMediaWCStatusIsPeripheralBlocked + 
                        ", PeripheralBlocked Reason=" +  mStreamCtrl.mDeviceAppAirMediaWCStatusIsPeripheralBlockedReason +
                        ", HDMI input mode=" + mStreamCtrl.userSettings.getHdmiInMode().equalsIgnoreCase("Camera") +
                        ", HDMI input enabled= " + mStreamCtrl.mDeviceAppConfigRunTimeSettingsIsHdmiInputEnabled +
                        " request from clientId="+clientId+" options="+options);

             //Accept open session only if WirelessConferencing is enabled
             //Accept open session only if Device/App/AirMedia/WirelessConferencing/Status/isPeripheralBlocked is "false"
                 //if the TV is off, do not start the WC
             if (  (mStreamCtrl != null) && 
                    ((!mStreamCtrl.isWirelessConferencingEnabled)            || 
                     (mStreamCtrl.mDeviceAppAirMediaWCStatusIsPeripheralBlocked == true)))
                return ERROR_WC_SERVICE_UNAVAILABLE;

//            if ( mStreamCtrl.userSettings.getHdmiInMode().equalsIgnoreCase("Camera") && 
//                mStreamCtrl.mDeviceAppConfigRunTimeSettingsIsHdmiInputEnabled == false 
//                )
//            {
//                // if HDMI input is camera mode and HDMI input is disabled, do not start the WC
//                Log.w(TAG,"WC_OpenSession: HDMI input/camera is disabled, WC is not allowed");
//                return ERROR_WC_SERVICE_UNAVAILABLE;
//            }

            if ((mUsbDevices==null) || (mUsbDevices.devices.size() == 0)) {
                return ERROR_NO_USB_DEVICES;
            }

            if(options.flags != WC_SessionFlags.Audio)
            {   //Make sure only for non-audio only devices this check is performed
                if(!cameraFormatSupported){
                    Log.w(TAG,"WC_OpenSession: Unsupported camera format");
                    return ERROR_UNSUPPORTED_CAMERA_FORMAT;
                }
            }
            
            if (inUse.compareAndSet(false, true)) {
                WCStartCompletePending = true;
                mCurrentId++;
                mCurrentUser = clientId.toLowerCase(Locale.ENGLISH).contains("IrisTX3".toLowerCase(Locale.ENGLISH)) ? "IrisTX3" : "AirMedia";
            	mStatus = new WC_Status(false, false, mCurrentId, clientId, options.nickname, options.flags);
            	updateUsbDeviceStatus(mUsbAvDeviceList);
            	// server start will communicate via callback onStatusChanged once it has been started
            	mStreamCtrl.setWcOwner(mCurrentUser);
                mStreamCtrl.setWirelessConferencingStreamEnable(true);

                mSessionFlags = options.flags;
                mWcCresstoreStatus.reportWCInUseStatus(mSessionFlags, true);

                //start a thread to monitor if the clients are getting connected.
                mWaitForClientToConnectThread =new Thread(waitForClientToConnectedRunnable);
                mWaitForClientToConnectThread.start();  

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
                wc_connection = getConnectionParameters(id);
            }
            return wc_connection;
        }

        // errorcode or 0 for success
        public int WC_CloseSession(int id)
        {
            Log.i(TAG,"WC_CloseSession: request from id="+id);
            if (id == mCurrentId)
            {
                Log.i(TAG,"WC_CloseSession --> closeSession");
                //check if WC start / client connected action is completed
                if( inUse.get() )
                {
                    waitForOpenSessionToComplete();
                }
                if (closeSession() == 0)
                    Log.i(TAG,"WC_CloseSession: WC is not in use");
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
            synchronized(broadcastObjectLock) {
                try {
                    final int N = mCallbacks.beginBroadcast();
                    for (int i=0; i<N; i++) {
                        try {
                            if (mCallbacks.getBroadcastItem(i) == cb) {
                                Log.i(TAG,"send current WC status to client: "+mStatus);
                                mCallbacks.getBroadcastItem(i).onStatusChanged(mStatus);
                                Log.i(TAG,"send current WC usb device status to client: "+mUsbDevices);
                                mCallbacks.getBroadcastItem(i).onUsbDevicesChanged(mUsbDevices);
                            }
                        } catch (RemoteException e) {
                            // The RemoteCallbackList will take care of removing
                            // the dead object for us.
                        }
                    }
                } catch (Exception e) {
                    Log.i(TAG, "registerCallback(): " + e);
                    e.printStackTrace();
                } finally {
                    try {
                        mCallbacks.finishBroadcast();
                    } catch (Exception e) {
                        Log.i(TAG, "registerCallback(): exception in finishBroadcast: " + e);
                        e.printStackTrace();
                    }
                }
                getAndReportAllWCStatus();
            }
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
    	if (!mStatus.isClientConnected) {
    		mStatus.isClientConnected=true;
    		onStatusChanged();
            // Indicate that the start is completed. Now any WC_CloseSession can be called. 
            notfiyOpenSessionCompletion();         
    	}
        //showConnectionParameters("onClientConnected", mCurrentId);
    }

    public void onClientDisconnected(String clientIp)
    {
        Log.i(TAG,"onClientDisconnected: client IP address="+clientIp);
    	if (mStatus.isClientConnected) {
    		mStatus.isClientConnected=false;
    		onStatusChanged();
    	}
    }

    public void onServerStart()
    {
        Log.i(TAG,"onServerStart()");
    	if (!mStatus.isServerStarted) {
    		mStatus.isServerStarted=true;
    		mWcCresstoreStatus.reportWCDeviceStatus(null,null,null,null,WC_CONF_STATUS_IN_USE);
    		onStatusChanged();
    		// if this was started by TX-3 notify wifivideoplayer so canvas can be informed that session is using peripherals
    		if (mStatus.nickname != null && mStatus.nickname.startsWith("Iris"))
    		{
    		    String deviceId = mStatus.nickname.replaceFirst("IrisTx3-", "");
    		    if (mStreamCtrl != null && mStreamCtrl.wifidVideoPlayer != null)
    		    {
    		        Log.i(TAG, "WC started by TX3 deviceId="+deviceId);
    		        mActiveTx3DeviceId = deviceId;
    		        mStreamCtrl.wifidVideoPlayer.onTx3WcChanged(mActiveTx3DeviceId, true);
    		    }
    		}
    	}
    }

    public void onServerStop()
    {
        Log.i(TAG,"onServerStop()");
    	if (mStatus.isServerStarted) {
    		mStatus.isServerStarted=false;
            mStatus.sessionId = 0; // for some reason AirMedia receiver uses this to see if we had a restart of server
    		mWcCresstoreStatus.reportWCDeviceStatus(null,null,null,null,WC_CONF_STATUS_AVAILABLE);
    		onStatusChanged();
            Log.i(TAG,"onServerStop --> closeSession");
            closeSession();
    	}
    }

    public void onStatusChanged()
    {
        Log.i(TAG,"invoking onStatusChanged() callbacks");
        // Broadcast to all clients the new value.
        synchronized(broadcastObjectLock) {
            try {
                final int N = mCallbacks.beginBroadcast();
                for (int i=0; i<N; i++) {
                    try {
                        mCallbacks.getBroadcastItem(i).onStatusChanged(mStatus);
                    } catch (RemoteException e) {
                        // The RemoteCallbackList will take care of removing
                        // the dead object for us.
                    }
                }
            } catch (Exception e) {
                Log.i(TAG, "onStatusChanged(): exception in beginBroadcast: " + e);
                e.printStackTrace();
            } finally {
                try {
                    mCallbacks.finishBroadcast();
                } catch (Exception e) {
                    Log.i(TAG, "onStatusChanged(): exception in finishBroadcast: " + e);
                    e.printStackTrace();
                }
            }
        }
    }
    
    public void onUsbDevicesChanged(WC_UsbDevices devices)
    {
        Log.i(TAG,"invoking onUsbDevicesChanged() callbacks");
        // Broadcast to all clients the new value.
        synchronized(broadcastObjectLock) {
            try {
                final int N = mCallbacks.beginBroadcast();
                for (int i=0; i<N; i++) {
                    try {
                        mCallbacks.getBroadcastItem(i).onUsbDevicesChanged(devices);
                    } catch (RemoteException e) {
                        // The RemoteCallbackList will take care of removing
                        // the dead object for us.
                    }
                }
            } catch (Exception e) {
                Log.i(TAG, "onUsbDevicesChanged(): exception in beginBroadcast: " + e);
                e.printStackTrace();
            } finally {
                try {
                    mCallbacks.finishBroadcast();
                } catch (Exception e) {
                    Log.i(TAG, "onUsbDevicesChanged(): exception in finishBroadcast: " + e);
                    e.printStackTrace();
                }
            }
        }
    }

    public void performSoftUsbReset()
    {
        int count = 0;
        final int maxCountWait = 10;
        Log.i(TAG,"*****+++++***** Entering performSoftUsbReset() *****+++++*****");

        while(  (mStatus.isServerStarted == true) &&
                (count++ < maxCountWait))
        {
            Log.i(TAG,"performSoftUsbReset() - serverStarted == true, waiting a sec " + count);
            try {
                Thread.sleep(1000);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        if(mStatus.isServerStarted == true)
            Log.w(TAG,"performSoftUsbReset() - serverStarted == true, Even after 10 second!!!! " + count);

        onUsbDevicesChanged(mUsbEmptyDevices);
        
        mStreamCtrl.performSoftUsbReset();

        Log.i(TAG,"Exiting performSoftUsbReset() - Done with the Reset");
    }

    public synchronized void onError(int module, int errorCode, String errorMessage)
    {
        WC_Error error = new WC_Error(module, errorCode, errorMessage);
        Log.i(TAG,"invoking onError() callbacks with "+error);
        // Broadcast to all clients the new value.
        synchronized(broadcastObjectLock) {
            try {
                final int N = mCallbacks.beginBroadcast();
                for (int i=0; i<N; i++) {
                    try {
                        mCallbacks.getBroadcastItem(i).onError(error);
                    } catch (RemoteException e) {
                        // The RemoteCallbackList will take care of removing
                        // the dead object for us.
                    }
                }
            } catch (Exception e) {
                Log.i(TAG, "onError(): exception in beginBroadcast: " + e);
                e.printStackTrace();
            } finally {
                try {
                    mCallbacks.finishBroadcast();
                } catch (Exception e) {
                    Log.i(TAG, "onError(): exception in finishBroadcast: " + e);
                    e.printStackTrace();
                }
            }
        }
    }
    
    public void stopServer(String user)
    {
        Log.i(TAG, "stopServer(): user="+user+" currentUser="+mCurrentUser);
        if (mCurrentUser != null)
        {
            if (user == null || user.toLowerCase(Locale.ENGLISH).contains(mCurrentUser.toLowerCase(Locale.ENGLISH)))
            {
                if (user != null)
                    Log.i(TAG, "stopServer(): calling closeSession for user="+user);
                else
                    Log.i(TAG, "stopServer(): calling closeSession unconditionally");
                Log.i(TAG,"stopServer --> closeSession");
                closeSession();
            }
        } else {
            Log.w(TAG,"stopServer(): Nothing to stop - current user is null");
        }
    }

    public int closeSession()
    {
        int rv = 0;
        if (closeSessionInProgress.compareAndSet(false, true)) {
            rv = _closeSession();
            closeSessionInProgress.set(false);
            // In case if there is a WC_CloseSession called while we are starting, and close session is called
            // due to any error scenario, wake up the waiting thread so that WC_CloseSession comes out of wait loop 
            notfiyOpenSessionCompletion();
        }
        else
            Log.w(TAG,"closeSession() ignored since closeSessionInProgress is "+closeSessionInProgress.get());
        return rv; 
    }

    private int _closeSession()
    {
        int rv = 0;
        if (inUse.get()) { // lockout any opensessions from succeeding until this finishes
            Log.i(TAG,"_closeSession: entry");
            mCurrentUser = null;
            // server stop will communicate via callback onStatusChanged once it has been started
            mStatus = new WC_Status(mStatus.isServerStarted, false, 0, "", "", WC_SessionFlags.None);
            mStreamCtrl.setWirelessConferencingStreamEnable(false);

            mWcCresstoreStatus.reportWCInUseStatus(mSessionFlags, false);
            mSessionFlags = WC_SessionFlags.None;

            // if this was started by TX-3 notify wifivideoplayer so canvas can be informed that session is no longer using peripherals
            if (mActiveTx3DeviceId != null)
            {
                if (mStreamCtrl != null && mStreamCtrl.wifidVideoPlayer != null)
                {
                    Log.i(TAG, "_closeSession(): WC stopped for session started by TX3 deviceId="+mActiveTx3DeviceId);
                    mStreamCtrl.wifidVideoPlayer.onTx3WcChanged(mActiveTx3DeviceId, false);
                }
                mActiveTx3DeviceId = null;
            }

            // check if any previous monitor thread is running, if yes stop it.
            if( mWaitForClientToConnectThread != null && 
                !mWaitForClientToConnectThread.getState().equals(Thread.State.TERMINATED) )
            {
                Log.w(TAG,"_closeSession: Check Client connected thread is not terminated, forcing to exit");
                mWaitForClientToConnectThread.interrupt();
            }               
            rv = 1;

            if (mStreamCtrl.userSettings.getAirMediaResetUsbOnStop()) 
            {
                performSoftUsbReset();
            }
            inUse.getAndSet(false); // not interested in previous value
            Log.i(TAG,"_closeSession: exit");
        }
        else
            Log.w(TAG,"_closeSession() ignored since inUse is "+inUse.get());

        return rv;
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

    public WC_Connection getConnectionParameters(int id)
    {
        List<String> urlList = mStreamOut.getWcServerUrlList();
        String cert = mStreamOut.getWcServerCertificate();
        //String privKey = mStreamOut.getWcServerKey();
        WC_Connection wc_connection = new WC_Connection(id, urlList, cert);
        Log.i(TAG,"getConnectionParameters(): params="+wc_connection.toStringSanitizedURL());
        return wc_connection;
    }

    public void showConnectionParameters(String from, int id)
    {
        Log.i(TAG, "from "+from);
        Log.i(TAG,"Connection_Parameters={\n"+getConnectionParameters(id).toString()+"\n}");
    }
    
    public WC_SessionFlags getSessionFlags()
    {
        if (mStatus == null)
            return WC_SessionFlags.None;
        else
            return mStatus.sessionFlags;
    }
    
    public void hdmiSyncStateUpdate()
    {
        if (!mStreamCtrl.userSettings.getHdmiInMode().equalsIgnoreCase("Camera"))
        {
            // Not in camera mode so HDMI sync does not matter
            mPreviousHdmiSync = null;
        } 
        else 
        {
            // If HDMI in sync change and we are in camera mode - we need to update our WC devices
            boolean hdmiSync = (mStreamCtrl.getHDMIInSyncStatus().equalsIgnoreCase("true") ? true : false);
            if (mPreviousHdmiSync == null || mPreviousHdmiSync != hdmiSync)
            {
                Log.i(TAG,"hdmiSyncStateUpdate(): sync changed from "+mPreviousHdmiSync+" to "+hdmiSync);
                mPreviousHdmiSync = hdmiSync;
                // if hdmi input sync changes and we are in camera mode stop any existing WC session by stopping the server
                // if we gained sync we could be in audio only call and we still need to stop and update devices to indicate
                // that both audio and video are now available
                // if we lost sync we want to stop existing call if and update devices to indicate that now audio only may be
                // available
                Log.i(TAG, "hdmiSyncStateUpdate(): stop WC server, HDMI input sync changed in HDMI camera mode!!!");
                stopServer(null);
                updateWcCamera();
            }
        }
    }
    
    public synchronized void updateWcCamera()
    {
        updateUsbDeviceStatus(mUsbAvDeviceList);
    }
    
    //Function gets invoked from 1) WC_OpenSession and 2) onUsbStatusChanged and 3) updateCamera
    public synchronized void updateUsbDeviceStatus(List<UsbAvDevice> devices)
    {
    	mUsbAvDeviceList = devices;
    	WC_UsbDevices usbDevices = generateUsbDevices(mUsbAvDeviceList);
    	if (!WC_UsbDevices.isEqual(mUsbDevices, usbDevices))
    	{
    		mUsbDevices = usbDevices;
    		Log.i(TAG, "updateUsbDeviceStatus(): usb device status has changed: "+mUsbDevices);
    		onUsbDevicesChanged(mUsbDevices);
            mStreamCtrl.setAudioPlaybackFile(null);
    		getAndReportAllWCStatus();
    	} else {
    		Log.i(TAG, "updateUsbDeviceStatus(): no change in usb device status");
    	}
    }
    
    public WC_UsbDevices generateUsbDevices(List<UsbAvDevice> devices)
    {
    	List<WC_UsbDevice> usbDeviceList = new ArrayList<WC_UsbDevice>();
    	List<UsbAvDevice> usb3Devices = new ArrayList<UsbAvDevice>();
    	List<UsbAvDevice> usb2Devices = new ArrayList<UsbAvDevice>();
    	WC_UsbDevice hdmiCameraDevice=null;

    	if (devices != null && devices.size() > 0)
    	{
    	    for (UsbAvDevice device : devices) {
    	        if (!device.usbPortType.equals("usb3"))
    	            continue;
    	        usb3Devices.add(device);
    	        WC_UsbDevice dev = new WC_UsbDevice("usb3", "usb3-device", device.deviceName, 
    	                (device.videoFile != null), (device.audioFile != null), device.properties);
    	        usbDeviceList.add(dev);
    	    }
    	    for (UsbAvDevice device : devices) {
    	        if (!device.usbPortType.equals("usb2"))
    	            continue;
    	        usb2Devices.add(device);
    	        WC_UsbDevice dev = new WC_UsbDevice("usb2", "usb2-device", device.deviceName, 
    	                (device.videoFile != null), (device.audioFile != null), device.properties);
    	        usbDeviceList.add(dev);
    	    }
    	}
        if ( mStreamCtrl.userSettings.getHdmiInMode().equalsIgnoreCase("Camera"))
        {
            boolean haveHdmiSync = mStreamCtrl.getHDMIInSyncStatus().equalsIgnoreCase("true");
            hdmiCameraDevice = new WC_UsbDevice("hdmi", "hdmi-device", "HDMI camera", haveHdmiSync, false, new java.util.HashMap<String,String>());
            usbDeviceList.add(hdmiCameraDevice);
        }

        setActiveDevices(usb3Devices, usb2Devices, hdmiCameraDevice);
    	
    	WC_UsbDevices usbDevices = new WC_UsbDevices(mVideoFormats, mAudioFormats, usbDeviceList);

    	Log.v(TAG, "generateUsbDevices(): usb device list = {"+usbDevices+"}");
    	return usbDevices;
    }
    
    public void setActiveDevices(List<UsbAvDevice> usb3Devices, List<UsbAvDevice> usb2Devices, WC_UsbDevice hdmiCamera)
    {
    	String videoFile = "none";
    	String audioFile = "none";
    	boolean usb3HasMedia = false;
    	boolean usb3HasVideo = false;
    	boolean usb3HasAudio = false;

    	if (usb3Devices != null && !usb3Devices.isEmpty()) {
    		for (UsbAvDevice d : usb3Devices) {
    			// Use USB3 video in preference to USB2 if connected
    			if (d.videoFile != null)
    			{
    				videoFile = d.videoFile;
    				usb3HasVideo = true;
    				Log.i(TAG, "setActiveDevices(): USB3 picked videoFile as "+ videoFile);
    			}
    			if (d.audioFile != null)
    			{
    				audioFile = d.audioFile;	
    				usb3HasAudio = true;
    				Log.i(TAG, "setActiveDevices(): USB3 picked audioFile as "+ audioFile);
    			}
    		}
    	}
    	if (usb2Devices != null && !usb2Devices.isEmpty()) {
			String aFile = null;
			boolean usb2HasVideo = false;
    		for (UsbAvDevice d : usb2Devices) {
    			if (d.videoFile != null)
    				usb2HasVideo = true;
    			if (videoFile.equals("none") && d.videoFile != null)
    			{
    				videoFile = d.videoFile;
    				Log.i(TAG, "setActiveDevices(): USB2 picked videoFile as "+ videoFile);
    				Log.w(TAG, "setActiveDevices(): Avoid Video Capability Peripheral on USB2 - UNSUPPORTED USECASE!!!*****");
    			}
    			if (d.audioFile != null && (aFile == null))
    			{
    				aFile = d.audioFile;	
    				Log.i(TAG, "setActiveDevices(): USB2 picked aFile as "+ aFile);
    			}
    		}

            usb3HasMedia = usb3HasVideo || usb3HasAudio;
            Log.d(TAG, "setActiveDevices: usb3HasMedia = " + usb3HasMedia + " usb2HasVideo "+ usb2HasVideo);
    		// use USB2 audio in preference to USB3 audio if it is connected and is an audio only device
    		if (aFile != null && ( !usb2HasVideo || !usb3HasMedia))
    		{
    			audioFile = aFile;
    			Log.i(TAG, "*****setActiveDevices(): USB2 vs USB3 Logic picked audioFile as "+ audioFile);
    		}
    	}
    	if (hdmiCamera != null)
    	{
    	    videoFile = (hdmiCamera.hasVideo && mStreamCtrl.mDeviceAppConfigRunTimeSettingsIsHdmiInputEnabled == true) ? "/dev/video0" : "none";
    	    //audioFile = "/dev/snd/pcmC2D0c"; // uncomment if HDMI input audio is to be used
    	}

    	if (videoFile != null && !videoFile.equals(mVideoFile))
    	{
    		mVideoFile = videoFile;
            mStreamCtrl.userSettings.setWcVideoCaptureDevice(mVideoFile);
            mVideoFormats = mStreamOut.getVideoFormats(mVideoFile);

    	}
    	if (audioFile != null && !audioFile.equals(mAudioFile))
    	{
    		mAudioFile = audioFile;
            mStreamCtrl.userSettings.setWcAudioCaptureDevice(mAudioFile);
            mAudioFormats = mStreamOut.getAudioFormats(mAudioFile);
    	}
        Log.i(TAG, "WC video capture device is "+mVideoFile+" audio capture device is "+mAudioFile);
    }

    public void setSpeakerDetectedStatus()
    {
        Log.v(TAG, "setSpeakerDetectedStatus() In: speakerDetected="+speakerDetected);
        // update value for speakerDetected
        speakerDetected = false;
        if(mUsbAvDeviceList != null)
        {
            for (UsbAvDevice ds : mUsbAvDeviceList) {
                if(ds.speakerPresent != null){
                    mStreamCtrl.setAudioPlaybackFile(ds.speakerPresent);
                    speakerDetected = true;

                    mStreamCtrl.initPeripheralVolume();
                    break; //If single instance of speaker found, then come out of loop.
                }
            }
        }
        Log.i(TAG, "setSpeakerDetectedStatus(): speakerDetected="+speakerDetected);
    }

    public void getAndReportAllWCStatus()
    {
        Boolean videoFile = false;
        Boolean audioFile = false;
        String camResolution = null;
        String conferencingStatus = null;

        if(mUsbDevices != null)
        {
            for (WC_UsbDevice listdevices : mUsbDevices.devices)
            {
                if(listdevices.hasVideo)
                    videoFile = true;
                if(listdevices.hasAudio)
                    audioFile = true;
            }
        }

        for (WC_VideoFormat vFormats : mVideoFormats)
        {
            //AM3XX-12356: fps need not be displayed by CCUI. Hence fps is not appended.
            camResolution = vFormats.width+"x"+vFormats.height;
        }

        if(!videoFile)
        {
            camResolution = "None";
            cameraFormatSupported = false;
        }
        else if((camResolution == null) || camResolution.startsWith("0x0")) //Occurs when video format detected is MJPG filtered here get_video_caps_from_caps
        {
                Log.w(TAG,"getAndReportAllWCStatus: Unsupported Video format and Resolution found( wxh@fps="+camResolution+" )");
                camResolution = "None";
                cameraFormatSupported = false;
                conferencingStatus = WC_CONF_STATUS_UNAVAILABLE;
                // AM3XX-11622 if HDMI input mode is Camera and HDMI input cable is not connected/ Sync is lost
                // then make camera detected as false
                // CCUI should show Camera detected as 'No'
                // When HDMI sync is false, the videoFile is made to none in setActiveDevices(). 
                // Due this getVideoFormats return resolution as 0x0. Fix for AM3XX-11622 is added here. 

                if (mStreamCtrl.userSettings.getHdmiInMode().equalsIgnoreCase("Camera"))
                    videoFile = false;
        }
        else
            cameraFormatSupported = true;

        // if HDMI input is camera and we have disabled HDMI input then do display camera parameters and 
        // disable conferencing. It overides if there is any USB camera connected
        if (mStreamCtrl.userSettings.getHdmiInMode().equalsIgnoreCase("Camera")
                && mStreamCtrl.mDeviceAppConfigRunTimeSettingsIsHdmiInputEnabled == false )
        {
            Log.w(TAG,"getAndReportAllWCStatus: HDMI input is disabled and HDMI input is camera mode, so not reporting Camera details");
            videoFile = false;
            camResolution = "None";
            cameraFormatSupported = false;
            //conferencingStatus = WC_CONF_STATUS_UNAVAILABLE;
        }
        setSpeakerDetectedStatus();

        if((videoFile || audioFile) && conferencingStatus != WC_CONF_STATUS_IN_USE)
            conferencingStatus = WC_CONF_STATUS_AVAILABLE;
        else if( !(videoFile && audioFile) && conferencingStatus == null)
            conferencingStatus = WC_CONF_STATUS_UNAVAILABLE;

        mWcCresstoreStatus.reportWCDeviceStatus(videoFile,audioFile,speakerDetected,camResolution,conferencingStatus);
    }
}
