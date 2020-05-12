package com.crestron.txrxservice.canvas;

import com.crestron.airmedia.canvas.channels.ipc.CanvasPlatformType;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSize;
import com.crestron.airmedia.utilities.Common;
import com.crestron.airmedia.utilities.TimeSpan;

import android.util.Log;

public class AirBoardSession extends Session
{
    public static final String TAG = "TxRx.canvas.airboard.session"; 

	public AirBoardSession(String airBoardUrl, String label) {
		super(); // will assign id;
		state = SessionState.Stopped;
		type = SessionType.AirBoard;
		airMediaType = null;
		userLabel = label;
		url = airBoardUrl;
		platform = CanvasPlatformType.Hardware;
	}
	
	public void setState(SessionState newState)
	{
		SessionState oldState = getState();
		if (oldState != newState)
		{
			Log.i(TAG, "Changing state from "+oldState+" to "+newState);
			super.setState(newState);
			sendAirBoardSessionFeedback(oldState);
		}
	}
	
	public void sendAirBoardSessionFeedback(SessionState oldState)
	{
		switch (state)
		{
		case Stopped:
			if (oldState != SessionState.Stopping)
				getCanvas().getCrestore().sendSessionFeedbackMessage(type, state, userLabel, 0);
			break;
		case Playing:
			if (oldState != SessionState.Starting && oldState != SessionState.Playing && 
			oldState != SessionState.Pausing && oldState != SessionState.Paused && oldState != SessionState.UnPausing)
				getCanvas().getCrestore().sendSessionFeedbackMessage(type, state, userLabel, 0);
			break;
		case Connecting:
		case Stopping:
		case Starting:
		case Disconnecting:
			getCanvas().getCrestore().sendSessionFeedbackMessage(type, state, userLabel, 0);
			break;
		}
	}
	
	public String toString()
	{
		return ("Session: "+type.toString()+"-"+url+"  sessionId="+sessionId());
	}
	
	public void doStop(boolean replace)
	{
		Common.Logging.i(TAG, "AirBoard Session "+this+" stop request");
		if (streamId >= 0)
		{
			// set device mode for this streamId to preview
			mStreamCtl.setDeviceMode(4, streamId);
			//start the preview mode
			Common.Logging.i(TAG, "AirBoard Session "+this+" calling Stop()");
			mStreamCtl.Stop(streamId, false);
			Common.Logging.i(TAG, "AirBoard Session "+this+" back from Stop()");
			if (!replace)
			{
				mCanvas.hideWindow(streamId); // TODO remove once real canvas app available
				releaseSurface();
			}
		}
	}
	
	public void stop(final Originator originator, final boolean replace, int timeoutInSeconds)
	{
		Runnable r = new Runnable() { public void run() { doStop(replace); } };
        TimeSpan start = TimeSpan.now();
		boolean completed = executeWithTimeout(r, TimeSpan.fromSeconds(timeoutInSeconds));
		Common.Logging.i(TAG, "AirBoard Session stop completed in "+TimeSpan.now().subtract(start).toString()+" seconds");
		if (completed) {
			streamId = -1;
			setState(SessionState.Stopped);
		} else {
			Common.Logging.w(TAG, "AirBoard Session "+this+" stop failed");		
			originator.failedSessionList.add(this);
		}
	}
	
	public void stop(Originator originator)
	{
		stop(originator, false, 10);
	}
	
	public void stop(Originator originator, boolean replace)
	{
		stop(originator, replace, 10);
	}
	
	public void doPlay(Originator originator, int replaceStreamId)
	{		
		Common.Logging.i(TAG, "AirBoard Session "+this+" play request");
		if (replaceStreamId > 0)
		{
			streamId = replaceStreamId;
		} else {
			// get unused streamId and associate a surface with it
			streamId = mCanvas.mSurfaceMgr.getUnusedStreamId();
			mCanvas.showWindow(streamId); // TODO remove once real canvas app available
		}
		Common.Logging.i(TAG, "AirBoard Session "+this+" got streamId "+streamId);		
		if (acquireSurface() != null)
		{
			// set device mode for this streamId to WBS_STREAM_IN
			mStreamCtl.setDeviceMode(3, streamId);
			// set the url
			mStreamCtl.setWbsStreamUrl(url, streamId);
			//start the preview mode
			Common.Logging.i(TAG, "AirBoard Session "+this+" calling Start()");
			mStreamCtl.Start(streamId);
			Common.Logging.i(TAG, "AirBoard Session "+this+" back from Start()");
		} else {
			Common.Logging.w(TAG, "AirBoard Session "+this+" play failed");		
			originator.failedSessionList.add(this);
		}
	}
	
	public void play(final Originator originator, final int replaceStreamId, int timeoutInSeconds)
	{
		Runnable r = new Runnable() { public void run() { doPlay(originator, replaceStreamId); } };
        TimeSpan start = TimeSpan.now();
		boolean completed = executeWithTimeout(r, TimeSpan.fromSeconds(timeoutInSeconds));
		Common.Logging.i(TAG, "AirBoard Session play completed in "+TimeSpan.now().subtract(start).toString()+" seconds");
		if (completed)
		{
			setState(SessionState.Playing);
			setResolution(new AirMediaSize(0, 0));
		}
	}
	
	public void play(Originator originator, int replaceStreamId)
	{
		play(originator, replaceStreamId, 10);
	}
	
	public void play(Originator originator)
	{
		play(originator, -1, 10);
	}
}
