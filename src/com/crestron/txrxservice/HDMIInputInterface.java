package com.crestron.txrxservice;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import android.util.Log;

import com.crestron.txrxservice.ProductSpecific;

public class HDMIInputInterface {
	static String TAG = "HDMIInInterface";
    CresStreamCtrl streamCtl;
	private static String syncStatus;
	private static String horizontalRes;
	private static String verticalRes;
	private static String fps;
	private static String aspectRatio;
	private static String audioFormat;
	private static String audioChannels;
	private static int resolutionIndex;
    private static int productType;
	private static boolean isHdmiDriverPresent;
	private static boolean hdmiCameraIsConnected = false;
	private static boolean hdmiCameraConnectionStateChanged = false;
	private static Am3KHdmiStateMachine am3kHdmiStateMachine = null;
        private static final int SYSTEM_AIRMEDIA = 0x7400; //AM3X Product type is SYSTEM_AIRMEDIA as defined in ProductDefs.h
	private static final int SYSTEM_DGE3200  = 0x8000; 

    public static boolean useAm3kStateMachine = true;

	public HDMIInputInterface(CresStreamCtrl sCtl) {
		syncStatus = "false";
		horizontalRes = "0";
		verticalRes = "0";
		fps = "0";
		aspectRatio = "0";
		audioFormat = "0";	//1=PCM for txrx and dge
		audioChannels = "0";
		resolutionIndex = 0;
		isHdmiDriverPresent = (isHdmiDriverPresent | false); //set isHdmiDriverPresentH to false if not set
            streamCtl = sCtl;
            productType = streamCtl.nativeGetProductTypeEnum();
            if (productType == SYSTEM_AIRMEDIA || productType == SYSTEM_DGE3200)      
            {            
                am3kHdmiStateMachine = new Am3KHdmiStateMachine();
            }
	}
		
	public void setSyncStatus(int resEnum) {
		if (isHdmiDriverPresent == true)
		{
			// Old Mistral interface
//			byte[] hdmiInSyncStatus = ProductSpecific.getEVSHdmiInSyncStatus();
//			
//			Log.i(TAG, "SyncStatus " + (char)hdmiInSyncStatus[0]);
//	
//			if(((char)hdmiInSyncStatus[0] == '1') && (resolutionIndex != 0))
			if ( resEnum > 0)	// Only send sync status high if valid resolution enum
				syncStatus = "true";
			else
				syncStatus = "false";
		}
	}
	
	public void setResolutionIndex(int index){
		resolutionIndex = index;
	}
	
	public int getResolutionIndex() {
		return resolutionIndex;
	}
	
	public String getSyncStatus() {
		return syncStatus;
	}

	public String getInterlacing() { 
		return String.valueOf(readInterlaced());
	}

	public void setHorizontalRes(String _horizontalRes) { 
		horizontalRes = _horizontalRes; 
	}
	
	public String getHorizontalRes() {
		return horizontalRes;
	}

	public void setVerticalRes(String _verticalRes) { 
		verticalRes = _verticalRes; 
	}
	
	public String getVerticalRes() {
		return verticalRes;
	}

	public void setFPS(String _fps) { 
		fps = _fps; 
	}
	
	public String getFPS() {
		return fps;
	}
	
	public void setAspectRatio() {
		if(syncStatus == "false")
		{
			aspectRatio = "0";
			return;
		}
		else
			aspectRatio = MiscUtils.calculateAspectRatio(Integer.parseInt(horizontalRes), Integer.parseInt(verticalRes));
		
		Log.i(TAG, "AR " + aspectRatio);
		return;
	}
	
	public String getAspectRatio() {
		return aspectRatio;
	}
	
	public void setAudioFormat(String audioFmt) {
		audioFormat = audioFmt;
	}
	
	public void setAudioChannels(String audioChn) {
		audioChannels = audioChn;
	}
	
	public String getAudioFormat() {
		return audioFormat;
	}
	
	public String getAudioChannels() {
		return audioChannels;
	}

	public String getAudioSampleRate() {
		
		return Integer.toString(readAudioSampleRate());
	}
	
