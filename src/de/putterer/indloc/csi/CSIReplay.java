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
import java.util.stream.Collectors;

import static de.putterer.indloc.csi.DataPreview.SubcarrierPropertyPreview.PropertyType.AMPLITUDE;
import static de.putterer.indloc.csi.DataPreview.SubcarrierPropertyPreview.PropertyType.PHASE;

/**
 * Represents a replay
 * Replay can be loaded from disk and used by using the constructor or replayed using the main method
 */
public class CSIReplay {

    private static final String RECORDED_DATA_PATTERN = "%s-\\d+.((csi)|(ecg)|(accel))(.deflate)?";
    private static final ExecutorService pool = Executors.newFixedThreadPool(4);

    @Getter
    private final CompletableFuture<?> completedFuture = new CompletableFuture<>();
    private final Map<DataInfo, String> stationByData = new HashMap<>();

    private final Map<Station, Consumer<DataInfo[]>> callbacks = new HashMap<>();
    @Getter
    private final Config.RoomConfig room;

    private final List<Runnable> statusUpdateCallbacks = new ArrayList<>();

    private final List<DataInfo> data;
    private final int groupThreshold; // the number of DataInfos to group before releasing them combined
    private int loadingProgress = 0;

    @Getter
    private final Instant startTime;
    @Getter
    private final Instant endTime;
    @Getter
    private final Duration totalRuntime;

