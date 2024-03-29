package de.putterer.indloc.ui;

import de.putterer.indloc.Config;
import de.putterer.indloc.Station;
import de.putterer.indloc.csi.CSIInfo;
import de.putterer.indloc.csi.DataPreview;
import de.putterer.indloc.csi.DataPreview.AmplitudeDiffEvolutionPreview.AntennaSubcarrier;
import de.putterer.indloc.csi.atheros.AthCSIInfo;
import de.putterer.indloc.csi.calibration.AndroidInfo;
import de.putterer.indloc.csi.intel.IntCSIInfo;
import de.putterer.indloc.data.DataClient;
import de.putterer.indloc.data.DataInfo;
import de.putterer.indloc.data.ecg.EcgInfo;
import de.putterer.indloc.util.Logger;
import de.putterer.indloc.util.serialization.Serialization;
import org.apache.commons.math3.util.Pair;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;

import static de.putterer.indloc.Config.ROOM;
import static de.putterer.indloc.csi.DataPreview.PhaseDiffVariancePreview.SUBCARRIER_AVG;
import static de.putterer.indloc.csi.DataPreview.PhaseDiffVariancePreview.SUBCARRIER_MAX;
import static de.putterer.indloc.util.SwingUtil.*;

public class GenericStatusUI extends UIComponentWindow {

	private CsiUserInterface csiUserInterface;

	private final JLabel stationsLabel = new JLabel("Stations:");
	private final JList<String> stationsList = new JList<>();
	private final JButton selectRespiratoryButton = new JButton("Select");
	private final JButton resubscribeButton = new JButton("Subs.");
	private final JButton unsubscribeButton = new JButton("Unsubs.");
	private JComboBox<String> previewSelector;
	private final JButton showDefaultPreviewButton = new JButton("Default");
	private final JButton showPreviewButton = new JButton("Show");
	private final JToggleButton recordButton = new JToggleButton("Record");
	private final JCheckBox showActivityUICheckbox = new JCheckBox("Mov.", false);
	private final JCheckBox showRespiratoryUICheckbox = new JCheckBox("Resp.", false);
	private final JCheckBox showSampleRecordingUICheckbox = new JCheckBox("CRec.", false);

	private boolean useDefaultSettingsForPreview = false;

	private final List<Consumer<Station>> showPreviewCallbacks = new ArrayList<>();
	public GenericStatusUI(CsiUserInterface csiUserInterface) {
		super("CSI toolbox", 420, 300);
		this.csiUserInterface = csiUserInterface;

		this.setLayout(null);
		initUI();

		setupFinished();
	}

	public void onStationUpdated(Station station) {
		String[] data = Arrays.stream(ROOM.getStations()).map(s -> {
			DataClient client = DataClient.getClient(s);
			String status = "";
			if(client != null) {
				status = client.isConnected() ?
						(client.getConsumers()[0].getType() == AndroidInfo.class ? "Connected: Android" : (client.getConsumers()[0].getType() == EcgInfo.class ? "Connected: Serial" : "Connected: CSI"))
						: (client.isTimedOut() ? "Timed out" : "Connecting...");
			}
			return String.format("%s (at %s) - %s",
					s.getName() != null ? s.getName() : s.getHW_ADDRESS(),
					s.getIP_ADDRESS(),
					status);
		}).toArray(String[]::new);
		int selectedIndex = stationsList.getSelectedIndex();
		stationsList.setListData(data);
		stationsList.setSelectedIndex(selectedIndex);
	}

