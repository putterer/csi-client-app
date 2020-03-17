package de.putterer.indloc.acceleration;

import de.putterer.indloc.csi.calibration.AndroidInfo;
import de.putterer.indloc.respiratory.Periodicity;
import lombok.Getter;

import java.time.Duration;
import java.util.LinkedList;
import java.util.List;

public class AndroidPeriodicityDetector {

    @Getter
    private double currentFrequency = 0.0;

    private final double samplingFrequency;
    private final int slidingWindowSize;

    private final List<AndroidInfo> history = new LinkedList<>();

    public AndroidPeriodicityDetector(double samplingFrequency, Duration slidingWindowDuration) {
        this.samplingFrequency = samplingFrequency;
        this.slidingWindowSize = (int) ((double)slidingWindowDuration.toSeconds() * samplingFrequency);
    }

    public void onData(AndroidInfo info) {
        history.add(info);
        while(history.size() > slidingWindowSize) {
            history.remove(0);
        }

        if(history.size() != slidingWindowSize) {
            return;
        }

        // TODO: maybe don't run that on each info, processing time?
        // process sliding window
        //TODO: how to deal with X, Y? parameter? Max?
        double[] values = history.stream().mapToDouble(e -> e.getData()[1]).toArray();
        currentFrequency = Periodicity.detectMLPeriodicity(values, samplingFrequency, 0.0, 10.0);
    }
}
