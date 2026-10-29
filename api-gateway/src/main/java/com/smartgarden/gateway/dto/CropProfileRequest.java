package com.smartgarden.gateway.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CropProfileRequest(
		@NotBlank(message = "El nombre es obligatorio") @Size(max = 64, message = "El nombre admite máximo 64 caracteres") String name,
		@NotNull(message = "minMoisture es obligatorio") @Min(value = 0, message = "minMoisture debe estar entre 0 y 100") @Max(value = 100, message = "minMoisture debe estar entre 0 y 100") Integer minMoisture,
		@NotNull(message = "maxMoisture es obligatorio") @Min(value = 0, message = "maxMoisture debe estar entre 0 y 100") @Max(value = 100, message = "maxMoisture debe estar entre 0 y 100") Integer maxMoisture,
		@Size(max = 255, message = "La descripción admite máximo 255 caracteres") String description) {
}
