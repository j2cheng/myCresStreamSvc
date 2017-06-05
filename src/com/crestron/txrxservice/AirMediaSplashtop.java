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

import com.crestron.airmedia.utilities.ViewBase;
import com.crestron.airmedia.utilities.ViewBase.Size;
import com.crestron.airmedia.utilities.delegates.MulticastChangedDelegate;
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
    public CountDownLatch serviceConnectedLatch=null;
    public CountDownLatch receiverLoadedLatch=null;
    public CountDownLatch receiverStoppedLatch=null;

	private AirMediaReceiver receiver_;
	private IAirMediaReceiver service_=null;
	private AirMediaSessionManager manager_;
	
	private AirMediaSession active_session_=null;
	private SurfaceHolder surfaceHolder_;
	private TextureView textureView_;
	private Surface surface_=null;
	private Rect window_= new Rect();
	private boolean resume_ = false;
	private String adapter_ip_address = "";

    private Handler handler_;
    private Map<Integer, AirMediaSession> userSessionMap = new ConcurrentHashMap<Integer, AirMediaSession>();

 
    public AirMediaSplashtop(CresStreamCtrl streamCtl) 
    {
    	mStreamCtl = streamCtl;
    	mContext = (Context)mStreamCtl;
    	    	
//    	shutDownAirMediaSplashtop();	// In case AirMediaSplashtop was already running shut it down
    	    			
		if (!connect2service()) {
			Log.e(TAG, "Service failed to connect, restarting txrxservice and m360 service");

			// Library failed to load kill mediaserver and restart txrxservice
//			RecoverMediaServer();
//			RecoverTxrxService();
		}
		
		Log.i(TAG, "AirMediaSpashtop constructor exiting receiver=" + receiver() + "  service=" + service() + "  manager=" + manager());
    }
    
    private void startAirMedia()
    {
		String host = MiscUtils.getHostName("AirMedia");		
		adapter_ip_address = mStreamCtl.getAirMediaConnectionIpAddress(0);

		// Now start receiver
        if (!startReceiver(host)) {
			Log.e(TAG, "Receiver failed to load, restarting txrxservice and m360 service");

			// Library failed to load kill mediaserver and restart txrxservice
//			RecoverMediaServer();
//			RecoverTxrxService();
        }

		manager_ = receiver_.sessionManager();
		if (manager_ != null)
		{
			registerSessionManagerEventHandlers(manager_);
		}
		intializeDisplay();
		
		set4in1ScreenEnable(false); // TODO: Remove this when quad view is eventually enabled
		resume_ = true;
    }
    
    private boolean connect2service()
    {
    	serviceConnectedLatch = new CountDownLatch(1);
		// start service and instantiate receiver class
		doBindService();
		boolean successfulStart = true; //indicates that there was no time out condition
		try { successfulStart = serviceConnectedLatch.await(3000, TimeUnit.MILLISECONDS); }
		catch (InterruptedException ex) { ex.printStackTrace(); }

		return successfulStart;
    }
    
    private boolean startReceiver(final String serverName)
    {
		boolean successfulStart = true; //indicates receiver was successfully loaded

        if (receiver_ == null) {
        	try {
        		receiver_ = new AirMediaReceiver(service_);

                receiver().serverName(serverName);
                receiver().product(-1); // TODO find out what this is for ?
                receiver().adapterAddress(adapter_ip_address);
                receiver().maxResolution(AirMediaReceiverResolutionMode.Max1080P);
                Point dSize = mStreamCtl.getDisplaySize();
                receiver().displayResolution(new AirMediaSize(dSize.x, dSize.y));

        		registerReceiverEventHandlers(receiver());

        		receiverLoadedLatch = new CountDownLatch(1);
        		Log.i(TAG,"Calling receiver.initalize()");
        		receiver().initialize();        		
        		try { successfulStart = receiverLoadedLatch.await(3000, TimeUnit.MILLISECONDS); }
        		catch (InterruptedException ex) { ex.printStackTrace(); }
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
        	Log.i(TAG, "Starting AirMedia Receiver");
        	receiver().start();
        }
		return successfulStart;
    }
    
    public void showVideo()
    {
    	mStreamCtl.showSplashtopWindow(streamIdx);
    }
    
    public void hideVideo()
    {
    	mStreamCtl.hideSplashtopWindow(streamIdx);
    }
    
    
    public void onDestroy(){
    	Log.i(TAG, "AirMediaSplashtop Class destroyed!!!");

    	surfaceDisplayed = false;

    	shutDownAirMediaSplashtop();
    }

    public void shutDownAirMediaSplashtop(){
    	doUnbindService();
    }
    
    public void recover(){
    	if (mStreamCtl.userSettings.getAirMediaLaunch())
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
    
    public void show(int x, int y, int width, int height)
    {
    	Rect window = new Rect(x, y, x+width-1, y+height-1);
    	if (surfaceDisplayed == false || !MiscUtils.rectanglesAreEqual(window_, window))
    	{
	    	surfaceDisplayed = true;
	    	window_ = window;
	    		    	
	    	Log.i(TAG, "show: Show window 0 " + window_);
	
	    	//show surface
	    	setSurfaceSize(x,y,width,height, true);	
	    	
	    	showVideo();
	    	
	    	Log.d(TAG, "show: calling attachSurface");
	    	if (session() != null && session().videoSurface() == null)
	    	{
	    		attachSurface();	
	    	}
    	}
    	else
    		Log.i(TAG, "show: AirMedia already shown, ignoring request");
    }    
    
    public void hide(boolean sendStopToSender)
    {
    	if (surfaceDisplayed == true)
    	{
    		surfaceDisplayed = false;
    		
    		detachSurface();
    		
    		if (mStreamCtl.use_texture)
    		{
    			if (Boolean.parseBoolean(mStreamCtl.hdmiOutput.getSyncStatus()))
    			{
    				clearSurface();
    			}
    		}
    		
    		hideVideo();
    		    		
    		if (sendStopToSender)
    			stopAllSenders(); // Inform senders that stream is stopped/hidden
    	}
    	else
    		Log.i(TAG, "hide: AirMedia already hidden, ignoring request");
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
    
    public boolean getSurfaceDisplayed()
    {
    	return surfaceDisplayed;
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
    	mStreamCtl.setVideoTransformation(0, mStreamCtl.use_texture);
    }

    public void setVideoTransformation(int x, int y, int w, int h) {
    	
    	AirMediaSession session = session();
    	
    	if (session == null)
    		return;
    			
    	if (!mStreamCtl.use_texture)
    		return;
    	
        final float viewWidth = (float)w;
        final float viewHeight = (float)h;

        final float videoWidth = (float)session.videoResolution().width;
        final float videoHeight = (float)session.videoResolution().height;

        Log.d(TAG, "setTransformation x,y,w,h="+x+","+y+","+w+","+h+"    video WxH="+videoWidth+"x"+videoHeight );

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
        Log.d(TAG, "setTransformation isLandscape="+isLandscape+"    scale"+scale+"  scaleX="+scaleX+"    scaleY="+scaleY );

        final float viewCenterX = viewWidth / 2.0f;
        final float viewCenterY = viewHeight / 2.0f;

        Log.d(TAG, "setTransformation  " + AirMediaSession.toDebugString(session()) + "  view= " + "1920x1080" + "  video= " + session.videoResolution() + "  rotate= " + videoRotation + "°" + "  scale= " + scaleX + " , " + scaleY);

    	textureView_ = mStreamCtl.getAirMediaTextureView();
        Matrix matrix = textureView_.getTransform(null);
        matrix.setRotate(videoRotation, viewCenterX, viewCenterY);
        matrix.postScale(scaleX, scaleY, viewCenterX, viewCenterY);
        textureView_.setTransform(matrix);
        mStreamCtl.dispSurface.forceParentLayoutInvalidation();
    }

    private void attachSurface() {
    	Surface surface;
    	SurfaceTexture surfaceTexture = null;
    	AirMediaSession session = session();
    	if (session == null) return;
    	AirMediaReceiver receiver = receiver();
    	if (receiver == null) return;
    	if (!getSurfaceDisplayed()) return;
    	if (session.videoSurface() != null)
    	{
			Log.d(TAG, "attachSurface: detach prior surface");
    		session.detach();
    	}
    	if (!mStreamCtl.use_texture)
    	{
    		surfaceHolder_ = mStreamCtl.getCresSurfaceHolder(streamIdx);
    		surface = surfaceHolder_.getSurface();
    		//surfaceHolder_.addCallback(video_surface_callback_handler_);
    	} else {
    		surfaceTexture = mStreamCtl.getAirMediaSurfaceTexture();
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
			surfaceHolder_.addCallback(video_surface_callback_handler_);
			Log.w(TAG, "attachSurface: No valid surface at this time");
		}
		if (mStreamCtl.use_texture)
		{
			setVideoTransformation();
		}
		mStreamCtl.dispSurface.stMGR.setInvalidateOnSurfaceTextureUpdate(true);
    }
    
    // TODO move into CresDisplaySurface
    private void clearSurface()
    {
    	Canvas canvas = null;
    	Surface s = new Surface(mStreamCtl.getAirMediaSurfaceTexture());
   	 	Log.i(TAG, "clearSurface: clearing surface: " + s);
    	TextureView textureView = mStreamCtl.dispSurface.GetAirMediaTextureView();
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
    		 if (mStreamCtl.use_texture)
    		 {
    			 // release surface if using textureview
    	    	 Log.i(TAG, "detachSurface: releasing surface: " + surface_);
    			 surface_.release();
    		 }
    	 }
    }
    
    // set an active session - give it the surface and show the video
    public void setActiveSession(AirMediaSession session)
    {
    	// if we already have an active session, then check if it is same
    	if (AirMediaSession.isEqual(session(), session))
    	{
    		return;
    	}
    	
    	// take surface away from active session if one exists
    	if (session() != null)
    	{
        	Log.i(TAG, "setActiveSession: removing prior active session " + AirMediaSession.toDebugString(session()));
    		unsetActiveSession(session(), false);
    	}
    	
    	active_session_ = session;
		Log.d(TAG, "setActiveSession: setting active session " + AirMediaSession.toDebugString(session()));  
		
		attachSurface();

 		// Send status of 1 to indicate video is playing
		querySenderList(false);
    } 
    
	// unset active session - take away its surface and hide the video from it
    public void unsetActiveSession(AirMediaSession session, boolean goto_next_active)
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
    	
    	Log.d(TAG, "removing active session setting active to null" + AirMediaSession.toDebugString(session()));
    	active_session_ = null;
    	
    	// If not asked to go to next active, we are done
    	Log.d(TAG, "removing active session goto_next=" + String.valueOf(goto_next_active));

    	if (!goto_next_active) {
        	Log.d(TAG, "detached surface returning from unsetActiveSession" + AirMediaSession.toDebugString(session()));
        	// status report should be handled by caller (it should call querySenderList
    		return;
    	}
    	
    	// Loop over remaining sessions to see if any are in playing state
    	// First one found will be made active and provided the surface
    	for (int i=1; i <= MAX_USERS; i++) {
    		AirMediaSession s = user2session(i);
    		if (s == null)
    			continue;
    		if (s.videoState() == AirMediaSessionStreamingState.Playing) {
    			active_session_ = s;
    	    	Log.d(TAG, "found candidate session i=" + String.valueOf(i) + " session: "+ AirMediaSession.toDebugString(session()));
    			// attach surface to new session
    			attachSurface();
    			break;
    		}
    	}
    	Log.d(TAG, "unsetActiveSessions - calling querySenderList ");
		querySenderList(false);
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
    	Log.i(TAG, "Set Login Code = " + String.valueOf(loginCode));
		if (loginCode < 0) {
			receiver().serverPassword("");
		} else {
			receiver().serverPassword(String.valueOf(loginCode));	
		}
    }
    
    public void setLoginCodeDisable()
    {
		setLoginCode(-1);
    }
    
    public void setLoginCodePrompt(String loginCodePrompt)
    {
    }
    
    private void stopReceiver()
    {
    	Log.i(TAG, "--------------- Calling stop for receiver");
    	boolean successfullStop = true;
		receiverStoppedLatch = new CountDownLatch(1);
    	receiver_.stop();
		try { successfullStop = receiverStoppedLatch.await(30000, TimeUnit.MILLISECONDS); }
		catch (InterruptedException ex) { ex.printStackTrace(); }
    }
    
    public void setAdapter(String address)
    {
    	if (receiver_ == null)
    		return;
    	if (adapter_ip_address.equals(address))
    		return;
    	adapter_ip_address = address;
    	Log.i(TAG, "--------------- Stopping all senders");
    	stopAllSenders();
    	Log.i(TAG, "--------------- Stopping receiver");
    	stopReceiver();
    	Log.i(TAG, "--------------- Setting new ip address for receiver: " + address);
    	receiver_.adapterAddress(address);
    	Log.i(TAG, "--------------- Starting receiver");
    	receiver().start();
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
		if (active_session_ != null && !AirMediaSession.isEqual(active_session_, session))
		{
			if (session.videoState() == AirMediaSessionStreamingState.Playing)
			{
				Log.w(TAG, "Trying to stop a playing session which is not active for user " + String.valueOf(userId));
				return;
			}
		}
		if (AirMediaSession.isEqual(active_session_, session)) {
			// session is  started
			session.stop(); 
		}
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
    
    private void showSurface(boolean enable)
    {
    }
    
    public void setSurfaceSize(int x, int y, int width, int height, boolean launch)
    {		
		Log.d(TAG, "------------ calling updateWindow --------------");
		mStreamCtl.setAirMediaWindowDimensions(x, y, width, height, streamIdx);
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
		Log.d(TAG, "doBindService - comleted bind BIND=" + AirMediaReceiver.AIRMEDIA_SERVICE_BIND + "   PACKAGE=" + AirMediaReceiver.AIRMEDIA_SERVICE_PACKAGE);
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
            try {
                receiver_.close();
                receiver_ = null;
                serviceConnectedLatch = new CountDownLatch(1);
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

    private final MulticastChangedDelegate.Observer<AirMediaReceiver, AirMediaReceiverState> stateChangedHandler_ = new MulticastChangedDelegate.Observer<AirMediaReceiver, AirMediaReceiverState>() {
        @Override
        public void onEvent(AirMediaReceiver receiver, AirMediaReceiverState from, AirMediaReceiverState to) {
            Log.v(TAG, "view.receiver.event.state  " + from + "  ==>  " + to);
            if (to == AirMediaReceiverState.Stopped)
            {
            	//Log.v(TAG, "In stateChangedHandler: receiverStoppedLatch="+receiverStoppedLatch+ "     receiver="+receiver);
            	Log.v(TAG, "In stateChangedHandler: receiverStoppedLatch="+receiverStoppedLatch);
            	receiverStoppedLatch.countDown();
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
    		Log.v(TAG, "view.session.event.username  " + AirMediaSession.toDebugString(session) + "  " + from + "  ==>  " + to);
    		// TODO Handle username changed
    		sendSessionFeedback(session);
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
