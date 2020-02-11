package com.crestron.airmedia.canvas.channels.ipc;

import android.os.Parcel;
import android.os.Parcelable;

public enum CanvasSessionState implements Parcelable {
    Disconnected(0), Stopped(1), Playing(2), Paused(3);

    public final int value;

    CanvasSessionState(int value) {
        this.value = value;
    }

    public static final Creator<CanvasSessionState> CREATOR = new Creator<CanvasSessionState>() {
        @Override
        public CanvasSessionState createFromParcel(Parcel in) {
            return CanvasSessionState.from(in);
        }

        @Override
        public CanvasSessionState[] newArray(int size) {
            return new CanvasSessionState[size];
        }
    };

    @Override
    public int describeContents() { return 0; }

    @Override
    public void writeToParcel(Parcel dest, int flags) { dest.writeInt(value); }

    public static CanvasSessionState from(Parcel in) { return from(in.readInt()); }

    public static CanvasSessionState from(int value) {
        switch (value) {
            case 0: return Disconnected;
            case 1: return Stopped;
            case 2: return Playing;
            case 3: return Paused;
        }
        return Disconnected;
    }
}
