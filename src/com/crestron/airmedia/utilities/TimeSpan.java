package com.crestron.airmedia.utilities;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class TimeSpan {
    public static final long NANOSECOND = 1;
    public static final long MICROSECOND = 1000 * NANOSECOND;
    public static final long MILLISECOND = 1000 * MICROSECOND;
    public static final long SECOND = 1000 * MILLISECOND;
    public static final long MINUTE = 60 * SECOND;
    public static final long HOUR = 60 * MINUTE;
    public static final long DAY = 24 * HOUR;

    private static final TimeSpan start_;
    private final long timeNanoseconds_;

    public static final TimeSpan Zero = fromNanoseconds(0);

    static { start_ = new TimeSpan(time()); }

    private static long time() { return System.nanoTime(); }

    public static TimeSpan now()
    {
        return new TimeSpan();
    }

    public static String getDelta(TimeSpan start) {
        return "[timespan= " + TimeSpan.now().subtract(start).toString() + "]";
    }

    public static TimeSpan fromNanoseconds(long nsec) { return new TimeSpan(nsec); }

    public static TimeSpan fromMicroseconds(double usec) { return new TimeSpan(Math.round(usec * MICROSECOND)); }

    public static TimeSpan fromMilliseconds(double msec) { return new TimeSpan(Math.round(msec * MILLISECOND)); }

    public static TimeSpan fromSeconds(double sec) { return new TimeSpan(Math.round(sec * SECOND)); }

    public static TimeSpan fromMinutes(double min) { return new TimeSpan(Math.round(min * MINUTE)); }

    public static TimeSpan fromHours(double hr) { return new TimeSpan(Math.round(hr * HOUR)); }

    public static TimeSpan fromDays(double days) { return new TimeSpan(Math.round(days * DAY)); }

    public static TimeSpan add(TimeSpan lhs, TimeSpan rhs) { return new TimeSpan(lhs.timeNanoseconds_ + rhs.timeNanoseconds_); }

    public static TimeSpan subtract(TimeSpan lhs, TimeSpan rhs) { return new TimeSpan(lhs.timeNanoseconds_ - rhs.timeNanoseconds_); }

    public static TimeSpan multiply(TimeSpan lhs, double factor) { return new TimeSpan(toLong(factor * (double)lhs.timeNanoseconds_)); }

    public static TimeSpan divide(TimeSpan lhs, double factor) { return new TimeSpan(toLong((double)lhs.timeNanoseconds_ / factor)); }

    public static boolean equal(TimeSpan lhs, TimeSpan rhs) { return lhs.timeNanoseconds_ == rhs.timeNanoseconds_; }

    public static boolean greaterThan(TimeSpan lhs, TimeSpan rhs) { return lhs.timeNanoseconds_ > rhs.timeNanoseconds_; }

    public static boolean greaterThanOrEqual(TimeSpan lhs, TimeSpan rhs) { return lhs.timeNanoseconds_ >= rhs.timeNanoseconds_; }

    public static boolean lessThan(TimeSpan lhs, TimeSpan rhs) { return lhs.timeNanoseconds_ < rhs.timeNanoseconds_; }

    public static boolean lessThanOrEqual(TimeSpan lhs, TimeSpan rhs) { return lhs.timeNanoseconds_ <= rhs.timeNanoseconds_; }

    public static long toLong(double value) { return Math.round(value); }

    public static int toInt(double value) {
        long numberLong = Math.round(value);
        int numberInt = (int)numberLong;
        if (numberLong > Integer.MAX_VALUE) numberInt = Integer.MAX_VALUE;
        else if (numberLong < Integer.MIN_VALUE) numberInt = Integer.MIN_VALUE;
        return numberInt;
    }

    public TimeSpan()
    {
        timeNanoseconds_ = time() - start_.timeNanoseconds_;
    }

    private TimeSpan(long totalNanoseconds)
    {
        timeNanoseconds_ = totalNanoseconds;
    }

    public long totalNanoseconds() { return timeNanoseconds_; }

    public double totalMicroseconds() { return (double) timeNanoseconds_ / (double)MICROSECOND; }

    public double totalMilliseconds() { return (double) timeNanoseconds_ / (double)MILLISECOND; }

    public double totalSeconds()
    {
        return (double) timeNanoseconds_ / (double)SECOND;
    }

    public double totalMinutes()
    {
        return (double) timeNanoseconds_ / (double)MINUTE;
    }

    public double totalHours()
    {
        return (double) timeNanoseconds_ / (double)HOUR;
    }

    public double totalDays() { return (double) timeNanoseconds_ / (double)DAY; }

    public long nanoseconds()
    {
        return (timeNanoseconds_ % 1000);
    }

    public long microseconds() { return (timeNanoseconds_ / MICROSECOND) % (MILLISECOND / MICROSECOND); }

    public long milliseconds() { return (timeNanoseconds_ / MILLISECOND) % (SECOND / MILLISECOND); }

    public long seconds() { return (timeNanoseconds_ / SECOND) % (MINUTE / SECOND); }

    public long minutes() { return (timeNanoseconds_ / MINUTE) % (HOUR / MINUTE); }

    public long hours() { return (timeNanoseconds_ / HOUR) % (DAY / HOUR); }

    public long days() { return timeNanoseconds_ / DAY; }

    public TimeSpan add(TimeSpan rhs)
    {
        return add(this, rhs);
    }

    public TimeSpan subtract(TimeSpan rhs)
    {
        return subtract(this, rhs);
    }

    public TimeSpan multiply(double factor) { return multiply(this, factor); }

    public TimeSpan divide(double factor) { return divide(this, factor); }

    public boolean equal(TimeSpan rhs)
    {
        return equal(this, rhs);
    }

    public boolean greaterThan(TimeSpan rhs)
    {
        return greaterThan(this, rhs);
    }

    public boolean greaterThanOrEqual(TimeSpan rhs)
    {
        return greaterThanOrEqual(this, rhs);
    }

    public boolean lessThan(TimeSpan rhs)
    {
        return lessThan(this, rhs);
    }

    public boolean lessThanOrEqual(TimeSpan rhs)
    {
        return lessThanOrEqual(this, rhs);
    }

    public String toString() { return toString(TimeUnit.MICROSECONDS); }

    public String toString(TimeUnit resolution) {
        boolean hasDays = totalNanoseconds() >= DAY;

        StringBuilder builder = new StringBuilder();

        if (hasDays) builder.append(days() + ".");

        switch (resolution) {
            case DAYS:
            case HOURS:
            case MINUTES:
            case SECONDS:
                builder.append(String.format(Locale.US, "%d:%02d:%02d", hours(), minutes(), seconds()));
                break;

            case MILLISECONDS:
                builder.append(String.format(Locale.US, "%02d:%02d:%02d.%03d", hours(), minutes(), seconds(), milliseconds()));
                break;

            case MICROSECONDS:
                long usec = (milliseconds() * (MILLISECOND / MICROSECOND)) + microseconds();
                builder.append(String.format(Locale.US, "%02d:%02d:%02d.%06d", hours(), minutes(), seconds(), usec));
                break;

            case NANOSECONDS:
                long nsec = (milliseconds() * (MILLISECOND / NANOSECOND)) + (microseconds() * (MICROSECOND / NANOSECOND)) + nanoseconds();
                builder.append(String.format(Locale.US, "%02d:%02d:%02d.%09d", hours(), minutes(), seconds(), nsec));
                break;
        }

        return builder.toString();
    }
}
