package com.crestron.txrxservice;

import com.crestron.txrxservice.CresStreamCtrl;

public class CommandReceiver {
    boolean enable_passwd = false;
    boolean disable_passwd = true;
    StringBuilder l_sb;
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
            return "the given number doesn't match any Status.";
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
            return "the given number doesn't match any Status.";
        }
    }
    
    private final CresStreamCtrl ctl;
    
    public static int VALIDATE_INT(String msg){
        try {
            return Integer.parseInt(msg);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public CommandReceiver(CresStreamCtrl a_crestctrl)
    {
        ctl = a_crestctrl;
    }

    public void setDeviceMode(int mode){
        ctl.setDeviceMode(mode);
    }

    public void setSessionInitation(int mode){
        ctl.setSessionInitMode(mode);
    }
    
    public void SetTMode(int mode){
        ctl.setTMode(TransportMode.getStringValueFromInt(mode));
    }
    
    public void SetVenc (int profile){
        ctl.setStreamProfile(VideoEncProfile.getStringValueFromInt(profile));
    }
    
    public void setRtspPort(int port){
        ctl.setRTSPPort(port);
    }
    
    public void setTsPort (int port){
        ctl.setTSPort(port);
    }
    
    public void setRtpV(int port){
        ctl.setRTPVideoPort(port);
    }
    
    public void setRtpA(int port){
        ctl.setRTPAudioPort(port);
    }
    
    public void setVfr(int vfr){
        ctl.setVFrmRate(vfr);
    }

    public void setVbr(int vbr){
        ctl.setVbitRate(vbr);
    }

    public void EnableTcpInterleave(){
        ctl.EnableTcpInterleave();
    }
    
    public void setIP(String ip){
        ctl.setIpAddress(ip);
    }

    public void setEncodingResolution(int resID){
       ctl.setEncodingResolution(resID);
    }
    
    public void setMute (boolean flag){
        if(flag)
            ctl.setStreamMute();
        else
            ctl.setStreamUnMute();
    }

    public void setUnmute (boolean flag){
        if(flag)
            ctl.setStreamUnMute();
        else
            ctl.setStreamMute();
    }
    
    public void setLatency(int latency){
        ctl.SetStreamInLatency(latency);
    }

    public void passwdEnable(){
        enable_passwd = true;
        disable_passwd = false;
    }

    public void passwdDisable(){
        enable_passwd = false;
        disable_passwd = true;
    }
    
    public void setUserName(String uname){
        String _uname = uname;
    }
    
    public void setPasswd(String passwd){
        String _passwd = passwd;
    }

    public void setStreamUrl(String uri){
        ctl.setStreamInUrl(uri);
    }

    public void setStart(){
        ctl.Start();
    }

    public void setStop(){
        ctl.Stop();
    }

    public void setPause(){
        ctl.Pause();
    }

    public void setXloc(int xloc){
        ctl.setXCoordinates(xloc);
    }

    public void setYloc(int yloc){
        ctl.setYCoordinates(yloc);
    }
    
    public void setDestWidth(int w){
        ctl.setWindowSizeW(w);
    }
    
    public void setDestHeight(int h ){
        ctl.setWindowSizeH(h);
    }

    //Process Feedbacks
    public String getStreamUrl(){
        return ctl.getStreamUrl();
    }


    public String getStartStatus(){
        return ctl.getStartStatus();
    }

    public String getStopStatus(){
        return ctl.getStopStatus();
    }
    
    public String getPauseStatus(){
        return ctl.getPauseStatus();
    }
    
    public String getStreamState(){
        //String replyString;
        int streamState = ctl.getStreamState();
        /*switch(streamState){
            case 0:
                replyString ="STREAMING IN";
                break;
            case 1:
                replyString ="STREAMING OUT";
                break;
            case 2:
                replyString ="Previewing Video";
                break;
            case 3:
                replyString ="Previewing Video and Streaming IN";
                break;
            case 4:
                replyString ="StreaminOut and StreamingIn";
                break;
            default:
                replyString = "Device in IdleMode";
        }
        return replyString;*/
        return Integer.toString(streamState);
    }
    //HDMIIN
    public String getHdmiInSync(){
        return ctl.getHDMIInSyncStatus();
    }

    public String getHdmiInInterlace(){
        return ctl.getHDMIInInterlacing();
    }

    public String getHdmiInHRes(){  
        return ctl.getHDMIInHorizontalRes();
    }

    public String getHdmiInVRes(){
        return ctl.getHDMIInVerticalRes();
    }

    public String  getHdmiInFps(){    
        return ctl.getHDMIInFPS();
    }

    public String getHdmiInAspect(){
        return ctl.getHDMIInAspectRatio();    
    }

    public String getHdmiInAudioFormat()   {
        return ctl.getHDMIInAudioFormat();
    }
    public String getHdmiInAudioChannels() {
        return ctl.getHDMIInAudioChannels();
    }
    //HDMIOut
    public String getHdmiOutSync(){
        return ctl.getHDMIOutSyncStatus();
    }

    public String getHdmiOutInterlaced(){
        return ctl.getHDMIOutInterlacing();
    }

    public String getHdmiOutHRes(){  
        return ctl.getHDMIOutHorizontalRes();
    }

    public String getHdmiOutVRes(){
        return ctl.getHDMIOutVerticalRes();
    }

    public String  getHdmiOutFps(){    
        return ctl.getHDMIOutFPS();
    }

    public String getHdmiOutAspect(){
        return ctl.getHDMIInAspectRatio();    
    }

    public String getHdmiOutAudioFormat()   {
        return ctl.getHDMIOutAudioFormat();
    }

    public String getHdmiOutAudioChannels() {
        return ctl.getHDMIOutAudioChannels();
    }

    public String getProcessingStats(){
        return ctl.getProcessingStatus();
    }

    public String getDeviceReadyStatus(){
        return ctl.getDeviceReadyStatus();
    }
    
    public String getElapsedSeconds(){
        return ctl.getElapsedSeconds();
    }
    
    public String getStatus(){
        return ctl.getStreamStatus();
    }
    
    public String getInitAddress(){
        //TODO
        return "";
        //return ctl.getInitiatorAddress();
    }
    
    public String getHresFb(){
        int hres = ctl.getHorizontalResFb();
        l_sb = new StringBuilder(256);
        l_sb.append(hres);
        return l_sb.toString();
    }

    public String getVresFb(){
        int vres= ctl.getVerticalResFb();
        l_sb = new StringBuilder(256);
        l_sb.append(vres);
        return l_sb.toString();
    }
    
    public String getFpsFb(){
        int fps = ctl.getFpsFb();
        l_sb = new StringBuilder(256);
        l_sb.append(fps);
        return l_sb.toString();
    }
    
    public String getAspectFb(){
        return ctl.getAspectRatioFb();
    }
    
    public String getAudioFormatFb(){
        int afmt= ctl.getAudioFormatFb();
        l_sb = new StringBuilder(256);
        l_sb.append(afmt);
        return l_sb.toString();
    }
    
    public String getAudioChannelsFb(){
        int achannels = ctl.getAudiochannelsFb();
        l_sb = new StringBuilder(256);
        l_sb.append(achannels);
        return l_sb.toString();
    }
}
