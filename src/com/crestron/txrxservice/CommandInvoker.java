package com.crestron.txrxservice;

public class CommandInvoker{
    private CommandIf myCmd;

    public void setCommand(CommandIf cmd){
        this.myCmd = cmd;
    }

    public void set(){
        myCmd.execute();
        CresStreamCtrl.saveSettingsPendingUpdate = true;
    }
    
    public String get(){
       return myCmd.getFeedbackMsg();
    }
}
