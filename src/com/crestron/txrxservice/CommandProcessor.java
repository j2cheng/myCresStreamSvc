
package com.crestron.txrxservice;

class CrestronCommand implements CommandIf {
	CommandReceiver launch = null;
    String msg = null;
    int idx = 0;
    
    public CrestronCommand(CommandReceiver launch, String arg, int sessId) {
        this.launch = launch;
        this.msg = arg;
        this.idx = sessId;
    }
    
    public CrestronCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }
    
    //Should override if join should be be acted upon
    public void execute() {
        return;
    }
    
    //Should override if join should be included in update request
    public String getFeedbackMsg() {
        return msg;  
    }
}


class DeviceCommand implements CommandIf {
    CommandReceiver launch;
    String msg;
    int idx;

    public DeviceCommand(CommandReceiver launch, String arg, int sessId) {
        this.launch = launch;
        this.msg = arg;
        this.idx = sessId;
    }

    @Override
        public void execute() {
            launch.setDeviceMode(launch.VALIDATE_INT(msg), idx);
        }
        public String getFeedbackMsg() {
            return launch.getDeviceMode(idx);
        }
}

class SessionInitiationCommand implements CommandIf {
    CommandReceiver launch;
    String msg;
    int idx;

    public SessionInitiationCommand(CommandReceiver launch, String arg, int sessId) {
        this.launch = launch;
        this.msg = arg;
        this.idx = sessId;
    }

    @Override
        public void execute() {
            launch.setSessionInitation(launch.VALIDATE_INT(msg), idx);
        }
        public String getFeedbackMsg() {
            return launch.getSessionInitiation(idx);
        }
}

class TModeCommand implements CommandIf {
    CommandReceiver launch;
    String msg;
    int idx;

    public TModeCommand(CommandReceiver launch, String arg, int sessId) {
        this.launch = launch;
        this.msg = arg;
        this.idx = sessId;
    }

    @Override
        public void execute() {
            launch.SetTMode(launch.VALIDATE_INT(msg), idx);
        }
        public String getFeedbackMsg() {
        	return launch.getTransportMode(idx);
        }
}

class VencCommand implements CommandIf {
    CommandReceiver launch;
    String msg;
    int idx;

    public VencCommand(CommandReceiver launch, String arg, int sessId) {
        this.launch = launch;
        this.msg = arg;
        this.idx = sessId;
    }

    @Override
        public void execute() {
            launch.SetVenc(launch.VALIDATE_INT(msg), idx);
        }
        public String getFeedbackMsg() {
        	return launch.getStreamProfile(idx);
        }
}

class RtspPortCommand implements CommandIf {
    CommandReceiver launch;
    String msg;
    int idx;

    public RtspPortCommand(CommandReceiver launch, String arg, int sessId) {
        this.launch = launch;
        this.msg = arg;
        this.idx = sessId;
    }

    @Override
        public void execute() {
            launch.setRtspPort(launch.VALIDATE_INT(msg), idx);
        }
        public String getFeedbackMsg() {
            return launch.getRtspPort(idx);
        }
}

class TsPortCommand implements CommandIf {
    CommandReceiver launch;
    String msg;
    int idx;

    public TsPortCommand(CommandReceiver launch, String arg, int sessId) {
        this.launch = launch;
        this.msg = arg;
        this.idx = sessId;
    }

    @Override
        public void execute() {
            launch.setTsPort(launch.VALIDATE_INT(msg), idx);
        }
        public String getFeedbackMsg() {
        	return launch.getTsPort(idx);
        }
}

class RtpVCommand implements CommandIf {
    CommandReceiver launch;
    String msg;
    int idx;

    public RtpVCommand(CommandReceiver launch, String arg, int sessId) {
        this.launch = launch;
        this.msg = arg;
        this.idx = sessId;
    }

    @Override
        public void execute() {
            launch.setRtpV(launch.VALIDATE_INT(msg), idx);
        }
        public String getFeedbackMsg() {
        	return launch.getRtpVideoPort(idx);
        }
}

class RtpACommand implements CommandIf {
    CommandReceiver launch;
    String msg;
    int idx;

    public RtpACommand(CommandReceiver launch, String arg, int sessId) {
        this.launch = launch;
        this.msg = arg;
        this.idx = sessId;
    }

    @Override
        public void execute() {
            launch.setRtpA(launch.VALIDATE_INT(msg), idx);
        }
        public String getFeedbackMsg() {
        	return launch.getRtpAudioPort(idx);
        }
}

class VfrCommand implements CommandIf {
    CommandReceiver launch;
    String msg;
    int idx;

    public VfrCommand(CommandReceiver launch, String arg, int sessId) {
        this.launch = launch;
        this.msg = arg;
        this.idx = sessId;
    }

    @Override
        public void execute() {
            launch.setVfr(launch.VALIDATE_INT(msg), idx);
        }
        public String getFeedbackMsg() {
            return launch.getEncodingFramerate(idx);
        }
}

class VbrCommand implements CommandIf {
    CommandReceiver launch;
    String msg;
    int idx;

    public VbrCommand(CommandReceiver launch, String arg, int sessId) {
        this.launch = launch;
        this.msg = arg;
        this.idx = sessId;
    }

    @Override
        public void execute() {
            launch.setVbr(launch.VALIDATE_INT(msg), idx);
        }
        public String getFeedbackMsg() {
            return launch.getBitrate(idx);
        }
}

