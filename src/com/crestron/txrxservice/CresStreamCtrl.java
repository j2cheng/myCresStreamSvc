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
    void executeStart();
}

interface myCommand {
    void executeStop();
}

interface myCommand2 {
    void executePause();
}

public class CresStreamCtrl extends Service {
    CameraStreaming cam_streaming;
    CameraPreview cam_preview;
    StringTokenizer tokenizer;
    StreamIn streamPlay;
    BroadcastReceiver hpdEvent = null;
    BroadcastReceiver resolutionEvent = null;
    BroadcastReceiver hdmioutResolutionChangedEvent = null;

    CresStreamConfigure myconfig;
    AudioManager amanager;
    TCPInterface sockTask;
   
    HDMIInputInterface hdmiInput;
    HDMIOutputInterface hdmiOutput;

    CresDisplaySurface dispSurface;
    
    final int cameraRestartTimout = 1000;//msec
    int hpdStateEnabled = 0;
    static int hpdHdmiEvent = 0;

    String TAG = "TxRx StreamCtrl";
    static String out_url="";
    static String playStatus="false";
    static String stopStatus="true";
    static String pauseStatus="false";
    static int last_x = 0, last_y = 0, last_w = 1920, last_h = 1080;
    //int device_mode = 0;
    int sessInitMode = 0;
    static int idx = 0;
    boolean StreamOutstarted = false;
    boolean hdmiInputDriverPresent = false;
    boolean enable_passwd = false;
    boolean disable_passwd = true;
    static boolean restartRequired;

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
    }
    
    private final ReentrantLock threadLock = new ReentrantLock(true); // fairness=true, makes lock ordered

    StreamState devicestatus = StreamState.STOPPED;
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
            myconfig = new CresStreamConfigure();
            tokenizer = new StringTokenizer();
            hdmiOutput = new HDMIOutputInterface();
            
