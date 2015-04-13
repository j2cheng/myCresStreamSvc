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
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.RelativeLayout;
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
    SurfaceManager sMGR;
    StreamIn streamPlay;
    BroadcastReceiver hpdEvent = null;
    BroadcastReceiver resolutionEvent = null;
    BroadcastReceiver hdmioutResolutionChangedEvent = null;
    private SurfaceView previewSurface;
    private SurfaceView streamingSurface = null;

    CresStreamConfigure myconfig;
    AudioManager amanager;
    TCPInterface sockTask;
   
    private RelativeLayout parentlayout;
    RelativeLayout.LayoutParams params_streamingview;
    RelativeLayout.LayoutParams params_preview;

    WindowManager.LayoutParams lp;
    WindowManager wm;

    HDMIInputInterface hdmiInput;
    HDMIOutputInterface hdmiOutput;

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
    int g_x = 0;
    int g_y = 0;
    int g_w = 0;
    int g_h = 0;
    boolean StreamOutstarted = false;

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
            final int windowWidth = 1920;
            final int windowHeight = 1080;
            
            //Stub: CSIO Cmd Receiver & TestApp functionality
            sockTask = new TCPInterface(this);
            sockTask.execute(new Void[0]);
            
            //Relative Layout to hanle multiple view
            parentlayout = new RelativeLayout(this);
            
            //Instance for Surfaceholder for StreamIn/Preview
            sMGR = new SurfaceManager(CresStreamCtrl.this);
            //StreamingIn View 
            streamingSurface = new SurfaceView(this);
            params_streamingview = new RelativeLayout.LayoutParams(
                  RelativeLayout.LayoutParams.WRAP_CONTENT,
                  RelativeLayout.LayoutParams.WRAP_CONTENT);
            parentlayout.addView(streamingSurface, params_streamingview);
            
            //Preview/StreamOut View
            previewSurface = new SurfaceView(this);
            params_preview = new RelativeLayout.LayoutParams(
                   RelativeLayout.LayoutParams.WRAP_CONTENT,
                   RelativeLayout.LayoutParams.WRAP_CONTENT);
            parentlayout.addView(previewSurface, params_preview);

            //Setting WindowManager and Parameters with system overlay
            lp = new WindowManager.LayoutParams(windowWidth, windowHeight, WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY, 0, PixelFormat.TRANSLUCENT);
            wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            lp.gravity = Gravity.TOP | Gravity.LEFT;
            lp.x = 0;
            lp.y = 0;
            //Adding Relative Layout to WindowManager
            wm.addView(parentlayout, lp); 

            
            //Get HPDEVent state frromsysfile
            hpdHdmiEvent = MiscUtils.getHdmiHpdEventState();
            Log.d(TAG, "hpdHdmiEvent :" + hpdHdmiEvent);

            //AudioManager
            amanager=(AudioManager)getSystemService(Context.AUDIO_SERVICE);
            //Input Streamout Config
            myconfig = new CresStreamConfigure();
            
            hdmiInput = new HDMIInputInterface();
            hdmiOutput = new HDMIOutputInterface();

            //refresh resolution on startup
            refreshResolutionInfo();

            //HPD and Resolution Event Registration
            registerBroadcasts();
            //Enable StreamIn and CameraPreview 
            SurfaceHolder streaminHolder = sMGR.getCresSurfaceHolder(streamingSurface);
            streamPlay = new StreamIn(CresStreamCtrl.this, streaminHolder);
            
            SurfaceHolder previewHolder = sMGR.getCresSurfaceHolder(previewSurface);
            cam_streaming = new CameraStreaming(CresStreamCtrl.this, previewHolder);
            cam_preview = new CameraPreview(previewHolder, hdmiInput);
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
        wm.removeView(parentlayout);
    }

    public void setDeviceMode(int mode)
    {
        Log.d(TAG, " setDeviceMode "+ mode);
        device_mode = mode;
    }
    
    public void setXCoordinates(int x)
    {
        g_x = x;
        update();
    }

    public void setYCoordinates(int y)
    {
        g_y = y;
        update();
    }

    public void setWindowSizeW(int w)
    {
        g_w = w;
        myconfig.setWidth(w);
        update();
    }

    public void setWindowSizeH(int h)
    {
        g_h = h;
        myconfig.setHeight(h);
        update();
    }
    
    private void update()
    {
        RelativeLayout.LayoutParams lp2=new RelativeLayout.LayoutParams(g_w, g_h);
        lp2.setMargins(g_x, g_y, 0, 0);
        if(device_mode==DeviceMode.STREAM_IN.ordinal())
            streamingSurface.setLayoutParams(lp2);
        else 
            previewSurface.setLayoutParams(lp2);
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
    }
    
    public void setStreamUnMute()
    {
        amanager.setStreamMute(AudioManager.STREAM_MUSIC, false);
    }

    public int getStreamState()
    {
        return device_mode;
    }


    //Ctrls
    public void Start(){
    	playStatus="true";
    	stopStatus="false";
        pauseStatus="false";
        devicestatus = DeviceStatus.STARTED;
        hm.get(device_mode).executeStart();
    }
    public void Stop(){
    	playStatus="false";
    	stopStatus="true";
        pauseStatus="false";
        devicestatus = DeviceStatus.STOPPED;
        hm2.get(device_mode).executeStop();
    }
    public void Pause(){
        pauseStatus="true";
    	playStatus="false";
    	stopStatus="false";
        devicestatus = DeviceStatus.PAUSED;
        hm3.get(device_mode).executePause();
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
        String proto = null;
        String file = "";
        int port = 0;  
        String l_ipaddr = myconfig.getIP();
        switch(myconfig.mode.getMode()){
            case 5:
            case 0:{
                       proto = "rtsp";
                       port = myconfig.getRTSPPort(); 
                       file = "/live.sdp";
                   }
                   break;
            case 1:{//Only RTP
                       proto = "rtp";
                       //l_ipaddr = "@";
                       port = myconfig.getRTPVPort();

                   }
                   break;
            case 2:{
                       proto = "rtp";
                       port = myconfig.getTSPort(); 
                   }
                   break;
            case 3:{
                       proto = "udp";
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
    }

    public void stopStreamOut()
    {
        if(StreamOutstarted){
            //Toast.makeText(this, "StreamOut Stopped", Toast.LENGTH_LONG).show();
            cam_streaming.stopRecording();
            StreamOutstarted = false;
            hidePreviewWindow();
        }
    }
    
    public void pauseStreamOut()
    {
        Log.d(TAG, "Nothing todo");
    }

    private void hidePreviewWindow()
    {
        Log.d(TAG, "Preview Window hidden");
       previewSurface.setVisibility(8);
    }
    
    private void showPreviewWindow()
    {
        Log.d(TAG, "Preview Window showing");
       previewSurface.setVisibility(0);
    }

    //StreamIn Ctrls & Config
    private void hideStreamInWindow()
    {
        Log.d(TAG, " streamin Window hidden ");
       streamingSurface.setVisibility(8);
    }

    private void showStreamInWindow()
    {
        Log.d(TAG, "streamin Window  showing");
        streamingSurface.setVisibility(0);
    }

    public void EnableTcpInterleave(){
        Log.d(TAG, " EnableTcpInterleave");
        streamPlay.setRtspTcpInterleave(true);
    }

    public void setStreamInUrl(String ap_url)
    {
        out_url = ap_url;
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
        //Toast.makeText(this, "StreamIN Started", Toast.LENGTH_LONG).show();
    }

    public void stopStreamIn()
    {
        streamPlay.onStop();
        //Toast.makeText(this, "StreamIN Stopped", Toast.LENGTH_LONG).show();
        hideStreamInWindow();
    }

    public void pauseStreamIn()
    {
        streamPlay.onPause();
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
