package com.crestron.txrxservice;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.EnumSet;
import java.util.Timer;
import java.util.TimerTask;
import java.net.InetAddress;
import java.net.UnknownHostException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.crestron.txrxservice.CresStreamCtrl.AirMediaLoginMode;
import com.crestron.txrxservice.CresStreamCtrl.CrestronHwPlatform;
import com.crestron.txrxservice.CresStreamCtrl.ServiceMode;
import com.crestron.txrxservice.canvas.CresCanvas;
import com.crestron.txrxservice.canvas.Originator;
import com.crestron.txrxservice.canvas.RequestOrigin;
import com.crestron.txrxservice.canvas.Session;
import com.crestron.airmedia.canvas.channels.ipc.CanvasPlatformType;
import com.crestron.airmedia.receiver.m360.IAirMediaReceiver;
import com.crestron.airmedia.receiver.m360.models.AirMediaReceiver;
import com.crestron.airmedia.receiver.m360.models.AirMediaSession;
import com.crestron.airmedia.receiver.m360.models.AirMediaSessionManager;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaPlatforms;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaReceiverLoadedState;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaReceiverMirroringAssist;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaReceiverProperties;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaReceiverResolutionMode;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaReceiverState;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSessionConnectionState;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSessionScreenPosition;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSessionScreenLayout;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSessionStreamingState;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSessionVideoSource;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSessionVideoType;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSize;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSessionInfo;
import com.crestron.airmedia.utilities.ViewBase;
import com.crestron.airmedia.utilities.ViewBase.Size;
import com.crestron.airmedia.utilities.Common;
import com.crestron.airmedia.utilities.delegates.MulticastChangedDelegate;
import com.crestron.airmedia.utilities.delegates.MulticastChangedWithReasonDelegate;
import com.crestron.airmedia.utilities.delegates.MulticastDelegate;
import com.crestron.airmedia.utilities.delegates.MulticastMessageDelegate;
import com.crestron.airmedia.utilities.TimeSpan;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.TextureView;
import android.view.View;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.graphics.Matrix; 
import android.graphics.Canvas;
import android.graphics.Color;

//public class AirMediaSplashtop 
public class AirMediaSplashtop
{
    CresStreamCtrl mStreamCtl;
    Context mContext;
    CresCanvas mCanvas;
    public static final String TAG = "TxRx Splashtop AirMedia"; 
	public final static String licenseFilePath = "/dev/shm/airmedia";
	private boolean surfaceDisplayed = false;
	private boolean doneQuerySenderList = true;
	private int streamIdx = 0;
	private static final boolean DEBUG = true;
	private static final int MAX_USERS = 32;
	private static final int UPDATE_STATUS_AND_TOTAL_USERS = 0x8000;
	private static final int CODEC_ERROR = 1;
	private static final int MEDIA_SERVER_HANG = 2;
	private static final int M360_TIMEOUT = -32001;
	private static final int MIRACAST_API_TIMEOUT = -32002;
	private static final int VIDEOPLAYER_API_TIMEOUT = -32003;
	private static final int VIDEOPLAYER_REMOTE_EXCEPTION = -32004;
	private static final int RECEIVER_FAILED_TO_START_EXCEPTION = -32005;
	private static final int RECEIVER_FAILED_TO_STOP_EXCEPTION = -32006;

	private final Object stopSessionObjectLock = new Object();
	private final Object stopSessionCriticalSectionLock = new Object();
	private final Object disconnectSessionObjectLock = new Object();
	private final Object disconnectSessionCriticalSectionLock = new Object();	
    private final MyReentrantLock orderedLock	= new MyReentrantLock(true); // fairness=true, makes lock ordered
    private final MyReentrantLock airMediaRcvLock = new MyReentrantLock(true); 

    public CountDownLatch serviceConnectedLatch=null;
    public CountDownLatch serviceDisconnectedLatch=null;
    public CountDownLatch receiverLoadedLatch=null;
    public CountDownLatch receiverStoppedLatch=null;
    public CountDownLatch receiverStartedLatch=null;
    public CountDownLatch startupCompleteLatch=null;
    public CountDownLatch deviceDisconnectedLatch=null;
    public CountDownLatch sessionDisconnectedLatch=null;

    public AirMediaSession sessionRequestingStop=null;
    public AirMediaSession sessionRequestingDisconnect=null;

    private Timer pendingSessionTimer=null;
    private Object pendingSessionLock = new Object();
    long sessionPendingTime;
    
    private final Object connectLock = new Object();
    private final Object startStopReceiverLock = new Object();

	private AirMediaReceiver receiver_=null;
	private IAirMediaReceiver service_=null;
	private AirMediaSessionManager manager_=null;
	
	private AirMediaSession active_session_=null;
	private AirMediaSession pending_session_=null;
	private Surface surface_=null;
	private Rect window_= new Rect();
    // next two variables should always be accessed through their getter and setter functions
    // so that they are locked during access and operations on them are atomic.
	private String adapter_ip_address = "None"; // must be initialized as it is used for locking/synchronization
	private String pending_adapter_ip_address = null;
	private String version = null;
	private String productName = null; 
	private int lastReturnedAirMediaStatus = 0;
	private boolean isServiceConnected = false;
	private boolean isReceiverStarted = false;
	private boolean isAirMediaUp = false;
	private boolean requestServiceConnection = false;
	
	private boolean quittingAirMediaService = false;
	
	private String randomKey = MiscUtils.getRandomAlphaNumericString(16);
	
	private String clientDataStatus = "Uninitialized";
	private int clientDataTotalUsers = -1;
	private boolean isAnyWifiUserConnected = true;
    private Handler handler_;
    private Map<Integer, AirMediaSession> userSessionMap = new ConcurrentHashMap<Integer, AirMediaSession>();
    
    private Map<Long, AirMediaSessionStreamingState> videoStateMap = new ConcurrentHashMap<Long, AirMediaSessionStreamingState>();
    private Map<Long, com.crestron.txrxservice.canvas.AirMediaSession> canvasSessionMap = new ConcurrentHashMap<Long, com.crestron.txrxservice.canvas.AirMediaSession>();
    
	Gson gson = new GsonBuilder().create();

    private class MyReentrantLock extends ReentrantLock {
    	MyReentrantLock(boolean fair) {
    		super(fair);
    	}
    	
    	String getThreadId()
    	{
    		Thread owner = this.getOwner();
    		if (owner == null)
    			return "null";
    		else
    			return Integer.toHexString((int)(owner.getId()));
    	}
    	
    	void lock(String s)
    	{
    		Common.Logging.i(TAG, s + " about to take reentrantLock     Current owner tid=" + getThreadId() + "  queueLength=" + getQueueLength());
    		lock();
    		Common.Logging.i(TAG, "reentrantLock taken by " + s + "   Owner tid=" + getThreadId() + "  queueLength=" + getQueueLength());
    	}
    	
    	void unlock(String s)
    	{
    		unlock();
    		Common.Logging.i(TAG, "reentrantLock released by " + s);
    	}
    }
 
    private void removeSessionVideoState(AirMediaSession session)
    {
    	if (session != null)
    	{
    		Long sessionId = session.id();
    		synchronized (videoStateMap) {	
    			if (videoStateMap.containsKey(sessionId))
    			{
    				Common.Logging.i(TAG, "removeSessionVideoState session="+session+"  sessionId="+sessionId);
    				videoStateMap.remove(sessionId);
    			}
    		}
    	}
    }
    
    private AirMediaSessionStreamingState getSessionVideoState(AirMediaSession session)
    {
    	AirMediaSessionStreamingState state = AirMediaSessionStreamingState.Stopped;
    	if (session != null)
    	{
    		Long sessionId = session.id();
    		synchronized (videoStateMap) {	
    			Object v = videoStateMap.get(sessionId);
    			if (v != null)
    			{
    				state = (AirMediaSessionStreamingState) v; 
    			}
    		}
			Common.Logging.i(TAG, "getSessionVideoState session="+session+"  sssionId="+sessionId+"  video streaming state="+state+" rxSvcState="+session.videoState());
    	}
    	return state;
    }
    
    private void setSessionVideoState(AirMediaSession session, AirMediaSessionStreamingState state)
    {
    	if (session != null)
    	{
    		Long sessionId = session.id();
    		synchronized (videoStateMap) {	
    			Common.Logging.i(TAG, "setSessionVideoState session="+session+"  sssionId="+sessionId+"  video streaming state="+state+" rxSvcState="+session.videoState());
    			videoStateMap.put(sessionId, state);
    		}
    	}
    }
    
    public void clearVideoStateMap()
    {
		synchronized (videoStateMap) {	
			if (!videoStateMap.isEmpty()) {
				Common.Logging.w(TAG, "clearVideoStateMap clearing video state map - it should have been empty");
				videoStateMap.clear();
			}
		}
    }
    
    public void launchReceiverService()
    {
    	Common.Logging.i(TAG, "launchReceiverService........");
    	new Thread(new Runnable() {
    		@Override
    		public void run() {
    			orderedLock.lock("launchReceiverService");
    			try {
    		    	boolean serviceSuccessfullyStarted = false;
    		    	while (!serviceSuccessfullyStarted)
    		    	{
    		    		serviceSuccessfullyStarted = connectAndStartReceiverService();
    		    		if (serviceSuccessfullyStarted) {
    		    			Common.Logging.i(TAG, "launchReceiverService exiting receiver=" + receiver() + "  service=" + service() + "  manager=" + manager());
    		    		} else {
    		    			Common.Logging.e(TAG, "launchReceiverService failed, trying again in 5 seconds");
    		    			sleep(5000);
    		    		}
    		    	}
    			} finally {
    				mStreamCtl.airMediaMiracastEnable(mStreamCtl.userSettings.getAirMediaMiracastEnable());
    				orderedLock.unlock("launchReceiverService");
    		    	Common.Logging.i(TAG, "launchReceiverService exit ........");
    			}
    		}
    	}).start();
    }
    
    public AirMediaSplashtop(CresStreamCtrl streamCtl) 
    {
    	mStreamCtl = streamCtl;
    	mContext = (Context)mStreamCtl;
    	mCanvas = mStreamCtl.mCanvas;
    	
    	mStreamCtl.setHostName("");
    	Common.Logging.i(TAG, "HostName="+mStreamCtl.hostName+"   DomainName="+mStreamCtl.userSettings.getDomainName());
    	mStreamCtl.sendAirMediaConnectionInfo();
    	Common.Logging.i(TAG, "IP address from CresStreamSvc: "+mStreamCtl.getAirMediaConnectionIpAddress());
    	set_pending_adapter_ip_address(mStreamCtl.getAirMediaConnectionIpAddress());
    	set_adapter_ip_address();
    	setActiveSession(null);
    	
    	launchReceiverService(); // will do its job asynchronously
    }
    
    public boolean connectAndStartReceiverService()
    {
    	synchronized (connectLock) {
    		boolean successfulStart = true; //indicates that there was no time out condition

	    	Common.Logging.i(TAG, "connectAndStartReceiverService() isServiceConnected="+isServiceConnected+"  isReceiverStarted="+isReceiverStarted);
    		if (!isServiceConnected)
    		{
    			startupCompleteLatch = new CountDownLatch(1);


    			if (!connect2service()) {
    				Common.Logging.e(TAG, "Service failed to connect, restarting AirMedia app");
    				restartAirMedia();
    				return false;
    			}		

    			try { successfulStart = startupCompleteLatch.await(50000, TimeUnit.MILLISECONDS); }
    			catch (InterruptedException ex) { ex.printStackTrace(); }
    			
    			if (!isIpAddrNone(get_adapter_ip_address()) && !isAirMediaUp)
    			{
    				Common.Logging.w(TAG, "Error trying to start receiver");
    				successfulStart = false;
    			}
    			else if (!successfulStart)
    			{
    				Common.Logging.w(TAG, "Timeout trying to start receiver");
    			}
    			
    			if (!successfulStart && isServiceConnected)
    			{
    				Common.Logging.w(TAG, "service connected but unsuccessful start - doUnbindService");
    				doUnbindService();
    			}
    		}
    		else if (!isReceiverStarted)
    		{
    			if (isIpAddrNone(get_adapter_ip_address()))
    			{
    				Common.Logging.i(TAG, "None Adapter selected, not starting receiver");
    				return true;
    			}
    			else
    			{
    				Common.Logging.w(TAG, "Service connected but receiver not started, unbind and try again");
    				doUnbindService();
    				return false;
    			}
    		}
    		else
    			Common.Logging.i(TAG, "Bypassing start because already started");
    		return successfulStart;
    	}    	
    }
    
    private void restartAirMedia()
    {
		mStreamCtl.RestartAirMedia();
    }

    private void RestartAirMediaAsynchronously()
    {
    	Common.Logging.i(TAG, "RestartAirMediaAsynchronously........");
    	new Thread(new Runnable() {
    		@Override
    		public void run() {
    			orderedLock.lock("RestartAirMediaAsynchronously");
    			try {
    				boolean serviceSuccessfullyStarted = false;
    				while (!serviceSuccessfullyStarted)    		    	
    				{
    					if (isServiceConnected)
    					{
    						Common.Logging.e(TAG, "Calling unbind because isServiceConnected when RestartAirMediaAsynchronously was called");
    						doUnbindService();
    					}

    					isAirMediaUp = false;

    					isReceiverStarted = false;
    					// if surface displayed is true and airmedia has been launched then we are showing airmedia window
    					// try to hide it - if we can find an active session.
    					if (surfaceDisplayed && mStreamCtl.userSettings.getAirMediaLaunch(streamIdx))
    					{
    						Common.Logging.e(TAG, "RestartAirMediaAsynchronously(): checking if we have active session");
    						if (getActiveSession() != null) {
        						Common.Logging.e(TAG, "RestartAirMediaAsynchronously(): have session - hiding aidmedia window");
    							hideVideo(useTextureView(getActiveSession()));
    						}
    					}
    	    			surfaceDisplayed = false;
    					removeAllSessionsFromMap(false, "RestartAirMediaAsynchronously()");
    					setActiveSession(null);
    					removePendingSession();
    					// RestartAirMediaAsynchronously is called to force a restart due to disconnect of receiver service.
    					if (receiver() != null && !isServiceConnected)
    					{
    						Common.Logging.i(TAG, "RestartAirMediaAsynchronously(): close");
    						close();
    					}
    					serviceSuccessfullyStarted = connectAndStartReceiverService();
    				}
    			} finally {
    				orderedLock.unlock("RestartAirMediaAsynchronously");
    		    	Common.Logging.i(TAG, "RestartAirMediaAsynchronously exit ........");
    			}
    		}
    	}).start();
    }
    
    private void startAirMedia()
    { 
		Common.Logging.i(TAG, "startAirMedia: Device IP="+mStreamCtl.userSettings.getDeviceIp()+"   Aux IP="+mStreamCtl.userSettings.getAuxiliaryIp());

		set_adapter_ip_address();
		version = mStreamCtl.getAirMediaVersion();
		Common.Logging.i(TAG, "Receiver apk version: " + version);
		productName = mStreamCtl.mProductName + " " + version;
		Common.Logging.i(TAG, "Product Name: " + productName);

		sendClientDataWifiUsers(0);
		// Now start receiver
        if (!startAirMediaReceiver(mStreamCtl.hostName)) {
			Common.Logging.e(TAG, "Receiver failed to startup, returning - restart will be attempted when service disconnects");
			isAirMediaUp = false;
	        startupCompleteLatch.countDown();
			return;
        }

        Common.Logging.i(TAG, "startAirMedia(): setup receiver session manager");
		manager_ = receiver().sessionManager();
		if (manager_ != null)
		{
			registerSessionManagerEventHandlers(manager_);
		}
		// Clear all user status
        Common.Logging.i(TAG, "startAirMedia(): clear all user status");
        removeAllSessionsFromMap(true, "startAirMedia()");

        Common.Logging.i(TAG, "startAirMedia(): initializeDisplay");
		intializeDisplay();
		
		set4in1ScreenEnable(false); // TODO: Remove this when quad view is eventually enabled
    	setActiveSession(null);
        Common.Logging.i(TAG, "startAirMedia(): exit");
        
        isAirMediaUp = true;
        startupCompleteLatch.countDown();
        if (mCanvas != null)
        {
        	Log.i(TAG, "Receiver startup is complete - calling canvasHasStarted()");
        	mCanvas.canvasHasStarted();
        }
    }
    
    private boolean connect2service()
    {
    	requestServiceConnection = true;
    	serviceConnectedLatch = new CountDownLatch(1);
		// start service and instantiate receiver class
		doBindService();
		boolean successfulStart = true; //indicates that there was no time out condition
		try { successfulStart = serviceConnectedLatch.await(20000, TimeUnit.MILLISECONDS); }
		catch (InterruptedException ex) { ex.printStackTrace(); }
		
		return successfulStart;
    }
    
