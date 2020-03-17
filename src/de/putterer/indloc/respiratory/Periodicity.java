package de.putterer.indloc.respiratory;

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

public class Periodicity {

    private static FastFourierTransformer transformer = new FastFourierTransformer(DftNormalization.STANDARD);

    public static Map<Double, Double> detectPeriodicity(double[] values, double samplingFreq) {
        values = Arrays.copyOf(values, (int)Math.pow(2, Math.ceil(Math.log(values.length) / Math.log(2))));

        //TODO: test what happens with padding
        Complex[] bins = transformer.transform(values, TransformType.FORWARD);

        double binSpacing = samplingFreq / 2.0 / (bins.length / 2.0); // half of the values in the array are discarded

        double[] magnitudeByBin = Arrays.stream(bins, 1, bins.length / 2)
                .mapToDouble(c -> Math.sqrt(c.getReal() * c.getReal() + c.getImaginary() * c.getImaginary())).toArray();

        Map<Double, Double> magnitudeByFrequency = new HashMap<>();
        for(int i = 0;i < magnitudeByBin.length;i++) {
            magnitudeByFrequency.put((i + 1) * binSpacing, magnitudeByBin[i]);
        }

        return magnitudeByFrequency;
    }

    public static Map<Double, Double> detectPeriodicity(double[] values, double samplingFreq, double minFreq, double maxFreq) {
        Map<Double, Double> filteredMagnitudes = new HashMap<>();
        detectPeriodicity(values, samplingFreq).entrySet().stream()
                .filter(e -> e.getKey() >= minFreq && e.getKey() <= maxFreq)
                .forEach(e -> filteredMagnitudes.put(e.getKey(), e.getValue()));
        return filteredMagnitudes;
    }

    public static double detectMLPeriodicity(double[] values, double samplingFreq, double minFreq, double maxFreq) {
        return detectPeriodicity(values, samplingFreq, minFreq, maxFreq).entrySet()
                .stream().max(Comparator.comparingDouble(Map.Entry::getValue)).get().getKey();
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

        System.out.println("\n\n" + detectMLPeriodicity(values, sampleFrequency, 0.0, 20.0));
    }
}
