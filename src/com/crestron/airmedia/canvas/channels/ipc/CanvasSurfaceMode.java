package com.crestron.airmedia.canvas.channels.ipc;

import android.os.Parcel;
import android.os.Parcelable;

public enum  CanvasSurfaceMode implements Parcelable {
    Normal(0),
    TagVideoLayer(0x00000001);

    public final int value;

    CanvasSurfaceMode(int value) {
        this.value = value;
    }

    public static final Creator<CanvasSurfaceMode> CREATOR = new Creator<CanvasSurfaceMode>() {
        @Override
        public CanvasSurfaceMode createFromParcel(Parcel in) {
            return CanvasSurfaceMode.from(in);
        }

        @Override
        public CanvasSurfaceMode[] newArray(int size) {
            return new CanvasSurfaceMode[size];
        }
    };

    @Override
    public int describeContents() { return 0; }

    @Override
    public void writeToParcel(Parcel dest, int flags) { dest.writeInt(value); }

    public static CanvasSurfaceMode from(Parcel in) { return from(in.readInt()); }

    public static CanvasSurfaceMode from(int value) {
        switch (value) {
            case 0: return Normal;
            case 0x00000001: return TagVideoLayer;
        }
        return Normal;
    }

    public boolean isNormal() { return this == Normal; }

    public boolean tagVideoLayer() { return this == TagVideoLayer; }
}