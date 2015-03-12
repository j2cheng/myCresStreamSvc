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
       
        public static int getHdmiHpdEventState(){
            StringBuilder text = new StringBuilder();
            try {
                //File sdcard = Environment.getExternalStorageDirectory();
                File file = new File("/sys/class/switch/evs_hdmi_hpd/state");

                BufferedReader br = new BufferedReader(new FileReader(file));  
                String line;   
                while ((line = br.readLine()) != null) {
                    text.append(line);
                    //text.append('\n');
                }
                br.close() ;
            }catch (IOException e) {
                e.printStackTrace();           
            }
            Log.d(TAG, "hpdState:" + text.toString());
            return Integer.parseInt(text.toString());
        }
       
        private static int calcAspect(int a, int b){
            return (b==0) ? a:calcAspect(b, a%b);
        }

        public static String calculateAspectRatio(int w, int h)
        {
            StringBuilder sb = new StringBuilder(64);
            int r = calcAspect(w,h);
            sb.append(w/r).append(":").append(h/r);
            return (sb.toString());
        }
}
