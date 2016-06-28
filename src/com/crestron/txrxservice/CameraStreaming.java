package com.crestron.txrxservice;

import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.crestron.txrxservice.AudioPlayback.StreamAudioTask;
import com.crestron.txrxservice.CresStreamCtrl.StreamState;
import com.crestron.txrxservice.ProductSpecific;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.hardware.Camera.ErrorCallback;
import android.hardware.Camera.PreviewCallback;
import android.media.MediaRecorder;
import android.media.MediaRecorder.OnErrorListener;
import android.os.FileObserver;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

public class CameraStreaming {
    private SurfaceHolder surfaceHolder;
    private FileObserver activeClientObserver;
    MediaRecorder mrec;
    String TAG = "TxRx CameraStreamer";
    String filename;
    File file4Recording;
    boolean out_stream_status = false;
    boolean is_pause = false;
    CresStreamCtrl streamCtl;
    private int idx = 0;
    private int streamOutWidth = 0;
    private int streamOutHeight = 0;
    private int streamOutFps = 0;
    private int streamOutAudioChannels = 0;
    private int streamOutAudioFormat = 0;
    private long statisticsNumVideoPackets = 0;
    private int statisticsNumVideoPacketsDropped = 0;
    private long statisticsNumAudioPackets = 0;
    private int statisticsNumAudioPacketsDropped = 0;
    private boolean confidencePreviewRunning = false;
    private final long stopTimeout_ms = 15000;
    private final long startTimeout_ms = 20000;
    private final String clientConnectedFilePath = "/dev/shm/crestron/CresStreamSvc/clientConnected";
    private final int audioSampleRate = 48000;

    private boolean shouldExit = false;
    private Thread statisticsThread;
    private final long statisticsThreadPollTime = 1000;

    //public CameraStreaming(CresStreamCtrl mContext, SurfaceHolder lpHolder ) {
    public CameraStreaming(CresStreamCtrl mContext) {
    	Log.d(TAG, "CameraStreaming :: Constructor called.....");
        streamCtl = mContext;
    }
   
    public void setSessionIndex(int id){
        idx = id;
    }
    
    protected void restartCamera() {
    	try {
    		boolean pauseStatus = is_pause;
	    	stopRecording(false);
	    	is_pause = pauseStatus;
	    	startRecording();
    	} catch (Exception e) { e.printStackTrace(); }
    }

