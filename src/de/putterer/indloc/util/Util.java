package de.putterer.indloc.util;

public class Util {

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
}
