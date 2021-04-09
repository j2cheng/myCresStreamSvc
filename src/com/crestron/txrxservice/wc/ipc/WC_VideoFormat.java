package com.crestron.txrxservice.wc.ipc;

import android.os.Parcel;
import android.os.Parcelable;

public class WC_VideoFormat implements Parcelable {
    private final int contents;
    public int width;
    public int height;
    public int fps;

    public WC_VideoFormat() {
        this(0, 0, 0);
    }

    public WC_VideoFormat(int width, int height, int fps) {
        this.contents = 0;
        this.width = width;
        this.height = height;
        this.fps = fps;
    }

    protected WC_VideoFormat(Parcel in) {
        this.contents = in.readInt();
        this.width = in.readInt();
        this.height = in.readInt();
        this.fps = in.readInt();
    }

    public static final Creator<WC_VideoFormat> CREATOR = new Creator<WC_VideoFormat>() {
        @Override
        public WC_VideoFormat createFromParcel(Parcel in) {
            return new WC_VideoFormat(in);
        }

        @Override
        public WC_VideoFormat[] newArray(int size) {
            return new WC_VideoFormat[size];
        }
    };

    @Override
    public int describeContents() {
        return contents;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.contents);
        dest.writeInt(this.width);
        dest.writeInt(this.height);
        dest.writeInt(this.fps);
    }
    
    public boolean isEqual(WC_VideoFormat rhs) {
        return this.width==rhs.width && this.height==rhs.height && this.fps==rhs.fps;
    }
    
    @Override
    public String toString() {
    	return "VideoFormat: "+width+"x"+height+"@"+fps;
    }
}

