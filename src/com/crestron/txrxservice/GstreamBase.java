package com.crestron.txrxservice;

import android.content.Context;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;

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
		Log.i(TAG,"loading gstreamer_android and gstreamer_jni" );
        try {
            Os.setenv("GST_AMC_IGNORE_UNKNOWN_COLOR_FORMATS", "true", false);
        } catch(ErrnoException except) {
            Log.e(TAG, "failed setenv: GST_AMC_IGNORE_UNKNOWN_COLOR_FORMATS");
        }
		System.loadLibrary("gstreamer_android");
		System.loadLibrary("gstreamer_jni");                
	}
}
