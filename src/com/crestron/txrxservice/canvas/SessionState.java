package com.crestron.txrxservice.canvas;

import android.os.Parcel;
import android.os.Parcelable;

public enum SessionState implements Parcelable {
    Stopped(0), Starting(1), Playing(2), Pausing(3), Paused(4), Stopping(5), UnPausing(6), Connecting(7), Disconnecting(8);
    public final int value;

    SessionState(int v) { value = v; }
    
    public int getValue() { return value; }

    @Override
    public void writeToParcel(Parcel dest, int flags) { dest.writeInt(value); }

    @Override
    public int describeContents() { return 0; }

    public static final Creator<SessionState> CREATOR = new Creator<SessionState>() {
        @Override
        public SessionState createFromParcel(Parcel in) { return SessionState.from(in); }

        @Override
        public SessionState[] newArray(int size) { return new SessionState[size]; }
    };

    public static SessionState from(Parcel in) { return from(in.readInt()); }

    public static SessionState from(int v) {
        switch (v) {
            case 0: return Stopped;
            case 1: return Starting;
            case 2: return Playing;
            case 3: return Pausing;
            case 4: return Paused;
            case 5: return Stopping;
            case 6: return UnPausing;
            case 7: return Connecting;
            case 8: return Disconnecting;
        }
        return Stopped;
    }
    
    public static String feedbackString(int v) {
    	switch(v) {
        	case 0: return "Stop";
        	case 1: return "Play";
        	case 2: return "Play";
        	case 3: return "Play";
        	case 4: return "Play";
        	case 5: return "Stop";
        	case 6: return "Play";
        	case 7: return "Connect";
        	case 8: return "Disconnect";
    	}
    	return "Stop";
    }
}