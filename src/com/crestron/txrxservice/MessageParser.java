package com.crestron.txrxservice;

import java.io.IOException;
import android.util.Log;

public class MessageParser {
    
    String TAG = "TxRx Parser"; 
    String ip_addr = null;// = "127.0.0.1";
    boolean enable_passwd = false;
    boolean disable_passwd = true;
    public static String replyString;
    int rport = 1234, tport = 1234, rvport=1234, raport = 1234, vbr = 6000, tmode = 0, resolution = 17;
    int initialLatency = 2000, profile = 2, venclevel = 4096, vframerate = 50;
    
    public enum VideoEncProfile {
        BP(2), MP(1), HP(0);
        private final int value;

        private VideoEncProfile(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
        public static String getStringValueFromInt(int i) {
            for (VideoEncProfile status : VideoEncProfile.values()) {
                if (status.getValue() == i) {
                    return status.toString();
                }
            }
            throw new IllegalArgumentException("the given number doesn't match any Status.");
        }
    }

    public enum TransportMode {
        MPEG2TS_UDP(2), MPEG2TS_RTP(1), RTP(0);
        private final int value;

        private TransportMode(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
        public static String getStringValueFromInt(int i) {
            for (TransportMode status : TransportMode.values()) {
                if (status.getValue() == i) {
                    return status.toString();
                }
            }
            throw new IllegalArgumentException("the given number doesn't match any Status.");
        }
    }
    
    private final CresStreamCtrl c_streamctl;
    StringTokenizer tokenizer; 
    
    String[] cmdArray = {"MODE", "SessionInitiation", "STREAMURL", "VENCPROFILE", "TRANSPORTMODE", "RTSPPORT", "TSPORT", "RTPVIDEOPORT", "RTPAUDIOPORT", "VFRAMERATE", "VBITRATE", "VENCLEVEL", "IPADDRESS", "START", "STOP", "PAUSE", "MUTE", "UNMUTE", "LATENCY", "PASSWD_ENABLE", "PASSWD_DISABLE", "USERNAME", "PASSWORD", 
    					"XLOC", "YLOC", "W", "H",
    					"HDMIIN_SYNC_DETECTED", "HDMIIN_INTERLACED", "HDMIIN_CEC_ERROR",
    					"HDMIIN_HORIZONTAL_RES_FB", "HDMIIN_VERTICAL_RES_FB", 
    					"HDMIIN_FPS_FB", "HDMIIN_ASPECT_RATIO",
    					"HDMIIN_AUDIO_FORMAT", "HDMIIN_AUDIO_CHANNELS",
    					"HDMIIN_TRANSMIT_CEC_MESSAGE", "HDMIIN_RECEIVE_CEC_MESSAGE",
    					"HDMIOUT_SYNC_DETECTED", "HDMIOUT_INTERLACED", "HDMIOUT_CEC_ERROR",
    					"HDMIOUT_HORIZONTAL_RES_FB", "HDMIOUT_VERTICAL_RES_FB", 
    					"HDMIOUT_FPS_FB", "HDMIOUT_ASPECT_RATIO",
    					"HDMIOUT_AUDIO_FORMAT", "HDMIOUT_AUDIO_CHANNELS",
    					"HDMIOUT_TRANSMIT_CEC_MESSAGE", "HDMIOUT_RECEIVE_CEC_MESSAGE",
    					"streamstate"};

    String[] fbArray = { "PROCESSING_FB", "device_ready_fb", "ELAPSED_SECONDS_FB", "STREAM_STATUS_FB", "INITIATOR_ADDRESS_FB","HORIZONTAL_RESOLUTION_FB", "VERTICAL_RESOLUTION_FB", "FPS_RESOLUTION_FB", "ASPECT_FB", "AUDIO_FORMAT_FB", "AUDIO_CHANNELS_FB"};
            

    public MessageParser (CresStreamCtrl a_crestctrl){
        c_streamctl = a_crestctrl;
        tokenizer = new StringTokenizer();
    }

