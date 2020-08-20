package de.putterer.indloc.util;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class SwingUtil {

	public static double openDoubleDialog(String message, double defaultValue, Component component) {
		String s = JOptionPane.showInputDialog(component, message, defaultValue);
		try {
			return Double.parseDouble(s);
		} catch(NumberFormatException | NullPointerException e) {
			return defaultValue;
		}
	}

	public static int openIntDialog(String message, int defaultValue, Component component) {
		String s = JOptionPane.showInputDialog(component, message, defaultValue);
		try {
			return Integer.parseInt(s);
		} catch(NumberFormatException | NullPointerException e) {
			return defaultValue;
		}
	}

	public static int[] openIntListDialog(String message, int[] defaultValue, Map<Integer, String> stringMapping, Component component) {
		String initialSelectionValue = Arrays.stream(defaultValue)
				.mapToObj(i -> {
					if(stringMapping.containsKey(i)) return stringMapping.get(i);
					else return String.valueOf(i);
				})
				.collect(Collectors.joining(", "));
		String s = JOptionPane.showInputDialog(component, message, initialSelectionValue);
		try {
			return Arrays.stream(s.split("[,\\s]+"))
					.mapToInt(element ->
							stringMapping.entrySet().stream()
									.filter(e -> Objects.equals(e.getValue(), element))
									.map(Map.Entry::getKey).findFirst()
									.orElseGet(() -> Integer.parseInt(element))
					)
					.toArray();
		} catch(NumberFormatException | NullPointerException e) {
			return defaultValue;
		}
	}

	public static String openStringDialog(String message, String defaultValue, Component component) {
		return JOptionPane.showInputDialog(component, message, defaultValue);
	}

	public static int[] openIntListDialog(String message, int[] defaultValue, Component component) {
		return openIntListDialog(message, defaultValue, Collections.emptyMap(), component);
	}
}