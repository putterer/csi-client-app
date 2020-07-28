package de.putterer.indloc.csi;

import de.putterer.indloc.Station;
import de.putterer.indloc.csi.calibration.AndroidInfo;
import de.putterer.indloc.csi.processing.RespiratoryPhaseProcessor;
import de.putterer.indloc.data.DataInfo;
import de.putterer.indloc.util.Logger;
import lombok.Getter;
import org.knowm.xchart.SwingWrapper;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.XYSeries.XYSeriesRenderStyle;
import org.knowm.xchart.style.Styler;
import org.knowm.xchart.style.Styler.ChartTheme;
import org.knowm.xchart.style.Styler.LegendPosition;

import javax.swing.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

import static de.putterer.indloc.util.CSIUtil.*;
import static de.putterer.indloc.util.Util.euclidean;
import static de.putterer.indloc.util.Util.manhattan;

/**
 * A preview of incoming / replayed CSI data
 * Requires XChart: https://github.com/knowm/XChart
 */
public class DataPreview {
	
	public static final Styler.ChartTheme CHART_THEME = ChartTheme.XChart;

	// The data currently being displayed
	private DataInfo dataInfo;

	// The preview mode used for displaying
	@Getter
	private PreviewMode mode;
	
	private final XYChart chart;
	private final SwingWrapper<XYChart> wrapper;
	@Getter
	private final JFrame frame;
	
	public DataPreview(PreviewMode mode) {
		this.mode = mode;
		
		chart = mode.createChart();		
		
		wrapper = new SwingWrapper<XYChart>(chart);
		this.frame = wrapper.displayChart();
	}
	
	public void setData(DataInfo info) {
		if(! frame.isVisible()) {
			Logger.error("Frame not visible, aborting setting data info");
			return;
		}

		this.dataInfo = info;
		
		mode.updateChart(info, chart);
		
		SwingUtilities.invokeLater(() -> {
			wrapper.repaintChart();
		});
	}

	@Getter
	public static abstract class PreviewMode {
		protected int width;
		protected int height;
		
		public abstract XYChart createChart();
		public abstract void updateChart(DataInfo dataInfo, XYChart chart);
	}

	public static class PhaseDiffVariancePreview extends PreviewMode {
		{ width = 700; height = 500; }
		private final int dataWidth = 150;

		public static final int SUBCARRIER_MAX = 120;
		public static final int SUBCARRIER_AVG = 121;

		private final Station station;
		private final int[] subcarriers;

		private final List<Double>[] previousDataPoints;

		/**
		 * @param station the station of which to use the activity tracker
		 * @param subcarriers the subcarriers to display
		 */
		public PhaseDiffVariancePreview(Station station, int... subcarriers) {
			this.station = station;
			this.subcarriers = subcarriers;

			previousDataPoints = new List[subcarriers.length];
			for(int i = 0;i < subcarriers.length;i++) {
				previousDataPoints[i] = new LinkedList<>();
				DoubleStream.generate(() -> 0.0).limit(dataWidth).forEach(previousDataPoints[i]::add);
			}
		}

		@Override
		public XYChart createChart() {
			XYChart chart = new XYChartBuilder()
					.width(width)
					.height(height)
					.title("PhaseDiffVariance")
					.xAxisTitle("Time")
					.yAxisTitle("Variance")
					.theme(CHART_THEME)
					.build();

			chart.getStyler().setLegendPosition(LegendPosition.InsideNE);
			chart.getStyler().setDefaultSeriesRenderStyle(XYSeriesRenderStyle.Line);
			chart.getStyler().setYAxisMin(-Math.PI * 0.02);
			chart.getStyler().setYAxisMax(Math.PI * 0.35);
			chart.getStyler().setXAxisMin((double) dataWidth);
			chart.getStyler().setXAxisMax(0.0);

			for (int subcarrierIndex = 0;subcarrierIndex < subcarriers.length;subcarrierIndex++) {
				String subcarrierName = "";
				switch(subcarriers[subcarrierIndex]) {
					case SUBCARRIER_MAX: subcarrierName = "max";break;
					case SUBCARRIER_AVG: subcarrierName = "avg";break;
					default: subcarrierName += this.subcarriers[subcarrierIndex];
				}
				String seriesName = String.format("sub: %s", subcarrierName);
				chart.addSeries(seriesName, new double[dataWidth]);
			}
			return chart;
		}

