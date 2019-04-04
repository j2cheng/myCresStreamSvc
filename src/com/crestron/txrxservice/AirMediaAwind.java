package com.crestron.txrxservice;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.crestron.txrxservice.CresStreamCtrl.AirMediaLoginMode;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;


// TODO: THIS INTERFACE IS DEAD AND SHOULD BE REMOVED!!!

//public class AirMediaAwind
public class AirMediaAwind implements AirMedia
{
    CresStreamCtrl mStreamCtl;
    Context mContext;
    String TAG = "TxRx Awind AirMedia"; 
    private final static String commandIntent = "com.awindinc.receiver.airmedia.command";
	private boolean surfaceDisplayed = false;
	private Map<Integer, Integer> idMap = new ConcurrentHashMap<Integer, Integer>();
    
    BroadcastReceiver feedback = null;
    
    private int translateAwindId(int awindId)
    {
    	int key = -1;
    	for (Map.Entry<Integer, Integer> e : idMap.entrySet()) 
    	{
    		if ((int)e.getValue() == awindId)
    		{
    			key = e.getKey();
    			break;
    		}
    	}
    	
    	return key;
    }
    
    private int translateSenderId(int senderId)
    {
    	int value = -1;
    	for (Map.Entry<Integer, Integer> e : idMap.entrySet()) 
    	{
    		if ((int)e.getKey() == senderId)
    		{
    			value = e.getValue();
    			break;
    		}
    	}
    	
    	return value;
    }
    
    private int addIdToMap(int awindId)
    {
    	int availableKey = -1;
    	
    	// Check if already added first
    	availableKey = translateAwindId(awindId);
    	if (availableKey != -1)
    		return availableKey;
    	
    	// Add to map
    	for (int i = 1; i <= 32; ++i)
    	{
    		if (!idMap.containsKey(i))
    		{
    			idMap.put(i, awindId); 
    			availableKey = i;
    			break;
    		}
    	}    	
    	
    	if (availableKey == -1)
    	{
    		Log.w(TAG, "Max number of AirMedia senders reached!");
    	}
    	
    	return availableKey;
    }
    
    private void removeIdFromMap(int awindId)
    {
    	int key = -1;
    	for (Map.Entry<Integer, Integer> e : idMap.entrySet()) 
    	{    	  
    	    if ((int)e.getValue() == awindId)
    	    {    	    	
    	    	key = e.getKey();
    	    	break;
    	    }
    	}
    	
    	if (key != -1)
    		idMap.remove(key);
    }
    
    public AirMediaAwind(CresStreamCtrl streamCtl) 
    {
    	mStreamCtl = streamCtl;
    	mContext = (Context)mStreamCtl;
    	
    	mStreamCtl.setHostName("");
    	mStreamCtl.setDomainName("");
    	Log.i(TAG, "HostName="+mStreamCtl.hostName+"   DomainName="+mStreamCtl.domainName);
    	mStreamCtl.sendAirMediaConnectionAddress();  

    	shutDownAirMediaAwind();	// In case AirMediaAwind was already running shut it down
    	
    	registerBroadcasts();
    	
		String host = MiscUtils.getHostName("AirMedia");

    	// Launch service
    	Intent intent = mContext.getPackageManager().getLaunchIntentForPackage("com.awindinc.receiver.airmedia");
    	if (intent != null) {
    		mContext.startActivity(intent);
    		intent.putExtra("receiver_name", host);
    		intializeDisplay();
    		set4in1ScreenEnable(false); // TODO: Remove this when quad view is eventually enabled
    		setStandbyScreen(0);	// We currently do not want the AirMedia OSD to be visible
    	}
    	else {
        	Log.e(TAG, "Failed to launch AirMediaAwind!");
        }
    }
    
    public void onDestroy(){
    	Log.e(TAG, "AirMediaAwind Class destroyed!!!");
    	
    	surfaceDisplayed = false;
    	
    	//shutDownAirMediaAwind();
        
    	unregisterBroadcasts();
    }
    
    public void shutDownAirMediaAwind(){
    	Intent i = new Intent(commandIntent);
        i.putExtra("command", "close_receiver");
        mContext.sendBroadcast(i);
    }
    
    public void close()
    {
    	
    }
    
