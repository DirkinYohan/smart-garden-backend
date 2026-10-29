package com.smartgarden.telemetry.mqtt;

import com.smartgarden.telemetry.config.MqttProperties;
import com.smartgarden.telemetry.service.DeviceStatusTracker;
import com.smartgarden.telemetry.service.TelemetryIngestionService;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

/**
 * Routes inbound MQTT messages. Never throws: an exception here would make Paho drop the connection, so invalid
 * payloads are logged and discarded.
 */
@Component
public class MqttInboundHandler {

	private static final Logger log = LoggerFactory.getLogger(MqttInboundHandler.class);

	private final MqttProperties props;

	private final TelemetryPayloadParser parser;

	private final TelemetryIngestionService ingestion;

	private final DeviceStatusTracker statusTracker;

	private final Clock clock;

	public MqttInboundHandler(MqttProperties props, TelemetryPayloadParser parser, TelemetryIngestionService ingestion,
			DeviceStatusTracker statusTracker, Clock clock) {
		this.props = props;
		this.parser = parser;
		this.ingestion = ingestion;
		this.statusTracker = statusTracker;
		this.clock = clock;
	}

	public void handle(Message<?> message) {
		String topic = message.getHeaders().get(MqttHeaders.RECEIVED_TOPIC, String.class);
		String payload = payloadAsString(message.getPayload());
		try {
			if (props.telemetryTopic().equals(topic)) {
				ingestion.ingest(parser.parse(payload, clock.instant()));
			}
			else if (props.statusTopic().equals(topic)) {
				if (payload.isBlank()) {
					return; // retained message cleared
				}
				statusTracker.onStatusMessage(TelemetryPayloadParser.parseStatus(payload));
			}
			else {
				log.debug("Ignoring message on unexpected topic {}", topic);
			}
		}
		catch (InvalidPayloadException ex) {
			log.warn("Discarding invalid MQTT payload on {}: {} | payload={}", topic, ex.getMessage(),
					abbreviate(payload));
		}
		catch (RuntimeException ex) {
			log.error("Error processing MQTT message on {} | payload={}", topic, abbreviate(payload), ex);
		}
	}

	private static String payloadAsString(Object payload) {
		if (payload instanceof byte[] bytes) {
			return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
		}
		return payload == null ? "" : payload.toString();
	}

	private static String abbreviate(String s) {
		return s.length() <= 300 ? s : s.substring(0, 300) + "...";
	}

}
