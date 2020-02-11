package com.crestron.txrxservice.canvas;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Collection;
import java.util.List;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import com.crestron.airmedia.canvas.channels.ipc.CanvasPlatformType;
import com.crestron.airmedia.canvas.channels.ipc.CanvasSessionState;
import com.crestron.airmedia.canvas.channels.ipc.CanvasSourceSession;
import com.crestron.airmedia.canvas.channels.ipc.CanvasSourceType;
import com.crestron.airmedia.receiver.m360.models.AirMediaSession;
import com.crestron.airmedia.utilities.Common;

import android.util.Log;

//import com.crestron.txrxservice.canvas.Session;

public class SessionManager
{
	com.crestron.txrxservice.canvas.CresCanvas mCanvas;
    public static final String TAG = "TxRxSessionManager"; 
    private final Object lock_ = new Object();
    private final Object layoutUpdateLock = new Object();
    private final List<Session> sessions_ = new LinkedList<Session>();
    private Map<String, Session> sessionMap = new ConcurrentHashMap<String, Session>();
    private boolean pendingLayoutUpdate = false;
    private boolean videoDisplayed = false;

	public SessionManager(com.crestron.txrxservice.canvas.CresCanvas canvas)
	{
		Log.i(TAG, "Creating SessionManager");
		mCanvas = canvas;
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
                	if (!entry.url.equalsIgnoreCase(url)) continue;
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
            	Common.Logging.i(TAG, "SessionManager::updateVideoStatus(): session="+session+" state="+session.getState());
            	if (session.getState() == SessionState.Playing)
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
        
    public void updateLayoutIfNotPending()
    {
    	// do a layout update if one is not already scheduled
    	synchronized(layoutUpdateLock)
    	{
    		if (!pendingLayoutUpdate)
    			doLayoutUpdate();
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
    		    CanvasSessionState state = CanvasSessionState.Stopped;
    		    CanvasSourceType type;
    		    CanvasPlatformType platform;

    		    if (s.isStopped())
    		    	state  = CanvasSessionState.Stopped;
    		    else if (s.isPlaying())
    		    	state  = CanvasSessionState.Playing;
    		    else if (s.isPaused())
    		    	state = CanvasSessionState.Paused;
    		    
    		    switch (s.type)
    		    {
				case AirBoard:
					type = CanvasSourceType.Airboard;
					break;
				case AirMedia:
					type = CanvasSourceType.AirMedia;
					break;
				case DM:
					type = CanvasSourceType.DM;
					break;
				case HDMI:
					type = CanvasSourceType.HDMI;
					break;
				case Unknown:
				default:
					type = CanvasSourceType.Unknown;
					break;	    	
    		    }
    		    
    		    platform = s.getPlatformType();

    		    CanvasSourceSession css = new CanvasSourceSession(s.sessionId(), s.getUserLabel(), state, type, 
    		    		platform, s.getResolution().width, s.getResolution().height);

    			sList.add(css);
    		}
    		doCanvasSessionsUpdate(sList);
    	}
    }
    
    public void doCanvasSessionsUpdate(List<CanvasSourceSession> sessions)
    {
    	for (CanvasSourceSession s : sessions)
    	{
    		Common.Logging.i("doCanvasSessionsUpdate: ", "\nSession "+mCanvas.getCrestore().getGson().toJson(s));
    	}
    }
}
