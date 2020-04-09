package de.putterer.indloc.csi.processing;

import de.putterer.indloc.csi.CSIInfo;
import de.putterer.indloc.data.DataInfo;

import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static de.putterer.indloc.util.CSIUtil.bound;
import static de.putterer.indloc.util.CSIUtil.unwrapPhase;

public class PhaseExtractor {

	public static double[][] extractFromCSI(int rx1, int rx2, int tx, List<CSIInfo> info) {
		return info.stream().map(csi -> {
			double[] diffs = new double[csi.getCsi_status().getNum_tones()];

			double[] rx1Phase = Arrays.stream(csi.getCsi_matrix()[rx1][0]).mapToDouble(CSIInfo.Complex::getPhase).toArray();
			double[] rx2Phase = Arrays.stream(csi.getCsi_matrix()[rx2][0]).mapToDouble(CSIInfo.Complex::getPhase).toArray();
			unwrapPhase(rx1Phase);
			unwrapPhase(rx2Phase);
//			previousPhaseMean[0] = timeUnwrapped(rx1Phase, previousPhaseMean[0]); not really necessary
//			previousPhaseMean[1] = timeUnwrapped(rx2Phase, previousPhaseMean[1]); not really necessary

			for(int i = 0;i < diffs.length;i++) {
				double diff = rx1Phase[i] - rx2Phase[i];
				diffs[i] = bound(diff);
			}

			return diffs;
		}).toArray(double[][]::new);
	}

	public static double[][] extract(int rx1, int rx2, int tx, List<DataInfo> info) {
		if(! info.stream().allMatch(i -> i instanceof CSIInfo)) {
			throw new InvalidParameterException("List must just contain CSI info");
		}
		List<CSIInfo> csi = new ArrayList<>();
		info.stream().map(i -> (CSIInfo)i).forEach(csi::add);

		return extractFromCSI(rx1, rx2, tx, csi);
	}
}
