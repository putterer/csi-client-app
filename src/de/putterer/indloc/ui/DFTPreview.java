package de.putterer.indloc.ui;

import de.putterer.indloc.respiratory.Periodicity;
import lombok.Getter;
import lombok.Setter;
import org.knowm.xchart.SwingWrapper;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.XYSeries;
import org.knowm.xchart.style.Styler;

import javax.swing.*;
import java.util.Comparator;
import java.util.Map;

public class DFTPreview {
	public static final Styler.ChartTheme CHART_THEME = Styler.ChartTheme.XChart;

	private final XYChart chart;
	private final SwingWrapper<XYChart> wrapper;
	@Getter
	private final JFrame frame;

	@Getter @Setter
	private double maxMagnitude = 100;

	public DFTPreview() {
		chart = new XYChartBuilder()
				.width(500)
				.height(300)
				.title("Frequency spectrum")
				.xAxisTitle("Frequency (bpm)")
				.yAxisTitle("Magnitude")
				.theme(CHART_THEME)
				.build();

		chart.getStyler().setLegendVisible(false);
		chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);
		chart.getStyler().setYAxisMin(0.0);
		chart.getStyler().setXAxisMin(0.0);
//		chart.getStyler().setXAxisMax(0.0);

		wrapper = new SwingWrapper<XYChart>(chart);
		frame = wrapper.displayChart();

		chart.addSeries("spectrum", new double[]{0.0});
	}

	public void setFreqSpectrum(Periodicity.FrequencySpectrum freqSpectrum) {
		chart.getStyler().setXAxisMax(freqSpectrum.getMagnitudesByFrequency().keySet().stream().mapToDouble(f -> f * 60.0).max().getAsDouble());
		chart.getStyler().setYAxisMax(Math.max(maxMagnitude, freqSpectrum.getMagnitudesByFrequency().entrySet().stream().mapToDouble(Map.Entry::getValue).max().getAsDouble()));

		double[] xData = freqSpectrum.getMagnitudesByFrequency().keySet().stream().mapToDouble(f -> f * 60.0).sorted().toArray();
		double[] yData = freqSpectrum.getMagnitudesByFrequency().entrySet().stream()
				.sorted(Comparator.comparingDouble(Map.Entry::getKey))
				.mapToDouble(Map.Entry::getValue)
				.toArray();

		chart.updateXYSeries("spectrum", xData, yData, null);

		SwingUtilities.invokeLater(wrapper::repaintChart);
	}
}
