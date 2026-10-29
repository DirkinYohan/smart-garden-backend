package com.smartgarden.gateway.dto;

import java.time.Instant;

public record TelemetryReadingDto(String deviceId, int raw, int moisture, boolean pumpOn, boolean sensorOk, String mode,
		int minMoisture, int maxMoisture, boolean rainPause, long uptimeMs, Instant receivedAt) {
}
