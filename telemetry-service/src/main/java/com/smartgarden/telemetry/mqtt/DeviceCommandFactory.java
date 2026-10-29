package com.smartgarden.telemetry.mqtt;

import com.smartgarden.contracts.common.v1.PumpAction;
import com.smartgarden.telemetry.domain.DeviceMode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Builds the JSON commands of the MQTT contract. CONFIG is retained so the ESP32 receives it when it reconnects; PUMP
 * is not.
 */
@Component
public class DeviceCommandFactory {

	/** The ESP32 enforces the same cap in MANUAL mode. */
	public static final int MAX_PUMP_DURATION_SEC = 120;

	private final JsonMapper jsonMapper;

	public DeviceCommandFactory(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	public DeviceCommand pump(PumpAction action, int durationSec) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("type", "PUMP");
		switch (action) {
			case PUMP_ACTION_ON -> {
				if (durationSec < 1 || durationSec > MAX_PUMP_DURATION_SEC) {
					throw new IllegalArgumentException(
							"durationSec must be between 1 and " + MAX_PUMP_DURATION_SEC + ": " + durationSec);
				}
				body.put("action", "ON");
				body.put("durationSec", durationSec);
			}
			case PUMP_ACTION_OFF -> body.put("action", "OFF");
			default -> throw new IllegalArgumentException("Pump action is required (ON or OFF)");
		}
		return new DeviceCommand(jsonMapper.writeValueAsString(body), false);
	}

	public DeviceCommand config(DeviceMode mode, int minMoisture, int maxMoisture, boolean rainPause) {
		if (minMoisture < 0 || maxMoisture > 100 || minMoisture >= maxMoisture) {
			throw new IllegalArgumentException(
					"Invalid thresholds: min=" + minMoisture + ", max=" + maxMoisture);
		}
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("type", "CONFIG");
		body.put("mode", mode.name());
		body.put("minMoisture", minMoisture);
		body.put("maxMoisture", maxMoisture);
		body.put("rainPause", rainPause);
		return new DeviceCommand(jsonMapper.writeValueAsString(body), true);
	}

	public record DeviceCommand(String payload, boolean retained) {
	}

}
