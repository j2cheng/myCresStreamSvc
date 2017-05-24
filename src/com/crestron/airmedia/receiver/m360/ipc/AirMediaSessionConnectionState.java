package com.crestron.airmedia.receiver.m360.ipc;

import android.os.Parcel;
import android.os.Parcelable;

public enum AirMediaSessionConnectionState implements Parcelable {
    Disconnected(0), Connecting(1), Connected(2), Disconnecting(3);
    public final int value;

    AirMediaSessionConnectionState(int v) { value = v; }

    @Override
    public void writeToParcel(Parcel dest, int flags) { dest.writeInt(value); }

    @Override
    public int describeContents() { return 0; }

    public static final Creator<AirMediaSessionConnectionState> CREATOR = new Creator<AirMediaSessionConnectionState>() {
        @Override
        public AirMediaSessionConnectionState createFromParcel(Parcel in) { return AirMediaSessionConnectionState.from(in); }

        @Override
        public AirMediaSessionConnectionState[] newArray(int size) { return new AirMediaSessionConnectionState[size]; }
    };

    public static AirMediaSessionConnectionState from(Parcel in) { return from(in.readInt()); }

    public static AirMediaSessionConnectionState from(int v) {
        switch (v) {
            case 0: return Disconnected;
            case 1: return Connecting;
            case 2: return Connected;
            case 3: return Disconnecting;
        }
        return Disconnected;
    }
}
