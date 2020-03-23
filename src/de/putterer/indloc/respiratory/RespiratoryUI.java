package de.putterer.indloc.respiratory;

import de.putterer.indloc.Station;
import de.putterer.indloc.acceleration.PeriodicityDetector;
import de.putterer.indloc.csi.calibration.AndroidInfo;
import de.putterer.indloc.data.DataClient;
import de.putterer.indloc.ui.UIComponentWindow;

import javax.swing.*;
import java.time.Duration;
import java.util.Arrays;

import static de.putterer.indloc.Config.ROOM;
import static javax.swing.SwingUtilities.invokeLater;

public class RespiratoryUI extends UIComponentWindow {

	public static final double DEFAULT_SAMPLING_FREQUENCY = 1.0 / 0.08;
	public static final Duration DEFAULT_SLIDING_WINDOW = Duration.ofSeconds(7);

	private final JLabel typeLabel = new JLabel("Type:");
	private final JLabel packetsReceivedLabel = new JLabel("Packets:");
	private final JButton samplingFrequencyLabel = new JButton("Sampl. freq.:");
	private final JButton slidingWindowSizeLabel = new JButton("Sliding window:");

	private PeriodicityDetector periodicityDetector;

	public RespiratoryUI() {
		super("Respiratory Detection", 420, 300);

		this.setLayout(null);
		initUI();

		Arrays.stream(ROOM.getStations()).findFirst().ifPresent(this::setStation);

		setupFinished();
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
	}

	public void setStation(Station station) {
		if(periodicityDetector != null) {
			rebuild(station, periodicityDetector.getSamplingFrequency(), periodicityDetector.getSlidingWindowDuration());
		} else {
			rebuild(station, DEFAULT_SAMPLING_FREQUENCY, DEFAULT_SLIDING_WINDOW);
		}
	}

	public void rebuild(Station station, double samplingFrequency, Duration slidingWindowSize) {
		DataClient client = DataClient.getClient(station);
		typeLabel.setText(station.getDataType() == AndroidInfo.class ? "Type: Android" : "Type: CSI");
		client.getPacketsReceived().addListener((oldValue, newValue) -> invokeLater(
				() -> packetsReceivedLabel.setText("Packets: " + newValue)
		), false);

		periodicityDetector = new PeriodicityDetector(samplingFrequency, slidingWindowSize);

		samplingFrequencyLabel.setText(String.format("Sampl. freq.: %.1f Hz", periodicityDetector.getSamplingFrequency()));
		slidingWindowSizeLabel.setText(String.format("Sliding window: %.1f s", periodicityDetector.getSlidingWindowDuration().toMillis() / 1000.0f));


		//TODO: instead use update method periodically called? or observables inside the detector?
	}
}