		@Override
		public void updateChart(DataInfo dataInfo, XYChart chart) {
			if(! (dataInfo instanceof CSIInfo)) {
				return;
			}
			CSIInfo csi = (CSIInfo)dataInfo;

			int subcarriers = csi.getCsi_status().getNum_tones();
			var detector = station.getActivityDetector();

			double[] xData = IntStream.range(0, dataWidth).mapToDouble(i -> i).toArray();
			double[] variances = detector.getVariancePerSubcarrier();

			for (int subcarrierIndex = 0;subcarrierIndex < this.subcarriers.length;subcarrierIndex++) {
				List<Double> previousList = this.previousDataPoints[subcarrierIndex];
				if(previousList.size() == dataWidth) {
					previousList.remove(previousList.size() - 1);
				}
				if(this.subcarriers[subcarrierIndex] < 113) {
					previousList.add(0, variances[this.subcarriers[subcarrierIndex]]);
				} else if (this.subcarriers[subcarrierIndex] == SUBCARRIER_MAX) {
					previousList.add(0, Arrays.stream(variances).max().getAsDouble());
				} else if (this.subcarriers[subcarrierIndex] == SUBCARRIER_AVG) {
					previousList.add(0, Arrays.stream(variances).average().getAsDouble());
				}

				String subcarrierName = "";
				switch(this.subcarriers[subcarrierIndex]) {
					case SUBCARRIER_MAX: subcarrierName = "max";break;
					case SUBCARRIER_AVG: subcarrierName = "avg";break;
					default: subcarrierName += this.subcarriers[subcarrierIndex];
				}
				String seriesName = String.format("sub: %s", subcarrierName);
				chart.updateXYSeries(seriesName, xData, previousList.stream().mapToDouble(d -> d).toArray(), null);
			}
		}
	}

	public static class AndroidEvolutionPreview extends PreviewMode {
		{ width = 700; height = 500; }
		private final int dataWidth = 150;

		private final List<Double>[] previousDataPoints;

		private final float limit;
		private final AndroidDataType[] androidDataTypes;

		public AndroidEvolutionPreview(float limit, AndroidDataType... androidDataTypes) {
			this.limit = limit;
			this.androidDataTypes = androidDataTypes;
			previousDataPoints = new List[androidDataTypes.length];
			for (int i = 0; i < androidDataTypes.length; i++) {
				previousDataPoints[i] = new LinkedList<>();
				for(int j = 0;j < dataWidth;j++) {
					previousDataPoints[i].add(0.0);
				}
			}
		}

		@Override
		public XYChart createChart() {
			XYChart chart = new XYChartBuilder()
					.width(width)
					.height(height)
					.title("Android position/acceleration Preview")
					.xAxisTitle("Subcarrier")
					.yAxisTitle("Magnitude")
					.theme(CHART_THEME)
					.build();

			chart.getStyler().setLegendPosition(LegendPosition.InsideNE);
			chart.getStyler().setDefaultSeriesRenderStyle(XYSeriesRenderStyle.Line);
			chart.getStyler().setYAxisMin((-1.0) * limit);
			chart.getStyler().setYAxisMax(( 1.0) * limit);
			chart.getStyler().setXAxisMin((double) dataWidth);
			chart.getStyler().setXAxisMax(0.0);

			for(AndroidDataType androidDataType : androidDataTypes) {
				chart.addSeries(androidDataType.name(), new double[dataWidth]);
			}
			return chart;
		}

		@Override
		public void updateChart(DataInfo dataInfo, XYChart chart) {
			if(! (dataInfo instanceof AndroidInfo)) {
				return;
			}
			AndroidInfo info = (AndroidInfo) dataInfo;


			double[] xData = IntStream.range(0, dataWidth).mapToDouble(i -> i).toArray();

			for (int accelerationType = 0; accelerationType < androidDataTypes.length; accelerationType++) {
				List<Double> previousList = this.previousDataPoints[accelerationType];
				if(previousList.size() == dataWidth) {
					previousList.remove(previousList.size() - 1);
				}
				previousList.add(0, (double) androidDataTypes[accelerationType].valueDerivationFunction.apply(info));
				chart.updateXYSeries(androidDataTypes[accelerationType].name(), xData, previousList.stream().mapToDouble(d -> d).toArray(), null);


				// http://www.trex-game.skipser.com/
//				if(Math.abs(previousList.get(0)) > 0.1) {
//					try {
//						Robot robot = new Robot();
////						robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
////						robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
//						robot.keyPress(KeyEvent.VK_SPACE);
//						robot.keyRelease(KeyEvent.VK_SPACE);
//					} catch (AWTException e) {
//						e.printStackTrace();
//					}
//
//				}
			}
		}

