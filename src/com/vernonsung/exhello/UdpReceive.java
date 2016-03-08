package com.vernonsung.exhello;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

public class UdpReceive extends HandlerThread {
	protected MulticastSocket socket = null;
	protected boolean keepReceiving = false;
	protected Handler packetReporter = null;
	
	public enum PACKET_REPORTER_MESSAGE_WHAT {
		ERROR,
		PACKET;
	}
	public class PACKET_REPORTER_MESSAGE_ERROR {
		public static final String MESSAGE = "Message";
	}
	public class PACKET_REPORTER_MESSAGE_PACKET {
		public static final String IP = "IP";
		public static final String PORT = "Port";
		public static final String MESSAGE = "Message";
	}

	/**
	 * @param name
	 * @deprecated	Please use {@link #UdpReceive(String _ip, int _port, Handler _handler)}
	 */
	public UdpReceive(String name) {
		super(name);
	}

	/**
	 * To receive UDP packets of the IP and port
	 * @param _ip	IP, ex, 255.255.255.255
	 * @param _port	Port = 1~65535
	 * @param _handler	To report Message of different type.
	 * <ul>
	 * <li> A packet
	 * <pre>
	 * {@code}
	 * Message {
	 * 	int what = PACKET_REPORTER_MESSAGE_WHAT.PACKET;
	 * 	Bundle data {
	 * 		(PACKET_REPORTER_MESSAGE_PACKET.IP, String ip),
	 * 		(PACKET_REPORTER_MESSAGE_PACKET.PORT, int port),
	 * 		(PACKET_REPORTER_MESSAGE_PACKET.MESSAGE, String message)
	 * 	}
	 * }
	 * </pre>
	 * <li> An error
	 * <pre>
	 * {@code}
	 * Message {
	 * 	int what = PACKET_REPORTER_MESSAGE_WHAT.ERROR;
	 * 	Bundle data {
	 * 		(PACKET_REPORTER_MESSAGE_ERROR.MESSAGE, String message)
	 * 	}
	 * }
	 * </pre>
	 * </ul>
	 * @throws Exception 
	 */
	public UdpReceive(String _ip, int _port, Handler _handler) throws Exception {
		super("UdpReceiverService");
		
		InetAddress group = null;
		packetReporter = _handler;
		keepReceiving = true;
		group = InetAddress.getByName(_ip);
		socket = new MulticastSocket(_port);
		if (group.isMulticastAddress())
			socket.joinGroup(group);
		if (socket.getBroadcast())
			reportError("Broadcast is enabled");
	}
	
	public void stopReceiving() {
		keepReceiving = false;
		if (socket != null && !socket.isClosed()) {
			socket.close();
		}
	}
	
	/**
	 * Report information of a received packet to the UI thread 
	 * @param _ip	Sender's IP
	 * @param _port	Sender's port
	 * @param _message	Sender's message
	 */
	private void reportPacket(String _ip, int _port, String _message) {
		Bundle b = null;
		Message m = null;
		b = new Bundle();
		b.putString(PACKET_REPORTER_MESSAGE_PACKET.IP, _ip);
		b.putInt(PACKET_REPORTER_MESSAGE_PACKET.PORT, _port);
		b.putString(PACKET_REPORTER_MESSAGE_PACKET.MESSAGE, _message);
		m = Message.obtain();
		m.what = PACKET_REPORTER_MESSAGE_WHAT.PACKET.ordinal();
		m.setData(b);
		packetReporter.sendMessage(m);
	}

	/**
	 * Report an error message to the UI thread
	 * @param _message	Error message
	 */
	private void reportError(String _message) {
		Bundle b = null;
		Message m = null;
		b = new Bundle();
		b.putString(PACKET_REPORTER_MESSAGE_ERROR.MESSAGE, _message);
		m = Message.obtain();
		m.what = PACKET_REPORTER_MESSAGE_WHAT.ERROR.ordinal();
		m.setData(b);
		packetReporter.sendMessage(m);
	}
	
	@Override
	public void run() {
		byte[] buf = new byte[65536];
		String message = null;
		String sender = null;
		int port;
		DatagramPacket packet = null;

		while (keepReceiving) {
			try {
				// Receive a packet
				packet = new DatagramPacket(buf, buf.length);
				socket.receive(packet);
				
				// Get packet information
				message = new String(packet.getData(), 0, packet.getLength());
				sender = packet.getAddress().toString().substring(1);
				port = packet.getPort();
				
				// Show in UI
				reportPacket(sender, port, message);
			} catch (IOException e) {
				e.printStackTrace();
				keepReceiving = false;
				
				// Show in UI
				reportError(e.getMessage());
			}
		}
		if (!socket.isClosed())
			socket.close();
		socket = null;
	}

	@Override
	protected void finalize() throws Throwable {
		if (!socket.isClosed())
			socket.close();
		
		super.finalize();
	}
	
}
