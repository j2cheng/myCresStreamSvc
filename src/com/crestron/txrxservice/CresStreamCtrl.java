package com.crestron.txrxservice;

import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.String;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.*;

import android.net.Uri;
import android.os.Bundle;
import android.os.FileObserver;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.app.Service;
import android.os.IBinder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.Toast;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;
import android.provider.MediaStore.Files;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.BroadcastReceiver;
import android.media.AudioManager;
import android.view.Surface;
import android.view.Display;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.Camera;
import android.app.Activity;
import android.app.Notification;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManager.DisplayListener;

import com.crestron.txrxservice.CresStreamCtrl.StreamState;
import com.crestron.txrxservice.ProductSpecific;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

interface Command {
    void executeStart(int sessId);
}

interface myCommand {
    void executeStop(int sessId, boolean fullStop);
}

interface myCommand2 {
    void executePause(int sessId);
}

public class CresStreamCtrl extends Service {
	Handler handler;
    CameraStreaming cam_streaming;
    CameraPreview cam_preview;
    GstreamOut gstStreamOut;
    StringTokenizer tokenizer;
    public static int VersionNumber = 2;
    
    public static boolean useGstreamer = true;
    StreamIn streamPlay = null;
    GstreamBase gstreamBase = null;
    BroadcastReceiver hpdEvent = null;
    BroadcastReceiver resolutionEvent = null;
    BroadcastReceiver hdmioutResolutionChangedEvent = null;
    
    private DisplayManager m_displayManager = null;

    public UserSettings userSettings;
    AudioManager amanager;
    public TCPInterface sockTask;
   
    HDMIInputInterface hdmiInput;
    HDMIOutputInterface hdmiOutput;

    CresDisplaySurface dispSurface = null;
    
    AirMedia mAirMedia = null;
    
    final int cameraRestartTimout = 1000;//msec
    static int hpdHdmiEvent = 0;
    
    private volatile boolean saveSettingsShouldExit = false;
    public static Object saveSettingsLock = new Object();
    public static volatile boolean saveSettingsUpdateArrived = false;

    public final static int NumOfSurfaces = 3;
    public volatile boolean restartStreamsOnStart = false;
    static String TAG = "TxRx StreamCtrl";
    static String out_url="";
    static String playStatus="false";
    static String stopStatus="true";
    static String pauseStatus="false";
    boolean StreamOutstarted = false;
    boolean hdmiInputDriverPresent = false;
    boolean[] restartRequired = new boolean[NumOfSurfaces];
    public final static String savedSettingsFilePath = "/data/CresStreamSvc/userSettings";
    public final static String savedSettingsOldFilePath = "/data/CresStreamSvc/userSettings.old";
    public final static String cameraModeFilePath = "/dev/shm/crestron/CresStreamSvc/cameraMode";
    public final static String restoreFlagFilePath = "/data/CresStreamSvc/restore";
    public final static String restartStreamsFilePath = "/dev/shm/crestron/CresStreamSvc/restartStreams";
    public volatile boolean mMediaServerCrash = false;
    public volatile boolean mDucatiCrash = false;
    public volatile boolean mIgnoreAllCrash = false;
    private FileObserver mediaServerObserver;
    private FileObserver ravaModeObserver;
    private Thread monitorCrashThread;
    private boolean mHDCPOutputStatus = false;
    private boolean mHDCPExternalStatus = false;
    private boolean mHDCPInputStatus = false;
    private boolean/*[]*/ mHDCPEncryptStatus = false;//new boolean[NumOfSurfaces];
    private boolean/*[]*/ mTxHdcpActive = false;//new boolean[NumOfSurfaces];
    private boolean mIgnoreHDCP = false; //FIXME: This is for testing
    public volatile boolean mForceHdcpStatusUpdate = true;
    private int mPreviousValidHdmiInputResolution = 0;
    private int mPreviousAudioInputSampleRate = 0;
    public CountDownLatch streamingReadyLatch = new CountDownLatch(1);
    public volatile boolean enableRestartMechanism = false; // Until we get a start or platform automatically restarts don't restart streams
    private Object cameraModeLock = new Object();
    private volatile Timer mNoVideoTimer = null;
    private Object mCameraModeScheduleLock = new Object();
    private int defaultLoggingLevel = -1;
    private int numberOfVideoTimeouts = 0; //we will use this to track stop/start timeouts
    private final ProductSpecific mProductSpecific = new ProductSpecific();
    private final static String multicastTTLFilePath = "/dev/shm/crestron/CresStreamSvc/multicast_ttl";
    
    private final static String ducatiCrashCountFilePath = "/dev/shm/crestron/CresStreamSvc/ducatiCrashCount";
    public final static String gstreamerTimeoutCountFilePath = "/dev/shm/crestron/CresStreamSvc/gstreamerTimeoutCount";
    public final static String hdcpEncryptFilePath = "/dev/shm/crestron/CresStreamSvc/HDCPEncrypt";
    public int mGstreamerTimeoutCount = 0;
    public boolean haveExternalDisplays;
    public boolean hideVideoOnStop = false;
    public CrestronHwPlatform mHwPlatform;
    public boolean mCameraDisabled = false;
    private final int backgroundViewColor = Color.argb(255, 0, 0, 0);
    public OutputDisplayListener mDisplayListener = new OutputDisplayListener();
    private final Runnable foregroundRunnable = new Runnable() {
    	@Override
    	public void run() {
    		ForceServiceToForeground();
    	}
    };

    // JNI prototype
    public native boolean nativeHaveExternalDisplays();
    public native boolean nativeHideVideoBeforeStop();
    public native int nativeGetHWPlatformEnum();

    enum DeviceMode {
        STREAM_IN,
        STREAM_OUT,
        PREVIEW
    }
    
    enum AirMediaLoginMode {
    	Disabled(0),
    	Random(1),
    	Fixed(2);
    	
    	private final int value;
    	
    	AirMediaLoginMode(int value) 
        {
            this.value = value;
        }
    }
    
    enum CameraMode {
    	Camera(0),
    	Paused(1),
    	NoVideo(2),
    	HDCPStreamError(3),
    	HDCPAllError(4),
    	BlackScreen(5);
    	
    	private final int value;
    	
    	CameraMode(int value) 
        {
            this.value = value;
        }
    }
    
    /*
     * Copied from csio.h
     * */
    public enum StreamState 
    {
    	// Basic States
    	STOPPED(0),
    	STARTED(1),
    	PAUSED(2),

    	// Add additional states after this
    	CONNECTING(3),
    	RETRYING(4),
    	CONNECTREFUSED(5),
    	BUFFERING(6),
    	CONFIDENCEMODE(7),
    	STREAMERREADY(8),
    	HDCPREFUSED(9),

    	// Do not add anything after the Last state
    	LAST(10);
    	
        private final int value;

        StreamState(int value) 
        {
            this.value = value;
        }

        public int getValue() 
        {
            return value;
        }
        public static String getStringValueFromInt(int i) 
        {
            for (StreamState state : StreamState.values()) 
            {
                if (state.getValue() == i) 
                {
                    return state.toString();
                }
            }
            return ("Invalid State.");
        }
        
        public static StreamState getStreamStateFromInt(int i) 
        {
            for (StreamState state : StreamState.values()) 
            {
                if (state.getValue() == i) 
                {
                    return state;
                }
            }
            return LAST;
        }
    }
    
 // ***********************************************************************************
 // Keep updated with eHardwarePlatform in csioCommonShare.h !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
 // ***********************************************************************************
    public enum CrestronHwPlatform
    {
        eHardwarePlatform_iMx53,
        eHardwarePlatform_iMx6,
        eHardwarePlatform_OMAP5,
        eHardwarePlatform_Arria10,
    	eHardwarePlatform_Amlogic,
    	eHardwarePlatform_Unknown;
    	
        public static CrestronHwPlatform fromInteger(int x) {
        	switch(x) {
        	case 0:
        		return eHardwarePlatform_iMx53;
        	case 1:
        		return eHardwarePlatform_iMx6;
        	case 2:
        		return eHardwarePlatform_OMAP5;
        	case 3:
        		return eHardwarePlatform_Arria10;
        	case 4:
        		return eHardwarePlatform_Amlogic;
        	default:
				Log.i(TAG, String.format("Unknown hardware platform %d, please update enum!!!!!", x));
        		return eHardwarePlatform_Unknown;
        	}
        }
    }

   
    private final ReentrantLock hdmiLock				= new ReentrantLock(true); // fairness=true, makes lock ordered
    private final ReentrantLock[] windowtLock			= new ReentrantLock[NumOfSurfaces]; // members will be allocated in constructor
    private final ReentrantLock[] stopStartLock			= new ReentrantLock[NumOfSurfaces]; // members will be allocated in constructor
    private final ReentrantLock[] streamStateLock 		= new ReentrantLock[NumOfSurfaces]; // members will be allocated in constructor

    private Notification mNote = new Notification( 0, null, System.currentTimeMillis() );
    
    /**
     * Force the service to the foreground
     */
    public void ForceServiceToForeground()
    {
    	mNote.when = System.currentTimeMillis();
    	mNote.flags |= Notification.FLAG_NO_CLEAR;
        startForeground( 42, mNote );
    }
    
    /**
     * Keep running the forcing of the service foreground piece every 5 seconds
     * Might not be needed anymore, running CresStreamSvc as persistent service
     */
    public void RunNotificationThread()
    {
    	new Thread(new Runnable() {
    		public void run() {
		    	while (true)
		    	{
			    	runOnUiThread(foregroundRunnable);
			    	try {
			    		Thread.sleep(5000);
			    	} catch (Exception e) {
			    		e.printStackTrace();
			    	}
		    	}
    		}
    	}).start();
    }
    
	static {
		Log.d(TAG,"loading csio product info library" );
		System.loadLibrary("CsioProdInfo");
		Log.d(TAG,"loading cresstreamctrl jni library" );
		System.loadLibrary("cresstreamctrl_jni");
	}
    
    //StreamState devicestatus = StreamState.STOPPED;
    //HashMap
    HashMap<Integer, Command> hm;
    HashMap<Integer, myCommand> hm2;
    HashMap<Integer, myCommand2> hm3;
        
    @Override
        public void onCreate() {
    		// Create Handler onCreate so that it is always associated with UI thread (main thread)
    		handler = new Handler();
            super.onCreate();
    		int windowWidth = 1920;
    		int windowHeight = 1080;
    		hideVideoOnStop = nativeHideVideoBeforeStop();
    		mHwPlatform = CrestronHwPlatform.fromInteger(nativeGetHWPlatformEnum());
    		
    		m_displayManager = (DisplayManager) this.getApplicationContext().getSystemService(Context.DISPLAY_SERVICE);
	        if (m_displayManager != null)
	        {
	        	m_displayManager.registerDisplayListener(mDisplayListener, null);
	        }
    		
    		// This needs to be done before Gstreamer setup
    		mCameraDisabled = getCameraDisabled();
    		if (mCameraDisabled == true)
    			Log.w(TAG, "Camera is either disabled or not available, removing access");
    		else
    			Log.d(TAG, String.format("Camera is enabled, allowing access"));

    		// Wait until 2nd display has settled down.
    		// Android will kill this after 20 seconds!
    		// Tried waiting for just 10 seconds, didn't work.
    		// I guess 2nd display is not ready yet.
    		haveExternalDisplays = nativeHaveExternalDisplays();
    		if(haveExternalDisplays){
    			String startUpFilePath = "/dev/shm/crestron/CresStreamSvc/startup";
    			int val = 0;
    			try
    			{
    				val = Integer.parseInt(MiscUtils.readStringFromDisk(startUpFilePath));
    			} catch (Exception e) {}
    			if (val == 1)
    			{
	    			Log.d(TAG, "about to wait for external display(s)");
	    			for(int i=1; i<=15; i++)
	    			{
	    				SystemClock.sleep(1000);
	    				Log.d(TAG, "waited " + i + " sec");
	    			}
	    			Log.d(TAG, "done waiting for external display(s)");
	    			MiscUtils.writeStringToDisk(startUpFilePath, String.valueOf(1));
    			}
    		}
    		
    		//Start service connection
    		tokenizer = new StringTokenizer();
    		sockTask = new TCPInterface(this);
    		sockTask.execute(new Void[0]);  

    		// Allocate startStop and streamstate Locks, also initializing array
    		for (int sessionId = 0; sessionId < NumOfSurfaces; sessionId++)
    		{
    			stopStartLock[sessionId] = new ReentrantLock(true);
    			streamStateLock[sessionId] = new ReentrantLock(true);
    			windowtLock[sessionId] =  new ReentrantLock(true);
    			//            	mHDCPEncryptStatus[sessionId] = false;          	
    		}

            RunNotificationThread();		// No longer needed since we are an activity not a service

    		//Global Default Exception Handler
    		final Thread.UncaughtExceptionHandler oldHandler = Thread.getDefaultUncaughtExceptionHandler();

    		Thread.setDefaultUncaughtExceptionHandler(
    				new Thread.UncaughtExceptionHandler() {
    					@Override
    					public void uncaughtException(Thread paramThread, Throwable paramThrowable) {
    						//Close Camera   
    						Log.d(TAG,"Global uncaught Exception !!!!!!!!!!!" );
    						paramThrowable.printStackTrace();

    						try {
    							for (int sessionId = 0; sessionId < NumOfSurfaces; sessionId++)
    							{
    								if (getCurrentStreamState(sessionId) != StreamState.STOPPED)
    								{
    									if (userSettings.getMode(sessionId) == DeviceMode.PREVIEW.ordinal())
    									{
    										if (cam_preview != null)
    								    	{
    											cam_preview.stopPlayback(false);
    								    	}
    									}    										
    									else if (userSettings.getMode(sessionId) == DeviceMode.STREAM_IN.ordinal())
    										streamPlay.onStop(sessionId);
    									else if (userSettings.getMode(sessionId) == DeviceMode.STREAM_OUT.ordinal())
    										cam_streaming.stopRecording(true);//false
    								}
    							}
    						}
    						catch (Exception e) { 
    							Log.e(TAG, "Failed to stop streams on error exit");
    							e.printStackTrace(); 
    						}
    						if (oldHandler != null)
    							oldHandler.uncaughtException(paramThread, paramThrowable); //Delegates to Android's error handling
    						else
    							System.exit(2); //Prevents the service/app from freezing
    					}
    				});
    		
    		boolean wipeOutUserSettings = false;
    		boolean useOldUserSettingsFile = false;
    		boolean fixSettingsVersionMismatch = false;

    		File restoreFlagFile = new File(restoreFlagFilePath);
    		if (restoreFlagFile.isFile())
    		{
    			wipeOutUserSettings = true;
    			boolean deleteSuccess = restoreFlagFile.delete(); //delete restore flag since we handled it by wiping out userSettings
    			if (deleteSuccess == false)
    				Log.e(TAG, "Failed to delete restore file!");
    		}

    		File serializedClassFile = new File (savedSettingsFilePath);
    		if (serializedClassFile.isFile())	//check if file exists
    		{
    			// File exists deserialize it into userSettings
    			GsonBuilder builder = new GsonBuilder();
    			Gson gson = builder.create();
    			try
    			{
    				String serializedClass = new Scanner(serializedClassFile, "US-ASCII").useDelimiter("\\A").next();
    				try {
    					userSettings = gson.fromJson(serializedClass, UserSettings.class);
    					if (userSettings.getVersionNum() < VersionNumber)
    						fixSettingsVersionMismatch = true;
    				} catch (Exception ex) {
    					Log.e(TAG, "Failed to deserialize userSettings: " + ex);
    					useOldUserSettingsFile = true;
    				}
    			}
    			catch (Exception ex)
    			{
    				Log.e(TAG, "Exception encountered loading userSettings from disk: " + ex);
    				useOldUserSettingsFile = true;
    			}            	
    		}
    		else
    		{
    			Log.e(TAG, "UserSettings file did not exist");
    			useOldUserSettingsFile = true;
    		}

    		if (useOldUserSettingsFile) //userSettings deserialization failed try using old userSettings file
    		{            	
    			File serializedOldClassFile = new File (savedSettingsOldFilePath);
    			if (serializedOldClassFile.isFile())	//check if file exists 
    			{
    				Log.i(TAG, "Deserializing old userSettings file");
    				// File exists deserialize it into userSettings
    				GsonBuilder builder = new GsonBuilder();
    				Gson gson = builder.create();
    				try
    				{
    					String serializedClass = new Scanner(serializedOldClassFile, "US-ASCII").useDelimiter("\\A").next();
    					try {
    						userSettings = gson.fromJson(serializedClass, UserSettings.class);
    						if (userSettings.getVersionNum() < VersionNumber)
    							fixSettingsVersionMismatch = true;
    					} catch (Exception ex) {
    						Log.e(TAG, "Failed to deserialize userSettings.old: " + ex);
    						wipeOutUserSettings = true;
    					}
    				}
    				catch (Exception ex)
    				{
    					Log.e(TAG, "Exception encountered loading old userSettings from disk: " + ex);
    					wipeOutUserSettings = true;
    				}
    			}
    			else
    			{
    				Log.e(TAG, "Old userSettings file did not exist");
    				wipeOutUserSettings = true;
    			}
    		}

    		if (wipeOutUserSettings)
    		{
    			// File Does not exist, create it
    			try
    			{
    				userSettings = new UserSettings(); 
    				serializedClassFile.createNewFile();
    				saveUserSettings();
    			}
    			catch (Exception ex)
    			{
    				Log.e(TAG, "Could not create serialized class file: " + ex);
    			}
    		}
    		else
    		{
    			// Always upgrade userSettings
    			try
    			{           		
    				UserSettings savedUserSettings = userSettings;
    				userSettings = new UserSettings();	// Create new userSettings with default values   
    				UserSettings.fixVersionMismatch(savedUserSettings, userSettings); 	// Copy over previous values on top of defaults already set

    				if (fixSettingsVersionMismatch)
    				{
    					Log.d(TAG, "Saved userSettings version too old, upgrading settings, saved version: " 
    							+ userSettings.getVersionNum() + " required version: " + VersionNumber);
    				}

    				saveUserSettings();
    			}
    			catch (Exception ex)
    			{
    				Log.e(TAG, "Could not upgrade userSettings: " + ex);
    			}
    		}
    		
    		//Input Streamout Config
    		if (useGstreamer)
    		{
    			// If mediaserver is in bad state this could get stuck
    			// Load GstreamBase first!
    			final CountDownLatch latch = new CountDownLatch(1);
    			Thread startGstreamerThread = new Thread(new Runnable() {
    				public void run() {
    					gstreamBase = new GstreamBase(CresStreamCtrl.this);
    					latch.countDown();
    				}
    			});
    			startGstreamerThread.start();

    			boolean successfulStart = true; //indicates that there was no time out condition
    			try { successfulStart = latch.await(3000, TimeUnit.MILLISECONDS); }
    			catch (InterruptedException ex) { ex.printStackTrace(); }

    			// Library failed to load kill mediaserver and restart txrxservice
    			if (!successfulStart)
    			{
    				Log.e(TAG, "Gstreamer failed to initialize, restarting txrxservice and mediaserver");

    				RecoverMediaServer();
    				RecoverTxrxService();    		
    			}
    			else
    			{            		
    				if (defaultLoggingLevel != -1) //-1 means that value still has not been set
    				{
    					streamPlay.setLogLevel(defaultLoggingLevel);
    				}
    			}

    			// After gstreamer is initialized we can load gstreamIn and gstreamOut
    			streamPlay = new StreamIn(new GstreamIn(CresStreamCtrl.this));

    			// Added for real camera on x60
    			// to-do: support having both hdmi input and a real camera at the same time...
    			if(ProductSpecific.hasRealCamera())
    			{
    				gstStreamOut = new GstreamOut(CresStreamCtrl.this);
    				// PEM - uncomment if you want to enable camera preview for real camera.
    				// To-do: support platform that has an hdmi input and a real camera.
    				cam_preview = new CameraPreview(this, null);    
    			}

    		}
    		else
    			streamPlay = new StreamIn(new NstreamIn(CresStreamCtrl.this));    		

    		hdmiOutput = new HDMIOutputInterface();
    		setHDCPBypass();

    		Thread saveSettingsThread = new Thread(new SaveSettingsTask());    	
    		saveSettingsThread.start();

    		hdmiInputDriverPresent = ProductSpecific.isHdmiDriverPresent();
    		HDMIInputInterface.setHdmiDriverPresent(hdmiInputDriverPresent);

    		if (hdmiInputDriverPresent)
    		{
    			Log.d(TAG, "HDMI input driver is present");
    			hdmiInput = new HDMIInputInterface();
    			//refresh resolution on startup
    			hdmiInput.setResolutionIndex(HDMIInputInterface.readResolutionEnum(true));

    			// Call getHdmiInResolutionSysFs in a separate thread so that if read takes a long time we don't get ANR 
    			new Thread(new Runnable() {
    				public void run() {
    					refreshInputResolution();
    				}
    			}).start();
    		}
    		else
    			Log.d(TAG, "HDMI input driver is NOT present");

    		refreshOutputResolution();
    		sendHdmiOutSyncState(); // Send out initial hdmi out resolution info
    		
            // Create a DisplaySurface to handle both preview and stream in
    		createCresDisplaySurface();
    		
    		//Get HPDEVent state fromsysfile
    		if (hdmiInputDriverPresent)
    		{
    			hpdHdmiEvent = HDMIInputInterface.getHdmiHpdEventState();            
    			Log.d(TAG, "hpdHdmiEvent :" + hpdHdmiEvent);
    		}

    		//AudioManager
    		amanager=(AudioManager)getSystemService(Context.AUDIO_SERVICE);

    		//HPD and Resolution Event Registration
    		registerBroadcasts();

    		//Enable StreamIn and CameraPreview 
    		if (hdmiInputDriverPresent)
    		{
    			cam_streaming = new CameraStreaming(this);
    			cam_preview = new CameraPreview(this, hdmiInput);    
    			// Set up Ducati
    			ProductSpecific.getHdmiInputStatus();
    		}

    		//Play Control
    		hm = new HashMap<Integer, Command>();
    		hm.put(2/*"PREVIEW"*/, new Command() {
    			public void executeStart(int sessId) {startPreview(sessId); };
    		});
    		hm.put(1 /*"STREAMOUT"*/, new Command() {
    			public void executeStart(int sessId) {startStreamOut(sessId); };
    		});
    		hm.put(0 /*"STREAMIN"*/, new Command() {
    			public void executeStart(int sessId) {startStreamIn(sessId); };
    		});
    		hm2 = new HashMap<Integer, myCommand>();
    		hm2.put(2/*"PREVIEW"*/, new myCommand() {
    			public void executeStop(int sessId, boolean fullStop) {stopPreview(sessId);};
    		});
    		hm2.put(1/*"STREAMOUT"*/, new myCommand() {
    			public void executeStop(int sessId, boolean fullStop) {stopStreamOut(sessId, fullStop);};
    		});
    		hm2.put(0/*"STREAMIN"*/, new myCommand() {
    			public void executeStop(int sessId, boolean fullStop) {stopStreamIn(sessId); };
    		});
    		hm3 = new HashMap<Integer, myCommand2>();
    		hm3.put(2/*"PREVIEW"*/, new myCommand2() {
    			public void executePause(int sessId) {pausePreview(sessId);};
    		});
    		hm3.put(1/*"STREAMOUT"*/, new myCommand2() {
    			public void executePause(int sessId) {pauseStreamOut(sessId);};
    		});
    		hm3.put(0/*"STREAMIN"*/, new myCommand2() {
    			public void executePause(int sessId) {pauseStreamIn(sessId); };
    		});

    		// Flag to TCPInterface that streaming can start
    		streamingReadyLatch.countDown();

    		// Monitor mediaserver, if it crashes restart stream
    		monitorMediaServer();

    		// Monitor Crash State
    		monitorCrashState();

    		// Set HDCP error color to red
    		setHDCPErrorColor();

    		// Monitor System State
    		monitorSystemState();

    		// Monitor Rava Mode
    		monitorRavaMode();
    		
    		airMediaLicenseThread(this);
    		
    		initAppFiles();

    		// FIXME: this is a temprorary workaround for testing so that we can ignore HDCP state
    		File ignoreHDCPFile = new File ("/data/CresStreamSvc/ignoreHDCP");
    		if (ignoreHDCPFile.isFile())	//check if file exists
    			mIgnoreHDCP = true;
    		else
    			mIgnoreHDCP = false;

    		// Monitor the number of times the gstreamer 10 second timeout occurs
    		try
    		{
    			mGstreamerTimeoutCount = Integer.parseInt(MiscUtils.readStringFromDisk(gstreamerTimeoutCountFilePath));
    		} catch (Exception e)
    		{
    			mGstreamerTimeoutCount = 0;	// not an error condition, just default to 0 if file does not exist
    		}
    		MiscUtils.writeStringToDisk(gstreamerTimeoutCountFilePath, String.valueOf(mGstreamerTimeoutCount));    
    	}
   
