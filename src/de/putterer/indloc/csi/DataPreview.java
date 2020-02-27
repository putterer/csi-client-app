package de.putterer.indloc.csi;

import de.putterer.indloc.csi.calibration.AccelerationInfo;
import de.putterer.indloc.data.DataInfo;
import de.putterer.indloc.util.CSIUtil;
import lombok.Getter;
import org.knowm.xchart.SwingWrapper;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.XYSeries.XYSeriesRenderStyle;
import org.knowm.xchart.style.Styler;
import org.knowm.xchart.style.Styler.ChartTheme;
import org.knowm.xchart.style.Styler.LegendPosition;

import javax.swing.*;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;

import static de.putterer.indloc.util.Util.euclidean;
import static de.putterer.indloc.util.Util.manhattan;

/**
 * A preview of incoming / replayed CSI data
 * Requires XChart: https://github.com/knowm/XChart
 */
public class DataPreview<T extends DataInfo> {
	
	public static final Styler.ChartTheme CHART_THEME = ChartTheme.XChart;

	// The data currently being displayed
	private DataInfo dataInfo;

	// The preview mode used for displaying
	@Getter
	private PreviewMode mode;
	
	private final XYChart chart;
	private final SwingWrapper<XYChart> wrapper;
	
	public DataPreview(PreviewMode mode) {
		this.mode = mode;
		
		chart = mode.createChart();		
		
		wrapper = new SwingWrapper<XYChart>(chart);
		wrapper.displayChart();
	}
	
