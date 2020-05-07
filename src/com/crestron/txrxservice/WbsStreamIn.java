package com.crestron.txrxservice;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;

import com.crestron.airmedia.utilities.Common;
import com.crestron.txrxservice.CresStreamCtrl.CrestronHwPlatform;
import com.crestron.txrxservice.CresStreamCtrl.DeviceMode;
import com.crestron.txrxservice.CresStreamCtrl.StreamState;

public class WbsStreamIn implements SurfaceHolder.Callback {

    String TAG = "TxRx WbsStream";
    StringBuilder sb;

    private CresStreamCtrl streamCtl;
    private boolean[] isPlaying = new boolean[CresStreamCtrl.NumOfSurfaces];
    public final boolean useSurfaceTexture=false;

    private static native void 	nativeSetUrl(String url, int sessionId);
    private native void nativeInit();     // Initialize native code, build pipeline, etc
    private native void nativeFinalize(); // Destroy pipeline and shutdown native code
    private native void nativePlay(int sessionId);     // Go to PLAYING state
    private native void nativePause(int sessionId);    // Go to PAUSED state (if playing)
    private native void nativeUnpause(int sessionId);  // Go to PLAYING state (if paused)
    private native void nativeStop(int sessionId);     // Go to STOPPED state
    private static native boolean nativeClassInit(); // Initialize native class: cache Method IDs for callbacks
    private native void nativeSurfaceInit(Object surface, int sessionId);
    private native void nativeSurfaceFinalize(int sessionId);
    private native void nativeSetWbsUrl(String url, int sessionId);
    private native void nativeSetLogLevel(int level);
        
    public WbsStreamIn(CresStreamCtrl mContext) {
        Log.e(TAG, "WbsStreamIn :: Constructor called...!");
        streamCtl = mContext;
		for (int streamId=0; streamId < CresStreamCtrl.NumOfSurfaces; streamId++)
			isPlaying[streamId] = false;
        nativeInit();
    }

    public void setUrl(String url, int sessionId) {
    	nativeSetUrl(url, sessionId);	
	}
    
    public int[] getCurrentWidthHeight(int sessionId){
        int[] widthHeight = new int[2];
        // width in index 0, height in index 1
    	widthHeight[0] = streamCtl.userSettings.getW(sessionId);
        widthHeight[1] = streamCtl.userSettings.getH(sessionId);
        return widthHeight;
    }
    
    public int getCurrentStreamState(int sessionId){
    	return (streamCtl.getCurrentStreamState(sessionId)).getValue();
	}
    
    public void updateStreamStatus(int streamStateEnum, int sessionId){
    	// Send stream url again on start fb
    	if (streamStateEnum == CresStreamCtrl.StreamState.STARTED.getValue())
    	{
    		streamCtl.sockTask.SendDataToAllClients(MiscUtils.stringFormat("STREAM_URL%d=%s", sessionId, streamCtl.userSettings.getWbsStreamUrl(sessionId)));
    		streamCtl.sockTask.SendDataToAllClients(MiscUtils.stringFormat("WBS_STREAMING_STREAM_URL=%s", streamCtl.userSettings.getWbsStreamUrl(sessionId)));
    	}
    	streamCtl.SendStreamState(StreamState.getStreamStateFromInt(streamStateEnum), sessionId);
	}
    
    public void forceStop(int sessionId)
    {
    	final int sessId = sessionId;
		Log.i(TAG, "forceStop(): force stop requested");
    	Thread stopThread = new Thread(new Runnable() {
    		public void run() {
    			try {
    				Log.i(TAG, "forceStop(): invoking onStop in async thread");
    				onStop(sessId); 		
    			}
    			catch(Exception e){
    				// TODO: explore exception handling with better feedback of what went wrong to user
    				e.printStackTrace();        
    			}     
    		}
    	});
    	stopThread.start();
		Log.i(TAG, "forceStop(): force stop thread started - exiting");
    }
    
