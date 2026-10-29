package com.smartgarden.telemetry.domain;

import java.time.Instant;

/**
 * A validated telemetry reading as received from the device, enriched with the server reception time.
 */
public record TelemetrySnapshot(
		String deviceId,
		int raw,
		int moisture,
		boolean pumpOn,
		boolean sensorOk,
		DeviceMode mode,
		int minMoisture,
		int maxMoisture,
		boolean rainPause,
		long uptimeMs,
		Instant receivedAt) {
}
