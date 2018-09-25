package com.crestron.airmedia.utilities;

import android.content.Context;
import android.os.Build;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Locale;

public abstract class ViewBase extends LinearLayout {

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// DEFINITIONS
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static class Size {
        public final static Size Zero = new Size(0, 0);
        public final int Width;
        public final int Height;
        public final double Ratio;
        public final boolean IsValid;
        public Size(int w, int h) { Width = w; Height = h; Ratio = Common.toRatio(w, h); IsValid = (w > 0 && h > 0); }
        public boolean isEqual(Size rhs) { return isEqual(this, rhs); }
        public static boolean isEqual(Size lhs, Size rhs) { return lhs == rhs || !(lhs == null || rhs == null) && lhs.Width == rhs.Width && lhs.Height == rhs.Height; }
        @Override public String toString() { return Width + "x" + Height; }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// FIELDS
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private Handler handler_;

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// CONSTRUCTOR
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public ViewBase(Context context) {
        this(context, null);
    }

    public ViewBase(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ViewBase(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        handler_ = new Handler(Looper.myLooper());
    }

    //@TargetApi(Build.VERSION_CODES.LOLLIPOP)
    //public ViewBase(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    //    super(context, attrs, defStyleAttr, defStyleRes);
    //    handler_ = new Handler(Looper.myLooper());
    //}

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// PROPERTIES
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public boolean isViewThread() { return handler().getLooper().getThread().getId() == Thread.currentThread().getId(); }

    public Handler handler() { return handler_; }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// METHODS: LOAD | UNLOAD
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public void load() {
        runOnViewThread(TimeSpan.fromSeconds(30.0), new Runnable() {
            @Override public void run() { loadInternal(); }});
    }

    public void unload() {
        runOnViewThread(TimeSpan.fromSeconds(30.0), new Runnable() {
            @Override public void run() { unloadInternal(); }});
    }

    protected abstract void loadInternal();

    protected abstract void unloadInternal();

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// METHODS: THREAD AFFINITY
    ////////////////////////////////////////////////////////////////////////////////////////////////

    protected void postOnViewThread(Runnable runnable) {
        handler().post(runnable);
    }

    protected void postOnViewThread(TimeSpan timeout, Runnable runnable) {
        ConditionVariable completed = new ConditionVariable();

        postOnViewThread(new Runnable() {
            private ConditionVariable completed_;
            private Runnable runnable_;
            @Override
            public void run() { try { runnable_.run(); } finally { completed_.open(); } }
            public Runnable set(ConditionVariable c, Runnable r) { completed_ = c; runnable_ = r; return this; }
        }.set(completed, runnable));

        completed.block(TimeSpan.toLong(timeout.totalMilliseconds()));
    }

    protected void runOnViewThread(Runnable runnable) {
        if (isViewThread()) {
            runnable.run();
        } else {
            postOnViewThread(runnable);
        }
    }

    protected void runOnViewThread(TimeSpan timeout, Runnable runnable) {
        if (isViewThread()) {
            runnable.run();
        } else {
            postOnViewThread(timeout, runnable);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// METHODS: PROPERTY UPDATE
    ////////////////////////////////////////////////////////////////////////////////////////////////

    protected interface PropertyUpdater<T> { void update(T value); }

    protected <T> void update(PropertyUpdater<T> property, T value) {
        if (isViewThread()) {
            property.update(value);
        }
        else {
            postOnViewThread(new Runnable() {
                private PropertyUpdater<T> property_;
                private T value_;
                @Override
                public void run() { property_.update(value_); }
                Runnable set(PropertyUpdater<T> p, T v) { property_ = p; value_ = v;  return this; }
            }.set(property, value));
        }
    }

    protected <T> void update(TimeSpan timeout, PropertyUpdater<T> property, T value) {
        if (isViewThread()) {
            property.update(value);
        }
        else {
            postOnViewThread(timeout, new Runnable() {
                private PropertyUpdater<T> property_;
                private T value_;
                @Override
                public void run() { property_.update(value_); }
                Runnable set(PropertyUpdater<T> p, T v) { property_ = p; value_ = v;  return this; }
            }.set(property, value));
        }
    }

    protected interface PropertyChanged<T> { void update(T from, T to); }

    protected <T> void update(PropertyChanged<T> property, T from, T to) {
        if (isViewThread()) {
            property.update(from, to);
        }
        else {
            postOnViewThread(new Runnable() {
                private PropertyChanged<T> property_;
                private T from_;
                private T to_;
                @Override
                public void run() { property_.update(from_, to_); }
                Runnable set(PropertyChanged<T> p, T o, T n) { property_ = p; from_ = o; to_ = n;  return this; }
            }.set(property, from, to));
        }
    }

    protected <T> void update(TimeSpan timeout, PropertyChanged<T> property, T from, T to) {
        if (isViewThread()) {
            property.update(from, to);
        }
        else {
            postOnViewThread(timeout, new Runnable() {
                private PropertyChanged<T> property_;
                private T from_;
                private T to_;
                @Override
                public void run() { property_.update(from_, to_); }
                Runnable set(PropertyChanged<T> p, T o, T n) { property_ = p; from_ = o; to_ = n;  return this; }
            }.set(property, from, to));
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// METHODS: VIEWS
    ////////////////////////////////////////////////////////////////////////////////////////////////

    protected void show(View view) {
        visibility(view, VISIBLE);
    }

    protected void hide(View view) {
        visibility(view, GONE);
    }

    private void visibility(View view, int value) {
        if (view == null) return;
        if (isViewThread()) {
            view.setVisibility(value);
        } else {
            runOnViewThread(new Runnable() {
                private View view_;
                private int value_;
                @Override
                public void run() { view_.setVisibility(value_); }
                public Runnable set(View vw, int v) { view_ = vw; value_ = v; return this; }
            }.set(view, value));
        }
    }

    protected void set(TextView view, String value) {
        if (view == null) return;
        if (isViewThread()) {
            view.setText(value);
        } else {
            runOnViewThread(new Runnable() {
                private TextView view_;
                private String value_;
                @Override
                public void run() {
                    try {
                        view_.setText(value_);
                    } catch (Exception ignore) { }
                }
                public Runnable set(TextView t, String v) { view_ = t; value_ = v; return this; }
            }.set(view, value));
        }
    }

    //@SuppressLint("DefaultLocale")
    protected void setPercent(TextView view, float value) {
        if (view == null) return;
        value = Common.limit(value, 0.0f, 1.0f);
        String text = String.format(Locale.US, "%d%%", Math.round(100.0f * value));
        if (isViewThread()) {
            view.setText(text);
        } else {
            runOnViewThread(new Runnable() {
                private TextView view_;
                private String text_;
                @Override
                public void run() {
                    try {
                        view_.setText(text_);
                    } catch (Exception ignore) { }
                }
                public Runnable set(TextView t, String v) { view_ = t; text_ = v; return this; }
            }.set(view, text));
        }
    }

    protected void set(SeekBar view, float value) {
        if (view == null) return;
        value = Common.limit(value, 0.0f, 1.0f);
        if (isViewThread()) {
            view.setProgress(Math.round(100.0f * value));
        } else {
            runOnViewThread(new Runnable() {
                private SeekBar view_;
                private float value_;
                @Override
                public void run() {
                    try {
                        view_.setProgress(Math.round(100.0f * value_));
                    } catch (Exception ignore) { }
                }
                public Runnable set(SeekBar t, float v) { view_ = t; value_ = v; return this; }
            }.set(view, value));
        }
    }

    protected void set(ProgressBar view, float value) {
        if (view == null) return;
        if (value > 1.0f) value = 1.0f;
        else if (value < 0.0f) value = 0.0f;
        if (isViewThread()) {
            view.setProgress(Math.round(100.0f * value));
        } else {
            runOnViewThread(new Runnable() {
                private ProgressBar view_;
                private float value_;
                @Override
                public void run() {
                    try {
                        view_.setProgress(Math.round(100.0f * value_));
                    } catch (Exception ignore) { }
                }
                public Runnable set(ProgressBar t, float v) { view_ = t; value_ = v; return this; }
            }.set(view, value));
        }
    }

    protected Size getDisplayResolution() {
        return getDisplayResolution(getContext());
    }

    public static Size getDisplayResolution(Context context) {
        return getDisplayResolution((WindowManager)context.getSystemService(Context.WINDOW_SERVICE));
    }

    public static Size getDisplayResolution(WindowManager manager) {
        DisplayMetrics display = getDisplayMetrics(manager);
        return new Size(display.widthPixels, display.heightPixels);
    }

    protected DisplayMetrics getDisplayMetrics() {
        return getDisplayMetrics(getContext());
    }

    public static DisplayMetrics getDisplayMetrics(Context context) {
        return getDisplayMetrics((WindowManager)context.getSystemService(Context.WINDOW_SERVICE));
    }

    public static DisplayMetrics getDisplayMetrics(WindowManager manager) {
        Display display = manager.getDefaultDisplay();
        DisplayMetrics metrics = new DisplayMetrics();
        //display.getMetrics(metrics);
        display.getRealMetrics(metrics);
        return metrics;
    }
}
