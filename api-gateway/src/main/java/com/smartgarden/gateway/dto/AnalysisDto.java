package com.smartgarden.gateway.dto;

import java.time.Instant;
import java.util.List;

public record AnalysisDto(String id, Instant createdAt, String source, String trigger, int recommendedMin,
		int recommendedMax, boolean pauseForRain, Double hoursUntilNextIrrigation, String riskLevel, String summary,
		List<String> recommendations, boolean applied, String fallbackReason, AnalysisInputDto input,

		String headline, String healthStatus, String nextAction, List<RecommendationDetailDto> recommendationDetails,
		InputSnapshotDto inputSnapshot, ProjectionDto projection, String fallbackDetail,

		String moistureLevel) {
}
