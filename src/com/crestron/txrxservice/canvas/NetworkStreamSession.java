package com.crestron.txrxservice.canvas;

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.crestron.airmedia.canvas.channels.ipc.CanvasPlatformType;
import com.crestron.airmedia.canvas.channels.ipc.CanvasSurfaceMode;
import com.crestron.airmedia.canvas.channels.ipc.CanvasSurfaceOptions;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSize;
import com.crestron.airmedia.utilities.Common;
import com.crestron.airmedia.utilities.TimeSpan;
import com.crestron.txrxservice.HDMIInputInterface;

import android.os.ConditionVariable;
import android.util.Log;
import android.view.Surface;

public class NetworkStreamSession extends Session
{
    public static final String TAG = "TxRx.canvas.NetworkStream.session";
    private static final HashMap<String, Integer> TransportModeMapping ;
	
    static {
        // posible string: SESSINIT_RTP, SESSINIT_MPEG2TSRTP and SESSINIT_MPEG2TSUDP
        TransportModeMapping = new HashMap<String, Integer>();
        TransportModeMapping.put("SESSINIT_RTP", 0);
        TransportModeMapping.put("SESSINIT_MPEG2TSRTP", 1);
        TransportModeMapping.put("SESSINIT_MPEG2TSUDP", 2);
    }

	public NetworkStreamSession(String label, String networkStreamUrl) {
		super(); // will assign id;
		state = SessionState.Stopped;
		type = SessionType.NetworkStreaming;
		airMediaType = null;
		url = networkStreamUrl;
		userLabel = label;
		platform = CanvasPlatformType.Hardware;		
		Common.Logging.i(TAG, "Created:  "+ this);
	}

	public String toString()
	{
		return ("Session: "+type.toString()+"-"+inputNumber+"  sessionId="+sessionId());
	}
	
	public void doStop()
	{
		Common.Logging.i(TAG, "doStop(): "+this+" stop request");
		if (streamId >= 0)
		{			
			Common.Logging.i(TAG, "doStop(): "+this+" calling Stop()");
			mStreamCtl.Stop(streamId, false);			
			Common.Logging.i(TAG, "doStop(): "+this+" back from Stop()");
			releaseSurface();
		}
	}
	
	public void stop(final Originator originator, int timeoutInSeconds)
	{
		Runnable r = new Runnable() { public void run() { doStop(); } };
        TimeSpan start = TimeSpan.now();
		boolean completed = executeWithTimeout(r, TimeSpan.fromSeconds(timeoutInSeconds));
		Common.Logging.i(TAG, "stop():  completed in "+TimeSpan.now().subtract(start).toString()+" seconds");
		if (completed) {
			streamId = -1;
			setState(SessionState.Stopped);
		} else {
			Common.Logging.w(TAG, "stop(): "+this+" stop failed");		
			originator.failedSessionList.add(this);
		}
	}
	
	public void stop(Originator originator)
	{
		stop(originator, 10);
	}	

	public void doPlay(final Originator originator)
	{		
		Common.Logging.i(TAG, "doPlay(): "+this+" play request");
		setStreamId();
		Common.Logging.i(TAG, "doPlay(): "+this+" got streamId "+streamId);
		if (acquireSurface() != null)
		{
			// signal to csio to set device mode for this streamId to STREAMIN
			mStreamCtl.setDeviceMode(0, streamId);
			mStreamCtl.userSettings.setProxyEnable(false,streamId);			

			Common.Logging.i(TAG, "doPlay(): url: " + url);
			
			//Need to parse url for SessionInitiation and TRANSPORTMODE
			if(url != null)
			{
				String delims = "[?]";
				String[] tokens = url.split(delims);

				//Note: the first section is the real url
				mStreamCtl.setStreamInUrl(tokens[0], streamId);

				//set TRANSPORTMODE here
				int transPortMode = findTransportMode(tokens);                
				Common.Logging.i(TAG, "doPlay():  calling setTMode: " + transPortMode);
				mStreamCtl.setTMode(transPortMode,streamId);			

				//set SessionInitiation here
				int sessInitiation = findSessionInitiation(tokens,transPortMode);				
				Common.Logging.i(TAG, "doPlay():  calling setSessionInitiation: " + sessInitiation);
				mStreamCtl.setSessionInitiation(sessInitiation,streamId);		

				Common.Logging.i(TAG, "doPlay():  "+this+" calling Start()");
				mStreamCtl.Start(streamId);
				
				audioMute(isAudioMuted);
				Common.Logging.i(TAG, "doPlay():  "+this+" back from audioMute(): " + isAudioMuted);		
			}
			else{
				Common.Logging.w(TAG, "doPlay(): "+this+" got null url");
			}

		} else {
		    Common.Logging.w(TAG, "doPlay(): "+this+" got null surface");
		    originator.failedSessionList.add(this);

                    //TODO: find out who is monitoring for failed session,
                    //      how to restart this failed session?
		}
	}
	
