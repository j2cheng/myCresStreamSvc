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
    public static final String TAG = "TxRxHDMISession";
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
		Common.Logging.i(TAG, "-----------------------HDMI Session "+this+" stop request");
		if (streamId >= 0)
		{
			// set device mode for this streamId to preview
			mStreamCtl.setDeviceMode(2, streamId);
			//start the preview mode
			Common.Logging.i(TAG, "-----------------------HDMI Session "+this+" calling Stop()");
			mStreamCtl.Stop(streamId, false);
			Common.Logging.i(TAG, "-----------------------HDMI Session "+this+" back from Stop()");
			if (!replace)
			{
				mCanvas.hideWindow(streamId); // TODO remove once real canvas app available
				releaseSurface();
			}
		}
	}
	
	public void stop(final boolean replace, int timeoutInSeconds)
	{
		Runnable r = new Runnable() { public void run() { doStop(replace); } };
        TimeSpan start = TimeSpan.now();
		boolean completed = executeWithTimeout(r, TimeSpan.fromSeconds(timeoutInSeconds));
		Common.Logging.i(TAG, "-----------------------HDMI Session stop completed in "+TimeSpan.now().subtract(start).toString()+" seconds");
		if (completed) {
			streamId = -1;
			setState(SessionState.Stopped);
		}
	}
	
	public void stop(Originator originator)
	{
		stop(false, 10);
	}
	
	public void stop(Originator originator, boolean replace)
	{
		stop(replace, 10);
	}
	
	public void doPlay(int replaceStreamId)
	{		
		Common.Logging.i(TAG, "-----------------------HDMI Session "+this+" play request");
		if (replaceStreamId > 0)
		{
			streamId = replaceStreamId;
		} else {
			// get unused streamId and associate a surface with it
			streamId = mCanvas.mSurfaceMgr.getUnusedStreamId();
			mCanvas.showWindow(streamId); // TODO remove once real canvas app available
		}
		Common.Logging.i(TAG, "-----------------------HDMI Session "+this+" got streamId "+streamId);
		acquireSurface();
		// set device mode for this streamId to preview
		mStreamCtl.setDeviceMode(2, streamId);
		//start the preview mode
		Common.Logging.i(TAG, "-----------------------HDMI Session "+this+" calling Start()");
		mStreamCtl.Start(streamId);
		Common.Logging.i(TAG, "-----------------------HDMI Session "+this+" back from Start()");
	}
	
	public void play(Originator originator, final int replaceStreamId, int timeoutInSeconds)
	{
		Runnable r = new Runnable() { public void run() { doPlay(replaceStreamId); } };
        TimeSpan start = TimeSpan.now();
		boolean completed = executeWithTimeout(r, TimeSpan.fromSeconds(timeoutInSeconds));
		Common.Logging.i(TAG, "-----------------------HDMI Session play completed in "+TimeSpan.now().subtract(start).toString()+" seconds");
		if (completed)
		{
			setState(SessionState.Playing);
			setResolution();
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
				
	// returns true if successful completion, false for timeout
    public boolean executeWithTimeout(Runnable r, TimeSpan timeout)
    {
    	 ConditionVariable completed = new ConditionVariable();

         Thread t = new Thread(new Runnable() {
        	 private ConditionVariable c_;
        	 private Runnable r_;
        	 @Override public void run() { try { r_.run(); } finally { c_.open(); } }
        	 public Runnable set(Runnable t, ConditionVariable c) { r_ = t; c_ = c; return this; }
         }.set(r, completed));
         t.start();

         return completed.block(TimeSpan.toLong(timeout.totalMilliseconds()));
    }    
}
