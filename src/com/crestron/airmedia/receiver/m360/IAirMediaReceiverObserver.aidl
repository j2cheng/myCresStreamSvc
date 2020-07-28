package com.crestron.airmedia.receiver.m360;

import com.crestron.airmedia.receiver.m360.ipc.AirMediaReceiverState;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaReceiverMirroringAssist;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaReceiverResolutionMode;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaReceiverLoadedState;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSize;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaReceiverVolume;

interface IAirMediaReceiverObserver {

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// EVENTS
    ////////////////////////////////////////////////////////////////////////////////////////////////

    void onLoadedChanged(in AirMediaReceiverLoadedState from, in AirMediaReceiverLoadedState to);

    void onProductChanged(in String from, in String to);

    void onStateChanged(in AirMediaReceiverState from, in AirMediaReceiverState to, in int reason);

    void onMirroringAssistChanged(in AirMediaReceiverMirroringAssist from, in AirMediaReceiverMirroringAssist to);

    void onAdapterAddressChanged(in String from, in String to);

    void onServerNameChanged(in String from, in String to);

    void onServerPasswordChanged(in String from, in String to);

    void onBonjourChanged(in boolean to);

    void onDisplayResolutionChanged(in AirMediaSize from, in AirMediaSize to);

    void onMaxResolutionChanged(in AirMediaReceiverResolutionMode from, in AirMediaReceiverResolutionMode to);

    void onForceCompatibilityChanged(in boolean to);

    void onDebugModeChanged(in boolean to);

    void onProjectionLockedChanged(in boolean to);

    void onVolumeSupportChanged(in boolean to);

    void onVolumePropertiesChanged(in AirMediaReceiverVolume from, in AirMediaReceiverVolume to);
    
    void onWiFiApUsersCountChanged(in int from, in int to);
}
