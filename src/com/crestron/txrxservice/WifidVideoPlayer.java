package com.crestron.txrxservice;

import android.graphics.Bitmap;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceView;

import com.crestron.airmedia.receiver.IVideoPlayer;
import com.crestron.airmedia.receiver.IVideoPlayerObserver;
import com.crestron.airmedia.receiver.m360.models.AirMediaReceiver;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaPlatforms;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSessionStreamingState;
import com.crestron.txrxservice.CresStreamCtrl;
import com.crestron.airmedia.utilities.Common;
import com.crestron.airmedia.utilities.TaskScheduler;
import com.crestron.airmedia.utilities.ViewBase;
import com.crestron.airmedia.utilities.delegates.MulticastChangedDelegate;
import com.crestron.airmedia.utilities.delegates.MulticastMessageDelegate;
import com.crestron.airmedia.utilities.delegates.Observer;
import com.crestron.airmedia.utilities.TimeSpan;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class WifidVideoPlayer {
    private static final String TAG = "WifidVideoPlayer";
    private static final long INVALID_SESSION_ID = (long) 0;
    private final Object startSessionObjectLock = new Object();
    private final Object stopSessionObjectLock = new Object();
    
    WifidVideoPlayer(CresStreamCtrl streamCtrl) {
        streamCtrl_ = streamCtrl;
    }
    
    private WifidVideoPlayer self() { return this; }

    public void register(AirMediaReceiver receiver)
    {
    	receiver_ = receiver;
		service_.clear(); // clear observer list in service
    	// register this class with the receiver as well
		Common.Logging.i(TAG, "Registering videoplayer with receiver");
    	receiver.addVideoPlayer(service_);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// FIELDS
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private final Object lock_ = new Object();
    private long id_ = 0;
    private AirMediaReceiver receiver_ = null;
    private CresStreamCtrl streamCtrl_ = null;
    private final Map<Long, VideoSession> sessionMap = new HashMap<Long, VideoSession>();

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// PROPERTIES
    ////////////////////////////////////////////////////////////////////////////////////////////////
    

    public AirMediaReceiver receiver() { return receiver_;}    
    public CresStreamCtrl streamCtrl() { return streamCtrl_;}

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// DEFINITIONS
    ////////////////////////////////////////////////////////////////////////////////////////////////

    
    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// SERVICE
    ////////////////////////////////////////////////////////////////////////////////////////////////
    
    IVideoPlayer service() { return service_; }
    
    private final VideoPlayerService service_ = new VideoPlayerService();
    
    private class VideoPlayerService extends IVideoPlayer.Stub {
    	VideoPlayerService() {
//            try {
//            	service_.register(observers_);
//            } catch (Exception e) {
//                Common.Logging.e(TAG, "VideoPlayerService  EXCEPTION  " + e + "while registering observer");
//            }
    	}   	
        
        private final Map<IBinder, IVideoPlayerObserver> observers_ = new HashMap<IBinder, IVideoPlayerObserver>();
        private IVideoPlayerObserver[] observers() { synchronized (observers_) { return observers_.values().toArray(new IVideoPlayerObserver[observers_.size()]); } }

        private void add(IVideoPlayerObserver observer) {
            synchronized (observers_) {
                IBinder binder = observer.asBinder();
                Common.Logging.i(TAG, "videoplayer.add  observer= " + observer + "  binder= " + binder);
                observers_.put(binder, observer);
            }
        }

        private void remove(IVideoPlayerObserver observer) {
            synchronized (observers_) {
                IBinder binder = observer.asBinder();
                Common.Logging.i(TAG, "videoplayer.remove  observer= " + observer + "  binder= " + binder);
                observers_.remove(binder);
            }
        }
        
        private void clear() {
            Common.Logging.i(TAG, "videoplayer.clear  clearing observer list");
        	observers_.clear();
        }
        
        @Override
        public void register(IVideoPlayerObserver observer) throws RemoteException {
            Common.Logging.i(TAG, "videoplayer.register  observer= " + observer);
            add(observer);
        }

        @Override
        public void unregister(IVideoPlayerObserver observer) throws RemoteException {
            Common.Logging.i(TAG, "videoplayer.unregister  observer= " + observer);
            remove(observer);
        }
        
        public void statusChanged(long id, int code, String reason)
        {
        	for (IVideoPlayerObserver observer : observers()) {
        		try {
        			observer.onStatusUpdate(id, code, reason);
        		} catch (RemoteException e) {
                    Common.Logging.e(TAG, "videoplayer.observer.statusChanged  id= " + id + "  code= " + code + "  reason= " + reason + "  EXCEPTION  " + e + "  " + Log.getStackTraceString(e));
                    remove(observer);
        		}
        	}
        }
        
        public void stateChanged(long id, AirMediaSessionStreamingState value)
        {
        	for (IVideoPlayerObserver observer : observers()) {
        		try {
        			observer.onStateChanged(id, value.value);
        		} catch (RemoteException e) {
                    Common.Logging.e(TAG, "videoplayer.observer.stateChanged  id= " + id + "  state= " + value + "  EXCEPTION  " + e + "  " + Log.getStackTraceString(e));
                    remove(observer);
        		}
        	}
        }

        public void resolutionChanged(long id, int width, int height)
        {
        	for (IVideoPlayerObserver observer : observers()) {
        		try {
        			observer.onResolutionChanged(id, width, height);
        		} catch (RemoteException e) {
                    Common.Logging.e(TAG, "videoplayer.observer.resolutionChanged  id= " + id + "  resolution= " + width + "x" + height + "  EXCEPTION  " + e + "  " + Log.getStackTraceString(e));
                    remove(observer);
        		}
        	}
        }
        
        public void onSessionReady(long id, String device_id, String device_name, String device_address, int port, String local_address)
        {
        	for (IVideoPlayerObserver observer : observers()) {
        		try {
        			observer.onSessionReady(id, device_id, device_name, device_address, port, local_address);
        		} catch (RemoteException e) {
                    Common.Logging.e(TAG, "videoplayer.observer.onSessionReady  id= " + id + "  device_id= " + device_address + "  device_name=" + device_name +
                    		"  device_address=" + device_address + "  rtsp_port=" + port + "  local_address=" + local_address+ "  EXCEPTION  " + e + "  " + Log.getStackTraceString(e));
                    remove(observer);
        		}
        	}
        }
        
        public void infoChanged(long id, AirMediaPlatforms platform, String os, String version)
        {
        	for (IVideoPlayerObserver observer : observers()) {        		
        		try {
        			observer.onInfoChanged(id, platform, os, version);
        		} catch (RemoteException e) {
                    Common.Logging.e(TAG, "videoplayer.observer.onInfoChanged  id= " + id + "  platform= " + platform.toString() + "  os=" + os +
                    		"  version=" + version + "  EXCEPTION  " + e + "  " + Log.getStackTraceString(e));
                    remove(observer);
        		}
        	}
        }
        
        ////////////////////////////////////////////////////////////////////////////////////////////////
        /// METHODS
        ////////////////////////////////////////////////////////////////////////////////////////////////

        @Override
        public int getVideoState(long id) {
            VideoSession session = sessionMap.get(id);
        	return (session == null) ? 0 : session.state.value;
        }
        
        /// start
        @Override
        public void start(long id, String endpoint, int port, Surface surface, String localAddress)
        {
            Common.Logging.i(TAG, "VideoPlayer.start  sessionId="+id+"  url="+endpoint+"  port="+port+"   Surface="+surface+"   localAddress="+localAddress);
            if (surface == null)
            {
                Common.Logging.w(TAG, "Cannot start player with null surface");
                return;
            }
            int streamId = streamCtrl_.surface2streamId(surface);
            if (streamId < 0)
            {
                Common.Logging.w(TAG, "Cannot find streamId for surface="+surface+" ignoring start command....");
                return;
            }
			// See if prior session exists with the same id
            VideoSession session = sessionMap.get(id);
            if (session != null)
            {
            	if (session.state == AirMediaSessionStreamingState.Playing || session.state == AirMediaSessionStreamingState.Starting)
            	{
                    Common.Logging.w(TAG, "There is an existing session with this id --- stopping it");
            		stop(id);
            	} else {
                    Common.Logging.w(TAG, "There is an existing session with this id --- removing it");
            		sessionMap.remove(id);
            	}
            }
            synchronized(startSessionObjectLock) {
            	session = new VideoSession(id, streamId, surface, AirMediaSessionStreamingState.Stopped);
            	// Start the video player
            	// Put session into the map
            	streamCtrl_.startWfdStream(streamId, id, endpoint, port, localAddress);
            }
            sessionMap.put(id, session);
            WifidVideoPlayer.this.stateChanged(streamId, AirMediaSessionStreamingState.Starting);
            Common.Logging.i(TAG, "VideoPlayer.start  sessionId="+id+"  exiting...");
        }
        
        /// startWithDtls
        @Override
        public void startWithDtls(long id, String endpoint, int port, Surface surface, String key, int cipher, int authentication)
        {
        }

        @Override
        public void stop(long id)
        {
            Common.Logging.i(TAG, "VideoPlayer.stop  sessionId="+id);
            stopSession(id);
            Common.Logging.i(TAG, "VideoPlayer.stop exit - sessionId="+id);
        }
        
        @Override
        public void setAdapterAddress(String address)
        {
            boolean msMiceOn = streamCtrl_.userSettings.getAirMediaMiracastEnable() && streamCtrl_.userSettings.getAirMediaMiracastMsMiceMode();
            Common.Logging.i(TAG, "VideoPlayer.setAdapterAddress  address="+address+"     msMiceOn = "+msMiceOn);
        	streamCtrl_.streamPlay.msMiceSetAdapterAddress(msMiceOn ? address : null);
            Common.Logging.i(TAG, "VideoPlayer.setAdapterAddress exit - address set to "+address);
        }
        
        @Override
        public void setPasscode(String pin)
        {
            Common.Logging.i(TAG, "VideoPlayer.setPasscode  pin="+pin);
            streamCtrl_.streamPlay.msMiceSetPin(pin);
            Common.Logging.i(TAG, "VideoPlayer.setPasscode exit - pin set to "+pin);
        }
        
        private String list2String(List<String> addresses, String separator)
        {
        	StringBuilder addressStringBuilder = new StringBuilder();
        	for (String address : addresses) {
        		addressStringBuilder.append(address);
        		addressStringBuilder.append(separator);
        	}
            String addressString = addressStringBuilder.toString();
            // remove last separator
            return addressString.substring(0, addressString.length()-separator.length());
        }
        
        @Override
        public void setAdapterAddresses(List<String> addresses)
        {
            boolean msMiceOn = streamCtrl_.userSettings.getAirMediaMiracastEnable() && streamCtrl_.userSettings.getAirMediaMiracastMsMiceMode();
            Common.Logging.i(TAG, "VideoPlayer.setAdapterAddresses  address="+addresses+"     msMiceOn = "+msMiceOn);
            String addressesString = list2String(addresses,",");
        	streamCtrl_.streamPlay.msMiceSetAdapterAddress(msMiceOn ? addressesString : null);
            Common.Logging.i(TAG, "VideoPlayer.setAdapterAddresses exit - address set to "+addressesString);
        }
        
        @Override
        public void disconnect(long id)
        {
            Common.Logging.i(TAG, "VideoPlayer.disconnect  sessionId="+id);
            //streamCtrl_.streamPlay.msMiceSetCloseSession(id);
            Common.Logging.i(TAG, "VideoPlayer.disconnect exit - sessionId="+id);
        }
    }

    private class VideoSession {
    	public long sessionId;
    	public AirMediaSessionStreamingState state;
    	public Surface surface;
    	public int streamId;
    	public String osVersion;
    	
    	public VideoSession(long id, int streamIdx, Surface s, AirMediaSessionStreamingState curState)
    	{
    		sessionId = id;
    		streamId = streamIdx;
    		surface = s;
    		state = curState;
    		osVersion = null;
    	}
    }
    
    public long streamId2sessionId(int streamId)
    {
    	long sessionId = INVALID_SESSION_ID;
		for (Map.Entry<Long, VideoSession> entry : sessionMap.entrySet())
		{
			VideoSession session = entry.getValue();
			if (session.streamId == streamId)
			{
				sessionId = session.sessionId;
				break;
			}
		}
		return sessionId;
    }
    
    public int sessionId2streamId(long sessionId)
    {
    	int streamId = -1;
		for (Map.Entry<Long, VideoSession> entry : sessionMap.entrySet())
		{
			VideoSession session = entry.getValue();
			if (session.sessionId == sessionId)
			{
				streamId = session.streamId;
				break;
			}
		}
		return streamId;
    }

    /// EVENTS

    public void statusChanged(long id, int code, String reason)
    {
        Common.Logging.i(TAG, "videoplayer.statusChanged():  sessionId="+id+"  code="+code+"   reason="+reason);
    	service_.statusChanged(id, code, reason);
    }
    
    public void resolutionChanged(int streamId, int width, int height)
    {
    	long sessionId = streamId2sessionId(streamId);
        Common.Logging.i(TAG, "videoplayer.resolutionChanged(): streamId="+streamId+" sessionId="+sessionId+"  wxh="+width+"x"+height);
    	service_.resolutionChanged(sessionId, width, height);
    	if (sessionId != INVALID_SESSION_ID)
    	{
    		service_.resolutionChanged(sessionId, width, height);
    	}
    }
    
    public void stateChanged(int streamId, AirMediaSessionStreamingState value)
    {
    	long sessionId = streamId2sessionId(streamId);
        Common.Logging.i(TAG, "videoplayer.stateChanged():  streamId="+streamId+" sessionId="+sessionId+"  state="+value);
    	if (sessionId != INVALID_SESSION_ID)
    	{
    		VideoSession session = sessionMap.get(sessionId);
    		if (session.state != value)
    		{
    			session.state = value;
    			service_.stateChanged(sessionId, value);
    		}
    	}
    }
    
    public void onSessionReady(long id, String local_address, String device_id, String device_name, String device_address, int port) 
    {
        Common.Logging.i(TAG, "videoplayer.onSessionReady():  sessionId="+id+" deviceId="+device_id+" deviceName="+device_name+" deviceAddress="+device_address+" rtsp_port="+port+" local_address="+local_address);
    	service_.onSessionReady(id, device_id, device_name, device_address, port, local_address);
    }
    
    public void infoChanged(int streamId, String osVersion)
    {
    	long sessionId = streamId2sessionId(streamId);
        Common.Logging.i(TAG, "videoplayer.infoChanged():  streamId="+streamId+" sessionId="+sessionId+"  osVersion="+osVersion);
    	if (sessionId != INVALID_SESSION_ID)
    	{
    		VideoSession session = sessionMap.get(sessionId);
    		if ((session.osVersion == null) || !session.osVersion.equals(osVersion))
    		{
    			session.osVersion = osVersion;
    			if (session.osVersion.startsWith("10."))
    			{
    				service_.infoChanged(sessionId, AirMediaPlatforms.Windows, "Win 10", session.osVersion);
    			}
    		}
    	}
    }
    
    public void stopSession(long id)
    {
        Common.Logging.i(TAG, "VideoPlayer.stopSession  sessionId="+id);
        synchronized(stopSessionObjectLock) {
        	// See if prior session exists with the same id
        	VideoSession session = sessionMap.get(id);
        	if (session == null)
        	{
        		Common.Logging.w(TAG, "There is an no existing session with this id="+id+" was it stopped earlier?");
        		return;
        	}
         	if (session.state != AirMediaSessionStreamingState.Stopped)
        	{
        		streamCtrl_.stopWfdStream(session.streamId, id);
        		stateChanged(session.streamId, AirMediaSessionStreamingState.Stopped);
        	} else {
        		Common.Logging.i(TAG, "Session with this id="+id+" is already stopped");
        	}
			// Remove session from the map
    		sessionMap.remove(id);
        }
        Common.Logging.i(TAG, "VideoPlayer.stopSession for sessionId="+id+" exiting...");
    }
    
    public void stopSessionWithStreamId(int streamId)
    {
        Common.Logging.i(TAG, "VideoPlayer.stopSessionWithStreamId  streamId="+streamId);
        synchronized(stopSessionObjectLock) {
        	long sessionId =  streamId2sessionId(streamId);
        	if (sessionId != INVALID_SESSION_ID)
        		stopSession(sessionId);
        	else
        		Common.Logging.i(TAG, "Session for streamId " + streamId +" has invalid streamId");
        }
        Common.Logging.i(TAG, "VideoPlayer.stopSessionWithStreamId  streamId="+streamId+" exiting...");
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// EVENTS
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /// REMOTE CONNECTION

    private void handleRemoteException(Exception e) {
        Common.Logging.w(TAG, "videoplayer.exception.remote    EXCEPTION  " + e + "  " + Log.getStackTraceString(e));
    }

}

