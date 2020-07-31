package de.putterer.indloc.csi;

import de.putterer.indloc.data.DataInfo;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

/**
 * Represents a CSIInfo object as sent by a station
 */
@Data
public abstract class CSIInfo extends DataInfo implements Serializable {
	private static final transient long serialVersionUID = 979206976253508405L;

	protected Complex[][][] csi_matrix = new Complex[3][3][114]; // [rx][tx][sc]

	public CSIInfo(long clientTimestamp, int messageId) {
		super(clientTimestamp, messageId);
	}

	public CSIInfo(long clientTimestamp, int messageId, Complex[][][] csi_matrix) {
		super(clientTimestamp, messageId);
		this.csi_matrix = csi_matrix;
	}

	public abstract CSIInfo clone(Complex[][][] newCsiMatrix);
	public abstract int getNumTones();

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
