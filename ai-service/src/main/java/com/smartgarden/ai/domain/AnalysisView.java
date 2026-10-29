package com.smartgarden.ai.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Stored analysis. The v1.1 fields (headline … projection) are null / empty for analyses created before; in that
 * case {@code input} is also not exposed as the new input snapshot.
 */
public record AnalysisView(
		UUID id,
		Instant createdAt,
		AnalysisSource source,
		String trigger,
		int recommendedMin,
		int recommendedMax,
		boolean pauseForRain,
		Double hoursUntilNextIrrigation,
		RiskLevel riskLevel,
		String summary,
		List<String> recommendations,
		boolean applied,
		String fallbackReason,
		AnalysisInputData input,
		// v1.1
		String headline,
		HealthStatus healthStatus,
		NextAction nextAction,
		List<RecommendationDetail> recommendationDetails,
		Projection projection,
		String fallbackDetail,
		// v1.2: level calculated in Java (null for analyses stored before)
		MoistureLevel moistureLevel) {

	/** True for analyses created before v1.1 (no snapshot / projection). */
	public boolean legacy() {
		return healthStatus == null;
	}

}
