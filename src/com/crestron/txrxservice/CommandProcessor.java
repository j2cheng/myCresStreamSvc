
package com.crestron.txrxservice;

class DeviceCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public DeviceCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            launch.setDeviceMode(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return msg;
        }
}

class SessionInitiationCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public SessionInitiationCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            launch.setSessionInitation(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return msg;
        }
}

class TModeCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public TModeCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            launch.SetTMode(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return msg;
        }
}

class VencCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public VencCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            launch.SetVenc(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return msg;
        }
}

class RtspPortCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public RtspPortCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            launch.setRtspPort(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return msg;
        }
}

class TsPortCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public TsPortCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            launch.setTsPort(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return msg;
        }
}

class RtpVCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public RtpVCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            launch.setRtpV(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return msg;
        }
}

class RtpACommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public RtpACommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            launch.setRtpA(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return msg;
        }
}

class VfrCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public VfrCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            launch.setVfr(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return msg;
        }
}

class VbrCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public VbrCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            launch.setVbr(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return msg;
        }
}

class TcpInterleaveCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public TcpInterleaveCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            boolean val = Boolean.valueOf(msg);
            if(val)
                launch.EnableTcpInterleave();
        }
        public String getFeedbackMsg() {
            return msg;
        }
}

class IpaddrCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public IpaddrCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            launch.setIP(msg);
        }
        public String getFeedbackMsg() {
            return msg;
        }
}

class EncodingResolutionCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public EncodingResolutionCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            launch.setEncodingResolution(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return msg;
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
            return msg;
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
            if(val)
                launch.setUnmute(val);
        }
        public String getFeedbackMsg() {
            return msg;
        }
}

class LatencyCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public LatencyCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            launch.setLatency(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return msg;
        }
}

class PasswdEnableCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public PasswdEnableCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            launch.passwdEnable();
        }
        public String getFeedbackMsg() {
            return msg;
        }
}

class PasswdDisableCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public PasswdDisableCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            launch.passwdDisable();
        }
        public String getFeedbackMsg() {
            return msg;
        }
}

class UserCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public UserCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            launch.setUserName(msg);
        }
        public String getFeedbackMsg() {
            return msg;
        }
}

class PasswdCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public PasswdCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            launch.setPasswd(msg);
        }
        public String getFeedbackMsg() {
            return msg;
        }
}

class StreamUrlCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public StreamUrlCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            launch.setStreamUrl(msg);
        }
        public String getFeedbackMsg() {
            return launch.getStreamUrl();
        }
}

class StartCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public StartCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            boolean val = Boolean.valueOf(msg);
            if(val)
                launch.setStart();
        }
        public String getFeedbackMsg() {
            return launch.getStartStatus();
        }
}

class StopCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public StopCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            boolean val = Boolean.valueOf(msg);
            if(val)
                launch.setStop();
        }
        public String getFeedbackMsg() {
            return launch.getStopStatus();
        }
}

class PauseCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public PauseCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            boolean val = Boolean.valueOf(msg);
            if(val)
                launch.setPause();
        }
        public String getFeedbackMsg() {
            return launch.getPauseStatus();
        }
}

class XlocCommand implements CommandIf {

    CommandReceiver launch;
    String msg;

    public XlocCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public  void execute() {
            launch.setXloc(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return launch.getXloc();
        }
}

class YlocCommand implements CommandIf {

    CommandReceiver launch;
    String msg;

    public YlocCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            launch.setYloc(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return launch.getYloc();
        }
}

class DestWidthCommand implements CommandIf {

    CommandReceiver launch;
    String msg;

    public DestWidthCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            launch.setDestWidth(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return launch.getDestWidth();
        }
}

class DestHeightCommand implements CommandIf {

    CommandReceiver launch;
    String msg;

    public DestHeightCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            launch.setDestHeight(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return launch.getDestHeight();
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


class HresCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public HresCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            //launch.(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return launch.getHresFb();
        }
}


class VresCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public VresCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            //launch.(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return launch.getVresFb();
        }
}

class ResolutionCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public ResolutionCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            //launch.(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return launch.getFpsFb();
        }
}

class AspectCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public AspectCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            //launch.(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return launch.getAspectFb();
        }
}

class SAudioFormatCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public SAudioFormatCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            //launch.(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return launch.getAudioFormatFb();
        }
}

class SAudioChannelsCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public SAudioChannelsCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            //launch.(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return launch.getAudioChannelsFb();
        }
}

class StreamStateCommand implements CommandIf {
    CommandReceiver launch;
    String msg;

    public StreamStateCommand(CommandReceiver launch, String arg) {
        this.launch = launch;
        this.msg = arg;
    }

    @Override
        public void execute() {
            //launch.(launch.VALIDATE_INT(msg));
        }
        public String getFeedbackMsg() {
            return launch.getStreamState();
        }
}


