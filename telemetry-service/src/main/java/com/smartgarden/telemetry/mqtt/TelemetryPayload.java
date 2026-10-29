package com.smartgarden.telemetry.mqtt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Raw JSON published by the ESP32 on {@code smartgarden/esp32/telemetry}. Wrapper types so missing fields can be
 * detected during validation.
 */
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
