package com.crestron.txrxservice.wc.ipc;

import java.util.ArrayList;
import java.util.List;

import com.crestron.airmedia.canvas.channels.ipc.CanvasSourceTransaction;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSessionVideoSource;

import android.os.Parcel;
import android.os.Parcelable;

public class WC_Status implements Parcelable {
    private final int contents;
    public List<WC_UsbDevice> devices;

    public WC_Status() {
        this.contents = 0;
        this.devices = new ArrayList<WC_UsbDevice>();    
    }

    protected WC_Status(Parcel in) {
        this.contents = in.readInt();
        final int count = in.readInt();
        final ArrayList<WC_UsbDevice> devices = new ArrayList<WC_UsbDevice>(count);
        for (int i = 0; i < count; i++) {
            devices.add(i, WC_UsbDevice.CREATOR.createFromParcel(in));
        }
        this.devices = devices;
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
        int count = devices != null ? devices.size() : 0;
        dest.writeInt(count);
        for (WC_UsbDevice device : devices) {
            device.writeToParcel(dest, flags);
        }
    }
    
    public boolean isEqual(WC_Status rhs) {
    	return isEqual(this, rhs);
    }
    
    public static boolean isEqual(WC_Status lhs, WC_Status rhs) {
        if (lhs == rhs || lhs == null || rhs == null)
            return lhs == rhs;

        if ((lhs.devices != null && rhs.devices == null) || (lhs.devices == null && rhs.devices != null))
        	return false;
        
        if (lhs.devices == rhs.devices)
        	return true;
        
        // Both lhs.devices and rhs.devices are non-null
        if (lhs.devices.size() != rhs.devices.size())
            return false;

        // Assumes same order - must generate list in order for this to work
        for (int i = 0; i < lhs.devices.size(); i++) {
            if (lhs.devices.get(i).isNotEqual(rhs.devices.get(i)))
                return false;
        }

        return true;
    }
    
    public String toString()
    {
    	return "WC_Status = "+devices;
    }
}