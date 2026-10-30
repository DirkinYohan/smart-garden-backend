package com.smartgarden.ai.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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

		String headline,
		HealthStatus healthStatus,
		NextAction nextAction,
		List<RecommendationDetail> recommendationDetails,
		Projection projection,
		String fallbackDetail,

		MoistureLevel moistureLevel) {

	public boolean legacy() {
		return healthStatus == null;
	}

}