    public void updateResolutionInfo() 
    {
        setSyncStatus(readResolutionEnum(false));
        
        String delims_am3x = "[xp]+"; // Delimiter for AM3X products
        String delims = "[x@]+"; //Delimiter for All products
        String hdmiInResolution = "0x0@0";
        String tokens[] = hdmiInResolution.split(delims);

        if (Boolean.parseBoolean(getSyncStatus()) == true)
        {
            hdmiInResolution = getHdmiInResolutionSysFs();
            if(hdmiInResolution.equals("0")) //SYSTEM_AIRMEDIA returns 0 if no resolution available
            {
                hdmiInResolution = "0x0@0";
                tokens = hdmiInResolution.split(delims);
            }
            else
            {
                if(productType != SYSTEM_AIRMEDIA && productType != SYSTEM_DGE3200)
                    tokens = hdmiInResolution.split(delims);
                else
                    tokens = hdmiInResolution.split(delims_am3x);
            }
        }

        Log.i(TAG, "updateResolutionInfo(): sync="+getSyncStatus()+"    HDMI In Resolution=" + hdmiInResolution);

        setHorizontalRes(tokens[0]);
        setVerticalRes(tokens[1]);
        if(productType != SYSTEM_AIRMEDIA && productType != SYSTEM_DGE3200)
            setFPS(tokens[2].trim());
        else
        {
            int fps = (int) Double.parseDouble(tokens[2].trim());
            setFPS(Integer.toString(fps));
        }
        setAspectRatio();
        
        if (Boolean.parseBoolean(getSyncStatus()) == true)
        {
        	setAudioFormat("1");        	
        	setAudioChannels("2");
    	}
        else
        {
        	setAudioFormat("0");        	
        	setAudioChannels("0");
        }
    }
    
    public static void setHdmiDriverPresent(boolean isPresent) {
    	isHdmiDriverPresent = isPresent;
    }
        
    public static int getHdmiHpdEventState(){
    	if (isHdmiDriverPresent == true)
		{
            StringBuilder text = new StringBuilder(16);
            if(productType != SYSTEM_AIRMEDIA && productType != SYSTEM_DGE3200)
            {
                try {
                    //File sdcard = Environment.getExternalStorageDirectory();
                    File file = new File("/sys/class/switch/evs_hdmi_hpd/state");

                    BufferedReader br = new BufferedReader(new FileReader(file));
                    String line;
                    while ((line = br.readLine()) != null) {
                        text.append(line);
                        //text.append('\n');
                    }
                    br.close();
                }catch (IOException e) {
                    e.printStackTrace();
                    text.append("0");
                }
            }
            else
            {
                text.append("0");
                Log.e(TAG, "HPD Not supported for AM3X ");
            }

	        Log.i(TAG, "hpdState:" + text.toString());
	        return Integer.parseInt(text.toString());
		}
    	else
    		return 0;
    }

    public static String getHdmiInResolutionSysFs(){
    	if (isHdmiDriverPresent == true)
		{
	        StringBuilder text = new StringBuilder(64);
            if(productType != SYSTEM_AIRMEDIA && productType != SYSTEM_DGE3200)
            {
                try {
                    File file = new File("/sys/devices/platform/omap_i2c.2/i2c-2/2-000f/hdmi_in_resolution");
                    BufferedReader br = new BufferedReader(new FileReader(file));
                    String line;
                    while ((line = br.readLine()) != null) {
                        text.append(line);
                    }
                    br.close();
                }catch (IOException e) {
                    e.printStackTrace();
                    text.append("0x0@0");
                }
                return text.toString();
            }
            else
            { 
                if (!useAm3kStateMachine)
                    return am3kHdmiStateMachine.readResolutionSysFs();
                else
                    return am3kHdmiStateMachine.getResolutionSysFs();
            }
		}
    	else
    		return "0x0@0";
    }

