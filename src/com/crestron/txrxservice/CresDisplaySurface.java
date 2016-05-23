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
import android.widget.RelativeLayout;
import android.app.Service;
import android.graphics.PixelFormat;
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
    CresStreamCtrl streamCtl;
    private RelativeLayout parentlayout;
    private RelativeLayout.LayoutParams viewLayoutParams;
    private WindowManager wm;
    private WindowManager.LayoutParams  wmLayoutParams;
    SurfaceManager sMGR;
    String TAG = "CresDisplaySurface";

    // PEM - add a view to the 2nd display
    // Needed to pass Service or else can't call getApplicationContext...
    private void addViewToExternalDisplay(Service svc, View view, WindowManager.LayoutParams params){
        DisplayManager dm = (DisplayManager) svc.getApplicationContext().getSystemService(Context.DISPLAY_SERVICE);
        if (dm != null){
            Display dispArray[] = dm.getDisplays();
            if (dispArray.length>1){
				Log.e(TAG, "Crestron PEM adding view to 2nd display");
                Context displayContext = svc.getApplicationContext().createDisplayContext(dispArray[1]);
                WindowManager wm = (WindowManager)displayContext.getSystemService(Context.WINDOW_SERVICE);
                wm.addView(view, params);
            }
            else{
				Log.e(TAG, "Crestron PEM 2nd display not ready yet");
            }
        }
    }

    public CresDisplaySurface(Service svc, int windowWidth, int windowHeight, boolean haveExternalDisplays)
    {
        Log.i(TAG, "Creating surface: " + windowWidth + "x" + windowHeight );
        
        streamCtl = (CresStreamCtrl)svc;

    	//Relative Layout to handle multiple views
        parentlayout = new RelativeLayout(svc);
        
        //Instance for Surfaceholder for StreamIn/Preview
        sMGR = new SurfaceManager(svc);

        // Create the surface and set the width and height to the display width
        // and height
        // TODO: Add ability to create multiple surfaces at different width and height
        // One way to do this is to create AddSurface and RemoveSurface functions
        // Adjust z-order as well
        for (int i = 0; i < CresStreamCtrl.NumOfSurfaces; i++){
            displaySurface[i] = new SurfaceView(svc);
            viewLayoutParams = new RelativeLayout.LayoutParams(
                  windowWidth,
                  windowHeight);
            parentlayout.addView(displaySurface[i], viewLayoutParams);
        }

        //Setting WindowManager and Parameters with system overlay
        wmLayoutParams = new WindowManager.LayoutParams(windowWidth, windowHeight, WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY, 0, PixelFormat.TRANSLUCENT);
        wmLayoutParams.gravity = Gravity.TOP | Gravity.LEFT;
        wmLayoutParams.x = 0;
        wmLayoutParams.y = 0;
        //Adding Relative Layout to WindowManager
		if(haveExternalDisplays){		
			Log.d(TAG, "moving streams to 2nd display");
			addViewToExternalDisplay(svc, parentlayout, wmLayoutParams);
		}
		else{
			wm = (WindowManager) svc.getSystemService(Context.WINDOW_SERVICE);
			wm.addView(parentlayout, wmLayoutParams); 
		}
		
        // Force invalidation
        forceLayoutInvalidation();
        
        // Add callbacks to surfaceviews
        for (int sessId = 0; sessId < CresStreamCtrl.NumOfSurfaces; sessId++)
        {
        	InitSurfaceHolder(sessId);
        }
    }
    
    /**
     * Just remove the parentLayout
     */
    public void RemoveView()
    {
    	wm.removeView(parentlayout);    	
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
	        
	    	forceLayoutInvalidation();
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
	        
	    	forceLayoutInvalidation();
    }
	/**
	 * Force the invalidation of the layout
	 */
    public void forceLayoutInvalidation() {
        parentlayout.bringToFront();
        parentlayout.invalidate();
        parentlayout.requestLayout();
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
        forceLayoutInvalidation();
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
}
