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
	private LinkedList<Token> list;
	private String delims;
	private String subdelims;
	private String querydelims;

	public StringTokenizer() {
		delims = "\\s*\\Q\\r\\n\\E\\s*";
		subdelims = "[=]+";
		list = new LinkedList<Token>();
		list.add(new Token("mode", "0"));
		list.add(new Token("sessioninitiation", "0"));
		list.add(new Token("transportmode", "0"));
		list.add(new Token("vencprofile", "2"));
		list.add(new Token("rtspport", "12462"));
		list.add(new Token("tsport", "12460"));
		list.add(new Token("rtpvideoport", "12458"));
		list.add(new Token("rtpaudioport", "12456"));
		list.add(new Token("vframerate", "50"));
		list.add(new Token("vbitrate", "6000"));
		list.add(new Token("hdmioutputres", "17"));
		list.add(new Token("mutestate", "false"));
		list.add(new Token("latency", "2000"));
		list.add(new Token("username", ""));
		list.add(new Token("password", ""));
		list.add(new Token("Xloc", "0"));
		list.add(new Token("Yloc", "0"));
		list.add(new Token("w", "1920"));
		list.add(new Token("h", "1080"));
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
		String[] myStr = null;
		String[] l_tokens = str.split(delims);
		for (int i = 0; i < l_tokens.length; i++){
			String tok = l_tokens[i];
			myStr = tok.split("=", REGEX_LIMIT);
			if(myStr.length>1){
				Log.d(TAG, "At Parser::strings are "+ myStr[0]+" "+myStr[1]);
				if(myStr[0].equalsIgnoreCase("start") || myStr[0].equalsIgnoreCase("stop")|| myStr[0].equalsIgnoreCase("pause")){
					Log.d(TAG, "received control cmd");
				}
				else{
					SearchElement(myStr[0]);
					list.add(new Token(myStr[0], myStr[1]));
				}
			}else{
				Log.d(TAG, "Query has been made for "+myStr[0]);
			}
		}
		return myStr;
	}

	public String getStringValueOf(String regex){
		String value = null;
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
