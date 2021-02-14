package de.putterer.indloc.data.ecg;

import de.putterer.indloc.Station;
import de.putterer.indloc.data.DataConsumer;
import de.putterer.indloc.data.DataInfo;
import de.putterer.indloc.data.serial.SerialClient;

public class EcgClient extends SerialClient {

    private int messageId = 0;
    public EcgClient(Station station, DataConsumer<? extends DataInfo>... consumers) {
        super(station, consumers);
    }

    @Override
    protected void onLineRead(String line) {
        float value;
        try {
            value = Float.parseFloat(line);
        } catch(NumberFormatException e) {
            return; // the reading could start in the middle of a transmission resulting in decoded garbage
        }
        value /= 4095.0;
        EcgInfo info = new EcgInfo(System.currentTimeMillis(), messageId++, value);
        getApplicableConsumers(EcgInfo.class).forEach(c -> c.accept(info));
    }
}
