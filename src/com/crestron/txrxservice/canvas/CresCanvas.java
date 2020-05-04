package com.crestron.txrxservice.canvas;

import com.crestron.airmedia.canvas.channels.ipc.CanvasResponse;
import com.crestron.airmedia.canvas.channels.ipc.CanvasSurfaceAcquireResponse;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSize;
import com.crestron.airmedia.utilities.Common;
import com.crestron.airmedia.utilities.TimeSpan;
import com.crestron.txrxservice.HDMIInputInterface;
import com.crestron.txrxservice.canvas.Session;
import com.crestron.txrxservice.canvas.HDMISession;
import com.crestron.txrxservice.canvas.DMSession;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

import android.graphics.Rect;
import android.os.ConditionVariable;
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
	public static final int MAX_CONNECTED = 10;
	public static final int MAX_PRESENTING = 2;
	private String prevHdmiResolution[] = new String[MAX_HDMI_INPUTS+1];
	private String prevHdmiSyncStatus[] = new String[MAX_HDMI_INPUTS+1];
	private AtomicBoolean codecFailure = new AtomicBoolean(false);
	private AtomicBoolean avfRestart = new AtomicBoolean(false);
	public SurfaceManager mSurfaceMgr=null;

	public CresCanvas(com.crestron.txrxservice.CresStreamCtrl streamCtl)
	{
		mSurfaceMgr = new SurfaceManager(); //TODO remove once we have real surface manager
		Log.i(TAG, "Creating CresCanvas");
		mStreamCtl = streamCtl;
		mSessionMgr = new SessionManager(this);
		mCrestore = new CanvasCrestore(mStreamCtl, this, mSessionMgr);
		mCanvasSourceManager = new CanvasSourceManager(mStreamCtl, mCrestore);
		for (int i=0; i <= MAX_HDMI_INPUTS; i++)
		{
			prevHdmiResolution[i] = "0x0";
			prevHdmiResolution[i] = "false";
		}
		mCrestore.sendCssRestart();
		//clearAVF();
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
			return;
		Common.Logging.i(TAG, "canvasHasStarted(): ------ canvas is ready -------");
		// send an empty list in layout update to indicate no sessions
		Common.Logging.i(TAG, "canvasHasStarted(): Layout update");
		mSessionMgr.doLayoutUpdate();
		// send an empty event map in session event to indicate no sessions to AVF
		//clearAVF();
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
	
	public void clear()
	{
		// Stop and remove all sessions
		mSessionMgr.clearAllSessions();
		// clear all surfaces and streamIds
		mSurfaceMgr.releaseAllSurfaces();
		clearAVF();
	}
	
	public synchronized void handleReceiverDisconnected()
	{
		Common.Logging.i(TAG, "*********************** handleReceiverDisconnected ***********************");
		// Stop all sessions
		clear();
		// Restart of HDMI/DM will be handled when service reconnects by canvasHasStarted()
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
		}
		codecFailure.set(false);
		// Restart HDMI/DM if possible
		Common.Logging.i(TAG, "handleCodecFailure(): restarting HDMI if needed");
		mStreamCtl.canvasHdmiSyncStateChange(false);
		Common.Logging.i(TAG, "handleCodecFailure(): restarting DM if needed");
		mStreamCtl.canvasDmSyncStateChange();
	}
	
	public void handleAvfRestart()
	{
		Common.Logging.i(TAG, "*********************** handleAvfRestart ***********************");
		if (!codecFailure.get())
		{
			// called from cresstore callback thread and so must be run asynchronously so sessionResponse is not blocked
			Runnable r = new Runnable() { @Override public void run() { mSessionMgr.sendAllSessionsInSessionEvent(new Originator(RequestOrigin.Error)); } };
			scheduler.execute(r);
		}
		else
		{
			avfRestart.set(true);
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
			return;
		}
		if (inputNumber > MAX_HDMI_INPUTS)
		{
			Common.Logging.e(TAG, "HDMI input number is "+inputNumber+"   Max allowed is "+MAX_HDMI_INPUTS);
			return;
		}
		String res = hdmiInput.getHorizontalRes() + "x" + hdmiInput.getVerticalRes();
		Session session = mSessionMgr.findSession("HDMI", "", "", inputNumber);
		Common.Logging.i(TAG, "handlePossibleHdmiSyncStateChange(): syncStatus="+hdmiInput.getSyncStatus()+"    resolution="+res+
				"     checkForChange="+changeCheck);
		if (changeCheck && (hdmiInput.getSyncStatus().equalsIgnoreCase(prevHdmiSyncStatus[inputNumber]) &&
				res.equalsIgnoreCase(prevHdmiResolution[inputNumber])))
		{
			Common.Logging.e(TAG, "handlePossibleHdmiSyncStateChange(): no change in resolution or sync status");
			return;
		}
		prevHdmiSyncStatus[inputNumber] = hdmiInput.getSyncStatus();
		prevHdmiResolution[inputNumber] = res;
		Originator origin = new Originator(RequestOrigin.Hardware);
		if (hdmiInput.getSyncStatus().equalsIgnoreCase("true"))
		{
			// HDMI sync is true 
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
			}
			session = com.crestron.txrxservice.canvas.Session.createSession("HDMI", "HDMI"+inputNumber, null, 1, null);
			((HDMISession) session).setHdmiInput(hdmiInput);
			Common.Logging.i(TAG, "Adding session " + session + " to sessionManager");
			mSessionMgr.add(session);
			mCrestore.doSynchronousSessionEvent(session, "Connect", origin, HdmiTimeout);
		}
		else
		{
			// if session is playing it should be stopped
			if (session != null)
			{
				
				Common.Logging.e(TAG, "HDMI sync is false with existing HDMI session in state "+session.state);
	            mCrestore.doSynchronousSessionEvent(session, "Disconnect", origin, HdmiTimeout);
			}
			else
			{
				Common.Logging.e(TAG, "No existing HDMI session");
			}
		}
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
			}
			session = com.crestron.txrxservice.canvas.Session.createSession("DM", "DM"+inputNumber, null, inputNumber, null);
			Common.Logging.i(TAG, "Adding session " + session + " to sessionManager");
			mSessionMgr.add(session);
			mCrestore.doSynchronousSessionEvent(session, "Connect", origin, DmTimeout);
		}
		else
		{
			// if session is playing it should be stopped
			if (session != null)
			{
				
				Common.Logging.e(TAG, "DM sync is false with existing DM session in state "+session.state);
	            mCrestore.doSynchronousSessionEvent(session, "Disconnect", origin, DmTimeout);
			}
			else
			{
				Common.Logging.w(TAG, "No existing DM session");
			}
		}
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
			session.setResolution(new AirMediaSize(width, height));
		}
	}
	
	public Surface acquireSurface(String sessionId)
	{
		CanvasSurfaceAcquireResponse response = null;
		try {
			response = mAirMediaCanvas.service().surfaceAcquire(sessionId);
		} catch(android.os.RemoteException ex)
		{
			Common.Logging.e(TAG, "exception encountered while calling surfaceAcquire for session: "+sessionId);
			return null;
		}
		if (response != null && response.isSucceeded())
		{
			if (response.surface == null)
			{
				Log.e(TAG, "acquireSurface for "+sessionId+" returned null surface from Canvas");
			}
			if (!response.surface.isValid())
			{
				Log.e(TAG, "acquireSurface for "+sessionId+" returned surface "+response.surface+" invalid surface");
			}
			return response.surface;
		} else {
			Common.Logging.e(TAG, "getSurface was unable to get surface from Canvas App for session: "+sessionId);
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
		}
		if (response == null || !response.isSucceeded())
			Common.Logging.e(TAG, "Canvas App failed to release surface for session: "+sessionId);
	}
	
	public class SurfaceManager {
		Surface surfaces[] = new Surface[MAX_PRESENTING];
		
		public SurfaceManager()
		{
			for (int i=0; i < MAX_PRESENTING; i++)
				surfaces[i] = null;
		}
		
		public synchronized Surface streamId2Surface(int streamId)
		{
			if (streamId >= MAX_PRESENTING)
			{
				Log.e(TAG, "streamId2Surface: invalid streamId="+streamId);
				return null;
			}
			return surfaces[streamId];
		}
		
		public synchronized int surface2StreamId(Surface s)
		{
			for (int i = 0; i < MAX_PRESENTING; i++)
			{
				if (surfaces[i] == s)
					return i;
			}
			return -1;
		}
		
		public synchronized int getUnusedStreamId()
		{
			for (int i=0; i < MAX_PRESENTING; i++)
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
			surfaces[streamId] = s;
		}
		
		public synchronized void removeSurface(Surface s)
		{
			for (int i=0; i < MAX_PRESENTING; i++)
			{
				if (surfaces[i] == s)
					surfaces[i] = null;
			}
		}
		
		public synchronized void removeSurface(int streamId)
		{
			if (streamId >= MAX_PRESENTING)
			{
				Log.e(TAG, "removeSurface: invalid streamId="+streamId);
			}
			surfaces[streamId] = null;
		}
		
	    public void releaseAllSurfaces()
	    {
			for (int i=0; i < MAX_PRESENTING; i++)
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
	}
}
