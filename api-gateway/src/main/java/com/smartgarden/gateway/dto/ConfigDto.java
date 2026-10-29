package com.smartgarden.gateway.dto;

import java.time.Instant;

public record ConfigDto(String mode, CropProfileDto crop, int minMoisture, int maxMoisture, boolean rainPause,
		boolean aiEnabled, Instant updatedAt, String updatedBy, String lastAnalysisId) {
}
