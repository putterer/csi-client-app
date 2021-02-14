package de.putterer.indloc.csi.esp;

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

    public EspCSIInfo(long timestamp, int messageId, String sourceMac, int length, String csiEntry) {
        super(timestamp, messageId);
        this.sourceMac = sourceMac;
        this.length = length;

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
}
