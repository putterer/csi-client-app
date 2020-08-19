package de.putterer.indloc.csi;

import de.putterer.indloc.Config;
import de.putterer.indloc.Station;
import de.putterer.indloc.csi.calibration.PhaseOffset;
import de.putterer.indloc.spotfi.Spotfi;
import de.putterer.indloc.util.Logger;
import de.putterer.indloc.util.Serialization;
import lombok.Getter;
import lombok.val;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import static de.putterer.indloc.csi.DataPreview.SubcarrierPropertyPreview.PropertyType.AMPLITUDE;
import static de.putterer.indloc.csi.DataPreview.SubcarrierPropertyPreview.PropertyType.PHASE;

/**
 * Represents a replay
 * Replay can be loaded from disk and used by using the constructor or replayed using the main method
 */
public class CSIReplay {

    private static final String RECORDED_CSI_PATTERN = "%s-\\d+.csi";
    private static final ExecutorService pool = Executors.newFixedThreadPool(4);

    private final LinkedList<CSIInfo> csi = new LinkedList<>();//TODO: sort csi into lists by station during loading
    @Getter
    private final CompletableFuture completedFuture = new CompletableFuture();
    private final Map<CSIInfo, String> stationByCSI = new HashMap<>();
    private final long startTimeDiff;
    private final Map<Station, Consumer<CSIInfo[]>> callbacks = new HashMap<>();
    @Getter
    private final Config.RoomConfig room;
    private final int groupThreshold; // the number of CSIInfos to group before releasing them combined

    private final Path folder;

