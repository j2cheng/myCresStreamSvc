package com.crestron.txrxservice.wc.ipc;

import android.os.Parcel;
import android.os.Parcelable;

public class WC_Status implements Parcelable {
    private final int contents;
    public boolean isServerStarted;
    public boolean isClientConnected;
    public int sessionId;
    public String clientId;
    public String nickname;
    public WC_SessionFlags sessionFlags;

    public WC_Status() {
        this(false, false, 0, "", "", WC_SessionFlags.None);
    }

    public WC_Status(
            boolean isServerStarted,
            boolean isClientConnected,
            int sessionId,
            String clientId,
            String nickname,
            WC_SessionFlags sessionFlags
    ) {
        this.contents = 0;
        this.isServerStarted = isServerStarted;
        this.isClientConnected = isClientConnected;
        this.sessionId = sessionId;
        this.clientId = clientId;
        this.nickname = nickname;
        this.sessionFlags = sessionFlags;
    }

    protected WC_Status(Parcel in) {
        this.contents = in.readInt();
        this.isServerStarted = in.readInt() != 0;
        this.isClientConnected = in.readInt() != 0;
        this.sessionId = in.readInt();
        this.clientId = in.readString();
        this.nickname = in.readString();
        this.sessionFlags = WC_SessionFlags.from(in);
    }

    public static final Creator<WC_Status> CREATOR = new Creator<WC_Status>() {
        @Override
        public WC_Status createFromParcel(Parcel in) {
            return new WC_Status(in);
        }

        @Override
        public WC_Status[] newArray(int size) {
            return new WC_Status[size];
        }
    };

    @Override
    public int describeContents() {
        return contents;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.contents);
        dest.writeInt(this.isServerStarted ? 1 : 0);
        dest.writeInt(this.isClientConnected ? 1 : 0);
        dest.writeInt(this.sessionId);
        dest.writeString(this.clientId);
        dest.writeString(this.nickname);
        this.sessionFlags.writeToParcel(dest, flags);
    }
    
    @Override
    public String toString() {
    	return "Status = {\n"+
               "    isServerStarted: "+isServerStarted+"\n"+
               "    isClientConnected: "+isServerStarted+"\n"+
               "    sessionId: "+sessionId+"\n"+
               "    clientId: "+clientId+"\n"+
               "    nickname: "+nickname+"\n"+
               "    flags: "+sessionFlags+"\n";
    }
}