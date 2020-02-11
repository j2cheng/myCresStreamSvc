package com.crestron.txrxservice.canvas;

import com.crestron.airmedia.canvas.channels.ipc.CanvasPlatformType;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSize;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaPlatforms;
import com.crestron.airmedia.receiver.m360.models.*;
import com.crestron.txrxservice.CresStreamCtrl;
import com.crestron.airmedia.utilities.Common;
import com.crestron.airmedia.utilities.TimeSpan;
import com.crestron.airmedia.utilities.TaskScheduler;
import com.crestron.txrxservice.canvas.SessionState;
import com.google.gson.annotations.SerializedName;

import android.graphics.Rect;
import android.os.ConditionVariable;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;
import android.util.Log;
import android.view.Surface;

public class Session
{
	com.crestron.txrxservice.canvas.CresCanvas mCanvas;
	public static CresStreamCtrl mStreamCtl;
	public SessionManager mSessionMgr;
    public static final String TAG = "TxRxSession"; 
    public long id;
    public int streamId;
    public SessionState state;
    public SessionType type;
    public SessionAirMediaType airMediaType;
    public CanvasPlatformType platform;
    public String userLabel;
    public int inputNumber;
    public String url;
    public AirMediaSize resolution;
    public boolean [] permissions = new boolean[PermissionType.size];
    //public SessionInfo info_;
    public static long nextId = 0;
	TaskScheduler scheduler_;
	
	TaskScheduler scheduler() { return scheduler_; }
	
	void queue(String name, TimeSpan timeout, Runnable task) {
		Common.Logging.i(TAG, name + "  timeout= " + timeout.toString());
		TimeSpan start = TimeSpan.now();
		boolean isCompleted = false;
		try {
			isCompleted = scheduler().queue(timeout, task);
		} catch (Exception e) {
			Common.Logging.e(TAG, name + "  timeout= " + timeout.toString() + "  EXCEPTION  " + e + "  " + Log.getStackTraceString(e));
		} finally {
			Common.Logging.i(TAG, name + "  timeout= " + timeout.toString() + "  COMPLETE= " + isCompleted + "  " + TimeSpan.getDelta(start));
		}
	}
	
	public Session()
	{
		mCanvas = com.crestron.txrxservice.canvas.CresCanvas.getInstance();
		mStreamCtl = mCanvas.mStreamCtl;
		mSessionMgr = mCanvas.mSessionMgr;
		streamId = -1;
		id = getNextId();
		state = SessionState.Connecting;
		type = SessionType.Unknown;
		airMediaType = null;
		platform = CanvasPlatformType.Undefined;
		inputNumber = 0;
		url = null;
		resolution = new AirMediaSize(0,0);
		scheduler_ = new com.crestron.airmedia.utilities.TaskScheduler(TAG);
		setSourceUserPermission(false);
		setCanvasUserPermission(false);
		setModeratorPermission(true);
	}
	
	public static Session createSession(String type, String label, String url, int inputNumber, com.crestron.airmedia.receiver.m360.models.AirMediaSession rSession)
	{
		Session session = null;
		
		if (type.equalsIgnoreCase("AirMedia")) {
			Log.i(TAG, "Must use createAirMediaSession()");
			session = new AirMediaSession(rSession, label);
		} else if (type.equalsIgnoreCase("AirBoard")) {
			session = new AirBoardSession(url, label);
		} else if (type.equalsIgnoreCase("HDMI")) {
			session = new HDMISession(inputNumber);
		} else if (type.equalsIgnoreCase("DM")) {
			session = new DMSession(inputNumber);
		}
		return session;
	}
	
	public static long getNextId()
	{
		return ++nextId;
	}
	
	public long id() { return id; };
	
	public String sessionId()
	{
		return "Session-"+id;
	}
	
	public com.crestron.txrxservice.canvas.CresCanvas getCanvas() { return mCanvas; }
	
	public boolean isStopped() { return (state == SessionState.Stopped || state == SessionState.Stopping); }
	public boolean isPlaying() { return (state == SessionState.Starting || state == SessionState.Playing || state == SessionState.UnPausing); }
	public boolean isPaused() { return (state == SessionState.Pausing || state == SessionState.Paused); }
	public boolean isConnecting() { return state == SessionState.Connecting;}

	public void setState(SessionState state) { this.state = state; mSessionMgr.updateVideoStatus();}	
	public SessionState getState() {return state;}
	
	public void setType(SessionType type) { this.type = type; }	
	public SessionType getType() {return type;}
	
	public void setAirMediaType(SessionAirMediaType airMediaType) { this.airMediaType = airMediaType; }	
	public SessionAirMediaType getAirMediaType() {return airMediaType;}
	
	public void setPlatformType(CanvasPlatformType platform) { this.platform = platform; }	
	public void setPlatformType(AirMediaPlatforms platform) { 
		Common.Logging.i(TAG, "setPlatformType: "+platform);
		CanvasPlatformType p;
		switch(platform) 
		{
		case Windows:
			p = CanvasPlatformType.Windows; break;
		case Mac:
			p = CanvasPlatformType.Mac; break;
		case iOS:
			p = CanvasPlatformType.iOS; break;
		case Android:
			p = CanvasPlatformType.Android; break;
		case Chromebook:
			p = CanvasPlatformType.Chrome; break;
		case Linux:
			p = CanvasPlatformType.Linux; break;
		case Undefined:
		default:
			p = CanvasPlatformType.Undefined; break;
		}
		if (p != this.platform)
		{
			this.platform = p;
			layoutUpdate();
		}
	}
	public CanvasPlatformType getPlatformType() {return platform;}
	
	public void setUserLabel(String label) { this.userLabel = label; }	
	public String getUserLabel() {return userLabel;}
	
