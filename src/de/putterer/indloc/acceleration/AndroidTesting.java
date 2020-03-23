package de.putterer.indloc.acceleration;

import de.putterer.indloc.Station;
import de.putterer.indloc.csi.DataPreview;
import de.putterer.indloc.csi.DataPreview.AndroidEvolutionPreview.AndroidDataType;
import de.putterer.indloc.csi.calibration.AndroidInfo;
import de.putterer.indloc.data.DataClient;
import de.putterer.indloc.data.DataConsumer;
import de.putterer.indloc.util.Logger;

import java.time.Duration;

public class AndroidTesting {

	public static final double SAMPLING_FREQUENCY = 1.0 / 0.08;


	//TODO: implement this better
	private static long lastUpdate = 0;

	public static void main(String args[]) {
		Logger.setLogLevel(Logger.Level.INFO);

		DataPreview preview = new DataPreview(new DataPreview.AndroidEvolutionPreview(
				200.0f,
//				AndroidDataType.X,
				AndroidDataType.Y,
				AndroidDataType.Z
//				AndroidDataType.EUCLIDEAN
		));

		PeriodicityDetector periodicityDetector = new PeriodicityDetector(SAMPLING_FREQUENCY, Duration.ofSeconds(7));


		DataClient client = new DataClient(
				new Station("00:00:00:00:00:00", "192.168.178.28", AndroidInfo.class, null, null),
				new DataConsumer<>(AndroidInfo.class, info -> {
					preview.setData(info);

					//TODO: set value and periodically grab that at sample rate
					if(System.currentTimeMillis() - lastUpdate > 1.0 / SAMPLING_FREQUENCY) {
						lastUpdate = System.currentTimeMillis();

						periodicityDetector.onData(info);
						System.out.printf("%.1f bpm\n", periodicityDetector.getCurrentFrequency().get() * 60.0);
					}

//					System.out.printf("%.2f\n", periodicityDetector.getCurrentFrequency());
//					System.out.printf("X: %.2f, Y: %.2f, Z: %.2f, \n",
//						accelerationInfo.getAcceleration()[0],
//						accelerationInfo.getAcceleration()[1],
//						accelerationInfo.getAcceleration()[2]);
				})
		);

		DataClient.addClient(client);
	}
}
