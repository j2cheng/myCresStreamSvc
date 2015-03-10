package com.crestron.txrxservice;

import java.io.IOException;
import java.util.List;

import android.hardware.Camera;
import android.hardware.Camera.ErrorCallback;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.util.Log;
import android.view.SurfaceHolder;
import android.graphics.ImageFormat;
import com.crestron.txrxservice.CameraStreaming;

public class CameraPreview {
	String TAG = "TxRx Preview";
	String hdmiinput = "";
	AudioPlayback audio_pb; ;
	CresCamera cresCam;
	private Camera mCamera = null;
	private SurfaceHolder surfaceHolder;
	boolean is_pause = false;
	boolean is_preview = false;
	boolean is_audioplaying = false;
	List<Camera.Size> mSupportedPreviewSizes;

	public CameraPreview(SurfaceHolder vHolder) {
		audio_pb = new AudioPlayback();
        surfaceHolder = vHolder;
	}

	public void MyCameraInstance (){
       /* try {
            mCamera = Camera.open(0);
            if(mCamera!=null)
                hdmiinput = mCamera.getHdmiInputStatus();
        } catch (Exception e) {
            Log.e(TAG, "fail to open camera");
            e.printStackTrace();
            mCamera = null; 
        }*/
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
		Log.d(TAG, "starting Playback"+ is_preview);
		if(is_preview == false){
			Log.d(TAG, "Actual startPlayback");
            if (paramBoolean == true){
                mCamera = cresCam.getCamera();
                  // MNT - 3.10.15 
                  // getHdmiInputStatus causes a reset on the chip.  Calling this here causes
                  // the chip to get reset twice.  This will be fixed by Mistral.  However,
                  // until then, we will only call this on a resolution change or on startup.
//                if(mCamera!=null)
//                    hdmiinput = mCamera.getHdmiInputStatus();
            }
            try {
                //mCamera.setPreviewDisplay(CameraStreaming.surfaceHolder);
                mCamera.setPreviewDisplay(surfaceHolder);
            }catch (Exception localException) {
                localException.printStackTrace();
            }

			Log.d(TAG, "########## Resolutin Info: "+hdmiinput);
			Camera.Parameters localParameters = mCamera.getParameters();
			mSupportedPreviewSizes = mCamera.getParameters().getSupportedPreviewSizes();
			for (int i = 0; i < mSupportedPreviewSizes.size(); i++) {
				Log.d(TAG, i + ". Supported Resolution = " + mSupportedPreviewSizes.get(i).width + "x" + mSupportedPreviewSizes.get(i).height);
			}
			if((CresStreamConfigure.getWidth()!= 0) && (CresStreamConfigure.getHeight() !=0))	//Set to default if there is no width or height
				localParameters.setPreviewSize(CresStreamConfigure.getWidth(), CresStreamConfigure.getHeight());
			//localParameters.set("mode", "high-quality");
			localParameters.set("ipp", "off");
			Log.d(TAG, "Preview Size set to " + localParameters.getPreviewSize().width + "x" + localParameters.getPreviewSize().height);
			//Log.d(TAG, "Scene mode" + localParameters.getSceneMode());
			//Log.d(TAG, "Mode set to " + localParameters.get("mode"));
			//Log.d(TAG, "Picture formate %s " + localParameters.getPictureFormat());
			mCamera.setDisplayOrientation(0);
			mCamera.setParameters(localParameters);
			mCamera.startPreview();
			startAudio();
			is_preview = true;
			is_audioplaying = true;
		}
	}

	public void stopPlayback()
	{
		Log.d(TAG, "stopPlayback");
	    stopAudio();
		try
		{
			if (mCamera!= null)
			{
				mCamera.stopPreview();
				cresCam.releaseCamera(mCamera);
			}
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
/*
	private void releaseCamera() {
		if (mCamera != null) {
			mCamera.release(); // release the camera for other applications
			mCamera = null;
		}
	}
*/	
	public String getHdmiInputResolution() {
		if(mCamera != null) {
			return mCamera.getHdmiInputStatus();
		}
		else {
			return null;
		}
	}
}
