package de.putterer.indloc.heart;

import jwave.Transform;
import jwave.transforms.FastWaveletTransform;
import jwave.transforms.wavelets.daubechies.Daubechies2;
import jwave.transforms.wavelets.haar.Haar1;
import jwave.transforms.wavelets.other.CDF53;

import java.util.Arrays;
import java.util.stream.Collectors;

public class HeartPeriodicity {


	public static void main(String args[]) {
		Transform transform = new Transform(new FastWaveletTransform(new Daubechies2()));

		//TODO experiment with, plot DWT, WPT
		double[] signal = {1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0};

		double[] trans = transform.forward(signal);
		double[] reverse = transform.reverse(signal);
		System.out.println(Arrays.stream(trans).mapToObj(String::valueOf).collect(Collectors.joining(", ")));;
		System.out.println(Arrays.stream(reverse).mapToObj(String::valueOf).collect(Collectors.joining(", ")));;
	}
}
