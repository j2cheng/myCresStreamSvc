package com.crestron.txrxservice;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

public class AirMedia 
{
    CresStreamCtrl mStreamCtl;
    Context mContext;
    String TAG = "TxRx AirMedia"; 
    private final static String commandIntent = "com.awindinc.receiver.airmedia.command";
	private final static String licenseFilePath = "/dev/shm/airmedia";
	private boolean surfaceDisplayed = false;
    
    BroadcastReceiver feedback = null;
    
    public AirMedia(CresStreamCtrl streamCtl) 
    {
    	mStreamCtl = streamCtl;
    	mContext = (Context)mStreamCtl;
    	registerBroadcasts();
    	
    	// Launch service
    	Intent intent = mContext.getPackageManager().getLaunchIntentForPackage("com.awindinc.receiver.airmedia");
    	if (intent != null) {
    		mContext.startActivity(intent);
    		intent.putExtra("receiver_name", "AirMedia");
    		intializeDisplay();
    	}
    	else {
        	Log.e(TAG, "Failed to launch Airmedia!");
        }
    }
    
    public void onDestroy(){
    	Log.e(TAG, "AirMedia Class destroyed!!!");
    	
    	surfaceDisplayed = false;
    	
    	Intent i = new Intent(commandIntent);
        i.putExtra("command", "close_receiver");
        mContext.sendBroadcast(i);
        
    	unregisterBroadcasts();
    }
    
    public void show(int x, int y, int width, int height)
    {
    	if (surfaceDisplayed == false)
    	{
	    	surfaceDisplayed = true;
	    	
	    	// Set z-order and display
	    	setWindowFlag(mStreamCtl.userSettings.getAirMediaWindowFlag());
	    	setDisplayScreen(mStreamCtl.userSettings.getAirMediaDisplayScreen());
	
	    	// Show/Hide display items based on current settings
	    	intializeDisplay();
	
	    	//show surface
	    	setSurfaceSize(x,y,width,height, true);
    	}
    	else
    		Log.i(TAG, "AirMedia already shown, ignoring request");
    }    
    
    public void hide()
    {
    	if (surfaceDisplayed == true)
    	{
    		surfaceDisplayed = false;

    		Intent i = new Intent(commandIntent);
    		i.putExtra("command", "hide_receiver_info");
    		mContext.sendBroadcast(i);
    	}
    	else
    		Log.i(TAG, "AirMedia already hidden, ignoring request");
    }
    
    public boolean getSurfaceDisplayed()
    {
    	return surfaceDisplayed;
    }
    
    public void registerBroadcasts() {
    	Log.d(TAG, "registerBroadcasts !");

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
    					    Log.d(TAG, String.format("Received airMedia feedback: %s %s (%s)", key,  
    					        value.toString(), value.getClass().getName()));
    					}
    					String eventName = paramAnonymousIntent.getStringExtra("event");

    					if (eventName.equals("sender_login"))
    					{
    						int senderId = paramAnonymousIntent.getIntExtra("sender_id", -1);
    						if ((senderId > 0) && (senderId <= 32))
    						{
    							mStreamCtl.userSettings.setAirMediaUserConnected(true, senderId);
    							mStreamCtl.sendAirMediaNumberUserConnected();
    						}
    						else
    							Log.w(TAG, "Received invalid sender Id of " + senderId);
    						
    					}
    					else if (eventName.equals("sender_logout"))
    					{
    						int senderId = paramAnonymousIntent.getIntExtra("sender_id", -1);
    						if ((senderId > 0) && (senderId <= 32))
    						{
    							mStreamCtl.userSettings.setAirMediaUserConnected(false, senderId);
    							mStreamCtl.sendAirMediaNumberUserConnected();
    						}
    						else
    							Log.w(TAG, "Received invalid sender Id of " + senderId);
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
    					else
    					{
    						Log.d(TAG, "Received unknown event '" + eventName + "' from airMedia app");
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
		    	int userId = cursor.getInt(0);
		    	
		    	// If sending all userFeedback mark which users feedback already sent on
		    	if ((sentUserFeedback != null) && (userId >= 1) && (userId <= 32)) //make sure in range
		    		sentUserFeedback[userId - 1] = true;
		    	
		    	boolean connected = (cursor.getInt(4) == 1);
		    	if (connected)
		    		status = 1;

		    	mStreamCtl.sendAirMediaUserFeedbacks(userId, cursor.getString(1), cursor.getString(2), cursor.getInt(3), connected);
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
					mStreamCtl.sendAirMediaUserFeedbacks(i + 1, "", "", 0, false);
				}
			}
		}
    }
    
    public void intializeDisplay()
    {
    	// Show/Hide login code depending on setting
        setLoginCode(mStreamCtl.userSettings.getAirMediaLoginCode());
        if (mStreamCtl.userSettings.getAirMediaDisplayLoginCode())
        {
        	showLoginCodePrompt(mStreamCtl.userSettings.getAirMediaLoginCode());
        }
        else
        {
        	hideLoginCodePrompt();
        }
        
        // Show/Hide IP address depending on setting
        setIpAddressPrompt(mStreamCtl.userSettings.getAirMediaIpAddressPrompt());
        //Show/Hide domain name depending on setting
        // TODO:
        
        // Set window display and flag (z order control)
        setDisplayScreen(mStreamCtl.userSettings.getAirMediaDisplayScreen());
        setWindowFlag(mStreamCtl.userSettings.getAirMediaWindowFlag());
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
    	setLoginCodePrompt(String.format("Code:%04d", loginCode));
    	
    	// need to send login_code_prompt intent again with no text to make sure it displays
    	Intent i = new Intent(commandIntent);
        i.putExtra("command", "login_code_prompt");
        mContext.sendBroadcast(i);
    }
    
    public void setLoginCode(int loginCode)
    {
    	Intent i = new Intent(commandIntent);
        i.putExtra("command", "login_code");
        i.putExtra("value", loginCode);
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
        i.putExtra("value", String.valueOf(enable ? 1 : 0));
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
        	Intent i = new Intent(commandIntent);
            i.putExtra("command", "address_prompt");
        	i.putExtra("value", String.format("IP:%s", mStreamCtl.userSettings.getDeviceIp()));
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
    
    public void showSurface(boolean enable)
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
    
    public static boolean checkAirMediaLicense()
    {
    	boolean licensed = false;
    	try
    	{
    		licensed = Integer.parseInt(MiscUtils.readStringFromDisk(licenseFilePath)) == 1;
    	} catch (Exception e) {} // If file DNE or corrupt not licensed
    	return licensed;
    }
}
