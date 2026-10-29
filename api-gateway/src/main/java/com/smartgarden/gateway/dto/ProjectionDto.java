package com.smartgarden.gateway.dto;

import java.time.Instant;
import java.util.List;

/** Hourly moisture forecast for the next 24 h (formula in the README). */
public record ProjectionDto(List<ProjectionPointDto> points, Double dryingRatePerHour,
		Instant estimatedNextIrrigationAt, String confidence) {
}
