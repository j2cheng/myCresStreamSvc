package com.crestron.txrxservice.canvas;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.Collection;
import java.util.List;
import java.util.LinkedList;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.EnumSet;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.net.InetAddress;
import java.net.UnknownHostException;

import android.content.Context;
import android.util.Log;

import com.crestron.airmedia.canvas.channels.ipc.CanvasResponse;
import com.crestron.airmedia.canvas.channels.ipc.CanvasSourceAction;
import com.crestron.airmedia.canvas.channels.ipc.CanvasSourceRequest;
import com.crestron.airmedia.canvas.channels.ipc.CanvasSourceTransaction;
import com.crestron.airmedia.utilities.Common;
import com.crestron.airmedia.utilities.TimeSpan;
import com.crestron.airmedia.utilities.TaskScheduler;
import com.crestron.txrxservice.CresStreamCtrl.StreamState;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

public class SimulatedAVF
{
    public static final String TAG = "SimulatedAVF";
	int failureReason = 0;
	CanvasCrestore mCrestore = null;
	Gson gson;
	Map<String, AVFSession> sessions = new ConcurrentHashMap<String, AVFSession>();

	public SimulatedAVF(CanvasCrestore crestore)
	{
		mCrestore = crestore;
		gson = mCrestore.getGson();
	}

	public class AVFSession {
		SessionType type;
		String state; // "Play" or "Stop"
		String url;
		Integer inputNumber;
		long playTime;

		public AVFSession(String t, String s, String u, Integer i) {
			type = SessionType.valueOf(t);
			state = s;
			url = (type == SessionType.AirBoard) ? ((u==null)?"wss:/xyzzy":u) : null;
			inputNumber = (type == SessionType.HDMI || type == SessionType.DM) ? i : null;
			playTime = state.equalsIgnoreCase("Play") ? System.currentTimeMillis() : 0;
		}
	}
	
