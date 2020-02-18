package de.putterer.indloc.rssi.estimator;

import de.putterer.indloc.rssi.RSSI;
import de.putterer.indloc.util.Interpolation;
import de.putterer.indloc.util.Interpolation.Step;

/**
 * Estimates the distance from RSSI by linearly interpolating between fixed points
 */
public class LinearInterpolationEstimator implements RSSIDistanceEstimator {
	
	//	public static final Interpolation INTERPOLATION = new Interpolation(
	//	new Step(-15, 30),
	//	new Step(-30, 200),
	//	new Step(-40, 400),
	//	new Step(-50, 800),
	//	new Step(-60, 1600)
	//);
	//WUE
	public static final Interpolation INTERPOLATION = new Interpolation(
			new Step(0, 1),
			new Step(-24, 30),
			new Step(-33, 100),
			new Step(-39, 150),
			new Step(-42, 220),
			new Step(-47, 270),
			new Step(-49, 330),
			new Step(-53, 400),
			new Step(-56, 500),
			new Step(-60, 580),
			new Step(-67, 660),
			new Step(-70, 750)
	);

	@Override
	public int estimate(RSSI rssi) {
		return INTERPOLATION.interpolate(rssi.getRssi());
	}
	
	@Override
	public String toString() {
		return String.format("LinearInterpolationEstimator, %d steps", INTERPOLATION.getSteps().size());
	}
}
