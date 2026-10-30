package com.smartgarden.telemetry.mqtt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TelemetryPayload(
		String deviceId,
		Integer raw,
		Integer moisture,
		Boolean pumpOn,
		Boolean sensorOk,
		String mode,
		Integer minMoisture,
		Integer maxMoisture,
		Boolean rainPause,
		Long uptimeMs) {
}
