package com.crestron.txrxservice.wc.ipc;

import com.crestron.txrxservice.wc.ipc.IWC_Callback;
import com.crestron.txrxservice.wc.ipc.WC_Connection;
import com.crestron.txrxservice.wc.ipc.WC_SessionOptions;

interface IWC_Service {
    // ********************************************************
    // SESSION

    // *******
    // open a new session
    //
    // will check USB, start server and return a positive session id on success
    // or a negative integer code in event of failure (-1 = in use, -2 = no USB
    // devices present)
    // when server is started a new username/password are generated to form URL
    // and new X509 certificate and privateKey are generated
    //
    // @param clientId  unique ID of the device/client that wants to open session
    // @param options   opens for opening the session
    //                  @see WC_SessionOptions
    // @return          success/failure result
    //                  when
    //                    result >= 0   ->  success, session ID
    //                    result == -1  ->  in use
    //                    result == -2  ->  no USB peripherals present
    int WC_OpenSession(in String clientId, in WC_SessionOptions options);

    // *******
    // get the open session connection pararameters
    //
    // WC connection contains URL (includes username and password as part of URL),
    // certificate, and private key as strings read from .pem files
    //
    // @param sessionId  session ID returned from open session
    //                   @see WC_OpenSession
    // @return           connection parameters
    //                   @see WC_Connection
    WC_Connection WC_GetConnectionParameters(in int sessionId);

    // *******
    // close the session
    //
    // errorcode or 0 for success
    //
    // @param sessionId  session ID returned from open session
    //                   @see WC_OpenSession
    // @return           success/failure result
    //                   when
    //                     result == 0   ->  success
    //                     result < 0    ->  failed
    int WC_CloseSession(in int sessionId);
    
    // ********************************************************
    // CALLBACK

    // *******
    // register callback
    //
    // @param cb  interface by consumers of wireless conferencing
    //            @see IWC_Callback
    void registerCallback(IWC_Callback cb);

    // *******
    // unregister callback
    //
    // @param cb  interface by consumers of wireless conferencing
    //            @see IWC_Callback
    void unregisterCallback(IWC_Callback cb);
}