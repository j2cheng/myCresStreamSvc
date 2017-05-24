package com.crestron.airmedia.receiver.m360.ipc;

import android.os.Parcel;
import android.os.Parcelable;

public enum AirMediaSessionScreenPositionLayout implements Parcelable {
    None(0), Mixed(1), Fullscreen(2), FourScreen(3), SixScreen(4), NineScreen(5);
    public final int value;

    AirMediaSessionScreenPositionLayout(int v) { value = v; }

    @Override
    public void writeToParcel(Parcel dest, int flags) { dest.writeInt(value); }

    @Override
    public int describeContents() { return 0; }

    public static final Creator<AirMediaSessionScreenPositionLayout> CREATOR = new Creator<AirMediaSessionScreenPositionLayout>() {
        @Override
        public AirMediaSessionScreenPositionLayout createFromParcel(Parcel in) { return AirMediaSessionScreenPositionLayout.from(in); }

        @Override
        public AirMediaSessionScreenPositionLayout[] newArray(int size) { return new AirMediaSessionScreenPositionLayout[size]; }
    };

    public static AirMediaSessionScreenPositionLayout from(Parcel in) { return from(in.readInt()); }

    public static AirMediaSessionScreenPositionLayout from(int v) {
        switch (v) {
            case 0: return None;
            case 1: return Mixed;
            case 2: return Fullscreen;
            case 3: return FourScreen;
            case 4: return SixScreen;
            case 5: return NineScreen;
        }
        return None;
    }
}

