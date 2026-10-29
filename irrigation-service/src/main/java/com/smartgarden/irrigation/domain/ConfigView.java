package com.smartgarden.irrigation.domain;

import java.time.Instant;

/**
 * Immutable snapshot of the irrigation configuration, safe to share across threads (streams).
 */
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
