package de.putterer.indloc.csi.intel;

import de.putterer.indloc.csi.CSIInfo;
import de.putterer.indloc.util.Logger;
import lombok.Data;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@Data
public class IntCSIInfo extends CSIInfo {

	private static final int NUM_TONES = 30;

	private IntCSINotification intelCsiNotification = new IntCSINotification();

	public IntCSIInfo(ByteBuffer buffer) {
		super(System.currentTimeMillis(), buffer.getInt());

		var notification = intelCsiNotification;
		notification.timestamp_low = buffer.getInt();
		notification.bfee_count = buffer.getShort();
		notification.Nrx = buffer.get();
		notification.Ntx = buffer.get();
		notification.rssi_a = buffer.getShort();
		notification.rssi_b = buffer.getShort();
		notification.rssi_c = buffer.getShort();
		notification.noise = buffer.getShort();
		notification.agc = buffer.getShort();
		notification.antenna_sel = buffer.get();
		notification.perm[0] = buffer.get();
		notification.perm[1] = buffer.get();
		notification.perm[2] = buffer.get();
		notification.len = buffer.getShort();
		notification.fake_rate_n_flags = buffer.getShort();

		int csi_mat_entries = notification.Ntx * notification.Nrx * NUM_TONES;

		// dimensions of matrix as received from csi-server:
		// TX x RX x SC
		for(int tx = 0;tx < notification.Ntx;tx++) {
			for(int rx = 0;rx < notification.Nrx;rx++) {
				for(int sc = 0;sc < NUM_TONES;sc++) {
					buffer.order(ByteOrder.LITTLE_ENDIAN);
					double real = buffer.getDouble();
					double imag = buffer.getDouble();

					// permute csi matrix according to antenna selection
					int targetRXAntenna = notification.perm[rx];

					// atheros 10 bit -> 512
					// intel 8 bit * 4? --> 512
					this.csi_matrix[targetRXAntenna][tx][sc] = new Complex(
							(int)Math.round(real * 4.0),
							(int)Math.round(imag * 4.0)
					);

//					System.out.printf("R%.0fI%.0f ", real, imag);
				}
			}
		}
//		System.out.println("\n\n");

		//TODO: decrypt AND PERMUTE matrix
		//TODO: scale CSI according to https://dhalperi.github.io/linux-80211n-csitool/faq.html -> section 2

		if(buffer.hasRemaining()) {
			Logger.warn("Intel CSI info buffer hasn't been consumed fully");
		}
	}

	@Override
	public CSIInfo clone(Complex[][][] newCsiMatrix) {
		//TODO:
		throw new UnsupportedOperationException("not yet implemented");
	}

	@Override
	public int getNumTones() {
		return NUM_TONES;
	}

	/**
	 * The CSI notification received excluding the csi matrix
	 */
	@Data
	public static class IntCSINotification implements Serializable {
		private static final long serialVersionUID = -4314496045245323601L;
		// Datatypes may be larger for handling than in C due to Java not supporting unsigned data types

		// timestamp_low is the low 32 bits of the NIC's 1 MHz clock. It wraps about every 4300 seconds, or 72 minutes
		/*u_int32_t*/ long timestamp_low;

		// the total number of bfee measurements that have been sent from the driver (loss may occur in the pipe)
		/*u_int16_t*/ int bfee_count;

		// Nrx represents the number of antennas used to receive the packet by this NIC,
		// and Ntx represents the number of space/time streams transmitted.
		/*u_int8_t*/ byte Nrx, Ntx;

		// rssi_a, rssi_b, and rssi_c correspond to RSSI measured by the receiving NIC at the input to each antenna port.
		// This measurement is made during the packet preamble. This value is in dB relative to an internal reference;
		// to get the received signal strength in dBm we must combine it with the
		// Automatic Gain Control (AGC) setting (agc) in dB and also subtract off a magic constant.
		/*u_int8_t*/ short rssi_a, rssi_b, rssi_c;

		/*u_int16_t*/ short noise;

		// Automatic gain control setting
		/*u_int16_t*/ short agc;

		// indicates which antenna got mapped to which RF chain, this info is also present in the perm field
		/*u_int8_t*/ short antenna_sel;

		// Antenna permutation, 0 BASED!!!!
		// tells us how the NIC permuted the signals from the 3 receive antennas into the 3 RF chains that process the
		// measurements. The sample value of [2 1 0] implies that Antenna C was sent to RF Chain A,
		// Antenna B to Chain B, and Antenna A to Chain C. This operation is performed by an antenna selection module
		// in the NIC and generally corresponds to ordering the antennas in decreasing order of RSSI.
		byte[] perm = new byte[3];

		// Length of the csi, should equal (checked by csi-server):
		// (30 * (notification->Nrx * notification->Ntx * 8 * 2 + 3) + 7) / 8
		/*u_int16_t*/
		/*u_int16_t*/ int len;

		// please do not ask me what this is :)
		/*u_int16_t*/ int fake_rate_n_flags;
	}
}
