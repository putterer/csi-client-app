package de.putterer.indloc.respiratory;

import lombok.Data;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.IntStream;

import static de.putterer.indloc.util.CSIUtil.mean;
import static de.putterer.indloc.util.CSIUtil.shift;

public class DFTPeriodicity {

    private static FastFourierTransformer transformer = new FastFourierTransformer(DftNormalization.STANDARD);

    public static FrequencySpectrum detectPeriodicity(double[] values, double samplingFreq) {
        //shift values by mean
        double mean = mean(values);
        values = Arrays.copyOf(values, values.length);
        shift(values, -mean);

        values = Arrays.copyOf(values, getNextPowerOfTwo(values.length));

        //TODO: test what happens with padding
        Complex[] bins = transformer.transform(values, TransformType.FORWARD);

        double binSpacing = getBinSpacingPowerOfTwo(samplingFreq, bins.length); // half of the values in the array are discarded


        double[] magnitudeByBin = Arrays.stream(bins, 1, bins.length / 2)
                .mapToDouble(c -> Math.sqrt(c.getReal() * c.getReal() + c.getImaginary() * c.getImaginary())).toArray();

        Map<Double, Double> magnitudeByFrequency = new HashMap<>();
        for(int i = 0;i < magnitudeByBin.length;i++) {
            magnitudeByFrequency.put((i + 1) * binSpacing, magnitudeByBin[i]);
        }

        return new FrequencySpectrum(magnitudeByFrequency, binSpacing);
    }

    @Data
    public static class FrequencySpectrum {
        private final Map<Double, Double> magnitudesByFrequency;
        private final double binSpacing;

        public FrequencySpectrum filter(double minFreq, double maxFreq) {
            Map<Double, Double> filteredMagnitudes = new HashMap<>();
            magnitudesByFrequency.entrySet().stream()
                    .filter(e -> e.getKey() >= minFreq && e.getKey() <= maxFreq)
                    .forEach(e -> filteredMagnitudes.put(e.getKey(), e.getValue()));
            return new FrequencySpectrum(filteredMagnitudes, binSpacing);
        }

        public double getMLFreq() {
            return magnitudesByFrequency.entrySet().stream().max(Comparator.comparingDouble(Map.Entry::getValue)).get().getKey();
        }

        // https://ccrma.stanford.edu/~jos/sasp/Quadratic_Interpolation_Spectral_Peaks.html
        public double getQuadraticMLPeriodicity() {
            var maxEntry = magnitudesByFrequency.entrySet().stream().max(Comparator.comparingDouble(Map.Entry::getValue)).get();
            var lowerEntry = magnitudesByFrequency.entrySet().stream().filter(e -> e.getKey() < maxEntry.getKey()).max(Comparator.comparingDouble(Map.Entry::getKey)).orElse(maxEntry);
            var upperEntry = magnitudesByFrequency.entrySet().stream().filter(e -> e.getKey() > maxEntry.getKey()).min(Comparator.comparingDouble(Map.Entry::getKey)).orElse(maxEntry);

            double offset = (upperEntry.getValue() - lowerEntry.getValue()) / (2.0 * (maxEntry.getValue() * 2.0 - lowerEntry.getValue() - upperEntry.getValue()));
            return maxEntry.getKey() + offset * binSpacing;
        }

        //TODO: https://dspguru.com/dsp/howtos/how-to-interpolate-fft-peak/

        public double getLinearMLPeriodicity() {
            var maxEntry = magnitudesByFrequency.entrySet().stream().max(Comparator.comparingDouble(Map.Entry::getValue)).get();

            //this is inefficient, should have stored the entire transform
            var lowerEntry = magnitudesByFrequency.entrySet().stream().filter(e -> e.getKey() < maxEntry.getKey()).max(Comparator.comparingDouble(Map.Entry::getKey)).orElse(maxEntry);
            var upperEntry = magnitudesByFrequency.entrySet().stream().filter(e -> e.getKey() > maxEntry.getKey()).min(Comparator.comparingDouble(Map.Entry::getKey)).orElse(maxEntry);
            return (maxEntry.getKey() * maxEntry.getValue() + lowerEntry.getKey() * lowerEntry.getValue() + upperEntry.getKey() * upperEntry.getValue())
                    / (maxEntry.getValue() + lowerEntry.getValue() + upperEntry.getValue());
        }
    }

    public static double getBinSpacingPowerOfTwo(double samplingFreq, int bins) {
        return samplingFreq / 2.0 / (bins / 2.0);
    }

    public static double getBinSpacing(double samplingFreq, int inputSize) {
        return getBinSpacingPowerOfTwo(samplingFreq, getNextPowerOfTwo(inputSize));
    }

    public static int getNextPowerOfTwo(int val) {
        return (int)Math.pow(2, Math.ceil(Math.log(val) / Math.log(2)));
    }

    // ------------------------------------------
    // Testing code below
    // ------------------------------------------

    public static void main(String args[]) {
        FastFourierTransformer transformer = new FastFourierTransformer(DftNormalization.STANDARD);

        double testingFrequency = 1.7;

        Function<Double, Double> f = (x) -> {
            double sum = 0;
//            for(double i = 1;i <= 5;i++) {
//                sum += i * Math.sin(i * 2.0 * Math.PI * x);
//            }
            sum += Math.sin(2.0 * Math.PI * x * testingFrequency);
            return sum;
        };

        double sampleFrequency = 10; // Hz

        double[] values = IntStream.range(0, 80).mapToDouble(d -> d / sampleFrequency).map(f::apply).toArray();
        values = Arrays.copyOf(values, (int)Math.pow(2, Math.ceil(Math.log(values.length) / Math.log(2)))); // padding to power of 2
        Arrays.stream(values).forEach(System.out::println);
        System.out.println("\n\n\n");

        // first frequency: constant offset, ignore
        // 1 -> n/2, afterwards: frequencies just mirrored (for real valued input)
        // real/imaginary output -> cos/sin --> look for magnitude
        // bin freq in freq domain is:   bin_id * sampleFreq / 2.0 / (N / 2)
        Complex[] freqs = transformer.transform(values, TransformType.FORWARD);
        System.out.println("Frequencies:" +  freqs.length);

        double binSpacing = sampleFrequency / 2.0 / (freqs.length / 2.0);
        System.out.println("Bin spacing: " + binSpacing);

        for (int i = 1; i < freqs.length / 2; i++) {
            Complex c = freqs[i];
            double magnitude = Math.sqrt(c.getReal() * c.getReal() + c.getImaginary() + c.getImaginary());
            System.out.printf("%d, %.2f Hz: %.3f\n", i, i * binSpacing, magnitude);
        }

        System.out.println("\n\n" + detectPeriodicity(values, sampleFrequency).filter(0.0, 20.0).getMLFreq());
    }
}
