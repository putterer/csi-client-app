package de.putterer.indloc.util;

import de.putterer.indloc.csi.CSIInfo.Complex;

import java.security.InvalidParameterException;
import java.util.Arrays;
import java.util.stream.IntStream;

/**
 * Utility code for processing CSI
 */
public class CSIUtil {

    /**
     * unwrap the CSI
     * @param data the data to be unwrapped
     */
    public static double[] unwrapPhase(double data[]) {
        return unwrap(data, 2.0 * Math.PI);
    }

    /**
     * unwrap the CSI, limited to half a phase
     * @param data the data to be unwrapped
     */
    public static double[] unwrapHalfPhase(double data[]) {
        return unwrap(data, Math.PI);
    }

    /**
     * unwrap the CSI, limited to the given interval
     * @param data the data to be unwrapped
     */
    private static double[] unwrap(double data[], double interval) {
        for(int i = 1;i < data.length;i++) {
            while(data[i] - data[i - 1] > interval / 2.0) {
                data[i] -= interval;
            }
            while(data[i] - data[i - 1] < -interval / 2.0) {
                data[i] += interval;
            }
        }
        return data;
    }

    /**
     * shifts the entire array by a uniform offset
     * @param data the data to be shifted
     * @param shift the offset
     */
    public static void shift(double data[], double shift) {
        for(int i = 0;i < data.length;i++) {
            data[i] = data[i] + shift;
        }
    }

    /**
     * limits the data to the interval [-PI, PI]
     * @param data a single value
     * @return the bounded value
     */
    public static double bound(double data) {
        while(data > Math.PI) {
            data -= Math.PI * 2.0;
        }
        while(data < -Math.PI) {
            data += Math.PI * 2.0;
        }
        return data;
    }

    /**
     * calculates the mean over the given data
     * @param data the data
     * @return the mean
     */
    public static double mean(double[] data) {
        return Arrays.stream(data).sum() / data.length;
    }

    public static double timeUnwrapped(double[] data, double previousMeanPhase) {
        while(mean(data) - previousMeanPhase > 1.1 * Math.PI) {  // TODO: 1.1?
            shift(data, -2.0 * Math.PI);
        }
        while(mean(data) - previousMeanPhase < 1.1 * -Math.PI) {
            shift(data, 2.0 * Math.PI);
        }
        return mean(data);
    }

    public static Complex[] sum(Complex[] l, Complex[] r) {
        if(l.length != r.length) {
            throw new InvalidParameterException(String.format("Lengths of arrays l: %d, r: %d, do not match", l.length, r.length));
        }

        return IntStream.range(0, l.length).mapToObj(i -> new Complex(
                l[i].getReal() + r[i].getReal(),
                l[i].getImag() + r[i].getImag()
        )).toArray(Complex[]::new);
    }

    public static Complex[] scale(Complex[] c, double v) {
        return Arrays.stream(c).map(it -> it.scale(v)).toArray(Complex[]::new);
    }

    /**
     * Calculates the variance of the supplied data
     * @param v the data
     * @return the variance
     */
    public static double variance(double[] v) {
        double mean = Arrays.stream(v).average().orElse(0.0);
        return Arrays.stream(v).map(it -> Math.pow(it - mean, 2)).average().orElse(0.0);
    }

    /**
     * Calculates the standard deviation of the supplied data
     * @param v the data
     * @return the standard deviation
     */
    public static double stddev(double[] v) {
        return Math.sqrt(variance(v));
    }
}
