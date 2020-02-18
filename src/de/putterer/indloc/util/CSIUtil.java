package de.putterer.indloc.util;

import java.util.Arrays;

/**
 * Utility code for processing CSI
 */
public class CSIUtil {

    /**
     * unwrap the CSI
     * @param data the data to be unwrapped
     */
    public static void unwrapPhase(double data[]) {
        unwrap(data, 2.0 * Math.PI);
    }

    /**
     * unwrap the CSI, limited to half a phase
     * @param data the data to be unwrapped
     */
    public static void unwrapHalfPhase(double data[]) {
        unwrap(data, Math.PI);
    }

    /**
     * unwrap the CSI, limited to the given interval
     * @param data the data to be unwrapped
     */
    private static void unwrap(double data[], double interval) {
        for(int i = 1;i < data.length;i++) {
            while(data[i] - data[i - 1] > interval / 2.0) {
                data[i] -= interval;
            }
            while(data[i] - data[i - 1] < -interval / 2.0) {
                data[i] += interval;
            }
        }
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
}
