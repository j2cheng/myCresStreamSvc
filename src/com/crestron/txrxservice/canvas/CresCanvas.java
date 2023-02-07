package com.crestron.txrxservice.canvas;

import com.crestron.airmedia.canvas.channels.ipc.CanvasResponse;
import com.crestron.airmedia.canvas.channels.ipc.CanvasSourceAction;
import com.crestron.airmedia.canvas.channels.ipc.CanvasSourceRequest;
import com.crestron.airmedia.canvas.channels.ipc.CanvasSourceSession;
import com.crestron.airmedia.canvas.channels.ipc.CanvasSourceTransaction;
import com.crestron.airmedia.canvas.channels.ipc.CanvasSurfaceAcquireResponse;
import com.crestron.airmedia.canvas.channels.ipc.CanvasSurfaceMode;
import com.crestron.airmedia.canvas.channels.ipc.CanvasSurfaceOptions;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSize;
import com.crestron.airmedia.utilities.Common;
import com.crestron.airmedia.utilities.TimeSpan;
import com.crestron.txrxservice.HDMIInputInterface;
import com.crestron.txrxservice.MiscUtils;
import com.crestron.txrxservice.canvas.Session;
import com.crestron.txrxservice.canvas.HDMISession;
import com.crestron.txrxservice.canvas.DMSession;
import com.crestron.txrxservice.CresStreamCtrl.StreamState;
import com.crestron.txrxservice.CresStreamCtrl.StartupEvent;

import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.Collection;

import android.graphics.Rect;
import android.os.ConditionVariable;
import android.os.Parcel;
import android.util.Log;
import android.view.Surface;

public class CresCanvas
{
	private static CresCanvas SINGLE_INSTANCE = null;
	public com.crestron.txrxservice.CresStreamCtrl mStreamCtl;
    public CanvasCrestore mCrestore = null;
    public SessionManager mSessionMgr = null;
    public CanvasSourceManager mCanvasSourceManager = null;
    AirMediaCanvas mAirMediaCanvas = null;
    private Scheduler scheduler = new Scheduler("CresCanvasScheduler");

	public static boolean useCanvasSurfaces = true;     // will be removed after integration
	public static boolean useSimulatedAVF = false;  // will be removed after integration

    public static final String TAG = "TxRx.canvas"; 
	private static final int MAX_HDMI_INPUTS = 1;
	private static final int MAX_DM_INPUTS = 1;

	private String prevHdmiResolution[] = new String[MAX_HDMI_INPUTS+1];
	private String prevHdmiSyncStatus[] = new String[MAX_HDMI_INPUTS+1];
	private AtomicBoolean codecFailure = new AtomicBoolean(false);
	private AtomicBoolean avfRestart = new AtomicBoolean(false);
	AtomicBoolean avfHasStarted = new AtomicBoolean(false);
	AtomicBoolean avfRestarting = new AtomicBoolean(false);
	public boolean cssRestart = true;
	public boolean canvasReady = false;
	public SurfaceManager mSurfaceMgr=null;
	
    private static boolean multiResolutionMode = MiscUtils.readStringFromDisk("/dev/shm/multiResolutionMode").equals("1");
    
    public boolean inMultiResolutionMode() { return multiResolutionMode; } 

	public CresCanvas(com.crestron.txrxservice.CresStreamCtrl streamCtl)
	{
	    Log.i(TAG, "MultiResolutionMode = "+multiResolutionMode);
		mSurfaceMgr = new SurfaceManager(); //TODO remove once we have real surface manager
		Log.i(TAG, "Creating CresCanvas");
		mStreamCtl = streamCtl;
		mSessionMgr = new SessionManager(this);
		try {
			mCrestore = new CanvasCrestore(mStreamCtl, this, mSessionMgr);
		} catch (IOException e) {
			Common.Logging.i(TAG, "CresCanvas(): exception trying to set up cresstore connection");
			mStreamCtl.RecoverTxrxService();
		}
		mCanvasSourceManager = new CanvasSourceManager(mStreamCtl, mCrestore);
		for (int i=0; i <= MAX_HDMI_INPUTS; i++)
		{
			prevHdmiResolution[i] = "0x0";
			prevHdmiResolution[i] = "false";
		}
	}
	
	public static CresCanvas getInstance(com.crestron.txrxservice.CresStreamCtrl streamCtl) {
		if (SINGLE_INSTANCE == null)
		{
			synchronized(CresCanvas.class)
			{
				SINGLE_INSTANCE = new CresCanvas(streamCtl);
			}
			
			//SINGLE_INSTANCE.mCrestore.setVideoDisplayed(true);
			//SINGLE_INSTANCE.mCrestore.setVideoDisplayed(false);
			//SINGLE_INSTANCE.mCrestore.setCurrentConnectionInfo("http://10.254.100.200");
		}
		return SINGLE_INSTANCE;
	}
	
