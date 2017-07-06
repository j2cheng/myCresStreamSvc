/**
 * 
 */
package com.crestron.txrxservice;

import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.view.TextureView;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.app.Activity;
import android.app.Service;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Matrix;
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
public class CresDisplaySurface 
{
    private SurfaceView[] displaySurface = new SurfaceView[CresStreamCtrl.NumOfSurfaces];
    private TextureView[] displayTexture = new TextureView[CresStreamCtrl.NumOfTextures];
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
    
// Prepare the class for destruction
    public void close()
    {
        Log.i(TAG, "CresDisplaySurface::close()");
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
    
    public CresDisplaySurface(Service app, int windowWidth, int windowHeight, boolean haveExternalDisplays, int color)
    {
        Log.i(TAG, "Creating surface: " + windowWidth + "x" + windowHeight );
        
        streamCtl = (CresStreamCtrl)app;       

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
            viewLayoutParams = new RelativeLayout.LayoutParams(windowWidth, windowHeight);
            parentlayout.addView(displaySurface[i], viewLayoutParams);
        }

        for (int i = 0; i < CresStreamCtrl.NumOfTextures; i++){
        	displayTexture[i] = new TextureView(app);
        	displayTexture[i].setVisibility(View.INVISIBLE);
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
			Log.d(TAG, "moving streams to 2nd display");
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
    
    /**
     * Just remove the parentLayout
     */
    public void RemoveView()
    {
    	wm.removeView(parentlayout);    	
    }
    
    /**
     * Update the x, y, width, height of the surface
     */
    public void UpdateWindowSize(int x, int y, int width, int height, int idx, final boolean use_texture_view)
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
    
    /**
     * Update the width and height of the surface
     * TODO: Add an index so that the correct surface can be updated
     */
    public void UpdateDimensions(int width, int height, int idx)
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

    public void UpdateCoordinates(int x, int y, int idx)
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
    
	/**
	 * Force the invalidation of the layout
	 */
    public void forceParentLayoutInvalidation() {
        parentlayout.bringToFront();
        parentlayout.invalidate();
        parentlayout.requestLayout();
    }
    
	/**
	 * Force the invalidation of the layout
	 */
    public void forceLayoutInvalidation(RelativeLayout layout) {
    	layout.bringToFront();
    	layout.invalidate();
        layout.requestLayout();
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
     * Initialize surface texture, set listener
     */
    public void InitSurfaceTextureListener(int idx)
    {
        stMGR.initCresSurfaceTextureListener(displayTexture[idx]);
    }
	
    public TextureView GetAirMediaTextureView(int idx)
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
    	displaySurface[idx].setVisibility(View.INVISIBLE);
    }
    
    /**
     * Show the window by setting the view visibility
     */
    public void ShowWindow(int idx)
    {
    	displaySurface[idx].setVisibility(View.VISIBLE);
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
    
    public Integer[][] createZOrderArray()
    {
    	// Index 0 is the sessionId value, Index 1 is the relative Z order saved in userSettings
    	Integer[][] zOrder = new Integer[2][CresStreamCtrl.NumOfSurfaces];
    	
    	for (int sessionId = 0; sessionId < CresStreamCtrl.NumOfSurfaces; sessionId++)
    	{
    		zOrder[0][sessionId] = sessionId; 
    		zOrder[1][sessionId] = streamCtl.userSettings.getZ(sessionId);
    	}
    	
    	return zOrder;
    }
    
    // passing in Integer[][] acts like pass by reference, this is desired in this case
    public void sortZOrderArray(Integer[][] zOrder)
    {
    	//bubble ascending sort, we want zorders to be listed so that lower z order has lower index
        for (int outerIndex = 0; outerIndex < CresStreamCtrl.NumOfSurfaces; outerIndex++)
        {
            for (int innerIndex = (outerIndex + 1); innerIndex < CresStreamCtrl.NumOfSurfaces; innerIndex++)
            {
                if (zOrder[1][outerIndex] > zOrder[1][innerIndex])
                {
                    int[] temp = new int[2];
                    temp[0] = zOrder[0][outerIndex];
                    temp[1] = zOrder[1][outerIndex];
                    
                    //Swap innerIndex to outerIndex position
                    zOrder[0][outerIndex] = zOrder[0][innerIndex];
                    zOrder[1][outerIndex] = zOrder[1][innerIndex];
          
                    zOrder[0][innerIndex] = temp[0];
                    zOrder[1][innerIndex] = temp[1];
                }
            }
        }   
    }
    
    public boolean didZOrderChange(Integer[][] zOrderOld, Integer[][] zOrderNew)
    {
    	boolean updated = false;
    	for (int i = 0; i < CresStreamCtrl.NumOfSurfaces; i++)
        {
    		// Check that order of sessionIds match
    		if (zOrderOld[0][i] != zOrderNew[0][i])
    		{
    			updated = true;
    			break;
    		}
        }
    	
    	return updated;
    }
    
    public boolean doWindowsOverlap()
    {
    	// TODO: this needs to be updated when we have more than 2 windows
    	int surface1xLeft 	= streamCtl.userSettings.getXloc(0);
    	int surface1xRight 	= surface1xLeft + streamCtl.userSettings.getW(0);
    	int surface1yTop 	= streamCtl.userSettings.getYloc(0);
    	int surface1yBottom	= surface1yTop + streamCtl.userSettings.getH(0);
    	
    	int surface2xLeft 	= streamCtl.userSettings.getXloc(1);
    	int surface2xRight 	= surface2xLeft + streamCtl.userSettings.getW(1);
    	int surface2yTop 	= streamCtl.userSettings.getYloc(1);
    	int surface2yBottom	= surface2yTop + streamCtl.userSettings.getH(1);
    	
    	return MiscUtils.rectanglesOverlap(surface1xLeft, surface1xRight, surface1yTop, surface1yBottom, surface2xLeft, surface2xRight, surface2yTop, surface2yBottom);
    }
    
    /**
     * Hide the window by setting the view visibility
     */
    public void HideTextureWindow(int idx)
    {
    	//parentlayout.removeView(displayTexture);
    	displayTexture[idx].setVisibility(View.INVISIBLE);
    }
    
    /**
     * Show the window by setting the view visibility
     */
    public void ShowTextureWindow(int idx)
    {
    	//parentlayout.addView(displayTexture);
    	displayTexture[idx].setVisibility(View.VISIBLE);        	
    }
    
    public void createBackgroundWindow(int windowWidth, int windowHeight, int color, boolean haveExternalDisplays)
    {
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
