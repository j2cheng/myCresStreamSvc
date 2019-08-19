package com.crestron.airmedia.receiver.m360.ipc;

import android.os.Parcel;
import android.os.Parcelable;

import com.crestron.airmedia.utilities.Common;

public class AirMediaSessionVideoSource implements Parcelable {
    public final AirMediaSize resolution;
    public final AirMediaSize aspectRatio;
    public final AirMediaSize dpi;
    public final boolean isPrimary;
    public final boolean isVirtual;

    private AirMediaSessionVideoSource(
            final int resolutionWidth, final int resolutionHeight,
            final int aspectWidth, final int aspectHeight,
            final int dpiWidth, final int dpiHeight,
            final boolean primary, final boolean virtual) {
        resolution = new AirMediaSize(resolutionWidth, resolutionHeight);
        aspectRatio = new AirMediaSize(aspectWidth, aspectHeight);
        dpi = new AirMediaSize(dpiWidth, dpiHeight);
        isPrimary = primary;
        isVirtual = virtual;
    }

    private AirMediaSessionVideoSource(Parcel in) {
        resolution = AirMediaSize.CREATOR.createFromParcel(in);
        aspectRatio = AirMediaSize.CREATOR.createFromParcel(in);
        dpi = AirMediaSize.CREATOR.createFromParcel(in);
        isPrimary = in.readInt() != 0;
        isVirtual = in.readInt() != 0;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        resolution.writeToParcel(dest, flags);
        aspectRatio.writeToParcel(dest, flags);
        dpi.writeToParcel(dest, flags);
        dest.writeInt(isPrimary ? 1 : 0);
        dest.writeInt(isVirtual ? 1 : 0);
    }

    @Override
    public String toString() {
        return "AirMediaSessionVideoSource[" + toString(this) + " ]";
    }

    public static String toString(AirMediaSessionVideoSource[] sources) {
        StringBuilder builder = new StringBuilder();
        boolean append = false;
        builder.append("[");
        for (AirMediaSessionVideoSource source : sources) {
            if (source == null) continue;
            if (append) builder.append(" , ");
            else builder.append(" ");
            append = true;
            builder.append(toString(source));
        }
        builder.append(" ]");
        return builder.toString();
    }

    private static String toString(AirMediaSessionVideoSource source) {
        return source == null
                ? " "
                : "resolution= " + source.resolution + Common.Delimiter
                + " aspect-ratio= " + source.aspectRatio + Common.Delimiter
                + " dpi= " + source.dpi + Common.Delimiter
                + " is-primary= " + source.isPrimary + Common.Delimiter
                + " is-virtual= " + source.isVirtual;
    }

    public static final Creator<AirMediaSessionVideoSource> CREATOR = new Creator<AirMediaSessionVideoSource>() {
        @Override
        public AirMediaSessionVideoSource createFromParcel(Parcel in) {
            return new AirMediaSessionVideoSource(in);
        }

        @Override
        public AirMediaSessionVideoSource[] newArray(int size) { return new AirMediaSessionVideoSource[size]; }
    };

    public static AirMediaSessionVideoSource from(
            final int resolutionWidth, final int resolutionHeight,
            final int aspectWidth, final int aspectHeight,
            final int dpiWidth, final int dpiHeight,
            final boolean primary, final boolean virtual) {
        return new AirMediaSessionVideoSource(resolutionWidth, resolutionHeight, aspectWidth, aspectHeight, dpiWidth, dpiHeight, primary, virtual);
    }

    public boolean isEqual(AirMediaSessionVideoSource rhs) {
        return isEqual(this, rhs);
    }

    public boolean isNotEqual(AirMediaSessionVideoSource rhs) {
        return isNotEqual(this, rhs);
    }

    public static boolean isEqual(AirMediaSessionVideoSource lhs, AirMediaSessionVideoSource rhs) {
        return lhs == rhs || !(lhs == null || rhs == null)
                && lhs.resolution.equals(rhs.resolution)
                && lhs.aspectRatio.equals(rhs.aspectRatio)
                && lhs.dpi.equals(rhs.dpi)
                && lhs.isPrimary == rhs.isPrimary
                && lhs.isVirtual == rhs.isVirtual;
    }

    public static boolean isNotEqual(AirMediaSessionVideoSource lhs, AirMediaSessionVideoSource rhs) {
        return !isEqual(lhs, rhs);
    }

    public static boolean isEqual(AirMediaSessionVideoSource[] lhs, AirMediaSessionVideoSource[] rhs) {
        if (lhs == rhs || lhs == null || rhs == null)
            return lhs == rhs;

        if (lhs.length != rhs.length)
            return false;

        for (int i = 0; i < lhs.length; i++) {
            if (!AirMediaSessionVideoSource.isNotEqual(lhs[i], rhs[i]))
                return false;
        }

        return true;
    }
}
