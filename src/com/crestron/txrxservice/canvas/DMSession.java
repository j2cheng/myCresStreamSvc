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

	public DMSession(int inputNumber) {
		super(); // will assign id;
		state = SessionState.Connecting;
		type = SessionType.DM;
		airMediaType = null;
		this.inputNumber = inputNumber;
		userLabel = "DM-"+String.valueOf(inputNumber);
		platform = CanvasPlatformType.Hardware;
	}
	
	public String toString()
	{
		return ("Session: "+type.toString()+"-"+inputNumber+"  sessionId="+sessionId());
	}
	
	public void doStop(boolean replace)
	{
		Common.Logging.i(TAG, "DM Session "+this+" stop request");
		if (streamId >= 0)
		{
			Common.Logging.i(TAG, "DM Session "+this+" sending stop to csio");
			mStreamCtl.sendDmStart(inputNumber, false);
			if (!replace)
			{
				mCanvas.hideWindow(streamId); // TODO remove once real canvas app available
				releaseSurface();
			}
		}
	}
	
	public void stop(final boolean replace, int timeoutInSeconds)
	{
		Runnable r = new Runnable() { public void run() { doStop(replace); } };
        TimeSpan start = TimeSpan.now();
		boolean completed = executeWithTimeout(r, TimeSpan.fromSeconds(timeoutInSeconds));
		Common.Logging.i(TAG, "DM Session stop completed in "+TimeSpan.now().subtract(start).toString()+" seconds");
		if (completed) {
			streamId = -1;
			setState(SessionState.Stopped);
		}
	}
	
	public void stop(Originator originator)
	{
		stop(false, 10);
	}
	
	public void stop(Originator originator, boolean replace)
	{
		stop(replace, 10);
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
	
	public void doPlay(int replaceStreamId)
	{		
		Common.Logging.i(TAG, "DM Session "+this+" play request");
		if (replaceStreamId > 0)
		{
			streamId = replaceStreamId;
		} else {
			// get unused streamId and associate a surface with it
			streamId = mCanvas.mSurfaceMgr.getUnusedStreamId();
			mCanvas.showWindow(streamId); // TODO remove once real canvas app available
		}
		Common.Logging.i(TAG, "DM Session "+this+" got streamId "+streamId);
		surface = acquireSurface();
		Common.Logging.i(TAG, "DM Session "+this+" sending start to csio");
		mStreamCtl.sendDmStart(inputNumber, true);
        if (!CresCanvas.useCanvasSurfaces) {
        	mStreamCtl.setFormat(streamId, PixelFormat.RGBA_8888);
        }
		if (surface != null && surface.isValid())
		{
			Common.Logging.i(TAG, "DM Session "+this+" drawing to surface: "+surface);
			drawChromaKeyColor();
			if (!CresCanvas.useCanvasSurfaces)
			{
				Rect window = getWindow(surface);
				mStreamCtl.sendDmWindow(inputNumber, window.left, window.top, window.width(), window.height());
			}
		}
		else
		{
			Common.Logging.e(TAG, "DM Session "+this+" doPlay() got null or invalid surface");
		}
	}
	
	public void play(Originator originator, final int replaceStreamId, int timeoutInSeconds)
	{
		Runnable r = new Runnable() { public void run() { doPlay(replaceStreamId); } };
        TimeSpan start = TimeSpan.now();
		boolean completed = executeWithTimeout(r, TimeSpan.fromSeconds(timeoutInSeconds));
		Common.Logging.i(TAG, "DM Session play completed in "+TimeSpan.now().subtract(start).toString()+" seconds");
		if (completed)
		{
			setState(SessionState.Playing);
			setResolution();
		}
	}
	
	public void play(Originator originator, int replaceStreamId)
	{
		play(originator, replaceStreamId, 10);
	}
	
	public void play(Originator originator)
	{
		play(originator, -1, 10);
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
}
