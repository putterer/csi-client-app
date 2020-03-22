package de.putterer.indloc.ui;

import lombok.Getter;

import javax.swing.*;

public class GenericStatusUI extends UIComponentWindow {

	public GenericStatusUI() {
		super("CSI toolbox - Fabian Putterer - TUM", 500, 300);

		this.setLayout(null);

		setupFinished();
	}
}
