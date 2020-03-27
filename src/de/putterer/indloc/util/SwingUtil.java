package de.putterer.indloc.util;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
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

	public static int[] openIntListDialog(String message, int[] defaultValue, Component component) {
		String s = JOptionPane.showInputDialog(component, message, Arrays.stream(defaultValue).mapToObj(String::valueOf).collect(Collectors.joining(",")));
		try {
			return Arrays.stream(s.split("[,\\s]+")).mapToInt(Integer::parseInt).toArray();
		} catch(NumberFormatException | NullPointerException e) {
			return defaultValue;
		}
	}
}
