package com.crestron.txrxservice;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.util.Scanner;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.net.UnknownHostException;

import android.graphics.Rect;
import android.os.Build;
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
    
    public static boolean rectanglesAreEqual(Rect r1, Rect r2)
    {
    	return (r1.left == r2.left && r1.right == r2.right && r1.top == r2.top && r2.bottom == r2.bottom);
    }
    
    public static Rect getAspectRatioPreservingRectangle(int x, int y, int w, int h, int videoW, int videoH)
    {
    	if (videoW == 0 || videoH == 0)
    		return new Rect(x, y, x+w, y+h);
    	       	
        final float viewWidth = (float)w;
        final float viewHeight = (float)h;
        final float videoWidth = (float)videoW;
        final float videoHeight = (float)videoH;

        final float scale = Math.min(viewWidth/videoWidth, viewHeight/videoHeight);
        final float finalWidth = videoWidth*scale;
        final float finalHeight = videoHeight*scale;
        
        int aw = (int) finalWidth;
        int ah = (int) finalHeight;
        
        Log.d(TAG, "getAspectRatioPreservingRectangle : x="+x+" y="+y+"   Window wxh="+w+"x"+h+"     video wxh="+videoW+"x"+videoH);
        Log.d(TAG, "getAspectRatioPreservingRectangle : rescaled wxh="+aw+"x"+ah);

        x += (w-aw)/2;
        y += (h-ah)/2;
        Rect r = new Rect(x, y, x+aw, y+ah);
        Log.d(TAG, "getAspectRatioPreservingRectangle x="+r.left+" y="+r.top+"   wxh="+r.width()+"x"+r.height());
        return r;
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
      	catch (Exception ex) {
    	  Log.e(TAG, "Failed to write to file " + filePath + " : " + ex);
    	} 
		finally 
    	{
    		try {writer.close();} catch (Exception ex) {/*ignore*/}
    	}	
    }
    
    public static String readStringFromDisk(String filePath)
    {
    	StringBuilder text = new StringBuilder();
    	text.append(""); //default to blank string
        try {
            File file = new File(filePath);

            BufferedReader br = new BufferedReader(new FileReader(file));  
            String line;   
            while ((line = br.readLine()) != null) {
                text.append(line);
            }
            br.close();
        }catch (Exception e) {}
        return text.toString();
    }
    
    public static String getLocalUrl(String url, int internalPort)
    {
    	String newUrl = url;
    	String matchString = "rtsp://";
    	
    	if (url.contains(matchString))	// only perform the replacement on rtsp addresses
    	{  
    		String ipRegex = "\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}";
	    	String portRegex = ":\\d{1,5}";
	    	newUrl = newUrl.replaceFirst(portRegex, ":" + internalPort);
	    	
	    	// If it doesnt come with port # we put in internal RTSP port
	    	if (!newUrl.contains(Integer.toString(internalPort)))
	    	{
	    		newUrl = newUrl.replaceFirst(ipRegex, "127.0.0.1:" + internalPort);
	    	}
	    	else
	    	{
	    		newUrl = newUrl.replaceFirst(ipRegex, "127.0.0.1");
	    	}    		
    	}
    	
    	return newUrl;
    }
    
    public static String getRTSPIP(String url)
    {
    	String ip = "";
    	String matchString = "rtsp://";
    	
    	if (url.contains(matchString))	// only perform the replacement on rtsp addresses
    	{  
    		Pattern ipRegex = Pattern.compile("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}");
	    	
    		Matcher matcher = ipRegex.matcher(url);
    		if (matcher.find())
    		{
    			ip = matcher.group();
    		}
    		else
    		{
    			// if no IP must be hostname 
    			Pattern hostnameRegex = Pattern.compile("\\/\\/(?:\\w+:\\w+\\@)*([\\w\\.]+)[\\/:]");
    			
    			Matcher hostnameMatcher = hostnameRegex.matcher(url);
    			if (hostnameMatcher.find())
    			{
    				// Resolve Domain/Host name
    				try {
    					InetAddress address = InetAddress.getByName(hostnameMatcher.group(1));
    					ip = address.getHostAddress();
    				} catch (java.net.UnknownHostException e)
    				{
    					e.printStackTrace();
    				}
    			}
    		}
    	}
    	
    	return ip;
    }
    
    public static long getSystemUptimeMs() {
        long uptime = -1;
        try {
        	 double d_time = Double.parseDouble(new Scanner(new FileInputStream("/proc/uptime")).next());
             uptime = (long)(d_time * 1000);
        } catch (Exception e) {}
        
        return uptime;
    }
    
    public static void copyInputStreamToFile( InputStream in, File file ) {
        try {
            OutputStream out = new FileOutputStream(file);
            byte[] buf = new byte[1024];
            int len;
            while((len=in.read(buf))>0){
                out.write(buf,0,len);
            }
            out.close();
            in.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public static String getHostName(String defValue) {
        try {
            Method getString = Build.class.getDeclaredMethod("getString", String.class);
            getString.setAccessible(true);
            return getString.invoke(null, "net.hostname").toString();
        } catch (Exception ex) {
            return defValue;
        }
    }
    
    public static String getDomainName(String defValue) {
        try {
            Method getString = Build.class.getDeclaredMethod("getString", String.class);
            getString.setAccessible(true);
            return getString.invoke(null, "net.domain").toString();
        } catch (Exception ex) {
            return defValue;
        }
    }
    
    public static String readBuildProp(String buildProp) {
    	String ret = "";
    	try {
    		Process p = new ProcessBuilder("/system/bin/getprop", buildProp).redirectErrorStream(true).start();
    		BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
    		String line = "";
    		while ((line=br.readLine()) != null)
    		{
    			ret = line;
    		}
    		p.destroy();
    	} catch (IOException e) { e.printStackTrace(); }
    	
    	return ret;
    }
}
