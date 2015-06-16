package com.crestron.txrxservice;

import java.io.IOException;

import android.util.Log;
import android.view.SurfaceHolder;

import android.content.Context;

import com.crestron.txrxservice.CresStreamCtrl.StreamState;
import org.freedesktop.gstreamer.GStreamer;

public class GstreamIn implements SurfaceHolder.Callback {

    String TAG = "TxRx GstreamIN";
    StringBuilder sb;
    boolean rtp_mode = false;
    boolean media_pause = false;
    boolean tcpInterleaveFlag = false;
    boolean disableLatencyFlag = false;
    private CresStreamCtrl streamCtl;
    private long statisticsNumVideoPackets = 0;
    private int statisticsNumVideoPacketsDropped = 0;
    private long statisticsNumAudioPackets = 0;
    private int statisticsNumAudioPacketsDropped = 0;
    private int statisticsBitrate = 0;


    private native void nativeInit();     // Initialize native code, build pipeline, etc
    private native void nativeFinalize(); // Destroy pipeline and shutdown native code
    private native void nativePlay(int sessionId);     // Set pipeline to PLAYING
    private native void nativePause(int sessionId);    // Set pipeline to PAUSED
    private native void nativeStop(int sessionId);    // Set pipeline to NULL
    private static native boolean nativeClassInit(); // Initialize native class: cache Method IDs for callbacks
    private native void nativeSurfaceInit(Object surface);
    private native void nativeSurfaceFinalize();
    private long native_custom_data;      // Native code will use this to keep private data

    private static native void 	nativeSetSeverUrl(String url, int sessionId);
    private native void 		nativeSetRtspPort(int port, int sessionId);
    private native void 		nativeSetTsPort(int port, int sessionId);
    private native void 		nativeSetRtpVideoPort(int port, int sessionId);
    private native void 		nativeSetRtpAudioPort(int port, int sessionId);
    private native void 		nativeSetSessionInitiation(int initMode, int sessionId);
    private native void 		nativeSetTransportMode(int transportMode, int sessionId);
    private native void 		nativeSetMulticastAddress(String multicastIp, int sessionId);
    private native void 		nativeSetStreamingBuffer(int buffer_ms, int sessionId);
    private native void 		nativeSetXYlocations(int xloc, int yloc, int sessionId);
    private static native void 	nativeSetStatistics(boolean enabled, int sessionId);
    private static native void 	nativeResetStatistics(int sessionId);

    public GstreamIn(CresStreamCtrl mContext) {
        Log.e(TAG, "GstreamIN :: Constructor called...!");
        streamCtl = mContext;
        // Initialize GStreamer and warn if it fails
        try {
            GStreamer.init((Context)mContext);
        } catch (Exception e) {
//             Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
//             finish();
            return;
        }
        nativeInit();
    }

    public void setSessionIndex(int id){
        //idx = id;
    }

    public static void setServerUrl(String url, int sessionId){
    	nativeSetSeverUrl(url, sessionId);	
	}
    
    public void setRtspPort(int port, int sessionId){
    	nativeSetRtspPort(port, 0);
    }
    
    public void setTsPort(int port, int sessionId){
    	nativeSetTsPort(port, 0);
    }
    
    public void setRtpVideoPort(int port, int sessionId){
    	nativeSetRtpVideoPort(port, 0);
    }
    
    public void setRtpAudioPort(int port, int sessionId){
    	nativeSetRtpAudioPort(port, 0);
    }
    
    public void setSessionInitiation(int initMode, int sessionId){
    	nativeSetSessionInitiation(initMode, sessionId);
    }
    
    public void setTransportMode(int transportMode, int sessionId){
    	nativeSetTransportMode(transportMode, sessionId);
    }
    
    public void setMulticastAddress(String multicastIp, int sessionId){
    	nativeSetMulticastAddress(multicastIp, sessionId);
    }
    
    public void setStreamingBuffer(int buffer_ms, int sessionId){
    	nativeSetStreamingBuffer(buffer_ms, sessionId);
    }
    
    public void sendStatistics(long video_packets_received, int video_packets_lost, long audio_packets_received, int audio_packets_lost, int bitrate){
    	statisticsNumVideoPackets = video_packets_received;
        statisticsNumVideoPacketsDropped = video_packets_lost;
        statisticsNumAudioPackets = audio_packets_received;
        statisticsNumAudioPacketsDropped = audio_packets_lost;
        statisticsBitrate = bitrate;
        
        streamCtl.SendStreamInFeedbacks();
    }
 
