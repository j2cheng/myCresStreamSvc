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
    
    String[] cmdArray = {"MODE", "SessionInitiation", "STREAMURL", "VENCPROFILE", "TRANSPORTMODE", "RTSPPORT", "TSPORT", "RTPVIDEOPORT", "RTPAUDIOPORT", "VFRAMERATE", "VBITRATE", "VENCLEVEL", "HDMIOUTPUTRES", "IPADDRESS", "START", "STOP", "PAUSE", "MUTESTATE", "LATENCY", "streamstate"};

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
        String str1= "MODE (= 0:STREAMIN 1: STREAMOUT 2:HDMIPREVIEW)\r\n";
        String str2= "SessionInitiation (= 0: ByReceiver 1: ByTransmitter 2: MCastviaRTSP 3: MCastviaUDP)\r\n";
        String str3= "TRANSPORTMODE (= 0: RTP 1: TS_RTP 2: TS_UDP)\r\n";
        String str4= "VENCPROFILE (= 0:HighProfile 1:MainProfile 2:BaseProfile)\r\n";
        String str5= "STREAMURL(= any url) \r\n";
        String str6= "RTSPPORT(= 1024 to 49151)\r\n";
        String str7= "TSPORT (= 1024 to 49151)\r\n";
        String str8= "RTPVIDEOPORT (= 1024 to 49151)\r\n";
        String str9= "RTPAUDIOPORT (= 1024 to 49151)\r\n";
        String str10= "VFRAMERATE (= 60 50 30 24)\r\n";
        String str11= "VBITRATE (= 96 to 25000kbps)\r\n";
        String str12= "VENCLEVEL (= 4096:for 4.1 level, 8192:for 4.2 level)\r\n";
        String str13= "HDMIOUTPUTRES(17=1920x1080, 16=1680x1050 follow join sheet)\r\n";
        String str14= "IPADDRESS(=xxx.xxx.xxx.xxx)\r\n";
        String str15= "MUTESTATE(=1:true/0:false)\r\n";
        String str16= "LATENCY=1000 to 3000 (in msec)\r\n";
        String str17= "START | STOP | PAUSE (=true)\r\n";
        String str18= "UPDATEREQUEST\r\nType COMMAND for Query |streamstate to know status\r\n";
        sb.append(str1).append(str2).append(str3).append(str4).append(str5).append(str6).append(str7).append(str8).append(str9).append(str10).append(str11).append(str12).append(str13).append(str14).append(str15).append(str16).append(str17).append(str18);
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
