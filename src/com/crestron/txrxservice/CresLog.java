package com.crestron.txrxservice;

import java.io.IOException;
import android.util.Log;
import com.crestron.txrxservice.CresStreamCtrl.ServiceMode;

public class CresLog 
{
    String TAG = "TxRx CresLog";

    private native void nativeInit();     // Initialize native code

    private CresStreamCtrl m_streamCtl;
    private static CresLog m_instance;

    public static CresLog getInstance()
    {
        if (m_instance == null) 
        {
            m_instance = getSync();
        }
        
        return m_instance;
    }

    private static synchronized CresLog getSync()
    {
        if (m_instance == null) 
        {
            m_instance = new CresLog();
        }
        
        return m_instance;
    }

    private static final int streamId_Undefined = -1;
	
    private static final int SendTo_CresStorePublishAndSave = 0;
    private static final int SendTo_CresStorePublish 		= 1;
    private static final int SendTo_CresStoreNone 			= 2;
	
    //Any modifications made to this block must also be implemented in corresponding
    //CSIO C class CCresLogCode in csioCommonShare.h	
    public static final int Error_None 									= 0;
    public static final int Error_HDMI_No_Sync 							= 1;
    public static final int Error_DM_No_Stream 							= 2;
    public static final int Error_Connection_Refused 					= -1;
    public static final int Error_No_Network 							= -2;
    public static final int Error_Generic_Retry 						= -1000;
    public static final int Error_Invalid_Credentials 					= -1001;
    public static final int Error_Invalid_Hostname 						= -1002;
    public static final int Error_Unsupported_Codec 					= -1003;
    public static final int Error_Generic_No_Retry 						= -9000;
    public static final int Error_Unsupported_Source_Type 				= -9001;
    public static final int Error_Invalid_Url							= -9002;
    public static final int Error_Exceeded_Maximum_Source_Type_Sessions = -9003;
    public static final int Error_Exceeded_Maximum_Total_Sessions 		= -9004;
    public static final int Error_Invalid_Surface 						= -9005;
    public static final int Error_Invalid_Stop_Command 					= -9006;
    //-----------end of block---------------
       
    public CresLog(CresStreamCtrl context)
    {  	
        Log.e(TAG, "CresLog :: Constructor 1 called...!");
        m_streamCtl = context;
        if(m_instance == null)
        {
            m_instance = this;
            nativeInit();
        }
    }
 
    public CresLog()
    {  	
        Log.e(TAG, "CresLog :: Constructor 2 called...!");
        if(m_instance == null)
        {
            m_instance = this;
            nativeInit();
        }
    }
    
    public static void sendErrorStatusMessage(int error, String msg)
    {
        getInstance().sendErrorStatus(SendTo_CresStorePublishAndSave, streamId_Undefined, error, msg);
    }
	
    public static void sendErrorStatusMessage(int streamId, int error, String msg)
    {
        getInstance().sendErrorStatus(SendTo_CresStorePublishAndSave, streamId, error, msg);
    }

    public static void sendErrorStatusMessage(int sendTo, int streamId, int error, String msg)
    {
        getInstance().sendErrorStatus(sendTo, streamId, error, msg);
    }

    public void sendErrorStatus(int sendto, int streamId, int errorCode, String message)
    {
        if((m_streamCtl != null) && (m_streamCtl.serviceMode == ServiceMode.Slave))
        {
            m_streamCtl.sockTask.SendDataToAllClients(MiscUtils.stringFormat("ERRSTATUSMSG=%d`%d`%d`%s", streamId, errorCode, sendto, message));
        }
    }
}