	public static CresCanvas getInstance()
	{
		return SINGLE_INSTANCE;
	}
	
	public CanvasCrestore getCrestore()
	{
		return mCrestore;
	}
	
	public CanvasSourceManager getCanvasSourceManager()
	{
		return mCanvasSourceManager;
	}
	
	public void setWindows()
	{
		if (!CresCanvas.useCanvasSurfaces)
			mStreamCtl.setCanvasWindows();
	}
	
	public void startAirMediaCanvas()
	{
		Common.Logging.i(TAG, "startAirMediaCanvas(): calling constructor");
		mAirMediaCanvas = new AirMediaCanvas(mStreamCtl);
		if (IsAirMediaCanvasUp()) {
			Common.Logging.i(TAG, "startAirMediaCanvas(): calling canvasHasStarted()");
			canvasHasStarted();
		} else
			Common.Logging.i(TAG, "startAirMediaCanvas(): canvas has not yet started completely");
	}
	
	public boolean IsAirMediaCanvasUp()
	{
		boolean rv=false;
		if (!CresCanvas.useCanvasSurfaces)
			rv = mStreamCtl.airMediaIsUp(); // needed only until new apk is added that supports canvas 
		else
		    rv = (mAirMediaCanvas != null && mAirMediaCanvas.IsAirMediaCanvasUp() && mStreamCtl.airMediaIsUp());
		Common.Logging.v(TAG, "------- IsAirMediaCanvasUp(): rv="+rv);
		return rv;
	}
	
	public boolean IsInCodecFailureRecovery()
	{
		return codecFailure.get();
	}
	
	public void canvasHasStarted()
	{
		if (!IsAirMediaCanvasUp())
		{
			Common.Logging.i(TAG, "canvasHasStarted(): canvas not ready (AM is up="+mStreamCtl.airMediaIsUp()+
					" AM Canvas is up="+((mAirMediaCanvas != null)?mAirMediaCanvas.IsAirMediaCanvasUp():false)+")");
			return;
		}
		Common.Logging.i(TAG, "canvasHasStarted(): ------ canvas is ready -------");
		// send an empty list in layout update to indicate no sessions
		Common.Logging.i(TAG, "canvasHasStarted(): Layout update");
		mSessionMgr.doLayoutUpdate();
		// send Css restart signal to AVF if it is a fresh startup of CSS
		if (cssRestart)
		{
			cssRestart = false;
			mCrestore.sendCssRestart();
		}
		else
		{
			// Clear any existing AVF sessions if a resync
			clearAVF();
		}
		// start possible hdmi and dm sessions
		Common.Logging.i(TAG, "canvasHasStarted(): HDMI sync update");
		mStreamCtl.canvasHdmiSyncStateChange(false);
		Common.Logging.i(TAG, "canvasHasStarted(): DM sync update");
		mStreamCtl.canvasDmSyncStateChange();
	}
	
	public Rect getWindow(int streamId)
	{
		return mStreamCtl.getWindowDimensions(streamId);
	}
	
	public void showWindow(int streamId)
	{
		if (!CresCanvas.useCanvasSurfaces)
			mStreamCtl.showCanvasWindow(streamId);
	}
	
	public void hideWindow(int streamId)
	{
		if (!CresCanvas.useCanvasSurfaces)
			mStreamCtl.hideCanvasWindow(streamId);
	}
	
	public void clearAVF()
	{
		if (CresCanvas.useSimulatedAVF)
			getCrestore().mAVF.clearAllSessions();
		else
			getCrestore().doClearAllSessionEvent();
	}
	
	public void clear(boolean force)
	{
		Common.Logging.i(TAG, "clear(): restart sessionResponseScheduler");
		mCrestore.restartSchedulers();
		// Stop and remove all sessions
		mSessionMgr.clearAllSessions(force);

        // Stop any WC Opened session.
        if(mStreamCtl != null && mStreamCtl.mWC_Service != null)
        {
            Log.i(TAG,"clear --> closeSession");
            mStreamCtl.mWC_Service.closeSession();
        }
        else
            Common.Logging.w(TAG, "clear(): failed to closeSession as mStreamCtl or mWC_Service is NULL!!!!");

		// clear all surfaces and streamIds
		mSurfaceMgr.releaseAllSurfaces();
		Common.Logging.i(TAG, "clear(): exit");
	}
	
