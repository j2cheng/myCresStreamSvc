package com.crestron.txrxservice;

import java.io.IOException;
import android.util.Log;

public class CommandParser {

    String TAG = "TxRx CmdParser"; 
    StringTokenizer tokenizer; 
    CommandInvoker invoke;
    CommandReceiver cmdRx;
    CommandIf cmd ; 
    //TEMP HACK:TODO Needs to be removed
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
    public enum CmdTable {
        MODE,
        SESSIONINITIATION,
        TRANSPORTMODE,
        VENCPROFILE,
        STREAMURL,
        RTSPPORT,
        TSPORT,
        RTPVIDEOPORT,
        RTPAUDIOPORT,
        VFRAMERATE,
        VBITRATE,
        VENCLEVEL,
        IPADDRESS,
        MUTE,
        UNMUTE,
        LATENCY,
        PASSWD_ENABLE,
        PASSWD_DISABLE,
        USERNAME,
        PASSWORD,
        START,
        STOP,
        PAUSE,
        XLOC,
        YLOC,
        W, 
        H,
        //HDMI IN 
        HDMIIN_SYNC_DETECTED,
        HDMIIN_INTERLACED,
        HDMIIN_CEC_ERROR,
        HDMIIN_HORIZONTAL_RES_FB,
        HDMIIN_VERTICAL_RES_FB,
        HDMIIN_FPS_FB,
        HDMIIN_ASPECT_RATIO,
        HDMIIN_AUDIO_FORMAT,
        HDMIIN_AUDIO_CHANNELS,
        HDMIIN_TRANSMIT_CEC_MESSAGE,
        HDMIIN_RECEIVE_CEC_MESSAGE,
        //HDMI OUT
        HDMIOUT_SYNC_DETECTED,
        HDMIOUT_INTERLACED,
        HDMIOUT_CEC_ERROR,
        HDMIOUT_HORIZONTAL_RES_FB,
        HDMIOUT_VERTICAL_RES_FB,
        HDMIOUT_FPS_FB,
        HDMIOUT_ASPECT_RATIO,
        HDMIOUT_AUDIO_FORMAT,
        HDMIOUT_AUDIO_CHANNELS,
        HDMIOUT_TRANSMIT_CEC_MESSAGE,
        HDMIOUT_RECEIVE_CEC_MESSAGE,
        //STREAMING
        PROCESSING_FB,
        DEVICE_READY_FB,
        ELAPSED_SECONDS_FB,
        STREAM_STATUS_FB, 
        INITIATOR_ADDRESS_FB,
        HORIZONTAL_RESOLUTION_FB, 
        VERTICAL_RESOLUTION_FB, 
        FPS_RESOLUTION_FB, 
        ASPECT_FB, 
        AUDIO_FORMAT_FB, 
        AUDIO_CHANNELS_FB,
        //STATUS  
        STREAMSTATE,
        UPDATEREQUEST;
    }

