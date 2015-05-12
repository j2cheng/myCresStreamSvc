package com.crestron.txrxservice;

import com.crestron.txrxservice.CresStreamCtrl;

public class CommandReceiver {
    StringBuilder l_sb;
//    public enum VideoEncProfile {
//        BP(2), MP(1), HP(0);
//        private final int value;
//
//        private VideoEncProfile(int value) {
//            this.value = value;
//        }
//
//        public int getValue() {
//            return value;
//        }
//        public static String getStringValueFromInt(int i) {
//            for (VideoEncProfile status : VideoEncProfile.values()) {
//                if (status.getValue() == i) {
//                    return status.toString();
//                }
//            }
//            return "the given number doesn't match any Status.";
//        }
//    }

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

    public void setDeviceMode(int mode, int sessId){
        ctl.setDeviceMode(mode, sessId);
    }

    public void setSessionInitation(int mode, int sessId){
    	ctl.userSettings.setSessionInitiation(mode, sessId);
    }
    
    public void SetTMode(int mode, int sessId){
        ctl.setTMode(mode, sessId);
    }
    
    public void SetVenc (int profile, int sessId){    	
        ctl.setStreamProfile(UserSettings.VideoEncProfile.fromInteger(profile), sessId);
    }
    
    public void setRtspPort(int port, int sessId){
        ctl.setRTSPPort(sessId, port);
    }
    
    public void setTsPort (int port, int sessId){
        ctl.setTSPort(sessId, port);
    }
    
    public void setRtpV(int port, int sessId){
        ctl.setRTPVideoPort(sessId, port);
    }
    
    public void setRtpA(int port, int sessId){
        ctl.setRTPAudioPort(sessId, port);
    }
    
    public void setVfr(int vfr, int sessId){
        ctl.setVFrmRate(vfr, sessId);
    }

    public void setVbr(int vbr, int sessId){
        ctl.setVbitRate(vbr, sessId);
    }

    public void EnableTcpInterleave(int sessId){
        ctl.EnableTcpInterleave(sessId);
    }
    
    public void setMulticastIpAddress(String ip, int sessId){
        ctl.setMulticastIpAddress(ip, sessId);
    }

