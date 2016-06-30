package com.crestron.txrxservice;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import android.util.Log;

import com.crestron.txrxservice.ProductSpecific;

public class HDMIInputInterface {
	static String TAG = "HDMIInInterface";

	private static String syncStatus;
	private static String horizontalRes;
	private static String verticalRes;
	private static String fps;
	private static String aspectRatio;
	private static String audioFormat;
	private static String audioChannels;
	private static int resolutionIndex;
	private static boolean isHdmiDriverPresent;
	
	public HDMIInputInterface() {
		syncStatus = "false";
		horizontalRes = "0";
		verticalRes = "0";
		fps = "0";
		aspectRatio = "0";
		audioFormat = "1";	//1=PCM for txrx and dge
		audioChannels = "2";
		resolutionIndex = 0;
		isHdmiDriverPresent = (isHdmiDriverPresent | false); //set isHdmiDriverPresentH to false if not set
	}
	
	public void setSyncStatus() {
		if (isHdmiDriverPresent == true)
		{
			byte[] hdmiInSyncStatus = ProductSpecific.getEVSHdmiInSyncStatus();
			
			Log.i(TAG, "SyncStatus " + (char)hdmiInSyncStatus[0]);
	
			if(((char)hdmiInSyncStatus[0] == '1') && (resolutionIndex != 0))
				syncStatus = "true";
			else
				syncStatus = "false";
		}
	}
	
	public void setResolutionIndex(int index){
		resolutionIndex = index;
	}
	
	public int getResolutionIndex() {
		return resolutionIndex;
	}
	
	public String getSyncStatus() {
		return syncStatus;
	}

	public String getInterlacing() { 
		return String.valueOf(readInterlaced());
	}

	public void setHorizontalRes(String _horizontalRes) { 
		horizontalRes = _horizontalRes; 
	}
	
	public String getHorizontalRes() {
		return horizontalRes;
	}

	public void setVerticalRes(String _verticalRes) { 
		verticalRes = _verticalRes; 
	}
	
	public String getVerticalRes() {
		return verticalRes;
	}

	public void setFPS(String _fps) { 
		fps = _fps; 
	}
	
	public String getFPS() {
		return fps;
	}
	
	public void setAspectRatio() {
		if(syncStatus == "false")
		{
			aspectRatio = "0";
			return;
		}
		else
			aspectRatio = MiscUtils.calculateAspectRatio(Integer.parseInt(horizontalRes), Integer.parseInt(verticalRes));
		
		Log.i(TAG, "AR " + aspectRatio);
		return;
	}
	
	public String getAspectRatio() {
		return aspectRatio;
	}
	
	public String getAudioFormat() {
		return audioFormat;
	}
	
	public String getAudioChannels() {
		return audioChannels;
	}

	public String getAudioSampleRate() {
		
		return Integer.toString(readAudioSampleRate());
	}
	
    public void updateResolutionInfo(String hdmiInResolution) 
    {
        String delims = "[x@]+";
        String tokens[] = hdmiInResolution.split(delims);
        for (int tokenIndex = 0; tokenIndex < tokens.length; tokenIndex++)
            Log.i(TAG, " " + tokens[tokenIndex]);

        setSyncStatus();
        setHorizontalRes(tokens[0]);
        setVerticalRes(tokens[1]);
        setFPS(tokens[2].trim());
        setAspectRatio();
    }
    
    public static void setHdmiDriverPresent(boolean isPresent) {
    	isHdmiDriverPresent = isPresent;
    }
        
    public static int getHdmiHpdEventState(){
    	if (isHdmiDriverPresent == true)
		{
	        StringBuilder text = new StringBuilder();
	        try {
	            //File sdcard = Environment.getExternalStorageDirectory();
	            File file = new File("/sys/class/switch/evs_hdmi_hpd/state");
	
	            BufferedReader br = new BufferedReader(new FileReader(file));  
	            String line;   
	            while ((line = br.readLine()) != null) {
	                text.append(line);
	                //text.append('\n');
	            }
	            br.close() ;
	        }catch (IOException e) {
	            e.printStackTrace();   
	            text.append("0");
	        }
	        Log.d(TAG, "hpdState:" + text.toString());
	        return Integer.parseInt(text.toString());
		}
    	else
    		return 0;
    }

