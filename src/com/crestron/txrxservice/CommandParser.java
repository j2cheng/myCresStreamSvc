package com.crestron.txrxservice;

import java.io.IOException;
import java.util.HashMap;

import com.crestron.txrxservice.CommandParser.CmdTable;
import com.crestron.txrxservice.CresStreamCtrl.DeviceMode;

import android.util.Log;

public class CommandParser {

    String TAG = "TxRx CmdParser";
    StringTokenizer tokenizer; 
    CommandInvoker invoke;
    CommandIf cmd ; 
    CresStreamCtrl ctrl;
    HashMap <CmdTable, CommandIf> cmdHashMap;

    public enum CmdTable {
        MODE,
        SERVICEMODE,
        SESSIONINITIATION,
        TRANSPORTMODE,
        VENCPROFILE,
        STREAMURL,
        PROXYENABLE,
        DECODEINTERNALRTSPPORT,
        HDCPENCRYPT,
        TXHDCPACTIVE,
        INTERNAL_RTSPPORT,
        RTSPPORT,
        TSPORT,
        RTPVIDEOPORT,
        RTPAUDIOPORT,
        VFRAMERATE,
        VBITRATE,
        TCPINTERLEAVE,
        MULTICAST_ADDRESS,
        ENCODING_RESOLUTION,
        AUDIO_VOLUME,
        AUDIO_MUTE,
        AUDIO_UNMUTE,
        PROCESS_HDMI_IN_AUDIO,
        AUTOMATIC_INITIATION_MODE,
        LATENCY,
        PASSWORD_ENABLE,
        PASSWORD_DISABLE,
        USERNAME,
        PASSWORD,
        START,
        STOP,
        STOPFULL,
        PAUSE,
        STRETCHVIDEO,
        XLOC,
        YLOC,
        W, 
        H,
        Z,
        WINDOW_DIMENSION,
        HDMI_OUT_EXTERNAL_HDCP_STATUS,
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
        HDMIIN_AUDIO_SAMPLE_RATE,
        HDMIIN_TRANSMIT_CEC_MESSAGE,
        HDMIIN_RECEIVE_CEC_MESSAGE,
        //HDMI OUT
        HDMIOUT_FORCE_HDCP,
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
        HDMI_SENDHDCPFB,
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
        STREAMOUT_RTSP_STREAM_FILENAME,
        STREAMOUT_RTSP_SESSION_NAME,
        STREAMOUT_KEY_FRAME_INTERVAL,
        //STREAMING
        PROCESSING_FB,
        DEVICE_READY_FB,
        ELAPSED_SECONDS_FB,
        STREAM_STATUS_FB, 
        INITIATOR_ADDRESS_FB,
        //STATUS  
        STREAMSTATE,
        //Ethernet
        STATISTICS_ENABLE,
        STATISTICS_DISABLE,
        STATISTICS_RESET,
        STATISTICS_NUMBEROFVIDEOPACKETS,
        STATISTICS_NUMBEROFVIDEOPACKETSDROPPED,
        STATISTICS_NUMBEROFAUDIOPACKETS,
        STATISTICS_NUMBEROFAUDIOPACKETSDROPPED,
        MULTICASTTTL,
        AUXILIARY_IP_ADDRESS,
        //OSD            
        OSD_ENABLE,
        OSD_DISABLE,
        OSD_TEXT,
        OSD_LOCATION,
        OSD_X,
        OSD_Y,
        
        // AirMedia Top slot
        AIRMEDIA_LAUNCH,
        AIRMEDIA_WINDOW_LAUNCH,
        AIRMEDIA_ENABLE,
        AIRMEDIA_LOGIN_CODE,
        AIRMEDIA_LOGIN_MODE,
        AIRMEDIA_WINDOW_POSITION,
        AIRMEDIA_WINDOW_X_OFFSET,
        AIRMEDIA_WINDOW_Y_OFFSET,
        AIRMEDIA_WINDOW_WIDTH,
        AIRMEDIA_WINDOW_HEIGHT,
        AIRMEDIA_ADAPTOR_SELECT,
        AIRMEDIA_CONNECTION_ADDRESS,
        AIRMEDIA_RESTART,
        AIRMEDIA_PROJECTION_LOCK,
        
        // AirMedia Layout control
        AIRMEDIA_MODERATOR,
        AIRMEDIA_RESET_CONNECTIONS,
        AIRMEDIA_APPLY_LAYOUT_PASSWORD,
        AIRMEDIA_LAYOUT_PASSWORD,
        AIRMEDIA_DISCONNECT_USER,
        AIRMEDIA_START_USER,
        AIRMEDIA_USER_POSITION,
        AIRMEDIA_STOP_USER,
        
