package com.crestron.txrxservice;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.WindowManager;
import android.content.Context;

import com.crestron.txrxservice.CresStreamCtrl.CrestronHwPlatform;
import com.crestron.txrxservice.CresStreamCtrl.DeviceMode;
import com.crestron.txrxservice.CresStreamCtrl.StreamState;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSessionStreamingState;

import com.crestron.airmedia.utilities.ViewBase;
import android.util.DisplayMetrics;

public class GstreamIn implements SurfaceHolder.Callback {

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
    private final int stopTimeout_sec = 15;
    private final int startTimeout_ms = 25000;
    private boolean isPlaying = false;
    private String priorAddress = null;
    boolean[] wfdIsPlaying = new boolean[com.crestron.txrxservice.CresStreamCtrl.NumOfSurfaces];
    private final static String ducatiRecoverFilePath = "ducatiRecoverTime";
    private final static long ducatiRecoverTimeDelta = (30 * 1000); //30 seconds


    private native void nativeInit();     // Initialize native code, build pipeline, etc
    private native void nativeFinalize(); // Destroy pipeline and shutdown native code
    private native void nativePlay(int sessionId);     // Set pipeline to PLAYING
    private native void nativePause(int sessionId);    // Set pipeline to PAUSED
    private native void nativeStop(int sessionId);    // Set pipeline to NULL
    private static native boolean nativeClassInit(); // Initialize native class: cache Method IDs for callbacks
    private native void nativeSurfaceInit(Object surface, int sessionId);
    private native void nativeSurfaceFinalize(int sessionId);
    private native void nativeSetWfdMaxMiracastBitrate(int maxrate);
    private native void nativeSetWfdMaxMiracastFps(int fps);
    private native void nativeWfdStart(int streamId, long sessionId, String url, int rtsp_port, String localAddress, String localIfc, boolean isTx3,
    String systemMode);
    private native void nativeWfdStop(int streamId, long sessionId);
    private native void nativeWfdPause(int streamId);
    private native void nativeWfdResume(int streamId);
    private native void nativeMsMiceStart(String configPath);
    private native void nativeMsMiceStop();
    private native void nativeMsMiceSetAdapterAddress(String address);
    private native void nativeMsMiceSetPin(String pin);
    private native void nativeMsMiceSetWfd2videoFmt(String wfd2VideoParms);
    private long native_custom_data;      // Native code will use this to keep private data

    private static native void 	nativeSetServerUrl(String url, int sessionId);
    private native void			nativeSetRTCPDestIP(String rtcpIp, int sessionId);
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
    private native void         nativeSetResolutionIndex(int resolutionIndex, int sessionId);

    private native void			nativeInitUnixSocketState();

    public GstreamIn(CresStreamCtrl mContext) {
        Log.i(TAG, "GstreamIn: begin");
        streamCtl = mContext;
        for (int i=0; i < CresStreamCtrl.NumOfSurfaces; i++)
        	wfdIsPlaying[i] = false;
        nativeSetStopTimeout(stopTimeout_sec);
        nativeInit();
        nativeSetWfdMaxMiracastBitrate(streamCtl.userSettings.getAirMediaMaxMiracastBitrate());
        Log.i(TAG, "GstreamIn: end");
    }

    public void initUnixSocketState()
    {
    	nativeInitUnixSocketState();
    }

    public static void setServerUrl(String url, int sessionId){
    	nativeSetServerUrl(url, sessionId);
	}

