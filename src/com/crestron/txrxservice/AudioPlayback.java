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
	boolean shouldExit = false;
	

	protected void startAudioTask(){
		 new Thread(new StreamAudioTask()).start();
	}

	class StreamAudioTask implements Runnable {

		@Override
			public void run() {
				ByteBuffer readBuffer;
				int audioFormat = 2;
				int bufferSize = 131072;
				int read = 0;
				int readSize = bufferSize;
				int sampleRate = 48000;

				Log.d(TAG, "Streaming Audio task started.... ");
				try
				{
					bufferSize = AudioRecord.getMinBufferSize(sampleRate, 3, 2);
					readBuffer = ByteBuffer.allocate(bufferSize);
					mRecorder = new AudioRecord(5, sampleRate, 3, audioFormat, 4 * bufferSize);
					mRecorder.startRecording();
					mPlayer = new AudioTrack(3, sampleRate, 3, audioFormat, 4 * bufferSize, 1);
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
		mRecorder.stop();
		mPlayer.stop();
		mRecorder.release();
		mPlayer.release();
		Log.e(TAG, "Audio Task Stopped");
		shouldExit = false;
	}
}
