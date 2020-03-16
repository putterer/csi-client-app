package de.putterer.indloc.activity;

import de.putterer.indloc.csi.CSIInfo;
import de.putterer.indloc.util.CSIUtil;
import lombok.Getter;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import static de.putterer.indloc.util.CSIUtil.mean;
import static de.putterer.indloc.util.Util.square;

public class ActivityDetector {

    public static final int CSI_ACTIVITY_HISTORY_LENGTH = 20;
    private static final int CSI_ACTIVITY_ANTENNA_TX = 0;
    private static final int CSI_ACTIVITY_ANTENNA_RX1 = 0;
    private static final int CSI_ACTIVITY_ANTENNA_RX2 = 2;

    private final List<CSIInfo> csiHistory = new LinkedList<>();
    @Getter
    private double[] variancePerSubcarrier = null;

    private final double[][] previousPhaseMean = new double[3][3];

    public void onCsiInfo(CSIInfo csi) {
        //TODO: only save phase-> allows processing?
        csiHistory.add(csi);
        while(csiHistory.size() > CSI_ACTIVITY_HISTORY_LENGTH) {
            csiHistory.remove(0);
        }

        int subcarriers = csi.getCsi_status().getNum_tones();
        variancePerSubcarrier = new double[subcarriers];

        for(int i = 0;i < subcarriers;i++) {
            int finalI = i;
            double[] phaseDiff = csiHistory.stream().mapToDouble(
                    c -> c.getCsi_matrix()[CSI_ACTIVITY_ANTENNA_RX1][CSI_ACTIVITY_ANTENNA_TX][finalI].getPhase()
                            - c.getCsi_matrix()[CSI_ACTIVITY_ANTENNA_RX2][CSI_ACTIVITY_ANTENNA_TX][finalI].getPhase())
                    .toArray();

            CSIUtil.unwrapPhase(phaseDiff);

            double mean = mean(phaseDiff);
            double variance = Arrays.stream(phaseDiff).map(diff -> square(CSIUtil.bound(diff - mean))).sum() / (double)csiHistory.size();

            variancePerSubcarrier[i] = variance;
        }
    }
}
