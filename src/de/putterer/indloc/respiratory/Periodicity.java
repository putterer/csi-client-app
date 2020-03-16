package de.putterer.indloc.respiratory;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;

import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.IntStream;

public class Periodicity {

    public static void main(String args[]) {
        FastFourierTransformer transformer = new FastFourierTransformer(DftNormalization.STANDARD);

        Function<Double, Double> f = (x) -> {
            double sum = 0;
            for(double i = 1;i <= 5;i++) {
                sum += i * Math.sin(i * 2.0 * Math.PI * x);
            }
            return sum;
        };

        double sampleFrequency = 10; // Hz

        double[] values = IntStream.range(0, 128).mapToDouble(d -> d / sampleFrequency).map(f::apply).toArray();
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

        for (int i = 1; i < freqs.length; i++) {
            Complex c = freqs[i];
            double magnitude = Math.sqrt(c.getReal() * c.getReal() + c.getImaginary() + c.getImaginary());
            System.out.printf("%d, %.2f Hz: %.3f\n", i, i * binSpacing, magnitude);
        }
    }
}
