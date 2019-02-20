package com.crestron.airmedia.receiver.m360.ipc;

import android.os.Parcel;
import android.os.Parcelable;

import com.crestron.airmedia.utilities.Common;

import java.util.Locale;

public class AirMediaReceiverVolume implements Parcelable {
    public final float min;
    public final float max;
    public final float step;

    public AirMediaReceiverVolume() {
        this(0.00f, 1.00f, 0.01f);
    }

    public AirMediaReceiverVolume(float min, float max, float step) {
        this.min = min;
        this.max = max;
        this.step = step;
    }

    private AirMediaReceiverVolume(Parcel in) {
        this(in.readFloat(), in.readFloat(), in.readFloat());
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeFloat(min);
        dest.writeFloat(max);
        dest.writeFloat(step);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<AirMediaReceiverVolume> CREATOR = new Creator<AirMediaReceiverVolume>() {
        @Override
        public AirMediaReceiverVolume createFromParcel(Parcel in) {
            return new AirMediaReceiverVolume(in);
        }

        @Override
        public AirMediaReceiverVolume[] newArray(int size) {
            return new AirMediaReceiverVolume[size];
        }
    };

    @Override
    public String toString() {
        return String.format(Locale.US, "ReceiverVolume[%s]", toPropertiesString());
    }

    public String toPropertiesString() {
        return String.format(Locale.US, "%.2f .. %.2f [%.2f]", min, max, step);
    }

    public AirMediaReceiverVolume clone() { return new AirMediaReceiverVolume(min, max, step); }

    public boolean isEqual(AirMediaReceiverVolume rhs) {
        return isEqual(this, rhs);
    }

    public static boolean isEqual(AirMediaReceiverVolume lhs, AirMediaReceiverVolume rhs) {
        final float epsilon = 0.005f;
        return lhs == rhs || !(lhs == null || rhs == null) && Common.isEqual(lhs.min, rhs.min, epsilon) && Common.isEqual(lhs.max, rhs.max, epsilon) && Common.isEqual(lhs.step, rhs.step, epsilon);
    }
}