class TcpInterleaveCommand implements CommandIf {
    CommandReceiver launch;
    String msg;
    int idx;

    public TcpInterleaveCommand(CommandReceiver launch, String arg, int sessId) {
        this.launch = launch;
        this.msg = arg;
        this.idx = sessId;
    }

    @Override
        public void execute() {
            launch.EnableTcpInterleave(launch.VALIDATE_INT(msg),idx);
        }
        public String getFeedbackMsg() {
        	return launch.getTcpInterleave(idx);
        }
}

class MulticastIpaddrCommand implements CommandIf {
    CommandReceiver launch;
    String msg;
    int idx;

    public MulticastIpaddrCommand(CommandReceiver launch, String arg, int sessId) {
        this.launch = launch;
        this.msg = arg;
        this.idx = sessId;
    }

    @Override
        public void execute() {
            launch.setMulticastIpAddress(msg, idx);
        }
        public String getFeedbackMsg() {
            return launch.getMulticastIpAddress(idx);
        }
}

class EncodingResolutionCommand implements CommandIf {
    CommandReceiver launch;
    String msg;
    int idx;

    public EncodingResolutionCommand(CommandReceiver launch, String arg, int sessId) {
        this.launch = launch;
        this.msg = arg;
        this.idx = sessId;
    }

    @Override
        public void execute() {
            launch.setEncodingResolution(launch.VALIDATE_INT(msg), idx);
        }
        public String getFeedbackMsg() {
            return launch.getEncodingResolution(idx);
        }
}

class SetVolumeCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public SetVolumeCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
    		double volume = Double.valueOf(msg);
            launch.setVolume(volume);
        }
        public String getFeedbackMsg() {
            return launch.getVolume();
        }
}

class MuteCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public MuteCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            boolean val = Boolean.valueOf(msg);
            if(val)
                launch.setMute(val);
        }
        public String getFeedbackMsg() {
            return launch.getMute();
        }
}

class UnmuteCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public UnmuteCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            boolean val = Boolean.valueOf(msg);
            launch.setUnmute(val);
        }
        public String getFeedbackMsg() {
        	return launch.getUnmute();
        }
}

class HdmiInAudioCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public HdmiInAudioCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            boolean val = Boolean.valueOf(msg);
            launch.setProcessHdmiInAudio(val);
        }
        public String getFeedbackMsg() {
        	return launch.getProcessHdmiInAudio();
        }
}

class LatencyCommand implements CommandIf {
    CommandReceiver launch;
    String msg;
    int idx;

    public LatencyCommand(CommandReceiver launch, String arg, int sessId) {
        this.launch = launch;
        this.msg = arg;
        this.idx = sessId;
    }

    @Override
        public void execute() {
            launch.setLatency(launch.VALIDATE_INT(msg), idx);
        }
        public String getFeedbackMsg() {
            return launch.getLatency(idx);
        }
}

class PasswdEnableCommand implements CommandIf {
    CommandReceiver launch;
    String msg;
    int idx;

    public PasswdEnableCommand(CommandReceiver launch, String arg, int sessId) {
        this.launch = launch;
        this.msg = arg;
        this.idx = sessId;
    }

    @Override
        public void execute() {
	    	boolean val = Boolean.valueOf(msg);
            launch.passwdEnable(val, idx);
        }
        public String getFeedbackMsg() {
            return launch.getPasswordEnable(idx);
        }
}

class PasswdDisableCommand implements CommandIf {
    CommandReceiver launch;
    String msg;
    int idx;

    public PasswdDisableCommand(CommandReceiver launch, String arg, int sessId) {
        this.launch = launch;
        this.msg = arg;
        this.idx = sessId;
    }

    @Override
        public void execute() {
	    	boolean val = Boolean.valueOf(msg);
            launch.passwdDisable(val, idx);
        }
        public String getFeedbackMsg() {
            return launch.getPasswordDisable(idx);
        }
}

class UserCommand implements CommandIf {
    CommandReceiver launch;
    String msg;
    int idx;

    public UserCommand(CommandReceiver launch, String arg, int sessId) {
        this.launch = launch;
        this.msg = arg;
        this.idx = sessId;
    }

    @Override
        public void execute() {
            launch.setUserName(msg, idx);
        }
        public String getFeedbackMsg() {
            return launch.getUsername(idx);
        }
}

class PasswdCommand implements CommandIf {
    CommandReceiver launch;
    String msg;
    int idx;

    public PasswdCommand(CommandReceiver launch, String arg, int sessId) {
        this.launch = launch;
        this.msg = arg;
        this.idx = sessId;
    }

    @Override
        public void execute() {
            launch.setPasswd(msg, idx);
        }
        public String getFeedbackMsg() {
            return launch.getPassword(idx);
        }
}

class StreamUrlCommand implements CommandIf {
    CommandReceiver launch;
    String msg;
    int idx;
    public StreamUrlCommand(CommandReceiver launch, String arg, int sessId) {
        this.launch = launch;
        this.msg = arg;
        this.idx = sessId;
    }

    @Override
        public void execute() {
            launch.setStreamUrl(msg, idx);
        }
        public String getFeedbackMsg() {
            return launch.getStreamUrl(idx);
        }
}

class ProxyEnableCommand implements CommandIf {
    CommandReceiver launch;
    String msg;
    int idx;
    public ProxyEnableCommand(CommandReceiver launch, String arg, int sessId) {
        this.launch = launch;
        this.msg = arg;
        this.idx = sessId;
    }