    protected void startRecording() throws IOException {
    	final CountDownLatch latch = new CountDownLatch(1);
    	Thread startThread = new Thread(new Runnable() {
    		public void run() {
    			String streamIp = "";
    			
    			if (is_pause == true)
    			{
    				resumePlayback();
    				latch.countDown();
    				return;
    			}

		        if(out_stream_status==true)
		            stopRecording(false);
		        Log.d(TAG, "startRecording");
		        
		        streamCtl.setSystemVolume((int)streamCtl.userSettings.getVolume());
		        
		        boolean isDirExists = true;
		        File path = new File("/dev/shm/crestron/CresStreamSvc");
		        if(!path.exists()){
		            isDirExists = path.mkdir();
		        }
		
		        filename = "/rec001.mp4";
		        Log.d(TAG, "CamTest: Camera Recording Filename: " + filename);
		
		        // create empty file it must use
		        file4Recording = new File(path, filename);
		        if (file4Recording == null)
		        {
		            Log.d(TAG, "CamTest: file() returned null");
		        }
		        else
		        {
		            Log.d(TAG, "CamTest: file() didn't return null");
		        }
		
		        if (CheckForValidIp() == false)
		        {
		        	streamCtl.cam_preview.setSessionIndex(idx);
			    	streamCtl.cam_preview.startPlayback(true);			    	
			    	confidencePreviewRunning = true;
		        	latch.countDown();
		        	return;
		        }
		
		        mrec = new MediaRecorder();
		        CresCamera.openCamera();
		        // This is here because moved out of openCamera
		        ProductSpecific.getHdmiInputStatus();			
		        if(CresCamera.mCamera != null){
		        	if (streamCtl.userSettings.getEncodingResolution(idx) == 0) // if in auto mode set framerate to input framerate
		        		ProductSpecific.setEncoderFps(CresCamera.mCamera, Integer.parseInt(streamCtl.hdmiInput.getFPS()), Integer.parseInt(streamCtl.hdmiInput.getFPS()));
		        	else
		        		ProductSpecific.setEncoderFps(CresCamera.mCamera, streamCtl.userSettings.getEncodingFramerate(idx), Integer.parseInt(streamCtl.hdmiInput.getFPS()));
		        	CresCamera.mCamera.unlock();
		            mrec.setCamera(CresCamera.mCamera);
		
		            mrec.setAudioSamplingRate(audioSampleRate);
		            mrec.setAudioSource(MediaRecorder.AudioSource.MIC);
		            mrec.setVideoSource(MediaRecorder.VideoSource.CAMERA);		            
                            Log.d(TAG, "selected mode is " + getStreamTransportMode());
		            ProductSpecific.setStreamTransportMode(mrec, getStreamTransportMode());
		            		            
		            //Set Port
		            int l_port;
		            int currentSessionInitiation = streamCtl.userSettings.getSessionInitiation(idx);
		            int currentTransportMode = streamCtl.userSettings.getTransportMode(idx);
		            if ((currentSessionInitiation == 0) || (currentSessionInitiation == 2)) //Multicast via RTSP or By Receiver
		            {
                                if(streamCtl.userSettings.isPasswordEnable(idx) && !(streamCtl.userSettings.isPasswordDisable(idx)))
                                {
                                    Log.d(TAG, "Auth Enabled " );
                                    ProductSpecific.setRtspAuthentication(mrec, 1);
                                    ProductSpecific.setRtspSessionUserName(mrec, streamCtl.userSettings.getUserName(idx));
                                    ProductSpecific.setRtspSessionPassword(mrec, streamCtl.userSettings.getPassword(idx));
                                }
                                else{
                                    Log.d(TAG, "Auth Disabled " );
                                    ProductSpecific.setRtspAuthentication(mrec, 0);
                                }

		            	if(streamCtl.userSettings.getProxyEnable(idx))
                        {
		            		l_port = streamCtl.userSettings.getInternalRtspPort(idx);
                            streamIp = "127.0.0.1";
                        }
                        else
                        {
		            		l_port = streamCtl.userSettings.getRtspPort(idx);
		            		streamIp = streamCtl.userSettings.getDeviceIp();
                        }
		            	
		            	ProductSpecific.setRTSPPort(mrec, l_port);
		                
		                if (currentSessionInitiation == 2) //Multicast via RTSP
		                	ProductSpecific.setMcastIP(mrec, streamCtl.userSettings.getMulticastAddress(idx));
		            	ProductSpecific.setRtspSessionName(mrec, streamCtl.userSettings.getRtspSessionName());
		            }
		            else //Multicast via UDP or By Transmitter
		            {
		            	if (currentSessionInitiation == 1)	//By Transmitter
		            		streamIp = streamCtl.userSettings.getStreamOutUrl(idx);
		            	else if (currentSessionInitiation == 3) //Multicast via UDP
		            		streamIp = streamCtl.userSettings.getMulticastAddress(idx);
		            	
		            	if (currentTransportMode == 0)	//RTP
		            	{
		                    l_port = streamCtl.userSettings.getRtpAudioPort(idx);
		                    ProductSpecific.setRTPAudioPort(mrec, l_port);
		                    l_port = streamCtl.userSettings.getRtpVideoPort(idx);
		                    ProductSpecific.setRTPVideoPort(mrec, l_port);
		            	}
		            	else
		            	{
		                     l_port = streamCtl.userSettings.getTsPort(idx);
		                     ProductSpecific.setMPEG2TSPort(mrec, l_port);
		            	}
		            }
		            
		            ProductSpecific.setDestinationIP(mrec, streamIp);
		            
		            mrec.setOutputFormat(9);//Streamout option set to Stagefright Recorder
		            if ((currentSessionInitiation == 2) || (currentSessionInitiation == 3))
		            	Log.d(TAG, "ip addr " + streamCtl.userSettings.getMulticastAddress(idx));
		            else
		            	Log.d(TAG, "ip addr " + streamCtl.userSettings.getDeviceIp());
		            Log.d(TAG, "setting profile: " + streamCtl.userSettings.getStreamProfile(idx).getVEncProfile());
		            Log.d(TAG, "setting video encoder level: " + streamCtl.userSettings.getEncodingLevel(idx));
		            Log.d(TAG, "setting video frame rate: " + streamCtl.userSettings.getEncodingFramerate(idx));
		            
		            setWidthAndHeightFromEncRes(idx);
		            mrec.setVideoEncodingBitRate(streamCtl.userSettings.getBitrate(idx) * 1000);	//This is in bits per second
		            mrec.setVideoFrameRate(streamCtl.userSettings.getEncodingFramerate(idx));//Mistral Propietary API 
		            mrec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
		            mrec.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
		            ProductSpecific.setEncoderProfile(mrec, streamCtl.userSettings.getStreamProfile(idx).getVEncProfile());
		            ProductSpecific.setVideoEncoderLevel(mrec, streamCtl.userSettings.getEncodingLevel(idx));            
		            mrec.setOutputFile(path + filename);   
		
		            mrec.setPreviewDisplay(streamCtl.getCresSurfaceHolder(idx).getSurface());	//TODO: put back in when preview audio works
		            try
		            {
		            	mrec.setOnErrorListener(new ErrorListner());
			            mrec.prepare();
			            mrec.start();
			            //Must be called after start(), send status feedbacks on first frame coming in
			            CresCamera.mCamera.setPreviewCallback(new PreviewCB());
			            CresCamera.mCamera.setErrorCallback(new ErrorCB());
		            }
		            catch (Exception ex) 
		            {
		            	// Mark media server error so that streams will be restarted
		            	streamCtl.mMediaServerCrash = true;
		            	ex.printStackTrace();
		            }
		
		            if ((currentSessionInitiation != 0) && (currentSessionInitiation != 2) && (currentTransportMode == 0)) {	//TODO: causes crash in RTSP modes currently being worked around
		            	String sb = ProductSpecific.getSDP(mrec);
		                Log.d(TAG, "########SDP Dump######\n" + sb);
		            }
		            
		            //TS UDP and TS RTP streaming modes do not call PreviewCB so send started state
		            if ((currentTransportMode == 1) || (currentTransportMode == 2))
		            {
		            	if ((currentSessionInitiation == 0) || (currentSessionInitiation == 2))
		            	{
    						streamCtl.SendStreamState(StreamState.STREAMERREADY, idx);
    						monitorRtspClientActiveConnections();
		            	}
    					else
    						streamCtl.SendStreamState(StreamState.STARTED, idx);
		            	startStatisticsTask();
		            }
		           
		            out_stream_status = true;
		            
		                      
		        }
		        else {
		        	stopRecording(false);
		            Log.e(TAG, "Camera Resource busy or not available !!!!");
		            file4Recording.delete();
		            mrec = null;
		        }
		        
		        latch.countDown();
    		}
    	});
    	startThread.start();
    	
    	// We launch the start command in its own thread and timeout in case mediaserver gets hung
    	boolean successfulStart = true; //indicates that there was no time out condition
    	try { successfulStart = latch.await(startTimeout_ms, TimeUnit.MILLISECONDS); }
    	catch (InterruptedException ex) { ex.printStackTrace(); }
    	
    	streamCtl.checkVideoTimeouts(successfulStart);
    	if (!successfulStart)
    	{
    		Log.e(TAG, String.format("MediaServer failed to start after %d ms", startTimeout_ms));
    		startThread.interrupt(); //cleanly kill thread
    		startThread = null;
    		streamCtl.RecoverDucati();
    	}
    }
    
