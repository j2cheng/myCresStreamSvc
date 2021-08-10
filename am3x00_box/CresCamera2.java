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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

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
    boolean mCamErrCur = false;

    private Semaphore mCameraOpenCloseLock = new Semaphore(1);
    static Object lockObj = new Object();
    CameraHandler mCameraHandler;
    final String hdmiCameraId = "/dev/video0";
    final String hdmiCameraName = "HDMI input camera";

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
            //Log.v(TAG, "findCamera: getCameraIdList length: " + cameraIds.length);

            for (String id:cameraIds) {
                if (CameraId.equals(id)) {
                    Log.v(TAG, "findCamera: HDMI Input camera is connected");
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
                releaseCamera2(true);
            else
                Log.i(TAG, " mCameraDevice is NULL");

            if(this.mCameraManager == null)
                Log.e(TAG, " mCameraManager is NULL");

            //FIXME: Add retry logic
            if(findCamera(hdmiCameraId))
            {
                final CountDownLatch cameraOpenLatch = new CountDownLatch(1);
                try {
                    mCameraManager.openCamera(hdmiCameraId, new CameraDevice.StateCallback() {
                        @Override
                        public void onOpened(CameraDevice camera) {
                            Log.i(TAG, "  onOpened " + hdmiCameraName);
                            mCamErrCur = false;
                            mCameraDevice = camera;
                            cameraOpenLatch.countDown();
                        }

                        @Override
                        public void onClosed(CameraDevice camera) {
                            Log.i(TAG, "  onClosed " + hdmiCameraName);
                        }

                        @Override
                        public void onDisconnected(CameraDevice camera) {
                            Log.i(TAG, "  onDisconnected " + hdmiCameraName);
                            if (mCameraDevice != null) {
                                releaseCamera2(true);
                            }
                        }

                        @Override
                        public void onError(CameraDevice camera, int error) {
                            Log.e(TAG, "  onError " + hdmiCameraName + " error " + error);
                            mCamErrCur = true;
                            if (mCameraDevice != null) {
                                releaseCamera2(false);//do not call abortCaptures when onError(bug AM3XX-5742)
                            }
                        }
                    }, mCameraHandler);
                    boolean openSuccess = true;
                    try {
                        openSuccess = cameraOpenLatch.await(2000, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException ex) { ex.printStackTrace(); }
                    if (!openSuccess || (mCameraDevice == null))
                    {
                        Log.e(TAG, "Unable to open camera even after 2 seconds");
                    }
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }
            else
                Log.e(TAG, "openCamera called but /dev/video0 not available" );
        }
    return;
    }

    @Override
    public void releaseCamera() {
        releaseCamera2(true);
    }

    public void releaseCamera2(boolean needAbort) {
        Log.i(TAG, " releaseCamera camera2 ");
        Log.i(TAG, "checkClosed " + hdmiCameraName);
        try {
            mCameraOpenCloseLock.acquire();
            if (findCamera(hdmiCameraId))
            {
                // Camera still exists - close device - according to documentation that should automatically close the session
                Log.i(TAG, hdmiCameraName + " still exists - try to close it");
                if (mCameraSession != null) {
                    try {
                        Log.i(TAG,  " abort captures " + hdmiCameraName + "needAbort: " + needAbort);
                        if(needAbort)
                            mCameraSession.abortCaptures();
                        Log.i(TAG,  " successfully aborted captures " + hdmiCameraName);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    } catch (IllegalStateException e) {
                        e.printStackTrace();
                    }
                    //              Log.i(TAG, " close " + hdmiCameraName + " mCameraSession");
                    //              mCameraSession.close();
                    //              Log.i(TAG, " successfully closed " + hdmiCameraName + "  mCameraSession");
                    //              mCameraSession = null;
                }
                if (mCameraDevice != null) {
                    Log.i(TAG, " close " + hdmiCameraName + " mCameraDevice");
                    mCameraDevice.close();
                    Log.i(TAG, " successfully closed " + hdmiCameraName + " mCameraDevice");
                    mCameraDevice = null;
                    mCameraSession = null;
                }
            } else {
                // camera no longer exists - nothing to close
                Log.i(TAG, hdmiCameraName + " no longer exists - bypass attempting to close it");
                mCameraDevice = null;
                mCameraSession = null;
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

    boolean cameraValid(){
        return ((mCameraDevice!=null));
    }

    public void startCamera() {
            if (mCameraDevice != null && mPreviewSurface != null) {

                Size[] sizes = null;
                try {
                    CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(hdmiCameraId);
                    StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    sizes = map.getOutputSizes(ImageFormat.JPEG);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
                
                if((sizes != null) && (sizes.length > 0))
                {
                	Log.i(TAG, "startCamera(): hRes=" + sizes[0].getWidth() +", vRes=" + sizes[0].getHeight());
                }
                else
                {
                	Log.i(TAG, "startCamera(): Resolution is not available.");
                	return;
                }
                
                Size size = sizes[0];

                List<Surface> outputSurfaces = new ArrayList<Surface>();
                outputSurfaces.add(mPreviewSurface);

                try {
                    CaptureRequest.Builder builder = mCameraDevice
                            .createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                    builder.addTarget(mPreviewSurface);
                    mPreViewRequest = builder.build();
                    Log.i(TAG, "  createCaptureSession " + hdmiCameraName);
                    mCameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(final CameraCaptureSession session) {
                            Log.i(TAG, "  createCaptureSession  onConfigured " + hdmiCameraName);
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
                            Log.i(TAG, hdmiCameraName + " createCaptureSession  onConfigureFailed " + session);
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
