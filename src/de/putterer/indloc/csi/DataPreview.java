package de.putterer.indloc.csi;

import de.putterer.indloc.Station;
import de.putterer.indloc.csi.calibration.AndroidInfo;
import de.putterer.indloc.csi.esp.EspCSIInfo;
import de.putterer.indloc.csi.intel.IntCSIInfo;
import de.putterer.indloc.csi.processing.RespiratoryPhaseProcessor;
import de.putterer.indloc.csi.processing.cm.ConjugateMultiplicationProcessor;
import de.putterer.indloc.csi.processing.cm.ShapeRepresentationProcessor;
import de.putterer.indloc.data.DataInfo;
import de.putterer.indloc.data.ecg.EcgInfo;
import de.putterer.indloc.util.CSIUtil;
import de.putterer.indloc.util.Logger;
import de.putterer.indloc.util.PlatformUtil;
import de.putterer.indloc.util.Vector;
import lombok.Data;
import lombok.Getter;
import org.apache.commons.math3.util.Pair;
import org.knowm.xchart.*;
import org.knowm.xchart.XYSeries.XYSeriesRenderStyle;
import org.knowm.xchart.internal.chartpart.Chart;
import org.knowm.xchart.style.Styler;
import org.knowm.xchart.style.Styler.ChartTheme;
import org.knowm.xchart.style.Styler.LegendPosition;

import javax.swing.*;
import java.awt.*;
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
public class DataPreview<T extends Chart> {
	
	public static final Styler.ChartTheme CHART_THEME = ChartTheme.XChart;

	// The data currently being displayed
	private DataInfo dataInfo;

	// The preview mode used for displaying
	@Getter
	private PreviewMode<T> mode;
	
	private final T chart;
	private final SwingWrapper<T> wrapper;
	@Getter
	private final JFrame frame;
	
