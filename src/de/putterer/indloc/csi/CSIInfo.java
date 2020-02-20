package de.putterer.indloc.csi;

import de.putterer.indloc.data.DataInfo;
import de.putterer.indloc.util.Logger;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.nio.ByteBuffer;

/**
 * Represents a CSIInfo object as sent by a station
 */
@Data
public class CSIInfo extends DataInfo implements Serializable {
	private static final transient long serialVersionUID = 979206976253508405L;

	private CSIStatus csi_status = new CSIStatus();
	private Complex[][][] csi_matrix = new Complex[3][3][114];

	public CSIInfo(long clientTimestamp, int messageId, CSIStatus csi_status, Complex[][][] csi_matrix) {
		super(clientTimestamp, messageId);
		this.csi_status = csi_status;
		this.csi_matrix = csi_matrix;
	}

	/**
	 * Obtains the CSI Info object from the received byte data
	 * @param buffer
	 */
	public CSIInfo(ByteBuffer buffer) {
		super(System.currentTimeMillis(), buffer.getInt());

		csi_status.tstamp = buffer.getLong();
		csi_status.channel = buffer.getShort();
		csi_status.chanBW = buffer.get();
		csi_status.rate = buffer.get();
		csi_status.nr = buffer.get();
		csi_status.nc = buffer.get();
		csi_status.num_tones = buffer.get();
		csi_status.noise = buffer.get();
		csi_status.phyerr = buffer.get();
		csi_status.rssi = buffer.get();
		csi_status.rssi_0 = buffer.get();
		csi_status.rssi_1 = buffer.get();
		csi_status.rssi_2 = buffer.get();
		csi_status.payload_len = buffer.getShort();
		csi_status.csi_len = buffer.getShort();
		csi_status.buf_len = buffer.getShort();
		
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

	/**
	 * The CSI status as read from the kernel
	 */
	@Data
	public static class CSIStatus implements Serializable  {
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

	/**
	 * Represents a complex value
	 */
	@Data
	@AllArgsConstructor
	public static class Complex implements Serializable  {
		private static final long serialVersionUID = -5518569518216735272L;
		private int real;
		private int imag;

		/**
		 * @return the phase of this complex value
		 */
		public double getPhase() {
			double angle = Math.atan2(imag, real);
			if(angle < 0) {
				angle += Math.PI * 2;
			}
			return angle;
		}

		/**
		 * @return the amplitude of this complex value
		 */
		public double getAmplitude() {
			return Math.sqrt(real * real + imag * imag);
		}

		/**
		 * constructs a complex value from amplitude and phase
		 * @param amplitude
		 * @param phase
		 * @return the resulting complex value
		 */
		public static Complex fromAmplitudePhase(double amplitude, double phase) {
			return new Complex((int) Math.round(Math.cos(phase) * amplitude), (int) Math.round(Math.sin(phase) * amplitude));
		}

		@Override
		public String toString() {
			return "R:" + real + " I:" + imag;
		}
	}
}
