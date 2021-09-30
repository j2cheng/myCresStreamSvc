package com.crestron.txrxservice;

import android.media.MediaRecorder;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder.AudioSource;
import android.media.AudioFormat;
import android.util.Log;

import java.io.File;
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
	int audioSampleRate = 48000;
	int audioFormat = AudioFormat.ENCODING_PCM_16BIT;//ENCODING_PCM_16BIT
	int audioChannels = AudioFormat.CHANNEL_OUT_STEREO;//CHANNEL_IN/OUT_STEREO:Default Android Val is 12
	int audioBufferSize = 0;
	volatile boolean initVolumePending = false;
	int mWaitFrames = 15;
	final int mWaitFramesDone = 15;
	final static int maxNumOfBuffers = 3;	// This value was determined through manual testing

        final int mMaxBufFullError = 100;
        int   mBufFullErrorCnt = 0;

	public AudioPlayback(CresStreamCtrl streamCtl) {
		mStreamCtl = streamCtl;
	}

	protected boolean startAudioTask(){
		audioSampleRate = HDMIInputInterface.readAudioSampleRate();
		if (mStreamCtl.isAM3X00() && (audioSampleRate <= 0 || audioSampleRate > 48000))
		{
			Log.i(TAG, "startAudioTask(): not launching audio thread because have a bad sample rate: "+audioSampleRate);
			mStreamCtl.mPreviousAudioInputSampleRate = audioSampleRate;
			Log.i(TAG, "startAudioTask() - previous audio sample rate has changed and is now "+mStreamCtl.mPreviousAudioInputSampleRate);
			return false;
		}
		audioBufferSize = AudioRecord.getMinBufferSize(audioSampleRate, audioChannels, audioFormat);
		Log.i(TAG, "AudioPlayback(): audio buffer size "+audioBufferSize);
		if (audioBufferSize == AudioRecord.ERROR || audioBufferSize == AudioRecord.ERROR_BAD_VALUE) {
			Log.w(TAG, "Got a bad audio buffersize "+audioBufferSize+" from getMinBufferSize (sampleRate="+audioSampleRate+")");
			return false;
		}
		streamAudioThread = new Thread(new StreamAudioTask());
		shouldExit = false;
		streamAudioThread.start();
		return true;
	}

	class StreamAudioTask implements Runnable {
		private LinkedBlockingQueue<audioBufferQueueObject> audioBufferQueue;		
		private final int AudioFmt = audioFormat;//ENCODING_PCM_16BIT
		private final int AudioChannels= audioChannels;//CHANNEL_IN/OUT_STEREO:Default Android Val is 12
		private final int SampleRate = audioSampleRate;
		private int AudioSrc = AudioSource.CAMCORDER; //Audio Source is CAMCORDER for all products except AM3X
		private final int BufferSize = audioBufferSize;
		private StaticAudioBuffers mAudioBuffers = new StaticAudioBuffers(maxNumOfBuffers + 2);
        public final static String rebootAudioFullFile = "/dev/shm/crestron/CresStreamSvc/rebootedOnAudioBufferFull";

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

                    // 9-13-2021: detect this error up to mMaxBufFullError, then restart txrx
                    mBufFullErrorCnt++;
                    if (mBufFullErrorCnt > mMaxBufFullError) 
                    {
                        File rebootFile = new File(rebootAudioFullFile);
                        if (rebootFile.isFile()) 
                        {
                            Log.v(TAG, "Skip Restart txrx because too many audio errors!");
                            mBufFullErrorCnt = 0;
                        } 
                        else 
                        {
                            try {
                                rebootFile.createNewFile();
                            } catch (Exception e) {}

                            Log.i(TAG, "Creste file because too many audio errors!");
                            //System.exit(1);
                        }
                    } // else
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

			Log.i(TAG, "Streaming Audio task started.... SampleRate: " + SampleRate);
			try
			{
				audioBufferQueue = new LinkedBlockingQueue<audioBufferQueueObject>();
				audioTrackThread = new Thread(new ProcessBufferQueue(audioBufferQueue));
				audioTrackThread.start();

                mRecorder = new AudioRecord(AudioSrc, SampleRate, AudioChannels, AudioFmt, (2 * BufferSize)); // multiple times 2 because 2 byte per sample
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
                                Log.i(TAG, "Audio ProcessBufferQueue started");
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
												if(CameraPreview.is_hdmisession_muted)
												{
													setVolume(0);
													Log.i(TAG, "Audio at startup muted: " + CameraPreview.is_hdmisession_muted);
												}
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
