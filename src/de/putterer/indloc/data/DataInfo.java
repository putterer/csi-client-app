package de.putterer.indloc.data;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * represents a data object received from any data server (e.g. csi server or acceleration server)
 */
@Getter
@Setter
@EqualsAndHashCode
public class DataInfo {

	private long clientTimestamp; // the timestamp when this message was RECEIVED by this client, unix timestamp in millis
	private int messageId; // the message id as set by the csi server

	public DataInfo(long clientTimestamp, int messageId) {
		this.clientTimestamp = clientTimestamp;
		this.messageId = messageId;
	}

	public Instant getClientInstant() {
		return Instant.ofEpochMilli(clientTimestamp);
	}
}
