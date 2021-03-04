package com.crestron.txrxservice.canvas;

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
    //TODO: private NetworkStreamInterface hdmiInput;

	public NetworkStreamSession(String label, String networkStreamUrl) {
		super(); // will assign id;
		state = SessionState.Stopped;
		type = SessionType.NetworkStreaming;
		airMediaType = null;
		url = networkStreamUrl;
		userLabel = label;
		platform = CanvasPlatformType.Hardware;		
		Common.Logging.i(TAG, "NetworkStreamSession created:  "+ this);
	}
	
	//TODO: public void setHdmiInput(HDMIInputInterface h) { hdmiInput = h; }

	public String toString()
	{
		return ("Session: "+type.toString()+"-"+inputNumber+"  sessionId="+sessionId());
	}
	
	public void doStop()
	{
		Common.Logging.i(TAG, "HDMI NetworkStream "+this+" stop request");
		if (streamId >= 0)
		{
			// set device mode for this streamId to Rx
			mStreamCtl.setDeviceMode(0, streamId);			
			Common.Logging.i(TAG, "NetworkStream Session "+this+" calling Stop()");
			mStreamCtl.Stop(streamId, false);
			Common.Logging.i(TAG, "NetworkStream Session "+this+" sending stop to csio for audio on AM-300");
			
			//TODO: sent to csio for handling audio for the stream, check this later.
			//      mStreamCtl.sendHdmiStart(streamId, false);
			Common.Logging.i(TAG, "NetworkStream Session "+this+" back from Stop()");
			releaseSurface();
		}
	}
	
	public void stop(final Originator originator, int timeoutInSeconds)
	{
		Runnable r = new Runnable() { public void run() { doStop(); } };
        TimeSpan start = TimeSpan.now();
		boolean completed = executeWithTimeout(r, TimeSpan.fromSeconds(timeoutInSeconds));
		Common.Logging.i(TAG, "NetworkStream Session stop completed in "+TimeSpan.now().subtract(start).toString()+" seconds");
		if (completed) {
			streamId = -1;
			setState(SessionState.Stopped);
		} else {
			Common.Logging.w(TAG, "NetworkStream Session "+this+" stop failed");		
			originator.failedSessionList.add(this);
		}
	}
	
	public void stop(Originator originator)
	{
		stop(originator, 10);
	}
	
	public void doPlay(final Originator originator)
	{		
		Common.Logging.i(TAG, "NetworkStream Session "+this+" play request");
		setStreamId();
		Common.Logging.i(TAG, "NetworkStream Session "+this+" got streamId "+streamId);
		if (acquireSurface() != null)
		{
			// signal to csio to set device mode for this streamId to STREAMIN
			mStreamCtl.setDeviceMode(0, streamId);
			mStreamCtl.userSettings.setProxyEnable(false,streamId);
			mStreamCtl.setSessionInitiation(0,streamId);//TODO: find out index
			mStreamCtl.setTMode(0,streamId);//TODO: find out index

			//rtsp://10.116.165.113:8554/test?SESSINIT_RTP
			mStreamCtl.setStreamInUrl("rtsp://10.116.165.113:8554/test", streamId);
			

			Common.Logging.i(TAG, "NetworkStream Session "+this+" calling Start()");
			mStreamCtl.Start(streamId);
			Common.Logging.i(TAG, "NetworkStream Session "+this+" calling audioMute isAudioMuted=" + isAudioMuted);
			audioMute(isAudioMuted);
			Common.Logging.i(TAG, "NetworkStream Session "+this+" back from audioMute()");			
			
		} else {
			Common.Logging.w(TAG, "NetworkStream Session "+this+" doPlay() got null surface");
			originator.failedSessionList.add(this);
		}
	}
	
	public void play(final Originator originator, int timeoutInSeconds)
	{
		playTimedout = false;
		setState(SessionState.Starting);
		Runnable r = new Runnable() { public void run() { doPlay(originator); } };
        TimeSpan start = TimeSpan.now();
		boolean completed = executeWithTimeout(r, TimeSpan.fromSeconds(timeoutInSeconds));
		Common.Logging.i(TAG, "NetworkStream Session play completed in "+TimeSpan.now().subtract(start).toString()+" seconds");
		if (completed)
		{
			setState(SessionState.Playing);
			setResolution();
		}
		else
		{
			Common.Logging.w(TAG, "NetworkStream Session "+this+" play failed - timeout");
			playTimedout = true;
			originator.failedSessionList.add(this);
		}
	}
	
	public void play(Originator originator)
	{
		play(originator, 10);
	}
	
	public void setResolution()
	{
	    /*TODO:  This should be the decoded resolution not preview resolution.
	     * Need to bubble it up thrrough jni once we have it and 
	     * then call this function from CSS
	    
		AirMediaSize res = mStreamCtl.getPreviewResolution();
		super.setResolution(res);*/
	}
	
	public boolean audioMute(boolean enable)
	{
		if (enable)
		{
			Common.Logging.i(TAG, "NetworkStream audioMute(): Session "+this+" calling streamMute()");
			mCanvas.mStreamCtl.setPreviewVolume(0);
			
			/*TODO: Don't know if this is right call for audio muting of NetworkStream
			mStreamCtl.sendExternalHdmiMute(streamId, true);*/
		} else {
			Common.Logging.i(TAG, "NetworkStream audioMute(): Session "+this+" calling streamUnMute()");
			mCanvas.mStreamCtl.setPreviewVolume((int)mCanvas.mStreamCtl.userSettings.getUserRequestedVolume());
			
			/*TODO: Don't know if this is right call for audio muting of NetworkStream
			mStreamCtl.sendExternalHdmiMute(streamId, false);*/
		}
		setIsAudioMuted(enable);
		return true;
	}
}
