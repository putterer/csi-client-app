package de.putterer.indloc.respiratory;

import de.putterer.indloc.Station;
import de.putterer.indloc.acceleration.PeriodicityDetector;
import de.putterer.indloc.csi.DataPreview;
import de.putterer.indloc.csi.DataPreview.AndroidEvolutionPreview.AndroidDataType;
import de.putterer.indloc.csi.calibration.AndroidInfo;
import de.putterer.indloc.csi.intel.IntCSIInfo;
import de.putterer.indloc.data.DataClient;
import de.putterer.indloc.data.DataInfo;
import de.putterer.indloc.ui.DFTPreview;
import de.putterer.indloc.ui.FrequencyGeneratorUI;
import de.putterer.indloc.ui.UIComponentWindow;
import de.putterer.indloc.util.Observable;

import javax.swing.*;
import java.awt.*;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

import static de.putterer.indloc.Config.ROOM;
import static javax.swing.SwingUtilities.invokeLater;

public class RespiratoryUI extends UIComponentWindow {

//	public static final double DEFAULT_SAMPLING_FREQUENCY = 1.0 / 0.02; // "game mode" on android
	private static final double DEFAULT_SAMPLING_FREQUENCY = 5.0;
	private static final Duration DEFAULT_SLIDING_WINDOW = Duration.ofSeconds(15);
	private static final Duration DEFAULT_TRUNCATED_MEAN_DURATION = Duration.ofMillis(200);
	private static final double DEFAULT_TRUNCATED_MEAN_PCT = 0.6;

	private final JLabel typeLabel = new JLabel("Type:");
	private final JLabel packetsReceivedLabel = new JLabel("Packets:");
	private final JLabel stationNameLabel = new JLabel("Station: ");
	private final JButton samplingFrequencyLabel = new JButton("Sampl. freq.:");
	private final JButton slidingWindowSizeLabel = new JButton("Sliding window:");
	private final JButton truncatedMeanWindowLabel = new JButton("Trun. μ win.:");
	private final JButton truncatedMeanPctLabel = new JButton("Trun. μ pct.:");
	private final JLabel binSpacingLabel = new JLabel("");
	private final JLabel bpmLabel = new JLabel("");
	private final JToggleButton dftPreviewButton = new JToggleButton("Show spectrum", false);
	private final JToggleButton frequencyGeneratorButton = new JToggleButton("Frequency generator", false);
	private final JToggleButton previewAndroidDataButton = new JToggleButton("Android data preview", false);

	private final DataPreview rawAndroidDataPreview;
	private final DFTPreview dftPreview;

	private final FrequencyGeneratorUI frequencyGeneratorUI;

	private Station station;
	private PeriodicityDetector periodicityDetector;
	private Thread samplingThread;
	private DataInfo lastSeenDataInfo = null;
	private Observable.ChangeListener<Integer> packetsReceivedListener;

	public RespiratoryUI(FrequencyGeneratorUI frequencyGeneratorUI) {
		super("Respiratory Detection", 420, 300);
		this.frequencyGeneratorUI = frequencyGeneratorUI;

		this.setLayout(null);
		initUI();

		Arrays.stream(ROOM.getStations()).findFirst().ifPresent(this::setStation);

		setupFinished();

		rawAndroidDataPreview = new DataPreview(new DataPreview.AndroidEvolutionPreview(
				10.0f,
				AndroidDataType.Y,
				AndroidDataType.Z
		));
		rawAndroidDataPreview.getFrame().setBounds(500, 450, 1200, 600);
		dftPreview = new DFTPreview();
		dftPreview.getFrame().setBounds(500, 450, 1200, 600);
	}

	@Override
	public void postConstruct() {
		rawAndroidDataPreview.getFrame().setVisible(false);
		dftPreview.getFrame().setVisible(false);
		samplingThread = new Thread(this::samplingThread);
		samplingThread.start();
	}

