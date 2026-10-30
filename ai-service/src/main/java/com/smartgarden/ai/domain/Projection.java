package com.smartgarden.ai.domain;

import java.time.Instant;
import java.util.List;

public record Projection(List<ProjectionPoint> points, Double dryingRatePerHour, Instant estimatedNextIrrigationAt,
		Confidence confidence) {

	public static Projection empty() {
		return new Projection(List.of(), null, null, Confidence.LOW);
	}

}
