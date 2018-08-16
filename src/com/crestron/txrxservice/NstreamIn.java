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

import com.crestron.txrxservice.ProductSpecific;


//import com.crestron.txrxservice.CresStreamConfigure;
import com.crestron.txrxservice.CresStreamCtrl.StreamState;

public class NstreamIn implements StreamInStrategy, OnPreparedListener, OnCompletionListener, OnBufferingUpdateListener, OnErrorListener {

    private MediaPlayer[] mediaPlayer = new MediaPlayer[CresStreamCtrl.NumOfSurfaces];
//    private SurfaceHolder vidHolder;
    String TAG = "TxRx StreamIN";
    StringBuilder[] sb = new StringBuilder[CresStreamCtrl.NumOfSurfaces];
    boolean rtp_mode[] = {false, false};
    boolean media_pause[] = {false, false};
    boolean tcpInterleaveFlag[] = {false, false};	//TODO: investigate if this should go into userSettings, needs to be enum
    boolean disableLatencyFlag[] = {false, false};
    private CresStreamCtrl streamCtl;

    //public StreamIn(CresStreamCtrl mContext, SurfaceHolder vHolder) {
    public NstreamIn(CresStreamCtrl mContext) {
        Log.e(TAG, "StreamIN :: Constructor called...!");
        //vidHolder = vHolder;
        streamCtl = mContext;
    }
    
    //MJPEG IN  ??? Not Needed
    public void disableLatency(int sessionId){
        disableLatencyFlag[sessionId] = true;    
    }

    //Enable TCP for RTSP Mode
    public void setRtspTcpInterleave(int tcpInterleave, int sessionId){
        Log.i(TAG, " setRtspTcpInterleave");
        if(tcpInterleave == 0)
        	tcpInterleaveFlag[sessionId] = false; 
        else
            tcpInterleaveFlag[sessionId] = true; 
    }

    //RTP Only Mode, SDP Creation based on RTP Video and Audio Ports
    public void setRtpOnlyMode(int vport, int aport, String ip, int sessionId){
        rtp_mode[sessionId] = true;
        sb[sessionId] = new StringBuilder(4096);
        Log.i(TAG, "vport "+vport+ "aport "+aport +"ip "+ip);
        sb[sessionId].append("v=0\r\n").append("o=- 15545345606659080561 15545345606659080561 IN IP4 cpu000669\r\n").append("s=Sample\r\n").append("i=N/A\r\n").append("c=IN IP4 ").append(ip).append("\r\n").append("t=0 0\r\n").append("a=range:npt=now-\r\n").append("m=audio ").append(Integer.toString(aport)).append(" RTP/AVP 96\r\n").append("a=control:audio\r\n").append("a=rtpmap:96 MP4A-LATM/44100/2\r\n").append("a=fmtp:96 profile-level-id=15; object=2; cpresent=0; config=400024203fc0\r\n").append("m=video ").append(Integer.toString(vport)).append(" RTP/AVP 97\r\n").append("a=control:video\r\n").append("a=rtpmap:97 H264/90000\r\n").append("a=fmtp:97 profile-level-id=64002A;in-band-parameter-sets=1;packetization-mode=1\r\n");
    }

