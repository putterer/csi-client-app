package de.putterer.indloc.csi.processing.cm;

import de.putterer.indloc.csi.CSIInfo;
import de.putterer.indloc.util.Util;
import de.putterer.indloc.util.Vector;

import java.util.stream.IntStream;

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


    public void wrapAngle(Vector[] data) {
        for(int i = 0; i < data.length;i++) {
            while(data[i].getY() > 2.0 * Math.PI) {
                data[i].setY(data[i].getY() - (float)(2.0 * Math.PI));
            }while(data[i].getY() < 0.0) {
                data[i].setY(data[i].getY() + (float)(2.0 * Math.PI));
            }
        }
    }

    public void shiftAngle(Vector[] data, float offset) {
        for(int i = 0; i < data.length;i++) {
            data[i].setY(data[i].getY() + offset);
        }
    }

    public void shiftAngleZeroFirstCarriers(Vector[] data, int firstCarrierCount) {
        float angleMean = Util.cyclicAngleMean(IntStream.range(0, firstCarrierCount).mapToDouble(i -> data[i].getY()).toArray());
        shiftAngle(data, -angleMean);
    }

    public void unwrapAngle(Vector[] data, boolean relativeToZero) {
        data[0].setY(unwrapZero(data[0].getY()));

        for(int i = 1;i < data.length;i++) {
            // take into account multiple previous samples, as the previous one might be an outlier
            float previousAngle = 0.0f;
            float iterations = 0;
            for(int k = Math.max(0, i - 5); k < i; k++) {
                previousAngle += data[k].getY();
                iterations++;
            }
            previousAngle /= iterations;

            if(relativeToZero) {
                data[i].setY(unwrapZero(data[i].getY()));
            } else {
                while(data[i].getY() - previousAngle > Math.PI) {
                    data[i].setY((float) (data[i].getY() - 2 * Math.PI));
                }
                while(data[i].getY() - previousAngle < -Math.PI) {
                    data[i].setY((float) (data[i].getY() + 2 * Math.PI));
                }
            }
        }
    }

    private final float unwrapZero(float value) {
        if(value > Math.PI) {
            return (float) (value - 2 * Math.PI);
        } else if(value < -Math.PI) {
            return (float) (value + 2 * Math.PI);
        } else {
            return value;
        }
    }
}