    public boolean airMediaIsUp()
    {
    	return isAirMediaUp;
    }
    
    private void setSessionId(int sessionId)
    {
    	if (sessionId != streamIdx)
    	{
    		Common.Logging.i(TAG, "setSessionId() **** sessionId = " + sessionId + " ****");
    		streamIdx = sessionId;
    	}
    }
    
    // synchronous version of receiver stop - waits for completion
    private boolean stopReceiver()
    {
        synchronized (startStopReceiverLock) {
            Common.Logging.i(TAG, "stopReceiver() enter (thread="+Integer.toHexString((int)(Thread.currentThread().getId()))+")");
            boolean successfulStop = true;

            /*Note: 4-16-2021, the same reason as startReceiver() below.
             **/
            if(mStreamCtl.getAirMediaLicensed()){
                receiverStoppedLatch = new CountDownLatch(1);
                receiver().stop();
                try { successfulStop = receiverStoppedLatch.await(30000, TimeUnit.MILLISECONDS); }
                catch (InterruptedException ex) { ex.printStackTrace(); }
                setActiveSession(null);
                Common.Logging.i(TAG, "stopReceiver() exit (thread="+Integer.toHexString((int)(Thread.currentThread().getId()))+") successfulStop="+successfulStop);
                receiverStoppedLatch = null; // release the latch
            }
            else{
                Common.Logging.i(TAG, "startReceiver: exiting no license");
            }

            return successfulStop;
    	}
    }
    
    // synchronous version of receiver start - waits for completion
    // this function does not handle ip adapter changes that may have occurred - for that use
    // the version that handles it: startReceiverWithPossibleIpAddressChange()
    private boolean startReceiver()
    {
        synchronized (startStopReceiverLock) {
            Common.Logging.i(TAG, "startReceiver() enter (thread="+Integer.toHexString((int)(Thread.currentThread().getId()))+")");
            mStreamCtl.initUpdateStreamStateOnFirstFrame(false);
            boolean successfulStart = true;
    		
            /*Note: 4-16-2021, calling receiver().start() will start/enable airMedia receiver,
             *      see IAirMediaReceiver.aidl file.
             *      The airMedia application runnung on you PC will connect to this program.
             *      So, do not call the program if it is not licensed.
             **/
            if(mStreamCtl.getAirMediaLicensed()){
                receiverStartedLatch = new CountDownLatch(1);
                receiver().start();
                try { successfulStart = receiverStartedLatch.await(45000, TimeUnit.MILLISECONDS); }
                catch (InterruptedException ex) { ex.printStackTrace(); }
                Common.Logging.i(TAG, "startReceiver() exit (thread="+Integer.toHexString((int)(Thread.currentThread().getId()))+") successfulStart="+successfulStart);
            }
            else{
                Common.Logging.i(TAG, "startReceiver: exiting no license");
            }

            return successfulStart;
        }
    }
	
    private void RestartReceiverAynchronously() {
    	new Thread(new Runnable() {
    		@Override
    		public void run() {
        		orderedLock.lock("RestartReceiverAynchronously");
        		try {
        			Common.Logging.i(TAG, "RestartReceiverAynchronously(): Entered");
        			if (receiver() != null && receiver_.state() != AirMediaReceiverState.Stopped)
        			{
        				Common.Logging.i(TAG, "RestartReceiverAynchronously(): Stopping receiver");
        				stopReceiver();			
        			}
        			startReceiver();
        		} finally {
        			orderedLock.unlock("RestartReceiverAynchronously");
        		}
        		Common.Logging.i(TAG, "RestartReceiverAynchronously(): Exited");
    		}
    	}).start();
    }
    
    private boolean isIpAddrNone(String ipaddr)
    {
    	if (ipaddr.equals(""))
    	{
    		Common.Logging.e(TAG, "isIpAddrNone(): empty IP address string");
    		return true;
    	}
    	if (ipaddr.equals("None"))
    		return true;
    	return false;
    }
    
    private String get_pending_adapter_ip_address()
    {
    	synchronized(adapter_ip_address) {
    		return pending_adapter_ip_address;
    	}
    }

    private void set_pending_adapter_ip_address(String addr)
    {
    	synchronized(adapter_ip_address) {
    		pending_adapter_ip_address = addr;
    		Common.Logging.i(TAG, "Pending adapter IP address set to "+pending_adapter_ip_address+
    				", Current adapter IP address is "+adapter_ip_address);
    	}
    }
    
    private String get_adapter_ip_address()
    {
    	synchronized(adapter_ip_address) {
    		return adapter_ip_address;
    	}
    }
    
    private String set_adapter_ip_address()
    {
    	synchronized(adapter_ip_address) {
    		if (pending_adapter_ip_address == null)
    		{
    			pending_adapter_ip_address = mStreamCtl.getAirMediaConnectionIpAddress();
    			Common.Logging.i(TAG, "set_adapter_ip_ddress(): Pending adapter IP address was null, now set to "+pending_adapter_ip_address);
    		}
    		adapter_ip_address = pending_adapter_ip_address;
    		pending_adapter_ip_address = null;
    		Common.Logging.i(TAG, "set_adapter_ip_address(): Current adapter IP address set to "+adapter_ip_address);
    		return adapter_ip_address;
    	}
    }

    private List<String> get_adapter_ip_address_as_list(String ipaddr)
    {
        List<String> ipaddrList = Arrays.asList(ipaddr.split(","));
        return ipaddrList;
    }
    
    private void RestartReceiverForAdapterChange() {
    	new Thread(new Runnable() {
    		@Override
    		public void run() {
    			orderedLock.lock("RestartReceiverForAdapterChange");
    			try {
    				if (get_pending_adapter_ip_address() != null) {
    					Common.Logging.i(TAG, "RestartReceiverForAdapterChange(): Entered");
    					if (receiver() != null && receiver_.state() != AirMediaReceiverState.Stopped)
    					{
    						Common.Logging.i(TAG, "RestartReceiverForAdapterChange(): Stopping receiver");
    						stopReceiver();			
    					}
    					String ipaddr = set_adapter_ip_address();
    					if (!isIpAddrNone(ipaddr)) {
    					    // MSMICE should restart on its own since function wifiVideoPlayer.setAdapterAddress() has been deprecated
//    					    /* Note: adapterAddresses() will ends up calling msMiceSetAdapterAddress(), which will not 
//    					     *       restart msMice if address did not change.
//    					     *       So, send address 0.0.0.0 will force it to restart msMice, since we are 
//    					     *       trying to restart everything here.
//    					     * */
//    						Common.Logging.i(TAG, "RestartReceiverForAdapterChange(): Trying to restart msMice, so setting ip to: 0.0.0.0");
//    						receiver().adapterAddresses(get_adapter_ip_address_as_list("0.0.0.0"));

    						Common.Logging.i(TAG, "RestartReceiverForAdapterChange(): Setting new ip address for receiver: " + ipaddr);
    						receiver().adapterAddresses(get_adapter_ip_address_as_list(ipaddr));
    						startReceiver();
    					}
    				}
    			} finally {
    				orderedLock.unlock("RestartReceiverForAdapterChange");
    			}
    			Common.Logging.i(TAG, "RestartReceiverForAdapterChange(): Exited");
    		}
    	}).start();
    }
    
    private boolean startAirMediaReceiver(final String serverName)
    {
		boolean successfulStart = true; //indicates receiver was successfully loaded

		if (receiver_ != null) {
			// close any prior receiver if it exists
			close();
		}
		if (receiver_ == null) {
        	try {
        		receiver_ = new AirMediaReceiver(service_);
        		
                receiver().serverName(serverName);
                receiver().product(productName);
                Common.Logging.i(TAG, "startAirMediaReceiver: get list of ip addresses for receiver= " + get_adapter_ip_address());
                receiver().adapterAddresses(get_adapter_ip_address_as_list(get_adapter_ip_address()));
                setAirMediaReceiverMaxResolution();
                Point dSize = mStreamCtl.getDisplaySize();
                receiver().displayResolution(new AirMediaSize(dSize.x, dSize.y));
                setAirMediaMiracast(mStreamCtl.userSettings.getAirMediaMiracastEnable());
                setAirMediaMiracastWifiDirectMode(mStreamCtl.userSettings.getAirMediaMiracastWifiDirectMode());
                setAirMediaMiracastMsMiceMode(mStreamCtl.userSettings.getAirMediaMiracastMsMiceMode());
                setAirMediaMiracastWirelessOperatingRegion(mStreamCtl.userSettings.getAirMediaMiracastWirelessOperatingRegion());
                setAirMediaIsCertificateRequired(mStreamCtl.userSettings.getAirMediaIsCertificateRequired());
                setAirMediaOnlyAllowSecureConnections(mStreamCtl.userSettings.getAirMediaOnlyAllowSecureConnections());
                setAirMediaChromeExtension(mStreamCtl.userSettings.getAirMediaChromeExtension());
                setAirMediaDiscoveryEnable(mStreamCtl.userSettings.getAirMediaDiscoveryEnable());
                setAirMediaWifiFrequencyBand(mStreamCtl.userSettings.getAirMediaWifiFrequencyBand());
                setAirMediaWifiEnabled(mStreamCtl.userSettings.getAirMediaWifiEnabled());
                setAirMediaWifiSsid(mStreamCtl.userSettings.getAirMediaWifiSsid());
                setAirMediaWifiKey(mStreamCtl.userSettings.getAirMediaWifiPskKey());

        		registerReceiverEventHandlers(receiver());
        		Common.Logging.i(TAG, "Registering receiver with videoplayer");
        		mStreamCtl.wifidVideoPlayer.register(receiver_);

        		if (receiver().loaded() != AirMediaReceiverLoadedState.Loaded)
        		{
        			Common.Logging.i(TAG,"startAirMediaReceiver: loading receiver");
        			receiverLoadedLatch = new CountDownLatch(1);
        			Common.Logging.i(TAG,"Calling receiver.initalize()");
        			receiver().initialize();        		
        			try { successfulStart = receiverLoadedLatch.await(30000, TimeUnit.MILLISECONDS); }
        			catch (InterruptedException ex) { ex.printStackTrace(); }
        			Common.Logging.i(TAG,"startAirMediaReceiver: receiverLoading success="+successfulStart);
        			Common.Logging.i(TAG,"receiver is in " + receiver().loaded() + " state");
        		} else {
        			Common.Logging.i(TAG,"Not calling receiver.initalize() because receiver is already loaded");
        		}
        	} 
        	catch (Exception ex)
        	{
        		Common.Logging.e(TAG, "Exception trying to create receiver " + ex);
        		successfulStart = false;
        	}
        	finally
        	{
        		Common.Logging.i(TAG, "Receiver state = " + receiver().loaded());
        	}
        }
        if (successfulStart)
        {
        	if (receiver().state() != AirMediaReceiverState.Stopped) {
        		Common.Logging.i(TAG,"startAirMediaReceiver: Receiver found to be in Started state - calling stop to 'restart' it");
        		receiver().sessionManager().clear();
        		removeAllSessionsFromMap(false, "startAirMediaReceiver() - clearing prior sessions");
        		stopReceiver();
        	}
        	if (!isIpAddrNone(get_adapter_ip_address()))
        	{
            	Common.Logging.i(TAG, "startAirMediaReceiver(): Starting AirMedia Receiver");
        		successfulStart = startReceiver();
        	}
        	else
        	{
            	Common.Logging.i(TAG, "startAirMediaReceiver(): Not starting AirMedia Receiver because adapter ip address is 'None'");
        	}
        }
        Common.Logging.i(TAG,"startAirMediaReceiver exiting with rv="+successfulStart);
		return successfulStart;
    }
    
    private void showVideo(boolean use_texture)
    {
		surfaceDisplayed = true;
    	Common.Logging.i(TAG, "showVideo: Splashtop Window showing " + ((use_texture) ? "TextureView" : "SurfaceView") + "    surfaceDisplayed=" + getSurfaceDisplayed());
    	mStreamCtl.showSplashtopWindow(streamIdx, use_texture);
    }
    
    private void hideVideo(boolean use_texture)
    {
    	Common.Logging.i(TAG, "hideVideo: Splashtop Window hidden " + ((use_texture) ? "TextureView" : "SurfaceView") + "    surfaceDisplayed=" + getSurfaceDisplayed());
    	if (getSurfaceDisplayed())
    	{
    		mStreamCtl.hideSplashtopWindow(streamIdx, use_texture);
    		surfaceDisplayed = false;
    	}
    	else
    	{
    		// Bug 139366 - do not hide window if AirMedia not showing - streaming may be using it and we could destroy the surface
        	Common.Logging.i(TAG, "hideVideo(): hideSplashtopWindow not called because surfaceDisplayed is false so AirMedia not showing");
    	}
    }
    
    
    public void onDestroy(){
    	Common.Logging.i(TAG, "AirMediaSplashtop Class destroyed!!!");

    	surfaceDisplayed = false;
    	Common.Logging.i(TAG, "surfaceDisplayed=" + getSurfaceDisplayed());

    	shutDownAirMediaSplashtop();
    }

    public void shutDownAirMediaSplashtop(){
    	doUnbindService();
    }
    
    public void setOrderedLock(boolean lock, String functionName)
    {
    	if (lock) {
    		orderedLock.lock(functionName);
    	} else {
    		orderedLock.unlock(functionName);
    	}
    }
    
    public void setAirMediaReceiverMaxResolution()
    {
        AirMediaReceiverResolutionMode res = AirMediaReceiverResolutionMode.Max1080P;
    	Common.Logging.i(TAG, "setAirMediaReceiverMaxResolution(): max resolutions set to "+res);
        receiver().maxResolution(res);
    }
    
	public void setAirMediaMiracast(boolean enable)
	{
		Common.Logging.i(TAG, "setAirMediaMiracast: " + enable);
		if (receiver() != null)
		{
			receiver().configureProperty(AirMediaReceiverProperties.Miracast.Enable, enable);
		}
	}

    public void setAirMediaMiracastWifiDirectMode(boolean enable)
    {
		Common.Logging.i(TAG, "setAirMediaMiracastWifiDirectMode(): " + enable);
		if (receiver() != null)
		{
			receiver().configureProperty(AirMediaReceiverProperties.Miracast.AllowWifiDirectConnections, enable);
		}
    }
    
    public void setAirMediaMiracastMsMiceMode(boolean enable)
    {
		Common.Logging.i(TAG, "setAirMediaMiracastMsMiceMode(): " + enable);
		if (receiver() != null)
		{
			receiver().configureProperty(AirMediaReceiverProperties.Miracast.AllowMsMiceConnections, enable);
		}
    }
    
    public void setAirMediaMiracastPreferWifiDirect(boolean enable)
    {
		Common.Logging.i(TAG, "setAirMediaMiracastPreferWifiDirect(): " + enable);
		if (receiver() != null)
		{
			receiver().configureProperty(AirMediaReceiverProperties.Miracast.PreferWifiDirect, enable);
		}
    }
    
    public void setAirMediaMiracastWirelessOperatingRegion(int regionCode)
    {
    	String[] regions = {"00", "US", "JA"};
		Common.Logging.i(TAG, "setAirMediaMiracastWirelessOperatingRegion(): " + regionCode);
		if (receiver() != null)
		{
			if (regionCode >= regions.length)
			{
				Common.Logging.i(TAG, "setAirMediaMiracastWirelessOperatingRegion(): invalid region code: " + regionCode);
			}
			else
			{
				receiver().configureProperty(AirMediaReceiverProperties.Miracast.WifiDirectCountryCode, regions[regionCode]);
			}
		}
	}
    
    public void setAirMediaIsCertificateRequired(boolean enable)
    {
		Common.Logging.i(TAG, "setAirMediaIsCertificateRequired: " + enable);
		if (receiver() != null)
		{
			receiver().configureProperty(AirMediaReceiverProperties.Splashtop.UseThirdPartyCertificate, enable);
		}
    }

    public void setAirMediaOnlyAllowSecureConnections(boolean enable)
    {
		Common.Logging.i(TAG, "setAirMediaOnlyAllowSecureConnections: " + enable);
		if (receiver() != null)
		{
			receiver().configureProperty(AirMediaReceiverProperties.Splashtop.SecureChannelOnly, enable);
		}
    }
    
    public void setAirMediaChromeExtension(boolean enable)
    {
		Common.Logging.i(TAG, "setAirMediaChromeExtension: " + enable);
		if (receiver() != null)
		{
			receiver().configureProperty(AirMediaReceiverProperties.Splashtop.AllowChromeExtension, enable);
		}
    }
    
