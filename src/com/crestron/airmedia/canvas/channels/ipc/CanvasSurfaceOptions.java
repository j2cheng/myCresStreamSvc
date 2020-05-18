package com.crestron.airmedia.canvas.channels.ipc;

import android.os.Parcel;
import android.os.Parcelable;

import com.crestron.airmedia.utilities.Common;

import java.util.ArrayList;
import java.util.List;


public class CanvasSurfaceOptions implements Parcelable {
    private final int contents;
    public final CanvasSurfaceMode mode;
    public final String tag;

    public CanvasSurfaceOptions() {
        this(CanvasSurfaceMode.Normal, null);
    }

    public CanvasSurfaceOptions(CanvasSurfaceMode mode, String tag) {
        this.contents = 0;
        this.mode = mode;
        this.tag = tag;
    }

    protected CanvasSurfaceOptions(Parcel in) {
        this.contents = in.readInt();
        this.mode = CanvasSurfaceMode.from(in);
        this.tag = in.readString();
    }

    public static final Creator<CanvasSurfaceOptions> CREATOR = new Creator<CanvasSurfaceOptions>() {
        @Override
        public CanvasSurfaceOptions createFromParcel(Parcel in) {
            return new CanvasSurfaceOptions(in);
        }

        @Override
        public CanvasSurfaceOptions[] newArray(int size) {
            return new CanvasSurfaceOptions[size];
        }
    };

    @Override
    public int describeContents() {
        return contents;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.contents);
        this.mode.writeToParcel(dest, flags);
        dest.writeString(tag);
    }

    @Override
    public String toString() {
        return "options[mode= " + mode + ((Common.isEmpty(tag)) ? "]" : " · tag= " + tag + "]");
    }
}