		public enum AndroidDataType {
			X( i -> i.accel()[0] - i.getCalibration()[0]),
			Y( i -> i.accel()[1] - i.getCalibration()[1]),
			Z( i -> i.accel()[2] - i.getCalibration()[2]),
			EUCLIDEAN( i -> euclidean(i.accel()) - euclidean(i.getCalibration())),
			MANHATTAN( i -> manhattan(i.accel()) - manhattan(i.getCalibration())),
			MAX( i -> Math.max(i.accel()[0], Math.max(i.accel()[1], i.accel()[2])) );

			AndroidDataType(Function<AndroidInfo, Float> valueDerivationFunction) { this.valueDerivationFunction = valueDerivationFunction; }
			@Getter private final Function<AndroidInfo, Float> valueDerivationFunction;
		}
	}

	/**
	 * Previews one property, amplitude or phase, across all subcarriers
	 */
	public static class SubcarrierPropertyPreview extends PreviewMode {
		{ width = 700; height = 500; }
		
		public enum PropertyType { AMPLITUDE, PHASE }
		private final PropertyType type;
		private final int rxAntennas; // number of rx antennas to display
		private final int txAntennas; // number of tx antennas to display
		private final double previousMeanPhase[][];

		/**
		 * @param type amplitude or phase
		 * @param rxAntennas the number of rx antennas to display
		 * @param txAntennas the number of tx antennas to display
		 */
		public SubcarrierPropertyPreview(PropertyType type, int rxAntennas, int txAntennas) {
			this.type = type;
			this.rxAntennas = rxAntennas;
			this.txAntennas = txAntennas;

			previousMeanPhase = new double[rxAntennas][txAntennas];
		}
		
		@Override
		public XYChart createChart() {
			XYChart chart = new XYChartBuilder()
					.width(width)
					.height(height)
					.title("CSI Preview")
					.xAxisTitle("Subcarrier")
					.yAxisTitle(type == PropertyType.AMPLITUDE ? "Amplitude" : "Phase")
					.theme(CHART_THEME)
					.build();
			
			chart.getStyler().setLegendPosition(LegendPosition.InsideNE);
			chart.getStyler().setDefaultSeriesRenderStyle(type == PropertyType.AMPLITUDE ? XYSeriesRenderStyle.Line : XYSeriesRenderStyle.Scatter);
			chart.getStyler().setYAxisMin(0.0);
			switch(type) {
			case AMPLITUDE: chart.getStyler().setYAxisMax(512.0);break;
			case PHASE: chart.getStyler().setYAxisMax(Math.PI * 2.5); chart.getStyler().setYAxisMin(Math.PI * -2.5);break;
			}
			chart.getStyler().setXAxisMin(0.0);
			chart.getStyler().setXAxisMax(114.0);
			
			for(int rx = 0;rx < rxAntennas;rx++) {
				for(int tx = 0;tx < txAntennas;tx++) {
					chart.addSeries(String.format("RX:%d, TX:%d", rx, tx), new double[114]);
				}
			}
			return chart;
		}

		@Override
		public void updateChart(DataInfo dataInfo, XYChart chart) {
			if(! (dataInfo instanceof CSIInfo)) {
				return;
			}
			CSIInfo csi = (CSIInfo)dataInfo;

			int subcarriers = csi.getCsi_status().getNum_tones();
			
			for(int rx = 0;rx < rxAntennas;rx++) {
				for(int tx = 0;tx < txAntennas;tx++) {
					double[] xData = new double[subcarriers];
					double[] yData = new double[subcarriers];
					for(int i = 0;i < subcarriers;i++) {
						xData[i] = i;
						switch(type) {
						case AMPLITUDE: yData[i] = csi.getCsi_matrix()[rx][tx][i].getAmplitude();break;
						case PHASE: yData[i] = csi.getCsi_matrix()[rx][tx][i].getPhase() - csi.getCsi_matrix()[0][0][0].getPhase();break;
						}
					}

					if(type == PropertyType.PHASE) {
						unwrapPhase(yData);
					}

					previousMeanPhase[rx][tx] = timeUnwrapped(yData, previousMeanPhase[rx][tx]);

					// move antennas above each other
//					while(yData[0] < csi.getCsi_matrix()[0][0][0].getPhase()) {
//						for(int i = 0;i < subcarriers;i++) {
//							yData[i] += 2 * Math.PI;
//						}
//					}

					chart.updateXYSeries(String.format("RX:%d, TX:%d", rx, tx), xData, yData, null);
				}
			}
		}
	}

