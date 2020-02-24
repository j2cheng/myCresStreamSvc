package com.crestron.txrxservice;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.crestron.airmedia.receiver.m360.ipc.AirMediaSize;
import com.crestron.txrxservice.CresStreamCtrl.DeviceMode;
import com.crestron.txrxservice.CresStreamCtrl.StreamState;

import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.Camera.ErrorCallback;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PreviewCallback;
import android.util.Log;
import android.view.Surface;
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
    private Thread preview_timeout_thread=null;
    private CountDownLatch preview_timeout_latch = null;
    private Object preview_timeout_lock = new Object();
    private AirMediaSize resolution = new AirMediaSize(0,0);

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
		// By default do not force RGB (only used for debugging)
    	startPlayback(confidenceMode, false);
    }
    
    public void startPlayback(final boolean confidenceMode, final boolean forceRgb){
    	final CountDownLatch latch = new CountDownLatch(1);
    	Thread startThread = new Thread(new Runnable() {
    		public void run() {
		        Log.i(TAG, "starting Playback " + is_preview);
		        if(is_preview == false){		        	
		        	Log.i(TAG, "Actual startPlayback, forceRGB set to " + forceRgb);
		        	
		        	// TODO: create console command to enable/disable rgb888
		        	if (forceRgb || streamCtl.isRGB888HDMIVideoSupported)
		        		ProductSpecific.setRGB888Mode(true);
					else
						ProductSpecific.setRGB888Mode(false);
					
		        	// Update window size in case the aspect ratio or stretch changes
			        try {
			        	streamCtl.updateWindowWithVideoSize(idx, false, Integer.parseInt(streamCtl.hdmiInput.getHorizontalRes()), Integer.parseInt(streamCtl.hdmiInput.getVerticalRes()));
        				resolution = new AirMediaSize(Integer.parseInt(streamCtl.hdmiInput.getHorizontalRes()), Integer.parseInt(streamCtl.hdmiInput.getVerticalRes()));
			        } catch (NumberFormatException e) { e.printStackTrace(); }

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
		        				// for this to work a change to added a public method in android.hardware.Camera
		        				Surface s = streamCtl.getSurface(idx);
								ProductSpecific.setPreviewSurface(CresCamera.mCamera, s);
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
		        					if (forceRgb || streamCtl.isRGB888HDMIVideoSupported)
		        						localParameters.setPreviewFormat(ProductSpecific.getRGB888PixelFormat());
		        					else
		        						localParameters.setPreviewFormat(ImageFormat.NV21);
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
		        				resolution = new AirMediaSize(localParameters.getPreviewSize().width, localParameters.getPreviewSize().height);
		        				CresCamera.mCamera.setPreviewCallback(new PreviewCB(confidenceMode));
		        				CresCamera.mCamera.setErrorCallback(new ErrorCB(confidenceMode));
		        				signalPreviewTimeoutThread();	// kill the previous thread if it exists
		        				preview_timeout_thread = new Thread(new previewTimeout());
		        				preview_timeout_thread.start();
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
    		Log.e(TAG, MiscUtils.stringFormat("Preview mode failed to start after %d ms", startTimeout_ms));
    		startThread.interrupt(); //cleanly kill thread
    		startThread = null;
    		streamCtl.RecoverDucati();
    		try {
    			Thread.sleep(2000);
    		} catch (Exception e) {}
    		streamCtl.RecoverMediaServer();
    	}

    }
    
    private void signalPreviewTimeoutThread() {
    	synchronized (preview_timeout_lock) {
    		if (preview_timeout_thread != null) {
    			Log.i(TAG, "startPlayback(): interrupt preview_timeout_thread");
    			// if prior thread exists - kill it
    			preview_timeout_latch = new CountDownLatch(1);
    			preview_timeout_thread.interrupt();
    			try {
    				boolean success = preview_timeout_latch.await(5, TimeUnit.SECONDS);
    				if (!success)
    					Log.e(TAG, "Error: preview_timeout_thread latch timed out");
    			}
    			catch (InterruptedException e) { e.printStackTrace(); }
    			preview_timeout_latch = null;			
    		}
		}    	
    }

 	public class previewTimeout implements Runnable {
    	public void run() {
			Log.i(TAG, "previewTimeoutThread launched to monitor completion of startCameraPreview()");
			try {
				Thread.sleep(30000);
				Log.i(TAG, " previewTimeoutThread timeout expired - calling RecoverMediaServer");
				streamCtl.RecoverMediaServer();
			} catch (InterruptedException e) {
				Log.i(TAG, "previewTimeoutThread exit before timeout");
			}
    		preview_timeout_thread = null;
    		if (preview_timeout_latch != null)
    			preview_timeout_latch.countDown();
    		else
    			Log.e(TAG, "Error: previewTimeoutThread set to null but latch is null!");
    	}
    }
    
    public void stopPlayback(final boolean confidenceMode){
		// By default do not force RGB (only used for debugging)
    	stopPlayback(confidenceMode, false);
    }

    public void stopPlayback(final boolean confidenceMode, final boolean forceRgb)
    {
    	final CountDownLatch latch = new CountDownLatch(1);
    	Thread stopThread = new Thread(new Runnable() {
    		public void run() {
		    	// TODO: ioctl crash when input hdmi is plugged in but no video passing
		        Log.i(TAG, "stopPlayback, forceRgb is set to " + forceRgb);
		        if(is_preview)
		        {
		            try
		            {
						// This should be moved to the below comment once HWC.c is updated to act on mode changes, that way we don't have to set the mode while pipeline is running
						// Otherwise screen will keep last frame up until a screen update occurs 
//		            	if (forceRgb || streamCtl.isRGB888HDMIVideoSupported)
//		            		ProductSpecific.setRGB888Mode(false);
		            	
		                if (CresCamera.mCamera != null)
		                {
		                	CresCamera.mCamera.setPreviewCallback(null); //probably not necessary since handled by callback, but doesn't hurt
		                	CresCamera.mCamera.stopPreview();     
		            		CresCamera.releaseCamera();
		                }
		                resolution = new AirMediaSize(0,0);
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
		        
		        signalPreviewTimeoutThread();
		        
		        latch.countDown();
    		}
    	});
    	stopThread.start();
    	
    	// We launch the stop commands in its own thread and timeout in case mediaserver gets hung
    	boolean successfulStop = true; //indicates that there was no time out condition
    	try { successfulStop = latch.await(stopTimeout_ms, TimeUnit.MILLISECONDS); }
    	catch (InterruptedException ex) { ex.printStackTrace(); }
    	    	
        // Reset Tag
		streamCtl.setSurfaceViewTag(idx, "VideoLayer");
		
    	streamCtl.checkVideoTimeouts(successfulStop);       
    	if (!successfulStop)
    	{
    		Log.e(TAG, MiscUtils.stringFormat("Preview mode failed to stop after %d ms", stopTimeout_ms));
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
    
    public void restartAudio() {
    	if (is_audioplaying == true)
    	{
    		stopAudio();
    		startAudio();
    	}
    }

    public void setVolume(int volume) {
    	audio_pb.setVolume(volume);
    }
    
    public AirMediaSize getResolution() {
    	return resolution;
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
			Log.i(TAG, "got first preview frame - kill preview timeout thread");
			signalPreviewTimeoutThread();
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
