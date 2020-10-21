package com.crestron.txrxservice.canvas;

import com.crestron.airmedia.canvas.channels.ipc.CanvasPlatformType;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSize;
import com.crestron.airmedia.utilities.Common;
import com.crestron.airmedia.utilities.TimeSpan;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.ConditionVariable;
import android.util.Log;
import android.view.Surface;

public class DMSession extends Session
{
    public static final String TAG = "TxRx.canvas.DM.session"; 
    private Surface surface = null;
    public int left, top, width, height;

	public DMSession(int inputNumber) {
		super(); // will assign id;
		state = SessionState.Connecting;
		type = SessionType.DM;
		airMediaType = null;
		this.inputNumber = inputNumber;
		userLabel = "DM-"+String.valueOf(inputNumber);
		platform = CanvasPlatformType.Hardware;
		left = top = width = height = 0;
	}
	
	public String toString()
	{
		return ("Session: "+type.toString()+"-"+inputNumber+"  sessionId="+sessionId());
	}
	
	public void doStop()
	{
		Common.Logging.i(TAG, "DM Session "+this+" stop request");
		if (streamId >= 0)
		{
			Common.Logging.i(TAG, "DM Session "+this+" sending stop to csio");
			mStreamCtl.sendDmStart(streamId, false);
			releaseSurface();
		}
	}
	
	public void stop(final Originator originator, int timeoutInSeconds)
	{
		Runnable r = new Runnable() { public void run() { doStop(); } };
        TimeSpan start = TimeSpan.now();
		boolean completed = executeWithTimeout(r, TimeSpan.fromSeconds(timeoutInSeconds));
		Common.Logging.i(TAG, "DM Session stop completed in "+TimeSpan.now().subtract(start).toString()+" seconds");
		if (completed) {
			streamId = -1;
			left = top = width = height = 0;
			setState(SessionState.Stopped);
		} else {
			Common.Logging.w(TAG, "DM Session "+this+" stop failed");		
			originator.failedSessionList.add(this);
		}
	}
	
	public void stop(Originator originator)
	{
		stop(originator, 10);
	}
	
	public void draw(Canvas canvas, int r, int g, int b)
	{
		canvas.drawRGB(r, g, b);
	}
	
	public synchronized void drawColor(int r, int g, int b)
	{
		Common.Logging.i(TAG, "drawColor(): Painting DM surface surface="+surface+"  r="+r+" g="+g+" b="+b);
        Canvas canvas = null;
        try {
            canvas = surface.lockCanvas(null);
    		Common.Logging.i(TAG, "drawColor(): canvas width="+canvas.getWidth()+" height="+canvas.getHeight());
            synchronized(surface) {
                draw(canvas, r, g, b);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (canvas != null) {
                surface.unlockCanvasAndPost(canvas);
            }
        }
	}
	
	public synchronized void drawChromaKeyColor(boolean blank)
	{
		Common.Logging.i(TAG, "Painting DM surface "+((blank)?"Blank":"ChromaKey")+" color");
        int r = (blank) ? 0xff : 0x09;
        int g = (blank) ? 0x00 : 0x00;
        int b = (blank) ? 0x00 : 0x09;
		drawColor(r, g, b);
	}
	
	public void drawChromaKeyColor()
	{
		drawChromaKeyColor(mStreamCtl.userSettings.getDmHdcpBlank(inputNumber));
	}
	
	public void doPlay(final Originator originator)
	{		
		Common.Logging.i(TAG, "DM Session "+this+" play request");
		setStreamId();
		Common.Logging.i(TAG, "DM Session "+this+" got streamId "+streamId);
		surface = acquireSurface();
		if (surface != null)
		{
			Common.Logging.i(TAG, "DM Session "+this+" sending start to csio");
			// signal to csio to start audio for DM via audiomux
			mStreamCtl.sendDmStart(streamId, true);
			audioMute(isAudioMuted);
	        if (!CresCanvas.useCanvasSurfaces) {
	        	mStreamCtl.setFormat(streamId, PixelFormat.RGBA_8888);
	        }
			Common.Logging.i(TAG, "DM Session "+this+" drawing to surface: "+surface);
			drawChromaKeyColor();
			if (!CresCanvas.useCanvasSurfaces)
			{
				Rect window = getWindow(surface);
				mStreamCtl.sendDmWindow(streamId, window.left, window.top, window.width(), window.height());
			}
		}
		else
		{
			Common.Logging.w(TAG, "DM Session "+this+" doPlay() got null or invalid surface");
			originator.failedSessionList.add(this);
		}
	}
	
	public void play(final Originator originator, int timeoutInSeconds)
	{
		playTimedout = false;
		setState(SessionState.Starting);
		Runnable r = new Runnable() { public void run() { doPlay(originator); } };
        TimeSpan start = TimeSpan.now();
		boolean completed = executeWithTimeout(r, TimeSpan.fromSeconds(timeoutInSeconds));
		Common.Logging.i(TAG, "DM Session play completed in "+TimeSpan.now().subtract(start).toString()+" seconds");
		if (completed)
		{
			setState(SessionState.Playing);
			setResolution();
		}
		else
		{
			Common.Logging.w(TAG, "DM Session "+this+" play failed - timeout");
			playTimedout = true;
			originator.failedSessionList.add(this);
		}
	}
	
	public void play(Originator originator)
	{
		play(originator, 10);
	}


	public void setResolution()
	{
		com.crestron.txrxservice.CresStreamCtrl.Resolution r = mStreamCtl.userSettings.getDmResolution(inputNumber);
		AirMediaSize res = new AirMediaSize(r.width, r.height);
		super.setResolution(res);
	}
				
	public void setHdcpBlank(boolean blank)
	{
		// TODO canvas implement
		Common.Logging.i(TAG, "DM Session setHdcpBlank called ");
		if (surface != null)
		{
			drawChromaKeyColor(blank);
		}
	}
	
	public boolean audioMute(boolean enable)
	{
		if (enable)
		{
			Common.Logging.i(TAG, "audioMute(): Session "+this+" calling sendDmMute()");
			mStreamCtl.sendDmMute(streamId, true);
		} else {
			Common.Logging.i(TAG, "audioMute(): Session "+this+" calling sendDmUnMute()");
			mStreamCtl.sendDmMute(streamId, false);
		}
		setIsAudioMuted(enable);
		return true;
	}
	
	public void updateDmWindow(int x, int y, int w, int h)
	{
		if ((left != x) || (top != y) || (width != w) || (height != h))
		{
			drawChromaKeyColor();
			left = x;
			top = y;
			width = w;
			height = h;
			Common.Logging.i(TAG, "send DM window for session "+this+" inputNumber="+inputNumber+
					" ("+width+","+height+")@("+left+","+top+")");
			mStreamCtl.sendDmWindow(streamId, left, top, width, height);
		}
		else
		{
			Common.Logging.i(TAG, "no change in DM window for session "+this+" inputNumber="+inputNumber+
					" ("+width+","+height+")@("+left+","+top+")");
		}	
	}
}
