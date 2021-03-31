package de.putterer.indloc.util.toolbox;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Scanner;
import java.util.zip.Deflater;

public class RecordingConverter {

    /**
     * Compresses a non deflated recording
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
                if (path.getFileName().toString().endsWith(".csi")) {
                    byte[] inputData = Files.readAllBytes(path);

                    System.out.println("Deflating " + path.getFileName());
                    Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
                    deflater.setInput(inputData);
                    deflater.finish();
                    byte[] compressedData = new byte[inputData.length];
                    int length = deflater.deflate(compressedData);
                    deflater.end();

                    // this removes file properties, permissions
                    Files.write(outputDir.resolve(path.getFileName().toString() + ".deflate"), Arrays.copyOfRange(compressedData, 0, length));
                } else {
                    System.out.println("Copying " + path.getFileName());
                    Files.copy(path, outputDir.resolve(path.getFileName().toString()));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }
}
