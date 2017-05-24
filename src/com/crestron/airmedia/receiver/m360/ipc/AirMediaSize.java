package com.crestron.airmedia.receiver.m360.ipc;

import android.os.Parcel;
import android.os.Parcelable;

public class AirMediaSize implements Parcelable {
    public static AirMediaSize Zero = new AirMediaSize(0, 0);
    public final int width;
    public final int height;

    public AirMediaSize(int w, int h) { width = w; height = h; }

    AirMediaSize(Parcel in) { width = in.readInt(); height = in.readInt(); }

    @Override
    public void writeToParcel(Parcel dest, int flags) { dest.writeInt(width); dest.writeInt(height); }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<AirMediaSize> CREATOR = new Creator<AirMediaSize>() {
        @Override
        public AirMediaSize createFromParcel(Parcel in) {
            return new AirMediaSize(in);
        }

        @Override
        public AirMediaSize[] newArray(int size) {
            return new AirMediaSize[size];
        }
    };

    @Override public String toString() { return width + "x" + height; }

    @Override
    public boolean equals(Object obj) {
        return (obj != null && obj instanceof AirMediaSize) && equals((AirMediaSize) obj);
    }

    public boolean equals(final AirMediaSize size) {
        return size != null && size.width == width && size.height == height;
    }

    public static boolean equals(final AirMediaSize lhs, final AirMediaSize rhs) {
        return (lhs == rhs) || (lhs != null && lhs.equals(rhs));
    }
}