	public void play(final Originator originator, int timeoutInSeconds)
	{
		playTimedout = false;
		setState(SessionState.Starting);
		Runnable r = new Runnable() { public void run() { doPlay(originator); } };
        TimeSpan start = TimeSpan.now();
		boolean completed = executeWithTimeout(r, TimeSpan.fromSeconds(timeoutInSeconds));
		Common.Logging.i(TAG, "play(): completed in "+TimeSpan.now().subtract(start).toString()+" seconds");
		if (completed)
		{
			setState(SessionState.Playing);
		}
		else
		{
			Common.Logging.w(TAG, "play(): "+this+" play failed - timeout");
			playTimedout = true;
			originator.failedSessionList.add(this);
		}
	}
	
	public void play(Originator originator)
	{
		play(originator, 10);
	}
	
    public boolean audioMute(boolean enable) {
        setIsAudioMuted(enable);
        return true;
    }

    /*
     * looking for multicast address: [224 - 239].x.x.x. return true if it is
     * multicast address.
     */
    public boolean isValidMulticastAddr(final String url) {
        String Multicast_REGEX = ".*((22[4-9])|(23[0-9]))\\.(?:(([0-1]?[0-9]{1,2})|(2[0-4][0-9])|(25[0-5]))\\.){2}(([0-1]?[0-9]{1,2})|(2[0-4][0-9])|(25[0-5])).*";
        String[] splitUp = url.split("/");
        String ipAddress = splitUp[2];

        Pattern regexPattern = Pattern.compile(Multicast_REGEX);
        if (regexPattern.matcher(ipAddress).matches() == true) {
            Common.Logging.v(TAG, "isValidMulticastAddr(): return true");
            return true;
        } else {
            Common.Logging.v(TAG, "isValidMulticastAddr(): return false");
            return false;
        }
    }

    /*
     * looking for transport mode default mode is 0.
     */
    public int findTransportMode(final String[] tokens) {
        // skip the first token
        for (int i = 1; i < tokens.length; i++) {
            Integer objTrMode = (Integer) TransportModeMapping.get(tokens[i].toUpperCase());
            if (objTrMode != null) {
                Common.Logging.v(TAG, "findTransportMode(): found: " + objTrMode);
                return objTrMode.intValue();// found valid transPortMode, so break here.

            } // else
        }

        Common.Logging.v(TAG, "findTransportMode(): return 0");
        return 0;
    }

    /*    https://crestron.jamacloud.com/perspective.req#/items/5887248?projectId=20916
     *                           RTP(0)                                      MPEG2TSRTP(1)                                   MPEG2TSUDP(2)
     * By Receiver 	        rtsp://<url>:<rtsp_port>/<session_name>     rtsp://<url>:<rtsp_port>/<session_name>     rtsp://<url>:<rtsp_port>/<session_name>
     * By Transmitter 	        No longer supported as it is non-standard   rtp://<tx_ip>:<port> 	                udp://<tx_ip>:<port>
     * Multicast via RTSP 	rtsp://<url>:<rtsp_port>/<session_name>     rtsp://<url>:<rtsp_port>/<session_name>     rtsp://<url>:<rtsp_port>/<session_name>
     * Multicast via UDP 	No longer supported as it is non-standard   rtp://<multicast_ip>:<port> 	        udp://<multicast_ip>:<port>
    */
    public int findSessionInitiation(final String[] tokens, final int transPortMode) {
        int sessInitiation = 0;

        if (transPortMode == 0) {
            // and it is multicast address
            if (isValidMulticastAddr(tokens[0]) == true)
                sessInitiation = 2;
        } else if (transPortMode == 1) {

            if (tokens[0].contains("rtsp://")) {
                if (isValidMulticastAddr(tokens[0]) == true)
                    sessInitiation = 2;
            } else if (tokens[0].contains("rtp://")) {
                sessInitiation = 1;

                if (isValidMulticastAddr(tokens[0]) == true)
                    sessInitiation = 3;
            } // else
        } else if (transPortMode == 2) {
            if (tokens[0].contains("rtsp://")) {
                if (isValidMulticastAddr(tokens[0]) == true)
                    sessInitiation = 2;
            } else if (tokens[0].contains("udp://")) {
                sessInitiation = 1;

                if (isValidMulticastAddr(tokens[0]) == true)
                    sessInitiation = 3;
            } // else
        } // else

        return sessInitiation;
    } 
}
