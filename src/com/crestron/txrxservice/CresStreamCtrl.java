package com.crestron.txrxservice;

import java.util.HashMap;
import java.util.Map;
import java.io.IOException;
import java.lang.String;
import android.net.Uri;

import android.os.Bundle;
import android.util.Log;
import android.app.Activity;
import android.os.IBinder;
import android.view.SurfaceView;
import android.widget.Toast;
import android.content.Context;
import android.os.AsyncTask;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.view.SurfaceHolder;

interface Command {
    void executeStart();
}

interface myCommand {
    void executeStop();
}

public class CresStreamCtrl extends Activity {
	CameraStreaming cam_streaming;
	StreamIn streamPlay;
	BroadcastReceiver hpdEvent = null;
	BroadcastReceiver resolutionEvent = null;
	private SurfaceView previewSurface;
	private SurfaceView streamingSurface = null;
	public SurfaceView dummyView = null;
	public static SurfaceHolder mPopupHolder;
	CresStreamConfigure myconfig;
	AsyncTask<Void, String, Long> sockTask;
	
	String TAG = "TxRx StreamCtrl";
	int device_mode = 0;
	int sessInitMode = 0;
        int StreamState = 100;//INVALID State
	public enum DeviceMode {
		PREVIEW(2), STREAMOUT(1), STREAMIN(0);
		private final int value;

		private DeviceMode(int value) {
			this.value = value;
		}

		public int getValue() {
			return value;
		}
		public static String getStringValueFromInt(int i) {
			for (DeviceMode status : DeviceMode.values()) {
				if (status.getValue() == i) {
					return status.toString();
				}
			}
			throw new IllegalArgumentException("the given number doesn't match any Status.");
		}
	}