    public static String getHdmiInResolutionSysFs(){
    	if (isHdmiDriverPresent == true)
		{
	        StringBuilder text = new StringBuilder();
	        try {
	            File file = new File("/sys/devices/platform/omap_i2c.2/i2c-2/2-000f/hdmi_in_resolution");
	
	            BufferedReader br = new BufferedReader(new FileReader(file));  
	            String line;   
	            while ((line = br.readLine()) != null) {
	                text.append(line);
	            }
	            br.close();
	        }catch (IOException e) {
	            e.printStackTrace();
	            text.append("0x0@0");
	        }
	        Log.d(TAG, "HDMI IN Res from sysfs:" + text.toString());
	        return text.toString();
		}
    	else
    		return "0x0@0";
    }

    public static int readResolutionEnum(){
    	if (isHdmiDriverPresent == true)
		{
	        int resolutionIndex = 0;
	        StringBuilder text = new StringBuilder();
	        try {
	            File file = new File("/sys/class/switch/evs_hdmi_resolution/state");
	            BufferedReader br = new BufferedReader(new FileReader(file));  
	            String line;   
	            while ((line = br.readLine()) != null) {
	                text.append(line);
	            }
	            br.close();
	            
	            resolutionIndex = Integer.parseInt(text.toString());
	        }catch (IOException e) {
	            e.printStackTrace(); 
	            resolutionIndex = 0;
	        }
	        
	        Log.d(TAG, "HDMI IN index from sysfs:" + resolutionIndex);
	        return resolutionIndex;
		}
    	else
    		return 0;
    }
    
    public static int getResolutionEnum()
    {
    	return resolutionIndex;
    }
    
    public static boolean readHDCPInputStatus (){
    	if (isHdmiDriverPresent == true)
		{
	    	boolean hdcpStatus = false;
	    	StringBuilder text = new StringBuilder();
	        try {
	            File file = new File("/sys/devices/platform/omap_i2c.2/i2c-2/2-000f/hdcp");
	
	            BufferedReader br = new BufferedReader(new FileReader(file));  
	            String line;   
	            while ((line = br.readLine()) != null) {
	                text.append(line);
	            }
	            br.close();
	        }catch (IOException e) {
	            e.printStackTrace();
	            text.append("0"); //if error default to no HDCP
	        }
//	        Log.d(TAG, "HDMI IN HDCP status from sysfs:" + text.toString());
	        return Integer.parseInt(text.toString()) == 57;
		}
    	else 
    		return false;
    }
    
    public static boolean readSyncState (){
    	if (isHdmiDriverPresent == true)
		{
	    	StringBuilder text = new StringBuilder();
	        try {
	            File file = new File("/sys/devices/platform/omap_i2c.2/i2c-2/2-000f/sync_state");
	
	            BufferedReader br = new BufferedReader(new FileReader(file));  
	            String line;   
	            while ((line = br.readLine()) != null) {
	                text.append(line);
	            }
	            br.close();
	        }catch (IOException e) {
	            e.printStackTrace();
	            text.append("0"); //if error default to no sync
	        }
	        return Integer.parseInt(text.toString()) == 1;
		}
    	else 
    		return false;
    }
    
    public static int readAudioSampleRate (){
    	if (isHdmiDriverPresent == true)
		{
	    	StringBuilder text = new StringBuilder();
	        try {
	            File file = new File("/sys/devices/platform/omap_i2c.2/i2c-2/2-000f/audio_sample_rate");
	
	            BufferedReader br = new BufferedReader(new FileReader(file));  
	            String line;   
	            while ((line = br.readLine()) != null) {
	                text.append(line);
	            }
	            br.close();
	        }catch (IOException e) {
	            e.printStackTrace();
	            text.append("48000"); //if error default to 48kHz
	        }
	        return Integer.parseInt(text.toString());
		}
    	else 
    		return 0;
    }
    public static boolean readInterlaced (){
    	if (isHdmiDriverPresent == true)
		{
	    	StringBuilder text = new StringBuilder();
	        try {
	            File file = new File("/sys/devices/platform/omap_i2c.2/i2c-2/2-000f/interlaced");
	
	            BufferedReader br = new BufferedReader(new FileReader(file));  
	            String line;   
	            while ((line = br.readLine()) != null) {
	                text.append(line);
	            }
	            br.close();
	        }catch (IOException e) {
	            e.printStackTrace();
	            text.append("0"); //if error default to not interlaced
	        }
	        return Integer.parseInt(text.toString()) == 1;
		}
    	else 
    		return false;
    }
}
