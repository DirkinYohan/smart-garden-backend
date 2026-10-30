package com.smartgarden.gateway.dto;

import java.time.Instant;

public record LoginResponse(String token, Instant expiresAt, boolean mustChangePassword, String username) {
}
