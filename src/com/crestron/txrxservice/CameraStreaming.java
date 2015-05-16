package com.crestron.txrxservice;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;

import com.crestron.txrxservice.CresStreamCtrl.StreamState;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.hardware.Camera.ErrorCallback;
import android.media.MediaRecorder;
import android.os.Environment;
import android.util.Log;
import android.view.SurfaceHolder;

public class CameraStreaming implements ErrorCallback {
    private SurfaceHolder surfaceHolder;
    static Camera mCameraPreviewObj = null;
    MediaRecorder mrec;
    String TAG = "TxRx CameraStreamer";
    String hostaddr;
    String filename;
    File file4Recording;
    boolean out_stream_status = false;
    CresStreamCtrl streamCtl;
    private int idx = 0;
    private int streamOutWidth = 0;
    private int streamOutHeight = 0;

    //public CameraStreaming(CresStreamCtrl mContext, SurfaceHolder lpHolder ) {
    public CameraStreaming(CresStreamCtrl mContext) {
        MiscUtils.getDeviceIpAddr();	
        hostaddr = MiscUtils.matcher.group();
        Log.d(TAG, "CameraStreaming :: Constructor called.....");
        //surfaceHolder = lpHolder;
        streamCtl = mContext;
    }
   
    public void setSessionIndex(int id){
        idx = id;
    }

    protected void startRecording() throws IOException {

        if(out_stream_status==true)
            stopRecording(false);
        Log.d(TAG, "startRecording");
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


        mrec = new MediaRecorder();
        mCameraPreviewObj = CresCamera.getCamera();
        if(mCameraPreviewObj!=null){
            mCameraPreviewObj.getHdmiInputStatus();
            mCameraPreviewObj.setEncoderFps(streamCtl.userSettings.getEncodingFramerate(idx));
            mCameraPreviewObj.lock();
            mCameraPreviewObj.unlock(); //TODO: what is the purpose of this????
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
                mrec.setDestinationIP( hostaddr);
                mrec.setRTSPPort(l_port);
                
                if (currentSessionInitiation == 2) //Multicast via RTSP
                	mrec.setMcastIP(streamCtl.userSettings.getMulticastAddress(idx));
            }
            else //Multicast via UDP or By Transmitter
            {
            	if (currentSessionInitiation == 1)	//By Transmitter
            		mrec.setDestinationIP(streamCtl.userSettings.getServerUrl(idx));
            	else if (currentSessionInitiation == 3) //Multicast via UDP
            		mrec.setDestinationIP(streamCtl.userSettings.getMulticastAddress(idx));
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
            
            mrec.setOutputFormat(9);//Streamout option set to Stagefright Recorder
            if ((currentSessionInitiation == 2) || (currentSessionInitiation == 3))
            	Log.d(TAG, "ip addr " + streamCtl.userSettings.getMulticastAddress(idx));
            else
            	Log.d(TAG, "ip addr " + streamCtl.userSettings.getDeviceIp());
            Log.d(TAG, "setting profile: " + streamCtl.userSettings.getStreamProfile(idx).getVEncProfile());
            Log.d(TAG, "setting video encoder level: " + streamCtl.userSettings.getEncodingLevel(idx));
            Log.d(TAG, "setting video frame rate: " + streamCtl.userSettings.getEncodingFramerate(idx));
            
            setWidthAndHeightFromEncRes(idx);
            mrec.setVideoEncodingBitRate(streamCtl.userSettings.getBitrate(idx));
            //mrec.setVideoFrameRate(streamCtl.userSettings.getEncodingFramerate(idx));//Mistral Propietary API 
            mrec.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mrec.setEncoderProfile(streamCtl.userSettings.getStreamProfile(idx).getVEncProfile());
            mrec.setVideoEncoderLevel(streamCtl.userSettings.getEncodingLevel(idx));
            mrec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mrec.setOutputFile(path + filename);   

            Log.d(TAG, "########setPreviewDisplay######");
            mrec.setPreviewDisplay(streamCtl.getCresSurfaceHolder(idx).getSurface());
            //mrec.setPreviewDisplay(surfaceHolder.getSurface());

            mrec.prepare();
            mrec.start();

            if ((currentSessionInitiation != 0) && (currentSessionInitiation != 2) && (currentTransportMode == 0)) {	//TODO: causes crash in RTSP modes currently being worked around
                String sb = mrec.getSDP();
                Log.d(TAG, "########SDP Dump######\n" + sb);
            }
            //mrec.getStatisticsData();
            streamCtl.SendStreamState(StreamState.STARTED, idx);
            streamCtl.SendStreamOutFeedbacks();
            out_stream_status = true;
        }
        else {
            Log.e(TAG, "Camera Resource busy or not available !!!!");
            file4Recording.delete();
            mrec = null;
        }
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

    public void stopRecording(boolean hpdEventAction) {
        Log.d(TAG, "stopRecording");
        if (out_stream_status && (mrec != null)) {
            if(hpdEventAction==true){
                CresCamera.releaseCamera(mCameraPreviewObj);
                mCameraPreviewObj = null;
            }
            mrec.stop();
            //mrec.setPreviewDisplay(null);
            out_stream_status = false;
            releaseMediaRecorder();
            if(hpdEventAction==false){
                CresCamera.releaseCamera(mCameraPreviewObj);
                mCameraPreviewObj = null;
            }
            streamCtl.SendStreamState(StreamState.STOPPED, idx);
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
    	return streamCtl.userSettings.getEncodingFramerate(idx);
    }

    public String getStreamOutAspectRatioFb(){
        return MiscUtils.calculateAspectRatio(streamCtl.userSettings.getW(idx),streamCtl.userSettings.getH(idx));
    }

    public int getStreamOutAudioFormatFb(){
        return 1;//Always
    }

    public int getStreamOutAudiochannelsFb(){
        return 2;//For Now only 2 Audio Channels
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
}
