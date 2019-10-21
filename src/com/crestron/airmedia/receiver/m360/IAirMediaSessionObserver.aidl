package com.crestron.airmedia.receiver.m360;

import android.graphics.Bitmap;
import android.view.Surface;

import com.crestron.airmedia.receiver.m360.ipc.AirMediaSessionInfo;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSessionConnectionState;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSessionStreamingState;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSessionScreenPosition;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSize;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSessionVideoType;

interface IAirMediaSessionObserver {

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// EVENTS
    ////////////////////////////////////////////////////////////////////////////////////////////////

    void onUsernameChanged(in String from, in String to);

    void onAddressesChanged(in String[] from, in String[] to);

    /// INFO

    void onInfoChanged(in AirMediaSessionInfo from, in AirMediaSessionInfo to);

    /// STATE

    void onConnectionChanged(in AirMediaSessionConnectionState from, in AirMediaSessionConnectionState to);

    void onStreamingChanged(in AirMediaSessionStreamingState from, in AirMediaSessionStreamingState to);

    /// CHANNEL

    void onChannelStateChanged(in AirMediaSessionConnectionState from, in AirMediaSessionConnectionState to);

    /// DEVICE

    void onDeviceStateChanged(in AirMediaSessionConnectionState from, in AirMediaSessionConnectionState to);

    /// VIDEO

    void onVideoStateChanged(in AirMediaSessionStreamingState from, in AirMediaSessionStreamingState to);
    void onVideoTypeChanged(in AirMediaSessionVideoType from, in AirMediaSessionVideoType to);
    void onVideoResolutionChanged(in AirMediaSize from, in AirMediaSize to);
    void onVideoRotationChanged(in int from, in int to);
    void onVideoSurfaceChanged(in Surface from, in Surface to);
    void onVideoDrmChanged(in boolean to);
    void onVideoLoadingChanged(in boolean to);
    void onVideoScreenPositionChanged(in AirMediaSessionScreenPosition from, in AirMediaSessionScreenPosition to);

    /// AUDIO

    void onAudioStateChanged(in AirMediaSessionStreamingState from, in AirMediaSessionStreamingState to);
    void onAudioMuteChanged(in boolean to);
    void onAudioVolumeChanged(in float from, in float to);

    /// PHOTO

    void onPhotoChanged(in Bitmap from, in Bitmap to);

    /// REMOTE AUDIO

    void onRemoteAudioMuteChanged(in boolean to);
    void onRemoteAudioVolumeChanged(in float from, in float to);
}