	public synchronized void handleReceiverDisconnected()
	{
		Common.Logging.i(TAG, "*********************** handleReceiverDisconnected ***********************");
		// Stop all sessions
		clear(false);
		// Restart of HDMI/DM will be handled when service reconnects by canvasHasStarted()
		Common.Logging.i(TAG, "handleReceiverDisconnected(): exit");
	}
	
	public synchronized void handleCodecFailure()
	{
		Common.Logging.i(TAG, "*********************** handleCodecFailure ***********************");
		codecFailure.set(true);
		// What do we do to handle ducati or media codec failure
		// Stop all sessions
		if (mSessionMgr.havePlayingAirMediaSession())
		{
        	Log.i(TAG, "handleCodecFailure() - existing playing AirMedia session - restart receiver service");
			// force receiver service restart
			mStreamCtl.RestartAirMedia();
			codecFailure.set(false);
			return;
		}
		else
		{
        	Log.i(TAG, "handleCodecFailure() - stop all HDMI and AirMedia sessions");
			mSessionMgr.stopAllSessions(new Originator(RequestOrigin.Error), new ArrayList<SessionType>(Arrays.asList(SessionType.AirMedia, SessionType.HDMI)));
			// Disconnect all airmedia sessions
        	Log.i(TAG, "handleCodecFailure() - disconnect all AirMedia sessions");
			mSessionMgr.disconnectAllSessions(new Originator(RequestOrigin.Error), new ArrayList<SessionType>(Arrays.asList(SessionType.AirMedia)));
		}
		if (avfRestart.get()) {
			// send AVF a message disconnecting all sessions if it happened to restart while we were dealing with a codec failure
        	Log.i(TAG, "handleCodecFailure() - disconnect all sessions due to AVF restart during codec failure");
			mSessionMgr.disconnectAllSessions(new Originator(RequestOrigin.Error));
			avfRestart.set(false);
		}
		codecFailure.set(false);
		// Restart HDMI/DM if possible
		Common.Logging.i(TAG, "handleCodecFailure(): restarting HDMI if needed");
		mStreamCtl.canvasHdmiSyncStateChange(false);
		Common.Logging.i(TAG, "handleCodecFailure(): restarting DM if needed");
		mStreamCtl.canvasDmSyncStateChange();
	}
	
	public void handleAvfRestart(boolean restart)
	{
		if (restart)
		{
			Common.Logging.i(TAG, "*********************** handleAvfRestart ***********************");
			final boolean isInAvfRestart = avfRestarting.get();
			//final boolean isInAvfRestart = false; // force full startup on AVF restart
			avfRestarting.compareAndSet(true, false);
			avfHasStarted.compareAndSet(false, true);
			if (!codecFailure.get() && IsAirMediaCanvasUp())
			{
				// called from cresstore callback thread and so must be run asynchronously so sessionResponse is not blocked
				Runnable r = new Runnable() { @Override public void run() { avfForcedStartup(isInAvfRestart, "handleAvfRestart"); } };
				scheduler.execute(r);
			}
			else
			{
				Common.Logging.i(TAG, "handleAvfRestart(): in codec failure/receiver restart - don't send any sessions to AVF - will be sent by codec failure handler");
				avfRestart.set(true);
			}
		}
		else
		{
			Common.Logging.i(TAG, "*********************** AVF stopping to restart ***********************");
			avfRestarting.compareAndSet(false, true);
		}
	}
	
	public void avfForcedStartup(boolean restart, String from)
	{
		if (restart)
		{
			Common.Logging.i(TAG, "avfForcedStartup(): (restart=true) - send all current sessions to AVF");
			mSessionMgr.sendAllSessionsInSessionEvent(new Originator(RequestOrigin.Error));
		} else {
			Common.Logging.i(TAG, "avfForcedStartup(): (restart=false) - clear all sessions");
			clear(true);
			Common.Logging.i(TAG, "avfForcedStartup() - send empty session event list to AVF");
			mSessionMgr.sendAllSessionsInSessionEvent(new Originator(RequestOrigin.Error));
			// Restart HDMI/DM if possible
			Common.Logging.i(TAG, from+": restarting HDMI if needed");
			mStreamCtl.canvasHdmiSyncStateChange(false);
			Common.Logging.i(TAG, from+": restarting DM if needed");
			mStreamCtl.canvasDmSyncStateChange();
		}
	}
	
	public void handlePossibleHdmiSyncStateChange(int inputNumber, HDMIInputInterface hdmiInput)
	{
		handlePossibleHdmiSyncStateChange(inputNumber, hdmiInput, true);
	}
	
