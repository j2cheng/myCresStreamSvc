package com.crestron.airmedia.receiver.m360.ipc;

import android.os.Parcel;
import android.os.Parcelable;

public enum AirMediaReceiverLoadedState implements Parcelable {
    Unloaded(0), Loading(1), Loaded(2), Unloading(3);
    public final int value;

    AirMediaReceiverLoadedState(int v) { value = v; }

    @Override
    public void writeToParcel(Parcel dest, int flags) { dest.writeInt(value); }

    @Override
    public int describeContents() { return 0; }

    public static final Creator<AirMediaReceiverLoadedState> CREATOR = new Creator<AirMediaReceiverLoadedState>() {
        @Override
        public AirMediaReceiverLoadedState createFromParcel(Parcel in) { return AirMediaReceiverLoadedState.from(in); }

        @Override
        public AirMediaReceiverLoadedState[] newArray(int size) { return new AirMediaReceiverLoadedState[size]; }
    };

    public static AirMediaReceiverLoadedState from(Parcel in) { return from(in.readInt()); }

    public static AirMediaReceiverLoadedState from(int v) {
        switch (v) {
            case 0: return Unloaded;
            case 1: return Loading;
            case 2: return Loaded;
            case 3: return Unloading;
        }
        return Unloaded;
    }
}
