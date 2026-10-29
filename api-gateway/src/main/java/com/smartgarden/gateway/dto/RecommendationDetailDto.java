package com.smartgarden.gateway.dto;

/** {@code action} is null when the recommendation has no one-click action. */
public record RecommendationDetailDto(String title, String detail, String category, String priority,
		SuggestedActionDto action) {
}
