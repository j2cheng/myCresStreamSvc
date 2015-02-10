package com.crestron.txrxservice;

import android.util.Log;
import android.hardware.display.DisplayManager;

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
		audioChannels = "0";
	}

	public void setSyncStatus() {
		byte[] hdmiOutSyncStatus = DisplayManager.getEVSHdmiOutSyncStatus();
		
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
	
}