    public void setVideoTransformation(int x, int y, int w, int h)
    {
    	
    }
    
    public void setAdapter(String address)
    {
    	
    }
    
    public void debugCommand(String debugCommand)
    {
    	
    }
    
    public void airmediaProcessDebugCommand(String debugCommand)
    {
    	
    }
    
    public void clearCache()
    {
    	
    }
    
    public boolean airMediaIsUp()
    {
    	return true;
    }
    
    public void recover(){
    	if (mStreamCtl.userSettings.getAirMediaLaunch(0))
    	{
    		Log.i(TAG, "Force recovering AirMedia");
    		hide(0, true);	// Need to stop sender in order to recover

    		try { Thread.sleep(5000); } catch (InterruptedException e) {};		

    		int width = mStreamCtl.userSettings.getAirMediaWidth();
			int height = mStreamCtl.userSettings.getAirMediaHeight();
			
			if ((width == 0) && (height == 0))
			{
				Point size = mStreamCtl.getDisplaySize();

				width = size.x;
				height = size.y;
			}
    		show(0, mStreamCtl.userSettings.getAirMediaX(),
    				mStreamCtl.userSettings.getAirMediaY(),
    				width,
    				height);
    	}
    }
    
    public void show(int sessionId, int x, int y, int width, int height)
    {
    	if (surfaceDisplayed == false)
    	{
	    	surfaceDisplayed = true;
	    	
	    	// Set z-order and display
	    	setWindowFlag(mStreamCtl.userSettings.getAirMediaWindowFlag());
	    	setDisplayScreen(mStreamCtl.userSettings.getAirMediaDisplayScreen());
	
	    	// Show/Hide display items based on current settings
	    	//intializeDisplay();
	
	    	//show surface
	    	setSurfaceSize(x,y,width,height, true);
    	}
    	else
    		Log.i(TAG, "AirMedia already shown, ignoring request");
    }    
    
    public void hide(int sessionId, boolean sendStopToSender, boolean clear)
    {
    	if (surfaceDisplayed == true)
    	{
    		surfaceDisplayed = false;

    		Intent i = new Intent(commandIntent);
    		i.putExtra("command", "hide_receiver_info");
    		mContext.sendBroadcast(i);
    		
    		if (sendStopToSender)
    			stopAllSenders(); // Inform senders that stream is stopped/hidden
    	}
    	else
    		Log.i(TAG, "AirMedia already hidden, ignoring request");
    }
    
    public void hide(int sessionId, boolean sendTopToSender)
    {
    	hide(0, sendTopToSender, true);
    }
    
    public boolean getSurfaceDisplayed()
    {
    	return surfaceDisplayed;
    }
    
    public void registerBroadcasts() {
    	Log.i(TAG, "registerBroadcasts !");

    	feedback = new BroadcastReceiver()
    	{
    		public void onReceive(Context paramAnonymousContext, final Intent paramAnonymousIntent)
    		{
    			// We need to make sure that the broadcastReceiver thread is not stuck processing start/stop requests
    			// Therefore we will run all commands through a worker thread
    			new Thread(new Runnable() {
    				public void run() {
    					Bundle bundle = paramAnonymousIntent.getExtras();
    					for (String key : bundle.keySet()) {
    					    Object value = bundle.get(key);
    					    Log.i(TAG, MiscUtils.stringFormat("Received airMedia feedback: %s %s (%s)", key,  
    					        value.toString(), value.getClass().getName()));
    					}
    					String eventName = paramAnonymousIntent.getStringExtra("event");

    					if (eventName.equals("sender_login"))
    					{
    						int awindId = paramAnonymousIntent.getIntExtra("sender_id", -1);
    						int senderId = addIdToMap(awindId);
    						Log.i(TAG, "Adding Id to map, userId: " + senderId + " awindId: " + awindId);
    						if ((senderId > 0) && (senderId <= 32))
    						{
    							mStreamCtl.userSettings.setAirMediaUserConnected(true, senderId);
    							mStreamCtl.sendAirMediaNumberUserConnected();
    						}
    						else
    							Log.w(TAG, "Received invalid sender Id of " + awindId);
    						
    					}
    					else if (eventName.equals("sender_logout"))
    					{
    						int awindId = paramAnonymousIntent.getIntExtra("sender_id", -1);
    						int senderId = translateAwindId(awindId);
    						removeIdFromMap(awindId);
    						Log.i(TAG, "Removing Id from map awindId: " + awindId);
    						if ((senderId > 0) && (senderId <= 32))
    						{
    							mStreamCtl.userSettings.setAirMediaUserConnected(false, senderId);
    							mStreamCtl.sendAirMediaNumberUserConnected();
    						}
    						else
    							Log.w(TAG, "Received invalid sender Id of " + awindId);
    					}
    					else if (eventName.equals("start_play_done"))
    					{
    					}
    					else if (eventName.equals("stop_play_done"))
    					{
    					}
    					else if (eventName.equals("provider_change"))
    					{
    						querySenderList(false);
    					}
    					else if (eventName.equals("set_display_error"))
    					{
    						int errorCode = paramAnonymousIntent.getIntExtra("value", -1);
    						Log.w(TAG, "Received set_display_error code " + errorCode);
    					}
    					else if (eventName.equals("restart"))
    					{
    						mStreamCtl.RestartAirMedia();
    					}
    					else
    					{
    						Log.i(TAG, "Received unknown event '" + eventName + "' from airMedia app");
    					}
       				}
    			}).start();
    		}
    	};
    	IntentFilter loginIntentFilter = new IntentFilter("com.awindinc.receiver.airmedia.event");
        mContext.registerReceiver(feedback, loginIntentFilter);        
    }
    
