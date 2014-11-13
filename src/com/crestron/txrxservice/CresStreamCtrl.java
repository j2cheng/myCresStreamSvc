package com.crestron.txrxservice;

import java.io.IOException;
import java.lang.String;
import android.net.Uri;

import android.os.Bundle;
import android.util.Log;
import android.app.Activity;
import android.os.IBinder;
import android.view.SurfaceView;
import android.os.Message;
import android.os.Handler;
import android.content.Context;
import android.os.AsyncTask;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.view.SurfaceHolder;


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
	boolean stream_out = false;
	boolean stream_in =  false;
	boolean preview =  false;
	String videourl ;
	int dmode = 0;
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
	}

	protected void onDestroy(){
		super.onDestroy();
		sockTask.cancel(true);
		Log.d(TAG, " Asynctask cancelled");
	}
	
	public void setSessionInitMode(int mode)
	{
		Log.d(TAG, " setSessionInitMode "+ mode);
	}

	public void setDeviceMode(int mode)
	{
		Log.d(TAG, " setMode "+ mode);
		dmode = mode;
	}
	
	//Ctrls
	public void Start(){
		if(dmode==1)	
			startStreamOut();
		else if (dmode==0)
			startStreamIn();
		else
			startPreview();
	}
	public void Stop(){
		if(dmode==1)	
			stopStreamOut();
		else if (dmode==0)
			stopStreamIn();
		else
			Log.d(TAG, " Unimplemented");
	}
	public void Pause(){
		Log.d(TAG, " Unimplemented");
	}
	//StreamOut Ctrl & Config 
	public void setStreamOutConfig(String ip, int port, int w, int h, int mode, int profile)
	{
		Log.d(TAG, " setStreamOutConfig");
		myconfig.setIP(ip);	
		myconfig.setPort(port);	
		myconfig.setWidth(w);	
		myconfig.setHeight(h);	
		myconfig.setMode(mode);	
		myconfig.setVEncProfile(profile);	
	}

	public void startStreamOut()
	{
		try {
			cam_streaming.startRecording();
		} catch(IOException e) {
			e.printStackTrace();
		}
	}

	public void stopStreamOut()
	{
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
	}

	public void stopStreamIn()
	{
		streamPlay.onStop();
		hideStreamInWindow();
	}
	
	//Preview 
	public void startPreview()
	{
		cam_streaming.mCameraObj.startAudio();
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
