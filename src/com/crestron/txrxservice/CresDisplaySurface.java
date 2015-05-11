/**
 * 
 */
package com.crestron.txrxservice;

import com.crestron.txrxservice.CresStreamCtrl.DeviceMode;

import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.RelativeLayout;
import android.app.Service;
import android.graphics.PixelFormat;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.RelativeLayout;
import android.content.Context;

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
    private RelativeLayout parentlayout;
    private RelativeLayout.LayoutParams viewLayoutParams;
    private WindowManager wm;
    private WindowManager.LayoutParams  wmLayoutParams;
    SurfaceManager sMGR;
    String TAG = "CresDisplaySurface";

    public CresDisplaySurface(Service svc, int windowWidth, int windowHeight)
    {
        Log.i(TAG, "Creating surface: " + windowWidth + "x" + windowHeight );

    	//Relative Layout to handle multiple views
        parentlayout = new RelativeLayout(svc);
        
        //Instance for Surfaceholder for StreamIn/Preview
        sMGR = new SurfaceManager(svc);

        // Create the surface and set the width and height to the display width
        // and height
        // TODO: Add ability to create multiple surfaces at different width and heigh
        // One way to do this is to create AddSurface and RemoveSurface functions
        // Adjust z-order as well
        //for (int i = 0; i < numOfSurfaces; i++){
            displaySurface[0] = new SurfaceView(svc);
            viewLayoutParams = new RelativeLayout.LayoutParams(
                  windowWidth,
                  windowHeight);
            parentlayout.addView(displaySurface[0], viewLayoutParams);
            displaySurface[1] = new SurfaceView(svc);
            viewLayoutParams = new RelativeLayout.LayoutParams(
                  windowWidth,
                  windowHeight);
            parentlayout.addView(displaySurface[1], viewLayoutParams);
        //}
       
        //Setting WindowManager and Parameters with system overlay
        wmLayoutParams = new WindowManager.LayoutParams(windowWidth, windowHeight, WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY, 0, PixelFormat.TRANSLUCENT);
        wm = (WindowManager) svc.getSystemService(Context.WINDOW_SERVICE);
        wmLayoutParams.gravity = Gravity.TOP | Gravity.LEFT;
        wmLayoutParams.x = 0;
        wmLayoutParams.y = 0;
        //Adding Relative Layout to WindowManager
        wm.addView(parentlayout, wmLayoutParams); 
        
        // Force invalidation
        forceLayoutInvalidation();

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
        
        viewLayoutParams =new RelativeLayout.LayoutParams(width, height);
        displaySurface[idx].setLayoutParams(viewLayoutParams);
        
    	forceLayoutInvalidation();
    }

    public void UpdateCoordinates(int x, int y, int idx)
    {
        Log.i(TAG, "UpdateDimensions: " + x + "," + y );
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
    private void forceLayoutInvalidation() {
        parentlayout.bringToFront();
        parentlayout.invalidate();
        parentlayout.requestLayout();
    }
    
    
    /**
     * Return the surface holder for the display surface
     * TODO: Add an index so that the correct surface can be updated
     * @return Surface holder of the surface view
     */
    public SurfaceHolder GetSurfaceHolder(int idx)
    {
        return sMGR.getCresSurfaceHolder(displaySurface[idx]);
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
}
