package com.crestron.txrxservice.canvas;

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
    private static final String TAG = "CanvasSourceManager";
    private static final long INVALID_SESSION_ID = (long) 0;
    
    CanvasSourceManager(CresStreamCtrl streamCtrl) {
    	mStreamCtl = streamCtrl;
        
    }
    
    private CanvasSourceManager self() { return this; }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// FIELDS
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private final Object mLock = new Object();
    private CresStreamCtrl mStreamCtl = null;
    private CresCanvas mCanvas = mStreamCtl.mCanvas;
    private CanvasCrestore mCresStore = mCanvas.mCrestore;

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
    
    ICanvasSourceManager service() { return mService; }
    
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
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// METHODS
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public CanvasSourceResponse processRequest(CanvasSourceRequest request)
    {
    	CanvasSourceResponse response = new CanvasSourceResponse();
    	CanvasCrestore.SessionEvent e = mCresStore.sourceRequestToEvent(request, response); 
    	TimeSpan timeout = TimeSpan.fromSeconds(60);
 
    	if (e == null)
    		return response;
    	
    	boolean timedout = false;
		TimeSpan startTime = TimeSpan.now();
    	Waiter waiter = new Waiter();
    	Originator originator = new Originator(RequestOrigin.CanvasSourceRequest, response);
    	mCresStore.doSessionEvent(e, originator, waiter);
    	timedout = waiter.waitForSignal(timeout);
    	if (response.getErrorCode() == CanvasResponse.ErrorCodes.OK)
    	{
    		if (timedout)
    		{
    			Common.Logging.i(TAG,"Timeout while processing canvas source request with transactionId="+e.transactionId);
    			response.setError(CanvasResponse.ErrorCodes.TimedOut, "Timedout after "+timeout.seconds()+" seconds");
    		}
    	}
    	Common.Logging.i(TAG, "Canvas source request completed in "+TimeSpan.now().subtract(startTime).toString()+" seconds");    	
    	return response;
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

