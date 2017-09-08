package com.crestron.txrxservice;

public class CommandInvoker{
    private CommandIf myCmd;

    public void setCommand(CommandIf cmd){
        this.myCmd = cmd;
    }

    public void set(){
        myCmd.execute();        
    }
    
    public String get(){
       return myCmd.getFeedbackMsg();
    }
    
    public void setFbMsg(String arg) {
    	myCmd.setFbMsg(arg);
    }
    
    public String getSetFb() {
    	return myCmd.getSetFbMsg();
    }
}
