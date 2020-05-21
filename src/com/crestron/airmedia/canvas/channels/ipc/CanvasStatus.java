package com.crestron.airmedia.canvas.channels.ipc;

import android.os.Parcel;
import android.os.Parcelable;

public class CanvasStatus implements Parcelable {
    private final int contents;
    public final boolean isReady;

    public CanvasStatus() {
        this(false);
    }

    public CanvasStatus(boolean isReady) {
        this.contents = 0;
        this.isReady = isReady;
    }

    protected CanvasStatus(Parcel in) {
        this.contents = in.readInt();
        this.isReady = in.readInt() != 0;
    }

    public static final Creator<CanvasStatus> CREATOR = new Creator<CanvasStatus>() {
        @Override
        public CanvasStatus createFromParcel(Parcel in) {
            return new CanvasStatus(in);
        }

        @Override
        public CanvasStatus[] newArray(int size) {
            return new CanvasStatus[size];
        }
    };

    @Override
    public int describeContents() {
        return contents;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.contents);
        dest.writeInt(this.isReady ? -1 : 0);
    }
}