    void monitorRtspClientActiveConnections() 
    {
    	File activeClientConnection = new File (clientConnectedFilePath);
        if (!activeClientConnection.isFile())	//check if file exist
        {
        	try {
            	activeClientConnection.getParentFile().mkdirs();
            	activeClientConnection.createNewFile();
        	} catch (Exception e) {}
        }
        
        //send initial state
        sendRtspStreamState(true);
        activeClientObserver = new FileObserver(clientConnectedFilePath, FileObserver.CLOSE_WRITE) {						
			@Override
			public void onEvent(int event, String path) {
				Log.i(TAG, String.format("Received FileObserver event, sending rtspStream State, event = %d, path = %s", event, path)); //my change remove
				if (event != 32768)//32768 is IN_IGNORED and should not send streamstate
					sendRtspStreamState(false);
			}
		};
		activeClientObserver.startWatching();
    }
    
    private void sendRtspStreamState(boolean firstRun)
    {
    	// If firstRun is true only send feedback if client is connected
    	int val = 0;
		BufferedReader br = null;
		try {
			String sCurrentLine;
			br = new BufferedReader(new FileReader(clientConnectedFilePath));
			while ((sCurrentLine = br.readLine()) != null) {
				val = Integer.parseInt(sCurrentLine);
			}

		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (br != null)
					br.close();
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}
		if(val > 0)
			streamCtl.SendStreamState(StreamState.STARTED, idx);
		else if (firstRun == false)
		    streamCtl.SendStreamState(StreamState.STREAMERREADY, idx);
    }

