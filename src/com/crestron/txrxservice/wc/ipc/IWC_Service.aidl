package com.crestron.txrxservice.wc.ipc;

import com.crestron.txrxservice.wc.ipc.WC_Connection;
import com.crestron.txrxservice.wc.ipc.IWC_Callback;

interface IWC_Service {
    // will check USB, start server and return a positive session id on success or a negative integer code in event of failure (-1 = in use, -2 = no USB devices present)
    // when server is started a new username/password are generated to form URL and new X509 certificate and privateKey are generated
	int WC_OpenSession(in String userId);

	// WC connection contains URL (includes username and password as part of URL), certificate, and private key as strings read from .pem files
	WC_Connection WC_GetConnectionParameters(in int id);

    // errorcode or 0 for success
	int WC_CloseSession(in int id);
	
	void registerCallback(IWC_Callback cb);
	void unregisterCallback(IWC_Callback cb);
}