	/**
	 * Displays the phase difference between 2 antennas
	 * data is unwrapped and shifted by mean before displaying
	 */
	public static class PhaseDiffPreview extends PreviewMode {
		{ width = 700; height = 500; }
		
		private final int rxAntenna1; // number of tx antennas to display
		private final int rxAntenna2; // number of tx antennas to display
		private final double[] previousPhaseMean;
		
		public PhaseDiffPreview(int rxAntenna1, int rxAntenna2) {
			this.rxAntenna1 = rxAntenna1;
			this.rxAntenna2 = rxAntenna2;

			previousPhaseMean = new double[2];
		}
		
		@Override
		public XYChart createChart() {
			XYChart chart = new XYChartBuilder()
					.width(width)
					.height(height)
					.title("CSI Preview")
					.xAxisTitle("Subcarrier")
					.yAxisTitle("Phase difference")
					.theme(CHART_THEME)
					.build();
			
			chart.getStyler().setLegendPosition(LegendPosition.InsideNE);
			chart.getStyler().setDefaultSeriesRenderStyle(XYSeriesRenderStyle.Line);
			chart.getStyler().setYAxisMin(-1.0 * Math.PI);
			chart.getStyler().setYAxisMax(1.0 * Math.PI);
			chart.getStyler().setXAxisMin(0.0);
			chart.getStyler().setXAxisMax(56.0);
			
			chart.addSeries(String.format("RX1:%d, RX2:%d", rxAntenna1, rxAntenna2), new double[114]);
			chart.addSeries(String.format("RX1:%d, RX2:%d total", rxAntenna1, rxAntenna2), new double[1]);
			return chart;
		}

		@Override
		public void updateChart(DataInfo dataInfo, XYChart chart) {
			if(! (dataInfo instanceof CSIInfo)) {
				return;
			}
			CSIInfo csi = (CSIInfo)dataInfo;

			int subcarriers = csi.getCsi_status().getNum_tones();
			if(subcarriers > 56) {
				chart.getStyler().setXAxisMax((double) subcarriers);
			}
			double[] xData = new double[subcarriers];
			double[] yData = new double[subcarriers];

			double[] rx1Phase = Arrays.stream(csi.getCsi_matrix()[rxAntenna1][0]).mapToDouble(CSIInfo.Complex::getPhase).toArray();
			double[] rx2Phase = Arrays.stream(csi.getCsi_matrix()[rxAntenna2][0]).mapToDouble(CSIInfo.Complex::getPhase).toArray();
			unwrapPhase(rx1Phase);
			unwrapPhase(rx2Phase);
			previousPhaseMean[0] = timeUnwrapped(rx1Phase, previousPhaseMean[0]);
			previousPhaseMean[1] = timeUnwrapped(rx2Phase, previousPhaseMean[1]);

			for(int i = 0;i < subcarriers;i++) {
				xData[i] = i;
				double diff = rx1Phase[i] - rx2Phase[i];
				yData[i] = diff;
			}

			unwrapPhase(yData);

			// Visualization only, unwrap in time
//			double previousStart = chart.getSeriesMap().get(String.format("RX1:%d, RX2:%d", rxAntenna1, rxAntenna2)).getYData()[0];
//			if(previousStart - yData[0] > Math.PI) {
//				CSIUtil.shift(yData, +2.0 * Math.PI);
//			}
//			if(previousStart - yData[0] < -Math.PI) {
//				CSIUtil.shift(yData, -2.0 * Math.PI);
//			}
			shift(yData, -mean(yData));



			chart.updateXYSeries(String.format("RX1:%d, RX2:%d", rxAntenna1, rxAntenna2), xData, yData, null);
		}
	}

	/**
	 * Displays the evolution of the phase difference between two antennas on one subcarrier over time
	 */
	public static class PhaseDiffEvolutionPreview extends PreviewMode {
		{ width = 700; height = 500; }
		private final int dataWidth = 150;

		private final int rxAntenna1;
		private final int rxAntenna2;
		private final int[] subcarriers;