    public static int readResolutionEnum(boolean logResult){
    	if (isHdmiDriverPresent == true)
		{
            int resIndex = 0;
            if(productType != SYSTEM_AIRMEDIA && productType != SYSTEM_DGE3200 )
            {
                StringBuilder text = new StringBuilder(64);
                try {
                    File file = new File("/sys/class/switch/evs_hdmi_resolution/state");
                    BufferedReader br = new BufferedReader(new FileReader(file));
                    String line;
                    while ((line = br.readLine()) != null) {
                        text.append(line);
                    }
                    br.close();

                    resIndex = Integer.parseInt(text.toString());
                }catch (IOException e) {
                    e.printStackTrace();
                    resIndex = 0;
                }
            }
            else
            {   
                //For AM3X handle default case differently
                String hdmiInResolution = "0x0@0";
                String tokens[] = hdmiInResolution.split("[x@]+");

                if (!useAm3kStateMachine)
                    hdmiInResolution = am3kHdmiStateMachine.readResolutionSysFs();
                else
                    hdmiInResolution = am3kHdmiStateMachine.getResolutionSysFs();
                if(hdmiInResolution.equals("0"))
                {
                    hdmiInResolution = "0x0@0";
                    tokens = hdmiInResolution.split("[x@]+");
                }
                else
                    tokens = hdmiInResolution.split("[xp]+");

                String hRes = tokens[0];
                String vRes = tokens[1];

                //Resolution to index table here, source: Refer DM ResolutionTable.xls
                if (logResult)
                    Log.i(TAG, "Product Type AM3X Detected: " + productType + ", vRes " + vRes + ", hRes " + hRes);

                if((hRes.equals("0")) && (vRes.equals("0")))
                    resIndex = 0; //Not defaulting to 640x480 Refer to startPlayback in CameraPreview.java
                else if((hRes.equals("640")) && (vRes.equals("480")))
                    resIndex = 1;
                else if((hRes.equals("720")) && (vRes.equals("480")))
                    resIndex = 2;
                else if((hRes.equals("720")) && (vRes.equals("576")))
                    resIndex = 4;
                else if((hRes.equals("800")) && (vRes.equals("600")))
                    resIndex = 6;
                else if((hRes.equals("848")) && (vRes.equals("480")))
                    resIndex = 7;
                else if((hRes.equals("1024")) && (vRes.equals("768")))
                    resIndex = 8;
                else if((hRes.equals("1280")) && (vRes.equals("720")))
                    resIndex = 9;
                else if((hRes.equals("1280")) && (vRes.equals("768")))
                    resIndex = 11;
                else if((hRes.equals("1280")) && (vRes.equals("800")))
                    resIndex = 13;
                else if((hRes.equals("1280")) && (vRes.equals("960")))
                    resIndex = 15;
                else if((hRes.equals("1280")) && (vRes.equals("1024")))
                    resIndex = 16;
                else if((hRes.equals("1360")) && (vRes.equals("768")))
                    resIndex = 17;
                else if((hRes.equals("1366")) && (vRes.equals("768")))
                    resIndex = 18;
                else if((hRes.equals("1400")) && (vRes.equals("1050")))
                    resIndex = 20;
                else if((hRes.equals("1440")) && (vRes.equals("900")))
                    resIndex = 22;
                else if((hRes.equals("1600")) && (vRes.equals("900")))
                    resIndex = 24;
                else if((hRes.equals("1600")) && (vRes.equals("1200")))
                    resIndex = 25;
                else if((hRes.equals("1680")) && (vRes.equals("1050")))
                    resIndex = 26;
                else if((hRes.equals("1920")) && (vRes.equals("1080")))
                    resIndex = 32;
                else if((hRes.equals("1920")) && (vRes.equals("1200")))
                    resIndex = 33;
                else
                    Log.e(TAG, "readResolutionEnum:ERROR no handling for vRes " + vRes + " hRes " + hRes);
            }

	        if (logResult)
                Log.i(TAG, "HDMI IN index from sysfs:" + resIndex);
            return resIndex;
		}
    	else
    		return 0;
    }
    
    public static int getResolutionEnum()
    {
    	return resolutionIndex;
    }
    
