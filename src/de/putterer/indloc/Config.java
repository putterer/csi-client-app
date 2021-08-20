package de.putterer.indloc;

import de.putterer.indloc.activity.ActivityDetector;
import de.putterer.indloc.csi.atheros.AthCSIInfo;
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
	
	// Interval between the indivdual packets sent as ICMP echo req, only applies to non windows based systems
	// only used for indoor localization using trilateration
	public static final int TRILATERATION_ICMP_ECHO_INTERVAL_MS = 500;
	
	public static final Trilaterator TRILATERATOR = new SimpleTrilaterator();

	public static final String STATION_5_MAC = "90:f6:52:4e:c5:ba"; // WR2543ND
	public static final String STATION_6_MAC = "f8:d1:11:cf:0d:9c"; // WR2543ND
	public static final String STATION_7_MAC = "90:f6:52:4e:b8:5c"; // WR2543ND
	public static final String STATION_10_MAC = "14:cc:20:b5:00:95"; // WDR4300
	public static final String STATION_11_MAC = "00:16:ea:ef:f1:14"; // APUBoard1
	public static final String STATION_ESP_CSI_MAC = "esp-csi";
	public static final String STATION_ESP_ECG_MAC = "esp-ecg";
	public static final String STATION_ANDROID_MAC = "android";

	
	// ------------------------
	// Room configuration
	// ------------------------
	

	private static final RoomConfig ROOM_CSI_TESTING = new RoomConfig(
			1300, 1150, // width and height are only used for indoor localization
			new Station[] {
//					new Station(STATION_10_MAC, "10.10.0.10", AndroidInfo.class, new Vector(0, 0), null, new ActivityDetector())
//					new Station(STATION_10_MAC, "192.168.178.210", CSIInfo.class,  2 23null, new ActivityDetector())
//							.setName("Router 10")
//							.enableRespiratoryUI(),
					new Station(STATION_5_MAC, "192.168.178.205", AthCSIInfo.class, null, new ActivityDetector()).setName("AthCsiRx"),//.proxyViaSsh(),
//					new Station(STATION_11_MAC, "192.168.178.211", IntCSIInfo.class, null, null).setName("IntCsiRx"),//.enableRespiratoryUI()
//					new Station(STATION_ESP_CSI_MAC, "/dev/ttyUSB1", EspCSIInfo.class, null, null).setName("EspCsiRx"),
//					new Station(STATION_ANDROID_MAC, "192.168.178.237", AndroidInfo.class, null, null).setName("Phone"),
//					new Station(STATION_ESP_ECG_MAC, "/dev/ttyUSB0", EcgInfo.class, null, null)
			},
			new RoomObject[] {
				// allows setting a background image for localization
			}
	);

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
