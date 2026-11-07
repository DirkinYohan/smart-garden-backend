package com.smartgarden.telemetry.mqtt;

import com.smartgarden.telemetry.mqtt.DeviceCommandFactory.DeviceCommand;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps the retained CONFIG on the broker fresh. Some managed brokers expire retained messages or lose them on
 * maintenance (EMQX Cloud Serverless keeps them without expiry, max. 2000), and then an ESP32 that reconnects later
 * would not get its CONFIG from the broker. telemetry-service
 * is always running, so it remembers the last retained CONFIG it sent and publishes it again once a day and when the
 * device comes back online after a long time. The ESP32 also keeps its last CONFIG in flash (Preferences), so this
 * is a safety net, not the only copy. The MQTT contract does not change.
 */
@Component
public class RetainedConfigRefresher {

	private static final Logger log = LoggerFactory.getLogger(RetainedConfigRefresher.class);

	private final Clock clock;

	private final Duration refreshOnlineAfter;

	private MqttCommandPublisher publisher;

	private DeviceCommand lastRetained;

	private Instant lastPublishedAt;

	public RetainedConfigRefresher(Clock clock,
			@Value("${smartgarden.mqtt.retained-refresh-online-after:12h}") Duration refreshOnlineAfter) {
		this.clock = clock;
		this.refreshOnlineAfter = refreshOnlineAfter;
	}

	void attach(MqttCommandPublisher publisher) {
		this.publisher = publisher;
	}

	synchronized void remember(DeviceCommand command) {
		if (command.retained()) {
			lastRetained = command;
			lastPublishedAt = clock.instant();
		}
	}

	/** Called when the device publishes {@code online}. */
	public void onDeviceOnline() {
		DeviceCommand command;
		synchronized (this) {
			if (lastRetained == null || lastPublishedAt == null
					|| clock.instant().isBefore(lastPublishedAt.plus(refreshOnlineAfter))) {
				return;
			}
			command = lastRetained;
		}
		republish(command, "device back online");
	}

	@Scheduled(fixedDelayString = "${smartgarden.mqtt.retained-refresh-interval:24h}",
			initialDelayString = "${smartgarden.mqtt.retained-refresh-interval:24h}")
	public void refreshPeriodically() {
		DeviceCommand command;
		synchronized (this) {
			command = lastRetained;
		}
		if (command != null) {
			republish(command, "periodic refresh");
		}
	}

	private void republish(DeviceCommand command, String reason) {
		if (publisher == null) {
			return;
		}
		try {
			publisher.publish(command);
			log.info("Retained CONFIG published again ({})", reason);
		}
		catch (RuntimeException ex) {
			log.warn("Could not refresh the retained CONFIG ({}): {}", reason, ex.getMessage());
		}
	}

}