	private void initUI() {
		stationsLabel.setBounds(10, 10, 400, 20);
		this.add(stationsLabel);
		stationsList.setBounds(10, 30, 400, 100);
		stationsList.setLayoutOrientation(JList.VERTICAL);
//		stationsList.setFont(stationsList.getFont().deriveFont(16.0f));
		this.add(stationsList);

		selectRespiratoryButton.setBounds(10, 140, 380/3, 30);
		this.add(selectRespiratoryButton);
		resubscribeButton.setBounds(20 + 380/3, 140, 380/3, 30);
		resubscribeButton.addActionListener(a -> new Thread(() -> {
			DataClient client = getCurrentlySelectedClient();
			client.unsubscribe();
			try { Thread.sleep(500); } catch(InterruptedException e) { e.printStackTrace(); }
			client.subscribe();
		}).start());
		this.add(resubscribeButton);
		unsubscribeButton.setBounds(30 + 380/3*2, 140, 380/3, 30);
		unsubscribeButton.addActionListener(a -> getCurrentlySelectedClient().unsubscribe());
		this.add(unsubscribeButton);

		initPreviewSelector();
		previewSelector.setBounds(10, 180, 220, 30);
		this.add(previewSelector);
		showDefaultPreviewButton.setEnabled(false);
		showDefaultPreviewButton.setBounds(240, 180, 80, 30);
		this.add(showDefaultPreviewButton);
		stationsList.addListSelectionListener(e -> showDefaultPreviewButton.setEnabled(true));
		showPreviewButton.setEnabled(false);
		showPreviewButton.setBounds(330, 180, 80, 30);
		this.add(showPreviewButton);
		stationsList.addListSelectionListener(e -> showPreviewButton.setEnabled(true));

		recordButton.setBounds(10, 220, 200, 30);
		this.add(recordButton);
		recordButton.addActionListener(e -> {
			if(recordingFolder.isPresent()) {
				stopRecording();
			} else {
				startRecording();
			}
		});

		showActivityUICheckbox.addItemListener(e -> csiUserInterface.setActivityUIsVisible(showActivityUICheckbox.isSelected()));
		showActivityUICheckbox.setBounds(220, 220, 62, 30);
		this.add(showActivityUICheckbox);

		showRespiratoryUICheckbox.addItemListener(e -> csiUserInterface.setRespiratoryUIVisible(getSelectedStation(), showRespiratoryUICheckbox.isSelected()));
		showRespiratoryUICheckbox.setBounds(282, 220, 62, 30);
		showRespiratoryUICheckbox.setEnabled(false);
		stationsList.addListSelectionListener(e -> showRespiratoryUICheckbox.setEnabled(true));
		this.add(showRespiratoryUICheckbox);
		stationsList.addListSelectionListener(e -> showRespiratoryUICheckbox.setSelected(csiUserInterface.isRespiratoryUIVisible(getSelectedStation())));

		showSampleRecordingUICheckbox.addItemListener(e -> csiUserInterface.setCurveRecorderUIVisible(showSampleRecordingUICheckbox.isSelected()));
		showSampleRecordingUICheckbox.setBounds(344, 220, 62, 30);
		this.add(showSampleRecordingUICheckbox);

		onStationUpdated(null);

		stationsList.setSelectedIndex(0);

		getFrame().repaint();
	}

	private Color backgroundColor;
	private final Color recordingColor = new Color(195, 0, 0);
	private Optional<Path> recordingFolder = Optional.empty();
	private void startRecording() {
		synchronized (recordingColor) {
			String recordingName = openStringDialog(
					"Recording directory",
					(getSelectedStation().getDataType() == EcgInfo.class ? "ecg" : "csi") + "-recording_" + new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss").format(new Date()),
					this);

			if(recordingName == null) {
				return;
			}

			Path recordingPath = Paths.get(recordingName);
			try {
				Files.createDirectory(recordingPath);
			} catch(IOException e) {
				Logger.error("Could not create recording directory", e);
				return;
			}

			recordingFolder = Optional.of(recordingPath);
		}

		try {
			Serialization.serialize(recordingFolder.get().resolve("room.cfg"), false, Config.ROOM);
		} catch (IOException e) {
			Logger.error("Could not serialize room configuration", e);
		}

		recordButton.setSelected(true);

		backgroundColor = this.getBackground();
		this.setBackground(recordingColor);
	}

	private void stopRecording() {
		recordButton.setSelected(false);

		this.setBackground(backgroundColor);

		synchronized (recordingColor) {
			recordingFolder = Optional.empty();
		}
	}

