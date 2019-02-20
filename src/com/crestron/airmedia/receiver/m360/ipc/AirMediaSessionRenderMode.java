package com.crestron.airmedia.receiver.m360.ipc;

import android.os.Parcel;
import android.os.Parcelable;

public enum AirMediaSessionRenderMode implements Parcelable {
    Undefined(0), Software(1), Hardware(2);
    public final int value;

    AirMediaSessionRenderMode(int v) {
        value = v;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) { dest.writeInt(value); }

    @Override
    public int describeContents() { return 0; }

    public static final Creator<AirMediaSessionRenderMode> CREATOR = new Creator<AirMediaSessionRenderMode>() {
        @Override
        public AirMediaSessionRenderMode createFromParcel(Parcel in) { return AirMediaSessionRenderMode.from(in); }

        @Override
        public AirMediaSessionRenderMode[] newArray(int size) { return new AirMediaSessionRenderMode[size]; }
    };

    public static AirMediaSessionRenderMode from(Parcel in) { return from(in.readInt()); }

    public static AirMediaSessionRenderMode from(int v) {
        switch (v) {
            case 0: return Undefined;
            case 1: return Software;
            case 2: return Hardware;
        }
        return Undefined;
    }
}

