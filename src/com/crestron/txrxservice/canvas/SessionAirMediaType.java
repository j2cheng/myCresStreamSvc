package com.crestron.txrxservice.canvas;

import android.os.Parcel;
import android.os.Parcelable;

public enum SessionAirMediaType implements Parcelable {
    Undefined(-1), App(0), AirPlay(1), Miracast(2), WebRtc(3);
    public final int value;

    SessionAirMediaType(int v) { value = v; }

    @Override
    public void writeToParcel(Parcel dest, int flags) { dest.writeInt(value); }

    @Override
    public int describeContents() { return 0; }

    public static final Creator<SessionAirMediaType> CREATOR = new Creator<SessionAirMediaType>() {
        @Override
        public SessionAirMediaType createFromParcel(Parcel in) { return SessionAirMediaType.from(in); }

        @Override
        public SessionAirMediaType[] newArray(int size) { return new SessionAirMediaType[size]; }
    };

    public static SessionAirMediaType from(Parcel in) { return from(in.readInt()); }

    public static SessionAirMediaType from(int v) {
        switch (v) {
            case -1: return Undefined;
            case 0: return App;
            case 1: return AirPlay;
            case 2: return Miracast;
            case 3: return WebRtc;
        }
        return Undefined;
    }
}