        // AirMedia OSD Control
        AIRMEDIA_APPLY_OSD_IMAGE,
        AIRMEDIA_OSD_IMAGE,
        AIRMEDIA_DISPLAY_LOGIN_CODE,
        AIRMEDIA_IP_ADDRESS_PROMPT,
        AIRMEDIA_DOMAIN_NAME_PROMPT,
        AIRMEDIA_HOST_NAME_PROMPT,
        AIRMEDIA_CUSTOM_PROMPT,
        AIRMEDIA_CUSTOM_PROMPT_STRING,
        AIRMEDIA_DISPLAY_CONNECTION_OPTION_ENABLE,
        AIRMEDIA_DISPLAY_CONNECTION_OPTION,
        AIRMEDIA_DISPLAY_SCREEN,
        AIRMEDIA_WINDOW_FLAG,
        AIRMEDIA_MIRACAST_WIFI_DIRECT_MODE_ENABLE,
        AIRMEDIA_VERSION,
        AIRMEDIA_DEBUG,
        AIRMEDIA_PROCESS_DEBUG_MESSAGE,
        AIRMEDIA_CLEAR_CACHE,
        
        // Camera Streaming Slot
        CAMERA_STREAMING_ENABLE,
        CAMERA_STREAMING_MULTICAST_ENABLE,
        CAMERA_STREAMING_RESOLUTION,
        CAMERA_STREAMING_STREAM_URL,
        CAMERA_STREAMING_SNAPSHOT_URL,
        CAMERA_STREAMING_STREAM_NAME,
        CAMERA_STREAMING_SNAPSHOT_NAME,
        CAMERA_STREAMING_MULTICAST_ADDRESS,

        WBS_STREAMING_STREAM_URL,
        
        RESTART_STREAM_ON_START,

        APPSPACE_IS_ENABLED,
        USE_GSTREAMER,
        NEW_SINK,
        NEW_IPADDR,
        FDEBUG_JNI,
        RESET_ALL_WINDOWS,
        WBSLOGLEVEL,
        WFDSTREAM,
        FORCE_RGB_PREVIEW_MODE,
        CHROMAKEY_COLOR,
    	LOGLEVEL;
    	//UPDATEREQUEST;
    }

    public  CommandParser(CresStreamCtrl a_crestctrl){
        tokenizer = new StringTokenizer();
        invoke = new CommandInvoker();
        ctrl = a_crestctrl;
        cmdHashMap = new HashMap<CmdTable, CommandIf>();	//update request will fill in hashmap values
    }
    
    public String validateReceivedMessage(String read){
        StringBuilder sb = new StringBuilder(4096);
        sb.append("DEBUG_MODE : Removes socket timeout\r\n");
        sb.append("MODE (= 0:STREAMIN 1: STREAMOUT 2:HDMIPREVIEW)\r\n");
        sb.append("SERVICEMODE (= 0:MASTER 1:SLAVE)\r\n");
        sb.append("SessionInitiation (= 0: ByReceiver 1: ByTransmitter 2: MCastviaRTSP 3: MCastviaUDP)\r\n");
        sb.append("TRANSPORTMODE (= 0: RTP 1: TS_RTP 2: TS_UDP)\r\n");
        sb.append("VENCPROFILE (= 0:HighProfile 1:MainProfile 2:BaseProfile)\r\n");
        sb.append("STREAMURL(= any url) \r\n");
        sb.append("PROXYENABLE(=true)\r\n");
        sb.append("INTERNAL_RTSPPORT(= 1024 to 49151)\r\n");
        sb.append("RTSPPORT(= 1024 to 49151)\r\n");
        sb.append("TSPORT (= 1024 to 49151)\r\n");
        sb.append("RTPVIDEOPORT (= 1024 to 49151)\r\n");
        sb.append("RTPAUDIOPORT (= 1024 to 49151)\r\n");
        sb.append("VFRAMERATE (= 60 50 30 24)\r\n");
        sb.append("VBITRATE (= 96 to 25000kbps)\r\n");
        sb.append("TCPINTERLEAVE (=0:auto 1:tcp 2:udp for RTSP Streaming In)\r\n");
        sb.append("MULTICAST_ADDRESS(=xxx.xxx.xxx.xxx)\r\n");
        sb.append("ENCODING_RESOLUTION(=0 to 17)\r\n");
        sb.append("AUDIO_VOLUME(=0 to 100)\r\n");
        sb.append("AUDIO_MUTE(=1:true/0:false)\r\n");
        sb.append("AUDIO_UNMUTE(=1:true/0:false)\r\n");
        sb.append("PROCESS_HDMI_IN_AUDIO(=1:true/0:false)\r\n");
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
        sb.append("HDMIIN_AUDIO_SAMPLE_RATE\r\n");
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
        sb.append("Z = (z order, higher z on top)\r\n");
        sb.append("WINDOW_DIMENSION = (X,Y,Width,Height)\r\n");
        
        sb.append("STATISTICS_ENABLE (=true)\r\n");
        sb.append("STATISTICS_DISABLE (=true)\r\n");
        sb.append("NEW_SINK(=true)\r\n");
        sb.append("STREAMOUT_RTSP_STREAM_FILENAME(=)\r\n");
        sb.append("STREAMOUT_RTSP_SESSION_NAME(=)\r\n");
        sb.append("STREAMOUT_KEY_FRAME_INTERVAL(=)\r\n");

        sb.append("UPDATEREQUEST\r\nType COMMAND for Query |streamstate to know status\r\n");
        
        return (sb.toString());
    }

