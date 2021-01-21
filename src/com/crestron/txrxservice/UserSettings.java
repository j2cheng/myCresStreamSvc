package com.crestron.txrxservice;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.Set;
import java.util.HashSet;

import com.crestron.airmedia.receiver.m360.ipc.AirMediaSize;
import com.crestron.txrxservice.CresStreamCtrl.StreamState;
import com.crestron.txrxservice.CresStreamCtrl.DeviceMode;

import android.util.Log;
import android.view.WindowManager;

public class UserSettings
{
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
	private int[] z;
	private int[] stretch;
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
	private String[] streamOutUrl;
	private String[] streamInUrl;
	private String[] streamServerUrl;
	private String[] wbsStreamUrl;
	private boolean[] proxyEnable;
	private int[]	decodeInternalRtspPort;
	private int[] internalRtspPort;
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
	private double volume;
	private double userRequestedVolume;
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
	private String rtspStreamFileName;
	private String rtspSessionName;
	private int keyFrameInterval_ms;

	// HDMI OUT
	private boolean hdmiOutForceHdcp;
	private int hdmiOutUnderscan;

	// Ethernet
	private String deviceIp;
	private String auxiliaryIp;
	private String wifiIp;
	private int multicastTTL;
	
	// AirMedia
	private boolean[] airMediaLaunch;
	private boolean airMediaEnable;
	private int airMediaLoginCode;
	private int airMediaLoginMode;
	private boolean airMediaModerator;
	private boolean airMediaProjectionLock;
	private boolean airMediaResetConnections;
	private boolean[] airMediaDisconnectUser;
	private boolean[] airMediaStartUser;
	private boolean[] airMediaUserConnected;
	private int[] airMediaUserPosition;
	private boolean[] airMediaStopUser;
	boolean airMediaIsCertificateRequired;
	boolean airMediaOnlyAllowSecureConnections;
	boolean airMediaSecureLandingPageEnabled;
	boolean airMediaChromeExtension;
	boolean airMediaDiscoveryEnable;
	boolean airMediaWifiEnabled;
	int airMediaMaxMiracastBitrate;
	private String airMediaOsdImage;
	private boolean airMediaDisplayConnectionOptionEnable;
	private int airMediaDisplayConnectionOption;
	private String airMediaCustomPromptString;
	private boolean airMediaConnectionOverlay;
	private boolean airMediaDisplayWirelessConnectionOptionEnable;
	private int airMediaDisplayWirelessConnectionOption;
	private String airMediaWirelessCustomPromptString;
	private int airMediaX;
	private int airMediaY;
	private int airMediaWidth;
	private int airMediaHeight;
	private String airMediaLayoutPassword;
	private boolean airMediaDisplayLoginCodeNew;
	private int airMediaDisplayScreen;
	private int airMediaWindowFlag;
	private Set<String> airMediaAdapters = new HashSet<String>(20);
	private boolean airMediaMiracastEnable;
	private boolean airMediaMiracastWifiDirectMode;
	private boolean airMediaMiracastPreferWifiDirect;
	private int airMediaMiracastWirelessOperatingRegion;
	private boolean airMediaMiracastMsMiceMode;
	private String airMediaWifiSsid;
	private transient String airMediaWifiPskKey; // transient keyword prevents serialization of field when saving 
	private int airMediaWifiFrequencyBand;
	private boolean airMediaWifiAutoLaunchAirMediaLandingPageEnable;
	private int airMediaInactivityTimeout;

	// Camera Streaming
	private boolean camStreamEnable;
	private boolean camStreamMulticastEnable;
	private int camStreamResolution;
	private int camStreamFrameRate;
	private int camStreamPort;
	private int camStreamBitrate;
	private int camStreamIFrameInterval;
	private String camStreamUrl;
	private String camStreamSnapshotUrl;
	private String camStreamName;
	private String camStreamSnapshotName;
	private String camStreamMulticastAddress;
	private boolean wcSecurityEnable;
	
	//Misc
	private boolean[] useNewSink;
	private boolean ravaMode;
	private boolean processHdmiInAudio;
	private int[] tcpInterleave;
	private boolean appspaceEnabled;
	private int chromaKeyColor;
	private boolean rgb888Enabled;
	private String domainName;
	
	//service mode
	private int serviceMode;
	
	// canvas mode
	boolean canvasModeEnabled;
	
	// DM in products with canvas mode
	private boolean[] dmSync;
	private boolean[] dmHdcpBlank;
	private CresStreamCtrl.Resolution[] dmResolution;

