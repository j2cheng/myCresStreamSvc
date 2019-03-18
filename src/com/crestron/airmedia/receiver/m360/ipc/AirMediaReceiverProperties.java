package com.crestron.airmedia.receiver.m360.ipc;

public final class AirMediaReceiverProperties {

    public static class Splashtop {
        public static String SecureChannelOnly = "splashtop.channels.secure";
        public static String UseThirdPartyCertificate = "splashtop.certificate.use-third-party";
    }

    public static class Miracast {
        public static String Enable = "miracast.enable";
        public static String AllowWifiDirectConnections = "miracast.wifi-direct.connection.allow";
        public static String AutonomousGO = "miracast.wifi-direct.go.autonomous";
    }
}
