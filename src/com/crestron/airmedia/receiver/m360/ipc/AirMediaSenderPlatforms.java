package com.crestron.airmedia.receiver.m360.ipc;

import android.os.Parcel;
import android.os.Parcelable;

import com.crestron.airmedia.utilities.Common;

import java.util.HashMap;
import java.util.Map;

public enum AirMediaSenderPlatforms implements Parcelable {
    Undefined(0), Windows(1), Mac(2), iOS(3), Android(4), Chromebook(5);
    public final int value;

    private static final Map<String, AirMediaSenderPlatforms> Mapping;

    static {
        Mapping = new HashMap<String, AirMediaSenderPlatforms>();
        Mapping.put("iphone", iOS);
        Mapping.put("ipad", iOS);
        Mapping.put("ipod", iOS);
        Mapping.put("mac", Mac);
        Mapping.put("android", Android);
        Mapping.put("win", Windows);
    }

    AirMediaSenderPlatforms(int v) { value = v; }

    @Override
    public void writeToParcel(Parcel dest, int flags) { dest.writeInt(value); }

    @Override
    public int describeContents() { return 0; }

    public static final Creator<AirMediaSenderPlatforms> CREATOR = new Creator<AirMediaSenderPlatforms>() {
        @Override
        public AirMediaSenderPlatforms createFromParcel(Parcel in) { return AirMediaSenderPlatforms.from(in); }

        @Override
        public AirMediaSenderPlatforms[] newArray(int size) { return new AirMediaSenderPlatforms[size]; }
    };

    public static AirMediaSenderPlatforms from(Parcel in) { return from(in.readInt()); }

    public static AirMediaSenderPlatforms from(int v) {
        switch (v) {
            case 0: return Undefined;
            case 1: return Windows;
            case 2: return Mac;
            case 3: return iOS;
            case 4: return Android;
            case 5: return Chromebook;
        }
        return Undefined;
    }

    public String manufacturer() {
        switch (this) {
            case Windows:
                return "Microsoft";
            case Mac:
            case iOS:
                return "Apple";
            case Android:
            case Chromebook:
                return "Google";
        }
        return "";
    }

    public boolean supportsRotation() {
        switch (this) {
            case Windows: return true;
            case Mac: return false;
            case iOS: return true;
            case Android: return true;
            case Chromebook: return true;
        }
        return true;
    }

    public boolean managesRotation() {
        switch (this) {
            case Windows: return true;
            case Mac: return false;
            case iOS: return false;
            case Android: return true;
            case Chromebook: return false;
        }
        return false;
    }

    public static AirMediaSenderPlatforms from(String model) {
        if (Common.isEmpty(model)) return Undefined;
        model = model.toLowerCase();
        for (Map.Entry<String, AirMediaSenderPlatforms> entry : Mapping.entrySet()) {
            if (model.contains(entry.getKey())) return entry.getValue();
        }
        return Undefined;
    }
}
