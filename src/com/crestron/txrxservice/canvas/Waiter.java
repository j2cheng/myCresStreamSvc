package com.crestron.txrxservice.canvas;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.crestron.airmedia.utilities.TimeSpan;

public class Waiter
{
	private CountDownLatch latch = null;
	private boolean is_waiting = false;
	
	public void prepForWait()
	{
		if (latch == null || (latch.getCount() != 1))
		{
			latch = new CountDownLatch(1);
		}
	}
	
	public boolean isWaiting()
	{
		return is_waiting;
	}
	
	public boolean waitForSignal(TimeSpan timeout)
	{
	    boolean retVal=true;
		if (latch == null)
		{
			latch = new CountDownLatch(1);
		}
		try 
		{
			is_waiting = true;
			retVal = latch.await(TimeSpan.toLong(timeout.totalMicroseconds()), TimeUnit.MICROSECONDS);
		} catch (InterruptedException ex) {};
		return !retVal; // true returned on timeout
	}
	
	public void signal()
	{
		if (latch != null)
		{
			is_waiting = false;
			latch.countDown();
		}
	}
}
