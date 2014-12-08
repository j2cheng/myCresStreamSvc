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
        boolean isWhiteSpace = false;
	public static String replyString;
	int port = 0, tmode = 0, w = 0, h = 0, profile = 0, venclevel = 0;

	private ServerSocket serverSocket;
		StringTokenizer tokenizer; 
		private BufferedWriter out; 

	private final CresStreamCtrl c_streamctl;
	public static final int SERVERPORT = 9876;
	private BufferedReader input;

	String[] array = {"MODE", "SessionInitiation", "STREAMURL", "VENCPROFILE", "TPROTOCOL", "RTSPPORT", "TSPORT", "RTPVIDEOPORT", "RTPAUDIOPORT", "HDMIOUTPUTRES", "IPADDRESS", "START", "STOP", "PAUSE", "VLEVELINFO", "streamstate"};
	
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
					Log.d(TAG, "closed down the socket" );
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
					if(read!=null && !(isWhiteSpace=(read.matches("^\\s*$"))))
                                        {
                                            Log.d(TAG, "msg recived is "+read);
                                            if((read.trim()).equalsIgnoreCase("help")){
                                                StringBuilder sb = new StringBuilder(4096);
                                                String str1= "MODE (= 0:STREAMIN 1: STREAMOUT 2:PREVIEW)\r\n";
                                                String str2= "SessionInitiation: (= 0: ByReceiver 1: ByTransmitter 3: MCastviaRTSP 4: MCastviaUDP)\r\n";
                                                String str3= "TPROTOCOL: (= 0: RTSP 1: RTP 2: TS_RTP 3: TS_UDP)\r\n";
                                                String str4= "VENCPROFILE: (= 1:BaseProfile 2:MainProfile 8:HighProfile)\r\n";
                                                String str5= "STREAMURL(= any url) \r\n";
                                                String str6= "RTSPPORT(= any port)\r\n";
                                                String str7= "TSPORT(Dummy,Use RTSP Port)\r\n";
                                                String str8= "RTPVIDEOPORT(Dummy,Use RTSP Port)\r\n";
                                                String str9= "RTPAUDIOPORT(Dummy,Use RTSP Port)\r\n";
                                                String str10= "HDMIOUTPUTRES(=1920x1080)\r\n";
                                                String str11= "IPADDRESS(=xxx.xxx.xxx.xxx)\r\n";
                                                String str12= "START | STOP | PAUSE (=true)\r\n";
                                                String str13= "VLEVELINFO (= 1:for 4.1 level 2:for 4.2 level)\r\n";
                                                String str14= "Type COMMAND for Query |streamstate to know status\r\n";
                                                sb.append(str1).append(str2).append(str3).append(str4).append(str5).append(str6).append(str7).append(str8).append(str9).append(str10).append(str11).append(str12).append(str13).append(str14).append("\r\nTxRx>");
                                                out.write(sb.toString());
                                                out.flush();
                                            }
                                            else{
                                                //StringBuilder sb = new StringBuilder(1024);
                                                //sb.append("\r\nTxRx>");
                                                //out.write(sb.toString());
                                                //out.flush();
                                                publishProgress(read.trim());
                                            }
                                        }
                                        else{//white space or NULL Commands
                                            StringBuilder sb = new StringBuilder(1024);
                                            sb.append("\r\nTxRx>");
                                            out.write(sb.toString());
                                            out.flush();
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
					c_streamctl.setStreamOutConfig(tmp_str, port, w, h, tmode, profile, venclevel);
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
                case 14://Video Encoding Level
                    {
                        venclevel = Integer.parseInt(tmp_str);
                    }
                    break;
                default:
                    break;
            }	
        }


        protected void onProgressUpdate(String... progress) { 
            String tmp_str;
            String receivedMsg = (String)progress[0];
            StringBuilder sb = new StringBuilder(1024);
            String[] msg = tokenizer.Parse(receivedMsg);

            for(int i = 0; i< array.length; i++){
                if(array[i].equalsIgnoreCase(msg[0])){
                    if(msg.length>1) {//cmd processing
                        callbackFunc(i, msg[1]);
                        sb.append("\r\nTxRx>");
                    }
                    else {
                        if(msg[0].equalsIgnoreCase("streamstate")){//Send StreamState
                            int streamState = c_streamctl.getStreamState();
                            switch(streamState){
                                case 0:
                                    replyString ="STREAMING IN";
                                    break;
                                case 1:
                                    replyString ="STREAMING OUT";
                                    break;
                                case 2:
                                    replyString ="Previewing Video";
                                    break;
                                default:
                                    replyString = "Device in IdleMode";
                            }
                            sb.append(receivedMsg).append("=").append(replyString).append("\r\nTxRx>");
                        }
                        else {//QUERY Procssing
                            tmp_str = tokenizer.getStringValueOf(msg[0]);
                            Log.d(TAG, "Querying:: searched for "+msg[0]+" and got value of "+tmp_str);
                            if(tmp_str!=null){
                                replyString = tmp_str ;
                                sb.append(receivedMsg).append("=").append(replyString).append("\r\nTxRx>");
                            }
                            else{
                                replyString="";
                                replyString.trim();
                                sb.append(replyString).append("\r\nTxRx>");
                            } 
                        }
                    }
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
