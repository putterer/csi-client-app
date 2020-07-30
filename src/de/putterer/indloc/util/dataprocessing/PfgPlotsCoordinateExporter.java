package de.putterer.indloc.util.dataprocessing;

import de.putterer.indloc.csi.CSIInfo;
import de.putterer.indloc.csi.CSIReplay;
import de.putterer.indloc.util.CSIUtil;
import de.putterer.indloc.util.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Tool for exporting CSIInfo csi data to a PfgPlot (LaTeX)
 */
public class PfgPlotsCoordinateExporter {

    public static void main(String args[]) throws IOException, ClassNotFoundException {
        if(args.length == 0) {
            Logger.error("Missing argument.");
            return;
        }

        Path path = Paths.get(args[0]);

        System.out.println("RX0 TX1->3, RX1 TX1->3, RX2 TX1->3");
        CSIReplay replay = new CSIReplay(path, 1, false);
        List<CSIInfo> csi = new ArrayList<>(replay.getCSI());
        for(CSIInfo info : csi) {
            for(int rx = 0;rx < 3;rx++) {
                for(int tx = 0;tx < 3;tx++) {
//                    for(int subcarrier = 0;subcarrier < info.getNumTones();subcarrier++) {
//                        System.out.printf("(%d,%d) ",
//                                info.getCsi_matrix()[rx][tx][subcarrier].getReal(),
//                                info.getCsi_matrix()[rx][tx][subcarrier].getImag()
//                        );
//                    }
//                    System.out.print("\n");

                    int finalRx = rx;
                    int finalTx = tx;
                    double[] phase = IntStream.range(0, info.getNumTones()).mapToDouble(i -> info.getCsi_matrix()[finalRx][finalTx][i].getPhase()).toArray();
                    CSIUtil.unwrapPhase(phase);
                    System.out.print("Phase: ");
                    for(int subcarrier = 0;subcarrier < info.getNumTones();subcarrier++) {
                        System.out.printf("(%d,%f) ",
                                subcarrier + 1,
                                phase[subcarrier]
                        );
                    }
                    System.out.print("\n");
                }
            }

//            System.out.println("Phase difference 01:");
//            double phaseDiff[] = PhaseOffsetCalibration.getPhaseDiff(info, 0, 0, 1);
//            for(int i = 0;i < info.getNumTones();i++) {
//                System.out.printf("(%d,%f) ", i + 1, phaseDiff[i]);
//            }

            System.out.print("\n\n");
        }

        System.exit(0);
    }
}
