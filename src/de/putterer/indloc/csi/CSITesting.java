package de.putterer.indloc.csi;

import de.putterer.indloc.Config;
import de.putterer.indloc.Station;
import de.putterer.indloc.csi.messages.SubscriptionMessage.FilterOptions;
import de.putterer.indloc.csi.messages.SubscriptionMessage.SubscriptionOptions;
import de.putterer.indloc.data.DataClient;
import de.putterer.indloc.data.DataConsumer;
import de.putterer.indloc.util.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * FOR TESTING PURPOSES
 * lots of commented out, unstructured, testing code
 */
public class CSITesting {

	private static final ExecutorService pool = Executors.newFixedThreadPool(4);

	//TODO: extract code, restructure/align
	public static void main(String[] args) throws IOException, ClassNotFoundException {
		Logger.setLogLevel(Logger.Level.INFO);
		int groupThreshold = 10;

	    List<DataPreview> previews = new ArrayList<>();
//		previews.add(new DataPreview(new DataPreview.CSIPlotPreview(3, 1)));
//		previews.add(new DataPreview(new DataPreview.SubcarrierPropertyPreview(DataPreview.SubcarrierPropertyPreview.PropertyType.AMPLITUDE, 3, 1)));
//		previews.add(new DataPreview(new DataPreview.SubcarrierPropertyPreview(DataPreview.SubcarrierPropertyPreview.PropertyType.PHASE, 3, 1)));
//		previews.add(new DataPreview(new DataPreview.PhaseDiffPreview(0, 1)));
//      previews.add(new DataPreview(new DataPreview.PhaseDiffPreview(0, 2)));
		previews.add(new DataPreview(new DataPreview.PhaseDiffEvolutionPreview(0, 2, -1, 0, -1, Double.NaN, new int[] {10, 20, 30, 40, 50})));
//		previews.add(new DataPreview(new DataPreview.PhaseDiffVariancePreview(Config.ROOM.getStations()[0], new int[] {SUBCARRIER_AVG, SUBCARRIER_MAX})));

		SubscriptionOptions subscriptionOptions = new SubscriptionOptions(
				new FilterOptions(DataClient.DEFAULT_ICMP_PAYLOAD_LENGTH)
		);

		List<Double> phaseShiftHistory = new LinkedList<>();
		List<CSIInfo[]> shiftedCsiGroup = new LinkedList<>();
		
		for(Station station : Config.ROOM.getStations()) {
			DataClient.addClient(new DataClient(station, subscriptionOptions, new DataConsumer<>(CSIInfo.class, csiInfo -> {
				previews.forEach(p -> p.setData(csiInfo));
				Logger.info("Received message with payload length: %d", csiInfo.getCsi_status().getPayload_len());
//				Logger.warn("Antenna signal strenght: 0: %d., 1: %d, 2: %d",
//						csiInfo.getCsi_status().getRssi_0(),
//						csiInfo.getCsi_status().getRssi_1(),
//						csiInfo.getCsi_status().getRssi_2()
//				);

				// Real time shifting + spotfi processing
//				CSIInfo[] shiftedCsi = new CSIInfo[4];
//				for(int i = 1;i <= 4;i++) {
//					shiftedCsi[i - 1] = new CSIInfo(
//							csiInfo.getClientTimestamp(),
//							csiInfo.getMessageId(),
//							csiInfo.getCsi_status(),
//							PhaseOffset.getByMac(station.getHW_ADDRESS()).shiftMatrix(csiInfo.getCsi_matrix(), PhaseOffset.PhaseOffsetType.CROSSED, i)
//					);
//				}

//				shiftedCsiGroup.add(shiftedCsi);
//				if(shiftedCsiGroup.size() >= groupThreshold) {
//					List<CompletableFuture> instances = new ArrayList<>();
//					for(int i = 0;i < 4;i++) {
//						int finalI = i;
//						CompletableFuture instance = CompletableFuture.runAsync(() -> {
//							CSIInfo[] csi = shiftedCsiGroup.stream().map(c -> c[finalI]).toArray(CSIInfo[]::new);
//							Spotfi.run(csi, finalI + 1, null, null);
//						}, pool);
//						instances.add(instance);
//					}
//
//					instances.forEach(CompletableFuture::join);
//					shiftedCsiGroup.clear();
//				}

				// Phase diff calibration:
//				int rxAnt0 = 0;
//				int rxAnt1 = 1;
//				double phaseDiff = PhaseOffsetCalibration.getPhaseDiff(Collections.singletonList(csiInfo), 0, rxAnt0, rxAnt1);
//				if(phaseDiff == 0.0) {
//					Logger.warn("DISCARDING PHASE SHIFT");
//					return;
//				}
//
//				if((csiInfo.getCsi_status().getRssi_0() > 45 && (rxAnt0 != 0 && rxAnt1 != 0))
//						|| (csiInfo.getCsi_status().getRssi_1() > 45 && (rxAnt0 != 1 && rxAnt1 != 1))
//						|| (csiInfo.getCsi_status().getRssi_2() > 45 && (rxAnt0 != 2 && rxAnt1 != 2))) {
//					Logger.error("WRONG ANTENNA CONNECTED");
//				}
//
//				phaseShiftHistory.add(phaseDiff);
//				Logger.warn("Phase diff %d%d: %f,   Avg: %f", rxAnt0, rxAnt1, Math.toDegrees(phaseDiff), Math.toDegrees(phaseShiftHistory.stream().mapToDouble(d -> d).average().getAsDouble()));
			})));
		}

//		CSIReplay.main(new String[] {
//				"./csiIncreasingAngle/",
//				String.valueOf(true), // activate spotfi
//				"3", //group threshold of 1
//				"", //no plot
//				"3", // 3 rx antennas
//				"1", // 1 tx antenna
//		});
	}
}
