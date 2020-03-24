package de.putterer.indloc.ui;

import de.putterer.indloc.Station;
import de.putterer.indloc.activity.ActivityUI;
import de.putterer.indloc.csi.messages.SubscriptionMessage;
import de.putterer.indloc.data.DataClient;
import de.putterer.indloc.data.DataConsumer;
import de.putterer.indloc.data.DataInfo;
import de.putterer.indloc.respiratory.RespiratoryUI;
import de.putterer.indloc.util.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static de.putterer.indloc.Config.ROOM;

public class CsiUserInterface implements KeyListener {
	private static final Point TOP_LEFT_OFFSET = new Point(30, 50);

	private static final SubscriptionMessage.SubscriptionOptions SUBSCRIPTION_OPTIONS = new SubscriptionMessage.SubscriptionOptions(
			new SubscriptionMessage.FilterOptions(DataClient.DEFAULT_ICMP_PAYLOAD_LENGTH)
	);

	private final ExecutorService executorService = Executors.newFixedThreadPool(4);
	private final List<UIComponentWindow> componentWindows = new ArrayList<>();
	private volatile boolean initComplete = false;

	private final GenericStatusUI genericStatusUI;
	private final RespiratoryUI respiratoryUI;
	private final FrequencyGeneratorUI frequencyGeneratorUI;
	private final List<ActivityUI> activityUIs = new ArrayList<>();

	public CsiUserInterface() {
		startClients();

		frequencyGeneratorUI = new FrequencyGeneratorUI();
		respiratoryUI = new RespiratoryUI(frequencyGeneratorUI);
		genericStatusUI = new GenericStatusUI(respiratoryUI);
		genericStatusUI.setPosition(TOP_LEFT_OFFSET.x, TOP_LEFT_OFFSET.y);
		respiratoryUI.setPosition(TOP_LEFT_OFFSET.x + genericStatusUI.getWindowWidth(), TOP_LEFT_OFFSET.y);
		frequencyGeneratorUI.setPosition(TOP_LEFT_OFFSET.x + genericStatusUI.getWindowWidth(), TOP_LEFT_OFFSET.y + respiratoryUI.getWindowHeight());
		componentWindows.addAll(Arrays.asList(genericStatusUI, respiratoryUI, frequencyGeneratorUI));

		for(int i = 0;i < ROOM.getStations().length;i++) {
			Station station = ROOM.getStations()[i];
			ActivityUI activityUI = new ActivityUI(station, SUBSCRIPTION_OPTIONS);
			activityUIs.add(activityUI);
			activityUI.setPosition(TOP_LEFT_OFFSET.x + genericStatusUI.getWindowWidth() + respiratoryUI.getWindowWidth(),
					TOP_LEFT_OFFSET.y + activityUI.getWindowHeight() * i);
		}
		componentWindows.addAll(activityUIs);

		//TODO: more status?
		//TODO: previews

		componentWindows.stream().map(UIComponentWindow::getFrame).forEach(f -> f.setVisible(true));
		componentWindows.stream().map(UIComponentWindow::getFrame).forEach(f -> f.addKeyListener(this));

		try { Thread.sleep(200); } catch(InterruptedException e) { e.printStackTrace(); }
		componentWindows.forEach(UIComponentWindow::postConstruct);

		initComplete = true;
	}

	private void startClients() {
		Arrays.stream(ROOM.getStations()).forEach(s ->
				DataClient.addClient(new DataClient(s, SUBSCRIPTION_OPTIONS, new DataConsumer<>(
						s.getDataType(), info -> this.onData(s, info))
				))
		);

		DataClient.getClients().stream().map(DataClient::getStatusUpdateCallback).forEach(c -> c.addListener((_void, s) -> {
			if(genericStatusUI != null) {
				genericStatusUI.onStationUpdated(s);
			}
		}, true));
	}

	private void onData(Station station, DataInfo info) {
		if(!initComplete) {
			return;
		}

		componentWindows.forEach(w -> executorService.submit(() -> w.onDataInfo(station, info)));

//		if(info instanceof CSIInfo) {
//			CSIInfo csi = (CSIInfo) info;
//		} else if (info instanceof AndroidInfo) {
//			AndroidInfo androidInfo = (AndroidInfo) info;
//		}
	}

	public static void main(String[] args) {
		Logger.setLogLevel(Logger.Level.DEBUG);
		new CsiUserInterface();
	}

	@Override public void keyTyped(KeyEvent e) {}
	@Override public void keyReleased(KeyEvent e) {}
	@Override public void keyPressed(KeyEvent e) {
		if(e.getKeyCode() == KeyEvent.VK_F) {
			componentWindows.stream().map(UIComponentWindow::getFrame).forEach(JFrame::toFront);
		}
	}
}
