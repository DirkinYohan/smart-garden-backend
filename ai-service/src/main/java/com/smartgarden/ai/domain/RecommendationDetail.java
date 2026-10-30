package com.smartgarden.ai.domain;

public record RecommendationDetail(String title, String detail, RecommendationCategory category, Priority priority,
		SuggestedAction action) {

	public RecommendationDetail withAction(SuggestedAction newAction) {
		return new RecommendationDetail(title, detail, category, priority, newAction);
	}

}
