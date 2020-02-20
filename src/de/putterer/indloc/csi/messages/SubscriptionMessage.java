package de.putterer.indloc.csi.messages;

import de.putterer.indloc.data.DataClient;
import lombok.Data;

import java.nio.ByteBuffer;

/**
 * Sent to a router running the OpenWRT server to subscribe to further CSI info
 */
@Data
public class SubscriptionMessage extends Message {
	
	private final SubscriptionOptions options;
	
	@Override
	public byte[] toBytes() {
		ByteBuffer buffer = ByteBuffer.allocate(5);
		buffer.put(DataClient.TYPE_SUBSCRIBE);
		buffer.putInt(options.filter_options.payload_size);
		return buffer.array();
	}
	
	@Data
	public static class SubscriptionOptions {
		private final FilterOptions filter_options;
	}
	
	@Data
	public static class FilterOptions {
		private final int payload_size;
	}
}