    public void setAirMediaDiscoveryEnable(boolean enable)
    {
		Common.Logging.i(TAG, "setAirMediaDiscoveryEnable: " + enable);
		if (receiver() != null)
		{
			receiver().bonjour(enable);;
		}
    }
    
    public void setAirMediaWifiEnabled(boolean enable)
    {
		Common.Logging.i(TAG, "setAirMediaWifiEnabled: " + enable);
		if (receiver() != null)
		{
			receiver().configureProperty(AirMediaReceiverProperties.WirlessAccessPoint.Enable, enable);
		}
    }
    
    public void setAirMediaWifiSsid(String ssid)
    {
		Common.Logging.i(TAG, "setAirMediaWifiSsid: " + ssid);
		if (receiver() != null)
		{
            Common.Logging.d(TAG,"configureStringProperty " + AirMediaReceiverProperties.WirlessAccessPoint.WifiSsid + "=" + ssid);
			receiver().configureProperty(AirMediaReceiverProperties.WirlessAccessPoint.WifiSsid, ssid);
		}
    }
    
    public void setAirMediaWifiKey(String key)
    {
		if (receiver() != null)
		{
			if (key.isEmpty())
				key = randomKey;
            Common.Logging.d(TAG,"configureStringProperty " + AirMediaReceiverProperties.WirlessAccessPoint.WifiKey);
			receiver().configureProperty(AirMediaReceiverProperties.WirlessAccessPoint.WifiKey, key);
		}
    }
    
    public void setAirMediaWifiFrequencyBand(int val)
    {
    	int freq;
		if (receiver() != null)
		{
			switch (val) {
			case 0: freq = 2; break;
			case 1: freq = 5; break;
			default:
				Common.Logging.d(TAG, "setAirMediaWifiFrequencyBand(): Invalid wifi mode value: "+val);
				return;
			}
            Common.Logging.d(TAG,"configureStringProperty " + AirMediaReceiverProperties.WirlessAccessPoint.WifiFrequency + "=" + freq);
			receiver().configureProperty(AirMediaReceiverProperties.WirlessAccessPoint.WifiFrequency, freq);
		}
    }
    
    public void recover(){
    	for (int sessId=0; sessId < CresStreamCtrl.NumOfSurfaces; sessId++)
    	{
    		if (mStreamCtl.userSettings.getAirMediaLaunch(sessId))
    		{
    			Common.Logging.i(TAG, "recover(): calling hide");
    			hide(sessId, true);	// Need to stop sender in order to recover
    			
    			try { Thread.sleep(5000); } catch (InterruptedException e) {};	

    			if (getPendingSession() != null)
    			{
    				int width = mStreamCtl.userSettings.getAirMediaWidth();
    				int height = mStreamCtl.userSettings.getAirMediaHeight();

    				if ((width == 0) && (height == 0))
    				{
    					Point size = mStreamCtl.getDisplaySize();

    					width = size.x;
    					height = size.y;
    				}
    				Common.Logging.i(TAG, "recover(): calling show");
    				show(sessId, mStreamCtl.userSettings.getAirMediaX(), mStreamCtl.userSettings.getAirMediaY(),width,height);
    			}
    		}
    	}
    }    
    
    public void show(int sessionId, int x, int y, int width, int height)
    {
    	if (mCanvas != null) return;
    	orderedLock.lock("show");
    	setSessionId(sessionId);
    	try {
    		Rect window = new Rect(x, y, x+width-1, y+height-1);
    		if (surfaceDisplayed == false || !MiscUtils.rectanglesAreEqual(window_, window))
    		{	    		    	
    			Common.Logging.i(TAG, "show: Show window " +sessionId + " " + window);

    			// if we have a pending session let it take over now
    			makePendingSessionActive();
    			
    			//show surface
    			setVideoTransformation();
    			
    			if (mStreamCtl.userSettings.getAirMediaLaunch(sessionId))
    			{
    				// show window provided someone upstream has requested it
    				showVideo(useTextureView(getActiveSession()));
    			}

    			if (getActiveSession() != null)
    			{
    				if (getActiveSession().videoSurface() == null)
    				{
    					Common.Logging.i(TAG, "show: calling attachSurface");
    					attachSurface();	
    				} 
    				else
    				{
    					Common.Logging.w(TAG, "show: have active session - but already has surface");    				
    				}
    			}
    		}
    		else
    			Common.Logging.i(TAG, "show: AirMedia already shown, ignoring request");
    	} finally {
    		orderedLock.unlock("show");
    	}
    } 
    
    public void hide(int sessionId, boolean sendStopToSender, boolean clear)
    {
    	if (mCanvas != null) return;
    	orderedLock.lock("hide");
    	setSessionId(sessionId);
    	try {
    		if (surfaceDisplayed == true)
    		{
    			// Invalidate rect on hide
    			window_ = new Rect();    		

    			detachSurface();

    			if (clear && Boolean.parseBoolean(mStreamCtl.hdmiOutput.getSyncStatus()))
    			{
    				clearSurface();			
    			}

    			if (getActiveSession() != null)
    				hideVideo(useTextureView(getActiveSession()));

    			surfaceDisplayed = false;

    			if (sendStopToSender) {   			
    				stopAllButPendingSenders(); // Inform all non-pending senders that stream is stopped/hidden
    			}
    		}
    		else
    			Common.Logging.i(TAG, "hide: AirMedia already hidden, ignoring request");
    	} finally {
    		orderedLock.unlock("hide");
    	}
    }
    
    public void hide(int sessionId, boolean sendTopToSender)
    {
    	hide(sessionId, sendTopToSender, true);
    }
    
    private int session2user(/*@NonNull */AirMediaSession session)
    {
    	int senderId = -1;
    	if (session != null)
    	{
    		for (Map.Entry<Integer, AirMediaSession> e : userSessionMap.entrySet()) 
    		{
    			if ((AirMediaSession)e.getValue() == session)
    			{
    				senderId = e.getKey();
    				break;
    			}
    		}
    	}
    	
    	return senderId;
    }
    
    private AirMediaSession user2session(int senderId)
    {
    	AirMediaSession session = null;
    	for (Map.Entry<Integer, AirMediaSession> e : userSessionMap.entrySet()) 
    	{
    		if ((int)e.getKey() == senderId)
    		{
    			session = e.getValue();
    			break;
    		}
    	}
    	
    	return session;
    }
    
    private int addSessionToMap(AirMediaSession session)
    {
    	int senderId = -1;
    	
    	// Check if already added first
    	senderId = session2user(session);
    	if (senderId != -1)
    		return senderId;
    	
    	// Add to map
    	for (int i = 1; i <= MAX_USERS; ++i)
    	{
    		if (!userSessionMap.containsKey(i))
    		{
    			userSessionMap.put(i, session); 
    			senderId = i;
    			break;
    		}
    	}    	
    	
    	if (senderId == -1)
    	{
    		Common.Logging.w(TAG, "Max number of AirMedia senders reached!");
    	}
    	
    	return senderId;
    }
    
    private void removeSessionFromMap(AirMediaSession session)
    {
    	int senderId = -1;
    	for (Map.Entry<Integer, AirMediaSession> e : userSessionMap.entrySet()) 
    	{    	  
    	    if ((AirMediaSession)e.getValue() == session)
    	    {    	    	
    	    	senderId = e.getKey();
    	    	break;
    	    }
    	}
    	
    	if (senderId != -1)
    		userSessionMap.remove(senderId);
    }
    
    // forceSendingFeeback=true implies send feedback for all users - even if no session exists for user
    // calledFrom string is for debug purposes - so log can show where the function was called from
    private void removeAllSessionsFromMap(boolean forceSendingFeedback, String calledFrom)
    {
		Common.Logging.i(TAG, calledFrom+": removeAllSessions");
    	for (int user = 1; user <= MAX_USERS; user++) // We handle airMedia user ID as 1 based
    	{
    		AirMediaSession session = user2session(user);
    		if (session != null) {
    			Common.Logging.i(TAG, "removing session for user "+user+" from map");
    			userSessionMap.remove(user);
    		}
    		if (forceSendingFeedback || session != null) {
                mStreamCtl.userSettings.setAirMediaUserConnected(false, user);
                mStreamCtl.sendAirMediaUserFeedbacks(user, "", "", 0, false);
    		}
    	}
    	canvasSessionMap.clear();
    	removeAllConnectedClients();
    	querySenderList(false); // force sending of AIRMEDIA_STATUS=0
    }
    
    public boolean getSurfaceDisplayed()
    {
    	return surfaceDisplayed;
    }

    public boolean useTextureView(AirMediaSession session)
    {
    	if (session == null)
    	{
    		Common.Logging.w(TAG, "useTextureView: called with a null session");
    		return false;
    	}
    	if (session.info() == null)
    	{
    		Common.Logging.w(TAG, "useTextureView: called with null session info (session="+session+")");
    		return false;
    	}
    	return (session.info().isRotationSupported && !session.info().isRotationManagedBySender) ? true : false;
    }
    
    private static int normalizeRotation(int rotation) {
        if (rotation > 360) rotation = rotation % 360;
        else if (rotation < 0) rotation = (rotation % 360) + 360;
        if (rotation >= 315 || rotation <= 45) return 0;
        if (rotation >= 135 && rotation <= 225) return 180;
        if (rotation > 45 && rotation < 135) return 90;
        return 270;
    }

    private void setVideoTransformation()
    {
    	if (getActiveSession() != null)
    	{
    		boolean use_texture_view = useTextureView(getActiveSession());
    		if (use_texture_view)
    		{
    			setVideoTransformation(streamIdx, use_texture_view);
    		}
    		else
    		{
        		int width = mStreamCtl.userSettings.getAirMediaWidth();
    			int height = mStreamCtl.userSettings.getAirMediaHeight();
    			
    			if ((width == 0) && (height == 0))
    			{
    				Point size = mStreamCtl.getDisplaySize();

    				width = size.x;
    				height = size.y;
    			}
    			Rect r = MiscUtils.getAspectRatioPreservingRectangle(
    					mStreamCtl.userSettings.getAirMediaX(), mStreamCtl.userSettings.getAirMediaY(), width, height,
    					getActiveSession().videoResolution().width, getActiveSession().videoResolution().height);
    			setSurfaceSize(r.left, r.top, r.width(), r.height());
    		}
    	}
    }

    public void log_matrix(Matrix m)
    {
    	float[] values;
    	
    	values = new float[9];
    	m.getValues(values);
    	Common.Logging.i(TAG, "Matrix = { " + values[0] + "   " + values[1] + "   " + values[2]);
    	Common.Logging.i(TAG, "           " + values[3] + "   " + values[4] + "   " + values[5]);
    	Common.Logging.i(TAG, "           " + values[6] + "   " + values[7] + "   " + values[8] + "}");
   	}

    public void setVideoTransformation(int x, int y, int w, int h) {
    	
    	AirMediaSession session = getActiveSession();
    	TextureView textureView = null;
    	
    	if (session == null)
    		return;
    			
    	if (!useTextureView(session))
    		return;
    	
        final float viewWidth = (float)w;
        final float viewHeight = (float)h;

        final float videoWidth = (float)session.videoResolution().width;
        final float videoHeight = (float)session.videoResolution().height;

        int videoRotation = normalizeRotation(session.videoRotation());

        final boolean isLandscape = (videoRotation == 90 || videoRotation == 270);

        final float scale = isLandscape
                ? Math.min(viewWidth / videoHeight, viewHeight / videoWidth)
                : Math.min(viewWidth / videoWidth, viewHeight / videoHeight);
        final float scaleX = isLandscape
                ? (videoHeight * scale) / viewHeight
                : (videoWidth * scale) / viewWidth;
        final float scaleY = isLandscape
                ? (videoWidth * scale) / viewWidth
                : (videoHeight * scale) / viewHeight;
        Common.Logging.i(TAG, "setTransformation isLandscape="+isLandscape+"    scale"+scale+"  scaleX="+scaleX+"    scaleY="+scaleY );

        final float viewCenterX = viewWidth / 2.0f;
        final float viewCenterY = viewHeight / 2.0f;

        Common.Logging.i(TAG, "setTransformation  " + AirMediaSession.toDebugString(getActiveSession()) + "  view=" + w + "x" + h + " @(" + x + "," + y + ")  video=" + session.videoResolution() + "  rotate=" + videoRotation + "°" + "  scale=" + scaleX + " , " + scaleY);

    	textureView = mStreamCtl.getTextureView(streamIdx);
    	if (textureView != null)
    	{
    		Matrix matrix = textureView.getTransform(null);
    		matrix.setRotate(videoRotation, viewCenterX, viewCenterY);
    		matrix.postScale(scaleX, scaleY, viewCenterX, viewCenterY);
    		//log_matrix(matrix);
    		textureView.setTransform(matrix);
    		mStreamCtl.dispSurface.forceParentLayoutInvalidation();
    	}
    }

    public void setVideoTransformation(int sessionId, boolean use_texture_view)
    {
    	if (mStreamCtl.serviceMode == ServiceMode.Slave)
    		return;
    	if (!use_texture_view)
    		return;
    	if (mStreamCtl.mAirMedia == null || !(mStreamCtl.mAirMedia instanceof AirMediaSplashtop))
    		return;
    	Common.Logging.i(TAG, "setVideoTransformation " + sessionId + " : Lock");
        mStreamCtl.dispSurface.lockWindow(sessionId);
        try
        {
        	int tmpWidth = mStreamCtl.userSettings.getW(sessionId);
        	int tmpHeight = mStreamCtl.userSettings.getH(sessionId);
        	int tmpX = mStreamCtl.userSettings.getXloc(sessionId);
        	int tmpY = mStreamCtl.userSettings.getYloc(sessionId);
        	if ((tmpWidth == 0) && (tmpHeight == 0))
        	{
        		Point size = mStreamCtl.getDisplaySize();
        		tmpWidth = size.x;
        		tmpHeight = size.y;
        	}
        	Common.Logging.i(TAG, "setVideoTransformation x="+String.valueOf(tmpX)+" y="+String.valueOf(tmpY) + " w=" + String.valueOf(tmpWidth) + " h=" + String.valueOf(tmpHeight));

        	// Needs to be final so that we can pass to another thread
        	final int width = tmpWidth;
        	final int height = tmpHeight;
        	final int x = tmpX;
        	final int y = tmpY;
        	
            if (mStreamCtl.mAirMedia != null)
            {
            	// Make sure view transform changes are only done in UI (main) thread
            	if (Looper.myLooper() != Looper.getMainLooper())
            	{
	            	final CountDownLatch latch = new CountDownLatch(1);
	
	            	mStreamCtl.runOnUiThread(new Runnable() {
	            		@Override
	            		public void run() {
	            			setVideoTransformation(x, y, width, height);		       		    	 
	            			latch.countDown();
	            		}
	            	});	            	

	            	try { 
	            		if (latch.await(15, TimeUnit.SECONDS) == false)
	            		{
	            			Common.Logging.e(TAG, "setVideoTransform: Timeout after 15 seconds");
	            			mStreamCtl.RecoverTxrxService();
	            		}
	            	}
	            	catch (InterruptedException ex) { ex.printStackTrace(); }  
            	}
            	else
            	{
        			setVideoTransformation(x, y, width, height);		       		    	 
            	}
            }
        }
        finally
        {
        	mStreamCtl.dispSurface.unlockWindow(sessionId);
        	Common.Logging.i(TAG, "setVideoTransformation " + sessionId + " : Unlock");
        }
    }