    @Getter
    private Instant currentReplayTime;
    private final List<DataInfo> nextData = new ArrayList<>();

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
    public CSIReplay(Path folder, int groupThreshold, boolean startReplay, Consumer<Double> progressCallback) throws IOException {
        this.folder = folder;
        this.groupThreshold = groupThreshold;
        this.replayPaused = !startReplay;

        room = Serialization.deserialize(folder.resolve("room.cfg"), Config.RoomConfig.class);

        final ArrayList<DataInfo> allData = new ArrayList<>();
        for(Station station : room.getStations()) {
            List<Path> matchingFiles = Files.list(folder)
                    .filter(p ->
                            Pattern.compile(String.format(RECORDED_DATA_PATTERN, station.getHW_ADDRESS())).matcher(p.toFile().getName()).matches()
                        || Pattern.compile(String.format(RECORDED_DATA_PATTERN, station.getIP_ADDRESS())).matcher(p.toFile().getName()).matches())
                    .collect(Collectors.toList());

            matchingFiles.stream()
                    .map(p -> {
                        loadingProgress++;
                        progressCallback.accept((double)loadingProgress / matchingFiles.size());

                        try {
                            return (DataInfo[])Serialization.deserialize(p, DataInfo[].class);
                        } catch (IOException e) {
                            e.printStackTrace();
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .flatMap(Arrays::stream)
                    .forEach(data -> {
                        allData.add(data);
                        stationByData.put(data, station.getHW_ADDRESS());
                    });
        }
        this.data = Collections.unmodifiableList(allData);

        startTime = Instant.ofEpochMilli(data.stream().mapToLong(DataInfo::getClientTimestamp).min().orElse(0));
        endTime = Instant.ofEpochMilli(data.stream().mapToLong(DataInfo::getClientTimestamp).max().orElse(0));
        totalRuntime = Duration.between(startTime, endTime);

        setReplayPosition(startTime);

        Logger.debug("Replay loaded, %d packets...", data.size());
    }

    /**
     * Adds a callback to be informed once a CSIInfo is to be released
     * @param station the station this callback is listening for
     * @param callback the callback
     */
    public void addCallback(Station station, Consumer<DataInfo[]> callback) {
        callbacks.put(station, callback);
    }

    public void setReplayPosition(Instant time) {
        currentReplayTime = time;

        synchronized (nextData) {
            nextData.clear();
            data.stream()
                    .filter(c -> ! c.getClientInstant().isBefore(time))
                    .sorted(Comparator.comparingLong(DataInfo::getClientTimestamp))
                    .forEach(nextData::add);
        }

        postNearestData(time);

        statusUpdateCallbacks.forEach(Runnable::run);

        if(! replayPaused) {
            new Thread(this::replayThread).start();
        }
    }

    public void stepBackward() {
        setReplayPosition(data.stream()
                .filter(c -> c.getClientInstant().isBefore(currentReplayTime))
                .max(Comparator.comparingLong(DataInfo::getClientTimestamp))
                .map(DataInfo::getClientInstant)
                .orElse(startTime)
        );
    }

    public void stepForward() {
        setReplayPosition(data.stream()
                .filter(c -> c.getClientInstant().isAfter(currentReplayTime))
                .min(Comparator.comparingLong(DataInfo::getClientTimestamp))
                .map(DataInfo::getClientInstant)
                .orElse(endTime)
        );
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
        List<DataInfo> groupingList = new LinkedList<>();

        Instant currentRealTime = Instant.now();
        while(!replayPaused && ! nextData.isEmpty()) {
            Duration realTimeDelta = Duration.between(currentRealTime, Instant.now());
            currentRealTime = currentRealTime.plus(realTimeDelta);

            if(! replayPaused) {
                currentReplayTime = currentReplayTime.plus(realTimeDelta);
            }

            Iterator<DataInfo> iter = nextData.iterator();
            while(iter.hasNext()) {
                synchronized (nextData) {
                    if(! iter.hasNext()) { // last check was before obtaining lock
                        continue;
                    }

                    val data = iter.next();
                    if(! currentReplayTime.isBefore(Instant.ofEpochMilli(data.getClientTimestamp()))) {
                        groupingList.add(data);
                        if(groupingList.size() >= groupThreshold || this.nextData.size() == 1) {
                            DataInfo[] group = groupingList.toArray(new DataInfo[0]);
                            postData(group);
                            groupingList.clear();
                        }

                        iter.remove();

                        Logger.trace("Replay: %d packets left", this.data.size());
                    }
                }
            }

            statusUpdateCallbacks.forEach(Runnable::run);
            try { Thread.sleep(20); } catch(InterruptedException e) { e.printStackTrace(); }
        }

        completedFuture.complete(null);
        Logger.debug("Replay thread terminating. Paused: %s, Packets left: %d", replayPaused, nextData.size());
    }

    private void postData(DataInfo[] data) {
        callbacks.entrySet().stream()
                .filter(e -> Objects.equals(e.getKey().getHW_ADDRESS(), stationByData.get(data[0])))
                .forEach(c -> c.getValue().accept(data));
    }

    private void postNearestData(Instant time) {
        for(Station station : room.getStations()) {
            data.stream()
                    .filter(d -> stationByData.get(d).equals(station.getHW_ADDRESS()))
                    .min(Comparator.comparingLong(
                    c -> Duration.between(time, c.getClientInstant()).abs().toMillis()
            )).ifPresent(nearestData -> postData(new DataInfo[] { nearestData }));
        }
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
        return (int) data.stream()
                .mapToLong(DataInfo::getClientTimestamp)
                .mapToObj(Instant::ofEpochMilli)
                .filter(t -> t.isBefore(currentReplayTime))
                .count();
    }

    public int getTotalNumberOfPackets() {
        return data.size();
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
                previews.add(new DataPreview("replay", new DataPreview.SubcarrierPropertyPreview(AMPLITUDE, rxAntennas, txAntennas, 1)));
            }
            if(args[3].toLowerCase().contains("p")) {
                previews.add(new DataPreview("replay", new DataPreview.SubcarrierPropertyPreview(PHASE, rxAntennas, txAntennas, 1)));
            }
            if(args[3].toLowerCase().contains("c")) {
                previews.add(new DataPreview("replay", new DataPreview.CSIPlotPreview(rxAntennas, txAntennas)));
            }
        }
        //TODO: remove
        try {Thread.sleep(1000);} catch(InterruptedException e) {e.printStackTrace();}
//        val prev = new DataPreview(new DataPreview.CSIPlotPreview(3, 1));

        CSIReplay replay = new CSIReplay(path, groupThreshold, true, null);
        for(Station station : replay.getRoom().getStations()) {
            replay.addCallback(station, data -> {
                List<CompletableFuture> instances = new ArrayList<>();

                for(int i = 1;i <= 4/*TODO*/;i++) {
                    final int finalI = i;
                    CompletableFuture instance = CompletableFuture.runAsync(() -> {
                        CSIInfo[] shiftedCsi = Arrays.stream(data).filter(it -> it instanceof CSIInfo).map(c -> ((CSIInfo)c).clone(
                                    PhaseOffset.getByMac(station.getHW_ADDRESS()).shiftMatrix(((CSIInfo)c).getCsi_matrix(), PhaseOffset.PhaseOffsetType.CROSSED, finalI)
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

    public List<DataInfo> getData() {
        return Collections.unmodifiableList(data);
    }
}
