package com.crestron.airmedia.utilities;

import android.os.Handler;
import android.util.Log;

public class TaskHandler {
    protected static final boolean VERBOSE = false;
    protected static final String TAG = "Task.Handler";
    protected static final TimeSpan SERVICE_TIMEOUT = TimeSpan.fromSeconds(5.0);
    private Handler handler_ = null;

    public TaskHandler() { }

    public TaskHandler(Handler handler) { handler_ = handler; }

    public Handler handler() { return handler_; }

    public void handler(Handler value) { handler_ = value; }

    public boolean isTaskThread() {
        Handler handler = handler();
        return handler != null && handler.getLooper().getThread().getId() == Thread.currentThread().getId();
    }

    public void start(TimeSpan timeout) { }
    public void start() { start(SERVICE_TIMEOUT); }

    public void stop(TimeSpan timeout) { }
    public void stop() { stop(SERVICE_TIMEOUT); }

    public boolean post(Runnable r) {
        try {
            Handler handler = handler();
            return handler != null && handler.post(r);
        } catch (Exception e) {
            Log.e(TAG, "post  EXCEPTION  " + e);
        }

        return false;
    }
}

