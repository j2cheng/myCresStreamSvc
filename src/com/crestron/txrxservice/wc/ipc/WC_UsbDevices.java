package com.crestron.txrxservice.wc.ipc;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;


public class WC_UsbDevices implements Parcelable {
    private final int contents;
    public final List<WC_UsbDevice> devices;

    public WC_UsbDevices() {
        this(new ArrayList<WC_UsbDevice>());
    }

    public WC_UsbDevices(List<WC_UsbDevice> devices) {
        this.contents = 0;
        this.devices = devices;
    }

    protected WC_UsbDevices(Parcel in) {
        this.contents = in.readInt();
        final int count = in.readInt();
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
        int count = devices != null ? devices.size() : 0;
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
        return "WC_UsbDevices = "+devices;
    }
}
