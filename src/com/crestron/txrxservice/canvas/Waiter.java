package com.crestron.txrxservice.canvas;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.crestron.airmedia.utilities.TimeSpan;

public class Waiter
{
	private CountDownLatch latch = null;
	
	public synchronized void prepForWait()
	{
		latch = new CountDownLatch(1);
	}
	
	public boolean waitForSignal(TimeSpan timeout)
	{
	    boolean retVal=true;
		try 
		{
			retVal = latch.await(TimeSpan.toLong(timeout.totalMicroseconds()), TimeUnit.MICROSECONDS);
		} catch (InterruptedException ex) {};
		latch = null;
		return !retVal; // true returned on timeout
	}
	
	// returns true if latch needed to be counted down else return false
	public synchronized boolean signal()
	{
		boolean retVal = false;
		if (latch != null)
		{
			if (latch.getCount() == 1)
				retVal = true;
			latch.countDown();
		}
		return retVal;
	}
}
