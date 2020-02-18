package de.putterer.indloc.rssi.estimator;

import de.putterer.indloc.rssi.RSSI;
import lombok.RequiredArgsConstructor;

/**
 * Estimates the distance from RSI by using the formula proposed by Texas Instruments and described in
 * "Evaluation of the Reliability of RSSI for Indoor Localization" by Qian Dong and Waltenegus Dargie
 */
@RequiredArgsConstructor
public class SimpleDistanceEstimator implements RSSIDistanceEstimator {
	
	// from www.rn.inf.tu-dresden.de/dargie/papers/icwcuca.pdf
	//
	//    RSSI = -(10 * n) log_10(d) - A
	// => d = 10 ^ ((RSSI + A) / (-10 * n))
	private final float A;// RSSI at 1 meter distance
	private final float n;// signal propagation constant, n = 1.6 - 1.8 for line of sight, indoors; n = 2.0 line of sight, outside, n = 3-4 for obstacles 
	
	
	
	@Override
	public int estimate(RSSI rssi) {
		return (int) (Math.pow(10, (rssi.getRssi() + A) / (-10 * n)) * 100.0f /* to cm*/);
	}
	
	@Override
	public String toString() {
		return String.format("simple RSSI dist estimator, A: %.0f,  n: %.1f", A, n);
	}
	
}