	public void setInputNumber(int inputNumber) { this.inputNumber = inputNumber; }	
	public int getInputNumber() {return inputNumber;}
	
	public void setUrl(String url) { this.url = url; }	
	public String getUrl() {return url;}
	
	public AirMediaSize getResolution() { return resolution; }
	
	public void attachToManager() { mSessionMgr.add(this);}
	public void detachFromManager() { mSessionMgr.remove(this); }

	public boolean getSourceUserPermission() {  return permissions[PermissionType.SourceUser.value]; }
	public boolean getCanvasUserPermission() {  return permissions[PermissionType.CanvasUser.value]; }
	public boolean getModeratorPermission() {  return permissions[PermissionType.Moderator.value]; }

	public void setSourceUserPermission(boolean v) { permissions[PermissionType.SourceUser.value] = v; }
	public void setCanvasUserPermission(boolean v) {  permissions[PermissionType.CanvasUser.value] = v; }
	public void setModeratorPermission(boolean v) {  permissions[PermissionType.Moderator.value] = v; }
	
	public void connect()
	{
	}
	
	public void disconnect(Originator originator)
	{
		disconnect();
	}
	
	public void disconnect()
	{
	}
	
	public void stop(Originator originator)
	{
		stop();
	}
	
	public void stop(Originator originator, boolean replace)
	{
		stop();
	}
	
	public void stop()
	{
		Common.Logging.i(TAG, "Session "+this+" stop request");
		setState(SessionState.Stopped);
	}
	


	public void play(Originator originator)
	{
		play(originator, -1);
	}
	
	public void play(Originator originator, int assignedStreamId)
	{
		play();
	}
	
	public void play()
	{
		Common.Logging.i(TAG, "Session "+this+" play request");
		setState(SessionState.Playing);
	}
	
//	public void replace(Originator originator, Session next)
//	{
//		boolean canReplace = false;
//		int commonStreamId = -1;
//		if (type == SessionType.AirMedia && next.type == SessionType.AirMedia)
//		{
//			commonStreamId = streamId;
//			canReplace = true;
//		}
//		
//		stop(originator, canReplace);
//		streamId = -1;
//		next.play(originator, commonStreamId);
//	}
	
	public void replace(Originator originator, Session next)
	{
		int origStreamId = streamId;
		stop(originator, true);
		streamId = -1;
		next.play(originator, origStreamId);
	}
	
	public Surface acquireSurface()
	{
		Surface surface = mStreamCtl.getSurface(streamId);
		mCanvas.mSurfaceMgr.addSurface(streamId, surface);
		return surface;
	}
	
	public void releaseSurface()
	{
		mCanvas.mSurfaceMgr.removeSurface(streamId);
	}
	
	public Rect getWindow(Surface s)
	{
		return mCanvas.getWindow(mCanvas.mSurfaceMgr.surface2StreamId(s));
	}
	
	public void layoutUpdate()
	{
		mSessionMgr.updateLayoutIfNotPending();
	}
	
	public void setResolution(AirMediaSize r) 
	{ 
		if (!r.equals(resolution))
		{
			resolution = r;
			layoutUpdate();
		}
	}

	public void setVideoResolution(AirMediaSize resolution)
	{
		Common.Logging.i(TAG, "Session "+this+" set video resolution to "+resolution.toString());
		setResolution(resolution);
	}
	
	
	
    /// EQUALITY

    public static boolean isEqual(Session lhs, Session rhs) {
        return lhs == rhs || !(lhs == null || rhs == null) && lhs.id == rhs.id;
    }
    
//    @Override
//    public int hashCode()
//    {
//    	int prime = 31;
//    	int result = 1;
//    	result = prime*result + Long.valueOf(id).hashCode();
//    	result = prime*result + sessionId().hashCode();
//    	result = prime*result + state.hashCode();
//    	result = prime*result + type.hashCode();
//    	result = prime*result + airMediaType.hashCode();
//    	result = prime*result + Integer.valueOf(inputNumber).hashCode();
//    	result = prime*result + resolution.hashCode();
//    	return result;
//    }
//    
//    @Override
//    public boolean equals(Object obj)
//    {
//    	Session s = (Session) obj;
//    	
//    	if (this.hashCode() != s.hashCode())
//    		return false;
//    	
//    	return (this.id == s.id &&
//    			this.sessionId().equals(s.sessionId()) && 
//    			this.state == s.state &&
//    			this.type == s.type &&
//    			this.airMediaType == s.airMediaType &&
//    			this.inputNumber == s.inputNumber &&
//    			this.resolution.equals(s.resolution));
//    }
//    
//	// returns true if successful completion, false for timeout
//    public boolean executeWithTimeout(Runnable r, TimeSpan timeout)
//    {
//    	 ConditionVariable completed = new ConditionVariable();
//
//         Thread t = new Thread(new Runnable() {
//        	 private ConditionVariable c_;
//        	 private Runnable r_;
//        	 @Override public void run() { try { r_.run(); } finally { c_.open(); } }
//        	 public Runnable set(Runnable t, ConditionVariable c) { r_ = t; c_ = c; return this; }
//         }.set(r, completed));
//         t.start();
//
//         return completed.block(TimeSpan.toLong(timeout.totalMilliseconds()));
//    }    
    
    public enum PermissionType {
        SourceUser(0), CanvasUser(1), Moderator(2);
        public final int value;

        PermissionType(int v) { value = v; }

        public static PermissionType from(int v) {
            switch (v) {
                case 0: return SourceUser;
                case 1: return CanvasUser;
                case 2: return Moderator;
            }
            return SourceUser;
        }
        
        private static final int size = PermissionType.values().length;
    }
}