    @Override
        public void execute() {
	    	boolean val = Boolean.valueOf(msg);
            launch.setProxyEnable(val, idx);
        }
        public String getFeedbackMsg() {
            return msg;
        }
}

class HdcpEncryptCommand implements CommandIf {
    CommandReceiver launch;
    String msg;
    int idx;
    public HdcpEncryptCommand(CommandReceiver launch, String arg, int sessId) {
        this.launch = launch;
        this.msg = arg;
        this.idx = sessId;
    }

    @Override
        public void execute() {
	    	boolean val = Boolean.valueOf(msg);
            launch.setHdcpEncrypt(val, idx);
        }
        public String getFeedbackMsg() {
            return msg;
        }
}

class TxHdcpActiveCommand implements CommandIf {
    CommandReceiver launch;
    String msg;
    int idx;
    public TxHdcpActiveCommand(CommandReceiver launch, String arg, int sessId) {
        this.launch = launch;
        this.msg = arg;
        this.idx = sessId;
    }

    @Override
        public void execute() {
	    	boolean val = Boolean.valueOf(msg);
            launch.setTxHdcpActive(val, idx);
        }
        public String getFeedbackMsg() {
            return msg;
        }
}

class InternalRtspPortCommand implements CommandIf {
    CommandReceiver launch;
    String msg;
    int idx;

    public InternalRtspPortCommand(CommandReceiver launch, String arg, int sessId) {
        this.launch = launch;
        this.msg = arg;
        this.idx = sessId;
    }

    @Override
        public void execute() {
            launch.setInternalRtspPort(launch.VALIDATE_INT(msg), idx);
        }
        public String getFeedbackMsg() {
            return msg;
        }
}

class StartCommand implements CommandIf {
    CommandReceiver launch;
    String msg;
    int idx;

    public StartCommand(CommandReceiver launch, String arg, int sessId) {
        this.launch = launch;
        this.msg = arg;
        this.idx = sessId;
    }

    @Override
        public void execute() {
            boolean val = Boolean.valueOf(msg);
            if(val)
                launch.setStart(idx);
        }
        public String getFeedbackMsg() {
            return launch.getStartStatus();
        }
}

class StopCommand implements CommandIf {
    CommandReceiver launch;
    String msg;
    int idx;
    boolean fullStop;

    public StopCommand(CommandReceiver launch, String arg, int sessId, boolean fullStopRequested) {
        this.launch = launch;
        this.msg = arg;
        this.idx = sessId;
        this.fullStop = fullStopRequested;
    }

    @Override
        public void execute() {
            boolean val = Boolean.valueOf(msg);
            if(val)
                launch.setStop(idx, fullStop);
        }
        public String getFeedbackMsg() {
            return launch.getStopStatus();
        }
}

class PauseCommand implements CommandIf {
    CommandReceiver launch;
    String msg;
    int idx;

    public PauseCommand(CommandReceiver launch, String arg, int sessId) {
        this.launch = launch;
        this.msg = arg;
        this.idx = sessId;
    }

    @Override
        public void execute() {
            boolean val = Boolean.valueOf(msg);
            if(val)
                launch.setPause(idx);
        }
        public String getFeedbackMsg() {
            return launch.getPauseStatus();
        }
}

class XlocCommand implements CommandIf {

    CommandReceiver launch;
    String msg;
    int idx;

    public XlocCommand(CommandReceiver launch, String arg, int sessId) {
        this.launch = launch;
        this.msg = arg;
        this.idx = sessId;
    }

    @Override
        public  void execute() {
            launch.setXloc(launch.VALIDATE_INT(msg), idx);
        }
        public String getFeedbackMsg() {
            return launch.getXloc(idx);
        }
}

class YlocCommand implements CommandIf {

    CommandReceiver launch;
    String msg;
    int idx;

    public YlocCommand(CommandReceiver launch, String arg, int sessId) {
        this.launch = launch;
        this.msg = arg;
        this.idx = sessId;
    }

    @Override
        public void execute() {
            launch.setYloc(launch.VALIDATE_INT(msg), idx);
        }
        public String getFeedbackMsg() {
            return launch.getYloc(idx);
        }
}

class DestWidthCommand implements CommandIf {

    CommandReceiver launch;
    String msg;
    int idx;

    public DestWidthCommand(CommandReceiver launch, String arg, int sessId) {
        this.launch = launch;
        this.msg = arg;
        this.idx = sessId;
    }

    @Override
        public void execute() {
            launch.setDestWidth(launch.VALIDATE_INT(msg), idx);
        }
        public String getFeedbackMsg() {
            return launch.getDestWidth(idx);
        }
}

class DestHeightCommand implements CommandIf {

    CommandReceiver launch;
    String msg;
    int idx;

    public DestHeightCommand(CommandReceiver launch, String arg, int sessId) {
        this.launch = launch;
        this.msg = arg;
        this.idx = sessId;
    }

    @Override
        public void execute() {
            launch.setDestHeight(launch.VALIDATE_INT(msg), idx);
        }
        public String getFeedbackMsg() {
            return launch.getDestHeight(idx);
        }
}

class DestZOrderCommand implements CommandIf {

    CommandReceiver launch;
    String msg;
    int idx;

    public DestZOrderCommand(CommandReceiver launch, String arg, int sessId) {
        this.launch = launch;
        this.msg = arg;
        this.idx = sessId;
    }