	public UserSettings()
	{
//		MiscUtils.getDeviceIpAddr();
		deviceIp 			= "0.0.0.0";//MiscUtils.matcher.group();
		auxiliaryIp			= "0.0.0.0";
		wifiIp              = "0.0.0.0";
		versionNum 			= CresStreamCtrl.VersionNumber;
		rtspPort 			= initIntArray(554);		
		tsPort 				= initIntArray(4570);		
		rtpVideoPort 		= initIntArray(49170);
		rtpAudioPort 		= initIntArray(49172);
		mode				= initDeviceMode();
		w 					= initIntArray(1920);
		h					= initIntArray(1080);
		z					= initZOrder();
		xloc 				= initIntArray(0);
		yloc				= initIntArray(0);
		stretch				= initIntArray(0);
		encodingResolution	= initIntArray(0);
		encodingFramerate	= initIntArray(60);
		bitrate				= initIntArray(10000);
		//mode 	= mode.RTSP;//RTSP;
		sessionInitiation 	= initIntArray(0);
		streamProfile 		= initStreamProfile(VideoEncProfile.HP);
		encodingLevel		= initIntArray(8192);
		multicastAddress 	= initStringArray("");
		streamOutUrl 	 	= initStringArray("");
		streamInUrl			= initStringArray("");
		streamServerUrl	 	= initStringArray("");
		wbsStreamUrl	 	= initStringArray("");
        proxyEnable         = initBoolArray(false);
        decodeInternalRtspPort = initIntArray(0);
        internalRtspPort 	= initIntArray(5540);
		userName 			= initStringArray("");
		password   			= initStringArray("");
		streamState			= initStreamState(StreamState.STOPPED);
		userRequestedStreamState = initStreamState(StreamState.STOPPED);
		transportMode		= initIntArray(1);  // Bug 111632: set default transport mode to MPEG TS RTP
		passwordEnable		= initBoolArray(false);
		passwordDisable		= initBoolArray(true);
		streamingBuffer		= initIntArray(1000);
		statisticsEnable	= initBoolArray(false);
		statisticsDisable	= initBoolArray(true);
		volume 				= 100.0;
		userRequestedVolume = 100.0;
		useNewSink			= initBoolArray(true);
		audioMute			= false;
		audioUnmute			= true;
		ravaMode			= false;
		processHdmiInAudio  = true; 
		hdmiOutForceHdcp 	= false;
		hdmiOutUnderscan    = 0;
		initiatorAddress 	= "";
		osdText 			= "";
		osdEnable           = false;
		osdDisable          = true;
		osdLocation         = 1;  // Upper left
		osdXLocation        = 0;
		osdYLocation        = 0;
		keyFrameInterval_ms    = 1000;
		rtspStreamFileName	= "live.sdp";
		rtspSessionName		= "Crestron Streaming";
		multicastTTL		= 64;
		airMediaLaunch		= initBoolArray(false);
		airMediaEnable      = true;
		airMediaLoginMode	= CresStreamCtrl.AirMediaLoginMode.Random.ordinal();
		airMediaLoginCode	= 1234; //Get default value
		airMediaProjectionLock = false;
		airMediaDisconnectUser = initBoolArray(false, 32);
		airMediaStartUser 	= initBoolArray(false, 32);
		airMediaUserPosition = initIntArray(0, 32);
		airMediaStopUser 	= initBoolArray(true, 32);
		airMediaUserConnected = initBoolArray(false, 32);
		airMediaIsCertificateRequired = false;
		airMediaOnlyAllowSecureConnections = false;
		airMediaSecureLandingPageEnabled = false;
		airMediaChromeExtension = true;
		airMediaDiscoveryEnable = true;
		airMediaWifiEnabled = true;
		airMediaMaxMiracastBitrate = 10000000;
		airMediaOsdImage	= "";
		airMediaX			= 0;
		airMediaY			= 0;
		airMediaWidth		= 1920;
		airMediaHeight		= 1080;
		airMediaLayoutPassword = "";
		airMediaDisplayLoginCodeNew = true;
		airMediaDisplayScreen = 0;
		airMediaWindowFlag	= WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY;
		airMediaAdapters.add("eth0");
		airMediaDisplayConnectionOptionEnable = true;
		airMediaDisplayConnectionOption = CresStreamCtrl.AirMediaDisplayConnectionOption.Ip;
		airMediaCustomPromptString = "";
		airMediaConnectionOverlay = false;
		airMediaDisplayWirelessConnectionOptionEnable = true;
		airMediaDisplayWirelessConnectionOption = CresStreamCtrl.AirMediaDisplayConnectionOption.Ip;
		airMediaWirelessCustomPromptString = "";
		airMediaMiracastEnable = false;
		airMediaMiracastWifiDirectMode = true;
		airMediaMiracastPreferWifiDirect = false; 
		airMediaMiracastWirelessOperatingRegion = 0;
		airMediaMiracastMsMiceMode = true;
		airMediaWifiSsid = "";
		airMediaWifiPskKey = "";
		airMediaWifiFrequencyBand = 1; // 5 Ghz
		airMediaWifiAutoLaunchAirMediaLandingPageEnable = true;
		airMediaInactivityTimeout = 45;
		appspaceEnabled		= false;
		tcpInterleave       = initIntArray(0);//auto mode
		camStreamEnable		= false;
		camStreamMulticastEnable = false;
		camStreamResolution = 10;
		camStreamFrameRate	= 15;
		camStreamPort		= 8554;
		camStreamBitrate    = 4194304;        //todo: 4Mbps as temp default
		camStreamIFrameInterval= 8;          //todo: will be overwritten by the getfunction
		camStreamUrl		= "";
		camStreamSnapshotUrl = "";
		camStreamName		= "camera";
		camStreamSnapshotName = "snapshot";
		camStreamMulticastAddress = "";
		wcSecurityEnable = true;
		serviceMode = CresStreamCtrl.ServiceMode.Master.ordinal();
		canvasModeEnabled = false;
		dmSync = initBoolArray(false,CresStreamCtrl.NumDmInputs);
		dmHdcpBlank = initBoolArray(false, CresStreamCtrl.NumDmInputs);
		dmResolution = new CresStreamCtrl.Resolution[CresStreamCtrl.NumDmInputs];
		chromaKeyColor = 0xff0000;
		rgb888Enabled = true;
		domainName = "";
	}
	
