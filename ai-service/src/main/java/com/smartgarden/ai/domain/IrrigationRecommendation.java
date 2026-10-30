package com.smartgarden.ai.domain;

import java.util.List;

public record IrrigationRecommendation(
		Integer recommendedMin,
		Integer recommendedMax,
		Boolean pauseForRain,
		Double hoursUntilNextIrrigation,
		RiskLevel riskLevel,
		String summary,
		List<String> recommendations,
		String headline,
		List<RecommendationDetail> details) {

	public IrrigationRecommendation withDetails(List<RecommendationDetail> newDetails) {
		return new IrrigationRecommendation(recommendedMin, recommendedMax, pauseForRain, hoursUntilNextIrrigation,
				riskLevel, summary, newDetails.stream().map(RecommendationDetail::detail).toList(), headline,
				newDetails);
	}

	public IrrigationRecommendation withHeadline(String newHeadline) {
		return new IrrigationRecommendation(recommendedMin, recommendedMax, pauseForRain, hoursUntilNextIrrigation,
				riskLevel, summary, recommendations, newHeadline, details);
	}

	public IrrigationRecommendation withHours(Double hours) {
		return new IrrigationRecommendation(recommendedMin, recommendedMax, pauseForRain, hours, riskLevel, summary,
				recommendations, headline, details);
	}

	public IrrigationRecommendation withThresholds(int min, int max) {
		return new IrrigationRecommendation(min, max, pauseForRain, hoursUntilNextIrrigation, riskLevel, summary,
				recommendations, headline, details);
	}

	public IrrigationRecommendation withRisk(RiskLevel newRisk) {
		return new IrrigationRecommendation(recommendedMin, recommendedMax, pauseForRain, hoursUntilNextIrrigation,
				newRisk, summary, recommendations, headline, details);
	}

}
