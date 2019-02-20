package com.crestron.airmedia.receiver.m360.ipc;

import android.os.Parcel;
import android.os.Parcelable;

import com.crestron.airmedia.utilities.Common;
import com.google.gson.annotations.SerializedName;

import java.util.HashMap;
import java.util.Map;

public enum AirMediaPlatforms implements Parcelable {
    @SerializedName("0") Undefined(0),
    @SerializedName("1") Windows(1),
    @SerializedName("2") Mac(2),
    @SerializedName("3") iOS(3),
    @SerializedName("4") Android(4),
    @SerializedName("5") Chromebook(5);
    public final int value;

    private static final Map<String, AirMediaPlatforms> Mapping;

    static {
        Mapping = new HashMap<String, AirMediaPlatforms>();
        Mapping.put("iphone", iOS);
        Mapping.put("ipad", iOS);
        Mapping.put("ipod", iOS);
        Mapping.put("mac", Mac);
        Mapping.put("android", Android);
        Mapping.put("win", Windows);
    }

    AirMediaPlatforms(int v) { value = v; }

    @Override
    public void writeToParcel(Parcel dest, int flags) { dest.writeInt(value); }

    @Override
    public int describeContents() { return 0; }

    public static final Creator<AirMediaPlatforms> CREATOR = new Creator<AirMediaPlatforms>() {
        @Override
        public AirMediaPlatforms createFromParcel(Parcel in) { return AirMediaPlatforms.from(in); }

        @Override
        public AirMediaPlatforms[] newArray(int size) { return new AirMediaPlatforms[size]; }
    };

    public static AirMediaPlatforms from(Parcel in) { return from(in.readInt()); }

    public static AirMediaPlatforms from(int v) {
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

    public boolean supportsVideoPush() {
        switch (this) {
            case Windows: return false;
            case Mac: return true;
            case iOS: return true;
            case Android: return false;
            case Chromebook: return false;
        }
        return true;
    }

    public static AirMediaPlatforms from(String model) {
        if (Common.isEmpty(model)) return Undefined;
        model = model.toLowerCase();
        for (Map.Entry<String, AirMediaPlatforms> entry : Mapping.entrySet()) {
            if (model.contains(entry.getKey())) return entry.getValue();
        }
        return Undefined;
    }
}