    public void querySenderList(boolean sendAllUserFeedback)
    {
    	int status = 0;  // 0 = no displayed video, 1 = at least 1 video presenting
    	boolean[] sentUserFeedback = null;
    	
    	if (sendAllUserFeedback)
    	{
	    	// Create list of all 32 user slots and mark off which ones are not connected
	    	sentUserFeedback = new boolean[32];
	    	for (int i = 0; i < 32; i++) { sentUserFeedback[i] = false; } // initialize all to false
    	}
    	
    	ContentResolver resolver = mContext.getContentResolver();
		String[] projection = new String[]{"_id","name","ip","quadrant","status"};
		Cursor cursor = resolver.query(Uri.parse("content://com.awindinc.receiver.airmedia.provider/sender"), projection, null, null, null);
		if(cursor != null){
		  if(cursor.moveToFirst()){
		    do{
		    	int awindId = cursor.getInt(0);
		    	
		    	int userId = addIdToMap(awindId);
		    	Log.i(TAG, "Adding Id to map, userId: " + userId + " awindId: " + awindId);
		    	if ((userId >= 1) && (userId <= 32)) //make sure in range
		    	{
		    		// If sending all userFeedback mark which users feedback already sent on
		    		if (sentUserFeedback != null)
		    			sentUserFeedback[userId - 1] = true;

		    		boolean connected = (cursor.getInt(4) == 1);
		    		if (connected)
		    			status = 1;

		    		mStreamCtl.userSettings.setAirMediaUserConnected(true, userId);
		    		mStreamCtl.sendAirMediaUserFeedbacks(userId, cursor.getString(1), cursor.getString(2), cursor.getInt(3), connected);
		    	}
		    }while(cursor.moveToNext());
		  }
		}else{
		   Log.e(TAG, "Failed to query sender information for AirMedia");
		   return;
		}
		
		mStreamCtl.sendAirMediaStatus(status);		
		
		if (sentUserFeedback != null)
		{
            // Send defaults for all user connections that weren't found in query
            for(int i = 0; i < 32; i++)
            {
                    if (sentUserFeedback[i] == false)
                    {
                            int awindId = translateSenderId(i + 1);
                            if (awindId != -1)
                            {
                                    removeIdFromMap(awindId);       // Remove from mapping if existing
                                    Log.i(TAG, "Removing Id from map awindId: " + awindId);
                            }
                            mStreamCtl.userSettings.setAirMediaUserConnected(false, i + 1);
                            mStreamCtl.sendAirMediaUserFeedbacks(i + 1, "", "", 0, false);
                    }
            }
		}
		
		mStreamCtl.sendAirMediaNumberUserConnected();
    }
    
