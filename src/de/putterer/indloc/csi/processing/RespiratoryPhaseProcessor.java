package de.putterer.indloc.csi.processing;

import de.putterer.indloc.data.DataInfo;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import static de.putterer.indloc.util.CSIUtil.*;

public class RespiratoryPhaseProcessor {

	//also called by preview, but with one datainfo --> over time effects disregarded in that case
	// trunactedMeanLength depends on samplingRate
	public static double[][] process(int rx1, int rx2, int tx, List<DataInfo> info, int truncatedMeanLength, double truncatedMeanPct) {
		double[][] phase = PhaseExtractor.extract(rx1, rx2, tx, info);   // phase[time][subcarrier]
		int subcarriers = phase[0].length;
		int samples = phase.length;


		Arrays.stream(phase).forEach(phaseOverSubcarriers -> {
//			unwrapPhase(diffs);  BS

			// this interestingly improves the signal
			// it will causes a change on one carrier to influence others as well though
			shift(phaseOverSubcarriers, -mean(phaseOverSubcarriers));
		});

//		unwrapPhase(result);

		// Abort for preview when only processing a single sample
		if(info.size() == 1 || truncatedMeanLength <= 1) {
			return phase;
		}

		// Over time processing

		// Alpha trimmed mean
		int truncatedCount = (int) Math.ceil(truncatedMeanLength * truncatedMeanPct); // the number of samples in the truncated mean

		double[][] alphaTrimmed = new double[samples - truncatedMeanLength][subcarriers];
		for(int carrier = 0;carrier < subcarriers;carrier++) {
			for(int t = 0;t < samples - truncatedMeanLength;t++) {
				List<Double> relevantSamples = new LinkedList<>();
				// Select relevant samples
				for(int i = 0;i < truncatedMeanLength;i++) {
					relevantSamples.add(phase[t + i][carrier]);
				}

				double truncatedMean =
						Arrays.stream(
								unwrapPhase(Arrays.copyOfRange(relevantSamples.stream().mapToDouble(Double::doubleValue).toArray(), 0, truncatedMeanLength))
						)
								.sorted()
								.skip((int) Math.ceil(truncatedCount / 2.0))
								.limit(truncatedMeanLength - truncatedCount)
								.average()
								.orElse(0.0);
				truncatedMean = bound(truncatedMean);

				alphaTrimmed[t][carrier] = truncatedMean;
			}
		}



		// TODO: jump removal
		double[][] result = alphaTrimmed;

		return result;
	}

	public static double[] selectCarrier(double[][] phase, int subcarrier) {
		return Arrays.stream(phase).mapToDouble(p -> p[subcarrier]).toArray();
	}
}
