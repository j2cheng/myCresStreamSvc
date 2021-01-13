
package com.crestron.txrxservice.wc.ipc;

interface IWC_Callback {
    void onClientConnected(String clientIP);
	void onClientDisconnected(String clientIP);
	void onServerStart();
	void onServerStop();
}
