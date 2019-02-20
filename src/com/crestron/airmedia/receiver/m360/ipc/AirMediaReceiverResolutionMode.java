package com.crestron.airmedia.receiver.m360.ipc;

import android.os.Parcel;
import android.os.Parcelable;

public enum AirMediaReceiverResolutionMode implements Parcelable {
    Unknown(-1), Max720P(0), Max1080P(1), MaxNative(2);
    public final int value;

    AirMediaReceiverResolutionMode(int v) {
        value = v;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) { dest.writeInt(value); }

    @Override
    public int describeContents() { return 0; }

    public static final Creator<AirMediaReceiverResolutionMode> CREATOR = new Creator<AirMediaReceiverResolutionMode>() {
        @Override
        public AirMediaReceiverResolutionMode createFromParcel(Parcel in) { return AirMediaReceiverResolutionMode.from(in); }

        @Override
        public AirMediaReceiverResolutionMode[] newArray(int size) { return new AirMediaReceiverResolutionMode[size]; }
    };

    public static AirMediaReceiverResolutionMode from(Parcel in) { return from(in.readInt()); }

    public static AirMediaReceiverResolutionMode from(int v) {
        switch (v) {
            case 0: return Max720P;
            case 1: return Max1080P;
            case 2: return MaxNative;
        }
        return Max720P;
    }
}
