package com.smartgarden.gateway.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

/** Partial update: null fields are left unchanged. */
public record UpdateConfigRequest(IrrigationModeDto mode,
		@Positive(message = "cropProfileId debe ser positivo") Long cropProfileId,
		@Min(value = 0, message = "minMoisture debe estar entre 0 y 100") @Max(value = 100, message = "minMoisture debe estar entre 0 y 100") Integer minMoisture,
		@Min(value = 0, message = "maxMoisture debe estar entre 0 y 100") @Max(value = 100, message = "maxMoisture debe estar entre 0 y 100") Integer maxMoisture,
		Boolean rainPause, Boolean aiEnabled) {
}
