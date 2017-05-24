package com.crestron.airmedia.utilities.delegates;

public class ObserverBase<SOURCE> {
    public void onError(SOURCE sender, String module, int code, String message) { }
}
