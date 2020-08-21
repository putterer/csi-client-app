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


    public ReplayUI(CsiUserInterface csiUserInterface, boolean isReplayLoaded) {
        super("Replay", 420, 200);
        this.csiUserInterface = csiUserInterface;
        this.isReplayLoaded = isReplayLoaded;

        this.setLayout(null);
        initUI();

        setupFinished();
    }

    private void initUI() {
        loadReplayButton.setBounds(10, 10, 400, 20);
        this.add(loadReplayButton);
        loadReplayButton.addActionListener(this::pickAndLoadReplayFile);

        if(! isReplayLoaded) {
            return;
        }

        //TODO:
        // play, pause, step button
        // time label
        // current csi out of total label
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

        Logger.info("Loading replay %s", chooser.getSelectedFile().toPath().toAbsolutePath().toString()); // TODO: actually do something
    }

    @Override
    public void onDataInfo(Station station, DataInfo dataInfo) {

    }
}
