package de.putterer.indloc.data.ssh;

import de.putterer.indloc.Station;
import de.putterer.indloc.csi.atheros.AthCSIInfo;
import de.putterer.indloc.data.DataClient;
import de.putterer.indloc.data.DataConsumer;
import de.putterer.indloc.data.DataInfo;
import de.putterer.indloc.util.Logger;

import java.io.*;
import java.net.DatagramPacket;
import java.nio.ByteBuffer;
import java.util.Base64;

public class SSHDataClient extends DataClient {

	public SSHDataClient(Station station, DataConsumer<? extends DataInfo>... consumers) {
		super(station, consumers);
	}

	@Override
	public void subscribe() {
//		serialPort = SerialPort.getCommPort(portDescriptor);
//
//		serialPort.setBaudRate(baudRate);
//		serialPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 0, 0);
//		serialPort.openPort();
//
//		if(serialPort.isOpen()) {
//			scannerThread = new Thread(this::scannerThread);
//			scannerThread.start();
//			connected = true;
//			timedOut = false;
//		} else {
//			connected = false;
//			timedOut = true;
//		}

		ProcessBuilder processBuilder = new ProcessBuilder();
		processBuilder.command("sshpass", "-p", "indloc", "ssh", "root@" + station.getIP_ADDRESS(), "sh -l -c \"killall prog;./prog --stdout\"");
		Logger.trace("Starting ssh process with command: " + String.join(" ", processBuilder.command()));
		try {
			Process process = processBuilder.start();
			new Thread(() -> scannerThread(process)).start();
			Logger.info("Attempted to subscribe to %s using SSH, ssh process alive: %s", station.getIP_ADDRESS(), String.valueOf(process.isAlive()));

			statusUpdateCallback.set(station);
		} catch (IOException e) {
			Logger.error("Couldn't start ssh process");
			e.printStackTrace();
			statusUpdateCallback.set(station);
			return;
		}
	}

	public void scannerThread(Process process) {
		BufferedReader stdout = new BufferedReader(new InputStreamReader(process.getInputStream()));
		BufferedWriter stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
		try {
			while (/*process.isAlive() &&*/ !Thread.interrupted()) {
				String line = stdout.readLine();
				if(line == null) {
					break;
				}

				if(line.contains("Starting CSI server") || line.contains("Dumping (atheros) csi data to stdout")) {
					connected = true;
					statusUpdateCallback.set(station);
				}

				if(line.startsWith("<athCSI>")) {
					StringBuilder sb = new StringBuilder(line);
					while(true) {
						line = stdout.readLine();
//						System.out.println(line);
						sb.append(line);
						if(line.endsWith("</athCSI>")) {
							break;
						}
					}

					String csiData = sb.toString().replace("<athCSI>", "").replace("</athCSI>", "");
					byte[] data = Base64.getDecoder().decode(csiData);

					if(data[0] != TYPE_ATH_CSI_INFO) {
						Logger.warn("Received SSH payload doesn't match expected data type");
					}

					AthCSIInfo info = new AthCSIInfo(ByteBuffer.wrap(data, 1, data.length - 1));
					getApplicableConsumers(AthCSIInfo.class).forEach(c -> c.accept(info));
				}
			}
		} catch (IOException e) {
			Logger.error("Error while communicating with ssh processing, assuming stopped", e);
		}

		if(process.isAlive()) {
			process.destroyForcibly();
		}
		Logger.warn("SSH process is no longer running, stopping scanner thread, interrupted: %s", String.valueOf(Thread.interrupted()));

		connected = false;
		timedOut = false;
		statusUpdateCallback.set(station);
	}

	@Override
	protected void onPacket(DatagramPacket packet) {

	}

	@Override
	public void unsubscribe() {

	}

	@Override
	protected void send(byte buffer[]) {
		throw new UnsupportedOperationException("Cannot send to ssh device at the moment");
	}
}
