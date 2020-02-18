package de.putterer.indloc.trilateration;

import de.putterer.indloc.Station;
import de.putterer.indloc.util.Vector;

import java.util.List;

public interface Trilaterator {
	public Vector estimate(List<Station> stations);
}
