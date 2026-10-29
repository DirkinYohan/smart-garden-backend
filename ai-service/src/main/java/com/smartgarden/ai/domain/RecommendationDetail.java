package com.smartgarden.ai.domain;

/** One recommendation with an optional one-click action ({@code action} may be null). */
public record RecommendationDetail(String title, String detail, RecommendationCategory category, Priority priority,
		SuggestedAction action) {

	public RecommendationDetail withAction(SuggestedAction newAction) {
		return new RecommendationDetail(title, detail, category, priority, newAction);
	}

}
