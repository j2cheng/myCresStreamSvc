package com.crestron.airmedia.receiver.m360;

import com.crestron.airmedia.receiver.m360.IAirMediaSession;

import com.crestron.airmedia.receiver.m360.ipc.AirMediaSessionScreenPositionLayout;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSessionScreenPosition;

interface IAirMediaSessionManagerObserver {

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// EVENTS
    ////////////////////////////////////////////////////////////////////////////////////////////////

    oneway void onLayoutChanged(in AirMediaSessionScreenPositionLayout from, in AirMediaSessionScreenPositionLayout to);

    // EnumSet<AirMediaSessionScreenPosition> from = AirMediaSessionScreenPosition.set(from);
    // EnumSet<AirMediaSessionScreenPosition> to = AirMediaSessionScreenPosition.set(to);
    oneway void onOccupiedChanged(in int from, in int to);

    oneway void onAdded(in IAirMediaSession session);

    oneway void onRemoved(in IAirMediaSession session);
}