	private void initUI() {
		typeLabel.setBounds(10, 10, 120, 20);
		this.add(typeLabel);
		packetsReceivedLabel.setBounds(140, 10, 110, 20);
		this.add(packetsReceivedLabel);
		stationNameLabel.setBounds(260, 10, 130, 20);
		this.add(stationNameLabel);
		samplingFrequencyLabel.setBounds(10, 40, 195, 20);
		this.add(samplingFrequencyLabel);
		slidingWindowSizeLabel.setBounds(215, 40, 195, 20);
		this.add(slidingWindowSizeLabel);
		truncatedMeanWindowLabel.setBounds(10, 70, 195, 20);
		this.add(truncatedMeanWindowLabel);
		truncatedMeanPctLabel.setBounds(215, 70, 195, 20);
		this.add(truncatedMeanPctLabel);

		samplingFrequencyLabel.addActionListener(a -> {
			try {
				double newSamplingFrequency = Double.parseDouble(JOptionPane.showInputDialog(this,
						"Sampling frequency?", periodicityDetector.getSamplingFrequency()));
				rebuild(station,
						newSamplingFrequency,
						periodicityDetector.getSlidingWindowDuration(),
						periodicityDetector.getTruncatedMeanWindowDuration(),
						periodicityDetector.getTruncatedMeanWindowPct()
				);
			} catch (NumberFormatException | NullPointerException e) {}
		});
		slidingWindowSizeLabel.addActionListener(a -> {
			try {
				double newSlidingWindowDuration = Double.parseDouble(JOptionPane.showInputDialog(this,
						"Sliding window duration (s)?", periodicityDetector.getSlidingWindowDuration().toSeconds()));
				rebuild(station,
						periodicityDetector.getSamplingFrequency(),
						Duration.ofMillis((long)(newSlidingWindowDuration * 1000.0)),
						periodicityDetector.getTruncatedMeanWindowDuration(),
						periodicityDetector.getTruncatedMeanWindowPct()
				);
			} catch (NumberFormatException | NullPointerException e) {}
		});

		truncatedMeanWindowLabel.addActionListener(a -> {
			try {
				double newTruncatedMeanWindowDuration = Double.parseDouble(JOptionPane.showInputDialog(this,
						"Truncated mean window duration (s)?", periodicityDetector.getTruncatedMeanWindowDuration().toMillis() / 1000.0));
				rebuild(station,
						periodicityDetector.getSamplingFrequency(),
						periodicityDetector.getSlidingWindowDuration(),
						Duration.ofMillis((long)(newTruncatedMeanWindowDuration * 1000.0)),
						periodicityDetector.getTruncatedMeanWindowPct()
				);
			} catch (NumberFormatException | NullPointerException e) {}
		});

		truncatedMeanPctLabel.addActionListener(a -> {
			try {
				double newTruncatedMeanPct = Double.parseDouble(JOptionPane.showInputDialog(this,
						"Truncated mean window pct (0.0 - 1.0)?", periodicityDetector.getTruncatedMeanWindowPct()));
				rebuild(station,
						periodicityDetector.getSamplingFrequency(),
						periodicityDetector.getSlidingWindowDuration(),
						periodicityDetector.getTruncatedMeanWindowDuration(),
						newTruncatedMeanPct
				);
			} catch (NumberFormatException | NullPointerException e) {}
		});

		binSpacingLabel.setBounds(10, 100, 400, 20);
		this.add(binSpacingLabel);

		bpmLabel.setFont(new Font(bpmLabel.getFont().getName(), Font.PLAIN, 42));
		bpmLabel.setHorizontalAlignment(JLabel.CENTER);
		bpmLabel.setVerticalAlignment(JLabel.CENTER);
		bpmLabel.setBounds(10, 130, 400, 60);
		this.add(bpmLabel);

		dftPreviewButton.setBounds(10, 200, 400, 20);
		dftPreviewButton.addActionListener(a -> {
			if(dftPreviewButton.isSelected()) {
				double maxMagnitude = Double.parseDouble(JOptionPane.showInputDialog(this,
						"Maximum magnitude?", 10.0));
				dftPreview.setMaxMagnitude(maxMagnitude);
			}
			dftPreview.getFrame().setVisible(dftPreviewButton.isSelected());
		});
		this.add(dftPreviewButton);

		previewAndroidDataButton.setBounds(10, 230, 195, 20);
		previewAndroidDataButton.addActionListener(a -> rawAndroidDataPreview.getFrame().setVisible(previewAndroidDataButton.isSelected()));
		this.add(previewAndroidDataButton);

		frequencyGeneratorButton.setBounds(215, 230, 195, 20);
		frequencyGeneratorButton.addActionListener(a -> frequencyGeneratorUI.getFrame().setVisible(frequencyGeneratorButton.isSelected()));
		this.add(frequencyGeneratorButton);
	}

