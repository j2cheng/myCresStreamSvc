package com.crestron.txrxservice;

import java.io.IOException;

import com.crestron.txrxservice.CresStreamCtrl.DeviceMode;

import android.util.Log;

public class CommandParser {

    String TAG = "TxRx CmdParser";
    StringTokenizer tokenizer; 
    CommandInvoker invoke;
    CommandReceiver cmdRx;
    CommandIf cmd ; 

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
        TCPINTERLEAVE,
        MULTICAST_ADDRESS,
        ENCODING_RESOLUTION,
        AUDIO_MUTE,
        AUDIO_UNMUTE,
        LATENCY,
        PASSWORD_ENABLE,
        PASSWORD_DISABLE,
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
        //STREAM IN
        STREAMIN_HORIZONTAL_RES_FB,
        STREAMIN_VERTICAL_RES_FB,
        STREAMIN_FPS_FB,
        STREAMIN_ASPECT_RATIO,
        STREAMIN_AUDIO_FORMAT,
        STREAMIN_AUDIO_CHANNELS,
        //STREAM OUT
        STREAMOUT_HORIZONTAL_RES_FB,
        STREAMOUT_VERTICAL_RES_FB,
        STREAMOUT_FPS_FB,
        STREAMOUT_ASPECT_RATIO,
        STREAMOUT_AUDIO_FORMAT,
        STREAMOUT_AUDIO_CHANNELS,
        //STREAMING
        PROCESSING_FB,
        DEVICE_READY_FB,
        ELAPSED_SECONDS_FB,
        STREAM_STATUS_FB, 
        INITIATOR_ADDRESS_FB,
        //STATUS  
        STREAMSTATE;
        //UPDATEREQUEST;
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
        sb.append("TCPINTERLEAVE (=true enable tcp for RTSP Streaming In)\r\n");
        sb.append("MULTICAST_ADDRESS(=xxx.xxx.xxx.xxx)\r\n");
        sb.append("ENCODING_RESOLUTION(=0 to 17)\r\n");
        sb.append("AUDIO_MUTE(=1:true/0:false)\r\n");
        sb.append("AUDIO_UNMUTE(=1:true/0:false)\r\n");
        sb.append("LATENCY=100 to 5000 (in msec)\r\n");
        sb.append("PASSWORD_ENABLE\r\n");
        sb.append("PASSWORD_DISABLE\r\n");
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

    // TODO: These joins should be organized by slots
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
            case TCPINTERLEAVE:
                cmd = new TcpInterleaveCommand(cmdRx, arg); 
                break;
            case MULTICAST_ADDRESS:
                cmd = new IpaddrCommand(cmdRx, arg); 
                break;
            case ENCODING_RESOLUTION:
                cmd = new EncodingResolutionCommand(cmdRx, arg); 
                break;
            case AUDIO_MUTE:
                cmd = new MuteCommand(cmdRx, arg); 
                break;
            case AUDIO_UNMUTE:
                cmd = new UnmuteCommand(cmdRx, arg); 
                break;
            case LATENCY:
                cmd = new LatencyCommand(cmdRx, arg); 
                break;
            case PASSWORD_ENABLE:
                cmd = new PasswdEnableCommand(cmdRx, arg); 
                break;
            case PASSWORD_DISABLE:
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
            //STREAM IN
            case STREAMIN_HORIZONTAL_RES_FB:
            	cmd = new StreamInHresCommand(cmdRx, arg); 
                break; 
            case STREAMIN_VERTICAL_RES_FB:
            	cmd = new StreamInVresCommand(cmdRx, arg); 
                break; 
            case STREAMIN_FPS_FB:
            	cmd = new StreamInFpsCommand(cmdRx, arg); 
                break;
            case STREAMIN_ASPECT_RATIO:
            	cmd = new StreamInAspectCommand(cmdRx, arg); 
                break;   
            case STREAMIN_AUDIO_FORMAT:
            	cmd = new StreamInAudioFormatCommand(cmdRx, arg); 
                break;
            case STREAMIN_AUDIO_CHANNELS:
            	cmd = new StreamInAudioChannelsCommand(cmdRx, arg); 
                break;   
            //STREAM OUT
            case STREAMOUT_HORIZONTAL_RES_FB:
        		cmd = new StreamOutHresCommand(cmdRx, arg); 
                break; 
            case STREAMOUT_VERTICAL_RES_FB:
        		cmd = new StreamOutVresCommand(cmdRx, arg); 
                break; 
            case STREAMOUT_FPS_FB:
            	cmd = new StreamOutFpsCommand(cmdRx, arg); 
                break;
            case STREAMOUT_ASPECT_RATIO:
            	cmd = new StreamOutAspectCommand(cmdRx, arg); 
                break;   
            case STREAMOUT_AUDIO_FORMAT:
            	cmd = new StreamOutAudioFormatCommand(cmdRx, arg); 
                break;
            case STREAMOUT_AUDIO_CHANNELS:
            	cmd = new StreamOutAudioChannelsCommand(cmdRx, arg); 
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
                //STATUS  
            case STREAMSTATE:
                cmd = new StreamStateCommand(cmdRx, arg); 
                break;
            default:
                break;
        }
        return cmd;
    }
    
    //Validating received command
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

        if(msg !=null && validateMsg(msg[0].toUpperCase())){
            if(msg.length>1){ //Process & Reply Feedback 
                cmd = ProcCommand(msg[0].toUpperCase(), msg[1]); 
                if (cmd != null)
                {
	                invoke.setCommand(cmd);
	                invoke.set();
                }
                reply = processReplyFbMessage(msg[0], msg[1]); 
            }
            else { //Test Stub Query
                String tmp_str = tokenizer.getStringValueOf(msg[0]);
                Log.d(TAG, "Querying:: searched for "+msg[0]+" and got value of "+tmp_str);
                cmd = ProcCommand(msg[0].toUpperCase(), tmp_str); 
                if (cmd != null)
                {
	                invoke.setCommand(cmd);
	                String fbMsg = invoke.get();
	                sb.append(receivedMsg).append("=").append(fbMsg);
	                reply = sb.toString();
                }
                //reply = processCmdMessage(receivedMsg, msg[0]); 
            }
        } else {
            sb.append(receivedMsg).append(" is invalid command!!!");
            reply = sb.toString();
        }
        return reply;
    }
}
