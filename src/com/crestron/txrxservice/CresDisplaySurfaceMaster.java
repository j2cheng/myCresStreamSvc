/**
 * 
 */
package com.crestron.txrxservice;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import com.crestron.txrxservice.CresStreamCtrl.DeviceMode;
import com.crestron.txrxservice.CresStreamCtrl.ServiceMode;
import com.crestron.txrxservice.CresStreamCtrl.videoDimensions;

import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.TextureView;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.app.Activity;
import android.app.Service;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.content.Context;

// PEM - for 2nd display
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManager.DisplayListener;
import android.view.Display;
import android.app.Service;

/**
 * CresDisplaySurface class
 * 
 * This class abstracts the windowmanager and surfaceview related details
 * from CresStreamCtrl
 *
 */
public class CresDisplaySurfaceMaster implements CresDisplaySurface 
{
    private SurfaceView[] displaySurface = new SurfaceView[CresStreamCtrl.NumOfSurfaces];
    private TextureView[] displayTexture = new TextureView[CresStreamCtrl.NumOfTextures];
    private boolean[] useTextureView = new boolean[CresStreamCtrl.NumOfSurfaces];
    CresStreamCtrl streamCtl;
    private RelativeLayout parentlayout;
    private RelativeLayout.LayoutParams viewLayoutParams;
    private WindowManager wm = null;
    private DisplayManager dm = null;
    private WindowManager.LayoutParams  wmLayoutParams;
    SurfaceManager sMGR;
    SurfaceTextureManager stMGR;
    
    private RelativeLayout backgroundLayout = null;
    private RelativeLayout.LayoutParams backgroundLayoutParams = null;
    private WindowManager.LayoutParams backgroundWmParams = null; 
    private ImageView backgroundView = null;
    private final ReentrantLock[] windowtLock = new ReentrantLock[CresStreamCtrl.NumOfSurfaces]; // members will be allocated in constructor
    private static Object windowTimerLock = new Object();
    private Timer[] windowTimer = new Timer[CresStreamCtrl.NumOfSurfaces];
    
    String TAG = "CresDisplaySurface";

    // PEM - add a view to the 2nd display
    // Needed to pass Service or else can't call getApplicationContext...
    private void addViewToExternalDisplay(Context app, View view, WindowManager.LayoutParams params){
        if (dm != null){
            Display dispArray[] = dm.getDisplays();
            if (dispArray.length>1){
            	if (wm == null)
            	{
	                Context displayContext = app.getApplicationContext().createDisplayContext(dispArray[1]);
	                wm = (WindowManager)displayContext.getSystemService(Context.WINDOW_SERVICE);
            	}
            	wm.addView(view, params);
            }
            else{
				Log.e(TAG, "Second display not ready yet");
            }
        }
    }
    
    
    // PEM - update a view to the 2nd display
    // Needed to pass Service or else can't call getApplicationContext...
    private void updateViewOnExternalDisplay(Context app, View view, WindowManager.LayoutParams params){
        if (dm != null){
            Display dispArray[] = dm.getDisplays();
            if (dispArray.length>1){
            	if (wm == null)
            	{
	                Context displayContext = app.getApplicationContext().createDisplayContext(dispArray[1]);
	                wm = (WindowManager)displayContext.getSystemService(Context.WINDOW_SERVICE);
            	}
            	wm.updateViewLayout(view, params);
            }
            else{
				Log.e(TAG, "Second display not ready yet");
            }
        }
    }
    
    
// Prepare the class for destruction
    public void close()
    {
        Log.i(TAG, "CresDisplaySurfaceMaster::close()");
        for (int sessionId=0; sessionId < CresStreamCtrl.NumOfSurfaces; sessionId++)
        {
			synchronized(windowTimerLock)
			{
				if (windowTimer[sessionId] != null)
				{
					Log.v(TAG, "CresDisplaySurface:close(): Canceling and purging delayed task for sessionId="+sessionId);
					// end this task and clear the timer
					windowTimer[sessionId].cancel();
					windowTimer[sessionId].purge();
					Log.v(TAG, "CresDisplaySurface:close(): setting window timer for sessionId="+sessionId+" to null");
					windowTimer[sessionId] = null;
				}
			}
        }
    	if (sMGR != null)
    		sMGR.close();
    	if (stMGR != null)
    		stMGR.close();
    	if (parentlayout != null)
    		parentlayout.removeAllViews();
    	if (backgroundLayout != null)
    		backgroundLayout.removeAllViews();
    	if (wm != null)
    	{
    		wm.removeView(backgroundLayout);
    		wm.removeView(parentlayout);
    	}
//    	dm.unregisterDisplayListener(streamCtl.mDisplayListener);
    	displaySurface = new SurfaceView[CresStreamCtrl.NumOfSurfaces];
    	displayTexture = null;
    	streamCtl = null;
    	parentlayout = null;
    	viewLayoutParams = null;
    	wm = null;
    	dm = null;
    	wmLayoutParams = null;
    	backgroundLayout = null;
    	backgroundLayoutParams = null;
    	backgroundWmParams = null; 
    	backgroundView = null;
    }
    
