package com.crestron.txrxservice;

import android.util.Log;

public class HDMIInputInterface {
	static String TAG = "HDMIInInterface";

   	private static String horizontalRes;
	private static String verticalRes;
	private static String fps;
	
	public HDMIInputInterface() {
		horizontalRes = "0";
		verticalRes = "0";
		fps = "0";
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
}