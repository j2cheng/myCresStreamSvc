package com.crestron.txrxservice;

import android.app.Service;
import android.content.Intent;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import java.io.File;

public class CSIOService extends Service
{
    public static String TAG = "CSIOService";
    public native int csioTask(String internalStoragePath, String externalStoragePath);

    public CSIOService()
    {
        try
        {
            Log.i(TAG,"loading csio library: begin");
            System.loadLibrary("csio");
            Log.i(TAG,"loading csio library: end");
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        Log.i(TAG, "onStartCommand " + ", " + startId);
        new Thread(() -> {
            String internalStoragePath = getFilesDir().getAbsolutePath();
            String externalStoragePath = internalStoragePath;
            File externalStorage = getExternalFilesDir(null);
            String externalStorageState = Environment.getExternalStorageState(externalStorage);

            if(Environment.MEDIA_MOUNTED == externalStorageState)
            {
                externalStoragePath = externalStorage.getAbsolutePath();
            }

            Log.i(
                    TAG,
                    "csioTask: begin, "
                    + internalStoragePath
                    + ", " + externalStoragePath
                    + "(" + externalStorageState + ")");

            csioTask(internalStoragePath, externalStoragePath);
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
