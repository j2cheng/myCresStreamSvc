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
import com.crestron.txrxservice.CresStreamConfigure;

public class StreamIn implements SurfaceHolder.Callback, OnPreparedListener {

    private MediaPlayer mediaPlayer;
    private SurfaceHolder vidHolder;
    String TAG = "TxRx StreamIN";
    StringBuilder sb;
    String srcUrl;
    int latency = 2000;//msec
    int dest_width = 1920;
    int dest_height = 1080;
    boolean rtp_mode = false;

    public StreamIn(Context mContext, SurfaceView view) {
        Log.e(TAG, "StreamIN :: Constructor called...!");
        if (view != null) {
            Log.d(TAG, "View is not null");
            vidHolder = view.getHolder();	
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

    public void setDestWidth(int w){
        dest_width = w;
    }

    public void setDestHeight(int h){
        dest_height = h;
    }

    public void setRtpOnlyMode(int vport, int aport, String ip){
        rtp_mode = true;
        sb = new StringBuilder(4096);
        Log.d(TAG, "vport "+vport+ "aport "+aport +"ip "+ip);
        sb.append("v=0\r\n").append("o=- 15545345606659080561 15545345606659080561 IN IP4 cpu000669\r\n").append("s=Sample\r\n").append("i=N/A\r\n").append("c=IN IP4 ").append(ip).append("\r\n").append("t=0 0\r\n").append("a=range:npt=now-\r\n").append("m=audio ").append(Integer.toString(aport)).append(" RTP/AVP 96\r\n").append("a=control:audio\r\n").append("a=rtpmap:96 MP4A-LATM/44100/2\r\n").append("a=fmtp:96 profile-level-id=15; object=2; cpresent=0; config=400024203fc0\r\n").append("m=video ").append(Integer.toString(vport)).append(" RTP/AVP 97\r\n").append("a=control:video\r\n").append("a=rtpmap:97 H264/90000\r\n").append("a=fmtp:97 profile-level-id=64002A;in-band-parameter-sets=1;packetization-mode=1\r\n");
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
            vidHolder.setFixedSize(dest_width, dest_height);
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

        //Response to CSIO Layer
        public boolean getMediaPlayerStatus()
        {
            return mediaPlayer.isPlaying();
        }

        public int getMediaPlayerHorizontalResFb(){
            return mediaPlayer.getVideoWidth();
        }
        
        public int getMediaPlayerVerticalResFb(){
            return mediaPlayer.getVideoHeight();
        }

        public int getMediaPlayerFpsFb(){
            return 30;//TODO
        }
        public String getMediaPlayerAspectRatioFb(){
            String aspect = MiscUtils.calculateAspectRatio(mediaPlayer.getVideoWidth(), mediaPlayer.getVideoHeight());
            return aspect;
        }
        public int getMediaPlayerAudioFormatFb(){
            return 1;//TODO
        }
        public int getMediaPlayerAudiochannelsFb(){
            return 2;//TODO
        }
        
}