    private void attachSurface() {
		Common.Logging.i(TAG, "Entered attachSurface");
    	SurfaceHolder surfaceHolder=null;
    	Surface surface = null;
    	SurfaceTexture surfaceTexture = null;
    	AirMediaSession session = getActiveSession();
    	if (session == null) {
    		Common.Logging.i(TAG, "Returning without attaching since no active session");
    		return;
    	}
    	AirMediaReceiver receiver = receiver();
    	if (receiver == null) {
    		Common.Logging.i(TAG, "Returning without attaching since receiver is null");
    		return;
    	}
    	if (!getSurfaceDisplayed()) {
    		Common.Logging.i(TAG, "Returning without attaching since getSurfaceDisplayed is false");
    		return;
    	}
    	if (session.videoSurface() != null)
    	{
			Common.Logging.i(TAG, "attachSurface: detach prior surface");
    		session.detach();
    	}
    	if (!useTextureView(session))
    	{
    		surfaceHolder = mStreamCtl.dispSurface.GetSurfaceHolder(streamIdx);
    		if (surfaceHolder != null)
    			surface = surfaceHolder.getSurface();
    		//surfaceHolder_.addCallback(video_surface_callback_handler_);
    	} else {
    		surfaceTexture = mStreamCtl.getSurfaceTexture(streamIdx);
    		if (surfaceTexture != null)
    			surface = new Surface(surfaceTexture);
    	}

    	// Fix bug : once surface is attached, first frame can come in, so make sure dimensions are reset prior to attaching
		// Reset video dimensions so that when we get first set of non-zero video dimensions start message sent to PPUX
		mStreamCtl.setVideoDimensions(streamIdx, 0, 0);
		mStreamCtl.setUpdateStreamStateOnFirstFrame(streamIdx, true);

		if ((surface != null) && surface.isValid())
		{
			if (session.videoSurface() != surface) {
		    	surface_ = surface;
				Common.Logging.i(TAG, "attachSurface: attaching surface: " + surface + " to session: " + AirMediaSession.toDebugString(session));
				session.attach(surface);
				Common.Logging.i(TAG, "attachSurface: attached surface");
			}
		} else {
			if (surfaceHolder != null)
				surfaceHolder.addCallback(video_surface_callback_handler_);
			Common.Logging.i(TAG, "attachSurface: No valid surface at this time");
		}

		Common.Logging.i(TAG, "attachSurface: calling setVideoTransform");
		setVideoTransformation();

		Common.Logging.i(TAG, "attachSurface: calling setInvalidateOnSurfaceTextureUpdate");
		mStreamCtl.dispSurface.setInvalidateOnSurfaceTextureUpdate(true);

		Common.Logging.i(TAG, "Exit from attachSurface");
    }
    
    // TODO move into CresDisplaySurface
    private void clearSurface()
    {
    	Canvas canvas = null;
    	Surface s = null;
    	SurfaceTexture surfaceTexture = null;

    	Common.Logging.i(TAG, "clearSurface(): Fetching surfacetexture");
    	surfaceTexture = mStreamCtl.getSurfaceTexture(streamIdx);
    	if (surfaceTexture != null)
    		s = new Surface(surfaceTexture);
    	if (s == null) {
    		Common.Logging.i(TAG, "null surface obtained from SurfaceTexture - nothing to clear");
    		return;
    	}
    	Common.Logging.i(TAG, "clearSurface: clearing surface: " + s);
    	TextureView textureView = mStreamCtl.dispSurface.GetTextureView(streamIdx);
    	Rect rect = new Rect(0, 0, textureView.getWidth(), textureView.getHeight());    	
        try {
            try {
                Common.Logging.i(TAG, "clearSurface locking canvas");
                canvas = s.lockCanvas(null);
                if (canvas != null) canvas.drawColor(Color.BLACK);
            } finally {
                if (canvas != null) s.unlockCanvasAndPost(canvas);
            }
        } catch (Exception e) {
            Common.Logging.e(TAG, "clearSurface:  EXCEPTION  " + e);
        }
    	s.release();
   	 	Common.Logging.i(TAG, "clearSurface: released surface: " + s);
    }

    private void sleep(int msec)
    {
    	try        
    	{
    		Thread.sleep(msec);
    	} 
    	catch(InterruptedException ex) 
    	{
    		Thread.currentThread().interrupt();
    	}
    }
    
    private void detachSurface() {
    	 AirMediaSession session = getActiveSession();
    	 if (session == null) return;
    	 AirMediaReceiver receiver = receiver();
    	 if (receiver == null) return;

    	 Common.Logging.i(TAG, "detachSurface: detaching surface from session: " + AirMediaSession.toDebugString(session));
    	 if (session.videoSurface() != null) {
    		 session.detach();
        	 // Must release surface after detaching
    		 if (useTextureView(session))
    		 {
    			 // release surface if we were using textureview
    	    	 Common.Logging.i(TAG, "detachSurface: releasing surface: " + surface_);
    	    	 if (surface_ != null)
    	    		 surface_.release();
    		 }
			 surface_ = null;
    	 }
    }

    private void cancelPendingSessionTimer()
    {
		if (pendingSessionTimer != null) 
		{
			Common.Logging.i(TAG, "canceling pending session timer");  	
			pendingSessionTimer.cancel();
			pendingSessionTimer = null;
		}    	
    }
    
    private void removePendingSession()
    {
		Common.Logging.v(TAG, "removePendingSession - entered");  	
		if (getPendingSession() != null)
		{
			Common.Logging.i(TAG, "removePendingSession: removing pending session " + AirMediaSession.toDebugString(getPendingSession()));  	
			getPendingSession().stop();
			pending_session_ = null;
			
		}
		cancelPendingSessionTimer();
		Common.Logging.v(TAG, "removePendingSession - exit");  	
	}

	private class setNoPendingSession extends TimerTask
	{
		@Override
		public void run() {
			synchronized (pendingSessionLock)
			{
				Common.Logging.i(TAG, "----- removing pending session due to scheduled timeout -----");
				removePendingSession();
			}
		}
	}

	private void setPendingSession(AirMediaSession session)
	{
		synchronized (pendingSessionLock) {
			if ((session != null) && session == getPendingSession()) {
				Common.Logging.i(TAG, "setPendingSession: same session is already pending " + AirMediaSession.toDebugString(session)); 
				return;
			}
			Common.Logging.i(TAG, "setPendingSession: calling remove pending session");  	
			removePendingSession();
			if (session == null) {
				return;
			}
			Common.Logging.i(TAG, "setPendingSession: setting pending session to  " + AirMediaSession.toDebugString(session));  	
			pending_session_ = session;
			pendingSessionTimer = new Timer();
			pendingSessionTimer.schedule(new setNoPendingSession(), 20000);
			sessionPendingTime = System.currentTimeMillis();
		}
    }
    
    
    private void makePendingSessionActive()
    {
		synchronized (pendingSessionLock) {
    		Common.Logging.i(TAG, "makePendingSessionActive: pending session=" + AirMediaSession.toDebugString(getPendingSession()) + 
					"  active session=" + AirMediaSession.toDebugString(getActiveSession()));
			if (getPendingSession() == null) {
				return;
			}
			if (getActiveSession() != null) {
				return;
			}

			setActiveSession(getPendingSession());
			pending_session_ = null;
			cancelPendingSessionTimer();
			long now = System.currentTimeMillis();
			Common.Logging.i(TAG,"Session "+AirMediaSession.toDebugString(getActiveSession())+
					" made active in "+(now-sessionPendingTime)+" msec.");
		}
    }
    
    
    // add an active session - give it the surface and show the video
    private void addActiveSession(AirMediaSession session)
    {
    	boolean wasShowing=false;
    	boolean priorActiveSessionExists = (getActiveSession() != null);

    	// if we already have an active session, then check if it is same
    	if (AirMediaSession.isEqual(getActiveSession(), session))
    	{
        	Common.Logging.i(TAG, "addActiveSession: already have same session active: " + AirMediaSession.toDebugString(session));
    		return;
    	}
		synchronized (pendingSessionLock) {
			if (AirMediaSession.isEqual(getPendingSession(), session))
			{
				Common.Logging.i(TAG, "addActiveSession: already have same session pending: " + AirMediaSession.toDebugString(session));
				return;
			}
		}
    	
    	// take surface away from active session if one exists
		wasShowing = getSurfaceDisplayed();
    	if (priorActiveSessionExists)
    	{
        	Common.Logging.i(TAG, "addActiveSession: removing prior active session " + AirMediaSession.toDebugString(getActiveSession()));
    		deleteActiveSession(getActiveSession(), false);
    	}
    	
    	if (wasShowing) {
    		setActiveSession(session);
    		Common.Logging.i(TAG, "addActiveSession: setting active session " + AirMediaSession.toDebugString(getActiveSession()));  	
			// update window dimensions - may be going to new surfaceView or TextureView
	    	setSurfaceSize();
	    	// show the new surfaceView or TextureView
			showVideo(useTextureView(session));
		} else {
			synchronized (pendingSessionLock) {
				Common.Logging.i(TAG, "addActiveSession: calling setPendingSession with " + AirMediaSession.toDebugString(session));  	
				setPendingSession(session);
			}
		}

    	mStreamCtl.setUpdateStreamStateOnFirstFrame(streamIdx, false);
		attachSurface();
    	
 		// Send status of 1 to indicate video is playing
		querySenderList(false);
    } 
    
	// delete active session - take away its surface and hide the video from it
    private void deleteActiveSession(AirMediaSession session, boolean sendFeedbackForSession)
    {
    	Common.Logging.i(TAG, "deleteActiveSession: entering active session=" + AirMediaSession.toDebugString(getActiveSession()));

    	if (session == null)
    	{
        	Common.Logging.w(TAG, "requesting removal of null session");
        	return;
    	}
		synchronized (pendingSessionLock) {
			if (AirMediaSession.isEqual(getPendingSession(), session))
			{
				Common.Logging.w(TAG, "remove pending session " + AirMediaSession.toDebugString(session));
				pending_session_ = null;
				cancelPendingSessionTimer();
	        	Common.Logging.i(TAG, "deleteActiveSession - calling querySenderList after pending session removal");
	    		querySenderList(false);
				doneQuerySenderList = true;
				return;
			}
		}
    	
    	if (getActiveSession() == null)
    	{
        	Common.Logging.i(TAG, "nothing to remove - there is no active session");
        	return;
    	}
    	if (!AirMediaSession.isEqual(getActiveSession(), session))
    	{
        	Common.Logging.w(TAG, "exiting removing active session without doing anything - incoming session is not the same as the active session" + AirMediaSession.toDebugString(getActiveSession()));
    		return;
    	}
    	
    	// detach surface from session
    	if (getActiveSession().videoSurface() != null)
    		detachSurface();
    	
    	hideVideo(useTextureView(getActiveSession()));
		// clear surfaceTexture if it was Textureview
		if (useTextureView(getActiveSession()) && Boolean.parseBoolean(mStreamCtl.hdmiOutput.getSyncStatus()))
		{
			clearSurface();			
		}
    	
    	Common.Logging.i(TAG, "removing active session " + AirMediaSession.toDebugString(getActiveSession()) + " setting it to null");
    	setActiveSession(null);

    	if (sendFeedbackForSession) {
        	Common.Logging.i(TAG, "deleteActiveSessions - calling querySenderList ");
    		querySenderList(false);
			doneQuerySenderList = true;
    	}
    	Common.Logging.i(TAG, "exit from deleteActiveSession");
    }
    
    // Always send feedback
    private void deleteActiveSessionWithFeedback(AirMediaSession session)
    {
    	doneQuerySenderList = false;;
    	deleteActiveSession(session, true);
    	if (!doneQuerySenderList)
    	{
    		querySenderList(false);
    	}
		doneQuerySenderList = true;
    }
    
    private void sendSessionFeedback(AirMediaSession session)
    {
        int user = session2user(session);
        if (user >= 0)
        {
        	AirMediaSessionScreenPosition screenpos = session.videoScreenPosition();
        	int position = (screenpos == AirMediaSessionScreenPosition.None) ? 0 : 128;;
        	mStreamCtl.sendAirMediaUserFeedbacks(user, session.username(), ((List<String>)session.addresses()).get(0), position, 
        			(getSessionVideoState(session) == AirMediaSessionStreamingState.Playing) ? true : false);
        }
    }

    public void querySenderList(boolean sendAllUserFeedback)
    {
    	Common.Logging.i(TAG, "querySenderList() entered");

    	int status = 0;  // 0 = no displayed video, 1 = at least 1 video presenting
    	boolean[] sentUserFeedback = null;
    	AirMediaSessionStreamingState sessionVideoState;
    	//Common.Logging.i(TAG, "Entered querySenderList (sendAllUserFeedback=" + String.valueOf(sendAllUserFeedback) + ")");

    	if (sendAllUserFeedback)
    	{
    		// Create list of all user slots and mark off which ones are not connected
    		sentUserFeedback = new boolean[MAX_USERS+1];
    		for (int i = 1; i <= MAX_USERS; i++) { sentUserFeedback[i] = false; } // initialize all to false
    	}

    	for (int i=1; i <= MAX_USERS; i++) {
    		AirMediaSession session = user2session(i);
    		if (session != null) {
    			sendSessionFeedback(session);
    			sessionVideoState = getSessionVideoState(session);
    			if ((sessionVideoState != AirMediaSessionStreamingState.Stopped) && (sessionVideoState != AirMediaSessionStreamingState.Stopping))
    				status = 1;
    			mStreamCtl.userSettings.setAirMediaUserConnected(true, i);
    			if (sentUserFeedback != null)
    				sentUserFeedback[i] = true;
    		}
    	}

    	Common.Logging.i(TAG, "querySenderList: status = " + String.valueOf(status));
    	mStreamCtl.sendAirMediaStatus(status);
    	lastReturnedAirMediaStatus = status;

    	if (sentUserFeedback != null)
    	{
    		// Send defaults for all user connections that weren't found in query
    		for(int i = 1; i <= MAX_USERS; i++)
    		{
    			if (sentUserFeedback[i] == false)
    			{
    				//idMap.remove(i);	// Remove from mapping if existing
    				mStreamCtl.userSettings.setAirMediaUserConnected(false, i);
    				mStreamCtl.sendAirMediaUserFeedbacks(i, "", "", 0, false);					
    			}
    		}
    	}

    	mStreamCtl.sendAirMediaNumberUserConnected();

    	Common.Logging.i(TAG, "querySenderList() exit");
    }
    
    private void intializeDisplay()
    {
    	Common.Logging.i(TAG, "InitializeDisplay() entered");
    	// Show/Hide login code depending on setting
    	if (mStreamCtl.userSettings.getAirMediaLoginMode() == AirMediaLoginMode.Disabled.ordinal())
    		setLoginCodeDisable();
    	else
    		setLoginCode(mStreamCtl.userSettings.getAirMediaLoginCode());
    	        
        // Update connection status
        querySenderList(false);
        
    	Common.Logging.i(TAG, "InitializeDisplay() exit");
    }
    
    
    public void hideLoginCodePrompt()
    {
    	setLoginCodePrompt("");
    }
    
    public void showLoginCodePrompt(int loginCode)
    {
    }
    
    public void setLoginCode(int loginCode)
    {
    	if (receiver() != null)
    	{
    		String curCodeStr = receiver().serverPassword();
    		int curCode = -1;
    		if (curCodeStr != null && !curCodeStr.equalsIgnoreCase("")) {
    			try {
    				curCode = Integer.valueOf(curCodeStr);
    			} catch (Exception ex) {
    				curCode = -1;
    			}
    		}
    		Common.Logging.i(TAG, MiscUtils.stringFormat("Current Login Code = <0x%x>", MiscUtils.hash(curCode)));
    		String code = MiscUtils.stringFormat("%04d", loginCode);
    		Common.Logging.i(TAG, MiscUtils.stringFormat("Set Login Code = <0x%x>", MiscUtils.hash(Integer.valueOf(code))));
    		if (loginCode < 0) {
    			receiver().serverPassword("");
    		} else {
    			receiver().serverPassword(String.valueOf(code));	
    		}
    	}
    }
    
    public void setLoginCodeDisable()
    {
		setLoginCode(-1);
    }
    
    public void setLoginCodePrompt(String loginCodePrompt)
    {
    }
    
    public void setAdapter(String address)
    {
        //Note: startAirMedia will set receiver_ in defferent thread,
        //      need lock here.
        airMediaRcvLock.lock("airMediaRcvLock_setAdapter");
    	if (get_adapter_ip_address().equals(address) && (get_pending_adapter_ip_address() == null)) {
    	    Common.Logging.i(TAG, "setAdapter(): Exiting without any change since adapter_ip_address is already " + get_adapter_ip_address());
    	    airMediaRcvLock.unlock("airMediaRcvLock_setAdapter");            
            return;
    	}
    	set_pending_adapter_ip_address(address);
        if(receiver_ != null)
        {
            Common.Logging.i(TAG, "setAdapter(): Stopping all senders");
            stopAllSenders();
            Common.Logging.i(TAG, "setAdapter(): Disconnect all senders");
            disconnectAllSenders();
        }
        // Launch Stop/Start of receiver in separate thread
        if(receiver_ == null && service_ != null)
        {
            Common.Logging.i(TAG, "setAdapter(): calling startAirMedia with ip address " + address);
            startAirMedia();
        }
        else
        {
            RestartReceiverForAdapterChange();
            Common.Logging.i(TAG, "setAdapter(): Exiting having issued restart of receiver with " + address);
        }
        airMediaRcvLock.unlock("airMediaRcvLock_setAdapter");
    }
    
    public void setProjectionLock(boolean enable)
    {
    	Common.Logging.i(TAG, "setProjectionLock: " + enable);
    	if (receiver_ != null)
    		receiver_.projectionLocked(enable);
    }
    
