package com.crestron.airmedia.receiver.m360.ipc;

import android.os.Parcel;
import android.os.Parcelable;

public enum AirMediaSessionVideoType implements Parcelable {
    Undefined(0), Mirror(1), Video(2), WebRTC(3);
    public final int value;

    AirMediaSessionVideoType(int v) {
        value = v;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) { dest.writeInt(value); }

    @Override
    public int describeContents() { return 0; }

    public static final Creator<AirMediaSessionVideoType> CREATOR = new Creator<AirMediaSessionVideoType>() {
        @Override
        public AirMediaSessionVideoType createFromParcel(Parcel in) { return AirMediaSessionVideoType.from(in); }

        @Override
        public AirMediaSessionVideoType[] newArray(int size) { return new AirMediaSessionVideoType[size]; }
    };

    public static AirMediaSessionVideoType from(Parcel in) { return from(in.readInt()); }

    public static AirMediaSessionVideoType from(int v) {
        switch (v) {
            case 0: return Undefined;
            case 1: return Mirror;
            case 2: return Video;
            case 3: return WebRTC;
        }
        return Undefined;
    }
}
