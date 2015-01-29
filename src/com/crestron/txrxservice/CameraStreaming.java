package com.crestron.txrxservice;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.hardware.Camera.ErrorCallback;
import android.media.MediaRecorder;
import android.os.Environment;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;

public class CameraStreaming implements Callback, ErrorCallback {
	static SurfaceHolder surfaceHolder;
	public static CameraPreview mCameraObj = null;
	MediaRecorder mrec;
	String TAG = "TxRx CameraStreamer";
	String hostaddr;
	String filename;
	String path;
	boolean out_stream_status = false;
	
	public CameraStreaming(Context mContext, SurfaceView view) {
		MiscUtils.getDeviceIpAddr();	
		hostaddr = MiscUtils.matcher.group();
		mCameraObj =  new CameraPreview();
		Log.d(TAG, "CameraStreaming :: Constructor called.....");
		if (view != null) {
			Log.d(TAG, "View is not null");
			surfaceHolder = view.getHolder();	
			surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
			surfaceHolder.addCallback(this);
			//view.setZOrderOnTop(true);
		} else {
			Log.d(TAG, "App passed null surface view for streaming");
		}
	}

	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {	
		Log.d(TAG, "########surfaceChanged#########");
		//surfaceHolder = holder;
		//try {
		//	mCamera.setPreviewDisplay(surfaceHolder);
		//} catch (IOException e) {
		//	e.printStackTrace();
		//}
	}
	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		Log.d(TAG, "########surfaceCreated#########");
		//mCameraObj.startPlayback(true);
		//surfaceHolder = holder;
		//try {
		//	mCamera.setPreviewDisplay(holder);
		//	mCamera.setPreviewCallback(null);
		//	startPlayback();
		//} catch (IOException exception) {
		//	mCamera.release();
		//	mCamera = null;
		//}
	}
	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		Log.d(TAG, "########surfaceDestroyed#########");
		if(mCameraObj.APlayStatus())
			mCameraObj.stopAudio();
		stopRecording();
		mCameraObj.stopPlayback();
		//mCameraObj = null;
		//surfaceHolder = null;
		//mCamera.stopPreview();
		//mCamera.release();

	}

	protected void startRecording() throws IOException {

		if(mCameraObj.APlayStatus())
			mCameraObj.stopAudio();
		if(out_stream_status==true)
			stopRecording();
		Log.d(TAG, "startRecording");

		path = "/sdcard/ROMDISK/";
		Log.d(TAG, "CamTest: Camera Recording Path: " + path);

		Date date = new Date();
		filename = "/rec" + date.toString().replace(" ", "_").replace(":", "_")
			+ ".mp4";
		Log.d(TAG, "CamTest: Camera Recording Filename: " + filename);

		// create empty file it must use
		File file = new File(path, filename);
		if (file == null)
		{
			Log.d(TAG, "CamTest: file() returned null");
		}
		else
		{
			Log.d(TAG, "CamTest: file() didn't return null");
		}


		mrec = new MediaRecorder();
		if(mCameraObj.mCamera==null)
			mCameraObj.MyCameraInstance();
		
		mCameraObj.mCamera.setEncoderFps(CresStreamConfigure.getVFrameRate());
		mCameraObj.mCamera.lock();
		mCameraObj.mCamera.unlock();

		mrec.setCamera(mCameraObj.mCamera);
		mrec.setAudioSource(MediaRecorder.AudioSource.MIC);
		mrec.setVideoSource(MediaRecorder.VideoSource.CAMERA);
		mrec.setStreamTransportMode(CresStreamConfigure.mode.getMode());
		//Set Port
		int l_port;
		switch(CresStreamConfigure.mode.getMode())
                {
                    case 0://RTSP
                        {
                            l_port = CresStreamConfigure.getRTSPPort();
                            mrec.setDestinationIP(hostaddr);
                            mrec.setRTSPPort(l_port);
                        }
                        break; 
                    case 1://RTP
                        mrec.setDestinationIP(CresStreamConfigure.getIP());
                        l_port = CresStreamConfigure.getRTPVPort();
                        mrec.setRTPAudioPort(l_port);
                        l_port = CresStreamConfigure.getRTPAPort();
                        mrec.setRTPVideoPort(l_port);
                        break;
                    case 2://TS_RTP
                    case 3://TS_UDP
                        mrec.setDestinationIP(CresStreamConfigure.getIP());
                        l_port = CresStreamConfigure.getTSPort();
                        mrec.setMPEG2TSPort(l_port);
                        break;
                    case 4://MJPEG
                        break;
                    case 5://MCast RTSP
                        {
                            l_port = CresStreamConfigure.getRTSPPort();
                            mrec.setMcastIP(CresStreamConfigure.getIP());
                            mrec.setRTSPPort(l_port);
                        }
                        break;
                    default:
                        break; 
                }
		mrec.setOutputFormat(9);//Streamout option set to Stagefright Recorder
		Log.d(TAG, "ip addr "+CresStreamConfigure.getIP());
		Log.d(TAG, "setting width: "+CresStreamConfigure.getWidth() );
		Log.d(TAG, "setting height: "+CresStreamConfigure.getHeight());
		Log.d(TAG, "setting profile: "+CresStreamConfigure.vprofile.getVEncProfile());
		Log.d(TAG, "setting video encoder level: "+CresStreamConfigure.getVEncLevel());
		Log.d(TAG, "setting video frame rate: "+CresStreamConfigure.getVFrameRate());
		if((CresStreamConfigure.getWidth()!=0) || (CresStreamConfigure.getHeight()!=0))
			mrec.setVideoSize(CresStreamConfigure.getWidth(),CresStreamConfigure.getHeight());
		mrec.setVideoEncodingBitRate(CresStreamConfigure.getVideoBitRate());
		//mrec.setVideoFrameRate(CresStreamConfigure.getVFrameRate());//Mistral Propietary API 
		mrec.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
		mrec.setEncoderProfile(CresStreamConfigure.vprofile.getVEncProfile());
		mrec.setVideoEncoderLevel(CresStreamConfigure.getVEncLevel());
		mrec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
		mrec.setOutputFile(path + filename);   

		Log.d(TAG, "########setPreviewDisplay######");
		mrec.setPreviewDisplay(surfaceHolder.getSurface());

		mrec.prepare();
		mrec.start();
		out_stream_status = true;
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
	
	public void stopRecording() {
		Log.d(TAG, "stopRecording");
		if (mrec != null) {
			mrec.stop();
			mrec.setPreviewDisplay(null);
			out_stream_status = false;
			releaseMediaRecorder();
                        mCameraObj.stopPlayback();
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

        public int getStreamOutHorizontalResFb(){
            return CresStreamConfigure.getWidth();
        }

        public int getStreamOutVerticalResFb(){
            return CresStreamConfigure.getHeight();
        }

        public int getStreamOutFpsFb(){
            return CresStreamConfigure.getVFrameRate();
        }

        public String getStreamOutAspectRatioFb(){
            String aspect = MiscUtils.calculateAspectRatio(CresStreamConfigure.getWidth(),CresStreamConfigure.getHeight());
            return aspect;
        }
        
        public int getStreamOutAudioFormatFb(){
            return 1;//Always
        }
        
        public int getStreamOutAudiochannelsFb(){
            return 2;//For Now only 2 Audio Channels
        }
}
