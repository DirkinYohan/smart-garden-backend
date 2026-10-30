package com.smartgarden.telemetry.domain;

import java.time.Instant;

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
