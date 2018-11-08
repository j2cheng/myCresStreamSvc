
package com.crestron.txrxservice;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.crestron.airmedia.receiver.m360.models.AirMediaSession;

import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.View;
import android.content.Context;

public class SurfaceManager implements SurfaceHolder.Callback {
	private CountDownLatch mLock;
	private final int surfaceCreateTimeout_ms = 10000;
	private final CresStreamCtrl streamCtl;
	
    private SurfaceHolder crestSurfaceHolder;
    private Map<View, Boolean> viewInitializedMap = new ConcurrentHashMap<View, Boolean>();
    String TAG = "TxRx SurfaceMgr"; 

    public SurfaceManager(Context mContext){
        Log.e(TAG, "SurfaceManager:: Constructor called...!");
        mLock = new CountDownLatch(1);
        streamCtl = (CresStreamCtrl)mContext;
        viewInitializedMap.clear();
    }
    
    public void initCresSurfaceHolder (SurfaceView view) {
    	if (view != null) {
            Log.i(TAG, "initCresSurfaceHolder(): View is not null");
            crestSurfaceHolder = view.getHolder();	
            crestSurfaceHolder.addCallback(this);
            crestSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
            view.setZOrderOnTop(true);
            viewInitializedMap.put(view, false);
        } else {
            Log.i(TAG, "App passed null surface view for stream in");
        }
    }

// Prepare the class for destruction
    public void close()
    {
    	if (crestSurfaceHolder != null)
    		crestSurfaceHolder.removeCallback(this);
    	viewInitializedMap.clear();
    	mLock = null;  	
    	crestSurfaceHolder = null; 
    }
    
    public SurfaceHolder getCresSurfaceHolder (SurfaceView view) {
    	//TODO: Have one lock per surface
		//Wait for callback to determine if surface is ready to be used
    	boolean surfaceCreatedSuccess = true; //indicates that there was no time out condition
    	try { surfaceCreatedSuccess = mLock.await(surfaceCreateTimeout_ms, TimeUnit.MILLISECONDS); }
    	catch (InterruptedException ex) { ex.printStackTrace(); }
    	
    	if (!surfaceCreatedSuccess)
    	{
    		Log.e(TAG, MiscUtils.stringFormat("Android failed to create surface after %d ms", surfaceCreateTimeout_ms));
    		streamCtl.RecoverTxrxService();
    	}
    	    	
        if (view != null) {
            Log.i(TAG, "getCresSurfaceHolder(): View is not null");
            crestSurfaceHolder = view.getHolder();
//            crestSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
//            view.setZOrderOnTop(true);

        } else {
            Log.i(TAG, "App passed null surface view for stream in");
        }
        return crestSurfaceHolder;
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
    	Log.i(TAG, "######### surfacechanged surface="+holder.getSurface()+
    			"  fmt=" + String.valueOf(format) +
    			"  wxh=" + String.valueOf(w) + "x" + String.valueOf(h) +
    			"##############");
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
    	Log.i(TAG, "######### surfaceCreated surface="+holder.getSurface()+"##############");
	    for (int i=0; i < CresStreamCtrl.NumOfSurfaces; i++)
	    {
	    	SurfaceView sv = streamCtl.getSurfaceView(i);
	    	// For a given view this should only be done only once at startup so surface is created once 
	    	if (sv.getHolder() == holder)
	    	{
	    		Boolean initialized = viewInitializedMap.get(sv);
	    		if (initialized != null && !initialized)
	    		{
	    			// Commenting out the below line because it may have cause issues with SurfaceFlinger (seen in PWC customer site)
//	    			sv.setVisibility(View.INVISIBLE);
	    			viewInitializedMap.put(sv, true);
					break;
	    		}
	    	}
	    }
    	mLock.countDown(); //mark that surface has been created and is ready for use
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
    	Log.i(TAG, "######### surfaceDestroyed surface="+holder.getSurface()+"##############");
    }
}
