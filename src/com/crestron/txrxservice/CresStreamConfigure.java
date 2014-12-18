package com.crestron.txrxservice;

import java.util.HashMap;
import java.util.Map;
import android.util.Log;

public class CresStreamConfigure {
	static String TAG = "CresStream Configure";

   	String[] resolutionArray = { "Auto", "176x144", "352x288", "528x384", "640x360", "640x480", "720x480", "800x480", "800x600", "1024x768", "1280x720", "1280x800", "1366x768", "1440x900", "1600x900", "1600x1200", "1680x1050", "1920x1080"};

	public enum StreamMode {
		RTSP(0), RTP(1), MPEG2TS_RTP(2), MPEG2TS_UDP(3), MJPEG(4);
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
	private static int width;
	private static int height;
	private static int port;
	private static int venclevel;
	private static int vfrmrate;
	private String url;

	public CresStreamConfigure() {
		ipAddr 	= "127.0.0.1";	//ipAddr; 
		port 	= 1234;		//port; 
		mode 	= mode.RTP;	//RTSP;
		width 	= 1920;		//width;
		height 	= 1080;		//height;
		vprofile=vprofile.HP;
	        vfrmrate = 50;
                venclevel  = 2;
		url 	= null;
	}

	public void setIP(String _ipAddr) { 
		ipAddr = _ipAddr; 
		Log.d(TAG, "SetIP:" + ipAddr);
	}
	
	public static String getIP() { 
		Log.d(TAG, "GetIP:" + ipAddr);
		return ipAddr; 
	}
	
	public void setPort(int _port) { 
		port = _port; 
		Log.d(TAG, "SetPort:" + port);
	}
	
	public static int getPort() { 
		Log.d(TAG, "GetPort:" + port);
		return port; 
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
		if(index!=0 && (index > resolutionArray.length)){ 
			String res = resolutionArray[index];	
			String[] str = res.split("[x]+");
			width = Integer.parseInt(str[0]);
			height = Integer.parseInt(str[1]);
		}
		else{
			width = 0;
			height = 0;
		}
	}
	
	public static int getWidth() { 
		Log.d(TAG, "Getwidth:" + width);
		return width; 
	}
	
	public static int getHeight() { 
		Log.d(TAG, "GetHeight:" + height);
	 	return height; 
	}
	
        public void setVFrameRate(int _frmRate) { 
		vfrmrate = _frmRate; 
		Log.d(TAG, "setVFrameRate:" + vfrmrate);
	}
	
	public static int getVFrameRate() { 
		Log.d(TAG, "getVFrameRate:" + vfrmrate);
	 	return vfrmrate; 
	}
	
	
	public void setVEncLevel(int level) { 
		venclevel = level; 
	}
	
	public static int getVEncLevel() { 
		return venclevel;
	}

	public void setUrl(String Url) { 
		url = Url; 
	}

	public String getUrl() { 
		return url; 
	}
}
