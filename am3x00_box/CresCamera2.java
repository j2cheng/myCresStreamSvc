package com.crestron.txrxservice;

import android.os.Build;
import android.util.Log;
import android.view.Surface;
import android.util.Size;

import java.util.ArrayList;
import java.util.List;
import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import java.util.concurrent.Semaphore;
import android.hardware.Camera;

public class CresCamera2 extends CresCamera
{
    static String TAG = "TxRx Camera2";

    CameraManager mCameraManager;
    static CameraDevice mCameraDevice;
    HandlerThread mCameraThread;
    static CameraCaptureSession mCameraSession;
    static Surface mPreviewSurface;
    CaptureRequest mPreViewRequest;

    private Semaphore mCameraOpenCloseLock = new Semaphore(1);
    static Object lockObj = new Object();
    CameraHandler mCameraHandler;

    final CameraManager.AvailabilityCallback mCallback = new CameraManager.AvailabilityCallback() {
        @Override
        public void onCameraAvailable(String cameraId) {
            Log.i(TAG, " onCameraAvailable " + cameraId);
        }

        @Override
        public void onCameraUnavailable(String cameraId) {
            Log.i(TAG, " onCameraUnavailable " + cameraId);
        }
    };

    class CameraHandler extends Handler {

        public CameraHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
        }
    }

    CresCamera2()
    {
        mCameraThread = new HandlerThread("CameraThreadP");
        mCameraThread.start();

        //mCameraThread.getLooper().prepare();

        mCameraHandler = new CameraHandler(mCameraThread.getLooper());
        if(mCameraManager != null)
            mCameraManager.registerAvailabilityCallback(mCallback, null);
    }

    boolean findCamera(String CameraId){
        Log.i(TAG, " findCamera camera2 " + CameraId);
        //final String HDMIIP_ID = "/dev/video0";
        try {
            String[] cameraIds = mCameraManager.getCameraIdList();
            for (String id:cameraIds) {
                if (CameraId.equals(id)) {
                    Log.v(TAG, "HDMI Input camera is connected");
                    return true;
                }
            
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        return false;
    }

    public void openCamera(CresStreamCtrl streamCtrl){
        Log.i(TAG, " openCamera camera2 ");
        synchronized (lockObj)
        {
            if(mCameraDevice != null)
                releaseCamera();
            else
                Log.i(TAG, " mCameraDevice is NULL");

            if(this.mCameraManager == null)
                Log.i(TAG, " mCameraManager is NULL");

            //FIXME: Add retry logic
            try {
                mCameraManager.openCamera("/dev/video0", new CameraDevice.StateCallback() {
                    @Override
                    public void onOpened(CameraDevice camera) {
                        Log.i(TAG, "  onOpened " + "/dev/video0");
                        mCameraDevice = camera;
                    }

                    @Override
                    public void onDisconnected(CameraDevice camera) {
                        Log.i(TAG, "onDisconnected" + "/dev/video0");
                        if (mCameraDevice != null) {
                            releaseCamera();
                        }
                    }

                    @Override
                    public void onError(CameraDevice camera, int error) {
                        Log.i(TAG, "onError " + "/dev/video0" + " error " + error);
                        releaseCamera();
                    }
                }, mCameraHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    return;
    }

    public void releaseCamera() {
        Log.i(TAG, " releaseCamera camera2 ");

            Log.i(TAG, "checkClosed " + "/dev/video0");
            try {
                mCameraOpenCloseLock.acquire();
                if (mCameraSession != null) {
                    //                try {
                    //                    Log.i(TAG,  " abortCaptures  mCameraSession " + mCameraId);
                    //                    mCameraSession.abortCaptures();
                    //                    Log.i(TAG,  " abortCaptures  mCameraSession ok " + mCameraId);
                    //                } catch (CameraAccessException e) {
                    //                    e.printStackTrace();
                    //                }
                    Log.i(TAG, " close  mCameraSession " + "/dev/video0");
                    mCameraSession.close();
                    Log.i(TAG, " close  mCameraSession ok " + "/dev/video0");
                    mCameraSession = null;
                }
                if (mCameraDevice != null) {
                    Log.i(TAG, " close  mCameraDevice " + "/dev/video0");
                    mCameraDevice.close();
                    Log.i(TAG, " close  mCameraDevice ok " + "/dev/video0");
                    mCameraDevice = null;
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                mCameraOpenCloseLock.release();
            }
    return;
    }

    boolean cameraPresent(){
        Log.i(TAG, " cameraPresent camera2 ");
        return true;
    }

    public static void setPreviewSurface(Surface surface) throws java.io.IOException
    {
        mPreviewSurface = surface;
    }

    public Camera getCamera(){
            return null;
    }


    public void startCamera() {
            if (mCameraDevice != null && mPreviewSurface != null) {

                Size[] sizes = null;
                try {
                    CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics("/dev/video0");
                    StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    sizes = map.getOutputSizes(ImageFormat.JPEG);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
                Size size = sizes != null ? sizes[0] : new Size(1920, 1080);

                List<Surface> outputSurfaces = new ArrayList<Surface>();
                outputSurfaces.add(mPreviewSurface);

                try {
                    CaptureRequest.Builder builder = mCameraDevice
                            .createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                    builder.addTarget(mPreviewSurface);
                    mPreViewRequest = builder.build();
                    Log.i(TAG, "  createCaptureSession " + "/dev/video0");
                    mCameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(final CameraCaptureSession session) {
                            Log.i(TAG, "  createCaptureSession  onConfigured " + "/dev/video0");
                            mCameraSession = session;
                            try {
                                mCameraSession.setRepeatingRequest(mPreViewRequest, new CameraCaptureSession.CaptureCallback() {

                                }, mCameraHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {
                            Log.i(TAG, "/dev/video0" + " createCaptureSession  onConfigureFailed " + session);
                        }
                    }, mCameraHandler);

                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }
            else
                Log.e(TAG, " mPreviewSurface or mCameraDevice is NULL ");
        }
}
