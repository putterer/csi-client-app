package de.putterer.indloc.ui;

import de.putterer.indloc.Station;
import de.putterer.indloc.activity.ActivityUI;
import de.putterer.indloc.csi.CSIInfo;
import de.putterer.indloc.csi.calibration.AndroidInfo;
import de.putterer.indloc.csi.messages.SubscriptionMessage;
import de.putterer.indloc.data.DataClient;
import de.putterer.indloc.data.DataConsumer;
import de.putterer.indloc.data.DataInfo;
import de.putterer.indloc.respiratory.RespiratoryUI;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static de.putterer.indloc.Config.ROOM;

public class CsiUserInterface implements KeyListener {
	private static final Point TOP_LEFT_OFFSET = new Point(30, 50);

	private static final SubscriptionMessage.SubscriptionOptions SUBSCRIPTION_OPTIONS = new SubscriptionMessage.SubscriptionOptions(
			new SubscriptionMessage.FilterOptions(DataClient.DEFAULT_ICMP_PAYLOAD_LENGTH)
	);

	private final List<UIComponentWindow> componentWindows = new ArrayList<>();

	private final GenericStatusUI genericStatusUI;
	private final RespiratoryUI respiratoryUI;
	private final List<ActivityUI> activityUIs = new ArrayList<>();

	public CsiUserInterface() {
		startClients();

		respiratoryUI = new RespiratoryUI();
		genericStatusUI = new GenericStatusUI(respiratoryUI);
		genericStatusUI.setPosition(TOP_LEFT_OFFSET.x, TOP_LEFT_OFFSET.y);
		respiratoryUI.setPosition(TOP_LEFT_OFFSET.x + genericStatusUI.getWindowWidth(), TOP_LEFT_OFFSET.y);

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


		componentWindows.stream().map(UIComponentWindow::getFrame).forEach(f -> f.addKeyListener(this));
	}

	private void startClients() {
		Arrays.stream(ROOM.getStations()).forEach(s ->
				DataClient.addClient(new DataClient(s, SUBSCRIPTION_OPTIONS, new DataConsumer<>(s.getDataType(), this::onData)))
		);

		Stream.concat(
				DataClient.getClients().stream().map(DataClient::getConnectedFuture),
				DataClient.getClients().stream().map(DataClient::getTimeoutFuture)
		).forEach(f -> f.thenAcceptAsync(s -> {
			if(genericStatusUI != null) {
				genericStatusUI.onStationUpdated(s);
			}
		}));
	}

	private void onData(DataInfo info) {
		//TODO: missing station?
		if(info instanceof CSIInfo) {
			CSIInfo csi = (CSIInfo) info;
			//todo
		} else if (info instanceof AndroidInfo) {
			AndroidInfo androidInfo = (AndroidInfo) info;
			//TODO
		}
	}

	public static void main(String[] args) {
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
