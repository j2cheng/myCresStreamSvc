package com.crestron.txrxservice;

public class UsbAvDevice {
	public String deviceName;
	public String videoFile;
	public String audioFile;
	public String usbPortType;
	public int perId;
	
	public UsbAvDevice(int id, String portType, String name, String vFile, String aFile) {
		deviceName = name;
		perId = id;
		usbPortType = portType;
		videoFile = vFile;
		audioFile = aFile;
	}
	
	public String toString()
	{
		return "{USB_Device: id:"+perId+" port:"+usbPortType+" name:"+deviceName+
				" videoFile:"+((videoFile != null)?videoFile:"null")+
				" audioFile:"+((audioFile != null)?audioFile:"null")+"}";
	}
}

