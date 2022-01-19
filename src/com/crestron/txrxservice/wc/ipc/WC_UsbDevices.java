package com.crestron.txrxservice.wc.ipc;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;


public class WC_UsbDevices implements Parcelable {
    private final int contents;
    public final List<WC_VideoFormat> videoFormats;
    public final List<WC_AudioFormat> audioFormats;
    public final List<WC_UsbDevice> devices;

    public WC_UsbDevices() {
        this(new ArrayList<WC_VideoFormat>(), new ArrayList<WC_AudioFormat>(), new ArrayList<WC_UsbDevice>());
    }

    public WC_UsbDevices(List<WC_VideoFormat> videoFormats, List<WC_AudioFormat> audioFormats, List<WC_UsbDevice> devices) {
        this.contents = 0;
        if (videoFormats != null) {
            this.videoFormats = videoFormats;
        } else {
            this.videoFormats = new ArrayList<WC_VideoFormat>();
        }
        if (audioFormats != null) {
            this.audioFormats = audioFormats;
        } else {
            this.audioFormats = new ArrayList<WC_AudioFormat>();
        }
        if (devices != null) {
            this.devices = devices;
        } else {
            this.devices = new ArrayList<WC_UsbDevice>();
        }
    }

    protected WC_UsbDevices(Parcel in) {
        this.contents = in.readInt();
        
        int count = in.readInt();
        final ArrayList<WC_VideoFormat> videoFormats = new ArrayList<WC_VideoFormat>(count);
        for (int i = 0; i < count; i++) {
        	videoFormats.add(i, WC_VideoFormat.CREATOR.createFromParcel(in));
        }
        this.videoFormats = videoFormats;
        
        count = in.readInt();
        final ArrayList<WC_AudioFormat> audioFormats = new ArrayList<WC_AudioFormat>(count);
        for (int i = 0; i < count; i++) {
        	audioFormats.add(i, WC_AudioFormat.CREATOR.createFromParcel(in));
        }
        this.audioFormats = audioFormats;
        
        count = in.readInt();
        final ArrayList<WC_UsbDevice> devices = new ArrayList<WC_UsbDevice>(count);
        for (int i = 0; i < count; i++) {
            devices.add(i, WC_UsbDevice.CREATOR.createFromParcel(in));
        }
        this.devices = devices;
    }

    public static final Creator<WC_UsbDevices> CREATOR = new Creator<WC_UsbDevices>() {
        @Override
        public WC_UsbDevices createFromParcel(Parcel in) {
            return new WC_UsbDevices(in);
        }

        @Override
        public WC_UsbDevices[] newArray(int size) {
            return new WC_UsbDevices[size];
        }
    };

    @Override
    public int describeContents() {
        return contents;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.contents);
        int count = videoFormats != null ? videoFormats.size() : 0;
        dest.writeInt(count);
        for (WC_VideoFormat videoFormat : videoFormats) {
            videoFormat.writeToParcel(dest, flags);
        }
        count = audioFormats != null ? audioFormats.size() : 0;
        dest.writeInt(count);
        for (WC_AudioFormat audioFormat : audioFormats) {
            audioFormat.writeToParcel(dest, flags);
        }
        count = devices != null ? devices.size() : 0;
        dest.writeInt(count);
        for (WC_UsbDevice device : devices) {
            device.writeToParcel(dest, flags);
        }
    }

    public boolean isEqual(WC_UsbDevices rhs) {
        return isEqual(this, rhs);
    }

    public static boolean isEqual(WC_UsbDevices lhs, WC_UsbDevices rhs) {
        if (lhs == rhs || lhs == null || rhs == null)
            return lhs == rhs;

        if ((lhs.videoFormats != null && rhs.videoFormats == null) || (lhs.videoFormats == null && rhs.videoFormats != null))
            return false;

        if ((lhs.audioFormats != null && rhs.audioFormats == null) || (lhs.audioFormats == null && rhs.audioFormats != null))
            return false;
        
        if ((lhs.devices != null && rhs.devices == null) || (lhs.devices == null && rhs.devices != null))
            return false;

        if (lhs.videoFormats == rhs.videoFormats && lhs.devices == rhs.devices)
            return true;

        // Both lhs.videoFormats and rhs.videoFormats are non-null
        if (lhs.videoFormats.size() != rhs.videoFormats.size())
            return false;
        
        // Both lhs.audioFormats and rhs.audioFormats are non-null
        if (lhs.videoFormats.size() != rhs.audioFormats.size())
            return false;
        
        // Both lhs.devices and rhs.devices are non-null
        if (lhs.devices.size() != rhs.devices.size())
            return false;

        // Assumes same order - must generate list in sorted order of formats for this to work
        for (int i = 0; i < lhs.videoFormats.size(); i++) {
            if (!lhs.videoFormats.get(i).isEqual(rhs.videoFormats.get(i)))
            	return false;
        }
        
        // Assumes same order - must generate list in sorted order of formats for this to work
        for (int i = 0; i < lhs.audioFormats.size(); i++) {
            if (!lhs.audioFormats.get(i).isEqual(rhs.audioFormats.get(i)))
            	return false;
        }
            
        // Assumes same order - must generate list in order for this to work
        for (int i = 0; i < lhs.devices.size(); i++) {
            if (lhs.devices.get(i).isNotEqual(rhs.devices.get(i)))
                return false;
        }

        return true;
    }

    public String toString()
    {
        return "VideoFormat: "+((videoFormats!=null)?videoFormats:"null")+
        		", AudioFormat: "+((audioFormats!=null)?audioFormats:"null")+
        		", WC_UsbDevices:"+((devices!=null)?devices:"null");
    }
}