    private boolean CheckForValidIp()
    {
    	boolean isMulticast = false;
    	String streamIp = "";
    	String IPV4_REGEX = "(([0-1]?[0-9]{1,2}\\.)|(2[0-4][0-9]\\.)|(25[0-5]\\.)){3}(([0-1]?[0-9]{1,2})|(2[0-4][0-9])|(25[0-5]))";
    	String Multicast_REGEX = "((22[4-9])|(23[0-9]))\\.(?:(([0-1]?[0-9]{1,2})|(2[0-4][0-9])|(25[0-5]))\\.){2}(([0-1]?[0-9]{1,2})|(2[0-4][0-9])|(25[0-5]))";
    	Pattern regexPattern;
    	
    	int currentSessionInitiation = streamCtl.userSettings.getSessionInitiation(idx);
        int currentTransportMode = streamCtl.userSettings.getTransportMode(idx);
        if ((currentSessionInitiation == 0) || (currentSessionInitiation == 2)) //Multicast via RTSP or By Receiver
        {
        	streamIp = streamCtl.userSettings.getDeviceIp();
        	if (currentSessionInitiation == 2) //Multicast via RTSP
        	{
        		isMulticast = true;
        		// First check device IP for validity
        		regexPattern = Pattern.compile(IPV4_REGEX);
        		if (regexPattern.matcher(streamIp).matches() == false)
            	{
        			streamCtl.SendStreamState(StreamState.CONNECTREFUSED, idx);
        			Log.e(TAG, String.format("Tried to stream to invalid address, address = %s", streamIp));
            		streamCtl.SendStreamState(StreamState.STOPPED, idx);
            		return false;
            	}
        		
        		// if device IP ok then check multicast
        		streamIp = streamCtl.userSettings.getMulticastAddress(idx);
        	}
        }
        else
        {
        	if (currentSessionInitiation == 1)	//By Transmitter
        		streamIp = streamCtl.userSettings.getStreamOutUrl(idx);
        	else if (currentSessionInitiation == 3) //Multicast via UDP
        	{
        		streamIp = streamCtl.userSettings.getMulticastAddress(idx);
        		isMulticast = true;
        	}
        }
        
        if (isMulticast)
        	regexPattern = Pattern.compile(Multicast_REGEX);
        else
        	regexPattern = Pattern.compile(IPV4_REGEX);
    	
    	Log.e(TAG, String.format("Checking address = %s", streamIp));
    	if (regexPattern.matcher(streamIp).matches() == false)
    	{
    		streamCtl.SendStreamState(StreamState.CONNECTREFUSED, idx);
			Log.e(TAG, String.format("Tried to stream to invalid address, address = %s", streamIp));
    		streamCtl.SendStreamState(StreamState.STOPPED, idx);
    		return false;
    	}
    	else
    		return true;
    }

