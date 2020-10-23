package de.putterer.indloc.data.serial;

import de.putterer.indloc.data.DataInfo;
import lombok.Data;

@Data
public class SerialInfo extends DataInfo{

	private float value;

	public SerialInfo(long clientTimestamp, int messageId, float value) {
		super(clientTimestamp, messageId);
		this.value = value;
	}
}
