package com.crestron.txrxservice.canvas;

import android.os.Parcel;
import android.os.Parcelable;

public enum RequestOrigin implements Parcelable {
    Unknown(0), Error(1), Hardware(2), Receiver(3), CanvasSourceRequest(4), CanvasUser(5), 
    Moderator(6), StateChangeMessage(7), InactivityTimer(8), ConsoleCommand(9);
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
            case 1: return Error;
            case 2: return Hardware;
            case 3: return Receiver;
            case 4: return CanvasSourceRequest;
            case 5: return CanvasUser;
            case 6: return Moderator;
            case 7: return StateChangeMessage;
            case 8: return ConsoleCommand;
        }
        return Unknown;
    }
    
    public static final int size = RequestOrigin.values().length;
}