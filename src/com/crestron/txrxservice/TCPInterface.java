package com.crestron.txrxservice;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
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
import java.net.SocketException;
import java.io.InputStream;

import com.crestron.txrxservice.CresStreamCtrl.StreamState;

import android.os.AsyncTask;
import android.util.Log;
import android.os.Bundle;
import android.content.Context;

public class TCPInterface extends AsyncTask<Void, Object, Long> {
    String TAG = "TxRx TCPInterface";
    public static final String CONSOLEPROMPT = "\r\nTxRx>";
    CommandParser parserInstance;
    boolean isWhiteSpace = false;
    private CresStreamCtrl streamCtl;

    public static final int SERVERPORT = 9876;
    public static final String LOCALHOST = "127.0.0.1";
    private ServerSocket serverSocket;
    private volatile boolean restartStreamsPending = true;
    public volatile boolean firstRun = true;
    private final Object serverLock = new Object();
    private boolean[] isProcessingMode = new boolean[CresStreamCtrl.NumOfSurfaces];
    
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
        
        for (int i = 0; i < CresStreamCtrl.NumOfSurfaces; ++i)
        {
            isProcessingMode[i] = false;
        }
        
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
        ArrayList<CommunicationThread> errorThreads = new ArrayList<TCPInterface.CommunicationThread>();

        synchronized(serverLock)
        {
            // First make sure that thread exists and then send out the data
            for (ListIterator<CommunicationThread> iter = clientList.listIterator(clientList.size()); iter.hasPrevious();)
            {
                CommunicationThread thread = iter.previous();
                if (thread != null)
                {
                    if (thread.SendDataToClient(data) != 0)
                    {
                        errorThreads.add(thread);
                    }
                }
            }

            for (ListIterator<CommunicationThread> iter = errorThreads.listIterator(errorThreads.size()); iter.hasPrevious();)
            {
                // Remove all clients that had an error
                CommunicationThread thread = iter.previous();
                if (thread.clientSocket != null)
                {
                    closeClientSocket(thread.clientSocket);
                    thread.connectionAlive = false;
                }
                thread.serverHandler.RemoveClientFromList(thread);
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
        
        Log.i(TAG, "Restarting Streams - tcp interface command");
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
            InputStream iStream = streamCtl.getResources().openRawResource(R.raw.rc);

            BufferedReader br = new BufferedReader(new InputStreamReader(iStream));  
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
                serverSocket = new ServerSocket(SERVERPORT, 50, InetAddress.getByName("0.0.0.0"));
                Log.i(TAG, "Allowing all tcp connections to debug port at 0.0.0.0");
            }
            else
                serverSocket = new ServerSocket(SERVERPORT, 50, InetAddress.getByName(allowedAddress));
        } catch (IOException e) {
            e.printStackTrace();
        }
        while (!Thread.currentThread().isInterrupted()) {

            try {
                clientSocket = serverSocket.accept();
                Log.i(TAG, "Client connected to clientSocket: " + clientSocket.toString());
                try
                {
                    clientSocket.setTcpNoDelay(true);
                } 
                catch (SocketException ex)
                {
                    Log.e(TAG, "Error disabling nagle: " + ex);
                }
                
                CommunicationThread commThread = new CommunicationThread(clientSocket, this);
                commThread.connectionAlive = true; //New Client Connected
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
                        int hdmiInEnum = HDMIInputInterface.readResolutionEnum(true);
                        streamCtl.setCamera(hdmiInEnum); //no need to restart streams
                    }

                    streamCtl.streamPlay.initUnixSocketState();
                }
                
                // Mark csio as connected
                streamCtl.csioConnected = true;
                MiscUtils.writeStringToDisk((new File(streamCtl.getFilesDir(), CresStreamCtrl.initializeSettingsFilePath)).getAbsolutePath(), "1");
                Log.i(TAG, "CSIO connected to CresStreamSvc via TCP Interface");
                
                // Update csio on hdmi input status
                if (streamCtl.hdmiInputDriverPresent)
                    SendDataToAllClients(MiscUtils.stringFormat("HDMIInputConnectedState=%s", streamCtl.hdmiInput.getSyncStatus())); //true means hdmi input connected

                streamCtl.pushWcStatusUpdate();
                streamCtl.sendPeripheralVolumeStatus();