    public void intializeDisplay()
    {
    	// Show/Hide login code depending on setting
    	if (mStreamCtl.userSettings.getAirMediaLoginMode() == AirMediaLoginMode.Disabled.ordinal())
    		setLoginCodeDisable();
    	else
    		setLoginCode(mStreamCtl.userSettings.getAirMediaLoginCode());
    	
        if (mStreamCtl.userSettings.getAirMediaDisplayLoginCode() && 
        		mStreamCtl.userSettings.getAirMediaLoginMode() != AirMediaLoginMode.Disabled.ordinal())
        {
        	showLoginCodePrompt(mStreamCtl.userSettings.getAirMediaLoginCode());
        }
        else
        {
        	hideLoginCodePrompt();
        }
        
        // Show/Hide IP address depending on setting
        setIpAddressPrompt(mStreamCtl.userSettings.getAirMediaIpAddressPrompt());

        // Set window display and flag (z order control)
        setDisplayScreen(mStreamCtl.userSettings.getAirMediaDisplayScreen());
        setWindowFlag(mStreamCtl.userSettings.getAirMediaWindowFlag());
        try {
            setReceiverResolution(Integer.parseInt(mStreamCtl.hdmiOutput.getHorizontalRes()), Integer.parseInt(mStreamCtl.hdmiOutput.getVerticalRes()));
        } catch (NumberFormatException ex) {
            Log.i(TAG, "Could not set receiver resolution");
        }
    }
    
    public void unregisterBroadcasts() {
    	mContext.unregisterReceiver(feedback);
    }
    
    public void hideLoginCodePrompt()
    {
    	setLoginCodePrompt("");
    }
    
    public void showLoginCodePrompt(int loginCode)
    {
    	setLoginCodePrompt(MiscUtils.stringFormat("Code:%04d", loginCode));
    	
    	// need to send login_code_prompt intent again with no text to make sure it displays
    	Intent i = new Intent(commandIntent);
        i.putExtra("command", "login_code_prompt");
        mContext.sendBroadcast(i);
    }
    
    public void setLoginCode(int loginCode)
    {
        try
        {
                Intent i = new Intent(commandIntent);
                i.putExtra("command", "login_code");
                i.putExtra("value", loginCode);
                mContext.sendBroadcast(i);
                        Log.i(TAG, "Sending Login Code " + loginCode);
        }
        catch (Exception e)
        {
                Log.e(TAG, "Exception setting login code");
        }
    }
    
    public void setLoginCodeDisable()
    {
    	Intent i = new Intent(commandIntent);
        i.putExtra("command", "login_code");
        mContext.sendBroadcast(i);
    }
    
    public void setLoginCodePrompt(String loginCodePrompt)
    {
    	Intent i = new Intent(commandIntent);
        i.putExtra("command", "login_code_prompt");
        i.putExtra("value", loginCodePrompt);
        mContext.sendBroadcast(i);
    }
    
    public void setModeratorEnable(boolean enable)
    {    	
    	Intent i = new Intent(commandIntent);
        i.putExtra("command", "conference_moderator");
        i.putExtra("value", (enable ? 1 : 0));
        mContext.sendBroadcast(i);
    }
    
    public void set4in1ScreenEnable(boolean enable)
    {    	
    	Intent i = new Intent(commandIntent);
        i.putExtra("command", "split_screen");
        i.putExtra("value", (enable ? 1 : 0));
        mContext.sendBroadcast(i);
    }
    
    public void resetConnections()
    {    	
    	Intent i = new Intent(commandIntent);
        i.putExtra("command", "reset_connections");
        mContext.sendBroadcast(i);
    }
    
    public void disconnectUser(int userId)
    {    	
    	Intent i = new Intent(commandIntent);
        i.putExtra("command", "disconnect_sender");
        i.putExtra("sender", userId);
        mContext.sendBroadcast(i);
    }
    
    public void startUser(int userId)
    {    	
    	// TODO: when start works check if this method works with app
    	Intent i = new Intent(commandIntent);
        i.putExtra("command", "start_play");
        i.putExtra("sender", userId);
        mContext.sendBroadcast(i);
    }
    
