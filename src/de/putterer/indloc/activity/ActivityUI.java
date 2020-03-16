package de.putterer.indloc.activity;

import de.putterer.indloc.Config;
import de.putterer.indloc.Station;
import de.putterer.indloc.csi.CSIInfo;
import de.putterer.indloc.csi.DataPreview;
import de.putterer.indloc.csi.messages.SubscriptionMessage;
import de.putterer.indloc.data.DataClient;
import de.putterer.indloc.data.DataConsumer;
import de.putterer.indloc.util.Logger;
import de.putterer.indloc.util.Util;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;

import static de.putterer.indloc.data.DataClient.addClient;

public class ActivityUI extends JPanel {
    private static final int WIDTH = 300;
    private static final int HEIGHT = 100;
    private static final Color lowColor = new Color(0, 184, 43);
    private static final Color highColor = new Color(197, 67, 0);
    private static final SubscriptionMessage.SubscriptionOptions subscriptionOptions = new SubscriptionMessage.SubscriptionOptions(
            new SubscriptionMessage.FilterOptions(DataClient.DEFAULT_ICMP_PAYLOAD_LENGTH)
    );
    private static final Station station = new Station(
            Config.STATION_10_MAC, "192.168.178.250",
            null, null,
            new ActivityDetector()
    );


    private static JFrame frame;
    private static final java.util.List<DataPreview> previews = new ArrayList<>();
    private final JLabel statusLabel = new JLabel("", SwingConstants.CENTER);

    public ActivityUI() {
        this.setLayout(new BorderLayout());

        this.add(statusLabel);
        statusLabel.setFont(new Font("Verdana", Font.PLAIN, 32));

        this.start();
    }

    private void onCsi(CSIInfo csi) {
        double avgVariance = Arrays.stream(station.getActivityDetector().getVariancePerSubcarrier()).average().orElse(0);

        SwingUtilities.invokeLater(() -> {
            if(avgVariance < 0.08) {
                statusLabel.setText("Stationary");
            } else if(avgVariance < 0.16) {
                statusLabel.setText("Minor movement");
            } else if(avgVariance < 0.24) {
                statusLabel.setText("Movement");
            } else {
                statusLabel.setText("Strong movement");
            }

            ActivityUI.this.setBackground(Util.blend(lowColor, highColor, (float)avgVariance / 0.30f));
            ActivityUI.this.setBackground(Color.getHSBColor(122f / 360f - Math.min(0.35f, (float)avgVariance / 0.30f * 0.2f), 0.8f, 0.8f));
        });

        previews.forEach(p -> p.setData(csi));
    }

    private void start() {
        addClient(new DataClient(station, subscriptionOptions, new DataConsumer<>(CSIInfo.class, this::onCsi)));
    }

    public static void main(String args[]) {
        Logger.setLogLevel(Logger.Level.INFO);

        previews.add(new DataPreview(new DataPreview.PhaseDiffVariancePreview(station, DataPreview.PhaseDiffVariancePreview.SUBCARRIER_AVG, DataPreview.PhaseDiffVariancePreview.SUBCARRIER_MAX)));
        previews.add(new DataPreview(new DataPreview.PhaseDiffEvolutionPreview(0, 2, 10, 30, 50)));

        frame = new JFrame("Activity Detection - Fabian Putterer - TUM");
        frame.setSize(WIDTH, HEIGHT);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);

        frame.add(new ActivityUI());

        frame.setVisible(true);
    }
}