    @Override
    public int onStartCommand (Intent intent, int flags, int startId) {
            Log.d(TAG,"CresStreamCtrl Started !" );            
            return START_STICKY;	// No longer needed since it is not a service
    }
    
    public IBinder onBind(Intent intent)
    {
        return null;
    } 

    public void onDestroy(){
        super.onDestroy();
        saveUserSettings();
        saveSettingsShouldExit = true;
        sockTask.cancel(true);
        mediaServerObserver.stopWatching();
        unregisterReceiver(resolutionEvent);
        unregisterReceiver(hpdEvent);
        unregisterReceiver(hdmioutResolutionChangedEvent);
        if (m_displayManager != null){
        	m_displayManager.unregisterDisplayListener(mDisplayListener);
        }
        try {
        	cam_streaming.stopRecording(false);
        	cam_preview.stopPlayback(false);
	        for (int i = 0; i < NumOfSurfaces; i++)
	        {
	        	streamPlay.onStop(i);
	        }
        } finally {}
        
        if (dispSurface != null)
        	dispSurface.RemoveView();
        
        CresCamera.releaseCamera();
    }
    
    private void runOnUiThread(Runnable runnable) {
    	// Android wants all surface methods to be run on UI thread, 
    	// Instability and/or crashes can occur if this is not observed
        handler.post(runnable);
    }
    
    private void initAppFiles() {
		// TODO: Lets setup all file folders needed here    	
		try 
    	{
			// Copy Cert file from assets folder
    		InputStream is = getAssets().open("ca-certificates.crt");
    		File file = new File(getApplicationInfo().dataDir + "/files/ssl/certs", "ca-certificates.crt");
    		file.getParentFile().mkdirs();	// make parent dirs if necessary
    		MiscUtils.copyInputStreamToFile(is, file);
    	}
    	catch (Exception ex)
    	{
    		Log.w(TAG, "Failed to copy cert file: " + ex);
    	}
    }
    
    public boolean getCameraDisabled()
    {
    	Camera c = null;
		boolean cameraDisabled = false;
		try {
			c = Camera.open(); // try to get camera
			c.release();	// relase camera if obtained
			cameraDisabled = false;
		}
		catch (RuntimeException e){
			cameraDisabled = true;
		}
		return cameraDisabled;
    }
    
 
    public SurfaceHolder getCresSurfaceHolder(final int sessionId){
    	SurfaceHolder surfaceHolder = null;
    	
    	// Make sure surface changes are only done in UI (main) thread
    	if (Looper.myLooper() != Looper.getMainLooper())
    	{
    		// We use a RunnableFuture because you cannot just instantiate a new SurfaceHolder
    		// This allows us to use the get() method to return the SurfaceHolder from the UI thread
	    	RunnableFuture<SurfaceHolder> runningThread = new RunnableFuture<SurfaceHolder>() {
				volatile SurfaceHolder sh;
				final CountDownLatch latch = new CountDownLatch(1);
				volatile boolean complete = false;
			     @Override
			     public void run() {		    	 		    	
	    			 sh = dispSurface.GetSurfaceHolder(sessionId);
		    		 latch.countDown();
			    	 complete = true;
			     }
	
				@Override
				public boolean cancel(boolean arg0) { return false; }
				
				@Override
				public SurfaceHolder get() throws InterruptedException,
						ExecutionException { 
					if (this.isDone() == false)
						latch.await();
					return sh; 
				}
	
				@Override
				public SurfaceHolder get(long arg0, TimeUnit arg1)
						throws InterruptedException, ExecutionException,
						TimeoutException { return sh; }
	
				@Override
				public boolean isCancelled() { return false; }
	
				@Override
				public boolean isDone() { return complete; }
	    	};
	    	
			runOnUiThread(runningThread);
			
			try { surfaceHolder = runningThread.get(); }
			catch (InterruptedException ex) { ex.printStackTrace(); }
			catch (ExecutionException ex) { ex.printStackTrace(); }
    	}
    	else
    		surfaceHolder = dispSurface.GetSurfaceHolder(sessionId);
    	
    	if (surfaceHolder != null)
    		Log.d(TAG, String.format("returned surface holder %s", surfaceHolder.toString()));
    	
    	return surfaceHolder;
    }
    
    private void airMediaLicenseThread(final CresStreamCtrl streamCtrl)
    {	
		new Thread(new Runnable() {
    		@Override
    		public void run() { 
    			// Wait until file exists then check
    			while ((new File(AirMedia.licenseFilePath)).exists() == false)
    			{
    				try { Thread.sleep(5000); } catch (InterruptedException e){}//Poll every 5 seconds
    			}
    			
    			if (mAirMedia == null && AirMedia.checkAirMediaLicense())
					mAirMedia = new AirMedia(streamCtrl);
    		}
    	}).start();
    }
    
    
    public void createCresDisplaySurface(){
    	final CresStreamCtrl streamCtrl = this;    	
    	
    	// Make sure surface changes are only done in UI (main) thread
    	if (Looper.myLooper() != Looper.getMainLooper())
    	{
    		final CountDownLatch latch = new CountDownLatch(1);
    		runOnUiThread(new Runnable() {
    			@Override
    			public void run() {
    				if (dispSurface != null)
    				{
    					dispSurface.close();
    					dispSurface = null;
    				}
    				dispSurface = new CresDisplaySurface(streamCtrl, 1920, 1200, haveExternalDisplays, backgroundViewColor); // set to max output resolution
    				latch.countDown();
    			}
    		});
    		try { 
        		if (latch.await(15, TimeUnit.SECONDS) == false)
        		{
        			Log.e(TAG, "createCresDisplaySurface: timeout after 15 seconds");
        			RecoverTxrxService();
        		}
        	}
        	catch (InterruptedException ex) { ex.printStackTrace(); }  
    	}
    	else
    	{
    		if (dispSurface != null)
    		{
    			dispSurface.close();
    			dispSurface = null;
    		}
    		dispSurface = new CresDisplaySurface(streamCtrl, 1920, 1200, haveExternalDisplays, backgroundViewColor); // set to max output resolution
    	}
    }
    
    private void monitorMediaServer()
    {
    	File mediaServerReboot = new File ("/dev/shm/crestron/CresStreamSvc/mediaServerState");
        if (!mediaServerReboot.isFile())	//check if file exists
        {
        	try {
            	mediaServerReboot.getParentFile().mkdirs(); 
            	mediaServerReboot.createNewFile();
        	} catch (Exception e) {}
        }
        mediaServerObserver = new FileObserver("/dev/shm/crestron/CresStreamSvc/mediaServerState", FileObserver.CLOSE_WRITE) {						
			@Override
			public void onEvent(int event, String path) {
				mMediaServerCrash = true;
			}
		};
		mediaServerObserver.startWatching();
    }
    
    private void monitorCrashState ()
    {
    	monitorCrashThread = new Thread(new Runnable() {
    		@Override
    		public void run() { 
    			int ducatiCrashCount;	//we will use this to record the number of times Ducati has crashed for statistical information
    			try
    			{
    				ducatiCrashCount = Integer.parseInt(MiscUtils.readStringFromDisk(ducatiCrashCountFilePath));
    			} catch (Exception e)
    			{
    				ducatiCrashCount = 0;	// not an error condition, just default to 0 if file does not exist
    			}
    			MiscUtils.writeStringToDisk(ducatiCrashCountFilePath, String.valueOf(ducatiCrashCount)); 
    			
    			// Clear out initial ducati flag since first boot is not a crash
    			writeDucatiState(1);
    			//wait for ducati state to be cleared (async call to csio)
    			try {
					Thread.sleep(5000);
				} catch (Exception e) { e.printStackTrace(); }

    			while (!Thread.currentThread().isInterrupted())
    			{
    				try {
    					Thread.sleep(1000);
    					
						//Check if ignoreAllCrash flag is set (this means we are handling resolution change)
						if (mIgnoreAllCrash == true)
						{
							continue;
						}
						
						if (sockTask.clientList.isEmpty() == false) // makes sure that csio is up so as not to spam the logs if it is not
						{
							// Check if Ducati Crashed
							int currentDucatiState = readDucatiState();
							if ((currentDucatiState == 0) && (mIgnoreAllCrash == false))
							{
								MiscUtils.writeStringToDisk(ducatiCrashCountFilePath, String.valueOf(++ducatiCrashCount)); 
								mPreviousValidHdmiInputResolution = 0;
								Log.i(TAG, "Recovering from Ducati crash!");
								recoverFromCrash();
							}
						}
						
						// Check if mediaserver crashed
						if ((mMediaServerCrash == true) && (mIgnoreAllCrash == false))
						{
							MiscUtils.writeStringToDisk(ducatiCrashCountFilePath, String.valueOf(++ducatiCrashCount)); 
							mPreviousValidHdmiInputResolution = 0;
							Log.i(TAG, "Recovering from mediaserver crash!");
							recoverFromCrash();
						}
						
						// Check if restartStreams was requested
						int restartStreamsState = readRestartStreamsState();
						if (restartStreamsState == 1)
						{
							recoverFromCrash();
						}
					} catch (Exception e) { 
						Log.e(TAG, "Problem occured in monitor thread!!!!");
						e.printStackTrace();
					}
    			}
    		}
    	});
    	
    	monitorCrashThread.start();
    }
    
    private void recoverFromCrash()
    {
    	restartStreams(false);
    }
    
    private int readDucatiState() {
		int ducatiState = 1;
        
    	StringBuilder text = new StringBuilder();
        try {
            File file = new File("/sys/kernel/debug/remoteproc/remoteproc0/ducati_recovered");

            BufferedReader br = new BufferedReader(new FileReader(file));  
            String line;   
            while ((line = br.readLine()) != null) {
                text.append(line);
            }
            br.close();
            ducatiState = Integer.parseInt(text.toString().trim());
        }catch (IOException e) {}
        
		return ducatiState;
	}
    
    private void writeDucatiState(int state) {
    	// we need csio to clear ducati state since sysfs needs root permissions to write
    	sockTask.SendDataToAllClients(String.format("CLEARDUCATISTATE=%d", state));
	}
    
    private int readRestartStreamsState() {
		int restartStreamsState = 0;
        
    	StringBuilder text = new StringBuilder();
        try {
            File file = new File(restartStreamsFilePath);

            BufferedReader br = new BufferedReader(new FileReader(file));  
            String line;   
            while ((line = br.readLine()) != null) {
                text.append(line);
            }
            br.close();
            restartStreamsState = Integer.parseInt(text.toString().trim());
        }catch (Exception e) {}
        
		return restartStreamsState;
	}
    
