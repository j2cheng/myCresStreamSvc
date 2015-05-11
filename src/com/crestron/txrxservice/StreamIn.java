package com.crestron.txrxservice;

import java.io.IOException;

import android.util.Log;
import android.view.SurfaceHolder;
import android.media.MediaPlayer;
import android.content.Context;
import android.media.MediaPlayer.OnPreparedListener;
import android.media.MediaPlayer.OnBufferingUpdateListener;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.AudioManager;

//import com.crestron.txrxservice.CresStreamConfigure;
import com.crestron.txrxservice.CresStreamCtrl.StreamState;

public class StreamIn implements OnPreparedListener, OnCompletionListener, OnBufferingUpdateListener, OnErrorListener {

    private MediaPlayer[] mediaPlayer = new MediaPlayer[2];
    private SurfaceHolder vidHolder;
    String TAG = "TxRx StreamIN";
    StringBuilder sb;
    static String srcUrl="";
    static int[] latency = new int [] {2000, 2000};//msec
    boolean rtp_mode = false;
    boolean media_pause = false;
    boolean tcpInterleaveFlag = false;
    boolean disableLatencyFlag = false;
    private CresStreamCtrl streamCtl;
    private int idx = 0;


    //public StreamIn(CresStreamCtrl mContext, SurfaceHolder vHolder) {
    public StreamIn(CresStreamCtrl mContext) {
        Log.e(TAG, "StreamIN :: Constructor called...!");
        //vidHolder = vHolder;
        streamCtl = mContext;
    }

    public void setSessionIndex(int id){
        idx = id;
    }
    
    //Setting source url to play streamin
    public void setUrl(String p_url){
        srcUrl = p_url;
        Log.d(TAG, "setting stream in URL to "+srcUrl);
    }

    //Dejitter Buffer latency
    public void setLatency(int sessId, int duration){
        latency[sessId] = duration;	
        Log.d(TAG, "setting stream in latency "+latency[sessId]);
    }

    //MJPEG IN  ??? Not Needed
    public void disableLatency(){
        disableLatencyFlag = true;    
    }

    //Enable TCP for RTSP Mode
    public void setRtspTcpInterleave(boolean tcpInterleave){
        Log.d(TAG, " setRtspTcpInterleave");
        tcpInterleaveFlag = tcpInterleave;    
    }

    //RTP Only Mode, SDP Creation based on RTP Video and Audio Ports
    public void setRtpOnlyMode(int vport, int aport, String ip){
        rtp_mode = true;
        sb = new StringBuilder(4096);
        Log.d(TAG, "vport "+vport+ "aport "+aport +"ip "+ip);
        sb.append("v=0\r\n").append("o=- 15545345606659080561 15545345606659080561 IN IP4 cpu000669\r\n").append("s=Sample\r\n").append("i=N/A\r\n").append("c=IN IP4 ").append(ip).append("\r\n").append("t=0 0\r\n").append("a=range:npt=now-\r\n").append("m=audio ").append(Integer.toString(aport)).append(" RTP/AVP 96\r\n").append("a=control:audio\r\n").append("a=rtpmap:96 MP4A-LATM/44100/2\r\n").append("a=fmtp:96 profile-level-id=15; object=2; cpresent=0; config=400024203fc0\r\n").append("m=video ").append(Integer.toString(vport)).append(" RTP/AVP 97\r\n").append("a=control:video\r\n").append("a=rtpmap:97 H264/90000\r\n").append("a=fmtp:97 profile-level-id=64002A;in-band-parameter-sets=1;packetization-mode=1\r\n");
    }

    //Play based on based Pause/Actual Playback 
    public void onStart() {
        if(media_pause && (mediaPlayer!=null)){
            mediaPlayer[idx].start();
            media_pause = false;

        }else{
            try {
                //MNT - 3.11.15 - clean up the player if it's already playing
                if ((mediaPlayer[idx]!=null) && (mediaPlayer[idx].isPlaying()==true))
                {
                    mediaPlayer[idx].stop();
                    mediaPlayer[idx].reset();
                    mediaPlayer[idx].release();
                    mediaPlayer[idx] = null;
                }
                mediaPlayer[idx] = new MediaPlayer();
                mediaPlayer[idx].setDisplay(streamCtl.getCresSurfaceHolder());
                //mediaPlayer.setDisplay(vidHolder);
                srcUrl = streamCtl.userSettings.getServerUrl(idx);
                mediaPlayer[idx].setDataSource(srcUrl);	
                Log.d(TAG, "URL is "+srcUrl);
                if(tcpInterleaveFlag && srcUrl.startsWith("rtsp://"))
                    mediaPlayer[idx].setTransportCommunication(true);
                //Setting Initial Latency
                if(disableLatencyFlag==false){
                    try {
                        mediaPlayer[idx].setDejitterBufferDuration(latency[idx]);
                    } catch(Exception e){
                        e.printStackTrace();
                    }
                }
                if(rtp_mode){
                    mediaPlayer[idx].setSDP(sb.toString());
                    rtp_mode = false;
                }
                mediaPlayer[idx].prepare();
                mediaPlayer[idx].setOnErrorListener(this);
                mediaPlayer[idx].setOnCompletionListener(this);
                mediaPlayer[idx].setOnBufferingUpdateListener(this);
                mediaPlayer[idx].setOnPreparedListener(this);
                mediaPlayer[idx].setAudioStreamType(AudioManager.STREAM_MUSIC);
            } 
            catch(Exception e){
            	// TODO: explore exception handling with better feedback of what went wrong to user
                streamCtl.SendStreamState(StreamState.STOPPED, idx);
                e.printStackTrace();
            }}
    }

