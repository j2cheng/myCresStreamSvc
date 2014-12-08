package com.crestron.txrxservice;

import java.util.HashMap;
import java.util.Map;
import android.util.Log;

public class CresStreamConfigure {
	static String TAG = "CresStream Configure";

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
		port 	= 5004;		//port; 
		mode 	= mode.RTSP;	//RTSP;
		width 	= 1280;		//width;
		height 	= 720;		//height;
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
	
	public void setMode(int _mode) { 
		mode = StreamMode.fromInteger(_mode);
	}
	
	public void setWidth(int _width) { 
		Log.d(TAG, "Setwidth:" + width);
		width = _width; 
	}

	public static int getWidth() { 
		Log.d(TAG, "Getwidth:" + width);
		return width; 
	}

	public void setHeight(int _height) { 
		height = _height; 
		Log.d(TAG, "SetHeight:" + height);
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
	
	
	public void setVEncProfile(int profile) { 
		vprofile =  VideoEncProfile.fromInteger(profile);
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
