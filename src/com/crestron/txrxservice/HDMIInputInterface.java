package com.crestron.txrxservice;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import android.util.Log;
import android.hardware.display.DisplayManager;

public class HDMIInputInterface {
	static String TAG = "HDMIInInterface";

	private static String syncStatus;
	private static String interlaced;
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
		interlaced = "false";	//no interlacing for txrx and dge for now
		horizontalRes = "0";
		verticalRes = "0";
		fps = "0";
		aspectRatio = "0";
		audioFormat = "1";	//1=PCM for txrx and dge
		audioChannels = "0";
		resolutionIndex = 0;
		isHdmiDriverPresent = (isHdmiDriverPresent | false); //set isHdmiDriverPresentH to false if not set
	}
	
	public void setSyncStatus() {
		if (isHdmiDriverPresent == true)
		{
			byte[] hdmiInSyncStatus = DisplayManager.getEVSHdmiInSyncStatus();
			
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
		return interlaced;
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
		if( (Integer.parseInt(horizontalRes) * 3) == (Integer.parseInt(verticalRes) * 4) )
		{
			aspectRatio = "133";
		}
		else if( (Integer.parseInt(horizontalRes) * 10) == (Integer.parseInt(verticalRes) * 16) )
		{
			aspectRatio = "160";
		}
		else if( (Integer.parseInt(horizontalRes) * 9) == (Integer.parseInt(verticalRes) * 16) )
		{
			aspectRatio = "177";
		}
		else
		{
			aspectRatio = "0";
		}
		
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
    
    public static boolean isHdmiDriverPresent()
    {
        File file = new File("/sys/devices/platform/omap_i2c.2/i2c-2/2-000f/sync_state");
        
        isHdmiDriverPresent = file.exists();
        
        return file.exists();
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
	        }
	        
	        Log.d(TAG, "HDMI IN index from sysfs:" + resolutionIndex);
	        return resolutionIndex;
		}
    	else
    		return 0;
    }
    
    public static boolean readHDCPStatus (){
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
	        }
	        Log.d(TAG, "HDMI IN HDCP status from sysfs:" + text.toString());
	        return Integer.parseInt(text.toString()) == 1;
		}
    	else 
    		return false;
    }
}
