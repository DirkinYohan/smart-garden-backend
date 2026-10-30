package com.smartgarden.telemetry.mqtt;

import com.smartgarden.telemetry.domain.DeviceMode;
import com.smartgarden.telemetry.domain.TelemetrySnapshot;
import java.time.Instant;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
public class TelemetryPayloadParser {

	public static final int ADC_RAIL_LOW = 10;

	public static final int ADC_RAIL_HIGH = 4000;

	private final JsonMapper jsonMapper;

	public TelemetryPayloadParser(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	public TelemetrySnapshot parse(String json, Instant receivedAt) {
		if (json == null || json.isBlank()) {
			throw new InvalidPayloadException("Empty payload");
		}
		TelemetryPayload p;
		try {
			p = jsonMapper.readValue(json, TelemetryPayload.class);
		}
		catch (JacksonException ex) {
			throw new InvalidPayloadException("Malformed JSON: " + ex.getOriginalMessage(), ex);
		}
		if (p == null) {
			throw new InvalidPayloadException("Payload is not a JSON object");
		}
		return validate(p, receivedAt);
	}

	private TelemetrySnapshot validate(TelemetryPayload p, Instant receivedAt) {
		if (p.deviceId() == null || p.deviceId().isBlank()) {
			throw new InvalidPayloadException("deviceId is required");
		}
		if (p.deviceId().length() > 64) {
			throw new InvalidPayloadException("deviceId is too long");
		}
		int moisture = required(p.moisture(), "moisture");
		if (moisture < 0 || moisture > 100) {
			throw new InvalidPayloadException("moisture must be between 0 and 100: " + moisture);
		}
		int raw = required(p.raw(), "raw");
		if (raw < 0) {
			throw new InvalidPayloadException("raw must be >= 0: " + raw);
		}
		boolean pumpOn = required(p.pumpOn(), "pumpOn");
		DeviceMode mode;
		try {
			mode = DeviceMode.parse(required(p.mode(), "mode"));
		}
		catch (IllegalArgumentException ex) {
			throw new InvalidPayloadException(ex.getMessage());
		}
		int min = required(p.minMoisture(), "minMoisture");
		int max = required(p.maxMoisture(), "maxMoisture");
		if (min < 0 || max > 100 || min >= max) {
			throw new InvalidPayloadException("Invalid thresholds: min=" + min + ", max=" + max);
		}
		boolean sensorOk = (p.sensorOk() == null || p.sensorOk()) && isPlausibleRaw(raw);
		boolean rainPause = p.rainPause() != null && p.rainPause();
		long uptimeMs = p.uptimeMs() == null ? 0 : Math.max(0, p.uptimeMs());
		return new TelemetrySnapshot(p.deviceId().trim(), raw, moisture, pumpOn, sensorOk, mode, min, max, rainPause,
				uptimeMs, receivedAt);
	}

	public static boolean isPlausibleRaw(int raw) {
		return raw > ADC_RAIL_LOW && raw < ADC_RAIL_HIGH;
	}

	private static <T> T required(T value, String field) {
		if (value == null) {
			throw new InvalidPayloadException(field + " is required");
		}
		return value;
	}

	public static String parseStatus(String payload) {
		if (payload == null) {
			throw new InvalidPayloadException("Empty status");
		}
		String value = payload.trim().replace("\"", "").toLowerCase();
		if (!value.equals("online") && !value.equals("offline")) {
			throw new InvalidPayloadException("Unknown status: " + payload);
		}
		return value;
	}

}
