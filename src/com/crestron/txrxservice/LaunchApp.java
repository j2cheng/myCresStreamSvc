package com.crestron.txrxservice;

import android.app.Application;
import android.content.Intent;
import android.util.Log;
import com.crestron.txrxservice.RuntimePermission;

public class LaunchApp extends Application
{
    public static String TAG = "TxRx LaunchApp";

	@Override
    public void onCreate()
    {
        Log.i(TAG, "super.onCreate");
        super.onCreate();
        Log.i(TAG, "onCreate, begin");
        Intent startRuntimePermission = 
            new Intent(getApplicationContext(), RuntimePermission.class);

        startRuntimePermission.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(startRuntimePermission);
        Log.i(TAG, "onCreate, end");
	}
}
