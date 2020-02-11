package com.crestron.txrxservice.canvas;

public class Originator {
	public RequestOrigin origin;
	private Session session;
	private CanvasSourceResponse response;
	
	public Originator(RequestOrigin o)
	{
		origin = o;
		session = null;
	}
	
	public Originator(RequestOrigin o, Session s)
	{
		origin = o;
		session = s;
	}
	
	public Originator(RequestOrigin o, CanvasSourceResponse r)
	{
		origin = o;
		response = r;
	}
	
	public Session getSession() {return (origin == RequestOrigin.Receiver) ? session : null; }
	public void setSession(Session s) { session = s; }
	
	public CanvasSourceResponse getCanvasResponse() {return (origin == RequestOrigin.CanvasSourceRequest) ? response : null; }
	public void setCanvasSourceResponse(CanvasSourceResponse r) { response = r; }
	
	public String toString() { return origin.toString() + ((origin == RequestOrigin.Receiver) ? session : ""); }
}