    //Play based on based Pause/Actual Playback 
    public void onStart(int sessionId) {
        if(media_pause[sessionId] && (mediaPlayer[sessionId]!=null)){
            mediaPlayer[sessionId].start();
            media_pause[sessionId] = false;

        }else{
            try {
                //MNT - 3.11.15 - clean up the player if it's already playing
                if ((mediaPlayer[sessionId]!=null) && (mediaPlayer[sessionId].isPlaying()==true))
                {
                    mediaPlayer[sessionId].stop();
                    mediaPlayer[sessionId].reset();
                    mediaPlayer[sessionId].release();
                    mediaPlayer[sessionId] = null;
                }
                mediaPlayer[sessionId] = new MediaPlayer();
                mediaPlayer[sessionId].setDisplay(streamCtl.dispSurface.GetSurfaceHolder(sessionId));
                //mediaPlayer.setDisplay(vidHolder);
                String srcUrl = streamCtl.userSettings.getStreamInUrl(sessionId);
                mediaPlayer[sessionId].setDataSource(srcUrl);	
                Log.i(TAG, "URL is "+srcUrl);
                if(tcpInterleaveFlag[sessionId] && srcUrl.startsWith("rtsp://"))
                	ProductSpecific.setTransportCommunication(mediaPlayer[sessionId], true);
                //Setting Initial Latency
                if(disableLatencyFlag[sessionId]==false){
                    try {
                    	ProductSpecific.setDejitterBufferDuration(mediaPlayer[sessionId], streamCtl.userSettings.getStreamingBuffer(sessionId));
                    } catch(Exception e){
                        e.printStackTrace();
                    }
                }
                if(rtp_mode[sessionId]){
                	ProductSpecific.setSDP(mediaPlayer[sessionId], sb[sessionId].toString());
                    rtp_mode[sessionId] = false;
                }
                mediaPlayer[sessionId].prepare();
                mediaPlayer[sessionId].setOnErrorListener(this);
                mediaPlayer[sessionId].setOnCompletionListener(this);
                mediaPlayer[sessionId].setOnBufferingUpdateListener(this);
                mediaPlayer[sessionId].setOnPreparedListener(this);
                mediaPlayer[sessionId].setAudioStreamType(AudioManager.STREAM_MUSIC);
            } 
            catch(Exception e){
            	// TODO: explore exception handling with better feedback of what went wrong to user
                streamCtl.SendStreamState(StreamState.STOPPED, sessionId);
                e.printStackTrace();
            }}
    }

    //Pause
    public void onPause(int sessionId) {
        if((mediaPlayer[sessionId]!=null) && (mediaPlayer[sessionId].isPlaying())) 
            mediaPlayer[sessionId].pause();
        media_pause[sessionId] = true;
        streamCtl.SendStreamState(StreamState.PAUSED, sessionId);
    }

    public void onStop(int sessionId) {
        Log.i(TAG, "Stopping MediaPlayer");
        if(mediaPlayer[sessionId] != null){
            if(mediaPlayer[sessionId].isPlaying()){
                mediaPlayer[sessionId].stop();
            }
            mediaPlayer[sessionId].release();
            mediaPlayer[sessionId] = null;
            streamCtl.SendStreamState(StreamState.STOPPED, sessionId);
        }
    }

    @Override
        public void onPrepared(MediaPlayer mp) {
    		Log.i(TAG, "######### OnPrepared##############");
    		
    		int sessionId = findSessionId(mp);            
            mediaPlayer[sessionId].start();
            //mediaPlayer[idx].getStatisticsData();
            
            streamCtl.SendStreamState(StreamState.STARTED, sessionId); // TODO: this should be on start complete not prepared
            int width 			= mediaPlayer[sessionId].getVideoWidth();
            int height 			= mediaPlayer[sessionId].getVideoHeight();
            int framerate 		= 30; //TODO
            int profile 		= 0; //TODO
            streamCtl.SendStreamInVideoFeedbacks(sessionId, width, height, framerate, profile);
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
        
        int sessionId = findSessionId(mp);
        
        Log.i(TAG, "Mediaplayer sessionId = " + sessionId);
        
        mediaPlayer[sessionId].stop();
        mediaPlayer[sessionId].reset();
        mediaPlayer[sessionId].release();
        mediaPlayer[sessionId] = null;
        return true;
    }
    
    private int findSessionId(MediaPlayer mp) {
    	int sessionId = 0;
        for (int i = 0; i < 2; i++)
        {
        	if (mp.getAudioSessionId() == mediaPlayer[i].getAudioSessionId())
        	{
        		sessionId = i;
        		break;
        	}
        }
        return sessionId;
    }
    
    public void onBufferingUpdate(MediaPlayer mp, int percent) {
    	int sessionId = findSessionId(mp);
        int progress = (int) ((float) mp.getDuration() * ((float) percent/ (float) 100));
        Log.i(TAG, "####### Buffering percent "+percent);
        streamCtl.SendStreamState(StreamState.BUFFERING, sessionId);
    }

