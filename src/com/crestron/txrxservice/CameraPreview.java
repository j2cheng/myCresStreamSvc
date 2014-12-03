package com.crestron.txrxservice;

import java.io.IOException;
import java.util.List;

import android.hardware.Camera;
import android.hardware.Camera.ErrorCallback;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.util.Log;
import android.content.Context;
import android.view.SurfaceHolder;
import android.graphics.ImageFormat;
import com.crestron.txrxservice.CameraStreaming;

public class CameraPreview implements Camera.PreviewCallback {
	String TAG = "TxRx Preview";
	AudioPlayback audio_pb; ;
	public Camera mCamera = null;
	private Camera.PreviewCallback mPreviewcallback;
	int hdmiinput;
	private int resId = 7;
	SurfaceHolder surfaceHolder;
	boolean is_pause = false;
	boolean is_preview = false;
	boolean is_audioplaying = false;
	List<Camera.Size> mSupportedPreviewSizes;

	public CameraPreview() {
		audio_pb = new AudioPlayback();
		mPreviewcallback = this;
	}

	public void MyCameraInstance (){
		mCamera = Camera.open(0);
		if(mCamera!=null)
			hdmiinput = mCamera.getHdmiInputStatus();
	}

	public void onPreviewFrame(byte[] paramArrayOfByte, Camera paramCamera) {}

	public boolean pausePlayback()
	{
		Log.d(TAG, "pausePlayback");
		try
		{
			if (mCamera != null)
			{
				is_pause = true;
				mCamera.stopPreview();	
				stopAudio();
			}
			return true;
		}
		catch (Exception localException)
		{
			localException.printStackTrace();
		}
		return false;
	}

	public boolean resumePlayback()
	{
		Log.d(TAG, "resumePlayback");
		try
		{
			if (mCamera != null)
			{
				is_pause = false;
				mCamera.startPreview();
				startAudio();
			}
			return true;
		}
		catch (Exception localException)
		{
			localException.printStackTrace();
		}
		return false;
	}

	public boolean IsPreviewStatus(){
		return is_preview;
	}

	public boolean APlayStatus (){
		return is_audioplaying;
	}

	public boolean IsPauseStatus(){
		return is_pause;
	}

	public void startPlayback(boolean paramBoolean){
		int currentFrameHeight = 0;
		int currentFrameWidth = 0;
		Log.d(TAG, "starting Playback"+ is_preview);
		if(is_preview == false){
			Log.d(TAG, "Actual startPlayback");
			if (paramBoolean == true){
				MyCameraInstance();
			}
			try {
				mCamera.setPreviewDisplay(CameraStreaming.surfaceHolder);
			}catch (Exception localException) {
				localException.printStackTrace();
			}
			resId = hdmiinput;

			Log.d(TAG, "#############Resolutin ID: "+resId);
			Camera.Parameters localParameters = mCamera.getParameters();
			mSupportedPreviewSizes = mCamera.getParameters().getSupportedPreviewSizes();
			for (int i = 0; i < mSupportedPreviewSizes.size(); i++) {
				Log.d(TAG, i + ". Supported Resolution = " + mSupportedPreviewSizes.get(i).width + "x" + mSupportedPreviewSizes.get(i).height);
			}
			//currentFrameWidth = (mSupportedPreviewSizes.get(resId)).width;//localSize.width;
			//currentFrameHeight = (mSupportedPreviewSizes.get(resId)).height;//localSize.height;
			//Log.d(TAG, "Preview size set to Width:" + currentFrameWidth + "& Height:" + currentFrameHeight);
			//localParameters.setPreviewSize(width, height);
			//localParameters.set("mode", "high-quality");
			localParameters.set("ipp", "off");
			Log.d(TAG, "Preview Size set to " + localParameters.getPreviewSize().width + "x" + localParameters.getPreviewSize().height);
			//Log.d(TAG, "Scene mode" + localParameters.getSceneMode());
			//Log.d(TAG, "Mode set to " + localParameters.get("mode"));
			//Log.d(TAG, "Picture formate %s " + localParameters.getPictureFormat());
			mCamera.setDisplayOrientation(0);
			mCamera.setParameters(localParameters);
			mCamera.setPreviewCallback(mPreviewcallback);
			mCamera.startPreview();
			startAudio();
			is_preview = true;
			is_audioplaying = true;
		}
	}

	public void stopPlayback()
	{
		Log.d(TAG, "stopPlayback");
		try
		{
			if (mCamera!= null)
			{
				mCamera.setPreviewCallback(null);
				mCamera.stopPreview();
				releaseCamera();
			}
			stopAudio();
			is_preview = false;
			Log.d(TAG, "Playback stopped !");
			return;
		}
		catch (Exception localException)
		{
			localException.printStackTrace();
		}
		is_preview = false;
	}

	protected void startAudio(){
		if(!is_audioplaying)
			audio_pb.startAudioTask();
		is_audioplaying = true;
	}

	public void stopAudio() {
		Log.d(TAG, "stoppingAudio");
		if(is_audioplaying){
			audio_pb.stopAudioTask();
			is_audioplaying = false;
		}
	}

	private void releaseCamera() {
		if (mCamera != null) {
			mCamera.release(); // release the camera for other applications
			mCamera = null;
		}
	}
}