    public void updateCurrentXYloc(int xloc, int yloc, int sessId){
    	nativeSetXYlocations(xloc, yloc, sessId);
    }
    
    public static void setStatistics(boolean enabled, int sessId){
    	nativeSetStatistics(enabled, sessId);
    }
    
    public static void resetStatistics(int sessId){
    	nativeResetStatistics(sessId);
    }

    public int[] getCurrentWidthHeight(int sessionId){
        int[] widthHeight = new int[2];
        // width in index 0, height in index 1
    	widthHeight[0] = streamCtl.userSettings.getW(sessionId);
        widthHeight[1] = streamCtl.userSettings.getH(sessionId);
        return widthHeight;
    }

    public int getCurrentStreamState(int sessionId){
    	return (streamCtl.userSettings.getStreamState(sessionId)).getValue();
	}
    
    public void updateStreamStatus(int streamStateEnum, int sessionId){
    	streamCtl.SendStreamState(StreamState.getStreamStateFromInt(streamStateEnum), sessionId); 
	}
    
    public void sendMulticastAddress(String multicastAddress, int sessionId){
    	streamCtl.sendMulticastIpAddress(multicastAddress, sessionId);
	}
    
    public void sendVideoSourceParams(int source, int width, int height, int framerate, int profile){
    	streamCtl.SendStreamInVideoFeedbacks(source, width, height, framerate, profile); //TODO: see if profile needs to be converted
    }
    
    public long getStreamInNumVideoPackets() {
		return statisticsNumVideoPackets;
	}
	
	public int getStreamInNumVideoPacketsDropped() {
		return statisticsNumVideoPacketsDropped;
	}
	
	public long getStreamInNumAudioPackets() {
		return statisticsNumAudioPackets;
	}
	
	public int getStreamInNumAudioPacketsDropped() {
		return statisticsNumAudioPacketsDropped;
	}
	
	public int getStreamInBitrate() {
		return statisticsBitrate;
	}
	
	public void resetStatistics() {
		//TODO: JNI command to tell native code to reset statistics
//		statisticsNumVideoPackets = 0;
//        statisticsNumVideoPacketsDropped = 0;
//        statisticsNumAudioPackets = 0;
//        statisticsNumAudioPacketsDropped = 0;
//        statisticsBitrate = 0;
//        
//        streamCtl.SendStreamInFeedbacks();
	}
    
    //MJPEG IN  ??? Not Needed
    public void disableLatency(){
//        disableLatencyFlag = true;    
    }

    //Enable TCP for RTSP Mode
    public void setRtspTcpInterleave(boolean tcpInterleave){
//        Log.d(TAG, " setRtspTcpInterleave");
//        tcpInterleaveFlag = tcpInterleave;    
    }

    //RTP Only Mode, SDP Creation based on RTP Video and Audio Ports
    public void setRtpOnlyMode(int vport, int aport, String ip){
//        rtp_mode = true;
//        sb = new StringBuilder(4096);
//        Log.d(TAG, "vport "+vport+ "aport "+aport +"ip "+ip);
//        sb.append("v=0\r\n").append("o=- 15545345606659080561 15545345606659080561 IN IP4 cpu000669\r\n").append("s=Sample\r\n").append("i=N/A\r\n").append("c=IN IP4 ").append(ip).append("\r\n").append("t=0 0\r\n").append("a=range:npt=now-\r\n").append("m=audio ").append(Integer.toString(aport)).append(" RTP/AVP 96\r\n").append("a=control:audio\r\n").append("a=rtpmap:96 MP4A-LATM/44100/2\r\n").append("a=fmtp:96 profile-level-id=15; object=2; cpresent=0; config=400024203fc0\r\n").append("m=video ").append(Integer.toString(vport)).append(" RTP/AVP 97\r\n").append("a=control:video\r\n").append("a=rtpmap:97 H264/90000\r\n").append("a=fmtp:97 profile-level-id=64002A;in-band-parameter-sets=1;packetization-mode=1\r\n");
    }

