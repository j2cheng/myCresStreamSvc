package com.crestron.txrxservice;

import android.os.AsyncTask;
import android.util.Log;
import android.os.Handler;
import android.os.Message;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class SocketListener extends AsyncTask<Void, String, Long>
{
	boolean stream_out = false;
	boolean stream_in =  false;
	boolean preview =  false;
	int stream_mode = 0;
	String TAG = "TxRx SocketListener"; 
	DatagramPacket p;
	DatagramSocket s;
	String StreamInUrl;
	private final CresStreamCtrl c_streamctl;
	//Handler l_handler = CresStreamCtrl.handler;

	public SocketListener(CresStreamCtrl a_crestctrl){
		c_streamctl = a_crestctrl;
	}

	protected Long doInBackground(Void... paramVarArgs)
	{
		Log.d(TAG, "In SocketListner");
		String text;
		int server_port = 9876;
		byte[] msg = new byte[512];
		p = new DatagramPacket(msg, msg.length);
		try{
			s = new DatagramSocket(server_port);
		}catch (Exception e){
			e.printStackTrace();
		}
		while(true){
			try{
				s.receive(p);
				text = new String(msg, 0, p.getLength());
				Log.d("Udp receiver","message:" + text);
				publishProgress(text);
				//Message n_msg =l_handler.obtainMessage();
				//n_msg.obj = text;
				//l_handler.sendMessage(n_msg);
			}catch (Exception e){
				s.close();
				e.printStackTrace();
			}
			if (isCancelled()){
				s.close();
				break;
			}
		}
		return Long.valueOf(0L);
	}
	protected void onProgressUpdate(String... progress) {
		String receivedMsg = (String)progress[0];
			if(receivedMsg.equalsIgnoreCase("start"))
			{
				if(stream_out==true){
					Log.e(TAG, "Starting Recording");
					c_streamctl.startStreamOut();
				//	try {
				//		cam_streaming.startRecording();
				//	} catch(IOException e) {
				//		e.printStackTrace();
				//	}
				}else if(stream_in == true){
					Log.e(TAG, "Starting in ");
					c_streamctl.startStreamIn();
				//	if((cam_streaming.mCameraObj.APlayStatus())==true)
				//		cam_streaming.mCameraObj.stopAudio();
				//	showStreamInWindow();
				//	streamPlay.onStart();
				}else if(preview == true){
					Log.e(TAG, "Starting preview");
					c_streamctl.startPreview();
				//	cam_streaming.mCameraObj.startAudio();
					//cam_streaming.mCameraObj.stopPlayback();
					//cam_streaming.mCameraObj.startPlayback(true);
				}
				else
					Log.e(TAG, "Error in configuration");
			}
			else if(receivedMsg.equalsIgnoreCase("stop"))
			{
				Log.e(TAG, "stop");
				if(stream_out==true){
					c_streamctl.stopStreamOut();
				//	cam_streaming.stopRecording();
					stream_out = false;
				}
				if(stream_in==true){
					c_streamctl.stopStreamIn();
			//		streamPlay.onStop();
					stream_in =  false;
			//		hideStreamInWindow();
				}
				//preview =  false;
			}
			/*else if(receivedMsg.equalsIgnoreCase("pause"))
			{
				if((cam_streaming.mCameraObj.IsPauseStatus()==false))
					cam_streaming.mCameraObj.pausePlayback();
				else
					cam_streaming.mCameraObj.resumePlayback();
			}*/
			else{
				String delims = "[;]+";
				String[] tokens = receivedMsg.split(delims);;
				for (int i = 0; i < tokens.length; i++){
					Log.d(TAG, "Tokens "+tokens[i]);
				}
				String text = tokens[0];
				if(text.equalsIgnoreCase("Rx")){
					Log.i(TAG, "Activating By Receiver Mode");
					c_streamctl.setStreamInUrl(tokens[2]);
					//streamPlay.setUrl(tokens[2]);
					stream_in = true;
				}
				else if(text.equalsIgnoreCase("Tx")){
					Log.i(TAG, "Activating By Transmitter Mode");
					configure(tokens);
					//cam_streaming.configure(tokens);
					stream_out = true;
				}
				else if(text.equalsIgnoreCase("Pre")){
					Log.i(TAG, "Activating PreviewMode");
					preview = true;
				}
				else 
					Log.i(TAG, "Inavlid Message Received. Ignoring !!!!");
			}
	}

	public void configure(String[] textTokens)  {
		String l_ip;
		int l_mode, out_w, out_h, l_port, l_profile;

		CresStreamConfigure myconfig = new CresStreamConfigure();
		Log.d(TAG, "configuring with: ");
		for (int i = 0; i < textTokens.length; i++){
			Log.d(TAG, textTokens[i]);
		}
		l_ip 	= textTokens[2];
		myconfig.setIP(l_ip);	
		l_port 	= Integer.parseInt(textTokens[3]);
		myconfig.setPort(l_port);	
		out_w 	= Integer.parseInt(textTokens[4]);
		myconfig.setWidth(out_w);	
		out_h 	= Integer.parseInt(textTokens[5]);
		myconfig.setHeight(out_h);	
		if(textTokens[6].equalsIgnoreCase("BP"))
			l_profile = 1;
		else if(textTokens[6].equalsIgnoreCase("MP"))
			l_profile = 2;
		else//HP
			l_profile = 8;
		myconfig.setVEncProfile(l_profile);	
		if(textTokens[1].equalsIgnoreCase("RTSP"))
		{
			l_mode=0;
		}
		else if(textTokens[1].equalsIgnoreCase("RTP"))
		{
			l_mode=1;
		}
		else if(textTokens[1].equalsIgnoreCase("TSR"))
		{
			l_mode=2;
		}
		else if(textTokens[1].equalsIgnoreCase("TSU"))
		{
			l_mode=3;	
		}
		else if(textTokens[1].equalsIgnoreCase("MJPEG"))
		{
			l_mode=4;	
		}
		else
			l_mode=0;	

		myconfig.setMode(l_mode);	
	}
}
