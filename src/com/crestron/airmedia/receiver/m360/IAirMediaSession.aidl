package com.crestron.airmedia.receiver.m360;

import android.graphics.Bitmap;
import android.view.Surface;

import com.crestron.airmedia.receiver.m360.IAirMediaReceiver;
import com.crestron.airmedia.receiver.m360.IAirMediaSessionObserver;

import com.crestron.airmedia.receiver.m360.ipc.AirMediaSessionInfo;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSessionConnectionState;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSessionStreamingState;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSessionScreenPosition;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSize;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSessionVideoType;

interface IAirMediaSession {

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// PROPERTIES
    ////////////////////////////////////////////////////////////////////////////////////////////////

    long getId();

    String getEndpoint();
    String getUsername();
    String[] getAddresses();

    /// INFO

    AirMediaSessionInfo getInfo();

    /// STATE

    AirMediaSessionConnectionState getConnection();
    AirMediaSessionStreamingState getStreaming();

    /// CHANNEL

    AirMediaSessionConnectionState getChannelState();
    long getChannelId();

    /// DEVICE

    AirMediaSessionConnectionState getDeviceState();
    String getDeviceId();

    /// VIDEO

    AirMediaSessionStreamingState getVideoState();
    int getVideoId();
    AirMediaSessionVideoType getVideoType();
    AirMediaSize getVideoResolution();
    int getVideoRotation();
    boolean getVideoIsDrm();
    boolean getVideoIsLoading();
    AirMediaSessionScreenPosition getVideoScreenPosition();
    void setVideoScreenPosition(in AirMediaSessionScreenPosition value);

    /// AUDIO

    AirMediaSessionStreamingState getAudioState();
    int getAudioId();
    boolean getAudioMuted();
    void setAudioMute(in boolean mute);
    float getAudioVolume();
    void setAudioVolume(in float value);

    /// PHOTO

    Bitmap getPhoto();

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// METHODS
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /// STATE

    void disconnect();

    /// TRANSPORTS

    void play();

    void pause();

    void stop();

    /// SURFACE

    void attach(in Surface value);
    void detach();

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// EVENTS
    ////////////////////////////////////////////////////////////////////////////////////////////////

    void register(IAirMediaSessionObserver observer);
    void unregister(IAirMediaSessionObserver observer);

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// PROPERTIES
    ////////////////////////////////////////////////////////////////////////////////////////////////

    boolean getRemoteAudioMuted();
    float getRemoteAudioVolume();
}