    /**
     * loads and starts a new replay
     * @param folder the location of the replay
     * @param groupThreshold the number of CSIInfos to group before releasing them combined
     * @param startReplay whether the replay should be started after loading
     * @throws IOException
     */
    public CSIReplay(Path folder, int groupThreshold, boolean startReplay) throws IOException {
        this.folder = folder;
        this.groupThreshold = groupThreshold;

        room = Serialization.deserialize(folder.resolve("room.cfg"), Config.RoomConfig.class);

        for(Station station : room.getStations()) {
            Files.list(folder)
                    .filter(p -> Pattern.compile(String.format(RECORDED_CSI_PATTERN, station.getHW_ADDRESS())).matcher(p.toFile().getName()).matches())
                    .map(p -> {
                        try {
                            return (CSIInfo[])Serialization.deserialize(p, CSIInfo[].class);
                        } catch (IOException e) {
                            e.printStackTrace();
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .flatMap(Arrays::stream)
                    .forEach(csi -> {
                        CSIReplay.this.csi.add(csi);
                        stationByCSI.put(csi, station.getHW_ADDRESS());
                    });
        }

        startTimeDiff = System.currentTimeMillis() - csi.stream().mapToLong(CSIInfo::getClientTimestamp).min().orElse(0) + 500;

        if(startReplay) {
            Logger.debug("Replay loaded, starting playback of %d packets...", csi.size());
            new Thread(this::replayThread).start();
        }
    }

    /**
     * Adds a callback to be informed once a CSIInfo is to be released
     * @param station the station this callback is listening for
     * @param callback the callback
     */
    public void addCallback(Station station, Consumer<CSIInfo[]> callback) {
        callbacks.put(station, callback);
    }

    /**
     * runs the replay
     */
    private void replayThread() {
        List<CSIInfo> groupingList = new LinkedList<>();

        while(! csi.isEmpty()) {
            Iterator<CSIInfo> iter = csi.iterator();
            while(iter.hasNext()) {
                val csi = iter.next();
                if(csi.getClientTimestamp() + startTimeDiff < System.currentTimeMillis()) {
                    groupingList.add(csi);
                    if(groupingList.size() >= groupThreshold || this.csi.size() == 1) {
                        CSIInfo[] group = groupingList.toArray(new CSIInfo[0]);
                        callbacks.entrySet().stream()
                                .filter(e -> e.getKey().getHW_ADDRESS().equals(stationByCSI.get(csi)))
                                .forEach(c -> c.getValue().accept(group));
                        groupingList.clear();
                    }
                    stationByCSI.remove(csi);
                    iter.remove();

                    Logger.trace("Replay: %d packets left", this.csi.size());
                }
            }
            try { Thread.sleep(20); } catch(InterruptedException e) { e.printStackTrace(); }
        }

        completedFuture.complete(null);
        Logger.info("Replay finished.");
    }

    public int isAvailable() {
        return csi.size();
    }

    public boolean isEmpty() {
        return csi.isEmpty();
    }

    public static CompletableFuture mainProxy(String args[]) throws IOException {
        if(args.length < 2) {
            Logger.error("Missing arguments");
            return CompletableFuture.completedFuture(null);
        }

        Path path = Paths.get(args[0]);
        boolean spotfi = args[1].equals("dry") || args[1].equals("lookup") || args[1].equals("store") || args[1].equals("lookup_store");
        int groupThreshold = Integer.parseInt(args[2]);
        List<DataPreview> previews = new ArrayList<>();
        if(args.length >= 6) {
            int rxAntennas = Integer.parseInt(args[4]);
            int txAntennas = Integer.parseInt(args[5]);
            if(args[3].toLowerCase().contains("a")) {
                previews.add(new DataPreview(new DataPreview.SubcarrierPropertyPreview(AMPLITUDE, rxAntennas, txAntennas, 1)));
            }
            if(args[3].toLowerCase().contains("p")) {
                previews.add(new DataPreview(new DataPreview.SubcarrierPropertyPreview(PHASE, rxAntennas, txAntennas, 1)));
            }
            if(args[3].toLowerCase().contains("c")) {
                previews.add(new DataPreview(new DataPreview.CSIPlotPreview(rxAntennas, txAntennas)));
            }
        }
        //TODO: remove
        try {Thread.sleep(1000);} catch(InterruptedException e) {e.printStackTrace();}
//        val prev = new DataPreview(new DataPreview.CSIPlotPreview(3, 1));

        CSIReplay replay = new CSIReplay(path, groupThreshold, true);
        for(Station station : replay.getRoom().getStations()) {
            replay.addCallback(station, csi -> {
                List<CompletableFuture> instances = new ArrayList<>();

                for(int i = 1;i <= 4/*TODO*/;i++) {
                    final int finalI = i;
                    CompletableFuture instance = CompletableFuture.runAsync(() -> {
                        CSIInfo[] shiftedCsi = Arrays.stream(csi).map(c -> c.clone(
                                    PhaseOffset.getByMac(station.getHW_ADDRESS()).shiftMatrix(c.getCsi_matrix(), PhaseOffset.PhaseOffsetType.CROSSED, finalI)
                                ))
                                .toArray(CSIInfo[]::new);

                        if(spotfi) {
                            String[] lookupFiles = Arrays.stream(shiftedCsi).map(c ->
                                    path.resolve(station.getHW_ADDRESS() + "-" + c.getMessageId() + "-" + finalI).toAbsolutePath().toString()
                            ).toArray(String[]::new);



                            Spotfi.run(shiftedCsi, finalI,
                                    args[1].equals("lookup") || args[1].equals("lookup_store") ? lookupFiles : null,
                                    args[1].equals("store") || args[1].equals("lookup_store") ? lookupFiles : null
                            );
                        }

                        previews.forEach(p -> p.setData(shiftedCsi[0]));
                    }, pool);
                    instances.add(instance);
                }

                instances.forEach(CompletableFuture::join);
            });
        }

        return replay.getCompletedFuture();
    }

    public static void main(String args[]) throws IOException, ClassNotFoundException {
        mainProxy(args);
    }

    public List<CSIInfo> getCSI() {
        return Collections.unmodifiableList(csi);
    }
}
