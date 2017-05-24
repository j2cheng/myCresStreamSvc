package com.crestron.airmedia.receiver.m360;

//import java.util.EnumSet;

import com.crestron.airmedia.receiver.m360.IAirMediaReceiver;
import com.crestron.airmedia.receiver.m360.IAirMediaSession;
import com.crestron.airmedia.receiver.m360.IAirMediaSessionManagerObserver;

import com.crestron.airmedia.receiver.m360.ipc.AirMediaSessionScreenPositionLayout;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSessionScreenPosition;

interface IAirMediaSessionManager {

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// PROPERTIES
    ////////////////////////////////////////////////////////////////////////////////////////////////

    AirMediaSessionScreenPositionLayout getLayout();

    int getOccupied(); // EnumSet<AirMediaSessionScreenPosition> occupied = AirMediaSessionScreenPosition.set(getOccupied());
    //EnumSet<AirMediaSessionScreenPosition> getOccupied();

    boolean isEmpty();

    boolean isNotEmpty();

    int getCount();

    IAirMediaSession getSession(in int index);
    //void getSessions(out IAirMediaSession[] sessions);
    //IAirMediaSession[] getSessions();

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// METHODS
    ////////////////////////////////////////////////////////////////////////////////////////////////

    void close();

    void clear();

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// EVENTS
    ////////////////////////////////////////////////////////////////////////////////////////////////

    void register(in IAirMediaSessionManagerObserver observer);
    void unregister(in IAirMediaSessionManagerObserver observer);
}
