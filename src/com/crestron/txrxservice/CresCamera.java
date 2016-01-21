package com.crestron.txrxservice;

import java.io.IOException;
import android.os.SystemClock;

import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Size;
import android.util.Log;

import com.crestron.txrxservice.ProductSpecific;

public class CresCamera {
	static String TAG = "TxRx Camera";
	public static Camera mCamera = null;
	static Object lockObj = new Object();
	public static boolean mSetHdmiInputStatus = false;

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

	public static void openCamera(){
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
						Log.e(TAG, "fail to open camera");
						e.printStackTrace();
						mCamera = null;
						SystemClock.sleep(1000);
					}
				}
			}
			
			// Always set Ducati up with resolution info
//			if (mSetHdmiInputStatus)
				getHdmiInputStatus();
			
			return;
		}
	}
	
	public static void getHdmiInputStatus()
	{
		if (mCamera != null)
		{
			ProductSpecific.getHdmiInputStatus(mCamera, HDMIInputInterface.getResolutionEnum());
			mSetHdmiInputStatus = false; 
		}
		else // If we can't set HDMI status now, lets do it later
			mSetHdmiInputStatus = true;
	}

	public static void releaseCamera() {
		if (mCamera != null) {
			mCamera.release(); // release the camera for other applications
			mCamera = null;
		}
	}
}
