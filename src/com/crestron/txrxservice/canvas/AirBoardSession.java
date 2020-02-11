package com.crestron.txrxservice.canvas;

import com.crestron.airmedia.canvas.channels.ipc.CanvasPlatformType;

import android.util.Log;

public class AirBoardSession extends Session
{
    public static final String TAG = "TxRxAirBoardSession"; 

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
}
