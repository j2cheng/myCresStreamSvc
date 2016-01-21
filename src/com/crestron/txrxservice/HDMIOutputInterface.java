package com.crestron.txrxservice;

import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;

import com.crestron.txrxservice.ProductSpecific;

public class HDMIOutputInterface {
	static String TAG = "TxRx HDMIOutInterface";

	private static String syncStatus;
	private static String interlaced;
   	private static String horizontalRes;
	private static String verticalRes;
	private static String fps;
	private static String aspectRatio;
	private static String audioFormat;
	private static String audioChannels;
	
	public HDMIOutputInterface() {
		syncStatus = "false";
		interlaced = "false";	//no interlacing for txrx and dge for now
		horizontalRes = "0";
		verticalRes = "0";
		fps = "0";
		aspectRatio = "0";
		audioFormat = "1";	//1=PCM for txrx and dge
		audioChannels = "2";
	}

	public void setSyncStatus() {
		byte[] hdmiOutSyncStatus = ProductSpecific.getEVSHdmiOutSyncStatus();
		
		Log.i(TAG, "SyncStatus " + (char)hdmiOutSyncStatus[0]);

		if((char)hdmiOutSyncStatus[0] == '1')
			syncStatus = "true";
		else
			syncStatus = "false";
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
	
	public static int readHDCPOutputStatus (){
    	boolean hdcpStatus = false;
    	StringBuilder text = new StringBuilder();
        try {
            File file = new File("/sys/devices/virtual/misc/hdcp/hdcp_status");

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
//        Log.d(TAG, "HDMI OUT HDCP status from sysfs:" + text.toString());
        return Integer.parseInt(text.toString());
    }
	
	public static void setHDCPBypass (boolean enabled){
        Writer writer = null;
		try 
      	{
			writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("/sys/devices/virtual/misc/hdcp/hdcp_bypass"), "US-ASCII"));
			if (enabled)
				writer.write("1");
			else
				writer.write("0");
		    writer.flush();
	    } 
      	catch (IOException ex) {
    	  Log.e(TAG, "Failed to set HDCP bypass mode: " + ex);
    	} 
		finally 
    	{
    		try {writer.close();} catch (Exception ex) {/*ignore*/}
    	}	
    }
}
