package de.putterer.indloc;

import de.putterer.indloc.activity.ActivityDetector;
import de.putterer.indloc.data.DataInfo;
import de.putterer.indloc.rssi.RSSI;
import de.putterer.indloc.rssi.estimator.RSSIDistanceEstimator;
import de.putterer.indloc.util.Observable;
import de.putterer.indloc.util.Vector;
import lombok.*;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;


/**
 * Represents a station from which RSSI or CSI is obtained
 */
@Getter
@Setter
@RequiredArgsConstructor
@AllArgsConstructor
public class Station implements Serializable {
	private static transient final long serialVersionUID = -1110866127235090866L;
	
	private final String HW_ADDRESS; // the MAC address of the station
	private final String IP_ADDRESS; // the IP address the station is reachable at
	private transient final Class<? extends DataInfo> dataType;
	private transient final Observable<RSSI> rssi = new Observable<>(new RSSI(-100));
	private transient final List<RSSI> pastRSSIs = new LinkedList<>();
	private Vector location = new Vector();
	private transient final RSSIDistanceEstimator estimator;
	private transient final ActivityDetector activityDetector;

	//display
	private boolean displayRespiratoryDetector = false; // will cause csi interface to run and display the respiratory detection UI

	private String name;
	
	{
		getRSSI().setStation(this);
	}
	
	public void addRSSI(RSSI rssi) {
		rssi.setStation(this);
		
		pastRSSIs.add(rssi);
		while(pastRSSIs.size() >= Config.MAX_RSSI_PAST_LENGTH) {
			pastRSSIs.remove(0);
		}
		
		val newRSSI = new RSSI(
				(float) pastRSSIs.stream().mapToDouble(RSSI::getRssi).average().orElse(100f)
		);
		newRSSI.setStation(this);
		setRSSI(newRSSI);
	}

	public Station setName(String name) {
		this.name = name;
		return this;
	}

	public Station enableRespiratoryUI() {
		displayRespiratoryDetector = true;
		return this;
	}

	public Station disableRespiratoryUI() {
		displayRespiratoryDetector = false;
		return this;
	}
	
	public RSSI getRSSI() {
		return rssi.get();
	}
	
	public void setRSSI(RSSI rssi) {
		this.rssi.set(rssi);
	}
	
	public Observable<RSSI> rssiProperty() {
		return rssi;
	}

	@Override
	public int hashCode() {
		return HW_ADDRESS.hashCode();
	}
}
