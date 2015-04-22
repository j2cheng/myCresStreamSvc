package com.crestron.txrxservice;

import java.util.HashMap;
import java.util.Map;
import java.io.IOException;
import java.lang.String;
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
    int device_mode = 0;
    int sessInitMode = 0;
    boolean StreamOutstarted = false;
    boolean enable_passwd = false;
    boolean disable_passwd = true;

    enum DeviceMode {
        STREAM_IN,
        STREAM_OUT,
        PREVIEW
    }
    enum DeviceStatus {
        STARTED,
        STOPPED,
        PAUSED
    }

    DeviceStatus devicestatus = DeviceStatus.STOPPED;
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

            hdmiInput = new HDMIInputInterface();
            hdmiOutput = new HDMIOutputInterface();

            //refresh resolution on startup
            refreshResolutionInfo();
            
            // Update the resolution information before creating surfaces
            try
            {  
            	windowWidth = Integer.parseInt(hdmiOutput.getHorizontalRes());
	            setWindowSizeW(windowWidth);
	            windowHeight = Integer.parseInt(hdmiOutput.getVerticalRes());
	            setWindowSizeH(windowHeight);
            }
            catch (Exception e)
            {
            	e.printStackTrace();            	
            }

            //Stub: CSIO Cmd Receiver & TestApp functionality
            sockTask = new TCPInterface(this);
            sockTask.execute(new Void[0]);
            
            // Create a DisplaySurface to handle both preview and stream in
            // TODO: Create an array to handle multiple instances 
            dispSurface = new CresDisplaySurface(this, windowWidth, windowHeight);
            
            //Get HPDEVent state frromsysfile
            hpdHdmiEvent = MiscUtils.getHdmiHpdEventState();
            Log.d(TAG, "hpdHdmiEvent :" + hpdHdmiEvent);

            //AudioManager
            amanager=(AudioManager)getSystemService(Context.AUDIO_SERVICE);
            
            //HPD and Resolution Event Registration
            registerBroadcasts();
            //Enable StreamIn and CameraPreview 
            SurfaceHolder sHolder = dispSurface.GetSurfaceHolder();
            streamPlay = new StreamIn(CresStreamCtrl.this, sHolder);
            
            cam_streaming = new CameraStreaming(CresStreamCtrl.this, sHolder);
            cam_preview = new CameraPreview(sHolder, hdmiInput);
            //Play Control
            hm = new HashMap();
            hm.put(2/*"PREVIEW"*/, new Command() {
                    public void executeStart() {startPreview(); };
                    });
            hm.put(1 /*"STREAMOUT"*/, new Command() {
                    public void executeStart() {startStreamOut();createStreamOutURL();};
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

            //Global Default Exception Handler
            final Thread.UncaughtExceptionHandler oldHandler = Thread.getDefaultUncaughtExceptionHandler();

            Thread.setDefaultUncaughtExceptionHandler(
                    new Thread.UncaughtExceptionHandler() {
                    @Override
                    public void uncaughtException(Thread paramThread, Throwable paramThrowable) {
                    //Close Camera   
                    Log.d(TAG,"Global uncaught Exception !!!!!!!!!!!" );
                    cam_preview.stopPlayback();
                    cam_streaming.stopRecording();
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
        cam_streaming.stopRecording();
        cam_preview.stopPlayback();
        streamPlay.onStop();
        if (dispSurface != null)
        	dispSurface.RemoveView();
    }

    public void setDeviceMode(int mode)
    {
        Log.d(TAG, " setDeviceMode "+ mode);
        device_mode = mode;
    }
    
    public void setXCoordinates(int x)
    {
        myconfig.setx(x);
        update();
    }

    public void setYCoordinates(int y)
    {
    	myconfig.sety(y);
        update();
    }

    public void setWindowSizeW(int w)
    {
        myconfig.setWidth(w);
        update();
    }

    public void setWindowSizeH(int h)
    {
        myconfig.setHeight(h);
        update();
    }

    
    public int getXCoordinates()
    {
        return CresStreamConfigure.getx();
    }

    public int getYCoordinates()
    {
        return CresStreamConfigure.gety();
    }

    public int getWindowSizeW()
    {
        return CresStreamConfigure.getWidth();
    }

    public int getWindowSizeH()
    {
        return CresStreamConfigure.getHeight();
    }
        
    
    public void update()
    {
    	if (dispSurface != null)
    	{
    		dispSurface.UpdateDimensions(CresStreamConfigure.getx(), 
    			CresStreamConfigure.gety(), 
    			CresStreamConfigure.getWidth(), 
    			CresStreamConfigure.getHeight());
    	}
    }
    
    public void readResolutionInfo(String hdmiInputResolution){
        hdmiInput.updateResolutionInfo(hdmiInputResolution);
    }
    
    void refreshResolutionInfo()
    {
        Log.i(TAG, "Refresh resolution info");
    	
    	//HDMI In
    	String hdmiInputResolution = MiscUtils.getHdmiInResolutionSysFs();
    	readResolutionInfo(hdmiInputResolution);
    
        /*if ((cam_preview.IsPreviewStatus()) == true)
        {
            //hdmiInputResolution = cam_streaming.mCameraPreviewObj.getHdmiInputResolution();
            hdmiInputResolution = cam_preview.getHdmiInputResolution();
            Log.i(TAG, "HDMI In Resolution " + hdmiInputResolution);
        }
        else if((cam_streaming.mCameraPreviewObj != null) && ((cam_streaming.isStreaming()) == true)){
            hdmiInputResolution = cam_preview.getHdmiInputResolution();
            //hdmiInputResolution = cam_streaming.mCameraPreviewObj.getHdmiInputResolution();
            Log.i(TAG, "HDMI In Resolution " + hdmiInputResolution);
        }
        else{
        	Camera cameraInstance = CresCamera.getCamera();//Camera.open(0);
        	if(cameraInstance != null){
        		hdmiInputResolution = cameraInstance.getHdmiInputStatus();
        		Log.i(TAG, "HDMI In Resolution " + hdmiInputResolution);
        	   	//cameraInstance.release();
                CresCamera.releaseCamera(cameraInstance);
        	}
        	cameraInstance = null;
        //}
        readResolutionInfo();
       */ 
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
    	return hdmiInput.getSyncStatus();
    }

    public String getHDMIInInterlacing()
    {
    	return hdmiInput.getInterlacing();
    }
    
    public String getHDMIInHorizontalRes()
    {
    	return hdmiInput.getHorizontalRes();
    }
    
    public String getHDMIInVerticalRes()
    {
    	return hdmiInput.getVerticalRes();
    }

    public String getHDMIInFPS()
    {
    	return hdmiInput.getFPS();
    }

    public String getHDMIInAspectRatio()
    {
    	return hdmiInput.getAspectRatio();
    }

    public String getHDMIInAudioFormat()
    {
    	return hdmiInput.getAudioFormat();
    }

    public String getHDMIInAudioChannels()
    {
    	return hdmiInput.getAudioChannels();
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

    public void setSessionInitMode(int mode)
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
	    		Log.d(TAG, "By TransmitterMode rtp streaming starts");
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
        return device_mode;
    }

    public void SetPasswdEnable()
    {
        StringBuilder sb = new StringBuilder(512);
        enable_passwd = true;
        disable_passwd = false;
        sb.append("PASSWD_DISABLE=false");
        sockTask.SendDataToAllClients(sb.toString());
    }

    public void SetPasswdDisable()
    {
        StringBuilder sb = new StringBuilder(512);
        enable_passwd = false;
        disable_passwd = true;
        sb.append("PASSWD_ENABLE=false");
        sockTask.SendDataToAllClients(sb.toString());
    }

    public void SetUserName(String uname){
        myconfig.setUserName(uname);
    }

    public void SetPasswd(String passwd){
        myconfig.setPasswd(passwd);
    }

    private void SendProcessingFb(String stats){
        StringBuilder sb = new StringBuilder(512);
        sb.append("PROCESSING_FB=").append(stats);
        sockTask.SendDataToAllClients(sb.toString());
    }

    private void SendStartFb(String stats){
        StringBuilder sb = new StringBuilder(512);
        sb.append("START=").append(stats);
        sockTask.SendDataToAllClients(sb.toString());
    }

    private void SendStopFb(String stats){
        StringBuilder sb = new StringBuilder(512);
        sb.append("STOP=").append(stats);
        sockTask.SendDataToAllClients(sb.toString());
    }

    private void SendPauseFb(String stats){
        StringBuilder sb = new StringBuilder(512);
        sb.append("PAUSE=").append(stats);
        sockTask.SendDataToAllClients(sb.toString());
    }


    //Ctrls
    public void Start(){
        SendProcessingFb("1");
    	playStatus="true";
    	stopStatus="false";
        pauseStatus="false";
        devicestatus = DeviceStatus.STARTED;
        hm.get(device_mode).executeStart();
        SendPauseFb("false");
        SendStopFb("false");
    }
    public void Stop(){
        SendProcessingFb("1");
    	playStatus="false";
    	stopStatus="true";
        pauseStatus="false";
        devicestatus = DeviceStatus.STOPPED;
        hm2.get(device_mode).executeStop();
        SendStartFb("false");
        SendPauseFb("false");
    }
    public void Pause(){
        SendProcessingFb("1");
        pauseStatus="true";
    	playStatus="false";
    	stopStatus="false";
        devicestatus = DeviceStatus.PAUSED;
        hm3.get(device_mode).executePause();
        SendStartFb("false");
        SendStopFb("false");
    }
    //StreamOut Ctrl & Config
    public void setIpAddress(String ip){
        if(ip!=null)
            myconfig.setIP(ip);	
    } 
    
    public void setEncodingResolution(int res){
        myconfig.setOutResolution(res);
    } 
    
    public void setTMode(String tmode){
        myconfig.setTransportMode(tmode);	
    } 
    
    public void setStreamProfile(String profile){
        myconfig.setVEncProfile(profile);	
    } 
    
    public void setVFrmRate(int vfr){
        myconfig.setVFrameRate(vfr);	
    } 
    
    public void setVbitRate(int vbr){
        myconfig.setVideoBitRate(vbr);	
    } 
    
    public void setRTSPPort(int _port){
	    myconfig.setRTSPPort(_port);	
    }
    public void setTSPort(int _port){
	    myconfig.setTSPort(_port);	
    }
    public void setRTPVideoPort(int _port){
	    myconfig.setRTPVPort(_port);	
    }
    public void setRTPAudioPort(int _port){
	    myconfig.setRTPAPort(_port);	
    }

    private String createStreamOutURL()
    {
        StringBuilder url = new StringBuilder(1024);
        String proto = "";
        String file = "";
        int port = 0;  
        String l_ipaddr= "";
        switch(myconfig.mode.getMode()){
            case 5:
            case 0:{
                       proto = "rtsp";
                       port = myconfig.getRTSPPort(); 
                       l_ipaddr = myconfig.getIP();
                       file = "/live.sdp";
                   }
                   break;
            case 1:{//Only RTP
                       proto = "rtp";
                       //l_ipaddr = "@";
                       l_ipaddr = myconfig.getUrl();
                       port = myconfig.getRTPVPort();

                   }
                   break;
            case 2:{
                       proto = "rtp";
                       l_ipaddr = myconfig.getUrl();
                       port = myconfig.getTSPort(); 
                   }
                   break;
            case 3:{
                       proto = "udp";
                       l_ipaddr = myconfig.getUrl();
                       port = myconfig.getTSPort(); 
                   }
                   break;
            default:
                   break;
        }
        url.append(proto).append("://").append(l_ipaddr).append(":").append(port).append(file);
        Log.d(TAG, "URL is "+url.toString());
        return url.toString();
    }

    public void startStreamOut()
    {
        StringBuilder sb = new StringBuilder(512);
        showPreviewWindow();
        out_url = createStreamOutURL();
        if((sessInitMode==0) && (myconfig.mode.getMode()!=0)){
            Toast.makeText(this, "Invalid Mode for this SessionInitation", Toast.LENGTH_LONG).show();
        }
        else{
            try {
                cam_streaming.startRecording();
            } catch(IOException e) {
                e.printStackTrace();
            }
            //Toast.makeText(this, "StreamOut Started", Toast.LENGTH_LONG).show();
            StreamOutstarted = true;
        }
        SendProcessingFb("0");
        sb.append("STREAMURL=").append(out_url);
        sockTask.SendDataToAllClients(sb.toString());
    }

    public void stopStreamOut()
    {
        if(StreamOutstarted){
            //Toast.makeText(this, "StreamOut Stopped", Toast.LENGTH_LONG).show();
            cam_streaming.stopRecording();
            StreamOutstarted = false;
            hidePreviewWindow();
            SendProcessingFb("0");
        }
    }
    
    public void pauseStreamOut()
    {
        Log.d(TAG, "Nothing todo");
    }

    private void hidePreviewWindow()
    {
        Log.d(TAG, "Preview Window hidden");
        if (dispSurface != null)
        	dispSurface.HideWindow();
    }
    
    private void showPreviewWindow()
    {
        Log.d(TAG, "Preview Window showing");
        if (dispSurface != null)
        	dispSurface.ShowWindow();
    }

    //StreamIn Ctrls & Config
    private void hideStreamInWindow()
    {
        Log.d(TAG, " streamin Window hidden ");
        if (dispSurface != null)
        	dispSurface.HideWindow();
    }

    private void showStreamInWindow()
    {
        Log.d(TAG, "streamin Window  showing");
        if (dispSurface != null)
        	dispSurface.ShowWindow();
    }

    public void EnableTcpInterleave(){
        Log.d(TAG, " EnableTcpInterleave");
        streamPlay.setRtspTcpInterleave(true);
    }

    public void setStreamInUrl(String ap_url)
    {
        out_url = ap_url;
        myconfig.setUrl(ap_url);
        streamPlay.setUrl(ap_url);
        if(ap_url.startsWith("rtp://@"))
            streamPlay.setRtpOnlyMode( myconfig.getRTPVPort(),  myconfig.getRTPAPort(), myconfig.getIP());
        else if(ap_url.startsWith("http://"))
            streamPlay.disableLatency();
        else
            Log.d(TAG, "No conditional Tags for StreamIn");
    }
    
    public void SetStreamInLatency(int initialLatency)
    {
        streamPlay.setLatency(initialLatency);
    }
    
    public String getStreamUrl()
    {
        return out_url;
    }

    public void startStreamIn()
    {
        showStreamInWindow();
        streamPlay.onStart();
        SendProcessingFb("0");
        //Toast.makeText(this, "StreamIN Started", Toast.LENGTH_LONG).show();
    }

    public void stopStreamIn()
    {
        streamPlay.onStop();
        //Toast.makeText(this, "StreamIN Stopped", Toast.LENGTH_LONG).show();
        hideStreamInWindow();
        SendProcessingFb("0");
    }

    public void pauseStreamIn()
    {
        streamPlay.onPause();
        SendProcessingFb("0");
        //TODO
    }

    public int getHorizontalResFb(){
        if(device_mode==DeviceMode.STREAM_IN.ordinal())
            return streamPlay.getMediaPlayerHorizontalResFb();
        else if(device_mode==DeviceMode.STREAM_OUT.ordinal())
            return cam_streaming.getStreamOutHorizontalResFb();
        else
            return 0;
    } 
    
    public int getVerticalResFb(){
        if(device_mode==DeviceMode.STREAM_IN.ordinal())
            return streamPlay.getMediaPlayerVerticalResFb();
        else if(device_mode==DeviceMode.STREAM_OUT.ordinal())
            return cam_streaming.getStreamOutVerticalResFb();
        else
            return 0;
    }
    
    public int getFpsFb(){
        if(device_mode==DeviceMode.PREVIEW.ordinal())
            return streamPlay.getMediaPlayerFpsFb();
        else if(device_mode==DeviceMode.STREAM_OUT.ordinal())
            return cam_streaming.getStreamOutFpsFb();
        else
            return 0;
    }
    
    public String getAspectRatioFb(){
        if(device_mode==DeviceMode.PREVIEW.ordinal())
            return streamPlay.getMediaPlayerAspectRatioFb();
        else if(device_mode==DeviceMode.STREAM_IN.ordinal())
                return cam_streaming.getStreamOutAspectRatioFb();
        else
            return "";
    }
    
    public int getAudioFormatFb(){
        if(device_mode==DeviceMode.PREVIEW.ordinal())
            return streamPlay.getMediaPlayerAudioFormatFb();
        else if(device_mode==DeviceMode.STREAM_IN.ordinal())
            return cam_streaming.getStreamOutAudioFormatFb();
        else
            return 0;
    }
    
    public int getAudiochannelsFb(){
        if(device_mode==DeviceMode.PREVIEW.ordinal())
            return streamPlay.getMediaPlayerAudiochannelsFb();
        else if(device_mode==DeviceMode.STREAM_IN.ordinal())
            return cam_streaming.getStreamOutAudiochannelsFb();
        else
            return 0;
    }


    //Preview 
    public void startPreview()
    {
        showPreviewWindow();
        cam_preview.startPlayback();
        SendProcessingFb("0");
        //Toast.makeText(this, "Preview Started", Toast.LENGTH_LONG).show();
    }

    public void stopPreview()
    {
        hidePreviewWindow();
        cam_preview.stopPlayback();
        SendProcessingFb("0");
        //Toast.makeText(this, "Preview Stopped", Toast.LENGTH_LONG).show();
    }
    
    public void pausePreview()
    {
        cam_preview.pausePlayback();
        SendProcessingFb("0");
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
	return "1";//"TODO:MISTRAL";
    }
    
    public String getProcessingStatus(){
	return "1";//"TODO:MISTRAL";
    }

    public String getElapsedSeconds(){
	return "0";//"TODO:MISTRAL";
    }

    public String getStreamStatus(){
	return "0";//"TODO:MISTRAL";
    }

    //Registering for HPD and Resolution Event detection	
    void registerBroadcasts(){
        Log.d(TAG, "registerBroadcasts !");
        resolutionEvent = new BroadcastReceiver()
        {
            public void onReceive(Context paramAnonymousContext, Intent paramAnonymousIntent)
            {
                if (paramAnonymousIntent.getAction().equals("evs.intent.action.hdmi.RESOLUTION_CHANGED"))
                {
                    int resolutionId = paramAnonymousIntent.getIntExtra("evs_hdmi_resolution_id", -1);
                    String hdmiInputResolution = null;
                    Log.i(TAG, "Received resolution changed broadcast !: " + resolutionId);
                    if ((device_mode==DeviceMode.PREVIEW.ordinal()) && (devicestatus == DeviceStatus.STARTED))  
                    {
                        Log.i(TAG, "Restart called due to resolution change broadcast ! ");
                        cam_preview.stopPlayback();
                        hidePreviewWindow();
                        SystemClock.sleep(cameraRestartTimout);
                        hpdHdmiEvent = 1;
                        Log.i(TAG, "HDMI resolutions - HRes:" + hdmiInput.getHorizontalRes() + " Vres:" + hdmiInput.getVerticalRes());
                        if ( (hdmiInput.getHorizontalRes().startsWith("0") != true) && (hdmiInput.getVerticalRes().startsWith("0")!= true)  &&
                                (resolutionId != 0))
                        {
                            showPreviewWindow();
                            cam_preview.startPlayback();
                        }

                    }
                    else if((cam_streaming.mCameraPreviewObj != null) && (device_mode==DeviceMode.STREAM_OUT.ordinal()) && (devicestatus == DeviceStatus.STARTED)){
                    	cam_streaming.stopRecording();
                        hidePreviewWindow();
                        SystemClock.sleep(10*cameraRestartTimout);
                        try{
                            showPreviewWindow();
                            cam_streaming.startRecording();
                        } catch(IOException e) {
                            e.printStackTrace();
                        }
                    }
                    else{
                        Log.i(TAG, " Nothing todo!!!");
                    }
                    if((hpdStateEnabled==1) && (device_mode==DeviceMode.PREVIEW.ordinal())){
                        SystemClock.sleep(cameraRestartTimout);
                        showPreviewWindow();
                        hpdHdmiEvent = 1;
                        cam_preview.startPlayback();
                    }
                }
            }
        };
        IntentFilter resolutionIntentFilter = new IntentFilter("evs.intent.action.hdmi.RESOLUTION_CHANGED");
        registerReceiver(resolutionEvent, resolutionIntentFilter);
        hpdEvent = new BroadcastReceiver()
        {
            public void onReceive(Context paramAnonymousContext, Intent paramAnonymousIntent)
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
                            cam_streaming.stopRecording();
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
                    Log.i(TAG, "Received hdmiout reoslution changed broadcast ! " + i);
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