    @Override
        public void execute() {
            launch.setDestZOrder(launch.VALIDATE_INT(msg), idx);
        }
        public String getFeedbackMsg() {
            return launch.getDestZOrder(idx);
        }
}

class ExternalHdcpStatusCommand implements CommandIf {

    CommandReceiver launch;
    String msg;

    public ExternalHdcpStatusCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
    		launch.setExternalHdcpStatus(CommandReceiver.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
        	return ""; // No feedback for this join
//        	return launch.getExternalHdcpStatus();
        }
}

class InSyncCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public InSyncCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            //launch.(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return launch.getHdmiInSync();
        }
}


class InInterlaceCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public InInterlaceCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            //launch.(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return launch.getHdmiInInterlace();
        }
}

class InCecCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public InCecCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            //launch.(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return msg;
        }
}


class InHresCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public InHresCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
           // launch.(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return launch.getHdmiInHRes();
        }
}


class InVResCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public InVResCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            //launch.(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return launch.getHdmiInVRes();
        }
}


class InFpsCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public InFpsCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
           // launch.(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return launch.getHdmiInFps();
        }
}


class InAspectCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public InAspectCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
           // launch.(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return launch.getHdmiInAspect();
        }
}


class InAudioFormatCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public InAudioFormatCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            //launch.(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return launch.getHdmiInAudioFormat();
        }
}


class InAudioChannelsCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public InAudioChannelsCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            //launch.(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return launch.getHdmiInAudioChannels();
        }
}

class InAudioSampleRateCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public InAudioSampleRateCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            //launch.(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return launch.getHdmiInAudioSampleRate();
        }
}

class InTxCecCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public InTxCecCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            //launch.(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return msg;
        }
}


class InRxCecCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public InRxCecCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            //launch.(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return msg;
        }
}

class OutForceHdcp implements CommandIf {
    CommandReceiver launch;
    String msg;

    public OutForceHdcp(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
    		boolean val = Boolean.valueOf(msg);
            launch.setHdmiOutForceHdcp(val);
        }
        public String getFeedbackMsg() {
            return launch.getHdmiOutForceHdcp();
        }
}


class OutSyncCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public OutSyncCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            //launch.(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return launch.getHdmiOutSync();
        }
}


class OutInterlaceCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public OutInterlaceCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            //launch.(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return launch.getHdmiOutInterlaced();
        }
}


class OutCecCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public OutCecCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            //launch.(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return msg;
        }
}


class OutHresCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public OutHresCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            //launch.(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return launch.getHdmiOutHRes();
        }
}


class OutVresCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public OutVresCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            //launch.(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return launch.getHdmiOutVRes();
        }
}


class outFpsCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public outFpsCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            //launch.(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return launch.getHdmiOutFps();
        }
}


class OutAspectCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public OutAspectCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            //launch.(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return launch.getHdmiOutAspect();
        }
}


class OutAudioFormatCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public OutAudioFormatCommand (CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            //launch.(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return launch.getHdmiOutAudioFormat();
        }
}


class OutAudioChannelsCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public OutAudioChannelsCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            //launch.(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return launch.getHdmiOutAudioChannels();
        }
}


class OutTxCecCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public OutTxCecCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            //launch.(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return msg;
        }
}


class OutRxCecCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public OutRxCecCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            //launch.(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return msg;
        }
}

class HdcpFeedbackCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public HdcpFeedbackCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            //not command join
        }
        public String getFeedbackMsg() {
            return launch.getHdcpFb();
        }
}


class ProcessingCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public ProcessingCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg; 
    }

    @Override
        public void execute() {
            //launch.(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return launch.getProcessingStats();
        }
}

class DeviceReadyCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public DeviceReadyCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            //launch.(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return launch.getDeviceReadyStatus();
        }
}


class ElapsedSecondsCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public ElapsedSecondsCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            //launch.(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return launch.getElapsedSeconds();
        }
}


class StatusCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public StatusCommand (CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            //launch.(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return launch.getStatus();
        }
}


class InitAddressCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public InitAddressCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            //launch.(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return launch.getInitAddress();
        }
}


class StreamInHresCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public StreamInHresCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            //launch.(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return launch.getHresFb(true);
        }
}

class StreamOutHresCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public StreamOutHresCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            //launch.(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return launch.getHresFb(false);
        }
}


class StreamInVresCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public StreamInVresCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            //launch.(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return launch.getVresFb(true);
        }
}

class StreamOutVresCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public StreamOutVresCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            //launch.(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return launch.getVresFb(false);
        }
}

class StreamInFpsCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public StreamInFpsCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            //launch.(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return launch.getFpsFb(true);
        }
}

class StreamOutFpsCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public StreamOutFpsCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            //launch.(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return launch.getFpsFb(false);
        }
}

class StreamInAspectCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public StreamInAspectCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            //launch.(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return launch.getAspectFb(true);
        }
}

class StreamOutAspectCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public StreamOutAspectCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            //launch.(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return launch.getAspectFb(false);
        }
}

class StreamInAudioFormatCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public StreamInAudioFormatCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            //launch.(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return launch.getAudioFormatFb(true);
        }
}

class StreamOutAudioFormatCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public StreamOutAudioFormatCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            //launch.(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return launch.getAudioFormatFb(false);
        }
}

class StreamInAudioChannelsCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public StreamInAudioChannelsCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            //launch.(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return launch.getAudioChannelsFb(true);
        }
}

class StreamOutAudioChannelsCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public StreamOutAudioChannelsCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            //launch.(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return launch.getAudioChannelsFb(false);
        }
}

