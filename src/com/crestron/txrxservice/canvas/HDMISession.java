package com.crestron.txrxservice.canvas;

import com.crestron.airmedia.canvas.channels.ipc.CanvasPlatformType;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSize;
import com.crestron.airmedia.utilities.Common;
import com.crestron.airmedia.utilities.TimeSpan;
import com.crestron.txrxservice.HDMIInputInterface;

import android.os.ConditionVariable;
import android.util.Log;
import android.view.Surface;

public class HDMISession extends Session
{
    public static final String TAG = "TxRx.canvas.HDMI.session";
    private HDMIInputInterface hdmiInput;

	public HDMISession(int inputNumber) {
		super(); // will assign id;
		state = SessionState.Connecting;
		type = SessionType.HDMI;
		airMediaType = null;
		this.inputNumber = inputNumber;
		userLabel = "HDMI-"+String.valueOf(inputNumber);
		platform = CanvasPlatformType.Hardware;
	}
	
	public void setHdmiInput(HDMIInputInterface h) { hdmiInput = h; }

	public String toString()
	{
		return ("Session: "+type.toString()+"-"+inputNumber+"  sessionId="+sessionId());
	}
	
	public void doStop(boolean replace)
	{
		Common.Logging.i(TAG, "HDMI Session "+this+" stop request");
		if (streamId >= 0)
		{
			// set device mode for this streamId to preview
			mStreamCtl.setDeviceMode(2, streamId);
			//start the preview mode
			Common.Logging.i(TAG, "HDMI Session "+this+" calling Stop()");
			mStreamCtl.Stop(streamId, false);
			Common.Logging.i(TAG, "HDMI Session "+this+" sending stop to csio for audio on AM-300");
			mStreamCtl.sendHdmiStart(streamId, false);
			Common.Logging.i(TAG, "HDMI Session "+this+" back from Stop()");
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
		Common.Logging.i(TAG, "HDMI Session stop completed in "+TimeSpan.now().subtract(start).toString()+" seconds");
		if (completed) {
			streamId = -1;
			setState(SessionState.Stopped);
		} else {
			Common.Logging.w(TAG, "HDMI Session "+this+" stop failed");		
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
	
	public void doPlay(final Originator originator, int replaceStreamId)
	{		
		Common.Logging.i(TAG, "HDMI Session "+this+" play request");
		if (replaceStreamId > 0)
		{
			streamId = replaceStreamId;
		} else {
			// get unused streamId and associate a surface with it
			streamId = mCanvas.mSurfaceMgr.getUnusedStreamId();
			mCanvas.showWindow(streamId); // TODO remove once real canvas app available
		}
		Common.Logging.i(TAG, "HDMI Session "+this+" got streamId "+streamId);
		if (acquireSurface() != null)
		{
			// set device mode for this streamId to preview
			mStreamCtl.setDeviceMode(2, streamId);
			//start the preview mode
			Common.Logging.i(TAG, "HDMI Session "+this+" calling Start()");
			mStreamCtl.Start(streamId);
			// signal to csio to start audio for HDMI via audiomux
			Common.Logging.i(TAG, "HDMI Session "+this+" sending HDMI Start signal to csio fur audio on AM-300");
			mStreamCtl.sendHdmiStart(streamId, true);
			Common.Logging.i(TAG, "HDMI Session "+this+" back from Start()");
		} else {
			Common.Logging.w(TAG, "HDMI Session "+this+" doPlay() got null surface");
			originator.failedSessionList.add(this);
		}
	}
	
	public void play(final Originator originator, final int replaceStreamId, int timeoutInSeconds)
	{
		Runnable r = new Runnable() { public void run() { doPlay(originator, replaceStreamId); } };
        TimeSpan start = TimeSpan.now();
		boolean completed = executeWithTimeout(r, TimeSpan.fromSeconds(timeoutInSeconds));
		Common.Logging.i(TAG, "HDMI Session play completed in "+TimeSpan.now().subtract(start).toString()+" seconds");
		if (completed)
		{
			setState(SessionState.Playing);
			setResolution();
		}
		else
		{
			Common.Logging.w(TAG, "HDMI Session "+this+" play failed - timeout");		
			originator.failedSessionList.add(this);
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

	
	public void setResolution()
	{
		AirMediaSize res = mStreamCtl.getPreviewResolution();
		super.setResolution(res);
	}
}
