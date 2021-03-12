package com.crestron.txrxservice;

public class UsbAvDevice {
	public String deviceName;
	public String bus;
	public String videoFile;
	public String audioFile;
	public String usbPortType;
	public int perId;
	
	public UsbAvDevice(int id, String portType, String usbBus, String name) {
		deviceName = name;
		perId = id;
		usbPortType = portType;
		bus = usbBus;
	}
	
	public String toString()
	{
		return "{USB_Device: id:"+perId+"port:"+usbPortType+" name:"+deviceName+" bus:"+bus+
				" videoFile:"+((videoFile != null)?videoFile:"null")+
				" audioFile:"+((audioFile != null)?audioFile:"null")+"}";
	}
}