	/**
	 *  Use the following conversion
	 *  0,Auto (follows input)
		1,176x144
		2,352x288
		3,528x384
		4,640x360
		5,640x480
		6,720x480
		7,800x480
		8,800x600
		9,1024x768
		10,1280x720
		11,1280x800
		12,1366x768
		13,1440x900
		14,1600x900
		15,1600x1200
		16,1680x1050
		17,1920x1080
 
	 */
	private void setWidthAndHeightFromEncRes(int sessionId) 
	{
		// Start with current hdmi input values
		streamOutWidth = Integer.valueOf(streamCtl.hdmiInput.getHorizontalRes());
		streamOutHeight = Integer.valueOf(streamCtl.hdmiInput.getVerticalRes());
		
		if ((streamOutWidth == 1366) && (streamOutHeight == 768))//FIXME: Currently Encoder does not accept 1366x768, workaround is set to 1280x720 (10)
		{
			streamOutWidth = 1280;
			streamOutHeight = 720;
		}
		else if ((streamOutWidth == 1400) && (streamOutHeight == 1050))//FIXME: Currently Encoder does not accept 1400x1050, workaround is set to 1024x768 (9)
		{
			streamOutWidth = 1024;
			streamOutHeight = 768;
		}

		switch (streamCtl.userSettings.getEncodingResolution(sessionId))
		{
			case 1: 
				streamOutWidth = 176;
				streamOutHeight = 144;
				break;
			
			case 2: 
				streamOutWidth = 352;
				streamOutHeight = 288;
				break;
			
			case 3: 
				streamOutWidth = 528;
				streamOutHeight = 384;
				break;
			
			case 4: 
				streamOutWidth = 640;
				streamOutHeight = 360;
				break;
			
			case 5: 
				streamOutWidth = 640;
				streamOutHeight = 480;
				break;
			
			case 6: 
				streamOutWidth = 720;
				streamOutHeight = 480;
				break;
			
			case 7: 
				streamOutWidth = 800;
				streamOutHeight = 480;
				break;
			
			case 8: 
				streamOutWidth = 800;
				streamOutHeight = 600;
				break;
			
			case 9: 
				streamOutWidth = 1024;
				streamOutHeight = 768;
				break;
			
			case 10: 
				streamOutWidth = 1280;
				streamOutHeight = 720;
				break;
			
			case 11: 
				streamOutWidth = 1280;
				streamOutHeight = 800;
				break;
			
			case 12: 
				streamOutWidth = 1366;
				streamOutHeight = 768;
				break;
			
			case 13: 
				streamOutWidth = 1440;
				streamOutHeight = 900;
				break;
			
			case 14: 
				streamOutWidth = 1600;
				streamOutHeight = 900;
				break;
			
			case 15: 
				streamOutWidth = 1600;
				streamOutHeight = 1200;
				break;
			
			case 16: 
				streamOutWidth = 1680;
				streamOutHeight = 1050;
				break;
			
			case 17: 
				streamOutWidth = 1920;
				streamOutHeight = 1080;
				break;
			
			case 0:
			{
				// If input is not a valid resolution, just stream out at 640x480
				if (streamCtl.hdmiInput.getResolutionIndex() == 0)
				{
					streamOutWidth = 640;
					streamOutHeight = 480;
				}
				break;
			}
				
			default:
				break;
		
		}
			
        Log.d(TAG, "setting width: " + streamOutWidth);
        Log.d(TAG, "setting height: " + streamOutHeight);
        
        //TODO: what if streamOutWidth or streamOutHeight is 0
			
		mrec.setVideoSize(streamOutWidth, streamOutHeight);
	}
    
    private int getStreamTransportMode() // TODO: MJPEG mode will never be set
    {
    	//RTSP(0), RTP(1), MPEG2TS_RTP(2), MPEG2TS_UDP(3), MJPEG(4), MRTSP(5);
    	int currentSessionInitiation = streamCtl.userSettings.getSessionInitiation(idx);
        int currentTransportMode = streamCtl.userSettings.getTransportMode(idx);
        
        if (currentSessionInitiation == 0){
        	if (currentTransportMode == 0)
        		return 0;	//RTP mode (By Transmitter or Multicast via UDP)
        	else if (currentTransportMode == 1)
        		return 6; 	//TS over RTP mode (By Transmitter or Multicast via UDP)
        	else if (currentTransportMode == 2)
        		return 7;	//TS over UDP mode (By Transmitter or Multicast via UDP)
        }
        else if (currentSessionInitiation == 2){
        	if (currentTransportMode == 0)
        		return 5;	//RTP mode (By Transmitter or Multicast via UDP)
        	else if (currentTransportMode == 1)
        		return 8; 	//TS over RTP mode (By Transmitter or Multicast via UDP)
        	else if (currentTransportMode == 2)
        		return 9;	//TS over UDP mode (By Transmitter or Multicast via UDP)
        	//return 5;	//RTSP multicast mode (Multicast via RTSP)
        }
        else
        {
        	if (currentTransportMode == 0)
        		return 1;	//RTP mode (By Transmitter or Multicast via UDP)
        	else if (currentTransportMode == 1)
        		return 2; 	//TS over RTP mode (By Transmitter or Multicast via UDP)
        	else if (currentTransportMode == 2)
        		return 3;	//TS over UDP mode (By Transmitter or Multicast via UDP)
        }    
        
        Log.e(TAG, "Invalid Mode !!!!, SessInit = " + currentSessionInitiation + ", TransMode = " + currentTransportMode);
        return 0; //correct thing to do?????
    }

