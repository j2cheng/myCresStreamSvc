package com.crestron.txrxservice;

import android.media.MediaRecorder;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder.AudioSource;
import android.media.AudioFormat;
import android.util.Log;

import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.crestron.txrxservice.TCPInterface.JoinObject;
import com.crestron.txrxservice.TCPInterface.ProcessJoinTask;

public class AudioPlayback 
{
	public AudioManager mAudiomanager = null;

	CresStreamCtrl mStreamCtl;
	AudioTrack mPlayer = null;
	AudioRecord mRecorder = null;
	String TAG = "TxRx AudioPlayback"; 
	volatile boolean shouldExit;
	Thread streamAudioThread;
	Thread audioTrackThread;
	volatile boolean initVolumePending = false;
	int mWaitFrames = 15;
	final int mWaitFramesDone = 15;
	final static int maxNumOfBuffers = 3;	// This value was determined through manual testing

	public AudioPlayback(CresStreamCtrl streamCtl) {
		mStreamCtl = streamCtl;
	}

	protected void startAudioTask(){
		streamAudioThread = new Thread(new StreamAudioTask());
		shouldExit = false;
		streamAudioThread.start();
	}

	class StreamAudioTask implements Runnable {
		private LinkedBlockingQueue<audioBufferQueueObject> audioBufferQueue;		
		private final int AudioFmt = AudioFormat.ENCODING_PCM_16BIT;//ENCODING_PCM_16BIT
		private final int AudioChannels= AudioFormat.CHANNEL_OUT_STEREO;//CHANNEL_IN/OUT_STEREO:Default Android Val is 12
		private final int SampleRate = HDMIInputInterface.readAudioSampleRate();
		private final int AudioSrc = AudioSource.CAMCORDER; //Audio Source is CAMCORDER
		private final int BufferSize = AudioRecord.getMinBufferSize(SampleRate, AudioChannels, AudioFmt);
		private StaticAudioBuffers mAudioBuffers = new StaticAudioBuffers(maxNumOfBuffers + 2);
		
		private void addToAudioBufferQueue(audioBufferQueueObject newAudioBuffer)
		{
			synchronized (audioBufferQueue)
			{
				if (audioBufferQueue.size() >= maxNumOfBuffers)
				{
					// TODO: make sure this doesn't print too much in error condition
					Log.i(TAG, "Dropping audio buffers because audio buffer queue full!");
					audioBufferQueue.clear();
					mAudioBuffers.clearBuffers();
				}
				audioBufferQueue.add(newAudioBuffer);
				audioBufferQueue.notify();
			}
		}