	public synchronized void handlePossibleHdmiSyncStateChange(int inputNumber, HDMIInputInterface hdmiInput, boolean changeCheck)
	{
		final int HdmiTimeout = 15; // seconds

		if (!IsAirMediaCanvasUp())
		{
			Common.Logging.i(TAG, "handlePossibleHdmiSyncStateChange(): AirMediaCanvas not up - cannot display HDMI yet");
			mStreamCtl.setChangedBeforeStartUp(StartupEvent.eAirMediaCanvas_HDMI_IN_SYNC, true);
			return;
		}
		mStreamCtl.setChangedBeforeStartUp(StartupEvent.eAirMediaCanvas_HDMI_IN_SYNC, false);
		
		if (inputNumber > MAX_HDMI_INPUTS)
		{
			Common.Logging.e(TAG, "HDMI input number is "+inputNumber+"   Max allowed is "+MAX_HDMI_INPUTS);
			return;
		}
		String res = hdmiInput.getHorizontalRes() + "x" + hdmiInput.getVerticalRes();
		Session session = mSessionMgr.findSession("HDMI", "", "", inputNumber);
		Common.Logging.i(TAG, "handlePossibleHdmiSyncStateChange(): syncStatus="+hdmiInput.getSyncStatus()+"    resolution="+res+
				"     checkForChange="+changeCheck);
		if (mStreamCtl.isAM3K) { 
		    if (!mStreamCtl.mHdmiCameraIsConnected) {
		        // For AM3K if camera is disconnected force a res of 0 so we do disconnect event
		        res = "0x0";
		        Common.Logging.i(TAG,  "handlePossibleHdmiSyncStateChange(): forcing res to 0 since camera is disconnected");
		    } else if (res.equals("0x0")){
		        // For AM3K if camera is connected snd res is 0 - ignore event for now - will be handled when res changes to non-zero
		        Common.Logging.i(TAG,  "handlePossibleHdmiSyncStateChange(): got 0 resolution with connected camera - ignoring for now");
		        return;
		    }
		}
		if (changeCheck && (hdmiInput.getSyncStatus().equalsIgnoreCase(prevHdmiSyncStatus[inputNumber]) &&
				res.equalsIgnoreCase(prevHdmiResolution[inputNumber])))
		{
			Common.Logging.e(TAG, "handlePossibleHdmiSyncStateChange(): no change in resolution or sync status");
			return;
		}
		prevHdmiSyncStatus[inputNumber] = hdmiInput.getSyncStatus();
		prevHdmiResolution[inputNumber] = res;
		Originator origin = new Originator(RequestOrigin.Hardware);
		if (hdmiInput.getSyncStatus().equalsIgnoreCase("true") &&
		        !mStreamCtl.userSettings.getHdmiInMode().equalsIgnoreCase("Camera"))
		{
			// HDMI sync is true but we want to stop presentation if HDMI input is set to camera mode
			if (res.equals("0x0"))
			{
				Common.Logging.e(TAG, "HDMI sync is true yet resolution is 0x0");
				return;
			}
			// if session is playing it should be stopped
			if (session != null)
			{
				
				Common.Logging.e(TAG, "HDMI sync is true with existing HDMI session in state "+session.state);
				mCrestore.doSynchronousSessionEvent(session, "Disconnect", origin, HdmiTimeout);
				Common.Logging.v(TAG, "HDMI session "+session+" disconnected");
			}
			session = com.crestron.txrxservice.canvas.Session.createSession("HDMI", "HDMI"+inputNumber, null, 1, null);
			((HDMISession) session).setHdmiInput(hdmiInput);
			Common.Logging.i(TAG, "Adding session " + session + " to sessionManager");
			mSessionMgr.add(session);
			mCrestore.doSynchronousSessionEvent(session, "Connect", origin, HdmiTimeout);
			Common.Logging.v(TAG, "HDMI session "+session+" connected");
		}
		else
		{
			// if session is playing it should be stopped
			if (session != null)
			{
				
				Common.Logging.e(TAG, "HDMI sync is false with existing HDMI session in state "+session.state);
	            mCrestore.doSynchronousSessionEvent(session, "Disconnect", origin, HdmiTimeout);
				Common.Logging.v(TAG, "HDMI session "+session+" disconnected");
			}
			else
			{
				Common.Logging.e(TAG, "No existing HDMI session");
			}
		}
		Common.Logging.v(TAG, "handlePossibleHdmiSyncStateChange(): exit");
	}
	
