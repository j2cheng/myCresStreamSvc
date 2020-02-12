package com.crestron.txrxservice.canvas;

import com.crestron.airmedia.utilities.Common;
import com.crestron.airmedia.utilities.TaskScheduler;
import com.crestron.airmedia.utilities.TimeSpan;

import android.util.Log;

public class Scheduler extends TaskScheduler
{
    public static final String TAG = "Scheduler"; 

	public Scheduler(String name) {
		super(name);
	}
	
	boolean queue(String tag, String workName, TimeSpan timeout, Runnable task) {
		Common.Logging.i(tag, workName + "  timeout= " + timeout.toString());
		TimeSpan start = TimeSpan.now();
		boolean isCompleted = false;
		try {
			isCompleted = queue(timeout, task);
		} catch (Exception e) {
			Common.Logging.e(tag, workName + "  timeout= " + timeout.toString() + "  EXCEPTION  " + e + "  " + Log.getStackTraceString(e));
		} finally {
			Common.Logging.i(tag, workName + "  timeout= " + timeout.toString() + "  COMPLETE= " + isCompleted + "  " + TimeSpan.getDelta(start));
		}
		return isCompleted;
	}
}