    public void setUserPosition(int userId, int position)
    {    	
    	// Translate from Crestron enum to AirMedia enum
    	int quadrant = 0;
    	switch (position)
    	{
    	case 1: //Crestron: topLeft, AirMedia: topLeft
    	case 2: //Crestron: topRight, AirMedia: topRight
    	case 3: //Crestron: bottomLeft, AirMedia: bottomLeft
    	case 4: //Crestron: bottomRight, AirMedia: bottomRight
    		quadrant = position;
    		break;
    	case 5: //Crestron: fullscreen
    		quadrant = 128; //AirMedia: fullscreen
    		break;
		default:
    			break;
    	}
    	
    	Intent i = new Intent(commandIntent);
        i.putExtra("command", "start_play");
        i.putExtra("sender", userId);
        i.putExtra("quadrant", quadrant);
        mContext.sendBroadcast(i);
    }
    
    public void stopUser(int userId)
    {    	
    	Intent i = new Intent(commandIntent);
        i.putExtra("command", "stop_play");
        i.putExtra("sender", userId);
        mContext.sendBroadcast(i);
    }
    
    public void stopAllUser()
    {    	
    	Intent i = new Intent(commandIntent);
        i.putExtra("command", "stop_play");
        i.putExtra("sender", (int)0);
        mContext.sendBroadcast(i);
    }
    
    public void setOsdImage(String filePath)
    {
    	Intent i = new Intent(commandIntent);
        i.putExtra("command", "background_image");
        i.putExtra("value", filePath);
        mContext.sendBroadcast(i);
    }
    
    public void setIpAddressPrompt(boolean enable)
    {    	
        if (enable)
        {
        	String ip;
        	
        	if (mStreamCtl.userSettings.getAirMediaAdaptorSelect() == 0)
        	{       		
        		// Use adaptor 0
        		ip = mStreamCtl.userSettings.getDeviceIp();
        	}
        	else if (mStreamCtl.userSettings.getAirMediaAdaptorSelect() == 1)
        	{
        		// Use adaptor 1
        		ip = mStreamCtl.userSettings.getAuxiliaryIp();
        	}
        	else
        	{
        		// Invalid selection setting use adaptor 0
        		Log.w(TAG, MiscUtils.stringFormat("Invalid adaptor select value of %d, using adaptor 0", mStreamCtl.userSettings.getAirMediaAdaptorSelect()));
        		ip = mStreamCtl.userSettings.getDeviceIp();
        	}
        	
        	Intent i = new Intent(commandIntent);
            i.putExtra("command", "address_prompt");
        	i.putExtra("value", MiscUtils.stringFormat("IP:%s", ip));
        	mContext.sendBroadcast(i);
        	
        	// Send with no value to make sure IP address displays
        	Intent forceDisplay = new Intent(commandIntent);
        	forceDisplay.putExtra("command", "address_prompt");
        	mContext.sendBroadcast(forceDisplay);
        }
        else
        {
        	Intent i = new Intent(commandIntent);
            i.putExtra("command", "address_prompt");
        	i.putExtra("value", "");
        	mContext.sendBroadcast(i);
        }
       
    }
    
    public void setDomainNamePrompt(boolean enable)
    {
        if (enable)
        {
        	Intent i = new Intent(commandIntent);
            i.putExtra("command", "domain_name_prompt");
        	i.putExtra("value", "Todo find where to query our DomainName");
        	mContext.sendBroadcast(i);
        	
        	// Send with no value to make sure domain name displays
        	Intent forceDisplay = new Intent(commandIntent);
        	forceDisplay.putExtra("command", "domain_name_prompt");
        	mContext.sendBroadcast(forceDisplay);
        }
        else
        {
        	Intent i = new Intent(commandIntent);
            i.putExtra("command", "domain_name_prompt");
        	i.putExtra("value", "");
        	 mContext.sendBroadcast(i);
        }        
    }
    
    private void showSurface(boolean enable)
    {
    	if (Boolean.parseBoolean(mStreamCtl.hdmiOutput.getSyncStatus()))
    	{
    		Intent i = new Intent(commandIntent);        
    		if (enable)
    			i.putExtra("command", "show_receiver_info");
    		else
    			i.putExtra("command", "hide_receiver_info");
    		mContext.sendBroadcast(i);
    	}
    }
    
