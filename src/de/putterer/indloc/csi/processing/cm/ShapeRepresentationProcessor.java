package de.putterer.indloc.csi.processing.cm;

import de.putterer.indloc.csi.CSIInfo;
import de.putterer.indloc.util.Vector;

public class ShapeRepresentationProcessor {

    private final boolean relative; // whether to store the angles between subcarriers relative to previous carriers or relative to the absolute position

    public ShapeRepresentationProcessor(boolean relative) {
        this.relative = relative;
    }

    /**
     * transforms a curve of complex data points into a list of angles and distances between those individual data points
     * @return
     */
    public Vector[] process(CSIInfo.Complex[] data) {
        double[] dists = new double[data.length - 1];
        double[] angles = new double[data.length - 1];
        double[] relativeAngles = new double[data.length - 1];

        for(int i = 0;i < dists.length;i++) {
            dists[i] = data[i+1].sub(data[i]).getAmplitude();
            angles[i] = data[i+1].sub(data[i]).getPhase();
        }

        double previousAngle = 0.0;
        for(int i = 0;i < dists.length;i++) {
            relativeAngles[i] = angles[i] - previousAngle;
            previousAngle = angles[i];
        }

        Vector[] res = new Vector[dists.length];
        for(int i = 0;i < dists.length;i++) {
            res[i] = new Vector((float) dists[i], (float) (relative ? relativeAngles[i] : angles[i]));
        }

        return res;
    }

}