    public void setModeratorEnable(boolean enable)
    {    	
		Common.Logging.e(TAG, "Implement ModeratorEnable");
    }
    
    public void set4in1ScreenEnable(boolean enable)
    {
    	if (enable)
    	{
    		Common.Logging.e(TAG, "Implement set4in1ScreenEnable");
    	}
    }
    
    // Our debug command
    public void debugCommand(String debugCommand)
    {
    	if (debugCommand == null || debugCommand.equalsIgnoreCase(""))
    	{
    		Common.Logging.i(TAG, "Usage: AIRMEDIA_DEBUG=showSessions");
    		Common.Logging.i(TAG, "       AIRMEDIA_DEBUG=showStatus");
    		return;
    	}
    	if (debugCommand.equalsIgnoreCase("showSessions"))
    	{
    		for (int i=1; i < MAX_USERS; i++) {
    			AirMediaSession s = user2session(i);
    			if (s != null) {
    				Common.Logging.i(TAG, "User: "+i+"  Session: "+s);
    	        	AirMediaSessionScreenPosition screenpos = s.videoScreenPosition();
    	        	int position = (screenpos == AirMediaSessionScreenPosition.None) ? 0 : 128;;
    		    	Common.Logging.i(TAG, MiscUtils.stringFormat("   AIRMEDIA_USER_NAME=%d:%s", i, s.username()));
    		    	Common.Logging.i(TAG, MiscUtils.stringFormat("   AIRMEDIA_USER_IP=%d:%s", i, ((List<String>)s.addresses()).get(0)));
    		    	Common.Logging.i(TAG, MiscUtils.stringFormat("   AIRMEDIA_USER_POSITION=%d:%d", i, position));
    		    	Common.Logging.i(TAG, MiscUtils.stringFormat("   AIRMEDIA_USER_CONNECTED=%d:%s", i, 
    		    			String.valueOf((getSessionVideoState(s) == AirMediaSessionStreamingState.Playing) ? true : false)));
    				logSession(s);
    			}
    		}
    		return;
    	}
    	if (debugCommand.equalsIgnoreCase("showStatus"))
    	{
    		Common.Logging.i(TAG, "Last AIRMEDIA_STATUS="+lastReturnedAirMediaStatus);
    		return;
    	}
    }
    
	// Debug command for airmedia.receiver/m360
    public void airmediaProcessDebugCommand(String debugCommand)
    {
    	if (receiver_ != null)
    		receiver_.console(debugCommand);
    }
    
    // We need to be restarted after this function is called (should be only done from a restore)
    public void clearCache()
    {
    	// Need to stop AirMedia first before clearing cache
		// Note: The actual shutting down and clearing is handled by CSIO, because root access is required
		Common.Logging.i(TAG, "clearCache(): Stopping AirMedia application");
		quittingAirMediaService = true;
		stopAllSenders();
    	if (receiver() != null && receiver_.state() != AirMediaReceiverState.Stopped)
		{
			stopReceiver();			
		}
    	if (isServiceConnected)
    		doUnbindService();
    }
    
    public void logSession(AirMediaSession session)
    {
		Common.Logging.i(TAG, "   endpoint: "+session.endpoint());
		Common.Logging.i(TAG, "   username: "+session.username());
		Common.Logging.i(TAG, "   connection: "+session.connection());
		Common.Logging.i(TAG, "   streaming: "+session.streaming());
		Common.Logging.i(TAG, "   channelState: "+session.channelState());
		Common.Logging.i(TAG, "   deviceState: "+session.deviceState());
		Common.Logging.i(TAG, "   device ID: "+session.deviceId());
		Common.Logging.i(TAG, "   videoState: "+session.videoState());
		Common.Logging.i(TAG, "   video ID: "+session.videoId());
		Common.Logging.i(TAG, "   videoResolution: "+session.videoResolution());
		Common.Logging.i(TAG, "   videoRotation: "+session.videoRotation());
		Common.Logging.i(TAG, "   videoIsDrm: "+session.videoIsDrm());
		Common.Logging.i(TAG, "   screenPosition: "+session.videoScreenPosition());
		Common.Logging.i(TAG, "   video Surface: "+session.videoSurface());
		Common.Logging.i(TAG, "   audioState: "+session.audioState());
		Common.Logging.i(TAG, "   audio ID: "+session.audioId());
		Common.Logging.i(TAG, "   audio Muted: "+session.audioMuted());
		Common.Logging.i(TAG, "   audio Volume: "+session.audioVolume());
	}
    
    private void addSession(AirMediaSession session)
    {
		Common.Logging.i(TAG, "addSession " + session);
        int user = addSessionToMap(session);
        // To avoid JIRA AMX00-1138 when AirMediaSessionManager.USE_SYNCHRONOUS_ADD_SESSION_EVENT is false
        if (!AirMediaSessionManager.USE_SYNCHRONOUS_ADD_SESSION_EVENT)
        	setSessionVideoState(session, session.videoState());
        Common.Logging.i(TAG, "User id: "+String.valueOf(user)+"  Connected: "+String.valueOf(getSessionVideoState(session) == AirMediaSessionStreamingState.Playing));
		Common.Logging.i(TAG, "Adding Id to map, userId: " + user + " session: " + session);
		if (mCanvas != null) {
			String name = ((session.username() != null) ? session.username() : ("AM-"+String.valueOf(user)));
			com.crestron.txrxservice.canvas.AirMediaSession s = new com.crestron.txrxservice.canvas.AirMediaSession(session, name);
			canvasSessionMap.put(session.id(), s);
			s.setAirMediaType(session);
        	sendClientData();
        	mCanvas.mSessionMgr.logSessionStates("AirMediaSplashtop::addSession");
		}
		if ((user > 0) && (user <= MAX_USERS))
		{
			mStreamCtl.userSettings.setAirMediaUserConnected(true, user);
			Common.Logging.i(TAG, "addSession: sending feedback for user: " + String.valueOf(user));
			sendSessionFeedback(session);
			sendClientData(session);
			mStreamCtl.sendAirMediaNumberUserConnected();
		}
		else
			Common.Logging.w(TAG, "Received invalid user id of " + user);
    }
    
    private void removeSession(AirMediaSession session)
    {
		Common.Logging.i(TAG, "removeSession " + session);
        int user = session2user(session);
        if (user > 0) {
            Common.Logging.i(TAG, "User id: "+String.valueOf(user)+"  Disconnected: "+String.valueOf(false));
            mStreamCtl.userSettings.setAirMediaUserConnected(false, user);
            mStreamCtl.sendAirMediaUserFeedbacks(user, "", "", 0, false);
			removeSessionVideoState(session);
        } else {
            Common.Logging.e(TAG, "Got invalid user id: "+String.valueOf(user) + "for " + session);
        }
        removeSessionFromMap(session);
        if (mCanvas != null)
        {
        	com.crestron.txrxservice.canvas.AirMediaSession s = canvasSessionMap.get(session.id());
        	if (s != null)
        	{
        		s.setDisconnected();
        		canvasSessionMap.remove(session.id());
        	}
        	mCanvas.mSessionMgr.logSessionStates("AirMediaSplashtop::removeSession");
        }
		removeClientData(user, session);
		querySenderList(false); // force update of AIRMEDIA_STATUS as well as AIRMEDIA_NUMBER_OF_USERS_CONNECTED
    }
    
    public void resetConnections()
    {    	
		Common.Logging.i(TAG, "resetConnections()");
		disconnectAllSenders();
    }
    
    public void disconnectUser(int userId)
    { 
		Common.Logging.i(TAG, "disconnectUser: " + String.valueOf(userId));
		AirMediaSession session = user2session(userId);
		if (session == null) {
			Common.Logging.e(TAG, "Trying to stop an unconnected session for user " + String.valueOf(userId));
			return;
		}
		Common.Logging.i(TAG, "disconnectUser: " + String.valueOf(userId) + " Session=" + AirMediaSession.toDebugString(session) + "  connectionState=" + session.connection());
		// Only allow a single 'session' to enter at a time - need to make sure that we have a unique session
		// requesting a stop and that the callback verifies it is for that specific session before counting
		// down the latch
		synchronized(disconnectSessionObjectLock) {
			if (session.connection() != AirMediaSessionConnectionState.Disconnected) {
				// session is connected
				disconnectSession(session);
			} else {
				Common.Logging.w(TAG, "Session: " + AirMediaSession.toDebugString(session) + " is already in connection disconnected state");
			}
		}
		Common.Logging.i(TAG, "disconnectUser: " + String.valueOf(userId) + " - exit");
    }
    
    private void disconnectSession(AirMediaSession session)
    {
		boolean disconnectStatus=true;
		long begin = System.currentTimeMillis();
		synchronized(disconnectSessionCriticalSectionLock)
		{
			sessionRequestingDisconnect = session;
			sessionDisconnectedLatch = new CountDownLatch(1);
		}
		Common.Logging.i(TAG, "Session " + AirMediaSession.toDebugString(session) + " requesting disconnect ");
		session.disconnect(); 
		try { disconnectStatus = sessionDisconnectedLatch.await(10000, TimeUnit.MILLISECONDS); }
		catch (InterruptedException ex) { ex.printStackTrace(); }
		if (!disconnectStatus) {
			Common.Logging.w(TAG, "Unable to disconnect session " + AirMediaSession.toDebugString(session) + "even after 10 seconds");
		} else {
			long end = System.currentTimeMillis();
			Common.Logging.i(TAG, "Session " + AirMediaSession.toDebugString(session) + " was successfully disconnected in "
					+ (end-begin) + " ms");
		}
		synchronized(disconnectSessionCriticalSectionLock)
		{
			sessionRequestingDisconnect=null;
			sessionDisconnectedLatch=null;
		}
    }
    
    private void countdownSessionDisconnectLatch(AirMediaSession session)
    {
		synchronized(disconnectSessionCriticalSectionLock)
		{
			if (AirMediaSession.isEqual(session, sessionRequestingDisconnect)) {
				if (sessionDisconnectedLatch != null && sessionDisconnectedLatch.getCount() > 0) {
                    Common.Logging.i(TAG, "countdownSessionDisconnectLatch - counting down session disconnect latch for session="+session);
					sessionDisconnectedLatch.countDown();
				}
			}
		}
    }
        
    public void disconnectAllSenders()
    {
    	Common.Logging.i(TAG, "disconnectAllSenders");
    	for (int i = 1; i <= MAX_USERS; i++) // We handle airMedia user ID as 1 based
    	{
    		if (mStreamCtl.userSettings.getAirMediaUserConnected(i))
    		{
    			disconnectUser(i);
    		}
    	}
    }

    
    public void startUser(int userId)
    { 
		Common.Logging.i(TAG, "startUser: " + String.valueOf(userId));
		AirMediaSession session = user2session(userId);
		if (session == null) {
			Common.Logging.w(TAG, "Trying to start an unconnected session for user "+ String.valueOf(userId));
			return;
		}
		if (getActiveSession() != null && !AirMediaSession.isEqual(getActiveSession(), session))
		{
			// stop prior active session
			stopUser(userId);
		} 
		if (AirMediaSession.isEqual(getActiveSession(), session)) {
			// session is already started
			Common.Logging.e(TAG, "session already started for userId="+userId);
			return; 
		} else {
			session.play();
		}
		// TODO need feedback for this user
    }
    
    public void setUserPosition(int userId, int position)
    {    	
    	// Translate from Crestron enum to AirMedia enum
    	int quadrant = 0;
    	switch (position)
    	{
    	case 1: //Crestron: topLeft, AirMedia: topLeft
    	case 2: //Crestron: topRight, AirMedia: topRight
    	case 3: //Crestron: bottomLeft, AirMedia: bottomLeft
    	case 4: //Crestron: bottomRight, AirMedia: bottomRight
    		quadrant = position;
    		break;
    	case 5: //Crestron: fullscreen
    		quadrant = 128; //AirMedia: fullscreen
    		break;
		default:
    			break;
    	}
    }
    
    public void stopUser(int userId)
    { 
		Common.Logging.i(TAG, "stopUser: " + String.valueOf(userId));
		AirMediaSession session = user2session(userId);
		if (session == null) {
			Common.Logging.e(TAG, "Trying to stop an unconnected session for user " + String.valueOf(userId));
			return;
		}
		Common.Logging.i(TAG, "stopUser: " + String.valueOf(userId) + " Session=" + AirMediaSession.toDebugString(session) + "  deviceState=" + session.deviceState());
		if (getActiveSession() != null && !AirMediaSession.isEqual(getActiveSession(), session))
		{
			if (getSessionVideoState(session) == AirMediaSessionStreamingState.Playing)
			{
				Common.Logging.w(TAG, "Trying to stop a playing session which is not active for user " + String.valueOf(userId));
			}
		}
		// Only allow a single 'session' to enter at a time - need to make sure that we have a unique session
		// requesting a stop and that the callback verifies it is for that specific session before counting
		// down the latch
		synchronized(stopSessionObjectLock) {
			if (session.deviceState() != AirMediaSessionConnectionState.Disconnected) {
				// session is started
				stopSession(session);
			} else {
				Common.Logging.w(TAG, "Session: " + AirMediaSession.toDebugString(session) + " is already in device disconnected state");
			}
		}
		Common.Logging.i(TAG, "stopUser: " + String.valueOf(userId) + " - exit");
    }
    
    private void stopSession(AirMediaSession session)
    {
		boolean stopStatus=true;
		long begin = System.currentTimeMillis();
		synchronized(stopSessionCriticalSectionLock)
		{
			sessionRequestingStop = session;
			deviceDisconnectedLatch = new CountDownLatch(1);
		}
		Common.Logging.i(TAG, "Session " + AirMediaSession.toDebugString(session) + " requesting stop ");
		session.stop(); 
		try { stopStatus = deviceDisconnectedLatch.await(10000, TimeUnit.MILLISECONDS); }
		catch (InterruptedException ex) { ex.printStackTrace(); }
		if (!stopStatus) {
			Common.Logging.w(TAG, "Unable to stop session " + AirMediaSession.toDebugString(session) + "even after 10 seconds");
		} else {
			long end = System.currentTimeMillis();
			Common.Logging.i(TAG, "Session " + AirMediaSession.toDebugString(session) + " was successfully stoppped in "
					+ (end-begin) + " ms");
		}
		synchronized(stopSessionCriticalSectionLock)
		{
			sessionRequestingStop=null;
			deviceDisconnectedLatch=null;
		}
    }
    
    private void countdownDeviceDisconnectLatch(AirMediaSession session)
    {
    	synchronized(stopSessionCriticalSectionLock)
    	{
    		if (AirMediaSession.isEqual(session, sessionRequestingStop)) {
    			if (deviceDisconnectedLatch != null && deviceDisconnectedLatch.getCount() > 0) {
                    Common.Logging.i(TAG, "countdownDeviceDisconnectLatch - counting down session stop latch for session="+session);
    				deviceDisconnectedLatch.countDown();
    			}
    		}
    	}
    }
    
	private String getSessionType(AirMediaSession session)
	{
		String sessionType = "None";

		if (session != null)
		{
			AirMediaSessionInfo info = session.info();
			Common.Logging.v(TAG, "getSessionType(): sessionType="+session.videoType()+"  info="+info.toString());

			switch (session.videoType())
			{
			case Undefined:
				//sessionType = "None";
				sessionType = (info.platform == AirMediaPlatforms.Mac || info.platform == AirMediaPlatforms.iOS) ? "AirPlayMirroring" : "AirMedia";
				break;
			case Mirror:
				sessionType = (info.platform == AirMediaPlatforms.Mac || info.platform == AirMediaPlatforms.iOS) ? "AirPlayMirroring" : "AirMedia";
				break;	
			case Video:
				sessionType = "AirPlayVideoPush";
				break;
			case WebRTC:
				sessionType = "GoogleChromeExtension";
				break;
			case Miracast:
				sessionType = "Miracast";
				break;
			}
		}
		if (session != null)
		{
			Common.Logging.v(TAG, "getSessionType(): session="+session.toString()+" is of sessionType="+sessionType);
		}
		else
		{
			Common.Logging.v(TAG, "getSessionType(): No active session --- sessionType="+sessionType);
		}

		return sessionType;
	}
    

	class Display {
		private boolean IsPrimary;
		private String Resolution;
		private int Dpi;

		public Display(AirMediaSessionVideoSource source)
		{
			this.IsPrimary = source.isPrimary;
			this.Resolution = source.resolution.toString();
			this.Dpi = source.dpi.width;
		}
	}
	

