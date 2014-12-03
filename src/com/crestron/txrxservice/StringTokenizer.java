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
	}

	private void SearchElement(String str){
		String l_str = str;
		for (StringTokenizer.Token tok : getTokens())
		{
			Log.d(TAG, "SearchElement::tokens are "+tok.sequence1+" "+tok.sequence2);
			String newtoken = tok.sequence1;
			pattern = Pattern.compile(l_str, Pattern.CASE_INSENSITIVE);	
			matcher = pattern.matcher(newtoken);
			if(matcher.find()){
				boolean found = list.removeFirstOccurrence(tok);
				Log.d(TAG, "removedToken "+found );
				break;
			}
		}
	}

	public String[] Parse(String str){
		String[] myStr = null;
		String[] l_tokens = str.split(delims);
		for (int i = 0; i < l_tokens.length; i++){
			String tok = l_tokens[i];
			myStr = tok.split("=");
			Log.d(TAG, "Total fields are "+ myStr.length);
			if(myStr.length>1){
				Log.d(TAG, "At Parser::strings are "+ myStr[0]+" "+myStr[1]);
				SearchElement(myStr[0]);
				list.add(new Token(myStr[0], myStr[1]));
			}else{
				Log.d(TAG, "Query has been made for "+myStr[0]);
			}
		}
		return myStr;
	}

	public void getMyTokens(){
		for (StringTokenizer.Token tok : getTokens())
		{
			String newtoken = tok.sequence1;
			String newtoken1 = tok.sequence2;
			Log.d(TAG, "tokens are "+newtoken+" "+newtoken1);
		}
	}
	public String getStringValueOf(String regex){
		String value = null;
		for (StringTokenizer.Token tok : getTokens())
		{
			Log.d(TAG, "searching for "+tok.sequence1+ "and"+tok.sequence2);
			String newtoken = tok.sequence1;
			pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);	
			matcher = pattern.matcher(newtoken);
			if(matcher.find()){
				Log.d(TAG, "found");
				value = tok.sequence2;
				break;
			}
		}
		return value;
	}
	
	public LinkedList<Token> getTokens()
	{
		return list;
	}
}