	public void setData(T info) {
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

	public static class AccelerationEvolutionPreview extends PreviewMode {
		{ width = 700; height = 500; }
		private final int dataWidth = 150;

		private final List<Double>[] previousDataPoints;

		private final float limit;
		private final AccelerationType[] accelerationTypes;

		public AccelerationEvolutionPreview(float limit, AccelerationType... accelerationTypes) {
			this.limit = limit;
			this.accelerationTypes = accelerationTypes;
			previousDataPoints = new List[accelerationTypes.length];
			for (int i = 0;i < accelerationTypes.length;i++) {
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
					.title("Acceleration Preview")
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

			for(AccelerationType accelerationType : accelerationTypes) {
				chart.addSeries(accelerationType.name(), new double[dataWidth]);
			}
			return chart;
		}

		@Override
		public void updateChart(DataInfo dataInfo, XYChart chart) {
			if(! (dataInfo instanceof AccelerationInfo)) {
				return;
			}
			AccelerationInfo info = (AccelerationInfo) dataInfo;


			double[] xData = IntStream.range(0, dataWidth).mapToDouble(i -> i).toArray();

			for (int accelerationType = 0; accelerationType < accelerationTypes.length; accelerationType++) {
				List<Double> previousList = this.previousDataPoints[accelerationType];
				if(previousList.size() == dataWidth) {
					previousList.remove(previousList.size() - 1);
				}
				previousList.add(0, (double)accelerationTypes[accelerationType].valueDerivationFunction.apply(info));
				chart.updateXYSeries(accelerationTypes[accelerationType].name(), xData, previousList.stream().mapToDouble(d -> d).toArray(), null);


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

		public enum AccelerationType {
			X( i -> i.accel()[0] - i.getCalibration()[0]),
			Y( i -> i.accel()[1] - i.getCalibration()[1]),
			Z( i -> i.accel()[2] - i.getCalibration()[2]),
			EUCLIDEAN( i -> euclidean(i.accel()) - euclidean(i.getCalibration())),
			MANHATTAN( i -> manhattan(i.accel()) - manhattan(i.getCalibration())),
			MAX( i -> Math.max(i.accel()[0], Math.max(i.accel()[1], i.accel()[2])) );

			AccelerationType(Function<AccelerationInfo, Float> valueDerivationFunction) { this.valueDerivationFunction = valueDerivationFunction; }
			@Getter private final Function<AccelerationInfo, Float> valueDerivationFunction;
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

		/**
		 * @param type amplitude or phase
		 * @param rxAntennas the number of rx antennas to display
		 * @param txAntennas the number of tx antennas to display
		 */
		public SubcarrierPropertyPreview(PropertyType type, int rxAntennas, int txAntennas) {
			this.type = type;
			this.rxAntennas = rxAntennas;
			this.txAntennas = txAntennas;
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

					// Unwrap phase
					if(type == PropertyType.PHASE) {
						for(int i = 1;i < subcarriers;i++) {
							while(yData[i] - yData[i - 1] > Math.PI) {
								yData[i] -= 2 * Math.PI;
							}
							while(yData[i] - yData[i - 1] < (-1) * Math.PI) {
								yData[i] += 2 * Math.PI;
							}
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
	 * Displays the phase difference between 2 antennas
	 * data is unwrapped and shifted by mean before displaying
	 */
	public static class PhaseDiffPreview extends PreviewMode {
		{ width = 700; height = 500; }
		
		private final int rxAntenna1; // number of tx antennas to display
		private final int rxAntenna2; // number of tx antennas to display
		
		public PhaseDiffPreview(int rxAntenna1, int rxAntenna2) {
			this.rxAntenna1 = rxAntenna1;
			this.rxAntenna2 = rxAntenna2;
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
			chart.getStyler().setYAxisMin(-2.0 * Math.PI);
			chart.getStyler().setYAxisMax(2.0 * Math.PI);
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

			for(int i = 0;i < subcarriers;i++) {
				xData[i] = i;
				double diff = csi.getCsi_matrix()[rxAntenna1][0][i].getPhase() - csi.getCsi_matrix()[rxAntenna2][0][i].getPhase();
//				diff = Math.abs(diff);
//				if(diff >= Math.PI) {
//					diff -= Math.PI;
//				}
				yData[i] = diff;
			}

			CSIUtil.unwrapPhase(yData);

			// Visualization only, unwrap in time
//			double previousStart = chart.getSeriesMap().get(String.format("RX1:%d, RX2:%d", rxAntenna1, rxAntenna2)).getYData()[0];
//			if(previousStart - yData[0] > Math.PI) {
//				CSIUtil.shift(yData, +2.0 * Math.PI);
//			}
//			if(previousStart - yData[0] < -Math.PI) {
//				CSIUtil.shift(yData, -2.0 * Math.PI);
//			}
			CSIUtil.shift(yData, -CSIUtil.mean(yData));



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

		/**
		 * @param rxAntenna1 the first antenna to compare
		 * @param rxAntenna2 the second antenna to compare
		 * @param subcarriers the subcarriers to display
		 */
		public PhaseDiffEvolutionPreview(int rxAntenna1, int rxAntenna2, int[] subcarriers) {
			this.rxAntenna1 = rxAntenna1;
			this.rxAntenna2 = rxAntenna2;
			this.subcarriers = subcarriers;
			previousDataPoints = new List[subcarriers.length];
			for (int i = 0;i < subcarriers.length;i++) {
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

			int subcarriers = csi.getCsi_status().getNum_tones();

			double[] xData = IntStream.range(0, dataWidth).mapToDouble(i -> i).toArray();
			double[] diffs = new double[subcarriers];

			for(int i = 0;i < subcarriers;i++) {
				double diff = csi.getCsi_matrix()[rxAntenna1][0][i].getPhase() - csi.getCsi_matrix()[rxAntenna2][0][i].getPhase();
//				diff = Math.abs(diff);
//				if(diff >= Math.PI) {
//					diff -= Math.PI;
//				}
				diffs[i] = diff;
			}

			CSIUtil.unwrapPhase(diffs);
			CSIUtil.shift(diffs, -CSIUtil.mean(diffs));

			for (int subcarrierIndex = 0;subcarrierIndex < this.subcarriers.length;subcarrierIndex++) {
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
