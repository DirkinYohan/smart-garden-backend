package com.smartgarden.gateway.dto;

import java.time.Instant;
import java.util.List;

public record AnalysisDto(String id, Instant createdAt, String source, String trigger, int recommendedMin,
		int recommendedMax, boolean pauseForRain, Double hoursUntilNextIrrigation, String riskLevel, String summary,
		List<String> recommendations, boolean applied, String fallbackReason, AnalysisInputDto input,
		// v1.1 (null / empty for analyses created before)
		String headline, String healthStatus, String nextAction, List<RecommendationDetailDto> recommendationDetails,
		InputSnapshotDto inputSnapshot, ProjectionDto projection, String fallbackDetail,
		// v1.2: SENSOR_ERROR | VERY_DRY | DRY | OPTIMAL | WET | SATURATED (null for analyses created before)
		String moistureLevel) {
}
