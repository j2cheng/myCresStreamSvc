package com.crestron.txrxservice.canvas;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.Collection;
import java.util.List;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import com.crestron.airmedia.canvas.channels.ipc.CanvasPlatformType;
import com.crestron.airmedia.canvas.channels.ipc.CanvasResponse;
import com.crestron.airmedia.canvas.channels.ipc.CanvasSessionState;
import com.crestron.airmedia.canvas.channels.ipc.CanvasSourceAction;
import com.crestron.airmedia.canvas.channels.ipc.CanvasSourceSession;
import com.crestron.airmedia.canvas.channels.ipc.CanvasSourceTransaction;
import com.crestron.airmedia.canvas.channels.ipc.CanvasSourceType;
import com.crestron.airmedia.receiver.m360.models.AirMediaSession;
import com.crestron.airmedia.utilities.Common;
import com.crestron.airmedia.utilities.TimeSpan;
import com.crestron.txrxservice.canvas.CanvasCrestore.SessionEvent;
import com.crestron.txrxservice.canvas.CanvasCrestore.SessionEventMapEntry;

import android.util.Log;

//import com.crestron.txrxservice.canvas.Session;

public class SessionManager
{
	com.crestron.txrxservice.canvas.CresCanvas mCanvas;
    public static final String TAG = "TxRx.canvas.sessionmanager"; 
    private final Object lock_ = new Object();
    private final Object layoutUpdateLock = new Object();
    private final List<Session> sessions_ = new LinkedList<Session>();
    private Map<String, Session> sessionMap = new ConcurrentHashMap<String, Session>();
    private boolean pendingLayoutUpdate = false;
    private boolean videoDisplayed = false;
    public static final int SEND_ALL_SESSION_TIMEOUT = 30;
    public static final int STOP_ALL_SESSION_TIMEOUT = 30;
    public static final int DISCONNECT_ALL_SESSION_TIMEOUT = 30;

	public SessionManager(com.crestron.txrxservice.canvas.CresCanvas canvas)
	{
		Log.i(TAG, "Creating SessionManager");
		mCanvas = canvas;
		new Timer().schedule(new InactivityTask(), 30000, 60000);
	}
	
    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// PROPERTIES
    ////////////////////////////////////////////////////////////////////////////////////////////////
	
    /// SESSIONS

    public boolean isEmpty() { return count() > 0; }

    public boolean isNotEmpty() { return !isEmpty(); }

    public int count() {
        return sessions_.size();
    }

    public Collection<Session> sessions() {
        Collection<Session> list = new LinkedList<Session>();
        synchronized (lock_) {
            for (Session session : sessions_)
                list.add(session);
        }
        return list;
    }
    
    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// METHODS
    ////////////////////////////////////////////////////////////////////////////////////////////////
    
    public void add(Session session) {
        synchronized (lock_) {
            for (Session entry : sessions_) {
                if (Session.isEqual(entry, session)) return;
            }
            sessions_.add(session);
            sessionMap.put(session.sessionId(), session);
        }
    }

    public void remove(Session aSession) {
    	Session session = null;
        synchronized (lock_) {
            for (Session entry : sessions_) {
                if (!Session.isEqual(entry, aSession)) continue;
                session = entry;
                break;
            }
            if (session == null) return;
            sessionMap.remove(session.sessionId());
            sessions_.remove(session);
        }
    }
    
    public void remove(String sessionId) {
        synchronized (lock_) {
        	Session session = sessionMap.get(sessionId);
            if (session == null) return;
            sessionMap.remove(session.sessionId());
            sessions_.remove(session);
        }
    }
    
    public Session getSession(String sessionId)
    {
        synchronized (lock_) {
        	return sessionMap.get(sessionId);        	
        }
    }
    
    public Session findSession(String type, String label, String url, int inputNumber)
    {
    	Session session = null;
        synchronized (lock_) {
            for (Session entry : sessions_) {
                if (!entry.type.toString().equalsIgnoreCase(type)) continue;
                if (entry.type == SessionType.AirBoard) {
                	if (!entry.userLabel.equalsIgnoreCase(label)) continue;
                } else if (entry.type == SessionType.HDMI) {
                	if (entry.inputNumber != inputNumber) continue;
                } else if (entry.type == SessionType.DM) {
                	if (entry.inputNumber != inputNumber) continue;
                } else {
                	continue;
                }
                session = entry;
                break;
            }
        }
        return session;
    }
    
