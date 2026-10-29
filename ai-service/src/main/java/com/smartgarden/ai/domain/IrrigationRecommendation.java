package com.smartgarden.ai.domain;

import java.util.List;

/**
 * Validated recommendation, produced from a Gemini answer (RecommendationValidator) or by the rule-based fallback.
 * {@code recommendations} always equals the {@code detail} of each entry of {@code details}.
 */
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

	/** Thresholds clamped to the crop profile (±10 points). */
	public IrrigationRecommendation withThresholds(int min, int max) {
		return new IrrigationRecommendation(min, max, pauseForRain, hoursUntilNextIrrigation, riskLevel, summary,
				recommendations, headline, details);
	}

	/** Risk derived from the moisture level (deterministic, never decided by Gemini). */
	public IrrigationRecommendation withRisk(RiskLevel newRisk) {
		return new IrrigationRecommendation(recommendedMin, recommendedMax, pauseForRain, hoursUntilNextIrrigation,
				newRisk, summary, recommendations, headline, details);
	}

}
