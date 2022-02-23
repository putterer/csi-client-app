package de.putterer.indloc.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import de.putterer.indloc.Config;
import de.putterer.indloc.Station;
import de.putterer.indloc.activity.ActivityUI;
import de.putterer.indloc.csi.CSIInfo;
import de.putterer.indloc.csi.CSIReplay;
import de.putterer.indloc.csi.DataPreview;
import de.putterer.indloc.csi.esp.EspCSIInfo;
import de.putterer.indloc.csi.esp.EspClient;
import de.putterer.indloc.csi.messages.SubscriptionMessage;
import de.putterer.indloc.data.DataClient;
import de.putterer.indloc.data.DataConsumer;
import de.putterer.indloc.data.DataInfo;
import de.putterer.indloc.data.ecg.EcgClient;
import de.putterer.indloc.data.ecg.EcgInfo;
import de.putterer.indloc.data.ssh.SSHDataClient;
import de.putterer.indloc.respiratory.RespiratoryUI;
import de.putterer.indloc.util.ArgumentParser;
import de.putterer.indloc.util.Logger;
import de.putterer.indloc.util.serialization.Serialization;
import lombok.Getter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
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
	private CurveRecorderUI curveRecorderUI;

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

		curveRecorderUI = new CurveRecorderUI(this);
		curveRecorderUI.setPosition(TOP_LEFT_OFFSET.x + genericStatusUI.getWindowWidth(), TOP_LEFT_OFFSET.y);
		componentWindows.add(curveRecorderUI);

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

		try { Thread.sleep(600); } catch(InterruptedException e) { e.printStackTrace(); }

		curveRecorderUI.getFrame().setVisible(false);

		try { Thread.sleep(300); } catch(InterruptedException e) { e.printStackTrace(); }
		componentWindows.forEach(UIComponentWindow::postConstruct);
		try { Thread.sleep(300); } catch(InterruptedException e) { e.printStackTrace(); }
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
		Arrays.stream(ROOM.getStations()).forEach(s -> {
			DataConsumer<? extends DataInfo> dataConsumer = new DataConsumer<>(s.getDataType(), info -> this.onData(s, info));
			if(s.getDataType() == EcgInfo.class) {
				DataClient.addClient(new EcgClient(s, dataConsumer));
			} else if(s.getDataType() == EspCSIInfo.class) {
				DataClient.addClient(new EspClient(s, dataConsumer));
			} else if(s.isProxyViaSsh()) {
				DataClient.addClient(new SSHDataClient(s, dataConsumer));
			} else {
				DataClient.addClient(new DataClient(s, SUBSCRIPTION_OPTIONS, dataConsumer));
			}
		});

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
			previews.entrySet().stream	().filter(e -> e.getValue() == station).forEach(p -> p.getKey().setData(info));
		}
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

	public void setRespiratoryUIVisible(Station station, boolean visible) {
		//recreated as processing is more intense than for activity
		Optional<RespiratoryUI> respiratoryUI = respiratoryUIs.stream().filter(r -> r.getStation() == station).findFirst();
		if(visible && ! respiratoryUI.isPresent()) {
			new Thread(() -> {
				RespiratoryUI rui = new RespiratoryUI(frequencyGeneratorUI).setStation(station);
				rui.setPosition(TOP_LEFT_OFFSET.x + genericStatusUI.getWindowWidth(), TOP_LEFT_OFFSET.y);
				respiratoryUIs.add(rui);
				rui.getFrame().setVisible(true);
				rui.getFrame().addKeyListener(this);
				rui.postConstruct();
				componentWindows.add(rui);
			}).start();
		}
		else respiratoryUI.ifPresent(ui -> {
			respiratoryUIs.remove(ui);
			componentWindows.remove(ui);
			ui.destroy();
		});
	}

	public boolean isRespiratoryUIVisible(Station station) {
		return respiratoryUIs.stream().anyMatch(r -> r.getStation() == station);
	}

	public void setCurveRecorderUIVisible(boolean visible) {
		// ignores stations, writes samples by station!!!
		curveRecorderUI.getFrame().setVisible(visible);
	}

	public boolean isCurveRecorderUIVisible() {
		return curveRecorderUI.isVisible();
	}


	public static String REPLAY_TO_RUN_ON_STARTUP = null;
	public static void main(String[] args) throws ClassNotFoundException, UnsupportedLookAndFeelException, InstantiationException, IllegalAccessException, IOException {
		Map<String, String> arguments = ArgumentParser.parse(args);

		if(arguments.containsKey("run-replay")) {
			REPLAY_TO_RUN_ON_STARTUP = arguments.get("run-replay");
		}
		if(arguments.containsKey("config")) {
			Config.ROOM = Serialization.deserialize(Paths.get(arguments.get("config")), Config.RoomConfig.class);
		}

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
