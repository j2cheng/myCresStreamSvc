package com.crestron.txrxservice.canvas;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.Collection;
import java.util.List;
import java.util.EnumSet;
import java.util.Timer;
import java.util.TimerTask;
import java.net.InetAddress;
import java.net.UnknownHostException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.crestron.txrxservice.CresStreamCtrl;
import com.crestron.txrxservice.canvas.CresCanvas;
import com.crestron.txrxservice.canvas.Originator;
import com.crestron.txrxservice.canvas.RequestOrigin;
import com.crestron.txrxservice.canvas.Session;
import com.crestron.airmedia.canvas.channels.ipc.CanvasPlatformType;
import com.crestron.airmedia.canvas.channels.ipc.IAirMediaCanvas;
import com.crestron.airmedia.receiver.m360.IAirMediaReceiver;
import com.crestron.airmedia.receiver.m360.models.AirMediaReceiver;
import com.crestron.airmedia.receiver.m360.models.AirMediaSession;
import com.crestron.airmedia.receiver.m360.models.AirMediaSessionManager;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaPlatforms;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaReceiverLoadedState;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaReceiverMirroringAssist;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaReceiverProperties;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaReceiverResolutionMode;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaReceiverState;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSessionConnectionState;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSessionScreenPosition;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSessionScreenLayout;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSessionStreamingState;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSessionVideoSource;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSessionVideoType;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSize;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSessionInfo;
import com.crestron.airmedia.utilities.ViewBase;
import com.crestron.airmedia.utilities.ViewBase.Size;
import com.crestron.airmedia.utilities.Common;
import com.crestron.airmedia.utilities.delegates.MulticastChangedDelegate;
import com.crestron.airmedia.utilities.delegates.MulticastChangedWithReasonDelegate;
import com.crestron.airmedia.utilities.delegates.MulticastMessageDelegate;
import com.crestron.airmedia.utilities.TimeSpan;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.TextureView;
import android.view.View;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.graphics.Matrix; 
import android.graphics.Canvas;
import android.graphics.Color;

//public class AirMediaSplashtop 
public class AirMediaCanvas
{
    CresStreamCtrl mStreamCtl;
    Context mContext;
    public static final String TAG = "TxRx AirMediaCanvas"; 
    public static final String AIRMEDIA_CANVAS_PACKAGE = "com.crestron.airmedia.receiver.m360"; // will likely change to different app
    public static final String AIRMEDIA_CANVAS_BIND = "com.crestron.airmedia.canvas.BIND";
	private static final boolean DEBUG = false;

    private final MyReentrantLock orderedLock	= new MyReentrantLock(true); // fairness=true, makes lock ordered
    public CountDownLatch serviceConnectedLatch=null;
    public CountDownLatch serviceDisconnectedLatch=null;
    public CountDownLatch receiverLoadedLatch=null;
    public CountDownLatch receiverStoppedLatch=null;
    public CountDownLatch receiverStartedLatch=null;
    public CountDownLatch startupCompleteLatch=null;
    
    private final Object connectLock = new Object();
    private final Object startStopReceiverLock = new Object();

	private IAirMediaCanvas service_=null;

	private boolean isServiceConnected = false;
	private boolean isCanvasAppStarted = false;
	private boolean isAirMediaCanvasUp = false;
	private boolean requestServiceConnection = false;
	
	private boolean quittingAirMediaCanvasService = false;

    private class MyReentrantLock extends ReentrantLock {
    	MyReentrantLock(boolean fair) {
    		super(fair);
    	}
    	
    	String getThreadId()
    	{
    		Thread owner = this.getOwner();
    		if (owner == null)
    			return "null";
    		else
    			return Integer.toHexString((int)(owner.getId()));
    	}
    	
    	void lock(String s)
    	{
    		Common.Logging.i(TAG, s + " about to take reentrantLock     Current owner tid=" + getThreadId() + "  queueLength=" + getQueueLength());
    		lock();
    		Common.Logging.i(TAG, "reentrantLock taken by " + s + "   Owner tid=" + getThreadId() + "  queueLength=" + getQueueLength());
    	}
    	
    	void unlock(String s)
    	{
    		unlock();
    		Common.Logging.i(TAG, "reentrantLock released by " + s);
    	}
    }
 
