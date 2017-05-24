package com.crestron.airmedia.receiver.m360.ipc;

import android.os.Parcel;
import android.os.Parcelable;

public enum AirMediaReceiverState implements Parcelable {
    Stopped(4), Starting(1), Started(2), Stopping(3);
    public final int value;

    AirMediaReceiverState(int v) { value = v; }

    @Override
    public void writeToParcel(Parcel dest, int flags) { dest.writeInt(value); }

    @Override
    public int describeContents() { return 0; }

    public static final Creator<AirMediaReceiverState> CREATOR = new Creator<AirMediaReceiverState>() {
        @Override
        public AirMediaReceiverState createFromParcel(Parcel in) { return AirMediaReceiverState.from(in); }

        @Override
        public AirMediaReceiverState[] newArray(int size) { return new AirMediaReceiverState[size]; }
    };

    public static AirMediaReceiverState from(Parcel in) { return from(in.readInt()); }

    public static AirMediaReceiverState from(int v) {
        switch (v) {
            case 0:
            case 4: return Stopped;
            case 1: return Starting;
            case 2: return Started;
            case 3: return Stopping;
        }
        return Stopped;
    }
}