		private final List<Double>[] previousDataPoints;
		private final List<Double>[] previousUnprocessed;

		private final int shortTermLength;
		private final double jumpThreshold;
		private final int truncatedMeanLength;
		private final double truncatedMeanPct;

		private final List<Double>[] shortTermHistory;
		private double[] currentOffset;

		/**
		 * @param rxAntenna1 the first antenna to compare
		 * @param rxAntenna2 the second antenna to compare
		 * @param truncatedMeanLength
		 * @param truncatedMeanPct
		 * @param subcarriers the subcarriers to display
		 */
		public PhaseDiffEvolutionPreview(int rxAntenna1, int rxAntenna2, int shortTermHistoryLength, double jumpThreshold, int truncatedMeanLength, double truncatedMeanPct, int... subcarriers) {
			this.rxAntenna1 = rxAntenna1;
			this.rxAntenna2 = rxAntenna2;
			this.shortTermLength = shortTermHistoryLength;
			this.jumpThreshold = jumpThreshold;
			this.truncatedMeanLength = truncatedMeanLength;
			this.truncatedMeanPct = truncatedMeanPct;
			this.subcarriers = subcarriers;
			previousDataPoints = new List[subcarriers.length];
			previousUnprocessed = new List[subcarriers.length];
			shortTermHistory = new List[subcarriers.length];
			currentOffset = new double[subcarriers.length];
			Arrays.fill(currentOffset, 0.0);
			for (int i = 0;i < subcarriers.length;i++) {
				previousDataPoints[i] = new LinkedList<>();
				previousUnprocessed[i] = new LinkedList<>();
				for(int j = 0;j < dataWidth;j++) {
					previousDataPoints[i].add(0.0);
					previousUnprocessed[i].add(0.0);
				}

				if(shortTermLength != -1) {
					shortTermHistory[i] = new LinkedList<>();
					DoubleStream.generate(() -> 0.0).limit(shortTermLength).forEach(shortTermHistory[i]::add);
				}
			}

		}

		@Override
		public XYChart createChart() {
			XYChart chart = new XYChartBuilder()
					.width(width)
					.height(height)
					.title("PhaseDiff Evolution")
					.xAxisTitle("Time")
					.yAxisTitle("Phase difference")
					.theme(CHART_THEME)
					.build();

			chart.getStyler().setLegendPosition(LegendPosition.InsideNE);
			chart.getStyler().setDefaultSeriesRenderStyle(XYSeriesRenderStyle.Line);
			chart.getStyler().setYAxisMin(-Math.PI);
			chart.getStyler().setYAxisMax(Math.PI);
			chart.getStyler().setXAxisMin((double)dataWidth);
			chart.getStyler().setXAxisMax(0.0);

			for (int subcarrierIndex = 0;subcarrierIndex < subcarriers.length;subcarrierIndex++) {
				chart.addSeries(String.format("RX%d-RX%d, sub: %d", rxAntenna1, rxAntenna2, subcarriers[subcarrierIndex]), new double[dataWidth]);
			}
			return chart;
		}

