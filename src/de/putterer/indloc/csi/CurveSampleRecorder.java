package de.putterer.indloc.csi;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import de.putterer.indloc.Station;
import de.putterer.indloc.csi.processing.cm.ConjugateMultiplicationProcessor;
import de.putterer.indloc.csi.processing.cm.ShapeRepresentationProcessor;
import de.putterer.indloc.data.DataInfo;
import de.putterer.indloc.util.Logger;
import de.putterer.indloc.util.Vector;
import lombok.Data;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CurveSampleRecorder {

    private static final int SLIDING_WINDOW_SIZE = 300;
    private static final int TIMESTAMP_COUNT_FOR_AVERAGE = 10;
    private static final double STDDEV_THRESHOLD_FOR_SAME_PHASE_DETECTION = 5.0;
    private static final double THRESHOLD_FOR_OFFSET_CORRECTION = 22000.0;

    private final Path recordingDirectory;
    private FileWriter fileWriter;
    private Gson gson = new Gson();

    private final Map<Station, CSIInfo> cachedCSIInfoByStation = new HashMap<>();

    private final int rx1;
    private final int rx2;
    private final ConjugateMultiplicationProcessor[] cmprocessors = new ConjugateMultiplicationProcessor[3];
    private final ShapeRepresentationProcessor shapeProcessor;

    public CurveSampleRecorder(Path recordingDirectory, int rx1, int rx2) {
        this.recordingDirectory = recordingDirectory;
        this.rx1 = rx1;
        this.rx2 = rx2;

        for(int i = 0; i < cmprocessors.length; i++) {
            this.cmprocessors[i] = new ConjugateMultiplicationProcessor(this.rx1, i, this.rx2, i, SLIDING_WINDOW_SIZE, TIMESTAMP_COUNT_FOR_AVERAGE, STDDEV_THRESHOLD_FOR_SAME_PHASE_DETECTION, THRESHOLD_FOR_OFFSET_CORRECTION);
        }
        this.shapeProcessor = new ShapeRepresentationProcessor(false);
    }

    private void initRecording() {
        Logger.info("Initializing recording of curve samples");

        if(! Files.exists(recordingDirectory)) {
            try {
                Files.createDirectory(recordingDirectory);
            } catch (IOException e) {
                Logger.error("Could not create recording directory for curve samples");
                e.printStackTrace();
            }
        }

        Path targetFile = recordingDirectory.resolve(System.currentTimeMillis() + ".samples");
        try {
            fileWriter = new FileWriter(targetFile.toFile());
        } catch (IOException e) {
            Logger.error("Could not create recording file for curve samples");
            e.printStackTrace();
        }
    }


    public void captureCMShapeSample(List<Station> stations, int classIndex) {
        // TODO: optional rotation offset fix, see DataPreview:1369

        if(fileWriter == null) {
            initRecording();
        }

        Map<String, SingleCurveSample> samplesByStation = new HashMap<>();

        for(Station station : stations) {
            CSIInfo info = cachedCSIInfoByStation.get(station);
            if(info == null) {
                Logger.info("Not csi data found to record for station %s", station.getName());
                return;
            }

            // log all 3 types of curve information
            SingleCurveSample sample = new SingleCurveSample();
            sample.sampleClass = classIndex;

            fillSample(sample, info);

            samplesByStation.put(station.getName(), sample);
        }

        try {
            fileWriter.write(gson.toJson(samplesByStation));
            fileWriter.write("\n");
            fileWriter.flush();
        } catch(IOException e) {
            Logger.error("Error while writing to shape recording");
            e.printStackTrace();
        }
    }

    public void fillSample(SingleCurveSample sample, CSIInfo csi) {
        Vector[][] curves = new Vector[3][];
        double[][] angles = new double[3][];
        double[][] dists = new double[3][];

        for(int i = 0;i < cmprocessors.length;i++) {
            CSIInfo.Complex[] processedData = cmprocessors[0].process(csi);

            // 2D curve plot data
            curves[i] = Arrays.stream(processedData).map(it -> new Vector(it.getReal(), it.getImag())).toArray(Vector[]::new);
            // 1D curve analysis data
            Vector[] shapeAnglesDists = shapeProcessor.process(processedData);
            shapeProcessor.wrapAngle(shapeAnglesDists);

            dists[i] = Arrays.stream(shapeAnglesDists).mapToDouble(Vector::getX).toArray();
            angles[i] = Arrays.stream(shapeAnglesDists).mapToDouble(Vector::getY).toArray();
        }

        sample.curve = curves[0]; sample.angles = angles[0]; sample.dists = dists[0];
        sample.curve2 = curves[1]; sample.angles2 = angles[1]; sample.dists2 = dists[1];
        sample.curve3 = curves[2]; sample.angles3 = angles[2]; sample.dists3 = dists[2];
    }

    public void onDataInfo(Station station, DataInfo dataInfo) {
        if (dataInfo instanceof CSIInfo) {
            cachedCSIInfoByStation.put(station, (CSIInfo) dataInfo);
        }
    }

    @Data
    static class SingleCurveSample {
        @SerializedName("cl") // the class of this sample according to known classification for training
        int sampleClass;

        Vector[] curve; // ensure backward compatibility for single TX antenna -> no array
        double[] angles;
        double[] dists;
        Vector[] curve2;
        double[] angles2;
        double[] dists2;
        Vector[] curve3;
        double[] angles3;
        double[] dists3;
    }
}