    private void writeRestartStreamsState(int state) {
    	Writer writer = null;
		try 
      	{
			writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(restartStreamsFilePath), "US-ASCII"));
		    writer.write(String.valueOf(state));
		    writer.flush();
	    } 
      	catch (Exception ex) {
    	  Log.e(TAG, "Failed to clearn restartStreams flag: " + ex);
    	} 
		finally 
    	{
    		try {writer.close();} catch (Exception ex) {/*ignore*/}
    	}
	}
    
    private void monitorSystemState() {
    	new Thread(new Runnable() {
    		@Override
    		public void run() {
    			// Poll input and output HDCP states once a second
    			while (!Thread.currentThread().isInterrupted())
    			{
    				// Sleep 1 second
    				try {
    					Thread.sleep(1000);
    				} catch (Exception e) { e.printStackTrace(); }
    				
    				hdmiLock.lock();
    				try
    				{
	    				// Query HDCP status
	    				boolean hdcpStatusChanged = checkHDCPStatus();
	    				if (hdcpStatusChanged) // Only send hdcp feedback if hdcp status has changed
	    					sendHDCPFeedbacks();
	    				
	    				// Query hdmiInput audio sampling frequency
	    				if (mIgnoreAllCrash != true) // Dont activate this code if we are handling hdmi input resolution change
	    				{
	    					int hdmiInSampleRate = HDMIInputInterface.readAudioSampleRate();
	    					// If sample frequency changes on the fly, restart stream
	    					if (hdmiInSampleRate != mPreviousAudioInputSampleRate)
	    					{
	    						mPreviousAudioInputSampleRate = hdmiInSampleRate;
	    						restartStreams(true); //skip stream in since it does not use hdmi input
	    					}
	    				}
    				} 
    				finally 
    				{
    					hdmiLock.unlock();
    				}
    			}
    		}
    	}).start();
    }
    
    private void monitorRavaMode()
    {
    	final String ravaModeFilePath = "/dev/shm/crestron/CresStreamSvc/ravacallMode";
    	final String audioDropDoneFilePath = "/dev/shm/crestron/CresStreamSvc/audiodropDone";
    	final Object ravaModeLock = new Object();
    	File ravaModeFile = new File (ravaModeFilePath);
        if (!ravaModeFile.isFile())	//check if file exists
        {
        	try {
        		ravaModeFile.getParentFile().mkdirs(); 
        		ravaModeFile.createNewFile();
        	} catch (Exception e) {}
        }
        
        // Set initial state, before file observing
        int initialRavaMode = readRavaMode(ravaModeFilePath);

		// Stop/Start all audio
        if (initialRavaMode == 1)
        {
        	Log.i(TAG, "Setting audio drop to true");
        	setAudioDropFlag(true);
        }
        else
        {
        	Log.i(TAG, "Setting audio drop to false");
        	setAudioDropFlag(false);
        }
        
        ravaModeObserver = new FileObserver(ravaModeFilePath, FileObserver.CLOSE_WRITE) {						
			@Override
			public void onEvent(int event, String path) {
				synchronized (ravaModeLock)
				{
					int ravaMode = readRavaMode(ravaModeFilePath);

					// Stop/Start all audio
			        if (ravaMode == 1)
			        {
			        	Log.i(TAG, "Setting audio drop to true");
			        	setAudioDropFlag(true);
			        }
			        else
			        {
			        	Log.i(TAG, "Setting audio drop to false");
			        	setAudioDropFlag(false);
			        }
			        
			        // Write Audio done
			        Writer writer = null;
	
		      		File audioDropDoneFile = new File(audioDropDoneFilePath);
		      		if (audioDropDoneFile.isFile())	//check if file exists
		            {
				      	try 
				      	{
		            		writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(audioDropDoneFilePath), "US-ASCII"));
				    	    writer.write("1");
				    	    writer.flush();
				      	}
			    	    catch (IOException ex) {
				    	  Log.e(TAG, "Failed to save audioDropDone to disk: " + ex);
				    	} finally 
				    	{
				    		try {writer.close();} catch (Exception ex) {/*ignore*/}
				    	}
		            }
				}
			}
		};
		ravaModeObserver.startWatching();
    }
    
    private int readRavaMode(String ravaModeFilePath)
    {
    	int ravaMode = 0;
        
		// Read rava mode
    	StringBuilder text = new StringBuilder();
        try {
            File file = new File(ravaModeFilePath);

            BufferedReader br = new BufferedReader(new FileReader(file));  
            String line;   
            while ((line = br.readLine()) != null) {
                text.append(line);
            }
            br.close();
            try {
            	ravaMode = Integer.parseInt(text.toString().trim());
            } catch (NumberFormatException e) { } //Ignore integer parse error, treat as 0
        }catch (IOException e) {}
		
        Log.i(TAG, "Received rava mode " + ravaMode);
		return ravaMode;
    }
    
    private void setAudioDropFlag(boolean enabled)
    {
    	userSettings.setRavaMode(enabled);    		
    	
    	for(int sessionId = 0; sessionId < NumOfSurfaces; sessionId++)
    	{
    		if (userSettings.getUserRequestedStreamState(sessionId) == StreamState.STARTED)
    		{
    			if (userSettings.getMode(sessionId) == DeviceMode.PREVIEW.ordinal())
    			{
    				if (cam_preview != null)
    				{
	    				if (enabled)
	    					cam_preview.stopAudio();
	    				else
	    					cam_preview.startAudio();
    				}
    			}
    			else if (userSettings.getMode(sessionId) == DeviceMode.STREAM_IN.ordinal())
    			{
					streamPlay.setAudioDrop(enabled, sessionId);
    			}
    			else
    				Log.e(TAG, "ERROR!!!! Rava mode was configured while streaming out!!!!");
    		}
    	}
    }
    
    public void restartStreams(final boolean skipStreamIn) 
    {
    	// If resolution change or crash occurs we don't want to restart until we know the system is up and stable
    	if (enableRestartMechanism == false)
    	{
    		// MNT - 1.26.16 - Even if the system is not up - This was causing the ducati
    		// engine to not start up in some scenarios correctly
    		writeDucatiState(1);
    		mMediaServerCrash = false;
    		
    		return;
    	}
    	
    	final CountDownLatch latch = new CountDownLatch(1);
    	
    	// Skip Stream in is when we need to only restart camera modes i.e. when resolution changes
    	new Thread(new Runnable() {
    		public void run() {	
    			// Make sure that CresStreamCtrl Constructor finishes before restarting 
    			try { streamingReadyLatch.await(); } catch (Exception e) {}
    			
    			for (int sessionId = 0; sessionId < NumOfSurfaces; sessionId++)
                {
    				stopStartLock[sessionId].lock();
                }
    	    	Log.i(TAG, "RestartLock : Lock");
    	    	try
    	    	{	
    	    		boolean restartStreamsCalled = false;
			    	Log.d(TAG, "Restarting Streams...");
			
			    	//If streamstate was previously started, restart stream
			        for (int sessionId = 0; sessionId < NumOfSurfaces; sessionId++)
			        {
			        	if (skipStreamIn && (userSettings.getMode(sessionId) == DeviceMode.STREAM_IN.ordinal()))
			        		continue;
			        	if ((userSettings.getMode(sessionId) == DeviceMode.STREAM_OUT.ordinal()) 
			        			&& (userSettings.getUserRequestedStreamState(sessionId) == StreamState.STOPPED))
			            {			
			        		restartStreamsCalled = true;

		    	    		cam_streaming.stopConfidencePreview(sessionId);
			    	    	
		    	    		try {
		    	    			Thread.sleep(1000);
		    	    		} catch (Exception e) {}
			    	    	
			    	    	// Clear crash flags after stop completes but before start
			    	    	clearErrorFlags();
			    	    	
		    	    		cam_streaming.startConfidencePreview(sessionId);
			            } 
			        	else if (userSettings.getUserRequestedStreamState(sessionId) == StreamState.STARTED)
			            {
			        		restartStreamsCalled = true;
			        		
			            	//Avoid starting confidence mode when stopping stream out
		            		Stop(sessionId, true);
		            		
		            		// Clear crash flags after stop completes but before start
		            		clearErrorFlags();

		            		// Fix bug 112972: We need to let MediaServer recover after error occurs
		            		try {
		            			Thread.sleep(1000);
		            		} catch (Exception e) { e.printStackTrace(); }

			            	Start(sessionId);
			            }                       
			        }
			        
			        // Clear crash flags if restart streams is not needed, otherwise no one will clear the flag
			        if (restartStreamsCalled == false)
			        {
			        	// We don't use clearErrorFlags() here because flags should be cleared immediately since we are not calling start/stop
	            		writeDucatiState(1);
	            		mMediaServerCrash = false;
			        }
	    		}
	    		finally
		    	{
		    		Log.i(TAG, "RestartLock : Unlock");
		    		for (int sessionId = 0; sessionId < NumOfSurfaces; sessionId++)
	                {
		    			stopStartLock[sessionId].unlock();
	                }
		    	}
    	    	
    	    	latch.countDown();
    		}
		}).start();
    	
    	//make call synchronous by waiting for completion before returning
    	try { latch.await(); }
    	catch (InterruptedException ex) { ex.printStackTrace(); }  
    }
    
    public void clearErrorFlags()
    {
    	// Clear mediaServer flag immediately because crash notification occurs immediately
		mMediaServerCrash = false;
		
    	new Thread(new Runnable() {    		
    		public void run() {	 
    			// Wait 3.5 seconds to clear the ducati flag because ducati does not notify of crash until ~3 seconds later
    			try {
    				Thread.sleep(3500);
    			} catch (Exception e) { e.printStackTrace(); }
    			writeDucatiState(1);
    		}
    	}).start();    	
    	
    	// Clear restartStreams flag
    	writeRestartStreamsState(0);
    }
    
    public void setDeviceMode(int mode, int sessionId)
    {
        Log.d(TAG, " setDeviceMode "+ mode);
        int prevMode = userSettings.getMode(sessionId);
        userSettings.setMode(mode, sessionId);
        
        // If hdmi input driver is present allow all 3 modes, otherwise only allow stream in mode
        // Only if mode actually changed
        if ((mode != prevMode) && (hdmiInputDriverPresent || (mode == DeviceMode.STREAM_IN.ordinal())))
        {
        	// Since this is a user request, mark as stopped requested if mode changes
        	userSettings.setUserRequestedStreamState(StreamState.STOPPED, sessionId);
        	
        	stopStartLock[sessionId].lock(); //Lock here to synchronize with restartStreams
        	StreamState currentStreamState = userSettings.getStreamState(sessionId);
        	StreamState currentUserReqStreamState = userSettings.getUserRequestedStreamState(sessionId);
        	stopStartLock[sessionId].unlock();
            if ( (currentStreamState != StreamState.STOPPED) || (currentUserReqStreamState != StreamState.STOPPED) )
                hm2.get(prevMode).executeStop(sessionId, false);
            
            if (mode == DeviceMode.STREAM_OUT.ordinal())
            {
            	// Bug 109256: send bitrate when changing to streamout mode
            	sockTask.SendDataToAllClients("VBITRATE=" + String.valueOf(userSettings.getBitrate(sessionId)));
            	
            	// we want confidence image up for stream out, until streamout is actually started
            	stopStartLock[sessionId].lock();
            	Log.d(TAG, "Start " + sessionId + " : Lock");
                try
                {
                	enableRestartMechanism = true; //enable restart detection
                	updateWindow(sessionId);
                    showPreviewWindow(sessionId);
                    invalidateSurface();
                	cam_streaming.startConfidencePreview(sessionId);
                } finally
                {
                	stopStartLock[sessionId].unlock();
                	Log.d(TAG, "Start " + sessionId + " : Unlock");
                }
            	restartRequired[sessionId] = true;
            }
            
            // Since we are changing mode, clear out stream url fb only (Bug 103801)
            if (mode == DeviceMode.STREAM_OUT.ordinal())
            {
            	// Only clear if in by receiver or multicast via rtsp, if By transmitter send saved url 
            	if ( (userSettings.getSessionInitiation(sessionId) == 0) || (userSettings.getSessionInitiation(sessionId) == 2) )			
            		sockTask.SendDataToAllClients(String.format("STREAMURL%d=", sessionId));
				else if (userSettings.getSessionInitiation(sessionId) == 1)
					sockTask.SendDataToAllClients(String.format("STREAMURL%d=%s", sessionId, userSettings.getStreamOutUrl(sessionId)));
            }
            else if (mode == DeviceMode.STREAM_IN.ordinal())
            {
            	// By transmitter clear, else send saved url
            	if (userSettings.getSessionInitiation(sessionId) == 1)
            		sockTask.SendDataToAllClients(String.format("STREAMURL%d=", sessionId));
            	else
            		sockTask.SendDataToAllClients(String.format("STREAMURL%d=%s", sessionId, userSettings.getStreamInUrl(sessionId)));
            }
        }
    }
    
    public void setUseGstreamer(boolean flag)
    {
    	if (flag != useGstreamer)
    	{
	    	for (int sessionId = 0; sessionId < NumOfSurfaces; sessionId++)
	    	{
	    		if ((userSettings.getMode(sessionId) == DeviceMode.STREAM_IN.ordinal())
	    				&& (getCurrentStreamState(sessionId) != StreamState.STOPPED))
	    		{
	    			Stop(sessionId, false);
	    		}
	    	}
	    	
	    	if (flag)
            	streamPlay = new StreamIn(new GstreamIn(CresStreamCtrl.this));
            else
            	streamPlay = new StreamIn(new NstreamIn(CresStreamCtrl.this));
	    	useGstreamer = flag;
    	}
    }
    
    public void setNewSink(boolean flag, int sessionId)
    {
        userSettings.setNewSink(flag, sessionId);
    	streamPlay.setNewSink(flag, sessionId);
    }
    
    public void setFieldDebugJni(String cmd, int sessId)
    {
        streamPlay.setFieldDebugJni(cmd, sessId);
    }
    
    public void resetAllWindows()
    {
    	for (int sessionId = 0; sessionId < NumOfSurfaces; sessionId++)
    	{
    		userSettings.setXloc(0, sessionId);
    		userSettings.setYloc(0, sessionId);
    		userSettings.setW(0, sessionId);
    		userSettings.setH(0, sessionId);
    		
    		updateWindow(sessionId);
    	}
    }
    
    public void setLogLevel(int logLevel)
    {
    	if (streamPlay != null)
    	{
    		streamPlay.setLogLevel(logLevel);
    	}
    	else
    	{
    		defaultLoggingLevel = logLevel; //since streamplay was not up mark inteded log level and set when streamPlay is created
    	}
    }
    
    public void setHdmiOutForceHdcp(boolean enabled) {
    	userSettings.setHdmiOutForceHdcp(enabled);
    	mForceHdcpStatusUpdate = true;
    }
    
    public void setWindowDimensions(int x, int y, int width, int height, int sessionId)
    {
    	userSettings.setXloc(x, sessionId);
    	userSettings.setYloc(y, sessionId);
    	userSettings.setW(width, sessionId);
    	userSettings.setH(height, sessionId);
    	updateWindow(sessionId);
    }

    public void setXCoordinates(int x, int sessionId)
    {    	
        if (userSettings.getXloc(sessionId) != x){
        	userSettings.setXloc(x, sessionId);
            updateXY(sessionId);
        }
    }

    public void setYCoordinates(int y, int sessionId)
    {
        if(userSettings.getYloc(sessionId) != y){
        	userSettings.setYloc(y, sessionId);
            updateXY(sessionId);
        }
    }

    public void setWindowSizeW(int w, int sessionId)
    {
        if(userSettings.getW(sessionId) != w){
        	userSettings.setW(w, sessionId);
            updateWH(sessionId);
        }
    }

    public void setWindowSizeH(int h, int sessionId)
    {
        if(userSettings.getH(sessionId) != h){
        	userSettings.setH(h, sessionId);
            updateWH(sessionId);
        }
    }
    
    public void setWindowSizeZ(int z, int sessionId)
    {
    	if (userSettings.getZ(sessionId) != z)
    	{
    		// Determine current zOrder before change
    		final Integer[][] zOrderOld = dispSurface.createZOrderArray();
	    	dispSurface.sortZOrderArray(zOrderOld);
	    	
    		userSettings.setZ(z, sessionId);
    		
    		// Determine whether zorder update is necessary
	    	boolean overlap = dispSurface.doWindowsOverlap();
	
	    	// Find zOrder after update
	    	// Index 0 is the sessionId value, Index 1 is the relative Z order saved in userSettings
	    	final Integer[][] zOrder = dispSurface.createZOrderArray();
	    	dispSurface.sortZOrderArray(zOrder);
	    	
	    	boolean zOrderUpdated = dispSurface.didZOrderChange(zOrderOld, zOrder);
	    	
	    	if ((overlap == true) && (zOrderUpdated == true))
	    	{  
	    		// All streams must be stopped before touching surfaces
				// Does not send userRequested Stop but sends feedback
	    		for (int i = 0; i < CresStreamCtrl.NumOfSurfaces; i++)
	            {
	    			Stop(i, true);
	            }
	    		
	    		// Make sure surface changes are only done in UI (main) thread
	        	if (Looper.myLooper() != Looper.getMainLooper())
	        	{
	            	final CountDownLatch latch = new CountDownLatch(1);
	
	            	runOnUiThread(new Runnable() {
		       		     @Override
		       		     public void run() {
		       		    	 dispSurface.updateZOrder(zOrder);
		       		    	 latch.countDown();
		       		     }
	            	});	            	
	
	            	try { latch.await(); }
	            	catch (InterruptedException ex) { ex.printStackTrace(); }  
	        	}
	        	else
	        	{
	        		dispSurface.updateZOrder(zOrder);
	        	}
	        	
	        	// Start streams back up
	        	restartStreams(false);
	    	}
    	}
    }

    public int getXCoordinates(int sessId)
    {
        return userSettings.getXloc(sessId);
    }

    public int getYCoordinates(int sessId)
    {
        return userSettings.getYloc(sessId);
    }

    public int getWindowSizeW(int sessId)
    {
        return userSettings.getW(sessId);
    }

    public int getWindowSizeH(int sessId)
    {
        return userSettings.getH(sessId);
    }
        
    public void updateWH(final int sessionId)
    {
        Log.d(TAG, "updateWH " + sessionId + " : Lock");
        windowtLock[sessionId].lock();
        try
        {
            if (dispSurface != null)
            {
            	// Make sure surface changes are only done in UI (main) thread
            	if (Looper.myLooper() != Looper.getMainLooper())
            	{
	            	final CountDownLatch latch = new CountDownLatch(1);
	
	            	runOnUiThread(new Runnable() {
		       		     @Override
		       		     public void run() {
		       		    	 int width = userSettings.getW(sessionId);
		       		    	 int height = userSettings.getH(sessionId);
		       		    	 if ((width == 0) && (height == 0))
		       		    	 {
		       		    		 width = Integer.parseInt(hdmiOutput.getHorizontalRes());
		       		    		 height = Integer.parseInt(hdmiOutput.getVerticalRes());
								 if ((width == 0) && (height == 0))
								 {
								 	width = 1920;
									height = 1080;
								 }
		       		    	 }
		       		    	 dispSurface.UpdateDimensions(width, height, sessionId);
		       		    	 latch.countDown();
		       		     }
	            	});	            	

	            	try { 
	            		if (latch.await(15, TimeUnit.SECONDS) == false)
	            		{
	            			Log.e(TAG, "updateWH: Timeout after 15 seconds");
	            			RecoverTxrxService();
	            		}
	            	}
	            	catch (InterruptedException ex) { ex.printStackTrace(); }  
            	}
            	else
            	{
            		int width = userSettings.getW(sessionId);
            		int height = userSettings.getH(sessionId);
            		if ((width == 0) && (height == 0))
            		{
            			width = Integer.parseInt(hdmiOutput.getHorizontalRes());
            			height = Integer.parseInt(hdmiOutput.getVerticalRes());
						if ((width == 0) && (height == 0))
						{
						 	width = 1920;
							height = 1080;
						}
            		}
            		dispSurface.UpdateDimensions(width, height, sessionId);
            	}
            }
        }
        finally
        {
        	windowtLock[sessionId].unlock();
            Log.d(TAG, "updateWH " + sessionId + " : Unlock");
        }

    }
    
    public void updateWindow(final int sessionId)
    {
    	// to avoid bug : we will set window dimensions and then set again after 10 seconds
    	updateFullWindow(sessionId);
    	new Timer().schedule(new doubleSendWindowDimensions(sessionId), 10000);
    }
    
    // !!!!!!! Do not call this function use updateWindow instead !!!!!!!
    private void updateFullWindow(final int sessionId)
    {
        Log.d(TAG, "updateFullWindow " + sessionId + " : Lock");
        windowtLock[sessionId].lock();
        try
        {
        	int tmpWidth = userSettings.getW(sessionId);
        	int tmpHeight = userSettings.getH(sessionId);
        	int tmpX = userSettings.getXloc(sessionId);
        	int tmpY = userSettings.getYloc(sessionId);
        	if ((tmpWidth == 0) && (tmpHeight == 0))
        	{
        		tmpWidth = Integer.parseInt(hdmiOutput.getHorizontalRes());
        		tmpHeight = Integer.parseInt(hdmiOutput.getVerticalRes());
        		if ((tmpWidth == 0) && (tmpHeight == 0))
        		{
        			tmpWidth = 1920;
        			tmpHeight = 1080;
        		}
        	}
        	
        	// Needs to be final so that we can pass to another thread
        	final int width = tmpWidth;
        	final int height = tmpHeight;
        	final int x = tmpX;
        	final int y = tmpY;
        	
            if (dispSurface != null)
            {
            	// Make sure surface changes are only done in UI (main) thread
            	if (Looper.myLooper() != Looper.getMainLooper())
            	{
	            	final CountDownLatch latch = new CountDownLatch(1);
	
	            	runOnUiThread(new Runnable() {
	            		@Override
	            		public void run() {
	            			dispSurface.UpdateWindowSize(x, y, width, height, sessionId);		       		    	 
	            			latch.countDown();
	            		}
	            	});	            	

	            	try { 
	            		if (latch.await(15, TimeUnit.SECONDS) == false)
	            		{
	            			Log.e(TAG, "updateFullWindow: Timeout after 15 seconds");
	            			RecoverTxrxService();
	            		}
	            	}
	            	catch (InterruptedException ex) { ex.printStackTrace(); }  
            	}
            	else
            	{
            		dispSurface.UpdateWindowSize(x, y, width, height, sessionId);
            	}
            }
        }
        finally
        {
        	windowtLock[sessionId].unlock();
            Log.d(TAG, "updateFullWindow " + sessionId + " : Unlock");
        }
    }
    
    public void hideWindowWithoutDestroy(final int sessionId)
    {
        Log.d(TAG, "updateWH " + sessionId + " : Lock");
        windowtLock[sessionId].lock();
        try
        {
            if (dispSurface != null)
            {
            	// Make sure surface changes are only done in UI (main) thread
            	if (Looper.myLooper() != Looper.getMainLooper())
            	{
	            	final CountDownLatch latch = new CountDownLatch(1);
	
	            	runOnUiThread(new Runnable() {
		       		     @Override
		       		     public void run() {
		       		    	dispSurface.UpdateDimensions(0, 0, sessionId);
			                latch.countDown();
		       		     }
	            	});	            	

	            	try { 
	            		if (latch.await(5, TimeUnit.SECONDS) == false)
	            		{
	            			Log.e(TAG, "hideWindowWithoutDestroy: timeout after 5 seconds");
	            			RecoverTxrxService();
	            		}
	            	}
	            	catch (InterruptedException ex) { ex.printStackTrace(); }  
            	}
            	else
            		dispSurface.UpdateDimensions(0, 0, sessionId);
            }
        }
        finally
        {
        	windowtLock[sessionId].unlock();
            Log.d(TAG, "updateWH " + sessionId + " : Unlock");
        }

    }
    
    public void invalidateSurface()
    {
    	if (dispSurface != null)
        {
    		// Make sure surface changes are only done in UI (main) thread
        	if (Looper.myLooper() != Looper.getMainLooper())
        	{
        		final CountDownLatch latch = new CountDownLatch(1);
        		runOnUiThread(new Runnable() {
	       		     @Override
	       		     public void run() {
		                dispSurface.forceParentLayoutInvalidation();
		                latch.countDown();
	       		     }
        		});
        		try { 
            		if (latch.await(5, TimeUnit.SECONDS) == false)
            		{
            			Log.e(TAG, "invalidateSurface: timeout after 5 seconds");
            			RecoverTxrxService();
            		}
            	}
            	catch (InterruptedException ex) { ex.printStackTrace(); }  
        	}
        	else
        		dispSurface.forceParentLayoutInvalidation();        		
        }
    }
    
    public void updateXY(final int sessionId)
    {
    	
    	Log.d(TAG, "updateXY " + sessionId + " : Lock");
    	windowtLock[sessionId].lock();
    	
    	try
    	{
        	if (dispSurface != null)
            {
        		// Make sure surface changes are only done in UI (main) thread
            	if (Looper.myLooper() != Looper.getMainLooper())
            	{
	        		final CountDownLatch latch = new CountDownLatch(1);
	        		runOnUiThread(new Runnable() {
		       		     @Override
		       		     public void run() {
			                dispSurface.UpdateCoordinates(userSettings.getXloc(sessionId), 
			                		userSettings.getYloc(sessionId), sessionId); 
			                latch.countDown();
		       		     }
	        		});
	        		try { 
	            		if (latch.await(5, TimeUnit.SECONDS) == false)
	            		{
	            			Log.e(TAG, "updateXY: timeout after 5 seconds");
	            			RecoverTxrxService();
	            		}
	            	}
	            	catch (InterruptedException ex) { ex.printStackTrace(); }  
            	}
            	else
            		dispSurface.UpdateCoordinates(userSettings.getXloc(sessionId), 
	                		userSettings.getYloc(sessionId), sessionId);
            		
            }
        	
        	// Gstreamer needs X and Y locations and does not update itself like it does for width and height
        	if (userSettings.getMode(sessionId) == DeviceMode.STREAM_IN.ordinal()) 
        		streamPlay.updateCurrentXYloc(userSettings.getXloc(sessionId), userSettings.getYloc(sessionId), sessionId);
    	}
    	finally
    	{
    		windowtLock[sessionId].unlock();
        	Log.d(TAG, "updateXY " + sessionId + " : Unlock");
    	}
    }

    public void readResolutionInfo(String hdmiInputResolution){
    	if (hdmiInputDriverPresent)
    		hdmiInput.updateResolutionInfo(hdmiInputResolution);
    }
    
    void refreshInputResolution()
    {
        Log.i(TAG, "Refresh resolution info");
        
        
    	
        if (HDMIInputInterface.readSyncState() == true)
        {
	    	//HDMI In
	        String hdmiInputResolution = HDMIInputInterface.getHdmiInResolutionSysFs();	
	    	readResolutionInfo(hdmiInputResolution);
        }
        else
        {
        	// If no sync then all values should be zeroed out
        	hdmiInput.updateResolutionInfo("0x0@0");
        }
    }

	public void refreshOutputResolution() {
		ProductSpecific.DispayInfo hdmiOutputResolution;
		//HDMI Out		
		if (haveExternalDisplays)
		{
			hdmiOutputResolution = mProductSpecific.new DispayInfo();
			WindowManager wm = (WindowManager) this.getSystemService(Context.WINDOW_SERVICE);
			DisplayManager dm = (DisplayManager) getApplicationContext().getSystemService(Context.DISPLAY_SERVICE);
			if (dm != null){
				Display dispArray[] = dm.getDisplays();
				if (dispArray.length>1){
					Context displayContext = getApplicationContext().createDisplayContext(dispArray[1]);	// querying display 1
					wm = (WindowManager)displayContext.getSystemService(Context.WINDOW_SERVICE);
				}
			}
			else
			{
				
				Log.e(TAG, "Unable to query second display size, using primary display");
			}


			Display display = wm.getDefaultDisplay();
			Point size = new Point();
			display.getSize(size);

			hdmiOutputResolution.width = size.x;
			hdmiOutputResolution.height = size.y;
			hdmiOutputResolution.refreshRate = display.getRefreshRate();
		}
		else
		{
//			hdmiOutputResolution = mProductSpecific.new DispayInfo(); // Old Mistral API
			
			hdmiOutputResolution = mProductSpecific.new DispayInfo();
			WindowManager wm = (WindowManager) this.getSystemService(Context.WINDOW_SERVICE);
			DisplayManager dm = (DisplayManager) getApplicationContext().getSystemService(Context.DISPLAY_SERVICE);
			if (dm != null){
				Display dispArray[] = dm.getDisplays();
				if (dispArray.length>1){
					Context displayContext = getApplicationContext().createDisplayContext(dispArray[0]); // querying display 0
					wm = (WindowManager)displayContext.getSystemService(Context.WINDOW_SERVICE);
				}
			}
			else
			{
				
				Log.e(TAG, "Unable to query first display size, using default display");
			}


			Display display = wm.getDefaultDisplay();
			Point size = new Point();
			display.getSize(size);

			hdmiOutputResolution.width = size.x;
			hdmiOutputResolution.height = size.y;
			hdmiOutputResolution.refreshRate = display.getRefreshRate();
		}

    	Log.i(TAG, "HDMI Out Resolution " + hdmiOutputResolution.width + " "
    			+ hdmiOutputResolution.height + " "
    			+ Math.round(hdmiOutputResolution.refreshRate));
    	
    	hdmiOutput.setSyncStatus();
    	
    	if (Boolean.parseBoolean(hdmiOutput.getSyncStatus()) == true)
		{
	        hdmiOutput.setHorizontalRes(Integer.toString(hdmiOutputResolution.width));
	        hdmiOutput.setVerticalRes(Integer.toString(hdmiOutputResolution.height));
	        hdmiOutput.setFPS(Integer.toString(Math.round(hdmiOutputResolution.refreshRate)));
	        hdmiOutput.setAudioFormat(Integer.toString(1));
	        hdmiOutput.setAudioChannels(Integer.toString(2));
		}
		else
		{
			hdmiOutput.setHorizontalRes(Integer.toString(0));
	        hdmiOutput.setVerticalRes(Integer.toString(0));
	        hdmiOutput.setFPS(Integer.toString(0));
	        hdmiOutput.setAudioFormat(Integer.toString(0));
	        hdmiOutput.setAudioChannels(Integer.toString(0));
		}

        hdmiOutput.setAspectRatio();
	}
    
    public String getHDMIInSyncStatus()
    {
    	if (hdmiInputDriverPresent)
    		return hdmiInput.getSyncStatus();
    	else
    		return "false";
    }

    public String getHDMIInInterlacing()
    {
    	if (hdmiInputDriverPresent)
    		return hdmiInput.getInterlacing();
    	else
    		return "false";
    }
    
    public String getHDMIInHorizontalRes()
    {
    	if (hdmiInputDriverPresent)
    		return hdmiInput.getHorizontalRes();
    	else
    		return "0";
    }
    
    public String getHDMIInVerticalRes()
    {
    	if (hdmiInputDriverPresent)
    		return hdmiInput.getVerticalRes();
    	else
    		return "0";
    }

    public String getHDMIInFPS()
    {
    	if (hdmiInputDriverPresent)
    		return hdmiInput.getFPS();
    	else
    		return "0";
    }

    public String getHDMIInAspectRatio()
    {
    	if (hdmiInputDriverPresent)
    		return hdmiInput.getAspectRatio();
    	else
    		return "0";
    }

    public String getHDMIInAudioFormat()
    {
    	if (hdmiInputDriverPresent)
    		return hdmiInput.getAudioFormat();
    	else
    		return "0";
    }

    public String getHDMIInAudioChannels()
    {
    	if (hdmiInputDriverPresent)
    		return hdmiInput.getAudioChannels();
    	else
    		return "0";
    }

    public String getHDMIInAudioSampleRate()
    {
    	if (hdmiInputDriverPresent)
    		return hdmiInput.getAudioSampleRate();
    	else
    		return "0";
    }
    
    public String getHDMIOutSyncStatus()
    {
    	return hdmiOutput.getSyncStatus();
    }

    public String getHDMIOutInterlacing()
    {
    	return hdmiOutput.getInterlacing();
    }
    
    public String getHDMIOutHorizontalRes()
    {
    	return hdmiOutput.getHorizontalRes();
    }
    
    public String getHDMIOutVerticalRes()
    {
    	return hdmiOutput.getVerticalRes();
    }

    public String getHDMIOutFPS()
    {
    	return hdmiOutput.getFPS();
    }
    
    public String getHDMIOutAspectRatio()
    {
    	return hdmiOutput.getAspectRatio();
    }

    public String getHDMIOutAudioFormat()
    {
    	return hdmiOutput.getAudioFormat();
    }

    public String getHDMIOutAudioChannels()
    {
    	return hdmiOutput.getAudioChannels();
    }
    
    public void setSystemVolume(int volume)
    {    	
    	// Stream Out preview audio will be placed on the unused ALARM stream
    	amanager.setStreamVolume(AudioManager.STREAM_ALARM, volume * amanager.getStreamMaxVolume(AudioManager.STREAM_ALARM) / 100, 0);
    }
    
    private void setPreviewVolume(int volume)
    {
    	if (cam_preview != null)
    		cam_preview.setVolume(volume);
    }
    
    private void setStreamInVolume(int volume, int sessionId)
    {
    	streamPlay.setVolume(volume, sessionId);
    }
    
    public void setStreamVolume(double volume) 
    {
    	//Volume of -1 means setting mute
    	//If user sets volume while in muted mode, save new volume in previousVolume
    	if ((userSettings.isAudioMute() == true) && (volume >= (double)0.0))
    	{
			userSettings.setUserRequestedVolume(volume);
    	}
    	else
    	{
    		// If Audio is unmuted always setUserRequested volume to new volume value
    		if ((userSettings.isAudioUnmute() == true) && (volume >= (double)0.0))
    		{
    			userSettings.setUserRequestedVolume(volume);
    		}
    		
    		// Negative volume means that we are trying to mute
    		if (volume < (double)0.0)
    			volume = (double)0.0;
    		
    		userSettings.setVolume(volume);
    		
	    	for (int sessionId = 0; sessionId < NumOfSurfaces; sessionId++)
	    	{
	    		if (userSettings.getMode(sessionId) == DeviceMode.PREVIEW.ordinal())
	    			setPreviewVolume((int)volume);
	    		else if (userSettings.getMode(sessionId) == DeviceMode.STREAM_IN.ordinal())
	    			setStreamInVolume((int)volume, sessionId);
	    		else if (userSettings.getMode(sessionId) == DeviceMode.STREAM_OUT.ordinal())
	    		{
	    			if (getCurrentStreamState(sessionId) == StreamState.CONFIDENCEMODE)
	    				setPreviewVolume((int)volume);
	    			else
	    				setSystemVolume((int)volume);
	    		}
	    	}
    	}
    }

    public void setStreamMute()
    {    	
    	userSettings.setAudioMute(true);
        userSettings.setAudioUnmute(false);
        
        setStreamVolume((double)-1.0);
        sockTask.SendDataToAllClients("AUDIO_UNMUTE=false");
    }
    
    public void setStreamUnMute()
    {
    	userSettings.setAudioMute(false);
        userSettings.setAudioUnmute(true);
    	setStreamVolume(userSettings.getUserRequestedVolume());
    	sockTask.SendDataToAllClients("AUDIO_MUTE=false");
    }
    
    public void setProcessHdmiInAudio(boolean flag)
    {
    	userSettings.setProcessHdmiInAudio(flag);
    	if(flag)
    	{
			sockTask.SendDataToAllClients("PROCESS_HDMI_IN_AUDIO=true");
		}
		else
		{
			sockTask.SendDataToAllClients("PROCESS_HDMI_IN_AUDIO=false");
		}			
    }
    
    public void SetPasswdEnable(int sessId)
    {
        StringBuilder sb = new StringBuilder(512);
        userSettings.setPasswordEnable(true, sessId);
        userSettings.setPasswordDisable(false, sessId);
        sb.append("PASSWORD_DISABLE=FALSE");
        sockTask.SendDataToAllClients(sb.toString());
    }

    public void SetPasswdDisable(int sessId)
    {
        StringBuilder sb = new StringBuilder(512);
        userSettings.setPasswordEnable(false, sessId);
        userSettings.setPasswordDisable(true, sessId);
        sb.append("PASSWORD_ENABLE=FALSE");
        sockTask.SendDataToAllClients(sb.toString());
    }

    public void SetUserName(String uname, int sessId){
    	userSettings.setUserName(uname, sessId);
    }

    public void SetPasswd(String passwd, int sessId){
    	userSettings.setPassword(passwd, sessId);
    }
    
    public void SendStreamInVideoFeedbacks(int source, int width, int height, int framerate, int profile)
    {	    
	    sockTask.SendDataToAllClients(String.format("STREAMIN_HORIZONTAL_RES_FB%d=%s", source, width));
	    sockTask.SendDataToAllClients(String.format("STREAMIN_VERTICAL_RES_FB%d=%s", source, height));
	    sockTask.SendDataToAllClients(String.format("STREAMIN_FPS_FB%d=%s", source, framerate));
	    sockTask.SendDataToAllClients(String.format("STREAMIN_ASPECT_RATIO%d=%s", source, MiscUtils.calculateAspectRatio(width, height)));
	    sockTask.SendDataToAllClients(String.format("VENCPROFILE%d=%s", source, profile));

	    sockTask.SendDataToAllClients("STREAMIN_AUDIO_FORMAT=" + String.valueOf(streamPlay.getMediaPlayerAudioFormatFb()));
	    sockTask.SendDataToAllClients("STREAMIN_AUDIO_CHANNELS=" + String.valueOf(streamPlay.getMediaPlayerAudiochannelsFb()));
    }
    
    public void SendStreamInFeedbacks()
    {
    	if(userSettings.isStatisticsEnable(0))
    	{
    		sockTask.SendDataToAllClients("STATISTICS_NUMBEROFVIDEOPACKETS=" + String.valueOf(streamPlay.getStreamInNumVideoPackets()));
    	   	sockTask.SendDataToAllClients("STATISTICS_NUMBEROFVIDEOPACKETSDROPPED=" + String.valueOf(streamPlay.getStreamInNumVideoPacketsDropped()));
    	   	sockTask.SendDataToAllClients("STATISTICS_NUMBEROFAUDIOPACKETS=" + String.valueOf(streamPlay.getStreamInNumAudioPackets()));
    	   	sockTask.SendDataToAllClients("STATISTICS_NUMBEROFAUDIOPACKETSDROPPED=" + String.valueOf(streamPlay.getStreamInNumAudioPacketsDropped()));    	
    	}
    	
    	if (streamPlay.getStreamInBitrate() >= 0) //negative value means don't send feedback
    		sockTask.SendDataToAllClients("VBITRATE=" + String.valueOf(streamPlay.getStreamInBitrate()));
    }
    
    public void SendStreamOutFeedbacks()
    {
    	sockTask.SendDataToAllClients("STREAMOUT_HORIZONTAL_RES_FB=" + String.valueOf(cam_streaming.getStreamOutWidth()));
    	sockTask.SendDataToAllClients("STREAMOUT_VERTICAL_RES_FB=" + String.valueOf(cam_streaming.getStreamOutHeight()));
    	sockTask.SendDataToAllClients("STREAMOUT_FPS_FB=" + String.valueOf(cam_streaming.getStreamOutFpsFb()));
    	sockTask.SendDataToAllClients("STREAMOUT_ASPECT_RATIO=" + String.valueOf(cam_streaming.getStreamOutAspectRatioFb()));
    	sockTask.SendDataToAllClients("STREAMOUT_AUDIO_FORMAT=" + String.valueOf(cam_streaming.getStreamOutAudioFormatFb()));
    	sockTask.SendDataToAllClients("STREAMOUT_AUDIO_CHANNELS=" + String.valueOf(cam_streaming.getStreamOutAudiochannelsFb()));
    	
    	sockTask.SendDataToAllClients("STATISTICS_NUMBEROFVIDEOPACKETS=" + String.valueOf(cam_streaming.getStreamOutNumVideoPackets()));
    	sockTask.SendDataToAllClients("STATISTICS_NUMBEROFVIDEOPACKETSDROPPED=" + String.valueOf(cam_streaming.getStreamOutNumVideoPacketsDropped()));
    	sockTask.SendDataToAllClients("STATISTICS_NUMBEROFAUDIOPACKETS=" + String.valueOf(cam_streaming.getStreamOutNumAudioPackets()));
    	sockTask.SendDataToAllClients("STATISTICS_NUMBEROFAUDIOPACKETSDROPPED=" + String.valueOf(cam_streaming.getStreamOutNumAudioPacketsDropped()));
    }
    
    public StreamState getCurrentStreamState(int sessionId)
    {
    	StreamState returnStreamState;
    	Log.d(TAG, "StreamState " + sessionId + " : Lock");
    	streamStateLock[sessionId].lock();
    	try
    	{
    		returnStreamState = userSettings.getStreamState(sessionId);
    	}
    	finally 
    	{
        	streamStateLock[sessionId].unlock();
        	Log.d(TAG, "StreamState " + sessionId + " : Unlock");
    	}
    	return returnStreamState;
    }
    
    public void SendStreamState(StreamState state, int sessionId)
    {
    	Log.d(TAG, "StreamState " + sessionId + " : Lock");
    	streamStateLock[sessionId].lock();
    	try
    	{
        	userSettings.setStreamState(state, sessionId);
        	CresStreamCtrl.saveSettingsUpdateArrived = true; // flag userSettings to save
	        StringBuilder sb = new StringBuilder(512);
	        String streamStateText = "STREAMSTATE" + String.valueOf(sessionId);
	        
	        sb.append(streamStateText + "=").append(state.getValue());
	        sockTask.SendDataToAllClients(sb.toString());
    	}
        finally 
    	{
        	streamStateLock[sessionId].unlock();
        	Log.d(TAG, "StreamState " + sessionId + " : Unlock");
    	}
    }

    //Ctrls
    public void Start(int sessionId)
    {
		ProductSpecific.doChromakey(true);
    	enableRestartMechanism = true; //if user starts stream allow restart mechanism
    	
    	if ((getCurrentStreamState(sessionId) != StreamState.STARTED) && (userSettings.getStreamState(sessionId) != StreamState.STREAMERREADY))
    	{	    	
	    	stopStartLock[sessionId].lock();
	    	Log.d(TAG, "Start " + sessionId + " : Lock");
	    	try
	    	{	    		
		    	playStatus="true";
		    	stopStatus="false";
		        pauseStatus="false";	
		        restartRequired[sessionId]=true;
		        hm.get(userSettings.getMode(sessionId)).executeStart(sessionId);
		        //hm.get(device_mode).executeStart();
		        // The started state goes back when we actually start
	    	}
	    	finally
	    	{
	    		stopStartLock[sessionId].unlock();
	        	Log.d(TAG, "Start " + sessionId + " : Unlock");
	    	}
    	}
    	else if (getCurrentStreamState(sessionId) == StreamState.STARTED)
    	{
			SendStreamState(StreamState.STARTED, sessionId);
    	}
    }

    public void Stop(int sessionId, boolean fullStop)
    {
    	//csio will send service full stop when it does not want confidence mode started
    	if ((getCurrentStreamState(sessionId) != StreamState.STOPPED) && ((userSettings.getStreamState(sessionId) != StreamState.CONFIDENCEMODE) || (fullStop == true)))
    	{
	    	stopStartLock[sessionId].lock();
	    	Log.d(TAG, "Stop " + sessionId + " : Lock");
	    	try
	    	{
		    	playStatus="false";
		    	stopStatus="true";
		        pauseStatus="false";
		        restartRequired[sessionId]=false;
		        hm2.get(userSettings.getMode(sessionId)).executeStop(sessionId, fullStop);
		        // device state will be set in stop callback
	    	}
	    	finally
	    	{
	    		stopStartLock[sessionId].unlock();
	    		Log.d(TAG, "Stop " + sessionId + " : Unlock");
	    	}
    	}
    	else
    	{
    		if (getCurrentStreamState(sessionId) == StreamState.CONFIDENCEMODE)
    			SendStreamState(StreamState.CONFIDENCEMODE, sessionId);
    		else
    			SendStreamState(StreamState.STOPPED, sessionId);
    	}
		ProductSpecific.doChromakey(false);
    }

    public void Pause(int sessionId)
    {
    	if ((getCurrentStreamState(sessionId) != StreamState.PAUSED) && (userSettings.getStreamState(sessionId) != StreamState.STOPPED))
    	{
	    	Log.d(TAG, "Pause " + sessionId + " : Lock");
	    	stopStartLock[sessionId].lock();
	    	try
	    	{
		        pauseStatus="true";
		    	playStatus="false";
		    	stopStatus="false";
		    	restartRequired[sessionId]=false;
		        hm3.get(userSettings.getMode(sessionId)).executePause(sessionId);
		        // Device state will be set in pause callback
	    	}
	        finally
	    	{
	    		Log.d(TAG, "Pause " + sessionId + " : Unlock");
	    		stopStartLock[sessionId].unlock();
	    	}
    	}
    }
    //StreamOut Ctrl & Config
    public void setMulticastIpAddress(String ip, int sessId){
        if(ip!=null)
        	userSettings.setMulticastAddress(ip, sessId);
    } 
    
    public void sendInitiatorFbAddress(String ip, int sessId){
    	sockTask.SendDataToAllClients("INITIATOR_ADDRESS_FB=" + ip);
    }

    public void sendMulticastIpAddress(String ip, int sessId){
    	sockTask.SendDataToAllClients(String.format("MULTICAST_ADDRESS%d=%s", sessId, ip));
    }
    
    public void setEncodingResolution(int res, int sessId){
    	userSettings.setEncodingResolution(res, sessId);
    } 
    
    public void setTMode(int tmode, int sessId){
    	userSettings.setTransportMode(tmode, sessId);
    } 
    
