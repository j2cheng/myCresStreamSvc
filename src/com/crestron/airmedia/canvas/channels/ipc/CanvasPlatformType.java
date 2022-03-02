package com.crestron.airmedia.canvas.channels.ipc;

import android.os.Parcel;
import android.os.Parcelable;

public enum CanvasPlatformType implements Parcelable {
    Undefined(0),
    Hardware(1),
    Windows(2),
    Mac(3),
    iOS(4),
    Android(5),
    Chrome(6),
    Linux(7),
    Tx3_100(8),
    Tx3_200(9);

    public final int value;

    CanvasPlatformType(int value) {
        this.value = value;
    }

    public static final Creator<CanvasPlatformType> CREATOR = new Creator<CanvasPlatformType>() {
        @Override
        public CanvasPlatformType createFromParcel(Parcel in) {
            return CanvasPlatformType.from(in);
        }

        @Override
        public CanvasPlatformType[] newArray(int size) {
            return new CanvasPlatformType[size];
        }
    };

    @Override
    public int describeContents() { return 0; }

    @Override
    public void writeToParcel(Parcel dest, int flags) { dest.writeInt(value); }

    public static CanvasPlatformType from(Parcel in) { return from(in.readInt()); }

    public static CanvasPlatformType from(int value) {
        switch (value) {
            case 0: return Undefined;
            case 1: return Hardware;
            case 2: return Windows;
            case 3: return Mac;
            case 4: return iOS;
            case 5: return Android;
            case 6: return Chrome;
            case 7: return Linux;
            case 8: return Tx3_100;
            case 9: return Tx3_200;
        }
        return Undefined;
    }
}
