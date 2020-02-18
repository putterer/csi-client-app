package de.putterer.indloc.csi.calibration;

import de.putterer.indloc.Config;
import de.putterer.indloc.csi.CSIInfo;
import de.putterer.indloc.util.Logger;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * Models the phase offset due to internal wiring of the router and the downconversion step
 */
@Getter
@RequiredArgsConstructor
public enum PhaseOffset {
    // (Ant01, Ant02), (Ant01, Ant02), (Ant01, Ant02)
    STATION_5(Config.STATION_5_MAC, null, null, null),
    STATION_6(Config.STATION_6_MAC, new double[] {-10.0, -105.0}, new double[] {52.0, 14.0}, new double[] {-73.0, -43.5}),
    STATION_7(Config.STATION_7_MAC, null, null, null),
    STATION_10(Config.STATION_10_MAC, null, null, null),
    DUMMY(null, new double[] {0.0, 0.0}, new double[] {0.0, 0.0}, new double[] {0.0, 0.0});

    public static final int ANTENNA_ZERO_ONE = 893498;
    public static final int ANTENNA_ZERO_TWO = 230958;

    private final String macAddress;
    private final double[] phaseShiftCrossed; // the phase shift when assuming the 120 degree shift is caused by the cabling therefore cancelling out
    private final double[] phaseShiftNormal; // the phase shift assuming the 120 degree shift is caused by the device, the values seen more often
    private final double[] phaseShiftNormalUnlikely; // the values seen less often

    // Antenna 0 --> [shift between 01], Antenna 1 --> [shift between 02]
    public CSIInfo.Complex shift(CSIInfo.Complex value, PhaseOffsetType type, int ant, int possibility) {
        int antenna = ant == ANTENNA_ZERO_ONE ? 0 : (ant == ANTENNA_ZERO_TWO ? 1 : -1);
        double shift = type == PhaseOffsetType.CROSSED ? phaseShiftCrossed[antenna] : (type == PhaseOffsetType.NORMAL ? phaseShiftNormal[antenna] : phaseShiftNormalUnlikely[antenna]);
        shift = Math.toRadians(shift);

        if(possibility == 0) {
            throw new RuntimeException("NO NO AND NO! THIS POSSIBILITY DOESN'T EXIST");
        }

        if((possibility == 2 && ant == ANTENNA_ZERO_ONE)
            || (possibility == 3 && ant == ANTENNA_ZERO_TWO)
            || (possibility == 4)) {
            shift += Math.PI;
        }
//        shift = CSIUtil.bound(shift);

        return CSIInfo.Complex.fromAmplitudePhase(value.getAmplitude(), value.getPhase() - shift);
    }

    public CSIInfo.Complex shift1(CSIInfo.Complex value, PhaseOffsetType type, int antenna) {
        return shift(value, type, antenna, 1);
    }

    public CSIInfo.Complex shift2(CSIInfo.Complex value, PhaseOffsetType type, int antenna) {
        return shift(value, type, antenna, 2);
    }

    public CSIInfo.Complex[] shift(CSIInfo.Complex[] values, PhaseOffsetType type, int antenna, int possibility) {
        return Arrays.stream(values).map(v -> shift(v, type, antenna, possibility)).toArray(CSIInfo.Complex[]::new);
    }

    public CSIInfo.Complex[][][] shiftMatrix(CSIInfo.Complex[][][] matrix, PhaseOffsetType type, int possibility) {
        CSIInfo.Complex[][][] result = new CSIInfo.Complex[3][3][114];
        result[0] = matrix[0];
        for(int rx = 1;rx < 3;rx++) {
            for(int tx = 0;tx < 3;tx++) {
                result[rx][tx] = shift(matrix[rx][tx], type, rx == 1 ? ANTENNA_ZERO_ONE : ANTENNA_ZERO_TWO, possibility);
            }
        }
        return result;
    }

    public static PhaseOffset getByMac(String macAddress) {
        PhaseOffset offset = Arrays.stream(PhaseOffset.values())
                .filter(o -> o.getMacAddress().equalsIgnoreCase(macAddress))
                .findFirst()
                .orElse(null);

        if(offset != null && offset.phaseShiftCrossed == null) {
            Logger.error("DUMMY STATION, NO PHASE SHIFT APPLIED");
            offset = PhaseOffset.DUMMY;
        }

        return offset;
    }

    public static enum PhaseOffsetType {
        CROSSED, NORMAL, NORMAL_UNLIKELY; // see thesis
    }
}