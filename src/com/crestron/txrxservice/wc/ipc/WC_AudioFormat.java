package com.crestron.txrxservice.wc.ipc;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

public class WC_AudioFormat implements Parcelable {
    private final int contents;
    public int sampleRate;
    public int channels;
	public String sampleFormat;

    public WC_AudioFormat() {
        this(0, 0, "");
    }

    public WC_AudioFormat(int sampleRate, int channels, String sampleFormat) {
        this.contents = 0;
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.sampleFormat = sampleFormat;
    }

    protected WC_AudioFormat(Parcel in) {
        this.contents = in.readInt();
        this.sampleRate = in.readInt();
        this.channels = in.readInt();
        this.sampleFormat = in.readString();
    }

    public static final Creator<WC_AudioFormat> CREATOR = new Creator<WC_AudioFormat>() {
        @Override
        public WC_AudioFormat createFromParcel(Parcel in) {
            return new WC_AudioFormat(in);
        }

        @Override
        public WC_AudioFormat[] newArray(int size) {
            return new WC_AudioFormat[size];
        }
    };

    @Override
    public int describeContents() {
        return contents;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.contents);
        dest.writeInt(this.sampleRate);
        dest.writeInt(this.channels);
        dest.writeString(this.sampleFormat);
    }
    
    public boolean isEqual(WC_AudioFormat rhs) {
        return this.sampleRate==rhs.sampleRate && this.channels==rhs.channels && this.sampleFormat.equals(rhs.sampleFormat);
    }
    
    @Override
    public String toString() {
    	return "AudioFormat: sampleRate:"+sampleRate+" channels:"+channels+" sampleFormat:"+sampleFormat;
    }
}

