package com.crestron.txrxservice;

import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.String;
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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

interface Command {
    void executeStart(int sessId);
}

interface myCommand {
    void executeStop(int sessId);
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
    int hpdStateEnabled = 0;
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
    public volatile boolean mIgnoreMediaServerCrash = false;
    private FileObserver mediaServerObserver;

    enum DeviceMode {
        STREAM_IN,
        STREAM_OUT,
        PREVIEW
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

    	// Do not add anything after the Last state
    	LAST(8);
    	
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
    
    private final ReentrantLock threadLock = new ReentrantLock(true); // fairness=true, makes lock ordered
    private final ReentrantLock streamStateLock = new ReentrantLock(true);
    private final ReentrantLock saveSettingsLock = new ReentrantLock(true);

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
                        
            RunNotificationThread();
            
            //Global Default Exception Handler
            final Thread.UncaughtExceptionHandler oldHandler = Thread.getDefaultUncaughtExceptionHandler();

            Thread.setDefaultUncaughtExceptionHandler(
                    new Thread.UncaughtExceptionHandler() {
                    @Override
                    public void uncaughtException(Thread paramThread, Throwable paramThrowable) {
                    //Close Camera   
                    Log.d(TAG,"Global uncaught Exception !!!!!!!!!!!" );
                    cam_preview.stopPlayback(false);
                    cam_streaming.stopRecording(true);//false
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
            
            boolean wipeOutUserSettings = false;
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
                		wipeOutUserSettings = true;
            		}
                }
                catch (Exception ex)
                {
                	Log.e(TAG, "Exception encountered loading userSettings from disk: " + ex);
                	wipeOutUserSettings = true;
                }            	
            }
            else
            {
            	wipeOutUserSettings = true;            	
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

            // Update the resolution information before creating surfaces
            try
            {  
            	windowWidth = Integer.parseInt(hdmiOutput.getHorizontalRes());
	            setWindowSizeW(windowWidth, 0);
	            windowHeight = Integer.parseInt(hdmiOutput.getVerticalRes());
	            setWindowSizeH(windowHeight, 0);
            }
            catch (Exception e)
            {
            	e.printStackTrace();            	
            }

            // Create a DisplaySurface to handle both preview and stream in
            // TODO: Create an array to handle multiple instances 
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
                    public void executeStop(int sessId) {stopPreview(sessId);};
                    });
            hm2.put(1/*"STREAMOUT"*/, new myCommand() {
                    public void executeStop(int sessId) {stopStreamOut(sessId);};
                    });
            hm2.put(0/*"STREAMIN"*/, new myCommand() {
                    public void executeStop(int sessId) {stopStreamIn(sessId); };
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


            //Stub: CSIO Cmd Receiver & TestApp functionality
            sockTask = new TCPInterface(this);
            sockTask.execute(new Void[0]);  
            
            // Monitor mediaserver, if it crashes restart stream
            monitorMediaServer();
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
        cam_streaming.stopRecording(false);
        cam_preview.stopPlayback(false);
        for (int i = 0; i < NumOfSurfaces; i++)
        {
        	streamPlay.onStop(i);
        }
        if (dispSurface != null)
        	dispSurface.RemoveView();
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
				//function start
				if (mIgnoreMediaServerCrash == false)
				{
					Log.e(TAG, "Mediaserver crashed, Restarting Streams!");
					//Sleep for 3000msec for
					//mediaserver to be up
					try {	 
						Thread.sleep(3000, 1);
					}
					catch (InterruptedException ex) 
					{ 
						ex.printStackTrace(); 
					}
					sockTask.restartStreams();
				}
				//function end
			}
		};
		mediaServerObserver.startWatching();
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
            if (userSettings.getStreamState(sessionId) == StreamState.STARTED)
                hm2.get(prevMode).executeStop(sessionId);
            else if (prevMode == DeviceMode.STREAM_OUT.ordinal())
            {
            	// Turn off confidence mode if leaving stream out mode and not streaming out
            	cam_streaming.stopConfidencePreview(sessionId);
            	SendStreamState(StreamState.STOPPED, sessionId);
    			restartRequired[sessionId] = false;
            }
                        
