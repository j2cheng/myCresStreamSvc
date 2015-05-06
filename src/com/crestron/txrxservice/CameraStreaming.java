package com.crestron.txrxservice;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;

import com.crestron.txrxservice.CresStreamCtrl.StreamState;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.hardware.Camera.ErrorCallback;
import android.media.MediaRecorder;
import android.os.Environment;
import android.util.Log;
import android.view.SurfaceHolder;

public class CameraStreaming implements ErrorCallback {
    private SurfaceHolder surfaceHolder;
    static Camera mCameraPreviewObj = null;
    MediaRecorder mrec;
    String TAG = "TxRx CameraStreamer";
    String hostaddr;
    String filename;
    File file4Recording;
    boolean out_stream_status = false;
    CresStreamCtrl streamCtl;
    private int idx = 0;

    //public CameraStreaming(CresStreamCtrl mContext, SurfaceHolder lpHolder ) {
    public CameraStreaming(CresStreamCtrl mContext) {
        MiscUtils.getDeviceIpAddr();	
        hostaddr = MiscUtils.matcher.group();
        Log.d(TAG, "CameraStreaming :: Constructor called.....");
        //surfaceHolder = lpHolder;
        streamCtl = mContext;
    }
   
    public void setSessionIndex(int id){
        idx = id;
    }

    protected void startRecording() throws IOException {

        if(out_stream_status==true)
            stopRecording(false);
        Log.d(TAG, "startRecording");
        boolean isDirExists = true;
        File path = new File("/dev/shm/crestron/CresStreamSvc");
        if(!path.exists()){
            isDirExists = path.mkdir();
        }

        filename = "/rec001.mp4";
        Log.d(TAG, "CamTest: Camera Recording Filename: " + filename);

        // create empty file it must use
        file4Recording = new File(path, filename);
        if (file4Recording == null)
        {
            Log.d(TAG, "CamTest: file() returned null");
        }
        else
        {
            Log.d(TAG, "CamTest: file() didn't return null");
        }


        mrec = new MediaRecorder();
        mCameraPreviewObj = CresCamera.getCamera();
        if(mCameraPreviewObj!=null){
            mCameraPreviewObj.getHdmiInputStatus();
            mCameraPreviewObj.setEncoderFps(CresStreamConfigure.getVFrameRate(idx));
            mCameraPreviewObj.lock();
            mCameraPreviewObj.unlock();
            mrec.setCamera(mCameraPreviewObj);

            mrec.setAudioSource(MediaRecorder.AudioSource.MIC);
            mrec.setVideoSource(MediaRecorder.VideoSource.CAMERA);
            mrec.setStreamTransportMode(CresStreamConfigure.mode.getMode());
            //Set Port
            int l_port;
            switch(CresStreamConfigure.mode.getMode())
            {
                case 0://RTSP
                    {
                        l_port = CresStreamConfigure.getRTSPPort(idx);
                        mrec.setDestinationIP( hostaddr);
                        mrec.setRTSPPort(l_port);
                    }
                    break; 
                case 1://RTP
                    mrec.setDestinationIP(CresStreamConfigure.getUrl(idx));
                    l_port = CresStreamConfigure.getRTPAPort(idx);
                    mrec.setRTPAudioPort(l_port);
                    l_port = CresStreamConfigure.getRTPVPort(idx);
                    mrec.setRTPVideoPort(l_port);
                    break;
                case 2://TS_RTP
                case 3://TS_UDP
                    //TODO
                    mrec.setDestinationIP(CresStreamConfigure.getUrl(idx));
                    l_port = CresStreamConfigure.getTSPort(idx);
                    mrec.setMPEG2TSPort(l_port);
                    break;
                case 4://MJPEG
                    break;
                case 5://MCast RTSP
                    {
                        mrec.setDestinationIP(hostaddr);//Set Server IP as HostIP
                        l_port = CresStreamConfigure.getRTSPPort(idx);
                        mrec.setMcastIP(CresStreamConfigure.getMulticastIP(idx));
                        mrec.setRTSPPort(l_port);
                    }
                    break;
                default:
                    break; 
            }
            mrec.setOutputFormat(9);//Streamout option set to Stagefright Recorder
            if (CresStreamConfigure.mode.getMode() == 5)
            	Log.d(TAG, "ip addr "+CresStreamConfigure.getMulticastIP(idx));
            else
            	Log.d(TAG, "ip addr "+CresStreamConfigure.getDeviceIP());
            Log.d(TAG, "setting width: "+CresStreamConfigure.getWidth(idx) );
            Log.d(TAG, "setting height: "+CresStreamConfigure.getHeight(idx));
            Log.d(TAG, "setting profile: "+CresStreamConfigure.vprofile.getVEncProfile());
            Log.d(TAG, "setting video encoder level: "+CresStreamConfigure.getVEncLevel(idx));
            Log.d(TAG, "setting video frame rate: "+CresStreamConfigure.getVFrameRate(idx));
            if((CresStreamConfigure.getWidth(idx)!=0) || (CresStreamConfigure.getHeight(idx)!=0))
                mrec.setVideoSize(CresStreamConfigure.getWidth(idx),CresStreamConfigure.getHeight(idx));
            mrec.setVideoEncodingBitRate(CresStreamConfigure.getVideoBitRate(idx));
            //mrec.setVideoFrameRate(CresStreamConfigure.getVFrameRate());//Mistral Propietary API 
            mrec.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mrec.setEncoderProfile(CresStreamConfigure.vprofile.getVEncProfile());
            mrec.setVideoEncoderLevel(CresStreamConfigure.getVEncLevel(idx));
            mrec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mrec.setOutputFile(path + filename);   

            Log.d(TAG, "########setPreviewDisplay######");
            mrec.setPreviewDisplay(streamCtl.getCresSurfaceHolder().getSurface());
            //mrec.setPreviewDisplay(surfaceHolder.getSurface());

            mrec.prepare();
            mrec.start();
            if(CresStreamConfigure.mode.getMode()==1){
                String sb = mrec.getSDP();
                Log.d(TAG, "########SDP Dump######\n" + sb);
            }
            //mrec.getStatisticsData();
            streamCtl.SendStreamState(StreamState.STARTED);
            streamCtl.SendStreamOutFeedbacks();
            out_stream_status = true;
        }
        else {
            Log.e(TAG, "Camera Resource busy or not available !!!!");
            file4Recording.delete();
            mrec = null;
        }
    }

