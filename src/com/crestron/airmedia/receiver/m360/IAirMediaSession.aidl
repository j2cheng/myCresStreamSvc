package com.crestron.airmedia.receiver.m360;

import android.graphics.Bitmap;
import android.view.Surface;

import com.crestron.airmedia.receiver.m360.IAirMediaReceiver;
import com.crestron.airmedia.receiver.m360.IAirMediaSessionObserver;

import com.crestron.airmedia.receiver.m360.ipc.AirMediaSessionConnectionState;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSessionStreamingState;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSessionScreenPosition;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSize;

interface IAirMediaSession {

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// PROPERTIES
    ////////////////////////////////////////////////////////////////////////////////////////////////

    long getId();

    String getEndpoint();
    String getUsername();
    String[] getAddresses();

    /// STATE

    AirMediaSessionConnectionState getConnection();
    AirMediaSessionStreamingState getStreaming();

    /// CHANNEL

    AirMediaSessionConnectionState getChannelState();
    int getChannelId();

    /// DEVICE

    AirMediaSessionConnectionState getDeviceState();
    String getDeviceId();

    /// VIDEO

    AirMediaSessionStreamingState getVideoState();
    int getVideoId();
    AirMediaSize getVideoResolution();
    int getVideoRotation();
    boolean getVideoIsDrm();
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
}
