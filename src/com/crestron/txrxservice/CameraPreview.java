package com.crestron.txrxservice;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.crestron.txrxservice.CresStreamCtrl.DeviceMode;
import com.crestron.txrxservice.CresStreamCtrl.StreamState;

import android.hardware.Camera;
import android.hardware.Camera.ErrorCallback;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.util.Log;
import android.view.SurfaceHolder;

public class CameraPreview {
    String TAG = "TxRx Preview";
    AudioPlayback audio_pb;
    HDMIInputInterface hdmiIf;
    private SurfaceHolder surfaceHolder;
    boolean is_pause = false;
    boolean is_preview = false;
    boolean is_audioplaying = false;
    private boolean skipAudio = false;
    private final long stopTimeout_ms = 15000;
    private final long startTimeout_ms = 15000;
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
    
    public int getSessionIndex(){
        return(idx);
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
        Log.i(TAG, "pausePlayback");
        try
        {
            if (is_preview)
            {
                is_pause = true;
                streamCtl.setPauseVideoImage(true, DeviceMode.PREVIEW);
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

    public boolean resumePlayback(boolean confidenceMode)
    {
        Log.i(TAG, "resumePlayback");
        try
        {
            if (is_preview)
            {
                is_pause = false;
                streamCtl.setPauseVideoImage(false, DeviceMode.PREVIEW);
                startAudio();
            }
            
            if (confidenceMode)
            	streamCtl.SendStreamState(StreamState.CONFIDENCEMODE, idx);
            else
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

    public void startPlayback(final boolean confidenceMode){
    	final CountDownLatch latch = new CountDownLatch(1);
    	Thread startThread = new Thread(new Runnable() {
    		public void run() {
		        Log.i(TAG, "starting Playback " + is_preview);
		        if(is_preview == false){		        	
		        	Log.i(TAG, "Actual startPlayback");

		        	CresCamera.openCamera(streamCtl);
		        	// This is here because moved out of openCamera
		        	if(hdmiIf != null)
		        	{
		        		ProductSpecific.getHdmiInputStatus();			
		        	}
		        	// MNT - 3.10.15 
		        	// getHdmiInputStatus causes a reset on the chip.  Calling this here causes
		        	// the chip to get reset twice.  This will be fixed by Mistral.  However,
		        	// until then, we will only call this on a resolution change or on startup.
		        	//                if(mCamera!=null)
		        	//                    hdmiinput = mCamera.getHdmiInputStatus();
		        	if(CresCamera.mCamera != null){
		        		try {
		        			try {
		        				SurfaceHolder sh = streamCtl.getCresSurfaceHolder(idx);
		        				CresCamera.mCamera.setPreviewDisplay(sh);

		        				//mCamera.setPreviewDisplay(surfaceHolder);
		        			}catch (Exception localException) {
		        				localException.printStackTrace();
		        			}

		        			Camera.Parameters localParameters = CresCamera.mCamera.getParameters();
		        			/*mSupportedPreviewSizes = mCamera.getParameters().getSupportedPreviewSizes();
		                  for (int i = 0; i < mSupportedPreviewSizes.size(); i++) {
		                  Log.i(TAG, i + ". Supported Resolution = " + mSupportedPreviewSizes.get(i).width + "x" + mSupportedPreviewSizes.get(i).height);
		                  }*/
		        			if(CresStreamCtrl.hpdHdmiEvent==1){
		        				ProductSpecific.handleHpdHdmiEvent(hdmiIf);
		        				CresStreamCtrl.hpdHdmiEvent=0;
		        			}

		        			// PEM - Previous check didn't look quite right here, was allowing zero horizontal or vertical to be considered ok.
		        			boolean validRes = false;
		        			if(hdmiIf != null)
		        			{
		        				int hres, vres;
		        				int resIndex = hdmiIf.getResolutionIndex();

		        				switch (streamCtl.hdmiInput.getResolutionIndex())
		        				{
		        				case 0:
		        				case 1:
		        					hres = 640;
		        					vres = 480;
		        					break;
		        				case 2:
		        				case 3:
		        					hres = 720;
		        					vres = 480;
		        					break;
		        				case 4:
		        				case 5:
		        					hres = 720;
		        					vres = 576;
		        					break;
		        				case 6:
		        					hres = 800;
		        					vres = 600;
		        					break;
		        				case 7:
		        					hres = 848;
		        					vres = 480;
		        					break;
		        				case 8:
		        					hres = 1024;
		        					vres = 768;
		        					break;
		        				case 9:
		        				case 10:
		        					hres = 1280;
		        					vres = 720;
		        					break;				
		        				case 11:
		        				case 12:
		        					hres = 1280;
		        					vres = 768;
		        					break;	
		        				case 13:
		        				case 14:
		        					hres = 1280;
		        					vres = 800;
		        					break;	
		        				case 15:
		        					hres = 1280;
		        					vres = 960;
		        					break;	
		        				case 16:
		        					hres = 1280;
		        					vres = 1024;
		        					break;	
		        				case 17:
		        					hres = 1360;
		        					vres = 768;
		        					break;
		        				case 18:
		        				case 19:
		        					hres = 1366;
		        					vres = 768;
		        					break;
		        				case 20:
		        				case 21:
		        					hres = 1400;
		        					vres = 1050;
		        					break;
		        				case 22:					
		        				case 23:
		        					hres = 1440;
		        					vres = 900;
		        					break;	
		        				case 24:
		        					hres = 1600;
		        					vres = 900;
		        					break;	
		        				case 25:
		        					hres = 1600;
		        					vres = 1200;
		        					break;	
		        				case 26:
		        				case 27:
		        					hres = 1680;
		        					vres = 1050;
		        					break;	
		        				case 28:
		        				case 29:
		        				case 30:
		        				case 31:
		        				case 32:
		        					hres = 1920;
		        					vres = 1080;
		        					break;	
		        				case 33:
		        					hres = 1920;
		        					vres = 1200;
		        					break;	
		        				default:
		        					hres = 640;
		        					vres = 480;
		        					break;
		        				}

		        				if((hres !=0) && ( vres !=0))
		        				{
		        					validRes = true;
		        					localParameters.setPreviewSize(hres, vres);
		        					localParameters.set("ipp", "off");
		        					CresCamera.mCamera.setDisplayOrientation(0);
		        					try {
		        						CresCamera.mCamera.setParameters(localParameters);
		        					}catch (Exception localException) {
		        						localException.printStackTrace();
		        						localParameters.setPreviewSize(640, 480);
		        						CresCamera.mCamera.setParameters(localParameters);
		        					}
		        				}		                
		        			}
		        			else // assume valid res for real camera, don't set preview size?
		        			{
		        				validRes = true;
		        			}
		        			if(validRes)
		        			{
		        				Log.i(TAG, "Camera preview size: " + localParameters.getPreviewSize().width + "x" + localParameters.getPreviewSize().height);
		        				CresCamera.mCamera.setPreviewCallback(new PreviewCB(confidenceMode));
		        				CresCamera.mCamera.setErrorCallback(new ErrorCB(confidenceMode));
		        				CresCamera.mCamera.startPreview();

		        				startAudio(); 
		        				//Streamstate is now being fedback using preview callback                   
		        			}
		        			is_preview = true;
		        		} catch (Exception e)
		        		{
		        			e.printStackTrace();
		        		}
		        	}
		        	else
		        	{
		        		stopPlayback(false);
		        	}
		        }else   //Pause/Resume Case
		            resumePlayback(confidenceMode);
		        
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
    		Log.e(TAG, String.format("Preview mode failed to start after %d ms", startTimeout_ms));
    		startThread.interrupt(); //cleanly kill thread
    		startThread = null;
    		streamCtl.RecoverDucati();
    		try {
    			Thread.sleep(2000);
    		} catch (Exception e) {}
    		streamCtl.RecoverMediaServer();
    	}

    }

    public void stopPlayback(final boolean confidenceMode)
    {
    	final CountDownLatch latch = new CountDownLatch(1);
    	Thread stopThread = new Thread(new Runnable() {
    		public void run() {
		    	// TODO: ioctl crash when input hdmi is plugged in but no video passing
		        Log.i(TAG, "stopPlayback");
		        if(is_preview)
		        {
		            try
		            {
		                if (CresCamera.mCamera != null)
		                {
		                	CresCamera.mCamera.setPreviewCallback(null); //probably not necessary since handled by callback, but doesn't hurt
		                	CresCamera.mCamera.stopPreview();            		
		            		CresCamera.releaseCamera();
		                }
		                is_preview = false;
		                streamCtl.setPauseVideoImage(false, DeviceMode.PREVIEW);
		                is_pause = false;
		                Log.i(TAG, "Playback stopped !");
		                
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
		            Log.i(TAG, "Playback already stopped");
		        
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
    		Log.e(TAG, String.format("Preview mode failed to stop after %d ms", stopTimeout_ms));
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

    protected void startAudio(){		
    	if ((skipAudio == false) 
			&& (streamCtl.userSettings.isRavaMode() == false)
			&& (streamCtl.userSettings.isProcessHdmiInAudio() == true)
			&& (hdmiIf != null))
    	{
	        if(!is_audioplaying)
	        {
				Log.i(TAG, "starting audio task");
	            audio_pb.startAudioTask();
	        }
	        else
	        {
				Log.i(TAG, "audio task already running");
	        }
	        is_audioplaying = true;
    	}
    	else
    	{
			Log.i(TAG, "NOT starting audio task");
    	}
    }

    public void stopAudio() {
    	if (skipAudio == false)
    	{
	        Log.i(TAG, "stoppingAudio");
	        if(is_audioplaying){
	            audio_pb.stopAudioTask();
	            is_audioplaying = false;
	        }
    	}
    }

    public void setVolume(int volume) {
    	audio_pb.setVolume(volume);
    }
    
    private class PreviewCB implements PreviewCallback
    {
    	public final boolean confidenceMode;
    	
    	public PreviewCB (boolean confidenceModeEnabled)
    	{
    		this.confidenceMode = confidenceModeEnabled;
    	}
    	
		@Override
		public void onPreviewFrame(byte[] data, Camera camera) {
			if (confidenceMode)
            	streamCtl.SendStreamState(StreamState.CONFIDENCEMODE, idx);
            else
            	streamCtl.SendStreamState(StreamState.STARTED, idx);

			//Set data to null so that it can be GC a little bit faster, it has a large allocation (size of frame)
			data = null;

			// After first frame arrives unregister callback to prevent sending multiple streamstates
			camera.setPreviewCallback(null);
		}    	
    }
    
    private class ErrorCB implements ErrorCallback
    {
    	public final boolean confidenceMode;
    	
    	public ErrorCB (boolean confidenceModeEnabled)
    	{
    		this.confidenceMode = confidenceModeEnabled;
    	}
    	
    	@Override
    	public void onError(int error, Camera camera) {
            Log.e(TAG, "Camera Error callback:" + error + "Camera :" + camera);
            //stopPlayback(confidenceMode); // TODO: decide what we want to do here
    	}
    }
}
