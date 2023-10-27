package com.crestron.txrxservice.canvas;

import com.crestron.airmedia.canvas.channels.ipc.CanvasPlatformType;
import com.crestron.airmedia.canvas.channels.ipc.CanvasSurfaceMode;
import com.crestron.airmedia.canvas.channels.ipc.CanvasSurfaceOptions;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSize;
import com.crestron.airmedia.utilities.Common;
import com.crestron.airmedia.utilities.TimeSpan;
import com.crestron.txrxservice.HDMIInputInterface;
import com.crestron.txrxservice.CameraPreview;

import android.os.ConditionVariable;
import android.util.Log;
import android.view.Surface;

public class HDMISession extends Session
{
    public static final String TAG = "TxRx HDMISession";
    private HDMIInputInterface hdmiInput;
    private static String displayLabel=null;

	public HDMISession(int inputNumber) {
		super(); // will assign id;
        Log.i(TAG, "HDMISession");
		state = SessionState.Connecting;
		type = SessionType.HDMI;
		airMediaType = null;
		this.inputNumber = inputNumber;
		userLabel = "HDMI - Wired Input 1";
		platform = CanvasPlatformType.Hardware;
		if (mStreamCtl.isRGB888HDMIVideoSupported)
		{
			options = new CanvasSurfaceOptions(CanvasSurfaceMode.TagVideoLayer, "PreviewVideoLayer");
		}
	}

	public void setHdmiInput(HDMIInputInterface h)
    {
        Log.i(TAG, "setHdmiInput");
        hdmiInput = h;
    }

	public String toString()
	{
		String value =
            "Session: " + type.toString() + "-" + inputNumber
            + "  sessionId=" + sessionId();
        Log.v(TAG, value);
        return value;
	}

	public void doStop()
	{
		Common.Logging.i(TAG, this + " stop request");
		if (streamId >= 0)
		{
			// set device mode for this streamId to preview
			mStreamCtl.setDeviceMode(2, streamId);
			//start the preview mode
			Common.Logging.i(TAG, this+" calling Stop()");
			mStreamCtl.Stop(streamId, false);
			Common.Logging.i(TAG, this+" sending stop to csio for audio on AM-300");
			mStreamCtl.sendHdmiStart(streamId, false);
			Common.Logging.i(TAG, this+" back from Stop()");
			releaseSurface();
		}
	}

	public void stop(final Originator originator, int timeoutInSeconds)
	{
		Runnable r = new Runnable() { public void run() { doStop(); } };
        TimeSpan start = TimeSpan.now();
		boolean completed = executeWithTimeout(r, TimeSpan.fromSeconds(timeoutInSeconds));
		Common.Logging.i(TAG, "stop completed in "+TimeSpan.now().subtract(start).toString()+" seconds");
		if (completed) {
			streamId = -1;
			setState(SessionState.Stopped);
		} else {
			Common.Logging.w(TAG, this+" stop failed");
			originator.failedSessionList.add(this);
		}
	}

	public void stop(Originator originator)
	{
		stop(originator, 10);
	}

	public void doPlay(final Originator originator)
	{
		Common.Logging.i(TAG, "HDMI Session "+this+" play request");
		setStreamId();
		Common.Logging.i(TAG, "HDMI Session "+this+" got streamId "+streamId);
		if (acquireSurface() != null)
		{
			// set device mode for this streamId to preview
			mStreamCtl.setDeviceMode(2, streamId);
			//start the preview mode
			Common.Logging.i(TAG, "HDMI Session "+this+" calling Start()");
			mStreamCtl.Start(streamId);
			// signal to csio to start audio for HDMI via audiomux
			Common.Logging.i(TAG, "HDMI Session "+this+" sending HDMI Start signal to csio for audio on AM-300");
			mStreamCtl.sendHdmiStart(streamId, true);
			audioMute(isAudioMuted);
			Common.Logging.i(TAG, "HDMI Session "+this+" back from Start()");
		} else {
			Common.Logging.w(TAG, "HDMI Session "+this+" doPlay() got null surface");
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
		Common.Logging.i(TAG, "HDMI Session play completed in "+TimeSpan.now().subtract(start).toString()+" seconds");
		if (completed)
		{
			setState(SessionState.Playing);
			setResolution();
		}
		else
		{
			Common.Logging.w(TAG, "HDMI Session "+this+" play failed - timeout");
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
		AirMediaSize res = mStreamCtl.getPreviewResolution();
		super.setResolution(res);
	}

	public boolean audioMute(boolean enable)
	{
		if (enable)
		{
			Common.Logging.i(TAG, "audioMute(): Session "+this+" calling streamMute()");
			mCanvas.mStreamCtl.setPreviewVolume(0);
			mStreamCtl.sendExternalHdmiMute(streamId, true);
		} else {
			Common.Logging.i(TAG, "audioMute(): Session "+this+" calling streamUnMute()");
			mCanvas.mStreamCtl.setPreviewVolume((int)mCanvas.mStreamCtl.userSettings.getUserRequestedVolume());
			mStreamCtl.sendExternalHdmiMute(streamId, false);
		}
		setIsAudioMuted(enable);
		CameraPreview.is_hdmisession_muted = enable;
		return true;
	}

	// this assumes we have a single HDMI session - so static displayLabel can be used for the single session
	public static void setDisplayLabel(String label)
	{
		Common.Logging.i(TAG, "HDMISession.setDisplayLabel(): displayLabel=label");
		displayLabel = label;
	}

	public String getDisplayLabel() { return (displayLabel==null)?userLabel:displayLabel; }
}
