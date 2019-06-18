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
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.BroadcastReceiver;
import android.media.AudioManager;
import android.view.Surface;
import android.view.Display;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.SurfaceHolder.Callback;
import android.view.TextureView;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.app.Activity;
import android.app.Notification;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManager.DisplayListener;

import com.crestron.txrxservice.CresStreamCtrl.StreamState;
import com.crestron.txrxservice.ProductSpecific;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

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
	private final CresStreamCtrl mCtx = this;
	Handler handler;
    CameraStreaming cam_streaming;
    CameraPreview cam_preview;
    GstreamOut gstStreamOut;
    StringTokenizer tokenizer;
    public static int VersionNumber = 2;
    
    GstreamIn streamPlay = null;
    GstreamBase gstreamBase = null;
    WbsStreamIn wbsStream = null;
    WifidVideoPlayer wifidVideoPlayer = null;
    
    private DisplayManager m_displayManager = null;

    public UserSettings userSettings;
    AudioManager amanager;
    public TCPInterface sockTask;
    private boolean mAutoInitMode = false;
   
    HDMIInputInterface hdmiInput;
    HDMIOutputInterface hdmiOutput;

    CresDisplaySurface dispSurface = null;
    
    AirMediaSplashtop mAirMedia = null;
    
    final int cameraRestartTimout = 1000;//msec
    static int hpdHdmiEvent = 0;
    
    private volatile boolean saveSettingsShouldExit = false;
    public static Object saveSettingsLock = new Object();
    public static volatile boolean saveSettingsUpdateArrived = false;

    public final static int NumOfSurfaces = 4;	//TODO: set as per product
    public final static int NumOfTextures = 4;	//TODO: set as per product
    public volatile boolean restartStreamsOnStart = false;
    static String TAG = "TxRx StreamCtrl";
    static String out_url="";
    static String playStatus="false";
    static String stopStatus="true";
    static String pauseStatus="false";
    private DeviceMode lastHDMImode = DeviceMode.PREVIEW;
    boolean StreamOutstarted = false;
    boolean hdmiInputDriverPresent = false;
    public boolean alphaBlending = false;
    public boolean csioConnected = false;
    boolean airMediaLicensed = false;
    boolean[] restartRequired = new boolean[NumOfSurfaces];
    public final static String savedSettingsFilePath = "/data/CresStreamSvc/userSettings";
    public final static String savedSettingsOldFilePath = "/data/CresStreamSvc/userSettings.old";
    public final static String cameraModeFilePath = "/dev/shm/crestron/CresStreamSvc/cameraMode";
    public final static String restoreFlagFilePath = "/data/CresStreamSvc/restore";
    public final static String restartStreamsFilePath = "/dev/shm/crestron/CresStreamSvc/restartStreams";
    public final static String hdmiLicenseFilePath = "/dev/shm/hdmi_licensed";
    public final static String pinpointEnabledFilePath = "/dev/crestron/alphablendingenable";
    private final static String mercuryHdmiOutWaitFilePath = "/dev/shm/crestron/CresStreamSvc/mercuryWait";
    public final static String initializeSettingsFilePath = "/dev/shm/crestron/CresStreamSvc/initializeSettings";
    public final static String hdmiInputHPDFilePath = "/dev/shm/crestron/hdmi/inputHpd";
    public final static String hdmiInputResolutionFilePath = "/dev/shm/crestron/hdmi/inputResolution";
    public final static String hdmiOutputResolutionFilePath = "/dev/shm/crestron/hdmi/outputResolution";
    public final static String surfaceFlingerViolationFilePath = "/dev/shm/crestron/CresStreamSvc/SFviolation";
	private static final String [] InterfaceNames = {"eth0", "eth1"};
    public volatile boolean mMediaServerCrash = false;
    public volatile boolean mDucatiCrash = false;
    public volatile boolean mIgnoreAllCrash = false;
    private FileObserver mediaServerObserver;
    private FileObserver ravaModeObserver;
    private FileObserver hdmiInputHpdObserver;
    private FileObserver hdmiInputResolutionObserver;
    private FileObserver hdmiOutputResolutionObserver;
    private FileObserver surfaceFlingerViolationObserver;
    private Thread monitorCrashThread;
    private boolean mHDCPOutputStatus = false;
    private boolean mHDCPExternalStatus = false;
    private boolean mHDCPInputStatus = false;
    private boolean mIsHdmiOutExternal = false;
    private boolean/*[]*/ mHDCPEncryptStatus = false;//new boolean[NumOfSurfaces];
    private boolean/*[]*/ mTxHdcpActive = false;//new boolean[NumOfSurfaces];
    private boolean mIgnoreHDCP = false; //FIXME: This is for testing
    public volatile boolean mForceHdcpStatusUpdate = true;
    private int mCurrentHdmiInputResolution = -1;
    private int mPreviousValidHdmiInputResolution = 0;
    private int mPreviousAudioInputSampleRate = 0;
    public CountDownLatch streamingReadyLatch = new CountDownLatch(1);
    public CountDownLatch audioReadyLatch = new CountDownLatch(1);
    private FileObserver audioReadyFileObserver;
    private boolean pendingAirMediaLoginCodeChange = false;   
    public volatile boolean enableRestartMechanism = false; // Until we get a start or platform automatically restarts don't restart streams
    private Object cameraModeLock = new Object();
    private volatile Timer mNoVideoTimer = null;
    private Object mCameraModeScheduleLock = new Object();
    public Object mAirMediaLock = new Object();
    private Object mAirMediaCodeLock = new Object();
    private int mAirMediaNumberOfUsersConnected = -1;	//Bug 141088: Setting to -1 will force code change on reboot if set to random
    private boolean mMsMiceEnabled = false;
    private boolean mMiracastEnabled = false;
	public boolean[] mUsedForAirMedia = new boolean[NumOfSurfaces];
    boolean[] updateStreamStateOnFirstFrame = new boolean[NumOfTextures]; // flags to update stream state only on first frame output from MediaCodec - used only in AirMedia currently
    private Object mDisplayChangedLock = new Object();
    private int defaultLoggingLevel = -1;
    private int numberOfVideoTimeouts = 0; //we will use this to track stop/start timeouts
    private final ProductSpecific mProductSpecific = new ProductSpecific();
    private final static String multicastTTLFilePath = "/dev/shm/crestron/CresStreamSvc/multicast_ttl";
    private final static String keyFrameIntervalFilePath = "/dev/shm/crestron/CresStreamSvc/keyframe_interval";
    
    private final static String ducatiCrashCountFilePath = "/dev/shm/crestron/CresStreamSvc/ducatiCrashCount";
    public final static String gstreamerTimeoutCountFilePath = "/dev/shm/crestron/CresStreamSvc/gstreamerTimeoutCount";
    public final static String hdcpEncryptFilePath = "/dev/shm/crestron/CresStreamSvc/HDCPEncrypt";
    private static long lastRecoveryTime = 0;
    public int mGstreamerTimeoutCount = 0;
    public boolean haveExternalDisplays;
    public boolean hideVideoOnStop = false;
    public boolean isRGB888HDMIVideoSupported = true;
    public CrestronHwPlatform mHwPlatform;
    public String mProductName;
    public boolean mCameraDisabled = false;
    public boolean restartMediaServer = false;
    public ServiceMode serviceMode = ServiceMode.Master;
    private final int backgroundViewColor = Color.argb(255, 0, 0, 0);
	public String hostName=null;
	public String domainName=null;
	public videoDimensions[] mVideoDimensions = new videoDimensions[NumOfSurfaces];
	public boolean[] m_InPause = new boolean[NumOfSurfaces];
	private final long hdmiBroadcastTimeout_ms = 60000; 
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
    public native int nativeGetProductTypeEnum();
    public native int nativeGetHDMIOutputBitmask();

    enum DeviceMode {
        STREAM_IN,
        STREAM_OUT,
        PREVIEW,
        WBS_STREAM_IN,
        CHROMA_KEY_STREAM,
    }
    
    enum ServiceMode {
    	Master(0),
    	Slave(1);
    	
    	private final int value;
    	ServiceMode(int value)
    	{
    		this.value = value;
    	}
        public int getValue() 
        {
            return value;
        }
        public static String getStringValueFromInt(int i) 
        {
            for (ServiceMode mode : ServiceMode.values()) 
            {
                if (mode.getValue() == i) 
                {
                    return mode.toString();
                }
            }
            return ("Invalid Service Mode.");
        }
        
        public static ServiceMode getServiceModeFromInt(int i) 
        {
            for (ServiceMode mode : ServiceMode.values()) 
            {
                if (mode.getValue() == i) 
                {
                    return mode;
                }
            }
            return ServiceMode.Master;
        }
    };
    
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

    public class AirMediaDisplayConnectionOption {
    	static final int Ip=1;
    	static final int Host=2;
    	static final int HostDomain=3;
    	static final int Custom=4;
    }
    
    public class CresstoreOptions {
    	static final int Publish=1;
    	static final int PublishAndSave=2;
    }
    
    enum CameraMode {
    	Camera(0),
    	StreamOutPaused(1),
    	NoVideo(2),
    	HDCPStreamError(3),
    	HDCPAllError(4),
    	BlackScreen(5),
    	PreviewPaused(6);
    	
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
				Log.i(TAG, MiscUtils.stringFormat("Unknown hardware platform %d, please update enum!!!!!", x));
        		return eHardwarePlatform_Unknown;
        	}
        }
    }
    
    public enum CrestronProductName
    {
    	DMC_STR(0xE4),
    	TXRX(0x1C),
    	DGE100(0x19),
    	DGE200(0x1D),
    	TS1542(0x17),
    	TS1542_C(0x18),
    	X60(0x7E00),
    	Mercury(0x20),
    	DMPS_4K_STR(0x24),
    	AM300(0x2D),
    	AM200(0x2E),
    	Unknown(0x0);
    	
    	private final int value;
    	
    	CrestronProductName(int value) 
        {
            this.value = value;
        }
    	
    	public static CrestronProductName fromInteger(int x) {
        	switch(x) {
        	case 0xE4:
        		return DMC_STR;
        	case 0x1C:
        		return TXRX;
        	case 0x19:
        		return DGE100;
        	case 0x1D:
        		return DGE200;
        	case 0x17:
        		return TS1542;
        	case 0x18:
        		return TS1542_C;
        	case 0x7E00:
        		return X60;
        	case 0x20:
        		return Mercury;
        	case 0x24:
        		return DMPS_4K_STR;
        	case 0x2D:
        		return AM300;
        	case 0x2E:
            	return AM200;
        	default:
				Log.i(TAG, MiscUtils.stringFormat("Unknown product type %d, please update enum!!!!!", x));
        		return Unknown;
        	}
        }
    }

    // ***********************************************************************************
    // Keep updated with definitions in trunk/customFiles/src/external/crestron/productNameUtil/productName.h
    // ***********************************************************************************    
    private String getProductName(int product_type)
    {
    	switch(product_type) {
    	case 0x1C:
    		return "DM_TXRX";
    	case 0x20:
    		return "MERCURY";
    	case 0x24:
    		return "DMPS3-4K";
    	case 0x2D:
    		return "AM-300";
    	case 0x2E:
    		return "AM-200";
    	default:
    		return "Crestron Device";
    	}
    }

    private class MyReentrantLock extends ReentrantLock {
    	private String name;
    	
    	MyReentrantLock(boolean fair, String id) {
    		super(fair);
    		name = id;
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
    		Log.i(TAG, s + ": about to take " + name + "     Current owner tid=" + getThreadId() + "  queueLength=" + getQueueLength());
    		lock();
    		Log.i(TAG, s + ": " + name + " taken   Owner tid=" + getThreadId() + "  queueLength=" + getQueueLength() + "  holdCount=" + getHoldCount());
    	}
    	
    	void unlock(String s)
    	{
    		unlock();
    		Log.i(TAG, s + ": " + name + " released   isLocked=" + isLocked() + "  holdCount=" + getHoldCount());
    	}
    }
    
    private final MyReentrantLock hdmiLock				= new MyReentrantLock(true, "hdmiLock"); // fairness=true, makes lock ordered
    private final MyReentrantLock cameraLock			= new MyReentrantLock(true, "CameraLock"); // fairness=true, makes lock ordered
    private final MyReentrantLock[] stopStartLock		= new MyReentrantLock[NumOfSurfaces]; // members will be allocated in constructor
    private final MyReentrantLock[] streamStateLock 	= new MyReentrantLock[NumOfSurfaces]; // members will be allocated in constructor

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
		Log.i(TAG,"loading csio product info library" );
		System.loadLibrary("CsioProdInfo");
		Log.i(TAG,"loading cresstreamctrl jni library" );
		System.loadLibrary("cresstreamctrl_jni");
	}
    
    //StreamState devicestatus = StreamState.STOPPED;
    //HashMap
    HashMap<Integer, Command> hm;
    HashMap<Integer, myCommand> hm2;
    HashMap<Integer, myCommand2> hm3;
        
    @Override
        public void onCreate() {
    	    super.onCreate();
    		// Create Handler onCreate so that it is always associated with UI thread (main thread)
    		handler = new Handler();
            super.onCreate();
    		int windowWidth = 1920;
    		int windowHeight = 1080;
    		hideVideoOnStop = nativeHideVideoBeforeStop();
    		mHwPlatform = CrestronHwPlatform.fromInteger(nativeGetHWPlatformEnum());
    		mProductName = getProductName(nativeGetProductTypeEnum());
    		
    		m_displayManager = (DisplayManager) this.getApplicationContext().getSystemService(Context.DISPLAY_SERVICE);
	        if (m_displayManager != null)
	        {
	        	m_displayManager.registerDisplayListener(mDisplayListener, null);
	        }
	        
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
	    			Log.i(TAG, "about to wait for external display(s)");
	    			for(int i=1; i<=15; i++)
	    			{
	    				SystemClock.sleep(1000);
	    				Log.i(TAG, "waited " + i + " sec");
	    			}
	    			Log.i(TAG, "done waiting for external display(s)");
	    			MiscUtils.writeStringToDisk(startUpFilePath, String.valueOf(1));
    			}
    		}
    		
    		// Allocate startStop and streamstate Locks, also initializing array
    		for (int sessionId = 0; sessionId < NumOfSurfaces; sessionId++)
    		{
    			stopStartLock[sessionId] = new MyReentrantLock(true, "StopStartLock-"+sessionId);
    			streamStateLock[sessionId] = new MyReentrantLock(true, "StreamStateLock-"+sessionId);
    			mVideoDimensions[sessionId] = new videoDimensions(0, 0);
    			m_InPause[sessionId] = false;
    			mUsedForAirMedia[sessionId] = false;
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
    						Log.i(TAG,"Global uncaught Exception !!!!!!!!!!!" );
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
    									else if (userSettings.getMode(sessionId) == DeviceMode.WBS_STREAM_IN.ordinal())
    										wbsStream.onStop(sessionId);
    								}
    							}
    						}
    						catch (Exception e) { 
    							Log.e(TAG, "Failed to stop streams on error exit");
    							e.printStackTrace(); 
    						}