	public synchronized void handleDmSyncStateChange(int inputNumber)
	{
		final int DmTimeout = 15; // seconds

		if (!IsAirMediaCanvasUp())
		{
			Common.Logging.e(TAG, "AirMediaCanvas not up - cannot display DM yet");
			return;
		}
		if (inputNumber > MAX_DM_INPUTS)
		{
			Common.Logging.e(TAG, "DM input number is "+inputNumber+"   Max allowed is "+MAX_DM_INPUTS);
			return;
		}
		Session session = mSessionMgr.findSession("DM", "", "", inputNumber);
		Originator origin = new Originator(RequestOrigin.Hardware);
		if (mStreamCtl.userSettings.getDmSync(inputNumber))
		{
			// if session is playing it should be stopped
			if (session != null)
			{
				
				Common.Logging.e(TAG, "DM sync is true with existing DM session in state "+session.state);
				mCrestore.doSynchronousSessionEvent(session, "Disconnect", origin, DmTimeout);
				Common.Logging.v(TAG, "DM session "+session+" disconnected");			
			}
			session = com.crestron.txrxservice.canvas.Session.createSession("DM", "DM"+inputNumber, null, inputNumber, null);
			Common.Logging.i(TAG, "Adding session " + session + " to sessionManager");
			mSessionMgr.add(session);
			mCrestore.doSynchronousSessionEvent(session, "Connect", origin, DmTimeout);
			Common.Logging.v(TAG, "DM session "+session+" connected");
		}
		else
		{
			// if session is playing it should be stopped
			if (session != null)
			{
				
				Common.Logging.e(TAG, "DM sync is false with existing DM session in state "+session.state);
	            mCrestore.doSynchronousSessionEvent(session, "Disconnect", origin, DmTimeout);
				Common.Logging.v(TAG, "DM session "+session+" disconnected");
			}
			else
			{
				Common.Logging.w(TAG, "No existing DM session");
			}
		}
		Common.Logging.v(TAG, "handleDmSyncStateChange(): exit");
	}
	
	public synchronized void handleDmHdcpBlankChange(boolean blank, int inputNumber)
	{
		if (inputNumber > MAX_DM_INPUTS)
		{
			Common.Logging.e(TAG, "DM input number is "+inputNumber+"   Max allowed is "+MAX_DM_INPUTS);
			return;
		}
		Session session = mSessionMgr.findSession("DM", "", "", inputNumber);
		if (session != null)
		{
			((DMSession) session).setHdcpBlank(blank);
		}
	}
	
	public void setSessionResolution(int streamId, int width, int height)
	{
		Session session = mSessionMgr.findSession(streamId);
		if (session != null && session instanceof AirBoardSession)
		{
	        Log.i(TAG, "setSessionResolution(): streamId="+streamId+" session="+session+" wxh="+width+"x"+height);
			session.setResolution(new AirMediaSize(width, height));
		}
		else
		{
			if (session == null)
				Log.e(TAG, "setSessionResolution(): streamId="+streamId+" could not find session");
			else
				Log.e(TAG, "setSessionResolution(): streamId="+streamId+" session="+this+" is not an Airboard session");
		}
	}
	
	public boolean sessionPlayTimedout(String sessionId)
	{
		Session session = mSessionMgr.findSession(sessionId);
		if (session == null)
			return true;
		return session.playTimedout;
	}
	
	public String getSessionId(Session session)
	{
		if (session != null)
			return session.sessionId();
		else
			return "Session Does Not Exist";
	}
	
