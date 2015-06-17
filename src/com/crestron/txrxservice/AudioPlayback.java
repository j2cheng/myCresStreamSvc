package com.crestron.txrxservice;

import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.util.Log;

import java.nio.ByteBuffer;

public class AudioPlayback 
{
    public AudioManager mAudiomanager = null;

    AudioTrack mPlayer = null;
    AudioRecord mRecorder = null;
    String TAG = "TxRx AudioPlayback"; 
    volatile boolean shouldExit;
    Thread streamAudioThread;


    protected void startAudioTask(){
    	streamAudioThread = new Thread(new StreamAudioTask());
    	shouldExit = false;
    	streamAudioThread.start();
    }

    class StreamAudioTask implements Runnable {

        public void run() {
            ByteBuffer readBuffer;
            final int audioFormat = 2;//ENCODING_PCM_!^BIT
            final int audioChannels= 12;//CHANNEL_IN/OUT_STEREO
            final int numOfBuffers= 4;
            final int audioSource = 5; //Audio Source is CAMCORDER
            int bufferSize = 131072;
            int read = 0;
            int readSize = bufferSize;
            final int sampleRate = 48000;

            Log.d(TAG, "Streaming Audio task started.... ");
            try
            {
                bufferSize = AudioRecord.getMinBufferSize(sampleRate, audioChannels, audioFormat);
                readBuffer = ByteBuffer.allocate(bufferSize);
                mRecorder = new AudioRecord(audioSource, sampleRate, audioChannels, audioFormat, (numOfBuffers * bufferSize));
                mRecorder.startRecording();
                mPlayer = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, audioChannels, audioFormat, (numOfBuffers * bufferSize), AudioTrack.MODE_STREAM);
                mPlayer.play();
                readSize = bufferSize;
                while(!shouldExit)
                {
                    read = mRecorder.read(readBuffer.array(), 0, readSize);
                    if (read > 0)
                    {
                        mPlayer.write(readBuffer.array(), 0, read);
                        mPlayer.flush();
                    }
                }
            } catch (Exception localException) {
                Log.e(TAG, "Audio exception caught");
                localException.printStackTrace();
                try{
                    Thread.sleep(100L);
                }
                catch (InterruptedException localInterruptedException)
                {
                    localInterruptedException.printStackTrace();
                    mRecorder.stop();
                    mPlayer.stop();
                    mRecorder.release();
                    mPlayer.release();
                }
            }
        }
    }

    protected void stopAudioTask(){
        Log.e(TAG, "stop Audio started");
        shouldExit = true;
        try
        {
        	this.streamAudioThread.join();
       	
        	mRecorder.stop();
	        mPlayer.stop();
	        mRecorder.release();
	        mPlayer.release();
	        
	        Log.e(TAG, "Audio Task Stopped");
        }
        catch (InterruptedException localInterruptedException)
        {
            localInterruptedException.printStackTrace();
        }        
    }
}