    public void launchCanvasService()
    {
    	Common.Logging.i(TAG, "launchCanvasService........");
    	new Thread(new Runnable() {
    		@Override
    		public void run() {
    			orderedLock.lock("launchCanvasService");
    			try {
    		    	boolean serviceSuccessfullyStarted = false;
    		    	while (!serviceSuccessfullyStarted)
    		    	{
    		    		serviceSuccessfullyStarted = connectAndStartCanvasService();
    		    		if (!serviceSuccessfullyStarted) {
    		    			Common.Logging.e(TAG, "launchCanvasService failed, trying again in 5 seconds.....");
    		    			sleep(5000);
    		    		}
    		    	}
    			} finally {
    				orderedLock.unlock("launchCanvasService");
    		    	Common.Logging.i(TAG, "launchCanvasService exit ........");
    			}
    		}
    	}).start();
    }
    
    public AirMediaCanvas(CresStreamCtrl streamCtl) 
    {
    	mStreamCtl = streamCtl;
    	mContext = (Context)mStreamCtl;
    	
    	Common.Logging.i(TAG, "AirMediaCanvas constructor called....");

    	if (CresCanvas.useCanvasSurfaces) // needed only until new apk is added that supports canvas
    	{
    		launchCanvasService(); // will do its job asynchronously
    		Common.Logging.i(TAG, "AirMediaCanvas constructor completed");
    	}
    	else
    	{
    		isAirMediaCanvasUp = true;
    	}
    }
    
    public boolean IsAirMediaCanvasUp()
    {
    	return isAirMediaCanvasUp;
    }
    
    public boolean connectAndStartCanvasService()
    {
    	synchronized (connectLock) {
    		boolean successfulStart = true; //indicates that there was no time out condition

	    	Common.Logging.i(TAG, "connectAndStartCanvasService() isServiceConnected="+isServiceConnected+"  isCanvasAppStarted="+isCanvasAppStarted);
    		if (!isServiceConnected)
    		{
    			startupCompleteLatch = new CountDownLatch(1);

    			if (!connect2service()) {
    				if (DEBUG) {
    					Common.Logging.e(TAG, "Service failed to connect, restarting AirMedia app");
    					restartAirMediaCanvasApp();
    				}
    				return false;
    			}		

    			try { successfulStart = startupCompleteLatch.await(50000, TimeUnit.MILLISECONDS); }
    			catch (InterruptedException ex) { ex.printStackTrace(); }
    			
    			if (!successfulStart)
    			{
    				Common.Logging.w(TAG, "Timeout trying to start CanvasApp");
    			}
    			
    			if (!successfulStart && isServiceConnected)
    			{
    				Common.Logging.w(TAG, "service connected but unsuccessful start - doUnbindService");
    				doUnbindService();
    			}
    		}
    		else
    			Common.Logging.i(TAG, "Bypassing start because already started");
    		return successfulStart;
    	}    	
    }

    private void restartAirMediaCanvasApp()
    {
//		mStreamCtl.RestartAirMediaCanvasApp();
    }
    
    private boolean connect2service()
    {
    	requestServiceConnection = true;
    	serviceConnectedLatch = new CountDownLatch(1);
		// start service and instantiate receiver class
		doBindService();
		boolean successfulStart = true; //indicates that there was no time out condition
		try { successfulStart = serviceConnectedLatch.await(20000, TimeUnit.MILLISECONDS); }
		catch (InterruptedException ex) { ex.printStackTrace(); }
		
		startupCompleteLatch.countDown(); // TODO signal completion of startup - if no app needs to be started
		return successfulStart;
    }

    private void sleep(int msec)
    {
    	try        
    	{
    		Thread.sleep(msec);
    	} 
    	catch(InterruptedException ex) 
    	{
    		Thread.currentThread().interrupt();
    	}
    }
    
	public IAirMediaCanvas service()
	{
		return service_;
	}
    
