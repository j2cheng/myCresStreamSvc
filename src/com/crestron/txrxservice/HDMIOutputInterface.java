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
import java.lang.String;

import com.crestron.txrxservice.ProductSpecific;

public class HDMIOutputInterface {
	static String TAG = "TxRx HDMIOutInterface";

	private boolean hasHdmiOutput = false;
	private static boolean isAM3K = false;
	CresStreamCtrl streamCtrl=null;
	private boolean am3kSyncStatus = false;
	
	private static String syncStatus;
	private static String interlaced;
   	private static String horizontalRes;
	private static String verticalRes;
	private static String fps;
	private static String aspectRatio;
	private static String audioFormat;
	private static String audioChannels;
	
	public HDMIOutputInterface(int hdcpCheckBitmask, CresStreamCtrl ctrl) {	
		setStreamCtrl(ctrl);
		if (hdcpCheckBitmask != 0)
		{
			hasHdmiOutput = true;
			syncStatus = "false";
			interlaced = "false";	//no interlacing for txrx and dge for now
			horizontalRes = "0";
			verticalRes = "0";
			fps = "0";
			aspectRatio = "0";
			audioFormat = "0";	//1=PCM for txrx and dge
			audioChannels = "0";
		}
		else
		{
			// If no HDMI output just set sync to true
			hasHdmiOutput = false;
			syncStatus = "true";
			interlaced = "false";	//no interlacing for txrx and dge for now
			horizontalRes = "0";
			verticalRes = "0";
			fps = "0";
			aspectRatio = "0";
			audioFormat = "0";	//1=PCM for txrx and dge
			audioChannels = "0";
		}
		
		Log.i(TAG, "Device has hdmi output is " + hasHdmiOutput);
	}
	
	public void setStreamCtrl(CresStreamCtrl ctrl)
	{
		streamCtrl = ctrl;
		isAM3K = streamCtrl.isAM3X00();
	}
	
	public void set_am3k_sync_status(boolean sync)
	{
		am3kSyncStatus = sync;
		Log.i(TAG,"Set AM3K HDMI out sync status to "+am3kSyncStatus);
	}
	
	public void setSyncStatus() {
		if (hasHdmiOutput)
		{
			if (!isAM3K)
			{
				StringBuilder text = new StringBuilder(16);
				try {
					File file = new File("/sys/class/switch/hdmi_true_hpd/state");

					BufferedReader br = new BufferedReader(new FileReader(file));  
					String line;   
					while ((line = br.readLine()) != null) {
						text.append(line);
					}
					br.close();
				}catch (Exception e) {
					text.append("0"); //if error default to no sync
				}
				if(Integer.parseInt(text.toString()) == 1)
					syncStatus = "true";
				else
					syncStatus = "false";
			} else {
				if (am3kSyncStatus)
					syncStatus = "true";
				else
					syncStatus = "false";
			}
			Log.i(TAG, "setSyncStatus: "+syncStatus);
		}
		
		// Old Mistral API method below
//		byte[] hdmiOutSyncStatus = ProductSpecific.getEVSHdmiOutSyncStatus();
//		
//		Log.i(TAG, "SyncStatus " + (char)hdmiOutSyncStatus[0]);
//
//		if((char)hdmiOutSyncStatus[0] == '1')
//			syncStatus = "true";
//		else
//			syncStatus = "false";
	}
	
	public String getSyncStatus() {
		return syncStatus;
	}

	public String getInterlacing() { 
		return interlaced;
	}

	private static int parseToInt(String value, int defaultValue)
	{
		int ret;
		try {
			ret = Integer.parseInt(value);
		} catch(NumberFormatException ex)
		{
			ret = defaultValue;
		}
		return ret;
	}
	
	public void setHorizontalRes(String _horizontalRes) { 
		horizontalRes = _horizontalRes; 
	}
	
	public String getHorizontalRes() {
		return horizontalRes;
	}

	public int getWidth() {
		return parseToInt(horizontalRes, 0);	
	}
	
	public void setVerticalRes(String _verticalRes) { 
		verticalRes = _verticalRes; 
	}
	
	public String getVerticalRes() {
		return verticalRes;
	}

	public int getHeight() {
		return parseToInt(verticalRes, 0);	
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
	
	public void setAudioFormat(String audioFmt) {
		audioFormat = audioFmt;
	}
	
	public void setAudioChannels(String audioChn) {
		audioChannels = audioChn;
	}
	
	public String getAudioFormat() {
		return audioFormat;
	}
	
	public String getAudioChannels() {
		return audioChannels;
	}
	
	public static int readHDCPOutputStatus (){
		if (!isAM3K)
		{
			StringBuilder text = new StringBuilder(16);
			try {
				File file = new File("/sys/devices/virtual/misc/hdcp/hdcp_status");

				BufferedReader br = new BufferedReader(new FileReader(file));  
				String line;   
				while ((line = br.readLine()) != null) {
					text.append(line);
				}
				br.close();
			}catch (Exception e) {
				//            e.printStackTrace();
				text.append("0"); //if error default to no HDCP
			}
			//        Log.i(TAG, "HDMI OUT HDCP status from sysfs:" + text.toString());
			return Integer.parseInt(text.toString());
		} else {
			String status = MiscUtils.readStringFromDisk("/sys/devices/platform/ff160000.i2c/i2c-7/7- 0030/hdcp_status");
			if (status.equalsIgnoreCase("hdcp_v1x succeed") || status.equalsIgnoreCase("hdcp_v2x succeed")) {
				return 1;
			} else if (status.equalsIgnoreCase("Off")) {
				return 0;
			} else { // Failed/Starting
				return -1;
			}
		}
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
