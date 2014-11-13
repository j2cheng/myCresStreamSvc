package com.crestron.txrxservice;

import java.io.BufferedWriter;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;

import android.os.AsyncTask;
import android.util.Log;
import android.os.Bundle;

public class TCPInterface extends AsyncTask<Void, String, Long> {
	String TAG = "TxRx TCPInterface"; 

	private ServerSocket serverSocket;
		StringTokenizer tokenizer; 

	private final CresStreamCtrl c_streamctl;
	public static final int SERVERPORT = 9876;
	private BufferedReader input;
	
	public TCPInterface(CresStreamCtrl a_crestctrl){
		c_streamctl = a_crestctrl;
		tokenizer = new StringTokenizer();
	}
	protected Long doInBackground(Void... paramVarArgs)
	{
		Socket socket = null;
		try {
			serverSocket = new ServerSocket(SERVERPORT);
		} catch (IOException e) {
			e.printStackTrace();
		}
		while (true) {

			try {
				socket = serverSocket.accept();
				Log.d(TAG, "Client connected to socket: " + socket.toString());

				CommunicationThread commThread = new CommunicationThread(socket);
				new Thread(commThread).start();

			} catch (IOException e) {
				e.printStackTrace();
			}
			if (isCancelled()){
				try {
					serverSocket.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
				break;
			}
		}
		return Long.valueOf(0L);
	}

	class CommunicationThread implements Runnable {

		private Socket clientSocket;
		private BufferedReader input;
		private BufferedWriter out; ;

		public CommunicationThread(Socket clientSocket) {

			this.clientSocket = clientSocket;

			try {

				input = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
				out = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));

			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		public void run() {

			while (!Thread.currentThread().isInterrupted()) {

				try {

					String read = input.readLine();
					if(read!=null)
					{
						Log.d(TAG, "msg recived is "+read);
						StringBuilder sb = new StringBuilder(1024);
						sb.append(read).append("\r\nTXRX\r\n");
						out.write(sb.toString());
						out.flush();
						publishProgress(read);
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

	}
	protected void onProgressUpdate(String... progress) { 
		String tmp_str;
		int val = 0;
		int port = 0, tmode = 0, w = 0, h = 0, profile = 0;
		String receivedMsg = (String)progress[0];
		tokenizer.parse(receivedMsg);

		tmp_str = tokenizer.getStringValueOf("MODE");
		if(tmp_str!=null){
			Log.d(TAG, "1.tmp_Str is "+tmp_str);
			val = Integer.parseInt(tmp_str);
			c_streamctl.setDeviceMode(val);
		}

		tmp_str = tokenizer.getStringValueOf("SessInitMode");
		if(tmp_str!=null){
			Log.d(TAG, "2.tmp_Str is "+tmp_str);
			val = Integer.parseInt(tmp_str);
			c_streamctl.setSessionInitMode(val);
		}

		tmp_str = tokenizer.getStringValueOf("VENCPROFILE");
		if(tmp_str!=null){
			Log.d(TAG, "3.tmp_Str is "+tmp_str);
			profile = Integer.parseInt(tmp_str);
		}
		tmp_str = tokenizer.getStringValueOf("TRANSPORTMODE");
		if(tmp_str!=null){
			Log.d(TAG, "4.tmp_Str is "+tmp_str);
			tmode = Integer.parseInt(tmp_str);
		}
		tmp_str = tokenizer.getStringValueOf("STREAMURL");
		if(tmp_str!=null){
			Log.d(TAG, "8.tmp_Str is "+tmp_str);
			c_streamctl.setStreamInUrl(tmp_str);
		}
		tmp_str = tokenizer.getStringValueOf("TSPORT");
		if(tmp_str!=null){
			Log.d(TAG, "9.tmp_Str is "+tmp_str);
			port = Integer.parseInt(tmp_str);
		}
		tmp_str= tokenizer.getStringValueOf("RTPVIDEOPORT");
		if(tmp_str!=null){
			Log.d(TAG, "10.tmp_Str is "+tmp_str);
			port = Integer.parseInt(tmp_str);
		}
		tmp_str = tokenizer.getStringValueOf("RTPAUDIOPORT");
		if(tmp_str!=null){
			Log.d(TAG, "11.tmp_Str is "+tmp_str);
			port = Integer.parseInt(tmp_str);
		}
		tmp_str = tokenizer.getStringValueOf("RTSPPORT");
		if(tmp_str!=null){
			Log.d(TAG, "12.tmp_Str is "+tmp_str);
			port = Integer.parseInt(tmp_str);
		}
		tmp_str = tokenizer.getStringValueOf("HDMIOUTPUTRES");
		if(tmp_str!=null){
			Log.d(TAG, "13.tmp_Str is "+tmp_str);
			String[] str = tmp_str.split("[x]+");
			w = Integer.parseInt(str[0]);
			h = Integer.parseInt(str[1]);
		}
		String ip_addr = tokenizer.getStringValueOf("IPADDR");
		if(tmp_str!=null){
			Log.d(TAG, "14.tmp_Str is "+tmp_str);
			c_streamctl.setStreamOutConfig(ip_addr, port, w, h, tmode, profile);
		}
		tmp_str= tokenizer.getStringValueOf("START");
		if(tmp_str!=null){
			Log.d(TAG, "5.tmp_Str is "+tmp_str);
			c_streamctl.Start();
		}
		tmp_str= tokenizer.getStringValueOf("STOP");
		if(tmp_str!=null){
			Log.d(TAG, "6.tmp_Str is "+tmp_str);
			c_streamctl.Stop();
		}
		tmp_str= tokenizer.getStringValueOf("PAUSE");
		if(tmp_str!=null){
			Log.d(TAG, "7.tmp_Str is "+tmp_str);
			c_streamctl.Pause();
		}
	}
}
