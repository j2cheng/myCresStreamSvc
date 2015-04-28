package com.crestron.txrxservice;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.LinkedList;
import android.util.Log;

public class StringTokenizer 
{
	String TAG = "TxRx Tokenizer";
	Pattern pattern;
	Matcher matcher;
        private final int REGEX_LIMIT = 2;//Only First Occurence

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
	private String delims;
	private String subdelims;
	private String querydelims;
	
	public StringTokenizer() {
		delims = "\\s*\\Q\\r\\n\\E\\s*";
		subdelims = "[=]+";
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

	public String[] Parse(String str){
		//Log.d(TAG, "At Parser::input string is '"+str+"'");
		String[] myStr = null;// = new String[2];
		Pattern regexP = Pattern.compile("([^=\r\n]+)(?:=([^\r\n]*))?");
        Matcher regexM = regexP.matcher(str);
		
        while (regexM.find())
        {
        	String cmd = regexM.group(1);
        	if (regexM.group(2) != null)
        	{
        		myStr = new String[2];
        		myStr[0] = myStr[1] = "";
        		myStr[0] = cmd;
        		String val = regexM.group(2);
        		myStr[1] = val;
        		Log.d(TAG, "At Parser::strings are '"+cmd+"' '"+val+"'");
        		if(cmd.equalsIgnoreCase("start") || cmd.equalsIgnoreCase("stop")|| cmd.equalsIgnoreCase("pause")){
					Log.d(TAG, "received control cmd");
				}
        		else
        		{
        			SearchElement(cmd);
					list.add(new Token(cmd, val));
        		}
        	}
        	else
        	{
        		myStr = new String[1];
        		myStr[0] = "";
        		myStr[0] = cmd;
        		Log.d(TAG, "Query has been made for '"+cmd+"'");
        	}
        }
        
        if (myStr == null)
        {
        	myStr = new String[1];
        	myStr[0] = "";
        }

		return myStr;
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
