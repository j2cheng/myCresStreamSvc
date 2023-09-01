package com.crestron.txrxservice;

import android.app.Service;
import android.content.Intent;
import android.util.Log;
import android.os.IBinder;

public class CSIOService extends Service
{
    public static String TAG = "CSIOService";
    public native int csioTask();

    static
    {
        Log.i(TAG,"loading csio library: begin");
        System.loadLibrary("csio");
        Log.i(TAG,"loading csio library: end");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        Log.i(TAG, "onStartCommand " + ", " + startId);
        new Thread(() -> {
            Log.i(TAG, "csioTask: begin");
            csioTask();
            Log.i(TAG, "csioTask: end");
        }).start();
        return START_STICKY;
    }

    @Override
    public void onDestroy()
    {
        Log.i(TAG, "onDestroy");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        Log.i(TAG, "onBind: " + intent.toString());
        return null;
    }
}
