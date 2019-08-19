package com.crestron.airmedia.receiver.m360.ipc;

import android.os.Parcel;
import android.os.Parcelable;

import com.crestron.airmedia.utilities.Common;

public class AirMediaSessionInfo implements Parcelable {
    public final AirMediaPlatforms platform;
    public final String os;
    public final String version;
    public final String manufacturer;
    public final String model;
    public final String language;
    public final String hostname;
    public final String controlUseragent;
    public final String deviceUseragent;
    public final boolean isRotationSupported;
    public final boolean isRotationManagedBySender;
    public final AirMediaSessionVideoSource[] videoSources;

    public AirMediaSessionInfo() {
        platform = AirMediaPlatforms.Undefined;
        os = "";
        version = "";
        manufacturer = "";
        model = "";
        language = "";
        hostname = "";
        controlUseragent = "";
        deviceUseragent = "";
        isRotationSupported = true;
        isRotationManagedBySender = false;
        videoSources = AirMediaSessionVideoSource.CREATOR.newArray(0);
    }

    private AirMediaSessionInfo(Parcel in) {
        platform = AirMediaPlatforms.from(in.readInt());
        os = in.readString();
        version = in.readString();
        manufacturer = in.readString();
        model = in.readString();
        language = in.readString();
        hostname = in.readString();
        controlUseragent = in.readString();
        deviceUseragent = in.readString();
        isRotationSupported = in.readInt() != 0;
        isRotationManagedBySender = in.readInt() != 0;
        int videoSourcesSize = in.readInt();
        videoSources = AirMediaSessionVideoSource.CREATOR.newArray(videoSourcesSize);
        for (int i = 0; i < videoSourcesSize; i++) {
            videoSources[i] = AirMediaSessionVideoSource.CREATOR.createFromParcel(in);
        }
    }

    public AirMediaSessionInfo(AirMediaSessionInfo old, AirMediaSessionInfo update) {
        platform = update != null && update.platform != AirMediaPlatforms.Undefined ? update.platform : old != null ? old.platform : AirMediaPlatforms.Undefined;
        os = update != null && Common.isNotEmpty(update.os) ? update.os : old != null ? old.os : "";
        version = update != null && Common.isNotEmpty(update.version) ? update.version : old != null ? old.version : "";
        manufacturer = update != null && Common.isNotEmpty(update.manufacturer) ? update.manufacturer : old != null ? old.manufacturer : "";
        model = update != null && Common.isNotEmpty(update.model) ? update.model : old != null ? old.model : "";
        language = update != null && Common.isNotEmpty(update.language) ? update.language : old != null ? old.language : "";
        hostname = update != null && Common.isNotEmpty(update.hostname) ? update.hostname : old != null ? old.hostname : "";
        controlUseragent = update != null && Common.isNotEmpty(update.controlUseragent) ? update.controlUseragent : old != null ? old.controlUseragent : "";
        deviceUseragent = update != null && Common.isNotEmpty(update.deviceUseragent) ? update.deviceUseragent : old != null ? old.deviceUseragent : "";
        isRotationSupported = update != null ? update.isRotationSupported : old == null || old.isRotationSupported;
        isRotationManagedBySender = update != null ? update.isRotationManagedBySender : old == null || old.isRotationManagedBySender;
        AirMediaSessionVideoSource[] videoSourcesToUse = null;
        if (update != null && old != null) {
            if (update.videoSources != null && old.videoSources != null) {
                videoSourcesToUse = update.videoSources.length > old.videoSources.length ? update.videoSources : old.videoSources;
            } else if (update.videoSources != null) {
                videoSourcesToUse = update.videoSources;
            } else {
                videoSourcesToUse = old.videoSources;
            }
        } else if (update != null) {
            videoSourcesToUse = update.videoSources;
        } else if (old != null) {
            videoSourcesToUse = old.videoSources;
        }
        videoSources = videoSourcesToUse != null ? videoSourcesToUse : AirMediaSessionVideoSource.CREATOR.newArray(0);
    }

