
package com.crestron.txrxservice;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import android.util.Log;
import android.view.TextureView.SurfaceTextureListener;
import android.view.TextureView;
import android.view.View;
import android.content.Context;
import android.graphics.SurfaceTexture;

// implementing for TextureView 
public class SurfaceTextureManager implements TextureView.SurfaceTextureListener {
	private CountDownLatch mLock;
	private final int stCreateTimeout_ms = 10000;
	private final CresStreamCtrl streamCtl;
	
    private SurfaceTexture CresSurfaceTexture;
    String TAG = "TxRx SurfaceTextureMgr"; 
    private boolean invalidateOnSurfaceTextureUpdate = false;

    public SurfaceTextureManager(Context mContext){
        Log.e(TAG, "SurfaceTextureManager:: Constructor called...!");
        mLock = new CountDownLatch(1);
        streamCtl = (CresStreamCtrl)mContext;
    }
    
    public void initCresSurfaceTextureListener (TextureView view) {
    	if (view != null) {
            Log.i(TAG, "initCresSurfaceTextureListener(): View is not null");
            view.setSurfaceTextureListener(this);	
        } else {
            Log.i(TAG, "App passed null Texture view for stream in");
        }
    }

    // Prepare the class for destruction
    public void close()
    {
    	mLock = null;  	
    }

    public SurfaceTexture getCresSurfaceTexture(TextureView view) {
    	//TODO: Have one lock per view
		//Wait for callback to determine if surfaceTexture is ready to be used
    	boolean stCreatedSuccess = true; //indicates that there was no time out condition
    	try { stCreatedSuccess = mLock.await(stCreateTimeout_ms, TimeUnit.MILLISECONDS); }
    	catch (InterruptedException ex) { ex.printStackTrace(); }
    	
    	if (!stCreatedSuccess)
    	{
    		Log.e(TAG, String.format("**** Android failed to create surface texture after %d ms ****", stCreateTimeout_ms));
    		streamCtl.RecoverTxrxService();
    	}
    	    	
        if (view != null) {
            Log.i(TAG, "getCresSurfaceTextureListener(): View is not null");
            CresSurfaceTexture = view.getSurfaceTexture();
        } else {
            Log.e(TAG, "App passed null texture view for stream in");
        }
        return CresSurfaceTexture;
    }

    public void setInvalidateOnSurfaceTextureUpdate(boolean value)
    {
    	invalidateOnSurfaceTextureUpdate = value;
    }
    
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
	    Log.i(TAG, "######### surfaceTextureAvailable ##############");
	    mLock.countDown(); //mark that surfaceTexture has been created and is ready for use
	    for (int i=0; i < streamCtl.NumOfTextures; i++)
	    {
	    	TextureView tv = streamCtl.getTextureView(i);
	    	if (tv.getSurfaceTexture() == surface)
	    	{
	    		tv.setVisibility(View.INVISIBLE);
	    	}
	    }
    }

    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
	    Log.i(TAG, "######### surfaceTextureSizeChanged ##############");
	    streamCtl.invalidateSurface();
    }

    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
	    Log.i(TAG, "######### surfaceTextureDestroyed ##############");
        mLock = new CountDownLatch(1);
        return true;
    }

    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
	    //Log.i(TAG, "######### surfaceTextureUpdated ##############");
    	int streamId = 0;
	    for (streamId=0; streamId < CresStreamCtrl.NumOfTextures; streamId++)
	    {
	    	if (streamCtl.getTextureView(streamId).getSurfaceTexture() == surface)
	    	{
	    		break;
	    	}
	    }
	    if (streamId == CresStreamCtrl.NumOfTextures)
	    {
	    	/* This should not happen - should find a match */
		    Log.w(TAG, "######### surfaceTextureUpdated event for unknown SurfaceTexture: "+surface+" ##############");
		    return;
	    }
		if (streamCtl.updateStreamStateOnFirstFrame[streamId]) {
		    Log.i(TAG, "######### First frame based Stream State Started Update for streamId="+streamId+" ##############");
		    streamCtl.sendAirMediaStartedState(streamId);
		    streamCtl.setUpdateStreamStateOnFirstFrame(streamId, false);
		}
	    if (invalidateOnSurfaceTextureUpdate) {
	    	Log.i(TAG, "######### Invalidating surface due to SurfaceTextureUpdate ##############");
	    	streamCtl.invalidateSurface(); // forces parent layout invalidation - does not need surface as they all have same parent
	    	invalidateOnSurfaceTextureUpdate = false;
	    }
    }

}

