package com.crestron.txrxservice.canvas;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.crestron.airmedia.canvas.channels.ipc.CanvasPlatformType;
import com.crestron.airmedia.canvas.channels.ipc.CanvasResponse;
import com.crestron.airmedia.canvas.channels.ipc.CanvasSurfaceAcquireResponse;
import com.crestron.airmedia.canvas.channels.ipc.CanvasVideoType;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaPlatforms;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSessionStreamingState;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSessionVideoType;
import com.crestron.airmedia.utilities.Common;
import com.crestron.airmedia.utilities.TimeSpan;
import com.crestron.txrxservice.CresStreamCtrl;

import android.view.Surface;

public class AirMediaSession extends Session
{
	com.crestron.airmedia.receiver.m360.models.AirMediaSession airMediaReceiverSession;
    public static final String TAG = "TxRx.canvas.airmedia.session"; 
    public static final int ConnectTimeout = 30; 
    public static final int DisconnectTimeout = 30; 
    public static final int StopTimeout = 30; 
    public static final int PlayTimeout = 30; 
    public AirMediaSessionStreamingState videoState;
    public Waiter waiterForPlayRequestToUser = null;
    public Waiter waiterForStopRequestToUser = null;
    public Waiter waiterForDisconnectRequestToUser = null;
    public Scheduler receiverCmdScheduler = new Scheduler("TxRx.airmedia.session.receiverCmd");
    private AtomicBoolean alreadyDisconnected = null;         // receiver has already disconnected sessopm
    private CanvasSurfaceAcquireResponse surfaceRenewRequestResponse;
    Surface surface=null;
    public long stopTime;
    public double nanoSecsInMin=(60L*1000*1000*1000);
    		
	public AirMediaSession(com.crestron.airmedia.receiver.m360.models.AirMediaSession session, String label) {
		super(); // will assign id;
		state = SessionState.Connecting;
		type = SessionType.AirMedia;
		stopTime = System.nanoTime(); // treat session as stopped
		airMediaType = SessionAirMediaType.Undefined;
		airMediaReceiverSession = session;
		userLabel = label;
		airmediaId = session.id();
		miracastSessionId = session.miracastSessionId();
        Common.Logging.i(TAG, "AirMediaSession::AirMediaSession - miracast session Id = " + miracastSessionId);
		videoState = AirMediaSessionStreamingState.Stopped;
		isAudioMuted = session.audioMuted();
        mStreamCtl.wifidVideoPlayer.postTx3DeviceType(miracastSessionId);
		if (session.info() != null)
		{
			setPlatformType(session.info().platform);
		}
		else
		{
			Common.Logging.i(TAG, this+" has null session info() or platform");
		}
		Common.Logging.i(TAG, this+" has AirMediaType set to "+airMediaType);
		waiterForPlayRequestToUser = new Waiter();
		waiterForStopRequestToUser = new Waiter();
		waiterForDisconnectRequestToUser = new Waiter();
		alreadyDisconnected = new AtomicBoolean(false);
	}
	
	public static SessionAirMediaType getAirMediaType(com.crestron.airmedia.receiver.m360.models.AirMediaSession session) { 
		if (session.videoType() == AirMediaSessionVideoType.Miracast)
			return SessionAirMediaType.Miracast;
		if (session.videoType() == AirMediaSessionVideoType.WebRTC)
			return SessionAirMediaType.WebRtc;
		if (session.info() == null)
			return SessionAirMediaType.Undefined;
		switch(session.info().platform) 
		{
		case Windows:
			return (session.videoType() == AirMediaSessionVideoType.Miracast) ? SessionAirMediaType.Miracast : SessionAirMediaType.App;
		case Mac:
		case iOS:
			return SessionAirMediaType.AirPlay;
		case Android:
			return (session.videoType() == AirMediaSessionVideoType.Miracast) ? SessionAirMediaType.Miracast : SessionAirMediaType.App;
		case Chromebook:
			return SessionAirMediaType.WebRtc;
		case Linux:
		case Undefined:
		default:
			return SessionAirMediaType.Undefined;
		}
	}
	
