package com.crestron.txrxservice;

public class StreamIn {
	private StreamInStrategy strategy;
	
	public StreamIn(StreamInStrategy streamStrategy) {
		this.strategy = streamStrategy;
	}
	
	// Static methods for GstreamIn
	public static void setServerUrl(String url, int sessionId){
		if (CresStreamCtrl.useGstreamer)
			GstreamIn.setServerUrl(url, sessionId);
	}
	
	public static void setStatistics(boolean enabled, int sessId){
		if (CresStreamCtrl.useGstreamer)
			GstreamIn.setStatistics(enabled, sessId);
	}
	
	public static void resetStatistics(int sessId){
		if (CresStreamCtrl.useGstreamer)
		GstreamIn.resetStatistics(sessId);
	}
	
	// Interface Methods
	public void setSessionIndex(int id) {
		strategy.setSessionIndex(id);
	}
	
    public void setRtspPort(int port, int sessionId) {
    	strategy.setRtspPort(port, sessionId);
    }
    
    public void setTsPort(int port, int sessionId) {
    	strategy.setTsPort(port, sessionId);
    }
    
    public void setRtpVideoPort(int port, int sessionId) {
    	strategy.setRtpVideoPort(port, sessionId);
    }
    
    public void setRtpAudioPort(int port, int sessionId) {
    	strategy.setRtpAudioPort(port, sessionId);
    }
    
    public void setSessionInitiation(int initMode, int sessionId) {
    	strategy.setSessionInitiation(initMode, sessionId);
    }
    
    public void setTransportMode(int transportMode, int sessionId) {
    	strategy.setTransportMode(transportMode, sessionId);
    }
    
    public void setMulticastAddress(String multicastIp, int sessionId) {
    	strategy.setMulticastAddress(multicastIp, sessionId);
    }
    
    public void setStreamingBuffer(int buffer_ms, int sessionId) {
    	strategy.setStreamingBuffer(buffer_ms, sessionId);
    }
    
    public void setUserName(String userName, int sessionId) {
    	strategy.setUserName(userName, sessionId);
    }
    
    public void setPassword(String password, int sessionId) {
    	strategy.setPassword(password, sessionId);
    }
    
    public void setVolume(int volume, int sessionId) {
    	strategy.setVolume(volume, sessionId);
    }
    
    public void updateCurrentXYloc(int xloc, int yloc, int sessId) {
    	strategy.updateCurrentXYloc(xloc, yloc, sessId);
    }
    
    public void setNewSink(boolean flag, int sessionId) {
    	strategy.setNewSink(flag, sessionId);
    }
    
    public long getStreamInNumVideoPackets() {
    	return strategy.getStreamInNumVideoPackets();
    }
    
	public int getStreamInNumVideoPacketsDropped() {
		return strategy.getStreamInNumVideoPacketsDropped();
	}
	
	public long getStreamInNumAudioPackets() {
		return strategy.getStreamInNumAudioPackets();
	}
	
	public int getStreamInNumAudioPacketsDropped() {
		return strategy.getStreamInNumAudioPacketsDropped();
	}
	
	public int getStreamInBitrate() {
		return strategy.getStreamInBitrate();
	}
	
    public void disableLatency(int sessionId) {
    	strategy.disableLatency(sessionId);
    }
    
    public void setRtspTcpInterleave(boolean tcpInterleave, int sessionId) {
    	strategy.setRtspTcpInterleave(tcpInterleave, sessionId);
    }
    
    public void setRtpOnlyMode(int vport, int aport, String ip, int sessionId) {
    	strategy.setRtpOnlyMode(vport, aport, ip, sessionId);
    }
    
    public void onStart(int sessionId) {
    	strategy.onStart(sessionId);
    }
    
    public void onPause(int sessionId) {
    	strategy.onPause(sessionId);
    }
    
    public void onStop(int sessionId) {
    	strategy.onStop(sessionId);
    }
    
    public boolean getMediaPlayerStatus() {
    	return strategy.getMediaPlayerStatus();
    }
    
    public int getMediaPlayerAudioFormatFb() {
    	return strategy.getMediaPlayerAudioFormatFb();
    }
    
    public int getMediaPlayerAudiochannelsFb() {
    	return strategy.getMediaPlayerAudiochannelsFb();
    }
}

interface StreamInStrategy{
    public void setSessionIndex(int id);    
    public void setRtspPort(int port, int sessionId);    
    public void setTsPort(int port, int sessionId);    
    public void setRtpVideoPort(int port, int sessionId);    
    public void setRtpAudioPort(int port, int sessionId);    
    public void setSessionInitiation(int initMode, int sessionId);    
    public void setTransportMode(int transportMode, int sessionId);    
    public void setMulticastAddress(String multicastIp, int sessionId);    
    public void setStreamingBuffer(int buffer_ms, int sessionId);    
    public void setUserName(String userName, int sessionId);    
    public void setPassword(String password, int sessionId);
    public void setVolume(int volume, int sessionId);
    public void updateCurrentXYloc(int xloc, int yloc, int sessId);
    public void setNewSink(boolean flag, int sessionId);
    public long getStreamInNumVideoPackets();	
	public int getStreamInNumVideoPacketsDropped();	
	public long getStreamInNumAudioPackets();	
	public int getStreamInNumAudioPacketsDropped();	
	public int getStreamInBitrate();
    public void disableLatency(int sessionId);
    public void setRtspTcpInterleave(boolean tcpInterleave, int sessionId);
    public void setRtpOnlyMode(int vport, int aport, String ip, int sessionId);
    public void onStart(int sessionId); 
    public void onPause(int sessionId);
    public void onStop(int sessionId);
    public boolean getMediaPlayerStatus();
    public int getMediaPlayerAudioFormatFb();
    public int getMediaPlayerAudiochannelsFb();
}
