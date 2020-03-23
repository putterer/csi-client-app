package de.putterer.indloc.data;

import de.putterer.indloc.Station;
import de.putterer.indloc.csi.CSIInfo;
import de.putterer.indloc.csi.calibration.AndroidInfo;
import de.putterer.indloc.csi.messages.SubscriptionMessage;
import de.putterer.indloc.csi.messages.SubscriptionMessage.SubscriptionOptions;
import de.putterer.indloc.util.Logger;
import de.putterer.indloc.util.Observable;
import lombok.Getter;
import lombok.var;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Represents a connection + subscription to a data server, this can be a CSI server or an acceleration server
 * The static part contains the general code for sending / receiving messages and handling multiple clients
 */
@Getter
public class DataClient {
	private static final int SERVER_PORT = 9380;
	private static final int CLIENT_PORT = 9381;

	// Message types
	public static final byte TYPE_SUBSCRIBE = 10;
	public static final byte TYPE_UNSUBSCRIBE = 11;
	public static final byte TYPE_CONFIRM_SUBSCRIPTION = 12;
	public static final byte TYPE_CONFIRM_UNSUBSCRIPTION = 13;
	public static final byte TYPE_CSI_INFO = 14;
	public static final byte TYPE_ACCELERATION_INFO = 20;
	
	public static final int MAX_MESSAGE_LENGTH = 65507;

	// Can be used to filter for CSI Info objects cause by ICMP echo
	public static final int DEFAULT_ICMP_PAYLOAD_LENGTH = 124;
	
	private static int SUBSCRIPTION_ID_FACTORY = 0;
	
	private static DatagramSocket socket;
	private static List<DataClient> clients = new ArrayList<>();
	
	static {
		try {
			socket = new DatagramSocket(CLIENT_PORT);
		} catch(IOException e) {
			System.err.println("Could not open socket for receiving CSI");
			e.printStackTrace();
			System.exit(-1);
		}
		
		Logger.info("Starting csi client on port %d...", CLIENT_PORT);
		new Thread(DataClient::listener).start();
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
			synchronized (clients) {
				for(DataClient client : clients) {
					if(client.getStation().getIP_ADDRESS().equals(packet.getAddress().getHostAddress())) {
						client.onPacket(packet);
					}
				}
			}
		}
	}

	public static DataClient addClient(DataClient client) {
		synchronized (clients) {
			clients.add(client);
			client.subscribe();
		}
		return client;
	}
	
	public static DataClient removeClient(DataClient client) {
		synchronized (clients) {
			clients.remove(client);
			client.unsubscribe();
		}
		return client;
	}

	public static List<DataClient> getClients() {
		return Collections.unmodifiableList(clients);
	}
	public static DataClient getClient(Station station) {
		return clients.stream().filter(c -> c.getStation() == station).findFirst().orElse(null);
	}





	private final Station station; // the station this client is connected to
	private boolean connected = false; // whether the subscription was successful
	private boolean timedOut = false;
	private final int subscriptionId;
	private final DataConsumer<? extends DataInfo>[] consumers; // callback to be called when CSIInfo was received from this station
	private final SubscriptionOptions subscriptionOptions; // the subscription options for this client, e.g. payload length filter

	// only for acceleration clients
	private float[] accelerationCalibration = null;

	private final Observable<Station> statusUpdateCallback = new Observable<>(null);
	private final Observable<Integer> packetsReceived = new Observable<>(0);

	public DataClient(Station station, SubscriptionOptions subscriptionOptions, DataConsumer<? extends DataInfo>... consumers) {
		this.station = station;
		this.consumers = consumers;

		this.subscriptionOptions = Objects.requireNonNullElseGet(
				subscriptionOptions,
				() -> new SubscriptionOptions(new SubscriptionMessage.FilterOptions(0)
		));
		
		this.subscriptionId = SUBSCRIPTION_ID_FACTORY++;
	}

	public DataClient(Station station, DataConsumer<? extends DataInfo>... consumers) {
		this(station, null, consumers);
	}

	/**
	 * Sends a subscription message to the associated station
	 */
	public void subscribe() {
		new Thread(() -> {
			for(int i = 0;i < 10 && !connected;i++) {
				Logger.info("Subscribing to %s, subscription id: %d, attempt %d/10", station.getIP_ADDRESS(), subscriptionId, i + 1);
				send(new SubscriptionMessage(
						subscriptionOptions
					).toBytes()
				);
				try { Thread.sleep(2000); } catch(InterruptedException e) {e.printStackTrace();}
			}
			if(!connected) {
				Logger.warn("Subscription for %s timed out", station.getIP_ADDRESS());
				timedOut = true;
				statusUpdateCallback.set(station);
			}
		}).start();
	}

	/**
	 * Sends an unsubscription message to the associated station
	 * BEST EFFORT, the success it not confirmed nor repeated
	 */
	public void unsubscribe() {
		send(new byte[] {TYPE_UNSUBSCRIBE});
		connected = false;
		timedOut = false;
		statusUpdateCallback.set(station);
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
			if(packet.getLength() > 1) {
				var byteBuffer = ByteBuffer.wrap(packet.getData(), 1, packet.getLength());
				accelerationCalibration = new float[] {byteBuffer.getFloat(), byteBuffer.getFloat(), byteBuffer.getFloat()};
				Logger.info("Received acceleration calibration:  X:%.3f, Y:%.3f, Z:%.3f", accelerationCalibration[0], accelerationCalibration[1], accelerationCalibration[2]);
			}
			connected = true;
			statusUpdateCallback.set(station);
			break;
		}
		
		case TYPE_CONFIRM_UNSUBSCRIPTION: {
			Logger.info("Unsubscription confirmed by station %s", station.getIP_ADDRESS());
			break;
		}
		
		case TYPE_CSI_INFO: {
			Logger.trace("Got csi from station %s", station.getIP_ADDRESS());
			packetsReceived.set(packetsReceived.get() + 1);
			CSIInfo info = new CSIInfo(ByteBuffer.wrap(packet.getData(), 1, packet.getLength() - 1));

			if(station.getActivityDetector() != null) {
				station.getActivityDetector().onCsiInfo(info);
			}

			getApplicableConsumers(CSIInfo.class).forEach(c -> c.accept(info));
			break;
		}

		case TYPE_ACCELERATION_INFO: {
			Logger.trace("Got acceleration info from station %s", station.getIP_ADDRESS());
			packetsReceived.set(packetsReceived.get() + 1);
			AndroidInfo info = new AndroidInfo(ByteBuffer.wrap(packet.getData(), 1, packet.getLength() - 1), accelerationCalibration);

			getApplicableConsumers(AndroidInfo.class).forEach(c -> c.accept(info));
			break;
		}

		default: {
			Logger.warn("Received packet with unknown type %d from station %s", packet.getData()[0], station.getIP_ADDRESS());
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

	private <T> Stream<Consumer<T>> getApplicableConsumers(Class<T> type) {
		return Arrays.stream(consumers).filter(c -> c.getType().getCanonicalName().equals(type.getCanonicalName()))
				.map(c -> (Consumer<T>) c.getConsumer());
	}
}
