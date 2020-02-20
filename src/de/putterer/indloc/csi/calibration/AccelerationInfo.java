package de.putterer.indloc.csi.calibration;

import de.putterer.indloc.data.DataInfo;
import de.putterer.indloc.util.Logger;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.nio.ByteBuffer;

/**
 * Represents an acceleration info object as sent by a station
 */
@Data
public class AccelerationInfo extends DataInfo implements Serializable {
	private static final transient long serialVersionUID = 979206976253508405L;

	private long serverTimestamp;
	private float[] acceleration;

	public AccelerationInfo(long clientTimestamp, int messageId, long serverTimestamp, float[] acceleration) {
		super(clientTimestamp, messageId);
		this.serverTimestamp = serverTimestamp;
		this.acceleration = acceleration;
	}

	/**
	 * Obtains the acceleration info object from the received byte data
	 * @param buffer
	 */
	public AccelerationInfo(ByteBuffer buffer) {
		super(System.currentTimeMillis(), buffer.getInt());

		serverTimestamp = buffer.getLong();

		acceleration = new float[] {
				buffer.getFloat(),
				buffer.getFloat(),
				buffer.getFloat()
		};
		
		if(buffer.hasRemaining()) {
			Logger.warn("acceleration info buffer hasn't been consumed fully");
		}
	}

}
