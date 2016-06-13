package com.crestron.txrxservice;

import android.app.Application;
import android.content.Intent;

public class LaunchApp extends Application {
	@Override
    public void onCreate() {
		// CresStreamSvc is now an activity not a service so it must be launched like one
//		startService(new Intent(this, CresStreamCtrl.class));
		Intent i = new Intent(this, CresStreamCtrl.class);
		i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);	// this is needed to spawn activity from application
		startActivity(i);
	}
}
