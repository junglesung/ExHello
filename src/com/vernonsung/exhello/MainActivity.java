package com.vernonsung.exhello;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;

import com.vernonsung.exhello.UdpReceive.PACKET_REPORTER_MESSAGE_WHAT;

import android.support.v4.util.ArrayMap;
import android.support.v7.app.ActionBarActivity;
import android.text.method.ScrollingMovementMethod;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.MulticastLock;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends ActionBarActivity {
	private Button buttonSend;
	private Button buttonStop;
	private Button buttonCount;
	private Button buttonIsLive;
	private Button buttonReceive;
	private Button buttonRoom;
	private Button buttonConnect;
	private Button buttonDisconnect;
	private Button buttonCancelConn;
	private Button buttonDiscover;
	private Button buttonConnInfo;
	private Button buttonGroupInfo;
	private Button buttonPeerInfo;
	private Button buttonDisPeer;
	private Button buttonStopDisPeer;
	private Spinner spinnerWps;
	private EditText editTextIp;
	private EditText editTextPort;
	private EditText editTextMessage;
	private TextView textViewCurrentStatus;
	private TextView textViewReceiveStatus;
	private TextView textViewReceivedMessage;
	private MulticastLock mLock;
	private MulticastSend notifier;
	private WifiP2pManager mManager;
	private WifiP2pManager.Channel mChannel;
	private IntentFilter intentFilter;
	private UdpReceive udpReceive;
	private P2pNsdHelper p2pNsdHelper;
	private WifiDirectBroadcastReceiver mReceiver;
	private int wpsSetupType = WpsInfo.PBC;
	private boolean isRestarting = false;  // Indicate whether activity is restarting 
	
	// InstanceState names
	private static final String EDIT_TEXT_IP_TEXT = "EDIT_TEXT_IP_TEXT";
	private static final String EDIT_TEXT_PORT_TEXT = "EDIT_TEXT_PORT_TEXT";
	private static final String EDIT_TEXT_MESSAGE_TEXT = "EDIT_TEXT_MESSAGE_TEXT";
	private static final String TEXT_VIEW_CURRENT_STATUS_TEXT = "TEXT_VIEW_CURRENT_STATUS_TEXT";
	private static final String TEXT_VIEW_RECEIVE_STATUS_TEXT = "TEXT_VIEW_RECEIVE_STATUS_TEXT";
	private static final String TEXT_VIEW_RECEIVED_MESSAGE_TEXT = "TEXT_VIEW_RECEIVED_MESSAGE_TEXT";
	private static final String IS_SENDING = "IS_SENDING";
	private static final String IS_RECEIVING = "IS_RECEIVING";
	private static final String IS_ROOM_CREATED = "IS_ROOM_CREATED";
	private static final String ROOM_LIST_KEY = "ROOM_LIST_KEY";
	private static final String ROOM_LIST_VALUE = "ROOM_LIST_VALUE";
	private static final String SPINNER_POSITION = "SPINNER_POSITION";
	
    // Use handler to print packets from UDP receiver in TextView
	@SuppressLint("HandlerLeak") private Handler udpPacketHandler = new Handler() {
    	@Override
    	public void handleMessage(Message msg) {
    		String ip;
    		int port;
    		String message;
    		Date now = new Date();
    		if (msg.what == PACKET_REPORTER_MESSAGE_WHAT.ERROR.ordinal()) {
    			message = msg.getData().getString(UdpReceive.PACKET_REPORTER_MESSAGE_ERROR.MESSAGE);
    			textViewReceivedMessage.append("\n" + now.toString() + " " + message);
    		} else if (msg.what == PACKET_REPORTER_MESSAGE_WHAT.PACKET.ordinal()) {
    			ip = msg.getData().getString(UdpReceive.PACKET_REPORTER_MESSAGE_PACKET.IP);
    			port = msg.getData().getInt(UdpReceive.PACKET_REPORTER_MESSAGE_PACKET.PORT);
    			message = msg.getData().getString(UdpReceive.PACKET_REPORTER_MESSAGE_PACKET.MESSAGE);
    			textViewReceivedMessage.append("\n" + now.toString() + " [" + ip + ":" + String.valueOf(port) + "] " + message);
    			// Scroll to bottom
    			int y = textViewReceivedMessage.getLayout().getHeight() - textViewReceivedMessage.getHeight();
    			textViewReceivedMessage.scrollTo(0, y);
    		}
    	}	    	
    };
	private Button.OnClickListener buttonSendListner = new Button.OnClickListener() {
		@Override
		public void onClick(View v) {
			if (notifier == null) {
				try {
					// Check data
					String message = editTextMessage.getText().toString();
					if (message.length() == 0)
						throw new Exception(getString(R.string.message_is_empty));
					String ip = editTextIp.getText().toString();
					if (InetAddress.getByName(ip) == null)
						throw new Exception(getString(R.string.invalid_ip));
					int port = Integer.parseInt(editTextPort.getText().toString());
					if (port < 1 || port > 65535)
						throw new Exception(getString(R.string.invalid_port));
					// Create a sender to send message
					notifier = new MulticastSend(ip, port, message);
					notifier.start();
				} catch (Exception e) {
					e.printStackTrace();
					Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
					notifier = null;
					return;
				}
			} else {
				notifier.setMessage(editTextMessage.getText().toString());
			}

			// Set status
			textViewCurrentStatus.setText(R.string.sending);

		    // Set component enable/disable
		    buttonSend.setEnabled(false);
		    buttonStop.setEnabled(true);
		    editTextMessage.setEnabled(false);
		}
	};
	private Button.OnClickListener buttonStopListner = new Button.OnClickListener() {
		@Override
		public void onClick(View v) {
			if (notifier != null) {
				notifier.setKeepSending(false);
			}
			notifier = null;

			// Set status
			textViewCurrentStatus.setText(R.string.stopped);

		    // Set component enable/disable
		    buttonSend.setEnabled(true);
		    buttonStop.setEnabled(false);
		    editTextMessage.setEnabled(true);
		}
	};
	private Button.OnClickListener buttonCountListner = new Button.OnClickListener() {
		@Override
		public void onClick(View v) {
			if (notifier != null) {
				String ac = String.valueOf(notifier.getCount());
				Toast.makeText(MainActivity.this, ac, Toast.LENGTH_SHORT).show();
			} else {
				Toast.makeText(MainActivity.this, "Not sending", Toast.LENGTH_SHORT).show();
			}
		}
	};
	private Button.OnClickListener buttonIsLiveListner = new Button.OnClickListener() {
		@Override
		public void onClick(View v) {
			if (notifier != null && notifier.isAlive()) {
				Toast.makeText(MainActivity.this, "Sending", Toast.LENGTH_SHORT).show();
			} else {
				Toast.makeText(MainActivity.this, "Stopped", Toast.LENGTH_SHORT).show();
			}
		}
	};
	private Button.OnClickListener buttonReceiveListner = new Button.OnClickListener() {
		@Override
		public void onClick(View v) {
			try {
				// Check data
				String ip = editTextIp.getText().toString();
				if (InetAddress.getByName(ip) == null)
					throw new Exception(getString(R.string.invalid_ip));
				int port = Integer.parseInt(editTextPort.getText().toString());
				if (port < 1 || port > 65535)
					throw new Exception(getString(R.string.invalid_port));
				// Stop existing receiver
				if (udpReceive != null && udpReceive.isAlive()) {
					udpReceive.stopReceiving();
				}
				// Tell receiver to receive the IP and port
				udpReceive = new UdpReceive(ip, port, udpPacketHandler);
				udpReceive.start();
				// Set status
				textViewReceiveStatus.setText(getString(R.string.receiving) + " " + ip + ":" + String.valueOf(port));
			} catch (UnknownHostException e) {
				e.printStackTrace();
				Toast.makeText(MainActivity.this, getString(R.string.invalid_ip), Toast.LENGTH_SHORT).show();
				// Stop existing receiver
				if (udpReceive != null) {
					if (udpReceive.isAlive())
						udpReceive.stopReceiving();
					udpReceive = null;
				}
				return;
			} catch (Exception e) {
				e.printStackTrace();
				Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
				// Stop existing receiver
				if (udpReceive != null) {
					if (udpReceive.isAlive())
						udpReceive.stopReceiving();
					udpReceive = null;
				}
				return;
			}
		}
	};
	private Button.OnClickListener buttonRoomListner = new Button.OnClickListener() {

		@Override
		public void onClick(View arg0) {
			if (p2pNsdHelper == null) {
				p2pNsdHelper = new P2pNsdHelper(MainActivity.this);
				p2pNsdHelper.createRoom(editTextIp.getText().toString(), editTextIp.getText().toString(), Integer.parseInt(editTextPort.getText().toString()));
			} else {
				p2pNsdHelper.resetRoom(editTextIp.getText().toString(), editTextIp.getText().toString(), Integer.parseInt(editTextPort.getText().toString()));
			}
		}
		
	};
	private Button.OnClickListener buttonConnectListner = new Button.OnClickListener() {
		
		@Override
		public void onClick(View arg0) {
			PopupMenu popup = new PopupMenu(MainActivity.this, buttonConnect);
			for (String key : roomList.keySet())
				popup.getMenu().add(key);
			PopupMenu.OnMenuItemClickListener popupListener = new PopupMenu.OnMenuItemClickListener() {
				
				@Override
				public boolean onMenuItemClick(MenuItem item) {
					String key = item.getTitle().toString();
					String value = roomList.get(key);
					Toast.makeText(MainActivity.this, "<" + key + ", " + value + ">", Toast.LENGTH_SHORT).show();
					connectTo(value);
					return false;
				}
			};
			popup.setOnMenuItemClickListener(popupListener);
			popup.show();
		}
		
	};
	private Button.OnClickListener buttonDisconnectListner = new Button.OnClickListener() {

		@Override
		public void onClick(View arg0) {
			disconnect();
		}
		
	};
	private Button.OnClickListener buttonCancelConnListner = new Button.OnClickListener() {
		
		@Override
		public void onClick(View arg0) {
			cancelConn();
		}
		
	};
	private Button.OnClickListener buttonDiscoverListner = new Button.OnClickListener() {

		@Override
		public void onClick(View arg0) {
			restartP2pServiceDiscovery();
		}
		
	};
	private Button.OnClickListener buttonConnInfoListner = new Button.OnClickListener() {
		
		@Override
		public void onClick(View arg0) {
			mManager.requestConnectionInfo(mChannel, new WifiP2pManager.ConnectionInfoListener() {
				
				@Override
				public void onConnectionInfoAvailable(WifiP2pInfo info) {
					try {
						if (info == null) {
							textViewReceivedMessage.append("\n" + new Date().toString() + "[RequestOnConnectionInfoAvailable] " + getString(R.string.wifi_p2p_connection_info_is_null));
							return;
						}
						// Request group owner IP
						InetAddress groupOwnerAddress = info.groupOwnerAddress;
						String message;
						if (groupOwnerAddress != null) {
							message = getString(R.string.group_owner_is) + " " + groupOwnerAddress.toString();
						} else {
							message = getString(R.string.group_owner_is) + " null";
						}
						
						if (info.groupFormed)
							message += ", " + getString(R.string.group_is_formed);
						else
							message += ", " + getString(R.string.group_is_not_formed);
						if (info.isGroupOwner)
							message += ", " + getString(R.string.i_am_group_owner);
						else
							message += ", " + getString(R.string.i_am_not_group_owner);
						textViewReceivedMessage.append("\n" + new Date().toString() + "[RequestOnConnectionInfoAvailable] " + message);
					} catch (Exception e) {
						e.printStackTrace();
						textViewReceivedMessage.append("\n" + new Date().toString() + "[RequestOnConnectionInfoAvailable] " + e.getMessage());
						return;
					}
				}
			});
		}
		
	};
	private Button.OnClickListener buttonGroupInfoListner = new Button.OnClickListener() {
		
		@Override
		public void onClick(View arg0) {
			mManager.requestGroupInfo(mChannel, new WifiP2pManager.GroupInfoListener() {
				
				@Override
				public void onGroupInfoAvailable(WifiP2pGroup group) {
					String message;
					Date now = new Date();
					if (group == null) {
						textViewReceivedMessage.append("\n" + now.toString() + "[RequestOnGroupInfoAvailable] " + getString(R.string.wifi_p2p_network_info_is_null));
						return;
					}
					message = "Interface: " + group.getInterface();
					message += ", NetworkName: " + group.getNetworkName();
					message += ", Passphrase: " + group.getPassphrase();
					ArrayList<WifiP2pDevice> clients = new ArrayList<WifiP2pDevice>(group.getClientList());
					message += ", clients:";
					for (WifiP2pDevice device : clients) {
						message += " <" + device.deviceName;
						message += ", " + device.deviceAddress;
						message += ", " + device.primaryDeviceType;
						message += ", " + device.secondaryDeviceType;
						switch (device.status) {
						case WifiP2pDevice.AVAILABLE:
							message += ", available>";
							break;
						case WifiP2pDevice.CONNECTED:
							message += ", connected>";
							break;
						case WifiP2pDevice.FAILED:
							message += ", failed>";
							break;
						case WifiP2pDevice.INVITED:
							message += ", invited>";
							break;
						case WifiP2pDevice.UNAVAILABLE:
							message += ", unavailable>";
							break;
						default:
							message += ", unknown>";
							break;
						}
					}
					textViewReceivedMessage.append("\n" + now.toString() + "[RequestOnGroupInfoAvailable] " + message);
				}
			});
		}
		
	};
	private Button.OnClickListener buttonPeerInfoListner = new Button.OnClickListener() {
		
		@Override
		public void onClick(View arg0) {
			mManager.requestPeers(mChannel, new WifiP2pManager.PeerListListener() {
				
				@Override
				public void onPeersAvailable(WifiP2pDeviceList peers) {
					String message;
					Date now = new Date();
					if (peers == null) {
						textViewReceivedMessage.append("\n" + now.toString() + "[RequestOnPeersAvailable] " + getString(R.string.wifi_p2p_peers_info_is_null));
						return;
					}
					message = "peers:";
					for (WifiP2pDevice device : peers.getDeviceList()) {
						message += " <" + device.deviceName;
						message += ", " + device.deviceAddress;
						message += ", " + device.primaryDeviceType;
						message += ", " + device.secondaryDeviceType;
						switch (device.status) {
						case WifiP2pDevice.AVAILABLE:
							message += ", available>";
							break;
						case WifiP2pDevice.CONNECTED:
							message += ", connected>";
							break;
						case WifiP2pDevice.FAILED:
							message += ", failed>";
							break;
						case WifiP2pDevice.INVITED:
							message += ", invited>";
							break;
						case WifiP2pDevice.UNAVAILABLE:
							message += ", unavailable>";
							break;
						default:
							message += ", unknown>";
							break;
						}
					}
					textViewReceivedMessage.append("\n" + now.toString() + "[RequestOnPeersAvailable] " + message);
				}
			});
		}
		
	};
	private Button.OnClickListener buttonDisPeerListner = new Button.OnClickListener() {
		
		@Override
		public void onClick(View arg0) {
			startP2pPeerDiscovery();
		}
		
	};
	private Button.OnClickListener buttonStopDisPeerListner = new Button.OnClickListener() {
		
		@Override
		public void onClick(View arg0) {
			stopP2pPeerDiscovery();
		}
		
	};
	private Spinner.OnItemSelectedListener spinnerWpsListner = new Spinner.OnItemSelectedListener() {

		@Override
		public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
			wpsSetupType = position;
		}

		@Override
		public void onNothingSelected(AdapterView<?> arg0) {
			// Nothing
		}
		
	}; 
	/**
	 *  It's here for debug purpose, otherwise it should be in another class
	 */
	private WifiP2pManager.ChannelListener mChannelListener = new WifiP2pManager.ChannelListener() {
		public void onChannelDisconnected() {
			textViewReceivedMessage.append("\n" + new Date().toString() + " [onChannelDisconnected]");
		}
	};
	private ArrayMap<String, String> roomList = new ArrayMap<String, String>();
	private WifiP2pManager.DnsSdServiceResponseListener mDnsSdServiceResponseListener = new WifiP2pManager.DnsSdServiceResponseListener() {

		@Override
		public void onDnsSdServiceAvailable(String instanceName,
				String registrationType, WifiP2pDevice srcDevice) {
			String message = "instanceName: " + instanceName +
					", registrationType: " + registrationType +
					", srcDevice.deviceAddress: " + srcDevice.deviceAddress +
					", srcDevice.deviceName: " + srcDevice.deviceName +
					", srcDevice.primaryDeviceType: " + srcDevice.primaryDeviceType +
					", srcDevice.secondaryDeviceType: " + srcDevice.secondaryDeviceType;
			switch (srcDevice.status) {
			case WifiP2pDevice.CONNECTED:
				message += ", srcDevice.status: CONNECTED";
				break;
			case WifiP2pDevice.AVAILABLE:
				message += ", srcDevice.status: AVAILABLE";
				break;
			case WifiP2pDevice.FAILED:
				message += ", srcDevice.status: FAILED";
				break;
			case WifiP2pDevice.INVITED:
				message += ", srcDevice.status: INVITED";
				break;
			case WifiP2pDevice.UNAVAILABLE:
				message += ", srcDevice.status: UNAVAILABLE";
				break;
			default:
				break;	
			}
			if (srcDevice.isGroupOwner())
				message += ", srcDevice is group owner";
			else
				message += ", srcDevice is not group owner";
			
			// Change UI
//			textViewReceivedMessage.append("\n" + new Date().toString() + " [onDnsSdServiceAvailable]" + message);
			textViewReceivedMessage.append("\n" + new Date().toString() + " [onDnsSdServiceAvailable] " + instanceName);
			buttonConnect.setEnabled(true);

			if (!roomList.containsKey(instanceName)) {
				roomList.put(instanceName, srcDevice.deviceAddress);
				// Request for TXT data
				WifiP2pDnsSdServiceRequest request = WifiP2pDnsSdServiceRequest.newInstance(instanceName, P2pNsdHelper.srvType);
				mManager.addServiceRequest(mChannel, request, new WifiP2pManager.ActionListener() {
					@Override
					public void onSuccess() {
						textViewReceivedMessage.append("\n" + new Date().toString() + " [AddTxtRequestOnSuccess] " + getString(R.string.wifi_p2p_ok));
					}
					@Override
					public void onFailure(int reason) {
						String message = getActionFailReason(reason);
						textViewReceivedMessage.append("\n" + new Date().toString() + " [AddTxtRequestOnFailure] " + message);
					}
				});
			}
		}
		
	};
	private WifiP2pManager.DnsSdTxtRecordListener mDnsSdTxtRecordListener = new WifiP2pManager.DnsSdTxtRecordListener() {
		
		@Override
		public void onDnsSdTxtRecordAvailable(String arg0,
				Map<String, String> arg1, WifiP2pDevice srcDevice) {
			String message = "fullDomainName: " + arg0 +
					", srcDevice.deviceAddress: " + srcDevice.deviceAddress +
					", srcDevice.deviceName: " + srcDevice.deviceName +
					", srcDevice.primaryDeviceType: " + srcDevice.primaryDeviceType +
					", srcDevice.secondaryDeviceType: " + srcDevice.secondaryDeviceType;
			switch (srcDevice.status) {
			case WifiP2pDevice.CONNECTED:
				message += ", srcDevice.status: CONNECTED";
				break;
			case WifiP2pDevice.AVAILABLE:
				message += ", srcDevice.status: AVAILABLE";
				break;
			case WifiP2pDevice.FAILED:
				message += ", srcDevice.status: FAILED";
				break;
			case WifiP2pDevice.INVITED:
				message += ", srcDevice.status: INVITED";
				break;
			case WifiP2pDevice.UNAVAILABLE:
				message += ", srcDevice.status: UNAVAILABLE";
				break;
			default:
				break;	
			}
			if (srcDevice.isGroupOwner())
				message += ", srcDevice is group owner";
			else
				message += ", srcDevice is not group owner";
			// Print TXT data
			message += ", TXT: {";
			for (Entry<String, String> entry : arg1.entrySet()) {
				message += ", <" + entry.getKey() + ": " + entry.getValue() + ">";
			}
			message += "}";
			// Debug: print room list
			message += ", Room:{";
			for (Entry<String, String> entry : roomList.entrySet()) {
				message += ", <" + entry.getKey() + ": " + entry.getValue() + ">";
			}
			message += "}";
			
			// Change UI
//			textViewReceivedMessage.append("\n" + new Date().toString() + " [onDnsSdTxtRecordAvailable]" + message);
			textViewReceivedMessage.append("\n" + new Date().toString() + " [onDnsSdTxtRecordAvailable]" + arg0);
			buttonConnect.setEnabled(true);
			
			if (!roomList.containsKey(arg0)) {
				roomList.put(arg0, srcDevice.deviceAddress);
			}
		}
	};
	
	public class WifiDirectBroadcastReceiver extends BroadcastReceiver {
		private WifiP2pManager nManager;
		private WifiP2pManager.Channel nChannel;
		private MainActivity nActivity;

		private void printNetworkInfo(NetworkInfo networkInfo) {
			Date now = new Date();
			if (networkInfo == null) {
				nActivity.textViewReceivedMessage.append("\n" + now.toString() + "[WIFI_P2P_CONNECTION_CHANGED_ACTION] " + nActivity.getString(R.string.wifi_p2p_network_info_is_null));
				return;
			}
			if (networkInfo.isConnected()) {
				nActivity.textViewReceivedMessage.append("\n" + now.toString() + "[WIFI_P2P_CONNECTION_CHANGED_ACTION] " + nActivity.getString(R.string.wifi_p2p_is_connected));
				nManager.requestConnectionInfo(nChannel, nConnectionInfoListener);
			} else {
				// Get connection state
				switch (networkInfo.getState()) {
				case CONNECTED:
					nActivity.textViewReceivedMessage.append("\n" + now.toString() + "[WIFI_P2P_CONNECTION_CHANGED_ACTION] " + nActivity.getString(R.string.wifi_p2p_is_connected));
					break;
				case CONNECTING:
					nActivity.textViewReceivedMessage.append("\n" + now.toString() + "[WIFI_P2P_CONNECTION_CHANGED_ACTION] " + nActivity.getString(R.string.wifi_p2p_is_connecting));
					break;
				case DISCONNECTED:
					nActivity.textViewReceivedMessage.append("\n" + now.toString() + "[WIFI_P2P_CONNECTION_CHANGED_ACTION] " + nActivity.getString(R.string.wifi_p2p_is_disconnected));
					break;
				case DISCONNECTING:
					nActivity.textViewReceivedMessage.append("\n" + now.toString() + "[WIFI_P2P_CONNECTION_CHANGED_ACTION] " + nActivity.getString(R.string.wifi_p2p_is_disconnecting));
					break;
				case SUSPENDED:
					nActivity.textViewReceivedMessage.append("\n" + now.toString() + "[WIFI_P2P_CONNECTION_CHANGED_ACTION] " + nActivity.getString(R.string.wifi_p2p_is_suspended));
					break;
				default:
					nActivity.textViewReceivedMessage.append("\n" + now.toString() + "[WIFI_P2P_CONNECTION_CHANGED_ACTION] " + nActivity.getString(R.string.wifi_p2p_is_unknown));
					break;
				}
			}
		}
		private void printGroupInfo(WifiP2pGroup groupInfo) {
			String message;
			Date now = new Date();
			if (groupInfo == null) {
				nActivity.textViewReceivedMessage.append("\n" + now.toString() + "[WIFI_P2P_CONNECTION_CHANGED_ACTION] " + nActivity.getString(R.string.wifi_p2p_network_info_is_null));
				return;
			}
			message = "Interface: " + groupInfo.getInterface();
			message += ", NetworkName: " + groupInfo.getNetworkName();
			message += ", Passphrase: " + groupInfo.getPassphrase();
			ArrayList<WifiP2pDevice> clients = new ArrayList<WifiP2pDevice>(groupInfo.getClientList());
			message += ", clients:";
			for (WifiP2pDevice device : clients) {
				message += " <" + device.deviceName;
				message += ", " + device.deviceAddress;
				message += ", " + device.primaryDeviceType;
				message += ", " + device.secondaryDeviceType;
				switch (device.status) {
				case WifiP2pDevice.AVAILABLE:
					message += ", available>";
					break;
				case WifiP2pDevice.CONNECTED:
					message += ", connected>";
					break;
				case WifiP2pDevice.FAILED:
					message += ", failed>";
					break;
				case WifiP2pDevice.INVITED:
					message += ", invited>";
					break;
				case WifiP2pDevice.UNAVAILABLE:
					message += ", unavailable>";
					break;
				default:
					message += ", unknown>";
					break;
				}
			}
			nActivity.textViewReceivedMessage.append("\n" + now.toString() + "[WIFI_P2P_CONNECTION_CHANGED_ACTION] " + message);
		}

		private WifiP2pManager.ConnectionInfoListener nConnectionInfoListener = new WifiP2pManager.ConnectionInfoListener() {
			
			@Override
			public void onConnectionInfoAvailable(WifiP2pInfo info) {
				// Request group owner IP
				InetAddress groupOwnerAddress = info.groupOwnerAddress;
				String message = nActivity.getString(R.string.group_owner_is) + " " + groupOwnerAddress.toString();
				
				if (info.groupFormed)
					message += ", " + nActivity.getString(R.string.group_is_formed);
				else
					message += ", " + nActivity.getString(R.string.group_is_not_formed);
				if (info.isGroupOwner)
					message += ", " + nActivity.getString(R.string.i_am_group_owner);
				else
					message += ", " + nActivity.getString(R.string.i_am_not_group_owner);
				nActivity.textViewReceivedMessage.append("\n" + new Date().toString() + "[onConnectionInfoAvailable] " + message);
			}
		};
		
		private WifiP2pManager.GroupInfoListener nGroupInfoListener = new WifiP2pManager.GroupInfoListener() {
			
			@Override
			public void onGroupInfoAvailable(WifiP2pGroup groupInfo) {
				printGroupInfo(groupInfo);
			}
		};
		
		public WifiDirectBroadcastReceiver(WifiP2pManager _manager, 
				WifiP2pManager.Channel _channel, 
				MainActivity _activity) {
			super();
			nManager = _manager;
			nChannel = _channel;
			nActivity = _activity;
		}
		
		private void wifiP2pStateChangedActionHandler(Intent intent) {
			// Log only
			String message;
			Date now = new Date();
			int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, 0);
			switch (state) {
			case WifiP2pManager.WIFI_P2P_STATE_ENABLED:
				message = nActivity.getString(R.string.wifi_p2p_on);
				break;
			case WifiP2pManager.WIFI_P2P_STATE_DISABLED:
				message = nActivity.getString(R.string.wifi_p2p_off);
				break;
			default:
				message = nActivity.getString(R.string.this_is_a_bug);
				break;
			}
			nActivity.textViewReceivedMessage.append("\n" + now.toString() + "[WIFI_P2P_STATE_CHANGED_ACTION] " + message);
		}
		
		private void wifiP2pPeersChangedActionHandler(Intent intent) {
			// Log only
			String message;
			Date now = new Date();
			message = nActivity.getString(R.string.wifi_p2p_peers_change);
			nActivity.textViewReceivedMessage.append("\n" + now.toString() + "[WIFI_P2P_PEERS_CHANGED_ACTION] " + message);
		}
		
		private void wifiP2pConnectionChangeActionHandler(Intent intent) {
			String message;
			Date now = new Date();
			message = nActivity.getString(R.string.wifi_p2p_connection_change);
			nActivity.textViewReceivedMessage.append("\n" + now.toString() + "[WIFI_P2P_CONNECTION_CHANGED_ACTION] " + message);
			if (mManager == null) {
				nActivity.textViewReceivedMessage.append("\n" + now.toString() + "[WIFI_P2P_CONNECTION_CHANGED_ACTION] " + nActivity.getString(R.string.wifi_p2p_manager_is_null));
				return;
			}
			// Get network info
			NetworkInfo networkInfo = (NetworkInfo) intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
			printNetworkInfo(networkInfo);
			// Get group info
			int currentApiVersion = android.os.Build.VERSION.SDK_INT;
			if (currentApiVersion < android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
				// < 4.3 API 18
				nManager.requestGroupInfo(nChannel, nGroupInfoListener);
			} else {
				// >= 4.3 API 18
				WifiP2pGroup groupInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_GROUP);
				printGroupInfo(groupInfo);
			}
		}
		
		private void wifiP2pThisDeviceChangedActionHandler(Intent intent) {
			// Log only
			String message;
			Date now = new Date();
			message = nActivity.getString(R.string.wifi_p2p_setting_changes);
			nActivity.textViewReceivedMessage.append("\n" + now.toString() + "[WIFI_P2P_THIS_DEVICE_CHANGED_ACTION] " + message);
		}
		
		private void wifiP2pDiscoveryChangedActionHandler(Intent intent) {
			// Log only
			String message;
			Date now = new Date();
			int status = intent.getIntExtra(WifiP2pManager.EXTRA_DISCOVERY_STATE, 0);
			switch (status) {
			case WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED:
				message = nActivity.getString(R.string.wifi_p2p_discovery_started);
				break;
			case WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED:
				message = nActivity.getString(R.string.wifi_p2p_discovery_stopped);
				break;
			default:
				message = nActivity.getString(R.string.this_is_a_bug);
				break;
			}
			nActivity.textViewReceivedMessage.append("\n" + now.toString() + "[WIFI_P2P_DISCOVERY_CHANGED_ACTION] " + message);
		}
		
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			String message;
			Date now = new Date();
			
			if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
				wifiP2pStateChangedActionHandler(intent);
			} else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
				wifiP2pPeersChangedActionHandler(intent);
			} else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
				wifiP2pConnectionChangeActionHandler(intent);
			} else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
				wifiP2pThisDeviceChangedActionHandler(intent);
			} else if (WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION.equals(action)) {
				wifiP2pDiscoveryChangedActionHandler(intent);
			} else {
				// Log only
				message = nActivity.getString(R.string.this_is_a_bug);
				nActivity.textViewReceivedMessage.append("\n" + now.toString() + "[Unknown action] " + message);
			}
		}
		
	}
	
	private String getActionFailReason(int reason) {
		switch (reason) {
		case WifiP2pManager.P2P_UNSUPPORTED:
			return getString(R.string.wifi_p2p_unsupported);
		case WifiP2pManager.BUSY:
			return getString(R.string.wifi_p2p_busy);
		case WifiP2pManager.ERROR:
			return getString(R.string.wifi_p2p_error);
		case WifiP2pManager.NO_SERVICE_REQUESTS:
			return getString(R.string.wifi_p2p_no_service_request);
		default:
			return getString(R.string.this_is_a_bug);
		}
	}
	
	/**
	 *  It's here for debug purpose, otherwise it should be in another class
	 */
	private void initialWifiP2p() {
		mManager = (WifiP2pManager)getSystemService(Context.WIFI_P2P_SERVICE);
		mChannel = mManager.initialize(this, getMainLooper(), mChannelListener);
	    mManager.setDnsSdResponseListeners(mChannel, mDnsSdServiceResponseListener, mDnsSdTxtRecordListener);
	    
		mReceiver = new WifiDirectBroadcastReceiver(mManager, mChannel, this);
		intentFilter = new IntentFilter();
	    intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
	    intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
	    intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
	    intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
	    intentFilter.addAction(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION);
	}
	
	public void startP2pPeerDiscovery() {
		mManager.discoverPeers(mChannel, new WifiP2pManager.ActionListener() {
			
			@Override
			public void onSuccess() {
				textViewReceivedMessage.append("\n" + new Date().toString() + " [DiscoverPeersOnSuccess] " + getString(R.string.wifi_p2p_ok));
			}
			
			@Override
			public void onFailure(int reason) {
				String message = getActionFailReason(reason);
				textViewReceivedMessage.append("\n" + new Date().toString() + " [DiscoverPeersOnSuccess] " + message);
			}
		});
	}
	
	public void stopP2pPeerDiscovery() {
		mManager.stopPeerDiscovery(mChannel, new WifiP2pManager.ActionListener() {
			
			@Override
			public void onSuccess() {
				textViewReceivedMessage.append("\n" + new Date().toString() + " [StopDiscoverPeersOnSuccess] " + getString(R.string.wifi_p2p_ok));
			}
			
			@Override
			public void onFailure(int reason) {
				String message = getActionFailReason(reason);
				textViewReceivedMessage.append("\n" + new Date().toString() + " [StopDiscoverPeersOnSuccess] " + message);
			}
		});
	}
	
	private void startP2pServiceDiscovery() {
		WifiP2pDnsSdServiceRequest request = WifiP2pDnsSdServiceRequest.newInstance(P2pNsdHelper.srvType); 
		mManager.addServiceRequest(mChannel, request, new WifiP2pManager.ActionListener() {
			@Override
			public void onSuccess() {
				textViewReceivedMessage.append("\n" + new Date().toString() + " [AddServiceOnSuccess] " + getString(R.string.wifi_p2p_ok));
				mManager.discoverServices(mChannel, new WifiP2pManager.ActionListener() {
					@Override
					public void onSuccess() {
						textViewReceivedMessage.append("\n" + new Date().toString() + " [DiscoverServiceOnSuccess] " + getString(R.string.wifi_p2p_ok));
					}
					@Override
					public void onFailure(int reason) {
						String message = getActionFailReason(reason);
						textViewReceivedMessage.append("\n" + new Date().toString() + " [DiscoverServiceOnFailure] " + message);
					}
				});
			}
			@Override
			public void onFailure(int reason) {
				String message = getActionFailReason(reason);
				textViewReceivedMessage.append("\n" + new Date().toString() + " [AddServiceOnFailure] " + message);
			}
		});
	}
	
	private void restartP2pServiceDiscovery() {
		if (mManager != null && mChannel != null) {
			if (roomList == null) {
				roomList = new ArrayMap<String, String>();
			} else {
				roomList.clear();
			}
			mManager.clearServiceRequests(mChannel, new WifiP2pManager.ActionListener() {
				
				@Override
				public void onSuccess() {
					textViewReceivedMessage.append("\n" + new Date().toString() + " [ClearServiceOnSuccess] " + getString(R.string.wifi_p2p_ok));
					startP2pServiceDiscovery();
				}
				
				@Override
				public void onFailure(int reason) {
					String message = getActionFailReason(reason);
					textViewReceivedMessage.append("\n" + new Date().toString() + " [ClearServiceOnSuccess] " + message);
				}
			});
		}
	}
	
	private void stopP2pServiceDiscovery() {
		mManager.clearServiceRequests(mChannel, null);
	}
	
	private void connectTo(String address) {
		// Connect to peer
		WifiP2pConfig config = new WifiP2pConfig();
		config.deviceAddress = address;
		config.groupOwnerIntent = 0;
		config.wps.setup = wpsSetupType;
		mManager.connect(mChannel, config, new WifiP2pManager.ActionListener() {
			
			@Override
			public void onSuccess() {
				textViewReceivedMessage.append("\n" + new Date().toString() + " [ConnectonSuccess] " + getString(R.string.wifi_p2p_ok));
			}
			
			@Override
			public void onFailure(int reason) {
				String message = getActionFailReason(reason);
				textViewReceivedMessage.append("\n" + new Date().toString() + " [ConnectonFailure] " + message);
			}
		});
	}
	private void disconnect() {
		if (mManager != null && mChannel != null) {
			mManager.removeGroup(mChannel, new WifiP2pManager.ActionListener() {
				
				@Override
				public void onSuccess() {
					textViewReceivedMessage.append("\n" + new Date().toString() + " [RemoveGroupOnSuccess]");
				}
				
				@Override
				public void onFailure(int reason) {
					String message = getActionFailReason(reason);
					textViewReceivedMessage.append("\n" + new Date().toString() + " [RemoveGroupOnFailure] " + message);
				}
			});
		}
	}
	private void cancelConn() {
		if (mManager != null && mChannel != null) {
			mManager.cancelConnect(mChannel, new WifiP2pManager.ActionListener() {
				
				@Override
				public void onSuccess() {
					textViewReceivedMessage.append("\n" + new Date().toString() + " [CancelConnOnSuccess]");
				}
				
				@Override
				public void onFailure(int reason) {
					String message = getActionFailReason(reason);
					textViewReceivedMessage.append("\n" + new Date().toString() + " [CancelConnOnFailure] " + message);
				}
			});
		}
	}

    @Override
    protected void onCreate(Bundle savedInstanceState) {
    	// Always call the superclass first
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // Get UI components
        buttonSend = (Button)findViewById(R.id.buttonSend);
        buttonStop = (Button)findViewById(R.id.buttonStop);
        buttonCount = (Button)findViewById(R.id.buttonCount);
        buttonIsLive = (Button)findViewById(R.id.buttonIsLive);
        buttonReceive = (Button)findViewById(R.id.buttonReceive);
        buttonRoom = (Button)findViewById(R.id.buttonRoom);
        buttonConnect = (Button)findViewById(R.id.buttonConnect);
        buttonDisconnect = (Button)findViewById(R.id.buttonDisconnect);
        buttonCancelConn = (Button)findViewById(R.id.buttonCancelConn);
        buttonDiscover = (Button)findViewById(R.id.buttonDiscover);
        buttonConnInfo = (Button)findViewById(R.id.buttonConnInfo);
        buttonGroupInfo = (Button)findViewById(R.id.buttonGroupInfo);
        buttonPeerInfo = (Button)findViewById(R.id.buttonPeerInfo);
        buttonDisPeer = (Button)findViewById(R.id.buttonDisPeer);
        buttonStopDisPeer = (Button)findViewById(R.id.buttonStopDisPeer);
        spinnerWps = (Spinner)findViewById(R.id.spinnerWps);
        editTextIp = (EditText)findViewById(R.id.editTextIp);
        editTextPort = (EditText)findViewById(R.id.editTextPort);
        editTextMessage = (EditText)findViewById(R.id.editTextMessage);
        textViewCurrentStatus = (TextView)findViewById(R.id.textViewCurrentStatus);
        textViewReceiveStatus = (TextView)findViewById(R.id.textViewReceiveStatus);
        textViewReceivedMessage = (TextView)findViewById(R.id.textViewReceivedMessage);
        
        // Set listener
        buttonSend.setOnClickListener(buttonSendListner);
        buttonStop.setOnClickListener(buttonStopListner);
        buttonCount.setOnClickListener(buttonCountListner);
        buttonIsLive.setOnClickListener(buttonIsLiveListner);
        buttonReceive.setOnClickListener(buttonReceiveListner);
        buttonRoom.setOnClickListener(buttonRoomListner);
        buttonConnect.setOnClickListener(buttonConnectListner);
        buttonDisconnect.setOnClickListener(buttonDisconnectListner);
        buttonCancelConn.setOnClickListener(buttonCancelConnListner);
        buttonDiscover.setOnClickListener(buttonDiscoverListner);
        buttonConnInfo.setOnClickListener(buttonConnInfoListner);
        buttonGroupInfo.setOnClickListener(buttonGroupInfoListner);
        buttonPeerInfo.setOnClickListener(buttonPeerInfoListner);
        buttonDisPeer.setOnClickListener(buttonDisPeerListner);
        buttonStopDisPeer.setOnClickListener(buttonStopDisPeerListner);
        spinnerWps.setOnItemSelectedListener(spinnerWpsListner);

        // Disable components
        buttonStop.setEnabled(false);
        buttonConnect.setEnabled(false);
        
        // Set scroll bar
        textViewReceivedMessage.setMovementMethod(new ScrollingMovementMethod());
        
        // Set WPS spinner content
		ArrayAdapter<CharSequence> adapterWps = ArrayAdapter.createFromResource(MainActivity.this, R.array.wps_setup_types, android.R.layout.simple_spinner_item);
		adapterWps.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinnerWps.setAdapter(adapterWps);
        
        // Keep screen light on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Let screen light off automatically
//        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
		// Get multicast lock
		WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
	    mLock = wifi.createMulticastLock("multicastLock");
	    
	    // Discover P2P NSD service
	    initialWifiP2p();

		// Debug
		textViewReceivedMessage.append("\nonCreate");
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		// Debug
		Toast.makeText(this, "onSaveInstanceState", Toast.LENGTH_SHORT).show();
		
		outState.putString(EDIT_TEXT_IP_TEXT, editTextIp.getText().toString());
		outState.putString(EDIT_TEXT_PORT_TEXT, editTextPort.getText().toString());
		outState.putString(EDIT_TEXT_MESSAGE_TEXT, editTextMessage.getText().toString());
		outState.putString(TEXT_VIEW_CURRENT_STATUS_TEXT, textViewCurrentStatus.getText().toString());
		outState.putString(TEXT_VIEW_RECEIVE_STATUS_TEXT, textViewReceiveStatus.getText().toString());
		outState.putString(TEXT_VIEW_RECEIVED_MESSAGE_TEXT, textViewReceivedMessage.getText().toString());
		outState.putBoolean(IS_SENDING, (notifier != null));
		outState.putBoolean(IS_RECEIVING, (udpReceive != null));
		outState.putBoolean(IS_ROOM_CREATED, (p2pNsdHelper != null));
		outState.putStringArray(ROOM_LIST_KEY, roomList.keySet().toArray(new String[0]));
		outState.putStringArray(ROOM_LIST_VALUE, roomList.values().toArray(new String[0]));
		outState.putInt(SPINNER_POSITION, spinnerWps.getSelectedItemPosition());

		// Tell onStop() and onDestroy() it's going to restart
		isRestarting = true;
		
		// Always call the superclass so it can save the view hierarchy
		super.onSaveInstanceState(outState);
	}

	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		// Always call the superclass so it can restore the view hierarchy
		super.onRestoreInstanceState(savedInstanceState);
		
		editTextIp.setText(savedInstanceState.getString(EDIT_TEXT_IP_TEXT));
		editTextPort.setText(savedInstanceState.getString(EDIT_TEXT_PORT_TEXT));
		editTextMessage.setText(savedInstanceState.getString(EDIT_TEXT_MESSAGE_TEXT));
		textViewCurrentStatus.setText(savedInstanceState.getString(TEXT_VIEW_CURRENT_STATUS_TEXT));
		textViewReceiveStatus.setText(savedInstanceState.getString(TEXT_VIEW_RECEIVE_STATUS_TEXT));
		textViewReceivedMessage.setText(savedInstanceState.getString(TEXT_VIEW_RECEIVED_MESSAGE_TEXT));
		if (savedInstanceState.getBoolean(IS_SENDING))
			buttonSend.callOnClick();
		if (savedInstanceState.getBoolean(IS_RECEIVING))
			buttonReceive.callOnClick();
		if (savedInstanceState.getBoolean(IS_ROOM_CREATED))
			buttonRoom.callOnClick();
		String[] key = savedInstanceState.getStringArray(ROOM_LIST_KEY);
		String[] value = savedInstanceState.getStringArray(ROOM_LIST_VALUE);
		if (key != null && value != null) {
			for (int i = 0; i < key.length; i++) {
				roomList.put(key[i], value[i]);
			}
		}
		spinnerWps.setSelection(savedInstanceState.getInt(SPINNER_POSITION, 0));

		isRestarting = false;
		
		// Debug
		Toast.makeText(this, "onRestoreInstanceState", Toast.LENGTH_SHORT).show();
	}

	@Override
	protected void onResume() {
		// Always call the superclass method first
		super.onResume();
		
		// Debug
		Toast.makeText(this, "onResume", Toast.LENGTH_SHORT).show();
		
		// Register the broadcast receiver with the intent values to be matched
		registerReceiver(mReceiver, intentFilter);
	}
	
	@Override
	protected void onPause() {
		// Always call the superclass method first
		super.onPause();
		
		// Debug
		Toast.makeText(this, "onPause", Toast.LENGTH_SHORT).show();

		// Unregister the broadcast receiver
		unregisterReceiver(mReceiver);
	}

	@Override
	protected void onStart() {
		// Always call the superclass method first
		super.onStart();
		
		// Debug
		Toast.makeText(this, "onStart", Toast.LENGTH_SHORT).show();
		if (isRestarting)
			Toast.makeText(this, "Restart = TRUE", Toast.LENGTH_SHORT).show();
		else
			Toast.makeText(this, "Restart = FALSE", Toast.LENGTH_SHORT).show();
		
		// Do nothing if it's restarting
		if (isRestarting)
			return;
		
		// Acquire multicast lock
		mLock.acquire();
		
		// Start peers discovery
		startP2pPeerDiscovery();
		
		// Start service discovery
		startP2pServiceDiscovery();
	}

	@Override
	protected void onStop() {
		// Always call the superclass method first
		super.onStop();
		
		// Debug
		Toast.makeText(this, "onStop", Toast.LENGTH_SHORT).show();
		
		// Do nothing if it'll start soon
		if (isRestarting)
			return;
		
		if (notifier != null && notifier.isKeepSending())
			notifier.setKeepSending(false);
		if (udpReceive != null && udpReceive.isAlive())
			udpReceive.stopReceiving();
		if (p2pNsdHelper != null)
			p2pNsdHelper.deleteRoom();
		
		// Stop service discovery
		stopP2pServiceDiscovery();

		// Stop peers discovery
		stopP2pPeerDiscovery();
		
		// Release multicast lock
		mLock.release();
		
		disconnect();
	}
	
	@Override
	protected void onRestart() {
		// Always call the superclass method first
		super.onRestart();

		// Debug
		Toast.makeText(this, "onRestart", Toast.LENGTH_SHORT).show();
	}

	@Override
	protected void onDestroy() {
		// Debug
		textViewReceivedMessage.append("\nonDestroy");
		Toast.makeText(this, "onDestroy", Toast.LENGTH_SHORT).show();

		super.onDestroy();
	}
    
}
