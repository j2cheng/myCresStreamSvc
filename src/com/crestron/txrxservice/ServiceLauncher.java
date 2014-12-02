package com.crestron.txrxservice;

import java.io.IOException;

import android.app.Service;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.content.Context;
import android.content.Intent;

public class ServiceLauncher extends Service {
    String TAG = "TxRx-Launcher";
    boolean isRunning = false;

    public ServiceLauncher() throws IOException
    {
    }

    @Override
        public IBinder onBind(Intent intent) {
            return null;
        }

    @Override
        public int onStartCommand (Intent intent, int flags, int startId) {
            // TODO Auto-generated method stub
            Log.d(TAG,"S: ServiceLauncher Started :" + isRunning );
            return 0;
        }

    @Override
        public void onCreate ()
        {
            // called when service is created
            super.onCreate();
            isRunning = true;
            Log.d(TAG,"S: ServiceLauncher Started :" + isRunning );
            Intent dIntent = new Intent(getBaseContext(), CresStreamCtrl.class);
            dIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getApplication().startActivity(dIntent);
        }
}
