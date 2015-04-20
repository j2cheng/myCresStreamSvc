package com.crestron.txrxservice;

import java.util.HashMap;
import java.util.Map;
import android.util.Log;

public class CresStreamConfigure {
	static String TAG = "CresStream Configure";

   	String[] resolutionArray = { "Auto", "176x144", "352x288", "528x384", "640x360", "640x480", "720x480", "800x480", "800x600", "1024x768", "1280x720", "1280x800", "1366x768", "1440x900", "1600x900", "1600x1200", "1680x1050", "1920x1080"};

	public enum StreamMode {
		RTSP(0), RTP(1), MPEG2TS_RTP(2), MPEG2TS_UDP(3), MJPEG(4), MRTSP(5);
		private int value;
		private static final Map<Integer, StreamMode> intToEnum = new HashMap<Integer, StreamMode>();		
		static {
			for (StreamMode type:values()){
				intToEnum.put(type.getMode(), type);
			}
		}
		private StreamMode(int _value){
			value = _value;
		}	
		public int getMode() { 
			return value;
		}
		public static StreamMode fromInteger(int value){
			return intToEnum.get(value);
		}
	}
	static StreamMode mode;
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
	static VideoEncProfile vprofile;
	private static String ipAddr;
	
	// TODO: add values for every surface we need to save
	private static int x;
	private static int y;
	private static int width;
	private static int height;
	
	private static int rport;
	private static int tport;
	private static int rvport;
	private static int raport;
	private static int venclevel;
	private static int vfrmrate;
	private static int vbitrate;
	private static String url;

    public CresStreamConfigure() {
        MiscUtils.getDeviceIpAddr();
        ipAddr = MiscUtils.matcher.group();
        rport 	= 12462;		//port; 
        tport 	= 12460;		//port; 
        rvport 	= 12458;		//port; 
        raport 	= 12456;		//port; 
        mode 	= mode.RTSP;//RTSP;
        width 	= 1920;		//width;
        height 	= 1080;		//height;
        vprofile=vprofile.HP;
        vfrmrate = 50;
        vbitrate = 600000;
        venclevel= 8192;
        url 	 = null;
	}

	public void setIP(String _ipAddr) { 
		ipAddr = _ipAddr; 
		Log.d(TAG, "SetIP:" + ipAddr);
	}
	
	public static String getIP() { 
		Log.d(TAG, "GetIP:" + ipAddr);
		return ipAddr; 
	}
	
	public void setRTSPPort(int _port) { 
		rport = _port; 
		Log.d(TAG, "SetRTSPPort:" + rport);
	}
	
	public static int getRTSPPort() { 
		Log.d(TAG, "GetRTSPPort:" + rport);
		return rport; 
	}
	
	public void setTSPort(int _port) { 
		tport = _port; 
		Log.d(TAG, "SetTSPort:" + tport);
	}
	
	public static int getTSPort() { 
		Log.d(TAG, "GetTSPort:" + tport);
		return tport; 
	}
	
	public void setRTPVPort(int _port) { 
		rvport = _port; 
		Log.d(TAG, "SetRTPVideoPort:" + rvport);
	}
	
	public static int getRTPVPort() { 
		Log.d(TAG, "GetRTPVideoPort:" + rvport);
		return rvport; 
	}
	
	public void setRTPAPort(int _port) { 
		raport = _port; 
		Log.d(TAG, "SetRTPAudioPort:" + raport);
	}
	
	public static int getRTPAPort() { 
		Log.d(TAG, "GetRTPAudioPort:" + raport);
		return raport; 
	}
	
	public void setTransportMode(String tmode) { 
		mode = StreamMode.valueOf(tmode);	
		Log.d(TAG, "Transport mode value set is: " + mode);
	}
	
	public void setVEncProfile(String profile) { 
		vprofile= VideoEncProfile.valueOf(profile);	
		Log.d(TAG, "vprofile value set is: " + vprofile);
	}
	
	public void setOutResolution(int index) {
		if((index==0) || (index >=resolutionArray.length)){ 
			width = 0;
			height = 0;
		}
		else{
			String res = resolutionArray[index];	
			String[] str = res.split("[x]+");
			width = Integer.parseInt(str[0]);
			height = Integer.parseInt(str[1]);
		}
	}
	
	public void setWidth(int w) { 
		Log.d(TAG, "setwidth:" + w);
		width = w; 
	}
	
	public void setHeight(int h) { 
		Log.d(TAG, "setHeight:" + h);
	 	height = h; 
	}
	
	public static int getWidth() { 
		Log.d(TAG, "Getwidth:" + width);
		return width; 
	}
	
	public static int getHeight() { 
		Log.d(TAG, "GetHeight:" + height);
	 	return height; 
	}

	
	
	public void setx(int xloc) { 
		Log.d(TAG, "setx:" + xloc);
		x = xloc; 
	}
	
	public void sety(int yloc) { 
		Log.d(TAG, "sety:" + yloc);
	 	y = yloc; 
	}
	
	public static int getx() { 
		Log.d(TAG, "getx:" + x);
		return x; 
	}
	
	public static int gety() { 
		Log.d(TAG, "gety:" + y);
	 	return y; 
	}
	
        public void setVFrameRate(int _frmRate) { 
		vfrmrate = _frmRate; 
		Log.d(TAG, "setVFrameRate:" + vfrmrate);
	}
	
	public static int getVFrameRate() { 
		Log.d(TAG, "getVFrameRate:" + vfrmrate);
	 	return vfrmrate; 
	}
	
        public void setVideoBitRate(int _bitRate) { 
		vbitrate = _bitRate*1000; 
		Log.d(TAG, "setVideoBitRate:" + vbitrate);
	}
	
	public static int getVideoBitRate() { 
		Log.d(TAG, "getVideoBitRate:" + vbitrate);
	 	return vbitrate; 
	}
	
	public static int getVEncLevel() { 
		return venclevel;
	}

	public static void setUrl(String Url) { 
		url = Url; 
	}

	public static String getUrl() { 
		return url; 
	}
}
