package com.crestron.txrxservice;

import java.io.IOException;
import android.util.Log;

public class MessageParser {
    
    String TAG = "TxRx Parser"; 
    String ip_addr = null;// = "127.0.0.1";
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
    
    String[] cmdArray = {"MODE", "SessionInitiation", "STREAMURL", "VENCPROFILE", "TRANSPORTMODE", "RTSPPORT", "TSPORT", "RTPVIDEOPORT", "RTPAUDIOPORT", "VFRAMERATE", "VBITRATE", "VENCLEVEL", "HDMIOUTPUTRES", "IPADDRESS", "START", "STOP", "PAUSE", "MUTESTATE", "LATENCY", 
    					"HDMIIN_HORIZONTAL_RES_FB", "HDMIIN_VERTICAL_RES_FB", "HDMIIN_FPS_FB", 
    					"HDMIOUT_INTERLACED_FB", 
    					"HDMIOUT_DISPLAYBLANK_ENABLED", "HDMIOUT_DISPLAYBLANK_DISABLED",
    					"HDMIOUT_HORIZONTAL_RES_FB", "HDMIOUT_VERTICAL_RES_FB", 
    					"HDMIOUT_FPS_FB", "HDMIOUT_ASPECT_RATIO_FB",
    					"HDMIOUT_AUDIO_FORMAT_FB", "HDMIOUT_AUDIO_CHANNELS_FB",
    					"HDMIOUT_MANUFACTURER_FB", "HDMIOUT_MODELNO_FB", "HDMIOUT_PREFTIMING_FB", "HDMIOUT_SERIALNO_FB",
    					"streamstate"};

    public boolean validateCommand(String targetValue) {
        for(String s: cmdArray){
            if(s.equals(targetValue))
                return true;
        }
        return false;
    }

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
        sb.append("HDMIOUTPUTRES(17=1920x1080, 16=1680x1050 follow join sheet)\r\n");
        sb.append("IPADDRESS(=xxx.xxx.xxx.xxx)\r\n");
        sb.append("MUTESTATE(=1:true/0:false)\r\n");
        sb.append("LATENCY=1000 to 3000 (in msec)\r\n");
        sb.append("START | STOP | PAUSE (=true)\r\n");
        sb.append("HDMIIN_HORIZONTAL_RES_FB\r\n");
        sb.append("HDMIIN_VERTICAL_RES_FB\r\n");
        sb.append("HDMIIN_FPS_FB\r\n");
        //sb.append("HDMIOUT_SYNC_DETECTED_FB\r\n");
        sb.append("HDMIOUT_INTERLACED_FB\r\n");
        //sb.append("HDMIOUT_CEC_ERROR_FB\r\n");
        sb.append("HDMIOUT_DISPLAYBLANK_ENABLED\r\n");
        sb.append("HDMIOUT_DISPLAYBLANK_DISABLED\r\n");
        sb.append("HDMIOUT_HORIZONTAL_RES_FB\r\n");
        sb.append("HDMIOUT_VERTICAL_RES_FB\r\n");
        sb.append("HDMIOUT_FPS_FB\r\n");
        sb.append("HDMIOUT_ASPECT_RATIO_FB\r\n");
        sb.append("HDMIOUT_AUDIO_FORMAT_FB\r\n");
        sb.append("HDMIOUT_AUDIO_CHANNELS_FB\r\n");
        sb.append("HDMIOUT_MANUFACTURER_FB\r\n");
        sb.append("HDMIOUT_MODELNO_FB\r\n");
        sb.append("HDMIOUT_SERIALNO_FB\r\n");
        sb.append("HDMIOUT_PREFTIMING_FB\r\n");
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
            case 12://Resolution
                {
		     resolution = Integer.parseInt(tmp_str);
                }
                break;
            case 13://IPAddr
                {
                    ip_addr = tmp_str;
                }
                break;
            case 14://START
                {
                    c_streamctl.setStreamOutConfig(ip_addr, resolution, TransportMode.getStringValueFromInt(tmode), VideoEncProfile.getStringValueFromInt(profile), vframerate, vbr, venclevel);
                    c_streamctl.Start();
                }
                break;
            case 15://STOP
                {
                    c_streamctl.Stop();
                }
                break;
            case 16://PAUSE
                {
                    c_streamctl.Pause();
                }
                break;
            case 17://MUTE/UNMUTE
                {
                    int mute_flag = Integer.parseInt(tmp_str);
                    if(mute_flag==1)
                        c_streamctl.setStreamMute();
                    else
                        c_streamctl.setStreamUnMute();
                }
                break;
            case 18://Latency
                {
		    initialLatency = Integer.parseInt(tmp_str);
                    c_streamctl.SetStreamInLatency(initialLatency);
                }
                break;
                
