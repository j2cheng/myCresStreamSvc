package com.crestron.txrxservice;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Scanner;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;

import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;

public class MiscUtils {	
	static Matcher matcher;
    static String TAG = "TxRx Utils";
	
	public static void getDeviceIpAddr() {
		String ipAddress;
		Process su = null, ipaddr = null;
		try {
			su = Runtime.getRuntime().exec("su");
			Process process = Runtime.getRuntime().exec("netcfg");
			InputStreamReader reader = new InputStreamReader(process.getInputStream());
			Scanner scanner = new Scanner(reader);

			while(scanner.hasNextLine()) {
				String ipString = scanner.nextLine();
				if(ipString.startsWith("eth0")){
					String IPADDRESS_PATTERN = "(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)";
					Pattern pattern = Pattern.compile(IPADDRESS_PATTERN);
					matcher = pattern.matcher(ipString);
					if (matcher.find()) {	
						Log.d(TAG, "ipstring:" + matcher.group());
					}
				}

			}
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}
//        private static int calcAspect(int a, int b){
//            return (b==0) ? a:calcAspect(b, a%b);
//        }

    public static String calculateAspectRatio(int w, int h)
    {
    	// We give a range of 10 to match with aspect ratio
        String retstr;
        if( w == 0 || h == 0 )
        {
        	retstr = "0";
        }
        //720x480(NTSC) and 720x576(PAL) should show as 4:3
        else if( (Math.abs((w * 3) - (h * 4)) <= 10) || ((w == 720) && (h == 576 || h == 480)) )
		{
        	retstr = "133";
		}
		else if( Math.abs((w * 10) - (h * 16)) <= 10 )
		{
			retstr = "160";
		}
		else if( Math.abs((w * 9) - (h * 16)) <= 10 )
		{
			retstr = "177";
		}
		else
		{
	        if (h != 0)
	        	retstr = String.valueOf((w * 100) / h);
	        else
	        	retstr = String.valueOf(0);
		}

        return retstr;
    }
    
    public static boolean rectanglesOverlap(int s1xLeft, int s1xRight, int s1yTop, int s1yBottom, int s2xLeft, int s2xRight, int s2yTop, int s2yBottom)
    {
    	Rect surface1 = new Rect(s1xLeft, s1yTop, s1xRight, s1yBottom);
    	Rect surface2 = new Rect(s2xLeft, s2yTop, s2xRight, s2yBottom);
    	
    	return Rect.intersects(surface1, surface2);    		
    }
    
    public static void writeStringToDisk(String filePath, String stringToWrite)
    {
    	Writer writer = null;
		try 
      	{
			writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filePath), "US-ASCII"));
			writer.write(stringToWrite);
		    writer.flush();
	    } 
      	catch (IOException ex) {
    	  Log.e(TAG, "Failed to write to file " + filePath + " : " + ex);
    	} 
		finally 
    	{
    		try {writer.close();} catch (Exception ex) {/*ignore*/}
    	}	
    }
}
