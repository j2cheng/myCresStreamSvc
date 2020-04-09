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
    public Waiter waiterForDisconnectRequestToUser = null;
    public Scheduler receiverCmdScheduler = new Scheduler("TxRx.airmedia.session.receiverCmd");
    public Scheduler requestScheduler = new Scheduler("TxRx.airmedia.session.request");
    		
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
		waiterForDisconnectRequestToUser = new Waiter();
	}
	
	// Receiver initiated requests - handled on thread of requestScheduler - will be called when state changes are signaled by receiver
	// Must be run async so we do not block receiver events thread
	public void doSessionEvent(String r, Originator o, int t)
	{
		// When this event actually runs - is popped from the handler's queue we check the state of the session
		// if it matches request exit
		boolean done = false;
		if (r.equalsIgnoreCase("Play") && isPlaying())
		{
			Common.Logging.i(TAG, this+" play request discarded - already in "+state+" state");
			done = true;
		} 
		else if (r.equalsIgnoreCase("Stop") && isStopped()) 
		{
			Common.Logging.i(TAG, this+" stop request discarded - already in "+state+" state");
			done = true;
		}
		if (!done)
		{
			Common.Logging.i(TAG, this+" starting processing of "+r+" request");
			boolean success = mCanvas.getCrestore().doSynchronousSessionEvent(this, r, o, t);
			Common.Logging.i(TAG, this+" completed processing of "+r+" request: "+((success)?"success":"failed - timeout"));
		}
	}
	
	public void scheduleRequest(String request, Originator originator, int timeout)
	{
		final String r_ = request;
		final int t_ = timeout;
		final Originator o_ = originator;
		Common.Logging.i(TAG, "Session "+this+" scheduling "+request+" request");
		Runnable r = new Runnable() { @Override public void run() { doSessionEvent(r_, o_, t_); } };
		requestScheduler.queue(r);
	}
	
	public void connectRequest(Originator originator)
	{
		Common.Logging.i(TAG, "Session "+this+" connect request from "+originator);
		mSessionMgr.add(this);
		scheduleRequest("Connect", originator, 15);
	}
	
	public void disconnectRequest(Originator originator)
	{
		if (state == SessionState.Disconnecting) {
			Common.Logging.i(TAG, "Session "+this+" is already disconnecting");
			return;
		}
		setState(SessionState.Disconnecting);
		Common.Logging.i(TAG, "Session "+this+" disconnect request from "+originator);
		scheduleRequest("Disconnect", originator, 15);
		mSessionMgr.remove(this);
	}
	
	public void playRequest(Originator originator)
	{
		Common.Logging.i(TAG, "Session "+this+" play request from "+originator);
		scheduleRequest("Play", originator, 15);
	}
	
	public void stopRequest(Originator originator)
	{
		Common.Logging.i(TAG, "Session "+this+" stop request from "+originator);
		scheduleRequest("Stop", originator, 15);
	}
	
	// Stop and Play actions below are always initiated by the AVF sending a sessionResponse message.  Therefore these functions
	// will always run on the thread of the sessionResponseScheduler
	// If the session is not in the correct state, a stop or play request may need to be sent to the receiver.  In this case we
	// send the command to the receiver on a different thread - that of the stopPlayScheduler and wait for success to be signaled
	// by the receiver via an event state change or we timeout.
	
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
			if (streamId >= 0)
			{
				mCanvas.showWindow(streamId); // TODO remove once real canvas app available
				surface = acquireSurface();
			}
			else
			{
				Common.Logging.e(TAG, "doPlay(): ****** AirMediaSession "+this+" invalid streamId "+streamId+" *****");
			}
		}
		else
		{
			streamId = replaceStreamId;
			surface = mStreamCtl.getSurface(streamId);
		}
		Common.Logging.i(TAG, "AirMediaSession "+this+" using surface "+surface+" isValid="+surface.isValid());
		if (airMediaReceiverSession  == null)
		{
			Common.Logging.i(TAG, "AirMediaSession::doPlay(): AirMediaSession "+this+" has a null receiver session ");
			streamId = -1;
			releaseSurface();		
			return false;
		}
		Common.Logging.i(TAG, "AirMediaSession "+this+" attaching surface "+surface);
		airMediaReceiverSession.attach(surface);
		mStreamCtl.mUsedForAirMedia[streamId] = true;
		return true;
	}
	
	public void play(Originator originator, int replaceStreamId)
	{
		Common.Logging.i(TAG, "Session "+this+" play entered originator="+originator+"  repalceStreamId="+replaceStreamId);
		if (isPlaying() && isVideoPlaying())
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
		// Command sent on separate thread and then we wait for the receiver event thread to invoke the setVideoState - when the video
		// state changes or we timeout.
		Common.Logging.w(TAG, "Session "+this+" sending play command to receiver");
		waiterForPlayRequestToUser.prepForWait();
		receiverCmdScheduler.queue(new Runnable() { @Override public void run() { airMediaReceiverSession.play(); }; });	
		boolean timeout = waiterForPlayRequestToUser.waitForSignal(TimeSpan.fromSeconds(5));
		return !timeout;
	}
	
	// Stop action
	public boolean doStop(boolean replace)
	{
		if (streamId == -1)
		{
			Common.Logging.i(TAG, "AirMediaSession::doStop(): AirMediaSession "+this+" already has streamId="+streamId+" must never had started");
			return true;
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
		if (isStopped() && isVideoStopped())
		{
			Common.Logging.i(TAG, "Session "+this+" current state is "+state+"  exiting");
			return;
		}
		setState(SessionState.Stopping);
		if (getVideoState() != AirMediaSessionStreamingState.Stopped)
		{
			Common.Logging.i(TAG, "Session "+this+" calling sendStopCommand to source user");
			boolean success = sendStopCommandToSourceUser();  // make synchronous must wait for videoState to go to Playing
			if (!success)
			{
				Common.Logging.i(TAG, "Session "+this+" stop command to AirMedia receiver failed or timed out");
				return;
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
		if (!mCanvas.IsAirMediaCanvasUp() || mCanvas.IsInCodecFailureRecovery())
		{
			if (mCanvas.IsInCodecFailureRecovery())
				Common.Logging.i(TAG, "stopAllSessions(): in codec failure recovery mode set state to stopped for session "+sessionId());
			else
				Common.Logging.i(TAG, "stopAllSessions(): AirMediaCanvas not up set state to stopped for session "+sessionId());
			setState(SessionState.Stopped);
			releaseSurface();
			mStreamCtl.mUsedForAirMedia[streamId] = false;
			return false;
		}
		// Command sent on separate thread and then we wait for the receiver event thread to invoke the setVideoState - when the video
		// state changes or we timeout.
		Common.Logging.i(TAG, "Session "+this+" sending stop command to receiver");
		waiterForStopRequestToUser.prepForWait();
		receiverCmdScheduler.queue(new Runnable() { @Override public void run() { airMediaReceiverSession.stop(); }; });	
        boolean timeout = waiterForStopRequestToUser.waitForSignal(TimeSpan.fromSeconds(5));
		return !timeout;
	}
	
	public void disconnect(Originator originator)
	{
		if (state == SessionState.Disconnecting)
			return;
		setState(SessionState.Disconnecting);
		sendDisconnectCommandToSourceUser();
	}
	
	public boolean sendDisconnectCommandToSourceUser()
	{
		Common.Logging.w(TAG, "Session "+this+" sending disconnect command to receiver");
		waiterForDisconnectRequestToUser.prepForWait();
		receiverCmdScheduler.queue(new Runnable() { @Override public void run() { airMediaReceiverSession.disconnect(); }; });	
		boolean timeout = waiterForDisconnectRequestToUser.waitForSignal(TimeSpan.fromSeconds(5));
		return !timeout;
	}
	
	public void setDisconnected()
	{
		Common.Logging.i(TAG, "setDisconnected(): Session "+this+" starting to check if processing is needed");
		if (waiterForDisconnectRequestToUser.isWaiting())
		{
			// user has already requested stop so simply signal that session is now stopped
			Common.Logging.i(TAG, "setDisconnected(): Session "+this+" signaling disconnect to waiter");
			waiterForDisconnectRequestToUser.signal();
		}
		else
		{
			final Originator originator = new Originator(RequestOrigin.Receiver, this);
			Common.Logging.i(TAG, "setVideoState(): Session "+this+" disconnect request");
			disconnectRequest(originator);
		}
		Common.Logging.i(TAG, "setDisconnected(): Session "+this+" exit");
	}

	public AirMediaSessionStreamingState getVideoState()
	{
		return videoState;
	}
	
	public boolean isVideoPlaying()
	{
		Common.Logging.i(TAG, "isVideoPlaying(): Session "+this+" videoState="+videoState);
		return (videoState == AirMediaSessionStreamingState.Playing || videoState == AirMediaSessionStreamingState.Paused);
	}
	
	public boolean isVideoStopped()
	{
		Common.Logging.i(TAG, "isVideoStopped(): Session "+this+" videoState="+videoState);
		return (videoState == AirMediaSessionStreamingState.Stopped);
	}
	
	public void setVideoState(AirMediaSessionStreamingState s)
	{
		if (videoState != s)
		{
			Common.Logging.i(TAG, "setVideoState(): Session "+this+" changing video state from "+videoState+" to "+s+"   sesion state="+getState());
			videoState = s;
		} else {
			Common.Logging.i(TAG, "setVideoState(): Session "+this+" ignoring incoming video state change to "+videoState+" already in that state");
			return;
		}
		Common.Logging.i(TAG, "setVideoState(): Session "+this+" starting to check if processing is needed");
		if (s == AirMediaSessionStreamingState.Stopped)
		{
			if (waiterForStopRequestToUser.isWaiting())
			{
				// user has already requested stop so simply signal that session is now stopped
				Common.Logging.i(TAG, "setVideoState(): Session "+this+" signaling stoppage to waiter");
				waiterForStopRequestToUser.signal();
			}
			else
			{
				final Originator originator = new Originator(RequestOrigin.Receiver, this);
				Common.Logging.i(TAG, "setVideoState(): Session "+this+" stop request");
				stopRequest(originator);
			}
		} 
		else if (s == AirMediaSessionStreamingState.Playing)
		{
			if (waiterForPlayRequestToUser.isWaiting())
			{
				// user has already requested play so simply signal that session is now playing
				Common.Logging.i(TAG, "setVideoState(): Session "+this+" signaling playing to waiter");
				waiterForPlayRequestToUser.signal();
			}
			else 
			{
				SessionState prevState = getState();
				if (prevState == SessionState.Stopped)
				{
					Originator originator = new Originator(RequestOrigin.Receiver, this);
					Common.Logging.i(TAG, "setVideoState(): Session "+this+" play request");
					playRequest(originator);
				}
				else if (prevState == SessionState.Paused)
				{
					Common.Logging.i(TAG, "setVideoState(): Session "+this+" resume from pause");
					setState(SessionState.Playing);
					Common.Logging.i(TAG, "setVideoState(): Session "+this+" calling canvasSessionUpdate()");
					if (!mSessionMgr.getPendingLayoutUpdate())
						canvasSessionUpdate(this);
				}
			}
		}
		else if (s == AirMediaSessionStreamingState.Paused)
		{
			Common.Logging.i(TAG, "setVideoState(): Session "+this+" processing paused case");
			setState(SessionState.Paused);
			if (!mSessionMgr.getPendingLayoutUpdate())
				canvasSessionUpdate(this);
		}
		Common.Logging.i(TAG, "setVideoState(): Session "+this+" exit");
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
