package de.putterer.indloc.respiratory;

import de.putterer.indloc.util.Logger;
import lombok.Getter;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.SingularValueDecomposition;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class SubcarrierSelector {

	private static final Executor threadPool = Executors.newFixedThreadPool(8);
	private static final RecurrencePlotPreview recurrencePlotPreview = new RecurrencePlotPreview();

	private final int previousSubcarrier;
	private final int totalSubcarriers;
	private final Function<Integer, double[]> respiratoryPhaseSupplier;

	@Getter
	private final double[][] processedPhaseByCarrier;
	@Getter
	private int selectedCarrier;


	public SubcarrierSelector(int previousSubcarrier, int totalSubcarriers, Function<Integer, double[]> respiratoryPhaseSupplier) {
		this.previousSubcarrier = previousSubcarrier;
		this.totalSubcarriers = totalSubcarriers;
		this.respiratoryPhaseSupplier = respiratoryPhaseSupplier;

		processedPhaseByCarrier = new double[totalSubcarriers][];
		selectedCarrier = previousSubcarrier;
	}

	public void runSelection() {
		long time = System.currentTimeMillis();

		List<CompletableFuture<double[]>> futures =
				IntStream.range(0, totalSubcarriers).mapToObj(carrier ->
						CompletableFuture.supplyAsync(() -> respiratoryPhaseSupplier.apply(carrier), threadPool)
				).collect(Collectors.toList());

		for(int i = 0;i < totalSubcarriers;i++) {
			try {
				processedPhaseByCarrier[i] = futures.get(i).get();
			} catch(InterruptedException | ExecutionException e) {
				e.printStackTrace();
			}
		}

		//TODO: recurrence plot resolution
		int samples = processedPhaseByCarrier[0].length;
		double epsilon = 0.05;

		double maxSingularPct = 0.0;
		int maxCarrier = selectedCarrier;
		BufferedImage maxRecurrencePlot = null;

		for(int carrier = 0;carrier < processedPhaseByCarrier.length;carrier++) {
			//Render recurrence plot
			BufferedImage image = new BufferedImage(samples, samples, BufferedImage.TYPE_INT_ARGB);
			Graphics g = image.createGraphics();
			for(int x = 0;x < samples;x++) {
				for(int y = 0;y < samples;y++) {
					double diff = Math.abs(processedPhaseByCarrier[carrier][x] - processedPhaseByCarrier[carrier][y]);
					//diff = Math.abs(Math.sin(0.1 * x)- Math.sin(0.1 * y));
//					recurrencePlot[x][y] = diff <= epsilon ? 1.0 : 0.0;

					float val = (float) Math.min(1.0, diff / epsilon); //lower is better
					image.setRGB(x, y, new Color(val, val, val, 1f).getRGB());
				}
			}

			//rotate recurrence plot
			BufferedImage rotatedImage = new BufferedImage(samples, samples, BufferedImage.TYPE_INT_ARGB);
			g = rotatedImage.createGraphics();
			g.setColor(Color.WHITE);
			rotatedImage.createGraphics().fillRect(0, 0, rotatedImage.getWidth(), rotatedImage.getHeight());
			rotatedImage.createGraphics().drawImage(
					new AffineTransformOp(
							AffineTransform.getRotateInstance(Math.toRadians(45), samples / 2.0, samples / 2.0),
							AffineTransformOp.TYPE_BILINEAR)
					.filter(image, null),
					//TODO nearest neighbour?
					0, 0,
					null
			);


			//TODO: test selecting the most recurrent
			double[][] recurrencePlot = new double[rotatedImage.getWidth()][rotatedImage.getHeight()];
			for(int x = 0;x < rotatedImage.getWidth();x++) {
				for(int y = 0;y < rotatedImage.getHeight();y++) {
					recurrencePlot[x][y] = 1.0f - (new Color(rotatedImage.getRGB(x, y)).getRed()/ 255.0f) > 0.5 ? 1 : 0;
				}
			}
			RealMatrix matrix = new Array2DRowRealMatrix(recurrencePlot);
			SingularValueDecomposition svd = new SingularValueDecomposition(matrix);
			double maxSingularValue = Arrays.stream(svd.getSingularValues()).max().getAsDouble();
			double singularPct = maxSingularValue / (Arrays.stream(svd.getSingularValues()).sum());

			if(singularPct > maxSingularPct) {
				maxSingularPct = singularPct;
				maxCarrier = carrier;
				maxRecurrencePlot = rotatedImage;
			}
		}

		if(maxRecurrencePlot != null) {
			recurrencePlotPreview.setImage(maxRecurrencePlot);
//			try {
//				ImageIO.write(maxRecurrencePlot, "PNG", Paths.get("recurrencePlot.png").toFile());
//			} catch (IOException e) {
//				e.printStackTrace();
//			}
		}

		this.selectedCarrier = maxCarrier;
		Logger.debug("SubcarrierSelector ran in %d ms, selected carrier: %d, singularPct: %.3f", System.currentTimeMillis() - time, selectedCarrier, maxSingularPct);
	}

	public double[] getProcessedPhaseForCarrier(int subcarrier) {
		if(processedPhaseByCarrier[subcarrier] == null) {
			processedPhaseByCarrier[subcarrier] = respiratoryPhaseSupplier.apply(subcarrier);
		}

		return processedPhaseByCarrier[subcarrier];
	}
}
