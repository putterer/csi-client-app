package de.putterer.indloc.rssi;

import de.putterer.indloc.Config;
import de.putterer.indloc.Station;
import de.putterer.indloc.util.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RSSIUtil {
	
	private static final String RSSI_COMMAND = "iw dev wlan1 station get %s";
	private static final Pattern RSSI_PATTERN = Pattern.compile("\\t(-?\\d+) dBm");
	
	/**
	 * Get's the current RSSI, updates the stations RSSI as specified by the smooth factor, the raw, unsmoothed rssi is returned
	 * @param station the station to be update
	 * @return the raw rssi value
	 * @throws IOException
	 */
	public static int updateRSSI(Station station) throws IOException {
		int newRSSI = getRSSI(station.getHW_ADDRESS());
		if(newRSSI == -1000) {
			Logger.error("No RSSI found for station %s", station.getIP_ADDRESS());
		}
		station.addRSSI(new RSSI(newRSSI));
		return newRSSI;
	}
	
	private static int getRSSI(String hwAddress) throws IOException {
		Process process = Runtime.getRuntime().exec(String.format(RSSI_COMMAND, hwAddress));
		try(Scanner scan = new Scanner(process.getInputStream());) {
			while(scan.hasNext()) {
				String line = scan.nextLine();
				if(! line.contains("signal:")) {
					continue;
				}
				Matcher matcher = RSSI_PATTERN.matcher(line);
				if(! matcher.find()) {
					continue;
				}
				
				return Integer.parseInt(matcher.group(1));
			}
		}

//		Logger.error("Couldn't find RSSI in process output.");
		return -1000;
	}
	
	public static Process spawnIcmpEchoProcess(Station station) {
		try {
			return spawnIcmpEchoProcess(station.getIP_ADDRESS());
		} catch(IOException e) {
			throw new RuntimeException(e.getMessage());
		}
	}
	
	public static Process spawnIcmpEchoProcess(String ipAddress) throws IOException {
		Logger.info("Echoing %s...", ipAddress);
		
		ProcessBuilder builder = new ProcessBuilder();
		if(System.getProperty("os.name").startsWith("Windows")) {
			builder.command(String.format("ping %s -t", ipAddress));
		} else {
			builder.command("ping", ipAddress, "-i", String.valueOf((float)Config.TRILATERATION_ICMP_ECHO_INTERVAL_MS / 1000f));
		}
		
		Process process = builder.start();
		consumeStream(process.getInputStream());
		consumeStream(process.getErrorStream());//TODO: test me if ping is running
		
		return process;
	}
	
	public static void consumeStream(InputStream inputStream) {
		new Thread(() -> {
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))){
				String line;
				do {
					line = reader.readLine();
				} while(line != null);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}).start();//TODO: daemon?
	}
}
