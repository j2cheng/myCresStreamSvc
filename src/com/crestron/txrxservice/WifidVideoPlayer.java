package com.crestron.txrxservice;

import android.graphics.Bitmap;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.Surface;

import com.crestron.airmedia.receiver.IVideoPlayer;
import com.crestron.airmedia.receiver.IVideoPlayerObserver;
import com.crestron.airmedia.receiver.m360.models.AirMediaReceiver;
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
    
    WifidVideoPlayer(CresStreamCtrl streamCtrl) {
        streamCtrl_ = streamCtrl;
    }
    
    private WifidVideoPlayer self() { return this; }

    public void register(AirMediaReceiver receiver)
    {
    	receiver_ = receiver;
    	// register this class with the receiver as well
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
        
        @Override
        public void register(IVideoPlayerObserver observer) throws RemoteException {
            add(observer);
        }

        @Override
        public void unregister(IVideoPlayerObserver observer) throws RemoteException {
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
        ////////////////////////////////////////////////////////////////////////////////////////////////
        /// METHODS
        ////////////////////////////////////////////////////////////////////////////////////////////////

        @Override
        public int getVideoState(long id) {
            VideoSession session = sessionMap.get(id);
        	return (session == null) ? 0 : session.state.value;
        }

        /// base _start function
        public void _start(long id, String endpoint, int port, Surface surface, 
        		String srtpCipher, String srtpAuthentication, String srtcpCipher, String srtcpAuthentication)
        {
            Common.Logging.i(TAG, "VideoPlayer.start  id="+id+"  url="+endpoint+"  port="+port+"   Surface="+surface);
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
            session = new VideoSession(id, streamId, surface, AirMediaSessionStreamingState.Stopped);
            // Start the video player
			// Put session into the map
            streamCtrl_.startWfdStream(streamId, endpoint, port, srtpCipher, srtpAuthentication, srtcpCipher, srtcpAuthentication);
            sessionMap.put(id, session);
        	stateChanged(id, AirMediaSessionStreamingState.Starting);
        }
        
        /// start
        @Override
        public void start(long id, String endpoint, int port, Surface surface)
        {
        	_start(id, endpoint, port, surface, null, null, null, null);
        }
        
        // start the video player for the given session ID
        @Override
        public void startWithDtls(long id, String endpoint, int port, Surface surface, 
        		String srtpCipher, String srtpAuthentication, String srtcpCipher, String srtcpAuthentication)
        {
        	_start(id, endpoint, port, surface, srtpCipher, srtpAuthentication, srtcpCipher, srtcpAuthentication);
        }
        
        @Override
        public void stop(long id)
        {
            Common.Logging.i(TAG, "VideoPlayer.stop  id="+id);
			// See if prior session exists with the same id
            VideoSession session = sessionMap.get(id);
            if (session == null)
            {
            	Common.Logging.w(TAG, "There is an no existing session with this id="+id);
            	return;
            }
            if (session.state != AirMediaSessionStreamingState.Stopped)
            {
            	stateChanged(id, AirMediaSessionStreamingState.Stopping);
            	streamCtrl_.stopWfdStream(session.streamId);
            	stateChanged(id, AirMediaSessionStreamingState.Stopped);
            }
			// Remove session from the map
    		sessionMap.remove(id);
        }
    }

    private class VideoSession {
    	public long sessionId;
    	public AirMediaSessionStreamingState state;
    	public Surface surface;
    	public int streamId;
    	
    	public VideoSession(long id, int streamIdx, Surface s, AirMediaSessionStreamingState curState)
    	{
    		sessionId = id;
    		streamId = streamIdx;
    		surface = s;
    		state = curState;
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

    /// EVENTS

    public void statusChanged(long id, int code, String reason)
    {
    	service_.statusChanged(id, code, reason);
    }
    
    public void resolutionChanged(long id, int width, int height)
    {
    	service_.resolutionChanged(id, width, height);
    }
    
    public void stateChanged(int streamId, AirMediaSessionStreamingState value)
    {
    	long sessionId = streamId2sessionId(streamId);
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

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// EVENTS
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /// REMOTE CONNECTION

    private void handleRemoteException(Exception e) {
        Common.Logging.w(TAG, "videoplayer.exception.remote    EXCEPTION  " + e + "  " + Log.getStackTraceString(e));
    }

}

