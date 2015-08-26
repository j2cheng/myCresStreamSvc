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

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.hardware.Camera.ErrorCallback;
import android.media.MediaRecorder;
import android.os.FileObserver;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

public class CameraStreaming implements ErrorCallback {
    private SurfaceHolder surfaceHolder;
    private FileObserver activeClientObserver;
    static Camera mCameraPreviewObj = null;
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
    	final Surface surface = streamCtl.getCresSurfaceHolder(idx).getSurface();
    	final CountDownLatch latch = new CountDownLatch(1);
    	new Thread(new Runnable() {
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
		        
		        streamCtl.setSystemVolume(streamCtl.userSettings.getVolume());
		        
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
		        	latch.countDown();
		        	return;
		        }
		
		        mrec = new MediaRecorder();
		        mCameraPreviewObj = CresCamera.getCamera();
		        if(mCameraPreviewObj!=null){
		            mCameraPreviewObj.getHdmiInputStatus();
		            mCameraPreviewObj.setEncoderFps(streamCtl.userSettings.getEncodingFramerate(idx));
		            mCameraPreviewObj.unlock();
		            mrec.setCamera(mCameraPreviewObj);
		
		            mrec.setAudioSource(MediaRecorder.AudioSource.MIC);
		            mrec.setVideoSource(MediaRecorder.VideoSource.CAMERA);
		            mrec.setStreamTransportMode(getStreamTransportMode());
		            
		            //Set Port
		            int l_port;
		            int currentSessionInitiation = streamCtl.userSettings.getSessionInitiation(idx);
		            int currentTransportMode = streamCtl.userSettings.getTransportMode(idx);
		            if ((currentSessionInitiation == 0) || (currentSessionInitiation == 2)) //Multicast via RTSP or By Receiver
		            {
		            	l_port = streamCtl.userSettings.getRtspPort(idx);
		            	streamIp = streamCtl.userSettings.getDeviceIp();
		                mrec.setRTSPPort(l_port);
		                
		                if (currentSessionInitiation == 2) //Multicast via RTSP
		                	mrec.setMcastIP(streamCtl.userSettings.getMulticastAddress(idx));
		            }
		            else //Multicast via UDP or By Transmitter
		            {
		            	if (currentSessionInitiation == 1)	//By Transmitter
		            		streamIp = streamCtl.userSettings.getServerUrl(idx);
		            	else if (currentSessionInitiation == 3) //Multicast via UDP
		            		streamIp = streamCtl.userSettings.getMulticastAddress(idx);
		            	
		            	if (currentTransportMode == 0)	//RTP
		            	{
		                    l_port = streamCtl.userSettings.getRtpAudioPort(idx);
		                    mrec.setRTPAudioPort(l_port);
		                    l_port = streamCtl.userSettings.getRtpVideoPort(idx);
		                    mrec.setRTPVideoPort(l_port);
		            	}
		            	else
		            	{
		                     l_port = streamCtl.userSettings.getTsPort(idx);
		                     mrec.setMPEG2TSPort(l_port);
		            	}
		            }
		            
		            mrec.setDestinationIP(streamIp);
		            
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
		            //mrec.setVideoFrameRate(streamCtl.userSettings.getEncodingFramerate(idx));//Mistral Propietary API 
		            mrec.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
		            mrec.setEncoderProfile(streamCtl.userSettings.getStreamProfile(idx).getVEncProfile());
		            mrec.setVideoEncoderLevel(streamCtl.userSettings.getEncodingLevel(idx));
		            mrec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
		            mrec.setOutputFile(path + filename);   
		
		            mrec.setPreviewDisplay(surface);	//TODO: put back in when preview audio works
		            try
		            {
			            mrec.prepare();
			            mrec.start();
		            }
		            catch (Exception ex) 
		            {
		            	ex.printStackTrace();
		            }
		
		            if ((currentSessionInitiation != 0) && (currentSessionInitiation != 2) && (currentTransportMode == 0)) {	//TODO: causes crash in RTSP modes currently being worked around
		                String sb = mrec.getSDP();
		                Log.d(TAG, "########SDP Dump######\n" + sb);
		            }
		            //mrec.getStatisticsData();
		            if ((currentSessionInitiation == 0) || (currentSessionInitiation == 2)) {	
		                streamCtl.SendStreamState(StreamState.STREAMER_READY, idx);
				monitorRtspClientActiveConnections();
							//RTSP Modified to Streamer Ready State, until client connects 
			    }
			    else {
		                streamCtl.SendStreamState(StreamState.STARTED, idx);     
			    }
		            out_stream_status = true;
		            
		            startStatisticsTask();	            
		        }
		        else {
		        	stopRecording(false);
		            Log.e(TAG, "Camera Resource busy or not available !!!!");
		            file4Recording.delete();
		            mrec = null;
		        }
		        
		        latch.countDown();
    		}
    	}).start();
    	
    	// We launch the start command in its own thread and timeout in case mediaserver gets hung
    	boolean successfulStart = true; //indicates that there was no time out condition
    	try { successfulStart = latch.await(startTimeout_ms, TimeUnit.MILLISECONDS); }
    	catch (InterruptedException ex) { ex.printStackTrace(); }
    	
    	if (!successfulStart)
    	{
		Log.e(TAG, String.format("MediaServer failed to start after %d ms", startTimeout_ms));
    		streamCtl.RecoverDucati();
    	}
    }
    
    void monitorRtspClientActiveConnections() 
    {
    	File activeClientConnection = new File ("/dev/shm/crestron/CresStreamSvc/clientConnected");
        if (!activeClientConnection.isFile())	//check if file exist
        {
        	try {
            	activeClientConnection.getParentFile().mkdirs();
            	activeClientConnection.createNewFile();
        	} catch (Exception e) {}
        }
        activeClientObserver = new FileObserver("/dev/shm/crestron/CresStreamSvc/clientConnected", FileObserver.CLOSE_WRITE) {						
			@Override
			public void onEvent(int event, String path) {
				int val = 0;
				//function start
				BufferedReader br = null;
				try {
					String sCurrentLine;
					br = new BufferedReader(new FileReader("/dev/shm/crestron/CresStreamSvc/clientConnected"));
					while ((sCurrentLine = br.readLine()) != null) {
						val = Integer.parseInt(sCurrentLine);
					}

				} catch (IOException e) {
					e.printStackTrace();
				} finally {
					try {
						if (br != null)br.close();
					} catch (IOException ex) {
						ex.printStackTrace();
					}
				}
				if(val > 0)
				    streamCtl.SendStreamState(StreamState.STARTED, idx);     
				else
				    streamCtl.SendStreamState(StreamState.STREAMER_READY, idx);     
				//function end
			}
	};
	activeClientObserver.startWatching();
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
        		streamIp = streamCtl.userSettings.getServerUrl(idx);
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
				break;
				
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
        
        if (currentSessionInitiation == 0)
        	return 0;	//RTSP unicast mode (By Receiver)
        else if (currentSessionInitiation == 2)
        	return 5;	//RTSP multicast mode (Multicast via RTSP)
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

    private void releaseMediaRecorder() {
        if (mrec != null) {
            mrec.reset(); // clear recorder configuration
            mrec.release(); // release the recorder object
            mrec = null;
        }
    }

    public void stopRecording(final boolean hpdEventAction) {
    	final CountDownLatch latch = new CountDownLatch(1);
    	new Thread(new Runnable() {
    		public void run() {
    			try {
			        Log.d(TAG, "stopRecording");
		                int currSessionInitiation = streamCtl.userSettings.getSessionInitiation(idx);
        			if ((currSessionInitiation == 0) || (currSessionInitiation == 2))
        			    activeClientObserver.stopWatching();

			        if (mrec != null) {
			            if(hpdEventAction==true){
			            	if (mCameraPreviewObj != null)
			            	{
			            		mCameraPreviewObj.lock(); //Android recommends this
			            		CresCamera.releaseCamera(mCameraPreviewObj);
			            	}
			                mCameraPreviewObj = null;
			            }
			            mrec.stop();
			            //mrec.setPreviewDisplay(null);
			            out_stream_status = false;
			            streamCtl.setPauseVideoImage(false);
			            is_pause = false;
			            releaseMediaRecorder();
			            
			            
			            if(hpdEventAction==false){
			            	if (mCameraPreviewObj != null)
			            	{
			            		mCameraPreviewObj.lock(); //Android recommends this
			            		CresCamera.releaseCamera(mCameraPreviewObj);
			            	}
			                mCameraPreviewObj = null;
			            }
			
			            stopStatisticsTask();
					            
			            streamCtl.SendStreamState(StreamState.STOPPED, idx);
			            
			            // Zero out statistics on stop
			            streamOutAudioFormat = streamOutAudioChannels = streamOutWidth = streamOutHeight = streamOutFps = 0;
						streamCtl.SendStreamOutFeedbacks();
					}
    			} catch (Exception e) { e.printStackTrace(); }
		        
		        latch.countDown();
    		}
    	}).start();

    	// We launch the stop commands in its own thread and timeout in case mediaserver gets hung
    	boolean successfulStop = true; //indicates that there was no time out condition
    	try { successfulStop = latch.await(stopTimeout_ms, TimeUnit.MILLISECONDS); }
    	catch (InterruptedException ex) { ex.printStackTrace(); }
    	
    	if (!successfulStop)
    	{
    		Log.e(TAG, String.format("MediaServer failed to stop after %d ms", stopTimeout_ms));
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
    

    @Override
        public void onError(int error, Camera camera) {
            Log.d(TAG, "Camera Error callback:" + error + "Camera :" + camera);
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
    		
    		try
            {
    			Pattern regexP;
    			Matcher regexM;
    			
    			String statisticsString = mrec.getStatisticsData();
				
				// Pull out Audio number of channels
				regexP = Pattern.compile("Audio\\s+Channel:\\s+(\\d+)");
				regexM = regexP.matcher(statisticsString);
				regexM.find();
				if (regexM.matches())
					streamOutAudioChannels = Integer.parseInt(regexM.group(1));
				
				// Pull out Video width and height
				regexP = Pattern.compile("Resolution:\\s+(\\d+)\\s+x\\s+(\\d+)");	//width x height
				regexM = regexP.matcher(statisticsString);
				regexM.find();				
				if (regexM.matches())
				{
					streamOutWidth = Integer.parseInt(regexM.group(1));
					streamOutHeight = Integer.parseInt(regexM.group(2));
				}
				
				// Pull out Video frames per second
				regexP = Pattern.compile("Frame\\s+Rate:\\s+(\\d+)");
				regexM = regexP.matcher(statisticsString);
				regexM.find();
				if (regexM.matches())
					streamOutFps = Integer.parseInt(regexM.group(1));
				
				streamOutAudioFormat = 1; //TODO: currently we are always setting this to PCM (1)
				
				streamCtl.SendStreamOutFeedbacks();
    			
    			while(!shouldExit)
				{    		
    				Thread.sleep(statisticsThreadPollTime);
    				
    				if (streamCtl.userSettings.isStatisticsEnable(idx))
    				{
    					if (mrec == null)
	    					continue;
	    				
						statisticsString = mrec.getStatisticsData();
						
						// Pull out number of Video packets
						regexP = Pattern.compile("Video\\s+Encoded Frames:\\s+(\\d+)");
						regexM = regexP.matcher(statisticsString);
						regexM.find();
						if (regexM.matches())
							statisticsNumVideoPackets = Long.valueOf(regexM.group(1));
						
						// Pull out number of Audio packets
						regexP = Pattern.compile("Audio\\s+Encoded Frames:\\s+(\\d+)");
						regexM = regexP.matcher(statisticsString);
						regexM.find();
						if (regexM.matches())
							statisticsNumAudioPackets = Long.valueOf(regexM.group(1));
						
						streamCtl.SendStreamOutFeedbacks();
    				}
				}
            }
            catch (InterruptedException localInterruptedException)
            {
                localInterruptedException.printStackTrace();
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
}
