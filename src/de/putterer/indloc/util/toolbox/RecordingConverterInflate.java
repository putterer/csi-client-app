package de.putterer.indloc.util.toolbox;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Scanner;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

public class RecordingConverterInflate {

    /**
     * Extract a deflated recording
     */
    public static void main(String args[]) throws IOException {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Input directory:");
        Path inputDir = Paths.get(scanner.nextLine());

        System.out.println("Output directory:");
        Path outputDir = Paths.get(scanner.nextLine());

        if (Files.notExists(inputDir) || !Files.isDirectory(inputDir)) {
            System.err.println("Input has to be a directory");
            return;
        }

        if (Files.notExists(outputDir)) {
            Files.createDirectories(outputDir);
        }

        if (!Files.isDirectory(outputDir)) {
            System.err.println("Output not a directory");
        }

        // Convert
        Files.list(inputDir).forEach(path -> {
            try {
                if (path.getFileName().toString().endsWith(".csi.deflate")) {
                    byte[] inputData = Files.readAllBytes(path);

                    System.out.println("Inflating " + path.getFileName());
                    Inflater inflater = new Inflater();
                    inflater.setInput(inputData);
                    byte[] decompressedData = new byte[inputData.length * 1000];
                    int length = inflater.inflate(decompressedData);
                    inflater.end();

                    // this removes file properties, permissions
                    Files.write(outputDir.resolve(path.getFileName().toString().replace(".deflate", "")), Arrays.copyOfRange(decompressedData, 0, length));
                } else {
                    System.out.println("Copying " + path.getFileName());
                    Files.copy(path, outputDir.resolve(path.getFileName().toString()));
                }
            } catch (IOException | DataFormatException e) {
                e.printStackTrace();
            }
        });
    }
}
