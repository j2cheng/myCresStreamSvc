
package com.crestron.txrxservice.wc.ipc;

import com.crestron.txrxservice.wc.ipc.WC_Status;
import com.crestron.txrxservice.wc.ipc.WC_UsbDevices;
import com.crestron.txrxservice.wc.ipc.WC_Error;

interface IWC_Callback {
    // ********************************************************
    // STATUS

    // *******
    // wireless conferencing status
    //
    // @param status  current status,
    //                will get event immediately when callback is registered
    //                @see WC_Status
    //                @see IWC_Callback
    void onStatusChanged(in WC_Status status);

    // ********************************************************
    // STATUS

    // *******
    // usb devices status
    //
    // @param status  current usb devices status,
    //                will get event immediately when callback is registered
    //                @see WC_UsbDevices
    //                @see IWC_Callback
    void onUsbDevicesChanged(in WC_UsbDevices devices);

    // ********************************************************
    // ERROR NOTIFICATION

    // *******
    // error notification
    //
    // @param error  error notification
    //                @see WC_Error
    //                @see IWC_Callback
    void onError(in WC_Error error);
}
