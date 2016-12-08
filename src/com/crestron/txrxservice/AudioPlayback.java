package com.crestron.txrxservice;

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
		private final int audioFormat = AudioFormat.ENCODING_PCM_16BIT;//ENCODING_PCM_16BIT
		private final int audioChannels= AudioFormat.CHANNEL_OUT_STEREO;//CHANNEL_IN/OUT_STEREO:Default Android Val is 12
		private final int sampleRate = HDMIInputInterface.readAudioSampleRate();
		private final int audioSource = AudioSource.CAMCORDER; //Audio Source is CAMCORDER
		private int bufferSize = AudioRecord.getMinBufferSize(sampleRate, audioChannels,audioFormat);

		private void addToAudioBufferQueue(audioBufferQueueObject newAudioBuffer)
		{
			synchronized (audioBufferQueue)
			{
				if (audioBufferQueue.size() >= maxNumOfBuffers)
				{
					// TODO: make sure this doesn't print too much in error condition
					Log.d(TAG, "Dropping audio buffers because audio buffer queue full!");
					audioBufferQueue.clear();
				}
				audioBufferQueue.add(newAudioBuffer);
				audioBufferQueue.notify();
			}
		}

		public void run() {
			initVolumePending = true;

			Log.d(TAG, "Streaming Audio task started.... ");
			try
			{
				audioBufferQueue = new LinkedBlockingQueue<audioBufferQueueObject>();        		
				audioTrackThread = new Thread(new ProcessBufferQueue(audioBufferQueue));
				audioTrackThread.start();

				mRecorder = new AudioRecord(audioSource, sampleRate, audioChannels, audioFormat, (2 * bufferSize)); // multiple times 2 because 2 byte per sample
				mRecorder.startRecording();
				mPlayer = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, audioChannels, audioFormat, (2 * bufferSize), AudioTrack.MODE_STREAM);
				mPlayer.play();
				while(!shouldExit)
				{
					int read = 0;
					ByteBuffer readBuffer = ByteBuffer.allocate(bufferSize);

					read = mRecorder.read(readBuffer.array(), 0, bufferSize);       

					addToAudioBufferQueue(new audioBufferQueueObject(readBuffer, read));
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
										mPlayer = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, audioChannels, audioFormat, (2 * bufferSize), AudioTrack.MODE_STREAM);
										mPlayer.play();
									}

									// Write to audiotrack
									if (currentBufferObject.bufferLen > 0)
									{
										if (!shouldExit) //write is time intensive function, skip if we are trying to stop
										{
											mPlayer.write(currentBufferObject.buffer.array(), 0, currentBufferObject.bufferLen);
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
					}
				}
			}
		}

		class audioBufferQueueObject
		{
			ByteBuffer buffer;
			int bufferLen;

			audioBufferQueueObject(ByteBuffer buf, int len)
			{
				buffer = buf;
				bufferLen = len;
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
			int audioSteps = 101; // 0-100
			float newVolume = (float)(1 - (Math.log(audioSteps - volume)/Math.log(audioSteps)));
			newVolume = newVolume * AudioTrack.getMaxVolume();
			int ret = mPlayer.setStereoVolume(newVolume, newVolume);
			if (ret < 0) 
				Log.e(TAG, "Could not change volume, error code = " + ret);
		}
	}
}
