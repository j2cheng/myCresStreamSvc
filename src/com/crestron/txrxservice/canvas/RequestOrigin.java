package com.crestron.txrxservice.canvas;

import android.os.Parcel;
import android.os.Parcelable;

public enum RequestOrigin implements Parcelable {
    Unknown(0), Hardware(1), Receiver(2), CanvasSourceRequest(2), CanvasUser(3), Moderator(4), StateChangeMessage(5);
    public final int value;

    RequestOrigin(int v) { value = v; }

    @Override
    public void writeToParcel(Parcel dest, int flags) { dest.writeInt(value); }

    @Override
    public int describeContents() { return 0; }

    public static final Creator<RequestOrigin> CREATOR = new Creator<RequestOrigin>() {
        @Override
        public RequestOrigin createFromParcel(Parcel in) { return RequestOrigin.from(in); }

        @Override
        public RequestOrigin[] newArray(int size) { return new RequestOrigin[size]; }
    };

    public static RequestOrigin from(Parcel in) { return from(in.readInt()); }

    public static RequestOrigin from(int v) {
        switch (v) {
            case 0: return Unknown;
            case 1: return Hardware;
            case 2: return Receiver;
            case 3: return CanvasSourceRequest;
            case 4: return CanvasUser;
            case 5: return Moderator;
            case 6: return StateChangeMessage;
        }
        return Unknown;
    }
    
    public static final int size = RequestOrigin.values().length;
}