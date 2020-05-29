package com.crestron.txrxservice.canvas;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.Collection;
import java.util.List;
import java.util.LinkedList;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.EnumSet;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.net.InetAddress;
import java.net.UnknownHostException;

import android.content.Context;
import android.util.Log;

import com.crestron.airmedia.canvas.channels.ipc.CanvasResponse;
import com.crestron.airmedia.canvas.channels.ipc.CanvasSourceAction;
import com.crestron.airmedia.canvas.channels.ipc.CanvasSourceRequest;
import com.crestron.airmedia.canvas.channels.ipc.CanvasSourceTransaction;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSessionStreamingState;
import com.crestron.airmedia.utilities.Common;
import com.crestron.airmedia.utilities.TimeSpan;
import com.crestron.txrxservice.CresStreamCtrl.StreamState;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

public class CanvasCrestore
{
	com.crestron.txrxservice.CresStreamCtrl mStreamCtl;
	com.crestron.txrxservice.canvas.CresCanvas mCanvas;
	com.crestron.txrxservice.canvas.SessionManager mSessionMgr;
	SimulatedAVF mAVF;
	
    public Scheduler sessionResponseScheduler = null;
    public PriorityScheduler sessionScheduler = null; // handle sessionEvent and sessionStateChange scheduled requests

	private final ReentrantLock sessionEventLock = new ReentrantLock();
	Map<String, TransactionData> transactionMap= new ConcurrentHashMap<String, TransactionData>();

    public static final String TAG = "TxRx.canvas.crestore";
    private com.crestron.cresstoreredis.CresStoreWrapper wrapper = null;
	private Gson gson = null;
	public Gson getGson() { return gson; }

	public class SessionEventReturnStatus {
		boolean timedout;
		
		public SessionEventReturnStatus() {
			timedout = false;
		}
	}
	
	public class TransactionData 
	{
		Waiter waiter;
		Originator originator;
		SessionEvent se;
		SessionResponse sr;
		boolean gotAvfResponse;
		
		public TransactionData(Waiter w, Originator o)
		{
			waiter = w;
			originator = o;
			gotAvfResponse = false;
		}
	}
	
	public enum FailureCodes {
		Success,
		PowerOff,
		InvalidSession,
		MaxSessionCountExceeded,
		MaxFailureCodes
	};
	
	public String[] FailureMessages = new String[] {
		"Success",
		"Power is off",
		"Cannot find session",
		"Maximum session count exceeded",
	};
	
	public class Failure {
		int failureReason;
		String failureMessage;
		
		public Failure()
		{
			failureReason = 0;
			failureMessage = "";
		}
	};
	
	public CanvasCrestore(com.crestron.txrxservice.CresStreamCtrl streamCtl,
			com.crestron.txrxservice.canvas.CresCanvas canvas,
			com.crestron.txrxservice.canvas.SessionManager sessionManager)
	{
		Common.Logging.i(TAG, "Creating CresCanvasCrestore");
	    mStreamCtl = streamCtl;
		gson = new GsonBuilder().create();
		mCanvas = canvas;
		mSessionMgr = sessionManager;
		if (CresCanvas.useSimulatedAVF)
			mAVF = new SimulatedAVF(this);
		sessionResponseScheduler = new Scheduler("TxRx.canvas.crestore.processSesionResponse");
		sessionScheduler = new PriorityScheduler("TxRx.canvas.crestore.sessionScheduler");

        Common.Logging.i(TAG, "CanvasCrestore: create Crestore wrapper");
        try {
            wrapper = com.crestron.cresstoreredis.CresStoreWrapper.createInstance();
        } catch (com.crestron.cresstoreredis.CresStoreException e) {
            Log.e(TAG, "failed to create wrapper" , e);
            return;
        }
        com.crestron.cresstoreredis.CresStoreResult rv = wrapper.setIgnoreOwnSets(!CresCanvas.useSimulatedAVF);
        CresStoreCallback crestoreCallback = new CresStoreCallback();
        String s = "{\"Device\":{\"AirMedia\":{\"AirMediaCanvas\":{}}}}";
        rv = wrapper.subscribeCallback(s, crestoreCallback);
        if (rv != com.crestron.cresstoreredis.CresStoreResult.CRESSTORE_SUCCESS)
        {
        	Common.Logging.i(TAG,"Could not set up Crestore Callback for subscription to "+s+": " + rv);
        }
        s = "{\"Internal\":{\"AirMedia\":{\"Canvas\":{}}}}";
        rv = wrapper.subscribeCallback(s, crestoreCallback);
        if (rv != com.crestron.cresstoreredis.CresStoreResult.CRESSTORE_SUCCESS)
        {
        	Common.Logging.i(TAG,"Could not set up Crestore Callback for subscription to "+s+": " + rv);
        }
        s = "{\"Pending\":{\"Device\":{\"AirMedia\":{\"AirMediaCanvas\":{}}}}}";
        rv = wrapper.subscribeCallback(s, crestoreCallback);
        if (rv != com.crestron.cresstoreredis.CresStoreResult.CRESSTORE_SUCCESS)
        {
        	Common.Logging.i(TAG,"Could not set up Crestore Callback for subscription to "+s+": " + rv);
        }
        s = "{\"Pending\":{\"Internal\":{\"AirMedia\":{\"Canvas\":{}}}}}";
        rv = wrapper.subscribeCallback(s, crestoreCallback);
        if (rv != com.crestron.cresstoreredis.CresStoreResult.CRESSTORE_SUCCESS)
        {
        	Common.Logging.i(TAG,"Could not set up Crestore Callback for subscription to "+s+": " + rv);
        }
        
        // TODO just for debugging - remove eventually: print existing map read from crestore
//        s = "{\"Internal\":{\"AirMedia\":{\"Canvas\":{\"SessionResponse\":{\"SessionResponseMap\":{}}}}}}";
//        String sessionResponseMapString = null;
//        try {
//        	sessionResponseMapString = wrapper.get(false, s);
//        } catch (Exception e) {
//        	Common.Logging.i(TAG, "Exception while reading "+s+" from cresstore");
//        	e.printStackTrace();
//        }
//        if (sessionResponseMapString != null)
//        {
//        	Root r = gson.fromJson(sessionResponseMapString, Root.class);
//        	Common.Logging.i(TAG, "Current Session Response Map in Crestore="+gson.toJson(r));
//        }
        // At startup make session response map empty 
        Root r = getRootedInternalAirMediaCanvas();
        r.internal.airMedia.canvas.sessionResponse = new SessionResponse();
        r.internal.airMedia.canvas.sessionResponse.sessionResponseMap = new HashMap<String, SessionResponseMapEntry>();
        s = gson.toJson(r);
    	Common.Logging.i(TAG, "Clearing Session Response Map in Crestore="+s);
    	wrapper.set(s, true);
    	Common.Logging.i(TAG, "Clearing video displayed flag in Cresstore"+s);
    	setVideoDisplayed(false);
	}
	
