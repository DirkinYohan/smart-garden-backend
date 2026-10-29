package com.smartgarden.gateway.dto;

/** One-click action; {@code type} maps to an existing REST call (e.g. APPLY_THRESHOLDS -> PUT /config). */
public record SuggestedActionDto(String type, String label) {
}
