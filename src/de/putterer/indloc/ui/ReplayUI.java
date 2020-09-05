package de.putterer.indloc.ui;

import de.putterer.indloc.Station;
import de.putterer.indloc.csi.CSIReplay;
import de.putterer.indloc.data.DataInfo;
import de.putterer.indloc.util.Logger;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.time.Duration;

public class ReplayUI extends UIComponentWindow {

    private CsiUserInterface csiUserInterface;

    private final boolean isReplayLoaded;

    private final JButton loadReplayButton = new JButton("Load replay");
    private final JLabel replayInfoLabel = new JLabel("filename");
    private final JLabel progressLabel = new JLabel("Packets: 0 / 0");
    private final JLabel timeProgressLabel = new JLabel("Time: 0.0s / 0.0s");
//    private final JProgressBar progressBar = new JProgressBar();
    private final JSlider progressSlider = new JSlider();
    private final JButton toStartButton = new JButton("|<<");
    private final JButton stepBackwardButton = new JButton("<<");
    private final JToggleButton playToggleButton = new JToggleButton("Play");
    private final JButton stepForwardButton = new JButton(">>");
    private final JButton toEndButton = new JButton(">>|");





    public ReplayUI(CsiUserInterface csiUserInterface, boolean isReplayLoaded) {
        super("Replay", 420, 300);
        this.csiUserInterface = csiUserInterface;
        this.isReplayLoaded = isReplayLoaded;

        this.setLayout(null);
        initUI();

        setupFinished();
    }

    private void initUI() {
        loadReplayButton.setBounds(10, 10, 400, 30);
        this.add(loadReplayButton);
        loadReplayButton.addActionListener(this::pickAndLoadReplayFile);

        if(! isReplayLoaded) {
            return;
        }

        replayInfoLabel.setBounds(10, 50, 400, 20);
        this.add(replayInfoLabel);
        replayInfoLabel.setText(csiUserInterface.getReplay().getFolder().getFileName().toString());

        progressLabel.setBounds(10, 90, 200, 30);
        this.add(progressLabel);
        timeProgressLabel.setBounds(220, 90, 200, 30);
        this.add(timeProgressLabel);


//        progressBar.setBounds(10, 130, 400, 30);
//        this.add(progressBar);
//        progressBar.setMinimum(0);
//        progressBar.setMaximum((int) csiUserInterface.getReplay().getTotalRuntime().toMillis());

        progressSlider.setBounds(10, 130, 400, 30);
        this.add(progressSlider);
        progressSlider.setMinimum(0);
        progressSlider.setMaximum((int) csiUserInterface.getReplay().getTotalRuntime().toMillis());
        progressSlider.setPaintTicks(false);
        progressSlider.setPaintLabels(false);


        int controlButtonCount = 5;
        int controlButtonWidth = (getWindowWidth() - (10 * (controlButtonCount + 1))) / controlButtonCount;
        int controlButtonDist = controlButtonWidth + 10;
        toStartButton.setBounds(10, 170, controlButtonWidth, 30);
        this.add(toStartButton);
        stepBackwardButton.setBounds(10 + controlButtonDist, 170, controlButtonWidth, 30);
        this.add(stepBackwardButton);
        playToggleButton.setBounds(10 + controlButtonDist * 2, 170, controlButtonWidth, 30);
        this.add(playToggleButton);
        stepForwardButton.setBounds(10 + controlButtonDist * 3, 170, controlButtonWidth, 30);
        this.add(stepForwardButton);
        toEndButton.setBounds(10 + controlButtonDist * 4, 170, controlButtonWidth, 30);
        this.add(toEndButton);

        CSIReplay replay = csiUserInterface.getReplay();
        playToggleButton.addActionListener(a -> replay.setReplayPaused(! playToggleButton.isSelected()));

        playToggleButton.addActionListener(a -> {
            toStartButton.setEnabled(! playToggleButton.isSelected());
            toEndButton.setEnabled(! playToggleButton.isSelected());
            stepForwardButton.setEnabled(! playToggleButton.isSelected());
            stepBackwardButton.setEnabled(! playToggleButton.isSelected());
            progressSlider.setEnabled(! playToggleButton.isSelected());
        });
        toStartButton.addActionListener(a -> replay.setReplayPosition(replay.getStartTime()));
        toEndButton.addActionListener(a -> replay.setReplayPosition(replay.getEndTime()));

        progressSlider.addChangeListener(a -> {
            if(replay.isReplayPaused()) {
                replay.setReplayPosition(replay.getStartTime().plus(Duration.ofMillis(progressSlider.getValue())));
            }
        });


        // TODO: replay loading takes really long, loading bar
        // TODO: step control
        // TODO:

        replay.addStatusUpdateCallback(this::updateStatus);
        updateStatus();
    }

    private void updateStatus() {
        CSIReplay replay = csiUserInterface.getReplay();
        SwingUtilities.invokeLater(() -> {
            progressLabel.setText(String.format("Packets:   %d / %d",
                    replay.getNumberOfPastPackets(),
                    replay.getTotalNumberOfPackets()));
            timeProgressLabel.setText(String.format("Time:   %.1f s / %.1f s",
                    Duration.between(replay.getStartTime(), replay.getCurrentReplayTime()).toMillis() / 1000.0f,
                    replay.getTotalRuntime().toMillis() / 1000.0f));
            progressSlider.setValue((int) Duration.between(replay.getStartTime(), replay.getCurrentReplayTime()).toMillis());
        });
    }

    private void pickAndLoadReplayFile(ActionEvent e) {
        JFileChooser chooser = new JFileChooser(System.getProperty("user.dir"));
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
//        chooser.addChoosableFileFilter(new FileFilter() {
//            @Override
//            public boolean accept(File file) {
//                return file.isDirectory() && Arrays.stream(file.list()).anyMatch(f -> f.endsWith(".csi")); // TODO: recursive
//            }
//
//            @Override
//            public String getDescription() {
//                return "CSI Recording";
//            }
//        });
        //TODO: alternative: recursive scan through current dir finding all possible recordings (could take forever)

        int res = chooser.showOpenDialog(this);
        if(res != JFileChooser.APPROVE_OPTION) {
            return;
        }

        Logger.info("Loading replay %s", chooser.getSelectedFile().toPath().toAbsolutePath().toString());

        csiUserInterface.loadReplay(chooser.getSelectedFile().toPath());
    }

    @Override
    public void onDataInfo(Station station, DataInfo dataInfo) {

    }
}
