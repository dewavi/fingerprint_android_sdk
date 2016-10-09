package com.fys.password;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashMap;

import com.fys.handprint.Protocol.CommandPack;
import com.fys.handprint.Protocol.DataPacket;
import com.fys.handprint.Protocol.Defined;
import com.fys.handprint.Protocol.ResponsePacket;
import com.fys.handprint.Protocol.Utils;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbRequest;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;
/*
 * ?��?��USB??�籵???
 * ?��潰USB籵�?�袨??
 * 潰脤USB?��??��?��?��??
 * mass storage 源�??
 */
public class UsbHelper {

	// USB?��???
	final int WaitTime = 1000; // ??��?�?�?
	private UsbManager usbManager;
	private UsbDevice usbDevice;
	boolean isFindDevice;
	final int Vid = 1241;	//?��??�ID
	final int Pid = 32776;	//?��模ID
	UsbEndpoint inEndpoint;// 黍�?��?�誹?��
	UsbEndpoint outEndpoint;// 迡�?��?�誹?��
	UsbDeviceConnection connection;
	byte[] m_abyTransferBuf = new byte[512];
	String TAG = "UsbHelper";
	Context context;// 奻�?��??
	Handler pHandler;// 籵眭
	int sendMax, recMax;
	public boolean Isconneced=false;

	private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
	private PendingIntent pendingIntent;

