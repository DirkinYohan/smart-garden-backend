package com.smartgarden.gateway.dto;

import java.time.Instant;

/** Token reemitido tras un cambio de contraseña (PUT /auth/password). */
public record TokenResponse(String token, Instant expiresAt) {
}
