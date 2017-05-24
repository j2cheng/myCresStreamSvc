package com.crestron.airmedia.utilities.delegates;

import android.util.Log;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class MulticastMessageDelegate<SOURCE, T> {
    public static class Observer<SOURCE, T> {
        public void onEvent(SOURCE source, T message) { }
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

    public void raise(SOURCE source, T message) {
        List<Observer<SOURCE, T>> observers;

        synchronized (sync_){
            observers = new ArrayList<Observer<SOURCE, T>>(observers_);
        }

        for (Observer<SOURCE, T> observer : observers) {
            try {
                observer.onEvent(source, message);
            } catch (Exception e) {
                Log.e(TAG, "raise  source= " + source + "  message= " + message + "  EXCEPTION  " + e);
            }
        }
    }
}