    public Surface acquireSurface(Session session)
    {
        CanvasSurfaceAcquireResponse response = null;
        CanvasSourceSession canvasSourceSession = Session.session2CanvasSourceSession(session);
        try
        {
            if (Session.replace.streamId < 0)
            {
                if (mStreamCtl != null && mStreamCtl.dontStartAirMediaFlag())
                {
                    Common.Logging.i(TAG, "dontStartAirMediaFlag is on, skip surfaceAcquire for session: "
                            + getSessionId(session) + " with options=" + session.options);
                }
                else
                {
                    Common.Logging.i(TAG, "surfaceAcquire for session: " + getSessionId(session) + " with options="
                            + session.options);
                    response = mAirMediaCanvas.service().surfaceAcquireWithSession(canvasSourceSession);
                }
            }
            else
            {
                Common.Logging.i(TAG, "surfaceReplace for session: " + getSessionId(session) + " old sessionId="
                        + Session.replace.oldSessionId + " with options=" + session.options);
                response = mAirMediaCanvas.service().surfaceReplaceWithSession(Session.replace.oldSessionId,
                        canvasSourceSession);
            }
        }
        catch (android.os.RemoteException ex)
        {
            Common.Logging.e(TAG,
                    "exception encountered while calling surfaceAcquire for session: " + getSessionId(session));
            ex.printStackTrace();
            return null;
        }

        if (sessionPlayTimedout(getSessionId(session)))
        {
            // release the surface - it is too late for us to use it
            Common.Logging.e(TAG, "Timed out while calling surfaceAcquire for session: " + getSessionId(session));
            releaseSurface(getSessionId(session));
            return null;
        }

        if (response != null && response.isSucceeded())
        {
            if (response.surface == null)
            {
                Log.e(TAG, "acquireSurface for " + getSessionId(session) + " returned null surface from Canvas");
            }
            if (!response.surface.isValid())
            {
                Log.e(TAG, "acquireSurface for " + getSessionId(session) + " returned surface " + response.surface
                        + " invalid surface");
            }
            return response.surface;
        }
        else
        {
            Common.Logging.e(TAG,
                    "acquireSurface was unable to get surface from Canvas App for session: " + getSessionId(session));
            if (response != null && response.getErrorCode() == CanvasResponse.ErrorCodes.TimedOut)
            {
                Common.Logging.e(TAG, "CresCanvas.acquireSurface: fatal eror timed out while acquiring surface for sessionId="+getSessionId(session));
                mStreamCtl.RecoverTxrxService();
            }
            return null;
        }
    }
	
	public void releaseSurface(String sessionId)
	{
		CanvasResponse response = null;
		try {
			response = mAirMediaCanvas.service().surfaceRelease(sessionId);
		} catch(android.os.RemoteException ex)
		{
			Common.Logging.e(TAG, "exception encountered while calling surfaceRelease for session: "+sessionId);
			ex.printStackTrace();
		}
		if (response == null || !response.isSucceeded())
		{
			Common.Logging.e(TAG, "Canvas App failed to release surface for session: "+sessionId);
			if (response != null && response.getErrorCode() == CanvasResponse.ErrorCodes.TimedOut)
			{
			    Common.Logging.e(TAG, "CresCanvas.releaseSurface: fatal eror timed out while releasing surface for sessionId="+sessionId);
			    mStreamCtl.RecoverTxrxService();
			}
		}
	}
	
	public class SurfaceManager {
		Surface surfaces[] = new Surface[mStreamCtl.NumOfSurfaces];

		public SurfaceManager()
		{
			for (int i=0; i < mStreamCtl.NumOfSurfaces; i++)
				surfaces[i] = null;
		}
		
		public synchronized Surface streamId2Surface(int streamId)
		{
			if (streamId >= mStreamCtl.NumOfSurfaces)
			{
				Log.e(TAG, "streamId2Surface: invalid streamId="+streamId);
				return null;
			}
			return surfaces[streamId];
		}
		
		public synchronized int surface2StreamId(Surface s)
		{
			for (int i = 0; i < mStreamCtl.NumOfSurfaces; i++)
			{
				if (surfaces[i] == s)
					return i;
			}
			return -1;
		}
		
		public synchronized int getUnusedStreamId()
		{
			for (int i=0; i < mStreamCtl.NumOfSurfaces; i++)
			{
				if (surfaces[i] == null)
				{
					return i;
				}
			}
			return -1;
		}
		
		public synchronized int addSurface(Surface s)
		{

			int streamId = getUnusedStreamId();
			if (streamId >= 0)
			{
				addSurface(streamId, s);
			}
			return streamId;
		}
		
		public synchronized void addSurface(int streamId, Surface s)
		{
			if (streamId < 0 || streamId >= mStreamCtl.NumOfSurfaces)
			{
				Common.Logging.w(TAG, "invalid streamId "+streamId+" passed to addSurface");
				return;
			}
			if (s == null || !s.isValid())
			{
				Common.Logging.w(TAG, "null or invalid surface passed to addSurface for streamId "+streamId);
				return;
			}
			surfaces[streamId] = s;
		}
		
		public synchronized void removeSurface(Surface s)
		{
			for (int i=0; i < mStreamCtl.NumOfSurfaces; i++)
			{
				if (surfaces[i] == s)
					surfaces[i] = null;
			}
		}
		
		public synchronized void removeSurface(int streamId)
		{
			if (streamId < 0 || streamId >= mStreamCtl.NumOfSurfaces)
			{
				Log.e(TAG, "removeSurface: invalid streamId="+streamId);
			}
			else
			{
				surfaces[streamId] = null;
			}
		}
		
	    public void releaseAllSurfaces()
	    {
			for (int i=0; i < mStreamCtl.NumOfSurfaces; i++)
			{
				if (streamId2Surface(i) != null)
				{
					if (CresCanvas.useCanvasSurfaces)
						mStreamCtl.deleteSurface(i);
					removeSurface(i);
				}
			}
	    }
	}
    
