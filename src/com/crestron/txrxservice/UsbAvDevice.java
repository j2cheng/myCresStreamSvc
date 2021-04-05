package com.crestron.txrxservice;

import java.util.List;

import com.gs.core.peripheral.PeripheralUsbDevice;

public class UsbAvDevice {
	public String deviceName;
	public String videoFile;
	public String audioFile;
	public String usbPortType;
	public int perId;
	public List<PeripheralUsbDevice> perDevices;
	
	public UsbAvDevice(int id, String portType, String name, String vFile, String aFile, List<PeripheralUsbDevice> perUsbDevices) {
		deviceName = name;
		perId = id;
		usbPortType = portType;
		videoFile = vFile;
		audioFile = aFile;
		perDevices = perUsbDevices;
	}
	
	public String toString()
	{
		return "{USB_Device: id:"+perId+" port:"+usbPortType+" name:"+deviceName+
				" videoFile:"+((videoFile != null)?videoFile:"null")+
				" audioFile:"+((audioFile != null)?audioFile:"null")+ 
				" devices="+((perDevices != null)?perDevices:"null")+"}";
	}
}

