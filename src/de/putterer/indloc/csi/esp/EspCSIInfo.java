package de.putterer.indloc.csi.esp;

import com.google.gson.annotations.SerializedName;
import de.putterer.indloc.csi.CSIInfo;
import de.putterer.indloc.util.Logger;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static de.putterer.indloc.csi.esp.EspCSIInfo.SecondaryChannel.BELOW;

@Getter
@Setter
@EqualsAndHashCode
public class EspCSIInfo extends CSIInfo {

    private static final int TRAINING_FIELD_LLTF = 0;
    private static final int TRAINING_FIELD_HT_LTF = 1;
    private static final int TRAINING_FIELD_STBC_HT_LTF = 2;

    private final int TRAINING_FIELD_TO_USE = TRAINING_FIELD_LLTF;

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

        int lltfLength = 0;
        int htLtfLength = 0;
        int stbcHtLtfLength = 0;

        // implements the table in the link above
        // always at HT rate
        switch(secondaryChannel) {
            case NONE:
                lltfLength = 64;
                htLtfLength = 64;
                if(isSpaceTimeBlockCode()) {
                    stbcHtLtfLength = 64;
                }
                break;

            case ABOVE:
            case BELOW:
                switch(channelBandwidth) {
                    case BW_20MHZ:
                        if(spaceTimeBlockCode) {
                            lltfLength = 64;
                            htLtfLength = (secondaryChannel == BELOW) ? 63 : 62;
                            stbcHtLtfLength = (secondaryChannel == BELOW) ? 63 : 62;
                        } else {
                            lltfLength = 64;
                            htLtfLength = 64;
                        }
                        break;
                    case BW_40MHZ:
                        if(spaceTimeBlockCode) {
                            lltfLength = 64;
                            htLtfLength = 121;
                            stbcHtLtfLength = 121;
                        } else {
                            lltfLength = 64;
                            htLtfLength = 128;
                        }
                        break;
                }

                break;
        }

        int[] trainingFieldLengths = new int[]{
                lltfLength,
                htLtfLength,
                stbcHtLtfLength
        };

        int expectedLength = (Arrays.stream(trainingFieldLengths).sum()) * 2;
        if(length != expectedLength) {
            Logger.error("EspCSI is of wrong length, expected %d, got %d", expectedLength, length);
        }

        // fill csi matrix
        ByteBuffer buffer = ByteBuffer.allocate(length);
        Arrays.stream(csiEntry.split(" ")).map(it -> (byte) Integer.parseInt(it, 16)).forEach(buffer::put);

        byte[] data = buffer.array();
        int currentStartIndex = 0;

        for(int trainingFieldType = 0;trainingFieldType < 2;trainingFieldType++) {
            for(int subcarrier = 0;subcarrier < 64;subcarrier++) {
                if(trainingFieldType == TRAINING_FIELD_TO_USE) {
                    csi_matrix[0][0][subcarrier] = new Complex(
                            data[currentStartIndex + subcarrier * 2 + 1] * ESP_CSI_SCALE, // yes, the imaginary part is located before the real part for some reason
                            data[currentStartIndex + subcarrier * 2] * ESP_CSI_SCALE
                    );
                }
            }
            currentStartIndex += trainingFieldLengths[trainingFieldType] * 2; // imag and real
        }

        subcarriers = trainingFieldLengths[TRAINING_FIELD_TO_USE];
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
