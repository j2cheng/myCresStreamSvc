package com.crestron.airmedia.receiver.m360.models;

import android.graphics.Bitmap;
import android.os.RemoteException;
import android.util.Log;
import android.view.Surface;

import com.crestron.airmedia.receiver.m360.IAirMediaSession;
import com.crestron.airmedia.receiver.m360.IAirMediaSessionObserver;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSessionConnectionState;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSessionInfo;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSessionScreenPosition;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSessionStreamingState;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSessionVideoType;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSize;
import com.crestron.airmedia.utilities.Common;
import com.crestron.airmedia.utilities.TaskScheduler;
import com.crestron.airmedia.utilities.ViewBase;
import com.crestron.airmedia.utilities.delegates.MulticastChangedDelegate;
import com.crestron.airmedia.utilities.delegates.MulticastDelegate;
import com.crestron.airmedia.utilities.delegates.MulticastMessageDelegate;
import com.crestron.airmedia.utilities.delegates.Observer;
import com.crestron.airmedia.utilities.TimeSpan;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public class AirMediaSession extends AirMediaBase {
    private static final String TAG = "airmedia.session";

    public static ViewBase.Size from(AirMediaSize input) { return new ViewBase.Size(input.width, input.height); }
    public static AirMediaSize from(ViewBase.Size input) { return new AirMediaSize(input.Width, input.Height); }

    public static String toDebugString(AirMediaSession session) {
        return session == null ? "<none>" : "session[" + session.endpoint() + Common.Delimiter + "dev:" + session.deviceId() + Common.Delimiter + "ch:" + Long.toHexString(session.channelId()) + "]";
    }

    AirMediaSession(IAirMediaSession session, TaskScheduler scheduler) {
        super(scheduler);
        session_ = session;
        try {
            session.register(observer_);
            id_ = session.getId();
            miracastSessionId_ = session.getMiracastSessionId();
            endpoint_ = session.getEndpoint();
            addresses_ = Arrays.asList(session.getAddresses());
            username_ = session.getUsername();
            info_ = session.getInfo();
            connection_ = session.getConnection();
            streaming_ = session.getStreaming();
            channelState_ = session.getChannelState();
            channelId_ = session.getChannelId();
            deviceState_ = session.getDeviceState();
            deviceId_ = session.getDeviceId();
            videoState_ = session.getVideoState();
            videoId_ = session.getVideoId();
            videoType_ = session.getVideoType();
            videoResolution_ = session.getVideoResolution();
            videoRotation_ = session.getVideoRotation();
            videoIsDrm_ = session.getVideoIsDrm();
            videoIsLoading_ = session.getVideoIsLoading();
            videoScreenPosition_ = session.getVideoScreenPosition();
            audioState_ = AirMediaSessionStreamingState.Stopped;
            audioId_ = session.getAudioId();
            audioMuted_ = session.getAudioMuted();
            audioVolume_ = session.getAudioVolume();
            photo_ = session.getPhoto();
        } catch (RemoteException e) {

        }
    }

    private AirMediaSession self() { return this; }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// DEFINITIONS
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private final IAirMediaSessionObserver observer_ = new IAirMediaSessionObserver.Stub() {
        @Override
        public void onUsernameChanged(String from, String to) throws RemoteException {
            Common.Logging.v(TAG, "IAirMediaSessionObserver.onUsernameChanged  " + AirMediaSession.toDebugString(self()) + "  " + from + "  ==>  " + to);
            username_ = to;
            scheduler().raise(usernameChanged(), self(), from, to);
        }

        @Override
        public void onAddressesChanged(String[] from, String[] to) throws RemoteException {
            Common.Logging.v(TAG, "IAirMediaSessionObserver.onAddressesChanged  " + AirMediaSession.toDebugString(self()));
            synchronized (lock_) {
                addresses_ =  Arrays.asList(to);
            }
            scheduler().raise(addressesChanged(), self(), Arrays.asList(from), Arrays.asList(to));
        }

        @Override
        public void onInfoChanged(AirMediaSessionInfo from, AirMediaSessionInfo to) throws RemoteException {
            Common.Logging.v(TAG, "IAirMediaSessionObserver.onInfoChanged  " + AirMediaSession.toDebugString(self()) + "  " + from + "  ==>  " + to);
            info_ = to;
            scheduler().raise(infoChanged(), self(), from, to);
        }

        @Override
        public void onConnectionChanged(AirMediaSessionConnectionState from, AirMediaSessionConnectionState to) throws RemoteException {
            Common.Logging.v(TAG, "IAirMediaSessionObserver.onConnectionChanged  " + AirMediaSession.toDebugString(self()) + "  " + from + "  ==>  " + to);
            connection_ = to;
            scheduler().raise(connectionChanged(), self(), from, to);
        }

        @Override
        public void onStreamingChanged(AirMediaSessionStreamingState from, AirMediaSessionStreamingState to) throws RemoteException {
            Common.Logging.v(TAG, "IAirMediaSessionObserver.onStreamingChanged  " + AirMediaSession.toDebugString(self()) + "  " + from + "  ==>  " + to);
                streaming_ = to;
                scheduler().raise(streamingChanged(), self(), from, to);
        }

        @Override
        public void onChannelStateChanged(AirMediaSessionConnectionState from, AirMediaSessionConnectionState to) throws RemoteException {
            Common.Logging.v(TAG, "IAirMediaSessionObserver.onChannelStateChanged  " + AirMediaSession.toDebugString(self()) + "  " + from + "  ==>  " + to);
            channelState_ = to;
            if (to == AirMediaSessionConnectionState.Connecting || to == AirMediaSessionConnectionState.Connected) channelId_ = session_.getChannelId();
            scheduler().raise(channelStateChanged(), self(), from, to);
        }

        @Override
        public void onDeviceStateChanged(AirMediaSessionConnectionState from, AirMediaSessionConnectionState to) throws RemoteException {
            Common.Logging.v(TAG, "IAirMediaSessionObserver.onDeviceStateChanged  " + AirMediaSession.toDebugString(self()) + "  " + from + "  ==>  " + to);
            deviceState_ = to;
            if (to == AirMediaSessionConnectionState.Connecting || to == AirMediaSessionConnectionState.Connected) deviceId_ = session_.getDeviceId();
            scheduler().raise(deviceStateChanged(), self(), from, to);
        }

        private synchronized void updateVideoType(AirMediaSessionVideoType old, AirMediaSessionVideoType type) {
            if (old == type) return;
            Common.Logging.v(TAG, "IAirMediaSessionObserver.updateVideoType  " + AirMediaSession.toDebugString(self()) + "  " + old + "  ==>  " + type);
        }

        @Override
        public void onVideoStateChanged(AirMediaSessionStreamingState from, AirMediaSessionStreamingState to) throws RemoteException {
                Common.Logging.v(TAG, "IAirMediaSessionObserver.onVideoStateChanged  " + AirMediaSession.toDebugString(self()) + "  " + from + "  ==>  " + to);
                videoState_ = to;
                AirMediaSessionVideoType old = videoType_;
                videoType_ = session_.getVideoType();
                if (to == AirMediaSessionStreamingState.Starting || to == AirMediaSessionStreamingState.Playing) {
                    videoId_ = session_.getVideoId();
                }
                scheduler().raise(videoStateChanged(), self(), from, to);
                updateVideoType(old, videoType_);
        }

        public void onVideoTypeChanged(AirMediaSessionVideoType from, AirMediaSessionVideoType to) throws RemoteException {
            Common.Logging.v(TAG, "IAirMediaSessionObserver.onVideoTypeChanged  " + AirMediaSession.toDebugString(self()) + "  " + from + "  ==>  " + to);
            videoId_ = session_.getVideoId();
            videoType_ = to;
            updateVideoType(from, to);
        }

        @Override
        public void onVideoResolutionChanged(AirMediaSize from, AirMediaSize to) throws RemoteException {
            Common.Logging.v(TAG, "IAirMediaSessionObserver.onVideoResolutionChanged  " + AirMediaSession.toDebugString(self()) + "  " + from + "  ==>  " + to);
            videoResolution_ = to;
            scheduler().raise(videoResolutionChanged(), self(), from, to);
        }

        @Override
        public void onVideoRotationChanged(int from, int to) throws RemoteException {
            Common.Logging.v(TAG, "IAirMediaSessionObserver.onVideoRotationChanged  " + AirMediaSession.toDebugString(self()) + "  " + from + "  ==>  " + to);
            videoRotation_ = to;
            scheduler().raise(videoRotationChanged(), self(), from, to);
        }

        @Override
        public void onVideoSurfaceChanged(Surface from, Surface to) throws RemoteException {
            Common.Logging.v(TAG, "IAirMediaSessionObserver.onVideoSurfaceChanged  " + AirMediaSession.toDebugString(self()) + "  " + from + "  ==>  " + to);
            videoSurface_ = to;
            scheduler().raise(videoSurfaceChanged(), self(), from, to);
        }

        @Override
        public void onVideoDrmChanged(boolean to) throws RemoteException {
            Common.Logging.v(TAG, "IAirMediaSessionObserver.onVideoDrmChanged  " + AirMediaSession.toDebugString(self()) + "  " + !to + "  ==>  " + to);
            videoIsDrm_ = to;
            scheduler().raise(videoDrmChanged(), self(), to);
        }

        @Override
        public void onVideoLoadingChanged(boolean to) throws RemoteException {
            Common.Logging.v(TAG, "IAirMediaSessionObserver.onVideoLoadingChanged  " + AirMediaSession.toDebugString(self()) + "  " + !to + "  ==>  " + to);
            videoIsLoading_ = to;
            scheduler().raise(videoLoadingChanged(), self(), to);
        }

        @Override
        public void onVideoScreenPositionChanged(AirMediaSessionScreenPosition from, AirMediaSessionScreenPosition to) throws RemoteException {
            Common.Logging.v(TAG, "IAirMediaSessionObserver.onVideoScreenPositionChanged  " + AirMediaSession.toDebugString(self()) + "  " + from + "  ==>  " + to);
            videoScreenPosition_ = to;
            scheduler().raise(videoScreenPositionChanged(), self(), from, to);
        }

        @Override
        public int onVideoSurfaceRenewRequest() throws RemoteException {
            Common.Logging.v(TAG, "IAirMediaSessionObserver.onVideoSurfaceRenewRequest  " + AirMediaSession.toDebugString(self()));
            scheduler().raise(videoSurfaceRenewRequest(), self());
            return -2;
        }
        
        @Override
        public void onAudioStateChanged(AirMediaSessionStreamingState from, AirMediaSessionStreamingState to) throws RemoteException {
            Common.Logging.v(TAG, "IAirMediaSessionObserver.onAudioStateChanged  " + AirMediaSession.toDebugString(self()) + "  " + from + "  ==>  " + to);
            audioState_ = to;
            if (to == AirMediaSessionStreamingState.Starting || to == AirMediaSessionStreamingState.Playing) audioId_ = session_.getAudioId();
            scheduler().raise(audioStateChanged(), self(), from, to);
        }

        @Override
        public void onAudioMuteChanged(boolean to) throws RemoteException {
            Common.Logging.v(TAG, "IAirMediaSessionObserver.onAudioMuteChanged  " + AirMediaSession.toDebugString(self()) + "  " + !to + "  ==>  " + to);
            audioMuted_ = to;
            scheduler().raise(audioMuteChanged(), self(), to);
        }

        @Override
        public void onAudioVolumeChanged(float from, float to) throws RemoteException {
            Common.Logging.v(TAG, "IAirMediaSessionObserver.onAudioVolumeChanged  " + AirMediaSession.toDebugString(self()) + "  " + from + "  ==>  " + to);
            audioVolume_ = to;
            scheduler().raise(audioVolumeChanged(), self(), from, to);
        }

        @Override
        public void onPhotoChanged(Bitmap from, Bitmap to) throws RemoteException {
            Common.Logging.v(TAG, "IAirMediaSessionObserver.onPhotoChanged  " + AirMediaSession.toDebugString(self()) + "  " + from + "  ==>  " + to);
            photo_ = to;
            scheduler().raise(photoChanged(), self(), from, to);
        }

        @Override
        public void onRemoteAudioMuteChanged(boolean to) throws RemoteException {
            Common.Logging.v(TAG, "IAirMediaSessionObserver.onRemoteAudioMuteChanged  " + AirMediaSession.toDebugString(self()) + "  " + !to + "  ==>  " + to);
        }

        @Override
        public void onRemoteAudioVolumeChanged(float from, float to) throws RemoteException {
            Common.Logging.v(TAG, "IAirMediaSessionObserver.onRemoteAudioVolumeChanged  " + AirMediaSession.toDebugString(self()) + "  " + from + "  ==>  " + to);
        }
    };

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// FIELDS
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private final Object lock_ = new Object();

    private long id_ = 0;
    private long miracastSessionId_ = 0;

    private IAirMediaSession session_;
    private String endpoint_;
    private List<String> addresses_;

    private String username_;
    private AirMediaSessionInfo info_;

    private AirMediaSessionConnectionState connection_ = AirMediaSessionConnectionState.Disconnected;

    private AirMediaSessionStreamingState streaming_ = AirMediaSessionStreamingState.Stopped;

    private AirMediaSessionConnectionState channelState_ = AirMediaSessionConnectionState.Disconnected;
    private long channelId_ = 0;

    private AirMediaSessionConnectionState deviceState_ = AirMediaSessionConnectionState.Disconnected;
    private String deviceId_;

    private AirMediaSessionStreamingState videoState_ = AirMediaSessionStreamingState.Stopped;
    private AirMediaSessionVideoType videoType_ = AirMediaSessionVideoType.Undefined;
    private int videoId_ = 0;
    private AirMediaSize videoResolution_ = AirMediaSize.Zero;
    private int videoRotation_ = 0;
    private boolean videoIsDrm_ = false;
    private boolean videoIsLoading_ = false;
    private AirMediaSessionScreenPosition videoScreenPosition_ = AirMediaSessionScreenPosition.None;
    private Surface videoSurface_;

    private AirMediaSessionStreamingState audioState_ = AirMediaSessionStreamingState.Stopped;
    private int audioId_ = 0;
    private boolean audioMuted_ = false;
    private float audioVolume_ = 0.0f;

    private Bitmap photo_ = null;

    /// EVENTS

    private final MulticastChangedDelegate<AirMediaSession, String> usernameChanged_ = new MulticastChangedDelegate<AirMediaSession, String>();
    private final MulticastChangedDelegate<AirMediaSession, Collection<String>> addressesChanged_ = new MulticastChangedDelegate<AirMediaSession, Collection<String>>();
    private final MulticastChangedDelegate<AirMediaSession, AirMediaSessionInfo> infoChanged_ = new MulticastChangedDelegate<AirMediaSession, AirMediaSessionInfo>();

    private final MulticastChangedDelegate<AirMediaSession, AirMediaSessionConnectionState> connectionChanged_ = new MulticastChangedDelegate<AirMediaSession, AirMediaSessionConnectionState>();
    private final MulticastChangedDelegate<AirMediaSession, AirMediaSessionStreamingState> streamingChanged_ = new MulticastChangedDelegate<AirMediaSession, AirMediaSessionStreamingState>();

    private final MulticastChangedDelegate<AirMediaSession, AirMediaSessionConnectionState> channelStateChanged_ = new MulticastChangedDelegate<AirMediaSession, AirMediaSessionConnectionState>();

    private final MulticastChangedDelegate<AirMediaSession, AirMediaSessionConnectionState> deviceStateChanged_ = new MulticastChangedDelegate<AirMediaSession, AirMediaSessionConnectionState>();

    private final MulticastChangedDelegate<AirMediaSession, AirMediaSessionStreamingState> videoStateChanged_ = new MulticastChangedDelegate<AirMediaSession, AirMediaSessionStreamingState>();
    private final MulticastChangedDelegate<AirMediaSession, AirMediaSessionVideoType> videoTypeChanged_ = new MulticastChangedDelegate<AirMediaSession, AirMediaSessionVideoType>();
    private final MulticastChangedDelegate<AirMediaSession, AirMediaSize> videoResolutionChanged_ = new MulticastChangedDelegate<AirMediaSession, AirMediaSize>();
    private final MulticastChangedDelegate<AirMediaSession, Integer> videoRotationChanged_ = new MulticastChangedDelegate<AirMediaSession, Integer>();
    private final MulticastChangedDelegate<AirMediaSession, Surface> videoSurfaceChanged_ = new MulticastChangedDelegate<AirMediaSession, Surface>();
    private final MulticastMessageDelegate<AirMediaSession, Boolean> videoDrmChanged_ = new MulticastMessageDelegate<AirMediaSession, Boolean>();
    private final MulticastMessageDelegate<AirMediaSession, Boolean> videoLoadingChanged_ = new MulticastMessageDelegate<AirMediaSession, Boolean>();
    private final MulticastChangedDelegate<AirMediaSession, AirMediaSessionScreenPosition> videoScreenPositionChanged_ = new MulticastChangedDelegate<AirMediaSession, AirMediaSessionScreenPosition>();
    private final MulticastDelegate<AirMediaSession> videoSurfaceRenewRequest_ = new MulticastDelegate<AirMediaSession>();

    private final MulticastChangedDelegate<AirMediaSession, AirMediaSessionStreamingState> audioStateChanged_ = new MulticastChangedDelegate<AirMediaSession, AirMediaSessionStreamingState>();
    private final MulticastMessageDelegate<AirMediaSession, Boolean> audioMuteChanged_ = new MulticastMessageDelegate<AirMediaSession, Boolean>();
    private final MulticastChangedDelegate<AirMediaSession, Float> audioVolumeChanged_ = new MulticastChangedDelegate<AirMediaSession, Float>();

    private final MulticastChangedDelegate<AirMediaSession, Bitmap> photoChanged_ = new MulticastChangedDelegate<AirMediaSession, Bitmap>();

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// PROPERTIES
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public long id() { return id_; }
    
    /// MIRACAST SESSION ID
 
    public long miracastSessionId() { return miracastSessionId_; }


    /// ENDPOINT

    public String endpoint() { return endpoint_; }

    /// ADDRESSES

    public Collection<String> addresses() { synchronized (lock_) { return new LinkedList<String>(addresses_); } }

    /// USERNAME

    public String username() { return username_; }

    /// SENDER INFO

    public AirMediaSessionInfo info() { return info_; }

    /// CONNECTION

    public AirMediaSessionConnectionState connection() { return connection_; }

    /// STREAMING

    public AirMediaSessionStreamingState streaming() { return streaming_; }

    /// CHANNEL

    public AirMediaSessionConnectionState channelState() { return channelState_; }

    public long channelId() { return channelId_; }

    /// DEVICE

    public AirMediaSessionConnectionState deviceState() { return deviceState_; }

    public String deviceId() { return deviceId_; }

    /// VIDEO STATE

    public AirMediaSessionStreamingState videoState() { return videoState_; }

    public int videoId() { return videoId_; }

    /// VIDEO TYPE

    public AirMediaSessionVideoType videoType() { return videoType_; }

    /// VIDEO RESOLUTION

    public AirMediaSize videoResolution() { return videoResolution_; }

    /// VIDEO ROTATION

    public int videoRotation() { return videoRotation_; }

    /// VIDEO DRM STATUS

    public boolean videoIsDrm() { return videoIsDrm_; }

    /// VIDEO LOADING STATUS

    public boolean videoIsLoading() { return videoIsLoading_; }

    /// VIDEO SCREEN POSITION

    public AirMediaSessionScreenPosition videoScreenPosition() { return videoScreenPosition_; }

    public void videoScreenPosition(AirMediaSessionScreenPosition value) {
        scheduler().update(new TaskScheduler.PropertyUpdater<AirMediaSessionScreenPosition>() { @Override public void update(AirMediaSessionScreenPosition v) { updateVideoScreenPosition(v); } }, value);
    }

    /// VIDEO SURFACE

    public Surface videoSurface() { return videoSurface_; }

    /// AUDIO STATE

    public AirMediaSessionStreamingState audioState() { return audioState_; }

    public int audioId() { return audioId_; }

    /// AUDIO MUTE

    public boolean audioMuted() { return audioMuted_; }

    public void audioMute(boolean mute) {
        scheduler().update(new TaskScheduler.PropertyUpdater<Boolean>() { @Override public void update(Boolean v) { updateAudioMute(v); } }, mute);
    }

    /// AUDIO VOLUME

    public float audioVolume() { return audioVolume_; }

    public void audioVolume(float value){
        scheduler().update(new TaskScheduler.PropertyUpdater<Float>() { @Override public void update(Float v) { updateAudioVolume(v); } }, Common.limit(value, 0.0f, 1.0f));
    }

    /// PHOTO

    public Bitmap photo() { return photo_; }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// EVENTS
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /// ADDRESSES

    public MulticastChangedDelegate<AirMediaSession, Collection<String>> addressesChanged() { return addressesChanged_; }

    /// USERNAME

    public MulticastChangedDelegate<AirMediaSession, String> usernameChanged() { return usernameChanged_; }

    /// SENDER INFO

    public MulticastChangedDelegate<AirMediaSession, AirMediaSessionInfo> infoChanged() { return infoChanged_; }

    /// STATE

    public MulticastChangedDelegate<AirMediaSession, AirMediaSessionConnectionState> connectionChanged() { return connectionChanged_; }
    public MulticastChangedDelegate<AirMediaSession, AirMediaSessionStreamingState> streamingChanged() { return streamingChanged_; }

    /// CHANNEL

    public MulticastChangedDelegate<AirMediaSession, AirMediaSessionConnectionState> channelStateChanged() { return channelStateChanged_; }

    /// DEVICE

    public MulticastChangedDelegate<AirMediaSession, AirMediaSessionConnectionState> deviceStateChanged() { return deviceStateChanged_; }

    /// VIDEO

    public MulticastChangedDelegate<AirMediaSession, AirMediaSessionStreamingState> videoStateChanged() { return videoStateChanged_; }
    public MulticastChangedDelegate<AirMediaSession, AirMediaSessionVideoType> videoTypeChanged() { return videoTypeChanged_; }
    public MulticastChangedDelegate<AirMediaSession, AirMediaSize> videoResolutionChanged() { return videoResolutionChanged_; }
    public MulticastChangedDelegate<AirMediaSession, Integer> videoRotationChanged() { return videoRotationChanged_ ; }
    public MulticastChangedDelegate<AirMediaSession, Surface> videoSurfaceChanged() { return videoSurfaceChanged_ ; }
    public MulticastMessageDelegate<AirMediaSession, Boolean> videoDrmChanged() { return videoDrmChanged_; }
    public MulticastMessageDelegate<AirMediaSession, Boolean> videoLoadingChanged() { return videoLoadingChanged_; }
    public MulticastChangedDelegate<AirMediaSession, AirMediaSessionScreenPosition> videoScreenPositionChanged() { return videoScreenPositionChanged_; }
    public MulticastDelegate<AirMediaSession> videoSurfaceRenewRequest() { return videoSurfaceRenewRequest_; }

    /// AUDIO

    public MulticastChangedDelegate<AirMediaSession, AirMediaSessionStreamingState> audioStateChanged() { return audioStateChanged_; }
    public MulticastMessageDelegate<AirMediaSession, Boolean> audioMuteChanged() { return audioMuteChanged_; }
    public MulticastChangedDelegate<AirMediaSession, Float> audioVolumeChanged() { return audioVolumeChanged_; }

    /// PHOTO

    public MulticastChangedDelegate<AirMediaSession, Bitmap> photoChanged() { return photoChanged_; }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// METHODS
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /// DISCONNECT

    public void disconnect() { disconnect(DefaultTimeout); }

    public void disconnect(TimeSpan timeout) {
        queue("session.disconnect", timeout, new Runnable() { @Override public void run() { disconnectTask(null); } });
    }

    public void disconnect(Observer<AirMediaSession> observer) {
        queue(this, "session.disconnect", observer, new TaskScheduler.ObservableTask<AirMediaSession>() { @Override public void run(Observer<AirMediaSession> value) { disconnectTask(value); } });
    }

    /// PLAY

    public void play() { play(DefaultTimeout); }

    public void play(TimeSpan timeout) {
        queue("session.play", timeout, new Runnable() { @Override public void run() { playTask(null); } });
    }

    public void play(Observer<AirMediaSession> observer) {
        queue(this, "session.play", observer, new TaskScheduler.ObservableTask<AirMediaSession>() { @Override public void run(Observer<AirMediaSession> value) { playTask(value); } });
    }

    /// PAUSE

    public void pause() { pause(DefaultTimeout); }

    public void pause(TimeSpan timeout) {
        queue("session.pause", timeout, new Runnable() { @Override public void run() { pauseTask(null); } });
    }

    public void pause(Observer<AirMediaSession> observer) {
        queue(this, "session.pause", observer, new TaskScheduler.ObservableTask<AirMediaSession>() { @Override public void run(Observer<AirMediaSession> value) { pauseTask(value); } });
    }

    /// STOP

    public void stop() { stop(DefaultTimeout); }

    public void stop(TimeSpan timeout) {
        queue("session.stop", timeout, new Runnable() { @Override public void run() { stopTask(null); } });
    }

    public void stop(Observer<AirMediaSession> observer) {
        queue(this, "session.stop", observer, new TaskScheduler.ObservableTask<AirMediaSession>() { @Override public void run(Observer<AirMediaSession> value) { stopTask(value); } });
    }

    /// SURFACE

    public void attach(Surface value) {
        try {
            IAirMediaSession session = session_;
            if (session == null) return;
            session.attach(value);
        } catch (RemoteException e) {
            Common.Logging.e(TAG, "session.video.surface.attach  " + toDebugString(self()) + "  value= " + value + "  EXCEPTION  " + e);
            handleRemoteException(e);
        }
    }

    public void detach() {
        try {
            IAirMediaSession session = session_;
            if (session == null) return;
            session.detach();
        } catch (RemoteException e) {
            Common.Logging.e(TAG, "session.video.surface.detach  " + toDebugString(self()) + "  EXCEPTION  " + e);
            handleRemoteException(e);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// INTERNAL
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private void disconnectTask(Observer<AirMediaSession> observer) {
        try {
            session_.disconnect();
            scheduler().raise(observer, this);
        } catch (RemoteException e) {
            handleRemoteException(e);
            scheduler().raiseError(observer, this, "AirMedia.Session", -1006, "task.disconnect  REMOTE EXCEPTION  " + e);
        } catch (Exception e) {
            scheduler().raiseError(observer, this, "AirMedia.Session", -1006, "task.disconnect  EXCEPTION  " + e);
        }
    }

    private void playTask(Observer<AirMediaSession> observer) {
        try {
            session_.play();
            scheduler().raise(observer, this);
        } catch (RemoteException e) {
            handleRemoteException(e);
            scheduler().raiseError(observer, this, "AirMedia.Session", -1007, "task.play  REMOTE EXCEPTION  " + e);
        } catch (Exception e) {
            scheduler().raiseError(observer, this, "AirMedia.Session", -1007, "task.play  EXCEPTION  " + e);
        }
    }

    private void pauseTask(Observer<AirMediaSession> observer) {
        try {
            session_.pause();
            scheduler().raise(observer, this);
        } catch (RemoteException e) {
            handleRemoteException(e);
            scheduler().raiseError(observer, this, "AirMedia.Session", -1008, "task.pause  REMOTE EXCEPTION  " + e);
        } catch (Exception e) {
            scheduler().raiseError(observer, this, "AirMedia.Session", -1008, "task.pause  EXCEPTION  " + e);
        }
    }

    private void stopTask(Observer<AirMediaSession> observer) {
        try {
            session_.stop();
            scheduler().raise(observer, this);
        } catch (RemoteException e) {
            handleRemoteException(e);
            scheduler().raiseError(observer, this, "AirMedia.Session", -1009, "task.stop  REMOTE EXCEPTION  " + e);
        } catch (Exception e) {
            scheduler().raiseError(observer, this, "AirMedia.Session", -1009, "task.stop  EXCEPTION  " + e);
        }
    }

    /// VIDEO SCREEN POSITION

    private void updateVideoScreenPosition(AirMediaSessionScreenPosition value) {
        try {
            IAirMediaSession session = session_;
            if (session == null) return;
            session.setVideoScreenPosition(value);
        } catch (RemoteException e) {
            Common.Logging.e(TAG, "session.video.screen-position  " + toDebugString(self()) + "  value= " + value + "  EXCEPTION  " + e);
            handleRemoteException(e);
        }
    }

    /// AUDIO MUTE

    private void updateAudioMute(boolean value) {
        try {
            IAirMediaSession session = session_;
            if (session == null) return;
            session.setAudioMute(value);
        } catch (RemoteException e) {
            Common.Logging.e(TAG, "session.audio.mute  " + toDebugString(self()) + "  value= " + value + "  EXCEPTION  " + e);
            handleRemoteException(e);
        }
    }

    /// AUDIO VOLUME

    private void updateAudioVolume(float value) {
        try {
            IAirMediaSession session = session_;
            if (session == null) return;
            session.setAudioVolume(value);
        } catch (RemoteException e) {
            Common.Logging.e(TAG, "session.audio.volume  " + toDebugString(self()) + "  value= " + value + "  EXCEPTION  " + e);
            handleRemoteException(e);
        }
    }

    /// REMOTE CONNECTION

    private void handleRemoteException(Exception e) {
        Common.Logging.v(TAG, "session.exception.remote  " + toDebugString(self()) + "  EXCEPTION  " + e + "  " + Log.getStackTraceString(e));
        session_ = null;
    }

    /// EQUALITY

    public static boolean isEqual(AirMediaSession lhs, AirMediaSession rhs) {
        return lhs == rhs || !(lhs == null || rhs == null) && lhs.id() == rhs.id();
    }

    public static boolean isEqual(AirMediaSession lhs, IAirMediaSession rhs) {
        return !(lhs == null || rhs == null) && lhs.id() == safelyGetId(rhs);
    }

    private static long safelyGetId(IAirMediaSession session) {
        try {
            return session.getId();
        } catch (Exception ignore) {
            // ignore
        }
        return 0;
    }
}