    protected AirMediaSessionInfo(
            AirMediaPlatforms inPlatforms, String inOs, String inVersion, String inManufacturer,
            String inModel, String inLanguage, String inHostname,
            String inControlUseragent, String inDeviceUseragent, boolean inIsRotationSupported, boolean inRotationManagedBySender,
            AirMediaSessionVideoSource[] inVideoSources) {
        platform = inPlatforms;
        os = inOs;
        version = inVersion;
        manufacturer = inManufacturer;
        model = inModel;
        language = inLanguage;
        hostname = inHostname;
        controlUseragent = inControlUseragent;
        deviceUseragent = inDeviceUseragent;
        isRotationSupported = inIsRotationSupported;
        isRotationManagedBySender = inRotationManagedBySender;
        videoSources = inVideoSources;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(platform.value);
        dest.writeString(os);
        dest.writeString(version);
        dest.writeString(manufacturer);
        dest.writeString(model);
        dest.writeString(language);
        dest.writeString(hostname);
        dest.writeString(controlUseragent);
        dest.writeString(deviceUseragent);
        dest.writeInt(isRotationSupported ? 1 : 0);
        dest.writeInt(isRotationManagedBySender ? 1 : 0);
        dest.writeInt(videoSources != null ? videoSources.length : 0);
        if (videoSources != null) {
            for (AirMediaSessionVideoSource videoSource : videoSources) {
                videoSource.writeToParcel(dest, flags);
            }
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<AirMediaSessionInfo> CREATOR = new Creator<AirMediaSessionInfo>() {
        @Override
        public AirMediaSessionInfo createFromParcel(Parcel in) {
            return new AirMediaSessionInfo(in);
        }

        @Override
        public AirMediaSessionInfo[] newArray(int size) {
            return new AirMediaSessionInfo[size];
        }
    };

    @Override
    public String toString() {
        return "AirMediaSessionInfo["
                + " platform= " + platform + Common.Delimiter
                + " os= " + os + Common.Delimiter
                + " version= " + version + Common.Delimiter
                + " manufacturer= " + manufacturer + Common.Delimiter
                + " model= " + model + Common.Delimiter
                + " language= " + language + Common.Delimiter
                + " hostname= " + hostname + Common.Delimiter
                + " control-user-agent= " + controlUseragent + Common.Delimiter
                + " device-user-agent= " + deviceUseragent + Common.Delimiter
                + " rotation-supported= " + isRotationSupported + Common.Delimiter
                + " rotation-by-sender= " + isRotationManagedBySender + Common.Delimiter
                + " video-sources= " + AirMediaSessionVideoSource.toString(videoSources)
                + " ]";
    }

    public boolean isEqual(AirMediaSessionInfo ma) {
        return isEqual(this, ma);
    }

    public static boolean isEqual(AirMediaSessionInfo lhs, AirMediaSessionInfo rhs) {
        return lhs == rhs || !(lhs == null || rhs == null)
                && lhs.platform == rhs.platform
                && Common.isEqual(lhs.os, rhs.os)
                && Common.isEqual(lhs.version, rhs.version)
                && Common.isEqual(lhs.manufacturer, rhs.manufacturer)
                && Common.isEqual(lhs.model, rhs.model)
                && Common.isEqual(lhs.language, rhs.language)
                && Common.isEqual(lhs.hostname, rhs.hostname)
                && Common.isEqual(lhs.controlUseragent, rhs.controlUseragent)
                && Common.isEqual(lhs.deviceUseragent, rhs.deviceUseragent)
                && lhs.isRotationSupported == rhs.isRotationSupported
                && lhs.isRotationManagedBySender == rhs.isRotationManagedBySender
                && AirMediaSessionVideoSource.isEqual(lhs.videoSources, rhs.videoSources);
    }

    public static boolean platformSupportsVideoPush(AirMediaSessionInfo info) {
        return info == null || info.platform.supportsVideoPush();
    }
}