package com.crestron.txrxservice.wc.ipc;

import com.crestron.airmedia.receiver.m360.ipc.AirMediaSessionVideoSource;

import android.os.Parcel;
import android.os.Parcelable;

public class WC_UsbDevice implements Parcelable {
    private final int contents;
    public final String usbPort;
    public final String deviceName;
	public final boolean hasVideo;
	public final boolean hasAudio;

    public WC_UsbDevice() {
        this(null, null, false, false);
    }

    public WC_UsbDevice(String port, String name, boolean hasVideo, boolean hasAudio) {
        this.contents = 0;
        this.usbPort = port;
        this.deviceName = name;
		this.hasVideo = hasVideo;
		this.hasAudio = hasAudio;
    }

    protected WC_UsbDevice(Parcel in) {
        this.contents = in.readInt();
        this.usbPort = in.readString();
		this.deviceName = in.readString();
		this.hasVideo = in.readInt() != 0;
		this.hasAudio = in.readInt() != 0;
    }

    public static final Creator<WC_UsbDevice> CREATOR = new Creator<WC_UsbDevice>() {
        @Override
        public WC_UsbDevice createFromParcel(Parcel in) {
            return new WC_UsbDevice(in);
        }

        @Override
        public WC_UsbDevice[] newArray(int size) {
            return new WC_UsbDevice[size];
        }
    };

    @Override
    public int describeContents() {
        return contents;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.contents);
        dest.writeString(this.usbPort);
		dest.writeString(this.deviceName);
        dest.writeInt(this.hasVideo ? -1 : 0);
        dest.writeInt(this.hasAudio ? -1 : 0);
    }
    
    public boolean isEqual(WC_UsbDevice rhs) {
        return isEqual(this, rhs);
    }

    public boolean isNotEqual(WC_UsbDevice rhs) {
        return !isEqual(this, rhs);
    }

    public static boolean isEqual(WC_UsbDevice lhs, WC_UsbDevice rhs) {
        return lhs == rhs || !(lhs == null || rhs == null)
                && lhs.usbPort.equals(rhs.usbPort)
                && lhs.deviceName.equals(rhs.deviceName)
                && lhs.hasVideo == rhs.hasVideo
                && lhs.hasAudio == rhs.hasAudio;
    }

    public String toString() {
        return "{ usbPort="+usbPort+" deviceName="+deviceName+" hasVideo="+hasVideo+" hasAudio="+hasAudio+" }";
    }
}