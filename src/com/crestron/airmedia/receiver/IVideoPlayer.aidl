package com.crestron.airmedia.receiver;

import android.view.Surface;
import com.crestron.airmedia.receiver.IVideoPlayerObserver;

interface IVideoPlayer {

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // NOTES:
    // - id represents the current video session, which is set when start is called

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// PROPERTIES

    // value (enumeration): Stopped(0), Starting(1), Playing(2), Pausing(3), Paused(4), Stopping(5);
    int getVideoState(in long id);

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// METHODS

    // start the video player for the given session ID
    void start(in long id, in String endpoint, in int port, in Surface surface, in String local_address);

    // start the video player for the given session ID
    void startWithDtls(in long id, in String endpoint, in int port, in Surface surface, in String key, in int cipher, in int authentication);

    // stop the video player for the given session ID
    void stop(in long id);

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// EVENTS

    void register(IVideoPlayerObserver observer);
    void unregister(IVideoPlayerObserver observer);

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// PROPERTIES
    
    void setAdapterAddress(in String address);
    void setPasscode(in String code);
    
    void setAdapterAddresses(in List<String> address);
    
    void setAudioMute(in long id, in boolean mute);

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// METHODS

    // disconnect the session for the given session ID
    //void disconnect(in long id);
}
