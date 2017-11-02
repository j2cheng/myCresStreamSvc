package com.crestron.airmedia.utilities.delegates;

import android.util.Log;

import com.crestron.airmedia.utilities.Common;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class MulticastChangedDelegate<SOURCE, T> {
    public static class Observer<SOURCE, T> {
        public void onEvent(SOURCE source, T from, T to) { }
    }

    private static final String TAG = "MulticastDelegate";
    private final Object sync_ = new Object();
    private final List<Observer<SOURCE, T>> observers_ = new LinkedList<Observer<SOURCE, T>>();

    public void register(Observer<SOURCE, T> observer) {
        synchronized (sync_){
            observers_.add(observer);
        }
    }

    public void unregister(Observer<SOURCE, T> observer) {
        synchronized (sync_){
            observers_.remove(observer);
        }
    }

    public void unregisterAll() {
        synchronized (sync_){
            observers_.clear();
        }
    }

    public void raise(SOURCE source, T from, T to) {
        List<Observer<SOURCE, T>> observers;

        synchronized (sync_){
            observers = new ArrayList<Observer<SOURCE, T>>(observers_);
        }

        for (Observer<SOURCE, T> observer : observers) {
            try {
                observer.onEvent(source, from, to);
            } catch (Exception e) {
                Common.Logging.e(TAG, "raise  source= " + source + "  from= " + from + "  to= " + to + "  EXCEPTION  " + e + "  " + Log.getStackTraceString(e));
            }
        }
    }
}