    public void setRTCPDestIP(String rtcpIp, int sessionId) {
    	nativeSetRTCPDestIP(rtcpIp, sessionId);
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

      Log.i(TAG, "in setStreamingBuffer(), buffer_ms = " + buffer_ms + ", sessionId = " + sessionId);

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

    public void sendStatistics(int streamId,long video_packets_received, int video_packets_lost, long audio_packets_received, int audio_packets_lost, int bitrate){
    	statisticsNumVideoPackets = video_packets_received;
    	if (video_packets_lost < 0)
    	{
    		video_packets_lost = 0;
    	}
        statisticsNumVideoPacketsDropped = video_packets_lost;
        statisticsNumAudioPackets = audio_packets_received;
        if (audio_packets_lost < 0)
        {
        	audio_packets_lost = 0;
        }
        statisticsNumAudioPacketsDropped = audio_packets_lost;
        statisticsBitrate = bitrate;

        streamCtl.SendStreamInFeedbacks(streamId);
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
    	// special launch command which needs a surface, for now just use id 0
    	if (cmd.contains("LAUNCH_START"))
    	{
    		Surface s = streamCtl.getSurface(sessionId);
			nativeSurfaceInit(s, sessionId);
    	}

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

    public void setResolutionIndex(int resolutionIndex, int sessionId){
        nativeSetResolutionIndex(resolutionIndex, sessionId);
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

    public void updateStreamStatus(int streamStateEnum, int streamId){
    	int curStreamState = 0;
    	// Send stream url again on start fb
    	Log.i(TAG, "updateStreamStatus: for streamId="+streamId+"  state="+streamStateEnum);
    	if (!streamCtl.mUsedForAirMedia[streamId])
    	{
        	Log.v(TAG, "updateStreamStatus: streamId " + streamId + " not being used for airmedia");
    		if (streamStateEnum == CresStreamCtrl.StreamState.STARTED.getValue())
    		{
    			streamCtl.sockTask.SendDataToAllClients(MiscUtils.stringFormat("STREAMURL%d=%s", streamId, streamCtl.userSettings.getStreamInUrl(streamId)));
    		}
    		streamCtl.SendStreamState(StreamState.getStreamStateFromInt(streamStateEnum), streamId);
    	}
    	else
    	{
        	Log.v(TAG, "updateStreamStatus: streamId " + streamId + " used for airmedia");
        	// For miracast case get current stream state so that stopSession is not called multiple times when we get "STOPPED" state
        	// Note: new state MUST be saved so that the subsequent check for state changing to STOPPED works properly
        	if (streamStateEnum == CresStreamCtrl.StreamState.STOPPED.getValue())
        	{
        		curStreamState = getCurrentStreamState(streamId);
        	}
        	streamCtl.setCurrentStreamState(StreamState.getStreamStateFromInt(streamStateEnum), streamId);
    		if (streamStateEnum == CresStreamCtrl.StreamState.STARTED.getValue())
    		{
                Log.v(TAG, "updateStreamStatus: inform wifiVideoPlayer streamstate changed to Playing");
    			streamCtl.wifidVideoPlayer.stateChanged(streamId, AirMediaSessionStreamingState.Playing);
    		}
    		else if (streamStateEnum == CresStreamCtrl.StreamState.STOPPED.getValue())
    		{
                Log.v(TAG, "updateStreamStatus: newStreamState="+streamStateEnum+" oldStreamState="+curStreamState+" wfdIsPlaying="+wfdIsPlaying[streamId]);
    			if (streamStateEnum != curStreamState && wfdIsPlaying[streamId])
    			{
    				streamCtl.wifidVideoPlayer.stopSessionWithStreamId(streamId);
    			}
    			else
    			{
    		    	Log.i(TAG, "updateStreamStatus: for streamId="+streamId+": state was already stopped");
    			}
    		}
    	}
    	Log.v(TAG, "updateStreamStatus: exit");
    }

    public void wfdSetFirewallRules(int streamId, int rtsp_port, int ts_port)
	{
		streamCtl.sockTask.SendDataToAllClients(MiscUtils.stringFormat("WFD_FIREWALL_RULES%d=%d %d", streamId, rtsp_port, ts_port));
	}

    public void sendStopCapture()
    {
        if (CresStreamCtrl.isAM3K)
            streamCtl.sockTask.SendDataToAllClients(MiscUtils.stringFormat("STOPCAPTURE=true"));
    }

    public void sendLostVideoIntent()
    {
        // intended for IrisService app and GS
        if (CresStreamCtrl.isAM3K)
            streamCtl.sendLostVideoIntent();
    }

    public void sendDSVideoReady()
    {
    	Log.i(TAG, "Send DS Video Ready message to CSIO - for sending of reserved join");
    	streamCtl.sockTask.SendDataToAllClients(MiscUtils.stringFormat("DS_VIDEO_READY=true"));
    }

    public void executeRootCommand(String command)
    {
        if (CresStreamCtrl.isAM3K)
            streamCtl.sendExecuteRootCommand(command);
    }

    public void sendInitiatorFbAddress(String initiatorFbAddress, int sessionId){
    	streamCtl.userSettings.setInitiatorAddress(initiatorFbAddress);
    	streamCtl.sendInitiatorFbAddress(initiatorFbAddress, sessionId);
    }

    public void recoverHdcpVideoFailure(int sessionId){
		// Pass to CSIO to restart stream
    	streamCtl.sockTask.SendDataToAllClients(MiscUtils.stringFormat("RECOVERHDCPVIDEOFAILURE%d=true", sessionId));
    }

    public void recoverDucati(){
    	// This recovery is only applicable for Ducati in OMAP5
    	if (streamCtl.mHwPlatform == CrestronHwPlatform.eHardwarePlatform_OMAP5)
    	{
       		// Increment gstreamer timeout counter
       		MiscUtils.writeStringToDisk(CresStreamCtrl.gstreamerTimeoutCountFilePath, String.valueOf(++(streamCtl.mGstreamerTimeoutCount)));

       		long previousRecoverTime = 0;
       		try {
       			previousRecoverTime = Long.parseLong(MiscUtils.readStringFromDisk(ducatiRecoverFilePath));
       		} catch (Exception e) {}
       		long currentTime = MiscUtils.getSystemUptimeMs();

       		// Write current time as most recent ducati recover time
       		MiscUtils.writeStringToDisk(ducatiRecoverFilePath, String.valueOf(currentTime));

       		// If recovery occurred within of previous recovery, give 10 seconds to let system recover
       		if ((previousRecoverTime != 0) && (Math.abs(currentTime - previousRecoverTime) <= ducatiRecoverTimeDelta))
       		{
       			Log.i(TAG, "Giving system extra 10 seconds to recover");
       			try {
       				Thread.sleep(10000);
       			} catch (Exception e) { e.printStackTrace(); }
       		}

       		// Delete all surfaces so we dont have a gralloc memory leak
       		for(int sessionId = 0; sessionId < CresStreamCtrl.NumOfSurfaces; sessionId++)
       		{
       			streamCtl.hideStreamInWindow(sessionId);
       		}

       		streamCtl.RecoverDucati();
       		try {
       			Thread.sleep(5000);
       		} catch (Exception e) { e.printStackTrace(); }
//    		streamCtl.RecoverTxrxService();

    		for(int sessionId = 0; sessionId < CresStreamCtrl.NumOfSurfaces; sessionId++)
    		{
    			if (streamCtl.userSettings.getMode(sessionId) == DeviceMode.STREAM_IN.ordinal())
    			{
    				// Fix bug 131977: Must send stop in order to allow next start to work
    				streamCtl.SendStreamState(StreamState.STOPPED, sessionId);
    			}
    		}

    		streamCtl.RecoverMediaServer();
    	}
	}

    public void recoverMediaServer(){
    	for(int sessionId = 0; sessionId < CresStreamCtrl.NumOfSurfaces; sessionId++)
    	{
    		if (streamCtl.userSettings.getMode(sessionId) == DeviceMode.STREAM_IN.ordinal())
    		{
    			streamCtl.SendStreamState(StreamState.STOPPED, sessionId);
    		}
    	}

    	streamCtl.RecoverMediaServer();
	}

    public void sendMulticastAddress(String multicastAddress, int sessionId){
    	streamCtl.userSettings.setMulticastAddress(multicastAddress, sessionId);
    	streamCtl.sendMulticastIpAddress(multicastAddress, sessionId);
	}

    public void sendVideoSourceParams(int source, int width, int height, int framerate, int profile){
    	// Update window size with new video dimensions
    	Log.v(TAG, "sendVideoSourceParams: streamId="+source+"  wxh="+width+"x"+height+
    			"   old="+streamCtl.mVideoDimensions[source].videoWidth+"x"+streamCtl.mVideoDimensions[source].videoHeight);
    	if (streamCtl.mVideoDimensions[source].videoHeight != height || streamCtl.mVideoDimensions[source].videoWidth != width)
    		streamCtl.updateWindowWithVideoSize(source, false, width, height);

    	streamCtl.userSettings.setStreamInHorizontalResolution(width);
    	streamCtl.userSettings.setStreamInVerticalResolution(height);
    	streamCtl.userSettings.setStreamInFPS(framerate);
    	streamCtl.userSettings.setStreamInAspectRatio(Integer.parseInt(MiscUtils.calculateAspectRatio(width, height)));
    	streamCtl.SendStreamInVideoFeedbacks(source, width, height, framerate, profile); //TODO: see if profile needs to be converted
    }

    public void sendMsMiceStateChange(long sessionId, int state, String localAddress, String deviceId, String deviceName, String deviceAddress, int rtsp_port)
    {
    	Log.i(TAG, "sendMsMiceStateChange: sessionId="+sessionId+"  state="+state+"   rtsp_port="+rtsp_port);
    	if (state == 1)
    	{
    		// signal session is ready to observer
    		streamCtl.wifidVideoPlayer.onSessionReady(sessionId, localAddress, deviceId, deviceName, deviceAddress, rtsp_port);
    	}
    	else if (state == 0)
    	{
    		// signal video from session has stopped to observer
    		int streamId = streamCtl.wifidVideoPlayer.sessionId2streamId(sessionId);
    		if (streamId >= 0)
    		{
    			streamCtl.wifidVideoPlayer.stopSession(sessionId);
    		}
    	}
    }

    //Note: it is called from jni.cpp for miracast connection.
    public void sendPendingSessionStateChange(long sessionId, String remoteAddress, String state)
    {
    	Log.i(TAG, "sendPendingSessionStateChange: sessionId = " + sessionId + ", remoteAddress = " + remoteAddress + " state = " + state);
        streamCtl.mCanvas.mCrestore.setPendingSessionStateChangeToCresstore("MiracastMsmice", remoteAddress, state);
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
//        Log.i(TAG, "vport "+vport+ "aport "+aport +"ip "+ip);
//        sb.append("v=0\r\n").append("o=- 15545345606659080561 15545345606659080561 IN IP4 cpu000669\r\n").append("s=Sample\r\n").append("i=N/A\r\n").append("c=IN IP4 ").append(ip).append("\r\n").append("t=0 0\r\n").append("a=range:npt=now-\r\n").append("m=audio ").append(Integer.toString(aport)).append(" RTP/AVP 96\r\n").append("a=control:audio\r\n").append("a=rtpmap:96 MP4A-LATM/44100/2\r\n").append("a=fmtp:96 profile-level-id=15; object=2; cpresent=0; config=400024203fc0\r\n").append("m=video ").append(Integer.toString(vport)).append(" RTP/AVP 97\r\n").append("a=control:video\r\n").append("a=rtpmap:97 H264/90000\r\n").append("a=fmtp:97 profile-level-id=64002A;in-band-parameter-sets=1;packetization-mode=1\r\n");
    }

    //Play based on based Pause/Actual Playback
    public void onStart(final int sessionId) {
    	final GstreamIn gStreamObj = this;
    	final CountDownLatch latch = new CountDownLatch(1);
        Log.i(TAG, "onStart, sessionId: " + sessionId);
    	Thread startThread = new Thread(new Runnable() {
    		public void run() {
    			try {
    				isPlaying = true;
    				updateNativeDataStruct(sessionId);
    				Surface s = streamCtl.getSurface(sessionId);
                    Log.i(TAG, "nativeSurfaceInit, sessionId: " + sessionId + ", isValid: " + s.isValid());
    				nativeSurfaceInit(s, sessionId);
    				//    		Code below is for trying TextureView rendering for QuadView
    				//    		Log.i(TAG, "*********** passing surface derived from TextureView for sessionId: " + sessionId + " to 'nativeSurfaceInit' ************");
    				//    		Surface s = new Surface(streamCtl.getAirMediaSurfaceTexture(sessionId));
    				//    		nativeSurfaceInit(s, sessionId);
                    Log.i(TAG, "nativePlay, sessionId: " + sessionId);
    				nativePlay(sessionId);
    			}
    			catch(Exception e){
    				// TODO: explore exception handling with better feedback of what went wrong to user
    				streamCtl.SendStreamState(StreamState.STOPPED, sessionId);
    				e.printStackTrace();
    			}
    			latch.countDown();
    		}
    	});
    	startThread.start();

    	// We launch the start command in its own thread and timeout in case mediaserver gets hung
    	boolean successfulStart = true; //indicates that there was no time out condition
    	try { successfulStart = latch.await(startTimeout_ms, TimeUnit.MILLISECONDS); }
    	catch (InterruptedException ex) { ex.printStackTrace(); }

    	streamCtl.checkVideoTimeouts(successfulStart);
    	if (!successfulStart)
    	{
    		Log.e(TAG, MiscUtils.stringFormat("Stream In failed to start after %d ms", startTimeout_ms));
    		startThread.interrupt(); //cleanly kill thread
    		startThread = null;
    		streamCtl.RecoverTxrxService();
    	}
    }

    //Pause
    public void onPause(int sessionId) {
        nativePause(sessionId);
    }

    public void onStop(final int sessionId) {
    	isPlaying = false;
    	Log.i(TAG, "Stopping MediaPlayer");
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
    		setRTCPDestIP(MiscUtils.getRTSPIP(url), sessionId);
    		newUrl = MiscUtils.getLocalUrl(url, streamCtl.userSettings.getDecodeInternalRtspPort(sessionId));
    	}
    	else
    	{
    		setRTCPDestIP("", sessionId);
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

    private void updateNativeWfdDataStruct(int sessionId)
    {
    	String url = streamCtl.userSettings.getStreamInUrl(sessionId);
    	String newUrl = url;

    	//Need to modify url function if proxy enable
    	if(streamCtl.userSettings.getProxyEnable(sessionId))
    	{
    		setRTCPDestIP(MiscUtils.getRTSPIP(url), sessionId);
    		newUrl = MiscUtils.getLocalUrl(url, streamCtl.userSettings.getDecodeInternalRtspPort(sessionId));
    	}
    	else
    	{
    		setRTCPDestIP("", sessionId);
    	}

    	//setTsPort(streamCtl.userSettings.getTsPort(sessionId), sessionId);
    	setStreamingBuffer(200, sessionId);
    	setStatistics(streamCtl.userSettings.isStatisticsEnable(sessionId), sessionId);
    	setVolume((int)streamCtl.userSettings.getVolume(), sessionId);
    	//setNewSink(streamCtl.userSettings.isNewSink(sessionId), sessionId);
    	//setAudioDrop(streamCtl.userSettings.isRavaMode(), sessionId);
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

    public void wfdSetMaxMiracastBitrate(int maxrate) {
    	nativeSetWfdMaxMiracastBitrate(maxrate);
    }

    public void wfdStart(final int streamId, final long sessionId, final String url, final int rtsp_port, final String localAddress, final String localIfc)
    {
    	final GstreamIn gStreamObj = this;
    	final CountDownLatch latch = new CountDownLatch(1);
    	Thread startThread = new Thread(new Runnable() {
    		public void run() {
    			try {
    				isPlaying = true;
    				if (!wfdIsPlaying[streamId])
    				{
    					wfdIsPlaying[streamId] = true;
    					updateNativeWfdDataStruct(streamId);
    					Surface s = streamCtl.getSurface(streamId);
    					nativeSurfaceInit(s, streamId);
    					nativeSetWfdMaxMiracastFps(streamCtl.getMaxMiracastFps());
    					nativeWfdStart(streamId, sessionId, url, rtsp_port, localAddress, localIfc, streamCtl.isTX3Device(streamId),
    					streamCtl.getSystemMode());
    				}
    				else
    				{
    		    		Log.w(TAG, MiscUtils.stringFormat("Trying to restart stream that was not stopped in wfdStart for streamId=", streamId));
    				}
    			}
    			catch(Exception e){
    				// TODO: explore exception handling with better feedback of what went wrong to user
    				streamCtl.SendStreamState(StreamState.STOPPED, streamId);
    				e.printStackTrace();
    			}
    			latch.countDown();
    		}
    	});
    	startThread.start();

    	// We launch the start command in its own thread and timeout in case mediaserver gets hung
    	boolean successfulStart = true; //indicates that there was no time out condition
    	try { successfulStart = latch.await(startTimeout_ms, TimeUnit.MILLISECONDS); }
    	catch (InterruptedException ex) { ex.printStackTrace(); }

    	streamCtl.checkVideoTimeouts(successfulStart);
    	if (!successfulStart)
    	{
    		Log.e(TAG, MiscUtils.stringFormat("Stream In failed to start after %d ms", startTimeout_ms));
    		startThread.interrupt(); //cleanly kill thread
    		startThread = null;
    		streamCtl.RecoverTxrxService();
    	}
    }

    public void wfdStop(final int streamId, long sessionId)
    {
		Log.i(TAG, "wfdStop");
    	isPlaying = false;
    	if (wfdIsPlaying[streamId])
    	{
    		wfdIsPlaying[streamId] = false;
    		nativeWfdStop(streamId, sessionId);
    	}
    	else
    	{
	    	Log.i(TAG, MiscUtils.stringFormat("Trying to stop stream that was not started in wfdStop for streamId=", streamId));
    	}
    }

    public void wfdPause(final int streamId)
    {
        Log.i(TAG, "wfdPause() called for streamId "+streamId);
    	nativeWfdPause(streamId);
    }

    public void wfdResume(final int streamId)
    {
        Log.i(TAG, "wfdResume() called for streamId "+streamId);
        if (!CresStreamCtrl.isAM3K)
        {
            Surface s = streamCtl.getSurface(streamId);
            nativeSurfaceInit(s, streamId);
        }
		nativeWfdResume(streamId);
    }

    public void sendMiracastOsVersion(final int streamId, String osVersion)
    {
    	Log.i(TAG, "sendMiracastOsVersion():   streamId="+streamId+"   OS version="+osVersion);
		streamCtl.wifidVideoPlayer.infoChanged(streamId, osVersion);
    }

    public void msMiceStart()
    {
    	Log.i(TAG, "msMiceStart");
    	nativeMsMiceStart(streamCtl.getFilesDir().getAbsolutePath());
    }

    public void msMiceStop()
    {
    	Log.i(TAG, "msMiceStop");
    	nativeMsMiceStop();
    }

    public void msMiceSetPin(String pin)
    {
    	Log.v(TAG, "msMiceSetPin - PIN="+pin);
    	nativeMsMiceSetPin(pin);
    }

    public void msMiceSetWfd2VideoFormat(String wfd2VideoParms)
    {
        if(streamCtl.isAM3K)
        {
            Log.i(TAG, "setWfd2VideoFormat: "+wfd2VideoParms);
            nativeMsMiceSetWfd2videoFmt(wfd2VideoParms);
        }
    }

    public void msMiceSetAdapterAddress(String address)
    {
    	Log.i(TAG, "msMiceSetAdapterAddress - address="+address);
    	boolean change = false;
    	if (priorAddress == null && address == null)
    		return;
    	else if (priorAddress == null && address != null)
    	{
    		priorAddress = address;
    		change = true;
    	}
    	else if (priorAddress != null && address == null)
    	{
    		priorAddress = address;
    		change = true;
    	}
    	else if (!priorAddress.equals(address))
    	{
    		priorAddress = address;
    		change = true;
    	}
    	if (change)
    	{
        	Log.i(TAG, "address has changed - calling msMiceSetAdapterAddress with address="+address);
        	nativeMsMiceSetAdapterAddress(address);
    	}
    }

	// Find the session id (aka stream number) given a surface holder.
	// Returns <0 for failure.
    private int sessionIdFromSurfaceHolder(SurfaceHolder holder) {
		int i;
		for(i=0; i<CresStreamCtrl.NumOfSurfaces; i++) {
			if(streamCtl.dispSurface.GetSurfaceHolder(i) == null){
			    continue;
			}
			if(streamCtl.dispSurface.GetSurfaceHolder(i) == holder) {
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
		    Log.i("GStreamer","Failed to get session id from surface holder");
		    return;
		}
        Log.i("GStreamer", "Surface for stream " + sessionId + " changed to format " + format + " width "
                + width + " height " + height);
        nativeSurfaceInit (holder.getSurface(), sessionId);
    }

    public void surfaceCreated(SurfaceHolder holder) {
        int sessionId = sessionIdFromSurfaceHolder(holder);
   		if(sessionId < 0)
		{
		    Log.i("GStreamer","Failed to get session id from surface holder");
		    return;
		}
        Log.i("GStreamer", "Surface for stream " + sessionId + " created: " + holder.getSurface());
        nativeSurfaceInit (holder.getSurface(), sessionId);
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        int sessionId = sessionIdFromSurfaceHolder(holder);
		if(sessionId < 0)
		{
		    Log.i("GStreamer","Failed to get session id from surface holder");
		    return;
		}
		Log.i("GStreamer", "Surface for stream " + sessionId + " destroyed");
        nativeSurfaceFinalize (sessionId);
    }

    public String getMinStreamResolution(int percentFS)
    {
        int widthMin  = 0;
        int heightMin = 0;
        DisplayMetrics dispMetrics = ViewBase.getDisplayMetrics((WindowManager) streamCtl.getSystemService(Context.WINDOW_SERVICE));
        if(percentFS > 0)
        {
            widthMin  = dispMetrics.widthPixels/percentFS;
            heightMin = dispMetrics.heightPixels/percentFS;
        }

        String minResString = widthMin+"x"+heightMin;
        Log.i(TAG, "getMinStreamResolution: Display [wxh] = "+dispMetrics.widthPixels+"x"+dispMetrics.heightPixels+", Minimum [wxh] = "+minResString);

        return(minResString);
    }
}
