package de.putterer.indloc.rssi;

import de.putterer.indloc.Config;
import de.putterer.indloc.Station;
import de.putterer.indloc.IndLocUserInterface;

import java.io.IOException;

/**
 * Starts the RSSI based trilateration and user interface
 */
public class RSSITrilateration {
	
	
	public static void main(String[] args) throws IOException {
		for(Station station : Config.ROOM.getStations()) {
			RSSIUtil.spawnIcmpEchoProcess(station);
		}
		
		IndLocUserInterface ui = new IndLocUserInterface(Config.ROOM);
		
		while(true) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			
			for(Station station : Config.ROOM.getStations()) {
				try {
					RSSIUtil.updateRSSI(station);
				} catch(RuntimeException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
}
