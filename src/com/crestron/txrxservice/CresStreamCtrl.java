package com.crestron.txrxservice;

import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
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
import android.widget.Toast;
import android.content.Context;
import android.os.AsyncTask;
import android.provider.MediaStore.Files;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.media.AudioManager;
import android.view.Surface;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.Surface.PhysicalDisplayInfo;
import android.view.SurfaceHolder.Callback;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.Camera;
import android.app.Notification;

import com.crestron.txrxservice.CresStreamCtrl.StreamState;
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
    StringTokenizer tokenizer;
    
    public static boolean useGstreamer = true;
    StreamIn streamPlay;
    BroadcastReceiver hpdEvent = null;
    BroadcastReceiver resolutionEvent = null;
    BroadcastReceiver hdmioutResolutionChangedEvent = null;

    public UserSettings userSettings;
    AudioManager amanager;
    TCPInterface sockTask;
   
    HDMIInputInterface hdmiInput;
    HDMIOutputInterface hdmiOutput;

    CresDisplaySurface dispSurface;
    
    final int cameraRestartTimout = 1000;//msec
    static int hpdHdmiEvent = 0;
    
    private volatile boolean saveSettingsShouldExit = false;
    public static Object saveSettingsPendingUpdate = new Object();
    public static boolean saveSettingsUpdateArrived = false;

    public final static int NumOfSurfaces = 2;
    public volatile boolean restartStreamsOnStart = false;
    String TAG = "TxRx StreamCtrl";
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
    public volatile boolean mMediaServerCrash = false;
    public volatile boolean mDucatiCrash = false;
    public volatile boolean mIgnoreAllCrash = false;
    private FileObserver mediaServerObserver;
    private FileObserver ravaModeObserver;
    private Thread monitorCrashThread;
    private boolean mHDCPOutputStatus = false;
    private boolean mHDCPInputStatus = false;
    private boolean mIgnoreHDCP = false; //FIXME: This is for testing
    public CountDownLatch streamingReadyLatch = new CountDownLatch(1);

    enum DeviceMode {
        STREAM_IN,
        STREAM_OUT,
        PREVIEW
    }
    
    enum CameraMode {
    	Camera(0),
    	Paused(1),
    	NoVideo(2),
    	HDCPStreamError(3),
    	HDCPAllError(4);
    	
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
    
    private final ReentrantLock stopStartLock			= new ReentrantLock(true); // fairness=true, makes lock ordered
    private final ReentrantLock hdmiLock				= new ReentrantLock(true);
    private final ReentrantLock streamStateLock 		= new ReentrantLock(true);
    private final ReentrantLock saveSettingsLock 		= new ReentrantLock(true);
    public final ReentrantLock restartLock	 			= new ReentrantLock(true);
    
    /**
     * Force the service to the foreground
     */
    public void ForceServiceToForeground()
    {
        Notification note = new Notification( 0, null, System.currentTimeMillis() );
        note.flags |= Notification.FLAG_NO_CLEAR;
        startForeground( 42, note );
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
			    	runOnUiThread(new Runnable() {
			  		     @Override
			  		     public void run() {
			  		    	ForceServiceToForeground();
			  		     }
			    	});
			    	try {
			    		Thread.sleep(5000);
			    	} catch (Exception e) {
			    		e.printStackTrace();
			    	}
		    	}
    		}
    	}).start();
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
            
            //Start service connection
            sockTask = new TCPInterface(this);
            sockTask.execute(new Void[0]);  
                        
            RunNotificationThread();
            
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
	                    	if (userSettings.getStreamState(sessionId) != StreamState.STOPPED)
	                    	{
	                    		if (userSettings.getMode(sessionId) == DeviceMode.PREVIEW.ordinal())
	                    			cam_preview.stopPlayback(false);
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
            
            //Input Streamout Config
            if (useGstreamer)
            	streamPlay = new StreamIn(new GstreamIn(CresStreamCtrl.this));
            else
            	streamPlay = new StreamIn(new NstreamIn(CresStreamCtrl.this));

            tokenizer = new StringTokenizer();
            hdmiOutput = new HDMIOutputInterface();
            setHDCPBypass();
            
            boolean wipeOutUserSettings = false;
            boolean useOldUserSettingsFile = false;
            File serializedClassFile = new File (savedSettingsFilePath);
            if (serializedClassFile.isFile())	//check if file exists
            {
            	// File exists deserialize it into userSettings
            	GsonBuilder builder = new GsonBuilder();
                Gson gson = builder.create();
                try
                {
                	String serializedClass = new Scanner(serializedClassFile, "UTF-8").useDelimiter("\\A").next();
                	try {
                		userSettings = gson.fromJson(serializedClass, UserSettings.class);
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
		            	String serializedClass = new Scanner(serializedOldClassFile, "UTF-8").useDelimiter("\\A").next();
		            	try {
		            		userSettings = gson.fromJson(serializedClass, UserSettings.class);
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

            Thread saveSettingsThread = new Thread(new SaveSettingsTask());    	
            saveSettingsThread.start();
            
            hdmiInputDriverPresent = HDMIInputInterface.isHdmiDriverPresent();

        	if (hdmiInputDriverPresent)
        	{
        		Log.d(TAG, "HDMI input driver is present");
        		hdmiInput = new HDMIInputInterface();
        		//refresh resolution on startup
        		hdmiInput.setResolutionIndex(hdmiInput.readResolutionEnum());
        		
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

            // Create a DisplaySurface to handle both preview and stream in
            dispSurface = new CresDisplaySurface(this, windowWidth, windowHeight);
            
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
            	CresCamera.getHdmiInputStatus();
        	}
            //Play Control
            hm = new HashMap();
            hm.put(2/*"PREVIEW"*/, new Command() {
                    public void executeStart(int sessId) {startPreview(sessId); };
                    });
            hm.put(1 /*"STREAMOUT"*/, new Command() {
                    public void executeStart(int sessId) {startStreamOut(sessId); };
                    });
            hm.put(0 /*"STREAMIN"*/, new Command() {
                    public void executeStart(int sessId) {startStreamIn(sessId); };
                    });
            hm2 = new HashMap();
            hm2.put(2/*"PREVIEW"*/, new myCommand() {
                    public void executeStop(int sessId, boolean fullStop) {stopPreview(sessId);};
                    });
            hm2.put(1/*"STREAMOUT"*/, new myCommand() {
                    public void executeStop(int sessId, boolean fullStop) {stopStreamOut(sessId, fullStop);};
                    });
            hm2.put(0/*"STREAMIN"*/, new myCommand() {
                    public void executeStop(int sessId, boolean fullStop) {stopStreamIn(sessId); };
                    });
            hm3 = new HashMap();
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
            
            // Monitor HDCP
            monitorHDCP();
            
        	// Monitor Rava Mode
            monitorRavaMode();
            
            // FIXME: this is a temprorary workaround for testing so that we can ignore HDCP state
            File ignoreHDCPFile = new File ("/data/CresStreamSvc/ignoreHDCP");
            if (ignoreHDCPFile.isFile())	//check if file exists
            	mIgnoreHDCP = true;
            else
            	mIgnoreHDCP = false;
        }
   
    @Override
        public int onStartCommand (Intent intent, int flags, int startId) {
            // TODO Auto-generated method stub
            Log.d(TAG,"S: CresStreamCtrl Started !" );
            return START_STICKY;
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
    	// unstablility and/or crashes can occur if this is not observed
        handler.post(runnable);
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
    	
    	Log.d(TAG, String.format("returned surface holder %s", surfaceHolder.toString()));
    	
    	return surfaceHolder;
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
								Log.i(TAG, "Recovering from Ducati crash!");
								recoverFromCrash();
							}
						}
						
						// Check if mediaserver crashed
						if ((mMediaServerCrash == true) && (mIgnoreAllCrash == false))
						{
							Log.i(TAG, "Recovering from mediaserver crash!");
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
    	CresCamera.mSetHdmiInputStatus = true;
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
    
    private void monitorHDCP() {
    	new Thread(new Runnable() {
    		@Override
    		public void run() {
    			// Poll input and output HDCP states once a second
    			while (!Thread.currentThread().isInterrupted())
    			{
    				checkHDCPStatus();
    				sendHDCPFeedbacks();
    				try {
    					Thread.sleep(1000);
    				} catch (Exception e) { e.printStackTrace(); }
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
        ravaModeObserver = new FileObserver(ravaModeFilePath, FileObserver.CLOSE_WRITE) {						
			@Override
			public void onEvent(int event, String path) {
				synchronized (ravaModeLock)
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
			            ravaMode = Integer.parseInt(text.toString().trim());
			        }catch (IOException e) {}
					
					// Stop/Start all audio
			        if (ravaMode == 1)
			        	setAudioDropFlag(true);
			        else if (ravaMode == 0)
			        	setAudioDropFlag(false);
			        else
			        	Log.e(TAG, String.format("Invalid rava mode detected, mode = %d", ravaMode));
			        
			        // Write Audio done
			        Writer writer = null;
	
		      		File audioDropDoneFile = new File(audioDropDoneFilePath);
		      		if (audioDropDoneFile.isFile())	//check if file exists
		            {
				      	try 
				      	{
		            		writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(audioDropDoneFilePath), "utf-8"));
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
    
    private void setAudioDropFlag(boolean enabled)
    {
    	userSettings.setRavaMode(enabled);    		
    	
    	for(int sessionId = 0; sessionId < NumOfSurfaces; sessionId++)
    	{
    		if (userSettings.getUserRequestedStreamState(sessionId) == StreamState.STARTED)
    		{
    			if (userSettings.getMode(sessionId) == DeviceMode.PREVIEW.ordinal())
    			{
    				if (enabled)
    					cam_preview.stopAudio();
    				else
    					cam_preview.startAudio();
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
    	final CountDownLatch latch = new CountDownLatch(1);
    	
    	// Skip Stream in is when we need to only restart camera modes i.e. when resolution changes
    	new Thread(new Runnable() {
    		public void run() {	
    			restartLock.lock();
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
			        		
			    	    	stopStartLock.lock();
			    	    	Log.d(TAG, "Stop : Lock");
			    	    	try
			    	    	{
			    	    		cam_streaming.stopConfidencePreview(sessionId);
			    	    	}
				    		finally
					    	{
					    		Log.d(TAG, "Stop : Unlock");
					    		stopStartLock.unlock();
					    	}
			    	    	
			    	    	// Clear crash flags after stop completes but before start
		            		writeDucatiState(1);
		            		mMediaServerCrash = false;
			    	    	
			    	    	stopStartLock.lock();
			    	    	Log.d(TAG, "Start : Lock");
			    	    	try
			    	    	{
			    	    		cam_streaming.startConfidencePreview(sessionId);
			    	    	}
				    		finally
					    	{
					    		Log.d(TAG, "Start : Unlock");
					    		stopStartLock.unlock();
					    	}            	
			            } 
			        	else if (userSettings.getUserRequestedStreamState(sessionId) == StreamState.STARTED)
			            {
			        		restartStreamsCalled = true;
			        		
			            	//Avoid starting confidence mode when stopping stream out
		            		Stop(sessionId, true);
		            		
		            		// Clear crash flags after stop completes but before start
		            		writeDucatiState(1);
		            		mMediaServerCrash = false;
		            		
			            	Start(sessionId);
			            }                       
			        }
			        
			        // Clear crash flags if restart streams is not needed, otherwise no one will clear the flag
			        if (restartStreamsCalled == true)
			        {
	            		writeDucatiState(1);
	            		mMediaServerCrash = false;
			        }
	    		}
	    		finally
		    	{
		    		Log.i(TAG, "RestartLock : Unlock");
		    		restartLock.unlock();
		    	}
    	    	
    	    	latch.countDown();
    		}
		}).start();
    	
    	//make call synchronous by waiting for completion before returning
    	try { latch.await(); }
    	catch (InterruptedException ex) { ex.printStackTrace(); }  
    }
    
    public void setDeviceMode(int mode, int sessionId)
    {
        Log.d(TAG, " setDeviceMode "+ mode);
        int prevMode = userSettings.getMode(sessionId);
        userSettings.setMode(mode, sessionId);
        
        // If hdmi input driver is present allow all 3 modes, otherwise only allow stream in mode
        // Only if mode actually changed
        if ((mode != prevMode) && ((hdmiInputDriverPresent || (mode == DeviceMode.STREAM_IN.ordinal())))) 
        {
        	// Since this is a user request, mark as stopped requested if mode changes
        	userSettings.setUserRequestedStreamState(StreamState.STOPPED, sessionId);
        	
            if (userSettings.getStreamState(sessionId) != StreamState.STOPPED)
                hm2.get(prevMode).executeStop(sessionId, false);
 
            if (mode == DeviceMode.STREAM_OUT.ordinal())
            {
            	// we want confidence image up for stream out, until streamout is actually started
            	stopStartLock.lock();
            	Log.d(TAG, "Start : Lock");
                try
                {
                	updateWindow(sessionId);
                    showPreviewWindow(sessionId);
                    invalidateSurface();
                	cam_streaming.startConfidencePreview(sessionId);
                } finally
                {
                	stopStartLock.unlock();
                	Log.d(TAG, "Start : Unlock");
                }
            	restartRequired[sessionId] = true;
            }
            
            // Since we are switching device modes, clear out stream url (Bug 100790)
            userSettings.setServerUrl("", sessionId);
            sockTask.SendDataToAllClients("STREAMURL=");
        }
    }
    
    public void setUseGstreamer(boolean flag)
    {
    	if (flag != useGstreamer)
    	{
	    	for (int sessionId = 0; sessionId < NumOfSurfaces; sessionId++)
	    	{
	    		if ((userSettings.getMode(sessionId) == DeviceMode.STREAM_IN.ordinal())
	    				&& (userSettings.getStreamState(sessionId) != StreamState.STOPPED))
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
        Log.d(TAG, "updateWH : Lock");
        stopStartLock.lock();
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
			                dispSurface.UpdateDimensions(userSettings.getW(sessionId),
			                		userSettings.getH(sessionId), sessionId);
			                latch.countDown();
		       		     }
	            	});	            	

	            	try { latch.await(); }
	            	catch (InterruptedException ex) { ex.printStackTrace(); }  
            	}
            	else
            		dispSurface.UpdateDimensions(userSettings.getW(sessionId),
	                		userSettings.getH(sessionId), sessionId);
            }
        }
        finally
        {
            stopStartLock.unlock();
            Log.d(TAG, "updateWH : Unlock");
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
		                dispSurface.forceLayoutInvalidation();
		                latch.countDown();
	       		     }
        		});
        		try { latch.await(); }
            	catch (InterruptedException ex) { ex.printStackTrace(); }  
        	}
        	else
        		dispSurface.forceLayoutInvalidation();        		
        }
    }
    
    public void updateXY(final int sessionId)
    {
    	
    	Log.d(TAG, "updateXY : Lock");
    	stopStartLock.lock();
    	
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
	        		try { latch.await(); }
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
        	stopStartLock.unlock();
        	Log.d(TAG, "updateXY : Unlock");
    	}
    }

    public void readResolutionInfo(String hdmiInputResolution){
    	if (hdmiInputDriverPresent)
    		hdmiInput.updateResolutionInfo(hdmiInputResolution);
    }
    
    void refreshInputResolution()
    {
        Log.i(TAG, "Refresh resolution info");
    	
    	//HDMI In
        String hdmiInputResolution = HDMIInputInterface.getHdmiInResolutionSysFs();

    	readResolutionInfo(hdmiInputResolution);
    	hdmiInput.setSyncStatus();
    }

	public void refreshOutputResolution() {
		//HDMI Out
        PhysicalDisplayInfo hdmiOutputResolution = new PhysicalDisplayInfo();
        Surface.getDisplayInfo(Surface.getBuiltInDisplay(Surface.BUILT_IN_DISPLAY_ID_MAIN), hdmiOutputResolution);
        
    	Log.i(TAG, "HDMI Out Resolution " + hdmiOutputResolution.width + " "
    			+ hdmiOutputResolution.height + " "
    			+ Math.round(hdmiOutputResolution.refreshRate));
    	
    	hdmiOutput.setSyncStatus();
        hdmiOutput.setHorizontalRes(Integer.toString(hdmiOutputResolution.width));
        hdmiOutput.setVerticalRes(Integer.toString(hdmiOutputResolution.height));
        hdmiOutput.setFPS(Integer.toString(Math.round(hdmiOutputResolution.refreshRate)));
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
    	cam_preview.setVolume(volume);
    }
    
    private void setStreamInVolume(int volume, int sessionId)
    {
    	streamPlay.setVolume(volume, sessionId);
    }
    
    public void setStreamVolume(int volume) 
    {
    	//Volume of -1 means setting mute
    	//If user sets volume while in muted mode, save new volume in previousVolume
    	if ((userSettings.isAudioMute() == true) && (volume != -1))
    	{
			userSettings.setUserRequestedVolume(volume);
    	}
    	else
    	{
    		// If Audio is unmuted always setUserRequested volume to new volume value
    		if (userSettings.isAudioUnmute() == true)
    			userSettings.setUserRequestedVolume(volume);
    			
    		if (volume == -1)
    			volume = 0;
    		
    		userSettings.setVolume(volume);
    		
	    	for (int sessionId = 0; sessionId < NumOfSurfaces; sessionId++)
	    	{
	    		if (userSettings.getMode(sessionId) == DeviceMode.PREVIEW.ordinal())
	    			setPreviewVolume(volume);
	    		else if (userSettings.getMode(sessionId) == DeviceMode.STREAM_IN.ordinal())
	    			setStreamInVolume(volume, sessionId);
	    		else if (userSettings.getMode(sessionId) == DeviceMode.STREAM_OUT.ordinal())
	    		{
	    			if (userSettings.getStreamState(sessionId) == StreamState.CONFIDENCEMODE)
	    				setPreviewVolume(volume);
	    			else
	    				setSystemVolume(volume);
	    		}
	    	}
    	}
    }

    public void setStreamMute()
    {
    	// volume cached in userSettings will store the current set value on the device
    	// userRequestedVolume in userSettings will store the last value the user set on the device
    	// We will always feedback the userRequestedVolume
    	if (userSettings.isAudioUnmute())
	    	userSettings.setUserRequestedVolume();
    	
    	userSettings.setAudioMute(true);
        userSettings.setAudioUnmute(false);
        
        setStreamVolume(-1);
        sockTask.SendDataToAllClients("AUDIO_UNMUTE=false");
    }
    
    public void setStreamUnMute()
    {
    	userSettings.setAudioMute(false);
        userSettings.setAudioUnmute(true);
    	setStreamVolume(userSettings.getUserRequestedVolume());
    	sockTask.SendDataToAllClients("AUDIO_MUTE=false");
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
    	sockTask.SendDataToAllClients("VBITRATE=" + String.valueOf(streamPlay.getStreamInBitrate()));

        //processReceivedMessage()
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

    public void SendStreamState(StreamState state, int sessionId)
    {
    	Log.d(TAG, "StreamState : Lock");
    	streamStateLock.lock();
    	try
    	{
        	userSettings.setStreamState(state, sessionId);
        	TriggerSettingsSave();
	        StringBuilder sb = new StringBuilder(512);
	        String streamStateText = "STREAMSTATE" + String.valueOf(sessionId);
	        
	        sb.append(streamStateText + "=").append(state.getValue());
	        sockTask.SendDataToAllClients(sb.toString());
    	}
        finally 
    	{
        	streamStateLock.unlock();
        	Log.d(TAG, "StreamState : Unlock");
    	}
    }

    //Ctrls
    public void Start(int sessionId)
    {
    	if (userSettings.getStreamState(sessionId) != StreamState.STARTED)
    	{	    	
	    	stopStartLock.lock();
	    	Log.d(TAG, "Start : Lock");
	    	try
	    	{
	    		SendStreamState(StreamState.CONNECTING, sessionId);
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
	    		stopStartLock.unlock();
	        	Log.d(TAG, "Start : Unlock");
	    	}
    	}
    }

    public void Stop(int sessionId, boolean fullStop)
    {
    	//csio will send service full stop when it does not want confidence mode started
    	if ((userSettings.getStreamState(sessionId) != StreamState.STOPPED) && (userSettings.getStreamState(sessionId) != StreamState.CONFIDENCEMODE))
    	{
	    	stopStartLock.lock();
	    	Log.d(TAG, "Stop : Lock");
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
	    		stopStartLock.unlock();
	    		Log.d(TAG, "Stop : Unlock");
	    	}
    	}
    	else
    		SendStreamState(StreamState.STOPPED, sessionId);
    }

    public void Pause(int sessionId)
    {
    	if ((userSettings.getStreamState(sessionId) != StreamState.PAUSED) && (userSettings.getStreamState(sessionId) != StreamState.STOPPED))
    	{
	    	Log.d(TAG, "Pause : Lock");
	    	stopStartLock.lock();
	    	try
	    	{
		        pauseStatus="true";
		    	playStatus="false";
		    	stopStatus="false";
		    	restartRequired[sessionId]=false;
		        hm3.get(userSettings.getMode(sessionId)).executePause(sessionId);
		        //hm3.get(device_mode).executePause();
		        // Device state will be set in pause callback
	    	}
	        finally
	    	{
	    		Log.d(TAG, "Pause : Unlock");
	    		stopStartLock.unlock();
	    	}
    	}
    }
    //StreamOut Ctrl & Config
    public void setMulticastIpAddress(String ip, int sessId){
        if(ip!=null)
        	userSettings.setMulticastAddress(ip, sessId);
    } 
    
    public void sendInitiatorFbAddress(String ip, int sessId){
    	sockTask.SendDataToAllClients("INITIATOR_ADDRESS=" + ip);
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
    
    public void setVbitRate(int vbr, int sessId){
    	userSettings.setBitrate(vbr, sessId);	
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
            file = "/live.sdp";

            url.append(proto).append("://").append(l_ipaddr).append(":").append(port).append(file);
            Log.d(TAG, "URL is "+url.toString());
        }else
            url.append(userSettings.getServerUrl(sessId));

        return url.toString();
    }

    public void startStreamOut(int sessId)
    {
        StringBuilder sb = new StringBuilder(512);
        
        // we are starting to streamout so stop confidence preview (unless resuming from pause)
        if (cam_streaming.getConfidencePreviewStatus() == true)
        	cam_streaming.stopConfidencePreview(sessId);
        
        updateWindow(sessId);
        showPreviewWindow(sessId);
        out_url = createStreamOutURL(sessId);
        userSettings.setServerUrl(out_url, sessId);

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
		    	try { latch.await(); }
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
		    	try { latch.await(); }
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
    private void hideStreamInWindow(int sessId)
    {
        Log.d(TAG, " streamin Window hidden " + sessId);
        hideWindow(sessId);
    }

    private void showStreamInWindow(int sessId)
    {
        Log.d(TAG, "streamin Window  showing " + sessId);
        showWindow(sessId);
    }

    public void EnableTcpInterleave(int sessionId){
        Log.d(TAG, " EnableTcpInterleave");
        streamPlay.setRtspTcpInterleave(true, sessionId);
    }

    public void setStreamInUrl(String ap_url, int sessionId)
    {
        userSettings.setServerUrl(ap_url, sessionId);

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
    	userSettings.setServerUrl(ap_url, sessionId);
    	
    	if (userSettings.getMode(sessionId) == DeviceMode.STREAM_OUT.ordinal())
    		sockTask.SendDataToAllClients(String.format("STREAMURL%d=%s", sessionId, createStreamOutURL(sessionId)));
    }

    public String getStreamUrl(int sessId)
    {
        //return out_url;
        return userSettings.getServerUrl(sessId);
    }

    public void startStreamIn(int sessId)
    {
        updateWindow(sessId);
        showStreamInWindow(sessId);
        invalidateSurface();
        streamPlay.onStart(sessId);
        //Toast.makeText(this, "StreamIN Started", Toast.LENGTH_LONG).show();
    }

	/**
	 * 
	 */
    public void updateWindow(int sessId) {
        updateWH(sessId);
        updateXY(sessId);
    }

    public void stopStreamIn(int sessId)
    {
        streamPlay.onStop(sessId);
        //Toast.makeText(this, "StreamIN Stopped", Toast.LENGTH_LONG).show();
        hideStreamInWindow(sessId);
    }

    public void pauseStreamIn(int sessId)
    {
        streamPlay.onPause(sessId);
    }

    //Preview 
    public void startPreview(int sessId)
    {
        updateWindow(sessId);
        showPreviewWindow(sessId);
        cam_preview.setSessionIndex(sessId);
        invalidateSurface();
        cam_preview.startPlayback(false);
        //Toast.makeText(this, "Preview Started", Toast.LENGTH_LONG).show();
    }

    public void stopPreview(int sessId)
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
    
    public void pausePreview(int sessId)
    {
    	cam_preview.setSessionIndex(sessId);
        cam_preview.pausePlayback();
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
    	sockTask.SendDataToAllClients("KillMePlease=true");
    }
    
    public void RecoverMediaServer() {
    	Log.e(TAG, "Fatal error, kill mediaserver!");
    	sockTask.SendDataToAllClients("KillMediaServer=true");
    }
    
    public void stopOnIpAddrChange(){
	Log.e(TAG, "stopping on device IP Address Change...!");
	try {
	    for (int sessionId = 0; sessionId < NumOfSurfaces; sessionId++)
	    {
	        if (userSettings.getStreamState(sessionId) != StreamState.STOPPED)
	        {
	               if (userSettings.getMode(sessionId) == DeviceMode.STREAM_IN.ordinal())
	           	    streamPlay.onStop(sessionId);
	               else if (userSettings.getMode(sessionId) == DeviceMode.STREAM_OUT.ordinal())
	           	    cam_streaming.stopRecording(true);//false
	        }
	    }
	}
	catch (Exception e) { 
	    Log.e(TAG, "Failed to stop streams on IP Addr Change");
	    e.printStackTrace(); 
	}
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
				                	setCameraAndRestartStreams(resolutionId); //we need to restart streams for resolution change		                	
			                        
				                    int prevResolutionIndex = hdmiInput.getResolutionIndex();
				                    hdmiInput.setResolutionIndex(resolutionId);
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
        	                		setNoVideoImage(true);
	        	                	sendHdmiInSyncState();
	        	                    int i = paramAnonymousIntent.getIntExtra("evs_hdmi_hdp_id", -1);
	        	                    Log.i(TAG, "Received hpd broadcast ! " + i);
	        	                    hpdHdmiEvent = 1;
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
		            		Log.d(TAG, "receiving intent!!!!");
		                	
		                    int i = paramAnonymousIntent.getIntExtra("evs_hdmiout_resolution_changed_id", -1);
		                    Log.i(TAG, "Received hdmiout resolution changed broadcast ! " + i);
		                    PhysicalDisplayInfo hdmiOutputResolution = new PhysicalDisplayInfo();
		                    Surface.getDisplayInfo(Surface.getBuiltInDisplay(Surface.BUILT_IN_DISPLAY_ID_MAIN), hdmiOutputResolution);
		                    
							Log.i(TAG, "HDMI Output resolution " + hdmiOutputResolution.width + " "
									+ hdmiOutputResolution.height + " "
									+ Math.round(hdmiOutputResolution.refreshRate));
							
							//Set bypass mode when output HDMI is not HDCP authenticated
							if (i != 0)
							{
								setHDCPBypass();
							}
							
							// Recheck if HDCP changed
							checkHDCPStatus();
							sendHDCPFeedbacks();
		
		                    //update HDMI output
							hdmiOutput.setSyncStatus();		
					        hdmiOutput.setHorizontalRes(Integer.toString(hdmiOutputResolution.width));
					        hdmiOutput.setVerticalRes(Integer.toString(hdmiOutputResolution.height));
					        hdmiOutput.setFPS(Integer.toString(Math.round(hdmiOutputResolution.refreshRate)));
					        hdmiOutput.setAspectRatio();
					        
					        //update with current HDMI output resolution information
					        sendHdmiOutSyncState();
		                }
		            }
            	}).start();
            }
        };
        IntentFilter hdmioutResolutionIntentFilter = new IntentFilter("evs.intent.action.hdmi.HDMIOUT_RESOLUTION_CHANGED");
        registerReceiver(hdmioutResolutionChangedEvent, hdmioutResolutionIntentFilter);
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
	}
	
	private void sendHdmiOutSyncState() 
	{
		sockTask.SendDataToAllClients("HDMIOUT_SYNC_DETECTED=" + hdmiOutput.getSyncStatus());
		sockTask.SendDataToAllClients("HDMIOUT_HORIZONTAL_RES_FB=" + hdmiOutput.getHorizontalRes());
		sockTask.SendDataToAllClients("HDMIOUT_VERTICAL_RES_FB=" + hdmiOutput.getVerticalRes());
		sockTask.SendDataToAllClients("HDMIOUT_FPS_FB=" + hdmiOutput.getFPS());
		sockTask.SendDataToAllClients("HDMIOUT_ASPECT_RATIO=" + hdmiOutput.getAspectRatio());
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
    			
				HDMIOutputInterface.setHDCPBypass(HDMIOutputInterface.readHDCPOutputStatus() == 0); //Set bypass high when hdcp is not authenticated on output
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
		//Set ignore restart to true if you want to set camera mode but do not want to restart any streams
		boolean validResolution = (hdmiInput.getHorizontalRes().startsWith("0") != true) && (hdmiInput.getVerticalRes().startsWith("0")!= true) && (hdmiInputResolutionEnum != 0);
    	if (validResolution == true)
    	{
    		CresCamera.mSetHdmiInputStatus = true;
    		
    		if (ignoreRestart == false)
    		{
    			if (sockTask.firstRun == false) // makes sure that csio is up so as restart streams before all information is received from platform
    			{
		    		mIgnoreAllCrash = true;
		    		restartStreams(true); //true because we do not need to restart stream in streams
		    		mIgnoreAllCrash = false;
    			}
    		}

   			setNoVideoImage(false);
   			checkHDCPStatus();
   			sendHDCPFeedbacks();
		 }			                
        else
        {
        	setNoVideoImage(true);
        }
	}
	
	public void setNoVideoImage(boolean enable) 
	{
		String cameraMode = "";
		int previousCameraMode = readCameraMode();
		if (enable)
			setCameraMode(String.valueOf(CameraMode.NoVideo.ordinal()));
		else if (previousCameraMode == CameraMode.NoVideo.ordinal())
		{
			if (Boolean.parseBoolean(pauseStatus) == true)
				setCameraMode(String.valueOf(CameraMode.Paused.ordinal()));
			else
				setCameraMode(String.valueOf(CameraMode.Camera.ordinal()));
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
			if (mHDCPOutputStatus == true)
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
	
	private void setCameraMode(String mode) 
	{
		Writer writer = null;
		try 
      	{
			writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(cameraModeFilePath), "utf-8"));
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
	
	private int readCameraMode() 
	{
		int cameraMode = 0;
		File cameraModeFile = new File (cameraModeFilePath);
        if (cameraModeFile.isFile())	//check if file exists
        {			
	    	try {
	    		String serializedCameraMode = new Scanner(cameraModeFile, "UTF-8").useDelimiter("\\A").next();
	    		cameraMode = Integer.parseInt(serializedCameraMode.trim());
	    	} catch (Exception ex) {
	    		Log.e(TAG, "Failed to read cameraMode: " + ex);
			}
        }
		return cameraMode;	
	}
	
	public void saveUserSettings()
	{
		Writer writer = null;
		GsonBuilder builder = new GsonBuilder();
      	Gson gson = builder.create();
      	String serializedClass = gson.toJson(this.userSettings);
      	String currentUserSettings = "";
      	try {
      		currentUserSettings = new Scanner(new File (savedSettingsFilePath), "UTF-8").useDelimiter("\\A").next();
      	} catch (Exception e) { }
      	
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
	      		
	      		// Save new userSettings
	      		saveSettingsLock.lock();
	      		writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(savedSettingsFilePath), "utf-8"));
	    	    writer.write(serializedClass);
	    	    writer.flush();
	    	} 
	      	catch (IOException ex) {
	    	  Log.e(TAG, "Failed to save userSettings to disk: " + ex);
	    	} finally 
	    	{
	    		saveSettingsLock.unlock();
	    		try {writer.close();} catch (Exception ex) {/*ignore*/}
	    	}
      	
	      	Log.d(TAG, "Saved userSettings to disk");
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
	
	public class SaveSettingsTask implements Runnable 
	{
		public void run() {
			while (!saveSettingsShouldExit)
			{
				synchronized (saveSettingsPendingUpdate) {
					while (!saveSettingsUpdateArrived) {
						try {
							saveSettingsPendingUpdate.wait();
						} catch (InterruptedException ex) {ex.printStackTrace();}
					}
					saveSettingsUpdateArrived = false;
					saveUserSettings();					
				}
			}
		}
	}
	
	public void TriggerSettingsSave()
	{
		synchronized ( saveSettingsPendingUpdate ) {  
        	saveSettingsUpdateArrived = true;        
            saveSettingsPendingUpdate.notify();
        }
	}
	
	private void recomputeHash() {
		sockTask.SendDataToAllClients(String.format("RECOMPUTEHASH=%s", savedSettingsFilePath));
	}

	static private Object mHdcpLock = new Object();
	private void checkHDCPStatus() {
		synchronized (mHdcpLock) {
			boolean currentHDCPInputStatus = HDMIInputInterface.readHDCPInputStatus();
			boolean currentHDCPOutputStatus = HDMIOutputInterface.readHDCPOutputStatus() == 1;
			// Only send new status when hdcp status changes for either input or output
			if ((mHDCPInputStatus != currentHDCPInputStatus) || (mHDCPOutputStatus != currentHDCPOutputStatus))
			{
				mHDCPInputStatus = currentHDCPInputStatus;
				mHDCPOutputStatus = currentHDCPOutputStatus;				
				
				if ((mHDCPInputStatus == true) && (mIgnoreHDCP == false))
					setHDCPErrorImage(true);
				else
					setHDCPErrorImage(false);
			}
		}
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
		if ((mHDCPInputStatus == true) && (mHDCPOutputStatus == false))
			sockTask.SendDataToAllClients(String.format("%s=%b", "HDMIOUT_DISABLEDBYHDCP", true));
		else
			sockTask.SendDataToAllClients(String.format("%s=%b", "HDMIOUT_DISABLEDBYHDCP", false));
	}
}
