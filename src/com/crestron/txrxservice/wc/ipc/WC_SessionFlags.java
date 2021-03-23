package com.crestron.txrxservice.wc.ipc;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;

public enum WC_SessionFlags implements Parcelable {
    None(0),
    Video(0x1),
    Audio(0x2),
    AudioAndVideo(0x3);

    public final int value;

    WC_SessionFlags(int value) {
        this.value = value;
    }

    boolean isAudioSet() {
        return isSet(Audio, value);
    }

    boolean isVideoSet() {
        return isSet(Video, value);
    }

    private static boolean isSet(WC_SessionFlags flag, int value) {
        return (value & flag.value) == flag.value;
    }

    public static final Creator<WC_SessionFlags> CREATOR = new Creator<WC_SessionFlags>() {
        @Override
        public WC_SessionFlags createFromParcel(Parcel in) {
            return WC_SessionFlags.from(in);
        }

        @Override
        public WC_SessionFlags[] newArray(int size) {
            return new WC_SessionFlags[size];
        }
    };

    @Override
    public int describeContents() { return 0; }

    @Override
    public void writeToParcel(Parcel dest, int flags) { dest.writeInt(value); }

    public static WC_SessionFlags from(Parcel in) { return from(in.readInt()); }

    public static WC_SessionFlags from(int value) {
        switch (value) {
            case 1: return Video;
            case 2: return Audio;
            case 3: return AudioAndVideo;
        }
        return None;
    }
}