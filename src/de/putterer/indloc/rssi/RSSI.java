package de.putterer.indloc.rssi;

import de.putterer.indloc.Station;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

/**
 * Represents an RSSI value obtained from a station
 */
@RequiredArgsConstructor
@Getter
public class RSSI {

	private final float rssi; // the RSSI value
	private Optional<Station> station = Optional.empty(); // the station the value was obtained from

	/**
	 * Estimates the distance from the station due to this RSSI using the station's estimator
	 * @return the estimated distance in cm
	 */
	public int toDistance() {
		return station.map(s -> s.getEstimator().estimate(this)).orElse(0);
	}
	
	public void setStation(Station station) {
		this.station = Optional.of(station);
	}
}