	// If there is a version mismatch between current userSettings and the one loaded from file system
	// Copy over user data while keeping all new data at default values
	public static void fixVersionMismatch(UserSettings storedSettings, UserSettings currentSettings)
	{
		// Loop over all fields saved in storedSettings
		for (Field storedField : storedSettings.getClass().getDeclaredFields()) 
		{
			if (!storedField.getName().equals("versionNum")) // Do not replace new versionNum with previous value
			{
				try {
					Field updatedField = currentSettings.getClass().getDeclaredField(storedField.getName());

					if (storedField.getType() == updatedField.getType())	//make sure both fields are of same type
					{
						updatedField.setAccessible(true); // Allow modifying private fields

						// If object is array copy, else use set function
						if (updatedField.getType().isArray())
						{
							int length = 0;
							Object storedArray = storedField.get(storedSettings);
							Object currentArray = updatedField.get(currentSettings);
							if (Array.getLength(currentArray) < Array.getLength(storedArray)) // make sure we aren't copying more than we have stored
								length = Array.getLength(currentArray);
							else
								length = Array.getLength(storedArray);
							System.arraycopy(storedArray, 0, updatedField.get(currentSettings), 0, length);
						}
						else
						{
							Object fieldValue = storedField.get(storedSettings);
							updatedField.set(currentSettings,fieldValue);
						}

						updatedField.setAccessible(false); // Disallow modifying private fields
					}
				}
				catch (NoSuchFieldException e) {} //Field doesn't exist, move on to next field
				catch (Exception e)
				{
					Log.e("UserSettings", "Failed to copy over field " + storedField.getName() + "! " + e); //TODO: make better
				} 
			}
		}
	}

	private boolean[] initBoolArray(boolean initValue)
	{
		return initBoolArray(initValue, CresStreamCtrl.NumOfSurfaces);
	}
	
	private boolean[] initBoolArray(boolean initValue, int arraySize)
	{
		boolean[] retArray = new boolean[arraySize];
		for (int i = 0; i < arraySize; i++)
		{
			retArray[i] = initValue;
		}
		return retArray;
	}

	private int[] initIntArray(int initValue)
	{
		return initIntArray(initValue, CresStreamCtrl.NumOfSurfaces);
	}
	
	private int[] initIntArray(int initValue, int arraySize)
	{
		int[] retArray = new int[arraySize];
		for (int i = 0; i < arraySize; i++)
		{
			retArray[i] = initValue;
		}
		return retArray;
	}

	private String[] initStringArray(String initValue)
	{
		return initStringArray(initValue, CresStreamCtrl.NumOfSurfaces);
	}

	private String[] initStringArray(String initValue, int arraySize)
	{
		String[] retArray = new String[arraySize];
		for (int i = 0; i < arraySize; i++)
		{
			retArray[i] = initValue;
		}
		return retArray;
	}
	
	private int[] initDeviceMode()
	{
		// Bug 105751: make default mode transmitter, other ids should be 0
		int[] retArray = new int[CresStreamCtrl.NumOfSurfaces];
		for (int i = 0; i < CresStreamCtrl.NumOfSurfaces; i++)
		{
			if (i == 0)
				retArray[i] = 1;
			else
				retArray[i] = 0;
		}
		return retArray;
	}
	
	private int[] initZOrder()
	{
		// Init z order with ascending z order values
		int[] retArray = new int[CresStreamCtrl.NumOfSurfaces];
		for (int i = 0; i < CresStreamCtrl.NumOfSurfaces; i++)
		{
			retArray[i] = i + 1;
		}
		return retArray;
	}
	
	private VideoEncProfile[] initStreamProfile(VideoEncProfile initValue)
	{
		VideoEncProfile[] retArray = new VideoEncProfile[CresStreamCtrl.NumOfSurfaces];
		for (int i = 0; i < CresStreamCtrl.NumOfSurfaces; i++)
		{
			retArray[i] = initValue;
		}
		return retArray;
	}
	
