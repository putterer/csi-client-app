package de.putterer.indloc.trilateration;

import de.putterer.indloc.Config;
import de.putterer.indloc.Station;
import de.putterer.indloc.util.Vector;

import java.util.List;

/**
 * Trilaterates a target's position based on the distance estimated from RSSI
 */
public class SimpleTrilaterator implements Trilaterator {

	/**
	 * performs the trilateration, RSSI/distance and position are obtainted from the stations, sampling grid is obtained from the room config
	 * @param stations the stations to use for the trilateration
	 * @return the estimated target location relative to the room and station config, in centimeters
	 */
	@Override
	public Vector estimate(List<Station> stations) {
		Vector bestPoint = null;
		float bestValue = 0.0f;
		
		for(int x = 0;x < Config.ROOM.getWidth();x += 10) {
			for(int y = 0;y < Config.ROOM.getHeight();y += 10) {
				Vector sample = new Vector(x, y);
				float acc = 1.0f;
				
				for(Station s : stations) {
					float stationDist = s.getRSSI().toDistance();
					float pointDist = sample.sub(s.getLocation()).length();
					float deviation = stationDist > pointDist ? pointDist / stationDist : stationDist / pointDist;
					acc *= deviation;
				}
				
				if(acc > bestValue) {
					bestPoint = sample;
					bestValue = acc;
				}
			}
		}
		
		return bestPoint;
	}
	
}
