package com.wol.wollistenerservice;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Enumeration;

import org.apache.http.conn.util.InetAddressUtils;
import org.apache.http.util.ExceptionUtils;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;


/*
 * Linux command to send UDP:
 * #socat - UDP-DATAGRAM:192.168.1.255:11111,broadcast,sp=11111
 */
public class WolListenerService extends Service {
	static final String TAG = "WolListenerService";
	static final int wolBroadcastPort = 9;
	static final int wolFromRouterReceivePort = 1111;
	
	private DatagramSocket socket;
	private WifiManager mWifi;
	private Thread UDPListenAndBroadcastThread = null;
	private Boolean shouldRestartSocketListen = true;	
	private InetAddress localIP = null;
	

	
	@Override
	public void onCreate() {
		
	};
	
	@Override
	public void onDestroy() {
		stopListen();
	}
	
	void stopListen() {
		shouldRestartSocketListen = false;
		socket.close();
		UDPListenAndBroadcastThread = null;
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {

		localIP = getLocalIpAddress();
		shouldRestartSocketListen = true;
		startListenForUDPBroadcast();
		Log.i(TAG, "Service started");
		return START_STICKY;
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	public InetAddress getLocalIpAddress() {
	    try {
	        for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
	            NetworkInterface intf = en.nextElement();
	            for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
	                InetAddress inetAddress = enumIpAddr.nextElement();
	                String sAddr = inetAddress.getHostAddress().toString();
	                if (!inetAddress.isLoopbackAddress() && InetAddressUtils.isIPv4Address(sAddr)) {
	        	        Log.i(TAG, "Local IP is: " + sAddr);
	                    return inetAddress;
	                }
	            }
	        }
	    } catch (SocketException ex) {
	        Log.e(TAG, ex.toString());
	    }
	    return null;
	}
	
	void startListenForUDPBroadcast() {
		if (UDPListenAndBroadcastThread != null) {
			return;
		}
		
        mWifi = (WifiManager)getSystemService(Context.WIFI_SERVICE);
		UDPListenAndBroadcastThread = new Thread(new Runnable() {
			public void run() {
				try {
					Integer port = wolFromRouterReceivePort;
					while (shouldRestartSocketListen) {
						if (localIP == null) {
							Log.e(TAG, "no local IP. sleeping for a minute and will try to get it again");
							Thread.sleep(60000);
							localIP = getLocalIpAddress();
							continue;
						}
						listenAndWaitAndSendBroadcast(localIP, port);
					}
				} catch (Exception e) {
					Log.i(TAG, "no longer listening for UDP broadcasts because of error " + e.getMessage());
				}
				UDPListenAndBroadcastThread = null;
			}
		});
		UDPListenAndBroadcastThread.start();
	}
	
	private void listenAndWaitAndSendBroadcast(InetAddress localIP, Integer localPort) throws Exception {
		byte[] recvBuf = new byte[108];
		if (socket == null || socket.isClosed()) {
			socket = new DatagramSocket(localPort, localIP);
			socket.setBroadcast(true);
		}
		
		DatagramPacket recvPacket = new DatagramPacket(recvBuf, recvBuf.length);
		Log.i(TAG, "Waiting for UDP packet");
		socket.receive(recvPacket);
		
		if (!isWolPacket(recvPacket.getData(), recvPacket.getData().length)) {
			Log.e(TAG, "Received packet is not a WOL packet. Not forwarding.");
			return;
		}
		
		DatagramPacket sendPacket = new DatagramPacket(recvPacket.getData(), recvPacket.getLength(),
			    getBroadcastAddress(), wolBroadcastPort);

		Log.i(TAG, "Re-Sending the WOL packet");
		socket.send(sendPacket);
		socket.send(sendPacket);
		socket.close();
	}
	
	/**
	 * Checks if a packet is a valid WOL packet.
	 * A valid WOL packet holds: 6 x 0xff, 16 x MAC address, password.
	 * (the password can be 0 or 4 or 6 bytes).
	 * 
	 * @param buf - the byte array with the packet's data
	 * @param length - the length of the byte array
	 * @return - true if the packet holds a valid WOL packet, false otherwise
	 */
	private boolean isWolPacket(byte[] buf, int length) {
		if (length != 102 && length != 106 && length != 108) {
			//
			//the password can be 0 or 4 or 6 bytes
			return false;
		}
		
		int curIndex = 0;
		byte[] macAddress = new byte[6];
		
		//check the Synchronization Stream
		for (curIndex = 0; curIndex < 6; curIndex++) {
			if (buf[curIndex] != (byte)0xff) {
				return false;
			}
		}
		
		//save the MAC address for checking that it is duplicated as needed
		for (; curIndex < 12; curIndex++) {
			macAddress[curIndex - 6] = buf[curIndex];
		}
		
		//check MAC address duplications
		for (int i = 0; i < 15; i++) {
			for (int j = 0; j < 6; j++) {
				if (buf[curIndex++] != macAddress[j]) {
					return false;
				}
			}
		}		
		
		return true;
	}

	/**
	 * Calculate the broadcast IP we need to send the packet along. If we send it
	 * to 255.255.255.255, it never gets sent. I guess this has something to do
	 * with the mobile network not wanting to do broadcast.
	 */
	private InetAddress getBroadcastAddress() throws IOException {
		DhcpInfo dhcp = mWifi.getDhcpInfo();
		if (dhcp == null) {
			Log.d(TAG, "Could not get dhcp info");
			return null;
		}

		int broadcast = (dhcp.ipAddress & dhcp.netmask) | ~dhcp.netmask;
		byte[] quads = new byte[4];
		for (int k = 0; k < 4; k++)
			quads[k] = (byte) ((broadcast >> k * 8) & 0xFF);
		return InetAddress.getByAddress(quads);
	}	
}