    public static boolean readHDCPInputStatus (){

    	if (isHdmiDriverPresent == true)
		{
	    	StringBuilder text = new StringBuilder(16);

            if(productType != SYSTEM_AIRMEDIA && productType != SYSTEM_DGE3200)
            {
                try {
                    File file = new File("/sys/devices/platform/omap_i2c.2/i2c-2/2-000f/hdcp");
                    BufferedReader br = new BufferedReader(new FileReader(file));
                    String line;
                    while ((line = br.readLine()) != null) {
                        text.append(line);
                    }
                    br.close();
                }catch (IOException e) {
                    e.printStackTrace();
                    text.append("0"); //if error default to no HDCP
                }
                return Integer.parseInt(text.toString()) == 57;
            }
            else
            {
                final String[] sHdmiInputValues = {"Succeed" , "Failed"};
                try {
                    File file = new File("/sys/devices/platform/ff3e0000.i2c/i2c-8/8-000f/hdcp_status");
                    BufferedReader br = new BufferedReader(new FileReader(file));
                    String line;
                    while ((line = br.readLine()) != null) {
                        text.append(line);
                    }
                    br.close();
                }catch (IOException e) {
                    e.printStackTrace();
                    text.append("0"); //if error default to no HDCP
                }

                //Log.i(TAG, "HDMI IN HDCP status from sysfs:" + text.toString());

                if(sHdmiInputValues[0].equalsIgnoreCase(text.toString()))
                    return true;
                else
                    return false;
            }
		}
    	else 
    		return false;
    }
    
    public static boolean readSyncState (){
    	if (isHdmiDriverPresent == true)
		{
	    	StringBuilder text = new StringBuilder(16);
            if(productType != SYSTEM_AIRMEDIA && productType != SYSTEM_DGE3200)
            {
                try {
                    File file = new File("/sys/devices/platform/omap_i2c.2/i2c-2/2-000f/sync_state");

                    BufferedReader br = new BufferedReader(new FileReader(file));
                    String line;
                    while ((line = br.readLine()) != null) {
                        text.append(line);
                    }
                    br.close();
                }catch (IOException e) {
                    e.printStackTrace();
                    text.append("0"); //if error default to no sync
                }
                return Integer.parseInt(text.toString()) == 1;
            }
            else
            {
                if (!useAm3kStateMachine)
                    return am3kHdmiStateMachine.readSyncStateSysFs();
                else
                    return am3kHdmiStateMachine.getSyncState();
            }
		}
    	else 
    		return false;
    }
    
    public static int readAudioSampleRate (){
    	if (isHdmiDriverPresent == true)
		{
	    	StringBuilder text = new StringBuilder(64);
            if(productType != SYSTEM_AIRMEDIA && productType != SYSTEM_DGE3200)
            {
                try {
                    File file = new File("/sys/devices/platform/omap_i2c.2/i2c-2/2-000f/audio_sample_rate");

                    BufferedReader br = new BufferedReader(new FileReader(file));
                    String line;
                    while ((line = br.readLine()) != null) {
                        text.append(line);
                    }
                    br.close();
                }catch (IOException e) {
                    e.printStackTrace();
                    text.append("48000"); //if error default to 48kHz
                }
            }
            else
            {
                try {
                    File file = new File("/sys/devices/platform/ff3e0000.i2c/i2c-8/8-000f/audio_fmts");

                    BufferedReader br = new BufferedReader(new FileReader(file));
                    String line;
                    while ((line = br.readLine()) != null) {
                        text.append(line);
                        break;//Since AM3X returns 4 lines of data on this sysfs
                    }
                    br.close();
                }catch (IOException e) {
                    e.printStackTrace();
                    text.append("48000"); //if error default to 48kHz
                }
            }
	        return Integer.parseInt(text.toString());
		}
    	else 
    		return 0;
    }

    public static boolean readInterlaced (){
    	if (isHdmiDriverPresent == true)
		{
            if(productType != SYSTEM_AIRMEDIA && productType != SYSTEM_DGE3200)
            {
                StringBuilder text = new StringBuilder(16);
                try {
                    File file = new File("/sys/devices/platform/omap_i2c.2/i2c-2/2-000f/interlaced");

                    BufferedReader br = new BufferedReader(new FileReader(file));
                    String line;
                    while ((line = br.readLine()) != null) {
                        text.append(line);
                    }
                    br.close();
                }catch (IOException e) {
                    e.printStackTrace();
                    text.append("0"); //if error default to not interlaced
                }

                return Integer.parseInt(text.toString()) == 1;
            }
            else
            {   //FIXME: Not Yet defined for AM3X
                return false;
            }
        }
        else
    		return false;
    }
    
    public boolean isHdmiCameraConnected()
    {
        return hdmiCameraIsConnected;
    }
    