    public String validateReceivedMessage(String read){
        StringBuilder sb = new StringBuilder(4096);
        sb.append("MODE (= 0:STREAMIN 1: STREAMOUT 2:HDMIPREVIEW)\r\n");
        sb.append("SessionInitiation (= 0: ByReceiver 1: ByTransmitter 2: MCastviaRTSP 3: MCastviaUDP)\r\n");
        sb.append("TRANSPORTMODE (= 0: RTP 1: TS_RTP 2: TS_UDP)\r\n");
        sb.append("VENCPROFILE (= 0:HighProfile 1:MainProfile 2:BaseProfile)\r\n");
        sb.append("STREAMURL(= any url) \r\n");
        sb.append("RTSPPORT(= 1024 to 49151)\r\n");
        sb.append("TSPORT (= 1024 to 49151)\r\n");
        sb.append("RTPVIDEOPORT (= 1024 to 49151)\r\n");
        sb.append("RTPAUDIOPORT (= 1024 to 49151)\r\n");
        sb.append("VFRAMERATE (= 60 50 30 24)\r\n");
        sb.append("VBITRATE (= 96 to 25000kbps)\r\n");
        sb.append("VENCLEVEL (= 4096:for 4.1 level, 8192:for 4.2 level)\r\n");
        sb.append("IPADDRESS(=xxx.xxx.xxx.xxx, also multicast address in SessionInitaion 2 & 3)\r\n");
        sb.append("MUTE(=1:true/0:false)\r\n");
        sb.append("UNMUTE(=1:true/0:false)\r\n");
        sb.append("LATENCY=100 to 5000 (in msec)\r\n");
        sb.append("PASSWD_ENABLE\r\n");
        sb.append("PASSWD_DISABLE\r\n");
        sb.append("USERNAME\r\n");
        sb.append("PASSWORD\r\n");
        sb.append("START | STOP | PAUSE (=true)\r\n");
        
        sb.append("HDMIIN_SYNC_DETECTED\r\n");
        sb.append("HDMIIN_INTERLACED\r\n");
        //sb.append("HDMIIN_CEC_ERROR\r\n");
        sb.append("HDMIIN_HORIZONTAL_RES_FB\r\n");
        sb.append("HDMIIN_VERTICAL_RES_FB\r\n");
        sb.append("HDMIIN_FPS_FB\r\n");
        sb.append("HDMIIN_ASPECT_RATIO\r\n");
        sb.append("HDMIIN_AUDIO_FORMAT\r\n");
        sb.append("HDMIIN_AUDIO_CHANNELS\r\n");
        //sb.append("HDMIIN_TRANSMIT_CEC_MESSAGE\r\n");
        //sb.append("HDMIIN_RECEIVE_CEC_MESSAGE\r\n");
        
        sb.append("HDMIOUT_SYNC_DETECTED\r\n");
        sb.append("HDMIOUT_INTERLACED\r\n");
        //sb.append("HDMIOUT_CEC_ERROR\r\n");
        sb.append("HDMIOUT_HORIZONTAL_RES_FB\r\n");
        sb.append("HDMIOUT_VERTICAL_RES_FB\r\n");
        sb.append("HDMIOUT_FPS_FB\r\n");
        sb.append("HDMIOUT_ASPECT_RATIO\r\n");
        sb.append("HDMIOUT_AUDIO_FORMAT\r\n");
        sb.append("HDMIOUT_AUDIO_CHANNELS\r\n");
        //sb.append("HDMIOUT_TRANSMIT_CEC_MESSAGE\r\n");
        //sb.append("HDMIOUT_RECEIVE_CEC_MESSAGE\r\n");
        
        sb.append("XLOC = (x position)\r\n");
        sb.append("YLOC = (y position)\r\n");
        sb.append("W (= window width)\r\n");
        sb.append("H (= window height)\r\n");
        sb.append("UPDATEREQUEST\r\nType COMMAND for Query |streamstate to know status\r\n");
        
        return (sb.toString());
    }