	class Client {
		private String SessionType;
		private Boolean IsClientActive;
		private String Platform;
		private String SenderVersion;
		private String OsVersion;
		private String Manufacturer;
		private String Model;
		private String AirPlayVersion;
		private String DeviceId;
		private String Language;
		private String VideoResolution;
		private String UserName;
		private String IpAddress;
		private Map<String, Display> VideoDisplays;


		public Client(AirMediaSession session) 
		{
			AirMediaSessionInfo info = session.info();
			this.SessionType = getSessionType(session);
			this.IsClientActive = Boolean.valueOf((session.videoState() == AirMediaSessionStreamingState.Playing) ||
					(session.videoState() == AirMediaSessionStreamingState.Paused));
			this.IpAddress = ((List<String>)session.addresses()).get(0);
			if (this.IpAddress == null)
			{
				this.IpAddress = "";
			}
			if (session.username() != null)
			{
				if (UserName==null || !UserName.equals(session.username()))
					this.UserName = session.username();
			} else {
				this.UserName = "";
			}
			String platform = info.platform.toString();
			if (!platform.equals("") && !platform.equals("Undefined"))
			{
			    if (platform.equals("Tx3_100") || platform.equals("Tx3_200"))
			       platform = "AirMedia TX3";
				this.Platform = platform;
			}
			if (!info.version.equals(""))
				this.SenderVersion = info.version;
			if (!info.os.equals(""))
				this.OsVersion = info.os;
			if (!info.manufacturer.equals(""))
				this.Manufacturer = info.manufacturer;
			if (!info.model.equals(""))
				this.Model = info.model;
			if (info.os.equals("iOS") || info.os.equals("Mac"))
				this.AirPlayVersion = info.deviceUseragent;
			if (!session.deviceId().equals(""))
				this.DeviceId = session.deviceId();
			if (!info.language.equals(""))
				this.Language = info.language;
			String resolution = session.videoResolution().toString();
			if (!resolution.equals("") && !resolution.equals("0x0"))
				this.VideoResolution = resolution;
			if (info.videoSources != null && info.videoSources.length > 0)
			{
				this.VideoDisplays = new LinkedHashMap<String, Display>();
				for (int i=0; i < info.videoSources.length; i++)
				{
					this.VideoDisplays.put("Display"+i, new Display(info.videoSources[i]));
				}
			}
		}
		
		public Client()
		{
		}
		
		public Client(AirMediaSize videoResolution)
		{
			this.VideoResolution = videoResolution.toString();
		}
		
		public Client(boolean isActive)
		{
			this.IsClientActive = Boolean.valueOf(isActive);
		}
		
		public Client(String sessionType)
		{
			this.SessionType = sessionType;
		}
	}

	class DeviceObject {
		Device Device;		
		class Device {
			private AirMedia AirMedia;			
			class AirMedia {
				private ClientData ClientData;				
				class ClientData {
					String Status; // "Idle", "Active", "Presenting"
					Integer TotalUsers;
					Boolean IsAnyWifiUserConnected;
					private Map<String, Client> ConnectedClients;
					
					void updateStatusAndTotalUsers(boolean force)
					{
						int totalUsers = canvasSessionMap.size();
						String status = (totalUsers == 0) ? "Idle" : "Active";
						if (totalUsers > 0)
						{
							for (Map.Entry<Long, com.crestron.txrxservice.canvas.AirMediaSession> e : canvasSessionMap.entrySet()) {
								com.crestron.txrxservice.canvas.AirMediaSession cs = e.getValue();
								if (cs.videoState == AirMediaSessionStreamingState.Playing || cs.videoState == AirMediaSessionStreamingState.Paused)
								{
									status = "Presenting";
									break;
								}
							}
						}
						if (totalUsers != clientDataTotalUsers)
						{
							clientDataTotalUsers = totalUsers;
							this.TotalUsers = Integer.valueOf(clientDataTotalUsers);
						}
						if (!status.equalsIgnoreCase(clientDataStatus))
						{
							clientDataStatus = status;
							this.Status = clientDataStatus;
						}
						// Adding due to bug AMX00-1813 but root cause from logs was something else (getting added events for sessions twice)
						// Rob LoPresti - said must have two handlers registered for add event
						if (force)
						{
							this.TotalUsers = Integer.valueOf(clientDataTotalUsers);
							this.Status = clientDataStatus;
						}
						Common.Logging.i(TAG, "updateStatusAndTotalUsers(): status ="+clientDataStatus+" totalUsers="+clientDataTotalUsers);
					}
				}

				public AirMedia()
				{
					ClientData = new ClientData();
				}
			}
			
			public Device()
			{
				AirMedia = new AirMedia();
			}
		}
		
		public DeviceObject()
		{
			Device = new Device();
		}
	}

	public void sendClientDataWifiUsers(int n)
	{
		DeviceObject dev = new DeviceObject();
		dev.Device.AirMedia.ClientData.IsAnyWifiUserConnected = (n > 0) ? true : false;
		// only send update if necessary - i.e it has changed
		if (isAnyWifiUserConnected != dev.Device.AirMedia.ClientData.IsAnyWifiUserConnected)
		{
		    isAnyWifiUserConnected = dev.Device.AirMedia.ClientData.IsAnyWifiUserConnected;
		    String sessionClientData = gson.toJson(dev);
		    Common.Logging.i(TAG,  "sendClientWifiUsers: ClientDataJSON=" + sessionClientData);
		    mStreamCtl.SendToCresstore(sessionClientData, CresStreamCtrl.CresstoreOptions.PublishAndSave);
		}
	}
	
	private void sendClientData(int client, Client clientData)
	{
		DeviceObject dev = new DeviceObject();
		dev.Device.AirMedia.ClientData.updateStatusAndTotalUsers((client&UPDATE_STATUS_AND_TOTAL_USERS) != 0);
		if (clientData != null)
		{
			dev.Device.AirMedia.ClientData.ConnectedClients = new LinkedHashMap<String, Client>();
			dev.Device.AirMedia.ClientData.ConnectedClients.put("Client"+(client&0xFF), clientData);
		}
		if (client < 0)
		{
			// set map to empty
			dev.Device.AirMedia.ClientData.ConnectedClients = new LinkedHashMap<String, Client>();
		}
		String sessionClientData = gson.toJson(dev);
		Common.Logging.i(TAG,  "sendClientData: ClientDataJSON=" + sessionClientData);
		mStreamCtl.SendToCresstore(sessionClientData, CresStreamCtrl.CresstoreOptions.PublishAndSave);
	}
	
	private void sendClientData()
	{
		sendClientData(UPDATE_STATUS_AND_TOTAL_USERS, null); // just update status and totalusers;
	}
	
	private void sendClientData(AirMediaSession session)
	{
		if (session == null)
			return;
		int client = session2user(session);
		AirMediaSessionInfo info = session.info();
		
		if (client < 0 || info == null)
			return;
		Common.Logging.i(TAG,  "sendClientData for client " + client + " session=" + session);
		sendClientData(client, new Client(session));
	}
	
    private void sendClientData(AirMediaSession session, AirMediaSessionInfo from, AirMediaSessionInfo to)
    {
		int client = session2user(session);
		
		if (client < 0)
			return;
		if (from != null && to != null)
		{
			if (from.platform != to.platform)
			{
				Common.Logging.i(TAG,  "sendClientData() platform: " + from.platform + " ---> " + to.platform);
			}
			if (!from.version.equals(to.version))
			{
				Common.Logging.i(TAG,  "sendClientData() version: " + from.version + " ---> " + to.version);
			}
			if (!from.os.equals(to.os))
			{
				Common.Logging.i(TAG,  "sendClientData() os: " + from.os + " ---> " + to.os);
			}
			if (!from.manufacturer.equals(to.manufacturer))
			{
				Common.Logging.i(TAG,  "sendClientData() manufacturer: " + from.manufacturer + " ---> " + to.manufacturer);
			}
			if (!from.model.equals(to.model))
			{
				Common.Logging.i(TAG,  "sendClientData() model: " + from.model + " ---> " + to.model);
			}
			if (!from.deviceUseragent.equals(to.deviceUseragent))
			{
				Common.Logging.i(TAG,  "sendClientData() deviceUseragent: " + from.deviceUseragent + " ---> " + to.deviceUseragent);
			}
			if (!from.language.equals(to.language))
			{
				Common.Logging.i(TAG,  "sendClientData() language: " + from.language + " ---> " + to.language);
			}
			if (AirMediaSessionVideoSource.isEqual(from.videoSources, to.videoSources))
			{
				Common.Logging.i(TAG,  "sendClientData() video sources changed: " + from.videoSources + " ---> " + to.videoSources);
			}
			if (!AirMediaSessionInfo.isEqual(from, to))
			{
				sendClientData(session);
				if (from.platform != to.platform)
				{
					setPlatformType(session, to.platform);
				}
			}
		}
		else if (from == null && to != null)
		{
			sendClientData(session);
			setPlatformType(session, to.platform);
		}
    }
    
    private void setPlatformType(AirMediaSession session, AirMediaPlatforms platform)
    {
		if (mCanvas != null)
		{
			com.crestron.txrxservice.canvas.AirMediaSession s = canvasSessionMap.get(session.id());
			if (s != null)
			{
				s.setPlatformType(platform);
			}
		}
    }
    
    private void sendClientDataIsActiveSession(AirMediaSession session, boolean isActiveSession)
    {
		int client = session2user(session);
		if (client < 0)
			return;
		
		sendClientData(client, new Client(isActiveSession));
    }
    
	private void sendClientDataSessionType(AirMediaSession session)
	{
		int client = session2user(session);
		if (client < 0)
			return;
		
		String sessionType = getSessionType(session);
		sendClientData(client, new Client(sessionType));
	}
	
	private void sendClientDataVideoResolution(AirMediaSession session)
	{
		int client = session2user(session);
		if (client < 0)
			return;
		
		sendClientData(client, new Client(session.videoResolution()));
	}
	
	private void sendClientDataUsername(AirMediaSession session, String username)
	{
		int client = session2user(session);
		if (client < 0)
			return;
		
		Client c = new Client();
		c.UserName = username;
		sendClientData(client, c);
	}
	
	private void sendClientDataIpAddress(AirMediaSession session, String ipAddress)
	{
		int client = session2user(session);
		if (client < 0)
			return;
		
		Client c = new Client();
		c.IpAddress = ipAddress;
		sendClientData(client, c);
	}
	
	private void removeClientData(int client, AirMediaSession session)
	{
		if (client < 0)
			return;

		Common.Logging.i(TAG,  "removeClientData for client "+ client + " session=" + session);
		sendClientData(client | UPDATE_STATUS_AND_TOTAL_USERS, new Client());
	}
	
	private void removeAllConnectedClients()
	{
		Common.Logging.i(TAG,  "removeAllConnectedClients ");
		sendClientData(-1, null);
	}
	
    public void stopAllUser()
    {
    }
    
    public void setOsdImage(String filePath)
    {
    }
    
    public void setIpAddressPrompt(boolean enable)
    {
    }
    
    public void setDomainNamePrompt(boolean enable)
    {
    }
    
    public void setSurfaceSize()
    {
    	int x = mStreamCtl.userSettings.getAirMediaX();
    	int y = mStreamCtl.userSettings.getAirMediaY();
		int width = mStreamCtl.userSettings.getAirMediaWidth();
		int height = mStreamCtl.userSettings.getAirMediaHeight();
		
		if ((width == 0) && (height == 0))
		{
			Point size = mStreamCtl.getDisplaySize();

			width = size.x;
			height = size.y;
		}
		setSurfaceSize(x, y, width, height);
    }
    
    public void setSurfaceSize(int x, int y, int width, int height, boolean launch)
    {
		setSurfaceSize(x, y, width, height);
    }

    public void setSurfaceSize(int x, int y, int width, int height)
    {		
		Common.Logging.i(TAG, "------------ In setSurfaceSize calling updateWindow (surfaceDisplayed="+getSurfaceDisplayed()+") --------------");
    	window_ = new Rect(x, y, x+width-1, y+height-1);
    	Common.Logging.i(TAG, "setSurfaceSize - calling updateWindow for sessId="+streamIdx+
    			" "+width+"x"+height+"@"+x+","+y+"   useTexture="+useTextureView(getActiveSession()));
		mStreamCtl.dispSurface.updateWindow(x, y, width, height, streamIdx, useTextureView(getActiveSession()));
		Common.Logging.i(TAG, "------------ finished updateWindow --------------");
    }
    
    public void setDisplayScreen(int displayId)
    {
    }
    
    public void setWindowFlag(int windowFlag)
    {
    }
    
    public void setStandbyScreen(int standbyScreen)
    {
    }
    
    private void sendStopToAllSenders()
    {
		Common.Logging.i(TAG, "sendStopToAllSenders");
    	for (int i = 1; i <= MAX_USERS; i++) // We handle airMedia user ID as 1 based
    	{
    		if (mStreamCtl.userSettings.getAirMediaUserConnected(i))
    		{
    			AirMediaSession session = user2session(i);
    			if (session != null)
    			{
    				Common.Logging.i(TAG, "sendStopToAllSenders: Session " + AirMediaSession.toDebugString(session) + " requesting stop ");
    				session.stop();
    			}
    		}
    	}
    }
    
    private void stopAllSenders()
    {
		Common.Logging.i(TAG, "stopAllSenders");
    	for (int i = 1; i <= MAX_USERS; i++) // We handle airMedia user ID as 1 based
    	{
    		if (mStreamCtl.userSettings.getAirMediaUserConnected(i))
    		{
    			stopUser(i);
    		}
    	}
    }
    
    private void stopAllButPendingSenders()
    {
		Common.Logging.i(TAG, "stopAllButPendingSenders");
		int pending_user = -1;
		synchronized(pendingSessionLock) {
			if (getPendingSession() != null) {
				pending_user = session2user(getPendingSession());
			}
		}
		// Should stopUser be locked ? in case session goes away from underneath us
    	for (int i = 1; i <= MAX_USERS; i++) // We handle airMedia user ID as 1 based
    	{
    		if (mStreamCtl.userSettings.getAirMediaUserConnected(i) && i != pending_user)
    		{
    			stopUser(i);
    		}
    	}
    }
    
	public AirMediaReceiver receiver()
	{
		return receiver_;
	}

	public IAirMediaReceiver service()
	{
		return service_;
	}
	
	public AirMediaSessionManager manager()
	{
		return manager_;
	}

	public AirMediaSession getPendingSession()
	{
		return pending_session_;
	}
	
	public AirMediaSession getActiveSession()
	{
		return active_session_;
	}
	
	public void setActiveSession(AirMediaSession session)
	{
		if (getActiveSession() != null)
		{
			sendClientDataIsActiveSession(getActiveSession(), false);	
		}
		active_session_ = session;
		if (session != null)
		{
			sendClientDataIsActiveSession(session, true);
		}
	}
	
    public static boolean checkAirMediaLicense()
    {
    	boolean licensed = false;
    	try
    	{
    		licensed = Integer.parseInt(MiscUtils.readStringFromDisk(licenseFilePath)) == 1;
    	} catch (NumberFormatException e) {} // If file DNE or corrupt not licensed
    	return licensed;
    }
    
	private void doBindService() {
        Common.Logging.i(TAG, "doBindService");
        String bindString = AirMediaReceiver.AIRMEDIA_SERVICE_BIND;
        if ((mCanvas!=null && CresCanvas.useCanvasSurfaces!=false))
        {
        	if (mStreamCtl.useFauxPPUX)
        		bindString = AirMediaReceiver.AIRMEDIA_SERVICE_CANVAS_BIND;
        	else
        		bindString = AirMediaReceiver.AIRMEDIA_SERVICE_CANVAS_LAUNCHER_BIND;
        }
        Intent serviceIntent = new Intent(bindString);
        serviceIntent.setPackage(AirMediaReceiver.AIRMEDIA_SERVICE_PACKAGE);
		List<ResolveInfo> list = mContext.getPackageManager().queryIntentServices(serviceIntent, 0);
		if (list == null || list.isEmpty()) {
        	Common.Logging.e(TAG, "doBindService  service does not exist  package= " + AirMediaReceiver.AIRMEDIA_SERVICE_PACKAGE + "  intent= " + AirMediaReceiver.AIRMEDIA_SERVICE_BIND + "------");
		} else {
			if (!mContext.bindService(serviceIntent, AirMediaServiceConnection, Context.BIND_AUTO_CREATE)) {
		        Common.Logging.e(TAG, "failed to bind to " + AirMediaReceiver.AIRMEDIA_SERVICE_PACKAGE);
			}
		}
		Common.Logging.i(TAG, "doBindService - completed bind BIND=" + bindString + "   PACKAGE=" + AirMediaReceiver.AIRMEDIA_SERVICE_PACKAGE);
	}

