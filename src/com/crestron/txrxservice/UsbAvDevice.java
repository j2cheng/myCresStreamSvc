package com.crestron.txrxservice;

import java.util.HashMap;

public class UsbAvDevice {
	public String deviceName;
	public String videoFile;
	public String audioFile;
	public Boolean speakerPresent;
	public String usbPortType;
	public int perId;
	public HashMap<String, String> properties;
	
	public UsbAvDevice(int id, String portType, String name, String vFile, String aFile, Boolean sFile, HashMap<String, String> propertyMap) 
	{
		deviceName = name;
		perId = id;
		usbPortType = portType;
		videoFile = vFile;
		audioFile = aFile;
		speakerPresent = sFile;
		properties = propertyMap;
	}
	
	public String toString()
	{
		return "{USB_Device: id:"+perId+" port:"+usbPortType+" name:"+deviceName+
				" videoFile:"+((videoFile != null)?videoFile:"null")+
				" audioFile:"+((audioFile != null)?audioFile:"null")+ 
				" Speaker present:"+((speakerPresent != null)?speakerPresent:false)+ 
				" properties=["+((properties != null)?properties:"null")+
				"]}";
	}
}