	public UsbHelper(Context c, Handler h) {
		context = c;
		pHandler = h;	
		usbManager = (UsbManager) context
				.getSystemService(Context.USB_SERVICE);
		isFindDevice = false;
		deviceHandler.postDelayed(deviceRunnable, 1000);
		pendingIntent = PendingIntent.getBroadcast(context, 0, new Intent(
				ACTION_USB_PERMISSION), 0);
	}
/*
 * ?��梑扢???
 */
	void FindDevice() {
		try {
			HashMap<String, UsbDevice> map = usbManager.getDeviceList();
			isFindDevice=false;
			for (UsbDevice device : map.values()) {
				if (Vid == device.getVendorId()
						&& Pid == device.getProductId()) {
					usbDevice = device;
					isFindDevice=true;
					break;
				}
			}
		
			if(!isFindDevice) return;	//羶�?��?��?�USB?��???

			if (usbManager.hasPermission(usbDevice)) {
				isFindDevice = true;
				Message msg = new Message();
				msg.what = Defined.USBPermission;
				pHandler.sendMessage(msg);
				INIConnection();

			} else {
				usbManager.requestPermission(usbDevice, pendingIntent);

			}

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}


	Handler deviceHandler = new Handler();
	//?��梑�?��?�USB,?��10??�脤梑珨棒�?�珨?��??��?��??;
	Runnable deviceRunnable = new Runnable() {

		@Override
		public void run() {
			// TODO Auto-generated method stub
			if (!isFindDevice) {
				FindDevice();
				deviceHandler.postDelayed(deviceRunnable, 10000);
			}
		}

	};
	
	void Close() {
		UsbInterface intf = usbDevice.getInterface(0);
		 connection.releaseInterface(intf);
		connection.close();
	}
	void INIConnection() {
		// ?��??��?��?��?��?�珨跺�?�諳
		UsbInterface intf = usbDevice.getInterface(0);
		connection = usbManager.openDevice(usbDevice);
		// connection.controlTransfer(requestType, request, value, index,
		// buffer, length, timeout)
		isFindDevice = connection.claimInterface(intf, true);
		inEndpoint = intf.getEndpoint(0);
		outEndpoint = intf.getEndpoint(1);
		sendMax = outEndpoint.getMaxPacketSize();
		recMax = inEndpoint.getMaxPacketSize();
		Isconneced=true;

	}
	// cmd?��??��?�鎢 para 統�??
	// 殿�?�ㄩResponsePacket
	// ??��??��1.1﹜SCSI 楷�?? 1.2﹜�?��?�楷??? 1.3 黍SCSI 袨�?? 2.1﹜SCSI諉彶 2.2﹜�?�彶 ACK 2.3黍SCSI
	// 袨�??
	public ResponsePacket SendCmd(short cmd, int para) {

		if (connection == null)
			return null;
		if(bulkSend(cmd, para))
		{
			return bulkResponse();
		}
		return null;
	}
	
	ResponsePacket bulkResponse()
	{
		byte[] responseBytes = new byte[Defined.responsePackLen];
		byte[] scsiStateBytes = new byte[13];
		byte[] scsiBytes = getSCSI(true, Defined.responsePackLen);
		// 2.1﹜SCSI諉彶
		if (connection.bulkTransfer(outEndpoint, scsiBytes,
				scsiBytes.length, WaitTime) != scsiBytes.length)
			return null;
//		Log.e(TAG, "2.1﹜SCSI諉彶"+Utils.byteToString(scsiBytes));
		// 2.2﹜�?�彶 ACK
		if (connection.bulkTransfer(inEndpoint, responseBytes,
				responseBytes.length, WaitTime) != responseBytes.length)
			return null;
//		Log.e(TAG, "2.2﹜�?�彶 ACK"+Utils.byteToString(responseBytes));
		// 2.3 黍SCSI 袨�??
		if (connection.bulkTransfer(inEndpoint, scsiStateBytes,
				scsiStateBytes.length, WaitTime) != scsiStateBytes.length)
			return null;
//		Log.e(TAG, "2.3 黍SCSI 袨�??"+Utils.byteToString(scsiStateBytes));
		return new ResponsePacket(responseBytes);
	}
	public DataPacket bulkData(int len)
	{
		byte[] responseBytes = new byte[len+6];
		byte[] scsiStateBytes = new byte[13];
		byte[] scsiBytes = getSCSI(true, len+6);
		byte[] tempBytes=new byte[recMax];
		int k=0;
		// 3.1﹜SCSI諉彶
		int out= connection.bulkTransfer(outEndpoint, scsiBytes,
				scsiBytes.length, WaitTime) ;
		if (out!= scsiBytes.length)
		{
			Log.e(TAG, "3.1﹜SCSI諉彶"+Utils.byteToString(scsiBytes));
			return null;
		}
		else
		{
			Log.e(TAG, "3.1﹜SCSI諉彶"+Utils.byteToString(scsiBytes));
		}
		// 3.2﹜�?�彶 ACK
		int out1=0;
		k=0;
		do
		{
		out =connection.bulkTransfer(inEndpoint, tempBytes,
				tempBytes.length, WaitTime);
		
		if(out>0)
		{
		 System.arraycopy(tempBytes,0,responseBytes,k,out);
		 k+=out;
		 out1+=out;
		}
		} while(k<responseBytes.length);
		
		if (out1 != responseBytes.length)
		{
//			Log.e(TAG, "3.2﹜�?�彶 ACK"+Utils.byteToString(responseBytes));
			return null;
		}
		else
		{
			Log.e(TAG, "??��?��?��?�ㄩ"+ responseBytes.length);
		}
		

		// 3.3 黍SCSI 袨�??
		if (connection.bulkTransfer(inEndpoint, scsiStateBytes,
				scsiStateBytes.length, WaitTime) != scsiStateBytes.length)
		{
			Log.e(TAG, "3.3 黍SCSI 袨�??"+Utils.byteToString(scsiStateBytes));
			return null;
		}
		
		return new DataPacket(responseBytes);
	}
	int bulkDataOut(int len,byte[] data)
	{
		//byte[] responseBytes = new byte[len+6];
		byte[] scsiStateBytes = new byte[13];
		byte[] scsiBytes = getSCSI(false, len+6);
		int out,out1;
		//byte[] tempBytes=new byte[recMax];

		// 3.1﹜SCSI諉彶
		out=connection.bulkTransfer(outEndpoint, scsiBytes,
				scsiBytes.length, WaitTime) ;
		
		//do
		//{
		out1=connection.bulkTransfer(outEndpoint, data,
				(len+6), WaitTime);
		//}while(out1!=(len+6));
		
		connection.bulkTransfer(inEndpoint, scsiStateBytes,
						scsiStateBytes.length, WaitTime);

		return out1;

	}
	boolean bulkSend(short cmd, int para)
	{
		boolean rec=true;
		CommandPack cmdp = new CommandPack(cmd, para);
		byte[] sendByte = cmdp.cmdBytes;
		byte[] scsiBytes = getSCSI(false, 12);
	
		byte[] scsiStateBytes = new byte[13];
		// 1.1﹜SCSI 楷�??		
		if (connection.bulkTransfer(outEndpoint, scsiBytes,
				scsiBytes.length, WaitTime)!= scsiBytes.length)
			return false;
//		Log.e(TAG, "1﹜SCSI 楷�??"+","+Utils. byteToString(scsiBytes));
		// 1.2﹜�?��?�楷???		
	if (connection.bulkTransfer(outEndpoint, sendByte, sendByte.length,
			WaitTime) != sendByte.length)
			return false;
//	Log.e(TAG, "1.2﹜�?��?�楷???"+"--"+Utils.byteToString(sendByte));
		// 1.3 黍SCSI 袨�??	
		if (connection.bulkTransfer(inEndpoint, scsiStateBytes,
				scsiStateBytes.length, WaitTime) != scsiStateBytes.length)
			return false;
//		Log.e(TAG, "1.3 黍SCSI 袨�??"+Utils.byteToString(scsiStateBytes));
		return rec;
	}
	

	// 諷秶??��?�揭?��

	byte[] getSCSI(boolean recFlag, int len) {
		ByteBuffer scsiBuffer = ByteBuffer.allocate(31);
		scsiBuffer.order(ByteOrder.LITTLE_ENDIAN);
		scsiBuffer.putInt(0, 0x43425355);// 0x43425355 梓�?��?�CBW??��?�輸
		scsiBuffer.putInt(4, 0x89182b28);// 0x89182b28 CBW梓�??
		scsiBuffer.putInt(8, len);
		if (recFlag) // 1=data-in from the device to the host
		{
			scsiBuffer.put(12, (byte) 0x80);
			scsiBuffer.putShort(15, (short) 0xffef);
		} else // 0=data-out from host to the device
		{
			scsiBuffer.put(12, (byte) 0x0);
			scsiBuffer.putShort(15, (short) 0xfeef);
		}
		scsiBuffer.put(13, (byte) 0); // LUN
		scsiBuffer.put(14, (byte) 10);// CBWCB??��?�虴趼誹??��??
		return scsiBuffer.array();

	}
	
}
