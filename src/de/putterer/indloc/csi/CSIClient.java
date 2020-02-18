package de.putterer.indloc.csi;

import de.putterer.indloc.Station;
import de.putterer.indloc.csi.messages.SubscriptionMessage;
import de.putterer.indloc.csi.messages.SubscriptionMessage.SubscriptionOptions;
import de.putterer.indloc.util.Logger;
import lombok.Getter;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Represents a connection + subscription to a csi server
 * The static part contains the general code for sending / receiving messages and handling multiple clients
 */
@Getter
public class CSIClient implements CSIProvider {
	private static final int SERVER_PORT = 9380;
	private static final int CLIENT_PORT = 9381;

	// Message types
	public static final byte TYPE_SUBSCRIBE = 10;
	public static final byte TYPE_UNSUBSCRIBE = 11;
	public static final byte TYPE_CONFIRM_SUBSCRIPTION = 12;
	public static final byte TYPE_CONFIRM_UNSUBSCRIPTION = 13;
	public static final byte TYPE_CSI_INFO = 14;
	
	public static final int MAX_MESSAGE_LENGTH = 65507;

	// Can be used to filter for CSI Info objects cause by ICMP echo
	public static final int DEFAULT_ICMP_PAYLOAD_LENGTH = 124;
	
	private static int SUBSCRIPTION_ID_FACTORY = 0;
	
	private static DatagramSocket socket;
	private static List<CSIClient> clients = new ArrayList<>();
	
	static {
		try {
			socket = new DatagramSocket(CLIENT_PORT);
		} catch(IOException e) {
			System.err.println("Could not open socket for receiving CSI");
			e.printStackTrace();
			System.exit(-1);
		}
		
		Logger.info("Starting csi client on port %d...", CLIENT_PORT);
		new Thread(CSIClient::listener).start();
	}

	/**
	 * listens for incoming messages
	 */
	public static void listener() {
		byte[] buffer = new byte[MAX_MESSAGE_LENGTH];
		while(true) {
			DatagramPacket packet = new DatagramPacket(buffer, MAX_MESSAGE_LENGTH);
			try {
				socket.receive(packet);
			} catch(IOException e) {
				Logger.error("Error while reading from datagram socket");
				e.printStackTrace();
			}
			Logger.trace("Received %d bytes from %s", packet.getLength(), packet.getAddress().getHostAddress());
			for(CSIClient client : clients) {
				if(client.getStation().getIP_ADDRESS().equals(packet.getAddress().getHostAddress())) {
					client.onPacket(packet);
				}
			}
		}
	}

	public static void addClient(CSIClient client) {
		clients.add(client);
		client.subscribe();
	}
	
	public static void removeClient(CSIClient client) {
		clients.remove(client);
		client.unsubscribe();
	}

	private final Station station; // the station this client is connected to
	private boolean connected = false; // whether the subscription was successful
	private final int subscriptionId;
	private final Consumer<CSIInfo> callback; // callback to be called when CSIInfo was received from this station
	private final SubscriptionOptions subscriptionOptions; // the subscription options for this client, e.g. payload length filter

	public CSIClient(Station station, Consumer<CSIInfo> callback, SubscriptionOptions subscriptionOptions) {
		this.station = station;
		this.callback = callback;
		this.subscriptionOptions = subscriptionOptions;
		
		this.subscriptionId = SUBSCRIPTION_ID_FACTORY++;
	}

	/**
	 * Sends a subscription message to the associated station
	 */
	private void subscribe() {
		new Thread(() -> {
			for(int i = 0;i < 10 && !connected;i++) {
				Logger.info("Subscribing to %s, subscription id: %d, attempt %d/10", station.getIP_ADDRESS(), subscriptionId, i + 1);
				send(new SubscriptionMessage(
						subscriptionOptions
					).toBytes()
				);
				try { Thread.sleep(5000); } catch(InterruptedException e) {e.printStackTrace();}
			}
			if(!connected) {
				Logger.warn("Subscription for %s timed out", station.getIP_ADDRESS());
			}
		}).start();
	}

	/**
	 * Sends an unsubscription message to the associated station
	 * BEST EFFORT, the success it not confirmed nor repeated
	 */
	private void unsubscribe() {
		send(new byte[] {TYPE_UNSUBSCRIBE});
		connected = false;
	}

	/**
	 * Called on receiving a datagram packet from the associated station
	 * @param packet
	 */
	private void onPacket(DatagramPacket packet) {
		if(packet.getLength() == 0) {
			return;
		}
		
		switch(packet.getData()[0]) {
		case TYPE_CONFIRM_SUBSCRIPTION: {
			Logger.info("Subscription confirmed by station %s", station.getIP_ADDRESS());
			connected = true;
			break;
		}
		
		case TYPE_CONFIRM_UNSUBSCRIPTION: {
			Logger.info("Unsubscription confirmed by station %s", station.getIP_ADDRESS());
			break;
		}
		
		case TYPE_CSI_INFO: {
			Logger.trace("Got csi from station %s", station.getIP_ADDRESS());
			CSIInfo info = new CSIInfo(ByteBuffer.wrap(packet.getData(), 1, packet.getLength() - 1));
			callback.accept(info);
			break;
		}
		default: {
			Logger.warn("Received packet with unknown type from station %s", station.getIP_ADDRESS());
		}
		}
	}

	/**
	 * sends data to the associated station
	 * @param buffer the data to send
	 */
	private void send(byte buffer[]) {
		try {
			DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
			packet.setAddress(InetAddress.getByName(station.getIP_ADDRESS()));
			packet.setPort(SERVER_PORT);
			socket.send(packet);
		} catch(IOException e) {
			e.printStackTrace();
		}
	}
}
