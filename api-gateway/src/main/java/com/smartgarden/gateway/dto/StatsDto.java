package com.smartgarden.gateway.dto;

import java.util.List;
import java.util.Map;

public record StatsDto(int days, int totalAnalyses, int geminiCount, int rulesCount,
		Map<String, Integer> riskDistribution, Map<String, Integer> healthDistribution, int rainPauses,
		List<ThresholdPointDto> thresholdHistory, List<DailyMoistureDto> dailyMoisture) {
}
