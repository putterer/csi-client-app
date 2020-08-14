package de.putterer.indloc.ui;

import de.putterer.indloc.Station;
import de.putterer.indloc.csi.DataPreview;
import de.putterer.indloc.csi.calibration.AndroidInfo;
import de.putterer.indloc.data.DataClient;
import de.putterer.indloc.data.DataInfo;

import javax.swing.*;
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
	private final JButton showPreviewButton = new JButton("Show");
	private final JCheckBox showActivityUICheckbox = new JCheckBox("Show activity UI", false);

	private final List<Consumer<Station>> showPreviewCallbacks = new ArrayList<>();

	public GenericStatusUI(CsiUserInterface csiUserInterface) {
		super("CSI toolbox - Fabian Putterer - TUM", 420, 300);
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
						(client.getConsumers()[0].getType() == AndroidInfo.class ? "Connected: Android" : "Connected: CSI")
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
		this.add(stationsList);

		selectRespiratoryButton.setBounds(10, 140, 380/3, 20);
		this.add(selectRespiratoryButton);
		resubscribeButton.setBounds(20 + 380/3, 140, 380/3, 20);
		resubscribeButton.addActionListener(a -> new Thread(() -> {
			DataClient client = getCurrentlySelectedClient();
			client.unsubscribe();
			try { Thread.sleep(500); } catch(InterruptedException e) { e.printStackTrace(); }
			client.subscribe();
		}).start());
		this.add(resubscribeButton);
		unsubscribeButton.setBounds(30 + 380/3*2, 140, 380/3, 20);
		unsubscribeButton.addActionListener(a -> getCurrentlySelectedClient().unsubscribe());
		this.add(unsubscribeButton);

		initPreviewSelector();
		previewSelector.setBounds(10, 170, 300, 20);
		this.add(previewSelector);
		showPreviewButton.setEnabled(false);
		showPreviewButton.setBounds(320, 170, 90, 20);
		this.add(showPreviewButton);
		stationsList.addListSelectionListener(e -> showPreviewButton.setEnabled(true));

		showActivityUICheckbox.addItemListener(e -> csiUserInterface.setActivityUIsVisible(showActivityUICheckbox.isSelected()));
		showActivityUICheckbox.setBounds(10, 230, 200, 20);
		this.add(showActivityUICheckbox);

		onStationUpdated(null);

		getFrame().repaint();
	}

	private void initPreviewSelector() {
		List<String> previewNames = new LinkedList<>();

		addPreviewOption("PhaseDiffEvolution Default", station -> {
			csiUserInterface.addPreview(new DataPreview(new DataPreview.PhaseDiffEvolutionPreview(
					0,
					2,
					-1,
					10,
					10,
					0.8,
					new int[]{10,30,50}
			)), station);
		}, previewNames);
		addPreviewOption("PhaseDiffEvolution", station -> {
			csiUserInterface.addPreview(new DataPreview(new DataPreview.PhaseDiffEvolutionPreview(
					openIntDialog("rxAntenna1", 0, this.getFrame()),
					openIntDialog("rxAntenna2", 2, this.getFrame()),
					openIntDialog("shortTermHistoryLength", -1, this.getFrame()),
					openDoubleDialog("jumpThreshold", 10, this.getFrame()),
					openIntDialog("truncated mean length", 10, this.getFrame()),
					openDoubleDialog("truncated mean pct", 0.8, this.getFrame()),
					openIntListDialog("subcarriers", new int[]{10,30,50}, this.getFrame())
			)), station);
		}, previewNames);
		addPreviewOption("PhaseDiffVariance", station -> {
			csiUserInterface.addPreview(new DataPreview(new DataPreview.PhaseDiffVariancePreview(
					station,
					openIntListDialog("subcarriers", new int[]{SUBCARRIER_AVG, SUBCARRIER_MAX},
							Map.of(SUBCARRIER_MAX, "MAX", SUBCARRIER_AVG, "AVG"),
							this.getFrame())
			)), station);
		}, previewNames);
		addPreviewOption("PhaseDiffPreview", station -> {
			csiUserInterface.addPreview(new DataPreview(new DataPreview.PhaseDiffPreview(
					openIntDialog("rxAntenna1", 0, this.getFrame()),
					openIntDialog("rxAntenna2", 2, this.getFrame())
			)), station);
		}, previewNames);
		addPreviewOption("AndroidEvolution", station -> {
			//TODO
			//TODO: convert list option to mapper for generic objects instead of string
		}, previewNames);
		addPreviewOption("SubcarrierProperty Amplitude", station -> {
			csiUserInterface.addPreview(new DataPreview(new DataPreview.SubcarrierPropertyPreview(
					DataPreview.SubcarrierPropertyPreview.PropertyType.AMPLITUDE,
					openIntDialog("rxAntennaCount", 3, this.getFrame()),
					openIntDialog("txAntennaCount", 1, this.getFrame())
			)), station);
		}, previewNames);
		addPreviewOption("CSIPlot", station -> {
			csiUserInterface.addPreview(new DataPreview(new DataPreview.CSIPlotPreview(
					openIntDialog("rxAntennaCount", 3, this.getFrame()),
					openIntDialog("txAntennaCount", 1, this.getFrame())
			)), station);
		}, previewNames);

		//TODO add configurations of multiple previews with location

		previewSelector = new JComboBox<>(previewNames.toArray(new String[0]));

		showPreviewButton.addActionListener(a -> {
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


	@Override
	public void onDataInfo(Station station, DataInfo dataInfo) {

	}
}
