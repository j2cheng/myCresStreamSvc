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
	AsyncTask<Void, String, Long> sockTask;
	
	String TAG = "TxRx StreamCtrl";
	boolean stream_out = false;
	boolean stream_in =  false;
	boolean preview =  false;
	String videourl ;
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		previewSurface = (SurfaceView) findViewById(R.id.surfaceView);
		streamingSurface = (SurfaceView) findViewById(R.id.surfaceView1);
		streamPlay = new StreamIn(CresStreamCtrl.this, streamingSurface);
	        cam_streaming = new CameraStreaming(CresStreamCtrl.this, previewSurface);
		dummyView = streamingSurface;
		registerBroadcasts();
		sockTask = new SocketListener(this);
                sockTask.execute(new Void[0]);
	}

	public void hideStreamInWindow()
	{
		if (streamingSurface != null)
		{
			streamingSurface.setVisibility(8);
			streamingSurface = null;
		}
	}

	public void showStreamInWindow()
	{
		if (streamingSurface == null){
			Log.d(TAG, " showStreamIn");
			streamingSurface = dummyView;
			mPopupHolder = streamingSurface.getHolder();
			streamingSurface.setVisibility(0);
			return;
		}
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
	public void startPreview()
	{
		cam_streaming.mCameraObj.startAudio();
	}
/*
	public static void configure(String[] textTokens)  {
		String l_ip;
		//Configure sconfig;
		int l_mode, out_w, out_h, l_port, l_profile;

		Log.d(TAG, "configuring with: ");
		for (int i = 0; i < textTokens.length; i++){
			Log.d(TAG, textTokens[i]);
		}
		l_ip 	= textTokens[2];
		l_port 	= Integer.parseInt(textTokens[3]);
		out_w 	= Integer.parseInt(textTokens[4]);
		out_h 	= Integer.parseInt(textTokens[5]);
		if(textTokens[6].equalsIgnoreCase("BP"))
			l_profile = 1;
		else if(textTokens[6].equalsIgnoreCase("MP"))
			l_profile = 2;
		else//HP
			l_profile = 8;
			
		if(textTokens[1].equalsIgnoreCase("RTSP"))
		{
		//	sconfig.setMode(0);
			l_mode=0;
		}
		else if(textTokens[1].equalsIgnoreCase("RTP"))
		{
			//sconfig.setMode(1);
			l_mode=1;
		}
		else if(textTokens[1].equalsIgnoreCase("TSR"))
		{
			//sconfig.setMode(2);
			l_mode=2;
		}
		else if(textTokens[1].equalsIgnoreCase("TSU"))
		{
			//sconfig.setMode(3);
			l_mode=3;	
		}
		else if(textTokens[1].equalsIgnoreCase("MJPEG"))
		{
			//sconfig.setMode(4);
			l_mode=4;	
		}
	}
*/
/*
	public static Handler handler = new Handler()
	{
		public void handleMessage(Message paramAnonymousMessage)
		{
			Log.e(TAG, "got message:"+paramAnonymousMessage.obj);
			String receivedMsg = (String) paramAnonymousMessage.obj;

			if(receivedMsg.equalsIgnoreCase("start"))
			{
				if(stream_out==true){
					Log.e(TAG, "Starting Recording");
					try {
						cam_streaming.startRecording();
					} catch(IOException e) {
						e.printStackTrace();
					}
				}else if(stream_in == true){
					Log.e(TAG, "Starting in ");
					if((cam_streaming.mCameraObj.APlayStatus())==true)
						cam_streaming.mCameraObj.stopAudio();
					showStreamInWindow();
					streamPlay.onStart();
				}else if(preview == true){
					Log.e(TAG, "Starting preview");
					cam_streaming.mCameraObj.startAudio();
					//cam_streaming.mCameraObj.stopPlayback();
					//cam_streaming.mCameraObj.startPlayback(true);
				}
				else
					Log.e(TAG, "Error in configuration");
			}
			else if(receivedMsg.equalsIgnoreCase("stop"))
			{
				Log.e(TAG, "stop");
				if(stream_out==true){
					cam_streaming.stopRecording();
					stream_out = false;
				}
				if(stream_in==true){
					streamPlay.onStop();
					stream_in =  false;
					hideStreamInWindow();
				}
				//preview =  false;
			}
			else if(receivedMsg.equalsIgnoreCase("pause"))
			{
				if((cam_streaming.mCameraObj.IsPauseStatus()==false))
					cam_streaming.mCameraObj.pausePlayback();
				else
					cam_streaming.mCameraObj.resumePlayback();
			}
			else{
				String delims = "[;]+";
				String[] tokens = receivedMsg.split(delims);;
				for (int i = 0; i < tokens.length; i++){
					Log.d(TAG, "Tokens "+tokens[i]);
				}
				String text = tokens[0];
				if(text.equalsIgnoreCase("Rx")){
					Log.i(TAG, "Activating By Receiver Mode");
					streamPlay.setUrl(tokens[2]);
					stream_in = true;
				}
				else if(text.equalsIgnoreCase("Tx")){
					Log.i(TAG, "Activating By Transmitter Mode");
					configure(tokens);
					//cam_streaming.configure(tokens);
					stream_out = true;
				}
				else if(text.equalsIgnoreCase("Pre")){
					Log.i(TAG, "Activating PreviewMode");
					preview = true;
				}
				else 
					Log.i(TAG, "Inavlid Message Received. Ignoring !!!!");
			}
		}
	};
*/
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
					//Log.i(TAG, "Ducati resolution values is: " + cam_preview.hdmiinput);
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