    public String updateSvcWithStreamStatistics(){
            //return mrec.getStatisticsData();
    	return "";
    }

    public boolean isStreaming(){
        return out_stream_status;
    }
    
    public void setHdcpEncrypt(boolean isHdcpEncrypt)
    {
        //This routine get called after start
        int val = (isHdcpEncrypt) ? 1 : 0;
        
        if(mrec != null)
        	ProductSpecific.setHdcpEncrypt(mrec, val);
        else
            Log.d(TAG, "HdcpEncrypt mrec is null");

    }

    private void releaseMediaRecorder() {
        if (mrec != null) {
            mrec.reset(); // clear recorder configuration
            mrec.release(); // release the recorder object
            mrec = null;
        }
    }

    public void stopRecording(final boolean hpdEventAction) {
    	final CountDownLatch latch = new CountDownLatch(1);
    	Thread stopThread = new Thread(new Runnable() {
    		public void run() {
		        Log.d(TAG, "stopRecording");
                int currSessionInitiation = streamCtl.userSettings.getSessionInitiation(idx);
                try {
	    			if ((activeClientObserver != null) && ((currSessionInitiation == 0) || (currSessionInitiation == 2)))
	    			    activeClientObserver.stopWatching();
                } catch (Exception e) { e.printStackTrace(); }
    			
    			try {
        			if(hpdEventAction==true){
		            	if (CresCamera.mCamera != null)
		            	{
		            		CresCamera.mCamera.setPreviewCallback(null);
		            		CresCamera.mCamera.lock(); //Android recommends this
		            		CresCamera.releaseCamera();
		            	}
		            }
    			} catch (Exception e) { e.printStackTrace(); }
    			try {
			        if (mrec != null) {				            
			            mrec.stop();
			            mrec.setPreviewDisplay(null);				            
			            releaseMediaRecorder();
					}
    			} catch (Exception e) { e.printStackTrace(); }
    			try {
	    			if(hpdEventAction==false){
		            	if (CresCamera.mCamera != null)
		            	{
		            		CresCamera.mCamera.setPreviewCallback(null);
		            		CresCamera.mCamera.lock(); //Android recommends this
		            		CresCamera.releaseCamera();
		            	}
		            }
    			} catch (Exception e) { e.printStackTrace(); }
    			
    			out_stream_status = false;
	            streamCtl.setPauseVideoImage(false);
	            is_pause = false;
	            stopStatisticsTask();
	            
	            //Delete clientConnectedFilePath
	            File activeClientConnection = new File (clientConnectedFilePath);
	            if (activeClientConnection.isFile())	//check if file exist
	            {
	            	boolean deleteSuccess = activeClientConnection.delete();
	            	if (deleteSuccess == false)
	            		Log.e(TAG, String.format("Unable to delete %s", clientConnectedFilePath));
	            }
	            
	            streamCtl.SendStreamState(StreamState.STOPPED, idx);
	            
	            // Zero out statistics on stop
	            streamOutAudioFormat = streamOutAudioChannels = streamOutWidth = streamOutHeight = streamOutFps = 0;
				streamCtl.SendStreamOutFeedbacks();
		        
		        latch.countDown();
    		}
    	});
    	stopThread.start();

    	// We launch the stop commands in its own thread and timeout in case mediaserver gets hung
    	boolean successfulStop = true; //indicates that there was no time out condition
    	try { successfulStop = latch.await(stopTimeout_ms, TimeUnit.MILLISECONDS); }
    	catch (InterruptedException ex) { ex.printStackTrace(); }
    	
    	streamCtl.checkVideoTimeouts(successfulStop);
    	if (!successfulStop)
    	{
    		Log.e(TAG, String.format("MediaServer failed to stop after %d ms", stopTimeout_ms));
    		stopThread.interrupt(); //cleanly kill thread
    		stopThread = null;
    		streamCtl.SendStreamState(StreamState.STOPPED, idx);
    		streamCtl.RecoverDucati();
    		try {
    			Thread.sleep(2000);
    		} catch (Exception e) {}
    		streamCtl.RecoverMediaServer();
    	}
    }
    
