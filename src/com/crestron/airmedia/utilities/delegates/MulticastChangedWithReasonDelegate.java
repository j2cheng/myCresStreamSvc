package com.crestron.airmedia.utilities.delegates;

import android.util.Log;

import com.crestron.airmedia.utilities.Common;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class MulticastChangedWithReasonDelegate<SOURCE, T, REASON> {
    public static class Observer<SOURCE, T, REASON> {
        public void onEvent(SOURCE source, T from, T to, REASON reason) { }
    }

    private static final String TAG = "MulticastDelegate";
    private final Object sync_ = new Object();
    private final List<Observer<SOURCE, T, REASON>> observers_ = new LinkedList<Observer<SOURCE, T, REASON>>();

    public void register(Observer<SOURCE, T, REASON> observer) {
        synchronized (sync_){
            observers_.add(observer);
        }
    }

    public void unregister(Observer<SOURCE, T, REASON> observer) {
        synchronized (sync_){
            observers_.remove(observer);
        }
    }

    public void unregisterAll() {
        synchronized (sync_){
            observers_.clear();
        }
    }

    public void raise(SOURCE source, T from, T to, REASON reason) {
        List<Observer<SOURCE, T, REASON>> observers;

        synchronized (sync_){
            observers = new ArrayList<Observer<SOURCE, T, REASON>>(observers_);
        }

        for (Observer<SOURCE, T, REASON> observer : observers) {
            try {
                observer.onEvent(source, from, to, reason);
            } catch (Exception e) {
                Common.Logging.e(TAG, "raise  source= " + source + "  from= " + from + "  to= " + to + "  reason= " + reason + "  EXCEPTION  " + e + "  " + Log.getStackTraceString(e));
            }
        }
    }
}