    public void setHdmiCameraConnected(boolean connected)
    {
        if (hdmiCameraIsConnected != connected)
        {
            Log.i(TAG, "##-------setHdmiCameraConnected(): HDMI camera connected = "+connected+" -------##");
            hdmiCameraIsConnected = connected;
            hdmiCameraConnectionStateChanged = true;
            synchronized(am3kHdmiStateMachine.threadLock) {
                // Wait on object
                try {
                    am3kHdmiStateMachine.notification = true;
                    am3kHdmiStateMachine.threadLock.notify();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    class Am3KHdmiStateMachine {
        private final int MIN_COUNT_FOR_CHANGE = 5;
        private final int SLEEP_TIME = 100; // msec
        private StateMachineThread stateMachineThread = null;
        public final boolean onlyUseCameraConnectEvents = true;
        private Object threadLock = new Object();
        private Object lock = new Object();
        
        private boolean sync;
        private boolean pendingSync;
        private int pendingSyncCount;
        
        private String resolution;
        private String pendingResolution;
        private int pendingResolutionCount;
       
        public boolean notification = false;

        private boolean readSyncStateSysFs()
        {
            StringBuilder text = new StringBuilder(16);
            try {
                File file = new File("/sys/devices/platform/ff3e0000.i2c/i2c-8/8-000f/sync_status");

                BufferedReader br = new BufferedReader(new FileReader(file));
                String line;
                while ((line = br.readLine()) != null) {
                    text.append(line);
                }
                br.close();
            } catch (IOException e) {
                e.printStackTrace();
                text.append("0"); //if error default to no sync
            }
            return Integer.parseInt(text.toString()) == 1;
        }
        
        public boolean getSyncState()
        {
            synchronized (lock) {
                if (onlyUseCameraConnectEvents)
                    return hdmiCameraIsConnected;
                else
                    return sync;
            }
        }
        
        public String readResolutionSysFs()
        {
            StringBuilder text = new StringBuilder(16);
            //for AM3X
            try {
                File file = new File("/sys/devices/platform/ff3e0000.i2c/i2c-8/8-000f/video_fmts");
                BufferedReader br = new BufferedReader(new FileReader(file));
                String line;
                while ((line = br.readLine()) != null) {
                    text.append(line);
                }
                br.close();
            }catch (IOException e) {
                e.printStackTrace();
                text.append("0x0@0");
            }
            //Log.i(TAG, "HDMI IN Res from sysfs:" + text.toString());
            return text.toString();
        }
        
        public String getResolutionSysFs()
        {
            synchronized(lock) 
            {
                if (onlyUseCameraConnectEvents)
                    return (hdmiCameraIsConnected) ? resolution : "0";
                else
                    return resolution;
            }
        }
        
        
        public Am3KHdmiStateMachine() {
            // initialize sync, resolutionIndex etc;
            sync = readSyncStateSysFs();
            pendingSync = sync;
            pendingSyncCount = MIN_COUNT_FOR_CHANGE;
            resolution = readResolutionSysFs();
            pendingResolution = resolution;
            pendingResolutionCount = MIN_COUNT_FOR_CHANGE;;
            
            if (useAm3kStateMachine)
            {
                // launch state machine thread
                stateMachineThread = new StateMachineThread();
                stateMachineThread.start();
            }
        }
        
        public Object getInstance() {
            return this;
        }
        
        public class StateMachineThread extends Thread {
            public int threadCount = 0;
            public String resolutionEventReason = null;
            public void run()
            {
                while (true) {
                    synchronized(threadLock) {
                        // Wait on object
                        try {
                            threadLock.wait(SLEEP_TIME);
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }

                    if (notification) {
                        notification = false;
                        Log.i(TAG, "HDMI Input state machine thread execution - due to notification");
                    }
                    if (threadCount%1000 == 0)
                        Log.i(TAG, "HDMI Input state machine thread execution (iter="+threadCount+")");
                    threadCount++;

                    resolutionEventReason = null;
                    
                    if (hdmiCameraConnectionStateChanged) {
                        // set flag to force update of input HDMI resolution event
                        resolutionEventReason = "camera connected state changed to "+hdmiCameraIsConnected;
                        if (!hdmiCameraIsConnected) {
                            // force sync to false and resolution to 0
                            synchronized(lock)
                            {
                                sync = pendingSync = false;
                                resolution = pendingResolution = "0";
                            }
                        } else {
                            // update sync and resolution
                            synchronized(lock)
                            {
                                sync = pendingSync = readSyncStateSysFs();
                                resolution = pendingResolution = readResolutionSysFs();
                            }
                        }
                        if (onlyUseCameraConnectEvents)
                        {
                            pendingSyncCount = MIN_COUNT_FOR_CHANGE;
                            pendingResolutionCount = MIN_COUNT_FOR_CHANGE;
                        }
                        hdmiCameraConnectionStateChanged = false;
                    }
                    
                    
                    // Right now we only want to react to real changes in sync and resolution if accompanied by 
                    // a camera connect/disconnect event.
                    if (!onlyUseCameraConnectEvents) {
                        boolean s = readSyncStateSysFs();
                        if (s != pendingSync) {
                            pendingSync = s;
                            pendingSyncCount = 1;
                        } else {
                            pendingSyncCount++;
                            if (pendingSync != sync) {
                                if (pendingSyncCount >= MIN_COUNT_FOR_CHANGE) {
                                    // update sync and set flag to force update of input HDMI resolution event
                                    synchronized(lock) {
                                        sync = pendingSync;
                                    }
                                    resolutionEventReason = "sync changed to "+sync;
                                    Log.i(TAG, "am3kHdmiStateMachineThread found sync change with sync=" + sync);
                                }
                                else {
                                    Log.i(TAG, "am3kHdmiStateMachineThread found possible sync change with new sync=" + pendingSync + " count="+pendingSyncCount);
                                }
                            }
                        }

                        String res = readResolutionSysFs();
                        if (!res.equalsIgnoreCase(pendingResolution)) {
                            pendingResolution = res;
                            pendingResolutionCount = 1;
                        } else {
                            pendingResolutionCount++;
                            if (!pendingResolution.equalsIgnoreCase(resolution)) {
                                if (pendingResolutionCount >= MIN_COUNT_FOR_CHANGE) {
                                    // update resolution and set flag to force update of input HDMI resolution event
                                    synchronized(lock) {
                                        resolution = pendingResolution;
                                    }
                                    resolutionEventReason = "resolution changed to "+resolution;
                                    Log.i(TAG, "am3kHdmiStateMachineThread found resolution change with resolution=" + resolution);
                                } else {
                                    Log.i(TAG, "am3kHdmiStateMachineThread possible resolution change with new resolution=" + pendingResolution+" count="+pendingResolutionCount);
                                }
                            }
                        }
                    } else {
                        // Mark bad sync or resolution events inconsistent with camera connection state
                        // Note these can happen before a camera connect or disconnect event actually reaches us
                        // If this works reliably then all this code can eventually be removed - for ow it is there to
                        // go to GrandStream if we see issues.
                        boolean s = readSyncStateSysFs();
                        String res = readResolutionSysFs();
                        if (hdmiCameraIsConnected) {
                            // connected camera - sync and resolution must be true and non-zero
                            if (!s) {
                                Log.w(TAG, "##-------Got sync false even though HDMI camera is connected -------##");
                            }
                            if (res.equals("0") || res.startsWith("0x0"))
                            {
                                Log.w(TAG, "##-------Got resolution=" + res +" even though HDMI camera is connected -------##");
                            }
                        } else {
                            // disconnected camera - sync and resolution must be false and zero
                            if (s) {
                                Log.w(TAG, "##-------Got sync true even though HDMI camera is disconnected -------##");
                            }
                            if (!res.equals("0") && !res.startsWith("0x0"))
                            {
                                Log.w(TAG, "##-------Got resolution=" + res +" even though HDMI camera is disconnected -------##");
                            }
                        }
                    }
                    
                    if (resolutionEventReason != null) {
                        int resolutionId = readResolutionEnum(false);
                        Log.i(TAG, "am3kHdmiStateMachineThread calling handleHdmiInputResolutionEvent with resolutionId=" + resolutionId + " because "+resolutionEventReason);
                        streamCtl.handleHdmiInputResolutionEvent(resolutionId);                        
                    }
                }
            }
        }
    }
}
