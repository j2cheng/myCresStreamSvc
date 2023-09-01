package com.crestron.txrxservice;

import java.util.List;
import java.util.Collections;
import android.util.Log;

public class PeripheralManager
{
    public static PeripheralManager mInstance;
    public static final int PER_USB_20 = 200;
    public static final int PER_USB_30 = 300;
    public static final int PER_HDMI_IN = 1;
    public static final int PER_HDMI_OUT = 2;
    static String TAG = "KONA PeripheralMgr";

    public static PeripheralManager instance()
    {
        if (mInstance == null) mInstance = new PeripheralManager();
        return mInstance;
    }

    public int enableUsbStatus(int id, boolean enable)
    {
        Log.i(TAG, "enableUsbStatus: " + id + ", " + enable);
        return 0;
    }

    public String getUsbPeripheralName(int id)
    {
        Log.i(TAG, "getUsbPeripheralName: " + id);
        return "undefined";
    }

    public int getStatus(int id)
    {
        return 0;
    }

    public List<String> getUsbVideoDevices(int id)
    {
        Log.i(TAG, "getUsbVideoDevices: " + id);
        return Collections.<String>emptyList();
    }

    public List<String> getUsbAudioDevices(int id)
    {
        Log.i(TAG, "getUsbAudioDevices: " + id);
        return Collections.<String>emptyList();
    }

    public List<PeripheralUsbDevice> getDevices(int id)
    {
        Log.i(TAG, "getDevices: " + id);
        return Collections.<PeripheralUsbDevice>emptyList();
    }

    public void addStatusListener(int id, StatusChangeListener listener, Object obj)
    {
        Log.i(TAG, "addStatusListener: " + id);
    }
}
