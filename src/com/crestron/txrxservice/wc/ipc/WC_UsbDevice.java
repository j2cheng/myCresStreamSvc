package com.crestron.txrxservice.wc.ipc;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.HashMap;
import java.util.Map;

public class WC_UsbDevice implements Parcelable {
    private final int contents;
    public final String deviceId;
    public final String usbPort;
    public final String deviceName;
	public final boolean hasVideo;
	public final boolean hasAudio;
	public final Map<String, String> properties;

    public WC_UsbDevice() {
        this("", "", "", false, false, new HashMap<String, String>());
    }

    public WC_UsbDevice(
            String deviceId,
            String port,
            String name,
            boolean hasVideo,
            boolean hasAudio,
            Map<String, String> properties
    ) {
        this.contents = 0;
        this.deviceId = deviceId;
        this.usbPort = port;
        if (name != null)
        {
            this.deviceName = name.trim();
        } else {
            this.deviceName = "Unknown";
        }
		this.hasVideo = hasVideo;
		this.hasAudio = hasAudio;
        this.properties = properties;
    }

    protected WC_UsbDevice(Parcel in) {
        this.contents = in.readInt();
        this.deviceId = in.readString();
        this.usbPort = in.readString();
		this.deviceName = in.readString();
		this.hasVideo = in.readInt() != 0;
		this.hasAudio = in.readInt() != 0;
        final int count = in.readInt();
        final Map<String, String> properties = new HashMap<String, String>(count);
        for (int i = 0; i < count; i++) {
            final String key = in.readString();
            final String value = in.readString();
            properties.put(key, value);
        }
        this.properties = properties;
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
        dest.writeString(this.deviceId);
        dest.writeString(this.usbPort);
		dest.writeString(this.deviceName);
        dest.writeInt(this.hasVideo ? -1 : 0);
        dest.writeInt(this.hasAudio ? -1 : 0);
        dest.writeInt(this.properties.size());
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            dest.writeString(entry.getKey());
            dest.writeString(entry.getValue());
        }
    }
    
    public boolean isEqual(WC_UsbDevice rhs) {
        return isEqual(this, rhs);
    }

    public boolean isNotEqual(WC_UsbDevice rhs) {
        return !isEqual(this, rhs);
    }

    public static boolean isEqual(Map<String, String> lhs, Map<String, String> rhs)
    {
    	if (lhs == rhs)
    		return true;
    	if (lhs == null || rhs == null)
    		return false;
    	if (lhs.size() != rhs.size())
    		return false;
        for (Map.Entry<String,String> entry : lhs.entrySet()) {
        	// find key,value if it exists in rhs 
        	String rhsValue = rhs.get(entry.getKey());
        	// if it does not exist return false
        	if (rhsValue == null)
        		return false;
        	// if it does exists but is not equal to lhs value return false
        	if (!rhs.equals(entry.getValue()))
        		return false;
        }
        return true;
    }
    
    public static boolean isEqual(WC_UsbDevice lhs, WC_UsbDevice rhs) {
        return lhs == rhs || !(lhs == null || rhs == null)
                && lhs.usbPort.equals(rhs.usbPort)
                && lhs.deviceName.equals(rhs.deviceName)
                && lhs.hasVideo == rhs.hasVideo
                && lhs.hasAudio == rhs.hasAudio
                && isEqual(lhs.properties, rhs.properties);
    }

    public String toString() {
        return "{usbPort="+usbPort+" deviceName="+deviceName+" hasVideo="+hasVideo+" hasAudio="+hasAudio+ 
        		" properties=["+properties+"]}";
    }
}