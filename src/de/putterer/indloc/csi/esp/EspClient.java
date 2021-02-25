package de.putterer.indloc.csi.esp;

import de.putterer.indloc.Station;
import de.putterer.indloc.data.DataConsumer;
import de.putterer.indloc.data.DataInfo;
import de.putterer.indloc.data.serial.SerialClient;

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static de.putterer.indloc.csi.esp.EspCSIInfo.ChannelBandwidth.BW_20MHZ;
import static de.putterer.indloc.csi.esp.EspCSIInfo.ChannelBandwidth.BW_40MHZ;
import static de.putterer.indloc.csi.esp.EspCSIInfo.GuardInterval.LONG_GI;
import static de.putterer.indloc.csi.esp.EspCSIInfo.GuardInterval.SHORT_GI;

public class EspClient extends SerialClient {

    public static final int ESP_CSI_BAUD_RATE = 921600;
    public static final Pattern ESP_CSI_SERIAL_PATTERN = Pattern.compile(
            "<CSI>" +
                    "<addr>([0-9a-f][0-9a-f]:[0-9a-f][0-9a-f]:[0-9a-f][0-9a-f]:[0-9a-f][0-9a-f]:[0-9a-f][0-9a-f]:[0-9a-f][0-9a-f])</addr>" +
                    "<len>(\\d+)</len>" +
                    "<inv>([01])</inv>" +
                    "<rssi>(-?\\d+)</rssi>" +
                    "<mcs>(\\d+)</mcs>" +
                    "<cwb>([01])</cwb>" +
                    "<stbc>([01])</stbc>" +
                    "<sgi>([01])</sgi>" +
                    "<chl>(\\d+)</chl>" +
                    "<sec_chl>(\\d)</sec_chl>" +
                    "<t>(\\d+)</t>" +
                    "<ant>([01])</ant>" +
                    "(([0-9a-f]?[0-9a-f]\\s)+)" +
            "</CSI>"
    );

    private int messageId = 0;
    public EspClient(Station station, DataConsumer<? extends DataInfo>... consumers) {
        super(station, ESP_CSI_BAUD_RATE, consumers);
    }

    @Override
    protected void onLineRead(String line) {
        Matcher matcher = ESP_CSI_SERIAL_PATTERN.matcher(line);

        if(! matcher.matches()) {
            return;
        }

        EspCSIInfo csi = new EspCSIInfo(
                System.currentTimeMillis(),
                messageId++,
                matcher.group(1),
                Integer.parseInt(matcher.group(2)),
                matcher.group(3).equals("1"),
                Integer.parseInt(matcher.group(4)),
                Integer.parseInt(matcher.group(5)),
                matcher.group(6).equals("0") ? BW_20MHZ : BW_40MHZ,
                matcher.group(7).equals("1"),
                matcher.group(8).equals("0") ? LONG_GI : SHORT_GI,
                Byte.parseByte(matcher.group(9)),
                ((Function<String, EspCSIInfo.SecondaryChannel>) s -> {
                    switch(s) {
                        case "0": return EspCSIInfo.SecondaryChannel.NONE;
                        case "1": return EspCSIInfo.SecondaryChannel.ABOVE;
                        case "2": return EspCSIInfo.SecondaryChannel.BELOW;
                        default: throw new RuntimeException("Unknown secondary channel type");
                    }
                }).apply(matcher.group(10)),
                Long.parseUnsignedLong(matcher.group(11)),
                Byte.parseByte(matcher.group(12)),
                matcher.group(13)
        );
        getApplicableConsumers(EspCSIInfo.class).forEach(c -> c.accept(csi));
    }

}
