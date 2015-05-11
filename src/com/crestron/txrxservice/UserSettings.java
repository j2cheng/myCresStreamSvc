package com.crestron.txrxservice;

import java.util.HashMap;
import java.util.Map;

import com.crestron.txrxservice.CresStreamConfigure.StreamMode;
import com.crestron.txrxservice.CresStreamConfigure.VideoEncProfile;
import com.crestron.txrxservice.CresStreamCtrl.StreamState;

import android.util.Log;

public class UserSettings //implements java.io.Serializable
{
	public enum VideoEncProfile {
		BP(1), MP(2), HP(8);
		private int value;
		private static final Map<Integer, VideoEncProfile> intToEnum = new HashMap<Integer, VideoEncProfile>();		
		static {
			for (VideoEncProfile type:values()){
				intToEnum.put(type.getVEncProfile(), type);
			}
		}
		private VideoEncProfile(int _value){
			value = _value;
		}	
		public int getVEncProfile() { 
			return value;
		}
		public static VideoEncProfile fromInteger(int value){
			return intToEnum.get(value);
		}
	}
	
//	public enum SessionInitiation 
//    {
//    	ByReceiver(0),
//    	ByTransmitter(1),
//    	MulticastViaRTSP(2),
//    	MulticastViaUDP(3),
//
//    	// Do not add anything after the Last state
//    	LAST(4);
//    	
//        private final int value;
//
//        SessionInitiation(int value) 
//        {
//            this.value = value;
//        }
//
//        public int getValue() 
//        {
//            return value;
//        }
//        public static String getStringValueFromInt(int i) 
//        {
//            for (StreamState state : StreamState.values()) 
//            {
//                if (state.getValue() == i) 
//                {
//                    return state.toString();
//                }
//            }
//            return ("Invalid State.");
//        }
//    } 
//	
//	public enum TransportMode 
//    {
//    	RTP(0),
//    	TsOverRtp(1),
//    	TsOverUdp(2),
//    	
//    	// Do not add anything after the Last state
//    	LAST(3);
//    	
//        private final int value;
//
//        TransportMode(int value) 
//        {
//            this.value = value;
//        }
//
//        public int getValue() 
//        {
//            return value;
//        }
//        public static String getStringValueFromInt(int i) 
//        {
//            for (StreamState state : StreamState.values()) 
//            {
//                if (state.getValue() == i) 
//                {
//                    return state.toString();
//                }
//            }
//            return ("Invalid State.");
//        }
//    }

	// MultiWindow joins	
	private int[] mode;
	private int[] xloc;
	private int[] yloc;
	private int[] w;
	private int[] h;
	private CresStreamCtrl.StreamState[] streamState;
	private int[] rtspPort;
	private int[] tsPort;
	private int[] rtpVideoPort;
	private int[] rtpAudioPort;
	private int[] bitrate;
	private int[] transportMode; // TODO: make enum
	private int[] sessionInitiation; //TODO: make enum
	private int[] encodingResolution; // TODO: make enum
	private int[] encodingFramerate;
	private int[] streamingBuffer;
	private String[] serverUrl;
	private String[] multicastAddress;
	private String[] userName;
	private String[] password;
	private boolean[] passwordEnable;
	private boolean[] passwordDisable;
	private VideoEncProfile[] streamProfile;
	private int[] encodingLevel; // TODO: this should be local to streamIn.java
	
	// Top Slot
	private int deviceReady; // TODO: needed?
	private boolean audioMute;
	private boolean audioUnmute;	
	private int volume;
	private int bass;
	private int treble;
	private String initiatorAddress;
	
	// OSD Slot
	private boolean osdEnable;
	private boolean osdDisable;
	private int osdLocation; // TODO: make enum
	private int osdXLocation;
	private int osdYLocation;
	private String osdText;
	
//	// HDMI in
//	private boolean hdmiInSyncDetected;
//	private boolean hdmiInInterlaced;
//	private boolean hdmiInCecError;
//	private int hdmiInHorizontalResolution;
//	private int hdmiInVerticalResolution;
//	private int hdmiInFPS;
//	private int hdmiInAspectRatio;
//	private int hdmiInAudioFormat;
//	private int hdmiInAudioChannels;
//	private String hdmiInTransmitCec;
//	private String hdmiInReceiveCec;
//	
//	// HDMI out
//	private boolean hdmiOutSyncDetected;
//	private boolean hdmiOutInterlaced;
//	private boolean hdmiOutCecError;
//	private boolean hdmiOutdisabledByHdcp;
//	private boolean hdmiOutdisplayBlankEnabled;
//	private boolean hdmiOutdisplayBlankDisabled;
//	private int hdmiOutResolution; // TODO: make enum
//	private int hdmiOutHorizontalResolution;
//	private int hdmiOutVerticalResolution;
//	private int hdmiOutFPS;
//	private int hdmiOutAspectRatio;
//	private int hdmiOutAudioFormat;
//	private int hdmiOutAudioChannels;
//	private String hdmiOutTransmitCec;
//	private String hdmiOutReceiveCec;
//	// TODO: do we need the hdmi out serials as well?
	