//    						if (oldHandler != null)
//    							oldHandler.uncaughtException(paramThread, paramThrowable); //Delegates to Android's error handling
//    						else
    						// Do not rely on Android error handling, just exit
							System.exit(2); //Prevents the service/app from freezing							
    					}
    				});
    		
    		boolean wipeOutUserSettings = false;
    		boolean useOldUserSettingsFile = false;
    		boolean fixSettingsVersionMismatch = false;

    		File restoreFlagFile = new File(restoreFlagFilePath);
    		boolean isRestore = restoreFlagFile.isFile();
    		if (isRestore)
    		{
    			Log.i(TAG, "restore flag is set");
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
    			Gson gson = builder.registerTypeAdapterFactory(new LenientTypeAdapterFactory()).create();
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
    				Gson gson = builder.registerTypeAdapterFactory(new LenientTypeAdapterFactory()).create();
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
    					Log.i(TAG, "Saved userSettings version too old, upgrading settings, saved version: " 
    							+ userSettings.getVersionNum() + " required version: " + VersionNumber);
    				}

    				saveUserSettings();
    			}
    			catch (Exception ex)
    			{
    				Log.e(TAG, "Could not upgrade userSettings: " + ex);
    			}
    		}
 
    		// This must happen before we receive the initial service mode from csio once TCP interface is up.
    		// If service mode from csio differs from our setting, a request to restart the service will be sent to csio
			serviceMode = ServiceMode.getServiceModeFromInt(userSettings.getServiceMode());
			Log.i(TAG, "===================== CresStreamSvc service mode = " + 
					((serviceMode == ServiceMode.Slave)?"Slave":"Master") + " =====================");
			
    		//Start service connection
    		tokenizer = new StringTokenizer();
    		sockTask = new TCPInterface(this);
    		sockTask.execute(new Void[0]);  
    		    		
    		if (MiscUtils.readStringFromDisk(initializeSettingsFilePath).compareTo("1") != 0)
    		{
    			// for some products set up the defaults on restore
    			// TODO: If we want special restore section do that here    		
    			//    		if (isRestore)
    			//    		{
    			//    			switch (nativeGetProductTypeEnum())
    			//				{
    			//				default:
    			//					break;
    			//				}
    			//    		}

    			// Special case to knock DMPS out of camera modes and force adapter to default value of 0 for each reboot
    			if (nativeGetProductTypeEnum() == 0x24)
    			{
    				// Does not support preview mode, set all to stream in
    				for (int sessionId = 0; sessionId < NumOfSurfaces; sessionId++)
    				{
    					userSettings.setMode(DeviceMode.STREAM_IN.ordinal(), sessionId);
    					userSettings.setUserRequestedStreamState(StreamState.STOPPED, sessionId);
    				}	
    				setAirMediaAdaptorSelect(0);
    			}    			
    		}
    		else
    		{
    			if (nativeGetProductTypeEnum() == 0x24)
    			{
    				for (int sessionId = 0; sessionId < NumOfSurfaces; sessionId++)
    				{
    					userSettings.setMode(DeviceMode.STREAM_IN.ordinal(), sessionId);
    				}
    				setAirMediaAdaptorSelect(0);
    			}    				
    		}

			// Product table 
    		switch (CrestronProductName.fromInteger(nativeGetProductTypeEnum()))
    		{
	    		case DGE100:
				case DGE200:
				case TS1542:
	    		case TS1542_C:
	    		case TXRX:
	    		{
					// Bug 154293: RGB888 on OMAP cannot support simultaneous video, BW limitation
	    			isRGB888HDMIVideoSupported = false;
	    			break;
	    		}
	    		case DMC_STR:
	    		case X60:
	    		case Mercury:
	    		case DMPS_4K_STR:
	    		case AM300:
	    		case AM200:
	    		case Unknown:
	    		{
	    			// Todo: Handle when needed
	    		}
    		}

    		// This needs to be done before Gstreamer setup
    		{
    			// If mediaserver is in bad state this could get stuck
    			// Load GstreamBase first!
    			final CountDownLatch latch = new CountDownLatch(1);
    			Thread checkCameraThread = new Thread(new Runnable() {
    				public void run() {
    					if (mHwPlatform == CrestronHwPlatform.eHardwarePlatform_Amlogic)
    					{
    						// sometimes txrxservice starts up too quickly after crash or kill and
    						// results in camera not closed. (part of bug 150720 - streamout would not restart after crash) 
    						try { Thread.sleep(500); } catch (InterruptedException e){}
    					}
    					mCameraDisabled = getCameraDisabled();
    					if (mCameraDisabled == true)
    						Log.w(TAG, "Camera is either disabled or not available, removing access");
    					else
    						Log.i(TAG, MiscUtils.stringFormat("Camera is enabled, allowing access restartMediaServer="+restartMediaServer));
    					latch.countDown();
    				}
    			});
    			checkCameraThread.start();
				Log.i(TAG, MiscUtils.stringFormat("---- Launched checkCameraThread ----"));

    			boolean successfulStart = true; //indicates that there was no time out condition
    			try { successfulStart = latch.await(3000, TimeUnit.MILLISECONDS); }
    			catch (InterruptedException ex) { ex.printStackTrace(); }
				Log.i(TAG, MiscUtils.stringFormat("---- end of wait for checkCameraThread - successfulStart="+successfulStart+" ----"));

    			// Library failed to load kill mediaserver and restart txrxservice
    			if (!successfulStart || restartMediaServer)
    			{
    				Log.e(TAG, "Camera failed to initialize successfully, restarting txrxservice and mediaserver");
    				Thread restartCameraThread = new Thread(new Runnable() {
        				public void run() {
                			while (!csioConnected) {
                				Log.i(TAG, "Waiting for csio connection");
                				try { Thread.sleep(500); } catch (InterruptedException e){}//Poll every 0.5 seconds
                			}
                			
            				RecoverMediaServer();
            				RecoverTxrxService();
            				try { Thread.sleep(3000); } catch (InterruptedException e){}
        				}
        			});
    				restartCameraThread.start();
    				Log.i(TAG, MiscUtils.stringFormat("---- Launched restart due to camera thread = ----"));
    			}
    		}
    		
    		//Input Streamout Config
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
    		streamPlay = new GstreamIn(CresStreamCtrl.this);

    		// Added for real camera on x60
    		// to-do: support having both hdmi input and a real camera at the same time...
    		if(ProductSpecific.hasRealCamera())
    		{
    			gstStreamOut = new GstreamOut(CresStreamCtrl.this);
    			// PEM - uncomment if you want to enable camera preview for real camera.
    			// To-do: support platform that has an hdmi input and a real camera.
    			// in X60, now use GstPreview, no longer use NativePreview, so comment out below:
    			//cam_preview = new CameraPreview(this, null);    
    		}
    		
    		wbsStream = new WbsStreamIn(CresStreamCtrl.this);
    		
    		wifidVideoPlayer = new WifidVideoPlayer(CresStreamCtrl.this);

    		hdmiOutput = new HDMIOutputInterface(nativeGetHDMIOutputBitmask());
    		setHDCPBypass();

    		Thread saveSettingsThread = new Thread(new SaveSettingsTask());    	
    		saveSettingsThread.start();

    		

    		refreshOutputResolution();
    		sendHdmiOutSyncState(); // Send out initial hdmi out resolution info
    		
    		// This must be done before CresDisplaySurface is created   		
    		// Wait until file exists then check
			while ((new File(pinpointEnabledFilePath)).exists() == false)
			{
				try { Thread.sleep(100); } catch (InterruptedException e){}
			}			
			int pinpointEnabled = 0;
			try {
				pinpointEnabled = Integer.parseInt(MiscUtils.readStringFromDisk(pinpointEnabledFilePath));
			} catch (NumberFormatException e) {}			
			alphaBlending = (pinpointEnabled == 1) ? true : false;
			
            // Create a DisplaySurface to handle both preview and stream in
    		createCresDisplaySurface();
			Point size = getDisplaySize();
			SetWindowManagerResolution(size.x, size.y, haveExternalDisplays);

    		//Get HPDEVent state fromsysfile
    		if (hdmiInputDriverPresent)
    		{
    			hpdHdmiEvent = HDMIInputInterface.getHdmiHpdEventState();            
    			Log.i(TAG, "hpdHdmiEvent :" + hpdHdmiEvent);
    		}

    		//AudioManager
    		amanager=(AudioManager)getSystemService(Context.AUDIO_SERVICE);

    		// Monitor HDMI for resolution changes
    		monitorHdmiStates();
    		
    		// Monitor surfaceFlinger violations
    		monitorSurfaceFlingerViolations();

    		//Play Control
    		hm = new HashMap<Integer, Command>();
    		hm.put(4/*"CHROMA_KEY_STREAM"*/, new Command() {
    			public void executeStart(int sessId) {startChromaKeyStream(sessId); };
    		});
    		hm.put(3/*"WBS_STREAMIN"*/, new Command() {
    			public void executeStart(int sessId) {startWbsStream(sessId); };
    		});
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
    		hm2.put(4/*"CHROMA_KEY_STREAM"*/, new myCommand() {
    			public void executeStop(int sessId, boolean fullStop) {stopChromaKeyStream(sessId); };
    		});
    		hm2.put(3/*"WBS_STREAMIN"*/, new myCommand() {
    			public void executeStop(int sessId, boolean fullStop) {stopWbsStream(sessId); };
    		});
    		hm2.put(2/*"PREVIEW"*/, new myCommand() {
    			public void executeStop(int sessId, boolean fullStop) {stopPreview(sessId,true);};
    		});
    		hm2.put(1/*"STREAMOUT"*/, new myCommand() {
    			public void executeStop(int sessId, boolean fullStop) {stopStreamOut(sessId, fullStop);};
    		});
    		hm2.put(0/*"STREAMIN"*/, new myCommand() {
    			public void executeStop(int sessId, boolean fullStop) {stopStreamIn(sessId); };
    		});
    		hm3 = new HashMap<Integer, myCommand2>();
    		hm3.put(4/*"CHROMA_KEY_STREAM"*/, new myCommand2() {
    			public void executePause(int sessId) {pauseChromaKeyStream(sessId); };
    		});
    		hm3.put(3/*"WBS_STREAMIN"*/, new myCommand2() {
    			public void executePause(int sessId) {pauseWbsStream(sessId); };
    		});
    		hm3.put(2/*"PREVIEW"*/, new myCommand2() {
    			public void executePause(int sessId) {pausePreview(sessId);};
    		});
    		hm3.put(1/*"STREAMOUT"*/, new myCommand2() {
    			public void executePause(int sessId) {pauseStreamOut(sessId);};
    		});
    		hm3.put(0/*"STREAMIN"*/, new myCommand2() {
    			public void executePause(int sessId) {pauseStreamIn(sessId); };
    		});
    		
    		hdmiLicenseThread(this);
    		airMediaLicenseThread(this);

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
    		
    		// Monitor Audio Ready flag
    		monitorAudioReady();
    		    		
    		initAppFiles();
    		
    		// Make sure that current Iframe interval is written to disk
    		setKeyFrameInterval(userSettings.getKeyFrameInterval());

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
            Log.i(TAG,"CresStreamCtrl Started !" );            
            return START_STICKY;	// No longer needed since it is not a service
    }
    
    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// SERVICE
    ////////////////////////////////////////////////////////////////////////////////////////////////
    
    private final IDecoderService.Stub mBinder = new IDecoderService.Stub() {
    	public int attachSurface(int id, Surface s)
    	{
    		Log.i(TAG,"attachSurface: id="+id+"   surface="+s.toString());
    		setSurface(id, s);
    		return 0;
    	}
    	
    	public int detachSurface(int id, Surface s)
    	{
    		Log.i(TAG,"detachSurface: id="+id+"   surface="+s.toString());
    		deleteSurface(id);
    		return 0;
    	}
    	
    	// Functions below for debugging purposes only
    	public int masterStartStream(int id, String url)
    	{
    		Log.i(TAG, "masterStartStream(): url = " + url + " for sessionId = " + id);
    		setStreamInUrl(url, id);
    		Log.i(TAG, "masterStartStream(): calling startStreamIn for sessionId = " + id);
    		startStreamIn(id);
    		return 0;
    	}
    	
    	public int masterStopStream(int id)
    	{
    		Log.i(TAG, "masterStopStream(): calling stopStreamIn for sessionId = " + id);
    		stopStreamIn(id);
    		return 0;
    	}
    };
    
    @Override
    public IBinder onBind(Intent intent)
    {
        Log.i(TAG, "onBind  intent= " + intent.toString());
        return mBinder;
    } 

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(TAG, "onUnbind  intent= " + intent.toString());
        return super.onUnbind(intent);
        // TODO stop all streams
    }

    @Override
    public void onRebind(Intent intent) {
        Log.i(TAG, "onRebind  intent= " + intent.toString());
        super.onRebind(intent);
    }
    
    public void onDestroy(){
        super.onDestroy();
        saveUserSettings();
        saveSettingsShouldExit = true;
        sockTask.cancel(true);
        mediaServerObserver.stopWatching();
//		hdmiInputHpdObserver.stopWatching();
    	hdmiInputResolutionObserver.stopWatching();
    	hdmiOutputResolutionObserver.stopWatching();
        if (m_displayManager != null){
        	m_displayManager.unregisterDisplayListener(mDisplayListener);
        }
        try {
        	cam_streaming.stopRecording(false);
            if(cam_preview != null)
            {
        	   cam_preview.stopPlayback(false);
            }
	        for (int i = 0; i < NumOfSurfaces; i++)
	        {
	        	streamPlay.onStop(i);
	        }
        } finally {}
        
        if (dispSurface != null)
        	dispSurface.RemoveView();
        
        CresCamera.releaseCamera();
    }
    
    public void runOnUiThread(Runnable runnable) {
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
    
    public void setServiceMode(int mode)
    {
    	if (mode != serviceMode.ordinal())
    	{
    		Log.i(TAG, "Service mode needs to change from " + ((serviceMode == ServiceMode.Master) ? "Master" : "Slave") +
    				" to " + ((mode == ServiceMode.Master.ordinal()) ? "Master" : "Slave") + " - requesting restart .....");
    		userSettings.setServiceMode(mode);
    		serviceMode = ServiceMode.getServiceModeFromInt(mode);
			RestartTxrxService();
    	}
    	else
    	{
    		Log.i(TAG, "Service mode is already set to " + ((serviceMode == ServiceMode.Master) ? "Master" : "Slave"));
    	}
    }
    
    public void setHostName(String dflt)
    {
		hostName = MiscUtils.getHostName(dflt);
    }
    
    public void setDomainName(String dflt)
    {
    	domainName = MiscUtils.getDomainName(dflt);
    }
    
    private boolean isCameraDisabledBySecurity()
    {
    	String cameraStatus = MiscUtils.readBuildProp("sys.secpolicy.camera.disabled");
    	Log.i(TAG, "isCameraDisabledBySecurity(): "+cameraStatus);
    	return (cameraStatus.contains("1")) ? true : false;    	
    }
    
    public boolean getCameraDisabled()
    {
    	Camera c = null;
		boolean cameraDisabled = false;
		
		if ((Camera.getNumberOfCameras() > 0) && !isCameraDisabledBySecurity()) {
			try {
				c = Camera.open(); // try to get camera
				c.release();	// relase camera if obtained
				cameraDisabled = false;
			}
			catch (RuntimeException e) {
				Log.i(TAG, "Runtime exception in getCameraDisabled");
				restartMediaServer = true;
			}
		} else {
			Log.i(TAG, "Camera feature not available according to PackageManager");
			cameraDisabled = true;
		}
		return cameraDisabled;
    }

    public Surface getSurface(int id) 
    {
    	Surface s = null;
    	if (dispSurface != null)
    	{
    		s = dispSurface.getSurface(id);
    	}
    	if (s == null)
    	{
    		Log.w(TAG, "GetSurface(): returning null surface");
    	}
    	return s;
    }
    
    public void setSurface(int id, Surface s)
    {
    	if (dispSurface != null)
    	{
    		dispSurface.setSurface(id, s);
    	}
    }
    
    public void deleteSurface(int id)
    {
    	if (dispSurface != null)
    	{
    		dispSurface.deleteSurface(id);
    	}
    }
    
    public int surface2streamId(Surface surface)
    {
    	if (dispSurface != null)
    		return dispSurface.surface2streamId(surface);
    	else
    		return -1;
    }
    
    public SurfaceView getSurfaceView(int id) {
    	return dispSurface.GetSurfaceView(id);
    }
    
    public TextureView getTextureView(int id) {
    	return dispSurface.GetTextureView(id);      
    }
    
    public SurfaceTexture getSurfaceTexture(int id) {
    	return dispSurface.GetSurfaceTexture(id);
    }
    
    public void setSurfaceViewTag(int idx, String tag)
    {
    	dispSurface.setTag(idx, tag);
    }
    
    private void airMediaLicenseThread(final CresStreamCtrl streamCtrl)
    {	
		new Thread(new Runnable() {
    		@Override
    		public void run() { 
    			// Wait until CSIO is connected (Bug 135686)
				// next line For debugging only - temporary
    			// airMediaLicensed = (new File("/data/CresStreamSvc/airmedialicense")).exists();
				Log.i(TAG, "******************  Airmedia Startup **************");
				if (!airMediaLicensed)
				{
    				while ((new File(AirMediaSplashtop.licenseFilePath)).exists() == false)
    				{
    					try { Thread.sleep(5000); } catch (InterruptedException e){}//Poll every 5 seconds
    				}
    				airMediaLicensed = AirMediaSplashtop.checkAirMediaLicense();
				}
				if (airMediaLicensed)
				{
					Log.i(TAG, MiscUtils.stringFormat("AirMedia is %s licensed", ((airMediaLicensed) ? "" : "not")));
				}
    			while (!csioConnected) {
        			Log.v(TAG, "AirMedia is licensed: try to start AirMedia - waiting for csio to connect");
    				try { Thread.sleep(500); } catch (InterruptedException e){}//Poll every 0.5 seconds
    			}
    			// Wait until file exists then check
    			if (mAirMedia == null && airMediaLicensed) {
    				Log.i(TAG, "Calling AirMediaConstructor from airMediaLicenseThread");
    				mAirMedia = new AirMediaSplashtop(streamCtrl); 
    				msMiceEnable(userSettings.getMsMiceEnable());
    				// Ensure any existing ms-mice connections that exist are dropped
    				for (int sessionId = 0; sessionId < NumOfSurfaces; sessionId++)
    				{
    					if (userSettings.getAirMediaLaunch(sessionId)) {
    						userSettings.setAirMediaLaunch(false, sessionId);
    						streamPlay.wfdStop(sessionId, 0);
    					}
    				}
    			}
    		}
    	}).start();
    }
    
    private void hdmiLicenseThread(final CresStreamCtrl streamCtrl)
    {	
		new Thread(new Runnable() {
    		@Override
    		public void run() { 
    			// Wait until file exists then check
    			while ((new File(hdmiLicenseFilePath)).exists() == false)
    			{
    				try { Thread.sleep(1000); } catch (InterruptedException e){}//Poll every 5 seconds
    			}
    			
    			int hdmiLicensed = 0;
    			try {
    				hdmiLicensed = Integer.parseInt(MiscUtils.readStringFromDisk(hdmiLicenseFilePath));
    			} catch (NumberFormatException e) {}
    			
    			if (hdmiLicensed == 1)
    			{
    				hdmiInputDriverPresent = ProductSpecific.isHdmiDriverPresent();
    	    		HDMIInputInterface.setHdmiDriverPresent(hdmiInputDriverPresent);

    	    		if (hdmiInputDriverPresent)
    	    		{
    	    			Log.i(TAG, "HDMI input driver is present");
    	    			hdmiInput = new HDMIInputInterface();
    	    			//refresh resolution on startup
    	    			hdmiInput.setResolutionIndex(HDMIInputInterface.readResolutionEnum(true));

    	    			// Populate hdmiInput info
    	    			refreshInputResolution();
    	    			
    	    			//Enable StreamIn and CameraPreview 
    	    			cam_streaming = new CameraStreaming(streamCtrl);
    	    			cam_preview = new CameraPreview(streamCtrl, hdmiInput);    
    	    			// Set up Ducati
    	    			ProductSpecific.getHdmiInputStatus();
    	    		}
    	    		else
    	    			Log.i(TAG, "HDMI input driver is NOT present");
    			}
    			else
    			{
    				hdmiInputDriverPresent = false;
    				cam_preview = null;
    				cam_streaming = null;
    			}
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
    				
    				int maxVRes, maxHRes;
    	    		int rotation = 0;
    	    		String rotationStr = MiscUtils.readBuildProp("persist.crestron.hwrotation");
    	    		try { rotation = Integer.parseInt(rotationStr); } catch (NumberFormatException e) {}
    	    		if (rotation == 3)
    	    		{
    	    			maxVRes = 1920;
    	    			maxHRes = 1200;
    	    		}
    	    		else
    	    		{
    	    			maxVRes = 1200;
    	    			maxHRes = 1920;
    	    		}
    	    		if (serviceMode == ServiceMode.Master)
    	    		{
    	    			dispSurface = new CresDisplaySurfaceMaster(streamCtrl, maxHRes, maxVRes, haveExternalDisplays, backgroundViewColor); // set to max output resolution
    	    		} else {
    	    			dispSurface = new CresDisplaySurfaceSlave(streamCtrl);
    	    		}
    				latch.countDown();
    			}
    		});
    		try { 
        		if (latch.await(60, TimeUnit.SECONDS) == false)
        		{
        			Log.e(TAG, "createCresDisplaySurface: timeout after 60 seconds");
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
    		int maxVRes, maxHRes;
    		int rotation = 0;
    		String rotationStr = MiscUtils.readBuildProp("persist.crestron.hwrotation");
    		try { rotation = Integer.parseInt(rotationStr); } catch (NumberFormatException e) {}
    		if (rotation == 3)
    		{
    			maxVRes = 1920;
    			maxHRes = 1200;
    		}
    		else
    		{
    			maxVRes = 1200;
    			maxHRes = 1920;
    		}
    		if (serviceMode == ServiceMode.Master)
    		{
    			dispSurface = new CresDisplaySurfaceMaster(streamCtrl, maxHRes, maxVRes, haveExternalDisplays, backgroundViewColor); // set to max output resolution
    		} else {
    			dispSurface = new CresDisplaySurfaceSlave(streamCtrl);
    		}
    	}
    }

    private void monitorMediaServer()
    {
    	final String commandIntent = "com.crestron.crashObserver";
    	final Context ctx = (Context)this;		
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
				// Send broadcast for third party apps
				Intent i = new Intent(commandIntent);
				i.putExtra("application", "mediaserver");
				ctx.sendBroadcast(i);
				
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
						    Log.i(TAG, "monitorCrashState(): restartStreamState is true");
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
    	new Thread(new Runnable() {
    		@Override
    		public void run() {
    			if (mAirMedia != null)
    			{
    				mAirMedia.recover();   		
    			}
    		}
    	}).start();
    	
        Log.i(TAG, "Restarting Streams - recoverFromCrash");
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
    	sockTask.SendDataToAllClients(MiscUtils.stringFormat("CLEARDUCATISTATE=%d", state));
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
	    				        boolean onlyRestartAudioNeeded = true;	// if streamout is started we need to restart stream
	    				        for(int sessionId = 0; sessionId < NumOfSurfaces; sessionId++)
	    				        {
	    				        	if ( (userSettings.getMode(sessionId) == DeviceMode.STREAM_OUT.ordinal()) &&
	    				        			(userSettings.getUserRequestedStreamState(sessionId) == StreamState.STARTED) )
	    				        	{
	    				        		onlyRestartAudioNeeded = false;
	    				        		break;
	    				        	}
	    				        }
	    				        if (onlyRestartAudioNeeded)
	    				        {
	    				        	Log.i(TAG, "Restarting Audio - sample rate change");
	    				        	cam_preview.restartAudio();	// Can get away with only restarting audio here
	    				        }
	    				        else
	    				        {
	    				        	Log.i(TAG, "Restarting Streams - sample rate change");
	    				        	restartStreams(true); //skip stream in since it does not use hdmi input
	    				        }
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
		      		if (!audioDropDoneFile.isFile())	//check if file exists, create if does not exist
		            {
		      			try {
		      				audioDropDoneFile.getParentFile().mkdirs(); 
		      				audioDropDoneFile.createNewFile();
		            	} catch (Exception e) {}
		            }
		      		
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
    	
// Workaround to mute airmedia during rava call
    	if ((mAirMedia != null))
    	{
    		setStreamMusicMute(enabled);
    	}
    	
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
    
    private void monitorAudioReady()
    {
    	final String audioReadyFilePath = "/sys/devices/platform/crestron-mcuctrl/avReady";
    	final Object audioReadyLock = new Object();
    	File audioReadyFile = new File (audioReadyFilePath);
    	// File will exist because kernel creates is on boot, if dne then assume audio ready
    	if (!audioReadyFile.isFile())	//check if file exists
    	{
    		Log.i(TAG, "Audio subsystem is ready");
    		audioReadyLatch.countDown();
    		return;
    	}

    	// Set initial state, before file observing
    	int initialAudioReady = 0;
    	try
    	{
    		initialAudioReady = Integer.parseInt(MiscUtils.readStringFromDisk(audioReadyFilePath));
        } catch (NumberFormatException e) {}

		// Stop/Start all audio
        if (initialAudioReady == 1)
        {
        	Log.i(TAG, "Audio subsystem is ready");
        	audioReadyLatch.countDown();
        	return;
        }
        
        audioReadyFileObserver = new FileObserver(audioReadyFilePath, FileObserver.CLOSE_WRITE) {						
			@Override
			public void onEvent(int event, String path) {
				synchronized (audioReadyLock)
				{
					int audioReady = 0;
			        try
			        {
			        	audioReady = Integer.parseInt(MiscUtils.readStringFromDisk(audioReadyFilePath));
			        } catch (NumberFormatException e) {}

					// Stop/Start all audio
			        if (audioReady == 1)
			        {
			        	Log.i(TAG, "Audio subsystem is ready");
			        	audioReadyLatch.countDown();
			        	audioReadyFileObserver.stopWatching(); 		// This is a one shot
			        	return;
			        }

				}
			}
		};
		audioReadyFileObserver.startWatching();
    }
    
    public void restartStreams(final boolean skipStreamIn) 
    {
		Log.i(TAG,"****** restartStreams " + String.valueOf(skipStreamIn) + " *****");
    	restartStreams(skipStreamIn, false);
    }
    
    public void restartStreams(final boolean skipStreamIn, final boolean skipPreview) 
    {
    	// If resolution change or crash occurs we don't want to restart until we know the system is up and stable
    	if (enableRestartMechanism == false)
    	{
    		// MNT - 1.26.16 - Even if the system is not up - This was causing the ducati
    		// engine to not start up in some scenarios correctly
    		writeDucatiState(1);
    		mMediaServerCrash = false;
    		
    		Log.i(TAG, "Filtering out restartStreams because restart mechanism is currently disabled");
    		
    		return;
    	}
    	
    	final CountDownLatch latch = new CountDownLatch(1);
    	
    	// Skip Stream in is when we need to only restart camera modes i.e. when resolution changes
    	new Thread(new Runnable() {
    		public void run() {	
    			// Make sure that CresStreamCtrl Constructor finishes before restarting 
    			try { streamingReadyLatch.await(); } catch (Exception e) {}
        		cameraLock.lock("restartStreams");
    			for (int sessionId = 0; sessionId < NumOfSurfaces; sessionId++)
                {
    				stopStartLock[sessionId].lock("restartStreams");
                }
    	    	long currentTime = MiscUtils.getSystemUptimeMs();
    			long deltaTime = Math.abs(currentTime - lastRecoveryTime);
    			if (deltaTime <= 10000)	// Less than 10 seconds
    			{
    				Log.i(TAG, "Holding off restart streams for " + (20000 - deltaTime) + "ms");
    				try { Thread.sleep(20000 - deltaTime); } catch (InterruptedException e) {}
    			}
    			
    	    	try
    	    	{	
    	    		boolean restartStreamsCalled = false;
			    	Log.i(TAG, "Restarting Streams...");
			
			    	//If streamstate was previously started, restart stream
			        for (int sessionId = 0; sessionId < NumOfSurfaces; sessionId++)
			        {
			        	if (skipStreamIn && ((userSettings.getMode(sessionId) == DeviceMode.STREAM_IN.ordinal()) ||
			        			(userSettings.getMode(sessionId) == DeviceMode.WBS_STREAM_IN.ordinal()) ||
			        			(userSettings.getMode(sessionId) == DeviceMode.CHROMA_KEY_STREAM.ordinal())))
			        		continue;
			        	else if (skipPreview && (userSettings.getMode(sessionId) == DeviceMode.PREVIEW.ordinal()))
			        		continue;
			        	if ((userSettings.getMode(sessionId) == DeviceMode.STREAM_OUT.ordinal()) 
			        			&& (userSettings.getUserRequestedStreamState(sessionId) == StreamState.STOPPED))
			            {			
			        		restartStreamsCalled = true;

			        		if (cam_streaming != null)
			        			cam_streaming.stopConfidencePreview(sessionId);
			    	    	
		    	    		try {
		    	    			Thread.sleep(1000);
		    	    		} catch (Exception e) {}
			    	    	
			    	    	// Clear crash flags after stop completes but before start
			    	    	clearErrorFlags();
			    	    	
			        		if (cam_streaming != null)
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
		    		for (int sessionId = 0; sessionId < NumOfSurfaces; sessionId++)
	                {
		    			stopStartLock[sessionId].unlock("restartStreams");
	                }
	        		cameraLock.unlock("restartStreams");
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
    	stopStartLock[sessionId].lock("setDeviceMode");
    	try
		{
		    Log.i(TAG, " setDeviceMode "+ mode);
		    int prevMode = userSettings.getMode(sessionId);
		    userSettings.setMode(mode, sessionId);
		    
		    // If hdmi input driver is present allow all 3 modes, otherwise only allow stream in mode and wbs mode
		    // Only if mode actually changed
		    if ((mode != prevMode) && (hdmiInputDriverPresent || (mode == DeviceMode.STREAM_IN.ordinal()) ||
		    		(mode == DeviceMode.WBS_STREAM_IN.ordinal())))
		    {
		    	StreamState currentStreamState = userSettings.getStreamState(sessionId);
		    	StreamState currentUserReqStreamState = userSettings.getUserRequestedStreamState(sessionId);
		    	// Since this is a user request, mark as stopped requested if mode changes
		        userSettings.setUserRequestedStreamState(StreamState.STOPPED, sessionId);

		        if ( (currentStreamState != StreamState.STOPPED) || (currentUserReqStreamState != StreamState.STOPPED) )
		            hm2.get(prevMode).executeStop(sessionId, true);

		        if (mode == DeviceMode.STREAM_OUT.ordinal())
		        {
		        	// Bug 109256: send bitrate when changing to streamout mode
		        	sockTask.SendDataToAllClients("VBITRATE=" + String.valueOf(userSettings.getBitrate(sessionId)));
		        	
		        	// we want confidence image up for stream out, until streamout is actually started
		        	enableRestartMechanism = true; //enable restart detection
		    		Log.i(TAG, "setDeviceMode: calling updateWindow for sessId="+sessionId);
		        	cam_streaming.startConfidencePreview(sessionId);
		        	restartRequired[sessionId] = true;
		        }
		        
		        // Since we are changing mode, clear out stream url fb only (Bug 103801)
		        if (mode == DeviceMode.STREAM_OUT.ordinal() && !getAutomaticInitiationMode())
		        {
		        	// Only clear if in by receiver or multicast via rtsp, if By transmitter send saved url 
		        	if ( (userSettings.getSessionInitiation(sessionId) == 0) || (userSettings.getSessionInitiation(sessionId) == 2) )			
		        		sockTask.SendDataToAllClients(MiscUtils.stringFormat("STREAMURL%d=", sessionId));
					else if (userSettings.getSessionInitiation(sessionId) == 1)
						sockTask.SendDataToAllClients(MiscUtils.stringFormat("STREAMURL%d=%s", sessionId, userSettings.getStreamOutUrl(sessionId)));
		        }
		        else if (mode == DeviceMode.STREAM_IN.ordinal())
		        {
		        	// By transmitter clear, else send saved url
		        	if (userSettings.getSessionInitiation(sessionId) == 1)
		        		sockTask.SendDataToAllClients(MiscUtils.stringFormat("STREAMURL%d=", sessionId));
		        	else
		        		sockTask.SendDataToAllClients(MiscUtils.stringFormat("STREAMURL%d=%s", sessionId, userSettings.getStreamInUrl(sessionId)));
		        }
		        else if (mode == DeviceMode.WBS_STREAM_IN.ordinal())
		        {
		    		sockTask.SendDataToAllClients(MiscUtils.stringFormat("STREAMURL%d=%s", sessionId, userSettings.getWbsStreamUrl(sessionId)));            	
		        }
		    }
		}
		finally
        {
        	stopStartLock[sessionId].unlock("setDeviceMode");
        }    
    }
    
    public void setNewSink(boolean flag, int sessionId)
    {
        userSettings.setNewSink(flag, sessionId);
    	streamPlay.setNewSink(flag, sessionId);
    }
    
    public void setFieldDebugJni(String cmd, int sessId)
    {
    	if (cmd.contains("LAUNCH_START"))
    	{
    		// Setup window needed for this debug command
    		updateWindow(sessId);
    		showStreamInWindow(sessId);
            invalidateSurface();
    	}
    	
        streamPlay.setFieldDebugJni(cmd, sessId);
    	
    	if (cmd.contains("LAUNCH_STOP"))
    	{
    		// Destroy window setup for this debug command
    		hideStreamInWindow(sessId);
    	}
    }
    
    public void resetAllWindows()
    {
    	for (int sessionId = 0; sessionId < NumOfSurfaces; sessionId++)
    	{
    		userSettings.setXloc(0, sessionId);
    		userSettings.setYloc(0, sessionId);
    		userSettings.setW(0, sessionId);
    		userSettings.setH(0, sessionId);
    		
    		Log.i(TAG, "resetAllWindows: calling updateWindow for sessId="+sessionId);
    		updateWindow(sessionId);
    	}
    }

    public void setWbsLogLevel(int logLevel)
    {
    	if (wbsStream != null)
    	{
    		wbsStream.setLogLevel(logLevel);
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
    
    public void setStretchVideo(int stretch, int sessionId)
    {
    	if (userSettings.getStretchVideo(sessionId) != stretch)
    	{
    		userSettings.setStretchVideo(stretch, sessionId);
    		
			// TODO look at combining textureview and mVideoDimensions when AirMedia is merged in
    		updateWindowWithVideoSize(sessionId, dispSurface.getUseTextureView(sessionId), 
    				mVideoDimensions[sessionId].videoWidth, mVideoDimensions[sessionId].videoHeight);
    	}
    }
    
    public void setWindowDimensions(int x, int y, int width, int height, int sessionId, boolean use_texture)
    {
    	userSettings.setXloc(x, sessionId);
    	userSettings.setYloc(y, sessionId);
    	userSettings.setW(width, sessionId);
    	userSettings.setH(height, sessionId);
    	Log.i(TAG, "setWindowDimensions - calling updateWindow for sessId="+sessionId+
    			" "+width+"x"+height+"@"+x+","+y+"   useTexture="+use_texture);
    	updateWindow(sessionId, use_texture);
    }

    public void setWindowDimensions(int x, int y, int width, int height, int sessionId)
    {
    	if (userSettings.getMode(sessionId) == DeviceMode.WBS_STREAM_IN.ordinal())
    	{
    		setWindowDimensions(x, y, width, height, sessionId, wbsStream.useSurfaceTexture);
    	} else {
    		setWindowDimensions(x, y, width, height, sessionId, false);
    	}
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

    public Point getWindowSize(int sessionId)
    {
    	Point size = new Point(userSettings.getW(sessionId), userSettings.getH(sessionId));
    	if (size.x == 0 && size.y == 0)
    	{
    		size = getDisplaySize();
    	}
    	return size;
    }
    
    private Integer[][] createZOrderArray()
    {
    	// Index 0 is the sessionId value, Index 1 is the relative Z order saved in userSettings
    	Integer[][] zOrder = new Integer[2][CresStreamCtrl.NumOfSurfaces];
    	
    	for (int sessionId = 0; sessionId < CresStreamCtrl.NumOfSurfaces; sessionId++)
    	{
    		zOrder[0][sessionId] = sessionId; 
    		zOrder[1][sessionId] = userSettings.getZ(sessionId);
    	}
    	
    	return zOrder;
    }
    
    // passing in Integer[][] acts like pass by reference, this is desired in this case
    private void sortZOrderArray(Integer[][] zOrder)
    {
    	//bubble ascending sort, we want zorders to be listed so that lower z order has lower index
        for (int outerIndex = 0; outerIndex < CresStreamCtrl.NumOfSurfaces; outerIndex++)
        {
            for (int innerIndex = (outerIndex + 1); innerIndex < CresStreamCtrl.NumOfSurfaces; innerIndex++)
            {
                if (zOrder[1][outerIndex] > zOrder[1][innerIndex])
                {
                    int[] temp = new int[2];
                    temp[0] = zOrder[0][outerIndex];
                    temp[1] = zOrder[1][outerIndex];
                    
                    //Swap innerIndex to outerIndex position
                    zOrder[0][outerIndex] = zOrder[0][innerIndex];
                    zOrder[1][outerIndex] = zOrder[1][innerIndex];
          
                    zOrder[0][innerIndex] = temp[0];
                    zOrder[1][innerIndex] = temp[1];
                }
            }
        }   
    }
    
    private boolean didZOrderChange(Integer[][] zOrderOld, Integer[][] zOrderNew)
    {
    	boolean updated = false;
    	for (int i = 0; i < CresStreamCtrl.NumOfSurfaces; i++)
        {
    		// Check that order of sessionIds match
    		if (zOrderOld[0][i] != zOrderNew[0][i])
    		{
    			updated = true;
    			break;
    		}
        }
    	
    	return updated;
    }
    
    private boolean doWindowsOverlap()
    {
    	// TODO: this needs to be updated when we have more than 2 windows
    	int surface1xLeft 	= userSettings.getXloc(0);
    	int surface1xRight 	= surface1xLeft + userSettings.getW(0);
    	int surface1yTop 	= userSettings.getYloc(0);
    	int surface1yBottom	= surface1yTop + userSettings.getH(0);
    	
    	int surface2xLeft 	= userSettings.getXloc(1);
    	int surface2xRight 	= surface2xLeft + userSettings.getW(1);
    	int surface2yTop 	= userSettings.getYloc(1);
    	int surface2yBottom	= surface2yTop + userSettings.getH(1);
    	
    	return MiscUtils.rectanglesOverlap(surface1xLeft, surface1xRight, surface1yTop, surface1yBottom, surface2xLeft, surface2xRight, surface2yTop, surface2yBottom);
    }
    
    public void setWindowSizeZ(int z, int sessionId)
    {
    	if (userSettings.getZ(sessionId) != z)
    	{
    		// Determine current zOrder before change
    		final Integer[][] zOrderOld = createZOrderArray();
	    	sortZOrderArray(zOrderOld);
	    	
    		userSettings.setZ(z, sessionId);
    		
    		// Determine whether zorder update is necessary
	    	boolean overlap = doWindowsOverlap();
	
	    	// Find zOrder after update
	    	// Index 0 is the sessionId value, Index 1 is the relative Z order saved in userSettings
	    	final Integer[][] zOrder = createZOrderArray();
	    	sortZOrderArray(zOrder);
	    	
	    	boolean zOrderUpdated = didZOrderChange(zOrderOld, zOrder);
	    	
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
	            Log.i(TAG, "Restarting Streams - z order change");
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
        
    private void updateWH(final int sessionId)
    {
    	if (dispSurface != null)
    	{
    		dispSurface.updateWH(sessionId);
    	}
    }
    
    public void updateWindowWithVideoSize(int sessionId, boolean use_texture_view, int videoWidth, int videoHeight)
    {
    	mVideoDimensions[sessionId].videoWidth = videoWidth;
    	mVideoDimensions[sessionId].videoHeight = videoHeight;
    	if (dispSurface != null)
    	{
    		dispSurface.updateWindowWithVideoSize(sessionId, use_texture_view, videoWidth, videoHeight);
    	}
    }
    
    public void updateWindow(final int sessionId)
    {
    	updateWindow(sessionId, false);
    }
    
    public void updateWindow(final int sessionId, boolean use_texture)
    {
    	if (dispSurface != null)
    	{
    		dispSurface.updateWindow(sessionId, use_texture);
    	}
    }
    
    public void invalidateSurface()
    {
    	if (dispSurface != null)
    	{
    		dispSurface.invalidateSurface();
    	}
    }

    private void updateXY(final int sessionId)
    {
    	if (dispSurface != null)
    	{
			dispSurface.updateXY(sessionId);
    	}
    }
    
    public void SetWindowManagerResolution(final int w, final int h, final boolean haveExternalDisplay)
    {
    	if (dispSurface != null)
        {
			// will be run on UI thread
    		dispSurface.setWindowManagerResolution(w, h, haveExternalDisplay);		
        }
    }
    
    void refreshInputResolution()
    {
    	Log.i(TAG, "Refresh resolution info");
    	if (hdmiInputDriverPresent)
    	{
    		hdmiInput.updateResolutionInfo();
    	}
    }

	public void refreshOutputResolution() {
		ProductSpecific.DispayInfo hdmiOutputResolution = mProductSpecific.new DispayInfo();
		WindowManager wm = null;

		//HDMI Out		
		if (haveExternalDisplays)
		{
			DisplayManager dm = (DisplayManager) getApplicationContext().getSystemService(Context.DISPLAY_SERVICE);
			if (dm != null){
				Display dispArray[] = dm.getDisplays();
				if (dispArray.length>1){
					Context displayContext = getApplicationContext().createDisplayContext(dispArray[1]);	// querying display 1
					wm = (WindowManager)displayContext.getSystemService(Context.WINDOW_SERVICE);
				}
			}

			Point size = new Point(0,0);
			if (wm == null)
			{
				Log.e(TAG, "Unable to find second display - setting resolution to 0x0@0");
				hdmiOutputResolution.refreshRate = 0;
			}
			else
			{
				Display display = wm.getDefaultDisplay();
				display.getSize(size);
				hdmiOutputResolution.refreshRate = display.getRefreshRate();
			}

			hdmiOutputResolution.width = size.x;
			hdmiOutputResolution.height = size.y;
		}
		else
		{			
			wm = (WindowManager) this.getSystemService(Context.WINDOW_SERVICE);
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
	        // Set window manager to reflect this resolution
	        if (hdmiOutputResolution.width == 0 && hdmiOutputResolution.height == 0)
		        SetWindowManagerResolution(1920, 1080, haveExternalDisplays);
	        else
	        	SetWindowManagerResolution(hdmiOutputResolution.width, hdmiOutputResolution.height, haveExternalDisplays);
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
        
           // Check if start was filtered out and then start if true
        if (streamingReadyLatch.getCount() == 0 && enableRestartMechanism && Boolean.parseBoolean(hdmiOutput.getSyncStatus()) == true)
        {
        	for (int sessionId = 0; sessionId < NumOfSurfaces; sessionId++)
        	{
        		if (userSettings.getUserRequestedStreamState(sessionId) == StreamState.STARTED)
        		{
        			Log.i(TAG, "HDMI output connected, attempting to start stream " + sessionId);
        			Start(sessionId);
        		}
        	}
        }
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
    
    // volume 0 - 100%
    public void setSystemVolume(int volume)
    {    	
    	// Stream Out preview audio will be placed on the unused ALARM stream
    	amanager.setStreamVolume(AudioManager.STREAM_ALARM, volume * amanager.getStreamMaxVolume(AudioManager.STREAM_ALARM) / 100, 0);
    }
    
    public void setStreamMusicMute(boolean enabled)
    {    
    	amanager.setStreamMute(AudioManager.STREAM_MUSIC, enabled);
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

    public void setAutomaticInitiationMode(boolean value)
    {
		mAutoInitMode = value;
    }

    public boolean getAutomaticInitiationMode()
    {
		return mAutoInitMode;
    }

    public void setAutomaticInitiationModeEnabled(boolean value)
    {
		setAutomaticInitiationMode(value); 
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
	    sockTask.SendDataToAllClients(MiscUtils.stringFormat("STREAMIN_HORIZONTAL_RES_FB%d=%s", source, width));
	    sockTask.SendDataToAllClients(MiscUtils.stringFormat("STREAMIN_VERTICAL_RES_FB%d=%s", source, height));
	    sockTask.SendDataToAllClients(MiscUtils.stringFormat("STREAMIN_FPS_FB%d=%s", source, framerate));
	    sockTask.SendDataToAllClients(MiscUtils.stringFormat("STREAMIN_ASPECT_RATIO%d=%s", source, MiscUtils.calculateAspectRatio(width, height)));
	    sockTask.SendDataToAllClients(MiscUtils.stringFormat("VENCPROFILE%d=%s", source, profile));

	    sockTask.SendDataToAllClients("STREAMIN_AUDIO_FORMAT=" + String.valueOf(streamPlay.getMediaPlayerAudioFormatFb()));
	    sockTask.SendDataToAllClients("STREAMIN_AUDIO_CHANNELS=" + String.valueOf(streamPlay.getMediaPlayerAudiochannelsFb()));
    }

    public void SendStreamInFeedbacks()
    {
    	if(userSettings.isStatisticsEnable(0))
    	{
    		sockTask.SendDataToAllClients("STATISTICS_NUMBEROFVIDEOPACKETS=" + MiscUtils.asUnsignedDecimalString(streamPlay.getStreamInNumVideoPackets()));
    	   	sockTask.SendDataToAllClients("STATISTICS_NUMBEROFVIDEOPACKETSDROPPED=" + String.valueOf(streamPlay.getStreamInNumVideoPacketsDropped()));
    	   	sockTask.SendDataToAllClients("STATISTICS_NUMBEROFAUDIOPACKETS=" + MiscUtils.asUnsignedDecimalString(streamPlay.getStreamInNumAudioPackets()));
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
    	
		sockTask.SendDataToAllClients("STATISTICS_NUMBEROFVIDEOPACKETS=" + MiscUtils.asUnsignedDecimalString(cam_streaming.getStreamOutNumVideoPackets()));
    	sockTask.SendDataToAllClients("STATISTICS_NUMBEROFVIDEOPACKETSDROPPED=" + String.valueOf(cam_streaming.getStreamOutNumVideoPacketsDropped()));
	   	sockTask.SendDataToAllClients("STATISTICS_NUMBEROFAUDIOPACKETS=" + MiscUtils.asUnsignedDecimalString(cam_streaming.getStreamOutNumAudioPackets()));
    	sockTask.SendDataToAllClients("STATISTICS_NUMBEROFAUDIOPACKETSDROPPED=" + String.valueOf(cam_streaming.getStreamOutNumAudioPacketsDropped()));
    }
    
    public StreamState getCurrentStreamState(int sessionId)
    {
    	StreamState returnStreamState;
    	streamStateLock[sessionId].lock("getCurrentStreamState");
    	try
    	{
    		returnStreamState = userSettings.getStreamState(sessionId);
        	Log.i(TAG, "getCurrentStreamState(): StreamState for sessionId " + sessionId + " is " + returnStreamState);
    	}
    	finally 
    	{
        	streamStateLock[sessionId].unlock("getCurrentStreamState");
    	}
    	return returnStreamState;
    }
    
    public void setCurrentStreamState(StreamState state, int sessionId)
    {
    	streamStateLock[sessionId].lock("setCurrentStreamState");
    	try
    	{
        	userSettings.setStreamState(state, sessionId);
        	Log.i(TAG, "setStreamState(): StreamState for sessionId " + sessionId + " is " + state);
    	}
    	finally 
    	{
        	streamStateLock[sessionId].unlock("setCurrentStreamState");
    	}
    }
    
    public void SendStreamState(StreamState state, int sessionId)
    {
    	streamStateLock[sessionId].lock("sendCurrentStreamState");
    	try
    	{
        	userSettings.setStreamState(state, sessionId);
        	Log.i(TAG, "sendStreamState(): StreamState for sessionId " + sessionId + " is " + state);
        	CresStreamCtrl.saveSettingsUpdateArrived = true; // flag userSettings to save
	        StringBuilder sb = new StringBuilder(512);
	        String streamStateText = "STREAMSTATE" + String.valueOf(sessionId);
	        
	        sb.append(streamStateText + "=").append(state.getValue());
	        sockTask.SendDataToAllClients(sb.toString());
    	}
        finally 
    	{
        	streamStateLock[sessionId].unlock("sendCurrentStreamState");
    	}
    }
    
    public void setForceRgbPreviewMode(boolean enable)
    {
    	int sessionId = 0;	// This is debug, assume always id 0
    	if (enable)
    	{
    		// Start
    		Log.d(TAG, "Starting debug RGB preview mode");    		
    		setSurfaceViewTag(sessionId, "PreviewVideoLayer");
	    	updateWindow(sessionId);
	    	showPreviewWindow(sessionId);
	    	cam_preview.setSessionIndex(sessionId);
	    	cam_preview.startPlayback(false, true);
    	}
    	else
    	{
    		// Stop
    		Log.d(TAG, "Stopping debug RGB preview mode");
    		hidePreviewWindow(sessionId);
        	cam_preview.setSessionIndex(sessionId);
        	cam_preview.stopPlayback(false, true);
        	setSurfaceViewTag(sessionId, "VideoLayer");
    	}
    }

    //Ctrls
    public void Start(int sessionId)
    {
    	boolean usingCamera = false;
    	if (userSettings.getMode(sessionId) == DeviceMode.STREAM_OUT.ordinal() || userSettings.getMode(sessionId) == DeviceMode.PREVIEW.ordinal())
    		usingCamera= true;
    	if (usingCamera)
    	{
    		cameraLock.lock("Start");
    	}
    	try
    	{
    		stopStartLock[sessionId].lock("Start");
    		try
    		{	
    			// For preview mode we need to set layer mark for HWC
    			if (isRGB888HDMIVideoSupported)
    			{
    				if (userSettings.getMode(sessionId) == DeviceMode.PREVIEW.ordinal())
    					setSurfaceViewTag(sessionId, "PreviewVideoLayer");
    				else
    					setSurfaceViewTag(sessionId, "VideoLayer");
    			}

    			if (userSettings.getAirMediaLaunch(sessionId)) {
    				// If we are starting streaming shutoff air media
    				launchAirMedia(false, sessionId, false);
    			}

    			// This needs to be set to true even if we filter out the start
    			enableRestartMechanism = true; //if user starts stream allow restart mechanism

    			// Check if HDMI out is connected before starting stream in or preview, Android gets in bad state otherwise
    			// Start will be handled by restart streams when HDMI output returns
    			if ( mIsHdmiOutExternal ||
    					Boolean.parseBoolean(hdmiOutput.getSyncStatus()) ||
    					(userSettings.getMode(sessionId) == DeviceMode.STREAM_OUT.ordinal()) )
    			{
    				ProductSpecific.doChromakey(true);

    				StreamState curStreamState = getCurrentStreamState(sessionId);
    				if ((curStreamState != StreamState.STARTED) && (curStreamState != StreamState.STREAMERREADY))
    				{	   
    					playStatus="true";
    					stopStatus="false";
    					pauseStatus="false";	
    					restartRequired[sessionId]=true;
    					if (hm != null)
    					{
    						hm.get(userSettings.getMode(sessionId)).executeStart(sessionId);
    					}
    					else
    					{
    						Log.w(TAG, "Start(): hashmap is null - ignoring start for sessionId="+sessionId);
    					}
    			    	m_InPause[sessionId] = false;
    					//hm.get(device_mode).executeStart();
    					// The started state goes back when we actually start

    				}
    				else if (curStreamState == StreamState.STARTED)
    				{
    	    			Log.i(TAG, "Sessionid "+sessionId+" already started in mode "+userSettings.getMode(sessionId));
    					SendStreamState(StreamState.STARTED, sessionId);
    				}
    			}
    			else
    				Log.i(TAG, "Filtering out start because hdmi output not connected");
    		}
    		finally
    		{
    			stopStartLock[sessionId].unlock("Start");
    		}
    	}
    	finally 
    	{
    		if (usingCamera)
    		{
    			cameraLock.unlock("Start");
    		}
    	}
    }

    public void Stop(int sessionId, boolean fullStop)
    {
    	boolean usingCamera = false;
    	if (userSettings.getMode(sessionId) == DeviceMode.STREAM_OUT.ordinal() || userSettings.getMode(sessionId) == DeviceMode.PREVIEW.ordinal())
    		usingCamera= true;
    	if (usingCamera)
    	{
    		cameraLock.lock("Stop");
    	}
    	try
    	{
    		stopStartLock[sessionId].lock("Stop");
    		try
    		{
    			//csio will send service full stop when it does not want confidence mode started
    			StreamState curStreamState = getCurrentStreamState(sessionId);
    			if ((curStreamState != StreamState.CONFIDENCEMODE) || (fullStop == true))
    			{
    				playStatus="false";
    				stopStatus="true";
    				pauseStatus="false";
    				restartRequired[sessionId]=false;
    				hm2.get(userSettings.getMode(sessionId)).executeStop(sessionId, fullStop);
    		    	m_InPause[sessionId] = false;
    				// device state will be set in stop callback
    			}
    			else
    			{
    				if (curStreamState == StreamState.CONFIDENCEMODE)
    					SendStreamState(StreamState.CONFIDENCEMODE, sessionId);
    				else
    					SendStreamState(StreamState.STOPPED, sessionId);
    			}
    			ProductSpecific.doChromakey(false);
    		}
    		finally
    		{
    			stopStartLock[sessionId].unlock("Stop");
    		}
    	}
    	finally 
    	{
    		if (usingCamera)
    		{
    			cameraLock.unlock("Stop");
    		}
    	}
    }

    public void Pause(int sessionId)
    {    	
    	stopStartLock[sessionId].lock("Pause");
    	try
    	{
    		StreamState curStreamState = getCurrentStreamState(sessionId);
    		if ((curStreamState != StreamState.PAUSED) && (curStreamState != StreamState.STOPPED))
    		{
    			pauseStatus="true";
    			playStatus="false";
    			stopStatus="false";
    			restartRequired[sessionId]=false;
    			hm3.get(userSettings.getMode(sessionId)).executePause(sessionId);
    	    	m_InPause[sessionId] = true;
    			// Device state will be set in pause callback
    		}
    	}
    	finally
    	{
    		stopStartLock[sessionId].unlock("Pause");
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
    	sockTask.SendDataToAllClients(MiscUtils.stringFormat("MULTICAST_ADDRESS%d=%s", sessId, ip));
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
            Log.i(TAG, "URL is "+url.toString());
        }else {
			if (getAutomaticInitiationMode()) {
				url.append(userSettings.getStreamServerUrl(sessId));
			} else {
				url.append(userSettings.getStreamOutUrl(sessId));
			}
			Log.i(TAG, "createStreamOut: URL="+url.toString());
		}

	
        return url.toString();
    }

    public void startStreamOut(int sessId)
    {
    	if (cam_streaming == null)
    		return;
    	
    	StringBuilder sb = new StringBuilder(512);

    	lastHDMImode = DeviceMode.STREAM_OUT;

    	SendStreamState(StreamState.CONNECTING, sessId);

    	// we are starting to streamout so stop confidence preview (unless resuming from pause)
    	if (cam_streaming.getConfidencePreviewStatus() == true)
    		cam_streaming.stopConfidencePreview(sessId);

    	Log.i(TAG, "startStreamOut: calling updateWindow for sessId="+sessId);
        if (!m_InPause[sessId])
        	updateWindow(sessId);
        else
		    updateWindowWithVideoSize(sessId, false, mVideoDimensions[sessId].videoWidth, mVideoDimensions[sessId].videoHeight);
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
    	Log.i(TAG, "Sending STREAMURL=" + out_url);
    	sockTask.SendDataToAllClients(sb.toString());  
    }

    public void stopStreamOut(int sessId, boolean fullStop)
    {
    	if (cam_streaming == null)
    		return;
    	
    	lastHDMImode = DeviceMode.PREVIEW;

    	// If in streamout and in By Reciever(0) or Multicast RTSP (2) clear url on stop- Bug 103801
    	if ( !getAutomaticInitiationMode() && (userSettings.getSessionInitiation(sessId) == 0) || (userSettings.getSessionInitiation(sessId) == 2) )
    		sockTask.SendDataToAllClients(MiscUtils.stringFormat("STREAMURL%d=", sessId));

    	cam_streaming.setSessionIndex(sessId);
    	if (cam_streaming.getConfidencePreviewStatus() == true)
    		cam_streaming.stopConfidencePreview(sessId);
    	else
    		cam_streaming.stopRecording(false);
    	StreamOutstarted = false;

    	// Do NOT hide window if being used by AirMedia
    	if ( !(mUsedForAirMedia[sessId]) )
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
    	else if (fullStop)
    	{
    	    SendStreamState(StreamState.STOPPED, sessId);
    	}
	}
    
    public void pauseStreamOut(int sessId)
    {
    	cam_streaming.setSessionIndex(sessId);
    	cam_streaming.pausePlayback();
    }
    
    private void hideWindow (final int sessId)
    {
    	// Reset video dimensions on hide
    	mVideoDimensions[sessId].videoWidth = 0;
    	mVideoDimensions[sessId].videoHeight = 0;
    	
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
            		if (latch.await(30, TimeUnit.SECONDS) == false)
            		{
            			Log.e(TAG, "hideWindow: timeout after 30 seconds");
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
            		if (latch.await(30, TimeUnit.SECONDS) == false)
            		{
            			Log.e(TAG, "showWindow: Timeout after 30 seconds");
            			RecoverTxrxService();
            		}
            	}
            	catch (InterruptedException ex) { ex.printStackTrace(); }  
	    	}
	    	else
	    		dispSurface.ShowWindow(sessId);
    	}
    }
    
    private void hideTextureWindow (final int sessId)
    {
    	// Reset video dimensions on hide
    	mVideoDimensions[sessId].videoWidth = 0;
    	mVideoDimensions[sessId].videoHeight = 0;
    	
    	if (dispSurface != null)
    	{
	    	// Make sure surface changes are only done in UI (main) thread
	    	if (Looper.myLooper() != Looper.getMainLooper())
	    	{
		    	final CountDownLatch latch = new CountDownLatch(1);
		    	runOnUiThread(new Runnable() {
				     @Override
				     public void run() {			    	 
			    		 dispSurface.HideTextureWindow(sessId);
				    	 latch.countDown();
				     }
		    	});
		    	try { 
            		if (latch.await(30, TimeUnit.SECONDS) == false)
            		{
            			Log.e(TAG, "hideWindow: timeout after 30 seconds");
            			RecoverTxrxService();
            		}
            	}
            	catch (InterruptedException ex) { ex.printStackTrace(); }  
	    	}
	    	else
	    		dispSurface.HideTextureWindow(sessId);
    	}  
    }
    
    private void showTextureWindow (final int sessId)
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
			    		 dispSurface.ShowTextureWindow(sessId);
				    	 latch.countDown();
				     }
		    	});
		    	try { 
            		if (latch.await(30, TimeUnit.SECONDS) == false)
            		{
            			Log.e(TAG, "showWindow: Timeout after 30 seconds");
            			RecoverTxrxService();
            		}
            	}
            	catch (InterruptedException ex) { ex.printStackTrace(); }  
	    	}
	    	else
	    		dispSurface.ShowTextureWindow(sessId);
    	}
    }
    
    public void showSplashtopWindow(int sessId, boolean use_texture)
    {
		if (serviceMode == ServiceMode.Slave)
			return;
    	Log.i(TAG, "Splashtop: Window showing");
    	if (use_texture)
    	{
    		showTextureWindow(sessId);
    	} else {
    		showWindow(sessId);
    	}
    }
    
    public void hideSplashtopWindow(int sessId, boolean use_texture)
    {
		if (serviceMode == ServiceMode.Slave)
			return;
    	Log.i(TAG, "Splashtop: Window hidden");
    	if (use_texture)
    	{
    		hideTextureWindow(sessId);
    	} else {
    		hideWindow(sessId);
    	}
    }
    
    public void hidePreviewWindow(int sessId)
    {
		if (serviceMode == ServiceMode.Slave)
			return;
    	Log.i(TAG, "Preview Window hidden");
    	hideWindow(sessId);
    }
    
    public void showPreviewWindow(int sessId)
    {
		if (serviceMode == ServiceMode.Slave)
			return;
        Log.i(TAG, "Preview Window showing");
        showWindow(sessId);
    }

    //StreamIn Ctrls & Config
    public void hideStreamInWindow(int sessId)
    {
		if (serviceMode == ServiceMode.Slave)
			return;
        Log.i(TAG, " streamin Window hidden " + sessId);
        hideWindow(sessId);
    }

    private void showStreamInWindow(int sessId)
    {
		if (serviceMode == ServiceMode.Slave)
			return;
        Log.i(TAG, "streamin Window  showing " + sessId);
        showWindow(sessId);
    }

    public void showWbsWindow(int sessId)
    {
		if (serviceMode == ServiceMode.Slave)
			return;
    	Log.i(TAG, "Wbs Window showing");
    	if (wbsStream.useSurfaceTexture)
    	{
    		showTextureWindow(sessId);
    	} else {
    		showWindow(sessId);
    	}
    }
    
    public void hideWbsWindow(int sessId)
    {
		if (serviceMode == ServiceMode.Slave)
			return;
    	Log.i(TAG, "Wbs Window hidden");
    	if (wbsStream.useSurfaceTexture)
    	{
    		hideTextureWindow(sessId);
    	} else {
    		hideWindow(sessId);
    	}
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
    	if (getAutomaticInitiationMode())
    		userSettings.setStreamServerUrl(ap_url, sessionId);

    	if(ap_url.startsWith("rtp://@"))
    		streamPlay.setRtpOnlyMode( userSettings.getRtpVideoPort(sessionId),  userSettings.getRtpAudioPort(sessionId), userSettings.getDeviceIp(), sessionId);
    	else if(ap_url.startsWith("http://"))
    		streamPlay.disableLatency(sessionId);
    	else
    		Log.i(TAG, "No conditional Tags for StreamIn");
    	sockTask.SendDataToAllClients(MiscUtils.stringFormat("STREAMURL%d=%s", sessionId, ap_url));
    }
    
    public void setStreamOutUrl(String ap_url, int sessionId)
    {
    	userSettings.setStreamOutUrl(ap_url, sessionId);
	if (getAutomaticInitiationMode())
	    userSettings.setStreamServerUrl(ap_url, sessionId);
    	
    	if (userSettings.getMode(sessionId) == DeviceMode.STREAM_OUT.ordinal())
    		sockTask.SendDataToAllClients(MiscUtils.stringFormat("STREAMURL%d=%s", sessionId, createStreamOutURL(sessionId)));
    }
    
    public void setWbsStreamUrl(String url, int sessionId)
    {
        Log.i(TAG, "setWbsStreamUrl invoked with url=" + url);
        userSettings.setWbsStreamUrl(url, sessionId);
        if (wbsStream != null)
        {
        		wbsStream.setUrl(url, 0);
        }
        Log.i(TAG, "WBS Stream URL = " + url);
    	sockTask.SendDataToAllClients(MiscUtils.stringFormat("STREAMURL%d=%s", sessionId, url));
    	sockTask.SendDataToAllClients(MiscUtils.stringFormat("WBS_STREAMING_STREAM_URL=%s", url));
    }
    
    public void updateStreamOutUrl_OnIPChange()
    {
    	for(int sessionId = 0; sessionId < NumOfSurfaces; sessionId++)
    	{
        	if (userSettings.getMode(sessionId) == DeviceMode.STREAM_OUT.ordinal())
        		sockTask.SendDataToAllClients(MiscUtils.stringFormat("STREAMURL%d=%s", sessionId, createStreamOutURL(sessionId)));
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
    	if (userSettings.getMode(sessId) == DeviceMode.WBS_STREAM_IN.ordinal())
    		return userSettings.getWbsStreamUrl(sessId);
    	else if (userSettings.getMode(sessId) == DeviceMode.STREAM_IN.ordinal())
    		return userSettings.getStreamInUrl(sessId);
    	else
    		return "";
    }

    public void startStreamIn(int sessId)
    {
		Log.i(TAG, "startStreamIn: sessId="+sessId);
		if (!m_InPause[sessId])
			updateWindow(sessId);
		else
		    updateWindowWithVideoSize(sessId, false, mVideoDimensions[sessId].videoWidth, mVideoDimensions[sessId].videoHeight);
        showStreamInWindow(sessId);
        invalidateSurface();
        streamPlay.onStart(sessId);
        //Toast.makeText(this, "StreamIN Started", Toast.LENGTH_LONG).show();
    }

    public void stopStreamIn(int sessId)
    {
    	//hide video window first
    	//if (hideVideoOnStop)
    	//	hideWindowWithoutDestroy(sessId);

    	streamPlay.onStop(sessId);   

		// Do NOT hide window if being used by AirMedia
    	if ( !(mUsedForAirMedia[sessId]) )
    		hideStreamInWindow(sessId);
    }

    public void pauseStreamIn(int sessId)
    {
        streamPlay.onPause(sessId);
    }

    public void startWbsStream(int sessId)
    {
		Log.i(TAG, "startWbsStream: sessId="+sessId);
		if (!m_InPause[sessId])
			updateWindow(sessId, wbsStream.useSurfaceTexture);
		else
		    updateWindowWithVideoSize(sessId, wbsStream.useSurfaceTexture, mVideoDimensions[sessId].videoWidth, mVideoDimensions[sessId].videoHeight);
        showWbsWindow(sessId);
        invalidateSurface();
        wbsStream.onStart(sessId);
    }

    public void stopWbsStream(int sessId)
    {
		wbsStream.onStop(sessId);   

		// Do NOT hide window if being used by AirMedia
    	if ( !(mUsedForAirMedia[sessId]) )
    		hideWbsWindow(sessId);
    }

    public void pauseWbsStream(int sessId)
    {
    	wbsStream.onPause(sessId);
    }

    // fillSurface
    public void fillSurface(int sessId, int color)
    {
		Surface s = dispSurface.getSurface(sessId);
		if (s == null || !s.isValid())
		{
			Log.e(TAG, "fillSurface(): invalid or null surface");
			return;
		}
		Canvas canvas = null;
		try {
			canvas = s.lockCanvas(null);
		} catch (Exception e)
		{
			Log.e(TAG, "fillSurface(): Exception encountered trying to lock Canvas for rendering");
			e.printStackTrace();
		}
		canvas.drawRGB(Color.red(color), Color.green(color), Color.blue(color));
		s.unlockCanvasAndPost(canvas);
    }
    
    // For testing only - debugging
    public void wfdStreamCommand(String msg, int sessId)
    {
    	String[] args = msg.split(",");
    	
    	if (args.length > 0)
    	{
    		if (args[0].equalsIgnoreCase("start"))
    		{
    			if (args.length < 3)
    			{
    				Log.i(TAG, "wfdStreamCommand: command should be 'start,url,rtsp_port'");
    				return;
    			}
    			int rtsp_port = 0;
    			try {
    				rtsp_port = Integer.parseInt(args[2]);
    			} catch (Exception e) {}
    			updateWindow(sessId, false);
    			showWindow(sessId);
    	        invalidateSurface();
    			startWfdStream(sessId, (long) 1, args[1], rtsp_port);
    		} 
    		else if (args[0].equalsIgnoreCase("stop"))
    		{
    			stopWfdStream(sessId, (long) 1);
				hideWindow(sessId); 
    		}
    		else
    		{
    			Log.e(TAG, "wfdStreamCommand: Invalid command="+args[0]);
    		}
    	}
    }
    
    public void startWfdStream(int streamId, long sessionId, String url, int rtsp_port)
    {
		Log.i(TAG, "startWfdStream: streamId="+streamId+"   sessionId="+sessionId+"   url="+url+"   rtspPort="+rtsp_port);
        streamPlay.wfdStart(streamId, sessionId, url, rtsp_port);
    }

    public void stopWfdStream(int streamId, long sessionId)
    {
		Log.i(TAG, "stopWfdStream: streamId="+streamId+" sessionId="+sessionId);
		streamPlay.wfdStop(streamId, sessionId); 
    }
    
    // Start chroma key
    public void startChromaKeyStream(int sessId)
    {
		Log.i(TAG, "startChromaKeyStream: sessId="+sessId);
		if (serviceMode != ServiceMode.Slave)
			return;
		int chromaKeyColor = userSettings.getChromaKeyColor();
		fillSurface(sessId, chromaKeyColor);
		SendStreamState(StreamState.STARTED, sessId);
    }

    public void stopChromaKeyStream(int sessId)
    {
		Log.i(TAG, "stopChromaKeyStream: sessId="+sessId);
		if (serviceMode != ServiceMode.Slave)
			return;
		fillSurface(sessId, 0);
		SendStreamState(StreamState.STOPPED, sessId);
    }

    public void pauseChromaKeyStream(int sessId)
    {
		Log.i(TAG, "pauseChromaKeyStream: sessId="+sessId);
    }
    
    //Start gstreamer Preview
    public void startGstPreview(int sessId)
    {
    	if (gstStreamOut != null)
    	{
    		lastHDMImode = DeviceMode.PREVIEW;
    		SendStreamState(StreamState.CONNECTING, sessId);
    		Log.i(TAG, "startGstPreview: sessId="+sessId);
    		updateWindow(sessId);
    		showPreviewWindow(sessId);
    		invalidateSurface();

    		gstStreamOut.setSessionIndex(sessId);
    		gstStreamOut.startPreview(getSurface(sessId), sessId);
    	}
    }

  //Start native Preview
    public void startNativePreview(int sessId)
    {
    	if (cam_preview != null)
    	{
    		SendStreamState(StreamState.CONNECTING, sessId);
    		Log.i(TAG, "startNativePreview: sessId="+sessId);
	        if (!m_InPause[sessId])
	        	updateWindow(sessId);
	        else
			    updateWindowWithVideoSize(sessId, false, mVideoDimensions[sessId].videoWidth, mVideoDimensions[sessId].videoHeight);
    		showPreviewWindow(sessId);
    		cam_preview.setSessionIndex(sessId);
    		invalidateSurface();

    		cam_preview.startPlayback(false);
    		//Toast.makeText(this, "Preview Started", Toast.LENGTH_LONG).show();
    	}
    }

    public void startPreview(int sessId)
    {
    	if(ProductSpecific.hasRealCamera())
		{
			startGstPreview(sessId);
		}
    	else
    		startNativePreview(sessId);
    }

    //Stop gstreamer Preview
    public void stopGstPreview(int sessId, boolean hide)
    {
	    Log.i(TAG, "stopGstPreview() sessId = " + sessId + ", hide = " + hide);
	    if (gstStreamOut != null) {
	    	if (serviceMode != ServiceMode.Slave)
	    	{
	    		if ( hide ) {
	    			Log.i(TAG, "Hide Preview Window first to enhance the user experience when quickly switching to another UI");

	    			// Do NOT hide window if being used by AirMedia
	    			if ( !(mUsedForAirMedia[sessId]) ) {
	    				hidePreviewWindow(sessId);
	    			}	
	    		}
	    	}
	    	// so these are contiuously running in background.
	    	gstStreamOut.stopPreview(sessId);
	    	gstStreamOut.waitForPreviewClosed(sessId,5);

	    	//On STOP, there is a chance to get ducati crash which does not save current state
	    	//causes streaming never stops.
	    	//FIXME:Temp Hack for ducati crash to save current state
	    	setCurrentStreamState(StreamState.STOPPED, sessId);
	    }
    }
    
    //Stop native Preview
    public void stopNativePreview(int sessId, boolean hide)
    {
    	if (cam_preview != null)
    	{
    		cam_preview.setSessionIndex(sessId);

	    	if (serviceMode != ServiceMode.Slave)
	    	{
	    		if(hide)
	    		{
	    			// Do NOT hide window if being used by AirMedia
	    			if ( !(mUsedForAirMedia[sessId]) )
	    				hidePreviewWindow(sessId);
	    		}
	    	}

    		//On STOP, there is a chance to get ducati crash which does not save current state
    		//causes streaming never stops.
    		//FIXME:Temp Hack for ducati crash to save current state
    		setCurrentStreamState(StreamState.STOPPED, sessId);
    		cam_preview.stopPlayback(false);
    		//Toast.makeText(this, "Preview Stopped", Toast.LENGTH_LONG).show();
    	}
    }

    public void stopPreview(int sessId, boolean hide)
    {
    	if(ProductSpecific.hasRealCamera())
		{
			stopGstPreview(sessId, hide);
		}
    	else
    		stopNativePreview(sessId, hide);
    }

    public void pausePreview(int sessId)
    {
    	Log.i(TAG, "Camera : pause Preview");
    	if(ProductSpecific.hasRealCamera())
		{
    		if(gstStreamOut != null)
    		{
				Log.i(TAG, "Camera : pause streamout Preview");
				gstStreamOut.setSessionIndex(sessId);
				gstStreamOut.pausePreview(sessId);
    		}
		}
		else
		{
			if(cam_preview != null)
			{
				Log.i(TAG, "Camera : pause native Preview");
				cam_preview.setSessionIndex(sessId);
				cam_preview.pausePlayback();
			}
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
    
    public void setKeyFrameInterval(int value){
    	userSettings.setKeyFrameInterval(value);
    	MiscUtils.writeStringToDisk(keyFrameIntervalFilePath, String.valueOf(value));
    }
    
    public void setSessionInitiation(int sessionInitiation, int sessionId)
    {
    	if (sessionInitiation != userSettings.getSessionInitiation(sessionId))
    	{
	    	userSettings.setSessionInitiation(sessionInitiation, sessionId);
	    	
		if (!getAutomaticInitiationMode()) {
		    if (userSettings.getMode(sessionId) == DeviceMode.STREAM_OUT.ordinal())
			{
	    		// if by transmitter, send currently saved url, else clear
	    		if (userSettings.getSessionInitiation(sessionId) == 1)
	    		{
	    			// Bug 108125: Clear out stream out url when changing initiation modes
	    			userSettings.setStreamOutUrl("", sessionId);
	    			sockTask.SendDataToAllClients(MiscUtils.stringFormat("STREAMURL%d=%s", sessionId, userSettings.getStreamOutUrl(sessionId)));
	    		}
	    		else
	    			sockTask.SendDataToAllClients(MiscUtils.stringFormat("STREAMURL%d=", sessionId));
			}
		    else if (userSettings.getMode(sessionId) == DeviceMode.STREAM_IN.ordinal())
			{
	    		// if not by transmitter send currently saved url, else clear
	    		if (userSettings.getSessionInitiation(sessionId) != 1)
	    			sockTask.SendDataToAllClients(MiscUtils.stringFormat("STREAMURL%d=%s", sessionId, userSettings.getStreamInUrl(sessionId)));
	    		else
	    			sockTask.SendDataToAllClients(MiscUtils.stringFormat("STREAMURL%d=", sessionId));
			}
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
    	RestartTxrxService();
    }
    
    public void RestartTxrxService(){
		saveUserSettings(); // Need to immediately save userSettings so that we remember our state after restarting
    	sockTask.SendDataToAllClients("DEVICE_READY_FB=FALSE");
    	sockTask.SendDataToAllClients("KillMePlease=true");	// Use kill me Please because sometimes kill -9 is needed
    }
    
    public void RecoverMediaServer() {
        Log.e(TAG, "Fatal error, kill mediaserver!");
    	sockTask.SendDataToAllClients("KillMediaServer=true");
    }
    
    public void stopOnIpAddrChange(){
		Log.e(TAG, "Restarting on device IP Address Change...!");
		// Bug 135405: Do not explicitly call restart streams for stream in, let timeouts handle it
        restartStreams(true, true); 	// Dont restart preview on IP change
    }
    
    public void SendToCresstore(String json, int option)
    {
    	String command = "CRESSTORE_PUBLISH";
    	if (option == CresstoreOptions.PublishAndSave)
    		command = "CRESSTORE_PUBLISH_AND_SAVE";
    	sockTask.SendDataToAllClients(command + "=" + json);
    }

    public void RestartAirMedia() {
        Log.e(TAG, "Fatal error, restart AirMedia!");
        sockTask.SendDataToAllClients("RestartAirMedia=splashtop");
    }
    
    public void airmediaRestart(int sessId) {
    	if (mAirMedia != null)
	    {
    		synchronized (mAirMediaLock) {	
    			Log.i(TAG, "restarting AirMedia!");
    			// Intentional do nothing for splashtop (not needed)
    		}
    	}
    }
   
    public void initUpdateStreamStateOnFirstFrame(boolean value)
    {
		for (int sessionId = 0; sessionId < NumOfSurfaces; sessionId++)
		{
			setUpdateStreamStateOnFirstFrame(sessionId, value);
		}
    }
    
    public void setUpdateStreamStateOnFirstFrame(int sessionId, boolean value)
    {
    	updateStreamStateOnFirstFrame[sessionId] = value;
    	Log.i(TAG,"setUpdateStreamStateOnFirstFrame: updateStreamStateOnFirstFrame["+sessionId+"] set to "+updateStreamStateOnFirstFrame[sessionId]);
    }
    
    public void sendAirMediaStoppedState(int sessionId)
    {
		// If DMPS send displayed join else use streamstate
		if (nativeGetProductTypeEnum() == 0x24)
		{    					
			sendAirMediaDisplayed(false);
		}
		else
		{
			// Don't send stopped if this index is being used by some other stream type
			if (userSettings.getUserRequestedStreamState(sessionId) == StreamState.STOPPED)
				SendStreamState(StreamState.STOPPED, sessionId);
			else
			{
				setCurrentStreamState(StreamState.STOPPED, sessionId);
	        	CresStreamCtrl.saveSettingsUpdateArrived = true; // flag userSettings to save
			}    					
		}
    }
    
    public void sendAirMediaStartedState(int sessionId)
    {
    	// If DMPS send displayed join else use streamstate
    	if (nativeGetProductTypeEnum() == 0x24)
    	{
    		sendAirMediaDisplayed(true);
    	}
    	else
    	{
    		SendStreamState(StreamState.STARTED, sessionId);
    	}
    }

    public void launchAirMedia(boolean val, int sessId, boolean fullscreen) {
    	stopStartLock[sessId].lock("launchAirMedia");
    	Log.i(TAG, "AirMedia sessionId=" + sessId + "   launch = " + val + "  fullScreen =" + fullscreen);
    	try
    	{
//    		// Bug 153417: When in AppSpace mode, allow AppSpace to stop before starting AirMedia
//    		if (userSettings.isAppspaceEnabled())
//    		{
//    			sendAirMediaStartedState(sessId);
//    			try { Thread.sleep(1000);} catch (InterruptedException e) {}
//    		}
    		
    		synchronized (mAirMediaLock) {
    			userSettings.setAirMediaLaunch(val, sessId);
    			if (val == true) // True = launch airmedia app, false = close app
    			{
    				// Do I need to stop all video here???  
    				if (mAirMedia == null && airMediaLicensed) 
    				{
    					if (mAirMedia == null)
    					{
    						Log.i(TAG, "launchAirMedia: airMedia is null - wait for constructor to be invoked - ignoring command");
    						return;
    					}
    					if (mAirMedia.airMediaIsUp())
    					{
    						Log.i(TAG, "launchAirMedia: airMedia is not yet up -ignoring command");
    						return;
    					}
    				}    				
    				if (mAirMedia != null)
    				{
    					int x, y, width, height;
    					if (fullscreen || ((userSettings.getAirMediaWidth() == 0) && (userSettings.getAirMediaHeight() == 0)))
    					{
    						Log.e(TAG, "AirMedia fullscreen true");

							// TODO functionize the lines below
    						Point size = getDisplaySize();

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

    					userSettings.setAirMediaDisplayScreen(haveExternalDisplays ? 1 : 0);
    					userSettings.setAirMediaWindowFlag(alphaBlending ? WindowManager.LayoutParams.TYPE_PRIORITY_PHONE : WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY);

    					if (mIsHdmiOutExternal || Boolean.parseBoolean(hdmiOutput.getSyncStatus()))
    					{
    						Log.i(TAG, "launching AirMedia");
    						// Force layer tag to be video to avoid any chance of the wrong type of surface (RGB888) from preview
    						setSurfaceViewTag(sessId, "VideoLayer");
    						mAirMedia.show(sessId, x, y, width, height);
    	    				mUsedForAirMedia[sessId] = true;
    						if (!updateStreamStateOnFirstFrame[sessId])
    							sendAirMediaStartedState(sessId);
    					}
    					else
    					{
               	            Log.i(TAG, "Ignoring start, because no output sync");
                        }
    				}
    				else
    				{
    					if (!airMediaLicensed)
    					{
    						Log.i(TAG, "Cannot launch AirMedia - no license");
    					}
    					else
    					{
    						Log.i(TAG, "Unable to launch AirMedia");
    					}
    				}
    			}
    			else
    			{
    				if (mAirMedia != null)
    				{
						Log.i(TAG, "hide AirMedia on streamId "+sessId);
    					mAirMedia.hide(sessId, true);
        				if (getSurfaceView(sessId).getVisibility() == View.VISIBLE)
        				{
            				Log.i(TAG, "Hiding window "+sessId+" because airmedia is turned off");
            	    		hideSplashtopWindow(sessId, false);
        				}
    				}
    				// Restore default Window once Air Media is stopped
    				Point size = getDisplaySize();
    				setWindowDimensions(0, 0, size.x, size.y, sessId);
    				
					sendAirMediaStoppedState(sessId);
    				mUsedForAirMedia[sessId] = false;
    			}
    		}
    	}
    	catch (Exception e)
    	{
    		Log.e(TAG, "Exception encountered trying to launch AirMedia");
    		e.printStackTrace();
    	}
    	finally
    	{
        	Log.i(TAG, "launchAirMedia exit for sessionId=" + sessId + "   launch = " + val);
    		stopStartLock[sessId].unlock("launchAirMedia");
    	}
    }
    
    public String setAirMediaLoginCode(int loginCode) {
    	synchronized (mAirMediaCodeLock) {
    		if ((loginCode < 0) || (loginCode > 9999))
    			return null; //Don't set out of range value

    		userSettings.setAirMediaLoginCode(loginCode);
			Log.i(TAG, "Incoming login code forces loginCodeMode to 'Fixed'");
    		userSettings.setAirMediaLoginMode(AirMediaLoginMode.Fixed.ordinal()); // When loginCode is set auto switch to fixed mode

    		if (getAirMediaNumberUserConnected() == 0)
    		{
    			if (mAirMedia != null)
    			{
    				mAirMedia.setLoginCode(userSettings.getAirMediaLoginCode());

    				if (userSettings.getAirMediaDisplayLoginCode())
    				{
    					mAirMedia.showLoginCodePrompt(loginCode);
    				}
    			} 
    			// send feedback of login mode since it might have changed
    			sockTask.SendDataToAllClients(MiscUtils.stringFormat("AIRMEDIA_LOGIN_MODE=%d", userSettings.getAirMediaLoginMode()));
    			return MiscUtils.stringFormat("%04d", userSettings.getAirMediaLoginCode());
    		}
    		else
    		{
    			// If users are connected wait until all disconnect before changing code
    			pendingAirMediaLoginCodeChange = true;
    			Log.i(TAG, "Filtering out AirMedia login code change to " + loginCode + " because " + getAirMediaNumberUserConnected() + " users are connected, changes will take effect once all user disconnect");
    			return null;         // do not send feedback for login code change - sent independently
    		}   
    	}
    }
    
    public String setAirMediaLoginMode(int loginMode) {    	
    	synchronized (mAirMediaCodeLock) {
    		userSettings.setAirMediaLoginMode(loginMode);

        	if (getAirMediaNumberUserConnected() == 0)
        	{
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
        		sockTask.SendDataToAllClients(MiscUtils.stringFormat("AIRMEDIA_LOGIN_CODE=%d", userSettings.getAirMediaLoginCode()));
        		return MiscUtils.stringFormat("%d", userSettings.getAirMediaLoginMode());
        	}
        	else
        	{
        		// If users are connected wait until all disconnect before changing code
        		pendingAirMediaLoginCodeChange = true;
        		Log.i(TAG, "Filtering out AirMedia login mode change to " + loginMode + " because " + getAirMediaNumberUserConnected() + " users are connected, changes will take effect once all user disconnect");
        		return null;         // do not send feedback for login mode change - sent independently
        	}    	
		}    	
    }
    
    public void setAirMediaDisplayLoginCode(boolean display)
    {
    	synchronized (mAirMediaLock) {
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
    }
    
    public void setAirMediaModerator(boolean enable)
    {
    	synchronized (mAirMediaLock) {
    		userSettings.setAirMediaModerator(enable);
    		if (mAirMedia != null)
    		{
    			mAirMedia.setModeratorEnable(enable);
    		}
    	}
    }
    
    public void setAirMediaResetConnections(boolean enable)
    {
    	synchronized (mAirMediaLock) {
    		if ((enable) && (mAirMedia != null))
    		{
    			mAirMedia.resetConnections();
    		}
    	}
    }
    
    public void setAirMediaDisconnectUser(int userId, boolean enable, int sessId)
    {
    	synchronized (mAirMediaLock) {
    		userSettings.setAirMediaDisconnectUser(enable, userId);
    		if ((enable) && (mAirMedia != null))
    		{
    			mAirMedia.disconnectUser(userId);
    		}
    	}
    }
    
    public void setAirMediaStartUser(int userId, boolean enable, int sessId)
    {
    	
    }
    
    public void setAirMediaUserPosition(int userId, int position, int sessId)
    {
    	synchronized (mAirMediaLock) {
    		if (mAirMedia != null)
    		{
    			if (position == 0)
    				mAirMedia.stopUser(userId);
    			else
    				mAirMedia.setUserPosition(userId, position);
    		}
    	}
    }
    
    public void setAirMediaStopUser(int userId, boolean enable, int sessId)
    {
    	Log.i(TAG, "setAirMediaStopuser userId="+userId+"  enable="+enable);
    	if (enable)
    	{
        	Log.i(TAG, "calling stopuser userId="+userId);
    		mAirMedia.stopUser(userId);
    	}
    }
    
    public void setAirMediaOsdImage(String filePath)
    {
    	// Image gets sent on apply
    	userSettings.setAirMediaOsdImage(filePath);
    }
    
    // Function deprecated - used with old digital joins which are no longer supported
    public void setAirMediaIpAddressPrompt(boolean enable)
    {    	
    	synchronized (mAirMediaLock) {
    		if (userSettings.getAirMediaCustomPrompt())
    		{
    			enable = false;
    		}
    		userSettings.setAirMediaIpAddressPrompt(enable);
    		if (mAirMedia != null)
    		{
    			mAirMedia.setIpAddressPrompt(enable);
    		}
    		sendAirMediaConnectionAddress();
    	}
    }
    
    // Function deprecated - used with old digital joins which are no longer supported
    public void setAirMediaDomainNamePrompt(boolean enable)
    {
    	synchronized (mAirMediaLock) {
    		// If Host Name Prompt is disabled or custom string is enabled - Domain Name too must be disabled
    		if (!userSettings.getAirMediaHostNamePrompt() || userSettings.getAirMediaCustomPrompt())
    		{
    			enable = false;
    		}
    		userSettings.setAirMediaDomainNamePrompt(enable);
    		if (mAirMedia != null)
    		{
    			mAirMedia.setDomainNamePrompt(enable);
    		}
    		setDomainName("");
    		sendAirMediaConnectionAddress();
    	}
    }

    // Function deprecated - used with old digital joins which are no longer supported
    public void setAirMediaHostNamePrompt(boolean enable)
    {
    	synchronized (mAirMediaLock) {
    		if (userSettings.getAirMediaCustomPrompt())
    		{
    			enable = false;
    		}
    		userSettings.setAirMediaHostNamePrompt(enable);
    		// If Host Name Prompt is disabled and Domain Name Prompt is enabled, disable latter and send feedback.
    		if (!userSettings.getAirMediaHostNamePrompt() && userSettings.getAirMediaDomainNamePrompt()) {
    			userSettings.setAirMediaDomainNamePrompt(false);
    			sendAirMediaDomainNamePromptFeedback();
    		}
    		setHostName("");
    		sendAirMediaConnectionAddress();
    	}
    }

    // Function deprecated - used with old digital joins which are no longer supported
    public void setAirMediaCustomPrompt(boolean enable)
    {
    	synchronized (mAirMediaLock) {
    		// All other prompt options must be disabled before we allow custom prompt to be enabled
    		if (userSettings.getAirMediaIpAddressPrompt() || 
    				userSettings.getAirMediaHostNamePrompt() || 
    				userSettings.getAirMediaDomainNamePrompt()) {
    			enable = false;
    		}
    		userSettings.setAirMediaCustomPrompt(enable);
    		// If custom prompt is enabled, all other prompts must be forced off (send feedbacks)
    		// This code will likely be used in future - when we go to the model where last setting wins
    		// right now it will have no effect due to the the check at the start of this synchronized block.
    		if (userSettings.getAirMediaCustomPrompt()) {
    			if (userSettings.getAirMediaIpAddressPrompt()) {
    				userSettings.setAirMediaIpAddressPrompt(false);
    				sendAirMediaIpAddressPromptFeedback();
    			}
    			if (userSettings.getAirMediaHostNamePrompt()) {
    				userSettings.setAirMediaHostNamePrompt(false);
    				sendAirMediaHostNamePromptFeedback();
    			}
    			if (userSettings.getAirMediaIpAddressPrompt()) {
    				userSettings.setAirMediaDomainNamePrompt(false);
    				sendAirMediaDomainNamePromptFeedback();
    			}
    		}
    		sendAirMediaConnectionAddress();
    	}
    }
    
    public void setAirMediaDisplayConnectionOptionEnable(boolean enable)
    {
    	synchronized (mAirMediaLock) {
    		userSettings.setAirMediaDisplayConnectionOptionEnable(enable);
    		sendAirMediaConnectionAddress();
    	}
    }
    
    public void setAirMediaDisplayConnectionOption(int optVal)
    {
    	synchronized (mAirMediaLock) {
    		userSettings.setAirMediaDisplayConnectionOption(optVal);
    		sendAirMediaConnectionAddress();
    	}
    }
    
    public void setAirMediaCustomPromptString(String promptString)
    {
    	synchronized (mAirMediaLock) {
    		userSettings.setAirMediaCustomPromptString(promptString);
    		if (userSettings.getAirMediaCustomPrompt() || 
    				(userSettings.getAirMediaDisplayConnectionOption() == AirMediaDisplayConnectionOption.Custom))
    			sendAirMediaConnectionAddress();
    	}
    }
    
    // Function deprecated - used with old digital joins which are no longer supported
    public void sendAirMediaIpAddressPromptFeedback()
    {
    	sockTask.SendDataToAllClients(MiscUtils.stringFormat("AIRMEDIA_IP_ADDRESS_PROMPT=%s", 
    			Boolean.toString(userSettings.getAirMediaIpAddressPrompt())));
    }
    
    // Function deprecated - used with old digital joins which are no longer supported
    public void sendAirMediaHostNamePromptFeedback()
    {
    	sockTask.SendDataToAllClients(MiscUtils.stringFormat("AIRMEDIA_HOST_NAME_PROMPT=%s", 
    			Boolean.toString(userSettings.getAirMediaHostNamePrompt())));
    }
    
    // Function deprecated - used with old digital joins which are no longer supported
    public void sendAirMediaDomainNamePromptFeedback()
    {
    	sockTask.SendDataToAllClients(MiscUtils.stringFormat("AIRMEDIA_DOMAIN_NAME_PROMPT=%s", 
    			Boolean.toString(userSettings.getAirMediaDomainNamePrompt())));
    }
    
    // Function deprecated - used with old digital joins which are no longer supported
    public void sendAirMediaCustomPromptFeedback()
    {
    	sockTask.SendDataToAllClients(MiscUtils.stringFormat("AIRMEDIA_CUSTOM_PROMPT=%s", 
    			Boolean.toString(userSettings.getAirMediaCustomPrompt())));
    }
    
    public void setAirMediaWindowPosition(int x, int y, int width, int height)
    {    	
    	synchronized (mAirMediaLock) {
    		userSettings.setAirMediaX(x);
    		userSettings.setAirMediaY(y);
    		userSettings.setAirMediaWidth(width);
    		userSettings.setAirMediaHeight(height);
    		// Just cache the position do not actually send to AirMedia
    	}
    }
    
    public void setAirMediaWindowXOffset(int x)
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
    
    public void setAirMediaWindowYOffset(int y)
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
    
    public void setAirMediaWindowWidth(int width)
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
    
    public void setAirMediaWindowHeight(int height)
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
    
    public void airMediaApplyLayoutPassword(boolean apply)
    {
    	if (apply)
    	{
    		// apply layout password here
    		// TODO: we need layout password from airmedia app    		
    	}
    }
    
    public void setAirMediaLayoutPassword(String layoutPassword)
    {
    	// User needs to send apply join to take effect
    	userSettings.setAirMediaLayoutPassword(layoutPassword);
    }
    
    public void airMediaApplyOsdImage(boolean apply)
    {
    	if (apply)
    	{
    		if (mAirMedia != null)
        	{
        		mAirMedia.setOsdImage(userSettings.getAirMediaOsdImage());
        	}
    	}
    }
    
    public void airMediaSetDisplayScreen(int displayId)
    {
    	if (mAirMedia != null)
    	{
    		userSettings.setAirMediaDisplayScreen(displayId);
    		mAirMedia.setDisplayScreen(displayId);
    	}
    }
    
    public void airMediaSetWindowFlag(int windowFlag)
    {
    	if (mAirMedia != null)
    	{
    		userSettings.setAirMediaWindowFlag(windowFlag);
    		mAirMedia.setWindowFlag(windowFlag);
    	}
    }
    
    public void setAirMediaIsCertificateRequired(boolean enable)
    {
    	if (mAirMedia != null)
    	{
    		userSettings.setAirMediaIsCertificateRequired(enable);
    		mAirMedia.setAirMediaIsCertificateRequired(enable);
    	}
    }
    
    public void setAirMediaOnlyAllowSecureConnections(boolean enable)
    {
    	if (mAirMedia != null)
    	{
    		userSettings.setAirMediaOnlyAllowSecureConnections(enable);
    		mAirMedia.setAirMediaOnlyAllowSecureConnections(enable);
    	}
    }
    
    public void setAirMediaDebug(String debugCommand)
    {
    	if (mAirMedia != null)
    	{
    		mAirMedia.debugCommand(debugCommand);
    	}
    }
    
    public void setAirMediaProcessDebugMessage(String debugCommand)
    {
    	if (mAirMedia != null)
    	{
    		mAirMedia.airmediaProcessDebugCommand(debugCommand);
    	}
    }
    
    public void setAirMediaClearCache()
    {
		// Note that this function does not clear cache (that is handled by CSIO)
		// This function will prevent AirMedia from being restarted when killed
    	if (mAirMedia != null)
    	{
    		mAirMedia.clearCache();
    	}
    }
    
    public void setAirMediaProjectionLock(boolean val)
    {
    	if (mAirMedia != null)
    	{
    		userSettings.setAirMediaProjectionLock(val);
    		mAirMedia.setProjectionLock(val);
    	}
    }

    public void airMediaEnable(boolean enable)
    {
    	userSettings.setAirMediaEnable(enable);
    	Log.i(TAG,"AirMedia Enable = "+userSettings.getAirMediaEnable());
		if (mAirMedia != null)
		{
    		Log.i(TAG,"*************** airMediaEnable "+(enable?"enable":"disable")+"   *********");
            // function getAirMediaConnectionIpAddress returns "None" when airMediaEnable is false
    		mAirMedia.setAdapter(getAirMediaConnectionIpAddress());
		}
		
		sendAirMediaConnectionAddress();
    }
    
    public void setAirMediaAdaptorSelect(int select)
    {
    	if (select != userSettings.getAirMediaAdaptorSelect())
    	{
    		userSettings.setAirMediaAdaptorSelect(select);
    		if (mAirMedia != null)
    		{
        		Log.i(TAG,"*************** setAirMediaAdaptorSelect -- addr="+getAirMediaConnectionIpAddress()+"   *********");
        		mAirMedia.setAdapter(getAirMediaConnectionIpAddress());
    			mAirMedia.setIpAddressPrompt(userSettings.getAirMediaIpAddressPrompt());
    		}
    		
    		// Update connection address as well
    		sendAirMediaConnectionAddress();
    	}    	
    }
    
    // Will update airMedia IP information when called
    public void updateAirMediaIpInformation()
    {
    	if (mAirMedia != null)
		{
    		Log.i(TAG,"*************** updateAirMediaIpInformation -- addr="+getAirMediaConnectionIpAddress()+"   *********");
    		mAirMedia.setAdapter(getAirMediaConnectionIpAddress());
    		mAirMedia.setIpAddressPrompt(userSettings.getAirMediaIpAddressPrompt());
		}
    	
    	sendAirMediaConnectionAddress();
    }
    
    public String getAirMediaInterface()
    {	
    	return InterfaceNames[userSettings.getAirMediaAdaptorSelect()];
    }

    
    public void sendAirMediaConnectionAddress()
    {
    	sockTask.SendDataToAllClients(MiscUtils.stringFormat("AIRMEDIA_CONNECTION_ADDRESS=%s", getAirMediaConnectionAddress()));
    }
    
    private String getAirMediaConnectionAddressWhenNone()
    {
    	if (userSettings.getAirMediaAdaptorSelect() < 0)
    	{
    		/* Disabled */
    		return "";
    	}
    	else
    	{
    		/* Offline */
    		return "Device Offline";
    	}
    }
    
    public String getAirMediaConnectionAddress()
    {
    	// When connection option is disabled feedback the same connection URL and rely on AVF/Program 0 to blank out the URL
//    	if (!userSettings.getAirMediaDisplayConnectionOptionEnable())
//    	{
//    		Log.i(TAG, "getAirMediaConnectionAddress() returning empty string because DisplayConnectionOptionEnable is false");
//    		return "";
//    	}
    	if (!userSettings.getAirMediaEnable()) {
    		return "";
    	}
    	StringBuilder url = new StringBuilder(512);
        url.append("http://");
		String ipAddr = getAirMediaConnectionIpAddress();    		
    	switch (userSettings.getAirMediaDisplayConnectionOption())
    	{
    	case AirMediaDisplayConnectionOption.Ip:
    		if (ipAddr.equals("None")) 
    			return getAirMediaConnectionAddressWhenNone();
    		url.append(ipAddr);
    		break;
    	case AirMediaDisplayConnectionOption.Host:
    		if (ipAddr.equals("None")) 
    			return getAirMediaConnectionAddressWhenNone();
    		setHostName("");
    		if (hostName == null) return "";
    		url.append(hostName);
    		break;
    	case AirMediaDisplayConnectionOption.HostDomain:
    		if (ipAddr.equals("None")) 
    			return getAirMediaConnectionAddressWhenNone();
    		setHostName("");
    		setDomainName("");
    		if (hostName == null) return "";
    		url.append(hostName);
            if (domainName != null) {
            	url.append(".");
            	url.append(domainName);
            }
    		break;
    	case AirMediaDisplayConnectionOption.Custom:
    		url = new StringBuilder(userSettings.getAirMediaCustomPromptString());
    		break;
    	default:
    		Log.i(TAG, "getAirMediaConnectionAddress() invalid AirMediaDisplayConnectionOption value"+
    				userSettings.getAirMediaDisplayConnectionOption());
    		return "";
    	}
 
		Log.i(TAG, "getAirMediaConnectionAddress() returning "+url.toString());
        return url.toString();    	
    }
    
    // Function deprecated - used with old digital joins which are no longer supported
    public String getDigitalJoinAirMediaConnectionAddress()
    {
    	boolean showIp = userSettings.getAirMediaIpAddressPrompt();
    	boolean showHost = userSettings.getAirMediaHostNamePrompt();
    	boolean showDomain = userSettings.getAirMediaDomainNamePrompt();
    	boolean showCustom = userSettings.getAirMediaCustomPrompt();
    	if (!showIp && !showHost && !showDomain && !showCustom)
    		return "";
    	if (showCustom)
    		return userSettings.getAirMediaCustomPromptString();
    	String ipAddr = getAirMediaConnectionIpAddress();
    	if (!showHost) {
        	// Do not allow display of DomainName if HostName is not displayed
    		showDomain = false;
    	}
    	if ((!showHost && !showIp) || ipAddr.equals("None"))
    		return "";
    	StringBuilder url = new StringBuilder(512);
        url.append("http://");
        if (showHost && hostName != null) {
        	url.append(hostName);
        }
        if (showDomain && domainName != null) {
        	url.append(".");
        	url.append(domainName);
        }
        if (showIp) {
        	if (!showHost && !showDomain) {
        		url.append(getAirMediaConnectionIpAddress());
        	} else {
        		url.append(" (");
        		url.append(getAirMediaConnectionIpAddress());
        		url.append(")");
        	}
        }
		Log.i(TAG, "getDigitalAirMediaConnectionAddress() returning "+url.toString());
        return url.toString();    	
    }
    
    public String getAirMediaConnectionIpAddress()
    {
    	String ipaddr=null;
    	if (!userSettings.getAirMediaEnable())
    	{
    		return "None";
    	}
        if (userSettings.getAirMediaAdaptorSelect() == 0)
        {
        	ipaddr = userSettings.getDeviceIp();
        	if (ipaddr.equals("0.0.0.0"))
        		return "None";
        	else
        	    return ipaddr;
        }
        else if (userSettings.getAirMediaAdaptorSelect() == 1)
        {
        	ipaddr = userSettings.getAuxiliaryIp();
        	if (ipaddr.equals("0.0.0.0"))
        		return "None";
        	else
        	    return ipaddr;
        }
        else if (userSettings.getAirMediaAdaptorSelect() < 0)
        {
        	return "None";
        }
        else
        {
        	Log.w(TAG, "Invalid adaptor select value of " + userSettings.getAirMediaAdaptorSelect());
        	return userSettings.getDeviceIp();
        }
    }

    
    public String getAirMediaVersion()
    {
    	String versionName = "";
		final PackageManager pm = getPackageManager();
		String apkName = "ReceiverAirMediaSplashtop.apk";
		String fullPath = "/data/app" + "/" + apkName;        
		PackageInfo info = pm.getPackageArchiveInfo(fullPath, 0);
		if (info != null)
		{
			ApplicationInfo ai=null;
			versionName = info.versionName;
			if (AirMediaSplashtop.checkAirMediaLicense())
				MiscUtils.writeStringToDisk("/dev/shm/crestron/CresStreamSvc/airmediaVersion", versionName);
			try {
				ai = pm.getApplicationInfo("com.crestron.airmedia.receiver.m360", PackageManager.GET_META_DATA);
			} catch(Exception e) { Log.e(TAG, "Exception encountered trying to get metadata for AirMedia SDK version");}
			if (ai != null) {
				Bundle bundle = ai.metaData;
				String serverVersion = bundle.getString("serverVersion");
				if (serverVersion != null && !serverVersion.equals("")) {
					MiscUtils.writeStringToDisk("/dev/shm/crestron/CresStreamSvc/airmediaServerVersion", serverVersion);
				}
			}
		}
		
		return versionName;
    }

    // mMsMiceEnable respresents actual current state - it is needed because at startup the userSettings.getMsMiceEnable() 
    // does not represent the actual state at startup - it is the desired state at startup.
    public void msMiceEnable(boolean enable)
    {
    	userSettings.setMsMiceEnable(enable);
    	Log.i(TAG, "msMiceEnable(): requesting msMice " + ((enable)?"enabled":"disabled") + 
    			" - currently it is " + ((mMsMiceEnabled)?"enabled":"disabled"));
    	boolean userRequested = userSettings.getMsMiceEnable();
    	if (userRequested == mMsMiceEnabled)
    		return;
    	mMsMiceEnabled = enable;
    	if (mMsMiceEnabled)
    	{
    		// turn on ms mice
    		streamPlay.msMiceStart();
    		String ipaddr = getAirMediaConnectionIpAddress();
    		if (ipaddr.equals("None"))
    			ipaddr = null;
    		streamPlay.msMiceSetAdapterAddress(ipaddr, getAirMediaInterface());
    	}
    	else
    	{
    		// turn off ms mice
    		streamPlay.msMiceStop();
    		streamPlay.msMiceSetAdapterAddress(null, getAirMediaInterface());    	}
    }
    
    // mMiracastEnabled respresents actual current state - it is needed because at startup the userSettings.getAirMediaMiracastEnable() 
    // does not represent the actual state at startup - it is the desired state at startup.
    public void airMediaMiracastEnable(boolean enable)
    {
    	userSettings.setAirMediaMiracastEnable(enable);
    	Log.i(TAG, "airMediaMiracastEnable(): requesting enable=" + ((enable)?"enabled":"disabled") +
    			" - currently it is " + ((mMiracastEnabled)?"enabled":"disabled"));
    	boolean userRequested = userSettings.getAirMediaMiracastEnable();
    	if (userRequested == mMiracastEnabled)
    		return;
    	mMiracastEnabled = enable;
    	msMiceEnable(enable);
    	if (mAirMedia != null)
    	{
    		mAirMedia.setAirMediaMiracast(enable);
    	}
    }
    
    public void airMediaMiracastWifiDirectMode(boolean enable)
    {
    	userSettings.setAirMediaMiracastWifiDirectMode(enable);
    	Log.i(TAG, "airMediaMiracastWifiDirectMode(): wifi_direct_mode_enable="+enable);
    	if (mAirMedia != null)
    	{
    		mAirMedia.setAirMediaMiracastWifiDirectMode(enable);
    	}
    }
    
    public void airMediaMiracastWirelessOperatingRegion(int value)
    {
    	userSettings.setAirMediaMiracastWirelessOperatingRegion(value);
    	Log.i(TAG, "airMediaMiracastWirelessOperatingRegion(): wireless operating region="+value);
    	if (mAirMedia != null)
    	{
    		mAirMedia.setAirMediaMiracastWirelessOperatingRegion(value);
    	}
    }
    
    public void setCamStreamEnable(boolean enable) {

		stopStartLock[0].lock("setCamStreamEnable");
		try
		{
			userSettings.setCamStreamEnable(enable);
			
			if (gstStreamOut != null)
			{
				int sessId;
				int rtn = 0;
				boolean bPreviewState;
				
				sessId = gstStreamOut.getSessionIndex();  // sessId was also set by startGstPreview()
				if (enable)
				{
				  //start streamout
					gstStreamOut.start();
					rtn = gstStreamOut.waitForPreviewAvailable(sessId,5);
				}
				else
				{
				  //stop streamout
					gstStreamOut.stop();
				}
				
				if( rtn == 110 )
				{
					Log.i(TAG, "Timed out waiting for Preview");
					gstStreamOut.recoverTxrxService();
				}
			}
		} finally
		{
			stopStartLock[0].unlock("setCamStreamEnable");
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
    	if (mAirMedia != null && mAirMedia.airMediaIsUp())
    	{
    		mAirMedia.setOrderedLock(true, "airMediaUserFeedbackUpdateRequest");
    		try {
    			mAirMedia.querySenderList(true);
    			sendAirMediaNumberUserConnectedUpdateRequest(); // Force sending of number of user connected on update request
    		} finally {
    			mAirMedia.setOrderedLock(false, "airMediaUserFeedbackUpdateRequest");
    		}
    	} 
    	else if (airMediaLicensed)
    	{
    		// Send defaults for all user connections 
        	Log.i(TAG, "airmedia is not yet up - returning default for airmedia status");
    		for (int i = 1; i <= 32; i++)
    		{
    			userSettings.setAirMediaUserConnected(false, i);
    			sendAirMediaUserFeedbacks(i, "", "", 0, false);					
    		}
    		sendAirMediaStatus(0);
    		sendAirMediaNumberUserConnectedUpdateRequest();
    	}
    }
    
    public void sendAirMediaUserFeedbacks(int userId, String userName, String ipAddress, int position, boolean status)
    {
    	userSettings.setAirMediaUserPosition(position, userId);
    	Log.i(TAG, MiscUtils.stringFormat("AIRMEDIA_USER_NAME=%d:%s", userId, userName));
    	Log.i(TAG, MiscUtils.stringFormat("AIRMEDIA_USER_IP=%d:%s", userId, ipAddress));
    	Log.i(TAG, MiscUtils.stringFormat("AIRMEDIA_USER_POSITION=%d:%d", userId, position));
    	Log.i(TAG, MiscUtils.stringFormat("AIRMEDIA_USER_CONNECTED=%d:%s", userId, String.valueOf(status)));

    	sockTask.SendDataToAllClients(MiscUtils.stringFormat("AIRMEDIA_USER_NAME=%d:%s", userId, userName));
    	sockTask.SendDataToAllClients(MiscUtils.stringFormat("AIRMEDIA_USER_IP=%d:%s", userId, ipAddress));
    	sockTask.SendDataToAllClients(MiscUtils.stringFormat("AIRMEDIA_USER_POSITION=%d:%d", userId, position));
    	sockTask.SendDataToAllClients(MiscUtils.stringFormat("AIRMEDIA_USER_CONNECTED=%d:%s", userId, String.valueOf(status)));   	
    }
    
    public void sendAirMediaStatus(int status)
    {
    	// TODO: send on update request
    	Log.i(TAG, MiscUtils.stringFormat(MiscUtils.stringFormat("AIRMEDIA_STATUS=%d", status)));
		sockTask.SendDataToAllClients(MiscUtils.stringFormat("AIRMEDIA_STATUS=%d", status));
    }
    
    public void sendAirMediaDisplayed(boolean val)
    {
    	Log.i(TAG, MiscUtils.stringFormat("AIRMEDIA_DISPLAYED="+val));
		sockTask.SendDataToAllClients("AIRMEDIA_DISPLAYED="+val);
    }
    
    public int getAirMediaNumberUserConnected()
    {
    	int numberUserConnected = 0;
    	for (int i = 1; i <= 32; i++) // We handle airMedia user ID as 1 based
    	{
    		if (userSettings.getAirMediaUserConnected(i))
    			numberUserConnected++;
    	}
    	
    	return numberUserConnected;
    }
    
    public void sendAirMediaNumberUserConnected()
    {
    	// Ensure that this function is safe from multiple calls
    	synchronized (mAirMediaCodeLock) {
    		// TODO: send on update request
        	int numberUserConnected = getAirMediaNumberUserConnected();
        	// Dont call if number of users did not change
        	if (numberUserConnected != mAirMediaNumberOfUsersConnected)
        	{
        		mAirMediaNumberOfUsersConnected = numberUserConnected;
        		
        		Log.i(TAG, MiscUtils.stringFormat("AIRMEDIA_NUMBER_USER_CONNECTED=%d", numberUserConnected));
        		sockTask.SendDataToAllClients(MiscUtils.stringFormat("AIRMEDIA_NUMBER_USER_CONNECTED=%d", numberUserConnected));

        		// Bug 121298: Generate new random code whenever all users disconnect
        		if ( (userSettings.getAirMediaLoginMode() == AirMediaLoginMode.Random.ordinal()) && (numberUserConnected == 0))
        		{
        			sockTask.SendDataToAllClients(MiscUtils.stringFormat("AIRMEDIA_LOGIN_MODE=%d", userSettings.getAirMediaLoginMode()));
        			setAirMediaLoginMode(userSettings.getAirMediaLoginMode());
        			pendingAirMediaLoginCodeChange = false;
        		}
        		else if ( (pendingAirMediaLoginCodeChange == true) && (numberUserConnected == 0) )
        		{
        			setAirMediaLoginMode(userSettings.getAirMediaLoginMode());
        			if (userSettings.getAirMediaLoginMode() == AirMediaLoginMode.Fixed.ordinal())
        			{
        				setAirMediaLoginCode(userSettings.getAirMediaLoginCode());
        			} else {
        				sockTask.SendDataToAllClients(MiscUtils.stringFormat("AIRMEDIA_LOGIN_MODE=%d", userSettings.getAirMediaLoginMode()));
        			}
        			pendingAirMediaLoginCodeChange = false;
        		}
        	}
		}    	
    }

	// Force sending here since we do not know if we are out of sync with CS
    public void sendAirMediaNumberUserConnectedUpdateRequest()
    {
        // If mAirMediaNumberOfUsersConnected is -1, that means we never called sendAirMediaNumberUserConnected
		if (mAirMediaNumberOfUsersConnected == -1)
			sendAirMediaNumberUserConnected();
		else
		{
			Log.d(TAG, "Sending AIRMEDIA_NUMBER_USER_CONNECTED=" + mAirMediaNumberOfUsersConnected);
	        sockTask.SendDataToAllClients(MiscUtils.stringFormat("AIRMEDIA_NUMBER_USER_CONNECTED=%d", mAirMediaNumberOfUsersConnected));
		}
    }
    
    private void checkFileExistsElseCreate(String filePath)
    {
    	File file = new File (filePath);
        if (!file.isFile())	//check if file exists
        {
        	try {
        		file.getParentFile().mkdirs(); 
        		file.createNewFile();
        	} catch (Exception e) {}
        }
    }
    
    private void handleSurfaceFlingerViolation()
    {
		Log.e(TAG, "------ Recreate CresDisplaySurface due to surfaceFlinger Violation ------");
		RecoverTxrxService();
    }
    
    private void monitorSurfaceFlingerViolations()
    {
    	final Object surfaceFlingerObserverLock = new Object();
    	// Make sure all files exist before observing
    	checkFileExistsElseCreate(surfaceFlingerViolationFilePath);

		// Monitor surfaceFlinger violation events
        surfaceFlingerViolationObserver = new FileObserver(surfaceFlingerViolationFilePath, FileObserver.CLOSE_WRITE) {						
			@Override
			public void onEvent(int event, String path) {
				try 
				{
					synchronized (surfaceFlingerObserverLock) {
						Log.i(TAG, "Received surfaceFlinger violation change event");
						int violation = 0;
						try 
						{
							violation =  Integer.parseInt(MiscUtils.readStringFromDisk(surfaceFlingerViolationFilePath));
							if (violation != 0)
								handleSurfaceFlingerViolation();
						} 
						catch (NumberFormatException e)
						{
							Log.w(TAG, "Invalid surfaceFlinger value found " + e.toString());
						}				
						Log.i(TAG, "Finished surfaceFlinger violation change event");
					}
				}
				catch (Exception e)
				{
					e.printStackTrace();
				}
			}
        };
        surfaceFlingerViolationObserver.startWatching();
    }

    private void monitorHdmiStates()
    {
    	final Object hdmiObserverLock = new Object();
    	// Make sure all files exist before observing
    	checkFileExistsElseCreate(hdmiInputHPDFilePath);
    	checkFileExistsElseCreate(hdmiInputResolutionFilePath);
    	checkFileExistsElseCreate(hdmiOutputResolutionFilePath);
		       
    	// Monitor input HPD events
//        hdmiInputHpdObserver = new FileObserver(hdmiInputHPDFilePath, FileObserver.CLOSE_WRITE) {						
//			@Override
//			public void onEvent(int event, String path) {
//				synchronized (hdmiObserverLock) {
//					Log.i(TAG, "Received HDMI input HPD event");
//					int hpdId = 0;
//					try 
//					{
//						hpdId =  Integer.parseInt(MiscUtils.readStringFromDisk(hdmiInputHPDFilePath));
//						handleHdmiInputHpdEvent(hpdId);
//					} 
//					catch (NumberFormatException e)
//					{
//						Log.w(TAG, "Invalid HPD id found " + e.toString());
//					}				
//					Log.i(TAG, "Finished HDMI input HPD event");
//				}				
//			}
//		};
//		hdmiInputHpdObserver.startWatching();
		
		// Monitor input resolution events
        hdmiInputResolutionObserver = new FileObserver(hdmiInputResolutionFilePath, FileObserver.CLOSE_WRITE) {						
			@Override
			public void onEvent(int event, String path) {
				try 
				{
					synchronized (hdmiObserverLock) {
						Log.i(TAG, "Received HDMI input resolution event");
						int resolutionId = 0;
						try 
						{
							resolutionId =  Integer.parseInt(MiscUtils.readStringFromDisk(hdmiInputResolutionFilePath));
							handleHdmiInputResolutionEvent(resolutionId);
						} 
						catch (NumberFormatException e)
						{
							Log.w(TAG, "Invalid resolution found " + e.toString());
						}				
						Log.i(TAG, "Finished HDMI input resolution event");
					}
				}
				catch (Exception e)
				{
					e.printStackTrace();
				}
			}
        };
		hdmiInputResolutionObserver.startWatching();
		
		// Monitor output resolution events
        hdmiOutputResolutionObserver = new FileObserver(hdmiOutputResolutionFilePath, FileObserver.CLOSE_WRITE) {						
			@Override
			public void onEvent(int event, String path) {
				try
				{
					synchronized (hdmiObserverLock) {
						Log.i(TAG, "Received HDMI output resolution");
						int hdmiOutResolutionEnum = 0;
						try 
						{
							hdmiOutResolutionEnum =  Integer.parseInt(MiscUtils.readStringFromDisk(hdmiOutputResolutionFilePath));
							Log.i(TAG, "Received hdmiout resolution changed broadcast ! " + hdmiOutResolutionEnum);

							synchronized (mDisplayChangedLock) 
							{
								handleHdmiOutputChange();
							}
						} 
						catch (NumberFormatException e)
						{
							Log.w(TAG, "Invalid resolution id found " + e.toString());
						}
						Log.i(TAG, "Finished HDMI output resolution event");
					}
				}
				catch (Exception e)
				{
					e.printStackTrace();
				}
			}
		};
		hdmiOutputResolutionObserver.startWatching();
    }
    
    void handleHdmiInputHpdEvent(int hpdId)
    {
    	hdmiLock.lock("handleHdmiInputHpdEvent");
    	try
    	{
    		if (hdmiInputDriverPresent)
    		{
				if (hpdId == 0)
					setNoVideoImage(true);
				sendHdmiInSyncState();

				Log.i(TAG, "Received hpd broadcast ! " + hpdId);
				hpdHdmiEvent = 1;
				mForceHdcpStatusUpdate = true;
       		}
    	}
    	finally
    	{
    		hdmiLock.unlock("handleHdmiInputHpdEvent");
    	}
    }
    
    void handleHdmiInputResolutionEvent(int resolutionId)
    {
    	hdmiLock.lock("handleHdmiInputResolutionEvent - got resolution id: " + resolutionId);
    	try
    	{
    		if (hdmiInputDriverPresent)
    		{
    			if (resolutionId != mCurrentHdmiInputResolution)
    			{
    				mCurrentHdmiInputResolution = resolutionId;
    				int prevResolutionIndex = hdmiInput.getResolutionIndex();
    				if (resolutionId != 0)
    					hdmiInput.setResolutionIndex(resolutionId);

    				// This will start CSI bus as well as setup ducati
    				try {
    					ProductSpecific.getHdmiInputStatus();
    					Log.i(TAG, "Setup CSI bus");
    				} catch (RuntimeException e) {
    					e.printStackTrace();
    				}

    				// Fix issue where we start video before we setup resolution 
    				refreshInputResolution();

    				setCameraAndRestartStreams(resolutionId); //we need to restart streams for resolution change		                	

    				//Wait 5 seconds before sending hdmi in sync state - bug 96552
    				if (nativeGetProductTypeEnum() == 0x1C)	// ONLY FOR TXRX, rest immediately send
    				{
    					new Thread(new Runnable() {
    						public void run() { 
    							try {
    								Thread.sleep(5000);
    							} catch (Exception e) { e.printStackTrace(); }				                			
    							sendHdmiInSyncState();
    						}
    					}).start();
    				}
    				// Dont add 5 seconds to HDMI display time bug 134029
    				else
    				{
    					sendHdmiInSyncState();
    				}
    				hpdHdmiEvent = 1;
    				mForceHdcpStatusUpdate = true;
    				Log.i(TAG, "HDMI resolutions - HRes:" + hdmiInput.getHorizontalRes() + " Vres:" + hdmiInput.getVerticalRes());
    			}
    		}
    	}
    	finally
    	{
    		hdmiLock.unlock("handleHdmiInputResolutionEvent for resolution id: " + resolutionId);
    	}
    }
   
    public void handleHdmiOutputChange()
	{
		Log.i(TAG, "handleHdmiOutputChange() - entered");
		refreshOutputResolution();

		// Recheck if HDCP changed
		mForceHdcpStatusUpdate = true;

		//update with current HDMI output resolution information
		sendHdmiOutSyncState();

		if (haveExternalDisplays && Boolean.parseBoolean(hdmiOutput.getSyncStatus()))
		{
			Log.i(TAG, "------ Recreate CresDisplaySurface due to regained HDMI sync ------");
			createCresDisplaySurface();
			Point size = getDisplaySize();
			SetWindowManagerResolution(size.x, size.y, haveExternalDisplays);

			try { Thread.sleep(3000); } catch (Exception e) {}
            Log.i(TAG, "handleHdmiOutputChange(): Restarting Streams ");
			restartStreams(false);

			for (int sessionId = 0; sessionId < NumOfSurfaces; sessionId++)
			{
				// Show AirMedia window if we acquire HDMI output sync
				if ((mAirMedia != null) && userSettings.getAirMediaLaunch(sessionId))
				{
					int width = userSettings.getAirMediaWidth();
					int height = userSettings.getAirMediaHeight();

					if ((width == 0) && (height == 0))
					{
						width = size.x;
						height = size.y;
					}
					Log.i(TAG, "------ Show AirMedia due to regained HDMI sync ------");
					mAirMedia.show(sessionId, userSettings.getAirMediaX(), userSettings.getAirMediaY(), width, height);
				}
			}
		}
		else if (haveExternalDisplays)
		{
			// Stop HDMI preview and stream in when we lose HDMI sync			
			for (int sessionId = 0; sessionId < NumOfSurfaces; sessionId++)
			{
				// Hide AirMedia window if we lose HDMI output sync
				if ((mAirMedia != null) && userSettings.getAirMediaLaunch(sessionId))
				{
					Log.i(TAG, "------ Hide AirMedia due to lost HDMI output ------");
					mAirMedia.hide(sessionId, true, false);
				}
				
				if (userSettings.getMode(sessionId) == DeviceMode.PREVIEW.ordinal() || 
						userSettings.getMode(sessionId) == DeviceMode.STREAM_IN.ordinal() ||
						userSettings.getMode(sessionId) == DeviceMode.WBS_STREAM_IN.ordinal())
				{
					Log.i(TAG, "Stopping stream because lost HDMI output: " + sessionId);
					Stop(sessionId, true);
				}
			}
		}
		Log.i(TAG, "handleHdmiOutputChange() - exited");
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
    				while ( (mIsHdmiOutExternal == false) && (HDMIOutputInterface.readHDCPOutputStatus() == -1) )
    				{
    					try {
    						Thread.sleep(100);
    					} catch (Exception e) { e.printStackTrace(); }
    				}

    				// No need to set HDCP bypass when handled externally
    				if (mIsHdmiOutExternal == false)
    				{
    					//Set bypass high when hdcp is not authenticated on output, if not in force hdcp mode
    				boolean setHDCPBypass = ((userSettings.isHdmiOutForceHdcp() == false) && (HDMIOutputInterface.readHDCPOutputStatus() == 0));
    				HDMIOutputInterface.setHDCPBypass(setHDCPBypass);
    			}
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
		Log.i(TAG, MiscUtils.stringFormat("Setting cameraMode with resolution enum = %d", hdmiInputResolutionEnum));
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
		            Log.i(TAG, "setCameraHelper(): Restarting Streams - on firstrun");
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
        Log.i(TAG, MiscUtils.stringFormat("Setting no video format to %s", String.valueOf(enable)));
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
					{
						if (lastHDMImode == DeviceMode.STREAM_OUT)
							setCameraMode(String.valueOf(CameraMode.StreamOutPaused.ordinal()));
						else
							setCameraMode(String.valueOf(CameraMode.PreviewPaused.ordinal()));
					}
					else
						setCameraMode(String.valueOf(CameraMode.Camera.ordinal()));
				}
			}
		}
		
		// Set hdmi connected states for csio
		sockTask.SendDataToAllClients(MiscUtils.stringFormat("HDMIInputConnectedState=%b", !enable)); //true means hdmi input connected
	}
	
	public void setPauseVideoImage(boolean enable, DeviceMode mode) 
	{	
		int previousCameraMode = readCameraMode();
		if (enable)
		{
			if (mode == DeviceMode.STREAM_OUT)
				setCameraMode(String.valueOf(CameraMode.StreamOutPaused.ordinal()));
			else
				setCameraMode(String.valueOf(CameraMode.PreviewPaused.ordinal()));
		}
		else if ( previousCameraMode == CameraMode.StreamOutPaused.ordinal() || 
				previousCameraMode == CameraMode.PreviewPaused.ordinal() )
			setCameraMode(String.valueOf(CameraMode.Camera.ordinal()));
	}
	
	public void setHDCPErrorImage(boolean enable) 
	{
		String cameraMode = "";
		int previousCameraMode = readCameraMode();
		Log.i(TAG, "setHDCPErrorImage: enable="+enable+"  previousCameraMode="+previousCameraMode);
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
			{
				if (lastHDMImode == DeviceMode.STREAM_OUT)
					setCameraMode(String.valueOf(CameraMode.StreamOutPaused.ordinal()));
				else
					setCameraMode(String.valueOf(CameraMode.PreviewPaused.ordinal()));
			}
			else
				setCameraMode(String.valueOf(CameraMode.Camera.ordinal()));
		}
	}
	
	public void sendHDCPLocalOutputBlanking(boolean enable) 
	{
		String msg = (enable ? "true" : "false");
		sockTask.SendDataToAllClients(MiscUtils.stringFormat("HDCP_BLANK_HDMI_OUTPUT=%s", msg));
		
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
			Log.i(TAG, "Writing " + mode + " to camera mode file");
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
	      	} catch (Exception e) {Log.i(TAG, "Exception in saveUserSettings" + e.toString()); }
	      	
	      	// Only update userSettings if it has changed
	      	if (serializedClass.compareToIgnoreCase(currentUserSettings) != 0)
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
	      	
		      	Log.i(TAG, "Saved userSettings to disk");
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
					Thread.sleep(1000 * 5);
				}
				catch (Exception e)
				{
					Log.i(TAG, "Save settings task exception" + e.toString());
				}				
			}
		}
	}

	private void recomputeHash() {
		sockTask.SendDataToAllClients(MiscUtils.stringFormat("RECOMPUTEHASH=%s", savedSettingsFilePath));
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
				Log.i(TAG, "checkHdcpStatus(): InputHdcpStatus prev: " + mHDCPInputStatus + " cur: " + currentHDCPInputStatus +
						", OutputHdcpStatus prev: " + mHDCPOutputStatus + " cur: " + currentHDCPOutputStatus +
						", HDCPExternalStatus=" + mHDCPExternalStatus + ", forceHdcpStatusUpdate=" + mForceHdcpStatusUpdate + ", HDCPEncryptStatus=" + mHDCPEncryptStatus);
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
				sockTask.SendDataToAllClients(MiscUtils.stringFormat("%s=%b", "HDMIIN_SOURCEHDCPACTIVE", true));
				sockTask.SendDataToAllClients(MiscUtils.stringFormat("%s=%d", "HDMIIN_SOURCEHDCPSTATE", 57));
			}
			else
			{
				sockTask.SendDataToAllClients(MiscUtils.stringFormat("%s=%b", "HDMIIN_SOURCEHDCPACTIVE", false));
				if (Boolean.parseBoolean(hdmiInput.getSyncStatus()) == true) //Valid input present
					sockTask.SendDataToAllClients(MiscUtils.stringFormat("%s=%d", "HDMIIN_SOURCEHDCPSTATE", 0));
				else
					sockTask.SendDataToAllClients(MiscUtils.stringFormat("%s=%d", "HDMIIN_SOURCEHDCPSTATE", 58));
			}
		}
		//Send output feedbacks
		// Check if HDMI content is visible on screen
		boolean hdmiContentVisible = false;
		for (int sessionId = 0; sessionId < NumOfSurfaces; sessionId++)
		{
			if ((userSettings.getMode(sessionId) == DeviceMode.STREAM_OUT.ordinal()) || 
					(userSettings.getMode(sessionId) == DeviceMode.PREVIEW.ordinal() &&
					userSettings.getUserRequestedStreamState(sessionId) == StreamState.STARTED))
			{
				hdmiContentVisible = true;
				break;
			}
		}
		
		// If force HDCP is enabled, blank output if not authenticated
		// If force HDCP is disabled, blank output if not authenticated and input is authenticated
		if (((mHDCPInputStatus == true && hdmiContentVisible == true) || (userSettings.isHdmiOutForceHdcp() == true)) && ((mHDCPOutputStatus == false) && (mHDCPExternalStatus == false)))
			sockTask.SendDataToAllClients(MiscUtils.stringFormat("%s=%b", "HDMIOUT_DISABLEDBYHDCP", true));
		// The below case is transmitter which is streaming protected content but can't display on loopout
		else if (((mHDCPInputStatus == true && hdmiContentVisible == true) && mHDCPEncryptStatus == true && mHDCPOutputStatus == false) && (mIgnoreHDCP == false))	
			sockTask.SendDataToAllClients(MiscUtils.stringFormat("%s=%b", "HDMIOUT_DISABLEDBYHDCP", true));
		// The below case is receiver which is receiving protect stream but can't display on output
		else if ((mTxHdcpActive == true && mHDCPEncryptStatus == true && mHDCPOutputStatus == false) && (mIgnoreHDCP == false))
			sockTask.SendDataToAllClients(MiscUtils.stringFormat("%s=%b", "HDMIOUT_DISABLEDBYHDCP", true));
		else
			sockTask.SendDataToAllClients(MiscUtils.stringFormat("%s=%b", "HDMIOUT_DISABLEDBYHDCP", false));
	}
	
	public void setExternalHdcpStatus(int hdcpStatus)
	{
		if (mIsHdmiOutExternal == false)
		{
			mIsHdmiOutExternal = true;
			// call restart streams just in case we missed the starts because of mIsHdmiOutExternal flag
            Log.i(TAG, "Restarting Streams - mIsHdmiOutExternal");
			restartStreams(false);
		}
		
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
		Log.i(TAG, "setExternalHdcpStatus(): mHDCPExternalStatus="+mHDCPExternalStatus);
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
					synchronized (mDisplayChangedLock) 
					{
						// Bug 135322: Need to sleep after powercycle for Mercury because of window became corrupted (size 0 zorder 0)
						if (haveExternalDisplays && MiscUtils.readStringFromDisk(mercuryHdmiOutWaitFilePath).compareTo("1") != 0)
						{
							MiscUtils.writeStringToDisk(mercuryHdmiOutWaitFilePath, "1");
							try { Thread.sleep(10000);} catch (InterruptedException e) {}
						}
						DisplayManager dm = 
								(DisplayManager) getSystemService(DISPLAY_SERVICE);
						Display dispArray[] = dm.getDisplays();
						if ( (haveExternalDisplays && (dispArray.length > 1) && (displayId == dispArray[1].getDisplayId())) ||
								(!haveExternalDisplays && (displayId == dispArray[0].getDisplayId())) )
						{
							Log.i(TAG, "HDMI Output display has been added");
							handleHdmiOutputChange();
						}
					}
				}
			}).start();
		}

		@Override
		public void onDisplayChanged(int displayId)
		{
			// TODO: I am not seeing this get called, so currently it is unimplemented
			Log.i(TAG, "HDMI Output display has changed");
		}

		@Override
		public void onDisplayRemoved(final int displayId)
		{
			new Thread(new Runnable() {
				public void run() { 
					synchronized (mDisplayChangedLock) 
					{
						DisplayManager dm = 
								(DisplayManager) getSystemService(DISPLAY_SERVICE);
						Display dispArray[] = dm.getDisplays();
						if ( (haveExternalDisplays && (dispArray.length == 1) && (displayId != dispArray[0].getDisplayId())) ||
								(dispArray.length == 0) )
						{
							Log.i(TAG, "HDMI Output display has been removed");
							handleHdmiOutputChange();
						}
					}
				}
			}).start();
		}		
	}	
	
	public Point getDisplaySize()
	{
		Point retVal = new Point();
		WindowManager wm = null;

		if (haveExternalDisplays)
		{
			DisplayManager dm = (DisplayManager) getApplicationContext().getSystemService(Context.DISPLAY_SERVICE);
			if (dm != null){
				Display dispArray[] = dm.getDisplays();
				if (dispArray.length>1){
					Context displayContext = getApplicationContext().createDisplayContext(dispArray[1]);
					wm = (WindowManager)displayContext.getSystemService(Context.WINDOW_SERVICE);
				}
				else
				    Log.e(TAG, "Unable to query second display size, length <= 1");
			}
			else
				Log.e(TAG, "Unable to query second display size, dm == null");
		}
		else
		{
			wm = (WindowManager) this.getSystemService(Context.WINDOW_SERVICE);
		    Log.d(TAG, "Device does not have second display, using primary");
		}

		if (wm != null)
		{
			Display display = wm.getDefaultDisplay();
			display.getSize(retVal);
		}
		else
		{
			retVal.set(1920, 1080);
		    Log.e(TAG, "Could not find display - setting size to 1920x1080");
		}
		return retVal;
	}
	
	public abstract class CustomizedTypeAdapterFactory<C>
	implements TypeAdapterFactory {
		private final Class<C> customizedClass;

		public CustomizedTypeAdapterFactory(Class<C> customizedClass) {
			this.customizedClass = customizedClass;
		}

		@SuppressWarnings("unchecked") // we use a runtime check to guarantee that 'C' and 'T' are equal
		public final <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
			return type.getRawType() == customizedClass
					? (TypeAdapter<T>) customizeMyClassAdapter(gson, (TypeToken<C>) type)
							: null;
		}

		private TypeAdapter<C> customizeMyClassAdapter(Gson gson, TypeToken<C> type) {
			final TypeAdapter<C> delegate = gson.getDelegateAdapter(this, type);
			final TypeAdapter<JsonElement> elementAdapter = gson.getAdapter(JsonElement.class);
			return new TypeAdapter<C>() {
				@Override public void write(JsonWriter out, C value) throws IOException {
					JsonElement tree = delegate.toJsonTree(value);
					beforeWrite(value, tree);
					elementAdapter.write(out, tree);
				}
				@Override public C read(JsonReader in) throws IOException {
					JsonElement tree = elementAdapter.read(in);
					afterRead(tree);
					return delegate.fromJsonTree(tree);
				}
			};
		}

		/**
		 * Override this to muck with {@code toSerialize} before it is written to
		 * the outgoing JSON stream.
		 */
		protected void beforeWrite(C source, JsonElement toSerialize) {
		}

		/**
		 * Override this to muck with {@code deserialized} before it parsed into
		 * the application type.
		 */
		protected void afterRead(JsonElement deserialized) {
		}
	}
	
	private class LenientTypeAdapterFactory extends CustomizedTypeAdapterFactory<UserSettings> {
	    private LenientTypeAdapterFactory() {
	        super(UserSettings.class);
	    }

	    // Protect against AirMediaLaunch going from single bool to array
	    @Override protected void afterRead(JsonElement deserialized) {
	    	try {

	    		JsonArray jsonArray = deserialized.getAsJsonObject().get("airMediaLaunch").getAsJsonArray();
	    	}
	    	catch (Exception e)
	    	{
	    		Log.i(TAG, "Failed to deserialize airMediaLaunch");
	    		deserialized.getAsJsonObject().remove("airMediaLaunch");
	    	}
	    }
	}
	
	protected class videoDimensions {
		public int videoWidth;
		public int videoHeight;
		public videoDimensions(int videoWidth, int videoHeight)
		{
			this.videoWidth = videoWidth;
			this.videoHeight = videoHeight;
		}
	}

}