class StreamOutRtspStreamFileName implements CommandIf {
    CommandReceiver launch;
    String msg;

    public StreamOutRtspStreamFileName(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            launch.setRtspStreamFileName(msg);
        }
        public String getFeedbackMsg() {
            return launch.getRtspStreamFileName();
        }
}

class StreamOutRtspSessionName implements CommandIf {
    CommandReceiver launch;
    String msg;
    
    public StreamOutRtspSessionName(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }
    
    @Override
        public void execute() {
            launch.setRtspSessionName(msg);
        }
        public String getFeedbackMsg() {
            return launch.getRtspSessionName();
        }
}

class StreamStateCommand implements CommandIf {
    CommandReceiver launch;
    String msg;
    int idx;

    public StreamStateCommand(CommandReceiver launch, String arg, int sessId) {
        this.launch = launch;
        this.msg = arg;
        this.idx = sessId;
    }

    @Override
        public void execute() {
            //launch.(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return launch.getStreamState(idx);
        }
}

class StatisticsEnableCommand implements CommandIf {
	CommandReceiver launch;
    String msg;
    int idx;

    public StatisticsEnableCommand(CommandReceiver launch, String arg, int sessId) {
        this.launch = launch;
        this.msg = arg;
        this.idx = sessId;
    }

    @Override
        public void execute() {
	    	boolean val = Boolean.valueOf(msg);
            launch.setStatisticsEnable(val, idx);
        }
        public String getFeedbackMsg() {
            return launch.getStatisticsEnable(idx);
        }
}

class StatisticsResetCommand implements CommandIf {
	CommandReceiver launch;
    String msg;
    int idx;

    public StatisticsResetCommand(CommandReceiver launch, String arg, int sessId) {
        this.launch = launch;
        this.msg = arg;
        this.idx = sessId;
    }

    @Override
        public void execute() {
	    	boolean val = Boolean.valueOf(msg);
            launch.setStatisticsReset(val, idx);
        }
        public String getFeedbackMsg() {
            return "false";	//no feedback for this join
        }
}

class StatisticsDisableCommand implements CommandIf {
	CommandReceiver launch;
    String msg;
    int idx;

    public StatisticsDisableCommand(CommandReceiver launch, String arg, int sessId) {
        this.launch = launch;
        this.msg = arg;
        this.idx = sessId;
    }

    @Override
        public void execute() {
	    	boolean val = Boolean.valueOf(msg);
            launch.setStatisticsDisable(val, idx);
        }
        public String getFeedbackMsg() {
            return launch.getStatisticsDisable(idx);
        }
}

class StatisticsNumVideoPacketsCommand implements CommandIf {
	CommandReceiver launch;
    String msg;

    public StatisticsNumVideoPacketsCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
    		// Feedback only join
        }
        public String getFeedbackMsg() {
            return launch.getNumVideoPackets();
        }
}

class StatisticsNumVideoPacketsDroppedCommand implements CommandIf {
	CommandReceiver launch;
    String msg;

    public StatisticsNumVideoPacketsDroppedCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
    		// Feedback only join
        }
        public String getFeedbackMsg() {
            return launch.getNumVideoPacketsDropped();
        }
}

class StatisticsNumAudioPacketsCommand implements CommandIf {
	CommandReceiver launch;
    String msg;

    public StatisticsNumAudioPacketsCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
    		// Feedback only join
        }
        public String getFeedbackMsg() {
            return launch.getNumAudioPackets();
        }
}

class StatisticsNumAudioPacketsDroppedCommand implements CommandIf {
	CommandReceiver launch;
    String msg;

    public StatisticsNumAudioPacketsDroppedCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
    		// Feedback only join
        }
        public String getFeedbackMsg() {
            return launch.getNumAudioPacketsDropped();
        }
}

class SetMulticastTTLCommand implements CommandIf {
    CommandReceiver launch;
    String msg;
    
    public SetMulticastTTLCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            int val = Integer.valueOf(msg);
            launch.setMulticastTTL(val);
        }
        public String getFeedbackMsg() {
            return launch.getMulticastTTL();
        }
}

class OsdEnableCommand implements CommandIf {
    CommandReceiver launch;
    String msg;
    
    public OsdEnableCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            boolean val = Boolean.valueOf(msg);
            if(val)
                launch.EnableOsd();
        }
        public String getFeedbackMsg() {
            return launch.getOsdEnable();
        }
}

class OsdDisableCommand implements CommandIf {
    CommandReceiver launch;
    String msg;
    
    public OsdDisableCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            boolean val = Boolean.valueOf(msg);
            if(val)
                launch.DisableOsd();
        }
        public String getFeedbackMsg() {
            return launch.getOsdDisable();
        }
}

class OsdTextCommand implements CommandIf {
    CommandReceiver launch;
    String msg;
    
    public OsdTextCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            launch.setOsdText(msg);
        }
        public String getFeedbackMsg() {
            return launch.getOsdText();
        }
}

class OsdLocationCommand implements CommandIf {
    CommandReceiver launch;
    String msg;
    
    public OsdLocationCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            launch.setOsdLocation(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return launch.getOsdLocation();
        }
}

class OsdXPosCommand implements CommandIf {
    CommandReceiver launch;
    String msg;
    
    public OsdXPosCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            launch.setOsdXPos(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return launch.getOsdXPos();
        }
}

class OsdYPosCommand implements CommandIf {
    CommandReceiver launch;
    String msg;
    