	private CresStreamCtrl.StreamState[] initStreamState(CresStreamCtrl.StreamState initValue)
	{
		CresStreamCtrl.StreamState[] retArray = new CresStreamCtrl.StreamState[CresStreamCtrl.NumOfSurfaces];
		for (int i = 0; i < CresStreamCtrl.NumOfSurfaces; i++)
		{
			retArray[i] = initValue;
		}
		return retArray;
	}	
	
	public int getVersionNum() {
		return versionNum;
	}

	public String getDeviceIp() {
		return deviceIp;
	}

	public void setDeviceIp(String newIpAddr) {
		deviceIp = newIpAddr;
	}
	
	public String getDomainName() {
		return domainName;
	}

	public void setDomainName(String newDomainName) {
		domainName = newDomainName;
	}
	
	public String getAuxiliaryIp() {
		return auxiliaryIp;
	}

	public void setAuxiliaryIp(String auxiliaryIp) {
		this.auxiliaryIp = auxiliaryIp;
	}

	public String getWifiIp() {
		return wifiIp;
	}
	
	public void setWifiIp(String wifiIp) {
		this.wifiIp = wifiIp;
	}
	
	public int getMulticastTTL() {
		return multicastTTL;
	}
	
	public void setMulticastTTL(int value) {
		this.multicastTTL = value;
	}
	
	public int getMode(int sessId) {
		return mode[sessId];
	}

	public void setMode(int mode, int sessId) {
		this.mode[sessId] = mode;
	}
	
	public int getStretchVideo(int sessId) {
		return stretch[sessId];
	}
	
