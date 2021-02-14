package de.putterer.indloc.csi.esp;

import de.putterer.indloc.Station;
import de.putterer.indloc.data.DataConsumer;
import de.putterer.indloc.data.DataInfo;
import de.putterer.indloc.data.serial.SerialClient;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EspClient extends SerialClient {

    public static final Pattern ESP_CSI_SERIAL_PATTERN = Pattern.compile(
            "<CSI><addr>([0-9a-f][0-9a-f]:[0-9a-f][0-9a-f]:[0-9a-f][0-9a-f]:[0-9a-f][0-9a-f]:[0-9a-f][0-9a-f]:[0-9a-f][0-9a-f])</addr><len>(\\d+)</len>(([0-9a-f]?[0-9a-f]\\s)+)</CSI>"
    );

    private int messageId = 0;
    public EspClient(Station station, DataConsumer<? extends DataInfo>... consumers) {
        super(station, consumers);
    }

    @Override
    protected void onLineRead(String line) {
        Matcher matcher = ESP_CSI_SERIAL_PATTERN.matcher(line);

        if(! matcher.matches()) {
            return;
        }

        EspCSIInfo csi = new EspCSIInfo(System.currentTimeMillis(), messageId++, matcher.group(1), Integer.parseInt(matcher.group(2)), matcher.group(3));
        getApplicableConsumers(EspCSIInfo.class).forEach(c -> c.accept(csi));
    }

}
