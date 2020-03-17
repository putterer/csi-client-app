package de.putterer.indloc.csi.calibration;

import de.putterer.indloc.data.DataInfo;
import de.putterer.indloc.util.Logger;
import lombok.Data;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Optional;

/**
 * Represents an acceleration info object as sent by a station
 */
@Data
public class AndroidInfo extends DataInfo implements Serializable {
	private static final transient long serialVersionUID = 979206976253508405L;

	private long serverTimestamp;
	private float[] data;
	private float[] calibration; // (de)serialized as data client is not stored in replay

	public AndroidInfo(long clientTimestamp, int messageId, long serverTimestamp, float[] data, float[] calibration) {
		super(clientTimestamp, messageId);
		this.serverTimestamp = serverTimestamp;
		this.data = data;
		this.calibration = calibration;
	}

	/**
	 * Obtains the acceleration info object from the received byte data
	 * @param buffer
	 */
	public AndroidInfo(ByteBuffer buffer, float[] calibration) {
		super(System.currentTimeMillis(), buffer.getInt());

		serverTimestamp = buffer.getLong();

		data = new float[] {
				buffer.getFloat(),
				buffer.getFloat(),
				buffer.getFloat()
		};

		this.calibration = Optional.ofNullable(calibration).orElseGet(() -> new float[] {0f, 0f, 0f});
		
		if(buffer.hasRemaining()) {
			Logger.warn("android info buffer hasn't been consumed fully");
		}
	}

	public float[] accel() {
		return data;
	}
}