	@Override
	public void onDataInfo(Station station, DataInfo dataInfo) {
		if(dataInfo instanceof CSIInfo || dataInfo instanceof EcgInfo || dataInfo instanceof AndroidInfo) {
			recordingFolder.ifPresent(folder -> {
				try {
					String file = "";
					if(dataInfo instanceof CSIInfo) {
						file = station.getHW_ADDRESS() + "-" + dataInfo.getMessageId() + ".csi";
					} else if(dataInfo instanceof EcgInfo) {
						file = station.getIP_ADDRESS().replace("/", "_") + "-" + dataInfo.getMessageId() + ".ecg";
					} else {
						file = station.getHW_ADDRESS() + "-" + dataInfo.getMessageId() + ".accel";
					}

					file = file + ".deflate";

					// looses type information, dataInfo type is present in station inside room config
					Serialization.save(folder.resolve(file), dataInfo);
				} catch (IOException e) {
					Logger.error("Could not serialize csi", e);
				}
			});
		}
	}

	private void initPreviewSelector() {
		List<String> previewNames = new LinkedList<>();

		addPreviewOption("Amplitude", station -> {
			csiUserInterface.addPreview(new DataPreview(station.getName(), new DataPreview.SubcarrierPropertyPreview(
					DataPreview.SubcarrierPropertyPreview.PropertyType.AMPLITUDE,
					openIntDialog("rxAntennaCount", 3, this.getFrame(), useDefaultSettingsForPreview),
					openIntDialog("txAntennaCount", 1, this.getFrame(), useDefaultSettingsForPreview),
					openIntDialog("smoothingPacketCount", 10, this.getFrame(), useDefaultSettingsForPreview)
			)), station);
		}, previewNames);
		addPreviewOption("AmplitudeEvolution", station -> {
			csiUserInterface.addPreview(new DataPreview(station.getName(), new DataPreview.AmplitudeEvolutionPreview(
					openIntDialog("rxAntenna", 0, this.getFrame(), useDefaultSettingsForPreview),
					openIntDialog("txAntenna", 0, this.getFrame(), useDefaultSettingsForPreview),
					openIntDialog("smoothingPacketCount", 10, this.getFrame(), useDefaultSettingsForPreview),
					openIntListDialog("subcarriers", new int[]{5,15,25}, this.getFrame(), useDefaultSettingsForPreview)
			)), station);
		}, previewNames);
		addPreviewOption("AmplitudeEvolutionAntDiff", station -> {
			csiUserInterface.addPreview(new DataPreview(station.getName(), new DataPreview.AmplitudeDiffEvolutionPreview(
					new Pair[]{
							new Pair(new AntennaSubcarrier(0, 0, 0), new AntennaSubcarrier(0, 2, 0)),
							new Pair(new AntennaSubcarrier(0, 0, 15), new AntennaSubcarrier(0, 2, 15)),
							new Pair(new AntennaSubcarrier(0, 0, 30), new AntennaSubcarrier(0, 2, 30))
					},
					openIntDialog("smoothingPacketCount", 10, this.getFrame(), useDefaultSettingsForPreview)
			)), station);
		}, previewNames);
		addPreviewOption("AmplitudeEvolutionCarrierDiff", station -> {
			csiUserInterface.addPreview(new DataPreview(station.getName(), new DataPreview.AmplitudeDiffEvolutionPreview(
					new Pair[]{
							new Pair(new AntennaSubcarrier(0, 0, 0), new AntennaSubcarrier(0, 2, station.getDataType() == AthCSIInfo.class ? 30 : 15)),
							new Pair(new AntennaSubcarrier(0, 0, 0), new AntennaSubcarrier(0, 2, station.getDataType() == AthCSIInfo.class ? 53 : 30))
					},
					openIntDialog("smoothingPacketCount", 10, this.getFrame(), useDefaultSettingsForPreview)
			)), station);
		}, previewNames);
		addPreviewOption("SerialEvolution", station -> {
			csiUserInterface.addPreview(new DataPreview(station.getName(), new DataPreview.SerialEvolutionPreview()), station);
		}, previewNames);
		addPreviewOption("Phase", station -> {
			csiUserInterface.addPreview(new DataPreview(station.getName(), new DataPreview.SubcarrierPropertyPreview(
					DataPreview.SubcarrierPropertyPreview.PropertyType.PHASE,
					openIntDialog("rxAntennaCount", 3, this.getFrame(), useDefaultSettingsForPreview),
					openIntDialog("txAntennaCount", 1, this.getFrame(), useDefaultSettingsForPreview),
					openIntDialog("smoothingPacketCount", 10, this.getFrame(), useDefaultSettingsForPreview)
			)), station);
		}, previewNames);
		addPreviewOption("AmplitudeDiff", station -> {
			csiUserInterface.addPreview(new DataPreview(station.getName(), new DataPreview.AmplitudeDiffPreview(
					openIntDialog("rxAntenna1", 0, this.getFrame(), useDefaultSettingsForPreview),
					openIntDialog("rxAntenna2", 1, this.getFrame(), useDefaultSettingsForPreview)
			)), station);
		}, previewNames);
		addPreviewOption("CSIPlot", station -> {
			csiUserInterface.addPreview(new DataPreview(station.getName(), new DataPreview.CSIPlotPreview(
					openIntDialog("rxAntennaCount", 3, this.getFrame(), useDefaultSettingsForPreview),
					openIntDialog("txAntennaCount", 1, this.getFrame(), useDefaultSettingsForPreview)
			)), station);
		}, previewNames);
		addPreviewOption("CSIDiffPlot", station -> {
			csiUserInterface.addPreview(new DataPreview(station.getName(), new DataPreview.CSIDiffPlotPreview(
					openIntDialog("rxAntenna1", 0, this.getFrame(), useDefaultSettingsForPreview),
					openIntDialog("txAntenna1", 0, this.getFrame(), useDefaultSettingsForPreview),
					openIntDialog("rxAntenna2", 2, this.getFrame(), useDefaultSettingsForPreview),
					openIntDialog("txAntenna2", 0, this.getFrame(), useDefaultSettingsForPreview),
					false, false)), station);
		}, previewNames);
		addPreviewOption("CSICmPlot", station -> {
			csiUserInterface.addPreview(new DataPreview(station.getName(), new DataPreview.CSIDiffPlotPreview(
					openIntDialog("rxAntenna1", 0, this.getFrame(), useDefaultSettingsForPreview),
					openIntDialog("txAntenna1", 0, this.getFrame(), useDefaultSettingsForPreview),
					openIntDialog("rxAntenna2", 2, this.getFrame(), useDefaultSettingsForPreview),
					openIntDialog("txAntenna2", 0, this.getFrame(), useDefaultSettingsForPreview),
					true, false)), station);
		}, previewNames);
		addPreviewOption("CSICmPlotNormalized", station -> {
			csiUserInterface.addPreview(new DataPreview(station.getName(), new DataPreview.CSIDiffPlotPreview(
					openIntDialog("rxAntenna1", 0, this.getFrame(), useDefaultSettingsForPreview),
					openIntDialog("txAntenna1", 0, this.getFrame(), useDefaultSettingsForPreview),
					openIntDialog("rxAntenna2", 2, this.getFrame(), useDefaultSettingsForPreview),
					openIntDialog("txAntenna2", 0, this.getFrame(), useDefaultSettingsForPreview),
					true, true)), station);
		}, previewNames);
		addPreviewOption("CSICmProcessed", station -> {
			csiUserInterface.addPreview(new DataPreview(station.getName(), new DataPreview.CSICmProcessedPlotPreview(
					openIntDialog("rxAntenna1", 0, this.getFrame(), useDefaultSettingsForPreview),
					openIntDialog("txAntenna1", 0, this.getFrame(), useDefaultSettingsForPreview),
					openIntDialog("rxAntenna2", 2, this.getFrame(), useDefaultSettingsForPreview),
					openIntDialog("txAntenna2", 0, this.getFrame(), useDefaultSettingsForPreview),
					openIntDialog("slidingWindowSize", 150, this.getFrame(), useDefaultSettingsForPreview),
					openIntDialog("timestampCountForAverage", 10, this.getFrame(), useDefaultSettingsForPreview),
					Math.toRadians(openDoubleDialog("stddevThresholdForSamePhaseDetection (degree)", 5.0, this.getFrame(), useDefaultSettingsForPreview)),
					openDoubleDialog("thresholdForOffsetCorrection", 22000.0, this.getFrame(), useDefaultSettingsForPreview)
			)), station);
		}, previewNames);
		addPreviewOption("CSICMShapePreview", station -> {
			csiUserInterface.addPreview(new DataPreview(station.getName(), new DataPreview.CSICMCurveShapePlotPreview(
					openIntDialog("rxAntenna1", 0, this.getFrame(), useDefaultSettingsForPreview),
					openIntDialog("txAntenna1", 0, this.getFrame(), useDefaultSettingsForPreview),
					openIntDialog("rxAntenna2", 2, this.getFrame(), useDefaultSettingsForPreview),
					openIntDialog("txAntenna2", 0, this.getFrame(), useDefaultSettingsForPreview),
					openIntDialog("slidingWindowSize", 150, this.getFrame(), useDefaultSettingsForPreview),
					openIntDialog("timestampCountForAverage", 10, this.getFrame(), useDefaultSettingsForPreview),
					Math.toRadians(openDoubleDialog("stddevThresholdForSamePhaseDetection (degree)", 5.0, this.getFrame(), useDefaultSettingsForPreview)),
					openDoubleDialog("thresholdForOffsetCorrection", 22000.0, this.getFrame(), useDefaultSettingsForPreview),
					openIntDialog("relative angles", 1, this.getFrame(), useDefaultSettingsForPreview) == 1
			)), station);
		}, previewNames);
		addPreviewOption("CSICMShapeAngle", station -> {
			csiUserInterface.addPreview(new DataPreview(station.getName(), new DataPreview.CSICMCurveShapeAngleDistributionPreview(
					openIntDialog("rxAntenna1", 0, this.getFrame(), useDefaultSettingsForPreview),
					openIntDialog("txAntenna1", 0, this.getFrame(), useDefaultSettingsForPreview),
					openIntDialog("rxAntenna2", 2, this.getFrame(), useDefaultSettingsForPreview),
					openIntDialog("txAntenna2", 0, this.getFrame(), useDefaultSettingsForPreview),
					openIntDialog("slidingWindowSize", 150, this.getFrame(), useDefaultSettingsForPreview),
					openIntDialog("timestampCountForAverage", 10, this.getFrame(), useDefaultSettingsForPreview),
					Math.toRadians(openDoubleDialog("stddevThresholdForSamePhaseDetection (degree)", 5.0, this.getFrame(), useDefaultSettingsForPreview)),
					openDoubleDialog("thresholdForOffsetCorrection", 22000.0, this.getFrame(), useDefaultSettingsForPreview),
					openIntDialog("relative angles", 1, this.getFrame(), useDefaultSettingsForPreview) == 0,
					openIntDialog("unwrap angles", 0, this.getFrame(), useDefaultSettingsForPreview) == 1,
					openIntDialog("fix phase offset", 0, this.getFrame(), useDefaultSettingsForPreview) == 1
			)), station);
		}, previewNames);
		addPreviewOption("CSICMShapeDist", station -> {
			csiUserInterface.addPreview(new DataPreview(station.getName(), new DataPreview.CSICMCurveShapeDistDistributionPreview(
					openIntDialog("rxAntenna1", 0, this.getFrame(), useDefaultSettingsForPreview),
					openIntDialog("txAntenna1", 0, this.getFrame(), useDefaultSettingsForPreview),
					openIntDialog("rxAntenna2", 2, this.getFrame(), useDefaultSettingsForPreview),
					openIntDialog("txAntenna2", 0, this.getFrame(), useDefaultSettingsForPreview),
					openIntDialog("slidingWindowSize", 150, this.getFrame(), useDefaultSettingsForPreview),
					openIntDialog("timestampCountForAverage", 10, this.getFrame(), useDefaultSettingsForPreview),
					Math.toRadians(openDoubleDialog("stddevThresholdForSamePhaseDetection (degree)", 5.0, this.getFrame(), useDefaultSettingsForPreview)),
					openDoubleDialog("thresholdForOffsetCorrection", 22000.0, this.getFrame(), useDefaultSettingsForPreview)
			)), station);
		}, previewNames);
		addPreviewOption("PhaseDiffEvolution", station -> {
			csiUserInterface.addPreview(new DataPreview(station.getName(), new DataPreview.PhaseDiffEvolutionPreview(
					openIntDialog("rxAntenna1", 0, this.getFrame(), useDefaultSettingsForPreview),
					openIntDialog("rxAntenna2", station.getDataType() == IntCSIInfo.class ? 1 : 2, this.getFrame(), useDefaultSettingsForPreview),
					openIntDialog("shortTermHistoryLength", -1, this.getFrame(), useDefaultSettingsForPreview),
					openDoubleDialog("jumpThreshold", 10, this.getFrame(), useDefaultSettingsForPreview),
					openIntDialog("truncated mean length", 10, this.getFrame(), useDefaultSettingsForPreview),
					openDoubleDialog("truncated mean pct", 0.8, this.getFrame(), useDefaultSettingsForPreview),
					openIntListDialog("subcarriers", station.getDataType() == IntCSIInfo.class ? new int[]{5,15,25} : new int[]{10,30,50}, this.getFrame(), useDefaultSettingsForPreview)
			)), station);
		}, previewNames);
		addPreviewOption("PhaseDiffVariance", station -> {
			csiUserInterface.addPreview(new DataPreview(station.getName(), new DataPreview.PhaseDiffVariancePreview(
					station,
					openIntListDialog("subcarriers", new int[]{SUBCARRIER_AVG, SUBCARRIER_MAX},
							Map.of(SUBCARRIER_MAX, "MAX", SUBCARRIER_AVG, "AVG"),
							this.getFrame(), useDefaultSettingsForPreview)
			)), station);
		}, previewNames);
		addPreviewOption("PhaseDiff", station -> {
			csiUserInterface.addPreview(new DataPreview(station.getName(), new DataPreview.PhaseDiffPreview(
					openIntDialog("rxAntenna1", 0, this.getFrame(), useDefaultSettingsForPreview),
					openIntDialog("rxAntenna2", 2, this.getFrame(), useDefaultSettingsForPreview)
			)), station);
		}, previewNames);
		addPreviewOption("AndroidEvolution", station -> {
			csiUserInterface.addPreview(new DataPreview(station.getName(), new DataPreview.AndroidEvolutionPreview(
					openDoubleDialog("limit", 30.0, this.getFrame(), useDefaultSettingsForPreview),
					Arrays.stream(openStringDialog("dataTypes (X,Y,Z,EUCLIDEAN,MANHATTAN,MAX)", "X,Y", this.getFrame(), useDefaultSettingsForPreview)
						.split(",")).map(name ->
							Arrays.stream(DataPreview.AndroidEvolutionPreview.AndroidDataType.values()).filter(it -> it.name().equals(name)).findFirst().orElse(null)
						).filter(Objects::nonNull).toArray(DataPreview.AndroidEvolutionPreview.AndroidDataType[]::new)
			)), station);
			//TODO: convert list option to mapper for generic objects instead of string
		}, previewNames);

		//TODO add configurations of multiple previews with location

		previewSelector = new JComboBox<>(previewNames.toArray(new String[0]));

		showPreviewButton.addActionListener(a -> {
			useDefaultSettingsForPreview = false;
			new Thread(() -> {
				showPreviewCallbacks.get(previewSelector.getSelectedIndex()).accept(getSelectedStation());
			}).start();
		});
		showDefaultPreviewButton.addActionListener(a -> {
			useDefaultSettingsForPreview = true;
			new Thread(() -> {
				showPreviewCallbacks.get(previewSelector.getSelectedIndex()).accept(getSelectedStation());
			}).start();
		});
	}

	private void addPreviewOption(String name, Consumer<Station> runnable, List<String> previewNames) {
		previewNames.add(name);
		showPreviewCallbacks.add(runnable);
	}

	private DataClient getCurrentlySelectedClient() {
		return DataClient.getClient(getSelectedStation());
	}

	private Station getSelectedStation() {
		return ROOM.getStations()[stationsList.getSelectedIndex()];
	}
}
