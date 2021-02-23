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
    private static final int SYSTEM_AIRMEDIA = 0x7400; //AM3X Product type is SYSTEM_AIRMEDIA as defined in ProductDefs.h
	
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
        
        String delims = "[xp]+";
        String delims_null = "[x@]+";
        String hdmiInResolution = "0x0@0";
        String tokens[] = hdmiInResolution.split(delims_null);

        if (Boolean.parseBoolean(getSyncStatus()) == true)
        {
            hdmiInResolution = getHdmiInResolutionSysFs();
            if(hdmiInResolution.equals("0"))
            {
                hdmiInResolution = "0x0@0";
                tokens = hdmiInResolution.split(delims_null);
            }
            else
                tokens = hdmiInResolution.split(delims);
        }

        Log.i(TAG, "updateResolutionInfo(): sync="+getSyncStatus()+"    HDMI In Resolution=" + hdmiInResolution);

        setHorizontalRes(tokens[0]);
        setVerticalRes(tokens[1]);
        setFPS(tokens[2].trim());
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
            if(productType != SYSTEM_AIRMEDIA)
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
            if(productType != SYSTEM_AIRMEDIA)
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
            }
            else
            { //for AM3X
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
            }

            //Log.i(TAG, "HDMI IN Res from sysfs:" + text.toString());
	        return text.toString();
		}
    	else
    		return "0x0@0";
    }

    public static int readResolutionEnum(boolean logResult){
    	if (isHdmiDriverPresent == true)
		{
            int resIndex = 0;
            if(productType != SYSTEM_AIRMEDIA)
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
            {   //For AM3X handle default case differently
                String hdmiInResolution = "0x0@0";
                String tokens[] = hdmiInResolution.split("[x@]+");

                hdmiInResolution = getHdmiInResolutionSysFs();
                if(hdmiInResolution.equals("0"))
                {
                    hdmiInResolution = "0x0@0";
                    tokens = hdmiInResolution.split("[x@]+");
                }
                else
                    tokens = hdmiInResolution.split("[xp]+");

                String hRes = tokens[0];
                String vRes = tokens[1];

                //FIXME: Add full Resolution to index table here. Refer DM ResolutionTable.xls
                if (logResult)
                    Log.i(TAG, "Product Type AM3X Detected: " + productType + ", vRes " + vRes + "hRes " + hRes);

                if((hRes.equals("1920")) && (vRes.equals("1080")))
                    resIndex = 32;
                else if((hRes.equals("0")) && (vRes.equals("0")))
                    resIndex = 0;
                else
                    Log.e(TAG, "readResolutionEnum:ERROR no handling for vRes " + vRes + " hRes " + hRes); //FIXME
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

            if(productType != SYSTEM_AIRMEDIA)
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
            if(productType != SYSTEM_AIRMEDIA)
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
            }
            else
            {
                try {
                    File file = new File("/sys/devices/platform/ff3e0000.i2c/i2c-8/8-000f/sync_status");

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
            }
	        return Integer.parseInt(text.toString()) == 1;
		}
    	else 
    		return false;
    }
    
    public static int readAudioSampleRate (){
    	if (isHdmiDriverPresent == true)
		{
	    	StringBuilder text = new StringBuilder(64);
            if(productType != SYSTEM_AIRMEDIA)
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
            if(productType != SYSTEM_AIRMEDIA)
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
}