    public boolean pausePlayback()
    {
    	if (confidencePreviewRunning == false)
    	{
	    	Log.d(TAG, "pausePlayback");
	        try
	        {
	        	streamCtl.setPauseVideoImage(true);
	        	is_pause = true;
	        	streamCtl.SendStreamState(StreamState.PAUSED, idx);
	        	return true;
	        } catch (Exception e)
	        {
	        	return false;
	        }	        
    	}
    	return false;
    }
    
    public boolean resumePlayback()
    {
    	Log.d(TAG, "resumePlayback");
        try
        {
        	streamCtl.setPauseVideoImage(false);
        	is_pause = false;
        	streamCtl.SendStreamState(StreamState.STARTED, idx);
        	return true;
        } catch (Exception e)
        {
        	return false;
        }
    }
    
    //Response to CSIO Layer
    public boolean getStreamOutStatus()
    {
        //TODO
        return true; 
    }

    public int getStreamOutFpsFb(){
    	return streamOutFps;
    }

    public String getStreamOutAspectRatioFb(){
        return MiscUtils.calculateAspectRatio(streamOutWidth, streamOutHeight);
    }

    public int getStreamOutAudioFormatFb(){
        return streamOutAudioFormat;
    }

    public int getStreamOutAudiochannelsFb(){
        return streamOutAudioChannels;
    }

	public int getStreamOutWidth() {
		return streamOutWidth;
	}

	public void setStreamOutWidth(int streamOutWidth) {
		this.streamOutWidth = streamOutWidth;
	}

	public int getStreamOutHeight() {
		return streamOutHeight;
	}

	public void setStreamOutHeight(int streamOutHeight) {
		this.streamOutHeight = streamOutHeight;
	}
	
    public long getStreamOutNumVideoPackets() {
		return statisticsNumVideoPackets;
	}
	
	public int getStreamOutNumVideoPacketsDropped() {
		return statisticsNumVideoPacketsDropped;
	}
	
	public long getStreamOutNumAudioPackets() {
		return statisticsNumAudioPackets;
	}
	
	public int getStreamOutNumAudioPacketsDropped() {
		return statisticsNumAudioPacketsDropped;
	}
	
	public void resetStatistics(int sessId) {
		//TODO: command to tell native code to reset statistics
//		statisticsNumVideoPackets = 0;
//        statisticsNumVideoPacketsDropped = 0;
//        statisticsNumAudioPackets = 0;
//        statisticsNumAudioPacketsDropped = 0;
//        
//        streamCtl.SendStreamOutFeedbacks();
	}
	
	protected void startStatisticsTask(){
		shouldExit = false;
		statisticsThread = new Thread(new StatisticsTask());    	
    	statisticsThread.start();
    }
	
	protected void stopStatisticsTask(){
		if (this.statisticsThread != null)
		{
	    	shouldExit = true;
	    	try
	        {
	        	this.statisticsThread.join();
	        }
	        catch (InterruptedException localInterruptedException)
	        {
	            localInterruptedException.printStackTrace();
	        }
		}
    }

