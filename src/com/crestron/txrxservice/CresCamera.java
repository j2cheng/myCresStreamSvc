package com.crestron.txrxservice;

import java.io.IOException;
import android.os.SystemClock;

import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
//import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.util.Log;

public class CresCamera {
	static String TAG = "TxRx Camera";

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

    public static Camera getCamera(){
	    Camera lCamera = null;
	    for(int retry = 5; lCamera == null && retry > 0; retry--)
	    {
	        int cameraId = findCamera();
	        if(cameraId>=0){
	            try {
	                lCamera = Camera.open(cameraId); 
	            } catch (Exception e) {
	                Log.e(TAG, "fail to open camera");
	                e.printStackTrace();
	                lCamera = null;
	                SystemClock.sleep(1000);
	            }
	        }
	    }
        return lCamera;
    }
    
    public static void releaseCamera(Camera lCamera) {
		if (lCamera != null) {
			lCamera.release(); // release the camera for other applications
			lCamera = null;
		}
	}
}
