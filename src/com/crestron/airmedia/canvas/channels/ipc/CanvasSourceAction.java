package com.crestron.airmedia.canvas.channels.ipc;

import android.os.Parcel;
import android.os.Parcelable;

public enum CanvasSourceAction implements Parcelable {
    Undefined(0), Disconnect(1), Stop(2), Play(3), Pause(4), Mute(5), UnMute(6);

    public final int value;

    CanvasSourceAction(int value) {
        this.value = value;
    }

    public static final Creator<CanvasSourceAction> CREATOR = new Creator<CanvasSourceAction>() {
        @Override
        public CanvasSourceAction createFromParcel(Parcel in) {
            return CanvasSourceAction.from(in);
        }

        @Override
        public CanvasSourceAction[] newArray(int size) {
            return new CanvasSourceAction[size];
        }
    };

    @Override
    public int describeContents() { return 0; }

    @Override
    public void writeToParcel(Parcel dest, int flags) { dest.writeInt(value); }

    public static CanvasSourceAction from(Parcel in) { return from(in.readInt()); }

    public static CanvasSourceAction from(int value) {
        switch (value) {
            case 0: return Undefined;
            case 1: return Disconnect;
            case 2: return Stop;
            case 3: return Play;
            case 4: return Pause;
            case 5: return Mute;
            case 6: return UnMute;
        }
        return Undefined;
    }
}
