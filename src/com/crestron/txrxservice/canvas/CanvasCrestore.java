package com.crestron.txrxservice.canvas;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentLinkedQueue;
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
import com.crestron.airmedia.utilities.Common;
import com.crestron.airmedia.utilities.TimeSpan;
import com.crestron.airmedia.utilities.TaskScheduler;
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
	
	Scheduler sessionResponseScheduler_;
	Scheduler sessionResponseScheduler() { return sessionResponseScheduler_; }
	
	private final ReentrantLock sessionEventLock = new ReentrantLock();
	Map<String, TransactionData> transactionMap= new ConcurrentHashMap<String, TransactionData>();

    public static final String TAG = "TxRx CanvasCrestore";
    private com.crestron.cresstoreredis.CresStoreWrapper wrapper = null;
	private Gson gson = null;
	public Gson getGson() { return gson; }

	public class TransactionData 
	{
		Waiter waiter;
		Originator originator;
		SessionEvent se;
		
		public TransactionData(Waiter w, Originator o)
		{
			waiter = w;
			originator = o;
		}
	}
	
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
		gson = new GsonBuilder().setPrettyPrinting().create();
		mCanvas = canvas;
		mSessionMgr = sessionManager;
		mAVF = new SimulatedAVF(this);
		sessionResponseScheduler_ = new Scheduler("sessionResponse");

        Common.Logging.i(TAG, "CanvasCrestore: create Crestore wrapper");
        try {
            wrapper = com.crestron.cresstoreredis.CresStoreWrapper.createInstance();
        } catch (com.crestron.cresstoreredis.CresStoreException e) {
            Log.e(TAG, "failed to create wrapper" , e);
            return;
        }
        com.crestron.cresstoreredis.CresStoreResult rv = wrapper.setIgnoreOwnSets(false);
        CresStoreCallback crestoreCallback = new CresStoreCallback();
        String s = "{\"Device\":{\"AirMedia\":{\"AirMediaCanvas\":{}}}}";
        rv = wrapper.subscribeCallback(s, crestoreCallback);
        if (rv != com.crestron.cresstoreredis.CresStoreResult.CRESSTORE_SUCCESS)
        {
        	Common.Logging.i(TAG,"Could not set up Crestore Callback for subscription to "+s+": " + rv);
        }
        s = "{\"Device\":{\"Internal\":{\"AirMedia\":{\"Canvas\":{}}}}}";
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
        s = "{\"Pending\":{\"Device\":{\"Internal\":{\"AirMedia\":{\"Canvas\":{}}}}}}";
        rv = wrapper.subscribeCallback(s, crestoreCallback);
        if (rv != com.crestron.cresstoreredis.CresStoreResult.CRESSTORE_SUCCESS)
        {
        	Common.Logging.i(TAG,"Could not set up Crestore Callback for subscription to "+s+": " + rv);
        }
	}
	
	public synchronized void doSessionEvent(Session s, String requestedState, Originator originator)
	{
		TransactionData t = new TransactionData(null, originator);
		String transactionId = UUID.randomUUID().toString();
		transactionMap.put(transactionId, t);
		t.se = sendSessionEvent(transactionId, s.sessionId(), requestedState, s.type, s.airMediaType, s.inputNumber);
	}
	
	public synchronized boolean doSessionEventWithCompletionTimeout(Session s, String requestedState, Originator originator, TimeSpan timeout)
	{
		boolean timedout = false;
		Waiter waiter = new Waiter();
		waiter.prepForWait();
		TransactionData t = new TransactionData(waiter, originator);
		TimeSpan startTime = TimeSpan.now();
		String transactionId = UUID.randomUUID().toString();
		transactionMap.put(transactionId, t);
		t.se = sendSessionEvent(transactionId, s.sessionId(), requestedState, s.type, s.airMediaType, s.inputNumber);
		timedout = waiter.waitForSignal(timeout);
		if (timedout)
			Common.Logging.i(TAG,"Timeout seen on "+requestedState+" event for "+s);
		else
			Common.Logging.i(TAG, requestedState+" event for "+s+" completed in "+TimeSpan.now().subtract(startTime).toString()+" seconds");
		return !timedout;
	}

	public synchronized void doSessionEvent(SessionEvent e, Originator originator, Waiter waiter)
	{
		if (waiter != null)
			waiter.prepForWait();
		TransactionData t = new TransactionData(waiter, originator);
		transactionMap.put(e.transactionId, t);
		t.se = sendSessionEvent(e);
	}
	
	public void sendSessionEvent(String transactionId, Session s, String requestedState)
	{
		sendSessionEvent(transactionId, s.sessionId(), requestedState, s.type, s.airMediaType, s.inputNumber);
	}
	
	private SessionEvent sendSessionEvent(String transactionId, String sessionId, String status, SessionType type, SessionAirMediaType amType, int inputNumber)
	{
		SessionEvent e = new SessionEvent(transactionId, sessionId, status, type.toString(), 
				(amType!=null)?amType.toString():null, (inputNumber==0)?null:Integer.valueOf(inputNumber));
		return sendSessionEvent(e);
	}
	
	private SessionEvent sendSessionEvent(SessionEvent e)
	{
		Root root = new Root();
		root.device = new Device();
		root.device.internal = new Internal();
		root.device.internal.airMedia = new InternalAirMedia();
		root.device.internal.airMedia.canvas = new InternalAirMediaCanvas();
		root.device.internal.airMedia.canvas.sessionEvent = e;
		String jsonStr = "{\"Pending\":" + gson.toJson(root) + "}";
		wrapper.set(jsonStr, false);
		return e;
	}
	
	public void setVideoDisplayed(boolean value)
	{
		Root root = new Root();
		root.device = new Device();
		root.device.internal = new Internal();
		root.device.internal.airMedia = new InternalAirMedia();
		root.device.internal.airMedia.osd = new Osd();
		root.device.internal.airMedia.osd.videoDisplayed = Boolean.valueOf(value);
		String jsonStr = "{\"Pending\":" + gson.toJson(root) + "}";
		wrapper.set(jsonStr, false);
	}
	
	public void setCurrentConnectionInfo(String connectionInfo)
	{
		Root root = new Root();
		root.device = new Device();
		root.device.internal = new Internal();
		root.device.internal.airMedia = new InternalAirMedia();
		root.device.internal.airMedia.osd = new Osd();
		root.device.internal.airMedia.osd.currentConnectionInfo = connectionInfo;
		String jsonStr = "{\"Pending\":" + gson.toJson(root) + "}";
		wrapper.set(jsonStr, false);
	}
	
	public void setCurrentWirelessConnectionInfo(String connectionInfo)
	{
		Root root = new Root();
		root.device = new Device();
		root.device.internal = new Internal();
		root.device.internal.airMedia = new InternalAirMedia();
		root.device.internal.airMedia.osd = new Osd();
		root.device.internal.airMedia.osd.currentWirelessConnectionInfo = connectionInfo;
		String jsonStr = "{\"Pending\":" + gson.toJson(root) + "}";
		wrapper.set(jsonStr, false);
	}
	
	public void processSessionStateChange(SessionStateChange s)
	{
		Common.Logging.v(TAG, "Processing Session State Change Message "+gson.toJson(s));
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
					doSessionEvent(session, "Connect", new Originator(RequestOrigin.StateChangeMessage, session));
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
					Common.Logging.i(TAG, "Need to send a play request for session "+session);
				}
				else if (s.state.equalsIgnoreCase("Stop") && session.isPlaying())
				{
					Common.Logging.i(TAG, "Need to send a stop request for session "+session);
				} else if (s.state.equalsIgnoreCase("Disconnect")) {
					if (session.isPlaying()) {
						Common.Logging.i(TAG, "Need to send a stop session "+session+" before disconnecting it");
					}
					Common.Logging.i(TAG, "Removing session " + session + " from sessionManager");
					mSessionMgr.remove(session);
				}
			}
		}
		mSessionMgr.logSessionStates("processSessionStateChange");
	}
	
	public void doSessionResponse(final String transactionId, final List<String> actionList) 
	{
		final com.crestron.airmedia.utilities.TimeSpan timeout = com.crestron.airmedia.utilities.TimeSpan.fromSeconds(60.0);
		final Failure f = new Failure();
		//scheduler().queue(timeout, new Runnable() { @Override public void run() { enqueueSessionResponse(transactionId, actionList); } } );
		sessionResponseScheduler().queue(TAG, "doSessionResponse", timeout, new Runnable() { @Override public void run() { enqueueSessionResponse(transactionId, actionList, f); } } );
	}
		
	public void enqueueSessionResponse(String transactionId, List<String> actionList, Failure failure)
	{
		TransactionData tData = null;
		Originator originator = new Originator(RequestOrigin.Unknown);
		if (transactionId != null)
		{
			tData = transactionMap.get(transactionId);
			if (tData != null)
			   originator = tData.originator;
		} else {
			Common.Logging.w(TAG, "enqueSessionResponse(): no transaction data found");
		}
		
		Common.Logging.i(TAG, "enqueSessionResponse(): ActionList ="+actionList);
		
		for (int i=0; i < actionList.size(); i++)
		{
			Session session;
			String[] tokens = actionList.get(i).split(" ");
			if (Common.isEqualIgnoreCase(tokens[0], "replace"))
			{
				Common.Logging.i(TAG, "enqueSessionResponse(): replacing "+tokens[1]+" with "+tokens[2]);
				(mSessionMgr.getSession(tokens[1])).replace(originator, mSessionMgr.getSession(tokens[2]));
			} 
			else if (Common.isEqualIgnoreCase(tokens[0], "stop"))
			{
				Common.Logging.i(TAG, "enqueSessionResponse(): stop "+tokens[1]);
				session = mSessionMgr.getSession(tokens[1]);
				session.stop(originator);
			}
			else if (Common.isEqualIgnoreCase(tokens[0], "play"))
			{
				Common.Logging.i(TAG, "enqueSessionResponse(): play "+tokens[1]);
				session = mSessionMgr.getSession(tokens[1]);
				session.play(originator);
			}
			else if (Common.isEqualIgnoreCase(tokens[0], "disconnect"))
			{
				Common.Logging.i(TAG, "enqueSessionResponse(): disconnect "+tokens[1]);
				session = mSessionMgr.getSession(tokens[1]);
				if (session.isPlaying())
				{
					Common.Logging.i(TAG, "enqueueSessionResponse(): must stop session "+session+" before disconnecting it");
					session.stop(originator);
				}
				session.disconnect(originator);
				mSessionMgr.remove(session.sessionId());
			}
		}
		// handle reporting of completion of session response
		// send message with Ack for transactionId
		ack(transactionId);
		mSessionMgr.doLayoutUpdate();
		// wake up anyone waiting for completion of this transactionId and remove it from map
		if (tData != null) {
			if (tData.waiter != null) tData.waiter.signal();
			transactionMap.remove(transactionId);
		}

		mSessionMgr.logSessionStates("enqueueSessionResponse");
	}
	
	public void processSessionResponse(SessionResponse response)
	{
		Common.Logging.v(TAG, "Processing Session Response Message "+gson.toJson(response));
        if (response.transactionId != null)
        {
        	// is a response to an earlier SessionEvent sent earlier
        	Common.Logging.i(TAG, "Got session response for transactionId: "+response.transactionId+
        			((response.failureReason!=null)?" failureReason="+response.failureReason.intValue():""));
        	// remove any pending timeout for this transactionId??
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
			for (String id: existingSessionIds) {
				if (!map.containsKey(id))
					disconnectList.add(id);
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
					if (Common.isEqualIgnoreCase(value.state, "Stop") && session.isConnecting())
					{
						Common.Logging.i(TAG, "Changing "+sessionId+" to connected state");
						session.setState(SessionState.Stopped);
					}
				} else if (value.state == null && session != null) {
			    	Common.Logging.i(TAG," Existing sessionId="+sessionId+" incoming state is null");
			    } else if (value.state != null && session == null) {
			    	Common.Logging.i(TAG," sessionId="+sessionId+" state is "+value.state+" but session does not exist in list of sessions");
			    }
			}
			Common.Logging.i(TAG," playList: "+playList);
			Common.Logging.i(TAG," stopList: "+stopList);
			List<String> actionList = new LinkedList<String>();
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
			for (int i=0; i < disconnectList.size(); i++)
			{
				actionList.add("disconnect "+disconnectList.get(i));
			}
			doSessionResponse(response.transactionId, actionList);		
		}
		else
		{
			if (response.failureReason == null)
			{
				Common.Logging.i(TAG, "Empty session response map!!!");
				// should we remove all existing sessions????  How???
			}
			else
			{
				// Need to forward this failure downstream
				nack(response.transactionId, response.failureReason);
				if (response.transactionId != null)
				{
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
		Common.Logging.v(TAG, "Finished processing Session Response Message "+gson.toJson(response));
	}

	private void ack(String transactionId)
	{
		Common.Logging.i(TAG, "Transaction id:"+transactionId+"  success");
		TransactionData tData = transactionMap.get(transactionId);
		if (tData != null)
		{
			
		}
	}
	
	private void nack(String transactionId, int failureReason)
	{
		Common.Logging.i(TAG, "Transaction id:"+transactionId+"  failureReason="+failureReason);
		TransactionData tData = transactionMap.get(transactionId);
		if (tData != null)
		{
			
		}
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
		Root root = new Root();
		root.device = new Device();
		root.device.internal = new Internal();
		root.device.internal.airMedia = new InternalAirMedia();
		root.device.internal.airMedia.canvas = new InternalAirMediaCanvas();
		root.device.internal.airMedia.canvas.sessionStateFeedback = f;
		String jsonStr = "{\"Pending\":" + gson.toJson(root) + "}";
		wrapper.set(jsonStr, false);
		return f;
	}
	
    //TODO remove once real AVF sends the responses
	public void sendSessionResponse(SessionResponse response)
	{
		Root root = new Root();
		root.device = new Device();
		root.device.internal = new Internal();
		root.device.internal.airMedia = new InternalAirMedia();
		root.device.internal.airMedia.canvas = new InternalAirMediaCanvas();
		root.device.internal.airMedia.canvas.sessionResponse = response;
		String jsonStr = "{\"Pending\":" + gson.toJson(root) + "}";
		wrapper.set(jsonStr, false);
	}
	
    public class CresStoreCallback implements com.crestron.cresstoreredis.CresStoreClientCallback {
    	
    	public void message(boolean pending, String json)
    	{
    		Root root = null;
    		boolean parsed=false;
    		    		
    		try
    		{
    			root = gson.fromJson(json, Root.class);
    		} catch (Exception ex) {
    			Common.Logging.i(TAG, "Exception: "+ex);
    			ex.printStackTrace();
    		}

    		if (root == null)
    			Common.Logging.i(TAG, "Got null object for root");
    		else if (root.device == null)
    			Common.Logging.i(TAG, "Got null object for device");

    		if (root.device.internal != null && root.device.internal.airMedia != null && root.device.internal.airMedia.canvas != null) {
    			parsed = true;
    			//Common.Logging.v(TAG, "Device/Internal/Canvas parsed from json string");
    			//Common.Logging.v(TAG, "Device string is "+gson.toJson(root));
    			if (root.device.internal.airMedia.canvas.sessionEvent != null)
    			{
    				mAVF.processSessionEvent(root.device.internal.airMedia.canvas.sessionEvent);
    			}
    			if (root.device.internal.airMedia.canvas.sessionStateChange != null)
    			{
    				processSessionStateChange(root.device.internal.airMedia.canvas.sessionStateChange);
    			}
    			if (root.device.internal.airMedia.canvas.sessionResponse != null)
    			{
    				processSessionResponse(root.device.internal.airMedia.canvas.sessionResponse);
    			}
    		}
    		if (root.device.airMedia != null && root.device.airMedia.canvas != null) {
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
    	}
    	
    	public void update(String json)
    	{
    		boolean pending = false;
			//Common.Logging.i(TAG, "Device Callback: "+json);

    		if (json.contains("Pending"))
    		{
        		int start=json.indexOf("\"Device\":");
        		//Log.v(TAG, "Device found at offset"+start+"   charAt(start)="+json.charAt(start));
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
            	this.type = "AirMedia";
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
        @SerializedName ("Internal")
        Internal internal;
        
//        public Device() {
//        	airMedia = new AirMedia();
//        	internal = new Internal();
//        }
    }
    
    public class Root {
        @SerializedName ("Device")
    	Device device;
    }
}
