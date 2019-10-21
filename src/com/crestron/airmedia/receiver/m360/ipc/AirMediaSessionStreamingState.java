package com.crestron.airmedia.receiver.m360.ipc;

import android.os.Parcel;
import android.os.Parcelable;

public enum AirMediaSessionStreamingState implements Parcelable {
    Stopped(0), Starting(1), Playing(2), Pausing(3), Paused(4), Stopping(5), UnPausing(6);
    public final int value;

    AirMediaSessionStreamingState(int v) { value = v; }

    @Override
    public void writeToParcel(Parcel dest, int flags) { dest.writeInt(value); }

    @Override
    public int describeContents() { return 0; }

    public static final Creator<AirMediaSessionStreamingState> CREATOR = new Creator<AirMediaSessionStreamingState>() {
        @Override
        public AirMediaSessionStreamingState createFromParcel(Parcel in) { return AirMediaSessionStreamingState.from(in); }

        @Override
        public AirMediaSessionStreamingState[] newArray(int size) { return new AirMediaSessionStreamingState[size]; }
    };

    public static AirMediaSessionStreamingState from(Parcel in) { return from(in.readInt()); }

    public static AirMediaSessionStreamingState from(int v) {
        switch (v) {
            case 0: return Stopped;
            case 1: return Starting;
            case 2: return Playing;
            case 3: return Pausing;
            case 4: return Paused;
            case 5: return Stopping;
            case 6: return UnPausing;
        }
        return Stopped;
    }
}