		@Override
		public void updateChart(DataInfo dataInfo, XYChart chart) {
			if(! (dataInfo instanceof CSIInfo)) {
				return;
			}
			CSIInfo csi = (CSIInfo)dataInfo;


			double[] xData = IntStream.range(0, dataWidth).mapToDouble(i -> i).toArray();
			double[] diffs = RespiratoryPhaseProcessor.process(rxAntenna1, rxAntenna2, 0, Collections.singletonList(csi), -1, -1.0)[0];

			for (int subcarrierIndex = 0;subcarrierIndex < this.subcarriers.length;subcarrierIndex++) {
				// Short term mean removal / moving average
				if(shortTermLength != -1) {
					double historyOffset = shortTermHistory[subcarrierIndex].stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
					shortTermHistory[subcarrierIndex].add(diffs[this.subcarriers[subcarrierIndex]]);
					shortTermHistory[subcarrierIndex].remove(0);

					diffs[this.subcarriers[subcarrierIndex]] -= historyOffset;
				}

				// Store for truncated mean
				List<Double> previousUnprocessedList = this.previousUnprocessed[subcarrierIndex];
				if(previousUnprocessedList.size() == dataWidth) {
					previousUnprocessedList.remove(previousUnprocessedList.size() - 1);
				}
				previousUnprocessedList.add(0, diffs[this.subcarriers[subcarrierIndex]]);

				// Truncated mean
				if(truncatedMeanLength >= 2) {
					int truncatedCount = (int) Math.ceil(truncatedMeanLength * truncatedMeanPct);
					double alphaTrimmedMean =
							Arrays.stream(
									unwrapPhase(Arrays.copyOfRange(previousUnprocessedList.stream().mapToDouble(Double::doubleValue).toArray(), 0, truncatedMeanLength))
							)
							.sorted()
							.skip((int) Math.ceil(truncatedCount / 2.0))
							.limit(truncatedMeanLength - truncatedCount)
							.average()
							.orElse(0.0);
					alphaTrimmedMean = bound(alphaTrimmedMean);

					diffs[this.subcarriers[subcarrierIndex]] = alphaTrimmedMean;
				}


				// Jump removal AFTER truncated mean
				diffs[this.subcarriers[subcarrierIndex]] -= currentOffset[subcarrierIndex];
				if(Math.abs(diffs[this.subcarriers[subcarrierIndex]] - this.previousDataPoints[subcarrierIndex].get(0)) > jumpThreshold) {
					currentOffset[subcarrierIndex] -= diffs[this.subcarriers[subcarrierIndex]] - this.previousDataPoints[subcarrierIndex].get(0);
					diffs[this.subcarriers[subcarrierIndex]] = this.previousDataPoints[subcarrierIndex].get(0);
					currentOffset[subcarrierIndex] = bound(currentOffset[subcarrierIndex]);
				}

				List<Double> previousList = this.previousDataPoints[subcarrierIndex];
				if(previousList.size() == dataWidth) {
					previousList.remove(previousList.size() - 1);
				}
				previousList.add(0, diffs[this.subcarriers[subcarrierIndex]]);

				chart.updateXYSeries(String.format("RX%d-RX%d, sub: %d", rxAntenna1, rxAntenna2, this.subcarriers[subcarrierIndex]), xData, previousList.stream().mapToDouble(d -> d).toArray(), null);
			}
		}
	}

	/**
	 * Previews a simple 2-dimensional plot of the CSI as complex values
	 */
	public static class CSIPlotPreview extends PreviewMode {
		{ width = 500; height = 500; }
		
		private final int txAntennas; // number of tx antennas to display
		private final int rxAntennas; // number of rx antennas to display

		/**
		 * @param rxAntennas the number of rx antennas to display
		 * @param txAntennas the number of tx antennas to display
		 */
		public CSIPlotPreview(int rxAntennas, int txAntennas) {
			this.rxAntennas = rxAntennas;
			this.txAntennas = txAntennas;
		}
		
		@Override
		public XYChart createChart() {
			XYChart chart = new XYChartBuilder()
					.width(width)
					.height(height)
					.title("CSI Plot")
					.xAxisTitle("Real")
					.yAxisTitle("Imag")
					.theme(CHART_THEME)
					.build();
			
			chart.getStyler().setLegendPosition(LegendPosition.InsideNE);
			chart.getStyler().setDefaultSeriesRenderStyle(XYSeriesRenderStyle.Line);
			chart.getStyler().setYAxisMin(-512.0);
			chart.getStyler().setYAxisMax(512.0);
			chart.getStyler().setXAxisMin(-512.0);
			chart.getStyler().setXAxisMax(512.0);
			
			for(int rx = 0;rx < rxAntennas;rx++) {
				for(int tx = 0;tx < txAntennas;tx++) {
					chart.addSeries(String.format("RX:%d, TX:%d", rx, tx), new double[114]);
				}
			}
			return chart;
		}

		@Override
		public void updateChart(DataInfo dataInfo, XYChart chart) {
			if(! (dataInfo instanceof CSIInfo)) {
				return;
			}
			CSIInfo csi = (CSIInfo)dataInfo;

			int subcarriers = csi.getCsi_status().getNum_tones();
			
			for(int rx = 0;rx < rxAntennas;rx++) {
				for(int tx = 0;tx < txAntennas;tx++) {
					double[] xData = new double[subcarriers];
					double[] yData = new double[subcarriers];
					for(int i = 0;i < subcarriers;i++) {
						xData[i] = csi.getCsi_matrix()[rx][tx][i].getReal();
						yData[i] = csi.getCsi_matrix()[rx][tx][i].getImag();
					}
					chart.updateXYSeries(String.format("RX:%d, TX:%d", rx, tx), xData, yData, null);
				}
			}
		}
	}
}
