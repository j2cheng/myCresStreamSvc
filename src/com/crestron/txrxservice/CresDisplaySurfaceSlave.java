/**
 * 
 */
package com.crestron.txrxservice;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
public class CresDisplaySurfaceSlave implements CresDisplaySurface 
{
    CresStreamCtrl streamCtl;
    
    String TAG = "CresDisplaySurfaceSlave";
    private Map<Integer, Surface> surfaceMap = new ConcurrentHashMap<Integer, Surface>();

// Prepare the class for destruction
    public void close()
    {
        Log.i(TAG, "close()");
    }
    
    // Take window lock for specific window
    public void lockWindow(int idx)
    {
    }
    
    // Release window lock for specific window
    public void unlockWindow(int idx)
    {
    }
    
    // Class constructor
    public CresDisplaySurfaceSlave(Service app)
    {
        Log.i(TAG, "CresDisplaySurfaceSlave" );        
        streamCtl = (CresStreamCtrl)app;       
    }
    
    // Call when HDMI resolution changes are detected
    public void setWindowManagerResolution(final int w, final int h, final boolean haveExternalDisplay)
    {
        Log.v(TAG, "setWindowManagerResolution - noop in slave mode" );        
    }

    // Just remove the parentLayout
    public void RemoveView()
    {
        Log.v(TAG, "RemoveView - noop in slave mode" );        
    }

     // Update the width and height of the surface
    public void updateWH(final int idx)
    {
    	Log.v(TAG, "UpdateWH: idx=" + idx + "  noop in slave mode" );
    }

    // Update the X, Y location of the surface
    public void updateXY(final int idx)
    {
    	Log.v(TAG, "UpdateXY: idx=" + idx + "  noop in slave mode" );
    }
    
    // update window with a new window size for the video
    public void updateWindowWithVideoSize(int idx, boolean use_texture_view, int videoWidth, int videoHeight)
    {
    	Log.v(TAG, "updateWindowWithVideoSize: idx=" + idx + "  noop in slave mode" );
    }
    
    // update window for surface
    public void updateWindow(final int idx, final boolean use_texture_view)
    {
    	Log.v(TAG, "updateWindow: idx=" + idx + "  noop in slave mode" );
    }
    
    // update window for surface (this version is public because it is currently used by AirMedia)
    public void updateWindow(final int x, final int y, final int w, final int h, final int idx, final boolean use_texture_view)
    {
    	Log.v(TAG, "updateWindow with x,y,w,h: idx=" + idx + "  noop in slave mode" );
    }
    
	// invalidates parent layout
    public void forceParentLayoutInvalidation() {
    	Log.v(TAG, "forceParentLayoutInvalidation - noop in slave mode" );
    }

    // invokes parent layout invalidation in UI thread
    public void invalidateSurface() {
    	Log.v(TAG, "invalidateSurface - noop in slave mode" );
    }
    
    // get Surface for Window idx
    public Surface getSurface(int idx)
    {
    	return surfaceMap.get(idx);
    }
    
    // set Surface for Window idx
    public void setSurface(int idx, Surface s)
    {
		surfaceMap.put(idx, s);
    }
    
    // delete Surface for Window idx
    public void deleteSurface(int idx)
    {
		surfaceMap.remove(idx);
    }
    
	public void setTag(int idx, String tag)
	{
		return;
	}

    /**
     * Return the surface holder for the display surface
     * @return Surface holder of the surface view
     */
    public SurfaceHolder GetSurfaceHolder(int idx)
    {
    	return null;
    }
    
    /**
     * Initialize surface holder for the display surface, create the surface for use
     */
    public void InitSurfaceHolder(int idx)
    {
    }
    
    /**
     * Return the surface view for the display surface
     * @return SurfaceView
     */
    public SurfaceView GetSurfaceView(int idx)
    {
		Log.i(TAG, "GetSurfaceView(): returning null because service is in slave mode");
    	return null;
    }
    
    /**
     * Initialize surface texture, set listener
     */
    public void InitSurfaceTextureListener(int idx)
    {
    }
	
    public TextureView GetTextureView(int idx)
    {
		Log.i(TAG, "GetTextureView(): returning null because service is in slave mode");
        return null;
    }
    
    public SurfaceTexture GetSurfaceTexture(int idx)
    {
		Log.i(TAG, "GetSurfaceTexture(): returning null because service is in slave mode");
    	return null;
    }
    
    /**
     * Hide the window by setting the view visibility
     */
    public void HideWindow(int idx)
    {
    }
    
    /**
     * Show the window by setting the view visibility
     */
    public void ShowWindow(int idx)
    {
    }
    
	// Call this function with streams stopped
    public void updateZOrder(Integer[][] zOrder)
    {
    }
    
    /**
     * Hide the window by setting the view visibility
     */
    public void HideTextureWindow(int idx)
    {
    }
    
    /**
     * Show the window by setting the view visibility
     */
    public void ShowTextureWindow(int idx)
    {
    }
    
    public boolean getUseTextureView(int idx)
    {
    	return false;
    }
    
    public void setInvalidateOnSurfaceTextureUpdate(boolean enable)
    {
    }
}