    public Session findSession(int streamId)
    {
    	Session session = null;
        synchronized (lock_) {
            for (Session entry : sessions_) {
            	if (entry.streamId == streamId) {
            		session = entry;
            		break;
            	}
            }
        }
        return session;
    }
    
    public Session findSession(String sessionId)
    {
    	Session session = null;
        synchronized (lock_) {
            for (Session entry : sessions_) {
            	if (entry.sessionId().equalsIgnoreCase(sessionId)) {
            		session = entry;
            		break;
            	}
            }
        }
        return session;
    }
    
    public List<String> getSessionIds()
    {
    	List<String> idList = new LinkedList<String>();
    	synchronized (lock_) {
            for (Session session : sessions_) {
            	idList.add(session.sessionId());
            }
    	}
    	return idList;
    }
    
    public void stopAllSessions(Originator origin)
    {
    	stopAllSessions(origin, null);
    }
    
    public void stopAllSessions(Originator origin, List<SessionType> typelist)
    {
    	CanvasCrestore crestore = mCanvas.getCrestore();
    	SessionEvent e = crestore.new SessionEvent(UUID.randomUUID().toString());
    	synchronized (lock_) { 
            for (Session session : sessions_) {
                if (session == null) continue;
            	if (typelist==null || typelist.contains(session.type))
            		e.add(session.sessionId(), crestore.new SessionEventMapEntry("stop", session));
            }
    	}
    	if (e.sessionEventMap.size() > 0)
    	{
    		TimeSpan startTime = TimeSpan.now();
        	boolean success = crestore.doSynchronousSessionEvent(e, origin, STOP_ALL_SESSION_TIMEOUT);
        	if (!success)
        		Common.Logging.i(TAG,"stopAllSessions(): Timeout while stopping all sessions"+e.transactionId);
        	else
        		Common.Logging.i(TAG,"stopAllSessions(): completed in "+TimeSpan.now().subtract(startTime).toString());
    	}
    }
    
    public void disconnectAllSessions(Originator origin)
    {
    	disconnectAllSessions(origin, null);
    }
    
    public void disconnectAllSessions(Originator origin, List<SessionType> typelist)
    {
    	CanvasCrestore crestore = mCanvas.getCrestore();
    	SessionEvent e = crestore.new SessionEvent(UUID.randomUUID().toString());
    	synchronized (lock_) { 
            for (Session session : sessions_) {
                if (session == null) continue;
            	if (typelist==null || typelist.contains(session.type))
            		e.add(session.sessionId(), crestore.new SessionEventMapEntry("disconnect", session));
            }
    	}
    	if (e.sessionEventMap.size() > 0)
    	{
    		TimeSpan startTime = TimeSpan.now();
        	boolean success = crestore.doSynchronousSessionEvent(e, origin, DISCONNECT_ALL_SESSION_TIMEOUT);
        	if (!success)
        		Common.Logging.i(TAG,"disconnectAllSessions(): Timeout while disconnecting all sessions"+e.transactionId);
        	else
        		Common.Logging.i(TAG,"disconnectAllSessions(): completed in "+TimeSpan.now().subtract(startTime).toString());
    	}
    }
    
    public void disconnectInactiveAirMediaSessions(Originator origin)
    {
    	CanvasCrestore crestore = mCanvas.getCrestore();
    	if (crestore == null)
    		return;
    	SessionEvent e = crestore.new SessionEvent(UUID.randomUUID().toString());
    	synchronized (lock_) { 
            for (Session session : sessions_) {
                if (session == null) continue;
            	if (session.inactiveSession())
            		e.add(session.sessionId(), crestore.new SessionEventMapEntry("disconnect", session));
            }
    	}
    	if (e.sessionEventMap.size() > 0)
    	{
            Common.Logging.i(TAG, "In inactivity task event="+e); 
    		TimeSpan startTime = TimeSpan.now();
        	boolean success = crestore.doSynchronousSessionEvent(e, origin, DISCONNECT_ALL_SESSION_TIMEOUT);
        	if (!success)
        		Common.Logging.i(TAG,"disconnectInactiveAirMediaSessions(): Timeout while stopping all sessions"+e.transactionId);
        	else
        		Common.Logging.i(TAG,"disconnectInactiveAirMediaSessions(): completed in "+TimeSpan.now().subtract(startTime).toString());
    	}
    }
    
