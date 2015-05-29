package com.crestron.txrxservice;

import java.util.HashMap;
import java.util.Map;
import java.io.IOException;
import java.lang.String;
import java.util.concurrent.locks.*;

import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.app.Service;
import android.os.IBinder;
import android.view.SurfaceView;
import android.widget.Toast;
import android.content.Context;
import android.os.AsyncTask;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.media.AudioManager;
import android.view.Surface;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.Surface.PhysicalDisplayInfo;
import android.graphics.PixelFormat;
import android.hardware.Camera;

//import com.google.gson.Gson;
//import com.google.gson.GsonBuilder;

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
    CameraStreaming cam_streaming;
    CameraPreview cam_preview;
    StringTokenizer tokenizer;
    
    GstreamIn streamPlay;
    BroadcastReceiver hpdEvent = null;
    BroadcastReceiver resolutionEvent = null;
    BroadcastReceiver hdmioutResolutionChangedEvent = null;

    //CresStreamConfigure myconfig;
    public UserSettings userSettings;
    AudioManager amanager;
    TCPInterface sockTask;
   
    HDMIInputInterface hdmiInput;
    HDMIOutputInterface hdmiOutput;

    CresDisplaySurface dispSurface;
    
    final int cameraRestartTimout = 1000;//msec
    int hpdStateEnabled = 0;
    static int hpdHdmiEvent = 0;

    public final static int NumOfSurfaces = 2;
    String TAG = "TxRx StreamCtrl";
    static String out_url="";
    static String playStatus="false";
    static String stopStatus="true";
    static String pauseStatus="false";
    static int idx = 0; //TODO: lets remove this, put in a more permanent solution
    boolean StreamOutstarted = false;
    boolean hdmiInputDriverPresent = false;
    //boolean enable_passwd = false;
    //boolean disable_passwd = true;
    boolean[] restartRequired = new boolean[NumOfSurfaces];

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

    	// Do not add anything after the Last state
    	LAST(7);
    	
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
    private final ReentrantLock streamStateLock = new ReentrantLock(true); // fairness=true, makes lock ordered

    //StreamState devicestatus = StreamState.STOPPED;
    //HashMap
    HashMap<Integer, Command> hm;
    HashMap<Integer, myCommand> hm2;
    HashMap<Integer, myCommand2> hm3;
    @Override
        public void onCreate() {
            super.onCreate();
            int windowWidth = 1920;
            int windowHeight = 1080;
            
            //Input Streamout Config
            streamPlay = new GstreamIn(CresStreamCtrl.this);
            userSettings = new UserSettings(streamPlay);

            //myconfig = new CresStreamConfigure();
            tokenizer = new StringTokenizer();
            hdmiOutput = new HDMIOutputInterface();
            
            userSettings.setStreamState(StreamState.STOPPED, 0);
            userSettings.setStreamState(StreamState.STOPPED, 1);
            
            //////////////////////////
//            UserSettings userSettings = new UserSettings();
//            userSettings.setVolume(11);
//            GsonBuilder builder = new GsonBuilder();
//            Gson gson = builder.create();
//            String serializedClass = gson.toJson(userSettings);
//            Log.d(TAG, "Serialized Class :" + serializedClass);
//            UserSettings settings2 = new UserSettings();
//            settings2 = gson.fromJson(serializedClass, UserSettings.class);
//            Log.d(TAG, "Volume is at :" + settings2.getVolume());
            ///////////////////////////
            
            hdmiInputDriverPresent = HDMIInputInterface.isHdmiDriverPresent();

        	if (hdmiInputDriverPresent)
        	{
        		Log.d(TAG, "HDMI input driver is present");
        		hdmiInput = new HDMIInputInterface();
        		//refresh resolution on startup
			hdmiInput.setResolutionIndex(hdmiInput.readResolutionEnum());
        		refreshInputResolution();
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
            //SurfaceHolder sHolder = dispSurface.GetSurfaceHolder(0);//TODO:IDX

            
            
            //sHolder = dispSurface.GetSurfaceHolder(1);//TODO:IDX
            //cam_streaming = new CameraStreaming(CresStreamCtrl.this, sHolder);
            cam_streaming = new CameraStreaming(CresStreamCtrl.this);
            //cam_preview = new CameraPreview(this, sHolder, hdmiInput);
            cam_preview = new CameraPreview(this, hdmiInput);
            //Play Control
            hm = new HashMap();
            hm.put(2/*"PREVIEW"*/, new Command() {
                    public void executeStart(int sessId) {startPreview(sessId); };
                    });
            hm.put(1 /*"STREAMOUT"*/, new Command() {
                    public void executeStart(int sessId) {startStreamOut(sessId);};//createStreamOutURL();};
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
            
            //Global Default Exception Handler
            final Thread.UncaughtExceptionHandler oldHandler = Thread.getDefaultUncaughtExceptionHandler();

            Thread.setDefaultUncaughtExceptionHandler(
                    new Thread.UncaughtExceptionHandler() {
                    @Override
                    public void uncaughtException(Thread paramThread, Throwable paramThrowable) {
                    //Close Camera   
                    Log.d(TAG,"Global uncaught Exception !!!!!!!!!!!" );
                    cam_preview.stopPlayback();
                    cam_streaming.stopRecording(false);
                    if (oldHandler != null)
                    oldHandler.uncaughtException(paramThread, paramThrowable); //Delegates to Android's error handling
                    else
                    System.exit(2); //Prevents the service/app from freezing
                    }
                    });
        }
   
    @Override
        public int onStartCommand (Intent intent, int flags, int startId) {
            // TODO Auto-generated method stub
            Log.d(TAG,"S: CresStreamCtrl Started !" );
            return 0;
        }
    
    public IBinder onBind(Intent intent)
    {
        return null;
    } 
    
    public void onDestroy(){
        super.onDestroy();
        sockTask.cancel(true);
        Log.d(TAG, " Asynctask cancelled");
        unregisterReceiver(resolutionEvent);
        unregisterReceiver(hpdEvent);
        unregisterReceiver(hdmioutResolutionChangedEvent);
        cam_streaming.stopRecording(false);
        cam_preview.stopPlayback();
        for (int i = 0; i < NumOfSurfaces; i++)
        {
        	streamPlay.onStop(i);
        }
        if (dispSurface != null)
        	dispSurface.RemoveView();
    }
 
    public SurfaceHolder getCresSurfaceHolder(int sessionId){
        return dispSurface.GetSurfaceHolder(sessionId);
    }

    public void setDeviceMode(int mode, int sessionId)
    {
        Log.d(TAG, " setDeviceMode "+ mode);
        // If hdmi input driver is present allow all 3 modes, otherwise only allow stream in mode
        if (hdmiInputDriverPresent || (mode == DeviceMode.STREAM_IN.ordinal())) 
        {
            Log.d(TAG, " setDeviceMode "+ mode);

            if ((userSettings.getMode(sessionId) != mode) && (userSettings.getStreamState(sessionId) == StreamState.STARTED))
                hm2.get(userSettings.getMode(sessionId)).executeStop(sessionId);
            userSettings.setMode(mode, sessionId);
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
        
    public void updateWH(int sessionId)
    {
        Log.d(TAG, "updateWH : Lock");
        threadLock.lock();
        try
        {
            if (dispSurface != null)
            {
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
    
    public void updateXY(int sessionId)
    {
    	
    	Log.d(TAG, "updateXY : Lock");
    	threadLock.lock();
    	
    	try
    	{
        	if (dispSurface != null)
            {
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
    			+ hdmiOutputResolution.refreshRate);
    	
    	hdmiOutput.setSyncStatus();
        hdmiOutput.setHorizontalRes(Integer.toString(hdmiOutputResolution.width));
        hdmiOutput.setVerticalRes(Integer.toString(hdmiOutputResolution.height));
        hdmiOutput.setFPS(Integer.toString((int)hdmiOutputResolution.refreshRate));
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
    }
    
    public void SendStreamInFeedbacks()
    {
    	sockTask.SendDataToAllClients("STREAMIN_HORIZONTAL_RES_FB=" + String.valueOf(getStreamHorizontalResFb(true)));
    	sockTask.SendDataToAllClients("STREAMIN_VERTICAL_RES_FB=" + String.valueOf(getStreamVerticalResFb(true)));
    	sockTask.SendDataToAllClients("STREAMIN_FPS_FB=" + String.valueOf(getStreamFpsFb(true)));
    	sockTask.SendDataToAllClients("STREAMIN_ASPECT_RATIO=" + String.valueOf(getStreamAspectRatioFb(true)));
    	sockTask.SendDataToAllClients("STREAMIN_AUDIO_FORMAT=" + String.valueOf(getStreamAudioFormatFb(true)));
    	sockTask.SendDataToAllClients("STREAMIN_AUDIO_CHANNELS=" + String.valueOf(getStreamAudiochannelsFb(true)));

        //processReceivedMessage()
    }
    
    public void SendStreamOutFeedbacks()
    {
    	sockTask.SendDataToAllClients("STREAMOUT_HORIZONTAL_RES_FB=" + String.valueOf(getStreamHorizontalResFb(false)));
    	sockTask.SendDataToAllClients("STREAMOUT_VERTICAL_RES_FB=" + String.valueOf(getStreamVerticalResFb(false)));
    	sockTask.SendDataToAllClients("STREAMOUT_FPS_FB=" + String.valueOf(getStreamFpsFb(false)));
    	sockTask.SendDataToAllClients("STREAMOUT_ASPECT_RATIO=" + String.valueOf(getStreamAspectRatioFb(false)));
    	sockTask.SendDataToAllClients("STREAMOUT_AUDIO_FORMAT=" + String.valueOf(getStreamAudioFormatFb(false)));
    	sockTask.SendDataToAllClients("STREAMOUT_AUDIO_CHANNELS=" + String.valueOf(getStreamAudiochannelsFb(false)));
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
        //idx = sessionId;
    	Log.d(TAG, "Start : Lock");
    	threadLock.lock();
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
    		threadLock.unlock();
        	Log.d(TAG, "Start : Unlock");
    	}
    }

    public void Stop(int sessionId)
    {
        //idx = sessionId;
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

    public void Pause(int sessionId)
    {
        //idx = sessionId;
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
    //StreamOut Ctrl & Config
    public void setMulticastIpAddress(String ip, int sessId){
        if(ip!=null)
        	userSettings.setMulticastAddress(ip, sessId);
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
    
    public void setStreamProfile(UserSettings.VideoEncProfile profile, int sessId){
    	userSettings.setStreamProfile(profile, sessId);
    } 
    
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
        }
//        else
//        {
//        	//By Transmitter
//        	if (currentStreamState == 1)
//        		l_ipaddr = userSettings.getServerUrl(idx);
//        	//Multicast via RTSP
//        	else if (currentStreamState == 3)
//        		l_ipaddr = userSettings.getMulticastAddress(idx);
//
//        	//RTP
//        	if (currentTransportMode == 0)
//        	{
//        		proto = "rtp";
//                port = userSettings.getRtpVideoPort(idx); //TODO: if we want to use this we need to change port to a string so we can have both video and audio ports
//        	}
//        	//TS over RTP
//        	else if (currentTransportMode == 1)
//        	{
//        		proto = "rtp";
//        		port = userSettings.getTsPort(idx); 
//        	}
//        	else if (currentTransportMode == 2)
//        	{
//        		proto = "udp";
//                port = userSettings.getTsPort(idx);
//        	}
//        }

        return url.toString();
    }

    public void startStreamOut(int sessId)
    {
        StringBuilder sb = new StringBuilder(512);
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
            cam_streaming.setSessionIndex(sessId);
            cam_streaming.stopRecording(false);
            StreamOutstarted = false;
            hidePreviewWindow(sessId);
        }
    }
    
    public void pauseStreamOut(int sessId)
    {
        Log.d(TAG, "Nothing to do");
    }

    private void hidePreviewWindow(int sessId)
    {
        Log.d(TAG, "Preview Window hidden");
        if (dispSurface != null)
        	dispSurface.HideWindow(sessId);
    }
    
    private void showPreviewWindow(int sessId)
    {
        Log.d(TAG, "Preview Window showing");
        if (dispSurface != null)
        	dispSurface.ShowWindow(sessId);
    }

    //StreamIn Ctrls & Config
    private void hideStreamInWindow(int sessId)
    {
        Log.d(TAG, " streamin Window hidden " + sessId);
        if (dispSurface != null)
        	dispSurface.HideWindow(sessId);
    }

    private void showStreamInWindow(int sessId)
    {
        Log.d(TAG, "streamin Window  showing" + sessId);
        if (dispSurface != null)
        	dispSurface.ShowWindow(sessId);
    }

    public void EnableTcpInterleave(int sessionId){
        Log.d(TAG, " EnableTcpInterleave");
        streamPlay.setRtspTcpInterleave(true);
    }

    public void setStreamInUrl(String ap_url, int sessionId)
    {
        //out_url = ap_url;
        userSettings.setServerUrl(ap_url, sessionId);
        //streamPlay.setUrl(ap_url); //TODO: remove and replace have streamPlay access userSettings directly
        if(ap_url.startsWith("rtp://@"))
            streamPlay.setRtpOnlyMode( userSettings.getRtpVideoPort(sessionId),  userSettings.getRtpAudioPort(sessionId), userSettings.getDeviceIp());
        else if(ap_url.startsWith("http://"))
            streamPlay.disableLatency();
        else
            Log.d(TAG, "No conditional Tags for StreamIn");
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

    public int getStreamHorizontalResFb(boolean streamIn){
    	if (streamIn)
    		return streamPlay.getMediaPlayerHorizontalResFb();
    	else
    		return cam_streaming.getStreamOutWidth();
    } 
    
    public int getStreamVerticalResFb(boolean streamIn){
    	if (streamIn)
    		return streamPlay.getMediaPlayerVerticalResFb();
    	else
    		return cam_streaming.getStreamOutHeight();
    }
    
    public int getStreamFpsFb(boolean streamIn){
    	if (streamIn)
    		return streamPlay.getMediaPlayerFpsFb();
    	else
    		return cam_streaming.getStreamOutFpsFb();
    }
    
    public String getStreamAspectRatioFb(boolean streamIn){
    	if (streamIn)
    		return streamPlay.getMediaPlayerAspectRatioFb();
    	else
    		return cam_streaming.getStreamOutAspectRatioFb();
    }

    public int getStreamAudioFormatFb(boolean streamIn){
    	if (streamIn)
    		return streamPlay.getMediaPlayerAudioFormatFb();
    	else
    		return cam_streaming.getStreamOutAudioFormatFb();
    }
    
    public int getStreamAudiochannelsFb(boolean streamIn){
    	if (streamIn)
    		return streamPlay.getMediaPlayerAudiochannelsFb();
    	else
    		return cam_streaming.getStreamOutAudiochannelsFb();
    }


    //Preview 
    public void startPreview(int sessId)
    {
        updateWindow(sessId);
        showPreviewWindow(sessId);
        cam_preview.setSessionIndex(sessId);
        cam_preview.startPlayback();
        //Toast.makeText(this, "Preview Started", Toast.LENGTH_LONG).show();
    }

    public void stopPreview(int sessId)
    {
        hidePreviewWindow(sessId);
        cam_preview.setSessionIndex(sessId);
        cam_preview.stopPlayback();
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

    public String getStreamStatistics(){
//        if (userSettings.getMode(idx) == DeviceMode.STREAM_IN.ordinal()) 
//            return streamPlay.updateSvcWithPlayerStatistics(); 
//        else if (userSettings.getMode(idx) == DeviceMode.STREAM_OUT.ordinal()) 
//            return cam_streaming.updateSvcWithStreamStatistics();
//        else
            return "";
    }

    //Registering for HPD and Resolution Event detection	
    void registerBroadcasts(){
        Log.d(TAG, "registerBroadcasts !");
        
        resolutionEvent = new BroadcastReceiver()
        {
            public void onReceive(Context paramAnonymousContext, Intent paramAnonymousIntent)
            {
            	threadLock.lock();
            	try
            	{
	                if (paramAnonymousIntent.getAction().equals("evs.intent.action.hdmi.RESOLUTION_CHANGED"))
	                {
                        int resolutionId = paramAnonymousIntent.getIntExtra("evs_hdmi_resolution_id", -1);
	                    int prevResolutionIndex = hdmiInput.getResolutionIndex();
	                    hdmiInput.setResolutionIndex(resolutionId);
	                	sendHdmiInSyncState();
	                	
	                	for(int sessionId = 0; sessionId < NumOfSurfaces; sessionId++)
	                	{
	                		int device_mode = userSettings.getMode(sessionId);
	                				                    
		                    String hdmiInputResolution = null;
		                    Log.i(TAG, "Received resolution changed broadcast !: " + resolutionId);
		                    boolean validResolution = (hdmiInput.getHorizontalRes().startsWith("0") != true) && (hdmiInput.getVerticalRes().startsWith("0")!= true) && (resolutionId != 0);
		                    
		                    if ((device_mode==DeviceMode.PREVIEW.ordinal()) && (restartRequired[sessionId]) && (prevResolutionIndex != resolutionId))  
		                    {
		                        Log.i(TAG, "Restart called due to resolution change broadcast ! ");
	
		                        cam_preview.stopPlayback();
		                        hidePreviewWindow(sessionId);
		                        SystemClock.sleep(cameraRestartTimout);
		                        hpdHdmiEvent = 1;
		                        Log.i(TAG, "HDMI resolutions - HRes:" + hdmiInput.getHorizontalRes() + " Vres:" + hdmiInput.getVerticalRes());
		                        
		                        if (validResolution)
		                        {
		                            showPreviewWindow(sessionId);
		                            cam_preview.startPlayback();
		                        }
		                    }
		                    else if((device_mode==DeviceMode.STREAM_OUT.ordinal()) && (restartRequired[sessionId]) && (prevResolutionIndex != resolutionId))
		                    {
	                    		//HACK: For ioctl issue 
		                        //1. Hide Preview 2. sleep One Sec 3.Stop Camera 4. Sleep 5 sec
		                        hidePreviewWindow(sessionId);
		                        SystemClock.sleep(cameraRestartTimout);
		                        if((cam_streaming.mCameraPreviewObj != null) && ((cam_streaming.isStreaming()) == true))
		                            cam_streaming.stopRecording(true);
		                        else if ((((cam_preview.IsPreviewStatus()) == true)))  
		                            cam_preview.stopPlayback();
		                        else
		                            Log.i(TAG, "Device is in Idle State");
	
		                        SystemClock.sleep((5*cameraRestartTimout));	                    	
		                        
		                        if (validResolution)
		                        {
			                        try{
			                            showPreviewWindow(sessionId);
			                            cam_streaming.startRecording();
			                        } catch(IOException e) {
			                            e.printStackTrace();
			                        }
		                        }
		                    }
	                	}
                    }
                    else{
                        Log.i(TAG, " Nothing to do!!!");
                    }
//	                    if((hpdStateEnabled==1) && (device_mode==DeviceMode.PREVIEW.ordinal())){
//	                        SystemClock.sleep(cameraRestartTimout);
//	                        showPreviewWindow();
//	                        hpdHdmiEvent = 1;
//	                        cam_preview.startPlayback();
//	                    }
	                
            	}
            	finally
            	{
            		threadLock.unlock();
            	}
            }
        };
        IntentFilter resolutionIntentFilter = new IntentFilter("evs.intent.action.hdmi.RESOLUTION_CHANGED");
        registerReceiver(resolutionEvent, resolutionIntentFilter);
        hpdEvent = new BroadcastReceiver()
        {
            public void onReceive(Context paramAnonymousContext, Intent paramAnonymousIntent)
            {
            	threadLock.lock();
            	try
            	{
	                if (paramAnonymousIntent.getAction().equals("evs.intent.action.hdmi.HPD"))
	                {
	                	sendHdmiInSyncState();
	                    int i = paramAnonymousIntent.getIntExtra("evs_hdmi_hdp_id", -1);
	                    Log.i(TAG, "Received hpd broadcast ! " + i);
	                    if(i==0){
	                        //HACK: For ioctl issue 
	                        //1. Hide Preview 2. sleep One Sec 3.Stop Camera 4. Sleep 5 sec
	                    	for (int sessionId = 0; sessionId < NumOfSurfaces; sessionId++)
		                	{
	                    		if (userSettings.getMode(sessionId) != DeviceMode.STREAM_IN.ordinal())
	                    			hidePreviewWindow(sessionId);
		                	}
	                        SystemClock.sleep(cameraRestartTimout);
	                        if((cam_streaming.mCameraPreviewObj != null) && ((cam_streaming.isStreaming()) == true))
	                            cam_streaming.stopRecording(true);
	                        else if ((((cam_preview.IsPreviewStatus()) == true)))  
	                            cam_preview.stopPlayback();
	                        else
	                            Log.i(TAG, "Device is in Idle State");

	                        SystemClock.sleep((5*cameraRestartTimout));
	                    }
	                    else 
	                        hpdStateEnabled = 1;
	                    	                	
	                }
            	}
            	finally
            	{
            		threadLock.unlock();
            	}
            }
        };
        IntentFilter hpdIntentFilter = new IntentFilter("evs.intent.action.hdmi.HPD");
        registerReceiver(hpdEvent, hpdIntentFilter);	

        hdmioutResolutionChangedEvent = new BroadcastReceiver()
        {
            public void onReceive(Context paramAnonymousContext, Intent paramAnonymousIntent)
            {
                if (paramAnonymousIntent.getAction().equals("evs.intent.action.hdmi.HDMIOUT_RESOLUTION_CHANGED"))
                {
            		Log.d(TAG, "receiving intent!!!!");
                	
                    int i = paramAnonymousIntent.getIntExtra("evs_hdmiout_resolution_changed_id", -1);
                    Log.i(TAG, "Received hdmiout resolution changed broadcast ! " + i);
                    PhysicalDisplayInfo hdmiOutputResolution = new PhysicalDisplayInfo();
                    Surface.getDisplayInfo(Surface.getBuiltInDisplay(Surface.BUILT_IN_DISPLAY_ID_MAIN), hdmiOutputResolution);
                    
					Log.i(TAG, "HDMI Output resolution " + hdmiOutputResolution.width + " "
							+ hdmiOutputResolution.height + " "
							+ hdmiOutputResolution.refreshRate);

                    //send out sync detection signal
                    hdmiOutput.setSyncStatus();
                    StringBuilder sb = new StringBuilder(1024);
                    sb.append("hdmiout_sync_detected=").append(hdmiOutput.getSyncStatus());
                    sockTask.SendDataToAllClients(sb.toString());

                    hdmiOutput.setSyncStatus();
			        hdmiOutput.setHorizontalRes(Integer.toString(hdmiOutputResolution.width));
			        hdmiOutput.setVerticalRes(Integer.toString(hdmiOutputResolution.height));
			        hdmiOutput.setFPS(Integer.toString((int)hdmiOutputResolution.refreshRate));
			        hdmiOutput.setAspectRatio();
                }
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
}
