package com.crestron.txrxservice;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.Collection;
import java.util.List;
import java.util.EnumSet;
import java.net.InetAddress;
import java.net.UnknownHostException;

import com.crestron.txrxservice.CresStreamCtrl.AirMediaLoginMode;

import com.crestron.airmedia.receiver.m360.IAirMediaReceiver;
import com.crestron.airmedia.receiver.m360.models.AirMediaReceiver;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSessionScreenPosition;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSessionScreenPositionLayout;
import com.crestron.airmedia.receiver.m360.models.AirMediaSession;
import com.crestron.airmedia.receiver.m360.models.AirMediaSessionManager;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaReceiverLoadedState;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaReceiverMirroringAssist;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaReceiverResolutionMode;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaReceiverState;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSessionConnectionState;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSessionScreenPosition;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSessionScreenPositionLayout;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSessionStreamingState;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSize;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSessionInfo;

import com.crestron.airmedia.utilities.ViewBase;
import com.crestron.airmedia.utilities.ViewBase.Size;
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
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.TextureView;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.graphics.Matrix; 
import android.graphics.Canvas;
import android.graphics.Color;

//public class AirMediaSplashtop 
public class AirMediaSplashtop implements AirMedia
{
    CresStreamCtrl mStreamCtl;
    Context mContext;
    String TAG = "TxRx Splashtop AirMedia"; 
	private boolean surfaceDisplayed = false;
	private static final boolean DEBUG = true;
	private static final int streamIdx = 0;
	private static final int MAX_USERS = 32;
	private static final int CODEC_ERROR = 1;
	private final Object stopSessionObjectLock = new Object();
    public CountDownLatch serviceConnectedLatch=null;
    public CountDownLatch receiverLoadedLatch=null;
    public CountDownLatch receiverStoppedLatch=null;
    public CountDownLatch receiverStartedLatch=null;
    public CountDownLatch startupCompleteLatch=null;
    public CountDownLatch deviceDisconnectedLatch=null;
    public AirMediaSession sessionRequestingStop=null;
    
    private final Object connectLock = new Object();
    private final Object startStopReceiverLock = new Object();

	private AirMediaReceiver receiver_;
	private IAirMediaReceiver service_=null;
	private AirMediaSessionManager manager_;
	
	private AirMediaSession active_session_=null;
	private Surface surface_=null;
	private Rect window_= new Rect();
	private String adapter_ip_address = null;
	private String version = null;
	private String productName = null; 
	private int lastReturnedAirMediaStatus;
	private boolean pending_adapter_ip_address_change = false;
	private boolean isServiceConnected = false;
	private boolean isReceiverStarted = false;

    private Handler handler_;
    private Map<Integer, AirMediaSession> userSessionMap = new ConcurrentHashMap<Integer, AirMediaSession>();

 
    public AirMediaSplashtop(CresStreamCtrl streamCtl) 
    {
    	mStreamCtl = streamCtl;
    	mContext = (Context)mStreamCtl;
    	
    	mStreamCtl.setHostName("");
    	mStreamCtl.setDomainName("");
    	Log.d(TAG, "HostName="+mStreamCtl.hostName+"   DomainName="+mStreamCtl.domainName);
    	mStreamCtl.sendAirMediaConnectionAddress(streamIdx);  
    	
//    	shutDownAirMediaSplashtop();	// In case AirMediaSplashtop was already running shut it down
    	boolean serviceSuccessfullyStarted = false;
    	while (!serviceSuccessfullyStarted)
    	{
    		serviceSuccessfullyStarted = connectAndStartReceiverService();
    		if (serviceSuccessfullyStarted) {
    			Log.i(TAG, "AirMediaSpashtop constructor exiting receiver=" + receiver() + "  service=" + service() + "  manager=" + manager());
    		} else {
    			Log.e(TAG, "Airmedia startup failed, trying again in 5 seconds");
    			sleep(5000);
    		}
    	}
    }
    
    public boolean connectAndStartReceiverService()
    {
    	synchronized (connectLock) {
    		boolean successfulStart = true; //indicates that there was no time out condition

    		if (!isServiceConnected)
    		{
    			startupCompleteLatch = new CountDownLatch(1);


    			if (!connect2service()) {
    				Log.e(TAG, "Service failed to connect, restarting AirMedia app");
    				restartAirMedia();
    				return false;
    			}		

    			try { successfulStart = startupCompleteLatch.await(50000, TimeUnit.MILLISECONDS); }
    			catch (InterruptedException ex) { ex.printStackTrace(); }
    		}
    		else if (!isReceiverStarted)
    		{
    			Log.w(TAG, "Service connected but not started, restarting AirMedia app");
    			restartAirMedia();
    		}
    		else
    			Log.d(TAG, "Bypassing start because already started");
    		return successfulStart;
    	}    	
    }
    
    private void restartAirMedia()
    {
		mStreamCtl.RestartAirMedia();
    }
    
    private class RestartAirMedia implements Runnable {
    	public void run() {
    		receiver_.stop();
            receiver_.close();
            receiver_ = null;
            removeAllSessionsFromMap();
            mStreamCtl.sendAirMediaNumberUserConnected();
            active_session_ = null;
            surfaceDisplayed = false;
    		connectAndStartReceiverService();
    	}
    }
    
    private void startAirMedia()
    { 
		Log.d(TAG, "startAirMedia: Device IP="+mStreamCtl.userSettings.getDeviceIp()+"   Aux IP="+mStreamCtl.userSettings.getAuxiliaryIp());

		adapter_ip_address = mStreamCtl.getAirMediaConnectionIpAddress(0);
		version = mStreamCtl.getAirMediaVersion(streamIdx);
		Log.d(TAG, "Receiver apk version: " + version);
		productName = mStreamCtl.mProductName + " " + version;
		Log.d(TAG, "Product Name: " + productName);

		// Now start receiver
        if (!startAirMediaReceiver(mStreamCtl.hostName)) {
			Log.e(TAG, "Receiver failed to load, restarting AirMedia app");
			restartAirMedia();
        }

		manager_ = receiver().sessionManager();
		if (manager_ != null)
		{
			registerSessionManagerEventHandlers(manager_);
		}
		intializeDisplay();
		
		set4in1ScreenEnable(false); // TODO: Remove this when quad view is eventually enabled
    }
    
    private boolean connect2service()
    {
    	serviceConnectedLatch = new CountDownLatch(1);
		// start service and instantiate receiver class
		doBindService();
		boolean successfulStart = true; //indicates that there was no time out condition
		try { successfulStart = serviceConnectedLatch.await(20000, TimeUnit.MILLISECONDS); }
		catch (InterruptedException ex) { ex.printStackTrace(); }
		
		return successfulStart;
    }
    
    // synchronous version of receiver stop - waits for completion
    private boolean stopReceiver()
    {
    	synchronized (startStopReceiverLock) {
    		Log.i(TAG, "stopReceiver() enter (thread="+Thread.currentThread().getId()+")");
    		boolean successfulStop = true;
    		receiverStoppedLatch = new CountDownLatch(1);
    		receiver_.stop();
    		try { successfulStop = receiverStoppedLatch.await(30000, TimeUnit.MILLISECONDS); }
    		catch (InterruptedException ex) { ex.printStackTrace(); }
    		Log.i(TAG, "stopReceiver() exit (thread="+Thread.currentThread().getId()+")");
    		return successfulStop;
    	}
    }
    
    // synchronous version of receiver start - waits for completion
    // this function does not handle ip adapter changes that may have occurred - for that use
    // the version that handles it: startReceiverWithPossibleIpAddressChange()
    private boolean startReceiver()
    {
    	synchronized (startStopReceiverLock) {
    		Log.i(TAG, "startReceiver() enter (thread="+Thread.currentThread().getId()+")");
    		boolean successfulStart = true;
    		receiverStartedLatch = new CountDownLatch(1);
    		receiver().start();
    		try { successfulStart = receiverStartedLatch.await(45000, TimeUnit.MILLISECONDS); }
    		catch (InterruptedException ex) { ex.printStackTrace(); }
    		Log.i(TAG, "startReceiver() exit (thread="+Thread.currentThread().getId()+")");
    		return successfulStart;
    	}
    }

