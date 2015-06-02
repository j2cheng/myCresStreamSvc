package com.crestron.txrxservice;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Iterator;
import java.util.LinkedList;
import android.util.Log;

public class StringTokenizer 
{
	String TAG = "TxRx Tokenizer";
	Pattern pattern;
	Matcher matcher;
	
	public class ParseResponse
	{
		public String joinName;
		public String joinValue;
		public int sessId;
		public boolean sessIdSpecified;	//mark if sessId was specified when sent through debug interface
		
		public ParseResponse()
		{
			this.joinName = "";
			this.joinValue = "";
			this.sessId = 0;
			this.sessIdSpecified = false;
		}
	}

    private String processCmdForSessionCreate(String msg){
        StringBuilder sb = new StringBuilder();
        boolean found = false;
        for(char c: msg.toCharArray()){
            if(Character.isDigit(c)){
                sb.append(c);
                found = true;
            }else if(found){
                break;
            }
        }
        return sb.toString();
    }

	public ParseResponse Parse(String str)
    {
        ParseResponse parseRes = new ParseResponse();
        Pattern regexP = Pattern.compile("([^=\\r\\n\\d]+)(\\d+)?(?:(=)([^\\r\\n]+)?)?");	//Group1=JoinName, Group2=SessId, Group3=EqualSign, Group4=JoinVal
        Matcher regexM = regexP.matcher(str);

        while (regexM.find())
        {
        	if (regexM.group(1) != null)
        		parseRes.joinName = regexM.group(1);
        	else
        		parseRes.joinName = "";
        	if (regexM.group(2) != null)
        	{
        		parseRes.sessIdSpecified = true;
        		parseRes.sessId = Integer.parseInt(regexM.group(2));
        	}
        	else
        	{
        		parseRes.sessIdSpecified = false;
        		parseRes.sessId = 0;
        	}
        	if (regexM.group(4) != null)
        		parseRes.joinValue = regexM.group(4);
        	else
        	{
        		if (regexM.group(3) != null)
        			parseRes.joinValue = "";
        		else
        			parseRes.joinValue = null;
        	}
        	
        	String joinNameWithSessId = (parseRes.joinName + String.valueOf(parseRes.sessId));
        	
            if (regexM.group(3) != null)
            {
                Log.d(TAG, "At Parser::strings are '"+joinNameWithSessId+"' '"+parseRes.joinValue+"'");
                if(parseRes.joinName.equalsIgnoreCase("start") || parseRes.joinName.equalsIgnoreCase("stop")|| parseRes.joinName.equalsIgnoreCase("pause")){
                    Log.d(TAG, "received control cmd");
                }
            }
            else
                Log.d(TAG, "Query has been made for '"+joinNameWithSessId+"'");
            
        }

        return parseRes;
    }
}