	public void processSessionEvent(CanvasCrestore.SessionEvent event)
	{

		Common.Logging.i(TAG, "SimulatedAVF processing Session Event Message "+mCrestore.getGson().toJson(event));
		Map<String,CanvasCrestore.SessionEventMapEntry> map = event.sessionEventMap;
		if (map == null || map.isEmpty())
		{
			Common.Logging.i(TAG, "Session Event Map is null or empty "+gson.toJson(event));
			return;
		}
		if (map.size() > 1)
		{
			Common.Logging.i(TAG, "Session Event Map has more than one entry - this AVF cannot handle "+gson.toJson(event));
			return;
		}
		for (Map.Entry<String,CanvasCrestore.SessionEventMapEntry> entry : map.entrySet())
		{
			String sessionId = entry.getKey();
			CanvasCrestore.SessionEventMapEntry e = entry.getValue();
			if (e.state.equalsIgnoreCase("Connect")) {
				// Allow incoming connections as long as number of connected sessions is < MaxConnected
				if (sessions.containsKey(sessionId))
				{
					Log.i(TAG, "Ignoring connect message for sessionId="+sessionId+" it is present in list of existing AVF sessions");
					failureReason = 1;
				}
				if (sessions.size() >= CresCanvas.MAX_CONNECTED)
				{
					Log.i(TAG, "Ignoring connect message for sessionId="+sessionId+" already have "+sessions.size()+" sessions");
					failureReason = 2;
				}
				if (failureReason == 0)
				{
					Log.i(TAG, "Creating AVF session with sessionId="+sessionId+"  state = Stop");
					AVFSession s = new AVFSession(e.type, "Stop", (String)null, e.inputNumber);
					Log.i(TAG, "Add session sessionId="+sessionId+" to AVF sessions map");
					sessions.put(sessionId, s);
				}
			} 
			else if (e.state.equalsIgnoreCase("Disconnect"))
			{
				if (!sessions.containsKey(sessionId))
				{
					Log.i(TAG, "Ignoring disconnect message for sessionId="+sessionId+" it is not present in list of existing AVF sessions");
					failureReason = 1;
				}
				if (failureReason == 0)
				{
					Log.i(TAG, "Remove session sessionId="+sessionId+" from AVF sessions map");
					sessions.remove(sessionId);
				}
			}
			else if (e.state.equalsIgnoreCase("Stop"))
			{
				if (!sessions.containsKey(sessionId))
				{
					Log.i(TAG, "Ignoring stop message for sessionId="+sessionId+" it is not present in list of existing AVF sessions");
					failureReason = 3;
				}
				AVFSession s = sessions.get(sessionId);
				if (!s.state.equalsIgnoreCase("Play"))
				{
					Log.i(TAG, "Ignoring stop message for sessionId="+sessionId+" it is not in a playing state");
					failureReason = 1;
				}
				if (failureReason == 0)
				{
					s.state = "Stop";
					s.playTime = 0;
					// If we stop a non-HDMI session and there is HDMI connected then start playing HDMI
					if (s.type != SessionType.HDMI)
					{
						for (Map.Entry<String,AVFSession> se : sessions.entrySet()) {
							if (se.getValue().type == SessionType.HDMI && se.getValue().state.equalsIgnoreCase("Stop"))
							{
								se.getValue().state = "Play";
								se.getValue().playTime = System.currentTimeMillis();
							}
						}
					}
				}
			}
			else if (e.state.equalsIgnoreCase("Play"))
			{
				if (!sessions.containsKey(sessionId))
				{
					Log.i(TAG, "Ignoring play message for sessionId="+sessionId+" it is not present in list of existing AVF sessions");
					failureReason = 3;
				}
				AVFSession s = sessions.get(sessionId);
				if (!s.state.equalsIgnoreCase("Stop"))
				{
					Log.i(TAG, "Ignoring play message for sessionId="+sessionId+" it is not in a stopped state");
					failureReason = 1;
				}
				if (failureReason == 0)
				{
					// Make a list of playing sessions
					List<Map.Entry<String,AVFSession>> playingList = new LinkedList<Map.Entry<String,AVFSession>>();
					for (Map.Entry<String,AVFSession> se : sessions.entrySet())
					{
						if (se.getValue().state.equalsIgnoreCase("Play"))
							playingList.add(se);
					}
					if (playingList.size() >= CresCanvas.MAX_PRESENTING)
					{
						// get oldest playing session
						Map.Entry<String,AVFSession> oldest = playingList.get(0);
						for (int i=1; i < playingList.size(); i++)
						{
							if (oldest.getValue().playTime > playingList.get(i).getValue().playTime)
							{
								oldest = playingList.get(i);
							}
						}
						// Force stop of oldest session
						oldest.getValue().state = "Stop";
						oldest.getValue().playTime = 0;
					}
					// change state to play for this session
					s.state = "Play";
					s.playTime = System.currentTimeMillis();
				}
			}
		}
		sendSessionResponse(event.transactionId, failureReason);
	}

	public void sendSessionResponse(String transactionId, int failureReason)
	{
		CanvasCrestore.SessionResponse r = mCrestore.new SessionResponse();

		r.transactionId = transactionId;
		if (failureReason != 0)
			r.failureReason = Integer.valueOf(failureReason);
		else
		{
			Log.i(TAG, "Generating sessionResponseMap from AVF session map - size="+sessions.size());
			Map<String, CanvasCrestore.SessionResponseMapEntry> map = new HashMap<String, CanvasCrestore.SessionResponseMapEntry>();

			for (Map.Entry<String, AVFSession> sentry : sessions.entrySet())
			{
				CanvasCrestore.SessionResponseMapEntry v = mCrestore.new SessionResponseMapEntry();
				v.state = sentry.getValue().state;
				v.url = sentry.getValue().url;
				map.put(sentry.getKey(), v);
			}
			r.sessionResponseMap = map;
		}

		mCrestore.sendSessionResponse(r);
	}
}
