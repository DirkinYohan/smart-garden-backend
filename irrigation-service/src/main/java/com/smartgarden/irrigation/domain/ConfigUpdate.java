package com.smartgarden.irrigation.domain;

public record ConfigUpdate(
		IrrigationModeType mode,
		Long cropProfileId,
		Integer minMoisture,
		Integer maxMoisture,
		Boolean rainPause,
		Boolean aiEnabled) {
}
