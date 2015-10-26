package com.crestron.txrxservice;

import java.util.HashMap;
import java.util.Map;

import com.crestron.txrxservice.CresStreamCtrl.StreamState;
import com.crestron.txrxservice.CresStreamCtrl.DeviceMode;

import android.util.Log;

public class UserSettings
{
	private final int CurrentVersionNumber = 1;
	public enum VideoEncProfile 
    {
		BP(1), MP(2), HP(8);
    	
        private final int value;

        VideoEncProfile(int value) 
        {
            this.value = value;
        }

        public int getVEncProfile() 
        {
            return value;
        }
        
        public int getVEncProfileUserEnum() 
        {
            // values from spreadsheet
        	switch (value) {
        	case 8:
        		return 0;	//High
        	case 2:
        		return 1;	//Main
        	case 1:
        		return 2;	//Baseline
        	default:
        		return -1;	//Error
        	}
        }
        
        public static VideoEncProfile fromInteger(int i) 
        {
        	// values from spreadsheet
        	switch (i) {
        	case 0:
        		return HP;
        	case 1:
        		return MP;
        	case 2:
        	default:
        		return BP;
        	}
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

	//Version info
	private int versionNum;
	
	// MultiWindow joins	
	private int[] mode;
	private int[] xloc;
	private int[] yloc;
	private int[] w;
	private int[] h;
	private CresStreamCtrl.StreamState[] streamState;
	private CresStreamCtrl.StreamState[] userRequestedStreamState;
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
	private boolean[] statisticsEnable;
	private boolean[] statisticsDisable;
	
	// Top Slot
	private int deviceReady; // TODO: needed?	
	private boolean audioMute;
	private boolean audioUnmute;
	private int volume;
	private int userRequestedVolume;
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
	
	// HDMI OUT
	private boolean hdmiOutForceHdcp;

	// Ethernet
	private String deviceIp;

	//Misc
	private boolean[] useNewSink;
	private boolean ravaMode;

	public UserSettings()
	{
        MiscUtils.getDeviceIpAddr();
        deviceIp 			= MiscUtils.matcher.group();
        versionNum 			= CurrentVersionNumber;
		rtspPort 			= new int[]{ 554, 554};		
        tsPort 				= new int[]{ 4570, 4570};		
        rtpVideoPort 		= new int[]{ 49170, 49170};
        rtpAudioPort 		= new int[]{ 49172, 49172};
        mode				= new int[]{0,0};
        w 					= new int[]{1920,1920};
        h					= new int[]{1080,1080};
        xloc 				= new int[]{0,0};
        yloc				= new int[]{0,0};
        encodingResolution	= new int[] {0, 0};
        encodingFramerate	= new int[] {30, 30};
        bitrate				= new int[]{25000, 25000}; 
        //mode 	= mode.RTSP;//RTSP;
        sessionInitiation 	= new int[] {0, 0};
        streamProfile 		= new VideoEncProfile[] {VideoEncProfile.HP, VideoEncProfile.HP};
        encodingLevel		= new int[] {8192, 8192};
        multicastAddress 	= new String[] {"0.0.0.0", "0.0.0.0"};
        serverUrl 	 		= new String[]{"", ""};
        userName 			= new String[] {"", ""};
        password   			= new String[] {"", ""};
        streamState			= new CresStreamCtrl.StreamState[] {StreamState.STOPPED, StreamState.STOPPED};
        userRequestedStreamState = new CresStreamCtrl.StreamState[] {StreamState.STOPPED, StreamState.STOPPED};
        transportMode		= new int[] {0, 0};
        passwordEnable		= new boolean[] {false, false};
        passwordDisable		= new boolean[] {true, true};
        streamingBuffer		= new int[] {250, 250};
        statisticsEnable	= new boolean[] {false, false};
    	statisticsDisable	= new boolean[] {true, true};
    	volume 				= 100;
    	userRequestedVolume = 100;
    	useNewSink			= new boolean[] {true, true};
    	audioMute			= false;
    	audioUnmute			= true;
    	ravaMode			= false;
    	hdmiOutForceHdcp 	= false;
    	initiatorAddress 	= "";
    	osdText 			= "";
	}
	
	public String getDeviceIp() {
		return deviceIp;
	}
	
	public void setDeviceIp(String newIpAddr) {
		deviceIp = newIpAddr;
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
	
	public CresStreamCtrl.StreamState getUserRequestedStreamState(int sessId) {
		return userRequestedStreamState[sessId];
	}
	
	public void setUserRequestedStreamState(CresStreamCtrl.StreamState streamState, int sessId) {
		this.userRequestedStreamState[sessId] = streamState;
		synchronized ( CresStreamCtrl.saveSettingsPendingUpdate ) {  
        	CresStreamCtrl.saveSettingsUpdateArrived = true;        
            CresStreamCtrl.saveSettingsPendingUpdate.notify();
        }
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
	
	public int getUserRequestedVolume() {
		return userRequestedVolume;
	}

	public void setUserRequestedVolume() {
		setUserRequestedVolume(this.volume);
	}
	
	public void setUserRequestedVolume(int volume) {
		this.userRequestedVolume = volume;
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
		
		if (this.mode[sessId] == DeviceMode.STREAM_IN.ordinal())
			StreamIn.setServerUrl(serverUrl, sessId);		
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
	
	public boolean isHdmiOutForceHdcp() {
		return hdmiOutForceHdcp;
	}
	
	public void setHdmiOutForceHdcp(boolean enabled) {
		this.hdmiOutForceHdcp = enabled;
	}

	public boolean isStatisticsEnable(int sessId) {
		return statisticsEnable[sessId];
	}

	public void setStatisticsEnable(boolean statisticsEnable, int sessId) {
		this.statisticsEnable[sessId] = statisticsEnable;
		
		if ((this.mode[sessId] == DeviceMode.STREAM_IN.ordinal()) && (statisticsEnable))
			StreamIn.setStatistics(statisticsEnable, sessId);
	}

	public boolean isStatisticsDisable(int sessId) {
		return statisticsDisable[sessId];
	}

	public void setStatisticsDisable(boolean statisticsDisable, int sessId) {
		this.statisticsDisable[sessId] = statisticsDisable;
		
		if ((this.mode[sessId] == DeviceMode.STREAM_IN.ordinal()) && (statisticsDisable))
			StreamIn.setStatistics(!statisticsDisable, sessId);
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

	public void setNewSink(boolean useNewSink, int sessId) {
		this.useNewSink[sessId] = useNewSink;
	}

	public boolean isNewSink(int sessId) {
		return useNewSink[sessId];
	}
	
	public void setRavaMode(boolean enabled) {
		this.ravaMode = enabled;
	}
	
	public boolean isRavaMode() {
		return ravaMode;
	}
}