	public void setAirMediaType(com.crestron.airmedia.receiver.m360.models.AirMediaSession s) 
	{
		SessionAirMediaType amType = getAirMediaType(s);
		if (amType != SessionAirMediaType.Undefined  && this.airMediaType == SessionAirMediaType.Undefined)
		{
			this.airMediaType = amType; 
			connectRequest(new Originator(RequestOrigin.Receiver, this));
		}
	}	
	
	public void setAirMediaPlatform(com.crestron.airmedia.receiver.m360.models.AirMediaSession s) 
	{
	    Common.Logging.i(TAG,"setAirMediaPlatform(): "+s.info().platform);
	    setPlatformType(s.info().platform);
	}   
	
	// Receiver initiated requests - handled on thread of requestScheduler - will be called when state changes are signaled by receiver
	// Must be run async so we do not block receiver events thread
	public void doSessionEvent(String r, Originator o, int t, TimeSpan startTime)
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
		else if (r.equalsIgnoreCase("Disconnect") && alreadyDisconnected.get())
		{
		    if (isAlreadyRemoved())
		    {
		        Common.Logging.i(TAG, this+" disconnect request discarded - session is already disconnected");
		        done = true;
		    }
		}
		if (!done)
		{
			Common.Logging.i(TAG, this+" starting processing of "+r+" request");
			boolean success = mCanvas.getCrestore().doSynchronousSessionEvent(this, r, o, t);
			if (success)
			{
				Common.Logging.i(TAG, this+" completed processing of "+r+" request in " + TimeSpan.now().subtract(startTime).toString() + "seconds");
			} else {
				Common.Logging.i(TAG, this+" processing of "+r+" request timedout in " + TimeSpan.now().subtract(startTime).toString() + "seconds");
			}
		}
	}
	
	public void scheduleRequest(String request, Originator originator, int timeout)
	{
		final String r_ = request;
		final int t_ = timeout;
		final Originator o_ = originator;
		final TimeSpan startTime = TimeSpan.now();
		Common.Logging.i(TAG, "Session "+this+" scheduling "+request+" request");
		Runnable r = new Runnable() { @Override public void run() { doSessionEvent(r_, o_, t_, startTime); } };
		mCanvas.getCrestore().sessionScheduler.queue(r, PriorityScheduler.NORMAL_PRIORITY);
	}
	
	public void connectRequest(Originator originator)
	{
		Common.Logging.i(TAG, "Session "+this+" connect request from "+originator);
		mSessionMgr.add(this);
		scheduleRequest("Connect", originator, ConnectTimeout);
	}
	
	public void disconnectRequest(Originator originator)
	{
		if (state == SessionState.Disconnecting) {
			Common.Logging.i(TAG, "Session "+this+" is already disconnecting");
			return;
		}
		setState(SessionState.Disconnecting);
		Common.Logging.i(TAG, "Session "+this+" disconnect request from "+originator);
		scheduleRequest("Disconnect", originator, DisconnectTimeout);
	}
	
	public void playRequest(Originator originator)
	{
		Common.Logging.i(TAG, "Session "+this+" play request from "+originator);
		scheduleRequest("Play", originator, PlayTimeout);
	}
	
	public void stopRequest(Originator originator)
	{
		Common.Logging.i(TAG, "Session "+this+" stop request from "+originator);
		scheduleRequest("Stop", originator, StopTimeout);
	}
	
	// Stop and Play actions below are always initiated by the AVF sending a sessionResponse message.  Therefore these functions
	// will always run on the thread of the sessionResponseScheduler
	// If the session is not in the correct state, a stop or play request may need to be sent to the receiver.  In this case we
	// send the command to the receiver on a different thread - that of the stopPlayScheduler and wait for success to be signaled
	// by the receiver via an event state change or we timeout.
	
	// Play action
	public boolean doPlay()
	{
		if (streamId != -1)
		{
			Common.Logging.i(TAG, "AirMediaSession::doPlay(): AirMediaSession "+this+" already has a valid streamId="+streamId);
			return false;
		}
		setStreamId();
		if (streamId >= 0)
			Common.Logging.i(TAG, "AirMediaSession Session "+this+" got streamId "+streamId);
		else {
			Common.Logging.e(TAG, "doPlay(): ****** AirMediaSession "+this+" invalid streamId "+streamId+" *****");
			return false;
		}
		surface = acquireSurface();
		if (surface == null || !surface.isValid())
		{
			Common.Logging.w(TAG, "AirMediaSession "+this+" got null or invalid surface");
			return false;
		}
		Common.Logging.i(TAG, "AirMediaSession "+this+" using surface "+surface+" isValid="+surface.isValid());
		if (airMediaReceiverSession  == null)
		{
			Common.Logging.i(TAG, "AirMediaSession::doPlay(): AirMediaSession "+this+" has a null receiver session ");
			releaseSurface();
			streamId = -1;
			return false;
		}
		Common.Logging.i(TAG, "AirMediaSession "+this+" attaching surface "+surface);
		airMediaReceiverSession.attach(surface);
		mStreamCtl.mUsedForAirMedia[streamId] = true;
		mStreamCtl.setVideoDimensions(streamId, 0, 0);
		Common.Logging.i(TAG, "AirMediaSession "+this+" send start to csio");
		mStreamCtl.sendAirMediaStart(streamId, true);
		return true;
	}
	
	public void play(Originator originator)
	{
		boolean success = true;
		Common.Logging.i(TAG, "Session "+this+" play entered originator="+originator);
		if (isPlaying() && isVideoPlaying())
			return;
		setState(SessionState.Starting);
		if (getVideoState() != AirMediaSessionStreamingState.Playing)
		{
			success = sendPlayCommandToSourceUser();  // make synchronous must wait for videoState to go to Playing
			if (!success)
			{
				Common.Logging.i(TAG, "Session "+this+" play command to AirMedia receiver timed out");
				if (inReplace())
				{
					Common.Logging.i(TAG, "force release surface for session "+replace.oldSessionId+" on streamId:"+replace.streamId);
					releaseSurface(replace.streamId, replace.oldSessionId);
				}
			}
		}
		if (success && doPlay())
		{
			//mStreamCtl.SendStreamState(StreamState.STARTED, 0);
			setState(SessionState.Playing);
			// in case it was transitioned to pause from the point the play request was issued
			if (videoState == AirMediaSessionStreamingState.Paused)
				setState(SessionState.Paused);
		}
		else
		{
			Common.Logging.w(TAG, "Session "+this+" play command failed");
			if (getVideoState() != AirMediaSessionStreamingState.Stopped)
			{
				setState(SessionState.Playing);
			} else
			{
				setState(SessionState.Stopped);
			}
			originator.failedSessionList.add(this);
		}
	}
	
	public synchronized boolean sendPlayCommandToSourceUser()
	{
		if (airMediaReceiverSession == null)
			return false;
		if (getVideoState() == AirMediaSessionStreamingState.Playing)
			return true;
		// Command sent on separate thread and then we wait for the receiver event thread to invoke the setVideoState - when the video
		// state changes or we timeout.
		Common.Logging.w(TAG, "Session "+this+" calling _play() asynchronously");
		waiterForPlayRequestToUser.prepForWait();
		receiverCmdScheduler.queue(new Runnable() { @Override public void run() { _play(); }; });	
		boolean timeout = waiterForPlayRequestToUser.waitForSignal(TimeSpan.fromSeconds(5));
		Common.Logging.i(TAG, "Session "+this+" play signal received (timeout="+timeout+")");
		return !timeout;
	}
	
	// check we are not already in playing state before calling receiver session play() command
	public void _play()
	{
		if (getVideoState() == AirMediaSessionStreamingState.Playing)
		{
			if (waiterForPlayRequestToUser.signal())
			{
				Common.Logging.i(TAG, "_play(): Session "+this+" signaled playing to waiter");
			}
		}
		else
		{
			Common.Logging.w(TAG, "Session "+this+" sending play command to receiver");
			airMediaReceiverSession.play();
		}
	}
		
	// Stop action
	public boolean doStop()
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
		if (surface != null)
		{
			Common.Logging.i(TAG, "AirMediaSession::doStop(): AirMediaSession "+this+" detaching surface ");
			airMediaReceiverSession.detach();
			Common.Logging.i(TAG, "AirMediaSession::doStop(): AirMediaSession "+this+" releasing surface ");
			releaseSurface();
		} else {
			Common.Logging.i(TAG, "AirMediaSession::doStop(): AirMediaSession "+this+" has a null surface - nothing to detach and release ");
		}
		Common.Logging.i(TAG, "AirMediaSession "+this+" send stop to csio");
		mStreamCtl.sendAirMediaStart(streamId, false);
		mStreamCtl.mUsedForAirMedia[streamId] = false;
		streamId = -1;
		return true;
	}
	
	public void stop(Originator originator)
	{
		Common.Logging.i(TAG, "Session "+this+" stop entered originator="+originator);
		if (isStopped() && isVideoStopped())
		{
			Common.Logging.i(TAG, "Session "+this+" current state is "+state+"  exiting");
			return;
		}
		// save disconnecting state so it can be restored if needed after stop is completed[AM3XX-10005]
		boolean isDisconnecting = isDisconnecting();
		if (isDisconnecting)
			Common.Logging.w(TAG, "Current state is 'Disconnecting' but must do Stop first");
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
		if (doStop())
		{
			//mStreamCtl.SendStreamState(StreamState.STOPPED, 0);
			setState(SessionState.Stopped);
			// restore disconnecting state if we had saved it earlier[AM3XX-10005]
			if (isDisconnecting) {
				Common.Logging.w(TAG, this+" Stop completed, restoring 'Disconnecting' state");
				state = SessionState.Disconnecting;
			}
		}
		else
		{
			Common.Logging.w(TAG, "Session "+this+" stop command failed");
			originator.failedSessionList.add(this);
		}
		stopTime = System.nanoTime();
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
			doForceStop();
			return false;
		}
		// Command sent on separate thread and then we wait for the receiver event thread to invoke the setVideoState - when the video
		// state changes or we timeout.
		Common.Logging.i(TAG, "Session "+this+" calling _stop() asynchronously");
		waiterForStopRequestToUser.prepForWait();
		receiverCmdScheduler.queue(new Runnable() { @Override public void run() { _stop(); }; });	
        boolean timeout = waiterForStopRequestToUser.waitForSignal(TimeSpan.fromSeconds(5));
		Common.Logging.i(TAG, "Session "+this+" stop signal received (timeout="+timeout+")");
		return !timeout;
	}
	
	// check we are not already in stopped state before calling receiver session stop() command
	public void _stop()
	{
		if (getVideoState() == AirMediaSessionStreamingState.Stopped)
		{
			if (waiterForStopRequestToUser.signal())
			{
				Common.Logging.i(TAG, "_stop(): Session "+this+" signaling stopped to waiter");
			}
		}
		else
		{
			Common.Logging.i(TAG, "Session "+this+" sending stop command to receiver");
			airMediaReceiverSession.stop();
		}
	}
	
	public void doForceStop()
	{
		Common.Logging.v(TAG, "doForceStop(): force stop for session "+sessionId()+" entered");
		if (state != SessionState.Disconnecting)
			setState(SessionState.Stopped);
		if (surface != null)
		{
			releaseSurface();
		}
		if (streamId != -1) {
			mStreamCtl.mUsedForAirMedia[streamId] = false;
			streamId = -1;
		}
		Common.Logging.v(TAG, "doForceStop(): force stop for session "+sessionId()+" exit");
	}
	
	public void disconnect(Originator originator)
	{
		Common.Logging.i(TAG, "disconnect(): entered for session "+sessionId()+" state="+state);
		if (state != SessionState.Disconnecting && !alreadyDisconnected.get())
		{
			setState(SessionState.Disconnecting);
			boolean success = sendDisconnectCommandToSourceUser();
			if (!success)
			{
				Common.Logging.i(TAG, "Session "+this+" disconnect command to AirMedia receiver failed or timed out");
				return;
			}
		}
		else
		{
			//ensure we release surface if not done and reset streamId
			doForceStop();
		}
		if (receiverCmdScheduler != null)
		{
		    receiverCmdScheduler.shutdownNow();
		    try {
		        if (!receiverCmdScheduler.awaitTermination(5, TimeUnit.SECONDS))
		            Common.Logging.w(TAG, "disconnect(): scheduler for "+sessionId()+" termination timed out");
		        else
		            Common.Logging.i(TAG, "disconnect(): scheduler for "+sessionId()+" terminated");
		    } catch (Exception ex) {
                Common.Logging.e(TAG, "exception encountered while awaiting termination of sessionScheuler for session: "+sessionId());
                ex.printStackTrace();
		    }
		}
		Common.Logging.i(TAG, "disconnect(): exit for session "+sessionId()+" state="+state);
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
		if (waiterForDisconnectRequestToUser.signal())
		{
			// user has already requested disconnect so simply signal that session is now disconnected
			Common.Logging.i(TAG, "setDisconnected(): Session "+this+" signaled disconnect to waiter");
		}
		else
		{
			final Originator originator = new Originator(RequestOrigin.Receiver, this);
			Common.Logging.i(TAG, "setDisconnected(): Session "+this+" disconnect request");
			alreadyDisconnected.set(true);
			disconnectRequest(originator);
		}
		Common.Logging.i(TAG, "setDisconnected(): Session "+this+" exit");
	}

	public void renewSurfaceRequest()
	{
		Runnable processSurfaceRenewRequest = new Runnable() {
			@Override
			public void run()
			{
				try {
					surfaceRenewRequestResponse = mCanvas.mAirMediaCanvas.service().surfaceRenew(sessionId());
				} catch(android.os.RemoteException ex)
				{
					Common.Logging.e(TAG, "exception encountered while calling surfaceRenew for session: "+sessionId());
					ex.printStackTrace();
				}
				if (surfaceRenewRequestResponse == null || !surfaceRenewRequestResponse.isSucceeded()) {
					Common.Logging.e(TAG, "Canvas App failed to renew surface for session: "+sessionId());
					return;
				}
				final Surface s = surfaceRenewRequestResponse.surface;
				if (s == null || !s.isValid())
				{
					Common.Logging.e(TAG, "renew request response returned null or invalid surface for session: "+sessionId());
					return;
				}
				// schedule processing of the response on the same scheduler as sessionResponses from AVF
				Runnable processSurfaceRenewResponse = new Runnable() { @Override public void run() { renewSurfaceResponse(s); }};
				mCanvas.mCrestore.sessionResponseScheduler.queue(processSurfaceRenewResponse);
			}
		};	
		// schedule processing of the request on the session scheduler
		mCanvas.mCrestore.sessionScheduler.queue(processSurfaceRenewRequest, PriorityScheduler.NORMAL_PRIORITY);
	}
	
	public void renewSurfaceResponse(Surface s)
	{
		if (getVideoState() == AirMediaSessionStreamingState.Stopped)
		{
			Common.Logging.i(TAG, "renewSurfaceResponse(): AirMediaSession "+this+" video state is stopped");
			return;
		}
		if (!(isPlaying() || isPaused()))
		{
			Common.Logging.i(TAG, "renewSurfaceResponse(): AirMediaSession "+this+" state is "+getState());
			return;
		}
		if (airMediaReceiverSession == null)
		{
			Common.Logging.i(TAG, "renewSurfaceResponse(): AirMediaSession "+this+" has a null receiver session ");
			return;
		}
		Common.Logging.i(TAG, "renewSurfaceResponse(): AirMediaSession "+this+" renewed surface="+s);
		this.surface = s;
		super.renewSurface(s);
		Common.Logging.i(TAG, "renewSurfaceResponse(): AirMediaSession "+this+" attaching surface "+s);
		airMediaReceiverSession.attach(surface);
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
		SessionState prevState = getState();
		if (s == AirMediaSessionStreamingState.Stopped)
		{
			if (waiterForStopRequestToUser.signal())
			{
				// user has already requested stop so simply signal that session is now stopped
				Common.Logging.i(TAG, "setVideoState(): Session "+this+" signaled stoppage to waiter");
			}
			else
			{
				if ((prevState == SessionState.Paused) || (prevState == SessionState.Playing) || (prevState == SessionState.Starting))
				{
					final Originator originator = new Originator(RequestOrigin.Receiver, this);
					Common.Logging.i(TAG, "setVideoState(): Session "+this+" stop request");
					if (CresStreamCtrl.isAM3K && airMediaType == SessionAirMediaType.Miracast)
					{
					    Common.Logging.i(TAG, "setVideoState(): Session "+this+" ignore stop request for miracast session on AM3K");
					} else
					    stopRequest(originator);
				} else if (prevState == SessionState.Disconnecting) {
					doForceStop();
				}
			}
		} 
		else if (s == AirMediaSessionStreamingState.Playing)
		{
			if (waiterForPlayRequestToUser.signal())
			{
				// user has already requested play so simply signal that session is now playing
				Common.Logging.i(TAG, "setVideoState(): Session "+this+" signaled playing to waiter");
			}
			else 
			{
				if ((prevState == SessionState.Stopped) || (prevState == SessionState.Stopping))
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
	
	public synchronized Surface acquireSurface()
	{
		return super.acquireSurface();
	}
	
	public synchronized void setSurface(Surface s)
	{
		surface = s;
	}
	
	public synchronized void releaseSurface()
	{
		if (surface != null)
			super.releaseSurface();
		surface = null;
	}
	
	public boolean audioMute(boolean enable)
	{
		if (airMediaReceiverSession == null)
		{
			Common.Logging.i(TAG, "audioMute(): AirMediaSession "+this+" has a null receiver session ");
			return false;
		}
		Common.Logging.i(TAG, "audioMute(): Session "+this+" calling audioMute()");
		// TODO this if/else should be removed and the line below the if/else should be uncommented once Receiver apk implements
		// audioMute as a call to VideoPlayer's audioMute function - if part will move to implementation of 
		// audioMute in VideoPlayer.  Waiting for receiver apk to be added to trunk
		if (getVideoType() == CanvasVideoType.Miracast)
		{
			if (enable) {
				mCanvas.mStreamCtl.setStreamInVolume(0, streamId);
			} else {
				mCanvas.mStreamCtl.setStreamInVolume((int)mCanvas.mStreamCtl.userSettings.getUserRequestedVolume(), streamId);
			}	
			setIsAudioMuted(enable); // will force canvasSourceSessionUpdate
		} else {
			airMediaReceiverSession.audioMute(enable);
		}
		//airMediaReceiverSession.audioMute(enable);
		return true;
	}
	
	public String getAirMediaUserName()
	{
		return (airMediaReceiverSession != null) ? airMediaReceiverSession.username() : "";
	}
	
	public CanvasVideoType getVideoType() {
		if (airMediaReceiverSession == null || airMediaReceiverSession.videoType() == null)
			return CanvasVideoType.Hardware;
		AirMediaSessionVideoType videoType = airMediaReceiverSession.videoType();
		switch (videoType)
		{
		case Mirror:
			return CanvasVideoType.AirPlayMirror;
		case Video:
			return CanvasVideoType.AirPlayVideo;
		case WebRTC:
			return CanvasVideoType.WebRTC;
		case Miracast:
			return CanvasVideoType.Miracast;
		default:
			return CanvasVideoType.Hardware;
		}
	}
	
   public boolean inactiveSession()
   {
      if (state != SessionState.Stopped && state != SessionState.Connecting)  // Treat connecting as stopped
         return false;
      TimeSpan timeSinceStop = TimeSpan.fromNanoseconds(System.nanoTime() - stopTime);
      TimeSpan timeout = TimeSpan.fromMinutes((double)mStreamCtl.userSettings.getAirMediaInactivityTimeout());
      Common.Logging.i(TAG, "Session " + this + " time elapsed since stop " + timeSinceStop
         + "  Inactivity timeout:" + timeout);
      if (timeSinceStop.greaterThan(timeout))
      {
         return true;
      }
      return false;
   }
}

