package com.crestron.txrxservice.canvas;

import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.crestron.airmedia.utilities.Common;
import com.crestron.airmedia.utilities.TimeSpan;

import android.util.Log;

public class PriorityScheduler
{
    public String TAG = "TxRx.canvas.priorityScheduler"; 
    TimeSpan start;
    public long ownThreadId = 0;
    Scheduler scheduler;
	private PriorityBlockingQueue<PriorityRunnable> priorityQueue;
	private boolean shutDown = false;
	public final static int HIGH_PRIORITY=0;
	public final static int NORMAL_PRIORITY=1;
	public final static int LOW_PRIORITY=2;

	public class PrioritySchedulerComparator implements Comparator<PriorityRunnable>
	{
		public int compare(PriorityRunnable r1, PriorityRunnable r2)
		{
			if (r1.priority != r2.priority)
				return r1.priority - r2.priority;
			else {
				long delta = r1.insertionTime - r2.insertionTime;
				if (delta < 0)
					return -1;
				else if (delta > 0)
					return 1;
				else
					return 0;
			}
		}
	}
	
	public class PriorityRunnable
	{
		public int priority;
		public long insertionTime;
		Runnable task;
		CountDownLatch latch;
		
		public PriorityRunnable(Runnable r, int priority, long insertionTime)
		{
			this.priority = priority;
			this.insertionTime = insertionTime;
			this.task = r;
			this.latch = null;
		}
		
		public PriorityRunnable(Runnable r, int priority, CountDownLatch latch, long insertionTime)
		{
			this.priority = priority;
			this.insertionTime = insertionTime;
			this.task = r;
			this.latch = latch;
		}
	}

    public PriorityScheduler(String tag)
    {
    	scheduler = new Scheduler();
    	TAG = tag;
    	priorityQueue = new PriorityBlockingQueue<PriorityRunnable>(21, new PrioritySchedulerComparator());
    	Thread executeThread = new Thread(new Runnable() { @Override public void run() { execute(); } } );
    	executeThread.start();
    }
    
    public boolean inOwnThread()
    {
    	return Thread.currentThread().getId() == ownThreadId;
    }
	
    public void execute()
    {
    	PriorityRunnable pr = null;
    	while (!shutDown) {
    		try {
    			pr = priorityQueue.take();
    			Future<?> future = scheduler.submit(pr.task);
    			future.get();
    		} catch (InterruptedException e) {
				Common.Logging.e(TAG, "  EXCEPTION  " + e + "  " + Log.getStackTraceString(e));
    		} catch (ExecutionException e) {
				Common.Logging.e(TAG, "  EXCEPTION  " + e + "  " + Log.getStackTraceString(e));
    		}
			if (pr != null && pr.latch != null)
			{
				pr.latch.countDown();
				pr.latch = null;
			}
    	}
		Common.Logging.i(TAG, "exit from execute thread");
    }
    
    public void shutdownNow()
    {
    	shutDown = true;
    	scheduler.shutdown();
    	try {
    		scheduler.awaitTermination(500, TimeUnit.MILLISECONDS);
    		scheduler.shutdownNow();
    	} catch (InterruptedException e) {
    		scheduler.shutdownNow();
    	}
    	priorityQueue.clear();
		Common.Logging.i(TAG, "shutdown completed");
    }
    
    // This is a asynchronous method and will run on the thread of the scheduler but should have an internal timeout
	// otherwise if it hangs, so does the scheduler
	void queue(Runnable task, int priority) 
	{
		long insertionTime = System.nanoTime();
		priorityQueue.add(new PriorityRunnable(task, priority, insertionTime));
		Common.Logging.i(TAG, "Pending tasks in queue: "+priorityQueue.size());
	}
	
	public void execute(Runnable task, int priority) 
	{
		CountDownLatch latch = new CountDownLatch(1);
		long insertionTime = System.nanoTime();
		priorityQueue.add(new PriorityRunnable(task, priority, latch, insertionTime));
		Common.Logging.i(TAG, "Pending tasks in queue: "+priorityQueue.size());
        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
	}
	
	public class Scheduler extends ThreadPoolExecutor
	{
		public Scheduler()
		{
	    	super(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());
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
}
