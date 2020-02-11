package com.crestron.airmedia.canvas.channels.ipc;

import com.crestron.airmedia.canvas.channels.ipc.CanvasResponse;
import com.crestron.airmedia.canvas.channels.ipc.CanvasSourceRequest;

interface ICanvasSourceManager {
    CanvasResponse sourceRequest(in CanvasSourceRequest request);
}
