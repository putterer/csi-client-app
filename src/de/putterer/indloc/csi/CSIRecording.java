package de.putterer.indloc.csi;

import de.putterer.indloc.Config;
import de.putterer.indloc.Station;
import de.putterer.indloc.csi.messages.SubscriptionMessage.FilterOptions;
import de.putterer.indloc.csi.messages.SubscriptionMessage.SubscriptionOptions;
import de.putterer.indloc.data.DataClient;
import de.putterer.indloc.data.DataConsumer;
import de.putterer.indloc.util.FileUtils;
import de.putterer.indloc.util.Logger;
import de.putterer.indloc.util.serialization.Serialization;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Records incoming CSI from a CSI client
 */
public class CSIRecording {
	public static int recordedPackets = 0;

	public static void main(String args[]) {
		if(args.length == 0) {
			System.err.println("Missing target folder");
//			return;

			System.err.println("Running with default configuration...");
			args = new String[] {
					"2los90deg_dist4",
					String.valueOf(DataClient.DEFAULT_ICMP_PAYLOAD_LENGTH),
					String.valueOf(100)
			};
		}
		
		Path folder = Paths.get(args[0]);
		FileUtils.deleteRecursiveIfExists(folder);
		
		try {
			Files.createDirectory(folder);
		} catch (IOException e) {
			Logger.error("Could not create recording directory");
			e.printStackTrace();
			System.exit(-1);
		}
		
		try {
			Serialization.serialize(folder.resolve("room.cfg"), Config.ROOM);
		} catch (IOException e) {
			e.printStackTrace();
			Logger.error("Could not save room settings");
			System.exit(-1);
		}
		
		int payloadLen = 0;
		if(args.length >= 2) {
			payloadLen = Integer.parseInt(args[1]);
		}

		int packetLimit = args.length >= 3 ? Integer.parseInt(args[2]) : Integer.MAX_VALUE;
		
		SubscriptionOptions subscriptionOptions = new SubscriptionOptions(
				new FilterOptions(payloadLen)
		);
		
		DataPreview preview = new DataPreview(new DataPreview.CSIPlotPreview(3, 3));

		recordedPackets = 0;
		for(Station station : Config.ROOM.getStations()) {
			DataClient.addClient(new DataClient(station, subscriptionOptions, new DataConsumer<CSIInfo>(CSIInfo.class, csiInfo -> {
				try {
					Serialization.save(folder.resolve(station.getHW_ADDRESS() + "-" + csiInfo.getMessageId() + ".csi"), csiInfo);
				} catch (IOException e) {
					Logger.error("Couldn't save CSI from %s", station.getIP_ADDRESS());
					e.printStackTrace();
				}
				preview.setData(csiInfo);
				Logger.debug("Recorded message %d", csiInfo.getMessageId());

				if(recordedPackets++ >= packetLimit) {
					Logger.info("Captured %d packets, terminating.", recordedPackets);
					System.exit(0);
				}
			})));
			Logger.info("Registering station %s", station.getIP_ADDRESS());
		}
		
		Logger.info("Recording CSI ...");
	}
}