	private void doBindService() {
        Common.Logging.i(TAG, "doBindService");
        Intent serviceIntent = new Intent(AirMediaCanvas.AIRMEDIA_CANVAS_BIND);
        serviceIntent.setPackage(AirMediaCanvas.AIRMEDIA_CANVAS_PACKAGE);
		List<ResolveInfo> list = mContext.getPackageManager().queryIntentServices(serviceIntent, 0);
		if (list == null || list.isEmpty()) {
        	Common.Logging.e(TAG, "doBindService  service does not exist  package= " + AirMediaCanvas.AIRMEDIA_CANVAS_PACKAGE + "  intent= " + AirMediaCanvas.AIRMEDIA_CANVAS_BIND + "------");
		} else {
			if (!mContext.bindService(serviceIntent, AirMediaCanvasServiceConnection, Context.BIND_AUTO_CREATE)) {
		        Common.Logging.e(TAG, "failed to bind to " + AirMediaCanvas.AIRMEDIA_CANVAS_PACKAGE);
			}
		}
		Common.Logging.i(TAG, "doBindService - completed bind BIND=" + AirMediaCanvas.AIRMEDIA_CANVAS_BIND + "   PACKAGE=" + AirMediaCanvas.AIRMEDIA_CANVAS_PACKAGE);
	}

	private void doUnbindService() {
        Common.Logging.i(TAG, "doUnbindService");
		try {
            if (service() != null)
            {
            	boolean successfulStart = false;
            	serviceDisconnectedLatch = new CountDownLatch(1);
            	mContext.unbindService(AirMediaCanvasServiceConnection);
    			try { successfulStart = serviceDisconnectedLatch.await(5000, TimeUnit.MILLISECONDS); }
    			catch (InterruptedException ex) { ex.printStackTrace(); }
    			service_ = null;
            	if (!successfulStart)
            	{
            		Common.Logging.w(TAG, "doUnbindService - did not onServiceDisconnect - timeout");
            		isServiceConnected = false;
            	}
            }
		} catch (Exception e) {
            Common.Logging.e(TAG, "doUnbindService  EXCEPTION  " + e);
		}
	}

	private final ServiceConnection AirMediaCanvasServiceConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(final ComponentName name, final IBinder binder) {
//			This comes in on UI thread, move off
			new Thread(new Runnable() {
				@Override
				public void run() {
					isServiceConnected = true;
					if (requestServiceConnection)
					{
						Common.Logging.i(TAG, "AirMediaCanvasServiceConnection.onServiceConnected  " + name);
						requestServiceConnection = false;
						try {
							Common.Logging.i(TAG, "AirMediaCanvasServiceConnection.onServiceConnected getting IAirMediaCanvas");
							service_ = IAirMediaCanvas.Stub.asInterface(binder);
							if (service_ == null) {
								Common.Logging.e(TAG, "AirMediaCanvasServiceConnection.onServiceConnected getting IAirMediaCanvas.Stub.asInterface failed");
								return;            	
							}
							serviceConnectedLatch.countDown();
							service().setSourceManager(mStreamCtl.mCanvas.getCanvasSourceManager().service());
							isAirMediaCanvasUp = true;
							Common.Logging.i(TAG, "AirMediaCanvasServiceConnection.onServiceConnected  calling canvasHasStarted()" + name);
							mStreamCtl.mCanvas.canvasHasStarted();
						} catch (Exception e) {
							Common.Logging.e(TAG, "AirMediaCanvasServiceConnection.onServiceConnected  EXCEPTION  " + e);
							e.printStackTrace();
						}
					}
					else
					{
						Common.Logging.i(TAG, "AirMediaCanvasServiceConnection.onServiceConnected, ignoring unsolicited" + name);
					}
				}
			}).start();
		}

		@Override
		public void onServiceDisconnected(final ComponentName name) {
//			This comes in on UI thread, move off
			new Thread(new Runnable() {
				@Override
				public void run() {
					Common.Logging.i(TAG, "AirMediaCanvasServiceConnection.onServiceDisconnected  " + name);
					isServiceConnected = false;
					if (serviceDisconnectedLatch != null)
					{
						serviceDisconnectedLatch.countDown();
					}
					isAirMediaCanvasUp = false;
					try {
						if (quittingAirMediaCanvasService == false)
						{
						}
						else
							Common.Logging.e(TAG, "AirMediaCanvasServiceConnection.onServiceDisconnected ignored because quitting");
					} catch (Exception e) {
						Common.Logging.e(TAG, "AirMediaCanvasServiceConnection.onServiceDisconnected  EXCEPTION  " + e);
						e.printStackTrace();
					}
				}
			}).start();
		}
	};
}
