package de.putterer.indloc.respiratory;

import de.putterer.indloc.Station;
import de.putterer.indloc.acceleration.PeriodicityDetector;
import de.putterer.indloc.csi.DataPreview;
import de.putterer.indloc.csi.DataPreview.AndroidEvolutionPreview.AndroidDataType;
import de.putterer.indloc.csi.calibration.AndroidInfo;
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

import static de.putterer.indloc.Config.ROOM;
import static javax.swing.SwingUtilities.invokeLater;

public class RespiratoryUI extends UIComponentWindow {

//	public static final double DEFAULT_SAMPLING_FREQUENCY = 1.0 / 0.02; // "game mode" on android
	public static final double DEFAULT_SAMPLING_FREQUENCY = 5.0;
	public static final Duration DEFAULT_SLIDING_WINDOW = Duration.ofSeconds(15);

	private final JLabel typeLabel = new JLabel("Type:");
	private final JLabel packetsReceivedLabel = new JLabel("Packets:");
	private final JButton samplingFrequencyLabel = new JButton("Sampl. freq.:");
	private final JButton slidingWindowSizeLabel = new JButton("Sliding window:");
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
		packetsReceivedLabel.setBounds(140, 10, 130, 20);
		this.add(packetsReceivedLabel);
		samplingFrequencyLabel.setBounds(10, 40, 195, 20);
		this.add(samplingFrequencyLabel);
		slidingWindowSizeLabel.setBounds(215, 40, 195, 20);
		this.add(slidingWindowSizeLabel);

		samplingFrequencyLabel.addActionListener(a -> {
			try {
				double newSamplingFrequency = Double.parseDouble(JOptionPane.showInputDialog(this,
						"Sampling frequency?", periodicityDetector.getSamplingFrequency()));
				rebuild(station, newSamplingFrequency, periodicityDetector.getSlidingWindowDuration());
			} catch (NumberFormatException | NullPointerException e) {}
		});
		slidingWindowSizeLabel.addActionListener(a -> {
			try {
				double newSlidingWindowDuration = Double.parseDouble(JOptionPane.showInputDialog(this,
						"Sliding window duration (s)?", periodicityDetector.getSlidingWindowDuration().toMillis()));
				rebuild(station, periodicityDetector.getSamplingFrequency(), Duration.ofMillis((long)(newSlidingWindowDuration * 1000.0)));
			} catch (NumberFormatException | NullPointerException e) {}
		});

		binSpacingLabel.setBounds(10, 70, 400, 20);
		this.add(binSpacingLabel);

		bpmLabel.setFont(new Font(bpmLabel.getFont().getName(), Font.PLAIN, 42));
		bpmLabel.setHorizontalAlignment(JLabel.CENTER);
		bpmLabel.setVerticalAlignment(JLabel.CENTER);
		bpmLabel.setBounds(10, 100, 400, 60);
		this.add(bpmLabel);

		dftPreviewButton.setBounds(10, 200, 400, 20);
		dftPreviewButton.addActionListener(a -> {
			if(dftPreviewButton.isSelected()) {
				double maxMagnitude = Double.parseDouble(JOptionPane.showInputDialog(this,
						"Maximum magnitude?", 100.0));
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

	public void rebuild(Station station, double samplingFrequency, Duration slidingWindowSize) {
		if(packetsReceivedListener != null) {
			DataClient.getClient(station).getPacketsReceived().removeListener(packetsReceivedListener);
		}

		this.station = station;
		DataClient client = DataClient.getClient(station);

		typeLabel.setText(station.getDataType() == AndroidInfo.class ? "Type: Android" : "Type: CSI");
		packetsReceivedListener = (oldValue, newValue) -> invokeLater(
				() -> packetsReceivedLabel.setText("Packets: " + newValue)
		);
		client.getPacketsReceived().addListener(packetsReceivedListener, false);

		periodicityDetector = new PeriodicityDetector(samplingFrequency, slidingWindowSize);
		periodicityDetector.getCurrentFrequency().addListener((oldValue, newValue) -> {
			invokeLater(() -> bpmLabel.setText(periodicityDetector.isIdle() ? "-" : String.format("%.1f bpm", newValue * 60.0f)));
		}, false);
		periodicityDetector.getFreqSpectrum().addListener((_void, newSpectrum) -> {
			dftPreview.setFreqSpectrum(newSpectrum);
		}, true);

		samplingFrequencyLabel.setText(String.format("Sampl. freq.: %.1f Hz", periodicityDetector.getSamplingFrequency()));
		slidingWindowSizeLabel.setText(String.format("Sliding window: %.1f s", periodicityDetector.getSlidingWindowDuration().toMillis() / 1000.0f));
		double binSpacing = Periodicity.getBinSpacing(periodicityDetector.getSamplingFrequency(), periodicityDetector.getSlidingWindowSize());
		binSpacingLabel.setText(String.format("Freq. resolution:  %.2f Hz   %.1f bpm", binSpacing, binSpacing * 60.0));
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

	public void setStation(Station station) {
		//TODO: remove packet listener
		if(periodicityDetector != null) {
			rebuild(station, periodicityDetector.getSamplingFrequency(), periodicityDetector.getSlidingWindowDuration());
		} else {
			rebuild(station, DEFAULT_SAMPLING_FREQUENCY, DEFAULT_SLIDING_WINDOW);
		}
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
