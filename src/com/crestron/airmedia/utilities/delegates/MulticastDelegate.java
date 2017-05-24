package com.crestron.airmedia.utilities.delegates;

import android.util.Log;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class MulticastDelegate<SOURCE> {
    public static class Observer<SOURCE> {
        public void onEvent(SOURCE source) { }
    }

    private static final String TAG = "MulticastDelegate";
    private final Object sync_ = new Object();
    private final List<Observer<SOURCE>> observers_ = new LinkedList<Observer<SOURCE>>();

    public void register(Observer<SOURCE> observer) {
        synchronized (sync_){
            observers_.add(observer);
        }
    }

    public void unregister(Observer<SOURCE> observer) {
        synchronized (sync_){
            observers_.remove(observer);
        }
    }

    public void unregisterAll() {
        synchronized (sync_){
            observers_.clear();
        }
    }

    public void raise(SOURCE source) {
        List<Observer<SOURCE>> observers;

        synchronized (sync_){
            observers = new ArrayList<Observer<SOURCE>>(observers_);
        }

        for (Observer<SOURCE> observer : observers) {
            try {
                observer.onEvent(source);
            } catch (Exception e) {
                Log.e(TAG, "raise  source= " + source + "  EXCEPTION  " + e);
            }
        }
    }
}
