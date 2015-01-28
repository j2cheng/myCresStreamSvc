package com.crestron.txrxservice;

import android.util.Log;
import android.hardware.display.DisplayManager;

public class HDMIOutputInterface {
	static String TAG = "TxRx HDMIOutInterface";

	private static String interlaced;
   	private static String horizontalRes;
	private static String verticalRes;
	private static String fps;
	private static String aspectRatio;
	private static String audioFormat;
	private static String audioChannels;
	private static String manf;
	private static String serialNo;
	private static String modelNo;
	private static String prefTiming;
	
	public HDMIOutputInterface() {
		interlaced = "0";	//no interlacing for txrx and dge for now
		horizontalRes = "0";
		verticalRes = "0";
		fps = "0";
		aspectRatio = "0";
		audioFormat = "1";	//1=PCM for txrx and dge
		audioChannels = "0";
        manf = "";
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
		//if(sync is not detected)
		//{
		//	aspectRatio = "0";
		//	return;
		//}
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
	
	public void setManf() {
		StringBuilder manfString = new StringBuilder();
		
		byte[] hdmiOutEdid = DisplayManager.getEVSHdmiOutEdid();
		
		manfString.append((char)( 'A' - 1 + ((hdmiOutEdid[8] >> 2) & 0x1F) )); //Bits 14-10
		manfString.append((char)( 'A' - 1 + ( ((hdmiOutEdid[8] << 3) & 0x18) | ((hdmiOutEdid[9] >> 5) & 0x07) ) ));//Bits 9-5
		manfString.append((char)( 'A' - 1 + (hdmiOutEdid[9] & 0x1F) ));//Bits 4-0
		
		manf = manfString.toString();
		
		Log.i(TAG, "Manufacturer " + manf);
	}
	
	public String getManf() {
		return manf;
	}
	
	public void setSerialNo() {
		byte[] hdmiOutEdid = DisplayManager.getEVSHdmiOutEdid();
		
		serialNo = Integer.toString( ((hdmiOutEdid[15] << 24) & 0xFF000000) | ((hdmiOutEdid[14] << 16) & 0x00FF0000) | ((hdmiOutEdid[13] << 8) & 0x0000FF00) | (hdmiOutEdid[12] & 0x000000FF) );
		
		Log.i(TAG, "Serial no " + serialNo);
	}
	
	public String getSerialNo() {
		return serialNo;
	}
	
	public void setModelNo() {
		byte[] hdmiOutEdid = DisplayManager.getEVSHdmiOutEdid();
		
		modelNo = Integer.toString( ((hdmiOutEdid[11] << 8) & 0xFF00) | (hdmiOutEdid[10] & 0x00FF) );
		
		Log.i(TAG, "Model no " + modelNo);
	}
	
	public String getModelNo() {
		return modelNo;
	}
	
	public void setPrefTiming() {
		StringBuilder prefTimingString = new StringBuilder();
		
		float pixClock;
		int horzActPixels;
		int horzBlkPixels;
		int vertActPixels;
		int vertBlkPixels;
		int refreshRate;
		
		byte[] hdmiOutEdid = DisplayManager.getEVSHdmiOutEdid();
		
		pixClock = ( ((hdmiOutEdid[55] << 8) & 0xFF00) | (hdmiOutEdid[54] & 0x00FF) ) * 10000; //In Hz
		horzActPixels = ( ((hdmiOutEdid[58] << 4) & 0x0F00) | (hdmiOutEdid[56] & 0x00FF) ); 
		horzBlkPixels = ( ((hdmiOutEdid[58] << 8) & 0x0F00) | (hdmiOutEdid[57] & 0x00FF) );
		vertActPixels = ( ((hdmiOutEdid[61] << 4) & 0x0F00) | (hdmiOutEdid[59] & 0x00FF) ); 
		vertBlkPixels = ( ((hdmiOutEdid[61] << 8) & 0x0F00) | (hdmiOutEdid[60] & 0x00FF) );
		
		Log.i(TAG, " pixclk " + pixClock + " horzActPixels " + horzActPixels + " horzBlkPixels " + horzBlkPixels + " vertActPixels " + vertActPixels + " vertBlkPixels " + vertBlkPixels);
		
		refreshRate = Math.round(pixClock / ( (horzActPixels + horzBlkPixels) * (vertActPixels + vertBlkPixels) ) );
		
		Log.i(TAG, "ref rate " + refreshRate);
		
		prefTimingString.append(Integer.toString(horzActPixels)).append("x").append(Integer.toString(vertActPixels)).append("@").append(Integer.toString(refreshRate));
		
		prefTiming = prefTimingString.toString();
		
		Log.i(TAG, "Pref timing " + prefTiming);
	}
	
	public String getPrefTiming() {
		return prefTiming;
	}
}