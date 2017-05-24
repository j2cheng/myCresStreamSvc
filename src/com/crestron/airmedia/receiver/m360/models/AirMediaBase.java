package com.crestron.airmedia.receiver.m360.models;

import android.util.Log;

import com.crestron.airmedia.utilities.TaskScheduler;
import com.crestron.airmedia.utilities.TimeSpan;
import com.crestron.airmedia.utilities.delegates.Observer;

/**
 * Created by rlopresti on 5/4/2017.
 */
abstract class AirMediaBase {
    private static final String TAG = "airmedia.base";
    static final TimeSpan DefaultTimeout = TimeSpan.fromSeconds(60.0);
    private final TaskScheduler scheduler_;
    TaskScheduler scheduler() { return scheduler_; }

    AirMediaBase(TaskScheduler scheduler) {
        scheduler_ = scheduler;
    }

    void queue(String name, TimeSpan timeout, Runnable task) {
        Log.i(TAG, name + "  timeout= " + timeout.toString());
        TimeSpan start = TimeSpan.now();
        boolean isCompleted = false;
        try {
            isCompleted = scheduler().queue(timeout, task);
        } catch (Exception e) {
            Log.e(TAG, name + "  timeout= " + timeout.toString() + "  EXCEPTION  " + e);
        } finally {
            Log.i(TAG, name + "  timeout= " + timeout.toString() + "  COMPLETE= " + isCompleted + "  " + TimeSpan.getDelta(start));
        }
    }

    <T> void queue(T source, String name, Observer<T> observer, TaskScheduler.ObservableTask<T> task) {
        try {
            boolean isQueued = scheduler().queue(observer, task);
            if (!isQueued) throw new Exception("failed to queue " + name + " task");
        } catch (Exception e) {
            scheduler().raiseError(observer, source, "AirMedia", -1002, name + "  EXCEPTION  " + e);
        }
    }
}
