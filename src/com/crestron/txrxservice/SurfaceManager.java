
package com.crestron.txrxservice;

import java.io.IOException;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.content.Context;

public class SurfaceManager implements SurfaceHolder.Callback {

    private SurfaceHolder crestSurfaceHolder;
    String TAG = "TxRx SurfaceMgr"; 

    public SurfaceManager(Context mContext){
        Log.e(TAG, "SurfaceManager:: Constructor called...!");
    }
    
    public SurfaceHolder getCresSurfaceHolder (SurfaceView view) {
        if (view != null) {
            Log.d(TAG, "View is not null");
            crestSurfaceHolder = view.getHolder();	
            crestSurfaceHolder.addCallback(this);
            crestSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
            view.setZOrderOnTop(true);

        } else {
            Log.d(TAG, "App passed null surface view for stream in");
        }
        return crestSurfaceHolder;
    }
    
    @Override
        public void surfaceChanged(SurfaceHolder arg0, int arg1, int arg2, int arg3) {
            Log.d(TAG, "######### surfacechanged##############");
            // TODO Auto-generated method stub
        }

    @Override
        public void surfaceCreated(SurfaceHolder arg0) {
            Log.d(TAG, "######### surfaceCreated##############");
        }

    @Override
        public void surfaceDestroyed(SurfaceHolder arg0) {
            // TODO Auto-generated method stub
        }
}
