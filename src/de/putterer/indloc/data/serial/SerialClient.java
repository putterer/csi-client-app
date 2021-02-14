package de.putterer.indloc.data.serial;

import com.fazecast.jSerialComm.SerialPort;
import de.putterer.indloc.Station;
import de.putterer.indloc.data.DataClient;
import de.putterer.indloc.data.DataConsumer;
import de.putterer.indloc.data.DataInfo;
import de.putterer.indloc.util.Logger;

import java.net.DatagramPacket;

public class SerialClient extends DataClient {

	private final String portDescriptor;
	private SerialPort serialPort;
	private Thread scannerThread;

	public SerialClient(Station station, DataConsumer<? extends DataInfo>... consumers) {
		super(station, consumers);
		this.portDescriptor = station.getIP_ADDRESS();
	}

	@Override
	public void subscribe() {
		serialPort = SerialPort.getCommPort(portDescriptor);

		serialPort.setBaudRate(115200);
		serialPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 0, 0);
		serialPort.openPort();

		if(serialPort.isOpen()) {
			scannerThread = new Thread(this::scannerThread);
			scannerThread.start();
			connected = true;
			timedOut = false;
		} else {
			connected = false;
			timedOut = true;
		}

		Logger.info("Attempted to subscribe to %s, isOpen: %s", portDescriptor, String.valueOf(serialPort.isOpen()));

		statusUpdateCallback.set(station);
	}

	public void scannerThread() {
		while(serialPort.isOpen() && ! Thread.interrupted()) {
			String s = serialReadLine(serialPort); // TODO is this realtime? could use serialPort.read() instead
			onLineRead(s);
		}
		Logger.warn("Serial port %s no longer open, stopping scanner thread, interrupted: %s", portDescriptor, String.valueOf(Thread.interrupted()));

		connected = false;
		timedOut = false;
		serialPort.closePort();
		serialPort = null;
		statusUpdateCallback.set(station);
	}

	protected void onLineRead(String line) {
		// override in subclass
	}

	@Override
	protected void onPacket(DatagramPacket packet) {

	}

	@Override
	public void unsubscribe() {
		scannerThread.interrupt();
	}

	@Override
	protected void send(byte buffer[]) {
		throw new UnsupportedOperationException("Cannot send to serial device at the moment");
	}

	public static String serialReadLine(SerialPort serialPort) {
		StringBuilder sb = new StringBuilder();
		byte[] data = new byte[1];
		while(serialPort.isOpen()) {
			int read = serialPort.readBytes(data, 1);
			if(read == 0) {
				continue;
			}

			if(data[0] == '\r') {
				break;
			}

			sb.append((char)data[0]);
		}
		serialPort.readBytes(data, 1); // read \n
		return sb.toString();
	}
}
