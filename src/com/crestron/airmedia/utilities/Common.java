package com.crestron.airmedia.utilities;

import android.app.ActivityManager;
import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;
//import android.util.Size;

import java.lang.reflect.Method;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.text.DecimalFormat;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class Common {
    private static String TAG = "Utilities.Common";
    private static Boolean isEmulator_ = null;
    private static DecimalFormat decimalFormat_ = new DecimalFormat("0.000");

    public static final String Delimiter = " · ";

    public static class Units {
        public static final long Kilobytes = 1024;
        public static final long Megabytes = 1048576;
        public static final long Gigabytes = 1073741824;

        public static double toKilobytes(long bytes) { return (double)bytes/(double)Kilobytes; }
        public static double toMegabytes(long bytes) { return (double)bytes/(double)Megabytes; }
        public static double toGigabytes(long bytes) { return (double)bytes/(double)Gigabytes; }

        //@SuppressLint("DefaultLocale")
        public static String toString(long bytes) {
            if (bytes > (Gigabytes - Kilobytes)) return String.format("%.2f GB", Common.Units.toGigabytes(bytes));
            else if (bytes > (Megabytes - Kilobytes)) return String.format("%.2f MB", Common.Units.toMegabytes(bytes));
            else if (bytes > Kilobytes) return String.format("%.2f KB", Common.Units.toKilobytes(bytes));
            return String.format("%d bytes", bytes);
        }
    }

    public static class Logging {
        private static Handler handler_;

        static {
        }

        public interface Handler {
            void log(int type, String tag, String message);
        }

        public static void add(Handler handler) {
            handler_ = handler;
        }

        public static void d(String tag, String message) {
            log(Log.DEBUG, tag, message);
        }

        public static void v(String tag, String message) {
            log(Log.VERBOSE, tag, message);
        }

        public static void i(String tag, String message) {
            log(Log.INFO, tag, message);
        }

        public static void w(String tag, String message) {
            log(Log.WARN, tag, message);
        }

        public static void e(String tag, String message) {
            log(Log.ERROR, tag, message);
        }

        private static void log(int type, String tag, String message) {
            Handler handler = handler_;
            if (handler != null) {
                try { handler.log(type, tag, message); } catch (Exception ignore) { }
            } else {
                message = String.format("<%1$04x>  %2$s", Thread.currentThread().getId(), message);
                switch (type) {
                    case Log.DEBUG:
                        Log.d(tag, message);
                        break;
                    case Log.VERBOSE:
                        Log.v(tag, message);
                        break;
                    case Log.INFO:
                        Log.i(tag, message);
                        break;
                    case Log.WARN:
                        Log.w(tag, message);
                        break;
                    case Log.ERROR:
                        Log.e(tag, message);
                        break;
                }
            }
        }
    }

    public static boolean isEmulator() {
        if (isEmulator_ == null) {
            isEmulator_ = Build.FINGERPRINT.startsWith("generic")
                    || Build.FINGERPRINT.startsWith("unknown")
                    || Build.MODEL.contains("google_sdk")
                    || Build.MODEL.contains("Emulator")
                    || Build.MODEL.contains("Android SDK built for x86")
                    || Build.MANUFACTURER.contains("Genymotion")
                    || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                    || "google_sdk".equals(Build.PRODUCT);

            Log.e(TAG, "isEmulator= " + isEmulator_
                    + "\nBuild.FINGERPRINT= " + Build.FINGERPRINT
                    + "\nBuild.MODEL= " + Build.MODEL
                    + "\nBuild.MANUFACTURER= " + Build.MANUFACTURER
                    + "\nBuild.BRAND= " + Build.BRAND
                    + "\nBuild.DEVICE= " + Build.DEVICE
                    + "\nBuild.PRODUCT= " + Build.PRODUCT);
        }
        return isEmulator_;
    }

    public static boolean isServiceRunning(Context context, Class<?> serviceClass) {
        return isServiceRunning((ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE), serviceClass);
    }

    public static boolean isServiceRunning(ActivityManager manager, Class<?> serviceClass) {
        return isServiceRunning(manager, serviceClass.getName());
    }

    public static boolean isServiceRunning(Context context, String name) {
        return isServiceRunning((ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE), name);
    }

    public static boolean isServiceRunning(ActivityManager manager, String targetName) {
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            String name = service.service.getClassName();
            if (targetName.equals(name)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isEmpty(String lhs) {
        return lhs == null || lhs.isEmpty();
    }

    public static boolean isNotEmpty(String lhs) {
        return !isEmpty(lhs);
    }

    public static boolean isEqual(String lhs, String rhs) {
        if (lhs == null && rhs == null) return true;

        if (lhs == null || rhs == null) return false;

        return lhs.equals(rhs);
    }

    public static boolean isNotEqual(String lhs, String rhs) {
        return !isEqual(lhs, rhs);
    }

    public static boolean isEqualIgnoreCase(String lhs, String rhs) {
        if (lhs == null && rhs == null) return true;

        if (lhs == null || rhs == null) return false;

        return lhs.equalsIgnoreCase(rhs);
    }

    //@TargetApi(Build.VERSION_CODES.LOLLIPOP)
    //public static boolean isEqual(Size lhs, Size rhs) {
    //    if (lhs == null && rhs == null) return true;
    //
    //    if (lhs == null || rhs == null) return false;
    //
    //    return lhs.getWidth() == rhs.getWidth() && lhs.getHeight() == rhs.getHeight();
    //}

    public static int width(int h, double r) {
        return (int)Math.floor((double)h * r);
    }

    //@TargetApi(Build.VERSION_CODES.LOLLIPOP)
    //public static int width(int h, Size s) {
    //    double r = toRatio(s);
    //    return width(h, r);
    //}

    public static int height(int w, double r) {
        return r != 0.0 ? (int)Math.floor((double)w / r) : 0;
    }

    //@TargetApi(Build.VERSION_CODES.LOLLIPOP)
    //public static int height(int w, Size s) {
    //    double r = toRatio(s);
    //    return height(w, r);
    //}

    public static double toRatio(int w, int h) {
        return toRatio((double)w, (double)h);
    }

    public static double toRatio(double w, double h) {
        return h != 0.0 ? w/h : 0.0;
    }

    //@TargetApi(Build.VERSION_CODES.LOLLIPOP)
    //public static double toRatio(Size r) {
    //    return toRatio((double)r.getWidth(), (double)r.getHeight());
    //}

    public static String toRatioString(int w, int h) {
        return toRatioString((double)w, (double)h);
    }

    public static String toRatioString(double w, double h) {
        return decimalFormat_.format(toRatio(w, h));
    }

    //@TargetApi(Build.VERSION_CODES.LOLLIPOP)
    //public static String toRatioString(Size r) {
    //    return toRatioString((double)r.getWidth(), (double)r.getHeight());
    //}

    public static float limit(final float input, final float min, final float max) {
        return input >= max ? max : input <= min ? min : input;
    }

    public static int limit(final int input, final int min, final int max) {
        return input >= max ? max : input <= min ? min : input;
    }

    private static InetAddress intToInetAddress(int hostAddress) {
        byte[] addressBytes = {
                (byte) (0xff & hostAddress),
                (byte) (0xff & (hostAddress >> 8)),
                (byte) (0xff & (hostAddress >> 16)),
                (byte) (0xff & (hostAddress >> 24)) };

        try {
            return InetAddress.getByAddress(addressBytes);
        } catch (UnknownHostException e) {
            throw new AssertionError();
        }
    }

//    public static void vp(Context context) {
//        ConnectivityManager connMananger = (ConnectivityManager)
//                context.getSystemService(Context.CONNECTIVITY_SERVICE);
//
//        NetworkInfo netInfo = connMananger.getActiveNetworkInfo();
//
//        //android.net.conn.CONNECTIVITY_CHANGE;
//    }

    public static String getHostname() {
        try {
            Method getString = Build.class.getDeclaredMethod("getString", String.class);
            getString.setAccessible(true);
            return getString.invoke(null, "net.hostname").toString();
        } catch (Exception ignore) { }
        return "";
    }

    public static String getWifiIpAddress(Context context) {
        return context == null ? null : getWifiIpAddress((WifiManager) context.getSystemService(Context.WIFI_SERVICE));
    }

    public static String getWifiIpAddress(WifiManager manager) {
        if (manager == null) return null;
        WifiInfo info = manager.getConnectionInfo();
        if (info == null) return null;
        int address = info.getIpAddress();
        if (address == 0) return null;
        InetAddress inetAddress = intToInetAddress(address);
        return inetAddress.getHostAddress();
    }

    public static Collection<String> getIpv4Addresses() throws SocketException {
        List<String> list = new LinkedList<String>();
        for (Enumeration<NetworkInterface> adapters = NetworkInterface.getNetworkInterfaces(); adapters.hasMoreElements();) {
            NetworkInterface adapter = adapters.nextElement();
            for (Enumeration<InetAddress> enumIpAddr = adapter.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                InetAddress inetAddress = enumIpAddr.nextElement();
                if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                    list.add(inetAddress.getHostAddress());
                }
            }
        }
        return list;
    }

    public static String getIpv4Address() throws SocketException {
        Collection<String> addresses = getIpv4Addresses();
        Iterator<String> iterator = addresses.iterator();
        return iterator.hasNext() ? iterator.next() : null;
    }

    public static int setAlpha(float opacity, int color) {
        final int alpha = Math.round(0xff * opacity);
        return (alpha << 24) | (color & 0x00ffffff);
    }
}