    public  CommandParser(CresStreamCtrl a_crestctrl){
        tokenizer = new StringTokenizer();
        invoke = new CommandInvoker();
        cmdRx = new CommandReceiver(a_crestctrl);
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

    private String processCmdMessage(String receivedMsg, String msg){
        StringBuilder sb = new StringBuilder(1024);
        //sb.append(receivedMsg).append("=").append(temp);
        return (sb.toString());
    }

    private String processReplyFbMessage(String msg1, String msg2){
        StringBuilder sb = new StringBuilder(1024);
        sb.append(msg1).append("=").append(msg2);
        return (sb.toString());
    }


    CommandIf ProcCommand (String msg, String arg){
        CommandIf cmd = null;
        switch(CmdTable.valueOf(msg)){
            case MODE:
                cmd = new DeviceCommand(cmdRx, arg); 
                break;
            case SESSIONINITIATION:
                cmd = new SessionInitiationCommand(cmdRx, arg); 
                break;
            case TRANSPORTMODE:
                cmd = new TModeCommand(cmdRx, arg); 
                break;
            case VENCPROFILE:
                cmd = new VencCommand(cmdRx, arg); 
                break;
            case RTSPPORT:
                cmd = new RtspPortCommand(cmdRx, arg); 
                break;
            case TSPORT:
                cmd = new TsPortCommand(cmdRx, arg); 
                break;
            case RTPVIDEOPORT:
                cmd = new RtpVCommand(cmdRx, arg); 
                break;
            case RTPAUDIOPORT:
                cmd = new RtpACommand(cmdRx, arg); 
                break;
            case VFRAMERATE:
                cmd = new VfrCommand(cmdRx, arg); 
                break;
            case VBITRATE:
                cmd = new VbrCommand(cmdRx, arg); 
                break;
            case VENCLEVEL:
                cmd = new VLevelCommand(cmdRx, arg); 
                break;
            case IPADDRESS:
                cmd = new IpaddrCommand(cmdRx, arg); 
                break;
            case MUTE:
                cmd = new MuteCommand(cmdRx, arg); 
                break;
            case UNMUTE:
                cmd = new UnmuteCommand(cmdRx, arg); 
                break;
            case LATENCY:
                cmd = new LatencyCommand(cmdRx, arg); 
                break;
            case PASSWD_ENABLE:
                cmd = new PasswdEnableCommand(cmdRx, arg); 
                break;
            case PASSWD_DISABLE:
                cmd = new PasswdDisableCommand(cmdRx, arg); 
                break;
            case USERNAME:
                cmd = new UserCommand(cmdRx, arg); 
                break;
            case PASSWORD:
                cmd = new PasswdCommand(cmdRx, arg); 
                break;
            case STREAMURL:
                cmd = new StreamUrlCommand(cmdRx, arg); 
                break;
            case START:
                cmd = new StartCommand(cmdRx, arg); 
                break;
            case STOP:
                cmd = new StopCommand(cmdRx, arg); 
                break;
            case PAUSE:
                cmd = new PauseCommand(cmdRx, arg); 
                break;
            case XLOC:
                cmd = new XlocCommand(cmdRx, arg); 
                break;
            case YLOC:
                cmd = new YlocCommand(cmdRx, arg); 
                break;
            case W: 
                cmd = new DestWidthCommand(cmdRx, arg); 
                break;
            case H:
                cmd = new DestHeightCommand(cmdRx, arg); 
                break;
            case HDMIIN_SYNC_DETECTED:
                cmd = new InSyncCommand(cmdRx, arg); 
                break;
            case HDMIIN_INTERLACED:
                cmd = new InInterlaceCommand(cmdRx, arg); 
                break;
            case HDMIIN_CEC_ERROR:
                cmd = new InCecCommand(cmdRx, arg); 
                break;
            case HDMIIN_HORIZONTAL_RES_FB:
                cmd = new InHresCommand(cmdRx, arg); 
                break;
            case HDMIIN_VERTICAL_RES_FB:
                cmd = new InVResCommand(cmdRx, arg); 
                break;
            case HDMIIN_FPS_FB:
                cmd = new InFpsCommand(cmdRx, arg); 
                break;
            case HDMIIN_ASPECT_RATIO:
                cmd = new InAspectCommand(cmdRx, arg); 
                break;
            case HDMIIN_AUDIO_FORMAT:
                cmd = new InAudioFormatCommand(cmdRx, arg); 
                break;
            case HDMIIN_AUDIO_CHANNELS:
                cmd = new InAudioChannelsCommand(cmdRx, arg); 
                break;
            case HDMIIN_TRANSMIT_CEC_MESSAGE:
                cmd = new InTxCecCommand(cmdRx, arg); 
                break;
            case HDMIIN_RECEIVE_CEC_MESSAGE:
                cmd = new InRxCecCommand(cmdRx, arg); 
                break;
                //HDMI OUT
            case HDMIOUT_SYNC_DETECTED:
                cmd = new OutSyncCommand(cmdRx, arg); 
                break;
            case HDMIOUT_INTERLACED:
                cmd = new OutInterlaceCommand(cmdRx, arg); 
                break;
            case HDMIOUT_CEC_ERROR:
                cmd = new OutCecCommand(cmdRx, arg); 
                break;
            case HDMIOUT_HORIZONTAL_RES_FB:
                cmd = new OutHresCommand(cmdRx, arg); 
                break;
            case HDMIOUT_VERTICAL_RES_FB:
                cmd = new OutVresCommand(cmdRx, arg); 
                break;
            case HDMIOUT_FPS_FB:
                cmd = new outFpsCommand(cmdRx, arg); 
                break;
            case HDMIOUT_ASPECT_RATIO:
                cmd = new OutAspectCommand(cmdRx, arg); 
                break;
            case HDMIOUT_AUDIO_FORMAT:
                cmd = new OutAudioFormatCommand(cmdRx, arg); 
                break;
            case HDMIOUT_AUDIO_CHANNELS:
                cmd = new OutAudioChannelsCommand(cmdRx, arg); 
                break;
            case HDMIOUT_TRANSMIT_CEC_MESSAGE:
                cmd = new OutTxCecCommand(cmdRx, arg); 
                break;
            case HDMIOUT_RECEIVE_CEC_MESSAGE:
                cmd = new OutRxCecCommand(cmdRx, arg); 
                break;
                //STREAMING
            case PROCESSING_FB:
                cmd = new ProcessingCommand(cmdRx, arg); 
                break;
            case DEVICE_READY_FB:
                cmd = new DeviceReadyCommand(cmdRx, arg); 
                break;
            case ELAPSED_SECONDS_FB:
                cmd = new ElapsedSecondsCommand(cmdRx, arg); 
                break;
            case STREAM_STATUS_FB:
                cmd = new StatusCommand(cmdRx, arg); 
                break;
            case INITIATOR_ADDRESS_FB:
                cmd = new InitAddressCommand(cmdRx, arg); 
                break;
            case HORIZONTAL_RESOLUTION_FB:
                cmd = new HresCommand(cmdRx, arg); 
                break;
            case VERTICAL_RESOLUTION_FB: 
                cmd = new VresCommand(cmdRx, arg); 
                break;
            case FPS_RESOLUTION_FB:
                cmd = new ResolutionCommand(cmdRx, arg); 
                break;
            case ASPECT_FB:
                cmd = new AspectCommand(cmdRx, arg); 
                break;
            case AUDIO_FORMAT_FB: 
                cmd = new SAudioFormatCommand(cmdRx, arg); 
                break;
            case AUDIO_CHANNELS_FB:
                cmd = new SAudioChannelsCommand(cmdRx, arg); 
                break;
                //STATUS  
            case STREAMSTATE:
                cmd = new StreamStateCommand(cmdRx, arg); 
                break;
            default:
                break;
        }
        return cmd;
    }
    
    private boolean validateMsg (String msg){
        try {
            CmdTable validatecmd = CmdTable.valueOf(msg);
            return true;
        } catch (IllegalArgumentException e){
            return false;
        }
    }

    public String processReceivedMessage(String receivedMsg){
        //tokenizer.printList();//DEBUG Purpose
        String[] msg = tokenizer.Parse(receivedMsg);
        StringBuilder sb = new StringBuilder(1024);
        String reply = ""; 

        if(validateMsg(msg[0].toUpperCase())){
            if(msg.length>1){ //Process & Reply Feedback 
                cmd = ProcCommand(msg[0].toUpperCase(), msg[1]); 
                invoke.setCommand(cmd);
                invoke.set();
                reply = processReplyFbMessage(msg[0], msg[1]); 
            }
            else { //Test Stub Query
                String tmp_str = tokenizer.getStringValueOf(msg[0]);
                Log.d(TAG, "Querying:: searched for "+msg+" and got value of "+tmp_str);
                cmd = ProcCommand(msg[0].toUpperCase(), tmp_str); 
                invoke.setCommand(cmd);
                String fbMsg = invoke.get();
                sb.append(receivedMsg).append("=").append(fbMsg);
                reply = sb.toString();
                //reply = processCmdMessage(receivedMsg, msg[0]); 
            }
        } else {
            sb.append(receivedMsg).append(" is invalid command!!!");
            reply = sb.toString();
        }
        return reply;
    }
}