    //Pause
    public void onPause() {
        if((mediaPlayer[idx]!=null) && (mediaPlayer[idx].isPlaying())) 
            mediaPlayer[idx].pause();
        media_pause = true;
        streamCtl.SendStreamState(StreamState.PAUSED, idx);
    }

    public void onStop() {
        Log.d(TAG, "Stopping MediaPlayer");
        if(mediaPlayer[idx] != null){
            if(mediaPlayer[idx].isPlaying()){
                mediaPlayer[idx].stop();
            }
            mediaPlayer[idx].release();
            mediaPlayer[idx] = null;
            streamCtl.SendStreamState(StreamState.STOPPED, idx);
        }
    }

    @Override
        public void onPrepared(MediaPlayer mp) {
            Log.d(TAG, "######### OnPrepared##############");
            mediaPlayer[idx].start();
            //mediaPlayer[idx].getStatisticsData();
            
            streamCtl.SendStreamState(StreamState.STARTED, idx); // TODO: this should be on start complete not prepared
            streamCtl.SendStreamInFeedbacks();
        }
        

    //Error Indication Callback
    public boolean onError(MediaPlayer mp, int what, int extra){
        switch (what){
            case MediaPlayer.MEDIA_ERROR_UNKNOWN:
                Log.e(TAG, "unknown media playback error");
                break;
            case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                Log.e(TAG, "server connection died");
            default:
                Log.e(TAG, "generic audio playback error");
                break;
        }

        switch (extra){
            case MediaPlayer.MEDIA_ERROR_IO:
                Log.e(TAG, "IO media error");
                break;
            case MediaPlayer.MEDIA_ERROR_MALFORMED:
                Log.e(TAG, "media error, malformed");
                break;
            case MediaPlayer.MEDIA_ERROR_UNSUPPORTED:
                Log.e(TAG, "unsupported media content");
                break;
            case MediaPlayer.MEDIA_ERROR_TIMED_OUT:
                Log.e(TAG, "media timeout error");
                break;
            default:
                Log.e(TAG, "unknown playback error");
                break;
        }
        mediaPlayer[idx].stop();
        mediaPlayer[idx].reset();
        mediaPlayer[idx].release();
        mediaPlayer[idx] = null;
        return true;
    }
    
    public void onBufferingUpdate(MediaPlayer mp, int percent) {
        int progress = (int) ((float) mp.getDuration() * ((float) percent/ (float) 100));
        Log.d(TAG, "####### Buffering percent "+percent);
        streamCtl.SendStreamState(StreamState.BUFFERING, idx);
    }

    public void onCompletion(MediaPlayer mp) {
        //mp.stop();
        Log.d(TAG, "####### Stopping mediaplayer");
    }

    public String updateSvcWithPlayerStatistics(){
//        if(mediaPlayer[idx].isPlaying())
//            return mediaPlayer[idx].getStatisticsData();
//        else
            return "";
    }
    
    //Response to CSIO Layer
    public boolean getMediaPlayerStatus()
    {
    	if (mediaPlayer[idx]!=null)
    		return mediaPlayer[idx].isPlaying();
    	else
    		return false;
    }

    public int getMediaPlayerHorizontalResFb(){
    	if (mediaPlayer[idx]!=null)
    		return mediaPlayer[idx].getVideoWidth();
    	else
    		return 0;
    }

    public int getMediaPlayerVerticalResFb(){
    	if (mediaPlayer[idx]!=null)
    		return mediaPlayer[idx].getVideoHeight();
    	else
    		return 0;
    }

    public int getMediaPlayerFpsFb(){
        return 30;//TODO
    }
    public String getMediaPlayerAspectRatioFb(){
    	String aspect;
    	if (mediaPlayer[idx] != null)
    	{
	        aspect = MiscUtils.calculateAspectRatio(mediaPlayer[idx].getVideoWidth(), mediaPlayer[idx].getVideoHeight());
    	}
    	else
    		aspect = String.valueOf(0);
        return aspect;
    }
    public int getMediaPlayerAudioFormatFb(){
        return 1;//TODO
    }
    public int getMediaPlayerAudiochannelsFb(){
        return 2;//TODO
    }

}
