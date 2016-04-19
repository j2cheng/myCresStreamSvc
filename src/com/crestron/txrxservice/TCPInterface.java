package com.crestron.txrxservice;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.lang.IllegalArgumentException;
import java.lang.reflect.Array;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import com.crestron.txrxservice.CresStreamCtrl.StreamState;

import android.os.AsyncTask;
import android.util.Log;
import android.os.Bundle;

public class TCPInterface extends AsyncTask<Void, Object, Long> {
    String TAG = "TxRx TCPInterface";
    public static final String CONSOLEPROMPT = "\r\nTxRx>";
    CommandParser parserInstance;
    boolean isWhiteSpace = false;
    static boolean connectionAlive = true;
    private CresStreamCtrl streamCtl;

    public static final int SERVERPORT = 9876;
    public static final String LOCALHOST = "127.0.0.1";
    private ServerSocket serverSocket;
    private volatile boolean restartStreamsPending = true;
    public volatile boolean firstRun = true;
    private final StringTokenizer tokenizer = new StringTokenizer();
    private final Object serverLock = new Object();
    
    public ArrayList<CommunicationThread> clientList;
    
    class JoinObject {
    	String joinString;
    	TCPInterface serverHandler;
    	public JoinObject(String joinString, TCPInterface serverHandler)
    	{
    		this.joinString = joinString;
    		this.serverHandler = serverHandler;
    	}
    }
    
    private final Thread[] joinProcessingThread = new Thread[CresStreamCtrl.NumOfSurfaces];
    volatile boolean shouldExit = true;
    @SuppressWarnings("unchecked")
	private Queue<JoinObject>[] joinQueue = (Queue<JoinObject>[]) new Queue[CresStreamCtrl.NumOfSurfaces];

    public TCPInterface(CresStreamCtrl a_crestctrl){
        parserInstance = new CommandParser (a_crestctrl);
        clientList = new ArrayList<TCPInterface.CommunicationThread>();
        streamCtl = a_crestctrl;
        StartJoinThread();
    }
    
    private void StartJoinThread()
    {
    	shouldExit = false;
    	
    	// Allocate memory for joinQueue, one per sessionId
    	// Kick off one thread of ProcessJoinTask per sessionId, allows parallel join processing
    	for (int sessionId = 0; sessionId < CresStreamCtrl.NumOfSurfaces; sessionId++)
    	{
    		joinQueue[sessionId] = new LinkedBlockingQueue<JoinObject>(); 
    		
    		joinProcessingThread[sessionId] = new Thread(new ProcessJoinTask(joinQueue[sessionId]));
    		joinProcessingThread[sessionId].start();
    	}
    }
    private void StopJoinThread()
    {
    	shouldExit = true;
    	
    	for (int sessionId = 0; sessionId < CresStreamCtrl.NumOfSurfaces; sessionId++)
    	{
	    	try {
	    		joinProcessingThread[sessionId].join();
	    	} catch (Exception e) {e.printStackTrace();}
    	}
    }
    
