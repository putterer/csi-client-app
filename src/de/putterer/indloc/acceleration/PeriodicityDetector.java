package de.putterer.indloc.acceleration;

import de.putterer.indloc.csi.CSIInfo;
import de.putterer.indloc.csi.calibration.AndroidInfo;
import de.putterer.indloc.csi.processing.RespiratoryPhaseProcessor;
import de.putterer.indloc.data.DataInfo;
import de.putterer.indloc.respiratory.Periodicity;
import de.putterer.indloc.util.Observable;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class PeriodicityDetector {
    //TODO: also support non android
    @Getter
    private final Observable<Double> currentFrequency = new Observable<>(0.0);
    @Getter
    private final Observable<Periodicity.FrequencySpectrum> freqSpectrum = new Observable<>(null);

    @Getter
    private final double samplingFrequency;
    @Getter
    private final int slidingWindowSize;
    @Getter
    private final Duration slidingWindowDuration; // not used internally, just for external queries

    @Getter @Setter
    private int subcarrier = 10;

    private final List<DataInfo> history = new LinkedList<>();

    public PeriodicityDetector(double samplingFrequency, Duration slidingWindowDuration) {
        this.samplingFrequency = samplingFrequency;
        this.slidingWindowSize = (int) ((double)slidingWindowDuration.toSeconds() * samplingFrequency);
        this.slidingWindowDuration = slidingWindowDuration;
    }

    public void onData(DataInfo info) {
        if(! history.stream().allMatch(i -> i.getClass() == info.getClass())) {
            throw new RuntimeException("Received data info of wrong type");
        }
        synchronized (history) {
            history.add(info);
            while(history.size() > slidingWindowSize) {
                history.remove(0);
            }
        }

        if(history.size() != slidingWindowSize) {
            return;
        }

        // TODO: maybe don't run that on each info, processing time?
        // process sliding window
        if(info instanceof AndroidInfo) {
            //TODO: how to deal with X, Y? parameter? Max?
            double[] values = history.stream().mapToDouble(e -> ((AndroidInfo)e).getData()[1]).toArray();
            Periodicity.FrequencySpectrum spectrum = Periodicity.detectPeriodicity(values, samplingFrequency);
            currentFrequency.set(spectrum.filter(0.0, 10.0).getQuadraticMLPeriodicity());//TODO: filter parameters
            freqSpectrum.set(spectrum);
        } else if(info instanceof CSIInfo) {
            double[] values = RespiratoryPhaseProcessor.selectCarrier(RespiratoryPhaseProcessor.process(0, 2, 0, history), subcarrier);
            Periodicity.FrequencySpectrum spectrum = Periodicity.detectPeriodicity(values, samplingFrequency);
            currentFrequency.set(spectrum.filter(0.0, 10.0).getQuadraticMLPeriodicity());//TODO: filter parameters
            freqSpectrum.set(spectrum);
        }
    }

    public boolean isIdle() {
        return Optional.ofNullable(freqSpectrum.get())
                .map(s -> s.getMagnitudesByFrequency().entrySet().stream().mapToDouble(Map.Entry::getValue).max().getAsDouble())
                .map(m -> m < 2.0)
                .orElse(true);
    }
}
