package de.putterer.indloc.rssi.estimator;

import de.putterer.indloc.rssi.RSSI;

/**
 * Estimates the distance from the given RSSI
 */
public interface RSSIDistanceEstimator {
	/**
	 * @param rssi the rssi
	 * @return the estimated distance (in cm)
	 */
	public int estimate(RSSI rssi);
	public String toString();
}
