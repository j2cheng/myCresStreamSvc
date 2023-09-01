package com.crestron.txrxservice;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import com.crestron.txrxservice.CSIOService;
import com.crestron.txrxservice.CresStreamCtrl;
import com.crestron.txrxservice.ProductSpecific;

public class RuntimePermission extends Activity 
{
    public static String TAG = "TxRx RuntimePermActivity";
    public final static int OVERLAY_PERMISSION_REQUEST_CODE = 1000000;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                requestOverlayPermission();
            }
            else startServices();
        }
    }

    @Override
    protected void onStart()
    {
        Log.i(TAG, "super.onStart");
        super.onStart();
        Log.i(TAG, "onStart");
    }

    @Override
    protected void onStop()
    {
        Log.i(TAG, "super.onStop");
        super.onStop();
        Log.i(TAG, "onStop");
    }

    @Override
    protected void onRestart()
    {
        Log.i(TAG, "supert.onRestart");
        super.onRestart();
        Log.i(TAG, "onRestart");
    }

    @Override
    protected void onResume()
    {
        Log.i(TAG, "super.onResume");
        super.onResume();
        Log.i(TAG, "onResume");
    }

    public void requestOverlayPermission()
    {
        Log.i(TAG, "requestOverlayPermission");
        Intent intent = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getApplicationContext().getPackageName()));
        startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        Log.i(TAG,
                "onActivityResult requestCode: " + String.valueOf(requestCode)
                + ", resultCode: " + String.valueOf(resultCode));
        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE) {
            startServices();
        }
    }

    protected void startServices()
    {
        Log.i(TAG, "start CSS Service");
        startService(new Intent(this, CresStreamCtrl.class));
        Log.i(TAG, "start CSIO Service");
        startService(new Intent(this, CSIOService.class));
        Log.i(TAG, "startServices completed");
    }
}