        public native void javaCallJNI();//JNI functionality
	//HashMap
	HashMap<String, Command> hm;
	HashMap<String, myCommand> hm2;
	@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			setContentView(R.layout.activity_main);
			//StreamIn Surface
			streamingSurface = (SurfaceView) findViewById(R.id.surfaceView1);
			streamPlay = new StreamIn(CresStreamCtrl.this, streamingSurface);
			//StreamOut & Preview Surface
			previewSurface = (SurfaceView) findViewById(R.id.surfaceView);
			cam_streaming = new CameraStreaming(CresStreamCtrl.this, previewSurface);
			dummyView = streamingSurface;
			//HPD and Resolution Event Registration
			registerBroadcasts();
			//Input Streamout Config
			myconfig = new CresStreamConfigure();
			//Stub: TestApp functionality
			sockTask = new TCPInterface(this);
			sockTask.execute(new Void[0]);
			hm = new HashMap();
			hm.put("PREVIEW", new Command() {
					public void executeStart() {startPreview(); StreamState = 2;};
					});
			hm.put("STREAMOUT", new Command() {
					public void executeStart() {startStreamOut(); StreamState = 1;};
					});
			hm.put("STREAMIN", new Command() {
					public void executeStart() {startStreamIn(); StreamState = 0;};
					});
			hm2 = new HashMap();
			hm2.put("PREVIEW", new myCommand() {
					public void executeStop() {stopPreview(); StreamState = 100;};
					});
			hm2.put("STREAMOUT", new myCommand() {
					public void executeStop() {stopStreamOut(); StreamState = 100;};
					});
			hm2.put("STREAMIN", new myCommand() {
					public void executeStop() {stopStreamIn(); StreamState = 100;};
					});
		}

	protected void onDestroy(){
		super.onDestroy();
		sockTask.cancel(true);
		Log.d(TAG, " Asynctask cancelled");
	}
		
	public void setSessionInitMode(int mode)
	{
		Log.d(TAG, " setSessionInitMode "+ mode);
		sessInitMode = mode;
	}

	public int getStreamState()
	{
		return StreamState;
	}
	
        public void setDeviceMode(int mode)
	{
		Log.d(TAG, " setDeviceMode "+ mode);
		device_mode = mode;
	}
	
	//Ctrls
	public void Start(){
		hm.get(DeviceMode.getStringValueFromInt(device_mode)).executeStart();
	}
	public void Stop(){
		hm2.get(DeviceMode.getStringValueFromInt(device_mode)).executeStop();
	}
	public void Pause(){
		Log.d(TAG, " Unimplemented");
	}
	//StreamOut Ctrl & Config 
	public void setStreamOutConfig(String ip, int port, int w, int h, int mode, int profile, int level)
	{
		Log.d(TAG, " setStreamOutConfig");
		myconfig.setIP(ip);	
		myconfig.setPort(port);	
		myconfig.setWidth(w);	
		myconfig.setHeight(h);	
		myconfig.setMode(mode);	
		myconfig.setVEncProfile(profile);	
		myconfig.setVEncLevel(level);	
	}

	public void startStreamOut()
	{
		try {
			cam_streaming.startRecording();
		} catch(IOException e) {
			e.printStackTrace();
		}
		Toast.makeText(this, "StreamOut Started", Toast.LENGTH_LONG).show();
	}

	public void stopStreamOut()
	{
		Toast.makeText(this, "StreamOut Stopped", Toast.LENGTH_LONG).show();
		cam_streaming.stopRecording();
	}

	//StreamIn Ctrls & Config
	private void hideStreamInWindow()
	{
		if (streamingSurface != null)
		{
			streamingSurface.setVisibility(8);
			streamingSurface = null;
		}
	}

	private void showStreamInWindow()
	{
		if (streamingSurface == null){
			Log.d(TAG, " showStreamIn");
			streamingSurface = dummyView;
			mPopupHolder = streamingSurface.getHolder();
			streamingSurface.setVisibility(0);
			return;
		}
	}
	
	public void setStreamInUrl(String ap_url)
	{
		streamPlay.setUrl(ap_url);
	}

	public void startStreamIn()
	{
		if((cam_streaming.mCameraObj.APlayStatus())==true)
			cam_streaming.mCameraObj.stopAudio();
		showStreamInWindow();
		streamPlay.onStart();
		Toast.makeText(this, "StreamIN Started", Toast.LENGTH_LONG).show();
	}

	public void stopStreamIn()
	{
		streamPlay.onStop();
		Toast.makeText(this, "StreamIN Stopped", Toast.LENGTH_LONG).show();
		hideStreamInWindow();
	}
	
	//Preview 
	public void startPreview()
	{
                cam_streaming.mCameraObj.startPlayback(true);
		Toast.makeText(this, "Preview Started", Toast.LENGTH_LONG).show();
	}
	
        public void stopPreview()
	{
                cam_streaming.mCameraObj.stopPlayback();
		Toast.makeText(this, "Preview Stopped", Toast.LENGTH_LONG).show();
	}
	

	//Registering for HPD and Resolution Event detection	
	void registerBroadcasts(){
		Log.d(TAG, "registerBroadcasts !");
		resolutionEvent = new BroadcastReceiver()
		{
			public void onReceive(Context paramAnonymousContext, Intent paramAnonymousIntent)
			{
				if (paramAnonymousIntent.getAction().equals("evs.intent.action.hdmi.RESOLUTION_CHANGED"))
				{
					int i = paramAnonymousIntent.getIntExtra("hdmi_resolution_id", -1);
					Log.i(TAG, "Received resolution changed broadcast !: " + i);
					if ((cam_streaming.mCameraObj != null) && (((cam_streaming.mCameraObj.IsPreviewStatus()) == true)))  
					{
						Log.i(TAG, "Restart called due to resolution change broadcast ! ");
						cam_streaming.mCameraObj.stopPlayback();
						cam_streaming.mCameraObj.startPlayback(true);
					}
					else if((cam_streaming.mCameraObj != null) && ((cam_streaming.isStreaming()) == true)){
						cam_streaming.stopRecording();
						try{
							cam_streaming.startRecording();
						} catch(IOException e) {
							e.printStackTrace();
						}
					}
					else
						Log.i(TAG, " Nothing todo!!!");
				}
			}
		};
		IntentFilter localIntentFilter1 = new IntentFilter("evs.intent.action.hdmi.RESOLUTION_CHANGED");
		registerReceiver(resolutionEvent, localIntentFilter1);
		hpdEvent = new BroadcastReceiver()
		{
			public void onReceive(Context paramAnonymousContext, Intent paramAnonymousIntent)
			{
				if (paramAnonymousIntent.getAction().equals("evs.intent.action.hdmi.HPD"))
				{
					int i = paramAnonymousIntent.getIntExtra("hdmi_hdp_id", -1);
					Log.i(TAG, "Received hpd broadcast ! " + i);
				}
			}
		};
		IntentFilter localIntentFilter2 = new IntentFilter("evs.intent.action.hdmi.HPD");
		registerReceiver(hpdEvent, localIntentFilter2);	
	}
}
