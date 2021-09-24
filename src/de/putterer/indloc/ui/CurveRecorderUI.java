package de.putterer.indloc.ui;

import de.putterer.indloc.Config;
import de.putterer.indloc.Station;
import de.putterer.indloc.csi.CurveSampleRecorder;
import de.putterer.indloc.data.DataInfo;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CurveRecorderUI extends UIComponentWindow implements KeyListener {

    private static final Font FONT = new Font("DejaVu Sans Mono", Font.PLAIN, 30);

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final CsiUserInterface csiUserInterface;

    private final CurveSampleRecorder curveSampleRecorder = new CurveSampleRecorder(Paths.get("./curve-recording"), 0, 2);
    private int samplesRecorded = 0;

    private final Color recordingColor = new Color(200, 200, 200);
    private final Color originalBackgroundColor;

    private final JLabel lastRecordedSampleTime = new JLabel("no sample recorded");
    private final JLabel lastRecordedSampleClass = new JLabel("Class: ");
    private final JLabel lastRecordedSampleStationCount = new JLabel("Stations: ");
    private final JLabel samplesRecordedLabel = new JLabel("Samples: ");
    private final JLabel[] labels = {samplesRecordedLabel, lastRecordedSampleTime, lastRecordedSampleClass, lastRecordedSampleStationCount};

    public CurveRecorderUI(CsiUserInterface csiUserInterface) {
        super("CurveRecorder", 420, 300);
        this.csiUserInterface = csiUserInterface;

        this.setLayout(null);
        this.initUI();

        this.getFrame().addKeyListener(this);

        this.setupFinished();

        originalBackgroundColor = this.getBackground();
    }

    private void initUI() {
        for(int i = 0, y = 30; i < labels.length; i++, y += 60) {
            labels[i].setBounds(30, y, 400, 50);
            labels[i].setFont(FONT);
            this.add(labels[i]);
        }
    }

    private void recordSample(int classIndex) {
        List<Station> stations = Arrays.asList(Config.ROOM.getStations());
        samplesRecorded++;

        SwingUtilities.invokeLater(() -> {
            samplesRecordedLabel.setText(String.format("Samples: %d", samplesRecorded));
            lastRecordedSampleTime.setText(String.format("Time %d", System.currentTimeMillis() / 1000));
            lastRecordedSampleClass.setText(String.format("Class: %d", classIndex));
            lastRecordedSampleStationCount.setText(String.format("Stations: %d", stations.size()));

            this.setBackground(recordingColor);
            this.repaint();

            executor.schedule(() -> {
                SwingUtilities.invokeLater(() -> this.setBackground(originalBackgroundColor));
                this.repaint();
            }, 100, TimeUnit.MILLISECONDS);
        });

        curveSampleRecorder.captureCMShapeSample(stations, classIndex);
    }

    @Override
    public void onDataInfo(Station station, DataInfo dataInfo) {
        curveSampleRecorder.onDataInfo(station, dataInfo);
    }

    @Override
    public void keyPressed(KeyEvent e) {
        if(e.getKeyCode() >= '0' && e.getKeyCode() < '9') {
            int pressedDigit = e.getKeyCode() - '0';
            recordSample(pressedDigit);
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {}

    @Override
    public void keyTyped(KeyEvent e) {}
}