    public class InactivityTask extends TimerTask {
        @Override
        public void run() {
        	// when timeout is 0, then inactivity disconnection is disabled
        	if (mCanvas.mStreamCtl.userSettings.getAirMediaInactivityTimeout() != 0)
        		disconnectInactiveAirMediaSessions(new Originator(RequestOrigin.InactivityTimer));
        }
    }
    
    public void clearAllSessions(boolean force)
    {
		Common.Logging.i(TAG,"clearAllSessions() entered force="+force);
        for (Session session : sessions()) {
            if (session == null) continue;
            if (session.type != SessionType.AirMedia || force)
            {
        		Common.Logging.i(TAG,"clearAllSessions() stopping session:"+session.sessionId());
        		session.stop(new Originator(RequestOrigin.Error));
        		Common.Logging.i(TAG,"clearAllSessions() stopped session:"+session.sessionId());
        		Common.Logging.i(TAG,"clearAllSessions() disconnecting session:"+session.sessionId());
            	session.disconnect(new Originator(RequestOrigin.Error));
        		Common.Logging.i(TAG,"clearAllSessions() disconnected session:"+session.sessionId());
            }
            else // AirMedia Sessions should have gone away due to receiver crash
            {
            	Common.Logging.i(TAG,"clearAllSessions() removing session:"+session.sessionId()+" from map");
            	remove(session.sessionId());
        		Common.Logging.i(TAG,"clearAllSessions() removed session:"+session.sessionId());
            }
        }
		Common.Logging.i(TAG,"clearAllSessions() clear list");
        sessions_.clear();
        updateVideoStatus();
    }
    
    public void sendAllSessionsInSessionEvent(Originator origin)
    {
    	CanvasCrestore crestore = mCanvas.getCrestore();
    	SessionEvent e = crestore.new SessionEvent(UUID.randomUUID().toString());
    	synchronized (lock_) { 
            for (Session session : sessions_) {
                if (session == null) continue;
            	String request = "Disconnect";
            	if (session.isConnecting())
            		request = "Connect";
            	else if (session.isPlaying())
            		request = "Play";
               	else if (session.isStopped())
            		request = "Stop";   
               	else
            		request = "Disconnect"; 
            	e.add(session.sessionId(), crestore.new SessionEventMapEntry(request, session));
            }
    	}
    	if (e.sessionEventMap.size() >= 0)
    	{
    		TimeSpan startTime = TimeSpan.now();
        	boolean success = crestore.doSynchronousSessionEvent(e, origin, SEND_ALL_SESSION_TIMEOUT);
        	if (!success)
        		Common.Logging.i(TAG,"sendAllSessionsInSessionEvent(): Timeout while doing allSessionsEvent "+e.transactionId);
        	else
        		Common.Logging.i(TAG,"sendAllSessionsInSessionEvent(): completed in "+TimeSpan.now().subtract(startTime).toString());
    	}
    }
    
    public boolean havePlayingAirMediaSession()
    {
    	boolean playingAirMediaSession=false;
        for (Session session : sessions()) {
            if (session == null) continue;
            if (session.type != SessionType.AirMedia)
            	continue;
            if (session.isPlaying())
            {
            	playingAirMediaSession=true;
            	break;
            }
        }
        return playingAirMediaSession;
    }
    
    public void logSessionStates(String tag)
    {
    	synchronized (lock_) {
        	StringBuilder sb = new StringBuilder("SessionStates = {");
            for (Session session : sessions_) {
            	sb.append(" ("+session+"   state="+session.getState()+")");
            }
            sb.append(" }\n");
        	Common.Logging.i(tag, sb.toString());
    	}
    }
    
