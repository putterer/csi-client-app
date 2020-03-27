package de.putterer.indloc.ui;

import de.putterer.indloc.Station;
import de.putterer.indloc.csi.DataPreview;
import de.putterer.indloc.csi.calibration.AndroidInfo;
import de.putterer.indloc.data.DataClient;
import de.putterer.indloc.data.DataInfo;
import de.putterer.indloc.respiratory.RespiratoryUI;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

import static de.putterer.indloc.Config.ROOM;
import static de.putterer.indloc.util.SwingUtil.openIntDialog;
import static de.putterer.indloc.util.SwingUtil.openIntListDialog;

public class GenericStatusUI extends UIComponentWindow {

	private CsiUserInterface csiUserInterface;
	private final RespiratoryUI respiratoryUI;

	private final JLabel stationsLabel = new JLabel("Stations:");
	private final JList<String> stationsList = new JList<>();
	private final JButton selectRespiratoryButton = new JButton("Select");
	private final JButton resubscribeButton = new JButton("Subs.");
	private final JButton unsubscribeButton = new JButton("Unsubs.");
	private JComboBox<String> previewSelector;

	private final List<Consumer<Station>> previewSelectedCallbacks = new ArrayList<>();

	public GenericStatusUI(CsiUserInterface csiUserInterface, RespiratoryUI respiratoryUI) {
		super("CSI toolbox - Fabian Putterer - TUM", 420, 300);
		this.csiUserInterface = csiUserInterface;
		this.respiratoryUI = respiratoryUI;

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
		selectRespiratoryButton.addActionListener(a -> respiratoryUI.setStation(getSelectedStation()));
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
		previewSelector.setBounds(10, 170, 400, 20);
		this.add(previewSelector);

		onStationUpdated(null);

		getFrame().repaint();
	}

	private void initPreviewSelector() {
		List<String> previewNames = new LinkedList<>();

		addPreviewOption("PhaseDiffEvolution", station -> {
			csiUserInterface.addPreview(new DataPreview(new DataPreview.PhaseDiffEvolutionPreview(
					openIntDialog("rxAntenna1", 0, this.getFrame()),
					openIntDialog("rxAntenna2", 1, this.getFrame()),
					openIntListDialog("subcarriers", new int[]{10,30,50}, this.getFrame())
			)), station);
		}, previewNames);
		addPreviewOption("PhaseDiffVariance", station -> {
			//TODO: no detector!!!
			csiUserInterface.addPreview(new DataPreview(new DataPreview.PhaseDiffVariancePreview(
					station,
					openIntListDialog("subcarriers", new int[]{10,30,50}, this.getFrame())
			)), station);
		}, previewNames);
		addPreviewOption("PhaseDiffPreview", station -> {
			csiUserInterface.addPreview(new DataPreview(new DataPreview.PhaseDiffPreview(
					openIntDialog("rxAntenna1", 0, this.getFrame()),
					openIntDialog("rxAntenna2", 1, this.getFrame())
			)), station);
		}, previewNames);
		addPreviewOption("AndroidEvolution", station -> {
			//TODO
		}, previewNames);
		addPreviewOption("SubcarrierProperty", station -> {}, previewNames);
		addPreviewOption("CSIPlot", station -> {
			csiUserInterface.addPreview(new DataPreview(new DataPreview.CSIPlotPreview(
					openIntDialog("rxAntennaCount", 3, this.getFrame()),
					openIntDialog("txAntennaCount", 1, this.getFrame())
			)), station);
		}, previewNames);

		//TODO add configurations of multiple previews with location

		previewSelector = new JComboBox<>(previewNames.toArray(new String[0]));
		previewSelector.addActionListener(a -> {
			new Thread(() -> {
				previewSelectedCallbacks.get(previewSelector.getSelectedIndex()).accept(getSelectedStation());
			}).start();
		});
	}

	private void addPreviewOption(String name, Consumer<Station> runnable, List<String> previewNames) {
		previewNames.add(name);
		previewSelectedCallbacks.add(runnable);
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
