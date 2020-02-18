package de.putterer.indloc.util.dataprocessing;

import de.putterer.indloc.util.Vector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Utility code for reading target recordings
 * allows calculating average distance and variance/deviation between actual target locations and estimates
 */
public class TargetRecordingAnalyzer {

    private static Pattern LINE_PATTERN = Pattern.compile("Target: (\\d+),(\\d+)  Estimation: (\\d+),(\\d+)  Distance: (\\d+(?:.\\d+))?");

    public static void main(String args[]) throws IOException {
        analyzeTargetRecording();
    }

    public static void analyzeTargetRecording() throws IOException {
        List<Double> distances = Files.readAllLines(Paths.get("./targetRecording.txt")).stream()
                .map(l -> l.split("Distance: ")[1])
                .map(Double::valueOf)
                .collect(Collectors.toList());

        double avg = distances.stream().mapToDouble(d -> d).average().getAsDouble();
        double variance = distances.stream().mapToDouble(d -> d - avg).map(d -> d * d).average().getAsDouble();
        double deviation = Math.sqrt(variance);

        System.out.printf("Average distance: %f, Deviation: %f\n", avg, deviation);
        for(int i = 0;i < distances.size();i++) {
            System.out.printf("(%d, %f)\n", i+1, distances.get(i));
        }
    }

    public static List<Vector> getMeasuredLocations() throws IOException {
        List<String> lines = Files.readAllLines(Paths.get("./targetRecording.txt"));

        List<Vector> locs = new ArrayList<>();
        for(String line : lines) {
            Matcher matcher = LINE_PATTERN.matcher(line);
            matcher.matches();
            locs.add(new Vector(Integer.parseInt(matcher.group(3)), Integer.parseInt(matcher.group(4))));
        }

        return locs;
    }
}
