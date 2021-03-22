package de.putterer.indloc.data.ssh;

import de.putterer.indloc.Station;
import de.putterer.indloc.csi.atheros.AthCSIInfo;
import de.putterer.indloc.data.DataConsumer;

import java.io.IOException;

public class SSHTest {

	public static void main(String args[]) throws IOException {
		SSHDataClient client = new SSHDataClient(new Station("", "192.168.178.205", AthCSIInfo.class, null, null), new DataConsumer<>(AthCSIInfo.class, athCSIInfo -> {
			System.out.println("Received csi info " + athCSIInfo.getAtherosCsiStatus().getRssi());
		}));

		client.subscribe();
	}

}
