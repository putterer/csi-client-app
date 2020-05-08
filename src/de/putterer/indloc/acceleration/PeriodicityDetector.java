package de.putterer.indloc.acceleration;

import de.putterer.indloc.csi.CSIInfo;
import de.putterer.indloc.csi.calibration.AndroidInfo;
import de.putterer.indloc.csi.processing.RespiratoryPhaseProcessor;
import de.putterer.indloc.data.DataInfo;
import de.putterer.indloc.respiratory.Periodicity;
import de.putterer.indloc.respiratory.SubcarrierSelector;
import de.putterer.indloc.util.Observable;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class PeriodicityDetector {

    private static final Duration SUBCARRIER_SELECTION_INTERVAL = Duration.ofSeconds(5);

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
    @Getter
    private final int truncatedMeanWindowSize;
    @Getter
    private final Duration truncatedMeanWindowDuration;
    @Getter
    private final double truncatedMeanWindowPct;

    @Getter @Setter
    private int subcarrier = 50;
    private Instant lastSubcarrierSelection = Instant.now().minus(SUBCARRIER_SELECTION_INTERVAL);

    private final List<DataInfo> history = new LinkedList<>();

    public PeriodicityDetector(double samplingFrequency, Duration slidingWindowDuration, Duration truncatedMeanWindowDuration, double truncatedMeanWindowPct) {
        this.samplingFrequency = samplingFrequency;
        this.slidingWindowSize = (int) ((double)slidingWindowDuration.toMillis() / 1000.0 * samplingFrequency);
        this.slidingWindowDuration = slidingWindowDuration;
        this.truncatedMeanWindowSize = (int) ((double)truncatedMeanWindowDuration.toMillis() / 1000.0 * samplingFrequency);
        this.truncatedMeanWindowDuration = truncatedMeanWindowDuration;
        this.truncatedMeanWindowPct = truncatedMeanWindowPct;
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

        // process sliding window
        if(info instanceof AndroidInfo) {
            double[] values = history.stream().mapToDouble(e -> ((AndroidInfo)e).getData()[1]).toArray();
            Periodicity.FrequencySpectrum spectrum = Periodicity.detectPeriodicity(values, samplingFrequency);
            currentFrequency.set(spectrum.filter(7.0 / 60.0, 10.0).getQuadraticMLPeriodicity());//TODO: filter parameters
            freqSpectrum.set(spectrum);
        } else if(info instanceof CSIInfo) {

            // Calculates and supplies the respiratory processed phase for one subcarrier when called, may be called in parallel
            Function<Integer, double[]> respiratoryPhaseSupplier = carrier ->
                    RespiratoryPhaseProcessor.selectCarrier(
                            RespiratoryPhaseProcessor.process(
                                    0, 2, 0,
                                    history,
                                    truncatedMeanWindowSize,
                                    truncatedMeanWindowPct
                            ),
                            carrier);

            SubcarrierSelector subcarrierSelector = new SubcarrierSelector(subcarrier, ((CSIInfo) info).getCsi_status().getNum_tones(), respiratoryPhaseSupplier);

            if(Instant.now().isAfter(lastSubcarrierSelection.plus(SUBCARRIER_SELECTION_INTERVAL))) {
                subcarrierSelector.runSelection();
                subcarrier = subcarrierSelector.getSelectedCarrier();

                lastSubcarrierSelection = Instant.now();
            }

            Periodicity.FrequencySpectrum spectrum = Periodicity.detectPeriodicity(subcarrierSelector.getProcessedPhaseForCarrier(subcarrier), samplingFrequency);
            currentFrequency.set(spectrum.filter(7.0 / 60.0, 10.0).getQuadraticMLPeriodicity());//TODO: filter parameters
            freqSpectrum.set(spectrum);
        }
    }

    public boolean isIdle() {
        return Optional.ofNullable(freqSpectrum.get())
                .map(s -> s.getMagnitudesByFrequency().entrySet().stream().mapToDouble(Map.Entry::getValue).max().getAsDouble())
                .map(m -> m < 1.0)
                .orElse(true);
    }
}