	// STREAM IN
	private int streamInHorizontalResolution;
	private int streamInVerticalResolution;
	private int streamInFPS;
	private int streamInAspectRatio;
	private int streamInAudioFormat;
	private int streamInAudioChannels;

    // STREAM OUT
	private int streamOutHorizontalResolution;
	private int streamOutVerticalResolution;
	private int streamOutFPS;
	private int streamOutAspectRatio;
	private int streamOutAudioFormat;
	private int streamOutAudioChannels;

	// Ethernet
	private boolean statisticsEnable;
	private boolean statisticsDisable;
	private String deviceIp;
	// TODO: do we need statistics and dhcp settings here?

	public UserSettings()
	{
        MiscUtils.getDeviceIpAddr();
        deviceIp 			= MiscUtils.matcher.group();
		rtspPort 			= new int[]{ 12462, 14462};		
        tsPort 				= new int[]{ 12460, 14460};		
        rtpVideoPort 		= new int[]{ 12458, 14458};
        rtpAudioPort 		= new int[]{ 12456, 14456};
        mode				= new int[]{0,0};
        w 					= new int[]{1920,1920};
        h					= new int[]{1080,1080};
        xloc 				= new int[]{0,0};
        yloc				= new int[]{0,0};
        encodingResolution	= new int[] {0, 0};
        encodingFramerate	= new int[] {50, 50}; // TODO: 50 not 60?
        bitrate				= new int[]{600000, 600000}; //????
        //mode 	= mode.RTSP;//RTSP;
        sessionInitiation 	= new int[] {0, 0};
        streamProfile 		= new VideoEncProfile[] {VideoEncProfile.HP, VideoEncProfile.HP};
        encodingLevel		= new int[] {8192, 8192};
        multicastAddress 	= new String[] {"0.0.0.0", "0.0.0.0"};
        serverUrl 	 		= new String[]{"", ""};
        userName 			= new String[] {"", ""};
        password   			= new String[] {"", ""};
        streamState			= new CresStreamCtrl.StreamState[] {StreamState.STOPPED, StreamState.STOPPED};
        transportMode		= new int[] {0, 0};
        passwordEnable		= new boolean[] {false, false};
        passwordDisable		= new boolean[] {true, true};
        streamingBuffer		= new int[] {2000, 2000};
	}
	
	public String getDeviceIp() {
		return deviceIp;
	}

	public int getMode(int sessId) {
		return mode[sessId];
	}
	
	public void setMode(int mode, int sessId) {
		this.mode[sessId] = mode;
	}

	public int getXloc(int sessId) {
		return xloc[sessId];
	}

	public void setXloc(int xloc, int sessId) {
		this.xloc[sessId] = xloc;
	}

	public int getYloc(int sessId) {
		return yloc[sessId];
	}

	public void setYloc(int yloc, int sessId) {
		this.yloc[sessId] = yloc;
	}

	public int getW(int sessId) {
		return w[sessId];
	}

	public void setW(int w, int sessId) {
		this.w[sessId] = w;
	}

	public int getH(int sessId) {
		return h[sessId];
	}

	public void setH(int h, int sessId) {
		this.h[sessId] = h;
	}

	public CresStreamCtrl.StreamState getStreamState(int sessId) {
		return streamState[sessId];
	}

	public void setStreamState(CresStreamCtrl.StreamState streamState, int sessId) {
		this.streamState[sessId] = streamState;
	}

	public int getDeviceReady() {
		return deviceReady;
	}

	public void setDeviceReady(int deviceReady) {
		this.deviceReady = deviceReady;
	}

	public boolean isAudioMute() {
		return audioMute;
	}

