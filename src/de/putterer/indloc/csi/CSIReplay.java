package de.putterer.indloc.csi;

import de.putterer.indloc.Config;
import de.putterer.indloc.Station;
import de.putterer.indloc.csi.calibration.PhaseOffset;
import de.putterer.indloc.data.DataInfo;
import de.putterer.indloc.spotfi.Spotfi;
import de.putterer.indloc.util.Logger;
import de.putterer.indloc.util.serialization.Serialization;
import lombok.Getter;
import lombok.val;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
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

    @Getter
    private final CompletableFuture<?> completedFuture = new CompletableFuture<>();
    private final Map<CSIInfo, String> stationByCSI = new HashMap<>();

    private final Map<Station, Consumer<CSIInfo[]>> callbacks = new HashMap<>();
    @Getter
    private final Config.RoomConfig room;

    private final List<Runnable> statusUpdateCallbacks = new ArrayList<>();

    private final List<CSIInfo> csi;
    private final long startTimeDiff; // TODO: remove
    private final int groupThreshold; // the number of CSIInfos to group before releasing them combined

    @Getter
    private final Instant startTime;
    @Getter
    private final Instant endTime;
    @Getter
    private final Duration totalRuntime;

    @Getter
    private Instant currentReplayTime;
    private final List<CSIInfo> nextCsi = new ArrayList<>();

    @Getter
    private boolean replayPaused;


    @Getter
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
        this.replayPaused = !startReplay;

        room = Serialization.deserialize(folder.resolve("room.cfg"), Config.RoomConfig.class);

        final ArrayList<CSIInfo> allCsi = new ArrayList<>();
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
                        allCsi.add(csi);
                        stationByCSI.put(csi, station.getHW_ADDRESS());
                    });
        }
        this.csi = Collections.unmodifiableList(allCsi);

        startTimeDiff = System.currentTimeMillis() - csi.stream().mapToLong(CSIInfo::getClientTimestamp).min().orElse(0) + 500;

        startTime = Instant.ofEpochMilli(csi.stream().mapToLong(CSIInfo::getClientTimestamp).min().orElse(0));
        endTime = Instant.ofEpochMilli(csi.stream().mapToLong(CSIInfo::getClientTimestamp).max().orElse(0));
        totalRuntime = Duration.between(startTime, endTime);

        setReplayPosition(startTime);

        Logger.debug("Replay loaded, %d packets...", csi.size());
    }

    /**
     * Adds a callback to be informed once a CSIInfo is to be released
     * @param station the station this callback is listening for
     * @param callback the callback
     */
    public void addCallback(Station station, Consumer<CSIInfo[]> callback) {
        callbacks.put(station, callback);
    }

    public void setReplayPosition(Instant time) {
        currentReplayTime = time;

        synchronized (nextCsi) {
            nextCsi.clear();
            csi.stream()
                    .filter(c -> ! Instant.ofEpochMilli(c.getClientTimestamp()).isBefore(time))
                    .sorted(Comparator.comparingLong(CSIInfo::getClientTimestamp))
                    .forEach(nextCsi::add);
        }

        new Thread(this::replayThread).start();
    }

    public void setReplayPaused(boolean replayPaused) {
        this.replayPaused = replayPaused;
        if(! replayPaused) {
            new Thread(this::replayThread).start();
        }
    }

    /**
     * runs the replay
     */
    private void replayThread() {
        Logger.debug("Starting replay thread");
        List<CSIInfo> groupingList = new LinkedList<>();

        Instant currentRealTime = Instant.now();
        while(!replayPaused && ! nextCsi.isEmpty()) {
            Duration realTimeDelta = Duration.between(currentRealTime, Instant.now());
            currentRealTime = currentRealTime.plus(realTimeDelta);

            if(! replayPaused) {
                currentReplayTime = currentReplayTime.plus(realTimeDelta);
            }

            Iterator<CSIInfo> iter = nextCsi.iterator();
            while(iter.hasNext()) {
                synchronized (nextCsi) {
                    if(! iter.hasNext()) { // last check was before obtaining lock
                        continue;
                    }

                    val csi = iter.next();
                    if(! currentReplayTime.isBefore(Instant.ofEpochMilli(csi.getClientTimestamp()))) {
                        groupingList.add(csi);
                        if(groupingList.size() >= groupThreshold || this.nextCsi.size() == 1) {
                            CSIInfo[] group = groupingList.toArray(new CSIInfo[0]);
                            callbacks.entrySet().stream()
                                    .filter(e -> Objects.equals(e.getKey().getHW_ADDRESS(), stationByCSI.get(csi)))
                                    .forEach(c -> c.getValue().accept(group));
                            groupingList.clear();
                        }

                        iter.remove();

                        Logger.trace("Replay: %d packets left", this.csi.size());
                    }
                }
            }

            statusUpdateCallbacks.forEach(Runnable::run);
            try { Thread.sleep(20); } catch(InterruptedException e) { e.printStackTrace(); }
        }

        completedFuture.complete(null);
        Logger.debug("Replay thread terminating. Paused: %s, Packets left: %d", replayPaused, nextCsi.size());
    }

    public void addStatusUpdateCallback(Runnable callback) {
        statusUpdateCallbacks.add(callback);
    }

    public void removeStatusUpdateCallback(Runnable callback) {
        statusUpdateCallbacks.remove(callback);
    }


//    public int isAvailable() {
//        return csi.size();
//    }
//
//    public boolean isEmpty() {
//        return csi.isEmpty();
//    }

    public int getNumberOfPastPackets() {
        return (int) csi.stream()
                .mapToLong(DataInfo::getClientTimestamp)
                .mapToObj(Instant::ofEpochMilli)
                .filter(t -> t.isBefore(currentReplayTime))
                .count();
    }

    public int getTotalNumberOfPackets() {
        return csi.size();
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