	private void startReceiverWithPossibleIpAddressChange()
	{
		synchronized (startStopReceiverLock) {
			// If there is an IP address change apply it first and 
			if (pending_adapter_ip_address_change && !adapter_ip_address.equals("None")) {
				Log.i(TAG, "startReceiverWithPossiblyIpAddressChange(): Setting new ip address for receiver: " + adapter_ip_address);
				receiver_.adapterAddress(adapter_ip_address);
				startReceiver();
			}
			pending_adapter_ip_address_change = false;
		}		
	}
	
    private class RestartReceiver implements Runnable {
    	public void run() {
    		Log.i(TAG, "RestartReceiver(): Entered");
    		if (receiver_.state() != AirMediaReceiverState.Stopped)
    		{
            	Log.i(TAG, "RestartReceiver(): Stopping receiver");
    			stopReceiver();			
    		}
    		startReceiverWithPossibleIpAddressChange();
    		Log.i(TAG, "RestartReceiver(): Exited");
    	}
    }
    
    private boolean startAirMediaReceiver(final String serverName)
    {
		boolean successfulStart = true; //indicates receiver was successfully loaded

        if (receiver_ == null) {
        	try {
        		receiver_ = new AirMediaReceiver(service_);

                receiver().serverName(serverName);
                receiver().product(productName);
                receiver().adapterAddress(adapter_ip_address);
                receiver().maxResolution(AirMediaReceiverResolutionMode.Max1080P);
                Point dSize = mStreamCtl.getDisplaySize();
                receiver().displayResolution(new AirMediaSize(dSize.x, dSize.y));

        		registerReceiverEventHandlers(receiver());

        		if (receiver().loaded() != AirMediaReceiverLoadedState.Loaded)
        		{
        			Log.d(TAG,"startAirMediaReceiver: loading receiver");
        			receiverLoadedLatch = new CountDownLatch(1);
        			Log.i(TAG,"Calling receiver.initalize()");
        			receiver().initialize();        		
        			try { successfulStart = receiverLoadedLatch.await(30000, TimeUnit.MILLISECONDS); }
        			catch (InterruptedException ex) { ex.printStackTrace(); }
        			Log.d(TAG,"startAirMediaReceiver: receiverLoading success="+successfulStart);
        			Log.i(TAG,"receiver is in " + receiver().loaded() + " state");
        		} else {
        			Log.i(TAG,"Not calling receiver.initalize() because receiver is already loaded");
        		}
        	} 
        	catch (Exception ex)
        	{
        		Log.e(TAG, "Exception trying to create receiver");
        	}
        	finally
        	{
        		Log.i(TAG, "Receiver state = " + receiver().loaded());
        	}
        }
        if (successfulStart)
        {
        	if (receiver().state() != AirMediaReceiverState.Stopped) {
        		Log.i(TAG,"startAirMediaReceiver: Receiver found to be in Started state - calling stop to 'restart' it");
        		receiver().sessionManager().clear();
        		stopReceiver();
        	}
        	Log.i(TAG, "startAirMediaReceiver(): Starting AirMedia Receiver");
        	successfulStart = startReceiver();
        }
        startupCompleteLatch.countDown();
		return successfulStart;
    }
    
    public void showVideo(boolean use_texture)
    {
		surfaceDisplayed = true;
    	Log.d(TAG, "showVideo: Splashtop Window showing " + ((use_texture) ? "TextureView" : "SurfaceView") + "    surfaceDisplayed=" + getSurfaceDisplayed());
    	mStreamCtl.showSplashtopWindow(streamIdx, use_texture);
    }
    
    public void hideVideo(boolean use_texture)
    {
		surfaceDisplayed = false;
    	Log.d(TAG, "hideVideo: Splashtop Window hidden " + ((use_texture) ? "TextureView" : "SurfaceView") + "    surfaceDisplayed=" + getSurfaceDisplayed());
    	mStreamCtl.hideSplashtopWindow(streamIdx, use_texture);
    }
    
    
    public void onDestroy(){
    	Log.i(TAG, "AirMediaSplashtop Class destroyed!!!");

    	surfaceDisplayed = false;
    	Log.d(TAG, "surfaceDisplayed=" + getSurfaceDisplayed());

    	shutDownAirMediaSplashtop();
    }

    public void shutDownAirMediaSplashtop(){
    	doUnbindService();
    }
    
    public void recover(){
    	for (int sessId=0; sessId < CresStreamCtrl.NumOfSurfaces; sessId++)
    	{
    		if (mStreamCtl.userSettings.getAirMediaLaunch(sessId))
    		{
    			hide(true);	// Need to stop sender in order to recover

    			try { Thread.sleep(5000); } catch (InterruptedException e) {};	

    			int width = mStreamCtl.userSettings.getAirMediaWidth();
    			int height = mStreamCtl.userSettings.getAirMediaHeight();

    			if ((width == 0) && (height == 0))
    			{
    				Point size = mStreamCtl.getDisplaySize();

    				width = size.x;
    				height = size.y;
    			}
    			show(mStreamCtl.userSettings.getAirMediaX(), mStreamCtl.userSettings.getAirMediaY(),width,height);
    		}
    	}
    }    
    
    public void show(int x, int y, int width, int height)
    {
    	Rect window = new Rect(x, y, x+width-1, y+height-1);
    	if (surfaceDisplayed == false || !MiscUtils.rectanglesAreEqual(window_, window))
    	{	    		    	
	    	Log.i(TAG, "show: Show window 0 " + window);
	
	    	//show surface
	    	setVideoTransformation();
	    	
	    	showVideo(useTextureView(session()));
	    	
	    	if (session() != null && session().videoSurface() == null)
	    	{
		    	Log.d(TAG, "show: calling attachSurface");
	    		attachSurface();	
	    	}
    	}
    	else
    		Log.i(TAG, "show: AirMedia already shown, ignoring request");
    }    
    
    public void hide(boolean sendStopToSender, boolean clear)
    {
    	if (surfaceDisplayed == true)
    	{
			// Invalidate rect on hide
    		window_ = new Rect();    		
    	
    		detachSurface();
    		
    		if (clear && Boolean.parseBoolean(mStreamCtl.hdmiOutput.getSyncStatus()))
    		{
    			clearSurface();			
    		}
    		
    		if (session() != null)
    			hideVideo(useTextureView(session()));
    		    		    		
    		if (sendStopToSender) {   			
    			stopAllSenders(); // Inform senders that stream is stopped/hidden
    		}
    	}
    	else
    		Log.i(TAG, "hide: AirMedia already hidden, ignoring request");
    }
    
    public void hide(boolean sendTopToSender)
    {
    	hide(sendTopToSender, true);
    }
    
    private int session2user(/*@NonNull */AirMediaSession session)
    {
    	int senderId = -1;
    	for (Map.Entry<Integer, AirMediaSession> e : userSessionMap.entrySet()) 
    	{
    		if ((AirMediaSession)e.getValue() == session)
    		{
    			senderId = e.getKey();
    			break;
    		}
    	}
    	
    	return senderId;
    }
    
    private AirMediaSession user2session(int senderId)
    {
    	AirMediaSession session = null;
    	for (Map.Entry<Integer, AirMediaSession> e : userSessionMap.entrySet()) 
    	{
    		if ((int)e.getKey() == senderId)
    		{
    			session = e.getValue();
    			break;
    		}
    	}
    	
    	return session;
    }
    
    private int addSessionToMap(AirMediaSession session)
    {
    	int senderId = -1;
    	
    	// Check if already added first
    	senderId = session2user(session);
    	if (senderId != -1)
    		return senderId;
    	
    	// Add to map
    	for (int i = 1; i <= MAX_USERS; ++i)
    	{
    		if (!userSessionMap.containsKey(i))
    		{
    			userSessionMap.put(i, session); 
    			senderId = i;
    			break;
    		}
    	}    	
    	
    	if (senderId == -1)
    	{
    		Log.w(TAG, "Max number of AirMedia senders reached!");
    	}
    	
    	return senderId;
    }
    
    private void removeSessionFromMap(AirMediaSession session)
    {
    	int senderId = -1;
    	for (Map.Entry<Integer, AirMediaSession> e : userSessionMap.entrySet()) 
    	{    	  
    	    if ((AirMediaSession)e.getValue() == session)
    	    {    	    	
    	    	senderId = e.getKey();
    	    	break;
    	    }
    	}
    	
    	if (senderId != -1)
    		userSessionMap.remove(senderId);
    }
    
