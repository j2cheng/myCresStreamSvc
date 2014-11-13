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

import com.crestron.txrxservice.CresStreamConfigure;

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
			surfaceHolder.addCallback(this);
			surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
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
		mCameraObj.startPlayback(true);
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
		releaseMediaRecorder();
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

		path = "/sdcard/Movies";
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

		mCameraObj.mCamera.lock();
		mCameraObj.mCamera.unlock();

		mrec.setCamera(mCameraObj.mCamera);
		mrec.setAudioSource(MediaRecorder.AudioSource.MIC);
		mrec.setVideoSource(MediaRecorder.VideoSource.CAMERA);
		if(CresStreamConfigure.getIP()==null)
			mrec.setRemoteIPAndPort(hostaddr, CresStreamConfigure.getPort());
		else
			mrec.setRemoteIPAndPort(CresStreamConfigure.getIP(), CresStreamConfigure.getPort());
		mrec.setStreamTransportMode(CresStreamConfigure.mode.getMode());
		mrec.setOutputFormat(9);
		Log.d(TAG, "port is"+CresStreamConfigure.getPort() );
		Log.d(TAG, "ip addr"+CresStreamConfigure.getIP());
		Log.d(TAG, "setting width: "+CresStreamConfigure.getWidth() );
		Log.d(TAG, "setting height: "+CresStreamConfigure.getHeight());
		Log.d(TAG, "setting profile: "+CresStreamConfigure.vprofile.getVEncProfile());
		mrec.setVideoSize(CresStreamConfigure.getWidth(),CresStreamConfigure.getHeight());
		//mrec.setVideoSize(out_w,out_h);
		mrec.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
		mrec.setEncoderProfile(CresStreamConfigure.vprofile.getVEncProfile());
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
			out_stream_status = false;
		}
	}
	
	@Override
	public void onError(int error, Camera camera) {
		Log.d(TAG, "Camera Error callback:" + error + "Camera :" + camera);
	}
}
