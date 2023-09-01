package com.crestron.txrxservice;

public abstract class StatusChangeListener
{

    public abstract void onChanged(int perId, String desc, int status);
};
