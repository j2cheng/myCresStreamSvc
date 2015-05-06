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

	public class Token
    {
        public final String sequence1, sequence2 ;

        public Token(String sequence1, String sequence2)
        {
            super();
            this.sequence1 = sequence1;
            this.sequence2 = sequence2;
        }
    }
	private static LinkedList<Token> list;
	private static int sessionIDX = 0;
	private static boolean markSessionForDelete = false;

    public StringTokenizer() {
        list = new LinkedList<Token>();
    }

    public void AddTokenToList(String command, String value){
        SearchElement(command);
        list.add(new Token(command, value));
    }

    private void SearchElement(String str){
        String l_str = str;
        for (StringTokenizer.Token tok : getTokens())
        {
            //Log.d(TAG, "SearchElement::tokens are "+tok.sequence1+" "+tok.sequence2);
            String newtoken = tok.sequence1;
            pattern = Pattern.compile(l_str, Pattern.CASE_INSENSITIVE);	
            matcher = pattern.matcher(newtoken);
            if(matcher.matches()){
                boolean found = list.remove(tok);
                //Log.d(TAG, "removedToken "+found );
                break;
            }
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
        Pattern regexP = Pattern.compile("([^=\\r\\n\\d]+)(\\d+)?(?:=([^\\r\\n]+))?");
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
        	if (regexM.group(3) != null)
        		parseRes.joinValue = regexM.group(3);
        	else
        		parseRes.joinValue = "";
        	
        	String joinNameWithSessId = (parseRes.joinName + String.valueOf(parseRes.sessId));
        	
            if (regexM.group(3) != null)
            {
                Log.d(TAG, "At Parser::strings are '"+joinNameWithSessId+"' '"+parseRes.joinValue+"'");
                if(parseRes.joinName.equalsIgnoreCase("start") || parseRes.joinName.equalsIgnoreCase("stop")|| parseRes.joinName.equalsIgnoreCase("pause")){
                    Log.d(TAG, "received control cmd");
                }
                else
                {
                    SearchElement(parseRes.joinName);
                    list.add(new Token(joinNameWithSessId, parseRes.joinValue));
                }
            }
            else
                Log.d(TAG, "Query has been made for '"+joinNameWithSessId+"'");
            
        }

        return parseRes;
    }

    public String getStringValueOf(String regex){
        String value = "";
        for (StringTokenizer.Token tok : getTokens())
        {
            //Log.d(TAG, "searching for "+regex+" and entries are "+tok.sequence1+" and "+tok.sequence2);
            String newtoken = tok.sequence1;
            pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);	
            matcher = pattern.matcher(newtoken);
            if(matcher.matches()){
                //Log.d(TAG, "found");
                value = tok.sequence2;
                break;
            }
        }
        return value;
    }

    public void printList()
    {
        Log.d(TAG, "###########LIST MARKER START");
        for (StringTokenizer.Token tok : getTokens())
        {
            Log.d(TAG, "List of entries are "+tok.sequence1+" and "+tok.sequence2);
        }
        Log.d(TAG, "###########LIST MARKER END");
    }

    public LinkedList<Token> getTokens()
    {
        return list;
    }
}
