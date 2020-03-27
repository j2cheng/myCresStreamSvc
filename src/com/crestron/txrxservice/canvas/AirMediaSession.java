package com.crestron.txrxservice.canvas;

import com.crestron.airmedia.receiver.m360.ipc.AirMediaSessionStreamingState;
import com.crestron.airmedia.utilities.Common;
import com.crestron.airmedia.utilities.TimeSpan;

import android.view.Surface;


public class AirMediaSession extends Session
{
	com.crestron.airmedia.receiver.m360.models.AirMediaSession airMediaReceiverSession;
    public static final String TAG = "TxRx.canvas.airmedia.session"; 
    public AirMediaSessionStreamingState videoState;
    public Waiter waiterForPlayRequestToUser = null;
    public Waiter waiterForStopRequestToUser = null;

	public AirMediaSession(com.crestron.airmedia.receiver.m360.models.AirMediaSession session, String label) {
		super(); // will assign id;
		state = SessionState.Connecting;
		type = SessionType.AirMedia;
		airMediaType = SessionAirMediaType.App;
		airMediaReceiverSession = session;
		userLabel = label;
		videoState = AirMediaSessionStreamingState.Stopped;
		if (session.info() != null)
		{
			setPlatformType(session.info().platform);
		}
		waiterForPlayRequestToUser = new Waiter();
		waiterForStopRequestToUser = new Waiter();
	}
	
	// Requests
	public boolean connectRequest(Originator originator)
	{
		Common.Logging.i(TAG, "Session "+this+" connect request from "+originator);
		mSessionMgr.add(this);
		return mCanvas.getCrestore().doSessionEventWithCompletionTimeout(this, "Connect", originator, TimeSpan.fromSeconds(5));
	}
	
	public void disconnectRequest(Originator originator)
	{
		if (state == SessionState.Disconnecting) {
			Common.Logging.i(TAG, "Session "+this+" is already disconnecting");
			return;
		}
		Common.Logging.i(TAG, "Session "+this+" disconnect request from "+originator);
		setState(SessionState.Disconnecting);
		mCanvas.getCrestore().doSessionEventWithCompletionTimeout(this, "Disconnect", originator, TimeSpan.fromSeconds(5));
		mSessionMgr.remove(this);
	}
	
	public boolean playRequest(Originator originator)
	{
		Common.Logging.i(TAG, "Session "+this+" play request from "+originator);
		return mCanvas.getCrestore().doSessionEventWithCompletionTimeout(this, "Play", originator, TimeSpan.fromSeconds(5));
	}
	
	public boolean stopRequest(Originator originator)
	{
		Common.Logging.i(TAG, "Session "+this+" stop request from "+originator);
		return mCanvas.getCrestore().doSessionEventWithCompletionTimeout(this, "Stop", originator, TimeSpan.fromSeconds(5));
	}
	
	// Play action
	public boolean doPlay(int replaceStreamId)
	{
		Surface surface = null;
		if (streamId != -1)
		{
			Common.Logging.i(TAG, "AirMediaSession::doPlay(): AirMediaSession "+this+" already has a valid streamId="+streamId);
			return false;
		}
		if (replaceStreamId < 0)
		{
			// get unused streamId and associate a surface with it
			streamId = mCanvas.mSurfaceMgr.getUnusedStreamId();
			Common.Logging.i(TAG, "AirMediaSession "+this+" assigned streamId "+streamId);
			mCanvas.showWindow(streamId); // TODO remove once real canvas app available
			surface = acquireSurface();
		}
		else
		{
			streamId = replaceStreamId;
			surface = mStreamCtl.getSurface(streamId);
		}
		mStreamCtl.mUsedForAirMedia[streamId] = true;
		Common.Logging.i(TAG, "AirMediaSession "+this+" using surface "+surface+" isValid="+surface.isValid());
		if (airMediaReceiverSession  == null)
		{
			Common.Logging.i(TAG, "AirMediaSession::doPlay(): AirMediaSession "+this+" has a null receiver session ");
			return false;
		}
		Common.Logging.i(TAG, "AirMediaSession "+this+" attaching surface "+surface);
		airMediaReceiverSession.attach(surface);
		return true;
	}
	
	public void play(Originator originator, int replaceStreamId)
	{
		Common.Logging.i(TAG, "Session "+this+" play entered originator="+originator+"  repalceStreamId="+replaceStreamId);
		if (isPlaying())
			return;
		setState(SessionState.Starting);
		if (getVideoState() != AirMediaSessionStreamingState.Playing)
		{
			boolean success = sendPlayCommandToSourceUser();  // make synchronous must wait for videoState to go to Playing
			if (!success)
			{
				Common.Logging.i(TAG, "Session "+this+" play command to AirMedia receiver timed out");
			}
		}
		if (doPlay(replaceStreamId))
		{
			//mStreamCtl.SendStreamState(StreamState.STARTED, 0);
			setState(SessionState.Playing);
		}
		else
		{
			Common.Logging.i(TAG, "Session "+this+" play command failed");
		}
	}
	
	public void play(Originator originator)
	{
		play(originator, -1);
	}
	
	public synchronized boolean sendPlayCommandToSourceUser()
	{
		if (airMediaReceiverSession == null)
			return false;
		if (getVideoState() == AirMediaSessionStreamingState.Playing)
			return true;
		Common.Logging.w(TAG, "Session "+this+" sending play command to receiver");
		waiterForPlayRequestToUser.prepForWait();
		airMediaReceiverSession.play();
		boolean timeout = waiterForPlayRequestToUser.waitForSignal(TimeSpan.fromSeconds(5));
		return !timeout;
	}
	