	public CanvasSourceRequest createCanvasSourceRequest(long id, String sessionId, CanvasSourceAction action)
	{
	    CanvasSourceTransaction t = new CanvasSourceTransaction(sessionId, action);
	    List<CanvasSourceTransaction> transactionList = new ArrayList<CanvasSourceTransaction>();
	    transactionList.add(t);
	    CanvasSourceRequest request = new CanvasSourceRequest(1, transactionList);
	    return request;
	}
	
	public void CanvasConsoleCommand(String cmd)
	{
		Log.i(TAG, "CanvasConsoleCommand: cmd="+cmd);
		String[] args = cmd.split("\\s+");
		if (args.length == 0)
			return;
		if (args[0].equalsIgnoreCase("show"))
		{
			mSessionMgr.logSessionStates("CanvasConsoleCmd");
		} 
		else if (args[0].equalsIgnoreCase("stop"))
		{
			String sessionId = (args.length == 1) ? "all" : args[1];
			if (sessionId.equalsIgnoreCase("all"))
			{
				mSessionMgr.stopAllSessions(new Originator(RequestOrigin.ConsoleCommand));
			}
			else
			{
				Session session = mSessionMgr.findSession(sessionId);
				if (session != null)
	            	getCrestore().doSynchronousSessionEvent(session, "stop", new Originator(RequestOrigin.ConsoleCommand), 60);
			}
		}
		else if (args[0].equalsIgnoreCase("fail"))
		{
			handleCodecFailure();
		}
		else if (args[0].equalsIgnoreCase("dm"))
		{
			String sessionId = args[1];
			Session session = mSessionMgr.findSession(sessionId);
			int r = Integer.parseInt(args[2]);
			int g = Integer.parseInt(args[3]);
			int b = Integer.parseInt(args[4]);
			if (session != null)
			{
				if (session.type == SessionType.DM)
					((DMSession)session).drawColor(r, g, b);
			}
		}
		else if (args[0].equalsIgnoreCase("testPriorityScheduler"))
		{
			for (int i=0; i < 10; i++)
			{
				final int jobNo = i;
				Runnable r = new Runnable () {
					@Override
					public void run()
					{
						int sleepTime = (int) (Math.random()*10 + 0.5);
						Log.i(TAG, "testPriorityScheduler(): Running job "+jobNo+" sleepTime="+sleepTime);
						try { Thread.sleep(sleepTime*1000); } catch (Exception ex) {};
					}
				};
				mCrestore.sessionScheduler.queue(r, jobNo%2);
			}
			try { Thread.sleep(15*1000); } catch (Exception ex) {};
			Log.i(TAG, "testPriorityScheduler(): shutting down the scheduler");
			mCrestore.sessionScheduler.shutdownNow();
			mCrestore.sessionScheduler = new PriorityScheduler("TxRx.canvas.crestore.sessionScheduler");;
		}
		else if (args[0].equalsIgnoreCase("testPrioritySchedulerShutDown"))
		{
			new Thread(new Runnable() {
				@Override 
				public void run()
				{
					final CountDownLatch latch = new CountDownLatch(1);
					Runnable r = new Runnable () {
						@Override
						public void run()
						{
							int sleepTime = 100;
							Log.i(TAG, "testPrioritySchedulerShutDown(): Running job sleepTime="+sleepTime);
							try { Thread.sleep(sleepTime*1000); } catch (Exception ex) {};
							Log.i(TAG, "testPrioritySchedulerShutDown():doing countdown on latch");
							latch.countDown();
						}
					};
					mCrestore.sessionScheduler.queue(r, PriorityScheduler.NORMAL_PRIORITY);
					try {
						latch.await();
					} 
					catch(Exception e)
					{
						Common.Logging.i(TAG,  "testPrioritySchedulerShutDown(): interrupted exception for latch await");
					}
				}
			}).start();
			try { Thread.sleep(5*1000); } catch (Exception ex) {};
			Log.i(TAG, "testPrioritySchedulerShutDown(): shutting down the scheduler");
			mCrestore.sessionScheduler.shutdownNow();
			Log.i(TAG, "testPrioritySchedulerShutDown(): shutdown complete");
			mCrestore.sessionScheduler = new PriorityScheduler("TxRx.canvas.crestore.sessionScheduler");;
		}
		else if (args[0].equalsIgnoreCase("verifysubscription"))
		{
			if (mCrestore.verifyCrestore())
				Log.i(TAG,"verified subscription successfully");
			else
				Log.i(TAG,"faield to verify subscription");
		}
		else if (args[0].equalsIgnoreCase("mute"))
		{
			if (args.length <= 1)
			{
				Log.i(TAG,"must include sessionId to be muted");
				return;
			}
			String sessionId = args[1];
			Log.i(TAG,"Audio mute for session through CanvasSourceRequest: "+sessionId);
			CanvasSourceRequest r = createCanvasSourceRequest(1, sessionId, CanvasSourceAction.Mute);
			mCanvasSourceManager.processRequest(r);
		}
		else if (args[0].equalsIgnoreCase("unmute"))
		{
			if (args.length <= 1)
			{
				Log.i(TAG,"must include sessionId to be muted");
				return;
			}
			String sessionId = args[1];
			Log.i(TAG,"Audio unmute for session through CanvasSourceRequest: "+sessionId);
			CanvasSourceRequest r = createCanvasSourceRequest(1, sessionId, CanvasSourceAction.UnMute);
			mCanvasSourceManager.processRequest(r);
		}
		else if (args[0].equalsIgnoreCase("miracast"))
		{
			if (args.length <= 2)
			{
				Log.i(TAG,"must include pause/resume command and sessionid of miracast session");
				return;
			}
			String command = args[1];
			String sessionId = args[2];
			Session session = mSessionMgr.findSession(sessionId);
			if (session != null)
			{
				if (session.type == SessionType.AirMedia && session.getAirMediaType() == SessionAirMediaType.Miracast)
				{
					if (command.equalsIgnoreCase("pause")) {
						Log.i(TAG,"pause miracast session "+sessionId);
						mStreamCtl.getStreamPlay().wfdPause(session.streamId);
						Log.i(TAG,"release surface");
						releaseSurface(sessionId);
					} else if (command.equalsIgnoreCase("resume")) {
						Log.i(TAG,"resume miracast session "+sessionId);
						Surface s = session.acquireSurface();
						Log.i(TAG,"acquire surface "+s);
						((AirMediaSession)session).setSurface(s);
						mStreamCtl.getStreamPlay().wfdResume(session.streamId);
						session.layoutUpdate();
					} else {
						Log.i(TAG,"Invalid command "+command);
					}
				} else {
					Log.i(TAG, "Session "+session+" is not a miracast session");
				}
			}
			else
			{
				Log.i(TAG, "Session "+sessionId+" not found in list of sessions");
			}
		}
        else if (args[0].equalsIgnoreCase("managerlist"))
        {	
            Collection<Session> collection = mSessionMgr.sessions();
            for(Session session : collection) {                
                Log.i(TAG, "managerlist Network session: " + session);
                Log.i(TAG, "managerlist Network id: " + session.id());
                Log.i(TAG, "managerlist Network getUserLabel: " + session.getUserLabel());
                Log.i(TAG, "managerlist Network getInputNumber: " + session.getInputNumber());
                Log.i(TAG, "managerlist Network sessionId: " + session.sessionId());
                Log.i(TAG, "managerlist Network streamId: " + session.streamId);				
                Log.i(TAG, "managerlist Network isStopped: " + session.isStopped());
                Log.i(TAG, "managerlist Network isPlaying: " + session.isPlaying());
                Log.i(TAG, "managerlist Network isPaused: " + session.isPaused());
                Log.i(TAG, "managerlist Network isAudioMuted: " + session.isAudioMuted);
                
                Log.i(TAG, "managerlist Network isConnecting: " + session.isConnecting());
                Log.i(TAG, "managerlist Network getState: " + session.getState());
                Log.i(TAG, "managerlist Network getType: " + session.getType());
                Log.i(TAG, "managerlist Network getUrl: " + session.getUrl());

                Log.i(TAG, "managerlist Network getVolume: " + mStreamCtl.userSettings.getVolume());

                if(session instanceof NetworkStreamSession)
                {
                    StreamState st = session.mStreamCtl.getCurrentStreamState(session.streamId);
                    Log.i(TAG, "managerlist Network streaming state: " + st.name() + "(" + st.getValue() + ")");
                    Log.i(TAG, "managerlist Network getBuffer: " + ((NetworkStreamSession)session).getBuffer());
                    Log.i(TAG, "managerlist Network getVolume: " + ((NetworkStreamSession)session).getVolume());
                    Log.i(TAG, "managerlist Network getStatistics: " + ((NetworkStreamSession)session).getStatistics());
                }
            }


            mStreamCtl.testfindCamera();


            //mSessionMgr.disconnectAllSessions(new Originator(RequestOrigin.Error));
        }
	}
}
