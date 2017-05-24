
package com.crestron.txrxservice;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.content.Context;

public class SurfaceManager implements SurfaceHolder.Callback {
	private CountDownLatch mLock;
	private final int surfaceCreateTimeout_ms = 10000;
	private final CresStreamCtrl streamCtl;
	
    private SurfaceHolder crestSurfaceHolder;
    String TAG = "TxRx SurfaceMgr"; 

    public SurfaceManager(Context mContext){
        Log.e(TAG, "SurfaceManager:: Constructor called...!");
        mLock = new CountDownLatch(1);
        streamCtl = (CresStreamCtrl)mContext;
    }
    
    public void initCresSurfaceHolder (SurfaceView view) {
    	if (view != null) {
            Log.d(TAG, "initCresSurfaceHolder(): View is not null");
            crestSurfaceHolder = view.getHolder();	
            crestSurfaceHolder.addCallback(this);
            crestSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
            view.setZOrderOnTop(true);

        } else {
            Log.d(TAG, "App passed null surface view for stream in");
        }
    }

// Prepare the class for destruction
    public void close()
    {
    	if (crestSurfaceHolder != null)
    		crestSurfaceHolder.removeCallback(this);
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
    		Log.e(TAG, String.format("Android failed to create surface after %d ms", surfaceCreateTimeout_ms));
    		streamCtl.RecoverTxrxService();
    	}
    	    	
        if (view != null) {
            Log.d(TAG, "getCresSurfaceHolder(): View is not null");
            crestSurfaceHolder = view.getHolder();
//            crestSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
//            view.setZOrderOnTop(true);

        } else {
            Log.d(TAG, "App passed null surface view for stream in");
        }
        return crestSurfaceHolder;
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
    	Log.d(TAG, "######### surfacechanged surface="+holder.getSurface()+
    			"  fmt=" + String.valueOf(format) +
    			"  wxh=" + String.valueOf(w) + "x" + String.valueOf(h) +
    			"##############");
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
    	Log.d(TAG, "######### surfaceCreated surface="+holder.getSurface()+"##############");
    	mLock.countDown(); //mark that surface has been created and is ready for use
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
    	Log.d(TAG, "######### surfaceDestroyed surface="+holder.getSurface()+"##############");
    }
}
