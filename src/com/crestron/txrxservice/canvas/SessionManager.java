package com.crestron.txrxservice.canvas;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.Collection;
import java.util.List;
import java.util.LinkedList;
import java.util.concurrent.*;

import com.crestron.airmedia.canvas.channels.ipc.CanvasSourceSession;
import com.crestron.airmedia.utilities.Common;
import com.crestron.airmedia.utilities.TimeSpan;
import com.crestron.txrxservice.CresStreamCtrl;
import com.crestron.txrxservice.canvas.CanvasCrestore.SessionEvent;
import android.util.Log;

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
    public  int mNumOfPresenters = 0;
    public static final int SEND_ALL_SESSION_TIMEOUT = 30;
    public static final int STOP_ALL_SESSION_TIMEOUT = 30;
    public static final int DISCONNECT_ALL_SESSION_TIMEOUT = 30;
    
    public enum MaxResolution 
    {
        Any(0),
        Max4K(1),
        Max1080P(2),
        Max720P(3),

        //Do not add anything after Unknown
        Unknown(4);

        private final int value;

        MaxResolution(int value) 
        {
            this.value = value;
        }

        public int getValue() 
        {
            return value;
        }
        public static String toString(int i) 
        {
            for (MaxResolution status : MaxResolution.values()) 
            {
                if (status.getValue() == i) 
                {
                    return status.toString();
                }
            }
            return ("Unknown");
        }
    }

    public SessionManager(com.crestron.txrxservice.canvas.CresCanvas canvas)
	 {
	 	Log.i(TAG, "Creating SessionManager");
	 	mCanvas = canvas;
	 	Log.i(TAG,"Creating Inactivity Task scheduled to run after 30 seconds and every 60 seconds after that");
       
       Runnable inactivityTask = new InactivityTask("SessionInactivityTask");
             
       // Creating a ScheduledThreadPoolExecutor object
       ScheduledThreadPoolExecutor threadPool = new ScheduledThreadPoolExecutor(1);
       
       // Scheduling a task which will execute after 30 seconds and then repeats periodically with
       // a period of 60 seconds
       threadPool.scheduleAtFixedRate(inactivityTask, 30, 60, TimeUnit.SECONDS);
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
                } else if (entry.type == SessionType.NetworkStreaming) {
                    if (!entry.userLabel.equalsIgnoreCase(label)) continue;
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
    
    public int getNumPlayingSessions()
    {
        int nSessions=0;
        synchronized (lock_) {
            for (Session session : sessions_)
                if (session.isPlaying() || session.isPaused())
                    nSessions++;
        }
        return nSessions;
    }
    
    public MaxResolution getMaxResolution(int numPlayingSessions)
    {
        if (numPlayingSessions <= 1)
            return MaxResolution.Max4K;
        else if (numPlayingSessions <= 4)
            return MaxResolution.Max1080P;
        else
            return MaxResolution.Max720P;
    }
    
    public void restartMultiResolutionSessions()
    {
        // Cannot take lock since restart requires stop which calls lower level functions (setNetworkStreamingFeedbacks and
        // set NetworkStreamingResolution) that call findSession() which also need lock cauing deadlock
        // So using sessions() which returns a copy of list but need to check for session existence during commands
        Collection<Session> sessionList = sessions();
        for (Session session : sessionList)
            if (session.isMultiResolution && (session.isPlaying() || session.isPaused()))
                session.restartWithNewResolution();
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
            for (Session session : sessions_)
            {
               if (session == null)
                  continue;
            	if (session.inactiveSession())
            		e.add(session.sessionId(), crestore.new SessionEventMapEntry("disconnect", session));
            }
    	}
    	if (e.sessionEventMap.size() > 0)
    	{
         Common.Logging.i(TAG, "In inactivity task event=" + e); 
    		TimeSpan startTime = TimeSpan.now();
        	boolean success = crestore.doSynchronousSessionEvent(e, origin, DISCONNECT_ALL_SESSION_TIMEOUT);
        	if (!success)
        		Common.Logging.i(TAG,"disconnectInactiveAirMediaSessions(): Timeout while stopping all sessions"+e.transactionId);
        	else
        		Common.Logging.i(TAG,"disconnectInactiveAirMediaSessions(): completed in "+TimeSpan.now().subtract(startTime).toString());
    	}
    }
    
    class InactivityTask implements Runnable
    {
        String taskName;
        public InactivityTask(String taskName)
        {
            this.taskName = taskName;
        }
        public void run()
        {
            try
            {
                int inactivityTimeout = mCanvas.mStreamCtl.userSettings.getAirMediaInactivityTimeout();
                Common.Logging.i(TAG, "InactivityTask::run(): inactivity timeout = " + inactivityTimeout);
                // when timeout is 0, then inactivity disconnection is disabled
                if(inactivityTimeout != 0)
                    disconnectInactiveAirMediaSessions(new Originator(RequestOrigin.InactivityTimer));
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
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
        		if (session.state != SessionState.Disconnecting)
        		{
        		    Common.Logging.i(TAG,"clearAllSessions() disconnecting session:"+session.sessionId());
        		    session.disconnect(new Originator(RequestOrigin.Error));
        		    Common.Logging.i(TAG,"clearAllSessions() disconnected session:"+session.sessionId());
        		}
            }
            else // AirMedia Sessions should have gone away due to receiver crash except Miracast on AM3K
            {
                if (session.getAirMediaType() == SessionAirMediaType.Miracast && CresStreamCtrl.isAM3K)
                {
                    // need to manually stop on device side as if it was called by AM receiver app
                    if (session.streamId >= 0)
                    {
                        Common.Logging.i(TAG,"clearAllSessions() stopping miracast session:"+session.sessionId());
                        mCanvas.mStreamCtl.wifidVideoPlayer.stopSessionWithStreamId(session.streamId);
                        Common.Logging.i(TAG,"clearAllSessions() release surface for session:"+session.sessionId());
                        session.releaseSurface();
                        Common.Logging.i(TAG,"clearAllSessions() stopped miracast session:"+session.sessionId());
                    }
                }
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
        int videoDisplayedCnt = 0;
    	synchronized (lock_) {
            for (Session session : sessions_) {
            	//Common.Logging.i(TAG, "SessionManager::updateVideoStatus(): session="+session+" state="+session.getState());
            	SessionState state = session.getState();
            	if (state == SessionState.Playing || state == SessionState.Paused)
            	{
                    //Note: videoPresenting will be set to true as long as there is video.
                    //      videoDisplayedCnt needs to go through all sessions.
            		videoPresenting = true;
                    videoDisplayedCnt++;         		
            	}
            }
    	}
    	if (videoPresenting != videoDisplayed)
    	{
    		videoDisplayed = videoPresenting;
    		mCanvas.getCrestore().setVideoDisplayed(videoDisplayed);
    	}//else

        if(mNumOfPresenters != videoDisplayedCnt)
        {
            mNumOfPresenters = videoDisplayedCnt;
            //send to csio for analog join
            mCanvas.mStreamCtl.sendNumOfPresenters(mNumOfPresenters);
        }//else
    	Common.Logging.i(TAG, "SessionManager::updateVideoStatus(): videoDisplayed = "+videoDisplayed + ", mNumOfPresenters: " + mNumOfPresenters);
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
