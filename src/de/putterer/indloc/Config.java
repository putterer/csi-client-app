package de.putterer.indloc;

import de.putterer.indloc.activity.ActivityDetector;
import de.putterer.indloc.csi.CSIInfo;
import de.putterer.indloc.csi.calibration.AndroidInfo;
import de.putterer.indloc.trilateration.SimpleTrilaterator;
import de.putterer.indloc.trilateration.Trilaterator;
import de.putterer.indloc.util.Vector;
import lombok.Data;

import java.awt.image.BufferedImage;
import java.io.Serializable;

public class Config {
	
	// all distances are in cm
	// TODO: move to config
	
	//Amount of RSSI measurements to smooth over
	public static final int MAX_RSSI_PAST_LENGTH = 30;
	
	public static final int MAX_LOCATION_PAST_LENGTH = 40;
	
	//Interval between the indivdual packets sent as ICMP echo req, only applies to non windows based systems
	public static final int ICMP_ECHO_INTERVAL_MS = 500;
	
	public static final Trilaterator TRILATERATOR = new SimpleTrilaterator();
	public static final String STATION_5_MAC = "90:f6:52:4e:c5:ba";
	public static final String STATION_6_MAC = "f8:d1:11:cf:0d:9c";
	public static final String STATION_7_MAC = "90:f6:52:4e:b8:5c";
	public static final String STATION_10_MAC = "14:cc:20:b5:00:95";

	
	// ------------------------
	// Room configuration
	// ------------------------
	
	private static final RoomConfig ROOM_CSI_TESTING = new RoomConfig(
			1300, 1150, 
			new Station[] {
//					new Station(STATION_10_MAC, "10.10.0.10", AndroidInfo.class, new Vector(0, 0), null, new ActivityDetector())
					new Station("", "192.168.178.28", AndroidInfo.class, null, null).setName("Phone"),
					new Station(STATION_10_MAC, "192.168.178.210", CSIInfo.class, null, new ActivityDetector()).setName("Router 10"),
					new Station(STATION_6_MAC, "192.168.178.206", CSIInfo.class, null, new ActivityDetector()).setName("Router 6")
			},
			new RoomObject[] {

			}
	);

//	private static final float EXAMPLE_SIG_PROP_CONST = 4.2f;
//	private static final RoomConfig ROOM_EXAMPLE = new RoomConfig(
//			1072, 1258,
//			new Station[] {
//					new Station(STATION_5_MAC, "10.10.0.5", new Vector(745, 700), new SimpleDistanceEstimator(32, EXAMPLE_SIG_PROP_CONST)), // WR2543ND 5
//					new Station(STATION_6_MAC, "10.10.0.6", new Vector(115, 125), new SimpleDistanceEstimator(30, EXAMPLE_SIG_PROP_CONST)), // WR2543ND 6
//					new Station(STATION_7_MAC, "10.10.0.7", new Vector(715, 235), new SimpleDistanceEstimator(30, EXAMPLE_SIG_PROP_CONST)), // WR2543ND 7
//			},
//			new RoomObject[] {
//					new Target(new Vector(100, 200), 1),
//
//					new Background(FileUtils.loadImage("./backgroundWue.png"))
//			}
//	);

	public static RoomConfig ROOM = ROOM_CSI_TESTING; // might be ignored by services providing their own room, like the replay of a recording
	
	
	@Data
	public static class RoomConfig implements Serializable {
		private final int width;
		private final int height;
		private final Station[] stations;
		private final RoomObject[] objects;
	}

	public interface RoomObject {

	}

	@Data
	public static class Target implements RoomObject{
		private final Vector position;
		private final int id;
	}

	@Data
	public static class Background implements RoomObject {
		private final BufferedImage image;
	}
}
