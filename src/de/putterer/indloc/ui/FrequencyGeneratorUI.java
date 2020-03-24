package de.putterer.indloc.ui;

import de.putterer.indloc.Station;
import de.putterer.indloc.data.DataInfo;
import de.putterer.indloc.util.Observable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;

import static javax.swing.SwingUtilities.invokeLater;

public class FrequencyGeneratorUI extends UIComponentWindow implements ComponentListener {

	private final Color color1 = new Color(0.8f, 0.8f, 0.8f);
	private final Color color2 = new Color(0.9f, 0.9f, 0.9f);

	private final JSlider frequencySelector = new JSlider(JSlider.HORIZONTAL, 0, 120, 60);
	private final Observable<Double> frequency = new Observable<>(1.0);

	public FrequencyGeneratorUI() {
		super("Frequency generator", 420, 100);

		this.setLayout(null);
		initUI();

		getFrame().addComponentListener(this);

		setupFinished();
	}

	private void loop() {
		while(getFrame().isVisible()) {
			long sleepMillis = (long) (1.0 / frequency.get() * 1000.0 / 2.0);
			frequencySelector.setBackground(color1);
			this.repaint();
			try { Thread.sleep(sleepMillis); } catch (InterruptedException e) { e.printStackTrace(); }
			frequencySelector.setBackground(color2);
			this.repaint();
			try { Thread.sleep(sleepMillis); } catch (InterruptedException e) { e.printStackTrace(); }
		}
	}

	private void initUI() {
		frequencySelector.setMajorTickSpacing(10);
		frequencySelector.setMinorTickSpacing(2);
		frequencySelector.setPaintTicks(true);
		frequencySelector.setPaintLabels(true);
		frequencySelector.setBounds(0, 0, 420, 70);
		this.add(frequencySelector);

		frequencySelector.addChangeListener(e -> frequency.set(frequencySelector.getValue() / 60.0));
	}

	@Override
	public void postConstruct() {
		// set window to hidden after all windows were set to visible
		invokeLater(() -> getFrame().setVisible(false));
	}

	@Override
	public void componentShown(ComponentEvent e) {
		new Thread(this::loop).start();
	}
	@Override public void componentResized(ComponentEvent e) { }
	@Override public void componentMoved(ComponentEvent e) { }
	@Override public void componentHidden(ComponentEvent e) { }

	@Override
	public void onDataInfo(Station station, DataInfo dataInfo) { }
}
