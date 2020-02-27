package de.putterer.indloc.acceleration;

import de.putterer.indloc.Station;
import de.putterer.indloc.csi.DataPreview;
import de.putterer.indloc.csi.DataPreview.AccelerationEvolutionPreview.AccelerationType;
import de.putterer.indloc.csi.calibration.AccelerationInfo;
import de.putterer.indloc.data.DataClient;
import de.putterer.indloc.data.DataConsumer;
import de.putterer.indloc.util.Logger;

import java.util.stream.IntStream;

import static de.putterer.indloc.util.Util.square;

public class AccelerationTesting {

	public static void main(String args[]) {
		DataPreview<AccelerationInfo> preview = new DataPreview<>(new DataPreview.AccelerationEvolutionPreview(
				30.0f,
//				AccelerationType.X
				AccelerationType.Y,
				AccelerationType.Z
//				AccelerationType.EUCLIDEAN
		));

		Logger.setLogLevel(Logger.Level.INFO);
		DataClient client = new DataClient(
				new Station("00:00:00:00:00:00", "192.168.43.1", null),
				new DataConsumer<>(AccelerationInfo.class, accelerationInfo -> {
					double lenSquared = square(accelerationInfo.getAcceleration()[0]) + square(accelerationInfo.getAcceleration()[1]) + square(accelerationInfo.getAcceleration()[2]);
					double len = Math.sqrt(lenSquared);

					StringBuilder s = new StringBuilder();
					IntStream.range(0, (int) Math.round(len * 1)).forEach(_void -> s.append("#"));
//					System.out.println(s.toString());

					preview.setData(accelerationInfo);

//					System.out.printf("X: %.2f, Y: %.2f, Z: %.2f, \n",
//						accelerationInfo.getAcceleration()[0],
//						accelerationInfo.getAcceleration()[1],
//						accelerationInfo.getAcceleration()[2]);
				})
		);

		DataClient.addClient(client);
	}
}
