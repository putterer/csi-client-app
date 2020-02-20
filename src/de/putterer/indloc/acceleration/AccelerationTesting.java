package de.putterer.indloc.acceleration;

import de.putterer.indloc.Station;
import de.putterer.indloc.csi.CSIInfo;
import de.putterer.indloc.csi.calibration.AccelerationInfo;
import de.putterer.indloc.data.DataClient;
import de.putterer.indloc.data.DataConsumer;
import de.putterer.indloc.util.Logger;

import java.util.Arrays;
import java.util.stream.IntStream;

public class AccelerationTesting {

	public static void main(String args[]) {
//		try {
//			DatagramSocket socket = new DatagramSocket(7890);
//			DatagramPacket packet = new DatagramPacket(new byte[DataClient.TYPE_SUBSCRIBE], 1);
//			packet.setAddress(InetAddress.getByName("192.168.178.25"));
//			packet.setPort();
//			socket.send(packet);
//		} catch (SocketException e) {
//			e.printStackTrace();
//		}


		Logger.setLogLevel(Logger.Level.INFO);
		DataClient client = new DataClient(
				new Station("00:00:00:00:00:00", "192.168.178.25", null),
				new DataConsumer<AccelerationInfo>(AccelerationInfo.class, accelerationInfo -> {
					double lenSquared = Math.pow(accelerationInfo.getAcceleration()[0], 2) + Math.pow(accelerationInfo.getAcceleration()[1], 2) + Math.pow(accelerationInfo.getAcceleration()[2], 2);
					double len = Math.sqrt(lenSquared);

					StringBuilder s = new StringBuilder();
					IntStream.range(0, (int)Math.round(len * 10)).forEach(_void -> s.append("#"));
					System.out.println(s.toString());

//					System.out.printf("X: %.2f, Y: %.2f, Z: %.2f, \n",
//						accelerationInfo.getAcceleration()[0],
//						accelerationInfo.getAcceleration()[1],
//						accelerationInfo.getAcceleration()[2]);
				})
		);

		DataClient.addClient(client);
	}
}
