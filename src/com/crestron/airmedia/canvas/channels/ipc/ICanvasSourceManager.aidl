package com.crestron.airmedia.canvas.channels.ipc;

import com.crestron.airmedia.canvas.channels.ipc.CanvasResponse;
import com.crestron.airmedia.canvas.channels.ipc.CanvasSourceRequest;
import com.crestron.airmedia.canvas.channels.ipc.CanvasLayout;
import com.crestron.airmedia.canvas.channels.ipc.CanvasStatus;

interface ICanvasSourceManager {
    CanvasResponse sourceRequest(in CanvasSourceRequest request);
    
    oneway void sourceLayout(in CanvasLayout layout);
    
    oneway void status(in CanvasStatus status);
}
