package com.crestron.txrxservice;

import android.app.Application;
import android.content.Intent;
import com.crestron.txrxservice.ProductSpecific;

public class LaunchApp extends Application {
	@Override
    public void onCreate() {
		// Need to use Product Specific class because startForegroundService does not exist on older API
		ProductSpecific.startForegroundService(this, new Intent(this, CresStreamCtrl.class));
	}
}