    //Play based on based Pause/Actual Playback 
    public void onStart(final int sessionId) {
    	final WbsStreamIn streamObj = this;
    	final CountDownLatch latch = new CountDownLatch(1);
    	final int startTimeout_ms = 20000;
    	if (streamCtl.userSettings.getWbsStreamUrl(sessionId).length() == 0)
    	{
    		// normally should not be used - should have an explicit stop message
    		if (isPlaying[sessionId])
    		{
    			onStop(sessionId);
    			return;
    		}
    	}
    	if (isPlaying[sessionId])
    	{
    		if (streamCtl.userSettings.getStreamState(sessionId) == StreamState.PAUSED) {
    			nativeUnpause(sessionId);
    		} else {
    			Log.i(TAG, "onStart(): streamId " + sessionId + " already playing !!");
    		}
			return;
    	}
    	Thread startThread = new Thread(new Runnable() {
    		public void run() {
    			try {
    				Surface s = null;
    				isPlaying[sessionId] = true;
    				updateNativeDataStruct(sessionId);
    				if (!useSurfaceTexture) {
    					s = streamCtl.getSurface(sessionId);
    				} else {
    					Log.i(TAG, "*********** passing surface derived from TextureView for sessionId: " + sessionId + " to 'nativeSurfaceInit' ************");
    					s = new Surface(streamCtl.getSurfaceTexture(sessionId));
    				}
					nativeSurfaceInit(s, sessionId);
    		    	Log.i(TAG, "Starting WBS Streaming");
    				nativePlay(sessionId);    		
    			}
    			catch(Exception e){
    				// TODO: explore exception handling with better feedback of what went wrong to user
    				streamCtl.SendStreamState(StreamState.STOPPED, sessionId);
    				e.printStackTrace();        
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
    		Log.e(TAG, MiscUtils.stringFormat("Stream In failed to start after %d ms", startTimeout_ms));
    		startThread.interrupt(); //cleanly kill thread
    		startThread = null;
    		streamCtl.RecoverTxrxService();
    	}
    }
    
    public void updateWindow(int streamId, int wbsWidth, int wbsHeight)
    {
    	Log.i(TAG, "In update window: streamId="+streamId+"   "+wbsWidth+"x"+wbsHeight);
    	
    	if (streamCtl.mCanvas != null)
    	{
    		streamCtl.setWbsResolution(streamId, wbsWidth, wbsHeight);
    	}
    	else
    	{
    		streamCtl.updateWindowWithVideoSize(streamId, useSurfaceTexture, wbsWidth, wbsHeight);
    	}
    }    	

    public void onStop(final int sessionId) {
    	if (!isPlaying[sessionId])
    	{
    		Log.i(TAG, "onStop(): streamId " + sessionId + " already stopped !!");
			streamCtl.SendStreamState(StreamState.STOPPED, sessionId);
    		return;
    	}
    	isPlaying[sessionId] = false;
    	Log.i(TAG, "Stopping WBS Streaming");
        nativeStop(sessionId);        
    	Log.i(TAG, "Finished stop - now calling nativeSurfaceFinalize");
    	nativeSurfaceFinalize(sessionId); // should be called in surfaceDestroyed()
    	Log.i(TAG, "Finished nativeSurfaceFinalize - exiting onStop()");
    }
    
    //Pause
    public void onPause(int sessionId) {
    	if (isPlaying[sessionId])
    	{
    		nativePause(sessionId);
    	}
    	else
    	{
    		Log.i(TAG, "onPause(): cannot pause a stopped stream !!");
    	}
    }
    
    private void updateNativeDataStruct(int sessionId)
    {
    	String url = streamCtl.userSettings.getWbsStreamUrl(sessionId);
    	String newUrl = url;
    	
    	setUrl(newUrl, sessionId); 
    }

    protected void onDestroy() {
        nativeFinalize();
//        super.onDestroy();
    }
    
    public void setLogLevel(int level)
    {
    	nativeSetLogLevel(level);
    }
 
	// Find the session id (aka stream number) given a surface holder.
	// Returns <0 for failure.
    private int sessionIdFromSurfaceHolder(SurfaceHolder holder) {
		int i;
		for(i=0; i<CresStreamCtrl.NumOfSurfaces; i++) {
			if(streamCtl.dispSurface.GetSurfaceHolder(i) == null){
			    continue;
			}
			if(streamCtl.dispSurface.GetSurfaceHolder(i) == holder) {
				return i;
			}			
		}    
		return -1;
    }
    
    //TODO: check if we can delete implements surface for class
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
            int height) {
		int sessionId = sessionIdFromSurfaceHolder(holder);     
		if(sessionId < 0)
		{
		    Log.i("WBSStream","Failed to get session id from surface holder");
		    return;
		}
        Log.i("WBSStream", "Surface for stream " + sessionId + " changed to format " + format + " width "
                + width + " height " + height);		
        nativeSurfaceInit (holder.getSurface(), sessionId);
    }

    public void surfaceCreated(SurfaceHolder holder) {
        int sessionId = sessionIdFromSurfaceHolder(holder);        
   		if(sessionId < 0)
		{
		    Log.i("WBSStream","Failed to get session id from surface holder");
		    return;
		}
        Log.i("WBSStream", "Surface for stream " + sessionId + " created: " + holder.getSurface());
        nativeSurfaceInit (holder.getSurface(), sessionId);
    }

    public void surfaceDestroyed(SurfaceHolder holder) {        
        int sessionId = sessionIdFromSurfaceHolder(holder);        
		if(sessionId < 0)
		{
		    Log.i("WBSStream","Failed to get session id from surface holder");
		    return;
		}        
		Log.i("WBSStream", "Surface for stream " + sessionId + " destroyed");		
        nativeSurfaceFinalize (sessionId);
    }
}