    public void setEncodingResolution(int resID, int sessId){
       ctl.setEncodingResolution(resID, sessId);
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
    
    public void setLatency(int latency, int sessId){
        ctl.SetStreamInLatency(latency, sessId);
    }

    public void passwdEnable(int sessId){
        ctl.SetPasswdEnable(sessId);
    }

    public void passwdDisable(int sessId){
        ctl.SetPasswdDisable(sessId);
    }
    
    public void setUserName(String uname, int sessId){
        ctl.SetUserName(uname, sessId);
    }
    
    public void setPasswd(String passwd, int sessId){
        ctl.SetPasswd(passwd, sessId);
    }

    public void setStreamUrl(String uri, int sessId){
        ctl.setStreamInUrl(uri, sessId);
    }

    public void setStart(int sessId){
        ctl.Start(sessId);
    }

    public void setStop(int sessId){
        ctl.Stop(sessId);
    }

    public void setPause(int sessId){
        ctl.Pause(sessId);
    }

    public void setXloc(int xloc, int sessId){
        ctl.setXCoordinates(xloc, sessId);
    }

    public void setYloc(int yloc, int sessId){
        ctl.setYCoordinates(yloc, sessId);
    }
    
    public void setDestWidth(int w, int sessId){
        ctl.setWindowSizeW(w, sessId);
    }
    
    public void setDestHeight(int h, int sessId){
        ctl.setWindowSizeH(h, sessId);
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
    
    public String getStreamState(int sessId){
        //String replyString;
        int streamState = (ctl.userSettings.getStreamState(sessId)).getValue();
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
    
    public String getHresFb(boolean streamIn){
        return "";
/*		int hres = ctl.getHorizontalResFb(streamIn);
        l_sb = new StringBuilder(256);
        l_sb.append(hres);
        return l_sb.toString();*/
    }

    public String getVresFb(boolean streamIn){
        return "";
        /*int vres= ctl.getVerticalResFb(streamIn);
        l_sb = new StringBuilder(256);
        l_sb.append(vres);
        return l_sb.toString();*/
    }
    
    public String getFpsFb(boolean streamIn){
        return "";
        /*int fps = ctl.getFpsFb(streamIn);
        l_sb = new StringBuilder(256);
        l_sb.append(fps);
        return l_sb.toString();*/
    }
    
    public String getAspectFb(boolean streamIn){
        return "";
        //return ctl.getAspectRatioFb(streamIn);
    }
    
    public String getAudioFormatFb(boolean streamIn){
        return "";
        /*int afmt= ctl.getAudioFormatFb(streamIn);
        l_sb = new StringBuilder(256);
        l_sb.append(afmt);
        return l_sb.toString();*/
    }
    
    public String getAudioChannelsFb(boolean streamIn){
        return "";
        /*int achannels = ctl.getAudiochannelsFb(streamIn);
        l_sb = new StringBuilder(256);
        l_sb.append(achannels);
        return l_sb.toString();*/
    }
    
    //TODO: we should really be calling userSettings directly and not adding an extra layer through ctl
    public String getXloc(int sessId){
        return Integer.toString(ctl.userSettings.getXloc(sessId));
    }

    public String getYloc(int sessId){
        return Integer.toString(ctl.userSettings.getYloc(sessId));
    }
    
    public String getDestWidth(int sessId){
        return Integer.toString(ctl.userSettings.getW(sessId));
    }
    
    public String getDestHeight(int sessId){
        return Integer.toString(ctl.userSettings.getH(sessId));
    }
    
    public String getDeviceMode(int sessId){
    	return Integer.toString(ctl.userSettings.getMode(sessId));
    }
    
    public String getSessionInitiation(int sessId){
    	return Integer.toString(ctl.userSettings.getSessionInitiation(sessId));
    }
    
    public String getTransportMode(int sessId){
    	return Integer.toString(ctl.userSettings.getTransportMode(sessId));
    }
    
    public String getStreamProfile(int sessId){
    	return Integer.toString(ctl.userSettings.getStreamProfile(sessId).getVEncProfile());
    }
    
    public String getRtspPort(int sessId){
    	return Integer.toString(ctl.userSettings.getRtspPort(sessId));
    }
    
    public String getTsPort(int sessId){
    	return Integer.toString(ctl.userSettings.getTsPort(sessId));
    }
    
    public String getRtpVideoPort(int sessId){
    	return Integer.toString(ctl.userSettings.getRtpVideoPort(sessId));
    }
    
    public String getRtpAudioPort(int sessId){
    	return Integer.toString(ctl.userSettings.getRtpAudioPort(sessId));
    }
    
    public String getEncodingFramerate(int sessId){
    	return Integer.toString(ctl.userSettings.getEncodingFramerate(sessId));
    }
    
    public String getBitrate(int sessId){
    	return Integer.toString(ctl.userSettings.getBitrate(sessId));
    }

    public String getMulticastIpAddress(int sessId){
    	return ctl.userSettings.getMulticastAddress(sessId);
    }
    
    public String getEncodingResolution(int sessId){
    	return Integer.toString(ctl.userSettings.getEncodingResolution(sessId));
    }
    
    public String getMute(){
    	return String.valueOf(ctl.userSettings.isAudioMute());
    }
    
    public String getUnmute(){
    	return String.valueOf(ctl.userSettings.isAudioUnmute());
    }
    
    public String getLatency(int sessId){
    	return String.valueOf(ctl.userSettings.getStreamingBuffer(sessId));
    }
    
    public String getPasswordEnable(int sessId){
    	return String.valueOf(ctl.userSettings.isPasswordEnable(sessId));
    }
    
    public String getPasswordDisable(int sessId){
    	return String.valueOf(ctl.userSettings.isPasswordDisable(sessId));
    }
    
    public String getUsername(int sessId){
    	return ctl.userSettings.getUserName(sessId);
    }
    
    public String getPassword(int sessId){
    	return ctl.userSettings.getPassword(sessId);
    }
}