    private String processReplyFbMessage(String msg1, String msg2){
        StringBuilder sb = new StringBuilder(1024);
        sb.append(msg1).append("=").append(msg2);
        return (sb.toString());
    }
    
    CommandIf ProcCommand (String msg, String arg, int idx){
    	CmdTable temp = CmdTable.valueOf(msg);	//check if command is in hashMap
    	if (cmdHashMap.containsKey(temp))		//if so, return value
    	{
    		CrestronCommand cmd = (CrestronCommand)cmdHashMap.get(temp);
    		if (cmd != null)
    		{
    			cmd.setVars(arg, idx);				// Make sure to set new value and stream ID
    			return cmd;
    		}
    	}
    	
    	CommandIf cmd = null;
    	cmd = ProcCommandSwitchTable(msg, arg, idx);		//if not in hashMap, use switch table to add to hashmap
    	cmdHashMap.put(temp, cmd);			
    	return cmd;								
    }

    // TODO: These joins should be organized by slots
    CommandIf ProcCommandSwitchTable (String msg, String arg, int idx){
        CommandIf cmd = null;
        switch(CmdTable.valueOf(msg)){
            case MODE:
                cmd = new DeviceCommand(ctrl, arg, idx); 
                break;
            case SERVICEMODE:
                cmd = new ServiceModeCommand(ctrl, arg); 
                break;
            case SESSIONINITIATION:
                cmd = new SessionInitiationCommand(ctrl, arg, idx); 
                break;
            case TRANSPORTMODE:
                cmd = new TModeCommand(ctrl, arg, idx); 
                break;
            case VENCPROFILE:
                cmd = new VencCommand(ctrl, arg, idx); 
                break;
            case RTSPPORT:
                cmd = new RtspPortCommand(ctrl, arg, idx); 
                break;
            case TSPORT:
                cmd = new TsPortCommand(ctrl, arg, idx); 
                break;
            case RTPVIDEOPORT:
                cmd = new RtpVCommand(ctrl, arg, idx); 
                break;
            case RTPAUDIOPORT:
                cmd = new RtpACommand(ctrl, arg, idx); 
                break;
            case VFRAMERATE:
                cmd = new VfrCommand(ctrl, arg, idx); 
                break;
            case VBITRATE:
                cmd = new VbrCommand(ctrl, arg, idx); 
                break;
            case TCPINTERLEAVE:
                cmd = new TcpInterleaveCommand(ctrl, arg, idx); 
                break;
            case MULTICAST_ADDRESS:
                cmd = new MulticastIpaddrCommand(ctrl, arg, idx); 
                break;
            case ENCODING_RESOLUTION:
                cmd = new EncodingResolutionCommand(ctrl, arg, idx); 
                break;
            case AUDIO_VOLUME:
                cmd = new SetVolumeCommand(ctrl, arg); 
                break;
            case AUDIO_MUTE:
                cmd = new MuteCommand(ctrl, arg); 
                break;
            case AUDIO_UNMUTE:
                cmd = new UnmuteCommand(ctrl, arg); 
                break;
            case PROCESS_HDMI_IN_AUDIO:
                cmd = new HdmiInAudioCommand(ctrl, arg); 
                break;
            case AUTOMATIC_INITIATION_MODE:
                cmd = new AutomaticInitiationModeCommand(ctrl, arg); 
                break;
            case LATENCY:
                cmd = new LatencyCommand(ctrl, arg, idx); 
                break;
            case PASSWORD_ENABLE:
                cmd = new PasswdEnableCommand(ctrl, arg, idx); 
                break;
            case PASSWORD_DISABLE:
                cmd = new PasswdDisableCommand(ctrl, arg, idx); 
                break;
            case USERNAME:
                cmd = new UserCommand(ctrl, arg, idx); 
                break;
            case PASSWORD:
                cmd = new PasswdCommand(ctrl, arg, idx); 
                break;
            case STREAMURL:
                cmd = new StreamUrlCommand(ctrl, arg, idx); 
                break;
            case PROXYENABLE:
                cmd = new ProxyEnableCommand(ctrl, arg, idx); 
                break;
            case DECODEINTERNALRTSPPORT:
            	cmd = new DecodeInternalRtspPortCommand(ctrl, arg, idx); 
                break;
            case HDCPENCRYPT:
                cmd = new HdcpEncryptCommand(ctrl, arg, idx); 
                break;
            case TXHDCPACTIVE:
                cmd = new TxHdcpActiveCommand(ctrl, arg, idx); 
                break;
            case INTERNAL_RTSPPORT:
                cmd = new InternalRtspPortCommand(ctrl, arg, idx); 
                break;
            case START:
                cmd = new StartCommand(ctrl, arg, idx); 
                break;
            case STOP:
                cmd = new StopCommand(ctrl, arg, idx, false); //normal stop
                break;
            case STOPFULL:
            	cmd = new StopCommand(ctrl, arg, idx, true); //full stop means do not start confidence preview on stop
            	break;
            case PAUSE:
                cmd = new PauseCommand(ctrl, arg, idx); 
                break;
            case STRETCHVIDEO:
            	cmd = new StretchVideoCommand(ctrl, arg, idx);
            	break;
            case XLOC:
                cmd = new XlocCommand(ctrl, arg, idx); 
                break;
            case YLOC:
                cmd = new YlocCommand(ctrl, arg, idx); 
                break;
            case W: 
                cmd = new DestWidthCommand(ctrl, arg, idx); 
                break;
            case H:
                cmd = new DestHeightCommand(ctrl, arg, idx); 
                break;
            case Z:
            	cmd = new DestZOrderCommand(ctrl, arg, idx);
            	break;
            case WINDOW_DIMENSION:
            	cmd = new WindowDimensionCommand(ctrl, arg, idx);;
            	break;
            case HDMI_OUT_EXTERNAL_HDCP_STATUS:
            	cmd = new ExternalHdcpStatusCommand(ctrl, arg);
            	break;
            case HDMIIN_SYNC_DETECTED:
                cmd = new InSyncCommand(ctrl, arg); 
                break;
            case HDMIIN_INTERLACED:
                cmd = new InInterlaceCommand(ctrl, arg); 
                break;
            case HDMIIN_CEC_ERROR:
                cmd = new InCecCommand(ctrl, arg); 
                break;
            case HDMIIN_HORIZONTAL_RES_FB:
                cmd = new InHresCommand(ctrl, arg); 
                break;
            case HDMIIN_VERTICAL_RES_FB:
                cmd = new InVResCommand(ctrl, arg); 
                break;
            case HDMIIN_FPS_FB:
                cmd = new InFpsCommand(ctrl, arg); 
                break;
            case HDMIIN_ASPECT_RATIO:
                cmd = new InAspectCommand(ctrl, arg); 
                break;
            case HDMIIN_AUDIO_FORMAT:
                cmd = new InAudioFormatCommand(ctrl, arg); 
                break;
            case HDMIIN_AUDIO_CHANNELS:
                cmd = new InAudioChannelsCommand(ctrl, arg); 
                break;
            case HDMIIN_AUDIO_SAMPLE_RATE:
                cmd = new InAudioSampleRateCommand(ctrl, arg); 
                break;
            case HDMIIN_TRANSMIT_CEC_MESSAGE:
                cmd = new InTxCecCommand(ctrl, arg); 
                break;
            case HDMIIN_RECEIVE_CEC_MESSAGE:
                cmd = new InRxCecCommand(ctrl, arg); 
                break;
                //HDMI OUT
            case HDMIOUT_FORCE_HDCP:
            	cmd = new OutForceHdcp(ctrl, arg);
            	break;
            case HDMIOUT_SYNC_DETECTED:
                cmd = new OutSyncCommand(ctrl, arg); 
                break;
            case HDMIOUT_INTERLACED:
                cmd = new OutInterlaceCommand(ctrl, arg); 
                break;
            case HDMIOUT_CEC_ERROR:
                cmd = new OutCecCommand(ctrl, arg); 
                break;
            case HDMIOUT_HORIZONTAL_RES_FB:
                cmd = new OutHresCommand(ctrl, arg); 
                break;
            case HDMIOUT_VERTICAL_RES_FB:
                cmd = new OutVresCommand(ctrl, arg); 
                break;
            case HDMIOUT_FPS_FB:
                cmd = new outFpsCommand(ctrl, arg); 
                break;
            case HDMIOUT_ASPECT_RATIO:
                cmd = new OutAspectCommand(ctrl, arg); 
                break;
            case HDMIOUT_AUDIO_FORMAT:
                cmd = new OutAudioFormatCommand(ctrl, arg); 
                break;
            case HDMIOUT_AUDIO_CHANNELS:
                cmd = new OutAudioChannelsCommand(ctrl, arg); 
                break;
            case HDMIOUT_TRANSMIT_CEC_MESSAGE:
                cmd = new OutTxCecCommand(ctrl, arg); 
                break;
            case HDMIOUT_RECEIVE_CEC_MESSAGE:
                cmd = new OutRxCecCommand(ctrl, arg); 
                break;
            case HDMI_SENDHDCPFB:
            	cmd = new HdcpFeedbackCommand(ctrl, arg);
            	break;
            //STREAM IN
            case STREAMIN_HORIZONTAL_RES_FB:
            	cmd = new StreamInHresCommand(ctrl, arg); 
                break; 
            case STREAMIN_VERTICAL_RES_FB:
            	cmd = new StreamInVresCommand(ctrl, arg); 
                break; 
            case STREAMIN_FPS_FB:
            	cmd = new StreamInFpsCommand(ctrl, arg); 
                break;
            case STREAMIN_ASPECT_RATIO:
            	cmd = new StreamInAspectCommand(ctrl, arg); 
                break;   
            case STREAMIN_AUDIO_FORMAT:
            	cmd = new StreamInAudioFormatCommand(ctrl, arg); 
                break;
            case STREAMIN_AUDIO_CHANNELS:
            	cmd = new StreamInAudioChannelsCommand(ctrl, arg); 
                break;   
            //STREAM OUT
            case STREAMOUT_HORIZONTAL_RES_FB:
        		cmd = new StreamOutHresCommand(ctrl, arg); 
                break; 
            case STREAMOUT_VERTICAL_RES_FB:
        		cmd = new StreamOutVresCommand(ctrl, arg); 
                break; 
            case STREAMOUT_FPS_FB:
            	cmd = new StreamOutFpsCommand(ctrl, arg); 
                break;
            case STREAMOUT_ASPECT_RATIO:
            	cmd = new StreamOutAspectCommand(ctrl, arg); 
                break;   
            case STREAMOUT_AUDIO_FORMAT:
            	cmd = new StreamOutAudioFormatCommand(ctrl, arg); 
                break;
            case STREAMOUT_AUDIO_CHANNELS:
            	cmd = new StreamOutAudioChannelsCommand(ctrl, arg); 
                break; 
            case STREAMOUT_RTSP_STREAM_FILENAME:
            	cmd = new StreamOutRtspStreamFileName(ctrl, arg); 
                break; 
            case STREAMOUT_RTSP_SESSION_NAME:
            	cmd = new StreamOutRtspSessionName(ctrl, arg); 
                break; 
            case STREAMOUT_KEY_FRAME_INTERVAL:
            	cmd = new StreamOutKeyFrameInterval(ctrl, arg);
            	break;
            //STREAMING
            case PROCESSING_FB:
                cmd = new ProcessingCommand(ctrl, arg); 
                break;
            case DEVICE_READY_FB:
                cmd = new DeviceReadyCommand(ctrl, arg); 
                break;
            case ELAPSED_SECONDS_FB:
                cmd = new ElapsedSecondsCommand(ctrl, arg); 
                break;
//            case STREAM_STATUS_FB:
//                cmd = new StatusCommand(ctrl, arg); 
//                break;
            case INITIATOR_ADDRESS_FB:
                cmd = new InitAddressCommand(ctrl, arg); 
                break;
                //STATUS  
            case STREAMSTATE:
                cmd = new StreamStateCommand(ctrl, arg, idx); 
                break;
            //Ethernet
            case STATISTICS_ENABLE:
            	cmd = new StatisticsEnableCommand(ctrl, arg, idx);
            	break;
            case STATISTICS_DISABLE:
            	cmd = new StatisticsDisableCommand(ctrl, arg, idx);
            	break;
            case STATISTICS_RESET:
            	cmd = new StatisticsResetCommand(ctrl, arg, idx);
            	break;
            case STATISTICS_NUMBEROFVIDEOPACKETS:
            	cmd = new StatisticsNumVideoPacketsCommand(ctrl, arg);
            	break;
            case STATISTICS_NUMBEROFVIDEOPACKETSDROPPED:
            	cmd = new StatisticsNumVideoPacketsDroppedCommand(ctrl, arg);
            	break;
            case STATISTICS_NUMBEROFAUDIOPACKETS:
            	cmd = new StatisticsNumAudioPacketsCommand(ctrl, arg);
            	break;
            case STATISTICS_NUMBEROFAUDIOPACKETSDROPPED:
            	cmd = new StatisticsNumAudioPacketsDroppedCommand(ctrl, arg);
            	break;
            case MULTICASTTTL:
            	cmd = new SetMulticastTTLCommand(ctrl, arg);
            	break;
		//OSD
            case OSD_ENABLE:
            	cmd = new OsdEnableCommand(ctrl, arg);
            	break;
            case OSD_DISABLE:
            	cmd = new OsdDisableCommand(ctrl, arg);
            	break;
            case OSD_TEXT:
            	cmd = new OsdTextCommand(ctrl, arg);
            	break;
            case OSD_LOCATION:
            	cmd = new OsdLocationCommand(ctrl, arg);
            	break;
            case OSD_X:
            	cmd = new OsdXPosCommand(ctrl, arg);
            	break;
            case OSD_Y:
            	cmd = new OsdYPosCommand(ctrl, arg);
            	break;
    	// AirMedia
            case AIRMEDIA_LAUNCH:            
            	cmd = new AirMediaLaunchCommand(ctrl, arg, idx);
            	break;
            case AIRMEDIA_WINDOW_LAUNCH:
            	cmd = new AirMediaWindowLaunchCommand(ctrl, arg, idx);
            	break;
            case AIRMEDIA_ENABLE:
            	cmd = new AirMediaEnableCommand(ctrl, arg, idx);
            	break;
            case AIRMEDIA_LOGIN_CODE:
            	cmd = new AirMediaLoginCodeCommand(ctrl, arg, idx);
            	break;
            case AIRMEDIA_LOGIN_MODE:
            	cmd = new AirMediaLoginModeCommand(ctrl, arg, idx);
            	break;
            case AIRMEDIA_MODERATOR:
            	cmd = new AirMediaModeratorCommand(ctrl, arg, idx);
            	break;
            case AIRMEDIA_RESET_CONNECTIONS:
            	cmd = new AirMediaResetConnectionsCommand(ctrl, arg, idx);
            	break;
            case AIRMEDIA_DISCONNECT_USER:
            	cmd = new AirMediaDisconnectUserCommand(ctrl, arg, idx);
            	break;
            case AIRMEDIA_START_USER:
            	cmd = new AirMediaStartUserCommand(ctrl, arg, idx);
            	break;
            case AIRMEDIA_USER_POSITION:
            	cmd = new AirMediaUserPositionCommand(ctrl, arg, idx);
            	break;
            case AIRMEDIA_STOP_USER:
            	cmd = new AirMediaStopUserCommand(ctrl, arg, idx);
            	break;
            case AIRMEDIA_OSD_IMAGE:
            	cmd = new AirMediaOsdImageCommand(ctrl, arg, idx);
            	break;
            case AIRMEDIA_IP_ADDRESS_PROMPT:
            	cmd = new AirMediaIpAddressPromptCommand(ctrl, arg, idx);
            	break;
            case AIRMEDIA_DOMAIN_NAME_PROMPT:
            	cmd = new AirMediaDomainNamePromptCommand(ctrl, arg, idx);
            	break;
            case AIRMEDIA_HOST_NAME_PROMPT:
            	cmd = new AirMediaHostNamePromptCommand(ctrl, arg, idx);
            	break;  
            case AIRMEDIA_CUSTOM_PROMPT:
            	cmd = new AirMediaCustomPromptCommand(ctrl, arg, idx);
            	break;  
            case AIRMEDIA_CUSTOM_PROMPT_STRING:
            	cmd = new AirMediaCustomPromptStringCommand(ctrl, arg, idx);
            	break; 
            case AIRMEDIA_DISPLAY_CONNECTION_OPTION_ENABLE:
            	cmd = new AirMediaDisplayConnectionOptionEnableCommand(ctrl, arg, idx);
            	break;
            case AIRMEDIA_DISPLAY_CONNECTION_OPTION:
            	cmd = new AirMediaDisplayConnectionOptionCommand(ctrl, arg, idx);
            	break;  
            case AIRMEDIA_WINDOW_POSITION:
            	cmd = new AirMediaWindowPositionCommand(ctrl, arg, idx);
            	break;
            case AIRMEDIA_WINDOW_X_OFFSET:
            	cmd = new AirMediaWindowXOffsetCommand(ctrl, arg, idx);
            	break;
            case AIRMEDIA_WINDOW_Y_OFFSET:
            	cmd = new AirMediaWindowYOffsetCommand(ctrl, arg, idx);
            	break;
            case AIRMEDIA_WINDOW_WIDTH:
            	cmd = new AirMediaWindowWidthCommand(ctrl, arg, idx);
            	break;
            case AIRMEDIA_WINDOW_HEIGHT:
            	cmd = new AirMediaWindowHeightCommand(ctrl, arg, idx);
            	break;
            case AIRMEDIA_APPLY_LAYOUT_PASSWORD:
            	cmd = new AirMediaApplyLayoutPasswordCommand(ctrl, arg, idx);
            	break;
            case AIRMEDIA_LAYOUT_PASSWORD:
            	cmd = new AirMediaLayoutPasswordCommand(ctrl, arg, idx);
            	break;
            case AIRMEDIA_APPLY_OSD_IMAGE:
            	cmd = new AirMediaApplyOsdImageCommand(ctrl, arg, idx);
            	break;
            case AIRMEDIA_DISPLAY_LOGIN_CODE:
            	cmd = new AirMediaDisplayLoginCodeCommand(ctrl, arg, idx);
            	break;
            case AIRMEDIA_DISPLAY_SCREEN:
            	cmd = new AirMediaDisplayScreenCommand(ctrl, arg, idx);
            	break;
            case AIRMEDIA_WINDOW_FLAG:
            	cmd = new AirMediaWindowFlagCommand(ctrl, arg, idx);
            	break;
            case AIRMEDIA_MIRACAST_WIFI_DIRECT_MODE_ENABLE:
            	cmd = new AirMediaMiracastWifiDirectModeEnableCommand(ctrl, arg, idx);
            	break;
            case AIRMEDIA_VERSION:
            	cmd = new AirMediaVersionCommand(ctrl, arg, idx);
            	break;
            case AIRMEDIA_DEBUG:
            	cmd = new AirMediaDebugCommand(ctrl, arg, idx);
            	break;
            case AIRMEDIA_PROCESS_DEBUG_MESSAGE:
            	cmd = new AirMediaProcessDebugMessageCommand(ctrl, arg);
            	break;
            case AIRMEDIA_ADAPTOR_SELECT:
            	cmd = new AirMediaAdaptorSelectCommand(ctrl, arg, idx);            	
            	break;
            case AIRMEDIA_CONNECTION_ADDRESS:
            	cmd = new AirMediaConnectionAddressCommand(ctrl, arg, idx);
            	break;
            case AIRMEDIA_RESTART:
            	cmd = new AirMediaRestartCommand(ctrl, arg, idx);
            	break;
            case AIRMEDIA_PROJECTION_LOCK:
            	cmd = new AirMediaProjectionLockCommand(ctrl, arg, idx);
            	break;
            case AIRMEDIA_CLEAR_CACHE:
            	cmd = new AirMediaClearCacheCommand(ctrl, arg);
            	break;
                        	
        	// Camera Streaming
            case CAMERA_STREAMING_ENABLE:
            	cmd = new camStreamEnableCommand(ctrl, arg);
            	break;            	
            case CAMERA_STREAMING_MULTICAST_ENABLE:
            	cmd = new camStreamMulticastEnableCommand(ctrl, arg);
            	break;
            case CAMERA_STREAMING_RESOLUTION:
            	cmd = new camStreamResolutionCommand(ctrl, arg);
            	break;
            case CAMERA_STREAMING_STREAM_URL:
            	cmd = new camStreamUrlCommand(ctrl, arg);
            	break;
            case CAMERA_STREAMING_SNAPSHOT_URL:
            	cmd = new camStreamSnapshotUrlCommand(ctrl, arg);
            	break;
            case CAMERA_STREAMING_STREAM_NAME:
            	cmd = new camStreamNameCommand(ctrl, arg);
            	break;
            case CAMERA_STREAMING_SNAPSHOT_NAME:
            	cmd = new camStreamSnapshotNameCommand(ctrl, arg);
            	break;
            case CAMERA_STREAMING_MULTICAST_ADDRESS:
            	cmd = new camStreamMulticastAddressCommand(ctrl, arg);
            	break;
            	
            	// Whiteboard Streaming
            case WBS_STREAMING_STREAM_URL:
            	cmd = new wbsStreamUrlCommand(ctrl, arg, idx);
            	break;
            	
            case APPSPACE_IS_ENABLED:
            	cmd = new AppspaceIsEnabledCommand(ctrl, arg);
            	break;
            case RESTART_STREAM_ON_START:
            	cmd = new RestartStreamOnStartCommand(ctrl, arg);
            	break;
            case USE_GSTREAMER:
            	cmd = new UseGstreamerCommand(ctrl, arg);
            	break;
            case NEW_SINK:
            	cmd = new UseNewSinkCommand(ctrl, arg, idx);
            	break;
            case NEW_IPADDR:
            	cmd = new UseNewIpAddrCommand(ctrl, arg);
            	break;
            case AUXILIARY_IP_ADDRESS:
            	cmd = new SetAuxiliaryIpAddressCommand(ctrl, arg);
            	break;
            case FDEBUG_JNI:            	
            	cmd = new FIELDDEBUGJNICommand(ctrl, arg, idx);
            	break;
            case RESET_ALL_WINDOWS:
            	cmd = new ResetAllWindowsCommand(ctrl, arg);
            	break;
            case WBSLOGLEVEL:
            	cmd = new SetWbsLogLevelCommand(ctrl, arg);
            	break;
            case WFDSTREAM:
            	cmd = new WfdStreamCommand(ctrl, arg, idx);
            	break;
            case FORCE_RGB_PREVIEW_MODE:
            	cmd = new ForceRgbPreviewModeCommand(ctrl, arg);
            	break;
            case CHROMAKEY_COLOR:
            	cmd = new ChromaKeyColorCommand(ctrl, arg);
            	break;
            case LOGLEVEL:
            	cmd = new SetLogLevelCommand(ctrl, arg);
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
        String reply = ""; 
        StringTokenizer.ParseResponse parseResponse = tokenizer.Parse(receivedMsg);
//        Log.i(TAG, "sessId parsed "+ parseResponse.sessId);
        
        if (parseResponse.sessId >= CresStreamCtrl.NumOfSurfaces)
        {
        	return MiscUtils.stringFormat("Invalid Session id: %d", parseResponse.sessId);
        }

        if (validateMsg(parseResponse.joinName.toUpperCase()))
		{
        	String joinNameWithSessId = (parseResponse.joinName + String.valueOf(parseResponse.sessId));
        	String valueResponse = "";
        	
	        if(parseResponse.joinValue != null)
	        { //Process & Reply Feedback 
	            cmd = ProcCommand(parseResponse.joinName.toUpperCase(), parseResponse.joinValue, parseResponse.sessId); 
	            if (cmd != null)
	            {
	                invoke.setCommand(cmd);
	                invoke.setFbMsg(parseResponse.joinValue);
	                invoke.set();
	                
	                // Don't save userSettings for these messages since they are not saved in userSettings
	                switch(CmdTable.valueOf(parseResponse.joinName.toUpperCase())){
	                case TXHDCPACTIVE:
	                case PROXYENABLE:
	                case HDCPENCRYPT:
	                case HDMI_OUT_EXTERNAL_HDCP_STATUS:
	                case HDMIOUT_TRANSMIT_CEC_MESSAGE:
	                	break;
	                default:
	                	CresStreamCtrl.saveSettingsUpdateArrived = true;
	                	break;
	                }           
	            }
	            
	            valueResponse = invoke.getSetFb();
	        }
	        else
	        {
                cmd = ProcCommand(parseResponse.joinName.toUpperCase(), "", parseResponse.sessId); 
                if (cmd != null)
                {
                    invoke.setCommand(cmd);
                    valueResponse = invoke.get();
                    Log.i(TAG, MiscUtils.stringFormat("Join %s value response: %s", parseResponse.joinName.toUpperCase(), valueResponse));
                }
	        }
	        
	        if (valueResponse != null)
	        {
	        	if (parseResponse.sessIdSpecified)
	        		reply = processReplyFbMessage(joinNameWithSessId, valueResponse);
	        	else
	        		reply = processReplyFbMessage(parseResponse.joinName, valueResponse);
	        }
		}

        return reply;
    }
}