	public void restartSchedulers()
	{
		Common.Logging.i(TAG, "restart sessionResponseScheduler");
		sessionResponseScheduler.shutdownNow();
		Common.Logging.i(TAG, "restart sessionScheduler");
		sessionScheduler.shutdownNow();
		
		try {
			sessionResponseScheduler.awaitTermination(60, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		Common.Logging.i(TAG, "clear transaction map");
		transactionMap.clear();
		
		Common.Logging.i(TAG, "create new schedulers");
		sessionResponseScheduler = new Scheduler("TxRx.canvas.crestore.processSesionResponse");
		sessionScheduler = new PriorityScheduler("TxRx.canvas.crestore.sessionScheduler");
		
		Common.Logging.i(TAG, "create new schedulers");
	}
	
	public void doClearAllSessionEvent()
	{
		Originator originator = new Originator(RequestOrigin.Error);
    	SessionEvent e = new CanvasCrestore.SessionEvent(UUID.randomUUID().toString()); 
    	doSynchronousSessionEvent(e, originator, 30);
	}
	
	public boolean doSynchronousSessionEvent(Session s, String requestedState, Originator originator, int timeoutInSecs)
	{
		String transactionId = UUID.randomUUID().toString();
		SessionEvent e = createSessionEvent(transactionId, s, requestedState);
		return doSynchronousSessionEvent(e, originator, timeoutInSecs);
	}
	
	public boolean doSynchronousSessionEvent(final SessionEvent e, final Originator originator, final int timeoutInSecs)
	{
		TimeSpan startTime = TimeSpan.now();
		final SessionEventReturnStatus status = new SessionEventReturnStatus();
		if (!sessionScheduler.inOwnThread())
		{
			// being called from a thread outside the scheduler.  Put the event on the queue and let scheduler process it in order
			Runnable r = new Runnable() {
				@Override 
				public void run() { 
					doSessionEvent(e, originator, timeoutInSecs, status);
				}
			};
			sessionScheduler.queue(r, PriorityScheduler.NORMAL_PRIORITY);
		} else {
			// already called from within a task running on the scheduler.  Process the event inline since putting on queue will deadlock
			doSessionEvent(e, originator, timeoutInSecs, status);
		}
		boolean timedout = status.timedout;
		if (!timedout)
			Common.Logging.i(TAG, "sessionEvent with transactionId="+((e.transactionId==null)?"null":e.transactionId)+" completed in "+TimeSpan.now().subtract(startTime).toString()+" seconds");
		return !timedout;
	}
	
	private synchronized void doSessionEvent(final SessionEvent e, final Originator originator, final int timeoutInSecs, final SessionEventReturnStatus status)
	{
		Waiter waiter = new Waiter();
		TransactionData t = new TransactionData(waiter, originator);
		status.timedout = true;
		if (!mCanvas.avfRestarting.get())
		{
			t.se = e;
			transactionMap.put(e.transactionId, t);
			waiter.prepForWait();
			sendSessionEvent(e);
			status.timedout = waiter.waitForSignal(TimeSpan.fromSeconds(timeoutInSecs));
			processSessionEventCompletion(status.timedout, e);
		} else {
			handleNoAvfResponse(e, originator);
		}
	}
	
	public SessionEvent createSessionEvent(String transactionId, Session s, String requestedState)
	{
		return createSessionEvent(transactionId, s.sessionId(), requestedState, s.type, s.airMediaType, s.inputNumber);
	}
	
	private SessionEvent createSessionEvent(String transactionId, String sessionId, String status, SessionType type, SessionAirMediaType amType, int inputNumber)
	{
		SessionEvent e = new SessionEvent(transactionId, sessionId, status, type.toString(), 
				(amType!=null)?amType.toString():null, (inputNumber==0)?null:Integer.valueOf(inputNumber));
		return e;
	}
	
	private SessionEvent sendSessionEvent(SessionEvent e)
	{
		Root root = getRootedInternalAirMediaCanvas();
		root.internal.airMedia.canvas.sessionEvent = e;
		String jsonStr = "{\"Pending\":" + gson.toJson(root) + "}";
		Common.Logging.i(TAG,"sendSessionEvent: "+jsonStr);
		wrapper.set(jsonStr, false);
		return e;
	}
	
	public void sendCssRestart()
	{
		Root root = getRootedInternalAirMediaCanvasRestartSignal();
		root.internal.airMedia.canvas.restartSignal.css = Boolean.valueOf(true);
		String jsonStr = "{\"Pending\":" + gson.toJson(root) + "}";
		wrapper.set(jsonStr, false);
	}
	
	public void setVideoDisplayed(boolean value)
	{
		Root root = getRootedInternalAirMediaOsd();
		Log.i(TAG, "==== videoDisplayed = "+value+" ====");
		root.internal.airMedia.osd.videoDisplayed = Boolean.valueOf(value);
		wrapper.set(gson.toJson(root), true);
	}
	
	public void setCurrentConnectionInfo(String connectionInfo)
	{
		Root root = getRootedInternalAirMediaOsd();
		root.internal.airMedia.osd.currentConnectionInfo = connectionInfo;
		wrapper.set(gson.toJson(root), true);
	}
	
	public void setCurrentWirelessConnectionInfo(String connectionInfo)
	{
		Root root = getRootedInternalAirMediaOsd();
		root.internal.airMedia.osd.currentWirelessConnectionInfo = connectionInfo;
		wrapper.set(gson.toJson(root), true);
	}
	
	public void markSessionsSentToAvf(SessionEvent e)
	{
		for (Map.Entry<String, SessionEventMapEntry> entry : e.sessionEventMap.entrySet())
		{
			Session session = mSessionMgr.getSession(entry.getKey());
			if (session != null)
			{
				// mark session as sent to avf with an action
				session.sentToAvf.compareAndSet(false, true);
			}
		}
	}

	public void processSessionStateChange(SessionStateChange s)
	{
		Common.Logging.v(TAG, "Processing Session State Change Message "+gson.toJson(s));
		if (!mCanvas.IsAirMediaCanvasUp())
		{
			Common.Logging.i(TAG, "processSessionStateChange(): AirMediaCanvas not up - cannot handle message yet");
			return;
		}
		Session session = mSessionMgr.findSession(s.type, s.userLabel, s.url, (s.inputNumber==null)?0:s.inputNumber.intValue());
		if (s.state.equalsIgnoreCase("Connect"))
		{
			// ensure session does not exist already 
			if (session != null)
			{
				Common.Logging.i(TAG, "Connect command for session that already exists - ignoring");
				return;
			}
			else
			{
				session = com.crestron.txrxservice.canvas.Session.createSession(s.type, s.userLabel, s.url, (s.inputNumber==null)?0:s.inputNumber.intValue(), null);
				if (session != null)
				{
					Common.Logging.i(TAG, "Adding session " + session + " to sessionManager");
					mSessionMgr.add(session);
					doSynchronousSessionEvent(session, "Connect", new Originator(RequestOrigin.StateChangeMessage, session), 10);
				}
				else
				{
					Common.Logging.i(TAG, "Connect not create requested session for connect command");
				}
			}
		} else {
			// find existing session
			if (session == null)
			{
				Common.Logging.i(TAG, s.state+" command for session that can't be found - ignoring");
				return;
			}
			else
			{
				if (s.state.equalsIgnoreCase("Play") && session.isStopped())
				{
					doSynchronousSessionEvent(session, "Play", new Originator(RequestOrigin.StateChangeMessage, session), 10);
				}
				else if (s.state.equalsIgnoreCase("Stop") && session.isPlaying())
				{
					doSynchronousSessionEvent(session, "Stop", new Originator(RequestOrigin.StateChangeMessage, session), 10);
				} else if (s.state.equalsIgnoreCase("Disconnect")) {
					doSynchronousSessionEvent(session, "Disconnect", new Originator(RequestOrigin.StateChangeMessage, session), 10);
					Common.Logging.i(TAG, "Removing session " + session + " from sessionManager");
					mSessionMgr.remove(session);
				}
			}
		}
		mSessionMgr.logSessionStates("processSessionStateChange");
	}
	
	public void cancelSessionResponse(String when, SessionResponse response)
	{
		Common.Logging.v(TAG, "processSessionResponse(): "+when+":Early interrupted exit for Session Response Message "+gson.toJson(response));
	}
	
	public void doSessionResponse(SessionResponse response, List<String> actionList) 
	{
		String transactionId = response.transactionId;
		Common.Logging.w(TAG, "doSessionResponse(): entered for transactionId="+((transactionId==null)?"null":transactionId));
		TransactionData tData = null;
		Originator originator = new Originator(RequestOrigin.Unknown);
		if (transactionId != null)
		{
			tData = transactionMap.get(transactionId);
			if (tData != null)
			   originator = tData.originator;
		} else {
			Common.Logging.w(TAG, "doSessionResponse(): no transaction data found");
		}
		
		Common.Logging.i(TAG, "doSessionResponse(): ActionList ="+actionList);
		
		for (int i=0; i < actionList.size(); i++)
		{
			if (Thread.currentThread().isInterrupted())
			{
				cancelSessionResponse("inSessionLoop", response);
				return;
			}
			Session session;
			String[] tokens = actionList.get(i).split(" ");
			if (Common.isEqualIgnoreCase(tokens[0], "replace"))
			{
				Common.Logging.i(TAG, "doSessionResponse(): replacing "+tokens[1]+" with "+tokens[2]);
				session = mSessionMgr.getSession(tokens[1]);
				if (session != null)
				{
					session.replace(originator, mSessionMgr.getSession(tokens[2]));
				}
				else
				{
					Common.Logging.i(TAG, "doSessionResponse(): cannot find session "+tokens[1]);
				}
			} 
			else if (Common.isEqualIgnoreCase(tokens[0], "stop"))
			{
				Common.Logging.i(TAG, "doSessionResponse(): stop "+tokens[1]);
				session = mSessionMgr.getSession(tokens[1]);
				if (session != null)
				{
					session.stop(originator);
				}
				else
				{
					Common.Logging.i(TAG, "doSessionResponse(): cannot find session "+tokens[1]);
				}
			}
			else if (Common.isEqualIgnoreCase(tokens[0], "play"))
			{
				Common.Logging.i(TAG, "doSessionResponse(): play "+tokens[1]);
				session = mSessionMgr.getSession(tokens[1]);
				if (session != null)
				{
					session.play(originator);
				}
				else
				{
					Common.Logging.i(TAG, "doSessionResponse(): cannot find session "+tokens[1]);
				}
			}
			else if (Common.isEqualIgnoreCase(tokens[0], "disconnect"))
			{
				Common.Logging.i(TAG, "doSessionResponse(): disconnect "+tokens[1]);
				session = mSessionMgr.getSession(tokens[1]);
				if (session != null)
				{
					if (session.isPlaying())
					{
						Common.Logging.i(TAG, "doSessionResponse(): must stop session "+session+" before disconnecting it");
						session.stop(originator);
					}
					Common.Logging.i(TAG, "doSessionResponse(): calling disconnect for "+session);
					session.disconnect(originator);
					Common.Logging.i(TAG, "doSessionResponse(): removing "+session+" from list of sessions in session manager");
					mSessionMgr.remove(session.sessionId());
				}
				else
				{
					if (originator.origin == RequestOrigin.Receiver)
					{
						Common.Logging.i(TAG, "doSessionResponse(): receiver initiated sessionevent for session "+tokens[1]+" - session is already disconnected");
					}
					else
					{
						Common.Logging.i(TAG, "doSessionResponse(): session "+tokens[1]+" - session not found in sessionMgr - already disconected");
					}
				}
			}
		}
		// handle reporting of completion of session response
		Common.Logging.i(TAG, "doSessionResponse(): reportSessionResponseResult");
		reportSessionResponseResult(response);
		Common.Logging.i(TAG, "doSessionResponse(): doLayoutUpdate");
		mSessionMgr.doLayoutUpdate();

		Common.Logging.i(TAG, "doSessionResponse(): logSessionStates");
		mSessionMgr.logSessionStates("doSessionResponse");
		if (Thread.currentThread().isInterrupted())
		{
			cancelSessionResponse("handleFailures", response);
			return;
		}
		// if failed session list is not empty - handle it
		handleSessionResponseSessionFailures(originator, response);
		
		// wake up anyone waiting for completion of this transactionId and remove it from map
		if (tData != null) {
			if (tData.waiter != null) 
				tData.waiter.signal();
		}
	}
	
	public void handleSessionResponseSessionFailures(Originator originator, SessionResponse response)
	{
		if (response.sessionResponseMap != null)
		{
			if (originator != null)
			{
				// handle any failed sessions
				if (originator.failedSessionList != null && originator.failedSessionList.size() > 0)
				{
					SessionEvent se = null;
					for (Session s : originator.failedSessionList)
					{
						SessionResponseMapEntry srme = response.sessionResponseMap.get(s.sessionId());
						if (srme != null)
						{
							Common.Logging.i(TAG, "handleSessionResponseSessionFailures(): failed session "+s+" requested state="+srme.state);
							if ((srme.state != null) && srme.state.equalsIgnoreCase("Play"))
							{
								// play request failed - put event into stopped state
								if (se == null)
								{
									se = new SessionEvent(UUID.randomUUID().toString());
								}
								se.add(s.sessionId(), new SessionEventMapEntry("Stop", s));
							}
						}
					}
					if (se != null)
					{
						Common.Logging.i(TAG, "handleSessionResponseSessionFailures(): sessionEvent="+gson.toJson(se));
						final SessionEvent fse = se;
						final Originator o = new Originator(RequestOrigin.Error);
						sessionScheduler.queue(new Runnable() { @Override public void run() { doSynchronousSessionEvent(fse, o, 30); }; }, PriorityScheduler.HIGH_PRIORITY);	
					}
				}
			}
		}
	}
	
	public void processSessionResponse(SessionResponse response)
	{
		Common.Logging.v(TAG, "----- processSessionResponse: Start processing Session Response Message "+gson.toJson(response));
        if (response.transactionId != null)
        {
        	// is a response to an earlier SessionEvent sent earlier
        	Common.Logging.i(TAG, "Got session response for transactionId: "+response.transactionId+
        			((response.failureReason!=null)?" failureReason="+response.failureReason.intValue():""));
        	// mark AVF response as received
        	avfResponseReceived(response);
        }
        // set the flag to indicate a layout update will be done once session response is processed
		Map<String, SessionResponseMapEntry> map = response.sessionResponseMap;
		if (map != null)
		{
	        mSessionMgr.setPendingLayoutUpdate();
			List<String> playList = new LinkedList<String>();
			List<String> stopList = new LinkedList<String>();
			List<String> disconnectList = new LinkedList<String>();
			List<String> existingSessionIds = mSessionMgr.getSessionIds();
			// Create the disconnect list as sessions that exist but are not in the map received from AVF
			for (String id: existingSessionIds) {
				Session session = mSessionMgr.getSession(id);
				// Disconnect only sessions whose connection sessionEvent has previously been sent to AVF
				if (session != null && session.sentToAvf.get())
				{
					if (!map.containsKey(id))
						disconnectList.add(id);
				}
			}
			for (Map.Entry<String, SessionResponseMapEntry> entry : map.entrySet())
			{
				String sessionId = entry.getKey();
				SessionResponseMapEntry value = entry.getValue();
				String valStr = (value.state==null)?"null":gson.toJson(value);
				Common.Logging.v(TAG, "Key="+sessionId+":  "+valStr);
				Session session = mSessionMgr.getSession(sessionId);

				if (value.state != null && session != null)
				{
					Common.Logging.i(TAG, "session="+((session==null)?"null":session)+" state="+session.state+"   value.state="+value.state);
					// does requested state match the current state
					if (Common.isEqualIgnoreCase(value.state, "Play") && session.isStopped()) 
					{
						Common.Logging.i(TAG, "Adding "+sessionId+" to playList");
						playList.add(sessionId);
					}
					if (Common.isEqualIgnoreCase(value.state, "Stop") && session.isPlaying())
					{
						Common.Logging.i(TAG, "Adding "+sessionId+" to stopList");
						stopList.add(sessionId);
					}
					if (session.isConnecting())
					{
						if (Common.isEqualIgnoreCase(value.state, "Stop"))
						{
							Common.Logging.i(TAG, "Changing "+sessionId+" to connected state");
							session.setState(SessionState.Stopped);
						} 
						else if (Common.isEqualIgnoreCase(value.state, "Play"))
						{
							Common.Logging.i(TAG, "Changing "+sessionId+" to stopped state");
							session.setState(SessionState.Stopped);
							// set it up to be played
							playList.add(sessionId);
						}
					}
				} else if (value.state == null && session != null) {
			    	Common.Logging.i(TAG," Existing sessionId="+sessionId+" incoming state is null");
			    } else if (value.state != null && session == null) {
			    	Common.Logging.i(TAG," sessionId="+sessionId+" state is "+value.state+" but session does not exist in list of sessions");
			    }
			}
			if (response.failureReason != null && response.failureReason != FailureCodes.Success.ordinal())
			{
				// Verify a receiver initiated request puts receiver into a consistent state with AVF request
				if (response.transactionId != null)
				{
					Log.e(TAG, "Got failure message '"+getFailureMessage(response.failureReason)+"' for transactionId="+response.transactionId);
					Originator originator = null;
					TransactionData tData = transactionMap.get(response.transactionId);
					if (tData != null)
					   originator = tData.originator;
					Log.e(TAG, "Originator="+originator.origin);
					if (originator.origin==RequestOrigin.Receiver)
					{
						Session s = originator.getSession();
						if (s == null)
						{
							Log.w(TAG, "Can't find session for receiver initiated event transactionId="+response.transactionId);
						}
						else if (s instanceof AirMediaSession)
						{
							String sessionId = s.sessionId();
							Log.e(TAG, "handling failure of receiver originated response message from session "+s+" with transactionId="+response.transactionId);
							if (!stopList.contains(sessionId) && !playList.contains(sessionId) && !disconnectList.contains(sessionId))
							{
								Log.e(TAG, "handling failure of receiver originated response message from session "+s+" with transactionId="+response.transactionId);
								// session has not been handled in any list yet - ensure its videoState is consistent with
								// the response demanded state
								SessionResponseMapEntry value = map.get(sessionId);
								if (value != null)
								{
									Log.e(TAG, "response message from requests state: "+value.state);
									if (value.state.equalsIgnoreCase("Play") && ((AirMediaSession) s).isVideoStopped())
									{
										playList.add(s.sessionId());
									}
									else if (value.state.equalsIgnoreCase("Stop") && ((AirMediaSession) s).isVideoPlaying())
									{
										stopList.add(s.sessionId());
									}
								}
								else
								{
									Log.e(TAG, "receiver origination session "+s+" not found in session response map");	
								}
							}
						}
						else
						{
							Log.w(TAG, "Session "+s+" receiver initiated event transactionId="+response.transactionId+" is of incorrect sessionType: "+s.type);
						}
					}
				}
			}
			Common.Logging.i(TAG," playList: "+playList);
			Common.Logging.i(TAG," stopList: "+stopList);
			List<String> actionList = new LinkedList<String>();
			for (int i=0; i < disconnectList.size(); i++)
			{
				actionList.add("disconnect "+disconnectList.get(i));
				// add this key to the map with empty value so when written to cresstore it will delete the key from the cresstore map
				map.put(disconnectList.get(i), new SessionResponseMapEntry());
			}		
			int nr = Math.min(playList.size(), stopList.size());
			for (int i=0; i < nr; i++)
			{
				actionList.add("replace "+stopList.get(i)+" "+playList.get(i));
			}
			for (int i=0; i < nr; i++)
			{
				stopList.remove(i);
				playList.remove(i);
			}
			for (int i=0; i < stopList.size(); i++)
			{
				actionList.add("stop "+stopList.get(i));
			}
			for (int i=0; i < playList.size(); i++)
			{
				actionList.add("play "+playList.get(i));
			}
			// First update crestore map
			if (Thread.currentThread().isInterrupted())
			{
				cancelSessionResponse("updateCresstoreMap", response);
				return;
			}
			//updateCresstoreMap(map);

			doSessionResponse(response, actionList);		
		}

		Common.Logging.v(TAG, "----- Finished processing Session Response Message "+gson.toJson(response));
	}
	
	public void updateCresstoreMap(Map<String, SessionResponseMapEntry> map)
	{
		// "Out of an abundance of caution" add deletion for any session that existed in Cresstore version of map but is not in this map
		Map<String, SessionResponseMapEntry> cresstoreMap = null;
		String s = "{\"Internal\":{\"AirMedia\":{\"Canvas\":{\"SessionResponse\":{}}}}}";
		String sessionResponseString = null;
		try {
			sessionResponseString = wrapper.get(false, s);
		} catch (Exception e) {
			Common.Logging.i(TAG, "Exception while reading "+s+" from cresstore");
			e.printStackTrace();
		}
		if (sessionResponseString != null)
		{
			Root r = gson.fromJson(sessionResponseString, Root.class);
			Common.Logging.v(TAG, "Cresstore Session Response in Crestore="+gson.toJson(r));
			if (r.internal.airMedia.canvas.sessionResponse.sessionResponseMap != null)
				cresstoreMap = r.internal.airMedia.canvas.sessionResponse.sessionResponseMap;
		}
		if (cresstoreMap != null && !cresstoreMap.isEmpty())
		{
			for (Map.Entry<String, SessionResponseMapEntry> entry : cresstoreMap.entrySet())
			{
				if (!map.containsKey(entry.getKey()))
				{				
					Common.Logging.w(TAG, "Cresstore Session Response Map key "+entry.getKey()+ " not found in incoming map - insert delete entry");
					map.put(entry.getKey(), new SessionResponseMapEntry());
				}
			}
		}
				
		// Now update cresstore with the map
		Root localroot = getRootedInternalAirMediaCanvas();
		localroot.internal.airMedia.canvas.sessionResponse = new SessionResponse();
		localroot.internal.airMedia.canvas.sessionResponse.sessionResponseMap = map;
		Common.Logging.v(TAG, "Writing "+gson.toJson(localroot)+" to cresstore");
		wrapper.set(gson.toJson(localroot), true);
	}

	private void ack(String transactionId)
	{
		if (transactionId == null)
			return;
		Common.Logging.i(TAG, "ack(): Transaction id:"+transactionId+"  success");
		TransactionData tData = transactionMap.get(transactionId);
		if (tData != null)
		{
			
		}
	}
	
	private void nack(String transactionId, int failureReason)
	{
		Common.Logging.i(TAG, "nack(): Transaction id:"+transactionId+"  failureReason="+failureReason);
		TransactionData tData = transactionMap.get(transactionId);
		if (tData != null)
		{
			
		}
	}
	
	public String getFailureMessage(int failureReason)
	{
		if (failureReason > FailureCodes.Success.ordinal() && failureReason < FailureCodes.MaxFailureCodes.ordinal())
		{
			return FailureMessages[failureReason];
		}
		else
			return "Unknown failure code "+failureReason;
	}
	
	private void reportSessionResponseResult(SessionResponse response)
	{
		String transactionId = response.transactionId;
		if ((response.failureReason == null) || response.failureReason == FailureCodes.Success.ordinal())
		{
			if (transactionId != null)
			{
				// send message with Ack for transactionId
				ack(transactionId);
			}
		}
		else
		{
			// Need to forward this failure downstream
			if (response.transactionId != null)
			{
				nack(response.transactionId, response.failureReason);
				Originator originator = null;
				TransactionData tData = transactionMap.get(response.transactionId);
				if (tData != null)
				   originator = tData.originator;
				if (originator!=null)
				{
					if (originator.origin == RequestOrigin.StateChangeMessage)
					{
						Session s = originator.getSession();
						sendSessionFeedbackMessage(s.type, s.state, s.userLabel, response.failureReason);
					}
					else if (originator.origin == RequestOrigin.CanvasSourceRequest)
					{
						CanvasSourceResponse r = originator.getCanvasResponse();
						if (r != null)
						{
							r.setErrorCode(response.failureReason);
						}
					}
				}
				transactionMap.remove(response.transactionId);
			}
		}
	}
	
	private void avfResponseReceived(SessionResponse response)
	{
		if (response.transactionId != null)
		{
			TransactionData tData = transactionMap.get(response.transactionId);
			if (tData != null)
			{
				tData.gotAvfResponse = true;
				if (tData.se != null)
					markSessionsSentToAvf(tData.se);
				tData.sr = response;
			}
		}
	}
	
	private void processSessionEventCompletion(boolean timedout, SessionEvent e)
	{
		String transactionId = e.transactionId;
		if (transactionId == null)
		{
			// should never have a null transactionId
			if (timedout)
				Common.Logging.i(TAG,"Timeout seen on sessionEvent with null transactionId");
			return;
		}
		if (!timedout)
		{
			// Normal exit - no timeout just remove from map
			transactionMap.remove(transactionId);
			return;
		}
		
		// timedout - try to handle errors
		Originator originator = new Originator(RequestOrigin.Unknown);
		SessionResponse response = null;
		TransactionData tData = transactionMap.get(transactionId);
		if (tData != null)
		{
			originator = tData.originator;
			response = tData.sr;
		}
		if (tData != null && !tData.gotAvfResponse)
		{
			// Never got AVF response for this transaction
			handleNoAvfResponse(e, originator);
		}
		else
		{
			handleSessionEventTimeout(e, tData, originator, response);
		}
		transactionMap.remove(e.transactionId);
	}
	
	private void handleNoAvfResponse(SessionEvent e, Originator originator)
	{
		boolean avfHasStarted = mCanvas.avfHasStarted.get();
		if (!mCanvas.avfRestarting.get())
			Common.Logging.w(TAG, "handleNoAvfResponse(): ******* no AVF response for SessionEvent transactionId="+e.transactionId);
		else
			Common.Logging.w(TAG, "handleNoAvfResponse(): ******* AVF is restarting and cannot process SessionEvent transactionId="+e.transactionId);
		// unroll event - mainly connect requests should be rejected as disconnected
        Map<String, SessionEventMapEntry> sessionEventMap = e.sessionEventMap;
		for (Map.Entry<String, SessionEventMapEntry> entry : sessionEventMap.entrySet())
		{
			Session s = mSessionMgr.findSession(entry.getKey());
			if (s != null)
			{
				if (entry.getValue().state.equalsIgnoreCase("Connect"))
				{
					if ((s.type == SessionType.HDMI || s.type == SessionType.DM))
					{
						// If AVF has started and is not in process of restarting disconnect the HDMI/DM session if no response
						if (avfHasStarted && !mCanvas.avfRestarting.get())
						{
							Common.Logging.i(TAG, "handleNoAvfResponse(): Disconnecting HDMI/DM session "+s.sessionId()+" due to no AVF response");
							s.disconnect(originator);
						}
					}
					else // non HDMI or DM session
					{
						Common.Logging.i(TAG, "handleNoAvfResponse(): Disconnecting session "+s.sessionId()+" due to no AVF response");
						s.disconnect(originator);
					}
				}
				else if (entry.getValue().state.equalsIgnoreCase("Stop"))
				{
					if (originator != null && originator.origin == RequestOrigin.Receiver)
					{
						// session has been stopped already must follow through and release surface and let canvas know
						Common.Logging.i(TAG, "handleNoAvfResponse(): Stopping session "+s.sessionId()+" due to no AVF response");
						s.stop(originator);
					}					
				}
				else if (entry.getValue().state.equalsIgnoreCase("Disconnect"))
				{
					Common.Logging.i(TAG, "handleNoAvfResponse(): Disconnecting session "+s.sessionId()+" due to no AVF response");
					if (s.isPlaying())
					{
						s.stop(originator);
					}
					s.disconnect(originator);
				}
			}
		}
	}
	
	private void handleSessionEventTimeout(SessionEvent e, TransactionData tData, Originator originator, SessionResponse response)
	{
		Common.Logging.w(TAG, "processSessionEventCompletion(): ******* timeout while processing AVF response for transactionId="+e.transactionId);
		// Assume we stop the sessionResponse somehow
		sessionResponseScheduler.cancel();
		// create a syncevent to be sent to AVF to bring it back into sync
    	final SessionEvent syncEvent = new SessionEvent(UUID.randomUUID().toString());
		for (Map.Entry<String, SessionResponseMapEntry> entry : response.sessionResponseMap.entrySet())
		{
			// for each event in the response map if state does not agree with requested state
			Session s = mSessionMgr.findSession(entry.getKey());
			if (s != null)
			{
				if (entry.getValue().state.equalsIgnoreCase("Play") && !s.isPlaying())
				{
					Common.Logging.i(TAG, "handleSessionEventTimeout(): Force Stopping session "+s.sessionId()+" due to session event timeout");
					s.stop(new Originator(RequestOrigin.Error));
					syncEvent.add(s.sessionId(), new SessionEventMapEntry("Stop", s));
				}
				else if (entry.getValue().state.equalsIgnoreCase("Stop") && !s.isStopped())
				{
					Common.Logging.i(TAG, "handleSessionEventTimeout(): Stopping session "+s.sessionId()+" due to session event timeout");
					s.stop(new Originator(RequestOrigin.Error));
					syncEvent.add(s.sessionId(), new SessionEventMapEntry("Stop", s));
				}
			}
		}
		Common.Logging.i(TAG, "handleSessionEventTimeout(): queueing "+gson.toJson(syncEvent)+" due to session event timeout");
		Runnable r = new Runnable() { 
			@Override public void run() { 
				final SessionEventReturnStatus status = new SessionEventReturnStatus();
				doSessionEvent(syncEvent, new Originator(RequestOrigin.Error), 30, status); 
			} 
		};
		sessionScheduler.queue(r, PriorityScheduler.NORMAL_PRIORITY);
	}
	
	public void sendSessionFeedbackMessage(SessionType type, SessionState state, String userLabel, Integer failureReason) {
		SessionStateFeedback f = new SessionStateFeedback();
		f.type = type.toString();
		f.state = SessionState.feedbackString(state.getValue());
		f.userLabel = userLabel;
		f.failureReason = failureReason;
		sendSessionStateFeedback(f);
	}

	private SessionStateFeedback sendSessionStateFeedback(SessionStateFeedback f)
	{
		Root root = getRootedInternalAirMediaCanvas();
		root.internal.airMedia.canvas.sessionStateFeedback = f;
		String jsonStr = "{\"Pending\":" + gson.toJson(root) + "}";
		wrapper.set(jsonStr, false);
		return f;
	}
	
    //TODO remove once real AVF sends the responses
	public void sendSessionResponse(SessionResponse response)
	{
		Root root = getRootedInternalAirMediaCanvas();
		root.internal.airMedia.canvas.sessionResponse = response;
		String jsonStr = "{\"Pending\":" + gson.toJson(root) + "}";
		wrapper.set(jsonStr, false);
	}
	
    public class CresStoreCallback implements com.crestron.cresstoreredis.CresStoreClientCallback {
    	
    	public void message(boolean pending, String json)
    	{
    		    		
    		try
    		{
    			final Root root = gson.fromJson(json, Root.class);
        		boolean parsed=false;

        		if (root == null)
        			Common.Logging.i(TAG, "Got null object for root");
        		else if (root.device == null && root.internal == null)
        			Common.Logging.i(TAG, "Got null object for device and internal");

        		if (root.internal != null && root.internal.airMedia != null && root.internal.airMedia.canvas != null) {
        			parsed = true;
        			//Common.Logging.v(TAG, "Internal/AirMedia/Canvas parsed from json string");
        			//Common.Logging.v(TAG, "JSON string is "+gson.toJson(root));
        			if (root.internal.airMedia.canvas.sessionEvent != null)
        			{
        				if (CresCanvas.useSimulatedAVF)
        					mAVF.processSessionEvent(root.internal.airMedia.canvas.sessionEvent);
        				else
        					Common.Logging.i(TAG, "Received a Session Event message - should not happen in normal mode");
        			}
        			if (root.internal.airMedia.canvas.sessionStateChange != null)
        			{
    					// Session response messages are queued on a Scheduler so we do not block incoming message from cresstore
    					final SessionStateChange sessionStateChange = root.internal.airMedia.canvas.sessionStateChange;
    					// Now process session state change
    					Common.Logging.i(TAG,"----- processSessionStateChange job to scheduler: "+gson.toJson(sessionStateChange));
    					sessionScheduler.queue(new Runnable() { 
    						@Override public void run() { 
    							processSessionStateChange(sessionStateChange); 
    						}; 
    					}, PriorityScheduler.NORMAL_PRIORITY);
        			}
        			if (root.internal.airMedia.canvas.sessionResponse != null)
        			{
        				if (pending)
        				{
        					mCanvas.avfHasStarted.compareAndSet(false, true);
        					// Session response messages are queued on a Scheduler so we do not block incoming message from cresstore
        					final SessionResponse sessionResponse = root.internal.airMedia.canvas.sessionResponse;
        					// Now process response
        					Common.Logging.i(TAG,"----- processSessionResponse job to scheduler: "+gson.toJson(sessionResponse));
        					sessionResponseScheduler.queue(new Runnable() { 
        						@Override public void run() { 
        							processSessionResponse(sessionResponse); 
        						}; 
        					});
        				}
        				else if (!CresCanvas.useSimulatedAVF)
        				{
        					Common.Logging.i(TAG, "Received a Session Response message with no pending flag - should not happen in normal mode");
        				}
        			}
        			if (root.internal.airMedia.canvas.restartSignal != null)
        			{
        				if (pending)
        				{
        					Boolean avfRestart = root.internal.airMedia.canvas.restartSignal.avf;
        					
        					if (avfRestart != null)
        					{
            					Common.Logging.i(TAG, "Received a AVF restartSignal="+avfRestart+" message");
            					mCanvas.handleAvfRestart(avfRestart.booleanValue());
        					}
        				}
        			}
        		}
        		if (root.device != null && root.device.airMedia != null && root.device.airMedia.canvas != null) {
        			parsed = true;
        			Common.Logging.i(TAG, "Device/AirMedia/AirMediaCanvas parsed from json string");
        			Common.Logging.i(TAG, "Device string is "+gson.toJson(root));
        			if (root.device.airMedia.canvas.canvasUserLayoutChangeRequest != null)
        			{
        				Common.Logging.i(TAG, "Canvas User Layout Change Request "+gson.toJson(root.device.airMedia.canvas.canvasUserLayoutChangeRequest));
        				Map<String, SessionPositionEntry> map = root.device.airMedia.canvas.canvasUserLayoutChangeRequest.sessionPositions;
        				if (map != null)
        				{
        					if (!map.isEmpty())
        					{
        						for (Map.Entry<String, SessionPositionEntry> entry : map.entrySet())
        						{
        							SessionPositionEntry value = entry.getValue();
        							String valStr = gson.toJson(value);
        							Common.Logging.i(TAG, "Key="+entry.getKey()+":  "+valStr);
        						}
        					} else {
        						Common.Logging.i(TAG, "Empty session position map!!! - destroy all sessons");
        					}
        				}
        				else
        				{
        					Common.Logging.i(TAG, "No session position map!!!");
        				}
        			}
        			if (root.device.airMedia.canvas.canvasUserResponse != null)
        			{
        				Common.Logging.i(TAG, "Canvas User Response "+gson.toJson(root.device.airMedia.canvas.canvasUserResponse));
        			}
        			if (root.device.airMedia.canvas.canvasUserLayoutUpdate != null)
        			{
        				Common.Logging.i(TAG, "Canvas User Layout Update "+gson.toJson(root.device.airMedia.canvas.canvasUserLayoutUpdate));
        				Map<String, SessionLayoutEntry> map = root.device.airMedia.canvas.canvasUserLayoutUpdate.sessionLayout;
        				if (map != null)
        				{
        					for (Map.Entry<String, SessionLayoutEntry> entry : map.entrySet())
        					{
        						SessionLayoutEntry value = entry.getValue();
        						String valStr = gson.toJson(value);
        						Common.Logging.i(TAG, "Key="+entry.getKey()+":  "+valStr);
        					}
        				}
        				else
        				{
        					Common.Logging.i(TAG, "Empty session layout map!!!");
        				}
        			}
        		}

        		if (!parsed) {
        			Common.Logging.i(TAG, "Device could not be parsed from json string");
        			Common.Logging.i(TAG, "Device string is "+gson.toJson(root));
        		}
    		} catch (Exception ex) {
    			Common.Logging.i(TAG, "CresStoreCallback(): Could not deserialize "+json);
    			Common.Logging.i(TAG, "Exception: "+ex);
    			ex.printStackTrace();
    		}
    	}
    	
    	public void update(String json)
    	{
    		boolean pending = false;
			//Common.Logging.i(TAG, "Device Callback: "+json);

    		if (json.contains("\"Pending\":"))
    		{
        		int start=json.indexOf("\"Device\":");
        		if (start != -1) {
        			//Log.v(TAG, "Device found at offset"+start+"   charAt(start)="+json.charAt(start));
        		} else {
        			start=json.indexOf("\"Internal\":");
        			if (start != -1) {
            			//Log.v(TAG, "Internal found at offset"+start+"   charAt(start)="+json.charAt(start));
        			} else {
        				Common.Logging.w(TAG, "Could not decipher Device Callback: "+json);
        				return;
        			}
        		}
        		for(start--; (start>=0) && Character.isWhitespace(json.charAt(start)); start--)
        		{
            		//Log.v(TAG, "start="+start+"   charAt(start)="+json.charAt(start));
        		}
        		if (json.charAt(start) != '{')
        		{
        			Log.e(TAG, "Device is not preceeded by '{' in json string ="+json);
        			return;
        		}
        		int end=json.lastIndexOf("}");
        		//Log.v(TAG, "update(): start="+start+"  end="+end);
        		String subStr = json.substring(start,end);
        		pending = true;
        		json = subStr;
    		}

    		message(pending, json);   		
    	}
    	
    	
    	public void onError()
    	{
    		Common.Logging.i(TAG, "Callback - onError");
    	}
    }
    
	private Root getRootedInternalAirMedia()
	{
		Root root = new Root();
		root.internal = new Internal();
		root.internal.airMedia = new InternalAirMedia();
		return root;
	}
	
	private Root getRootedInternalAirMediaCanvas()
	{
		Root root = getRootedInternalAirMedia();
		root.internal.airMedia.canvas = new InternalAirMediaCanvas();
		return root;
	}
	
	private Root getRootedInternalAirMediaCanvasRestartSignal()
	{
		Root root = getRootedInternalAirMediaCanvas();
		root.internal.airMedia.canvas.restartSignal = new RestartSignal();
		return root;
	}
	
	private Root getRootedInternalAirMediaOsd()
	{
		Root root = getRootedInternalAirMedia();
		root.internal.airMedia.osd = new Osd();
		return root;
	}
	
    public class SessionEventMapEntry {
        @SerializedName ("State")
    	String state;
        @SerializedName ("Type")
    	String type;
        @SerializedName ("AirMediaType")
    	String airMediaType;
        @SerializedName ("InputNumber")
    	Integer inputNumber;
        
        public SessionEventMapEntry(String state, String type, String amType, Integer inputNumber)
        {
        	this.state = state;
        	this.type = type;
        	this.airMediaType = amType;
        	this.inputNumber = inputNumber;
        }
        
        public SessionEventMapEntry(String state, SessionType type, SessionAirMediaType amType, int inputNumber)
        {
        	if (type == SessionType.AirMedia)
        	{
            	this.state = state;
            	this.type = "AirMedia";
            	this.airMediaType = amType.toString();
            	this.inputNumber = null;
        	}
        	else
        	{
            	this.state = state;
            	this.type = type.toString();
            	this.airMediaType = null;
            	this.inputNumber = inputNumber;
        	}
        }
        
        public SessionEventMapEntry(String state, Session s)
        {
        	this(state, s.getType(), s.getAirMediaType(), s.getInputNumber());
        }
    }
    
    public class SessionEvent {
        @SerializedName ("TransactionId")
        String transactionId;
        @SerializedName ("SessionEventMap")
        Map<String, SessionEventMapEntry> sessionEventMap;
        
        public SessionEvent(String transactionId, String sessionId, String status, String type, String amType, Integer inputNumber)
        {
        	this.transactionId = transactionId;
        	this.sessionEventMap = new HashMap<String, SessionEventMapEntry>();
        	this.sessionEventMap.put(sessionId, new SessionEventMapEntry(status, type, amType, inputNumber));
        }
        
        public SessionEvent(String transactionId)
        {
        	this.transactionId = transactionId;
        	this.sessionEventMap = new HashMap<String, SessionEventMapEntry>();        	
        }
        
        public void add(String sessionId, SessionEventMapEntry entry)
        {
        	this.sessionEventMap.put(sessionId,  entry);
        }
    }
    
    public SessionEvent sourceRequestToEvent(CanvasSourceRequest request, CanvasSourceResponse response)
    {
    	// first convert the request into a sessionEvent message
    	if (request.transactions == null || request.transactions.size() == 0)
    	{
    		response.setErrorCode(CanvasResponse.ErrorCodes.EmptyRequest);
    		return null;
    	}

    	SessionEvent e = new CanvasCrestore.SessionEvent(UUID.randomUUID().toString()); 
    	for (int i=0; i < request.transactions.size(); i++) 
    	{
    		CanvasSourceTransaction t = request.transactions.get(i);
    		String sessionId = t.sessionId;
			if (t.action == CanvasSourceAction.Mute || t.action == CanvasSourceAction.UnMute ||
					t.action == CanvasSourceAction.Pause)
			{
				Log.i(TAG,"sourceRequestToEvent(): sessionId="+sessionId+" requesting action="+t.action.toString()+" not implemented");
				response.setErrorCode(CanvasResponse.ErrorCodes.UnsupportedAction);
				return null;
			}

    		Session session = mSessionMgr.getSession(sessionId);
    		if (session != null)
    		{
    			e.add(sessionId, new SessionEventMapEntry(t.action.toString(), session));
    		}
    		else
    		{
    			Log.w(TAG, "sourceRequestToEvent(): reference to sessionId="+sessionId+" for a session that does not exist in list of current sessions");
    			response.setErrorCode(CanvasResponse.ErrorCodes.InvalidSessionId);
    			return null;
    		}
    	}
    	return e;
    }
    

    
    public class SessionResponseMapEntry {
        @SerializedName ("State")
    	String state;
        @SerializedName ("Url")
    	String url;
    }
    
    public class SessionResponse {
        @SerializedName ("TransactionId")
        String transactionId;
        @SerializedName ("SessionResponseMap")
        Map<String, SessionResponseMapEntry> sessionResponseMap;
        @SerializedName ("FailureReason")
        Integer failureReason;
    }
    
    public class SessionStateChange {
        @SerializedName ("Type")
        String type;
        @SerializedName ("State")
        String state;
        @SerializedName ("UserLabel")
        String userLabel;
        @SerializedName ("Url")
        String url;  
        @SerializedName ("InputNumber")
        Integer inputNumber; 
    }
    
    public class SessionStateFeedback {
        @SerializedName ("Type")
        String type;
        @SerializedName ("State")
        String state;
        @SerializedName ("UserLabel")
        String userLabel;
        @SerializedName ("FailureReason")
        Integer failureReason;
    }
    
    public class RestartSignal {
        @SerializedName ("Avf")
        Boolean avf;
        @SerializedName ("Css")
        Boolean css;
    }
    
    public class SessionWindow {
    	@SerializedName ("Left")
    	Integer left;
    	@SerializedName ("Top")
    	Integer top;
    	@SerializedName ("Width")
    	Integer width;
    	@SerializedName ("Height")
    	Integer height;
    	@SerializedName ("Zorder")
    	Integer zOrder;
    }
    
    public class SessionPositionEntry {
    	@SerializedName ("WindowPosition")
    	String windowPosition;
    	@SerializedName ("SessionWindow")
    	SessionWindow sessionWindow;
    }
    
    public class CanvasUserLayoutChangeRequest
    {
        @SerializedName ("TransactionId")
        String transactionId;
        @SerializedName ("LayoutSequenceNumber")
        Integer layoutSequenceNumber;
        @SerializedName ("SessionPositions")
        Map<String, SessionPositionEntry> sessionPositions;
    }
    
    public class CanvasUserResponse
    {
        @SerializedName ("TransactionId")
        String transactionId;
        @SerializedName ("ResponseType")
        String responseType;
        @SerializedName ("FailureReason")
        Integer failureReason;
    }
    
    public class SessionLayoutEntry {
    	@SerializedName ("Type")
    	String type;
    	@SerializedName ("AirMediaType")
    	String airMediaType;
    	@SerializedName ("UserLabel")
    	String userLabel;
        @SerializedName ("Width")
        Integer width;
        @SerializedName ("Height")
        Integer height;
    	@SerializedName ("WindowPosition")
    	String windowPosition;
    	@SerializedName ("SessionWindow")
    	SessionWindow sessionWindow;
    }
    
    public class CanvasUserLayoutUpdate
    {
        @SerializedName ("LayoutSequenceNumber")
        Integer layoutSequenceNumber;
        @SerializedName ("DisplayWidth")
        Integer displayWidth;
        @SerializedName ("DisplayHeight")
        Integer displayHeight;
        Map<String, SessionLayoutEntry> sessionLayout;
    }
    
    public class AirMediaCanvas {
        @SerializedName ("CanvasUserLayoutChangeRequest")
    	CanvasUserLayoutChangeRequest canvasUserLayoutChangeRequest;
        @SerializedName ("CanvasUserResponse")
        CanvasUserResponse canvasUserResponse;
        @SerializedName ("CanvasUserLayoutUpdate")
        CanvasUserLayoutUpdate canvasUserLayoutUpdate;
    }
    
    public class AirMedia {
        @SerializedName ("AirMediaCanvas")
    	AirMediaCanvas canvas;
        
        public AirMedia() {
        	canvas = new AirMediaCanvas();
        }
    }
    
    public class Osd {
    	@SerializedName ("CurrentConnectionInfo")
    	String currentConnectionInfo;
    	@SerializedName ("CurrentWirelessConnectionInfo")
    	String currentWirelessConnectionInfo;
    	@SerializedName ("VideoDisplayed")
    	Boolean videoDisplayed;
    }
    
    public class InternalAirMediaCanvas {
        @SerializedName ("SessionEvent")
    	SessionEvent sessionEvent;
        @SerializedName ("SessionResponse")
    	SessionResponse sessionResponse;
        @SerializedName ("SessionStateChange")
        SessionStateChange sessionStateChange;
        @SerializedName ("SessionStateFeedback")
        SessionStateFeedback sessionStateFeedback;
        @SerializedName ("RestartSignal")
        RestartSignal restartSignal;
    }
    
    public class InternalAirMedia {
    	@SerializedName ("Osd")
    	Osd osd;
    	@SerializedName ("Canvas")
    	InternalAirMediaCanvas canvas;
    }
    
    public class Internal {
        @SerializedName ("AirMedia")
        InternalAirMedia airMedia;
        
        public Internal() {
        	airMedia = new InternalAirMedia();
        }
    }
    
    public class Device {
        @SerializedName ("AirMedia")
    	AirMedia airMedia;
    }
    
    public class Root {
        @SerializedName ("Device")
    	Device device;
        @SerializedName ("Internal")
        Internal internal;
    }
}