    public void removeAllSessionsFromMap()
    {
		Log.i(TAG, "removeAllSessions");
    	for (int user = 1; user <= MAX_USERS; user++) // We handle airMedia user ID as 1 based
    	{
    		AirMediaSession session = user2session(user);
    		if (session != null) {
    			Log.i(TAG, "removing session for user "+user+" from map");
    			userSessionMap.remove(user);
                mStreamCtl.userSettings.setAirMediaUserConnected(false, user);
                mStreamCtl.sendAirMediaUserFeedbacks(user, "", "", 0, false);
    		}
    	}
    }
    
    public boolean getSurfaceDisplayed()
    {
    	return surfaceDisplayed;
    }

    public boolean useTextureView(AirMediaSession session)
    {
    	if (session() == null)
    	{
    		Log.w(TAG, "useTextureView: called with no active session");
    		return false;
    	}
    	return (session.info().isRotationSupported && !session.info().isRotationManagedBySender) ? true : false;
    }
    
    private static int normalizeRotation(int rotation) {
        if (rotation > 360) rotation = rotation % 360;
        else if (rotation < 0) rotation = (rotation % 360) + 360;
        if (rotation >= 315 || rotation <= 45) return 0;
        if (rotation >= 135 && rotation <= 225) return 180;
        if (rotation > 45 && rotation < 135) return 90;
        return 270;
    }

    private void setVideoTransformation()
    {
    	if (session() != null)
    	{
    		boolean use_texture_view = useTextureView(session());
    		if (use_texture_view)
    		{
    			mStreamCtl.setVideoTransformation(0, use_texture_view);
    		}
    		else
    		{
        		int width = mStreamCtl.userSettings.getAirMediaWidth();
    			int height = mStreamCtl.userSettings.getAirMediaHeight();
    			
    			if ((width == 0) && (height == 0))
    			{
    				Point size = mStreamCtl.getDisplaySize();

    				width = size.x;
    				height = size.y;
    			}
    			Rect r = MiscUtils.getAspectRatioPreservingRectangle(
    					mStreamCtl.userSettings.getAirMediaX(), mStreamCtl.userSettings.getAirMediaY(), width, height,
    					session().videoResolution().width, session().videoResolution().height);
    			setSurfaceSize(r.left, r.top, r.width(), r.height());
    		}
    	}
    }

    public void setVideoTransformation(int x, int y, int w, int h) {
    	
    	AirMediaSession session = session();
    	TextureView textureView = null;
    	
    	if (session == null)
    		return;
    			
    	if (!useTextureView(session))
    		return;
    	
        final float viewWidth = (float)w;
        final float viewHeight = (float)h;

        final float videoWidth = (float)session.videoResolution().width;
        final float videoHeight = (float)session.videoResolution().height;

        int videoRotation = normalizeRotation(session.videoRotation());

        final boolean isLandscape = (videoRotation == 90 || videoRotation == 270);

        final float scale = isLandscape
                ? Math.min(viewWidth / videoHeight, viewHeight / videoWidth)
                : Math.min(viewWidth / videoWidth, viewHeight / videoHeight);
        final float scaleX = isLandscape
                ? (videoHeight * scale) / viewHeight
                : (videoWidth * scale) / viewWidth;
        final float scaleY = isLandscape
                ? (videoWidth * scale) / viewWidth
                : (videoHeight * scale) / viewHeight;
        Log.v(TAG, "setTransformation isLandscape="+isLandscape+"    scale"+scale+"  scaleX="+scaleX+"    scaleY="+scaleY );

        final float viewCenterX = viewWidth / 2.0f;
        final float viewCenterY = viewHeight / 2.0f;

        Log.d(TAG, "setTransformation  " + AirMediaSession.toDebugString(session()) + "  view=" + w + "x" + h + " @(" + x + "," + y + ")  video=" + session.videoResolution() + "  rotate=" + videoRotation + "°" + "  scale=" + scaleX + " , " + scaleY);

    	textureView = mStreamCtl.getAirMediaTextureView(streamIdx);
        Matrix matrix = textureView.getTransform(null);
        matrix.setRotate(videoRotation, viewCenterX, viewCenterY);
        matrix.postScale(scaleX, scaleY, viewCenterX, viewCenterY);
        textureView.setTransform(matrix);
        mStreamCtl.dispSurface.forceParentLayoutInvalidation();
    }

    private void attachSurface() {
		Log.d(TAG, "Entered attachSurface");
    	SurfaceHolder surfaceHolder=null;
    	Surface surface;
    	SurfaceTexture surfaceTexture = null;
    	AirMediaSession session = session();
    	if (session == null) {
    		Log.d(TAG, "Returning without attaching since no active session");
    		return;
    	}
    	AirMediaReceiver receiver = receiver();
    	if (receiver == null) {
    		Log.d(TAG, "Returning without attaching since receiver is null");
    		return;
    	}
    	if (!getSurfaceDisplayed()) {
    		Log.d(TAG, "Returning without attaching since getSurfaceDisplayed is false");
    		return;
    	}
    	if (session.videoSurface() != null)
    	{
			Log.d(TAG, "attachSurface: detach prior surface");
    		session.detach();
    	}
    	if (!useTextureView(session))
    	{
    		surfaceHolder = mStreamCtl.getCresSurfaceHolder(streamIdx);
    		surface = surfaceHolder.getSurface();
    		//surfaceHolder_.addCallback(video_surface_callback_handler_);
    	} else {
    		surfaceTexture = mStreamCtl.getAirMediaSurfaceTexture(streamIdx);
    		surface = new Surface(surfaceTexture);
    	}
		if (surface.isValid())
		{
			if (session.videoSurface() != surface) {
		    	surface_ = surface;
				Log.d(TAG, "attachSurface: attaching surface: " + surface + " to session: " + AirMediaSession.toDebugString(session));
				session.attach(surface);
			}
		} else {
			if (surfaceHolder != null)
				surfaceHolder.addCallback(video_surface_callback_handler_);
			Log.d(TAG, "attachSurface: No valid surface at this time");
		}

		setVideoTransformation();

		mStreamCtl.dispSurface.stMGR.setInvalidateOnSurfaceTextureUpdate(true);
		Log.d(TAG, "Exit from attachSurface");
    }
    
