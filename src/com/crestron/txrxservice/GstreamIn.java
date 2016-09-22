package com.crestron.txrxservice;

import java.io.IOException;

import android.util.Log;
import android.view.SurfaceHolder;
import android.content.Context;

import com.crestron.txrxservice.CresStreamCtrl.CrestronHwPlatform;
import com.crestron.txrxservice.CresStreamCtrl.StreamState;

public class GstreamIn implements StreamInStrategy, SurfaceHolder.Callback {

    String TAG = "TxRx GstreamIN";
    StringBuilder sb;
    boolean rtp_mode = false;
    boolean media_pause = false;
    int     tcpInterleaveFlag = 0;
    boolean disableLatencyFlag = false;
    private CresStreamCtrl streamCtl;
    private long statisticsNumVideoPackets = 0;
    private int statisticsNumVideoPacketsDropped = 0;
    private long statisticsNumAudioPackets = 0;
    private int statisticsNumAudioPacketsDropped = 0;
    private int statisticsBitrate = 0;
    private final int stopTimeout_sec = 30;
    private boolean isPlaying = false;
    private final static String ducatiRecoverFilePath = "/dev/shm/crestron/CresStreamSvc/ducatiRecoverTime";
    private final static long ducatiRecoverTimeDelta = (30 * 1000); //30 seconds


    private native void nativeInit();     // Initialize native code, build pipeline, etc
    private native void nativeFinalize(); // Destroy pipeline and shutdown native code
    private native void nativePlay(int sessionId);     // Set pipeline to PLAYING
    private native void nativePause(int sessionId);    // Set pipeline to PAUSED
    private native void nativeStop(int sessionId);    // Set pipeline to NULL
    private static native boolean nativeClassInit(); // Initialize native class: cache Method IDs for callbacks
    private native void nativeSurfaceInit(Object surface, int sessionId);
    private native void nativeSurfaceFinalize(int sessionId);
    private long native_custom_data;      // Native code will use this to keep private data

    private static native void 	nativeSetServerUrl(String url, int sessionId);
    private native void 		nativeSetRtspPort(int port, int sessionId);
    private native void 		nativeSetTsPort(int port, int sessionId);
    private native void 		nativeSetHdcpEncrypt(boolean flag, int sessionId);
    private native void 		nativeSetRtpVideoPort(int port, int sessionId);
    private native void 		nativeSetRtpAudioPort(int port, int sessionId);
    private native void 		nativeSetSessionInitiation(int initMode, int sessionId);
    private native void 		nativeSetTransportMode(int transportMode, int sessionId);
    private native void 		nativeSetMulticastAddress(String multicastIp, int sessionId);
    private native void 		nativeSetStreamingBuffer(int buffer_ms, int sessionId);
    private native void 		nativeSetXYlocations(int xloc, int yloc, int sessionId);
    private static native void 	nativeSetStatistics(boolean enabled, int sessionId);
    private static native void 	nativeResetStatistics(int sessionId);
    private native void 		nativeSetNewSink(boolean enabled, int sessionId);
    private native void			nativeSetUserName(String userName, int sessionId);
    private native void			nativeSetPassword(String password, int sessionId);
    public native void 			nativeSetVolume(int volume, int sessionid);
    public native void			nativeSetStopTimeout(int stopTimeout_sec);
    private native void         nativeSetFieldDebugJni(String cmd,int sessId);
    private native void			nativeDropAudio(boolean enabled, boolean dropAudioPipeline, int sessionId);
    private native void			nativeSetLogLevel(int logLevel);
    private native void			nativeSetTcpMode(int tcpMode, int sessionId);
    
    private native void			nativeInitUnixSocketState();
    
    public GstreamIn(CresStreamCtrl mContext) {
        Log.e(TAG, "GstreamIN :: Constructor called...!");
        streamCtl = mContext;
        nativeSetStopTimeout(stopTimeout_sec);
        nativeInit();
    }
    
    public void initUnixSocketState()
    {
    	nativeInitUnixSocketState();
    }

