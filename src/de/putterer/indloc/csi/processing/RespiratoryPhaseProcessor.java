package de.putterer.indloc.csi.processing;

import de.putterer.indloc.data.DataInfo;

import java.util.Arrays;
import java.util.List;

import static de.putterer.indloc.util.CSIUtil.mean;
import static de.putterer.indloc.util.CSIUtil.shift;

public class RespiratoryPhaseProcessor {

	public static double[][] process(int rx1, int rx2, int tx, List<DataInfo> info) {
		double[][] phase = PhaseExtractor.extract(rx1, rx2, tx, info);   // phase[time][subcarrier]

		Arrays.stream(phase).forEach(phaseOverSubcarriers -> {
//			unwrapPhase(diffs);  BS
			shift(phaseOverSubcarriers, -mean(phaseOverSubcarriers));
		});

//		unwrapPhase(result);


		//TODO: unwrap over time



		return phase;
	}

	public static double[] selectCarrier(double[][] phase, int subcarrier) {
		return Arrays.stream(phase).mapToDouble(p -> p[subcarrier]).toArray();
	}
}