//    public void setStreamProfile(UserSettings.VideoEncProfile profile, int sessId){
//    	userSettings.setStreamProfile(profile, sessId);
//    } 
    
    public void setVFrmRate(int vfr, int sessId){
    	userSettings.setEncodingFramerate(vfr, sessId);
    } 
    
    public void setRTSPPort(int _port, int sessId){
    	userSettings.setRtspPort(_port, sessId);	
    }
    public void setTSPort(int _port, int sessId){
    	userSettings.setTsPort(_port, sessId);	
    }
    public void setRTPVideoPort(int _port, int sessId){
    	userSettings.setRtpVideoPort(_port, sessId);
    }
    public void setRTPAudioPort(int _port, int sessId){
    	userSettings.setRtpAudioPort(_port, sessId);
    }

    private String createStreamOutURL(int sessId)
    {
        StringBuilder url = new StringBuilder(1024);
        url.append("");
        String proto = "";
        String file = "";
        int port = 0;  
        String l_ipaddr= "";
        int currentSessionInitiation = userSettings.getSessionInitiation(sessId);
        int currentTransportMode = userSettings.getTransportMode(sessId);
        
        //Rtsp Modes
        if ((currentSessionInitiation == 0) || (currentSessionInitiation == 2))
        {
            proto = "rtsp";
            port = userSettings.getRtspPort(sessId);
            l_ipaddr = userSettings.getDeviceIp();
            file = userSettings.getRtspStreamFileName();;

            url.append(proto).append("://").append(l_ipaddr).append(":").append(port).append("/").append(file);
            Log.d(TAG, "URL is "+url.toString());
        }else
            url.append(userSettings.getStreamOutUrl(sessId));

        return url.toString();
    }

    public void startStreamOut(int sessId)
    {
        StringBuilder sb = new StringBuilder(512);

        SendStreamState(StreamState.CONNECTING, sessId);

        // we are starting to streamout so stop confidence preview (unless resuming from pause)
        if (cam_streaming.getConfidencePreviewStatus() == true)
        	cam_streaming.stopConfidencePreview(sessId);
        
        updateWindow(sessId);
        showPreviewWindow(sessId);
        out_url = createStreamOutURL(sessId);
        userSettings.setStreamOutUrl(out_url, sessId);

        try {
            cam_streaming.setSessionIndex(sessId);
            invalidateSurface();
        	cam_streaming.startRecording();
			StreamOutstarted = true;
        } catch(Exception e) {
            e.printStackTrace();
        }
        //Toast.makeText(this, "StreamOut Started", Toast.LENGTH_LONG).show();
               
        sb.append("STREAMURL=").append(out_url);
        sockTask.SendDataToAllClients(sb.toString());        
    }

    public void stopStreamOut(int sessId, boolean fullStop)
    {
    	// If in streamout and in By Reciever(0) or Multicast RTSP (2) clear url on stop- Bug 103801
    	if ( (userSettings.getSessionInitiation(sessId) == 0) || (userSettings.getSessionInitiation(sessId) == 2) )
    		sockTask.SendDataToAllClients(String.format("STREAMURL%d=", sessId));
    	
        cam_streaming.setSessionIndex(sessId);
        if (cam_streaming.getConfidencePreviewStatus() == true)
        	cam_streaming.stopConfidencePreview(sessId);
    	else
        	cam_streaming.stopRecording(false);
        StreamOutstarted = false;
        hidePreviewWindow(sessId);
        
        // Make sure that stop stream out was called by stop not a device mode change
    	// We do not want to restart confidence preview if mode is changing
        // If fullstop is passed down then do not start confidence preview
    	if ((!fullStop) && (userSettings.getMode(sessId) == DeviceMode.STREAM_OUT.ordinal()))
    	{
    		try {
    			Thread.sleep(1000);
    		} catch (Exception e) { }
    		cam_streaming.startConfidencePreview(sessId);
    		restartRequired[sessId] = true;
    	}
    }
    
    public void pauseStreamOut(int sessId)
    {
    	cam_streaming.setSessionIndex(sessId);
    	cam_streaming.pausePlayback();
    }
    
    private void hideWindow (final int sessId)
    {
    	if (dispSurface != null)
    	{
	    	// Make sure surface changes are only done in UI (main) thread
	    	if (Looper.myLooper() != Looper.getMainLooper())
	    	{
		    	final CountDownLatch latch = new CountDownLatch(1);
		    	runOnUiThread(new Runnable() {
				     @Override
				     public void run() {			    	 
			    		 dispSurface.HideWindow(sessId);
				    	 latch.countDown();
				     }
		    	});
		    	try { 
            		if (latch.await(5, TimeUnit.SECONDS) == false)
            		{
            			Log.e(TAG, "hideWindow: timeout after 5 seconds");
            			RecoverTxrxService();
            		}
            	}
            	catch (InterruptedException ex) { ex.printStackTrace(); }  
	    	}
	    	else
	    		dispSurface.HideWindow(sessId);
    	}    		
    }
    
    private void showWindow (final int sessId)
    {
    	if (dispSurface != null)
    	{
	    	// Make sure surface changes are only done in UI (main) thread
	    	if (Looper.myLooper() != Looper.getMainLooper())
	    	{
		    	final CountDownLatch latch = new CountDownLatch(1);
		    	runOnUiThread(new Runnable() {
				     @Override
				     public void run() {
			    		 dispSurface.ShowWindow(sessId);
				    	 latch.countDown();
				     }
		    	});
		    	try { 
            		if (latch.await(5, TimeUnit.SECONDS) == false)
            		{
            			Log.e(TAG, "showWindow: Timeout after 5 seconds");
            			RecoverTxrxService();
            		}
            	}
            	catch (InterruptedException ex) { ex.printStackTrace(); }  
	    	}
	    	else
	    		dispSurface.ShowWindow(sessId);
    	}
    }

    public void hidePreviewWindow(int sessId)
    {
    	Log.d(TAG, "Preview Window hidden");
    	hideWindow(sessId);
    }
    
    public void showPreviewWindow(int sessId)
    {
        Log.d(TAG, "Preview Window showing");
        showWindow(sessId);
    }

    //StreamIn Ctrls & Config
    public void hideStreamInWindow(int sessId)
    {
        Log.d(TAG, " streamin Window hidden " + sessId);
        hideWindow(sessId);
    }

    private void showStreamInWindow(int sessId)
    {
        Log.d(TAG, "streamin Window  showing " + sessId);
        showWindow(sessId);
    }

    public void EnableTcpInterleave(int tcpInterleave,int sessionId){
        userSettings.setTcpInterleave(tcpInterleave,sessionId);
        streamPlay.setRtspTcpInterleave(tcpInterleave, sessionId);
    }

    public int getTcpInterleave(int sessionId)
    {    	
        return userSettings.getTcpInterleave(sessionId);
    }
    
    public void setStreamInUrl(String ap_url, int sessionId)
    {
        userSettings.setStreamInUrl(ap_url, sessionId);

        if(ap_url.startsWith("rtp://@"))
            streamPlay.setRtpOnlyMode( userSettings.getRtpVideoPort(sessionId),  userSettings.getRtpAudioPort(sessionId), userSettings.getDeviceIp(), sessionId);
        else if(ap_url.startsWith("http://"))
            streamPlay.disableLatency(sessionId);
        else
            Log.d(TAG, "No conditional Tags for StreamIn");
        sockTask.SendDataToAllClients(String.format("STREAMURL%d=%s", sessionId, ap_url));
    }
    
    public void setStreamOutUrl(String ap_url, int sessionId)
    {
    	userSettings.setStreamOutUrl(ap_url, sessionId);
    	
    	if (userSettings.getMode(sessionId) == DeviceMode.STREAM_OUT.ordinal())
    		sockTask.SendDataToAllClients(String.format("STREAMURL%d=%s", sessionId, createStreamOutURL(sessionId)));
    }
    
    public void updateStreamOutUrl_OnIPChange()
    {
    	for(int sessionId = 0; sessionId < NumOfSurfaces; sessionId++)
    	{
        	if (userSettings.getMode(sessionId) == DeviceMode.STREAM_OUT.ordinal())
        		sockTask.SendDataToAllClients(String.format("STREAMURL%d=%s", sessionId, createStreamOutURL(sessionId)));
    	}
    }

    public void setHdcpEncrypt(boolean flag, int sessId)
    {    	
    	if(cam_streaming != null)
    		cam_streaming.setHdcpEncrypt(flag);
    	
    	if(streamPlay != null)
    		streamPlay.setHdcpEncrypt(flag, sessId);
    	
    	mHDCPEncryptStatus/*[sessId]*/ = flag;
    	mForceHdcpStatusUpdate = true;
    	
    	if(flag)
    	{
    		MiscUtils.writeStringToDisk(hdcpEncryptFilePath, String.valueOf(1));
    	}
    	else
    	{
    		MiscUtils.writeStringToDisk(hdcpEncryptFilePath, String.valueOf(0));
    	}
    }

    
    public void setTxHdcpActive(boolean flag, int sessId)
    {
    	if (flag != mTxHdcpActive)
    	{
    		mTxHdcpActive/*[sessId]*/ = flag;
    		mForceHdcpStatusUpdate = true;
    	}
    }
    
    
    public boolean getHdcpEncrypt(int sessId)
    {
    	return mHDCPEncryptStatus;
    }
    
    public String getStreamUrl(int sessId)
    {
        //return out_url;
    	if (userSettings.getMode(sessId) == DeviceMode.STREAM_OUT.ordinal())
    		return userSettings.getStreamOutUrl(sessId);
    	else if (userSettings.getMode(sessId) == DeviceMode.STREAM_IN.ordinal())
    		return userSettings.getStreamInUrl(sessId);
    	else
    		return "";
    }

    public void startStreamIn(int sessId)
    {
    	updateWindow(sessId);
        showStreamInWindow(sessId);
        invalidateSurface();
        streamPlay.onStart(sessId);
        //Toast.makeText(this, "StreamIN Started", Toast.LENGTH_LONG).show();
    }

    public void stopStreamIn(int sessId)
    {
    	//hide video window first
    	if (hideVideoOnStop)
    		hideWindowWithoutDestroy(sessId);

    	streamPlay.onStop(sessId);   

    	hideStreamInWindow(sessId);
    }

    public void pauseStreamIn(int sessId)
    {
        streamPlay.onPause(sessId);
    }

    //Preview 
    public void startPreview(int sessId)
    {
    	if (cam_preview != null)
    	{
    		SendStreamState(StreamState.CONNECTING, sessId);
    		updateWindow(sessId);
    		showPreviewWindow(sessId);
    		cam_preview.setSessionIndex(sessId);
    		invalidateSurface();
    		cam_preview.startPlayback(false);
    		//Toast.makeText(this, "Preview Started", Toast.LENGTH_LONG).show();
    	}
    }

    public void stopPreview(int sessId)
    {
    	if (cam_preview != null)
    	{
    		hidePreviewWindow(sessId);
    		cam_preview.setSessionIndex(sessId);
    		//On STOP, there is a chance to get ducati crash which does not save current state
    		//causes streaming never stops.
    		//FIXME:Temp Hack for ducati crash to save current state
    		userSettings.setStreamState(StreamState.STOPPED, sessId);
    		cam_preview.stopPlayback(false);
    		//Toast.makeText(this, "Preview Stopped", Toast.LENGTH_LONG).show();
    	}
    }
    
    public void pausePreview(int sessId)
    {
    	if (cam_preview != null)
    	{
    		cam_preview.setSessionIndex(sessId);
    		cam_preview.pausePlayback();
    	}
    }
   
   
    //Control Feedback
    public String getStartStatus(){
	return playStatus;
    }

    public String getStopStatus(){
	return stopStatus;
    }
    
    public String getPauseStatus(){
	return pauseStatus;
    }
   
    public String getDeviceReadyStatus(){
    	return "TRUE";
    }
    
    public String getProcessingStatus(){
	return "1";//"TODO";
    }

    public String getElapsedSeconds(){
	return "0";//"TODO";
    }

    public String getStreamStatus(){
	return "0";//"TODO";
    }
    
    public void setStatistics(boolean enabled, int sessId){
    	userSettings.setStatisticsEnable(enabled, sessId);
		userSettings.setStatisticsDisable(!enabled, sessId);
    }
    
    public void setMulticastTTl(int value){
    	userSettings.setMulticastTTL(value);
    	MiscUtils.writeStringToDisk(multicastTTLFilePath, String.valueOf(value));
    }
    
    public void setSessionInitiation(int sessionInitiation, int sessionId)
    {
    	if (sessionInitiation != userSettings.getSessionInitiation(sessionId))
    	{
	    	userSettings.setSessionInitiation(sessionInitiation, sessionId);
	    	
	    	if (userSettings.getMode(sessionId) == DeviceMode.STREAM_OUT.ordinal())
			{
	    		// if by transmitter, send currently saved url, else clear
	    		if (userSettings.getSessionInitiation(sessionId) == 1)
	    		{
	    			// Bug 108125: Clear out stream out url when changing initiation modes
	    			userSettings.setStreamOutUrl("", sessionId);
	    			sockTask.SendDataToAllClients(String.format("STREAMURL%d=%s", sessionId, userSettings.getStreamOutUrl(sessionId)));
	    		}
	    		else
	    			sockTask.SendDataToAllClients(String.format("STREAMURL%d=", sessionId));
			}
	    	else if (userSettings.getMode(sessionId) == DeviceMode.STREAM_IN.ordinal())
	    	{
	    		// if not by transmitter send currently saved url, else clear
	    		if (userSettings.getSessionInitiation(sessionId) != 1)
	    			sockTask.SendDataToAllClients(String.format("STREAMURL%d=%s", sessionId, userSettings.getStreamInUrl(sessionId)));
	    		else
	    			sockTask.SendDataToAllClients(String.format("STREAMURL%d=", sessionId));
	    	}
    	}
    }
    
    public void resetStatistics(int sessId){
    	if (userSettings.getMode(sessId) == DeviceMode.STREAM_IN.ordinal())
    		streamPlay.resetStatistics(sessId);
    	else if (userSettings.getMode(sessId) == DeviceMode.STREAM_OUT.ordinal())
    		cam_streaming.resetStatistics(sessId);
    }
    

