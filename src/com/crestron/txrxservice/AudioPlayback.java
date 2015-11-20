package com.crestron.txrxservice;

import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder.AudioSource;
import android.media.AudioFormat;
import android.util.Log;

import java.nio.ByteBuffer;

public class AudioPlayback 
{
    public AudioManager mAudiomanager = null;

    CresStreamCtrl mStreamCtl;
    AudioTrack mPlayer = null;
    AudioRecord mRecorder = null;
    String TAG = "TxRx AudioPlayback"; 
    volatile boolean shouldExit;
    Thread streamAudioThread;
    volatile boolean initVolumePending = false;
    int mWaitFrames = 15;
    final int mWaitFramesDone = 15;
    
    public AudioPlayback(CresStreamCtrl streamCtl) {
    	mStreamCtl = streamCtl;
    }

    protected void startAudioTask(){
    	streamAudioThread = new Thread(new StreamAudioTask());
    	shouldExit = false;
    	streamAudioThread.start();
    }

    class StreamAudioTask implements Runnable {

        public void run() {
            ByteBuffer readBuffer;
            final int audioFormat = AudioFormat.ENCODING_PCM_16BIT;//ENCODING_PCM_16BIT
            final int audioChannels= AudioFormat.CHANNEL_OUT_STEREO;//CHANNEL_IN/OUT_STEREO:Default Android Val is 12
            final int numOfBuffers= 4;
            final int audioSource = AudioSource.CAMCORDER; //Audio Source is CAMCORDER
            int bufferSize = 131072;
            int read = 0;
            int readSize = bufferSize;
            final int sampleRate = 44100;//48000;
            initVolumePending = true;

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
                    
                    if (Boolean.parseBoolean(mStreamCtl.hdmiOutput.getSyncStatus()) == false)
                    {
                    	mWaitFrames = 0; // reset mWaitFrames count
                    	if (mPlayer != null)
                    	{
                    		mPlayer.stop();                  		
                    		mPlayer.release();
                    		mPlayer = null;
                    	}
                    }
                    else
                    {
                    	if (mWaitFrames >= mWaitFramesDone)
                    	{
                    		if (mPlayer == null)
                    		{
                    			mPlayer = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, audioChannels, audioFormat, (numOfBuffers * bufferSize), AudioTrack.MODE_STREAM);
                                mPlayer.play();
                    		}
                    		
                    		// Write to audiotrack
                    		if (read > 0)
                            {
                            	if (!shouldExit) //write is time intensive function, skip if we are trying to stop
                            	{
        	                        mPlayer.write(readBuffer.array(), 0, read);
//        	                        mPlayer.flush();
        	                        if (initVolumePending)
        	                        {
        	                        	setVolume((int)mStreamCtl.userSettings.getVolume());
        	                        	initVolumePending = false;
        	                        }
                            	}
                            }
                    	}
                    	else
                    		mWaitFrames++;
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
    
    public void setVolume(int volume) {
    	if (mPlayer != null)
    	{
    		int audioSteps = 101; // 0-100
        	float newVolume = (float)(1 - (Math.log(audioSteps - volume)/Math.log(audioSteps)));
        	newVolume = newVolume * AudioTrack.getMaxVolume();
    		int ret = mPlayer.setStereoVolume(newVolume, newVolume);
			if (ret < 0) 
    			Log.e(TAG, "Could not change volume, error code = " + ret);
    	}
    }
}
