package com.crestron.airmedia.canvas.channels.ipc;

import android.os.Parcel;
import android.os.Parcelable;

public enum CanvasVideoType implements Parcelable {
    Undefined(0),
    AirPlayMirror(1),
    AirPlayVideo(2),
    WebRTC(3),
    Hardware(255),
    Miracast(256);

    public final int value;

    CanvasVideoType(int value) {
        this.value = value;
    }

    public static final Creator<CanvasVideoType> CREATOR = new Creator<CanvasVideoType>() {
        @Override
        public CanvasVideoType createFromParcel(Parcel in) {
            return CanvasVideoType.from(in);
        }

        @Override
        public CanvasVideoType[] newArray(int size) {
            return new CanvasVideoType[size];
        }
    };

    @Override
    public int describeContents() { return 0; }

    @Override
    public void writeToParcel(Parcel dest, int flags) { dest.writeInt(value); }

    public static CanvasVideoType from(Parcel in) { return from(in.readInt()); }

    public static CanvasVideoType from(int value) {
        switch (value) {
            case 0: return Undefined;
            case 1: return AirPlayMirror;
            case 2: return AirPlayVideo;
            case 3: return WebRTC;
            case 255: return Hardware;
            case 256: return Miracast;
        }
        return Undefined;
    }
}