	public void rebuild(Station station, double samplingFrequency, Duration slidingWindowSize, Duration truncatedMeanWindowDuration, double truncatedMeanWindowPct) {
		if(packetsReceivedListener != null) {
			DataClient.getClient(station).getPacketsReceived().removeListener(packetsReceivedListener);
		}

		this.station = station;
		DataClient client = DataClient.getClient(station);

		typeLabel.setText(station.getDataType() == AndroidInfo.class ? "Type: Android" : (station.getDataType() == IntCSIInfo.class ? "Type: CSI (int)" : "Type: CSI (ath)"));
		packetsReceivedListener = (oldValue, newValue) -> invokeLater(
				() -> packetsReceivedLabel.setText("Packets: " + newValue)
		);
		client.getPacketsReceived().addListener(packetsReceivedListener, false);
		stationNameLabel.setText("Station: " + Optional.ofNullable(station.getName()).orElse(station.getIP_ADDRESS()));

		periodicityDetector = new PeriodicityDetector(samplingFrequency, slidingWindowSize, truncatedMeanWindowDuration, truncatedMeanWindowPct);
		periodicityDetector.getCurrentFrequency().addListener((oldValue, newValue) -> {
			invokeLater(() -> bpmLabel.setText(periodicityDetector.isIdle() ? "-" : String.format("%.1f bpm", newValue * 60.0f)));
		}, false);
		periodicityDetector.getFreqSpectrum().addListener((_void, newSpectrum) -> {
			dftPreview.setFreqSpectrum(newSpectrum);
		}, true);

		samplingFrequencyLabel.setText(String.format("Sampl. freq.: %.1f Hz", periodicityDetector.getSamplingFrequency()));
		slidingWindowSizeLabel.setText(String.format("Sliding window: %.1f s", periodicityDetector.getSlidingWindowDuration().toMillis() / 1000.0f));
		truncatedMeanWindowLabel.setText(String.format("Trun. μ win.: %.1f s", periodicityDetector.getTruncatedMeanWindowDuration().toMillis() / 1000.0f));
		truncatedMeanPctLabel.setText(String.format("Trun. μ pct.: %.1f", periodicityDetector.getTruncatedMeanWindowPct()));
		double binSpacing = Periodicity.getBinSpacing(periodicityDetector.getSamplingFrequency(), periodicityDetector.getSlidingWindowSize());
		binSpacingLabel.setText(String.format("Res: %.2f Hz = %.1f bpm,    N: %d,    μ-N: %d",
				binSpacing, binSpacing * 60.0,
				periodicityDetector.getSlidingWindowSize(),
				(int)(periodicityDetector.getTruncatedMeanWindowDuration().toMillis() / 1000.0 * periodicityDetector.getSamplingFrequency()))
		);
	}

	@Override
	public void onDataInfo(Station station, DataInfo info) {
		if(station != this.station) {
			return;
		}


		lastSeenDataInfo = info;

		if (info instanceof AndroidInfo) {
			AndroidInfo androidInfo = (AndroidInfo) info;

			if(rawAndroidDataPreview.getFrame().isVisible()) {
				rawAndroidDataPreview.setData(androidInfo);
			}
		}
	}

	public RespiratoryUI setStation(Station station) {
		if(periodicityDetector != null) {
			rebuild(station,
					periodicityDetector.getSamplingFrequency(),
					periodicityDetector.getSlidingWindowDuration(),
					periodicityDetector.getTruncatedMeanWindowDuration(),
					periodicityDetector.getTruncatedMeanWindowPct()
			);
		} else {
			rebuild(station,
					DEFAULT_SAMPLING_FREQUENCY,
					DEFAULT_SLIDING_WINDOW,
					DEFAULT_TRUNCATED_MEAN_DURATION,
					DEFAULT_TRUNCATED_MEAN_PCT
			);
		}

		return this;
	}

	private void samplingThread() {
		long nextTime = System.currentTimeMillis();
		while(true) {
			if(periodicityDetector != null && lastSeenDataInfo != null) {
				periodicityDetector.onData(lastSeenDataInfo);
			}

			long desiredDelta = (long) (1.0 / periodicityDetector.getSamplingFrequency() * 1000.0);
			nextTime += desiredDelta;

			while(System.currentTimeMillis() < nextTime) {
				try { Thread.sleep(Math.max(1, (System.currentTimeMillis() - nextTime) / 2)); } catch(InterruptedException e) { e.printStackTrace();break; }
			}
		}
	}
}
