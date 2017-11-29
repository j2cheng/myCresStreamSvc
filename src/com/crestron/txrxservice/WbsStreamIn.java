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
    private boolean isPlaying = false;
    public final boolean useSurfaceTexture=true;

    private static native void 	nativeSetUrl(String url, int sessionId);
    private native void nativeInit();     // Initialize native code, build pipeline, etc
    private native void nativeFinalize(); // Destroy pipeline and shutdown native code
    private native void nativePlay(int sessionId);     // Set pipeline to PLAYING
    private native void nativePause(int sessionId);    // Set pipeline to PAUSED
    private native void nativeStop(int sessionId);    // Set pipeline to NULL
    private static native boolean nativeClassInit(); // Initialize native class: cache Method IDs for callbacks
    private native void nativeSurfaceInit(Object surface, int sessionId);
    private native void nativeSurfaceFinalize(int sessionId);
    private long native_custom_data;      // Native code will use this to keep private data

    private native void 	nativeSetWbsUrl(String url, int sessionId);
    private native void		nativeSetLogLevel(int logLevel);
        
    public WbsStreamIn(CresStreamCtrl mContext) {
        Log.e(TAG, "WbsStreamIn :: Constructor called...!");
        streamCtl = mContext;
        nativeInit();
    }

    public void setUrl(String url, int sessionId) {
    	nativeSetUrl(url, sessionId);	
	}
    
    public void setLogLevel(int logLevel) {
    	nativeSetLogLevel(logLevel);
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
    		streamCtl.sockTask.SendDataToAllClients(String.format("STREAM_URL%d=%s", sessionId, streamCtl.userSettings.getWbsStreamUrl(sessionId)));
    		streamCtl.sockTask.SendDataToAllClients(String.format("WBS_STREAMING_STREAM_URL=%s", streamCtl.userSettings.getWbsStreamUrl(sessionId)));
    	}
    	streamCtl.SendStreamState(StreamState.getStreamStateFromInt(streamStateEnum), sessionId); 
	}
    
    //Play based on based Pause/Actual Playback 
    public void onStart(final int sessionId) {
    	final WbsStreamIn streamObj = this;
    	final CountDownLatch latch = new CountDownLatch(1);
    	final int startTimeout_ms = 20000;
    	if (isPlaying)
    	{
    		Log.d(TAG, "onStart(): already playing !!");
    		return;
    	}
    	Thread startThread = new Thread(new Runnable() {
    		public void run() {
    			try {
    				isPlaying = true;
    				SurfaceHolder sh = streamCtl.getCresSurfaceHolder(sessionId);
    				updateNativeDataStruct(sessionId);
    				if (!useSurfaceTexture) {
    					nativeSurfaceInit(sh.getSurface(), sessionId);
    				} else {
    					Log.d(TAG, "*********** passing surface derived from TextureView for sessionId: " + sessionId + " to 'nativeSurfaceInit' ************");
    					Surface s = new Surface(streamCtl.getAirMediaSurfaceTexture(sessionId));
    					nativeSurfaceInit(s, sessionId);
    				}
    		    	Log.d(TAG, "Starting WBS Streaming");
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
    		Log.e(TAG, String.format("Stream In failed to start after %d ms", startTimeout_ms));
    		startThread.interrupt(); //cleanly kill thread
    		startThread = null;
    		streamCtl.RecoverTxrxService();
    	}
    }
    
    public void updateWindow(int streamId, int wbsWidth, int wbsHeight)
    {
    	Log.d(TAG, "In update window: streamId="+streamId+"   "+wbsWidth+"x"+wbsHeight);
    	int width = streamCtl.userSettings.getW(streamId);
    	int height = streamCtl.userSettings.getH(streamId);

    	if ((width == 0) && (height == 0))
    	{
    		Point size = streamCtl.getDisplaySize();

    		width = size.x;
    		height = size.y;
    	}
    	Rect r = MiscUtils.getAspectRatioPreservingRectangle(
    			streamCtl.userSettings.getXloc(streamId), streamCtl.userSettings.getYloc(streamId), width, height,
    			wbsWidth, wbsHeight);
		Log.d(TAG, "updateWindow: "+r.width()+"x"+r.height()+" @ ("+r.left+","+r.top+")");
		streamCtl.setWindowDimensions(r.left, r.top, r.width(), r.height(), streamId, useSurfaceTexture);
    }    	

    public void onStop(final int sessionId) {
    	if (!isPlaying)
    	{
    		Log.d(TAG, "onStop(): already stopped !!");
    		return;
    	}
    	isPlaying = false;
    	Log.d(TAG, "Stopping WBS Streaming");
        //nativeSurfaceFinalize (sessionId);should be called in surfaceDestroyed()
        nativeStop(sessionId);        
    }
    
    //Pause
    public void onPause(int sessionId) {
        nativePause(sessionId);
    }
    
    private void updateNativeDataStruct(int sessionId)
    {
    	String url = streamCtl.userSettings.getWbsStreamUrl(sessionId);
    	String newUrl = url;
    	
    	setUrl(newUrl, sessionId); 
    }

    private void setMessage(final String message) {
        Log.d(TAG, "setMessage " + message);
    }
    
    protected void onDestroy() {
        nativeFinalize();
//        super.onDestroy();
    }

    static {
        nativeClassInit();
    }
 
	// Find the session id (aka stream number) given a surface holder.
	// Returns <0 for failure.
    private int sessionIdFromSurfaceHolder(SurfaceHolder holder) {
		int i;
		for(i=0; i<CresStreamCtrl.NumOfSurfaces; i++) {
			if(streamCtl.getCresSurfaceHolder(i) == null){
			    continue;
			}
			if(streamCtl.getCresSurfaceHolder(i) == holder) {
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
		    Log.d("WBSStream","Failed to get session id from surface holder");
		    return;
		}
        Log.d("WBSStream", "Surface for stream " + sessionId + " changed to format " + format + " width "
                + width + " height " + height);		
        nativeSurfaceInit (holder.getSurface(), sessionId);
    }

    public void surfaceCreated(SurfaceHolder holder) {
        int sessionId = sessionIdFromSurfaceHolder(holder);        
   		if(sessionId < 0)
		{
		    Log.d("WBSStream","Failed to get session id from surface holder");
		    return;
		}
        Log.d("WBSStream", "Surface for stream " + sessionId + " created: " + holder.getSurface());
        nativeSurfaceInit (holder.getSurface(), sessionId);
    }

    public void surfaceDestroyed(SurfaceHolder holder) {        
        int sessionId = sessionIdFromSurfaceHolder(holder);        
		if(sessionId < 0)
		{
		    Log.d("WBSStream","Failed to get session id from surface holder");
		    return;
		}        
		Log.d("WBSStream", "Surface for stream " + sessionId + " destroyed");		
        nativeSurfaceFinalize (sessionId);
    }
}
