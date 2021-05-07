package de.putterer.indloc.csi.processing;

import de.putterer.indloc.csi.CSIInfo;
import de.putterer.indloc.csi.CSIInfo.Complex;
import de.putterer.indloc.data.DataInfo;
import de.putterer.indloc.util.CSIUtil;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.IntStream;

public class ConjugateMultiplicationProcessor {

    private static final double PHASE_SHIFT_CORRECTION_THRESHOLD = Math.toRadians(60.0); // minimum threshold for rotation/phase shift detection

    private final int rx1, tx1, rx2, tx2;
    private final int slidingWindowSize; // the size of the cached sliding window
    private final int timestampCountForAverage; // the number of samples to use for the running average
    private final double stddevThresholdForSamePhaseDetection; // threshold for detecting invalid samples where all subcarriers contain the same phase data
    private final double thresholdForOffsetCorrection; // threshold for diff while detecting same samples that have been phase shifted (rotated)

    private final List<PreviousCMEntry> previousData = new LinkedList<>();

    public ConjugateMultiplicationProcessor(int rx1, int tx1, int rx2, int tx2, int slidingWindowSize, int timestampCountForAverage, double stddevThresholdForSamePhaseDetection, double thresholdForOffsetCorrection) {
        this.rx1 = rx1;
        this.tx1 = tx1;
        this.rx2 = rx2;
        this.tx2 = tx2;
        this.slidingWindowSize = slidingWindowSize;
        this.timestampCountForAverage = timestampCountForAverage;
        this.stddevThresholdForSamePhaseDetection = stddevThresholdForSamePhaseDetection;
        this.thresholdForOffsetCorrection = thresholdForOffsetCorrection;
    }

    public ConjugateMultiplicationProcessor(int rx1, int tx1, int rx2, int tx2) {
        this(rx1, tx1, rx2, tx2, 150, 10, Math.toRadians(20.0), 10000.0);
    }


    public Complex[] process(DataInfo info) { // TODO: remove duplicate code from preview
        while(previousData.size() > slidingWindowSize) {
            previousData.remove(0);
        }

        Complex[] cm = getRawConjugateMultiplicative(info);

        // normalize amplitude
        double amplitudeDeviation = CSIUtil.stddev(cm);
        double scaleFactor = 2000.0 / amplitudeDeviation; // scale around mean or origin? this scales around origin

        for(int i = 0;i < cm.length;i++) {
            cm[i] = cm[i].scale(scaleFactor);
        }


        // filter out "straight line" with same phase on all carriers
        double[] phase = Arrays.stream(cm).mapToDouble(Complex::getPhase).toArray();
        double phaseStddev = CSIUtil.stddev(phase);

        double averagePreviousPhaseStddev = previousData.stream().mapToDouble(PreviousCMEntry::getPhaseStddev).average().orElse(0.0);

        if(phaseStddev < stddevThresholdForSamePhaseDetection && phaseStddev < averagePreviousPhaseStddev * 0.66) {
            // assume invalid data, filter
            PreviousCMEntry previous = previousData.get(previousData.size() - 1);
            previousData.add(previous);

            System.out.println("filtering out invalid csi sample");
            return previous.processed;
        }


        // Add to history
        PreviousCMEntry newCMEntry = new PreviousCMEntry(cm);
        newCMEntry.phaseStddev = phaseStddev;
        previousData.add(newCMEntry);

        // Detect and remove rotation
        // ----------------------------
        // compare with previous processed (therefore already rotated)
        // if rotated by mean diff, they match (squared mean diff ampl. between individual carriers), apply rotation

        Complex[] processed = cm;

        if(previousData.size() >= 2) {
            PreviousCMEntry previousCMEntry = previousData.get(previousData.size() - 1 - 1);

            Complex mean = CSIUtil.mean(cm);
            Complex previousMean = CSIUtil.mean(previousCMEntry.processed);
            double phaseOffset = previousMean.getPhase() - mean.getPhase();

            if(phaseOffset > PHASE_SHIFT_CORRECTION_THRESHOLD) {
                System.out.println("Rotation triggered");
                // rotate current
                Complex[] rotated = CSIUtil.shift(cm, phaseOffset);

                // compare with previous
                double rotatedDiff = IntStream.range(0, rotated.length)
                        .mapToDouble(i -> rotated[i].sub(cm[i]).getAmplitude())
                        .average().orElse(Double.MAX_VALUE);

                if(rotatedDiff < thresholdForOffsetCorrection) {
                    System.out.println("correction triggered! diff: " + rotatedDiff);
                    // apply rotation
                    processed = rotated;
                } else {
                    System.out.println("correction not triggered! diff: " + rotatedDiff);
                    processed = cm;
                }
            }
        }

        newCMEntry.processed = processed;

        // Average
        Complex[] average = getAverage(newCMEntry.processed);

        return average;
    }

    public Complex[] getAverage(Complex[] input) {
        Complex[] sum = new Complex[input.length];
        Arrays.fill(sum, new Complex(0, 0));
        int elementCount = Math.min(timestampCountForAverage, previousData.size());
        for(int i = 0;i < elementCount;i++) {
            sum = CSIUtil.sum(sum, previousData.get(previousData.size() - 1 - i).processed);
        }
        return CSIUtil.scale(sum, 1.0 / ((double)elementCount));
    }

    public Complex[] getRawConjugateMultiplicative(DataInfo info) {
        if(! (info instanceof CSIInfo)) {
            throw new IllegalArgumentException("unsupported data info type " + info.getClass());
        }
        CSIInfo csi = (CSIInfo)info;

        int subcarriers = csi.getNumTones();
        List<Complex> result = new ArrayList<>();

        for(int subcarrier = 0;subcarrier < subcarriers;subcarrier++) {
            Complex v1 = csi.getCsi_matrix()[rx1][tx1][subcarrier];
            Complex v2 = csi.getCsi_matrix()[rx2][tx2][subcarrier];

            result.add(v1.prod(v2.conjugate()));
        }

        return result.toArray(new Complex[0]);
    }

    @Data
    @RequiredArgsConstructor
    public class PreviousCMEntry {
        private final Complex[] cm;
        private Complex mean;
        private double phaseStddev;
        private Complex[] processed;
    }

}
