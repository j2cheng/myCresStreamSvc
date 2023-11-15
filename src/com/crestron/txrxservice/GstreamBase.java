package com.crestron.txrxservice;

import android.content.Context;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;

import org.freedesktop.gstreamer.GStreamer;

public class GstreamBase  {
	static String TAG = "TxRx GstreamBASE";
	private CresStreamCtrl streamCtl;
    public native void postGStreamerInit();

	public GstreamBase(CresStreamCtrl mContext) {
        Log.i(TAG, "GstreamBase: begin");
		streamCtl = mContext;

        Log.i(TAG,"loading css_jni" );
		System.loadLibrary("css_jni");                

        Log.i(TAG,"loading gstreamer_android");
		System.loadLibrary("gstreamer_android");

        Log.i(TAG, "loading completed");

		// Initialize GStreamer and warn if it fails
		try {
			GStreamer.init((Context)mContext);
		} catch (Exception e) {
			Log.e(TAG, "Failed to init Gstreamer, error: " + e);
			return;
		}
        postGStreamerInit();
        Log.i(TAG, "GstreamBase: end");
	}
}