    public void updateVideoStatus()
    {
    	boolean videoPresenting = false;
    	synchronized (lock_) {
            for (Session session : sessions_) {
            	//Common.Logging.i(TAG, "SessionManager::updateVideoStatus(): session="+session+" state="+session.getState());
            	SessionState state = session.getState();
            	if (state == SessionState.Playing || state == SessionState.Paused)
            	{
            		videoPresenting = true;
            		break;
            	}
            }
    	}
    	if (videoPresenting != videoDisplayed)
    	{
    		videoDisplayed = videoPresenting;
    		mCanvas.getCrestore().setVideoDisplayed(videoDisplayed);
    	}
    	Common.Logging.i(TAG, "SessionManager::updateVideoStatus(): videoDisplayed = "+videoDisplayed);
    }
    
    public void setPendingLayoutUpdate()
    {
    	synchronized(layoutUpdateLock)
    	{
    		if (!pendingLayoutUpdate)
    		{
    			pendingLayoutUpdate = true;
    		}
    	}
    }
        
    public boolean getPendingLayoutUpdate()
    {
    	synchronized(layoutUpdateLock)
    	{
    		return pendingLayoutUpdate;
    	}
    }
    
    public void updateLayoutIfNotPending()
    {
    	// do a layout update if one is not already scheduled
    	synchronized(layoutUpdateLock)
    	{
    		if (!pendingLayoutUpdate)
    			doLayoutUpdate();
    		else
    			Log.i(TAG, "updateLayoutIfNotPending(): Already have a pending layout update");
    	}
    }
    
    public void doLayoutUpdate()
    {
    	final boolean doCanvasSessionsUpdate = true;
    	final boolean doSessionLayoutMap = true;
    	
    	synchronized(layoutUpdateLock)
    	{
    		if (doSessionLayoutMap)
    		{
        		updateSessionLayoutMap();
    		}

    		if (doCanvasSessionsUpdate) {
    			canvasSessionsUpdate();
    		}

    		pendingLayoutUpdate = false;
    	}
    }
    
    private void updateSessionLayoutMap()
    {
    	class SessionLayoutMapEntry {
    		SessionType type;
    		String userLabel;
    		SessionState state;
    		int width;
    		int height;
    		String mode;

    		public String toString() 
    		{ 
    			return "Type:"+type+", UserLabel="+userLabel+", State="+state+", resolution="+width+"x"+height; 
    		}
    	}

    	ConcurrentHashMap<String, SessionLayoutMapEntry> layoutMap = new ConcurrentHashMap<String, SessionLayoutMapEntry>();

    	synchronized (lock_) {
    		for (Session s : sessions_) {
    			SessionLayoutMapEntry lme = new SessionLayoutMapEntry();
    			lme.type = s.type;
    			lme.userLabel = s.userLabel;
    			lme.state = s.state;
    			lme.width = s.getResolution().width;
    			lme.height = s.getResolution().height;
    			lme.mode = "PreserveAR";
    			layoutMap.put(s.sessionId(), lme);
    		}
    	}

    	for (Map.Entry<String, SessionLayoutMapEntry> e : layoutMap.entrySet())
    	{
    		Common.Logging.i("layoutUpdate", e.getKey()+": "+e.getValue().toString());
    	}
    }
    
    private void canvasSessionsUpdate()
    {
    	synchronized (lock_) {
    		List<CanvasSourceSession> sList = new ArrayList<CanvasSourceSession>();
    		for (Session s : sessions_) {
    			if (s.state == SessionState.Connecting || s.state == SessionState.Disconnecting)
    				continue;
    			
    			CanvasSourceSession css = Session.session2CanvasSourceSession(s);
    			
    			sList.add(css);
    		}
    		doCanvasSessionsUpdate(sList);
    	}
    }
    
    public void doCanvasSessionsUpdate(List<CanvasSourceSession> sessions)
    {
    	if (sessions.isEmpty())
    	{
    		Common.Logging.i(TAG, "doSessionsUpdate: empty sessions list");
    	} else {
    		for (CanvasSourceSession s : sessions)
    		{
    			Common.Logging.i("TxRx.canvas.sessionsupdate: ", "\nSession "+mCanvas.getCrestore().getGson().toJson(s));
    		}
    	}
		if ((mCanvas.mAirMediaCanvas.service() != null) && mCanvas.IsAirMediaCanvasUp())
		{
			try {
				mCanvas.mAirMediaCanvas.service().sessionsUpdate(sessions);
			} catch (android.os.RemoteException ex)
			{
				Log.i(TAG, "Remote exceptione encountered while doing sessions update");
				ex.printStackTrace();
			}
		}
    }
}