	public void setAudioMute(boolean audioMute) {
		this.audioMute = audioMute;
	}

	public boolean isAudioUnmute() {
		return audioUnmute;
	}

	public void setAudioUnmute(boolean audioUnmute) {
		this.audioUnmute = audioUnmute;
	}

	public boolean isPasswordEnable(int sessId) {
		return passwordEnable[sessId];
	}

	public void setPasswordEnable(boolean passwordEnable, int sessId) {
		this.passwordEnable[sessId] = passwordEnable;
	}

	public boolean isPasswordDisable(int sessId) {
		return passwordDisable[sessId];
	}

	public void setPasswordDisable(boolean passwordDisable, int sessId) {
		this.passwordDisable[sessId] = passwordDisable;
	}

	public int getSessionInitiation(int sessId) {
		return sessionInitiation[sessId];
	}

	public void setSessionInitiation(int sessionInitiation, int sessId) {
		this.sessionInitiation[sessId] = sessionInitiation;
	}

	public int getEncodingResolution(int sessId) {
		return encodingResolution[sessId];
	}

	public void setEncodingResolution(int encodingResolution, int sessId) {
		this.encodingResolution[sessId] = encodingResolution;
	}

	public int getEncodingFramerate(int sessId) {
		return encodingFramerate[sessId];
	}

	public void setEncodingFramerate(int encodingFramerate, int sessId) {
		this.encodingFramerate[sessId] = encodingFramerate;
	}

	public int getBitrate(int sessId) {
		return bitrate[sessId];
	}

	public void setBitrate(int bitrate, int sessId) {
		this.bitrate[sessId] = bitrate;
	}

	public VideoEncProfile getStreamProfile(int sessId) {
		return streamProfile[sessId];
	}

	public void setStreamProfile(VideoEncProfile streamProfile, int sessId) {
		this.streamProfile[sessId] = streamProfile;
	}

	public int getStreamingBuffer(int sessId) {
		return streamingBuffer[sessId];
	}

	public void setStreamingBuffer(int streamingBuffer, int sessId) {
		this.streamingBuffer[sessId] = streamingBuffer;
	}

	public int getTransportMode(int sessId) {
		return transportMode[sessId];
	}

	public void setTransportMode(int transportMode, int sessId) {
		this.transportMode[sessId] = transportMode;
	}

	public int getVolume() {
		return volume;
	}

	public void setVolume(int volume) {
		this.volume = volume;
	}

	public int getBass() {
		return bass;
	}

	public void setBass(int bass) {
		this.bass = bass;
	}

	public int getTreble() {
		return treble;
	}

	public void setTreble(int treble) {
		this.treble = treble;
	}

	public String getServerUrl(int sessId) {
		return serverUrl[sessId];
	}

	public void setServerUrl(String serverUrl, int sessId) {
		this.serverUrl[sessId] = serverUrl;
	}

	public String getInitiatorAddress() {
		return initiatorAddress;
	}

	public void setInitiatorAddress(String initiatorAddress) {
		this.initiatorAddress = initiatorAddress;
	}

	public String getMulticastAddress(int sessId) {
		return multicastAddress[sessId];
	}

	public void setMulticastAddress(String multicastAddress, int sessId) {
		this.multicastAddress[sessId] = multicastAddress;
	}

	public String getUserName(int sessId) {
		return userName[sessId];
	}

	public void setUserName(String userName, int sessId) {
		this.userName[sessId] = userName;
	}

	public String getPassword(int sessId) {
		return password[sessId];
	}

	public void setPassword(String password, int sessId) {
		this.password[sessId] = password;
	}

	public boolean isOsdEnable() {
		return osdEnable;
	}

	public void setOsdEnable(boolean osdEnable) {
		this.osdEnable = osdEnable;
	}

	public boolean isOsdDisable() {
		return osdDisable;
	}

	public void setOsdDisable(boolean osdDisable) {
		this.osdDisable = osdDisable;
	}

	public int getOsdLocation() {
		return osdLocation;
	}

	public void setOsdLocation(int osdLocation) {
		this.osdLocation = osdLocation;
	}

	public int getOsdXLocation() {
		return osdXLocation;
	}

	public void setOsdXLocation(int osdXLocation) {
		this.osdXLocation = osdXLocation;
	}

	public int getOsdYLocation() {
		return osdYLocation;
	}