//    public String getStreamStatistics(){
//        if (userSettings.getMode(idx) == DeviceMode.STREAM_IN.ordinal()) 
//            return streamPlay.updateSvcWithPlayerStatistics(); 
//        else if (userSettings.getMode(idx) == DeviceMode.STREAM_OUT.ordinal()) 
//            return cam_streaming.updateSvcWithStreamStatistics();
//        else
//            return "";
//    }
    
    public void RecoverDucati(){
    	if (sockTask != null)
    		sockTask.SendDataToAllClients("RECOVER_DUCATI=TRUE");
    }
    
    public void RecoverTxrxService(){
    	Log.e(TAG, "Fatal error, kill CresStreamSvc!");
    	sockTask.SendDataToAllClients("DEVICE_READY_FB=FALSE");
//    	sockTask.SendDataToAllClients("KillMePlease=true");
    	System.exit(1); //We pick 1 since 0 probably means success
    }
    
    public void RecoverMediaServer() {
        Log.e(TAG, "Fatal error, kill mediaserver!");
    	sockTask.SendDataToAllClients("KillMediaServer=true");
    }
    
    public void stopOnIpAddrChange(){
		Log.e(TAG, "Restarting on device IP Address Change...!");
        restartStreams(false);
    }

    public void launchAirMedia(boolean val, int sessId, boolean fullscreen) {
    	stopStartLock[sessId].lock();
    	Log.d(TAG, "AirMedia " + sessId + " : Lock");
    	try
    	{
    		userSettings.setAirMediaLaunch(val);
    		if (val == true) // True = launch airmedia app, false = close app
    		{
    			// Do I need to stop all video here???
    			if (mAirMedia == null && AirMedia.checkAirMediaLicense())
    				mAirMedia = new AirMedia(this);
    			int x, y, width, height;
    			if (fullscreen || ((userSettings.getAirMediaWidth() == 0) && (userSettings.getAirMediaHeight() == 0)))
    			{
    				WindowManager wm = (WindowManager) this.getSystemService(Context.WINDOW_SERVICE);
    				Log.e(TAG, "AirMedia fullscreen true");

    				if (haveExternalDisplays)
    				{
    					DisplayManager dm = (DisplayManager) getApplicationContext().getSystemService(Context.DISPLAY_SERVICE);
    					if (dm != null){
    						Display dispArray[] = dm.getDisplays();
    						if (dispArray.length>1){
    							Context displayContext = getApplicationContext().createDisplayContext(dispArray[1]);
    							wm = (WindowManager)displayContext.getSystemService(Context.WINDOW_SERVICE);
    						}
    					}
    					else
    					{
    						Log.e(TAG, "Unable to query second display size, using primary display");
    					}
    				}	

    				Display display = wm.getDefaultDisplay();
    				Point size = new Point();
    				display.getSize(size);

    				width = size.x;
    				height = size.y;    			
    				x = y = 0;
    			}
    			else
    			{
    				x = userSettings.getAirMediaX();
    				y = userSettings.getAirMediaY();
    				width = userSettings.getAirMediaWidth();
    				height = userSettings.getAirMediaHeight();  	
    			}

    			if (haveExternalDisplays)
    			{
    				// TODO: Change to priority phone when alpha blending is working
    				userSettings.setAirMediaWindowFlag(WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY);
    				userSettings.setAirMediaDisplayScreen(1);
    			}
    			else
    			{
    				userSettings.setAirMediaWindowFlag(WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY);
    				userSettings.setAirMediaDisplayScreen(0);
    			}

    			if (haveExternalDisplays && Boolean.parseBoolean(hdmiOutput.getSyncStatus()))
    				mAirMedia.show(x, y, width, height);
    		}
    		else
    		{
    			mAirMedia.hide();
    		}
    	}
    	finally
    	{
    		stopStartLock[sessId].unlock();
        	Log.d(TAG, "AirMedia " + sessId + " : Unlock");
    	}
    }
    
    public void setAirMediaLoginCode(int loginCode, int sessId) {
    	if ((loginCode < 1) || (loginCode > 9999))
    		return; //Don't set out of range value
    		
    	userSettings.setAirMediaLoginCode(loginCode);
    	userSettings.setAirMediaLoginMode(AirMediaLoginMode.Fixed.ordinal()); // When loginCode is set auto switch to fixed mode
    	
    	if (mAirMedia != null)
    	{
	    	mAirMedia.setLoginCode(userSettings.getAirMediaLoginCode());
	    	
	    	if (userSettings.getAirMediaDisplayLoginCode())
			{
	    		mAirMedia.showLoginCodePrompt(loginCode);
			}
    	}

    	// send feedback of login mode since it might have changed
    	sockTask.SendDataToAllClients(String.format("AIRMEDIA_LOGIN_MODE=%d", AirMediaLoginMode.Fixed.ordinal()));
    }
    
    public void setAirMediaLoginMode(int loginMode, int sessId) {
    	// FIXME: protect against same join val coming in????
    	userSettings.setAirMediaLoginMode(loginMode);
    	
		if (loginMode == AirMediaLoginMode.Disabled.ordinal())
    	{
			userSettings.setAirMediaLoginCode(0);
			if (mAirMedia != null)
	    	{
	    		mAirMedia.setLoginCodeDisable();
	    		mAirMedia.hideLoginCodePrompt();
	    	}
    	}
		else if (loginMode == AirMediaLoginMode.Random.ordinal())
    	{
    		int rand = (int)(Math.random() * 9999 + 1); 
    		userSettings.setAirMediaLoginCode(rand);
    		if (mAirMedia != null)
	    	{
	    		mAirMedia.setLoginCode(rand);
	    		if (userSettings.getAirMediaDisplayLoginCode())
	    		{
	    			mAirMedia.showLoginCodePrompt(rand);
	    		}
	    	}
    	}
		else if(loginMode == AirMediaLoginMode.Fixed.ordinal())
    	{
			if (mAirMedia != null)
	    	{
	    		mAirMedia.setLoginCode(userSettings.getAirMediaLoginCode());
	    		if (userSettings.getAirMediaDisplayLoginCode())
	    		{
	    			mAirMedia.showLoginCodePrompt(userSettings.getAirMediaLoginCode());
	    		}
	    	}
    	}
    		
		sockTask.SendDataToAllClients(String.format("AIRMEDIA_LOGIN_CODE=%d", userSettings.getAirMediaLoginCode()));
    }
    
    public void setAirMediaDisplayLoginCode(boolean display, int sessid)
    {
    	userSettings.setAirMediaDisplayLoginCode(display);
    	if (mAirMedia != null)
    	{
	    	if ((display) && (userSettings.getAirMediaLoginMode() != AirMediaLoginMode.Disabled.ordinal()))
	    	{
	    		mAirMedia.showLoginCodePrompt(userSettings.getAirMediaLoginCode());
	    	}
	    	else
	    	{
	    		mAirMedia.hideLoginCodePrompt();
	    	}
    	}
    }
    
    public void setAirMediaModerator(boolean enable, int sessId)
    {
    	userSettings.setAirMediaModerator(enable);
    	if (mAirMedia != null)
    	{
    		mAirMedia.setModeratorEnable(enable);
    	}
    }
    
    public void setAirMediaResetConnections(boolean enable, int sessId)
    {
    	if ((enable) && (mAirMedia != null))
    	{
    		mAirMedia.resetConnections();
    	}
    }
    
    public void setAirMediaDisconnectUser(int userId, boolean enable, int sessId)
    {
    	userSettings.setAirMediaDisconnectUser(enable, userId);
    	if ((enable) && (mAirMedia != null))
    	{
    		mAirMedia.disconnectUser(userId);
    	}
    }
    
    public void setAirMediaStartUser(int userId, boolean enable, int sessId)
    {
    	
    }
    
    public void setAirMediaUserPosition(int userId, int position, int sessId)
    {
    	if (mAirMedia != null)
    	{
    		if (position == 0)
    			mAirMedia.stopUser(userId);
    		else
    			mAirMedia.setUserPosition(userId, position);
    	}
    }
    
    public void setAirMediaStopUser(int userId, boolean enable, int sessId)
    {
    	
    }
    
    public void setAirMediaOsdImage(String filePath, int sessId)
    {
    	// Image gets sent on apply
    	userSettings.setAirMediaOsdImage(filePath);
    }
    
    public void setAirMediaIpAddressPrompt(boolean enable, int sessId)
    {
    	userSettings.setAirMediaIpAddressPrompt(enable);
    	if (mAirMedia != null)
    	{
    		mAirMedia.setIpAddressPrompt(enable);
    	}
    }
    
    public void setAirMediaDomainNamePrompt(boolean enable, int sessId)
    {
    	userSettings.setAirMediaDomainNamePrompt(enable);
    	if (mAirMedia != null)
    	{
    		mAirMedia.setDomainNamePrompt(enable);
    	}
    }
    
    public void setAirMediaWindowPosition(int x, int y, int width, int height)
    {    	
    	userSettings.setAirMediaX(x);
    	userSettings.setAirMediaY(y);
    	userSettings.setAirMediaWidth(width);
    	userSettings.setAirMediaHeight(height);
    	// Just cache the position do not actually send to AirMedia
    }
    
    public void setAirMediaWindowXOffset(int x, int sessId)
    {    	
    	userSettings.setAirMediaX(x);
    	int y = userSettings.getAirMediaY();
    	int width = userSettings.getAirMediaWidth();
		int height = userSettings.getAirMediaHeight();
		if ((mAirMedia != null) && (mAirMedia.getSurfaceDisplayed() == true))
    	{
    		mAirMedia.setSurfaceSize(x, y, width, height, false);
    	}
    }
    
    public void setAirMediaWindowYOffset(int y, int sessId)
    {    	
    	userSettings.setAirMediaY(y);
    	int x = userSettings.getAirMediaX();    	
    	int width = userSettings.getAirMediaWidth();
		int height = userSettings.getAirMediaHeight();
		if ((mAirMedia != null) && (mAirMedia.getSurfaceDisplayed() == true))
    	{
    		mAirMedia.setSurfaceSize(x, y, width, height, false);
    	}
    }
    
    public void setAirMediaWindowWidth(int width, int sessId)
    {    	
    	userSettings.setAirMediaWidth(width);
    	int x = userSettings.getAirMediaX();    	
    	int y = userSettings.getAirMediaY();
		int height = userSettings.getAirMediaHeight();
		if ((mAirMedia != null) && (mAirMedia.getSurfaceDisplayed() == true))
    	{
    		mAirMedia.setSurfaceSize(x, y, width, height, false);
    	}
    }
    
    public void setAirMediaWindowHeight(int height, int sessId)
    {    	
    	userSettings.setAirMediaHeight(height);
    	int x = userSettings.getAirMediaX();    	
    	int y = userSettings.getAirMediaY();
    	int width = userSettings.getAirMediaWidth();
    	if ((mAirMedia != null) && (mAirMedia.getSurfaceDisplayed() == true))
    	{
    		mAirMedia.setSurfaceSize(x, y, width, height, false);
    	}
    }
    
    public void airMediaApplyLayoutPassword(boolean apply, int sessId)
    {
    	if (apply)
    	{
    		// apply layout password here
    		// TODO: we need layout password from airmedia app    		
    	}
    }
    
    public void setAirMediaLayoutPassword(String layoutPassword, int sessId)
    {
    	// User needs to send apply join to take effect
    	userSettings.setAirMediaLayoutPassword(layoutPassword);
    }
    
    public void airMediaApplyOsdImage(boolean apply, int sessId)
    {
    	if (apply)
    	{
    		if (mAirMedia != null)
        	{
        		mAirMedia.setOsdImage(userSettings.getAirMediaOsdImage());
        	}
    	}
    }
    
    public void airMediaSetDisplayScreen(int displayId, int sessId)
    {
    	if (mAirMedia != null)
    	{
    		userSettings.setAirMediaDisplayScreen(displayId);
    		mAirMedia.setDisplayScreen(displayId);
    	}
    }
    
    public void airMediaSetWindowFlag(int windowFlag, int sessId)
    {
    	if (mAirMedia != null)
    	{
    		userSettings.setAirMediaWindowFlag(windowFlag);
    		mAirMedia.setWindowFlag(windowFlag);
    	}
    }
    
    public void setAirMediaAdaptorSelect(int select, int sessId)
    {
    	if (select != userSettings.getAirMediaAdaptorSelect())
    	{
    		userSettings.setAirMediaAdaptorSelect(select);
    		if (mAirMedia != null)
    		{
    			mAirMedia.setIpAddressPrompt(userSettings.getAirMediaIpAddressPrompt());
    		}
    		
    		// Update connection address as well
    		sendAirMediaConnectionAddress(sessId);
    	}    	
    }
    
    // Will update airMedia IP information when called
    public void updateAirMediaIpInformation(int sessId)
    {
    	if (mAirMedia != null)
		{
    		mAirMedia.setIpAddressPrompt(userSettings.getAirMediaIpAddressPrompt());
		}
    	
    	sendAirMediaConnectionAddress(sessId);
    }
    
    public void sendAirMediaConnectionAddress(int sessId)
    {
    	sockTask.SendDataToAllClients(String.format("AIRMEDIA_CONNECTION_ADDRESS=%s", getAirMediaConnectionAddress(sessId)));
    }
    
    public String getAirMediaConnectionAddress(int sessId)
    {
    	StringBuilder url = new StringBuilder(512);
        url.append("http://");
        if (userSettings.getAirMediaAdaptorSelect() == 0)
        {
        	url.append(userSettings.getDeviceIp());
        }
        else if (userSettings.getAirMediaAdaptorSelect() == 1)
        {
        	url.append(userSettings.getAuxiliaryIp());
        }
        else
        {
        	Log.w(TAG, "Invalid adaptor select value of " + userSettings.getAirMediaAdaptorSelect());
        	url.append(userSettings.getDeviceIp());
        }
        return url.toString();
    }

    
    public String getAirMediaVersion(int sessId)
    {
    	String versionName = "";
		final PackageManager pm = getPackageManager();
		String apkName = "ReceiverAirMedia.apk";
		String fullPath = "/data/app" + "/" + apkName;        
		PackageInfo info = pm.getPackageArchiveInfo(fullPath, 0);
		if (info != null)
			versionName = info.versionName;
		if (AirMedia.checkAirMediaLicense())
			MiscUtils.writeStringToDisk("/dev/shm/crestron/CresStreamSvc/airmediaVersion", versionName);
		
		return versionName;
    }
    
    public void setCamStreamEnable(boolean enable) {
    	userSettings.setCamStreamEnable(enable);
    	
    	if (gstStreamOut != null)
    	{
    		if (enable)
    			gstStreamOut.start();
    		else
    			gstStreamOut.stop();
    	}
    }
    
    public void setCamStreamMulticastEnable(boolean enable) {
    	userSettings.setCamStreamMulticastEnable(enable);
    	
    	if (gstStreamOut != null)
    	{
    		gstStreamOut.setMulticastEnable(enable);
    	}
    }
    
    public void setCamStreamResolution(int resolution) {
    	userSettings.setCamStreamResolution(resolution);
    	
    	if (gstStreamOut != null)
    	{
    		gstStreamOut.setResolution(resolution);
    	}
    }
    
    public void setCamStreamName(String name) {
    	userSettings.setCamStreamName(name);
    	
    	if (gstStreamOut != null)
    	{
    		gstStreamOut.setCamStreamName(name);
    	}
    }
    
    public void setCamStreamSnapshotName(String name) {
    	userSettings.setCamStreamSnapshotName(name);
    	
    	if (gstStreamOut != null)
    	{
    		gstStreamOut.setCamStreamSnapshotName(name);
    	}
    }
    
    public void setCamStreamMulticastAddress(String address) {
    	userSettings.setCamStreamMulticastAddress(address);
    	
    	if (gstStreamOut != null)
    	{
    		gstStreamOut.setCamStreamMulticastAddress(address);
    	}
    }
        
    public String getAirMediaDisconnectUser(int sessId)
    {
    	// Do nothing handled by getAirMediaUserPosition
    	return "";
    }
    
    public String getAirMediaStartUser(int sessId)
    {
    	// Do nothing handled by getAirMediaUserPosition
    	return "";
    }
    
    public String getAirMediaUserPosition(int sessId)
    {
    	// Just send all airMedia user feedbacks
    	airMediaUserFeedbackUpdateRequest(sessId);
    	return "";
    }
    
    public String getAirMediaStopUser(int sessId)
    {
    	// Do nothing handled by getAirMediaUserPosition
    	return "";
    }
    
    public void airMediaUserFeedbackUpdateRequest(int sessId)
    {
    	if (mAirMedia != null)
    	{
    		mAirMedia.querySenderList(true);
    	}
    }
    
    public void sendAirMediaUserFeedbacks(int userId, String userName, String ipAddress, int position, boolean status)
    {
    	userSettings.setAirMediaUserPosition(position, userId);
    	sockTask.SendDataToAllClients(String.format("AIRMEDIA_USER_NAME=%d:%s", userId, userName));
    	sockTask.SendDataToAllClients(String.format("AIRMEDIA_USER_IP=%d:%s", userId, ipAddress));
    	sockTask.SendDataToAllClients(String.format("AIRMEDIA_USER_POSITION=%d:%d", userId, position));
    	sockTask.SendDataToAllClients(String.format("AIRMEDIA_USER_CONNECTED=%d:%s", userId, String.valueOf(status)));
    	
    }
    
    public void sendAirMediaStatus(int status)
    {
    	// TODO: send on update request
    	sockTask.SendDataToAllClients(String.format("AIRMEDIA_STATUS=%d", status));
    }
    
    public void sendAirMediaNumberUserConnected()
    {
    	// TODO: send on update request
    	int numberUserConnected = 0;
    	for (int i = 1; i <= 32; i++) // We handle airMedia user ID as 1 based
    	{
    		if (userSettings.getAirMediaUserConnected(i))
    			numberUserConnected++;
    	}
    	sockTask.SendDataToAllClients(String.format("AIRMEDIA_NUMBER_USER_CONNECTED=%d", numberUserConnected));
    }

    //Registering for HPD and Resolution Event detection	
    void registerBroadcasts(){
        Log.d(TAG, "registerBroadcasts !");
        
        resolutionEvent = new BroadcastReceiver()
        {
            public void onReceive(Context paramAnonymousContext, final Intent paramAnonymousIntent)
            {
            	// We need to make sure that the broadcastReceiver thread is not stuck processing start/stop requests
            	// Therefore we will run all commands through a worker thread
            	new Thread(new Runnable() {
            		public void run() {
            			hdmiLock.lock();
		            	try
		            	{
			                if (paramAnonymousIntent.getAction().equals("evs.intent.action.hdmi.RESOLUTION_CHANGED"))
			                {
			                	if (hdmiLock.getQueueLength() == 0)
			                	{
				                	int resolutionId = paramAnonymousIntent.getIntExtra("evs_hdmi_resolution_id", -1);
				                	
				                	int prevResolutionIndex = hdmiInput.getResolutionIndex();
				                    if (resolutionId != 0)
				                    	hdmiInput.setResolutionIndex(resolutionId);
				                    
				                    // Fix issue where we start video before we setup resolution 
				                    refreshInputResolution();
				                	
				                	setCameraAndRestartStreams(resolutionId); //we need to restart streams for resolution change		                	

				                    //Wait 5 seconds before sending hdmi in sync state - bug 96552
				                    new Thread(new Runnable() {
				                		public void run() { 
				                			try {
				                				Thread.sleep(5000);
				                			} catch (Exception e) { e.printStackTrace(); }				                			
				                			sendHdmiInSyncState();
				                		}
				                    }).start();
				                	hpdHdmiEvent = 1;
			                        Log.i(TAG, "HDMI resolutions - HRes:" + hdmiInput.getHorizontalRes() + " Vres:" + hdmiInput.getVerticalRes());
			                	}
		                    }
		                    else
		                        Log.i(TAG, " Nothing to do!!!");
		            	}
		            	finally
		            	{
		            		hdmiLock.unlock();
		            	}
            		}
            	}).start();
        	}            
        };
        IntentFilter resolutionIntentFilter = new IntentFilter("evs.intent.action.hdmi.RESOLUTION_CHANGED");
        registerReceiver(resolutionEvent, resolutionIntentFilter);
        hpdEvent = new BroadcastReceiver()
        {
            public void onReceive(Context paramAnonymousContext, final Intent paramAnonymousIntent)
            {
            	// We need to make sure that the broadcastReceiver thread is not stuck processing start/stop requests
            	// Therefore we will run all commands through a worker thread
            	new Thread(new Runnable() {
            		public void run() {
            			hdmiLock.lock();
                    	try
                    	{
        	                if (paramAnonymousIntent.getAction().equals("evs.intent.action.hdmi.HPD"))
        	                {
        	                	if ((hdmiLock.getQueueLength() == 0) || (hdmiLock.getQueueLength() == 1))
        	                	{
        	                		int i = paramAnonymousIntent.getIntExtra("evs_hdmi_hdp_id", -1);
        	                		if (i == 0)
        	                			setNoVideoImage(true);
	        	                	sendHdmiInSyncState();
	        	                    
	        	                    Log.i(TAG, "Received hpd broadcast ! " + i);
	        	                    hpdHdmiEvent = 1;
	        	                    mForceHdcpStatusUpdate = true;
        	                	}
        	                }
                    	}
                    	finally
                    	{
                    		hdmiLock.unlock();
                    	}
            		}
            	}).start();            	
            }
        };
        IntentFilter hpdIntentFilter = new IntentFilter("evs.intent.action.hdmi.HPD");
        registerReceiver(hpdEvent, hpdIntentFilter);	
        
        final CresStreamCtrl streamCtrl = this;

        hdmioutResolutionChangedEvent = new BroadcastReceiver()
        {
            public void onReceive(Context paramAnonymousContext, final Intent paramAnonymousIntent)
            {
            	// We need to make sure that the broadcastReceiver thread is not stuck processing start/stop requests
            	// Therefore we will run all commands through a worker thread
            	new Thread(new Runnable() {
            		public void run() {
		                if (paramAnonymousIntent.getAction().equals("evs.intent.action.hdmi.HDMIOUT_RESOLUTION_CHANGED"))
		                {
		                	if (!haveExternalDisplays)
		                	{
		                		Log.d(TAG, "receiving intent!!!!");

		                		int hdmiOutResolutionEnum = paramAnonymousIntent.getIntExtra("evs_hdmiout_resolution_changed_id", -1);
		                		Log.i(TAG, "Received hdmiout resolution changed broadcast ! " + hdmiOutResolutionEnum);

		                		handleHdmiOutputChange();
		                	}
		                }
		            }
            	}).start();
            }
        };
        IntentFilter hdmioutResolutionIntentFilter = new IntentFilter("evs.intent.action.hdmi.HDMIOUT_RESOLUTION_CHANGED");
        registerReceiver(hdmioutResolutionChangedEvent, hdmioutResolutionIntentFilter);
    }
    
    public void handleHdmiOutputChange()
	{
		refreshOutputResolution();

		// Recheck if HDCP changed
		mForceHdcpStatusUpdate = true;

		//update with current HDMI output resolution information
		sendHdmiOutSyncState();

		if (haveExternalDisplays && Boolean.parseBoolean(hdmiOutput.getSyncStatus()))
		{
			createCresDisplaySurface();

			try { Thread.sleep(3000); } catch (Exception e) {}
			restartStreams(false);

			// Show AirMedia window if we acquire HDMI output sync
			if ((mAirMedia != null) && userSettings.getAirMediaLaunch())
			{
				mAirMedia = null;
				launchAirMedia(true, 0, false);
//				mAirMedia.showSurface(true);	// Workaround for Bug 117036
			}
		}
		else if (haveExternalDisplays)
		{
			// Hide AirMedia window if we lose HDMI output sync
			if ((mAirMedia != null) && userSettings.getAirMediaLaunch())
				mAirMedia.showSurface(false);
		}
	}
    
	private void sendHdmiInSyncState() 
	{
		refreshInputResolution();
		sockTask.SendDataToAllClients("hdmiin_sync_detected=" + hdmiInput.getSyncStatus());
		sockTask.SendDataToAllClients("HDMIIN_INTERLACED=" + hdmiInput.getInterlacing());
		sockTask.SendDataToAllClients("HDMIIN_HORIZONTAL_RES_FB=" + hdmiInput.getHorizontalRes());
		sockTask.SendDataToAllClients("HDMIIN_VERTICAL_RES_FB=" + hdmiInput.getVerticalRes());
		sockTask.SendDataToAllClients("HDMIIN_FPS_FB=" + hdmiInput.getFPS());
		sockTask.SendDataToAllClients("HDMIIN_ASPECT_RATIO=" + hdmiInput.getAspectRatio());
		sockTask.SendDataToAllClients("HDMIIN_AUDIO_FORMAT=" + hdmiInput.getAudioFormat());
		sockTask.SendDataToAllClients("HDMIIN_AUDIO_CHANNELS=" + hdmiInput.getAudioChannels());
		sockTask.SendDataToAllClients("HDMIIN_AUDIO_SAMPLE_RATE=" + hdmiInput.getAudioSampleRate());
	}
	
	private void sendHdmiOutSyncState() 
	{
		sockTask.SendDataToAllClients("HDMIOUT_SYNC_DETECTED=" + hdmiOutput.getSyncStatus());
		sockTask.SendDataToAllClients("HDMIOUT_HORIZONTAL_RES_FB=" + hdmiOutput.getHorizontalRes());
		sockTask.SendDataToAllClients("HDMIOUT_VERTICAL_RES_FB=" + hdmiOutput.getVerticalRes());
		sockTask.SendDataToAllClients("HDMIOUT_FPS_FB=" + hdmiOutput.getFPS());
		sockTask.SendDataToAllClients("HDMIOUT_ASPECT_RATIO=" + hdmiOutput.getAspectRatio());
		sockTask.SendDataToAllClients("HDMIOUT_AUDIO_FORMAT=" + hdmiOutput.getAudioFormat());
		sockTask.SendDataToAllClients("HDMIOUT_AUDIO_CHANNELS=" + hdmiOutput.getAudioChannels());
	}
	
	private void setHDCPBypass()
	{
		new Thread(new Runnable() {
    		public void run() {
    			while (HDMIOutputInterface.readHDCPOutputStatus() == -1)
    			{
    				try {
    					Thread.sleep(100);
    				} catch (Exception e) { e.printStackTrace(); }
    			}
    			
    			//Set bypass high when hdcp is not authenticated on output, if not in force hdcp mode
    			boolean setHDCPBypass = ((userSettings.isHdmiOutForceHdcp() == false) && (HDMIOutputInterface.readHDCPOutputStatus() == 0));
				HDMIOutputInterface.setHDCPBypass(setHDCPBypass);
            }
		}).start();
	}
	
	public void setCameraAndRestartStreams(int hdmiInputResolutionEnum)
	{
		//Set Camera and restart streams
        setCameraHelper(hdmiInputResolutionEnum, false);
	}
	
	public void setCamera(int hdmiInputResolutionEnum)
	{
		//Set Camera but do not restart streams
		setCameraHelper(hdmiInputResolutionEnum, true);
	}
	
	private void setCameraHelper(int hdmiInputResolutionEnum, boolean ignoreRestart)
	{
		Log.i(TAG, String.format("Setting cameraMode with resolution enum = %d", hdmiInputResolutionEnum));
		int hdmiInSampleRate = HDMIInputInterface.readAudioSampleRate();
		// If resolution did not change don't restart streams, ignore 0 enum
		if ( (hdmiInputResolutionEnum == mPreviousValidHdmiInputResolution) && (hdmiInSampleRate == mPreviousAudioInputSampleRate) )
			ignoreRestart = true;
		else if (hdmiInputResolutionEnum != 0) {
			mPreviousValidHdmiInputResolution = hdmiInputResolutionEnum;
			mPreviousAudioInputSampleRate = hdmiInSampleRate;
		}
		
		//Set ignore restart to true if you want to set camera mode but do not want to restart any streams
		boolean validResolution = (hdmiInputResolutionEnum != 0);
    	if (validResolution == true)
    	{
    		if (ignoreRestart == false)
    		{
    			if (sockTask.firstRun == false) // makes sure that csio is up so as restart streams before all information is received from platform
    			{
		    		mIgnoreAllCrash = true;
		    		// Lets wait to make sure CSI buffer is ready
		    		try {
            			Thread.sleep(500);
            		} catch (Exception e ) { e.printStackTrace(); }
		    		restartStreams(true); //true because we do not need to restart stream in streams
		    		mIgnoreAllCrash = false;
    			}
    		}
    		
    		setNoVideoImage(false);
   			mForceHdcpStatusUpdate = true;
		 }			                
        else
        {
        	setNoVideoImage(true);
        }
	}
	
	public void setNoVideoImage(boolean enable) {
        Log.d(TAG, String.format("Setting no video format to %s", String.valueOf(enable)));
		String cameraMode = "";
		int previousCameraMode = readCameraMode();
		if ( (enable) && (previousCameraMode != CameraMode.NoVideo.ordinal() 
				|| previousCameraMode != CameraMode.BlackScreen.ordinal()) )
		{
			synchronized (mCameraModeScheduleLock)
			{
				if (mNoVideoTimer == null)
				{
					mNoVideoTimer = new Timer();
					mNoVideoTimer.schedule(new setNoVideoImage(CameraMode.NoVideo.ordinal()), 5000);
				}
				setCameraMode(String.valueOf(CameraMode.BlackScreen.ordinal()));
			}
		}
		else if (!enable)
		{
			synchronized (mCameraModeScheduleLock)
			{
				if (mNoVideoTimer != null)
				{
					mNoVideoTimer.cancel();
					mNoVideoTimer = null;
				}
				
				previousCameraMode = readCameraMode(); //check camera mode again if it changed
				if ((previousCameraMode == CameraMode.NoVideo.ordinal()) || (previousCameraMode == CameraMode.BlackScreen.ordinal()))
				{
					if (Boolean.parseBoolean(pauseStatus) == true)
						setCameraMode(String.valueOf(CameraMode.Paused.ordinal()));
					else
						setCameraMode(String.valueOf(CameraMode.Camera.ordinal()));
				}
			}
		}
		
		// Set hdmi connected states for csio
		sockTask.SendDataToAllClients(String.format("HDMIInputConnectedState=%b", !enable)); //true means hdmi input connected
	}
	
	public void setPauseVideoImage(boolean enable) 
	{
		String cameraMode = "";
		int previousCameraMode = readCameraMode();
		if (enable)
			setCameraMode(String.valueOf(CameraMode.Paused.ordinal()));
		else if (previousCameraMode == CameraMode.Paused.ordinal())
			setCameraMode(String.valueOf(CameraMode.Camera.ordinal()));
	}
	
	public void setHDCPErrorImage(boolean enable) 
	{
		String cameraMode = "";
		int previousCameraMode = readCameraMode();
		if (enable)
		{
			//Check HDCP output status
			if ((mHDCPOutputStatus == true) || (mHDCPExternalStatus == true))
				setCameraMode(String.valueOf(CameraMode.HDCPStreamError.ordinal()));
			else
				setCameraMode(String.valueOf(CameraMode.HDCPAllError.ordinal()));
		}
		else if ((previousCameraMode == CameraMode.HDCPStreamError.ordinal()) ||
				(previousCameraMode == CameraMode.HDCPAllError.ordinal()))
		{
			if (Boolean.parseBoolean(pauseStatus) == true)
				setCameraMode(String.valueOf(CameraMode.Paused.ordinal()));
			else
				setCameraMode(String.valueOf(CameraMode.Camera.ordinal()));
		}
	}
	
	public void sendHDCPLocalOutputBlanking(boolean enable) 
	{
		String msg = (enable ? "true" : "false");
		sockTask.SendDataToAllClients(String.format("HDCP_BLANK_HDMI_OUTPUT=%s", msg));
		
		// mute/unmute volume as well 
		if (enable)
			setStreamVolume(-1);
		else
			setStreamVolume(userSettings.getUserRequestedVolume());
	}
	
	private void setCameraMode(String mode) 
	{
		synchronized(cameraModeLock)
		{
			Log.d(TAG, "Writing " + mode + " to camera mode file");
			Writer writer = null;
			try 
	      	{
				writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(cameraModeFilePath), "US-ASCII"));
			    writer.write(mode);
			    writer.flush();
		    } 
	      	catch (IOException ex) {
	    	  Log.e(TAG, "Failed to save cameraMode to disk: " + ex);
	    	} 
			finally 
	    	{
	    		try {writer.close();} catch (Exception ex) {/*ignore*/}
	    	}
		}
	}
	
	private int readCameraMode() 
	{
		synchronized(cameraModeLock)
		{
			int cameraMode = 0;
			File cameraModeFile = new File (cameraModeFilePath);
	        if (cameraModeFile.isFile())	//check if file exists
	        {			
		    	try {
		    		String serializedCameraMode = new Scanner(cameraModeFile, "US-ASCII").useDelimiter("\\A").next();
		    		cameraMode = Integer.parseInt(serializedCameraMode.trim());
		    	} catch (Exception ex) {
		    		Log.e(TAG, "Failed to read cameraMode: " + ex);
				}
	        }
			return cameraMode;	
		}
	}
	
	public void saveUserSettings()
	{
		synchronized (saveSettingsLock)
		{
			Writer writer = null;
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
	      	String serializedClass = gson.toJson(this.userSettings);
	      	String currentUserSettings = "";
	      	try {      		
	      		currentUserSettings = new Scanner(new File (savedSettingsFilePath), "US-ASCII").useDelimiter("\\A").next();
	      	} catch (Exception e) {Log.d(TAG, "Exception in saveUserSettings" + e.toString()); }
	      	
	      	// Only update userSettings if it has changed
	      	if (serializedClass != currentUserSettings)
	      	{
		      	try 
		      	{
		      		// If old file exists delete it
		      		File oldFile = new File(savedSettingsOldFilePath);
			      	if (oldFile.exists())
					{
						boolean deleteSuccess = oldFile.delete();
						if (!deleteSuccess)
							Log.e(TAG,"Failed to delete " + savedSettingsOldFilePath);
					}
			      	
		      		// rename current file to old
		      		renameFile(savedSettingsFilePath, savedSettingsOldFilePath);
		      		syncFileSystem();	      		
		      		
		      		writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(savedSettingsFilePath), "US-ASCII"));
		      		writer.write(serializedClass);
		    	    writer.flush();
		    	} 
		      	catch (IOException ex) {
		    	  Log.e(TAG, "Failed to save userSettings to disk: " + ex);
		    	} finally 
		    	{
		    		try {writer.close();} catch (Exception ex) {/*ignore*/}
		    		syncFileSystem();
		    	}
	      	
		      	Log.d(TAG, "Saved userSettings to disk");
	      	}
		}
	}
	
	private void renameFile(String currentFilePath, String newFilePath)
	{
		// File (or directory) with old name
		File oldFile = new File(currentFilePath);

		// File (or directory) with new name
		File newFile = new File(newFilePath);
		
		if (newFile.exists())
		{
			boolean deleteSuccess = newFile.delete();
		}
		
		// Rename file (or directory)
		boolean success = oldFile.renameTo(newFile);

		if (!success) {
		   Log.e(TAG, "Failed to rename file");
		}
	}
	
	private void syncFileSystem()
	{
		try
		{
			Process p = Runtime.getRuntime().exec("sync");
			BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(p.getInputStream()));
			p.waitFor();
		} catch (Exception e)
		{
			Log.e(TAG, "Failed to call sync");
			e.printStackTrace();
		}
	}
	
	public class SaveSettingsTask implements Runnable 
	{
		public void run() {
			while (!saveSettingsShouldExit)
			{
				if (saveSettingsUpdateArrived == true)
				{
					saveSettingsUpdateArrived = false;
					saveUserSettings();					
				}

				try
				{
					Thread.sleep(1000);
				}
				catch (Exception e)
				{
					Log.d(TAG, "Save settings task exception" + e.toString());
				}				
			}
		}
	}

	private void recomputeHash() {
		sockTask.SendDataToAllClients(String.format("RECOMPUTEHASH=%s", savedSettingsFilePath));
	}

	static private Object mHdcpLock = new Object();
	private boolean checkHDCPStatus() {
		boolean hdcpStatusChanged = false;
		synchronized (mHdcpLock) {
			boolean currentHDCPInputStatus = (HDMIInputInterface.readHDCPInputStatus() && HDMIInputInterface.readResolutionEnum(false) != 0); // Check for valid resolution 
			boolean currentHDCPOutputStatus = HDMIOutputInterface.readHDCPOutputStatus() == 1;
			// Only send new status when hdcp status changes for either input or output, or if force status update is called
			if ((mHDCPInputStatus != currentHDCPInputStatus) || (mHDCPOutputStatus != currentHDCPOutputStatus) || (mForceHdcpStatusUpdate == true))
			{
				boolean outputHDCPstatus = currentHDCPOutputStatus || mHDCPExternalStatus;
				hdcpStatusChanged = true;
				mHDCPInputStatus = currentHDCPInputStatus;
				mHDCPOutputStatus = currentHDCPOutputStatus;	
				
				if ((mHDCPInputStatus == true && mHDCPEncryptStatus == false) && (mIgnoreHDCP == false))
					setHDCPErrorImage(true);
				else
					setHDCPErrorImage(false);
				
				// Check if HDMI content is visible on screen
				boolean hdmiContentVisible = false;
				for (int sessionId = 0; sessionId < NumOfSurfaces; sessionId++)
				{
					if ((userSettings.getMode(sessionId) == DeviceMode.STREAM_OUT.ordinal()) || 
							(userSettings.getMode(sessionId) == DeviceMode.PREVIEW.ordinal() &&
							userSettings.getStreamState(sessionId) == StreamState.STARTED))
					{
						hdmiContentVisible = true;
						break;
					}
				}
				// The below case is transmitter which is streaming protected content but can't display on loopout
				if (((mHDCPInputStatus == true && hdmiContentVisible == true) && mHDCPEncryptStatus == true && outputHDCPstatus == false) && (mIgnoreHDCP == false))	
					sendHDCPLocalOutputBlanking(true);
				// The below case is receiver which is receiving protect stream but can't display on output
				else if ((mTxHdcpActive == true && mHDCPEncryptStatus == true && outputHDCPstatus == false) && (mIgnoreHDCP == false))
					sendHDCPLocalOutputBlanking(true);
				else
					sendHDCPLocalOutputBlanking(false);
				
				//Call set bypass mode when output HDMI is connected
				if (Boolean.parseBoolean(hdmiOutput.getSyncStatus()) == true)
				{
					setHDCPBypass();
				}
				
				mForceHdcpStatusUpdate = false; // we have updated hdcp status, clear flag
			}
		}
		
		return hdcpStatusChanged;
	}
	
	private void sendHDCPFeedbacks()
	{
		//Send input feedbacks
		if (hdmiInputDriverPresent)
		{		
			if (mHDCPInputStatus == true)
			{
				sockTask.SendDataToAllClients(String.format("%s=%b", "HDMIIN_SOURCEHDCPACTIVE", true));
				sockTask.SendDataToAllClients(String.format("%s=%d", "HDMIIN_SOURCEHDCPSTATE", 57));
			}
			else
			{
				sockTask.SendDataToAllClients(String.format("%s=%b", "HDMIIN_SOURCEHDCPACTIVE", false));
				if (Boolean.parseBoolean(hdmiInput.getSyncStatus()) == true) //Valid input present
					sockTask.SendDataToAllClients(String.format("%s=%d", "HDMIIN_SOURCEHDCPSTATE", 0));
				else
					sockTask.SendDataToAllClients(String.format("%s=%d", "HDMIIN_SOURCEHDCPSTATE", 58));
			}
		}
		//Send output feedbacks
		// Check if HDMI content is visible on screen
		boolean hdmiContentVisible = false;
		for (int sessionId = 0; sessionId < NumOfSurfaces; sessionId++)
		{
			if ((userSettings.getMode(sessionId) == DeviceMode.STREAM_OUT.ordinal()) || 
					(userSettings.getMode(sessionId) == DeviceMode.PREVIEW.ordinal() &&
					userSettings.getStreamState(sessionId) == StreamState.STARTED))
			{
				hdmiContentVisible = true;
				break;
			}
		}
		
		// If force HDCP is enabled, blank output if not authenticated
		// If force HDCP is disabled, blank output if not authenticated and input is authenticated
		if (((mHDCPInputStatus == true && hdmiContentVisible == true) || (userSettings.isHdmiOutForceHdcp() == true)) && ((mHDCPOutputStatus == false) && (mHDCPExternalStatus == false)))
			sockTask.SendDataToAllClients(String.format("%s=%b", "HDMIOUT_DISABLEDBYHDCP", true));
		// The below case is transmitter which is streaming protected content but can't display on loopout
		else if (((mHDCPInputStatus == true && hdmiContentVisible == true) && mHDCPEncryptStatus == true && mHDCPOutputStatus == false) && (mIgnoreHDCP == false))	
			sockTask.SendDataToAllClients(String.format("%s=%b", "HDMIOUT_DISABLEDBYHDCP", true));
		// The below case is receiver which is receiving protect stream but can't display on output
		else if ((mTxHdcpActive == true && mHDCPEncryptStatus == true && mHDCPOutputStatus == false) && (mIgnoreHDCP == false))
			sockTask.SendDataToAllClients(String.format("%s=%b", "HDMIOUT_DISABLEDBYHDCP", true));
		else
			sockTask.SendDataToAllClients(String.format("%s=%b", "HDMIOUT_DISABLEDBYHDCP", false));
	}
	
	public void setExternalHdcpStatus(int hdcpStatus)
	{
		switch(hdcpStatus)
		{ 
		case 50: //unauthenticated
		case 51: //unauthenticated  DEVICE_COUNT_EXCEEDED
		case 52: //unauthenticated CASCADE_DEPTH_EXCEEDED
		case 54: //No HDCP receiver in downstream
			mHDCPExternalStatus = false;			
			break;
		case 53: //authenticated
			mHDCPExternalStatus = true;
			break;
		default:
			mHDCPExternalStatus = false;
		}

		mForceHdcpStatusUpdate = true;
	}
	
	private class setNoVideoImage extends TimerTask
	{
		private int cameraMode;
		setNoVideoImage(int mode)
		{
			cameraMode = mode;
		}
		
		@Override
		public void run() {
			synchronized (mCameraModeScheduleLock)
			{
				if (mNoVideoTimer != null)
				{
					setCameraMode(String.valueOf(cameraMode));
					mNoVideoTimer.cancel();
					mNoVideoTimer = null;
				}
			}
		}
	}
	
	private class doubleSendWindowDimensions extends TimerTask
	{
		private int sessionId;
		doubleSendWindowDimensions(int sessId)
		{
			sessionId = sessId;
		}		
		@Override
		public void run() {
			updateFullWindow(sessionId);
		}
	}
	
	public void checkVideoTimeouts(boolean successfulStateChange)
	{		
		if (successfulStateChange)
		{
			// clear number of timeouts
			numberOfVideoTimeouts = 0;
		}
		else
		{
			if ((++numberOfVideoTimeouts) >= 10) // if more than 10 timeouts occur in a row call recovery
			{
		    	sockTask.SendDataToAllClients("DEVICE_READY_FB=FALSE");
		    	sockTask.SendDataToAllClients("KillEveryThingPlease=true");
			}
		}
	}
	
	public void setHDCPErrorColor()
	{
        sockTask.SendDataToAllClients("SET_HDCP_ERROR_COLOR=TRUE");
	}

	public class OutputDisplayListener implements DisplayListener
	{
		@Override
		public void onDisplayAdded(final int displayId)
		{
			new Thread(new Runnable() {
				public void run() { 
					DisplayManager dm = 
							(DisplayManager) getSystemService(DISPLAY_SERVICE);
					Display dispArray[] = dm.getDisplays();
					if ( (haveExternalDisplays && (dispArray.length > 1) && (displayId == dispArray[1].getDisplayId())) ||
							(!haveExternalDisplays && (displayId == dispArray[0].getDisplayId())) )
					{
						Log.d(TAG, "HDMI Output display has been added");
						handleHdmiOutputChange();
					}
				}
			}).start();
		}

		@Override
		public void onDisplayChanged(int displayId)
		{
			// TODO: I am not seeing this get called, so currently it is unimplemented
			Log.d(TAG, "HDMI Output display has changed");
		}

		@Override
		public void onDisplayRemoved(final int displayId)
		{
			new Thread(new Runnable() {
				public void run() { 
					DisplayManager dm = 
							(DisplayManager) getSystemService(DISPLAY_SERVICE);
					Display dispArray[] = dm.getDisplays();
					if ( (haveExternalDisplays && (dispArray.length == 1) && (displayId != dispArray[0].getDisplayId())) ||
							(dispArray.length == 0) )
					{
						Log.d(TAG, "HDMI Output display has been removed");
						handleHdmiOutputChange();
					}
				}
			}).start();
		}		
	}	
}
