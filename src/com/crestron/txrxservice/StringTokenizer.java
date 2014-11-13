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
		public final int token;
		public final String sequence;

		public Token(int token, String sequence)
		{
			super();
			this.token = token;
			this.sequence = sequence;
		}

	}
	private LinkedList<Token> tokens;
	private String delims;
	private String subdelims;
	private String querydelims;

	public StringTokenizer() {
		delims = "\\s*\\Q\\r\\n\\E\\s*";
		subdelims = "[=]+";
		tokens = new LinkedList<Token>();
	}

	public void parse(String str)
	{
		tokens.clear();
		String[] l_tokens = str.split(delims);
		for (int i = 0; i < l_tokens.length; i++){
			Log.d(TAG, l_tokens[i]);
			String tok = l_tokens[i];
			tokens.add(new Token(i, tok));
		}
	}
	
	public String getStringValueOf(String regex){
		String value = null;
		for (StringTokenizer.Token tok : getTokens())
		{
			String newtoken = tok.sequence;
			pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);	
			matcher = pattern.matcher(newtoken);
			if(matcher.find()){
				String[] value2 = newtoken.split(subdelims);
				if(value2.length>1)
					value = value2[1];
				break;
			}
		}
		return value;
	}
	
	public LinkedList<Token> getTokens()
	{
		return tokens;
	}
}
