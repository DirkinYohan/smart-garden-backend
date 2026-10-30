package com.smartgarden.gateway.dto;

import java.time.Instant;

public record MeResponse(String username, String role, boolean mustChangePassword, Instant passwordChangedAt) {
}
