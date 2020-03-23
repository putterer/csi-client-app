package de.putterer.indloc.ui;

import de.putterer.indloc.Station;
import de.putterer.indloc.csi.calibration.AndroidInfo;
import de.putterer.indloc.data.DataClient;
import de.putterer.indloc.data.DataInfo;
import de.putterer.indloc.respiratory.RespiratoryUI;

import javax.swing.*;
import java.util.Arrays;

import static de.putterer.indloc.Config.ROOM;

public class GenericStatusUI extends UIComponentWindow {

	private final RespiratoryUI respiratoryUI;

	private final JLabel stationsLabel = new JLabel("Stations:");
	private final JList<String> stationsList = new JList<>();
	private final JButton selectRespiratoryButton = new JButton("Select");
	private final JButton resubscribeButton = new JButton("Subs.");
	private final JButton unsubscribeButton = new JButton("Unsubs.");

	public GenericStatusUI(RespiratoryUI respiratoryUI) {
		super("CSI toolbox - Fabian Putterer - TUM", 420, 300);
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
						(client.getConsumers()[0].getType() == AndroidInfo.class ? "Android" : "CSI")
						: (client.isTimedOut() ? "Timed out" : "CONNECTING...");
			}
			return String.format("%s (at %s): %s",
					s.getHW_ADDRESS(),
					s.getIP_ADDRESS(),
					status);
		}).toArray(String[]::new);
		stationsList.setListData(data);
	}

	private void initUI() {
		stationsLabel.setBounds(10, 10, 400, 20);
		this.add(stationsLabel);
		stationsList.setBounds(10, 30, 400, 100);
		stationsList.setLayoutOrientation(JList.VERTICAL);
		this.add(stationsList);

		selectRespiratoryButton.setBounds(10, 140, 100, 30);
		selectRespiratoryButton.addActionListener(a -> respiratoryUI.setStation(ROOM.getStations()[stationsList.getSelectedIndex()]));
		this.add(selectRespiratoryButton);
		resubscribeButton.setBounds(120, 140, 100, 30);
		resubscribeButton.addActionListener(a -> new Thread(() -> {
			DataClient client = getCurrentlySelectedClient();
			client.unsubscribe();
			try { Thread.sleep(500); } catch(InterruptedException e) { e.printStackTrace(); }
			client.subscribe();
		}).start());
		this.add(resubscribeButton);
		unsubscribeButton.setBounds(230, 140, 100, 30);
		unsubscribeButton.addActionListener(a -> getCurrentlySelectedClient().unsubscribe());
		this.add(unsubscribeButton);

		onStationUpdated(null);

		getFrame().repaint();
	}

	private DataClient getCurrentlySelectedClient() {
		return DataClient.getClient(ROOM.getStations()[stationsList.getSelectedIndex()]);
	}

	@Override
	public void onDataInfo(Station station, DataInfo dataInfo) {
		//TODO: count packets?
	}
}
