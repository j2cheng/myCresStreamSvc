package com.crestron.airmedia.canvas.channels.ipc;

import android.view.Surface;
import com.crestron.airmedia.canvas.channels.ipc.CanvasSourceSession;
import com.crestron.airmedia.canvas.channels.ipc.CanvasSurfaceAcquireResponse;
import com.crestron.airmedia.canvas.channels.ipc.CanvasResponse;
import com.crestron.airmedia.canvas.channels.ipc.CanvasSurfaceOptions;
import com.crestron.airmedia.canvas.channels.ipc.ICanvasSourceManager;
import java.util.List;

interface IAirMediaCanvas {
    void setSourceManager(in ICanvasSourceManager manager);

    void sessionsUpdate(in List<CanvasSourceSession> sessions);

    CanvasSurfaceAcquireResponse surfaceAcquire(in String sessionId, in CanvasSurfaceOptions options);
    CanvasResponse surfaceRelease(in String sessionId);
    
    void sessionUpdate(in CanvasSourceSession sessions);
    
    CanvasSurfaceAcquireResponse surfaceReplace(in String oldSessionId, in String newSessionId, in CanvasSurfaceOptions options);
}