    public OsdYPosCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            launch.setOsdYPos(launch.VALIDATE_INT(msg));
        }

        public String getFeedbackMsg() {
            return launch.getOsdYPos();
        }
}

class AirMediaLaunchCommand extends CrestronCommand {
	public AirMediaLaunchCommand(CommandReceiver launch, String arg, int sessId) {
		super(launch, arg, sessId);
	}
	public void execute() {
		boolean val = Boolean.valueOf(msg);
		launch.setAirMediaLaunch(val, idx, true); //Launch is full screen
	}
	public String getFeedbackMsg() {
		return launch.getAirMediaLaunch(idx);
	}
}

class AirMediaWindowLaunchCommand extends CrestronCommand {
	public AirMediaWindowLaunchCommand(CommandReceiver launch, String arg, int sessId) {
		super(launch, arg, sessId);
	}
	public void execute() {
		boolean val = Boolean.valueOf(msg);
		launch.setAirMediaLaunch(val, idx, false); //Window launch is with window parameters
	}
	public String getFeedbackMsg() {
		return launch.getAirMediaLaunch(idx);
	}
}

class AirMediaLoginCodeCommand extends CrestronCommand {
	public AirMediaLoginCodeCommand(CommandReceiver launch, String arg, int sessId) {
		super(launch, arg, sessId);
	}
	public void execute() {
		launch.setAirMediaLoginCode(CommandReceiver.VALIDATE_INT(msg), idx);
	}
	public String getFeedbackMsg() {
		return launch.getAirMediaLoginCode(idx);
	}
}

class AirMediaLoginModeCommand extends CrestronCommand {
	public AirMediaLoginModeCommand(CommandReceiver launch, String arg, int sessId) {
		super(launch, arg, sessId);
	}

	public void execute() {
		launch.setAirMediaLoginMode(CommandReceiver.VALIDATE_INT(msg), idx);
	}
	public String getFeedbackMsg() {
		return launch.getAirMediaLoginMode(idx);
	}
}

class AirMediaModeratorCommand extends CrestronCommand {
	public AirMediaModeratorCommand(CommandReceiver launch, String arg, int sessId) {
		super(launch, arg, sessId);
	}
	public void execute() {
		launch.setAirMediaModerator(Boolean.valueOf(msg), idx);
	}
	public String getFeedbackMsg() {
		return launch.getAirMediaModerator(idx);
	}
}

class AirMediaResetConnectionsCommand extends CrestronCommand {
	public AirMediaResetConnectionsCommand(CommandReceiver launch, String arg, int sessId) {
		super(launch, arg, sessId);
	}
	public void execute() {
		launch.setAirMediaResetConnections(Boolean.valueOf(msg), idx);
	}
	public String getFeedbackMsg() {
		return launch.getAirMediaResetConnections(idx);
	}
}

class AirMediaDisconnectUserCommand extends CrestronCommand {
	public AirMediaDisconnectUserCommand(CommandReceiver launch, String arg, int sessId) {
		super(launch, arg, sessId);
	}	
	public void execute() {
		String[] tokens = msg.split("[:]"); // Format should be "UserId:JoinVal"
		if (tokens.length == 2)
		{
			launch.setAirMediaDisconnectUser(CommandReceiver.VALIDATE_INT(tokens[0]), Boolean.valueOf(tokens[1]), idx);
		}
	}
	public String getFeedbackMsg() {
		return launch.getAirMediaDisconnectUser(idx);
	}
}

class AirMediaStartUserCommand extends CrestronCommand {
	public AirMediaStartUserCommand(CommandReceiver launch, String arg, int sessId) {
		super(launch, arg, sessId);
	}	
	public void execute() {
		String[] tokens = msg.split("[:]"); // Format should be "UserId:JoinVal"
		if (tokens.length == 2)
		{
			launch.setAirMediaStartUser(CommandReceiver.VALIDATE_INT(tokens[0]), Boolean.valueOf(tokens[1]), idx);
		}
	}
	public String getFeedbackMsg() {
		return launch.getAirMediaStartUser(idx);
	}
}

class AirMediaUserPositionCommand extends CrestronCommand {
	public AirMediaUserPositionCommand(CommandReceiver launch, String arg, int sessId) {
		super(launch, arg, sessId);
	}	
	public void execute() {
		String[] tokens = msg.split("[:]"); // Format should be "UserId:JoinVal"
		if (tokens.length == 2)
		{
			launch.setAirMediaUserPosition(CommandReceiver.VALIDATE_INT(tokens[0]), CommandReceiver.VALIDATE_INT(tokens[1]), idx);
		}
	}
	public String getFeedbackMsg() {
		return launch.getAirMediaUserPosition(idx);
	}
}

class AirMediaStopUserCommand extends CrestronCommand {
	public AirMediaStopUserCommand(CommandReceiver launch, String arg, int sessId) {
		super(launch, arg, sessId);
	}	
	public void execute() {
		String[] tokens = msg.split("[:]"); // Format should be "UserId:JoinVal"
		if (tokens.length == 2)
		{
			launch.setAirMediaStopUser(CommandReceiver.VALIDATE_INT(tokens[0]), Boolean.valueOf(tokens[1]), idx);
		}
	}
	public String getFeedbackMsg() {
		return launch.getAirMediaStopUser(idx);
	}
}

