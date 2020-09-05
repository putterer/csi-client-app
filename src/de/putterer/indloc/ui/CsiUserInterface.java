package de.putterer.indloc.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import de.putterer.indloc.Config;
import de.putterer.indloc.Station;
import de.putterer.indloc.activity.ActivityUI;
import de.putterer.indloc.csi.CSIInfo;
import de.putterer.indloc.csi.CSIReplay;
import de.putterer.indloc.csi.DataPreview;
import de.putterer.indloc.csi.messages.SubscriptionMessage;
import de.putterer.indloc.data.DataClient;
import de.putterer.indloc.data.DataConsumer;
import de.putterer.indloc.data.DataInfo;
import de.putterer.indloc.respiratory.RespiratoryUI;
import de.putterer.indloc.util.Logger;
import lombok.Getter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import static de.putterer.indloc.Config.ROOM;

public class CsiUserInterface implements KeyListener {
	private static final Point TOP_LEFT_OFFSET = new Point(30, 50);

	private static final SubscriptionMessage.SubscriptionOptions SUBSCRIPTION_OPTIONS = new SubscriptionMessage.SubscriptionOptions(
			new SubscriptionMessage.FilterOptions(0)
	);

	private final ExecutorService executorService = Executors.newFixedThreadPool(4);
	private final List<UIComponentWindow> componentWindows = new ArrayList<>();
	private volatile boolean initComplete = false;
	private final Map<DataPreview, Station> previews = new HashMap<>();

	private GenericStatusUI genericStatusUI;
	private final List<RespiratoryUI> respiratoryUIs = new ArrayList<>();
	private FrequencyGeneratorUI frequencyGeneratorUI;
	private final List<ActivityUI> activityUIs = new ArrayList<>();
	private ReplayUI replayUI;

	@Getter
	private CSIReplay replay = null;

	public CsiUserInterface() {
		startClients();

		buildUI();
	}


	private void buildUI() {
		frequencyGeneratorUI = new FrequencyGeneratorUI();
		genericStatusUI = new GenericStatusUI(this);
		genericStatusUI.setPosition(TOP_LEFT_OFFSET.x, TOP_LEFT_OFFSET.y);

		replayUI = new ReplayUI(this, replay != null);
		replayUI.setPosition(TOP_LEFT_OFFSET.x, TOP_LEFT_OFFSET.y + genericStatusUI.getWindowHeight());
		componentWindows.add(replayUI);

		Iterator<Station> stationIter = Arrays.stream(ROOM.getStations()).filter(Station::isDisplayRespiratoryDetector).iterator();
		for(int i = 0;stationIter.hasNext();i++) {
			RespiratoryUI rui = new RespiratoryUI(frequencyGeneratorUI).setStation(stationIter.next());
			rui.setPosition(TOP_LEFT_OFFSET.x + genericStatusUI.getWindowWidth(), i * rui.getWindowHeight() + TOP_LEFT_OFFSET.y);
			respiratoryUIs.add(rui);
		}
		componentWindows.addAll(respiratoryUIs);

		int respiratoryUIWidth = respiratoryUIs.isEmpty() ? 0 : respiratoryUIs.get(0).getWindowWidth();
		frequencyGeneratorUI.setPosition(TOP_LEFT_OFFSET.x + genericStatusUI.getWindowWidth(), TOP_LEFT_OFFSET.y + respiratoryUIWidth);
		componentWindows.addAll(Arrays.asList(genericStatusUI, frequencyGeneratorUI));

		for(int i = 0;i < ROOM.getStations().length;i++) {
			Station station = ROOM.getStations()[i];
			if(! CSIInfo.class.isAssignableFrom(station.getDataType())) {
				continue;
			}

			ActivityUI activityUI = new ActivityUI(station, SUBSCRIPTION_OPTIONS);
			activityUIs.add(activityUI);
			activityUI.setPosition(TOP_LEFT_OFFSET.x + genericStatusUI.getWindowWidth() + respiratoryUIWidth,
					TOP_LEFT_OFFSET.y + activityUI.getWindowHeight() * i);
		}
		componentWindows.addAll(activityUIs);

		componentWindows.stream().map(UIComponentWindow::getFrame).forEach(f -> f.setVisible(true));
		componentWindows.stream().map(UIComponentWindow::getFrame).forEach(f -> f.addKeyListener(this));

		try { Thread.sleep(200); } catch(InterruptedException e) { e.printStackTrace(); }
		componentWindows.forEach(UIComponentWindow::postConstruct);
		try { Thread.sleep(200); } catch(InterruptedException e) { e.printStackTrace(); }
		activityUIs.forEach(au -> au.getFrame().setVisible(false));

		initComplete = true;
	}

	private void rebuildUI() {
		if(genericStatusUI != null) {
			genericStatusUI.destroy();
		}
		if(frequencyGeneratorUI != null) {
			frequencyGeneratorUI.destroy();
		}

		componentWindows.forEach(UIComponentWindow::destroy);
		componentWindows.clear();
		respiratoryUIs.clear();
		activityUIs.clear();

		previews.keySet().forEach(DataPreview::destroy);
		previews.clear();


		if(replay != null) {
			Arrays.stream(ROOM.getStations()).forEach(station ->
				replay.addCallback(station, csiInfos -> Arrays.stream(csiInfos).forEach(c -> onData(station, c)))
			);
		}

		buildUI();
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

	public void addPreview(DataPreview preview, Station station) {
		preview.getFrame().setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		preview.getFrame().addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				preview.getFrame().dispose();
				synchronized (previews) {
					previews.remove(preview);
				}
			}
		});

		synchronized (previews) {
			previews.put(preview, station);
		}
	}

	private void onData(Station station, DataInfo info) {
		if(!initComplete) {
			return;
		}

		componentWindows.forEach(w -> executorService.submit(() -> w.onDataInfo(station, info)));

		synchronized (previews) {
			previews.entrySet().stream().filter(e -> e.getValue() == station).forEach(p -> p.getKey().setData(info));
		}

//		if(info instanceof CSIInfo) {
//			CSIInfo csi = (CSIInfo) info;
//		} else if (info instanceof AndroidInfo) {
//			AndroidInfo androidInfo = (AndroidInfo) info;
//		}
	}

	public void loadReplay(Path replayPath, Consumer<Double> progressCallback) {
		initComplete = false;
		stopClients();

		try {
			replay = new CSIReplay(replayPath, 1, false, progressCallback);
		} catch(IOException e) {
			Logger.error("Couldn't load replay file: %s", replayPath.toAbsolutePath().toString());
		}

		Config.ROOM = replay.getRoom();
		// manually add replay clients

		rebuildUI();
	}

	public void stopClients() {
		DataClient.getClients().forEach(DataClient::removeClient);
	}

	public void setActivityUIsVisible(boolean visible) {
		activityUIs.forEach(au -> au.getFrame().setVisible(visible));
	}

	public static void main(String[] args) throws ClassNotFoundException, UnsupportedLookAndFeelException, InstantiationException, IllegalAccessException {
		Logger.setLogLevel(Logger.Level.DEBUG);

//		FlatDarculaLaf.install();
		FlatDarkLaf.install();
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