    // Don't use this because it messes up the surfaces
//    public void setSurfaceFullscreen()
//    {    	
//    	Intent i = new Intent(airMediaIntent);
//    	i.putExtra("command", "set_receiver_size");
//    	i.putExtra("full_screen", (int)1);
//    	
//    	// We have to hide, modify, then display surface
//    	showSurface(false);
//        mContext.sendBroadcast(i);
//        showSurface(true);
//    }
    
    public void setSurfaceSize(int x, int y, int width, int height, boolean launch)
    {
    	Intent i = new Intent(commandIntent);
    	i.putExtra("command", "set_receiver_size");
    	i.putExtra("full_screen", (int)0);    	
    	i.putExtra("full_screen", (int)0);
    	i.putExtra("x", x);
    	i.putExtra("y", y);
    	i.putExtra("width", width);
    	i.putExtra("height", height);
        
    	// We have to hide, modify, then display surface
    	if (launch == false)
    		showSurface(false);	//Don't hide surface if we are launching app, otherwise it messes up window
        mContext.sendBroadcast(i);
        showSurface(true);
    }
    
    public void setDisplayScreen(int displayId)
    {
    	Intent i = new Intent(commandIntent);
    	i.putExtra("command", "set_display_screen");
    	i.putExtra("value", (int)displayId);
    	mContext.sendBroadcast(i);
    }
    
    public void setWindowFlag(int windowFlag)
    {
    	Intent i = new Intent(commandIntent);
    	i.putExtra("command", "set_window_flag");
    	i.putExtra("value", (int)windowFlag);
    	mContext.sendBroadcast(i);
    }
    
    public void setStandbyScreen(int standbyScreen)
    {
    	// Set 0 for standby screen disabled, 1 standby screen enabled 
    	Intent i = new Intent(commandIntent);
    	i.putExtra("command", "set_standby_screen");
    	i.putExtra("value", (int)standbyScreen);
    	mContext.sendBroadcast(i);
    }
    
    public void setReceiverResolution(int width, int height)
    {
        // Should improve performance by removing unnecessary scaling
        Intent i = new Intent(commandIntent);
        i.putExtra("command", "set_resolution");
        i.putExtra("width", (int)width);
        i.putExtra("height", (int)height);
        mContext.sendBroadcast(i);
    }

    private void stopAllSenders()
    {
    	for (int i = 1; i <= 32; i++) // We handle airMedia user ID as 1 based
    	{
    		if (mStreamCtl.userSettings.getAirMediaUserConnected(i))
    		{
    			int awindId = translateSenderId(i);
    			if (awindId != -1)
    				stopUser(awindId);
    			else
    				Log.w(TAG, "Could not find senderId " + i + "!");
    		}
    	}
    }
    
    public void disconnectAllSenders()
    {
    	for (int i = 1; i <= 32; i++) // We handle airMedia user ID as 1 based
    	{
    		if (mStreamCtl.userSettings.getAirMediaUserConnected(i))
    		{
    			int awindId = translateSenderId(i);
    			if (awindId != -1)
    				disconnectUser(awindId);
    			else
    				Log.w(TAG, "Could not find senderId " + i + "!");
    		}
    	}
    }
    
    public void setProjectionLock(boolean enable)
    {
    	
    }
    
    public static boolean checkAirMediaLicense()
    {
    	boolean licensed = false;
    	try
    	{
    		licensed = Integer.parseInt(MiscUtils.readStringFromDisk(licenseFilePath)) == 1;
    	} catch (NumberFormatException e) {} // If file DNE or corrupt not licensed
    	return licensed;
    }
    
    public void setOrderedLock(boolean lock, String functionName)
    {
    	// Not needed for Awind
    }

	public void setAirMediaMiracast(boolean enable)
	{
    	// Not needed for Awind
	}

    public void setAirMediaMiracastWifiDirectMode(boolean enable)
    {
    	// Not needed for Awind
    }

    public void setAirMediaMiracastWirelessOperatingRegion(int regionCode)
    {
    	// Not needed for Awind
    }
    
    public void setAirMediaIsCertificateRequired(boolean enable)
    {
    	// Not needed for Awind
    }

    public void setAirMediaOnlyAllowSecureConnections(boolean enable)
    {
    	// Not needed for Awind
    }
}
