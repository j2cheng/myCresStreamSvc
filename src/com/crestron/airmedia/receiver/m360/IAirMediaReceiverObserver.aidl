package com.crestron.airmedia.receiver.m360;

import com.crestron.airmedia.receiver.m360.ipc.AirMediaReceiverState;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaReceiverMirroringAssist;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaReceiverResolutionMode;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaReceiverLoadedState;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSize;

interface IAirMediaReceiverObserver {

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// EVENTS
    ////////////////////////////////////////////////////////////////////////////////////////////////

    oneway void onLoadedChanged(in AirMediaReceiverLoadedState from, in AirMediaReceiverLoadedState to);

    oneway void onProductChanged(in int from, in int to);

    oneway void onStateChanged(in AirMediaReceiverState from, in AirMediaReceiverState to);

    oneway void onMirroringAssistChanged(in AirMediaReceiverMirroringAssist from, in AirMediaReceiverMirroringAssist to);

    oneway void onAdapterAddressChanged(in String from, in String to);

    oneway void onServerNameChanged(in String from, in String to);

    oneway void onServerPasswordChanged(in String from, in String to);

    oneway void onBonjourChanged(in boolean to);

    oneway void onDisplayResolutionChanged(in AirMediaSize from, in AirMediaSize to);

    oneway void onMaxResolutionChanged(in AirMediaReceiverResolutionMode from, in AirMediaReceiverResolutionMode to);

    oneway void onForceCompatibilityChanged(in boolean to);

    oneway void onDebugModeChanged(in boolean to);
}
