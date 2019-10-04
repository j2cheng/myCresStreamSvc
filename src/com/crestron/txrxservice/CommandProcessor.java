
package com.crestron.txrxservice;

import android.util.Log;

import com.crestron.txrxservice.CresStreamCtrl;
import com.crestron.txrxservice.CresStreamCtrl.DeviceMode;
import com.crestron.txrxservice.CresStreamCtrl.StreamState;

class CrestronCommand implements CommandIf {
	CresStreamCtrl ctrl = null;
    String msg = null;
    String setFbMsg = null;
    int sessId = 0;
    protected static final String TAG="TxRx Command Processor";
    
    public CrestronCommand(CresStreamCtrl ctrl, String arg, int sessId) {
        this.ctrl = ctrl;
        this.msg = arg;
        this.setFbMsg = arg;
        this.sessId = sessId;
    }
    
    public CrestronCommand(CresStreamCtrl ctrl, String arg) {
        this.ctrl = ctrl;
        this.msg = arg;
		this.setFbMsg = arg;
    }
    
    //Should override if join should be be acted upon
    public void execute() {
        return;
    }
    
    //Should override if join should be included in update request
    public String getFeedbackMsg() {
        return msg;  
    }
    
    public void setFbMsg(String arg) {
    	setFbMsg = arg;
    }
    
    public String getSetFbMsg() {
    	return setFbMsg;
    }
    
    public void setVars(String arg, int sessId)
    {
    	this.msg = arg;
        this.sessId = sessId;
    }
    
    public void setVars(String arg)
    {
    	this.msg = arg;
    }
    
