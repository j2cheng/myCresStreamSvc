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
	public static String replyString;
	int port = 0, tmode = 0, w = 0, h = 0, profile = 0;

	private ServerSocket serverSocket;
		StringTokenizer tokenizer; 
		private BufferedWriter out; 

	private final CresStreamCtrl c_streamctl;
	public static final int SERVERPORT = 9876;
	private BufferedReader input;

	String[] array = {"MODE", "SessInitMode", "STREAMURL", "VENCPROFILE", "TRANSPORTMODE", "RTSPPORT", "TSPORT", "RTPVIDEOPORT", "RTSPAUDIOPORT", "HDMIOUTPUTRES", "IPADDRESS", "START", "STOP", "PAUSE"};
	
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
						if(read.equalsIgnoreCase("help")){
							StringBuilder sb = new StringBuilder(4096);
							String str1= "MODE (= 0:STREAMIN 1: STREAMOUT 2:PREVIEW)\r\n";
							String str2= "SessInitMode: (= 0: ByReceiver 1: ByTransmitter 3: MCastviaRTSP 4: MCastviaUDP)\r\n";
							String str3= "TRANSPORTMODE: (= 0: RTSP 1: RTP 2: TS_RTP 3: TS_UDP)\r\n";
							String str4= "VENCPROFILE: (= 1:BaseProfile 2:MainProfile 8:HighProfile)\r\n";
							String str5= "STREAMURL(= any url) \r\n";
							String str6= "RTSPPORT(= any port)\r\n";
							String str7= "TSPORT(Dummy,Use RTSP Port)\r\n";
							String str8= "RTPVIDEOPORT(Dummy,Use RTSP Port)\r\n";
							String str9= "RTPAUDIOPORT(Dummy,Use RTSP Port)\r\n";
							String str10= "HDMIOUTPUTRES(=1920x1080)\r\n";
							String str11= "IPADDRESS(=xxx.xxx.xxx.xxx)\r\n";
							String str12= "START | STOP | PAUSE (=true)\r\n";
							String str13="Type command for query\r\n";
							sb.append("TxRx>").append(str1).append(str2).append(str3).append(str4).append(str5).append(str6).append(str7).append(str8).append(str9).append(str10).append(str11).append(str12).append(str13).append("\r\n");
							out.write(sb.toString());
							out.flush();
						}
						else{
							StringBuilder sb = new StringBuilder(1024);
							sb.append(read).append("TxRx>\r\n");
							out.write(sb.toString());
							out.flush();
							publishProgress(read);
						}
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

	}

	void callbackFunc(int i, String l_msg){
		String tmp_str = l_msg;
		int val = 0;

		switch(i){
			case 0://DeviceMode
				{
					val = Integer.parseInt(l_msg);
					c_streamctl.setDeviceMode(val);
				}
				break;
			case 1:
				{
					val = Integer.parseInt(tmp_str);
					c_streamctl.setSessionInitMode(val);
				}
				break;
			case 2://StreamIn url
				{
					c_streamctl.setStreamInUrl(tmp_str);
				}
				break;
			case 3://VideoProfile
				{
					profile = Integer.parseInt(tmp_str);
				}
				break;
			case 4://TransportType
				{
					tmode = Integer.parseInt(tmp_str);
				}
				break;
			case 5://RTSP Port
			case 6://TS Port
			case 7://RTP VPort
			case 8:// RTP APort
				{
					port = Integer.parseInt(tmp_str);
				}
				break;
			case 9://Resolution
				{
					String[] str = tmp_str.split("[x]+");
					w = Integer.parseInt(str[0]);
					h = Integer.parseInt(str[1]);
				}
				break;
			case 10://IPAddr
				{
					c_streamctl.setStreamOutConfig(tmp_str, port, w, h, tmode, profile);
				}
				break;
			case 11://START
				{
					c_streamctl.Start();
				}
				break;
			case 12://STOP
				{
					c_streamctl.Stop();
				}
				break;
			case 13://PAUSE
				{
					c_streamctl.Pause();
				}
				break;
			default:
				break;
		}	
	}

        
        protected void onProgressUpdate(String... progress) { 
            String tmp_str;
            String receivedMsg = (String)progress[0];
            String[] msg = tokenizer.Parse(receivedMsg);

            for(int i = 0; i< array.length; i++){
                if(array[i].equalsIgnoreCase(msg[0])){
                    if(msg.length>1) {	//cmd processing
                        callbackFunc(i, msg[1]);
                    }
                    else {	//QUERY Procssing
                        Log.d(TAG, "Query mode for loop "+i);
                        tmp_str = tokenizer.getStringValueOf(msg[0]);
                        Log.d(TAG, "Query mode searched for "+msg[0]+"and got value of"+tmp_str);
                        if(tmp_str!=null){
                            StringBuilder sb = new StringBuilder(1024);
                            replyString = tmp_str ;
                            sb.append(replyString).append("TxRx>\r\n");
                            try {
                                out.write(sb.toString());
                                out.flush();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }	
            }        
        }
}
