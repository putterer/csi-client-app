package de.putterer.indloc.csi.calibration;

import de.putterer.indloc.csi.CSIInfo;
import de.putterer.indloc.csi.CSIReplay;
import de.putterer.indloc.util.CSIUtil;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public class PhaseOffsetCalibration {

    /**
     * calculates the phase offset between 2 rx antennas
     */
    public static void main(String args[]) throws IOException {
        if (args.length < 4) {
            throw new IllegalArgumentException("Not enough arguments");
        }

        CSIReplay replay = new CSIReplay(Paths.get(args[0]), 1, false, null);
        int txAntenna = Integer.parseInt(args[1]);
        int rxAntenna0 = Integer.parseInt(args[2]);
        int rxAntenna1 = Integer.parseInt(args[3]);
        List<CSIInfo> csi = replay.getCSI();

        double totalAverageShift = getPhaseDiff(csi, txAntenna, rxAntenna0, rxAntenna1);

        System.out.printf("\n Average: %f\n", totalAverageShift);
    }

    public static double getPhaseDiff(List<CSIInfo> csi, int txAntenna, int rxAntenna0, int rxAntenna1) {
//        DataPreview preview = new DataPreview(new DataPreview.CSIPlotPreview(3, 3));
//        preview.setData(csi.get(1));

        double totalAverage = 0;

        for (CSIInfo info : csi) {
            double[] diff = getPhaseDiff(info, txAntenna, rxAntenna0, rxAntenna1);
            double average = Arrays.stream(diff).average().getAsDouble();
            totalAverage += average;
//            Arrays.stream(diff).mapToObj(d -> d + " ").forEach(System.out::print);
//            System.out.print("\n");
        }

        totalAverage /= (double) csi.size();
        return totalAverage;
    }

    public static double[] getPhaseDiff(CSIInfo csi, int txAntenna, int rxAntenna0, int rxAntenna1) {
        CSIInfo.Complex[] ant0 = csi.getCsi_matrix()[rxAntenna0][txAntenna];
        CSIInfo.Complex[] ant1 = csi.getCsi_matrix()[rxAntenna1][txAntenna];
        int subcarriers = csi.getNumTones();

        //TODO: unwrap, stack

        double[] result = new double[subcarriers];

        for(int i = 0;i < subcarriers;i++) {
            result[i] = getPhaseDiff(ant0[i], ant1[i]);
        }

        return result;
    }

    private static double getPhaseDiff(CSIInfo.Complex ant0, CSIInfo.Complex ant1) {
        return CSIUtil.bound(ant1.getPhase() - ant0.getPhase());
    }
}
