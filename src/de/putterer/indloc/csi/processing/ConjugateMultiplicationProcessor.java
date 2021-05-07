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

public class ConjugateMultiplicationProcessor {

    private final int rx1, tx1, rx2, tx2;
    private final int slidingWindowSize;
    private final int timestampCountForAverage;
    private final double stddevThresholdForSamePhaseDetection;

    private final List<PreviousCMEntry> previousData = new LinkedList<>();

    public ConjugateMultiplicationProcessor(int rx1, int tx1, int rx2, int tx2, int slidingWindowSize, int timestampCountForAverage, double stddevThresholdForSamePhaseDetection) {
        this.rx1 = rx1;
        this.tx1 = tx1;
        this.rx2 = rx2;
        this.tx2 = tx2;
        this.slidingWindowSize = slidingWindowSize;
        this.timestampCountForAverage = timestampCountForAverage;
        this.stddevThresholdForSamePhaseDetection = stddevThresholdForSamePhaseDetection;
    }

    public ConjugateMultiplicationProcessor(int rx1, int tx1, int rx2, int tx2) {
        this(rx1, tx1, rx2, tx2, 150, 5, Math.toRadians(20.0));
    }


    public Complex[] process(DataInfo info) {
        while(previousData.size() > slidingWindowSize) {
            previousData.remove(0);
        }

        Complex[] cm = getRawConjugateMultiplicative(info);

        // normalize amplitude
        CSIInfo.Complex rawMean = Arrays.stream(cm).reduce(CSIInfo.Complex::add).orElse(new CSIInfo.Complex(0,0)).scale(1.0 / cm.length);
        double amplitudeVariance = Arrays.stream(cm)
                .map(it -> Math.pow(it.sub(rawMean).getAmplitude(), 2))
                .reduce(Double::sum)
                .orElse(0.0)
                / cm.length;
        double amplitudeDeviation = Math.sqrt(amplitudeVariance);
        double scaleFactor = 2000.0 / amplitudeDeviation; // scale around mean or origin? this scales around origin
        System.out.println("ProcScale" + scaleFactor);
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

            return previous.processed;
        }


        // Add to history
        PreviousCMEntry newCMEntry = new PreviousCMEntry(cm);
        newCMEntry.phaseStddev = phaseStddev;
        previousData.add(newCMEntry);

        // Detect and remove rotation
        Complex mean = Arrays.stream(cm).reduce(CSIInfo.Complex::add).orElse(new CSIInfo.Complex(0,0)).scale(1.0 / cm.length);

        newCMEntry.processed = cm; // TODO: change

        // Average
        Complex[] average = getAverage(newCMEntry.processed);

        return cm;
    }

    public Complex[] getAverage(Complex[] input) {
        Complex[] sum = new Complex[input.length];
        Arrays.fill(sum, new Complex(0, 0));
        for(int i = 0;i < Math.min(timestampCountForAverage, previousData.size());i++) {
            sum = CSIUtil.sum(sum, previousData.get(previousData.size() - 1 - i).processed);
        }
        return CSIUtil.scale(sum, 1.0 / ((double)sum.length));
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