                // Update csio on current service mode
                Log.i(TAG, "Sending present service mode to csio: " + MiscUtils.stringFormat("SERVICEMODE=%d", streamCtl.serviceMode.ordinal()));
                SendDataToAllClients(MiscUtils.stringFormat("SERVICEMODE=%d", streamCtl.serviceMode.ordinal()));
                if (streamCtl.airMediav21)
                {
                    //Update here becasue we did not add NUMBER_OF_PRESENTERS to the CommandParser.CmdTable.
                    SendDataToAllClients(MiscUtils.stringFormat("NUMBER_OF_PRESENTERS=%d", streamCtl.mCanvas.mSessionMgr.mNumOfPresenters));
                }
                SendDataToAllClients(MiscUtils.stringFormat("AIRMEDIA_WC_RESET_USB_ON_STOP=%d", (streamCtl.userSettings.getAirMediaResetUsbOnStop() ? 1 : 0)));
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
                                closeClientSocket(thread.clientSocket);
                            }
                            iter.remove();
                        }
                    }
                    Log.i(TAG, "closed down the server socket" );
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
        private boolean connectionAlive = false;
        private long lastRead = 0;        
        
        public CommunicationThread(Socket clientSocket, TCPInterface server) {

            this.clientSocket = clientSocket;
            this.serverHandler = server;

            try {
                this.clientSocket.setSoTimeout(20000);	// Heartbeat should come every 15 seconds at least
                input = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                out = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));

            } catch (IOException e) {
                e.printStackTrace();
                //  Was client socket closed - Clean up
                Log.i(TAG, "Client cleanup ");
                serverHandler.RemoveClientFromList(this);
            }
        }
        
        /***
         * Lock the send and send the data to the client
         * @param data
         */
        public int SendDataToClient(String data)
        {
            int retVal = 0;

            synchronized(out)
            {
                try 
                {
                    // Append the Prompt to signal to the client that a transaction has been completed
                    out.write(data + CONSOLEPROMPT);
                    out.flush();
                    retVal = 0;
                } 
                catch (IOException e) 
                {
                    e.printStackTrace();
                    Log.i(TAG, "Error sending data to client.  Cleaning up");
//                    serverHandler.RemoveClientFromList(this);
                    retVal = -1;
                }
                return retVal;
            }
        }

        public void run() {
            while (connectionAlive) {
                long currentRead = 0;
                if ((streamCtl.restartStreamsOnStart == true) && (restartStreamsPending   == true))
                    restartStreams(serverHandler);
                try {
                    String read = input.readLine();
                    currentRead = System.nanoTime();
                    if (lastRead == 0)
                        lastRead = currentRead;
                    if(read!=null && !(isWhiteSpace=(read.matches("^\\s*$"))))
                    {
                        read = read.trim();
                        if (!read.startsWith("*"))
                        {
                            Log.i(TAG, "msg received is "+read);
                        } else {
                            read = read.substring(1); // strip of '*'
                        }
                        if(read.equalsIgnoreCase("help")){
                            String validatedMsg = parserInstance.validateReceivedMessage(read);
                            serverHandler.SendDataToAllClients(validatedMsg);
                        }
                        else if (read.equalsIgnoreCase("updaterequest")) {
                        	Log.v(TAG, "--updaterequest being processed--");
                            for(CommandParser.CmdTable ct: CommandParser.CmdTable.values()){
                                // Send device ready as last join
                                if (!ct.name().equals("DEVICE_READY_FB"))
                                    addJoinToQueue(new JoinObject(ct.name(), serverHandler), 0); //Currently updaterequest will only be handled for sessionId 0
                            }

                            // Tell CSIO that update request is complete
                            addJoinToQueue(new JoinObject("DEVICE_READY_FB", serverHandler), 0);
                        	Log.v(TAG, "--updaterequest completed--");
                        }
                        else if(read.equalsIgnoreCase("RESTART_STREAM_ON_START=TRUE")){
                            Log.i(TAG, "RESTART_STREAM_ON_START=TRUE received");
                            streamCtl.restartStreamsOnStart = true;
                        }
                        else if (read.equalsIgnoreCase("RESTART_STREAM_ON_START=FALSE")) {
                            streamCtl.restartStreamsOnStart = false;
                        }
                        else if (read.equalsIgnoreCase("DEBUG_MODE")) {
                            // Remove timeout for debugging
                            clientSocket.setSoTimeout(0);
                            Log.i(TAG, "Turning telnet debug mode on");
                        }
                        else{
                            // determine sessionId first so we can add to the right queue
                            StringTokenizer.ParseResponse parseResponse = new StringTokenizer().Parse(read);
                            addJoinToQueue(new JoinObject(read, serverHandler), parseResponse.sessId);
                        }
                    }
                    else if(read == null) {
                        Log.i(TAG, "Client Disconnected..... ");
                        connectionAlive = false;
                    }
                    else{//white space or NULL Commands
                        serverHandler.SendDataToAllClients("");
                    }
                } catch (java.net.SocketTimeoutException e)
                {
                    if (Math.abs(currentRead - lastRead) >= (20 * 1000000000))
                    {
                        Log.w(TAG, "Failed to receive heartbeat, restarting internal connection");
                        connectionAlive = false;
                    }
                    else
                        Log.v(TAG, "Spurrious timeout ignored with time delta " + (Math.abs(currentRead - lastRead)) + " ns");
                } catch (IOException e) {
                    e.printStackTrace();
                    connectionAlive = false;
                }
                //reset timeout
                lastRead = currentRead;
            }
            
            // Cleanup when connection goes to false
            closeClientSocket(clientSocket);
            serverHandler.RemoveClientFromList(this);
        }
    }
    
    private void closeClientSocket(Socket socket)
    {
        try {socket.shutdownInput();} catch(Exception ex) {}
        try {socket.shutdownOutput();} catch(Exception ex) {}
        try {socket.close();} catch(Exception ex) {}
    }

    protected void onProgressUpdate(Object... progress) { 
        //TODO: can we remove this method??
        //Intentionally left blank
    }
    
    private void addJoinToQueue(JoinObject newJoin, int sessionId) {
        if ((sessionId >= 0) && (sessionId < CresStreamCtrl.NumOfSurfaces))
        {
            // Mode change needs to be handled in session 0 as well (plus all joins after until mode completes)
            if (newJoin.joinString.toLowerCase().startsWith("mode"))
            {
                isProcessingMode[sessionId] = true;
            }

            if ( (streamCtl.userSettings.getMode(sessionId) == CresStreamCtrl.DeviceMode.PREVIEW.ordinal()) ||
                    (streamCtl.userSettings.getMode(sessionId) == CresStreamCtrl.DeviceMode.STREAM_OUT.ordinal()) ||
                    isProcessingMode[sessionId] )
            {
                sessionId = 0;	// Fix bug where camera modes could be handled out of order such that device ended in wrong state
            }

            synchronized (joinQueue[sessionId])
            {
                joinQueue[sessionId].add(newJoin);
                joinQueue[sessionId].notify();
            }
        }
        else
            Log.w(TAG, "Invalid stream ID "+ sessionId);
    }
    
    class ProcessJoinTask implements Runnable {
        private Queue<JoinObject> jQ;
        private boolean signalProcessingModeComplete = false;
        private int processingModeSessionId = -1;

        public ProcessJoinTask(Queue<JoinObject> join_queue)
        {
            jQ = join_queue;
        }

        public void run() {
            while (!shouldExit) {
                if (jQ.isEmpty())
                {
                    // wait until join queue is empty before signaling that processing mode is complete
                    if (signalProcessingModeComplete) {
                        if (processingModeSessionId != -1)
                            isProcessingMode[processingModeSessionId] = false;
                        processingModeSessionId = -1;
                        signalProcessingModeComplete = false;
                    }
                    try {
                        synchronized (jQ)
                        {
                            jQ.wait(5000);
                        }
                    } catch (Exception e) {e.printStackTrace();}
                }
                else //process queue
                {
                    // Wait until CresStreamCtl signals that is ok to start processing join queue
                    try {
                        streamCtl.streamingReadyLatch.await();
                    } catch (InterruptedException e) { e.printStackTrace();	}

                    JoinObject currentJoinObject = jQ.poll();
                    if (currentJoinObject != null)
                    {
                        final String receivedMsg = currentJoinObject.joinString;
                        final TCPInterface server = currentJoinObject.serverHandler;

                        if (receivedMsg != null)
                        {
                            final CountDownLatch latch = new CountDownLatch(1);

                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    String tmp_str = parserInstance.processReceivedMessage(receivedMsg);

                                    if (receivedMsg.toLowerCase().startsWith("mode"))	// finished processing mode
                                    {
                                        StringTokenizer.ParseResponse parseResponse = new StringTokenizer().Parse(receivedMsg);
                                        signalProcessingModeComplete = true;
                                        processingModeSessionId = parseResponse.sessId;
                                    }

                                    try {
                                        server.SendDataToAllClients(tmp_str);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }

                                    latch.countDown();
                                }
                            }).start();

                            try {
                                if (latch.await(30, TimeUnit.SECONDS) == false)
                                {
                                    Log.e(TAG, "ProcessJoinTask: timeout after 30 seconds - last received message="+receivedMsg);
                                    streamCtl.RecoverTxrxService();
                                }
                            }
                            catch (InterruptedException ex) { ex.printStackTrace(); }
                        }
                    }
                }
            }
        }
    }
}
