package com.crestron.txrxservice;

import android.util.Log;
import android.content.Context;

import org.freedesktop.gstreamer.GStreamer;

public class GstreamBase  {
	static String TAG = "TxRx GstreamBASE";
	private CresStreamCtrl streamCtl;

	public GstreamBase(CresStreamCtrl mContext) {
		Log.e(TAG, "GstreamBase :: Constructor called...!");
		streamCtl = mContext;
		// Initialize GStreamer and warn if it fails
		try {
			GStreamer.init((Context)mContext);
		} catch (Exception e) {
			Log.e(TAG, "Failed to init Gstreamer, error: " + e);
			return;
		}
	}

	// Moved here from GstreamIn.java,
	// since gstreamer is used for streaming out as well as in.
	static {
		Log.d(TAG,"loading gstreamer_android and gstreamer_jni" );
		System.loadLibrary("gstreamer_android");
		System.loadLibrary("gstreamer_jni");                
	}
}
