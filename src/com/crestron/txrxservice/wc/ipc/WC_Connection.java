package com.crestron.txrxservice.wc.ipc;

import android.os.Parcel;
import android.os.Parcelable;

public class WC_Connection implements Parcelable {
    private final int contents;
    public final int id;
    public final String url;
	public final String certificate;
	public final String key;

    public WC_Connection() {
        this(0, null, null, null);
    }

    public WC_Connection(int id, String url, String cert, String key) {
        this.contents = 0;
        this.id = id;
        this.url = url;
		this.certificate = cert;
		this.key = key;
    }

    protected WC_Connection(Parcel in) {
        this.contents = in.readInt();
        this.id = in.readInt();
		this.url = in.readString();
		this.certificate = in.readString();
		this.key = in.readString();
    }

    public static final Creator<WC_Connection> CREATOR = new Creator<WC_Connection>() {
        @Override
        public WC_Connection createFromParcel(Parcel in) {
            return new WC_Connection(in);
        }

        @Override
        public WC_Connection[] newArray(int size) {
            return new WC_Connection[size];
        }
    };

    @Override
    public int describeContents() {
        return contents;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.contents);
        dest.writeInt(this.id);
		dest.writeString(this.url);
		dest.writeString(this.certificate);
		dest.writeString(this.key);
    }

    public String toString() {
        return "URL="+url+"\n Certificate="+certificate+"\n Key="+key;
    }
}