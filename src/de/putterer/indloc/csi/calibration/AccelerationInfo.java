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
public class AccelerationInfo extends DataInfo implements Serializable {
	private static final transient long serialVersionUID = 979206976253508405L;

	private long serverTimestamp;
	private float[] acceleration;
	private float[] calibration; // (de)serialized as data client is not stored in replay

	public AccelerationInfo(long clientTimestamp, int messageId, long serverTimestamp, float[] acceleration, float[] calibration) {
		super(clientTimestamp, messageId);
		this.serverTimestamp = serverTimestamp;
		this.acceleration = acceleration;
		this.calibration = calibration;
	}

	/**
	 * Obtains the acceleration info object from the received byte data
	 * @param buffer
	 */
	public AccelerationInfo(ByteBuffer buffer, float[] calibration) {
		super(System.currentTimeMillis(), buffer.getInt());

		serverTimestamp = buffer.getLong();

		acceleration = new float[] {
				buffer.getFloat(),
				buffer.getFloat(),
				buffer.getFloat()
		};

		this.calibration = Optional.ofNullable(calibration).orElseGet(() -> new float[] {0f, 0f, 0f});
		
		if(buffer.hasRemaining()) {
			Logger.warn("acceleration info buffer hasn't been consumed fully");
		}
	}

	public float[] accel() {
		return acceleration;
	}
}