//            //////////////////////////
//            UserSettings userSettings = new UserSettings();
//            userSettings.setVolume(11);
//            GsonBuilder builder = new GsonBuilder();
//            Gson gson = builder.create();
//            String serializedClass = gson.toJson(userSettings);
//            Log.d(TAG, "Serialized Class :" + serializedClass);
//            UserSettings settings2 = new UserSettings();
//            settings2 = gson.fromJson(serializedClass, UserSettings.class);
//            Log.d(TAG, "Volume is at :" + settings2.getVolume());
//            ///////////////////////////
            
            hdmiInputDriverPresent = HDMIInputInterface.isHdmiDriverPresent();

        	if (hdmiInputDriverPresent)
        	{
        		Log.d(TAG, "HDMI input driver is present");
        		hdmiInput = new HDMIInputInterface();
        		//refresh resolution on startup
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
            //streamPlay = new StreamIn(CresStreamCtrl.this, sHolder);
            streamPlay = new StreamIn(CresStreamCtrl.this);
            
            //sHolder = dispSurface.GetSurfaceHolder(1);//TODO:IDX
            //cam_streaming = new CameraStreaming(CresStreamCtrl.this, sHolder);
            cam_streaming = new CameraStreaming(CresStreamCtrl.this);
            //cam_preview = new CameraPreview(this, sHolder, hdmiInput);
            cam_preview = new CameraPreview(this, hdmiInput);
            //Play Control
            hm = new HashMap();
            hm.put(2/*"PREVIEW"*/, new Command() {
                    public void executeStart() {startPreview(); };
                    });
            hm.put(1 /*"STREAMOUT"*/, new Command() {
                    public void executeStart() {startStreamOut();};//createStreamOutURL();};
                    });
            hm.put(0 /*"STREAMIN"*/, new Command() {
                    public void executeStart() {startStreamIn(); };
                    });
            hm2 = new HashMap();
            hm2.put(2/*"PREVIEW"*/, new myCommand() {
                    public void executeStop() {stopPreview();};
                    });
            hm2.put(1/*"STREAMOUT"*/, new myCommand() {
                    public void executeStop() {stopStreamOut();};
                    });
            hm2.put(0/*"STREAMIN"*/, new myCommand() {
                    public void executeStop() {stopStreamIn(); };
                    });
            hm3 = new HashMap();
            hm3.put(2/*"PREVIEW"*/, new myCommand2() {
                    public void executePause() {pausePreview();};
                    });
            hm3.put(1/*"STREAMOUT"*/, new myCommand2() {
                    public void executePause() {pauseStreamOut();};
                    });
            hm3.put(0/*"STREAMIN"*/, new myCommand2() {
                    public void executePause() {pauseStreamIn(); };
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
        streamPlay.onStop();
        if (dispSurface != null)
        	dispSurface.RemoveView();
    }

    public SurfaceHolder getCresSurfaceHolder(){
        return dispSurface.GetSurfaceHolder(idx);
    }

    public void setDeviceMode(int mode, int sessionId)
    {
        Log.d(TAG, " setDeviceMode "+ mode);
        // If hdmi input driver is present allow all 3 modes, otherwise only allow stream in mode
        if (hdmiInputDriverPresent || (mode == DeviceMode.STREAM_IN.ordinal())) 
        {
            Log.d(TAG, " setDeviceMode "+ mode);

            if ((myconfig.getDeviceMode(sessionId) != mode) && (devicestatus == StreamState.STARTED))
                hm2.get(myconfig.getDeviceMode(sessionId)).executeStop();
            myconfig.setDeviceMode(sessionId, mode);
        }
    }
    
    public void setXCoordinates(int x, int sessionId)
    {
        if (last_x!=x){
            myconfig.setx(sessionId, x);
            updateXY(sessionId);
        }
    }

    public void setYCoordinates(int y, int sessionId)
    {
        if(last_y!=y){
            myconfig.sety(sessionId, y);
            updateXY(sessionId);
        }
    }

    public void setWindowSizeW(int w, int sessionId)
    {
        if(last_w!=w){
            myconfig.setWidth(sessionId, w);
            updateWH(sessionId);
        }
    }

    public void setWindowSizeH(int h, int sessionId)
    {
        if(last_h!=h){
            myconfig.setHeight(sessionId, h);
            updateWH(sessionId);
        }
    }

    public int getXCoordinates()
    {
        return CresStreamConfigure.getx(idx);
    }

    public int getYCoordinates()
    {
        return CresStreamConfigure.gety(idx);
    }

    public int getWindowSizeW()
    {
        return CresStreamConfigure.getWidth(idx);
    }

    public int getWindowSizeH()
    {
        return CresStreamConfigure.getHeight(idx);
    }
        
    public void updateWH(int sessionId)
    {
        Log.d(TAG, "updateWH : Lock");
        threadLock.lock();
        try
        {
            if (dispSurface != null)
            {
                dispSurface.UpdateDimensions(CresStreamConfigure.getWidth(sessionId), 
                        CresStreamConfigure.getHeight(sessionId), sessionId);
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
                    dispSurface.UpdateCoordinates(CresStreamConfigure.getx(sessionId), 
                            CresStreamConfigure.gety(sessionId), sessionId); 
                }
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

    public void setSessionInitMode(int sessId, int mode)
    {
	    Log.d(TAG, " setSessionInitMode "+ mode);
	    sessInitMode = mode;
	    switch(mode){
		//case "ByReceiver":
		case 0: 
		{
        		myconfig.setTransportMode("RTSP");	
	    		Log.d(TAG, "By ReceiverMode rtsp streaming starts");
		}
		break;
		//case "ByTransmitter":
		case 1:
		{
	    		Log.d(TAG, "By TransmitterMode streaming starts");
		}
		break;
		//case "MCastViaRTSP":
		case 2:
		{
        		myconfig.setTransportMode("MRTSP");	
	    		Log.d(TAG, "MCastViaRTSP streaming starts");
		}
		break;
		//case "MCastViaUDP":
		case 3:
		{
	    		Log.d(TAG, "MCastViaUDP Mode");
		}
		break;
		default:
		break;
	    }
    }

    public void setStreamMute()
    {
        amanager.setStreamMute(AudioManager.STREAM_MUSIC, true);
        StringBuilder sb = new StringBuilder(512);
        sb.append("AUDIO_UNMUTE=false");
        sockTask.SendDataToAllClients(sb.toString());
    }
    
    public void setStreamUnMute()
    {
        amanager.setStreamMute(AudioManager.STREAM_MUSIC, false);
        StringBuilder sb = new StringBuilder(512);
        sb.append("AUDIO_MUTE=false");
        sockTask.SendDataToAllClients(sb.toString());
    }

    public int getStreamState()
    {
        return myconfig.getDeviceMode(idx);
        //return device_mode[idx];
    }

    public void SetPasswdEnable(int sessId)
    {
        StringBuilder sb = new StringBuilder(512);
        enable_passwd = true;
        disable_passwd = false;
        sb.append("PASSWORD_DISABLE=FALSE");
        sockTask.SendDataToAllClients(sb.toString());
    }

    public void SetPasswdDisable(int sessId)
    {
        StringBuilder sb = new StringBuilder(512);
        enable_passwd = false;
        disable_passwd = true;
        sb.append("PASSWORD_ENABLE=FALSE");
        sockTask.SendDataToAllClients(sb.toString());
    }

    public void SetUserName(String uname, int sessId){
        myconfig.setUserName(sessId, uname);
    }

    public void SetPasswd(String passwd, int sessId){
        myconfig.setPasswd(sessId, passwd);
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

    public void SendStreamState(StreamState state)
    {
    	Log.d(TAG, "StreamState : Lock");
    	threadLock.lock();
    	
    	try
    	{
        	devicestatus = state;
	        StringBuilder sb = new StringBuilder(512);
	        sb.append("STREAMSTATE=").append(state.getValue());
	        sockTask.SendDataToAllClients(sb.toString());
	        tokenizer.AddTokenToList("STREAMSTATE", String.valueOf(state.getValue()));
    	}
        finally 
    	{
        	threadLock.unlock();
        	Log.d(TAG, "StreamState : Unlock");
    	}
    }

    //Ctrls
    public void Start(int sessionId)
    {
        idx = sessionId;
    	Log.d(TAG, "Start : Lock");
    	threadLock.lock();
    	try
    	{
    		SendStreamState(StreamState.CONNECTING);
	    	playStatus="true";
	    	stopStatus="false";
	        pauseStatus="false";
	        hm.get(myconfig.getDeviceMode(sessionId)).executeStart();
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
        idx = sessionId;
    	Log.d(TAG, "Stop : Lock");
    	threadLock.lock();
    	try
    	{
	    	playStatus="false";
	    	stopStatus="true";
	        pauseStatus="false";
	        restartRequired=false;
	        hm2.get(myconfig.getDeviceMode(sessionId)).executeStop();
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
        idx = sessionId;
    	Log.d(TAG, "Pause : Lock");
    	threadLock.lock();
    	try
    	{
	        pauseStatus="true";
	    	playStatus="false";
	    	stopStatus="false";
	        hm3.get(myconfig.getDeviceMode(sessionId)).executePause();
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
            myconfig.setMulticastIP(sessId, ip);
    } 
    
    public void setEncodingResolution(int res, int sessId){
        myconfig.setOutResolution(sessId,res);
    } 
    
    public void setTMode(String tmode){
        myconfig.setTransportMode(tmode);	
    } 
    
    public void setStreamProfile(String profile){
        myconfig.setVEncProfile(profile);	
    } 
    
    public void setVFrmRate(int sessId, int vfr){
        myconfig.setVFrameRate(sessId, vfr);	
    } 
    
    public void setVbitRate(int sessId, int vbr){
        myconfig.setVideoBitRate(sessId, vbr);	
    } 
    
    public void setRTSPPort(int sessId, int _port){
	    myconfig.setRTSPPort(sessId,_port);	
    }
    public void setTSPort(int sessId, int _port){
	    myconfig.setTSPort(sessId,_port);	
    }
    public void setRTPVideoPort(int sessId, int _port){
	    myconfig.setRTPVPort(sessId,_port);	
    }
    public void setRTPAudioPort(int sessId, int _port){
	    myconfig.setRTPAPort(sessId,_port);	
    }

    private String createStreamOutURL()
    {
        StringBuilder url = new StringBuilder(1024);
        url.append("");
        String proto = "";
        String file = "";
        int port = 0;  
        String l_ipaddr= "";
        if ((sessInitMode == 0) || (sessInitMode == 2))
        {
	        switch(myconfig.mode.getMode()){
	            case 5:
	            case 0:{
	                       proto = "rtsp";
	                       port = myconfig.getRTSPPort(idx); 
	                       l_ipaddr = myconfig.getDeviceIP();
	                       file = "/live.sdp";
	                   }
	                   break;
	            case 1:{//Only RTP
	                       proto = "rtp";
	                       //l_ipaddr = "@";
	                       l_ipaddr = myconfig.getUrl(idx);
	                       port = myconfig.getRTPVPort(idx);
	
	                   }
	                   break;
	            case 2:{
	                       proto = "rtp";
	                       l_ipaddr = myconfig.getUrl(idx);
	                       port = myconfig.getTSPort(idx); 
	                   }
	                   break;
	            case 3:{
	                       proto = "udp";
	                       l_ipaddr = myconfig.getUrl(idx);
	                       port = myconfig.getTSPort(idx); 
	                   }
	                   break;
	            default:
	                   break;
	        }
	        url.append(proto).append("://").append(l_ipaddr).append(":").append(port).append(file);
	        Log.d(TAG, "URL is "+url.toString());
        }        
        return url.toString();
    }

    public void startStreamOut()
    {
        StringBuilder sb = new StringBuilder(512);
        updateWindow(idx);
        showPreviewWindow();
        out_url = createStreamOutURL();

        if((sessInitMode==0) && (myconfig.mode.getMode()!=0)){
            Toast.makeText(this, "Invalid Mode for this SessionInitation", Toast.LENGTH_LONG).show();
        }
        else{
            try {
                cam_streaming.setSessionIndex(idx);
                cam_streaming.startRecording();
            } catch(IOException e) {
                e.printStackTrace();
            }
            //Toast.makeText(this, "StreamOut Started", Toast.LENGTH_LONG).show();
            StreamOutstarted = true;
        }
        sb.append("STREAMURL=").append(out_url);
        sockTask.SendDataToAllClients(sb.toString());
    }

    public void stopStreamOut()
    {
        if(StreamOutstarted){
            //Toast.makeText(this, "StreamOut Stopped", Toast.LENGTH_LONG).show();
            cam_streaming.setSessionIndex(idx);
            cam_streaming.stopRecording(false);
            StreamOutstarted = false;
            hidePreviewWindow();
        }
    }
    
    public void pauseStreamOut()
    {
        Log.d(TAG, "Nothing to do");
    }

    private void hidePreviewWindow()
    {
        Log.d(TAG, "Preview Window hidden");
        if (dispSurface != null)
        	dispSurface.HideWindow(idx);
    }
    
    private void showPreviewWindow()
    {
        Log.d(TAG, "Preview Window showing");
        if (dispSurface != null)
        	dispSurface.ShowWindow(idx);
    }

    //StreamIn Ctrls & Config
    private void hideStreamInWindow()
    {
        Log.d(TAG, " streamin Window hidden " + idx);
        if (dispSurface != null)
        	dispSurface.HideWindow(idx);
    }

    private void showStreamInWindow()
    {
        Log.d(TAG, "streamin Window  showing" + idx);
        if (dispSurface != null)
        	dispSurface.ShowWindow(idx);
    }

    public void EnableTcpInterleave(int sessionId){
        Log.d(TAG, " EnableTcpInterleave");
        streamPlay.setRtspTcpInterleave(true);
    }

    public void setStreamInUrl(String ap_url, int sessionId)
    {
        out_url = ap_url;
        myconfig.setUrl(sessionId, ap_url);
        streamPlay.setUrl(ap_url);
        if(ap_url.startsWith("rtp://@"))
            streamPlay.setRtpOnlyMode( myconfig.getRTPVPort(idx),  myconfig.getRTPAPort(idx), myconfig.getDeviceIP());
        else if(ap_url.startsWith("http://"))
            streamPlay.disableLatency();
        else
            Log.d(TAG, "No conditional Tags for StreamIn");
    }
    
    public void SetStreamInLatency(int sessId, int initialLatency)
    {
        streamPlay.setLatency(sessId, initialLatency);
    }
    
    public String getStreamUrl()
    {
        return out_url;
    }

    public void startStreamIn()
    {
        updateWindow(idx);
        showStreamInWindow();
        streamPlay.setSessionIndex(idx);
        streamPlay.onStart();
        //Toast.makeText(this, "StreamIN Started", Toast.LENGTH_LONG).show();
    }

	/**
	 * 
	 */
    public void updateWindow(int sessId) {
        updateWH(sessId);
        updateXY(sessId);
    }

    public void stopStreamIn()
    {
        streamPlay.setSessionIndex(idx);
        streamPlay.onStop();
        //Toast.makeText(this, "StreamIN Stopped", Toast.LENGTH_LONG).show();
        hideStreamInWindow();
    }

    public void pauseStreamIn()
    {
        streamPlay.onPause();
        //TODO
    }

    public int getStreamHorizontalResFb(boolean streamIn){
    	if (streamIn)
    		return streamPlay.getMediaPlayerHorizontalResFb();
    	else
    		return cam_streaming.getStreamOutHorizontalResFb();
    } 
    
    public int getStreamVerticalResFb(boolean streamIn){
    	if (streamIn)
    		return streamPlay.getMediaPlayerVerticalResFb();
    	else
    		return cam_streaming.getStreamOutVerticalResFb();
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
    public void startPreview()
    {
        updateWindow(idx);
        showPreviewWindow();
        cam_preview.startPlayback();
        //Toast.makeText(this, "Preview Started", Toast.LENGTH_LONG).show();
    }

    public void stopPreview()
    {
        hidePreviewWindow();
        cam_preview.stopPlayback();
        //Toast.makeText(this, "Preview Stopped", Toast.LENGTH_LONG).show();
    }
    
    public void pausePreview()
    {
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
        if (myconfig.getDeviceMode(idx) == DeviceMode.STREAM_IN.ordinal()) 
            return streamPlay.updateSvcWithPlayerStatistics(); 
        else if (myconfig.getDeviceMode(idx) == DeviceMode.STREAM_OUT.ordinal()) 
            return cam_streaming.updateSvcWithStreamStatistics();
        else
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
                        int device_mode = myconfig.getDeviceMode(idx);
	                    int resolutionId = paramAnonymousIntent.getIntExtra("evs_hdmi_resolution_id", -1);
	                    String hdmiInputResolution = null;
	                    Log.i(TAG, "Received resolution changed broadcast !: " + resolutionId);
	                    boolean validResolution = (hdmiInput.getHorizontalRes().startsWith("0") != true) && (hdmiInput.getVerticalRes().startsWith("0")!= true) && (resolutionId != 0);
	                    
	                    if (device_mode != DeviceMode.STREAM_IN.ordinal())
	                    	restartRequired = ((devicestatus == StreamState.STARTED) || (restartRequired));
	                    if ((device_mode==DeviceMode.PREVIEW.ordinal()) && (restartRequired))  
	                    {
	                        Log.i(TAG, "Restart called due to resolution change broadcast ! ");

	                        cam_preview.stopPlayback();
	                        hidePreviewWindow();
	                        SystemClock.sleep(cameraRestartTimout);
	                        hpdHdmiEvent = 1;
	                        Log.i(TAG, "HDMI resolutions - HRes:" + hdmiInput.getHorizontalRes() + " Vres:" + hdmiInput.getVerticalRes());
	                        
	                        if (validResolution)
	                        {
	                            showPreviewWindow();
	                            cam_preview.startPlayback();
	                            restartRequired = false;
	                        }
	                    }
	                    else if((device_mode==DeviceMode.STREAM_OUT.ordinal()) && (restartRequired))
	                    {
                    		//HACK: For ioctl issue 
	                        //1. Hide Preview 2. sleep One Sec 3.Stop Camera 4. Sleep 5 sec
	                        hidePreviewWindow();
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
		                            showPreviewWindow();
		                            cam_streaming.startRecording();
		                            restartRequired = false;
		                        } catch(IOException e) {
		                            e.printStackTrace();
		                        }
	                        }
	                    }
	                    else{
	                        Log.i(TAG, " Nothing to do!!!");
	                    }
	                    if((hpdStateEnabled==1) && (device_mode==DeviceMode.PREVIEW.ordinal())){
	                        SystemClock.sleep(cameraRestartTimout);
	                        showPreviewWindow();
	                        hpdHdmiEvent = 1;
	                        cam_preview.startPlayback();
	                    }
	                }
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
	                    int i = paramAnonymousIntent.getIntExtra("evs_hdmi_hdp_id", -1);
	                    Log.i(TAG, "Received hpd broadcast ! " + i);
	                    if(i==0){
	                        //HACK: For ioctl issue 
	                        //1. Hide Preview 2. sleep One Sec 3.Stop Camera 4. Sleep 5 sec
	                        hidePreviewWindow();
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
	                    //send out sync detection signal
	                    hdmiInput.setSyncStatus();
	                    StringBuilder sb = new StringBuilder(1024);
	                    sb.append("hdmiin_sync_detected=").append(hdmiInput.getSyncStatus());
	                    sockTask.SendDataToAllClients(sb.toString());
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
}
