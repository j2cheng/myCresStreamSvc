package com.crestron.txrxservice.wc.ipc;

import android.os.Parcel;
import android.os.Parcelable;

public class WC_SessionOptions implements Parcelable {
    private final int contents;
    public final String nickname;
    public final WC_SessionFlags flags;

    public WC_SessionOptions() {
        this("", WC_SessionFlags.None);
    }

    public WC_SessionOptions(String nickname, WC_SessionFlags flags) {
        this.contents = 0;
        this.nickname = nickname;
        this.flags = flags;
    }

    protected WC_SessionOptions(Parcel in) {
        this.contents = in.readInt();
        this.nickname = in.readString();
        this.flags = WC_SessionFlags.from(in);
    }

    public static final Creator<WC_SessionOptions> CREATOR = new Creator<WC_SessionOptions>() {
        @Override
        public WC_SessionOptions createFromParcel(Parcel in) {
            return new WC_SessionOptions(in);
        }

        @Override
        public WC_SessionOptions[] newArray(int size) {
            return new WC_SessionOptions[size];
        }
    };

    @Override
    public int describeContents() {
        return contents;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.contents);
        dest.writeString(this.nickname);
        this.flags.writeToParcel(dest, flags);
    }
    
    @Override
    public String toString() {
    	return "Options = {nickname: "+nickname+"  flags: "+flags+" }";
    }
}

