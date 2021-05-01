package de.putterer.indloc.ui;

import de.putterer.indloc.Station;
import de.putterer.indloc.csi.DataPreview;
import de.putterer.indloc.data.DataInfo;
import de.putterer.indloc.util.Logger;
import de.putterer.indloc.util.PlatformUtil;
import lombok.Getter;

import javax.swing.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public abstract class UIComponentWindow extends JPanel {
	private static final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

	@Getter protected int windowWidth;
	@Getter protected int windowHeight;
	@Getter private JFrame frame;

	public UIComponentWindow(String title, int windowWidth, int windowHeight) {
		this.windowWidth = windowWidth;
		this.windowHeight = windowHeight;
		frame = new JFrame(title + (PlatformUtil.isRunningI3() ? " i3float" : ""));
		frame.setSize(windowWidth, windowHeight);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setLocationRelativeTo(null);

		executor.schedule(() -> {
			if(! setupFinishedCalled) {
				Logger.error("Setup finished not called on at least one UIComponentWindow with type %s", this.getClass().getName());
			}
		}, 3, TimeUnit.SECONDS);
	}

	private boolean setupFinishedCalled = false;
	protected void setupFinished() {
		setupFinishedCalled = true;
		frame.add(this);
	}

	public UIComponentWindow setPosition(int x, int y) {
		frame.setLocation(x, y);
		return this;
	}

	public void postConstruct() {

	}

	public abstract void onDataInfo(Station station, DataInfo dataInfo);

	public void onPreviewCreated(DataPreview dataPreview) {

	}

	public void destroy() {
		frame.dispose();
	}
}
