package com.crestron.txrxservice.canvas;

import com.crestron.airmedia.utilities.Common;
import com.crestron.airmedia.utilities.TimeSpan;
import com.crestron.txrxservice.HDMIInputInterface;
import com.crestron.txrxservice.canvas.Session;
import com.crestron.txrxservice.canvas.HDMISession;
import com.crestron.txrxservice.canvas.DMSession;

import java.util.concurrent.*;
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

    public static final String TAG = "TxRx Canvas"; 
	private static final int MAX_HDMI_INPUTS = 1;
	private static final int MAX_DM_INPUTS = 1;
	public static final int MAX_CONNECTED = 10;
	public static final int MAX_PRESENTING = 2;
	private String prevHdmiResolution[] = new String[MAX_HDMI_INPUTS+1];
	private String prevHdmiSyncStatus[] = new String[MAX_HDMI_INPUTS+1];
	public SurfaceManager mSurfaceMgr=null;

	public CresCanvas(com.crestron.txrxservice.CresStreamCtrl streamCtl)
	{
		mSurfaceMgr = new SurfaceManager(); //TODO remove once we have real surface manager
		Log.i(TAG, "Creating CresCanvas");
		mStreamCtl = streamCtl;
		mSessionMgr = new SessionManager(this);
		mCrestore = new CanvasCrestore(mStreamCtl, this, mSessionMgr);
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
	
	public void setWindows()
	{
		mStreamCtl.setCanvasWindows();
	}
	
	public Rect getWindow(int streamId)
	{
		return mStreamCtl.getWindowDimensions(streamId);
	}
	
	public void showWindow(int streamId)
	{
		mStreamCtl.showCanvasWindow(streamId);
	}
	
	public void hideWindow(int streamId)
	{
		mStreamCtl.hideCanvasWindow(streamId);
	}
	
	public synchronized void handlePossibleHdmiSyncStateChange(int inputNumber, HDMIInputInterface hdmiInput)
	{
		if (inputNumber > MAX_HDMI_INPUTS)
		{
			Common.Logging.e(TAG, "HDMI input number is "+inputNumber+"   Max allowed is "+MAX_HDMI_INPUTS);
			return;
		}
		String res = hdmiInput.getHorizontalRes() + "x" + hdmiInput.getVerticalRes();
		Session session = mSessionMgr.findSession("HDMI", "", "", inputNumber);
		Common.Logging.i(TAG, "handlePossibleHdmiSyncStateChange(): syncStatus="+hdmiInput.getSyncStatus()+"    resolution="+res);
		if (hdmiInput.getSyncStatus().equalsIgnoreCase(prevHdmiSyncStatus[inputNumber]) &&
				res.equalsIgnoreCase(prevHdmiResolution[inputNumber]))
		{
			Common.Logging.e(TAG, "handlePossibleHdmiSyncStateChange(): no change in resolution or sync status");
			return;
		}
		prevHdmiSyncStatus[inputNumber] = hdmiInput.getSyncStatus();
		prevHdmiResolution[inputNumber] = res;
		if (hdmiInput.getSyncStatus().equalsIgnoreCase("true"))
		{
			// HDMI sync is true 
			if (res.equals("0x0"))
			{
				Common.Logging.e(TAG, "HDMI sync is true yet resolution is 0x0");
				return;
			}
//			// if session is playing it should be stopped
//			if (session != null)
//			{
//				
//				Common.Logging.e(TAG, "HDMI sync is true with existing HDMI session in state "+session.state);
//	            mCrestore.sendSessionEvent(UUID.randomUUID().toString(), session.sessionId(), "Disconnect", "HDMI", null, Integer.valueOf(1));
//			}
			session = com.crestron.txrxservice.canvas.Session.createSession("HDMI", "HDMI"+inputNumber, null, 1, null);
			((HDMISession) session).setHdmiInput(hdmiInput);
			if (session != null)
			{
				Common.Logging.i(TAG, "Adding session " + session + " to sessionManager");
				mSessionMgr.add(session);
			}
			else
			{
				Common.Logging.i(TAG, "Connect not create requested session for connect command");
			}
            mCrestore.doSessionEvent(session, "Connect", new Originator(RequestOrigin.Unknown));
            // For now HDMI sessions should immediately be transitioned to Play
            mCrestore.doSessionEvent(session, "Play", new Originator(RequestOrigin.Unknown));
		}
		else
		{
			// if session is playing it should be stopped
			if (session != null)
			{
				
				Common.Logging.e(TAG, "HDMI sync is false with existing HDMI session in state "+session.state);
	            mCrestore.doSessionEvent(session, "Disconnect", new Originator(RequestOrigin.Unknown));
			}
			else
			{
				Common.Logging.e(TAG, "No existing HDMI session");
			}
		}
	}
	
	public synchronized void handleDmSyncStateChange(int inputNumber)
	{
		if (inputNumber > MAX_DM_INPUTS)
		{
			Common.Logging.e(TAG, "DM input number is "+inputNumber+"   Max allowed is "+MAX_DM_INPUTS);
			return;
		}
		Session session = mSessionMgr.findSession("DM", "", "", inputNumber);
		if (mStreamCtl.userSettings.getDmSync(inputNumber))
		{
			// if session is playing it should be stopped
			if (session != null)
			{
				
				Common.Logging.e(TAG, "DM sync is true with existing DM session in state "+session.state);
	            mCrestore.sendSessionEvent(UUID.randomUUID().toString(), session, "disconnect");
			}
			session = com.crestron.txrxservice.canvas.Session.createSession("DM", "DM"+inputNumber, null, inputNumber, null);
			if (session != null)
			{
				Common.Logging.i(TAG, "Adding session " + session + " to sessionManager");
				mSessionMgr.add(session);
			}
			else
			{
				Common.Logging.i(TAG, "Connect not create requested DM session for connect command");
			}
            mCrestore.doSessionEvent(session, "Connect", new Originator(RequestOrigin.Unknown));
            // For now DM sessions should immediately be transitioned to Play
            mCrestore.doSessionEvent(session, "Play", new Originator(RequestOrigin.Unknown));
		}
		else
		{
			// if session is playing it should be stopped
			if (session != null)
			{
				
				Common.Logging.e(TAG, "HDMI sync is false with existing DM session in state "+session.state);
	            mCrestore.doSessionEvent(session, "Disconnect", new Originator(RequestOrigin.Unknown));
			}
			else
			{
				Common.Logging.e(TAG, "No existing DM session");
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
	
	
	public class SurfaceManager {
		Surface surfaces[] = new Surface[MAX_PRESENTING];
		
		public SurfaceManager()
		{
			for (int i=0; i < MAX_PRESENTING; i++)
				surfaces[i] = null;
		}
		
		public Surface streamId2Surface(int streamId)
		{
			if (streamId >= MAX_PRESENTING)
			{
				Log.e(TAG, "streamId2Surface: invalid streamId="+streamId);
				return null;
			}
			return surfaces[streamId];
		}
		
		public int surface2StreamId(Surface s)
		{
			for (int i = 0; i < MAX_PRESENTING; i++)
			{
				if (surfaces[i] == s)
					return i;
			}
			return -1;
		}
		
		public int getUnusedStreamId()
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
		
		public int addSurface(Surface s)
		{

			int streamId = getUnusedStreamId();
			if (streamId >= 0)
			{
				addSurface(streamId, s);
			}
			return streamId;
		}
		
		public void addSurface(int streamId, Surface s)
		{
			surfaces[streamId] = s;
		}
		
		public void removeSurface(Surface s)
		{
			for (int i=0; i < MAX_PRESENTING; i++)
			{
				if (surfaces[i] == s)
					surfaces[i] = null;
			}
		}
		
		public void removeSurface(int streamId)
		{
			if (streamId >= MAX_PRESENTING)
			{
				Log.e(TAG, "removeSurface: invalid streamId="+streamId);
			}
			surfaces[streamId] = null;
		}
	}
}