	public void setOsdYLocation(int osdYLocation) {
		this.osdYLocation = osdYLocation;
	}

	public String getOsdText() {
		return osdText;
	}

	public void setOsdText(String osdText) {
		this.osdText = osdText;
	}

	public int getStreamInHorizontalResolution() {
		return streamInHorizontalResolution;
	}

	public void setStreamInHorizontalResolution(int streamInHorizontalResolution) {
		this.streamInHorizontalResolution = streamInHorizontalResolution;
	}

	public int getStreamInVerticalResolution() {
		return streamInVerticalResolution;
	}

	public void setStreamInVerticalResolution(int streamInVerticalResolution) {
		this.streamInVerticalResolution = streamInVerticalResolution;
	}

	public int getStreamInFPS() {
		return streamInFPS;
	}

	public void setStreamInFPS(int streamInFPS) {
		this.streamInFPS = streamInFPS;
	}

	public int getStreamInAspectRatio() {
		return streamInAspectRatio;
	}

	public void setStreamInAspectRatio(int streamInAspectRatio) {
		this.streamInAspectRatio = streamInAspectRatio;
	}

	public int getStreamInAudioFormat() {
		return streamInAudioFormat;
	}

	public void setStreamInAudioFormat(int streamInAudioFormat) {
		this.streamInAudioFormat = streamInAudioFormat;
	}

	public int getStreamInAudioChannels() {
		return streamInAudioChannels;
	}

	public void setStreamInAudioChannels(int streamInAudioChannels) {
		this.streamInAudioChannels = streamInAudioChannels;
	}

	public int getStreamOutHorizontalResolution() {
		return streamOutHorizontalResolution;
	}

	public void setStreamOutHorizontalResolution(int streamOutHorizontalResolution) {
		this.streamOutHorizontalResolution = streamOutHorizontalResolution;
	}

	public int getStreamOutVerticalResolution() {
		return streamOutVerticalResolution;
	}

	public void setStreamOutVerticalResolution(int streamOutVerticalResolution) {
		this.streamOutVerticalResolution = streamOutVerticalResolution;
	}

	public int getStreamOutFPS() {
		return streamOutFPS;
	}

	public void setStreamOutFPS(int streamOutFPS) {
		this.streamOutFPS = streamOutFPS;
	}

	public int getStreamOutAspectRatio() {
		return streamOutAspectRatio;
	}

	public void setStreamOutAspectRatio(int streamOutAspectRatio) {
		this.streamOutAspectRatio = streamOutAspectRatio;
	}

	public int getStreamOutAudioFormat() {
		return streamOutAudioFormat;
	}

	public void setStreamOutAudioFormat(int streamOutAudioFormat) {
		this.streamOutAudioFormat = streamOutAudioFormat;
	}

	public int getStreamOutAudioChannels() {
		return streamOutAudioChannels;
	}

	public void setStreamOutAudioChannels(int streamOutAudioChannels) {
		this.streamOutAudioChannels = streamOutAudioChannels;
	}

	public boolean isStatisticsEnable() {
		return statisticsEnable;
	}

	public void setStatisticsEnable(boolean statisticsEnable) {
		this.statisticsEnable = statisticsEnable;
	}

	public boolean isStatisticsDisable() {
		return statisticsDisable;
	}

	public void setStatisticsDisable(boolean statisticsDisable) {
		this.statisticsDisable = statisticsDisable;
	}

	public int getRtspPort(int sessId) {
		return rtspPort[sessId];
	}

	public void setRtspPort(int rtspPort, int sessId) {
		this.rtspPort[sessId] = rtspPort;
	}

	public int getTsPort(int sessId) {
		return tsPort[sessId];
	}

	public void setTsPort(int tsPort, int sessId) {
		this.tsPort[sessId] = tsPort;
	}

	public int getRtpVideoPort(int sessId) {
		return rtpVideoPort[sessId];
	}

	public void setRtpVideoPort(int rtpVideoPort, int sessId) {
		this.rtpVideoPort[sessId] = rtpVideoPort;
	}

	public int getRtpAudioPort(int sessId) {
		return rtpAudioPort[sessId];
	}

	public void setRtpAudioPort(int rtpAudioPort, int sessId) {
		this.rtpAudioPort[sessId] = rtpAudioPort;
	}

	public int getEncodingLevel(int sessId) {
		return encodingLevel[sessId];
	}

}