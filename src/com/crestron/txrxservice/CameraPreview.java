package com.crestron.txrxservice;

import java.io.IOException;
import java.util.List;

import com.crestron.txrxservice.CresStreamCtrl.StreamState;

import android.hardware.Camera;
import android.hardware.Camera.ErrorCallback;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.util.Log;
import android.view.SurfaceHolder;

public class CameraPreview {
    String TAG = "TxRx Preview";
    AudioPlayback audio_pb;
    HDMIInputInterface hdmiIf;
    CresCamera cresCam;
    private Camera mCamera = null;
    private SurfaceHolder surfaceHolder;
    boolean is_pause = false;
    boolean is_preview = false;
    boolean is_audioplaying = false;
    private boolean skipAudio = false;
    //List<Camera.Size> mSupportedPreviewSizes;
    CresStreamCtrl streamCtl;
    private int idx = 0;

    //public CameraPreview(CresStreamCtrl ctl, SurfaceHolder vHolder, HDMIInputInterface hdmiInIface) {
    public CameraPreview(CresStreamCtrl ctl, HDMIInputInterface hdmiInIface) {
        audio_pb = new AudioPlayback(ctl);
        //surfaceHolder = vHolder;
        hdmiIf = hdmiInIface;
        streamCtl = ctl;
    }
    
    public void setSessionIndex(int id){
        idx = id;
    }
    
    public void restartCamera(boolean confidenceMode)
    {
    	try {
    		boolean pauseStatus = is_pause;
	    	skipAudio = true;
	    	stopPlayback(confidenceMode);
	    	is_pause = pauseStatus;
	    	startPlayback(confidenceMode);
	    	skipAudio = false;
    	} catch (Exception e) { e.printStackTrace(); }        
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
                streamCtl.setPauseVideoImage(true);
                stopAudio();
                streamCtl.SendStreamState(StreamState.PAUSED, idx);
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
                streamCtl.setPauseVideoImage(false);
                startAudio();
            }
            streamCtl.SendStreamState(StreamState.STARTED, idx);
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

    public void startPlayback(boolean confidenceMode){
        Log.d(TAG, "starting Playback"+ is_preview);
        if(is_preview == false){
            Log.d(TAG, "Actual startPlayback");
            if (mCamera != null)
            {
            	cresCam.releaseCamera(mCamera);
            	mCamera = null;
            }
        	mCamera = cresCam.getCamera();
            // MNT - 3.10.15 
            // getHdmiInputStatus causes a reset on the chip.  Calling this here causes
            // the chip to get reset twice.  This will be fixed by Mistral.  However,
            // until then, we will only call this on a resolution change or on startup.
            //                if(mCamera!=null)
            //                    hdmiinput = mCamera.getHdmiInputStatus();
            if(mCamera!=null){
                try {
                    mCamera.setPreviewDisplay(streamCtl.getCresSurfaceHolder(idx));
                    //mCamera.setPreviewDisplay(surfaceHolder);
                }catch (Exception localException) {
                    localException.printStackTrace();
                }

                Camera.Parameters localParameters = mCamera.getParameters();
                /*mSupportedPreviewSizes = mCamera.getParameters().getSupportedPreviewSizes();
                  for (int i = 0; i < mSupportedPreviewSizes.size(); i++) {
                  Log.d(TAG, i + ". Supported Resolution = " + mSupportedPreviewSizes.get(i).width + "x" + mSupportedPreviewSizes.get(i).height);
                  }*/
                if(CresStreamCtrl.hpdHdmiEvent==1){
                    String resInfo = HDMIInputInterface.getHdmiInResolutionSysFs();//Reading From SysFs
                    Log.i(TAG, "HDMI In Resolution API" + resInfo);
                    hdmiIf.updateResolutionInfo(resInfo);
                    CresStreamCtrl.hpdHdmiEvent=0;
                }
                if((Integer.parseInt(hdmiIf.getHorizontalRes())==0) && (Integer.parseInt(hdmiIf.getVerticalRes())==0)){
                    //localParameters.setPreviewSize(640, 480);//if no hdmi cable is connected
                    //mCamera.setDisplayOrientation(0);
                    //mCamera.setParameters(localParameters);
                    //mCamera.startPreview();
                }
                else{
                    localParameters.setPreviewSize(Integer.parseInt(hdmiIf.getHorizontalRes()), Integer.parseInt(hdmiIf.getVerticalRes()));

                    localParameters.set("ipp", "off");
                    Log.d(TAG, "Preview Size set to " + localParameters.getPreviewSize().width + "x" + localParameters.getPreviewSize().height);
                    mCamera.setDisplayOrientation(0);
                    try {
                        mCamera.setParameters(localParameters);
                    }catch (Exception localException) {
                        localException.printStackTrace();
                        localParameters.setPreviewSize(640, 480);
                        mCamera.setParameters(localParameters);
                    }
                    mCamera.startPreview();

                	startAudio();                    
                    if (confidenceMode)
                    	streamCtl.SendStreamState(StreamState.CONFIDENCEMODE, idx);
                    else
                    	streamCtl.SendStreamState(StreamState.STARTED, idx);
                }
                is_preview = true;
            }
            else
            {
            	stopPlayback(false);
            }
        }else   //Pause/Resume Case
            resumePlayback();      

    }

    public void stopPlayback(boolean confidenceMode)
    {
    	// TODO: ioctl crash when input hdmi is plugged in but no video passing
        Log.d(TAG, "stopPlayback");
        if(is_preview)
        {
            try
            {
                if (mCamera!= null)
                {
            		mCamera.stopPreview();            		
                	cresCam.releaseCamera(mCamera);
                	mCamera = null;
                }
                is_preview = false;
                streamCtl.setPauseVideoImage(false);
                is_pause = false;
                Log.d(TAG, "Playback stopped !");
                
                if (!confidenceMode)
                	streamCtl.SendStreamState(StreamState.STOPPED, idx);
                
            	stopAudio();
            }
            catch (Exception localException)
            {
                localException.printStackTrace();
            }
            
            is_preview = false;
        }
        else
            Log.d(TAG, "Playback already stopped");
    }

    protected void startAudio(){
    	if (skipAudio == false)
    	{
	        if(!is_audioplaying)
	            audio_pb.startAudioTask();
	        is_audioplaying = true;
    	}
    }

    public void stopAudio() {
    	if (skipAudio == false)
    	{
	        Log.d(TAG, "stoppingAudio");
	        if(is_audioplaying){
	            audio_pb.stopAudioTask();
	            is_audioplaying = false;
	        }
    	}
    }

    public String getHdmiInputResolution() {    	
        if(mCamera != null) {
        	return mCamera.getHdmiInputStatus();
        }
        else {
        	mCamera = cresCam.getCamera();
        	if (mCamera != null) {
        		String ret = mCamera.getHdmiInputStatus();
        		cresCam.releaseCamera(mCamera);
            	mCamera = null;
            	return ret;
        	} else
        		return null;
        }
    }
    
    public void setVolume(int volume) {
    	audio_pb.setVolume(volume);
    }
}