    void callbackFunc(int i, String l_msg){
        String tmp_str = l_msg;
        int val = 0;

        switch(i){
            case 0://DeviceMode
                {
                    val = Integer.parseInt(l_msg);
                    c_streamctl.setDeviceMode(val);
                }
                break;
            case 1://Session Initation Mode
                {
                    val = Integer.parseInt(tmp_str);
                    c_streamctl.setSessionInitMode(val);
                }
                break;
            case 2://StreamIn url
                {
                    c_streamctl.setStreamInUrl(tmp_str);
                }
                break;
            case 3://VideoProfile
                {
                    profile = Integer.parseInt(tmp_str);
                }
                break;
            case 4://TransportType
                {
                    tmode = Integer.parseInt(tmp_str);
                }
                break;
            case 5://RTSP Port
                {
                    rport = Integer.parseInt(tmp_str);
                    c_streamctl.setRTSPPort(rport);
                }
                break;
            case 6://TS Port
                {
                    tport = Integer.parseInt(tmp_str);
                    c_streamctl.setTSPort(tport);
                }
                break;
            case 7://RTP VPort
                {
                    rvport = Integer.parseInt(tmp_str);
                    c_streamctl.setRTPVideoPort(rvport);
                }
                break;
            case 8:// RTP APort
                {
                    raport = Integer.parseInt(tmp_str);
                    c_streamctl.setRTPAudioPort(raport);
                }
                break;
            case 9://Videoframerate 
                {
                    vframerate = Integer.parseInt(tmp_str);
                }
                break;
            case 10://Video Bit Rate
            	{
                    vbr = Integer.parseInt(tmp_str);
            	}
            	break;
            case 11://Video Encoding Level
                {
                    venclevel = Integer.parseInt(tmp_str);
                }
                break;
            case 12://IPAddr
                {
                    ip_addr = tmp_str;
                }
                break;
            case 13://START
                {
                    c_streamctl.setStreamOutConfig(ip_addr, resolution, TransportMode.getStringValueFromInt(tmode), VideoEncProfile.getStringValueFromInt(profile), vframerate, vbr, venclevel);
                    c_streamctl.Start();
                }
                break;
            case 14://STOP
                {
                    c_streamctl.Stop();
                }
                break;
            case 15://PAUSE
                {
                    c_streamctl.Pause();
                }
                break;
            case 16://MUTE
                {
                    int mute_flag = Integer.parseInt(tmp_str);
                    if(mute_flag==1)
                        c_streamctl.setStreamMute();
                    else
                        c_streamctl.setStreamUnMute();
                }
                break;
            case 17://UNMUTE
                {
                    int mute_flag = Integer.parseInt(tmp_str);
                    if(mute_flag==1)
                        c_streamctl.setStreamUnMute();
                    else
                        c_streamctl.setStreamMute();
                }
                break;
            case 18://Latency
                {
                	initialLatency = Integer.parseInt(tmp_str);
                    c_streamctl.SetStreamInLatency(initialLatency);
                }
                break;
            case 19://Passwd enable
                {
                    enable_passwd = true;
                    disable_passwd = false;
                }
                break;
            case 20://Passwd disable
                {
                    enable_passwd = false;
                    disable_passwd = true;
                }
                break;
            case 21://USERNAME
                {
                    String uname = tmp_str;
                }
                break;
            case 22://PASSWD
                {
                    String passwd= tmp_str;
                }
                break;
            case 23://X Position
            	{
                    val = Integer.parseInt(l_msg);
            	}
            	break;
            case 24://Y Position
            	{
                    val = Integer.parseInt(l_msg);
            	}
            	break;
            case 25://window width
            	{
                    val = Integer.parseInt(l_msg);
            	}
            	break;
            case 26://window height
            	{
                    val = Integer.parseInt(l_msg);
            	}
            	break;
            default:
                break;
        }	
    }

