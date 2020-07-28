package de.putterer.indloc.csi.atheros;

import de.putterer.indloc.csi.CSIInfo;
import de.putterer.indloc.util.Logger;
import lombok.Data;

import java.io.Serializable;
import java.nio.ByteBuffer;

@Data
public class AthCSIInfo extends CSIInfo {

	private AthCSIStatus atherosCsiStatus = new AthCSIStatus();

	/**
	 * Obtains the CSI Info object from the received byte data
	 * @param buffer
	 */
	public AthCSIInfo(ByteBuffer buffer) {
		super(System.currentTimeMillis(), buffer.getInt());

		atherosCsiStatus.tstamp = buffer.getLong();
		atherosCsiStatus.channel = buffer.getShort();
		atherosCsiStatus.chanBW = buffer.get();
		atherosCsiStatus.rate = buffer.get();
		atherosCsiStatus.nr = buffer.get();
		atherosCsiStatus.nc = buffer.get();
		atherosCsiStatus.num_tones = buffer.get();
		atherosCsiStatus.noise = buffer.get();
		atherosCsiStatus.phyerr = buffer.get();
		atherosCsiStatus.rssi = buffer.get();
		atherosCsiStatus.rssi_0 = buffer.get();
		atherosCsiStatus.rssi_1 = buffer.get();
		atherosCsiStatus.rssi_2 = buffer.get();
		atherosCsiStatus.payload_len = buffer.getShort();
		atherosCsiStatus.csi_len = buffer.getShort();
		atherosCsiStatus.buf_len = buffer.getShort();

		for(int i1 = 0;i1 < 3;i1++) {
			for(int i2 = 0;i2 < 3;i2++) {
				for(int i3 = 0;i3 < 114;i3++) {
					csi_matrix[i1][i2][i3] = new Complex(buffer.getInt(), buffer.getInt());
				}
			}
		}

		if(buffer.hasRemaining()) {
			Logger.warn("CSI info buffer hasn't been consumed fully");
		}
	}

	public AthCSIInfo(long clientTimestamp, int messageId, Complex[][][] csi_matrix, AthCSIStatus atherosCsiStatus) {
		super(clientTimestamp, messageId, csi_matrix);
		this.atherosCsiStatus = atherosCsiStatus;
	}

	@Override
	public CSIInfo clone(Complex[][][] newCsiMatrix) {
		return new AthCSIInfo(this.getClientTimestamp(), this.getMessageId(), newCsiMatrix, this.atherosCsiStatus);
	}

	@Override
	public int getNumTones() {
		return atherosCsiStatus.num_tones;
	}

	/**
	 * The CSI status as read from the kernel
	 */
	@Data
	public static class AthCSIStatus implements Serializable {
		private static final long serialVersionUID = -4314496045245323601L;
		// Datatypes may be larger for handling than in C due to Java not supporting unsigned data types

		/*u_int64_t*/ private long tstamp;         /* h/w assigned time stamp ON THE CSI SERVER device */

		/*u_int16_t*/ private int channel;        /* wireless channel (represented in Hz)*/
		/*u_int8_t*/  private byte chanBW;         /* channel bandwidth (0->20MHz,1->40MHz)*/

		/*u_int8_t*/  private short rate;           /* transmission rate*/
		/*u_int8_t*/  private byte nr;             /* number of receiving antenna*/
		/*u_int8_t*/  private byte nc;             /* number of transmitting antenna*/
		/*u_int8_t*/  private byte num_tones;      /* number of tones (subcarriers) */
		/*u_int8_t*/  private short noise;          /* noise floor (to be updated)*/

		/*u_int8_t*/  private short phyerr;          /* phy error code (set to 0 if correct)*/

		/*u_int8_t*/   private short rssi;         /*  rx frame RSSI */
		/*u_int8_t*/   private short rssi_0;       /*  rx frame RSSI [ctl, chain 0] */
		/*u_int8_t*/   private short rssi_1;       /*  rx frame RSSI [ctl, chain 1] */
		/*u_int8_t*/   private short rssi_2;       /*  rx frame RSSI [ctl, chain 2] */

		/*u_int16_t*/  private int payload_len;  /*  payload length (bytes) */
		/*u_int16_t*/  private int csi_len;      /*  csi data length (bytes) */
		/*u_int16_t*/  private int buf_len;      /*  data length in buffer */
	}
}
