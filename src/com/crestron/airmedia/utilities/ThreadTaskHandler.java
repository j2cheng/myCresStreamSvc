package com.crestron.airmedia.utilities;

import android.os.Build;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ThreadTaskHandler extends TaskHandler {
    private final String name_;
    private final Lock lock_ = new ReentrantLock();
    private ConditionVariable closed_;

    public ThreadTaskHandler(String name) {
        name_ = name;
    }

    @Override
    public void start(TimeSpan timeout) {
        TimeSpan start;

        synchronized (lock_) {
            if (handler() != null) return;

            if (VERBOSE) Log.v(TAG, name_ + ".start");

            ConditionVariable started = new ConditionVariable();

            Thread thread = new Thread(new Runnable() {
                private ConditionVariable mStarted;
                @Override
                public void run() {
                    ConditionVariable closed = new ConditionVariable();
                    try {
                        closed_ = closed;
                        Looper.prepare();
                        handler(new Handler());
                        if (VERBOSE) Log.v(TAG, name_ + ".thread  STARTED");
                        mStarted.open();
                        Looper.loop();
                        if (VERBOSE) Log.v(TAG, name_ + ".thread  EXITING");
                    } catch (Exception e) {
                        Log.e(TAG, name_ + ".thread  EXCEPTION  " + e);
                    } finally {
                        closed.open();
                        if (VERBOSE) Log.v(TAG, name_ + ".thread  COMPLETE");
                    }
                }
                public Runnable set(ConditionVariable s) { mStarted = s; return this; }
            }.set(started), "thread.task.handler." + name_);

            start = TimeSpan.now();

            thread.start();

            try {
                double milliseconds = timeout.totalMilliseconds();
                started.block(TimeSpan.toLong(milliseconds));
            } catch (Exception e) {
                Log.e(TAG, name_ + ".start  EXCEPTION  " + e);
            }
        }

        if (VERBOSE) Log.v(TAG, name_ + ".start  COMPLETE  timespan= " + TimeSpan.now().subtract(start).toString());
    }

    @Override
    public void stop(TimeSpan timeout) {
        TimeSpan start;

        synchronized (lock_) {
            try {
                if (handler() == null) return;

                start = TimeSpan.now();

                if (VERBOSE) Log.v(TAG, name_ + ".stop");

                Looper looper = handler().getLooper();

                //if (looper.getThread().getId() != Thread.currentThread().getId()) {
                //    looper.quit();
                //} else {
                //}

                looper.quit();

                try {
                    closed_.block(TimeSpan.toLong(timeout.totalMilliseconds()));
                    closed_ = null;
                } catch (Exception e) {
                    Log.e(TAG, name_ + ".stop  EXCEPTION  " + e);
                }
            } finally {
                handler(null);
            }
        }

        if (VERBOSE) Log.v(TAG, name_ + ".stop  COMPLETE  timespan= " + TimeSpan.now().subtract(start).toString());
    }

    @Override
    public boolean post(Runnable r) {
        synchronized (lock_) { return super.post(r); }
    }

}
