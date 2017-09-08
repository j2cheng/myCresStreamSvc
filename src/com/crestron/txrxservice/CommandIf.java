package com.crestron.txrxservice;

public interface CommandIf {
    public void execute();
    public String getFeedbackMsg();
    public void setFbMsg(String arg);
    public String getSetFbMsg();
}