	public void setStretchVideo(int stretch, int sessId) {
		this.stretch[sessId] = stretch;
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

	public int getZ(int sessId) { 
		return z[sessId];
	}

	public void setZ(int z, int sessId) {
		this.z[sessId] = z;
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
		CresStreamCtrl.saveSettingsUpdateArrived = true;        
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

	public double getVolume() {
		return volume;
	}

	public void setVolume(double volume) {
		this.volume = volume;
	}

	public double getUserRequestedVolume() {
		return userRequestedVolume;
	}

	public void setUserRequestedVolume() {
		setUserRequestedVolume(this.volume);
	}

	public void setUserRequestedVolume(double volume) {
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

	public String getStreamInUrl(int sessId) {
		return streamInUrl[sessId];
	}

	public boolean getProxyEnable(int sessId) {
		return proxyEnable[sessId];
	}
	
	public void setStreamInUrl(String url, int sessId) {
		this.streamInUrl[sessId] = url;
		String newUrl = url;

		if (this.mode[sessId] == DeviceMode.STREAM_IN.ordinal())
		{
			//Need to modify url function if proxy enable
			if(this.getProxyEnable(sessId))
			{
	    		newUrl = MiscUtils.getLocalUrl(url, getDecodeInternalRtspPort(sessId));
			}

			GstreamIn.setServerUrl(newUrl, sessId);
		}
	}
	
	public void setProxyEnable(boolean flag, int sessId) {
		this.proxyEnable[sessId] = flag;	
	}
	
	public int getDecodeInternalRtspPort(int sessId) {
		return this.decodeInternalRtspPort[sessId];
	}
	
	public void setDecodeInternalRtspPort(int rtspPort, int sessId) {
		this.decodeInternalRtspPort[sessId] = rtspPort;
	}

	public int getInternalRtspPort(int sessId) {
		return internalRtspPort[sessId];
	}

	public void setInternalRtspPort(int rtspPort, int sessId) {
		this.internalRtspPort[sessId] = rtspPort;
	}
		
	public String getStreamOutUrl(int sessId) {
		return streamOutUrl[sessId];
	}

	public void setStreamOutUrl(String url, int sessId) {
		this.streamOutUrl[sessId] = url;	
	}	

	public String getStreamServerUrl(int sessId) {
		return streamServerUrl[sessId];
	}

	public void setStreamServerUrl(String url, int sessId) {
		this.streamServerUrl[sessId] = url;	
	}	

	public String getWbsStreamUrl(int sessId) {
		return wbsStreamUrl[sessId];
	}

	public void setWbsStreamUrl(String wbsStreamUrl, int sessId) {
		this.wbsStreamUrl[sessId] = wbsStreamUrl;
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

	public int getKeyFrameInterval() {
		return keyFrameInterval_ms;
	}

	public void setKeyFrameInterval(int keyFrameInterval) {
		this.keyFrameInterval_ms = keyFrameInterval;
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

	public void SetRtspStreamFileName(String fileName){
		rtspStreamFileName      = fileName;
	}

	public String getRtspStreamFileName() {
		return rtspStreamFileName;
	}

	public void SetRtspSessionName(String sessionName){
		rtspSessionName         = sessionName;
	}

	public String getRtspSessionName() {
		return rtspSessionName;
	}

	public boolean isHdmiOutForceHdcp() {
		return hdmiOutForceHdcp;
	}

	public void setHdmiOutForceHdcp(boolean enabled) {
		this.hdmiOutForceHdcp = enabled;
	}

	public void setHdmiOutUnderscan(int percent) {
		this.hdmiOutUnderscan = percent;
	}

	public int getHdmiOutUnderscan() {
		return hdmiOutUnderscan;
	}
	
	public boolean isStatisticsEnable(int sessId) {
		return statisticsEnable[sessId];
	}

	public void setStatisticsEnable(boolean statisticsEnable, int sessId) {
		this.statisticsEnable[sessId] = statisticsEnable;

		if ((this.mode[sessId] == DeviceMode.STREAM_IN.ordinal()) && (statisticsEnable))
			GstreamIn.setStatistics(statisticsEnable, sessId);
	}

	public boolean isStatisticsDisable(int sessId) {
		return statisticsDisable[sessId];
	}

	public void setStatisticsDisable(boolean statisticsDisable, int sessId) {
		this.statisticsDisable[sessId] = statisticsDisable;

		if ((this.mode[sessId] == DeviceMode.STREAM_IN.ordinal()) && (statisticsDisable))
			GstreamIn.setStatistics(!statisticsDisable, sessId);
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
	
	public void setProcessHdmiInAudio(boolean enabled) {
		this.processHdmiInAudio = enabled;
	}

	public boolean isProcessHdmiInAudio() {
		return processHdmiInAudio;
	}
	
	public boolean isAppspaceEnabled() {
		return appspaceEnabled;
	}
	
	public void setAppspaceEnabled(boolean isEnabled) {
		this.appspaceEnabled= isEnabled; 
	}

	public int getTcpInterleave(int sessId) {		
		return tcpInterleave[sessId];
	}
	
	public void setTcpInterleave(int tcpIn,int sessId) {	
		//TODO: as of now command allways set is to 0.
		//so we have to set all windows to the same mode for now
		for (int i = 0; i < CresStreamCtrl.NumOfSurfaces; i++)
		this.tcpInterleave[i] = tcpIn;
	}
	
	public boolean getAirMediaLaunch(int sessId) {
		return airMediaLaunch[sessId];
	}
	
	public void setAirMediaLaunch(boolean enable, int sessId) {
		this.airMediaLaunch[sessId] = enable;
	}
	
	public boolean getAirMediaEnable() {
		return airMediaEnable;
	}
	
	public void setAirMediaEnable(boolean enable) {
		this.airMediaEnable = enable;
	}
	
	public int getAirMediaLoginCode() {
		return airMediaLoginCode;
	}
	
	public void setAirMediaLoginCode(int loginCode) {
		this.airMediaLoginCode = loginCode;
	}
	
	public int getAirMediaLoginMode() {
		return airMediaLoginMode;
	}
	
	public void setAirMediaLoginMode(int loginMode) {
		this.airMediaLoginMode = loginMode;
	}
	
	public boolean getAirMediaModerator() {
		return airMediaModerator;
	}
	
	public void setAirMediaModerator(boolean enable) {
		this.airMediaModerator = enable;
	}
	
	public boolean getAirMediaProjectionLock() {
		return airMediaProjectionLock;
	}
	
	public void setAirMediaProjectionLock(boolean enable) {
		this.airMediaProjectionLock = enable;
	}	

	public boolean getAirMediaIsCertificateRequired() {
		return airMediaIsCertificateRequired;
	}
	
	public void setAirMediaIsCertificateRequired(boolean enable) {
		this.airMediaIsCertificateRequired = enable;
	}	

	public boolean getAirMediaOnlyAllowSecureConnections() {
		return airMediaOnlyAllowSecureConnections;
	}
	
	public void setAirMediaOnlyAllowSecureConnections(boolean enable) {
		this.airMediaOnlyAllowSecureConnections = enable;
	}	

	public boolean getAirMediaSecureLandingPageEnabled() {
		return airMediaSecureLandingPageEnabled;
	}
	
	public void setAirMediaSecureLandingPageEnabled(boolean enable) {
		this.airMediaSecureLandingPageEnabled = enable;
	}	
	
	public boolean getAirMediaChromeExtension() {
		return airMediaChromeExtension;
	}
	
	public void setAirMediaChromeExtension(boolean enable) {
		this.airMediaChromeExtension = enable;
	}	
	
	public boolean getAirMediaDiscoveryEnable() {
		return airMediaDiscoveryEnable;
	}
	
	public void setAirMediaDiscoveryEnable(boolean enable) {
		this.airMediaDiscoveryEnable = enable;
	}	
	
	public boolean getAirMediaWifiEnabled() {
		return airMediaWifiEnabled;
	}	
	
	public void setAirMediaWifiEnabled(boolean enable) {
		this.airMediaWifiEnabled = enable;
	}	
	
	public void setAirMediaMaxMiracastBitrate(int maxrate) {
		this.airMediaMaxMiracastBitrate = maxrate;
	}
	
	public int getAirMediaMaxMiracastBitrate() {
		return airMediaMaxMiracastBitrate;
	}
	
	public boolean getAirMediaResetConnections() {
		return airMediaResetConnections;
	}

	public void setAirMediaResetConnections(boolean airMediaResetConnections) {
		this.airMediaResetConnections = airMediaResetConnections;
	}

	public boolean getAirMediaDisconnectUser(int userId) {
		userId--; // We want user Id 0 based when saving
		return airMediaDisconnectUser[userId];
	}

	public void setAirMediaDisconnectUser(boolean airMediaDisconnectUser, int userId) {
		userId--; // We want user Id 0 based when saving
		this.airMediaDisconnectUser[userId] = airMediaDisconnectUser;
	}

	public boolean getAirMediaStartUser(int userId) {
		userId--; // We want user Id 0 based when saving
		return airMediaStartUser[userId];
	}

	public void setAirMediaStartUser(boolean airMediaStartUser, int userId) {
		userId--; // We want user Id 0 based when saving
		this.airMediaStartUser[userId] = airMediaStartUser;
	}

	public int getAirMediaUserPosition(int userId) {
		userId--; // We want user Id 0 based when saving
		return airMediaUserPosition[userId];
	}

	public void setAirMediaUserPosition(int airMediaUserPosition, int userId) {
		userId--; // We want user Id 0 based when saving
		this.airMediaUserPosition[userId] = airMediaUserPosition;
	}

	public boolean getAirMediaStopUser(int userId) {
		userId--; // We want user Id 0 based when saving
		return airMediaStopUser[userId];
	}

	public void setAirMediaStopUser(boolean airMediaStopUser, int userId) {
		userId--; // We want user Id 0 based when saving
		this.airMediaStopUser[userId] = airMediaStopUser;
	}
	
	public boolean getAirMediaUserConnected(int userId) {
		userId--; // We want user Id 0 based when saving
		return airMediaUserConnected[userId];
	}
	
	public void setAirMediaUserConnected(boolean userConnected, int userId) {
		userId--; // We want user Id 0 based when saving
		this.airMediaUserConnected[userId] = userConnected;
	}

	public String getAirMediaOsdImage() {
		return airMediaOsdImage;
	}

	public void setAirMediaOsdImage(String airMediaOsdImage) {
		this.airMediaOsdImage = airMediaOsdImage;
	}

	public boolean getAirMediaDisplayConnectionOptionEnable() {
		return airMediaDisplayConnectionOptionEnable;
	}

	public void setAirMediaDisplayConnectionOptionEnable(boolean enable) {
		this.airMediaDisplayConnectionOptionEnable = enable;
	}
	
	public int getAirMediaDisplayConnectionOption() {
		return airMediaDisplayConnectionOption;
	}

	public void setAirMediaDisplayConnectionOption(int airMediaDisplayConnectionOption) {
		this.airMediaDisplayConnectionOption = airMediaDisplayConnectionOption;
	}
	
	public String getAirMediaCustomPromptString() {
		return airMediaCustomPromptString;
	}

	public void setAirMediaCustomPromptString(String airMediaCustomPromptString) {
		this.airMediaCustomPromptString = airMediaCustomPromptString;
	}
	
	public boolean getAirMediaConnectionOverlay() {
		return airMediaConnectionOverlay;
	}
	
	public void setAirMediaConnectionOverlay(boolean enable) {
		this.airMediaConnectionOverlay = enable;
	}
	
	public boolean getAirMediaDisplayWirelessConnectionOptionEnable() {
		return airMediaDisplayWirelessConnectionOptionEnable;
	}

	public void setAirMediaDisplayWirelessConnectionOptionEnable(boolean enable) {
		this.airMediaDisplayWirelessConnectionOptionEnable = enable;
	}
	
	public int getAirMediaDisplayWirelessConnectionOption() {
		return airMediaDisplayWirelessConnectionOption;
	}

	public void setAirMediaDisplayWirelessConnectionOption(int airMediaDisplayConnectionOption) {
		this.airMediaDisplayWirelessConnectionOption = airMediaDisplayConnectionOption;
	}

	public String getAirMediaWirelessCustomPromptString() {
		return airMediaWirelessCustomPromptString;
	}

	public void setAirMediaWirelessCustomPromptString(String airMediaWirelessCustomPromptString) {
		this.airMediaWirelessCustomPromptString = airMediaWirelessCustomPromptString;
	}
	
	public int getAirMediaX() {
		return airMediaX;
	}

	public void setAirMediaX(int airMediaX) {
		this.airMediaX = airMediaX;
	}

	public int getAirMediaY() {
		return airMediaY;
	}

	public void setAirMediaY(int airMediaY) {
		this.airMediaY = airMediaY;
	}

	public int getAirMediaWidth() {
		return airMediaWidth;
	}

	public void setAirMediaWidth(int airMediaWidth) {
		this.airMediaWidth = airMediaWidth;
	}

	public int getAirMediaHeight() {
		return airMediaHeight;
	}

	public void setAirMediaHeight(int airMediaHeight) {
		this.airMediaHeight = airMediaHeight;
	}
	
	public String getAirMediaLayoutPassword() {
		return airMediaLayoutPassword;
	}

	public void setAirMediaLayoutPassword(String airMediaLayoutPassword) {
		this.airMediaLayoutPassword = airMediaLayoutPassword;
	}

	public boolean getAirMediaDisplayLoginCode() {
		return airMediaDisplayLoginCodeNew;
	}

	public void setAirMediaDisplayLoginCode(boolean airMediaDisplayLoginCode) {
		this.airMediaDisplayLoginCodeNew = airMediaDisplayLoginCode;
	}
	
	public void setAirMediaDisplayScreen(int displayScreen) {
		this.airMediaDisplayScreen = displayScreen;
	}

	public int getAirMediaDisplayScreen() {
		return airMediaDisplayScreen;
	}
	
	public void setAirMediaWindowFlag(int windowFlag) {
		this.airMediaWindowFlag = windowFlag;
	}

	public int getAirMediaWindowFlag() {
		return airMediaWindowFlag;
	}

	public Set<String> getAirMediaAdapters() {
		return airMediaAdapters;
	}

	public void setAirMediaAdapters(Set<String> adapters) {
		this.airMediaAdapters = adapters;
	}
	
	public boolean getAirMediaMiracastEnable() {
		return airMediaMiracastEnable;
	}
	
	public void setAirMediaMiracastEnable(boolean enable) {
		this.airMediaMiracastEnable = enable;
	}

	public boolean getAirMediaMiracastMsMiceMode() {
		return airMediaMiracastMsMiceMode;
	}
	
	public void setAirMediaMiracastMsMiceMode(boolean enable) {
		this.airMediaMiracastMsMiceMode = enable;
	}
	
	public boolean getAirMediaMiracastWifiDirectMode() {
		return airMediaMiracastWifiDirectMode;
	}
	
	public void setAirMediaMiracastWifiDirectMode(boolean enable) {
		this.airMediaMiracastWifiDirectMode = enable;
	}
	
	public boolean getAirMediaMiracastPreferWifiDirect() {
		return airMediaMiracastPreferWifiDirect;
	}
	
	public void setAirMediaMiracastPreferWifiDirect(boolean enable) {
		this.airMediaMiracastPreferWifiDirect = enable;
	}
	
	public int getAirMediaMiracastWirelessOperatingRegion() {
		return airMediaMiracastWirelessOperatingRegion;
	}
	
	public void setAirMediaMiracastWirelessOperatingRegion(int value) {
		this.airMediaMiracastWirelessOperatingRegion = value;
	}
	
	public void setAirMediaWifiSsid(String ssid) {
		this.airMediaWifiSsid = ssid;
	}

	public String getAirMediaWifiSsid() {
		return airMediaWifiSsid;
	}
	
	public void setAirMediaWifiPskKey(String key) {
		this.airMediaWifiPskKey = key;
	}

	public String getAirMediaWifiPskKey() {
		return airMediaWifiPskKey;
	}
	
	public void setAirMediaWifiFrequencyBand(int val) {
		this.airMediaWifiFrequencyBand = val;
	}
	
	public int getAirMediaWifiFrequencyBand() {
		return airMediaWifiFrequencyBand;
	}
	
	public void setAirMediaWifiAutoLaunchAirMediaLandingPageEnabled(boolean enable) {
		this.airMediaWifiAutoLaunchAirMediaLandingPageEnable = enable;
	}
	
	public boolean getAirMediaWifiAutoLaunchAirMediaLandingPageEnabled() {
		return airMediaWifiAutoLaunchAirMediaLandingPageEnable;
	}
	
	public void setAirMediaInactivityTimeout(int val) {
		this.airMediaInactivityTimeout = val;
	}
	
	public int getAirMediaInactivityTimeout() {
		return airMediaInactivityTimeout;
	}
	
	public boolean getCamStreamEnable() {
		return camStreamEnable;
	}

	public void setCamStreamEnable(boolean camStreamEnable) {
		this.camStreamEnable = camStreamEnable;
	}
	
	public boolean getCamStreamMulticastEnable() {
		return camStreamMulticastEnable;
	}

	public void setCamStreamMulticastEnable(boolean camStreamMulticastEnable) {
		this.camStreamMulticastEnable = camStreamMulticastEnable;
	}

	public int getCamStreamResolution() {
		return camStreamResolution;
	}

	public void setCamStreamResolution(int camStreamResolution) {
		this.camStreamResolution = camStreamResolution;
	}
	
	public int getCamStreamFrameRate() {
        // Due to bug 150537 we are reducing maximum frame rate for camera to 15
        // which seems to be the maximum supported by the AmLogic in X60 series.
        // Currently this parameter is not user facing but it may be some point in the future
        // Should be removed once userSettings has been updated to 15 
		if (camStreamFrameRate > 15) {
			setCamStreamFrameRate(15);
		}
		return camStreamFrameRate;
	}

	public void setCamStreamFrameRate(int camStreamFrameRate) {
		this.camStreamFrameRate = camStreamFrameRate;
	}
	
	public int getCamStreamPort() {
		return camStreamPort;
	}

	public void setCamStreamPort(int camStreamPort) {
		this.camStreamPort = camStreamPort;
	}
	
	public int getCamStreamBitrate() {
		return camStreamBitrate;
	}

	public void setCamStreamBitrate(int camStreamBitrate) {
		this.camStreamBitrate = camStreamBitrate;
	}
	
	public int getCamStreamIFrameInterval() {
        // Due to bug 150537 we are reducing maximum frame rate for camera to 15
        // which seems to be the maximum supported by the AmLogic in X60 series.
        // Currently this parameter is not user facing but it may be some point in the future
        // This change is to keep the I frame update rate close to twice a second 
		if (camStreamIFrameInterval > Math.round(0.5f*camStreamFrameRate)) {
			setCamStreamIFrameInterval(Math.round(0.5f*camStreamFrameRate));
		}
		return camStreamIFrameInterval;
	}

	public void setCamStreamIFrameInterval(int camStreamIFrameInterval) {
		this.camStreamIFrameInterval = camStreamIFrameInterval;
	}
	
	public String getCamStreamUrl() {
		return camStreamUrl;
	}

	public void setCamStreamUrl(String camStreamUrl) {
		this.camStreamUrl = camStreamUrl;
	}

	public String getCamStreamSnapshotUrl() {
		return camStreamSnapshotUrl;
	}

	public void setCamStreamSnapshotUrl(String camStreamSnapshotUrl) {
		this.camStreamSnapshotUrl = camStreamSnapshotUrl;
	}

	public String getCamStreamName() {
		return camStreamName;
	}

	public void setCamStreamName(String camStreamName) {
		this.camStreamName = camStreamName;
	}

	public String getCamStreamSnapshotName() {
		return camStreamSnapshotName;
	}

	public void setCamStreamSnapshotName(String camStreamSnapshotName) {
		this.camStreamSnapshotName = camStreamSnapshotName;
	}

	public String getCamStreamMulticastAddress() {
		return camStreamMulticastAddress;
	}

	public void setCamStreamMulticastAddress(String camStreamMulticastAddress) {
		this.camStreamMulticastAddress = camStreamMulticastAddress;
	}
	
	public boolean getWcSecurityEnable() {
		return wcSecurityEnable;
	}

	public void setWcSecurityEnable(boolean enable) {
		this.wcSecurityEnable = enable;
	}

	public int getServiceMode() {
		return serviceMode;
	}
	
	public void setServiceMode(int mode) {
		this.serviceMode = mode;
	}
	
	public boolean getCanvasModeEnabled() {
		return canvasModeEnabled;
	}
	
	public void setCanvasModeEnabled(boolean enable) {
		this.canvasModeEnabled = enable;
	}
	
	public boolean getDmSync(int inputNum) {
		if (CresStreamCtrl.NumDmInputs <= 0)
			return false;
		return dmSync[inputNum];
	}
	
	public void setDmSync(boolean enable, int inputNum) {
		if (CresStreamCtrl.NumDmInputs <= 0)
			return;
		this.dmSync[inputNum] = enable;
	}
	
	public boolean getDmHdcpBlank(int inputNum) {
		if (CresStreamCtrl.NumDmInputs <= 0)
			return false;
		return dmHdcpBlank[inputNum];
	}
	
	public void setDmHdcpBlank(boolean enable, int inputNum) {
		if (CresStreamCtrl.NumDmInputs <= 0)
			return;
		this.dmHdcpBlank[inputNum] = enable;
	}
	
	public CresStreamCtrl.Resolution getDmResolution(int inputNum) {
		if (CresStreamCtrl.NumDmInputs <= 0)
			return null;
		return dmResolution[inputNum];
	}
	
	public void setDmResolution(CresStreamCtrl.Resolution resolution, int inputNum) {
		if (CresStreamCtrl.NumDmInputs <= 0)
			return;
		this.dmResolution[inputNum] = resolution;
	}
	
	public int getChromaKeyColor() {
		return chromaKeyColor;
	}
	
	public void setChromaKeyColor(int color) {
		this.chromaKeyColor = color;
	}

	public boolean getRgb888Enabled() { return rgb888Enabled; }

	public void setRgb888Enabled(boolean enable) { this.rgb888Enabled = enable; }
}
