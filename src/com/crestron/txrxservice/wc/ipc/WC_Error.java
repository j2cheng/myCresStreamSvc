package com.crestron.txrxservice.wc.ipc;

import android.os.Parcel;
import android.os.Parcelable;


public class WC_Error implements Parcelable {
    private final int contents;
    public int module;
    public int code;
    public String message;

    public WC_Error() {
        this(0, 0, "");
    }

    public WC_Error(int module, int code, String message) {
        this.contents = 0;
        this.module = module;
        this.code = code;
        this.message = message;
    }

    protected WC_Error(Parcel in) {
        this.contents = in.readInt();
        this.module = in.readInt();
        this.code = in.readInt();
        this.message = in.readString();
    }

    public boolean isOK() { return code == 0; }

    public boolean isError() { return !isOK(); }

    public static final Creator<WC_Error> CREATOR = new Creator<WC_Error>() {
        @Override
        public WC_Error createFromParcel(Parcel in) {
            return new WC_Error(in);
        }

        @Override
        public WC_Error[] newArray(int size) {
            return new WC_Error[size];
        }
    };

    @Override
    public int describeContents() {
        return contents;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.contents);
        dest.writeInt(this.module);
        dest.writeInt(this.code);
        dest.writeString(this.message);
    }
    
    @Override
    public String toString() {
    	return "Error = { "
                + "module: " + module
                + "code: " + code
                + "message: " + message
                + " }";
    }
}