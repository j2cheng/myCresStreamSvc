package com.crestron.airmedia.canvas.channels.ipc;

import android.os.Parcel;
import android.os.Parcelable;

public class CanvasSourceTransaction implements Parcelable {
    private final int content;
    public final String sessionId;
    public final CanvasSourceAction action;

    public CanvasSourceTransaction() {
        this(null, CanvasSourceAction.Undefined);
    }

    public CanvasSourceTransaction(String sessionId, CanvasSourceAction action) {
        this.content = 0;
        this.sessionId = sessionId;
        this.action = action;
    }

    protected CanvasSourceTransaction(Parcel in) {
        this.content = in.readInt();
        this.sessionId = in.readString();
        this.action = CanvasSourceAction.CREATOR.createFromParcel(in);
    }

    public static final Creator<CanvasSourceTransaction> CREATOR = new Creator<CanvasSourceTransaction>() {
        @Override
        public CanvasSourceTransaction createFromParcel(Parcel in) {
            return new CanvasSourceTransaction(in);
        }

        @Override
        public CanvasSourceTransaction[] newArray(int size) {
            return new CanvasSourceTransaction[size];
        }
    };

    @Override
    public int describeContents() {
        return content;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.content);
        dest.writeString(this.sessionId);
        action.writeToParcel(dest, flags);
    }
}
