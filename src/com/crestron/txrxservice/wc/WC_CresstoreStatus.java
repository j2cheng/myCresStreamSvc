package com.crestron.txrxservice.wc;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import com.crestron.txrxservice.CresStreamCtrl;
import com.crestron.txrxservice.wc.ipc.WC_SessionFlags;

import android.util.Log;

// Below class is to build the CresNext Object of Wireless Conferencing
public class WC_CresstoreStatus {
    private static final String TAG="WC_CresstoreStatus";
    public CresStreamCtrl mStreamCtrl = null;
    Gson gson = new GsonBuilder().create();

    static final int Publish=1;
    static final int PublishAndSave=2;

    DeviceObject DeviceObject;

    class DeviceObject { 
        Device Device;
        class Device {
            AirMedia AirMedia;
            class AirMedia {
                WirelessConferencing WirelessConferencing;
                class WirelessConferencing {
                    Status Status;
                    class Status {
                        Boolean IsMicInUse;
                        Boolean IsMicDetected;

                        Boolean IsSpeakerDetected;

                        Boolean IsCameraInUse;
                        Boolean IsCameraDetected;
                        String  CameraResolution;

                        String ConferencingStatus;
                    }

                    public WirelessConferencing()
                    {
                        Status = new Status();
                    }
                }

                public AirMedia()
                {
                    WirelessConferencing = new WirelessConferencing();
                }
            }

            public Device()
            {
                AirMedia = new AirMedia();
            }
        }

        public DeviceObject()
        {
            Device = new Device();
        }
    }

    public WC_CresstoreStatus(CresStreamCtrl streamCtrl)
    {
        mStreamCtrl = streamCtrl;
        DeviceObject = new DeviceObject();
    }

    // Below class is to build the CresNext Object of Wireless Conferencing
    public void reportWCInUseStatus(WC_SessionFlags flagInUse, boolean enable)
    {
        if((flagInUse == WC_SessionFlags.AudioAndVideo) || (flagInUse == WC_SessionFlags.None))
        {
            Log.v(TAG,"reportWCInUseStatus: update to Crestore MIC and Camera in use status to " + enable);
            this.DeviceObject.Device.AirMedia.WirelessConferencing.Status.IsMicInUse = enable;
            this.DeviceObject.Device.AirMedia.WirelessConferencing.Status.IsCameraInUse = enable;
        }
        else if(flagInUse == WC_SessionFlags.Audio)
        {
            Log.v(TAG,"reportWCInUseStatus: update to Crestore only MIC in use status to " + enable);
            this.DeviceObject.Device.AirMedia.WirelessConferencing.Status.IsMicInUse = enable;
        }
        else if(flagInUse == WC_SessionFlags.Video)
        {
            Log.v(TAG,"reportWCInUseStatus: update to Crestore Camera in use status to " + enable);
            this.DeviceObject.Device.AirMedia.WirelessConferencing.Status.IsCameraInUse = enable;
        }
        String sessionWirelessConferencing = gson.toJson(this.DeviceObject);
        Log.i(TAG,  "reportWCInUseStatus: WirelessConferencingJSON=" + sessionWirelessConferencing);

        mStreamCtrl.SendToCresstore(sessionWirelessConferencing, PublishAndSave);
    }
    
    public void reportWCDeviceStatus(Boolean IsCameraDetected, 
                                        Boolean IsMicDetected, 
                                        Boolean IsSpeakerDetected, 
                                        String CameraResolution, 
                                        String ConferencingStatus)
    {
        Log.v(TAG,"reportWCDeviceStatus: IsCameraDetected:"
                    + IsCameraDetected + " IsMicDetected:" 
                    + IsMicDetected + " IsSpeakerDetected:" 
                    + IsSpeakerDetected + " CameraResolution:" 
                    + CameraResolution + " ConferencingStatus:" 
                    + ConferencingStatus);

        this.DeviceObject.Device.AirMedia.WirelessConferencing.Status.IsCameraDetected = IsCameraDetected;
        this.DeviceObject.Device.AirMedia.WirelessConferencing.Status.IsMicDetected = IsMicDetected;
        this.DeviceObject.Device.AirMedia.WirelessConferencing.Status.IsSpeakerDetected = IsSpeakerDetected;

        this.DeviceObject.Device.AirMedia.WirelessConferencing.Status.CameraResolution = CameraResolution;
        this.DeviceObject.Device.AirMedia.WirelessConferencing.Status.ConferencingStatus = ConferencingStatus;
        
        final String sessionWCStatus = gson.toJson(this.DeviceObject);
        Log.i(TAG,  "reportWCDeviceStatus: WirelessConferencingStatusJSON=" + sessionWCStatus);

        //Needs to be in separate thread for NetworkOnMainThreadException, since SendtoCrestore occurs in the flow
        mStreamCtrl.SendToCresstore(sessionWCStatus, PublishAndSave);
    }
}
