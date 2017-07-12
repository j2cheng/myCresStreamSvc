
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
            Log.d(TAG, "initCresSurfaceTextureListener(): View is not null");
            view.setSurfaceTextureListener(this);	
        } else {
            Log.d(TAG, "App passed null Texture view for stream in");
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
            Log.d(TAG, "getCresSurfaceTextureListener(): View is not null");
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
	    Log.d(TAG, "######### surfaceTextureAvailable ##############");
	    mLock.countDown(); //mark that surfaceTexture has been created and is ready for use
	    TextureView textureView = null;
	    for (int i=0; i < streamCtl.NumOfTextures; i++)
	    {
	    	textureView = streamCtl.getAirMediaTextureView(i);
	    	if (textureView.getSurfaceTexture() == surface)
	    	{
	    		textureView.setVisibility(View.INVISIBLE);
	    	}
	    }
    }

    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
	    Log.d(TAG, "######### surfaceTextureSizeChanged ##############");
	    streamCtl.invalidateSurface();
    }

    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
	    Log.d(TAG, "######### surfaceTextureDestroyed ##############");
        mLock = new CountDownLatch(1);
        return true;
    }

    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
	    //Log.d(TAG, "######### surfaceTextureUpdated ##############");
	    if (invalidateOnSurfaceTextureUpdate) {
	    	Log.d(TAG, "######### Invalidating surface due to SurfaceTextureUpdate ##############");
	    	streamCtl.invalidateSurface();
	    	invalidateOnSurfaceTextureUpdate = false;
	    }
    }

}