    public static void setServerUrl(String url, int sessionId){
    	nativeSetServerUrl(url, sessionId);	
	}
    
    public void setRtspPort(int port, int sessionId){
    	nativeSetRtspPort(port, sessionId);
    }
    
    public void setTsPort(int port, int sessionId){
    	nativeSetTsPort(port, sessionId);
    }
    
    public void setHdcpEncrypt(boolean flag, int sessionId) {
    	nativeSetHdcpEncrypt(flag, sessionId);
    }
    
    public void setRtpVideoPort(int port, int sessionId){
    	nativeSetRtpVideoPort(port, sessionId);
    }
    
    public void setRtpAudioPort(int port, int sessionId){
    	nativeSetRtpAudioPort(port, sessionId);
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
    
    public void setUserName(String userName, int sessionId){
    	nativeSetUserName(userName, sessionId);
    }
    
    public void setPassword(String password, int sessionId){
    	nativeSetPassword(password, sessionId);
    }
    
    public void setVolume(int volume, int sessionId){
    	nativeSetVolume(volume, sessionId);
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

    public void setNewSink(boolean enabled, int sessId){
    	nativeSetNewSink(enabled, sessId);
    }
    public void setFieldDebugJni(String cmd, int sessionId){
    	nativeSetFieldDebugJni(cmd, sessionId);
    }
    
    public void setAudioDrop(boolean enabled, int sessionId) {
    	boolean dropAudioPipeline = true; 	// do not dynamically drop audio through valve because that cause obtainBuffer timeout 
											// we will not add audio pipeline instead
    	nativeDropAudio(enabled, dropAudioPipeline, sessionId);
    }
    
    public void setLogLevel(int logLevel) {
    	nativeSetLogLevel(logLevel);
    }

    public int[] getCurrentWidthHeight(int sessionId){
        int[] widthHeight = new int[2];
        // width in index 0, height in index 1
    	widthHeight[0] = streamCtl.userSettings.getW(sessionId);
        widthHeight[1] = streamCtl.userSettings.getH(sessionId);
        return widthHeight;
    }
    
    public int getCurrentStreamState(int sessionId){
    	return (streamCtl.getCurrentStreamState(sessionId)).getValue();
	}
    
    public void updateStreamStatus(int streamStateEnum, int sessionId){
    	// Send stream url again on start fb
    	if (streamStateEnum == CresStreamCtrl.StreamState.STARTED.getValue())
    	{
    		streamCtl.sockTask.SendDataToAllClients(String.format("STREAMURL%d=%s", sessionId, streamCtl.userSettings.getStreamInUrl(sessionId)));
    	}
    	streamCtl.SendStreamState(StreamState.getStreamStateFromInt(streamStateEnum), sessionId); 
	}
    
    public void sendInitiatorFbAddress(String initiatorFbAddress, int sessionId){
    	streamCtl.userSettings.setInitiatorAddress(initiatorFbAddress);
    	streamCtl.sendInitiatorFbAddress(initiatorFbAddress, sessionId);
    }

    public void recoverDucati(){
    	// This recovery is only applicable for Ducati in OMAP5
    	if (streamCtl.mHwPlatform == CrestronHwPlatform.eHardwarePlatform_OMAP5)
    	{
//    		// Increment gstreamer timeout counter
//    		MiscUtils.writeStringToDisk(CresStreamCtrl.gstreamerTimeoutCountFilePath, String.valueOf(++(streamCtl.mGstreamerTimeoutCount)));
//
//    		long previousRecoverTime = 0;
//    		try {
//    			previousRecoverTime = Long.parseLong(MiscUtils.readStringFromDisk(ducatiRecoverFilePath));
//    		} catch (Exception e) {}    	
//    		long currentTime = MiscUtils.getSystemUptimeMs();
//
//    		// Write current time as most recent ducati recover time
//    		MiscUtils.writeStringToDisk(ducatiRecoverFilePath, String.valueOf(currentTime));
//
//    		// If recovery occurred within of previous recovery, give 10 seconds to let system recover
//    		if ((previousRecoverTime != 0) && (Math.abs(currentTime - previousRecoverTime) <= ducatiRecoverTimeDelta))
//    		{
//    			Log.i(TAG, "Giving system extra 10 seconds to recover");
//    			try {
//    				Thread.sleep(10000);
//    			} catch (Exception e) { e.printStackTrace(); }
//    		}
//
//    		// Delete all surfaces so we dont have a gralloc memory leak
//    		for(int sessionId = 0; sessionId < CresStreamCtrl.NumOfSurfaces; sessionId++)
//    		{
//    			streamCtl.hideStreamInWindow(sessionId);
//    		}
//
//    		streamCtl.RecoverDucati();
//    		try {
//    			Thread.sleep(5000);
//    		} catch (Exception e) { e.printStackTrace(); }
//    		streamCtl.RecoverTxrxService();
    		streamCtl.RecoverMediaServer();
    	}
	}
    
    public void sendMulticastAddress(String multicastAddress, int sessionId){
    	streamCtl.userSettings.setMulticastAddress(multicastAddress, sessionId);
    	streamCtl.sendMulticastIpAddress(multicastAddress, sessionId);
	}
    
    public void sendVideoSourceParams(int source, int width, int height, int framerate, int profile){
    	streamCtl.userSettings.setStreamInHorizontalResolution(width);
    	streamCtl.userSettings.setStreamInVerticalResolution(height);
    	streamCtl.userSettings.setStreamInFPS(framerate);
    	streamCtl.userSettings.setStreamInAspectRatio(Integer.parseInt(MiscUtils.calculateAspectRatio(width, height)));
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

	//Not needed by Gstreamer, only gallery player
    public void disableLatency(int sessionId){
//        disableLatencyFlag = true;    
    }

    //needed by Gstreamer
    public void setRtspTcpInterleave(int tcpInterleave, int sessionId){    	
    	nativeSetTcpMode(tcpInterleave,sessionId);
        tcpInterleaveFlag = tcpInterleave;    
    }

  //Not needed by Gstreamer, only gallery player
    public void setRtpOnlyMode(int vport, int aport, String ip, int sessionId){
//        rtp_mode = true;
//        sb = new StringBuilder(4096);
//        Log.d(TAG, "vport "+vport+ "aport "+aport +"ip "+ip);
//        sb.append("v=0\r\n").append("o=- 15545345606659080561 15545345606659080561 IN IP4 cpu000669\r\n").append("s=Sample\r\n").append("i=N/A\r\n").append("c=IN IP4 ").append(ip).append("\r\n").append("t=0 0\r\n").append("a=range:npt=now-\r\n").append("m=audio ").append(Integer.toString(aport)).append(" RTP/AVP 96\r\n").append("a=control:audio\r\n").append("a=rtpmap:96 MP4A-LATM/44100/2\r\n").append("a=fmtp:96 profile-level-id=15; object=2; cpresent=0; config=400024203fc0\r\n").append("m=video ").append(Integer.toString(vport)).append(" RTP/AVP 97\r\n").append("a=control:video\r\n").append("a=rtpmap:97 H264/90000\r\n").append("a=fmtp:97 profile-level-id=64002A;in-band-parameter-sets=1;packetization-mode=1\r\n");
    }

    //Play based on based Pause/Actual Playback 
    public void onStart(int sessionId) {
    	try {
    		isPlaying = true;
			SurfaceHolder sh = streamCtl.getCresSurfaceHolder(sessionId);
    		sh.addCallback(this); //needed?
    		updateNativeDataStruct(sessionId);
    		nativeSurfaceInit(sh.getSurface(), sessionId);
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

    public void onStop(final int sessionId) {
    	isPlaying = false;
    	Log.d(TAG, "Stopping MediaPlayer");
        //nativeSurfaceFinalize (sessionId);should be called in surfaceDestroyed()
        nativeStop(sessionId);        
    }
    
    private void updateNativeDataStruct(int sessionId)
    {
    	String url = streamCtl.userSettings.getStreamInUrl(sessionId);
    	String newUrl = url;
    	
    	//Need to modify url function if proxy enable
    	if(streamCtl.userSettings.getProxyEnable(sessionId))
    	{
    		newUrl = MiscUtils.getLocalUrl(url);
    	}

    	setServerUrl(newUrl, sessionId); 
    	setRtspPort(streamCtl.userSettings.getRtspPort(sessionId), sessionId);
    	setTsPort(streamCtl.userSettings.getTsPort(sessionId), sessionId);
    	setRtpVideoPort(streamCtl.userSettings.getRtpVideoPort(sessionId), sessionId);
    	setRtpAudioPort(streamCtl.userSettings.getRtpAudioPort(sessionId), sessionId);
    	setSessionInitiation(streamCtl.userSettings.getSessionInitiation(sessionId), sessionId);
    	setTransportMode(streamCtl.userSettings.getTransportMode(sessionId), sessionId);
    	setMulticastAddress(streamCtl.userSettings.getMulticastAddress(sessionId), sessionId);
    	setStreamingBuffer(streamCtl.userSettings.getStreamingBuffer(sessionId), sessionId);
    	setStatistics(streamCtl.userSettings.isStatisticsEnable(sessionId), sessionId);
    	setUserName(streamCtl.userSettings.getUserName(sessionId), sessionId);
    	setPassword(streamCtl.userSettings.getPassword(sessionId), sessionId);
    	setVolume((int)streamCtl.userSettings.getVolume(), sessionId);
    	setNewSink(streamCtl.userSettings.isNewSink(sessionId), sessionId);
    	setAudioDrop(streamCtl.userSettings.isRavaMode(), sessionId);
    	setRtspTcpInterleave(streamCtl.userSettings.getTcpInterleave(sessionId), sessionId);
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
    	if (isPlaying)
    		return 1;//TODO
    	else
    		return 0;
    }
    public int getMediaPlayerAudiochannelsFb(){
    	if (isPlaying)
    		return 2;//TODO
    	else
    		return 0;
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
//    private void onGStreamerInitialized () {
//         Log.i ("GStreamer", "Gst initialized. Restoring state, playing:" + is_playing_desired);
//         // Restore previous playing state
//         if (is_playing_desired) {
//             nativePlay();
//         } else {
//             nativePause();
//         }
//    }

    static {
        nativeClassInit();
    }
 
	// Find the session id (aka stream number) given a surface holder.
	// Returns <0 for failure.
    private int sessionIdFromSurfaceHolder(SurfaceHolder holder) {
		int i;
		for(i=0; i<CresStreamCtrl.NumOfSurfaces; i++) {
			if(streamCtl.getCresSurfaceHolder(i) == null){
			    continue;
			}
			if(streamCtl.getCresSurfaceHolder(i) == holder) {
				return i;
			}			
		}    
		return -1;
    }
    
    //TODO: check if we can delete implements surface for class
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
            int height) {
		int sessionId = sessionIdFromSurfaceHolder(holder);     
		if(sessionId < 0)
		{
		    Log.d("GStreamer","Failed to get session id from surface holder");
		    return;
		}
        Log.d("GStreamer", "Surface for stream " + sessionId + " changed to format " + format + " width "
                + width + " height " + height);		
        nativeSurfaceInit (holder.getSurface(), sessionId);
    }

    public void surfaceCreated(SurfaceHolder holder) {
        int sessionId = sessionIdFromSurfaceHolder(holder);        
   		if(sessionId < 0)
		{
		    Log.d("GStreamer","Failed to get session id from surface holder");
		    return;
		}
        Log.d("GStreamer", "Surface for stream " + sessionId + " created: " + holder.getSurface());
        nativeSurfaceInit (holder.getSurface(), sessionId);
    }

    public void surfaceDestroyed(SurfaceHolder holder) {        
        int sessionId = sessionIdFromSurfaceHolder(holder);        
		if(sessionId < 0)
		{
		    Log.d("GStreamer","Failed to get session id from surface holder");
		    return;
		}        
		Log.d("GStreamer", "Surface for stream " + sessionId + " destroyed");		
        nativeSurfaceFinalize (sessionId);
    }
}