class AirMediaOsdImageCommand extends CrestronCommand {
	public AirMediaOsdImageCommand(CommandReceiver launch, String arg, int sessId) {
		super(launch, arg, sessId);
	}	
	public void execute() {
		launch.setAirMediaOsdImage(msg, idx);
	}
	public String getFeedbackMsg() {
		return launch.getAirMediaOsdImage(idx);
	}
}

class AirMediaIpAddressPromptCommand extends CrestronCommand {
	public AirMediaIpAddressPromptCommand(CommandReceiver launch, String arg, int sessId) {
		super(launch, arg, sessId);
	}	
	public void execute() {
		launch.setAirMediaIpAddressPrompt(Boolean.valueOf(msg), idx);
	}
	public String getFeedbackMsg() {
		return launch.getAirMediaIpAddressPrompt(idx);
	}
}

class AirMediaDomainNamePromptCommand extends CrestronCommand {
	public AirMediaDomainNamePromptCommand(CommandReceiver launch, String arg, int sessId) {
		super(launch, arg, sessId);
	}	
	public void execute() {
		launch.setAirMediaDomainNamePrompt(Boolean.valueOf(msg), idx);
	}
	public String getFeedbackMsg() {
		return launch.getAirMediaDomainNamePrompt(idx);
	}
}

class AirMediaWindowPositionCommand extends CrestronCommand {
	public AirMediaWindowPositionCommand(CommandReceiver launch, String arg, int sessId) {
		super(launch, arg, sessId);
	}	
	public void execute() {
		String[] tokens = msg.split("[,]"); // Format should be "x,y,width,height"
		if (tokens.length == 4)
		{
			launch.setAirMediaWindowPosition(
					CommandReceiver.VALIDATE_INT(tokens[0]),
					CommandReceiver.VALIDATE_INT(tokens[1]),
					CommandReceiver.VALIDATE_INT(tokens[2]),
					CommandReceiver.VALIDATE_INT(tokens[3]));
		}
	}
}

class AirMediaWindowXOffsetCommand extends CrestronCommand {
	public AirMediaWindowXOffsetCommand(CommandReceiver launch, String arg, int sessId) {
		super(launch, arg, sessId);
	}	
	public void execute() {
		launch.setAirMediaWindowXOffset(CommandReceiver.VALIDATE_INT(msg), idx);
	}
	public String getFeedbackMsg() {
		return launch.getAirMediaWindowXOffset(idx);
	}
}

class AirMediaWindowYOffsetCommand extends CrestronCommand {
	public AirMediaWindowYOffsetCommand(CommandReceiver launch, String arg, int sessId) {
		super(launch, arg, sessId);
	}	
	public void execute() {
		launch.setAirMediaWindowYOffset(CommandReceiver.VALIDATE_INT(msg), idx);
	}
	public String getFeedbackMsg() {
		return launch.getAirMediaWindowYOffset(idx);
	}
}

class AirMediaWindowWidthCommand extends CrestronCommand {
	public AirMediaWindowWidthCommand(CommandReceiver launch, String arg, int sessId) {
		super(launch, arg, sessId);
	}	
	public void execute() {
		launch.setAirMediaWindowWidth(CommandReceiver.VALIDATE_INT(msg), idx);
	}
	public String getFeedbackMsg() {
		return launch.getAirMediaWindowWidth(idx);
	}
}

class AirMediaWindowHeightCommand extends CrestronCommand {
	public AirMediaWindowHeightCommand(CommandReceiver launch, String arg, int sessId) {
		super(launch, arg, sessId);
	}	
	public void execute() {
		launch.setAirMediaWindowHeight(CommandReceiver.VALIDATE_INT(msg), idx);
	}
	public String getFeedbackMsg() {
		return launch.getAirMediaWindowHeight(idx);
	}
}

class AirMediaApplyLayoutPasswordCommand extends CrestronCommand {
	public AirMediaApplyLayoutPasswordCommand(CommandReceiver launch, String arg, int sessId) {
		super(launch, arg, sessId);
	}	
	public void execute() {
		launch.airMediaApplyLayoutPassword(Boolean.valueOf(msg), idx);
	}
}

class AirMediaLayoutPasswordCommand extends CrestronCommand {
	public AirMediaLayoutPasswordCommand(CommandReceiver launch, String arg, int sessId) {
		super(launch, arg, sessId);
	}	
	public void execute() {
		launch.setAirMediaLayoutPassword(msg, idx);
	}
	public String getFeedbackMsg() {
		return launch.getAirMediaLayoutPassword(idx);
	}
}

class AirMediaApplyOsdImageCommand extends CrestronCommand {
	public AirMediaApplyOsdImageCommand(CommandReceiver launch, String arg, int sessId) {
		super(launch, arg, sessId);
	}	
	public void execute() {
		launch.airMediaApplyOsdImage(Boolean.valueOf(msg), idx);
	}
}

class AirMediaDisplayLoginCodeCommand extends CrestronCommand {
	public AirMediaDisplayLoginCodeCommand(CommandReceiver launch, String arg, int sessId) {
		super(launch, arg, sessId);
	}	
	public void execute() {
		launch.setAirMediaDisplayLoginCode(Boolean.valueOf(msg), idx);
	}
	public String getFeedbackMsg() {
		return launch.getAirMediaDisplayLoginCode(idx);
	}
}

class AirMediaDisplayScreenCommand extends CrestronCommand {
	public AirMediaDisplayScreenCommand(CommandReceiver launch, String arg, int sessId) {
		super(launch, arg, sessId);
	}	
	public void execute() {
		launch.airMediaSetDisplayScreen(CommandReceiver.VALIDATE_INT(msg), idx);
	}
}

