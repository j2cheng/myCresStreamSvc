package com.crestron.txrxservice.canvas;

import android.os.Parcel;
import android.os.Parcelable;

public enum SessionType implements Parcelable {
    Unknown(0), AirMedia(1), AirBoard(2), HDMI(3), DM(4);
    public final int value;

    SessionType(int v) { value = v; }

    @Override
    public void writeToParcel(Parcel dest, int flags) { dest.writeInt(value); }

    @Override
    public int describeContents() { return 0; }

    public static final Creator<SessionType> CREATOR = new Creator<SessionType>() {
        @Override
        public SessionType createFromParcel(Parcel in) { return SessionType.from(in); }

        @Override
        public SessionType[] newArray(int size) { return new SessionType[size]; }
    };

    public static SessionType from(Parcel in) { return from(in.readInt()); }

    public static SessionType from(int v) {
        switch (v) {
            case 0: return Unknown;
            case 1: return AirMedia;
            case 2: return AirBoard;
            case 3: return HDMI;
            case 4: return DM;
        }
        return Unknown;
    }
}