package de.putterer.indloc.csi.esp;

import com.google.gson.annotations.SerializedName;
import de.putterer.indloc.csi.CSIInfo;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.nio.ByteBuffer;
import java.util.Arrays;

@Getter
@Setter
@EqualsAndHashCode
public class EspCSIInfo extends CSIInfo {

    private static final int ESP_CSI_SCALE = 20;

    private int subcarriers = 0;
    private final String sourceMac;
    private final int length;
    private final boolean firstWordInvalid; // first four bytes of the CSI data invalid
    private final int rssi; // signed rssi, in dBm
    private final int mcs;
    private final boolean ht = true; // filtered by tool
    private final ChannelBandwidth channelBandwidth; // 0: 20MHz, 1: 40MHz
    private final boolean spaceTimeBlockCode;
    private final GuardInterval guardInterval;
    private final byte channel;
    private final SecondaryChannel secondaryChannel;
    private final long timestamp;
    private final byte antenna;


    public EspCSIInfo(long timestamp, int messageId, String sourceMac, int length, boolean firstWordInvalid, int rssi, int mcs, ChannelBandwidth channelBandwidth, boolean spaceTimeBlockCode, GuardInterval guardInterval, byte channel, SecondaryChannel secondaryChannel, long timestamp1, byte antenna, String csiEntry) {
        super(timestamp, messageId);
        this.sourceMac = sourceMac;
        this.length = length;
        this.firstWordInvalid = firstWordInvalid;
        this.rssi = rssi;
        this.mcs = mcs;
        this.channelBandwidth = channelBandwidth;
        this.spaceTimeBlockCode = spaceTimeBlockCode;
        this.guardInterval = guardInterval;
        this.channel = channel;
        this.secondaryChannel = secondaryChannel;
        this.timestamp = timestamp1;
        this.antenna = antenna;

        // https://docs.espressif.com/projects/esp-idf/en/latest/esp32/api-guides/wifi.html#wi-fi-channel-state-information :
        // `Each item is stored as two bytes: imaginary part followed by real part.
        // There are up to three fields of channel frequency responses according to the type of received packet.
        // They are legacy long training field (LLTF), high throughput LTF (HT-LTF) and space time block code HT-LTF (STBC-HT-LTF).
        // For different types of packets which are received on channels with different state,
        // the sub-carrier index and total bytes of signed characters of CSI is shown in the following table.`

        // fill csi matrix
        ByteBuffer buffer = ByteBuffer.allocate(length);
        Arrays.stream(csiEntry.split(" ")).map(it ->(byte) Integer.parseInt(it, 16)).forEach(buffer::put);

         byte[] data = buffer.array();

        // 384 --> 3 (LLTF, HT-LTF, STBC-HT-LTF) * 64 * 2(imag+real), TODO: only works for a single case
        for(int trainingFieldType = 1;trainingFieldType < 2;trainingFieldType++) { // 0, 1, 2 available, 1 selects HT_LTF
            for(int subcarrier = 0;subcarrier < 64;subcarrier++) {
                csi_matrix[0][0][subcarrier] = new Complex(
                        data[trainingFieldType*64 + subcarrier * 2 + 1] * ESP_CSI_SCALE, // yes, the imaginary part is located before the real part for some reason
                        data[trainingFieldType*64 + subcarrier * 2] * ESP_CSI_SCALE
                );
            }
        }
    }

    @Override
    public CSIInfo clone(Complex[][][] newCsiMatrix) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public int getNumTones() {
        return subcarriers;
    }

    public enum ChannelBandwidth {
        @SerializedName("20MHz") BW_20MHZ,
        @SerializedName("40MHz") BW_40MHZ
    }

    public enum GuardInterval {
        @SerializedName("short") SHORT_GI,
        @SerializedName("long") LONG_GI,
    }

    public enum SecondaryChannel {
        @SerializedName("none") NONE,
        @SerializedName("above") ABOVE,
        @SerializedName("below") BELOW,
    }
}