    private String processFbMessage(String receivedMsg, String msg)
    {
        StringBuilder sb = new StringBuilder(1024);

        if (msg.equalsIgnoreCase("device_ready_fb"))
        {
            sb.append(receivedMsg).append("=").append(c_streamctl.getDeviceReadyStatus());
        }
        else if (msg.equalsIgnoreCase("PROCESSING_FB"))
        {
            sb.append(receivedMsg).append("=").append(c_streamctl.getProcessingStatus());
        }
        else if (msg.equalsIgnoreCase("ELAPSED_SECONDS_FB"))
        {
            sb.append(receivedMsg).append("=").append(c_streamctl.getElapsedSeconds());
        }
        else if (msg.equalsIgnoreCase("STREAM_STATUS_FB"))
        {
            sb.append(receivedMsg).append("=").append(c_streamctl.getStreamStatus());
        }
        else if (msg.equalsIgnoreCase("INITIATOR_ADDRESS_FB"))
        {
            //TODO
            sb.append(receivedMsg).append("=").append("TODO");
            //sb.append(receivedMsg).append("=").append(c_streamctl.getInitatorAddress());
        }
        else if (msg.equalsIgnoreCase("HORIZONTAL_RESOLUTION_FB"))
        {
            sb.append(receivedMsg).append("=").append(c_streamctl.getHorizontalResFb());
        }
        else if (msg.equalsIgnoreCase("VERTICAL_RESOLUTION_FB"))
        {
            sb.append(receivedMsg).append("=").append(c_streamctl.getVerticalResFb());
        }
        else if (msg.equalsIgnoreCase("FPS_RESOLUTION_FB"))
        {
            sb.append(receivedMsg).append("=").append(c_streamctl.getFpsFb());
        }
        else if (msg.equalsIgnoreCase("ASPECT_FB"))
        {
            sb.append(receivedMsg).append("=").append(c_streamctl.getAspectRatioFb());
        }
        else if (msg.equalsIgnoreCase("AUDIO_FORMAT_FB"))
        {
            sb.append(receivedMsg).append("=").append(c_streamctl.getAudioFormatFb());
        }
        else if (msg.equalsIgnoreCase("AUDIO_CHANNELS_FB"))
        {
            sb.append(receivedMsg).append("=").append(c_streamctl.getAudiochannelsFb());
        }
        else{
            replyString="";
            replyString.trim();
            sb.append(replyString);
        } 
        return (sb.toString());
    }
    
