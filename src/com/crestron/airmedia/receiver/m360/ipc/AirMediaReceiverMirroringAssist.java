package com.crestron.airmedia.receiver.m360.ipc;

import android.os.Parcel;
import android.os.Parcelable;

import com.crestron.airmedia.utilities.Common;

import java.util.Locale;

public class AirMediaReceiverMirroringAssist implements Parcelable {
    public final String id;
    public final String description;

    public AirMediaReceiverMirroringAssist() {
        id = "";
        description = "";
    }

    public AirMediaReceiverMirroringAssist(String i, String d) {
        id = i;
        description = d;
    }

    private AirMediaReceiverMirroringAssist(Parcel in) {
        id = in.readString();
        description = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(description);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<AirMediaReceiverMirroringAssist> CREATOR = new Creator<AirMediaReceiverMirroringAssist>() {
        @Override
        public AirMediaReceiverMirroringAssist createFromParcel(Parcel in) {
            return new AirMediaReceiverMirroringAssist(in);
        }

        @Override
        public AirMediaReceiverMirroringAssist[] newArray(int size) {
            return new AirMediaReceiverMirroringAssist[size];
        }
    };

    @Override
    public String toString() {
        return String.format(Locale.US, "MirroringAssist[%s]", id);
    }

    public boolean isEqual(AirMediaReceiverMirroringAssist ma) {
        return isEqual(this, ma);
    }

    public static boolean isEqual(AirMediaReceiverMirroringAssist lhs, AirMediaReceiverMirroringAssist rhs) {
        return lhs == rhs || !(lhs == null || rhs == null) && Common.isEqual(lhs.id, rhs.id) && Common.isEqual(lhs.description, rhs.description);
    }
}
