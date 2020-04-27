package com.crestron.airmedia.canvas.channels.ipc;

import android.os.Parcel;
import android.os.Parcelable;

public class CanvasSourceLayout implements Parcelable {
    private final int contents;
    public final String sessionId;
    public final int left;
    public final int top;
    public final int width;
    public final int height;

    public CanvasSourceLayout(
            String sessionId,
            int left,
            int top,
            int width,
            int height
    ) {
        this.contents = 0;
        this.sessionId = sessionId;
        this.left = left;
        this.top = top;
        this.width = width;
        this.height = height;
    }

    protected CanvasSourceLayout(Parcel in) {
        this.contents = in.readInt();
        this.sessionId = in.readString();
        this.left = in.readInt();
        this.top = in.readInt();
        this.width = in.readInt();
        this.height = in.readInt();
    }

    public static final Creator<CanvasSourceLayout> CREATOR = new Creator<CanvasSourceLayout>() {
        @Override
        public CanvasSourceLayout createFromParcel(Parcel in) {
            return new CanvasSourceLayout(in);
        }

        @Override
        public CanvasSourceLayout[] newArray(int size) {
            return new CanvasSourceLayout[size];
        }
    };

    @Override
    public int describeContents() {
        return contents;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.contents);
        dest.writeString(this.sessionId);
        dest.writeInt(this.left);
        dest.writeInt(this.top);
        dest.writeInt(this.width);
        dest.writeInt(this.height);
    }
}