package com.vernonsung.exhello;

import java.util.HashMap;
import android.content.Context;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.widget.Toast;

public class P2pNsdHelper {
	public static final String srvType = "_holdme._tcp";
	private Context context;
	private WifiP2pManager mManager;
	private WifiP2pManager.Channel mChannel;
	private WifiP2pDnsSdServiceInfo mServInfo;
	WifiP2pManager.ActionListener addLocalServiceActionlistener = new WifiP2pManager.ActionListener() {
		@Override
		public void onSuccess() {
			Toast.makeText(context, "[AddService] Wifi is OK", Toast.LENGTH_SHORT).show();
		}
		@Override
		public void onFailure(int reason) {
			String message;
			switch (reason) {
			case WifiP2pManager.P2P_UNSUPPORTED:
				message = context.getString(R.string.wifi_p2p_unsupported);
				break;
			case WifiP2pManager.BUSY:
				message = context.getString(R.string.wifi_p2p_busy);
				break;
			case WifiP2pManager.ERROR:
				message = context.getString(R.string.wifi_p2p_error);
				break;
			default:
				message = context.getString(R.string.this_is_a_bug);
				break;
			}
			Toast.makeText(context, "[AddService] " + message, Toast.LENGTH_SHORT).show();
		}
	};
	WifiP2pManager.ActionListener delLocalServiceActionlistener = new WifiP2pManager.ActionListener() {
		@Override
		public void onSuccess() {
			Toast.makeText(context, "[DelService] Wifi is OK", Toast.LENGTH_SHORT).show();
		}
		@Override
		public void onFailure(int reason) {
			String message;
			switch (reason) {
			case WifiP2pManager.P2P_UNSUPPORTED:
				message = context.getString(R.string.wifi_p2p_unsupported);
				break;
			case WifiP2pManager.BUSY:
				message = context.getString(R.string.wifi_p2p_busy);
				break;
			case WifiP2pManager.ERROR:
				message = context.getString(R.string.wifi_p2p_error);
				break;
			default:
				message = context.getString(R.string.this_is_a_bug);
				break;
			}
			Toast.makeText(context, "[DelService] " + message, Toast.LENGTH_SHORT).show();
		}
	};
	private WifiP2pManager.ChannelListener mChannelListener = new WifiP2pManager.ChannelListener() {
		public void onChannelDisconnected() {
			Toast.makeText(context, "onChannelDisconnected", Toast.LENGTH_SHORT).show();
		}
	};
	
	public P2pNsdHelper(Context _context) {
		context = _context;
		mManager = (WifiP2pManager)_context.getSystemService(Context.WIFI_P2P_SERVICE);
		mChannel = mManager.initialize(_context, _context.getMainLooper(), mChannelListener);
	}
	
	public void createRoom(String _name, String _ip, int _port) {
		HashMap<String, String> txtMap = new HashMap<String, String>();
		txtMap.put("name", _name);
		txtMap.put("ip", _ip);
		txtMap.put("port", String.valueOf(_port));
		mServInfo = WifiP2pDnsSdServiceInfo.newInstance(_name, srvType, txtMap);
		mManager.addLocalService(mChannel, mServInfo, addLocalServiceActionlistener);
	}
	
	public void resetRoom(String _name, String _ip, int _port) {
		HashMap<String, String> txtMap = new HashMap<String, String>();
		txtMap.put("name", _name);
		txtMap.put("ip", _ip);
		txtMap.put("port", String.valueOf(_port));
		mServInfo = WifiP2pDnsSdServiceInfo.newInstance(_name, srvType, txtMap);
		mManager.clearLocalServices(mChannel, new WifiP2pManager.ActionListener() {
			
			@Override
			public void onSuccess() {
				Toast.makeText(context, "[ClearLocalServiceOnSuccess] Wifi is OK", Toast.LENGTH_SHORT).show();
				mManager.addLocalService(mChannel, mServInfo, addLocalServiceActionlistener);
			}
			
			@Override
			public void onFailure(int reason) {
				String message;
				switch (reason) {
				case WifiP2pManager.P2P_UNSUPPORTED:
					message = context.getString(R.string.wifi_p2p_unsupported);
					break;
				case WifiP2pManager.BUSY:
					message = context.getString(R.string.wifi_p2p_busy);
					break;
				case WifiP2pManager.ERROR:
					message = context.getString(R.string.wifi_p2p_error);
					break;
				default:
					message = context.getString(R.string.this_is_a_bug);
					break;
				}
				Toast.makeText(context, "[ClearLocalServiceOnFail] " + message, Toast.LENGTH_SHORT).show();
			}
		});
	}
	
	public void deleteRoom() {
		if (mManager != null)
			mManager.removeLocalService(mChannel, mServInfo, delLocalServiceActionlistener);
	}

	@Override
	protected void finalize() throws Throwable {
		deleteRoom();
		super.finalize();
	}
	
	
}
