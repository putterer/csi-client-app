package de.putterer.indloc.ui;

import de.putterer.indloc.Station;
import de.putterer.indloc.data.DataInfo;
import lombok.Getter;

import javax.swing.*;

public abstract class UIComponentWindow extends JPanel {
	@Getter protected int windowWidth;
	@Getter protected int windowHeight;
	@Getter private JFrame frame;

	public UIComponentWindow(String title, int windowWidth, int windowHeight) {
		this.windowWidth = windowWidth;
		this.windowHeight = windowHeight;
		frame = new JFrame(title);
		frame.setSize(windowWidth, windowHeight);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setLocationRelativeTo(null);
	}

	protected void setupFinished() {
		frame.add(this);
		frame.setVisible(true);
	}

	public UIComponentWindow setPosition(int x, int y) {
		frame.setLocation(x, y);
		return this;
	}

	public abstract void onDataInfo(Station station, DataInfo dataInfo);
}
