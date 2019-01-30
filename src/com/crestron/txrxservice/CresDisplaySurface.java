/**
 * 
 */
package com.crestron.txrxservice;

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
public interface CresDisplaySurface 
{

    // Prepare the class for destruction
    public void close();
    
    // Take window lock for specific window
    public void lockWindow(int idx);
        
    // Release window lock for specific window
    public void unlockWindow(int idx);

    // Call when HDMI resolution changes are detected
    public void setWindowManagerResolution(final int w, final int h, final boolean haveExternalDisplay);

    // Just remove the parentLayout
    public void RemoveView();

    // Update the width and height of the surface
    public void updateWH(final int idx);
    
    // Update the X, Y location of the surface
    public void updateXY(final int idx);
   
	// invalidates parent layout
    public void forceParentLayoutInvalidation();
    
    // update window with a new window size for the video
    public void updateWindowWithVideoSize(int idx, boolean use_texture_view, int videoWidth, int videoHeight);

    // invokes parent layout invalidation in UI thread
    public void invalidateSurface();
    
    // update window for surface
    public void updateWindow(final int idx, final boolean use_texture_view);

    // update window for surface (this version is public because it is currently used by AirMedia)
    public void updateWindow(final int x, final int y, final int w, final int h, final int idx, final boolean use_texture_view);

    // get Surface for Window idx
    public Surface getSurface(int idx);
    
    // set Surface for Window idx
    public void setSurface(int idx, Surface s);
    
    // delete Surface for Window idx
    public void deleteSurface(int idx);
    
    // get stream id for surface
	public int surface2streamId(Surface surface);

    // set tag for Window idx
	public void setTag(int idx, String tag);

    /**
     * Return the surface holder for the display surface
     * @return Surface holder of the surface view
     */
    public SurfaceHolder GetSurfaceHolder(int idx);

    
    /**
     * Initialize surface holder for the display surface, create the surface for use
     */
    public void InitSurfaceHolder(int idx);
    
    /**
     * Return the surface view for the display surface
     * @return SurfaceView
     */
    public SurfaceView GetSurfaceView(int idx);
    
    /**
     * Initialize surface texture, set listener
     */
    public void InitSurfaceTextureListener(int idx);
	
    public TextureView GetTextureView(int idx);
    
    public SurfaceTexture GetSurfaceTexture(int idx);
    
    /**
     * Hide the window by setting the view visibility
     */
    public void HideWindow(int idx);
    
    /**
     * Show the window by setting the view visibility
     */
    public void ShowWindow(int idx);
    
	// Call this function with streams stopped
    public void updateZOrder(Integer[][] zOrder);
    
    /**
     * Hide the window by setting the view visibility
     */
    public void HideTextureWindow(int idx);
    
    /**
     * Show the window by setting the view visibility
     */
    public void ShowTextureWindow(int idx);
    
    public boolean getUseTextureView(int idx);
    
    public void setInvalidateOnSurfaceTextureUpdate(boolean enable);
}
