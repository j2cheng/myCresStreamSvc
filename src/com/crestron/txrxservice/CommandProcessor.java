
package com.crestron.txrxservice;

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
            boolean val = Boolean.valueOf(msg);
            if(val)
                launch.EnableTcpInterleave(idx);
        }
        public String getFeedbackMsg() {
            return msg;
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
    		int volume = Integer.valueOf(msg);
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