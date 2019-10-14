package com.crestron.airmedia.receiver;

import android.view.Surface;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaPlatforms;

interface IVideoPlayerObserver {

    // id represents the current video session, this is set in the IVideoPlayer interface

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// EVENTS

    oneway void onStatusUpdate(in long id, in int code, in String reason);

    // value (enumeration): Stopped(0), Starting(1), Playing(2), Pausing(3), Paused(4), Stopping(5);
    oneway void onStateChanged(in long id, in int value);

    oneway void onResolutionChanged(in long id, in int width, in int height);

    oneway void onSessionReady(in long id, in String device_id, in String device_name, in String device_address, in int port);

    oneway void onInfoChanged(in long id, in AirMediaPlatforms platform, in String os, in String version);
}
