package com.smartgarden.gateway.dto;

import java.time.Instant;

/** Respuesta de POST /auth/login. */
public record LoginResponse(String token, Instant expiresAt, boolean mustChangePassword, String username) {
}
