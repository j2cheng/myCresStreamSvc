package com.crestron.txrxservice.canvas;

import java.util.ArrayList;
import java.util.List;
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

public class ActionExecutor extends ThreadPoolExecutor
{
    public String TAG = "TxRx.canvas.action.executor"; 
    TimeSpan start;
	
    public ActionExecutor(String tag, int nThreads)
    {
    	super(nThreads, nThreads, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());
    	TAG = tag;
    }
	
	@Override 
	protected void beforeExecute(Thread thread, Runnable run)
	{
		super.beforeExecute(thread, run);
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
	
    public List<Runnable> shutdownNow()
    {
    	super.shutdown();
    	try {
    		super.awaitTermination(500, TimeUnit.MILLISECONDS);
    		return super.shutdownNow();
    	} catch (InterruptedException e) {
    		return super.shutdownNow();
    	}
    }
}
