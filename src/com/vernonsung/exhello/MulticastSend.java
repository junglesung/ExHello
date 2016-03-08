package com.vernonsung.exhello;

import java.io.*;
import java.net.*;
import java.util.*;

public class MulticastSend extends Thread {
	protected DatagramSocket socket = null;
	protected String message = null;
	protected boolean keepSending = false;
	protected InetAddress group = null;
	protected int port = 12345;
	protected long maxIntervalMs = 5000;
	protected int count = 0;

	public String getMessage() {
		synchronized (message) {
			return message;
		}
	}

	public void setMessage(String message) {
		synchronized (message) {
			this.message = message;
		}
	}

	public boolean isKeepSending() {
		return keepSending;
	}

	public void setKeepSending(boolean keepSending) {
		this.keepSending = keepSending;
	}

	public int getCount() {
		return count;
	}

	public MulticastSend() throws Exception {
		this("230.0.0.1", 12345, new Date().toString());
	}

	public MulticastSend(String _ip, int _port, String _message) throws Exception {
		super("MulticastThread");
		message = _message;
		group = InetAddress.getByName(_ip);
		port = _port;
		socket = new DatagramSocket();
	}

	public void run() {
		byte[] buf = null;
		DatagramPacket packet = null;

		count = 0;
		keepSending = true;
		while (keepSending) {
			try {
				// send packet
				synchronized (message) {
					buf = message.getBytes();
				}
				packet = new DatagramPacket(buf, buf.length, group, port);
				socket.send(packet);
				count++;

				// sleep for a while
				try {
					sleep((long) (Math.random() * maxIntervalMs));
				} catch (InterruptedException e) {
				}
			} catch (IOException e) {
				e.printStackTrace();
				keepSending = false;
			}
		}
		socket.close();
	}
}
