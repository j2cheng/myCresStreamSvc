
package com.crestron.txrxservice.wc.ipc;

import com.crestron.txrxservice.wc.ipc.WC_Status;

interface IWC_Callback {
    void onClientConnected(String clientIP);
	void onClientDisconnected(String clientIP);
	void onServerStart();
	void onServerStop();
	void onStatusChanged(in WC_Status status);
}