            case 23://Display blank enabled
            	{
            		boolean result = false;
            		result = c_streamctl.setDisplayBlankEnabled();
            	}
            	break;
            	
            case 24://Display blank disabled
            	{
            		boolean result = false;
            		result = c_streamctl.setDisplayBlankDisabled();
            	}
            	break;
            	
            default:
                break;
        }	
    }

    public String processReceivedMessage(String receivedMsg){
        StringBuilder sb = new StringBuilder(1024);
        //tokenizer.printList();//DEBUG Purpose
        String[] msg = tokenizer.Parse(receivedMsg);

        for(int i = 0; i< cmdArray.length; i++){
            if(cmdArray[i].equalsIgnoreCase(msg[0])){
                if(msg.length>1) {//cmd processing
                    callbackFunc(i, msg[1]);
                }
                else {
                    if(msg[0].equalsIgnoreCase("streamstate")){//Send StreamState
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
                    else if(msg[0].equalsIgnoreCase("streamurl")){//Send StreamState
                        String l_url = c_streamctl.getStreamUrl();
                        sb.append(receivedMsg).append("=").append(l_url);
                    }
                    else if(msg[0].equalsIgnoreCase("start")){//Send Start status
                        String temp = c_streamctl.getStartStatus();
                        sb.append(receivedMsg).append("=").append(temp);
                    }
                    else if(msg[0].equalsIgnoreCase("stop")){//Send Start status
                        String temp = c_streamctl.getStopStatus();
                        sb.append(receivedMsg).append("=").append(temp);
                    }
                    else if (msg[0].equalsIgnoreCase("hdmiin_horizontal_res_fb"))
                    {
                    	sb.append(receivedMsg).append("=").append(c_streamctl.getHDMIInHorizontalRes());
                    }
                    else if (msg[0].equalsIgnoreCase("hdmiin_vertical_res_fb"))
                    {
                    	sb.append(receivedMsg).append("=").append(c_streamctl.getHDMIInVerticalRes());
                    }
                    else if (msg[0].equalsIgnoreCase("hdmiin_fps_fb"))
                    {
                    	sb.append(receivedMsg).append("=").append(c_streamctl.getHDMIInFPS());
                    }
                    else if (msg[0].equalsIgnoreCase("hdmiout_interlaced_fb"))
                    {
                    	sb.append(receivedMsg).append("=").append(c_streamctl.getHDMIOutInterlacing());
                    }
                    else if (msg[0].equalsIgnoreCase("hdmiout_horizontal_res_fb"))
                    {
                    	sb.append(receivedMsg).append("=").append(c_streamctl.getHDMIOutHorizontalRes());
                    }
                    else if (msg[0].equalsIgnoreCase("hdmiout_vertical_res_fb"))
                    {
                    	sb.append(receivedMsg).append("=").append(c_streamctl.getHDMIOutVerticalRes());
                    }
                    else if (msg[0].equalsIgnoreCase("hdmiout_fps_fb"))
                    {
                    	sb.append(receivedMsg).append("=").append(c_streamctl.getHDMIOutFPS());
                    }
                    else if (msg[0].equalsIgnoreCase("hdmiout_aspect_ratio_fb"))
                    {
                    	sb.append(receivedMsg).append("=").append(c_streamctl.getHDMIOutAspectRatio());
                    }
                    else if (msg[0].equalsIgnoreCase("hdmiout_audio_format_fb"))
                    {
                    	sb.append(receivedMsg).append("=").append(c_streamctl.getHDMIOutAudioFormat());
                    }
                    else if (msg[0].equalsIgnoreCase("hdmiout_audio_channels_fb"))
                    {
                    	sb.append(receivedMsg).append("=").append(c_streamctl.getHDMIOutAudioChannels());
                    }
                    else if (msg[0].equalsIgnoreCase("hdmiout_manufacturer_fb")) {
                    	sb.append(receivedMsg).append("=").append(c_streamctl.getHDMIOutManf());
                    }
                    else if (msg[0].equalsIgnoreCase("hdmiout_modelno_fb")) {
                    	sb.append(receivedMsg).append("=").append(c_streamctl.getHDMIOutModelNo());
                    }
                    else if (msg[0].equalsIgnoreCase("hdmiout_preftiming_fb")) {
                    	sb.append(receivedMsg).append("=").append(c_streamctl.getHDMIOutPrefTiming());
                    }
                    else if (msg[0].equalsIgnoreCase("hdmiout_serialno_fb")) {
                    	sb.append(receivedMsg).append("=").append(c_streamctl.getHDMIOutSerialNo());
                    }
                    else {//QUERY Procssing
                        String tmp_str = tokenizer.getStringValueOf(msg[0]);
                        Log.d(TAG, "Querying:: searched for "+msg[0]+" and got value of "+tmp_str);
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
                }
            }
        }
        return (sb.toString());
    }
}
