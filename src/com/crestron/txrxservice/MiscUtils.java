package com.crestron.txrxservice;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Scanner;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

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
        else if( Math.abs((w * 3) - (h * 4)) <= 10 )
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
}
