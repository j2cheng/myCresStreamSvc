package com.crestron.airmedia.receiver.m360;

import com.crestron.airmedia.receiver.IVideoPlayer;

import com.crestron.airmedia.receiver.m360.IAirMediaSessionManager;
import com.crestron.airmedia.receiver.m360.IAirMediaReceiverObserver;

import com.crestron.airmedia.receiver.m360.ipc.AirMediaReceiverState;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaReceiverResolutionMode;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaReceiverMirroringAssist;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaReceiverLoadedState;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaReceiverVolume;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSize;

interface IAirMediaReceiver {

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// PROPERTIES
    ////////////////////////////////////////////////////////////////////////////////////////////////

    AirMediaReceiverLoadedState getLoadedState();

    String getProduct();
    void setProduct(in String name);

    String getVersion();

    IAirMediaSessionManager sessionManager();

    AirMediaReceiverState getState();

    String getAdapterAddress();
    void setAdapterAddress(in String address);

    String getServerVersion();

    String getServerName();
    void setServerName(in String name);

    String getServerPassword();
    void setServerPassword(in String password);

    boolean getBonjour();
    void setBonjour(in boolean enable);

    AirMediaSize getDisplayResolution();
    void setDisplayResolution(in AirMediaSize resolution);

    AirMediaReceiverResolutionMode getMaxResolution();
    void setMaxResolution(in AirMediaReceiverResolutionMode mode);

    boolean getForceCompatibility();
    void setForceCompatibility(in boolean enabled);

    boolean getDebugMode();
    void setDebugMode(in boolean enabled);

    boolean getProjectionLocked();
    void setProjectionLocked(in boolean locked);

    AirMediaReceiverMirroringAssist getMirroringAssist();

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// METHODS
    ////////////////////////////////////////////////////////////////////////////////////////////////

    void initialize();
    void close();

    void start();
    void stop();

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// EVENTS
    ////////////////////////////////////////////////////////////////////////////////////////////////

    void register(IAirMediaReceiverObserver observer);
    void unregister(IAirMediaReceiverObserver observer);

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// DEBUG
    ////////////////////////////////////////////////////////////////////////////////////////////////

    oneway void console(in String input);

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// PROPERTIES
    ////////////////////////////////////////////////////////////////////////////////////////////////

    boolean getVolumeSupported();
    void setVolumeSupported(in boolean enable);

    AirMediaReceiverVolume getVolumeProperties();
    void setVolumeProperties(in boolean enable, in float min, in float max, in float step);

    void addVideoPlayer(IVideoPlayer player);
    void removeVideoPlayer(IVideoPlayer player);
}
