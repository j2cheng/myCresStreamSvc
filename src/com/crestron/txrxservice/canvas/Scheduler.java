package com.crestron.txrxservice.canvas;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.crestron.airmedia.utilities.Common;
import com.crestron.airmedia.utilities.TimeSpan;

import android.util.Log;

public class Scheduler extends ThreadPoolExecutor
{
    public String TAG = "TxRx.canvas.scheduler"; 
    TimeSpan start;
    public long ownThreadId = 0;
    Future<?> currentFuture;
	Map<UUID, Future<?>> futureMap= new ConcurrentHashMap<UUID, Future<?>>();
	
	public class RunnableWithId implements Runnable
	{
		private UUID uuid;
		Runnable task;
		
		public RunnableWithId(UUID id, Runnable r)
		{
			uuid = id;
			task = r;
		}
		
		public void run() {
			try {
				currentFuture = futureMap.get(uuid);
				task.run();
				futureMap.remove(uuid);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public void cancel()
	{
		if (currentFuture != null)
			currentFuture.cancel(true);
		else
			Common.Logging.i(TAG, "null future - cannot cancel");
	}
	
    public Scheduler(String tag)
    {
    	super(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());
    	TAG = tag;
    }
    
    public boolean inOwnThread()
    {
    	return Thread.currentThread().getId() == ownThreadId;
    }
    
    // This is a synchronous method and will block calling thread to completion or timeout
	boolean queue(TimeSpan timeout, Runnable task) {
		Common.Logging.i(TAG, "task submitted with timeout= " + timeout.toString());
		TimeSpan start = TimeSpan.now();
		Future<?> future = submit(task);
		boolean isCompleted = false;
		try {
			future.get(timeout.milliseconds(), TimeUnit.MILLISECONDS);
			isCompleted = true;
		} catch (CancellationException e) {
			Common.Logging.e(TAG, "  timeout= " + timeout.toString() + "  EXCEPTION  " + e + "  " + Log.getStackTraceString(e));
		} catch (ExecutionException e) {
			Common.Logging.e(TAG, "  timeout= " + timeout.toString() + "  EXCEPTION  " + e + "  " + Log.getStackTraceString(e));
		} catch (InterruptedException e) {
			Common.Logging.e(TAG, "  timeout= " + timeout.toString() + "  EXCEPTION  " + e + "  " + Log.getStackTraceString(e));
		} catch (TimeoutException e) {
			Common.Logging.e(TAG, "  timeout= " + timeout.toString() + "  EXCEPTION  " + e + "  " + Log.getStackTraceString(e));
		} finally {
			Common.Logging.i(TAG, "  timeout= " + timeout.toString() + "  COMPLETE= " + isCompleted + "  " + TimeSpan.getDelta(start));
		}
		return isCompleted;
	}
	
    // This is a asynchronous method and will run on the thread of the scheduler but should have an internal timeout
	// otherwise if it hangs, so does the scheduler
	void queue(Runnable task) 
	{
		Common.Logging.i(TAG, "Pending tasks in scheduler queue "+getQueue().size());
		UUID uuid = UUID.randomUUID();
		RunnableWithId r = new RunnableWithId(uuid, task);
		Future<?> future = submit(r);
		futureMap.put(uuid, future);
	}
	
    // This is a synchronous method.  It will run on the thread of the scheduler but should have an internal timeout
	// because the caller is going to be blocked
	Boolean queue(Callable<Boolean> task) 
	{
		Common.Logging.i(TAG, "Pending tasks in scheduler queue "+getQueue().size());
		Boolean timeout = false;
		Future<Boolean> f = submit(task);
		try {
			timeout = f.get();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return timeout;
	}
	
	@Override 
	protected void beforeExecute(Thread thread, Runnable run)
	{
		super.beforeExecute(thread, run);
		ownThreadId = thread.getId();
		start = TimeSpan.now();
	}
	
	protected void afterExecute(Runnable r, Throwable e)
	{
		super.afterExecute(r, e);
		if (e == null)
		{
			Common.Logging.i(TAG, " COMPLETED in "+ TimeSpan.getDelta(start) + " Thread=" + Thread.currentThread().getName());
		}
		else
		{
			Common.Logging.e(TAG, " EXCEPTION  " + e + "  " + Log.getStackTraceString(e));
		}
	}
}
