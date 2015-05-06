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
	private static String[] multicastIpAddr;
	
	// TODO: add values for every surface we need to save
	private static int[] x;
	private static int[] y;
	private static int[] width;
	private static int[] height;
	
	private static int[] rport;
	private static int[] tport;
	private static int[] rvport;
	private static int[] raport;
	private static int[] venclevel;
	private static int[] vfrmrate;
	private static int[] vbitrate;
	private static String[] url;
	private static String[] username;
	private static String[] password;

	private static int[] deviceMode;
	private static int sessInitMode;

    public CresStreamConfigure() {
        MiscUtils.getDeviceIpAddr();
        ipAddr = MiscUtils.matcher.group();
        rport 	= new int[]{ 12462, 14462};		//port; 
        tport 	= new int[]{ 12460, 14460};;		//port; 
        rvport 	= new int[]{ 12458, 14458};		//port; 
        raport 	= new int[]{ 12456, 14456};		//port; 
        mode 	= mode.RTSP;//RTSP;
        x       = new int[]{0,0};
        y       = new int[]{0,0};
        width   = new int[]{1920, 1920};		//width;
        height  = new int[]{1080, 1080};		//height;
        vprofile=vprofile.HP;
        vfrmrate = new int[]{50, 50 };
        vbitrate = new int[]{600000, 600000};
        venclevel= new int[]{8192, 8192 };
        multicastIpAddr = new String[]{ "0.0.0.0", "0.0.0.0"};
        url 	 = new String[]{"", ""};
        username = new String[]{"", ""};
        password = new String[]{"", ""};
        deviceMode=new int[]{0, 0};
        sessInitMode=0;
	}

	public void setMulticastIP(int id, String _ipAddr) { 
		multicastIpAddr[id] = _ipAddr; 
		Log.d(TAG, "setMulticastIP:" + multicastIpAddr[id]);
	}
	
	public static String getMulticastIP(int id) { 
		Log.d(TAG, "getMulticastIP:" + multicastIpAddr[id]);
		return multicastIpAddr[id]; 
	}
	
	public static String getDeviceIP() { 
		Log.d(TAG, "getDeviceIP:" + ipAddr);
		return ipAddr; 
	}

	public void setRTSPPort(int id, int _port) { 
		rport[id] = _port; 
		Log.d(TAG, "SetRTSPPort:" + rport[id]);
	}
	
	public static int getRTSPPort(int id) { 
		Log.d(TAG, "GetRTSPPort:" + rport[id]);
		return rport[id]; 
	}
	
	public void setTSPort(int id, int _port) { 
		tport[id] = _port; 
		Log.d(TAG, "SetTSPort:" + tport[id]);
	}
	
	public static int getTSPort(int id) { 
		Log.d(TAG, "GetTSPort:" + tport[id]);
		return tport[id]; 
	}
	
	public void setRTPVPort(int id, int _port) { 
		rvport[id] = _port; 
		Log.d(TAG, "SetRTPVideoPort:" + rvport[id]);
	}
	
	public static int getRTPVPort(int id) { 
		Log.d(TAG, "GetRTPVideoPort:" + rvport[id]);
		return rvport[id]; 
	}
	
	public void setRTPAPort(int id, int _port) { 
		raport[id] = _port; 
		Log.d(TAG, "SetRTPAudioPort:" + raport[id]);
	}
	
	public static int getRTPAPort(int id) { 
		Log.d(TAG, "GetRTPAudioPort:" + raport[id]);
		return raport[id]; 
	}
	
	public void setTransportMode(String tmode) { 
		mode = StreamMode.valueOf(tmode);	
		Log.d(TAG, "Transport mode value set is: " + mode);
	}
	
	public void setVEncProfile(String profile) { 
		vprofile= VideoEncProfile.valueOf(profile);	
		Log.d(TAG, "vprofile value set is: " + vprofile);
	}
	
	public void setOutResolution(int id, int index) {//TODO
		if((index==0) || (index >=resolutionArray.length)){ 
			width[0] = 0;
			height[0] = 0;
		}
		else{
			String res = resolutionArray[index];	
			String[] str = res.split("[x]+");
			width[0] = Integer.parseInt(str[0]);
			height[0] = Integer.parseInt(str[1]);
		}
	}
	
	public void setWidth(int id, int w) { 
		Log.d(TAG, "setwidth:" + w);
		width[id] = w; 
	}
	
	public void setHeight(int id, int h) { 
		Log.d(TAG, "setHeight:" + h);
	 	height[id] = h; 
	}
	
	public static int getWidth(int id) { 
		Log.d(TAG, "Getwidth:" + width[id]);
		return width[id]; 
	}
	
	public static int getHeight(int id) { 
		Log.d(TAG, "GetHeight:" + height[id]);
	 	return height[id]; 
	}
	
	public void setx(int id, int xloc) { 
		Log.d(TAG, "setx:" + xloc);
		x[id] = xloc; 
	}
	
	public void sety(int id, int yloc) { 
		Log.d(TAG, "sety:" + yloc);
	 	y[id] = yloc; 
	}
	
	public static int getx (int id) { 
		Log.d(TAG, "getx:" + x[id]);
		return x[id]; 
	}
	
	public static int gety(int id) { 
		Log.d(TAG, "gety:" + y[id]);
	 	return y[id]; 
	}
	
        public void setVFrameRate(int id, int _frmRate) { 
		vfrmrate[id] = _frmRate; 
		Log.d(TAG, "setVFrameRate:" + vfrmrate[id]);
	}
	
	public static int getVFrameRate(int id) { 
		Log.d(TAG, "getVFrameRate:" + vfrmrate[id]);
	 	return vfrmrate[id]; 
	}
	
        public void setVideoBitRate(int id, int _bitRate) { 
		vbitrate[id] = _bitRate*1000; 
		Log.d(TAG, "setVideoBitRate:" + vbitrate[id]);
	}
	
	public static int getVideoBitRate(int id) { 
		Log.d(TAG, "getVideoBitRate:" + vbitrate[id]);
	 	return vbitrate[id]; 
	}
	
	public static int getVEncLevel(int id) { 
		return venclevel[id];
	}

	public static void setUrl(int id, String Url) { 
		url[id] = Url; 
	}

	public static String getUrl(int id) { 
		return url[id]; 
	}
	
        public static void setUserName(int id, String uname) { 
                username[id] = uname;
	}

	public static void setPasswd(int id, String passwd) { 
		password[id] = passwd; 
	}
       
        public static String getUserName(int id) { 
                 return username[id];
	}

	public static String getPasswd(int id) { 
		return password[id];
	}

        public static void setDeviceMode(int id, int devmode) { 
            Log.d(TAG, "Setting device mode to "+ devmode + "for session id "+ id);
            deviceMode[id] = devmode;
        }

        public static int getDeviceMode(int id) { 
                return deviceMode[id];
	}

}
