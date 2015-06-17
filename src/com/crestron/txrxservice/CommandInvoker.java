package com.crestron.txrxservice;

public class CommandInvoker{
    private CommandIf myCmd;

    public void setCommand(CommandIf cmd){
        this.myCmd = cmd;
    }

    public void set(){
        myCmd.execute();
        synchronized ( CresStreamCtrl.saveSettingsPendingUpdate ) {  
        	CresStreamCtrl.saveSettingsUpdateArrived = true;        
            CresStreamCtrl.saveSettingsPendingUpdate.notify();
        }
    }
    
    public String get(){
       return myCmd.getFeedbackMsg();
    }
}
