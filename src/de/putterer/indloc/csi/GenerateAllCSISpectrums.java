package de.putterer.indloc.csi;

import de.putterer.indloc.util.Logger;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class GenerateAllCSISpectrums {

	public static void main(String args[]) throws IOException {
		List<String> replays = new LinkedList<>();
		for (int angle = -90;angle <= 90;angle += 20) {
			for (int dist = 1;dist <= 4;dist++) {
				replays.add(String.format("2los%02ddeg_dist%d", angle, dist));
			}
		}

		for (String replay : replays) {
			CompletableFuture future = CSIReplay.mainProxy(new String[] {
					replay,
					"lookup_store",
					String.valueOf(1000000),
					"none",
					"3",
					"1"
			});

			future.join();
		}

		Logger.info("All spectrums generated");
	}
}
