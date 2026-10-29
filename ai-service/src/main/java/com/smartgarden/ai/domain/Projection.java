package com.smartgarden.ai.domain;

import java.time.Instant;
import java.util.List;

/**
 * Hourly soil-moisture forecast for the next 24 h (see MoistureProjector for the formula).
 * @param estimatedNextIrrigationAt when the projection crosses the minimum threshold, or null
 */
public record Projection(List<ProjectionPoint> points, Double dryingRatePerHour, Instant estimatedNextIrrigationAt,
		Confidence confidence) {

	public static Projection empty() {
		return new Projection(List.of(), null, null, Confidence.LOW);
	}

}
