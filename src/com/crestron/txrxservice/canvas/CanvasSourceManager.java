package com.crestron.txrxservice.canvas;

import java.util.List;
import java.util.UUID;

import com.crestron.airmedia.canvas.channels.ipc.*;
import com.crestron.airmedia.utilities.Common;
import com.crestron.airmedia.utilities.TimeSpan;
import com.crestron.txrxservice.CresStreamCtrl;
import com.crestron.txrxservice.canvas.CanvasCrestore.SessionEvent;
import com.crestron.txrxservice.canvas.CanvasCrestore.SessionEventMapEntry;
import com.crestron.txrxservice.canvas.CanvasCrestore.TransactionData;

import android.util.Log;

public class CanvasSourceManager {
    private static final String TAG = "TxRx.canvas.sourcemanager";
    private static final int CanvasSourceRequestTimeout = 60;
    
    CanvasSourceManager(CresStreamCtrl streamCtrl, CanvasCrestore crestore) {
    	mStreamCtl = streamCtrl;
    	mCresStore = crestore;
    }
    
    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// FIELDS
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private final Object mLock = new Object();
    private CresStreamCtrl mStreamCtl = null;
    private CanvasCrestore mCresStore = null;

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// PROPERTIES
    ////////////////////////////////////////////////////////////////////////////////////////////////
    

    public CresStreamCtrl streamCtrl() { return mStreamCtl;}

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// DEFINITIONS
    ////////////////////////////////////////////////////////////////////////////////////////////////

    
    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// SERVICE
    ////////////////////////////////////////////////////////////////////////////////////////////////
    
    public ICanvasSourceManager service() { return mService; }
    
    private final CanvasSourceManagerService mService = new CanvasSourceManagerService();
    
    private class CanvasSourceManagerService extends ICanvasSourceManager.Stub {
    	CanvasSourceManagerService() {
//            try {
//            	service_.register(observers_);
//            } catch (Exception e) {
//                Common.Logging.e(TAG, "VideoPlayerService  EXCEPTION  " + e + "while registering observer");
//            }
    	}   	
        
        public CanvasResponse sourceRequest(CanvasSourceRequest request) {
            synchronized (mLock) {
                Common.Logging.i(TAG, "canvassourcemanager.sourcerequest   ----- CanvasSourceRequest event -----");
                return processRequest(request);
            }
        }
        
        public void sourceLayout(CanvasLayout layout)
        {
	    	Common.Logging.i(TAG, "canvassourcemanager.sourcelayout  ----- CanvasSourceLayout event -----");
            processLayout(layout.sources);
        }
        
        public void status(CanvasStatus status)
        {
        	mStreamCtl.mCanvas.mAirMediaCanvas.canvasIsReady(status.isReady);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// METHODS
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public CanvasSourceResponse processRequest(CanvasSourceRequest request)
    {
    	CanvasSourceResponse response = new CanvasSourceResponse();
    	CanvasCrestore.SessionEvent e = mCresStore.sourceRequestToEvent(request, response); 
 
    	if (e == null)
    		return response;
 
    	Common.Logging.i(TAG, "processRequest(): processing canvas source request");    	
    	Originator originator = new Originator(RequestOrigin.CanvasSourceRequest, response);
    	boolean success = mCresStore.doSynchronousSessionEvent(e, originator, CanvasSourceRequestTimeout);
		Common.Logging.i(TAG,"processRequest(): completed canvas source request - success="+success);
    	if (response.getErrorCode() == CanvasResponse.ErrorCodes.OK)
    	{
    		if (!success)
    		{
    			Common.Logging.i(TAG,"Timeout while processing canvas source request with transactionId="+e.transactionId);
    			response.setError(CanvasResponse.ErrorCodes.TimedOut, "Timedout after "+CanvasSourceRequestTimeout+" seconds");
    		}
    	}
    	return response;
    }
    
    void processLayout(final List<CanvasSourceLayout> sources)
    {
    	for (CanvasSourceLayout srcLayout : sources)
    	{
    		Session s = mCresStore.mSessionMgr.findSession(srcLayout.sessionId);
    		if (s != null && s.type == SessionType.DM)
    		{
    			((DMSession)s).updateDmWindow(srcLayout.left, srcLayout.top, srcLayout.width, srcLayout.height);
    		}
    	}
    }
    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// EVENTS
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /// REMOTE CONNECTION

    private void handleRemoteException(Exception e) {
        Common.Logging.w(TAG, "canvassourcemanager.exception.remote    EXCEPTION  " + e + "  " + Log.getStackTraceString(e));
    }
    
    // Just to get around the fact that CanvasResponse has protected methods for setting the object
    public class LocalCanvasResponse extends CanvasResponse {
    	@Override
    	public void setErrorCode(int code) { super.setErrorCode(code); }
    	
    	@Override
    	public void setErrorMessage(String message) { super.setErrorMessage(message); }
    	
    	@Override
    	public void setError(int code, String message) { super.setError(code, message); }
    }

}

