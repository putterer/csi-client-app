package de.putterer.indloc.activity;

import de.putterer.indloc.Config;
import de.putterer.indloc.Station;
import de.putterer.indloc.csi.CSIInfo;
import de.putterer.indloc.csi.DataPreview;
import de.putterer.indloc.csi.messages.SubscriptionMessage;
import de.putterer.indloc.data.DataClient;
import de.putterer.indloc.data.DataConsumer;
import de.putterer.indloc.data.DataInfo;
import de.putterer.indloc.ui.UIComponentWindow;
import de.putterer.indloc.util.Util;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;

import static de.putterer.indloc.data.DataClient.addClient;

public class ActivityUI extends UIComponentWindow {

    private static final Color lowColor = new Color(0, 184, 43);
    private static final Color highColor = new Color(197, 67, 0);

    private static final java.util.List<DataPreview> previews = new ArrayList<>();

    private final Station station;
    private final SubscriptionMessage.SubscriptionOptions subscriptionOptions;

    private final JLabel statusLabel = new JLabel("", SwingConstants.CENTER);

    public ActivityUI(Station station, SubscriptionMessage.SubscriptionOptions subscriptionOptions) {
        super("Activity Detector", 300, 100);
        this.station = station;
        this.subscriptionOptions = subscriptionOptions;

        this.setLayout(new BorderLayout());

        this.add(statusLabel);
        statusLabel.setFont(new Font("Verdana", Font.PLAIN, 32));

        setupFinished();
    }

    @Override
    public void onDataInfo(Station station, DataInfo info) {
        if(! (info instanceof CSIInfo)) {
            return;
        }

        CSIInfo csi = (CSIInfo) info;

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






    public static void main(String args[]) {
        Station station = new Station(
                Config.STATION_10_MAC, "192.168.178.250",
                null, null,
                new ActivityDetector()
        );

        SubscriptionMessage.SubscriptionOptions subscriptionOptions = new SubscriptionMessage.SubscriptionOptions(
                new SubscriptionMessage.FilterOptions(DataClient.DEFAULT_ICMP_PAYLOAD_LENGTH)
        );

        previews.add(new DataPreview(new DataPreview.PhaseDiffVariancePreview(station, DataPreview.PhaseDiffVariancePreview.SUBCARRIER_AVG, DataPreview.PhaseDiffVariancePreview.SUBCARRIER_MAX)));
        previews.add(new DataPreview(new DataPreview.PhaseDiffEvolutionPreview(0, 2, -1,10, 30, 50)));

        ActivityUI activityUI = new ActivityUI(station, subscriptionOptions);

        addClient(new DataClient(station, subscriptionOptions, new DataConsumer<>(CSIInfo.class, csi -> activityUI.onDataInfo(station, csi))));

        activityUI.getFrame().setVisible(true);
    }
}
