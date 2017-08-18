package com.crestron.txrxservice;

public interface AirMedia 
{ 
	public final static String licenseFilePath = "/dev/shm/airmedia";

    public void recover();    
    
    public void show(int x, int y, int width, int height);   
    public void hide(boolean sendStopToSender);   
    public void hide(boolean sendStopToSender, boolean clear);   
    public boolean getSurfaceDisplayed();
    
    public void querySenderList(boolean sendAllUserFeedback);
    
    
    public void hideLoginCodePrompt();
    public void showLoginCodePrompt(int loginCode);
    public void setLoginCode(int loginCode);    
    public void setLoginCodeDisable();    
    public void setLoginCodePrompt(String loginCodePrompt);
    
    public void setModeratorEnable(boolean enable);
    
    public void set4in1ScreenEnable(boolean enable);
    
    public void resetConnections();    
    public void disconnectUser(int userId);   
    public void startUser(int userId);
    public void setUserPosition(int userId, int position);
    public void stopUser(int userId);
    public void stopAllUser();
    public void disconnectAllSenders();

    
    public void setOsdImage(String filePath);
    
    public void debugCommand(String debugCommand);
    
    public void setIpAddressPrompt(boolean enable); 
    public void setDomainNamePrompt(boolean enable);
    
    public void setSurfaceSize(int x, int y, int width, int height, boolean launch);
    
    public void setDisplayScreen(int displayId);
    
    public void setWindowFlag(int windowFlag);
    
    public void setStandbyScreen(int standbyScreen);
    
    public void setVideoTransformation(int x, int y, int w, int h);
    
    public void setAdapter(String address);
}