	// Stop action
	public boolean doStop(boolean replace)
	{
		if (streamId == -1)
		{
			Common.Logging.i(TAG, "AirMediaSession::doStop(): AirMediaSession "+this+" already has streamId="+streamId);
			return false;
		}
		if (airMediaReceiverSession  == null)
		{
			Common.Logging.i(TAG, "AirMediaSession::doStop(): AirMediaSession "+this+" has a null receiver session ");
			return false;
		}
		airMediaReceiverSession.detach();
		releaseSurface();
		mStreamCtl.mUsedForAirMedia[streamId] = false;
		if (!replace)
		{
			Common.Logging.i(TAG, "AirMediaSession::doStop(): AirMediaSession "+this+" calling hideWindow with streamId="+streamId);
			mCanvas.hideWindow(streamId); // TODO remove once real canvas app available
			// release surface
			streamId = -1;
		}
		return true;
	}
	
	public void stop(Originator originator, boolean replace)
	{
		Common.Logging.i(TAG, "Session "+this+" stop entered originator="+originator);
		if (isStopped())
			return;
		setState(SessionState.Stopping);
		if (getVideoState() != AirMediaSessionStreamingState.Stopped)
		{
			Common.Logging.i(TAG, "Session "+this+" calling sensStopCommand to source user");
			boolean success = sendStopCommandToSourceUser();  // make synchronous must wait for videoState to go to Playing
			if (!success)
			{
				Common.Logging.i(TAG, "Session "+this+" stop command to AirMedia receiver failed or timed out");
			}
		}
		if (doStop(replace))
		{
			//mStreamCtl.SendStreamState(StreamState.STOPPED, 0);
			setState(SessionState.Stopped);
		}
		else
		{
			Common.Logging.w(TAG, "Session "+this+" stop command failed");
		}
	}
	
	public void stop(Originator originator)
	{
		stop(originator, false);
	}
	
	public boolean sendStopCommandToSourceUser()
	{
		if (airMediaReceiverSession == null)
			return false;
		if (getVideoState() == AirMediaSessionStreamingState.Stopped)
			return true;
		Common.Logging.i(TAG, "Session "+this+" sending stop command to receiver");
		waiterForStopRequestToUser.prepForWait();
		airMediaReceiverSession.stop();
		boolean timeout = waiterForStopRequestToUser.waitForSignal(TimeSpan.fromSeconds(5));
		return !timeout;
	}
	
	public AirMediaSessionStreamingState getVideoState()
	{
		return videoState;
	}
	
	public void setVideoState(AirMediaSessionStreamingState s)
	{
		if (videoState != s)
		{
			Common.Logging.i(TAG, "Session "+this+" changing video state from "+videoState+" to "+s);
			videoState = s;
		} else {
			Common.Logging.i(TAG, "Session "+this+" ignoring incoming video state change to "+videoState+" already in that state");
			return;
		}
		if (s == AirMediaSessionStreamingState.Stopped)
		{
		  if (waiterForStopRequestToUser.isWaiting())
		  {
			  // user has already requested stop so simply signal that session is now stopped
			  Common.Logging.i(TAG, "Session "+this+" signaling stoppage to waiter");
			  waiterForStopRequestToUser.signal();
		  }
		  else
		  {
			  final Originator originator = new Originator(RequestOrigin.Receiver, this);
			  Runnable r = new Runnable() { @Override public void run() { stopRequest(originator); } };
			  Common.Logging.i(TAG, "Session "+this+" scheduling stop request");
			  scheduler().queue(TAG, "stopRequest", TimeSpan.fromSeconds(10), r);
		  }
		} 
		else if (s == AirMediaSessionStreamingState.Playing)
		{
  		  if (waiterForPlayRequestToUser.isWaiting())
  		  {
  			  // user has already requested play so simply signal that session is now playing
  			  waiterForPlayRequestToUser.signal();
  		  }
  		  else 
  		  {
  			  SessionState prevState = getState();
  			  if (prevState == SessionState.Stopped)
  			  {
  				  final Originator originator = new Originator(RequestOrigin.Receiver, this);
  				  Runnable r = new Runnable() { @Override public void run() { playRequest(originator); } };
  				  scheduler().queue(TAG, "playRequest", TimeSpan.fromSeconds(10), r);
  			  }
  			  else if (prevState == SessionState.Paused)
  			  {
  				  setState(SessionState.Playing);
  				  canvasSessionUpdate(this);
  			  }
  		  }
		}
		else if (s == AirMediaSessionStreamingState.Paused)
		{
			setState(SessionState.Paused);
			canvasSessionUpdate(this);
		}
	}
	
	public String toString()
	{
		return ("Session: "+type.toString()+"-"+airMediaType.toString()+" "+com.crestron.airmedia.receiver.m360.models.AirMediaSession.toDebugString(airMediaReceiverSession)+"  sessionId="+sessionId());
	}
	
	public Surface acquireSurface()
	{
		return super.acquireSurface();
	}
	
	public void releaseSurface()
	{
		super.releaseSurface();
	}
	
	public String getAirMediaUserName()
	{
		return (airMediaReceiverSession != null) ? airMediaReceiverSession.username() : "";
	}
}
