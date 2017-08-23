package com.crestron.airmedia.receiver.m360;

import android.graphics.Bitmap;
import android.view.Surface;

import com.crestron.airmedia.receiver.m360.ipc.AirMediaSessionInfo;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSessionConnectionState;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSessionStreamingState;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSessionScreenPosition;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSize;

interface IAirMediaSessionObserver {

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// EVENTS
    ////////////////////////////////////////////////////////////////////////////////////////////////

    oneway void onUsernameChanged(in String from, in String to);

    oneway void onAddressesChanged(in String[] from, in String[] to);

    /// INFO

    oneway void onInfoChanged(in AirMediaSessionInfo from, in AirMediaSessionInfo to);

    /// STATE

    oneway void onConnectionChanged(in AirMediaSessionConnectionState from, in AirMediaSessionConnectionState to);

    oneway void onStreamingChanged(in AirMediaSessionStreamingState from, in AirMediaSessionStreamingState to);

    /// CHANNEL

    oneway void onChannelStateChanged(in AirMediaSessionConnectionState from, in AirMediaSessionConnectionState to);

    /// DEVICE

    oneway void onDeviceStateChanged(in AirMediaSessionConnectionState from, in AirMediaSessionConnectionState to);

    /// VIDEO

    oneway void onVideoStateChanged(in AirMediaSessionStreamingState from, in AirMediaSessionStreamingState to);
    oneway void onVideoResolutionChanged(in AirMediaSize from, in AirMediaSize to);
    oneway void onVideoRotationChanged(in int from, in int to);
    oneway void onVideoSurfaceChanged(in Surface from, in Surface to);
    oneway void onVideoDrmChanged(in boolean to);
    oneway void onVideoLoadingChanged(in boolean to);
    oneway void onVideoScreenPositionChanged(in AirMediaSessionScreenPosition from, in AirMediaSessionScreenPosition to);

    /// AUDIO

    oneway void onAudioStateChanged(in AirMediaSessionStreamingState from, in AirMediaSessionStreamingState to);
    oneway void onAudioMuteChanged(in boolean to);
    oneway void onAudioVolumeChanged(in float from, in float to);

    /// PHOTO

    oneway void onPhotoChanged(in Bitmap from, in Bitmap to);
}