	private void doUnbindService() {
        Common.Logging.i(TAG, "doUnbindService");
		try {
            close();
            if (service_ != null)
            {
            	boolean successfulStart = false;
            	serviceDisconnectedLatch = new CountDownLatch(1);
            	mContext.unbindService(AirMediaServiceConnection);
    			try { successfulStart = serviceDisconnectedLatch.await(5000, TimeUnit.MILLISECONDS); }
    			catch (InterruptedException ex) { ex.printStackTrace(); }
            	service_ = null;
            	if (!successfulStart)
            	{
            		Common.Logging.w(TAG, "doUnbindService - did not onServiceDisconnect - timeout");
            		isServiceConnected = false;
            	}
            }
		} catch (Exception e) {
            Common.Logging.e(TAG, "doUnbindService  EXCEPTION  " + e);
		}
	}

    public void close() {
        AirMediaReceiver receiver = receiver_;
        AirMediaSessionManager manager = manager_;
        
        if (manager_ != null)
        {
        	Common.Logging.i(TAG, "close(): remove prior event handlers");
        	unregisterSessionManagerEventHandlers(manager_);
        	manager_ = null;
        }
        if (receiver != null) {
        	unregisterReceiverEventHandlers(receiver_);
            try {
            	receiver.stop(TimeSpan.fromSeconds(10.0));
                receiver.close(TimeSpan.fromSeconds(10.0));
            } catch (Exception e) {
                Common.Logging.e(TAG, "close  EXCEPTION  " + e);
            }
        }
		receiver_ = null;
        Common.Logging.i(TAG, "close(): completed");
    }

    private final ServiceConnection AirMediaServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(final ComponentName name, final IBinder binder) {
            // This comes in on UI thread, move off
            new Thread(new Runnable() {
                @Override
                public void run() {
                    isServiceConnected = true;
                    if (requestServiceConnection)
                    {
                        Common.Logging.i(TAG, "AirMediaServiceConnection.onServiceConnected  " + name);
                        requestServiceConnection = false;
                        try {
                            airMediaRcvLock.lock("airMediaRcvLock_onSrvCondCb");
                            service_ = IAirMediaReceiver.Stub.asInterface(binder);
                            if (service_ == null){
                                airMediaRcvLock.unlock("airMediaRcvLock_onSrvCondCb");
                                return;
                            } 
                            serviceConnectedLatch.countDown();

                            // Note: startAirMedia will set receiver_, which used by setAdapter in
                            // different thread, so lock here.                            
                            startAirMedia();
                        } catch (Exception e) {
                            Common.Logging.e(TAG, "AirMediaServiceConnection.onServiceConnected  EXCEPTION  " + e);
                            e.printStackTrace();
                        }finally{
                            airMediaRcvLock.unlock("airMediaRcvLock_onSrvCondCb");
                        }
                    } 
                    else 
                    {
                        Common.Logging.i(TAG, "AirMediaServiceConnection.onServiceConnected, ignoring unsolicited" + name);
                    }
                }
            }).start();
        }

