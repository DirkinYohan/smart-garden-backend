package com.smartgarden.irrigation.domain;

import java.time.Instant;

public record ConfigView(
		IrrigationModeType mode,
		CropProfileView crop,
		int minMoisture,
		int maxMoisture,
		boolean rainPause,
		boolean aiEnabled,
		Instant updatedAt,
		String updatedBy,
		String lastAnalysisId) {
}