    class StatisticsTask implements Runnable {
    	public void run() {
    		/*
    		 * Example getStatisticsData response:
			Statistics
			**********

			Codec
			*****
			Audio:
			Codec Type: AAC
			SampleRate: 44100
			Audio Channel: 2

			Video
			Codec Type: H264
			Resolution: 1920 x 1080
			Frame Rate: 30
			Encoder Profile: High Profile
			Encoder Level: 4.2

			Audio
			Encoded Frames: 196
			Bytes Sent: 27365
			Video
			Encoded Frames: 252
			Bytes Sent: 446387 */
    		
    		Pattern regexP;
			Matcher regexM;
			String statisticsString;
    		try
            {
    			// This function crashes if MediaServer is in a bad state, wait 5 seconds before calling
    			Thread.sleep(5000);
    			if (shouldExit)
    				return;
    			
    			if (mrec != null)
    			{
    				statisticsString = ProductSpecific.getStatisticsData(mrec);
					
					// Pull out Audio number of channels
					regexP = Pattern.compile("Audio\\s+Channel:\\s+(\\d+)");
					regexM = regexP.matcher(statisticsString);
					if (regexM.find())
						streamOutAudioChannels = Integer.parseInt(regexM.group(1));
					
					// Pull out Video width and height
					regexP = Pattern.compile("Resolution:\\s+(\\d+)\\s+x\\s+(\\d+)");	//width x height
					regexM = regexP.matcher(statisticsString);			
					if (regexM.find())
					{
						streamOutWidth = Integer.parseInt(regexM.group(1));
						streamOutHeight = Integer.parseInt(regexM.group(2));
					}
					
					// Pull out Video frames per second
					regexP = Pattern.compile("Frame\\s+Rate:\\s+(\\d+)");
					regexM = regexP.matcher(statisticsString);
					if (regexM.find())
						streamOutFps = Integer.parseInt(regexM.group(1));
					
					streamOutAudioFormat = 1; //TODO: currently we are always setting this to PCM (1)
					
					streamCtl.SendStreamOutFeedbacks();
    			}
            }
            catch (Exception e)
            {
            	Log.d(TAG, "Failed to query statistics from mediaServer");
            }

    		while(!shouldExit)
    		{    	
    			try
    			{
    				if (streamCtl.userSettings.isStatisticsEnable(idx))
    				{
    					if (mrec == null)
    						continue;

    					statisticsString = ProductSpecific.getStatisticsData(mrec);

    					// Pull out number of Video packets
    					regexP = Pattern.compile("Video\\s+Encoded Frames:\\s+(\\d+)");
    					regexM = regexP.matcher(statisticsString);
    					if (regexM.find())
    						statisticsNumVideoPackets = Long.valueOf(regexM.group(1));

    					// Pull out number of Audio packets
    					regexP = Pattern.compile("Audio\\s+Encoded Frames:\\s+(\\d+)");
    					regexM = regexP.matcher(statisticsString);
    					if (regexM.find())
    						statisticsNumAudioPackets = Long.valueOf(regexM.group(1));

    					streamCtl.SendStreamOutFeedbacks();
    				}
    				// Sleep last so that we dont call getStatisticsData while trying to stop					
    				Thread.sleep(statisticsThreadPollTime);
    			}
    			catch (Exception e)
    			{
    				Log.v(TAG, "Failed to query statistics from mediaServer");
    			}
    		}

    	}    
    }
    
    public void startConfidencePreview(int sessionId){
    	if (Boolean.parseBoolean(streamCtl.hdmiOutput.getSyncStatus()))
    	{
	    	streamCtl.updateWindow(sessionId);
	    	streamCtl.showPreviewWindow(sessionId);
	    	streamCtl.cam_preview.setSessionIndex(sessionId);
	    	streamCtl.cam_preview.startPlayback(true);
	    	
	    	confidencePreviewRunning = true;
    	}
    }
    
    public void stopConfidencePreview(int sessionId){
    	streamCtl.hidePreviewWindow(sessionId);
    	streamCtl.cam_preview.setSessionIndex(sessionId);
        streamCtl.cam_preview.stopPlayback(true);
        
        confidencePreviewRunning = false;
    }
    
    public boolean getConfidencePreviewStatus()
    {
    	return confidencePreviewRunning;
    }
    
    private class PreviewCB implements PreviewCallback
    {
		@Override
		public void onPreviewFrame(byte[] data, Camera camera) {
			int currentSessionInitiation = streamCtl.userSettings.getSessionInitiation(idx);
                        if ((currentSessionInitiation == 0) || (currentSessionInitiation == 2)) {	
                            streamCtl.SendStreamState(StreamState.STREAMERREADY, idx);
                            monitorRtspClientActiveConnections();
                        }
		    else {
                streamCtl.SendStreamState(StreamState.STARTED, idx);     
		    }
			startStatisticsTask();

			//Set data to null so that it can be GC a little bit faster, it has a large allocation (size of frame)
			data = null;
			// After first frame arrives unregister callback to prevent sending multiple streamstates
			camera.setPreviewCallback(null);
		}    	
    }
    
    private class ErrorCB implements ErrorCallback
    {
    	@Override
        public void onError(int error, Camera camera) {
            Log.d(TAG, "Camera Error callback: " + error + "Camera :" + camera);
        }
    }
    
    private class ErrorListner implements OnErrorListener
    {
		@Override
		public void onError(MediaRecorder mr, int what, int extra) {
			// TODO Auto-generated method stub
			Log.d(TAG, "MediaRecorder Error callback: " + what + ", " + extra + " MediaRecorder :" + mr);
		}
    }
}
