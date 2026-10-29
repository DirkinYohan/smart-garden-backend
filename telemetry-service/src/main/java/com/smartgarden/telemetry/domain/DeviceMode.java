package com.smartgarden.telemetry.domain;

import com.smartgarden.contracts.common.v1.IrrigationMode;

/**
 * Irrigation mode reported by / sent to the ESP32.
 */
public enum DeviceMode {
	AUTO, MANUAL, AI;

	public static DeviceMode parse(String value) {
		if (value == null) {
			throw new IllegalArgumentException("mode is required");
		}
		try {
			return DeviceMode.valueOf(value.trim().toUpperCase());
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException("Unknown mode: " + value);
		}
	}

	public IrrigationMode toProto() {
		return switch (this) {
			case AUTO -> IrrigationMode.IRRIGATION_MODE_AUTO;
			case MANUAL -> IrrigationMode.IRRIGATION_MODE_MANUAL;
			case AI -> IrrigationMode.IRRIGATION_MODE_AI;
		};
	}

	public static DeviceMode fromProto(IrrigationMode mode) {
		return switch (mode) {
			case IRRIGATION_MODE_AUTO -> AUTO;
			case IRRIGATION_MODE_MANUAL -> MANUAL;
			case IRRIGATION_MODE_AI -> AI;
			default -> throw new IllegalArgumentException("mode is required");
		};
	}
}
