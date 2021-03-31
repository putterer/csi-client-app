package de.putterer.indloc.util.toolbox;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Scanner;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public class RecordingEcgRepair {

    static long files;
    static long processed;

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

        files = Files.list(inputDir).count();
        processed = 0;

        // Convert
        Files.list(inputDir).forEach(path -> {
            System.out.println("Processed: " + (processed++) + " / " + files);
            try {
                if (path.getFileName().toString().endsWith(".deflate")) {
                    byte[] inputData = Files.readAllBytes(path);

//                    System.out.println("Inflating " + path.getFileName());
                    Inflater inflater = new Inflater();
                    inflater.setInput(inputData);
                    byte[] inflatedData = new byte[1000000];
                    int length = inflater.inflate(inflatedData);
                    inflater.end();

                    String s = new String(inflatedData, 0, length);

                    if(path.getFileName().toString().endsWith(".ecg.deflate")) {
                        if(!s.endsWith("]") && s.endsWith("}")) {
                            s += "]";
                        }
                        if(!s.endsWith("}]")) {
                            s += "}]";
                        }
                    }

                    inputData = s.getBytes(StandardCharsets.UTF_8);

//                    System.out.println("Deflating " + path.getFileName());
                    Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
                    deflater.setInput(inputData);
                    deflater.finish();
                    byte[] compressedData = new byte[1000000];
                    length = deflater.deflate(compressedData);
                    deflater.end();

                    // this removes file properties, permissions
                    Files.write(outputDir.resolve(path.getFileName().toString()), Arrays.copyOfRange(compressedData, 0, length));
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