		@Override
		public void onServiceDisconnected(final ComponentName name) {
//			This comes in on UI thread, move off
			new Thread(new Runnable() {
				@Override
				public void run() {
					Common.Logging.i(TAG, "AirMediaServiceConnection.onServiceDisconnected  " + name);
					isServiceConnected = false;
					if (serviceDisconnectedLatch != null)
					{
						serviceDisconnectedLatch.countDown();
					}
					try {
						if (quittingAirMediaService == false)
						{
							// remove active session so AirMedia screen pops up until fresh connection made to service
							if (getActiveSession() != null)
							{
							    // if we are attempting to stop this session countdown latch and treat it as stopped
							    countdownDeviceDisconnectLatch(getActiveSession());
								removeSession(getActiveSession());
								for (int sessionId=0; sessionId < mStreamCtl.NumOfSurfaces; sessionId++)
								{
									if (mStreamCtl.userSettings.getAirMediaLaunch(sessionId))
									{
										if (mStreamCtl.getSurfaceView(sessionId).getVisibility() == View.VISIBLE)
										{
											hideVideo(false);
										} 
										else if (mStreamCtl.getTextureView(sessionId).getVisibility() == View.VISIBLE)
										{
							    	    	if (surface_ != null)
							    	    		surface_.release();
							    	    	surface_ = null;
											hideVideo(true);
										}
										else
										{
											Log.i(TAG, "OnServiceDisconnected(): Neither SurfaceView nor TextureView visible even though AirMediaLaunch is true for streamId="+sessionId);
										}
									}
								}
								setActiveSession(null);
							}
							if (mCanvas != null)
							{
								Log.i(TAG, "OnServiceDisconnected(): call handleReceiverDisconnected");
								mCanvas.handleReceiverDisconnected();
							}
							mStreamCtl.stopWcServer("AirMedia");
							removeAllSessionsFromMap(false, "onServiceDisconnected()");
							RestartAirMediaAsynchronously();
						}
						else
							Common.Logging.e(TAG, "AirMediaServiceConnection.onServiceDisconnected ignored because quitting");
					} catch (Exception e) {
						Common.Logging.e(TAG, "AirMediaServiceConnection.onServiceDisconnected  EXCEPTION  " + e);
						e.printStackTrace();
					}
				}
			}).start();
		}
	};
	
	//  Receiver events
	private void registerReceiverEventHandlers(AirMediaReceiver receiver) {
        if (receiver == null) return;
        unregisterReceiverEventHandlers(receiver);
        receiver.loadedChanged().register(loadedChangedHandler_);
        receiver.stateChanged().register(stateChangedHandler_);
        receiver.wifiApUsersCountChanged().register(wifiApUsersCountChangedHandler_);
	}
	
	private void unregisterReceiverEventHandlers(AirMediaReceiver receiver) {
        if (receiver == null) return;
        receiver.loadedChanged().unregister(loadedChangedHandler_);
        receiver.stateChanged().unregister(stateChangedHandler_);
        receiver.wifiApUsersCountChanged().unregister(wifiApUsersCountChangedHandler_);
	}
	
	//  SessionManager Events
    private void registerSessionManagerEventHandlers(AirMediaSessionManager manager) {
        if (manager == null) return;
        unregisterSessionManagerEventHandlers(manager);
        manager.layoutChanged().register(layoutChangedHandler_);
        manager.occupiedChanged().register(occupiedChangedHandler_);
        manager.added().register(addedHandler_);
        manager.removed().register(removedHandler_);
    }

    private void unregisterSessionManagerEventHandlers(AirMediaSessionManager manager) {
        if (manager == null) return;
        manager.layoutChanged().unregister(layoutChangedHandler_);
        manager.occupiedChanged().unregister(occupiedChangedHandler_);
        manager.added().unregister(addedHandler_);
        manager.removed().unregister(removedHandler_);
    }
	
    private final MulticastChangedDelegate.Observer<AirMediaReceiver, AirMediaReceiverLoadedState> loadedChangedHandler_ = new MulticastChangedDelegate.Observer<AirMediaReceiver, AirMediaReceiverLoadedState>() {
        @Override
        public void onEvent(AirMediaReceiver receiver, AirMediaReceiverLoadedState from, AirMediaReceiverLoadedState to) {
            Common.Logging.i(TAG, "view.receiver.event.loaded  " + from + "  ==>  " + to);
            if (to == AirMediaReceiverLoadedState.Loaded)
            {
            	receiverLoadedLatch.countDown();
            }
        }
    };

    private final MulticastChangedWithReasonDelegate.Observer<AirMediaReceiver, AirMediaReceiverState, Integer> stateChangedHandler_ = new MulticastChangedWithReasonDelegate.Observer<AirMediaReceiver, AirMediaReceiverState, Integer>() {
        @Override
        public void onEvent(AirMediaReceiver receiver, AirMediaReceiverState from, AirMediaReceiverState to, Integer reason) {
            Common.Logging.i(TAG, "view.receiver.event.state  reason=" + reason + "  " + from + "  ==>  " + to);
            if (to == AirMediaReceiverState.Stopped)
            {
            	Common.Logging.i(TAG, "In stateChangedHandler: receiverStoppedLatch="+receiverStoppedLatch);
            	isReceiverStarted = false;
		    	Common.Logging.i(TAG,"In stateChangedHandler: - setting receiver started state to "+isReceiverStarted);
		    	if (receiverStoppedLatch != null)
		    	{
		    		receiverStoppedLatch.countDown();
		    	}
            }
            if (to == AirMediaReceiverState.Started)
            {
            	Common.Logging.i(TAG, "In stateChangedHandler: receiverStartedLatch="+receiverStartedLatch);
            	isReceiverStarted = true;
            	receiverStartedLatch.countDown();
            }
			if (reason != 0)
			{
				sendStopToAllSenders();
				if (reason == CODEC_ERROR || reason == MEDIA_SERVER_HANG) {
					// Force a recovery of the Ducati codec and media server
					mStreamCtl.RecoverDucati();
					mStreamCtl.RecoverMediaServer();
					sleep(5000);
				}
				if (reason == MEDIA_SERVER_HANG || reason == M360_TIMEOUT || reason == MIRACAST_API_TIMEOUT || 
						reason == VIDEOPLAYER_API_TIMEOUT || reason == VIDEOPLAYER_REMOTE_EXCEPTION ||
						reason == RECEIVER_FAILED_TO_START_EXCEPTION || reason == RECEIVER_FAILED_TO_STOP_EXCEPTION) {
					Common.Logging.w(TAG, "Receiver " + to + " with error="+reason+"  Restarting receiver service .... ");
					restartAirMedia();
				} else {
					Common.Logging.w(TAG, "Receiver " + to + " with error="+reason+"  Restarting receiver asynchronously .... ");
					RestartReceiverAynchronously();
				}
				Common.Logging.w(TAG, "Exit from stateChangedHandler for error="+reason);
			}
        }
    };
    
    private final MulticastChangedDelegate.Observer<AirMediaReceiver, Integer> wifiApUsersCountChangedHandler_ = new MulticastChangedDelegate.Observer<AirMediaReceiver, Integer>() {
        @Override
        public void onEvent(AirMediaReceiver receiver, Integer from, Integer to) {
            Common.Logging.i(TAG, "view.receiver.event.wifiApUsersCount  " + from + "  ==>  " + to);
            sendClientDataWifiUsers(to);
        }
    };
    
    private final MulticastMessageDelegate.Observer<AirMediaSessionManager, AirMediaSession> addedHandler_ =
            new MulticastMessageDelegate.Observer<AirMediaSessionManager, AirMediaSession>() {
                @Override
                public void onEvent(AirMediaSessionManager manager, final AirMediaSession session) {
                    Common.Logging.i(TAG, "manager.sessions.event.added  " + AirMediaSession.toDebugString(session));
                    // Add code here to add session to "table" of sessions and take any action needed
                    registerSessionEventHandlers(session);
                    addSession(session);
                    if ((mCanvas==null) && (getSessionVideoState(session) == AirMediaSessionStreamingState.Playing))
                    {
                    	addActiveSession(session);
                    }
                }
            };

    private final MulticastMessageDelegate.Observer<AirMediaSessionManager, AirMediaSession> removedHandler_ =
            new MulticastMessageDelegate.Observer<AirMediaSessionManager, AirMediaSession>() {
                @Override
                public void onEvent(AirMediaSessionManager manager, final AirMediaSession session) {
                    Common.Logging.i(TAG, "manager.sessions.event.removed  " + AirMediaSession.toDebugString(session));
                    // Add code here to remove session from "table" of sessions and take any action needed;
                    removeSession(session);
                    if (getSessionVideoState(session) != AirMediaSessionStreamingState.Playing)
                    {
                    	  deleteActiveSessionWithFeedback(session);
                    }
                    // Strictly speaking this should not be needed since we should get deviceDisconnected event before a session removal event
                    // countdown the latch if for some reason the deviceDisconnected event is not received - seen once in 100 attempts
                    // with a MAC. (Bug 142802)
        			countdownDeviceDisconnectLatch(session);
                    // Strictly speaking this should not be needed since we should get connectionDisconnected event before a session removal event
                    // countdown the latch if for some reason the connectionDisconnected event is not received
        			countdownSessionDisconnectLatch(session);
        			
                    unregisterSessionEventHandlers(session);
                }
            };
            
    private final MulticastChangedDelegate.Observer<AirMediaSessionManager, AirMediaSessionScreenLayout> layoutChangedHandler_ =
    		new MulticastChangedDelegate.Observer<AirMediaSessionManager, AirMediaSessionScreenLayout>() {
            	@Override public void onEvent(AirMediaSessionManager manager, AirMediaSessionScreenLayout from, AirMediaSessionScreenLayout to) {
            		Common.Logging.i(TAG, "manager.sessions.event.layout  " + from + "  ==>  " + to);
            	}
            };

    private final MulticastChangedDelegate.Observer<AirMediaSessionManager, EnumSet<AirMediaSessionScreenPosition>> occupiedChangedHandler_ =
    		new MulticastChangedDelegate.Observer<AirMediaSessionManager, EnumSet<AirMediaSessionScreenPosition>>() {
            	@Override
            	public void onEvent(AirMediaSessionManager manager, EnumSet<AirMediaSessionScreenPosition> removed, EnumSet<AirMediaSessionScreenPosition> added) {
            		Common.Logging.i(TAG, "manager.sessions.event.occupied  OCCUPIED:  " + manager.occupied() + "  REMOVED: " + removed + "  ADDED: " + added);
            	}
            };
            
    //	Session Events
    private void registerSessionEventHandlers(AirMediaSession session) {
    	if (session == null) return;
    	unregisterSessionEventHandlers(session);
    	session.usernameChanged().register(usernameChangedHandler_);
    	session.addressesChanged().register(addressesChangedHandler_);
    	session.infoChanged().register(infoChangedHandler_);
    	session.connectionChanged().register(connectionChangedHandler_);
    	session.streamingChanged().register(streamingChangedHandler_);
    	session.channelStateChanged().register(channelStateChangedHandler_);
    	session.deviceStateChanged().register(deviceStateChangedHandler_);
    	session.videoStateChanged().register(videoStateChangedHandler_);
    	session.videoTypeChanged().register(videoTypeChangedHandler_);
    	session.videoResolutionChanged().register(videoResolutionChangedHandler_);
    	session.videoRotationChanged().register(videoRotationChangedHandler_);
    	session.videoSurfaceChanged().register(videoSurfaceChangedHandler_);
    	session.videoDrmChanged().register(videoDrmChangedHandler_);
    	session.videoLoadingChanged().register(videoLoadingChangedHandler_);
    	session.videoScreenPositionChanged().register(videoScreenPositionHandler_);
    	session.videoSurfaceRenewRequest().register(videoSurfaceRenewRequestHandler_);
    	session.audioStateChanged().register(audioStateChangedHandler_);
    	session.audioMuteChanged().register(audioMuteChangedHandler_);
    	session.audioVolumeChanged().register(audioVolumeChangedHandler_);
    	session.photoChanged().register(photoChangedHandler_);
    }

    private void unregisterSessionEventHandlers(AirMediaSession session) {
    	if (session == null) return;
    	session.usernameChanged().unregister(usernameChangedHandler_);
    	session.addressesChanged().unregister(addressesChangedHandler_);
    	session.infoChanged().unregister(infoChangedHandler_);
    	session.connectionChanged().unregister(connectionChangedHandler_);
    	session.streamingChanged().unregister(streamingChangedHandler_);
    	session.channelStateChanged().unregister(channelStateChangedHandler_);
    	session.deviceStateChanged().unregister(deviceStateChangedHandler_);
    	session.videoStateChanged().unregister(videoStateChangedHandler_);
    	session.videoTypeChanged().unregister(videoTypeChangedHandler_);
    	session.videoResolutionChanged().unregister(videoResolutionChangedHandler_);
    	session.videoRotationChanged().unregister(videoRotationChangedHandler_);
    	session.videoSurfaceChanged().unregister(videoSurfaceChangedHandler_);
    	session.videoDrmChanged().unregister(videoDrmChangedHandler_);
    	session.videoScreenPositionChanged().unregister(videoScreenPositionHandler_);
    	session.videoSurfaceRenewRequest().unregister(videoSurfaceRenewRequestHandler_);
    	session.audioStateChanged().unregister(audioStateChangedHandler_);
    	session.audioMuteChanged().unregister(audioMuteChangedHandler_);
    	session.audioVolumeChanged().unregister(audioVolumeChangedHandler_);
    	session.photoChanged().unregister(photoChangedHandler_);
    }
    
    private final MulticastChangedDelegate.Observer<AirMediaSession, String> usernameChangedHandler_ =
            new MulticastChangedDelegate.Observer<AirMediaSession, String>() {
                @Override
                public void onEvent(AirMediaSession session, String from, String to) {
                    Common.Logging.i(TAG, "view.session.event.username  " + AirMediaSession.toDebugString(session) + "  " + from + "  ==>  " + to);
                    // TODO Handle username changed
                    sendSessionFeedback(session);
                    sendClientDataUsername(session, to);
                    if (mCanvas != null)
                    {
                    	com.crestron.txrxservice.canvas.AirMediaSession s = canvasSessionMap.get(session.id());
                    	if (s != null)
                    	{
                    		s.setUserLabel(to);
                    	}
                    }
                }
            };

    private final MulticastChangedDelegate.Observer<AirMediaSession, Collection<String>> addressesChangedHandler_ =
    		new MulticastChangedDelegate.Observer<AirMediaSession, Collection<String>>() {
    	@Override
    	public void onEvent(AirMediaSession session, Collection<String> from, Collection<String> to) {
    		Common.Logging.i(TAG, "view.session.event.addresses  " + AirMediaSession.toDebugString(session) + "  " + from + "  ==>  " + to);
    		// TODO Handle addresses changed
    		sendSessionFeedback(session);
    		for (String s: to)
    		{
    			sendClientDataIpAddress(session, s);
    			break;
    		}
    	}
    };

    private final MulticastChangedDelegate.Observer<AirMediaSession, AirMediaSessionInfo> infoChangedHandler_ =
    		new MulticastChangedDelegate.Observer<AirMediaSession, AirMediaSessionInfo>() {
    	@Override
    	public void onEvent(AirMediaSession session, AirMediaSessionInfo from, AirMediaSessionInfo to) {
    		Common.Logging.i(TAG, "view.session.event.info  " + AirMediaSession.toDebugString(session) + "  " + from + "  ==>  " + to);
    		sendClientData(session, from, to);
    		if (from == null && mCanvas != null)
    		{
    			com.crestron.txrxservice.canvas.AirMediaSession s = canvasSessionMap.get(session.id());
    			if (s != null)
    			{
    				// did not know AirMediaType until now - set it and send connect session event
    				s.setAirMediaType(session);
    			}
    		}
    	}
    };

    private final MulticastChangedDelegate.Observer<AirMediaSession, AirMediaSessionConnectionState> connectionChangedHandler_ =
            new MulticastChangedDelegate.Observer<AirMediaSession, AirMediaSessionConnectionState>() {
                @Override
                public void onEvent(AirMediaSession session, AirMediaSessionConnectionState from, AirMediaSessionConnectionState to) {
                    Common.Logging.i(TAG, "view.session.event.connection  " + AirMediaSession.toDebugString(session) + "  " + from + "  ==>  " + to);
                    // Strictly speaking this should not be needed since we should get deviceDisconnected event before a connection disconnected event
                    // countdown the latch if for some reason the deviceDisconnected event is not received 
                    if (session.deviceState() == AirMediaSessionConnectionState.Disconnected)
                    {
                    	countdownDeviceDisconnectLatch(session);
                    }
                    if (session.connection() == AirMediaSessionConnectionState.Disconnected)
                    {
            			countdownSessionDisconnectLatch(session);
                    }
                    sendSessionFeedback(session);
                }
            };

    private final MulticastChangedDelegate.Observer<AirMediaSession, AirMediaSessionStreamingState> streamingChangedHandler_ =
            new MulticastChangedDelegate.Observer<AirMediaSession, AirMediaSessionStreamingState>() {
                @Override
                public void onEvent(AirMediaSession session, AirMediaSessionStreamingState from, AirMediaSessionStreamingState to) {
                    Common.Logging.i(TAG, "view.session.event.streaming  " + AirMediaSession.toDebugString(session) + "  " + from + "  ==>  " + to);
                    // TODO Handle streaming change
                }
            };

    private final MulticastChangedDelegate.Observer<AirMediaSession, AirMediaSessionConnectionState> channelStateChangedHandler_ =
            new MulticastChangedDelegate.Observer<AirMediaSession, AirMediaSessionConnectionState>() {
                @Override
                public void onEvent(AirMediaSession session, AirMediaSessionConnectionState from, AirMediaSessionConnectionState to) {
                    Common.Logging.i(TAG, "view.session.event.channel  " + AirMediaSession.toDebugString(session) + "  id= " + Long.toHexString(session.channelId()) + "  " + from + "  ==>  " + to);
                    // TODO Handle connection state change
                }
            };

    private final MulticastMessageDelegate.Observer<AirMediaSession, byte[]> receivedHandler_ =
            new MulticastMessageDelegate.Observer<AirMediaSession, byte[]>() {
                @Override
                public void onEvent(AirMediaSession session, byte[] message) {
                    Common.Logging.i(TAG, "view.session.event.message  " + AirMediaSession.toDebugString(session) + "  id= " + Long.toHexString(session.channelId()) + "  " + message.length + " bytes");
                }
            };

    private final MulticastChangedDelegate.Observer<AirMediaSession, AirMediaSessionConnectionState> deviceStateChangedHandler_ =
            new MulticastChangedDelegate.Observer<AirMediaSession, AirMediaSessionConnectionState>() {
                @Override
                public void onEvent(AirMediaSession session, AirMediaSessionConnectionState from, AirMediaSessionConnectionState to) {
                    Common.Logging.i(TAG, "view.session.event.device  " + AirMediaSession.toDebugString(session) + "  id= " + session.deviceId() + "  " + from + "  ==>  " + to);
                    if (session.deviceState() == AirMediaSessionConnectionState.Disconnected)
                    {
                    	countdownDeviceDisconnectLatch(session);
                    }
                }
            };

    private final MulticastChangedDelegate.Observer<AirMediaSession, AirMediaSessionStreamingState> videoStateChangedHandler_ =
            new MulticastChangedDelegate.Observer<AirMediaSession, AirMediaSessionStreamingState>() {
                @Override
                public void onEvent(AirMediaSession session, AirMediaSessionStreamingState from, AirMediaSessionStreamingState to) {
                    Common.Logging.i(TAG, "view.session.event.video.state  " + AirMediaSession.toDebugString(session) + "  " + from + "  ==>  " + to);
                    // TODO Handle video state change
                      setSessionVideoState(session, session.videoState());
                      if (getSessionVideoState(session) == AirMediaSessionStreamingState.Playing)
                      {
                    	  if (mCanvas != null)
                    	  {
                    		  com.crestron.txrxservice.canvas.AirMediaSession s = canvasSessionMap.get(session.id());
                    		  if (s != null)
                    		  {
                    			  s.setVideoState(AirMediaSessionStreamingState.Playing);
                    		  }
                  			  sendClientDataIsActiveSession(session, true);
                  			  querySenderList(false);
                    	  } else {
                    		  addActiveSession(session);
                    	  }
                      } 
                      else if (getSessionVideoState(session) == AirMediaSessionStreamingState.Stopped)
                      {
                    	  if (mCanvas != null)
                    	  {
                    		  com.crestron.txrxservice.canvas.AirMediaSession s = canvasSessionMap.get(session.id());
                    		  if (s != null)
                    		  {
                    			  s.setVideoState(AirMediaSessionStreamingState.Stopped);
                    		  }
                  			  sendClientDataIsActiveSession(session, false);
                  			  querySenderList(false);
                    	  } else {
                        	  deleteActiveSessionWithFeedback(session);
                    	  }
                      }
                      else if (getSessionVideoState(session) == AirMediaSessionStreamingState.Paused)
                      {
                    	  if (mCanvas != null)
                    	  {
                    		  com.crestron.txrxservice.canvas.AirMediaSession s = canvasSessionMap.get(session.id());
                    		  if (s != null)
                    		  {
                    			  s.setVideoState(AirMediaSessionStreamingState.Paused);
                    		  }
                  			  sendClientDataIsActiveSession(session, true);	
                    	  }
                      }
                }
            };

            private final MulticastChangedDelegate.Observer<AirMediaSession, AirMediaSessionVideoType> videoTypeChangedHandler_ =
                    new MulticastChangedDelegate.Observer<AirMediaSession, AirMediaSessionVideoType>() {
                        @Override
                        public void onEvent(AirMediaSession session, AirMediaSessionVideoType from, AirMediaSessionVideoType to) {
                            Common.Logging.i(TAG, "view.session.event.video.type  " + AirMediaSession.toDebugString(session) + "  " + from + "  ==>  " + to);
                            // update session type for session
                            sendClientDataSessionType(session);
                        }
                    };

    private final MulticastChangedDelegate.Observer<AirMediaSession, AirMediaSize> videoResolutionChangedHandler_ =
            new MulticastChangedDelegate.Observer<AirMediaSession, AirMediaSize>() {
                @Override
                public void onEvent(AirMediaSession session, AirMediaSize from, AirMediaSize to) {
                    Common.Logging.i(TAG, "view.session.event.video.size  " + AirMediaSession.toDebugString(session) + "  " + 
                    		from.width + "x" + from.height + "  ==>  " + to.width + "x" + to.height);
                    if (mStreamCtl.updateStreamStateOnFirstFrame[streamIdx]) {
                    	Log.i(TAG, "######### First frame based Stream State Started Update for streamId="+streamIdx+" ##############");
                    	mStreamCtl.sendAirMediaStartedState(streamIdx);
                    	mStreamCtl.setUpdateStreamStateOnFirstFrame(streamIdx, false);
                    }
                    // TODO Handle video resolution change
                    if (mCanvas != null)
                    {
                    	com.crestron.txrxservice.canvas.AirMediaSession s = canvasSessionMap.get(session.id());
                    	if (s != null)
                    	{
                    		s.setVideoResolution(session.videoResolution());
                    	}
                    }
                    sendClientDataVideoResolution(session);
                    if (session == getActiveSession())
                    {
                    	// If it is the active session apply
                    	setVideoTransformation();
                    }
                }
            };

    private final MulticastChangedDelegate.Observer<AirMediaSession, Integer> videoRotationChangedHandler_ =
            new MulticastChangedDelegate.Observer<AirMediaSession, Integer>() {
                @Override
                public void onEvent(AirMediaSession session, Integer from, Integer to) {
                    Common.Logging.i(TAG, "view.session.event.video.rotation  " + AirMediaSession.toDebugString(session) + "  " + from + "  ==>  " + to);
                    // TODO Handle video rotation change
                    if (session == getActiveSession())
                    {
                    	// If it is the active session apply
                    	setVideoTransformation();
                    }                
                }
            };

    private final MulticastChangedDelegate.Observer<AirMediaSession, Surface> videoSurfaceChangedHandler_ =
            new MulticastChangedDelegate.Observer<AirMediaSession, Surface>() {
                @Override
                public void onEvent(AirMediaSession session, Surface from, Surface to) {
                    Common.Logging.i(TAG, "view.session.event.video.surface  " + AirMediaSession.toDebugString(session) + "  " + from + "  ==>  " + to);
                    // TODO Handle video surface change change
                }
            };

    private final MulticastMessageDelegate.Observer<AirMediaSession, Boolean> videoDrmChangedHandler_ =
            new MulticastMessageDelegate.Observer<AirMediaSession, Boolean>() {
                @Override
                public void onEvent(AirMediaSession session, Boolean value) {
                    Common.Logging.i(TAG, "view.session.event.video.drm  " + AirMediaSession.toDebugString(session) + "  " + !value + "  ==>  " + value);
                    // TODO Handle video Drm change
                }
            };

    private final MulticastMessageDelegate.Observer<AirMediaSession, Boolean> videoLoadingChangedHandler_ =
		   new MulticastMessageDelegate.Observer<AirMediaSession, Boolean>() {
	           @Override
	           public void onEvent(AirMediaSession session, Boolean value) {
	        	   Common.Logging.i(TAG, "view.session.event.video.loading  " + AirMediaSession.toDebugString(session) + "  " + !value + "  ==>  " + value);
	        	   // TODO Handle video loading change
	        	   if (mCanvas != null) {
	        		   com.crestron.txrxservice.canvas.AirMediaSession s = canvasSessionMap.get(session.id());
	        		   if (s != null)
	        		   {
	        			   s.setIsVideoLoading(value);
	        		   }
	        	   }
	           }
           };
                    
    private final MulticastChangedDelegate.Observer<AirMediaSession, AirMediaSessionScreenPosition> videoScreenPositionHandler_ =
            new MulticastChangedDelegate.Observer<AirMediaSession, AirMediaSessionScreenPosition>() {
                @Override
                public void onEvent(AirMediaSession session, AirMediaSessionScreenPosition from, AirMediaSessionScreenPosition to) {
                    Common.Logging.i(TAG, "view.session.event.video.screen-position  " + AirMediaSession.toDebugString(session) + "  " + from + "  ==>  " + to);
                    // TODO Handle video screen position change
                    sendSessionFeedback(session);
                }
            };

    private final MulticastDelegate.Observer<AirMediaSession> videoSurfaceRenewRequestHandler_ =
            		new MulticastDelegate.Observer<AirMediaSession>() {
            	@Override
            	public void onEvent(AirMediaSession session) {
            		Common.Logging.i(TAG, "view.session.event.video.surface.renew.request  " + AirMediaSession.toDebugString(session));
            		// TODO Handle video screen position change
 	        	   if (mCanvas != null) {
	        		   com.crestron.txrxservice.canvas.AirMediaSession s = canvasSessionMap.get(session.id());
	        		   if (s != null)
	        		   {
	        			   s.renewSurfaceRequest();
	        		   }
	        	   }
            	}
            };
                    
    private final MulticastChangedDelegate.Observer<AirMediaSession, AirMediaSessionStreamingState> audioStateChangedHandler_ =
            new MulticastChangedDelegate.Observer<AirMediaSession, AirMediaSessionStreamingState>() {
                @Override
                public void onEvent(AirMediaSession session, AirMediaSessionStreamingState from, AirMediaSessionStreamingState to) {
                    Common.Logging.i(TAG, "view.session.event.audio.state  " + AirMediaSession.toDebugString(session) + "  " + from + "  ==>  " + to);
                    // TODO Handle audio state change
                }
            };

    private final MulticastMessageDelegate.Observer<AirMediaSession, Boolean> audioMuteChangedHandler_ =
            new MulticastMessageDelegate.Observer<AirMediaSession, Boolean>() {
                @Override
                public void onEvent(AirMediaSession session, Boolean value) {
                    Common.Logging.i(TAG, "view.session.event.audio.mute  " + AirMediaSession.toDebugString(session) + "  " + !value + "  ==>  " + value);
                    // TODO Handle audio mute change
 	        	   if (mCanvas != null) {
	        		   com.crestron.txrxservice.canvas.AirMediaSession s = canvasSessionMap.get(session.id());
	        		   if (s != null)
	        		   {
	        			   s.setIsAudioMuted(value);
	        		   }
	        	   }
                }
            };

    private final MulticastChangedDelegate.Observer<AirMediaSession, Float> audioVolumeChangedHandler_ =
            new MulticastChangedDelegate.Observer<AirMediaSession, Float>() {
                @Override
                public void onEvent(AirMediaSession session, Float from, Float to) {
                    Common.Logging.i(TAG, "view.session.event.audio.volume  " + AirMediaSession.toDebugString(session) + "  " + from + "  ==>  " + to);
                    // TODO Handle audio volume change
                }
            };

    private final MulticastChangedDelegate.Observer<AirMediaSession, Bitmap> photoChangedHandler_ =
            new MulticastChangedDelegate.Observer<AirMediaSession, Bitmap>() {
                @Override
                public void onEvent(AirMediaSession session, Bitmap from, Bitmap to) {
                    Common.Logging.i(TAG, "view.session.event.photo  " + AirMediaSession.toDebugString(session) + "  " + from + "  ==>  " + to);
                }
            };


    // Implement surface holder callback
    private final SurfaceHolder.Callback video_surface_callback_handler_ = new SurfaceHolder.Callback() {
    	@Override
    	public void surfaceCreated(SurfaceHolder holder) {
    		Surface surface = holder.getSurface();
    		Common.Logging.i(TAG, "surfaceCreated  surface= " + surface);
    		attachSurface();
    	}

    	@Override
    	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    		Surface surface = holder.getSurface();
    		Common.Logging.i(TAG, "surfaceChanged  surface= " +  surface + "  format= " + format + "  wxh= " + width + "x" + height);
    	}

    	@Override
    	public void surfaceDestroyed(SurfaceHolder holder) {
    		Common.Logging.i(TAG, "surfaceDestroyed  surface= " + holder.getSurface());
    		detachSurface();
    	}
    };
}
