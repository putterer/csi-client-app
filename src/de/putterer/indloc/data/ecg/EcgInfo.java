package de.putterer.indloc.data.ecg;

import de.putterer.indloc.data.DataInfo;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode
public class EcgInfo extends DataInfo{

	private float value;

	public EcgInfo(long clientTimestamp, int messageId, float value) {
		super(clientTimestamp, messageId);
		this.value = value;
	}
}