            if (mode == DeviceMode.STREAM_OUT.ordinal())
            {
            	// we want confidence image up for stream out, until streamout is actually started
            	cam_streaming.startConfidencePreview(sessionId);
            	restartRequired[sessionId] = true;
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
	    				&& (userSettings.getStreamState(sessionId) != StreamState.STOPPED))
	    		{
	    			Stop(sessionId);
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
        threadLock.lock();
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
            threadLock.unlock();
            Log.d(TAG, "updateWH : Unlock");
        }

    }
    
    public void updateXY(final int sessionId)
    {
    	
    	Log.d(TAG, "updateXY : Lock");
    	threadLock.lock();
    	
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
        	threadLock.unlock();
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

    public void setStreamMute() //TODO: store in userSettings
    {
        amanager.setStreamMute(AudioManager.STREAM_MUSIC, true);
        StringBuilder sb = new StringBuilder(512);
        userSettings.setAudioMute(true);
        userSettings.setAudioUnmute(false);
        sb.append("AUDIO_UNMUTE=false");
        sockTask.SendDataToAllClients(sb.toString());
    }
    
    public void setStreamUnMute()//TODO: store in userSettings
    {
        amanager.setStreamMute(AudioManager.STREAM_MUSIC, false);
        StringBuilder sb = new StringBuilder(512);
        userSettings.setAudioMute(false);
        userSettings.setAudioUnmute(true);
        sb.append("AUDIO_MUTE=false");
        sockTask.SendDataToAllClients(sb.toString());
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
    	sockTask.SendDataToAllClients("STATISTICS_NUMBEROFVIDEOPACKETS=" + String.valueOf(streamPlay.getStreamInNumVideoPackets()));
    	sockTask.SendDataToAllClients("STATISTICS_NUMBEROFVIDEOPACKETSDROPPED=" + String.valueOf(streamPlay.getStreamInNumVideoPacketsDropped()));
    	sockTask.SendDataToAllClients("STATISTICS_NUMBEROFAUDIOPACKETS=" + String.valueOf(streamPlay.getStreamInNumAudioPackets()));
    	sockTask.SendDataToAllClients("STATISTICS_NUMBEROFAUDIOPACKETSDROPPED=" + String.valueOf(streamPlay.getStreamInNumAudioPacketsDropped()));    	
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
	    	Log.d(TAG, "Start : Lock");
	    	threadLock.lock();
	    	try
	    	{
	    		mIgnoreMediaServerCrash = false;
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
	    		threadLock.unlock();
	        	Log.d(TAG, "Start : Unlock");
	    	}
    	}
    }

    public void Stop(int sessionId)
    {
    	if ((userSettings.getStreamState(sessionId) != StreamState.STOPPED) && (userSettings.getStreamState(sessionId) != StreamState.CONFIDENCEMODE))
    	{
	    	Log.d(TAG, "Stop : Lock");
	    	threadLock.lock();
	    	try
	    	{
		    	playStatus="false";
		    	stopStatus="true";
		        pauseStatus="false";
		        restartRequired[sessionId]=false;
		        hm2.get(userSettings.getMode(sessionId)).executeStop(sessionId);
		        // device state will be set in stop callback
	    	}
	    	finally
	    	{
	    		Log.d(TAG, "Stop : Unlock");
	    		threadLock.unlock();
	    	}
    	}
    }

    public void Pause(int sessionId)
    {
    	if (userSettings.getStreamState(sessionId) != StreamState.PAUSED)
    	{
	    	Log.d(TAG, "Pause : Lock");
	    	threadLock.lock();
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
	    		threadLock.unlock();
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
        
        // we are starting to streamout so stop confidence preview
        cam_streaming.stopConfidencePreview(sessId);
        
        updateWindow(sessId);
        showPreviewWindow(sessId);
        out_url = createStreamOutURL(sessId);

        try {
            cam_streaming.setSessionIndex(sessId);
            cam_streaming.startRecording();
        } catch(Exception e) {
            e.printStackTrace();
        }
        //Toast.makeText(this, "StreamOut Started", Toast.LENGTH_LONG).show();
        StreamOutstarted = true;
       
        sb.append("STREAMURL=").append(out_url);
        sockTask.SendDataToAllClients(sb.toString());
    }

    public void stopStreamOut(int sessId)
    {
        if(StreamOutstarted){
            //Toast.makeText(this, "StreamOut Stopped", Toast.LENGTH_LONG).show();
            //On STOP, there is a chance to get ducati crash which does not save current state
            //causes streaming never stops.
            //FIXME:Temp Hack for ducati crash to save current state
            userSettings.setStreamState(StreamState.STOPPED, sessId);
            cam_streaming.setSessionIndex(sessId);
            cam_streaming.stopRecording(false);
            StreamOutstarted = false;
            hidePreviewWindow(sessId);
            
            // Make sure that stop stream out was called by stop not a device mode change
        	// We do not want to restart confidence preview if mode is changing
        	if (userSettings.getMode(sessId) == DeviceMode.STREAM_OUT.ordinal())
        	{
        		cam_streaming.startConfidencePreview(sessId);
        		restartRequired[sessId] = true;
        	}
        }
    }
    
    public void pauseStreamOut(int sessId)
    {
        Log.d(TAG, "Nothing to do");
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
        Log.d(TAG, "streamin Window  showing" + sessId);
        showWindow(sessId);
    }

    public void EnableTcpInterleave(int sessionId){
        Log.d(TAG, " EnableTcpInterleave");
        streamPlay.setRtspTcpInterleave(true, sessionId);
    }

    public void setStreamInUrl(String ap_url, int sessionId)
    {
        //out_url = ap_url;
        userSettings.setServerUrl(ap_url, sessionId);
        //streamPlay.setUrl(ap_url); //TODO: remove and replace have streamPlay access userSettings directly
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
        //streamPlay.setSessionIndex(sessId);
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
        //streamPlay.setSessionIndex(sessId);
        streamPlay.onStop(sessId);
        //Toast.makeText(this, "StreamIN Stopped", Toast.LENGTH_LONG).show();
        hideStreamInWindow(sessId);
    }

    public void pauseStreamIn(int sessId)
    {
    	//streamPlay.setSessionIndex(sessId);
        streamPlay.onPause(sessId);
        //TODO
    }

    //Preview 
    public void startPreview(int sessId)
    {
        updateWindow(sessId);
        showPreviewWindow(sessId);
        cam_preview.setSessionIndex(sessId);
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
	return "1";//"TODO";
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
    	sockTask.SendDataToAllClients("RECOVER_DUCATI=TRUE");
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
		            	threadLock.lock();
		            	try
		            	{
			                if (paramAnonymousIntent.getAction().equals("evs.intent.action.hdmi.RESOLUTION_CHANGED"))
			                {
		                        int resolutionId = paramAnonymousIntent.getIntExtra("evs_hdmi_resolution_id", -1);
			                    int prevResolutionIndex = hdmiInput.getResolutionIndex();
			                    hdmiInput.setResolutionIndex(resolutionId);
			                    // this will set up ci bus, sending data to camera
//			                    if (cam_streaming.mCameraPreviewObj != null)
//			                    	cam_preview.getHdmiInputResolution();
			                	sendHdmiInSyncState();
			                	
			                	for(int sessionId = 0; sessionId < NumOfSurfaces; sessionId++)
			                	{
			                		int device_mode = userSettings.getMode(sessionId);
			                		
			                		// Hide window while hdmi status is changing
			                		if (device_mode != DeviceMode.STREAM_IN.ordinal())
    	                    			hidePreviewWindow(sessionId);
			                				                    
				                    String hdmiInputResolution = null;
				                    Log.i(TAG, "Received resolution changed broadcast !: " + resolutionId);
				                    boolean validResolution = (hdmiInput.getHorizontalRes().startsWith("0") != true) && (hdmiInput.getVerticalRes().startsWith("0")!= true) && (resolutionId != 0);
				                    // Treat confidence mode same as preview mode, except dont include streamstate flag
				                    boolean confidencePreviewMode = ((device_mode==DeviceMode.STREAM_OUT.ordinal()) && (cam_streaming.getConfidencePreviewStatus()));
				                    if (confidencePreviewMode)
				                    	device_mode = DeviceMode.PREVIEW.ordinal();
				                    	
				                    if ((device_mode!=DeviceMode.STREAM_IN.ordinal()) && (restartRequired[sessionId]) && (prevResolutionIndex != resolutionId))  
				                    {
										//TODO: Improve logic for mediaserver crash gating
				                        Log.i(TAG, "Restart called due to resolution change broadcast ! ");
			
				                        mIgnoreMediaServerCrash = true;
				                        
				                        // stop stream
				                        if (device_mode == DeviceMode.PREVIEW.ordinal())
				                        {
					                        if (cam_preview.IsPreviewStatus())
						                        cam_preview.stopPlayback(false);
					                        else
					                            Log.i(TAG, "Device is in Idle State");
				                        }
				                        else if (device_mode == DeviceMode.STREAM_OUT.ordinal())
				                        {
				                        	//HACK: For ioctl issue 
					                        //1. Stop Camera 2. Hide Preview 3. Sleep 5 sec		                        
					                        if((cam_streaming.mCameraPreviewObj != null) && ((cam_streaming.isStreaming()) == true))
					                            cam_streaming.stopRecording(false); //true
					                        else
					                            Log.i(TAG, "Device is in Idle State");
				                        }
				                        
				                        hpdHdmiEvent = 1;
				                        Log.i(TAG, "HDMI resolutions - HRes:" + hdmiInput.getHorizontalRes() + " Vres:" + hdmiInput.getVerticalRes());
				                        
				                        if ((validResolution) && (threadLock.getQueueLength() == 0))
				                        {
				                        	mIgnoreMediaServerCrash = false;
				                        	
				                            showPreviewWindow(sessionId);
				                            
				                            if (device_mode == DeviceMode.PREVIEW.ordinal())
					                        {
					                            if (confidencePreviewMode)
					                            	cam_preview.startPlayback(true);
					                            else
					                            	cam_preview.startPlayback(false);
					                        }
				                            else if (device_mode == DeviceMode.STREAM_OUT.ordinal())
				                            {
				                            	try {				                            	
				                            		cam_streaming.startRecording();
				                            	} catch (Exception e) {e.printStackTrace();}
				                            }
				                        }
				                        else
				                        	// This will send processing join
				                        	SendStreamState(StreamState.CONNECTING, sessionId);
				                    }
			                	}
		                    }
		                    else
		                        Log.i(TAG, " Nothing to do!!!");
		            	}
		            	finally
		            	{
		            		threadLock.unlock();
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
            			threadLock.lock();
                    	try
                    	{
        	                if (paramAnonymousIntent.getAction().equals("evs.intent.action.hdmi.HPD"))
        	                {
        	                	if ((threadLock.getQueueLength() == 0) || (threadLock.getQueueLength() == 1))
        	                	{
        	                		// this will set up ci bus, sending data to camera
//        	                		if (cam_streaming.mCameraPreviewObj != null)
//        	                			cam_preview.getHdmiInputResolution();
	        	                	sendHdmiInSyncState();
	        	                    int i = paramAnonymousIntent.getIntExtra("evs_hdmi_hdp_id", -1);
	        	                    Log.i(TAG, "Received hpd broadcast ! " + i);
	        	                    if(i==0){
	        	                    	mIgnoreMediaServerCrash = true;
	        	                    	
	        	                        //HACK: For ioctl issue 
	        	                        //1. Hide Preview 2. sleep One Sec 3.Stop Camera 4. Sleep 5 sec
	        	                    	for (int sessionId = 0; sessionId < NumOfSurfaces; sessionId++)
	        		                	{
	        	                    		if (userSettings.getMode(sessionId) != DeviceMode.STREAM_IN.ordinal())
	        	                    			hidePreviewWindow(sessionId);
	        		                	}
//	        	                        SystemClock.sleep(cameraRestartTimout);
	        	                        if((cam_streaming.mCameraPreviewObj != null) && ((cam_streaming.isStreaming()) == true))
	        	                            cam_streaming.stopRecording(false);	//true
	        	                        else if ((((cam_preview.IsPreviewStatus()) == true)))  
	        	                            cam_preview.stopPlayback(false);
	        	                        else
	        	                            Log.i(TAG, "Device is in Idle State");
	
//	        	                        SystemClock.sleep((5*cameraRestartTimout));
	        	                        
	        	                        for (int sessionId = 0; sessionId < NumOfSurfaces; sessionId++)
	        		                	{
	        		                		if ((userSettings.getMode(sessionId) != DeviceMode.STREAM_IN.ordinal()) && (restartRequired[sessionId]))
	        		                        	// This will send processing join
	        	                        		SendStreamState(StreamState.CONNECTING, sessionId);
	        		                	}
	        	                    }
	        	                    else 
	        	                        hpdStateEnabled = 1;
        	                	}
        	                }
                    	}
                    	finally
                    	{
                    		threadLock.unlock();
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
		
		                    //send out sync detection signal
		                    hdmiOutput.setSyncStatus();
		                    StringBuilder sb = new StringBuilder(1024);
		                    sb.append("hdmiout_sync_detected=").append(hdmiOutput.getSyncStatus());
		                    sockTask.SendDataToAllClients(sb.toString());
		
		                    hdmiOutput.setSyncStatus();
					        hdmiOutput.setHorizontalRes(Integer.toString(hdmiOutputResolution.width));
					        hdmiOutput.setVerticalRes(Integer.toString(hdmiOutputResolution.height));
					        hdmiOutput.setFPS(Integer.toString(Math.round(hdmiOutputResolution.refreshRate)));
					        hdmiOutput.setAspectRatio();
		                }
		            }
            	}).start();
            }
        };
        IntentFilter hdmioutResolutionIntentFilter = new IntentFilter("evs.intent.action.hdmi.HDMIOUT_RESOLUTION_CHANGED");
        registerReceiver(hdmioutResolutionChangedEvent, hdmioutResolutionIntentFilter);
    }
    
	private void sendHdmiInSyncState() {
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
	
	public void saveUserSettings()
	{
		Writer writer = null;
		GsonBuilder builder = new GsonBuilder();
      	Gson gson = builder.create();
      	String serializedClass = gson.toJson(this.userSettings);
      	try 
      	{
      		saveSettingsLock.lock();
      		writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(savedSettingsFilePath), "utf-8"));
    	    writer.write(serializedClass);
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
}