    public void onCompletion(MediaPlayer mp) {
        //mp.stop();
        Log.i(TAG, "####### Stopping mediaplayer");
    }

    public String updateSvcWithPlayerStatistics(){
//        if(mediaPlayer[idx].isPlaying())
//            return mediaPlayer[idx].getStatisticsData();
//        else
            return "";
    }
    
//    //Response to CSIO Layer
    public boolean getMediaPlayerStatus()
    {
    	return true;//TODO
    }

    public int getMediaPlayerHorizontalResFb(int sessionId){
    	if (mediaPlayer[sessionId]!=null)
    		return mediaPlayer[sessionId].getVideoWidth();
    	else
    		return 0;
    }

    public int getMediaPlayerVerticalResFb(int sessionId){
    	if (mediaPlayer[sessionId]!=null)
    		return mediaPlayer[sessionId].getVideoHeight();
    	else
    		return 0;
    }

    public int getMediaPlayerFpsFb(int sessionId){
        return 30;//TODO
    }
    public String getMediaPlayerAspectRatioFb(int sessionId){
    	String aspect;
    	if (mediaPlayer[sessionId] != null)
    	{
	        aspect = MiscUtils.calculateAspectRatio(mediaPlayer[sessionId].getVideoWidth(), mediaPlayer[sessionId].getVideoHeight());
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
    
    public void setVolume(int volume, int sessId){
    	int audioSteps = 101; // 0-100
    	float newVolume = (float)(1 - (Math.log(audioSteps - volume)/Math.log(audioSteps)));
    	Log.i(TAG, "New volume = " + newVolume);
    	mediaPlayer[sessId].setVolume(newVolume, newVolume);
    }
    
    public void updateCurrentXYloc(int xloc, int yloc, int sessId){
    	// Not needed by gallery player, only for Gstreamer
    }
    
    // TODO
    public long getStreamInNumVideoPackets() {
		return 0;
	}
	
	public int getStreamInNumVideoPacketsDropped() {
		return 0;
	}
	
	public long getStreamInNumAudioPackets() {
		return 0;
	}
	
	public int getStreamInNumAudioPacketsDropped() {
		return 0;
	}
	
	public int getStreamInBitrate() {
		return 0;
	}

    public static void setStatistics(boolean enabled, int sessId){
    	// Not needed by gallery player
    }
    
	public static void resetStatistics(int sessId){
    	// Not needed by gallery player
	}

	public void setRtspPort(int port, int sessionId) {
		// Not needed by gallery player			
	}

	public void setTsPort(int port, int sessionId) {
		// Not needed by gallery player			
	}

	public void setRtpVideoPort(int port, int sessionId) {
		// Not needed by gallery player			
	}

	public void setRtpAudioPort(int port, int sessionId) {
		// Not needed by gallery player		
	}

	public void setSessionInitiation(int initMode, int sessionId) {
		// Not needed by gallery player			
	}

	public void setTransportMode(int transportMode, int sessionId) {
		// Not needed by gallery player			
	}

	public void setMulticastAddress(String multicastIp, int sessionId) {
		// Not needed by gallery player			
	}

	public void setStreamingBuffer(int buffer_ms, int sessionId) {
		// Not needed by gallery player			
	}

	public void setUserName(String userName, int sessionId) {
		// Not needed by gallery player			
	}

	public void setPassword(String password, int sessionId) {
		// Not needed by gallery player			
	}
	
	public void setNewSink(boolean flag, int sessId){
    	// Not needed by gallery player
	}
    public void setFieldDebugJni(String cmd, int sessId){
    	// Not needed by gallery player
	}
     
    public void setAudioDrop(boolean enabled, int sessionId) {
    	// Not needed by gallery player
    }
    
    public void setLogLevel(int logLevel) {
    	// Not needed by gallery player
    }
    
    public void setHdcpEncrypt(boolean flag, int sessionId) {
    	// Not needed by gallery player
    }
    
    public void initUnixSocketState()
    {
    	// Not needed by gallery player
    }
}
