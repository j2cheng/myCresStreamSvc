package com.crestron.airmedia.canvas.channels.ipc;

import android.os.Parcel;
import android.os.Parcelable;

public class CanvasSourceSession implements Parcelable {
    private final int contents;
    public final String sessionId;
    public final String username;
    public final CanvasSessionState state;
    public final CanvasSourceType type;
    public final CanvasPlatformType platform;
    public final int width;
    public final int height;

    public CanvasSourceSession(
            String sessionId,
            String username,
            CanvasSessionState state,
            CanvasSourceType type,
            CanvasPlatformType platform,
            int width,
            int height
    ) {
        this.contents = 0;
        this.sessionId = sessionId;
        this.state = state;
        this.username = username;
        this.type = type;
        this.platform = platform;
        this.width = width;
        this.height = height;
    }
    
    private CanvasSourceSession(Parcel in) {
        this.contents = in.readInt();
        this.sessionId = in.readString();
        this.state = CanvasSessionState.from(in);
        this.username = in.readString();
        this.type = CanvasSourceType.from(in);
        this.platform = CanvasPlatformType.from(in);
        this.width = in.readInt();
        this.height = in.readInt();
    }

    public static final Creator<CanvasSourceSession> CREATOR = new Creator<CanvasSourceSession>() {
        @Override
        public CanvasSourceSession createFromParcel(Parcel in) {
            return new CanvasSourceSession(in);
        }

        @Override
        public CanvasSourceSession[] newArray(int size) {
            return new CanvasSourceSession[size];
        }
    };

    @Override
    public int describeContents() {
        return this.contents;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.contents);
        dest.writeString(this.sessionId);
        this.state.writeToParcel(dest, flags);
        dest.writeString(this.username);
        this.type.writeToParcel(dest, flags);
        this.platform.writeToParcel(dest, flags);
        dest.writeInt(this.width);
        dest.writeInt(this.height);
    }
}
