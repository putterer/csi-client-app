package de.putterer.indloc.acceleration;

import de.putterer.indloc.csi.calibration.AndroidInfo;
import de.putterer.indloc.respiratory.Periodicity;
import de.putterer.indloc.util.Observable;
import lombok.Getter;

import java.time.Duration;
import java.util.LinkedList;
import java.util.List;

public class PeriodicityDetector {
    //TODO: also support non android
    @Getter
    private Observable<Double> currentFrequency = new Observable<>(0.0);

    @Getter
    private final double samplingFrequency;
    @Getter
    private final int slidingWindowSize;
    @Getter
    private final Duration slidingWindowDuration; // not used internally, just for external queries

    private final List<AndroidInfo> history = new LinkedList<>();

    public PeriodicityDetector(double samplingFrequency, Duration slidingWindowDuration) {
        this.samplingFrequency = samplingFrequency;
        this.slidingWindowSize = (int) ((double)slidingWindowDuration.toSeconds() * samplingFrequency);
        this.slidingWindowDuration = slidingWindowDuration;
    }

    public void onData(AndroidInfo info) {
        //TODO: TODO: TODO: I AM TESTING CODE REMOVE ME
        info.getData()[1] = (float) Math.sin(System.currentTimeMillis() / 1000.0 * 2.0 * Math.PI / 60.0 * 15.0) + (float)Math.random() * 1f - 0.5f;

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
        currentFrequency.set(Periodicity.detectSmoothedMLPeriodicity(values, samplingFrequency, 0.0, 10.0));
//        System.out.printf("%.1f%n", Periodicity.detectSmoothedMLPeriodicity(values, samplingFrequency, 0.0, 10.0) * 60.0);
    }
}
