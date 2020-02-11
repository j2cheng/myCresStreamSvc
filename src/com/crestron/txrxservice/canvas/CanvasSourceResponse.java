package com.crestron.txrxservice.canvas;

import com.crestron.airmedia.canvas.channels.ipc.CanvasResponse;

// Just to get around the fact that CanvasResponse has protected methods for setting the object
public class CanvasSourceResponse extends CanvasResponse {
	@Override
	public void setErrorCode(int code) { super.setErrorCode(code); }

	@Override
	public void setErrorMessage(String message) { super.setErrorMessage(message); }

	@Override
	public void setError(int code, String message) { super.setError(code, message); }
}