    public String updateSvcWithStreamStatistics(){
            return mrec.getStatisticsData();
    }

    public boolean isStreaming(){
        return out_stream_status;
    }

    private void releaseMediaRecorder() {
        if (mrec != null) {
            mrec.reset(); // clear recorder configuration
            mrec.release(); // release the recorder object
            mrec = null;
        }
    }

    public void stopRecording(boolean hpdEventAction) {
        Log.d(TAG, "stopRecording");
        if (out_stream_status && (mrec != null)) {
            if(hpdEventAction==true){
                CresCamera.releaseCamera(mCameraPreviewObj);
                mCameraPreviewObj = null;
            }
            mrec.stop();
            //mrec.setPreviewDisplay(null);
            out_stream_status = false;
            releaseMediaRecorder();
            if(hpdEventAction==false){
                CresCamera.releaseCamera(mCameraPreviewObj);
                mCameraPreviewObj = null;
            }
            streamCtl.SendStreamState(StreamState.STOPPED);
        }
    }

    @Override
        public void onError(int error, Camera camera) {
            Log.d(TAG, "Camera Error callback:" + error + "Camera :" + camera);
        }

    //Response to CSIO Layer
    public boolean getStreamOutStatus()
    {
        //TODO
        return true; 
    }

    public int getStreamOutHorizontalResFb(){
        return CresStreamConfigure.getWidth(idx);
    }

    public int getStreamOutVerticalResFb(){
        return CresStreamConfigure.getHeight(idx);
    }

    public int getStreamOutFpsFb(){
        return CresStreamConfigure.getVFrameRate(idx);
    }

    public String getStreamOutAspectRatioFb(){
        String aspect = MiscUtils.calculateAspectRatio(CresStreamConfigure.getWidth(idx),CresStreamConfigure.getHeight(idx));
        return aspect;
    }

    public int getStreamOutAudioFormatFb(){
        return 1;//Always
    }

    public int getStreamOutAudiochannelsFb(){
        return 2;//For Now only 2 Audio Channels
    }
}