		public void run() {
			initVolumePending = true;
			
			try { 
        		if (mStreamCtl.audioReadyLatch.await(240, TimeUnit.SECONDS) == false)
        		{
        			Log.e(TAG, "Audio ready: timeout after 240 seconds");
        			mStreamCtl.RecoverTxrxService();
        		}
        	}
        	catch (InterruptedException ex) { ex.printStackTrace(); }  

			Log.i(TAG, "Streaming Audio task started.... ");
			try
			{
				audioBufferQueue = new LinkedBlockingQueue<audioBufferQueueObject>();        		
				audioTrackThread = new Thread(new ProcessBufferQueue(audioBufferQueue));
				audioTrackThread.start();

                if(!mStreamCtl.isAM3X00())
                    mRecorder = new AudioRecord(AudioSrc, SampleRate, AudioChannels, AudioFmt, (2 * BufferSize)); // multiple times 2 because 2 byte per sample
                else
                    mRecorder = new AudioRecord(MediaRecorder.AudioSource.MIC, SampleRate, AudioChannels, AudioFmt, (2 * BufferSize));

				mRecorder.startRecording();
				mPlayer = new AudioTrack(AudioManager.STREAM_MUSIC, SampleRate, AudioChannels, AudioFmt, (2 * BufferSize), AudioTrack.MODE_STREAM);
				mPlayer.play();
				while(!shouldExit)
				{
					int read = 0;
					
					int index = mAudioBuffers.obtainBuffer();
					read = mRecorder.read(mAudioBuffers.staticBuffer.array(), (index * BufferSize), BufferSize);       

					addToAudioBufferQueue(new audioBufferQueueObject(index, read));
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

		class ProcessBufferQueue implements Runnable {
			private Queue<audioBufferQueueObject> bufferQueue;

			public ProcessBufferQueue(Queue<audioBufferQueueObject> buffer_queue)
			{
				bufferQueue = buffer_queue;
			}

			public void run() {
				while (!shouldExit) {
					if (bufferQueue.isEmpty())
					{
						try {
							synchronized (bufferQueue)
							{
								bufferQueue.wait(5000);
							}
						} catch (Exception e) {e.printStackTrace();}
					}
					else //process queue
					{         
						audioBufferQueueObject currentBufferObject = bufferQueue.poll();
						if (currentBufferObject != null)
						{
							if (Boolean.parseBoolean(mStreamCtl.hdmiOutput.getSyncStatus()) == false)
							{
								mWaitFrames = 0; // reset mWaitFrames count
								if (mPlayer != null)
								{
									initVolumePending = true;
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
										mPlayer = new AudioTrack(AudioManager.STREAM_MUSIC, SampleRate, AudioChannels, AudioFmt, (2 * BufferSize), AudioTrack.MODE_STREAM);
										mPlayer.play();
									}

									// Write to audiotrack
									if (currentBufferObject.length > 0)
									{
										if (!shouldExit) //write is time intensive function, skip if we are trying to stop
										{
											mPlayer.write(mAudioBuffers.staticBuffer.array(), (currentBufferObject.index * BufferSize), currentBufferObject.length);
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
							mAudioBuffers.releaseBuffer(currentBufferObject.index);	// Even if audio is not sent to audiotrack mark as consumed
						}
					}
				}
			}
		}
		
		class StaticAudioBuffers {
			public ByteBuffer staticBuffer;
			private char[] bufferUsed;
			private Object lock = new Object();
			
			public StaticAudioBuffers(int numOfBuffers)
			{
				staticBuffer = ByteBuffer.allocate(BufferSize * numOfBuffers);
				bufferUsed = new char[numOfBuffers];
				for (int i = 0; i < bufferUsed.length; i++)
				{
					bufferUsed[i] = 0;
				}
			}
			
			public int obtainBuffer()
			{
				synchronized(lock)
				{
					int availableIndex = -1;
					for (int i = 0; i < bufferUsed.length; i++)
					{
						if (bufferUsed[i] == 0)
						{
							availableIndex = i;
							break;
						}
					}
					if (availableIndex == -1)
					{
						// This should never happen because buffers will get dropped before we hit 2 over maxBuffers
						Log.e(TAG, "No available audio buffers!");
						availableIndex = 0;
					}

					bufferUsed[availableIndex] = 1;

					return availableIndex;
				}
			}
			
			public void releaseBuffer(int index)
			{
				synchronized(lock)
				{
					bufferUsed[index] = 0;
				}
			}
			
			public void clearBuffers()
			{
				synchronized(lock)
				{
					for (int i = 0; i < bufferUsed.length; i++)
					{
						bufferUsed[i] = 0;
					}
				}
			}
		}

		class audioBufferQueueObject
		{
			int index;
			int length;

			audioBufferQueueObject(int idx, int len)
			{
				index = idx;
				length = len;
			}
		}
	}

	protected void stopAudioTask(){
		Log.e(TAG, "stop Audio started");
		shouldExit = true;
		try
		{
			this.audioTrackThread.join();
			this.streamAudioThread.join();       	
		} catch (Exception ex) { ex.printStackTrace(); }           

		try
		{
			mRecorder.stop();
			mRecorder.release();
		} catch (Exception ex) { ex.printStackTrace(); }

		try
		{
			mPlayer.stop();	        
			mPlayer.release();
		} catch (Exception ex) { ex.printStackTrace(); }  

		Log.e(TAG, "Audio Task Stopped");     
	}

	public void setVolume(int volume) {
		if (mPlayer != null)
		{
			try {
				int audioSteps = 101; // 0-100
				float newVolume = (float)(1 - (Math.log(audioSteps - volume)/Math.log(audioSteps)));
				newVolume = newVolume * AudioTrack.getMaxVolume();
				int ret = mPlayer.setStereoVolume(newVolume, newVolume);
				if (ret < 0) 
					Log.e(TAG, "Could not change volume to "+volume+", error code = " + ret);
			} catch (IllegalStateException e)
			{
				Log.e(TAG, "Error when attempting to change volume to "+volume);
			}
		}
	}
}
