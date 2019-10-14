package com.crestron.airmedia.receiver.m360.ipc;

import android.os.Parcel;
import android.os.Parcelable;

import com.crestron.airmedia.utilities.Common;
import com.google.gson.annotations.SerializedName;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public enum AirMediaPlatforms implements Parcelable {
    @SerializedName("0") Undefined(0),
    @SerializedName("1") Windows(1),
    @SerializedName("2") Mac(2),
    @SerializedName("3") iOS(3),
    @SerializedName("4") Android(4),
    @SerializedName("5") Chromebook(5),
    @SerializedName("6") Linux(6);
    public final int value;

    private static final Map<String, AirMediaPlatforms> ModelMapping;
    private static final Map<String, AirMediaPlatforms> UserAgentMapping;

    static {
        ModelMapping = new HashMap<String, AirMediaPlatforms>();
        ModelMapping.put("iphone", iOS);
        ModelMapping.put("ipad", iOS);
        ModelMapping.put("ipod", iOS);
        ModelMapping.put("mac", Mac);
        ModelMapping.put("android", Android);
        ModelMapping.put("win", Windows);

        UserAgentMapping = new HashMap<String, AirMediaPlatforms>();
        UserAgentMapping.put("iphone", iOS);
        UserAgentMapping.put("ipad", iOS);
        UserAgentMapping.put("ipod", iOS);
        UserAgentMapping.put("cros", Chromebook);
        UserAgentMapping.put("android", Android);
        UserAgentMapping.put("macintosh", Mac);
        UserAgentMapping.put("windows", Windows);
        UserAgentMapping.put("linux", Linux);
    }

    AirMediaPlatforms(int v) { value = v; }

    @Override
    public void writeToParcel(Parcel dest, int flags) { dest.writeInt(value); }

    @Override
    public int describeContents() { return 0; }

    public static final Parcelable.Creator<AirMediaPlatforms> CREATOR = new Parcelable.Creator<AirMediaPlatforms>() {
        @Override
        public AirMediaPlatforms createFromParcel(Parcel in) { return AirMediaPlatforms.from(in); }

        @Override
        public AirMediaPlatforms[] newArray(int size) { return new AirMediaPlatforms[size]; }
    };

    public static AirMediaPlatforms from(Parcel in) { return from(in.readInt()); }

    public static AirMediaPlatforms from(int v) {
        switch (v) {
            case 0: return Undefined;
            case 1: return Windows;
            case 2: return Mac;
            case 3: return iOS;
            case 4: return Android;
            case 5: return Chromebook;
        }
        return Undefined;
    }

    public String manufacturer() {
        switch (this) {
            case Windows:
                return "Microsoft";
            case Mac:
            case iOS:
                return "Apple";
            case Android:
            case Chromebook:
                return "Google";
        }
        return "";
    }

    public boolean supportsRotation() {
        switch (this) {
            case Windows: return true;
            case Mac: return false;
            case iOS: return true;
            case Android: return true;
            case Chromebook: return true;
        }
        return true;
    }

    public boolean managesRotation() {
        switch (this) {
            case Windows: return true;
            case Mac: return false;
            case iOS: return true;
            case Android: return true;
            case Chromebook: return true;
        }
        return false;
    }

    public boolean supportsVideoPush() {
        switch (this) {
            case Windows: return false;
            case Mac: return true;
            case iOS: return true;
            case Android: return false;
            case Chromebook: return false;
        }
        return false;
    }

    // ^[^\/]+\/\d+[.]\d+\s+\((?<platform>[^\)]+)\)
    static private Pattern UserAgentPattern = Pattern.compile("^[^/]+/\\d+[.]\\d+\\s+\\(([^)]+)\\)");

    // Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_2) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/71.0.3578.98 Safari/537.36
    // Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/73.0.3683.103 Safari/537.36
    // Mozilla/5.0 (X11; CrOS aarch64 11647.104.0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/73.0.3683.88 Safari/537.36
    // Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/73.0.3683.103 Safari/537.36
    public static AirMediaPlatforms from(String model, String userAgent) {
        Common.Logging.e("AirMediaPlatforms", "AirMediaPlatforms.from  model= " + model + "  user-agent= " + userAgent);
        if (Common.isEmpty(model) && Common.isEmpty(userAgent)) return Undefined;
        if (Common.isNotEmpty(model)) {
            model = model.toLowerCase();
            for (Map.Entry<String, AirMediaPlatforms> entry : ModelMapping.entrySet()) {
                if (model.contains(entry.getKey())) return entry.getValue();
            }
        }
        if (Common.isNotEmpty(userAgent)) {
            try {
                Matcher matcher = UserAgentPattern.matcher(userAgent);
                boolean found = matcher.find();
                if (found) {
                    String platform = matcher.group(1);
                    if (Common.isNotEmpty(platform)) {
                        platform = platform.toLowerCase();
                        for (Map.Entry<String, AirMediaPlatforms> entry : UserAgentMapping.entrySet()) {
                            if (platform.contains(entry.getKey())) return entry.getValue();
                        }
                    }
                }
            } catch (Exception e) {
                Common.Logging.e("AirMediaPlatforms", "AirMediaPlatforms.from  model= " + model + "  user-agent= " + userAgent + "  EXCEPTION  " + e);
            }
        }
        return Undefined;
    }
}
