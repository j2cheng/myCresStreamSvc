package com.crestron.txrxservice;

import android.app.Application;
import android.content.Intent;

public class LaunchApp extends Application {
	@Override
    public void onCreate() {
		startService(new Intent(this, CresStreamCtrl.class));
	}
}
