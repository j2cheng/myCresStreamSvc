package com.crestron.txrxservice;

import java.io.IOException;
import android.os.SystemClock;
import android.os.Build;
import android.view.Surface;

import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Size;
import android.util.Log;

public class CresCamera {
	static String TAG = "TxRx Camera";
	public static Camera mCamera = null;
	static Object lockObj = new Object();

	private static int findCamera(){
		int cameraId = 0;
		int numOfCameras = Camera.getNumberOfCameras();
		for (int i = 0; i < numOfCameras; i++){
			CameraInfo info = new CameraInfo();
			Camera.getCameraInfo(i, info);
			cameraId = i;
		}
		return cameraId;
	}

    boolean findCamera(String CameraId){
        Log.i(TAG, " findCamera camera2 " + CameraId);
        return true;
    }

    public static void setPreviewSurface(Surface surface) throws java.io.IOException
    {
        //Do Nothing
        return;
    }

    public void startCamera() {
        //Do Nothing
        return;            
    }

    boolean cameraValid(){
        //Do Nothing
        return true;
    }

	public void openCamera(CresStreamCtrl streamCtrl){
		synchronized (lockObj)
		{
			if (mCamera != null)
				releaseCamera();

			for(int retry = 5; mCamera == null && retry > 0; retry--)
			{
				int cameraId = findCamera();
				if(cameraId>=0){
					try {
						mCamera = Camera.open(cameraId);            	
					} catch (Exception e) {
						Log.e(TAG, "fail to open camera " + cameraId);
						e.printStackTrace();
						mCamera = null;
						SystemClock.sleep(1000);
					}
				}
			}	
			if (mCamera == null)
				streamCtrl.RecoverMediaServer();
			return;
		  }
	}
	
	public void releaseCamera() {
		if (mCamera != null) {
			mCamera.release(); // release the camera for other applications
			mCamera = null;
		}
	}
	
	public Camera getCamera(){
            return mCamera;
	}

    boolean cameraPresent(){
        return true;
    }
}
