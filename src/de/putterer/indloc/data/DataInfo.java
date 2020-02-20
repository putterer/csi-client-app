package de.putterer.indloc.data;

import lombok.Data;

/**
 * represents a data object received from any data server (e.g. csi server or acceleration server)
 */
@Data
public class DataInfo {

	private long clientTimestamp; // the timestamp when this message was RECEIVED by this client, unix timestamp in millis
	private int messageId; // the message id as set by the csi server

	public DataInfo(long clientTimestamp, int messageId) {
		this.clientTimestamp = clientTimestamp;
		this.messageId = messageId;
	}
}