    // TODO move into CresDisplaySurface
    private void clearSurface()
    {
    	Canvas canvas = null;
    	Surface s = null;
    	SurfaceTexture surfaceTexture = null;

    	Log.i(TAG, "clearSurface(): Fetching surfacetexture");
    	surfaceTexture = mStreamCtl.getAirMediaSurfaceTexture(streamIdx);
    	s = new Surface(surfaceTexture);
    	if (s == null) {
    		Log.d(TAG, "null surface obtained from SurfaceTexture - nothing to clear");
    		return;
    	}
    	Log.i(TAG, "clearSurface: clearing surface: " + s);
    	TextureView textureView = mStreamCtl.dispSurface.GetAirMediaTextureView(streamIdx);
    	Rect rect = new Rect(0, 0, textureView.getWidth(), textureView.getHeight());    	
    	try {
    		canvas = s.lockCanvas(rect);
    	} catch (android.view.Surface.OutOfResourcesException ex) { ex.printStackTrace(); }
    	if (canvas!=null)
    	{
    		canvas.drawColor(Color.BLACK);
    		s.unlockCanvasAndPost(canvas);
    	} else {
    		Log.i(TAG, "clearSurface: canvas is null");
    	}
    	s.release();
   	 	Log.i(TAG, "clearSurface: released surface: " + s);
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
    
    private void detachSurface() {
    	 AirMediaSession session = session();
    	 if (session == null) return;
    	 AirMediaReceiver receiver = receiver();
    	 if (receiver == null) return;

    	 Log.i(TAG, "detachSurface: detaching surface from session: " + AirMediaSession.toDebugString(session));
    	 if (session.videoSurface() != null) {
    		 session.detach();
        	 // Must release surface after detaching
    		 if (useTextureView(session))
    		 {
    			 // release surface if we were using textureview
    	    	 Log.i(TAG, "detachSurface: releasing surface: " + surface_);
    	    	 if (surface_ != null)
    	    		 surface_.release();
    		 }
			 surface_ = null;
    	 }
    }
    
    // set an active session - give it the surface and show the video
    public void setActiveSession(AirMediaSession session)
    {
    	boolean wasShowing=false;
    	boolean priorSessionExists = (session() != null);

    	// if we already have an active session, then check if it is same
    	if (AirMediaSession.isEqual(session(), session))
    	{
        	Log.i(TAG, "setActiveSession: already have same session active: " + AirMediaSession.toDebugString(session()));
    		return;
    	}
    	
    	// take surface away from active session if one exists
    	if (priorSessionExists)
    	{
    		wasShowing = getSurfaceDisplayed();
        	Log.i(TAG, "setActiveSession: removing prior active session " + AirMediaSession.toDebugString(session()));
    		unsetActiveSession(session(), false);
    	}
    	
    	active_session_ = session;
		Log.d(TAG, "setActiveSession: setting active session " + AirMediaSession.toDebugString(session()));  
		
		if (wasShowing)
		{
			// update window dimensions - may be going to new surfaceView or TextureView
	    	setSurfaceSize();
	    	// show the new surfaceView or TextureView
			showVideo(useTextureView(session));
		}
		
		attachSurface();

 		// Send status of 1 to indicate video is playing
		querySenderList(false);
    } 
    
	// unset active session - take away its surface and hide the video from it
    public void unsetActiveSession(AirMediaSession session, boolean sendFeedbackForSession)
    {
    	Log.d(TAG, "unsetActiveSession: entering removing active session " + AirMediaSession.toDebugString(session()));

    	if (session == null)
    	{
        	Log.w(TAG, "requesting removal of null session");
        	return;
    	}
    	if (session() == null)
    	{
        	Log.d(TAG, "nothing to remove - there is no active session");
        	return;
    	}
    	if (!AirMediaSession.isEqual(session(), session))
    	{
        	Log.w(TAG, "exiting removing active session without doing anything - incoming session is not the same as the active session" + AirMediaSession.toDebugString(session()));
    		return;
    	}
    	
    	// detach surface from session
    	if (session().videoSurface() != null)
    		detachSurface();
    	
    	hideVideo(useTextureView(session()));
		// clear surfaceTexture if it was Textureview
		if (useTextureView(session()) && Boolean.parseBoolean(mStreamCtl.hdmiOutput.getSyncStatus()))
		{
			clearSurface();			
		}
    	
    	Log.d(TAG, "removing active session setting active to null" + AirMediaSession.toDebugString(session()));
    	active_session_ = null;

    	Log.d(TAG, "detached surface returning from unsetActiveSession" + AirMediaSession.toDebugString(session()));

    	if (sendFeedbackForSession) {
        	Log.d(TAG, "unsetActiveSessions - calling querySenderList ");
    		querySenderList(false);
    	}
    }
    
    public void sendSessionFeedback(AirMediaSession session)
    {
        int user = session2user(session);
        if (user >= 0)
        {
        	AirMediaSessionScreenPosition screenpos = session.videoScreenPosition();
        	int position = (screenpos == AirMediaSessionScreenPosition.None) ? 0 : 128;;
        	mStreamCtl.sendAirMediaUserFeedbacks(user, session.username(), ((List<String>)session.addresses()).get(0), position, 
        			(session.videoState() == AirMediaSessionStreamingState.Playing) ? true : false);
        }
    }

    public void querySenderList(boolean sendAllUserFeedback)
    {
    	int status = 0;  // 0 = no displayed video, 1 = at least 1 video presenting
    	boolean[] sentUserFeedback = null;
		//Log.i(TAG, "Entered querySenderList (sendAllUserFeedback=" + String.valueOf(sendAllUserFeedback) + ")");
	
    	if (sendAllUserFeedback)
    	{
	    	// Create list of all user slots and mark off which ones are not connected
	    	sentUserFeedback = new boolean[MAX_USERS+1];
	    	for (int i = 1; i <= MAX_USERS; i++) { sentUserFeedback[i] = false; } // initialize all to false
    	}
    	
    	for (int i=1; i <= MAX_USERS; i++) {
    		AirMediaSession session = user2session(i);
    		if (session != null) {
    			sendSessionFeedback(session);
    			if (session.videoState() == AirMediaSessionStreamingState.Playing)
    				status = 1;
	    		mStreamCtl.userSettings.setAirMediaUserConnected(true, i);
    			if (sentUserFeedback != null)
    				sentUserFeedback[i] = true;
    		}
    	}
		
    	Log.d(TAG, "querySenderList: status = " + String.valueOf(status));
		mStreamCtl.sendAirMediaStatus(status);
		lastReturnedAirMediaStatus = status;
		
		if (sentUserFeedback != null)
		{
			// Send defaults for all user connections that weren't found in query
			for(int i = 1; i <= MAX_USERS; i++)
			{
				if (sentUserFeedback[i] == false)
				{
					//idMap.remove(i);	// Remove from mapping if existing
					mStreamCtl.userSettings.setAirMediaUserConnected(false, i);
					mStreamCtl.sendAirMediaUserFeedbacks(i, "", "", 0, false);					
				}
			}
		}
		
		mStreamCtl.sendAirMediaNumberUserConnected();
    }
    
    public void intializeDisplay()
    {
    	Log.d(TAG, "InitializeDisplay() entered");
    	// Show/Hide login code depending on setting
    	if (mStreamCtl.userSettings.getAirMediaLoginMode() == AirMediaLoginMode.Disabled.ordinal())
    		setLoginCodeDisable();
    	else
    		setLoginCode(mStreamCtl.userSettings.getAirMediaLoginCode());
    	
        if (mStreamCtl.userSettings.getAirMediaDisplayLoginCode() && 
        		mStreamCtl.userSettings.getAirMediaLoginMode() != AirMediaLoginMode.Disabled.ordinal())
        {
        	showLoginCodePrompt(mStreamCtl.userSettings.getAirMediaLoginCode());
        }
        else
        {
        	hideLoginCodePrompt();
        }
        
        // Show/Hide IP address depending on setting
        setIpAddressPrompt(mStreamCtl.userSettings.getAirMediaIpAddressPrompt());

        // Set window display and flag (z order control)
        setDisplayScreen(mStreamCtl.userSettings.getAirMediaDisplayScreen());
        setWindowFlag(mStreamCtl.userSettings.getAirMediaWindowFlag());
    	Log.d(TAG, "InitializeDisplay() exit");
    }
    
    
    public void hideLoginCodePrompt()
    {
    	setLoginCodePrompt("");
    }
    
    public void showLoginCodePrompt(int loginCode)
    {
    }
    
    public void setLoginCode(int loginCode)
    {
    	Log.i(TAG, "Current login code = " + receiver().serverPassword());
    	String code = String.format("%04d", loginCode);
    	Log.i(TAG, "Set Login Code = " + code + " (" + String.valueOf(loginCode) + ")");
		if (loginCode < 0) {
			receiver().serverPassword("");
		} else {
			receiver().serverPassword(String.valueOf(code));	
		}
    }
    
    public void setLoginCodeDisable()
    {
		setLoginCode(-1);
    }
    
    public void setLoginCodePrompt(String loginCodePrompt)
    {
    }
    
    public void setAdapter(String address)
    {
    	if (receiver_ == null)
    		return;
    	if (adapter_ip_address.equals(address))
    		return;
    	adapter_ip_address = address;
    	pending_adapter_ip_address_change = true;
    	Log.i(TAG, "setAdapter(): Stopping all senders");
    	stopAllSenders();
    	if (receiver().state() != AirMediaReceiverState.Stopped) {
        	Log.i(TAG, "setAdapter(): Stopping receiver");
    		stopReceiver();
    	}
    	startReceiverWithPossibleIpAddressChange();
		Log.i(TAG, "setAdapter(): Exiting having set ip address to "+ adapter_ip_address);
    }
    
    public void setProjectionLock(boolean enable)
    {
    	Log.i(TAG, "setProjectionLock: " + enable);
    	if (receiver_ != null)
    		receiver_.projectionLocked(enable);
    }
    
    public void setModeratorEnable(boolean enable)
    {    	
		Log.e(TAG, "Implement ModeratorEnable");
    }
    
    public void set4in1ScreenEnable(boolean enable)
    {
    	if (enable)
    	{
    		Log.e(TAG, "Implement set4in1ScreenEnable");
    	}
    }
    
    public void debugCommand(String debugCommand)
    {
    	if (debugCommand == null || debugCommand.equalsIgnoreCase(""))
    	{
    		Log.d(TAG, "Usage: AIRMEDIA_DEBUG=showSessions");
    		Log.d(TAG, "       AIRMEDIA_DEBUG=showStatus");
    		return;
    	}
    	if (debugCommand.equalsIgnoreCase("showSessions"))
    	{
    		for (int i=1; i < MAX_USERS; i++) {
    			AirMediaSession s = user2session(i);
    			if (s != null) {
    				Log.d(TAG, "User: "+i+"  Session: "+s);
    	        	AirMediaSessionScreenPosition screenpos = s.videoScreenPosition();
    	        	int position = (screenpos == AirMediaSessionScreenPosition.None) ? 0 : 128;;
    		    	Log.i(TAG, String.format("   AIRMEDIA_USER_NAME=%d:%s", i, s.username()));
    		    	Log.i(TAG, String.format("   AIRMEDIA_USER_IP=%d:%s", i, ((List<String>)s.addresses()).get(0)));
    		    	Log.i(TAG, String.format("   AIRMEDIA_USER_POSITION=%d:%d", i, position));
    		    	Log.i(TAG, String.format("   AIRMEDIA_USER_CONNECTED=%d:%s", i, 
    		    			String.valueOf((s.videoState() == AirMediaSessionStreamingState.Playing) ? true : false)));
    				logSession(s);
    			}
    		}
    		return;
    	}
    	if (debugCommand.equalsIgnoreCase("showStatus"))
    	{
    		Log.d(TAG, "Last AIRMEDIA_STATUS="+lastReturnedAirMediaStatus);
    		return;
    	}
    }
    
    public void logSession(AirMediaSession session)
    {
		Log.d(TAG, "   endpoint: "+session.endpoint());
		Log.d(TAG, "   username: "+session.username());
		Log.d(TAG, "   connection: "+session.connection());
		Log.d(TAG, "   streaming: "+session.streaming());
		Log.d(TAG, "   channelState: "+session.channelState());
		Log.d(TAG, "   deviceState: "+session.deviceState());
		Log.d(TAG, "   device ID: "+session.deviceId());
		Log.d(TAG, "   videoState: "+session.videoState());
		Log.d(TAG, "   video ID: "+session.videoId());
		Log.d(TAG, "   videoResolution: "+session.videoResolution());
		Log.d(TAG, "   videoRotation: "+session.videoRotation());
		Log.d(TAG, "   videoIsDrm: "+session.videoIsDrm());
		Log.d(TAG, "   screenPosition: "+session.videoScreenPosition());
		Log.d(TAG, "   video Surface: "+session.videoSurface());
		Log.d(TAG, "   audioState: "+session.audioState());
		Log.d(TAG, "   audio ID: "+session.audioId());
		Log.d(TAG, "   audio Muted: "+session.audioMuted());
		Log.d(TAG, "   audio Volume: "+session.audioVolume());
	}
    
    public void addSession(AirMediaSession session)
    {
		Log.d(TAG, "addSession " + session);
        int user = addSessionToMap(session);
        Log.d(TAG, "User id: "+String.valueOf(user)+"  Connected: "+String.valueOf(session.videoState() == AirMediaSessionStreamingState.Playing));
		Log.d(TAG, "Adding Id to map, userId: " + user + " session: " + session);
		if ((user > 0) && (user <= MAX_USERS))
		{
			mStreamCtl.userSettings.setAirMediaUserConnected(true, user);
			Log.i(TAG, "addSession: sending feedback for user: " + String.valueOf(user));
			sendSessionFeedback(session);
			mStreamCtl.sendAirMediaNumberUserConnected();
		}
		else
			Log.w(TAG, "Received invalid user id of " + user);
    }
    
    public void removeSession(AirMediaSession session)
    {
		Log.d(TAG, "removeSession " + session);
        int user = session2user(session);
        if (user > 0) {
            Log.i(TAG, "User id: "+String.valueOf(user)+"  Disconnected: "+String.valueOf(false));
            mStreamCtl.userSettings.setAirMediaUserConnected(false, user);
            mStreamCtl.sendAirMediaUserFeedbacks(user, "", "", 0, false);
        } else {
            Log.e(TAG, "Got invalid user id: "+String.valueOf(user) + "for " + session);
        }
		mStreamCtl.sendAirMediaNumberUserConnected();
        removeSessionFromMap(session);
    }
    
    public void resetConnections()
    {    	
		Log.i(TAG, "resetConnections()");
		manager().clear();
    }
    
    public void disconnectUser(int userId)
    {
		Log.i(TAG, "disconnectUser" + String.valueOf(userId));
		AirMediaSession session = user2session(userId);
		if (session == null) {
			Log.e(TAG, "No session found for userId="+userId);
		} else {
			session.disconnect();			
		}
    }
    
    public void disconnectAllSenders()
    {
    	Log.i(TAG, "disconnectAllSenders");
    	for (int i = 1; i <= MAX_USERS; i++) // We handle airMedia user ID as 1 based
    	{
    		if (mStreamCtl.userSettings.getAirMediaUserConnected(i))
    		{
    			disconnectUser(i);
    		}
    	}
    }

    
    public void startUser(int userId)
    { 
		Log.i(TAG, "startUser: " + String.valueOf(userId));
		AirMediaSession session = user2session(userId);
		if (session == null) {
			Log.w(TAG, "Trying to start an unconnected session for user "+ String.valueOf(userId));
			return;
		}
		if (active_session_ != null && !AirMediaSession.isEqual(active_session_, session))
		{
			// stop prior active session
			stopUser(userId);
		} 
		if (AirMediaSession.isEqual(active_session_, session)) {
			// session is already started
			Log.e(TAG, "session already started for userId="+userId);
			return; 
		} else {
			session.play();
		}
		// TODO need feedback for this user
    }
    
    public void setUserPosition(int userId, int position)
    {    	
    	// Translate from Crestron enum to AirMedia enum
    	int quadrant = 0;
    	switch (position)
    	{
    	case 1: //Crestron: topLeft, AirMedia: topLeft
    	case 2: //Crestron: topRight, AirMedia: topRight
    	case 3: //Crestron: bottomLeft, AirMedia: bottomLeft
    	case 4: //Crestron: bottomRight, AirMedia: bottomRight
    		quadrant = position;
    		break;
    	case 5: //Crestron: fullscreen
    		quadrant = 128; //AirMedia: fullscreen
    		break;
		default:
    			break;
    	}
    }
    
    public void stopUser(int userId)
    { 
		Log.i(TAG, "stopUser: " + String.valueOf(userId));
		AirMediaSession session = user2session(userId);
		if (session == null) {
			Log.e(TAG, "Trying to stop an unconnected session for user " + String.valueOf(userId));
			return;
		}
		Log.i(TAG, "stopUser: " + String.valueOf(userId) + " Session=" + AirMediaSession.toDebugString(session) + "  deviceState=" + session.deviceState());
		if (active_session_ != null && !AirMediaSession.isEqual(active_session_, session))
		{
			if (session.videoState() == AirMediaSessionStreamingState.Playing)
			{
				Log.w(TAG, "Trying to stop a playing session which is not active for user " + String.valueOf(userId));
			}
		}
		// Only allow a single 'session' to enter at a time - need to make sure that we have a unique session
		// requesting a stop and that the callback verifies it is for that specific session before counting
		// down the latch
		synchronized(stopSessionObjectLock) {
			if (session.deviceState() != AirMediaSessionConnectionState.Disconnected) {
				// session is started
				stopSession(session);
			} else {
				Log.w(TAG, "Session: " + AirMediaSession.toDebugString(session) + "is already in device disconnected state");
			}
		}
		Log.v(TAG, "stopUser: " + String.valueOf(userId) + "exit");
    }
    
    private void stopSession(AirMediaSession session)
    {
		boolean stopStatus=true;
		long begin = System.currentTimeMillis();
		sessionRequestingStop = session;
		deviceDisconnectedLatch = new CountDownLatch(1);
		Log.d(TAG, "Session " + AirMediaSession.toDebugString(session) + " requesting stop ");
		session.stop(); 
		try { stopStatus = deviceDisconnectedLatch.await(10000, TimeUnit.MILLISECONDS); }
		catch (InterruptedException ex) { ex.printStackTrace(); }
		if (!stopStatus) {
			Log.w(TAG, "Unable to stop session " + session + "even after 15 seconds");
		} else {
			long end = System.currentTimeMillis();
			Log.d(TAG, "Session " + AirMediaSession.toDebugString(session) + "was successfully stoppped in "
					+ (end-begin) + " ms");
		}
		sessionRequestingStop=null;
    }
    
    public void stopAllUser()
    {
    }
    
    public void setOsdImage(String filePath)
    {
    }
    
    public void setIpAddressPrompt(boolean enable)
    {
    }
    
    public void setDomainNamePrompt(boolean enable)
    {
    }
    
    public void setSurfaceSize()
    {
    	int x = mStreamCtl.userSettings.getAirMediaX();
    	int y = mStreamCtl.userSettings.getAirMediaY();
		int width = mStreamCtl.userSettings.getAirMediaWidth();
		int height = mStreamCtl.userSettings.getAirMediaHeight();
		
		if ((width == 0) && (height == 0))
		{
			Point size = mStreamCtl.getDisplaySize();

			width = size.x;
			height = size.y;
		}
		setSurfaceSize(x, y, width, height);
    }
    
    public void setSurfaceSize(int x, int y, int width, int height, boolean launch)
    {
		setSurfaceSize(x, y, width, height);
    }

    public void setSurfaceSize(int x, int y, int width, int height)
    {		
		Log.d(TAG, "------------ In setSurfaceSize calling updateWindow (surfaceDisplayed="+getSurfaceDisplayed()+") --------------");
    	window_ = new Rect(x, y, x+width-1, y+height-1);
		mStreamCtl.setAirMediaWindowDimensions(x, y, width, height, streamIdx, useTextureView(session()));
		Log.d(TAG, "------------ finished updateWindow --------------");
    }
    
    public void setDisplayScreen(int displayId)
    {
    }
    
    public void setWindowFlag(int windowFlag)
    {
    }
    
    public void setStandbyScreen(int standbyScreen)
    {
    }
    
    private void stopAllSenders()
    {
		Log.i(TAG, "stopAllSenders");
    	for (int i = 1; i <= MAX_USERS; i++) // We handle airMedia user ID as 1 based
    	{
    		if (mStreamCtl.userSettings.getAirMediaUserConnected(i))
    		{
    			stopUser(i);
    		}
    	}
    }
    
	public AirMediaReceiver receiver()
	{
		return receiver_;
	}

	public IAirMediaReceiver service()
	{
		return service_;
	}
	
	public AirMediaSessionManager manager()
	{
		return manager_;
	}

	public AirMediaSession session()
	{
		return active_session_;
	}
	
    public static boolean checkAirMediaLicense()
    {
    	boolean licensed = false;
    	try
    	{
    		licensed = Integer.parseInt(MiscUtils.readStringFromDisk(licenseFilePath)) == 1;
    	} catch (NumberFormatException e) {} // If file DNE or corrupt not licensed
    	return licensed;
    }
    
	private void doBindService() {
        Log.d(TAG, "doBindService");
        Intent serviceIntent = new Intent(AirMediaReceiver.AIRMEDIA_SERVICE_BIND);
        serviceIntent.setPackage(AirMediaReceiver.AIRMEDIA_SERVICE_PACKAGE);
		List<ResolveInfo> list = mContext.getPackageManager().queryIntentServices(serviceIntent, 0);
		if (list == null || list.isEmpty()) {
        	Log.e(TAG, "doBindService  service does not exist  package= " + AirMediaReceiver.AIRMEDIA_SERVICE_PACKAGE + "  intent= " + AirMediaReceiver.AIRMEDIA_SERVICE_BIND + "------");
		} else {
			if (!mContext.bindService(serviceIntent, AirMediaServiceConnection, Context.BIND_AUTO_CREATE)) {
		        Log.e(TAG, "failed to bind to " + AirMediaReceiver.AIRMEDIA_SERVICE_PACKAGE);
			}
		}
		Log.d(TAG, "doBindService - completed bind BIND=" + AirMediaReceiver.AIRMEDIA_SERVICE_BIND + "   PACKAGE=" + AirMediaReceiver.AIRMEDIA_SERVICE_PACKAGE);
	}

	private void doUnbindService() {
        Log.d(TAG, "doUnbindService");
		try {
            close();
            if (service_ != null)
            {
            	mContext.unbindService(AirMediaServiceConnection);
            	service_ = null;
            }
		} catch (Exception e) {
            Log.e(TAG, "doUnbindService  EXCEPTION  " + e);
		}
	}

    public void close() {
        AirMediaReceiver receiver = receiver_;
		receiver_ = null;
        if (receiver != null) {
            try {
            	receiver.stop();
                receiver.close();
            } catch (Exception e) {
                Log.e(TAG, "close  EXCEPTION  " + e);
            }
        }
    }

	private final ServiceConnection AirMediaServiceConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName name, IBinder binder) {
            Log.d(TAG, "AirMediaServiceConnection.onServiceConnected  " + name);
            isServiceConnected = true;
			try {
                service_ = IAirMediaReceiver.Stub.asInterface(binder);
                if (service_ == null) return;
            	serviceConnectedLatch.countDown();
            	startAirMedia();
			} catch (Exception e) {
                Log.e(TAG, "AirMediaServiceConnection.onServiceConnected  EXCEPTION  " + e);
			}
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "AirMediaServiceConnection.onServiceDisconnected  " + name);
            isServiceConnected = false;
            try {
            	// remove active session so AirMedia screen pops up until fresh connection made to service
            	if (session() != null)
            	{
            		removeSession(session());
            		active_session_ = null;
            	}
            	removeAllSessionsFromMap();
        		querySenderList(false);
        		mStreamCtl.sendAirMediaStatus(0);
                RestartAirMedia restarter = new RestartAirMedia();
                Thread t = new Thread(restarter);
                t.start();
            } catch (Exception e) {
                Log.e(TAG, "AirMediaServiceConnection.onServiceDisconnected  EXCEPTION  " + e);
            }
		}
	};
	
	//  Receiver events
	private void registerReceiverEventHandlers(AirMediaReceiver receiver) {
        if (receiver == null) return;
        receiver.loadedChanged().register(loadedChangedHandler_);
        receiver.stateChanged().register(stateChangedHandler_);
	}
	
	private void unregisterReceiverEventHandlers(AirMediaReceiver receiver) {
        if (receiver == null) return;
        receiver.loadedChanged().unregister(loadedChangedHandler_);
        receiver.stateChanged().unregister(stateChangedHandler_);
	}
	
	//  SessionManager Events
    private void registerSessionManagerEventHandlers(AirMediaSessionManager manager) {
        if (manager == null) return;
        manager.layoutChanged().register(layoutChangedHandler_);
        manager.occupiedChanged().register(occupiedChangedHandler_);
        manager.added().register(addedHandler_);
        manager.removed().register(removedHandler_);
    }

    private void unregisterSessionManagerEventHandlers(AirMediaSessionManager manager) {
        if (manager == null) return;
        manager.layoutChanged().unregister(layoutChangedHandler_);
        manager.occupiedChanged().unregister(occupiedChangedHandler_);
        manager.added().unregister(addedHandler_);
        manager.removed().unregister(removedHandler_);
    }
	
    private final MulticastChangedDelegate.Observer<AirMediaReceiver, AirMediaReceiverLoadedState> loadedChangedHandler_ = new MulticastChangedDelegate.Observer<AirMediaReceiver, AirMediaReceiverLoadedState>() {
        @Override
        public void onEvent(AirMediaReceiver receiver, AirMediaReceiverLoadedState from, AirMediaReceiverLoadedState to) {
            Log.v(TAG, "view.receiver.event.loaded  " + from + "  ==>  " + to);
            if (to == AirMediaReceiverLoadedState.Loaded)
            {
            	receiverLoadedLatch.countDown();
            }
        }
    };

    private final MulticastChangedWithReasonDelegate.Observer<AirMediaReceiver, AirMediaReceiverState, Integer> stateChangedHandler_ = new MulticastChangedWithReasonDelegate.Observer<AirMediaReceiver, AirMediaReceiverState, Integer>() {
        @Override
        public void onEvent(AirMediaReceiver receiver, AirMediaReceiverState from, AirMediaReceiverState to, Integer reason) {
            Log.d(TAG, "view.receiver.event.state  reason=" + reason + "  " + from + "  ==>  " + to);
            if (to == AirMediaReceiverState.Stopped)
            {
            	Log.d(TAG, "In stateChangedHandler: receiverStoppedLatch="+receiverStoppedLatch);
            	isReceiverStarted = false;
				receiverStoppedLatch.countDown();
            }
            if (to == AirMediaReceiverState.Started)
            {
            	Log.d(TAG, "In stateChangedHandler: receiverStartedLatch="+receiverStartedLatch);
            	isReceiverStarted = true;
            	receiverStartedLatch.countDown();
            }
			if (reason != 0)
			{
				stopAllSenders();
				if (reason == CODEC_ERROR) {
					// Force a recovery of the Ducati codec and media server
					mStreamCtl.RecoverDucati();
					mStreamCtl.RecoverMediaServer();
				}
				Log.w(TAG, "Receiver stopped with error="+reason+"  Restarting receiver .... ");
				if (reason != CODEC_ERROR)
					sleep(5000);
                RestartReceiver restarter = new RestartReceiver();
                Thread t = new Thread(restarter);
                t.start();
			}
        }
    };
    
    private final MulticastMessageDelegate.Observer<AirMediaSessionManager, AirMediaSession> addedHandler_ =
            new MulticastMessageDelegate.Observer<AirMediaSessionManager, AirMediaSession>() {
                @Override
                public void onEvent(AirMediaSessionManager manager, final AirMediaSession session) {
                    Log.v(TAG, "manager.sessions.event.added  " + AirMediaSession.toDebugString(session));
                    // Add code here to add session to "table" of sessions and take any action needed
                    registerSessionEventHandlers(session);
                    addSession(session);
                    if (session.videoState() == AirMediaSessionStreamingState.Playing)
                    {
                    	setActiveSession(session);
                    }
                }
            };

    private final MulticastMessageDelegate.Observer<AirMediaSessionManager, AirMediaSession> removedHandler_ =
            new MulticastMessageDelegate.Observer<AirMediaSessionManager, AirMediaSession>() {
                @Override
                public void onEvent(AirMediaSessionManager manager, final AirMediaSession session) {
                    Log.v(TAG, "manager.sessions.event.removed  " + AirMediaSession.toDebugString(session));
                    // Add code here to remove session from "table" of sessions and take any action needed;
                    removeSession(session);
                    if (session.videoState() != AirMediaSessionStreamingState.Playing)
                    {
                    	  unsetActiveSession(session, true);
                    }
                    unregisterSessionEventHandlers(session);
                }
            };
            
    private final MulticastChangedDelegate.Observer<AirMediaSessionManager, AirMediaSessionScreenPositionLayout> layoutChangedHandler_ =
    		new MulticastChangedDelegate.Observer<AirMediaSessionManager, AirMediaSessionScreenPositionLayout>() {
            	@Override public void onEvent(AirMediaSessionManager manager, AirMediaSessionScreenPositionLayout from, AirMediaSessionScreenPositionLayout to) {
            		Log.v(TAG, "manager.sessions.event.layout  " + from + "  ==>  " + to);
            	}
            };

    private final MulticastChangedDelegate.Observer<AirMediaSessionManager, EnumSet<AirMediaSessionScreenPosition>> occupiedChangedHandler_ =
    		new MulticastChangedDelegate.Observer<AirMediaSessionManager, EnumSet<AirMediaSessionScreenPosition>>() {
            	@Override
            	public void onEvent(AirMediaSessionManager manager, EnumSet<AirMediaSessionScreenPosition> removed, EnumSet<AirMediaSessionScreenPosition> added) {
            		Log.v(TAG, "manager.sessions.event.occupied  OCCUPIED:  " + manager.occupied() + "  REMOVED: " + removed + "  ADDED: " + added);
            	}
            };
            
    //	Session Events
    private void registerSessionEventHandlers(AirMediaSession session) {
    	if (session == null) return;
    	session.usernameChanged().register(usernameChangedHandler_);
    	session.addressesChanged().register(addressesChangedHandler_);
    	session.infoChanged().register(infoChangedHandler_);
    	session.connectionChanged().register(connectionChangedHandler_);
    	session.streamingChanged().register(streamingChangedHandler_);
    	session.channelStateChanged().register(channelStateChangedHandler_);
    	session.deviceStateChanged().register(deviceStateChangedHandler_);
    	session.videoStateChanged().register(videoStateChangedHandler_);
    	session.videoResolutionChanged().register(videoResolutionChangedHandler_);
    	session.videoRotationChanged().register(videoRotationChangedHandler_);
    	session.videoSurfaceChanged().register(videoSurfaceChangedHandler_);
    	session.videoDrmChanged().register(videoDrmChangedHandler_);
    	session.videoScreenPositionChanged().register(videoScreenPositionHandler_);
    	session.audioStateChanged().register(audioStateChangedHandler_);
    	session.audioMuteChanged().register(audioMuteChangedHandler_);
    	session.audioVolumeChanged().register(audioVolumeChangedHandler_);
    	session.photoChanged().register(photoChangedHandler_);
    }

    private void unregisterSessionEventHandlers(AirMediaSession session) {
    	if (session == null) return;
    	session.usernameChanged().unregister(usernameChangedHandler_);
    	session.addressesChanged().unregister(addressesChangedHandler_);
    	session.infoChanged().unregister(infoChangedHandler_);
    	session.connectionChanged().unregister(connectionChangedHandler_);
    	session.streamingChanged().unregister(streamingChangedHandler_);
    	session.channelStateChanged().unregister(channelStateChangedHandler_);
    	session.deviceStateChanged().unregister(deviceStateChangedHandler_);
    	session.videoStateChanged().unregister(videoStateChangedHandler_);
    	session.videoResolutionChanged().unregister(videoResolutionChangedHandler_);
    	session.videoRotationChanged().unregister(videoRotationChangedHandler_);
    	session.videoSurfaceChanged().unregister(videoSurfaceChangedHandler_);
    	session.videoDrmChanged().unregister(videoDrmChangedHandler_);
    	session.videoScreenPositionChanged().unregister(videoScreenPositionHandler_);
    	session.audioStateChanged().unregister(audioStateChangedHandler_);
    	session.audioMuteChanged().unregister(audioMuteChangedHandler_);
    	session.audioVolumeChanged().unregister(audioVolumeChangedHandler_);
    	session.photoChanged().unregister(photoChangedHandler_);
    }
    
    private final MulticastChangedDelegate.Observer<AirMediaSession, String> usernameChangedHandler_ =
            new MulticastChangedDelegate.Observer<AirMediaSession, String>() {
                @Override
                public void onEvent(AirMediaSession session, String from, String to) {
                    Log.v(TAG, "view.session.event.username  " + AirMediaSession.toDebugString(session) + "  " + from + "  ==>  " + to);
                    // TODO Handle username changed
                    sendSessionFeedback(session);
                }
            };

    private final MulticastChangedDelegate.Observer<AirMediaSession, Collection<String>> addressesChangedHandler_ =
    		new MulticastChangedDelegate.Observer<AirMediaSession, Collection<String>>() {
    	@Override
    	public void onEvent(AirMediaSession session, Collection<String> from, Collection<String> to) {
    		Log.v(TAG, "view.session.event.addresses  " + AirMediaSession.toDebugString(session) + "  " + from + "  ==>  " + to);
    		// TODO Handle addresses changed
    		sendSessionFeedback(session);
    	}
    };

    private final MulticastChangedDelegate.Observer<AirMediaSession, AirMediaSessionInfo> infoChangedHandler_ =
    		new MulticastChangedDelegate.Observer<AirMediaSession, AirMediaSessionInfo>() {
    	@Override
    	public void onEvent(AirMediaSession session, AirMediaSessionInfo from, AirMediaSessionInfo to) {
    		Log.v(TAG, "view.session.event.info  " + AirMediaSession.toDebugString(session) + "  " + from + "  ==>  " + to);
    	}
    };

    private final MulticastChangedDelegate.Observer<AirMediaSession, AirMediaSessionConnectionState> connectionChangedHandler_ =
            new MulticastChangedDelegate.Observer<AirMediaSession, AirMediaSessionConnectionState>() {
                @Override
                public void onEvent(AirMediaSession session, AirMediaSessionConnectionState from, AirMediaSessionConnectionState to) {
                    Log.v(TAG, "view.session.event.connection  " + AirMediaSession.toDebugString(session) + "  " + from + "  ==>  " + to);
                    // TODO Handle connection state change
                    sendSessionFeedback(session);
                }
            };

    private final MulticastChangedDelegate.Observer<AirMediaSession, AirMediaSessionStreamingState> streamingChangedHandler_ =
            new MulticastChangedDelegate.Observer<AirMediaSession, AirMediaSessionStreamingState>() {
                @Override
                public void onEvent(AirMediaSession session, AirMediaSessionStreamingState from, AirMediaSessionStreamingState to) {
                    Log.v(TAG, "view.session.event.streaming  " + AirMediaSession.toDebugString(session) + "  " + from + "  ==>  " + to);
                    // TODO Handle streaming change
                }
            };

    private final MulticastChangedDelegate.Observer<AirMediaSession, AirMediaSessionConnectionState> channelStateChangedHandler_ =
            new MulticastChangedDelegate.Observer<AirMediaSession, AirMediaSessionConnectionState>() {
                @Override
                public void onEvent(AirMediaSession session, AirMediaSessionConnectionState from, AirMediaSessionConnectionState to) {
                    Log.v(TAG, "view.session.event.channel  " + AirMediaSession.toDebugString(session) + "  id= " + Integer.toHexString(session.channelId()) + "  " + from + "  ==>  " + to);
                    // TODO Handle connection state change
                }
            };

    private final MulticastMessageDelegate.Observer<AirMediaSession, byte[]> receivedHandler_ =
            new MulticastMessageDelegate.Observer<AirMediaSession, byte[]>() {
                @Override
                public void onEvent(AirMediaSession session, byte[] message) {
                    Log.v(TAG, "view.session.event.message  " + AirMediaSession.toDebugString(session) + "  id= " + Integer.toHexString(session.channelId()) + "  " + message.length + " bytes");
                }
            };

    private final MulticastChangedDelegate.Observer<AirMediaSession, AirMediaSessionConnectionState> deviceStateChangedHandler_ =
            new MulticastChangedDelegate.Observer<AirMediaSession, AirMediaSessionConnectionState>() {
                @Override
                public void onEvent(AirMediaSession session, AirMediaSessionConnectionState from, AirMediaSessionConnectionState to) {
                    Log.v(TAG, "view.session.event.device  " + AirMediaSession.toDebugString(session) + "  id= " + session.deviceId() + "  " + from + "  ==>  " + to);
                    // TODO Handle device state change
                    if (session.deviceState() == AirMediaSessionConnectionState.Disconnected)
                    {
                    	if (AirMediaSession.isEqual(session, sessionRequestingStop))
                    		deviceDisconnectedLatch.countDown();
                    }
                }
            };

    private final MulticastChangedDelegate.Observer<AirMediaSession, AirMediaSessionStreamingState> videoStateChangedHandler_ =
            new MulticastChangedDelegate.Observer<AirMediaSession, AirMediaSessionStreamingState>() {
                @Override
                public void onEvent(AirMediaSession session, AirMediaSessionStreamingState from, AirMediaSessionStreamingState to) {
                    Log.v(TAG, "view.session.event.video.state  " + AirMediaSession.toDebugString(session) + "  " + from + "  ==>  " + to);
                    // TODO Handle video state change
                      if (session.videoState() == AirMediaSessionStreamingState.Playing)
                      {
                    	  setActiveSession(session);
                      } 
                      else if (session.videoState() == AirMediaSessionStreamingState.Stopped)
                      {
                    	  unsetActiveSession(session, true);
                      }
                }
            };

    private final MulticastChangedDelegate.Observer<AirMediaSession, AirMediaSize> videoResolutionChangedHandler_ =
            new MulticastChangedDelegate.Observer<AirMediaSession, AirMediaSize>() {
                @Override
                public void onEvent(AirMediaSession session, AirMediaSize from, AirMediaSize to) {
                    Log.v(TAG, "view.session.event.video.size  " + AirMediaSession.toDebugString(session) + "  " + 
                    		from.width + "x" + from.height + "  ==>  " + to.width + "x" + to.height);
                    // TODO Handle video resolution change
                    if (session == session())
                    {
                    	// If it is the active session apply
                    	setVideoTransformation();
                    }
                }
            };

    private final MulticastChangedDelegate.Observer<AirMediaSession, Integer> videoRotationChangedHandler_ =
            new MulticastChangedDelegate.Observer<AirMediaSession, Integer>() {
                @Override
                public void onEvent(AirMediaSession session, Integer from, Integer to) {
                    Log.v(TAG, "view.session.event.video.rotation  " + AirMediaSession.toDebugString(session) + "  " + from + "  ==>  " + to);
                    // TODO Handle video rotation change
                    if (session == session())
                    {
                    	// If it is the active session apply
                    	setVideoTransformation();
                    }                
                }
            };

    private final MulticastChangedDelegate.Observer<AirMediaSession, Surface> videoSurfaceChangedHandler_ =
            new MulticastChangedDelegate.Observer<AirMediaSession, Surface>() {
                @Override
                public void onEvent(AirMediaSession session, Surface from, Surface to) {
                    Log.v(TAG, "view.session.event.video.surface  " + AirMediaSession.toDebugString(session) + "  " + from + "  ==>  " + to);
                    // TODO Handle video surface change change
                }
            };

    private final MulticastMessageDelegate.Observer<AirMediaSession, Boolean> videoDrmChangedHandler_ =
            new MulticastMessageDelegate.Observer<AirMediaSession, Boolean>() {
                @Override
                public void onEvent(AirMediaSession session, Boolean value) {
                    Log.v(TAG, "view.session.event.video.drm  " + AirMediaSession.toDebugString(session) + "  " + !value + "  ==>  " + value);
                    // TODO Handle video Drm change
                }
            };

    private final MulticastChangedDelegate.Observer<AirMediaSession, AirMediaSessionScreenPosition> videoScreenPositionHandler_ =
            new MulticastChangedDelegate.Observer<AirMediaSession, AirMediaSessionScreenPosition>() {
                @Override
                public void onEvent(AirMediaSession session, AirMediaSessionScreenPosition from, AirMediaSessionScreenPosition to) {
                    Log.v(TAG, "view.session.event.video.screen-position  " + AirMediaSession.toDebugString(session) + "  " + from + "  ==>  " + to);
                    // TODO Handle video screen position change
                    sendSessionFeedback(session);
                }
            };

    private final MulticastChangedDelegate.Observer<AirMediaSession, AirMediaSessionStreamingState> audioStateChangedHandler_ =
            new MulticastChangedDelegate.Observer<AirMediaSession, AirMediaSessionStreamingState>() {
                @Override
                public void onEvent(AirMediaSession session, AirMediaSessionStreamingState from, AirMediaSessionStreamingState to) {
                    Log.v(TAG, "view.session.event.audio.state  " + AirMediaSession.toDebugString(session) + "  " + from + "  ==>  " + to);
                    // TODO Handle audio state change
                }
            };

    private final MulticastMessageDelegate.Observer<AirMediaSession, Boolean> audioMuteChangedHandler_ =
            new MulticastMessageDelegate.Observer<AirMediaSession, Boolean>() {
                @Override
                public void onEvent(AirMediaSession session, Boolean value) {
                    Log.v(TAG, "view.session.event.audio.mute  " + AirMediaSession.toDebugString(session) + "  " + !value + "  ==>  " + value);
                    // TODO Handle audio mute change
                }
            };

    private final MulticastChangedDelegate.Observer<AirMediaSession, Float> audioVolumeChangedHandler_ =
            new MulticastChangedDelegate.Observer<AirMediaSession, Float>() {
                @Override
                public void onEvent(AirMediaSession session, Float from, Float to) {
                    Log.v(TAG, "view.session.event.audio.volume  " + AirMediaSession.toDebugString(session) + "  " + from + "  ==>  " + to);
                    // TODO Handle audio volume change
                }
            };

    private final MulticastChangedDelegate.Observer<AirMediaSession, Bitmap> photoChangedHandler_ =
            new MulticastChangedDelegate.Observer<AirMediaSession, Bitmap>() {
                @Override
                public void onEvent(AirMediaSession session, Bitmap from, Bitmap to) {
                    Log.v(TAG, "view.session.event.photo  " + AirMediaSession.toDebugString(session) + "  " + from + "  ==>  " + to);
                }
            };


    // Implement surface holder callback
    private final SurfaceHolder.Callback video_surface_callback_handler_ = new SurfaceHolder.Callback() {
    	@Override
    	public void surfaceCreated(SurfaceHolder holder) {
    		Surface surface = holder.getSurface();
    		Log.v(TAG, "surfaceCreated  surface= " + surface);
    		attachSurface();
    	}

    	@Override
    	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    		Surface surface = holder.getSurface();
    		Log.v(TAG, "surfaceChanged  surface= " +  surface + "  format= " + format + "  wxh= " + width + "x" + height);
    	}

    	@Override
    	public void surfaceDestroyed(SurfaceHolder holder) {
    		Log.v(TAG, "surfaceDestroyed  surface= " + holder.getSurface());
    		detachSurface();
    	}
    };
}