    private String processCmdMessage(String receivedMsg, String msg)
    {
        StringBuilder sb = new StringBuilder(1024);
        if(msg.equalsIgnoreCase("streamstate")){//Send StreamState
            int streamState = c_streamctl.getStreamState();
            switch(streamState){
                case 0:
                    replyString ="STREAMING IN";
                    break;
                case 1:
                    replyString ="STREAMING OUT";
                    break;
                case 2:
                    replyString ="Previewing Video";
                    break;
                default:
                    replyString = "Device in IdleMode";
            }
            sb.append(receivedMsg).append("=").append(replyString);
        }
        else if(msg.equalsIgnoreCase("streamurl")){//Send StreamState
            String l_url = c_streamctl.getStreamUrl();
            sb.append(receivedMsg).append("=").append(l_url);
        }
        else if(msg.equalsIgnoreCase("start")){//Send Start status
            String temp = c_streamctl.getStartStatus();
            sb.append(receivedMsg).append("=").append(temp);
        }
        else if(msg.equalsIgnoreCase("stop")){//Send Stop status
            String temp = c_streamctl.getStopStatus();
            sb.append(receivedMsg).append("=").append(temp);
        }
        else if(msg.equalsIgnoreCase("pause")){//Send Pause status
            String temp = c_streamctl.getPauseStatus();
            sb.append(receivedMsg).append("=").append(temp);
        }
        else if (msg.equalsIgnoreCase("hdmiin_sync_detected")) {
            sb.append(receivedMsg).append("=").append(c_streamctl.getHDMIInSyncStatus());
        }
        else if (msg.equalsIgnoreCase("hdmiin_interlaced")) {
            sb.append(receivedMsg).append("=").append(c_streamctl.getHDMIInInterlacing());
        }
        else if (msg.equalsIgnoreCase("hdmiin_horizontal_res_fb"))
        {
            sb.append(receivedMsg).append("=").append(c_streamctl.getHDMIInHorizontalRes());
        }
        else if (msg.equalsIgnoreCase("hdmiin_vertical_res_fb"))
        {
            sb.append(receivedMsg).append("=").append(c_streamctl.getHDMIInVerticalRes());
        }
        else if (msg.equalsIgnoreCase("hdmiin_fps_fb"))
        {
            sb.append(receivedMsg).append("=").append(c_streamctl.getHDMIInFPS());
        }
        else if (msg.equalsIgnoreCase("hdmiin_aspect_ratio"))
        {
            sb.append(receivedMsg).append("=").append(c_streamctl.getHDMIInAspectRatio());
        }
        else if (msg.equalsIgnoreCase("hdmiin_audio_format"))
        {
            sb.append(receivedMsg).append("=").append(c_streamctl.getHDMIInAudioFormat());
        }
        else if (msg.equalsIgnoreCase("hdmiin_audio_channels"))
        {
            sb.append(receivedMsg).append("=").append(c_streamctl.getHDMIInAudioChannels());
        }
        else if (msg.equalsIgnoreCase("hdmiout_sync_detected")) {
            sb.append(receivedMsg).append("=").append(c_streamctl.getHDMIOutSyncStatus());
        }
        else if (msg.equalsIgnoreCase("hdmiout_interlaced")) {
            sb.append(receivedMsg).append("=").append(c_streamctl.getHDMIOutInterlacing());
        }
        else if (msg.equalsIgnoreCase("hdmiout_horizontal_res_fb"))
        {
            sb.append(receivedMsg).append("=").append(c_streamctl.getHDMIOutHorizontalRes());
        }
        else if (msg.equalsIgnoreCase("hdmiout_vertical_res_fb"))
        {
            sb.append(receivedMsg).append("=").append(c_streamctl.getHDMIOutVerticalRes());
        }
        else if (msg.equalsIgnoreCase("hdmiout_fps_fb"))
        {
            sb.append(receivedMsg).append("=").append(c_streamctl.getHDMIOutFPS());
        }
        else if (msg.equalsIgnoreCase("hdmiout_aspect_ratio"))
        {
            sb.append(receivedMsg).append("=").append(c_streamctl.getHDMIOutAspectRatio());
        }
        else if (msg.equalsIgnoreCase("hdmiout_audio_format"))
        {
            sb.append(receivedMsg).append("=").append(c_streamctl.getHDMIOutAudioFormat());
        }
        else if (msg.equalsIgnoreCase("hdmiout_audio_channels"))
        {
            sb.append(receivedMsg).append("=").append(c_streamctl.getHDMIOutAudioChannels());
        }
        else {//QUERY Procssing
            String tmp_str = tokenizer.getStringValueOf(msg);
            Log.d(TAG, "Querying:: searched for "+msg+" and got value of "+tmp_str);
            if(tmp_str!=null){
                replyString = tmp_str ;
                sb.append(receivedMsg).append("=").append(replyString);
            }
            else{
                replyString="";
                replyString.trim();
                sb.append(replyString);
            } 
        }
        return (sb.toString());
    }
   
    public String processReceivedMessage(String receivedMsg){
        //tokenizer.printList();//DEBUG Purpose
        String[] msg = tokenizer.Parse(receivedMsg);
        String reply = ""; 
        for(int i = 0; i< fbArray.length; i++){
            if(fbArray[i].equalsIgnoreCase(msg[0])){
                reply = processFbMessage(receivedMsg, msg[0]); 
            }
        }
        for(int i = 0; i< cmdArray.length; i++){
            if(cmdArray[i].equalsIgnoreCase(msg[0])){
                if(msg.length>1) {//cmd processing
                    callbackFunc(i, msg[1]);
                }
                else{
                    reply = processCmdMessage(receivedMsg, msg[0]); 
                }
            }
        }
        return reply;
    }
}
