package com.crestron.txrxservice;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.ListIterator;
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

public class TCPInterface extends AsyncTask<Void, Object, Long> {
    String TAG = "TxRx TCPInterface"; 
    MessageParser parserInstance;
    boolean isWhiteSpace = false;
    static boolean connectionAlive = true;
    static boolean clientNew = false;

    public static final int SERVERPORT = 9876;
    private ServerSocket serverSocket;
    private BufferedReader input;
    
    private ArrayList<CommunicationThread> clientList;

    public TCPInterface(CresStreamCtrl a_crestctrl){
        parserInstance = new MessageParser (a_crestctrl);
        clientList = new ArrayList<TCPInterface.CommunicationThread>();
    }
    
    public void RemoveClientFromList(CommunicationThread clientThread)
    {
    	try
    	{
    		clientList.remove(clientThread);
    	}
    	catch (Exception e)
    	{
    		e.printStackTrace();
    	}
    }
    
    
    /***
     * Function to asynchronously send data to all clients
     * @param data
     */
    public void SendDataToAllClients(String data)
    {
    	// First make sure that thread exists and then send out the data
        for (ListIterator<CommunicationThread> iter = clientList.listIterator(clientList.size()); iter.hasPrevious();)
        {
        	CommunicationThread thread = iter.previous();
        	if (thread != null)
        	{
        		thread.SendDataToClient(data);
        	}
        }

    }
    
    protected Long doInBackground(Void... paramVarArgs)
    {
        Socket clientSocket = null;
        try {
            serverSocket = new ServerSocket(SERVERPORT);
        } catch (IOException e) {
            e.printStackTrace();
        }
        while (!Thread.currentThread().isInterrupted()) {

            try {
                clientSocket = serverSocket.accept();
                Log.d(TAG, "Client connected to clientSocket: " + clientSocket.toString());
                connectionAlive = true;//New Client Connected
                clientNew = true;//New Client Connected
                CommunicationThread commThread = new CommunicationThread(clientSocket, this);
                clientList.add(commThread);
                new Thread(commThread).start();

            } catch (IOException e) {
                e.printStackTrace();
            }
            if (isCancelled()){
                try {
                    serverSocket.close();
                    for (ListIterator<CommunicationThread> iter = clientList.listIterator(clientList.size()); iter.hasPrevious();)
                    {
                    	CommunicationThread thread = iter.previous();
                    	if (thread != null)
                    	{
                    		if (thread.clientSocket != null)
                    		{
                    			thread.clientSocket.close();
                    		}
                        	iter.remove();
                    	}
                    }
                    Log.d(TAG, "closed down the server socket" );
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
        private BufferedWriter out; 
        private TCPInterface serverHandler;

        public CommunicationThread(Socket clientSocket, TCPInterface server) {

            this.clientSocket = clientSocket;
            this.serverHandler = server;

            try {

                input = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                out = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));

            } catch (IOException e) {
                e.printStackTrace();
                //  Was client socket closed - Clean up
                Log.d(TAG, "Client cleanup ");
                serverHandler.RemoveClientFromList(this);
            }
        }
        
        /***
         * Lock the send and send the data to the client
         * @param data
         */
        public void SendDataToClient(String data)
        {
            synchronized(out)
            {
                try 
                {
                    out.write(data);
                    out.flush();
                } 
                catch (IOException e) 
                {
                    e.printStackTrace();
                }
            }
        }

        public void run() {
            while (connectionAlive) {
                if(clientNew){
                        for(String s: parserInstance.cmdArray){
                            publishProgress(s, this);
                        }
                        clientNew = false;
                }
                try {
                    String read = input.readLine();
                    if(read!=null && !(isWhiteSpace=(read.matches("^\\s*$"))))
                    {
                        Log.d(TAG, "msg recived is "+read);
                        if((read.trim()).equalsIgnoreCase("help")){
                            String validatedMsg = parserInstance.validateReceivedMessage(read);
                            SendDataToClient(validatedMsg);
                        }
                        else{
                            publishProgress(read.trim(), this);
                        }
                    }
                    else if(read == null) {
                        Log.d(TAG, "Client Disconnected..... ");
                        connectionAlive = false;
                    }
                    else{//white space or NULL Commands
                        StringBuilder sb = new StringBuilder(1024);
                        sb.append("\r\nTxRx>");
                        SendDataToClient(sb.toString());
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    protected void onProgressUpdate(Object... progress) { 
        String tmp_str;
        String receivedMsg = (String)progress[0];
        CommunicationThread thread = (CommunicationThread)progress[1];
        tmp_str = parserInstance.processReceivedMessage(receivedMsg); 
        try {
            thread.SendDataToClient(tmp_str);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
