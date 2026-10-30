package com.smartgarden.gateway.dto;

import java.time.Instant;
import java.util.List;

public record ProjectionDto(List<ProjectionPointDto> points, Double dryingRatePerHour,
		Instant estimatedNextIrrigationAt, String confidence) {
}
