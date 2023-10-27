package com.crestron.txrxservice;

import android.app.Application;
import android.content.Intent;
import android.util.Log;

import com.crestron.txrxservice.CSIOService;
import com.crestron.txrxservice.CresStreamCtrl;


public class LaunchApp extends Application
{
    public static String TAG = "TxRx LaunchApp";

	@Override
    public void onCreate()
    {
        super.onCreate();
        Log.i(TAG, "onCreate, begin");
        Log.i(TAG, "start CSS Service");
        startService(new Intent(this, CresStreamCtrl.class));
        Log.i(TAG, "start CSIO Service");
        startService(new Intent(this, CSIOService.class));
        Log.i(TAG, "startServices completed");
        Log.i(TAG, "onCreate, end");
	}
}
