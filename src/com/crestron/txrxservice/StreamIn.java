package com.crestron.txrxservice;

import java.io.IOException;

import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.media.MediaPlayer;
import android.content.Context;
import android.media.MediaPlayer.OnPreparedListener;
import android.media.AudioManager;

public class StreamIn implements SurfaceHolder.Callback, OnPreparedListener {

	private MediaPlayer mediaPlayer;
	private SurfaceHolder vidHolder;
	String TAG = "TxRx StreamIN";
	String srcUrl;
        int latency = 2000;//msec
        boolean rtp_mode = false;
        StringBuilder sb;
	public StreamIn(Context mContext, SurfaceView view) {
		Log.e(TAG, "StreamIN :: Constructor called...!");
		if (view != null) {
			Log.d(TAG, "View is not null");
			 = view.getHolder();	
			vidHolder.addCallback(this);
			vidHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
			view.setZOrderOnTop(true);

		} else {
			Log.d(TAG, "App passed null surface view for stream in");
		}
	}

	public void setUrl(String p_url){
		srcUrl = p_url;
		Log.d(TAG, "setting stream in URL to "+srcUrl);
	}
	
        public void setLatency(int duration){
	    latency = duration;	
            Log.d(TAG, "setting stream in latency "+latency);
	}

        public void setRtpOnlyMode(String portStr){
            rtp_mode = true;
            sb = new StringBuilder(4096);
            String str1 = portStr;
            sb.append("v=0\r\n").append("o=- 15545345606659080561 15545345606659080561 IN IP4 cpu000669\r\n").append("s=Unnamed\r\n").append("i=N/A\r\n").append("c=IN IP4 127.0.0.1\r\n").append("t=0 0\r\n").append("a=tool:vlc 2.0.8\r\n").append("a=recvonly\r\n").append("a=type:broadcast\r\n").append("a=charset:UTF-8\r\n").append("m=video ").append(str1).append("RTP/AVP 33\r\n").append("b=RR:0\r\n").append("a=rtpmap:33 MP2T/90000\r\n");
	}

	@Override
		public void surfaceChanged(SurfaceHolder arg0, int arg1, int arg2, int arg3) {
			Log.d(TAG, "######### surfacechanged##############");
			// TODO Auto-generated method stub
		}

	@Override
		public void surfaceCreated(SurfaceHolder arg0) {
			Log.d(TAG, "######### surfaceCreated##############");
		}

	@Override
		public void surfaceDestroyed(SurfaceHolder arg0) {
			// TODO Auto-generated method stub
			onStop();
		}

	public void onStart() {
		try {
			mediaPlayer = new MediaPlayer();
			if(vidHolder==null){
				Log.d(TAG, "holder is null ");
				vidHolder = CresStreamCtrl.mPopupHolder;
			}
                        mediaPlayer.setDisplay(vidHolder);
                        mediaPlayer.setDataSource(srcUrl);	
                        Log.d(TAG, "URL is "+srcUrl);
                        //Setting Initial Latency
                        mediaPlayer.setDejitterBufferDuration(latency);
                        if(rtp_mode){
                            mediaPlayer.setSDP(sb.toString());
                            rtp_mode = false;
                        }
                        mediaPlayer.prepare();
			mediaPlayer.setOnPreparedListener(this);
			mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
		} 
		catch(Exception e){
			e.printStackTrace();
		}
	}

	public void onStop() {
		Log.d(TAG, "Stopping MediaPlayer");
		if(mediaPlayer != null){
			if(mediaPlayer.isPlaying()){
				mediaPlayer.stop();
			}
			mediaPlayer.release();
			mediaPlayer = null;
		}
	}

	@Override
		public void onPrepared(MediaPlayer mp) {
			Log.d(TAG, "######### OnPrepared##############");
			mediaPlayer.start();
		}
}