    //Play based on based Pause/Actual Playback 
    public void onStart(int sessionId) {
    	try {
    		streamCtl.getCresSurfaceHolder(sessionId).addCallback(this); //needed?
    		updateNativeDataStruct(sessionId);
    		nativeSurfaceInit (streamCtl.getCresSurfaceHolder(sessionId).getSurface());
    		nativePlay(sessionId);
    	}
    	catch(Exception e){
        	// TODO: explore exception handling with better feedback of what went wrong to user
            streamCtl.SendStreamState(StreamState.STOPPED, sessionId);
            e.printStackTrace();        
        }        
    }

    //Pause
    public void onPause(int sessionId) {
        nativePause(sessionId);
    }

    public void onStop(int sessionId) {
        Log.d(TAG, "Stopping MediaPlayer");
//         if(mediaPlayer != null){
//             if(mediaPlayer.isPlaying()){
//                 mediaPlayer.stop();
//             }
//             mediaPlayer.release();
//             mediaPlayer = null;
//         }
        nativeSurfaceFinalize ();
        nativeStop(sessionId);
    }
    
    private void updateNativeDataStruct(int sessionId)
    {
    	setServerUrl(streamCtl.userSettings.getServerUrl(sessionId), sessionId);
    	setRtspPort(streamCtl.userSettings.getRtspPort(sessionId), sessionId);
    	setTsPort(streamCtl.userSettings.getTsPort(sessionId), sessionId);
    	setRtpVideoPort(streamCtl.userSettings.getRtpVideoPort(sessionId), sessionId);
    	setRtpAudioPort(streamCtl.userSettings.getRtpAudioPort(sessionId), sessionId);
    	setSessionInitiation(streamCtl.userSettings.getSessionInitiation(sessionId), sessionId);
    	setTransportMode(streamCtl.userSettings.getTransportMode(sessionId), sessionId);
    	setMulticastAddress(streamCtl.userSettings.getMulticastAddress(sessionId), sessionId);
    	setStreamingBuffer(streamCtl.userSettings.getStreamingBuffer(sessionId), sessionId);
    	nativeSetStatistics(streamCtl.userSettings.isStatisticsEnable(sessionId), sessionId);
    }

    //Response to CSIO Layer TODO: these can most likely be deleted handled in jni library
    public boolean getMediaPlayerStatus()
    {
    	return true;//TODO
    }

//    public int getMediaPlayerHorizontalResFb(){
//        return 0;//TODO
//    }
//
//    public int getMediaPlayerVerticalResFb(){
//    	return 0;//TODO
//    }
//
//    public int getMediaPlayerFpsFb(){
//        return 30;//TODO
//    }
//    public String getMediaPlayerAspectRatioFb(){
//        String aspect = MiscUtils.calculateAspectRatio(0, 0);//TODO
//        return aspect;
//    }
    public int getMediaPlayerAudioFormatFb(){
        return 1;//TODO
    }
    public int getMediaPlayerAudiochannelsFb(){
        return 2;//TODO
    }

    protected void onDestroy() {
        nativeFinalize();
//        super.onDestroy();
    }


    // Called from native code. This sets the content of the TextView from the UI thread.
    private void setMessage(final String message) {
//         final TextView tv = (TextView) this.findViewById(R.id.textview_message);
//         runOnUiThread (new Runnable() {
//           public void run() {
//             tv.setText(message);
//           }
//         });
    }

    // Called from native code. Native code calls this once it has created its pipeline and
    // the main loop is running, so it is ready to accept commands.
    private void onGStreamerInitialized () {
//         Log.i ("GStreamer", "Gst initialized. Restoring state, playing:" + is_playing_desired);
//         // Restore previous playing state
//         if (is_playing_desired) {
//             nativePlay();
//         } else {
//             nativePause();
//         }
    }




    static {
        System.loadLibrary("gstreamer_android");
        System.loadLibrary("gstreamer_jni");
        nativeClassInit();
    }

    //TODO: check if we can delete implements surface for class
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
            int height) {
        Log.d("GStreamer", "Surface changed to format " + format + " width "
                + width + " height " + height);
        nativeSurfaceInit (holder.getSurface());
    }

    public void surfaceCreated(SurfaceHolder holder) {
        Log.d("GStreamer", "Surface created: " + holder.getSurface());
        nativeSurfaceInit (holder.getSurface());
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.d("GStreamer", "Surface destroyed");
        nativeSurfaceFinalize ();
    }

}
