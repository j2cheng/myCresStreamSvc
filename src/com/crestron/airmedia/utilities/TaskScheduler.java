package com.crestron.airmedia.utilities;

import android.os.ConditionVariable;

import com.crestron.airmedia.utilities.delegates.MulticastChangedDelegate;
import com.crestron.airmedia.utilities.delegates.MulticastDelegate;
import com.crestron.airmedia.utilities.delegates.MulticastMessageDelegate;
import com.crestron.airmedia.utilities.delegates.Observer;
import com.crestron.airmedia.utilities.delegates.ObserverBase;
import com.crestron.airmedia.utilities.delegates.ObserverResult;

public class TaskScheduler {

    private final TaskHandler work_;
    private final TaskHandler events_;

    public TaskScheduler(String name) {
        this(new ThreadTaskHandler("tasks." + name + ".main"), new ThreadTaskHandler("tasks." + name + ".event"));
    }

    public TaskScheduler(ThreadTaskHandler work, ThreadTaskHandler events) {
        work_  = work;
        events_ = events;

        work_.start();
        events_.start();
    }

    public boolean isWorkerThread() {
        return work_.isTaskThread();
    }

    public boolean isEventThread() {
        return events_.isTaskThread();
    }

    public void close() {
        work_.stop();
        events_.stop();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// WORK
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public boolean queue(Runnable r) {
        return work_.post(r);
    }

    public boolean queue(TimeSpan timeout, Runnable r) {
        ConditionVariable completed = new ConditionVariable();

        boolean isQueued = queue(new Runnable() {
            private ConditionVariable c_;
            private Runnable r_;
            @Override public void run() { try { r_.run(); } finally { c_.open(); } }
            public Runnable set(Runnable t, ConditionVariable c) { r_ = t; c_ = c; return this; }
        }.set(r, completed));

        return isQueued && completed.block(TimeSpan.toLong(timeout.totalMilliseconds()));
    }

    public interface ObservableTask<T> {
        void run(Observer<T> value);
    }

    public <T> boolean queue(Observer<T> o, ObservableTask<T> t) {
        boolean isQueued = queue(new Runnable() {
            private ObservableTask<T> t_;
            private Observer<T> o_;
            @Override public void run() { t_.run(o_); }
            public Runnable set(ObservableTask<T> t1, Observer<T> o1) { t_ = t1; o_ = o1; return this; }
        }.set(t, o));
        return isQueued;
    }

    public void execute(Runnable r) {
        if (isWorkerThread()) {
            r.run();
        }
        else {
            queue(r);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// UPDATE PROPERTIES
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public interface PropertyUpdater<T> { void update(T value); }

    public <T> void update(PropertyUpdater<T> property, T value) {
        if (isWorkerThread()) {
            property.update(value);
        }
        else {
            queue(new Runnable() {
                PropertyUpdater<T> property_;
                T value_;
                @Override public void run() { property_.update(value_); }
                Runnable set(PropertyUpdater<T> p, T v) { property_ = p; value_ = v;  return this; }
            }.set(property, value));
        }
    }

    public <T> void update(TimeSpan timeout, PropertyUpdater<T> property, T value) {
        if (isWorkerThread()) {
            property.update(value);
        }
        else {
            ConditionVariable completed = new ConditionVariable();

            queue(new Runnable() {
                ConditionVariable completed_;
                PropertyUpdater<T> property_;
                T value_;
                @Override public void run() { try { property_.update(value_); } finally { completed_.open(); } }
                Runnable set(ConditionVariable c, PropertyUpdater<T> p, T v) { completed_ = c; property_ = p; value_ = v;  return this; }
            }.set(completed, property, value));

            completed.block(TimeSpan.toLong(timeout.totalMilliseconds()));
        }
    }

    public interface PropertyChanged<T> { void update(T from, T to); }

    public <T> void update(PropertyChanged<T> property, T from, T to) {
        if (isWorkerThread()) {
            property.update(from, to);
        }
        else {
            queue(new Runnable() {
                PropertyChanged<T> property_;
                T from_;
                T to_;
                @Override public void run() { property_.update(from_, to_); }
                Runnable set(PropertyChanged<T> p, T o, T n) { property_ = p; from_ = o; to_ = n; return this; }
            }.set(property, from, to));
        }
    }

    public <T> void update(TimeSpan timeout, PropertyChanged<T> property, T from, T to) {
        if (isWorkerThread()) {
            property.update(from, to);
        }
        else {
            ConditionVariable completed = new ConditionVariable();

            queue(new Runnable() {
                ConditionVariable completed_;
                PropertyChanged<T> property_;
                T from_;
                T to_;
                @Override public void run() { try { property_.update(from_, to_); } finally { completed_.open(); } }
                Runnable set(ConditionVariable c, PropertyChanged<T> p, T o, T n) { completed_ = c; property_ = p; from_ = o; to_ = n; return this; }
            }.set(completed, property, from, to));

            completed.block(TimeSpan.toLong(timeout.totalMilliseconds()));
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// EVENTS
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public <T> void raise(PropertyUpdater<T> property, T value) {
        events_.post(new Runnable() {
            PropertyUpdater<T> property_;
            T value_;
            @Override public void run() { property_.update(value_); }
            Runnable set(PropertyUpdater<T> p, T v) { property_ = p; value_ = v;  return this; }
        }.set(property, value));
    }

    public <T> void raise(PropertyChanged<T> property, T from, T to) {
        events_.post(new Runnable() {
            PropertyChanged<T> property_;
            T from_;
            T to_;
            @Override public void run() { property_.update(from_, to_); }
            Runnable set(PropertyChanged<T> p, T o, T n) { property_ = p; from_ = o; to_ = n; return this; }
        }.set(property, from, to));
    }


    public <SOURCE> void raise(MulticastDelegate<SOURCE> delegate, SOURCE source) {
        events_.post(new Runnable() {
            MulticastDelegate<SOURCE> delegate_;
            SOURCE source_;
            @Override
            public void run() { delegate_.raise(source_); }
            public Runnable set(MulticastDelegate<SOURCE> d, SOURCE s) { delegate_ = d; source_ = s; return this; }
        }.set(delegate, source));
    }

    public <SOURCE, T> void raise(MulticastChangedDelegate<SOURCE, T> delegate, SOURCE source, T from, T to) {
        events_.post(new Runnable() {
            MulticastChangedDelegate<SOURCE, T> delegate_;
            SOURCE source_;
            T from_;
            T to_;
            @Override
            public void run() { delegate_.raise(source_, from_, to_); }
            public Runnable set(MulticastChangedDelegate<SOURCE, T> d, SOURCE s, T o, T n) { delegate_ = d; source_ = s; from_ = o; to_ = n;  return this; }
        }.set(delegate, source, from, to));
    }

    public <SOURCE, T> void raise(MulticastMessageDelegate<SOURCE, T> delegate, SOURCE source, T value) {
        events_.post(new Runnable() {
            MulticastMessageDelegate<SOURCE, T> delegate_;
            SOURCE source_;
            T value_;
            @Override
            public void run() { delegate_.raise(source_, value_); }
            public Runnable set(MulticastMessageDelegate<SOURCE, T> d, SOURCE s, T v) { delegate_ = d; source_ = s; value_ = v;  return this; }
        }.set(delegate, source, value));
    }

    @SuppressWarnings("unchecked")
    public <SOURCE> void raise(ObserverBase<SOURCE> base, SOURCE source) {
        if (base == null) return;

        Observer<SOURCE> observer = (Observer<SOURCE>)base;

        boolean posted = events_.post(new Runnable() {
            Observer<SOURCE> observer_;
            SOURCE source_;
            @Override
            public void run() { notifyComplete(observer_, source_); }
            public Runnable set(Observer<SOURCE> o, SOURCE s) { observer_ = o; source_ = s; return this; }
        }.set(observer, source));

        if (!posted) notifyComplete(observer, source);
    }

    @SuppressWarnings("unchecked")
    public <SOURCE, T> void raise(ObserverBase<SOURCE> base, SOURCE source, T result) {
        if (base == null) return;

        ObserverResult<SOURCE, T> observer = (ObserverResult<SOURCE, T>)base;

        boolean posted = events_.post(new Runnable() {
            ObserverResult<SOURCE, T> observer_;
            SOURCE source_;
            T result_;
            @Override
            public void run() { notifyComplete(observer_, source_, result_); }
            public Runnable set(ObserverResult<SOURCE, T> o, SOURCE s, T r) { observer_ = o; source_ = s; result_ = r;  return this; }
        }.set(observer, source, result));

        if (!posted) notifyComplete(observer, source, result);
    }

    public <SOURCE> void raiseError(ObserverBase<SOURCE> observer, SOURCE source, String module, int code, String message) {
        if (observer == null) return;

        boolean posted = events_.post(new Runnable() {
            ObserverBase<SOURCE> observer_;
            SOURCE source_;
            String module_;
            int code_;
            String message_;
            @Override
            public void run() { notifyError(observer_, source_, module_, code_, message_); }
            public Runnable set(ObserverBase<SOURCE> o, SOURCE s, String m, int c, String msg) { observer_ = o; source_ = s; module_ = m; code_ = c; message_ = msg;  return this; }
        }.set(observer, source, module, code, message));

        if (!posted) notifyError(observer, source, module, code, message);
    }

    private static <SOURCE> void notifyComplete(Observer<SOURCE> observer, SOURCE source) {
        if (observer == null) return;
        observer.onComplete(source);
    }

    private static <SOURCE, T> void notifyComplete(ObserverResult<SOURCE, T> observer, SOURCE source, T result) {
        if (observer == null) return;
        observer.onComplete(source, result);
    }

    private static <SOURCE> void notifyError(ObserverBase<SOURCE> observer, SOURCE source, String module, int code, String message) {
        if (observer == null) return;
        observer.onError(source, module, code, message);
    }
}