	public DataPreview(String stationName, PreviewMode<T> mode) {
		this.mode = mode;
		
		chart = mode.createChart(stationName);
		
		wrapper = new SwingWrapper<>(chart);
		this.frame = wrapper.displayChart();

		this.frame.setTitle(this.frame.getTitle() + (PlatformUtil.isRunningI3() ? " i3float" : ""));
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

	public void destroy() {
		frame.dispose();
	}

	@Getter
	public static abstract class PreviewMode<T extends Chart> {
		protected int width;
		protected int height;
		
		public abstract T createChart(String stationName);
		public abstract void updateChart(DataInfo dataInfo, T chart);
	}

	public static class PhaseDiffVariancePreview extends PreviewMode<XYChart> {
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
		public XYChart createChart(String stationName) {
			XYChart chart = new XYChartBuilder()
					.width(width)
					.height(height)
					.title("PhaseDiffVariance - " + stationName)
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

			int subcarriers = csi.getNumTones();
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

	public static class AndroidEvolutionPreview extends PreviewMode<XYChart> {
		{ width = 700; height = 500; }
		private final int dataWidth = 150;

		private final List<Double>[] previousDataPoints;

		private final double limit;
		private final AndroidDataType[] androidDataTypes;

		public AndroidEvolutionPreview(double limit, AndroidDataType... androidDataTypes) {
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
		public XYChart createChart(String stationName) {
			XYChart chart = new XYChartBuilder()
					.width(width)
					.height(height)
					.title("Android position/acceleration Preview - " + stationName)
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

	public static class SerialEvolutionPreview extends PreviewMode<XYChart> {
		{ width = 700; height = 500; }
		private final int dataWidth = 500;

		private final List<Double> previousDataPoints;

		public SerialEvolutionPreview() {
			previousDataPoints = new LinkedList<>();
			for(int j = 0;j < dataWidth;j++) {
				previousDataPoints.add(0.0);
			}
		}

		@Override
		public XYChart createChart(String stationName) {
			XYChart chart = new XYChartBuilder()
					.width(width)
					.height(height)
					.title("Serial Preview - " + stationName)
					.xAxisTitle("t")
					.yAxisTitle("value")
					.theme(CHART_THEME)
					.build();

			chart.getStyler().setLegendPosition(LegendPosition.InsideNE);
			chart.getStyler().setDefaultSeriesRenderStyle(XYSeriesRenderStyle.Line);
			chart.getStyler().setYAxisMin(0.0);
			chart.getStyler().setYAxisMax(1.0);
			chart.getStyler().setXAxisMin((double) dataWidth);
			chart.getStyler().setXAxisMax(0.0);

			chart.getStyler().setMarkerSize(0);

			chart.addSeries("serial", new double[dataWidth]);
			return chart;
		}

		@Override
		public void updateChart(DataInfo dataInfo, XYChart chart) {
			if(! (dataInfo instanceof EcgInfo)) {
				return;
			}
			EcgInfo info = (EcgInfo) dataInfo;

			double[] xData = IntStream.range(0, dataWidth).mapToDouble(i -> i).toArray();

			if (previousDataPoints.size() == dataWidth) {
				previousDataPoints.remove(previousDataPoints.size() - 1);
			}
			previousDataPoints.add(0, (double) info.getValue());
			chart.updateXYSeries("serial", xData, previousDataPoints.stream().mapToDouble(d -> d).toArray(), null);
		}
	}

	/**
	 * Previews one property, amplitude or phase, across all subcarriers
	 */
	public static class SubcarrierPropertyPreview extends PreviewMode<XYChart> {
		{ width = 700; height = 500; }
		
		public enum PropertyType { AMPLITUDE, PHASE }
		private final PropertyType type;
		private final int rxAntennas; // number of rx antennas to display
		private final int txAntennas; // number of tx antennas to display
		private final int smoothingPacketCount;

		private final List<Double> previousData[][][];
		private final double previousMeanPhase[][];

		/**
		 * @param type amplitude or phase
		 * @param rxAntennas the number of rx antennas to display
		 * @param txAntennas the number of tx antennas to display
		 */
		public SubcarrierPropertyPreview(PropertyType type, int rxAntennas, int txAntennas, int smoothingPacketCount) {
			this.type = type;
			this.rxAntennas = rxAntennas;
			this.txAntennas = txAntennas;
			this.smoothingPacketCount = smoothingPacketCount;

			previousData = new List[rxAntennas][txAntennas][114];
			for(int rx = 0;rx < rxAntennas;rx++) {
				for(int tx = 0;tx < txAntennas;tx++) {
					for(int c = 0;c < 114;c++) {
						previousData[rx][tx][c] = new LinkedList<>();
					}
				}
			}
			previousMeanPhase = new double[rxAntennas][txAntennas];
		}
		
		@Override
		public XYChart createChart(String stationName) {
			XYChart chart = new XYChartBuilder()
					.width(width)
					.height(height)
					.title("CSI Preview - " + stationName)
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

			if(csi instanceof IntCSIInfo) {
				chart.getStyler().setXAxisMax(30.0);
			} else if(csi instanceof EspCSIInfo) {
				chart.getStyler().setXAxisMax(64.0);
			} else {
				chart.getStyler().setXAxisMax(114.0);
			}

			int subcarriers = csi.getNumTones();
			
			for(int rx = 0;rx < rxAntennas;rx++) {
				for(int tx = 0;tx < txAntennas;tx++) {
					double[] xData = new double[subcarriers];
					double[] yData = new double[subcarriers];
					for(int i = 0;i < subcarriers;i++) {
						xData[i] = i;

						if(csi.getCsi_matrix()[rx][tx][i] == null) {
							yData[i] = 0;
							continue;
						}
						switch(type) {
						case AMPLITUDE: yData[i] = csi.getCsi_matrix()[rx][tx][i].getAmplitude()/* - csi.getCsi_matrix()[rx][tx][0].getAmplitude()*/;break;
						case PHASE: yData[i] = csi.getCsi_matrix()[rx][tx][i].getPhase() - csi.getCsi_matrix()[0][0][0].getPhase();break;
						}
					}

					if(type == PropertyType.PHASE) {
						unwrapPhase(yData);
						previousMeanPhase[rx][tx] = timeUnwrapped(yData, previousMeanPhase[rx][tx]);
					}

					if(smoothingPacketCount >= 2) {
						for (int i = 0; i < subcarriers; i++) {
							List<Double> l = previousData[rx][tx][i];
							l.add(yData[i]);
							while (l.size() > smoothingPacketCount) {
								l.remove(0);
							}

							yData[i] = l.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
						}
					}

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
	 * Displays the amplitude difference between 2 antennas
	 * data is unwrapped and shifted by mean before displaying
	 */
	public static class AmplitudeDiffPreview extends PreviewMode<XYChart> {
		{ width = 700; height = 500; }

		private final int rxAntenna1; // rx antennas to display
		private final int rxAntenna2;

		public AmplitudeDiffPreview(int rxAntenna1, int rxAntenna2) {
			this.rxAntenna1 = rxAntenna1;
			this.rxAntenna2 = rxAntenna2;
		}

		@Override
		public XYChart createChart(String stationName) {
			XYChart chart = new XYChartBuilder()
					.width(width)
					.height(height)
					.title("CSI Amplitude difference preview - " + stationName)
					.xAxisTitle("Subcarrier")
					.yAxisTitle("Amplitude difference")
					.theme(CHART_THEME)
					.build();

			chart.getStyler().setLegendPosition(LegendPosition.InsideNE);
			chart.getStyler().setDefaultSeriesRenderStyle(XYSeriesRenderStyle.Line);
			chart.getStyler().setYAxisMin(-500.0);
			chart.getStyler().setYAxisMax(500.0);
			chart.getStyler().setXAxisMin(0.0);
			chart.getStyler().setXAxisMax(56.0);

			chart.addSeries(String.format("RX1:%d, RX2:%d", rxAntenna1, rxAntenna2), new double[114]);
			return chart;
		}

		@Override
		public void updateChart(DataInfo dataInfo, XYChart chart) {
			if(! (dataInfo instanceof CSIInfo)) {
				return;
			}
			CSIInfo csi = (CSIInfo)dataInfo;

			int subcarriers = csi.getNumTones();
			if(subcarriers > 56) {
				chart.getStyler().setXAxisMax((double) subcarriers);
			}
			if(dataInfo instanceof IntCSIInfo) {
				chart.getStyler().setXAxisMax(30.0);
			}

			double[] xData = new double[subcarriers];
			double[] yData = new double[subcarriers];

			for(int i = 0;i < subcarriers;i++) {
				xData[i] = i;
				double diff = csi.getCsi_matrix()[rxAntenna1][0][i].getAmplitude() - csi.getCsi_matrix()[rxAntenna2][0][i].getAmplitude();
				yData[i] = diff;
			}

			chart.updateXYSeries(String.format("RX1:%d, RX2:%d", rxAntenna1, rxAntenna2), xData, yData, null);
		}
	}

	/**
	 * Displays the evolution of the phase difference between two antennas on one subcarrier over time
	 */
	public static class AmplitudeEvolutionPreview extends PreviewMode<XYChart> {
		{ width = 700; height = 500; }
		private final int dataWidth = 250;

		private double yAxisHeight = 500.0;

		private final int rxAntenna;
		private final int txAntenna;
		private final int smoothingPacketCount;
		private final int[] subcarriers;

		private final List<Double>[] previousDataPoints;
		private final List<Double>[] previousUnprocessedDataPoints;

		/**
		 * @param rxAntenna the rx antenna to use
		 * @param txAntenna the tx antenna to use
		 * @param smoothingPacketCount
		 * @param subcarriers the subcarriers to display
		 */
		public AmplitudeEvolutionPreview(int rxAntenna, int txAntenna, int smoothingPacketCount, int... subcarriers) {
			this.rxAntenna = rxAntenna;
			this.txAntenna = txAntenna;
			this.smoothingPacketCount = smoothingPacketCount;
			this.subcarriers = subcarriers;
			previousDataPoints = new List[subcarriers.length];
			previousUnprocessedDataPoints = new List[subcarriers.length];
			for (int i = 0;i < subcarriers.length;i++) {
				previousDataPoints[i] = new LinkedList<>();
				previousUnprocessedDataPoints[i] = new LinkedList<>();
				for(int j = 0;j < dataWidth;j++) {
					previousDataPoints[i].add(0.0);
					previousUnprocessedDataPoints[i].add(0.0);
				}
			}

		}

		@Override
		public XYChart createChart(String stationName) {
			XYChart chart = new XYChartBuilder()
					.width(width)
					.height(height)
					.title("Amplitude Evolution - " + stationName)
					.xAxisTitle("Time")
					.yAxisTitle("Amplitude difference")
					.theme(CHART_THEME)
					.build();

			chart.getStyler().setLegendPosition(LegendPosition.InsideNE);
			chart.getStyler().setDefaultSeriesRenderStyle(XYSeriesRenderStyle.Line);
			chart.getStyler().setYAxisMin(0.0);
			chart.getStyler().setYAxisMax(500.0);
			chart.getStyler().setXAxisMin((double)dataWidth);
			chart.getStyler().setXAxisMax(0.0);

			for (int subcarrierIndex = 0;subcarrierIndex < subcarriers.length;subcarrierIndex++) {
				chart.addSeries(String.format("RX%d-TX%d, sub: %d", rxAntenna, txAntenna, subcarriers[subcarrierIndex]), new double[dataWidth]);
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

			for (int subcarrierIndex = 0;subcarrierIndex < this.subcarriers.length;subcarrierIndex++) {

				double currentData = csi.getCsi_matrix()[rxAntenna][txAntenna][this.subcarriers[subcarrierIndex]].getAmplitude();

				List<Double> previousList = this.previousDataPoints[subcarrierIndex];
				List<Double> previousUnprocessedList = this.previousUnprocessedDataPoints[subcarrierIndex];
				if(previousList.size() == dataWidth) {
					previousList.remove(previousList.size() - 1);
				}
				if(previousUnprocessedList.size() == dataWidth) {
					previousUnprocessedList.remove(previousUnprocessedList.size() - 1);
				}
				previousUnprocessedList.add(0, currentData);

				previousList.add(0, previousUnprocessedList.stream().mapToDouble(d -> d).limit(smoothingPacketCount).average().orElse(0));

				if(currentData > yAxisHeight - 100.0) {
					yAxisHeight = currentData + 100.0;
					chart.getStyler().setYAxisMax(yAxisHeight);
				}
				chart.updateXYSeries(String.format("RX%d-TX%d, sub: %d", rxAntenna, txAntenna, this.subcarriers[subcarrierIndex]), xData, previousList.stream().mapToDouble(d -> d).toArray(), null);
			}
		}
	}

	public static class AmplitudeDiffEvolutionPreview extends PreviewMode<XYChart> {
		{ width = 700; height = 500; }
		private final int dataWidth = 250;

		private double yAxisHeight = 500.0;

		private final Pair<AntennaSubcarrier, AntennaSubcarrier>[] antennaSubcarriersPairs;
		private final int smoothingPacketCount;

		private final List<Double>[] previousDataPoints;
		private final List<Double>[] previousUnprocessedDataPoints;

		@Data
		public static final class AntennaSubcarrier {
			private final int txAntenna, rxAntenna, subcarrier;
		}

		public AmplitudeDiffEvolutionPreview(Pair<AntennaSubcarrier, AntennaSubcarrier>[] antennaSubcarriersPairs, int smoothingPacketCount) {
			this.antennaSubcarriersPairs = antennaSubcarriersPairs;
			this.smoothingPacketCount = smoothingPacketCount;

			previousDataPoints = new List[antennaSubcarriersPairs.length];
			previousUnprocessedDataPoints = new List[antennaSubcarriersPairs.length];
			for (int i = 0;i < antennaSubcarriersPairs.length;i++) {
				previousDataPoints[i] = new LinkedList<>();
				previousUnprocessedDataPoints[i] = new LinkedList<>();
				for(int j = 0;j < dataWidth;j++) {
					previousDataPoints[i].add(0.0);
					previousUnprocessedDataPoints[i].add(0.0);
				}
			}

		}



		private String getSeriesName(Pair<AntennaSubcarrier, AntennaSubcarrier> it) {
			return String.format("RX%d-TX%d-sub%d <-> RX%d-TX%d-sub%d",
					it.getFirst().rxAntenna,
					it.getFirst().txAntenna,
					it.getFirst().subcarrier,
					it.getSecond().rxAntenna,
					it.getSecond().txAntenna,
					it.getSecond().subcarrier
			);
		}

		@Override
		public XYChart createChart(String stationName) {
			XYChart chart = new XYChartBuilder()
					.width(width)
					.height(height)
					.title("Amplitude Diff Evolution - " + stationName)
					.xAxisTitle("Time")
					.yAxisTitle("Amplitude difference")
					.theme(CHART_THEME)
					.build();

			chart.getStyler().setLegendPosition(LegendPosition.InsideNE);
			chart.getStyler().setDefaultSeriesRenderStyle(XYSeriesRenderStyle.Line);
			chart.getStyler().setYAxisMin(-100.0);
			chart.getStyler().setYAxisMax(+100.0);
			chart.getStyler().setXAxisMin((double)dataWidth);
			chart.getStyler().setXAxisMax(0.0);

			Arrays.stream(antennaSubcarriersPairs).forEach(it ->
					chart.addSeries(getSeriesName(it), new double[dataWidth])
			);
			return chart;
		}

		@Override
		public void updateChart(DataInfo dataInfo, XYChart chart) {
			if(! (dataInfo instanceof CSIInfo)) {
				return;
			}
			CSIInfo csi = (CSIInfo)dataInfo;


			double[] xData = IntStream.range(0, dataWidth).mapToDouble(i -> i).toArray();

			for (int index = 0;index < this.antennaSubcarriersPairs.length;index++) {

				Pair<AntennaSubcarrier, AntennaSubcarrier> antennaSubcarrierPair = antennaSubcarriersPairs[index];


				double firstAmplitude = csi.getCsi_matrix()
						[antennaSubcarrierPair.getFirst().rxAntenna]
						[antennaSubcarrierPair.getFirst().txAntenna]
						[antennaSubcarrierPair.getFirst().subcarrier]
						.getAmplitude();
				double secondAmplitude = csi.getCsi_matrix()
						[antennaSubcarrierPair.getSecond().rxAntenna]
						[antennaSubcarrierPair.getSecond().txAntenna]
						[antennaSubcarrierPair.getSecond().subcarrier]
						.getAmplitude();

				double currentData = secondAmplitude - firstAmplitude;

				List<Double> previousList = this.previousDataPoints[index];
				List<Double> previousUnprocessedList = this.previousUnprocessedDataPoints[index];
				if(previousList.size() == dataWidth) {
					previousList.remove(previousList.size() - 1);
				}
				if(previousUnprocessedList.size() == dataWidth) {
					previousUnprocessedList.remove(previousUnprocessedList.size() - 1);
				}
				previousUnprocessedList.add(0, currentData);

				previousList.add(0, previousUnprocessedList.stream().mapToDouble(d -> d).limit(smoothingPacketCount).average().orElse(0));

				if(currentData > yAxisHeight - 100.0) {
					yAxisHeight = currentData + 100.0;
					chart.getStyler().setYAxisMax(yAxisHeight);
				}
				chart.updateXYSeries(getSeriesName(antennaSubcarrierPair), xData, previousList.stream().mapToDouble(d -> d).toArray(), null);
			}
		}
	}

	/**
	 * Displays the phase difference between 2 antennas
	 * data is unwrapped and shifted by mean before displaying
	 */
	public static class PhaseDiffPreview extends PreviewMode<XYChart> {
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
		public XYChart createChart(String stationName) {
			XYChart chart = new XYChartBuilder()
					.width(width)
					.height(height)
					.title("CSI Preview - " + stationName)
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

			int subcarriers = csi.getNumTones();
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
	public static class PhaseDiffEvolutionPreview extends PreviewMode<XYChart> {
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
		public XYChart createChart(String stationName) {
			XYChart chart = new XYChartBuilder()
					.width(width)
					.height(height)
					.title("PhaseDiff Evolution - " + stationName)
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
	public static class CSIPlotPreview extends PreviewMode<XYChart> {
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
		public XYChart createChart(String stationName) {
			XYChart chart = new XYChartBuilder()
					.width(width)
					.height(height)
					.title("CSI Plot - " + stationName)
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

			int subcarriers = csi.getNumTones();
			
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

	public static class CSIDiffPlotPreview extends PreviewMode<XYChart> {

		private String seriesName;

		{ width = 500; height = 500; }

		private final int rxAntenna1;
		private final int txAntenna1;
		private final int rxAntenna2;
		private final int txAntenna2;
		private final boolean conjugateMultiplication;
		private final boolean normalizeAmplitude;

		public CSIDiffPlotPreview(int rxAntenna1, int txAntenna1, int rxAntenna2, int txAntenna2, boolean conjugateMultiplication, boolean normalizeAmplitude) {
			this.rxAntenna1 = rxAntenna1;
			this.txAntenna1 = txAntenna1;
			this.rxAntenna2 = rxAntenna2;
			this.txAntenna2 = txAntenna2;
			this.conjugateMultiplication = conjugateMultiplication;
			this.normalizeAmplitude = normalizeAmplitude;

			seriesName = String.format("RX:%d, TX:%d to RX:%d, TX:%d", rxAntenna1, txAntenna1, rxAntenna2, txAntenna2);
		}

		@Override
		public XYChart createChart(String stationName) {
			XYChart chart = new XYChartBuilder()
					.width(width)
					.height(height)
					.title(conjugateMultiplication ? "CSI CM Plot - " : "CSI Diff Plot - " + stationName)
					.xAxisTitle("Real")
					.yAxisTitle("Imag")
					.theme(CHART_THEME)
					.build();

			chart.getStyler().setLegendPosition(LegendPosition.InsideNE);
			chart.getStyler().setDefaultSeriesRenderStyle(XYSeriesRenderStyle.Line);
			double maxValue = conjugateMultiplication ? 8192.0 : 512.0;
			chart.getStyler().setYAxisMin(-maxValue);
			chart.getStyler().setYAxisMax(maxValue);
			chart.getStyler().setXAxisMin(-maxValue);
			chart.getStyler().setXAxisMax(maxValue);

			chart.addSeries(seriesName, new double[114]);
			if(normalizeAmplitude) {
				chart.addSeries(seriesName + " - mean", new double[1]);
			}
			return chart;
		}

		@Override
		public void updateChart(DataInfo dataInfo, XYChart chart) {
			if(! (dataInfo instanceof CSIInfo)) {
				return;
			}
			CSIInfo csi = (CSIInfo)dataInfo;

			int subcarriers = csi.getNumTones();

			CSIInfo.Complex[] complexData = new CSIInfo.Complex[subcarriers];


			for (int i = 0; i < subcarriers; i++) {
				CSIInfo.Complex v1 = csi.getCsi_matrix()[rxAntenna1][txAntenna1][i];
				CSIInfo.Complex v2 = csi.getCsi_matrix()[rxAntenna2][txAntenna2][i];
				CSIInfo.Complex diff;

				if(conjugateMultiplication) {
					diff = v1.prod(v2.conjugate());
				} else {
					diff = v2.sub(v1);
				}

				complexData[i] = diff;
			}

			// normalize standard deviation of complex data, not amplitude itself
			if(normalizeAmplitude) {
				CSIInfo.Complex mean = Arrays.stream(complexData).reduce(CSIInfo.Complex::add).orElse(new CSIInfo.Complex(0,0)).scale(1.0 / complexData.length);

				double amplitudeVariance = Arrays.stream(complexData)
						.map(it -> Math.pow(it.sub(mean).getAmplitude(), 2))
						.reduce(Double::sum)
						.orElse(0.0)
						/ complexData.length;

				double amplitudeDeviation = Math.sqrt(amplitudeVariance);
				double scaleFactor = 2000.0 / amplitudeDeviation; // scale around mean or origin? this scales around origin

				for(int i = 0;i < complexData.length;i++) {
					complexData[i] = complexData[i].scale(scaleFactor);
				}

				chart.updateXYSeries(seriesName + " - mean", new double[] {mean.getReal() * scaleFactor}, new double[] {mean.getImag() * scaleFactor}, null);
			}

			double[] xData = new double[subcarriers];
			double[] yData = new double[subcarriers];
			for (int i = 0; i < subcarriers; i++) {
				xData[i] = complexData[i].getReal();
				yData[i] = complexData[i].getImag();
			}

			chart.updateXYSeries(seriesName, xData, yData, null);
		}
	}

	public static class CSICmProcessedPlotPreview extends PreviewMode<XYChart> {

		private String seriesName;

		{ width = 800; height = 800; }

		private final int rx1;
		private final int tx1;
		private final int rx2;
		private final int tx2;

		private final ConjugateMultiplicationProcessor processor;

		public CSICmProcessedPlotPreview(int rx1, int tx1, int rx2, int tx2, int slidingWindowSize, int timestampCountForAverage, double stddevThresholdForSamePhaseDetection, double thresholdForOffsetCorrection) {
			this.rx1 = rx1;
			this.tx1 = tx1;
			this.rx2 = rx2;
			this.tx2 = tx2;
			this.processor = new ConjugateMultiplicationProcessor(rx1, tx1, rx2, tx2, slidingWindowSize, timestampCountForAverage, stddevThresholdForSamePhaseDetection, thresholdForOffsetCorrection); // TODO: parameters

			seriesName = String.format("RX:%d, TX:%d to RX:%d, TX:%d", rx1, tx1, rx2, tx2);
		}

		@Override
		public XYChart createChart(String stationName) {
			XYChart chart = new XYChartBuilder()
					.width(width)
					.height(height)
					.title("CSI Processed CM Plot - " + stationName)
					.xAxisTitle("Real")
					.yAxisTitle("Imag")
					.theme(CHART_THEME)
					.build();

			chart.getStyler().setLegendPosition(LegendPosition.InsideNE);
			chart.getStyler().setDefaultSeriesRenderStyle(XYSeriesRenderStyle.Line);
			double maxValue = 8192.0 * 1.5;
			chart.getStyler().setYAxisMin(-maxValue);
			chart.getStyler().setYAxisMax(maxValue);
			chart.getStyler().setXAxisMin(-maxValue);
			chart.getStyler().setXAxisMax(maxValue);

			chart.addSeries(seriesName, new double[114]);
			chart.addSeries(seriesName + " - mean", new double[1]);
			return chart;
		}

		@Override
		public void updateChart(DataInfo dataInfo, XYChart chart) {
			if(! (dataInfo instanceof CSIInfo)) {
				return;
			}
			CSIInfo csi = (CSIInfo)dataInfo;

			int subcarriers = csi.getNumTones();

			CSIInfo.Complex[] processedData = processor.process(dataInfo);

			double[] xData = new double[subcarriers];
			double[] yData = new double[subcarriers];
			for (int i = 0; i < subcarriers; i++) {
				xData[i] = processedData[i].getReal();
				yData[i] = processedData[i].getImag();
			}

			CSIInfo.Complex mean = CSIUtil.mean(processedData);

			chart.updateXYSeries(seriesName, xData, yData, null);
			chart.updateXYSeries(seriesName + " - mean", new double[]{mean.getReal()}, new double[]{mean.getImag()}, null);
		}
	}

	public abstract static class CSICMCurveShapePreview<T extends Chart> extends PreviewMode<T> {

		{ width = 800; height = 800; }

		private final int rx1;
		private final int tx1;
		private final int rx2;
		private final int tx2;

		protected final ConjugateMultiplicationProcessor cmprocessor;
		protected final ShapeRepresentationProcessor shapeProcessor;
		protected final boolean relative;

		public CSICMCurveShapePreview(int rx1, int tx1, int rx2, int tx2, int slidingWindowSize, int timestampCountForAverage, double stddevThresholdForSamePhaseDetection, double thresholdForOffsetCorrection, boolean relative) {
			this.rx1 = rx1;
			this.tx1 = tx1;
			this.rx2 = rx2;
			this.tx2 = tx2;
			this.cmprocessor = new ConjugateMultiplicationProcessor(rx1, tx1, rx2, tx2, slidingWindowSize, timestampCountForAverage, stddevThresholdForSamePhaseDetection, thresholdForOffsetCorrection); // TODO: parameters
			this.shapeProcessor = new ShapeRepresentationProcessor(relative);
			this.relative = relative;
		}

		@Override
		public abstract T createChart(String stationName);

		@Override
		public void updateChart(DataInfo dataInfo, T chart) {
			if(! (dataInfo instanceof CSIInfo)) {
				return;
			}
			CSIInfo csi = (CSIInfo)dataInfo;

			CSIInfo.Complex[] processedData = cmprocessor.process(dataInfo);
			Vector[] shape = shapeProcessor.process(processedData);

			setChartData(shape, csi.getNumTones(), chart);
		}

		public abstract void setChartData(Vector[] shape, int subcarriers, T chart);
	}

	public static class CSICMCurveShapePlotPreview extends CSICMCurveShapePreview<XYChart> {

		public CSICMCurveShapePlotPreview(int rx1, int tx1, int rx2, int tx2, int slidingWindowSize, int timestampCountForAverage, double stddevThresholdForSamePhaseDetection, double thresholdForOffsetCorrection, boolean relative) {
			super(rx1, tx1, rx2, tx2, slidingWindowSize, timestampCountForAverage, stddevThresholdForSamePhaseDetection, thresholdForOffsetCorrection, relative);
		}

		@Override
		public XYChart createChart(String stationName) {
			XYChart chart = new XYChartBuilder()
					.width(width)
					.height(height)
					.title("CSI CM Shape Plot - " + stationName)
					.xAxisTitle("Real")
					.yAxisTitle("Imag")
					.theme(CHART_THEME)
					.build();

			chart.getStyler().setDefaultSeriesRenderStyle(XYSeriesRenderStyle.Line);
			double maxValue = 1000.0;
			chart.getStyler().setYAxisMin(-maxValue);
			chart.getStyler().setYAxisMax(maxValue);
			chart.getStyler().setXAxisMin(-maxValue);
			chart.getStyler().setXAxisMax(maxValue);

			Color[] colorPalette = new Color[113];
			for(int i = 0;i < 113;i++) {
				colorPalette[i] = Color.getHSBColor(0.0f + (2.0f/3.0f * (i / 56.0f)), 1.0f, 1.0f);
			}
			chart.getStyler().setSeriesColors(colorPalette);

			for(int i = 0;i < 113;i++) {
				chart.addSeries(String.valueOf(i), new double[1]);
			}
			return chart;
		}

		public void setChartData(Vector[] shape, int subcarriers, XYChart chart) {
			CSIInfo.Complex[] shapeAmpPhaseEncoded = new CSIInfo.Complex[shape.length];
			for(int i = 0;i < shape.length;i++) {
				shapeAmpPhaseEncoded[i] = CSIInfo.Complex.fromAmplitudePhase(shape[i].getX(), shape[i].getY());
			}

			double[] xData = new double[subcarriers - 1];
			double[] yData = new double[subcarriers - 1];
			for (int i = 0; i < subcarriers - 1; i++) {
				xData[i] = shapeAmpPhaseEncoded[i].getReal();
				yData[i] = shapeAmpPhaseEncoded[i].getImag();

				chart.updateXYSeries(String.valueOf(i), new double[]{xData[i]}, new double[]{yData[i]}, null);
			}
		}
	}

	public static class CSICMCurveShapeAngleDistributionPreview extends CSICMCurveShapePreview<CategoryChart> {

		{ width = 800; height = 800; }

		private final boolean unwrapAngle;
		private final boolean fixRotationOffset; // fixes the offset introduced by random orientations by aligning the first subcarrier to 0

		public CSICMCurveShapeAngleDistributionPreview(int rx1, int tx1, int rx2, int tx2, int slidingWindowSize, int timestampCountForAverage, double stddevThresholdForSamePhaseDetection, double thresholdForOffsetCorrection, boolean relative, boolean unwrapAngle, boolean fixRotationOffset) {
			super(rx1, tx1, rx2, tx2, slidingWindowSize, timestampCountForAverage, stddevThresholdForSamePhaseDetection, thresholdForOffsetCorrection, relative);
			this.unwrapAngle = unwrapAngle;
			this.fixRotationOffset = fixRotationOffset;
		}

		@Override
		public CategoryChart createChart(String stationName) {
			CategoryChart chart = new CategoryChartBuilder()
					.width(width)
					.height(height)
					.title(String.format("CSI CM Shape Angle Distribution%s - ", (this.relative ? " relative" : "")) + stationName)
					.theme(CHART_THEME)
					.build();

			chart.getStyler().setChartTitleVisible(true);
//			chart.getStyler().setLegendPosition(LegendPosition.InsideNW);
			chart.getStyler().setDefaultSeriesRenderStyle(CategorySeries.CategorySeriesRenderStyle.Stick);

			double maxValue = 10.0;
			chart.getStyler().setYAxisMin(-maxValue);
			chart.getStyler().setYAxisMax(maxValue);
			chart.getStyler().setXAxisMin(-maxValue);
			chart.getStyler().setXAxisMax(maxValue);

			chart.addSeries("angle", new double[56], new double[56]);
			return chart;
		}

		@Override
		public void setChartData(Vector[] shape, int subcarriers, CategoryChart chart) {
			if(fixRotationOffset) {
				this.shapeProcessor.shiftAngleZeroFirstCarriers(shape, 3);
				this.shapeProcessor.shiftAngle(shape, (float) Math.PI);
				this.shapeProcessor.wrapAngle(shape);
			}

			if(unwrapAngle) {
				this.shapeProcessor.unwrapAngle(shape, this.relative); // if the angle is relative to predecessors, we want to wrap it relative to 0
			}

			double[] xData = new double[subcarriers - 1];
			double[] yData = new double[subcarriers - 1];
			for (int i = 0; i < subcarriers - 1; i++) {
				xData[i] = i;
				yData[i] = shape[i].getY();
			}

			chart.updateCategorySeries("angle", xData, yData, null);
		}
	}

	public static class CSICMCurveShapeDistDistributionPreview extends CSICMCurveShapePreview<CategoryChart> {

		{ width = 800; height = 800; }

		public CSICMCurveShapeDistDistributionPreview(int rx1, int tx1, int rx2, int tx2, int slidingWindowSize, int timestampCountForAverage, double stddevThresholdForSamePhaseDetection, double thresholdForOffsetCorrection) {
			super(rx1, tx1, rx2, tx2, slidingWindowSize, timestampCountForAverage, stddevThresholdForSamePhaseDetection, thresholdForOffsetCorrection, false);
		}

		@Override
		public CategoryChart createChart(String stationName) {
			CategoryChart chart = new CategoryChartBuilder()
					.width(width)
					.height(height)
					.title("CSI CM Shape Distance Distribution - " + stationName)
					.theme(CHART_THEME)
					.build();

			chart.getStyler().setChartTitleVisible(true);
//			chart.getStyler().setLegendPosition(LegendPosition.InsideNW);
			chart.getStyler().setDefaultSeriesRenderStyle(CategorySeries.CategorySeriesRenderStyle.Stick);

			double maxValue = 600.0;
			chart.getStyler().setYAxisMin(-maxValue);
			chart.getStyler().setYAxisMax(maxValue);
			chart.getStyler().setXAxisMin(-maxValue);
			chart.getStyler().setXAxisMax(maxValue);

			chart.addSeries("dist", new double[56], new double[56]);
			return chart;
		}

		@Override
		public void setChartData(Vector[] shape, int subcarriers, CategoryChart chart) {
			double[] xData = new double[subcarriers - 1];
			double[] yData = new double[subcarriers - 1];
			for (int i = 0; i < subcarriers - 1; i++) {
				xData[i] = i;
				yData[i] = shape[i].getX();
			}

			chart.updateCategorySeries("dist", xData, yData, null);
		}
	}
}
