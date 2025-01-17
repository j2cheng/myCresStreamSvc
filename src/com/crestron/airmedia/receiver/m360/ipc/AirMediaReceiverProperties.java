package com.crestron.airmedia.receiver.m360.ipc;

public final class AirMediaReceiverProperties {

    public static class Splashtop {
        public static String SecureChannelOnly = "splashtop.channels.secure";
        public static String UseThirdPartyCertificate = "splashtop.certificate.use-third-party";
        public static String AllowChromeExtension = "splashtop.chrome-extension.allow";
    }

    public static class Miracast {
        public static String Enable = "miracast.enable";
        public static String AllowWifiDirectConnections = "miracast.wifi-direct.connection.allow";
        public static String AllowMsMiceConnections = "miracast.ms-mice.connection.allow";
        public static String AutonomousGO = "miracast.wifi-direct.go.autonomous";
        public static String WifiDirectCountryCode = "miracast.wifi-direct.country-code";
        public static String PreferWifiDirect = "miracast.wifi-direct.prefer";
    }
    
    public static class WirlessAccessPoint {
        public static String Enable = "wifi.enable";
        public static String WifiSsid = "wifi.ssid";
        public static String WifiKey = "wifi.key";
        public static String WifiFrequency = "wifi.frequency";
    }
}
