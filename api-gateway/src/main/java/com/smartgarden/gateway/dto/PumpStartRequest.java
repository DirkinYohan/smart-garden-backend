package com.smartgarden.gateway.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PumpStartRequest(@NotNull(message = "durationSec es obligatorio") @Min(value = 1,
		message = "durationSec debe estar entre 1 y 120") @Max(value = 120,
				message = "durationSec debe estar entre 1 y 120") Integer durationSec) {
}