    // Take window lock for specific window
    public void lockWindow(int idx)
    {
    	windowtLock[idx].lock();
    }
    
    // Release window lock for specific window
    public void unlockWindow(int idx)
    {
    	windowtLock[idx].unlock();
    }

    public CresDisplaySurfaceMaster(Service app, int windowWidth, int windowHeight, boolean haveExternalDisplays, int color)
    {
        Log.i(TAG, "CresDisplaySurfaceMaster(): Creating surface: " + windowWidth + "x" + windowHeight );

        streamCtl = (CresStreamCtrl)app; 
        
		// Allocate window Locks
		for (int sessionId = 0; sessionId < CresStreamCtrl.NumOfSurfaces; sessionId++)
		{
			windowtLock[sessionId] =  new ReentrantLock(true);
		}

    	//Relative Layout to handle multiple views
        parentlayout = new RelativeLayout(app);
        
        //Instance for Surfaceholder for StreamIn/Preview
        sMGR = new SurfaceManager(app);
        Log.i(TAG, "Created SurfaceManager");
        
        //Instance for Surface Texture
        stMGR = new SurfaceTextureManager(app);
        Log.i(TAG, "Created SurfaceTextureManager");
        
        // Create the surface and set the width and height to the display width
        // and height
        for (int i = 0; i < CresStreamCtrl.NumOfSurfaces; i++){
            displaySurface[i] = new SurfaceView(app);
            String layerMarker = "VideoLayer";
            displaySurface[i].setTag(layerMarker);
            viewLayoutParams = new RelativeLayout.LayoutParams(windowWidth, windowHeight);
            parentlayout.addView(displaySurface[i], viewLayoutParams);
        }

        for (int i = 0; i < CresStreamCtrl.NumOfTextures; i++){
        	displayTexture[i] = new TextureView(app);
        	viewLayoutParams = new RelativeLayout.LayoutParams(windowWidth, windowHeight);
        	parentlayout.addView(displayTexture[i], viewLayoutParams);
        }
        
        //Setting WindowManager and Parameters with system overlay
        // Z-order by type (higher value is higher z order)
        // TYPE_SYSTEM_OVERLAY 		= ~181000
        // TYPE_SYSTEM_ALERT 		= ~91000
        // TYPE_PRIORITY_PHONE 		= ~71000
        // TYPE_SEARCH_BAR 			= ~41000
        // TYPE_PHONE 				= ~31000
        // TYPE_INPUT_METHOD_DIALOG	= ~21015
        // TYPE_APPLICATION 		= ~21000 <- Can't use as a service
        int windowType;
        
        if (streamCtl.alphaBlending)
        	windowType = WindowManager.LayoutParams.TYPE_PRIORITY_PHONE;	// For alpha blending
        else
        	windowType = WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY;	// For chroma blending

        wmLayoutParams = new WindowManager.LayoutParams(
        		windowWidth, 
        		windowHeight, 
        		windowType, // See above chart for z order control
        		(0 | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED), 
        		PixelFormat.TRANSLUCENT);
        wmLayoutParams.gravity = Gravity.TOP | Gravity.LEFT; 
        wmLayoutParams.x = 0;
        wmLayoutParams.y = 0;
		if(haveExternalDisplays){		
			Log.i(TAG, "moving streams to 2nd display");
			dm = (DisplayManager) app.getApplicationContext().getSystemService(Context.DISPLAY_SERVICE);
//	        if (dm != null)
//	        {
//	        	dm.registerDisplayListener(streamCtl.mDisplayListener, null);
//	        }
			addViewToExternalDisplay(app, parentlayout, wmLayoutParams);
		}
		else{
			wm =  (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
//			wm = app.getWindowManager();	// getting windowManager in this fashion fills in application binder token automatically
			wm.addView(parentlayout, wmLayoutParams); 
		}
		
        // Force invalidation
        forceLayoutInvalidation(parentlayout);
        
        // Add callbacks to surfaceviews
        for (int sessId = 0; sessId < CresStreamCtrl.NumOfSurfaces; sessId++)
        {
        	InitSurfaceHolder(sessId);
        }

        for (int sessId = 0; sessId < CresStreamCtrl.NumOfTextures; sessId++)
        {
        	InitSurfaceTextureListener(sessId);
        }
        
        createBackgroundWindow(windowWidth, windowHeight, color, haveExternalDisplays);
    }
    
    // Call when HDMI resolution changes are detected
    private void _setWindowManagerResolution(Service app, int w, int h, boolean haveExternalDisplay)
    {
    	// If parentLayout has not yet been created (see CresDisplaySurface) return immediately
        if (parentlayout == null) {
        	Log.i(TAG, "exiting setWindowManagerResolution because no parentlayout available");
        	return;
        }
        
        int windowType;
                
        if (streamCtl.alphaBlending)
        	windowType = WindowManager.LayoutParams.TYPE_PRIORITY_PHONE;	// For alpha blending
        else
        	windowType = WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY;	// For chroma blending
        
        wmLayoutParams = new WindowManager.LayoutParams(
    		w, 
    		h, 
    		windowType, // See above chart for z order control
    		(0 | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED), 
    		PixelFormat.TRANSLUCENT);
	    
        wmLayoutParams.gravity = Gravity.TOP | Gravity.LEFT; 
        wmLayoutParams.x = 0;
        wmLayoutParams.y = 0;
		if(haveExternalDisplay){		
			dm = (DisplayManager) app.getApplicationContext().getSystemService(Context.DISPLAY_SERVICE);
//	        if (dm != null)
//	        {
//	        	dm.registerDisplayListener(streamCtl.mDisplayListener, null);
//	        }
			updateViewOnExternalDisplay(app, parentlayout, wmLayoutParams);
		}
		else{
			wm =  (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
//			wm = app.getWindowManager();	// getting windowManager in this fashion fills in application binder token automatically
		    wm.updateViewLayout(parentlayout, wmLayoutParams);
		}
    	Log.i(TAG, "setWindowManagerResolution: resolution set to "+w+"x"+h);
    }
    
    public void setWindowManagerResolution(final int w, final int h, final boolean haveExternalDisplay)
    {
    	// Make sure surface changes are only done in UI (main) thread
    	if (Looper.myLooper() != Looper.getMainLooper())
    	{
    		final CountDownLatch latch = new CountDownLatch(1);
    		streamCtl.runOnUiThread(new Runnable() {
    			@Override
    			public void run() {
    				_setWindowManagerResolution(streamCtl, w, h, haveExternalDisplay);
    				latch.countDown();
    			}
    		});
    		try { 
    			if (latch.await(30, TimeUnit.SECONDS) == false)
    			{
    				Log.e(TAG, "invalidateSurface: timeout after 30 seconds");
    				streamCtl.RecoverTxrxService();
    			}
    		}
    		catch (InterruptedException ex) { ex.printStackTrace(); }  
    	}
    	else
    		_setWindowManagerResolution(streamCtl, w, h, haveExternalDisplay);        		
    }
	
    // Just remove the parentLayout
    public void RemoveView()
    {
    	wm.removeView(parentlayout);    	
    }
    
    // Update the x, y, width, height of the surface
    private void UpdateWindowSize(int x, int y, int width, int height, int idx, final boolean use_texture_view)
    {
    	Log.i(TAG, "UpdateWindowSize: " + x + "," + y + " " + width + "x" + height + "    use_texture_view=" + use_texture_view);

    	if (use_texture_view) {
    		// only used for AirMedia splashtop
    		viewLayoutParams = new RelativeLayout.LayoutParams(width, height);
    		viewLayoutParams.setMargins(x, y, 0, 0);
    		displayTexture[idx].setLayoutParams(viewLayoutParams);
    	} else {
			viewLayoutParams = new RelativeLayout.LayoutParams(width, height);
			viewLayoutParams.setMargins(x, y, 0, 0);
			displaySurface[idx].setLayoutParams(viewLayoutParams);
    	}

    	Log.i(TAG, "UpdateWindowSize: invalidateLayout" );
    	forceLayoutInvalidation(parentlayout);
    }
    
    // Return rectangle representing the current location of the surface
	// Does not need to be called on UI thread
    private Rect getCurrentWindowSize(int idx, final boolean use_texture_view)
    {
    	int x, y, w, h;
    	RelativeLayout.LayoutParams layout;
    	if (use_texture_view)
    	{
    		layout = (RelativeLayout.LayoutParams)displayTexture[idx].getLayoutParams();
    	}
    	else
    	{
    		layout = (RelativeLayout.LayoutParams)displaySurface[idx].getLayoutParams();    		
    	}
    	
    	x = layout.leftMargin;
		y = layout.topMargin;
		w = layout.width;
		h = layout.height;
    	return new Rect(x, y, x + w, y + h);
    }
    
    private void updateDimensions(int width, int height, int idx)
    {
    	Log.i(TAG, "UpdateDimensions: " + width + "x" + height );
    	//
    	//    	WindowManager.LayoutParams params = (WindowManager.LayoutParams) parentlayout.getLayoutParams();
    	//    	params.x = x;
    	//    	params.y = y;
    	//    	params.width = width;
    	//    	params.height = height;
    	//    	

    	/*old way of setting params*/

    	viewLayoutParams = new RelativeLayout.LayoutParams(width, height);
		displaySurface[idx].setLayoutParams(viewLayoutParams);

    	forceLayoutInvalidation(parentlayout);
    }
    
    // Update the width and height of the surface
    public void updateWH(final int idx)
    {
        Log.i(TAG, "updateWH " + idx + " : Lock");
        lockWindow(idx);
        try
        {
        	// Make sure surface changes are only done in UI (main) thread
        	if (Looper.myLooper() != Looper.getMainLooper())
        	{
        		final CountDownLatch latch = new CountDownLatch(1);

        		streamCtl.runOnUiThread(new Runnable() {
        			@Override
        			public void run() {
        				Point windowSize = streamCtl.getWindowSize(idx);
        				updateDimensions(windowSize.x, windowSize.y, idx);
        				latch.countDown();
        			}
        		});	            	

        		try { 
        			if (latch.await(60, TimeUnit.SECONDS) == false)
        			{
        				Log.e(TAG, "updateWH: Timeout after 60 seconds");
        				streamCtl.RecoverTxrxService();
        			}
        		}
        		catch (InterruptedException ex) { ex.printStackTrace(); }  
        	}
        	else
        	{
				Point windowSize = streamCtl.getWindowSize(idx);
				updateDimensions(windowSize.x, windowSize.y, idx);
        	}
        }
        finally
        {
            unlockWindow(idx);
            Log.i(TAG, "updateWH " + idx + " : Unlock");
        }
    }

    private void updateCoordinates(int x, int y, int idx)
    {
    	Log.i(TAG, "UpdateCoordinates: " + x + "," + y );
    	//
    	//    	WindowManager.LayoutParams params = (WindowManager.LayoutParams) parentlayout.getLayoutParams();
    	//    	params.x = x;
    	//    	params.y = y;
    	//    	params.width = width;
    	//    	params.height = height;
    	//    	

    	/*old way of setting params*/

    	viewLayoutParams = new RelativeLayout.LayoutParams(viewLayoutParams);
    	viewLayoutParams.setMargins(x, y, 0, 0);
		displaySurface[idx].setLayoutParams(viewLayoutParams);

    	forceLayoutInvalidation(parentlayout);
    }
    
    // Update the X, Y location of the surface
    public void updateXY(final int idx)
    {
    	Log.i(TAG, "updateXY " + idx + " : Lock");
    	lockWindow(idx);

    	final int x = streamCtl.userSettings.getXloc(idx);
    	final int y = streamCtl.userSettings.getYloc(idx);

    	try
    	{
    		// Make sure surface changes are only done in UI (main) thread
    		if (Looper.myLooper() != Looper.getMainLooper())
    		{
    			final CountDownLatch latch = new CountDownLatch(1);
    			streamCtl.runOnUiThread(new Runnable() {
    				@Override
    				public void run() {
    					updateCoordinates(x, y, idx); 
    					latch.countDown();
    				}
    			});
    			try { 
    				if (latch.await(30, TimeUnit.SECONDS) == false)
    				{
    					Log.e(TAG, "updateXY: timeout after 30 seconds");
    					streamCtl.RecoverTxrxService();
    				}
    			}
    			catch (InterruptedException ex) { ex.printStackTrace(); }  
    		}
    		else
    			updateCoordinates(x, y, idx);

    		// Gstreamer needs X and Y locations and does not update itself like it does for width and height
//    		if (streamCtl.userSettings.getMode(idx) == DeviceMode.STREAM_IN.ordinal()) 
//    			streamCtl.streamPlay.updateCurrentXYloc(x, y, idx);
    	}
    	finally
    	{
    		unlockWindow(idx);
    		Log.i(TAG, "updateXY " + idx + " : Unlock");
    	}
    }
    
    // update window with a new window size for the video
    public void updateWindowWithVideoSize(int idx, boolean use_texture_view, int videoWidth, int videoHeight)
    {
    	Log.i(TAG, "updateWindowWithVideoSize " + idx + " : Lock");
        lockWindow(idx);
        try
        {
        	int tmpX = streamCtl.userSettings.getXloc(idx);
        	int tmpY = streamCtl.userSettings.getYloc(idx);
        	Point windowSize = streamCtl.getWindowSize(idx);
       	
			// TODO Add rotation and textureview scaling when AirMedia is merged in
        	Rect newWindowSize;
        	if (streamCtl.userSettings.getStretchVideo(idx) == 1)
        	{
        		// Stretch video means set all dimensions to exact window size
        		newWindowSize = new Rect(tmpX, tmpY, tmpX + windowSize.x, tmpY + windowSize.y);
        	}
        	else
        	{
        		if (videoWidth == 0 || videoHeight == 0)
        		{
        			Log.i(TAG, "unable to preserve video aspect ratio, video width = " + videoWidth + " video height = " + videoHeight);
        			newWindowSize = new Rect(tmpX, tmpY, tmpX + windowSize.x, tmpY + windowSize.y);
        		}
        		else 
        		{
        			// Don't Stretch means calculate aspect ratio preserving window inside of given window dimensions
        			newWindowSize = MiscUtils.getAspectRatioPreservingRectangle(tmpX, tmpY, windowSize.x, windowSize.y, videoWidth, videoHeight);
        		}
        	}
        	
        	// Only update window if there is a change to the current window (this method is no intended to force update the same dimensions)
        	Rect currentWindowSize = getCurrentWindowSize(idx, use_texture_view);
        	if (	currentWindowSize.left != newWindowSize.left ||
        			currentWindowSize.top != newWindowSize.top ||
        			currentWindowSize.width() != newWindowSize.width() ||
        			currentWindowSize.height() != newWindowSize.height())
        	{
        		Log.i(TAG, "updateWindowWithVideoSize:  updating size to "+newWindowSize.width()+"x"+newWindowSize.height()+" @ ("+newWindowSize.left+","+newWindowSize.top+")");
        		updateWindow(newWindowSize.left, newWindowSize.top, newWindowSize.width(), newWindowSize.height(), idx, use_texture_view);
        	}
        }
        finally
        {
            unlockWindow(idx);
            Log.i(TAG, "updateWindowWithVideoSize " + idx + " : Unlock");
        }
    }
    
    // update window for surface
    public void updateWindow(final int idx, final boolean use_texture_view)
    {
    	updateFullWindow(idx, use_texture_view);
    }
    
    private void updateFullWindow(final int idx, final boolean use_texture_view)
    {
        Log.i(TAG, "updateFullWindow " + idx + " : Lock");
        lockWindow(idx);
        try
        {
        	int tmpX = streamCtl.userSettings.getXloc(idx);
        	int tmpY = streamCtl.userSettings.getYloc(idx);
        	Point windowSize = streamCtl.getWindowSize(idx);
        	updateWindow(tmpX, tmpY, windowSize.x, windowSize.y, idx, use_texture_view);
        }
        finally
        {
            unlockWindow(idx);
            Log.i(TAG, "updateFullWindow " + idx + " : Unlock");
        }
    }
    
	private class delayedCallToUpdateWindow extends TimerTask
	{
		private int x, y, w, h;
		private int sessionId;
		private boolean use_text;
		delayedCallToUpdateWindow(int xloc, int yloc, int width, int height, int sessId, final boolean use_texture_view)
		{
			x = xloc;
			y = yloc;
			w = width;
			h = height;
			sessionId = sessId;
			use_text = use_texture_view;
		}		
		@Override
		public void run() {
			Log.i(TAG, "delayed call to _updateWindow");
			_updateWindow(x, y, w, h, sessionId, use_text);
			synchronized(windowTimerLock)
			{
				Log.v(TAG, "Enter delayedCallToUpdateWindow: WindowTimer["+sessionId+"]="+windowTimer[sessionId]);
				// end this task and clear the timer
				windowTimer[sessionId].cancel();
				windowTimer[sessionId].purge();
				Log.v(TAG, "setting window timer for sessionId="+sessionId+" to null");
				windowTimer[sessionId] = null;
				Log.v(TAG, "Exit delayedCallToUpdateWindow: WindowTimer["+sessionId+"]="+windowTimer[sessionId]);
			}
		}
	}
	
    // update window for surface (this version is public because it is currently used by AirMedia)
    public void updateWindow(final int x, final int y, final int w, final int h, final int idx, final boolean use_texture_view)
    {
    	// to avoid bug : we will set window dimensions and then set again after 10 seconds
    	_updateWindow(x, y, w, h, idx, use_texture_view);
    	synchronized(windowTimerLock)
    	{
			Log.v(TAG, "Enter updateWindow: WindowTimer["+idx+"]="+windowTimer[idx]);
    		// if there is a pending timer update for this window then cancel it and add a new one.
    		if (windowTimer[idx] != null) {
    			Log.i(TAG, "canceling prior delayed updateWindow event for idx="+idx);
    			windowTimer[idx].cancel();
    			windowTimer[idx].purge();
    		}
			Log.i(TAG, "scheduling delayed updateWindow event for idx="+idx+"  use_texture_view="+use_texture_view);
    		windowTimer[idx] = new Timer(MiscUtils.stringFormat("windowTimer-%d", idx));
    		windowTimer[idx].schedule(new delayedCallToUpdateWindow(x, y, w, h, idx, use_texture_view), 10000);
			Log.v(TAG, "Exit updateWindow: WindowTimer["+idx+"]="+windowTimer[idx]);
    	}
    }
    
    // !!!!!!! Do not call this function use updateWindow instead !!!!!!!
    private void _updateWindow(int xloc, int yloc, int w, int h, final int idx, final boolean use_texture_view)
    {
        Log.i(TAG, "_updateWindow " + idx + " : Lock");
        lockWindow(idx);
        try
        {
            Log.i(TAG, "****** _updateWindow x="+String.valueOf(xloc)+" y="+String.valueOf(yloc) + " w=" + String.valueOf(w) + " h=" + String.valueOf(h));
        	// Needs to be final so that we can pass to another thread
        	final int width = w;
        	final int height = h;
        	final int x = xloc;
        	final int y = yloc;
        	
        	// Make sure surface changes are only done in UI (main) thread
        	if (Looper.myLooper() != Looper.getMainLooper())
        	{
        		final CountDownLatch latch = new CountDownLatch(1);

        		streamCtl.runOnUiThread(new Runnable() {
        			@Override
        			public void run() {
        				UpdateWindowSize(x, y, width, height, idx, use_texture_view);		       		    	 
        				latch.countDown();
        			}
        		});	            	

        		try { 
        			if (latch.await(60, TimeUnit.SECONDS) == false)
        			{
        				Log.e(TAG, "_updateWindow: Timeout after 60 seconds");
        				streamCtl.RecoverTxrxService();
        			}
        		}
        		catch (InterruptedException ex) { ex.printStackTrace(); }  
        	}
        	else
        	{
        		UpdateWindowSize(x, y, width, height, idx, use_texture_view);
        	}
        }
        finally
        {
            unlockWindow(idx);
            Log.i(TAG, "_updateWindow " + idx + " : Unlock");
        }
    }
    
	// invalidates parent layout
    public void forceParentLayoutInvalidation() {
    	// Removing bringToFront for X70 onwards
    	if (android.os.Build.VERSION.SDK_INT < 27 /*android.os.Build.VERSION_CODES.O_MR1*/) {
    		parentlayout.bringToFront();
    	}
        parentlayout.invalidate();
        parentlayout.requestLayout();
    }
    
	// invalidates layout
    private void forceLayoutInvalidation(RelativeLayout layout) {
    	if (android.os.Build.VERSION.SDK_INT < 27 /*android.os.Build.VERSION_CODES.O_MR1*/) {
    		layout.bringToFront();
    	}
    	layout.invalidate();
        layout.requestLayout();
    }

	// invalidates parent layout in UI thread
    public void invalidateSurface()
    {
    	// Make sure surface changes are only done in UI (main) thread
    	if (Looper.myLooper() != Looper.getMainLooper())
    	{
    		final CountDownLatch latch = new CountDownLatch(1);
    		streamCtl.runOnUiThread(new Runnable() {
    			@Override
    			public void run() {
    				forceParentLayoutInvalidation();
    				latch.countDown();
    			}
    		});
    		try { 
    			if (latch.await(30, TimeUnit.SECONDS) == false)
    			{
    				Log.e(TAG, "invalidateSurface: timeout after 30 seconds");
    				streamCtl.RecoverTxrxService();
    			}
    		}
    		catch (InterruptedException ex) { ex.printStackTrace(); }  
    	}
    	else
    		forceParentLayoutInvalidation();        		
    }
    
    // get Surface for Window idx
    public Surface getSurface(int idx)
    {
    	return GetSurfaceHolder(idx).getSurface();
    }
    
    // set Surface for Window idx
    public void setSurface(int idx, Surface s)
    {
		Log.i(TAG, "setSurface: idx=" + idx + " called in Master Mode - should not occur");
    }
    
    // delete Surface for Window idx
    public void deleteSurface(int idx)
    {
		Log.i(TAG, "deleteSurface: idx=" + idx + " called in Master Mode - should not occur");

    }
    
	public int surface2streamId(Surface surface)
	{
		int streamId = -1;
		if (surface == null)
			return streamId;
		for (int idx=0; idx < CresStreamCtrl.NumOfSurfaces; idx++)
		{
			Surface s = sMGR.getCresSurfaceHolder(displaySurface[idx]).getSurface();
			if ((s != null) && (surface != null) && s.toString().equals(surface.toString())) {
				streamId = idx;
				break;
			}
		}
		return streamId;
	}
	
	public void setTag(int idx, String tag)
	{
		Log.i(TAG, "CresDisplaySurfaceMaster::setTag: idx=" + idx + " setting tag to "+tag);
		if (displaySurface[idx].getVisibility() == View.VISIBLE)
		{
			Log.w(TAG, "CresDisplaySurfaceMaster::setTag: idx=" + idx + " setting tag when view is visible");
		}
		displaySurface[idx].setTag(tag);
	}

    /**
     * Return the surface holder for the display surface
     * @return Surface holder of the surface view
     */
    public SurfaceHolder GetSurfaceHolder(int idx)
    {
        return sMGR.getCresSurfaceHolder(displaySurface[idx]);
    }
    
    /**
     * Initialize surface holder for the display surface, create the surface for use
     */
    public void InitSurfaceHolder(int idx)
    {
        sMGR.initCresSurfaceHolder(displaySurface[idx]);
    }
    
    /**
     * Return the surface view for the display surface
     * @return SurfaceView
     */
    public SurfaceView GetSurfaceView(int idx)
    {
    	return displaySurface[idx];
    }
    
    /**
     * Initialize surface texture, set listener
     */
    public void InitSurfaceTextureListener(int idx)
    {
        stMGR.initCresSurfaceTextureListener(displayTexture[idx]);
    }
	
    public TextureView GetTextureView(int idx)
    {
        return displayTexture[idx];
    }
    
    public SurfaceTexture GetSurfaceTexture(int idx)
    {
    	return stMGR.getCresSurfaceTexture(displayTexture[idx]);
    }
    
    /**
     * Hide the window by setting the view visibility
     */
    public void HideWindow(int idx)
    {
    	if (android.os.Build.VERSION.SDK_INT >= 27 /*android.os.Build.VERSION_CODES.O_MR1*/) {
    		Log.i(TAG, "HideWindow() move surfaceview off screen");
    		updateWindow(-32, -20, 32, 18, idx, false);
    	} else {
    		displaySurface[idx].setVisibility(View.INVISIBLE);
    	}
    	useTextureView[idx] = false;
    	LogVisibility(MiscUtils.stringFormat("HideWindow-%d", idx));
    }
    
    /**
     * Show the window by setting the view visibility
     */
    public void ShowWindow(int idx)
    {
    	displaySurface[idx].setVisibility(View.VISIBLE);
    	useTextureView[idx] = false;
    	LogVisibility(MiscUtils.stringFormat("ShowWindow-%d", idx));
    }
    
	// Call this function with streams stopped
    public void updateZOrder(Integer[][] zOrder)
    {
    	Log.i(TAG, "Updating z order");
 		
        parentlayout.removeAllViews();
        //Add from lowest zorder to highest
        for (int i = 0; i < CresStreamCtrl.NumOfSurfaces; i++)
        {
        	parentlayout.addView(displaySurface[zOrder[0][i]], viewLayoutParams);
        }
        forceLayoutInvalidation(parentlayout);
    }
    
    private String visibility(int v)
    {
    	switch (v) {
    	case View.VISIBLE:
    		return "Visible";
    	case View.INVISIBLE:
    		return "Invisible";
    	case View.GONE:
    		return "Gone";
    	default:
    		return "Unknown";
    	}    		
    }
    
    private void LogVisibility(String s)
    {
        StringBuilder sb = new StringBuilder(512);
        sb.append("SurfaceViews[]=[");
        for (int i=0; i < CresStreamCtrl.NumOfSurfaces; i++)
        {
        	sb.append(visibility(displaySurface[i].getVisibility()));
        	if (i < (CresStreamCtrl.NumOfSurfaces-1))
        		sb.append(", ");
        }
        sb.append("]   TextureViews[]=[");
        for (int i=0; i < CresStreamCtrl.NumOfTextures; i++)
        {
        	sb.append(visibility(displayTexture[i].getVisibility()));
        	if (i < (CresStreamCtrl.NumOfTextures-1))
        		sb.append(", ");
        }
        sb.append("]");
        Log.d(TAG, MiscUtils.stringFormat("%s: %s", s, sb.toString()));
    }
    
    /**
     * Hide the window by setting the view visibility
     */
    public void HideTextureWindow(int idx)
    {
    	//parentlayout.removeView(displayTexture);
    	displayTexture[idx].setVisibility(View.INVISIBLE);
    	useTextureView[idx] = false;
    	LogVisibility(MiscUtils.stringFormat("HideTextureWindow-%d", idx));
    }
    
    /**
     * Show the window by setting the view visibility
     */
    public void ShowTextureWindow(int idx)
    {
    	//parentlayout.addView(displayTexture);
    	displayTexture[idx].setVisibility(View.VISIBLE);        	
    	useTextureView[idx] = true;
    	LogVisibility(MiscUtils.stringFormat("ShowTextureWindow-%d", idx));
    }
    
    public boolean getUseTextureView(int idx)
    {
    	return useTextureView[idx];
    }
    
    public void setInvalidateOnSurfaceTextureUpdate(boolean enable)
    {
    	stMGR.setInvalidateOnSurfaceTextureUpdate(true);
    }
    
    private void createBackgroundWindow(int windowWidth, int windowHeight, int color, boolean haveExternalDisplays)
    {
    	// Bug 134825: Just create 32x32 window to save memory
    	windowHeight = 32;
    	windowWidth = 32;
    	
    	// For Devices with external display, put a surface always on background of external display
    	if(haveExternalDisplays)
    	{
    		Context context = (Context)streamCtl;
    		Log.i(TAG, "Creating background surface: color" + color );

    		//Relative Layout to handle multiple views (if necessary in future)
    		backgroundLayout = new RelativeLayout(context);

    		// Create and setup view (ImageView)
    		backgroundView = new ImageView(context);
    		backgroundView.setBackgroundColor(color);
    		backgroundView.setVisibility(View.VISIBLE);        	
    		
    		// Add in view to layout
    		RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(windowWidth, windowHeight);
    		backgroundLayout.addView(backgroundView, layoutParams);

    		//Setting WindowManager and Parameters with system overlay
    		// Pixel format set to RGB_888 to force backgroundView to be implemented as a HWC layer.  Using Translucent
    		// would force an alpha layer and then the view is implemented as a GLES layer.  In Mercury, then
    		// this layer is at the bottom, video layer is in the middle as a HWC layer, and top layer is
    		// Pinpoint UX which turns transparent.  Sandwiching of HWC between two GLES layers is not handled
    		// properly in OMAP5 and results in a black screen where the composited background and Pinpoint layers
    		// occlude the video.  To avoid this problem, forcing backgroundView to be a HWC layer for now.
    		if (backgroundWmParams == null)
    		{
    			backgroundWmParams = new WindowManager.LayoutParams(
    					windowWidth, 
    					windowHeight, 
    					WindowManager.LayoutParams.TYPE_PHONE,	//TYPE_INPUT_METHOD_DIALOG caused crash
    					(0 | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE), 
    					PixelFormat.RGB_888);
    			backgroundWmParams.gravity = Gravity.TOP | Gravity.LEFT; 
    			backgroundWmParams.x = 0;
    			backgroundWmParams.y = 0;
    		}

    		// Add layout to window
    		addViewToExternalDisplay(context, backgroundLayout, backgroundWmParams);

    		// Force invalidation
    		forceLayoutInvalidation(backgroundLayout);
    	}
    }
}
