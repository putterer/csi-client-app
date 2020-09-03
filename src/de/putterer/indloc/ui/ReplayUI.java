package de.putterer.indloc.ui;

import de.putterer.indloc.Station;
import de.putterer.indloc.data.DataInfo;
import de.putterer.indloc.util.Logger;

import javax.swing.*;
import java.awt.event.ActionEvent;

public class ReplayUI extends UIComponentWindow {

    private CsiUserInterface csiUserInterface;

    private final boolean isReplayLoaded;

    private final JButton loadReplayButton = new JButton("Load replay");
    private final JLabel progressLabel = new JLabel("0 / 0 packets, 0.0s / 0.0s");
    private final JProgressBar progressBar = new JProgressBar();
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

        progressLabel.setBounds(10, 70, 400, 30);
        this.add(progressLabel);
        progressBar.setBounds(10, 110, 400, 30);
        this.add(progressBar);

        int controlButtonCount = 5;
        int controlButtonWidth = (getWindowWidth() - (10 * (controlButtonCount + 1))) / controlButtonCount;
        int controlButtonDist = controlButtonWidth + 10;
        toStartButton.setBounds(10, 150, controlButtonWidth, 30);
        this.add(toStartButton);
        stepBackwardButton.setBounds(10 + controlButtonDist, 150, controlButtonWidth, 30);
        this.add(stepBackwardButton);
        playToggleButton.setBounds(10 + controlButtonDist * 2, 150, controlButtonWidth, 30);
        this.add(playToggleButton);
        stepForwardButton.setBounds(10 + controlButtonDist * 3, 150, controlButtonWidth, 30);
        this.add(stepForwardButton);
        toEndButton.setBounds(10 + controlButtonDist * 4, 150, controlButtonWidth, 30);
        this.add(toEndButton);


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

        csiUserInterface.loadReplay(chooser.getSelectedFile().toPath());// TODO: actually do something
    }

    @Override
    public void onDataInfo(Station station, DataInfo dataInfo) {

    }
}
