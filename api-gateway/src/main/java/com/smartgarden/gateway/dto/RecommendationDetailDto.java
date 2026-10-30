package com.smartgarden.gateway.dto;

public record RecommendationDetailDto(String title, String detail, String category, String priority,
		SuggestedActionDto action) {
}
