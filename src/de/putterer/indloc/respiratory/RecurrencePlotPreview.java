package de.putterer.indloc.respiratory;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class RecurrencePlotPreview extends JFrame {

	private final JLabel label = new JLabel();

	public RecurrencePlotPreview() {
		this.setDefaultCloseOperation(HIDE_ON_CLOSE);
		this.setBounds(Toolkit.getDefaultToolkit().getScreenSize().width - 280, 20, 400, 400);
		this.setTitle("Recurrence plot");

		this.add(label);

		this.setVisible(true);
	}

	public void setImage(BufferedImage image) {
		label.setIcon(new ImageIcon(image.getScaledInstance(this.getWidth(), this.getHeight(), Image.SCALE_SMOOTH)));
		this.repaint();
	}
}
