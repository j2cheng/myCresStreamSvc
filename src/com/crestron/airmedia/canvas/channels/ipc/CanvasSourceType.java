package com.crestron.airmedia.canvas.channels.ipc;

import android.os.Parcel;
import android.os.Parcelable;

public enum CanvasSourceType implements Parcelable {
    Unknown(0), AirMedia(1), HDMI(2), DM(3), Airboard(4);

    public final int value;

    CanvasSourceType(int value) {
        this.value = value;
    }

    public static final Creator<CanvasSourceType> CREATOR = new Creator<CanvasSourceType>() {
        @Override
        public CanvasSourceType createFromParcel(Parcel in) {
            return CanvasSourceType.from(in);
        }

        @Override
        public CanvasSourceType[] newArray(int size) {
            return new CanvasSourceType[size];
        }
    };

    @Override
    public int describeContents() { return 0; }

    @Override
    public void writeToParcel(Parcel dest, int flags) { dest.writeInt(value); }

    public static CanvasSourceType from(Parcel in) { return from(in.readInt()); }

    public static CanvasSourceType from(int value) {
        switch (value) {
            case 0: return Unknown;
            case 1: return AirMedia;
            case 2: return HDMI;
            case 3: return DM;
            case 4: return Airboard;
        }
        return Unknown;
    }
}