    public static int VALIDATE_INT(String msg){
        try {
            return Integer.parseInt(msg);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    public static int VALIDATE_HEXINT(String msg){
    	int index=0;
    	if (msg.startsWith("0x"))
    	{
    		index += 2;
    	}
        try {
            return Integer.parseInt(msg.substring(index), 16);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    public String getHresFb(boolean streamIn){
    	if (streamIn)
    		return String.valueOf(ctrl.userSettings.getStreamInHorizontalResolution());
    	else
    	{
    		try {
    			return String.valueOf(ctrl.cam_streaming.getStreamOutWidth());
    		} catch (Exception e) { return "0"; }
    	}
    		
    }

    public String getVresFb(boolean streamIn){
    	if (streamIn)
    		return String.valueOf(ctrl.userSettings.getStreamInVerticalResolution());
    	else
    	{
    		try {
    			return String.valueOf(ctrl.cam_streaming.getStreamOutHeight());
    		} catch (Exception e) { return "0"; }
    	}    		
    }
    
    public String getFpsFb(boolean streamIn){
    	if (streamIn)
    		return String.valueOf(ctrl.userSettings.getStreamInFPS());
    	else
    	{
    		try {
    			return String.valueOf(ctrl.cam_streaming.getStreamOutFpsFb());
    		} catch (Exception e) { return "0"; }
    	}   
    }
    
    public String getAspectFb(boolean streamIn){
    	if (streamIn)
    		return String.valueOf(ctrl.userSettings.getStreamInAspectRatio());
    	else
    	{
    		try {
    			return String.valueOf(ctrl.cam_streaming.getStreamOutAspectRatioFb());
    		} catch (Exception e) { return "0"; }
    	}  
    }
    
    public String getAudioFormatFb(boolean streamIn){
    	if (streamIn)
    		return String.valueOf(ctrl.streamPlay.getMediaPlayerAudioFormatFb());
    	else
    	{
    		try {
    			return String.valueOf(ctrl.cam_streaming.getStreamOutAudioFormatFb());
    		} catch (Exception e) { return "0"; }
    	} 
    }
    
    public String getAudioChannelsFb(boolean streamIn){
    	if (streamIn)
    		return  String.valueOf(ctrl.streamPlay.getMediaPlayerAudiochannelsFb());
    	else
    	{
    		try {
    			return String.valueOf(ctrl.cam_streaming.getStreamOutAudiochannelsFb());
    		} catch (Exception e) { return "0"; }
    	}
    }
}


class DeviceCommand extends CrestronCommand {
	
    public DeviceCommand(CresStreamCtrl ctrl, String arg, int sessId) {
		super (ctrl, arg, sessId);
    }

    @Override
        public void execute() {
            ctrl.setDeviceMode(VALIDATE_INT(msg), sessId);
        }
        public String getFeedbackMsg() {
            return Integer.toString(ctrl.userSettings.getMode(sessId));
        }
}

class ServiceModeCommand extends CrestronCommand {
	
    public ServiceModeCommand(CresStreamCtrl ctrl, String arg) {
		super (ctrl, arg);
    }

    @Override
    public void execute() {
    	ctrl.setServiceMode(VALIDATE_INT(msg));
    }
    public String getFeedbackMsg() {
        return Integer.toString(ctrl.userSettings.getServiceMode());
    }
}

class SessionInitiationCommand extends CrestronCommand {
    
    public SessionInitiationCommand(CresStreamCtrl ctrl, String arg, int sessId) {
		super(ctrl, arg, sessId);
    }

    @Override
        public void execute() {
            ctrl.setSessionInitiation(VALIDATE_INT(msg), sessId);
        }
        public String getFeedbackMsg() {
            return Integer.toString(ctrl.userSettings.getSessionInitiation(sessId));
        }
}

class TModeCommand extends CrestronCommand {

    public TModeCommand(CresStreamCtrl ctrl, String arg, int sessId) {
		super(ctrl, arg, sessId);
    }

    @Override
        public void execute() {
            ctrl.userSettings.setTransportMode(VALIDATE_INT(msg), sessId);
        }
        public String getFeedbackMsg() {
        	return Integer.toString(ctrl.userSettings.getTransportMode(sessId));
        }
}

class VencCommand extends CrestronCommand{

    public VencCommand(CresStreamCtrl ctrl, String arg, int sessId) {
        super(ctrl, arg, sessId);
    }

    @Override
        public void execute() {
            ctrl.userSettings.setStreamProfile(UserSettings.VideoEncProfile.fromInteger(VALIDATE_INT(msg)), sessId);
        }
        public String getFeedbackMsg() {
        	return Integer.toString(ctrl.userSettings.getStreamProfile(sessId).getVEncProfileUserEnum());
        }
}

class RtspPortCommand extends CrestronCommand {

    public RtspPortCommand(CresStreamCtrl ctrl, String arg, int sessId) {
        super (ctrl, arg, sessId);
    }

    @Override
        public void execute() {
    	ctrl.userSettings.setRtspPort(VALIDATE_INT(msg), sessId);
        }
        public String getFeedbackMsg() {
            return Integer.toString(ctrl.userSettings.getRtspPort(sessId));
        }
}

class TsPortCommand extends CrestronCommand {

    public TsPortCommand(CresStreamCtrl ctrl, String arg, int sessId) {
       super (ctrl, arg, sessId);
    }

    @Override
        public void execute() {
            ctrl.userSettings.setTsPort(VALIDATE_INT(msg), sessId);
        }
        public String getFeedbackMsg() {
        	return Integer.toString(ctrl.userSettings.getTsPort(sessId));
        }
}

class RtpVCommand extends CrestronCommand {

    public RtpVCommand(CresStreamCtrl ctrl, String arg, int sessId) {
        super(ctrl, arg, sessId);
    }

    @Override
        public void execute() {
            ctrl.userSettings.setRtpVideoPort(VALIDATE_INT(msg), sessId);
        }
        public String getFeedbackMsg() {
        	return Integer.toString(ctrl.userSettings.getRtpVideoPort(sessId));
        }
}

class RtpACommand extends CrestronCommand {
	
    public RtpACommand(CresStreamCtrl ctrl, String arg, int sessId) {
        super(ctrl, arg, sessId);
    }

    @Override
        public void execute() {
            ctrl.userSettings.setRtpAudioPort(VALIDATE_INT(msg), sessId);
        }
        public String getFeedbackMsg() {
        	return Integer.toString(ctrl.userSettings.getRtpAudioPort(sessId));
        }
}

class VfrCommand extends CrestronCommand {

    public VfrCommand(CresStreamCtrl ctrl, String arg, int sessId) {
        super(ctrl, arg, sessId);
    }

    @Override
        public void execute() {
            ctrl.userSettings.setEncodingFramerate(VALIDATE_INT(msg), sessId);
        }
        public String getFeedbackMsg() {
            return Integer.toString(ctrl.userSettings.getEncodingFramerate(sessId));
        }
}

class VbrCommand extends CrestronCommand {

    public VbrCommand(CresStreamCtrl ctrl, String arg, int sessId) {
        super(ctrl, arg, sessId);
    }

    @Override
        public void execute() {
            ctrl.userSettings.setBitrate(VALIDATE_INT(msg), sessId);
        }
        public String getFeedbackMsg() {
			if (ctrl.userSettings.getMode(sessId) == DeviceMode.STREAM_IN.ordinal())
				return Integer.toString(ctrl.streamPlay.getStreamInBitrate());
			else
				return Integer.toString(ctrl.userSettings.getBitrate(sessId));
        }
}

class TcpInterleaveCommand extends CrestronCommand {

    public TcpInterleaveCommand(CresStreamCtrl ctrl, String arg, int sessId) {
        super(ctrl, arg, sessId);
    }

    @Override
        public void execute() {
            ctrl.EnableTcpInterleave(VALIDATE_INT(msg),sessId);
        }
        public String getFeedbackMsg() {
        	return Integer.toString(ctrl.getTcpInterleave(sessId));
        }
}

class MulticastIpaddrCommand extends CrestronCommand {

    public MulticastIpaddrCommand(CresStreamCtrl ctrl, String arg, int sessId) {
        super(ctrl, arg, sessId);
    }

    @Override
        public void execute() {
            ctrl.userSettings.setMulticastAddress(msg, sessId);
        }
        public String getFeedbackMsg() {
            return ctrl.userSettings.getMulticastAddress(sessId);
        }
}

class EncodingResolutionCommand extends CrestronCommand {

    public EncodingResolutionCommand(CresStreamCtrl ctrl, String arg, int sessId) {
		super(ctrl, arg, sessId);
    }

    @Override
        public void execute() {
            ctrl.userSettings.setEncodingResolution(VALIDATE_INT(msg), sessId);
        }
        public String getFeedbackMsg() {
            return Integer.toString(ctrl.userSettings.getEncodingResolution(sessId));
        }
}

class SetVolumeCommand extends CrestronCommand {

    public SetVolumeCommand(CresStreamCtrl ctrl, String arg) { 
        super(ctrl, arg);
    }

    @Override
        public void execute() {
    		double volume = Double.valueOf(msg);
            ctrl.setStreamVolume(volume);
        }
        public String getFeedbackMsg() {
            return String.valueOf(ctrl.userSettings.getUserRequestedVolume());
        }
}

class MuteCommand extends CrestronCommand{

    public MuteCommand(CresStreamCtrl ctrl, String arg) {
        super(ctrl, arg);
    }

    @Override
        public void execute() {
            boolean val = Boolean.valueOf(msg);
            if(val)
                ctrl.setStreamMute();
        }
        public String getFeedbackMsg() {
            return String.valueOf(ctrl.userSettings.isAudioMute());
        }
}

class UnmuteCommand extends CrestronCommand{

    public UnmuteCommand(CresStreamCtrl ctrl, String arg) {
        super(ctrl, arg);
    }

    @Override
        public void execute() {
            boolean val = Boolean.valueOf(msg);
			if(val)
            ctrl.setStreamUnMute();
        }
        public String getFeedbackMsg() {
        	return String.valueOf(ctrl.userSettings.isAudioUnmute());
        }
}

class HdmiInAudioCommand extends CrestronCommand {

    public HdmiInAudioCommand(CresStreamCtrl ctrl, String arg) {
		super(ctrl, arg);
    }

    @Override
        public void execute() {
            boolean val = Boolean.valueOf(msg);
            ctrl.setProcessHdmiInAudio(val);
        }
        public String getFeedbackMsg() {
        	return String.valueOf(ctrl.userSettings.isProcessHdmiInAudio());
        }
}

class AutomaticInitiationModeCommand extends CrestronCommand {

    public AutomaticInitiationModeCommand(CresStreamCtrl ctrl, String arg) {
		super(ctrl, arg);
    }

    @Override
        public void execute() {
            boolean val = Boolean.valueOf(msg);
            ctrl.setAutomaticInitiationModeEnabled(val);
        }
        public String getFeedbackMsg() {
        	return String.valueOf(ctrl.getAutomaticInitiationMode());
        }
}

class LatencyCommand extends CrestronCommand {

    public LatencyCommand(CresStreamCtrl ctrl, String arg, int sessId) {
        super(ctrl, arg, sessId);
    }

    @Override
        public void execute() {
           ctrl.userSettings.setStreamingBuffer(VALIDATE_INT(msg), sessId);// ctrl.setLatency(VALIDATE_INT(msg), sessId);
        }
        public String getFeedbackMsg() {
            return String.valueOf(ctrl.userSettings.getStreamingBuffer(sessId));//return ctrl.getLatency(sessId);
        }
}

class PasswdEnableCommand extends CrestronCommand {

    public PasswdEnableCommand(CresStreamCtrl ctrl, String arg, int sessId) {
        super(ctrl, arg, sessId);
    }

    @Override
        public void execute() {
	    	boolean val = Boolean.valueOf(msg);
			if (val)
            ctrl.SetPasswdEnable(sessId);
        }
        public String getFeedbackMsg() {
            return String.valueOf(ctrl.userSettings.isPasswordEnable(sessId));
        }
}

class PasswdDisableCommand extends CrestronCommand {
	
    public PasswdDisableCommand(CresStreamCtrl ctrl, String arg, int sessId) {
        super(ctrl, arg, sessId);
    }

    @Override
        public void execute() {
	    	boolean val = Boolean.valueOf(msg);
            if (val)
				ctrl.SetPasswdDisable(sessId);
        }
        public String getFeedbackMsg() {
            return String.valueOf(ctrl.userSettings.isPasswordDisable(sessId));
        }
}

class UserCommand extends CrestronCommand {
	
    public UserCommand(CresStreamCtrl ctrl, String arg, int sessId) {
        super(ctrl, arg, sessId);
    }

    @Override
        public void execute() {
            ctrl.SetUserName(msg, sessId);
        }
        public String getFeedbackMsg() {
            return ctrl.userSettings.getUserName(sessId);
        }
}

class PasswdCommand extends CrestronCommand {
	
    public PasswdCommand(CresStreamCtrl ctrl, String arg, int sessId) {
        super(ctrl, arg, sessId);
    }

    @Override
        public void execute() {
            ctrl.SetPasswd(msg, sessId);
        }
        public String getFeedbackMsg() {
            return ctrl.userSettings.getPassword(sessId);
        }
}

class StreamUrlCommand extends CrestronCommand {
	
    public StreamUrlCommand(CresStreamCtrl ctrl, String arg, int sessId) {
        super(ctrl, arg, sessId);
    }

    @Override
    public void execute() {
    	if (ctrl.userSettings.getMode(sessId) == DeviceMode.STREAM_IN.ordinal())
    		ctrl.setStreamInUrl(msg, sessId);
    	else if (ctrl.userSettings.getMode(sessId) == DeviceMode.WBS_STREAM_IN.ordinal())
    		ctrl.setWbsStreamUrl(msg, sessId);
    	else
    		ctrl.setStreamOutUrl(msg, sessId);
    }
    public String getFeedbackMsg() {
    	return ctrl.getStreamUrl(sessId);
    }
}

class ProxyEnableCommand extends CrestronCommand {
	
    public ProxyEnableCommand(CresStreamCtrl ctrl, String arg, int sessId) {
        super(ctrl, arg, sessId);
    }

    @Override
        public void execute() {
	    	boolean val = Boolean.valueOf(msg);
            ctrl.userSettings.setProxyEnable(val, sessId);
        }
        public String getFeedbackMsg() {
            return msg;
        }
}

class DecodeInternalRtspPortCommand extends CrestronCommand {
	
    public DecodeInternalRtspPortCommand(CresStreamCtrl ctrl, String arg, int sessId) {
        super(ctrl, arg, sessId);
    }

    @Override
        public void execute() {
            ctrl.userSettings.setDecodeInternalRtspPort(VALIDATE_INT(msg), sessId);
        }
    
    @Override
    public String getFeedbackMsg() {
        return String.valueOf(ctrl.userSettings.getDecodeInternalRtspPort(sessId));
    }
}

class HdcpEncryptCommand extends CrestronCommand {
	
    public HdcpEncryptCommand(CresStreamCtrl ctrl, String arg, int sessId) {
        super(ctrl, arg, sessId);
    }

    @Override
        public void execute() {
	    	boolean val = Boolean.valueOf(msg);
            ctrl.setHdcpEncrypt(val, sessId);
        }
        public String getFeedbackMsg() {
            return msg;
        }
}

class TxHdcpActiveCommand extends CrestronCommand {
	
    public TxHdcpActiveCommand(CresStreamCtrl ctrl, String arg, int sessId) {
        super(ctrl, arg, sessId);
    }

    @Override
        public void execute() {
	    	boolean val = Boolean.valueOf(msg);
            ctrl.setTxHdcpActive(val, sessId);
        }
        public String getFeedbackMsg() {
            return msg;
        }
}

class InternalRtspPortCommand extends CrestronCommand {

    public InternalRtspPortCommand(CresStreamCtrl ctrl, String arg, int sessId) {
        super(ctrl, arg, sessId);
    }

    @Override
        public void execute() {
            ctrl.userSettings.setInternalRtspPort(VALIDATE_INT(msg), sessId);
        }
        public String getFeedbackMsg() {
            return msg;
        }
}

class StartCommand extends CrestronCommand {

    public StartCommand(CresStreamCtrl ctrl, String arg, int sessId) {
        super(ctrl, arg, sessId);
    }

    @Override
        public void execute() {
            boolean val = Boolean.valueOf(msg);
    		Log.i(TAG, "executeStart: sessId="+sessId+"  val="+val);
            if(val){
				ctrl.userSettings.setUserRequestedStreamState(StreamState.STARTED, sessId);
				ctrl.Start(sessId);
			}
        }
        public String getFeedbackMsg() {
            return ctrl.getStartStatus();
        }
}

class StopCommand extends CrestronCommand {
    boolean fullStop;

    public StopCommand(CresStreamCtrl ctrl, String arg, int sessId, boolean fullStopRequested) {
        super(ctrl, arg, sessId);
        this.fullStop = fullStopRequested;
    }

    @Override
        public void execute() {
            boolean val = Boolean.valueOf(msg);
			Log.i(TAG, "executeStop: sessId="+sessId+"  val="+val);
            if(val){
				ctrl.userSettings.setUserRequestedStreamState(StreamState.STOPPED, sessId);
				ctrl.Stop(sessId, fullStop);
			}
        }
        public String getFeedbackMsg() {
            return ctrl.getStopStatus();
        }
}

class PauseCommand extends CrestronCommand {

    public PauseCommand(CresStreamCtrl ctrl, String arg, int sessId) {
        super(ctrl, arg, sessId);
    }

    @Override
        public void execute() {
            boolean val = Boolean.valueOf(msg);
			Log.i(TAG, "executePause: sessId="+sessId+"  val="+val);
            if(val){
				ctrl.userSettings.setUserRequestedStreamState(StreamState.PAUSED, sessId);
				ctrl.Pause(sessId);
			}
        }
        public String getFeedbackMsg() {
            return ctrl.getPauseStatus();
        }
}

class StretchVideoCommand extends CrestronCommand {

    public StretchVideoCommand(CresStreamCtrl ctrl, String arg, int sessId) {
		super(ctrl, arg, sessId);
    }

    @Override
        public  void execute() {
            ctrl.setStretchVideo(VALIDATE_INT(msg), sessId);
        }
        public String getFeedbackMsg() {
            return Integer.toString(ctrl.userSettings.getStretchVideo(sessId));
        }
}

class XlocCommand extends CrestronCommand {

    public XlocCommand(CresStreamCtrl ctrl, String arg, int sessId) {
		super(ctrl, arg, sessId);
    }

    @Override
        public  void execute() {
            ctrl.setXCoordinates(VALIDATE_INT(msg), sessId);
        }
        public String getFeedbackMsg() {
            return Integer.toString(ctrl.userSettings.getXloc(sessId));
        }
}

class YlocCommand extends CrestronCommand {
	
	public YlocCommand(CresStreamCtrl ctrl, String arg, int sessId) {
		super(ctrl, arg, sessId);
    }

    @Override
        public void execute() {
            ctrl.setYCoordinates(VALIDATE_INT(msg), sessId);
        }
        public String getFeedbackMsg() {
            return Integer.toString(ctrl.userSettings.getYloc(sessId));
        }
}

class DestWidthCommand extends CrestronCommand {

    public DestWidthCommand(CresStreamCtrl ctrl, String arg, int sessId) {
		super (ctrl, arg, sessId);
    }

    @Override
        public void execute() {
            ctrl.setWindowSizeW(VALIDATE_INT(msg), sessId);
        }
        public String getFeedbackMsg() {
            return Integer.toString(ctrl.userSettings.getW(sessId));
        }
}

class DestHeightCommand extends CrestronCommand {

    public DestHeightCommand(CresStreamCtrl ctrl, String arg, int sessId) {
        super(ctrl, arg, sessId);
    }

    @Override
        public void execute() {
            ctrl.setWindowSizeH(VALIDATE_INT(msg), sessId);
        }
        public String getFeedbackMsg() {
            return Integer.toString(ctrl.userSettings.getH(sessId));
        }
}

class DestZOrderCommand extends CrestronCommand {

    public DestZOrderCommand(CresStreamCtrl ctrl, String arg, int sessId) {
        super(ctrl, arg, sessId);
    }

    @Override
        public void execute() {
            ctrl.setWindowSizeZ(VALIDATE_INT(msg), sessId);
        }
        public String getFeedbackMsg() {
            return Integer.toString(ctrl.userSettings.getZ(sessId));
        }
}

class WindowDimensionCommand extends CrestronCommand {

	public WindowDimensionCommand(CresStreamCtrl ctrl, String arg, int sessId) {
		super(ctrl, arg, sessId);
	}

	@Override
	public void execute() {
		String[] tokens = msg.split("[,]"); // Format should be "X,Y,Width,Height"
		if (tokens.length == 4)
		{
			ctrl.setWindowDimensions(
					VALIDATE_INT(tokens[0]), 
					VALIDATE_INT(tokens[1]), 
					VALIDATE_INT(tokens[2]), 
					VALIDATE_INT(tokens[3]), 
					sessId);
		}
	}
	// Feedbacks will get sent individually when x,y,width,height are queried
}

class ExternalHdcpStatusCommand extends CrestronCommand {
	
    public ExternalHdcpStatusCommand(CresStreamCtrl ctrl, String arg) {
        super(ctrl, arg);
    }

    @Override
        public void execute() {
    		ctrl.setExternalHdcpStatus(VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
        	return ""; // No feedback for this join
//        	return ctrl.getExternalHdcpStatus();
        }
}

class InSyncCommand extends CrestronCommand {

    public InSyncCommand(CresStreamCtrl ctrl, String arg) {
        super(ctrl, arg);
    }

    @Override
        public void execute() {
            //ctrl.(VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return ctrl.getHDMIInSyncStatus();
        }
}


class InInterlaceCommand extends CrestronCommand {

    public InInterlaceCommand(CresStreamCtrl ctrl, String arg) {
        super(ctrl, arg);
    }

    @Override
        public void execute() {
            //ctrl.(VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return ctrl.getHDMIInInterlacing();
        }
}

class InCecCommand extends CrestronCommand {

    public InCecCommand(CresStreamCtrl ctrl, String arg) {
        super(ctrl, arg);
    }

    @Override
        public void execute() {
            //ctrl.(VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return msg;
        }
}


class InHresCommand extends CrestronCommand {

    public InHresCommand(CresStreamCtrl ctrl, String arg) {
        super(ctrl, arg);
    }

    @Override
        public void execute() {
           // ctrl.(VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return ctrl.getHDMIInHorizontalRes();
        }
}


class InVResCommand extends CrestronCommand {

    public InVResCommand(CresStreamCtrl ctrl, String arg) {
        super(ctrl, arg);
    }

    @Override
        public void execute() {
            //ctrl.(VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return ctrl.getHDMIInVerticalRes();
        }
}


class InFpsCommand extends CrestronCommand {

    public InFpsCommand(CresStreamCtrl ctrl, String arg) {
        super(ctrl, arg);
    }

    @Override
        public void execute() {
           // ctrl.(VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return ctrl.getHDMIInFPS();
        }
}


class InAspectCommand extends CrestronCommand {

    public InAspectCommand(CresStreamCtrl ctrl, String arg) {
        super(ctrl, arg);
    }

    @Override
        public void execute() {
           // ctrl.(VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return ctrl.getHDMIInAspectRatio();
        }
}


class InAudioFormatCommand extends CrestronCommand {

    public InAudioFormatCommand(CresStreamCtrl ctrl, String arg) {
        super(ctrl, arg);
    }

    @Override
        public void execute() {
            //ctrl.(VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return ctrl.getHDMIInAudioFormat();
        }
}


class InAudioChannelsCommand extends CrestronCommand {
	
    public InAudioChannelsCommand(CresStreamCtrl ctrl, String arg) {
        super(ctrl, arg);
    }

    @Override
        public void execute() {
            //ctrl.(VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return ctrl.getHDMIInAudioChannels();
        }
}

class InAudioSampleRateCommand extends CrestronCommand {

    public InAudioSampleRateCommand(CresStreamCtrl ctrl, String arg) {
        super(ctrl, arg);
    }

    @Override
        public void execute() {
            //ctrl.(VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return ctrl.getHDMIInAudioSampleRate();
        }
}

class InTxCecCommand extends CrestronCommand {

    public InTxCecCommand(CresStreamCtrl ctrl, String arg) {
        super(ctrl, arg);
    }

    @Override
        public void execute() {
            //ctrl.(VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return msg;
        }
}


class InRxCecCommand extends CrestronCommand {

    public InRxCecCommand(CresStreamCtrl ctrl, String arg) {
        super(ctrl, arg);
    }

    @Override
        public void execute() {
            //ctrl.(VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return msg;
        }
}

class OutForceHdcp extends CrestronCommand {

    public OutForceHdcp(CresStreamCtrl ctrl, String arg) {
        super(ctrl, arg);
    }

    @Override
        public void execute() {
    		boolean val = Boolean.valueOf(msg);
            ctrl.setHdmiOutForceHdcp(val);
        }
        public String getFeedbackMsg() {
            return String.valueOf(ctrl.userSettings.isHdmiOutForceHdcp());
        }
}


class OutSyncCommand extends CrestronCommand {
	
    public OutSyncCommand(CresStreamCtrl ctrl, String arg) {
        super(ctrl, arg);
    }

    @Override
        public void execute() {
            //ctrl.(VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return ctrl.getHDMIOutSyncStatus();
        }
}


class OutInterlaceCommand extends CrestronCommand {

    public OutInterlaceCommand(CresStreamCtrl ctrl, String arg) {
        super(ctrl, arg);
    }

    @Override
        public void execute() {
            //ctrl.(VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return ctrl.getHDMIOutInterlacing();
        }
}


class OutCecCommand extends CrestronCommand {

    public OutCecCommand(CresStreamCtrl ctrl, String arg) {
        super(ctrl, arg);
    }

    @Override
        public void execute() {
            //ctrl.(VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return msg;
        }
}


class OutHresCommand extends CrestronCommand {

    public OutHresCommand(CresStreamCtrl ctrl, String arg) {
        super(ctrl, arg);
    }

    @Override
        public void execute() {
            //ctrl.(VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return ctrl.getHDMIOutHorizontalRes();
        }
}


class OutVresCommand extends CrestronCommand {
	
    public OutVresCommand(CresStreamCtrl ctrl, String arg) {
        super(ctrl, arg);
    }

    @Override
        public void execute() {
            //ctrl.(VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return ctrl.getHDMIOutVerticalRes();
        }
}


class outFpsCommand extends CrestronCommand {

    public outFpsCommand(CresStreamCtrl ctrl, String arg) {
        super(ctrl, arg);
    }

    @Override
        public void execute() {
            //ctrl.(VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return ctrl.getHDMIOutFPS();
        }
}


class OutAspectCommand extends CrestronCommand {

    public OutAspectCommand(CresStreamCtrl ctrl, String arg) {
        super(ctrl, arg);
    }

    @Override
        public void execute() {
            //ctrl.(VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return ctrl.getHDMIOutAspectRatio();
        }
}


class OutAudioFormatCommand extends CrestronCommand {

    public OutAudioFormatCommand (CresStreamCtrl ctrl, String arg) {
        super(ctrl, arg);
    }

    @Override
        public void execute() {
            //ctrl.(VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return ctrl.getHDMIOutAudioFormat();
        }
}


class OutAudioChannelsCommand extends CrestronCommand {
	
    public OutAudioChannelsCommand(CresStreamCtrl ctrl, String arg) {
        super(ctrl, arg);
    }

    @Override
        public void execute() {
            //ctrl.(VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return ctrl.getHDMIOutAudioChannels();
        }
}


class OutTxCecCommand extends CrestronCommand {

    public OutTxCecCommand(CresStreamCtrl ctrl, String arg) {
        super(ctrl, arg);
    }

    @Override
        public void execute() {
            //ctrl.(VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return msg;
        }
}


class OutRxCecCommand extends CrestronCommand {
	
    public OutRxCecCommand(CresStreamCtrl ctrl, String arg) {
        super(ctrl, arg);
    }

    @Override
        public void execute() {
            //ctrl.(VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return msg;
        }
}

class HdcpFeedbackCommand extends CrestronCommand {

    public HdcpFeedbackCommand(CresStreamCtrl ctrl, String arg) {
        super(ctrl, arg);
    }

    @Override
        public void execute() {
            //not command join
        }
        public String getFeedbackMsg() {
			ctrl.mForceHdcpStatusUpdate = true;
			return "TRUE";
        }
}


class ProcessingCommand extends CrestronCommand {

    public ProcessingCommand(CresStreamCtrl ctrl, String arg) {
        super(ctrl, arg);
    }

    @Override
        public void execute() {
            //ctrl.(VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return ctrl.getProcessingStatus();
        }
}

class DeviceReadyCommand extends CrestronCommand {

    public DeviceReadyCommand(CresStreamCtrl ctrl, String arg) {
        super(ctrl, arg);
    }

    @Override
        public void execute() {
            //ctrl.(VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return ctrl.getDeviceReadyStatus();
        }
}


class ElapsedSecondsCommand extends CrestronCommand {

    public ElapsedSecondsCommand(CresStreamCtrl ctrl, String arg) {
        super(ctrl, arg);
    }

    @Override
        public void execute() {
            //ctrl.(VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return ctrl.getElapsedSeconds();
        }
}


class StatusCommand extends CrestronCommand {

    public StatusCommand (CresStreamCtrl ctrl, String arg) {
        super(ctrl, arg);
    }

    @Override
        public void execute() {
            //ctrl.(VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return ctrl.getStreamStatus();
        }
}


class InitAddressCommand extends CrestronCommand {

    public InitAddressCommand(CresStreamCtrl ctrl, String arg) {
        super(ctrl, arg);
    }

    @Override
        public void execute() {
            //ctrl.(VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return (ctrl.userSettings.getInitiatorAddress());
        }
}


class StreamInHresCommand extends CrestronCommand {

    public StreamInHresCommand(CresStreamCtrl ctrl, String arg) {
        super(ctrl, arg);
    }

    @Override
        public void execute() {
            //ctrl.(VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return getHresFb(true);
        }
}

class StreamOutHresCommand extends CrestronCommand {

    public StreamOutHresCommand(CresStreamCtrl ctrl, String arg) {
        super(ctrl, arg);
    }

    @Override
        public void execute() {
            //ctrl.(VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return getHresFb(false);
        }
}


class StreamInVresCommand extends CrestronCommand {

    public StreamInVresCommand(CresStreamCtrl ctrl, String arg) {
        super(ctrl, arg);
    }

    @Override
        public void execute() {
            //ctrl.(VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return getVresFb(true);
        }
}

class StreamOutVresCommand extends CrestronCommand {

    public StreamOutVresCommand(CresStreamCtrl ctrl, String arg) {
        super(ctrl, arg);
    }

    @Override
        public void execute() {
            //ctrl.(VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return getVresFb(false);
        }
}

class StreamInFpsCommand extends CrestronCommand {

    public StreamInFpsCommand(CresStreamCtrl ctrl, String arg) {
        super(ctrl, arg);
    }

    @Override
        public void execute() {
            //ctrl.(VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return getFpsFb(true);
        }
}

class StreamOutFpsCommand extends CrestronCommand {

    public StreamOutFpsCommand(CresStreamCtrl ctrl, String arg) {
        super(ctrl, arg);
    }

    @Override
        public void execute() {
            //ctrl.(VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return getFpsFb(false);
        }
}

class StreamInAspectCommand extends CrestronCommand {

    public StreamInAspectCommand(CresStreamCtrl ctrl, String arg) {
        super(ctrl, arg);
    }

    @Override
        public void execute() {
            //ctrl.(VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return getAspectFb(true);
        }
}

class StreamOutAspectCommand extends CrestronCommand {

    public StreamOutAspectCommand(CresStreamCtrl ctrl, String arg) {
        super(ctrl, arg);
    }

    @Override
        public void execute() {
            //ctrl.(VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return getAspectFb(false);
        }
}

class StreamInAudioFormatCommand extends CrestronCommand {

    public StreamInAudioFormatCommand(CresStreamCtrl ctrl, String arg) {
        super(ctrl, arg);
    }

    @Override
        public void execute() {
            //ctrl.(VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return getAudioFormatFb(true);
        }
}

class StreamOutAudioFormatCommand extends CrestronCommand {

    public StreamOutAudioFormatCommand(CresStreamCtrl ctrl, String arg) {
        super(ctrl, arg);
    }

    @Override
        public void execute() {
            //ctrl.(VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return getAudioFormatFb(false);
        }
}

class StreamInAudioChannelsCommand extends CrestronCommand {
	
    public StreamInAudioChannelsCommand(CresStreamCtrl ctrl, String arg) {
        super(ctrl, arg);
    }

    @Override
        public void execute() {
            //ctrl.(VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return getAudioChannelsFb(true);
        }
}

class StreamOutAudioChannelsCommand extends CrestronCommand {

    public StreamOutAudioChannelsCommand(CresStreamCtrl ctrl, String arg) {
        super(ctrl, arg);
    }

    @Override
        public void execute() {
            //ctrl.(VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return getAudioChannelsFb(false);
        }
}

class StreamOutRtspStreamFileName extends CrestronCommand {

    public StreamOutRtspStreamFileName(CresStreamCtrl ctrl, String arg) {
        super(ctrl, arg);
    }

    @Override
        public void execute() {
            ctrl.userSettings.SetRtspStreamFileName(msg);
        }
        public String getFeedbackMsg() {
            return ctrl.userSettings.getRtspStreamFileName();
        }
}

class StreamOutRtspSessionName extends CrestronCommand {
    
    public StreamOutRtspSessionName(CresStreamCtrl ctrl, String arg) {
        super(ctrl, arg);
    }
    
    @Override
        public void execute() {
            ctrl.userSettings.SetRtspSessionName(msg);
        }
        public String getFeedbackMsg() {
            return ctrl.userSettings.getRtspSessionName();
        }
}

class StreamOutKeyFrameInterval extends CrestronCommand {

    public StreamOutKeyFrameInterval(CresStreamCtrl ctrl, String arg) {
        super(ctrl, arg);
    }
    
    @Override
    public void execute() {
        int val = Integer.valueOf(msg);
        ctrl.setKeyFrameInterval(val);
    }
    public String getFeedbackMsg() {
        return String.valueOf(ctrl.userSettings.getKeyFrameInterval());
    }
}

class StreamStateCommand extends CrestronCommand {

    public StreamStateCommand(CresStreamCtrl ctrl, String arg, int sessId) {
        super(ctrl, arg, sessId);
    }

    @Override
        public void execute() {
            //ctrl.(VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
			int streamState = (ctrl.getCurrentStreamState(sessId)).getValue();
			return Integer.toString(streamState);
        }
}

class StatisticsEnableCommand extends CrestronCommand {

    public StatisticsEnableCommand(CresStreamCtrl ctrl, String arg, int sessId) {
        super(ctrl, arg, sessId);
    }

    @Override
        public void execute() {
	    	boolean val = Boolean.valueOf(msg);
            if(val)
				ctrl.setStatistics(val, sessId);
        }
        public String getFeedbackMsg() {
            return String.valueOf(ctrl.userSettings.isStatisticsEnable(sessId));
        }
}

class StatisticsResetCommand extends CrestronCommand {

    public StatisticsResetCommand(CresStreamCtrl ctrl, String arg, int sessId) {
        super(ctrl, arg, sessId);
    }

    @Override
        public void execute() {
	    	boolean val = Boolean.valueOf(msg);
			if(val)
				ctrl.resetStatistics(sessId);
        }
        public String getFeedbackMsg() {
            return "false";	//no feedback for this join
        }
}

class StatisticsDisableCommand extends CrestronCommand {

    public StatisticsDisableCommand(CresStreamCtrl ctrl, String arg, int sessId) {
        super(ctrl, arg, sessId);
    }

    @Override
        public void execute() {
	    	boolean val = Boolean.valueOf(msg);
			if(val)
				ctrl.setStatistics(!val, sessId);
        }
        public String getFeedbackMsg() {
            return String.valueOf(ctrl.userSettings.isStatisticsDisable(sessId));
        }
}

class StatisticsNumVideoPacketsCommand extends CrestronCommand {

    public StatisticsNumVideoPacketsCommand(CresStreamCtrl ctrl, String arg) {
        super(ctrl, arg);
    }

    @Override
        public void execute() {
    		// Feedback only join
        }
        public String getFeedbackMsg() {
            return "0";
        }
}

class StatisticsNumVideoPacketsDroppedCommand extends CrestronCommand {

    public StatisticsNumVideoPacketsDroppedCommand(CresStreamCtrl ctrl, String arg) {
        super(ctrl, arg);
    }

    @Override
        public void execute() {
    		// Feedback only join
        }
        public String getFeedbackMsg() {
            return "0";
        }
}

class StatisticsNumAudioPacketsCommand extends CrestronCommand {

    public StatisticsNumAudioPacketsCommand(CresStreamCtrl ctrl, String arg) {
        super(ctrl, arg);
    }

    @Override
        public void execute() {
    		// Feedback only join
        }
        public String getFeedbackMsg() {
            return "0";
        }
}

class StatisticsNumAudioPacketsDroppedCommand extends CrestronCommand {

    public StatisticsNumAudioPacketsDroppedCommand(CresStreamCtrl ctrl, String arg) {
        super(ctrl, arg);
    }

    @Override
        public void execute() {
    		// Feedback only join
        }
        public String getFeedbackMsg() {
            return "0";
        }
}

class SetMulticastTTLCommand extends CrestronCommand {
    
    public SetMulticastTTLCommand(CresStreamCtrl ctrl, String arg) {
        super(ctrl, arg);
    }

    @Override
        public void execute() {
            int val = Integer.valueOf(msg);
            ctrl.setMulticastTTl(val);
        }
        public String getFeedbackMsg() {
            return String.valueOf(ctrl.userSettings.getMulticastTTL());
        }
}

class OsdEnableCommand extends CrestronCommand {
    
    public OsdEnableCommand(CresStreamCtrl ctrl, String arg) {
        super(ctrl, arg);
    }

    @Override
        public void execute() {
            boolean val = Boolean.valueOf(msg);
            if(val){
				ctrl.userSettings.setOsdEnable(true);
				ctrl.userSettings.setOsdDisable(false);
			}
        }
        public String getFeedbackMsg() {
            return String.valueOf(ctrl.userSettings.isOsdEnable());
        }
}

class OsdDisableCommand extends CrestronCommand {
    
    public OsdDisableCommand(CresStreamCtrl ctrl, String arg) {
        super(ctrl, arg);
    }

    @Override
        public void execute() {
            boolean val = Boolean.valueOf(msg);
            if(val){
				ctrl.userSettings.setOsdDisable(true);
				ctrl.userSettings.setOsdEnable(false);
			}
        }
        public String getFeedbackMsg() {
            return String.valueOf(ctrl.userSettings.isOsdDisable());
        }
}

class OsdTextCommand extends CrestronCommand {
    
    public OsdTextCommand(CresStreamCtrl ctrl, String arg) {
        super(ctrl, arg);
    }

    @Override
        public void execute() {
            ctrl.userSettings.setOsdText(msg);
        }
        public String getFeedbackMsg() {
            return ctrl.userSettings.getOsdText();
        }
}

class OsdLocationCommand extends CrestronCommand {
    
    public OsdLocationCommand(CresStreamCtrl ctrl, String arg) {
        super(ctrl, arg);
    }

    @Override
        public void execute() {
            ctrl.userSettings.setOsdLocation(VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return Integer.toString(ctrl.userSettings.getOsdLocation());
        }
}

class OsdXPosCommand extends CrestronCommand {
    
    public OsdXPosCommand(CresStreamCtrl ctrl, String arg) {
        super(ctrl, arg);
    }

    @Override
        public void execute() {
            ctrl.userSettings.setOsdXLocation(VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return Integer.toString(ctrl.userSettings.getOsdXLocation());
        }
}

class OsdYPosCommand extends CrestronCommand {
    
    public OsdYPosCommand(CresStreamCtrl ctrl, String arg) {
        super(ctrl, arg);
    }

    @Override
        public void execute() {
            ctrl.userSettings.setOsdYLocation(VALIDATE_INT(msg));
        }

        public String getFeedbackMsg() {
            return Integer.toString(ctrl.userSettings.getOsdYLocation());
        }
}

class AirMediaLaunchCommand extends CrestronCommand {
	public AirMediaLaunchCommand(CresStreamCtrl ctrl, String arg, int sessId) {
		super(ctrl, arg, sessId);
	}
	public void execute() {
		boolean val = Boolean.valueOf(msg);
		ctrl.launchAirMedia(val, sessId, true); //ctrl is full screen
	}
	public String getFeedbackMsg() {
		return Boolean.toString(ctrl.userSettings.getAirMediaLaunch(sessId));
	}
}

class AirMediaWindowLaunchCommand extends CrestronCommand {
	public AirMediaWindowLaunchCommand(CresStreamCtrl ctrl, String arg, int sessId) {
		super(ctrl, arg, sessId);
	}
	public void execute() {
		boolean val = Boolean.valueOf(msg);
		ctrl.launchAirMedia(val, sessId, false); //Window ctrl is with window parameters
	}
	public String getFeedbackMsg() {
		return Boolean.toString(ctrl.userSettings.getAirMediaLaunch(sessId));
	}
}

class AirMediaEnableCommand extends CrestronCommand {
	public AirMediaEnableCommand(CresStreamCtrl ctrl, String arg, int sessId) {
		super(ctrl, arg, sessId);
	}
	public void execute() {
		boolean val = Boolean.valueOf(msg);
		ctrl.airMediaEnable(val);
	}
    public String getFeedbackMsg() {
    	return msg;	//no feedback for this join
    }
}

class AirMediaLoginCodeCommand extends CrestronCommand {
	public AirMediaLoginCodeCommand(CresStreamCtrl ctrl, String arg, int sessId) {
		super(ctrl, arg, sessId);
	}
	public void execute() {
		// Lock here because setAirMediaLoginCode can be called from constructor
		synchronized (ctrl.mAirMediaLock) {
			this.setFbMsg = ctrl.setAirMediaLoginCode(VALIDATE_INT(msg));
		}
	}
	public String getFeedbackMsg() {
		return Integer.toString(ctrl.userSettings.getAirMediaLoginCode());
	}
}

class AirMediaLoginModeCommand extends CrestronCommand {
	public AirMediaLoginModeCommand(CresStreamCtrl ctrl, String arg, int sessId) {
		super(ctrl, arg, sessId);
	}

	public void execute() {
		// Lock here because setAirMediaLoginMode can be called from constructor
		synchronized (ctrl.mAirMediaLock) {
			this.setFbMsg = ctrl.setAirMediaLoginMode(VALIDATE_INT(msg));
		}
	}
	public String getFeedbackMsg() {
		return Integer.toString(ctrl.userSettings.getAirMediaLoginMode());
	}
}

class AirMediaModeratorCommand extends CrestronCommand {
	public AirMediaModeratorCommand(CresStreamCtrl ctrl, String arg, int sessId) {
		super(ctrl, arg, sessId);
	}
	public void execute() {
		ctrl.setAirMediaModerator(Boolean.valueOf(msg));
	}
	public String getFeedbackMsg() {
		return Boolean.toString(ctrl.userSettings.getAirMediaModerator());
	}
}

class AirMediaResetConnectionsCommand extends CrestronCommand {
	public AirMediaResetConnectionsCommand(CresStreamCtrl ctrl, String arg, int sessId) {
		super(ctrl, arg, sessId);
	}
	public void execute() {
		ctrl.setAirMediaResetConnections(Boolean.valueOf(msg));
	}
	public String getFeedbackMsg() {
		return Boolean.toString(ctrl.userSettings.getAirMediaResetConnections());
	}
}

class AirMediaDisconnectUserCommand extends CrestronCommand {
	public AirMediaDisconnectUserCommand(CresStreamCtrl ctrl, String arg, int sessId) {
		super(ctrl, arg, sessId);
	}	
	public void execute() {
		String[] tokens = msg.split("[:]"); // Format should be "UserId:JoinVal"
		if (tokens.length == 2)
		{
			ctrl.setAirMediaDisconnectUser(VALIDATE_INT(tokens[0]), Boolean.valueOf(tokens[1]), sessId);
		}
	}
	public String getFeedbackMsg() {
		return ctrl.getAirMediaDisconnectUser(sessId);
	}
}

class AirMediaStartUserCommand extends CrestronCommand {
	public AirMediaStartUserCommand(CresStreamCtrl ctrl, String arg, int sessId) {
		super(ctrl, arg, sessId);
	}	
	public void execute() {
		String[] tokens = msg.split("[:]"); // Format should be "UserId:JoinVal"
		if (tokens.length == 2)
		{
			ctrl.setAirMediaStartUser(VALIDATE_INT(tokens[0]), Boolean.valueOf(tokens[1]), sessId);
		}
	}
	public String getFeedbackMsg() {
		return ctrl.getAirMediaStartUser(sessId);
	}
}

class AirMediaUserPositionCommand extends CrestronCommand {
	public AirMediaUserPositionCommand(CresStreamCtrl ctrl, String arg, int sessId) {
		super(ctrl, arg, sessId);
	}	
	public void execute() {
		String[] tokens = msg.split("[:]"); // Format should be "UserId:JoinVal"
		if (tokens.length == 2)
		{
			ctrl.setAirMediaUserPosition(VALIDATE_INT(tokens[0]), VALIDATE_INT(tokens[1]), sessId);
		}
	}
	public String getFeedbackMsg() {
		return ctrl.getAirMediaUserPosition(sessId);
	}
}

class AirMediaStopUserCommand extends CrestronCommand {
	public AirMediaStopUserCommand(CresStreamCtrl ctrl, String arg, int sessId) {
		super(ctrl, arg, sessId);
	}	
	public void execute() {
		String[] tokens = msg.split("[:]"); // Format should be "UserId:JoinVal"
		if (tokens.length == 2)
		{
			ctrl.setAirMediaStopUser(VALIDATE_INT(tokens[0]), Boolean.valueOf(tokens[1]), sessId);
		}
	}
	public String getFeedbackMsg() {
		return ctrl.getAirMediaStopUser(sessId);
	}
}

class AirMediaOsdImageCommand extends CrestronCommand {
	public AirMediaOsdImageCommand(CresStreamCtrl ctrl, String arg, int sessId) {
		super(ctrl, arg, sessId);
	}	
	public void execute() {
		ctrl.setAirMediaOsdImage(msg);
	}
	public String getFeedbackMsg() {
		return ctrl.userSettings.getAirMediaOsdImage();
	}
}

class AirMediaIpAddressPromptCommand extends CrestronCommand {
	public AirMediaIpAddressPromptCommand(CresStreamCtrl ctrl, String arg, int sessId) {
		super(ctrl, arg, sessId);
	}	
	public void execute() {
		ctrl.setAirMediaIpAddressPrompt(Boolean.valueOf(msg));
	}
	public String getFeedbackMsg() {
		return Boolean.toString(ctrl.userSettings.getAirMediaIpAddressPrompt());
	}
}

class AirMediaDomainNamePromptCommand extends CrestronCommand {
	public AirMediaDomainNamePromptCommand(CresStreamCtrl ctrl, String arg, int sessId) {
		super(ctrl, arg, sessId);
	}	
	public void execute() {
		ctrl.setAirMediaDomainNamePrompt(Boolean.valueOf(msg));
	}
	public String getFeedbackMsg() {
		return Boolean.toString(ctrl.userSettings.getAirMediaDomainNamePrompt());
	}
}

class AirMediaHostNamePromptCommand extends CrestronCommand {
	public AirMediaHostNamePromptCommand(CresStreamCtrl ctrl, String arg, int sessId) {
		super(ctrl, arg, sessId);
	}	
	public void execute() {
		ctrl.setAirMediaHostNamePrompt(Boolean.valueOf(msg));
	}
	public String getFeedbackMsg() {
		return Boolean.toString(ctrl.userSettings.getAirMediaHostNamePrompt());
	}
}

class AirMediaCustomPromptCommand extends CrestronCommand {
	public AirMediaCustomPromptCommand(CresStreamCtrl ctrl, String arg, int sessId) {
		super(ctrl, arg, sessId);
	}	
	public void execute() {
		ctrl.setAirMediaCustomPrompt(Boolean.valueOf(msg));
	}
	public String getFeedbackMsg() {
		return Boolean.toString(ctrl.userSettings.getAirMediaCustomPrompt());
	}
}

class AirMediaDisplayConnectionOptionEnableCommand extends CrestronCommand {
	public AirMediaDisplayConnectionOptionEnableCommand(CresStreamCtrl ctrl, String arg, int sessId) {
		super(ctrl, arg, sessId);
	}	
	public void execute() {
		ctrl.setAirMediaDisplayConnectionOptionEnable(Boolean.valueOf(msg));
	}
	public String getFeedbackMsg() {
		return Boolean.toString(ctrl.userSettings.getAirMediaDisplayConnectionOptionEnable());
	}
}

class AirMediaDisplayConnectionOptionCommand extends CrestronCommand {
	public AirMediaDisplayConnectionOptionCommand(CresStreamCtrl ctrl, String arg, int sessId) {
		super(ctrl, arg, sessId);
	}	
	public void execute() {
		ctrl.setAirMediaDisplayConnectionOption(VALIDATE_INT(msg));
	}
	public String getFeedbackMsg() {
		return String.valueOf(ctrl.userSettings.getAirMediaDisplayConnectionOption());
	}
}

class AirMediaCustomPromptStringCommand extends CrestronCommand {
	public AirMediaCustomPromptStringCommand(CresStreamCtrl ctrl, String arg, int sessId) {
		super(ctrl, arg, sessId);
	}	
	public void execute() {
		ctrl.setAirMediaCustomPromptString(msg);
	}
	public String getFeedbackMsg() {
		return ctrl.userSettings.getAirMediaCustomPromptString();
	}
}

class AirMediaWindowPositionCommand extends CrestronCommand {
	public AirMediaWindowPositionCommand(CresStreamCtrl ctrl, String arg, int sessId) {
		super(ctrl, arg, sessId);
	}	
	public void execute() {		
		String[] tokens = msg.split("[,]"); // Format should be "x,y,width,height"
		if (tokens.length == 4)
		{
			ctrl.setAirMediaWindowPosition(
					VALIDATE_INT(tokens[0]),
					VALIDATE_INT(tokens[1]),
					VALIDATE_INT(tokens[2]),
					VALIDATE_INT(tokens[3]));
		}
	}
}

class AirMediaWindowXOffsetCommand extends CrestronCommand {
	public AirMediaWindowXOffsetCommand(CresStreamCtrl ctrl, String arg, int sessId) {
		super(ctrl, arg, sessId);
	}	
	public void execute() {
		ctrl.setAirMediaWindowXOffset(VALIDATE_INT(msg));
	}
	public String getFeedbackMsg() {
		return String.valueOf(ctrl.userSettings.getAirMediaX());
	}
}

class AirMediaWindowYOffsetCommand extends CrestronCommand {
	public AirMediaWindowYOffsetCommand(CresStreamCtrl ctrl, String arg, int sessId) {
		super(ctrl, arg, sessId);
	}	
	public void execute() {
		ctrl.setAirMediaWindowYOffset(VALIDATE_INT(msg));
	}
	public String getFeedbackMsg() {
		return String.valueOf(ctrl.userSettings.getAirMediaY());
	}
}

class AirMediaWindowWidthCommand extends CrestronCommand {
	public AirMediaWindowWidthCommand(CresStreamCtrl ctrl, String arg, int sessId) {
		super(ctrl, arg, sessId);
	}	
	public void execute() {
		ctrl.setAirMediaWindowWidth(VALIDATE_INT(msg));
	}
	public String getFeedbackMsg() {
		return String.valueOf(ctrl.userSettings.getAirMediaWidth());
	}
}

class AirMediaWindowHeightCommand extends CrestronCommand {
	public AirMediaWindowHeightCommand(CresStreamCtrl ctrl, String arg, int sessId) {
		super(ctrl, arg, sessId);
	}	
	public void execute() {
		ctrl.setAirMediaWindowHeight(VALIDATE_INT(msg));
	}
	public String getFeedbackMsg() {
		return String.valueOf(ctrl.userSettings.getAirMediaHeight());
	}
}

class AirMediaApplyLayoutPasswordCommand extends CrestronCommand {
	public AirMediaApplyLayoutPasswordCommand(CresStreamCtrl ctrl, String arg, int sessId) {
		super(ctrl, arg, sessId);
	}	
	public void execute() {
		ctrl.airMediaApplyLayoutPassword(Boolean.valueOf(msg));
	}
}

class AirMediaLayoutPasswordCommand extends CrestronCommand {
	public AirMediaLayoutPasswordCommand(CresStreamCtrl ctrl, String arg, int sessId) {
		super(ctrl, arg, sessId);
	}	
	public void execute() {
		ctrl.setAirMediaLayoutPassword(msg);
	}
	public String getFeedbackMsg() {
		return ctrl.userSettings.getAirMediaLayoutPassword();
	}
}

class AirMediaApplyOsdImageCommand extends CrestronCommand {
	public AirMediaApplyOsdImageCommand(CresStreamCtrl ctrl, String arg, int sessId) {
		super(ctrl, arg, sessId);
	}	
	public void execute() {
		ctrl.airMediaApplyOsdImage(Boolean.valueOf(msg));
	}
}

class AirMediaDisplayLoginCodeCommand extends CrestronCommand {
	public AirMediaDisplayLoginCodeCommand(CresStreamCtrl ctrl, String arg, int sessId) {
		super(ctrl, arg, sessId);
	}	
	public void execute() {
		ctrl.setAirMediaDisplayLoginCode(Boolean.valueOf(msg));
	}
	public String getFeedbackMsg() {
		return Boolean.toString(ctrl.userSettings.getAirMediaDisplayLoginCode());
	}
}

class AirMediaDisplayScreenCommand extends CrestronCommand {
	public AirMediaDisplayScreenCommand(CresStreamCtrl ctrl, String arg, int sessId) {
		super(ctrl, arg, sessId);
	}	
	public void execute() {
		ctrl.airMediaSetDisplayScreen(VALIDATE_INT(msg));
	}
}

class AirMediaWindowFlagCommand extends CrestronCommand {
	public AirMediaWindowFlagCommand(CresStreamCtrl ctrl, String arg, int sessId) {
		super(ctrl, arg, sessId);
	}	
	public void execute() {
		ctrl.airMediaSetWindowFlag(VALIDATE_INT(msg));
	}
}

class AirMediaMiracastEnableCommand extends CrestronCommand {
	public AirMediaMiracastEnableCommand(CresStreamCtrl ctrl, String arg, int sessId) {
		super(ctrl, arg, sessId);
	}	
	public void execute() {
		ctrl.airMediaMiracastEnable(Boolean.valueOf(msg));
	}
	public String getFeedbackMsg() {
		return Boolean.toString(ctrl.userSettings.getAirMediaMiracastEnable());
	}
}

class MsMiceEnableCommand extends CrestronCommand {
	public MsMiceEnableCommand(CresStreamCtrl ctrl, String arg, int sessId) {
		super(ctrl, arg, sessId);
	}	
	public void execute() {
		ctrl.msMiceEnable(Boolean.valueOf(msg));
	}
	public String getFeedbackMsg() {
		return Boolean.toString(ctrl.userSettings.getAirMediaMiracastMsMiceMode());
	}
}

class AirMediaMiracastWifiDirectModeEnableCommand extends CrestronCommand {
	public AirMediaMiracastWifiDirectModeEnableCommand(CresStreamCtrl ctrl, String arg, int sessId) {
		super(ctrl, arg, sessId);
	}	
	public void execute() {
		ctrl.airMediaMiracastWifiDirectMode(Boolean.valueOf(msg));
	}
	public String getFeedbackMsg() {
		return Boolean.toString(ctrl.userSettings.getAirMediaMiracastWifiDirectMode());
	}
}

class AirMediaMiracastPreferWifiDirectCommand extends CrestronCommand {
	public AirMediaMiracastPreferWifiDirectCommand(CresStreamCtrl ctrl, String arg, int sessId) {
		super(ctrl, arg, sessId);
	}	
	public void execute() {
		ctrl.airMediaMiracastPreferWifiDirect(Boolean.valueOf(msg));
	}
	public String getFeedbackMsg() {
		return Boolean.toString(ctrl.userSettings.getAirMediaMiracastPreferWifiDirect());
	}
}

class AirMediaMiracastWirelessOperatingRegionCommand extends CrestronCommand {
	public AirMediaMiracastWirelessOperatingRegionCommand(CresStreamCtrl ctrl, String arg, int sessId) {
		super(ctrl, arg, sessId);
	}	
	public void execute() {
		ctrl.airMediaMiracastWirelessOperatingRegion(VALIDATE_INT(msg));
	}
	public String getFeedbackMsg() {
		return String.valueOf(ctrl.userSettings.getAirMediaMiracastWirelessOperatingRegion());
	}
}

class AirMediaVersionCommand extends CrestronCommand {
	public AirMediaVersionCommand(CresStreamCtrl ctrl, String arg, int sessId) {
		super(ctrl, arg, sessId);
	}	

	public String getFeedbackMsg() {
		return ctrl.getAirMediaVersion();
	}
}

class AirMediaDebugCommand extends CrestronCommand {
	public AirMediaDebugCommand(CresStreamCtrl ctrl, String arg, int sessId) {
		super(ctrl, arg, sessId);
	}	
	public void execute() {
		ctrl.setAirMediaDebug(msg);
	}
	// No feedback
}

class AirMediaProcessDebugMessageCommand extends CrestronCommand {
	public AirMediaProcessDebugMessageCommand(CresStreamCtrl ctrl, String arg) {
		super(ctrl, arg);
	}	
	public void execute() {
		ctrl.setAirMediaProcessDebugMessage(msg);
	}
	// No feedback
}

class AirMediaIsCertificateRequiredCommand extends CrestronCommand {
	public AirMediaIsCertificateRequiredCommand(CresStreamCtrl ctrl, String arg) {
		super(ctrl, arg);
	}	
	public void execute() {
		ctrl.setAirMediaIsCertificateRequired(Boolean.valueOf(msg));
	}
	// No feedback
}

class AirMediaOnlyAllowSecureConnectionsCommand extends CrestronCommand {
	public AirMediaOnlyAllowSecureConnectionsCommand(CresStreamCtrl ctrl, String arg) {
		super(ctrl, arg);
	}	
	public void execute() {
		ctrl.setAirMediaOnlyAllowSecureConnections(Boolean.valueOf(msg));
	}
	// No feedback
}

class AirMediaClearCacheCommand extends CrestronCommand {
	public AirMediaClearCacheCommand(CresStreamCtrl ctrl, String arg) {
		super(ctrl, arg);
	}	
	public void execute() {
		ctrl.setAirMediaClearCache();
	}
	// No feedback
}

class AirMediaAdaptorSelectCommand extends CrestronCommand {
	public AirMediaAdaptorSelectCommand(CresStreamCtrl ctrl, String arg, int sessId) {
		super(ctrl, arg, sessId);
	}	
	public void execute() {
		ctrl.setAirMediaAdaptorSelect(VALIDATE_INT(msg));
	}
	public String getFeedbackMsg() {
		return Integer.toString(ctrl.userSettings.getAirMediaAdaptorSelect());
	}
}

class AirMediaConnectionAddressCommand extends CrestronCommand {
	public AirMediaConnectionAddressCommand(CresStreamCtrl ctrl, String arg, int sessId) {
		super(ctrl, arg, sessId);
	}	
// No execute: this is feedback only
	public String getFeedbackMsg() {
		return ctrl.getAirMediaConnectionAddress();
	}
}

class AirMediaRestartCommand extends CrestronCommand {
	public AirMediaRestartCommand(CresStreamCtrl ctrl, String arg, int sessId) {
		super(ctrl, arg, sessId);
	}
	
	public void execute() {		
		ctrl.airmediaRestart(sessId);
	}
	// No feedback
}

class AirMediaProjectionLockCommand extends CrestronCommand {
	public AirMediaProjectionLockCommand(CresStreamCtrl ctrl, String arg, int sessId) {
		super(ctrl, arg, sessId);
	}
	
	public void execute() {		
		boolean val = Boolean.valueOf(msg);
		ctrl.setAirMediaProjectionLock(val);
	}
	// No feedback
}

class camStreamEnableCommand extends CrestronCommand {
	public camStreamEnableCommand(CresStreamCtrl ctrl, String arg) {
		super(ctrl, arg);
	}	
	public void execute() {		
		ctrl.setCamStreamEnable(Boolean.valueOf(msg));
	}
	public String getFeedbackMsg() {
		return Boolean.toString(ctrl.userSettings.getCamStreamEnable());
	}
}

class camStreamMulticastEnableCommand extends CrestronCommand {
	public camStreamMulticastEnableCommand(CresStreamCtrl ctrl, String arg) {
		super(ctrl, arg);
	}	
	public void execute() {		
		ctrl.setCamStreamMulticastEnable(Boolean.valueOf(msg));
	}
	public String getFeedbackMsg() {
		return Boolean.toString(ctrl.userSettings.getCamStreamMulticastEnable());
	}
}

class camStreamResolutionCommand extends CrestronCommand {
	public camStreamResolutionCommand(CresStreamCtrl ctrl, String arg) {
		super(ctrl, arg);
	}	
	public void execute() {		
		ctrl.setCamStreamResolution(VALIDATE_INT(msg));
	}
	public String getFeedbackMsg() {
		return String.valueOf(ctrl.userSettings.getCamStreamResolution());
	}
}

class camStreamUrlCommand extends CrestronCommand {
	public camStreamUrlCommand(CresStreamCtrl ctrl, String arg) {
		super(ctrl, arg);
	}	
	public String getFeedbackMsg() {
		return ctrl.userSettings.getCamStreamUrl();
	}
}

class camStreamSnapshotUrlCommand extends CrestronCommand {
	public camStreamSnapshotUrlCommand(CresStreamCtrl ctrl, String arg) {
		super(ctrl, arg);
	}	
	public String getFeedbackMsg() {
		return ctrl.userSettings.getCamStreamSnapshotUrl();
	}
}

class camStreamNameCommand extends CrestronCommand {
	public camStreamNameCommand(CresStreamCtrl ctrl, String arg) {
		super(ctrl, arg);
	}	
	public void execute() {		
		ctrl.setCamStreamName(msg);
	}
	public String getFeedbackMsg() {
		return ctrl.userSettings.getCamStreamName();
	}
}

class camStreamSnapshotNameCommand extends CrestronCommand {
	public camStreamSnapshotNameCommand(CresStreamCtrl ctrl, String arg) {
		super(ctrl, arg);
	}	
	public void execute() {		
		ctrl.setCamStreamSnapshotName(msg);
	}
	public String getFeedbackMsg() {
		return ctrl.userSettings.getCamStreamSnapshotName();
	}
}

class camStreamMulticastAddressCommand extends CrestronCommand {
	public camStreamMulticastAddressCommand(CresStreamCtrl ctrl, String arg) {
		super(ctrl, arg);
	}	
	public void execute() {		
		ctrl.setCamStreamMulticastAddress(msg);
	}
	public String getFeedbackMsg() {
		return ctrl.userSettings.getCamStreamMulticastAddress();
	}
}

class wbsStreamUrlCommand extends CrestronCommand {
	public wbsStreamUrlCommand(CresStreamCtrl ctrl, String arg, int sessId) {
		super(ctrl, arg, sessId);
	}	
	public void execute() {
		ctrl.setWbsStreamUrl(msg, sessId);
	}
	public String getFeedbackMsg() {
		return ctrl.userSettings.getWbsStreamUrl(sessId);
	}
}

class AppspaceIsEnabledCommand extends CrestronCommand {

    public AppspaceIsEnabledCommand(CresStreamCtrl ctrl, String arg) {
     super(ctrl, arg);
    }

    @Override
    public void execute() {
    	boolean val = Boolean.valueOf(msg);
    	ctrl.userSettings.setAppspaceEnabled(val);
    }
    //no feedback for this join
}

class RestartStreamOnStartCommand extends CrestronCommand {

    public RestartStreamOnStartCommand(CresStreamCtrl ctrl, String arg) {
     super(ctrl, arg);
    }

    @Override
    public void execute() {
    	boolean val = Boolean.valueOf(msg);
    	ctrl.restartStreamsOnStart = val;
    }
    public String getFeedbackMsg() {
    	return msg;	//no feedback for this join
    }
}

class UseNewSinkCommand extends CrestronCommand {
	
	public UseNewSinkCommand(CresStreamCtrl ctrl, String arg, int sessId) {
		super(ctrl, arg, sessId);
	}

	@Override
	public void execute() {
		boolean val = Boolean.valueOf(msg);
		ctrl.setNewSink(val, sessId);
	}
	public String getFeedbackMsg() {
		return msg;	//no feedback for this join
	}
}

class UseNewIpAddrCommand extends CrestronCommand {

	public UseNewIpAddrCommand(CresStreamCtrl ctrl, String arg) {
		super(ctrl, arg);
	}

	@Override
	public void execute() {
		if(!msg.equals(ctrl.userSettings.getDeviceIp()))
		{
			Log.i(TAG, "UseNewIpAddrCommand: set device primary ip address="+msg);
			ctrl.userSettings.setDeviceIp(msg);
			ctrl.stopOnIpAddrChange();			
			if (ctrl.userSettings.getAirMediaAdaptorSelect() == 0)
				ctrl.updateAirMediaIpInformation();
		}
	}
}

class SetAuxiliaryIpAddressCommand extends CrestronCommand {

	public SetAuxiliaryIpAddressCommand(CresStreamCtrl ctrl, String arg) {
		super(ctrl, arg);
	}

	@Override
	public void execute() {
		if(!msg.equals(ctrl.userSettings.getAuxiliaryIp()))
		{
			Log.i(TAG, "SetAuxiliaryIpAddressCommand: set device auxiliary ip address="+msg);
			ctrl.userSettings.setAuxiliaryIp(msg);
			if (ctrl.userSettings.getAirMediaAdaptorSelect() == 1)
				ctrl.updateAirMediaIpInformation();
		}
	}
}

class FIELDDEBUGJNICommand extends CrestronCommand {

	public FIELDDEBUGJNICommand(CresStreamCtrl ctrl, String arg, int sessId) {
		super(ctrl, arg, sessId);
	}

	@Override
	public void execute() {
		//int val = Integer.valueOf(msg);
		ctrl.setFieldDebugJni(msg, sessId);
	}
	public String getFeedbackMsg() {
		return msg;	//no feedback for this join
	}
}

class ResetAllWindowsCommand extends CrestronCommand {

	public ResetAllWindowsCommand(CresStreamCtrl ctrl, String arg) {
		super(ctrl, arg);
	}

	@Override
	public void execute() {
		ctrl.resetAllWindows();
	}
	public String getFeedbackMsg() {
		return msg;	//no feedback for this join
	}
}

class SetWbsLogLevelCommand extends CrestronCommand {

	public SetWbsLogLevelCommand(CresStreamCtrl ctrl, String arg) {
		super(ctrl, arg);
	}

	@Override
	public void execute() {
		try {
			ctrl.setWbsLogLevel(Integer.parseInt(msg));
		} catch (Exception e) {}
	}
	public String getFeedbackMsg() {
		return msg;	//no feedback for this join
	}
}

class WfdStreamCommand extends CrestronCommand {

	public WfdStreamCommand(CresStreamCtrl ctrl, String arg, int sessId) {
		super(ctrl, arg);
	}

	@Override
	public void execute() {
		try {
			ctrl.wfdStreamCommand(msg, sessId);
		} catch (Exception e) {}
	}
}

class ForceRgbPreviewModeCommand extends CrestronCommand {

	public ForceRgbPreviewModeCommand(CresStreamCtrl ctrl, String arg) {
		super(ctrl, arg);
	}

	@Override
	public void execute() {
		try {
			boolean val = Boolean.valueOf(msg);
			ctrl.setForceRgbPreviewMode(val);
		} catch (Exception e) {}
	}
	// no feedback
}

class Rgb888ModeCommand extends CrestronCommand {
    public Rgb888ModeCommand(CresStreamCtrl ctrl, String arg) {
        super(ctrl, arg);
    }

    @Override
    public void execute() {
        try {
            boolean val = Boolean.valueOf(msg);
            ctrl.setRgb888Mode(val);
        } catch (Exception e) {}
    }
    // no feedback
}

class ChromaKeyColorCommand extends CrestronCommand {

	public ChromaKeyColorCommand(CresStreamCtrl ctrl, String arg) {
		super(ctrl, arg);
	}

	@Override
	public void execute() {
		try {
			int val = VALIDATE_HEXINT(msg);
			ctrl.userSettings.setChromaKeyColor(val);
		} catch (Exception e) {}
	}
	// no feedback
}

class SetLogLevelCommand extends CrestronCommand {

	public SetLogLevelCommand(CresStreamCtrl ctrl, String arg) {
		super(ctrl, arg);
	}

	@Override
	public void execute() {
		try {
			ctrl.setLogLevel(Integer.parseInt(msg));
		} catch (Exception e) {}
	}
	public String getFeedbackMsg() {
		return msg;	//no feedback for this join
	}
}