class camStreamMulticastEnableCommand extends CrestronCommand {
	public camStreamMulticastEnableCommand(CommandReceiver launch, String arg) {
		super(launch, arg);
	}	
	public void execute() {		
		launch.setCamStreamMulticastEnable(Boolean.valueOf(msg));
	}
	public String getFeedbackMsg() {
		return launch.getCamStreamMulticastEnable();
	}
}

class camStreamResolutionCommand extends CrestronCommand {
	public camStreamResolutionCommand(CommandReceiver launch, String arg) {
		super(launch, arg);
	}	
	public void execute() {		
		launch.setCamStreamResolution(CommandReceiver.VALIDATE_INT(msg));
	}
	public String getFeedbackMsg() {
		return launch.getCamStreamResolution();
	}
}

class camStreamUrlCommand extends CrestronCommand {
	public camStreamUrlCommand(CommandReceiver launch, String arg) {
		super(launch, arg);
	}	
	public String getFeedbackMsg() {
		return launch.getCamStreamUrl();
	}
}

class camStreamSnapshotUrlCommand extends CrestronCommand {
	public camStreamSnapshotUrlCommand(CommandReceiver launch, String arg) {
		super(launch, arg);
	}	
	public String getFeedbackMsg() {
		return launch.getCamStreamSnapshotUrl();
	}
}

class camStreamNameCommand extends CrestronCommand {
	public camStreamNameCommand(CommandReceiver launch, String arg) {
		super(launch, arg);
	}	
	public void execute() {		
		launch.setCamStreamName(msg);
	}
	public String getFeedbackMsg() {
		return launch.getCamStreamName();
	}
}

class camStreamSnapshotNameCommand extends CrestronCommand {
	public camStreamSnapshotNameCommand(CommandReceiver launch, String arg) {
		super(launch, arg);
	}	
	public void execute() {		
		launch.setCamStreamSnapshotName(msg);
	}
	public String getFeedbackMsg() {
		return launch.getCamStreamSnapshotName();
	}
}

class camStreamMulticastAddressCommand extends CrestronCommand {
	public camStreamMulticastAddressCommand(CommandReceiver launch, String arg) {
		super(launch, arg);
	}	
	public void execute() {		
		launch.setCamStreamMulticastAddress(msg);
	}
	public String getFeedbackMsg() {
		return launch.getCamStreamMulticastAddress();
	}
}

class RestartStreamOnStartCommand implements CommandIf {
	CommandReceiver launch;
    String msg;

    public RestartStreamOnStartCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
    public void execute() {
    	boolean val = Boolean.valueOf(msg);
        launch.setRestartStreamOnStart(val);
    }
    public String getFeedbackMsg() {
    	return msg;	//no feedback for this join
    }
}

class UseGstreamerCommand implements CommandIf {
	CommandReceiver launch;
    String msg;

    public UseGstreamerCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
    public void execute() {
    	boolean val = Boolean.valueOf(msg);
        launch.setUseGstreamer(val);
    }
    public String getFeedbackMsg() {
    	return msg;	//no feedback for this join
    }
}

class UseNewSinkCommand implements CommandIf {
	CommandReceiver launch;
	String msg;
	int idx;

	public UseNewSinkCommand(CommandReceiver launch, String arg, int sessId) {
		this.launch = launch;
		this.msg = arg;
        	this.idx = sessId;
	}

	@Override
	public void execute() {
		boolean val = Boolean.valueOf(msg);
		launch.setNewSink(val, idx);
	}
	public String getFeedbackMsg() {
		return msg;	//no feedback for this join
	}
}

class UseNewIpAddrCommand implements CommandIf {
	CommandReceiver launch;
	String msg;

	public UseNewIpAddrCommand(CommandReceiver launch, String arg) {
		this.launch = launch;
		this.msg = arg;
	}

	@Override
	public void execute() {
            launch.setChangedIpAddress(msg);
	}
	public String getFeedbackMsg() {
		return msg;	//no feedback for this join
	}
}

class FIELDDEBUGJNICommand implements CommandIf {
	CommandReceiver launch;
	String msg;
	int idx;

	public FIELDDEBUGJNICommand(CommandReceiver launch, String arg, int sessId) {
		this.launch = launch;
		this.msg = arg;
        this.idx = sessId;
	}

	@Override
	public void execute() {
		//int val = Integer.valueOf(msg);
		launch.setFieldDebugJni(msg, idx);
	}
	public String getFeedbackMsg() {
		return msg;	//no feedback for this join
	}
}

class ResetAllWindowsCommand implements CommandIf {
	CommandReceiver launch;
	String msg;

	public ResetAllWindowsCommand(CommandReceiver launch, String arg) {
		this.launch = launch;
		this.msg = arg;
	}

	@Override
	public void execute() {
		launch.resetAllWindows();
	}
	public String getFeedbackMsg() {
		return msg;	//no feedback for this join
	}
}

class SetLogLevelCommand implements CommandIf {
	CommandReceiver launch;
	String msg;

	public SetLogLevelCommand(CommandReceiver launch, String arg) {
		this.launch = launch;
		this.msg = arg;
	}

	@Override
	public void execute() {
		try {
			launch.setLogLevel(Integer.parseInt(msg));
		} catch (Exception e) {}
	}
	public String getFeedbackMsg() {
		return msg;	//no feedback for this join
	}
}

