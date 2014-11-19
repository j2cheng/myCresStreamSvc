package com.crestron.txrxservice;

import java.io.IOException;

import android.app.Service;
import android.os.Binder;
import android.os.IBinder;
import android.os.Bundle;
import android.util.Log;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;


public class ServiceLauncherActivity extends BroadcastReceiver{
        String TAG = "TxRx-Launcher";
    @Override
        public void onReceive(Context context, Intent intent) {
            /****** For Start Activity *****/
            if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)){
                Log.d(TAG, "Received Boot completed!!!");
                Intent i = new Intent(context, CresStreamCtrl.class);  
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(i);  

                /***** For start Service  ****/
                //    Intent myIntent = new Intent(context, ServiceClassName.class);
                //context.startService(myIntent);
            }   }
}
