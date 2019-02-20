package com.crestron.airmedia.receiver.m360.ipc;

import android.os.Parcel;
import android.os.Parcelable;

import com.crestron.airmedia.utilities.Common;
import com.google.gson.annotations.SerializedName;

public enum AirMediaSessionScreenLayout implements Parcelable {
    @SerializedName("0") None(0),
    @SerializedName("1") Mixed(1),
    @SerializedName("2") Fullscreen(2),
    @SerializedName("3") FourScreen(3),
    @SerializedName("4") SixScreen(4),
    @SerializedName("5") NineScreen(5),
    @SerializedName("6") TwoScreen(6);
    public final int value;

    AirMediaSessionScreenLayout(int v) { value = v; }

    @Override
    public void writeToParcel(Parcel dest, int flags) { dest.writeInt(value); }

    @Override
    public int describeContents() { return 0; }

    public static final Creator<AirMediaSessionScreenLayout> CREATOR = new Creator<AirMediaSessionScreenLayout>() {
        @Override
        public AirMediaSessionScreenLayout createFromParcel(Parcel in) { return AirMediaSessionScreenLayout.from(in); }

        @Override
        public AirMediaSessionScreenLayout[] newArray(int size) { return new AirMediaSessionScreenLayout[size]; }
    };

    public static AirMediaSessionScreenLayout from(Parcel in) { return from(in.readInt()); }

    public static AirMediaSessionScreenLayout from(int v) {
        switch (v) {
            case 0: return None;
            case 1: return Mixed;
            case 2: return Fullscreen;
            case 3: return FourScreen;
            case 4: return SixScreen;
            case 5: return NineScreen;
            case 6: return TwoScreen;
        }
        return None;
    }

    public static String toString(AirMediaSessionScreenLayout[] layouts) {
        if (layouts == null || layouts.length == 0) return "<none>";
        StringBuilder builder = new StringBuilder();
        builder.append("[ ");
        boolean delimit = false;
        for (AirMediaSessionScreenLayout layout : layouts) {
            if (delimit) builder.append(Common.Delimiter);
            delimit = true;
            builder.append(layout);
        }
        builder.append(" ]");
        return builder.toString();
    }
}
