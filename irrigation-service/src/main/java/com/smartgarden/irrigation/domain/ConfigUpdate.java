package com.smartgarden.irrigation.domain;

/**
 * Partial update of the configuration; {@code null} means "leave unchanged".
 */
public record ConfigUpdate(
		IrrigationModeType mode,
		Long cropProfileId,
		Integer minMoisture,
		Integer maxMoisture,
		Boolean rainPause,
		Boolean aiEnabled) {
}
