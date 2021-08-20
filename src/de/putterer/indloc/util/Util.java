package de.putterer.indloc.util;

import de.putterer.indloc.csi.CSIInfo;

import java.awt.*;

public class Util {

	public static float cyclicAngleMean(double... angles) {
		CSIInfo.Complex sum = new CSIInfo.Complex(0, 0);
		for(double a : angles) {
			sum = sum.add(CSIInfo.Complex.fromAmplitudePhase(10000, a));
		}
		return (float) sum.getPhase();
	}

	public static float square(float f) {
		return f * f;
	}

	public static double square(double f) {
		return f * f;
	}

	public static float euclidean(float... f) {
		float res = 0f;
		for(int i = 0;i < f.length;i++) {
			res += f[i] * f[i];
		}
		return (float) Math.sqrt(res);
	}

	public static float manhattan(float... f) {
		float res = 0f;
		for(int i = 0;i < f.length;i++) {
			res += f[i];
		}
		return res;
	}

	public static Color blend(Color c1, Color c2, float w) {
		w = Math.max(0.0f, Math.min(1.0f, w));
		float w_i = 1.0f - w;

		return new Color(
				(int)(c1.getRed() * w_i + c2.getRed() * w),
				(int)(c1.getGreen() * w_i + c2.getGreen() * w),
				(int)(c1.getBlue() * w_i + c2.getBlue() * w)
		);
	}


	public static double db(double x) {
		return 10.0 * Math.log10(x);
	}

	public static double dbinv(double x) {
		return Math.pow(10.0, x / 10.0);
	}
}