    public void RemoveClientFromList(CommunicationThread clientThread)
    {
    	try
    	{
    		synchronized (serverLock) {
    			clientList.remove(clientThread);
    		}
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
    	synchronized(serverLock)
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
    }
    
    private void restartStreams(TCPInterface serverHandler)
    {
        restartStreamsPending   = false;
        streamCtl.enableRestartMechanism = true;
        
        // Sleep for 3 seconds before calling initial restart streams to give rest of system time to catch up
//        try {
//        	Thread.sleep(3000);
//        } catch (Exception e) { e.printStackTrace(); }
        
        streamCtl.restartStreams(false);
    }
    
    public void restartStreams()
    {
    	restartStreams(clientList.get(0).serverHandler);
    }
    
    private String FindAllowedTcpAddress()
    {
    	String allowedAddress = LOCALHOST;

    	StringBuilder text = new StringBuilder();
        try {
            File file = new File("/data/crestron/config/rc.conf");

            BufferedReader br = new BufferedReader(new FileReader(file));  
            String line;   
            while ((line = br.readLine()) != null) {
                text.append(line);
                //text.append('\n');
            }
            br.close() ;
        }catch (IOException e) {
            e.printStackTrace();           
        }
        
        Pattern regexP = Pattern.compile("TELNETPORT=\"(\\d+)\"");
		Matcher regexM = regexP.matcher(text.toString());
		regexM.find();
		try {
			int currentTelnetSetting = Integer.parseInt(regexM.group(1));
			if (currentTelnetSetting == 2)
			{
				allowedAddress = null; // null address means all address accepted
			}
		} catch (Exception e) {}
            
        return allowedAddress;
    }
    
    protected Long doInBackground(Void... paramVarArgs)
    {
        Socket clientSocket = null;
        try {
        	//If telnet debug is enabled we allow all connections to 9876 port, otherwise restrict to local ip
        	String allowedAddress = FindAllowedTcpAddress();
        	if (allowedAddress == null)
        	{
        		serverSocket = new ServerSocket(SERVERPORT, 50, null);
        		Log.d(TAG, "Allowing all tcp connections to debug port");
        	}
        	else
        		serverSocket = new ServerSocket(SERVERPORT, 50, InetAddress.getByName(allowedAddress));
        } catch (IOException e) {
            e.printStackTrace();
        }
        while (!Thread.currentThread().isInterrupted()) {

            try {
                clientSocket = serverSocket.accept();
                Log.d(TAG, "Client connected to clientSocket: " + clientSocket.toString());
                connectionAlive = true;//New Client Connected
                CommunicationThread commThread = new CommunicationThread(clientSocket, this);
                synchronized (serverLock) {
                	clientList.add(commThread);
				}                
                new Thread(commThread).start();
                
                // Wait until CresStreamCtl signals that is ok to start processing join queue
                try {
					streamCtl.streamingReadyLatch.await();
				} catch (InterruptedException e) { e.printStackTrace();	}
                
                // Always wipe out previous streamstate for first connection
                if (firstRun)
                {
                	firstRun = false;
	                for (int sessionId = 0; sessionId < streamCtl.NumOfSurfaces; sessionId++)
	        		{
	            		streamCtl.SendStreamState(StreamState.STOPPED, sessionId);
	        		}   
	                
	                if (streamCtl.hdmiInputDriverPresent == true)
	                {
	                	int hdmiInEnum = HDMIInputInterface.readResolutionEnum();
	                	streamCtl.setCamera(hdmiInEnum); //no need to restart streams
	                }
                }
                
                // Update csio on hdmi input status
                if (streamCtl.hdmiInputDriverPresent)
                	SendDataToAllClients(String.format("HDMIInputConnectedState=%s", streamCtl.hdmiInput.getSyncStatus())); //true means hdmi input connected
                
                // Tell CSIO to send update request to control system
                SendDataToAllClients("UPDATE_REQUEST_TO_CONTROLSYSTEM=");

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
        public TCPInterface serverHandler;

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
                	// Append the Prompt to signal to the client that a transaction has been completed
                    out.write(data + CONSOLEPROMPT);
                    out.flush();
                } 
                catch (IOException e) 
                {
                    Log.d(TAG, "Error sending data to client.  Cleaning up");
                    serverHandler.RemoveClientFromList(this);
                }
            }
        }

        public void run() {
            while (connectionAlive) {
            	if ((streamCtl.restartStreamsOnStart == true) && (restartStreamsPending   == true))
                	restartStreams(serverHandler);
                try {
                    String read = input.readLine();
                    if(read!=null && !(isWhiteSpace=(read.matches("^\\s*$"))))
                    {
                        Log.d(TAG, "msg recived is "+read);
                        if((read.trim()).equalsIgnoreCase("help")){
                            String validatedMsg = parserInstance.validateReceivedMessage(read);
                            serverHandler.SendDataToAllClients(validatedMsg);
                        }
                        else if (read.trim().equalsIgnoreCase("updaterequest")) {
                        	for(CommandParser.CmdTable ct: CommandParser.CmdTable.values()){
                        		// Send device ready as last join
                        		if (!ct.name().equals("DEVICE_READY_FB"))
                        			addJoinToQueue(new JoinObject(ct.name(), serverHandler), 0); //Currently updaterequest will only be handled for sessionId 0
                        	}
                        	
                        	// Tell CSIO that update request is complete
                        	addJoinToQueue(new JoinObject("DEVICE_READY_FB", serverHandler), 0);
                        }
                        else{
                        	// determine sessionId first so we can add to the right queue
                        	StringTokenizer.ParseResponse parseResponse = tokenizer.Parse(read.trim());
                        	addJoinToQueue(new JoinObject(read.trim(), serverHandler), parseResponse.sessId);
                        }
                    }
                    else if(read == null) {
                        Log.d(TAG, "Client Disconnected..... ");
                        try {clientSocket.close();} catch(Exception ex) {}
                        connectionAlive = false;
                        serverHandler.RemoveClientFromList(this);
                    }
                    else{//white space or NULL Commands
                    	serverHandler.SendDataToAllClients("");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    protected void onProgressUpdate(Object... progress) { 
		//TODO: can we remove this method??
		//Intentionally left blank
    }
    
    private void addJoinToQueue(JoinObject newJoin, int sessionId) {
    	if ((sessionId >= 0) && (sessionId < CresStreamCtrl.NumOfSurfaces))
    	{
	    	synchronized (joinQueue[sessionId])
	    	{
	    		joinQueue[sessionId].add(newJoin);
	    		joinQueue[sessionId].notify();
	    	}
    	}
    }
    
    class ProcessJoinTask implements Runnable {
    	private Queue<JoinObject> jQ;
    	
    	public ProcessJoinTask(Queue<JoinObject> join_queue)
    	{
    		jQ = join_queue;
    	}
    	
        public void run() {
        	while (!shouldExit) {
        		if (jQ.isEmpty())
        		{
        			try {
        				synchronized (jQ)
        				{
        					jQ.wait(5000);
        				}
        			} catch (Exception e) {e.printStackTrace();}
        		}
        		else //process queue
        		{        			       			
        			String tmp_str;
        			
        			// Wait until CresStreamCtl signals that is ok to start processing join queue
        			try {
    					streamCtl.streamingReadyLatch.await();
    				} catch (InterruptedException e) { e.printStackTrace();	}
        			
        			JoinObject currentJoinObject = jQ.poll();
        			if (currentJoinObject != null)
        			{
	        	        String receivedMsg = currentJoinObject.joinString;
	        	        TCPInterface server = currentJoinObject.serverHandler;
	        	        
	        	        if (receivedMsg != null)
	        	        {
	        	    		tmp_str = parserInstance.processReceivedMessage(receivedMsg); 
	        	        	
	        		        try {	        		        	
        		        		server.SendDataToAllClients(tmp_str);	        		        	
	        		        } catch (Exception e) {
	        		            e.printStackTrace();
	        		        }
	        	        }
        			}
        		}
        	}
        }
    }
}
