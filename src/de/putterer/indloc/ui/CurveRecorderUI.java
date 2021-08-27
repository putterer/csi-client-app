package de.putterer.indloc.ui;

import de.putterer.indloc.Config;
import de.putterer.indloc.Station;
import de.putterer.indloc.csi.CurveSampleRecorder;
import de.putterer.indloc.data.DataInfo;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public class CurveRecorderUI extends UIComponentWindow implements KeyListener {

    private final CsiUserInterface csiUserInterface;

    private final CurveSampleRecorder curveSampleRecorder = new CurveSampleRecorder(Paths.get("./curve-recording"), 0, 0, 2, 0);

    private final JLabel lastRecordedSampleTime = new JLabel("no sample recorded");
    private final JLabel lastRecordedSampleClass = new JLabel("Class: ");
    private final JLabel lastRecordedSampleStationCount = new JLabel("Stations: ");

    public CurveRecorderUI(CsiUserInterface csiUserInterface) {
        super("CurveRecorder", 420, 300);
        this.csiUserInterface = csiUserInterface;

        this.setLayout(null);
        this.initUI();

        this.getFrame().addKeyListener(this);

        this.setupFinished();
    }

    private void initUI() {
        lastRecordedSampleTime.setBounds(10, 0, 400, 20);
        lastRecordedSampleClass.setBounds(10, 30, 400, 20);
        lastRecordedSampleStationCount.setBounds(10, 60, 400, 20);

        this.add(lastRecordedSampleTime);
        this.add(lastRecordedSampleClass);
        this.add(lastRecordedSampleStationCount);
    }

    private void recordSample(int classIndex) {
        List<Station> stations = Arrays.asList(Config.ROOM.getStations());

        lastRecordedSampleTime.setText(String.format("Time %d", System.currentTimeMillis() / 1000));
        lastRecordedSampleClass.setText(String.format("Class: %d", classIndex));
        lastRecordedSampleStationCount.setText(String.format("Stations: %d", stations.size()));

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
