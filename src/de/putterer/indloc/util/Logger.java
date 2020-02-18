package de.putterer.indloc.util;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;

/**
 * Guess what, it's a logger. :)
 */
public class Logger {
	
	private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	private static Level logLevel = Level.TRACE;
	
	public static void trace(String format, Object... args) {
		log(Level.TRACE, format, args);
	}
	public static void debug(String format, Object... args) {
		log(Level.DEBUG, format, args);
	}
	public static void info(String format, Object... args) {
		log(Level.INFO, format, args);
	}
	public static void warn(String format, Object... args) {
		log(Level.WARNING, format, args);
	}
	public static void error(String format, Object... args) {
		log(Level.ERROR, format, args);
	}
	
	public static void log(Level level, String format, Object... args) {
		if(level.value < logLevel.value) {
			return;
		}
		
		String message = String.format(format, args);
		String output = String.format("%s %s -- %s", dateFormat.format(Date.from(Instant.now())), level.name, message);
		if(level.value >= Level.WARNING.value) {
			System.err.println(output);
		} else {
			System.out.println(output);
		}
	}
	
	public static void setLogLevel(Level level) {
		Logger.logLevel = level;
	}
	
	public enum Level {
		TRACE(0, "TRACE"), DEBUG(1, "DEBUG"), INFO(2, "INFO"), WARNING(3, "WARN"), ERROR(4, "ERROR");
		
		Level(int value, String name) {
			this.value = value;
			this.name = name;
		}
		private final int value;
		private final String name;

		public static Level getByName(String name) {
			return Arrays.stream(Level.values()).filter(l -> l.name.equalsIgnoreCase(name)).findFirst().orElse(null);
		}
	}
}
