package com.crestron.airmedia.receiver;

import android.view.Surface;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaPlatforms;

interface IVideoPlayerObserver {

    // id represents the current video session, this is set in the IVideoPlayer interface

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// EVENTS

    void onStatusUpdate(in long id, in int code, in String reason);

    // value (enumeration): Stopped(0), Starting(1), Playing(2), Pausing(3), Paused(4), Stopping(5);
    void onStateChanged(in long id, in int value);

    void onResolutionChanged(in long id, in int width, in int height);

    void onSessionReady(in long id, in String device_id, in String device_name, in String device_address, in int port);

    void onInfoChanged(in long id, in AirMediaPlatforms platform, in String os, in String version);
}
