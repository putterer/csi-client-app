package de.putterer.indloc.spotfi;

import com.google.gson.Gson;
import de.putterer.indloc.csi.CSIInfo;
import de.putterer.indloc.util.Logger;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Represents the spotfi implementation
 */
public class Spotfi {

    public static final String INTERPRETER = "python3";
    public static final String ENTRY_POINT = "./src/spotfi.py";

    private static final Gson gson = new Gson();

    /**
     * Runs the spotfi python script
     * @param csiInfo the list of grouped (threshold) csi infos to be processed by the algorithm
     * @param calibrationPossibility the current calibration (just for displaying in the UI)
     * @param lookupFiles a list of lookup files for MUSIC spectrums (nullable)
     * @param storageFiles a list of files to store the calculated MUSIC spectrums in (nullable)
     */
    public static void run(CSIInfo[] csiInfo, int calibrationPossibility, String[] lookupFiles, String[] storageFiles) {
        if(lookupFiles == null) {
            lookupFiles = new String[0];
        }
        if(storageFiles == null) {
            storageFiles = new String[0];
        }

        String json = gson.toJson(csiInfo);

        ProcessBuilder builder = new ProcessBuilder();
        builder.command(INTERPRETER, ENTRY_POINT);
        builder.environment().put("OPENBLAS_MAIN_FREE", "1");

        Process process;
        try {
            Logger.debug("Starting python process...");
            process = builder.start();
            new Thread(() -> {
                byte[] buffer = new byte[8096];
                while(process.isAlive()) {
                    try {
                        int read = process.getErrorStream().read(buffer, 0, buffer.length);
                        if(read == -1) {
                            return;
                        }
                        System.err.print(new String(buffer, 0, read));
                        Thread.sleep(10);
                    } catch(IOException | InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }).start();

            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
            out.write(calibrationPossibility + "\n");
            out.write(gson.toJson(lookupFiles) + "\n");
            out.write(gson.toJson(storageFiles) + "\n");
            out.write(json + "\n");
            out.flush();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String res = reader.readLine();

            out.close();
        } catch(IOException e) {
            Logger.error("Could not start spotfi with python");
            e.printStackTrace();
        }
;    }

    public static void runParallel(CSIInfo[] info, int calibrationPossibility) {
        List<CompletableFuture> futures = new ArrayList<>();
        Arrays.stream(info).forEach(csi -> futures.add(CompletableFuture.runAsync(() -> Spotfi.run(new CSIInfo[] {csi}, calibrationPossibility, null, null))));
        futures.forEach(CompletableFuture::join);//todo: return futures.map(Future::get)
    }
}
