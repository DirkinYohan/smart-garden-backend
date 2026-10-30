package com.smartgarden.telemetry.service;

import com.smartgarden.telemetry.config.TelemetryProperties;
import com.smartgarden.telemetry.domain.TelemetrySnapshot;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class DeviceStatusTracker {

	private static final Logger log = LoggerFactory.getLogger(DeviceStatusTracker.class);

	public static final String UNKNOWN = "unknown";

	private final TelemetryProperties props;

	private final Clock clock;

	private final Subscribers<DeviceStatusChange> subscribers = new Subscribers<>();

	private String lastStatus = UNKNOWN;

	private Instant lastReadingAt;

	private String deviceId = "";

	private boolean online;

	public DeviceStatusTracker(TelemetryProperties props, Clock clock) {
		this.props = props;
		this.clock = clock;
	}

	public synchronized void onReading(TelemetrySnapshot reading) {
		lastReadingAt = reading.receivedAt();
		deviceId = reading.deviceId();

		lastStatus = "online";
		evaluate("telemetry");
	}

	public synchronized void onStatusMessage(String status) {
		log.info("Device status message: {}", status);
		lastStatus = status;
		evaluate("offline".equals(status) ? "last-will" : "status-message");
	}

	@Scheduled(fixedDelayString = "${smartgarden.telemetry.offline-check-interval:5s}")
	public synchronized void checkTimeout() {
		evaluate("timeout");
	}

	public synchronized boolean isOnline() {
		return computeOnline();
	}

	public synchronized String lastStatus() {
		return lastStatus;
	}

	public synchronized Instant lastReadingAt() {
		return lastReadingAt;
	}

	public Subscribers<DeviceStatusChange> subscribers() {
		return subscribers;
	}

	private boolean computeOnline() {
		if ("offline".equals(lastStatus) || lastReadingAt == null) {
			return false;
		}
		return Duration.between(lastReadingAt, clock.instant()).compareTo(props.offlineTimeout()) <= 0;
	}

	private void evaluate(String reason) {
		boolean now = computeOnline();
		if (now != online) {
			online = now;
			log.info("Device {} is now {} ({})", deviceId, now ? "ONLINE" : "OFFLINE", reason);
			subscribers.publish(new DeviceStatusChange(deviceId, now, reason, clock.instant()));
		}
	}

	public record DeviceStatusChange(String deviceId, boolean online, String reason, Instant changedAt) {
	}

}
